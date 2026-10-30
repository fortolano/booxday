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
