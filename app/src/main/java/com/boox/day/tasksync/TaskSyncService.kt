package com.boox.day.tasksync

import com.boox.day.data.db.TaskSyncLink

/**
 * A task service as task sync sees it (Oct 2026): Google Tasks (google/GoogleTasksProvider), CalDAV
 * task lists later. Sending and reading; the merge of what is read comes with plan step 2.3.
 */
internal interface TaskSyncService {
    /** [com.boox.day.data.db.TaskSyncProvider] */
    val provider: String

    /** Creates the task ([link] null) or updates the copy [link] points to; returns what it holds now. */
    suspend fun upsert(task: OutgoingTask, link: TaskSyncLink?): RemoteTask

    /** Deletes a copy; one already gone counts as deleted. */
    suspend fun delete(listId: String, remoteId: String)

    // ── Reading (the pull side) ──

    /** The account's task lists, for the Preferences card. */
    suspend fun lists(): List<RemoteList> = emptyList()

    /** The service's main list: ticked and used for new tasks at first turn-on (owner decision 30). */
    suspend fun mainList(): RemoteList? = lists().firstOrNull()

    /**
     * What changed in [listId] since [cursor] (null: everything), deleted ones included, and the
     * cursor to ask from next time. Asking again from an older cursor is harmless.
     */
    suspend fun changes(listId: String, cursor: String?): RemoteChanges = RemoteChanges(emptyList(), cursor)

    /** One copy as it is now, or null when it is gone. */
    suspend fun fetch(listId: String, remoteId: String): RemoteTaskState? = null

    /** Every copy's id in [listId], for the daily check that catches deletions the service stopped reporting. */
    suspend fun allIds(listId: String): Set<String> = emptySet()

    /**
     * A copy gone from its list may have been moved to another one (owner decision 49): looks in the
     * lists not in [skip] for one changed since [since] (epoch ms) with [remoteId], or with [title] and
     * [dueDay]. Null: not found there.
     */
    suspend fun findInOtherLists(skip: Set<String>, remoteId: String, title: String?, dueDay: String?, since: Long): RemoteTaskState? = null
}

internal data class RemoteList(val listId: String, val title: String)

internal data class RemoteChanges(val tasks: List<RemoteTaskState>, val nextCursor: String?)

/** A copy as the service has it. [dueDay] "yyyy-MM-dd" or null (undated). */
internal data class RemoteTaskState(
    val listId: String,
    val remoteId: String,
    val title: String,
    val completed: Boolean,
    val dueDay: String?,
    val parentRemoteId: String?,
    val deleted: Boolean,
    /** Google `updated` (RFC 3339), CalDAV etag: compared with the link's remoteVersion. */
    val version: String?,
    /** When it last changed in the service (epoch ms), for "the later change wins"; null if unknown. */
    val modifiedAt: Long? = null
)

/** What BooxDay sends for one task. [dueDay] null: the copy stays undated (owner decision 1). */
internal data class OutgoingTask(
    val taskId: String,
    val listId: String,
    val title: String,
    val completed: Boolean,
    val dueDay: String?,
    val parentRemoteId: String?
)

internal data class RemoteTask(
    val listId: String,
    val remoteId: String,
    /** Google `updated`, CalDAV etag. */
    val version: String?,
    /** The words as the service keeps them (Google cuts titles at 1,024 characters); null: as sent. */
    val title: String? = null
)

/** Failures a service reports so the sender can react; anything else is retried with growing waits. */
internal sealed class TaskSyncServiceException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The account's permission is gone or refused: stop until the user reconnects. */
    class SignInNeeded(cause: Throwable? = null) : TaskSyncServiceException("Sign in again", cause)

    /** Too many requests: wait [retryAfterMs] (the service's own figure when it gives one). */
    class Busy(val retryAfterMs: Long?, cause: Throwable? = null) : TaskSyncServiceException("Service busy", cause)

    /** No connection: everything waits for the next run. */
    class Offline(cause: Throwable? = null) : TaskSyncServiceException("Offline", cause)

    /** The copy being updated is gone (deleted in the service): the merge decides what happens. */
    class NotFound(cause: Throwable? = null) : TaskSyncServiceException("Gone from the service", cause)

    /** The whole list is gone (deleted in the service): the user picks another. */
    class ListGone(val listId: String, cause: Throwable? = null) : TaskSyncServiceException("List gone from the service", cause)

    /**
     * The copy changed in the service since the last sync: nothing was written. The merge takes
     * [remote] in, then the send is tried once more (plan step 2.2).
     */
    class RemoteChanged(val remote: RemoteTaskState) : TaskSyncServiceException("Changed in the service")
}
