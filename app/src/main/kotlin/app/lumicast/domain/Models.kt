package app.lumicast.domain

enum class ChatKind { SavedMessages, Channel, Group, Private }

data class TgChat(
    val id: Long,
    val title: String,
    val kind: ChatKind,
    val videoCount: Int? = null,
)

data class TgVideo(
    val fileId: Int,
    val chatId: Long,
    val messageId: Long,
    val title: String,
    val durationSec: Int,
    val sizeBytes: Long,
    val mimeType: String,
    /** 0f..1f — quanto já foi assistido (para "retomar"). */
    val resumeFraction: Float = 0f,
)

enum class CastProtocol(val label: String) {
    GoogleCast("Google Cast"),
    Dlna("DLNA"),
}

data class CastDevice(
    val id: String,
    val name: String,
    val protocol: CastProtocol,
)

sealed interface AuthState {
    data object Loading : AuthState
    data object WaitPhone : AuthState
    data class WaitCode(val phone: String) : AuthState
    data object WaitPassword : AuthState
    data class Ready(val userName: String) : AuthState
    data class Failed(val message: String) : AuthState
}

enum class PlayStatus { Idle, Connecting, Buffering, Playing, Paused }

data class PlaybackState(
    val video: TgVideo? = null,
    val device: CastDevice? = null,
    val status: PlayStatus = PlayStatus.Idle,
    val positionSec: Int = 0,
    /** Parte do arquivo já baixada do Telegram, 0f..1f. */
    val downloadedFraction: Float = 0f,
)
