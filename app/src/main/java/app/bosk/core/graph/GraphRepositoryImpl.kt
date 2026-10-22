package app.bosk.core.graph

import app.bosk.core.cache.SessionCache
import app.bosk.core.data.dao.GraphDao
import app.bosk.core.data.dao.NodePosition
import app.bosk.core.data.entity.EdgeEntity
import app.bosk.core.data.entity.NodeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sin

@Singleton
class GraphRepositoryImpl @Inject constructor(
    private val dao: GraphDao,
    private val cache: SessionCache,
) : GraphRepository {

    override fun observeConnectionCount(noteId: Long): Flow<Int> =
        dao.observeConnectionCountForNote(noteId)

    override fun observeGraph(): Flow<GraphSnapshot> =
        combine(dao.observeNodes(), dao.observeEdges()) { nodes, edges ->
            // Node size derives from its degree (edge count) — computed in-memory, no N queries.
            val degree = HashMap<Long, Int>()
            edges.forEach {
                degree[it.sourceNodeId] = (degree[it.sourceNodeId] ?: 0) + 1
                degree[it.targetNodeId] = (degree[it.targetNodeId] ?: 0) + 1
            }
            GraphSnapshot(
                nodes = nodes.map { it.toNode(degree[it.id] ?: 0) },
                edges = edges.map { it.toEdge() },
            )
        }.onEach { cache.putGraph(it) }

    override suspend fun ensureNodeForNote(noteId: Long, label: String): Long {
        val clean = label.ifBlank { "Untitled" }.take(40)
        dao.nodeForNote(noteId)?.let { existing ->
            // Keep the node's label in sync with the note's title so the graph updates live.
            if (existing.label != clean) dao.upsertNode(existing.copy(label = clean))
            return existing.id
        }
        // Lay new nodes out on a loose spiral so they don't all stack at the origin.
        val count = dao.nodeCount()
        val angle = count * 0.5
        val radius = 120f + count * 18f
        return dao.upsertNode(
            NodeEntity(
                noteId = noteId,
                label = label.ifBlank { "Untitled" }.take(40),
                positionX = (radius * cos(angle)).toFloat(),
                positionY = (radius * sin(angle)).toFloat(),
            ),
        )
    }

    override suspend fun addStandaloneNode(label: String): Long {
        // Same loose-spiral placement as note nodes so it doesn't stack at the origin.
        val count = dao.nodeCount()
        val angle = count * 0.5
        val radius = 120f + count * 18f
        return dao.upsertNode(
            NodeEntity(
                noteId = null,
                label = label.trim().ifBlank { "Untitled" }.take(40),
                positionX = (radius * cos(angle)).toFloat(),
                positionY = (radius * sin(angle)).toFloat(),
            ),
        )
    }

    override suspend fun ensureConceptNode(label: String): Long {
        val clean = label.trim().ifBlank { "Untitled" }.take(40)
        dao.nodeByLabel(clean)?.let { return it.id }
        return addStandaloneNode(clean)
    }

    override suspend fun addEdge(
        sourceId: Long,
        targetId: Long,
        label: String?,
        strength: Float,
        aiGenerated: Boolean,
    ): Long = dao.upsertEdge(
        EdgeEntity(
            sourceNodeId = sourceId,
            targetNodeId = targetId,
            relationshipLabel = label,
            strength = strength.coerceIn(0f, 1f),
            aiGenerated = aiGenerated,
        ),
    )

    override suspend fun clearWikiLinksFrom(sourceNodeId: Long) {
        dao.clearWikiLinksFrom(sourceNodeId)
    }

    override suspend fun addWikiLinkEdge(sourceId: Long, targetId: Long): Long = dao.upsertEdge(
        EdgeEntity(
            sourceNodeId = sourceId,
            targetNodeId = targetId,
            relationshipLabel = "links to",
            strength = 1f,
            aiGenerated = false,
            wikiLink = true,
        ),
    )

    override suspend fun confirmEdge(edgeId: Long) {
        dao.edgeById(edgeId)?.let { dao.upsertEdge(it.copy(aiGenerated = false)) }
    }

    override suspend fun deleteEdge(edgeId: Long) {
        dao.edgeById(edgeId)?.let { dao.deleteEdge(it) }
    }

    override suspend fun deleteNode(nodeId: Long) {
        dao.nodeById(nodeId)?.let { dao.deleteNode(it) }
    }

    override suspend fun saveNodePositions(positions: Map<Long, Pair<Float, Float>>) {
        dao.updateNodePositions(positions.map { (id, p) -> NodePosition(id, p.first, p.second) })
    }
}

private fun NodeEntity.toNode(degree: Int) = GraphNode(
    id = id,
    label = label,
    noteId = noteId,
    x = positionX,
    y = positionY,
    size = 1f + degree, // renderer scales this to a radius
)

private fun EdgeEntity.toEdge() = GraphEdge(
    id = id,
    sourceId = sourceNodeId,
    targetId = targetNodeId,
    label = relationshipLabel,
    strength = strength,
    aiGenerated = aiGenerated,
    wikiLink = wikiLink,
)
