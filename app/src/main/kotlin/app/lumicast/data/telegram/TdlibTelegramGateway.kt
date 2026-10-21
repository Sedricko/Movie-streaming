package app.lumicast.data.telegram

import android.content.Context
import android.os.Build
import app.lumicast.BuildConfig
import app.lumicast.Diag
import app.lumicast.domain.AuthState
import app.lumicast.domain.ChatKind
import app.lumicast.domain.StreamSource
import app.lumicast.domain.TelegramGateway
import app.lumicast.domain.TgChat
import app.lumicast.domain.TgVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** O download ativo em [downloadOffset] (com [prefix] bytes prontos) vai chegar a [offset] sem reiniciar? */
internal fun downloadReaches(offset: Long, active: Boolean, downloadOffset: Long, prefix: Long, window: Long = 16L * 1024 * 1024) =
    active && offset >= downloadOffset && offset - (downloadOffset + prefix) < window

class TdException(val error: TdApi.Error) : Exception(error.message)

/** Telegram real via TDLib (interface Java/JNI oficial). Veja docs/06-especificacao-tecnica.md. */
class TdlibTelegramGateway(context: Context) : TelegramGateway {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _auth = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _auth.asStateFlow()

    private var phone = ""
    private val files = ConcurrentHashMap<Int, TdApi.File>()
    private val fileUpdates = MutableSharedFlow<Int>(extraBufferCapacity = 64)

    @Volatile private var client: Client = newClient()

    private fun newClient(): Client {
        Client.execute(TdApi.SetLogVerbosityLevel(1))
        return Client.create({ onUpdate(it) }, null, null)
    }

    private suspend fun send(query: TdApi.Function<*>): TdApi.Object = suspendCancellableCoroutine { cont ->
        client.send(query) { result ->
            if (result is TdApi.Error) cont.resumeWithException(TdException(result)) else cont.resume(result)
        }
    }

