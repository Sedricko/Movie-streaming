package app.lumicast.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lumicast.domain.CastDevice
import app.lumicast.domain.PlaybackState
import app.lumicast.domain.TgVideo
import app.lumicast.ui.components.TvPicker

/** Tudo sobre a TV num lugar só: qual TV, o que está tocando e o que vem a seguir. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvScreen(
    playback: PlaybackState,
    tv: CastDevice?,
    devices: List<CastDevice>,
    onPickTv: (CastDevice) -> Unit,
    onRescan: () -> Unit,
    onTogglePause: () -> Unit,
    onSeek: (Int) -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    queue: List<TgVideo>,
    scheduledAt: Long?,
    switchEveryMin: Int,
    trimStart: Int,
    trimEnd: Int,
    onTrim: (Int, Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onSwitchEvery: (Int) -> Unit,
    onStartQueue: (CastDevice, Long?) -> Unit,
    onCancelSchedule: () -> Unit,
    onDownloadQueue: () -> Unit,
    onClearQueue: () -> Unit,
    onSortQueue: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("TV", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(tv?.name ?: "Nenhuma escolhida", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = { picking = true }) { Text(if (tv == null) "Escolher" else "Trocar") }
        }

        NowPlayingSection(
            state = playback,
            onTogglePause = onTogglePause,
            onSeek = onSeek,
            onStop = onStop,
            onPrevious = onPrevious,
            onNext = onNext,
            hasNext = queue.isNotEmpty(),
        )

        HorizontalDivider()

        QueueSection(
            queue = queue,
            tv = tv,
            playing = playback.video != null,
            scheduledAt = scheduledAt,
            switchEveryMin = switchEveryMin,
            trimStart = trimStart,
            trimEnd = trimEnd,
            onTrim = onTrim,
            onMove = onMove,
            onRemove = onRemove,
            onSwitchEvery = onSwitchEvery,
            onStart = onStartQueue,
            onCancelSchedule = onCancelSchedule,
            onDownloadQueue = onDownloadQueue,
            onClearQueue = onClearQueue,
            onSort = onSortQueue,
        )
    }

    if (picking) {
        ModalBottomSheet(onDismissRequest = { picking = false }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Escolher TV", style = MaterialTheme.typography.titleLarge)
                TvPicker(devices, tv?.id, onPick = { onPickTv(it); picking = false }, onRescan = onRescan)
            }
        }
    }
}
