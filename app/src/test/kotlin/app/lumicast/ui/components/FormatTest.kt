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
}
