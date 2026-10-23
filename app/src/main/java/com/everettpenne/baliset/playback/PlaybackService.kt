package com.everettpenne.baliset.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.everettpenne.baliset.MainActivity
import com.everettpenne.baliset.data.LEVEL_GAIN_EXTRA

/**
 * Owns the sound, so it outlives the screen: switch apps, lock the phone or go Home and the music keeps playing with a media
 * notification, lock-screen controls and a session that Uno Launcher's island can read.
 *
 * Two ExoPlayers share one session. Normally only one is playing, and the session points at it, so gapless albums behave exactly as
 * before. When a crossfade is configured, the idle player starts the next queue entry under the last seconds of the current one;
 * the volumes ramp in opposite directions and the session switches to the new player when the fade is done. Audio focus is managed
 * here, once, because two ExoPlayers each asking for focus would fight.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var playerA: ExoPlayer
    private lateinit var playerB: ExoPlayer
    private var active: ExoPlayer? = null
    private val gainA = GainProcessor()
    private val gainB = GainProcessor()
    private val handler = Handler(Looper.getMainLooper())
    private val preferences by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private var hasFocus = false
    private var resumeAfterFocus = false

    private var crossfading = false
    private var fadingFrom: ExoPlayer? = null
    private var fadeStart = 0L
    private var fadeDuration = 0L

    private val musicAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    /** AIFF is not in Media3, so our extractor is tried before the standard ones. */
    private val extractors = object : ExtractorsFactory {
        private val defaults = DefaultExtractorsFactory()
        override fun createExtractors(): Array<Extractor> = arrayOf<Extractor>(AiffExtractor()) + defaults.createExtractors()
        override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
            arrayOf<Extractor>(AiffExtractor()) + defaults.createExtractors(uri, responseHeaders)
    }

    private val levelingListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "leveling") {
            playerA.let { applyLevel(it, it.currentMediaItem) }
            playerB.let { applyLevel(it, it.currentMediaItem) }
        }
    }

    private val focusRequest: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        resumeAfterFocus = false
                        pauseAll()
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        resumeAfterFocus = active?.isPlaying == true
                        pauseAll()
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        gainA.duck = 0.2f
                        gainB.duck = 0.2f
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        gainA.duck = 1f
                        gainB.duck = 1f
                        if (resumeAfterFocus) {
                            resumeAfterFocus = false
                            active?.play()
                        }
                    }
                }
            }
            .build()
    }

    private val ticker = object : Runnable {
        override fun run() {
            val current = active
            if (current != null) {
                val fadeMs = preferences.getInt("crossfade_ms", 0)
                if (!crossfading && fadeMs > 0 && current.isPlaying && current.hasNextMediaItem()) {
                    val duration = current.duration
                    val remaining = duration - current.currentPosition
                    if (duration > 0 && remaining in 1..fadeMs.toLong()) {
                        startFade(current, remaining)
                    }
                }
            }
            if (crossfading) advanceFade()
            if (playerA.isPlaying || playerB.isPlaying || crossfading) handler.postDelayed(this, 50)
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferences.registerOnSharedPreferenceChangeListener(levelingListener)
        playerA = buildPlayer(gainA)
        playerB = buildPlayer(gainB)
        active = playerA
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, playerA).setSessionActivity(open).build()
    }

    private fun buildPlayer(gain: GainProcessor): ExoPlayer {
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this, extractors))
            .setRenderersFactory(LevelRenderersFactory(this, gain))
            .setAudioAttributes(musicAttributes, /* handleAudioFocus = */ false)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .setMaxSeekToPreviousPositionMs(3_000)
            .setPauseAtEndOfMediaItems(false)
            .build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applyLevel(player, mediaItem)
                if (crossfading && player === fadingFrom) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) finishFade() else cancelFade()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    requestFocus()
                    startTicker()
                } else {
                    if (!playerA.isPlaying && !playerB.isPlaying) {
                        abandonFocus()
                        handler.removeCallbacks(ticker)
                    }
                    if (crossfading && player === fadingFrom) cancelFade()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (crossfading && player === fadingFrom && reason == Player.DISCONTINUITY_REASON_SEEK) cancelFade()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (crossfading && player === fadingFrom) cancelFade()
            }
        })
        return player
    }

    private fun startTicker() {
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun requestFocus() {
        if (hasFocus) return
        hasFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        audioManager.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
        gainA.duck = 1f
        gainB.duck = 1f
    }

    private fun pauseAll() {
        if (crossfading) cancelFade()
        playerA.pause()
        playerB.pause()
    }

    private fun applyLevel(player: Player, item: MediaItem?) {
        val enabled = preferences.getBoolean("leveling", true)
        val measured = item?.mediaMetadata?.extras?.getFloat(LEVEL_GAIN_EXTRA, 1f) ?: 1f
        val processor = if (player === playerA) gainA else gainB
        processor.gain = if (enabled) measured else 1f
    }

    // ---- crossfade ---------------------------------------------------------------------------------------------------------

    /** Starts the next queue entry on the idle player and ramps the two against each other. */
    private fun startFade(from: ExoPlayer, remainingMs: Long) {
        val to = if (from === playerA) playerB else playerA
        if (to.isPlaying || to.playbackState == Player.STATE_BUFFERING) return
        val next = from.nextMediaItemIndex
        if (next == C.INDEX_UNSET || next == from.currentMediaItemIndex) return
        val items = (0 until from.mediaItemCount).map { from.getMediaItemAt(it) }
        runCatching {
            to.stop()
            to.clearMediaItems()
            to.setMediaItems(items, next, 0L)
            to.repeatMode = from.repeatMode
            to.volume = 0f
            to.prepare()
            to.play()
        }.onFailure { return }
        fadingFrom = from
        crossfading = true
        fadeStart = SystemClock.elapsedRealtime()
        fadeDuration = remainingMs.coerceAtLeast(250L)
        startTicker()
    }

    private fun advanceFade() {
        val from = fadingFrom ?: run { cancelFade(); return }
        val to = if (from === playerA) playerB else playerA
        val progress = ((SystemClock.elapsedRealtime() - fadeStart).toFloat() / fadeDuration).coerceIn(0f, 1f)
        from.volume = 1f - progress
        to.volume = progress
        if (progress >= 1f || !from.isPlaying) finishFade()
    }

    private fun finishFade() {
        val from = fadingFrom ?: return
        val to = if (from === playerA) playerB else playerA
        crossfading = false
        fadingFrom = null
        to.volume = 1f
        from.volume = 1f
        from.pause()
        from.stop()
        from.clearMediaItems()
        active = to
        applyLevel(to, to.currentMediaItem)
        session?.setPlayer(to)
    }

    private fun cancelFade() {
        val from = fadingFrom ?: run { crossfading = false; return }
        val to = if (from === playerA) playerB else playerA
        crossfading = false
        fadingFrom = null
        from.volume = 1f
        to.pause()
        to.volume = 1f
        to.clearMediaItems()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Music keeps going when the app is swiped away; if nothing is playing there is no reason to stay. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (active?.isPlaying != true) pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        preferences.unregisterOnSharedPreferenceChangeListener(levelingListener)
        session?.release()
        session = null
        playerA.release()
        playerB.release()
        abandonFocus()
        super.onDestroy()
    }

    /** The stock audio renderer, but with the leveling processor in its sink. */
    @UnstableApi
    private class LevelRenderersFactory(
        context: Context,
        private val gain: GainProcessor,
    ) : DefaultRenderersFactory(context) {
        override fun buildAudioRenderers(
            context: Context,
            extensionRendererMode: Int,
            mediaCodecSelector: MediaCodecSelector,
            enableDecoderFallback: Boolean,
            audioSink: AudioSink,
            eventHandler: Handler,
            eventListener: AudioRendererEventListener,
            out: ArrayList<Renderer>,
        ) {
            out.add(
                MediaCodecAudioRenderer(
                    context,
                    mediaCodecSelector,
                    eventHandler,
                    eventListener,
                    AudioCapabilities.getCapabilities(context),
                    gain,
                ),
            )
        }
    }
}
