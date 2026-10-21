package app.lumicast.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class DecodeTextTest {
    @Test fun utf8() = assertEquals("Ação é ótima", decodeText("Ação é ótima".toByteArray()))

    @Test fun windows1252DeLegendaAntiga() =
        assertEquals("Ação é ótima", decodeText("Ação é ótima".toByteArray(Charset.forName("windows-1252"))))
}
