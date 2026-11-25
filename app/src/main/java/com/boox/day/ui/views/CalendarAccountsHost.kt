package com.boox.day.ui.views

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.boox.day.TAG
import com.boox.day.data.AppRepository
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.datastore.SetupChecklistStore
import com.boox.day.data.db.LocalCalendar
import com.boox.day.data.db.LocalCalendarRepository
import com.boox.day.data.db.LocalEventRepository
import com.boox.day.data.model.serviceName
import com.boox.day.sync.MultiAccountSyncViewModel
import com.boox.day.ui.SnackConf
import com.boox.day.ui.SnackState
import com.boox.day.ui.UnifiedOverlayRegistry
import com.boox.day.ui.dialogs.AddCalendarDialog
import com.boox.day.ui.dialogs.CalendarSettingsDialog
import com.boox.day.ui.dialogs.CalendarSyncSettingsDialog
import com.boox.day.ui.dialogs.CloudAccountSummary
import com.boox.day.viewmodel.CalendarSyncViewModel
import com.boox.day.viewmodel.EventsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Controller for Calendar Accounts, Calendar Settings, and Add Local Calendar dialogs (Phase 5 / H14).
 * Keeps `showAccounts` in [rememberSaveable] so Calendar Accounts stays open across Google Sign-In
 * Activity recreation (H10).
 */
@Stable
internal class CalendarAccountsHostState(
    val showAccountsState: MutableState<Boolean>,
    val showSettingsState: MutableState<Boolean>,
    val showAddCalendarState: MutableState<Boolean>
) {
    fun openAccounts() { showAccountsState.value = true }
    fun openSettings() { showSettingsState.value = true }
}

@Composable
internal fun rememberCalendarAccountsHostState(): CalendarAccountsHostState {
    val showAccounts = rememberSaveable { mutableStateOf(false) }
    val showSettings = remember { mutableStateOf(false) }
    val showAddCalendar = remember { mutableStateOf(false) }
    return remember { CalendarAccountsHostState(showAccounts, showSettings, showAddCalendar) }
}

