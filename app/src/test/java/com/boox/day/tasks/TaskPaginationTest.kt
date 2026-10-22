package com.boox.day.tasks

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Page
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.data.db.Task
import com.boox.day.data.db.TextAnnotation
import com.boox.day.editor.utils.Pen
import com.boox.day.editor.utils.getDominantRowIndex
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Full screen Tasks (Oct 2026): a day's tasks are one ordered list of slots, laid out on pages of 10
 * rows (normal view) or 17 (full screen). Going to full screen and back must give back exactly the
 * same day — task IDs, rows, drawing pages and ink — and rows written in full screen must land on
 * the next page in order. Runs on the JVM (Robolectric) against the real Room database and DAOs.
 * Geometry like the Go 10.3: about 1386 px = 10 rows of 138.4 px under a 2.4 px top offset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TaskPaginationTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun fullScreenPutsTheDaysTasksInOrderOnOneLongPage() {
        val pages = seedDay(db)
        val idOf = db.taskDao().getTasksForDay(DATE).filter { it.typedText != null }.associate { it.typedText to it.id }

        assertNotNull(expand(pages))

        val after = db.taskDao().getTasksForDay(DATE)
        // A..O and the two blank rows after them on one page of 17; the blank slots past it are gone
        assertEquals(listOf(0), after.map { it.taskPageIndex }.distinct())
        assertEquals((0 until 17).toList(), after.map { it.rowIndex })
        assertEquals(TITLES, after.mapNotNull { it.typedText })
        after.filter { it.typedText != null }.forEach { assertEquals(idOf[it.typedText], it.id) }
        // All on the first drawing page; the second is kept, empty, for the way back
        assertTrue(after.all { it.pageId == pages[0] })
        assertNotNull(db.pageDao().getById(pages[1]))
        assertTrue(db.strokeDao().getAllForPage(pages[1]).isEmpty())
        // K's ink went from page 2 row 0 to page 1 row 10, moved by whole rows (not scaled)
        val kRow = after.first { it.typedText == "K" }.rowIndex
        assertEquals(10, kRow)
        val kInk = inkInRow(pages[0], kRow, rows = 17)
        assertEquals(1, kInk.size)
        assertEquals(rowTop(10) + 0.2f * ROW_HEIGHT, kInk[0].top, 0.01f)
        assertEquals(rowTop(10) + 0.7f * ROW_HEIGHT, kInk[0].bottom, 0.01f)
    }

    @Test
    fun fullScreenAndBackGivesBackTheSameDay() {
        val pages = seedDay(db)
        // A subtask, a repeat link with its due day, and ink reaching into the next row
        val b = taskTitled("B")
        db.taskDao().insertAll(listOf(taskTitled("C").copy(parentTaskId = b.id), taskTitled("M").copy(templateId = "weekly", dueDay = DATE)))
        db.strokeDao().create(strokeInRow(pages[1], row = 3, from = 0.3f, to = 1.4f))
        val tasksBefore = slots()
        val inkBefore = allInk(pages)

        assertNotNull(expand(pages))
        assertNotNull(collapse(pages))

        assertEquals(tasksBefore, slots())
        val inkAfter = allInk(pages)
        assertEquals(inkBefore.keys, inkAfter.keys)
        for ((id, before) in inkBefore) {
            val after = inkAfter.getValue(id)
            assertEquals(before.pageId, after.pageId)
            assertEquals(before.top, after.top, 0.01f)
            assertEquals(before.bottom, after.bottom, 0.01f)
            // Points are stored to 0.01 px: there and back gives exactly the stored points
            before.points.zip(after.points).forEach { (p, q) -> assertEquals(p.y, q.y, 0.001f) }
        }
        assertEquals(2, (db.taskDao().getMaxTaskPageIndex(DATE) ?: -1) + 1)
        assertEquals(20, db.taskDao().getTasksForDay(DATE).size)
    }

    @Test
    fun rowsWrittenInFullScreenGoOnTheNextPageInOrder() {
        val pages = seedDay(db)
        assertNotNull(expand(pages))
        // Write P and Q in the two blank rows at the bottom of the long page
        for ((row, title) in listOf(15 to "P", 16 to "Q")) {
            val task = db.taskDao().getTasksForPage(DATE, 0).first { it.rowIndex == row }
            db.taskDao().insertAll(listOf(task.copy(typedText = title)))
            db.strokeDao().create(strokeInRow(pages[0], row))
        }

        assertNotNull(collapse(pages))

        val page2 = db.taskDao().getTasksForPage(DATE, 1).sortedBy { it.rowIndex }
        assertEquals(listOf("K", "L", "M", "N", "O", "P", "Q"), page2.mapNotNull { it.typedText })
        assertEquals(5, page2.first { it.typedText == "P" }.rowIndex)
        val pInk = inkInRow(pages[1], row = 5, rows = 10)
        assertEquals(1, pInk.size)
        assertEquals(rowTop(5) + 0.2f * ROW_HEIGHT, pInk[0].top, 0.01f)
    }

    @Test
    fun theWayBackKeepsTheDaysPagesEvenWhenEmpty() {
        // Three pages, tasks only on the first: full screen shows one page, back gives three
        val pages = seedDay(db, pageCount = 3, titles = 4)
        assertNotNull(expand(pages))
        assertEquals(listOf(0), db.taskDao().getTasksForDay(DATE).map { it.taskPageIndex }.distinct())

        assertNotNull(collapse(pages))

        val after = db.taskDao().getTasksForDay(DATE)
        assertEquals(listOf(0, 1, 2), after.map { it.taskPageIndex }.distinct())
        assertEquals(pages, after.map { it.pageId }.distinct())
        assertEquals(30, after.size)
    }

    @Test
    fun textBoxesFollowTheirRow() = runBlocking {
        val pages = seedDay(db)
        // A text box pasted on page 2, row 2 (slot 12)
        val box = TextAnnotation(content = "call", x = 300f, y = rowTop(2) + 10f, width = 200f, height = 60f, pageId = pages[1])
        db.textAnnotationDao().insert(box)

        assertNotNull(expand(pages))
        val moved = db.textAnnotationDao().getById(box.id)!!
        assertEquals(pages[0], moved.pageId)
        assertEquals(rowTop(12) + 10f, moved.y, 0.01f)

        assertNotNull(collapse(pages))
        val back = db.textAnnotationDao().getById(box.id)!!
        assertEquals(pages[1], back.pageId)
        assertEquals(box.y, back.y, 0.01f)
    }

    @Test
    fun aDayWhoseRowsDontFitIsLeftAlone() {
        val pages = seedDay(db)
        db.taskDao().insertAll(listOf(Task(dayDate = DATE, taskPageIndex = 0, rowIndex = 12, pageId = pages[0])))
        val before = db.taskDao().getTasksForDay(DATE)

        assertNull(expand(pages))

        assertEquals(before, db.taskDao().getTasksForDay(DATE))
    }

    private fun expand(pages: List<String>) = TaskPagination.reflowDay(
        db, DATE, 10, 17, ROW_HEIGHT, TOP, pages, minPages = 1, deleteUnused = false
    )

    private fun collapse(pages: List<String>) = TaskPagination.reflowDay(
        db, DATE, 17, 10, ROW_HEIGHT, TOP, pages, minPages = pages.size, deleteUnused = true
    )

    private fun taskTitled(title: String) = db.taskDao().getTasksForDay(DATE).first { it.typedText == title }

    /** Every task row with content: where it is, by ID (blank placeholder rows may be renewed). */
    private fun slots(): Map<String, Task> =
        db.taskDao().getTasksForDay(DATE).filter { it.typedText != null }.associateBy { it.id }

    private fun allInk(pages: List<String>): Map<String, Stroke> =
        pages.flatMap { db.strokeDao().getAllForPage(it) }.associateBy { it.id }

    private fun inkInRow(pageId: String, row: Int, rows: Int): List<Stroke> =
        db.strokeDao().getAllForPage(pageId).filter { getDominantRowIndex(it, TOP + rows * ROW_HEIGHT, rows, TOP) == row }

    companion object {
        const val DATE = "2026-10-22"
        const val ROW_HEIGHT = 138.3725f  // not whole hundredths: moves are rounded to 0.01 px
        const val TOP = 2.4f
        val TITLES = ('A'..'O').map { it.toString() }

        fun rowTop(row: Int) = TOP + row * ROW_HEIGHT

        /** A stroke written from [from] to [to] of a row's height (more than 1 reaches into the next row). */
        fun strokeInRow(pageId: String, row: Int, from: Float = 0.2f, to: Float = 0.7f): Stroke {
            val y1 = rowTop(row) + from * ROW_HEIGHT
            val y2 = rowTop(row) + to * ROW_HEIGHT
            return Stroke(
                size = 3f, pen = Pen.BALLPEN, top = y1, bottom = y2, left = 300f, right = 500f,
                points = listOf(StrokePoint(300f, y1), StrokePoint(400f, (y1 + y2) / 2f), StrokePoint(500f, y2)),
                pageId = pageId
            )
        }

        /**
         * A day on pages of 10 rows: the first [titles] slots hold A, B, C… (typed, with one stroke
         * each); the other slots are blank rows. Returns the drawing pages in order.
         */
        fun seedDay(db: AppDatabase, pageCount: Int = 2, titles: Int = 15): List<String> {
            val pages = List(pageCount) { Page() }.onEach { db.pageDao().create(it) }
            val tasks = (0 until pageCount * 10).map { slot ->
                Task(
                    dayDate = DATE,
                    taskPageIndex = slot / 10,
                    rowIndex = slot % 10,
                    pageId = pages[slot / 10].id,
                    typedText = if (slot < titles) ('A' + slot).toString() else null
                )
            }
            db.taskDao().insertAll(tasks)
            tasks.filter { it.typedText != null }.forEach { db.strokeDao().create(strokeInRow(it.pageId, it.rowIndex)) }
            return pages.map { it.id }
        }
    }
}
