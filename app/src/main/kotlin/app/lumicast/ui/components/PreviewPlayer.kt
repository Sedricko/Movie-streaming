package app.lumicast.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import app.lumicast.Diag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Assistir no celular antes de mandar para a TV. Lê do mesmo servidor local que a TV usaria. */
@Composable
fun PreviewPlayer(url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var error by remember(url) { mutableStateOf<String?>(null) }
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) {
                    Diag.log("preview", "erro: ${e.errorCodeName} ${e.cause?.message}")
                    error = "Não deu para tocar no celular (${e.errorCodeName})."
                }
            })
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    // Largura total: com a largura padrão de diálogo o player ficava estreito demais.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().background(Color.Black)) {
            AndroidView(
                factory = { PlayerView(it).apply { this.player = player; setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS) } },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            error?.let { Text(it, color = Color.White, modifier = Modifier.padding(16.dp)) }
        }
    }
}
