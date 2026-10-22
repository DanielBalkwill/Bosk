package app.bosk.core.notes

import app.bosk.core.graph.GraphRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the knowledge graph in sync with note links in bodies. On each save we re-derive a
 * note's outgoing link edges from its current text (Obsidian-style: the text is the source of
 * truth), resolving link names to notes by title (case-insensitive). Two link forms count:
 * bracketed `[[Note]]` / `![[Note]]` and the bare `!Note` form (resolved against known titles).
 */
@Singleton
class WikiLinkSyncer @Inject constructor(
    private val notes: NotesRepository,
    private val graph: GraphRepository,
) {
    /** Re-derive [note]'s link edges from its body. Returns the number of links resolved. */
    suspend fun sync(note: Note): Int {
        val sourceNode = graph.ensureNodeForNote(note.id, note.title)
        graph.clearWikiLinksFrom(sourceNode)

        // Snapshot existing notes once — needed both to resolve names and to match bare `!Title`.
        val all = notes.observeNotes().first()
        val byTitle = all.associateBy { it.title.trim().lowercase() }
        val titles = all.map { it.title }

        // Bracketed links plus bare `!Title` links, de-duplicated case-insensitively.
        val names = (WikiLinks.names(note.body) + WikiLinks.bareNames(note.body, titles))
            .distinctBy { it.lowercase() }
        if (names.isEmpty()) return 0

        var linked = 0
        names.forEach { name ->
            val target = byTitle[name.trim().lowercase()]
            if (target != null && target.id == note.id) return@forEach // ignore self-links
            // Resolved → link to the note's node; unresolved → an Obsidian-style "ghost" concept
            // node, so EVERY link always produces a visible connection on the graph.
            val targetNode = if (target != null) graph.ensureNodeForNote(target.id, target.title)
            else graph.ensureConceptNode(name)
            graph.addWikiLinkEdge(sourceNode, targetNode)
            linked++
        }
        return linked
    }
}
