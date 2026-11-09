package com.boox.day.tasksync.microsoft

import android.app.Activity
import android.content.Context
import com.boox.day.data.db.TaskSyncLink
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.sync.outlook.AccountNotFoundException
import com.boox.day.sync.outlook.OutlookAuthManager
import com.boox.day.sync.outlook.SignInCancelledException
import com.boox.day.sync.provider.OutlookCalendarProvider
import com.boox.day.tasksync.OutgoingTask
import com.boox.day.tasksync.RemoteChanges
import com.boox.day.tasksync.RemoteList
import com.boox.day.tasksync.RemoteTask
import com.boox.day.tasksync.RemoteTaskState
import com.boox.day.tasksync.TaskSyncService
import com.boox.day.tasksync.TaskSyncServiceException
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.microsoft.identity.client.exception.MsalClientException
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant

/**
 * Microsoft To Do for task sync (Nov 2026), through the Graph API, for an Outlook account signed in
 * at Calendar Accounts (MSAL, sync/outlook). Its To Do permission is asked for on its own when Task
 * Sync is turned on for the account ([connect]), as Google's Tasks permission is.
 *
 * - **Reading:** the lists; what changed since a cursor with Graph's delta query (the cursor is the
 *   delta link; deleted tasks come as `@removed`; a cursor Graph has forgotten, 410, starts over);
 *   every id of a list for the daily check.
 * - **Sending:** a new task is posted; an update first reads the copy and stops with
 *   [TaskSyncServiceException.RemoteChanged] if it changed since the last sync, then patches only the
 *   fields that changed on the tablet. To Do has no subtasks and no move between lists: a subtask
 *   is a task of its own there (the tablet keeps its grouping, owner decision 24), and a move is a
 *   new copy in the other list and the old one deleted.
 * - **Errors:** a token Microsoft won't renew without the user, or a 401, means "sign in again";
 *   429 and 503 mean "busy" (Graph's Retry-After); no connection means offline.
 *
 * Priority and reminder stay on the tablet ([carriesExtras] false): To Do's three importances
 * can't hold the tablet's four priorities without changing them on the way back.
 */
