package app.lumicast.data.stream

import app.lumicast.domain.StreamSource

/** Arquivo pequeno na memória (imagem de "conectado", legenda) servido pelo mesmo servidor local. */
class BytesSource(private val bytes: ByteArray, override val mimeType: String) : StreamSource {
    override val sizeBytes: Long = bytes.size.toLong()

    override suspend fun read(offset: Long, buffer: ByteArray, length: Int): Int {
        if (offset >= sizeBytes || length <= 0) return -1
        val n = minOf(length.toLong(), sizeBytes - offset).toInt()
        bytes.copyInto(buffer, 0, offset.toInt(), offset.toInt() + n)
        return n
    }
}
