package app.lumicast.data.stream

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class GrowingFileStreamSourceTest {

    private fun tempFile(bytes: ByteArray): File =
        File.createTempFile("lumicast", ".bin").apply { deleteOnExit(); writeBytes(bytes) }

    @Test fun leBytesJaDisponiveis() = runTest {
        val data = ByteArray(100) { it.toByte() }
        val src = GrowingFileStreamSource(tempFile(data), 100, "video/mp4", availablePrefix = { 100L })
        val buf = ByteArray(10)
        val n = src.read(20, buf, 10)
        assertEquals(10, n)
        assertArrayEquals(data.copyOfRange(20, 30), buf)
    }

    @Test fun limitaALeituraAoQueExiste() = runTest {
        val data = ByteArray(100) { it.toByte() }
        val src = GrowingFileStreamSource(tempFile(data), 100, "video/mp4", availablePrefix = { 25L })
        val buf = ByteArray(50)
        assertEquals(5, src.read(20, buf, 50))
    }

    @Test fun finalDoArquivo_devolveMenosUm() = runTest {
        val src = GrowingFileStreamSource(tempFile(ByteArray(10)), 10, "video/mp4", availablePrefix = { 10L })
        assertEquals(-1, src.read(10, ByteArray(4), 4))
    }

    @Test fun semDados_estouraTimeout() = runTest {
        val src = GrowingFileStreamSource(
            tempFile(ByteArray(10)), 10, "video/mp4",
            availablePrefix = { 0L }, timeoutMs = 500, pollMs = 50,
        )
        assertEquals(-1, src.read(0, ByteArray(4), 4))
    }
}
