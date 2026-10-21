package app.lumicast

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.lumicast.domain.Transfer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Notificação com progresso enquanto há download ou conversão; some sozinha quando tudo acaba. */
class TransferService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watch: Job? = null
    private val transfers get() = (application as LumicastApp).container.transfers

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) transfers.cancelAll()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(null), type)
        if (watch == null) {
            watch = scope.launch {
                combine(transfers.download, transfers.conversion) { d, c -> c ?: d }.collect { t ->
                    if (t == null) {
                        ServiceCompat.stopForeground(this@TransferService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(t))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
    }

    private fun notification(t: Transfer?) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle(t?.let { tr -> tr.title + (tr.fraction?.let { " · ${(it * 100).toInt()}%" } ?: "") } ?: "Preparando…")
        .setContentText(t?.detail)
        .setProgress(100, ((t?.fraction ?: 0f) * 100).toInt(), t?.fraction == null)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
        )
        .addAction(
            0, "Cancelar",
            PendingIntent.getService(this, 1, Intent(this, TransferService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE),
        )
        .build()

    companion object {
        private const val CHANNEL = "transfers"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_CANCEL = "app.lumicast.CANCEL_TRANSFER"

        fun start(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads e conversões", NotificationManager.IMPORTANCE_LOW))
            // Começado pelo toque do usuário funciona; se o Android recusar (app em segundo plano), segue sem notificação.
            runCatching { context.startForegroundService(Intent(context, TransferService::class.java)) }
                .onFailure { Diag.log("transfer", "notificação não iniciou: ${it.message}") }
        }
    }
}
