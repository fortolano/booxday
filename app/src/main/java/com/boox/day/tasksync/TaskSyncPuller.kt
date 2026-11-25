package com.boox.day.tasksync

import android.util.Log
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.TaskSyncList

/**
 * Reads what changed in each synced list of one account and merges it (task sync, Oct 2026).
 * Plan: C:\Users\fort\.claude\plans\reactive-wiggling-micali.md, step 2.3. Runs before sending, so
 * a copy made by a send whose reply was lost is found and linked, not made twice.
 *
 * - A list's cursor moves on only when every change in it was merged; one that has to wait (a
 *   full-screen day) comes again at the next pull.
 * - Once a day, every id of the list is checked, to catch deletions the service stopped reporting.
 *   An empty answer while tasks are linked is not taken as "all deleted": deletions arrive as such
 *   with the changes, and this check is only the safety net.
 * - A list gone from the service: its tasks as if deleted there (owner decision 23).
 * - All lists are read before anything is merged, and deletions are merged last, so a task moved
 *   from one synced list to another is followed (owner decision 49).
 * Sign-in, offline and busy stop the pull (thrown); another failure skips that list this time.
 */
internal class TaskSyncPuller(
    private val db: AppDatabase,
    private val service: TaskSyncService,
    private val merger: TaskSyncMerger,
    private val accountEmail: String,
    private val serverUrl: String = "",
    private val clock: () -> Long = System::currentTimeMillis
) {
    data class Result(val merged: Int, val waiting: Int, val failedLists: Int)

    private val sync = db.taskSyncDao()

    suspend fun pull(): Result {
        var merged = 0
        var waiting = 0
        var failed = 0
        // Every list is read first; then what is in the lists is merged before what was deleted, so
        // a task moved between synced lists is followed, not cleared and added again (owner decision 49)
        val read = mutableListOf<Pair<TaskSyncList, RemoteChanges>>()
        for (list in sync.getListsFor(service.provider, accountEmail, serverUrl)) {
            try {
                read += list to service.changes(list.listId, list.pullCursor)
            } catch (e: TaskSyncServiceException.ListGone) {
                if (merger.listGone(list.listId) == TaskSyncMerger.Result.WAITS) waiting++
            } catch (e: TaskSyncServiceException) {
                throw e
            } catch (e: Exception) {
                failed++
                Log.w(TAG, "Pull of a list failed (${e.javaClass.simpleName}); next time")
            }
        }
        val waitsIn = HashMap<String, Int>()
        val failedLists = HashSet<String>()
        merger.goneInThisPull = read.flatMap { it.second.tasks }.filter { it.deleted }.mapTo(HashSet()) { it.listId to it.remoteId }
        try {
            for (deletedOnes in listOf(false, true)) for ((list, changes) in read) {
                if (list.listId in failedLists) continue
                try {
                    for (remote in changes.tasks) {
                        if (remote.deleted != deletedOnes) continue
                        when (merger.merge(remote)) {
                            TaskSyncMerger.Result.APPLIED -> merged++
                            TaskSyncMerger.Result.WAITS -> waitsIn[list.listId] = (waitsIn[list.listId] ?: 0) + 1
                            TaskSyncMerger.Result.UNCHANGED -> Unit
                        }
                    }
                } catch (e: TaskSyncServiceException) {
                    throw e
                } catch (e: Exception) {
                    failedLists += list.listId
                    failed++
                    Log.w(TAG, "Merge of a list failed (${e.javaClass.simpleName}); next time")
                }
            }
        } finally {
            merger.goneInThisPull = emptySet()
        }
        for ((list, changes) in read) {
            if (list.listId in failedLists) continue
            val listWaits = waitsIn[list.listId] ?: 0
            waiting += listWaits
            // A list's cursor moves on only when every change in it was merged
            if (listWaits == 0) sync.setPullCursor(service.provider, accountEmail, serverUrl, list.listId, changes.nextCursor)
            try {
                merged += dailyCheck(list)
            } catch (e: TaskSyncServiceException.ListGone) {
                if (merger.listGone(list.listId) == TaskSyncMerger.Result.WAITS) waiting++
            } catch (e: TaskSyncServiceException) {
                throw e
            } catch (e: Exception) {
                failed++
                Log.w(TAG, "Daily check of a list failed (${e.javaClass.simpleName}); next time")
            }
        }
        return Result(merged, waiting, failed)
    }

    /** Once a day, every id of the list, for deletions the service stopped reporting. */
    private suspend fun dailyCheck(list: TaskSyncList): Int {
        val now = clock()
        if (now - (list.lastFullCheckAt ?: 0L) < FULL_CHECK_EVERY_MS) return 0
        var merged = 0
        val links = sync.getLinksInList(service.provider, accountEmail, serverUrl, list.listId)
        val ids = service.allIds(list.listId)
        var checkWaits = false
        if (ids.isNotEmpty() || links.none { !it.keptOnTablet }) {
            for (link in links) {
                // Just sent: the service may not list it yet
                if (link.keptOnTablet || link.remoteId in ids || now - link.syncedAt < RECENT_MS) continue
                when (merger.gone(link)) {
                    TaskSyncMerger.Result.WAITS -> checkWaits = true
                    else -> merged++
                }
            }
        } else {
            Log.w(TAG, "Daily check: the service listed no tasks while ${links.size} are linked; not taken as deleted")
        }
        if (!checkWaits) sync.setLastFullCheck(service.provider, accountEmail, serverUrl, list.listId, now)
        return merged
    }

    companion object {
        private const val TAG = "TaskSyncPuller"
        const val FULL_CHECK_EVERY_MS = 24 * 60 * 60_000L
        private const val RECENT_MS = 10 * 60_000L
    }
}
