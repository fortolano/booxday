package com.boox.day.ui.components

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.boox.day.BuildConfig
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.model.AccountType
import com.boox.day.data.model.ProviderId
import com.boox.day.sync.GoogleTasksAuth
import com.boox.day.sync.workerAppSettings
import com.boox.day.tasksync.RemoteList
import com.boox.day.tasksync.TaskSync
import com.boox.day.tasksync.TaskSyncRun
import com.boox.day.tasksync.TaskSyncServiceException
import com.boox.day.tasksync.TaskSyncStart
import com.boox.day.tasksync.TaskSyncTarget
import com.boox.day.tasksync.microsoft.MicrosoftToDoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Task Sync card in Preferences with its state (task sync plan step 2.6, Oct 2026). Shown only
 * in builds with task sync (BuildConfig.TASK_SYNC_ENABLED: debug builds until Google approves the
 * Tasks permission, owner decision 15). Keeps its own state, so Preferences needs no parameters.
 *
 * The service is chosen while off (plan step 4.6, Nov 2026; owner decision 68): Google Tasks for a
 * Google account, Microsoft To Do for an Outlook account, or a CalDAV account from Calendar Accounts
 * (iCloud has no task lists for other apps). Turning it on for Google asks for the Tasks permission
 * (its own consent screen, sync/GoogleTasksAuth), for Microsoft the To Do permission the same way
 * (MicrosoftToDoProvider.connect); a CalDAV account is already signed in. Then sync turns on (main
 * list ticked, decision 30; ticked tasks stay here, decision 9) and the first sync runs at once.
 */
