package app.lumicast

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lumicast.data.library.SavedVideoSource
import app.lumicast.data.library.VideoConverter
import android.net.Uri
import app.lumicast.data.library.decodeFolders
import app.lumicast.data.library.deleteSaved
import app.lumicast.data.library.encodeFolders
import app.lumicast.data.library.findInDownloads
import app.lumicast.data.library.saveToDownloads
import app.lumicast.data.library.savedExists
import app.lumicast.data.cast.renderConnectedSplash
import app.lumicast.data.stream.BytesSource
import app.lumicast.domain.StorageInfo
import app.lumicast.domain.ThemeMode
import app.lumicast.data.stream.RangeStreamServer
import app.lumicast.data.stream.StreamActivity
import app.lumicast.data.stream.findLanIPv4
import app.lumicast.domain.Account
import app.lumicast.domain.AppSettings
import app.lumicast.domain.AuthState
import app.lumicast.domain.CastDevice
import app.lumicast.domain.NaturalTitleOrder
import app.lumicast.domain.PlayStatus
import app.lumicast.domain.QueueLogic
import app.lumicast.domain.TgChat
import app.lumicast.domain.TgVideo
import app.lumicast.domain.Transfer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.lumicast.domain.VideoFolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
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
import kotlinx.coroutines.flow.drop
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
    private var splashJob: Job? = null

    private fun showSplash(device: CastDevice) {
        if (!_settings.value.showSplash || !container.realMode) return
        splashJob?.cancel()
        splashJob = viewModelScope.launch {
            runCatching {
                if (castJob?.isActive == true) return@launch // escolher a TV e mandar um vídeo juntos: o vídeo vence, a imagem não pode trocá-lo
                endStream()
                val srv = ensureServer()
                splashUrl?.let(srv::unregister)
                val png = withContext(Dispatchers.Default) { renderConnectedSplash(getApplication(), device.name) }
                val url = srv.register(BytesSource(png, "image/png")).also { splashUrl = it }
                if (castJob?.isActive == true) return@launch
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
            prefetch = prefs.getBoolean("set.prefetch", true),
            cacheLimitGb = prefs.getInt("set.cacheLimitGb", 0),
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
            .putBoolean("set.autoConvert", new.autoConvert).putBoolean("set.prefetch", new.prefetch)
            .putBoolean("set.showSplash", new.showSplash).putString("set.theme", new.theme.name)
            .putInt("set.cacheLimitGb", new.cacheLimitGb)
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

    // A fila sobrevive a fechar o app: guardada como uma "pasta" no mesmo formato das pastas do usuário.
    private val _queue = MutableStateFlow(decodeFolders(accountPrefs.getString("queue", null)).firstOrNull()?.videos ?: emptyList())
    val queue: StateFlow<List<TgVideo>> = _queue.asStateFlow()

    /** Vídeos que este app já salvou em Downloads (a lista mostra "Baixado"). Vem das preferências, então sobrevive a fechar o app. */
    private val _savedKeys = MutableStateFlow(accountPrefs.all.keys.filter { it.startsWith("saved:") }.map { it.removePrefix("saved:") }.toSet())
    val savedKeys: StateFlow<Set<String>> = _savedKeys.asStateFlow()

    private fun rememberSaved(video: TgVideo, uri: String) {
        accountPrefs.edit().putString(savedKey(video), uri).apply()
        _savedKeys.update { it + keyOf(video) }
    }

    /** Vídeos da fila que já tocaram (a fila não os tira sozinha). O próximo a tocar é o primeiro que não está aqui. */
    private val _played = MutableStateFlow(accountPrefs.getStringSet("queuePlayed", emptySet())!!.toSet())
    val queuePlayed: StateFlow<Set<String>> = _played.asStateFlow()

    /** Marca "visto", em qualquer lista. O app marca ao passar de 90% do vídeo; o usuário põe e tira à mão. */
    private val _watched = MutableStateFlow(accountPrefs.getStringSet("watched", emptySet())!!.toSet())
    val watchedKeys: StateFlow<Set<String>> = _watched.asStateFlow()

    fun setWatched(videos: List<TgVideo>, watched: Boolean) {
        val keys = videos.map { keyOf(it) }.toSet()
        _watched.update { if (watched) it + keys else it - keys }
        accountPrefs.edit().putStringSet("watched", _watched.value).apply()
    }

    /** Tira da fila só os que já tocaram. */
    fun clearPlayed() {
        _queue.update { q -> QueueLogic.clearPlayed(q, _played.value) }
        _played.value = emptySet()
    }

    private fun hasPlayable() = QueueLogic.hasPlayable(_queue.value, _played.value)

    /**
     * Confere se os arquivos do histórico de downloads ainda existem e esquece os que sumiram (apagados pelo gerenciador de
     * arquivos, por exemplo). Sem isso o selo "Baixado" e a fila ficariam mentindo. Roda ao abrir o app e ao voltar para ele.
     */
    private var refreshing: Job? = null

    fun refreshSaved() {
        if (refreshing?.isActive == true) return // abrir o app chama duas vezes (init e onStart)
        refreshing = viewModelScope.launch { doRefreshSaved() }
    }

    private suspend fun doRefreshSaved() {
        val stale = withContext(Dispatchers.IO) {
            accountPrefs.all.filter { (k, v) -> k.startsWith("saved:") && v is String && !savedExists(getApplication(), v) }.keys.toList()
        }
        if (stale.isEmpty()) return
        accountPrefs.edit().apply { stale.forEach { remove(it) } }.apply()
        _savedKeys.update { it - stale.map { k -> k.removePrefix("saved:") }.toSet() }
        Diag.log("download", "${stale.size} download(s) do histórico não existem mais em Downloads: esquecidos")
    }

    /** Apaga de Downloads os vídeos salvos, só quando o usuário pede (confirmado na tela). O vídeo continua no Telegram. */
    fun deleteDownloads(videos: List<TgVideo>) = viewModelScope.launch {
        var ok = 0
        var failed = 0
        for (v in videos) {
            val uri = accountPrefs.getString(savedKey(v), null) ?: continue
            if (streaming?.first?.fileId == v.fileId) { failed++; continue } // está tocando agora: apagar derrubaria a transmissão
            val done = withContext(Dispatchers.IO) { runCatching { deleteSaved(getApplication(), uri) }.onFailure { Diag.log("download", "apagar falhou: ${it.message}") }.isSuccess }
            if (done) {
                accountPrefs.edit().remove(savedKey(v)).apply()
                _savedKeys.update { it - keyOf(v) }
                ok++
            } else failed++
        }
        val msg = buildString {
            if (ok > 0) append(if (ok == 1) "Download apagado." else "$ok downloads apagados.")
            if (failed > 0) append(" Não deu para apagar $failed (está tocando ou o Android não deixou).")
        }.trim()
        if (msg.isNotEmpty()) Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
    }

    init {
        refreshSaved()
        viewModelScope.launch {
            _played.drop(1).collect { accountPrefs.edit().putStringSet("queuePlayed", it).apply() }
        }
        viewModelScope.launch {
            _queue.drop(1).collect { accountPrefs.edit().putString("queue", encodeFolders(listOf(VideoFolder("queue", "queue", it)))).apply() }
        }
    }

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

    /** Guarda o vídeo para o "Anterior". Limite de 100 e sem repetir em sequência (toques rápidos): a lista crescia sem fim. */
    private fun pushHistory(video: TgVideo?) {
        if (video == null || history.lastOrNull()?.let { keyOf(it) == keyOf(video) } == true) return
        history.addLast(video)
        if (history.size > 100) history.removeFirst()
    }

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
                    if (frac >= 0.9f && (old ?: 0f) < 0.9f) setWatched(listOf(v), true) // só na passagem dos 90%: não refaz a marca que o usuário tirou
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
        val poll = launch {
            var last = -1f
            var since = System.nanoTime()
            while (true) {
                val f = telegram.downloadedFraction(video)
                if (f != last) { last = f; since = System.nanoTime() }
                // TDLib espera a rede voltar para sempre: sem este limite a barra congela e nada mais baixa (botão fica desligado)
                else if (System.nanoTime() - since > STALL_NS) error("Sem progresso há 90 s. Confira a internet e tente de novo.")
                onFraction(f)
                delay(500)
            }
        }
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
            var lastError: String? = null
            var current: TgVideo? = null
            try {
                videos.forEachIndexed { i, video ->
                    current = video
                    _downloadStatus.value = Transfer("Baixando ${i + 1} de ${videos.size}", video.title, 0f)
                    if (i == 0) TransferService.start(getApplication())
                    runCatching {
                        saveVideo(video, name) { f -> _downloadStatus.update { it?.copy(fraction = f) } }
                        done++
                    }.onFailure {
                        if (it is CancellationException) throw it
                        Diag.log("download", "falhou ${video.fileId}: ${it.message}")
                        lastError = it.message
                    }
                }
                val msg = if (done == videos.size) "Vídeos salvos em Downloads/Lumicast/$name."
                else "Baixados $done de ${videos.size}. ${lastError ?: "Confira a internet e o espaço livre e tente de novo."}"
                Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) {
                // parar o download no TDLib também, mas não se for o vídeo que está tocando ou em prévia (cortaria a transmissão)
                current?.takeIf { it.fileId != streaming?.first?.fileId && it.fileId != previewing?.first?.fileId }?.let { withContext(NonCancellable) { telegram.closeStream(it) } }
                Toast.makeText(getApplication(), "Download cancelado. $done de ${videos.size} já estão em Downloads/Lumicast/$name.", Toast.LENGTH_LONG).show()
                throw e
            } finally {
                _downloadStatus.value = null
            }
        }
    }

    /**
     * Garante o vídeo em Downloads/Lumicast/[folder] e só devolve com certeza: já salvo (e o arquivo abre), já está lá
     * (nome e tamanho) ou baixou e salvou agora. Qualquer falha lança exceção, e o vídeo segue como "não baixado".
     */
    private val saveLock = Mutex()

    /** Chave do vídeo que está sendo baixado agora (a fila mostra um spinner nele). */
    private val _savingKey = MutableStateFlow<String?>(null)
    val savingKey: StateFlow<String?> = _savingKey.asStateFlow()

    private suspend fun saveVideo(video: TgVideo, folder: String, onFraction: (Float) -> Unit = {}) {
        // Um de cada vez, na ordem em que chegam: botão de baixar e "baixar adiante" juntos não salvam o mesmo vídeo duas vezes ("(1)").
        saveLock.withLock {
            _savingKey.value = keyOf(video)
            try { doSaveVideo(video, folder, onFraction) } finally { _savingKey.value = null }
        }
    }

    private suspend fun doSaveVideo(video: TgVideo, folder: String, onFraction: (Float) -> Unit) {
        run {
            downloadedSource(video)?.let { it.close(); return } // só o arquivo em Downloads (o cache de convertidos não conta como baixado)
            // O app pode ter esquecido que salvou (reinstalou, limpou dados): confere o arquivo em Downloads antes de baixar.
            val existing = withContext(Dispatchers.IO) { runCatching { findInDownloads(getApplication(), folder, video.title, video.mimeType, video.sizeBytes) }.getOrNull() }
            if (existing != null) {
                rememberSaved(video, existing)
                Diag.log("download", "file=${video.fileId} já existe em Downloads/Lumicast/$folder: não baixou de novo")
                return
            }
            val path = withDownloadProgress(video, onFraction) { telegram.download(video) }
            val uri = withContext(Dispatchers.IO) { saveToDownloads(getApplication(), File(path), folder, video.title, video.mimeType) }
            rememberSaved(video, uri)
            if (streaming?.first?.fileId != video.fileId && previewing?.first?.fileId != video.fileId) telegram.releaseLocal(video)
        }
    }

    fun enqueue(video: TgVideo) = enqueueAll(listOf(video))

    fun enqueueAll(videos: List<TgVideo>) {
        // Mesmo vídeo duas vezes confundia o "já tocou" (a marca é por vídeo): só entra o que ainda não está na fila.
        val fresh = QueueLogic.fresh(_queue.value, videos)
        if (fresh.isEmpty()) {
            Toast.makeText(getApplication(), "Já está na fila.", Toast.LENGTH_SHORT).show()
            return
        }
        _queue.value += fresh
        startPrefetch() // fila nova com algo tocando: o laço pega os acrescentados
        val what = if (fresh.size == 1) "Adicionado" else "${fresh.size} vídeos adicionados"
        val skipped = if (fresh.size < videos.size) " ${videos.size - fresh.size} já estavam." else ""
        Toast.makeText(getApplication(), "$what à fila (${_queue.value.size} no total).$skipped", Toast.LENGTH_SHORT).show()
    }

    /** Põe a fila em sequência pelo nome (vídeo 1, 2, 3…); [descending] inverte. */
    fun sortQueue(descending: Boolean) {
        _queue.value = _queue.value.sortedWith(if (descending) NaturalTitleOrder.reversed() else NaturalTitleOrder)
    }

    fun clearQueue() {
        _queue.value = emptyList()
        _played.value = emptySet()
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
        val (items, played) = QueueLogic.remove(_queue.value, _played.value, index) ?: return
        _queue.value = items
        _played.value = played
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
        current?.let { c ->
            val (items, played) = QueueLogic.rewind(_queue.value, _played.value, c)
            _queue.value = items
            _played.value = played
        }
        castTo(prev, device)
    }

    /** Toca agora o item [index] da fila, inclusive um que já tocou. Os de antes ficam "já tocou"; ele e os de depois seguem a ordem. */
    fun playFromQueue(index: Int) {
        val q = _queue.value
        val video = q.getOrNull(index) ?: return
        val device = queueDevice ?: cast.playback.value.device ?: selectedTv.value ?: return
        _played.value = QueueLogic.playedUpTo(q, index)
        pushHistory(cast.playback.value.video)
        castTo(video, device)
    }

    fun setSwitchEvery(minutes: Int) {
        _switchEveryMin.value = minutes
    }

    /** Começa a fila na [device] agora ou no horário [atMillis]. */
    fun startQueue(device: CastDevice, atMillis: Long?) {
        if (_queue.value.isEmpty()) {
            Toast.makeText(getApplication(), "A fila está vazia. Na Biblioteca, toque num vídeo e escolha “Fila”.", Toast.LENGTH_LONG).show()
            return
        }
        scheduleJob?.cancel()
        _scheduledAt.value = atMillis
        // shortcut: o agendamento vive enquanto o app estiver aberto; usar AlarmManager se precisar sobreviver a fechar o app.
        scheduleJob = viewModelScope.launch {
            atMillis?.let { delay(it - System.currentTimeMillis()) }
            _scheduledAt.value = null
            if (!hasPlayable()) _played.value = emptySet() // só sobraram os que já tocaram: recomeça do primeiro
            playNext(device)
        }
    }

    fun cancelSchedule() {
        scheduleJob?.cancel()
        _scheduledAt.value = null
    }

    /** Terminou um vídeo: próximo da fila; sem fila, próximo episódio (se ligado); senão para ou fica onde está. */
    private fun advance(device: CastDevice, stopIfNothing: Boolean) {
        if (hasPlayable()) return playNext(device)
        val next = if (_settings.value.autoNext) nextEpisode() else null
        when {
            next != null -> {
                pushHistory(cast.playback.value.video)
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
        val next = QueueLogic.next(_queue.value, _played.value) ?: return
        _played.update { it + keyOf(next) } // fica na fila, marcado como "já tocou"; quem tira é o usuário (Limpar)
        pushHistory(cast.playback.value.video)
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

    fun logout() = viewModelScope.launch {
        telegram.logout(); _chats.value = emptyList(); _openChat.value = null
        _queue.value = emptyList(); _played.value = emptySet() // os fileId da fila só valem para a sessão que saiu
    }

    fun loadChats() = viewModelScope.launch {
        _loading.value = true
        runCatching { _chats.value = telegram.chats() }.onFailure { if (it !is CancellationException) toast(it) }
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
                    _videos.value = (_videos.value + page.videos).distinctBy { "${it.chatId}:${it.messageId}" }
                    _loading.value = false
                    next = page.next
                } while (next != 0L && page.videos.isNotEmpty() && _videos.value.size < MAX_VIDEOS)
            }.onFailure { if (it is CancellationException) throw it else toast(it) }
            _loading.value = false
        }
    }

    fun closeChat() {
        openJob?.cancel()
        _loading.value = false // cancelar a carga relança a cancelação e pulava o "false" lá em cima
        _openFolderId.value = null
        _openChat.value = null
        _videos.value = emptyList()
    }

    fun rescan() = cast.startDiscovery()

    // O servidor mora no AppContainer: fechar a tela (Voltar) não pode cortar a transmissão que a TV ainda está puxando.
    private var server: RangeStreamServer?
        get() = container.streamServer
        set(v) { container.streamServer = v }
    private var streaming: Pair<TgVideo, String>? = null
    private var streamSource: StreamSource? = null
    private var progressJob: Job? = null

    private fun savedKey(video: TgVideo) = "saved:${video.chatId}:${video.messageId}"

    /** Vídeo já salvo na galeria por este app: transmitir do arquivo, sem baixar do Telegram de novo. */
    private fun savedSource(video: TgVideo): StreamSource? {
        convertedSource(video)?.let { return it }
        val uri = accountPrefs.getString(savedKey(video), null) ?: return null
        val source = SavedVideoSource.open(getApplication(), uri, video.mimeType)
        if (source == null) { accountPrefs.edit().remove(savedKey(video)).apply(); _savedKeys.update { it - keyOf(video) } } // apagaram da galeria: volta a usar o Telegram
        return source
    }

    /** Só o arquivo salvo em Downloads por este app (sem o convertido do cache). Sumiu: esquece o registro. */
    private fun downloadedSource(video: TgVideo): StreamSource? {
        val uri = accountPrefs.getString(savedKey(video), null) ?: return null
        val source = SavedVideoSource.open(getApplication(), uri, video.mimeType)
        if (source == null) { accountPrefs.edit().remove(savedKey(video)).apply(); _savedKeys.update { it - keyOf(video) } }
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
        if (streaming?.first?.fileId != video.fileId) {
            Diag.log("convert", "file=${video.fileId}: erro da TV de um vídeo que já não é o atual (troca rápida), ignorado")
            return
        }
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
    private suspend fun ensureServer(): RangeStreamServer {
        val ip = findLanIPv4(getApplication()) ?: error("Sem Wi-Fi. Conecte o celular à mesma rede da TV.")
        server?.takeIf { it.host == ip }?.let { return it }
        server?.stop() // o IP mudou (Wi-Fi renovou ou trocou de rede): a URL antiga já não chega na TV
        return RangeStreamServer(ip).also { it.start(); server = it }
    }

    /** Miniatura do vídeo servida para a TV mostrar enquanto carrega (null se não houver). */
    private suspend fun thumbnailUrl(video: TgVideo): String? {
        val path = runCatching { telegram.thumbnail(video) }.getOrNull() ?: return null
        val source = SavedVideoSource.open(getApplication(), Uri.fromFile(File(path)).toString(), "image/jpeg") ?: return null
        val srv = ensureServer()
        thumbRegistered?.let(srv::unregister) // a anterior era registrada a cada vídeo e nunca saía (descritor de arquivo vazando)
        return srv.register(source).also { thumbRegistered = it }
    }

    private var thumbRegistered: String? = null

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
        if (t is CancellationException && t !is TimeoutCancellationException) return // fechar o app ou trocar de vídeo cancela tarefas: não é erro
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

    /**
     * Fecha a prévia NA HORA (sem esperar o Telegram): antes, o fechamento terminava depois de a prévia seguinte
     * abrir e fechava a nova junto, por isso "Ver no celular" só funcionava uma vez.
     */
    fun endPreview() {
        val old = previewing
        val source = previewSource
        previewing = null
        previewSource = null
        _previewUrl.value = null
        source?.close()
        old?.let { (video, url) ->
            server?.unregister(url)
            if (streaming?.first?.fileId != video.fileId) { // a TV pode estar usando o mesmo arquivo
                viewModelScope.launch { telegram.closeStream(video) }
            }
        }
    }

    /** TDLib (download progressivo) → servidor HTTP local na LAN → TV. No modo demonstração, só simula. */
    private var castJob: Job? = null

    fun castTo(original: TgVideo, device: CastDevice) {
        castJob?.cancel() // toques rápidos em Próximo/Anterior/fila: só o último vale (empilhar derrubava a TV)
        castJob = viewModelScope.launch { castToNow(original, device) }
    }

    private suspend fun castToNow(original: TgVideo, device: CastDevice) {
        splashJob?.cancel() // a tela "conectado" não pode chegar depois do vídeo
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
        if (every > 0 && hasPlayable()) {
            switchJob = viewModelScope.launch { delay(every * 60_000L); playNext(device) }
        }
        if (!container.realMode) {
            cast.play(device, video, streamUrl = "http://demo.invalid/stream")
            return
        }
        val t0 = System.nanoTime()
        runCatching {
            endStream()
            val (url, source) = serve(video)
            streaming = video to url
            streamSource = source
            progressJob = viewModelScope.launch { watchStream(source) }
            cast.play(device, video.copy(mimeType = source.mimeType, sizeBytes = source.sizeBytes), url, imageUrl = thumbnailUrl(video))
            Diag.metric("tv.ms_ate_enviar", (System.nanoTime() - t0) / 1_000_000) // clique → TV recebeu a URL
            Diag.log("vm", "transmitindo file=${video.fileId} (${video.mimeType}, ${video.sizeBytes} B) em ${device.protocol}")
            startPrefetch()
        }.onFailure {
            if (it is CancellationException && it !is TimeoutCancellationException) throw it // trocaram de vídeo: não mexer no stream do novo (o timeout é erro de verdade)
            endStream()
            runCatching { cast.stop() } // o gateway já tinha gravado "Conectando": sem isto o mini player ficava em loop
            _played.update { p -> p - keyOf(original) } // não chegou a tocar: não pular na fila
            toast(it)
        }
    }

    /**
     * Atualiza o progresso do download e vigia a transmissão: o celular e a notificação do Cast seguem mostrando
     * "tocando" mesmo quando a TV parou (download do Telegram sem dados, TV sem pedir bytes). Registra a causa e
     * tenta retomar com um seek na posição atual, que obriga a TV a pedir o trecho de novo.
     */
    private suspend fun watchStream(source: StreamSource) {
        var lastPos = -1
        var lastStatus: PlayStatus? = null
        var posSince = System.currentTimeMillis()
        var lastDl = -1f
        var dlSince = posSince
        var tries = 0
        while (true) {
            val now = System.currentTimeMillis()
            val dl = source.downloadedFraction
            downloaded.value = dl
            if (dl != lastDl) { lastDl = dl; dlSince = now }
            val p = cast.playback.value
            if (p.positionSec != lastPos || p.status != lastStatus) { lastPos = p.positionSec; lastStatus = p.status; posSince = now }
            val limit = if (p.status == PlayStatus.Playing) 10_000 else 20_000 // abrir um vídeo grande demora mais que travar no meio
            if ((p.status == PlayStatus.Playing || p.status == PlayStatus.Buffering) && now - posSince > limit) {
                val noData = dl < 1f && now - dlSince > limit
                val idle = StreamActivity.lastByteAt.let { if (it == 0L) -1L else now - it }
                Diag.metric("tv.paradas", 1)
                Diag.log(
                    "parada",
                    "TV ${p.status} sem avançar há ${(now - posSince) / 1000}s em ${p.positionSec}s · download ${(dl * 100).toInt()}% " +
                        "(${if (noData) "PARADO há ${(now - dlSince) / 1000}s" else "andando"}) · respostas abertas ${StreamActivity.active.get()} · " +
                        "último byte servido há ${if (idle < 0) "nunca" else "${idle / 1000}s"} · causa provável: " +
                        if (noData) "download do Telegram parado" else if (StreamActivity.active.get() == 0) "a TV não está pedindo dados" else "a TV está lendo devagar",
                )
                if (tries < 3) {
                    tries++
                    Diag.log("parada", "tentativa $tries de retomar: seek em ${p.positionSec}s")
                    runCatching { cast.seekTo(p.positionSec) }.onFailure { Diag.log("parada", "seek falhou: ${it.message}") }
                }
                posSince = now // próxima verificação só daqui a [limit]
            }
            delay(1_000)
        }
    }

    private var prefetchJob: Job? = null

    /**
     * Com a fila tocando, baixa os vídeos dela na ordem de transmissão, do próximo até o último, para cada um
     * começar pronto. Espera o vídeo em transmissão terminar de baixar (ele tem prioridade) e relê a fila a cada
     * passo: o que o usuário acrescenta ou tira entra na ordem. Salva em Downloads/Lumicast/Fila (o botão \"Baixado\" confere o arquivo).
     */
    private fun startPrefetch() {
        if (!container.realMode || !_settings.value.prefetch || prefetchJob?.isActive == true || streaming == null) return
        prefetchJob = viewModelScope.launch {
            val tried = HashSet<Int>()
            while (true) {
                // Espera o stream atual (prioridade) baixar, mas no máximo 60 s: ao retomar do meio ou após seek ele nunca completa o começo.
                var waited = 0
                while ((streamSource?.downloadedFraction ?: 1f) < 1f && waited < 60_000) { delay(2_000); waited += 2_000 }
                val video = _queue.value.firstOrNull { it.fileId !in tried && keyOf(it) !in _played.value } ?: break // adiante = o que ainda vai tocar
                tried += video.fileId
                val t0 = System.nanoTime()
                runCatching { saveVideo(video, "Fila") }
                    .onSuccess {
                        val ms = (System.nanoTime() - t0) / 1_000_000
                        Diag.metric("prefetch.ms", ms)
                        Diag.log("prefetch", "file=${video.fileId} baixado em ${ms / 1000}s (${tried.size} de ${tried.size + _queue.value.count { v -> v.fileId !in tried }} da fila)")
                    }
                    .onFailure {
                        if (it is CancellationException) throw it
                        Diag.log("prefetch", "falhou file=${video.fileId}: ${it.message}") // segue para o próximo; este baixa ao tocar
                    }
            }
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
    private fun cacheIdle() = streaming == null && previewing == null && prefetchJob?.isActive != true && downloadJob?.isActive != true && convertJob?.isActive != true

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

    // TV desligada ou Wi-Fi oscilando faz o gateway lançar exceção: sem o try, ela subia sem tratamento e derrubava o app.
    fun togglePause() = viewModelScope.launch { runCatching { cast.togglePause() }.onFailure(::toast) }
    fun seekTo(sec: Int) = viewModelScope.launch { runCatching { cast.seekTo(sec) }.onFailure(::toast) }
    fun stop() = viewModelScope.launch {
        castJob?.cancel() // parar durante o carregamento: o vídeo não pode começar depois do Parar
        scheduleJob?.cancel()
        _scheduledAt.value = null
        convertJob?.cancel()
        switchJob?.cancel()
        prefetchJob?.cancel()
        queueDevice = null // parar à mão não puxa o próximo da fila
        runCatching { cast.stop() }.onFailure(::toast)
        runCatching { endStream() }.onFailure { if (it !is CancellationException) Diag.log("erro", "endStream: ${it.message}") }
    }

    override fun onCleared() {
        if (streaming == null) { server?.stop(); server = null } // tocando: deixa o servidor vivo para a TV não perder o vídeo
        cast.stopDiscovery()
    }

    private companion object {
        const val STALL_NS = 90_000_000_000L
        const val MAX_VIDEOS = 5_000 // canais enormes: o suficiente sem travar a lista
    }
}
