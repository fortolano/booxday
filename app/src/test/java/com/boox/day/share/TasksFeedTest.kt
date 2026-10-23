package com.boox.day.share

import android.app.Application
import android.content.pm.ProviderInfo
import android.database.ContentObserver
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Page
import com.boox.day.data.db.Task
import com.boox.day.tasks.TaskPaginationTest.Companion.ROW_HEIGHT
import com.boox.day.tasks.TaskPaginationTest.Companion.TOP
import com.boox.day.tasks.TaskPaginationTest.Companion.strokeInRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The read-only tasks feed (share/): a task that is only handwriting is in the feed (review of PR #2:
 * it was left out until handwriting recognition gave it text, and for good if it couldn't), by the
 * app's own rule for ink in a row; and readers hear about changes written straight to the database,
 * like handwriting recognition's. Robolectric, the app's own database, the Go 10.3's row geometry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TasksFeedTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private val today = LocalDate.now().toString()
    private val settingsBefore = GlobalAppSettings.current

    @Before
    fun setUp() {
        db = AppDatabase.getDatabase(context)
        GlobalAppSettings.update(settingsBefore.copy(tasksInkCanvasHeight = TOP + 10 * ROW_HEIGHT, tasksInkTopOffset = TOP))
    }

    @After
    fun tearDown() {
        GlobalAppSettings.update(settingsBefore)
        AppDatabase.closeDatabase()
    }

    @Test
    fun aTaskThatIsOnlyHandwritingIsInTheFeed() {
        val page = seedToday()
        // Row 1: two words of ink, no text. Row 3: one stroke centred in row 3 that reaches into row 4.
        db.strokeDao().create(strokeInRow(page, row = 1, from = 0.2f, to = 0.6f))
        db.strokeDao().create(strokeInRow(page, row = 1, from = 0.3f, to = 0.7f))
        db.strokeDao().create(strokeInRow(page, row = 3, from = 0.4f, to = 1.3f))

        val rows = query("today")

        // A (typed), row 1 and row 3 (handwriting only); row 4, which only the overflow reaches, is not a task
        assertEquals(listOf("A", "Handwritten task", "Handwritten task"), rows.map { it.title })
        assertEquals(listOf(0, 1, 1), rows.map { it.handwrittenOnly })
        val idOfRow = db.taskDao().getTasksForDay(today).associate { it.rowIndex to it.id }
        assertEquals(listOf(idOfRow[0], idOfRow[1], idOfRow[3]), rows.map { it.taskId })
        // The limit counts handwritten rows too
        assertEquals(2, query("open", limit = 2).size)
    }

    @Test
    fun withoutTasksInkGeometryOnlyTextCounts() {
        GlobalAppSettings.update(settingsBefore.copy(tasksInkCanvasHeight = 0f, tasksInkTopOffset = 0f))
        val page = seedToday()
        db.strokeDao().create(strokeInRow(page, row = 1))
        db.strokeDao().create(strokeInRow(page, row = 1, from = 0.3f, to = 0.8f))

        assertEquals(listOf("A"), query("today").map { it.title })
    }

    @Test
    fun readersHearOfTextWrittenStraightToTheDatabase() {
        seedToday()
        val heard = CountDownLatch(1)
        context.contentResolver.registerContentObserver(TasksFeedContract.BASE_URI, true, object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean, uri: Uri?) = heard.countDown()
        })
        val scope = CoroutineScope(SupervisorJob())
        try {
            TasksFeedNotifier.start(context, scope)
            Thread.sleep(300)  // the notifier subscribes on a background thread
            // What handwriting recognition does (TaskSemanticBridge, TaskOcrService): no TaskRepository event
            val row2 = db.taskDao().getTasksForDay(today).first { it.rowIndex == 2 }
            db.taskDao().setRecognizedTextById(row2.id, "Call the bank")

            assertTrue("feed readers notified", heard.await(5, TimeUnit.SECONDS))
            assertEquals(listOf("A", "Call the bank"), query("today").map { it.title })
        } finally {
            scope.cancel()
        }
    }

    private class Row(val taskId: String, val title: String, val handwrittenOnly: Int)

    private fun query(path: String, limit: Int? = null): List<Row> {
        val provider = Robolectric.buildContentProvider(TasksFeedProvider::class.java)
            .create(ProviderInfo().apply { authority = TasksFeedContract.AUTHORITY }).get()
        val uri = Uri.withAppendedPath(TasksFeedContract.BASE_URI, path)
            .let { if (limit != null) it.buildUpon().appendQueryParameter("limit", "$limit").build() else it }
        return provider.query(uri, null, null, null, null)!!.use { c ->
            List(c.count) {
                c.moveToNext()
                Row(
                    c.getString(c.getColumnIndexOrThrow(TasksFeedContract.TASK_ID)),
                    c.getString(c.getColumnIndexOrThrow(TasksFeedContract.TITLE)),
                    c.getInt(c.getColumnIndexOrThrow(TasksFeedContract.HANDWRITTEN_ONLY))
                )
            }
        }
    }

    /** Today's Tasks page: row 0 typed "A", the other 9 rows blank. Returns the drawing page. */
    private fun seedToday(): String {
        val page = Page().also { db.pageDao().create(it) }
        db.taskDao().insertAll((0 until 10).map { row ->
            Task(dayDate = today, taskPageIndex = 0, rowIndex = row, pageId = page.id, typedText = if (row == 0) "A" else null)
        })
        return page.id
    }
}
