package com.everettpenne.baliset.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.everettpenne.baliset.BuildConfig
import com.everettpenne.baliset.graph
import com.everettpenne.baliset.playback.Playback
import com.everettpenne.baliset.ui.components.LocalTrackActions
import com.everettpenne.baliset.ui.components.TrackActionSheet
import com.everettpenne.baliset.ui.components.TrackActionsState
import com.everettpenne.baliset.ui.components.rememberArtwork
import com.everettpenne.baliset.ui.screens.AlbumScreen
import com.everettpenne.baliset.ui.screens.ArtistScreen
import com.everettpenne.baliset.ui.screens.HomeScreen
import com.everettpenne.baliset.ui.screens.LibraryScreen
import com.everettpenne.baliset.ui.screens.NowPlayingScreen
import com.everettpenne.baliset.ui.screens.PlaylistScreen
import com.everettpenne.baliset.ui.screens.PlaylistsScreen
import com.everettpenne.baliset.ui.screens.QueueScreen
import com.everettpenne.baliset.ui.screens.SearchScreen
import com.everettpenne.baliset.ui.screens.SettingsScreen
import com.everettpenne.baliset.ui.screens.WelcomeScreen
import com.everettpenne.baliset.ui.theme.Baliset
import com.everettpenne.baliset.ui.theme.BalisetTheme
import com.everettpenne.glass.GlassTabBar
import com.everettpenne.media.GlassMiniPlayer
import com.everettpenne.glass.GlassTabSpec
import com.everettpenne.glass.LocalGlassBackdrop
import com.everettpenne.glass.LocalTabBar
import com.everettpenne.updater.LocalUpdater
import com.everettpenne.updater.UpdateConfig
import com.everettpenne.updater.UpdateDialog
import com.everettpenne.updater.Updater
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** The three tabs beside the search button; search is its own round control, as the kit's tab bar draws it. */
enum class Page(override val route: String, override val label: String, override val icon: ImageVector) : GlassTabSpec {
    Home(Route.HOME, "Home", Icons.Rounded.Home),
    Library(Route.LIBRARY, "Library", Icons.Rounded.LibraryMusic),
    Playlists(Route.PLAYLISTS, "Playlists", Icons.Rounded.QueueMusic),
    Search(Route.SEARCH, "Search", Icons.Rounded.Search),
}

/**
 * The shell: one navigation graph, the floating tab bar with the now-playing capsule above it, and the shared sheets and updater.
 *
 * The page colour is recorded into a sibling backdrop (as the rest of the suite does) so menus and the tab bar can bend the page
 * rather than the screens sampling themselves. Now Playing, Up Next and the welcome page hide the chrome and take the whole window.
 */
@Composable
fun BalisetRoot(openUri: Uri? = null, debugFolder: String? = null) {
    BalisetTheme {
        val context = LocalContext.current
        val graph = context.graph
        val navController = rememberNavController()
        val nav = remember(navController) { Nav(navController) }
        val backStack by navController.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val welcomeSeen by graph.settings.welcomeSeen.collectAsState()
        val snapshot by graph.library.snapshot.collectAsState()
        val now by Playback.state.collectAsState()
        val actions = remember { TrackActionsState() }
        val updater = remember {
            Updater.get(context, UpdateConfig("everettpenne/Baliset", BuildConfig.VERSION_NAME))
        }

        LaunchedEffect(Unit) {
            Playback.start(context, graph.library, graph.settings, graph.levels)
            if (!BuildConfig.DEBUG) updater.checkOnLaunch()
        }

        actions.onOpenAlbum = { key -> nav.album(key) }
        actions.onOpenArtist = { name -> nav.artist(name) }

        // "Open with Baliset" on an audio file: describe it, play it, and show the player.
        LaunchedEffect(openUri) {
            val uri = openUri ?: return@LaunchedEffect
            val track = graph.library.describe(uri) ?: return@LaunchedEffect
            Playback.play(listOf(track), 0)
            nav.now()
        }

        LaunchedEffect(debugFolder) {
            val tree = debugFolder ?: return@LaunchedEffect
            graph.settings.setFolder(tree, com.everettpenne.baliset.folderName(context, tree))
            graph.library.scanFolder(tree)
        }

        // The media notification is how playback is controlled outside the app; ask once, when the first song starts.
        var askedForNotifications by remember { mutableStateOf(false) }
        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(now.playing) {
            if (now.playing && !askedForNotifications && Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                askedForNotifications = true
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val hideChrome = route == Route.NOW || route == Route.QUEUE || route == Route.WELCOME
        val selected = Page.entries.firstOrNull { it.route == route }
        val tabBar: (@Composable () -> Unit)? = if (hideChrome) null else ({
            Column(Modifier.fillMaxWidth()) {
                if (now.track != null) {
                    GlassMiniPlayer(
                        title = now.track?.title.orEmpty(),
                        subtitle = now.track?.artist.orEmpty(),
                        artwork = rememberArtwork(now.track?.albumKey),
                        progress = now.fraction,
                        playing = now.playing,
                        accent = Baliset.accent,
                        onOpen = { nav.now() },
                        onToggle = { Playback.toggle() },
                        onNext = { Playback.next() },
                    )
                }
                GlassTabBar(
                    tabs = listOf(Page.Home, Page.Library, Page.Playlists),
                    selected = selected,
                    onSelect = { page -> if (page == Page.Search) nav.search() else nav.go(page.route) },
                    search = Page.Search,
                )
            }
        })

        val pageBackdrop = rememberLayerBackdrop()
        CompositionLocalProvider(
            LocalTabBar provides tabBar,
            LocalNav provides nav,
            LocalTrackActions provides actions,
            LocalUpdater provides updater,
        ) {
            Box(Modifier.fillMaxSize()) {
                // The page colour as a backdrop recorded beside the screens, so nothing samples a layer containing itself.
                Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop).background(MaterialTheme.colorScheme.background))
                CompositionLocalProvider(LocalGlassBackdrop provides pageBackdrop) {
                    NavHost(
                        navController,
                        startDestination = if (welcomeSeen) Route.HOME else Route.WELCOME,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = { fadeIn() }, exitTransition = { fadeOut() },
                        popEnterTransition = { fadeIn() }, popExitTransition = { fadeOut() },
                    ) {
                        composable(Route.WELCOME) { WelcomeScreen() }
                        composable(Route.HOME) { HomeScreen() }
                        composable(Route.LIBRARY) { LibraryScreen() }
                        composable(Route.PLAYLISTS) { PlaylistsScreen() }
                        composable(Route.SEARCH) { SearchScreen() }
                        composable(Route.SETTINGS) { SettingsScreen() }
                        composable(Route.NOW) { NowPlayingScreen() }
                        composable(Route.QUEUE) { QueueScreen() }
                        composable(Route.FAVORITES) { PlaylistScreen(Route.FAVORITES) }
                        composable(Route.ALBUM_PATTERN, listOf(navArgument("key") { type = NavType.StringType })) { entry ->
                            AlbumScreen(entry.arguments?.getString("key").orEmpty())
                        }
                        composable(Route.ARTIST_PATTERN, listOf(navArgument("name") { type = NavType.StringType })) { entry ->
                            ArtistScreen(entry.arguments?.getString("name").orEmpty())
                        }
                        composable(Route.PLAYLIST_PATTERN, listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                            PlaylistScreen(entry.arguments?.getString("id").orEmpty())
                        }
                    }
                }
            }
            TrackActionSheet(actions)
        }
        UpdateDialog(updater, "Baliset")
    }
}
