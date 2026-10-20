package com.generic.mnemos.ui.screens.onboarding

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.generic.mnemos.ui.components.ApiKeyField
import kotlinx.coroutines.launch

private const val PAGES = 5

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val pager = rememberPagerState(pageCount = { PAGES })
    val scope = rememberCoroutineScope()
    val hasKey by viewModel.hasKey.collectAsStateWithLifecycle()
    val keyStatus by viewModel.keyStatus.collectAsStateWithLifecycle()

    fun next() = scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(PAGES - 1)) }
    fun back() = scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (page) {
                    0 -> Welcome()
                    1 -> GoogleStep()
                    2 -> ApiKeyStep(hasKey, keyStatus, viewModel)
                    3 -> FirstNoteStep(viewModel)
                    4 -> PermissionsStep()
                }
            }
        }

        // Dots
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(PAGES) { i ->
                Text(
                    if (i == pager.currentPage) "●" else "○",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { back() }, enabled = pager.currentPage > 0) { Text("Back") }
            if (pager.currentPage < PAGES - 1) {
                Button(onClick = { next() }) { Text("Next") }
            } else {
                Button(onClick = { viewModel.finish() }) { Text("Start learning") }
            }
        }
    }
}

@Composable
private fun Welcome() {
    Text("◐", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
    Text("Mnemos", style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(top = 8.dp))
    Text(
        "Learn everything. Forget nothing.",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        "Your notes become a living knowledge graph that reviews itself and reads back to you.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Composable
private fun GoogleStep() {
    Text("Sign in", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Google sign-in is optional — Mnemos works fully offline and stores everything on your device. " +
            "(Sign-in for cloud sync arrives with the paid tier.)",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun ApiKeyStep(
    hasKey: Boolean,
    keyStatus: com.generic.mnemos.ui.screens.settings.KeyStatus,
    viewModel: OnboardingViewModel,
) {
    Text("Connect your AI", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Genuinely free, no card needed: grab a key from Groq (console.groq.com) or Cerebras " +
            "— paste it here and you're done. Keys from OpenAI, Claude, Gemini, NVIDIA and " +
            "OpenRouter work too; Mnemos auto-detects which it is. Every key stays encrypted " +
            "on your device, and your notes go only to the provider you chose. You can change " +
            "this any time in Settings.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 12.dp),
    )
    ApiKeyField(
        hasKey = hasKey,
        status = keyStatus,
        onSave = viewModel::saveAndValidateKey,
        onClear = viewModel::clearKey,
    )
    OutlinedButton(
        onClick = viewModel::useOnDeviceAi,
        modifier = Modifier.padding(top = 12.dp),
    ) {
        Text("Use on-device AI (free)")
    }
}

@Composable
private fun FirstNoteStep(viewModel: OnboardingViewModel) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var added by remember { mutableStateOf(false) }

    Text("Your first note", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Add something you're learning. (Optional — you can do this later.)",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 12.dp),
    )
    OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        body, { body = it }, label = { Text("Notes (use #tags)") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Button(
        onClick = { viewModel.addFirstNote(title, body); added = true; title = ""; body = "" },
        modifier = Modifier.padding(top = 12.dp),
        enabled = title.isNotBlank() || body.isNotBlank(),
    ) { Text(if (added) "Added ✓ — add another?" else "Add note") }
}

@Composable
private fun PermissionsStep() {
    val context = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Text("Permissions", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Optional, for the ambient companion: notifications to nudge you, and overlay to pop up commands over any app.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 12.dp),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        OutlinedButton(
            onClick = { notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Allow notifications") }
    }
    OutlinedButton(
        onClick = {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
            )
        },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) { Text("Allow overlay popup") }
}
