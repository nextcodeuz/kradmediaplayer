package space.manus.kradusmedia.t004358040099515.music

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer

/**
 * App-wide singleton ExoPlayer (port of expo-audio useAudioPlayer).
 * Owned by the Application so playback survives navigation; the foreground
 * [KradusPlayerService] attaches a MediaSession for background + lock-screen.
 */
object PlayerHolder {
    @Volatile
    private var player: ExoPlayer? = null

    fun get(context: Context): ExoPlayer {
        return player ?: synchronized(this) {
            player ?: build(context.applicationContext).also { player = it }
        }
    }

    private fun build(appContext: Context): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        return ExoPlayer.Builder(appContext)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    fun loadTrack(player: ExoPlayer, track: Track) {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist.ifBlank { "Noma'lum ijrochi" })
            .setAlbumTitle(track.album.ifBlank { "Kradus Media" })
            .build()
        val item = MediaItem.Builder()
            .setUri(track.uri)
            .setMediaMetadata(metadata)
            .build()
        player.setMediaItem(item)
        player.prepare()
    }

    fun applyRate(player: ExoPlayer, rate: Float) {
        player.playbackParameters = PlaybackParameters(rate.coerceIn(0.5f, 2f))
    }

    fun release() {
        synchronized(this) {
            player?.release()
            player = null
        }
    }
}
