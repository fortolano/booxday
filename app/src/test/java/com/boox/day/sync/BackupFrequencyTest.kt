package com.boox.day.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The backup screen offers Daily, Weekly and Monthly (Nov 2026). Monthly wasn't a known backup
 * frequency, so it read back as Weekly: the screen then showed Weekly and backups ran weekly.
 */
class BackupFrequencyTest {

    @Test
    fun monthlyReadsBackAsMonthly() {
        assertEquals(GoogleAuthManager.FREQUENCY_MONTHLY, GoogleAuthManager.backupFrequencyOf("Monthly"))
    }

    @Test
    fun monthlyMeansThirtyDays() {
        assertEquals(30L * 24 * 60 * 60 * 1000, GoogleAuthManager.getFrequencyMillis(GoogleAuthManager.FREQUENCY_MONTHLY))
    }

    @Test
    fun nothingSavedOrUnknownIsWeekly() {
        assertEquals(GoogleAuthManager.FREQUENCY_WEEKLY, GoogleAuthManager.backupFrequencyOf(null))
        assertEquals(GoogleAuthManager.FREQUENCY_WEEKLY, GoogleAuthManager.backupFrequencyOf("Yearly"))
    }
}
