package com.boox.day.tasksync

import android.content.Context
import android.util.Log
import com.boox.day.BuildConfig
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.TaskSyncKept
import com.boox.day.data.db.TaskSyncLink
import com.boox.day.data.db.TaskSyncList
import com.boox.day.share.HandwrittenRows
import kotlinx.coroutines.CoroutineScope
import java.time.LocalDate

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
    private const val KEY_ACCOUNT = "account"
    private const val KEY_LAST_RUN = "last_run"
    private const val KEY_LAST_RUN_AT = "last_run_at"
    private const val KEY_LAST_SYNCED_AT = "last_synced_at"
    private const val KEY_NEEDS_RECONNECT = "needs_reconnect"

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
                    listId = "", remoteId = TaskSyncRules.TICKED_AT_TURN_ON + task.id, syncedAt = now, keptOnTablet = true,
                    keptReason = TaskSyncKept.TICKED_AT_TURN_ON
                )
            )
        }
        ticked.size
    }

    // ── The Task Sync card (plan step 2.6) ──────────────────────────────────────────────────

    /** The Google account task sync uses; changed only while it is off (owner decision 32). */
    fun account(context: Context): String? = prefs(context).getString(KEY_ACCOUNT, null)

    /**
     * Turns task sync on for [email] (the Tasks permission is already given): another account's
     * links and lists go, so its tasks count as never synced (owner decision 32); with no list yet,
     * Google's main list is ticked and gets new tasks (decision 30); ticked tasks from today on stay
     * on the tablet (decision 9). The first sync is the caller's to start ([TaskSyncRun]).
     * Throws what the service throws (offline, sign-in): then nothing changed.
     */
    internal suspend fun turnOn(context: Context, email: String, service: TaskSyncService, today: String = LocalDate.now().toString()) {
        val app = context.applicationContext
        val db = AppDatabase.getDatabase(app)
        val sync = db.taskSyncDao()
        val provider = service.provider
        val main = if (sync.getListsFor(provider, email, "").isEmpty()) service.mainList() else null
        db.runInTransaction<Unit> {
            sync.dropLinksOfOtherAccounts(provider, email, "")
            sync.dropListsOfOtherAccounts(provider, email, "")
            sync.dropDeletesOfOtherAccounts(provider, email, "")
            if (main != null) sync.insertList(TaskSyncList(provider, email, "", main.listId, main.title, isDefaultForNew = true))
        }
        keepTickedTasksOnTablet(db, provider, email, today = today)
        prefs(app).edit().putString(KEY_ACCOUNT, email).putBoolean(KEY_ON, true).putBoolean(KEY_NEEDS_RECONNECT, false).apply()
        start(app)
        Log.i(TAG, "Task sync turned on")
    }

    /** Off: nothing runs; links stay, so turning it on again resumes without doubles. */
    fun turnOff(context: Context) {
        setOn(context, false)
        synchronized(this) {
            watcher?.stop()
            watcher = null
        }
        TaskSyncTriggers.cancel()
    }

    /** Only while off (owner decision 32). */
    fun chooseAccount(context: Context, email: String) {
        if (!isOn(context)) prefs(context).edit().putString(KEY_ACCOUNT, email).apply()
    }

    /**
     * Ticks or unticks a list. The first ticked list gets new tasks; unticking the "New tasks go
     * to" list passes that on to another ticked one. An unticked list's tasks stay on the tablet and
     * stop syncing (owner decision 31: TaskSyncSender leaves them out); ticked again, they resume.
     */
    internal fun tickList(context: Context, provider: String, email: String, listId: String, title: String, ticked: Boolean) {
        val db = AppDatabase.getDatabase(context.applicationContext)
        val sync = db.taskSyncDao()
        db.runInTransaction<Unit> {
            if (ticked) {
                sync.insertList(TaskSyncList(provider, email, "", listId, title, isDefaultForNew = sync.getDefaultList(provider, email, "") == null))
            } else {
                sync.deleteList(provider, email, "", listId)
                if (sync.getDefaultList(provider, email, "") == null) {
                    sync.getListsFor(provider, email, "").firstOrNull()?.let { sync.setDefaultList(provider, email, "", it.listId) }
                }
            }
        }
    }

    internal fun newTasksTo(context: Context, provider: String, email: String, listId: String) {
        AppDatabase.getDatabase(context.applicationContext).taskSyncDao().setDefaultList(provider, email, "", listId)
    }

    // ── What happened, and what needs the user ──

    internal data class Status(
        val lastRun: TaskSyncRun.Result?,
        val lastSyncedAt: Long?,
        val needsReconnect: Boolean,
        val deletesWaiting: Int
    )

    internal fun status(context: Context): Status {
        val prefs = prefs(context)
        val waiting = if (isOn(context)) {
            AppDatabase.getDatabase(context.applicationContext).taskSyncDao()
                .countDeletesAfter(prefs.getLong(TaskSyncSender.KEY_DELETES_APPROVED_UP_TO, 0L))
        } else 0
        return Status(
            lastRun = prefs.getString(KEY_LAST_RUN, null)?.let { runCatching { TaskSyncRun.Result.valueOf(it) }.getOrNull() },
            lastSyncedAt = prefs.getLong(KEY_LAST_SYNCED_AT, 0L).takeIf { it > 0L },
            needsReconnect = isOn(context) && prefs.getBoolean(KEY_NEEDS_RECONNECT, false),
            deletesWaiting = waiting.takeIf { it > TaskSyncSender.MASS_DELETE_LIMIT } ?: 0
        )
    }

    /** For the Settings dot (ui/attention): why Preferences needs the user for task sync, or null. */
    fun attentionReason(context: Context): String? {
        if (!isOn(context)) return null
        val status = status(context)
        return when {
            status.needsReconnect -> "Task Sync needs you to sign in again"
            status.deletesWaiting > 0 -> "Task Sync is waiting for your OK to delete tasks"
            else -> null
        }
    }

    /** When the last run finished, whatever its result (0 = never); [TaskSyncTriggers] spaces runs by it. */
    internal fun lastRunAt(context: Context): Long = prefs(context).getLong(KEY_LAST_RUN_AT, 0L)

    internal fun recordRun(context: Context, result: TaskSyncRun.Result): TaskSyncRun.Result {
        val edit = prefs(context).edit().putString(KEY_LAST_RUN, result.name)
            .putLong(KEY_LAST_RUN_AT, System.currentTimeMillis())
            .putBoolean(KEY_NEEDS_RECONNECT, result == TaskSyncRun.Result.NEEDS_RECONNECT)
        if (result == TaskSyncRun.Result.DONE) edit.putLong(KEY_LAST_SYNCED_AT, System.currentTimeMillis())
        edit.apply()
        return result
    }

    /** "Delete in Google": the waiting deletes go at the next run. */
    fun approveWaitingDeletes(context: Context) {
        val db = AppDatabase.getDatabase(context.applicationContext)
        prefs(context).edit().putLong(TaskSyncSender.KEY_DELETES_APPROVED_UP_TO, db.taskSyncDao().lastDeleteId()).apply()
    }

    /** "Keep in Google": the waiting deletes are dropped; the copies stay in Google. */
    fun keepWaitingDeletesInService(context: Context) {
        val db = AppDatabase.getDatabase(context.applicationContext)
        db.taskSyncDao().dropDeletesAfter(prefs(context).getLong(TaskSyncSender.KEY_DELETES_APPROVED_UP_TO, 0L))
    }

    // ── Running ──

    /** Starts watching for changes when task sync is on; does nothing otherwise. */
    fun startIfOn(context: Context, scope: CoroutineScope) {
        appScope = scope  // kept for turning it on later in this session
        if (!isOn(context)) return
        start(context.applicationContext)
    }

    private fun start(app: Context) {
        val scope = appScope ?: return
        synchronized(this) {
            if (watcher != null) return
            watcher = TaskSyncWatcher(AppDatabase.getDatabase(app), scope, scanner(app), onQueued = { TaskSyncTriggers.afterEdit(app) })
                .also { it.start() }
        }
        Log.i(TAG, "Task sync watching for changes")
    }

    /** The app's own scope (BooxDayApp), for runs that must outlive the screen that asked; null in tests. */
    @Volatile
    internal var appScope: CoroutineScope? = null
        private set

    @Volatile
    private var sharedScanner: TaskSyncScanner? = null

    /** One scanner for the watcher and the runs: its handwriting and empty-row timers are shared. */
    internal fun scanner(context: Context): TaskSyncScanner = synchronized(this) {
        sharedScanner ?: run {
            val app = context.applicationContext
            val db = AppDatabase.getDatabase(app)
            TaskSyncScanner(db, inkReader(app, db)).also { sharedScanner = it }
        }
    }

    /** "Has this row ink?" on the saved Tasks geometry; null until Tasks has been shown once. */
    private fun inkReader(app: Context, db: AppDatabase): () -> (com.boox.day.data.db.Task) -> Boolean? = {
        val rows = HandwrittenRows(app, db.openHelper.readableDatabase)
        val check: (com.boox.day.data.db.Task) -> Boolean? = { task ->
            if (rows.known) rows.inRow(task.pageId, task.dayDate, task.rowIndex) else null
        }
        check
    }

    internal fun prefs(context: Context): android.content.SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
