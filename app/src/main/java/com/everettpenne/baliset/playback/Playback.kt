package com.everettpenne.baliset.playback

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.everettpenne.baliset.data.Artwork
import com.everettpenne.baliset.data.LEVEL_GAIN_EXTRA
import com.everettpenne.baliset.data.SOURCE_JELLYFIN
import com.everettpenne.baliset.data.jellyfin.Jellyfin
import com.everettpenne.baliset.data.Library
import com.everettpenne.baliset.data.Levels
import com.everettpenne.baliset.data.Settings
import com.everettpenne.baliset.data.Track
import com.everettpenne.baliset.util.clock
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the whole app reads to draw the mini player, Now Playing and Up Next. One immutable snapshot of the session. */
data class NowPlaying(
    val connected: Boolean = false,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val track: Track? = null,
    val queue: List<Track> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val sleepLabel: String? = null,
) {
    val hasNext: Boolean get() = index < queue.lastIndex || repeatMode != Player.REPEAT_MODE_OFF
    val hasPrevious: Boolean get() = index > 0 || positionMs > 3_000 || repeatMode == Player.REPEAT_MODE_ALL
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * The app's side of the session: connects a [MediaController] to [PlaybackService], starts queues, and mirrors what the player is doing
 * into one [StateFlow] the screens draw.
 *
 * The queue is kept here as real [Track]s, not just media ids, so Up Next can show artwork and tapping an entry can go to its album. The
 * service owns the sound; this object owns the order, shuffle and repeat, and the sleep timer (which lives with the UI because it is a
 * listening intention, not part of the audio graph).
 */
object Playback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var connecting: ListenableFuture<MediaController>? = null
    private var appContext: Context? = null
    private var library: Library? = null
    private var settings: Settings? = null
    private var levels: Levels? = null
    private var jellyfin: Jellyfin? = null

    private var baseQueue: List<Track> = emptyList()
    private var queue: List<Track> = emptyList()
    private var shuffled = false
    private var ticker: Job? = null
    private var sleepJob: Job? = null
    /** A play request made before the controller finished connecting (a cold start from an "open with" link). */
    private var pending: (() -> Unit)? = null
    private var sleepAtEnd = false

    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    /** Connects once. Safe to call from every composition: later calls are ignored. */
    fun start(context: Context, library: Library, settings: Settings, levels: Levels, jellyfin: Jellyfin) {
        appContext = context.applicationContext
        this.library = library
        this.settings = settings
        this.levels = levels
        this.jellyfin = jellyfin
        if (controller != null || connecting != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        connecting = future
        future.addListener({
            val connected = runCatching { future.get() }.getOrNull()
            if (connected == null) { connecting = null; return@addListener }
            controller = connected
            connected.addListener(listener)
            refresh()
            pending?.let { action -> pending = null; action() }
            if (ticker == null) ticker = scope.launch {
                while (true) {
                    val player = controller ?: break
                    if (player.isPlaying) _state.value = _state.value.copy(positionMs = player.currentPosition.coerceAtLeast(0))
                    delay(500)
                }
            }
        }, MoreExecutors.directExecutor())
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { refresh() }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                mediaItem?.mediaId?.let { settings?.rememberPlayed(it) }
                if (sleepAtEnd) {
                    controller?.pause()
                    sleepAtEnd = false
                    _state.value = _state.value.copy(sleepLabel = null)
                }
            }
            refresh()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                controller?.currentMediaItem?.mediaId?.let { settings?.rememberPlayed(it) }
            } else {
                savePosition()
            }
            refresh()
        }
    }

    private fun refresh() {
        val player = controller ?: return
        if (queue.isEmpty() && player.mediaItemCount > 0) queue = readQueue(player)
        val index = player.currentMediaItemIndex.coerceIn(0, maxOf(0, queue.lastIndex))
        val current = queue.getOrNull(index)
        val reported = player.duration
        val duration = if (reported > 0 && reported != C.TIME_UNSET) reported else current?.durationMs ?: 0L
        _state.value = _state.value.copy(
            connected = true,
            playing = player.isPlaying,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            track = current,
            queue = queue,
            index = index,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = duration.coerceAtLeast(0),
            shuffle = shuffled,
            repeatMode = player.repeatMode,
        )
    }

    private fun readQueue(player: Player): List<Track> = (0 until player.mediaItemCount).mapNotNull { index ->
        val item = player.getMediaItemAt(index)
        val meta = item.mediaMetadata
        library?.find(item.mediaId) ?: Track(
            id = item.mediaId,
            uri = item.localConfiguration?.uri?.toString().orEmpty(),
            title = meta.title?.toString().orEmpty(),
            artist = meta.artist?.toString().orEmpty(),
            album = meta.albumTitle?.toString().orEmpty(),
            durationMs = meta.durationMs ?: 0L,
        )
    }

    // ---- starting a queue --------------------------------------------------------------------------------------------------

    /** Plays [tracks] from [startIndex]. With [shuffle], the chosen song comes first and the rest are shuffled. */
    fun play(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        if (controller == null) { pending = { play(tracks, startIndex, shuffle) }; return }
        val start = tracks.getOrNull(startIndex) ?: tracks.first()
        baseQueue = tracks
        val ordered = if (shuffle) QueueLogic.shuffleAround(tracks, start.id) else tracks
        val index = QueueLogic.indexOf(ordered, start.id).coerceAtLeast(0)
        shuffled = shuffle
        queue = ordered
        scope.launch { setAndPlay(ordered, index, 0L) }
    }

    /** Adds a whole collection and starts at the top, replacing whatever was playing. */
    fun playAll(tracks: List<Track>, shuffle: Boolean = false) = play(tracks, 0, shuffle)

    private suspend fun setAndPlay(tracks: List<Track>, index: Int, positionMs: Long) {
        val player = controller ?: return
        val items = withContext(Dispatchers.IO) { itemsFor(tracks) }
        if (items.isEmpty()) return
        player.setMediaItems(items, index.coerceIn(0, items.lastIndex), positionMs)
        player.prepare()
        player.play()
        queue = tracks
        refresh()
    }

    private suspend fun itemsFor(tracks: List<Track>): List<MediaItem> {
        val context = appContext ?: return emptyList()
        val artwork = HashMap<String, ByteArray?>()
        return tracks.mapNotNull { track ->
            val remote = track.source == SOURCE_JELLYFIN
            if (remote) jellyfin?.ensureArt(track.albumKey, track.artUrl)
            val local = if (remote) track.remoteId?.let { jellyfin?.downloads?.fileFor(it) } else null
            val uri = local?.let { Uri.fromFile(it).toString() } ?: track.uri
            if (uri.isBlank()) return@mapNotNull null
            val bytes = artwork.getOrPut(track.albumKey) { Artwork.bytes(context, track.albumKey) }
            // The gain is always carried, so the service can honour the leveling switch as it is now, not as it was when the
            // queue was built. Tracks without a measurement simply carry 1.0.
            val gain = levels?.gainFor(track.id) ?: 1f
            val extras = Bundle().apply { putFloat(LEVEL_GAIN_EXTRA, gain) }
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setAlbumArtist(track.albumArtist ?: track.artist)
                .setExtras(extras)
                .apply { if (bytes != null) setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
                .build()
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(uri)
                .setMediaMetadata(metadata)
                .build()
        }
    }

    // ---- transport ---------------------------------------------------------------------------------------------------------

    fun toggle() {
        val player = controller ?: return
        when {
            player.isPlaying -> player.pause()
            player.playbackState == Player.STATE_IDLE -> { player.prepare(); player.play() }
            else -> player.play()
        }
    }

    fun playTrack(track: Track) {
        val index = queue.indexOfFirst { it.id == track.id }
        val player = controller ?: return
        if (index >= 0 && index < player.mediaItemCount) { player.seekTo(index, 0L); player.play(); refresh() }
        else play(listOf(track), 0)
    }

    fun next() {
        val player = controller ?: return
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
        else if (player.mediaItemCount > 0) player.seekTo(0, 0L)
        refresh()
    }

    fun previous() {
        val player = controller ?: return
        if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
        else player.seekTo(0, 0L)
        refresh()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0))
        refresh()
    }

    fun seekToFraction(fraction: Float) {
        val duration = _state.value.durationMs
        if (duration > 0) seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun cycleRepeat() {
        val player = controller ?: return
        player.repeatMode = QueueLogic.nextRepeatMode(player.repeatMode)
        refresh()
    }

    /** Reorders the queue around the current song, so the Up Next page always tells the truth. */
    fun toggleShuffle() {
        val player = controller ?: return
        if (queue.isEmpty()) return
        val current = queue.getOrNull(player.currentMediaItemIndex) ?: return
        val position = player.currentPosition
        val turnOn = !shuffled
        val source = baseQueue.ifEmpty { queue }
        val nextQueue = if (turnOn) QueueLogic.shuffleAround(source, current.id) else source
        if (nextQueue.isEmpty()) return
        val index = QueueLogic.indexOf(nextQueue, current.id).coerceAtLeast(0)
        shuffled = turnOn
        queue = nextQueue
        scope.launch { setAndPlay(nextQueue, index, position) }
    }

    // ---- queue editing -----------------------------------------------------------------------------------------------------

    fun playNext(track: Track) {
        val player = controller ?: return
        if (queue.isEmpty()) { play(listOf(track), 0); return }
        val at = (player.currentMediaItemIndex + 1).coerceIn(0, queue.size)
        scope.launch {
            val item = withContext(Dispatchers.IO) { itemsFor(listOf(track)) }.firstOrNull() ?: return@launch
            player.addMediaItem(at, item)
            queue = queue.toMutableList().also { it.add(at, track) }
            baseQueue = baseQueue.toMutableList().also { base ->
                if (base.none { it.id == track.id }) base.add(at.coerceAtMost(base.size), track)
            }
            refresh()
        }
    }

    fun addToQueue(track: Track) {
        val player = controller ?: return
        if (queue.isEmpty()) { play(listOf(track), 0); return }
        scope.launch {
            val item = withContext(Dispatchers.IO) { itemsFor(listOf(track)) }.firstOrNull() ?: return@launch
            player.addMediaItem(item)
            queue = queue + track
            if (baseQueue.none { it.id == track.id }) baseQueue = baseQueue + track
            refresh()
        }
    }

    fun moveInQueue(from: Int, to: Int) {
        val player = controller ?: return
        if (from == to || from !in queue.indices || to !in queue.indices) return
        player.moveMediaItem(from, to)
        queue = QueueLogic.move(queue, from, to)
        if (!shuffled) baseQueue = queue
        refresh()
    }

    fun removeFromQueue(index: Int) {
        val player = controller ?: return
        val removed = queue.getOrNull(index) ?: return
        if (index !in 0 until player.mediaItemCount) return
        player.removeMediaItem(index)
        queue = QueueLogic.removeAt(queue, index)
        baseQueue = baseQueue.filterNot { it.id == removed.id }
        if (queue.isEmpty()) {
            player.clearMediaItems()
            _state.value = NowPlaying()
        } else refresh()
    }

    fun clearQueue() {
        val player = controller ?: return
        player.clearMediaItems()
        queue = emptyList(); baseQueue = emptyList(); shuffled = false
        _state.value = NowPlaying(sleepLabel = _state.value.sleepLabel)
    }

    // ---- sleep timer -------------------------------------------------------------------------------------------------------

    /** [minutes] counts down and pauses; null cancels; with [endOfTrack] the current song finishes first. */
    fun setSleep(minutes: Int?, endOfTrack: Boolean = false) {
        sleepJob?.cancel()
        sleepJob = null
        sleepAtEnd = false
        if (endOfTrack) {
            sleepAtEnd = true
            _state.value = _state.value.copy(sleepLabel = "End of track")
            return
        }
        if (minutes == null) {
            _state.value = _state.value.copy(sleepLabel = null)
            return
        }
        val until = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = scope.launch {
            while (true) {
                val left = until - System.currentTimeMillis()
                if (left <= 0) break
                _state.value = _state.value.copy(sleepLabel = "Sleep in ${QueueLogic.sleepText(left)}")
                delay(1_000)
            }
            controller?.pause()
            _state.value = _state.value.copy(sleepLabel = null)
        }
    }

    /** "Jump back in": reopens the last song where it was left. */
    fun resumeLast() {
        val id = settings?.lastTrackId?.value ?: return
        val track = library?.find(id) ?: return
        val position = settings?.lastPosition?.value ?: 0L
        baseQueue = listOf(track)
        queue = listOf(track)
        shuffled = false
        scope.launch { setAndPlay(listOf(track), 0, position) }
    }

    private fun savePosition() {
        val player = controller ?: return
        settings?.rememberPosition(player.currentMediaItem?.mediaId, player.currentPosition)
    }

    /** The row subtitle for the mini player: the artist, or a quiet prompt when nothing is loaded. */
    fun subtitle(state: NowPlaying): String = state.track?.artist ?: "Nothing playing"
}
