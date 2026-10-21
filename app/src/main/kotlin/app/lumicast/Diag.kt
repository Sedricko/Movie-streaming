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

    fun install(context: Context) {
        crashFile = File(context.filesDir, "last-crash.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { crashFile?.writeText("${Date()}\n${e.stackTraceToString()}") }
            previous?.uncaughtException(thread, e)
        }
    }

    fun log(tag: String, msg: String) = synchronized(lines) {
        if (lines.size >= MAX) lines.removeFirst()
        lines.addLast("${time.format(Date())} [$tag] $msg")
    }

    fun report(context: Context): String = buildString {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        appendLine("Lumicast ${pkg.versionName} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
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
