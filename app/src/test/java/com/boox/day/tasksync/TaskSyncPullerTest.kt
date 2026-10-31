package com.boox.day.tasksync

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.APP_DATABASE_FILE_NAME
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.TaskDao
import com.boox.day.data.db.TaskRepository
import com.boox.day.data.db.TaskSyncDao
import com.boox.day.data.db.TaskSyncKept
import com.boox.day.data.db.TaskSyncLink
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.data.getDbDir
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Reading each synced list and merging it (task sync step 2.3): cursors, the daily check, lists gone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TaskSyncPullerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repo: TaskRepository
    private lateinit var db: AppDatabase
    private lateinit var tasks: TaskDao
    private lateinit var sync: TaskSyncDao
    private val service = FakeService()
    private var now = 1_800_000_000_000L
    private var canvasHeight = CANVAS_HEIGHT
    private lateinit var puller: TaskSyncPuller

    @Before
    fun setUp() {
        deleteDatabaseFiles()
        repo = TaskRepository(context)
        repo.invalidateTaskCache()
        db = AppDatabase.getDatabase(context)
        tasks = db.taskDao()
        sync = db.taskSyncDao()
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO task_sync_list (provider, accountEmail, serverUrl, listId, title, isDefaultForNew) " +
                "VALUES ('${TaskSyncProvider.GOOGLE_TASKS}', '$EMAIL', '', '$LIST', 'BooxDay test', 1)"
        )
        val merger = TaskSyncMerger(
            db, RepositoryTabletTasks(context, repo, canvasHeight = { canvasHeight }, penOn = { false }), TaskSyncProvider.GOOGLE_TASKS, EMAIL,
            clock = { now }, today = { DAY }, service = service
        )
        puller = TaskSyncPuller(db, service, merger, EMAIL, clock = { now })
    }

    @After
    fun tearDown() {
        repo.invalidateTaskCache()
        AppDatabase.closeDatabase()
        deleteDatabaseFiles()
    }

    @Test
    fun theCursorMovesOnOnceEverythingMergedAndTheNextPullAsksFromIt() = runBlocking {
        val id = synced(0, "Call mom", "r-1")
        service.changes = RemoteChanges(listOf(remote("r-1", "Call mom", completed = true)), "c1")

        assertEquals(1, puller.pull().merged)

        assertTrue(tasks.getById(id)!!.isCompleted)
        assertEquals("c1", sync.getLists().single().pullCursor)
        puller.pull()
        assertEquals(listOf(null, "c1"), service.cursorsAsked)
    }

    @Test
    fun aChangeThatWaitsKeepsTheCursor() = runBlocking {
        // A new task from the phone, while the Tasks rows' size isn't known yet
        canvasHeight = 0f
        service.changes = RemoteChanges(listOf(remote("r-new", "Buy bread")), "c1")

        assertEquals(1, puller.pull().waiting)

        assertNull("asked again from the old cursor next time", sync.getLists().single().pullCursor)
    }

    @Test
    fun theDailyCheckClearsATaskMissingFromTheList() = runBlocking {
        val kept = synced(0, "Call mom", "r-1")
        val gone = synced(1, "Pay rent", "r-2")
        service.ids = setOf("r-1")

        puller.pull()

        assertEquals("Call mom", tasks.getById(kept)!!.typedText)
        assertNull(tasks.getById(gone)!!.typedText)
        assertNull(sync.getLink(gone))
        assertEquals(now, sync.getLists().single().lastFullCheckAt)

        now += 60 * 60_000L
        puller.pull()
        assertEquals("once a day", 1, service.idChecks)
    }

    @Test
    fun anEmptyAnswerFromTheDailyCheckDeletesNothing() = runBlocking {
        val a = synced(0, "Call mom", "r-1")
        service.ids = emptySet()

        puller.pull()

        assertEquals("Call mom", tasks.getById(a)!!.typedText)
        assertEquals("r-1", sync.getLink(a)?.remoteId)
    }

    @Test
    fun aTaskSentAMomentAgoIsNotJudgedByTheDailyCheck() = runBlocking {
        val id = synced(0, "Call mom", "r-1", syncedAt = now - 60_000)
        synced(1, "Pay rent", "r-2")
        service.ids = setOf("r-2")

        puller.pull()

        assertEquals("Call mom", tasks.getById(id)!!.typedText)
    }

    @Test
    fun aListGoneFromGoogleClearsItsTasks() = runBlocking {
        val id = synced(0, "Call mom", "r-1")
        service.listGone = true

        puller.pull()

        assertNull(tasks.getById(id)!!.typedText)
        assertTrue(sync.getLists().isEmpty())
    }

    @Test
    fun aSignInProblemStopsThePull() = runBlocking {
        service.failure = TaskSyncServiceException.SignInNeeded()
        try {
            puller.pull()
            fail("expected SignInNeeded")
        } catch (expected: TaskSyncServiceException.SignInNeeded) {
        }
    }

    // ── A task moved to another list on the phone (owner decision 49) ──

    @Test
    fun aTaskMovedToAnotherSyncedListUnderTheSameIdKeepsItsRow() = runBlocking {
        tickSecondList()
        val id = synced(0, "Call mom", "r-1")
        tasks.setPriority(id, "A")  // only the original has it (like its ink): a task added from Google has none
        // The old list may still report it deleted; it is merged last
        service.changesByList[LIST] = RemoteChanges(listOf(remote("r-1", "Call mom").copy(deleted = true)), "c1")
        service.changesByList[LIST_2] = RemoteChanges(listOf(remote("r-1", "Call mom").copy(listId = LIST_2)), "c2")

        puller.pull()

        assertEquals("the row keeps the task (and its ink)", "A", tasks.getById(id)?.priority)
        assertEquals(LIST_2, sync.getLink(id)?.listId)
        assertEquals(1, tasks.getTasksForDay(DAY).count { it.typedText == "Call mom" })
    }

    @Test
    fun aTaskMovedToAnotherSyncedListUnderANewIdIsFollowedNotCopied() = runBlocking {
        tickSecondList()
        val id = synced(0, "Call mom", "r-1")
        tasks.setPriority(id, "A")
        service.changesByList[LIST] = RemoteChanges(listOf(remote("r-1", "Call mom").copy(deleted = true)), "c1")
        service.changesByList[LIST_2] = RemoteChanges(listOf(remote("r-9", "Call mom").copy(listId = LIST_2)), "c2")

        puller.pull()

        assertEquals("the original, not a copy", "A", tasks.getById(id)?.priority)
        assertEquals(LIST_2 to "r-9", sync.getLink(id)?.let { it.listId to it.remoteId })
        assertEquals("no typed copy", 1, tasks.getTasksForDay(DAY).count { it.typedText == "Call mom" })
    }

    @Test
    fun aTaskMovedToAListThatIsntSyncedStaysOnTheTablet() = runBlocking {
        val id = synced(0, "Call mom", "r-1")
        service.changesByList[LIST] = RemoteChanges(listOf(remote("r-1", "Call mom").copy(deleted = true)), "c1")
        service.elsewhere = remote("r-1", "Call mom").copy(listId = "unsynced")

        puller.pull()

        assertEquals("not cleared", "Call mom", tasks.getById(id)?.typedText)
        assertEquals(TaskSyncKept.MOVED_TO_UNSYNCED_LIST, sync.getLink(id)?.keptReason)
        assertTrue("no longer synced", sync.getLink(id)!!.keptOnTablet)
    }

    // ── Helpers ──

    private fun tickSecondList() {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO task_sync_list (provider, accountEmail, serverUrl, listId, title, isDefaultForNew) " +
                "VALUES ('${TaskSyncProvider.GOOGLE_TASKS}', '$EMAIL', '', '$LIST_2', 'Work', 0)"
        )
    }

    private fun synced(row: Int, text: String, remoteId: String, syncedAt: Long = 1): String {
        val (page, _) = repo.getOrCreateTasksForPage(DAY, 0)
        val id = page[row].id
        repo.setTypedText(id, text)
        repo.invalidateTaskCache()
        val task = tasks.getById(id)!!
        sync.insertLink(
            TaskSyncLink(
                taskId = id, provider = TaskSyncProvider.GOOGLE_TASKS, accountEmail = EMAIL, listId = LIST,
                remoteId = remoteId, remoteVersion = "v1", syncedTitle = text, syncedDueDay = DAY,
                contentHash = TaskSyncRules.contentHash(task, null), syncedAt = syncedAt
            )
        )
        return id
    }

    private fun remote(remoteId: String, title: String, completed: Boolean = false) =
        RemoteTaskState(LIST, remoteId, title, completed, DAY, null, false, "v2", now)

    private class FakeService : TaskSyncService {
        override val provider = TaskSyncProvider.GOOGLE_TASKS
        var changes = RemoteChanges(emptyList(), null)
        val changesByList = HashMap<String, RemoteChanges>()
        var elsewhere: RemoteTaskState? = null
        var ids: Set<String>? = null
        var listGone = false
        var failure: Exception? = null
        val cursorsAsked = mutableListOf<String?>()
        var idChecks = 0

        override suspend fun changes(listId: String, cursor: String?): RemoteChanges {
            failure?.let { throw it }
            if (listGone) throw TaskSyncServiceException.ListGone(listId)
            cursorsAsked += cursor
            changesByList.remove(listId)?.let { return it }
            return changes.also { changes = RemoteChanges(emptyList(), it.nextCursor) }
        }

        override suspend fun findInOtherLists(skip: Set<String>, remoteId: String, title: String?, dueDay: String?, since: Long) =
            elsewhere?.takeIf { it.listId !in skip }

        override suspend fun allIds(listId: String): Set<String> {
            idChecks++
            return ids ?: emptySet()
        }

        override suspend fun upsert(task: OutgoingTask, link: TaskSyncLink?): RemoteTask = error("not sent here")
        override suspend fun delete(listId: String, remoteId: String) = error("not sent here")
    }

    private fun deleteDatabaseFiles() {
        val file = File(getDbDir(context), APP_DATABASE_FILE_NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
    }

    private companion object {
        const val EMAIL = "me@gmail.com"
        const val LIST = "list-1"
        const val LIST_2 = "list-2"
        const val DAY = "2026-11-02"
        const val CANVAS_HEIGHT = 1386f
    }
}
