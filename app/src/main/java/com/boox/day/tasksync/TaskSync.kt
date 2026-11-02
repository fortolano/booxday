package com.boox.day.tasksync

import android.content.Context
import android.util.Log
import com.boox.day.BuildConfig
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.TaskSyncKept
import com.boox.day.data.db.TaskSyncLink
import com.boox.day.data.db.TaskSyncList
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.share.HandwrittenRows
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
    private const val KEY_ACCOUNT_GONE = "account_gone"
    /** Debug builds start with this list instead of the main one (owner decision 42). */
    internal const val DEBUG_TEST_LIST = "BooxDay test"
    private const val KEY_LAST_SYNCED_AT = "last_synced_at"
    private const val KEY_NEEDS_RECONNECT = "needs_reconnect"
    // The service (plan step 4.0): "google" (also when missing: set before CalDAV tasks) or "caldav"
    private const val KEY_SERVICE = "service"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_ACCOUNT_ID = "account_id"
    private const val KEY_SERVICE_NAME = "service_name"

    @Volatile
    private var watcher: TaskSyncWatcher? = null

    private val changes = MutableStateFlow(0L)

    /**
     * Changes whenever what [attentionReason] reads may have changed (a run's result, turning it on
     * or off, deletes approved or kept), so the Settings menu updates at once (Nov 2026; before, it
     * waited for BooxDay to come back to the front).
     */
    val statusChanges: StateFlow<Long> = changes

    private fun statusChanged() = changes.update { it + 1 }

    fun isOn(context: Context): Boolean =
        BuildConfig.TASK_SYNC_ENABLED && prefs(context).getBoolean(KEY_ON, false)

    /** For the Preferences card (Google Tasks stage). */
    fun setOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ON, on).apply()
    }

    /**
     * The task card's List line (owner decisions 47, 53, 56): only with Task Sync on and 2+ synced lists;
     * not for a task kept on the tablet only. A subtask shows its main task's list, not changeable.
     */
    internal fun cardList(context: Context, task: com.boox.day.data.db.Task): com.boox.day.editor.ui.TaskCardList? {
        if (!BuildConfig.TASK_SYNC_ENABLED || !isOn(context)) return null
        val target = target(context) ?: return null
        val sync = AppDatabase.getDatabase(context.applicationContext).taskSyncDao()
        val lists = sync.getListsFor(target.provider, target.accountEmail, target.serverUrl)
        if (lists.size < 2) return null
        val link = sync.getLink(task.id)
        if (link?.keptOnTablet == true) return null
        val parentLink = task.parentTaskId?.let { sync.getLink(it) }
        val current = link?.listId ?: parentLink?.listId ?: lists.firstOrNull { it.isDefaultForNew }?.listId
        val subtask = task.parentTaskId != null || link?.remoteParentId != null
        return com.boox.day.editor.ui.TaskCardList(
            lists = lists.map { it.listId to it.title },
            currentId = current,
            fixedReason = if (subtask) "with its main task" else null,
            serviceName = target.serviceName
        )
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

    /** The account task sync uses (its Google address or CalDAV sign-in name). */
    fun account(context: Context): String? = target(context)?.accountEmail

    /** The service and account task sync uses; changed only while it is off (owner decision 32). */
    internal fun target(context: Context): TaskSyncTarget? {
        val prefs = prefs(context)
        val email = prefs.getString(KEY_ACCOUNT, null) ?: return null
        if (prefs.getString(KEY_SERVICE, SERVICE_GOOGLE) != SERVICE_CALDAV) return TaskSyncTarget.google(email)
        return TaskSyncTarget(
            provider = TaskSyncProvider.CALDAV,
            accountEmail = email,
            serverUrl = prefs.getString(KEY_SERVER_URL, null).orEmpty(),
            accountId = prefs.getString(KEY_ACCOUNT_ID, null) ?: return null,
            serviceName = prefs.getString(KEY_SERVICE_NAME, null) ?: "CalDAV"
        )
    }

    private const val SERVICE_GOOGLE = "google"
    private const val SERVICE_CALDAV = "caldav"

    private fun save(edit: android.content.SharedPreferences.Editor, target: TaskSyncTarget) = edit
        .putString(KEY_SERVICE, if (target.isGoogle) SERVICE_GOOGLE else SERVICE_CALDAV)
        .putString(KEY_ACCOUNT, target.accountEmail)
        .putString(KEY_SERVER_URL, target.serverUrl)
        .putString(KEY_ACCOUNT_ID, target.accountId)
        .putString(KEY_SERVICE_NAME, target.serviceName)

    /**
     * Turns task sync on for [target] (Google's Tasks permission is already given): another account's
     * or service's links and lists go, so its tasks count as never synced (owner decision 32); with no
     * list yet, the service's main list is ticked and gets new tasks (decision 30); ticked tasks from
     * today on stay on the tablet (decision 9). The first sync is the caller's to start ([TaskSyncRun]).
     * [testList]: debug builds tick only the list with this name instead (owner decision 42, so the
     * owner's own tablet test never fills their real lists), or none when it doesn't exist.
     * Throws what the service throws (offline, sign-in): then nothing changed.
     */
    internal suspend fun turnOn(
        context: Context,
        target: TaskSyncTarget,
        service: TaskSyncService,
        today: String = LocalDate.now().toString(),
        testList: String? = if (BuildConfig.DEBUG) DEBUG_TEST_LIST else null
    ) {
        val app = context.applicationContext
        val db = AppDatabase.getDatabase(app)
        val sync = db.taskSyncDao()
        val provider = service.provider
        val email = target.accountEmail
        val server = target.serverUrl
        val main = when {
            sync.getListsFor(provider, email, server).isNotEmpty() -> null
            testList != null -> service.lists().firstOrNull { it.title == testList }
            else -> service.mainList()
        }
        db.runInTransaction<Unit> {
            sync.dropLinksOfOtherAccounts(provider, email, server)
            sync.dropListsOfOtherAccounts(provider, email, server)
            sync.dropDeletesOfOtherAccounts(provider, email, server)
            if (main != null) sync.insertList(TaskSyncList(provider, email, server, main.listId, main.title, isDefaultForNew = true))
        }
        keepTickedTasksOnTablet(db, provider, email, serverUrl = server, today = today)
        save(prefs(app).edit(), target).putBoolean(KEY_ON, true).putBoolean(KEY_NEEDS_RECONNECT, false).apply()
        statusChanged()
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
        statusChanged()
    }

    /** Only while off (owner decision 32): Google Tasks or a CalDAV account. */
    internal fun choose(context: Context, target: TaskSyncTarget) {
        if (!isOn(context)) save(prefs(context).edit(), target).apply()
    }

    /**
     * Ticks or unticks a list. The first ticked list gets new tasks; unticking the "New tasks go
     * to" list passes that on to another ticked one. An unticked list's tasks stay on the tablet and
     * stop syncing (owner decision 31: TaskSyncSender leaves them out); ticked again, they resume.
     */
    internal fun tickList(
        context: Context, provider: String, email: String, listId: String, title: String, ticked: Boolean, serverUrl: String = ""
    ) {
        val db = AppDatabase.getDatabase(context.applicationContext)
        val sync = db.taskSyncDao()
        db.runInTransaction<Unit> {
            if (ticked) {
                sync.insertList(
                    TaskSyncList(provider, email, serverUrl, listId, title, isDefaultForNew = sync.getDefaultList(provider, email, serverUrl) == null)
                )
            } else {
                sync.deleteList(provider, email, serverUrl, listId)
                if (sync.getDefaultList(provider, email, serverUrl) == null) {
                    sync.getListsFor(provider, email, serverUrl).firstOrNull()?.let { sync.setDefaultList(provider, email, serverUrl, it.listId) }
                }
            }
        }
    }

    internal fun newTasksTo(context: Context, provider: String, email: String, listId: String, serverUrl: String = "") {
        AppDatabase.getDatabase(context.applicationContext).taskSyncDao().setDefaultList(provider, email, serverUrl, listId)
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
            status.needsReconnect && prefs(context).getBoolean(KEY_ACCOUNT_GONE, false) ->
                "Task Sync's ${target(context)?.shortName ?: "Google"} account isn't connected"
            status.needsReconnect -> "Task Sync needs you to sign in again"
            status.deletesWaiting > 0 -> "Task Sync is waiting for your OK to delete tasks"
            else -> null
        }
    }

    /** The account was removed in Calendar Accounts (TaskSyncRun checks at every run; owner decision 41). */
    internal fun recordAccountGone(context: Context, gone: Boolean) {
        prefs(context).edit().putBoolean(KEY_ACCOUNT_GONE, gone).apply()
        statusChanged()
    }

    /** When the last run finished, whatever its result (0 = never); [TaskSyncTriggers] spaces runs by it. */
    internal fun lastRunAt(context: Context): Long = prefs(context).getLong(KEY_LAST_RUN_AT, 0L)

    internal fun recordRun(context: Context, result: TaskSyncRun.Result): TaskSyncRun.Result {
        val edit = prefs(context).edit().putString(KEY_LAST_RUN, result.name)
            .putLong(KEY_LAST_RUN_AT, System.currentTimeMillis())
            .putBoolean(KEY_NEEDS_RECONNECT, result == TaskSyncRun.Result.NEEDS_RECONNECT)
        if (result == TaskSyncRun.Result.DONE) edit.putLong(KEY_LAST_SYNCED_AT, System.currentTimeMillis())
        edit.apply()
        statusChanged()
        return result
    }

    /** "Delete in Google": the waiting deletes go at the next run. */
    fun approveWaitingDeletes(context: Context) {
        val db = AppDatabase.getDatabase(context.applicationContext)
        prefs(context).edit().putLong(TaskSyncSender.KEY_DELETES_APPROVED_UP_TO, db.taskSyncDao().lastDeleteId()).apply()
        statusChanged()
    }

    /** "Keep in Google": the waiting deletes are dropped; the copies stay in Google. */
    fun keepWaitingDeletesInService(context: Context) {
        val db = AppDatabase.getDatabase(context.applicationContext)
        db.taskSyncDao().dropDeletesAfter(prefs(context).getLong(TaskSyncSender.KEY_DELETES_APPROVED_UP_TO, 0L))
        statusChanged()
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
            // Priority and reminder count as changes where the service carries them (a CalDAV list)
            TaskSyncScanner(db, extras = { target(app)?.isGoogle == false }, inkReader = inkReader(app, db)).also { sharedScanner = it }
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
