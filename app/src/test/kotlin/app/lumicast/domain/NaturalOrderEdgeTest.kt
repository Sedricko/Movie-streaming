package app.lumicast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalOrderEdgeTest {
    private fun v(id: Int, title: String) = TgVideo(id, 1L, id.toLong(), title, 0, 0, "video/mp4")
    private fun sorted(vararg n: String) = n.sortedWith(::compareNatural)

    @Test fun vaziosEPrefixos() {
        assertEquals(0, compareNatural("", ""))
        assertTrue(compareNatural("", "a") < 0)
        assertTrue(compareNatural("a", "a1") < 0)
        assertTrue(compareNatural("Ep 2", "Ep 2 final") < 0)
    }

    @Test fun antissimetria() {
        val s = listOf("", "a", "a1", "a01", "a10", "1", "01", "10", "9", "x-2", "x2", "Z", "é1")
        for (a in s) for (b in s) {
            val ab = compareNatural(a, b); val ba = compareNatural(b, a)
            assertEquals("$a vs $b", Integer.signum(ab), -Integer.signum(ba))
        }
    }

    @Test fun transitividadeNaOrdenacaoNaoLancaErro() {
        // TimSort lança "Comparison method violates its general contract" se o comparador for incoerente
        val s = (1..60).flatMap { listOf("ep $it", "Ep0$it", "ep ${it}b", "$it", "a$it-$it") }.shuffled(java.util.Random(7))
        val out = s.sortedWith(::compareNatural)
        for (i in 0 until out.size - 1) assertTrue(compareNatural(out[i], out[i + 1]) <= 0)
    }

    @Test fun zerosPuros() {
        assertEquals(0, compareNatural("a0", "a000"))
        assertTrue(compareNatural("a0", "a1") < 0)
    }

    @Test fun numeroAntesDeLetraNoMesmoLugar() =
        assertEquals(listOf("1a", "a1"), sorted("a1", "1a"))

    @Test fun duasSequenciasNumericas() =
        assertEquals(listOf("s1e2", "s1e10", "s2e1"), sorted("s2e1", "s1e10", "s1e2"))

    @Test fun comparadorDeVideosEstavelParaTitulosIguais() {
        val l = listOf(v(1, "B 2"), v(2, "A"), v(3, "B 2"), v(4, "B 10"))
        assertEquals(listOf(2, 1, 3, 4), l.sortedWith(NaturalTitleOrder).map { it.fileId })
        // reversed(): o que sortQueue(descending) usa
        assertEquals(listOf(4, 1, 3, 2), l.sortedWith(NaturalTitleOrder.reversed()).map { it.fileId })
    }
}
