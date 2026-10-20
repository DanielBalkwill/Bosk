package com.generic.mnemos.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.generic.mnemos.core.ai.llm.OpenAiCompatPreset
import com.generic.mnemos.core.ai.llm.ProviderId
import com.generic.mnemos.ui.screens.settings.KeyStatus

/**
 * Multi-provider BYOK key entry + live validation. Shared by Settings and onboarding step 3.
 * Paste any key — Gemini (AIza…), OpenAI (sk-…) or Claude (sk-ant-…) — and we detect which
 * provider it is from the prefix. The key is handed straight to SecureKeyStore by the caller;
 * this widget never logs it.
 */
@Composable
fun ApiKeyField(
    hasKey: Boolean,
    status: KeyStatus,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    val detected = remember(input) { ProviderId.fromKey(input) }
    // Name the specific backend for OpenAI-compatible keys (OpenRouter / Groq).
    val detectedName = remember(input, detected) {
        when (detected) {
            ProviderId.OPENAI_COMPAT -> OpenAiCompatPreset.fromKey(input)?.let { "${it.label} (free)" }
                ?: detected.displayName
            null -> null
            else -> detected.displayName
        }
    }

    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (hasKey) "Replace API key" else "AI API key") },
            placeholder = { Text("Paste a Gemini, OpenAI or Claude key") },
            supportingText = {
                Text(
                    when {
                        input.isBlank() -> "We detect the provider automatically from the key."
                        detectedName != null -> "Detected: $detectedName"
                        else -> "Unrecognised key format."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (input.isNotBlank() && detected == null)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { onSave(input); input = "" }, enabled = input.isNotBlank()) {
                Text("Save & test")
            }
            if (hasKey) TextButton(onClick = onClear) { Text("Clear") }

            when (status) {
                is KeyStatus.Validating -> {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Text("Testing…", style = MaterialTheme.typography.bodySmall)
                }
                is KeyStatus.Valid -> Text(
                    "✓ Key works",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                is KeyStatus.Invalid -> Text(
                    status.message.take(60),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                KeyStatus.Idle -> if (hasKey) Text(
                    "Key saved",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
