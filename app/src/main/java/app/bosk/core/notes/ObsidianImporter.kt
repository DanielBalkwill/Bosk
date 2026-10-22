package app.bosk.core.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports an Obsidian vault (a folder picked via SAF). Every `.md` file becomes a note (title =
 * filename, body = markdown — `[[wiki-links]]` and `![[embeds]]` carry over), and image files are
 * copied into [ImageStore] so embeds resolve. Run "Load graph" afterwards to draw all the links.
 */
@Singleton
class ObsidianImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notes: NotesRepository,
    private val images: ImageStore,
) {
    /** Returns the number of notes created. Throws if the folder can't be read (the caller
     *  surfaces the message) — silent failure used to look identical to "empty vault". */
    suspend fun import(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        // Persist the read grant so the import survives a config change / process nudge mid-walk
        // (a dropped grant throws SecurityException, which previously looked like "0 notes").
        runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Couldn't open that folder.")
        var created = 0
        val stack = ArrayDeque<DocumentFile>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            dir.listFiles().forEach { f ->
                val name = f.name
                when {
                    f.isDirectory -> {
                        // Skip Obsidian's config/trash dirs — nothing to import there.
                        if (name?.startsWith(".") != true) stack.add(f)
                    }
                    name == null -> Unit
                    name.endsWith(".md", ignoreCase = true) || name.endsWith(".markdown", ignoreCase = true) -> {
                        val title = name.substringBeforeLast('.').trim().ifBlank { "Untitled" }
                        val body = readText(f.uri)
                        notes.upsert(Note(title = title, body = body))
                        created++
                    }
                    images.isImageName(name) -> images.importUri(f.uri, name)
                }
            }
        }
        created
    }

    private fun readText(uri: Uri): String =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
        }.getOrNull().orEmpty()
}
