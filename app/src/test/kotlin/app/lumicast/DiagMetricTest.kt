package app.lumicast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/** Só nomes "dx.*" (Diag é singleton e compartilha estado com DiagTest). */
class DiagMetricTest {
    private fun line(name: String) = Diag.statsText().lines().first { it.startsWith("$name:") }

    @Test fun mediaEInteiraEMaximoRastreado() {
        Diag.metric("dx.a", 1); Diag.metric("dx.a", 2); Diag.metric("dx.a", 2)
        assertEquals("dx.a: n=3 média=1 máx=2", line("dx.a")) // 5/3 = 1 (divisão inteira)
    }

    @Test fun metricaComValorZeroContaNoN() {
        Diag.metric("dx.zero", 0)
        assertEquals("dx.zero: n=1 média=0 máx=0", line("dx.zero"))
    }

    @Test fun maximoDeValoresNegativos() {
        Diag.metric("dx.neg", -5); Diag.metric("dx.neg", -3)
        assertEquals("dx.neg: n=2 média=-4 máx=-3", line("dx.neg"))
    }

    @Test fun primeiraVezPorChave() {
        assertTrue(Diag.first("dx.k1")); assertTrue(Diag.first("dx.k2"))
        assertEquals(false, Diag.first("dx.k1"))
    }

    @Test fun concorrenciaNaoPerdeContagem() {
        val go = CountDownLatch(1)
        val ts = List(8) { thread { go.await(); repeat(500) { Diag.metric("dx.conc", 2) } } }
        go.countDown(); ts.forEach { it.join() }
        assertEquals("dx.conc: n=4000 média=2 máx=2", line("dx.conc"))
    }

    @Test fun logContaPorTagENaoEstouraOLimite() {
        repeat(450) { Diag.log("dx.tag", "m$it") } // MAX=400 linhas, mas a contagem de eventos é total
        assertEquals("eventos.dx.tag: n=450 média=1 máx=1", line("eventos.dx.tag"))
    }
}
