package com.everettpenne.baliset.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.everettpenne.baliset.data.Playlist
import com.everettpenne.baliset.data.Track
import com.everettpenne.baliset.graph
import com.everettpenne.baliset.playback.Playback
import com.everettpenne.glass.GlassButton
import com.everettpenne.glass.GlassColors
import com.everettpenne.glass.GlassSheet
import com.everettpenne.glass.IosType
import com.everettpenne.glass.LocalGlassColors
import com.everettpenne.glass.glassPalette
import androidx.compose.ui.platform.LocalContext

/**
 * One actions sheet for every song in the app. A row's long press hands the track here, so "Play next", "Add to queue", the playlists
 * and the favourite toggle are in exactly the same place on Home, in the library, in search and on an album.
 */
class TrackActionsState {
    var track by mutableStateOf<Track?>(null)
        private set
    var visible by mutableStateOf(false)
        private set
    var playlistId by mutableStateOf<String?>(null)
        private set
    var onOpenAlbum: ((String) -> Unit)? = null
    var onOpenArtist: ((String) -> Unit)? = null
    var onRemoveFromPlaylist: ((Track) -> Unit)? = null

    fun show(track: Track, playlistId: String? = null) {
        this.track = track
        this.playlistId = playlistId
        onRemoveFromPlaylist = null
        visible = true
    }

    fun hide() { visible = false }
}

val LocalTrackActions = androidx.compose.runtime.staticCompositionLocalOf<TrackActionsState?> { null }

/** The sheet itself, drawn once at the root. */
@Composable
fun TrackActionSheet(state: TrackActionsState) {
    val track = state.track
    var addingToPlaylist by remember(track, state.visible) { mutableStateOf(false) }
    if (!state.visible || track == null) return
    val context = LocalContext.current
    val graph = context.graph
    val favorites by graph.playlists.favorites.collectAsState()
    val playlists by graph.playlists.playlists.collectAsState()
    val favourite = track.id in favorites

    GlassSheet(onDismiss = { state.hide() }) {
        if (addingToPlaylist) {
            AddToPlaylist(track, playlists, onDone = { state.hide() })
            return@GlassSheet
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AlbumArt(track.albumKey, Modifier.size(56.dp), corner = 12.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(track.title, style = IosType.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        SheetAction(Icons.Rounded.QueueMusic, "Play next") { Playback.playNext(track); state.hide() }
        SheetAction(Icons.Rounded.PlaylistPlay, "Add to queue") { Playback.addToQueue(track); state.hide() }
        SheetAction(Icons.Rounded.PlaylistAdd, "Add to playlist") { addingToPlaylist = true }
        SheetAction(
            if (favourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            if (favourite) "Remove from favourites" else "Add to favourites",
            tint = if (favourite) LocalGlassColors.current.red else null,
        ) {
            graph.playlists.toggleFavorite(track.id)
            state.hide()
        }
        if (state.onRemoveFromPlaylist != null && state.playlistId != null && track.id in (playlists.firstOrNull { it.id == state.playlistId }?.trackIds ?: emptyList())) {
            SheetAction(Icons.Rounded.RemoveCircleOutline, "Remove from this playlist", tint = LocalGlassColors.current.red) {
                state.onRemoveFromPlaylist?.invoke(track)
                state.hide()
            }
        }
        state.onOpenAlbum?.let { open ->
            SheetAction(Icons.Rounded.MusicNote, "Go to album") { state.hide(); open(track.albumKey) }
        }
        state.onOpenArtist?.let { open ->
            SheetAction(Icons.Rounded.Person, "Go to artist") { state.hide(); open(track.artist) }
        }
    }
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color? = null, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = tint ?: ink, modifier = Modifier.size(22.dp))
        Text(label, style = IosType.body, color = if (tint != null) tint else ink)
    }
}

@Composable
private fun AddToPlaylist(track: Track, playlists: List<Playlist>, onDone: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Add to playlist", style = IosType.title3.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
        Text("Done", style = IosType.subheadline.copy(fontWeight = FontWeight.SemiBold), color = LocalGlassColors.current.accent,
            modifier = Modifier.clickable(onClick = onDone).padding(8.dp))
    }
    if (creating) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(46.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(23.dp)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                if (name.isEmpty()) Text("Playlist name", style = IosType.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(name, { name = it }, singleLine = true, textStyle = IosType.body.copy(color = glassPalette().ink), cursorBrush = SolidColor(LocalGlassColors.current.accent))
            }
            GlassButton("Create", {
                val playlist = graph.playlists.create(name)
                graph.playlists.add(playlist.id, listOf(track.id))
                onDone()
            }, enabled = name.isNotBlank(), prominent = true)
        }
    } else {
        SheetAction(Icons.Rounded.Add, "New playlist") { creating = true }
    }
    if (playlists.isEmpty() && !creating) {
        Text("No playlists yet. Make one to keep this song.", style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp))
    }
    playlists.forEach { playlist ->
        val contains = track.id in playlist.trackIds
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable {
                if (contains) graph.playlists.remove(playlist.id, track.id) else graph.playlists.add(playlist.id, listOf(track.id))
            }.padding(horizontal = 22.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AlbumArt(graph.library.tracks(playlist.trackIds).firstOrNull()?.albumKey, Modifier.size(36.dp), corner = 8.dp, iconSize = 16.dp)
            Text(playlist.name, style = IosType.body, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (contains) Icon(Icons.Rounded.Check, "Added", tint = LocalGlassColors.current.accent, modifier = Modifier.size(20.dp))
        }
    }
}
