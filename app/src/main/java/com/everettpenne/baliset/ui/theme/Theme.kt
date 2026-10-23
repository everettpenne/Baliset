package com.everettpenne.baliset.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.everettpenne.glass.GlassColors
import com.everettpenne.glass.GlassTheme

/** Baliset's brand colours; the theme, type scale and glass kit come from the shared Glass library. */
object Baliset {
    /** Burnished brass: the tab selection, the play button and highlights. */
    val accent = Color(0xFFA9632E)
    /** A deeper brass for text drawn on the accent. */
    val onAccent = Color(0xFF241505)
    val green = Color(0xFF34C759)
    val red = Color(0xFFFF453A)
    /** The near-black the Now Playing page fades to under the artwork. */
    val night = Color(0xFF0B0A09)
}

@Composable
fun BalisetTheme(content: @Composable () -> Unit) =
    GlassTheme(GlassColors(accent = Baliset.accent, red = Baliset.red, green = Baliset.green), content)
