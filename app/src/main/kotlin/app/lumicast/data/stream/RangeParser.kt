package app.lumicast.data.stream

/** Resultado da interpretação do cabeçalho HTTP `Range` (RFC 9110, só unidade `bytes`). */
sealed interface RangeResult {
    /** Sem Range válido: responder 200 com o arquivo inteiro. */
    data object Full : RangeResult

    /** Faixa inclusiva [start, end]: responder 206. */
    data class Partial(val start: Long, val end: Long) : RangeResult

    /** Faixa fora do arquivo: responder 416 com `Content-Range: bytes * /size`. */
    data object Unsatisfiable : RangeResult
}

object RangeParser {
    fun parse(header: String?, size: Long): RangeResult {
        if (header.isNullOrBlank()) return RangeResult.Full
        val h = header.trim()
        if (!h.startsWith("bytes=", ignoreCase = true)) return RangeResult.Full
        val spec = h.substring(6).trim()
        // Várias faixas: servimos o arquivo inteiro (permitido pela RFC).
        if (spec.contains(',') || spec.isEmpty()) return RangeResult.Full

        val dash = spec.indexOf('-')
        if (dash < 0) return RangeResult.Full
        val first = spec.substring(0, dash).trim()
        val last = spec.substring(dash + 1).trim()

        return when {
            first.isEmpty() -> { // sufixo: "-N" = últimos N bytes
                val n = last.toLongOrNull() ?: return RangeResult.Full
                if (n <= 0L || size <= 0L) RangeResult.Unsatisfiable
                else RangeResult.Partial(maxOf(0L, size - n), size - 1)
            }
            else -> {
                val start = first.toLongOrNull() ?: return RangeResult.Full
                if (start < 0L) return RangeResult.Full
                val end = if (last.isEmpty()) size - 1 else (last.toLongOrNull() ?: return RangeResult.Full)
                if (last.isNotEmpty() && end < start) return RangeResult.Full // especificação inválida → ignorar
                if (start >= size) RangeResult.Unsatisfiable
                else RangeResult.Partial(start, minOf(end, size - 1))
            }
        }
    }
}
