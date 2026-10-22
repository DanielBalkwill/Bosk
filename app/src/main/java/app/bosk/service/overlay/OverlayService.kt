package app.bosk.service.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.bosk.ui.theme.BoskTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * The flagship overlay popup (SYSTEM_ALERT_WINDOW). A Service can't host Compose for free —
 * Compose needs a Lifecycle/ViewModelStore/SavedStateRegistry owner — so this Service
 * implements all three and attaches them to the ComposeView before adding it to the window.
 */
/** A due flashcard shown as an interactive quick quiz in the overlay (reveal + self-rate). */
data class OverlayQuiz(val reviewId: Long, val back: String)

@AndroidEntryPoint
class OverlayService : Service(), ViewModelStoreOwner, SavedStateRegistryOwner {

    @Inject lateinit var handler: OverlayCommandHandler
    @Inject lateinit var reviews: app.bosk.core.review.ReviewRepository

    private val serviceScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate,
    )

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Bosk"
        val body = intent?.getStringExtra(EXTRA_BODY).orEmpty()
        val quizId = intent?.getLongExtra(EXTRA_QUIZ_ID, -1L) ?: -1L
        val quiz = if (quizId > 0) OverlayQuiz(quizId, intent?.getStringExtra(EXTRA_QUIZ_BACK).orEmpty()) else null
        showWindow(title, body, quiz)
        return START_NOT_STICKY
    }

    private fun showWindow(title: String, body: String, quiz: OverlayQuiz?) {
        removeWindow() // single instance

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                BoskTheme {
                    OverlayContent(
                        title, body, this@OverlayService.handler, onDismiss = ::dismiss,
                        quiz = quiz,
                        onRate = { reviewId, rating ->
                            serviceScope.launch {
                                runCatching { reviews.recordReview(reviewId, rating) }
                            }
                        },
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // No NOT_FOCUSABLE — the command field needs keyboard input. Dim the rest.
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = 80
            dimAmount = 0.4f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        windowManager.addView(view, params)
        composeView = view
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        // TODO CP-later: configurable auto-dismiss timeout (10–60s) + swipe-down to dismiss.
    }

    private fun dismiss() {
        removeWindow()
        stopSelf()
    }

    private fun removeWindow() {
        composeView?.let { v -> runCatching { windowManager.removeView(v) } }
        composeView = null
    }

    override fun onDestroy() {
        removeWindow()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_QUIZ_ID = "quiz_id"
        const val EXTRA_QUIZ_BACK = "quiz_back"

        /** Show the popup if the user has granted the overlay permission; no-op otherwise. */
        fun show(context: Context, title: String, body: String) {
            if (!Settings.canDrawOverlays(context)) return
            context.startService(
                Intent(context, OverlayService::class.java)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_BODY, body),
            )
        }

        /** Show a due card as an interactive quick quiz (reveal the answer, rate yourself). */
        fun showQuiz(context: Context, reviewId: Long, front: String, back: String) {
            if (!Settings.canDrawOverlays(context)) return
            context.startService(
                Intent(context, OverlayService::class.java)
                    .putExtra(EXTRA_TITLE, "Quick quiz")
                    .putExtra(EXTRA_BODY, front)
                    .putExtra(EXTRA_QUIZ_ID, reviewId)
                    .putExtra(EXTRA_QUIZ_BACK, back),
            )
        }
    }
}
