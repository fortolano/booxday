package com.boox.day.tasksync.microsoft

import com.boox.day.data.db.TaskSyncLink
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.tasksync.OutgoingTask
import com.boox.day.tasksync.TaskSyncServiceException
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

/**
 * The Microsoft To Do connection against a small stand-in for Graph's To Do API: what is asked,
 * what is sent, how its delta query becomes the pull's cursor, and how its answers reach the sender.
 */
class MicrosoftToDoProviderTest {

    /** Graph's To Do for one account: lists of tasks, two per page, a delta query per list, every request recorded. */
    private class FakeGraph {
        val server = MockWebServer()
        val lists = linkedMapOf<String, JsonObject>()
        val tasks = linkedMapOf<String, LinkedHashMap<String, JsonObject>>()
        /** When each task last changed, and when a deleted one went (the delta token is this counter). */
        private val changedAt = HashMap<String, Int>()
        private val removedAt = linkedMapOf<String, LinkedHashMap<String, Int>>()
        val requests = mutableListOf<String>()
        val bodies = mutableListOf<JsonObject>()
        /** A canned answer for a request ("METHOD path"), or null to answer normally. */
        var answer: (String) -> MockResponse? = { null }
        var forgottenTokens = setOf<String>()
        private var version = 0
        private var nextId = 0

        init {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = handle(request)
            }
            server.start()
        }

        val provider = MicrosoftToDoProvider(OkHttpClient(), token = { TOKEN }, baseUrl = server.url("/v1.0").toString())

        fun list(id: String, title: String, wellknown: String = "none") {
            lists[id] = JsonObject().apply {
                addProperty("id", id)
                addProperty("displayName", title)
                addProperty("wellknownListName", wellknown)
            }
            tasks.getOrPut(id) { linkedMapOf() }
            removedAt.getOrPut(id) { linkedMapOf() }
        }

        fun task(listId: String, id: String, title: String, configure: JsonObject.() -> Unit = {}): JsonObject {
            val task = JsonObject().apply {
                addProperty("id", id)
                addProperty("title", title)
                addProperty("status", "notStarted")
                configure()
            }
            touch(listId, task)
            return task
        }

        fun due(day: String): JsonObject = JsonObject().apply {
            addProperty("dateTime", "${day}T00:00:00.0000000")
            addProperty("timeZone", "UTC")
        }

        fun delete(listId: String, id: String) {
            tasks.getValue(listId).remove(id)
            removedAt.getValue(listId)[id] = ++version
        }

        fun versionOf(listId: String, id: String): String = tasks.getValue(listId).getValue(id).get("lastModifiedDateTime").asString

        private fun touch(listId: String, task: JsonObject) {
            version++
            task.addProperty("lastModifiedDateTime", Instant.parse(T0).plusSeconds(version.toLong()).toString().replace("Z", ".0000000Z"))
            val id = task.get("id").asString
            tasks.getValue(listId)[id] = task
            changedAt[id] = version
        }

        private fun handle(request: RecordedRequest): MockResponse {
            val url = request.requestUrl!!
            val key = "${request.method} ${url.encodedPath}"
            requests += key + (url.encodedQuery?.let { "?$it" } ?: "")
            assertEquals("Bearer $TOKEN", request.getHeader("Authorization"))
            answer(key)?.let { return it }
            val parts = url.pathSegments  // v1.0, me, todo, lists, [L, tasks, [T|delta]]
            if (parts.size < 4 || parts[3] != "lists") return MockResponse().setResponseCode(400)
            val listId = parts.getOrNull(4)
            if (listId == null) return page(lists.values.toList(), url.toString(), url.queryParameter("\$skiptoken"))
            val list = tasks[listId] ?: return MockResponse().setResponseCode(404)
            if (parts.size == 5) return json(lists.getValue(listId))
            val taskId = parts.getOrNull(6)
            return when {
                taskId == "delta" -> delta(listId, url.queryParameter("\$deltatoken"), url.toString())
                taskId == null && request.method == "GET" -> {
                    val items = if (url.queryParameter("\$select") == "id") list.values.map { JsonObject().apply { add("id", it.get("id")) } } else list.values.toList()
                    page(items, url.toString(), url.queryParameter("\$skiptoken"))
                }
                taskId == null && request.method == "POST" -> {
                    val body = body(request)
                    val task = JsonObject().apply { addProperty("id", "t${++nextId}") }
                    body.entrySet().forEach { (name, value) -> task.add(name, value) }
                    touch(listId, task)
                    json(task).setResponseCode(201)
                }
                taskId != null && request.method == "GET" -> list[taskId]?.let(::json) ?: MockResponse().setResponseCode(404)
                taskId != null && request.method == "PATCH" -> {
                    val task = list[taskId] ?: return MockResponse().setResponseCode(404)
                    body(request).entrySet().forEach { (name, value) -> if (value is JsonNull) task.remove(name) else task.add(name, value) }
                    touch(listId, task)
                    json(task)
                }
                taskId != null && request.method == "DELETE" -> {
                    if (list[taskId] == null) return MockResponse().setResponseCode(404)
                    delete(listId, taskId)
                    MockResponse().setResponseCode(204)
                }
                else -> MockResponse().setResponseCode(405)
            }
        }

