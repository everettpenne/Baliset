package com.everettpenne.baliset.data.musicbrainz

import android.os.SystemClock
import com.everettpenne.baliset.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The two MetaBrainz services, used politely.
 *
 * MusicBrainz asks for at most about one request a second per address and a User-Agent that identifies the app; every call
 * here goes through one throttle. The Cover Art Archive is a different host and has no published limit, but images are still
 * fetched one at a time. Redirects are followed by hand because the archive redirects to archive.org, and a 503 is retried
 * once after the delay the server asks for.
 */
object MusicBrainz {
    private const val USER_AGENT = "Baliset/${BuildConfig.VERSION_NAME} (https://github.com/everettpenne/Baliset)"
    private const val MB = "https://musicbrainz.org/ws/2"
    private const val CAA = "https://coverartarchive.org"

    private val searchLock = Mutex()
    private var lastSearch = 0L
    private val imageLock = Mutex()
    private var lastImage = 0L

    private suspend fun throttleSearch() = searchLock.withLock {
        val since = SystemClock.elapsedRealtime() - lastSearch
        if (since < 1_100) delay(1_100 - since)
        lastSearch = SystemClock.elapsedRealtime()
    }

    private suspend fun throttleImage() = imageLock.withLock {
        val since = SystemClock.elapsedRealtime() - lastImage
        if (since < 350) delay(350 - since)
        lastImage = SystemClock.elapsedRealtime()
    }

    /** Releases matching the album's title (and artist, when known), newest search first. */
    suspend fun searchReleases(albumTitle: String, artist: String): List<MbRelease> {
        val query = releaseQuery(albumTitle, artist) ?: return emptyList()
        throttleSearch()
        val url = "$MB/release?query=${URLEncoder.encode(query, "UTF-8")}&fmt=json&limit=8"
        val bytes = fetch(url, "application/json", 2_000_000) ?: return emptyList()
        return parseReleaseSearch(String(bytes, Charsets.UTF_8))
    }

    /** The Cover Art Archive listing for a release group (preferred, the canonical album art) or a single release. */
    suspend fun coverJson(releaseGroupId: String?, releaseId: String?): String? {
        if (releaseGroupId != null) {
            throttleImage()
            fetch("$CAA/release-group/$releaseGroupId", "application/json", 1_000_000)?.let { return String(it, Charsets.UTF_8) }
        }
        if (releaseId != null) {
            throttleImage()
            fetch("$CAA/release/$releaseId", "application/json", 1_000_000)?.let { return String(it, Charsets.UTF_8) }
        }
        return null
    }

    /** Downloads one image; null when the archive has nothing there. */
    suspend fun download(url: String): ByteArray? {
        throttleImage()
        return fetch(upgradeHttps(url), "image/*", 12_000_000)
    }

    private suspend fun fetch(url: String, accept: String, maxBytes: Int, retry: Boolean = true): ByteArray? = withContext(Dispatchers.IO) {
        var current = url
        var redirects = 0
        while (redirects <= 6) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", accept)
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: throw IOException("A redirect without a destination.")
                    current = URL(URL(current), location).toString()
                    redirects++
                    continue
                }
                if (code == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null
                if (code == 503 && retry) {
                    val wait = connection.getHeaderField("Retry-After")?.toIntOrNull()?.coerceIn(1, 10) ?: 3
                    delay(wait * 1_000L)
                    return@withContext fetch(url, accept, maxBytes, retry = false)
                }
                if (code !in 200..299) throw IOException("The service answered $code from ${URL(current).host}.")
                val output = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) throw IOException("The answer was too large.")
                        output.write(buffer, 0, read)
                    }
                }
                return@withContext output.toByteArray()
            } finally {
                connection.disconnect()
            }
        }
        null
    }
}
