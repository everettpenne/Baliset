package com.everettpenne.baliset.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.everettpenne.baliset.data.Playlist
import com.everettpenne.baliset.graph
import com.everettpenne.baliset.playback.Playback
import com.everettpenne.baliset.ui.LocalNav
import com.everettpenne.baliset.ui.Route
import com.everettpenne.baliset.ui.components.AlbumArt
import com.everettpenne.baliset.ui.components.LocalTrackActions
import com.everettpenne.baliset.ui.components.NameSheet
import com.everettpenne.baliset.ui.components.TrackRow
import com.everettpenne.baliset.util.countWord
import com.everettpenne.baliset.util.lengthWords
import com.everettpenne.glass.GlassAlert
import com.everettpenne.glass.GlassButton
import com.everettpenne.glass.GlassCenteredTopBar
import com.everettpenne.glass.GlassDropdownMenu
import com.everettpenne.glass.GlassEmptyState
import com.everettpenne.glass.GlassIconButton
import com.everettpenne.glass.GlassMenuItem
import com.everettpenne.glass.GlassScreen
import com.everettpenne.glass.GlassTopBar
import com.everettpenne.glass.IosType
import com.everettpenne.glass.contentBottomPadding

/**
 * The playlists page: Favourites (which every song can join with a heart) pinned first, then the lists the person made. Each row
 * carries its own ••• menu for rename and delete, so nothing destructive is a single mistaken tap.
 */
