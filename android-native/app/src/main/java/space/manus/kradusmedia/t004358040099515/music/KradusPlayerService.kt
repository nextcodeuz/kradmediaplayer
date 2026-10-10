package space.manus.kradusmedia.t004358040099515.music

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Foreground media session service: background playback + notification +
 * lock-screen controls (port of expo-audio shouldPlayInBackground +
 * updateLockScreenMetadata / setActiveForLockScreen).
 *
 * Crash-hardened: the service foregrounds ITSELF instantly in onStartCommand
 * with a placeholder. Otherwise, when a track buffers slowly (weak network)
 * or errors before Media3 posts its media notification, the system kills the
 * whole app with ForegroundServiceDidNotStartInTimeException. The placeholder
 * is cancelled as soon as Media3 takes over, or when the service stops.
 */
class KradusPlayerService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = PlayerHolder.get(this)
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        foregroundInstantly()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean
    ) {
        super.onUpdateNotification(session, startInForegroundRequired)
        // Media3 owns the notification from here on; drop our placeholder so
        // two notifications never stack.
        try {
            getSystemService(NotificationManager::class.java).cancel(PLACEHOLDER_ID)
        } catch (_: Exception) {
        }
    }

    private fun foregroundInstantly() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Ijro", NotificationManager.IMPORTANCE_LOW)
            )
            val note = NotificationCompat.Builder(this, CHANNEL)
                .setContentTitle("Kradus Media")
                .setContentText("Tayyorlanmoqda…")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build()
            startForeground(PLACEHOLDER_ID, note)
        } catch (_: Exception) {
            // startForeground itself never throws for a started service; guard anyway.
        }
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "kradus_playback"
        private const val PLACEHOLDER_ID = 41
    }
}
