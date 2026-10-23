package com.everettpenne.baliset.util

import java.util.Locale

/** Elapsed time as m:ss, or h:mm:ss from an hour. Shared by the queue, the rows and the Now Playing page. */
fun clock(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000).toInt()
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** A total length in words, for an album or playlist header: "48 min", "1 hr 12 min". */
fun lengthWords(ms: Long): String {
    val minutes = (ms.coerceAtLeast(0) + 30_000) / 60_000
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0L) "$hours hr" else "$hours hr $rest min"
}

/** "1 song", "12 songs". */
fun countWord(count: Int, singular: String, plural: String = singular + "s"): String =
    if (count == 1) "1 $singular" else "$count $plural"

/** A greeting that fits the hour, used at the top of Home. */
fun greeting(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Late night listening"
}

/** Sort-friendly form of a title: lower case, without accents, punctuation or a leading "the". */
fun sortKey(text: String): String {
    val folded = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("^the\\s+"), "")
    return folded.trim().replace(Regex("\\s+"), " ")
}
