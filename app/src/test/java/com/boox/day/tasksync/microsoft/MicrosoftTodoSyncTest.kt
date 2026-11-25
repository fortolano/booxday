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
            zone = { ZoneId.of("Europe/Warsaw") },
            pause = {}
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

    /** One run as TaskSyncRun does it: tablet changes noticed, the service read, then the outbox sent. */
    private suspend fun run() {
        TaskSyncScanner(db, extras = { true }) { { true } }.scan(System.currentTimeMillis(), DAY, wordsSettled = true)
        val merger = merger()
        TaskSyncPuller(db, provider, merger, EMAIL).pull()
        TaskSyncSender(db, provider, merger, EMAIL, prefs = TaskSync.prefs(context)).sendDue()
    }

    private fun merger() = TaskSyncMerger(
        db, RepositoryTabletTasks(context, repo, canvasHeight = { CANVAS_HEIGHT }, penOn = { false }),
        TaskSyncProvider.MICROSOFT_TODO, EMAIL, today = { DAY }, service = provider
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

    // ── Repeating tasks: To Do keeps the id for the next one (owner decision 82, measured 25 Nov 2026) ──

    @Test
    fun aRepeatingTaskTickedHereStaysTickedOnItsDayAndTheNextOneComes() = runBlocking {
        val original = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))  // 25 Nov, as To Do keeps due days
        run()
        val plants = tasks.getTasksForDay(DAY).single { it.typedText == "Water plants" }.id

        repo.setCompleted(plants, true)
        run()
        val done = todo.tasksIn(TASKS).single { it.text("status") == "completed" }
        assertEquals("the tablet's task is the done copy now", done.text("id"), sync.getLink(plants)?.remoteId)
        assertEquals("one PATCH, nothing else", listOf("PATCH"), todo.writes.map { it.substringBefore(' ') })

        run()
        assertTrue("ticked on its day", task(plants).isCompleted)
        assertEquals(DAY, task(plants).dayDate)
        val next = tasks.getTasksForDay(NEXT_DAY).single { it.typedText == "Water plants" }
        assertTrue("the next one comes unticked", !next.isCompleted)
        assertEquals(original, sync.getLink(next.id)?.remoteId)
        assertEquals("nothing deleted or sent twice", 1, todo.writes.size)
    }

    @Test
    fun aRepeatingTaskTickedOnThePhoneIsTickedHereAndTheNextOneComes() = runBlocking {
        val original = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        run()
        val plants = tasks.getTasksForDay(DAY).single { it.typedText == "Water plants" }.id

        todo.patchTask(TASKS, original, mapOf("status" to JsonPrimitive("completed")))
        run()

        assertTrue("ticked here, on its day", task(plants).isCompleted)
        assertEquals(DAY, task(plants).dayDate)
        val done = todo.tasksIn(TASKS).single { it.text("status") == "completed" }
        assertEquals(done.text("id"), sync.getLink(plants)?.remoteId)
        val next = tasks.getTasksForDay(NEXT_DAY).single { it.typedText == "Water plants" }
        assertTrue(!next.isCompleted)
        assertEquals(original, sync.getLink(next.id)?.remoteId)
        assertTrue("nothing written back: ${todo.writes}", todo.writes.isEmpty())
        run()
        assertTrue("and nothing later: ${todo.writes}", todo.writes.isEmpty())
    }

    @Test
    fun aRepeatingTaskWithStepsTickedHereKeepsItsTicksOnItsDayAndTheNextOnesStepsCome() = runBlocking {
        val original = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        val kitchen = todo.addStep(TASKS, original, "Kitchen")
        val balcony = todo.addStep(TASKS, original, "Balcony")
        run()
        val (plants, kitchenRow, balconyRow) = rowsOn(DAY, "Water plants", "Kitchen", "Balcony")

        repo.setCompleted(kitchenRow, true)
        repo.setCompleted(plants, true)
        run()
        val done = todo.tasksIn(TASKS).single { it.text("status") == "completed" }.text("id")
        assertEquals(done, sync.getLink(plants)?.remoteId)
        assertEquals("the subtasks follow the done copy, same step ids", listOf(kitchen, balcony).map { TodoFields.stepRemoteId(done, it) },
            listOf(kitchenRow, balconyRow).map { sync.getLink(it)?.remoteId })
        assertEquals("the done copy keeps the tick", listOf(true, false), todo.stepsOf(done).map { it.text("isChecked").toBoolean() })
        val writes = todo.writes.size

        run()
        assertEquals("ticks stay on the done day", listOf(true, true, false), listOf(plants, kitchenRow, balconyRow).map { task(it).isCompleted })
        val next = rowsOn(NEXT_DAY, "Water plants", "Kitchen", "Balcony").toList()
        assertTrue("the next one and its steps come unticked", next.none { task(it).isCompleted })
        assertEquals(listOf(original) + listOf(kitchen, balcony).map { TodoFields.stepRemoteId(original, it) }, next.map { sync.getLink(it)?.remoteId })
        assertEquals("nothing sent twice", writes, todo.writes.size)
        assertTrue(todo.writes.none { it.startsWith("DELETE") })
    }

    @Test
    fun aRepeatingTaskWithStepsTickedOnThePhoneKeepsItsTicksHereAndTheNextOnesStepsCome() = runBlocking {
        val original = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        val kitchen = todo.addStep(TASKS, original, "Kitchen")
        val balcony = todo.addStep(TASKS, original, "Balcony")
        run()
        val (plants, kitchenRow, balconyRow) = rowsOn(DAY, "Water plants", "Kitchen", "Balcony")

        todo.tickStep(TASKS, original, kitchen, ticked = true)
        todo.patchTask(TASKS, original, mapOf("status" to JsonPrimitive("completed")))
        run()

        val done = todo.tasksIn(TASKS).single { it.text("status") == "completed" }.text("id")
        assertEquals(listOf(done) + listOf(kitchen, balcony).map { TodoFields.stepRemoteId(done, it) },
            listOf(plants, kitchenRow, balconyRow).map { sync.getLink(it)?.remoteId })
        assertEquals("ticked here as on the phone, on its day", listOf(true, true, false), listOf(plants, kitchenRow, balconyRow).map { task(it).isCompleted })
        val next = rowsOn(NEXT_DAY, "Water plants", "Kitchen", "Balcony").toList()
        assertTrue("the next one's steps were reset: unticked here", next.none { task(it).isCompleted })
        assertEquals(listOf(original) + listOf(kitchen, balcony).map { TodoFields.stepRemoteId(original, it) }, next.map { sync.getLink(it)?.remoteId })
        run()
        assertTrue("nothing written back: ${todo.writes}", todo.writes.isEmpty())
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
    fun aSourceDeleteThatDidNotHappenTakesTheNewCopyBack() = runBlocking {
        todo.addList(WORK, "Work")
        sync.insertList(TaskSyncList(TaskSyncProvider.MICROSOFT_TODO, EMAIL, "", WORK, "Work"))
        val trip = row(0, "Plan trip")
        row(1, "Book hotel") { repo.setParentTask(it, trip) }
        run()
        val oldId = todo.tasksIn(TASKS).single().text("id")
        todo.refuse = { method, path -> method == "DELETE" && path.endsWith("/tasks/$oldId") }

        val failed = runCatching { TaskSyncListMover(db, provider, EMAIL).move(trip, WORK) }

        assertTrue(failed.isFailure)
        assertEquals("the old one stays whole", 1, todo.stepsOf(oldId).size)
        assertTrue("the new one is taken back: nothing doubled", todo.tasksIn(WORK).isEmpty())
        assertEquals(TASKS to oldId, sync.getLink(trip)!!.let { it.listId to it.remoteId })
    }

    @Test
    fun twoAlikeSeriesNeverTakeEachOthersDoneCopy() = runBlocking {
        val first = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        val second = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        run()
        val firstRow = linkedTo(first)!!.taskId
        val secondRow = linkedTo(second)!!.taskId

        // On the phone: the second one's due day changed, the first one ticked
        todo.patchTask(TASKS, second, mapOf("dueDateTime" to FakeTodoServer.date("2026-11-27T00:00:00", "UTC")))
        todo.patchTask(TASKS, first, mapOf("status" to JsonPrimitive("completed")))
        run()

        val done = todo.tasksIn(TASKS).single { it.text("status") == "completed" }.text("id")
        assertNull("either could be its task: neither is re-pointed", linkedTo(done))
        // Each keeps its own copy, taken as it is (a task moved to a new day moves to a new row)
        assertEquals("2026-11-26", task(linkedTo(first)!!.taskId).dayDate)
        assertEquals("the edited one keeps its own copy and its new day", "2026-11-27", task(linkedTo(second)!!.taskId).dayDate)
        assertTrue(listOf(firstRow, secondRow).none { sync.getLink(it)?.remoteId == done })
    }

    @Test
    fun stepsTellTwoAlikeSeriesApart() = runBlocking {
        val first = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        todo.addStep(TASKS, first, "Kitchen")
        val second = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        todo.addStep(TASKS, second, "Balcony")
        run()
        val firstRow = linkedTo(first)!!.taskId
        val secondRow = linkedTo(second)!!.taskId

        todo.patchTask(TASKS, second, mapOf("dueDateTime" to FakeTodoServer.date("2026-11-27T00:00:00", "UTC")))
        todo.patchTask(TASKS, first, mapOf("status" to JsonPrimitive("completed")))
        run()

        val done = todo.tasksIn(TASKS).single { it.text("status") == "completed" }.text("id")
        assertEquals("the first one's steps make it certain", done, sync.getLink(firstRow)?.remoteId)
        assertTrue(task(firstRow).isCompleted)
        assertEquals(DAY, task(firstRow).dayDate)
        assertTrue("the other series isn't re-pointed", sync.getLink(secondRow)?.remoteId != done)
        assertEquals("2026-11-27", task(linkedTo(second)!!.taskId).dayDate)
    }

    @Test
    fun theTabletsTickTakesOnlyItsOwnDaysDoneCopy() = runBlocking {
        val mine = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        val other = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-24T00:00:00"))
        run()
        val mineRow = linkedTo(mine)!!.taskId
        val otherRow = linkedTo(other)!!.taskId

        todo.patchTask(TASKS, other, mapOf("status" to JsonPrimitive("completed")))  // a recent done copy of another series
        todo.hideDoneCopiesFromSearch = true  // this tick's own copy isn't listed by the search yet
        repo.setCompleted(mineRow, true)
        run()

        assertEquals("not the other series' copy", mine, sync.getLink(mineRow)?.remoteId)
        assertTrue("and not taken as sent", !sync.getLink(mineRow)!!.syncedCompleted)
        val otherDone = sync.getLink(otherRow)!!.remoteId
        assertTrue(otherDone != other)

        run()  // the next pull has the tick's own copy: its day, its steps
        val mineDone = sync.getLink(mineRow)!!.remoteId
        assertTrue(mineDone != mine && mineDone != otherDone)
        assertEquals("2026-11-25T00:00:00.0000000", todo.task(TASKS, mineDone)!!["dueDateTime"]!!.let { (it as JsonObject).text("dateTime") })
        assertEquals("ticked once", 1, patches())
    }

    @Test
    fun aTickWhoseDoneCopyIsListedLateNeverTicksTheNextOne() = runBlocking {
        val original = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        run()
        val plants = rowsOn(DAY, "Water plants").single()
        todo.hideDoneCopiesFromSearch = true
        todo.hideDoneCopiesFromDelta = true

        repo.setCompleted(plants, true)
        run()
        assertEquals(1, patches())
        assertEquals("not taken as sent", original to false, sync.getLink(plants)!!.let { it.remoteId to it.syncedCompleted })

        // The next one shows, its done copy still doesn't: the tick waits and is tried again
        sendWaitingNow()
        run()
        assertEquals("never ticked a second time", 1, patches())
        assertEquals(DAY, task(plants).dayDate)
        assertTrue(task(plants).isCompleted)
        assertEquals(original, sync.getLink(plants)?.remoteId)

        // Listed at last
        todo.hiddenFromSearch.clear()
        todo.hiddenFromDelta.clear()
        sendWaitingNow()
        run()
        val done = todo.tasksIn(TASKS).single { it.text("status") == "completed" }.text("id")
        assertEquals(done, sync.getLink(plants)?.remoteId)
        assertEquals("one tick in all", 1, patches())
        assertEquals("the next one isn't ticked", "notStarted", todo.task(TASKS, original)!!.text("status"))
        assertTrue(!task(rowsOn(NEXT_DAY, "Water plants").single()).isCompleted)
    }

    @Test
    fun aTakeoverNotDueOnTheLinksDayIsIgnored() = runBlocking {
        val original = todo.addTask(TASKS, repeating("Water plants", dueUtc = "2026-11-25T00:00:00"))
        run()
        val plants = rowsOn(DAY, "Water plants").single()

        merger().takeOver(
            com.boox.day.tasksync.RemoteTaskState(
                TASKS, "someone-elses-done-copy", "Water plants", true, "2026-11-20", null, false, "v9",
                takesOverRemoteId = original
            )
        )

        assertEquals(original, sync.getLink(plants)?.remoteId)
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
