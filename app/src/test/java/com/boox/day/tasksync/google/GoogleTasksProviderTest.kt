package com.boox.day.tasksync.google

import com.boox.day.data.db.TaskSyncLink
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.tasksync.OutgoingTask
import com.boox.day.tasksync.RemoteTask
import com.boox.day.tasksync.TaskSyncServiceException
import com.google.android.gms.auth.GoogleAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAuthIOException
import com.google.api.client.http.HttpResponseException
import com.google.api.client.http.LowLevelHttpRequest
import com.google.api.client.http.LowLevelHttpResponse
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpRequest
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.model.Task
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.UnknownHostException
import java.time.Instant

/**
 * The Google Tasks connection (task sync step 2.2) against a small stand-in for Google Tasks built
 * on Google's own test transport: what is asked, what is sent, and how Google's answers reach the
 * sender.
 */
class GoogleTasksProviderTest {

    /** Google Tasks for one account: lists of tasks, two per page, every request recorded. */
    private class FakeGoogle {
        val lists = linkedMapOf<String, String>()
        val tasks = linkedMapOf<String, LinkedHashMap<String, Task>>()
        val requests = mutableListOf<String>()
        val bodies = mutableListOf<JsonObject>()
        /** A canned answer for a request ("METHOD path"), or null to answer normally. */
        var answer: (String) -> LowLevelHttpResponse? = { null }
        /** A failure before any answer (no connection, a refused token). */
        var failure: (String) -> IOException? = { null }
        private var nextId = 0
        private var time = Instant.parse("2026-10-30T10:00:00Z")

        val provider = GoogleTasksProvider(
            Tasks.Builder(
                object : MockHttpTransport() {
                    override fun buildRequest(method: String, url: String): LowLevelHttpRequest =
                        object : MockLowLevelHttpRequest(url) {
                            override fun execute(): LowLevelHttpResponse = handle(method, url, this)
                        }
                },
                GsonFactory.getDefaultInstance(),
                null
            ).setApplicationName("BooxDay test").build(),
            clock = { NOW_MS }
        )

        fun list(id: String, title: String = id) {
            lists[id] = title
            tasks.getOrPut(id) { linkedMapOf() }
        }

        fun task(listId: String, id: String, title: String, configure: Task.() -> Unit = {}): Task {
            val task = Task().setId(id).setTitle(title).setStatus("needsAction").setUpdated(tick())
            task.configure()
            tasks.getValue(listId)[id] = task
            return task
        }

        fun tick(): String {
            time = time.plusSeconds(60)
            return time.toString()
        }

        private fun handle(method: String, url: String, request: MockLowLevelHttpRequest): LowLevelHttpResponse {
            val uri = URI(url)
            val path = uri.path.substringAfter("/tasks/v1/")
            val query = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate {
                URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
            }
            val key = "$method $path"
            requests += if (query.isEmpty()) key else "$key?" + query.entries.joinToString("&") { "${it.key}=${it.value}" }
            failure(key)?.let { throw it }
            answer(key)?.let { return it }
            val body = request.contentAsString.takeIf { it.isNotEmpty() }?.let { JsonParser.parseString(it).asJsonObject }
            body?.let { bodies += it }

            val parts = path.split('/')
            return when {
                path == "users/@me/lists" -> page(lists.map { (id, title) -> """{"id":"$id","title":"$title"}""" }, query)
                path.startsWith("users/@me/lists/") ->
                    if (parts[3] in lists) json(200, """{"id":"${parts[3]}","title":"${lists[parts[3]]}"}""") else notFound()
                parts[0] == "lists" && parts[1] !in lists -> notFound()
                parts.size == 3 && method == "GET" -> listTasks(parts[1], query)
                parts.size == 3 && method == "POST" -> {
                    val created = GSON.fromString(body.toString(), Task::class.java)
                        .setId("new-${++nextId}").setParent(query["parent"]).setUpdated(tick())
                    tasks.getValue(parts[1])[created.id] = created
                    json(200, GSON.toString(created))
                }
                else -> {
                    val task = tasks.getValue(parts[1])[parts[3]] ?: return notFound()
                    when {
                        parts.size == 5 && parts[4] == "move" -> task.setParent(query["parent"]).setUpdated(tick())
                        method == "PATCH" -> {
                            for ((field, value) in body!!.entrySet()) {
                                val text = if (value.isJsonNull) null else value.asString
                                when (field) {
                                    "title" -> task.title = text
                                    "status" -> task.status = text
                                    "due" -> task.due = text
                                    "completed" -> task.completed = text
                                }
                            }
                            task.setUpdated(tick())
                        }
                        method == "DELETE" -> {
                            task.setDeleted(true).setUpdated(tick())
                            return MockLowLevelHttpResponse().setStatusCode(204)
                        }
                    }
                    json(200, GSON.toString(task))
                }
            }
        }

