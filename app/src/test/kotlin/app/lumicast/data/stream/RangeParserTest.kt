package app.lumicast.data.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class RangeParserTest {
    private val size = 1000L

    @Test fun semCabecalho_entregaArquivoInteiro() {
        assertEquals(RangeResult.Full, RangeParser.parse(null, size))
        assertEquals(RangeResult.Full, RangeParser.parse("", size))
    }

    @Test fun faixaNormal() {
        assertEquals(RangeResult.Partial(0, 499), RangeParser.parse("bytes=0-499", size))
        assertEquals(RangeResult.Partial(500, 999), RangeParser.parse("bytes=500-999", size))
    }

    @Test fun faixaAberta_vaiAteOFim() {
        assertEquals(RangeResult.Partial(900, 999), RangeParser.parse("bytes=900-", size))
    }

    @Test fun fimALemDoArquivo_eAjustado() {
        assertEquals(RangeResult.Partial(990, 999), RangeParser.parse("bytes=990-5000", size))
    }

    @Test fun sufixo_ultimosNBytes() {
        assertEquals(RangeResult.Partial(900, 999), RangeParser.parse("bytes=-100", size))
        assertEquals(RangeResult.Partial(0, 999), RangeParser.parse("bytes=-5000", size))
    }

    @Test fun inicioForaDoArquivo_eInsatisfazivel() {
        assertEquals(RangeResult.Unsatisfiable, RangeParser.parse("bytes=1000-", size))
        assertEquals(RangeResult.Unsatisfiable, RangeParser.parse("bytes=-0", size))
    }

    @Test fun invalidos_saoIgnorados() {
        assertEquals(RangeResult.Full, RangeParser.parse("items=0-10", size))
        assertEquals(RangeResult.Full, RangeParser.parse("bytes=abc-10", size))
        assertEquals(RangeResult.Full, RangeParser.parse("bytes=10-5", size))
        assertEquals(RangeResult.Full, RangeParser.parse("bytes=0-10,20-30", size))
    }
}
