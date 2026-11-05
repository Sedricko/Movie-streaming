package app.lumicast.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.ContentScale
import app.lumicast.domain.TgVideo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lumicast.domain.PlayStatus
import app.lumicast.domain.PlaybackState
import app.lumicast.ui.theme.LumiShapes

@Composable
fun MiniPlayer(state: PlaybackState, loadThumbnail: suspend (TgVideo) -> String?, onOpen: () -> Unit, onTogglePause: () -> Unit, modifier: Modifier = Modifier) {
    val video = state.video ?: return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(LumiShapes.Card)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen)
            .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val art = rememberThumb(video, loadThumbnail)
        Box(Modifier.size(48.dp).clip(LumiShapes.Thumb).background(MaterialTheme.colorScheme.surface)) {
            art?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Column(Modifier.weight(1f)) {
            Text(video.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val live = state.status == PlayStatus.Playing
            Text(
                if (live) "Transmitindo na ${state.device?.name}" else "Na ${state.device?.name}",
                style = MaterialTheme.typography.bodySmall,
                color = if (live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onTogglePause) {
            val playing = state.status == PlayStatus.Playing
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pausar" else "Reproduzir",
            )
        }
    }
}
