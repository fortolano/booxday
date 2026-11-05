package com.boox.day.week

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** D7: the same grid on every tablet; only the density changes. */
class WeekLayoutSpecTest {
    @Test
    fun everyTabletGetsAtLeastFiveLinesADay() {
        val sizes = listOf(572 to 772, 674 to 896, 773 to 1031, 997 to 1330)
        sizes.forEach { (w, h) -> assertTrue("$w x $h", WeekLayoutSpec.of(w, h).linesPerDay >= 5) }
        assertTrue(WeekLayoutSpec.of(572, 772).compact)
        assertFalse(WeekLayoutSpec.of(674, 896).compact)
    }

    @Test
    fun timesFollowTheSettingAndTheSize() {
        assertEquals("9:00 AM", WeekLayoutSpec.timeText(540, use24Hour = false, compact = false))
        assertEquals("9a", WeekLayoutSpec.timeText(540, use24Hour = false, compact = true))
        assertEquals("12:30p", WeekLayoutSpec.timeText(750, use24Hour = false, compact = true))
        assertEquals("12a", WeekLayoutSpec.timeText(0, use24Hour = false, compact = true))
        assertEquals("13:00", WeekLayoutSpec.timeText(780, use24Hour = true, compact = true))
    }
}
