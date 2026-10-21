package app.lumicast

import android.app.Application
import android.content.Context
import app.lumicast.data.cast.FakeCastGateway
import app.lumicast.data.cast.GoogleCastGateway
import app.lumicast.data.telegram.FakeTelegramGateway
import app.lumicast.data.telegram.TdlibTelegramGateway
import app.lumicast.domain.CastGateway
import app.lumicast.domain.TelegramGateway

/** Contêiner manual de dependências. No marco M1 isso vira módulos Hilt. */
class AppContainer(context: Context) {
    /** Sem api_id no build, o app abre em modo demonstração (Telegram e TVs de mentira). */
    val realMode = BuildConfig.TELEGRAM_API_ID != 0
    val telegram: TelegramGateway = if (realMode) TdlibTelegramGateway(context) else FakeTelegramGateway()
    val cast: CastGateway = if (realMode) GoogleCastGateway(context) else FakeCastGateway()
}

class LumicastApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
