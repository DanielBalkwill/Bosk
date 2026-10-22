package app.bosk.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Lightweight haptic feedback for UI interactions, driven through the host View so the system
 * "touch feedback" setting governs it (no permission, no app-level toggle needed). Three
 * strengths, weakest to strongest:
 *  - [tick]    — subtle: toolbar buttons, chips, suggestion taps
 *  - [confirm] — solid: an action landed (checkbox toggled, card rated, redaction applied)
 *  - [strong]  — heavy: picking up / dragging something, entering a mode
 */
class Haptics(private val view: View) {

    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm() {
        view.performHapticFeedback(
            // CONFIRM exists from API 30; VIRTUAL_KEY is the closest older equivalent.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.VIRTUAL_KEY,
        )
    }

    fun strong() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

/** The [Haptics] for the current composition's host view. */
@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
