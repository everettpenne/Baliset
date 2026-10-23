package com.everettpenne.baliset.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.everettpenne.baliset.data.Track
import com.everettpenne.baliset.graph
import com.everettpenne.baliset.playback.Playback
import com.everettpenne.baliset.ui.LocalNav
import com.everettpenne.baliset.ui.components.LocalTrackActions
import com.everettpenne.baliset.ui.components.rememberArtwork
import com.everettpenne.baliset.ui.theme.Baliset
import com.everettpenne.glass.GlassChip
import com.everettpenne.glass.GlassDropdownMenu
import com.everettpenne.glass.GlassMenuItem
import com.everettpenne.glass.GlassSheet
import com.everettpenne.glass.IosType
import com.everettpenne.glass.LocalGlassColors
import com.everettpenne.glass.pressSwell
import com.everettpenne.media.PlayerScrubber

/**
 * Now Playing, drawn for the dark: the cover blurred to the edges of the screen, the title and artist, a scrubber you can drag, and
 * five controls in the order every phone music player has taught people to expect. The background is always dark, so the controls and
 * text are white regardless of the system theme.
 *
 * Swipe down or tap the chevron to leave; the min player stays behind on every other page.
 */
@Composable
fun NowPlayingScreen() {
    val context = LocalContext.current
    val graph = context.graph
    val nav = LocalNav.current
    val actions = LocalTrackActions.current
    val state by Playback.state.collectAsState()
    val favorites by graph.playlists.favorites.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var sleep by remember { mutableStateOf(false) }
    var dragging by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 140.dp.toPx() }
    val track = state.track

    Box(Modifier.fillMaxSize().background(Baliset.night)) {
        val art = rememberArtwork(track?.albumKey)
        if (art != null) {
            Image(
                art, null,
                modifier = Modifier.fillMaxSize().blur(72.dp).graphicsLayer { scaleX = 1.3f; scaleY = 1.3f },
                contentScale = ContentScale.Crop, alpha = .55f,
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .28f), Color.Black.copy(alpha = .55f), Baliset.night))))

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = 0f },
                        onDragEnd = { if (dragging > threshold) nav?.back(); dragging = 0f },
                        onDragCancel = { dragging = 0f },
                    ) { _, amount -> dragging += amount }
                },
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                LightCircle(Icons.Rounded.ExpandMore, "Close", { nav?.back() })
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM", style = IosType.caption2.copy(fontWeight = FontWeight.SemiBold), color = Color.White.copy(alpha = .5f))
                    Text(
                        track?.album?.takeIf { it.isNotBlank() } ?: "Your library",
                        style = IosType.caption1.copy(fontWeight = FontWeight.SemiBold), color = Color.White.copy(alpha = .9f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    LightCircle(Icons.Rounded.MoreHoriz, "More", { menu = true })
                    GlassDropdownMenu(menu, { menu = false }) {
                        GlassMenuItem("Up Next", { menu = false; nav?.queue() }, icon = Icons.Rounded.QueueMusic)
                        GlassMenuItem("Sleep timer", { menu = false; sleep = true }, icon = Icons.Rounded.Bedtime)
                        track?.let { t ->
                            GlassMenuItem("Add to playlist", { menu = false; actions?.show(t) }, icon = Icons.Rounded.QueueMusic)
                            GlassMenuItem("Go to album", { menu = false; nav?.album(t.albumKey) }, icon = Icons.Rounded.Album)
                            GlassMenuItem("Go to artist", { menu = false; nav?.artist(t.artist) }, icon = Icons.Rounded.Person)
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            if (track == null) {
                Text("Nothing playing", style = IosType.title2, color = Color.White, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            } else {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(.82f).widthIn(max = 340.dp).aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = .06f)),
                ) {
                    AlbumCover(track)
                }
                Spacer(Modifier.height(24.dp))
                Text(track.title, style = IosType.title2.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    track.artist,
                    style = IosType.subheadline,
                    color = Color.White.copy(alpha = .65f),
                    modifier = Modifier.clickable { nav?.artist(track.artist) }.padding(vertical = 5.dp),
                )
            }
            Spacer(Modifier.weight(1f))

            PlayerScrubber(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = { Playback.seekTo(it) },
                timeColor = Color.White.copy(alpha = .55f),
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                BareControl(Icons.Rounded.Shuffle, "Shuffle", active = state.shuffle) { Playback.toggleShuffle() }
                BareControl(Icons.Rounded.SkipPrevious, "Previous") { Playback.previous() }
                PlayCircle(state.playing, state.buffering) { Playback.toggle() }
                BareControl(Icons.Rounded.SkipNext, "Next") { Playback.next() }
                BareControl(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Repeat", active = state.repeatMode != Player.REPEAT_MODE_OFF,
                ) { Playback.cycleRepeat() }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                track?.let { t ->
                    val favourite = t.id in favorites
                    BottomAction(
                        if (favourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (favourite) "Loved" else "Love", active = favourite,
                    ) { graph.playlists.toggleFavorite(t.id) }
                }
                BottomAction(Icons.Rounded.QueueMusic, "Up Next") { nav?.queue() }
                BottomAction(Icons.Rounded.Bedtime, state.sleepLabel ?: "Sleep", active = state.sleepLabel != null) { sleep = true }
            }
        }
    }

    if (sleep) SleepSheet(onDismiss = { sleep = false })
}

@Composable
private fun AlbumCover(track: Track) {
    val art = rememberArtwork(track.albumKey)
    if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else Box(Modifier.fillMaxSize().background(com.everettpenne.baliset.ui.components.placeholderBrush(track.albumKey)), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.QueueMusic, null, tint = Color.White.copy(alpha = .4f), modifier = Modifier.size(56.dp))
    }
}

@Composable
private fun LightCircle(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)).pressSwell()
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun BareControl(icon: ImageVector, description: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(54.dp).pressSwell().clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (active) Baliset.accent else Color.White.copy(alpha = .9f), modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun PlayCircle(playing: Boolean, buffering: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(78.dp).clip(CircleShape).background(Color.White).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (playing) "Pause" else "Play" },
        contentAlignment = Alignment.Center,
    ) {
        if (buffering) CircularProgressIndicator(Modifier.size(30.dp), color = Baliset.night, strokeWidth = 3.dp)
        else Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Baliset.night, modifier = Modifier.size(38.dp))
    }
}

@Composable
private fun BottomAction(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(min = 78.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, null, tint = if (active) Baliset.accent else Color.White.copy(alpha = .8f), modifier = Modifier.size(22.dp))
        Text(label, style = IosType.caption2, color = if (active) Baliset.accent else Color.White.copy(alpha = .6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SleepSheet(onDismiss: () -> Unit) {
    val state by Playback.state.collectAsState()
    GlassSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Sleep timer", style = IosType.title3.copy(fontWeight = FontWeight.SemiBold))
            Text(
                state.sleepLabel ?: "Music keeps playing until the timer ends.",
                style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45).forEach { minutes ->
                    GlassChip(false, { Playback.setSleep(minutes); onDismiss() }, Modifier.weight(1f)) { Text("$minutes min") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(false, { Playback.setSleep(60); onDismiss() }, Modifier.weight(1f)) { Text("60 min") }
                GlassChip(false, { Playback.setSleep(null, endOfTrack = true); onDismiss() }, Modifier.weight(1f)) { Text("End of track") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(state.sleepLabel == null, { Playback.setSleep(null); onDismiss() }, Modifier.weight(1f)) { Text("Off") }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
