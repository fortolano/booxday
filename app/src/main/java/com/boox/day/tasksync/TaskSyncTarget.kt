package com.boox.day.tasksync

import android.content.Context
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.data.model.ConnectedAccount
import com.boox.day.data.model.serviceName
import com.boox.day.tasksync.caldav.CalDavTasksProvider
import com.boox.day.tasksync.google.GoogleTasksProvider

/**
 * The service and account task sync uses (plan step 4.0, Nov 2026; one at a time, owner decision 6):
 * Google Tasks for a Google account, or the task lists of a CalDAV account from Calendar Accounts
 * (signed in there, so no second sign-in). Links and lists are keyed by [provider], [accountEmail]
 * and [serverUrl].
 */
internal data class TaskSyncTarget(
    val provider: String,
    /** The Google address, or the CalDAV sign-in name. */
    val accountEmail: String,
    /** The CalDAV account's server ("" for Google). */
    val serverUrl: String = "",
    /** The CalDAV account's id in Calendar Accounts (null for Google). */
    val accountId: String? = null,
    /** "Google Tasks", "Nextcloud", "Fastmail", or the server for Other. */
    val serviceName: String = GOOGLE_TASKS_NAME
) {
    val isGoogle: Boolean get() = provider == TaskSyncProvider.GOOGLE_TASKS

    /** In sentences: "Google", "Nextcloud", the server. */
    val shortName: String get() = if (isGoogle) "Google" else serviceName

    /** The Task Sync card's choice. */
    val key: String get() = if (isGoogle) "google:$accountEmail" else "caldav:$accountId"

    /** Throws [TaskSyncServiceException.SignInNeeded] when a CalDAV account's password can't be read. */
    suspend fun service(context: Context): TaskSyncService =
        if (isGoogle) GoogleTasksProvider.create(context, accountEmail)
        else CalDavTasksProvider.create(context, checkNotNull(accountId) { "A CalDAV target has an account" })

    companion object {
        const val GOOGLE_TASKS_NAME = "Google Tasks"

        fun google(email: String) = TaskSyncTarget(TaskSyncProvider.GOOGLE_TASKS, email)

        fun calDav(account: ConnectedAccount) =
            TaskSyncTarget(TaskSyncProvider.CALDAV, account.email, account.serverUrl, account.id, account.serviceName)
    }
}
