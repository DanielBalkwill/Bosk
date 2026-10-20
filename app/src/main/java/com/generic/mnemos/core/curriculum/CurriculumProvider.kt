package com.generic.mnemos.core.curriculum

import com.generic.mnemos.core.data.dao.GraphDao
import com.generic.mnemos.core.data.dao.ReviewDao
import com.generic.mnemos.core.notes.NotesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** One thing for the ambient daemon to surface next. */
sealed interface SurfaceItem {
    data class ReviewCard(val reviewId: Long, val front: String, val back: String) : SurfaceItem
    data class NoteSummary(val noteId: Long, val title: String) : SurfaceItem
    data class ConnectOrphan(val nodeId: Long, val label: String) : SurfaceItem
    data object Nothing : SurfaceItem
}

interface CurriculumProvider {
    /** The single best item to surface now. */
    suspend fun next(): SurfaceItem

    /** An ordered batch (the curriculum) — used by the Review/Home surfaces. */
    suspend fun nextBatch(limit: Int = 10): List<SurfaceItem>
}

/**
 * Pure ordering of the curriculum (global rule #7 — the graph IS the curriculum). Kept
 * side-effect-free so the priority logic is unit-testable.
 *
 * Spec priority: overdue review cards → orphan nodes (knowledge gaps) → recent notes to
 * revisit. (High-connectivity ranking + AI synthesis moments are a later enhancement.)
 */
object Curriculum {
    fun order(
        due: List<SurfaceItem.ReviewCard>,
        orphans: List<SurfaceItem.ConnectOrphan>,
        recent: List<SurfaceItem.NoteSummary>,
        limit: Int,
    ): List<SurfaceItem> = (due + orphans + recent).take(limit)
}

@Singleton
class CurriculumProviderImpl @Inject constructor(
    private val reviews: ReviewDao,
    private val notes: NotesRepository,
    private val graph: GraphDao,
) : CurriculumProvider {

    override suspend fun next(): SurfaceItem = nextBatch(1).firstOrNull() ?: SurfaceItem.Nothing

    override suspend fun nextBatch(limit: Int): List<SurfaceItem> {
        val now = System.currentTimeMillis()
        val due = reviews.due(now).map { SurfaceItem.ReviewCard(it.id, it.front, it.back) }
        val orphans = graph.orphanNodes().map { SurfaceItem.ConnectOrphan(it.id, it.label) }
        val recent = notes.observeNotes().first().map { SurfaceItem.NoteSummary(it.id, it.title) }
        return Curriculum.order(due, orphans, recent, limit)
    }
}
