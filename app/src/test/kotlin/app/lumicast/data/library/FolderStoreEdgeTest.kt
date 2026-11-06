package app.lumicast.data.library

import app.lumicast.domain.TgVideo
import app.lumicast.domain.VideoFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderStoreEdgeTest {
    private fun v(title: String, thumb: Int? = null) =
        TgVideo(1, -1001234567890L, Long.MAX_VALUE, title, 0, Long.MAX_VALUE, "video/x-matroska", thumbnailFileId = thumb)

    @Test fun textosEspeciaisEUnicode() {
        val t = "Aspas \" barra \\ nova\nlinha \t tab 日本語 🎬 </script>"
        val f = listOf(VideoFolder("id\"1", "Pasta 'x' \\ \"y\"", listOf(v(t, 0))))
        assertEquals(f, decodeFolders(encodeFolders(f)))
    }

    @Test fun limitesNumericos() {
        val f = listOf(VideoFolder("a", "n", listOf(v("t", Int.MAX_VALUE).copy(fileId = Int.MIN_VALUE))))
        assertEquals(f, decodeFolders(encodeFolders(f)))
    }

    @Test fun listaVaziaEEntradasNulasOuEmBranco() {
        assertEquals(emptyList<VideoFolder>(), decodeFolders(encodeFolders(emptyList())))
        assertEquals(emptyList<VideoFolder>(), decodeFolders(null))
        assertEquals(emptyList<VideoFolder>(), decodeFolders("   "))
        assertEquals(emptyList<VideoFolder>(), decodeFolders("{}"))
        assertEquals(emptyList<VideoFolder>(), decodeFolders("[]"))
    }

    @Test fun ordemDasPastasEVideosEPreservada() {
        val f = listOf(VideoFolder("2", "B", listOf(v("z"), v("a"))), VideoFolder("1", "A"))
        val back = decodeFolders(encodeFolders(f))
        assertEquals(listOf("2", "1"), back.map { it.id })
        assertEquals(listOf("z", "a"), back[0].videos.map { it.title })
    }

    @Test fun thumbnailZeroNaoViraNulo() =
        assertEquals(0, decodeFolders(encodeFolders(listOf(VideoFolder("a", "n", listOf(v("t", 0)))))).single().videos.single().thumbnailFileId)

    @Test fun fraseResumeFractionNaoEPersistidaDeProposito() {
        val f = listOf(VideoFolder("a", "n", listOf(v("t").copy(resumeFraction = 0.5f))))
        assertEquals(0f, decodeFolders(encodeFolders(f)).single().videos.single().resumeFraction, 0f)
    }

    @Test fun umRegistroRuimNaoApagaAsOutrasPastas() {
        // Hoje decodeFolders devolve lista vazia se QUALQUER registro estiver incompleto: perde todas as pastas.
        val good = encodeFolders(listOf(VideoFolder("a", "ok", listOf(v("t")))))
        val broken = good.dropLast(1) + "," + """{"id":"b","name":"ruim"}]"""
        val back = decodeFolders(broken)
        assertTrue("pasta boa deveria sobreviver", back.any { it.id == "a" })
    }
}
