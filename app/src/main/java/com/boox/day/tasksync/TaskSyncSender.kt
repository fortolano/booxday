package com.boox.day.tasksync

import android.content.SharedPreferences
import android.util.Log
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.TaskSyncLink
import com.boox.day.data.db.TaskSyncOp
import com.boox.day.data.db.TaskSyncOutboxEntry

/**
 * Sends the outbox to one account of one service (Oct 2026). Plan step 1.8; the services and the
 * pull/merge side come in their stages.
 * - The outbox is in the database, so what waits survives restarts and lost connections.
 * - A failed entry waits longer each time (1 min, 2, 4 ... up to 6 h); a busy service's own wait is
 *   kept; offline and "sign in again" stop the run with everything kept.
 * - **Mass-delete guard:** with more than [MASS_DELETE_LIMIT] deletes waiting, nothing is sent until
 *   the user chooses ([approveWaitingDeletes] / [keepWaitingDeletesInService]), so a bug or a big
 *   clear on the tablet can't silently empty someone's lists.
 * - **Match before create:** a new task with the words of an orphaned link takes that link over
 *   (Undo of a move, a recognition hiccup) instead of making a second copy.
 * - Parents go before their subtasks, so a subtask can name its parent's copy.
 */
internal class TaskSyncSender(
    private val db: AppDatabase,
    private val service: TaskSyncService,
    private val accountEmail: String,
    private val serverUrl: String = "",
    private val prefs: SharedPreferences,
    private val clock: () -> Long = System::currentTimeMillis
) {
    sealed class Outcome {
        data class Sent(val count: Int) : Outcome()
        data class PausedForDeletes(val waiting: Int) : Outcome()
        object NeedsReconnect : Outcome()
        object Offline : Outcome()
        data class Busy(val retryAt: Long) : Outcome()
    }

    /** No list for new tasks yet ("New tasks go to" not chosen): the task waits. */
    private class NoListYet : Exception("No list for new tasks")

    private val sync = db.taskSyncDao()
    private val tasks = db.taskDao()

    suspend fun sendDue(): Outcome {
        val waitingDeletes = sync.countDeletesAfter(prefs.getLong(KEY_DELETES_APPROVED_UP_TO, 0L))
        if (waitingDeletes > MASS_DELETE_LIMIT) {
            Log.w(TAG, "Paused: $waitingDeletes deletes waiting for the user's OK")
            return Outcome.PausedForDeletes(waitingDeletes)
        }
        val due = sync.getDueOutbox(clock())
            .sortedBy { entry -> entry.op == TaskSyncOp.UPSERT && tasks.getById(entry.taskId)?.parentTaskId != null }
        var sent = 0
        for (entry in due) {
            try {
                val stillChanged = when (entry.op) {
                    TaskSyncOp.DELETE -> sendDelete(entry)
                    else -> sendUpsert(entry)
                }
                if (!stillChanged) sync.deleteOutboxEntry(entry.id)
                sent++
            } catch (e: TaskSyncServiceException.SignInNeeded) {
                return Outcome.NeedsReconnect
            } catch (e: TaskSyncServiceException.Offline) {
                return Outcome.Offline
            } catch (e: TaskSyncServiceException.Busy) {
                val retryAt = clock() + (e.retryAfterMs ?: DEFAULT_BUSY_WAIT_MS)
                sync.markOutboxFailed(entry.id, entry.attempts, retryAt, "Busy")
                return Outcome.Busy(retryAt)
            } catch (e: Exception) {
                val attempts = entry.attempts + 1
                val wait = minOf(MAX_WAIT_MS, FIRST_WAIT_MS shl minOf(attempts - 1, 20))
                // The class only: messages can hold a task's words, which never go into logs
                sync.markOutboxFailed(entry.id, attempts, clock() + wait, e.javaClass.simpleName)
                Log.w(TAG, "Send failed (${e.javaClass.simpleName}), try $attempts, next in ${wait / 1000} s")
            }
        }
        return Outcome.Sent(sent)
    }

    /** The user said yes to the waiting deletes: they go on the next run. */
    fun approveWaitingDeletes() {
        prefs.edit().putLong(KEY_DELETES_APPROVED_UP_TO, sync.lastDeleteId()).apply()
    }

    /** The user said keep them: the waiting deletes are dropped; the copies stay in the service. */
    fun keepWaitingDeletesInService() {
        sync.dropDeletesAfter(prefs.getLong(KEY_DELETES_APPROVED_UP_TO, 0L))
    }

    private suspend fun sendDelete(entry: TaskSyncOutboxEntry): Boolean {
        val listId = entry.listId
        val remoteId = entry.remoteId
        if (listId != null && remoteId != null) service.delete(listId, remoteId)
        return false
    }

    /** Sends the task's current state; true when it changed again while being sent (sent again). */
    private suspend fun sendUpsert(entry: TaskSyncOutboxEntry): Boolean {
        val task = tasks.getById(entry.taskId) ?: return false  // row gone: the trigger queued its delete
        val title = TaskSyncRules.textToSend(task) ?: return false  // no words: the scanner decides
        var link: TaskSyncLink? = sync.getLink(task.id)
        if (link == null) {
            sync.findOrphan(service.provider, accountEmail, serverUrl, title)?.let { orphan ->
                tasks.moveSyncLink(orphan.taskId, task.id)
                link = sync.getLink(task.id)
            }
        }
        val current = link
        val listId = current?.listId
            ?: sync.getDefaultList(service.provider, accountEmail, serverUrl)?.listId
            ?: throw NoListYet()
        val parentRemoteId = task.parentTaskId?.let { sync.getLink(it)?.remoteId }
        // Undated in the service until the user gives it a due day or moves it (owner decision 1)
        val staysUndated = current != null && current.remoteUndated && task.dueDay == null && task.dayDate == current.syncedDueDay
        val dueDay = if (staysUndated) null else TaskSyncRules.dueDay(task)

        val remote = service.upsert(OutgoingTask(task.id, listId, title, task.isCompleted, dueDay, parentRemoteId), current)

        val sentHash = TaskSyncRules.contentHash(task, parentRemoteId)
        val now = clock()
        db.runInTransaction<Unit> {
            if (sync.getLink(task.id) == null) {
                sync.insertLink(
                    TaskSyncLink(
                        taskId = task.id, provider = service.provider, accountEmail = accountEmail, serverUrl = serverUrl,
                        listId = remote.listId, remoteId = remote.remoteId, remoteVersion = remote.version,
                        syncedTitle = title, syncedCompleted = task.isCompleted, syncedDueDay = dueDay ?: task.dayDate,
                        syncedParentRemoteId = parentRemoteId, contentHash = sentHash, remoteUndated = staysUndated, syncedAt = now
                    )
                )
            } else {
                sync.updateSyncedLink(
                    task.id, remote.listId, remote.remoteId, remote.version, title, task.isCompleted,
                    dueDay ?: task.dayDate, parentRemoteId, sentHash, staysUndated, now
                )
            }
        }
        // Changed while it was being sent: keep the entry, the newer state goes next
        val after = tasks.getById(task.id) ?: return false
        return TaskSyncRules.contentHash(after, parentRemoteId) != sentHash
    }

    companion object {
        private const val TAG = "TaskSyncSender"
        const val MASS_DELETE_LIMIT = 20
        private const val KEY_DELETES_APPROVED_UP_TO = "deletes_approved_up_to"
        private const val FIRST_WAIT_MS = 60_000L
        private const val MAX_WAIT_MS = 6 * 60 * 60_000L
        private const val DEFAULT_BUSY_WAIT_MS = 60_000L
    }
}
