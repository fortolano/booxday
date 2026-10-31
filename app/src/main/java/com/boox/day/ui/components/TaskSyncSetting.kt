package com.boox.day.ui.components

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
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.data.model.ProviderId
import com.boox.day.sync.GoogleTasksAuth
import com.boox.day.sync.workerAppSettings
import com.boox.day.tasksync.RemoteList
import com.boox.day.tasksync.TaskSync
import com.boox.day.tasksync.TaskSyncRun
import com.boox.day.tasksync.TaskSyncServiceException
import com.boox.day.tasksync.google.GoogleTasksProvider
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
 * Turning it on asks Google for the Tasks permission for the chosen account (its own consent
 * screen, sync/GoogleTasksAuth), then turns sync on (main list ticked, decision 30; ticked tasks
 * stay here, decision 9) and runs the first sync at once.
 */
@Composable
fun TaskSyncSetting(modifier: Modifier = Modifier) {
    if (!BuildConfig.TASK_SYNC_ENABLED) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var googleLists by remember { mutableStateOf<List<RemoteList>?>(null) }
    var working by remember { mutableStateOf<String?>(null) }  // "Connecting to Google…", "Syncing…"
    var message by remember { mutableStateOf<String?>(null) }  // the outcome of the last action, if it failed
    var reloads by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<TaskSyncCardState?>(null) }
    var afterConsent by remember { mutableStateOf<(suspend () -> Unit)?>(null) }

    LaunchedEffect(reloads, googleLists, working, message) {
        state = withContext(Dispatchers.IO) { loadTaskSyncCard(context, googleLists, working ?: message) }
    }
    // The account's lists, to tick (only while on: turning on is when Google's permission is given)
    LaunchedEffect(state?.on, state?.account) {
        val email = state?.account
        if (state?.on == true && email != null && googleLists == null) {
            googleLists = withContext(Dispatchers.IO) { runCatching { GoogleTasksProvider.create(context, email).lists() }.getOrNull() }
        }
    }

    fun syncNow() {
        scope.launch {
            working = "Syncing…"
            message = null
            withContext(Dispatchers.IO) { TaskSyncRun.run(context, changedAt = System.currentTimeMillis(), handwritingNow = true) }
            working = null
            reloads++
        }
    }

    suspend fun turnOn(email: String) {
        working = "Connecting to Google…"
        message = try {
            withContext(Dispatchers.IO) { TaskSync.turnOn(context, email, GoogleTasksProvider.create(context, email)) }
            googleLists = null
            working = "Syncing…"
            withContext(Dispatchers.IO) { TaskSyncRun.run(context, changedAt = System.currentTimeMillis()) }
            null
        } catch (e: TaskSyncServiceException.Offline) {
            "No connection to Google. Try again when the tablet is online."
        } catch (e: TaskSyncServiceException) {
            "Google didn't answer as expected. Try again in a moment."
        } catch (e: Exception) {
            "Task Sync couldn't be turned on. Try again in a moment."
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

    val provider = TaskSyncProvider.GOOGLE_TASKS
    val actions = TaskSyncCardActions(
        turnOn = { on ->
            val email = state?.account
            if (on && email != null) withPermission(email) { turnOn(email) }
            if (!on) {
                TaskSync.turnOff(context)
                message = null
                reloads++
            }
        },
        pickAccount = { email ->
            TaskSync.chooseAccount(context, email)
            reloads++
        },
        tickList = { listId, ticked ->
            val email = state?.account
            val title = state?.lists?.firstOrNull { it.id == listId }?.title.orEmpty()
            if (email != null) {
                scope.launch {
                    withContext(Dispatchers.IO) { TaskSync.tickList(context, provider, email, listId, title, ticked) }
                    if (ticked) syncNow() else reloads++
                }
            }
        },
        newTasksTo = { listId ->
            val email = state?.account
            if (email != null) {
                scope.launch {
                    withContext(Dispatchers.IO) { TaskSync.newTasksTo(context, provider, email, listId) }
                    reloads++
                }
            }
        },
        syncNow = { syncNow() },
        reconnect = {
            val email = state?.account
            if (email != null) withPermission(email) { working = "Syncing…"; withContext(Dispatchers.IO) { TaskSyncRun.run(context, changedAt = System.currentTimeMillis()) }; working = null; reloads++ }
        },
        keepInGoogle = {
            scope.launch {
                withContext(Dispatchers.IO) { TaskSync.keepWaitingDeletesInService(context) }
                syncNow()
            }
        },
        deleteInGoogle = {
            scope.launch {
                withContext(Dispatchers.IO) { TaskSync.approveWaitingDeletes(context) }
                syncNow()
            }
        }
    )

    SettingCard(
        title = "Task Sync",
        description = "Keep your tasks in step with Google Tasks on your phone. Off until you turn it on.",
        modifier = modifier
    ) {
        state?.let { TaskSyncCardContent(it, actions) }
    }
}

/** What the card shows now. [override]: a line in place of the last sync's ("Syncing…", an error). */
internal suspend fun loadTaskSyncCard(context: Context, googleLists: List<RemoteList>?, override: String?): TaskSyncCardState {
    val app = context.applicationContext
    val db = AppDatabase.getDatabase(app)
    val provider = TaskSyncProvider.GOOGLE_TASKS
    val accounts = db.connectedAccountDao().getAccountsByProvider(ProviderId.GOOGLE.key).map { it.email }
    val settings = workerAppSettings(app)
    val on = TaskSync.isOn(app)
    val account = TaskSync.account(app)?.takeIf { on || it in accounts } ?: accounts.firstOrNull()
    val cantTurnOn = when {
        settings.syncMode != AppSettings.SyncMode.HYBRID -> "Task Sync works with Cloud Sync. Turn on Cloud Sync in the Settings menu first."
        accounts.isEmpty() -> "Connect a Google account first: Settings → Calendar Accounts."
        else -> null
    }
    val sync = db.taskSyncDao()
    val rows = account?.let { sync.getListsFor(provider, it, "") }.orEmpty()
    val ticked = rows.mapTo(HashSet()) { it.listId }
    val lists = googleLists?.map { TaskSyncCardList(it.listId, it.title, it.listId in ticked) }
        ?.plus(rows.filter { row -> googleLists.none { it.listId == row.listId } }.map { TaskSyncCardList(it.listId, it.title, true) })
        ?: rows.map { TaskSyncCardList(it.listId, it.title, true) }
    val status = TaskSync.status(app)
    val use24Hour = settings.timeFormat == AppSettings.TimeFormat.TwentyFourHour
    return TaskSyncCardState(
        on = on,
        cantTurnOn = cantTurnOn,
        account = account,
        accounts = accounts,
        lists = lists,
        newTasksListId = rows.firstOrNull { it.isDefaultForNew }?.listId,
        status = override ?: if (on) statusLine(status, use24Hour) else null,
        needsReconnect = status.needsReconnect,
        // Removed in Calendar Accounts while on (owner decision 41)
        accountGone = on && account != null && account !in accounts,
        deletesWaiting = status.deletesWaiting,
        fullDays = if (on) TaskSync.fullDays(app).map(::dayLabel) else emptyList(),
        keptChangedHere = if (on) sync.countKeptChangedHere() else 0
    )
}

private fun statusLine(status: TaskSync.Status, use24Hour: Boolean): String = when (status.lastRun) {
    null -> "Not synced yet"
    TaskSyncRun.Result.DONE, TaskSyncRun.Result.OFF ->
        status.lastSyncedAt?.let { "Last synced ${whenLabel(it, use24Hour)}" } ?: "Not synced yet"
    TaskSyncRun.Result.OFFLINE -> "Waiting for a connection"
    TaskSyncRun.Result.BUSY -> "Google is busy; sync tries again soon"
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
