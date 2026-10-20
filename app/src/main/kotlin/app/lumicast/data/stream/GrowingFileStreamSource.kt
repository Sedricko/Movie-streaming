package app.lumicast.data.stream

import app.lumicast.domain.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.RandomAccessFile

/**
 * Serve um arquivo que ainda está sendo escrito (ex.: download do TDLib).
 *
 * @param availablePrefix bytes contíguos já disponíveis a partir do início do arquivo.
 * @param onRandomAccess chamado quando o cliente pede um offset além do que existe (seek longe);
 *        o dono do download deve repriorizar/reiniciar o download nesse offset (ver docs/02-arquitetura.md).
 */
class GrowingFileStreamSource(
    private val file: File,
    override val sizeBytes: Long,
    override val mimeType: String,
    private val availablePrefix: () -> Long,
    private val timeoutMs: Long = 30_000,
    private val pollMs: Long = 100,
    private val onRandomAccess: suspend (offset: Long) -> Unit = {},
) : StreamSource {

    override suspend fun read(offset: Long, buffer: ByteArray, length: Int): Int {
        if (offset >= sizeBytes || length <= 0) return -1
        if (availablePrefix() <= offset) onRandomAccess(offset)

        val ready = withTimeoutOrNull(timeoutMs) {
            while (availablePrefix() <= offset) delay(pollMs)
            true
        } ?: return -1

        check(ready)
        val canRead = minOf(length.toLong(), sizeBytes - offset, availablePrefix() - offset).toInt()
        return withContext(Dispatchers.IO) {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(offset)
                raf.read(buffer, 0, canRead)
            }
        }
    }
}
