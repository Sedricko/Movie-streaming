package app.lumicast

import android.app.Application
import app.lumicast.data.cast.FakeCastGateway
import app.lumicast.data.telegram.FakeTelegramGateway
import app.lumicast.domain.CastGateway
import app.lumicast.domain.TelegramGateway

/** Contêiner manual de dependências. No marco M1 isso vira módulos Hilt. */
class AppContainer {
    val telegram: TelegramGateway = FakeTelegramGateway()
    val cast: CastGateway = FakeCastGateway()
}

class LumicastApp : Application() {
    val container: AppContainer by lazy { AppContainer() }
}
