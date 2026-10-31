package com.boox.day.tasksync

import com.boox.day.data.db.TaskSyncLink

/**
 * A task service as task sync sees it (Oct 2026): Google Tasks from the Google Tasks stage, CalDAV
 * task lists later. Pulling and merging come with the services; this is the sending side.
 */
internal interface TaskSyncService {
    /** [com.boox.day.data.db.TaskSyncProvider] */
    val provider: String

    /** Creates the task ([link] null) or updates the copy [link] points to; returns what it holds now. */
    suspend fun upsert(task: OutgoingTask, link: TaskSyncLink?): RemoteTask

    /** Deletes a copy; one already gone counts as deleted. */
    suspend fun delete(listId: String, remoteId: String)
}

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
    val version: String?
)

/** Failures a service reports so the sender can react; anything else is retried with growing waits. */
internal sealed class TaskSyncServiceException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The account's permission is gone or refused: stop until the user reconnects. */
    class SignInNeeded(cause: Throwable? = null) : TaskSyncServiceException("Sign in again", cause)

    /** Too many requests: wait [retryAfterMs] (the service's own figure when it gives one). */
    class Busy(val retryAfterMs: Long?, cause: Throwable? = null) : TaskSyncServiceException("Service busy", cause)

    /** No connection: everything waits for the next run. */
    class Offline(cause: Throwable? = null) : TaskSyncServiceException("Offline", cause)
}
