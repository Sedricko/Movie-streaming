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
    val thumbnailFileId: Int? = null,
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

enum class PlayStatus { Idle, Connecting, Buffering, Playing, Paused,
    /** O vídeo chegou ao fim (a fila avança). */
    Ended,
    /** A conexão com a TV caiu e o app está tentando voltar sozinho. */
    Reconnecting }

data class PlaybackState(
    val video: TgVideo? = null,
    val device: CastDevice? = null,
    val status: PlayStatus = PlayStatus.Idle,
    val positionSec: Int = 0,
    /** Parte do arquivo já baixada do Telegram, 0f..1f. */
    val downloadedFraction: Float = 0f,
    /** Volume da TV 0f..1f, ou null se ainda não sabemos. */
    val volume: Float? = null,
    val speed: Float = 1f,
)

/** Pasta do Lumicast (só no aparelho; não mexe no Telegram). Os vídeos guardam o título que o usuário deu. */
data class VideoFolder(
    val id: String,
    val name: String,
    val videos: List<TgVideo> = emptyList(),
)

/** Uma página de vídeos de um chat; [next] = 0 quando acabou. */
data class VideoPage(val videos: List<TgVideo>, val next: Long)

/** Configurações do usuário: cada recurso automático pode ser desligado. */
data class AppSettings(
    /** Ao transmitir de novo, continuar do ponto onde parou. */
    val resume: Boolean = true,
    /** Sem fila, ao terminar um vídeo tocar o próximo do mesmo chat/pasta (ordem 1→9). */
    val autoNext: Boolean = true,
    /** Procurar também TVs DLNA (Samsung, LG, Sony…) além de Chromecast/Google TV. */
    val dlna: Boolean = true,
    /** Com o app aberto e transmitindo, as teclas de volume do celular mudam o volume da TV. */
    val volumeKeys: Boolean = true,
    /** Se a TV recusar o formato (MKV, HEVC, AC3…), converter para MP4 no celular e transmitir o convertido. */
    val autoConvert: Boolean = true,
    /** Ao escolher a TV (e quando a fila acaba), mostrar nela a tela "Lumicast conectado". */
    val showSplash: Boolean = true,
    val theme: ThemeMode = ThemeMode.System,
    /** Tamanho máximo do cache do Telegram em GB (0 = sem limite). O excesso é apagado ao parar de transmitir. */
    val cacheLimitGb: Int = 0,
)

/** Espaço usado pelo app (não conta os vídeos salvos na galeria, que são seus). */
data class StorageInfo(val telegramBytes: Long, val convertedBytes: Long)

enum class ThemeMode { System, Light, Dark }

/** Um download ou conversão em andamento, para a barra de progresso. [fraction] null = ainda sem porcentagem. */
data class Transfer(val title: String, val detail: String, val fraction: Float?)
