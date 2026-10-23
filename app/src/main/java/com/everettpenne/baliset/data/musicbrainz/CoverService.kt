package com.everettpenne.baliset.data.musicbrainz

import android.content.Context
import com.everettpenne.baliset.data.Album
import com.everettpenne.baliset.data.Artwork
import com.everettpenne.baliset.data.UNKNOWN_ALBUM
import com.everettpenne.baliset.data.UNKNOWN_ARTIST
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Which release a cover came from, so the album page can say so and the cover can be reset. */
@Serializable
data class CoverInfo(
    val releaseId: String,
    val releaseGroupId: String? = null,
    val title: String,
    val artist: String,
    val year: Int = 0,
    val confidence: Double = 0.0,
    val appliedAt: Long = 0L,
)

/** Progress of "fill missing covers", for Settings. */
data class CoverJobState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val current: String = "",
    val applied: Int = 0,
    val skipped: Int = 0,
    val finished: Boolean = false,
    val error: String? = null,
)

/**
 * Finds and applies covers.
 *
 * A single album is looked up when its page asks: search MusicBrainz, rank the releases against the local title, artist,
 * track count and year, then ask the Cover Art Archive for a front image of the best few. The library-wide job only touches
 * albums with no cover and only applies a confident match, so a wrong guess is never written silently. Everything is opt-in
 * through the MusicBrainz setting; nothing here runs on its own.
 */
class CoverService(private val context: Context) {
    private val meta = CoverMetaStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _job = MutableStateFlow(CoverJobState())
    val job: StateFlow<CoverJobState> = _job.asStateFlow()
    private var running: Job? = null

    fun info(albumKey: String): CoverInfo? = meta.get(albumKey)

    suspend fun candidates(album: Album, max: Int = 4): List<CoverCandidate> {
        if (album.title.isBlank() || album.title == UNKNOWN_ALBUM) return emptyList()
        val local = LocalAlbumQuery(album.title, album.artist, album.tracks.size, album.year)
        val ranked = CoverMatch.rank(local, MusicBrainz.searchReleases(album.title, album.artist), limit = 6)
        val found = ArrayList<CoverCandidate>()
        for (candidate in ranked) {
            if (found.size >= max) break
            val json = MusicBrainz.coverJson(candidate.release.releaseGroupId, candidate.release.id) ?: continue
            val thumb = pickCoverUrl(json, "250") ?: continue
            found += candidate.copy(imageUrl = thumb, largeUrl = pickCoverUrl(json, "500"))
        }
        return found
    }

    suspend fun apply(album: Album, candidate: CoverCandidate): Boolean {
        val url = candidate.largeUrl ?: candidate.imageUrl ?: return false
        val bytes = MusicBrainz.download(url) ?: return false
        if (!Artwork.apply(context, album.key, bytes)) return false
        meta.put(
            album.key,
            CoverInfo(
                releaseId = candidate.release.id,
                releaseGroupId = candidate.release.releaseGroupId,
                title = candidate.release.title,
                artist = candidate.release.artist,
                year = candidate.release.year,
                confidence = candidate.confidence,
                appliedAt = System.currentTimeMillis(),
            ),
        )
        return true
    }

    /** Forgets a chosen cover and its record, so the album falls back to the file or folder art. */
    fun forget(albumKey: String) {
        meta.remove(albumKey)
        Artwork.delete(context, albumKey)
    }

    /** Runs over the albums without a cover; safe to call twice (the second is ignored while the first runs). */
    fun fillMissing(albums: List<Album>) {
        if (running?.isActive == true) return
        val targets = albums.filter {
            it.title.isNotBlank() && it.title != UNKNOWN_ALBUM && it.artist != UNKNOWN_ARTIST && !Artwork.has(context, it.key)
        }
        if (targets.isEmpty()) {
            _job.value = CoverJobState(finished = true, total = 0)
            return
        }
        _job.value = CoverJobState(running = true, total = targets.size)
        running = scope.launch {
            var applied = 0
            var skipped = 0
            try {
                for ((index, album) in targets.withIndex()) {
                    ensureActive()
                    _job.value = _job.value.copy(done = index, current = album.title)
                    val best = runCatching { candidates(album, max = 1) }.getOrDefault(emptyList()).firstOrNull()
                    if (best != null && best.confidence >= CoverMatch.AUTO_APPLY && apply(album, best)) applied++ else skipped++
                    _job.value = _job.value.copy(done = index + 1, applied = applied, skipped = skipped)
                }
                _job.value = _job.value.copy(running = false, finished = true, current = "")
            } catch (cancel: CancellationException) {
                _job.value = _job.value.copy(running = false, finished = true, current = "Stopped")
                throw cancel
            } catch (error: Exception) {
                _job.value = _job.value.copy(
                    running = false, finished = true, current = "",
                    error = error.message?.takeIf { it.isNotBlank() } ?: "MusicBrainz could not be reached.",
                )
            }
        }
    }

    fun cancel() {
        running?.cancel()
    }
}

/** The chosen covers, in one small JSON file beside the library. */
private class CoverMetaStore(context: Context) {
    private val file = File(context.filesDir, "cover-meta.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var data: CoverMetaFile = runCatching {
        if (file.isFile) json.decodeFromString<CoverMetaFile>(file.readText()) else CoverMetaFile()
    }.getOrDefault(CoverMetaFile())

    @Synchronized fun get(albumKey: String): CoverInfo? = data.albums[albumKey]

    @Synchronized fun put(albumKey: String, info: CoverInfo) {
        data = CoverMetaFile(albums = data.albums + (albumKey to info))
        save()
    }

    @Synchronized fun remove(albumKey: String) {
        data = CoverMetaFile(albums = data.albums - albumKey)
        save()
    }

    private fun save() {
        runCatching {
            val temporary = File(file.parentFile, file.name + ".tmp")
            temporary.writeText(json.encodeToString(data))
            temporary.renameTo(file) || run { file.delete(); temporary.renameTo(file) }
        }
    }
}

@Serializable
private data class CoverMetaFile(val version: Int = 1, val albums: Map<String, CoverInfo> = emptyMap())
