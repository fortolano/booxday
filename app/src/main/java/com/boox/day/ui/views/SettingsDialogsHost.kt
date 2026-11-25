package com.boox.day.ui.views

import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.boox.day.TAG
import com.boox.day.booxnotes.BooxNoteIndexer
import com.boox.day.booxnotes.BooxNotesIntegration
import com.boox.day.data.AppDataWipe
import com.boox.day.data.AppRepository
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.datastore.SetupChecklistStore
import com.boox.day.data.db.BooxNoteRepository
import com.boox.day.data.db.CalendarEventRepository
import com.boox.day.data.db.TaskRepository
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.ui.state.CalendarSourceFilter
import com.boox.day.editor.DrawCanvas
import com.boox.day.notifications.NotificationPermissionHelper
import com.boox.day.ocr.OcrQuotaManager
import com.boox.day.ocr.UnifiedOcrIdleProcessor
import com.boox.day.ocr.UnifiedOcrScheduler
import com.boox.day.speech.AudioRecorder
import com.boox.day.speech.TranscriptionService
import com.boox.day.tips.TipsService
import com.boox.day.ui.MainScreenRequest
import com.boox.day.ui.MainScreenRequests
import com.boox.day.ui.SnackConf
import com.boox.day.ui.SnackState
import com.boox.day.ui.UnifiedOverlayRegistry
import com.boox.day.ui.calendar.BooxNoteWithEvent
import com.boox.day.ui.calendar.DayQuickViewData
import com.boox.day.ui.calendar.DayQuickViewPopup
import com.boox.day.ui.dialogs.AboutDialog
import com.boox.day.ui.dialogs.AiKeyGateHost
import com.boox.day.ui.dialogs.AiSettingsDialog
import com.boox.day.ui.dialogs.BooxNotesDialog
import com.boox.day.ui.dialogs.BooxNotesPlaces
import com.boox.day.ui.dialogs.BooxNotesSettingsDialog
import com.boox.day.ui.dialogs.CalendarAiNoticeHost
import com.boox.day.ui.dialogs.DataBackupDialog
import com.boox.day.ui.dialogs.HelpDialog
import com.boox.day.ui.dialogs.PreferencesDialog
import com.boox.day.ui.dialogs.SearchDialog
import com.boox.day.ui.dialogs.VoiceSettingsDialog
import com.boox.day.viewmodel.CalendarSyncViewModel
import com.boox.day.viewmodel.EventsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Controller for Settings, Search, Help, About, and Day Quick View dialogs (Phase 5 / H14).
 * Keeps closed-dialog flows (`ocrProcessingState`, preference fields) out of `MainScreen` root.
 */
@Stable
internal class SettingsDialogsHostState(
    val showPreferencesState: MutableState<Boolean>,
    val showAiSettingsState: MutableState<Boolean>,
    val showVoiceSettingsState: MutableState<Boolean>,
    val showDataBackupState: MutableState<Boolean>,
    val showSearchState: MutableState<Boolean>,
    val showHelpState: MutableState<Boolean>,
    val showAboutState: MutableState<Boolean>,
    val quickViewDateState: MutableState<String?>
) {
    fun openPreferences() { showPreferencesState.value = true }
    fun openAiSettings() { showAiSettingsState.value = true }
    fun openVoiceSettings() { showVoiceSettingsState.value = true }
    fun openDataBackup() { showDataBackupState.value = true }
    fun openSearch() { showSearchState.value = true }
    fun openHelp() { showHelpState.value = true }
    fun openAbout() { showAboutState.value = true }
    fun openDayQuickView(date: String) { quickViewDateState.value = date }
}

@Composable
internal fun rememberSettingsDialogsHostState(): SettingsDialogsHostState {
    val showPreferences = remember { mutableStateOf(false) }
    val showAiSettings = remember { mutableStateOf(false) }
    val showVoiceSettings = remember { mutableStateOf(false) }
    val showDataBackup = remember { mutableStateOf(false) }
    val showSearch = remember { mutableStateOf(false) }
    val showHelp = remember { mutableStateOf(false) }
    val showAbout = remember { mutableStateOf(false) }
    val quickViewDate = remember { mutableStateOf<String?>(null) }

    return remember {
        SettingsDialogsHostState(
            showPreferencesState = showPreferences,
            showAiSettingsState = showAiSettings,
            showVoiceSettingsState = showVoiceSettings,
            showDataBackupState = showDataBackup,
            showSearchState = showSearch,
            showHelpState = showHelp,
            showAboutState = showAbout,
            quickViewDateState = quickViewDate
        )
    }
}

