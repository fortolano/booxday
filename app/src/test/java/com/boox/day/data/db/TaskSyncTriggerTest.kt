package com.boox.day.data.db

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The row-deleted trigger (TaskSyncSchema): a linked task row that is deleted, by any statement or
 * by its page cascading away, leaves a DELETE for the service's copy in the outbox and no link.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TaskSyncTriggerTest {

    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var taskSync: TaskSyncDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(TaskSyncSchema.CALLBACK)
            .allowMainThreadQueries()
            .build()
        sql = db.openHelper.writableDatabase
        taskSync = db.taskSyncDao()
        insertPage("page-1")
        insertPage("page-2")
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deletingALinkedRowQueuesTheRemoteDelete() {
        insertTask("t-1", "page-1", row = 0, text = "Call mom")
        taskSync.insertLink(link("t-1", remoteId = "g-task-1"))
        sql.execSQL(
            "INSERT INTO task_sync_outbox (op, taskId, createdAt, attempts, nextAttemptAt) VALUES ('UPSERT', 't-1', 1, 0, 0)"
        )

        sql.execSQL("DELETE FROM task WHERE id = 't-1'")

        assertNull("the link goes with the row", taskSync.getLink("t-1"))
        val outbox = taskSync.getOutbox()
        assertEquals("only the DELETE is left; the UPSERT for the row is dropped", 1, outbox.size)
        with(outbox.single()) {
            assertEquals(TaskSyncOp.DELETE, op)
            assertEquals("t-1", taskId)
            assertEquals(TaskSyncProvider.GOOGLE_TASKS, provider)
            assertEquals("me@gmail.com", accountEmail)
            assertEquals("list-1", listId)
            assertEquals("g-task-1", remoteId)
            assertTrue(createdAt > 0)
        }
    }

    @Test
    fun deletingAnUnlinkedRowQueuesNothing() {
        insertTask("t-2", "page-1", row = 1, text = "Buy bread")

        sql.execSQL("DELETE FROM task WHERE id = 't-2'")

        assertTrue(taskSync.getOutbox().isEmpty())
    }

    @Test
    fun aPageCascadingAwayQueuesItsLinkedRows() {
        insertTask("t-3", "page-2", row = 0, text = "Send mockups")
        insertTask("t-4", "page-2", row = 1, text = "Not synced")
        taskSync.insertLink(link("t-3", remoteId = "g-task-3"))

        sql.execSQL("DELETE FROM Page WHERE id = 'page-2'")  // task.pageId is ON DELETE CASCADE

        sql.query("SELECT COUNT(*) FROM task WHERE pageId = 'page-2'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("the cascade ran", 0, c.getInt(0))
        }
        assertEquals(listOf("g-task-3"), taskSync.getOutbox().map { it.remoteId })
        assertTrue(taskSync.getAllLinks().isEmpty())
    }

    private fun insertPage(id: String) {
        sql.execSQL(
            "INSERT INTO Page (id, scroll, notebookId, background, backgroundType, parentFolderId, linkedEventId, createdAt, updatedAt) " +
                "VALUES (?, 0, NULL, 'blank', 'native', NULL, NULL, 1, 1)",
            arrayOf<Any?>(id)
        )
    }

    private fun insertTask(id: String, pageId: String, row: Int, text: String) {
        sql.execSQL(
            "INSERT INTO task (id, dayDate, taskPageIndex, rowIndex, pageId, isCompleted, priority, typedText, " +
                "migrationCount, createdAt, updatedAt) VALUES (?, ?, 0, ?, ?, 0, '', ?, 0, 1, 1)",
            arrayOf<Any?>(id, "2026-10-30-$pageId", row, pageId, text)
        )
    }

    private fun link(taskId: String, remoteId: String) = TaskSyncLink(
        taskId = taskId,
        provider = TaskSyncProvider.GOOGLE_TASKS,
        accountEmail = "me@gmail.com",
        listId = "list-1",
        remoteId = remoteId,
        syncedAt = 1
    )
}
