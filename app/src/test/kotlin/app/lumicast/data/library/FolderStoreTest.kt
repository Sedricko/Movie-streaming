package app.lumicast.data.library

import app.lumicast.domain.TgVideo
import app.lumicast.domain.VideoFolder
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderStoreTest {
    private val video = TgVideo(7, -100L, 42L, "Episódio 1", 600, 200L * 1024 * 1024, "video/mp4", thumbnailFileId = 9)

    @Test fun pastasSobrevivemAoSalvarELer() {
        val folders = listOf(VideoFolder("a", "Animes", listOf(video, video.copy(fileId = 8, thumbnailFileId = null))), VideoFolder("b", "Vazia"))
        assertEquals(folders, decodeFolders(encodeFolders(folders)))
    }

    @Test fun jsonCorrompidoViraListaVazia() = assertEquals(emptyList<VideoFolder>(), decodeFolders("{quebrado"))

    @Test fun nomeDeArquivoSeguro() {
        assertEquals("Ep_1_ parte 2.mp4", safeFileName("Ep/1: parte 2"))
        assertEquals("31_6d1774.mp4", safeFileName("31_6d1774.mp4"))
        assertEquals("show.mkv", safeFileName("show", "video/x-matroska"))
        assertEquals("video.mp4", safeFileName(".."))
    }
}