        private fun body(request: RecordedRequest): JsonObject =
            JsonParser.parseString(request.body.readUtf8()).asJsonObject.also { bodies += it }

        /** Two items a page, Graph's next link for the rest. */
        private fun page(items: List<JsonObject>, url: String, skip: String?): MockResponse {
            val from = skip?.toInt() ?: 0
            val body = JsonObject()
            body.add("value", com.google.gson.JsonArray().apply { items.drop(from).take(PAGE).forEach(::add) })
            if (from + PAGE < items.size) body.addProperty("@odata.nextLink", withQuery(url, "\$skiptoken", (from + PAGE).toString()))
            return json(body)
        }

        private fun delta(listId: String, token: String?, url: String): MockResponse {
            if (token != null && token in forgottenTokens) return MockResponse().setResponseCode(410)
            val since = token?.toInt() ?: 0
            val body = JsonObject()
            val value = com.google.gson.JsonArray()
            tasks.getValue(listId).values.filter { changedAt.getValue(it.get("id").asString) > since }.forEach(value::add)
            removedAt.getValue(listId).filter { it.value > since }.keys.forEach { id ->
                value.add(JsonObject().apply {
                    addProperty("id", id)
                    add("@removed", JsonObject().apply { addProperty("reason", "deleted") })
                })
            }
            body.add("value", value)
            body.addProperty("@odata.deltaLink", withQuery(url.substringBefore("?"), "\$deltatoken", version.toString()))
            return json(body)
        }

        /** [url] with [name] set to [value], its other parameters kept (as Graph's links keep $select). */
        private fun withQuery(url: String, name: String, value: String): String =
            url.toHttpUrl().newBuilder().removeAllEncodedQueryParameters(name).addEncodedQueryParameter(name, value).build().toString()

        private fun json(body: JsonObject) = MockResponse().setHeader("Content-Type", "application/json").setBody(body.toString())

        fun shutdown() = server.shutdown()

