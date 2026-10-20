package com.generic.mnemos

import com.generic.mnemos.core.settings.AmbientPrefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientPrefsTest {

    @Test
    fun quietHours_wrappingMidnight() {
        val p = AmbientPrefs(quietStartHour = 22, quietEndHour = 7)
        assertTrue(p.isQuietAt(23))   // late night
        assertTrue(p.isQuietAt(2))    // small hours
        assertTrue(p.isQuietAt(22))   // start is inclusive
        assertFalse(p.isQuietAt(7))   // end is exclusive
        assertFalse(p.isQuietAt(12))  // midday
    }

    @Test
    fun quietHours_sameDayWindow() {
        val p = AmbientPrefs(quietStartHour = 1, quietEndHour = 5)
        assertTrue(p.isQuietAt(3))
        assertFalse(p.isQuietAt(0))
        assertFalse(p.isQuietAt(5))   // exclusive end
        assertFalse(p.isQuietAt(23))
    }
}
