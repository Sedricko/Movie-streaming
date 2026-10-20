package app.lumicast

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lumicast.domain.CastDevice
import app.lumicast.domain.TgChat
import app.lumicast.domain.TgVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as LumicastApp).container
    private val telegram = container.telegram
    private val cast = container.cast

    val auth = telegram.authState
    val devices = cast.devices
    val playback = cast.playback

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

    /**
     * M3: aqui entram openStream() → RangeStreamServer.register() → cast.play(url real).
     * No modo demonstração, só simula.
     */
    fun castTo(video: TgVideo, device: CastDevice) = viewModelScope.launch {
        cast.play(device, video, streamUrl = "http://demo.invalid/stream")
    }

    fun togglePause() = viewModelScope.launch { cast.togglePause() }
    fun seekTo(sec: Int) = viewModelScope.launch { cast.seekTo(sec) }
    fun stop() = viewModelScope.launch { cast.stop() }
}