        private fun listTasks(listId: String, query: Map<String, String>): LowLevelHttpResponse {
            val since = query["updatedMin"]?.let { Instant.parse(it) }
            val items = tasks.getValue(listId).values
                .filter { query["showDeleted"] == "true" || it.deleted != true }
                .filter { since == null || !Instant.parse(it.updated).isBefore(since) }
                .map { GSON.toString(it) }
            return page(items, query)
        }

        private fun page(items: List<String>, query: Map<String, String>): LowLevelHttpResponse {
            val start = query["pageToken"]?.toInt() ?: 0
            val next = if (start + PAGE < items.size) ""","nextPageToken":"${start + PAGE}"""" else ""
            return json(200, """{"items":[${items.drop(start).take(PAGE).joinToString(",")}]$next}""")
        }
    }

    private val google = FakeGoogle().apply { list(LIST) }
    private val provider = google.provider

    // ── Reading ──

    @Test
    fun `the lists come from every page`() = runBlocking<Unit> {
        google.list("l2", "Groceries")
        google.list("l3", "Work")

        val lists = provider.lists()

        assertEquals(listOf(LIST, "l2", "l3"), lists.map { it.listId })
        assertEquals("Groceries", lists[1].title)
        assertEquals(2, google.requests.count { it.startsWith("GET users/@me/lists") })
    }

    @Test
    fun `the first pull asks for everything, ticked, hidden and deleted ones included`() = runBlocking<Unit> {
        google.task(LIST, "a", "Call mom") { due = "2026-11-02T00:00:00.000Z" }
        google.task(LIST, "b", "Pay rent") { status = "completed"; hidden = true }
        google.task(LIST, "c", "Old") { deleted = true }
        val sub = google.task(LIST, "d", "Buy milk") { parent = "a" }

        val changes = provider.changes(LIST, cursor = null)

        val query = google.requests.first()
        assertTrue(query, query.contains("showCompleted=true") && query.contains("showHidden=true") && query.contains("showDeleted=true"))
        assertFalse(query, query.contains("updatedMin"))
        assertEquals(listOf("a", "b", "c", "d"), changes.tasks.map { it.remoteId })
        val (a, b, c, d) = changes.tasks
        assertEquals("2026-11-02", a.dueDay)
        assertFalse(a.completed)
        assertTrue(b.completed)
        assertNull("undated", b.dueDay)
        assertTrue(c.deleted)
        assertEquals("a", d.parentRemoteId)
        assertEquals(sub.updated, d.version)
        assertEquals("the newest change", sub.updated, changes.nextCursor)
    }

    @Test
    fun `the next pull asks from a little before the newest change`() = runBlocking<Unit> {
        val seen = google.task(LIST, "a", "Call mom")
        val cursor = provider.changes(LIST, null).nextCursor!!
        val changed = google.task(LIST, "b", "New on the phone")

        val changes = provider.changes(LIST, cursor)

        val asked = google.requests.last().substringAfter("updatedMin=").substringBefore('&')
        assertEquals(Instant.parse(seen.updated).minusSeconds(300), Instant.parse(asked))
        assertEquals("the newest seen comes again (harmless), then the new one", listOf("a", "b"), changes.tasks.map { it.remoteId })
        assertEquals(changed.updated, changes.nextCursor)
        google.tasks.getValue(LIST).clear()
        assertEquals("nothing new: the cursor stays", changed.updated, provider.changes(LIST, changed.updated).nextCursor)
    }