internal class MicrosoftToDoProvider(
    private val client: OkHttpClient,
    /** Microsoft's access token for To Do, fresh enough for one call; throws what the sender knows. */
    private val token: suspend () -> String,
    private val baseUrl: String = GRAPH
) : TaskSyncService {

    override val provider: String = TaskSyncProvider.MICROSOFT_TODO

    private class GraphError(val code: Int, val retryAfter: String?) : Exception("Graph answered $code")

    @Volatile
    private var bearer = ""

    override suspend fun lists(): List<RemoteList> = call { allLists().map(::listOf) }

    /** To Do's own "Tasks" list, which every account has. */
    override suspend fun mainList(): RemoteList? = call {
        val all = allLists()
        (all.firstOrNull { it.string("wellknownListName") == DEFAULT_LIST } ?: all.firstOrNull())?.let(::listOf)
    }

    override suspend fun changes(listId: String, cursor: String?): RemoteChanges = call {
        val start = "${tasksUrl(listId)}/delta"
        var url = cursor ?: start
        val found = mutableListOf<RemoteTaskState>()
        var next: String? = null
        do {
            val page = try {
                onList(listId) { get(url) }
            } catch (e: GraphError) {
                if (e.code != 410 || url == start) throw e
                // The cursor is too old for Graph: everything again from the start
                url = start
                found.clear()
                continue
            }
            for (item in page.items()) found += stateOf(listId, item)
            next = page.string(DELTA_LINK)
            url = page.string(NEXT_LINK) ?: ""
        } while (url.isNotEmpty())
        RemoteChanges(found, next ?: cursor)
    }

    override suspend fun fetch(listId: String, remoteId: String): RemoteTaskState? = call {
        try {
            stateOf(listId, onTask(listId) { get(taskUrl(listId, remoteId)) })
        } catch (e: TaskSyncServiceException.NotFound) {
            null
        }
    }

    override suspend fun allIds(listId: String): Set<String> = call {
        pages("${tasksUrl(listId)}?\$select=id&\$top=$PAGE", listId).mapNotNullTo(HashSet()) { it.string("id") }
    }

    // One list call, then one per list not skipped: only when a synced copy is gone (owner decision 49)
    override suspend fun findInOtherLists(skip: Set<String>, remoteId: String, title: String?, dueDay: String?, since: Long): RemoteTaskState? = call {
        for (list in allLists()) {
            val id = list.string("id") ?: continue
            if (id in skip) continue
            val copies = try {
                pages("${tasksUrl(id)}?\$top=$PAGE", id)
            } catch (e: TaskSyncServiceException.ListGone) {
                continue  // deleted meanwhile: nothing there
            }
            copies.map { stateOf(id, it) }.firstOrNull { copy ->
                (copy.modifiedAt ?: Long.MAX_VALUE) >= since &&
                    (copy.remoteId == remoteId || (title != null && copy.title == title && copy.dueDay == dueDay))
            }?.let { return@call it }
        }
        null
    }

    override suspend fun moveToList(fromListId: String, remoteId: String, toListId: String, parentRemoteId: String?): RemoteTaskState = call {
        // Graph has no move: the same task posted to the other list, then the old copy deleted
        val old = onTask(fromListId) { get(taskUrl(fromListId, remoteId)) }
        val copy = JsonObject()
        for (field in COPIED_FIELDS) old.get(field)?.let { copy.add(field, it) }
        val created = onList(toListId) { send("POST", tasksUrl(toListId), copy) }
        try {
            send("DELETE", taskUrl(fromListId, remoteId))
        } catch (e: GraphError) {
            if (e.code != 404) throw e
        }
        stateOf(toListId, created)
    }

    override suspend fun upsert(task: OutgoingTask, link: TaskSyncLink?): RemoteTask = call {
        if (link == null) insert(task) else update(task, link)
    }

    override suspend fun delete(listId: String, remoteId: String) = call {
        try {
            send("DELETE", taskUrl(listId, remoteId))
        } catch (e: GraphError) {
            if (e.code != 404) throw e  // already gone (or its list is): done
        }
        Unit
    }

    private fun insert(task: OutgoingTask): RemoteTask {
        val body = JsonObject()
        body.addProperty("title", task.title)
        body.addProperty("status", statusFor(task.completed))
        task.dueDay?.let { body.add("dueDateTime", dueFor(it)) }
        return remoteTaskOf(task.listId, onList(task.listId) { send("POST", tasksUrl(task.listId), body) })
    }

    private fun update(task: OutgoingTask, link: TaskSyncLink): RemoteTask {
        val listId = link.listId
        val url = taskUrl(listId, link.remoteId)
        val before = onTask(listId) { get(url) }
        if (before.string("lastModifiedDateTime") != link.remoteVersion) throw TaskSyncServiceException.RemoteChanged(stateOf(listId, before))

        // Only what changed on the tablet since the last sync (the copy is as it was then)
        val patch = JsonObject()
        if (task.title != link.syncedTitle) patch.addProperty("title", task.title)
        if (task.completed != link.syncedCompleted) patch.addProperty("status", statusFor(task.completed))
        val lastDue = if (link.remoteUndated) null else link.syncedDueDay
        if (task.dueDay != lastDue) patch.add("dueDateTime", task.dueDay?.let(::dueFor) ?: JsonNull.INSTANCE)
        val after = if (patch.size() > 0) onTask(listId) { send("PATCH", url, patch) } else before
        return remoteTaskOf(listId, after)
    }

    // ── Graph's answers ──

    private fun allLists(): List<JsonObject> = pages("$baseUrl/me/todo/lists?\$top=$PAGE")

    /** Every item of a paged answer, following Graph's next links. */
    private fun pages(firstUrl: String, listId: String? = null): List<JsonObject> {
        val items = mutableListOf<JsonObject>()
        var url: String? = firstUrl
        while (url != null) {
            val page = if (listId != null) onList(listId) { get(url) } else get(url)
            items += page.items()
            url = page.string(NEXT_LINK)
        }
        return items
    }

    /** A 404 for the list itself. */
    private inline fun <T> onList(listId: String, block: () -> T): T = try {
        block()
    } catch (e: GraphError) {
        if (e.code == 404) throw TaskSyncServiceException.ListGone(listId, e)
        throw e
    }

    /** A 404 for one task: the task is gone, or its whole list. */
    private inline fun <T> onTask(listId: String, block: () -> T): T = try {
        block()
    } catch (e: GraphError) {
        if (e.code != 404) throw e
        if (listExists(listId)) throw TaskSyncServiceException.NotFound(e)
        throw TaskSyncServiceException.ListGone(listId, e)
    }

    private fun listExists(listId: String): Boolean = try {
        get("$baseUrl/me/todo/lists/$listId")
        true
    } catch (e: GraphError) {
        if (e.code != 404) throw e
        false
    }

    private fun get(url: String): JsonObject = send("GET", url)

    private fun send(method: String, url: String, body: JsonObject? = null): JsonObject {
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $bearer")
            .method(method, body?.toString()?.toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw GraphError(response.code, response.header("Retry-After"))
            val text = response.body?.string().orEmpty()
            return if (text.isBlank()) JsonObject() else JsonParser.parseString(text).asJsonObject
        }
    }

    /** Graph's blocking calls off the caller's thread, with a token, their failures as the sender knows them. */
    private suspend fun <T> call(block: () -> T): T = withContext(Dispatchers.IO) {
        bearer = token()
        try {
            block()
        } catch (e: IOException) {
            throw TaskSyncServiceException.Offline(e)  // no connection, a timeout, or the reply lost on the way
        } catch (e: GraphError) {
            throw when (e.code) {
                401 -> TaskSyncServiceException.SignInNeeded(e)
                429, 503, 504 -> TaskSyncServiceException.Busy(retryAfterMs(e.retryAfter), e)
                else -> e
            }
        }
    }

    private fun tasksUrl(listId: String) = "$baseUrl/me/todo/lists/$listId/tasks"
    private fun taskUrl(listId: String, remoteId: String) = "${tasksUrl(listId)}/$remoteId"

    companion object {
        const val SERVICE_NAME = "Microsoft To Do"
        /** Microsoft's permission for To Do, asked for only when Task Sync is turned on for an Outlook account. */
        val SCOPES = listOf("Tasks.ReadWrite")

        private const val GRAPH = "https://graph.microsoft.com/v1.0"
        private const val PAGE = 100
        private const val DEFAULT_LIST = "defaultList"
        private const val NEXT_LINK = "@odata.nextLink"
        private const val DELTA_LINK = "@odata.deltaLink"
        private const val STATUS_COMPLETED = "completed"
        private const val STATUS_NOT_STARTED = "notStarted"
        private const val MAX_RETRY_AFTER_MS = 24 * 60 * 60_000L
        private val JSON = "application/json".toMediaType()
        private val DAY = Regex("""\d{4}-\d{2}-\d{2}""")
        /** What a task keeps when it moves to another list. */
        private val COPIED_FIELDS = listOf("title", "status", "dueDateTime", "importance", "body", "isReminderOn", "reminderDateTime", "categories")

        /** For the Outlook account [accountId] of Calendar Accounts; [TaskSyncServiceException.SignInNeeded] when it isn't in MSAL any more. */
        suspend fun create(context: Context, accountId: String): MicrosoftToDoProvider {
            val app = context.applicationContext
            val auth = OutlookAuthManager(app)
            auth.initialize().getOrThrow()
            val msalId = OutlookCalendarProvider.msalAccountId(app, accountId) ?: throw TaskSyncServiceException.SignInNeeded()
            return MicrosoftToDoProvider(OkHttpClient(), token = {
                auth.getAccessToken(msalId, SCOPES).getOrElse { throw failureOf(it) }
            })
        }

        /**
         * Microsoft's To Do permission for the Outlook account [accountId]: silently when it was given,
         * else Microsoft's own consent screen. Null when given; else why not, in plain words.
         */
        suspend fun connect(activity: Activity, accountId: String): String? {
            val auth = OutlookAuthManager(activity.applicationContext)
            auth.initialize().getOrElse { return "Microsoft sign-in isn't available right now. Try again in a moment." }
            val gone = "This Microsoft account isn't signed in any more. Connect it again in Settings → Calendar Accounts."
            val msalId = OutlookCalendarProvider.msalAccountId(activity, accountId) ?: return gone
            if (auth.getAccessToken(msalId, SCOPES).isSuccess) return null
            return auth.acquireTokenInteractive(activity, msalId, SCOPES).fold({ null }) { e ->
                when (e) {
                    is SignInCancelledException -> "Microsoft To Do wasn't allowed, so Task Sync stays off."
                    is AccountNotFoundException -> gone
                    is MsalException -> "Microsoft said no (${e.errorCode})."
                    else -> "Microsoft didn't answer as expected. Try again in a moment."
                }
            }
        }

        /** MSAL's failures as the sender knows them. */
        private fun failureOf(e: Throwable): Throwable = when {
            e is MsalUiRequiredException || e is AccountNotFoundException -> TaskSyncServiceException.SignInNeeded(e)
            e is MsalClientException && (e.errorCode == MsalClientException.DEVICE_NETWORK_NOT_AVAILABLE || e.errorCode == MsalClientException.IO_ERROR) ->
                TaskSyncServiceException.Offline(e)
            else -> e
        }

        private fun statusFor(completed: Boolean) = if (completed) STATUS_COMPLETED else STATUS_NOT_STARTED

        /** To Do keeps a due date as midnight UTC, as its own apps write it. */
        internal fun dueFor(day: String): JsonObject = JsonObject().apply {
            addProperty("dateTime", "${day}T00:00:00.0000000")
            addProperty("timeZone", "UTC")
        }

        /** The date part of a Graph dateTimeTimeZone, or null when there is none (undated). */
        internal fun dayOf(due: JsonObject?): String? =
            due?.string("dateTime")?.take(10)?.takeIf { DAY.matches(it) }

        /** Graph's Retry-After (seconds), when it sends one; at most a day. */
        private fun retryAfterMs(value: String?): Long? =
            value?.trim()?.toLongOrNull()?.times(1000)?.coerceIn(0L, MAX_RETRY_AFTER_MS)

        private fun listOf(list: JsonObject) = RemoteList(list.string("id").orEmpty(), list.string("displayName").orEmpty())

        private fun stateOf(listId: String, task: JsonObject): RemoteTaskState {
            val id = task.string("id").orEmpty()
            if (task.has("@removed")) return RemoteTaskState(listId, id, "", completed = false, dueDay = null, parentRemoteId = null, deleted = true, version = null)
            val modified = task.string("lastModifiedDateTime")
            return RemoteTaskState(
                listId = listId,
                remoteId = id,
                title = task.string("title").orEmpty(),
                completed = task.string("status") == STATUS_COMPLETED,
                dueDay = dayOf(task.getAsJsonObject("dueDateTime")),
                parentRemoteId = null,  // To Do has no subtasks; its steps aren't tasks
                deleted = false,
                version = modified,
                modifiedAt = modified?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            )
        }

        private fun remoteTaskOf(listId: String, task: JsonObject) =
            RemoteTask(listId, task.string("id").orEmpty(), task.string("lastModifiedDateTime"), task.string("title"))

        private fun JsonObject.string(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString

        private fun JsonObject.items(): List<JsonObject> =
            getAsJsonArray("value")?.mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }.orEmpty()
    }
}
