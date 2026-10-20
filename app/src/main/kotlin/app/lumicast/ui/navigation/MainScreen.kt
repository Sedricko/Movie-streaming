package app.lumicast.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
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
import app.lumicast.ui.components.MiniPlayer
import app.lumicast.ui.screens.DevicesScreen
import app.lumicast.ui.screens.LibraryScreen
import app.lumicast.ui.screens.LoginScreen
import app.lumicast.ui.screens.NowPlayingScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Library("biblioteca", "Biblioteca", Icons.Rounded.VideoLibrary),
    Devices("tvs", "TVs", Icons.Rounded.Tv),
    Playing("reproduzindo", "Reproduzindo", Icons.Rounded.PlayCircle),
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

    fun go(tab: Tab) = nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                if (playback.video != null && route != Tab.Playing.route) {
                    MiniPlayer(playback, onOpen = { go(Tab.Playing) }, onTogglePause = vm::togglePause)
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
                    devices = devices,
                    onOpenChat = vm::open,
                    onBack = vm::closeChat,
                    onLogout = vm::logout,
                    onCast = vm::castTo,
                    onGoToPlaying = { go(Tab.Playing) },
                    castActive = playback.video != null,
                    onBeam = { go(if (playback.video != null) Tab.Playing else Tab.Devices) },
                )
            }
            composable(Tab.Devices.route) {
                DevicesScreen(devices = devices, activeDeviceId = playback.device?.id, onRescan = vm::rescan)
            }
            composable(Tab.Playing.route) {
                NowPlayingScreen(
                    state = playback,
                    onTogglePause = vm::togglePause,
                    onSeek = vm::seekTo,
                    onStop = vm::stop,
                )
            }
        }
    }
}
