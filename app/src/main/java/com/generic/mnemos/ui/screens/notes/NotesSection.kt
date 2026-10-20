package com.generic.mnemos.ui.screens.notes

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * The Notes section hosts two stacked pages in a [VerticalPager]: the notes list (page 0) and the
 * Compiler (page 1). Swipe up — or tap the Compiler button in the Notes top bar — to reach it; the
 * Compiler's up-arrow swipes back down to the notes.
 */
@Composable
fun NotesSection(
    onOpenNote: (Long) -> Unit,
    onCreateNote: () -> Unit,
    onOpenAiTutor: () -> Unit = {},
    onOpenNoteInTutor: (Long) -> Unit = {},
    onOpenRecycleBin: () -> Unit = {},
) {
    val pager = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    VerticalPager(
        state = pager,
        modifier = Modifier.fillMaxSize(),
        // Pre-compose the neighbour so the swipe reveals an already-laid-out page.
        beyondViewportPageCount = 1,
    ) { page ->
        when (page) {
            0 -> NotesScreen(
                onOpenNote = onOpenNote,
                onCreateNote = onCreateNote,
                onOpenCompiler = { scope.launch { pager.animateScrollToPage(1) } },
                onOpenAiTutor = onOpenAiTutor,
                onOpenNoteInTutor = onOpenNoteInTutor,
                onOpenRecycleBin = onOpenRecycleBin,
            )
            else -> CompilerScreen(
                onOpenNote = onOpenNote,
                onBackToNotes = { scope.launch { pager.animateScrollToPage(0) } },
            )
        }
    }
}
