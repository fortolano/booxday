package com.boox.day.sync

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.TaskSyncLink
import com.boox.day.data.db.TaskSyncList
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.data.db.TaskSyncSchema
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
 * Task sync links in backups: they come back with their tasks, a restore never leaves links or
 * outbox entries describing the old rows, and the device's pull cursors start over.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TaskSyncBackupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(TaskSyncSchema.CALLBACK)
            .allowMainThreadQueries()
            .build()
        val sql = db.openHelper.writableDatabase
        sql.execSQL(
            "INSERT INTO Page (id, scroll, notebookId, background, backgroundType, parentFolderId, linkedEventId, createdAt, updatedAt) " +
                "VALUES ('page-1', 0, NULL, 'blank', 'native', NULL, NULL, 1, 1)"
        )
        sql.execSQL(
            "INSERT INTO task (id, dayDate, taskPageIndex, rowIndex, pageId, isCompleted, priority, typedText, " +
                "migrationCount, createdAt, updatedAt) VALUES ('t-1', '2026-10-30', 0, 0, 'page-1', 0, '', 'Call mom', 0, 1, 1)"
        )
        db.taskSyncDao().insertLink(
            TaskSyncLink(
                taskId = "t-1", provider = TaskSyncProvider.GOOGLE_TASKS, accountEmail = "me@gmail.com",
                listId = "list-1", remoteId = "g-task-1", syncedTitle = "Call mom", syncedAt = 5
            )
        )
        sql.execSQL(
            "INSERT INTO task_sync_outbox (op, taskId, createdAt, attempts, nextAttemptAt) VALUES ('UPSERT', 't-1', 1, 0, 0)"
        )
        sql.execSQL(
            "INSERT INTO task_sync_list (provider, accountEmail, serverUrl, listId, title, isDefaultForNew, pullCursor, lastFullCheckAt) " +
                "VALUES ('${TaskSyncProvider.GOOGLE_TASKS}', 'me@gmail.com', '', 'list-1', 'BooxDay test', 1, '2026-10-30T10:00:00Z', 9)"
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun linksComeBackWithTheirTasks() = runBlocking {
        val backup = exportBackup()
        assertEquals(1, backup.getValue("taskSyncLinks").jsonArray.size)

        val result = ImportService(context) { db }.restoreBackup(backup.toString())

        assertTrue(result.toString(), result is ImportService.ImportResult.Success)
        val link = db.taskSyncDao().getLink("t-1")
        assertEquals("g-task-1", link?.remoteId)
        assertEquals("Call mom", link?.syncedTitle)
        assertTrue("the outbox described the old rows", db.taskSyncDao().getOutbox().isEmpty())
        assertNoCursors()
    }

    @Test
    fun anOlderBackupWithoutLinksLeavesNoStaleLinks() = runBlocking {
        val withoutLinks = JsonObject(exportBackup().filterKeys { it != "taskSyncLinks" })

        val result = ImportService(context) { db }.restoreBackup(withoutLinks.toString())

        assertTrue(result.toString(), result is ImportService.ImportResult.Success)
        assertNull("restored tasks are unlinked; the next sync re-links them by matching", db.taskSyncDao().getLink("t-1"))
        assertTrue(db.taskSyncDao().getOutbox().isEmpty())
        assertTrue("the restore queued no remote deletes", db.taskSyncDao().getOutbox().none { it.op == "DELETE" })
        assertNoCursors()
        assertEquals("the ticked lists stay (device-only)", listOf("list-1"), db.taskSyncDao().getLists().map { it.listId })
    }

    @Test
    fun twoLinksToTheSameRemoteTaskAreRefusedAndNothingChanges() = runBlocking {
        val backup = exportBackup()
        val link = backup.getValue("taskSyncLinks").jsonArray.single().jsonObject
        val twin = JsonObject(link + ("taskId" to kotlinx.serialization.json.JsonPrimitive("t-2")))
        val broken = JsonObject(backup + ("taskSyncLinks" to JsonArray(listOf(link, twin))))

        val result = ImportService(context) { db }.restoreBackup(broken.toString())

        assertTrue(result is ImportService.ImportResult.Error)
        assertEquals("g-task-1", db.taskSyncDao().getLink("t-1")?.remoteId)
        assertEquals(1, db.taskSyncDao().getOutbox().size)
        assertEquals("2026-10-30T10:00:00Z", db.taskSyncDao().getLists().single().pullCursor)
    }

    private suspend fun exportBackup(): JsonObject {
        val result = ExportService(context) { db }.createBackup()
        check(result is ExportService.ExportResult.Success) { "export failed: $result" }
        return Json.parseToJsonElement(result.backupFile.readText()).jsonObject
    }

    private fun assertNoCursors() {
        val list: TaskSyncList = db.taskSyncDao().getLists().single()
        assertNull(list.pullCursor)
        assertNull(list.lastFullCheckAt)
    }
}
