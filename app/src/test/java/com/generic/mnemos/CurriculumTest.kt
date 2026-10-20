package com.generic.mnemos

import com.generic.mnemos.core.curriculum.Curriculum
import com.generic.mnemos.core.curriculum.SurfaceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurriculumTest {

    private val due = listOf(SurfaceItem.ReviewCard(1, "f", "b"))
    private val orphan = listOf(SurfaceItem.ConnectOrphan(2, "lonely"))
    private val recent = listOf(SurfaceItem.NoteSummary(3, "title"))

    @Test
    fun prioritises_dueCards_then_orphans_then_recent() {
        val ordered = Curriculum.order(due, orphan, recent, limit = 10)
        assertTrue(ordered[0] is SurfaceItem.ReviewCard)
        assertTrue(ordered[1] is SurfaceItem.ConnectOrphan)
        assertTrue(ordered[2] is SurfaceItem.NoteSummary)
    }

    @Test
    fun respectsLimit() {
        val ordered = Curriculum.order(due, orphan, recent, limit = 2)
        assertEquals(2, ordered.size)
    }

    @Test
    fun emptyInputs_giveEmpty() {
        assertTrue(Curriculum.order(emptyList(), emptyList(), emptyList(), 10).isEmpty())
    }
}
