package app.lumicast

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumicast.domain.ThemeMode
import app.lumicast.ui.navigation.MainScreen
import app.lumicast.ui.theme.LumicastTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            LumicastTheme(darkTheme = dark) {
                MainScreen(vm)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val up = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        if ((up || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) && event.action == KeyEvent.ACTION_DOWN && vm.nudgeVolume(up)) return true
        return super.dispatchKeyEvent(event)
    }
}
