package com.boox.day.data.db

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.getDbDir
import com.boox.day.editor.utils.Pen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Moving tasks never loses one (Oct 2026, task sync Groundwork 1.5). TaskRepository uses the app's
 * database singleton and a process-wide page cache, so each test starts from an empty file and cache.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TaskMoveSafetyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repo: TaskRepository
    private lateinit var tasks: TaskDao

    @Before
    fun setUp() {
        deleteDatabaseFiles()
        repo = TaskRepository(context)
        repo.invalidateTaskCache()
        tasks = AppDatabase.getDatabase(context).taskDao()
    }

    @After
    fun tearDown() {
        repo.invalidateTaskCache()
        AppDatabase.closeDatabase()
        deleteDatabaseFiles()
    }

    @Test
    fun moveAllNeverClearsATaskItCouldNotPlace() {
        // A target day whose page has only rows 0-4 (ensureTenTasks exists because pages like this happen)
        val pages = AppDatabase.getDatabase(context).pageDao()
        val targetPage = Page()
        pages.create(targetPage)
        tasks.insertAll((0 until 5).map { row ->
            Task(dayDate = TARGET, taskPageIndex = 0, rowIndex = row, pageId = targetPage.id, typedText = "Target $row")
        })
        val (source, _) = repo.getOrCreateTasksForPage(SOURCE, 0)
        repo.setTypedText(source[0].id, "Call mom")
        repo.invalidateTaskCache()
        // Changing month pre-warms page 0 of each day with tasks
        repo.preWarmCacheForMonth("2026-11")

        val result = repo.moveAllTasksFromPage(SOURCE, 0, TARGET, CANVAS_HEIGHT)

        assertTrue(result.toString(), result.isSuccess)
        val everywhere = tasks.getTasksForDay(SOURCE) + tasks.getTasksForDay(TARGET)
        assertTrue("'Call mom' must still exist somewhere: ${everywhere.mapNotNull { it.typedText }}",
            everywhere.any { it.typedText == "Call mom" })
        // The short page is no longer pre-warmed as it is, so it was filled and the task landed there
        assertEquals(1, result.getOrNull())
        assertEquals(5, tasks.getTasksForDay(TARGET).single { it.typedText == "Call mom" }.rowIndex)
    }

    @Test
    fun moveToAnotherDayMovesTheTaskAndFreesItsRow() {
        val (source, _) = repo.getOrCreateTasksForPage(SOURCE, 0)
        repo.setTypedText(source[2].id, "Send mockups")
        repo.setDueDay(source[2].id, SOURCE, CANVAS_HEIGHT)
        repo.setTypedText(source[3].id, "Tax return")
        repo.setDueDay(source[3].id, "2026-11-10", CANVAS_HEIGHT)

        val result = repo.moveTaskWithStrokes(tasks.getById(source[2].id)!!, TARGET, 0, CANVAS_HEIGHT, context)
        val other = repo.moveTaskWithStrokes(tasks.getById(source[3].id)!!, TARGET, 0, CANVAS_HEIGHT, context)

        assertTrue(result.success)
        val moved = tasks.getById(assertNotNull(result.movedTaskId).let { result.movedTaskId!! })
        assertEquals("Send mockups", moved?.typedText)
        assertEquals(TARGET, moved?.dayDate)
        assertEquals("due on its own day: the due date moves too (owner, 31 Oct 2026)", TARGET, moved?.dueDay)
        assertEquals("a due date of its own stays", "2026-11-10", tasks.getById(other.movedTaskId!!)?.dueDay)
        assertEquals(null, tasks.getById(source[2].id)?.typedText)
    }

    @Test
    fun aParentAndItsSubtaskMoveTogether() {
        val (source, _) = repo.getOrCreateTasksForPage(SOURCE, 0)
        repo.setTypedText(source[0].id, "Plan trip")
        repo.setTypedText(source[1].id, "Book hotel")
        repo.setParentTask(source[1].id, source[0].id)

        val result = repo.moveTaskWithStrokes(tasks.getById(source[0].id)!!, TARGET, 0, CANVAS_HEIGHT, context)

        assertTrue(result.success)
        val target = tasks.getTasksForDay(TARGET)
        val parent = target.single { it.typedText == "Plan trip" }
        val child = target.single { it.typedText == "Book hotel" }
        assertEquals(parent.id, child.parentTaskId)
        assertEquals(parent.rowIndex + 1, child.rowIndex)
        assertTrue(tasks.getTasksForDay(SOURCE).none { it.typedText != null })
    }

    /**
     * Owner decision 75 (Nov 2026): Undo of a same-day Move puts the whole task back. It used to undo
     * only the ink, on the page on screen, so the words stayed on the new row and the ink on both.
     */
    @Test
    fun undoOfASameDayMovePutsTheWholeTaskBack() {
        repo.getOrCreateTasksForPage(SOURCE, 0)
        val (page1, _) = repo.getOrCreateTasksForPage(SOURCE, 1)
        val task = page1[3]
        repo.setTypedText(task.id, "Pay rent")
        repo.setPriority(task.id, "A")
        repo.setDueDay(task.id, "2026-11-10", CANVAS_HEIGHT)
        val strokes = StrokeRepository(context)
        strokes.create(Stroke(size = 6f, pen = Pen.BALLPEN, top = 470f, bottom = 490f, left = 100f, right = 300f,
            points = (0..10).map { StrokePoint(100f + it * 20f, 480f) }, pageId = task.pageId))

        val result = repo.moveTaskWithStrokes(tasks.getById(task.id)!!, SOURCE, 0, CANVAS_HEIGHT, context)
        val undo = result.undoOperations?.single() as com.boox.day.editor.state.Operation.MoveTasks
        val movedTo = tasks.getById(result.movedTaskId!!)!!
        assertEquals(0, movedTo.taskPageIndex)

        assertTrue(repo.moveTaskRows(undo.move))

        val back = tasks.getById(task.id)!!
        assertEquals("Pay rent", back.typedText)
        assertEquals("A", back.priority)
        assertEquals("2026-11-10", back.dueDay)
        assertEquals(null, tasks.getById(movedTo.id)?.typedText)
        assertEquals("the ink is on the old row only", 1, strokes.getAllForPage(task.pageId).size)
        assertTrue(strokes.getAllForPage(movedTo.pageId).isEmpty())

        // Redo refuses a row that was used since, and changes nothing
        repo.setTypedText(movedTo.id, "Buy milk")
        assertEquals(false, repo.moveTaskRows(undo.move.reversed()))
        assertEquals("Pay rent", tasks.getById(task.id)?.typedText)
        assertEquals("Buy milk", tasks.getById(movedTo.id)?.typedText)
    }

    private fun deleteDatabaseFiles() {
        val file = File(getDbDir(context), APP_DATABASE_FILE_NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
    }

    private companion object {
        const val SOURCE = "2026-11-02"
        const val TARGET = "2026-11-03"
        const val CANVAS_HEIGHT = 1386f
    }
}
