package app.bosk

import app.bosk.ui.screens.graph.labelFadeAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The zoom-in label fade: names should ramp in smoothly over a band of zoom ending at the render
 * threshold, not pop in. Locks the opacity curve so the gradient stays correct.
 */
class GraphLabelsTest {

    @Test
    fun hiddenWellBelowThreshold() {
        assertEquals(0f, labelFadeAlpha(scale = 0.5f, minScale = 1.2f, band = 0.4f), 1e-4f)
    }

    @Test
    fun fullyVisibleAtThreshold() {
        assertEquals(1f, labelFadeAlpha(scale = 1.2f, minScale = 1.2f, band = 0.4f), 1e-4f)
    }

    @Test
    fun fullyVisibleAboveThreshold() {
        assertEquals(1f, labelFadeAlpha(scale = 3f, minScale = 1.2f, band = 0.4f), 1e-4f)
    }

    @Test
    fun halfwayThroughBandIsHalfOpacity() {
        // midpoint of the fade band -> smoothstep(0.5) == 0.5
        assertEquals(0.5f, labelFadeAlpha(scale = 1.0f, minScale = 1.2f, band = 0.4f), 1e-4f)
    }

    @Test
    fun opacityIncreasesMonotonicallyAcrossBand() {
        val lo = labelFadeAlpha(0.9f, 1.2f, 0.4f)
        val mid = labelFadeAlpha(1.0f, 1.2f, 0.4f)
        val hi = labelFadeAlpha(1.1f, 1.2f, 0.4f)
        assertTrue(lo < mid && mid < hi)
    }
}
