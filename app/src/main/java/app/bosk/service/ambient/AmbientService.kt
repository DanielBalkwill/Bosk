package app.bosk.service.ambient

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import app.bosk.core.curriculum.CurriculumProvider
import app.bosk.core.curriculum.SurfaceItem
import app.bosk.core.settings.DeliveryMode
import app.bosk.core.settings.SettingsRepository
import app.bosk.core.tts.SpeechItem
import app.bosk.core.tts.TtsQueueEngine
import app.bosk.service.notification.NotificationHelper
import app.bosk.service.overlay.OverlayService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that owns playback. Hosts the [TtsQueueEngine], keeps an ongoing
 * Now Playing notification in sync, and handles control intents (from the notification or
 * the ambient worker). The flagship ambient loop runs here.
 */
@AndroidEntryPoint
class AmbientService : Service() {

    @Inject lateinit var tts: TtsQueueEngine
    @Inject lateinit var notifications: NotificationHelper
    @Inject lateinit var curriculum: CurriculumProvider
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var llm: app.bosk.core.ai.llm.LlmClient
    @Inject lateinit var notes: app.bosk.core.notes.NotesRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile private var lastSurfaceMs = 0L

    // On-unlock trigger: while the daemon runs, unlocking the phone can surface the next item —
    // the "ambient companion meets you when you pick up the phone" moment. Context-registered
    // (USER_PRESENT can't be manifest-registered on API 26+), rate-limited by UNLOCK_MIN_GAP_MS.
    private val unlockReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_USER_PRESENT) return
            scope.launch {
                val prefs = settings.prefs.first()
                if (!prefs.enabled || prefs.isQuietNow()) return@launch
                if (System.currentTimeMillis() - lastSurfaceMs < UNLOCK_MIN_GAP_MS) return@launch
                surfaceNext()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannels()
        startForegroundNow()
        // Keep the notification reflecting playback state.
        scope.launch {
            tts.state.collect { state ->
                notifications.notifyNowPlaying(notifications.buildNowPlaying(state))
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            android.content.IntentFilter(Intent.ACTION_USER_PRESENT),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> tts.play()
            ACTION_PAUSE -> tts.pause()
            ACTION_SKIP -> tts.skip()
            ACTION_STOP -> { tts.stop(); stopSelf() }
            ACTION_SPEAK -> intent.getStringExtra(EXTRA_TEXT)?.let {
                tts.enqueue(SpeechItem(it, priority = 4))
            }
            ACTION_SURFACE -> scope.launch { surfaceNext() }
        }
        return START_STICKY
    }

    /** Pull the next curriculum item and deliver it per the user's chosen mode. */
    private suspend fun surfaceNext() {
        if (settings.prefs.first().isQuietNow()) return
        lastSurfaceMs = System.currentTimeMillis()
        val item = curriculum.next()
        val delivery = settings.prefs.first().delivery
        // Overlay + a due card = an interactive quick quiz (reveal the answer, rate yourself) —
        // active recall instead of passively reading the question.
        if (delivery == DeliveryMode.OVERLAY && item is SurfaceItem.ReviewCard) {
            OverlayService.showQuiz(this, item.reviewId, item.front, item.back)
            return
        }
        val plain = describe(item) ?: return
        // Spoken delivery gets an AI micro-lesson when a provider is configured; deterministic
        // text is the ever-present fallback (no key, offline, or the call fails).
        val (title, body) =
            (if (delivery == DeliveryMode.TTS) composeMicroLesson(item) else null) ?: plain
        when (delivery) {
            DeliveryMode.TTS -> tts.enqueue(SpeechItem(body, priority = 3))
            DeliveryMode.NOTIFICATION -> notifications.postAmbient(NotificationHelper.AMBIENT_ID, title, body)
            DeliveryMode.OVERLAY -> OverlayService.show(this, title, body)
        }
    }

    /**
     * Compose a ≤60-word spoken micro-lesson for [item] — re-teach, then end on a recall
     * question. Null (→ deterministic fallback) when no provider is set or the call fails.
     */
    private suspend fun composeMicroLesson(item: SurfaceItem): Pair<String, String>? {
        if (llm.activeProvider == null) return null
        return when (item) {
            is SurfaceItem.ReviewCard -> {
                val prompt = "Compose a spoken micro-lesson of at most 60 words: re-teach the idea " +
                    "behind this flashcard in one or two sentences, then END by asking the recall " +
                    "question so the listener answers in their head. Plain speech, no markdown.\n" +
                    "Question: ${item.front}\nAnswer: ${item.back}"
                llm.generate(prompt).getOrNull()?.trim()?.takeIf { it.isNotBlank() }
                    ?.let { "Micro-lesson" to it }
            }
            is SurfaceItem.NoteSummary -> {
                val note = runCatching { notes.get(item.noteId) }.getOrNull() ?: return null
                if (note.body.isBlank()) return null
                val prompt = "In at most 50 spoken words, recap the key idea of this note, then end " +
                    "with one short reflection question. Plain speech, no markdown.\n" +
                    "Note \"${note.title}\":\n" +
                    app.bosk.core.notes.Redaction.forAi(note.body).take(1200)
                llm.generate(prompt).getOrNull()?.trim()?.takeIf { it.isNotBlank() }
                    ?.let { "Micro-lesson" to it }
            }
            else -> null
        }
    }

    private fun describe(item: SurfaceItem): Pair<String, String>? = when (item) {
        is SurfaceItem.ReviewCard -> "Review due" to item.front
        is SurfaceItem.NoteSummary -> "Revisit" to "Time to revisit \"${item.title}\"."
        is SurfaceItem.ConnectOrphan ->
            "Connect" to "\"${item.label}\" has no connections yet — want to link it?"
        SurfaceItem.Nothing -> null
    }

    private fun startForegroundNow() {
        val notification = notifications.buildNowPlaying(tts.state.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, NotificationHelper.NOW_PLAYING_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NotificationHelper.NOW_PLAYING_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(unlockReceiver) }
        tts.stop()
        super.onDestroy()
    }

    companion object {
        /** Min gap between an unlock-triggered surface and the previous one (any trigger). */
        const val UNLOCK_MIN_GAP_MS = 15 * 60 * 1000L

        const val ACTION_PLAY = "bosk.PLAY"
        const val ACTION_PAUSE = "bosk.PAUSE"
        const val ACTION_SKIP = "bosk.SKIP"
        const val ACTION_STOP = "bosk.STOP"
        const val ACTION_SPEAK = "bosk.SPEAK"
        const val ACTION_SURFACE = "bosk.SURFACE"
        const val EXTRA_TEXT = "extra_text"

        /** Ambient interval floors (minutes): free vs paid (gated in CP6). */
        const val INTERVAL_FREE_MIN = 60L
        const val INTERVAL_PAID_MIN = 15L

        fun speak(context: Context, text: String) {
            // startForegroundService: legal from background; onCreate calls startForeground() in time.
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                Intent(context, AmbientService::class.java)
                    .setAction(ACTION_SPEAK).putExtra(EXTRA_TEXT, text),
            )
        }

        fun surface(context: Context) {
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                Intent(context, AmbientService::class.java).setAction(ACTION_SURFACE),
            )
        }

        fun play(context: Context) = sendAction(context, ACTION_PLAY)
        fun pause(context: Context) = sendAction(context, ACTION_PAUSE)
        fun skip(context: Context) = sendAction(context, ACTION_SKIP)

        private fun sendAction(context: Context, action: String) {
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                Intent(context, AmbientService::class.java).setAction(action),
            )
        }
    }
}
