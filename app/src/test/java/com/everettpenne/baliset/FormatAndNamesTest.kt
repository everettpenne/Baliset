package com.everettpenne.baliset

import com.everettpenne.baliset.util.cleanFileName
import com.everettpenne.baliset.util.clock
import com.everettpenne.baliset.util.countWord
import com.everettpenne.baliset.util.guessName
import com.everettpenne.baliset.util.lengthWords
import com.everettpenne.baliset.util.sortKey
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatAndNamesTest {
    @Test fun clockShowsMinutesThenHours() {
        assertEquals("0:00", clock(0))
        assertEquals("0:09", clock(9_000))
        assertEquals("1:05", clock(65_000))
        assertEquals("1:02:05", clock(3_725_000))
        assertEquals("0:00", clock(-5_000))
    }

    @Test fun lengthWordsRoundsToMinutesAndHours() {
        assertEquals("0 min", lengthWords(0))
        assertEquals("1 min", lengthWords(30_000))
        assertEquals("48 min", lengthWords(48 * 60_000))
        assertEquals("1 hr", lengthWords(60 * 60_000))
        assertEquals("1 hr 12 min", lengthWords(72 * 60_000))
    }

    @Test fun countWordIsSingularAtOne() {
        assertEquals("1 song", countWord(1, "song"))
        assertEquals("0 songs", countWord(0, "song"))
        assertEquals("12 songs", countWord(12, "song"))
    }

    @Test fun sortKeyFoldsCaseAccentsAndThe() {
        assertEquals("beatles", sortKey("The Beatles"))
        assertEquals("cafe", sortKey("Café"))
        assertEquals("night drive", sortKey("  Night   Drive "))
    }

    @Test fun cleanFileNameDropsTrackNumbersAndUnderscores() {
        assertEquals("First Light", cleanFileName("01 First Light.mp3"))
        assertEquals("First Light", cleanFileName("01 - First Light.mp3"))
        assertEquals("First Light", cleanFileName("01. First Light.mp3"))
        assertEquals("Aster Vale - Long Way Home", cleanFileName("Aster_Vale_-_Long_Way_Home.mp3"))
        assertEquals("song", cleanFileName("song.mp3"))
    }

    @Test fun guessNameSplitsArtistAndTitle() {
        assertEquals("Aster Vale" to "Long Way Home", guessName("Aster Vale - Long Way Home.mp3").let { it.artist to it.title })
        assertEquals("Artist" to "Title", guessName("01 - Artist - Title.mp3").let { it.artist to it.title })
        assertEquals("Unknown artist" to "Paper Moon", guessName("06 - Paper Moon.mp3").let { it.artist to it.title })
        assertEquals("Title Only", guessName("Title Only.mp3").title)
    }
}
