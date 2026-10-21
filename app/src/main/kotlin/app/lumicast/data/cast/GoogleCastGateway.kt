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
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** O que está na TV agora, para reconectar se a conexão cair; null depois de parar ou terminar. */
    private var casting: Triple<CastDevice, TgVideo, String>? = null
    private var reconnectJob: Job? = null
    /** Já tocou desde o último load? Erro antes de tocar é formato que a TV não aceita, não queda de rede. */
    private var playedSinceLoad = false
    private val router = MediaRouter.getInstance(context)
    @Suppress("DEPRECATION") // a versão com Executor devolve Task; a síncrona basta aqui
    private val castContext = CastContext.getSharedInstance(context)
    private val selector = MediaRouteSelector.Builder()
        .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
        .build()

    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    override val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    private val _formatErrors = MutableSharedFlow<TgVideo>(extraBufferCapacity = 4)
    override val formatErrors: Flow<TgVideo> = _formatErrors

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
            if (status == PlayStatus.Playing) playedSinceLoad = true
            if (client.playerState == MediaStatus.PLAYER_STATE_IDLE && client.idleReason == MediaStatus.IDLE_REASON_ERROR) {
                if (playedSinceLoad) {
                    reconnect("a TV parou com erro") // ex.: Wi-Fi oscilou e a TV perdeu o stream
                    return
                }
                casting?.second?.let { _formatErrors.tryEmit(it) } // o ViewModel decide: converter ou avisar
                casting = null
            }
            if (status == PlayStatus.Ended) casting = null // terminou de verdade: nada a retomar
            _playback.update { it.copy(status = status) }
        }
    }

    private val progress = RemoteMediaClient.ProgressListener { positionMs, _ ->
        _playback.update { it.copy(positionSec = (positionMs / 1000).toInt()) }
    }

    /** Fica de olho na sessão o tempo todo (não só ao conectar) para perceber quedas. */
    private val sessionWatcher = object : SessionManagerListener<CastSession> {
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            if (casting == null) return
            Diag.log("cast", "sessão suspensa ($reason): o SDK tenta retomar sozinho")
            _playback.update { it.copy(status = PlayStatus.Reconnecting) }
        }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            if (casting == null) return
            Diag.log("cast", "sessão retomada")
            session.remoteMediaClient?.let { attach(it); it.requestStatus() }
        }
        override fun onSessionResumeFailed(session: CastSession, error: Int) = reconnect("não retomou a sessão ($error)")
        override fun onSessionEnded(session: CastSession, error: Int) {
            if (error != 0) reconnect("sessão caiu ($error)") // error 0 = encerrada de propósito (ex.: pela TV)
        }
        override fun onSessionStarted(session: CastSession, sessionId: String) = Unit
        override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
    }

    init {
        castContext.sessionManager.addSessionManagerListener(sessionWatcher, CastSession::class.java)
    }

    /** Tenta voltar para a mesma TV e retomar o vídeo onde parou, esperando cada vez mais entre tentativas. */
    private fun reconnect(reason: String) {
        val (device, video, url) = casting ?: return
        if (reconnectJob?.isActive == true) return
        Diag.log("cast", "reconectando: $reason")
        reconnectJob = scope.launch {
            val pos = _playback.value.positionSec
            val resume = if (video.durationSec > 0) video.copy(resumeFraction = (pos + 0.5f) / video.durationSec) else video
            for (wait in RETRY_DELAYS_MS) {
                _playback.update { it.copy(status = PlayStatus.Reconnecting) }
                delay(wait)
                val ok = runCatching { load(device, resume, url) }
                    .onFailure { Diag.log("cast", "tentativa de reconectar falhou: ${it.message}") }
                    .isSuccess
                if (ok) return@launch
            }
            Diag.log("cast", "desisti de reconectar")
            casting = null
            _playback.update { it.copy(status = PlayStatus.Idle) }
            Toast.makeText(appContext, "Não deu para reconectar à ${device.name}. Confira o Wi-Fi e transmita de novo.", Toast.LENGTH_LONG).show()
        }
    }

    private fun attach(client: RemoteMediaClient) {
        // a sessão é reaproveitada entre vídeos da fila: tirar antes de pôr, senão cada evento chega N vezes
        client.unregisterCallback(mediaCallback)
        client.removeProgressListener(progress)
        client.registerCallback(mediaCallback)
        client.addProgressListener(progress, 1_000)
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
        reconnectJob?.cancel() // o usuário escolheu outro vídeo: esquecer a reconexão pendente
        load(device, video, streamUrl)
    }

    private suspend fun load(device: CastDevice, video: TgVideo, streamUrl: String) = withContext(Dispatchers.Main) {
        casting = null // trocar de TV encerra a sessão anterior; isso não é queda
        playedSinceLoad = false
        val start = (video.durationSec * video.resumeFraction).toLong()
        _playback.value = PlaybackState(video, device, PlayStatus.Connecting, start.toInt())
        val session = connect(device)
        val client = session.remoteMediaClient ?: error("A TV não aceitou a conexão.")
        attach(client)
        val info = MediaInfo.Builder(streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(video.mimeType)
            .setStreamDuration(video.durationSec * 1000L)
            .setMetadata(MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply { putString(MediaMetadata.KEY_TITLE, video.title) })
            .build()
        client.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).setCurrentTime(start * 1000).build())
        _playback.update { it.copy(volume = session.volume.toFloat()) }
        casting = Triple(device, video, streamUrl)
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

    override suspend fun setVolume(level: Float) = withContext(Dispatchers.Main) {
        val v = level.coerceIn(0f, 1f)
        castContext.sessionManager.currentCastSession?.volume = v.toDouble()
        _playback.update { it.copy(volume = v) }
    }

    override suspend fun setSpeed(rate: Float) = withContext(Dispatchers.Main) {
        client()?.setPlaybackRate(rate.toDouble())
        _playback.update { it.copy(speed = rate) }
    }

    override suspend fun stop() = withContext(Dispatchers.Main) {
        casting = null // parada pedida: não reconectar
        reconnectJob?.cancel()
        client()?.run {
            unregisterCallback(mediaCallback)
            removeProgressListener(progress)
            stop()
        }
        castContext.sessionManager.endCurrentSession(true)
        _playback.value = PlaybackState()
    }

    private companion object {
        val RETRY_DELAYS_MS = listOf(2_000L, 4_000L, 8_000L, 15_000L, 30_000L)
    }
}
