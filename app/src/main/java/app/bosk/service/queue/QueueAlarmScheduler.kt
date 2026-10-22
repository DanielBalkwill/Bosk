package app.bosk.service.queue

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules (and cancels) alarms for queue alarm/event items via [AlarmManager]. */
@Singleton
class QueueAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(id: Long, title: String, timeMs: Long) {
        if (timeMs <= System.currentTimeMillis()) return
        val pi = pendingIntent(id, title)
        // Prefer exact; fall back to inexact if we lack the exact-alarm privilege (Android 12+).
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pi)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, timeMs, pi)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, timeMs, pi)
        }
    }

    fun cancel(id: Long) {
        alarmManager.cancel(pendingIntent(id, ""))
    }

    private fun pendingIntent(id: Long, title: String): PendingIntent {
        val intent = Intent(context, QueueAlarmReceiver::class.java).apply {
            putExtra(QueueAlarmReceiver.EXTRA_TITLE, title)
            putExtra(QueueAlarmReceiver.EXTRA_ID, id.toInt())
        }
        return PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