@Composable
fun TaskSyncSetting(modifier: Modifier = Modifier) {
    if (!BuildConfig.TASK_SYNC_ENABLED) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var serviceLists by remember { mutableStateOf<List<RemoteList>?>(null) }
    var working by remember { mutableStateOf<String?>(null) }  // "Connecting to Google…", "Syncing…"
    var message by remember { mutableStateOf<String?>(null) }  // the outcome of the last action, if it failed
    var reloads by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf<TaskSyncCardModel?>(null) }
    var afterConsent by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    // Asked again when the lists couldn't be read (no connection, access withdrawn) and that may
    // have changed: after Reconnect, after a sync
    var listsAttempt by remember { mutableIntStateOf(0) }
    val state = loaded?.state

    LaunchedEffect(reloads, serviceLists, working, message) {
        loaded = withContext(Dispatchers.IO) { loadTaskSyncCard(context, serviceLists, working ?: message) }
    }
    // The account's lists, to tick (only while on: turning on is when Google's permission is given)
    LaunchedEffect(state?.on, state?.chosen, listsAttempt) {
        val target = loaded?.chosenTarget
        if (state?.on == true && target != null && serviceLists == null) {
            serviceLists = withContext(Dispatchers.IO) { runCatching { target.service(context).lists() }.getOrNull() }
        }
    }

    fun syncNow() {
        scope.launch {
            working = "Syncing…"
            message = null
            withContext(Dispatchers.IO) { TaskSyncRun.run(context, changedAt = System.currentTimeMillis(), handwritingNow = true) }
            working = null
            if (serviceLists == null) listsAttempt++
            reloads++
        }
    }

    suspend fun turnOn(target: TaskSyncTarget) {
        working = "Connecting to ${target.shortName}…"
        message = TaskSyncStart.turnOn(context, target) {
            serviceLists = null
            working = "Syncing…"
        }
        working = null
        reloads++
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val next = afterConsent
        afterConsent = null
        if (GoogleTasksAuth.consentResult(context, result.data) is GoogleTasksAuth.Status.Granted && next != null) {
            scope.launch { next() }
        } else {
            working = null
            message = "Google Tasks wasn't allowed, so Task Sync stays off."
        }
    }

    /** Google's Tasks permission for [email], then [then]; its consent screen first if Google asks. */
    fun withPermission(email: String, then: suspend () -> Unit) {
        scope.launch {
            working = "Connecting to Google…"
            message = null
            when (val status = GoogleTasksAuth.check(context, email)) {
                is GoogleTasksAuth.Status.Granted -> then()
                is GoogleTasksAuth.Status.NeedsConsent -> {
                    afterConsent = then
                    consent.launch(IntentSenderRequest.Builder(status.pendingIntent).build())
                }
                is GoogleTasksAuth.Status.Failed -> {
                    working = null
                    message = "Google said no: ${status.reason}."
                }
            }
        }
    }

    /** Microsoft's To Do permission for [target]'s account, then [then]; its consent screen first if Microsoft asks. */
    fun withMicrosoft(target: TaskSyncTarget, then: suspend () -> Unit) {
        scope.launch {
            working = "Connecting to Microsoft…"
            message = null
            val activity = context as? Activity
            val why = if (activity == null) "Microsoft's sign-in couldn't be opened. Try again in a moment."
            else MicrosoftToDoProvider.connect(activity, checkNotNull(target.accountId) { "A Microsoft target has an account" })
            if (why == null) then() else {
                working = null
                message = why
            }
        }
    }

    val actions = TaskSyncCardActions(
        turnOn = { on ->
            val target = loaded?.chosenTarget
            if (on && target != null) {
                when {
                    target.isGoogle -> withPermission(target.accountEmail) { turnOn(target) }
                    target.isMicrosoft -> withMicrosoft(target) { turnOn(target) }
                    else -> scope.launch { message = null; turnOn(target) }
                }
            }
            if (!on) {
                TaskSync.turnOff(context)
                message = null
                reloads++
            }
        },
        choose = { key ->
            loaded?.targets?.get(key)?.let { TaskSync.choose(context, it) }
            message = null
            reloads++
        },
        tickList = { listId, ticked ->
            val target = loaded?.chosenTarget
            val title = state?.lists?.firstOrNull { it.id == listId }?.title.orEmpty()
            if (target != null) {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        TaskSync.tickList(context, target.provider, target.accountEmail, listId, title, ticked, target.serverUrl)
                    }
                    if (ticked) syncNow() else reloads++
                }
            }
        },
        newTasksTo = { listId ->
            val target = loaded?.chosenTarget
            if (target != null) {
                scope.launch {
                    withContext(Dispatchers.IO) { TaskSync.newTasksTo(context, target.provider, target.accountEmail, listId, target.serverUrl) }
                    reloads++
                }
            }
        },
        syncNow = { syncNow() },
        // Google and Microsoft: a CalDAV password is signed in again in Calendar Accounts (owner decision 68)
        reconnect = {
            val target = loaded?.chosenTarget
            val afterReconnect: suspend () -> Unit = {
                working = "Syncing…"
                withContext(Dispatchers.IO) { TaskSyncRun.run(context, changedAt = System.currentTimeMillis()) }
                working = null
                serviceLists = null  // read while the access was gone: only the ticked lists
                listsAttempt++
                reloads++
            }
            when {
                target == null -> Unit
                target.isGoogle -> withPermission(target.accountEmail, afterReconnect)
                target.isMicrosoft -> withMicrosoft(target, afterReconnect)
            }
        },
        keepInService = {
            scope.launch {
                withContext(Dispatchers.IO) { TaskSync.keepWaitingDeletesInService(context) }
                syncNow()
            }
        },
        deleteInService = {
            scope.launch {
                withContext(Dispatchers.IO) { TaskSync.approveWaitingDeletes(context) }
                syncNow()
            }
        }
    )

    SettingCard(
        title = "Task Sync",
        description = "Keep your tasks in step with Google Tasks, Microsoft To Do or a CalDAV server (Nextcloud, Fastmail and others). Off until you turn it on.",
        modifier = modifier
    ) {
        state?.let { TaskSyncCardContent(it, actions) }
    }
}

/** What the card shows, and the service behind each of its choices. */
internal class TaskSyncCardModel(val state: TaskSyncCardState, val targets: Map<String, TaskSyncTarget>) {
    val chosenTarget: TaskSyncTarget? get() = state.chosen?.key?.let(targets::get)
}

