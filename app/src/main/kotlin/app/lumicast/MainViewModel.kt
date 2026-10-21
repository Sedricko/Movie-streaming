package app.lumicast

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lumicast.data.stream.RangeStreamServer
import app.lumicast.data.stream.findLanIPv4
import app.lumicast.domain.CastDevice
import app.lumicast.domain.TgChat
import app.lumicast.domain.TgVideo
import app.lumicast.domain.StreamSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    init {
        cast.startDiscovery()
    }

    fun submitPhone(phone: String) = viewModelScope.launch { telegram.submitPhone(phone) }
    fun submitCode(code: String) = viewModelScope.launch { telegram.submitCode(code) }
    fun submitPassword(password: String) = viewModelScope.launch { telegram.submitPassword(password) }
    fun logout() = viewModelScope.launch { telegram.logout(); _chats.value = emptyList(); _openChat.value = null }

    fun loadChats() = viewModelScope.launch {
        _loading.value = true
        _chats.value = telegram.chats()
        _loading.value = false
    }

    fun open(chat: TgChat) = viewModelScope.launch {
        _openChat.value = chat
        _loading.value = true
        _videos.value = telegram.videos(chat.id)
        _loading.value = false
    }

    fun closeChat() {
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

    private fun toast(t: Throwable) =
        Toast.makeText(getApplication(), t.message ?: "Não foi possível abrir o vídeo. Tente de novo.", Toast.LENGTH_LONG).show()

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
    fun castTo(video: TgVideo, device: CastDevice) = viewModelScope.launch {
        if (!container.realMode) {
            cast.play(device, video, streamUrl = "http://demo.invalid/stream")
            return@launch
        }
        runCatching {
            endStream()
            val (url, source) = serve(video)
            streaming = video to url
            progressJob = launch { while (true) { downloaded.value = source.downloadedFraction; delay(1_000) } }
            cast.play(device, video, url)
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
    fun stop() = viewModelScope.launch { cast.stop(); endStream() }

    override fun onCleared() {
        server?.stop()
        cast.stopDiscovery()
    }
}
