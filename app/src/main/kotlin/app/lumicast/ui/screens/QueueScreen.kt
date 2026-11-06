package app.lumicast.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lumicast.domain.CastDevice
import app.lumicast.domain.TgVideo

/** Aba Fila: planejar e baixar (ordem, estado de cada vídeo, Iniciar, Limpar). O controle da TV fica na aba TV. */
@Composable
fun QueueScreen(
    queue: List<TgVideo>,
    queueNote: (TgVideo) -> String?,
    tv: CastDevice?,
    playing: Boolean,
    busy: Boolean,
    downloading: Boolean,
    scheduledAt: Long?,
    switchEveryMin: Int,
    trimStart: Int,
    trimEnd: Int,
    onTrim: (Int, Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onSwitchEvery: (Int) -> Unit,
    onStart: (CastDevice, Long?) -> Unit,
    onCancelSchedule: () -> Unit,
    onDownloadQueue: () -> Unit,
    onClearQueue: () -> Unit,
    onClearPlayed: () -> Unit,
    onPlayItem: (Int) -> Unit,
    onSort: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
        QueueSection(
            queue = queue, queueNote = queueNote, tv = tv, playing = playing,
            scheduledAt = scheduledAt, switchEveryMin = switchEveryMin, trimStart = trimStart, trimEnd = trimEnd,
            onTrim = onTrim, onMove = onMove, onRemove = onRemove, onSwitchEvery = onSwitchEvery, onStart = onStart,
            onCancelSchedule = onCancelSchedule, onDownloadQueue = onDownloadQueue, onClearQueue = onClearQueue,
            onClearPlayed = onClearPlayed, onPlayItem = onPlayItem, onSort = onSort,
            busy = busy, downloading = downloading,
        )
    }
}
