package com.boox.day.tasksync

import android.content.Context
import android.util.Log
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.data.model.ProviderId
import com.boox.day.editor.DrawCanvas
import com.boox.day.ocr.TaskTextExtractor
import com.boox.day.sync.SingleFlight
import com.boox.day.sync.workerAppSettings
import com.boox.day.tasksync.google.GoogleTasksProvider
import kotlinx.coroutines.Dispatchers
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

    /**
     * [changedAt]: when the change that asked for this run was made. A run that started before then
     * isn't joined (it may be past its look for tablet changes): this one waits for it and runs again.
     * [handwritingNow]: the user tapped Sync, so every handwritten task whose words are read goes
     * now (owner, 31 Oct 2026); one read later follows through the change watcher.
     */
    suspend fun run(context: Context, changedAt: Long = 0L, handwritingNow: Boolean = false): Result {
        val app = context.applicationContext
        if (handwritingNow) readFreshHandwriting(app)
        return flight.run("task-sync", startedSince = changedAt) { withContext(Dispatchers.IO) { runNow(app, handwritingNow) } }
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
        val email = TaskSync.account(app) ?: return Result.OFF
        val db = AppDatabase.getDatabase(app)
        // The account was removed in Calendar Accounts: sync stops and asks for the user (plan 2.1).
        // Android may still hold the account, so Google would go on answering without this check.
        val accountGone = db.connectedAccountDao().getByEmail(ProviderId.GOOGLE.key, "", email) == null
        TaskSync.recordAccountGone(app, accountGone)
        if (accountGone) return TaskSync.recordRun(app, Result.NEEDS_RECONNECT)
        val provider = TaskSyncProvider.GOOGLE_TASKS
        if (db.taskSyncDao().getListsFor(provider, email, "").isEmpty()) return TaskSync.recordRun(app, Result.NO_LIST)

        TaskSync.scanner(app).scan(System.currentTimeMillis(), LocalDate.now().toString(), wordsSettled = handwritingNow)
        val service = GoogleTasksProvider.create(app, email)
        val merger = TaskSyncMerger(db, RepositoryTabletTasks(app), provider, email, service = service)
        val result = try {
            val pulled = TaskSyncPuller(db, service, merger, email).pull()
            val sent = TaskSyncSender(db, service, merger, email, prefs = TaskSync.prefs(app)).sendDue()
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