@Composable
internal fun CalendarAccountsHost(
    state: CalendarAccountsHostState,
    overlayRegistry: UnifiedOverlayRegistry,
    multiAccountSyncViewModel: MultiAccountSyncViewModel,
    syncViewModel: CalendarSyncViewModel,
    eventsViewModel: EventsViewModel,
    appRepository: AppRepository,
    localCalendarRepository: LocalCalendarRepository,
    localEventRepository: LocalEventRepository,
    localCalendars: List<LocalCalendar>,
    onLocalCalendarsChanged: (List<LocalCalendar>) -> Unit,
    syncModeSetting: AppSettings.SyncMode,
    firstDayOfWeek: AppSettings.FirstDayOfWeek,
    showWeekNumbers: Boolean,
    syncInProgress: Boolean
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    // Multi-provider Google sign-in launcher stays mounted so Activity recreation delivers the result
    val multiProviderSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Multi-provider sign-in result: resultCode=${result.resultCode}, data=${result.data != null}")
        multiAccountSyncViewModel.handleGoogleSignInResult(result.data, result.resultCode)
    }

    overlayRegistry.Overlay("calendar_accounts", state.showAccountsState.value) {
        CalendarSyncSettingsDialog(
            viewModel = multiAccountSyncViewModel,
            onDismiss = { state.showAccountsState.value = false },
            onGoogleSignIn = { accountType ->
                activity?.let { act ->
                    scope.launch {
                        val intent = multiAccountSyncViewModel.getGoogleSignInIntent(act, accountType)
                        intent?.let { multiProviderSignInLauncher.launch(it) }
                    }
                }
            },
            onOutlookSignIn = { accountType ->
                activity?.let { act ->
                    multiAccountSyncViewModel.addOutlookAccount(act, accountType)
                }
            },
            onFirstAccountAdded = {
                if (syncModeSetting == AppSettings.SyncMode.STANDALONE) {
                    Log.i(TAG, "First cloud account added - switching to HYBRID mode")
                    val newSettings = GlobalAppSettings.current.copy(syncMode = AppSettings.SyncMode.HYBRID)
                    appRepository.kvProxy.setAppSettings(newSettings)
                }
            },
            onClearDataAndStandalone = {
                Log.i(TAG, "User chose: Clear data and return to Standalone")
                scope.launch {
                    eventsViewModel.clearAllSyncedEvents()
                    val newSettings = GlobalAppSettings.current.copy(syncMode = AppSettings.SyncMode.STANDALONE)
                    appRepository.kvProxy.setAppSettings(newSettings)
                    SnackState.globalSnackFlow.tryEmit(
                        SnackConf(text = "Synced events cleared. Standalone mode active.", duration = SnackConf.DURATION_STANDARD)
                    )
                }
            },
            onKeepDataAndHybrid = {
                Log.i(TAG, "User chose: Keep data and stay in Hybrid")
                SnackState.globalSnackFlow.tryEmit(
                    SnackConf(text = "Events preserved. You can add accounts later.", duration = SnackConf.DURATION_STANDARD)
                )
            },
            syncedEventCount = 0
        )
    }

    overlayRegistry.Overlay("calendar_settings", state.showSettingsState.value) {
        val autoSyncEnabled by syncViewModel.autoSyncEnabled.collectAsState()
        val syncFrequency by syncViewModel.syncFrequency.collectAsState()
        val multiAccountUiState by multiAccountSyncViewModel.uiState.collectAsState()
        val connectedEmails = remember(multiAccountUiState) {
            multiAccountUiState.allAccounts.map { it.account.email }
        }
        val cloudAccountSummaries = remember(multiAccountUiState) {
            multiAccountUiState.allAccounts.map { acctWithCal ->
                CloudAccountSummary(
                    email = acctWithCal.account.email,
                    provider = acctWithCal.account.serviceName,
                    lastSyncTimeMillis = acctWithCal.account.lastSyncAt
                )
            }
        }

        CalendarSettingsDialog(
            onDismiss = { state.showSettingsState.value = false },
            onSyncNow = { multiAccountSyncViewModel.syncAllAccounts() },
            onOpenCalendarAccounts = { state.showAccountsState.value = true },
            connectedAccounts = cloudAccountSummaries,
            syncInProgress = syncInProgress || multiAccountUiState.isSyncing,
            autoSyncEnabled = autoSyncEnabled,
            syncFrequency = syncFrequency,
            onAutoSyncChanged = { syncViewModel.setAutoSync(it) },
            onSyncFrequencyChanged = { syncViewModel.setSyncFrequency(it) },
            firstDayOfWeek = firstDayOfWeek,
            onFirstDayOfWeekChanged = { newValue ->
                val newSettings = GlobalAppSettings.current.copy(firstDayOfWeek = newValue)
                appRepository.kvProxy.setAppSettings(newSettings)
            },
            showWeekNumbers = showWeekNumbers,
            onShowWeekNumbersChanged = { newValue ->
                val newSettings = GlobalAppSettings.current.copy(showWeekNumbers = newValue)
                appRepository.kvProxy.setAppSettings(newSettings)
            },
            syncMode = syncModeSetting,
            connectedAccountEmails = connectedEmails,
            syncedEventCount = 0,
            onSyncModeChanged = { newMode ->
                val newSettings = GlobalAppSettings.current.copy(syncMode = newMode)
                appRepository.kvProxy.setAppSettings(newSettings)
                SetupChecklistStore.mark(context) { it.copy(calendarModeChosen = true) }
            },
            onSwitchToStandaloneKeepData = {
                Log.i(TAG, "Switch to Standalone: Keep data")
                SetupChecklistStore.mark(context) { it.copy(calendarModeChosen = true) }
                scope.launch {
                    multiAccountSyncViewModel.disconnectAllAccounts()
                    val newSettings = GlobalAppSettings.current.copy(syncMode = AppSettings.SyncMode.STANDALONE)
                    appRepository.kvProxy.setAppSettings(newSettings)
                    SnackState.globalSnackFlow.tryEmit(
                        SnackConf(text = "Private Mode enabled. Synced events preserved (read-only).", duration = SnackConf.DURATION_STANDARD)
                    )
                }
            },
            onSwitchToStandaloneClearData = {
                Log.i(TAG, "Switch to Standalone: Clear data")
                SetupChecklistStore.mark(context) { it.copy(calendarModeChosen = true) }
                scope.launch {
                    eventsViewModel.clearAllSyncedEvents()
                    multiAccountSyncViewModel.disconnectAllAccounts()
                    val newSettings = GlobalAppSettings.current.copy(syncMode = AppSettings.SyncMode.STANDALONE)
                    appRepository.kvProxy.setAppSettings(newSettings)
                    SnackState.globalSnackFlow.tryEmit(
                        SnackConf(text = "Synced events cleared. Standalone mode active.", duration = SnackConf.DURATION_STANDARD)
                    )
                }
            },
            localCalendars = localCalendars,
            onAddLocalCalendar = { state.showAddCalendarState.value = true },
            onDeleteLocalCalendars = { calendarIds ->
                scope.launch {
                    val updated = withContext(Dispatchers.IO) {
                        localCalendarRepository.delete(calendarIds, localEventRepository)
                        localCalendarRepository.getAll()
                    }
                    onLocalCalendarsChanged(updated)
                    SnackState.globalSnackFlow.tryEmit(
                        SnackConf(text = "${calendarIds.size} calendar(s) deleted", duration = SnackConf.DURATION_STANDARD)
                    )
                }
            }
        )
    }

    overlayRegistry.Overlay("add_local_calendar", state.showAddCalendarState.value) {
        AddCalendarDialog(
            onDismiss = { state.showAddCalendarState.value = false },
            onAdd = { name, colorHex ->
                scope.launch {
                    try {
                        val updated = withContext(Dispatchers.IO) {
                            localCalendarRepository.create(name, colorHex)
                            localCalendarRepository.getAll()
                        }
                        onLocalCalendarsChanged(updated)
                        state.showAddCalendarState.value = false
                        SnackState.globalSnackFlow.tryEmit(
                            SnackConf(text = "Calendar \"$name\" created", duration = SnackConf.DURATION_STANDARD)
                        )
                    } catch (e: Exception) {
                        SnackState.globalSnackFlow.tryEmit(
                            SnackConf(text = e.message ?: "Failed to create calendar", duration = SnackConf.DURATION_ERROR)
                        )
                    }
                }
            },
            existingNames = localCalendars.map { it.name }.toSet()
        )
    }
}
