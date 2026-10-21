package app.lumicast.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lumicast.domain.CastDevice
import app.lumicast.domain.TgChat
import app.lumicast.domain.TgVideo
import app.lumicast.ui.components.BeamButton
import app.lumicast.ui.components.ChatRow
import app.lumicast.ui.components.DeviceRow
import app.lumicast.ui.components.EmptyState
import app.lumicast.ui.components.VideoRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    userName: String,
    chats: List<TgChat>,
    openChat: TgChat?,
    videos: List<TgVideo>,
    devices: List<CastDevice>,
    pinned: Set<Long>,
    onTogglePin: (TgChat) -> Unit,
    loadThumbnail: suspend (TgVideo) -> String?,
    onPreview: (TgVideo) -> Unit,
    onOpenChat: (TgChat) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onCast: (TgVideo, CastDevice) -> Unit,
    onGoToPlaying: () -> Unit,
    castActive: Boolean,
    onBeam: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<TgVideo?>(null) }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (openChat != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar para a lista de chats")
                    }
                    Text(openChat.title, style = MaterialTheme.typography.titleLarge)
                }
            } else {
                Text("Olá, $userName", style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BeamButton(
                        active = castActive,
                        onClick = onBeam,
                        description = if (castActive) "Abrir transmissão em andamento" else "Escolher uma TV",
                    )
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Sair da conta")
                    }
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text(if (openChat == null) "Buscar chat" else "Buscar vídeo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )

        if (openChat == null) {
            val shown = chats.filter { it.title.contains(query, ignoreCase = true) }.sortedBy { it.id !in pinned }
            if (shown.isEmpty()) {
                EmptyState("Nenhum chat encontrado", "Tente outro nome ou limpe a busca.")
            } else {
                LazyColumn { items(shown, key = { it.id }) { ChatRow(it, pinned = it.id in pinned, onClick = { onOpenChat(it) }, onTogglePin = { onTogglePin(it) }) } }
            }
        } else {
            val shown = videos.filter { it.title.contains(query, ignoreCase = true) }
            if (shown.isEmpty()) {
                EmptyState(
                    "Nenhum vídeo neste chat",
                    "Envie um vídeo pelo Telegram e ele aparece aqui.",
                )
            } else {
                LazyColumn { items(shown, key = { it.fileId }) { VideoRow(it, loadThumbnail, onClick = { pending = it }) } }
            }
        }
    }

    pending?.let { video ->
        ModalBottomSheet(onDismissRequest = { pending = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Transmitir “${video.title}”", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = { onPreview(video); pending = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("Ver no celular")
                }
                if (devices.isEmpty()) {
                    Text(
                        "Não achamos TVs. Confira se o celular e a TV estão no mesmo Wi-Fi.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                devices.forEach { device ->
                    DeviceRow(
                        device = device,
                        active = false,
                        actionLabel = "Transmitir",
                        onClick = {
                            onCast(video, device)
                            pending = null
                            onGoToPlaying()
                        },
                    )
                }
            }
        }
    }
}
