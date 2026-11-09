package com.boox.day.tasksync

import android.content.Context
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.data.model.ConnectedAccount
import com.boox.day.data.model.ProviderId
import com.boox.day.data.model.serviceName
import com.boox.day.tasksync.caldav.CalDavTasksProvider
import com.boox.day.tasksync.google.GoogleTasksProvider
import com.boox.day.tasksync.microsoft.MicrosoftToDoProvider

/**
 * The service and account task sync uses (plan step 4.0, Nov 2026; one at a time, owner decision 6):
 * Google Tasks for a Google account, Microsoft To Do for an Outlook account, or the task lists of a
 * CalDAV account from Calendar Accounts (signed in there, so no second sign-in). Links and lists are
 * keyed by [provider], [accountEmail] and [serverUrl].
 */
internal data class TaskSyncTarget(
    val provider: String,
    /** The Google or Outlook address, or the CalDAV sign-in name. */
    val accountEmail: String,
    /** The CalDAV account's server ("" for Google and Outlook). */
    val serverUrl: String = "",
    /** The CalDAV or Outlook account's id in Calendar Accounts (null for Google). */
    val accountId: String? = null,
    /** "Google Tasks", "Microsoft To Do", "Nextcloud", "Fastmail", or the server for Other. */
    val serviceName: String = GOOGLE_TASKS_NAME
) {
    val isGoogle: Boolean get() = provider == TaskSyncProvider.GOOGLE_TASKS
    val isMicrosoft: Boolean get() = provider == TaskSyncProvider.MICROSOFT_TODO
    val isCalDav: Boolean get() = provider == TaskSyncProvider.CALDAV

    /** The Calendar Accounts provider the account is connected with. */
    val accountProvider: ProviderId get() = when {
        isGoogle -> ProviderId.GOOGLE
        isMicrosoft -> ProviderId.OUTLOOK
        else -> ProviderId.CALDAV
    }

    /** In sentences: "Google", "Microsoft", "Nextcloud", the server. */
    val shortName: String get() = when {
        isGoogle -> "Google"
        isMicrosoft -> "Microsoft"
        else -> serviceName
    }

    /**
     * [serviceName], with the account when [other] has the same one ("localhost (both)"): two
     * accounts on one server told apart (owner decision 78's "Switch Task Sync?").
     */
    fun nameBeside(other: TaskSyncTarget): String =
        if (other.serviceName == serviceName && other.key != key) "$serviceName ($accountEmail)" else serviceName

    /** The Task Sync card's choice. */
    val key: String get() = when {
        isGoogle -> "google:$accountEmail"
        isMicrosoft -> "microsoft:$accountId"
        else -> "caldav:$accountId"
    }

    /** Throws [TaskSyncServiceException.SignInNeeded] when a CalDAV account's password can't be read, or an Outlook account is gone from MSAL. */
    suspend fun service(context: Context): TaskSyncService = when {
        isGoogle -> GoogleTasksProvider.create(context, accountEmail)
        isMicrosoft -> MicrosoftToDoProvider.create(context, checkNotNull(accountId) { "A Microsoft target has an account" })
        else -> CalDavTasksProvider.create(context, checkNotNull(accountId) { "A CalDAV target has an account" })
    }

    companion object {
        const val GOOGLE_TASKS_NAME = "Google Tasks"

        fun google(email: String) = TaskSyncTarget(TaskSyncProvider.GOOGLE_TASKS, email)

        /** To Do of an Outlook account from Calendar Accounts (its MSAL sign-in, with To Do's own permission). */
        fun microsoft(account: ConnectedAccount) =
            TaskSyncTarget(TaskSyncProvider.MICROSOFT_TODO, account.email, accountId = account.id, serviceName = MicrosoftToDoProvider.SERVICE_NAME)

        fun calDav(account: ConnectedAccount) =
            TaskSyncTarget(TaskSyncProvider.CALDAV, account.email, account.serverUrl, account.id, account.serviceName)
    }
}
