package app.lumicast.data.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadReachesTest {
    private val mib = 1024L * 1024

    @Test fun leituraLogoAposOTrechoBaixadoNaoReiniciaODownload() =
        assertTrue(downloadReaches(offset = 10 * mib + 64 * 1024, active = true, downloadOffset = 0, prefix = 10 * mib))

    @Test fun seekLongeReiniciaODownload() =
        assertFalse(downloadReaches(offset = 500 * mib, active = true, downloadOffset = 0, prefix = 10 * mib))

    @Test fun seekParaTrasDoInicioDoDownloadReinicia() =
        assertFalse(downloadReaches(offset = 1 * mib, active = true, downloadOffset = 100 * mib, prefix = 5 * mib))

    @Test fun downloadParadoSemprePedeDeNovo() =
        assertFalse(downloadReaches(offset = 1 * mib, active = false, downloadOffset = 0, prefix = 0))
}
