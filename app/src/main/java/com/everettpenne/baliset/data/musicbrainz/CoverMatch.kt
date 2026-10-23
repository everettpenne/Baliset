package com.everettpenne.baliset.data.musicbrainz

import com.everettpenne.baliset.data.UNKNOWN_ARTIST
import com.everettpenne.baliset.util.sortKey
import kotlin.math.abs

/** What the local album says about itself, for comparing with MusicBrainz. */
data class LocalAlbumQuery(val title: String, val artist: String, val trackCount: Int, val year: Int)

/** A release that might be this album, with a confidence and (once looked up) the cover URLs found for it. */
data class CoverCandidate(
    val release: MbRelease,
    val confidence: Double,
    val imageUrl: String? = null,
    val largeUrl: String? = null,
)

/**
 * The matching rules, pure and testable. Title matters most, then the artist, then the number of tracks — which is the best
 * defence against picking a 19-track deluxe edition for a 5-track album. The MusicBrainz search score is a small tie-breaker,
 * never the main signal.
 */
object CoverMatch {
    const val AUTO_APPLY = 0.74
    const val SHOW = 0.35

    fun confidence(local: LocalAlbumQuery, release: MbRelease): Double {
        val title = titleSimilarity(local.title, release.title)
        if (title == 0.0) return 0.0
        val artist = when {
            local.artist.isBlank() || local.artist == UNKNOWN_ARTIST -> 0.5
            sortKey(local.artist) == sortKey(release.artist) -> 1.0
            sortKey(release.artist).contains(sortKey(local.artist)) || sortKey(local.artist).contains(sortKey(release.artist)) -> 0.6
            else -> 0.0
        }
        val count = when {
            local.trackCount <= 0 || release.trackCount <= 0 -> 0.5
            local.trackCount == release.trackCount -> 1.0
            abs(local.trackCount - release.trackCount) == 1 -> 0.55
            else -> 0.0
        }
        val year = when {
            local.year <= 0 || release.year <= 0 -> 0.5
            local.year == release.year -> 1.0
            abs(local.year - release.year) <= 1 -> 0.85
            abs(local.year - release.year) <= 3 -> 0.55
            else -> 0.15
        }
        val searchScore = release.score.coerceIn(0, 100) / 100.0
        val status = when {
            release.status.equals("Official", ignoreCase = true) -> 1.0
            release.status.equals("Bootleg", ignoreCase = true) -> 0.0
            release.status.isBlank() -> 0.6
            else -> 0.6
        }
        return 0.32 * title + 0.20 * artist + 0.24 * count + 0.12 * year + 0.07 * searchScore + 0.05 * status
    }

    /**
     * The releases worth showing, best first, one per release group (editions of the same album share their artwork, so
     * showing all of them would just repeat the same cover).
     */
    fun rank(local: LocalAlbumQuery, releases: List<MbRelease>, limit: Int = 6): List<CoverCandidate> {
        val seen = HashSet<String>()
        return releases
            .map { it to confidence(local, it) }
            .filter { it.second >= SHOW }
            .sortedByDescending { it.second }
            .filter { seen.add(it.first.releaseGroupId ?: it.first.id) }
            .map { CoverCandidate(it.first, it.second) }
            .take(limit)
    }

    private fun titleSimilarity(local: String, candidate: String): Double {
        val a = sortKey(local)
        val b = sortKey(candidate)
        if (a.isBlank() || b.isBlank()) return 0.0
        return when {
            a == b -> 1.0
            a.contains(b) || b.contains(a) -> 0.7
            a.split(' ').any { word -> word.length > 3 && b.contains(word) } -> 0.35
            else -> 0.0
        }
    }
}
