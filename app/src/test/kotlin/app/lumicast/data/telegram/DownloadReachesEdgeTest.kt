package app.lumicast.data.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadReachesEdgeTest {
    private val mib = 1024L * 1024

    @Test fun limiteDaJanelaEExclusivo() {
        // fronteira do download = 0 + 10 MiB; janela 16 MiB
        assertTrue(downloadReaches(10 * mib + 16 * mib - 1, true, 0, 10 * mib))
        assertFalse(downloadReaches(10 * mib + 16 * mib, true, 0, 10 * mib))
    }

    @Test fun offsetExatamenteNoInicioDoDownload() {
        assertTrue(downloadReaches(100 * mib, true, 100 * mib, 0))
        assertFalse(downloadReaches(100 * mib - 1, true, 100 * mib, 0))
    }

    @Test fun offsetDentroDoTrechoJaBaixado() =
        assertTrue(downloadReaches(3 * mib, true, 0, 10 * mib))

    @Test fun janelaPersonalizada() {
        assertTrue(downloadReaches(99, true, 0, 0, window = 100))
        assertFalse(downloadReaches(100, true, 0, 0, window = 100))
        assertFalse(downloadReaches(0, true, 0, 0, window = 0))
    }

    @Test fun inativoNuncaAlcanca() = assertFalse(downloadReaches(0, false, 0, 100 * mib))
}
