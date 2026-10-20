package com.generic.mnemos.service.queue

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.generic.mnemos.R

/**
 * Fires when a queue alarm/event is due: vibrates, plays the default alarm ringtone, and posts a
 * high-priority notification. Built without DI so it stays a plain manifest-registered receiver;
 * it creates its own alarm channel (sound + vibration) defensively.
 */
class QueueAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder"
        val id = intent.getIntExtra(EXTRA_ID, title.hashCode())

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Only created once; the sound/vibration config is baked in on first creation.
            val channel = NotificationChannel(CHANNEL, "Queue reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alarms and events from your queue."
                enableVibration(true)
                vibrationPattern = VIBRATION
                setSound(
                    alarmSound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
            mgr.createNotificationChannel(channel)
        }

        // Vibrate directly (robust even if the channel was created before this config).
        vibrate(context)

        // Play the default alarm tone directly, so it's audible even when notifications are quiet.
        runCatching {
            alarmSound?.let { RingtoneManager.getRingtone(context, it)?.play() }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("Queued reminder")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(alarmSound)
            .setVibrate(VIBRATION)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(VIBRATION, -1)
            }
        }
    }

    companion object {
        const val CHANNEL = "queue_reminders"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ID = "id"
        private val VIBRATION = longArrayOf(0, 500, 250, 500, 250, 500)
    }
}
