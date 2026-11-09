package com.boox.day.tasksync

import android.content.Context
import android.util.Log
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.editor.DrawCanvas
import com.boox.day.ocr.TaskTextExtractor
import com.boox.day.sync.SingleFlight
import com.boox.day.sync.workerAppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * One task sync run (Oct 2026). Plan: C:\Users\fort\.claude\plans\reactive-wiggling-micali.md, steps
 * 2.6-2.7. Looks for tablet changes, then reads the service (so a copy made by an earlier send whose
 * reply was lost is linked, not made twice), then sends. One run at a time: a second caller waits
 * for the running one. What happened is kept for the Task Sync card ([TaskSync.status]).
 */
internal object TaskSyncRun {
    private const val TAG = "TaskSyncRun"
    /** Ink this recent may not be read yet: its page is saved 2 s after the pen rests. */
    private const val FRESH_INK_MS = 5_000L
    /** The longest a Sync tap waits for that page's words. */
    private const val WORDS_WAIT_MS = 2_000L

    enum class Result { DONE, OFF, NO_LIST, OFFLINE, BUSY, NEEDS_RECONNECT, PAUSED_FOR_DELETES, FAILED }

    private val flight = SingleFlight<String, Result>()
    /** A run and a list move never overlap (both change links). */
    private val work = Mutex()

    /**
     * [changedAt]: when the change that asked for this run was made. A run that started before then
     * isn't joined (it may be past its look for tablet changes): this one waits for it and runs again.
     * [handwritingNow]: the user tapped Sync, so every handwritten task whose words are read goes
     * now (owner, 31 Oct 2026); one read later follows through the change watcher.
     */
    suspend fun run(context: Context, changedAt: Long = 0L, handwritingNow: Boolean = false): Result {
        val app = context.applicationContext
        if (handwritingNow) readFreshHandwriting(app)
        return flight.run("task-sync", startedSince = changedAt) { withContext(Dispatchers.IO) { work.withLock { runNow(app, handwritingNow) } } }
    }

    /**
     * The task card's List (owner decisions 47, 53, 57): moves the task to [listId] at once, its
     * subtasks along; a task not in Google yet goes there now. Returns what to tell the user.
     */
    suspend fun moveToList(context: Context, taskId: String, listId: String): String {
        val app = context.applicationContext
        return withContext(Dispatchers.IO) { work.withLock { moveNow(app, taskId, listId) } }
    }

    private suspend fun moveNow(app: Context, taskId: String, listId: String): String {
        val target = TaskSync.target(app)
        if (!TaskSync.isOn(app) || target == null || workerAppSettings(app).syncMode != AppSettings.SyncMode.HYBRID) {
            return "Task Sync is off, so the task stays where it is."
        }
        val db = AppDatabase.getDatabase(app)
        val provider = target.provider
        val email = target.accountEmail
        val server = target.serverUrl
        val lists = db.taskSyncDao().getListsFor(provider, email, server)
        val name = lists.firstOrNull { it.listId == listId }?.title ?: return "That list isn't synced any more."
        val from = db.taskSyncDao().getLink(taskId)?.listId?.let { id -> lists.firstOrNull { it.listId == id }?.title }
        val stays = from?.let { "It stays in $it." } ?: "It stays where it is."
        return try {
            val service = target.service(app)
            if (db.taskSyncDao().getLink(taskId) == null) {
                val merger = TaskSyncMerger(db, RepositoryTabletTasks(app), provider, email, server, service = service)
                val sent = TaskSyncSender(db, service, merger, email, server, prefs = TaskSync.prefs(app)).sendNewTo(taskId, listId)
                if (sent) "Moved to $name." else "The words of this task aren't read yet. Try again in a moment."
            } else when (TaskSyncListMover(db, service, email, server).move(taskId, listId)) {
                TaskSyncListMover.Outcome.MOVED, TaskSyncListMover.Outcome.ALREADY_THERE -> "Moved to $name."
                TaskSyncListMover.Outcome.REFUSED -> "${target.shortName} doesn't move repeating tasks to another list. $stays"
                TaskSyncListMover.Outcome.SUBTASK -> "A subtask moves with its main task."
                TaskSyncListMover.Outcome.NOT_SYNCED -> "This task isn't synced with ${target.serviceName}."
            }
        } catch (e: TaskSyncServiceException.Offline) {
            "No connection to ${target.shortName}. $stays"
        } catch (e: TaskSyncServiceException.SignInNeeded) {
            if (target.isCalDav) "${target.shortName} refused the password. Sign in again in Settings → Calendar Accounts. $stays"
            else "Task Sync needs you to sign in again (Settings → Preferences → Task Sync). $stays"
        } catch (e: TaskSyncServiceException.Busy) {
            "${target.shortName} is busy. Try again in a moment. $stays"
        } catch (e: Exception) {
            Log.w(TAG, "Move to another list failed (${e.javaClass.simpleName})")  // never the message: it can hold words
            "The task couldn't be moved. Try again in a moment. $stays"
        }
    }

