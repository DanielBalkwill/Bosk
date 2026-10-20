package com.generic.mnemos.ui.screens.graph

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The editor's "View connections" graph: the FULL vault graph UI (toolbar pill, overview,
 * health, groups, search, settings — everything the Graph tab has) scoped to the note that was
 * open and its nearest connections. The top-bar toggle switches the reach between 1 hop (direct
 * links only) and 2 hops (their links too); AI-suggested edges draw gold until confirmed.
 *
 * The view-model instance is scoped to this nav entry, so the hop limit and any toggles here
 * never leak into the main Graph tab. The focused note comes from ActiveNoteStore, which the
 * editor sets on open/save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteGraphScreen(
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onOpenCompiler: () -> Unit,
    viewModel: GraphViewModel = hiltViewModel(),
) {
    // Open with direct links only (1 hop); the top-bar toggle widens to 2.
    LaunchedEffect(Unit) { viewModel.setFocusHops(1) }
    val hops by viewModel.focusHops.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Note connections", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Nearest-connections reach: 1 hop (direct links) ⇄ 2 hops (their links too).
                    FilledTonalButton(
                        onClick = { viewModel.setFocusHops(if (hops == 2) 1 else 2) },
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        // Label reads "1 hop" during the initial frame too (hops is null until
                        // the LaunchedEffect sets it).
                        Text(if (hops == 2) "2 hops" else "1 hop")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // LocalPageActive defaults to true outside the pager, so GraphScreen runs live here.
            GraphScreen(
                viewModel = viewModel,
                onOpenNote = onOpenNote,
                onOpenCompiler = onOpenCompiler,
            )
        }
    }
}
