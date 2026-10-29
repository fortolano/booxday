package com.boox.day.ui.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The waiting lines and start-screen taglines stay short, varied and fit to show (29 Oct 2026). */
class AiFeedbackMessagesTest {

    private val sets = AiFeedbackMessages.allMessageSets()
    private val all = sets.values.flatten()

    /** Worn-out templates and lines that should never come back. */
    private val banned = listOf(
        "Führer", "Wakanda", "Who you gonna call", "very particular set", "server",
        "Pretending I understood", "didn't hear", "You had me at", "Show me the"
    )

    @Test
    fun everySetHasEnoughLines() {
        sets.forEach { (name, lines) -> assertTrue("$name has ${lines.size}", lines.size >= 7) }
    }

    @Test
    fun linesAreReadableInFourSeconds() {
        val long = all.filter { it.length > 60 }
        assertTrue("Lines over 60 characters: $long", long.isEmpty())
    }

    @Test
    fun noLineRepeatsAnywhere() {
        val dupes = all.groupBy { it.lowercase() }.filterValues { it.size > 1 }.keys
        assertTrue("Repeated lines: $dupes", dupes.isEmpty())
    }

    @Test
    fun noBannedPhrases() {
        val hits = (all + splashTaglines).filter { line -> banned.any { line.contains(it, ignoreCase = true) } }
        assertTrue("Banned phrases: $hits", hits.isEmpty())
    }

    @Test
    fun shuffleBagShowsEveryLineBeforeRepeating() {
        val review = sets.getValue("review")
        val drawn = List(review.size) { AiFeedbackMessages.getRandomReviewMessage() }
        assertEquals(review.toSet(), drawn.toSet())
    }

    @Test
    fun taglinesFitTheStartScreen() {
        val long = splashTaglines.filter { it.length > 50 }
        assertTrue("Taglines over 50 characters: $long", long.isEmpty())
        assertEquals(splashTaglines.size, splashTaglines.map { it.lowercase() }.toSet().size)
        assertTrue(splashTaglines.size >= 30)
        // BooxDay is a black and white daily planner
        assertTrue(splashTaglines.none { it.contains("shades of gray", true) || it.contains("frames per second", true) })
    }
}
