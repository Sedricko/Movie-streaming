package app.lumicast.data.stream

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
        call.response.header(HttpHeaders.AcceptRanges, "bytes")
        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
        call.response.header("transferMode.dlna.org", "Streaming")

        when (val r = RangeParser.parse(call.request.headers[HttpHeaders.Range], size)) {
            RangeResult.Unsatisfiable -> {
                call.response.header(HttpHeaders.ContentRange, "bytes */$size")
                call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
            }
            RangeResult.Full -> call.respond(SourceContent(source, 0, size - 1, size, partial = false, headOnly))
            is RangeResult.Partial -> call.respond(SourceContent(source, r.start, r.end, size, partial = true, headOnly))
        }
    }
}

private class SourceContent(
    private val source: StreamSource,
    private val start: Long,
    private val end: Long,
    private val total: Long,
    private val partial: Boolean,
    private val headOnly: Boolean,
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
        while (pos <= end) {
            val want = minOf(buf.size.toLong(), end - pos + 1).toInt()
            val n = source.read(pos, buf, want)
            if (n <= 0) break
            channel.writeFully(buf, 0, n)
            pos += n
        }
    }
}
