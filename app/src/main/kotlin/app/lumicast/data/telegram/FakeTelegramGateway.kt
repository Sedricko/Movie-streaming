package app.lumicast.data.telegram

import app.lumicast.domain.AuthState
import app.lumicast.domain.ChatKind
import app.lumicast.domain.StreamSource
import app.lumicast.domain.TelegramGateway
import app.lumicast.domain.TgChat
import app.lumicast.domain.TgVideo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Dados de demonstração. Substituído por TdlibTelegramGateway no marco M2. */
class FakeTelegramGateway : TelegramGateway {
    private val _auth = MutableStateFlow<AuthState>(AuthState.WaitPhone)
    override val authState: StateFlow<AuthState> = _auth.asStateFlow()

    override suspend fun submitPhone(phone: String) {
        _auth.value = AuthState.Loading
        delay(600)
        _auth.value = AuthState.WaitCode(phone)
    }

    override suspend fun submitCode(code: String) {
        _auth.value = AuthState.Loading
        delay(600)
        // Demo: o código "2fa" simula conta com senha em duas etapas.
        _auth.value = if (code.trim() == "2fa") AuthState.WaitPassword else AuthState.Ready("Ana")
    }

    override suspend fun submitPassword(password: String) {
        _auth.value = AuthState.Loading
        delay(400)
        _auth.value = AuthState.Ready("Ana")
    }

    override suspend fun logout() {
        _auth.value = AuthState.WaitPhone
    }

    override suspend fun chats(): List<TgChat> {
        delay(300)
        return listOf(
            TgChat(1, "Mensagens salvas", ChatKind.SavedMessages, 14),
            TgChat(2, "Cinema de quinta", ChatKind.Channel, 38),
            TgChat(3, "Documentários", ChatKind.Channel, 21),
            TgChat(4, "Família", ChatKind.Group, 9),
            TgChat(5, "Treinos em casa", ChatKind.Channel, 56),
        )
    }

    override suspend fun videos(chatId: Long): List<TgVideo> {
        delay(300)
        val mib = 1024L * 1024L
        return listOf(
            TgVideo(101, chatId, 1, "Viagem a Paraty - parte 1", 1_320, 820 * mib, "video/mp4", 0.45f),
            TgVideo(102, chatId, 2, "Aniversário da vovó", 2_710, 1_540 * mib, "video/mp4"),
            TgVideo(103, chatId, 3, "Show ao vivo (completo)", 5_880, 3_200 * mib, "video/x-matroska"),
            TgVideo(104, chatId, 4, "Receita de pão de queijo", 610, 96 * mib, "video/mp4", 1f),
        )
    }

    override suspend fun openStream(video: TgVideo): StreamSource =
        throw UnsupportedOperationException("Modo demonstração: sem streaming real (marco M2).")

    override suspend fun closeStream(video: TgVideo) = Unit

    override suspend fun thumbnail(video: TgVideo): String? = null
}
