package app.lumicast

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lumicast.data.library.saveToDownloads
import app.lumicast.data.stream.RangeStreamServer
import app.lumicast.data.stream.findLanIPv4
import app.lumicast.domain.CastDevice
import app.lumicast.domain.PlayStatus
import app.lumicast.domain.TgChat
import app.lumicast.domain.TgVideo
import app.lumicast.domain.VideoFolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import app.lumicast.domain.StreamSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as LumicastApp).container
    private val telegram = container.telegram
    private val cast = container.cast

    val auth = telegram.authState
    val devices = cast.devices

    /** % baixado do Telegram vem da fonte do stream (null = modo demonstração, o FakeCastGateway simula). */
    private val downloaded = MutableStateFlow<Float?>(null)
    val playback = combine(cast.playback, downloaded) { p, f -> if (f == null) p else p.copy(downloadedFraction = f) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, cast.playback.value)

    private val prefs = app.getSharedPreferences("lumicast", 0)
    private val _pinned = MutableStateFlow(prefs.getStringSet("pinned", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet())
    val pinned: StateFlow<Set<Long>> = _pinned.asStateFlow()

    /** URL do vídeo aberto no player do celular (prévia), ou null. */
    private val _previewUrl = MutableStateFlow<String?>(null)
    val previewUrl: StateFlow<String?> = _previewUrl.asStateFlow()
    private var previewing: Pair<TgVideo, String>? = null

    private val _chats = MutableStateFlow<List<TgChat>>(emptyList())
    val chats: StateFlow<List<TgChat>> = _chats.asStateFlow()

    private val _openChat = MutableStateFlow<TgChat?>(null)
    val openChat: StateFlow<TgChat?> = _openChat.asStateFlow()

    private val _videos = MutableStateFlow<List<TgVideo>>(emptyList())
    val videos: StateFlow<List<TgVideo>> = _videos.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _queue = MutableStateFlow<List<TgVideo>>(emptyList())
    val queue: StateFlow<List<TgVideo>> = _queue.asStateFlow()

    /** Horário marcado para a fila começar (epoch ms), ou null. */
    private val _scheduledAt = MutableStateFlow<Long?>(null)
    val scheduledAt: StateFlow<Long?> = _scheduledAt.asStateFlow()

    /** Troca para o próximo da fila a cada N minutos; 0 = só quando o vídeo terminar. */
    private val _switchEveryMin = MutableStateFlow(0)
    val switchEveryMin: StateFlow<Int> = _switchEveryMin.asStateFlow()

    /** Começar/terminar cada vídeo nesses segundos (ex.: pular abertura e créditos); 0 = desligado. Salvo no aparelho. */
    private val _trimStart = MutableStateFlow(prefs.getInt("trimStart", 0))
    val trimStart: StateFlow<Int> = _trimStart.asStateFlow()
    private val _trimEnd = MutableStateFlow(prefs.getInt("trimEnd", 0))
    val trimEnd: StateFlow<Int> = _trimEnd.asStateFlow()
    private var trimmedFileId: Int? = null

    /** Vídeos já tocados, para o botão "Anterior". */
    private val history = ArrayDeque<TgVideo>()

    private var queueDevice: CastDevice? = null
    private var scheduleJob: Job? = null
    private var switchJob: Job? = null

    init {
        cast.startDiscovery()
        // Vídeo terminou na TV → próximo da fila na mesma TV.
        viewModelScope.launch {
            cast.playback.map { it.status }.distinctUntilChanged().collect { status ->
                if (status == PlayStatus.Ended) queueDevice?.let(::playNext)
            }
        }
        // Chegou no "Terminar em" → próximo da fila (ou parar, se a fila acabou).
        viewModelScope.launch {
            cast.playback.collect { p ->
                val video = p.video ?: return@collect
                if (p.status == PlayStatus.Connecting) trimmedFileId = null // vídeo novo começando
                val end = _trimEnd.value
                if (end <= 0 || end >= video.durationSec || p.status != PlayStatus.Playing || p.positionSec < end) return@collect
                if (trimmedFileId == video.fileId) return@collect
                trimmedFileId = video.fileId
                if (_queue.value.isNotEmpty()) queueDevice?.let(::playNext) else stop()
            }
        }
    }

    val folders: StateFlow<List<VideoFolder>> = container.folders.folders
    private val _openFolderId = MutableStateFlow<String?>(null)
    val openFolder: StateFlow<VideoFolder?> = combine(folders, _openFolderId) { fs, id -> fs.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Texto do download de pasta em andamento ("Baixando 2 de 5…"), ou null. */
    private val _downloadStatus = MutableStateFlow<String?>(null)
    val downloadStatus: StateFlow<String?> = _downloadStatus.asStateFlow()

    fun showFolder(folder: VideoFolder) {
        _openChat.value = null
        _openFolderId.value = folder.id
    }

    private fun editFolder(id: String, change: (VideoFolder) -> VideoFolder?) =
        container.folders.update { fs -> fs.mapNotNull { if (it.id == id) change(it) else it } }

    /** Cria a pasta (e já põe [video] nela, se veio de um vídeo). */
    fun createFolder(name: String, video: TgVideo?) =
        container.folders.update { it + VideoFolder(UUID.randomUUID().toString(), name.trim(), listOfNotNull(video)) }

    fun addToFolder(folderId: String, video: TgVideo) {
        editFolder(folderId) { f -> if (f.videos.any { it.fileId == video.fileId }) f else f.copy(videos = f.videos + video) }
        Toast.makeText(getApplication(), "Adicionado à pasta.", Toast.LENGTH_SHORT).show()
    }

    fun renameFolder(folderId: String, name: String) = editFolder(folderId) { it.copy(name = name.trim()) }

    fun deleteFolder(folderId: String) {
        editFolder(folderId) { null }
        if (_openFolderId.value == folderId) _openFolderId.value = null
    }

    fun renameVideo(folderId: String, video: TgVideo, title: String) =
        editFolder(folderId) { f -> f.copy(videos = f.videos.map { if (it.fileId == video.fileId) it.copy(title = title.trim()) else it }) }

    fun removeFromFolder(folderId: String, video: TgVideo) =
        editFolder(folderId) { f -> f.copy(videos = f.videos.filterNot { it.fileId == video.fileId }) }

    fun moveVideo(fromId: String, video: TgVideo, toId: String) {
        if (fromId == toId) return
        removeFromFolder(fromId, video)
        addToFolder(toId, video)
    }

    private var downloadJob: Job? = null

    /** Baixa todos os vídeos da pasta para Downloads/Lumicast/<pasta>. */
    fun downloadFolder(folderId: String) {
        folders.value.firstOrNull { it.id == folderId }?.let { downloadAll(it.name, it.videos) }
    }

    /** Baixa todos os vídeos do chat aberto para Downloads/Lumicast/<chat>. */
    fun downloadChat() {
        _openChat.value?.let { downloadAll(it.title, _videos.value) }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    /** Um vídeo por vez: terminou um, começa o próximo, até acabar a lista (ou o usuário cancelar). */
    private fun downloadAll(name: String, list: List<TgVideo>) {
        if (!container.realMode || downloadJob?.isActive == true) return
        val videos = list.distinctBy { it.fileId } // o mesmo arquivo pode estar em duas mensagens
        if (videos.isEmpty()) return
        // shortcut: o download vive no ViewModel; se o Android matar o app no meio, refaça (WorkManager resolveria).
        downloadJob = viewModelScope.launch {
            var done = 0
            var current: TgVideo? = null
            try {
                videos.forEachIndexed { i, video ->
                    current = video
                    _downloadStatus.value = "Baixando ${i + 1} de ${videos.size}: ${video.title}"
                    runCatching {
                        val path = telegram.download(video)
                        withContext(Dispatchers.IO) { saveToDownloads(getApplication(), File(path), name, video.title, video.mimeType) }
                        if (streaming?.first?.fileId != video.fileId && previewing?.first?.fileId != video.fileId) telegram.releaseLocal(video)
                        done++
                    }.onFailure {
                        if (it is CancellationException) throw it
                        Diag.log("download", "falhou ${video.fileId}: ${it.message}")
                    }
                }
                val msg = if (done == videos.size) "Vídeos salvos em Downloads/Lumicast/$name."
                else "Baixados $done de ${videos.size}. Confira a internet e o espaço livre e tente de novo."
                Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) {
                current?.let { withContext(NonCancellable) { telegram.closeStream(it) } } // parar o download no TDLib também
                Toast.makeText(getApplication(), "Download cancelado. $done de ${videos.size} já estão em Downloads/Lumicast/$name.", Toast.LENGTH_LONG).show()
                throw e
            } finally {
                _downloadStatus.value = null
            }
        }
    }

    fun enqueue(video: TgVideo) {
        _queue.value += video
        Toast.makeText(getApplication(), "Adicionado à fila (${_queue.value.size}).", Toast.LENGTH_SHORT).show()
    }

    fun moveInQueue(index: Int, delta: Int) {
        val list = _queue.value.toMutableList()
        val to = index + delta
        if (index !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(index))
        _queue.value = list
    }

    fun removeFromQueue(index: Int) {
        _queue.value = _queue.value.filterIndexed { i, _ -> i != index }
    }

    fun setTrim(startSec: Int, endSec: Int) {
        _trimStart.value = startSec
        _trimEnd.value = endSec
        prefs.edit().putInt("trimStart", startSec).putInt("trimEnd", endSec).apply()
    }

    fun next() {
        (queueDevice ?: cast.playback.value.device)?.let(::playNext)
    }

    /** Volta para o vídeo anterior; o atual volta para o começo da fila. Sem anterior, recomeça o atual. */
    fun previous() {
        val device = queueDevice ?: cast.playback.value.device ?: return
        val current = cast.playback.value.video
        val prev = history.removeLastOrNull()
        if (prev == null) {
            seekTo(_trimStart.value)
            return
        }
        current?.let { _queue.value = listOf(it) + _queue.value }
        castTo(prev, device)
    }

    fun setSwitchEvery(minutes: Int) {
        _switchEveryMin.value = minutes
    }

    /** Começa a fila na [device] agora ou no horário [atMillis]. */
    fun startQueue(device: CastDevice, atMillis: Long?) {
        scheduleJob?.cancel()
        _scheduledAt.value = atMillis
        // shortcut: o agendamento vive enquanto o app estiver aberto; usar AlarmManager se precisar sobreviver a fechar o app.
        scheduleJob = viewModelScope.launch {
            atMillis?.let { delay(it - System.currentTimeMillis()) }
            _scheduledAt.value = null
            playNext(device)
        }
    }

    fun cancelSchedule() {
        scheduleJob?.cancel()
        _scheduledAt.value = null
    }

    private fun playNext(device: CastDevice) {
        val next = _queue.value.firstOrNull() ?: return
        _queue.value = _queue.value.drop(1)
        cast.playback.value.video?.let { history.addLast(it) }
        castTo(next, device)
    }

    fun submitPhone(phone: String) = viewModelScope.launch { telegram.submitPhone(phone) }
    fun submitCode(code: String) = viewModelScope.launch { telegram.submitCode(code) }
    fun resendCode() = viewModelScope.launch { telegram.resendCode() }
    fun submitPassword(password: String) = viewModelScope.launch { telegram.submitPassword(password) }
    fun logout() = viewModelScope.launch { telegram.logout(); _chats.value = emptyList(); _openChat.value = null }

    fun loadChats() = viewModelScope.launch {
        _loading.value = true
        _chats.value = telegram.chats()
        _loading.value = false
    }

    fun open(chat: TgChat) = viewModelScope.launch {
        _openFolderId.value = null
        _openChat.value = chat
        _loading.value = true
        _videos.value = telegram.videos(chat.id)
        _loading.value = false
    }

    fun closeChat() {
        _openFolderId.value = null
        _openChat.value = null
        _videos.value = emptyList()
    }

    fun rescan() = cast.startDiscovery()

    private var server: RangeStreamServer? = null
    private var streaming: Pair<TgVideo, String>? = null
    private var progressJob: Job? = null

    fun togglePin(chat: TgChat) {
        _pinned.value = if (chat.id in _pinned.value) _pinned.value - chat.id else _pinned.value + chat.id
        prefs.edit().putStringSet("pinned", _pinned.value.map { it.toString() }.toSet()).apply()
    }

    suspend fun thumbnail(video: TgVideo): String? = runCatching { telegram.thumbnail(video) }.getOrNull()

    /** Abre o stream do Telegram no servidor local e devolve a URL (a TV e o player do celular usam a mesma). */
    private suspend fun serve(video: TgVideo): Pair<String, StreamSource> {
        val srv = server ?: run {
            val ip = findLanIPv4(getApplication()) ?: error("Sem Wi-Fi. Conecte o celular à mesma rede da TV.")
            RangeStreamServer(ip).also { it.start(); server = it }
        }
        val source = telegram.openStream(video)
        return srv.register(source) to source
    }

    fun shareDiagnostics() {
        val app = getApplication<Application>()
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico do Lumicast")
            .putExtra(Intent.EXTRA_TEXT, Diag.report(app))
        app.startActivity(Intent.createChooser(send, "Enviar diagnóstico").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun toast(t: Throwable) {
        Diag.log("erro", t.stackTraceToString().take(800))
        Toast.makeText(getApplication(), t.message ?: "Não foi possível abrir o vídeo. Tente de novo.", Toast.LENGTH_LONG).show()
    }

    fun preview(video: TgVideo) = viewModelScope.launch {
        if (!container.realMode) {
            Toast.makeText(getApplication(), "Prévia indisponível no modo demonstração.", Toast.LENGTH_SHORT).show()
            return@launch
        }
        runCatching {
            endPreview()
            val (url, _) = serve(video)
            previewing = video to url
            _previewUrl.value = url
        }.onFailure(::toast)
    }

    fun endPreview() = viewModelScope.launch {
        previewing?.let { (video, url) ->
            server?.unregister(url)
            if (streaming?.first?.fileId != video.fileId) telegram.closeStream(video) // a TV pode estar usando o mesmo arquivo
        }
        previewing = null
        _previewUrl.value = null
    }

    /** TDLib (download progressivo) → servidor HTTP local na LAN → TV. No modo demonstração, só simula. */
    fun castTo(original: TgVideo, device: CastDevice) = viewModelScope.launch {
        val start = _trimStart.value
        // O "Começar em" usa o mesmo caminho do "retomar de onde parou" (os gateways começam em duração × fração).
        // +0,5 s porque os gateways truncam: 90 s não pode virar 89.
        val video = if (start in 1 until original.durationSec) original.copy(resumeFraction = (start + 0.5f) / original.durationSec) else original
        queueDevice = device
        switchJob?.cancel()
        val every = _switchEveryMin.value
        if (every > 0 && _queue.value.isNotEmpty()) {
            switchJob = viewModelScope.launch { delay(every * 60_000L); playNext(device) }
        }
        if (!container.realMode) {
            cast.play(device, video, streamUrl = "http://demo.invalid/stream")
            return@launch
        }
        runCatching {
            endStream()
            val (url, source) = serve(video)
            streaming = video to url
            progressJob = viewModelScope.launch { while (true) { downloaded.value = source.downloadedFraction; delay(1_000) } }
            cast.play(device, video, url)
            Diag.log("vm", "transmitindo file=${video.fileId} (${video.mimeType}, ${video.sizeBytes} B) em ${device.protocol}")
        }.onFailure {
            endStream()
            toast(it)
        }
    }

    private suspend fun endStream() {
        progressJob?.cancel()
        downloaded.value = null
        streaming?.let { (video, url) -> server?.unregister(url); telegram.closeStream(video) }
        streaming = null
    }

    fun togglePause() = viewModelScope.launch { cast.togglePause() }
    fun seekTo(sec: Int) = viewModelScope.launch { cast.seekTo(sec) }
    fun stop() = viewModelScope.launch {
        switchJob?.cancel()
        queueDevice = null // parar à mão não puxa o próximo da fila
        cast.stop()
        endStream()
    }

    override fun onCleared() {
        server?.stop()
        cast.stopDiscovery()
    }
}
