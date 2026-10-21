package app.lumicast.domain

private val SRT_TIME = Regex("""(\d{1,2}:\d{2}:\d{2}),(\d{3})""")
private val NUMBER = Regex("""\d+""")

/** SRT → WebVTT (o Chromecast só aceita VTT): cabeçalho e vírgula dos milissegundos virando ponto. */
fun srtToVtt(srt: String): String {
    val body = srt.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
    if (body.trimStart().startsWith("WEBVTT")) return body
    return "WEBVTT\n\n" + body.replace(SRT_TIME) { "${it.groupValues[1]}.${it.groupValues[2]}" }
}

private fun baseName(name: String) = name.substringBeforeLast('.').lowercase().trim()

/** Primeiro número do nome que não é ano nem resolução (ex.: "Ep 07 1080p" → 7). */
private fun episodeNumber(name: String): Int? = NUMBER.findAll(baseName(name)).map { it.value }
    .firstOrNull { v -> v.length <= 4 && v.toInt() !in 1900..2100 && v !in setOf("480", "720", "1080", "2160") }
    ?.toInt()

/**
 * Escolhe a legenda certa entre os nomes de arquivo do chat: primeiro o mesmo nome do vídeo,
 * depois o mesmo número de episódio. null se nada combina (melhor sem legenda que com a errada).
 */
fun pickSubtitle(videoName: String, subtitleNames: List<String>): String? {
    val subs = subtitleNames.filter { it.endsWith(".srt", true) || it.endsWith(".vtt", true) }
    subs.firstOrNull { baseName(it) == baseName(videoName) }?.let { return it }
    subs.firstOrNull { baseName(it).startsWith(baseName(videoName)) }?.let { return it } // "ep1.pt-br.srt"
    val ep = episodeNumber(videoName) ?: return null
    return subs.filter { episodeNumber(it) == ep }.singleOrNull() // dois candidatos = ambíguo: não chuta
}
