package com.generic.mnemos.ui.screens.queue

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.generic.mnemos.core.queue.QueueItem
import com.generic.mnemos.core.queue.QueueKind
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(onBack: () -> Unit = {}, viewModel: QueueViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Queue") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "Add to queue")
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "Your queue is empty. Add tasks, alarms or events with +.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    QueueRow(
                        item = item,
                        onToggleDone = { viewModel.toggleDone(item.id) },
                        onUp = { viewModel.moveUp(item.id) },
                        onDown = { viewModel.moveDown(item.id) },
                        onDelete = { viewModel.remove(item.id) },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddQueueDialog(
            onDismiss = { showAdd = false },
            onAdd = { title, kind, timeMs -> viewModel.add(title, kind, timeMs); showAdd = false },
        )
    }
}

@Composable
private fun QueueRow(
    item: QueueItem,
    onToggleDone: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
) {
    androidx.compose.material3.Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = item.done, onCheckedChange = { onToggleDone() })
            Icon(
                kindIcon(item.kind),
                contentDescription = item.kind.name,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                )
                val sub = buildString {
                    append(item.kind.name.lowercase().replaceFirstChar { it.uppercase() })
                    item.timeMs?.let { append(" · ").append(formatTime(it)) }
                }
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onUp) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up") }
            IconButton(onClick = onDown) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddQueueDialog(onDismiss: () -> Unit, onAdd: (String, QueueKind, Long?) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(QueueKind.TASK) }
    var timeMs by remember { mutableStateOf<Long?>(null) }

    fun pickTime() {
        val now = Calendar.getInstance()
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
                }
                timeMs = cal.timeInMillis
            },
            now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false,
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to queue") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QueueKind.entries.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k; if (k == QueueKind.TASK) timeMs = null },
                            label = { Text(k.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                if (kind != QueueKind.TASK) {
                    TextButton(onClick = { pickTime() }, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(18.dp))
                        Text(timeMs?.let { "At ${formatTime(it)}" } ?: "Set time")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(title, kind, timeMs) },
                enabled = title.isNotBlank() && (kind == QueueKind.TASK || timeMs != null),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun kindIcon(kind: QueueKind) = when (kind) {
    QueueKind.TASK -> Icons.Outlined.TaskAlt
    QueueKind.ALARM -> Icons.Outlined.Alarm
    QueueKind.EVENT -> Icons.Outlined.Event
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(ms)
