package app.lumicast.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.lumicast.MainViewModel
import app.lumicast.domain.AuthState
import app.lumicast.domain.PlayStatus
import app.lumicast.ui.components.FolderActions
import app.lumicast.ui.components.MiniPlayer
import app.lumicast.ui.components.TransferBar
import app.lumicast.ui.components.PreviewPlayer
import app.lumicast.ui.components.SettingsSheet
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lumicast.ui.screens.LibraryScreen
import app.lumicast.ui.screens.LoginScreen
import app.lumicast.ui.screens.TvScreen

/** Duas abas: escolher vídeos (Biblioteca) e tudo o que acontece na TV (TV). */
private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Library("biblioteca", "Biblioteca", Icons.Rounded.VideoLibrary),
    Tv("tv", "TV", Icons.Rounded.Tv),
}

@Composable
fun MainScreen(vm: MainViewModel) {
    val auth by vm.auth.collectAsStateWithLifecycle()
    val ready = auth as? AuthState.Ready

    if (ready == null) {
        LoginScreen(
            state = auth,
            onPhone = vm::submitPhone,
            onCode = vm::submitCode,
            onResendCode = vm::resendCode,
            onPassword = vm::submitPassword,
        )
        return
    }

    LaunchedEffect(Unit) { vm.loadChats() }

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    val chats by vm.chats.collectAsStateWithLifecycle()
    val openChat by vm.openChat.collectAsStateWithLifecycle()
    val videos by vm.videos.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val pinned by vm.pinned.collectAsStateWithLifecycle()
    val previewUrl by vm.previewUrl.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val openFolder by vm.openFolder.collectAsStateWithLifecycle()
    val downloadStatus by vm.downloadStatus.collectAsStateWithLifecycle()
    val folderActions = remember(vm) {
        FolderActions(
            open = vm::showFolder, create = vm::createFolder, addTo = vm::addToFolder, rename = vm::renameFolder,
            delete = vm::deleteFolder, renameVideo = vm::renameVideo,
            moveVideo = vm::moveVideo, removeVideo = vm::removeFromFolder,
        )
    }
    val scheduledAt by vm.scheduledAt.collectAsStateWithLifecycle()
    val switchEveryMin by vm.switchEveryMin.collectAsStateWithLifecycle()
    val trimStart by vm.trimStart.collectAsStateWithLifecycle()
    val trimEnd by vm.trimEnd.collectAsStateWithLifecycle()
    val selectedTv by vm.selectedTv.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val conversion by vm.conversion.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) SettingsSheet(settings, onChange = vm::updateSettings, onDismiss = { showSettings = false })

    previewUrl?.let { PreviewPlayer(it, onDismiss = { vm.endPreview() }) }

    fun go(tab: Tab) = nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                downloadStatus?.let { TransferBar(it, onCancel = vm::cancelDownload) }
                conversion?.let { TransferBar(it, onCancel = vm::cancelConversion) }
                if (playback.video != null && route != Tab.Tv.route) {
                    MiniPlayer(playback, onOpen = { go(Tab.Tv) }, onTogglePause = vm::togglePause)
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { go(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(nav, startDestination = Tab.Library.route, modifier = Modifier.padding(inner)) {
            composable(Tab.Library.route) {
                LibraryScreen(
                    userName = ready.userName,
                    chats = chats,
                    openChat = openChat,
                    videos = videos,
                    folders = folders,
                    openFolder = openFolder,
                    downloadStatus = downloadStatus,
                    folderActions = folderActions,
                    devices = devices,
                    tv = selectedTv,
                    onPickTv = vm::selectTv,
                    onRescan = vm::rescan,
                    onShareDiagnostics = vm::shareDiagnostics,
                    onOpenSettings = { showSettings = true },
                    pinned = pinned,
                    onTogglePin = vm::togglePin,
                    loadThumbnail = vm::thumbnail,
                    onPreview = vm::preview,
                    onEnqueue = vm::enqueue,
                    onEnqueueAll = vm::enqueueAll,
                    onDownloadVideos = vm::downloadVideos,
                    onOpenChat = vm::open,
                    onBack = vm::closeChat,
                    onLogout = vm::logout,
                    onCast = vm::castTo,
                    onGoToPlaying = { go(Tab.Tv) },
                    castActive = playback.video != null || playback.device != null,
                    castConnecting = playback.status in listOf(PlayStatus.Connecting, PlayStatus.Buffering, PlayStatus.Reconnecting),
                    castStatus = playback.device?.takeIf { playback.video == null }?.let { "● Conectado à ${it.name}" }
                        ?: playback.device?.takeIf { playback.video != null }?.let { tv ->
                        when (playback.status) {
                            PlayStatus.Connecting, PlayStatus.Buffering -> "Conectando à ${tv.name}…"
                            PlayStatus.Reconnecting -> "Conexão instável. Reconectando à ${tv.name}…"
                            PlayStatus.Idle, PlayStatus.Ended -> null
                            else -> "● Conectado à ${tv.name}"
                        }
                    } ?: selectedTv?.let { "TV: ${it.name}" },
                    onBeam = { go(Tab.Tv) },
                )
            }
            composable(Tab.Tv.route) {
                TvScreen(
                    playback = playback,
                    tv = selectedTv,
                    devices = devices,
                    onPickTv = vm::selectTv,
                    onRescan = vm::rescan,
                    onTogglePause = vm::togglePause,
                    onSeek = vm::seekTo,
                    onStop = vm::stop,
                    onPrevious = vm::previous,
                    onNext = vm::next,
                    canSkipNext = queue.isNotEmpty() || settings.autoNext,
                    onVolume = vm::setVolume,
                    onSpeed = vm::setSpeed,
                    queue = queue,
                    scheduledAt = scheduledAt,
                    switchEveryMin = switchEveryMin,
                    trimStart = trimStart,
                    trimEnd = trimEnd,
                    onTrim = vm::setTrim,
                    onMove = vm::moveInQueue,
                    onRemove = vm::removeFromQueue,
                    onSwitchEvery = vm::setSwitchEvery,
                    onStartQueue = vm::startQueue,
                    onCancelSchedule = vm::cancelSchedule,
                    onDownloadQueue = vm::downloadQueue,
                    onClearQueue = vm::clearQueue,
                    onSortQueue = vm::sortQueue,
                )
            }
        }
    }
}
