package app.lumicast.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeNameTest {
    private val illegal = Regex("""[\\/:*?"<>|\n\r]""")

    @Test fun tituloVazioOuSoEspacos() {
        assertEquals("video.mp4", safeFileName(""))
        assertEquals("video.mp4", safeFileName("   "))
        assertEquals("video.mp4", safeFileName("..."))
    }

    @Test fun nenhumCaractereProibidoSobra() {
        val n = safeFileName("a\\b/c:d*e?f\"g<h>i|j\nk\rl")
        assertFalse(n, illegal.containsMatchIn(n))
        assertTrue(n, n.endsWith(".mp4"))
    }

    @Test fun pontoNoFimEPontoNoComecoSaem() {
        assertEquals("Fim.mp4", safeFileName("Fim."))
        assertEquals("oculto.mp4", safeFileName(".oculto"))
    }

    @Test fun extensaoPeloMime() {
        assertEquals("a.webm", safeFileName("a", "video/webm"))
        assertEquals("a.mov", safeFileName("a", "video/quicktime"))
        assertEquals("a.mp4", safeFileName("a", "application/octet-stream"))
        assertEquals("a.mkv", safeFileName("a.mkv", "video/x-matroska"))
    }

    @Test fun tituloLongoFicaLimitadoEComExtensao() {
        val n = safeFileName("x".repeat(500) + ".mkv")
        assertTrue(n.length <= 130)
        assertTrue(n, n.endsWith(".mp4") || n.endsWith(".mkv"))
    }

    @Test fun pontoNoMeioDoTituloNaoImpedeExtensaoDeVideo() {
        // "Aula 1.5" tem ponto, mas ".5" não é extensão de vídeo: o arquivo salvo ficaria sem .mp4.
        assertEquals("Aula 1.5.mp4", safeFileName("Aula 1.5"))
    }

    @Test fun pastaSegura() {
        assertEquals("Pasta", safeFolderName(""))
        assertEquals("Pasta", safeFolderName(".."))
        assertEquals("a_b", safeFolderName("a/b"))
        assertEquals(60, safeFolderName("p".repeat(100)).length)
        assertEquals("Meus filmes", safeFolderName("  Meus filmes "))
    }
}
