package app.lumicast.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Fonte de bytes de um vídeo que pode ainda estar sendo baixado.
 * Implementações devem ESPERAR (com timeout) até que os bytes pedidos existam.
 */
interface StreamSource {
    val sizeBytes: Long
    val mimeType: String

    /** Parte do arquivo já baixada, 0f..1f. */
    val downloadedFraction: Float get() = 1f

    /** Libera o arquivo quando a transmissão termina. */
    fun close() {}

    /** Lê até [length] bytes a partir de [offset] para [buffer]. Retorna o nº de bytes lidos, ou -1 no fim/erro. */
    suspend fun read(offset: Long, buffer: ByteArray, length: Int): Int
}

interface TelegramGateway {
    val authState: StateFlow<AuthState>
    suspend fun submitPhone(phone: String)
    suspend fun submitCode(code: String)
    /** Pede ao Telegram para mandar o código de novo (pode chegar por SMS ou ligação). */
    suspend fun resendCode()
    suspend fun submitPassword(password: String)
    suspend fun logout()

    suspend fun chats(): List<TgChat>
    /** Vídeos do chat em páginas: comece com [fromMessageId] = 0 e siga com [VideoPage.next]. */
    suspend fun videos(chatId: Long, fromMessageId: Long = 0): VideoPage

    /** Inicia o download progressivo e devolve a fonte que o servidor local vai servir. */
    suspend fun openStream(video: TgVideo): StreamSource
    suspend fun closeStream(video: TgVideo)

    /** Baixa o vídeo inteiro e devolve o caminho local (cache do Telegram). */
    suspend fun download(video: TgVideo): String

    /** Apaga a cópia do cache do Telegram (depois de salvar na galeria, para não ocupar o dobro). */
    suspend fun releaseLocal(video: TgVideo)

    /** Caminho local da miniatura (baixa se preciso), ou null se o vídeo não tem. */
    suspend fun thumbnail(video: TgVideo): String?
}

interface CastGateway {
    val devices: StateFlow<List<CastDevice>>
    val playback: StateFlow<PlaybackState>

    fun startDiscovery()
    fun stopDiscovery()

    suspend fun play(device: CastDevice, video: TgVideo, streamUrl: String)
    suspend fun togglePause()
    suspend fun seekTo(seconds: Int)

    /** A TV recusou o vídeo antes de começar a tocar (formato/codec que ela não aceita). */
    val formatErrors: Flow<TgVideo> get() = emptyFlow()

    /** Volume da TV, 0f..1f. Pode lançar UnsupportedOperationException se a TV não deixar. */
    suspend fun setVolume(level: Float)

    /** Velocidade (1f = normal). Pode lançar UnsupportedOperationException se a TV não deixar. */
    suspend fun setSpeed(rate: Float)
    suspend fun stop()
}
