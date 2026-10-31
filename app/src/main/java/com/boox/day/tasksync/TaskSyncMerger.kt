package com.boox.day.tasksync

import android.util.Log
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskSyncLink
import java.time.LocalDate

/**
 * Brings a task service's changes onto the tablet, field by field against what was last synced
 * (task sync, Oct 2026). Plan: C:\Users\fort\.claude\plans\reactive-wiggling-micali.md, step 2.3.
 *
 * - **Words, done mark, due day:** a field changed only in the service is taken; changed only on
 *   the tablet, the tablet's goes back; changed on both, the later change wins (the tablet's time is
 *   when its change was first noticed, the outbox entry). A handwritten task keeps its words: ink
 *   wins and the recognized words go back (owner decision 2).
 * - **New due day** (owner decision 10): the task moves to that day with its ink (and its subtasks,
 *   as Move does), or only its own due day changes when it has one apart from its day. A subtask
 *   only changes its own due day (decision 25); one BooxDay refuses (after its parent's) goes back.
 *   No due day in the service: the task stays where it is, undated there (decision 1).
 * - **Subtasks:** each side keeps its own grouping (decision 24); the service's parent is only
 *   recorded (remoteParentId), for deleting a parent safely.
 * - **Deleted in the service:** the row is cleared, unless the task changed on the tablet since the
 *   last sync: then it stays on the tablet only (keptOnTablet) and is never sent again. A whole list
 *   deleted: each of its tasks the same way (decision 23).
 * - **New in the service:** linked to an unlinked tablet task with the same words and due day if
 *   there is one (a reply lost on the way, a restore), else added to the tablet (step 2.4).
 *
 * A change that can't be made now ([Result.WAITS]: a full-screen day, no row size yet) leaves
 * everything as it was; the pull keeps its cursor, so it comes again at the next sync.
 */
