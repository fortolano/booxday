package com.boox.day.tasksync.google

import android.content.Context
import com.boox.day.data.db.TaskSyncLink
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.sync.GoogleTasksAuth
import com.boox.day.sync.SyncErrorType
import com.boox.day.sync.googleErrorType
import com.boox.day.sync.isGoogleTokenRefused
import com.boox.day.tasksync.OutgoingTask
import com.boox.day.tasksync.RemoteChanges
import com.boox.day.tasksync.RemoteList
import com.boox.day.tasksync.RemoteTask
import com.boox.day.tasksync.RemoteTaskState
import com.boox.day.tasksync.TaskSyncService
import com.boox.day.tasksync.TaskSyncServiceException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpResponseException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.Data
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Google Tasks for task sync (Oct 2026). Plan: C:\Users\fort\.claude\plans\reactive-wiggling-micali.md,
 * step 2.2. One account, with its own Tasks permission (sync/GoogleTasksAuth).
 *
 * - **Reading:** the lists; what changed since a cursor (Google's own `updated` times, so the
 *   tablet's clock doesn't matter); every id of a list, for the daily check that catches deletions
 *   Google stops reporting. Completed and hidden tasks are asked for explicitly: tasks ticked in
 *   Google's own apps are hidden, and Google's docs disagree on showHidden's default.
 * - **Sending:** a new task is inserted (under its parent); an update first reads the copy and
 *   stops with [TaskSyncServiceException.RemoteChanged] if it changed in Google since the last sync,
 *   then patches only the fields that changed on the tablet (notes are never touched), and moves
 *   it for a new parent. A delete of a copy already gone counts as done.
 * - **Errors:** a refused token or 401 means "sign in again"; rate limits, 429 and 503 mean "busy"
 *   (Google's Retry-After when it sends one); no connection, or a reply lost on the way, means
 *   offline. Anything else reaches the sender as it is, which retries it with growing waits.
 *
 * Not in Google's docs, checked on the "BooxDay test" list (plan step 2.9): whether deleting a
 * parent deletes its subtasks (the sender moves them out first, TaskSyncSender), and whether
 * "completed": null and "due": null clear those fields.
 */
internal class GoogleTasksProvider(
    private val tasks: Tasks,
    private val clock: () -> Long = System::currentTimeMillis
) : TaskSyncService {

    override val provider: String = TaskSyncProvider.GOOGLE_TASKS

    override suspend fun lists(): List<RemoteList> = call {
        val lists = mutableListOf<RemoteList>()
        var page: String? = null
        do {
            val result = tasks.tasklists().list().setMaxResults(LISTS_PAGE_SIZE).setPageToken(page).execute()
            result.items.orEmpty().mapTo(lists) { RemoteList(it.id, it.title.orEmpty()) }
            page = result.nextPageToken
        } while (page != null)
        lists
    }

    /** Google's own default list ("My Tasks"), by the API's "@default" name. */
    override suspend fun mainList(): RemoteList? = call {
        tasks.tasklists().get(DEFAULT_LIST).execute().let { RemoteList(it.id, it.title.orEmpty()) }
    }

    override suspend fun changes(listId: String, cursor: String?): RemoteChanges = call {
        val since = cursor?.let(::instantOf)
        val found = mutableListOf<RemoteTaskState>()
        var newest = since
        var page: String? = null
        do {
            val request = tasks.tasks().list(listId)
                .setShowCompleted(true).setShowHidden(true).setShowDeleted(true)
                .setMaxResults(TASKS_PAGE_SIZE).setPageToken(page)
            // A little earlier than the newest change seen: one Google hadn't listed yet still comes
            since?.let { request.setUpdatedMin(it.minusMillis(CURSOR_MARGIN_MS).toString()) }
            val result = onList(listId) { request.execute() }
            for (item in result.items.orEmpty()) {
                found += stateOf(listId, item)
                val updated = item.updated?.let(::instantOf) ?: continue
                if (newest == null || updated.isAfter(newest)) newest = updated
            }
            page = result.nextPageToken
        } while (page != null)
        RemoteChanges(found, newest?.toString() ?: cursor)
    }

    override suspend fun fetch(listId: String, remoteId: String): RemoteTaskState? = call {
        try {
            onTask(listId) { tasks.tasks().get(listId, remoteId).execute() }
                .takeUnless { it.deleted == true }
                ?.let { stateOf(listId, it) }
        } catch (e: TaskSyncServiceException.NotFound) {
            null
        }
    }

    override suspend fun allIds(listId: String): Set<String> = call {
        val ids = HashSet<String>()
        var page: String? = null
        do {
            val result = onList(listId) {
                tasks.tasks().list(listId)
                    .setShowCompleted(true).setShowHidden(true).setShowDeleted(false)
                    .setMaxResults(TASKS_PAGE_SIZE).setPageToken(page)
                    .setFields("items(id),nextPageToken")
                    .execute()
            }
            result.items.orEmpty().mapNotNullTo(ids) { it.id }
            page = result.nextPageToken
        } while (page != null)
        ids
    }

    // One list call, then one per list not skipped: only when a synced copy is gone (owner decision 49)
    override suspend fun findInOtherLists(skip: Set<String>, remoteId: String, title: String?, dueDay: String?, since: Long): RemoteTaskState? {
        for (list in lists()) {
            if (list.listId in skip) continue
            val found = call {
                var page: String? = null
                var match: RemoteTaskState? = null
                do {
                    val result = try {
                        onList(list.listId) {
                            tasks.tasks().list(list.listId)
                                .setShowCompleted(true).setShowHidden(true).setShowDeleted(false)
                                .setUpdatedMin(Instant.ofEpochMilli(since).toString())
                                .setMaxResults(TASKS_PAGE_SIZE).setPageToken(page)
                                .execute()
                        }
                    } catch (e: TaskSyncServiceException.ListGone) {
                        null  // deleted meanwhile: nothing there
                    } ?: break
                    match = result.items.orEmpty().map { stateOf(list.listId, it) }.firstOrNull { copy ->
                        copy.remoteId == remoteId || (title != null && copy.title == title && copy.dueDay == dueDay)
                    }
                    page = result.nextPageToken
                } while (match == null && page != null)
                match
            }
            if (found != null) return found
        }
        return null
    }

    override suspend fun upsert(task: OutgoingTask, link: TaskSyncLink?): RemoteTask = call {
        if (link == null) insert(task) else update(task, link)
    }

    override suspend fun delete(listId: String, remoteId: String) = call {
        try {
            tasks.tasks().delete(listId, remoteId).execute()
        } catch (e: HttpResponseException) {
            if (!isGone(e)) throw e  // already gone (or its list is): done
        }
        Unit
    }

    private fun insert(task: OutgoingTask): RemoteTask {
        val body = Task()
            .setTitle(titleFor(task.title))
            .setStatus(statusFor(task.completed))
            .setDue(task.dueDay?.let(::dueFor))
        val request = tasks.tasks().insert(task.listId, body)
        task.parentRemoteId?.let { request.setParent(it) }
        val created = onParentOrList(task.listId) { request.execute() }
        return remoteTaskOf(task.listId, created)
    }

    private fun update(task: OutgoingTask, link: TaskSyncLink): RemoteTask {
        val listId = link.listId
        val remoteId = link.remoteId
        val before = onTask(listId) { tasks.tasks().get(listId, remoteId).execute() }
        if (before.deleted == true) throw TaskSyncServiceException.NotFound()
        if (before.updated != link.remoteVersion) throw TaskSyncServiceException.RemoteChanged(stateOf(listId, before))

        // Only what changed on the tablet since the last sync (the copy is as it was then)
        val patch = Task()
        var patched = false
        val title = titleFor(task.title)
        if (title != link.syncedTitle) {
            patch.setTitle(title)
            patched = true
        }
        if (task.completed != link.syncedCompleted) {
            patch.setStatus(statusFor(task.completed))
            if (!task.completed) patch.setCompleted(Data.NULL_STRING)
            patched = true
        }
        val lastDue = if (link.remoteUndated) null else link.syncedDueDay
        if (task.dueDay != lastDue) {
            patch.setDue(task.dueDay?.let(::dueFor) ?: Data.NULL_STRING)
            patched = true
        }

        var after = before
        if (patched) after = onTask(listId) { tasks.tasks().patch(listId, remoteId, patch).execute() }
        if (task.parentRemoteId != link.syncedParentRemoteId) {
            // No parent: to the top level (Google's tasks.move)
            val move = tasks.tasks().move(listId, remoteId)
            task.parentRemoteId?.let { move.setParent(it) }
            after = onParentOrList(listId) { move.execute() }
        }
        return remoteTaskOf(listId, after)
    }

    // ── Google's answers ──

    /** A 404/410 for the list itself. */
    private inline fun <T> onList(listId: String, block: () -> T): T = try {
        block()
    } catch (e: HttpResponseException) {
        if (isGone(e)) throw TaskSyncServiceException.ListGone(listId, e)
        throw e
    }

    /** A 404/410 for one task: the task is gone, or its whole list. */
    private inline fun <T> onTask(listId: String, block: () -> T): T = try {
        block()
    } catch (e: HttpResponseException) {
        if (!isGone(e)) throw e
        if (listExists(listId)) throw TaskSyncServiceException.NotFound(e)
        throw TaskSyncServiceException.ListGone(listId, e)
    }

    /**
     * A 404/410 when creating or moving: the list is gone, or the parent is (deleted in Google; the
     * next pull brings that in and the task is sent again without it), which is retried later.
     */
    private inline fun <T> onParentOrList(listId: String, block: () -> T): T = try {
        block()
    } catch (e: HttpResponseException) {
        if (isGone(e) && !listExists(listId)) throw TaskSyncServiceException.ListGone(listId, e)
        throw e
    }

    private fun listExists(listId: String): Boolean = try {
        tasks.tasklists().get(listId).execute()
        true
    } catch (e: HttpResponseException) {
        if (!isGone(e)) throw e
        false
    }

    private fun isGone(e: HttpResponseException) = e.statusCode == 404 || e.statusCode == 410

    /** Google's blocking calls off the caller's thread, their failures as the sender knows them. */
    private suspend fun <T> call(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: IOException) {
            throw failureOf(e)
        }
    }

    private fun failureOf(e: IOException): Exception = when {
        e.isGoogleTokenRefused() -> TaskSyncServiceException.SignInNeeded(e)
        e is HttpResponseException -> {
            val reason = (e as? GoogleJsonResponseException)?.details?.errors?.firstOrNull()?.reason
            when (googleErrorType(e.statusCode, reason)) {
                SyncErrorType.AUTHENTICATION -> TaskSyncServiceException.SignInNeeded(e)
                SyncErrorType.QUOTA -> TaskSyncServiceException.Busy(retryAfterMs(e), e)
                SyncErrorType.SERVER ->
                    if (e.statusCode == 503) TaskSyncServiceException.Busy(retryAfterMs(e), e) else e
                else -> e
            }
        }
        // No connection, a timeout, or the reply lost on the way: the next run tries again
        else -> TaskSyncServiceException.Offline(e)
    }

    /** Google's Retry-After (seconds or a date), when it sends one; at most a day. */
    private fun retryAfterMs(e: HttpResponseException): Long? {
        val value = e.headers?.retryAfter?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val ms = value.toLongOrNull()?.times(1000)
            ?: runCatching {
                ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - clock()
            }.getOrNull()
            ?: return null
        return ms.coerceIn(0L, MAX_RETRY_AFTER_MS)
    }

    companion object {
        private const val APP_NAME = "BooxDay"

        /** Google's maximum page sizes (tasks.list 100, tasklists.list 1,000). */
        private const val TASKS_PAGE_SIZE = 100
        private const val LISTS_PAGE_SIZE = 1000

        /** Google keeps at most 1,024 characters of a title. */
        const val TITLE_MAX = 1024

        private const val DEFAULT_LIST = "@default"
        private const val CURSOR_MARGIN_MS = 5 * 60_000L
        private const val MAX_RETRY_AFTER_MS = 24 * 60 * 60_000L
        private val DAY = Regex("""\d{4}-\d{2}-\d{2}""")

        /** For [email]'s Google Tasks, with its own token (sync/GoogleTasksAuth). */
        fun create(context: Context, email: String): GoogleTasksProvider = GoogleTasksProvider(
            Tasks.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), GoogleTasksAuth.credential(context, email))
                .setApplicationName(APP_NAME)
                .build()
        )

        /** Cut to Google's limit, never inside a character made of two UTF-16 units. */
        internal fun titleFor(text: String): String {
            if (text.length <= TITLE_MAX) return text
            val end = if (Character.isHighSurrogate(text[TITLE_MAX - 1])) TITLE_MAX - 1 else TITLE_MAX
            return text.substring(0, end)
        }

        private fun statusFor(completed: Boolean) = if (completed) STATUS_COMPLETED else STATUS_NEEDS_ACTION

        /** Google keeps only the date of `due`; midnight UTC so no time zone moves it. */
        internal fun dueFor(day: String) = "${day}T00:00:00.000Z"

        /** The date part of Google's `due`, or null when there is none (undated). */
        internal fun dayOf(due: String?): String? = due?.take(10)?.takeIf { DAY.matches(it) }

        private fun instantOf(value: String): Instant? =
            runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()

        private fun stateOf(listId: String, task: Task) = RemoteTaskState(
            listId = listId,
            remoteId = task.id,
            title = task.title.orEmpty(),
            completed = task.status == STATUS_COMPLETED,
            dueDay = dayOf(task.due),
            parentRemoteId = task.parent,
            deleted = task.deleted == true,
            version = task.updated,
            modifiedAt = task.updated?.let(::instantOf)?.toEpochMilli()
        )

        private fun remoteTaskOf(listId: String, task: Task) = RemoteTask(listId, task.id, task.updated, task.title)

        private const val STATUS_COMPLETED = "completed"
        private const val STATUS_NEEDS_ACTION = "needsAction"
    }
}
