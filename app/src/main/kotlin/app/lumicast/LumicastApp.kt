package app.lumicast

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import app.lumicast.data.cast.FakeCastGateway
import app.lumicast.data.cast.GoogleCastGateway
import app.lumicast.data.cast.MultiCastGateway
import app.lumicast.data.dlna.DlnaCastGateway
import app.lumicast.data.library.FolderStore
import app.lumicast.data.telegram.FakeTelegramGateway
import app.lumicast.data.telegram.TdlibTelegramGateway
import app.lumicast.domain.CastGateway
import app.lumicast.domain.TelegramGateway
import app.lumicast.domain.Transfer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/** Contêiner manual de dependências. No marco M1 isso vira módulos Hilt. */
class AppContainer(context: Context) {
    /** Configurações e TV ficam em comum; o resto (Telegram, pastas, progresso…) é de cada conta. */
    val globalPrefs: SharedPreferences = context.getSharedPreferences("lumicast", 0)
    val accountId: String = globalPrefs.getString("account", null) ?: "0"

    /** A conta 0 usa os nomes de antes: quem já usava o app não perde nada ao ganhar outras contas. */
    fun accountName(base: String) = if (accountId == "0") base else "$base-$accountId"
    val accountPrefs: SharedPreferences = context.getSharedPreferences(accountName("lumicast"), 0)

    /** Sem api_id no build, o app abre em modo demonstração (Telegram e TVs de mentira). */
    val realMode = BuildConfig.TELEGRAM_API_ID != 0
    val telegram: TelegramGateway = if (realMode) TdlibTelegramGateway(context, dbDirName = accountName("tdlib")) else FakeTelegramGateway()
    val cast: CastGateway = if (realMode) {
        val prefs = context.getSharedPreferences("lumicast", 0)
        MultiCastGateway(GoogleCastGateway(context), DlnaCastGateway(context), dlnaEnabled = { prefs.getBoolean("set.dlna", true) })
    } else {
        FakeCastGateway()
    }
    val folders = FolderStore(context, accountName("lumicast"))
    val transfers = Transfers()
}

/**
 * Downloads e conversão vivem no app, não na tela: continuam se você fechar o Lumicast (o TransferService mostra
 * a notificação e mantém o Android de pé). A tela só observa.
 */
class Transfers {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val download = MutableStateFlow<Transfer?>(null)
    val conversion = MutableStateFlow<Transfer?>(null)
    var downloadJob: Job? = null
    var convertJob: Job? = null

    fun cancelAll() {
        downloadJob?.cancel()
        convertJob?.cancel()
    }
}

class LumicastApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        Diag.install(this)
    }
}