    /**
     * Sync tapped right after writing: the page is read now instead of after its save (2 s) and
     * recognition (about 0.7 s), so the new task goes in this run (owner, 31 Oct 2026).
     */
    private suspend fun readFreshHandwriting(app: Context) {
        if (!DrawCanvas.isStylusActiveWithin(FRESH_INK_MS)) return  // later: saved and read already
        val pageId = DrawCanvas.activeCanvasPageId.value ?: return
        val extractor = TaskTextExtractor.getInstanceOrNull() ?: return
        extractor.extractAndWait(pageId, app.resources.displayMetrics.density, WORDS_WAIT_MS)
    }

    private suspend fun runNow(app: Context, handwritingNow: Boolean): Result {
        if (!TaskSync.isOn(app)) return Result.OFF
        // Cloud Sync only (plan step 2.6): Private Mode keeps everything on the tablet
        if (workerAppSettings(app).syncMode != AppSettings.SyncMode.HYBRID) return Result.OFF
        val target = TaskSync.target(app) ?: return Result.OFF
        val email = target.accountEmail
        val server = target.serverUrl
        val db = AppDatabase.getDatabase(app)
        // The account was removed in Calendar Accounts: sync stops and asks for the user (plan 2.1).
        // Android may still hold a Google account, so Google would go on answering without this check.
        val accountGone = db.connectedAccountDao().getByEmail(target.accountProvider.key, server, email) == null
        TaskSync.recordAccountGone(app, accountGone)
        if (accountGone) return TaskSync.recordRun(app, Result.NEEDS_RECONNECT)
        val provider = target.provider
        if (db.taskSyncDao().getListsFor(provider, email, server).isEmpty()) return TaskSync.recordRun(app, Result.NO_LIST)

        TaskSync.scanner(app).scan(System.currentTimeMillis(), LocalDate.now().toString(), wordsSettled = handwritingNow)
        val result = try {
            val service = target.service(app)
            val merger = TaskSyncMerger(db, RepositoryTabletTasks(app), provider, email, server, service = service)
            val pulled = TaskSyncPuller(db, service, merger, email, server).pull()
            val sent = TaskSyncSender(db, service, merger, email, server, prefs = TaskSync.prefs(app)).sendDue()
            Log.i(TAG, "Merged ${pulled.merged}, ${pulled.waiting} waiting; send: ${sent.javaClass.simpleName}")
            when (sent) {
                is TaskSyncSender.Outcome.Sent -> if (pulled.failedLists > 0) Result.FAILED else Result.DONE
                is TaskSyncSender.Outcome.PausedForDeletes -> Result.PAUSED_FOR_DELETES
                TaskSyncSender.Outcome.NeedsReconnect -> Result.NEEDS_RECONNECT
                TaskSyncSender.Outcome.Offline -> Result.OFFLINE
                is TaskSyncSender.Outcome.Busy -> Result.BUSY
            }
        } catch (e: TaskSyncServiceException.SignInNeeded) {
            Result.NEEDS_RECONNECT
        } catch (e: TaskSyncServiceException.Offline) {
            Result.OFFLINE
        } catch (e: TaskSyncServiceException.Busy) {
            Result.BUSY
        } catch (e: Exception) {
            Log.w(TAG, "Task sync run failed (${e.javaClass.simpleName})")  // never the message: it can hold words
            Result.FAILED
        }
        return TaskSync.recordRun(app, result)
    }
}
