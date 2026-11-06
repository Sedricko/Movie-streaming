package app.lumicast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueLogicTest {
    private fun v(id: Long) = TgVideo(fileId = id.toInt(), chatId = 1, messageId = id, title = "v$id", durationSec = 60, sizeBytes = 1, mimeType = "video/mp4")
    private fun k(id: Long) = QueueLogic.key(v(id))

    @Test fun proximoEOPrimeiroQueNaoTocou() {
        val q = listOf(v(1), v(2), v(3))
        assertEquals(v(1), QueueLogic.next(q, emptySet()))
        assertEquals(v(3), QueueLogic.next(q, setOf(k(1), k(2))))
        assertNull(QueueLogic.next(q, setOf(k(1), k(2), k(3))))
        assertNull(QueueLogic.next(emptyList(), emptySet()))
    }

    @Test fun hasPlayableSoComTocaveis() {
        assertTrue(QueueLogic.hasPlayable(listOf(v(1)), emptySet()))
        assertEquals(false, QueueLogic.hasPlayable(listOf(v(1)), setOf(k(1))))
        assertEquals(false, QueueLogic.hasPlayable(emptyList(), emptySet()))
    }

    @Test fun freshIgnoraRepetidosDentroDaListaEContraAFila() {
        val q = listOf(v(1))
        assertEquals(listOf(v(2)), QueueLogic.fresh(q, listOf(v(2), v(2), v(1))))
        assertTrue(QueueLogic.fresh(q, listOf(v(1))).isEmpty())
        assertTrue(QueueLogic.fresh(emptyList(), emptyList()).isEmpty())
    }

    @Test fun removerEsqueceOJaTocou() {
        val (items, played) = QueueLogic.remove(listOf(v(1), v(2)), setOf(k(1), k(2)), 0)!!
        assertEquals(listOf(v(2)), items)
        assertEquals(setOf(k(2)), played)
    }

    @Test fun removerIndiceInvalidoDevolveNull() {
        assertNull(QueueLogic.remove(listOf(v(1)), emptySet(), 5))
        assertNull(QueueLogic.remove(listOf(v(1)), emptySet(), -1))
    }

    @Test fun limparTocadosMantemOResto() {
        assertEquals(listOf(v(3)), QueueLogic.clearPlayed(listOf(v(1), v(2), v(3)), setOf(k(1), k(2))))
    }

    @Test fun tocarDoIndiceMarcaOsAnterioresEOProprio() {
        val q = listOf(v(1), v(2), v(3), v(4))
        assertEquals(setOf(k(1), k(2), k(3)), QueueLogic.playedUpTo(q, 2))
        assertEquals(setOf(k(1)), QueueLogic.playedUpTo(q, 0))
        assertEquals(q.map(QueueLogic::key).toSet(), QueueLogic.playedUpTo(q, 99)) // índice além do fim: todos
    }

    @Test fun anteriorDesmarcaSeJaEstaNaFilaSenaoEntraNaFrente() {
        val (items1, played1) = QueueLogic.rewind(listOf(v(1), v(2)), setOf(k(1)), v(1))
        assertEquals(listOf(v(1), v(2)), items1)
        assertTrue(played1.isEmpty())
        val (items2, played2) = QueueLogic.rewind(listOf(v(2)), setOf(k(2)), v(9))
        assertEquals(listOf(v(9), v(2)), items2)
        assertEquals(setOf(k(2)), played2)
    }
}
