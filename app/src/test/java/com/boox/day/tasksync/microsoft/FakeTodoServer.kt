package com.boox.day.tasksync.microsoft

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Microsoft To Do as Graph showed it on 25 Nov 2026 (owner's test account), in memory, for
 * MockWebServer: dates kept and returned in UTC, steps listed with their task in delta and a step
 * change changing its task, a ticked repeating task keeping its id for the next one and a new done
 * copy. Only what MicrosoftTodoProvider uses.
 */
internal class FakeTodoServer : Dispatcher() {
    private val lists = LinkedHashMap<String, JsonObject>()
    private val tasks = LinkedHashMap<String, LinkedHashMap<String, JsonObject>>()
    private val steps = HashMap<String, LinkedHashMap<String, JsonObject>>()
    private val changes = HashMap<String, MutableList<Pair<Int, String>>>()  // list -> (rev, task id)
    private var rev = 0
    private var ids = 0
    var now: Instant = Instant.parse("2026-11-25T09:00:00Z")
        private set

    /** Delta items per page, to see the pages followed. */
    var deltaPageSize = 50
    /** Delta tokens Microsoft no longer knows (410). */
    val expired = HashSet<String>()
    /** Every request: "METHOD /path?query". */
    val requests = mutableListOf<String>()
    /** Every time-zone name BooxDay sent in a date. */
    val sentZones = mutableListOf<String>()
    /** New done copies left out of delta (a round that lags); a title search still finds them. */
    var hideDoneCopiesFromDelta = false
    private val hiddenFromDelta = HashSet<String>()
    /** A request ("DELETE", decoded path) Microsoft carries out but whose answer is lost on the way. */
    var loseReplyTo: (String, String) -> Boolean = { _, _ -> false }
    /** A request Microsoft refuses (500) without doing it. */
    var refuse: (String, String) -> Boolean = { _, _ -> false }
    /** Requests that change something. */
    val writes: List<String> get() = requests.filter { !it.startsWith("GET") }

    // ── The phone's side ──

    fun addList(id: String, name: String, wellKnown: String = "none") {
        lists[id] = buildJsonObject { put("id", id); put("displayName", name); put("wellknownListName", wellKnown) }
        tasks[id] = LinkedHashMap()
    }

    /** A task made on the phone. */
    fun addTask(listId: String, fields: Map<String, JsonElement>): String = create(listId, JsonObject(fields)).str("id")!!

    /** A change made on the phone (ticking a repeating task makes its done copy). */
    fun patchTask(listId: String, taskId: String, fields: Map<String, JsonElement>) {
        patch(listId, taskId, JsonObject(fields))
    }

    fun deleteTask(listId: String, taskId: String) {
        tasks.getValue(listId).remove(taskId)
        steps.remove(taskId)
        log(listId, taskId)
    }

    fun addStep(listId: String, taskId: String, words: String, ticked: Boolean = false): String =
        createStep(listId, taskId, buildJsonObject { put("displayName", words); put("isChecked", ticked) }).str("id")!!

    fun tickStep(listId: String, taskId: String, stepId: String, ticked: Boolean) {
        val step = steps.getValue(taskId).getValue(stepId)
        steps.getValue(taskId)[stepId] = JsonObject(step + ("isChecked" to JsonPrimitive(ticked)) + ("checkedDateTime" to JsonPrimitive(stamp())))
        touch(listId, taskId)
    }

    fun deleteStep(listId: String, taskId: String, stepId: String) {
        steps.getValue(taskId).remove(stepId)
        touch(listId, taskId)
    }

    fun task(listId: String, taskId: String): JsonObject? = tasks[listId]?.get(taskId)
    fun tasksIn(listId: String): List<JsonObject> = tasks.getValue(listId).values.toList()
    fun stepsOf(taskId: String): List<JsonObject> = steps[taskId]?.values?.toList().orEmpty()

    // ── Graph ──

    override fun dispatch(request: RecordedRequest): MockResponse {
        val url = request.requestUrl!!
        val query = url.encodedQuery?.let { "?$it" }.orEmpty()
        requests += "${request.method} ${url.encodedPath}$query"
        val path = url.pathSegments.drop(1)  // after v1.0
        val body = request.body.readUtf8().takeIf { it.isNotBlank() }?.let { Json.parseToJsonElement(it).jsonObject }
        body?.let { sent -> DATES.forEach { key -> (sent[key] as? JsonObject)?.str("timeZone")?.let { sentZones += it } } }
        // Delta takes no $top (measured 25 Nov 2026)
        if (path.lastOrNull() == "delta" && url.queryParameter("\$top") != null) return error(400, "BadRequest")
        val decoded = "/" + path.joinToString("/")
        if (refuse(request.method!!, decoded)) return error(500, "ErrorInternalServerError")
        val answer = try {
            route(request.method!!, path, url.queryParameter("\$deltatoken"), url.queryParameter("\$skiptoken"), url.queryParameter("\$filter"), body)
        } catch (e: NoSuchElementException) {
            error(404, "ErrorItemNotFound")
        }
        return if (loseReplyTo(request.method!!, decoded)) MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) else answer
    }

    private fun route(method: String, path: List<String>, deltaToken: String?, skipToken: String?, filter: String?, body: JsonObject?): MockResponse {
        // me / todo / lists / {list} / tasks / {task} / checklistItems / {step}
        if (path.take(3) != listOf("me", "todo", "lists")) return error(400, "BadRequest")
        val listId = path.getOrNull(3)
        if (listId == null) return ok(page(lists.values.toList()))
        if (listId !in lists) return error(404, "ErrorItemNotFound")
        if (path.size == 4) return ok(lists.getValue(listId))
        val taskId = path.getOrNull(5)
        when {
            taskId == null && method == "GET" -> {
                var all = tasks.getValue(listId).values.map { withSteps(it) }
                filter?.let { f -> val title = f.substringAfter("'").substringBeforeLast("'").replace("''", "'"); all = all.filter { it.str("title") == title } }
                return ok(page(all))
            }
            taskId == null && method == "POST" -> return created(create(listId, body!!))
            taskId == "delta" -> return delta(listId, deltaToken, skipToken)
        }
        val task = tasks.getValue(listId)[taskId!!] ?: return error(404, "ErrorItemNotFound")
        when (path.getOrNull(6)) {
            null -> return when (method) {
                "GET" -> ok(withSteps(task))
                "PATCH" -> ok(patch(listId, taskId, body!!))
                "DELETE" -> { deleteTask(listId, taskId); MockResponse().setResponseCode(204) }
                else -> error(405, "MethodNotAllowed")
            }
            "linkedResources" -> return ok(page(emptyList()))
            "checklistItems" -> Unit
            else -> return error(400, "BadRequest")
        }
        val stepId = path.getOrNull(7)
        val taskSteps = steps.getOrPut(taskId) { LinkedHashMap() }
        if (stepId == null) {
            return if (method == "POST") created(createStep(listId, taskId, body!!)) else ok(page(taskSteps.values.toList()))
        }
        val step = taskSteps[stepId] ?: return error(404, "ErrorItemNotFound")
        return when (method) {
            "GET" -> ok(step)
            "PATCH" -> {
                taskSteps[stepId] = JsonObject(step + body!!)
                touch(listId, taskId)
                ok(taskSteps.getValue(stepId))
            }
            "DELETE" -> {
                taskSteps.remove(stepId)
                touch(listId, taskId)
                MockResponse().setResponseCode(204)
            }
            else -> error(405, "MethodNotAllowed")
        }
    }

    private fun delta(listId: String, token: String?, skip: String?): MockResponse {
        if (token != null && token in expired) return error(410, "syncStateNotFound")
        val from = skip?.substringBefore('_')?.toInt() ?: token?.toInt() ?: 0
        val offset = skip?.substringAfter('_')?.toInt() ?: 0
        // Each changed task once, as it is now (or removed)
        val changed = changes[listId].orEmpty().filter { it.first > from }.map { it.second }.distinct().filter { it !in hiddenFromDelta }
        val items = changed.map { id ->
            tasks.getValue(listId)[id]?.let(::withSteps)
                ?: buildJsonObject { put("id", id); put("@removed", buildJsonObject { put("reason", "deleted") }) }
        }
        val pageItems = items.drop(offset).take(deltaPageSize)
        val base = "https://graph.microsoft.com/v1.0/me/todo/lists/$listId/tasks/delta"
        val answer = buildJsonObject {
            put("value", JsonArray(pageItems))
            if (offset + deltaPageSize < items.size) put("@odata.nextLink", "$base?\$skiptoken=${from}_${offset + deltaPageSize}")
            else put("@odata.deltaLink", "$base?\$deltatoken=$rev")
        }
        return ok(answer)
    }

    // ── What Microsoft does ──

    private fun create(listId: String, fields: JsonObject): JsonObject {
        val id = "AAMk-${++ids}="
        val at = stamp()
        val task = JsonObject(
            mapOf(
                "@odata.etag" to JsonPrimitive("W/\"$id-$rev\""),
                "importance" to JsonPrimitive("normal"),
                "isReminderOn" to JsonPrimitive(false),
                "status" to JsonPrimitive("notStarted"),
                "title" to JsonPrimitive(""),
                "createdDateTime" to JsonPrimitive(at),
                "lastModifiedDateTime" to JsonPrimitive(at),
                "hasAttachments" to JsonPrimitive(false),
                "categories" to JsonArray(emptyList()),
                "body" to buildJsonObject { put("content", ""); put("contentType", "text") },
                "id" to JsonPrimitive(id)
            ) + utcDates(fields)
        )
        tasks.getValue(listId)[id] = task
        log(listId, id)
        return task
    }

    /**
     * A ticked repeating task keeps its id for the next one; a new done copy holds this one. Both
     * get the due day as midnight UTC of its date, and the same step ids: the done copy's ticked as
     * they were, the next one's all unticked (measured 25 Nov 2026).
     */
    private fun patch(listId: String, taskId: String, fields: JsonObject): JsonObject {
        val before = tasks.getValue(listId).getValue(taskId)
        val ticked = fields["status"]?.jsonPrimitive?.contentOrNull == "completed" && before["recurrence"] is JsonObject
        if (ticked) {
            val doneId = "AAMk-${++ids}="
            val at = stamp()
            val due = midnightUtc(before["dueDateTime"])
            val done = JsonObject(
                before + mapOf(
                    "id" to JsonPrimitive(doneId),
                    "status" to JsonPrimitive("completed"),
                    "dueDateTime" to due,
                    "createdDateTime" to JsonPrimitive(at),
                    "lastModifiedDateTime" to JsonPrimitive(at),
                    "completedDateTime" to buildJsonObject { put("dateTime", wire(LocalDateTime.ofInstant(now, ZoneOffset.UTC))); put("timeZone", "UTC") },
                    "@odata.etag" to JsonPrimitive("W/\"$doneId-$rev\"")
                )
            )
            val stepsBefore = steps[taskId].orEmpty()
            steps[doneId] = LinkedHashMap(stepsBefore)
            steps[taskId] = LinkedHashMap(stepsBefore.mapValues { (_, step) -> JsonObject(step + ("isChecked" to JsonPrimitive(false))) })
            tasks.getValue(listId)[doneId] = done
            if (hideDoneCopiesFromDelta) hiddenFromDelta += doneId
            log(listId, doneId)
            val next = JsonObject(before + ("dueDateTime" to dayLater(due)) + ("reminderDateTime" to dayLater(before["reminderDateTime"])))
            tasks.getValue(listId)[taskId] = stamped(next)
            log(listId, taskId)
            return tasks.getValue(listId).getValue(taskId)
        }
        tasks.getValue(listId)[taskId] = stamped(JsonObject(before + utcDates(fields)))
        log(listId, taskId)
        return tasks.getValue(listId).getValue(taskId)
    }

    private fun createStep(listId: String, taskId: String, fields: JsonObject): JsonObject {
        val id = "step-${++ids}"
        val step = buildJsonObject {
            put("displayName", fields["displayName"]?.jsonPrimitive?.contentOrNull.orEmpty())
            put("isChecked", fields["isChecked"]?.jsonPrimitive?.contentOrNull == "true")
            put("createdDateTime", stamp())
            put("id", id)
        }
        steps.getOrPut(taskId) { LinkedHashMap() }[id] = step
        touch(listId, taskId)
        return step
    }

    /** A step change changes its task (measured 25 Nov 2026). */
    private fun touch(listId: String, taskId: String) {
        tasks.getValue(listId)[taskId]?.let { tasks.getValue(listId)[taskId] = stamped(it) }
        log(listId, taskId)
    }

    private fun stamped(task: JsonObject): JsonObject {
        val at = stamp()
        return JsonObject(task + ("lastModifiedDateTime" to JsonPrimitive(at)) + ("@odata.etag" to JsonPrimitive("W/\"${task.str("id")}-$rev-$at\"")))
    }

    private fun withSteps(task: JsonObject) = JsonObject(task + ("checklistItems" to JsonArray(stepsOf(task.str("id")!!))))

    private fun log(listId: String, taskId: String): Int {
        rev++
        changes.getOrPut(listId) { mutableListOf() } += rev to taskId
        return rev
    }

    /** A second on, as Microsoft's clock. */
    private fun stamp(): String {
        now = now.plusSeconds(1)
        return now.toString().replace("Z", ".0000000Z")
    }

    /** Dates as Graph keeps them: in UTC (measured 25 Nov 2026). */
    private fun utcDates(fields: JsonObject): Map<String, JsonElement> = fields.mapValues { (key, value) ->
        if (key in DATES && value is JsonObject) toUtc(value) else value
    }

    private fun toUtc(value: JsonObject): JsonObject {
        val zoneName = value.str("timeZone").orEmpty()
        val zone = runCatching { ZoneId.of(zoneName) }.getOrNull()
            ?: android.icu.util.TimeZone.getIDForWindowsID(zoneName, null)?.let { ZoneId.of(it) }
            ?: ZoneOffset.UTC
        val local = LocalDateTime.parse(value.str("dateTime")!!.take(19))
        return buildJsonObject {
            put("dateTime", wire(local.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()))
            put("timeZone", "UTC")
        }
    }

    /** Midnight UTC of a stored UTC date's day: how To Do keeps a due day. */
    private fun midnightUtc(value: JsonElement?): JsonElement {
        val date = (value as? JsonObject)?.str("dateTime") ?: return value ?: JsonNull
        return buildJsonObject { put("dateTime", wire(LocalDateTime.parse(date.take(19)).toLocalDate().atStartOfDay())); put("timeZone", "UTC") }
    }

    private fun dayLater(value: JsonElement?): JsonElement {
        val date = (value as? JsonObject)?.str("dateTime") ?: return value ?: JsonNull
        return buildJsonObject { put("dateTime", wire(LocalDateTime.parse(date.take(19)).plusDays(1))); put("timeZone", "UTC") }
    }

    private fun page(items: List<JsonObject>) = buildJsonObject { put("value", JsonArray(items)) }

    private fun ok(json: JsonObject) = MockResponse().setResponseCode(200).setBody(json.toString())
    private fun created(json: JsonObject) = MockResponse().setResponseCode(201).setBody(json.toString())
    private fun error(status: Int, code: String) =
        MockResponse().setResponseCode(status).setBody("""{"error":{"code":"$code","message":"test"}}""")

    companion object {
        private val DATES = setOf("dueDateTime", "reminderDateTime", "startDateTime", "completedDateTime")
        private val WIRE = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'.0000000'")
        fun wire(at: LocalDateTime): String = at.format(WIRE)

        /** A Graph date as JSON. */
        fun date(dateTime: String, timeZone: String) = buildJsonObject { put("dateTime", dateTime); put("timeZone", timeZone) }

        /** A daily repeat from [start]. */
        fun daily(start: String) = buildJsonObject {
            put("pattern", buildJsonObject { put("type", "daily"); put("interval", 1) })
            put("range", buildJsonObject { put("type", "noEnd"); put("startDate", start) })
        }
    }
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
