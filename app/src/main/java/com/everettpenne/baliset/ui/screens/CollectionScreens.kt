package com.everettpenne.baliset.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.everettpenne.baliset.data.Album
import com.everettpenne.baliset.data.albumsOf
import com.everettpenne.baliset.graph
import com.everettpenne.baliset.playback.Playback
import com.everettpenne.baliset.ui.LocalNav
import com.everettpenne.baliset.ui.components.AlbumArt
import com.everettpenne.baliset.ui.components.AlbumShelf
import com.everettpenne.baliset.ui.components.LocalTrackActions
import com.everettpenne.baliset.ui.components.TrackRow
import com.everettpenne.baliset.util.countWord
import com.everettpenne.baliset.util.lengthWords
import com.everettpenne.glass.GlassButton
import com.everettpenne.glass.GlassCenteredTopBar
import com.everettpenne.glass.GlassDropdownMenu
import com.everettpenne.glass.GlassEmptyState
import com.everettpenne.glass.GlassIconButton
import com.everettpenne.glass.GlassMenuItem
import com.everettpenne.glass.GlassScreen
import com.everettpenne.glass.IosType
import com.everettpenne.glass.contentBottomPadding

/** One album: a big cover, who made it and when, Play and Shuffle, then the tracks in disc order. */
@Composable
fun AlbumScreen(key: String) {
    val context = LocalContext.current
    val graph = context.graph
    val nav = LocalNav.current
    val actions = LocalTrackActions.current
    val snapshot by graph.library.snapshot.collectAsState()
    val now by Playback.state.collectAsState()
    val albums = remember(snapshot.tracks) { albumsOf(snapshot.tracks) }
    val album: Album? = albums.firstOrNull { it.key == key }
    var menu by remember { mutableStateOf(false) }

    GlassScreen(bars = {
        GlassCenteredTopBar("Album", onBack = { nav?.back() }, trailing = {
            Box {
                GlassIconButton(Icons.Rounded.MoreVert, "Album actions", { menu = true })
                GlassDropdownMenu(menu, { menu = false }) {
                    GlassMenuItem("Play", { menu = false; album?.let { Playback.playAll(it.tracks) } }, icon = Icons.Rounded.PlayArrow)
                    GlassMenuItem("Shuffle", { menu = false; album?.let { Playback.playAll(it.tracks, shuffle = true) } }, icon = Icons.Rounded.Shuffle)
                    GlassMenuItem("Add to queue", {
                        menu = false
                        album?.tracks?.forEach { Playback.addToQueue(it) }
                    }, icon = Icons.Rounded.QueueMusic)
                }
            }
        })
    }) { padding ->
        if (album == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                GlassEmptyState(Icons.Rounded.QueueMusic, "Album not found", "It may have moved since the last scan.")
            }
            return@GlassScreen
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 6.dp, bottom = contentBottomPadding() + 8.dp),
        ) {
            item("header") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AlbumArt(album.key, Modifier.fillMaxWidth(0.62f).aspectRatio(1f), corner = 18.dp, iconSize = 40.dp)
                    Spacer(Modifier.height(18.dp))
                    Text(album.title, style = IosType.title2.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        album.artist,
                        style = IosType.subheadline,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { nav?.artist(album.artist) }.padding(vertical = 4.dp),
                    )
                    Text(
                        listOfNotNull(
                            album.year.takeIf { it > 0 }?.toString(),
                            countWord(album.tracks.size, "song"),
                            lengthWords(album.tracks.sumOf { it.durationMs }),
                        ).joinToString(" · "),
                        style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton({ Playback.playAll(album.tracks) }, Modifier.weight(1f), prominent = true) {
                            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp)); Text("Play", maxLines = 1)
                        }
                        GlassButton({ Playback.playAll(album.tracks, shuffle = true) }, Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(18.dp)); Text("Shuffle", maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            itemsIndexed(album.tracks, key = { _, track -> track.id }) { index, track ->
                TrackRow(
                    track = track,
                    playing = now.track?.id == track.id,
                    onClick = { Playback.play(album.tracks, index) },
                    onLongClick = { actions?.show(track) },
                    showArtwork = false,
                    trackNumber = index + 1,
                    subtitle = null,
                )
            }
        }
    }
}

/** One artist: their portrait, everything they have here, their albums, and every song. */
@Composable
fun ArtistScreen(name: String) {
    val context = LocalContext.current
    val graph = context.graph
    val nav = LocalNav.current
    val actions = LocalTrackActions.current
    val snapshot by graph.library.snapshot.collectAsState()
    val now by Playback.state.collectAsState()
    val artists = remember(snapshot.tracks) { com.everettpenne.baliset.data.artistsOf(snapshot.tracks) }
    val artist = artists.firstOrNull { it.name == name }
    val albums = remember(artist) { artist?.albums.orEmpty() }

    GlassScreen(bars = { GlassCenteredTopBar("Artist", onBack = { nav?.back() }) }) { padding ->
        if (artist == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                GlassEmptyState(Icons.Rounded.QueueMusic, "Artist not found", "They may not be in the library any more.")
            }
            return@GlassScreen
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 6.dp, bottom = contentBottomPadding() + 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item("header") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AlbumArt(albums.firstOrNull()?.key, Modifier.size(150.dp), corner = 75.dp, iconSize = 40.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(artist.name, style = IosType.title2.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
                    Text(
                        "${countWord(albums.size, "album")} · ${countWord(artist.tracks.size, "song")}",
                        style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton({ Playback.playAll(artist.tracks, shuffle = true) }, Modifier.weight(1f), prominent = true) {
                            Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(18.dp)); Text("Shuffle", maxLines = 1)
                        }
                        GlassButton({ Playback.playAll(artist.tracks) }, Modifier.weight(1f)) {
                            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp)); Text("Play", maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (albums.size > 1) {
                item("albums-header") { com.everettpenne.glass.GlassSectionHeader("Albums") }
                item("albums") { AlbumShelf(albums, onOpen = { nav?.album(it.key) }) }
                item("songs-header") { com.everettpenne.glass.GlassSectionHeader("Songs") }
            }
            itemsIndexed(artist.tracks, key = { _, track -> track.id }) { index, track ->
                TrackRow(
                    track = track,
                    playing = now.track?.id == track.id,
                    onClick = { Playback.play(artist.tracks, index) },
                    onLongClick = { actions?.show(track) },
                    subtitle = track.album,
                )
            }
        }
    }
}