    @Test
    fun `a pull of a list deleted in Google says the list is gone`() = runBlocking<Unit> {
        try {
            provider.changes("deleted-list", null)
            fail("expected ListGone")
        } catch (e: TaskSyncServiceException.ListGone) {
            assertEquals("deleted-list", e.listId)
        }
    }

    @Test
    fun `the daily check lists every id that is not deleted`() = runBlocking<Unit> {
        google.task(LIST, "a", "One")
        google.task(LIST, "b", "Two") { status = "completed"; hidden = true }
        google.task(LIST, "c", "Three")
        google.task(LIST, "d", "Gone") { deleted = true }

        assertEquals(setOf("a", "b", "c"), provider.allIds(LIST))
        assertTrue(google.requests.all { it.contains("fields=items(id),nextPageToken") && it.contains("showHidden=true") })
        assertEquals("two pages", 2, google.requests.size)
    }

    @Test
    fun `one copy is read as it is now, or null once it is gone`() = runBlocking<Unit> {
        google.task(LIST, "a", "Call mom") { due = "2026-11-02T00:00:00.000Z" }
        google.task(LIST, "b", "Gone") { deleted = true }

        assertEquals("Call mom", provider.fetch(LIST, "a")?.title)
        assertNull(provider.fetch(LIST, "b"))
        assertNull(provider.fetch(LIST, "never"))
    }

    // ── Sending ──

    @Test
    fun `a new task is created with its words, done mark and due day, under its parent`() = runBlocking<Unit> {
        google.task(LIST, "p", "Groceries")

        val remote = provider.upsert(OutgoingTask("t-1", LIST, "Buy milk", completed = true, dueDay = "2026-11-02", parentRemoteId = "p"), null)

        assertEquals("POST lists/$LIST/tasks?parent=p", google.requests.single())
        val body = google.bodies.single()
        assertEquals("Buy milk", body["title"].asString)
        assertEquals("completed", body["status"].asString)
        assertEquals("midnight UTC: no time zone moves the day", "2026-11-02T00:00:00.000Z", body["due"].asString)
        assertFalse("notes are never sent", body.has("notes"))
        val created = google.tasks.getValue(LIST).getValue(remote.remoteId)
        assertEquals(RemoteTask(LIST, created.id, created.updated, "Buy milk"), remote)
    }

    @Test
    fun `an undated new task is sent without a due day`() = runBlocking<Unit> {
        provider.upsert(OutgoingTask("t-1", LIST, "Someday", completed = false, dueDay = null, parentRemoteId = null), null)

        assertEquals("POST lists/$LIST/tasks", google.requests.single())
        assertFalse(google.bodies.single().has("due"))
    }

    @Test
    fun `an update sends only what changed on the tablet and keeps the notes`() = runBlocking<Unit> {
        val copy = google.task(LIST, "r", "Call mom") { due = "2026-11-02T00:00:00.000Z"; notes = "written on the phone" }

        val remote = provider.upsert(outgoing(completed = true), linkTo(copy))

        assertEquals(listOf("GET lists/$LIST/tasks/r", "PATCH lists/$LIST/tasks/r"), google.requests)
        assertEquals("""{"status":"completed"}""", google.bodies.single().toString())
        assertEquals("written on the phone", copy.notes)
        assertEquals(copy.updated, remote.version)
    }

    @Test
    fun `un-ticking clears Google's completed time, and an undated task clears the due day`() = runBlocking<Unit> {
        val copy = google.task(LIST, "r", "Call mom") {
            status = "completed"; completed = "2026-10-30T09:00:00.000Z"; due = "2026-11-02T00:00:00.000Z"
        }

        provider.upsert(outgoing(completed = false, dueDay = null), linkTo(copy, completed = true))

        val body = google.bodies.single()
        assertEquals("needsAction", body["status"].asString)
        assertTrue(body["completed"].isJsonNull)
        assertTrue(body["due"].isJsonNull)
        assertNull(copy.completed)
        assertNull(copy.due)
    }

    @Test
    fun `a new day changes the due day, and nothing changed sends nothing`() = runBlocking<Unit> {
        val copy = google.task(LIST, "r", "Call mom") { due = "2026-11-02T00:00:00.000Z" }

        provider.upsert(outgoing(), linkTo(copy))
        assertEquals("read, nothing written", listOf("GET lists/$LIST/tasks/r"), google.requests)

        provider.upsert(outgoing(dueDay = "2026-11-04"), linkTo(copy))
        assertEquals("""{"due":"2026-11-04T00:00:00.000Z"}""", google.bodies.single().toString())
    }

