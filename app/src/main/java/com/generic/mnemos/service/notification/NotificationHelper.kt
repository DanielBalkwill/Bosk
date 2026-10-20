package com.generic.mnemos.service.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.generic.mnemos.R
import com.generic.mnemos.core.tts.TtsState
import com.generic.mnemos.service.ambient.AmbientService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Builds Mnemos's notifications: the ongoing "Now Playing" controls and ambient nudges. */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannels() {
        manager.createNotificationChannel(
            NotificationChannel(NOW_PLAYING, "Now Playing", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Playback controls for spoken notes." },
        )
        manager.createNotificationChannel(
            NotificationChannel(AMBIENT, "Ambient nudges", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Surfaced reviews and insights." },
        )
    }

    /** Persistent, podcast-style notification with play/pause + skip. */
    fun buildNowPlaying(state: TtsState): Notification {
        val playPauseAction = if (state.isPlaying) {
            action("Pause", AmbientService.ACTION_PAUSE, android.R.drawable.ic_media_pause)
        } else {
            action("Play", AmbientService.ACTION_PLAY, android.R.drawable.ic_media_play)
        }
        return NotificationCompat.Builder(context, NOW_PLAYING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (state.isPlaying) "Speaking…" else "Mnemos")
            .setContentText(state.currentText?.take(120) ?: "Ready")
            .setOngoing(state.isPlaying)
            .setOnlyAlertOnce(true)
            .addAction(playPauseAction)
            .addAction(action("Skip", AmbientService.ACTION_SKIP, android.R.drawable.ic_media_next))
            .build()
    }

    fun postAmbient(id: Int, title: String, text: String) {
        val n = NotificationCompat.Builder(context, AMBIENT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        manager.notify(id, n)
    }

    fun notifyNowPlaying(notification: Notification) = manager.notify(NOW_PLAYING_ID, notification)

    private fun action(title: String, action: String, icon: Int): NotificationCompat.Action {
        val intent = Intent(context, AmbientService::class.java).setAction(action)
        val pi = PendingIntent.getService(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(icon, title, pi).build()
    }

    companion object {
        const val NOW_PLAYING = "mnemos.now_playing"
        const val AMBIENT = "mnemos.ambient"
        const val NOW_PLAYING_ID = 1001
        const val AMBIENT_ID = 2001
    }
}
