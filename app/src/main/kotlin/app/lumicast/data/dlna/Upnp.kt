package app.lumicast.data.dlna

import java.net.URL
import java.util.Locale

/** O que interessa da descrição UPnP de uma TV (MediaRenderer). */
internal data class RendererInfo(
    val udn: String,
    val name: String,
    val avTransportType: String,
    val avTransportUrl: String,
    val renderingType: String?,
    val renderingUrl: String?,
)

private val SERVICE = Regex("""<service>(.*?)</service>""", RegexOption.DOT_MATCHES_ALL)

private fun tag(xml: String, name: String): String? =
    Regex("""<(?:\w+:)?$name\b[^>]*>(.*?)</(?:\w+:)?$name>""", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.trim()

/** Lê a descrição (device.xml); null se não for uma TV que aceita AVTransport. URLs relativas viram absolutas. */
internal fun parseRenderer(xml: String, location: String): RendererInfo? {
    val base = tag(xml, "URLBase")?.takeIf { it.isNotBlank() } ?: location
    fun abs(u: String) = URL(URL(base), u).toString()
    var av: Pair<String, String>? = null
    var rc: Pair<String, String>? = null
    SERVICE.findAll(xml).forEach { m ->
        val block = m.groupValues[1]
        val type = tag(block, "serviceType") ?: return@forEach
        val control = tag(block, "controlURL") ?: return@forEach
        when {
            ":service:AVTransport:" in type && av == null -> av = type to abs(control)
            ":service:RenderingControl:" in type && rc == null -> rc = type to abs(control)
        }
    }
    val transport = av ?: return null
    return RendererInfo(
        udn = tag(xml, "UDN") ?: location,
        name = unescapeXml(tag(xml, "friendlyName") ?: "TV DLNA"),
        avTransportType = transport.first,
        avTransportUrl = transport.second,
        renderingType = rc?.first,
        renderingUrl = rc?.second,
    )
}

/** Valor de uma tag na resposta SOAP (ex.: RelTime, CurrentTransportState). */
internal fun soapValue(xml: String, name: String): String? = tag(xml, name)?.let(::unescapeXml)

/** "0:01:23", "00:01:23.500" → 83. Valores que a TV não sabe ("NOT_IMPLEMENTED") → null. */
internal fun parseUpnpTime(text: String?): Int? {
    val parts = text?.trim()?.substringBefore('.')?.split(':') ?: return null
    if (parts.size != 3) return null
    val (h, m, s) = parts.map { it.toIntOrNull() ?: return null }
    return h * 3600 + m * 60 + s
}

internal fun upnpTime(totalSec: Int): String {
    val s = totalSec.coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

internal fun escapeXml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

internal fun unescapeXml(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

/** Metadados DIDL-Lite que muitas TVs (Samsung, LG) exigem junto com a URL. */
internal fun didl(
    title: String,
    url: String,
    mime: String,
    durationSec: Int,
    size: Long,
    upnpClass: String = "object.item.videoItem",
    albumArt: String? = null,
    subtitle: String? = null,
): String {
    val duration = if (durationSec > 0) """ duration="${upnpTime(durationSec)}.000"""" else ""
    val sizeAttr = if (size > 0) """ size="$size"""" else ""
    val art = albumArt?.let { "<upnp:albumArtURI>${escapeXml(it)}</upnp:albumArtURI>" }.orEmpty()
    // Legenda: Samsung/LG leem sec:CaptionInfoEx; outras TVs, um <res> extra do tipo text/srt.
    val sub = subtitle?.let {
        """<sec:CaptionInfoEx sec:type="srt">${escapeXml(it)}</sec:CaptionInfoEx>"""
    }.orEmpty()
    val subRes = subtitle?.let { """<res protocolInfo="http-get:*:text/srt:*">${escapeXml(it)}</res>""" }.orEmpty()
    return """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" """ +
        """xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns:sec="http://www.sec.co.kr/">""" +
        """<item id="0" parentID="-1" restricted="1">""" +
        "<dc:title>${escapeXml(title)}</dc:title><upnp:class>$upnpClass</upnp:class>$art$sub" +
        """<res protocolInfo="http-get:*:$mime:$DLNA_FEATURES"$duration$sizeAttr>""" +
        "${escapeXml(url)}</res>$subRes</item></DIDL-Lite>"
}

/** Pode pedir trechos (Range), é stream e não precisa baixar tudo antes. */
const val DLNA_FEATURES = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
