package app.lumicast.data.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpnpTest {
    private val samsung = """<?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0"><device>
          <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
          <friendlyName>[TV] Samsung Q60 &amp; Cia</friendlyName>
          <UDN>uuid:abc-123</UDN>
          <serviceList>
            <service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
              <controlURL>/upnp/control/RenderingControl1</controlURL></service>
            <service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
              <controlURL>upnp/control/AVTransport1</controlURL></service>
          </serviceList></device></root>"""

    @Test fun leDescricaoDaTv() {
        val r = parseRenderer(samsung, "http://192.168.0.20:9197/dmr")!!
        assertEquals("uuid:abc-123", r.udn)
        assertEquals("[TV] Samsung Q60 & Cia", r.name)
        assertEquals("http://192.168.0.20:9197/upnp/control/AVTransport1", r.avTransportUrl)
        assertEquals("http://192.168.0.20:9197/upnp/control/RenderingControl1", r.renderingUrl)
    }

    @Test fun semAVTransportNaoETv() = assertNull(parseRenderer("<root><device><friendlyName>NAS</friendlyName></device></root>", "http://x/"))

    @Test fun tempoUpnp() {
        assertEquals(83, parseUpnpTime("0:01:23"))
        assertEquals(3723, parseUpnpTime("01:02:03.500"))
        assertNull(parseUpnpTime("NOT_IMPLEMENTED"))
        assertEquals("1:02:03", upnpTime(3723))
        assertEquals("0:00:00", upnpTime(-5))
    }

    @Test fun valorDaRespostaSoap() =
        assertEquals("PLAYING", soapValue("<s:Body><u:R><CurrentTransportState>PLAYING</CurrentTransportState></u:R></s:Body>", "CurrentTransportState"))

    @Test fun didlEscapaTituloEUrl() {
        val d = didl("Ep <1> & 2", "http://h/s?a=1&b=2", "video/mp4", 61, 100)
        assertTrue("Ep &lt;1&gt; &amp; 2" in d)
        assertTrue("http://h/s?a=1&amp;b=2" in d)
        assertTrue("""duration="0:01:01.000"""" in d)
    }
}
