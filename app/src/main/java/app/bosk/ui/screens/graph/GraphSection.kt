package app.bosk.ui.screens.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.bosk.ui.navigation.LocalPageActive
import app.bosk.ui.screens.flashcardgraph.FlashcardGraphScreen

/**
 * The Graph tab's host: a slim sub-tab row over three graph surfaces —
 *  • **Vault** — the full knowledge graph (the existing [GraphScreen], untouched);
 *  • **Flashcards** — the green flashcard-scoped graph (previously only reachable from the
 *    terminal; its back arrow returns to Vault here);
 *  • **AI graph** — the prompt-driven graph maker ([AiGraphScreen]).
 * The chosen sub-tab survives tab switches via rememberSaveable (the pager retains page state).
 */
@Composable
fun GraphSection(
    onOpenNote: (Long) -> Unit,
    onOpenCompiler: () -> Unit,
    onSwipePrev: () -> Unit,
    onSwipeNext: () -> Unit,
    onOpenTutor: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GraphTabChip("Vault", selected = tab == 0) { tab = 0 }
            GraphTabChip("Flashcards", selected = tab == 1) { tab = 1 }
            GraphTabChip("AI graph", selected = tab == 2) { tab = 2 }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            // Same rule as GraphScreen's own gate: as a pre-composed pager neighbour, none of
            // the graph surfaces should subscribe or simulate. GraphScreen gates itself; the
            // other two are cheap-gated here.
            val pageActive = LocalPageActive.current
            when {
                tab == 0 -> GraphScreen(
                    onOpenNote = onOpenNote,
                    onOpenCompiler = onOpenCompiler,
                    onSwipePrev = onSwipePrev,
                    onSwipeNext = onSwipeNext,
                )
                !pageActive -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                tab == 1 -> FlashcardGraphScreen(onBack = { tab = 0 }, onOpenNote = onOpenNote)
                else -> AiGraphScreen(onOpenNote = onOpenNote, onOpenTutor = onOpenTutor)
            }
        }
    }
}

/** One named sub-tab pill; lit in the accent colour while selected. */
@Composable
private fun GraphTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}
