package app.lumicast.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * Fonte de bytes de um vídeo que pode ainda estar sendo baixado.
 * Implementações devem ESPERAR (com timeout) até que os bytes pedidos existam.
 */
interface StreamSource {
    val sizeBytes: Long
    val mimeType: String

    /** Lê até [length] bytes a partir de [offset] para [buffer]. Retorna o nº de bytes lidos, ou -1 no fim/erro. */
    suspend fun read(offset: Long, buffer: ByteArray, length: Int): Int
}

interface TelegramGateway {
    val authState: StateFlow<AuthState>
    suspend fun submitPhone(phone: String)
    suspend fun submitCode(code: String)
    suspend fun submitPassword(password: String)
    suspend fun logout()

    suspend fun chats(): List<TgChat>
    suspend fun videos(chatId: Long): List<TgVideo>

    /** Inicia o download progressivo e devolve a fonte que o servidor local vai servir. */
    suspend fun openStream(video: TgVideo): StreamSource
    suspend fun closeStream(video: TgVideo)
}

interface CastGateway {
    val devices: StateFlow<List<CastDevice>>
    val playback: StateFlow<PlaybackState>

    fun startDiscovery()
    fun stopDiscovery()

    suspend fun play(device: CastDevice, video: TgVideo, streamUrl: String)
    suspend fun togglePause()
    suspend fun seekTo(seconds: Int)
    suspend fun stop()
}
