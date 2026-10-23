package com.everettpenne.baliset.data.musicbrainz

import com.everettpenne.baliset.data.UNKNOWN_ALBUM
import com.everettpenne.baliset.data.UNKNOWN_ARTIST
import org.json.JSONArray
import org.json.JSONObject

/**
 * One MusicBrainz release, reduced to what Baliset needs to choose a cover: its title and artist, how many tracks it has, when
 * it came out, who put it out, and the release group whose artwork it shares with its other editions.
 */
data class MbRelease(
    val id: String,
    val title: String,
    val artist: String,
    val score: Int,
    val year: Int,
    val trackCount: Int,
    val status: String,
    val releaseGroupId: String?,
    val primaryType: String?,
    val label: String?,
)

/** A release search response, parsed. Search results already carry the artist credit, track count and release group. */
fun parseReleaseSearch(json: String): List<MbRelease> {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
    val releases = root.optJSONArray("releases") ?: return emptyList()
    return (0 until releases.length()).mapNotNull { index ->
        val item = releases.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val group = item.optJSONObject("release-group")
        val credits = item.optJSONArray("artist-credit")
        val labelInfo = item.optJSONArray("label-info")?.optJSONObject(0)?.optJSONObject("label")?.optString("name")
        MbRelease(
            id = id,
            title = item.optString("title"),
            artist = artistNames(credits),
            score = item.optInt("score"),
            year = item.optString("date").take(4).toIntOrNull() ?: 0,
            trackCount = item.optInt("track-count"),
            status = item.optString("status"),
            releaseGroupId = group?.optString("id")?.takeIf { it.isNotBlank() },
            primaryType = group?.optString("primary-type")?.takeIf { it.isNotBlank() },
            label = labelInfo?.takeIf { it.isNotBlank() },
        )
    }
}

private fun artistNames(credits: JSONArray?): String {
    if (credits == null || credits.length() == 0) return UNKNOWN_ARTIST
    val names = ArrayList<String>()
    for (i in 0 until credits.length()) {
        val entry = credits.optJSONObject(i) ?: continue
        val name = entry.optString("name").takeIf { it.isNotBlank() } ?: entry.optJSONObject("artist")?.optString("name") ?: continue
        names += name
        entry.optString("joinphrase").takeIf { it.isNotBlank() }?.let { names += it.trim() }
    }
    return names.joinToString(" ").replace(Regex("\\s+"), " ").trim().ifBlank { UNKNOWN_ARTIST }
}

/**
 * The Lucene query for a release search. Quoted values, with backslashes and quotes escaped; an unknown album is not worth
 * searching for, and an unknown artist is left out rather than sent as words.
 */
fun releaseQuery(albumTitle: String, artist: String): String? {
    val album = albumTitle.trim()
    if (album.isBlank() || album.equals(UNKNOWN_ALBUM, ignoreCase = true)) return null
    fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
    val parts = mutableListOf("release:\"${escape(album)}\"")
    val artistName = artist.trim()
    if (artistName.isNotBlank() && !artistName.equals(UNKNOWN_ARTIST, ignoreCase = true)) {
        parts += "artist:\"${escape(artistName)}\""
    }
    return parts.joinToString(" AND ")
}

/** One image record from the Cover Art Archive. */
data class CaaImage(
    val front: Boolean,
    val approved: Boolean,
    val types: List<String>,
    val image: String?,
    val thumbnails: Map<String, String>,
)

fun parseCaaImages(json: String): List<CaaImage> {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
    val images = root.optJSONArray("images") ?: return emptyList()
    return (0 until images.length()).mapNotNull { index ->
        val item = images.optJSONObject(index) ?: return@mapNotNull null
        val types = item.optJSONArray("types")?.let { array -> (0 until array.length()).map { array.optString(it) } }.orEmpty()
        val thumbs = HashMap<String, String>()
        item.optJSONObject("thumbnails")?.let { object_ ->
            listOf("250", "500", "1200", "small", "large").forEach { key ->
                object_.optString(key).takeIf { it.isNotBlank() }?.let { thumbs[key] = it }
            }
        }
        CaaImage(
            front = item.optBoolean("front", false),
            approved = item.optBoolean("approved", true),
            types = types,
            image = item.optString("image").takeIf { it.isNotBlank() },
            thumbnails = thumbs,
        )
    }
}

/**
 * The best front cover URL, at [size] (250, 500 or 1200), from a Cover Art Archive listing. Only approved images are used;
 * a release's designated front wins, then anything typed Front, then the first approved image. URLs are upgraded to https
 * because the archive still publishes some as http.
 */
fun pickCoverUrl(json: String, size: String = "500"): String? {
    val usable = parseCaaImages(json).filter { it.approved }
    val chosen = usable.firstOrNull { it.front }
        ?: usable.firstOrNull { image -> image.types.any { it.equals("Front", ignoreCase = true) } }
        ?: usable.firstOrNull()
        ?: return null
    val url = chosen.thumbnails[size]
        ?: chosen.thumbnails["1200"]
        ?: chosen.thumbnails["500"]
        ?: chosen.image
        ?: return null
    return upgradeHttps(url)
}

fun upgradeHttps(url: String): String = when {
    url.startsWith("http://") -> "https://" + url.removePrefix("http://")
    else -> url
}
