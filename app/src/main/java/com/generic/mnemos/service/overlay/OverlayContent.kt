package com.generic.mnemos.service.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.generic.mnemos.core.srs.Rating
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The Authenticator-style floating card. Shows the surfaced item, takes a free-text
 * command, and streams Gemini's reply inline.
 */
@Composable
fun OverlayContent(
    title: String,
    body: String,
    handler: OverlayCommandHandler,
    onDismiss: () -> Unit,
    /** Non-null when the surfaced item is a due card → interactive quick-quiz controls. */
    quiz: OverlayQuiz? = null,
    onRate: (Long, com.generic.mnemos.core.srs.Rating) -> Unit = { _, _ -> },
) {
    var command by remember { mutableStateOf("") }
    var response by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun send() {
        val cmd = command.trim()
        if (cmd.isEmpty()) return
        if (handler.isDismiss(cmd)) { onDismiss(); return }
        command = ""
        response = ""
        loading = true
        scope.launch {
            try {
                val sys = handler.systemContext()
                handler.stream(cmd, title, body, sys).collect { chunk -> response += chunk }
            } catch (e: Exception) {
                response = e.message ?: "Couldn't reach Gemini. Is your API key set?"
            } finally {
                loading = false
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(12.dp), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Dismiss")
                }
            }
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            // Quick quiz: reveal the answer, then rate yourself — a real FSRS review recorded
            // right from the overlay, without ever opening the app.
            if (quiz != null) {
                var revealed by remember { mutableStateOf(false) }
                if (!revealed) {
                    TextButton(onClick = { revealed = true }, modifier = Modifier.padding(top = 6.dp)) {
                        Text("Show answer")
                    }
                } else {
                    Text(
                        quiz.back,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onRate(quiz.reviewId, Rating.AGAIN); onDismiss() },
                            modifier = Modifier.weight(1f),
                        ) { Text("Again") }
                        OutlinedButton(
                            onClick = { onRate(quiz.reviewId, Rating.GOOD); onDismiss() },
                            modifier = Modifier.weight(1f),
                        ) { Text("Good") }
                        Button(
                            onClick = { onRate(quiz.reviewId, Rating.EASY); onDismiss() },
                            modifier = Modifier.weight(1f),
                        ) { Text("Easy") }
                    }
                }
            }

            if (loading || response.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (loading && response.isEmpty()) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(2.dp))
                    }
                    Text(
                        response.ifEmpty { "Thinking…" },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask or command… (e.g. \"explain more\", \"skip\")") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { send() }),
                )
                IconButton(onClick = { send() }) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send")
                }
            }
        }
    }
}
