package com.boox.day.tasksync

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.APP_DATABASE_FILE_NAME
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.TaskRepository
import com.boox.day.data.db.TaskSyncDao
import com.boox.day.data.db.TaskSyncKept
import com.boox.day.data.db.TaskSyncLink
import com.boox.day.data.db.TaskSyncOp
import com.boox.day.data.db.TaskSyncOutboxEntry
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.data.getDbDir
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** What the Task Sync card does (plan step 2.6; owner decisions 30-32). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TaskSyncSetupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repo: TaskRepository
    private lateinit var db: AppDatabase
    private lateinit var sync: TaskSyncDao
    private val service = FakeService()
    private val provider = TaskSyncProvider.GOOGLE_TASKS

    @Before
    fun setUp() {
        deleteDatabaseFiles()
        TaskSync.prefs(context).edit().clear().commit()
        repo = TaskRepository(context)
        repo.invalidateTaskCache()
        db = AppDatabase.getDatabase(context)
        sync = db.taskSyncDao()
    }

    @After
    fun tearDown() {
        TaskSync.turnOff(context)
        TaskSync.prefs(context).edit().clear().commit()
        repo.invalidateTaskCache()
        AppDatabase.closeDatabase()
        deleteDatabaseFiles()
    }

    @Test
    fun turningOnTicksGooglesMainListForNewTasks() = runBlocking {
        TaskSync.turnOn(context, EMAIL, service, today = TODAY)

        val lists = sync.getListsFor(provider, EMAIL, "")
        assertEquals(listOf("main"), lists.map { it.listId })
        assertTrue("new tasks go to it (owner decision 30)", lists.single().isDefaultForNew)
        assertTrue(TaskSync.isOn(context))
        assertEquals(EMAIL, TaskSync.account(context))
    }

    @Test
    fun turningOnAgainKeepsTheListsYouChose() = runBlocking {
        TaskSync.turnOn(context, EMAIL, service, today = TODAY)
        TaskSync.tickList(context, provider, EMAIL, "work", "Work", ticked = true)
        TaskSync.newTasksTo(context, provider, EMAIL, "work")
        TaskSync.tickList(context, provider, EMAIL, "main", "My Tasks", ticked = false)
        TaskSync.turnOff(context)

        TaskSync.turnOn(context, EMAIL, service, today = TODAY)

        assertEquals(listOf("work"), sync.getListsFor(provider, EMAIL, "").map { it.listId })
        assertEquals("work", sync.getDefaultList(provider, EMAIL, "")?.listId)
    }

    @Test
    fun untickingTheNewTasksListPassesItOn() {
        TaskSync.tickList(context, provider, EMAIL, "main", "My Tasks", ticked = true)
        TaskSync.tickList(context, provider, EMAIL, "work", "Work", ticked = true)
        assertEquals("the first ticked list gets new tasks", "main", sync.getDefaultList(provider, EMAIL, "")?.listId)

        TaskSync.tickList(context, provider, EMAIL, "main", "My Tasks", ticked = false)

        assertEquals("work", sync.getDefaultList(provider, EMAIL, "")?.listId)
    }

    @Test
    fun switchingAccountTreatsTheOldAccountsTasksAsNeverSynced() = runBlocking {
        // Owner decision 32
        val id = repo.getOrCreateTasksForPage(TODAY, 0).first[0].id
        repo.setTypedText(id, "Call mom")
        sync.insertLink(TaskSyncLink(taskId = id, provider = provider, accountEmail = "old@gmail.com", listId = "l", remoteId = "r", syncedAt = 1))
        TaskSync.tickList(context, provider, "old@gmail.com", "l", "Old list", ticked = true)
        sync.insertOutboxEntry(
            TaskSyncOutboxEntry(op = TaskSyncOp.DELETE, taskId = "x", provider = provider, accountEmail = "old@gmail.com",
                serverUrl = "", listId = "l", remoteId = "r-x", createdAt = 1)
        )

        TaskSync.turnOn(context, EMAIL, service, today = TODAY)

        assertNull(sync.getLink(id))
        assertTrue(sync.getListsFor(provider, "old@gmail.com", "").isEmpty())
        assertTrue(sync.getOutbox().none { it.op == TaskSyncOp.DELETE })
        TaskSync.scanner(context).scan(System.currentTimeMillis(), TODAY)
        assertEquals("it goes to the new account as new", listOf(id), sync.getOutbox().map { it.taskId })
    }

    @Test
    fun turningOnKeepsTodaysTickedTasksOnTheTablet() = runBlocking {
        val id = repo.getOrCreateTasksForPage(TODAY, 0).first[1].id
        repo.setTypedText(id, "Pay rent")
        repo.setCompleted(id, true)

        TaskSync.turnOn(context, EMAIL, service, today = TODAY)

        assertEquals(TaskSyncKept.TICKED_AT_TURN_ON, sync.getLink(id)?.keptReason)
    }

    @Test
    fun theSettingsDotSaysWhenTaskSyncNeedsYou() = runBlocking {
        TaskSync.turnOn(context, EMAIL, service, today = TODAY)
        assertNull(TaskSync.attentionReason(context))

        TaskSync.recordRun(context, TaskSyncRun.Result.NEEDS_RECONNECT)
        assertEquals("Task Sync needs you to sign in again", TaskSync.attentionReason(context))

        TaskSync.recordRun(context, TaskSyncRun.Result.DONE)
        assertNull(TaskSync.attentionReason(context))
        assertTrue(TaskSync.status(context).lastSyncedAt != null)

        TaskSync.turnOff(context)
        TaskSync.recordRun(context, TaskSyncRun.Result.NEEDS_RECONNECT)
        assertNull("off: nothing needs you", TaskSync.attentionReason(context))
    }

    @Test
    fun privateModeRunsNothing() = runBlocking {
        // Cloud Sync only: the saved settings here are the defaults (Private Mode)
        TaskSync.turnOn(context, EMAIL, service, today = TODAY)

        assertEquals(TaskSyncRun.Result.OFF, TaskSyncRun.run(context))
        assertEquals("not counted as a run", 0L, TaskSync.lastRunAt(context))
    }

    private class FakeService : TaskSyncService {
        override val provider = TaskSyncProvider.GOOGLE_TASKS
        override suspend fun lists() = listOf(RemoteList("main", "My Tasks"), RemoteList("work", "Work"))
        override suspend fun mainList() = RemoteList("main", "My Tasks")
        override suspend fun upsert(task: OutgoingTask, link: TaskSyncLink?): RemoteTask = error("not sent here")
        override suspend fun delete(listId: String, remoteId: String) = error("not sent here")
    }

    private fun deleteDatabaseFiles() {
        val file = File(getDbDir(context), APP_DATABASE_FILE_NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
    }

    private companion object {
        const val EMAIL = "me@gmail.com"
        const val TODAY = "2026-11-02"
    }
}
