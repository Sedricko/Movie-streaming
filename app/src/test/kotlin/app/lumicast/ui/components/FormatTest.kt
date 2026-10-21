package app.lumicast.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {
    @Test fun leHorarioDoVideo() {
        assertEquals(90, parseClock("1:30"))
        assertEquals(890, parseClock(" 14:50 "))
        assertEquals(3723, parseClock("1:02:03"))
        assertEquals(90, parseClock("90"))
        assertEquals(0, parseClock("0:00"))
    }

    @Test fun recusaHorarioInvalido() {
        assertNull(parseClock(""))
        assertNull(parseClock("1:75"))
        assertNull(parseClock("abc"))
        assertNull(parseClock("1::2"))
        assertNull(parseClock("-1:00"))
    }

    @Test fun formatarEVoltarDaOMesmoValor() = assertEquals(890, parseClock(formatDuration(890)))

    @Test fun mascaraDeTempoAoDigitar() {
        assertEquals("0:05", formatClockDigits("5"))
        assertEquals("1:30", formatClockDigits("130"))
        assertEquals("14:50", formatClockDigits("1450"))
        assertEquals("10:00", formatClockDigits("1000"))
        assertEquals("1:12:59", formatClockDigits("11259"))
        assertEquals("10:12:59", formatClockDigits("101259"))
        assertEquals("1:30", formatClockDigits("1:30")) // já formatado continua igual
        assertEquals("", formatClockDigits("0"))
        assertEquals(890, parseClock(formatClockDigits("1450")))
    }
}
