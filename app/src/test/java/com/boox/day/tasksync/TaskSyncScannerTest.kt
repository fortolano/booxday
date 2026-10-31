package com.boox.day.tasksync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Page
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskSyncLink
import com.boox.day.data.db.TaskSyncOp
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.data.db.TaskSyncSchema
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

/** What the change scan queues, marks or deletes (Groundwork 1.8; owner decisions 9 and 21). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TaskSyncScannerTest {

    private lateinit var db: AppDatabase
    private var ink: Boolean? = true
    private lateinit var scanner: TaskSyncScanner

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(TaskSyncSchema.CALLBACK)
            .allowMainThreadQueries()
            .build()
        db.pageDao().create(Page(id = "page-1"))
        db.pageDao().create(Page(id = "page-0"))
        scanner = TaskSyncScanner(db) { { ink } }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun aNewTypedTaskFromTodayOnIsQueuedOnce() {
        insert("t-1", TODAY, row = 0, typed = "Call mom")
        insert("t-old", YESTERDAY, row = 0, typed = "Old task", pageId = "page-0")
        insert("t-blank", TODAY, row = 1)

        scanner.scan(NOW, TODAY)
        scanner.scan(NOW + 1_000, TODAY)

        assertEquals(listOf(TaskSyncOp.UPSERT to "t-1"), outbox())
    }

    @Test
    fun handwritingWaitsUntilItsWordsSettle() {
        insert("t-ink", TODAY, row = 0, recognized = "Buy brea")

        val first = scanner.scan(NOW, TODAY)
        assertTrue(outbox().isEmpty())
        assertEquals(NOW + TaskSyncRules.HANDWRITING_STABLE_MS, first.rescanAt)

        // Recognition read the row again and the words changed: the minute starts again
        db.taskDao().setRecognizedTextById("t-ink", "Buy bread")
        scanner.scan(NOW + 30_000, TODAY)
        scanner.scan(NOW + 70_000, TODAY)
        assertTrue("only 40 s with these words", outbox().isEmpty())

        scanner.scan(NOW + 90_001, TODAY)
        assertEquals(listOf(TaskSyncOp.UPSERT to "t-ink"), outbox())
    }

    @Test
    fun aLinkedTaskIsQueuedOnlyWhenItsSyncedFieldsChange() {
        val task = insert("t-1", TODAY, row = 0, typed = "Call mom")
        link("t-1", "g-1", TaskSyncRules.contentHash(task, null))

        scanner.scan(NOW, TODAY)
        assertTrue("nothing changed", outbox().isEmpty())

        db.taskDao().setPriority("t-1", "A")  // not synced
        scanner.scan(NOW + 1_000, TODAY)
        assertTrue(outbox().isEmpty())

        db.taskDao().setCompleted("t-1", true)
        scanner.scan(NOW + 2_000, TODAY)
        assertEquals(listOf(TaskSyncOp.UPSERT to "t-1"), outbox())
    }

    @Test
    fun aRowThatLostItsWordsButKeptItsInkIsOnlyOrphaned() {
        val task = insert("t-ink", TODAY, row = 0, recognized = "Pay rent")
        link("t-ink", "g-rent", TaskSyncRules.contentHash(task, null))
        ink = true

        db.taskDao().setRecognizedTextById("t-ink", null)  // recognition failed this time
        scanner.scan(NOW, TODAY)
        scanner.scan(NOW + 10 * 60_000, TODAY)

        assertTrue(outbox().isEmpty())
        assertNotNull(db.taskSyncDao().getLink("t-ink")?.orphanedAt)

        db.taskDao().setRecognizedTextById("t-ink", "Pay rent")
        scanner.scan(NOW + 11 * 60_000, TODAY)
        assertNull("words back: no longer an orphan", db.taskSyncDao().getLink("t-ink")?.orphanedAt)
    }

    @Test
    fun anErasedTaskIsDeletedAfterTwoMinutes() {
        val task = insert("t-ink", TODAY, row = 0, recognized = "Pay rent")
        link("t-ink", "g-rent", TaskSyncRules.contentHash(task, null))
        db.taskDao().setRecognizedTextById("t-ink", null)
        ink = false

        val first = scanner.scan(NOW, TODAY)
        assertEquals(NOW + TaskSyncRules.EMPTY_ROW_DELETE_MS, first.rescanAt)
        scanner.scan(NOW + 119_000, TODAY)
        assertTrue("still within the two minutes", outbox().isEmpty())

        val later = scanner.scan(NOW + 120_000, TODAY)
        assertEquals(1, later.deleted)
        assertEquals(listOf(TaskSyncOp.DELETE to "g-rent"), db.taskSyncDao().getOutbox().map { it.op to it.remoteId })
        assertNull(db.taskSyncDao().getLink("t-ink"))
    }

    @Test
    fun anErasedTaskWrittenAgainInTimeIsKept() {
        val task = insert("t-ink", TODAY, row = 0, recognized = "Pay rent")
        link("t-ink", "g-rent", TaskSyncRules.contentHash(task, null))
        db.taskDao().setRecognizedTextById("t-ink", null)
        ink = false
        scanner.scan(NOW, TODAY)

        ink = true  // Undo, or written again
        scanner.scan(NOW + 60_000, TODAY)
        ink = false
        scanner.scan(NOW + 150_000, TODAY)

        assertTrue("the two minutes started again when the row emptied again", outbox().isEmpty())
        assertNotNull(db.taskSyncDao().getLink("t-ink"))
    }

    @Test
    fun whenInkCantBeCheckedNothingIsDeleted() {
        val task = insert("t-ink", TODAY, row = 0, recognized = "Pay rent")
        link("t-ink", "g-rent", TaskSyncRules.contentHash(task, null))
        db.taskDao().setRecognizedTextById("t-ink", null)
        ink = null  // Tasks never shown: no row geometry yet

        scanner.scan(NOW, TODAY)
        scanner.scan(NOW + 60 * 60_000, TODAY)

        assertTrue(outbox().isEmpty())
        assertNotNull(db.taskSyncDao().getLink("t-ink"))
    }

    private fun insert(
        id: String,
        day: String,
        row: Int,
        typed: String? = null,
        recognized: String? = null,
        pageId: String = "page-1"
    ): Task {
        val task = Task(id = id, dayDate = day, taskPageIndex = 0, rowIndex = row, pageId = pageId, typedText = typed, recognizedText = recognized)
        db.taskDao().insertAll(listOf(task))
        return task
    }

    private fun link(taskId: String, remoteId: String, hash: String) {
        db.taskSyncDao().insertLink(
            TaskSyncLink(
                taskId = taskId, provider = TaskSyncProvider.GOOGLE_TASKS, accountEmail = "me@gmail.com",
                listId = "list-1", remoteId = remoteId, contentHash = hash, syncedAt = 1
            )
        )
    }

    private fun outbox() = db.taskSyncDao().getOutbox().map { it.op to it.taskId }

    private companion object {
        const val TODAY = "2026-11-02"
        const val YESTERDAY = "2026-11-01"
        const val NOW = 1_800_000_000_000L
    }
}