        companion object {
            const val TOKEN = "t0k3n"
            const val T0 = "2026-11-09T10:00:00Z"
            const val PAGE = 2
        }
    }

    private val graph = FakeGraph()
    private val provider get() = graph.provider

    @After
    fun tearDown() = graph.shutdown()

    @Test
    fun listsComeInPagesAndTheMainListIsToDosOwn() = runBlocking {
        graph.list("work", "Work")
        graph.list("tasks", "Tasks", wellknown = "defaultList")
        graph.list("home", "Home")

        assertEquals(listOf("work" to "Work", "tasks" to "Tasks", "home" to "Home"), provider.lists().map { it.listId to it.title })
        assertEquals(2, graph.requests.size)  // two pages

        assertEquals("tasks", provider.mainList()?.listId)
    }

    @Test
    fun changesFromScratchBringEveryTaskAndTheCursorGivesOnlyWhatChangedSince() = runBlocking {
        graph.list("tasks", "Tasks")
        graph.task("tasks", "a", "Buy milk") { add("dueDateTime", graph.due("2026-11-12")) }
        graph.task("tasks", "b", "Pay rent") { addProperty("status", "completed") }

        val first = provider.changes("tasks", null)
        assertEquals(listOf("a", "b"), first.tasks.map { it.remoteId })
        val a = first.tasks[0]
        assertEquals("Buy milk", a.title)
        assertEquals("2026-11-12", a.dueDay)
        assertFalse(a.completed)
        assertEquals(graph.versionOf("tasks", "a"), a.version)
        assertEquals(Instant.parse(FakeGraph.T0).plusSeconds(1).toEpochMilli(), a.modifiedAt)
        assertNull("To Do has no subtasks", a.parentRemoteId)
        assertTrue(first.tasks[1].completed)
        assertNull(first.tasks[1].dueDay)
        val cursor = first.nextCursor!!
        assertTrue(cursor.contains("\$deltatoken="))

        graph.task("tasks", "a", "Buy oat milk")
        graph.task("tasks", "c", "Call mom")
        graph.delete("tasks", "b")

        val next = provider.changes("tasks", cursor)
        assertEquals(listOf("a" to false, "c" to false, "b" to true), next.tasks.map { it.remoteId to it.deleted })
        assertEquals("Buy oat milk", next.tasks[0].title)
        assertNotEquals(cursor, next.nextCursor)

        assertEquals("asking again from the older cursor is harmless", 3, provider.changes("tasks", cursor).tasks.size)
        assertEquals("nothing new", 0, provider.changes("tasks", next.nextCursor).tasks.size)
    }

    @Test
    fun aCursorGraphHasForgottenStartsOver() = runBlocking {
        graph.list("tasks", "Tasks")
        graph.task("tasks", "a", "Buy milk")
        val cursor = provider.changes("tasks", null).nextCursor!!
        graph.task("tasks", "c", "Call mom")
        graph.forgottenTokens = setOf(cursor.substringAfter("\$deltatoken="))

        val again = provider.changes("tasks", cursor)

        assertEquals(listOf("a", "c"), again.tasks.map { it.remoteId })
        assertNotEquals(cursor, again.nextCursor)
    }

    @Test
    fun aNewTaskIsPostedWithItsWordsDoneMarkAndDueDayAtMidnightUtc() = runBlocking {
        graph.list("tasks", "Tasks")

        val sent = provider.upsert(OutgoingTask("row1", "tasks", "Buy milk", completed = false, dueDay = "2026-11-12", parentRemoteId = "p"), null)

        val body = graph.bodies.single()
        assertEquals("Buy milk", body.get("title").asString)
        assertEquals("notStarted", body.get("status").asString)
        assertEquals("2026-11-12T00:00:00.0000000", body.getAsJsonObject("dueDateTime").get("dateTime").asString)
        assertEquals("UTC", body.getAsJsonObject("dueDateTime").get("timeZone").asString)
        assertFalse("a subtask is a task of its own in To Do", body.has("parentId"))
        assertEquals("tasks", sent.listId)
        assertEquals("t1", sent.remoteId)
        assertEquals(graph.versionOf("tasks", "t1"), sent.version)
        assertEquals("Buy milk", sent.title)

        provider.upsert(OutgoingTask("row2", "tasks", "Someday", completed = true, dueDay = null, parentRemoteId = null), null)
        assertFalse("undated stays undated", graph.bodies.last().has("dueDateTime"))
        assertEquals("completed", graph.bodies.last().get("status").asString)
    }

    @Test
    fun anUpdatePatchesOnlyWhatChangedOnTheTablet() = runBlocking {
        graph.list("tasks", "Tasks")
        graph.task("tasks", "a", "Buy milk") { add("dueDateTime", graph.due("2026-11-12")) }
        val link = linkTo("a", title = "Buy milk", dueDay = "2026-11-12")

        val sent = provider.upsert(OutgoingTask("row1", "tasks", "Buy milk", completed = true, dueDay = null, parentRemoteId = null), link)

        val patch = graph.bodies.single()
        assertFalse("the words didn't change", patch.has("title"))
        assertEquals("completed", patch.get("status").asString)
        assertTrue("the due day is cleared with null", patch.has("dueDateTime") && patch.get("dueDateTime").isJsonNull)
        assertEquals(graph.versionOf("tasks", "a"), sent.version)
        assertEquals(listOf("GET /v1.0/me/todo/lists/tasks/tasks/a", "PATCH /v1.0/me/todo/lists/tasks/tasks/a"), graph.requests)

        graph.requests.clear()
        provider.upsert(
            OutgoingTask("row1", "tasks", "Buy milk", completed = true, dueDay = null, parentRemoteId = null),
            link.copy(remoteVersion = sent.version, syncedCompleted = true, remoteUndated = true)
        )
        assertEquals("nothing changed: nothing is written", listOf("GET /v1.0/me/todo/lists/tasks/tasks/a"), graph.requests)
    }

    @Test
    fun anUpdateStopsWhenTheCopyChangedInToDoSinceTheLastSync() = runBlocking {
        graph.list("tasks", "Tasks")
        graph.task("tasks", "a", "Buy milk")
        val link = linkTo("a", title = "Buy milk", dueDay = "2026-11-12")
        graph.task("tasks", "a", "Buy milk and bread")  // changed on the phone meanwhile

        try {
            provider.upsert(OutgoingTask("row1", "tasks", "Buy milk", completed = true, dueDay = "2026-11-12", parentRemoteId = null), link)
            fail("expected RemoteChanged")
        } catch (e: TaskSyncServiceException.RemoteChanged) {
            assertEquals("Buy milk and bread", e.remote.title)
            assertEquals(graph.versionOf("tasks", "a"), e.remote.version)
        }
        assertTrue("nothing was written", graph.requests.none { it.startsWith("PATCH") })
    }

    @Test
    fun whatIsGoneIsToldApartFromAGoneList() = runBlocking {
        graph.list("tasks", "Tasks")
        graph.task("tasks", "a", "Buy milk")

        assertNull(provider.fetch("tasks", "zzz"))
        assertEquals("Buy milk", provider.fetch("tasks", "a")?.title)
        try {
            provider.fetch("gone", "a")
            fail("expected ListGone")
        } catch (e: TaskSyncServiceException.ListGone) {
            assertEquals("gone", e.listId)
        }
        try {
            provider.changes("gone", null)
            fail("expected ListGone")
        } catch (e: TaskSyncServiceException.ListGone) {
            assertEquals("gone", e.listId)
        }
        try {
            provider.upsert(OutgoingTask("row1", "tasks", "Buy milk", false, null, null), linkTo("zzz", "Buy milk", null))
            fail("expected NotFound")
        } catch (e: TaskSyncServiceException.NotFound) {
            // the copy was deleted in To Do: the merge decides
        }

        provider.delete("tasks", "a")
        provider.delete("tasks", "a")  // already gone: done
        assertTrue(graph.tasks.getValue("tasks").isEmpty())
    }

    @Test
    fun aMoveToAnotherListPostsACopyThereAndDeletesTheOld() = runBlocking {
        graph.list("tasks", "Tasks")
        graph.list("work", "Work")
        graph.task("tasks", "a", "Buy milk") {
            add("dueDateTime", graph.due("2026-11-12"))
            addProperty("importance", "high")
        }

        val moved = provider.moveToList("tasks", "a", "work")

        assertEquals("work", moved.listId)
        assertEquals("t1", moved.remoteId)
        assertEquals("Buy milk", moved.title)
        assertEquals("2026-11-12", moved.dueDay)
        assertEquals("high", graph.tasks.getValue("work").getValue("t1").get("importance").asString)
        assertTrue("the old copy is gone", graph.tasks.getValue("tasks").isEmpty())
    }

    @Test
    fun allIdsAsksOnlyForIdsAcrossPages() = runBlocking {
        graph.list("tasks", "Tasks")
        for (id in listOf("a", "b", "c")) graph.task("tasks", id, "Task $id")

        assertEquals(setOf("a", "b", "c"), provider.allIds("tasks"))
        assertEquals(2, graph.requests.size)
        assertTrue(graph.requests.all { it.contains("\$select=id") })
    }

    @Test
    fun aCopyGoneFromItsListIsLookedForInTheOthers() = runBlocking {
        graph.list("tasks", "Tasks")
        graph.list("work", "Work")
        graph.list("home", "Home")
        graph.task("work", "w1", "Buy milk") { add("dueDateTime", graph.due("2026-11-12")) }
        val since = Instant.parse(FakeGraph.T0).toEpochMilli()

        val found = provider.findInOtherLists(skip = setOf("tasks"), remoteId = "a", title = "Buy milk", dueDay = "2026-11-12", since = since)

        assertEquals("work", found?.listId)
        assertEquals("w1", found?.remoteId)
        assertTrue("the synced list isn't searched", graph.requests.none { it.contains("/lists/tasks/") })
        assertNull(provider.findInOtherLists(skip = setOf("tasks"), remoteId = "a", title = "Buy milk", dueDay = "2026-11-13", since = since))
    }

    @Test
    fun failuresReachTheSenderAsItKnowsThem() = runBlocking {
        graph.list("tasks", "Tasks")

        graph.answer = { MockResponse().setResponseCode(401) }
        assertFailsWith<TaskSyncServiceException.SignInNeeded> { provider.lists() }

        graph.answer = { MockResponse().setResponseCode(429).setHeader("Retry-After", "7") }
        assertEquals(7_000L, assertFailsWith<TaskSyncServiceException.Busy> { provider.lists() }.retryAfterMs)

        graph.answer = { MockResponse().setResponseCode(503) }
        assertNull(assertFailsWith<TaskSyncServiceException.Busy> { provider.lists() }.retryAfterMs)

        graph.answer = { MockResponse().setResponseCode(500) }
        try {
            provider.lists()
            fail("expected a plain failure")
        } catch (e: TaskSyncServiceException) {
            fail("a 500 is retried with growing waits, not ${e.javaClass.simpleName}")
        } catch (e: Exception) {
            // as it is
        }

        graph.answer = { null }
        graph.shutdown()
        assertFailsWith<TaskSyncServiceException.Offline> { provider.lists() }
        Unit
    }

    private inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit): T {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return e
            throw e
        }
        fail("expected ${T::class.simpleName}")
        throw IllegalStateException()
    }

    private fun linkTo(remoteId: String, title: String, dueDay: String?) = TaskSyncLink(
        taskId = "row1", provider = TaskSyncProvider.MICROSOFT_TODO, accountEmail = "me@outlook.com", listId = "tasks", remoteId = remoteId,
        remoteVersion = graph.tasks.getValue("tasks")[remoteId]?.get("lastModifiedDateTime")?.asString,
        syncedTitle = title, syncedCompleted = false, syncedDueDay = dueDay ?: "2026-11-12", remoteUndated = dueDay == null, syncedAt = 0L
    )
}