@Composable
internal fun SettingsDialogsHost(
    state: SettingsDialogsHostState,
    overlayRegistry: UnifiedOverlayRegistry,
    selectedDate: String,
    appRepository: AppRepository,
    syncViewModel: CalendarSyncViewModel,
    eventsViewModel: EventsViewModel,
    booxNoteRepository: BooxNoteRepository,
    booxNotesIntegration: BooxNotesIntegration,
    booxNoteIndexer: BooxNoteIndexer,
    eventRepository: CalendarEventRepository,
    taskRepository: TaskRepository,
    ocrRepository: UnifiedOcrRepository,
    ocrIdleProcessor: UnifiedOcrIdleProcessor,
    sourceFilter: CalendarSourceFilter,
    onTriggerBooxNotesScan: () -> Unit,
    onOpenCalendarAccounts: () -> Unit,
    onGoToTask: (taskId: String, date: String) -> Unit,
    onOpenQuickViewList: (date: String, tab: String) -> Unit,
    onNoteLinkChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    CalendarAiNoticeHost(onOpenAiSettings = { state.showAiSettingsState.value = true })
    AiKeyGateHost(
        aiSettingsOpen = state.showAiSettingsState.value,
        onOpenAiSettings = { state.showAiSettingsState.value = true }
    )

    overlayRegistry.Overlay("help", state.showHelpState.value) {
        HelpDialog(onDismiss = { state.showHelpState.value = false })
    }

    overlayRegistry.Overlay("about", state.showAboutState.value) {
        AboutDialog(onDismiss = { state.showAboutState.value = false })
    }

    overlayRegistry.Overlay("preferences", state.showPreferencesState.value) {
        val dateFormatSetting by remember { derivedStateOf { GlobalAppSettings.current.dateFormat } }
        val timeFormatSetting by remember { derivedStateOf { GlobalAppSettings.current.timeFormat } }
        val startTabSetting by remember { derivedStateOf { GlobalAppSettings.current.startTab } }
        val tipsEnabled by remember { derivedStateOf { GlobalAppSettings.current.tipsEnabled } }
        val tipInterval by remember { derivedStateOf { GlobalAppSettings.current.tipInterval } }
        val taskNotificationsEnabled by remember { derivedStateOf { GlobalAppSettings.current.taskNotificationsEnabled } }
        val eventNotificationsEnabled by remember { derivedStateOf { GlobalAppSettings.current.eventNotificationsEnabled } }
        val defaultTaskReminderHour by remember { derivedStateOf { GlobalAppSettings.current.defaultTaskReminderHour } }
        val defaultTaskReminderMinute by remember { derivedStateOf { GlobalAppSettings.current.defaultTaskReminderMinute } }
        val defaultEventReminderMinutes by remember { derivedStateOf { GlobalAppSettings.current.defaultEventReminderMinutes } }

        PreferencesDialog(
            onDismiss = { state.showPreferencesState.value = false },
            dateFormat = dateFormatSetting,
            onDateFormatChanged = { newValue ->
                appRepository.kvProxy.setAppSettings(GlobalAppSettings.current.copy(dateFormat = newValue))
                SetupChecklistStore.mark(context) { it.copy(preferencesReviewed = true) }
            },
            timeFormat = timeFormatSetting,
            onTimeFormatChanged = { newValue ->
                appRepository.kvProxy.setAppSettings(GlobalAppSettings.current.copy(timeFormat = newValue))
                SetupChecklistStore.mark(context) { it.copy(preferencesReviewed = true) }
            },
            startTab = startTabSetting,
            onStartTabChanged = { newValue ->
                appRepository.kvProxy.setAppSettings(GlobalAppSettings.current.copy(startTab = newValue))
                SetupChecklistStore.mark(context) { it.copy(preferencesReviewed = true) }
            },
            tipsEnabled = tipsEnabled,
            onTipsEnabledChanged = { newValue ->
                appRepository.kvProxy.setAppSettings(GlobalAppSettings.current.copy(tipsEnabled = newValue))
                SetupChecklistStore.mark(context) { it.copy(preferencesReviewed = true) }
            },
            tipInterval = tipInterval,
            onTipIntervalChanged = { newValue ->
                appRepository.kvProxy.setAppSettings(GlobalAppSettings.current.copy(tipInterval = newValue))
                if (tipsEnabled) TipsService.start()
                SetupChecklistStore.mark(context) { it.copy(preferencesReviewed = true) }
            },
            taskNotificationsEnabled = taskNotificationsEnabled,
            onTaskNotificationsEnabledChanged = { newValue ->
                val newSettings = GlobalAppSettings.current.copy(taskNotificationsEnabled = newValue)
                appRepository.kvProxy.setAppSettings(newSettings)
                scope.launch(Dispatchers.IO) {
                    appRepository.taskRepository.applyTaskNotificationsSetting(newSettings)
                }
            },
            eventNotificationsEnabled = eventNotificationsEnabled,
            onEventNotificationsEnabledChanged = { newValue ->
                val newSettings = GlobalAppSettings.current.copy(eventNotificationsEnabled = newValue)
                appRepository.kvProxy.setAppSettings(newSettings)
                scope.launch(Dispatchers.IO) {
                    appRepository.localEventRepository.applyEventNotificationsSetting(newSettings)
                }
            },
            defaultTaskReminderHour = defaultTaskReminderHour,
            defaultTaskReminderMinute = defaultTaskReminderMinute,
            onDefaultTaskReminderChanged = { hour, minute ->
                val newSettings = GlobalAppSettings.current.copy(
                    defaultTaskReminderHour = hour,
                    defaultTaskReminderMinute = minute
                )
                appRepository.kvProxy.setAppSettings(newSettings)
                if (newSettings.taskNotificationsEnabled) {
                    scope.launch(Dispatchers.IO) {
                        appRepository.taskRepository.scheduleNotificationsForExistingTasks(newSettings)
                    }
                }
            },
            defaultEventReminderMinutes = defaultEventReminderMinutes,
            onDefaultEventReminderChanged = { minutes ->
                val newSettings = GlobalAppSettings.current.copy(defaultEventReminderMinutes = minutes)
                appRepository.kvProxy.setAppSettings(newSettings)
                if (newSettings.eventNotificationsEnabled) {
                    scope.launch(Dispatchers.IO) {
                        appRepository.localEventRepository.scheduleNotificationsForUpcomingEvents(newSettings)
                    }
                }
            },
            onOpenAlarmSettings = { NotificationPermissionHelper.openExactAlarmSettings(context) },
            onOpenBatteryOptimizationSettings = { NotificationPermissionHelper.openBatteryOptimizationSettings(context) }
        )
    }

    overlayRegistry.Overlay("ai_settings", state.showAiSettingsState.value) {
        val aiEnabled by remember { derivedStateOf { GlobalAppSettings.current.aiEnabled } }
        AiSettingsDialog(
            onDismiss = { state.showAiSettingsState.value = false },
            aiEnabled = aiEnabled,
            onAiEnabledChanged = { newValue ->
                appRepository.kvProxy.setAppSettings(GlobalAppSettings.current.copy(aiEnabled = newValue))
                SetupChecklistStore.mark(context) { it.copy(aiSettingsReviewed = true) }
            }
        )
    }

    overlayRegistry.Overlay("voice_settings", state.showVoiceSettingsState.value) {
        val transcriptionService = remember { TranscriptionService(context, AudioRecorder(context)) }
        VoiceSettingsDialog(
            hasGoogleSearchApp = transcriptionService.isSpeechRecognitionAvailable(),
            currentMode = GlobalAppSettings.current.voiceTextMode,
            onSelectMode = { mode ->
                appRepository.kvProxy.setAppSettings(GlobalAppSettings.current.copy(voiceTextMode = mode))
                state.showVoiceSettingsState.value = false
            },
            onInstallGoogleSearch = { state.showVoiceSettingsState.value = false },
            onDismiss = { state.showVoiceSettingsState.value = false }
        )
    }

    overlayRegistry.Overlay("data_backup", state.showDataBackupState.value) {
        DataBackupDialog(
            onDismiss = { state.showDataBackupState.value = false },
            onClearSyncedDataOnly = {
                state.showDataBackupState.value = false
                syncViewModel.clearSyncedData(performFreshSync = false)
            },
            onClearSyncedDataAndResync = {
                state.showDataBackupState.value = false
                syncViewModel.clearSyncedData(performFreshSync = true)
            },
            onWipeAllData = {
                state.showDataBackupState.value = false
                scope.launch(Dispatchers.IO) {
                    if (!AppDataWipe.wipe(context)) {
                        withContext(Dispatchers.Main) {
                            SnackState.globalSnackFlow.tryEmit(
                                SnackConf(
                                    text = "Couldn't erase automatically. In App info, tap Storage, then Clear storage.",
                                    duration = SnackConf.DURATION_ERROR
                                )
                            )
                            NotificationPermissionHelper.openAppSettings(context)
                        }
                    }
                }
            }
        )
    }

    overlayRegistry.Overlay("search", state.showSearchState.value) {
        SearchDialog(
            ocrRepository = ocrRepository,
            onNavigateToDate = { date ->
                MainScreenRequests.request(MainScreenRequest.ShowDay(date))
                state.showSearchState.value = false
            },
            onOpenTask = { taskId, date ->
                state.showSearchState.value = false
                onGoToTask(taskId, date)
            },
            onOpenBooxNote = { uniqueId ->
                BooxNotesPlaces.openNote(uniqueId)
                state.showSearchState.value = false
            },
            onDismiss = { state.showSearchState.value = false }
        )
    }

    // Collect ocrProcessingState ONLY while Boox Notes setup or notes dialog is actually open (M8(3))
    if (BooxNotesPlaces.setupOpen) {
        val ocrProcessingState by ocrIdleProcessor.processingState.collectAsState()
        BooxNotesSettingsDialog(
            onDismiss = { BooxNotesPlaces.setupOpen = false },
            booxNoteRepository = booxNoteRepository,
            eventRepository = eventRepository,
            onScanNeeded = onTriggerBooxNotesScan,
            onOpenCalendarAccounts = onOpenCalendarAccounts,
            onIndexNow = { scope.launch { ocrIdleProcessor.processNow() } },
            onClearIndex = {
                scope.launch(Dispatchers.IO) {
                    ocrRepository.clearAll()
                    UnifiedOcrScheduler(context).clearQueue()
                    OcrQuotaManager(context).resetQuotaAfterClear()
                }
            },
            onOcrSettingsChanged = { newOcrSettings ->
                val newSettings = GlobalAppSettings.current.copy(ocrSettings = newOcrSettings)
                GlobalAppSettings.update(newSettings)
                scope.launch(Dispatchers.IO) {
                    appRepository.kvProxy.setAppSettings(newSettings)
                }
            },
            ocrProcessingState = ocrProcessingState,
            ocrRepository = ocrRepository
        )
    }

    if (BooxNotesPlaces.notesOpen) {
        val ocrProcessingState by ocrIdleProcessor.processingState.collectAsState()
        BooxNotesDialog(
            date = selectedDate,
            booxNoteRepository = booxNoteRepository,
            booxNotesIntegration = booxNotesIntegration,
            booxNoteIndexer = booxNoteIndexer,
            ocrRepository = if (GlobalAppSettings.current.ocrSettings.enabled) ocrRepository else null,
            isOcrIndexingInProgress = GlobalAppSettings.isBooxNotesScanning || (ocrProcessingState?.isProcessing == true),
            eventRepository = eventRepository,
            onDismiss = { BooxNotesPlaces.closeNotes() },
            onLinkChanged = onNoteLinkChanged,
            sourceFilter = sourceFilter,
            onOpenCalendarAccounts = {
                BooxNotesPlaces.closeNotes()
                onOpenCalendarAccounts()
            },
            onOpenHelp = {
                BooxNotesPlaces.closeNotes()
                state.showHelpState.value = true
            }
        )
    }

    // Day Quick View Popup (long-press on calendar day)
    var quickViewData by remember { mutableStateOf<DayQuickViewData?>(null) }
    val requestedQuickViewDate = state.quickViewDateState.value
    LaunchedEffect(requestedQuickViewDate) {
        if (requestedQuickViewDate == null) {
            quickViewData = null
            return@LaunchedEffect
        }
        quickViewData = withContext(Dispatchers.IO) {
            val notesDeferred = async { booxNoteRepository.getNotesForDate(requestedQuickViewDate) }
            val eventsDeferred = async { eventsViewModel.getEventCountForDate(requestedQuickViewDate).first() }
            val tasksDeferred = async { taskRepository.getTasksDueOnDateCount(requestedQuickViewDate).first() }

            val displayDate = try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                val parsed = inputFormat.parse(requestedQuickViewDate)
                if (parsed != null) outputFormat.format(parsed) else requestedQuickViewDate
            } catch (_: Exception) {
                requestedQuickViewDate
            }

            val notes = notesDeferred.await().filter { it.pageCount > 0 }
            val notesWithEvents = notes.map { note ->
                async {
                    val link = booxNoteRepository.getLinkForNote(note.uniqueId)
                    val linkedEvent = link?.linkedEventId?.let { eventId ->
                        eventRepository.getEventById(eventId)
                    }
                    BooxNoteWithEvent(
                        note = note,
                        linkedEvent = linkedEvent,
                        isManualLink = link?.isManualLink ?: false
                    )
                }
            }.awaitAll()

            DayQuickViewData(
                date = requestedQuickViewDate,
                displayDate = displayDate,
                booxNotes = notesWithEvents,
                eventCount = eventsDeferred.await(),
                tasksDueCount = tasksDeferred.await()
            )
        }
    }

    val loadedQuickView = quickViewData
    overlayRegistry.Overlay("day_quick_view", requestedQuickViewDate != null && loadedQuickView != null) {
        val data = loadedQuickView ?: return@Overlay
        DayQuickViewPopup(
            data = data,
            onNavigateToEvents = {
                state.quickViewDateState.value = null
                onOpenQuickViewList(data.date, "events")
            },
            onNavigateToTasksDue = {
                state.quickViewDateState.value = null
                onOpenQuickViewList(data.date, "due")
            },
            onOpenBooxNote = { uniqueId, title ->
                booxNotesIntegration.openNote(uniqueId, title)
                state.quickViewDateState.value = null
            },
            onLinkBooxNote = { noteId, noteDate ->
                BooxNotesPlaces.openNote(noteId, noteDate)
                state.quickViewDateState.value = null
            },
            onDismiss = { state.quickViewDateState.value = null }
        )
    }
}

