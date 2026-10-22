package app.bosk.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.bosk.core.data.entity.EdgeEntity
import app.bosk.core.data.entity.NodeEntity
import kotlinx.coroutines.flow.Flow

/** A node's persisted layout position (written back when the graph settles). */
data class NodePosition(val id: Long, val x: Float, val y: Float)

/** Nodes + edges share a DAO since the graph is read/written as a unit. */
@Dao
interface GraphDao {
    @Query("SELECT * FROM nodes")
    fun observeNodes(): Flow<List<NodeEntity>>

    @Query("SELECT * FROM edges")
    fun observeEdges(): Flow<List<EdgeEntity>>

    @Upsert
    suspend fun upsertNode(node: NodeEntity): Long

    @Query("UPDATE nodes SET positionX = :x, positionY = :y WHERE id = :id")
    suspend fun updateNodePosition(id: Long, x: Float, y: Float)

    /** Persist many positions in one transaction (called when the layout settles). */
    @Transaction
    suspend fun updateNodePositions(positions: List<NodePosition>) {
        positions.forEach { updateNodePosition(it.id, it.x, it.y) }
    }

    @Upsert
    suspend fun upsertEdge(edge: EdgeEntity): Long

    @Delete
    suspend fun deleteNode(node: NodeEntity)

    @Delete
    suspend fun deleteEdge(edge: EdgeEntity)

    @Query("SELECT * FROM nodes WHERE noteId = :noteId LIMIT 1")
    suspend fun nodeForNote(noteId: Long): NodeEntity?

    @Query("SELECT * FROM nodes WHERE id = :id")
    suspend fun nodeById(id: Long): NodeEntity?

    /** First node with this exact label — used to reuse a concept ("ghost") node for a link. */
    @Query("SELECT * FROM nodes WHERE label = :label LIMIT 1")
    suspend fun nodeByLabel(label: String): NodeEntity?

    @Query("SELECT * FROM edges WHERE id = :id")
    suspend fun edgeById(id: Long): EdgeEntity?

    /** All wiki-link edges originating from [sourceId] (so we can reconcile against the text). */
    @Query("DELETE FROM edges WHERE sourceNodeId = :sourceId AND wikiLink = 1")
    suspend fun clearWikiLinksFrom(sourceId: Long)

    @Query("SELECT COUNT(*) FROM nodes")
    suspend fun nodeCount(): Int

    // Edge count per node — used to size nodes in the renderer (CP3).
    @Query("SELECT COUNT(*) FROM edges WHERE sourceNodeId = :nodeId OR targetNodeId = :nodeId")
    suspend fun edgeCount(nodeId: Long): Int

    /**
     * Fast, reactive "does this note have connections" — one indexed query, no graph walk.
     * Counts edges touching any node mapped to [noteId].
     */
    @Query(
        """
        SELECT COUNT(*) FROM edges
        WHERE sourceNodeId IN (SELECT id FROM nodes WHERE noteId = :noteId)
           OR targetNodeId IN (SELECT id FROM nodes WHERE noteId = :noteId)
        """,
    )
    fun observeConnectionCountForNote(noteId: Long): Flow<Int>

    // Orphan nodes (no edges) — knowledge gaps surfaced by the curriculum engine (CP5).
    @Query(
        """
        SELECT * FROM nodes
        WHERE id NOT IN (SELECT sourceNodeId FROM edges)
          AND id NOT IN (SELECT targetNodeId FROM edges)
        """,
    )
    suspend fun orphanNodes(): List<NodeEntity>
}
