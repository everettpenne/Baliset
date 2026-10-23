package com.everettpenne.baliset.playback

import android.app.PendingIntent
import android.net.Uri
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.everettpenne.baliset.MainActivity

/**
 * Owns the sound, so it outlives the screen: switch apps, lock the phone or go Home and the music keeps playing with a media
 * notification, lock-screen controls and a session that Uno Launcher's island can read.
 *
 * The player is a plain ExoPlayer with music audio attributes (other apps duck rather than pause), gapless playback of consecutive
 * files, audio-focus handling, and "previous" restarting the current song once it is a few seconds in. Swiping the app away leaves a
 * playing queue alone; a paused one lets the service go.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    /** AIFF is not in Media3, so our extractor is tried before the standard ones. */
    private val extractors = object : ExtractorsFactory {
        private val defaults = DefaultExtractorsFactory()
        override fun createExtractors(): Array<Extractor> = arrayOf<Extractor>(AiffExtractor()) + defaults.createExtractors()
        override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
            arrayOf<Extractor>(AiffExtractor()) + defaults.createExtractors(uri, responseHeaders)
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this, extractors))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            // "Previous" restarts the song once it is past three seconds, like every other player.
            .setMaxSeekToPreviousPositionMs(3_000)
            .build()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Music keeps going when the app is swiped away; if nothing is playing there is no reason to stay. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val playing = session?.player?.isPlaying == true
        if (!playing) pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
