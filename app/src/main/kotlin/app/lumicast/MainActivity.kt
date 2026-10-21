package app.lumicast

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
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
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Para o progresso de downloads/conversão aparecer na notificação. Pergunta uma vez; depois o usuário decide nas configurações do Android.
        val prefs = getSharedPreferences("lumicast", 0)
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("askedNotifications", false) &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            prefs.edit().putBoolean("askedNotifications", true).apply()
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
