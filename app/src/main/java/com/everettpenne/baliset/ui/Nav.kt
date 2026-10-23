package com.everettpenne.baliset.ui

import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController

/** The routes the app navigates between. The album key and an artist name travel URL-encoded. */
object Route {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val PLAYLISTS = "playlists"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val JELLYFIN = "jellyfin"
    const val WELCOME = "welcome"
    const val NOW = "now"
    const val QUEUE = "queue"
    const val FAVORITES = "favorites"
    fun album(key: String) = "album/${Uri.encode(key)}"
    fun artist(name: String) = "artist/${Uri.encode(name)}"
    fun playlist(id: String) = "playlist/${Uri.encode(id)}"
    const val ALBUM_PATTERN = "album/{key}"
    const val ARTIST_PATTERN = "artist/{name}"
    const val PLAYLIST_PATTERN = "playlist/{id}"
}

/** Navigation, the way the screens use it: by name, not by string-building at every call site. */
class Nav(private val controller: NavController) {
    fun home() = tab(Route.HOME)
    fun library() = tab(Route.LIBRARY)
    fun playlists() = tab(Route.PLAYLISTS)
    fun search() = controller.navigate(Route.SEARCH) { launchSingleTop = true }
    fun settings() = controller.navigate(Route.SETTINGS) { launchSingleTop = true }
    fun jellyfin() = controller.navigate(Route.JELLYFIN) { launchSingleTop = true }
    fun welcome() = controller.navigate(Route.WELCOME) { launchSingleTop = true }
    fun now() = controller.navigate(Route.NOW) { launchSingleTop = true }
    fun queue() = controller.navigate(Route.QUEUE) { launchSingleTop = true }
    fun favorites() = controller.navigate(Route.FAVORITES) { launchSingleTop = true }
    fun album(key: String) = controller.navigate(Route.album(key)) { launchSingleTop = true }
    fun artist(name: String) = controller.navigate(Route.artist(name)) { launchSingleTop = true }
    fun playlist(id: String) = controller.navigate(Route.playlist(id)) { launchSingleTop = true }
    fun back() { controller.popBackStack() }
    /** A tab: returns to Home first so the bar never stacks pages. */
    fun go(route: String) = tab(route)
    private fun tab(route: String) = controller.navigate(route) {
        popUpTo(Route.HOME) { inclusive = false }
        launchSingleTop = true
    }
}

val LocalNav = staticCompositionLocalOf<Nav?> { null }
