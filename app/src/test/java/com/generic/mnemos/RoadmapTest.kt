package com.generic.mnemos

import com.generic.mnemos.core.ai.DailyRoadmap
import com.generic.mnemos.core.ai.RoadmapPalette
import com.generic.mnemos.core.ai.RoadmapStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadmapTest {

    private val sample = DailyRoadmap(
        steps = listOf(
            RoadmapStep("09:00–09:30", "Review due cards", "Clear the queue first.", listOf("Insulin")),
            RoadmapStep("Morning", "Read new topic", "", emptyList()),
        ),
        epochDay = 20_000L,
    )

    @Test
    fun encodeDecodeRoundTrips() {
        val decoded = DailyRoadmap.decode(DailyRoadmap.encode(sample))
        assertEquals(sample, decoded)
    }

    @Test
    fun decodeBlankOrGarbageReturnsNull() {
        assertNull(DailyRoadmap.decode(null))
        assertNull(DailyRoadmap.decode(""))
        assertNull(DailyRoadmap.decode("   "))
        assertNull(DailyRoadmap.decode("not json at all"))
    }

    @Test
    fun decodeToleratesUnknownFields() {
        val raw = """{"epochDay":42,"steps":[{"time":"9am","title":"Go","extra":"ignored"}]}"""
        val decoded = DailyRoadmap.decode(raw)
        assertNotNull(decoded)
        assertEquals(42L, decoded!!.epochDay)
        assertEquals("Go", decoded.steps.single().title)
    }

    @Test
    fun isForDayRequiresMatchingDayAndNonEmpty() {
        assertTrue(sample.isForDay(20_000L))
        assertFalse(sample.isForDay(19_999L))
        // An empty roadmap is never "for" a day, even if the day matches.
        assertFalse(DailyRoadmap(emptyList(), 20_000L).isForDay(20_000L))
    }

    @Test
    fun paletteCyclesAndHandlesAnyIndex() {
        val n = RoadmapPalette.colors.size
        assertEquals(RoadmapPalette.colorForIndex(0), RoadmapPalette.colorForIndex(n))
        // Never throws for out-of-range / negative indices.
        assertEquals(RoadmapPalette.colors.first(), RoadmapPalette.colorForIndex(0))
        assertTrue(RoadmapPalette.colorForIndex(-1) in RoadmapPalette.colors)
    }
}