    @Test
    fun `a new parent moves the copy under it, no parent moves it to the top level`() = runBlocking<Unit> {
        google.task(LIST, "p", "Groceries")
        val copy = google.task(LIST, "r", "Buy milk") { due = "2026-11-02T00:00:00.000Z" }

        provider.upsert(outgoing(title = "Buy milk", parentRemoteId = "p"), linkTo(copy, title = "Buy milk"))
        assertEquals("POST lists/$LIST/tasks/r/move?parent=p", google.requests.last())
        assertEquals("p", copy.parent)

        provider.upsert(outgoing(title = "Buy milk"), linkTo(copy, title = "Buy milk", parentRemoteId = "p"))
        assertEquals("POST lists/$LIST/tasks/r/move", google.requests.last())
        assertNull(copy.parent)
    }

    @Test
    fun `a copy changed in Google since the last sync is not overwritten`() = runBlocking<Unit> {
        val copy = google.task(LIST, "r", "Call mom") { due = "2026-11-02T00:00:00.000Z" }
        val link = linkTo(copy)
        copy.setTitle("Call mom at 6").setUpdated(google.tick())  // edited on the phone

        try {
            provider.upsert(outgoing(completed = true), link)
            fail("expected RemoteChanged")
        } catch (e: TaskSyncServiceException.RemoteChanged) {
            assertEquals("Call mom at 6", e.remote.title)
            assertEquals(copy.updated, e.remote.version)
        }
        assertEquals("nothing written", listOf("GET lists/$LIST/tasks/r"), google.requests)
    }

    @Test
    fun `a copy deleted in Google is gone, and so is a deleted list`() = runBlocking<Unit> {
        val copy = google.task(LIST, "r", "Call mom") { due = "2026-11-02T00:00:00.000Z" }
        val link = linkTo(copy)

        google.tasks.getValue(LIST).remove("r")
        assertFails<TaskSyncServiceException.NotFound> { provider.upsert(outgoing(completed = true), link) }

        google.task(LIST, "r", "Call mom") { deleted = true }
        assertFails<TaskSyncServiceException.NotFound> { provider.upsert(outgoing(completed = true), link) }

        google.lists.remove(LIST)
        assertFails<TaskSyncServiceException.ListGone> { provider.upsert(outgoing(completed = true), link) }
        assertFails<TaskSyncServiceException.ListGone> {
            provider.upsert(OutgoingTask("t-2", LIST, "New", false, "2026-11-02", null), null)
        }
    }

    @Test
    fun `deleting a copy already gone counts as done`() = runBlocking<Unit> {
        google.task(LIST, "r", "Call mom")

        provider.delete(LIST, "r")
        provider.delete(LIST, "never-there")
        google.answer = { if (it.startsWith("DELETE")) error(410, "deleted") else null }
        provider.delete(LIST, "r")

        assertTrue(google.tasks.getValue(LIST).getValue("r").deleted == true)
        assertEquals(3, google.requests.count { it.startsWith("DELETE") })
    }

    // ── Google's answers ──

    @Test
    fun `a refused token, 401 or a missing permission asks for sign-in again`() = runBlocking<Unit> {
        google.failure = { GoogleAuthIOException(GoogleAuthException("refused")) }
        assertFails<TaskSyncServiceException.SignInNeeded> { provider.lists() }

        google.failure = { null }
        google.answer = { error(401, "authError") }
        assertFails<TaskSyncServiceException.SignInNeeded> { provider.lists() }

        google.answer = { error(403, "insufficientPermissions") }
        assertFails<TaskSyncServiceException.SignInNeeded> { provider.lists() }
    }

    @Test
    fun `rate limits and an overloaded Google mean busy, with Google's wait when it gives one`() = runBlocking<Unit> {
        google.answer = { error(403, "rateLimitExceeded") }
        assertEquals(null, assertFails<TaskSyncServiceException.Busy> { provider.lists() }.retryAfterMs)

        google.answer = { error(429, "rateLimitExceeded").addHeader("Retry-After", "30") }
        assertEquals(30_000L, assertFails<TaskSyncServiceException.Busy> { provider.lists() }.retryAfterMs)

        google.answer = { error(503, "backendError").addHeader("Retry-After", "Fri, 30 Oct 2026 10:02:00 GMT") }
        assertEquals(120_000L, assertFails<TaskSyncServiceException.Busy> { provider.lists() }.retryAfterMs)
    }

