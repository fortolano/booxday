package com.boox.day.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Task sync (v30, Oct 2026): BooxDay tasks kept in step with a task service (Google Tasks first,
 * CalDAV task lists later). Plan: C:\Users\fort\.claude\plans\reactive-wiggling-micali.md.
 *
 * A task row is a slot on a day's page, not a task: moving or sorting copies the content to
 * another row. So the link to the service's copy is its own table keyed by the row id, re-keyed
 * where content moves (transferTaskContent), and turned into a DELETE in the outbox where the
 * task is really cleared or its row deleted. Never inferred from a row that just lost its words.
 */
object TaskSyncProvider {
    const val GOOGLE_TASKS = "GOOGLE_TASKS"
    const val CALDAV = "CALDAV"
}

object TaskSyncOp {
    /** Send the task's current fields (create or update). One open entry per task. */
    const val UPSERT = "UPSERT"
    /** Delete the service's copy. Keyed by the remote identity, which outlives the row's link. */
    const val DELETE = "DELETE"
}

/**
 * One row per synced task. No foreign key to `task` on purpose: a restore empties `task`, and a
 * cascade would delete links the restore must handle itself (ImportService clears them).
 * Backed up, so a restore on a new tablet and reconnecting the same account doesn't duplicate tasks.
 */
@Entity(
    tableName = "task_sync_link",
    indices = [Index(value = ["provider", "accountEmail", "serverUrl", "listId", "remoteId"], unique = true)]
)
data class TaskSyncLink(
    @PrimaryKey val taskId: String,
    val provider: String,
    val accountEmail: String,
    @ColumnInfo(defaultValue = "") val serverUrl: String = "",
    val listId: String,
    val remoteId: String,
    /** Google `updated` or the CalDAV etag of the version last seen. */
    val remoteVersion: String? = null,
    // Snapshot of what was last synced, for field-by-field merging
    val syncedTitle: String? = null,
    val syncedCompleted: Boolean = false,
    val syncedDueDay: String? = null,
    val syncedParentRemoteId: String? = null,
    val contentHash: String? = null,
    /** The service's copy has no date (owner decision 1): it stays undated until the user sets one. */
    val remoteUndated: Boolean = false,
    /** When the row lost its words without being cleared (recognition hiccup, Undo of a move). */
    val orphanedAt: Long? = null,
    val syncedAt: Long
)

/** Changes waiting to be sent. Device-only (not backed up). */
@Entity(
    tableName = "task_sync_outbox",
    indices = [Index(value = ["taskId"]), Index(value = ["nextAttemptAt"])]
)
data class TaskSyncOutboxEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [TaskSyncOp] */
    val op: String,
    val taskId: String,
    // DELETE: the remote copy to delete, copied from the link that was retired
    val provider: String? = null,
    val accountEmail: String? = null,
    val serverUrl: String? = null,
    val listId: String? = null,
    val remoteId: String? = null,
    val createdAt: Long,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0,
    val lastError: String? = null
)

/** The lists the user ticked in the Task Sync window. Device-only (not backed up). */
@Entity(
    tableName = "task_sync_list",
    primaryKeys = ["provider", "accountEmail", "serverUrl", "listId"]
)
data class TaskSyncList(
    val provider: String,
    val accountEmail: String,
    @ColumnInfo(defaultValue = "") val serverUrl: String = "",
    val listId: String,
    val title: String,
    /** The list tasks written in BooxDay go to ("New tasks go to"). */
    val isDefaultForNew: Boolean = false,
    /** Where the next pull starts (Google `updatedMin`, CalDAV sync token); null = pull everything. */
    val pullCursor: String? = null,
    val lastFullCheckAt: Long? = null
)

@Dao
interface TaskSyncDao {
    @Query("SELECT * FROM task_sync_link ORDER BY taskId")
    fun getAllLinks(): List<TaskSyncLink>

    @Query("SELECT * FROM task_sync_link WHERE taskId = :taskId")
    fun getLink(taskId: String): TaskSyncLink?

