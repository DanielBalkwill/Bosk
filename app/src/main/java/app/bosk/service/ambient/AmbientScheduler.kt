package app.bosk.service.ambient

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.bosk.core.settings.AmbientPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules / cancels the periodic ambient tick based on the user's settings. */
@Singleton
class AmbientScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun reschedule(prefs: AmbientPrefs) {
        val wm = WorkManager.getInstance(context)
        if (!prefs.enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        // WorkManager enforces a 15-minute minimum period.
        val intervalMin = prefs.intervalMinutes.toLong().coerceAtLeast(15)
        val request = PeriodicWorkRequestBuilder<AmbientWorker>(intervalMin, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true) // don't drain a low battery
                    .build(),
            )
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object {
        private const val WORK_NAME = "bosk.ambient.periodic"
    }
}
