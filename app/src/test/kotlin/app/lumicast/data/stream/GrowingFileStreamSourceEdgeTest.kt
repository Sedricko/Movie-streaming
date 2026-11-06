package app.lumicast.data.stream

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class GrowingFileStreamSourceEdgeTest {
    private val data = ByteArray(100) { it.toByte() }
    private fun f(): File = File.createTempFile("lumicast", ".bin").apply { deleteOnExit(); writeBytes(data) }

    @Test fun pedidoMaiorQueORestanteELimitadoAoTamanho() = runTest {
        val s = GrowingFileStreamSource(f(), 100, "video/mp4", { 100L })
        val b = ByteArray(50)
        assertEquals(10, s.read(90, b, 50))
        assertArrayEquals(data.copyOfRange(90, 100), b.copyOf(10))
    }

    @Test fun ultimoByteEUmByteSo() = runTest {
        val s = GrowingFileStreamSource(f(), 100, "video/mp4", { 100L })
        val b = ByteArray(8)
        assertEquals(1, s.read(99, b, 8))
        assertEquals(99, b[0].toInt())
    }

    @Test fun tamanhoDeclaradoMenorQueOArquivoCorta() = runTest {
        val s = GrowingFileStreamSource(f(), 40, "video/mp4", { 100L })
        assertEquals(10, s.read(30, ByteArray(50), 50))
        assertEquals(-1, s.read(40, ByteArray(4), 4))
    }

    @Test fun comprimentoZeroOuNegativo() = runTest {
        val s = GrowingFileStreamSource(f(), 100, "video/mp4", { 100L })
        assertEquals(-1, s.read(0, ByteArray(4), 0))
        assertEquals(-1, s.read(0, ByteArray(4), -1))
    }

    @Test fun offsetDeUmaFaixaNoLimiteDoPrefixoEsperaOProximoByte() = runTest {
        var calls = 0
        // prefixo == offset: nada para ler ainda; depois de algumas voltas cresce
        val s = GrowingFileStreamSource(f(), 100, "video/mp4", { if (++calls < 6) 20L else 30L }, pollMs = 10)
        val b = ByteArray(50)
        assertEquals(10, s.read(20, b, 50))
        assertArrayEquals(data.copyOfRange(20, 30), b.copyOf(10))
    }

    @Test fun seekLongeAvisaODono() = runTest {
        val seen = mutableListOf<Long>()
        val s = GrowingFileStreamSource(f(), 100, "video/mp4", { 10L }, timeoutMs = 200, pollMs = 50, onRandomAccess = { seen += it })
        assertEquals(-1, s.read(60, ByteArray(4), 4)) // nunca chega: expira
        assertEquals(listOf(60L), seen)
    }

    @Test fun lerDentroDoPrefixoNaoChamaOnRandomAccess() = runTest {
        var called = false
        val s = GrowingFileStreamSource(f(), 100, "video/mp4", { 50L }, onRandomAccess = { called = true })
        s.read(49, ByteArray(1), 1)
        assertEquals(false, called)
    }

    @Test fun offsetNegativoNaoLancaExcecao() = runTest {
        val s = GrowingFileStreamSource(f(), 100, "video/mp4", { 100L })
        assertEquals(-1, s.read(-1, ByteArray(4), 4)) // hoje RandomAccessFile.seek(-1) lança IOException
    }
}
