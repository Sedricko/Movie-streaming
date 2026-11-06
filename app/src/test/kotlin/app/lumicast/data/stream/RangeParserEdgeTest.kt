package app.lumicast.data.stream

import org.junit.Assert.assertEquals
import org.junit.Test

/** Limites exatos do RangeParser (o servidor responde 206/416 conforme isto). */
class RangeParserEdgeTest {
    private fun p(h: String?, size: Long) = RangeParser.parse(h, size)

    @Test fun ultimoByteExato() {
        assertEquals(RangeResult.Partial(999, 999), p("bytes=999-999", 1000))
        assertEquals(RangeResult.Partial(999, 999), p("bytes=999-", 1000))
        assertEquals(RangeResult.Unsatisfiable, p("bytes=1000-1000", 1000))
    }

    @Test fun fimIgualAoTamanhoEAjustado() =
        assertEquals(RangeResult.Partial(0, 999), p("bytes=0-1000", 1000))

    @Test fun umByteSo() = assertEquals(RangeResult.Partial(5, 5), p("bytes=5-5", 1000))

    @Test fun arquivoVazio() {
        assertEquals(RangeResult.Unsatisfiable, p("bytes=0-", 0))
        assertEquals(RangeResult.Unsatisfiable, p("bytes=-5", 0))
        assertEquals(RangeResult.Full, p(null, 0))
    }

    @Test fun sufixoDeUmByteEExatamenteOTamanho() {
        assertEquals(RangeResult.Partial(999, 999), p("bytes=-1", 1000))
        assertEquals(RangeResult.Partial(0, 999), p("bytes=-1000", 1000))
        assertEquals(RangeResult.Partial(1, 999), p("bytes=-999", 1000))
    }

    @Test fun espacosECaixaDaUnidade() {
        assertEquals(RangeResult.Partial(0, 9), p("  BYTES=0-9  ", 1000))
        assertEquals(RangeResult.Partial(0, 9), p("bytes= 0 - 9 ", 1000))
    }

    @Test fun lixoViraArquivoInteiro() {
        assertEquals(RangeResult.Full, p("bytes=", 1000))
        assertEquals(RangeResult.Full, p("bytes=-", 1000))
        assertEquals(RangeResult.Full, p("bytes=5", 1000))
        assertEquals(RangeResult.Full, p("bytes=a-b", 1000))
        assertEquals(RangeResult.Full, p("bytes=0-99999999999999999999", 1000)) // estoura Long
    }

    @Test fun tamanhoGrandeNaoEstouraInt() {
        val big = 5L * 1024 * 1024 * 1024
        assertEquals(RangeResult.Partial(big - 1, big - 1), p("bytes=${big - 1}-", big))
        assertEquals(RangeResult.Partial(big - 10, big - 1), p("bytes=-10", big))
    }

    @Test fun duplosSinaisNaoSaoFaixaValida() {
        // "--5" é inválido e deve ser ignorado (200 inteiro); hoje vira 416 (n negativo cai em Unsatisfiable)
        assertEquals(RangeResult.Full, p("bytes=--5", 1000))
    }
}
