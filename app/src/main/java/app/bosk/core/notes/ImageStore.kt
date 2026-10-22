package app.bosk.core.notes

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device store for images embedded in notes (Obsidian-style `![[image.png]]`). Images live in
 * `filesDir/note_images/` keyed by filename, so an embed resolves by the name written in the body.
 */
@Singleton
class ImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir = File(context.filesDir, "note_images").apply { if (!exists()) mkdirs() }

    fun fileFor(name: String): File = File(dir, name)

    fun isImageName(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in IMAGE_EXTS

    /** Copy [uri] into the store, returning the stored file name (its original name, sanitised). */
    fun importUri(uri: Uri, suggestedName: String?): String? {
        val raw = suggestedName ?: queryName(uri) ?: "image_${System.currentTimeMillis()}.png"
        val name = sanitize(raw)
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                fileFor(name).outputStream().use { input.copyTo(it) }
            }
            name
        }.getOrNull()
    }

    private fun queryName(uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

    private fun sanitize(name: String): String =
        name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "image.png" }

    companion object {
        val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
    }
}
