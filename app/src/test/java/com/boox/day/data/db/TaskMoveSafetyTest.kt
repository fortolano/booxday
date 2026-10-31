package com.boox.day.data.db

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.getDbDir
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
