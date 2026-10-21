package app.lumicast.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lumicast.domain.PlayStatus
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.lumicast.domain.PlaybackState
import app.lumicast.ui.components.BeamLine
import app.lumicast.ui.components.formatDuration

/** O que está na TV agora, com os controles. Fica no topo da aba TV. */
@Composable
fun NowPlayingSection(
    state: PlaybackState,
    onTogglePause: () -> Unit,
    onSeek: (Int) -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    hasNext: Boolean,
    onVolume: (Float) -> Unit,
    onSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val video = state.video
    if (video == null) {
        Text("Nada tocando. Na Biblioteca, toque num vídeo para transmitir.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
        return
    }

    val statusText = when (state.status) {
        PlayStatus.Connecting -> "Conectando à ${state.device?.name}…"
        PlayStatus.Buffering -> "Carregando…"
        PlayStatus.Playing -> "Transmitindo na ${state.device?.name}"
        PlayStatus.Paused -> "Pausado na ${state.device?.name}"
        PlayStatus.Idle -> ""
        PlayStatus.Ended -> "Terminou na ${state.device?.name}"
        PlayStatus.Reconnecting -> "Conexão instável. Reconectando à ${state.device?.name}…"
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(video.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Smartphone, contentDescription = "Celular", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f)) {
                BeamLine(buffering = state.status in listOf(PlayStatus.Connecting, PlayStatus.Buffering, PlayStatus.Reconnecting))
            }
            Icon(Icons.Rounded.Tv, contentDescription = "TV", tint = MaterialTheme.colorScheme.primary)
        }
        Text(statusText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Column {
            // M5: trocar por barra própria mostrando o trecho já baixado em tom mais claro.
            Slider(
                value = state.positionSec.toFloat(),
                onValueChange = { onSeek(it.toInt()) },
                valueRange = 0f..video.durationSec.toFloat(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(state.positionSec), style = MaterialTheme.typography.bodySmall)
                Text(
                    "Baixado ${(state.downloadedFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(formatDuration(video.durationSec), style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Vídeo anterior")
            }
            IconButton(onClick = { onSeek((state.positionSec - 10).coerceAtLeast(0)) }) {
                Icon(Icons.Rounded.Replay10, contentDescription = "Voltar 10 segundos")
            }
            FilledIconButton(onClick = onTogglePause, modifier = Modifier.size(72.dp), shape = CircleShape) {
                Icon(
                    if (state.status == PlayStatus.Playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.status == PlayStatus.Playing) "Pausar" else "Reproduzir",
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(onClick = { onSeek((state.positionSec + 10).coerceAtMost(video.durationSec)) }) {
                Icon(Icons.Rounded.Forward10, contentDescription = "Avançar 10 segundos")
            }
            IconButton(onClick = onNext, enabled = hasNext) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Próximo vídeo da fila")
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Rounded.Stop, contentDescription = "Parar transmissão")
            }
        }

        // Volume (quando a TV informa) e velocidade.
        var speedMenu by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            state.volume?.let { vol ->
                Icon(Icons.AutoMirrored.Rounded.VolumeDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                var dragging by remember(vol) { mutableStateOf(vol) }
                Slider(
                    value = dragging,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = { onVolume(dragging) }, // um comando ao soltar, não um por pixel (DLNA engasga)
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Volume da TV" },
                )
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } ?: Spacer(Modifier.weight(1f))
            Box {
                TextButton(onClick = { speedMenu = true }) { Text("${formatSpeed(state.speed)}×") }
                DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    SPEEDS.forEach { r ->
                        DropdownMenuItem(text = { Text("${formatSpeed(r)}×") }, onClick = { speedMenu = false; onSpeed(r) })
                    }
                }
            }
        }
    }
}

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

private fun formatSpeed(r: Float) = if (r % 1f == 0f) r.toInt().toString() else r.toString().replace('.', ',')
