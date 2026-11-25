package com.boox.day.tasksync.microsoft

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.APP_DATABASE_FILE_NAME
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskDao
import com.boox.day.data.db.TaskRepository
import com.boox.day.data.db.TaskSyncDao
import com.boox.day.data.db.TaskSyncKept
import com.boox.day.data.db.TaskSyncList
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.data.getDbDir
import com.boox.day.sync.microsoft.MicrosoftGraph
import com.boox.day.tasksync.RepositoryTabletTasks
import com.boox.day.tasksync.TaskSync
import com.boox.day.tasksync.TaskSyncListMover
import com.boox.day.tasksync.TaskSyncMerger
import com.boox.day.tasksync.TaskSyncPuller
import com.boox.day.tasksync.TaskSyncScanner
import com.boox.day.tasksync.TaskSyncSender
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
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
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Task sync with Microsoft To Do end to end (owner decisions 79-83): the real sender, pull, merge
 * and list move on tablet rows, against To Do as measured on 25 Nov 2026 (FakeTodoServer). Each
 * risk: a priority lost, a subtask's own due day overwritten, a task sent twice, a row cleared, a
 * ticked repeating task jumping to the next day.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MicrosoftTodoSyncTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val todo = FakeTodoServer()
    private lateinit var repo: TaskRepository
    private lateinit var db: AppDatabase
    private lateinit var tasks: TaskDao
    private lateinit var sync: TaskSyncDao
    private lateinit var provider: MicrosoftTodoProvider

    @Before
    fun setUp() {
        deleteDatabaseFiles()
        TaskSync.prefs(context).edit().clear().commit()
        server.dispatcher = todo
        server.start()
        todo.addList(TASKS, "Tasks", wellKnown = "defaultList")
        repo = TaskRepository(context)
        repo.invalidateTaskCache()
        db = AppDatabase.getDatabase(context)
        tasks = db.taskDao()
        sync = db.taskSyncDao()
        sync.insertList(TaskSyncList(TaskSyncProvider.MICROSOFT_TODO, EMAIL, "", TASKS, "Tasks", isDefaultForNew = true))
        provider = MicrosoftTodoProvider(
            // No silent retries: an answer lost on the way stays lost, as on a bad connection
            MicrosoftGraph(
                token = { "token" }, baseUrl = server.url("/v1.0").toString().trimEnd('/'),
                client = OkHttpClient.Builder().retryOnConnectionFailure(false).readTimeout(5, TimeUnit.SECONDS).build()
            ),
            linksIn = { listId -> sync.getLinksInList(TaskSyncProvider.MICROSOFT_TODO, EMAIL, "", listId) },
            zone = { ZoneId.of("Europe/Warsaw") }
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        GlobalAppSettings.update(AppSettings(version = 1))
        TaskSync.prefs(context).edit().clear().commit()
        repo.invalidateTaskCache()
        AppDatabase.closeDatabase()
        deleteDatabaseFiles()
    }

    /** The tablet's clock for the runs. */
    private var now = System.currentTimeMillis()

    /** One run as TaskSyncRun does it: tablet changes noticed, the service read, then the outbox sent. */
    private suspend fun run() {
        TaskSyncScanner(db, extras = { true }) { { true } }.scan(now, DAY, wordsSettled = true)
        val merger = merger()
        TaskSyncPuller(db, provider, merger, EMAIL, clock = { now }).pull()
        TaskSyncSender(db, provider, merger, EMAIL, prefs = TaskSync.prefs(context), clock = { now }).sendDue()
    }

    private fun merger() = TaskSyncMerger(
        db, RepositoryTabletTasks(context, repo, canvasHeight = { CANVAS_HEIGHT }, penOn = { false }),
        TaskSyncProvider.MICROSOFT_TODO, EMAIL, clock = { now }, today = { DAY }, service = provider,
        createTried = TaskSyncSender.createTried(TaskSync.prefs(context), TaskSyncProvider.MICROSOFT_TODO, EMAIL)
    )

    // ── Priority (owner decision 80) ──

    @Test
    fun priorityBStaysBTheStarIsAAndUnstarringAClearsIt() = runBlocking {
        val id = row(0, "Call Anna") { repo.setPriority(it, "B", CANVAS_HEIGHT) }
        run()
        val copy = todo.tasksIn(TASKS).single()
        assertEquals("B goes unstarred", "normal", copy.text("importance"))
        val writes = todo.writes.size

        run()
        assertEquals("B stays B after a sync", "B", task(id).priority)
        assertEquals("nothing sent twice", writes, todo.writes.size)

        todo.patchTask(TASKS, copy.text("id"), mapOf("importance" to JsonPrimitive("high")))
        run()
        assertEquals("a star on the phone is A", "A", task(id).priority)

        todo.patchTask(TASKS, copy.text("id"), mapOf("importance" to JsonPrimitive("normal")))
        run()
        assertEquals("unstarring A clears it", "", task(id).priority)

        // Starred while A, then B here while the phone takes the star away: B stays
        repo.setPriority(id, "A", CANVAS_HEIGHT)
        run()
        assertEquals("high", todo.task(TASKS, copy.text("id"))!!.text("importance"))
        repo.setPriority(id, "B", CANVAS_HEIGHT)
        todo.patchTask(TASKS, copy.text("id"), mapOf("importance" to JsonPrimitive("normal")))
        run()
        assertEquals("unstarred while B: B", "B", task(id).priority)
    }

    // ── Subtasks are steps (owner decision 79) ──

    @Test
    fun aSubtaskGoesAsAStepAndKeepsItsOwnDueDayPriorityAndReminder() = runBlocking {
        val trip = row(0, "Plan trip") { repo.setDueDay(it, "2026-11-28", CANVAS_HEIGHT) }
        val hotel = row(1, "Book hotel") {
            repo.setParentTask(it, trip)
            repo.setDueDay(it, "2026-11-27", CANVAS_HEIGHT)
            repo.setPriority(it, "C", CANVAS_HEIGHT)
            repo.setReminderMinuteOfDay(it, 9 * 60)
        }
        run()

        val parent = todo.tasksIn(TASKS).single()
        assertEquals("one To Do task, the subtask a step of it", "Plan trip", parent.text("title"))
        val step = todo.stepsOf(parent.text("id")).single()
        assertEquals("Book hotel", step.text("displayName"))

        todo.tickStep(TASKS, parent.text("id"), step.text("id"), ticked = true)
        run()

        val after = task(hotel)
        assertTrue("ticked from the phone", after.isCompleted)
        assertEquals("its own due day stays", "2026-11-27", after.dueDay)
        assertEquals("C", after.priority)
        assertEquals(9 * 60, after.reminderMinuteOfDay)
        assertEquals("still a subtask here", trip, after.parentTaskId)
    }

    @Test
    fun aStepAddedOnThePhoneComesAsATaskOnItsParentsDay() = runBlocking {
        val trip = row(0, "Plan trip")
        run()
        val parent = todo.tasksIn(TASKS).single().text("id")

        todo.addStep(TASKS, parent, "Pack bags")
        run()
        val writes = todo.writes.size
        run()

        val bags = tasks.getTasksForDay(DAY).single { it.typedText == "Pack bags" }
        assertNull("a normal task here (decision 24)", bags.parentTaskId)
        assertEquals(TodoFields.stepRemoteId(parent, todo.stepsOf(parent).single().text("id")), sync.getLink(bags.id)?.remoteId)
        assertTrue(tasks.getById(trip) != null)
        assertEquals("nothing sent back", writes, todo.writes.size)
        assertEquals("still a step in To Do", 1, todo.tasksIn(TASKS).size)
    }

    // ── Moving to another list (owner decision 83) ──

    @Test
    fun aMoveTakesBothStepsAlongAndEveryLinkFollows() = runBlocking {
        todo.addList(WORK, "Work")
        sync.insertList(TaskSyncList(TaskSyncProvider.MICROSOFT_TODO, EMAIL, "", WORK, "Work"))
        val trip = row(0, "Plan trip")
        val hotel = row(1, "Book hotel") { repo.setParentTask(it, trip) }
        val bags = row(2, "Pack bags") { repo.setParentTask(it, trip) }
        run()
        val oldId = todo.tasksIn(TASKS).single().text("id")

        assertEquals(TaskSyncListMover.Outcome.MOVED, TaskSyncListMover(db, provider, EMAIL).move(trip, WORK))

        assertTrue("the old copy is gone", todo.tasksIn(TASKS).isEmpty())
        val copy = todo.tasksIn(WORK).single()
        val newSteps = todo.stepsOf(copy.text("id")).associate { it.text("displayName") to TodoFields.stepRemoteId(copy.text("id"), it.text("id")) }
        assertEquals(WORK to copy.text("id"), sync.getLink(trip)!!.let { it.listId to it.remoteId })
        for ((row, words) in listOf(hotel to "Book hotel", bags to "Pack bags")) {
            val link = sync.getLink(row)!!
            assertEquals("$words follows its new step", WORK to newSteps.getValue(words), link.listId to link.remoteId)
            assertEquals(copy.text("id"), link.remoteParentId)
        }
        val writes = todo.writes.size

        run()
        assertEquals("nothing sent twice", writes, todo.writes.size)
        assertEquals("nothing cleared on the tablet", listOf("Plan trip", "Book hotel", "Pack bags"), listOf(trip, hotel, bags).map { task(it).typedText })
        assertTrue("To Do gave the copy a new id", oldId != copy.text("id"))
    }

    // ── Repeating tasks: To Do keeps the id for the next one (measured 25 Nov 2026); a ticked one's
    // row stays ticked on its day, kept on the tablet, and the next one comes as a new task (owner decision 100) ──

    @Test
    fun aRepeatingTaskTickedHereStaysTickedOnItsDayAndTheNextOneComesAsANewTask() = runBlocking {
        val original = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))  // 25 Nov, as To Do keeps due days
        run()
        val plants = rowsOn(DAY, "Water plants").single()

        repo.setCompleted(plants, true)
        run()
        assertEquals("one tick, nothing else", listOf("PATCH"), todo.writes.map { it.substringBefore(' ') })
        assertKeptTicked(plants, DAY, original)

        run()
        val next = rowsOn(NEXT_DAY, "Water plants").single()
        assertTrue("the next one comes unticked", !task(next).isCompleted)
        assertEquals(original, sync.getLink(next)?.remoteId)
        assertEquals("the done copy doesn't come (decision 27)", listOf(plants), rowsOn(DAY, "Water plants"))
        run()
        assertEquals("nothing sent again", 1, todo.writes.size)
        assertEquals("the next one isn't ticked", "notStarted", todo.task(TASKS, original)!!.text("status"))
    }

    @Test
    fun theNextOnesResetStepsNeverUntickTheTicksOnTheDoneDay() = runBlocking {
        val original = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        val kitchen = todo.addStep(TASKS, original, "Kitchen")
        val balcony = todo.addStep(TASKS, original, "Balcony")
        run()
        val day = rowsOn(DAY, "Water plants", "Kitchen", "Balcony")

        repo.setCompleted(day[1], true)  // Kitchen
        run()
        repo.setCompleted(day[0], true)  // then the task
        run()
        for (row in day) assertTrue("kept on the tablet", sync.getLink(row)!!.keptOnTablet)

        run()  // the next one, its steps reset (same ids), comes as new rows
        val next = rowsOn(NEXT_DAY, "Water plants", "Kitchen", "Balcony")
        assertTrue(next.none { task(it).isCompleted })
        assertEquals(listOf(original) + listOf(kitchen, balcony).map { TodoFields.stepRemoteId(original, it) }, next.map { sync.getLink(it)?.remoteId })
        todo.tickStep(TASKS, original, balcony, ticked = true)  // the next one's step, on the phone
        run()

        assertEquals("the done day's ticks as they were", listOf(true, true, false), day.map { task(it).isCompleted })
        assertTrue("the next one's step ticked", task(next[2]).isCompleted)
        assertEquals("the step's tick and the task's, nothing more", 2, patches())
        assertTrue(todo.writes.none { it.startsWith("DELETE") })
    }

    @Test
    fun aTickWhoseAnswerWasLostIsNeverSentTwice() = runBlocking {
        val original = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        run()
        val plants = rowsOn(DAY, "Water plants").single()
        todo.loseReplyTo = { method, path -> method == "PATCH" && path.endsWith("/tasks/$original") }

        repo.setCompleted(plants, true)
        run()  // To Do ticks it and rolls it over; the answer is lost
        todo.loseReplyTo = { _, _ -> false }
        assertEquals(1, patches())
        assertTrue("not taken as sent", !sync.getLink(plants)!!.keptOnTablet)

        // Sent again before a pull sees the change: due on another day now, so not ticked again
        sendWaitingNow()
        TaskSyncSender(db, provider, merger(), EMAIL, prefs = TaskSync.prefs(context), clock = { now }).sendDue()
        assertEquals("a second tick would tick the next one", 1, patches())
        assertKeptTicked(plants, DAY, original)

        run()
        assertTrue(!task(rowsOn(NEXT_DAY, "Water plants").single()).isCompleted)
        assertEquals("notStarted", todo.task(TASKS, original)!!.text("status"))
        assertEquals(1, patches())
    }

    @Test
    fun aTickHereWhileThePhoneTickedItAndClearedTheDoneCopyNeverTicksTheNextOne() = runBlocking {
        val original = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        run()
        val plants = rowsOn(DAY, "Water plants").single()

        repo.setCompleted(plants, true)  // ticked here, not sent yet
        todo.patchTask(TASKS, original, mapOf("status" to JsonPrimitive("completed")))  // and on the phone,
        todo.deleteTask(TASKS, todo.tasksIn(TASKS).single { it.text("status") == "completed" }.text("id"))  // its done copy cleared
        run()

        assertTrue("nothing sent: ${todo.writes}", todo.writes.isEmpty())
        assertKeptTicked(plants, DAY, original)
        val next = rowsOn(NEXT_DAY, "Water plants").single()
        assertTrue(!task(next).isCompleted)
        assertEquals(original, sync.getLink(next)?.remoteId)
        run()
        assertTrue(todo.writes.isEmpty())
        assertEquals("notStarted", todo.task(TASKS, original)!!.text("status"))
    }

    @Test
    fun aRepeatingTaskTickedOnThePhoneIsTickedHereOnItsDayAndTheNextOneComesAsANewTask() = runBlocking {
        val original = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        run()
        val plants = rowsOn(DAY, "Water plants").single()

        todo.patchTask(TASKS, original, mapOf("status" to JsonPrimitive("completed")))
        run()

        assertKeptTicked(plants, DAY, original)
        val next = rowsOn(NEXT_DAY, "Water plants").single()
        assertTrue(!task(next).isCompleted)
        assertEquals(original, sync.getLink(next)?.remoteId)
        assertTrue("nothing written back: ${todo.writes}", todo.writes.isEmpty())
        run()
        assertTrue("and nothing later: ${todo.writes}", todo.writes.isEmpty())
    }

    @Test
    fun aPhoneTickWhoseDoneCopyIsNotInTheRoundIsFoundByItsTitle() = runBlocking {
        val original = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        run()
        val plants = rowsOn(DAY, "Water plants").single()
        todo.hideDoneCopiesFromDelta = true

        todo.patchTask(TASKS, original, mapOf("status" to JsonPrimitive("completed")))
        run()

        assertKeptTicked(plants, DAY, original)
        assertEquals("one lookup", 1, todo.requests.count { "filter=" in it })
        assertEquals(original, sync.getLink(rowsOn(NEXT_DAY, "Water plants").single())?.remoteId)
    }

    @Test
    fun aRepeatingTaskPostponedOnThePhoneMovesAndIsNotTicked() = runBlocking {
        val original = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        run()
        val plants = rowsOn(DAY, "Water plants").single()

        // A day later, exactly as a tick would make it, but no ticked task of it anywhere
        todo.patchTask(TASKS, original, mapOf("dueDateTime" to FakeTodoServer.date("2026-11-26T00:00:00", "UTC")))
        run()

        val moved = linkedTo(original)!!
        assertTrue("an ordinary date change: still synced", !moved.keptOnTablet)
        assertEquals("the task moved (decision 43)", NEXT_DAY, task(moved.taskId).dayDate)
        assertTrue("not ticked", !task(moved.taskId).isCompleted)
        assertTrue("its row went with it", tasks.getTasksForDay(DAY).none { it.typedText == "Water plants" } && sync.getLink(plants) == null)
        assertTrue(todo.writes.isEmpty())
    }

    @Test
    fun aPhoneTickKeepsTheStepRowsOnTheDoneDayAndTheNextOnesStepsComeNew() = runBlocking {
        val original = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        val kitchen = todo.addStep(TASKS, original, "Kitchen")
        val balcony = todo.addStep(TASKS, original, "Balcony")
        run()
        val day = rowsOn(DAY, "Water plants", "Kitchen", "Balcony")

        todo.tickStep(TASKS, original, kitchen, ticked = true)
        run()
        todo.patchTask(TASKS, original, mapOf("status" to JsonPrimitive("completed")))
        run()

        assertEquals("ticked here as on the phone, on its day", listOf(true, true, false), day.map { task(it).isCompleted })
        for (row in day) assertTrue(sync.getLink(row)!!.keptOnTablet)
        val next = rowsOn(NEXT_DAY, "Water plants", "Kitchen", "Balcony")
        assertTrue("the next one's steps were reset: unticked", next.none { task(it).isCompleted })
        assertEquals(listOf(original) + listOf(kitchen, balcony).map { TodoFields.stepRemoteId(original, it) }, next.map { sync.getLink(it)?.remoteId })
        run()
        assertTrue("nothing written back: ${todo.writes}", todo.writes.isEmpty())
    }

    @Test
    fun ofTwoAlikeSeriesTheTickedOneStaysAndThePostponedOneMoves() = runBlocking {
        val first = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        val second = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        run()
        val firstRow = linkedTo(first)!!.taskId
        val secondRow = linkedTo(second)!!.taskId

        todo.patchTask(TASKS, second, mapOf("dueDateTime" to FakeTodoServer.date("2026-11-27T00:00:00", "UTC")))
        todo.patchTask(TASKS, first, mapOf("status" to JsonPrimitive("completed")))
        run()

        assertKeptTicked(firstRow, DAY, first)
        assertEquals(NEXT_DAY, task(linkedTo(first)!!.taskId).dayDate)
        val moved = linkedTo(second)!!
        assertTrue("not the next date of its repeat: a date change", !moved.keptOnTablet && !task(moved.taskId).isCompleted)
        assertEquals("2026-11-27", task(moved.taskId).dayDate)
        assertTrue(secondRow != firstRow)
    }

    @Test
    fun oneDoneCopyIsTheEvidenceForOneTaskOnly() = runBlocking {
        val first = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        val second = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        run()

        // One ticked, the other moved to the very day a tick would: either could be the done copy's
        todo.patchTask(TASKS, second, mapOf("dueDateTime" to FakeTodoServer.date("2026-11-26T00:00:00", "UTC")))
        todo.patchTask(TASKS, first, mapOf("status" to JsonPrimitive("completed")))
        run()

        for (id in listOf(first, second)) {
            val link = linkedTo(id)!!
            assertTrue("neither taken as ticked: both move", !link.keptOnTablet && !task(link.taskId).isCompleted)
            assertEquals(NEXT_DAY, task(link.taskId).dayDate)
        }
        assertTrue(tasks.getTasksForDay(DAY).none { it.typedText == "Water plants" })
        assertTrue(todo.writes.isEmpty())
    }

    @Test
    fun anOldDoneCopyInAFullRoundNeverTakesTheTabletsTask() = runBlocking {
        // Ticked on the phone before the tablet ever synced: an old done copy (25 Nov), the open one on 26 Nov
        val original = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        todo.patchTask(TASKS, original, mapOf("status" to JsonPrimitive("completed")))
        run()
        val next = tasks.getTasksForDay(NEXT_DAY).single { it.typedText == "Water plants" }.id
        assertTrue("the old done copy doesn't come (decision 27)", tasks.getTasksForDay(DAY).none { it.typedText == "Water plants" })

        // Microsoft forgets the cursor: a full round with the old done copy in it again
        sync.getListsFor(TaskSyncProvider.MICROSOFT_TODO, EMAIL, "").single().pullCursor?.let { todo.expired += it.substringAfter("deltatoken=") }
        run()

        assertEquals("still the open one", original, sync.getLink(next)?.remoteId)
        assertTrue("not ticked", !task(next).isCompleted)
        assertEquals(NEXT_DAY, task(next).dayDate)
        assertTrue(tasks.getTasksForDay(DAY).none { it.typedText == "Water plants" })
    }

    // ── Review of 25 Nov 2026 ──

    @Test
    fun aRepeatingTaskClearedHereAfterThePhoneTickedItLeavesTheSeriesInToDo() = runBlocking {
        // Owner decision 89; the original id is the next one by the time the tablet's delete goes
        val original = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        todo.addStep(TASKS, original, "Kitchen")
        run()
        val (plants, kitchenRow) = rowsOn(DAY, "Water plants", "Kitchen")

        todo.patchTask(TASKS, original, mapOf("status" to JsonPrimitive("completed")))  // ticked on the phone
        repo.clearTaskSlot(task(plants), CANVAS_HEIGHT)  // cleared here before the tablet synced
        repo.clearTaskSlot(task(kitchenRow), CANVAS_HEIGHT)
        run()

        assertTrue("nothing deleted in To Do: ${todo.writes}", todo.writes.none { it.startsWith("DELETE") })
        assertEquals("the next one is still there", "notStarted", todo.task(TASKS, original)!!.text("status"))
        assertEquals("with its step", 1, todo.stepsOf(original).size)
        assertEquals(2, todo.tasksIn(TASKS).size)
        assertNull("the rows went here", task(plants).typedText)
        assertNull(sync.getLink(plants))
        assertTrue("the deletes are done with", sync.getOutbox().isEmpty())
    }

    @Test
    fun aStepOfARepeatingTaskStaysInTheSeriesWhenItsTaskIsClearedHereAndCanNotBeMovedOut() = runBlocking {
        val original = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        run()
        val plants = rowsOn(DAY, "Water plants").single()
        val kitchen = row(1, "Kitchen") { repo.setParentTask(it, plants) }  // a subtask here: a step there
        run()
        val step = TodoFields.stepRemoteId(original, todo.stepsOf(original).single().text("id"))
        assertEquals(step, sync.getLink(kitchen)?.remoteId)
        val writes = todo.writes.size

        repo.clearTaskSlot(task(plants), CANVAS_HEIGHT)  // the repeating task cleared here (owner decision 89)
        run()
        assertTrue("nothing deleted, made or moved: ${todo.writes.drop(writes)}", todo.writes.drop(writes).isEmpty())
        assertEquals("still its step", listOf("Kitchen"), todo.stepsOf(original).map { it.text("displayName") })
        assertEquals("its parent there as it is: never taken as left", original, sync.getLink(kitchen)!!.remoteParentId)

        // A later move to another list can't take it out of the series
        todo.addList(WORK, "Work")
        sync.insertList(TaskSyncList(TaskSyncProvider.MICROSOFT_TODO, EMAIL, "", WORK, "Work"))
        assertEquals(TaskSyncListMover.Outcome.SUBTASK, TaskSyncListMover(db, provider, EMAIL).move(kitchen, WORK))
        assertEquals(step, sync.getLink(kitchen)?.remoteId)
        assertEquals(1, todo.stepsOf(original).size)
        assertTrue(todo.writes.drop(writes).isEmpty())
    }

    @Test
    fun aLostDeleteAnswerDuringAMoveLeavesOneCopyWithEveryLinkOnIt() = runBlocking {
        todo.addList(WORK, "Work")
        sync.insertList(TaskSyncList(TaskSyncProvider.MICROSOFT_TODO, EMAIL, "", WORK, "Work"))
        val trip = row(0, "Plan trip")
        val hotel = row(1, "Book hotel") { repo.setParentTask(it, trip) }
        val bags = row(2, "Pack bags") { repo.setParentTask(it, trip) }
        run()
        val oldId = todo.tasksIn(TASKS).single().text("id")
        // Microsoft deletes the old copy, but its answer never arrives
        todo.loseReplyTo = { method, path -> method == "DELETE" && path.endsWith("/tasks/$oldId") }

        assertEquals(TaskSyncListMover.Outcome.MOVED, TaskSyncListMover(db, provider, EMAIL).move(trip, WORK))

        assertTrue("the old one is gone", todo.tasksIn(TASKS).isEmpty())
        val copy = todo.tasksIn(WORK).single().text("id")
        assertEquals("one copy, never none", 2, todo.stepsOf(copy).size)
        assertEquals(WORK to copy, sync.getLink(trip)!!.let { it.listId to it.remoteId })
        for (row in listOf(hotel, bags)) assertEquals(copy, sync.getLink(row)!!.let { TodoFields.taskOf(it.remoteId) })
    }

    @Test
    fun aSourceDeleteRefusedForCertainTakesTheNewCopyBack() = runBlocking {
        todo.addList(WORK, "Work")
        sync.insertList(TaskSyncList(TaskSyncProvider.MICROSOFT_TODO, EMAIL, "", WORK, "Work"))
        val trip = row(0, "Plan trip")
        row(1, "Book hotel") { repo.setParentTask(it, trip) }
        run()
        val oldId = todo.tasksIn(TASKS).single().text("id")
        todo.refuse = { method, path -> if (method == "DELETE" && path.endsWith("/tasks/$oldId")) 409 else null }

        val failed = runCatching { TaskSyncListMover(db, provider, EMAIL).move(trip, WORK) }

        assertTrue(failed.isFailure)
        assertEquals("the old one stays whole", 1, todo.stepsOf(oldId).size)
        assertTrue("the new one is taken back: nothing doubled", todo.tasksIn(WORK).isEmpty())
        assertEquals(TASKS to oldId, sync.getLink(trip)!!.let { it.listId to it.remoteId })
    }

    @Test
    fun aSourceDeleteThatMayStillHappenNeverTakesTheNewCopyBack() = runBlocking {
        todo.addList(WORK, "Work")
        sync.insertList(TaskSyncList(TaskSyncProvider.MICROSOFT_TODO, EMAIL, "", WORK, "Work"))
        val trip = row(0, "Plan trip")
        val hotel = row(1, "Book hotel") { repo.setParentTask(it, trip) }
        run()
        val oldId = todo.tasksIn(TASKS).single().text("id")
        // A server error: the old one is still there when read again, but the delete may go through late
        todo.refuse = { method, path -> if (method == "DELETE" && path.endsWith("/tasks/$oldId")) 500 else null }

        val failed = runCatching { TaskSyncListMover(db, provider, EMAIL).move(trip, WORK) }

        assertTrue(failed.isFailure)
        val copy = todo.tasksIn(WORK).single().text("id")
        assertEquals("the new one is never deleted", 1, todo.stepsOf(copy).size)
        assertEquals("nor the old one", 1, todo.stepsOf(oldId).size)

        // It did go through late: the next pull follows the one copy left (owner decision 49)
        todo.refuse = { _, _ -> null }
        todo.deleteTask(TASKS, oldId)
        run()
        assertEquals("one copy, never none, the link on it", WORK to copy, sync.getLink(trip)!!.let { it.listId to it.remoteId })
        assertEquals(copy, TodoFields.taskOf(sync.getLink(hotel)!!.remoteId))
        assertEquals("nothing doubled here", 1, tasks.getTasksForDay(DAY).count { it.typedText == "Plan trip" })
    }

    @Test
    fun aStepFromThePhoneNeverTakesTheUsersOwnTaskWithTheSameWords() = runBlocking {
        row(0, "Plan trip")
        run()
        val parent = todo.tasksIn(TASKS).single().text("id")
        val mine = row(1, "Pack bags")  // the user's own task on the same day
        val step = todo.addStep(TASKS, parent, "Pack bags")
        run()

        assertTrue("the user's task went as its own task", sync.getLink(mine)!!.remoteId.let { !TodoFields.isStep(it) })
        val stepRow = tasks.getTasksForDay(DAY).single { it.typedText == "Pack bags" && it.id != mine }.id
        assertEquals(TodoFields.stepRemoteId(parent, step), sync.getLink(stepRow)?.remoteId)

        todo.deleteStep(TASKS, parent, step)  // deleted on the phone
        run()
        assertEquals("the user's task is untouched", "Pack bags", task(mine).typedText)
        assertNull("the step's row went", task(stepRow).typedText)
    }

    @Test
    fun aSubtaskNotSentYetIsNeverTakenByAStepWithTheSameWords() = runBlocking {
        val trip = row(0, "Plan trip")
        run()
        val parent = todo.tasksIn(TASKS).single().text("id")
        val bags = row(1, "Pack bags") { repo.setParentTask(it, trip) }  // a subtask here, not sent yet
        val phoneStep = TodoFields.stepRemoteId(parent, todo.addStep(TASKS, parent, "Pack bags"))  // the same words on the phone
        run()  // the pull goes first: no create of it was sent, so the phone's step isn't it

        val stepRow = linkedTo(phoneStep)!!.taskId
        assertTrue("the phone's step comes as its own row", stepRow != bags)
        assertEquals("Pack bags", task(stepRow).typedText)
        val mine = sync.getLink(bags)!!.remoteId
        assertTrue("the tablet's subtask went as its own step", mine != phoneStep && TodoFields.taskOf(mine) == parent)
        assertEquals(2, todo.stepsOf(parent).size)
    }

    @Test
    fun aSubtaskWhoseCreateAnswerWasLostIsLinkedNotDoubled() = runBlocking {
        val trip = row(0, "Plan trip")
        run()
        todo.loseReplyTo = { method, path -> method == "POST" && path.endsWith("/checklistItems") }
        val hotel = row(1, "Book hotel") { repo.setParentTask(it, trip) }
        run()  // the step is made, its answer lost
        todo.loseReplyTo = { _, _ -> false }

        run()
        val parent = todo.tasksIn(TASKS).single().text("id")
        val step = todo.stepsOf(parent).single()
        assertEquals("its own send's step, waiting for its answer", TodoFields.stepRemoteId(parent, step.text("id")), sync.getLink(hotel)?.remoteId)
        assertEquals(1, tasks.getTasksForDay(DAY).count { it.typedText == "Book hotel" })
    }

    // ── Helpers ──

    /** A typed task on [DAY]'s first page. */
    private fun row(index: Int, words: String, configure: (String) -> Unit = {}): String {
        val id = repo.getOrCreateTasksForPage(DAY, 0).first[index].id
        repo.setTypedText(id, words)
        configure(id)
        repo.invalidateTaskCache()
        return id
    }

    private fun task(id: String): Task = tasks.getById(id)!!

    /** The rows on [day] with these words, in this order. */
    private fun rowsOn(day: String, vararg words: String): List<String> {
        val rows = tasks.getTasksForDay(day)
        return words.map { w -> rows.single { it.typedText == w }.id }
    }

    /** Waiting sends go in the next run, whatever their wait. */
    private fun sendWaitingNow() = sync.getOutbox().forEach { sync.markOutboxFailed(it.id, it.attempts, 0L, it.lastError) }

    private fun linkedTo(remoteId: String) = sync.getLinkByRemote(TaskSyncProvider.MICROSOFT_TODO, EMAIL, "", TASKS, remoteId)

    private fun patches() = todo.writes.count { it.startsWith("PATCH") }

    /** [row] stays ticked on [day], kept on the tablet only, its link off [remoteId] (owner decision 100). */
    private fun assertKeptTicked(row: String, day: String, remoteId: String) {
        val link = sync.getLink(row)!!
        assertTrue("kept on the tablet", link.keptOnTablet)
        assertEquals(TaskSyncKept.ROLLED_OVER, link.keptReason)
        assertTrue("its id is free for the next one", link.remoteId != remoteId)
        assertTrue("ticked", task(row).isCompleted)
        assertEquals("on its day", day, task(row).dayDate)
    }

    private fun repeating(title: String, dueUtc: String): JsonObject = buildJsonObject {
        put("title", title)
        put("dueDateTime", FakeTodoServer.date(dueUtc, "UTC"))
        put("recurrence", FakeTodoServer.daily("2026-11-25"))
    }

    private fun JsonObject.text(key: String): String = this[key]!!.jsonPrimitive.content

    private fun deleteDatabaseFiles() {
        val file = File(getDbDir(context), APP_DATABASE_FILE_NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
    }

    private companion object {
        const val EMAIL = "me@outlook.com"
        const val TASKS = "AAMk-tasks="
        const val WORK = "AAMk-work="
        const val DAY = "2026-11-25"
        const val NEXT_DAY = "2026-11-26"
        const val CANVAS_HEIGHT = 1386f
    }
}
