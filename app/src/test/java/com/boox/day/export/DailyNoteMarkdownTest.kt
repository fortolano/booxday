package com.boox.day.export

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.boox.day.today.DayDigest
import com.boox.day.today.DigestEvent
import com.boox.day.today.DigestMemoPage
import com.boox.day.today.DigestTask
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * The Markdown daily note (Oct 2026): what it says, and that writing it again replaces only Boox
 * Day's part of the file. Robolectric for the file written to Documents/BooxDay/Daily Notes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DailyNoteMarkdownTest {

    private val zone = ZoneId.of("America/Toronto")
    private val day = LocalDate.of(2026, 10, 24)

    @After
    fun tearDown() {
        File(DailyNoteWriter.defaultFolder(), "2026-10-24.md").delete()
    }

    private fun at(h: Int, m: Int) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private val digest = DayDigest(
        date = day,
        events = listOf(
            DigestEvent("a", "Holiday", at(0, 0), null, allDay = true),
            DigestEvent("e", "Team\nmeeting", at(9, 0), at(10, 30), allDay = false, location = "Room 4",
                booxNoteTitles = listOf("Meeting notes")),
        ),
        tasks = listOf(
            DigestTask("t1", "Buy bread", completed = true),
            DigestTask("t2", "Call dentist", completed = false, priority = "A", fullText = "Call dentist\nask about Tuesday"),
            DigestTask("t3", "Book the 3 PM slot", completed = false, isSubtask = true),
            DigestTask("t4", null, completed = false),
        ),
        streakDays = 3,
        memoPages = listOf(
            DigestMemoPage("p1", "Ideas for the garden\nTomatoes", textBoxes = listOf("Typed note"), hasInk = true),
            DigestMemoPage("p2", null, hasInk = true),
            DigestMemoPage("p3", null, hasInk = false),
        ),
    )

    @Test
    fun noteListsTheDay() {
        val block = DailyNoteMarkdown.block(digest, use24Hour = false, zone = zone, locale = Locale.US)
        val expected = """
            <!-- booxday:start -->
            # Saturday, October 24, 2026

            ## Schedule
            - All day: Holiday
            - 9:00 AM–10:30 AM: Team meeting (Room 4)
              - BOOX Note: Meeting notes

            ## Tasks
            - [x] Buy bread
            - [ ] (A) Call dentist
              ask about Tuesday
              - [ ] Book the 3 PM slot
            - [ ] _Handwritten task (not read yet)_

            ## Memos

            ### Page 1

            Ideas for the garden
            Tomatoes

            > Typed note

            ### Page 2

            _Handwriting not read yet._

            <!-- booxday:end -->
        """.trimIndent()
        assertEquals(expected, block)
    }

    @Test
    fun anEmptyDaySaysSo() {
        val block = DailyNoteMarkdown.block(DayDigest(day, emptyList(), emptyList(), 0), use24Hour = true, zone = zone, locale = Locale.US)
        assertTrue(block.contains("## Schedule\nNo events."))
        assertTrue(block.contains("## Tasks\nNo tasks."))
        assertFalse(block.contains("## Memos"))
    }

    @Test
    fun writingAgainKeepsTheUsersOwnText() {
        val first = DailyNoteMarkdown.merge(null, "<!-- booxday:start -->\nold\n<!-- booxday:end -->", "---\ndate: x\n---\n")
        assertTrue(first.startsWith("---\ndate: x\n---\n"))
        val edited = first + "\nMy own thoughts.\n"
        val second = DailyNoteMarkdown.merge(edited, "<!-- booxday:start -->\nnew\n<!-- booxday:end -->", "---\n")
        assertEquals(edited.replace("old", "new"), second)
    }

    @Test
    fun aFileWithoutBooxDaysPartGetsItAtTheEnd() {
        val merged = DailyNoteMarkdown.merge("# My day\nNotes", "<!-- booxday:start -->\nx\n<!-- booxday:end -->", "---\n")
        assertEquals("# My day\nNotes\n\n<!-- booxday:start -->\nx\n<!-- booxday:end -->\n", merged)
    }

    @Test
    fun theDefaultFolderGetsOneFilePerDay() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val block = DailyNoteMarkdown.block(digest, use24Hour = true, zone = zone, locale = Locale.US)
        DailyNoteWriter.write(context, "2026-10-24.md") { DailyNoteMarkdown.merge(it, block, DailyNoteMarkdown.frontMatter(digest)) }
        val file = File(DailyNoteWriter.defaultFolder(), "2026-10-24.md")
        file.appendText("\nAdded in Obsidian.\n")

        val changed = digest.copy(tasks = digest.tasks.map { if (it.id == "t2") it.copy(completed = true) else it })
        val block2 = DailyNoteMarkdown.block(changed, use24Hour = true, zone = zone, locale = Locale.US)
        DailyNoteWriter.write(context, "2026-10-24.md") { DailyNoteMarkdown.merge(it, block2, DailyNoteMarkdown.frontMatter(changed)) }

        val text = file.readText()
        assertTrue(text.startsWith("---\ndate: 2026-10-24\nsource: Boox Day\n---\n"))
        assertTrue(text.contains("- [x] (A) Call dentist"))
        assertTrue(text.trimEnd().endsWith("Added in Obsidian."))
        assertEquals(1, Regex("booxday:start").findAll(text).count())
        assertFalse(File(DailyNoteWriter.defaultFolder(), "2026-10-24.md.tmp").exists())
    }
}
