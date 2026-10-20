package com.generic.mnemos.core.review

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports flashcards — grouped into decks — from Anki exports. Two shapes are accepted:
 *  - **`.apkg`** — a ZIP whose `collection.anki2` / `collection.anki21` is a SQLite database.
 *    Cards (`cards.did` → deck) join to notes (`notes.flds`, fields separated by 0x1f: field 0
 *    is the front, the rest the back). Deck names come from the `decks` table or the legacy
 *    `col.decks` JSON. HTML is stripped to plain text.
 *  - **plain text** (`.txt` / `.tsv` / `.csv`) — one card per line, `front <tab|comma> back`,
 *    placed in a single "Imported deck".
 *
 * Anki's newest format (`collection.anki21b`, zstd-compressed) is not supported — the user is
 * told to re-export ticking "Support older Anki versions".
 */
@Singleton
class AnkiImporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Card(val front: String, val back: String)
    data class Deck(val name: String, val cards: List<Card>)

    suspend fun import(uri: Uri): Result<List<Deck>> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openInputStream(uri)
                ?: error("Couldn't open that file.")
            BufferedInputStream(stream).use { bis ->
                if (isZip(bis)) parseApkg(bis) else parseText(bis.reader().readText())
            }
        }
    }

    /** Peek the first two bytes for the ZIP signature ("PK") without consuming the stream. */
    private fun isZip(bis: BufferedInputStream): Boolean {
        bis.mark(4)
        val sig = ByteArray(2)
        val read = bis.read(sig)
        bis.reset()
        return read == 2 && sig[0] == 'P'.code.toByte() && sig[1] == 'K'.code.toByte()
    }

    private fun parseApkg(bis: InputStream): List<Deck> {
        var legacyDb: File? = null // collection.anki2
        var modernDb: File? = null // collection.anki21
        var sawCompressed = false

        ZipInputStream(bis).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "collection.anki21b" -> sawCompressed = true
                    "collection.anki21" -> modernDb = extractTo(zip, "anki_import_21.db")
                    "collection.anki2" -> legacyDb = extractTo(zip, "anki_import_2.db")
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val db = modernDb ?: legacyDb ?: run {
            if (sawCompressed) {
                error("This deck uses Anki's newest format. In Anki, re-export and tick \"Support older Anki versions\".")
            } else {
                error("No Anki collection was found in that file.")
            }
        }
        return try {
            readDecks(db)
        } finally {
            db.delete()
        }
    }

    private fun extractTo(zip: ZipInputStream, name: String): File {
        val out = File(context.cacheDir, name)
        out.outputStream().use { zip.copyTo(it) }
        return out
    }

    private fun readDecks(db: File): List<Deck> {
        SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            val deckNames = readDeckNames(sqlite)
            // Group note fields by deck id via the cards→notes join.
            val grouped = LinkedHashMap<Long, MutableList<Card>>()
            val hasCards = tableExists(sqlite, "cards")
            if (hasCards) {
                sqlite.rawQuery(
                    "SELECT c.did AS did, n.flds AS flds FROM cards c JOIN notes n ON n.id = c.nid",
                    null,
                ).use { c ->
                    val didIdx = c.getColumnIndexOrThrow("did")
                    val fldsIdx = c.getColumnIndexOrThrow("flds")
                    while (c.moveToNext()) {
                        val card = cardFromFields(c.getString(fldsIdx)) ?: continue
                        grouped.getOrPut(c.getLong(didIdx)) { mutableListOf() }.add(card)
                    }
                }
            } else {
                // No cards table — fall back to flat notes in one deck.
                sqlite.rawQuery("SELECT flds FROM notes", null).use { c ->
                    while (c.moveToNext()) {
                        cardFromFields(c.getString(0))?.let { grouped.getOrPut(0L) { mutableListOf() }.add(it) }
                    }
                }
            }
            return grouped.entries
                .map { (did, cards) -> Deck(deckNames[did] ?: "Imported deck", cards) }
                .filter { it.cards.isNotEmpty() }
        }
    }

    /** Deck id → name, from the `decks` table (newer) or the `col.decks` JSON (legacy). */
    private fun readDeckNames(db: SQLiteDatabase): Map<Long, String> {
        val out = HashMap<Long, String>()
        if (tableExists(db, "decks")) {
            db.rawQuery("SELECT id, name FROM decks", null).use { c ->
                while (c.moveToNext()) out[c.getLong(0)] = cleanDeckName(c.getString(1))
            }
        } else if (tableExists(db, "col")) {
            runCatching {
                db.rawQuery("SELECT decks FROM col LIMIT 1", null).use { c ->
                    if (c.moveToFirst()) {
                        val json = JSONObject(c.getString(0) ?: "{}")
                        json.keys().forEach { key ->
                            val name = json.getJSONObject(key).optString("name", "Imported deck")
                            out[key.toLongOrNull() ?: return@forEach] = cleanDeckName(name)
                        }
                    }
                }
            }
        }
        return out
    }

    private fun tableExists(db: SQLiteDatabase, name: String): Boolean =
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name)).use {
            it.moveToFirst()
        }

    private fun cardFromFields(flds: String?): Card? {
        if (flds == null) return null
        val parts = flds.split('') // Anki joins fields with the 0x1f unit separator
        val front = clean(parts.getOrNull(0).orEmpty())
        val back = clean(parts.drop(1).joinToString("\n"))
        return if (front.isBlank() || back.isBlank()) null else Card(front, back)
    }

    private fun parseText(text: String): List<Deck> {
        val cards = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = if ('\t' in line) line.split('\t', limit = 2) else line.split(',', limit = 2)
                if (parts.size < 2) return@mapNotNull null
                val front = clean(parts[0])
                val back = clean(parts[1])
                if (front.isBlank() || back.isBlank()) null else Card(front, back)
            }
            .toList()
        return if (cards.isEmpty()) emptyList() else listOf(Deck("Imported deck", cards))
    }

    /** Anki subdecks use "Parent::Child"; keep just the leaf for a tidy title. */
    private fun cleanDeckName(raw: String?): String =
        raw?.substringAfterLast("::")?.trim()?.ifBlank { "Imported deck" } ?: "Imported deck"

    /** Strip Anki/HTML markup down to readable plain text. */
    private fun clean(raw: String): String =
        raw
            .replace(Regex("\\[sound:[^]]*]"), " ") // [sound:foo.mp3]
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]*>"), " ") // remaining tags
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("[ \\t]+"), " ")
            .trim()
}
