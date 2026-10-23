package com.everettpenne.baliset.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.everettpenne.baliset.graph
import com.everettpenne.baliset.playback.Playback
import com.everettpenne.baliset.ui.LocalNav
import com.everettpenne.baliset.ui.components.AlbumArt
import com.everettpenne.baliset.ui.components.LocalTrackActions
import com.everettpenne.baliset.util.clock
import com.everettpenne.glass.GlassAlert
import com.everettpenne.glass.GlassCenteredTopBar
import com.everettpenne.glass.GlassEmptyState
import com.everettpenne.glass.GlassIconButton
import com.everettpenne.glass.GlassScreen
import com.everettpenne.glass.GlassSectionHeader
import com.everettpenne.glass.IosType
import com.everettpenne.glass.contentTopPadding
import kotlin.math.roundToInt

/**
 * Up Next: the queue in the order it will really play, with the current song marked. Hold the handle and drag to move a song; remove
 * one with the × on its right. Everything here is exactly what the player will do, because the app owns the order.
 */
@Composable
fun QueueScreen() {
    val context = LocalContext.current
    val graph = context.graph
    val nav = LocalNav.current
    val actions = LocalTrackActions.current
    val state by Playback.state.collectAsState()
    var clearing by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(-1) }
    var offset by remember { mutableFloatStateOf(0f) }
    val rowHeight = 68.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }

    GlassScreen(bars = {
        GlassCenteredTopBar("Up Next", onBack = { nav?.back() }, trailing = {
            if (state.queue.isNotEmpty()) GlassIconButton(Icons.Rounded.ClearAll, "Clear queue", { clearing = true }, size = 38)
        })
    }) { padding ->
        if (state.queue.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                GlassEmptyState(Icons.Rounded.QueueMusic, "The queue is empty", "Play an album, a playlist or a song and it will show up here.")
            }
            return@GlassScreen
        }
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 4.dp, bottom = com.everettpenne.glass.contentBottomPadding()),
        ) {
            item("current-header") { GlassSectionHeader("Now playing") }
            item("current") {
                val current = state.queue.getOrNull(state.index)
                if (current != null) QueueRow(
                    title = current.title, artist = current.artist, albumKey = current.albumKey, durationMs = current.durationMs,
                    current = true, dragging = false, offset = 0f,
                    onClick = { },
                    onRemove = null,
                )
                else Spacer(Modifier.height(1.dp))
            }
            item("next-header") {
                GlassSectionHeader("Next") {
                    Text("${state.queue.size - state.index - 1} songs", style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            itemsIndexed(state.queue) { index, track ->
                if (index <= state.index) return@itemsIndexed
                val isDragging = index == dragging
                QueueRow(
                    title = track.title, artist = track.artist, albumKey = track.albumKey, durationMs = track.durationMs,
                    current = false, dragging = isDragging, offset = if (isDragging) offset else 0f,
                    onClick = { Playback.playTrack(track) },
                    onRemove = { Playback.removeFromQueue(index) },
                    handle = {
                        Box(
                            Modifier.width(34.dp).fillMaxHeight().pointerInput(index, state.queue.size) {
                                detectDragGestures(
                                    onDragStart = { dragging = index; offset = 0f },
                                    onDragEnd = {
                                        val target = (dragging + (offset / rowPx).roundToInt()).coerceIn(state.index + 1, state.queue.lastIndex)
                                        if (dragging >= 0 && target != dragging) Playback.moveInQueue(dragging, target)
                                        dragging = -1; offset = 0f
                                    },
                                    onDragCancel = { dragging = -1; offset = 0f },
                                ) { change, amount -> change.consume(); offset += amount.y }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.DragHandle, "Drag to reorder", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f), modifier = Modifier.size(20.dp)) }
                    },
                )
            }
            item("footer") {
                val total = state.queue.drop(state.index + 1).sumOf { it.durationMs }
                Text(
                    "${state.queue.size} in the queue · ${clock(total)} left",
                    style = IosType.caption1, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                )
            }
        }
    }

    if (clearing) GlassAlert(
        "Clear the queue?", "Playback stops and Up Next becomes empty. Your library is not affected.",
        "Clear", { Playback.clearQueue(); clearing = false; nav?.back() }, { clearing = false }, destructive = true,
    )
}

@Composable
private fun QueueRow(
    title: String,
    artist: String,
    albumKey: String?,
    durationMs: Long,
    current: Boolean,
    dragging: Boolean,
    offset: Float,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    handle: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().height(68.dp)
            .graphicsLayer { translationY = offset }
            .zIndex(if (dragging) 1f else 0f)
            .background(if (dragging) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .9f) else MaterialTheme.colorScheme.background)
            .clickable(enabled = !dragging, onClick = onClick)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumArt(albumKey, Modifier.size(44.dp), corner = 10.dp, iconSize = 18.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title, style = IosType.body,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (current) Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                Text(artist, style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(clock(durationMs), style = IosType.caption1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (handle != null) handle() else Box(Modifier.size(34.dp))
        if (onRemove != null) Box(
            Modifier.size(40.dp).clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Close, "Remove from queue", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f), modifier = Modifier.size(18.dp)) }
        else Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            Text("Playing", style = IosType.caption2, color = MaterialTheme.colorScheme.primary)
        }
    }
}
