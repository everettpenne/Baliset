package com.everettpenne.baliset

import com.everettpenne.baliset.data.Track
import com.everettpenne.baliset.data.UNKNOWN_ALBUM
import com.everettpenne.baliset.data.UNKNOWN_ARTIST
import com.everettpenne.baliset.data.albumsOf
import com.everettpenne.baliset.data.artistsOf
import com.everettpenne.baliset.data.songsOf
import com.everettpenne.baliset.util.searchLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryAndSearchTest {
    private fun track(id: String, title: String, artist: String = "Artist", album: String = "Album", trackNo: Int = 0, year: Int = 2026, albumId: Long = 0) =
        Track(id = id, uri = "file:///$id.mp3", title = title, artist = artist, album = album, albumId = albumId, trackNo = trackNo, year = year, durationMs = 30_000)

    private val songs = listOf(
        track("1", "Blinding Lights", "The Weeknd", "After Hours", 1),
        track("2", "First Light", "Baliset Test", "Dune Sketches", 1),
        track("3", "Night Drive", "The Sietches", "Night Drive", 1),
        track("4", "Chrome Dunes", "The Sietches", "Night Drive", 2),
        track("5", "Café Song", "Artiste", "Café", 1),
        track("6", "Unknown Thing", UNKNOWN_ARTIST, UNKNOWN_ALBUM, 0),
    )

    @Test fun albumsGroupAndSortByDiscNumber() {
        val albums = albumsOf(songs)
        assertEquals(5, albums.size)
        val night = albums.first { it.title == "Night Drive" }
        assertEquals(listOf("Night Drive", "Chrome Dunes"), night.tracks.map { it.title })
        assertEquals(2, night.tracks.size)
    }

    @Test fun albumKeysHaveNoControlCharacters() {
        // A key travels in a navigation route; a NUL in it silently broke navigation once.
        assertTrue(songs.all { it.albumKey.none { c -> c.code < 0x20 } })
        assertTrue(songs.all { it.albumKey.isNotBlank() })
    }

    @Test fun mediaIndexAlbumsUseTheIndexId() {
        val a = track("a", "One", album = "Same Name", albumId = 4)
        val b = track("b", "Two", album = "Same Name", albumId = 5)
        assertEquals("m4", a.albumKey)
        assertEquals(2, albumsOf(listOf(a, b)).size)
    }

    @Test fun artistsSortUnknownLast() {
        val artists = artistsOf(songs)
        assertEquals(UNKNOWN_ARTIST, artists.last().name)
        assertEquals("Artiste", artists.first().name)
    }

    @Test fun songsSortCaseInsensitively() {
        assertEquals(listOf("Blinding Lights", "Café Song", "Chrome Dunes", "First Light", "Night Drive", "Unknown Thing"), songsOf(songs).map { it.title })
    }

    @Test fun searchFindsSongsAlbumsAndArtists() {
        val results = searchLibrary(songs, "night")
        assertEquals(listOf("Night Drive"), results.songs.map { it.title })
        assertEquals("Night Drive", results.albums.first().second.first().album)
        assertTrue(results.artists.isEmpty())
        assertEquals("The Sietches", searchLibrary(songs, "sietches").artists.first().first)
    }

    @Test fun searchIgnoresCaseAndAccents() {
        assertEquals(listOf("Café Song"), searchLibrary(songs, "cafe").songs.map { it.title })
        assertEquals(listOf("Café Song"), searchLibrary(songs, "CAFÉ").songs.map { it.title })
    }

    @Test fun prefixMatchesRankFirst() {
        val results = searchLibrary(songs, "chr")
        assertEquals(listOf("Chrome Dunes"), results.songs.map { it.title })
    }

    @Test fun emptyQueryHasNoResults() {
        assertTrue(searchLibrary(songs, "   ").isEmpty)
    }
}
