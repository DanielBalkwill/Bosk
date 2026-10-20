package com.generic.mnemos.ui.screens.aipartner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.generic.mnemos.core.ai.PersonaStyle
import com.generic.mnemos.core.ai.Verbosity

/**
 * The AI Partner — customise how Mnemos's AI talks to you. The chosen persona is merged into the
 * system instruction of EVERY AI call (tutor, terminal, note AI, Home, ambient), so this one screen
 * governs the whole app's voice. Reached from the top of Settings.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiPartnerScreen(
    onBack: () -> Unit = {},
    viewModel: AiPartnerViewModel = hiltViewModel(),
) {
    val persona by viewModel.persona.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val previewBusy by viewModel.previewBusy.collectAsStateWithLifecycle()

    // Local text state so typing is smooth; re-sync if the persisted value changes underneath us.
    var name by remember { mutableStateOf(persona.name) }
    LaunchedEffect(persona.name) { if (persona.name != name) name = persona.name }
    var custom by remember { mutableStateOf(persona.customInstruction) }
    LaunchedEffect(persona.customInstruction) { if (persona.customInstruction != custom) custom = persona.customInstruction }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Partner") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.reset() }) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = "Reset to default")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "How your AI companion talks to you — applied everywhere the AI is used.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Name.
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; viewModel.setName(it) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Style.
            SectionLabel("Style")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PersonaStyle.entries.forEach { style ->
                    FilterChip(
                        selected = persona.style == style,
                        onClick = { viewModel.setStyle(style) },
                        label = { Text(style.display) },
                    )
                }
            }
            Text(
                persona.style.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Reply length.
            SectionLabel("Reply length")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Verbosity.entries.forEach { v ->
                    FilterChip(
                        selected = persona.verbosity == v,
                        onClick = { viewModel.setVerbosity(v) },
                        label = { Text(v.display) },
                    )
                }
            }

            // Custom instruction.
            SectionLabel("Custom instruction")
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it; viewModel.setCustomInstruction(it) },
                label = { Text("Extra steering (the whole prompt for the Custom style)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            // Emoji.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Allow emoji", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = persona.useEmoji, onCheckedChange = { viewModel.setUseEmoji(it) })
            }

            // Live system-prompt preview.
            SectionLabel("System prompt")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text(
                    persona.systemPrompt(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }

            // See it in action.
            FilledTonalButton(
                onClick = { viewModel.previewReply() },
                enabled = !previewBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (previewBusy) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Preview reply")
                }
            }
            if (preview.isNotBlank()) {
                Card {
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
