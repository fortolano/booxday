package com.boox.day.tasksync

import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Task

/**
 * Finds what changed since the last sync and queues it (Oct 2026). Looks at every linked row and
 * at the unlinked rows dated today or later that have words:
 * - a new or changed task (its synced fields' hash differs from the link's) is queued as an UPSERT;
 *   handwriting only once its recognized words have stayed the same for a minute;
 * - a linked row that lost its words but still has ink (a recognition hiccup, Undo of a move) is
 *   marked orphaned and nothing is sent; it can be re-linked to the task when it turns up again;
 * - a linked row with no words and no ink for two minutes (the task was erased) has its copy
 *   deleted, like Clear (owner decision 21). If the ink can't be checked, nothing is deleted.
 * Clears, moves and row deletes are handled where they happen (TaskDao, the v30 trigger).
 *
 * [inkReader] gives a fresh "has this row ink?" check per scan (true, false, or null = can't tell).
 * The timers live in memory: after a restart they start again, which only delays.
 */
internal class TaskSyncScanner(
    private val db: AppDatabase,
    private val inkReader: () -> (Task) -> Boolean?
) {
    data class Result(val queued: Int, val deleted: Int, val rescanAt: Long?)

    private val handwritingSince = HashMap<String, Pair<String, Long>>()  // row -> (words, first seen)
    private val emptySince = HashMap<String, Long>()

    @Synchronized
    fun scan(now: Long, today: String): Result = db.runInTransaction<Result> {
        val sync = db.taskSyncDao()
        val tasks = db.taskDao()
        val links = sync.getAllLinks().associateBy { it.taskId }
        val hasInk = inkReader()
        var queued = 0
        var deleted = 0
        var rescanAt: Long? = null
        fun rescanBy(at: Long) {
            rescanAt = rescanAt?.let { minOf(it, at) } ?: at
        }

        val rows = sync.getRowsToScan(today)
        for (row in rows) {
            val link = links[row.id]
            val text = TaskSyncRules.textToSend(row)

            if (text == null) {
                handwritingSince.remove(row.id)
                if (link == null) continue
                sync.markOrphaned(row.id, now)
                if (hasInk(row) == false) {
                    val since = emptySince.getOrPut(row.id) { now }
                    if (now - since >= TaskSyncRules.EMPTY_ROW_DELETE_MS) {
                        tasks.retireSyncLink(row.id)
                        emptySince.remove(row.id)
                        deleted++
                    } else {
                        rescanBy(since + TaskSyncRules.EMPTY_ROW_DELETE_MS)
                    }
                } else {
                    emptySince.remove(row.id)  // ink is there (or can't tell): never delete on a guess
                }
                continue
            }

            emptySince.remove(row.id)
            if (link?.orphanedAt != null) sync.clearOrphaned(row.id)

            if (TaskSyncRules.isHandwrittenOnly(row)) {
                val seen = handwritingSince[row.id]?.takeIf { it.first == text }
                    ?: (text to now).also { handwritingSince[row.id] = it }
                if (now - seen.second < TaskSyncRules.HANDWRITING_STABLE_MS) {
                    rescanBy(seen.second + TaskSyncRules.HANDWRITING_STABLE_MS)
                    continue
                }
            } else {
                handwritingSince.remove(row.id)
            }

            val parentRemoteId = row.parentTaskId?.let { links[it]?.remoteId }
            if (link == null || TaskSyncRules.contentHash(row, parentRemoteId) != link.contentHash) {
                sync.queueUpsert(row.id, now)
                queued++
            }
        }

        val scanned = rows.mapTo(HashSet()) { it.id }
        handwritingSince.keys.retainAll(scanned)
        emptySince.keys.retainAll(scanned)
        Result(queued, deleted, rescanAt)
    }
}
