package com.everettpenne.baliset.util

/**
 * What to call a file that carries no tags. Music copies arrive named in a few common ways, and a friendly player should show
 * "Artist · Title" rather than "03_-_artist_-_title.mp3". Pure, so it is unit-tested.
 */
data class NameGuess(val artist: String, val title: String)

/** Strips the extension, a leading track number ("01 ", "01.", "01 - "), and turns underscores and runs of spaces into spaces. */
fun cleanFileName(fileName: String): String {
    val withoutExtension = fileName.substringBeforeLast('.')
    val withoutNumber = Regex("^\\s*\\d{1,3}\\s*[-.)]?\\s+").replace(withoutExtension, "")
    return withoutNumber.replace('_', ' ').replace(Regex("\\s+"), " ").trim()
}

/** Guesses artist and title from a file name. "Artist - Title" splits; a lone name is the title with an unknown artist. */
fun guessName(fileName: String, unknownArtist: String = "Unknown artist"): NameGuess {
    val clean = cleanFileName(fileName)
    val parts = clean.split(" - ", limit = 2).map { it.trim() }.filter { it.isNotEmpty() }
    return when {
        parts.size == 2 -> NameGuess(parts[0], parts[1])
        else -> NameGuess(unknownArtist, clean.ifBlank { fileName })
    }
}
