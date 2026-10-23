package com.everettpenne.baliset.playback

import androidx.media3.common.Player
import com.everettpenne.baliset.data.Track
import kotlin.random.Random

/**
 * The queue rules, kept pure so they can be tested without a player.
 *
 * Shuffle is the app's own ordering rather than the player's hidden one, so the Up Next page always shows the order that will really
 * play. Turning it on puts the current track first and shuffles everything else; turning it off puts back the order the album or
 * playlist had. Repeat cycles off → all → one, as the system control does.
 */
object QueueLogic {

    /** The queue with [currentId] first and the rest shuffled. An unknown current id simply shuffles everything. */
    fun shuffleAround(tracks: List<Track>, currentId: String?, random: Random = Random.Default): List<Track> {
        if (tracks.size <= 1) return tracks
        val current = tracks.firstOrNull { it.id == currentId }
        val rest = tracks.filter { it.id != currentId }.shuffled(random)
        return if (current != null) listOf(current) + rest else rest
    }

    fun indexOf(tracks: List<Track>, id: String?): Int = tracks.indexOfFirst { it.id == id }

    /** A moved entry, with out-of-range indexes ignored rather than throwing. */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        return list.toMutableList().also { it.add(to, it.removeAt(from)) }
    }

    fun <T> removeAt(list: List<T>, index: Int): List<T> =
        if (index !in list.indices) list else list.filterIndexed { i, _ -> i != index }

    /** Player.REPEAT_MODE_OFF → ALL → ONE → OFF. */
    fun nextRepeatMode(mode: Int): Int = when (mode) {
        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_OFF
    }

    /** "32:10" style countdown for the sleep timer, rounded up to the next second so it never shows 0 while still running. */
    fun sleepText(remainingMs: Long): String {
        val seconds = ((remainingMs.coerceAtLeast(0) + 999) / 1000).toInt()
        val minutes = seconds / 60
        val rest = seconds % 60
        return if (minutes >= 60) "%d:%02d:%02d".format(minutes / 60, minutes % 60, rest) else "%d:%02d".format(minutes, rest)
    }
}
