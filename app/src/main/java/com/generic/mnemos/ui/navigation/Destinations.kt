package com.generic.mnemos.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector

/** Top-level navigation destinations shown in the bottom bar. */
enum class TopDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Home("home", com.generic.mnemos.R.string.nav_home, Icons.Outlined.Today),
    Notes("notes", com.generic.mnemos.R.string.nav_notes, Icons.Outlined.Description),
    Graph("graph", com.generic.mnemos.R.string.nav_graph, Icons.Outlined.AccountTree),
    Review("review", com.generic.mnemos.R.string.nav_review, Icons.Outlined.Style),
    Settings("settings", com.generic.mnemos.R.string.nav_settings, Icons.Outlined.Settings),
}

/** Non-tab routes (full-screen flows). */
object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main" // the swipeable pager hosting all five top destinations
    const val AI_PARTNER = "ai_partner" // full-screen, opened from Settings
    const val QUEUE = "queue" // full-screen queue manager, opened from Home
    const val COMPILER = "compiler" // full-screen Compiler, opened from the graph (also a Notes page)
    const val AI_INTERFACE = "ai_interface" // terminal-style AI console, opened from Home
    const val FLASHCARD_GRAPH = "flashcard_graph" // green scoped graph of flashcard notes + similar notes
    const val NOTE_GRAPH = "note_graph" // the editor's "View connections" graph, scoped to the open note
    const val AI_TUTOR_BASE = "ai_tutor" // notes-grounded study chat; full route: ai_tutor?roadmap={bool}&note={id}
    const val NOTE_EDITOR_BASE = "note_editor" // full route: note_editor/{noteId}, -1 = new
    const val RECYCLE_BIN = "recycle_bin" // soft-deleted notes, opened from the Notes top bar
    const val STATS = "stats" // streak + review-activity statistics, opened from Home's greeting
    // TODO CP5: const val REVIEW_SESSION = "review/session"
}
