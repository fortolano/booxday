package com.boox.day.tasksync

import android.content.Context
import android.util.Log
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.sync.SingleFlight
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

    enum class Result { DONE, OFF, NO_LIST, OFFLINE, BUSY, NEEDS_RECONNECT, PAUSED_FOR_DELETES, FAILED }

    private val flight = SingleFlight<String, Result>()

    suspend fun run(context: Context): Result {
        val app = context.applicationContext
        return flight.run("task-sync") { withContext(Dispatchers.IO) { runNow(app) } }
    }

    private suspend fun runNow(app: Context): Result {
        if (!TaskSync.isOn(app)) return Result.OFF
        val email = TaskSync.account(app) ?: return Result.OFF
        val db = AppDatabase.getDatabase(app)
        val provider = TaskSyncProvider.GOOGLE_TASKS
        if (db.taskSyncDao().getListsFor(provider, email, "").isEmpty()) return TaskSync.recordRun(app, Result.NO_LIST)

        TaskSync.scanner(app).scan(System.currentTimeMillis(), LocalDate.now().toString())
        val service = GoogleTasksProvider.create(app, email)
        val merger = TaskSyncMerger(db, RepositoryTabletTasks(app), provider, email)
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
