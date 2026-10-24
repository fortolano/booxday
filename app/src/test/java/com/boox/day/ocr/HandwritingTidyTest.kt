package com.boox.day.ocr

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * What is saved for a handwritten task once ML Kit has read it (DigitalInkService.tidyRecognizedText,
 * with the app's own English word and name lists). Oct 2026: the Go 6 saved "TASK B" and "TASK X" as
 * "Task"; a clean-up rule dropped last letters and short words, and a spell "correction" against too
 * small a word list turned 11 of 40 everyday tasks into other words ("Buy bread" → "Buy read").
 * Here ML Kit's reading is taken as perfect: nothing may be lost or replaced, only capitals tidied.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HandwritingTidyTest {

    private fun wordList(language: String, name: String): Set<String> {
        val file = listOf("src/main/assets/text_processing/$language/$name", "app/src/main/assets/text_processing/$language/$name")
            .map { File(it) }.first { it.isFile }
        return file.readLines().map { it.trim().lowercase() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
    }

    private val english by lazy {
        HandwritingNormalizer.withWordLists(
            "en-US",
            ApplicationProvider.getApplicationContext<Application>(),
            wordList("en-US", "common_words.txt"),
            wordList("en-US", "names.txt")
        )
    }

    private fun tidy(read: String, language: String = "en-US") = DigitalInkService.tidyRecognizedText(read, language, english)

    private val everydayTasks = listOf(
        "Buy bread and milk", "Pay taxes", "Book dentist appointment", "Renew passport", "Print boarding pass",
        "Pick up dry cleaning", "Call plumber about leak", "Order birthday cake", "Schedule car inspection",
        "Email invoice to client", "Return library books", "Water the plants", "Take out the trash",
        "Walk the dog", "Buy chicken for dinner", "Pay electric bill", "Cancel gym membership",
        "Send thank you notes", "Fix the kitchen sink", "Review budget report", "Prepare slides for Monday",
        "Call mom", "Get a haircut", "Change car oil", "Buy groceries", "Clean the garage",
        "Finish homework", "Pick up kids at 3 PM", "Mail the package", "Refill prescription",
        "Book flight to Denver", "Update resume", "Wash the car", "Buy flowers for Anna", "Meet Rakesh",
        "Submit expense report", "Do laundry", "Vacuum living room", "Read chapter 5", "Plan B for trip"
    )

    private val shortAndOddWords = listOf(
        "TASK B", "TASK X", "Plan B", "Option A", "Vitamin D", "Building C", "Gate A12", "Room 5B", "Call Mr X",
        "Meeting at 3 PM", "Lunch 12 PM", "Review PR", "Email HR", "Fix UI", "Fix CI", "Ship v2", "Plan Q4",
        "Budget for Q3", "Fly to LA", "Go to NY", "Call Al", "Call Ed", "Text Jo", "Ask Dr", "5 min call",
        "2 eggs", "3 pm meeting", "1 Call mom", "X ray appt", "B12 shot", "C++ homework", "Call IBM",
        "Pres Santos", "Buy AA batteries", "Buy milk, eggs", "B", "X", "PR", "Task 2"
    )

    @Test
    fun theLettersTheGo6DroppedAreKept() {
        assertEquals("Task B", tidy("TASK B"))
        assertEquals("Task X", tidy("TASK X"))
        assertEquals("Task B", tidy("Task B"))
    }

    @Test
    fun noWordIsDroppedOrReplaced() {
        val changed = (everydayTasks + shortAndOddWords).mapNotNull { read ->
            tidy(read).takeIf { !it.equals(read, ignoreCase = true) }?.let { "$read -> $it" }
        }
        assertTrue("changed: $changed", changed.isEmpty())
    }

    @Test
    fun capitalsAreTidiedWithoutLosingAbbreviationsOrLetters() {
        val expected = mapOf(
            "CALL MOM" to "Call mom",
            "call mom" to "Call mom",
            "Review PR" to "Review PR",
            "REVIEW PR" to "Review PR",
            "Call IBM" to "Call IBM",
            "Fix UI" to "Fix UI",
            "Do it" to "Do it",
            "DO IT" to "Do it",
            "Call IT about the laptop" to "Call IT about the laptop",
            "Pick up kids at 3 pm" to "Pick up kids at 3 PM",
            "Call mom at 9 am" to "Call mom at 9 AM",
            "I am late" to "I am late",
            "I AM LATE" to "I am late",
            "Tell him I'm late" to "Tell him I'm late",
            "Ask if i can go" to "Ask if I can go",
            "PLAN A" to "Plan A",
            "GET A HAIRCUT" to "Get a haircut",
            "Get a haircut" to "Get a haircut",
            "A to do" to "A to do",
            "Q4 review" to "Q4 review",
            "Gate A12" to "Gate A12",
            "TASK4" to "Task4",
            "Mark as done" to "Mark as done",
            "Call Mark" to "Call Mark",
            "Meet Rakesh" to "Meet Rakesh",
            "MEET RAKESH" to "Meet Rakesh",
            "Meet Pres Santos" to "Meet Pres Santos",
            "Fly to LA" to "Fly to LA",
            "Buy milk, eggs" to "Buy milk, eggs"
        )
        val wrong = expected.mapNotNull { (read, saved) -> tidy(read).takeIf { it != saved }?.let { "$read -> $it (expected $saved)" } }
        assertTrue("wrong: $wrong", wrong.isEmpty())
    }

    @Test
    fun onlySpacesAndStrayEndPunctuationGo() {
        assertEquals("Call mom", tidy("  Call   mom. "))
        assertEquals("Pay rent", tidy("Pay rent!"))
        assertEquals("", tidy(""))
        assertEquals("", tidy("   "))
        assertEquals("", tidy("."))
    }

    @Test
    fun otherLanguagesKeepWhatWasRead() {
        // The English rules lowercased German nouns, and a second English pass made "und" into "fund"
        assertEquals("Brot und Milch kaufen", tidy("Brot und Milch kaufen", "de-DE"))
        assertEquals("Mama anrufen", tidy("Mama anrufen", "de-DE"))
        assertEquals("Rendez-vous chez le Médecin", tidy("rendez-vous chez le Médecin", "fr-FR"))
    }

    @Test
    fun tidyingTwiceChangesNothing() {
        // Typed task text goes through the same capitals for the search index
        val unstable = (everydayTasks + shortAndOddWords).mapNotNull { read ->
            val once = tidy(read)
            tidy(once).takeIf { it != once }?.let { "$read: $once -> $it" }
        }
        assertTrue("unstable: $unstable", unstable.isEmpty())
    }
}