@Composable
fun PlaylistsScreen() {
    val context = LocalContext.current
    val graph = context.graph
    val nav = LocalNav.current
    val favorites by graph.playlists.favorites.collectAsState()
    val playlists by graph.playlists.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }

    GlassScreen(bars = {
        GlassTopBar(
            "Playlists",
            subtitle = countWord(playlists.size, "playlist"),
            actions = { GlassIconButton(Icons.Rounded.Add, "New playlist", { creating = true }) },
        )
    }) { padding ->
        if (favorites.isEmpty() && playlists.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                GlassEmptyState(
                    icon = Icons.Rounded.PlaylistAdd,
                    title = "No playlists yet",
                    message = "Make a list for a mood or a drive. Then add songs from anywhere by holding one down.",
                    action = { GlassButton("New playlist", { creating = true }, prominent = true) },
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding() + 6.dp, bottom = contentBottomPadding() + 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (favorites.isNotEmpty()) {
                    item("favorites") {
                        PlaylistRow(
                            name = "Favourites", count = favorites.size, artKey = null, favourites = true,
                            onClick = { nav?.favorites() },
                        )
                    }
                }
                items(playlists, key = { it.id }) { playlist ->
                    val artwork = remember(playlist.trackIds, graph.library.snapshot.value) {
                        graph.library.tracks(playlist.trackIds).firstOrNull()?.albumKey
                    }
                    PlaylistRow(
                        name = playlist.name, count = playlist.trackIds.size, artKey = artwork,
                        onClick = { nav?.playlist(playlist.id) },
                        trailing = {
                            Box {
                                GlassIconButton(Icons.Rounded.MoreVert, "Playlist actions", { menuFor = playlist.id }, size = 36)
                                GlassDropdownMenu(menuFor == playlist.id, { menuFor = null }) {
                                    GlassMenuItem("Rename", { menuFor = null; renaming = playlist }, icon = Icons.Rounded.Edit)
                                    GlassMenuItem("Delete", { menuFor = null; deleting = playlist }, icon = Icons.Rounded.Delete, destructive = true)
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (creating) NameSheet("New playlist", hint = "Playlist name", confirmLabel = "Create", onDone = { name ->
        val playlist = graph.playlists.create(name)
        creating = false
        nav?.playlist(playlist.id)
    }, onDismiss = { creating = false })

    renaming?.let { playlist ->
        NameSheet("Rename playlist", initial = playlist.name, onDone = { name ->
            graph.playlists.rename(playlist.id, name)
            renaming = null
        }, onDismiss = { renaming = null })
    }
    deleting?.let { playlist ->
        GlassAlert(
            "Delete playlist?",
            "\"${playlist.name}\" will be removed. The songs stay in your library.",
            "Delete", { graph.playlists.delete(playlist.id); deleting = null }, { deleting = null }, destructive = true,
        )
    }
}

@Composable
private fun PlaylistRow(
    name: String,
    count: Int,
    artKey: String?,
    onClick: () -> Unit,
    favourites: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 70.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (favourites) {
            Box(
                Modifier.size(54.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) }
        } else {
            AlbumArt(artKey, Modifier.size(54.dp), corner = 12.dp, iconSize = 20.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = IosType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(countWord(count, "song"), style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

/**
 * A playlist, or Favourites. Play and Shuffle sit under the cover; a long press on a song still opens the shared actions sheet, with
 * "Remove from this playlist" added to it.
 */
@Composable
fun PlaylistScreen(id: String) {
    val context = LocalContext.current
    val graph = context.graph
    val nav = LocalNav.current
    val actions = LocalTrackActions.current
    val playlists by graph.playlists.playlists.collectAsState()
    val favorites by graph.playlists.favorites.collectAsState()
    val snapshot by graph.library.snapshot.collectAsState()
    val now by Playback.state.collectAsState()
    val isFavorites = id == Route.FAVORITES
    val playlist = playlists.firstOrNull { it.id == id }
    val tracks = remember(isFavorites, favorites, playlists, snapshot.tracks) {
        if (isFavorites) favorites.mapNotNull { graph.library.find(it) }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        else graph.library.tracks(playlist?.trackIds.orEmpty())
    }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    if (!isFavorites && playlist == null) {
        GlassScreen(bars = { GlassCenteredTopBar("Playlist", onBack = { nav?.back() }) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                GlassEmptyState(Icons.Rounded.PlaylistAdd, "Playlist not found", "It may have been deleted.")
            }
        }
        return
    }

    val title = if (isFavorites) "Favourites" else playlist!!.name
    GlassScreen(bars = {
        GlassCenteredTopBar(title, onBack = { nav?.back() }, trailing = {
            if (!isFavorites) {
                Box {
                    GlassIconButton(Icons.Rounded.MoreVert, "Playlist actions", { menu = true }, size = 36)
                    GlassDropdownMenu(menu, { menu = false }) {
                        GlassMenuItem("Rename", { menu = false; renaming = true }, icon = Icons.Rounded.Edit)
                        GlassMenuItem("Delete", { menu = false; deleting = true }, icon = Icons.Rounded.Delete, destructive = true)
                    }
                }
            }
        })
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 6.dp, bottom = contentBottomPadding() + 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item("header") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isFavorites) {
                        Box(
                            Modifier.size(150.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .16f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(60.dp)) }
                    } else {
                        AlbumArt(tracks.firstOrNull()?.albumKey, Modifier.size(150.dp), corner = 18.dp, iconSize = 36.dp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(title, style = IosType.title2.copy(fontWeight = FontWeight.Bold), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${countWord(tracks.size, "song")} · ${lengthWords(tracks.sumOf { it.durationMs })}",
                        style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton({ Playback.playAll(tracks) }, Modifier.weight(1f), prominent = true, enabled = tracks.isNotEmpty()) {
                            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp)); Text("Play", maxLines = 1)
                        }
                        GlassButton({ Playback.playAll(tracks, shuffle = true) }, Modifier.weight(1f), enabled = tracks.isNotEmpty()) {
                            Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(18.dp)); Text("Shuffle", maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (tracks.isEmpty()) {
                item("empty") {
                    GlassEmptyState(
                        Icons.Rounded.PlaylistAdd, "Nothing here yet",
                        if (isFavorites) "Tap the heart in the player, or hold a song anywhere, to keep it here."
                        else "Hold a song anywhere and choose Add to playlist.",
                    )
                }
            }
            items(tracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    playing = now.track?.id == track.id,
                    onClick = { Playback.play(tracks, tracks.indexOf(track)) },
                    onLongClick = {
                        actions?.show(track, playlistId = if (isFavorites) null else id)
                        if (!isFavorites) actions?.onRemoveFromPlaylist = { removed -> graph.playlists.remove(id, removed.id) }
                    },
                    subtitle = track.artist,
                )
            }
        }
    }

    if (renaming && playlist != null) NameSheet("Rename playlist", initial = playlist.name, onDone = { name -> graph.playlists.rename(playlist.id, name); renaming = false }, onDismiss = { renaming = false })
    if (deleting && playlist != null) GlassAlert(
        "Delete playlist?", "\"${playlist.name}\" will be removed. The songs stay in your library.",
        "Delete", { graph.playlists.delete(playlist.id); deleting = false; nav?.back() }, { deleting = false }, destructive = true,
    )
}
