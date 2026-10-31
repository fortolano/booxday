package com.boox.day.tasksync

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.APP_DATABASE_FILE_NAME
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.TaskDao
import com.boox.day.data.db.TaskRepository
import com.boox.day.data.db.TaskSyncDao
import com.boox.day.data.db.TaskSyncOp
import com.boox.day.data.db.TaskSyncProvider
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
 * Turning task sync on (owner decision 9, plan step 2.5): unfinished tablet tasks dated today or
 * later go; ticked ones and older days stay on the tablet. After turn-on, a new task ticked before
 * it is sent still goes (decision 29).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TaskSyncTurnOnTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repo: TaskRepository
    private lateinit var db: AppDatabase
    private lateinit var tasks: TaskDao
    private lateinit var sync: TaskSyncDao
    private val now = 1_800_000_000_000L

    @Before
    fun setUp() {
        deleteDatabaseFiles()
        repo = TaskRepository(context)
        repo.invalidateTaskCache()
        db = AppDatabase.getDatabase(context)
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
    fun atTurnOnUnfinishedTasksFromTodayGoAndTickedOnesAndOlderDaysStay() {
        val unfinished = write(TODAY, 0, "Call mom")
        val ticked = write(TODAY, 1, "Pay rent", ticked = true)
        val future = write(TOMORROW, 0, "Dentist")
        val olderTicked = write(YESTERDAY, 0, "Old done", ticked = true)
        val olderOpen = write(YESTERDAY, 1, "Old open")

        assertEquals(1, turnOn())
        scan()

        assertEquals("the unfinished ones go", setOf(unfinished, future), queued())
        assertTrue("the ticked one stays on the tablet", sync.getLink(ticked)!!.keptOnTablet)
        assertNull("older days are never looked at", sync.getLink(olderTicked))
        assertNull(sync.getLink(olderOpen))
    }

    @Test
    fun afterTurnOnANewTaskTickedBeforeItIsSentStillGoes() {
        turnOn()
        val id = write(TODAY, 2, "Water plants", ticked = true)

        scan()

        assertEquals(setOf(id), queued())
    }

    @Test
    fun aTaskTickedAtTurnOnAndUntickedLaterSyncs() {
        val id = write(TODAY, 1, "Pay rent", ticked = true)
        turnOn()
        scan()
        assertTrue(queued().isEmpty())

        repo.setCompleted(id, false)
        scan()

        assertEquals(setOf(id), queued())
        assertNull("the tablet-only mark is gone", sync.getLink(id))
    }

    @Test
    fun theTurnOnMarkMovesWithItsTask() {
        val id = write(TODAY, 1, "Pay rent", ticked = true)
        turnOn()

        val result = repo.moveTaskWithStrokes(tasks.getById(id)!!, TOMORROW, 0, CANVAS_HEIGHT, context)
        scan()

        assertTrue(sync.getLink(result.movedTaskId!!)!!.keptOnTablet)
        assertTrue("still not sent", queued().isEmpty())
    }

    @Test
    fun clearingOrDeletingATabletOnlyTaskDeletesNothingInTheService() {
        val cleared = write(TODAY, 1, "Pay rent", ticked = true)
        val deleted = write(TODAY, 2, "Bought milk", ticked = true)
        turnOn()

        repo.clearTaskSlot(tasks.getById(cleared)!!, CANVAS_HEIGHT)
        db.openHelper.writableDatabase.execSQL("DELETE FROM task WHERE id = ?", arrayOf<Any?>(deleted))

        assertTrue(sync.getOutbox().none { it.op == TaskSyncOp.DELETE })
        assertNull(sync.getLink(cleared))
        assertNull(sync.getLink(deleted))
    }

    // ── Helpers ──

    private fun turnOn() = TaskSync.keepTickedTasksOnTablet(db, TaskSyncProvider.GOOGLE_TASKS, EMAIL, today = TODAY, now = now)

    private fun scan() {
        TaskSyncScanner(db) { { true } }.scan(now, TODAY)
    }

    private fun queued(): Set<String> = sync.getOutbox().filter { it.op == TaskSyncOp.UPSERT }.mapTo(HashSet()) { it.taskId }

    private fun write(day: String, row: Int, text: String, ticked: Boolean = false): String {
        val id = repo.getOrCreateTasksForPage(day, 0).first[row].id
        repo.setTypedText(id, text)
        if (ticked) repo.setCompleted(id, true)
        repo.invalidateTaskCache()
        return id
    }

    private fun deleteDatabaseFiles() {
        val file = File(getDbDir(context), APP_DATABASE_FILE_NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
    }

    private companion object {
        const val EMAIL = "me@gmail.com"
        const val YESTERDAY = "2026-11-01"
        const val TODAY = "2026-11-02"
        const val TOMORROW = "2026-11-03"
        const val CANVAS_HEIGHT = 1386f
    }
}
