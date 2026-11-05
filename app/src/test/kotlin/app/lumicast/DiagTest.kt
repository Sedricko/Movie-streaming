package app.lumicast

import kotlin.test.Test
import kotlin.test.assertTrue

class DiagTest {
    @Test
    fun metricAggregatesAndLogCountsByTag() {
        Diag.metric("t.ms", 10); Diag.metric("t.ms", 30)
        Diag.log("tagx", "a")
        val out = Diag.statsText()
        assertTrue("t.ms: n=2 média=20 máx=30" in out, out)
        assertTrue("eventos.tagx: n=1" in out, out)
    }
}
