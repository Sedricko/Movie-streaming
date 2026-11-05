package app.lumicast.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

private fun notificationsOk(c: Context) = Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
private fun nearbyOk(c: Context) = Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
private fun batteryOk(c: Context) = c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

/** Falta alguma permissão que ajuda o app a funcionar bem? (usado para abrir o pedido sozinho na primeira vez) */
fun missingPermissions(c: Context) = !notificationsOk(c) || !nearbyOk(c) || !batteryOk(c)

/**
 * Pede, uma de cada vez e com o motivo, as permissões que o app precisa para funcionar bem. Cada uma mostra se já está
 * permitida; se o Android não deixar mais perguntar, o botão leva às configurações do app.
 */
@Composable
fun PermissionsDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) } // reavalia depois de cada resposta e ao voltar das configurações
    val owner = ctx as? LifecycleOwner // a MainActivity é o dono do ciclo de vida
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
    var notifDenied by remember { mutableStateOf(false) }
    var nearbyDenied by remember { mutableStateOf(false) }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifDenied = !it; tick++ }
    val askNearby = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { nearbyDenied = !it; tick++ }
    val askBattery = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }
    fun appSettings() = ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    val notif = remember(tick) { notificationsOk(ctx) }
    val nearby = remember(tick) { nearbyOk(ctx) }
    val battery = remember(tick) { batteryOk(ctx) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissões do app") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PermissionRow(
                    "Notificações",
                    "Mostram o progresso dos downloads e deixam o app vivo com a tela bloqueada.",
                    notif,
                ) {
                    if (notifDenied) appSettings() else askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                PermissionRow(
                    "Dispositivos próximos",
                    "Para achar a TV na rede Wi-Fi (Chromecast e DLNA).",
                    nearby,
                ) {
                    if (nearbyDenied) appSettings() else askNearby.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                }
                PermissionRow(
                    "Bateria sem restrições",
                    "Sem isso o Android pode encerrar o app em segundo plano e a transmissão para.",
                    battery,
                ) {
                    runCatching { askBattery.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))) }
                        .onFailure { runCatching { askBattery.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (notif && nearby && battery) "Pronto" else "Agora não") } },
    )
}

@Composable
private fun PermissionRow(title: String, why: String, granted: Boolean, onAllow: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (granted) Text("Permitido", style = MaterialTheme.typography.labelMedium, color = app.lumicast.ui.theme.LumiTheme.extras.ok)
        else OutlinedButton(onClick = onAllow) { Text("Permitir") }
    }
}