internal class TaskSyncMerger(
    private val db: AppDatabase,
    private val tablet: TabletTasks,
    private val provider: String,
    private val accountEmail: String,
    private val serverUrl: String = "",
    private val clock: () -> Long = System::currentTimeMillis,
    private val today: () -> String = { LocalDate.now().toString() }
) {
    enum class Result { APPLIED, UNCHANGED, WAITS }

    /** Rolls a merge's transaction back when part of it can't be done now. */
    private class NotNow : Exception("Not now")

    private val sync = db.taskSyncDao()
    private val tasks = db.taskDao()

    /** One task as the service has it now: from a pull, or a send that found it changed. */
    suspend fun merge(remote: RemoteTaskState): Result {
        val link = sync.getLinkByRemote(provider, accountEmail, serverUrl, remote.listId, remote.remoteId)
            ?: return incoming(remote)
        if (link.keptOnTablet) return Result.UNCHANGED
        if (remote.deleted) return gone(link)
        // Unchanged since the last sync: the pull's margin brings our own writes back too
        if (remote.version != null && remote.version == link.remoteVersion) return Result.UNCHANGED
        val task = tasks.getById(link.taskId) ?: return Result.UNCHANGED
        return notNowIsWaits { tablet.inTransaction { update(task, link, remote) } }
    }

    /** The copy is gone from the service: deleted there, or missing from the daily check. */
    fun gone(link: TaskSyncLink): Result {
        if (link.keptOnTablet) return Result.UNCHANGED
        val task = tasks.getById(link.taskId)
        if (task == null) {
            tasks.dropSyncLink(link.taskId)
            return Result.APPLIED
        }
        // A row that only lost its words to recognition hasn't changed (owner decision 21)
        val changedHere = link.orphanedAt == null &&
            (TaskSyncRules.contentHash(task, hashParentOf(task)) != link.contentHash || sync.queuedUpsertAt(task.id) != null)
        return notNowIsWaits {
            tablet.inTransaction {
                tasks.dropQueuedSyncUpsert(task.id)
                if (changedHere) {
                    sync.markKeptOnTablet(task.id)
                    Log.i(TAG, "Deleted in the service but changed here: kept on the tablet only")
                } else {
                    tasks.dropSyncLink(task.id)  // first: clearing a linked row would delete the copy again
                    if (!tablet.clear(task)) throw NotNow()
                }
            }
            Result.APPLIED
        }
    }

    /** The whole list is gone from the service: each of its tasks as if deleted there (owner decision 23). */
    fun listGone(listId: String): Result {
        var waits = false
        for (link in sync.getLinksInList(provider, accountEmail, serverUrl, listId)) {
            if (gone(link) == Result.WAITS) waits = true
        }
        if (waits) return Result.WAITS  // the list stays until every task is done, so this comes again
        sync.dropDeletesForList(provider, accountEmail, serverUrl, listId)
        sync.deleteList(provider, accountEmail, serverUrl, listId)
        Log.i(TAG, "A synced list is gone from the service; its tasks were handled")
        return Result.APPLIED
    }

    private fun update(task: Task, link: TaskSyncLink, remote: RemoteTaskState): Result {
        val text = TaskSyncRules.textToSend(task)
        val lastDue = if (link.remoteUndated) null else link.syncedDueDay
        val dueHere = dueSent(task, link)
        val localAt = sync.queuedUpsertAt(task.id) ?: clock()
        val remoteAt = remote.modifiedAt ?: Long.MIN_VALUE
        fun serviceWins(changedHere: Boolean) = !changedHere || remoteAt > localAt

        val takeWords = remote.title != link.syncedTitle && remote.title.isNotBlank() && text != null &&
            remote.title != text && !TaskSyncRules.isHandwrittenOnly(task) && serviceWins(text != link.syncedTitle)
        val takeDone = remote.completed != link.syncedCompleted && remote.completed != task.isCompleted &&
            serviceWins(task.isCompleted != link.syncedCompleted)
        val takeDue = remote.dueDay != lastDue && remote.dueDay != dueHere && serviceWins(dueHere != lastDue)

        // The move first: if it can't be done now, nothing is written
        var row = task
        if (takeDue) row = applyDue(task, remote.dueDay)
        if (takeWords) tablet.setWords(row, remote.title)
        if (takeDone) tablet.setCompleted(row.id, remote.completed)

        val after = tasks.getById(row.id) ?: throw IllegalStateException("Merged row vanished")
        sync.updateMergedLink(
            taskId = after.id,
            remoteVersion = remote.version,
            title = remote.title,
            completed = remote.completed,
            dueDay = remote.dueDay ?: after.dayDate,
            remoteUndated = remote.dueDay == null,
            remoteParentId = remote.parentRemoteId,
            contentHash = TaskSyncRules.contentHash(after, hashParentOf(after)),
            now = clock()
        )
        sendBackWhatDiffers(after, remote)
        return if (takeWords || takeDone || takeDue) Result.APPLIED else Result.UNCHANGED
    }

    /** The service's new due day on the tablet (owner decisions 1, 10, 25); returns the row holding the task. */
    private fun applyDue(task: Task, dueDay: String?): Task {
        if (dueDay == null) {
            // Undated in the service: the task stays on its day
            if (task.dueDay != null) tablet.setDueDay(task.id, null)
            return task
        }
        val isSubtask = task.parentTaskId?.let { tasks.getById(it) } != null
        val ownDueDay = task.dueDay != null && task.dueDay != task.dayDate
        if (isSubtask || ownDueDay) {
            tablet.setDueDay(task.id, dueDay)  // refused for a subtask: the tablet's goes back
            return task
        }
        if (dueDay == task.dayDate) return task
        val link = sync.getLink(task.id) ?: return task
        if (!tablet.moveToDay(task, dueDay)) throw NotNow()
        // The link moved with the task to its new row
        val moved = sync.getLinkByRemote(provider, accountEmail, serverUrl, link.listId, link.remoteId)
            ?.let { tasks.getById(it.taskId) } ?: throw IllegalStateException("Moved task lost its link")
        // Its own due day (the same as its day) follows it
        if (moved.dueDay != null && moved.dueDay != dueDay) tablet.setDueDay(moved.id, dueDay)
        return tasks.getById(moved.id) ?: moved
    }

    /** What the tablet keeps differently from the service is queued for sending; else nothing waits. */
    private fun sendBackWhatDiffers(task: Task, remote: RemoteTaskState) {
        val link = sync.getLink(task.id) ?: return
        val text = TaskSyncRules.textToSend(task)
        val parentLink = task.parentTaskId?.let { sync.getLink(it) }
        val tabletParent = parentLink?.takeIf { it.listId == link.listId }?.remoteId
        val differs = (text != null && text != remote.title) ||
            task.isCompleted != remote.completed ||
            dueSent(task, link) != remote.dueDay ||
            tabletParent != link.syncedParentRemoteId
        if (differs) sync.queueUpsert(task.id, clock()) else tasks.dropQueuedSyncUpsert(task.id)
    }

    private suspend fun incoming(remote: RemoteTaskState): Result {
        if (remote.deleted) return Result.UNCHANGED
        findMatch(remote)?.let { match ->
            tablet.inTransaction {
                sync.insertLink(linkFor(match, remote))
                sendBackWhatDiffers(match, remote)
            }
            return Result.APPLIED
        }
        // On its due day; undated, on the day it first comes (owner decision 1)
        val day = remote.dueDay ?: today()
        val added = tablet.add(day, remote.title, remote.completed) { row -> sync.insertLink(linkFor(row, remote)) }
        return if (added) Result.APPLIED else Result.WAITS
    }

    /** An unlinked tablet task with the same words and due day: the same task (match before create). */
    private fun findMatch(remote: RemoteTaskState): Task? {
        val title = remote.title.trim().takeIf { it.isNotEmpty() } ?: return null
        return sync.getUnlinkedTasksDue(remote.dueDay ?: today()).firstOrNull { task ->
            TaskSyncRules.textToSend(task) == title && (remote.dueDay != null || task.dueDay == null)
        }
    }

    private fun linkFor(task: Task, remote: RemoteTaskState): TaskSyncLink {
        val parentLink = task.parentTaskId?.let { sync.getLink(it) }
        return TaskSyncLink(
            taskId = task.id, provider = provider, accountEmail = accountEmail, serverUrl = serverUrl,
            listId = remote.listId, remoteId = remote.remoteId, remoteVersion = remote.version,
            syncedTitle = remote.title, syncedCompleted = remote.completed,
            syncedDueDay = remote.dueDay ?: task.dayDate, remoteUndated = remote.dueDay == null,
            // Each side keeps its own grouping (owner decision 24)
            syncedParentRemoteId = parentLink?.takeIf { it.listId == remote.listId }?.remoteId,
            remoteParentId = remote.parentRemoteId,
            contentHash = TaskSyncRules.contentHash(task, parentLink?.remoteId),
            syncedAt = clock()
        )
    }

    /** The due day the tablet would send now: null while the copy stays undated (owner decision 1). */
    private fun dueSent(task: Task, link: TaskSyncLink): String? =
        if (link.remoteUndated && task.dueDay == null && task.dayDate == link.syncedDueDay) null
        else TaskSyncRules.dueDay(task)

    /** The parent's copy as the change watcher hashes it (TaskSyncScanner). */
    private fun hashParentOf(task: Task): String? = task.parentTaskId?.let { sync.getLink(it)?.remoteId }

    private inline fun notNowIsWaits(block: () -> Result): Result = try {
        block()
    } catch (e: NotNow) {
        Result.WAITS
    }

    private companion object {
        const val TAG = "TaskSyncMerger"
    }
}
