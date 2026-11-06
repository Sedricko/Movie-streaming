package app.lumicast.domain

/**
 * Regras puras da fila de reprodução (sem Android): o ViewModel só cuida dos efeitos (TV, aviso, disco).
 * A fila é a lista [items]; [played] guarda a chave "chat:mensagem" dos que já tocaram e continuam na lista.
 * O próximo a tocar é sempre o primeiro que não está em [played].
 */
object QueueLogic {
    fun key(v: TgVideo) = "${v.chatId}:${v.messageId}"

    fun hasPlayable(items: List<TgVideo>, played: Set<String>) = items.any { key(it) !in played }

    fun next(items: List<TgVideo>, played: Set<String>): TgVideo? = items.firstOrNull { key(it) !in played }

    /** O que realmente entra: sem repetir dentro de [videos] e sem o que já está em [items]. */
    fun fresh(items: List<TgVideo>, videos: List<TgVideo>): List<TgVideo> =
        videos.distinctBy(::key).filter { v -> items.none { key(it) == key(v) } }

    /** Tira o item [index] e esquece que ele tocou (senão, ao recolocar, voltaria como "já tocou" e nunca tocaria). null = índice inválido. */
    fun remove(items: List<TgVideo>, played: Set<String>, index: Int): Pair<List<TgVideo>, Set<String>>? {
        val gone = items.getOrNull(index) ?: return null
        return items.filterIndexed { i, _ -> i != index } to (played - key(gone))
    }

    fun clearPlayed(items: List<TgVideo>, played: Set<String>): List<TgVideo> = items.filter { key(it) !in played }

    /** Tocar o item [index] agora: ele e os de antes ficam "já tocou"; os de depois seguem a ordem. */
    fun playedUpTo(items: List<TgVideo>, index: Int): Set<String> = items.take(index + 1).map(::key).toSet()

    /** "Anterior": o vídeo atual volta a ser o próximo. Já na fila: só desmarca. Fora dela: entra na frente. */
    fun rewind(items: List<TgVideo>, played: Set<String>, current: TgVideo): Pair<List<TgVideo>, Set<String>> {
        val k = key(current)
        return if (items.any { key(it) == k }) items to (played - k) else (listOf(current) + items) to played
    }
}
