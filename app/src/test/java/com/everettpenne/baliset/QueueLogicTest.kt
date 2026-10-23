package com.everettpenne.baliset

import com.everettpenne.baliset.data.Track
import com.everettpenne.baliset.playback.QueueLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QueueLogicTest {
    private fun track(id: String) = Track(id = id, uri = "file:///$id.mp3", title = id, artist = "Artist", album = "Album")

    @Test fun shuffleKeepsTheCurrentSongFirst() {
        val queue = listOf(track("a"), track("b"), track("c"), track("d"))
        val shuffled = QueueLogic.shuffleAround(queue, "c", Random(7))
        assertEquals("c", shuffled.first().id)
        assertEquals(queue.size, shuffled.size)
        assertEquals(queue.map { it.id }.toSet(), shuffled.map { it.id }.toSet())
    }

    @Test fun shuffleWithoutACurrentSongStillReturnsEverything() {
        val queue = listOf(track("a"), track("b"), track("c"))
        val shuffled = QueueLogic.shuffleAround(queue, "missing", Random(3))
        assertEquals(queue.map { it.id }.toSet(), shuffled.map { it.id }.toSet())
    }

    @Test fun shuffleOfOneOrNoneIsUnchanged() {
        val one = listOf(track("a"))
        assertEquals(one, QueueLogic.shuffleAround(one, "a"))
        assertEquals(emptyList<Track>(), QueueLogic.shuffleAround(emptyList(), "a"))
    }

    @Test fun moveReordersAndIgnoresBadIndexes() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("a", "c", "d", "b"), QueueLogic.move(list, 1, 3))
        assertEquals(list, QueueLogic.move(list, 0, 9))
        assertEquals(list, QueueLogic.move(list, -1, 2))
        assertEquals(list, QueueLogic.move(list, 2, 2))
    }

    @Test fun removeDropsOneEntryAndIgnoresBadIndexes() {
        assertEquals(listOf("a", "c"), QueueLogic.removeAt(listOf("a", "b", "c"), 1))
        assertEquals(listOf("a", "b"), QueueLogic.removeAt(listOf("a", "b"), 5))
    }

    @Test fun repeatCyclesOffAllOne() {
        val off = QueueLogic.nextRepeatMode(androidx.media3.common.Player.REPEAT_MODE_OFF)
        val all = QueueLogic.nextRepeatMode(off)
        val one = QueueLogic.nextRepeatMode(all)
        assertEquals(androidx.media3.common.Player.REPEAT_MODE_ALL, off)
        assertEquals(androidx.media3.common.Player.REPEAT_MODE_ONE, all)
        assertEquals(androidx.media3.common.Player.REPEAT_MODE_OFF, one)
    }

    @Test fun sleepTextCountsUpToTheSecond() {
        assertEquals("0:00", QueueLogic.sleepText(0))
        assertEquals("0:01", QueueLogic.sleepText(1))
        assertEquals("0:01", QueueLogic.sleepText(999))
        assertEquals("1:00", QueueLogic.sleepText(60_000))
        assertEquals("1:01:05", QueueLogic.sleepText(3_665_000))
    }

    @Test fun everyShuffleIsAPermutation() {
        val queue = (1..60).map { track("t$it") }
        repeat(20) { seed ->
            val shuffled = QueueLogic.shuffleAround(queue, "t30", Random(seed))
            assertEquals(60, shuffled.size)
            assertEquals(60, shuffled.map { it.id }.toSet().size)
            assertTrue(shuffled.first().id == "t30")
        }
    }
}
