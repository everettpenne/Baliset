package com.everettpenne.baliset.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.everettpenne.baliset.data.albumsOf
import com.everettpenne.baliset.data.songsOf
import com.everettpenne.baliset.graph
import com.everettpenne.baliset.playback.Playback
import com.everettpenne.baliset.ui.LocalNav
import com.everettpenne.baliset.ui.components.AlbumArt
import com.everettpenne.baliset.ui.components.AlbumShelf
import com.everettpenne.baliset.ui.components.LocalTrackActions
import com.everettpenne.baliset.ui.components.TrackRow
import com.everettpenne.baliset.util.clock
import com.everettpenne.baliset.util.countWord
import com.everettpenne.baliset.util.greeting
import com.everettpenne.baliset.util.lengthWords
import com.everettpenne.glass.GlassButton
import com.everettpenne.glass.GlassCircleButton
import com.everettpenne.glass.GlassEmptyState
import com.everettpenne.glass.GlassIconButton
import com.everettpenne.glass.GlassScreen
import com.everettpenne.glass.GlassSectionHeader
import com.everettpenne.glass.GlassTopBar
import com.everettpenne.glass.IosType
import com.everettpenne.glass.contentBottomPadding
import java.time.LocalTime

/**
 * Home: what to do next, not a wall of controls. The last song picks up where it stopped, one big button shuffles everything, and
 * the newest albums and the songs you actually played sit under it. The greeting and the count make the page feel alive without
 * asking anything of the person.
 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val graph = context.graph
    val nav = LocalNav.current
    val actions = LocalTrackActions.current
    val snapshot by graph.library.snapshot.collectAsState()
    val now by Playback.state.collectAsState()
    val recents by graph.settings.recents.collectAsState()
    val lastId by graph.settings.lastTrackId.collectAsState()
    val lastPosition by graph.settings.lastPosition.collectAsState()

    val albums = remember(snapshot.tracks) { albumsOf(snapshot.tracks) }
    val songs = remember(snapshot.tracks) { songsOf(snapshot.tracks) }
    val last = remember(lastId, snapshot.tracks) { graph.library.find(lastId) }
    val recent = remember(recents, snapshot.tracks) { recents.mapNotNull { graph.library.find(it) } }
    val newest = remember(albums) { albums.sortedByDescending { it.artTrack.addedAt }.take(12) }
    val greetingText = remember { greeting(LocalTime.now().hour) }

    GlassScreen(bars = {
        GlassTopBar(
            "Baliset",
            subtitle = if (snapshot.tracks.isEmpty()) "Your music, privately" else "$greetingText · ${countWord(songs.size, "song")} · ${countWord(albums.size, "album")}",
            actions = {
                GlassIconButton(Icons.Rounded.Dns, "Jellyfin", { nav?.jellyfin() })
                GlassIconButton(Icons.Rounded.Settings, "Settings", { nav?.settings() })
            },
        )
    }) { padding ->
        if (snapshot.tracks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                GlassEmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = "No music yet",
                    message = "Point Baliset at the songs on this phone and they will appear here, with covers and playlists.",
                    action = { GlassButton("Find my music", { nav?.welcome() }, prominent = true) },
                )
            }
            return@GlassScreen
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = contentBottomPadding() + 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (last != null && lastPosition > 3_000) {
                item("jump") {
                    JumpBackIn(
                        title = last.title, subtitle = last.artist, albumKey = last.albumKey,
                        position = lastPosition, duration = last.durationMs,
                        onClick = { Playback.resumeLast() },
                    )
                }
            }
            item("actions") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton({ Playback.playAll(songs, shuffle = true) }, Modifier.weight(1f), prominent = true) {
                        Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(18.dp))
                        Text("Shuffle all", maxLines = 1)
                    }
                    GlassButton({ Playback.playAll(songs) }, Modifier.weight(1f)) {
                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Text("Play all", maxLines = 1)
                    }
                }
            }
            if (newest.isNotEmpty()) {
                item("new-header") { GlassSectionHeader("Recently added") }
                item("new-shelf") { AlbumShelf(newest, onOpen = { nav?.album(it.key) }) }
            }
            if (recent.isNotEmpty()) {
                item("recent-header") { GlassSectionHeader("Recently played") }
                items(recent.take(6), key = { "recent-${it.id}" }) { track ->
                    TrackRow(
                        track = track,
                        playing = now.track?.id == track.id,
                        onClick = { Playback.playTrack(track) },
                        onLongClick = { actions?.show(track) },
                        subtitle = "${track.artist} · ${track.album}",
                    )
                }
            }
            item("footer") {
                val total = songs.sumOf { it.durationMs }
                Text(
                    "${countWord(songs.size, "song")} · ${countWord(albums.size, "album")} · ${lengthWords(total)}",
                    style = IosType.caption1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun JumpBackIn(title: String, subtitle: String, albumKey: String, position: Long, duration: Long, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AlbumArt(albumKey, Modifier.size(62.dp), corner = 14.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("JUMP BACK IN", style = IosType.caption2.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
            Text(title, style = IosType.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$subtitle · ${clock(position)} of ${clock(duration)}",
                style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        GlassCircleButton({ onClick() }, size = 46.dp, tint = MaterialTheme.colorScheme.primary, description = "Resume") {
            Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
        }
    }
}
