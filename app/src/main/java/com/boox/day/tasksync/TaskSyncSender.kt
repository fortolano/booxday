package com.boox.day.tasksync

import android.content.SharedPreferences
import android.util.Log
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.TaskSyncLink
import com.boox.day.data.db.TaskSyncOp
import com.boox.day.data.db.TaskSyncOutboxEntry

/**
 * Sends the outbox to one account of one service (Oct 2026). Plan steps 1.8 and 2.2-2.3.
 * - The outbox is in the database, so what waits survives restarts and lost connections.
 * - A failed entry waits longer each time (1 min, 2, 4 ... up to 6 h); a busy service's own wait is
 *   kept; offline and "sign in again" stop the run with everything kept.
 * - **Mass-delete guard:** with more than [MASS_DELETE_LIMIT] deletes waiting, nothing is sent until
 *   the user chooses ([approveWaitingDeletes] / [keepWaitingDeletesInService]), so a bug or a big
 *   clear on the tablet can't silently empty someone's lists.
 * - **Match before create:** a new task with the words of an orphaned link takes that link over
 *   (Undo of a move, a recognition hiccup) instead of making a second copy.
 * - Parents go before their subtasks, so a subtask can name its parent's copy.
 * - **Changed in the service since the last sync:** [merger] takes the change in, then the send
 *   is tried once more. Gone from the service, or its whole list: [merger] decides.
 * - A task kept on the tablet (deleted in the service while changed here) is never sent.
 */
