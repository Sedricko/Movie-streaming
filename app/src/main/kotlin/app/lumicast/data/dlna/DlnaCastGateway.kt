package app.lumicast.data.dlna

import android.content.Context
import android.net.wifi.WifiManager
import app.lumicast.Diag
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
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * TVs DLNA/UPnP (Samsung, LG, Sony…) sem biblioteca externa: descoberta por SSDP e controle por SOAP
 * (AVTransport para tocar/pausar/seek, RenderingControl para volume). A TV puxa o vídeo do nosso servidor local.
 */
class DlnaCastGateway(context: Context) : CastGateway {
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val renderers = ConcurrentHashMap<String, RendererInfo>()
    private var discoveryJob: Job? = null
    private var pollJob: Job? = null
    private var current: RendererInfo? = null

    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    override val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    private val _playback = MutableStateFlow(PlaybackState())
    override val playback: StateFlow<PlaybackState> = _playback.asStateFlow()

    override fun startDiscovery() {
        if (discoveryJob?.isActive == true) return
        discoveryJob = scope.launch {
            // Sem o MulticastLock muitos celulares descartam as respostas SSDP para economizar bateria.
            val lock = wifi?.createMulticastLock("lumicast-dlna")?.apply { setReferenceCounted(false); acquire() }
            try {
                search()
            } catch (e: Exception) {
                Diag.log("dlna", "busca falhou: ${e.message}")
            } finally {
                lock?.release()
            }
        }
    }

    override fun stopDiscovery() {
        discoveryJob?.cancel()
    }

