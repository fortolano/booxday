package com.boox.day.tasks

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.PagePersistenceWriter
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Attachment
import com.boox.day.data.db.Image
import com.boox.day.data.db.Page
import com.boox.day.tasks.TaskPaginationTest.Companion.DATE
import com.boox.day.tasks.TaskPaginationTest.Companion.ROW_HEIGHT
import com.boox.day.tasks.TaskPaginationTest.Companion.TITLES
import com.boox.day.tasks.TaskPaginationTest.Companion.TOP
import com.boox.day.tasks.TaskPaginationTest.Companion.seedDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

    @Test
    fun cancellingCallerDuringExpandStillPublishesCurrentAndAllowsCollapse() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        TaskPagination.init(context)
        val db = AppDatabase.getDatabase(context)
        seedDay(db)

        // Hold the write queue while the caller coroutine cancels mid-expand
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        PagePersistenceWriter.submit("test barrier") {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))

        val callerJob = launch(Dispatchers.Default) {
            TaskPagination.expand(DATE, rows = 17, normalHeight = TOP + 10 * ROW_HEIGHT, topOffset = TOP)
        }
        // Give expand a moment to enter reflowLock and submit to PagePersistenceWriter
        Thread.sleep(100)
        callerJob.cancel()
        callerJob.join()

        release.countDown()

        // collapse() serializes behind the accepted expand() on reflowLock: it must see current != null
        // and put the day cleanly back on 10-row pages without leaving changingDate stuck.
        assertTrue(TaskPagination.collapse())
        assertNull(TaskPagination.current)
        assertFalse(TaskPagination.isReflowed(DATE))
        assertNull(db.kvDao().get("tasks_full_screen_day"))
        assertEquals(10, db.taskDao().getTasksForPage(DATE, 0).size)
    }

    @Test
    fun reflowDeleteUnusedKeepsPagesHoldingImagesOrAttachments() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        TaskPagination.init(context)
        val db = AppDatabase.getDatabase(context)
        val pages = seedDay(db)
        val imgPage = Page(id = "img-page").also { db.pageDao().create(it) }.id
        val attPage = Page(id = "att-page").also { db.pageDao().create(it) }.id
        db.ImageDao().create(Image(id = "img-1", x = 0, y = 0, width = 100, height = 100, uri = "file:///tmp/a.png", pageId = imgPage))
        db.attachmentDao().create(
            Attachment(id = "att-1", x = 0, y = 0, width = 100, height = 100, filePath = "/tmp/doc.pdf", displayName = "doc.pdf", fileType = "pdf", pageId = attPage)
        )

        val outcome = db.runInTransaction<TaskPagination.Outcome?> {
            TaskPagination.reflowDay(
                db = db,
                date = DATE,
                fromRows = 10,
                toRows = 17,
                rowHeight = ROW_HEIGHT,
                topOffset = TOP,
                preferredPageIds = pages + listOf(imgPage, attPage),
                minPages = 1,
                deleteUnused = true
            )
        }

        assertNotNull(outcome)
        assertFalse("image page must not be deleted", imgPage in outcome!!.deletedPageIds)
        assertFalse("attachment page must not be deleted", attPage in outcome.deletedPageIds)
        assertTrue("image must survive reflow", db.ImageDao().hasImagesForPage(imgPage))
        assertTrue("attachment must survive reflow", db.attachmentDao().hasAttachmentsForPage(attPage))
    }
}
