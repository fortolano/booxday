package com.boox.day.ai

import com.boox.day.data.ai.AiPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

/** Health or Social suggestions off drop those ideas from the answer, whatever the AI did (Oct 2026). */
class SuggestionOptionsFilterTest {

    private val ideas = listOf("Book dentist" to "WORK", "Evening yoga" to "HEALTH", "Call Sam" to "social", "Pay rent" to "FINANCE")

    private fun kept(options: AiPreferences) = SuggestionOptionsFilter.keep(ideas, options) { it.second }.map { it.first }

    @Test
    fun bothOnKeepsEverything() {
        assertEquals(ideas.map { it.first }, kept(AiPreferences()))
    }

    @Test
    fun healthOffDropsHealthOnly() {
        assertEquals(listOf("Book dentist", "Call Sam", "Pay rent"), kept(AiPreferences(includeHealthReminders = false)))
    }

    @Test
    fun socialOffDropsSocialOnlyWhateverTheCase() {
        assertEquals(listOf("Book dentist", "Evening yoga", "Pay rent"), kept(AiPreferences(includeSocialReminders = false)))
    }
}
