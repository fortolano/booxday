package com.boox.day.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Settings setup dots (Oct 2026): one writer that never undoes another dot, and waits for the saved settings. */
class SetupChecklistStoreTest {

    private val saved = AppSettings(version = 1, setupChecklist = SetupChecklist(preferencesReviewed = true))

    @Test
    fun markKeepsTheOtherFlags() {
        val result = SetupChecklistStore.updated(loaded = true, current = saved) { it.copy(aiSettingsReviewed = true) }
        assertEquals(SetupChecklist(preferencesReviewed = true, aiSettingsReviewed = true), result?.setupChecklist)
    }

    @Test
    fun nothingBeforeTheSavedSettingsAreLoaded() {
        assertNull(SetupChecklistStore.updated(loaded = false, current = saved) { it.copy(aiSettingsReviewed = true) })
    }

    @Test
    fun noWriteWhenNothingChanges() {
        assertNull(SetupChecklistStore.updated(loaded = true, current = saved) { it.copy(preferencesReviewed = true) })
    }
}
