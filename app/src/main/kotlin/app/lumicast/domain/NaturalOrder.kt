package app.lumicast.domain

private val CHUNK = Regex("""\d+|\D+""")

/**
 * Ordem "natural" de nomes: números dentro do texto valem pelo valor, não letra a letra.
 * "Vídeo 2" < "Vídeo 10"; maiúsculas/minúsculas não importam.
 */
fun compareNatural(a: String, b: String): Int {
    val x = CHUNK.findAll(a.lowercase()).map { it.value }.toList()
    val y = CHUNK.findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(x.size, y.size)) {
        val p = x[i]
        val q = y[i]
        val c = if (p[0].isDigit() && q[0].isDigit()) {
            // compara pelo tamanho sem zeros à esquerda e depois dígito a dígito: sem estouro com números enormes
            val pt = p.trimStart('0')
            val qt = q.trimStart('0')
            if (pt.length != qt.length) pt.length - qt.length else pt.compareTo(qt)
        } else {
            p.compareTo(q)
        }
        if (c != 0) return c
    }
    return x.size - y.size
}

val NaturalTitleOrder: Comparator<TgVideo> = Comparator { a, b -> compareNatural(a.title, b.title) }
