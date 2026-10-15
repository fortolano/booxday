package com.boox.day.booxnotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Smart Link title matching: real shared words only (Oct 2026). */
class TitleMatcherTest {

    private val abbreviations = mapOf("mtg" to "meeting", "oct" to "october", "budg" to "budget")
    private fun match(note: String, event: String) = TitleMatcher.matchTerm(note, event) { abbreviations[it] }

    // ── False matches seen on the Go 10.3 (all showed as "Strong" suggestions) ──

    @Test
    fun defaultNotebookTitleDoesNotMatchWordsInsideIt() {
        assertNull(match("Notebook-27", "TuGo's AI Technology Roadmap Forum. Please note: not recorded"))
    }

    @Test
    fun dateTitleDoesNotMatchTheMonthOrYear() {
        assertNull(match("Oct 12, 2026", "Senior Leader meeting October 14, 2026 agenda"))
        assertNull(match("Oct 7, 2026", "Adyen Account Review -- TuGo (Oct 6)"))
        assertNull(match("20260927", "TuGo with Kore customer Success 2026"))
    }

    @Test
    fun eventWordInsideALongerNoteWordIsNotAMatch() {
        assertNull(match("Notebook planning", "Book club"))
    }

    // ── Real matches keep working ──

    @Test
    fun sameWordMatches() {
        assertEquals("budget", match("Budget review notes", "Q4 Budget"))
    }

    @Test
    fun wordInTheEventDescriptionMatches() {
        assertEquals("roadmap", match("Roadmap ideas", "Forum. We will walk through the roadmap"))
    }

    @Test
    fun abbreviationAndStemMatch() {
        assertEquals("budget", match("budg follow-up", "Budget"))
        assertEquals("planning", match("Plannings", "Planning"))
    }

    @Test
    fun handwritingMisreadingMatches() {
        assertEquals("ministering", match("Mlnistering", "Ministering interviews"))
    }

    @Test
    fun titleOfOnlyDatesAndDefaultWordsNeverMatches() {
        assertNull(match("Notebook-1-23_24", "Notebook review 23"))
        assertNull(match("Monday", "Monday standup"))
    }
}
