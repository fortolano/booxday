package com.boox.day.tasks

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.tasks.TaskPaginationTest.Companion.DATE
import com.boox.day.tasks.TaskPaginationTest.Companion.ROW_HEIGHT
import com.boox.day.tasks.TaskPaginationTest.Companion.TITLES
import com.boox.day.tasks.TaskPaginationTest.Companion.TOP
import com.boox.day.tasks.TaskPaginationTest.Companion.seedDay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Full screen Tasks (Oct 2026): the reflow writes its record in the same transaction, and the next
 * start puts a day left on full screen pages (app killed in full screen) back on pages of 10.
 * Uses the app's own database (AppDatabase.getDatabase) and the page write queue, like the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TaskPaginationRecordTest {

    @After
    fun tearDown() {
        runBlocking { TaskPagination.collapse() }
        AppDatabase.closeDatabase()
    }

    @Test
    fun aDayLeftOnFullScreenPagesIsPutBackAtStart() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        TaskPagination.init(context)
        val db = AppDatabase.getDatabase(context)
        val pages = seedDay(db)
        val before = db.taskDao().getTasksForDay(DATE).filter { it.typedText != null }.associateBy { it.id }

        assertTrue(TaskPagination.expand(DATE, rows = 17, normalHeight = TOP + 10 * ROW_HEIGHT, topOffset = TOP))
        assertTrue(TaskPagination.isReflowed(DATE))
        assertEquals(17, TaskPagination.rowsPerPage(DATE))
        assertNotNull("record written with the reflow", db.kvDao().get("tasks_full_screen_day"))
        assertEquals(TITLES, db.taskDao().getTasksForPage(DATE, 0).mapNotNull { it.typedText })

        // The app is killed in full screen; the next start runs this before any screen
        TaskPagination.restoreAtStartup()

        assertNull(TaskPagination.current)
        assertFalse(TaskPagination.isReflowed(DATE))
        assertNull("record removed", db.kvDao().get("tasks_full_screen_day"))
        val after = db.taskDao().getTasksForDay(DATE)
        assertEquals(pages, after.map { it.pageId }.distinct())
        assertEquals(20, after.size)
        assertEquals(before, after.filter { it.typedText != null }.associateBy { it.id })
    }
}
