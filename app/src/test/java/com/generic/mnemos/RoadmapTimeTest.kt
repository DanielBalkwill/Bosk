package com.generic.mnemos

import com.generic.mnemos.core.ai.RoadmapStep
import com.generic.mnemos.core.ai.RoadmapTime
import org.junit.Assert.assertEquals
import org.junit.Test

class RoadmapTimeTest {

    @Test
    fun parsesCommonTimeFormats() {
        assertEquals(9 * 60, RoadmapTime.startMinutes("09:00"))
        assertEquals(9 * 60 + 30, RoadmapTime.startMinutes("9:30 AM"))
        assertEquals(14 * 60, RoadmapTime.startMinutes("2pm"))
        assertEquals(14 * 60, RoadmapTime.startMinutes("14.00 – 15.00"))
        assertEquals(0, RoadmapTime.startMinutes("12:00 am"))
        assertEquals(null, RoadmapTime.startMinutes("morning"))
        assertEquals(null, RoadmapTime.startMinutes(""))
    }

    @Test
    fun stepIsPastOnceALaterStepHasStarted() {
        val steps = listOf(
            RoadmapStep("09:00", "A", "", emptyList()),
            RoadmapStep("11:00", "B", "", emptyList()),
            RoadmapStep("15:00", "C", "", emptyList()),
        )
        // 12:00 — B has started, so A is over; B is current; C is future.
        assertEquals(setOf(0), RoadmapTime.pastStepIndices(steps, 12 * 60))
        // 16:00 — C has started: A and B are over.
        assertEquals(setOf(0, 1), RoadmapTime.pastStepIndices(steps, 16 * 60))
        // 08:00 — nothing has started.
        assertEquals(emptySet<Int>(), RoadmapTime.pastStepIndices(steps, 8 * 60))
    }

    @Test
    fun unparseableTimesAreNeverStruck() {
        val steps = listOf(
            RoadmapStep("morning", "A", "", emptyList()),
            RoadmapStep("11:00", "B", "", emptyList()),
        )
        assertEquals(emptySet<Int>(), RoadmapTime.pastStepIndices(steps, 12 * 60))
    }
}
