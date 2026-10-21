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
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import app.lumicast.domain.VideoFolder
import app.lumicast.ui.components.FolderActions
import app.lumicast.ui.components.FolderPickerDialog
import app.lumicast.ui.components.FolderRow
import app.lumicast.ui.components.NameDialog
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
    folders: List<VideoFolder>,
    openFolder: VideoFolder?,
    downloadStatus: String?,
    folderActions: FolderActions,
    devices: List<CastDevice>,
    pinned: Set<Long>,
    onTogglePin: (TgChat) -> Unit,
    loadThumbnail: suspend (TgVideo) -> String?,
    onPreview: (TgVideo) -> Unit,
    onEnqueue: (TgVideo) -> Unit,
    onEnqueueAll: (List<TgVideo>) -> Unit,
    onDownloadVideos: (List<TgVideo>) -> Unit,
    onOpenChat: (TgChat) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onCast: (TgVideo, CastDevice) -> Unit,
    onGoToPlaying: () -> Unit,
    castActive: Boolean,
    castConnecting: Boolean,
    /** "Conectado à SHIELD", "Reconectando…" ou null quando não há TV. */
    castStatus: String?,
    onBeam: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<TgVideo?>(null) }
    // Diálogos de pasta: o vídeo em questão (ou null quando é a pasta aberta / pasta nova vazia).
    var newFolderFor by remember { mutableStateOf<List<TgVideo>>(emptyList()) }
    var askNewFolder by remember { mutableStateOf(false) }
    var addingToFolder by remember { mutableStateOf<List<TgVideo>?>(null) }
    // Seleção de vários vídeos (toque longo); some ao trocar de chat/pasta.
    var selected by remember(openChat?.id, openFolder?.id) { mutableStateOf(setOf<String>()) }
    fun keyOf(v: TgVideo) = "${v.chatId}:${v.messageId}"
    fun toggle(v: TgVideo) { selected = if (keyOf(v) in selected) selected - keyOf(v) else selected + keyOf(v) }
    val listVideos = openFolder?.videos ?: videos
    val selectedVideos = listVideos.filter { keyOf(it) in selected }
    var moving by remember { mutableStateOf<TgVideo?>(null) }
    var renamingVideo by remember { mutableStateOf<TgVideo?>(null) }
    var renamingFolder by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (openFolder != null) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar para a lista de chats")
                    }
                    Text(openFolder.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { renamingFolder = true }) { Icon(Icons.Rounded.Edit, "Renomear pasta") }
                IconButton(onClick = { folderActions.download(openFolder.id) }, enabled = downloadStatus == null && openFolder.videos.isNotEmpty()) {
                    Icon(Icons.Rounded.Download, "Baixar a pasta para o celular")
                }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Apagar pasta") }
            } else if (openChat != null) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar para a lista de chats")
                    }
                    Text(openChat.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = folderActions.downloadChat, enabled = downloadStatus == null && videos.isNotEmpty()) {
                    Icon(Icons.Rounded.Download, "Baixar todos os vídeos deste chat, um por vez")
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Text("Olá, $userName", style = MaterialTheme.typography.titleLarge)
                    castStatus?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (castConnecting) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BeamButton(
                        active = castActive,
                        connecting = castConnecting,
                        onClick = onBeam,
                        description = if (castActive) "Abrir transmissão em andamento" else "Escolher uma TV",
                    )
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Sair da conta")
                    }
                }
            }
        }

        if (selected.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Rounded.Close, "Cancelar seleção") }
                Text("${selected.size} selecionados", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { selected = listVideos.map(::keyOf).toSet() }) { Icon(Icons.Rounded.SelectAll, "Selecionar todos") }
                IconButton(onClick = { onEnqueueAll(selectedVideos); selected = emptySet() }) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Adicionar selecionados à fila")
                }
                IconButton(onClick = { addingToFolder = selectedVideos; selected = emptySet() }) {
                    Icon(Icons.Rounded.CreateNewFolder, "Adicionar selecionados a uma pasta")
                }
                IconButton(onClick = { onDownloadVideos(selectedVideos); selected = emptySet() }, enabled = downloadStatus == null) {
                    Icon(Icons.Rounded.Download, "Baixar selecionados, um por vez")
                }
                if (openFolder != null) {
                    IconButton(onClick = { selectedVideos.forEach { folderActions.removeVideo(openFolder.id, it) }; selected = emptySet() }) {
                        Icon(Icons.Rounded.RemoveCircleOutline, "Tirar selecionados da pasta")
                    }
                }
            }
        } else OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text(if (openChat == null && openFolder == null) "Buscar chat ou pasta" else "Buscar vídeo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )

        downloadStatus?.let {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                TextButton(onClick = folderActions.cancelDownload) { Text("Cancelar") }
            }
        }

        if (openChat == null && openFolder == null) {
            val shownFolders = folders.filter { it.name.contains(query, ignoreCase = true) }
            val shown = chats.filter { it.title.contains(query, ignoreCase = true) }.sortedBy { it.id !in pinned }
            LazyColumn {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Minhas pastas", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { newFolderFor = emptyList(); askNewFolder = true }) { Text("Nova pasta") }
                    }
                }
                items(shownFolders, key = { "f" + it.id }) { FolderRow(it, onClick = { folderActions.open(it) }) }
                item { Text("Chats", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, top = 12.dp)) }
                if (shown.isEmpty()) item {
                    Text(
                        "Nenhum chat encontrado. Tente outro nome ou limpe a busca.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
                items(shown, key = { it.id }) { ChatRow(it, pinned = it.id in pinned, onClick = { onOpenChat(it) }, onTogglePin = { onTogglePin(it) }) }
            }
        } else {
            val shown = (openFolder?.videos ?: videos).filter { it.title.contains(query, ignoreCase = true) }
            if (shown.isEmpty()) {
                EmptyState(
                    if (openFolder != null) "Pasta vazia" else "Nenhum vídeo neste chat",
                    if (openFolder != null) "Toque num vídeo de um chat e escolha “Adicionar à pasta”." else "Envie um vídeo pelo Telegram e ele aparece aqui.",
                )
            } else {
                LazyColumn { items(shown, key = { "${it.chatId}:${it.messageId}" }) { VideoRow(
                            it,
                            loadThumbnail,
                            onClick = { if (selected.isNotEmpty()) toggle(it) else pending = it },
                            selected = keyOf(it) in selected,
                            onLongClick = { toggle(it) },
                        ) } }
            }
        }
    }

    pending?.let { video ->
        ModalBottomSheet(onDismissRequest = { pending = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Transmitir “${video.title}”", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onPreview(video); pending = null }, modifier = Modifier.weight(1f)) {
                        Text("Ver no celular")
                    }
                    OutlinedButton(onClick = { onEnqueue(video); pending = null }, modifier = Modifier.weight(1f)) {
                        Text("Adicionar à fila")
                    }
                }
                if (openFolder == null) {
                    OutlinedButton(onClick = { addingToFolder = listOf(video); pending = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("Adicionar à pasta")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { renamingVideo = video; pending = null }, modifier = Modifier.weight(1f)) { Text("Renomear") }
                        OutlinedButton(onClick = { moving = video; pending = null }, modifier = Modifier.weight(1f)) { Text("Mover") }
                        OutlinedButton(onClick = { folderActions.removeVideo(openFolder.id, video); pending = null }, modifier = Modifier.weight(1f)) {
                            Text("Tirar")
                        }
                    }
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

    addingToFolder?.let { list ->
        FolderPickerDialog(
            title = "Adicionar à pasta",
            folders = folders,
            excludeId = null,
            onPick = { folderActions.addTo(it.id, list) },
            onNew = { newFolderFor = list; askNewFolder = true; addingToFolder = null },
            onDismiss = { addingToFolder = null },
        )
    }
    if (openFolder != null) moving?.let { video ->
        FolderPickerDialog(
            title = "Mover para",
            folders = folders,
            excludeId = openFolder.id,
            onPick = { folderActions.moveVideo(openFolder.id, video, it.id) },
            onNew = { newFolderFor = listOf(video); askNewFolder = true; folderActions.removeVideo(openFolder.id, video); moving = null },
            onDismiss = { moving = null },
        )
    }
    if (askNewFolder) {
        NameDialog("Nova pasta", "", "Criar", onConfirm = { folderActions.create(it, newFolderFor) }, onDismiss = { askNewFolder = false })
    }
    if (openFolder != null) {
        renamingVideo?.let { video ->
            NameDialog("Renomear vídeo", video.title, "Salvar", onConfirm = { folderActions.renameVideo(openFolder.id, video, it) }, onDismiss = { renamingVideo = null })
        }
        if (renamingFolder) {
            NameDialog("Renomear pasta", openFolder.name, "Salvar", onConfirm = { folderActions.rename(openFolder.id, it) }, onDismiss = { renamingFolder = false })
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Apagar “${openFolder.name}”?") },
                text = { Text("Os vídeos continuam no Telegram e no que você já baixou. Só a pasta some do Lumicast.") },
                confirmButton = { TextButton(onClick = { folderActions.delete(openFolder.id); confirmDelete = false }) { Text("Apagar") } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
            )
        }
    }
}