    private suspend fun search() {
        val msg = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n" +
            "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n").toByteArray()
        val locations = mutableSetOf<String>()
        DatagramSocket().use { socket ->
            socket.soTimeout = 1_000
            val group = InetAddress.getByName("239.255.255.250")
            repeat(3) { socket.send(DatagramPacket(msg, msg.size, group, 1900)) } // UDP perde pacote: mandar 3 vezes
            val buf = ByteArray(2048)
            val until = System.currentTimeMillis() + 4_000
            while (System.currentTimeMillis() < until) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val text = String(packet.data, 0, packet.length)
                Regex("""(?im)^LOCATION:\s*(\S+)""").find(text)?.groupValues?.get(1)?.let(locations::add)
            }
        }
        for (location in locations) {
            val info = runCatching { parseRenderer(httpGet(location), location) }.getOrNull() ?: continue
            if (renderers.put(info.udn, info) == null) Diag.log("dlna", "TV encontrada: ${info.name}")
        }
        _devices.value = renderers.values.map { CastDevice(it.udn, it.name, CastProtocol.Dlna) }.sortedBy { it.name }
    }

    override suspend fun play(device: CastDevice, video: TgVideo, streamUrl: String, imageUrl: String?, subtitleUrl: String?) = withContext(Dispatchers.IO) {
        pollJob?.cancel()
        val r = renderers[device.id] ?: error("A TV saiu da rede. Procure de novo.")
        current = r
        val start = (video.durationSec * video.resumeFraction).toInt()
        _playback.value = PlaybackState(video, device, PlayStatus.Connecting, start)
        runCatching { av(r, "Stop") } // algumas TVs recusam trocar de vídeo sem parar o anterior
        av(
            r, "SetAVTransportURI",
            "CurrentURI" to streamUrl,
            "CurrentURIMetaData" to didl(video.title, streamUrl, video.mimeType, video.durationSec, video.sizeBytes, albumArt = imageUrl),
        )
        av(r, "Play", "Speed" to "1")
        Diag.log("dlna", "tocando em ${r.name}")
        pollJob = scope.launch { poll(r, video, start) }
    }

    override suspend fun showImage(device: CastDevice, imageUrl: String, title: String) = withContext(Dispatchers.IO) {
        pollJob?.cancel()
        val r = renderers[device.id] ?: error("A TV saiu da rede. Procure de novo.")
        current = r
        runCatching { av(r, "Stop") }
        av(r, "SetAVTransportURI", "CurrentURI" to imageUrl, "CurrentURIMetaData" to didl(title, imageUrl, "image/png", 0, 0, upnpClass = "object.item.imageItem.photo"))
        av(r, "Play", "Speed" to "1")
        _playback.value = PlaybackState(device = device, status = PlayStatus.Idle)
    }

    /** A cada 1 s pergunta à TV o estado e a posição (DLNA não avisa sozinho). */
    private suspend fun poll(r: RendererInfo, video: TgVideo, start: Int) {
        var played = false
        var seeked = start <= 0
        var failures = 0
        while (true) { // sai no fim do vídeo ou quando o job é cancelado (delay é ponto de cancelamento)
            delay(1_000)
            val state = runCatching { soapValue(av(r, "GetTransportInfo"), "CurrentTransportState") }
            val pos = runCatching { parseUpnpTime(soapValue(av(r, "GetPositionInfo"), "RelTime")) }.getOrNull()
            if (state.isFailure) {
                if (++failures == 5) Diag.log("dlna", "TV não responde: ${state.exceptionOrNull()?.message}")
                _playback.update { it.copy(status = if (failures >= 3) PlayStatus.Reconnecting else it.status) }
                continue
            }
            failures = 0
            val status = when (state.getOrNull()) {
                "PLAYING" -> PlayStatus.Playing.also { played = true }
                "PAUSED_PLAYBACK" -> PlayStatus.Paused
                "TRANSITIONING" -> PlayStatus.Buffering
                // Parou depois de tocar perto do fim = terminou; antes de tocar = ainda carregando.
                "STOPPED", "NO_MEDIA_PRESENT" -> when {
                    !played -> PlayStatus.Connecting
                    video.durationSec - _playback.value.positionSec <= 10 -> PlayStatus.Ended
                    else -> PlayStatus.Idle
                }
                else -> _playback.value.status
            }
            if (status == PlayStatus.Playing && !seeked) { // DLNA não aceita "começar em" no Play: seek depois que toca
                seeked = true
                runCatching { av(r, "Seek", "Unit" to "REL_TIME", "Target" to upnpTime(start)) }
            }
            _playback.update { it.copy(status = status, positionSec = pos ?: it.positionSec) }
            if (status == PlayStatus.Ended || status == PlayStatus.Idle) return
        }
    }

    override suspend fun togglePause() = withContext(Dispatchers.IO) {
        val r = current ?: return@withContext
        if (_playback.value.status == PlayStatus.Playing) av(r, "Pause") else av(r, "Play", "Speed" to "1")
        Unit
    }

    override suspend fun seekTo(seconds: Int) = withContext(Dispatchers.IO) {
        val r = current ?: return@withContext
        av(r, "Seek", "Unit" to "REL_TIME", "Target" to upnpTime(seconds))
        _playback.update { it.copy(positionSec = seconds) }
    }

    override suspend fun setVolume(level: Float) = withContext(Dispatchers.IO) {
        val r = current ?: return@withContext
        val url = r.renderingUrl ?: throw UnsupportedOperationException("Esta TV não deixa mudar o volume pelo celular.")
        val v = (level.coerceIn(0f, 1f) * 100).toInt()
        soap(url, r.renderingType!!, "SetVolume", "InstanceID" to "0", "Channel" to "Master", "DesiredVolume" to v.toString())
        _playback.update { it.copy(volume = level.coerceIn(0f, 1f)) }
    }

    override suspend fun setSpeed(rate: Float) {
        throw UnsupportedOperationException("TVs DLNA não deixam mudar a velocidade.")
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        pollJob?.cancel()
        current?.let { r -> runCatching { av(r, "Stop") } }
        current = null
        _playback.value = PlaybackState()
    }

    private fun av(r: RendererInfo, action: String, vararg args: Pair<String, String>) =
        soap(r.avTransportUrl, r.avTransportType, action, "InstanceID" to "0", *args)

    private fun soap(url: String, service: String, action: String, vararg args: Pair<String, String>): String {
        val body = """<?xml version="1.0" encoding="utf-8"?>""" +
            """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""" +
            """<s:Body><u:$action xmlns:u="$service">""" +
            args.joinToString("") { (k, v) -> "<$k>${escapeXml(v)}</$k>" } +
            "</u:$action></s:Body></s:Envelope>"
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 3_000
            conn.readTimeout = 5_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            conn.setRequestProperty("SOAPAction", "\"$service#$action\"")
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode != 200) {
                val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                throw IOException("$action recusado pela TV (HTTP ${conn.responseCode}) ${soapValue(err, "errorDescription").orEmpty()}")
            }
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 3_000
            conn.readTimeout = 3_000
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }
}
