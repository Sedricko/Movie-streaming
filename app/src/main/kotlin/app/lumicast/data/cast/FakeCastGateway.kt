package app.lumicast.data.cast

import app.lumicast.domain.CastDevice
import app.lumicast.domain.CastGateway
import app.lumicast.domain.CastProtocol
import app.lumicast.domain.PlayStatus
import app.lumicast.domain.PlaybackState
import app.lumicast.domain.TgVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** TVs e reprodução de mentira. Substituído por GoogleCastGateway (M3) e DlnaCastGateway (M4). */
class FakeCastGateway : CastGateway {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    override val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    private val _playback = MutableStateFlow(PlaybackState())
    override val playback: StateFlow<PlaybackState> = _playback.asStateFlow()

    override fun startDiscovery() {
        scope.launch {
            _devices.value = emptyList()
            delay(900)
            _devices.value = listOf(CastDevice("tv-sala", "TV da sala", CastProtocol.GoogleCast))
            delay(700)
            _devices.update { it + CastDevice("tv-quarto", "TV do quarto", CastProtocol.Dlna) }
        }
    }

    override fun stopDiscovery() = Unit

    override suspend fun play(device: CastDevice, video: TgVideo, streamUrl: String, imageUrl: String?, subtitleUrl: String?) {
        ticker?.cancel()
        val start = (video.durationSec * video.resumeFraction).toInt()
        _playback.value = PlaybackState(video, device, PlayStatus.Connecting, start, 0.05f, volume = 0.5f)
        delay(800)
        _playback.update { it.copy(status = PlayStatus.Buffering) }
        delay(1_200)
        _playback.update { it.copy(status = PlayStatus.Playing) }
        ticker = scope.launch {
            while (true) {
                delay(1_000)
                _playback.update { s ->
                    if (s.status != PlayStatus.Playing) s
                    else s.copy(
                        status = if (s.positionSec + 1 >= video.durationSec) PlayStatus.Ended else s.status,
                        positionSec = (s.positionSec + 1).coerceAtMost(video.durationSec),
                        downloadedFraction = (s.downloadedFraction + 0.004f).coerceAtMost(1f),
                    )
                }
            }
        }
    }

    override suspend fun showImage(device: CastDevice, imageUrl: String, title: String) {
        ticker?.cancel()
        _playback.value = PlaybackState(device = device, volume = 0.5f)
    }

    override suspend fun togglePause() {
        _playback.update {
            when (it.status) {
                PlayStatus.Playing -> it.copy(status = PlayStatus.Paused)
                PlayStatus.Paused -> it.copy(status = PlayStatus.Playing)
                else -> it
            }
        }
    }

    override suspend fun seekTo(seconds: Int) {
        _playback.update { it.copy(positionSec = seconds.coerceAtLeast(0)) }
    }

    override suspend fun setVolume(level: Float) {
        _playback.update { it.copy(volume = level.coerceIn(0f, 1f)) }
    }

    override suspend fun setSpeed(rate: Float) {
        _playback.update { it.copy(speed = rate) }
    }

    override suspend fun stop() {
        ticker?.cancel()
        _playback.value = PlaybackState()
    }
}
