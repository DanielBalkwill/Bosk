package com.generic.mnemos.core.graph

/**
 * In-memory graph model used by the renderer (CP3) and curriculum engine (CP5).
 * Backed by NodeEntity/EdgeEntity in Room (CP2). The graph IS the curriculum
 * (global rule #7), so this is the shared source of truth.
 */

data class GraphNode(
    val id: Long,
    val label: String,
    val noteId: Long?,
    val x: Float,
    val y: Float,
    val size: Float, // derived from edge count
)

data class GraphEdge(
    val id: Long,
    val sourceId: Long,
    val targetId: Long,
    val label: String?,
    val strength: Float,      // 0..1, drives line opacity
    val aiGenerated: Boolean, // dashed/pending colour until user confirms
    val wikiLink: Boolean = false, // solid, user-authored [[link]] connection
)

data class GraphSnapshot(
    val nodes: List<GraphNode> = emptyList(),
    val edges: List<GraphEdge> = emptyList(),
)
// TODO CP3: GraphRepository (CRUD + snapshot Flow) and connection inference.