    @Test
    fun `no connection, or a reply lost on the way, means offline`() = runBlocking<Unit> {
        google.failure = { UnknownHostException("tasks.googleapis.com") }
        assertFails<TaskSyncServiceException.Offline> { provider.lists() }

        // The task may exist in Google now: the next pull finds it and links it (match before create)
        google.failure = { if (it.startsWith("POST")) IOException("unexpected end of stream") else null }
        assertFails<TaskSyncServiceException.Offline> {
            provider.upsert(OutgoingTask("t-1", LIST, "Call mom", false, "2026-11-02", null), null)
        }
    }

    @Test
    fun `other errors reach the sender as they are`() = runBlocking<Unit> {
        google.answer = { error(400, "invalid") }
        assertEquals(400, assertFails<HttpResponseException> { provider.lists() }.statusCode)

        google.answer = { error(403, "forbidden") }
        assertEquals(403, assertFails<HttpResponseException> { provider.lists() }.statusCode)

        google.answer = { error(500, "backendError") }
        assertEquals(500, assertFails<HttpResponseException> { provider.lists() }.statusCode)
    }

    @Test
    fun `titles are cut at Google's limit, never inside a character`() {
        assertEquals("Call mom", GoogleTasksProvider.titleFor("Call mom"))
        assertEquals(1024, GoogleTasksProvider.titleFor("a".repeat(2000)).length)
        val emojiAtTheEdge = "a".repeat(1023) + "😀" + "b"
        assertEquals(1023, GoogleTasksProvider.titleFor(emojiAtTheEdge).length)
    }

    @Test
    fun `Google's due is read as its date`() {
        assertEquals("2026-11-02", GoogleTasksProvider.dayOf("2026-11-02T00:00:00.000Z"))
        assertNull(GoogleTasksProvider.dayOf(null))
        assertNull(GoogleTasksProvider.dayOf("soon"))
    }

    // ── Helpers ──

    private fun outgoing(
        title: String = "Call mom",
        completed: Boolean = false,
        dueDay: String? = "2026-11-02",
        parentRemoteId: String? = null
    ) = OutgoingTask("t-1", LIST, title, completed, dueDay, parentRemoteId)

    /** The link as the last sync left it: the copy as it is now. */
    private fun linkTo(
        copy: Task,
        title: String = "Call mom",
        completed: Boolean = false,
        parentRemoteId: String? = null
    ) = TaskSyncLink(
        taskId = "t-1", provider = TaskSyncProvider.GOOGLE_TASKS, accountEmail = "me@gmail.com", listId = LIST,
        remoteId = copy.id, remoteVersion = copy.updated, syncedTitle = title, syncedCompleted = completed,
        syncedDueDay = "2026-11-02", syncedParentRemoteId = parentRemoteId, remoteUndated = false, syncedAt = 1
    )

    private inline fun <reified E : Exception> assertFails(block: () -> Unit): E {
        try {
            block()
        } catch (e: Exception) {
            if (e is E) return e
            throw AssertionError("expected ${E::class.simpleName}, got $e", e)
        }
        throw AssertionError("expected ${E::class.simpleName}")
    }

    private companion object {
        const val LIST = "list-1"
        const val PAGE = 2
        val NOW_MS: Long = Instant.parse("2026-10-30T10:00:00Z").toEpochMilli()
        val GSON: GsonFactory = GsonFactory.getDefaultInstance()

        fun json(status: Int, body: String): MockLowLevelHttpResponse = MockLowLevelHttpResponse()
            .setStatusCode(status)
            .setContentType("application/json; charset=UTF-8")
            .setContent(body)

        fun notFound() = error(404, "notFound")

        fun error(status: Int, reason: String) =
            json(status, """{"error":{"code":$status,"message":"$reason","errors":[{"reason":"$reason","message":"$reason"}]}}""")
    }
}
