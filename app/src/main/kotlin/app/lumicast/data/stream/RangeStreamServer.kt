package app.lumicast.data.stream

import app.lumicast.Diag
import app.lumicast.data.dlna.DLNA_FEATURES
import app.lumicast.domain.StreamSource
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Servidor HTTP local que a TV acessa pela rede: `GET /stream/{token}` com suporte a Range.
 * Cada vídeo ganha um token aleatório; ao parar de transmitir, chame [unregister].
 *
 * Segurança: informe em [host] o IPv4 do Wi-Fi (veja [findLanIPv4]), não "0.0.0.0".
 */
class RangeStreamServer(
    private val host: String,
    private val requestedPort: Int = 0,
) {
    private val sources = ConcurrentHashMap<String, StreamSource>()
    private var server: EmbeddedServer<*, *>? = null

    var port: Int = 0
        private set

    suspend fun start() {
        if (server != null) return
        val s = embeddedServer(CIO, host = host, port = requestedPort) {
            routing {
                route("/stream/{token}") {
                    head { serve(call, headOnly = true) }
                    get { serve(call, headOnly = false) }
                }
            }
        }
        s.start(wait = false)
        port = s.engine.resolvedConnectors().first().port
        server = s
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)
        server = null
        sources.clear()
    }

    /** Registra uma fonte e devolve a URL completa para entregar à TV. */
    fun register(source: StreamSource): String {
        check(server != null) { "Servidor não iniciado" }
        val token = UUID.randomUUID().toString().replace("-", "")
        sources[token] = source
        return "http://$host:$port/stream/$token"
    }

    fun unregister(url: String) {
        sources.remove(url.substringAfterLast('/'))
    }

    private suspend fun serve(call: ApplicationCall, headOnly: Boolean) {
        val source = call.parameters["token"]?.let { sources[it] }
        if (source == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        val size = source.sizeBytes
        val client = call.request.local.remoteAddress
        val range = call.request.headers[HttpHeaders.Range]
        Diag.log("server", "${if (headOnly) "HEAD" else "GET"} range=$range de $client")
        if (Diag.first("cliente:$client")) Diag.metric("server.clientes_distintos", 1)
        val who = "$client ${range ?: "tudo"}"
        call.response.header(HttpHeaders.AcceptRanges, "bytes")
        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
        call.response.header("transferMode.dlna.org", "Streaming")
        call.response.header("contentFeatures.dlna.org", DLNA_FEATURES) // TVs DLNA exigentes (Samsung/LG) pedem isso

        when (val r = RangeParser.parse(call.request.headers[HttpHeaders.Range], size)) {
            RangeResult.Unsatisfiable -> {
                call.response.header(HttpHeaders.ContentRange, "bytes */$size")
                call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
            }
            RangeResult.Full -> call.respond(SourceContent(source, 0, size - 1, size, partial = false, headOnly, who))
            is RangeResult.Partial -> {
                // MP4 com o índice no fim: a TV pede o final antes de tocar e o download sequencial tem que pular para lá
                if (!headOnly && size > TAIL_BYTES * 4 && r.start >= size - TAIL_BYTES) {
                    Diag.metric("server.pedido_final", 1)
                    Diag.log("server", "$client pediu o final do arquivo (a partir de ${r.start} de $size): índice do MP4 no fim?")
                }
                call.respond(SourceContent(source, r.start, r.end, size, partial = true, headOnly, who))
            }
        }
    }
}

private const val TAIL_BYTES = 4L * 1024 * 1024

private class SourceContent(
    private val source: StreamSource,
    private val start: Long,
    private val end: Long,
    private val total: Long,
    private val partial: Boolean,
    private val headOnly: Boolean,
    private val who: String,
) : OutgoingContent.WriteChannelContent() {

    override val contentLength: Long = if (total == 0L) 0L else end - start + 1
    override val contentType: ContentType = runCatching { ContentType.parse(source.mimeType) }
        .getOrDefault(ContentType.Video.Any)
    override val status: HttpStatusCode = if (partial) HttpStatusCode.PartialContent else HttpStatusCode.OK
    override val headers: Headers =
        if (partial) headersOf(HttpHeaders.ContentRange, "bytes $start-$end/$total") else Headers.Empty

    override suspend fun writeTo(channel: ByteWriteChannel) {
        if (headOnly || total == 0L) return
        val buf = ByteArray(64 * 1024)
        var pos = start
        var waitedMs = 0L
        val t0 = System.nanoTime()
        var finished = false
        try {
            while (pos <= end) {
                val want = minOf(buf.size.toLong(), end - pos + 1).toInt()
                val t = System.nanoTime()
                val n = source.read(pos, buf, want)
                val w = (System.nanoTime() - t) / 1_000_000
                waitedMs += w
                if (w > 1_000) Diag.log("gargalo", "leitura esperou $w ms em $pos: download mais lento que a reprodução")
                if (n <= 0) {
                    Diag.log("erro", "fonte acabou em $pos antes do fim ($end): arquivo incompleto ou download falhou")
                    break
                }
                channel.writeFully(buf, 0, n)
                pos += n
            }
            finished = pos > end
        } finally {
            // !finished = TV fechou a conexão (seek costuma fazer isso) ou a fonte falhou
            val sent = pos - start
            val ms = ((System.nanoTime() - t0) / 1_000_000).coerceAtLeast(1)
            val pct = waitedMs * 100 / ms
            Diag.metric("server.kb_enviados", sent / 1024)
            if (sent >= 256 * 1024) Diag.metric("server.kb_por_s", sent / 1024 * 1000 / ms) // respostas minúsculas dariam vazão falsa
            Diag.metric("server.espera_pct", pct)
            if (!finished) Diag.metric("server.respostas_interrompidas", 1)
            Diag.log("server", "fim $who: ${sent / 1024} KB em $ms ms, espera $pct% — ${if (finished) "ok" else "CORTADA"}")
        }
    }
}
