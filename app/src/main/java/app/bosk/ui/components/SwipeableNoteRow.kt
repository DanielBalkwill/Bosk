package app.bosk.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.bosk.core.notes.Note
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A note row in the list with two horizontal gestures:
 *  • swipe RIGHT  → fire [onOpenInTutor] (open the note in the AI Tutor and auto-inquire). The row
 *    springs back; it's an action, not a held state.
 *  • swipe LEFT   → hold open revealing an [Icons.Outlined.Close] (X) button; tapping it calls
 *    [onTrash] to move the note to the recycle bin.
 *
 * Tapping the row opens it via [onClick] when settled, or closes the revealed X first.
 *
 * Built on a plain [draggable] + [Animatable] (rather than SwipeToDismissBox) because we need
 * asymmetric behaviour: one direction triggers-and-resets, the other reveals-and-holds.
 */
@Composable
fun SwipeableNoteRow(
    note: Note,
    onClick: () -> Unit,
    onOpenInTutor: () -> Unit,
    onTrash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val revealPx = with(density) { 76.dp.toPx() }   // how far left-swipe holds open (X button width)
    val triggerPx = with(density) { 120.dp.toPx() } // right-swipe distance that fires the tutor

    val offsetX = remember { Animatable(0f) }

    val settle: () -> Unit = { scope.launch { offsetX.animateTo(0f) } }

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
    ) {
        // Backdrop revealed as the row slides. Right (offset > 0): a tutor hint. Left (offset < 0):
        // the X / move-to-bin button anchored to the trailing edge.
        if (offsetX.value > 0f) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer).padding(start = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    Icons.Outlined.School,
                    contentDescription = "Open in AI Tutor",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        } else if (offsetX.value < 0f) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .width(76.dp)
                        .fillMaxSize()
                        .clickable { onTrash(); settle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Move to recycle bin",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        // Foreground card.
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-revealPx, triggerPx))
                        }
                    },
                    onDragStopped = {
                        scope.launch {
                            when {
                                // Crossed the right-swipe trigger → open in tutor, then reset.
                                offsetX.value >= triggerPx * 0.7f -> {
                                    offsetX.animateTo(0f)
                                    onOpenInTutor()
                                }
                                // Far enough left → hold open showing the X button.
                                offsetX.value <= -revealPx * 0.5f -> offsetX.animateTo(-revealPx)
                                else -> offsetX.animateTo(0f)
                            }
                        }
                    },
                ),
        ) {
            NoteCard(
                note = note,
                // When the X is revealed, a tap closes it instead of opening the note.
                onClick = { if (offsetX.value != 0f) settle() else onClick() },
            )
        }
    }
}
