package com.boox.day.tasksync.microsoft

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.TaskSyncLink
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.sync.microsoft.MicrosoftGraph
import com.boox.day.tasksync.OutgoingTask
import com.boox.day.tasksync.RemoteList
import com.boox.day.tasksync.TaskExtras
import com.boox.day.tasksync.TaskSyncServiceException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Microsoft To Do as task sync's service (owner decisions 79-84), against Graph's answers as
 * measured on 25 Nov 2026 (FakeTodoServer): the lists offered, delta's pages and cursor, dates
 * across time zones, reminders, the star, moving between lists, and failures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MicrosoftTodoProviderTest {

    private val server = MockWebServer()
    private val todo = FakeTodoServer()
    private var zone: ZoneId = WARSAW
    private val links = mutableListOf<TaskSyncLink>()

    @Before
    fun start() {
        server.dispatcher = todo
        server.start()
        todo.addList(TASKS, "Tasks", wellKnown = "defaultList")
    }

    @After
    fun stop() = server.shutdown()

    private fun provider(leftCopies: LeftCopies? = null) = MicrosoftTodoProvider(
        MicrosoftGraph(token = { "token" }, baseUrl = server.url("/v1.0").toString().trimEnd('/')),
        linksIn = { listId -> links.filter { it.listId == listId } },
        zone = { zone },
        leftCopies = leftCopies
    )

    // ── Lists (owner decision 84) ──

    @Test
    fun flaggedEmailIsNotOfferedAndTasksIsTheMainList() = runBlocking {
        todo.addList("flagged", "Flagged email", wellKnown = "flaggedEmails")
        todo.addList("groceries", "Groceries")

        assertEquals(listOf(RemoteList(TASKS, "Tasks"), RemoteList("groceries", "Groceries")), provider().lists())
        assertEquals(RemoteList(TASKS, "Tasks"), provider().mainList())
    }

    // ── Reading ──

    @Test
    fun deltaFollowsEveryPageAndTheNextRoundStartsWhereItEnded() = runBlocking {
        todo.deltaPageSize = 2
        repeat(5) { todo.addTask(TASKS, task("Task $it")) }

        val first = provider().changes(TASKS, null)
        assertEquals((0 until 5).map { "Task $it" }, first.tasks.map { it.title })
        assertEquals("three pages", 3, todo.requests.count { "/delta" in it })
        assertTrue("the round's end is the cursor: ${first.nextCursor}", first.nextCursor!!.contains("deltatoken"))

        todo.patchTask(TASKS, todo.tasksIn(TASKS)[1].str("id"), buildJsonObject { put("title", "Task 1 later") })
        val next = provider().changes(TASKS, first.nextCursor)
        assertEquals("only what changed since", listOf("Task 1 later"), next.tasks.map { it.title })
        assertTrue("delta refuses \$top (measured 25 Nov 2026)", todo.requests.filter { "/delta" in it }.none { "top" in it })
    }

    @Test
    fun aCursorMicrosoftForgotStartsAgainFromTheBeginning() = runBlocking {
        todo.addTask(TASKS, task("Call Anna"))
        val first = provider().changes(TASKS, null)
        todo.expired += first.nextCursor!!.substringAfter("deltatoken=")

        val again = provider().changes(TASKS, first.nextCursor)

        assertEquals("everything again, which is harmless", listOf("Call Anna"), again.tasks.map { it.title })
        assertEquals("a new round from the start", 2, todo.requests.count { "/delta" in it && "token" !in it })
    }

    @Test
    fun aDeletedListIsListGoneAndAMailboxWithoutToDoSaysSo() = runBlocking {
        try {
            provider().changes("deleted-list", null)
            fail("expected ListGone")
        } catch (e: TaskSyncServiceException.ListGone) {
            assertEquals("deleted-list", e.listId)
        }
        val noToDo = MockWebServer()
        noToDo.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"code":"ErrorAccessDenied"}}"""))
        noToDo.start()
        try {
            MicrosoftTodoProvider(MicrosoftGraph(token = { "t" }, baseUrl = noToDo.url("/v1.0").toString().trimEnd('/'))).lists()
            fail("expected NotAvailable")
        } catch (e: TaskSyncServiceException.NotAvailable) {
            // The card says the account has no To Do (TaskSyncStart)
        } finally {
            noToDo.shutdown()
        }
    }

    // ── Dates (measured 25 Nov 2026): always UTC worked out on the tablet; a due day is midnight UTC ──

    @Test
    fun aDueDayGoesAsMidnightUtcAndAReminderAsItsMomentEastOfUtc() = runBlocking {
        zone = WARSAW
        val sent = provider().upsert(outgoing("Pay rent", due = "2026-11-26", reminder = 9 * 60 + 30), null)

        val stored = todo.task(TASKS, sent.remoteId)!!
        assertEquals("the day as To Do keeps due days", "2026-11-26T00:00:00.0000000", stored.obj("dueDateTime").str("dateTime"))
        assertEquals("9:30 in Warsaw", "2026-11-26T08:30:00.0000000", stored.obj("reminderDateTime").str("dateTime"))
        assertEquals("never a zone's name", listOf("UTC", "UTC"), todo.sentZones)
        val read = provider().changes(TASKS, null).tasks.single()
        assertEquals("2026-11-26", read.dueDay)
        assertEquals(TaskExtras("", 9 * 60 + 30), read.extras)
    }

    @Test
    fun aDueDayGoesAsMidnightUtcAndAReminderAsItsMomentWestOfUtc() = runBlocking {
        zone = ZoneOffset.ofHours(-8)
        val sent = provider().upsert(outgoing("Pay rent", due = "2026-11-26", reminder = 20 * 60), null)

        val stored = todo.task(TASKS, sent.remoteId)!!
        assertEquals("2026-11-26T00:00:00.0000000", stored.obj("dueDateTime").str("dateTime"))
        assertEquals("the evening reminder is the next day in UTC", "2026-11-27T04:00:00.0000000", stored.obj("reminderDateTime").str("dateTime"))
        assertEquals(listOf("UTC", "UTC"), todo.sentZones)
        val read = provider().changes(TASKS, null).tasks.single()
        assertEquals("2026-11-26", read.dueDay)
        assertEquals("still on its due day here", 20 * 60, read.extras?.reminderMinute)
    }

    @Test
    fun whatBooxDaySendsReadsBackAsTheSameDayInEveryZone() = runBlocking {
        for (tabletZone in listOf(WARSAW, NEW_YORK, ZoneOffset.ofHours(-7), ZoneOffset.ofHours(-8), ZoneOffset.ofHours(13))) {
            zone = tabletZone
            val sent = provider().upsert(outgoing("Day in $tabletZone", due = "2026-11-26", reminder = 23 * 60 + 30), null)
            val read = provider().fetch(TASKS, sent.remoteId)!!
            assertEquals("$tabletZone", "2026-11-26", read.dueDay)
            assertEquals("$tabletZone", 23 * 60 + 30, read.extras?.reminderMinute)
        }
        assertTrue(todo.sentZones.all { it == "UTC" })
    }

    @Test
    fun aDueDayReadsAsTheTabletsDayWhoeverWroteIt() {
        val midnightUtc = FakeTodoServer.date("2026-11-26T00:00:00.0000000", "UTC")
        assertEquals("To Do's own way, west of UTC", "2026-11-26", TodoFields.dayOf(midnightUtc, ZoneOffset.ofHours(-7)))
        assertEquals("To Do's own way, east of UTC", "2026-11-26", TodoFields.dayOf(midnightUtc, WARSAW))
        // Another app (or an older send) wrote the tablet's local midnight
        assertEquals("2026-11-26", TodoFields.dayOf(FakeTodoServer.date("2026-11-25T23:00:00.0000000", "UTC"), WARSAW))
        assertEquals("2026-11-26", TodoFields.dayOf(FakeTodoServer.date("2026-11-26T08:00:00.0000000", "UTC"), ZoneOffset.ofHours(-8)))
    }

    // ── Reminders (owner decision 81, like CalDAV's 69) ──

    @Test
    fun aReminderOnTheDueDayIsTheTasksOwnTimeAndAnyOtherIsTheUsualTime() = runBlocking {
        zone = WARSAW
        val due = FakeTodoServer.date("2026-11-25T23:00:00.0000000", "UTC")  // 26 Nov
        todo.addTask(TASKS, task("On the day") + mapOf("dueDateTime" to due, "isReminderOn" to JsonPrimitive(true), "reminderDateTime" to FakeTodoServer.date("2026-11-26T07:15:00", "UTC")))
        todo.addTask(TASKS, task("Day before") + mapOf("dueDateTime" to due, "isReminderOn" to JsonPrimitive(true), "reminderDateTime" to FakeTodoServer.date("2026-11-25T07:15:00", "UTC")))
        todo.addTask(TASKS, task("Turned off") + mapOf("dueDateTime" to due, "isReminderOn" to JsonPrimitive(false), "reminderDateTime" to FakeTodoServer.date("2026-11-26T07:15:00", "UTC")))

        val minutes = provider().changes(TASKS, null).tasks.associate { it.title to it.extras?.reminderMinute }

        assertEquals(mapOf("On the day" to 8 * 60 + 15, "Day before" to null, "Turned off" to null), minutes)
    }

    @Test
    fun theUsualTimeSendsNoReminderAndTurnsOffTheOneBooxDaySent() = runBlocking {
        val p = provider()
        val made = p.upsert(outgoing("Call Anna", due = "2026-11-26"), null)
        assertEquals("the usual time: none sent", false, todo.task(TASKS, made.remoteId)!!["isReminderOn"]!!.jsonPrimitive.content.toBoolean())

        // Its own time, then the usual time again
        val link = linkOf(made, "Call Anna", due = "2026-11-26")
        val withTime = p.upsert(outgoing("Call Anna", due = "2026-11-26", reminder = 10 * 60), link)
        assertEquals("true", todo.task(TASKS, made.remoteId)!!["isReminderOn"]!!.jsonPrimitive.content)
        p.upsert(outgoing("Call Anna", due = "2026-11-26"), link.copy(remoteVersion = withTime.version, syncedReminder = 10 * 60))
        assertEquals("false", todo.task(TASKS, made.remoteId)!!["isReminderOn"]!!.jsonPrimitive.content)
    }

    // ── Changing a copy only as it was read (If-Match, measured 25 Nov 2026) ──

    @Test
    fun aChangeMadeInToDoSinceTheReadIsNeverWrittenOver() = runBlocking {
        val p = provider()
        val made = p.upsert(outgoing("Call Anna", due = "2026-11-26"), null)
        todo.meanwhile = { method, _ ->
            if (method == "PATCH") todo.patchTask(TASKS, made.remoteId, mapOf("title" to JsonPrimitive("Call Anna and Bo")))
        }

        try {
            p.upsert(outgoing("Call Anna today", due = "2026-11-26"), linkOf(made, "Call Anna", "2026-11-26"))
            fail("expected RemoteChanged")
        } catch (e: TaskSyncServiceException.RemoteChanged) {
            assertEquals("the merge takes To Do's words in first", "Call Anna and Bo", e.remote.title)
        }
        assertEquals("nothing written over them", "Call Anna and Bo", todo.task(TASKS, made.remoteId)!!.str("title"))
        assertEquals(1, todo.ifMatches.size)
    }

    // ── Priority (owner decision 80): one mapping ──

    @Test
    fun priorityAIsTheStarAndBAndCStayOnTheTablet() {
        assertEquals(listOf("high", "normal", "normal", "normal"), listOf("A", "B", "C", "").map(TodoFields::importanceOf))
        assertEquals(listOf("A", "", ""), listOf("high", "normal", "low").map(TodoFields::priorityOf))
        assertEquals("B and C read back as none, so a copy without a star never clears them", listOf("A", "", "", ""), listOf("A", "B", "C", "").map(TodoFields::keptPriority))
    }

    // ── Moving to another list (owner decision 83) ──

    @Test
    fun aMoveCopiesEverythingWithItsStepsAndDeletesTheOldOne() = runBlocking {
        todo.addList(WORK, "Work")
        val id = todo.addTask(
            TASKS,
            task("Plan trip") + mapOf(
                "importance" to JsonPrimitive("high"),
                "body" to buildJsonObject { put("content", "Ask about the visa"); put("contentType", "text") },
                "dueDateTime" to FakeTodoServer.date("2026-11-27T23:00:00", "UTC"),
                "categories" to kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("Travel")))
            )
        )
        val hotel = todo.addStep(TASKS, id, "Book hotel")
        todo.addStep(TASKS, id, "Pack bags", ticked = true)
        val p = provider()

        val moved = p.moveToList(TASKS, id, WORK)

        assertNull("the old one is deleted", todo.task(TASKS, id))
        val copy = todo.task(WORK, moved.remoteId)!!
        assertEquals("Ask about the visa", copy.obj("body").str("content"))
        assertEquals("high", copy.str("importance"))
        assertEquals("2026-11-27T23:00:00.0000000", copy.obj("dueDateTime").str("dateTime"))
        assertEquals(listOf("Book hotel" to false, "Pack bags" to true), todo.stepsOf(moved.remoteId).map { it.str("displayName") to (it["isChecked"]!!.jsonPrimitive.content == "true") })
        val along = p.takenAlong(WORK, TodoFields.stepRemoteId(id, hotel))!!
        assertEquals("the step under its new id", "Book hotel", along.title)
        assertEquals(moved.remoteId, along.parentRemoteId)
    }

    @Test
    fun aRepeatingTaskOneOfItsStepsOrATaskWithAttachmentsIsNotMoved() = runBlocking {
        todo.addList(WORK, "Work")
        val repeating = todo.addTask(TASKS, task("Water plants") + ("recurrence" to FakeTodoServer.daily("2026-11-25")))
        val step = TodoFields.stepRemoteId(repeating, todo.addStep(TASKS, repeating, "Kitchen"))
        val withFile = todo.addTask(TASKS, task("Sign lease") + ("hasAttachments" to JsonPrimitive(true)))
        val writesBefore = todo.writes.size

        // A step of a repeating task moved out would be deleted from the series (owner decision 89)
        for (id in listOf(repeating, step, withFile)) {
            try {
                provider().moveToList(TASKS, id, WORK)
                fail("expected Refused")
            } catch (e: TaskSyncServiceException.Refused) {
                // It stays in its list (TaskSyncListMover.Outcome.REFUSED)
            }
        }
        assertEquals("nothing written", writesBefore, todo.writes.size)
        assertEquals(2, todo.tasksIn(TASKS).size)
        assertEquals("the step stays in its series", 1, todo.stepsOf(repeating).size)
    }

    @Test
    fun aCopyAMoveLeftIsNeverDeletedWhenHowItWasCopiedIsNotKnown() = runBlocking {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("left-copies-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val left = LeftCopies(prefs, "me@outlook.com")
        val id = todo.addTask(TASKS, task("Plan trip"))
        left.left(TASKS, id, was = null)  // no etag saved with it

        try {
            provider(left).delete(TASKS, id)
            fail("expected LeftCopyChanged")
        } catch (e: TaskSyncServiceException.LeftCopyChanged) {
            assertEquals("its list is read in full next, so it comes in", TASKS, e.listId)
        }
        assertTrue("not deleted", todo.task(TASKS, id) != null)
        assertTrue(todo.writes.none { it.startsWith("DELETE") })
    }

    // ── Repeats (owner decision 100): the next date, only when it can be worked out ──

    @Test
    fun theNextDateOfEachRepeatToDoHas() {
        fun next(pattern: JsonObject, after: String) =
            TodoFields.nextDue(buildJsonObject { put("pattern", pattern) }, LocalDate.parse(after))?.toString()
        fun pattern(type: String, interval: Int = 1, more: JsonObjectBuilder.() -> Unit = {}) =
            buildJsonObject { put("type", type); put("interval", interval); more() }
        fun days(vararg names: String) = JsonArray(names.map(::JsonPrimitive))

        assertEquals("2026-11-26", next(pattern("daily"), "2026-11-25"))
        assertEquals("every 3 days", "2026-11-28", next(pattern("daily", 3), "2026-11-25"))
        // 25 Nov 2026 is a Wednesday; weeks start on Sunday
        val monWedFri = pattern("weekly") { put("daysOfWeek", days("monday", "wednesday", "friday")); put("firstDayOfWeek", "sunday") }
        assertEquals("the next of its days that week", "2026-11-27", next(monWedFri, "2026-11-25"))
        assertEquals("the week after", "2026-11-30", next(monWedFri, "2026-11-27"))
        val everyOtherMonday = pattern("weekly", 2) { put("daysOfWeek", days("monday")); put("firstDayOfWeek", "sunday") }
        assertEquals("2026-12-07", next(everyOtherMonday, "2026-11-23"))
        assertEquals("the 31st in a shorter month: its last day", "2026-11-30", next(pattern("absoluteMonthly") { put("dayOfMonth", 31) }, "2026-10-31"))
        assertEquals("every 2 months", "2027-01-15", next(pattern("absoluteMonthly", 2) { put("dayOfMonth", 15) }, "2026-11-15"))
        assertEquals("the last Friday", "2026-12-25", next(pattern("relativeMonthly") { put("daysOfWeek", days("friday")); put("index", "last") }, "2026-11-27"))
        assertEquals("the first Monday", "2026-12-07", next(pattern("relativeMonthly") { put("daysOfWeek", days("monday")); put("index", "first") }, "2026-11-02"))
        assertEquals("2027-11-25", next(pattern("absoluteYearly") { put("month", 11); put("dayOfMonth", 25) }, "2026-11-25"))
        assertEquals("the fourth Thursday of November", "2027-11-25",
            next(pattern("relativeYearly") { put("month", 11); put("daysOfWeek", days("thursday")); put("index", "fourth") }, "2026-11-26"))

        // Several days: one date a month, the first that fits (each day's own one, the earliest of them)
        val secondMonWed = pattern("relativeMonthly") { put("daysOfWeek", days("monday", "wednesday")); put("index", "second") }
        assertEquals("November's is the 9th (second Monday), never the 11th as well: December's, its second Wednesday",
            "2026-12-09", next(secondMonWed, "2026-11-09"))
        assertEquals("January's: its second Monday", "2027-01-11", next(secondMonWed, "2026-12-09"))
        val lastMonFri = pattern("relativeMonthly") { put("daysOfWeek", days("monday", "friday")); put("index", "last") }
        assertEquals("November's is the 27th (last Friday), never the 30th as well", "2026-12-25", next(lastMonFri, "2026-11-27"))
        assertEquals("yearly the same way", "2027-11-08",
            next(pattern("relativeYearly") { put("month", 11); put("daysOfWeek", days("monday", "wednesday")); put("index", "second") }, "2026-11-09"))

        assertNull("a pattern To Do doesn't have", next(pattern("hourly"), "2026-11-25"))
        assertNull("weekly without its days", next(pattern("weekly"), "2026-11-25"))
        assertNull("no interval", next(buildJsonObject { put("type", "daily") }, "2026-11-25"))
    }

    // ── Deleting (owner decision 89) ──

    @Test
    fun aRepeatingTaskAndItsStepsAreNeverDeletedInToDo() = runBlocking {
        val plain = todo.addTask(TASKS, task("Call Anna"))
        val repeating = todo.addTask(
            TASKS,
            task("Water plants") + mapOf(
                "recurrence" to FakeTodoServer.daily("2026-11-25"),
                "dueDateTime" to FakeTodoServer.date("2026-11-25T00:00:00", "UTC")
            )
        )
        val step = todo.addStep(TASKS, repeating, "Kitchen")
        val p = provider()

        p.delete(TASKS, repeating)
        p.delete(TASKS, TodoFields.stepRemoteId(repeating, step))
        p.delete(TASKS, plain)
        p.delete(TASKS, plain)  // already gone: done

        assertEquals("only the plain task is deleted", 1, todo.writes.count { it.startsWith("DELETE") })
        assertNull(todo.task(TASKS, plain))
        assertTrue("the series goes on in To Do", todo.task(TASKS, repeating) != null)
        assertEquals(1, todo.stepsOf(repeating).size)
    }

    // ── Helpers ──

    private fun task(title: String): JsonObject = buildJsonObject { put("title", title) }

    private fun outgoing(title: String, due: String?, reminder: Int? = null, priority: String = "", parent: String? = null) = OutgoingTask(
        taskId = "row-1", listId = TASKS, title = title, completed = false, dueDay = due, parentRemoteId = parent,
        extras = TaskExtras(priority, reminder)
    )

    private fun linkOf(made: com.boox.day.tasksync.RemoteTask, title: String, due: String) = TaskSyncLink(
        taskId = "row-1", provider = TaskSyncProvider.MICROSOFT_TODO, accountEmail = "me@outlook.com", listId = TASKS,
        remoteId = made.remoteId, remoteVersion = made.version, syncedTitle = title, syncedDueDay = due,
        syncedPriority = "", syncedReminder = null, syncedAt = 1
    )

    private fun JsonObject.str(key: String): String = this[key]!!.jsonPrimitive.content
    private fun JsonObject.obj(key: String): JsonObject = this[key]!!.jsonObject

    private companion object {
        const val TASKS = "AAMk-tasks="
        const val WORK = "AAMk-work="
        val WARSAW: ZoneId = ZoneId.of("Europe/Warsaw")
        val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
    }
}
