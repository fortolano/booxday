package com.boox.day.tasksync

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Page
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskSyncLink
import com.boox.day.data.db.TaskSyncOp
import com.boox.day.data.db.TaskSyncOutboxEntry
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.data.db.TaskSyncSchema
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

/** The sending side against a stand-in service (Groundwork 1.8; Google Tasks stage 2.2-2.3). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TaskSyncSenderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private val prefs by lazy { context.getSharedPreferences("task_sync_sender_test", Context.MODE_PRIVATE) }
    private val service = FakeService()
    private var now = 1_800_000_000_000L
    private lateinit var sender: TaskSyncSender

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(TaskSyncSchema.CALLBACK)
            .allowMainThreadQueries()
            .build()
        prefs.edit().clear().commit()
        db.pageDao().create(Page(id = "page-1"))
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO task_sync_list (provider, accountEmail, serverUrl, listId, title, isDefaultForNew) " +
                "VALUES ('${TaskSyncProvider.GOOGLE_TASKS}', '$EMAIL', '', 'list-new', 'BooxDay test', 1)"
        )
        sender = newSender()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun aNewTaskIsCreatedInTheNewTasksListAndLinked() = runBlocking {
        insert("t-1", row = 0, typed = "Call mom")
        db.taskSyncDao().queueUpsert("t-1", now)

        assertEquals(TaskSyncSender.Outcome.Sent(1), sender.sendDue())

        assertEquals(listOf("create list-new Call mom due 2026-11-02"), service.calls)
        val link = db.taskSyncDao().getLink("t-1")!!
        assertEquals("r-1", link.remoteId)
        assertEquals(TaskSyncRules.contentHash(db.taskDao().getById("t-1")!!, null), link.contentHash)
        assertTrue(db.taskSyncDao().getOutbox().isEmpty())
        // The next scan finds nothing new to send
        TaskSyncScanner(db) { { true } }.scan(now, DAY)
        assertTrue(db.taskSyncDao().getOutbox().isEmpty())
    }

    @Test
    fun aLinkedTaskIsUpdatedAndADeleteDeletes() = runBlocking {
        insert("t-1", row = 0, typed = "Call mom")
        link("t-1", "r-9")
        db.taskSyncDao().queueUpsert("t-1", now)
        db.taskSyncDao().insertOutboxEntry(delete("r-old"))

        sender.sendDue()

        assertEquals(listOf("update r-9 Call mom", "delete list-1 r-old"), service.calls)
        assertTrue(db.taskSyncDao().getOutbox().isEmpty())
    }

    @Test
    fun aFailureWaitsLongerEachTime() = runBlocking {
        insert("t-1", row = 0, typed = "Call mom")
        db.taskSyncDao().queueUpsert("t-1", now)
        service.failNext = IllegalStateException("boom")

        sender.sendDue()
        val entry = db.taskSyncDao().getOutbox().single()
        assertEquals(1, entry.attempts)
        assertEquals(now + 60_000, entry.nextAttemptAt)

        now += 30_000
        sender.sendDue()
        assertEquals("not due yet", 1, service.calls.size)

        now += 31_000
        sender.sendDue()
        assertEquals("sent on the retry", 2, service.calls.size)
        assertTrue(db.taskSyncDao().getOutbox().isEmpty())
    }

    @Test
    fun whatWaitsSurvivesARestart() = runBlocking {
        insert("t-1", row = 0, typed = "Call mom")
        db.taskSyncDao().queueUpsert("t-1", now)
        service.failNext = TaskSyncServiceException.Offline()
        assertEquals(TaskSyncSender.Outcome.Offline, sender.sendDue())

        val afterRestart = newSender()
        afterRestart.sendDue()

        assertEquals("tried offline, then sent after the restart", 2, service.calls.count { it.startsWith("create") })
        assertEquals("r-1", db.taskSyncDao().getLink("t-1")?.remoteId)
        assertTrue(db.taskSyncDao().getOutbox().isEmpty())
    }

    @Test
    fun signInNeededStopsAndKeepsEverything() = runBlocking {
        insert("t-1", row = 0, typed = "One")
        insert("t-2", row = 1, typed = "Two")
        db.taskSyncDao().queueUpsert("t-1", now)
        db.taskSyncDao().queueUpsert("t-2", now)
        service.failNext = TaskSyncServiceException.SignInNeeded()

        assertEquals(TaskSyncSender.Outcome.NeedsReconnect, sender.sendDue())

        assertEquals("stopped at the first", 1, service.calls.size)
        assertEquals(2, db.taskSyncDao().getOutbox().size)
    }

    @Test
    fun moreThanTwentyDeletesWaitForTheUser() = runBlocking {
        repeat(21) { db.taskSyncDao().insertOutboxEntry(delete("r-$it")) }

        assertEquals(TaskSyncSender.Outcome.PausedForDeletes(21), sender.sendDue())
        assertTrue("nothing deleted", service.calls.isEmpty())

        sender.approveWaitingDeletes()
        sender.sendDue()
        assertEquals(21, service.calls.size)
    }

    @Test
    fun keepingThemInTheServiceDropsTheDeletes() = runBlocking {
        repeat(25) { db.taskSyncDao().insertOutboxEntry(delete("r-$it")) }

        sender.keepWaitingDeletesInService()
        sender.sendDue()

        assertTrue(service.calls.isEmpty())
        assertTrue(db.taskSyncDao().getOutbox().isEmpty())
    }

    @Test
    fun aNewTaskWithAnOrphansWordsTakesItsLinkOver() = runBlocking {
        insert("t-old", row = 5)  // lost its words (Undo of a move): orphaned link
        db.taskSyncDao().insertLink(
            TaskSyncLink(
                taskId = "t-old", provider = TaskSyncProvider.GOOGLE_TASKS, accountEmail = EMAIL, listId = "list-1",
                remoteId = "r-rent", syncedTitle = "Pay rent", orphanedAt = now - 5_000, syncedAt = 1
            )
        )
        insert("t-new", row = 2, recognized = "Pay rent")
        db.taskSyncDao().queueUpsert("t-new", now)

        sender.sendDue()

        assertEquals("an update, not a second copy", listOf("update r-rent Pay rent"), service.calls)
        assertEquals("r-rent", db.taskSyncDao().getLink("t-new")?.remoteId)
        assertNull(db.taskSyncDao().getLink("t-old"))
        assertTrue("no delete for the orphan", db.taskSyncDao().getOutbox().isEmpty())
    }

    @Test
    fun anUndatedCopyStaysUndatedUntilTheTaskMoves() = runBlocking {
        insert("t-1", row = 0, typed = "Someday")
        db.taskSyncDao().insertLink(
            TaskSyncLink(
                taskId = "t-1", provider = TaskSyncProvider.GOOGLE_TASKS, accountEmail = EMAIL, listId = "list-1",
                remoteId = "r-1", syncedDueDay = DAY, remoteUndated = true, syncedAt = 1
            )
        )
        db.taskSyncDao().queueUpsert("t-1", now)
        sender.sendDue()
        assertEquals("update r-1 Someday undated", service.calls.single())

        db.openHelper.writableDatabase.execSQL("UPDATE task SET dayDate = '2026-11-04' WHERE id = 't-1'")
        db.taskSyncDao().queueUpsert("t-1", now)
        sender.sendDue()
        assertEquals("moved: now dated", "update r-1 Someday due 2026-11-04", service.calls.last())
    }

    @Test
    fun withNoListForNewTasksTheTaskWaits() = runBlocking {
        db.openHelper.writableDatabase.execSQL("DELETE FROM task_sync_list")
        insert("t-1", row = 0, typed = "Call mom")
        db.taskSyncDao().queueUpsert("t-1", now)

        sender.sendDue()

        assertTrue(service.calls.isEmpty())
        assertEquals(1, db.taskSyncDao().getOutbox().single().attempts)
    }

    @Test
    fun aClearedParentsSubtasksMoveOutBeforeItIsDeleted() = runBlocking {
        // The parent's row was cleared: its DELETE waits, the subtask stays on the tablet as a task
        insert("t-child", row = 1, typed = "Buy milk")
        link("t-child", "r-child", parentRemoteId = "r-parent")
        db.taskSyncDao().insertOutboxEntry(delete("r-parent"))
        db.taskSyncDao().queueUpsert("t-child", now)

        sender.sendDue()

        assertEquals(listOf("update r-child Buy milk", "delete list-1 r-parent"), service.calls)
        assertEquals("moved to the top level first", listOf("r-child" to null), service.parentsSent)
        assertNull(db.taskSyncDao().getLink("t-child")?.syncedParentRemoteId)
        assertTrue(db.taskSyncDao().getOutbox().isEmpty())
    }

    @Test
    fun whenASubtaskCantMoveOutItsParentsDeleteWaits() = runBlocking {
        insert("t-child", row = 1, typed = "Buy milk")
        link("t-child", "r-child", parentRemoteId = "r-parent")
        db.taskSyncDao().insertOutboxEntry(delete("r-parent"))
        service.failNext = IllegalStateException("boom")

        sender.sendDue()

        assertEquals("no delete", listOf("update r-child Buy milk"), service.calls)
        assertEquals(1, db.taskSyncDao().getOutbox().single { it.op == TaskSyncOp.DELETE }.attempts)
    }

    @Test
    fun aNewSubtaskGoesToItsParentsListUnderIt() = runBlocking {
        insert("t-parent", row = 0, typed = "Groceries")
        link("t-parent", "r-parent", listId = "list-shop")
        insert("t-child", row = 1, typed = "Buy milk", parent = "t-parent")
        db.taskSyncDao().queueUpsert("t-child", now)

        sender.sendDue()

        assertEquals(listOf("create list-shop Buy milk due $DAY"), service.calls)
        assertEquals(listOf("new" to "r-parent"), service.parentsSent)
    }

    @Test
    fun aParentInAnotherListIsNotSentAndTheTaskIsNotSentAgain() = runBlocking {
        insert("t-parent", row = 0, typed = "Groceries")
        link("t-parent", "r-parent", listId = "list-shop")
        insert("t-child", row = 1, typed = "Buy milk", parent = "t-parent")
        link("t-child", "r-child")  // in list-1
        db.taskSyncDao().queueUpsert("t-child", now)

        sender.sendDue()
        assertEquals(listOf("update r-child Buy milk"), service.calls)
        assertTrue("no parent sent", service.parentsSent.isEmpty())

        TaskSyncScanner(db) { { true } }.scan(now, DAY)
        assertTrue("nothing new to send", db.taskSyncDao().getOutbox().isEmpty())
    }

    @Test
    fun theLinkKeepsTheWordsAsTheServiceKeepsThem() = runBlocking {
        insert("t-1", row = 0, typed = "Call mom" + "!".repeat(30))
        db.taskSyncDao().queueUpsert("t-1", now)
        service.keepsTitle = { it.take(10) }

        sender.sendDue()

        assertEquals("Call mom!!", db.taskSyncDao().getLink("t-1")?.syncedTitle)
        TaskSyncScanner(db) { { true } }.scan(now, DAY)
        assertTrue("nothing new to send", db.taskSyncDao().getOutbox().isEmpty())
    }

    @Test
    fun aCopyChangedOnThePhoneIsMergedThenSentAgain() = runBlocking {
        insert("t-1", row = 0, typed = "Call mom")
        link("t-1", "r-1")
        db.taskDao().setTypedText("t-1", "Call mom today")
        db.taskSyncDao().queueUpsert("t-1", now)
        // Ticked on the phone since the last sync
        service.failNext = TaskSyncServiceException.RemoteChanged(
            RemoteTaskState("list-1", "r-1", "Call mom", true, DAY, null, false, "v9", now - 1_000)
        )

        sender.sendDue()

        assertEquals("tried, merged, sent again", listOf("update r-1 Call mom today", "update r-1 Call mom today"), service.calls)
        assertTrue("the phone's tick is on the tablet", db.taskDao().getById("t-1")!!.isCompleted)
        assertTrue(db.taskSyncDao().getOutbox().isEmpty())
    }

    @Test
    fun aCopyDeletedOnThePhoneWhileChangedHereStaysOnTheTabletOnly() = runBlocking {
        insert("t-1", row = 0, typed = "Call mom")
        link("t-1", "r-1")
        db.taskDao().setTypedText("t-1", "Call mom today")
        db.taskSyncDao().queueUpsert("t-1", now)
        service.failNext = TaskSyncServiceException.NotFound()

        sender.sendDue()

        assertEquals("Call mom today", db.taskDao().getById("t-1")!!.typedText)
        assertTrue(db.taskSyncDao().getLink("t-1")!!.keptOnTablet)
        assertTrue(db.taskSyncDao().getOutbox().isEmpty())

        db.taskSyncDao().queueUpsert("t-1", now)
        sender.sendDue()
        assertEquals("never sent again", 1, service.calls.size)
    }

    @Test
    fun aListGoneWhileSendingClearsItsUnchangedTasks() = runBlocking {
        insert("t-1", row = 0, typed = "Call mom")
        link("t-1", "r-1")
        db.taskDao().setTypedText("t-1", "Call mom today")
        db.taskSyncDao().queueUpsert("t-1", now)
        insert("t-2", row = 1, typed = "Pay rent")
        link("t-2", "r-2")
        service.failNext = TaskSyncServiceException.ListGone("list-1")

        sender.sendDue()

        assertEquals("unchanged: cleared (owner decision 23)", listOf("t-2"), tablet.cleared)
        assertTrue("changed here: kept on the tablet", db.taskSyncDao().getLink("t-1")!!.keptOnTablet)
        assertTrue(db.taskSyncDao().getOutbox().isEmpty())
    }

    @Test
    fun aPhoneMadeSubtaskMovesOutBeforeItsParentIsDeleted() = runBlocking {
        // A normal row on the tablet, a subtask in Google (owner decision 24)
        insert("t-child", row = 1, typed = "Buy milk")
        link("t-child", "r-child", remoteParentId = "r-parent")
        db.taskSyncDao().insertOutboxEntry(delete("r-parent"))

        sender.sendDue()

        assertEquals(listOf("update r-child Buy milk", "delete list-1 r-parent"), service.calls)
        assertEquals(listOf("r-child" to null), service.parentsSent)
        assertNull(db.taskSyncDao().getLink("t-child")!!.remoteParentId)
    }

    private fun newSender() = TaskSyncSender(db, service, merger, EMAIL, prefs = prefs, clock = { now })

    private val tablet by lazy { FakeTablet() }
    private val merger by lazy {
        TaskSyncMerger(db, tablet, TaskSyncProvider.GOOGLE_TASKS, EMAIL, clock = { now }, today = { DAY })
    }

    /** The tablet's task operations reduced to their database writes. */
    private inner class FakeTablet : TabletTasks {
        val cleared = mutableListOf<String>()
        override fun <T> inTransaction(block: () -> T): T = db.runInTransaction<T> { block() }
        override fun setWords(task: Task, words: String) = db.taskDao().setTypedText(task.id, words)
        override fun setCompleted(taskId: String, completed: Boolean) = db.taskDao().setCompleted(taskId, completed)
        override fun setDueDay(taskId: String, dueDay: String?): Boolean {
            db.taskDao().setDueDay(taskId, dueDay)
            return true
        }
        override fun moveToDay(task: Task, day: String) = false
        override fun clear(task: Task): Boolean {
            db.taskDao().clearTaskMetadata(task.id)
            cleared += task.id
            return true
        }
        override suspend fun add(day: String, words: String, completed: Boolean, link: (Task) -> Unit) = false
    }

    private fun insert(id: String, row: Int, typed: String? = null, recognized: String? = null, parent: String? = null) {
        db.taskDao().insertAll(
            listOf(
                Task(
                    id = id, dayDate = DAY, taskPageIndex = 0, rowIndex = row, pageId = "page-1",
                    typedText = typed, recognizedText = recognized, parentTaskId = parent
                )
            )
        )
    }

    /**
     * As the last sync left it: the task's current fields. [parentRemoteId]: the tablet's grouping as
     * sent; [remoteParentId]: Google's parent (the same unless made on the phone).
     */
    private fun link(
        taskId: String,
        remoteId: String,
        listId: String = "list-1",
        parentRemoteId: String? = null,
        remoteParentId: String? = parentRemoteId
    ) {
        val task = db.taskDao().getById(taskId)!!
        db.taskSyncDao().insertLink(
            TaskSyncLink(
                taskId = taskId, provider = TaskSyncProvider.GOOGLE_TASKS, accountEmail = EMAIL,
                listId = listId, remoteId = remoteId, syncedTitle = TaskSyncRules.textToSend(task),
                syncedDueDay = DAY, syncedParentRemoteId = parentRemoteId, remoteParentId = remoteParentId,
                contentHash = TaskSyncRules.contentHash(task, parentRemoteId), syncedAt = 1
            )
        )
    }

    private fun delete(remoteId: String) = TaskSyncOutboxEntry(
        op = TaskSyncOp.DELETE, taskId = "gone-$remoteId", provider = TaskSyncProvider.GOOGLE_TASKS,
        accountEmail = EMAIL, serverUrl = "", listId = "list-1", remoteId = remoteId, createdAt = now
    )

    private class FakeService : TaskSyncService {
        override val provider = TaskSyncProvider.GOOGLE_TASKS
        val calls = mutableListOf<String>()
        /** Parents sent: (copy, parent) when new ("new") or when the parent changed. */
        val parentsSent = mutableListOf<Pair<String, String?>>()
        var failNext: Exception? = null
        var keepsTitle: (String) -> String? = { null }
        private var next = 0

        override suspend fun upsert(task: OutgoingTask, link: TaskSyncLink?): RemoteTask {
            val due = task.dueDay?.let { "due $it" } ?: "undated"
            calls += if (link == null) "create ${task.listId} ${task.title} $due" else
                "update ${link.remoteId} ${task.title}" + if (link.remoteUndated || task.dueDay != DAY) " $due" else ""
            if (link == null && task.parentRemoteId != null) parentsSent += "new" to task.parentRemoteId
            if (link != null && task.parentRemoteId != link.syncedParentRemoteId) parentsSent += link.remoteId to task.parentRemoteId
            failNext?.let { failNext = null; throw it }
            return RemoteTask(task.listId, link?.remoteId ?: "r-${++next}", "v$next", keepsTitle(task.title))
        }

        override suspend fun delete(listId: String, remoteId: String) {
            calls += "delete $listId $remoteId"
            failNext?.let { failNext = null; throw it }
        }
    }

    private companion object {
        const val EMAIL = "me@gmail.com"
        const val DAY = "2026-11-02"
    }
}
