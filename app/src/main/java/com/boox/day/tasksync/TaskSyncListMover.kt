package com.boox.day.tasksync

import android.util.Log
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.TaskSyncLink

/**
 * Moves a synced task to another list of its account (owner decisions 47 and 53, 31 Oct 2026). The
 * group moves together: only a main task (or one without subtasks) is moved, and its subtasks in the
 * service go along: moved under it when the service left them behind, followed when it took them.
 * The links keep pointing at the copies, so nothing is sent twice and nothing is cleared.
 */
internal class TaskSyncListMover(
    private val db: AppDatabase,
    private val service: TaskSyncService,
    private val accountEmail: String,
    private val serverUrl: String = "",
    private val clock: () -> Long = System::currentTimeMillis
) {
    enum class Outcome {
        MOVED,
        ALREADY_THERE,
        /** No copy in the service yet (or kept on the tablet only): nothing to move. */
        NOT_SYNCED,
        /** A subtask goes with its main task (owner decision 53). */
        SUBTASK,
        /** The service won't move it (Google: a repeating task); it stays in its list. */
        REFUSED
    }

    private val sync = db.taskSyncDao()
    private val tasks = db.taskDao()

    suspend fun move(taskId: String, toListId: String): Outcome {
        val link = sync.getLink(taskId)?.takeUnless { it.keptOnTablet } ?: return Outcome.NOT_SYNCED
        val task = tasks.getById(taskId) ?: return Outcome.NOT_SYNCED
        if (task.parentTaskId != null || link.remoteParentId != null) return Outcome.SUBTASK
        if (link.listId == toListId) return Outcome.ALREADY_THERE

        val subtasks = sync.getChildLinks(service.provider, accountEmail, serverUrl, link.listId, link.remoteId)
        val moved = try {
            service.moveToList(link.listId, link.remoteId, toListId)
        } catch (e: TaskSyncServiceException.Refused) {
            Log.i(TAG, "The service won't move this task to another list; it stays")
            return Outcome.REFUSED
        }
        db.runInTransaction {
            sync.moveLinkToList(taskId, toListId, moved.remoteId)
            sync.setMovedVersion(taskId, moved.version, clock())
        }
        for (subtask in subtasks) bringAlong(subtask, link, toListId, moved.remoteId)
        Log.i(TAG, "Moved a task with ${subtasks.size} subtask(s) to another list")
        return Outcome.MOVED
    }

    /** One subtask of the moved task ([parent]: its link before the move): moved under it, or followed. */
    private suspend fun bringAlong(subtask: TaskSyncLink, parent: TaskSyncLink, toListId: String, newParentRemoteId: String) {
        val now = if (service.fetch(parent.listId, subtask.remoteId) != null) {
            service.moveToList(parent.listId, subtask.remoteId, toListId, newParentRemoteId)
        } else {
            // Taken along under the same id; under a new one the next pull follows it (owner decision 49)
            service.fetch(toListId, subtask.remoteId) ?: return
        }
        db.runInTransaction {
            sync.moveLinkToList(subtask.taskId, toListId, now.remoteId)
            sync.setMovedVersion(subtask.taskId, now.version, clock())
            sync.setMovedParent(subtask.taskId, parent.remoteId, newParentRemoteId)
        }
    }

    private companion object {
        const val TAG = "TaskSyncListMover"
    }
}
