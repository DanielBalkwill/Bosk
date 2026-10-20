package com.generic.mnemos.service.ambient

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.generic.mnemos.core.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Battery-aware periodic tick. WorkManager (not polling) wakes us; we respect the
 * enabled flag + quiet hours, then ask the service to surface the next curriculum item.
 */
@HiltWorker
class AmbientWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = settings.prefs.first()
        if (prefs.enabled && !prefs.isQuietNow()) {
            AmbientService.surface(applicationContext)
        }
        return Result.success()
    }
}
