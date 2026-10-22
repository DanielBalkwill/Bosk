package app.bosk.core.notes

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Vault export — the trust feature: every live note leaves as plain Markdown in a single zip
 * (tags preserved as Obsidian-style frontmatter, embedded images bundled under images/), so the
 * user's knowledge is never hostage to the app. Round-trips with [ObsidianImporter].
 */
@Singleton
class VaultExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notes: NotesRepository,
    private val imageStore: ImageStore,
) {

    /** Write the whole vault into a zip at [target] (a user-picked SAF Uri). Returns note count. */
    suspend fun exportTo(target: Uri): Int = withContext(Dispatchers.IO) {
        val all = notes.observeNotes().first()
        if (all.isEmpty()) throw IOException("Nothing to export yet.")
        val out = context.contentResolver.openOutputStream(target)
            ?: throw IOException("Couldn't open the destination file.")
        val usedNames = HashSet<String>()
        val imageNames = LinkedHashSet<String>()
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            all.forEach { n ->
                val base = sanitize(n.title.ifBlank { "Untitled" })
                var name = base
                var i = 2
                while (!usedNames.add(name.lowercase())) { name = "$base ($i)"; i++ }
                zip.putNextEntry(ZipEntry("$name.md"))
                val frontmatter =
                    if (n.tags.isEmpty()) ""
                    else "---\ntags: [${n.tags.joinToString(", ")}]\n---\n\n"
                zip.write((frontmatter + n.body).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                // Collect embedded images (![[name]]) so they travel with the notes.
                WikiLinks.refs(n.body).forEach { ref ->
                    if (ref.embed && imageStore.isImageName(ref.name)) imageNames.add(ref.name)
                }
            }
            imageNames.forEach { imgName ->
                val file = imageStore.fileFor(imgName)
                if (file.exists()) {
                    zip.putNextEntry(ZipEntry("images/${sanitize(imgName)}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        all.size
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(80).ifBlank { "Untitled" }
}
