package app.lumicast.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalOrderTest {
    private fun sorted(vararg names: String) = names.sortedWith(::compareNatural)

    @Test fun numerosPeloValor() = assertEquals(
        listOf("video 1", "video 2", "video 3", "video 4", "video 5", "video 6", "video 7", "video 10"),
        sorted("video 1", "video 5", "video 7", "video 10", "video 2", "video 3", "video 4", "video 6"),
    )

    @Test fun nomesDeArquivoDoTelegram() = assertEquals(
        listOf("27_26b40a.mp4", "28_b739ab.mp4", "29_158245.mp4", "30_ab4676.mp4", "31_6d1774.mp4"),
        sorted("31_6d1774.mp4", "29_158245.mp4", "30_ab4676.mp4", "27_26b40a.mp4", "28_b739ab.mp4"),
    )

    @Test fun ignoraMaiusculasEZerosAEsquerda() {
        assertEquals(listOf("Ep 01", "ep 2", "EP 10"), sorted("EP 10", "ep 2", "Ep 01"))
        assertEquals(0, compareNatural("Ep 007", "ep 7"))
    }

    @Test fun numeroGiganteNaoEstoura() =
        assertEquals(listOf("a 99", "a 123456789012345678901234567890"), sorted("a 123456789012345678901234567890", "a 99"))
}
