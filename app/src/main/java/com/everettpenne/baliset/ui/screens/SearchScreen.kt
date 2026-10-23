package com.everettpenne.baliset.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.everettpenne.baliset.data.albumsOf
import com.everettpenne.baliset.graph
import com.everettpenne.baliset.playback.Playback
import com.everettpenne.baliset.ui.LocalNav
import com.everettpenne.baliset.ui.components.AlbumShelf
import com.everettpenne.baliset.ui.components.ArtistRow
import com.everettpenne.baliset.ui.components.LocalTrackActions
import com.everettpenne.baliset.ui.components.TrackRow
import com.everettpenne.baliset.util.searchLibrary
import com.everettpenne.glass.GlassEmptyState
import com.everettpenne.glass.GlassScreen
import com.everettpenne.glass.GlassSearchField
import com.everettpenne.glass.GlassSectionHeader
import com.everettpenne.glass.IosType
import com.everettpenne.glass.contentBottomPadding
import com.everettpenne.glass.glassTopBar
import com.everettpenne.glass.progressiveBlur

/**
 * Search across songs, albums and artists at once, as you type. The field sits in the bar and samples the page beneath it; results are
 * grouped, with songs first because that is usually what "search" means in a music app. Closing goes back to wherever search was
 * opened from.
 */
@Composable
fun SearchScreen() {
    val context = LocalContext.current
    val graph = context.graph
    val nav = LocalNav.current
    val actions = LocalTrackActions.current
    val snapshot by graph.library.snapshot.collectAsState()
    val now by Playback.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, snapshot.tracks) { searchLibrary(snapshot.tracks, query) }
    val ink = MaterialTheme.colorScheme.onBackground

    GlassScreen(bars = {
        Column(
            Modifier.fillMaxWidth().glassTopBar().progressiveBlur(fromTop = true, tint = MaterialTheme.colorScheme.background)
                .statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassSearchField(query, { query = it }, Modifier.weight(1f), placeholder = "Songs, albums, artists", autoFocus = true)
                Text(
                    "Cancel", style = IosType.subheadline.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { nav?.back() }.padding(vertical = 12.dp),
                )
            }
        }
    }) { padding ->
        when {
            query.isBlank() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                GlassEmptyState(Icons.Rounded.Search, "Search your library", "Find a song, an album or an artist. Results appear as you type.")
            }
            results.isEmpty -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                GlassEmptyState(Icons.Rounded.Search, "No matches", "Nothing in your library is called \"$query\".")
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = contentBottomPadding() + 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (results.songs.isNotEmpty()) {
                    item("songs") { GlassSectionHeader("Songs") }
                    items(results.songs.take(12), key = { "song-${it.id}" }) { track ->
                        TrackRow(
                            track = track,
                            playing = now.track?.id == track.id,
                            onClick = { Playback.play(results.songs, results.songs.indexOf(track)) },
                            onLongClick = { actions?.show(track) },
                            subtitle = "${track.artist} · ${track.album}",
                        )
                    }
                }
                if (results.albums.isNotEmpty()) {
                    item("albums") { GlassSectionHeader("Albums") }
                    item("album-shelf") {
                        AlbumShelf(
                            results.albums.mapNotNull { pair -> albumsOf(pair.second).firstOrNull() },
                            onOpen = { nav?.album(it.key) },
                        )
                    }
                }
                if (results.artists.isNotEmpty()) {
                    item("artists") { GlassSectionHeader("Artists") }
                    items(results.artists.take(8), key = { "artist-${it.first}" }) { (name, tracks) ->
                        ArtistRow(name, albumsOf(tracks).size, tracks.size, albumsOf(tracks).firstOrNull()?.key, { nav?.artist(name) })
                    }
                }
            }
        }
    }
}
