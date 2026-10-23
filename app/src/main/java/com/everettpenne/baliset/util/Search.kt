package com.everettpenne.baliset.util

import com.everettpenne.baliset.data.Track

/** One row of search results, and why it matched, so the page can say "in Songs" and order good matches first. */
data class SearchResults(
    val songs: List<Track> = emptyList(),
    val albums: List<Pair<String, List<Track>>> = emptyList(),
    val artists: List<Pair<String, List<Track>>> = emptyList(),
) {
    val isEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

/**
 * Searches songs, albums and artists together. Matching is case- and accent-insensitive on title, artist and album; a title that
 * starts with the words ranks above one that merely contains them, so typing "bl" puts "Blinding Lights" first. Pure, and tested.
 */
fun searchLibrary(tracks: List<Track>, query: String, limitPerKind: Int = 60): SearchResults {
    val q = sortKey(query.trim())
    if (q.isEmpty()) return SearchResults()

    /** 0 exact, 1 starts with, 2 a word starts with, 3 contains, [NO_MATCH] otherwise. */
    fun rankOf(field: String): Int {
        val f = sortKey(field)
        if (f.isEmpty()) return NO_MATCH
        return when {
            f == q -> 0
            f.startsWith(q) -> 1
            f.split(' ').any { it.startsWith(q) } -> 2
            f.contains(q) -> 3
            else -> NO_MATCH
        }
    }

    // Songs match on their own title or artist, not on their album: typing an album's name shows the album, and its songs are
    // one tap away, instead of filling the song list with every track on it.
    val songs = tracks.mapNotNull { track ->
        val rank = minOf(rankOf(track.title), rankOf(track.artist).let { if (it == NO_MATCH) NO_MATCH else it + 1 })
        if (rank == NO_MATCH) null else rank to track
    }.sortedWith(compareBy({ it.first }, { sortKey(it.second.title) })).map { it.second }.take(limitPerKind)

    val albums = tracks.filter { rankOf(it.album) != NO_MATCH }.groupBy { it.albumKey }
        .map { (key, list) -> key to list.sortedWith(compareBy({ it.discNo }, { it.trackNo }, { it.title })) }
        .sortedWith(compareBy({ minOf(rankOf(it.second.first().album), rankOf(it.second.first().artist)) }, { sortKey(it.second.first().album) }))
        .take(limitPerKind)

    val artists = tracks.filter { rankOf(it.artist) != NO_MATCH }.groupBy { primaryArtist(it.artist) }
        .map { (name, list) -> name to list }
        .sortedWith(compareBy({ rankOf(it.first) }, { sortKey(it.first) }))
        .take(limitPerKind)

    return SearchResults(songs = songs, albums = albums, artists = artists)
}

private const val NO_MATCH = 9
