package app.lumicast

import android.app.Application
import android.content.Context
import app.lumicast.data.cast.FakeCastGateway
import app.lumicast.data.telegram.FakeTelegramGateway
import app.lumicast.data.telegram.TdlibTelegramGateway
import app.lumicast.domain.CastGateway
import app.lumicast.domain.TelegramGateway

/** Contêiner manual de dependências. No marco M1 isso vira módulos Hilt. */
class AppContainer(context: Context) {
    /** Sem api_id no build, o app abre em modo demonstração. */
    val telegram: TelegramGateway =
        if (BuildConfig.TELEGRAM_API_ID != 0) TdlibTelegramGateway(context) else FakeTelegramGateway()
    val cast: CastGateway = FakeCastGateway()
}

class LumicastApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
