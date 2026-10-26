package com.boox.day.editor.ui

import com.boox.day.data.ai.AiPreferences
import com.boox.day.data.ai.EnergyPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Closing the AI Preferences wizard keeps the answers, like Save (Oct 2026). */
class AiPreferencesCloseTest {

    private val saved = AiPreferences(workPriority = 4, lastUpdated = 1_000L)

    @Test
    fun closingWithAChangedAnswerSavesIt() {
        val changed = saved.copy(energyPattern = EnergyPattern.MORNING_PERSON)
        val toSave = preferencesToSaveOnClose(saved, changed)!!
        assertEquals(EnergyPattern.MORNING_PERSON, toSave.energyPattern)
        assertEquals(4, toSave.workPriority)
        // The same save as the Save button
        assertTrue(toSave.setupComplete)
    }

    @Test
    fun closingWithoutAChangeWritesNothing() {
        assertNull(preferencesToSaveOnClose(saved, saved))
        // Changed and changed back
        assertNull(preferencesToSaveOnClose(saved, saved.copy(workPriority = 5).copy(workPriority = 4)))
    }

    @Test
    fun answersSavedEarlierStaySetUp() {
        val done = saved.copy(setupComplete = true)
        assertNull(preferencesToSaveOnClose(done, done))
        assertTrue(preferencesToSaveOnClose(done, done.copy(hasPets = true))!!.setupComplete)
    }
}
