package com.boox.day.io

import android.app.Application
import android.content.pm.ProviderInfo
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.DayPage
import com.boox.day.data.db.DayPageRepository
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.Page
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.data.db.UnifiedOcrPage
import com.boox.day.data.db.UnifiedOcrText
import com.boox.day.editor.utils.Pen
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.model.UnifiedOcrStatus
import com.boox.day.share.TasksFeedContract
import com.boox.day.share.TasksFeedProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.util.Date
import java.util.UUID

/** Exporting more than one day (Nov 2026): which pages a choice covers, and how a PDF of them is set out. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExportDaysTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val monday = LocalDate.of(2026, 11, 2)
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
    }

    private fun <T> offMain(block: suspend () -> T): T = runBlocking { withContext(Dispatchers.IO) { block() } }

    private fun line(pageId: String, y: Float) = Stroke(
        size = 6f, pen = Pen.BALLPEN, top = y - 3f, bottom = y + 3f, left = 97f, right = 403f,
        points = (0..20).map { StrokePoint(100f + 15f * it, y) }, pageId = pageId
    )

    /** A memo page with a line of ink at [inkY], on [date]. */
    private fun memoDay(date: LocalDate, vararg inkY: Float): List<String> {
        val pages = inkY.map { y ->
            val id = "days-" + UUID.randomUUID()
            db.pageDao().create(Page(id = id))
            runBlocking { db.strokeDao().create(listOf(line(id, y))) }
            id
        }
        db.dayPageDao().insert(DayPage(date = date.toString(), pageIds = pages))
        return pages
    }

    private fun plan(days: List<ExportTarget.DayPages>, options: ExportOptions, words: (String) -> List<String> = { emptyList() }) =
        offMain { CompactExportEngine(context).plan(days, options) { _, pageId -> ExportText.PageWords(words(pageId), false) } }

    // ---- Which days

    @Test fun `this week follows the first day of the week`() {
        val thursday = LocalDate.of(2026, 11, 5)
        assertEquals(LocalDate.of(2026, 11, 1), ExportScope.weekOf(thursday, AppSettings.FirstDayOfWeek.Sunday).start)
        assertEquals(monday, ExportScope.weekOf(thursday, AppSettings.FirstDayOfWeek.Monday).start)
        assertEquals(LocalDate.of(2026, 10, 31), ExportScope.weekOf(thursday, AppSettings.FirstDayOfWeek.Saturday).start)
        assertEquals(LocalDate.of(2026, 11, 8), ExportScope.weekOf(thursday, AppSettings.FirstDayOfWeek.Monday).endInclusive)
    }

    @Test fun `dates give the days with memos, in order`() {
        val tuesday = memoDay(monday.plusDays(1), 100f)
        val friday = memoDay(monday.plusDays(4), 100f, 200f)

        val target = ExportScope.resolve(DayPageRepository(context), monday, monday.plusDays(6))

        assertEquals(listOf("2026-11-03", "2026-11-06"), target.days.map { it.date })
        assertEquals(listOf(tuesday, friday), target.days.map { it.pageIds })
        assertEquals("2026-11-02", target.from)
        assertEquals("2026-11-08", target.to)
    }

    @Test fun `looking at dates makes no day or page`() {
        fun pageCount() = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM page").use {
            it.moveToFirst()
            it.getInt(0)
        }
        val pagesBefore = pageCount()

        ExportScope.resolve(DayPageRepository(context), monday, monday.plusDays(30))

        assertNull(db.dayPageDao().getByDate(monday.toString()))
        assertEquals(pagesBefore, pageCount())
    }

    @Test fun `up to 31 days can be chosen`() {
        assertTrue(ExportScope.isValid(monday, monday.plusDays(30)))
        assertFalse(ExportScope.isValid(monday, monday.plusDays(31)))
        assertFalse(ExportScope.isValid(monday, monday.minusDays(1)))
    }

    // ---- The PDF

    @Test fun `several days pack in order under their dates, with a contents page`() {
        val days = listOf(monday.plusDays(4), monday.plusDays(1)).map {
            ExportTarget.DayPages(it.toString(), memoDay(it, 100f))
        }

        val plan = plan(days, ExportOptions())

        val dates = plan.sheets.flatMap { sheet -> sheet.blocks.map { it.date } }
        assertEquals(listOf("2026-11-03", "2026-11-06"), dates)
        assertEquals(listOf("2026-11-03", "2026-11-06"), plan.contents.map { it.first })
        assertEquals("the first day follows the contents page", 2, plan.contents.first().second)
        assertEquals(plan.sheets.size + 1, plan.pageCount)
        assertTrue(plan.style.footer)
    }

    @Test fun `one day has no contents page or page numbers`() {
        val day = ExportTarget.DayPages(monday.toString(), memoDay(monday, 100f, 200f))

        val plan = plan(listOf(day), ExportOptions())

        assertTrue(plan.contents.isEmpty())
        assertFalse(plan.style.footer)
        assertEquals(plan.sheets.size, plan.pageCount)
    }

    @Test fun `a date range with memos on one day only has no contents page`() {
        val days = listOf(
            ExportTarget.DayPages(monday.toString(), memoDay(monday, 100f)),
            ExportTarget.DayPages(monday.plusDays(1).toString(), emptyList())
        )
        assertTrue(plan(days, ExportOptions()).contents.isEmpty())
    }

    @Test fun `one memo per page starts each memo on its own page, and nothing is lost`() {
        val days = listOf(
            ExportTarget.DayPages(monday.toString(), memoDay(monday, 100f, 2000f)),
            ExportTarget.DayPages(monday.plusDays(1).toString(), memoDay(monday.plusDays(1), 100f))
        )

        val plan = plan(days, ExportOptions(layout = ExportLayout.ONE_PER_PAGE))

        assertEquals(3, plan.sheets.count { sheet -> sheet.blocks.filterIsInstance<PageSliceBlock>().any { it.startsSheet } })
        assertTrue("date headers on several days", plan.style.dateHeaders)
        plan.sheets.forEach { assertTrue("used ${it.usedHeight}", it.remainingHeight >= 0) }
    }

    @Test fun `a page cut by the page break keeps every part`() {
        val day = ExportTarget.DayPages(monday.toString(), memoDay(monday, 100f, 2400f))
        val text = (1..40).map { "A line of typed text number $it" }

        val plan = plan(listOf(day), ExportOptions(layout = ExportLayout.ONE_PER_PAGE, typedText = true)) { text }

        val parts = plan.sheets.flatMap { it.blocks }.filterIsInstance<PageSliceBlock>().groupBy { it.pageId }.values
        parts.forEach { slices -> slices.zipWithNext { a, b -> assertEquals(a.bottom, b.top, 0.01f) } }
        plan.sheets.forEach { assertTrue("used ${it.usedHeight}", it.remainingHeight >= 0) }
    }

    @Test fun `typed text stays with its page and runs on rather than being cut`() {
        val pages = memoDay(monday, 100f, 200f)
        val long = (1..400).map { "Line $it of what was written" }

        val plan = plan(listOf(ExportTarget.DayPages(monday.toString(), pages)), ExportOptions(typedText = true)) { id ->
            if (id == pages.first()) long else listOf("Second page")
        }

        val blocks = plan.sheets.flatMap { it.blocks }
        val text = blocks.filterIsInstance<TextFlowBlock>()
        assertTrue("runs on: ${text.size} parts", text.size > 2)
        assertTrue(blocks[0] is CompactExportEngine.ContentBlock)
        assertTrue("under the first memo", blocks[1] is TextFlowBlock)
        plan.sheets.forEach { assertTrue("used ${it.usedHeight}", it.remainingHeight >= 0) }
        assertTrue("the text fills more than one PDF page", plan.pageCount > 1)
    }

    @Test fun `a page with no words adds no heading`() {
        val day = ExportTarget.DayPages(monday.toString(), memoDay(monday, 100f))
        val plan = plan(listOf(day), ExportOptions(typedText = true))
        assertTrue(plan.sheets.flatMap { it.blocks }.none { it is TextFlowBlock })
    }

    @Test fun `the day's events and tasks come first, and only when asked`() {
        val day = ExportTarget.DayPages(monday.toString(), memoDay(monday, 100f))
        runBlocking {
            db.localEventDao().insert(LocalEvent(id = "e1", title = "Dentist", dayId = monday.toString(), endDayId = monday.toString(),
                startTime = 1_762_000_000_000, endTime = 1_762_003_600_000))
        }

        assertTrue(plan(listOf(day), ExportOptions(eventsAndTasks = true)).sheets.first().blocks.first() is DayInfoBlock)
        assertTrue(plan(listOf(day), ExportOptions()).sheets.flatMap { it.blocks }.none { it is DayInfoBlock })
    }

    @Test fun `what's in a day's info stays with its first memo on one memo per page`() {
        val day = ExportTarget.DayPages(monday.toString(), memoDay(monday, 100f))
        runBlocking {
            db.localEventDao().insert(LocalEvent(id = "e2", title = "Dentist", dayId = monday.toString(), endDayId = monday.toString(),
                startTime = 1_762_000_000_000, endTime = 1_762_003_600_000))
        }

        val plan = plan(listOf(day), ExportOptions(layout = ExportLayout.ONE_PER_PAGE, eventsAndTasks = true))

        assertEquals(1, plan.sheets.size)
        assertEquals(2, plan.sheets.single().blocks.size)
    }

    @Test fun `with events on, each day of one memo per page still starts its own page`() {
        val days = listOf(monday, monday.plusDays(1)).map { ExportTarget.DayPages(it.toString(), memoDay(it, 100f)) }
        runBlocking {
            days.forEachIndexed { i, day ->
                db.localEventDao().insert(LocalEvent(id = "e$i", title = "Dentist", dayId = day.date, endDayId = day.date,
                    startTime = 1_762_000_000_000, endTime = 1_762_003_600_000))
            }
        }

        val plan = plan(days, ExportOptions(layout = ExportLayout.ONE_PER_PAGE, eventsAndTasks = true))

        assertEquals(2, plan.sheets.size)
        plan.sheets.forEach { assertEquals(1, it.blocks.map { block -> block.date }.distinct().size) }
    }

    @Test fun `a day's events stay on the page of its first memo`() {
        val first = ExportTarget.DayPages(monday.toString(), memoDay(monday, 100f, 1900f))
        val second = ExportTarget.DayPages(monday.plusDays(1).toString(), memoDay(monday.plusDays(1), 100f, 1900f))
        val rows = (1..14).map { TextRow("Event $it") }
        val engine = CompactExportEngine(context)
        val blocks = listOf(first, second).flatMap { day ->
            val measured = offMain { engine.measureAllContent(day.date, day.pageIds) }
            listOf<SheetBlock>(DayInfoBlock(day.date, rows, emptyList())) + engine.contentBlocks(measured, engine.scaleFactorFor(measured))
        }

        val sheets = engine.packContentBlocks(blocks)

        sheets.forEach { sheet ->
            val last = sheet.blocks.last()
            assertFalse("events at the foot of a page, memo on the next", last is DayInfoBlock)
        }
    }

    @Test fun `a page break goes between lines of writing`() {
        val slice = PageSliceBlock("2026-11-02", "p", 0f, 0f, 2000f, 0.5f, startsSheet = true, blankRows = listOf(500f, 900f, 1500f))

        val (first, rest) = checkNotNull(slice.split(600))

        assertEquals(900f, (first as PageSliceBlock).bottom, 0f)
        assertEquals(900f, (rest as PageSliceBlock).top, 0f)
        assertFalse(rest.startsSheet)
    }

    @Test fun `nothing on the chosen dates says so`() {
        assertEquals(
            "No memos in these dates.",
            ExportOutcome.emptyMessage(ExportTarget.Days("2026-11-02", "2026-11-08", emptyList()))
        )
    }

    // ---- Typed text

    private fun saveReading(date: String, pageId: String, text: String, readAt: Long) = runBlocking {
        val id = UnifiedOcrText.generateId(OcrSource.MEMO, date, pageId)
        db.unifiedOcrDao().upsertOcrPage(
            UnifiedOcrPage(
                id = id, sourceType = OcrSource.MEMO.name, sourceId = date, pageId = pageId, sourceCreatedAt = readAt,
                sourceModifiedAt = readAt, ocrAt = readAt, status = UnifiedOcrStatus.SUCCESS.name
            )
        )
        db.unifiedOcrDao().upsertOcrText(
            UnifiedOcrText(
                id = id, sourceType = OcrSource.MEMO.name, sourceId = date, pageId = pageId, text = text,
                textNormalized = text.lowercase(), cleanText = text, wordCount = 2, confidence = 1f, synopsis = text,
                updatedAt = readAt
            )
        )
    }

    @Test fun `up to date saved reading is used`() {
        val page = memoDay(monday, 100f).single()
        db.pageDao().updateUpdatedAt(page, Date(1_000L))
        saveReading(monday.toString(), page, "Call the bank\nPay rent", readAt = 2_000L)

        val words = offMain { ExportText.read(context, monday.toString(), page, ink = null) }

        assertEquals(listOf("Call the bank", "Pay rent"), words.lines)
        assertFalse(words.notRead)
    }

    @Test fun `saved reading older than the page is not used`() {
        val page = memoDay(monday, 100f).single()
        db.pageDao().updateUpdatedAt(page, Date(3_000L))
        saveReading(monday.toString(), page, "Old words", readAt = 2_000L)

        assertNull(offMain { ExportText.savedText(context, monday.toString(), page) })
    }

    @Test fun `no language pack is reported, not a crash`() {
        val page = memoDay(monday, 100f).single()

        val words = offMain { ExportText.read(context, monday.toString(), page, ink = null) }

        assertTrue(words.lines.isEmpty())
        assertTrue(words.notRead)
    }

    @Test fun `the message says when handwriting wasn't typed or the file moved`() {
        val saved = "Saved a.pdf to Documents/BooxDay"
        assertEquals(saved, ExportOutcome.withNotes(saved, handwritingNotRead = false, movedToDefault = false))
        assertTrue(ExportOutcome.withNotes(saved, handwritingNotRead = true, movedToDefault = false).contains("language pack"))
        assertTrue(ExportOutcome.withNotes(saved, handwritingNotRead = false, movedToDefault = true).startsWith("Couldn't reach the chosen folder."))
    }

    // ---- Estimated size

    @Test fun `the size is rounded the way people say it`() {
        assertEquals("about 300 KB", ExportSize.label(296_000))
        assertEquals("about 100 KB", ExportSize.label(12_000))
        assertEquals("about 1 MB", ExportSize.label(960_000))
        assertEquals("about 3 MB", ExportSize.label(2_700_000))
    }
}
