package com.boox.day.tasksync

import com.boox.day.data.db.TaskSyncLink

/**
 * A task service as task sync sees it (Oct 2026): Google Tasks (google/GoogleTasksProvider), CalDAV
 * task lists (caldav/CalDavTasksProvider), Microsoft To Do (microsoft/MicrosoftTodoProvider, Nov 2026).
 */
internal interface TaskSyncService {
    /** [com.boox.day.data.db.TaskSyncProvider] */
    val provider: String

    /** Priority and reminder time sync too ([TaskExtras]): CalDAV and To Do yes, Google Tasks no (owner decision 67). */
    val carriesExtras: Boolean get() = false

    /**
     * The part of a tablet priority the service keeps: what is compared with the copy's and recorded
     * as synced. To Do keeps only A, its star (owner decision 80), so B and C stay on the tablet and a
     * copy without a star never clears them.
     */
    fun keptPriority(priority: String): String = priority

    /**
     * Creates the task ([link] null) or updates the copy [link] points to; returns what it holds now.
     * The copy returned may have another id than [link]'s (a To Do step that became a task, a ticked
     * To Do repeating task's done copy): the link follows it.
     */
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

    /**
     * Moves a copy to another list of the account (owner decision 47), under [parentRemoteId] there
     * if given; returns it as it is now (the service may give it a new id). [TaskSyncServiceException.Refused]:
     * the service won't move this one (Google: repeating tasks).
     */
    suspend fun moveToList(fromListId: String, remoteId: String, toListId: String, parentRemoteId: String? = null): RemoteTaskState =
        throw UnsupportedOperationException("Moving between lists")

    /**
     * The copy in [toListId] of the subtask [remoteId] that [moveToList] of its main task took along,
     * or null. The same id elsewhere; To Do gives a moved task's steps new ids.
     */
    suspend fun takenAlong(toListId: String, remoteId: String): RemoteTaskState? = fetch(toListId, remoteId)

    /**
     * The id the subtask [subtaskRemoteId] of [replaced] has under [replacement], when the service
     * made [replacement] in its place with the same subtasks (To Do: a ticked repeating task's done
     * copy keeps its steps' ids, measured 25 Nov 2026); null: the subtask doesn't follow.
     */
    fun subtaskUnder(replacement: String, subtaskRemoteId: String, replaced: String): String? = null
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
    val modifiedAt: Long? = null,
    /** Priority and reminder as the service has them; null when it doesn't carry them. */
    val extras: TaskExtras? = null,
    /**
     * False: the copy has no due day of its own (a To Do step: words and tick only, owner decision
     * 79). [dueDay] is null then and never changes the tablet's; a new one comes on its parent's day.
     */
    val carriesDue: Boolean = true,
    /**
     * This copy replaces [takesOverRemoteId] in the same list: a To Do repeating task ticked on the
     * phone keeps its id for the next one and is now this new done copy (owner decision 82). The
     * task linked to that id moves to this copy before the round is merged.
     */
    val takesOverRemoteId: String? = null
)

/**
 * A task's priority ("", "A", "B", "C") and its own reminder time (minutes after midnight on its due
 * day; null = the usual time from settings, never sent: owner decision 69). Only for services that
 * carry them ([TaskSyncService.carriesExtras]).
 */
internal data class TaskExtras(val priority: String, val reminderMinute: Int?)

/** What BooxDay sends for one task. [dueDay] null: the copy stays undated (owner decision 1). */
internal data class OutgoingTask(
    val taskId: String,
    val listId: String,
    val title: String,
    val completed: Boolean,
    val dueDay: String?,
    val parentRemoteId: String?,
    /** Null for a service that doesn't carry them. */
    val extras: TaskExtras? = null,
    /** Deterministic creation UID persisted before send so interrupted creates don't duplicate on retry (H15). */
    val createOpId: String? = null,
    /** True when a prior create attempt started without committing its local sync link. */
    val createRetry: Boolean = false,
    /** Already-linked remote IDs in this list, used to avoid adopting an unrelated identical task on Google retry. */
    val knownRemoteIds: Set<String> = emptySet()
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

    /** The service won't do this to this copy (Google won't move a repeating task to another list). */
    class Refused(cause: Throwable? = null) : TaskSyncServiceException("Refused by the service", cause)

    /** The whole list is gone (deleted in the service): the user picks another. */
    class ListGone(val listId: String, cause: Throwable? = null) : TaskSyncServiceException("List gone from the service", cause)

    /** The account can't use the service (a Microsoft account without To Do): signing in again won't help. */
    class NotAvailable(cause: Throwable? = null) : TaskSyncServiceException("Not available for this account", cause)

    /**
     * The copy changed in the service since the last sync: nothing was written. The merge takes
     * [remote] in, then the send is tried once more (plan step 2.2).
     */
    class RemoteChanged(val remote: RemoteTaskState) : TaskSyncServiceException("Changed in the service")
}
