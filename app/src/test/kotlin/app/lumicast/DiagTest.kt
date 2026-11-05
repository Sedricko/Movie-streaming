package app.lumicast

import org.junit.Test
import org.junit.Assert.assertTrue

class DiagTest {
    @Test
    fun metricAggregatesAndLogCountsByTag() {
        Diag.metric("t.ms", 10); Diag.metric("t.ms", 30)
        Diag.log("tagx", "a")
        val out = Diag.statsText()
        assertTrue(out, "t.ms: n=2 média=20 máx=30" in out)
        assertTrue(out, "eventos.tagx: n=1" in out)
    }
}
