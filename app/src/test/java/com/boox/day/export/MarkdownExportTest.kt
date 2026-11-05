package com.boox.day.export

import android.app.Application
import android.content.pm.ProviderInfo
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.DayPage
import com.boox.day.data.db.Page
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.editor.utils.Pen
import com.boox.day.io.ExportOutcome
import com.boox.day.io.ExportTarget
import com.boox.day.share.TasksFeedContract
import com.boox.day.share.TasksFeedProvider
import com.boox.day.today.DayDigest
import com.boox.day.today.DigestMemoPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Export as Markdown (Nov 2026): the day's note with its page pictures, and nothing the user wrote lost. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkdownExportTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val day = LocalDate.of(2026, 11, 3)
    private val note get() = File(DailyNoteWriter.defaultFolder(), "$day.md")
    private val attachments get() = File(DailyNoteWriter.defaultFolder(), MarkdownExport.ATTACHMENTS)
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = AppDatabase.getDatabase(context)
        Robolectric.buildContentProvider(TasksFeedProvider::class.java)
            .create(ProviderInfo().apply { authority = TasksFeedContract.AUTHORITY }).get()
    }

    @After
    fun tearDown() {
        AppDatabase.closeDatabase()
        note.delete()
        attachments.deleteRecursively()
    }

    private fun memoDay(pages: Int): ExportTarget.DayPages {
        val ids = (1..pages).map {
            val id = "note-" + UUID.randomUUID()
            db.pageDao().create(Page(id = id))
            runBlocking {
                db.strokeDao().create(listOf(Stroke(
                    size = 6f, pen = Pen.BALLPEN, top = 97f, bottom = 103f, left = 97f, right = 403f,
                    points = (0..20).map { n -> StrokePoint(100f + 15f * n, 100f) }, pageId = id
                )))
            }
            id
        }
        db.dayPageDao().insert(DayPage(date = day.toString(), pageIds = ids))
        return ExportTarget.DayPages(day.toString(), ids)
    }

    private fun export(target: ExportTarget.DayPages, images: Boolean = true) = runBlocking {
        withContext(Dispatchers.IO) { MarkdownExport.export(context, listOf(target), target, images) }
    }

    @Test fun `each link in the note matches a file written`() {
        val outcome = export(memoDay(2))

        val links = DailyNoteMarkdown.imagesIn(note.readText())
        assertEquals(mapOf(1 to "attachments/$day-p1.png", 2 to "attachments/$day-p2.png"), links)
        links.values.forEach { assertTrue(it, File(DailyNoteWriter.defaultFolder(), it).length() > 0) }
        assertEquals("Saved $day.md to Documents/BooxDay/Daily Notes", outcome.message)
        assertTrue("nothing to share", (outcome as ExportOutcome.Saved).uris.isEmpty())
    }

    @Test fun `a second export keeps what the user wrote around the note`() {
        val target = memoDay(1)
        export(target)
        note.writeText("My own line above\n\n" + note.readText() + "\nAnd one below\n")

        export(target)

        val text = note.readText()
        assertTrue(text.contains("My own line above"))
        assertTrue(text.contains("And one below"))
        assertEquals(1, Regex("booxday:start").findAll(text).count())
        assertEquals("one picture, replaced", listOf("$day-p1.png"), attachments.list()!!.toList())
    }

    @Test fun `a page with its picture has no not-read-yet line`() {
        export(memoDay(1))
        assertFalse(note.readText().contains("not read yet"))
    }

    @Test fun `without page images a page says it isn't read yet, and no picture is written`() {
        export(memoDay(1), images = false)
        assertTrue(note.readText().contains("Handwriting not read yet"))
        assertFalse(attachments.exists())
    }

    @Test fun `daily notes keep the pictures an export added`() {
        export(memoDay(1))

        runBlocking { DailyNotes.writeNote(context, day) }

        assertEquals(mapOf(1 to "attachments/$day-p1.png"), DailyNoteMarkdown.imagesIn(note.readText()))
    }

    @Test fun `a page holding only a picture is shown`() {
        val digest = DayDigest(day, emptyList(), emptyList(), 0, listOf(DigestMemoPage("p", null, hasPicture = true)))
        assertEquals(1, DailyNoteMarkdown.shownPages(digest).size)
    }

    @Test fun `a day with no memos writes nothing`() {
        val empty = ExportTarget.DayPages(day.toString(), emptyList())
        val outcome = export(empty)
        assertTrue(outcome is ExportOutcome.Empty)
        assertFalse(note.exists())
    }
}
