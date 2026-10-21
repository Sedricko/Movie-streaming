package app.lumicast.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.lumicast.domain.PlayStatus
import app.lumicast.domain.PlaybackState
import app.lumicast.ui.components.BeamLine
import app.lumicast.ui.components.EmptyState
import app.lumicast.ui.components.formatDuration
import app.lumicast.ui.theme.LumiShapes

@Composable
fun NowPlayingScreen(
    state: PlaybackState,
    onTogglePause: () -> Unit,
    onSeek: (Int) -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    hasNext: Boolean,
    modifier: Modifier = Modifier,
) {
    val video = state.video
    if (video == null) {
        EmptyState(
            title = "Nada sendo transmitido",
            message = "Escolha um vídeo na Biblioteca e uma TV para começar.",
            modifier = modifier,
        )
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

    Column(
        modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(LumiShapes.Card)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(video.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        }

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
    }
}
