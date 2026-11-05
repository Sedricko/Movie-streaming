package app.lumicast

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

enum class Level { None, Ok, Warn, Bad }

/** Uma leitura pronta para a tela de Monitoramento: o que foi medido, o valor e se está bom. */
data class Health(val label: String, val value: String, val level: Level)

/**
 * Diagnóstico interno: eventos, métricas, estado do aparelho, travamentos da tela e motivo de o Android ter
 * encerrado o app. Serve para o usuário enviar por "Enviar diagnóstico". Nada sai do aparelho sem ele compartilhar.
 * Nunca registrar conteúdo de mensagens, nome de rede nem telefone.
 */
object Diag {
    private const val MAX = 400
    private val lines = ArrayDeque<String>()
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
    private var crashFile: File? = null
    private var sessionFile: File? = null
    private var previousSession: String? = null
    private val stats = sortedMapOf<String, LongArray>() // nome -> [n, soma, máx]; mesmo lock de [lines]
    private val seen = HashSet<String>()
    private var dog: Thread? = null

    fun install(context: Context) {
        crashFile = File(context.filesDir, "last-crash.txt")
        sessionFile = File(context.filesDir, "last-session.txt").also { f ->
            previousSession = f.takeIf { it.exists() }?.readText() // a sessão anterior: o que aconteceu antes de o app fechar/morrer
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            // os eventos ficam só na memória: gravar junto com a falha para não sumirem quando o app reabrir
            runCatching { crashFile?.writeText("${Date()}\n${e.stackTraceToString()}\n\n${sessionText()}") }
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

    /** true só na primeira vez que [key] aparece (ex.: contar aparelhos distintos). */
    fun first(key: String): Boolean = synchronized(lines) { seen.add(key) }

    private fun count(name: String) = synchronized(lines) { stats[name]?.get(0) ?: 0L }
    private fun avg(name: String) = synchronized(lines) { stats[name]?.let { it[1] / it[0] } }
    private fun max(name: String) = synchronized(lines) { stats[name]?.get(2) }

    fun statsText(): String = synchronized(lines) {
        stats.entries.joinToString("\n") { (k, s) -> "$k: n=${s[0]} média=${s[1] / s[0]} máx=${s[2]}" }
    }

    private fun level(v: Number?, warn: Double, bad: Double, lowIsBad: Boolean = false): Level = when {
        v == null -> Level.None
        lowIsBad -> if (v.toDouble() < bad) Level.Bad else if (v.toDouble() < warn) Level.Warn else Level.Ok
        else -> if (v.toDouble() > bad) Level.Bad else if (v.toDouble() > warn) Level.Warn else Level.Ok
    }

    /** Leitura resumida com limites: o que está bom, em atenção ou ruim. */
    fun health(): List<Health> {
        val kbs = avg("server.kb_por_s")
        val wait = avg("server.espera_pct")
        val toTv = avg("tv.ms_ate_enviar")
        val cut = count("server.respostas_interrompidas")
        val moves = count("stream.reposicionamentos")
        val freeze = max("ui.travamento_ms")
        val errors = count("eventos.erro")
        fun na(v: Any?, f: () -> String) = if (v == null) "—" else f()
        return listOf(
            Health("Vazão para a TV", na(kbs) { "%.1f MB/s".format(Locale.ROOT, kbs!! / 1024.0) }, level(kbs, 2000.0, 500.0, lowIsBad = true)),
            Health("Espera pelo download", na(wait) { "$wait%" }, level(wait, 30.0, 60.0)),
            Health("Clique até a TV", na(toTv) { "%.1f s".format(Locale.ROOT, toTv!! / 1000.0) }, level(toTv, 3000.0, 6000.0)),
            Health("Conexões cortadas", "$cut", level(cut, 2.0, 5.0)),
            Health("Reposicionamentos", "$moves", level(moves, 3.0, 6.0)),
            Health("Travamento da tela", na(freeze) { "${freeze}ms" }, level(freeze, 1000.0, 5000.0)),
            Health("Erros", "$errors", if (errors > 0) Level.Bad else Level.Ok),
        )
    }

    /** Causas prováveis, em frases curtas, a partir das métricas. Regras simples: são pistas, não certeza. */
    fun hints(): List<String> = buildList {
        val moves = count("stream.reposicionamentos")
        val wait = avg("server.espera_pct") ?: 0
        if (count("server.pedido_final") > 0)
            add("A TV pediu o final do arquivo antes de tocar: típico de MP4 com o índice no fim. Isso atrasa o início.")
        if (moves >= 3 && wait >= 25)
            add("O download do Telegram foi movido $moves vezes e a leitura esperou ${wait}% do tempo: a TV está lendo pontos distantes do arquivo.")
        if (wait >= 60) add("O servidor passa a maior parte do tempo esperando o download: o gargalo é o Telegram, não o Wi-Fi.")
        if (count("server.clientes_distintos") > 1) add("Mais de um aparelho puxou o vídeo ao mesmo tempo (veja os IPs em [server]).")
        if (count("server.respostas_interrompidas") > 2) add("A TV fechou várias conexões: normal em seek, suspeito se o vídeo não toca.")
        if ((max("ui.travamento_ms") ?: 0) > 1000) add("A tela travou mais de 1 s: veja [travamento] nos eventos, tem a pilha de chamadas.")
        if (count("eventos.erro") > 0) add("Houve erros: veja [erro] nos eventos.")
    }

    /** Grava a sessão atual (métricas + eventos) para aparecer no próximo relatório, mesmo se o app morrer em segundo plano. */
    fun flush() {
        runCatching { sessionFile?.writeText("${Date()}\n${sessionText()}") }
    }

    private fun sessionText() = "== Métricas ==\n${statsText()}\n\n== Eventos ==\n${synchronized(lines) { lines.joinToString("\n") }}"

    /** Vigia a thread principal enquanto o app está visível: se ela parar > 1 s, registra a pilha de onde travou. */
    fun watch(on: Boolean) {
        dog?.interrupt()
        dog = null
        if (!on) return
        val main = Handler(Looper.getMainLooper())
        dog = Thread {
            try {
                while (true) {
                    val ran = AtomicBoolean(false)
                    val sent = SystemClock.uptimeMillis()
                    main.post { ran.set(true) }
                    Thread.sleep(1_000)
                    if (!ran.get()) {
                        val where = Looper.getMainLooper().thread.stackTrace.take(14).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
                        while (!ran.get()) Thread.sleep(100)
                        val ms = SystemClock.uptimeMillis() - sent - 1_000
                        metric("ui.travamento_ms", ms)
                        log("travamento", "tela parada por ~${ms + 1_000} ms em: $where")
                    }
                }
            } catch (_: InterruptedException) {
                // vigia desligado (app saiu da tela)
            }
        }.apply { isDaemon = true; name = "lumicast-vigia"; start() }
    }

    /** Estado do aparelho agora: o que costuma explicar lentidão e mortes em segundo plano. */
    private fun device(context: Context): String = buildString {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        appendLine("Lumicast ${pkg.versionName} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
        runCatching {
            val mem = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
            appendLine("Memória livre ${mem.availMem shr 20} de ${mem.totalMem shr 20} MB${if (mem.lowMemory) " · POUCA MEMÓRIA" else ""}")
        }
        runCatching {
            val pm = context.getSystemService(PowerManager::class.java)
            val bat = context.getSystemService(BatteryManager::class.java)
            val thermal = if (Build.VERSION.SDK_INT >= 29) " · térmico ${pm.currentThermalStatus} (0 normal … 6 desligando)" else ""
            appendLine("Bateria ${bat.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%${if (pm.isPowerSaveMode) " · ECONOMIA DE BATERIA LIGADA" else ""}$thermal")
            appendLine("Ignora otimização de bateria: ${pm.isIgnoringBatteryOptimizations(context.packageName)}")
        }
        runCatching {
            @Suppress("DEPRECATION")
            val w = context.applicationContext.getSystemService(WifiManager::class.java).connectionInfo
            appendLine("Wi-Fi ${w.frequency} MHz · link ${w.linkSpeed} Mbps · sinal ${w.rssi} dBm")
        }
        runCatching { appendLine("Espaço livre no aparelho: ${context.filesDir.usableSpace shr 20} MB") }
    }

    /** Por que o Android encerrou o app nas últimas vezes (ANR, pouca memória, falha…). Só Android 11+. */
    private fun exits(context: Context): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        return runCatching {
            context.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(context.packageName, 0, 6).joinToString("\n") {
                val why = when (it.reason) {
                    ApplicationExitInfo.REASON_ANR -> "ANR (app parou de responder)"
                    ApplicationExitInfo.REASON_LOW_MEMORY -> "pouca memória"
                    ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE -> "falha"
                    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "uso excessivo de recursos"
                    ApplicationExitInfo.REASON_USER_REQUESTED -> "fechado pelo usuário"
                    ApplicationExitInfo.REASON_USER_STOPPED -> "forçado a parar"
                    ApplicationExitInfo.REASON_SIGNALED -> "encerrado pelo sistema (sinal)"
                    ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependência morreu"
                    else -> "motivo ${it.reason}"
                }
                "${Date(it.timestamp)}: $why · ${it.description.orEmpty()} · importância ${it.importance} · PSS ${it.pss} KB"
            }
        }.getOrNull()?.ifBlank { null }
    }

    fun report(context: Context): String = buildString {
        append(device(context))
        appendLine()
        appendLine("== Leitura ==")
        health().forEach { appendLine("${it.label}: ${it.value} (${it.level})") }
        hints().forEach { appendLine("• $it") }
        appendLine()
        appendLine("== Métricas (nesta sessão) ==")
        appendLine(statsText())
        appendLine()
        appendLine("== Eventos recentes ==")
        synchronized(lines) { lines.forEach(::appendLine) }
        exits(context)?.let { appendLine(); appendLine("== Como o Android encerrou o app nas últimas vezes =="); appendLine(it) }
        previousSession?.let { appendLine(); appendLine("== Sessão anterior (até o app sair da tela) =="); appendLine(it) }
        crashFile?.takeIf { it.exists() }?.let {
            appendLine()
            appendLine("== Última falha ==")
            appendLine(it.readText())
        }
    }
}
