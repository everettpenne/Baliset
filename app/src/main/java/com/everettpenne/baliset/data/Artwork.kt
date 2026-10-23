package com.everettpenne.baliset.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Album artwork, read once per album and kept as a small JPEG in the app's private storage.
 *
 * Music files carry their cover in their tags, so the picture is read when the library is scanned and cached under the album's key.
 * Lists then load a decoded bitmap off the main thread, and the media session uses the same JPEG bytes, which is how the lock screen,
 * the notification and Uno Launcher's island get a cover without the player loading every track's picture.
 */
object Artwork {
    private const val MAX_PX = 640
    private const val JPEG_QUALITY = 86
    private const val MEMORY_BITMAPS = 24

    private val memory = object : LruCache<String, Bitmap>(MEMORY_BITMAPS) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    private fun dir(context: Context): File = File(context.filesDir, "art").apply { if (!isDirectory) mkdirs() }

    private fun file(context: Context, albumKey: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(albumKey.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }.take(24)
        return File(dir(context), "$name.jpg")
    }

    fun has(context: Context, albumKey: String): Boolean = file(context, albumKey).isFile

    /** The decoded cover, from memory or the cache file; null while it has not been extracted. */
    suspend fun load(context: Context, albumKey: String): Bitmap? = withContext(Dispatchers.IO) {
        memory.get(albumKey)?.let { return@withContext it }
        val cached = file(context, albumKey)
        if (!cached.isFile) return@withContext null
        runCatching { BitmapFactory.decodeFile(cached.path) }.getOrNull()?.also { memory.put(albumKey, it) }
    }

    /** The JPEG bytes, for a media item's metadata (the notification and the island read these). */
    suspend fun bytes(context: Context, albumKey: String): ByteArray? = withContext(Dispatchers.IO) {
        val cached = file(context, albumKey)
        if (!cached.isFile) null else runCatching { cached.readBytes() }.getOrNull()
    }

    /** Reads and saves a cover for the album, if the file has one. Returns true when something was written. */
    suspend fun extract(context: Context, albumKey: String, uri: String): Boolean = withContext(Dispatchers.IO) {
        if (has(context, albumKey)) return@withContext true
        val picture = runCatching { thumbnail(context, uri) }.getOrNull() ?: embedded(context, uri)
        if (picture == null) return@withContext false
        write(context, albumKey, picture)
    }

    private fun write(context: Context, albumKey: String, picture: Bitmap): Boolean {
        val scaled = scaleDown(picture, MAX_PX)
        val bytes = ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
        val target = file(context, albumKey)
        return runCatching {
            val temporary = File(target.parentFile, target.name + ".tmp")
            temporary.writeBytes(bytes)
            temporary.renameTo(target) || run { target.delete(); temporary.renameTo(target) }
        }.getOrDefault(false)
    }

    /** Android's own media thumbnail; for an audio row this is the album cover. */
    private fun thumbnail(context: Context, uri: String): Bitmap? =
        context.contentResolver.loadThumbnail(Uri.parse(uri), Size(MAX_PX, MAX_PX), null)

    /** The cover in the file's own tags, for a folder that Android has not indexed. */
    private fun embedded(context: Context, uri: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        val parsed = Uri.parse(uri)
        return try {
            if (parsed.scheme == "file") retriever.setDataSource(parsed.path) else retriever.setDataSource(context, parsed)
            retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun scaleDown(source: Bitmap, max: Int): Bitmap {
        val largest = maxOf(source.width, source.height)
        if (largest <= max) return source
        val scale = max.toFloat() / largest
        return Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true)
    }

    /** Forgets every cached cover, for "Remove library" and for tests. */
    fun clear(context: Context) {
        memory.evictAll()
        dir(context).listFiles()?.forEach { runCatching { it.delete() } }
    }
}
