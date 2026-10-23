package com.everettpenne.baliset.data

import kotlinx.serialization.Serializable

/**
 * One piece of music, however it was found: a row in Android's media index or a document in a folder the person granted.
 *
 * [id] is stable across rescans (`m:<media id>` or `d:<document id>`), because playlists, favourites and "jump back in" all remember
 * tracks by it. [albumKey] is what album art is cached under: the media index's album id, or the album and artist for a folder.
 */
@Serializable
data class Track(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long = 0L,
    val durationMs: Long = 0L,
    val trackNo: Int = 0,
    val year: Int = 0,
    val addedAt: Long = 0L,
    val mime: String? = null,
    val fileName: String = "",
    /** A cover image sitting beside the file (cover.jpg and friends), when the folder scan found one. */
    val coverUri: String? = null,
) {
    val albumKey: String get() = if (albumId > 0L) "m$albumId" else "d:$album|$artist"
}

/** An album, gathered from its tracks, with the track whose file the artwork is read from. */
data class Album(
    val key: String,
    val title: String,
    val artist: String,
    val year: Int,
    val artTrack: Track,
    val tracks: List<Track>,
) {
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
}

/** An artist, gathered from every track credited to them. */
data class Artist(val name: String, val tracks: List<Track>) {
    val albums: List<Album> get() = albumsOf(tracks)
}

/** The saved library: what was found, where, and when. */
@Serializable
data class LibrarySnapshot(
    val version: Int = 1,
    val source: String = SOURCE_MEDIA,
    val scannedAt: Long = 0L,
    val tracks: List<Track> = emptyList(),
) {
    companion object {
        const val SOURCE_MEDIA = "media"
        const val SOURCE_FOLDER = "folder"
    }
}

/** How far a scan has got, for the progress line on the welcome and settings pages. */
data class ScanState(
    val running: Boolean = false,
    val phase: String = "",
    val found: Int = 0,
    val done: Int = 0,
    val total: Int = 0,
    val error: String? = null,
)

/**
 * Albums in alphabetical order, each with its tracks in disc then track order. Only tracks with a playable duration are counted, so the
 * album count matches what the library can actually play.
 */
fun albumsOf(tracks: List<Track>): List<Album> = tracks
    .filter { it.album.isNotBlank() }
    .groupBy { it.albumKey }
    .map { (key, list) ->
        val sorted = list.sortedWith(compareBy({ it.trackNo }, { it.title.lowercase() }))
        val first = sorted.first()
        Album(key = key, title = first.album, artist = first.artist, year = sorted.maxOf { it.year }, artTrack = first, tracks = sorted)
    }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

/** Artists in alphabetical order, with "Unknown artist" last rather than filed under U. */
fun artistsOf(tracks: List<Track>): List<Artist> = tracks
    .groupBy { it.artist }
    .map { (name, list) -> Artist(name, list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })) }
    .sortedWith(compareBy<Artist> { it.name == UNKNOWN_ARTIST }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

const val UNKNOWN_ARTIST = "Unknown artist"
const val UNKNOWN_ALBUM = "Unknown album"

/** Songs in alphabetical order, the way a phone's music app lists them. */
fun songsOf(tracks: List<Track>): List<Track> =
    tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