    private fun onUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> onAuthState(update.authorizationState)
            is TdApi.UpdateFile -> {
                files[update.file.id] = update.file
                fileUpdates.tryEmit(update.file.id)
            }
        }
    }

    private fun onAuthState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> scope.launch { sendParameters() }
            is TdApi.AuthorizationStateWaitPhoneNumber -> _auth.value = AuthState.WaitPhone
            is TdApi.AuthorizationStateWaitCode -> _auth.value = AuthState.WaitCode(phone)
            is TdApi.AuthorizationStateWaitPassword -> _auth.value = AuthState.WaitPassword
            is TdApi.AuthorizationStateReady -> scope.launch {
                val name = runCatching { (send(TdApi.GetMe()) as TdApi.User).firstName }.getOrDefault("")
                _auth.value = AuthState.Ready(name)
            }
            is TdApi.AuthorizationStateClosed -> { // após sair, a instância morre: criar outra
                files.clear()
                client = newClient()
            }
            is TdApi.AuthorizationStateLoggingOut, is TdApi.AuthorizationStateClosing -> _auth.value = AuthState.Loading
            else -> _auth.value = AuthState.Failed("Esta conta precisa ser configurada no app oficial do Telegram.")
        }
    }

    private suspend fun sendParameters() {
        if (BuildConfig.TELEGRAM_API_ID == 0 || BuildConfig.TELEGRAM_API_HASH.isBlank()) {
            _auth.value = AuthState.Failed("App sem chave do Telegram. Defina TELEGRAM_API_ID e TELEGRAM_API_HASH no local.properties.")
            return
        }
        val dir = File(appContext.filesDir, "tdlib")
        val params = TdApi.SetTdlibParameters().apply {
            databaseDirectory = dir.absolutePath
            filesDirectory = File(dir, "files").absolutePath
            // shortcut: banco sem criptografia; chave no Android Keystore é o item de segurança do M5.
            databaseEncryptionKey = ByteArray(0)
            useFileDatabase = true
            useChatInfoDatabase = true
            useMessageDatabase = true
            apiId = BuildConfig.TELEGRAM_API_ID
            apiHash = BuildConfig.TELEGRAM_API_HASH
            systemLanguageCode = "pt-BR"
            deviceModel = Build.MODEL
            systemVersion = Build.VERSION.RELEASE
            applicationVersion = BuildConfig.VERSION_NAME
        }
        runCatching { send(params) }.onFailure { _auth.value = AuthState.Failed(friendly(it)) }
    }

    private suspend fun authStep(query: TdApi.Function<*>) {
        _auth.value = AuthState.Loading
        runCatching { send(query) }.onFailure { _auth.value = AuthState.Failed(friendly(it)) }
    }

    override suspend fun submitPhone(phone: String) {
        this.phone = phone.trim()
        authStep(TdApi.SetAuthenticationPhoneNumber(this.phone, null))
    }

    override suspend fun submitCode(code: String) = authStep(TdApi.CheckAuthenticationCode(code.trim()))

    override suspend fun resendCode() = authStep(TdApi.ResendAuthenticationCode(TdApi.ResendCodeReasonUserRequest()))

    override suspend fun submitPassword(password: String) = authStep(TdApi.CheckAuthenticationPassword(password))

    override suspend fun logout() = authStep(TdApi.LogOut())

    override suspend fun chats(): List<TgChat> {
        // LoadChats devolve 404 quando já carregou tudo: não é erro.
        runCatching { send(TdApi.LoadChats(TdApi.ChatListMain(), CHAT_LIMIT)) }
        val ids = (send(TdApi.GetChats(TdApi.ChatListMain(), CHAT_LIMIT)) as TdApi.Chats).chatIds
        val meId = runCatching { (send(TdApi.GetMe()) as TdApi.User).id }.getOrDefault(0L)
        return ids.toList().mapNotNull { id ->
            val chat = runCatching { send(TdApi.GetChat(id)) as TdApi.Chat }.getOrNull() ?: return@mapNotNull null
            // Conteúdo protegido ("proibir salvar") não é transmitido. Veja docs/05-riscos-e-conformidade.md.
            if (chat.hasProtectedContent) return@mapNotNull null
            when (val type = chat.type) {
                is TdApi.ChatTypePrivate ->
                    if (type.userId == meId) TgChat(id, "Mensagens salvas", ChatKind.SavedMessages)
                    else TgChat(id, chat.title, ChatKind.Private)
                is TdApi.ChatTypeSupergroup -> TgChat(id, chat.title, if (type.isChannel) ChatKind.Channel else ChatKind.Group)
                is TdApi.ChatTypeBasicGroup -> TgChat(id, chat.title, ChatKind.Group)
                else -> null
            }
        }.sortedBy { it.kind != ChatKind.SavedMessages }
    }

    override suspend fun videos(chatId: Long): List<TgVideo> {
        val query = TdApi.SearchChatMessages().apply {
            this.chatId = chatId
            this.query = ""
            filter = TdApi.SearchMessagesFilterVideo()
            fromMessageId = 0
            offset = 0
            limit = 50 // shortcut: só a primeira página; paginar por nextFromMessageId quando a UI tiver rolagem infinita.
        }
        val found = send(query) as TdApi.FoundChatMessages
        return found.messages.mapNotNull { m ->
            val content = m.content as? TdApi.MessageVideo ?: return@mapNotNull null
            val v = content.video
            files[v.video.id] = v.video
            val title = v.fileName.ifBlank { content.caption.text.lineSequence().firstOrNull().orEmpty() }.ifBlank { "Vídeo" }
            TgVideo(
                fileId = v.video.id,
                chatId = chatId,
                messageId = m.id,
                title = title,
                durationSec = v.duration,
                sizeBytes = if (v.video.size > 0) v.video.size else v.video.expectedSize,
                mimeType = v.mimeType.ifBlank { "video/mp4" },
                thumbnailFileId = v.thumbnail?.file?.id,
            )
        }
    }

    override suspend fun openStream(video: TgVideo): StreamSource {
        send(TdApi.DownloadFile(video.fileId, STREAM_PRIORITY, 0, 0, false))
        return TdlibStreamSource(video.fileId, video.sizeBytes, video.mimeType)
    }

    override suspend fun download(video: TgVideo): String {
        val file = send(TdApi.DownloadFile(video.fileId, 1, 0, 0, true)) as TdApi.File
        check(file.local.isDownloadingCompleted) { "O download de “${video.title}” não terminou. Tente de novo." }
        return file.local.path
    }

    override suspend fun releaseLocal(video: TgVideo) {
        runCatching { send(TdApi.DeleteFile(video.fileId)) }
    }

    override suspend fun thumbnail(video: TgVideo): String? {
        val id = video.thumbnailFileId ?: return null
        val file = runCatching { send(TdApi.DownloadFile(id, 1, 0, 0, true)) as TdApi.File }.getOrNull()
        return file?.local?.path?.takeIf { file.local.isDownloadingCompleted }
    }

    override suspend fun closeStream(video: TgVideo) {
        runCatching { send(TdApi.CancelDownloadFile(video.fileId, false)) }
    }

    /** Lê o arquivo parcial do TDLib; pede download a partir do offset quando a TV pula para um trecho ainda não baixado. */
    private inner class TdlibStreamSource(
        private val fileId: Int,
        override val sizeBytes: Long,
        override val mimeType: String,
    ) : StreamSource {
        @Volatile private var lastMoveAt = 0L

        override val downloadedFraction: Float
            get() = files[fileId]?.let { f -> if (sizeBytes > 0) f.local.downloadedSize.toFloat() / sizeBytes else 0f } ?: 0f

        /**
         * O download atual chega logo a [offset]? Reiniciar o download a cada leitura além do trecho baixado
         * (o que a TV faz o tempo todo) zera o progresso e trava a reprodução; só pulamos em seek de verdade.
         */
        private fun downloadReaches(offset: Long): Boolean {
            val local = files[fileId]?.local ?: return false
            return downloadReaches(offset, local.isDownloadingActive, local.downloadOffset, local.downloadedPrefixSize)
        }

        override suspend fun read(offset: Long, buffer: ByteArray, length: Int): Int {
            if (offset >= sizeBytes || length <= 0) return -1
            return withTimeoutOrNull(READ_TIMEOUT_MS) {
                while (true) {
                    val path = files[fileId]?.local?.path.orEmpty()
                    val ready = if (path.isEmpty()) 0L else prefixAt(offset)
                    if (ready > 0) {
                        val n = minOf(length.toLong(), ready).toInt()
                        return@withTimeoutOrNull withContext(Dispatchers.IO) {
                            RandomAccessFile(path, "r").use { it.seek(offset); it.read(buffer, 0, n) }
                        }
                    }
                    // A TV costuma ler começo e fim do arquivo em paralelo; o TDLib baixa um trecho por vez.
                    // Sem esse intervalo as duas conexões "puxam" o download de um lado para o outro e nada anda.
                    val now = System.currentTimeMillis()
                    if (!downloadReaches(offset) && now - lastMoveAt > MOVE_COOLDOWN_MS) {
                        lastMoveAt = now
                        Diag.log("stream", "file=$fileId: baixar a partir de $offset (seek ou download parado)")
                        runCatching { send(TdApi.DownloadFile(fileId, STREAM_PRIORITY, offset, 0, false)) }
                    }
                    withTimeoutOrNull(500) { fileUpdates.first { it == fileId } }
                }
                @Suppress("UNREACHABLE_CODE") -1
            } ?: (-1).also { Diag.log("stream", "file=$fileId: tempo esgotado esperando bytes em $offset") }
        }

        private suspend fun prefixAt(offset: Long): Long =
            runCatching { (send(TdApi.GetFileDownloadedPrefixSize(fileId, offset)) as TdApi.FileDownloadedPrefixSize).size }
                .getOrDefault(0L)
    }

    private fun friendly(t: Throwable): String {
        val msg = (t as? TdException)?.error?.message ?: t.message.orEmpty()
        Diag.log("telegram", "erro: $msg")
        val wait = Regex("""(?:FLOOD_WAIT_|retry after )(\d+)""").find(msg)?.groupValues?.get(1)
        return when {
            wait != null -> "Muitas tentativas. Aguarde $wait segundos e tente de novo."
            "PHONE_NUMBER_INVALID" in msg -> "Número inválido. Use o formato +55 11 91234-5678."
            "PHONE_CODE_INVALID" in msg || "PHONE_CODE_EXPIRED" in msg -> "Código incorreto ou expirado. Peça um novo código."
            "PASSWORD_HASH_INVALID" in msg -> "Senha incorreta. Tente de novo."
            else -> "Não foi possível falar com o Telegram ($msg). Confira a internet e tente de novo."
        }
    }

    private companion object {
        const val CHAT_LIMIT = 100
        const val STREAM_PRIORITY = 32
        const val READ_TIMEOUT_MS = 30_000L
        const val MOVE_COOLDOWN_MS = 1_000L
    }
}
