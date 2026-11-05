package app.lumicast

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnóstico interno: guarda os últimos eventos (streaming, Cast, erros) e a última falha do app,
 * para o usuário enviar por "Enviar diagnóstico". Nada sai do aparelho sem ele compartilhar.
 * Nunca registrar conteúdo de mensagens nem telefone.
 */
object Diag {
    private const val MAX = 400
    private val lines = ArrayDeque<String>()
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
    private var crashFile: File? = null
    private val stats = sortedMapOf<String, LongArray>() // nome -> [n, soma, máx]; mesmo lock de [lines]

    fun install(context: Context) {
        crashFile = File(context.filesDir, "last-crash.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            // os eventos ficam só na memória: gravar junto com a falha para não sumirem quando o app reabrir
            val events = synchronized(lines) { lines.joinToString("\n") }
            runCatching { crashFile?.writeText("${Date()}\n${e.stackTraceToString()}\n\n== Métricas ==\n${statsText()}\n\n== Eventos antes da falha ==\n$events") }
            previous?.uncaughtException(thread, e)
        }
    }

    fun log(tag: String, msg: String) = synchronized(lines) {
        if (lines.size >= MAX) lines.removeFirst()
        lines.addLast("${time.format(Date())} [$tag] $msg")
        metric("eventos.$tag", 1) // contagem por tag mostra de relance onde há mais erros/ruído
    }

    /** Acumula uma medida (ms, KB, contagem…). O relatório mostra n · média · máx: aponta gargalos sem logar tudo. */
    fun metric(name: String, value: Long) = synchronized(lines) {
        val s = stats.getOrPut(name) { LongArray(3) }
        s[0]++; s[1] += value; if (value > s[2]) s[2] = value
    }

    fun statsText(): String = synchronized(lines) {
        stats.entries.joinToString("\n") { (k, s) -> "$k: n=${s[0]} média=${s[1] / s[0]} máx=${s[2]}" }
    }

    fun report(context: Context): String = buildString {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        appendLine("Lumicast ${pkg.versionName} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine()
        appendLine("== Métricas (nesta sessão) ==")
        appendLine(statsText())
        appendLine()
        appendLine("== Eventos recentes ==")
        synchronized(lines) { lines.forEach(::appendLine) }
        crashFile?.takeIf { it.exists() }?.let {
            appendLine()
            appendLine("== Última falha ==")
            appendLine(it.readText())
        }
    }
}