internal class TaskSyncSender(
    private val db: AppDatabase,
    private val service: TaskSyncService,
    private val merger: TaskSyncMerger,
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
        for (listed in due) {
            // Read again: sent with another (a subtask before its parent's delete), or moved with its
            // task to another row by a merge (the link and its waiting change go along)
            val entry = sync.getOutboxEntry(listed.id) ?: continue
            try {
                val stillChanged = when (entry.op) {
                    TaskSyncOp.DELETE -> sendDelete(entry)
                    else -> sendUpsert(entry.taskId)
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

    /** The lists the user ticked; tasks in other lists stay on the tablet, unsent (owner decision 31). */
    private fun tickedLists(): Set<String> =
        sync.getListsFor(service.provider, accountEmail, serverUrl).mapTo(HashSet()) { it.listId }

    private suspend fun sendDelete(entry: TaskSyncOutboxEntry): Boolean {
        val listId = entry.listId ?: return false
        val remoteId = entry.remoteId ?: return false
        if (listId !in tickedLists()) return false  // an unticked list: Google keeps its copy
        // A cleared parent's subtasks stay on the tablet (TaskRepository.clearTaskSlot), but Google
        // may delete them with their parent (its docs don't say): they move out first, also the
        // ones made subtasks on the phone (owner decision 24). If one can't be sent, the delete waits.
        for (child in sync.getChildLinks(service.provider, accountEmail, serverUrl, listId, remoteId)) {
            if (!sendUpsert(child.taskId, leavingParent = remoteId)) tasks.dropQueuedSyncUpsert(child.taskId)
        }
        service.delete(listId, remoteId)
        return false
    }

    /**
     * Sends the task's current state; true when it changed again while being sent (sent again).
     * [leavingParent]: the service's parent is about to be deleted, so the copy moves out of it
     * even where the tablet's grouping didn't change.
     */
    private suspend fun sendUpsert(taskId: String, leavingParent: String? = null, retried: Boolean = false): Boolean {
        val task = tasks.getById(taskId) ?: return false  // row gone: the trigger queued its delete
        val title = TaskSyncRules.textToSend(task) ?: return false  // no words: the scanner decides
        var link: TaskSyncLink? = sync.getLink(task.id)
        if (link?.keptOnTablet == true) return false  // deleted in the service while changed here: tablet only
        if (link == null) {
            sync.findOrphan(service.provider, accountEmail, serverUrl, title)?.let { orphan ->
                tasks.moveSyncLink(orphan.taskId, task.id)
                link = sync.getLink(task.id)
            }
        }
        val current = link
        val ticked = tickedLists()
        if (current != null && current.listId !in ticked) return false  // an unticked list: stays here (decision 31)
        val parentLink = task.parentTaskId?.let { sync.getLink(it) }
        // A new subtask goes to its parent's list: a service keeps a subtask in its parent's list
        val listId = current?.listId
            ?: parentLink?.listId?.takeIf { it in ticked }
            ?: sync.getDefaultList(service.provider, accountEmail, serverUrl)?.listId
            ?: throw NoListYet()
        // The hash uses the parent's copy as the scanner does; one in another list isn't sent as parent
        val hashParentId = parentLink?.remoteId
        val parentRemoteId = parentLink?.takeIf { it.listId == listId }?.remoteId
        // Undated in the service until the user gives it a due day or moves it (owner decision 1)
        val staysUndated = current != null && current.remoteUndated && task.dueDay == null && task.dayDate == current.syncedDueDay
        val dueDay = if (staysUndated) null else TaskSyncRules.dueDay(task)

        // The service compares the parent with the tablet's last sent one (owner decision 24)
        val asLinked = if (leavingParent != null) current?.copy(syncedParentRemoteId = leavingParent) else current
        val remote = try {
            service.upsert(OutgoingTask(task.id, listId, title, task.isCompleted, dueDay, parentRemoteId), asLinked)
        } catch (e: TaskSyncServiceException.RemoteChanged) {
            // Changed in the service since the last sync: merged in, then sent once more
            if (retried || merger.merge(e.remote) == TaskSyncMerger.Result.WAITS) throw e
            // The merge may have moved the task to its new due day: the link knows its row now
            val row = current?.let { sync.getLinkByRemote(it.provider, it.accountEmail, it.serverUrl, it.listId, it.remoteId) }
            return sendUpsert(row?.taskId ?: taskId, leavingParent, retried = true)
        } catch (e: TaskSyncServiceException.NotFound) {
            if (current == null || merger.gone(current) == TaskSyncMerger.Result.WAITS) throw e
            return false
        } catch (e: TaskSyncServiceException.ListGone) {
            if (merger.listGone(e.listId) == TaskSyncMerger.Result.WAITS) throw e
            return false
        }

        val sentHash = TaskSyncRules.contentHash(task, hashParentId)
        val keptTitle = remote.title ?: title  // as the service keeps it, so the pull sees no change
        // A new copy, or a parent move: the service's parent is now the one sent
        val moved = asLinked == null || parentRemoteId != asLinked.syncedParentRemoteId
        val serviceParent = if (moved) parentRemoteId else current?.remoteParentId
        val now = clock()
        db.runInTransaction<Unit> {
            if (sync.getLink(task.id) == null) {
                sync.insertLink(
                    TaskSyncLink(
                        taskId = task.id, provider = service.provider, accountEmail = accountEmail, serverUrl = serverUrl,
                        listId = remote.listId, remoteId = remote.remoteId, remoteVersion = remote.version,
                        syncedTitle = keptTitle, syncedCompleted = task.isCompleted, syncedDueDay = dueDay ?: task.dayDate,
                        syncedParentRemoteId = parentRemoteId, remoteParentId = serviceParent, contentHash = sentHash,
                        remoteUndated = staysUndated, syncedAt = now
                    )
                )
            } else {
                sync.updateSyncedLink(
                    task.id, remote.listId, remote.remoteId, remote.version, keptTitle, task.isCompleted,
                    dueDay ?: task.dayDate, parentRemoteId, serviceParent, sentHash, staysUndated, now
                )
            }
        }
        // Changed while it was being sent: keep the entry, the newer state goes next
        val after = tasks.getById(task.id) ?: return false
        return TaskSyncRules.contentHash(after, hashParentId) != sentHash
    }

    companion object {
        private const val TAG = "TaskSyncSender"
        const val MASS_DELETE_LIMIT = 20
        internal const val KEY_DELETES_APPROVED_UP_TO = "deletes_approved_up_to"
        private const val FIRST_WAIT_MS = 60_000L
        private const val MAX_WAIT_MS = 6 * 60 * 60_000L
        private const val DEFAULT_BUSY_WAIT_MS = 60_000L
    }
}
