package app.lumicast.data.cast

import app.lumicast.domain.CastDevice
import app.lumicast.domain.CastGateway
import app.lumicast.domain.CastProtocol
import app.lumicast.domain.PlaybackState
import app.lumicast.domain.TgVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Uma lista só de TVs (Google Cast + DLNA); cada comando vai para quem está tocando. */
class MultiCastGateway(
    private val google: CastGateway,
    private val dlna: CastGateway,
    /** Configuração "Procurar TVs DLNA". */
    private val dlnaEnabled: () -> Boolean,
) : CastGateway {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val active = MutableStateFlow(CastProtocol.GoogleCast)
    private val rescans = MutableStateFlow(0) // reavalia "DLNA ligado?" a cada nova busca

    override val devices: StateFlow<List<CastDevice>> =
        combine(google.devices, dlna.devices, rescans) { g, d, _ -> if (dlnaEnabled()) g + d else g }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val playback: StateFlow<PlaybackState> =
        combine(google.playback, dlna.playback, active) { g, d, a -> if (a == CastProtocol.Dlna) d else g }
            .stateIn(scope, SharingStarted.Eagerly, PlaybackState())

    private fun now() = if (active.value == CastProtocol.Dlna) dlna else google

    override fun startDiscovery() {
        rescans.value++
        google.startDiscovery()
        if (dlnaEnabled()) dlna.startDiscovery()
    }

    override fun stopDiscovery() {
        google.stopDiscovery()
        dlna.stopDiscovery()
    }

    override suspend fun play(device: CastDevice, video: TgVideo, streamUrl: String) {
        if (device.protocol != active.value) runCatching { now().stop() } // trocar de tipo de TV: parar na anterior
        active.value = device.protocol
        now().play(device, video, streamUrl)
    }

    override suspend fun togglePause() = now().togglePause()
    override suspend fun seekTo(seconds: Int) = now().seekTo(seconds)
    override suspend fun setVolume(level: Float) = now().setVolume(level)
    override suspend fun setSpeed(rate: Float) = now().setSpeed(rate)
    override suspend fun stop() = now().stop()
}
