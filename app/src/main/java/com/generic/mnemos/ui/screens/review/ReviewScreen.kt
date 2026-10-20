package com.generic.mnemos.ui.screens.review

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.generic.mnemos.core.review.Deck
import com.generic.mnemos.core.srs.Rating
import com.generic.mnemos.ui.components.rememberHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(viewModel: ReviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val decks by viewModel.decks.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    // SAF file picker for Anki exports — */* so .apkg (no registered MIME) is selectable.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importAnki(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Practise") },
                navigationIcon = {
                    if (state.current != null) {
                        IconButton(onClick = { viewModel.exitToDecks() }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Exit to decks")
                        }
                    }
                },
                actions = {
                    // Browse & download shared decks in the user's own browser (no embedded web view).
                    IconButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://ankiweb.net/shared/decks"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = "Download decks (AnkiWeb)")
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = "Import Anki deck")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // New-card button: top-right, beneath the import action, above the flashcard.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = { showAdd = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("New card")
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f).padding(16.dp), contentAlignment = Alignment.Center) {
                when {
                state.loading -> CircularProgressIndicator()
                state.finished -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SessionSummary(
                        reviewed = state.reviewedCount,
                        retentionPct = state.retentionPct,
                        onAgain = viewModel::load,
                    )
                    DeckList(decks = decks, onStudy = viewModel::studyDeck)
                }
                else -> {
                    val card = state.current
                    if (card != null) {
                        ReviewCardView(
                            front = card.front,
                            back = card.back,
                            showBack = state.showBack,
                            progress = "${state.index + 1} / ${state.cards.size}",
                            onReveal = viewModel::flip,
                            onRate = viewModel::rate,
                        )
                    }
                }
                }
            }
        }
    }

    if (showAdd) {
        AddCardDialog(
            onDismiss = { showAdd = false },
            onSave = { f, b -> viewModel.addManualCard(f, b); showAdd = false },
        )
    }
}

@Composable
private fun ReviewCardView(
    front: String,
    back: String,
    showBack: Boolean,
    progress: String,
    onReveal: () -> Unit,
    onRate: (Rating) -> Unit,
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(progress, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 16.dp).clickable(enabled = !showBack, onClick = onReveal),
        ) {
            Crossfade(targetState = showBack, label = "flip") { revealed ->
                Column(
                    Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(front, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    if (revealed) {
                        Text(
                            back,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 20.dp),
                        )
                    } else {
                        Text(
                            "Tap to reveal",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 20.dp),
                        )
                    }
                }
            }
        }

        if (showBack) {
            // Rating a card gets a solid haptic so the grade registers without looking up.
            val haptics = rememberHaptics()
            val rate = { r: Rating -> haptics.confirm(); onRate(r) }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // "Again" is a compact rewind icon, freeing width so the three labels fit cleanly.
                OutlinedIconButton(onClick = { rate(Rating.AGAIN) }) {
                    Icon(Icons.Filled.Replay, contentDescription = "Again")
                }
                RatingButton("Hard", Modifier.weight(1f)) { rate(Rating.HARD) }
                RatingButton("Good", Modifier.weight(1f)) { rate(Rating.GOOD) }
                RatingButton("Easy", Modifier.weight(1f)) { rate(Rating.EASY) }
            }
        }
    }
}

@Composable
private fun RatingButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
    ) {
        Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SessionSummary(reviewed: Int, retentionPct: Int, onAgain: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (reviewed == 0) {
            Text("All caught up", style = MaterialTheme.typography.headlineMedium)
            Text(
                "No cards are due right now. Add one with +, or generate cards from a note.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Text("Session complete", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Reviewed $reviewed · ~$retentionPct% recalled",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(onClick = onAgain, modifier = Modifier.padding(top = 20.dp)) { Text("Check for more") }
        }
    }
}

@Composable
private fun DeckList(decks: List<Deck>, onStudy: (Long) -> Unit) {
    if (decks.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
        Text(
            "Your decks",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        decks.forEach { deck ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onStudy(deck.noteId) },
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(deck.title.ifBlank { "Untitled deck" }, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            "${deck.total} card${if (deck.total == 1) "" else "s"}" +
                                if (deck.due > 0) " · ${deck.due} due" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Study",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddCardDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New flashcard") },
        text = {
            Column {
                OutlinedTextField(front, { front = it }, label = { Text("Front (question)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(back, { back = it }, label = { Text("Back (answer)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(front, back) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
