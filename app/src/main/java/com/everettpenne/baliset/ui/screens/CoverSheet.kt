package com.everettpenne.baliset.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.everettpenne.baliset.data.Album
import com.everettpenne.baliset.data.musicbrainz.CoverCandidate
import com.everettpenne.baliset.data.musicbrainz.MusicBrainz
import com.everettpenne.baliset.graph
import com.everettpenne.baliset.ui.components.placeholderBrush
import com.everettpenne.glass.GlassAlert
import com.everettpenne.glass.GlassButton
import com.everettpenne.glass.GlassSheet
import com.everettpenne.glass.IosType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Finds a cover for one album.
 *
 * The first time, it asks before sending anything: the album and artist names go to MusicBrainz, and the cover comes from the
 * Cover Art Archive. After that, the release candidates are shown with a small preview and a confidence, and nothing is
 * written until one is chosen.
 */
@Composable
fun CoverSheet(album: Album, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val consent by graph.settings.mbConsent.collectAsState()
    val scope = rememberCoroutineScope()
    var candidates by remember { mutableStateOf<List<CoverCandidate>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var applying by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(consent, attempt) {
        if (!consent) return@LaunchedEffect
        candidates = null
        error = null
        runCatching { graph.covers.candidates(album) }
            .onSuccess { candidates = it }
            .onFailure {
                error = it.message?.takeIf { message -> message.isNotBlank() } ?: "MusicBrainz could not be reached."
                candidates = emptyList()
            }
    }

    if (!consent) {
        GlassAlert(
            "Find cover art?",
            "Baliset will send \"${album.artist} — ${album.title}\" to MusicBrainz and download artwork from the Cover Art Archive. Nothing about you is sent, and only albums you ask about are looked up.",
            "Turn on",
            onConfirm = { graph.settings.setMbConsent(true) },
            onDismiss = onDismiss,
            dismissLabel = "Not now",
        )
        return
    }

    val found = candidates
    GlassSheet(onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Cover art", style = IosType.title3.copy(fontWeight = FontWeight.SemiBold))
                Text("${album.title} · ${album.artist}", style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            GlassButton("Done", onDismiss)
        }
        when {
            error != null -> Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(error!!, style = IosType.subheadline, color = MaterialTheme.colorScheme.error)
                GlassButton("Try again", { attempt++ }, prominent = true)
            }
            found == null -> Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.primary)
                Text("Searching MusicBrainz…", style = IosType.subheadline, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            found.isEmpty() -> Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("No cover found", style = IosType.title3.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    "MusicBrainz may not have this release, or its artist and album names may differ from the file's. A cover.jpg in the folder always wins.",
                    style = IosType.subheadline, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GlassButton("Search again", { attempt++ })
            }
            else -> found.take(4).forEach { candidate ->
                CandidateRow(
                    candidate = candidate,
                    applying = applying == candidate.release.id,
                    enabled = applying == null,
                    onUse = {
                        if (applying == null) {
                            applying = candidate.release.id
                            scope.launch {
                                val applied = graph.covers.apply(album, candidate)
                                applying = null
                                if (applied) onDismiss() else error = "That cover could not be saved."
                            }
                        }
                    },
                )
            }
        }
        Text(
            "Artwork from the Cover Art Archive · data by MusicBrainz contributors.",
            style = IosType.caption1, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun CandidateRow(candidate: CoverCandidate, applying: Boolean, enabled: Boolean, onUse: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onUse).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoverThumb(candidate.imageUrl)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(candidate.release.title, style = IosType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    candidate.release.artist,
                    candidate.release.year.takeIf { it > 0 }?.toString(),
                    candidate.release.label,
                    candidate.release.trackCount.takeIf { it > 0 }?.let { "$it tracks" },
                ).joinToString(" · "),
                style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${(candidate.confidence * 100).roundToInt()}% match",
                style = IosType.caption2, color = if (candidate.confidence >= 0.74) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (applying) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.primary)
        else GlassButton("Use", onUse, enabled = enabled, prominent = true)
    }
}

@Composable
private fun CoverThumb(url: String?) {
    val image by produceState<ImageBitmap?>(null, url) {
        value = url?.let { runCatching { MusicBrainz.download(it) }.getOrNull() }
            ?.let { bytes -> runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()?.asImageBitmap() }
    }
    Box(
        Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)).background(placeholderBrush(url)),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) Image(bitmap, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.Album, null, tint = androidx.compose.ui.graphics.Color.White.copy(alpha = .4f), modifier = Modifier.size(22.dp))
    }
}
