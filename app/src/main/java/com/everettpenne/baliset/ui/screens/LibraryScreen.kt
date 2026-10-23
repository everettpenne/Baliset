package com.everettpenne.baliset.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.everettpenne.baliset.data.LibrarySnapshot
import com.everettpenne.baliset.data.albumsOf
import com.everettpenne.baliset.data.artistsOf
import com.everettpenne.baliset.data.songsOf
import com.everettpenne.baliset.graph
import com.everettpenne.baliset.playback.Playback
import com.everettpenne.baliset.ui.LocalNav
import com.everettpenne.baliset.ui.components.AlbumTile
import com.everettpenne.baliset.ui.components.ArtistRow
import com.everettpenne.baliset.ui.components.LocalTrackActions
import com.everettpenne.baliset.ui.components.TrackRow
import com.everettpenne.baliset.util.countWord
import com.everettpenne.glass.GlassChip
import com.everettpenne.glass.GlassIconButton
import com.everettpenne.glass.GlassScreen
import com.everettpenne.glass.GlassTopBar
import com.everettpenne.glass.IosType
import com.everettpenne.glass.contentBottomPadding

private enum class Shelf { Albums, Artists, Songs }

/**
 * The library, split three ways: albums (a grid, because covers are the point), artists (round portraits, as the original does), and
 * songs (one long alphabetical list). The choice is a row of chips under the title, so all three are one tap apart and the page never
 * reshuffles under your thumb.
 */
@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val graph = context.graph
    val nav = LocalNav.current
    val actions = LocalTrackActions.current
    val snapshot by graph.library.snapshot.collectAsState()
    val scan by graph.library.scan.collectAsState()
    val now by Playback.state.collectAsState()
    var shelf by rememberSaveable { mutableStateOf(Shelf.Albums) }

    val albums = remember(snapshot.tracks) { albumsOf(snapshot.tracks) }
    val artists = remember(snapshot.tracks) { artistsOf(snapshot.tracks) }
    val songs = remember(snapshot.tracks) { songsOf(snapshot.tracks) }

    fun rescan() {
        val folder = graph.settings.folder.value
        if (graph.settings.source.value == LibrarySnapshot.SOURCE_FOLDER && folder != null) graph.library.scanFolder(folder)
        else graph.library.scanMedia()
    }

    GlassScreen(bars = {
        GlassTopBar(
            "Library",
            subtitle = "${countWord(songs.size, "song")} · ${countWord(albums.size, "album")}",
            actions = { GlassIconButton(Icons.Rounded.Refresh, "Rescan library", { rescan() }) },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Shelf.entries.forEach { option ->
                    GlassChip(shelf == option, { shelf = option }) {
                        Text(option.name, maxLines = 1)
                    }
                }
            }
            if (scan.running) {
                Text("Updating… ${scan.found} songs so far", style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
            }
            if (scan.error != null) {
                Text(scan.error!!, style = IosType.footnote, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
            }
            when (shelf) {
                Shelf.Albums -> LazyVerticalGrid(
                    GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = contentBottomPadding() + 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(albums, key = { it.key }) { album -> AlbumTile(album, { nav?.album(album.key) }) }
                }
                Shelf.Artists -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 6.dp, bottom = contentBottomPadding() + 8.dp),
                ) {
                    items(artists, key = { it.name }) { artist ->
                        val covers = remember(artist) { artist.albums.firstOrNull()?.key }
                        ArtistRow(artist.name, artist.albums.size, artist.tracks.size, covers, { nav?.artist(artist.name) })
                    }
                }
                Shelf.Songs -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 4.dp, bottom = contentBottomPadding() + 8.dp),
                ) {
                    items(songs, key = { it.id }) { track ->
                        TrackRow(
                            track = track,
                            playing = now.track?.id == track.id,
                            onClick = { Playback.play(songs, songs.indexOf(track)) },
                            onLongClick = { actions?.show(track) },
                            subtitle = "${track.artist} · ${track.album}",
                        )
                    }
                }
            }
        }
    }
}
