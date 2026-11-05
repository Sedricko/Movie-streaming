package app.lumicast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagTest {
    @Test
    fun metricAggregatesAndLogCountsByTag() {
        Diag.metric("t.ms", 10); Diag.metric("t.ms", 30)
        Diag.log("tagx", "a")
        val out = Diag.statsText()
        assertTrue(out, "t.ms: n=2 média=20 máx=30" in out)
        assertTrue(out, "eventos.tagx: n=1" in out)
    }

    @Test
    fun healthFlagsBottleneckAndHintsTheCause() {
        // reposicionamentos + espera alta = download alternando entre pontos do arquivo
        repeat(4) { Diag.metric("stream.reposicionamentos", 1) }
        Diag.metric("server.espera_pct", 99)
        Diag.metric("server.pedido_final", 1)
        val h = Diag.health().associateBy { it.label }
        assertEquals(Level.Bad, h["Espera pelo download"]!!.level)
        assertEquals(Level.Warn, h["Reposicionamentos"]!!.level)
        assertTrue(Diag.hints().any { "índice no fim" in it })
        assertTrue(Diag.first("k-unico") && !Diag.first("k-unico"))
    }
}
