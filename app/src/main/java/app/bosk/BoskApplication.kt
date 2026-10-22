package app.bosk

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.bosk.core.graph.GraphRepository
import app.bosk.core.notes.NotesRepository
import app.bosk.core.review.ReviewRepository
import app.bosk.core.settings.SettingsRepository
import app.bosk.service.ambient.AmbientScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point. Hilt's object graph is rooted here, and we supply the
 * WorkManager configuration with Hilt's worker factory so [app.bosk.
 * service.ambient.AmbientWorker] can be injected.
 */
@HiltAndroidApp
class BoskApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var scheduler: AmbientScheduler
    @Inject lateinit var notes: NotesRepository
    @Inject lateinit var graph: GraphRepository
    @Inject lateinit var reviews: ReviewRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Keep the ambient schedule in lock-step with the user's settings.
        appScope.launch {
            settings.prefs.collectLatest { scheduler.reschedule(it) }
        }
        // Warm-start: open the Room connection and pull the first notes + graph emissions on a
        // background thread while the user is still on the splash/onboarding check. This primes
        // SessionCache (the repos write through on every emission), so the first time the Notes
        // and Graph screens subscribe they paint real data immediately instead of an empty flash.
        appScope.launch { runCatching { notes.observeNotes().first() } }
        appScope.launch { runCatching { graph.observeGraph().first() } }
        // Home's very first frame wants the due-review count — pull it once here so the query
        // plan/page cache is hot before the UI subscribes.
        appScope.launch { runCatching { reviews.observeDueCount().first() } }
        // Parse the bundled brand fonts (two variable TTFs + the serif) off the critical path.
        // The framework caches the parsed Typeface per resource, so the first styled text on
        // screen no longer pays the parse on (or near) the first frame.
        appScope.launch {
            runCatching {
                resources.getFont(R.font.dm_sans_variable)
                resources.getFont(R.font.dm_serif_display_regular)
                resources.getFont(R.font.jetbrains_mono_variable)
            }
        }
    }
}
