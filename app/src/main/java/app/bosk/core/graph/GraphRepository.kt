package app.bosk.core.graph

import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to the knowledge graph. The graph IS the curriculum (global rule #7),
 * so this is the shared source of truth for the renderer (CP3) and curriculum engine (CP5).
 */
interface GraphRepository {
    fun observeGraph(): Flow<GraphSnapshot>

    /** Fast, reactive count of a note's connections (a quick "has connections?" check). */
    fun observeConnectionCount(noteId: Long): Flow<Int>

    /** Ensure a node exists for [noteId]; returns its id (existing or newly created). */
    suspend fun ensureNodeForNote(noteId: Long, label: String): Long

    /** Create a standalone concept node (not backed by a note); returns its id. */
    suspend fun addStandaloneNode(label: String): Long

    /** Find-or-create a concept node by [label] (for unresolved `[[link]]`s — Obsidian ghosts). */
    suspend fun ensureConceptNode(label: String): Long

    suspend fun addEdge(
        sourceId: Long,
        targetId: Long,
        label: String?,
        strength: Float,
        aiGenerated: Boolean,
    ): Long

    /** Remove all `[[wiki-link]]` edges leaving [sourceNodeId] (before re-deriving from text). */
    suspend fun clearWikiLinksFrom(sourceNodeId: Long)

    /** Add a solid, user-authored `[[wiki-link]]` edge. */
    suspend fun addWikiLinkEdge(sourceId: Long, targetId: Long): Long

    /** Promote an AI-suggested edge to a confirmed one. */
    suspend fun confirmEdge(edgeId: Long)

    suspend fun deleteEdge(edgeId: Long)
    suspend fun deleteNode(nodeId: Long)

    /** Persist laid-out node positions so reopening the graph restores them (minimal re-animation). */
    suspend fun saveNodePositions(positions: Map<Long, Pair<Float, Float>>)
}
