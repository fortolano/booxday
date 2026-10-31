package com.boox.day.tasksync

import android.content.Context
import android.util.Log
import com.boox.day.BuildConfig
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.TaskSyncLink
import com.boox.day.share.HandwrittenRows
import kotlinx.coroutines.CoroutineScope

/**
 * Task sync's switch and start-up (Oct 2026). Off unless the user turns it on in Preferences
 * (owner decision 8, from the Google Tasks stage), and never in a build without
 * BuildConfig.TASK_SYNC_ENABLED (release builds until Google approves the Tasks permission).
 * The switch is device-only: its own preferences file, left out of Android backup, and not in
 * AppSettings, which backups export and restore.
 */
object TaskSync {
    private const val TAG = "TaskSync"
    /** Also named in res/xml/backup_rules.xml and data_extraction_rules.xml. */
    const val PREFS_NAME = "task_sync"
    private const val KEY_ON = "on"
    private const val KEY_FULL_DAYS = "full_days"

    @Volatile
    private var watcher: TaskSyncWatcher? = null

    fun isOn(context: Context): Boolean =
        BuildConfig.TASK_SYNC_ENABLED && prefs(context).getBoolean(KEY_ON, false)

    /** For the Preferences card (Google Tasks stage). */
    fun setOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ON, on).apply()
    }

    /**
     * Days a new task from the service couldn't be placed on: all 10 pages full (plan step 2.4).
     * The Task Sync card says so; the task comes once a row is free.
     */
    fun fullDays(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_FULL_DAYS, emptySet()).orEmpty().toSortedSet()

    internal fun setDayFull(context: Context, day: String, full: Boolean) {
        val days = fullDays(context)
        if ((day in days) == full) return
        prefs(context).edit().putStringSet(KEY_FULL_DAYS, if (full) days + day else days - day).apply()
    }

    /**
     * Turning task sync on for an account (owner decision 9, plan step 2.5), before the first sync:
     * tasks dated today or later that are already ticked stay on the tablet. Each gets a tablet-only
     * link ([TaskSyncRules.TICKED_AT_TURN_ON]), which moves with it and is never sent; unticked
     * later, it syncs like any unfinished task. Unfinished tasks dated today or later then go to
     * "New tasks go to" (the change watcher), and the service's unfinished ones come with the first
     * pull, which runs before the first send so the same task on both sides is linked, not doubled.
     * Older tasks are never looked at. Ticked after turn-on, a new task still goes (decision 29).
     * @return how many ticked tasks stay on the tablet
     */
    internal fun keepTickedTasksOnTablet(
        db: AppDatabase,
        provider: String,
        accountEmail: String,
        serverUrl: String = "",
        today: String,
        now: Long = System.currentTimeMillis()
    ): Int = db.runInTransaction<Int> {
        val sync = db.taskSyncDao()
        val ticked = sync.getUnlinkedTickedTasksFrom(today)
        for (task in ticked) {
            sync.insertLink(
                TaskSyncLink(
                    taskId = task.id, provider = provider, accountEmail = accountEmail, serverUrl = serverUrl,
                    listId = "", remoteId = TaskSyncRules.TICKED_AT_TURN_ON + task.id, syncedAt = now, keptOnTablet = true
                )
            )
        }
        ticked.size
    }

    /** Starts watching for changes when task sync is on; does nothing otherwise. */
    fun startIfOn(context: Context, scope: CoroutineScope) {
        if (!isOn(context)) return
        synchronized(this) {
            if (watcher != null) return
            val app = context.applicationContext
            val db = AppDatabase.getDatabase(app)
            watcher = TaskSyncWatcher(db, scope, TaskSyncScanner(db, inkReader(app, db))).also { it.start() }
        }
        Log.i(TAG, "Task sync watching for changes")
    }

    /** "Has this row ink?" on the saved Tasks geometry; null until Tasks has been shown once. */
    private fun inkReader(app: Context, db: AppDatabase): () -> (com.boox.day.data.db.Task) -> Boolean? = {
        val rows = HandwrittenRows(app, db.openHelper.readableDatabase)
        val check: (com.boox.day.data.db.Task) -> Boolean? = { task ->
            if (rows.known) rows.inRow(task.pageId, task.dayDate, task.rowIndex) else null
        }
        check
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