    /** Up to SQLITE_ID_CHUNK_SIZE ids (one page sort is at most a few dozen). */
    @Query("SELECT * FROM task_sync_link WHERE taskId IN (:taskIds)")
    fun getLinksFor(taskIds: Collection<String>): List<TaskSyncLink>

    /**
     * What the change watcher looks at: every linked row, and the unlinked rows dated [today] or
     * later that have words (owner decision 9: older tasks stay on the tablet).
     */
    @Query(
        "SELECT * FROM task WHERE id IN (SELECT taskId FROM task_sync_link) OR (dayDate >= :today AND (" +
            "(typedText IS NOT NULL AND trim(typedText) != '') OR (convertedText IS NOT NULL AND trim(convertedText) != '') " +
            "OR (recognizedText IS NOT NULL AND trim(recognizedText) != '')))"
    )
    fun getRowsToScan(today: String): List<Task>

    /** One open UPSERT per task: a second change before it is sent sends the latest state anyway. */
    @Query(
        "INSERT INTO task_sync_outbox (op, taskId, createdAt, attempts, nextAttemptAt) SELECT 'UPSERT', :taskId, :now, 0, 0 " +
            "WHERE NOT EXISTS (SELECT 1 FROM task_sync_outbox WHERE op = 'UPSERT' AND taskId = :taskId)"
    )
    fun queueUpsert(taskId: String, now: Long)

    /** The row lost its words without being cleared; kept, with no change sent (owner decision 21). */
    @Query("UPDATE task_sync_link SET orphanedAt = :now WHERE taskId = :taskId AND orphanedAt IS NULL")
    fun markOrphaned(taskId: String, now: Long)

    @Query("UPDATE task_sync_link SET orphanedAt = NULL WHERE taskId = :taskId")
    fun clearOrphaned(taskId: String)

    // ── Sending (tasksync/TaskSyncSender) ───────────────────────────────────────────────────

    @Query("SELECT * FROM task_sync_outbox WHERE nextAttemptAt <= :now ORDER BY id")
    fun getDueOutbox(now: Long): List<TaskSyncOutboxEntry>

    @Query("DELETE FROM task_sync_outbox WHERE id = :id")
    fun deleteOutboxEntry(id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM task_sync_outbox WHERE id = :id)")
    fun hasOutboxEntry(id: Long): Boolean

    @Query("UPDATE task_sync_outbox SET attempts = :attempts, nextAttemptAt = :nextAttemptAt, lastError = :error WHERE id = :id")
    fun markOutboxFailed(id: Long, attempts: Int, nextAttemptAt: Long, error: String?)

    @Query("SELECT COUNT(*) FROM task_sync_outbox WHERE op = 'DELETE' AND id > :afterId")
    fun countDeletesAfter(afterId: Long): Int

    @Query("SELECT COALESCE(MAX(id), 0) FROM task_sync_outbox WHERE op = 'DELETE'")
    fun lastDeleteId(): Long

    @Query("DELETE FROM task_sync_outbox WHERE op = 'DELETE' AND id > :afterId")
    fun dropDeletesAfter(afterId: Long)

    /** An orphaned link whose task was last sent with these words (match before create). */
    @Query(
        "SELECT * FROM task_sync_link WHERE provider = :provider AND accountEmail = :accountEmail AND serverUrl = :serverUrl " +
            "AND orphanedAt IS NOT NULL AND syncedTitle = :title ORDER BY orphanedAt DESC LIMIT 1"
    )
    fun findOrphan(provider: String, accountEmail: String, serverUrl: String, title: String): TaskSyncLink?

    @Query(
        "SELECT * FROM task_sync_list WHERE provider = :provider AND accountEmail = :accountEmail AND serverUrl = :serverUrl " +
            "AND isDefaultForNew = 1 LIMIT 1"
    )
    fun getDefaultList(provider: String, accountEmail: String, serverUrl: String): TaskSyncList?

    /** The tasks whose copies were last sent as subtasks of [parentRemoteId]. */
    @Query(
        "SELECT * FROM task_sync_link WHERE provider = :provider AND accountEmail = :accountEmail AND serverUrl = :serverUrl " +
            "AND listId = :listId AND syncedParentRemoteId = :parentRemoteId ORDER BY taskId"
    )
    fun getChildLinks(provider: String, accountEmail: String, serverUrl: String, listId: String, parentRemoteId: String): List<TaskSyncLink>

