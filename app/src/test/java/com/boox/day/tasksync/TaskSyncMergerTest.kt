package com.boox.day.tasksync

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.APP_DATABASE_FILE_NAME
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.TaskDao
import com.boox.day.data.db.TaskRepository
import com.boox.day.data.db.TaskSyncDao
import com.boox.day.data.db.TaskSyncLink
import com.boox.day.data.db.TaskSyncOp
import com.boox.day.data.db.TaskSyncOutboxEntry
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.data.getDbDir
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Bringing the service's changes onto the tablet (task sync step 2.3), through BooxDay's real task
 * operations: moves carry ink and links, clears remove the row, subtask rules apply.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TaskSyncMergerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repo: TaskRepository
    private lateinit var db: AppDatabase
    private lateinit var tasks: TaskDao
    private lateinit var sync: TaskSyncDao
    private lateinit var merger: TaskSyncMerger
    private var canvasHeight = CANVAS_HEIGHT
    private var now = 1_800_000_000_000L

    @Before
    fun setUp() {
        deleteDatabaseFiles()
        repo = TaskRepository(context)
        repo.invalidateTaskCache()
        db = AppDatabase.getDatabase(context)
        tasks = db.taskDao()
        sync = db.taskSyncDao()
        merger = TaskSyncMerger(
            db, RepositoryTabletTasks(context, repo) { canvasHeight }, TaskSyncProvider.GOOGLE_TASKS, EMAIL,
            clock = { now }, today = { DAY }
        )
    }

    @After
    fun tearDown() {
        repo.invalidateTaskCache()
        AppDatabase.closeDatabase()
        deleteDatabaseFiles()
    }

    // ── Words, done mark ──

    @Test
    fun aTaskTickedOnThePhoneIsTickedHereAndNothingGoesBack() = runBlocking {
        val id = synced(row = 0, text = "Call mom", remoteId = "r-1")

        assertEquals(TaskSyncMerger.Result.APPLIED, merger.merge(remote("r-1", "Call mom", completed = true)))

        assertTrue(tasks.getById(id)!!.isCompleted)
        val link = sync.getLink(id)!!
        assertTrue(link.syncedCompleted)
        assertEquals("v2", link.remoteVersion)
        assertNothingWaits()
    }

    @Test
    fun newWordsFromThePhoneReplaceATypedTasksWords() = runBlocking {
        val id = synced(row = 0, text = "Call mom", remoteId = "r-1")

        merger.merge(remote("r-1", "Call mom at 6"))

        assertEquals("Call mom at 6", tasks.getById(id)!!.typedText)
        assertNothingWaits()
    }

    @Test
    fun aHandwrittenTaskKeepsItsWordsAndSendsThemBack() = runBlocking {
        val id = synced(row = 0, text = null, recognized = "Call mom", remoteId = "r-1")

        merger.merge(remote("r-1", "Call dad"))

        val task = tasks.getById(id)!!
        assertEquals("ink wins (owner decision 2)", "Call mom", task.recognizedText)
        assertNull(task.typedText)
        assertEquals("Call dad", sync.getLink(id)!!.syncedTitle)
        assertEquals(listOf(TaskSyncOp.UPSERT to id), sync.getOutbox().map { it.op to it.taskId })
    }

    @Test
    fun changedOnBothThePhonesLaterChangeWins() = runBlocking {
        val id = synced(row = 0, text = "Call mom", remoteId = "r-1")
        repo.setTypedText(id, "Call mom today")
        sync.queueUpsert(id, now - 60_000)

        merger.merge(remote("r-1", "Call mom at 6", modifiedAt = now))

        assertEquals("Call mom at 6", tasks.getById(id)!!.typedText)
        assertTrue("the tablet's older change is dropped", sync.getOutbox().isEmpty())
    }

    @Test
    fun changedOnBothTheTabletsLaterChangeWinsAndGoesBack() = runBlocking {
        val id = synced(row = 0, text = "Call mom", remoteId = "r-1")
        repo.setTypedText(id, "Call mom today")
        sync.queueUpsert(id, now)

        merger.merge(remote("r-1", "Call mom at 6", modifiedAt = now - 60_000))

        assertEquals("Call mom today", tasks.getById(id)!!.typedText)
        assertEquals(listOf(TaskSyncOp.UPSERT to id), sync.getOutbox().map { it.op to it.taskId })
    }

    @Test
    fun ourOwnWriteComingBackChangesNothing() = runBlocking {
        val id = synced(row = 0, text = "Call mom", remoteId = "r-1")

        assertEquals(TaskSyncMerger.Result.UNCHANGED, merger.merge(remote("r-1", "Something else", version = "v1")))

        assertEquals("Call mom", tasks.getById(id)!!.typedText)
    }

    // ── Due days (owner decisions 1, 10, 25) ──

    @Test
    fun aNewDueDayMovesTheTaskToThatDayWithItsLink() = runBlocking {
        val id = synced(row = 2, text = "Send mockups", remoteId = "r-2")

        assertEquals(TaskSyncMerger.Result.APPLIED, merger.merge(remote("r-2", "Send mockups", dueDay = NEXT_DAY)))

        assertNull("the old row is empty", tasks.getById(id)!!.typedText)
        val moved = tasks.getTasksForDay(NEXT_DAY).single { it.typedText == "Send mockups" }
        val link = sync.getLink(moved.id)!!
        assertEquals("r-2", link.remoteId)
        assertEquals(NEXT_DAY, link.syncedDueDay)
        assertNull(moved.dueDay)
        assertNothingWaits()
    }

    @Test
    fun aTaskWithItsOwnDueDayOnlyChangesThatDueDay() = runBlocking {
        val id = synced(row = 0, text = "Tax return", remoteId = "r-1") { repo.setDueDay(it, "2026-11-10", CANVAS_HEIGHT) }

        merger.merge(remote("r-1", "Tax return", dueDay = "2026-11-12"))

        val task = tasks.getById(id)!!
        assertEquals("stays on its day", DAY, task.dayDate)
        assertEquals("2026-11-12", task.dueDay)
        assertNothingWaits()
    }

    @Test
    fun aSubtasksNewDueDayChangesOnlyItsOwnAndOneAfterItsParentsGoesBack() = runBlocking {
        val parent = synced(row = 0, text = "Trip", remoteId = "r-p") { repo.setDueDay(it, "2026-11-05", CANVAS_HEIGHT) }
        val child = synced(row = 1, text = "Hotel", remoteId = "r-c", parentId = parent)

        merger.merge(remote("r-c", "Hotel", dueDay = "2026-11-04", parent = "r-p"))
        assertEquals(DAY, tasks.getById(child)!!.dayDate)
        assertEquals("2026-11-04", tasks.getById(child)!!.dueDay)
        assertEquals("still a subtask", parent, tasks.getById(child)!!.parentTaskId)
        assertNothingWaits()

        merger.merge(remote("r-c", "Hotel", dueDay = "2026-11-09", parent = "r-p", version = "v3"))
        assertEquals("BooxDay refuses a subtask due after its parent", "2026-11-04", tasks.getById(child)!!.dueDay)
        assertEquals("the tablet's due day goes back", listOf(TaskSyncOp.UPSERT to child), sync.getOutbox().map { it.op to it.taskId })
    }

    @Test
    fun undatedOnThePhoneTheTaskStaysWhereItIs() = runBlocking {
        val id = synced(row = 0, text = "Someday", remoteId = "r-1")

        merger.merge(remote("r-1", "Someday", dueDay = null))

        assertEquals(DAY, tasks.getById(id)!!.dayDate)
        assertTrue(sync.getLink(id)!!.remoteUndated)
        assertNothingWaits()
    }

    @Test
    fun aMoveThatCantBeDoneNowWaitsAndChangesNothing() = runBlocking {
        val id = synced(row = 0, text = "Call mom", remoteId = "r-1")
        canvasHeight = 0f  // the Tasks canvas hasn't been measured yet

        assertEquals(TaskSyncMerger.Result.WAITS, merger.merge(remote("r-1", "Call mom", completed = true, dueDay = NEXT_DAY)))

        val task = tasks.getById(id)!!
        assertEquals("Call mom", task.typedText)
        assertFalse("nothing else written either", task.isCompleted)
        assertEquals("v1", sync.getLink(id)!!.remoteVersion)
    }

    // ── Subtasks (owner decision 24) ──

    @Test
    fun aSubtaskMadeOnThePhoneStaysANormalRowHere() = runBlocking {
        val id = synced(row = 3, text = "Buy milk", remoteId = "r-1")

        merger.merge(remote("r-1", "Buy milk", parent = "r-groceries"))

        assertNull(tasks.getById(id)!!.parentTaskId)
        val link = sync.getLink(id)!!
        assertEquals("r-groceries", link.remoteParentId)
        assertNull("the tablet's grouping as last sent", link.syncedParentRemoteId)
        assertNothingWaits()
    }

    // ── Deletes (owner decision 23) ──

    @Test
    fun deletedOnThePhoneTheRowIsClearedAndNothingIsSent() = runBlocking {
        val id = synced(row = 0, text = "Call mom", remoteId = "r-1")

        assertEquals(TaskSyncMerger.Result.APPLIED, merger.merge(remote("r-1", "Call mom", deleted = true)))

        assertNull(tasks.getById(id)!!.typedText)
        assertNull(sync.getLink(id))
        assertTrue("no delete sent back", sync.getOutbox().isEmpty())
    }

    @Test
    fun deletedOnThePhoneButChangedHereItStaysOnTheTabletOnly() = runBlocking {
        val id = synced(row = 0, text = "Call mom", remoteId = "r-1")
        repo.setTypedText(id, "Call mom today")

        merger.merge(remote("r-1", "Call mom", deleted = true))

        assertEquals("Call mom today", tasks.getById(id)!!.typedText)
        assertTrue(sync.getLink(id)!!.keptOnTablet)
        TaskSyncScanner(db) { { true } }.scan(now, DAY)
        assertTrue("never sent again", sync.getOutbox().isEmpty())

        repo.clearTaskSlot(tasks.getById(id)!!, CANVAS_HEIGHT)
        assertTrue("clearing it later deletes nothing in the service", sync.getOutbox().isEmpty())
        assertNull(sync.getLink(id))
    }

    @Test
    fun aListDeletedInGoogleClearsItsTasks() = runBlocking {
        val a = synced(row = 0, text = "One", remoteId = "r-a")
        val b = synced(row = 1, text = "Two", remoteId = "r-b")
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO task_sync_list (provider, accountEmail, serverUrl, listId, title, isDefaultForNew) " +
                "VALUES ('${TaskSyncProvider.GOOGLE_TASKS}', '$EMAIL', '', '$LIST', 'Shopping', 1)"
        )
        sync.insertOutboxEntry(
            TaskSyncOutboxEntry(
                op = TaskSyncOp.DELETE, taskId = "gone", provider = TaskSyncProvider.GOOGLE_TASKS, accountEmail = EMAIL,
                serverUrl = "", listId = LIST, remoteId = "r-old", createdAt = now
            )
        )

        assertEquals(TaskSyncMerger.Result.APPLIED, merger.listGone(LIST))

        assertNull(tasks.getById(a)!!.typedText)
        assertNull(tasks.getById(b)!!.typedText)
        assertTrue(sync.getLists().isEmpty())
        assertTrue("its waiting deletes went with it", sync.getOutbox().isEmpty())
    }

    // ── New in the service ──

    @Test
    fun aCopyMadeBySendWhoseReplyWasLostIsLinkedNotAdded() = runBlocking {
        val id = synced(row = 4, text = "Pay rent", remoteId = null)
        sync.queueUpsert(id, now)

        assertEquals(TaskSyncMerger.Result.APPLIED, merger.merge(remote("r-9", "Pay rent", dueDay = DAY)))

        assertEquals("r-9", sync.getLink(id)?.remoteId)
        assertTrue("its create is no longer needed", sync.getOutbox().isEmpty())
        assertEquals(1, tasks.getTasksForDay(DAY).count { it.typedText == "Pay rent" })
    }

    @Test
    fun aNewTaskFromThePhoneWaitsForPlacing() = runBlocking {
        assertEquals(TaskSyncMerger.Result.WAITS, merger.merge(remote("r-new", "Buy bread")))
        assertNull(sync.getLinkByRemote(TaskSyncProvider.GOOGLE_TASKS, EMAIL, "", LIST, "r-new"))
    }

    // ── Helpers ──

    /** A task on [DAY] linked as the sender leaves it after a send (version "v1"). */
    private fun synced(
        row: Int,
        text: String?,
        remoteId: String?,
        recognized: String? = null,
        parentId: String? = null,
        configure: (String) -> Unit = {}
    ): String {
        val (page, _) = repo.getOrCreateTasksForPage(DAY, 0)
        val id = page[row].id
        text?.let { repo.setTypedText(id, it) }
        recognized?.let { tasks.setRecognizedTextById(id, it) }
        parentId?.let { repo.setParentTask(id, it) }
        configure(id)
        repo.invalidateTaskCache()
        if (remoteId != null) {
            val task = tasks.getById(id)!!
            val parentRemote = parentId?.let { sync.getLink(it)?.remoteId }
            sync.insertLink(
                TaskSyncLink(
                    taskId = id, provider = TaskSyncProvider.GOOGLE_TASKS, accountEmail = EMAIL, listId = LIST,
                    remoteId = remoteId, remoteVersion = "v1", syncedTitle = TaskSyncRules.textToSend(task),
                    syncedCompleted = task.isCompleted, syncedDueDay = TaskSyncRules.dueDay(task),
                    syncedParentRemoteId = parentRemote, remoteParentId = parentRemote,
                    contentHash = TaskSyncRules.contentHash(task, parentRemote), syncedAt = 1
                )
            )
        }
        return id
    }

    private fun remote(
        remoteId: String,
        title: String,
        completed: Boolean = false,
        dueDay: String? = DAY,
        parent: String? = null,
        deleted: Boolean = false,
        version: String = "v2",
        modifiedAt: Long = now + 1_000
    ) = RemoteTaskState(LIST, remoteId, title, completed, dueDay, parent, deleted, version, modifiedAt)

    private fun assertNothingWaits() {
        assertTrue("nothing to send back: ${sync.getOutbox()}", sync.getOutbox().isEmpty())
        TaskSyncScanner(db) { { true } }.scan(now, DAY)
        assertTrue("and the change watcher finds nothing new: ${sync.getOutbox()}", sync.getOutbox().isEmpty())
    }

    private fun deleteDatabaseFiles() {
        val file = File(getDbDir(context), APP_DATABASE_FILE_NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
    }

    private companion object {
        const val EMAIL = "me@gmail.com"
        const val LIST = "list-1"
        const val DAY = "2026-11-02"
        const val NEXT_DAY = "2026-11-03"
        const val CANVAS_HEIGHT = 1386f
    }
}
