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
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.data.getDbDir
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Moving a synced task to another list from the tablet: the group moves together (owner decisions 47, 53). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TaskSyncListMoverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repo: TaskRepository
    private lateinit var db: AppDatabase
    private lateinit var tasks: TaskDao
    private lateinit var sync: TaskSyncDao
    private val service = FakeService()
    private lateinit var mover: TaskSyncListMover

    @Before
    fun setUp() {
        deleteDatabaseFiles()
        repo = TaskRepository(context)
        repo.invalidateTaskCache()
        db = AppDatabase.getDatabase(context)
        tasks = db.taskDao()
        sync = db.taskSyncDao()
        mover = TaskSyncListMover(db, service, EMAIL)
    }

    @After
    fun tearDown() {
        repo.invalidateTaskCache()
        AppDatabase.closeDatabase()
        deleteDatabaseFiles()
    }

    @Test
    fun aMainTaskMovesWithItsSubtasksAndTheRowsStay() = runBlocking {
        val trip = synced(0, "Plan trip", "p")
        val hotel = synced(1, "Book hotel", "c", googleParent = "p")
        repo.setParentTask(hotel, trip)

        assertEquals("a subtask goes with its main task", TaskSyncListMover.Outcome.SUBTASK, mover.move(hotel, WORK))
        assertEquals(TaskSyncListMover.Outcome.MOVED, mover.move(trip, WORK))

        // Google left the subtask behind (it doesn't say what it does): it was moved under the main task
        assertEquals(WORK to "p-moved", sync.getLink(trip)?.let { it.listId to it.remoteId })
        assertEquals(WORK to "c-moved", sync.getLink(hotel)?.let { it.listId to it.remoteId })
        assertEquals("p-moved", sync.getLink(hotel)?.remoteParentId)
        assertEquals("p-moved", service.copies[WORK to "c-moved"]?.parentRemoteId)
        assertEquals("same rows, nothing cleared", listOf("Plan trip", "Book hotel"), listOf(trip, hotel).map { tasks.getById(it)?.typedText })
    }

    @Test
    fun subtasksTheServiceTookAlongAreFollowed() = runBlocking {
        service.takesSubtasksAlong = true
        val trip = synced(0, "Plan trip", "p")
        val hotel = synced(1, "Book hotel", "c", googleParent = "p")

        mover.move(trip, WORK)

        assertEquals(WORK to "c", sync.getLink(hotel)?.let { it.listId to it.remoteId })
        assertEquals("not moved a second time", 1, service.moves)
    }

    @Test
    fun aTaskTheServiceWontMoveStaysInItsList() = runBlocking {
        service.refuses = true  // Google: a repeating task
        val id = synced(0, "Water plants", "r")

        assertEquals(TaskSyncListMover.Outcome.REFUSED, mover.move(id, WORK))
        assertEquals(LIST to "r", sync.getLink(id)?.let { it.listId to it.remoteId })
    }

    // ── Helpers ──

    private fun synced(row: Int, text: String, remoteId: String, googleParent: String? = null): String {
        val id = repo.getOrCreateTasksForPage(DAY, 0).first[row].id
        repo.setTypedText(id, text)
        repo.invalidateTaskCache()
        sync.insertLink(
            TaskSyncLink(
                taskId = id, provider = TaskSyncProvider.GOOGLE_TASKS, accountEmail = EMAIL, listId = LIST,
                remoteId = remoteId, remoteVersion = "v1", syncedTitle = text, syncedDueDay = DAY,
                syncedParentRemoteId = googleParent, remoteParentId = googleParent, syncedAt = 1
            )
        )
        service.copies[LIST to remoteId] = RemoteTaskState(LIST, remoteId, text, false, DAY, googleParent, false, "v1")
        return id
    }

    /** Google as far as moving goes: copies by (list, id); a move gives a new id, as Google may. */
    private class FakeService : TaskSyncService {
        override val provider = TaskSyncProvider.GOOGLE_TASKS
        val copies = HashMap<Pair<String, String>, RemoteTaskState>()
        var takesSubtasksAlong = false
        var refuses = false
        var moves = 0

        override suspend fun fetch(listId: String, remoteId: String) = copies[listId to remoteId]

        override suspend fun moveToList(fromListId: String, remoteId: String, toListId: String, parentRemoteId: String?): RemoteTaskState {
            if (refuses) throw TaskSyncServiceException.Refused()
            moves++
            val copy = copies.remove(fromListId to remoteId) ?: throw TaskSyncServiceException.NotFound()
            val newId = if (takesSubtasksAlong) remoteId else "$remoteId-moved"
            val moved = copy.copy(listId = toListId, remoteId = newId, parentRemoteId = parentRemoteId, version = "v2")
            copies[toListId to newId] = moved
            if (takesSubtasksAlong) {
                copies.filterValues { it.listId == fromListId && it.parentRemoteId == remoteId }.forEach { (key, child) ->
                    copies.remove(key)
                    copies[toListId to child.remoteId] = child.copy(listId = toListId, version = "v2")
                }
            }
            return moved
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
        const val WORK = "list-work"
        const val DAY = "2026-11-02"
    }
}
