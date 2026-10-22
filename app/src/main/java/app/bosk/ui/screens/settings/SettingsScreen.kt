package app.bosk.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bosk.core.ai.llm.OpenAiCompatPreset
import app.bosk.core.settings.DeliveryMode
import app.bosk.ui.components.ApiKeyField

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenAiPartner: () -> Unit = {},
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val hasKey by viewModel.hasKey.collectAsStateWithLifecycle()
    val keyStatus by viewModel.keyStatus.collectAsStateWithLifecycle()
    val activeProvider by viewModel.activeProvider.collectAsStateWithLifecycle()
    val noteFontSp by viewModel.noteFontSp.collectAsStateWithLifecycle()
    var liveFont by remember(noteFontSp) { mutableStateOf(noteFontSp) }
    val commandNote by viewModel.commandNote.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result handled by the OS UI; nothing to do */ }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.displayMedium)

        Button(
            onClick = onOpenAiPartner,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Outlined.Psychology,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp).size(20.dp),
            )
            Text("AI Partner")
        }

        Section("Accessibility") {
            Text(
                "Note font size  ${liveFont.toInt()}sp",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Sets the text size in the note editor (preview below).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = liveFont,
                onValueChange = { liveFont = it },
                onValueChangeFinished = { viewModel.setNoteFontSp(liveFont) },
                valueRange = 10f..32f,
            )
            Text(
                "The quick brown fox jumps over the lazy dog.",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = liveFont.sp),
            )
        }

        Section("Command palette") {
            CommandNoteEditor(
                value = commandNote,
                onSave = viewModel::setCommandNote,
                onReset = viewModel::resetCommandNote,
            )
        }

        Section("AI provider") {
            Text(
                "Bring your own key — Gemini, OpenAI or Claude. Paste any one and Bosk detects " +
                    "which it is. Keys are stored encrypted on this device and only ever sent to " +
                    "that provider. No key? Use the free on-device helper below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            ApiKeyField(
                hasKey = hasKey,
                status = keyStatus,
                onSave = viewModel::saveAndValidateKey,
                onClear = viewModel::clearKey,
            )
            activeProvider?.let { p ->
                Text(
                    "Active: ${p.displayName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            OutlinedButton(
                onClick = viewModel::useOnDeviceAi,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Use on-device AI (free, no key)")
            }
            Text(
                "On-device runs Gemini Nano locally — fully private, zero cost. Needs a supported " +
                    "phone (Pixel 8+/Samsung S24+ class); falls back to a key elsewhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Section("Free & custom AI (OpenAI-compatible)") {
            CompatProviderSection(viewModel)
        }

        Section("Ambient companion") {
            ToggleRow("Enabled", prefs.enabled, viewModel::setEnabled)

            Text("Check interval", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            ChipRow(
                options = listOf(15, 30, 60),
                selected = prefs.intervalMinutes,
                label = { "$it min" },
                onSelect = viewModel::setIntervalMinutes,
            )

            Text("Delivery", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            ChipRow(
                options = DeliveryMode.entries,
                selected = prefs.delivery,
                label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                onSelect = viewModel::setDelivery,
            )

            Text(
                "Voice speed: ${"%.2f".format(prefs.ttsSpeed)}x",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            Slider(
                value = prefs.ttsSpeed,
                onValueChange = viewModel::setSpeed,
                valueRange = 0.75f..2.0f,
                steps = 4,
            )

            Text(
                "Quiet hours: ${prefs.quietStartHour}:00 – ${prefs.quietEndHour}:00",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stepper("Start", prefs.quietStartHour) { viewModel.setQuietHours(it, prefs.quietEndHour) }
                Stepper("End", prefs.quietEndHour) { viewModel.setQuietHours(prefs.quietStartHour, it) }
            }
        }

        Section("Permissions") {
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (viewModel.hasOverlayPermission()) "Overlay: granted ✓" else "Grant overlay permission") }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                OutlinedButton(
                    onClick = { notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Allow notifications") }
            }
        }

        Section("Try it") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::testSpeak) { Text("Speak a test") }
                OutlinedButton(onClick = viewModel::surfaceNow) { Text("Surface now") }
            }
        }
    }
}

@Composable
private fun CompatProviderSection(viewModel: SettingsViewModel) {
    val baseUrl by viewModel.compatBaseUrl.collectAsStateWithLifecycle()
    val model by viewModel.compatModel.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf("") }
    val selectedPreset = OpenAiCompatPreset.entries.firstOrNull {
        it != OpenAiCompatPreset.CUSTOM && it.baseUrl == baseUrl
    }

    Text(
        "Groq and OpenRouter give a free key with no card — just paste it in the box above and it " +
            "works. For DeepSeek or any other OpenAI-compatible endpoint, pick it here then paste its key.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OpenAiCompatPreset.entries.forEach { preset ->
            FilterChip(
                selected = preset == selectedPreset,
                onClick = { viewModel.selectCompatPreset(preset) },
                label = { Text(if (preset.free) "${preset.label} · free" else preset.label) },
            )
        }
    }

    selectedPreset?.takeIf { it.signupUrl.isNotBlank() }?.let { p ->
        Text(
            "Get a ${p.label} key: ${p.signupUrl}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    OutlinedTextField(
        value = baseUrl,
        onValueChange = viewModel::setCompatBaseUrl,
        label = { Text("Base URL") },
        placeholder = { Text("https://…/v1") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = model,
        onValueChange = viewModel::setCompatModel,
        label = { Text("Model") },
        placeholder = { Text("e.g. llama-3.3-70b-versatile") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text("API key for this provider") },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Button(
        onClick = { viewModel.saveCompatKey(key); key = "" },
        enabled = key.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank(),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text("Save & use this provider")
    }
}

@Composable
private fun CommandNoteEditor(value: String, onSave: (String) -> Unit, onReset: () -> Unit) {
    // Re-seed the editable copy whenever the stored note changes (e.g. after Reset to default).
    var draft by remember(value) { mutableStateOf(value) }
    val dirty = draft != value

    Text(
        "The slash commands available in the AI terminal's palette and the /help command. Edit, add " +
            "or document commands here — changes apply everywhere commands are listed.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp),
        label = { Text("Command note") },
        textStyle = MaterialTheme.typography.bodySmall,
    )
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = { onSave(draft) }, enabled = dirty) { Text("Save") }
        OutlinedButton(onClick = onReset) { Text("Reset to default") }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            FilterChip(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                label = { Text(label(opt)) },
            )
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { onChange((value + 23) % 24) }) { Text("–") }
        Text("$value", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onChange((value + 1) % 24) }) { Text("+") }
    }
}