    /** After a send: what the service now holds. Updated in place (no REPLACE: the remote id is a unique index). */
    @Query(
        "UPDATE task_sync_link SET listId = :listId, remoteId = :remoteId, remoteVersion = :remoteVersion, syncedTitle = :title, " +
            "syncedCompleted = :completed, syncedDueDay = :dueDay, syncedParentRemoteId = :parentRemoteId, contentHash = :contentHash, " +
            "remoteUndated = :remoteUndated, orphanedAt = NULL, syncedAt = :now WHERE taskId = :taskId"
    )
    fun updateSyncedLink(
        taskId: String, listId: String, remoteId: String, remoteVersion: String?, title: String, completed: Boolean,
        dueDay: String?, parentRemoteId: String?, contentHash: String, remoteUndated: Boolean, now: Long
    )

    @Insert
    fun insertOutboxEntry(entry: TaskSyncOutboxEntry): Long

    /** A DELETE of [link]'s remote copy (the link itself is already off its row). */
    fun queueDelete(link: TaskSyncLink, now: Long) {
        insertOutboxEntry(
            TaskSyncOutboxEntry(
                op = TaskSyncOp.DELETE,
                taskId = link.taskId,
                provider = link.provider,
                accountEmail = link.accountEmail,
                serverUrl = link.serverUrl,
                listId = link.listId,
                remoteId = link.remoteId,
                createdAt = now
            )
        )
    }

    /** Restore only (after [clearLinks]). A clash on the remote identity fails loudly. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertLink(link: TaskSyncLink)

    @Query("DELETE FROM task_sync_link")
    fun clearLinks()

    @Query("SELECT * FROM task_sync_outbox ORDER BY id")
    fun getOutbox(): List<TaskSyncOutboxEntry>

    @Query("DELETE FROM task_sync_outbox")
    fun clearOutbox()

    @Query("SELECT * FROM task_sync_list ORDER BY title")
    fun getLists(): List<TaskSyncList>

    /** After a restore the device's cursors are newer than the restored tasks: pull everything again. */
    @Query("UPDATE task_sync_list SET pullCursor = NULL, lastFullCheckAt = NULL")
    fun resetPullCursors()
}

/**
 * The SQL Room doesn't model: a trigger that turns a linked row's deletion into an outbox DELETE,
 * in the same statement. It covers every way rows go (page deletes, "Clear This Day's Tasks",
 * pages cascading away). Created by MIGRATION_29_30 and, for fresh installs, by [CALLBACK] on
 * open (IF NOT EXISTS, like the FTS4 triggers). REPLACE doesn't fire it (SQLite runs delete
 * triggers on REPLACE only with recursive triggers on), and no linked row is ever REPLACEd:
 * inserts go to empty rows.
 */
object TaskSyncSchema {
    const val ROW_DELETED_TRIGGER = "task_sync_row_deleted"

    fun createTriggers(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS `$ROW_DELETED_TRIGGER`
            AFTER DELETE ON `task`
            WHEN EXISTS (SELECT 1 FROM `task_sync_link` WHERE `taskId` = old.`id`)
            BEGIN
                INSERT INTO `task_sync_outbox` (`op`, `taskId`, `provider`, `accountEmail`, `serverUrl`, `listId`, `remoteId`, `createdAt`, `attempts`, `nextAttemptAt`)
                    SELECT '${TaskSyncOp.DELETE}', `taskId`, `provider`, `accountEmail`, `serverUrl`, `listId`, `remoteId`,
                        CAST(strftime('%s', 'now') AS INTEGER) * 1000, 0, 0
                    FROM `task_sync_link` WHERE `taskId` = old.`id`;
                DELETE FROM `task_sync_outbox` WHERE `op` = '${TaskSyncOp.UPSERT}' AND `taskId` = old.`id`;
                DELETE FROM `task_sync_link` WHERE `taskId` = old.`id`;
            END
            """.trimIndent()
        )
    }

    val CALLBACK = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            createTriggers(db)
        }
    }
}
