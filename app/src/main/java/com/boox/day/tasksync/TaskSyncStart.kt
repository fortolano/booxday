package com.boox.day.tasksync

import android.content.Context
import com.boox.day.data.datastore.AppSettings
import com.boox.day.sync.workerAppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turning Task Sync on with a service: from the Preferences card, and from Calendar Accounts when a
 * CalDAV account is used for its tasks (owner decision 78). One way, one set of messages.
 */
internal object TaskSyncStart {

    /** The service Task Sync uses now when it is on with another one than [target] (ask before switching, decision 78). */
    fun otherServiceOn(context: Context, target: TaskSyncTarget): TaskSyncTarget? =
        TaskSync.target(context)?.takeIf { TaskSync.isOn(context) && it.key != target.key }

    /**
     * Turns Task Sync on with [target] and runs its first sync (decision 9's rules); a service in use
     * before is turned off first (decision 32: switching while off). Null when it's on, else why
     * not, in plain words. [onSyncing] is called once the turn-on is done and the first sync starts.
     */
    suspend fun turnOn(context: Context, target: TaskSyncTarget, onSyncing: () -> Unit = {}): String? {
        val name = target.shortName
        if (workerAppSettings(context).syncMode != AppSettings.SyncMode.HYBRID) {
            return "Task Sync works with Cloud Sync. Turn on Cloud Sync in the Settings menu first."
        }
        return try {
            val service = withContext(Dispatchers.IO) { target.service(context) }
            // A server without task lists: nothing to sync with yet (picture E, owner decision 68)
            if (!target.isGoogle && withContext(Dispatchers.IO) { service.lists() }.isEmpty()) {
                "$name has no task lists. Make one there first, then turn Task Sync on again."
            } else {
                if (TaskSync.isOn(context) && TaskSync.target(context)?.key != target.key) TaskSync.turnOff(context)
                if (!TaskSync.isOn(context)) TaskSync.choose(context, target)
                withContext(Dispatchers.IO) { TaskSync.turnOn(context, target, service) }
                onSyncing()
                withContext(Dispatchers.IO) { TaskSyncRun.run(context, changedAt = System.currentTimeMillis()) }
                null
            }
        } catch (e: TaskSyncServiceException.Offline) {
            "No connection to $name. Try again when the tablet is online."
        } catch (e: TaskSyncServiceException.SignInNeeded) {
            "$name refused the password. Sign in again in Settings → Calendar Accounts."
        } catch (e: TaskSyncServiceException) {
            "$name didn't answer as expected. Try again in a moment."
        } catch (e: Exception) {
            "Task Sync couldn't be turned on. Try again in a moment."
        }
    }
}
