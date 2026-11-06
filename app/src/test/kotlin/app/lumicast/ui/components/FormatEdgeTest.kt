package app.lumicast.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatEdgeTest {
    @Test fun duracaoNosLimites() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:59", formatDuration(59))
        assertEquals("1:00", formatDuration(60))
        assertEquals("59:59", formatDuration(3599))
        assertEquals("1:00:00", formatDuration(3600))
        assertEquals("25:00:01", formatDuration(25 * 3600 + 1))
    }

    @Test fun tamanhoNasFronteiras() {
        assertEquals("0 MB", formatSize(0))
        assertEquals("1 MB", formatSize(1024L * 1024))
        assertEquals("1,0 GB", formatSize(1024L * 1024 * 1024))
        assertEquals("1,5 GB", formatSize(1536L * 1024 * 1024))
    }

    @Test fun tamanhoPertoDeUmGigaNaoMostra1024MB() {
        // 1023,6 MiB arredonda para "1024 MB" em vez de "1,0 GB"
        val s = formatSize((1023.6 * 1024 * 1024).toLong())
        assertEquals("1,0 GB", s)
    }

    @Test fun parseClockLimites() {
        assertEquals(59, parseClock("0:59"))
        assertNull(parseClock("0:60"))
        assertNull(parseClock("1:2:3:4"))
        assertNull(parseClock("99999999999"))
        assertNull(parseClock("1.5"))
        assertEquals(4500, parseClock("75:00")) // minutos > 59 só no primeiro campo
    }

    @Test fun mascaraNosLimites() {
        assertEquals("", formatClockDigits(""))
        assertEquals("", formatClockDigits("abc"))
        assertEquals("0:07", formatClockDigits("007"))
        assertEquals("1:00", formatClockDigits("100"))
        assertEquals("99:59:59", formatClockDigits("995959"))
        assertEquals("99:59:59", formatClockDigits("9959591234")) // só 6 dígitos
    }

    @Test fun mascaraNaoDeixaPassarSegundosInvalidos() {
        // "9999" vira "99:99", que parseClock recusa: a máscara deixa o usuário digitar tempo impossível
        assertNull(parseClock(formatClockDigits("9999")))
    }
}
