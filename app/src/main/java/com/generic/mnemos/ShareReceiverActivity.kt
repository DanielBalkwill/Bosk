package com.generic.mnemos

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.generic.mnemos.core.notes.Note
import com.generic.mnemos.core.notes.NotesRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * The share-sheet capture target: share text or a URL from ANY app and it lands in the vault as a
 * note, no questions asked. Translucent + finishes immediately — the only UI is a toast. Title
 * comes from the share subject (or the first line), and captures are tagged #captured so they're
 * easy to find and process later.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject lateinit var notes: NotesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isBlank()) {
            finish()
            return
        }
        val subject = intent?.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        val title = subject.ifBlank { text.substringBefore('\n').take(60) }
        lifecycleScope.launch {
            val saved = runCatching {
                notes.upsert(Note(title = title, body = text, tags = listOf("captured")))
            }.isSuccess
            Toast.makeText(
                applicationContext,
                if (saved) "Saved to Mnemos" else "Mnemos couldn't save that",
                Toast.LENGTH_SHORT,
            ).show()
            finish()
        }
    }
}
