package app.lumicast.data.cast

import android.content.Context
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import app.lumicast.Diag
import app.lumicast.MainActivity
import app.lumicast.domain.CastDevice
import app.lumicast.domain.CastGateway
import app.lumicast.domain.CastProtocol
import app.lumicast.domain.PlayStatus
import app.lumicast.domain.PlaybackState
import app.lumicast.domain.TgVideo
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Default Media Receiver + notificação de mídia do SDK (serviço em primeiro plano: mantém o app vivo com a tela bloqueada). */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions = CastOptions.Builder()
        .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
        .setCastMediaOptions(
            CastMediaOptions.Builder()
                .setNotificationOptions(NotificationOptions.Builder().setTargetActivityClassName(MainActivity::class.java.name).build())
                .build(),
        )
        .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

/** Google Cast (Chromecast/Google TV). Todas as chamadas do SDK rodam na thread principal. */
class GoogleCastGateway(context: Context) : CastGateway {
    private val router = MediaRouter.getInstance(context)
    @Suppress("DEPRECATION") // a versão com Executor devolve Task; a síncrona basta aqui
    private val castContext = CastContext.getSharedInstance(context)
    private val selector = MediaRouteSelector.Builder()
        .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
        .build()

    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    override val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    private val _playback = MutableStateFlow(PlaybackState())
    override val playback: StateFlow<PlaybackState> = _playback.asStateFlow()

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
    }

    private val mediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val client = client() ?: return
            val status = when (client.playerState) {
                MediaStatus.PLAYER_STATE_PLAYING -> PlayStatus.Playing
                MediaStatus.PLAYER_STATE_PAUSED -> PlayStatus.Paused
                MediaStatus.PLAYER_STATE_BUFFERING, MediaStatus.PLAYER_STATE_LOADING -> PlayStatus.Buffering
                else -> when (client.idleReason) {
                    MediaStatus.IDLE_REASON_NONE -> PlayStatus.Connecting
                    MediaStatus.IDLE_REASON_FINISHED -> PlayStatus.Ended
                    else -> PlayStatus.Idle
                }
            }
            Diag.log("cast", "estado=${client.playerState} motivo=${client.idleReason} -> $status")
            _playback.update { it.copy(status = status) }
        }
    }

    private val progress = RemoteMediaClient.ProgressListener { positionMs, _ ->
        _playback.update { it.copy(positionSec = (positionMs / 1000).toInt()) }
    }

    private fun client(): RemoteMediaClient? = castContext.sessionManager.currentCastSession?.remoteMediaClient

    private fun refresh() {
        _devices.value = router.routes
            .filter { !it.isDefault && it.matchesSelector(selector) && it.isEnabled }
            .map { CastDevice(it.id, it.name, CastProtocol.GoogleCast) }
    }

    override fun startDiscovery() {
        router.addCallback(selector, routerCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        refresh()
    }

    override fun stopDiscovery() = router.removeCallback(routerCallback)

    override suspend fun play(device: CastDevice, video: TgVideo, streamUrl: String) = withContext(Dispatchers.Main) {
        val start = (video.durationSec * video.resumeFraction).toLong()
        _playback.value = PlaybackState(video, device, PlayStatus.Connecting, start.toInt())
        val session = connect(device)
        val client = session.remoteMediaClient ?: error("A TV não aceitou a conexão.")
        // a sessão é reaproveitada entre vídeos da fila: tirar antes de pôr, senão cada evento chega N vezes
        client.unregisterCallback(mediaCallback)
        client.removeProgressListener(progress)
        client.registerCallback(mediaCallback)
        client.addProgressListener(progress, 1_000)
        val info = MediaInfo.Builder(streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(video.mimeType)
            .setStreamDuration(video.durationSec * 1000L)
            .setMetadata(MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply { putString(MediaMetadata.KEY_TITLE, video.title) })
            .build()
        client.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).setCurrentTime(start * 1000).build())
        Unit
    }

    /** Seleciona a rota da TV e espera a sessão Cast abrir (ou reaproveita a que já está aberta nela). */
    private suspend fun connect(device: CastDevice): CastSession {
        val current = castContext.sessionManager.currentCastSession
        if (current?.isConnected == true && router.selectedRoute.id == device.id) return current
        val route = router.routes.firstOrNull { it.id == device.id } ?: error("A TV saiu da rede. Procure de novo.")
        return withTimeout(20_000) {
            suspendCancellableCoroutine { cont ->
                val sessions = castContext.sessionManager
                val listener = object : SessionManagerListener<CastSession> {
                    fun done(result: Result<CastSession>) {
                        sessions.removeSessionManagerListener(this, CastSession::class.java)
                        if (cont.isActive) result.fold(cont::resume, cont::resumeWithException)
                    }
                    override fun onSessionStarted(session: CastSession, sessionId: String) = done(Result.success(session))
                    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = done(Result.success(session))
                    override fun onSessionStartFailed(session: CastSession, error: Int) {
                        Diag.log("cast", "sessão falhou: $error")
                        done(Result.failure(IllegalStateException("Não foi possível conectar à TV (código $error).")))
                    }
                    override fun onSessionStarting(session: CastSession) = Unit
                    override fun onSessionEnding(session: CastSession) = Unit
                    override fun onSessionEnded(session: CastSession, error: Int) = Unit
                    override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
                    override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
                    override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
                }
                sessions.addSessionManagerListener(listener, CastSession::class.java)
                cont.invokeOnCancellation { sessions.removeSessionManagerListener(listener, CastSession::class.java) }
                router.selectRoute(route)
            }
        }
    }

    override suspend fun togglePause() = withContext(Dispatchers.Main) {
        client()?.togglePlayback()
        Unit
    }

    override suspend fun seekTo(seconds: Int) = withContext(Dispatchers.Main) {
        client()?.seek(MediaSeekOptions.Builder().setPosition(seconds.coerceAtLeast(0) * 1000L).build())
        Unit
    }

    override suspend fun stop() = withContext(Dispatchers.Main) {
        client()?.run {
            unregisterCallback(mediaCallback)
            removeProgressListener(progress)
            stop()
        }
        castContext.sessionManager.endCurrentSession(true)
        _playback.value = PlaybackState()
    }
}
