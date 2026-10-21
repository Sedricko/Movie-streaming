package app.lumicast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitlesTest {
    @Test fun srtViraVtt() {
        val srt = "﻿1\r\n00:00:01,500 --> 00:00:03,250\r\nOlá, mundo\r\n\r\n2\r\n00:01:02,000 --> 00:01:04,000\r\nTchau\r\n"
        assertEquals(
            "WEBVTT\n\n1\n00:00:01.500 --> 00:00:03.250\nOlá, mundo\n\n2\n00:01:02.000 --> 00:01:04.000\nTchau\n",
            srtToVtt(srt),
        )
    }

    @Test fun vttContinuaIgual() = assertEquals("WEBVTT\n\nx", srtToVtt("WEBVTT\n\nx"))

    @Test fun mesmoNomeGanha() = assertEquals(
        "30_ab4676.srt",
        pickSubtitle("30_ab4676.mp4", listOf("29_158245.srt", "30_ab4676.srt", "outro.txt")),
    )

    @Test fun nomeComIdiomaNoFim() = assertEquals("Ep 03.pt-br.srt", pickSubtitle("Ep 03.mkv", listOf("Ep 03.pt-br.srt", "Ep 04.srt")))

    @Test fun mesmoEpisodioPeloNumero() = assertEquals(
        "Legenda episodio 7.srt",
        pickSubtitle("Serie 2021 E07 1080p.mp4", listOf("Legenda episodio 6.srt", "Legenda episodio 7.srt")),
    )

    @Test fun ambiguoOuNadaNaoChuta() {
        assertNull(pickSubtitle("Ep 5.mp4", listOf("A 5.srt", "B 5.srt")))
        assertNull(pickSubtitle("Ep 5.mp4", listOf("Ep 6.srt")))
        assertNull(pickSubtitle("filme.mp4", emptyList()))
    }
}
