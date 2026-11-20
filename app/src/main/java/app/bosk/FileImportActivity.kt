package app.bosk

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.bosk.core.notes.Note
import app.bosk.core.notes.NotesRepository
import app.bosk.core.review.AnkiImporter
import app.bosk.core.review.ReviewRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The "Open with Bosk" target: tap a file in a file manager (or any app's open-with sheet) and it
 * imports straight into the vault —
 *  - Markdown / text  -> a note (title from the file name),
 *  - an Anki `.apkg` (a zip) -> a deck of flashcards,
 *  - a term/definition text export (e.g. from Quizlet) -> a deck of flashcards.
 *
 * Translucent and finish-immediately, like [ShareReceiverActivity]; the only UI is a toast. All I/O
 * runs off the main thread.
 */
@AndroidEntryPoint
class FileImportActivity : ComponentActivity() {

    @Inject lateinit var notes: NotesRepository
    @Inject lateinit var anki: AnkiImporter
    @Inject lateinit var reviews: ReviewRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching { importFrom(uri) }.getOrElse { it.message ?: "Bosk couldn't open that file." }
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private suspend fun importFrom(uri: Uri): String {
        val name = displayName(uri)
        val lower = name.lowercase()
        return when {
            lower.endsWith(".apkg") || isZip(uri) -> importDeck(uri)
            lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".mdown") -> importNote(uri, name)
            lower.endsWith(".txt") || lower.endsWith(".csv") || lower.endsWith(".tsv") -> importCardsOrNote(uri, name)
            else -> importNote(uri, name)
        }
    }

    /** Anki `.apkg` (or plain-text card pairs) -> one deck note per deck, cards attached. */
    private suspend fun importDeck(uri: Uri): String {
        val decks = anki.import(uri).getOrElse { return it.message ?: "Bosk couldn't read that deck." }
        var total = 0
        decks.forEach { deck ->
            val holder = ensureDeckNote(deck.name)
            deck.cards.forEach { reviews.addCard(holder, it.front, it.back, aiGenerated = false); total++ }
        }
        return if (total == 0) "No cards found in that file." else "Imported $total card${plural(total)} into Bosk."
    }

    /** A text export (Quizlet / Anki) -> cards if it parses into pairs, otherwise a plain note. */
    private suspend fun importCardsOrNote(uri: Uri, name: String): String {
        val decks = anki.import(uri).getOrNull()
        val total = decks?.sumOf { it.cards.size } ?: 0
        if (total == 0) return importNote(uri, name)
        decks!!.forEach { deck ->
            val holder = ensureDeckNote(deck.name)
            deck.cards.forEach { reviews.addCard(holder, it.front, it.back, aiGenerated = false) }
        }
        return "Imported $total card${plural(total)} into Bosk."
    }

    /** Markdown / text -> a single note (title from the file name, else its first line). */
    private suspend fun importNote(uri: Uri, name: String): String {
        val text = contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            ?: return "Bosk couldn't read that file."
        if (text.isBlank()) return "That file was empty."
        val title = name.substringBeforeLast('.').trim().ifBlank { text.substringBefore('\n').take(60) }
        notes.upsert(Note(title = title, body = text, tags = listOf("imported")))
        return "Imported “$title” into Bosk."
    }

    private suspend fun ensureDeckNote(deckName: String): Long {
        val title = deckName.ifBlank { "Imported deck" }
        notes.search(title).firstOrNull { it.title == title }?.let { return it.id }
        return notes.upsert(Note(title = title, body = "Flashcards imported into Bosk."))
    }

    private fun displayName(uri: Uri): String {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) return n
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    /** Peek the first two bytes for the ZIP signature ("PK") — an Anki .apkg is a zip. */
    private fun isZip(uri: Uri): Boolean = runCatching {
        contentResolver.openInputStream(uri)?.use { s ->
            val sig = ByteArray(2)
            s.read(sig) == 2 && sig[0] == 'P'.code.toByte() && sig[1] == 'K'.code.toByte()
        } ?: false
    }.getOrDefault(false)

    private fun plural(n: Int) = if (n == 1) "" else "s"
}