/** What the card shows now. [override]: a line in place of the last sync's ("Syncing…", an error). */
internal suspend fun loadTaskSyncCard(context: Context, serviceLists: List<RemoteList>?, override: String?): TaskSyncCardModel {
    val app = context.applicationContext
    val db = AppDatabase.getDatabase(app)
    val google = db.connectedAccountDao().getAccountsByProvider(ProviderId.GOOGLE.key).map { TaskSyncTarget.google(it.email) }
    val microsoft = db.connectedAccountDao().getAccountsByProvider(ProviderId.OUTLOOK.key).map { TaskSyncTarget.microsoft(it.toDomainModel()) }
    val calDav = db.connectedAccountDao().getAccountsByProvider(ProviderId.CALDAV.key).map { it.toDomainModel() }
    // iCloud has CalDAV calendars but no task lists for other apps (owner decision 68)
    val iCloud = calDav.any { it.accountType == AccountType.CALDAV_ICLOUD }
    val targets = (google + microsoft + calDav.filter { it.accountType != AccountType.CALDAV_ICLOUD }.map(TaskSyncTarget::calDav))
        .associateBy { it.key }
    val settings = workerAppSettings(app)
    val on = TaskSync.isOn(app)
    val saved = TaskSync.target(app)
    // While on, the one in use (even if its account is gone); while off, the one picked if it is still there
    val chosen = saved?.takeIf { on || it.key in targets }?.let { targets[it.key] ?: it } ?: targets.values.firstOrNull()
    val cantTurnOn = when {
        settings.syncMode != AppSettings.SyncMode.HYBRID -> "Task Sync works with Cloud Sync. Turn on Cloud Sync in the Settings menu first."
        targets.isEmpty() && iCloud -> "iCloud can't be used: Apple doesn't share Reminders with other apps. " +
            "Connect Google, Microsoft, Nextcloud, Fastmail or another CalDAV server in Settings → Calendar Accounts."
        targets.isEmpty() -> "Connect Google, Microsoft, Nextcloud, Fastmail or another CalDAV server first: Settings → Calendar Accounts."
        else -> null
    }
    val sync = db.taskSyncDao()
    val rows = chosen?.let { sync.getListsFor(it.provider, it.accountEmail, it.serverUrl) }.orEmpty()
    val ticked = rows.mapTo(HashSet()) { it.listId }
    val lists = serviceLists?.map { TaskSyncCardList(it.listId, it.title, it.listId in ticked) }
        ?.plus(rows.filter { row -> serviceLists.none { it.listId == row.listId } }.map { TaskSyncCardList(it.listId, it.title, true) })
        ?: rows.map { TaskSyncCardList(it.listId, it.title, true) }
    val status = TaskSync.status(app)
    val use24Hour = settings.timeFormat == AppSettings.TimeFormat.TwentyFourHour
    val state = TaskSyncCardState(
        on = on,
        cantTurnOn = cantTurnOn,
        chosen = chosen?.let { TaskSyncChoice(it.key, it.serviceName, it.accountEmail, it.isGoogle, it.isMicrosoft) },
        choices = targets.values.map { TaskSyncChoice(it.key, it.serviceName, it.accountEmail, it.isGoogle, it.isMicrosoft) },
        notListed = if (iCloud) "iCloud isn't listed: Apple doesn't share Reminders with other apps." else null,
        lists = lists,
        newTasksListId = rows.firstOrNull { it.isDefaultForNew }?.listId,
        status = override ?: if (on) statusLine(status, use24Hour, chosen?.shortName ?: "The service") else null,
        needsReconnect = status.needsReconnect,
        // Removed in Calendar Accounts while on (owner decision 41)
        accountGone = on && chosen != null && chosen.key !in targets,
        deletesWaiting = status.deletesWaiting,
        fullDays = if (on) TaskSync.fullDays(app).map(::dayLabel) else emptyList(),
        keptChangedHere = if (on) sync.countKeptChangedHere() else 0
    )
    return TaskSyncCardModel(state, targets)
}

private fun statusLine(status: TaskSync.Status, use24Hour: Boolean, service: String): String = when (status.lastRun) {
    null -> "Not synced yet"
    TaskSyncRun.Result.DONE, TaskSyncRun.Result.OFF ->
        status.lastSyncedAt?.let { "Last synced ${whenLabel(it, use24Hour)}" } ?: "Not synced yet"
    TaskSyncRun.Result.OFFLINE -> "Waiting for a connection"
    TaskSyncRun.Result.BUSY -> "$service is busy; sync tries again soon"
    TaskSyncRun.Result.NEEDS_RECONNECT, TaskSyncRun.Result.PAUSED_FOR_DELETES -> "Waiting for you"
    TaskSyncRun.Result.NO_LIST -> "No list ticked"
    TaskSyncRun.Result.FAILED -> "The last sync didn't finish; it tries again"
}

/** "today at 10:42", "Mon 3 Nov at 10:42". */
private fun whenLabel(at: Long, use24Hour: Boolean): String {
    val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    val clock = time.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", Locale.ENGLISH))
    val day = if (time.toLocalDate() == LocalDate.now()) "today" else dayLabel(time.toLocalDate().toString())
    return "$day at $clock"
}

/** "Mon 3 Nov" (the app is in English). */
private fun dayLabel(day: String): String =
    runCatching { LocalDate.parse(day).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)) }.getOrDefault(day)
