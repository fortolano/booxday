package com.boox.day.tasksync

import android.util.Log
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskSyncKept
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
 *   last sync, or is ticked (a past day's record, decision 26): then it stays on the tablet only
 *   (keptOnTablet) and is never sent again. A whole list deleted: each of its tasks the same way
 *   (decision 23).
 * - **New in the service:** linked to an unlinked tablet task with the same words and due day if
 *   there is one (a reply lost on the way, a restore), else added to the tablet on its due day
 *   (step 2.4): unticked ones only (decision 27), typed, due on the service's due date with its
 *   reminder (decision 43, replaced 28's "no due day"; undated ones have none).
 *
 * - **Moved to another list in the service** (owner decision 49): followed, so the row and its ink
 *   stay: the same id in a synced list, or the same words and due day under a new id while the old
 *   copy is gone. Gone from a synced list but found in one that isn't synced: kept on the tablet,
 *   no longer synced. [service] looks; without it a copy gone from its list is handled as deleted.
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
    private val today: () -> String = { LocalDate.now().toString() },
    private val service: TaskSyncService? = null,
    /** Priority and reminder sync too (a CalDAV or To Do list; owner decisions 67, 80, 81). */
    private val extras: Boolean = service?.carriesExtras == true,
    /** The part of a priority the service keeps ([TaskSyncService.keptPriority]: To Do only A). */
    private val keptPriority: (String) -> String = { priority -> service?.keptPriority(priority) ?: priority }
) {
    enum class Result { APPLIED, UNCHANGED, WAITS }

    /** Copies reported deleted in the pull now running ((list, id)): set by [TaskSyncPuller]. */
    var goneInThisPull: Set<Pair<String, String>> = emptySet()

    /** Rolls a merge's transaction back when part of it can't be done now. */
    private class NotNow : Exception("Not now")

    private val sync = db.taskSyncDao()
    private val tasks = db.taskDao()

    /** One task as the service has it now: from a pull, or a send that found it changed. */
    suspend fun merge(remote: RemoteTaskState): Result {
        val link = sync.getLinkByRemote(provider, accountEmail, serverUrl, remote.listId, remote.remoteId)
            ?: movedHere(remote)
            ?: return incoming(remote)
        if (link.keptOnTablet) return Result.UNCHANGED
        if (remote.deleted) return gone(link, completedThere = remote.completed)
        // Unchanged since the last sync: the pull's margin brings our own writes back too
        if (remote.version != null && remote.version == link.remoteVersion) return Result.UNCHANGED
        val task = tasks.getById(link.taskId) ?: return Result.UNCHANGED
        if (remote.rolledOver || tickOutrun(task, link, remote)) return rolledOver(task, link, remote)
        return notNowIsWaits { tablet.inTransaction { update(task, link, remote) } }
    }

    /**
     * The tablet's tick of a repeating copy hasn't gone and the copy's due day changed since the last
     * sync (rolled over already, or moved on the phone): as the send would (owner decision 100), the
     * tick stays on the tablet and the copy is never ticked. Merged as a date change, the ticked task
     * would move to the next one's day and its tick would tick that one.
     */
    private fun tickOutrun(task: Task, link: TaskSyncLink, remote: RemoteTaskState): Boolean =
        remote.rollsOver && !remote.completed && task.isCompleted && !link.syncedCompleted && remote.dueDay != dueOf(link)

    /**
     * A repeating copy ticked and rolled over to its next one under the same id (owner decision 100):
     * the task is ticked on its day and stays there with its subtasks that are part of the copy (To
     * Do steps), kept on the tablet only ([keepRolledOver]); the id, the next one now, comes in as a
     * new task with its subtasks.
     */
    private suspend fun rolledOver(task: Task, link: TaskSyncLink, remote: RemoteTaskState): Result {
        val kept = notNowIsWaits {
            tablet.inTransaction {
                if (!task.isCompleted) tablet.setCompleted(task.id, true)
                keepWithParts(link)
            }
            Result.APPLIED
        }
        if (kept == Result.WAITS) return kept
        Log.i(TAG, "A repeating task was ticked in the service; kept ticked on the tablet, its next one comes in")
        return incoming(remote)
    }

    /**
     * The tablet's tick made [link]'s copy the next one of its repeat, or it already was (a To Do
     * repeating task, owner decision 100): the task stays ticked on its day with its subtasks that are
     * part of the copy, kept on the tablet only (as a ticked task deleted in the service, decision 26)
     * and never sent again, so the next one is never ticked. Their links leave the copies' ids, which
     * hold the next one now: it comes in as a new task with its steps.
     */
    fun keepRolledOver(link: TaskSyncLink) {
        db.runInTransaction { keepWithParts(link) }
        Log.i(TAG, "A repeating task was ticked; kept ticked on the tablet, its next one comes in")
    }

    /** [keepRolledOver] inside the caller's transaction. */
    private fun keepWithParts(link: TaskSyncLink) {
        val parts = service?.let { s ->
            sync.getLinksInList(provider, accountEmail, serverUrl, link.listId).filter { !it.keptOnTablet && s.isPartOf(it.remoteId, link.remoteId) }
        }.orEmpty()
        for (kept in parts + link) {
            tasks.dropQueuedSyncUpsert(kept.taskId)
            sync.moveLinkToList(kept.taskId, kept.listId, TaskSyncRules.ROLLED_OVER + kept.taskId)
            sync.markKeptOnTablet(kept.taskId, TaskSyncKept.ROLLED_OVER)
        }
    }

    /**
     * A copy new in [remote]'s list that is a synced task moved there from another list (owner
     * decision 49): its link moves with it and the merge goes on as for any change. Null: it isn't.
     */
    private suspend fun movedHere(remote: RemoteTaskState): TaskSyncLink? {
        if (remote.deleted) return null
        val elsewhere = sync.getLinksElsewhere(provider, accountEmail, serverUrl, remote.listId, remote.remoteId, remote.title)
        val moved = elsewhere.firstOrNull { it.remoteId == remote.remoteId }
            ?: elsewhere.firstOrNull {
                it.syncedTitle == remote.title && (!remote.carriesDue || dueOf(it) == remote.dueDay) && oldCopyGone(it)
            }
            ?: return null
        sync.moveLinkToList(moved.taskId, remote.listId, remote.remoteId)
        Log.i(TAG, "A synced task was moved to another list in the service; followed")
        return sync.getLink(moved.taskId)
    }

    /** Its copy was reported deleted in this pull, or isn't there any more. */
    private suspend fun oldCopyGone(link: TaskSyncLink): Boolean {
        if ((link.listId to link.remoteId) in goneInThisPull) return true
        val service = service ?: return false
        return try {
            service.fetch(link.listId, link.remoteId) == null
        } catch (e: TaskSyncServiceException.ListGone) {
            true
        }
    }

    /** The due day the service had at the last sync (null: undated). */
    private fun dueOf(link: TaskSyncLink): String? = if (link.remoteUndated) null else link.syncedDueDay

    /**
     * The copy is gone from the service: deleted there, or missing from the daily check.
     * [completedThere]: it was ticked in the service when it was deleted. [lookElsewhere]: before
     * clearing the row, look for the copy in the lists that aren't synced (owner decision 49).
     */
    suspend fun gone(link: TaskSyncLink, completedThere: Boolean = false, lookElsewhere: Boolean = true): Result {
        if (link.keptOnTablet) return Result.UNCHANGED
        val task = tasks.getById(link.taskId)
        if (task == null) {
            tasks.dropSyncLink(link.taskId)
            return Result.APPLIED
        }
        // A row that only lost its words to recognition hasn't changed (owner decision 21)
        val changedHere = link.orphanedAt == null &&
            (hashOf(task) != link.contentHash || sync.queuedUpsertAt(task.id) != null)
        // A ticked task stays on its day as a record ("Delete all completed tasks" on the phone,
        // owner decision 26), also one ticked there just before it was deleted
        val ticked = task.isCompleted || completedThere
        if (!changedHere && !ticked && lookElsewhere && movedToUnsyncedList(link)) {
            tablet.inTransaction {
                tasks.dropQueuedSyncUpsert(task.id)
                sync.markKeptOnTablet(task.id, TaskSyncKept.MOVED_TO_UNSYNCED_LIST)
            }
            Log.i(TAG, "Moved in the service to a list that isn't synced; kept on the tablet only")
            return Result.APPLIED
        }
        return notNowIsWaits {
            tablet.inTransaction {
                tasks.dropQueuedSyncUpsert(task.id)
                if (changedHere || ticked) {
                    if (!changedHere && !task.isCompleted) tablet.setCompleted(task.id, true)
                    sync.markKeptOnTablet(task.id, if (changedHere) TaskSyncKept.CHANGED_HERE else TaskSyncKept.TICKED)
                    Log.i(TAG, "Deleted in the service, kept on the tablet only (${if (changedHere) "changed here" else "ticked"})")
                } else {
                    tasks.dropSyncLink(task.id)  // first: clearing a linked row would delete the copy again
                    if (!tablet.clear(task)) throw NotNow()
                }
            }
            Result.APPLIED
        }
    }

    /** The copy is in a list that isn't synced (a failed look throws: nothing is cleared on a guess). */
    private suspend fun movedToUnsyncedList(link: TaskSyncLink): Boolean {
        val service = service ?: return false
        val synced = sync.getListsFor(provider, accountEmail, serverUrl).mapTo(HashSet()) { it.listId }
        return service.findInOtherLists(
            skip = synced + link.listId, remoteId = link.remoteId, title = link.syncedTitle, dueDay = dueOf(link),
            since = link.syncedAt - MOVE_LOOK_BACK_MS
        ) != null
    }

    /** The whole list is gone from the service: each of its tasks as if deleted there (owner decision 23). */
    suspend fun listGone(listId: String): Result {
        var waits = false
        for (link in sync.getLinksInList(provider, accountEmail, serverUrl, listId)) {
            if (gone(link, lookElsewhere = false) == Result.WAITS) waits = true
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
        // A copy without a due day of its own (a To Do step) never changes the tablet's
        val takeDue = remote.carriesDue && remote.dueDay != lastDue && remote.dueDay != dueHere && serviceWins(dueHere != lastDue)

        // Priority and reminder (a CalDAV or To Do list), by the same rules; a link from before they
        // synced counts as in step with the tablet. Priority as far as the service keeps it (To Do: A)
        val theirs = remote.extras?.takeIf { extras }
        val ours = keptExtras(task, dueHere)
        val lastPriority = link.syncedPriority ?: ours.priority
        val lastReminder = if (link.syncedPriority != null) link.syncedReminder else ours.reminderMinute
        val takePriority = theirs != null && theirs.priority != lastPriority && theirs.priority != ours.priority &&
            serviceWins(ours.priority != lastPriority)
        val takeReminder = theirs != null && theirs.reminderMinute != lastReminder && theirs.reminderMinute != ours.reminderMinute &&
            serviceWins(ours.reminderMinute != lastReminder)

        // The move first: if it can't be done now, nothing is written
        var row = task
        if (takeDue) row = applyDue(task, remote.dueDay)
        if (takeWords) tablet.setWords(row, remote.title)
        if (takeDone) tablet.setCompleted(row.id, remote.completed)
        if (takePriority) tablet.setPriority(row.id, theirs!!.priority)
        if (takeReminder) tablet.setReminder(row.id, theirs!!.reminderMinute)

        val after = tasks.getById(row.id) ?: throw IllegalStateException("Merged row vanished")
        sync.updateMergedLink(
            taskId = after.id,
            remoteVersion = remote.version,
            title = remote.title,
            completed = remote.completed,
            dueDay = if (remote.carriesDue) remote.dueDay ?: after.dayDate else link.syncedDueDay,
            remoteUndated = if (remote.carriesDue) remote.dueDay == null else link.remoteUndated,
            remoteParentId = remote.parentRemoteId,
            priority = theirs?.priority,
            reminder = theirs?.reminderMinute,
            contentHash = hashOf(after),
            now = clock()
        )
        sendBackWhatDiffers(after, remote)
        return if (takeWords || takeDone || takeDue || takePriority || takeReminder) Result.APPLIED else Result.UNCHANGED
    }

    /**
     * The service's new due day on the tablet (owner decisions 1, 10, 25, 43); returns the row holding
     * the task. A date set in the service becomes the task's due day, with its reminder.
     */
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
        if (dueDay == task.dayDate) {
            if (task.dueDay == null) tablet.setDueDay(task.id, dueDay)  // dated on its own day
            return task
        }
        val link = sync.getLink(task.id) ?: return task
        if (!tablet.moveToDay(task, dueDay)) throw NotNow()
        // The link moved with the task to its new row
        val moved = sync.getLinkByRemote(provider, accountEmail, serverUrl, link.listId, link.remoteId)
            ?.let { tasks.getById(it.taskId) } ?: throw IllegalStateException("Moved task lost its link")
        // The new day is its due day (owner decision 43)
        if (moved.dueDay != dueDay) tablet.setDueDay(moved.id, dueDay)
        return tasks.getById(moved.id) ?: moved
    }

    /** What the tablet keeps differently from the service is queued for sending; else nothing waits. */
    private fun sendBackWhatDiffers(task: Task, remote: RemoteTaskState) {
        val link = sync.getLink(task.id) ?: return
        val text = TaskSyncRules.textToSend(task)
        val parentLink = task.parentTaskId?.let { sync.getLink(it) }
        val tabletParent = parentLink?.takeIf { it.listId == link.listId }?.remoteId
        val due = dueSent(task, link)
        val theirs = remote.extras?.takeIf { extras }
        val differs = (text != null && text != remote.title) ||
            task.isCompleted != remote.completed ||
            (remote.carriesDue && due != remote.dueDay) ||
            tabletParent != link.syncedParentRemoteId ||
            (theirs != null && keptExtras(task, due) != theirs)
        if (differs) sync.queueUpsert(task.id, clock()) else tasks.dropQueuedSyncUpsert(task.id)
    }

    /** Priority (as far as the service keeps it) and reminder, as the tablet would send them with [dueDay]. */
    private fun keptExtras(task: Task, dueDay: String?): TaskExtras =
        TaskSyncRules.extras(task, dueDay).let { it.copy(priority = keptPriority(it.priority)) }

    private suspend fun incoming(remote: RemoteTaskState): Result {
        if (remote.deleted) return Result.UNCHANGED
        // Cleared here and its delete not sent yet (the pull runs first): not a new task
        if (sync.hasQueuedDelete(provider, accountEmail, serverUrl, remote.listId, remote.remoteId)) return Result.UNCHANGED
        // On its due day; undated, on the day it first comes (owner decision 1); a To Do step, on its
        // parent's day (owner decision 79)
        val day = if (remote.carriesDue) remote.dueDay ?: today() else parentDay(remote) ?: today()
        findMatch(remote, day)?.let { match ->
            tablet.inTransaction {
                sync.insertLink(linkFor(match, remote))
                sendBackWhatDiffers(match, remote)
            }
            return Result.APPLIED
        }
        // Unticked ones only (owner decision 27): also keeps old finished tasks, left in the service
        // at turn-on, off the tablet when they are touched there
        if (remote.completed || remote.title.isBlank()) return Result.UNCHANGED
        val dueDay = remote.dueDay.takeIf { remote.carriesDue }
        val added = tablet.add(day, remote.title, dueDay, remote.extras?.takeIf { extras }) { row -> sync.insertLink(linkFor(row, remote)) }
        return if (added) Result.APPLIED else Result.WAITS
    }

    /** The day the task linked to [remote]'s parent copy sits on, if it is on the tablet. */
    private fun parentDay(remote: RemoteTaskState): String? = remote.parentRemoteId
        ?.let { sync.getLinkByRemote(provider, accountEmail, serverUrl, remote.listId, it) }
        ?.let { tasks.getById(it.taskId) }?.dayDate

    /** An unlinked tablet task due on [day] with the same words: the same task (match before create). */
    private fun findMatch(remote: RemoteTaskState, day: String): Task? {
        val title = remote.title.trim().takeIf { it.isNotEmpty() } ?: return null
        if (!remote.carriesDue) return pendingSubtask(remote, title)
        return sync.getUnlinkedTasksDue(day).firstOrNull { task ->
            TaskSyncRules.textToSend(task) == title && (remote.dueDay != null || task.dueDay == null)
        }
    }

    /**
     * A To Do step is the tablet's own only with proof: an unlinked subtask of the task linked to the
     * step's parent, with the same words and its send still waiting (a create whose answer was lost).
     * Never a task that merely has the same words on that day: that one is the user's own.
     */
    private fun pendingSubtask(remote: RemoteTaskState, title: String): Task? {
        val parentLink = remote.parentRemoteId?.let { sync.getLinkByRemote(provider, accountEmail, serverUrl, remote.listId, it) } ?: return null
        return tasks.getChildTasks(parentLink.taskId).firstOrNull { child ->
            sync.getLink(child.id) == null && TaskSyncRules.textToSend(child) == title && sync.queuedUpsertAt(child.id) != null
        }
    }

    private fun linkFor(task: Task, remote: RemoteTaskState): TaskSyncLink {
        val parentLink = task.parentTaskId?.let { sync.getLink(it) }
        return TaskSyncLink(
            taskId = task.id, provider = provider, accountEmail = accountEmail, serverUrl = serverUrl,
            listId = remote.listId, remoteId = remote.remoteId, remoteVersion = remote.version,
            syncedTitle = remote.title, syncedCompleted = remote.completed,
            // A copy without a due day of its own: the tablet's, as the sender records it
            syncedDueDay = if (remote.carriesDue) remote.dueDay ?: task.dayDate else TaskSyncRules.dueDay(task),
            remoteUndated = remote.carriesDue && remote.dueDay == null,
            // Each side keeps its own grouping (owner decision 24)
            syncedParentRemoteId = parentLink?.takeIf { it.listId == remote.listId }?.remoteId,
            remoteParentId = remote.parentRemoteId,
            syncedPriority = remote.extras?.takeIf { extras }?.priority,
            syncedReminder = remote.extras?.takeIf { extras }?.reminderMinute,
            contentHash = TaskSyncRules.contentHash(task, parentLink?.remoteId, extras),
            syncedAt = clock()
        )
    }

    /** The due day the tablet would send now: null while the copy stays undated (owner decision 1). */
    private fun dueSent(task: Task, link: TaskSyncLink): String? =
        if (link.remoteUndated && task.dueDay == null && task.dayDate == link.syncedDueDay) null
        else TaskSyncRules.dueDay(task)

    /** The parent's copy as the change watcher hashes it (TaskSyncScanner). */
    private fun hashParentOf(task: Task): String? = task.parentTaskId?.let { sync.getLink(it)?.remoteId }

    /** The task's hash as the change watcher computes it. */
    private fun hashOf(task: Task): String = TaskSyncRules.contentHash(task, hashParentOf(task), extras)

    private inline fun notNowIsWaits(block: () -> Result): Result = try {
        block()
    } catch (e: NotNow) {
        Result.WAITS
    }

    private companion object {
        const val TAG = "TaskSyncMerger"
        /** A moved copy changed in its new list after it was last synced; a little earlier for clocks. */
        const val MOVE_LOOK_BACK_MS = 5 * 60_000L
    }
}
