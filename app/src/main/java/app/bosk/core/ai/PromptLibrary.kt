package app.bosk.core.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** The prompt templates, packaged as editable .md assets under assets/prompts/. */
enum class PromptTemplate(val asset: String) {
    SUMMARISE_NOTE("prompts/summarise_note.md"),
    CONNECT_NODES("prompts/connect_nodes.md"),
    GENERATE_CARD("prompts/generate_card.md"),
    EXPLAIN_CONCEPT("prompts/explain_concept.md"),
    CURRICULUM_NEXT("prompts/curriculum_next.md"),
}

/**
 * Loads prompt templates from assets (cached) and fills {{placeholders}}.
 *
 * Deviation note: the master spec lists these under /core/ai/prompts/. On Android, files
 * that must be readable at runtime live in assets/, so they sit in assets/prompts/ and are
 * loaded here. They remain plain .md files, easy to iterate on.
 */
@Singleton
class PromptLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cache = ConcurrentHashMap<PromptTemplate, String>()
    private val loadMutex = Mutex()

    suspend fun render(template: PromptTemplate, vars: Map<String, String>): String {
        val raw = load(template)
        return vars.entries.fold(raw) { acc, (k, v) -> acc.replace("{{$k}}", v) }
    }

    private suspend fun load(template: PromptTemplate): String {
        cache[template]?.let { return it }
        return loadMutex.withLock {
            cache[template] ?: withContext(Dispatchers.IO) {
                context.assets.open(template.asset).bufferedReader().use { it.readText() }
            }.also { cache[template] = it }
        }
    }
}
