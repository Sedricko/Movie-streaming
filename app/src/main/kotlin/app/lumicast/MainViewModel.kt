package app.lumicast

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lumicast.data.library.SavedVideoSource
import app.lumicast.data.library.VideoConverter
import android.net.Uri
import app.lumicast.data.library.saveToDownloads
import app.lumicast.data.cast.renderConnectedSplash
import app.lumicast.data.stream.BytesSource
import app.lumicast.domain.StorageInfo
import app.lumicast.domain.ThemeMode
import app.lumicast.domain.CastProtocol
import app.lumicast.domain.srtToVtt
import app.lumicast.data.stream.RangeStreamServer
import app.lumicast.data.stream.findLanIPv4
import app.lumicast.domain.Account
import app.lumicast.domain.AppSettings
import app.lumicast.domain.AuthState
import app.lumicast.domain.CastDevice
import app.lumicast.domain.NaturalTitleOrder
import app.lumicast.domain.PlayStatus
import app.lumicast.domain.TgChat
import app.lumicast.domain.TgVideo
import app.lumicast.domain.Transfer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.update
import app.lumicast.domain.VideoFolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
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

    private val prefs = container.globalPrefs // configurações, TV, fila: valem para todas as contas
    private val accountPrefs = container.accountPrefs // fixados, salvos, convertidos: de cada conta

    /** Contas deste celular (a atual sempre aparece). */
    private val _accounts = MutableStateFlow(loadAccounts())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()
    val currentAccountId = container.accountId

    private fun loadAccounts(): List<Account> =
        (prefs.getStringSet("accounts", emptySet())!! + container.accountId)
            .sortedBy { it.toIntOrNull() ?: 0 }
            .map { Account(it, prefs.getString("account.name.$it", "").orEmpty()) }


    /** A TV escolhida (lembrada entre aberturas). Com uma TV só na rede, ela já vem escolhida. */
    private val _selectedTvId = MutableStateFlow(prefs.getString("tv", null))
    val selectedTv: StateFlow<CastDevice?> = combine(devices, _selectedTvId) { ds, id -> ds.firstOrNull { it.id == id } ?: ds.singleOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Escolheu a TV na lista: lembra e, se nada estiver tocando, mostra nela a tela "conectado". */
    fun selectTv(device: CastDevice) {
        rememberTv(device)
        if (playback.value.video == null) showSplash(device)
    }

    private fun rememberTv(device: CastDevice) {
        _selectedTvId.value = device.id
        prefs.edit().putString("tv", device.id).apply()
    }

    private var splashUrl: String? = null

    /** Tela "Lumicast conectado" na TV (configurável). Também encerra o stream que estava aberto. */
    private fun showSplash(device: CastDevice) {
        if (!_settings.value.showSplash || !container.realMode) return
        viewModelScope.launch {
            runCatching {
                endStream()
                val srv = ensureServer()
                splashUrl?.let(srv::unregister)
                val png = withContext(Dispatchers.Default) { renderConnectedSplash(getApplication(), device.name) }
                val url = srv.register(BytesSource(png, "image/png")).also { splashUrl = it }
                cast.showImage(device, url, "Lumicast conectado")
                queueDevice = device
            }.onFailure { Diag.log("splash", "não mostrou: ${it.message}") } // detalhe visual: não incomodar com aviso
        }
    }

    private val _settings = MutableStateFlow(
        AppSettings(
            resume = prefs.getBoolean("set.resume", true),
            autoNext = prefs.getBoolean("set.autoNext", true),
            dlna = prefs.getBoolean("set.dlna", true),
            volumeKeys = prefs.getBoolean("set.volumeKeys", true),
            autoConvert = prefs.getBoolean("set.autoConvert", true),
            showSplash = prefs.getBoolean("set.showSplash", true),
            cacheLimitGb = prefs.getInt("set.cacheLimitGb", 0),
            subtitles = prefs.getBoolean("set.subtitles", true),
            theme = runCatching { ThemeMode.valueOf(prefs.getString("set.theme", null)!!) }.getOrDefault(ThemeMode.System),
        ),
    )
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun updateSettings(new: AppSettings) {
        val dlnaChanged = new.dlna != _settings.value.dlna
        _settings.value = new
        prefs.edit()
            .putBoolean("set.resume", new.resume).putBoolean("set.autoNext", new.autoNext)
            .putBoolean("set.dlna", new.dlna).putBoolean("set.volumeKeys", new.volumeKeys)
            .putBoolean("set.autoConvert", new.autoConvert)
            .putBoolean("set.showSplash", new.showSplash).putString("set.theme", new.theme.name)
            .putInt("set.cacheLimitGb", new.cacheLimitGb).putBoolean("set.subtitles", new.subtitles)
            .commit() // commit: o MultiCastGateway lê "set.dlna" logo em seguida
        if (dlnaChanged) cast.startDiscovery()
    }

    fun setVolume(level: Float) = viewModelScope.launch { runCatching { cast.setVolume(level) }.onFailure(::toast) }

    /** Teclas de volume do celular → volume da TV. false = não está transmitindo (o celular trata a tecla). */
    fun nudgeVolume(up: Boolean): Boolean {
        val p = playback.value
        if (!_settings.value.volumeKeys || p.video == null) return false
        setVolume(((p.volume ?: 0.5f) + if (up) 0.05f else -0.05f).coerceIn(0f, 1f))
        return true
    }

    fun setSpeed(rate: Float) = viewModelScope.launch { runCatching { cast.setSpeed(rate) }.onFailure(::toast) }

    /** Quanto de cada vídeo já foi assistido (0..1), por chat:mensagem. Mostrado nas miniaturas e usado para continuar. */
    private val progressPrefs = app.getSharedPreferences(container.accountName("progress"), 0)
    private val _progress = MutableStateFlow(progressPrefs.all.mapNotNull { (k, v) -> (v as? Float)?.let { k to it } }.toMap())
    private fun keyOf(v: TgVideo) = "${v.chatId}:${v.messageId}"
    private fun withProgress(list: List<TgVideo>, p: Map<String, Float>) = list.map { it.copy(resumeFraction = p[keyOf(it)] ?: 0f) }
    private val _pinned = MutableStateFlow(accountPrefs.getStringSet("pinned", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet())
    val pinned: StateFlow<Set<Long>> = _pinned.asStateFlow()

    /** URL do vídeo aberto no player do celular (prévia), ou null. */
    private val _previewUrl = MutableStateFlow<String?>(null)
    val previewUrl: StateFlow<String?> = _previewUrl.asStateFlow()
    private var previewing: Pair<TgVideo, String>? = null
    private var previewSource: StreamSource? = null

    private val _chats = MutableStateFlow<List<TgChat>>(emptyList())
    val chats: StateFlow<List<TgChat>> = _chats.asStateFlow()

    private val _openChat = MutableStateFlow<TgChat?>(null)
    val openChat: StateFlow<TgChat?> = _openChat.asStateFlow()

    private val _videos = MutableStateFlow<List<TgVideo>>(emptyList())
    val videos: StateFlow<List<TgVideo>> = combine(_videos, _progress, ::withProgress)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private var openJob: Job? = null

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
        // Guarda o nome de quem entrou, para a lista de contas.
        viewModelScope.launch {
            auth.collect { a ->
                if (a is AuthState.Ready && a.userName.isNotBlank()) {
                    prefs.edit().putString("account.name.${container.accountId}", a.userName)
                        .putStringSet("accounts", _accounts.value.map { it.id }.toSet()).apply()
                    _accounts.value = loadAccounts()
                }
            }
        }
        // Vídeo terminou na TV → próximo da fila na mesma TV.
        viewModelScope.launch {
            cast.playback.map { it.status }.distinctUntilChanged().collect { status ->
                // só vídeo "termina": a imagem de "conectado" não pode puxar a fila
                if (status == PlayStatus.Ended && cast.playback.value.video != null) queueDevice?.let { advance(it, stopIfNothing = false) }
            }
        }
        viewModelScope.launch { cast.formatErrors.collect(::onFormatError) }
        // Guarda até onde cada vídeo foi assistido (a cada 1% e no fim).
        viewModelScope.launch {
            cast.playback.collect { p ->
                val v = p.video ?: return@collect
                val frac = when {
                    p.status == PlayStatus.Ended -> 1f
                    p.status == PlayStatus.Playing && v.durationSec > 0 -> (p.positionSec.toFloat() / v.durationSec).coerceIn(0f, 1f)
                    else -> return@collect
                }
                val key = keyOf(v)
                val old = _progress.value[key]
                if (old == null || abs(frac - old) >= 0.01f || (frac == 1f && old != 1f)) {
                    _progress.value += key to frac
                    progressPrefs.edit().putFloat(key, frac).apply()
                }
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
                queueDevice?.let { advance(it, stopIfNothing = true) } ?: stop()
            }
        }
    }

    val folders: StateFlow<List<VideoFolder>> = container.folders.folders
    private val _openFolderId = MutableStateFlow<String?>(null)
    val openFolder: StateFlow<VideoFolder?> = combine(folders, _openFolderId, _progress) { fs, id, p ->
        fs.firstOrNull { it.id == id }?.let { it.copy(videos = withProgress(it.videos, p)) }
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Texto do download de pasta em andamento ("Baixando 2 de 5…"), ou null. */
    private val _downloadStatus = container.transfers.download
    val downloadStatus: StateFlow<Transfer?> = _downloadStatus.asStateFlow()

    /** Roda [block] (um download do Telegram) atualizando a % a cada meio segundo. */
    private suspend fun <T> withDownloadProgress(video: TgVideo, onFraction: (Float) -> Unit, block: suspend () -> T): T = coroutineScope {
        val poll = launch { while (true) { onFraction(telegram.downloadedFraction(video)); delay(500) } }
        try { block() } finally { poll.cancel() }
    }

    fun showFolder(folder: VideoFolder) {
        _openChat.value = null
        _openFolderId.value = folder.id
    }

    private fun editFolder(id: String, change: (VideoFolder) -> VideoFolder?) =
        container.folders.update { fs -> fs.mapNotNull { if (it.id == id) change(it) else it } }

    /** Cria a pasta (e já põe [video] nela, se veio de um vídeo). */
    fun createFolder(name: String, videos: List<TgVideo>) =
        container.folders.update { it + VideoFolder(UUID.randomUUID().toString(), name.trim(), videos.distinctBy { v -> v.fileId }) }

    fun addToFolder(folderId: String, videos: List<TgVideo>) {
        editFolder(folderId) { f -> f.copy(videos = (f.videos + videos).distinctBy { it.fileId }) }
        Toast.makeText(getApplication(), if (videos.size == 1) "Adicionado à pasta." else "${videos.size} vídeos adicionados à pasta.", Toast.LENGTH_SHORT).show()
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
        addToFolder(toId, listOf(video))
    }

    private var downloadJob: Job?
        get() = container.transfers.downloadJob
        set(v) { container.transfers.downloadJob = v }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    /** Um vídeo por vez: terminou um, começa o próximo, até acabar a lista (ou o usuário cancelar). */
    private fun downloadAll(name: String, list: List<TgVideo>) {
        if (!container.realMode || downloadJob?.isActive == true) return
        val videos = list.distinctBy { it.fileId } // o mesmo arquivo pode estar em duas mensagens
        if (videos.isEmpty()) return
        // shortcut: o download vive no ViewModel; se o Android matar o app no meio, refaça (WorkManager resolveria).
        downloadJob = container.transfers.scope.launch { // sobrevive a fechar a tela
            var done = 0
            var current: TgVideo? = null
            try {
                videos.forEachIndexed { i, video ->
                    current = video
                    _downloadStatus.value = Transfer("Baixando ${i + 1} de ${videos.size}", video.title, 0f)
                    if (i == 0) TransferService.start(getApplication())
                    savedSource(video)?.let {
                        it.close()
                        done++
                        return@forEachIndexed // já está na galeria: não baixar de novo
                    }
                    runCatching {
                        val path = withDownloadProgress(video, { f -> _downloadStatus.update { it?.copy(fraction = f) } }) { telegram.download(video) }
                        val uri = withContext(Dispatchers.IO) { saveToDownloads(getApplication(), File(path), name, video.title, video.mimeType) }
                        accountPrefs.edit().putString(savedKey(video), uri).apply()
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

    fun enqueue(video: TgVideo) = enqueueAll(listOf(video))

    fun enqueueAll(videos: List<TgVideo>) {
        _queue.value += videos
        val what = if (videos.size == 1) "Adicionado" else "${videos.size} vídeos adicionados"
        Toast.makeText(getApplication(), "$what à fila (${_queue.value.size} no total).", Toast.LENGTH_SHORT).show()
    }

    /** Põe a fila em sequência pelo nome (vídeo 1, 2, 3…); [descending] inverte. */
    fun sortQueue(descending: Boolean) {
        _queue.value = _queue.value.sortedWith(if (descending) NaturalTitleOrder.reversed() else NaturalTitleOrder)
    }

    fun clearQueue() {
        _queue.value = emptyList()
    }

    fun downloadQueue() = downloadAll("Fila", _queue.value)

    /** Baixa os vídeos selecionados na Biblioteca para a pasta do chat/pasta aberta. */
    fun downloadVideos(videos: List<TgVideo>) =
        downloadAll(openFolder.value?.name ?: _openChat.value?.title ?: "Lumicast", videos)

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
        (queueDevice ?: cast.playback.value.device)?.let { advance(it, stopIfNothing = false) }
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

    /** Terminou um vídeo: próximo da fila; sem fila, próximo episódio (se ligado); senão para ou fica onde está. */
    private fun advance(device: CastDevice, stopIfNothing: Boolean) {
        if (_queue.value.isNotEmpty()) return playNext(device)
        val next = if (_settings.value.autoNext) nextEpisode() else null
        when {
            next != null -> {
                cast.playback.value.video?.let { history.addLast(it) }
                castTo(next, device)
            }
            _settings.value.showSplash -> showSplash(device) // acabou tudo: a TV volta a mostrar "conectado"
            stopIfNothing -> stop()
        }
    }

    /** Próximo vídeo pelo nome (ordem 1→9) na pasta ou no chat de onde veio o atual. */
    private fun nextEpisode(): TgVideo? {
        val current = cast.playback.value.video ?: return null
        val key = keyOf(current)
        val list = folders.value.firstOrNull { it.id == _openFolderId.value }?.videos?.takeIf { vs -> vs.any { keyOf(it) == key } }
            ?: _videos.value.takeIf { vs -> vs.any { keyOf(it) == key } }
            ?: return null
        val sorted = list.sortedWith(NaturalTitleOrder)
        return sorted.getOrNull(sorted.indexOfFirst { keyOf(it) == key } + 1)
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
    /** Nova conta: abre o app já na tela de login dela. */
    fun addAccount() {
        val next = ((_accounts.value.mapNotNull { it.id.toIntOrNull() }.maxOrNull() ?: 0) + 1).toString()
        switchAccount(next)
    }

    /** Troca de conta: fecha o Telegram com segurança e reabre o app limpo, já na outra conta. */
    fun switchAccount(id: String) = viewModelScope.launch {
        if (id == container.accountId) return@launch
        container.transfers.cancelAll()
        runCatching { cast.stop() }
        prefs.edit()
            .putString("account", id)
            .putStringSet("accounts", _accounts.value.map { it.id }.toSet() + id)
            .commit() // commit: o processo vai reiniciar logo abaixo
        telegram.close()
        val app = getApplication<Application>()
        app.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
            app.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        Runtime.getRuntime().exit(0) // tudo (Telegram, pastas, progresso) recomeça com os dados da outra conta
    }

    fun logout() = viewModelScope.launch { telegram.logout(); _chats.value = emptyList(); _openChat.value = null }

    fun loadChats() = viewModelScope.launch {
        _loading.value = true
        _chats.value = telegram.chats()
        _loading.value = false
    }

    /** Abre o chat e vai trazendo os vídeos página por página (a lista aparece já na primeira). */
    fun open(chat: TgChat) {
        openJob?.cancel()
        _openFolderId.value = null
        _openChat.value = chat
        _videos.value = emptyList()
        openJob = viewModelScope.launch {
            _loading.value = true
            var next = 0L
            runCatching {
                do {
                    val page = telegram.videos(chat.id, next)
                    _videos.value += page.videos
                    _loading.value = false
                    next = page.next
                } while (next != 0L && page.videos.isNotEmpty() && _videos.value.size < MAX_VIDEOS)
            }.onFailure { if (it is CancellationException) throw it else toast(it) }
            _loading.value = false
        }
    }

    fun closeChat() {
        openJob?.cancel()
        _openFolderId.value = null
        _openChat.value = null
        _videos.value = emptyList()
    }

    fun rescan() = cast.startDiscovery()

    private var server: RangeStreamServer? = null
    private var streaming: Pair<TgVideo, String>? = null
    private var streamSource: StreamSource? = null
    private var progressJob: Job? = null

    private fun savedKey(video: TgVideo) = "saved:${video.chatId}:${video.messageId}"

    /** Vídeo já salvo na galeria por este app: transmitir do arquivo, sem baixar do Telegram de novo. */
    private fun savedSource(video: TgVideo): StreamSource? {
        convertedSource(video)?.let { return it }
        val uri = accountPrefs.getString(savedKey(video), null) ?: return null
        val source = SavedVideoSource.open(getApplication(), uri, video.mimeType)
        if (source == null) accountPrefs.edit().remove(savedKey(video)).apply() // apagaram da galeria: volta a usar o Telegram
        return source
    }

    private fun convertedKey(video: TgVideo) = "conv:${video.chatId}:${video.messageId}"

    /** Versão MP4 já convertida deste vídeo (o Android pode apagar o cache: aí some e converte de novo se preciso). */
    private fun convertedSource(video: TgVideo): StreamSource? {
        val uri = accountPrefs.getString(convertedKey(video), null) ?: return null
        val source = SavedVideoSource.open(getApplication(), uri, "video/mp4")
        if (source == null) accountPrefs.edit().remove(convertedKey(video)).apply()
        return source
    }

    private val converter = VideoConverter(app)
    private var convertJob: Job?
        get() = container.transfers.convertJob
        set(v) { container.transfers.convertJob = v }

    /** "Convertendo para MP4: 40%", ou null. Mostrado na aba TV. */
    private val _conversion = container.transfers.conversion
    val conversion: StateFlow<Transfer?> = _conversion.asStateFlow()

    fun cancelConversion() {
        convertJob?.cancel()
    }

    /** A TV recusou o formato: converte para MP4 (se ligado) e transmite o convertido na mesma TV. */
    private fun onFormatError(video: TgVideo) {
        val device = queueDevice ?: return
        val msg = "A TV não aceitou o formato deste vídeo."
        when {
            !_settings.value.autoConvert -> Toast.makeText(getApplication(), "$msg Ligue \"Converter formato\" nas Configurações.", Toast.LENGTH_LONG).show()
            convertedSource(video)?.also { it.close() } != null ->
                Toast.makeText(getApplication(), "$msg Nem a versão convertida tocou nesta TV.", Toast.LENGTH_LONG).show()
            convertJob?.isActive == true -> Unit
            else -> convertJob = container.transfers.scope.launch { // sobrevive a fechar a tela
                Diag.log("convert", "file=${video.fileId} (${video.mimeType}) recusado pela TV: convertendo")
                try {
                    _conversion.value = Transfer("Baixando para converter", video.title, 0f)
                    TransferService.start(getApplication())
                    val input = accountPrefs.getString(savedKey(video), null)?.let(Uri::parse)
                        ?: Uri.fromFile(File(withDownloadProgress(video, { f -> _conversion.update { it?.copy(fraction = f) } }) { telegram.download(video) }))
                    val out = File(getApplication<Application>().cacheDir, "convertidos/${video.chatId}_${video.messageId}.mp4")
                    _conversion.value = Transfer("Convertendo para MP4", video.title, null)
                    converter.toMp4(input, out) { p -> _conversion.value = Transfer("Convertendo para MP4", video.title, p / 100f) }
                    accountPrefs.edit().putString(convertedKey(video), Uri.fromFile(out).toString()).apply()
                    Diag.log("convert", "file=${video.fileId} convertido (${out.length()} B)")
                    _conversion.value = null
                    convertJob = null // o castTo abaixo não deve cancelar a si mesmo
                    castTo(video, device)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Diag.log("convert", "falhou: ${e.stackTraceToString().take(600)}")
                    Toast.makeText(getApplication(), "Não deu para converter este vídeo (${e.message}).", Toast.LENGTH_LONG).show()
                } finally {
                    _conversion.value = null
                }
            }
        }
    }

    fun togglePin(chat: TgChat) {
        _pinned.value = if (chat.id in _pinned.value) _pinned.value - chat.id else _pinned.value + chat.id
        accountPrefs.edit().putStringSet("pinned", _pinned.value.map { it.toString() }.toSet()).apply()
    }

    suspend fun thumbnail(video: TgVideo): String? = runCatching { telegram.thumbnail(video) }.getOrNull()

    /** Abre o stream do Telegram no servidor local e devolve a URL (a TV e o player do celular usam a mesma). */
    private suspend fun ensureServer(): RangeStreamServer = server ?: run {
        val ip = findLanIPv4(getApplication()) ?: error("Sem Wi-Fi. Conecte o celular à mesma rede da TV.")
        RangeStreamServer(ip).also { it.start(); server = it }
    }

    /** Legenda do mesmo chat, no formato que a TV entende: VTT para Chromecast, SRT para DLNA. */
    private suspend fun subtitleUrl(video: TgVideo, device: CastDevice): String? {
        if (!_settings.value.subtitles) return null
        val text = runCatching { telegram.subtitleText(video) }.onFailure { Diag.log("legenda", "falhou: ${it.message}") }.getOrNull()
            ?: return null
        Diag.log("legenda", "file=${video.fileId}: legenda encontrada")
        val (body, mime) = if (device.protocol == CastProtocol.Dlna) text to "text/srt" else srtToVtt(text) to "text/vtt"
        return ensureServer().register(BytesSource(body.toByteArray(), "$mime; charset=utf-8"))
    }

    /** Miniatura do vídeo servida para a TV mostrar enquanto carrega (null se não houver). */
    private suspend fun thumbnailUrl(video: TgVideo): String? {
        val path = runCatching { telegram.thumbnail(video) }.getOrNull() ?: return null
        val source = SavedVideoSource.open(getApplication(), Uri.fromFile(File(path)).toString(), "image/jpeg") ?: return null
        return ensureServer().register(source)
    }

    private suspend fun serve(video: TgVideo): Pair<String, StreamSource> {
        val srv = ensureServer()
        val source = savedSource(video)?.also { Diag.log("vm", "file=${video.fileId}: usando a cópia salva no celular") }
            ?: telegram.openStream(video)
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
            val (url, source) = serve(video)
            previewing = video to url
            previewSource = source
            _previewUrl.value = url
        }.onFailure(::toast)
    }

    fun endPreview() = viewModelScope.launch {
        previewing?.let { (video, url) ->
            server?.unregister(url)
            if (streaming?.first?.fileId != video.fileId) telegram.closeStream(video) // a TV pode estar usando o mesmo arquivo
        }
        previewSource?.close()
        previewSource = null
        previewing = null
        _previewUrl.value = null
    }

    /** TDLib (download progressivo) → servidor HTTP local na LAN → TV. No modo demonstração, só simula. */
    fun castTo(original: TgVideo, device: CastDevice) = viewModelScope.launch {
        convertJob?.cancel() // escolheu outro vídeo: esquecer a conversão em andamento
        val start = _trimStart.value
        val saved = _progress.value[keyOf(original)]
        // Os gateways começam em duração × resumeFraction. Ordem: continuar de onde parou → "Começar em" → início.
        // +0,5 s porque os gateways truncam: 90 s não pode virar 89.
        val video = when {
            _settings.value.resume && saved != null && saved in 0.01f..0.95f -> original.copy(resumeFraction = saved)
            start in 1 until original.durationSec -> original.copy(resumeFraction = (start + 0.5f) / original.durationSec)
            else -> original.copy(resumeFraction = 0f)
        }
        queueDevice = device
        if (_selectedTvId.value != device.id) rememberTv(device)
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
            streamSource = source
            progressJob = viewModelScope.launch { while (true) { downloaded.value = source.downloadedFraction; delay(1_000) } }
            cast.play(
                device,
                video.copy(mimeType = source.mimeType, sizeBytes = source.sizeBytes),
                url,
                imageUrl = thumbnailUrl(video),
                subtitleUrl = subtitleUrl(video, device),
            )
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
        streamSource?.close()
        streamSource = null
        streaming = null
        applyCacheLimit()
    }

    private val _storage = MutableStateFlow<StorageInfo?>(null)
    val storage: StateFlow<StorageInfo?> = _storage.asStateFlow()

    private val convertedDir get() = File(getApplication<Application>().cacheDir, "convertidos")

    /** Nada usando o cache agora? (limpar no meio de uma transmissão ou download quebraria o vídeo) */
    private fun cacheIdle() = streaming == null && previewing == null && downloadJob?.isActive != true && convertJob?.isActive != true

    fun refreshStorage() = viewModelScope.launch {
        val converted = withContext(Dispatchers.IO) { convertedDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
        _storage.value = StorageInfo(runCatching { telegram.cacheBytes() }.getOrDefault(0), converted)
    }

    fun clearTelegramCache() = viewModelScope.launch {
        if (!cacheIdle()) {
            Toast.makeText(getApplication(), "Pare a transmissão e os downloads antes de limpar.", Toast.LENGTH_LONG).show()
            return@launch
        }
        runCatching { telegram.trimCache(0) }.onFailure(::toast)
        refreshStorage()
    }

    fun clearConverted() = viewModelScope.launch {
        if (convertJob?.isActive == true || streamSource is SavedVideoSource) {
            Toast.makeText(getApplication(), "Pare a transmissão e a conversão antes de apagar.", Toast.LENGTH_LONG).show()
            return@launch
        }
        withContext(Dispatchers.IO) { convertedDir.deleteRecursively() }
        accountPrefs.edit().apply { accountPrefs.all.keys.filter { it.startsWith("conv:") }.forEach(::remove) }.apply()
        refreshStorage()
    }

    /** "Limite do cache" das Configurações: apaga o excesso (os mais antigos primeiro) quando nada está usando. */
    private suspend fun applyCacheLimit() {
        val gb = _settings.value.cacheLimitGb
        if (gb <= 0 || !cacheIdle()) return
        runCatching { telegram.trimCache(gb * 1024L * 1024 * 1024) }.onFailure { Diag.log("storage", "limite falhou: ${it.message}") }
    }

    fun togglePause() = viewModelScope.launch { cast.togglePause() }
    fun seekTo(sec: Int) = viewModelScope.launch { cast.seekTo(sec) }
    fun stop() = viewModelScope.launch {
        convertJob?.cancel()
        switchJob?.cancel()
        queueDevice = null // parar à mão não puxa o próximo da fila
        cast.stop()
        endStream()
    }

    override fun onCleared() {
        server?.stop()
        cast.stopDiscovery()
    }

    private companion object {
        const val MAX_VIDEOS = 5_000 // canais enormes: o suficiente sem travar a lista
    }
}
