package com.everettpenne.baliset.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.everettpenne.baliset.data.Album
import com.everettpenne.baliset.data.Track
import com.everettpenne.baliset.util.clock
import com.everettpenne.glass.IosType
import com.everettpenne.glass.pressSwell

/**
 * The song row used everywhere: artwork (or a track number on an album), title, a quiet second line, and the length. A long press
 * opens the actions sheet; a tap plays. It is a flat row rather than a glass capsule so a long list scrolls cleanly.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    playing: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArtwork: Boolean = true,
    trackNumber: Int? = null,
    subtitle: String? = track.artist,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier.fillMaxWidth().heightIn(min = 62.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        when {
            trackNumber != null -> Box(Modifier.width(26.dp), contentAlignment = Alignment.CenterEnd) {
                Text(
                    "$trackNumber",
                    style = IosType.callout,
                    color = if (playing) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            showArtwork -> AlbumArt(track.albumKey, Modifier.size(46.dp), corner = 10.dp, iconSize = 18.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                track.title,
                style = IosType.body,
                color = if (playing) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) Text(subtitle, style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (playing) Icon(Icons.Rounded.GraphicEq, "Playing", tint = accent, modifier = Modifier.size(18.dp))
        trailing()
        Text(clock(track.durationMs), style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** An album in a shelf or a grid: its cover, name and artist. */
@Composable
fun AlbumTile(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.pressSwell().clickable(interactionSource = null, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AlbumArt(album.key, Modifier.fillMaxWidth().aspectRatio(1f), corner = 14.dp, iconSize = 30.dp)
        Text(album.title, style = IosType.subheadline.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(album.artist, style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A horizontal shelf of albums for Home, 148dp wide, with the page's own side margin. */
@Composable
fun AlbumShelf(albums: List<Album>, onOpen: (Album) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(albums, key = { it.key }) { album -> AlbumTile(album, { onOpen(album) }, Modifier.width(148.dp)) }
    }
}

/** Artists get a round portrait rather than a square cover, as in the original music app. */
@Composable
fun ArtistRow(name: String, albums: Int, songs: Int, artworkKey: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AlbumArt(artworkKey, Modifier.size(50.dp), corner = 25.dp, iconSize = 20.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = IosType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${if (albums == 1) "1 album" else "$albums albums"} · $songs songs", style = IosType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A small pill of the accent, used for the one primary action on a page ("Play", "Shuffle all"). */
@Composable
fun AccentPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true) {
    com.everettpenne.glass.GlassButton(
        onClick = onClick,
        modifier = modifier,
        prominent = filled,
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Text(text)
    }
}