/**
 * Isolated OCR startup maintenance + idle indexing loop (extracted from MainScreen root).
 * Returns a stable `onUserActivity` callback that aborts background OCR immediately on touch.
 */
@Composable
internal fun rememberOcrIdleUserActivityHandler(
    ocrRepository: UnifiedOcrRepository,
    ocrIdleProcessor: UnifiedOcrIdleProcessor,
    booxNoteIndexer: BooxNoteIndexer
): () -> Unit {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var lastUserActivityMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        ocrIdleProcessor.booxNoteIndexer = booxNoteIndexer
        withContext(Dispatchers.IO) {
            val recoveredCount = ocrRepository.recoverFromCrash()
            if (recoveredCount > 0) {
                Log.i(TAG, "OCR crash recovery: reset $recoveredCount stale pages")
            }
            ocrRepository.fixEmptyPagesFirstIndexedAt()
            ocrRepository.releaseNetworkQuarantines()
        }
    }

    val ocrEnabled by remember { derivedStateOf { GlobalAppSettings.current.ocrSettings.enabled } }
    LaunchedEffect(ocrEnabled) {
        if (!ocrEnabled) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(2000L)
                if (!powerManager.isInteractive) continue
                val lastActivityMs = maxOf(lastUserActivityMs, DrawCanvas.lastStylusActivityMs)
                val idleMs = System.currentTimeMillis() - lastActivityMs
                if (idleMs >= UnifiedOcrIdleProcessor.DEFAULT_IDLE_THRESHOLD_MS && !ocrIdleProcessor.isProcessing()) {
                    ocrIdleProcessor.tryProcessIfIdle(lastActivityMs)
                }
            }
        }
    }

    return remember(ocrIdleProcessor) {
        {
            lastUserActivityMs = System.currentTimeMillis()
            if (ocrIdleProcessor.isProcessing()) {
                ocrIdleProcessor.abortNow()
            }
        }
    }
}
