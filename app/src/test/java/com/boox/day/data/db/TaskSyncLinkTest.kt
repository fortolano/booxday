package com.boox.day.data.db

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.getDbDir
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

/**
 * Task sync links follow the task, not the row (Groundwork 1.8, Oct 2026): moving, moving all,
 * sorting and Break Down carry the link to the task's new row; clearing a task or deleting its
 * subtask queues the delete of its synced copy; a copy is a new task with no link.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TaskSyncLinkTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repo: TaskRepository
    private lateinit var tasks: TaskDao
    private lateinit var sync: TaskSyncDao

    @Before
    fun setUp() {
        deleteDatabaseFiles()
        repo = TaskRepository(context)
        repo.invalidateTaskCache()
        val db = AppDatabase.getDatabase(context)
        tasks = db.taskDao()
        sync = db.taskSyncDao()
    }

    @After
    fun tearDown() {
        repo.invalidateTaskCache()
        AppDatabase.closeDatabase()
        deleteDatabaseFiles()
    }

    @Test
    fun clearingATaskQueuesTheDeleteOfItsCopy() {
        val row = writeTask(DAY, 0, "Call mom", remoteId = "g-1")

        repo.clearTaskSlot(tasks.getById(row)!!, CANVAS_HEIGHT)

        assertNull(sync.getLink(row))
        assertEquals(listOf(TaskSyncOp.DELETE to "g-1"), sync.getOutbox().map { it.op to it.remoteId })
    }

    @Test
    fun movingToAnotherDayKeepsTheLink() {
        val row = writeTask(DAY, 2, "Send mockups", remoteId = "g-2")

        val result = repo.moveTaskWithStrokes(tasks.getById(row)!!, NEXT_DAY, 0, CANVAS_HEIGHT, context)

        assertTrue(result.success)
        assertEquals("g-2", sync.getLink(result.movedTaskId!!)?.remoteId)
        assertNull(sync.getLink(row))
        assertTrue("nothing deleted", sync.getOutbox().isEmpty())
    }

    @Test
    fun movingAllTasksFromAPageKeepsTheirLinks() {
        writeTask(DAY, 0, "One", remoteId = "g-a")
        writeTask(DAY, 1, "Two", remoteId = "g-b")

        assertTrue(repo.moveAllTasksFromPage(DAY, 0, NEXT_DAY, CANVAS_HEIGHT).isSuccess)

        val moved = tasks.getTasksForDay(NEXT_DAY).filter { it.typedText != null }.associate { it.typedText to it.id }
        assertEquals("g-a", sync.getLink(moved.getValue("One"))?.remoteId)
        assertEquals("g-b", sync.getLink(moved.getValue("Two"))?.remoteId)
        assertTrue(sync.getOutbox().isEmpty())
    }

    @Test
    fun sortingSwapsKeepEveryLinkWithItsTask() {
        val a = writeTask(DAY, 0, "A", remoteId = "g-a")
        val b = writeTask(DAY, 1, "B", remoteId = "g-b")
        val rowA = tasks.getById(a)!!
        val rowB = tasks.getById(b)!!

        assertTrue(
            repo.reorderTasksWithStrokes(
                DAY,
                listOf(
                    TaskRepository.TaskReorderItem(rowA, 0, 1, rowB.id),
                    TaskRepository.TaskReorderItem(rowB, 0, 0, rowA.id)
                ),
                CANVAS_HEIGHT
            )
        )

        assertEquals("B", tasks.getById(a)?.typedText)
        assertEquals("g-b", sync.getLink(a)?.remoteId)
        assertEquals("g-a", sync.getLink(b)?.remoteId)
        assertTrue(sync.getOutbox().isEmpty())
    }

    @Test
    fun deletingASubtaskDeletesItsCopyAndTheOnesBelowKeepTheirs() {
        val parent = writeTask(DAY, 0, "Trip", remoteId = "g-trip")
        val hotel = writeTask(DAY, 1, "Hotel", remoteId = "g-hotel")
        val car = writeTask(DAY, 2, "Car", remoteId = "g-car")
        repo.setParentTask(hotel, parent)
        repo.setParentTask(car, parent)
        repo.invalidateTaskCache()

        repo.deleteChildTaskAndCompact(tasks.getById(hotel)!!, CANVAS_HEIGHT)

        assertEquals(listOf(TaskSyncOp.DELETE to "g-hotel"), sync.getOutbox().map { it.op to it.remoteId })
        val carNow = tasks.getTasksForDay(DAY).single { it.typedText == "Car" }
        assertEquals("Car moved up into the freed row", 1, carNow.rowIndex)
        assertEquals("g-car", sync.getLink(carNow.id)?.remoteId)
        assertEquals("g-trip", sync.getLink(parent)?.remoteId)
    }

    @Test
    fun breakDownMovesTheLinkToTheNewParent() {
        val original = writeTask(DAY, 0, "Plan trip", remoteId = "g-trip")
        val newParent = writeTask(DAY, 3, "Plan trip", remoteId = null)

        repo.moveTaskSchedulingTo(tasks.getById(original)!!, newParent)

        assertEquals("g-trip", sync.getLink(newParent)?.remoteId)
        assertNull(sync.getLink(original))
        assertTrue(sync.getOutbox().isEmpty())
    }

    @Test
    fun aCopyIsANewTaskWithNoLink() {
        val row = writeTask(DAY, 0, "Water plants", remoteId = "g-water")

        val result = repo.copySingleTask(tasks.getById(row)!!, NEXT_DAY, 0, CANVAS_HEIGHT)

        assertTrue(result.toString(), result.isSuccess)
        val copy = tasks.getTasksForDay(NEXT_DAY).single { it.typedText == "Water plants" }
        assertNull(sync.getLink(copy.id))
        assertEquals("g-water", sync.getLink(row)?.remoteId)
        assertTrue(sync.getOutbox().isEmpty())
    }

    @Test
    fun aTaskMovedOntoAnOrphanedRowRetiresTheOrphan() {
        // The target day's row 0 lost its words without being cleared: its link is an orphan
        val (target, _) = repo.getOrCreateTasksForPage(NEXT_DAY, 0)
        sync.insertLink(link(target[0].id, "g-orphan"))
        val row = writeTask(DAY, 4, "Book dentist", remoteId = "g-dentist")

        val result = repo.moveTaskWithStrokes(tasks.getById(row)!!, NEXT_DAY, 0, CANVAS_HEIGHT, context)

        assertEquals(target[0].id, result.movedTaskId)
        assertEquals("g-dentist", sync.getLink(target[0].id)?.remoteId)
        assertEquals(listOf(TaskSyncOp.DELETE to "g-orphan"), sync.getOutbox().map { it.op to it.remoteId })
    }

    private fun writeTask(day: String, row: Int, text: String, remoteId: String?): String {
        val (page, _) = repo.getOrCreateTasksForPage(day, 0)
        val id = page[row].id
        repo.setTypedText(id, text)
        if (remoteId != null) sync.insertLink(link(id, remoteId))
        repo.invalidateTaskCache()
        return id
    }

    private fun link(taskId: String, remoteId: String) = TaskSyncLink(
        taskId = taskId,
        provider = TaskSyncProvider.GOOGLE_TASKS,
        accountEmail = "me@gmail.com",
        listId = "list-1",
        remoteId = remoteId,
        syncedAt = 1
    )

    private fun deleteDatabaseFiles() {
        val file = File(getDbDir(context), APP_DATABASE_FILE_NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
    }

    private companion object {
        const val DAY = "2026-11-02"
        const val NEXT_DAY = "2026-11-03"
        const val CANVAS_HEIGHT = 1386f
    }
}
