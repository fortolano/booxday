package com.boox.day.tasksync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Page
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskSyncOp
import com.boox.day.data.db.TaskSyncSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The watcher runs the scan when the task table changes, whoever wrote it, and waits while the pen
 * is on the page. Real Room invalidation; short waits instead of the app's 5 s and 60 s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TaskSyncWatcherTest {

    private lateinit var db: AppDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var penDown = false
    private lateinit var watcher: TaskSyncWatcher

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(TaskSyncSchema.CALLBACK)
            .allowMainThreadQueries()  // as the app's database
            .build()
        db.pageDao().create(Page(id = "page-1"))
        db.taskDao().insertAll(listOf(
            Task(id = "t-1", dayDate = TODAY, taskPageIndex = 0, rowIndex = 0, pageId = "page-1"),
            Task(id = "t-2", dayDate = TODAY, taskPageIndex = 0, rowIndex = 1, pageId = "page-1")
        ))
        watcher = TaskSyncWatcher(
            db, scope, TaskSyncScanner(db) { { true } },
            today = { TODAY }, debounceMs = 50L, inkQuietMs = 0L, penActive = { penDown }
        )
    }

    @After
    fun tearDown() {
        watcher.stop()
        scope.cancel()
        db.close()
    }

    @Test
    fun aTypedTaskIsQueuedWithoutAnyoneAsking() {
        watcher.start()

        db.taskDao().setTypedText("t-1", "Call mom")  // straight to the database, like recognition does
        db.taskDao().setRecognizedTextById("t-2", "Buy bread")

        // Handwriting too, once things are quiet: no extra wait for its words (owner, 31 Oct 2026)
        assertTrue("queued within 5 s", waitFor { db.taskSyncDao().getOutbox().size == 2 })
        assertEquals(setOf(TaskSyncOp.UPSERT to "t-1", TaskSyncOp.UPSERT to "t-2"),
            db.taskSyncDao().getOutbox().map { it.op to it.taskId }.toSet())
    }

    @Test
    fun nothingIsLookedAtWhileThePenIsOnThePage() {
        penDown = true
        watcher.start()
        db.taskDao().setTypedText("t-1", "Call mom")

        Thread.sleep(1_500)
        assertTrue("pen down: no scan yet", db.taskSyncDao().getOutbox().isEmpty())

        penDown = false
        assertTrue("pen up: scanned", waitFor { db.taskSyncDao().getOutbox().isNotEmpty() })
    }

    private fun waitFor(timeoutMs: Long = 5_000, check: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (check()) return true
            Thread.sleep(25)
        }
        return check()
    }

    private companion object {
        const val TODAY = "2026-11-02"
    }
}
