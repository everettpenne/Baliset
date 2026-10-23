package com.everettpenne.baliset.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.everettpenne.baliset.data.Artwork

/** Bumped when the library is rescanned, so cached covers reload once the artwork pass has written new files. */
val LocalArtworkEpoch = staticCompositionLocalOf { 0L }

/** The decoded cover for an album, loaded off the main thread and remembered for the composition. */
@Composable
fun rememberArtwork(albumKey: String?): ImageBitmap? {
    val context = LocalContext.current
    val epoch = LocalArtworkEpoch.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, albumKey, epoch) {
        value = albumKey?.let { key -> Artwork.load(context, key)?.asImageBitmap() }
    }
    return bitmap
}

/** A deterministic warm gradient for a song or album with no embedded cover, so the grid still has colour. */
fun placeholderBrush(key: String?): Brush {
    val palettes = listOf(
        Color(0xFF4A3426) to Color(0xFF201712),
        Color(0xFF2C3A45) to Color(0xFF141C22),
        Color(0xFF3E2D3A) to Color(0xFF1C1418),
        Color(0xFF2F3B2E) to Color(0xFF161D15),
        Color(0xFF43392A) to Color(0xFF1E1A12),
        Color(0xFF332E46) to Color(0xFF171521),
    )
    val index = ((key?.hashCode() ?: 0) and 0x7fffffff) % palettes.size
    val (start, end) = palettes[index]
    return Brush.linearGradient(listOf(start, end))
}

/**
 * Cover art in a rounded tile. When there is no cover it draws a quiet gradient with a music note rather than an empty hole, which is
 * what makes a library of untagged files still look intentional.
 */
@Composable
fun AlbumArt(
    albumKey: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 12.dp,
    iconSize: Dp = 22.dp,
    iconTint: Color = Color.White.copy(alpha = .45f),
) {
    val art = rememberArtwork(albumKey)
    Box(modifier.clip(RoundedCornerShape(corner)).background(placeholderBrush(albumKey)), contentAlignment = Alignment.Center) {
        if (art != null) Image(art, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.MusicNote, null, tint = iconTint, modifier = Modifier.size(iconSize))
    }
}
