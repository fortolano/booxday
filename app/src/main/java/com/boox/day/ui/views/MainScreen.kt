package com.boox.day.ui.views

import android.app.Activity
import android.content.Intent
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.boox.day.PERF_LOGS
import com.boox.day.R
import androidx.compose.ui.res.painterResource
import com.boox.day.TAG
import com.boox.day.BuildConfig
import com.boox.day.data.AppRepository
import com.boox.day.data.PageDataManager
import com.boox.day.booxnotes.BooxNoteIndexer
import com.boox.day.data.db.BooxNoteIndex
import com.boox.day.booxnotes.BooxNotesIntegration
import com.boox.day.ocr.semantic.SemanticGeniusService
import com.boox.day.data.db.BooxNoteLink
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.ocr.UnifiedOcrIdleProcessor
import com.boox.day.ocr.TaskOcrService
import com.boox.day.ocr.TaskTextIndexer
import com.boox.day.data.db.BooxNoteRepository
import com.boox.day.data.db.TaskRepository
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.CalendarEventRepository
import com.boox.day.data.db.LocalCalendar
import com.boox.day.data.db.LocalCalendarRepository
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.LocalEventRepository
import com.boox.day.data.db.SyncStatus
import com.boox.day.data.db.DayPageRepository
import com.boox.day.data.CacheWarmupCoordinator
import com.boox.day.data.MemoryPressureCallback
import com.boox.day.notifications.NotificationPermissionHelper
import android.content.ComponentCallbacks2
import android.os.Build
import android.os.PowerManager
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskPageInfo
import com.boox.day.data.db.DueDateResult
import com.boox.day.data.db.CopyTasksResult
import com.boox.day.data.db.DueTasksData
import com.boox.day.data.db.SummaryCache
import com.boox.day.data.db.SummaryCacheRepository
import com.boox.day.sync.EventOperationResult
import com.boox.day.sync.MultiAccountSyncManager
import com.boox.day.sync.MultiAccountSyncViewModel
import com.boox.day.sync.CalendarSyncManager
import com.boox.day.sync.GoogleAuthManager
import com.boox.day.sync.outlook.OutlookAuthManager
import com.boox.day.sync.outlook.OutlookCalendarService
import com.boox.day.sync.provider.GoogleCalendarProvider
import com.boox.day.sync.provider.OutlookCalendarProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.model.AccountType
import com.boox.day.data.model.ProviderId
import com.boox.day.ui.dialogs.AddEditEventDialog
import com.boox.day.ui.dialogs.BooxNotesDialog
import com.boox.day.ui.dialogs.CalendarSyncSettingsDialog
import com.boox.day.ui.dialogs.AddCalendarDialog
import com.boox.day.ui.dialogs.CalendarSettingsDialog
import com.boox.day.ui.dialogs.CloudAccountSummary
import com.boox.day.ui.dialogs.HelpDialog
import com.boox.day.ui.dialogs.EventDetailsDialog
import com.boox.day.ui.dialogs.AboutDialog
import com.boox.day.ui.dialogs.PreferencesDialog
import com.boox.day.ui.dialogs.AiSettingsDialog
import com.boox.day.ui.dialogs.DataBackupDialog
import com.boox.day.ui.dialogs.SearchDialog
import com.boox.day.ui.dialogs.NoTaskSlotsDialog
import com.boox.day.tasks.TaskSlotCalculator
import com.boox.day.ui.dialogs.PartialInsertDialog
import com.boox.day.ui.dialogs.ShowSimpleConfirmationDialog
import com.boox.day.ui.dialogs.SyncSplashDialog
import com.boox.day.ui.dialogs.VoiceEventDialog
import com.boox.day.ui.dialogs.VoiceSettingsDialog
import com.boox.day.ui.dialogs.WelcomePopup
import com.boox.day.speech.TranscriptionService
import com.boox.day.speech.AudioRecorder
import com.boox.day.data.model.ParsedEventData
import com.boox.day.editor.ui.VoiceToTaskDialog
import com.boox.day.editor.ui.TaskPreviewDialog
import com.boox.day.editor.ui.TaskToCreate
import com.boox.day.editor.ui.SortPreviewDialog
import com.boox.day.editor.ui.SortProcessingState
import com.boox.day.editor.ui.SortScopePopup
import com.boox.day.editor.ui.SortScope
import com.boox.day.editor.ui.PlacedSortedTask
import com.boox.day.editor.ui.DailySummaryDialog
import com.boox.day.editor.ui.WeeklySummaryDialog
import com.boox.day.editor.ui.WeeklySummaryCacheInfo
import com.boox.day.editor.ui.WeeklySummaryProcessingState
import com.boox.day.editor.ui.DailySummaryProcessingState
import com.boox.day.editor.ui.SmartSuggestionsDialog
import com.boox.day.editor.ui.SmartSuggestionsProcessingState
import com.boox.day.editor.ui.TaskBreakdownDialog
import com.boox.day.editor.ui.TaskBreakdownProcessingState
import com.boox.day.ai.TaskAiService
import com.boox.day.ai.MemoToTasksService
import com.boox.day.ai.MemoToTasksResult
import com.boox.day.ai.MemoToTasksProcessingState
import com.boox.day.ai.SuggestionContextBuilder
import com.boox.day.ai.intelligence.IntelligenceEngine
import com.boox.day.data.ai.IntelligenceFeature
import com.boox.day.data.ai.ScoredContext
import com.boox.day.data.ai.AiPreferencesPromptManager
import com.boox.day.data.ai.ProposalAction
import com.boox.day.editor.ui.AiPreferencesInvitePopup
import com.boox.day.ui.dialogs.AiScope
import com.boox.day.ui.dialogs.MemoToTasksDialog
import com.boox.day.tips.TipsService
import java.time.LocalDate
import java.time.temporal.WeekFields
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.datastore.SetupChecklist
import com.boox.day.viewmodel.CalendarSyncViewModel
import com.boox.day.editor.utils.refreshScreen
import com.boox.day.editor.utils.loadPreview
import com.boox.day.viewmodel.EventsViewModel
import com.boox.day.editor.DrawCanvas
import com.boox.day.editor.TasksCanvasRefresh
import com.boox.day.editor.EditorControlTower
import com.boox.day.editor.EditorTabManager
import com.boox.day.editor.PageView
import com.boox.day.editor.state.EditorState
import com.boox.day.editor.state.History
import com.boox.day.editor.ui.EditorGestureReceiver
import com.boox.day.editor.ui.EditorSurface
// ScrollIndicator imports removed - infinite scroll is disabled
import com.boox.day.editor.ui.SelectedBitmap
import com.boox.day.editor.ui.TextBoxOverlay
import com.boox.day.editor.ui.TASKS_CONTROL_COLUMN_WIDTH
import com.boox.day.editor.ui.TASKS_TOOLBAR_OFFSET
import com.boox.day.editor.ui.TasksControlColumn
import com.boox.day.editor.ui.TasksLinesOverlay
import com.boox.day.editor.ui.TaskRowHighlight
import com.boox.day.editor.ui.DeletePageConfirmDialog
import com.boox.day.editor.utils.MoveTaskError
import com.boox.day.editor.utils.SlotAllocator
import com.boox.day.editor.utils.calculateRowBounds
import com.boox.day.editor.utils.getDominantRowIndex
import com.boox.day.editor.utils.TaskCrossOut
import com.boox.day.editor.ui.StartDayReviewData
import com.boox.day.editor.ui.StartDayReviewLoader
import com.boox.day.editor.utils.hasIntentionalStrokeContent
import com.boox.day.ui.LocalSnackContext
import com.boox.day.ui.SnackConf
import com.boox.day.ui.SnackState
import com.boox.day.ui.showHint
import com.boox.day.ui.calendar.CalendarBadges
import com.boox.day.ui.calendar.CalendarGrid
import com.boox.day.ui.calendar.CalendarView
import com.boox.day.ui.calendar.DayHeader
import com.boox.day.ui.calendar.DayOfWeekHeader
import com.boox.day.ui.calendar.BooxNoteWithEvent
import com.boox.day.ui.calendar.DayQuickViewData
import com.boox.day.ui.calendar.DayQuickViewPopup
import com.boox.day.ui.calendar.MonthHeaderWithToolbar
import com.boox.day.ui.calendar.CompactCalendarRows
import com.boox.day.ui.calendar.CompactDayListPopup
import com.boox.day.ui.calendar.CompactMonthPopup
import com.boox.day.ui.calendar.parseIsoDateOrNull
import androidx.compose.runtime.SideEffect
import com.boox.day.ui.theme.LayoutProfiles
import com.boox.day.data.db.TaskDueDates
import androidx.compose.material.icons.filled.Add
import com.boox.day.ui.convertDpToPixel
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.components.CalendarSourceFilterIcons
import com.boox.day.ui.state.CalendarSource
import com.boox.day.ui.state.CalendarSourceFilter
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.theme.InkaTheme
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Enum for Memos/Tasks tab selection.
 * Public visibility for use in EditorTabManager and other components.
 */
enum class EditorTab {
    Memos,
    Tasks
}

private data class WeeklySummarySnapshot(
    val tasksByDate: Map<String, List<Task>>,
    val allTasks: List<Task>,
    val contentHash: String,
    val taskCount: Int,
    val completedCount: Int,
    val lastUpdatedAt: Long?
)

private data class MemoToTasksInsertPlan(
    val tasksToInsert: List<com.boox.day.ai.ConvertedTask>,
    val totalSelectedCount: Int,
    val totalAvailableCount: Int,
    val singleDateHasNoSlots: Boolean = false
)

/**
 * Main screen for BooxDay - combines calendar view with drawing canvas
 * Layout: Top Toolbar -> Calendar (40%) -> Drawing Toolbar -> Drawing Canvas (60%)
 */

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val snackManager = LocalSnackContext.current
    val scope = rememberCoroutineScope()

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val today = dateFormat.format(Calendar.getInstance().time)

    // ViewModels for Google Calendar sync
    val syncViewModel: CalendarSyncViewModel = viewModel()
    val eventsViewModel: EventsViewModel = viewModel()

    // Multi-provider sync manager and ViewModel
    val (multiAccountSyncManager, multiAccountSyncViewModel) = remember {
        val db = AppDatabase.getDatabase(context)
        val accountDao = db.connectedAccountDao()
        val selectionDao = db.calendarSelectionDao()
        val eventRepo = CalendarEventRepository(context)

        // Auth managers
        val googleAuthManager = GoogleAuthManager(context)
        val outlookAuthManager = OutlookAuthManager(context)
        val calendarSyncManager = CalendarSyncManager(context, googleAuthManager, eventRepo)
        val outlookCalendarService = OutlookCalendarService(outlookAuthManager)

        // Providers
        val googleProvider = GoogleCalendarProvider(context, googleAuthManager, calendarSyncManager, accountDao, eventRepo)
        val outlookProvider = OutlookCalendarProvider(context, outlookAuthManager, outlookCalendarService, accountDao)

        // Multi-account sync manager
        val syncManager = MultiAccountSyncManager(context, googleProvider, outlookProvider, accountDao, selectionDao, eventRepo)

        // ViewModel
        val viewModel = MultiAccountSyncViewModel(syncManager, googleAuthManager, outlookAuthManager)
        syncManager to viewModel
    }

    // Activity reference for Outlook MSAL sign-in
    val activity = context as? Activity

    // Sync state
    val isSignedIn by syncViewModel.isSignedIn.collectAsState()
    val userEmail by syncViewModel.userEmail.collectAsState()
    val syncInProgress by syncViewModel.syncInProgress.collectAsState()
    val lastSyncTime by syncViewModel.lastSyncTime.collectAsState()
    val lastSyncMessage by syncViewModel.lastSyncMessage.collectAsState()
    val autoSyncEnabled by syncViewModel.autoSyncEnabled.collectAsState()
    val syncFrequency by syncViewModel.syncFrequency.collectAsState()
    val availableCalendars by syncViewModel.availableCalendars.collectAsState()
    val selectedCalendars by syncViewModel.selectedCalendars.collectAsState()

    // Events state
    val operationInProgress by eventsViewModel.operationInProgress.collectAsState()

    // Multi-account sync state (for connected account detection)
    val multiAccountUiState by multiAccountSyncViewModel.uiState.collectAsState()
    val writableCalendars by multiAccountSyncManager.allWritableCalendars.collectAsState()

    // Source filter state
    val sourceFilter by eventsViewModel.sourceFilter.collectAsState()

    // Derived connected account flags
    val hasGoogleAccount = multiAccountUiState.googleAccounts.isNotEmpty()
    val hasOutlookAccount = multiAccountUiState.outlookAccounts.isNotEmpty()
    val hasAnyExternalAccount = hasGoogleAccount || hasOutlookAccount

    LaunchedEffect(hasGoogleAccount, hasOutlookAccount) {
        if (hasGoogleAccount) {
            multiAccountSyncManager.getWritableCalendars(ProviderId.GOOGLE)
        }
        if (hasOutlookAccount) {
            multiAccountSyncManager.getWritableCalendars(ProviderId.OUTLOOK)
        }
    }

    // Dialog states
    var showCalendarSettingsDialog by remember { mutableStateOf(false) }
    var showAddEventDialog by remember { mutableStateOf(false) }
    var showEventDetailsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showBooxNotesDialog by remember { mutableStateOf(false) }
    var autoOpenBulkWizard by remember { mutableStateOf(false) }  // When true, opens Bulk Smart Link wizard directly
    var showBooxNotesSettingsDialog by remember { mutableStateOf(false) }
    var showVoiceEventDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showAiPreferencesDialog by remember { mutableStateOf(false) }
    var showAiPreferencesInvitePopup by remember { mutableStateOf(false) }
    // Dec 2025 settings redesign - new dialogs
    var showPreferencesDialog by remember { mutableStateOf(false) }
    var showAiSettingsDialog by remember { mutableStateOf(false) }
    var showVoiceSettingsDialog by remember { mutableStateOf(false) }
    var showDataBackupDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }

    // Initialize TaskOcrService for Digital Ink recognition
    LaunchedEffect(Unit) {
        TaskOcrService.initialize(context, scope)
    }

    // Multi-provider calendar sync dialog
    var showMultiProviderSyncDialog by remember { mutableStateOf(false) }

    // AI Preferences prompt manager (for progressive backoff prompts)
    val aiPreferencesPromptManager = remember { AiPreferencesPromptManager(context) }

    // Voice input parsed event data (for pre-filling AddEditEventDialog)
    var parsedEventData by remember { mutableStateOf<ParsedEventData?>(null) }

    // Get settings (reactive to changes)
    var firstDayOfWeek by remember { mutableStateOf(GlobalAppSettings.current.firstDayOfWeek) }
    var dateFormatSetting by remember { mutableStateOf(GlobalAppSettings.current.dateFormat) }
    var timeFormatSetting by remember { mutableStateOf(GlobalAppSettings.current.timeFormat) }
    var showWeekNumbers by remember { mutableStateOf(GlobalAppSettings.current.showWeekNumbers) }
    var startTabSetting by remember { mutableStateOf(GlobalAppSettings.current.startTab) }
    var tipsEnabled by remember { mutableStateOf(GlobalAppSettings.current.tipsEnabled) }
    var tipInterval by remember { mutableStateOf(GlobalAppSettings.current.tipInterval) }
    var aiEnabled by remember { mutableStateOf(GlobalAppSettings.current.aiEnabled) }
    var booxNotesEnabled by remember { mutableStateOf(true) }
    // Notification settings - decoupled task and event toggles
    var taskNotificationsEnabled by remember { mutableStateOf(GlobalAppSettings.current.taskNotificationsEnabled) }
    var eventNotificationsEnabled by remember { mutableStateOf(GlobalAppSettings.current.eventNotificationsEnabled) }
    var defaultTaskReminderHour by remember { mutableStateOf(GlobalAppSettings.current.defaultTaskReminderHour) }
    var defaultTaskReminderMinute by remember { mutableStateOf(GlobalAppSettings.current.defaultTaskReminderMinute) }
    var defaultEventReminderMinutes by remember { mutableStateOf(GlobalAppSettings.current.defaultEventReminderMinutes) }
    var syncModeSetting by remember { mutableStateOf(GlobalAppSettings.current.syncMode) }
    var defaultSyncEnabled by remember { mutableStateOf(GlobalAppSettings.current.defaultSyncEnabled) }

    // First-run onboarding state (Play Store launch - Jan 2026)
    var onboardingDismissedAt by remember { mutableStateOf(GlobalAppSettings.current.onboardingDismissedAt) }
    var setupChecklist by remember { mutableStateOf(GlobalAppSettings.current.setupChecklist) }

    // Keep startup raw drawing gate in sync with welcome popup visibility.
    LaunchedEffect(onboardingDismissedAt) {
        DrawCanvas.setWelcomePopupVisible(onboardingDismissedAt == 0L)
    }

    var selectedEventForDetails by remember { mutableStateOf<CalendarEvent?>(null) }
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var linkedBooxNotesForEvent by remember { mutableStateOf<List<BooxNoteIndex>>(emptyList()) }

    // Local calendars state (for Standalone mode)
    var localCalendars by remember { mutableStateOf<List<LocalCalendar>>(emptyList()) }
    var showAddCalendarDialog by remember { mutableStateOf(false) }
    var syncedEventCountForSwitch by remember { mutableStateOf(0) }
    var memoRefreshTrigger by remember { mutableIntStateOf(0) }

    // Expanded events view state
    var isEventsExpanded by remember { mutableStateOf(false) }
    var isEventsPinned by remember { mutableStateOf(false) }

    // Events panel tab state: "events" or "due"
    var selectedEventsTab by remember { mutableStateOf("events") }

    // OAuth sign-in launcher
    // NOTE: Google Sign-In can return different result codes even for successful sign-ins.
    // When the user goes through the consent flow (selecting email, granting permissions),
    // the result might not always be RESULT_OK. We should always attempt to process the
    // result and let handleSignInResult determine if it was successful based on the intent data.
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Always try to handle the result - GoogleSignIn.getSignedInAccountFromIntent
        // will throw an appropriate exception if sign-in actually failed
        Log.d(TAG, "Sign-in result: resultCode=${result.resultCode}, data=${result.data != null}")
        syncViewModel.handleSignInResult(result.data)
    }

    // Multi-provider Google sign-in launcher (for new CalendarSyncSettingsDialog)
    val multiProviderSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Multi-provider sign-in result: resultCode=${result.resultCode}, data=${result.data != null}")
        multiAccountSyncViewModel.handleGoogleSignInResult(result.data, result.resultCode)
    }

    // Notification permission launcher (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Notification permission granted via in-app launcher - triggering scheduling")

            // Trigger scheduling immediately for existing tasks/events
            scope.launch(Dispatchers.IO) {
                val settings = GlobalAppSettings.current

                if (settings.taskNotificationsEnabled) {
                    try {
                        TaskRepository(context).scheduleNotificationsForExistingTasks()
                        Log.d(TAG, "Scheduled notifications for existing tasks")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to schedule task notifications: ${e.message}")
                    }
                }

                if (settings.eventNotificationsEnabled) {
                    try {
                        LocalEventRepository(context).scheduleNotificationsForUpcomingEvents()
                        Log.d(TAG, "Scheduled notifications for upcoming events")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to schedule event notifications: ${e.message}")
                    }
                }
            }
        } else {
            Log.d(TAG, "Notification permission denied")
        }
    }

    // State for selected date - starts with today
    var selectedDate by remember { mutableStateOf(today) }

    // PERFORMANCE: Use count-specific flow for badge (avoids full list collection)
    // eventsPanelEventsCount is now an Int directly, not List.size
    // Plan §13.4 (Oct 2026): one Flow per day — building them inline re-subscribed and
    // re-queried on every recomposition
    val dueFlowToday = java.time.LocalDate.now().toString()
    val eventsPanelEventsCount by remember(selectedDate) {
        eventsViewModel.getEventCountForDate(selectedDate)
    }.collectAsState(initial = 0)
    val eventsPanelDueCount by remember(selectedDate, dueFlowToday) {
        eventsViewModel.getDueTaskCount(selectedDate)
    }.collectAsState(initial = 0)

    // Repository for day-page mapping (use appRepository.dayPageRepository for shared cache)
    val appRepository = remember { AppRepository(context) }
    val booxNoteRepository = remember { BooxNoteRepository(context) }
    val taskRepository = remember { TaskRepository(context) }
    val booxNotesIntegration = remember { BooxNotesIntegration(context) }
    val eventRepository = remember { CalendarEventRepository(context) }
    val localCalendarRepository = remember { LocalCalendarRepository(context) }
    val localEventRepository = remember { LocalEventRepository(context) }

    // Boox Notes baseline is always enabled. Persist compatibility flag as true.
    LaunchedEffect(Unit) {
        if (!GlobalAppSettings.current.booxNotesEnabled || !booxNotesEnabled) {
            booxNotesEnabled = true
            val newSettings = GlobalAppSettings.current.copy(booxNotesEnabled = true)
            GlobalAppSettings.update(newSettings)
            withContext(Dispatchers.IO) {
                appRepository.kvProxy.setAppSettings(newSettings)
            }
        }
    }

    // Pending sync count for toolbar indicator (local events waiting to sync to cloud)
    val pendingSyncCount by localEventRepository.observePendingSyncCount().collectAsState(initial = 0)
    val summaryCacheRepository = remember { SummaryCacheRepository(context) }
    // Unified OCR repository for search (all content types)
    val ocrRepository = remember { UnifiedOcrRepository(context) }
    // Unified OCR idle processor - triggers OCR during foreground idle time
    val ocrIdleProcessor = remember { UnifiedOcrIdleProcessor(context) }
    val semanticGeniusService = remember { SemanticGeniusService(context) }
    // Initialize TaskTextIndexer singleton - enables typed text indexing for Tasks
    val taskTextIndexer = remember { TaskTextIndexer.getInstance(context, scope) }

    // Helper to mark a setup checklist item as complete (clears badge)
    // Used by settings dialogs to clear their respective badges when user interacts
    val markSetupComplete: ((SetupChecklist) -> SetupChecklist) -> Unit = { update ->
        val newChecklist = update(setupChecklist)
        if (newChecklist != setupChecklist) {
            setupChecklist = newChecklist
            val newSettings = GlobalAppSettings.current.copy(setupChecklist = newChecklist)
            GlobalAppSettings.update(newSettings)
            scope.launch(Dispatchers.IO) {
                appRepository.kvProxy.setAppSettings(newSettings)
            }
        }
    }

    // OCR startup fixes - crash recovery and bug fixes
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // Crash recovery - reset any pages stuck in IN_PROGRESS status from previous session
            val recoveredCount = ocrRepository.recoverFromCrash()
            if (recoveredCount > 0) {
                Log.i(TAG, "OCR crash recovery: reset $recoveredCount stale pages")
            }

            // BUG FIX (Jan 2026): Reset firstIndexedAt for EMPTY pages so they can be
            // properly counted as NEW when content is later added
            ocrRepository.fixEmptyPagesFirstIndexedAt()

            // Oct 2026: pages read while offline were quarantined for good; read them again
            ocrRepository.releaseNetworkQuarantines()
        }
    }

    val booxNoteIndexer = remember { BooxNoteIndexer(context, booxNotesIntegration, booxNoteRepository, eventRepository) }
    val cacheWarmupCoordinator = remember { CacheWarmupCoordinator(appRepository.dayPageRepository, localEventRepository, eventRepository, taskRepository, booxNoteRepository) }

    LaunchedEffect(semanticGeniusService) {
        booxNoteIndexer.semanticGeniusService = semanticGeniusService
    }
    LaunchedEffect(Unit) {
        ocrIdleProcessor.booxNoteIndexer = booxNoteIndexer
    }

    // Register memory pressure callback to clear repository caches during low memory
    DisposableEffect(Unit) {
        val callback = MemoryPressureCallback { level ->
            @Suppress("DEPRECATION")
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                Log.i(TAG, "Memory pressure level $level: clearing repository caches")
                appRepository.dayPageRepository.invalidatePageCache()
                localEventRepository.invalidateCache()
                taskRepository.invalidateTaskCache()
                booxNoteRepository.invalidateLinksCache()
                PageDataManager.clearPreviewCache()
            }
        }
        PageDataManager.registerMemoryPressureCallback(callback)
        onDispose {
            PageDataManager.unregisterMemoryPressureCallback(callback)
        }
    }

    // Get current page ID for selected date
    var currentPageId by remember { mutableStateOf<String?>(null) }
    var isMemosPageLoading by remember { mutableStateOf(false) }

    // OPTION C: EditorTabManager for single toolbar architecture
    // PERFORMANCE: Centralized state management eliminates dual-toolbar sync overhead
    // PERSISTENCE: Restores last active tab from settings (lastActiveTab)
    val kvProxy = remember { com.boox.day.data.db.KvProxy(context) }
    val tabManager = remember { EditorTabManager(kvProxy, scope) }

    // Observe current tab for canvas visibility
    val currentEditorTab by tabManager.currentTab.collectAsState()

    // Initialize legacy StateFlow for backwards compatibility
    LaunchedEffect(Unit) {
        DrawCanvas.memoModeChanged.value = (currentEditorTab == EditorTab.Memos)
    }

    // Load local calendars on startup and ensure Default exists
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            localCalendarRepository.ensureDefaultExists()
            localCalendars = localCalendarRepository.getAll()
        }
    }

    // Load connected accounts on startup (for source filter icons)
    LaunchedEffect(Unit) {
        multiAccountSyncViewModel.loadAccounts()
    }

    // Startup auto-sync for multi-account system.
    // Fires once when hasAnyExternalAccount becomes true after loadAccounts() completes.
    // Replaces legacy CalendarSyncViewModel startup sync for users with connected accounts.
    LaunchedEffect(hasAnyExternalAccount) {
        if (hasAnyExternalAccount) {
            multiAccountSyncViewModel.syncAllAccounts()
        }
    }

    // Clean up legacy events with empty accountId on startup.
    // These are invisible to the filtered grid query (INNER JOIN calendar_selections)
    // but were counted by the old unfiltered badge query, causing badge/grid mismatch.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val db = com.boox.day.data.db.AppDatabase.getDatabase(context)
            val cleaned = db.calendarEventDao().deleteLegacyEvents()
            if (cleaned > 0) {
                Log.i(TAG, "Cleaned up $cleaned legacy events with empty accountId")
            }
        }
    }

    // Start/stop TipsService based on setting
    LaunchedEffect(tipsEnabled) {
        if (tipsEnabled) {
            TipsService.start()
        } else {
            TipsService.stop()
        }
    }

    // Update TipsService context when tab changes
    LaunchedEffect(currentEditorTab) {
        TipsService.setCurrentTab(currentEditorTab)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // OCR IDLE PROCESSING
    // Triggers OCR indexing during idle periods (user not touching screen for 5s)
    // INSTANT ABORT: Any user interaction aborts processing immediately
    // ═══════════════════════════════════════════════════════════════════════════
    var lastUserActivityMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val ocrProcessingState by ocrIdleProcessor.processingState.collectAsState()

    // Track user activity - reset on any interaction
    val onUserActivity: () -> Unit = remember {
        {
            lastUserActivityMs = System.currentTimeMillis()
            // Abort OCR immediately on any user interaction
            if (ocrIdleProcessor.isProcessing()) {
                ocrIdleProcessor.abortNow()
            }
        }
    }

    // Idle detection loop - checks every 2 seconds if user has been idle for 5+ seconds
    // BATTERY FIX: Only runs when app is RESUMED and screen is interactive
    // FIX: Uses repeatOnLifecycle to properly restart polling when lifecycle enters RESUMED
    val ocrLifecycleOwner = LocalLifecycleOwner.current
    val powerManager = remember { context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager }

    LaunchedEffect(GlobalAppSettings.current.ocrSettings.enabled) {
        if (!GlobalAppSettings.current.ocrSettings.enabled) {
            return@LaunchedEffect
        }

        // repeatOnLifecycle automatically restarts when lifecycle enters RESUMED
        // and cancels when lifecycle leaves RESUMED - no timing race possible
        ocrLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                kotlinx.coroutines.delay(2000L)  // Check every 2 seconds

                // Skip if screen is off - prevent background battery drain
                if (!powerManager.isInteractive) continue

                // Pen strokes count as activity too (Oct 2026): lastUserActivityMs only sees taps on the
                // calendar area, so a memo written with the pen alone waited out the 10-minute rest
                val lastActivityMs = maxOf(lastUserActivityMs, DrawCanvas.lastStylusActivityMs)
                val idleMs = System.currentTimeMillis() - lastActivityMs
                if (idleMs >= UnifiedOcrIdleProcessor.DEFAULT_IDLE_THRESHOLD_MS && !ocrIdleProcessor.isProcessing()) {
                    // User has been idle for 5+ seconds, try to process OCR
                    ocrIdleProcessor.tryProcessIfIdle(lastActivityMs)
                }
            }
        }
    }

    // Tasks pagination and data for the selected date
    var currentTaskPageIndex by remember { mutableStateOf(0) }
    var currentTaskPageInfo by remember { mutableStateOf<TaskPageInfo?>(null) }
    var currentTaskPageId by remember { mutableStateOf<String?>(null) }
    var currentTasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var isTasksPageLoading by remember { mutableStateOf(false) }
    var pendingTaskPageNavigationIndex by remember { mutableStateOf<Int?>(null) }
    var pendingTaskCanvasForceRefresh by remember { mutableStateOf(false) }

    suspend fun loadTasksPage(date: String, pageIndex: Int) {
        appRepository.taskRepository.materializeRecurringTasksForDate(
            date = date,
            canvasHeight = GlobalAppSettings.tasksCanvasHeight
        )
        val (tasks, pageInfo) = withContext(Dispatchers.IO) {
            appRepository.taskRepository.getOrCreateTasksForPage(date, pageIndex)
        }
        currentTasks = tasks
        currentTaskPageInfo = pageInfo
        currentTaskPageIndex = pageInfo.currentIndex
        currentTaskPageId = tasks.firstOrNull()?.pageId
    }

    val resolveTaskAiReferenceHour: (LocalDate) -> Int = remember(aiPreferencesPromptManager) {
        { targetDate ->
            val constraints = com.boox.day.ai.PreferenceInterpreter.interpret(
                aiPreferencesPromptManager.getPreferences()
            )
            if (targetDate == LocalDate.now()) {
                java.time.LocalTime.now().hour.coerceIn(0, 23)
            } else {
                constraints.workHoursStart.coerceIn(0, 23)
            }
        }
    }

    // Multi-page indicator: Track page counts for tab labels
    var memosPageCount by remember { mutableStateOf(1) }

    // Update completedTaskRows whenever completed status actually changes - used by DrawCanvas to block erasing
    // Using derivedStateOf so we only react to actual completed row changes, not every list reference change
    val completedRowsSet by remember {
        derivedStateOf {
            currentTasks.filter { it.isCompleted }.map { it.rowIndex }.toSet()
        }
    }
    LaunchedEffect(completedRowsSet) {
        DrawCanvas.completedTaskRows.value = completedRowsSet
    }

    // Tasks bulk actions state - consolidated dialog with scope selection
    var activeTaskOperation by remember { mutableStateOf<com.boox.day.editor.ui.TaskOperationType?>(null) }

    // Convert handwriting dialog state
    var showConvertHandwritingDialog by remember { mutableStateOf(false) }
    var convertProcessingState by remember { mutableStateOf<com.boox.day.editor.ui.RecognitionProcessingState>(com.boox.day.editor.ui.RecognitionProcessingState.Idle) }

    // Task AI feature dialog states
    var showVoiceToTaskDialog by remember { mutableStateOf(false) }
    var voiceTaskResult by remember { mutableStateOf<TaskAiService.VoiceTaskResult?>(null) }
    var showTaskPreviewDialog by remember { mutableStateOf(false) }
    var showSortPreviewDialog by remember { mutableStateOf(false) }
    var sortProcessingState by remember { mutableStateOf<SortProcessingState>(SortProcessingState.Processing()) }
    var sortResult by remember { mutableStateOf<TaskAiService.SortResult?>(null) }
    var showSortScopePopup by remember { mutableStateOf(false) }
    var placementPlan by remember { mutableStateOf<List<PlacedSortedTask>?>(null) }
    var sortScoredContext by remember { mutableStateOf<ScoredContext?>(null) }
    var sortPriorityAssignments by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Daily Summary dialog state
    var showDailySummaryDialog by remember { mutableStateOf(false) }
    var dailySummaryProcessingState by remember { mutableStateOf<DailySummaryProcessingState>(DailySummaryProcessingState.Processing()) }
    var dailySummaryResult by remember { mutableStateOf<TaskAiService.DailySummary?>(null) }

    // Weekly Summary dialog state
    var showWeeklySummaryDialog by remember { mutableStateOf(false) }
    var weeklySummaryProcessingState by remember { mutableStateOf<WeeklySummaryProcessingState>(WeeklySummaryProcessingState.Processing()) }
    var weeklySummaryResult by remember { mutableStateOf<TaskAiService.WeeklySummary?>(null) }
    var weeklySummaryCacheInfo by remember { mutableStateOf<WeeklySummaryCacheInfo?>(null) }

    // Smart Suggestions dialog state
    var showSmartSuggestionsDialog by remember { mutableStateOf(false) }
    var smartSuggestionsProcessingState by remember { mutableStateOf<SmartSuggestionsProcessingState>(SmartSuggestionsProcessingState.Processing()) }
    var smartSuggestionsResult by remember { mutableStateOf<TaskAiService.SuggestionsResult?>(null) }

    // Agentic Plan ("Plan My Day") dialog state
    var showAgenticPlanDialog by remember { mutableStateOf(false) }
    var agenticPlanProcessingState by remember { mutableStateOf<com.boox.day.data.ai.AgenticPlanProcessingState>(com.boox.day.data.ai.AgenticPlanProcessingState.Processing("Initializing...")) }
    var agenticPlanResult by remember { mutableStateOf<com.boox.day.data.ai.AgenticPlanResult?>(null) }
    var agenticCompletedProposalIds by remember { mutableStateOf(setOf<String>()) }
    var agenticInFlightProposalIds by remember { mutableStateOf(setOf<String>()) }
    var agenticFocusEventId by remember { mutableStateOf<String?>(null) }
    var agenticPendingTaskRefreshTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var agenticNeedsVisibleTaskFlush by remember { mutableStateOf(false) }
    val currentAgenticPlanKey = remember(agenticPlanResult) {
        agenticPlanResult?.proposals?.joinToString("|") { it.id } ?: "none"
    }

    LaunchedEffect(currentAgenticPlanKey) {
        agenticCompletedProposalIds = emptySet()
        agenticInFlightProposalIds = emptySet()
        agenticFocusEventId = null
    }

    // Task Breakdown dialog state
    var showTaskBreakdownDialog by remember { mutableStateOf(false) }
    var taskBreakdownProcessingState by remember { mutableStateOf<TaskBreakdownProcessingState>(TaskBreakdownProcessingState.SelectTask) }
    var taskBreakdownResult by remember { mutableStateOf<TaskAiService.BreakdownResult?>(null) }
    var selectedTaskForBreakdown by remember { mutableStateOf<TaskAiService.TaskForBreakdown?>(null) }
    var tasksForBreakdown by remember { mutableStateOf<List<TaskAiService.TaskForBreakdown>>(emptyList()) }

    // Memo to Tasks dialog state (Memos AI feature)
    var showMemoToTasksDialog by remember { mutableStateOf(false) }
    var memoToTasksProcessingState by remember { mutableStateOf<MemoToTasksProcessingState>(MemoToTasksProcessingState.Processing("")) }
    var memoToTasksResult by remember { mutableStateOf<MemoToTasksResult?>(null) }
    var memoToTasksTargetDate by remember { mutableStateOf(selectedDate) }
    var memoToTasksProcessingStartTime by remember { mutableStateOf(0L) }
    var memoToTasksSkipScopeSelection by remember { mutableStateOf(false) } // true when triggered from lasso selection
    var memoToTasksDueDate by remember { mutableStateOf<Long?>(null) } // optional due date for converted tasks

    // ═══════════════════════════════════════════════════════════════════════════
    // AI GATING DIALOG STATE (Dual-Gate System)
    // ═══════════════════════════════════════════════════════════════════════════
    // Gate 1: AI quota exceeded dialog

    // Gate 2: No task slots available dialog
    var showNoTaskSlotsDialog by remember { mutableStateOf(false) }

    // Partial insert dialog (after user selection)
    var showPartialInsertDialog by remember { mutableStateOf(false) }
    var partialInsertDialogSelectedCount by remember { mutableStateOf(0) }
    var partialInsertDialogAvailableSlots by remember { mutableStateOf(0) }
    var partialInsertDialogItemName by remember { mutableStateOf("tasks") }
    var partialInsertDialogOnConfirm by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Delete Task Page confirmation dialog state
    var showDeleteTaskPageConfirmation by remember { mutableStateOf(false) }

    // BUG FIX: Store actual canvas height for stroke migration calculations
    // Default to 1080 but will be updated when canvas renders
    // Measured Tasks canvas height; 0 until the canvas reports it (plan §13.2-2, Oct 2026:
    // it started at a made-up 1080 px, which gave wrong rows to anything that ran first)
    var actualCanvasHeight by remember { mutableStateOf(GlobalAppSettings.tasksCanvasHeight) }

    // Recurring placement is deferred until the canvas is measured (it needs real rows to see
    // ink); run it for the shown day once the height is known, and show what it created.
    val tasksCanvasMeasured = actualCanvasHeight > 2f
    LaunchedEffect(tasksCanvasMeasured) {
        if (!tasksCanvasMeasured) return@LaunchedEffect
        val date = selectedDate
        val result = appRepository.taskRepository.materializeRecurringTasksForDate(
            date = date,
            canvasHeight = actualCanvasHeight
        )
        if (result.changed && date == selectedDate) {
            val pageIndex = currentTaskPageInfo?.currentIndex ?: 0
            val (tasks, pageInfo) = withContext(Dispatchers.IO) {
                appRepository.taskRepository.getOrCreateTasksForPage(date, pageIndex)
            }
            if (date == selectedDate) {
                currentTasks = tasks
                currentTaskPageInfo = pageInfo
            }
        }
    }

    // AI FEATURE: Bitmap capture provider for task page OCR
    // This function is provided by PersistentCanvasHost when tasks page is ready
    var captureTasksBitmap by remember { mutableStateOf<(() -> android.graphics.Bitmap?)?>(null) }

    // Quick Add task state - when set, opens TypeTaskDialog for this task
    var quickAddTargetTask by remember { mutableStateOf<Task?>(null) }

    // Calendar viewing state (for month navigation)
    var viewingCalendar by remember {
        mutableStateOf(Calendar.getInstance().apply {
            try {
                time = dateFormat.parse(selectedDate) ?: time
            } catch (e: Exception) { }
        })
    }
    val currentYear = viewingCalendar.get(Calendar.YEAR)
    val currentMonth = viewingCalendar.get(Calendar.MONTH)

    // ========== COMPACT LAYOUT (plan Phase 1.5: narrow screens such as the Go 6) ==========
    // A one-week strip + Events/Due chips stay above the page; the month grid and the lists open
    // as popups over it. Fixed per device — Regular screens never enter these paths.
    val isCompactLayout = remember { LayoutProfiles.isCompact }
    var compactWeekAnchor by remember { mutableStateOf(parseIsoDateOrNull(selectedDate) ?: LocalDate.now()) }
    var showCompactMonthPopup by remember { mutableStateOf(false) }
    var showCompactEventsPopup by remember { mutableStateOf(false) }
    var compactEventsListDate by remember { mutableStateOf(selectedDate) }
    var compactHeaderPopupVisible by remember { mutableStateOf(false) }
    var compactAddEventDate by remember { mutableStateOf<String?>(null) }

    // Start-the-day review (plan T4 §13.6B, Oct 2026)
    var startDayReview by remember { mutableStateOf<StartDayReviewData?>(null) }
    var reviewPickDayTask by remember { mutableStateOf<Task?>(null) }
    var reviewDeleteTask by remember { mutableStateOf<Task?>(null) }

    // Layout B (Oct 2026): first event and first due task of the selected day for the one-line
    // Events / Due boxes (compact only — the Regular layout never collects these)
    val compactEventsPreview: String? = if (isCompactLayout) {
        val dayEvents by remember(selectedDate) { eventsViewModel.getEventsForDate(selectedDate) }
            .collectAsState(initial = emptyList())
        remember(dayEvents, selectedDate) {
            com.boox.day.ui.calendar.compactEventPreview(dayEvents, selectedDate)
        }
    } else {
        null
    }
    val compactDuePreview: String? = if (isCompactLayout) {
        val dueData by remember(selectedDate, dueFlowToday) { eventsViewModel.getDueTasksForDate(selectedDate) }
            .collectAsState(initial = null)
        dueData?.let { data ->
            (data.dueTodayTasks + data.overdueTasks).firstOrNull()?.let { com.boox.day.ui.calendar.compactTaskPreview(it) }
        }
    } else {
        null
    }

    // Shows a month and syncs it, like the header's month arrows do
    fun showCompactMonth(year: Int, monthIndex: Int) {
        viewingCalendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthIndex)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        if (hasAnyExternalAccount) {
            scope.launch { multiAccountSyncViewModel.syncAllAccounts() }
        } else if (isSignedIn) {
            syncViewModel.syncMonthIfNeeded(year, monthIndex + 1)
        }
    }

    if (isCompactLayout) {
        // The two-week strip follows the selected day — only when it leaves the two shown weeks, so
        // picking a day in the second row doesn't shift the rows (layout B, Oct 2026)...
        LaunchedEffect(selectedDate) {
            parseIsoDateOrNull(selectedDate)?.let { date ->
                val windowStart = com.boox.day.ui.calendar.compactWindowStart(compactWeekAnchor, firstDayOfWeek)
                if (date < windowStart || date > windowStart.plusDays(13)) {
                    compactWeekAnchor = date
                }
            }
        }
        // ...the header month arrows move it to the 1st of the shown month...
        LaunchedEffect(currentYear, currentMonth) {
            if (compactWeekAnchor.year != currentYear || compactWeekAnchor.monthValue - 1 != currentMonth) {
                compactWeekAnchor = LocalDate.of(currentYear, currentMonth + 1, 1)
            }
        }
        // ...and the shown month follows the strip when a week step crosses into another month
        LaunchedEffect(compactWeekAnchor) {
            if (compactWeekAnchor.year != currentYear || compactWeekAnchor.monthValue - 1 != currentMonth) {
                showCompactMonth(compactWeekAnchor.year, compactWeekAnchor.monthValue - 1)
            }
        }
    }

    // Track dates with notes for the current month
    val currentYearMonth = remember(currentYear, currentMonth) {
        String.format(Locale.getDefault(), "%04d-%02d", currentYear, currentMonth + 1)
    }

    // Dates with notes - use MutableState for immediate updates
    var datesWithNotes by remember { mutableStateOf<List<String>>(emptyList()) }

    // Track dates with Google Calendar events for the current month
    var datesWithEvents by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Track dates with Boox Notes for the current month
    var datesWithBooxNotes by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Track dates with meaningful Tasks for the current month
    var datesWithTasks by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Trigger to refresh note-event links (incremented when links change in dialog)
    var noteLinkRefreshTrigger by remember { mutableStateOf(0) }

    suspend fun collectWeeklySummarySnapshot(
        startDate: LocalDate,
        endDate: LocalDate,
        canvasHeight: Float
    ): WeeklySummarySnapshot = withContext(Dispatchers.IO) {
        val tasksByDateRaw = mutableMapOf<String, MutableList<Task>>()
        val pageIds = mutableSetOf<String>()

        val totalDays = java.time.temporal.ChronoUnit.DAYS
            .between(startDate, endDate)
            .toInt()
            .coerceAtLeast(0)
        for (i in 0..totalDays) {
            val date = startDate.plusDays(i.toLong())
            val dateStr = date.toString()
            val dayTasks = mutableListOf<Task>()
            val maxPageIndex = appRepository.taskRepository.getMaxTaskPageIndex(dateStr)
            if (maxPageIndex >= 0) {
                // Read-only scan: avoid getOrCreateTasksForPage to prevent creating empty pages.
                for (pageIndex in 0..maxPageIndex) {
                    val tasks = appRepository.taskRepository.getTasksForPage(dateStr, pageIndex)
                    if (tasks.isNotEmpty()) {
                        dayTasks.addAll(tasks)
                        tasks.forEach { pageIds.add(it.pageId) }
                    }
                }
            }
            tasksByDateRaw[dateStr] = dayTasks
        }

        val strokesByPage = if (pageIds.isEmpty()) {
            emptyMap()
        } else {
            appRepository.strokeRepository.getAllForPages(pageIds.toList()).groupBy { it.pageId }
        }

        fun taskHasContent(task: Task): Boolean {
            if (task.typedText?.isNotBlank() == true) return true
            if (task.convertedText?.isNotBlank() == true) return true
            if (task.recognizedText?.isNotBlank() == true) return true
            if (task.isCompleted) return true
            if (task.priority.isNotBlank()) return true
            if (task.dueDay != null) return true
            if (task.parentTaskId != null) return true

            if (canvasHeight <= 2f) return false
            val strokesForPage = strokesByPage[task.pageId]
            val rowBounds = calculateRowBounds(task.rowIndex, canvasHeight, 10)
            return hasIntentionalStrokeContent(strokesForPage, rowBounds)
        }

        val tasksByDate = mutableMapOf<String, List<Task>>()
        val allTasks = mutableListOf<Task>()
        var completedCount = 0
        var maxUpdatedAt = 0L

        for (i in 0..totalDays) {
            val date = startDate.plusDays(i.toLong())
            val dateStr = date.toString()
            val dayTasks = tasksByDateRaw[dateStr].orEmpty()
            val filtered = dayTasks.filter { taskHasContent(it) }
            tasksByDate[dateStr] = filtered
            allTasks.addAll(filtered)
            completedCount += filtered.count { it.isCompleted }
        }

        allTasks.forEach { task ->
            maxUpdatedAt = maxOf(maxUpdatedAt, task.updatedAt.time)
        }
        strokesByPage.values.flatten().forEach { stroke ->
            maxUpdatedAt = maxOf(maxUpdatedAt, stroke.updatedAt.time)
        }

        WeeklySummarySnapshot(
            tasksByDate = tasksByDate,
            allTasks = allTasks,
            contentHash = TaskAiService.computeWeeklySummaryContentHash(allTasks, strokesByPage),
            taskCount = allTasks.size,
            completedCount = completedCount,
            lastUpdatedAt = if (maxUpdatedAt > 0L) maxUpdatedAt else null
        )
    }

    fun launchWeeklySummary(forceRefresh: Boolean) {
        weeklySummaryProcessingState = WeeklySummaryProcessingState.Processing("Checking cache...")
        weeklySummaryResult = null
        weeklySummaryCacheInfo = null
        showWeeklySummaryDialog = true

        scope.launch {
            val weekFields = WeekFields.of(Locale.getDefault())
            val selected = LocalDate.parse(selectedDate)
            val startDate = selected.with(weekFields.dayOfWeek(), 1)
            val endDate = startDate.plusDays(6)
            val cacheId = SummaryCache.weeklyId(startDate.toString(), endDate.toString())

            weeklySummaryProcessingState = WeeklySummaryProcessingState.Processing("Gathering 7 days of tasks...")
            val snapshot = collectWeeklySummarySnapshot(startDate, endDate, actualCanvasHeight)

            val cachedEntry = if (!forceRefresh) {
                withContext(Dispatchers.IO) {
                    summaryCacheRepository.getValidCache(cacheId, snapshot.contentHash)
                }
            } else null

            if (cachedEntry != null) {
                weeklySummaryProcessingState = WeeklySummaryProcessingState.Processing("Loading cached summary...")
                val cached = TaskAiService.deserializeWeeklySummary(cachedEntry.resultJson)
                if (cached != null) {
                    weeklySummaryResult = cached
                    weeklySummaryCacheInfo = WeeklySummaryCacheInfo(
                        isCached = true,
                        generatedAt = cachedEntry.generatedAt,
                        lastUpdatedAt = cachedEntry.lastUpdatedAt ?: snapshot.lastUpdatedAt,
                        taskCount = cachedEntry.taskCount ?: snapshot.taskCount,
                        completedCount = cachedEntry.completedCount ?: snapshot.completedCount
                    )
                    weeklySummaryProcessingState = WeeklySummaryProcessingState.Ready
                    return@launch
                }
            }

            // A new summary needs the user's own OpenRouter key (Oct 2026)
            if (!com.boox.day.ui.AiKeyGate.requireKey()) {
                showWeeklySummaryDialog = false
                return@launch
            }
            // It sends the week's events: the once-only "AI and your calendar" notice (ui/CalendarAiNotice)
            if (!com.boox.day.ui.CalendarAiNotice.awaitAllowed()) {
                showWeeklySummaryDialog = false
                return@launch
            }


            weeklySummaryProcessingState = WeeklySummaryProcessingState.Processing("Analyzing patterns...")
            val intelligenceEngine = IntelligenceEngine(context)
            val scoredContext = intelligenceEngine.getScoredContext(
                feature = IntelligenceFeature.WEEKLY_SUMMARY,
                tasks = snapshot.allTasks
            )

            val result = TaskAiService.generateWeeklySummaryV3(
                weeklyTasks = snapshot.tasksByDate,
                startDate = startDate,
                endDate = endDate,
                scoredContext = scoredContext,
                onStageChange = { stage ->
                    weeklySummaryProcessingState = WeeklySummaryProcessingState.Processing(stage)
                },
                context = context
            )

            if (result.error == null) {
                val generatedAt = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    val json = TaskAiService.serializeWeeklySummary(result)
                    summaryCacheRepository.save(SummaryCache(
                        id = cacheId,
                        type = SummaryCache.TYPE_WEEKLY,
                        dateKey = endDate.toString(),
                        rangeStart = startDate.toString(),
                        rangeEnd = endDate.toString(),
                        taskCount = snapshot.taskCount,
                        completedCount = snapshot.completedCount,
                        lastUpdatedAt = snapshot.lastUpdatedAt,
                        contentHash = snapshot.contentHash,
                        resultJson = json,
                        generatedAt = generatedAt,
                        validHours = SummaryCache.WEEKLY_VALID_HOURS
                    ))
                }
                weeklySummaryCacheInfo = WeeklySummaryCacheInfo(
                    isCached = false,
                    generatedAt = generatedAt,
                    lastUpdatedAt = snapshot.lastUpdatedAt,
                    taskCount = snapshot.taskCount,
                    completedCount = snapshot.completedCount
                )
            }

            weeklySummaryResult = result
            weeklySummaryProcessingState = if (result.error != null) {
                WeeklySummaryProcessingState.Error(result.error!!)
            } else {
                WeeklySummaryProcessingState.Ready
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Day Quick View Popup State (long-press on calendar day)
    // ─────────────────────────────────────────────────────────────────────────────
    var showDayQuickView by remember { mutableStateOf(false) }
    var quickViewDate by remember { mutableStateOf<String?>(null) }
    var quickViewData by remember { mutableStateOf<DayQuickViewData?>(null) }

    fun openQuickViewList(tab: String) {
        val date = quickViewData?.date ?: return
        selectedDate = date
        selectedEventsTab = tab
        if (isCompactLayout) {
            compactEventsListDate = date
            showCompactEventsPopup = true
        } else {
            isEventsExpanded = true
        }
        showDayQuickView = false
    }
    // Tasks page navigation (plan §13.4, Oct 2026): the quota check and the page lookup or
    // creation run off the main thread; the result is applied only if the day hasn't changed.
    // Break down one task from its task card (plan T3): same steps as the Daily Summary entry —
    // pre-select the task, open the break-down dialog and ask the AI (usage recorded on success)
    fun startTaskBreakdown(task: Task) {
        if (!com.boox.day.ui.AiKeyGate.requireKey()) return  // the user's own OpenRouter key (Oct 2026)
        val taskText = task.typedText?.takeIf { it.isNotBlank() }
            ?: task.convertedText?.takeIf { it.isNotBlank() }
            ?: task.recognizedText?.takeIf { it.isNotBlank() }
            ?: return
        selectedTaskForBreakdown = TaskAiService.TaskForBreakdown(
            task = task,
            displayText = taskText,
            isHandwritten = task.typedText.isNullOrBlank() && task.convertedText.isNullOrBlank()
        )
        taskBreakdownProcessingState = TaskBreakdownProcessingState.ProcessingBreakdown
        taskBreakdownResult = null
        showTaskBreakdownDialog = true
        val date = selectedDate
        val pageIndex = currentTaskPageIndex
        scope.launch {
            val tasksForContext = withContext(Dispatchers.IO) {
                appRepository.taskRepository.getOrCreateTasksForPage(date, pageIndex).first
            }
            val planningDate = LocalDate.parse(date)
            val scoredContext = IntelligenceEngine(context).getScoredContext(
                feature = IntelligenceFeature.BREAK_DOWN_TASK,
                tasks = tasksForContext,
                referenceDate = planningDate,
                referenceHour = resolveTaskAiReferenceHour(planningDate)
            )
            val result = TaskAiService.breakDownTaskV3(
                taskText = taskText,
                task = task,
                existingTasks = tasksForContext,
                currentDate = planningDate,
                scoredContext = scoredContext
            )
            taskBreakdownResult = result
            taskBreakdownProcessingState = if (result.error != null) {
                TaskBreakdownProcessingState.Error(result.error!!)
            } else {
                TaskBreakdownProcessingState.Ready
            }
        }
    }

    // Go to task (plan T3, Oct 2026): Due-tab rows, search hits and reminder notifications open
    // the task's day on the Tasks tab, on its page, with its row highlighted for a few seconds
    var highlightedTaskId by remember { mutableStateOf<String?>(null) }

    fun goToTask(taskId: String?, date: String) {
        scope.launch {
            val task = taskId?.let { id ->
                withContext(Dispatchers.IO) { appRepository.taskRepository.getTaskById(id) }
            }
            val targetDate = task?.dayDate ?: date
            val targetPageIndex = task?.taskPageIndex ?: 0
            if (targetDate != selectedDate) {
                pendingTaskPageNavigationIndex = targetPageIndex
                pendingTaskCanvasForceRefresh = true
                selectedDate = targetDate
            } else if (targetPageIndex != (currentTaskPageInfo?.currentIndex ?: 0)) {
                loadTasksPage(targetDate, targetPageIndex)
                if (currentEditorTab == EditorTab.Tasks) {
                    DrawCanvas.forceUpdate.emit(null)
                }
            }
            tabManager.switchToTasks(targetDate, null)
            if (task != null) {
                highlightedTaskId = task.id
                delay(6_000)
                if (highlightedTaskId == task.id) highlightedTaskId = null
            }
        }
    }

    LaunchedEffect(Unit) {
        com.boox.day.ui.TaskFocusRequests.pending.collect { request ->
            if (request != null) {
                com.boox.day.ui.TaskFocusRequests.consume()
                goToTask(request.taskId, request.dayDate)
            }
        }
    }

    // Start-the-day review (plan T4 §13.6B, Oct 2026): unfinished tasks from earlier days and tasks
    // due today. Canvas refreshes are held while it's open and done once when it closes.
    fun openStartDayReview(lastReviewDay: String?, showWhenEmpty: Boolean) {
        scope.launch {
            val data = StartDayReviewLoader.load(
                repository = appRepository.taskRepository,
                today = LocalDate.parse(today),
                lastReviewDay = lastReviewDay,
                canvasHeight = GlobalAppSettings.tasksCanvasHeight
            )
            if (data.isEmpty && !showWhenEmpty) return@launch
            if (startDayReview == null) TasksCanvasRefresh.hold()
            startDayReview = data
        }
    }

    fun closeStartDayReview() {
        if (startDayReview == null) return
        startDayReview = null
        reviewPickDayTask = null
        reviewDeleteTask = null
        scope.launch {
            // Moves, deletions and completions may have changed the page on screen
            loadTasksPage(selectedDate, currentTaskPageInfo?.currentIndex ?: 0)
            TasksCanvasRefresh.release()
        }
    }

    /** Takes [task] off the review (moved, done or deleted). */
    fun removeFromReview(task: Task) {
        startDayReview = startDayReview?.let { data ->
            data.copy(
                unfinished = data.unfinished.filter { it.id != task.id },
                dueToday = data.dueToday.filter { it.id != task.id }
            )
        }
    }

    // Move with its ink; the due date moves with it and the deferral is counted
    fun reviewMove(task: Task, targetDate: String) {
        removeFromReview(task)
        scope.launch(Dispatchers.IO) {
            val result = appRepository.taskRepository.moveTaskWithStrokes(
                task = task,
                targetDate = targetDate,
                targetPageIndex = 0,
                canvasHeight = GlobalAppSettings.tasksCanvasHeight,
                context = context
            )
            if (result.success) {
                appRepository.taskRepository.recordTaskMigration(result.movedTaskId ?: task.id, task.dayDate)
            } else {
                withContext(Dispatchers.Main) {
                    snackManager.displaySnack(SnackConf(text = "Couldn't move the task", duration = SnackConf.DURATION_ERROR))
                }
            }
        }
    }

    fun reviewDone(task: Task) {
        removeFromReview(task)
        scope.launch(Dispatchers.IO) { appRepository.taskRepository.setCompleted(task.id, true) }
    }

    fun reviewDelete(task: Task) {
        removeFromReview(task)
        scope.launch(Dispatchers.IO) {
            appRepository.taskRepository.clearTaskSlot(task, GlobalAppSettings.tasksCanvasHeight)
            TaskTextIndexer.getInstanceOrNull()?.indexNow(task.dayDate, task.taskPageIndex, task.pageId)
        }
    }

    // Keep Tasks ink on its rows when the page height changed (layout or Boox DPI): once per app
    // start, on a canvas height that stayed the same for 2 s (TasksInkLayout)
    LaunchedEffect(tasksCanvasMeasured) {
        if (!tasksCanvasMeasured) return@LaunchedEffect
        val measured = GlobalAppSettings.tasksCanvasHeight
        delay(2_000)
        val canvasHeight = GlobalAppSettings.tasksCanvasHeight
        if (kotlin.math.abs(canvasHeight - measured) > 1f) return@LaunchedEffect
        val moved = withContext(Dispatchers.IO) {
            com.boox.day.editor.utils.TasksInkLayout.ensureInkMatchesRows(
                context = context,
                canvasHeight = canvasHeight,
                topOffset = com.boox.day.editor.utils.tasksTopOffset(),
                realignOnFirstRun = isCompactLayout
            )
        }
        if (moved) {
            loadTasksPage(selectedDate, currentTaskPageInfo?.currentIndex ?: 0)
            TasksCanvasRefresh.request()
        }
    }

    // Opens once a day, the first time today's Tasks are shown (only when there is something to review)
    LaunchedEffect(currentEditorTab, selectedDate, tasksCanvasMeasured) {
        if (currentEditorTab != EditorTab.Tasks || selectedDate != today || !tasksCanvasMeasured) return@LaunchedEffect
        val settings = GlobalAppSettings.current
        if (settings.lastStartDayReviewDay == today) return@LaunchedEffect
        appRepository.kvProxy.setAppSettings(settings.copy(lastStartDayReviewDay = today))
        openStartDayReview(lastReviewDay = settings.lastStartDayReviewDay, showWhenEmpty = false)
    }

    fun navigateTaskPage(delta: Int) {
        val currentIndex = currentTaskPageInfo?.currentIndex ?: 0
        val totalPages = currentTaskPageInfo?.totalPages ?: 1
        val newIndex = when {
            delta > 0 -> currentIndex + 1
            delta < 0 && currentIndex > 0 -> currentIndex - 1
            else -> currentIndex
        }
        val wouldCreateNewPage = delta > 0 && newIndex == totalPages
        if (!wouldCreateNewPage && newIndex == currentIndex) return
        val date = selectedDate
        scope.launch {
            val (tasks, pageInfo) = withContext(Dispatchers.IO) {
                appRepository.taskRepository.getOrCreateTasksForPage(date, newIndex)
            }
            if (date != selectedDate) return@launch
            currentTasks = tasks
            currentTaskPageInfo = pageInfo
            currentTaskPageIndex = pageInfo.currentIndex
            currentTaskPageId = tasks.firstOrNull()?.pageId
        }
    }

    val isMainScreenModalBlockingCanvas by remember {
        derivedStateOf {
            showCalendarSettingsDialog ||
                showAddEventDialog ||
                (showEventDetailsDialog && selectedEventForDetails != null) ||
                showAboutDialog ||
                showBooxNotesDialog ||
                showBooxNotesSettingsDialog ||
                showVoiceEventDialog ||
                showHelpDialog ||
                showAiPreferencesDialog ||
                showAiPreferencesInvitePopup ||
                showPreferencesDialog ||
                showAiSettingsDialog ||
                showVoiceSettingsDialog ||
                showDataBackupDialog ||
                showSearchDialog ||
                showMultiProviderSyncDialog ||
                showAddCalendarDialog ||
                activeTaskOperation != null ||
                showConvertHandwritingDialog ||
                quickAddTargetTask != null ||
                showVoiceToTaskDialog ||
                (showTaskPreviewDialog && voiceTaskResult != null) ||
                showSortScopePopup ||
                showSortPreviewDialog ||
                showDailySummaryDialog ||
                showWeeklySummaryDialog ||
                showSmartSuggestionsDialog ||
                showAgenticPlanDialog ||
                showTaskBreakdownDialog ||
                showMemoToTasksDialog ||
                showNoTaskSlotsDialog ||
                showPartialInsertDialog ||
                showDeleteTaskPageConfirmation ||
                startDayReview != null ||
                reviewPickDayTask != null ||
                reviewDeleteTask != null ||
                (showDayQuickView && quickViewData != null) ||
                showCompactMonthPopup ||
                showCompactEventsPopup ||
                compactHeaderPopupVisible ||
                com.boox.day.actionitems.ActionItemsUi.isOpen ||
                com.boox.day.meetings.MeetingNotesUi.isOpen ||
                com.boox.day.ui.AiKeyGate.isOpen ||
                com.boox.day.update.AppUpdateGate.isOpen ||
                com.boox.day.diagnostics.CrashReportGate.isOpen ||
                com.boox.day.ui.CalendarAiNotice.isOpen ||
                com.boox.day.ui.GoogleReconnectGate.isOpen ||
                com.boox.day.ui.FullScreenState.transitioning ||
                onboardingDismissedAt == 0L
        }
    }

    LaunchedEffect(isMainScreenModalBlockingCanvas, agenticPendingTaskRefreshTarget, agenticNeedsVisibleTaskFlush) {
        val refreshTarget = agenticPendingTaskRefreshTarget
        val needsVisibleTaskFlush = agenticNeedsVisibleTaskFlush
        if (refreshTarget == null && !needsVisibleTaskFlush) return@LaunchedEffect
        if (isMainScreenModalBlockingCanvas) return@LaunchedEffect

        if (needsVisibleTaskFlush && currentEditorTab == EditorTab.Tasks) {
            agenticNeedsVisibleTaskFlush = false
            DrawCanvas.forceUnfreeze()
        } else if (needsVisibleTaskFlush) {
            agenticNeedsVisibleTaskFlush = false
        }

        val resolvedRefreshTarget = refreshTarget ?: return@LaunchedEffect
        agenticPendingTaskRefreshTarget = null
        val (refreshDate, refreshPageIndex) = resolvedRefreshTarget

        if (refreshDate != selectedDate) {
            pendingTaskPageNavigationIndex = refreshPageIndex
            pendingTaskCanvasForceRefresh = true
            selectedDate = refreshDate
        } else {
            val previousTaskPageId = currentTaskPageId
            loadTasksPage(refreshDate, refreshPageIndex)
            if (currentEditorTab == EditorTab.Tasks && currentTaskPageId == previousTaskPageId) {
                DrawCanvas.forceUnfreeze()
            }
        }
    }

    LaunchedEffect(isMainScreenModalBlockingCanvas) {
        DrawCanvas.mainScreenModalVisible.value = isMainScreenModalBlockingCanvas
    }

    DisposableEffect(Unit) {
        onDispose {
            DrawCanvas.mainScreenModalVisible.value = false
        }
    }

    // Fetch data when quick view popup opens
    LaunchedEffect(showDayQuickView, quickViewDate) {
        if (showDayQuickView && quickViewDate != null) {
            val date = quickViewDate!!
            quickViewData = withContext(Dispatchers.IO) {
                // Fetch all data in parallel for performance
                val notesDeferred = async { booxNoteRepository.getNotesForDate(date) }
                val eventsDeferred = async { eventsViewModel.getEventCountForDate(date).first() }
                val tasksDeferred = async { taskRepository.getTasksDueOnDateCount(date).first() }

                // Format display date (e.g., "December 30, 2025")
                val displayDate = try {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val outputFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                    val parsedDate = inputFormat.parse(date)
                    if (parsedDate != null) outputFormat.format(parsedDate) else date
                } catch (e: Exception) { date }

                // Filter out empty notes (0 pages) for consistency with BooxNotesDialog
                val notes = notesDeferred.await().filter { it.pageCount > 0 }

                // Fetch links and linked events for each note
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
                    date = date,
                    displayDate = displayDate,
                    booxNotes = notesWithEvents,
                    eventCount = eventsDeferred.await(),
                    tasksDueCount = tasksDeferred.await()
                )
            }
        } else if (!showDayQuickView) {
            // Clear data when popup closes
            quickViewData = null
        }
    }

    // Load linked Boox Notes when an event is selected for details
    LaunchedEffect(selectedEventForDetails) {
        if (selectedEventForDetails != null) {
            linkedBooxNotesForEvent = withContext(Dispatchers.IO) {
                booxNoteRepository.getNotesLinkedToEvent(selectedEventForDetails!!.id)
            }
        } else {
            linkedBooxNotesForEvent = emptyList()
        }
    }

    // Consolidated badge data loading when month changes (parallel pre-warming)
    LaunchedEffect(currentYearMonth) {
        if (PERF_LOGS) Log.i(TAG, "PERF: CacheWarmupCoordinator START for $currentYearMonth")
        val startTime = System.currentTimeMillis()
        val result = withContext(Dispatchers.IO) {
            cacheWarmupCoordinator.warmupForMonth(currentYearMonth)
        }
        datesWithNotes = result.datesWithNotes.distinct()
        datesWithTasks = result.datesWithTasks
        datesWithEvents = result.datesWithEvents
        datesWithBooxNotes = result.datesWithBooxNotes
        if (PERF_LOGS) Log.i(TAG, "PERF: CacheWarmupCoordinator COMPLETE for $currentYearMonth (${System.currentTimeMillis() - startTime}ms)")

        // Pre-warm adjacent months in background for faster month navigation
        launch(Dispatchers.IO) {
            delay(500)  // Wait for UI to settle
            try {
                cacheWarmupCoordinator.warmupAdjacentMonths(currentYearMonth)
            } catch (e: Exception) {
                Log.w(TAG, "Adjacent month warmup failed: ${e.message}")
            }
        }
    }

    // Update Memos/Tasks badge in real-time when content changes (strokes/images added/removed)
    // Uses collectLatest + delay to batch rapid stroke events and avoid DB queries during drawing
    val selectedDateState = rememberUpdatedState(selectedDate)
    val canvasHeightState = rememberUpdatedState(actualCanvasHeight)
    LaunchedEffect(Unit) {
        DrawCanvas.contentChanged.collectLatest {
            delay(300) // Batch rapid stroke events
            memoRefreshTrigger++

            val currentDate = selectedDateState.value
            val isMemoMode = DrawCanvas.memoModeChanged.value
            var badgeStateChanged = false

            if (isMemoMode) {
                val hasContent = withContext(Dispatchers.IO) {
                    appRepository.dayPageRepository.dateHasContent(currentDate)
                }
                val alreadyContains = datesWithNotes.contains(currentDate)
                datesWithNotes = if (hasContent) {
                    if (!alreadyContains) {
                        badgeStateChanged = true
                        datesWithNotes + currentDate
                    } else {
                        datesWithNotes
                    }
                } else {
                    if (alreadyContains) { badgeStateChanged = true }
                    datesWithNotes.filterNot { it == currentDate }
                }
            } else {
                // Use stroke-aware task detection that includes intentional handwritten content
                val hasTask = withContext(Dispatchers.IO) {
                    taskRepository.dateHasTaskContent(currentDate, canvasHeightState.value)
                }
                val alreadyHasTask = datesWithTasks.contains(currentDate)
                datesWithTasks = if (hasTask) {
                    if (!alreadyHasTask) { badgeStateChanged = true }
                    datesWithTasks + currentDate
                } else {
                    if (alreadyHasTask) { badgeStateChanged = true }
                    datesWithTasks - currentDate
                }
            }

            // Toggle raw drawing to flush UI updates to framebuffer (Onyx SDK blocks rendering during raw drawing)
            if (badgeStateChanged) {
                withContext(Dispatchers.Main) {
                    DrawCanvas.flushUIUpdates()
                }
            }
        }
    }

    val datesWithNotesSet = remember(datesWithNotes) { datesWithNotes.toSet() }

    // Update Tasks badge in real-time when tasks are modified (checkbox/priority/typedText/strokes)
    // Use collect (not collectLatest) to ensure both source and target dates are processed on task move
    val currentYearMonthState = rememberUpdatedState(currentYearMonth)
    LaunchedEffect(Unit) {
        // ≡ "Clear All Tasks Today" (Oct 2026): the cleared day is back to one page. Show it, or the page
        // count stays at the old total. Here, in this effect's own class: MainScreen's root lambda is full.
        launch {
            TaskRepository.dayCleared.collect { clearedDate ->
                if (clearedDate == selectedDateState.value) {
                    loadTasksPage(clearedDate, 0)
                    TasksCanvasRefresh.request()
                }
            }
        }
        TaskRepository.taskChanged.collect { changedDate ->
            // Only update if the changed date is in the current viewing month
            if (changedDate.startsWith(currentYearMonthState.value)) {
                // Re-query using comprehensive check that includes strokes (off main thread)
                val hasTask = withContext(Dispatchers.IO) {
                    taskRepository.dateHasTaskContent(changedDate, canvasHeightState.value)
                }
                datesWithTasks = if (hasTask) {
                    datesWithTasks + changedDate
                } else {
                    datesWithTasks - changedDate
                }
            }
        }
    }

    // Automatic Boox Notes scan on startup (runs once when MainScreen loads)
    // OPTIMIZATION: Silent scan - only notify on errors, not on success
    // This avoids blocking user perception with snack messages on every app launch
    LaunchedEffect(Unit) {
        val hasPerm = hasFilePermission(context)
        val isAlreadyScanning = GlobalAppSettings.isBooxNotesScanning
        Log.d("BooxScanDebug", "Startup Boox scan check: hasPerm=$hasPerm, isAlreadyScanning=$isAlreadyScanning")
        if (hasPerm && !isAlreadyScanning) {
            Log.d("BooxScanDebug", "Setting isBooxNotesScanning=true")
            GlobalAppSettings.setBooxNotesScanning(true)
            try {
                Log.d("BooxScanDebug", "Starting fullScan...")
                // Run initial scan in background (silent on success) - off main thread
                val (scanResult, booxNoteDates) = withContext(Dispatchers.IO) {
                    val result = booxNoteIndexer.fullScan()
                    Log.d("BooxScanDebug", "fullScan returned: newNotes=${result.newNotes}, error=${result.error}")
                    // PERFORMANCE: Invalidate links cache since note data may have changed
                    booxNoteRepository.invalidateLinksCache()
                    // Refresh the dates with Boox Notes for the current month
                    val dates = booxNoteRepository.getDatesWithNotesForMonth(currentYearMonth)
                    Pair(result, dates)
                }
                datesWithBooxNotes = booxNoteDates
                val scanSucceeded = scanResult.error == null ||
                    scanResult.error.contains("UnsatisfiedLinkError")
                if (scanSucceeded) {
                    // Mark Boox Notes as ready (scan complete)
                    GlobalAppSettings.setBooxNotesReady(true)
                    Log.d("BooxScanDebug", "Scan complete, setBooxNotesReady(true)")

                    // FIX: Repair corrupted backfill state from the premature-completion
                    // race condition.  Must run AFTER isBooxNotesReady is true so the
                    // guard in checkAndMarkBackfillComplete sees the correct state.
                    withContext(Dispatchers.IO) {
                        com.boox.day.ocr.OcrQuotaManager(context).repairBackfillStateIfCorrupted()
                    }
                } else {
                    Log.d("BooxScanDebug", "Scan failed, not setting ready. Error: ${scanResult.error}")
                }
                // Only show message on ERROR - silent success for better UX
                // Suppress UnsatisfiedLinkError - native library functions not available
                if (scanResult.error != null && !scanResult.error.contains("UnsatisfiedLinkError")) {
                    SnackState.globalSnackFlow.tryEmit(
                        SnackConf(key = "boox-scan", text = "Boox Notes scan failed: ${scanResult.error}", duration = SnackConf.DURATION_ERROR)
                    )
                }
                // Note: Success is silent on startup. Manual "Refresh All Notes" still shows results.
            } finally {
                Log.d("BooxScanDebug", "Finally block: setting isBooxNotesScanning=false")
                GlobalAppSettings.setBooxNotesScanning(false)
            }
        }
        // If permission is missing, readiness remains false until permission is granted and scan runs.
    }

    // Scan function that can be called from dialogs when permission/toggle changes
    val triggerBooxNotesScan: () -> Unit = remember(scope, booxNoteIndexer, booxNoteRepository, currentYearMonth) {
        {
            if (hasFilePermission(context) && !GlobalAppSettings.isBooxNotesScanning) {
                scope.launch {
                    GlobalAppSettings.setBooxNotesScanning(true)
                    try {
                        SnackState.globalSnackFlow.tryEmit(
                            SnackConf(key = "boox-scan", text = "Scanning Boox Notes...", duration = SnackConf.DURATION_ERROR)
                        )

                        val (scanResult, booxNoteDates) = withContext(Dispatchers.IO) {
                            val result = booxNoteIndexer.fullScan()
                            booxNoteRepository.invalidateLinksCache()
                            val dates = booxNoteRepository.getDatesWithNotesForMonth(currentYearMonth)
                            Pair(result, dates)
                        }
                        datesWithBooxNotes = booxNoteDates
                        if (scanResult.error == null || scanResult.error.contains("UnsatisfiedLinkError")) {
                            GlobalAppSettings.setBooxNotesReady(true)
                        }

                        // Show completion message
                        if (scanResult.error != null && !scanResult.error.contains("UnsatisfiedLinkError")) {
                            SnackState.globalSnackFlow.tryEmit(
                                SnackConf(key = "boox-scan", text = "Boox Notes scan failed: ${scanResult.error}", duration = SnackConf.DURATION_ERROR)
                            )
                        } else {
                            val totalFound = scanResult.newNotes + scanResult.updatedNotes
                            SnackState.globalSnackFlow.tryEmit(
                                SnackConf(key = "boox-scan", text = "Boox Notes ready! Found $totalFound notes.", duration = SnackConf.DURATION_ERROR)
                            )
                        }
                        noteLinkRefreshTrigger++
                    } finally {
                        GlobalAppSettings.setBooxNotesScanning(false)
                    }
                }
            }
        }
    }

    // Action items added to Tasks (Oct 2026): show them if they landed on the page shown
    LaunchedEffect(com.boox.day.actionitems.ActionItemsUi.tasksAdded) {
        if (com.boox.day.actionitems.ActionItemsUi.tasksAdded > 0) loadTasksPage(selectedDate, currentTaskPageIndex)
    }
    // Meeting notes (Oct 2026): show a meeting's notes page — its day (with the page as the day's open
    // page), the Memos tab, then the page
    LaunchedEffect(Unit) {
        com.boox.day.meetings.MeetingNotesUi.pages.collect { target ->
            val pageCount = withContext(Dispatchers.IO) {
                appRepository.dayPageRepository.setOpenPageId(target.date, target.pageId)
                appRepository.dayPageRepository.getPageCount(target.date).coerceAtLeast(1)
            }
            selectedDate = target.date
            memosPageCount = pageCount
            com.boox.day.meetings.MeetingNotes.showOnCanvas(tabManager, target.date, target.pageId)
        }
    }
    // Scan on app resume - handles both first-time permission grant and regular quick scan
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
            }
            if (event == Lifecycle.Event.ON_RESUME && hasFilePermission(context) && !GlobalAppSettings.isBooxNotesScanning) {
                scope.launch {
                    // Check if this is a first-time permission grant (need full scan)
                    val needsFullScan = !GlobalAppSettings.isBooxNotesReady

                    if (needsFullScan) {
                        // FIRST-TIME PERMISSION GRANT: Run full scan with progress notification
                        GlobalAppSettings.setBooxNotesScanning(true)
                        try {
                            SnackState.globalSnackFlow.tryEmit(
                                SnackConf(key = "boox-scan", text = "Scanning Boox Notes...", duration = SnackConf.DURATION_ERROR)
                            )

                            val (scanResult, booxNoteDates) = withContext(Dispatchers.IO) {
                                val result = booxNoteIndexer.fullScan()
                                booxNoteRepository.invalidateLinksCache()
                                val dates = booxNoteRepository.getDatesWithNotesForMonth(currentYearMonth)
                                Pair(result, dates)
                            }
                            datesWithBooxNotes = booxNoteDates

                            // Mark Boox Notes as ready only on success
                            if (scanResult.error == null || scanResult.error.contains("UnsatisfiedLinkError")) {
                                GlobalAppSettings.setBooxNotesReady(true)
                            }

                            // Show completion message (replaces "Scanning..." message with same key)
                            if (scanResult.error != null && !scanResult.error.contains("UnsatisfiedLinkError")) {
                                SnackState.globalSnackFlow.tryEmit(
                                    SnackConf(key = "boox-scan", text = "Boox Notes scan failed: ${scanResult.error}", duration = SnackConf.DURATION_ERROR)
                                )
                            } else {
                                val totalFound = scanResult.newNotes + scanResult.updatedNotes
                                SnackState.globalSnackFlow.tryEmit(
                                    SnackConf(key = "boox-scan", text = "Boox Notes ready! Found $totalFound notes.", duration = SnackConf.DURATION_ERROR)
                                )
                            }
                            noteLinkRefreshTrigger++
                        } finally {
                            GlobalAppSettings.setBooxNotesScanning(false)
                        }
                    } else {
                        // REGULAR RESUME: Quick scan for recent notes (last 24 hours only)
                        val scanResult = withContext(Dispatchers.IO) {
                            booxNoteIndexer.scanIfStale()
                        }
                        if (scanResult != null && (scanResult.newNotes > 0 || scanResult.updatedNotes > 0)) {
                            // Refresh UI if new/updated notes found
                            booxNoteRepository.invalidateLinksCache()
                            datesWithBooxNotes = withContext(Dispatchers.IO) {
                                booxNoteRepository.getDatesWithNotesForMonth(currentYearMonth)
                            }
                            // Trigger refresh for events grid badges
                            noteLinkRefreshTrigger++
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Handle sign-in state changes for events badge
    // (Month changes are handled by Phase 4 pre-warm above)
    LaunchedEffect(isSignedIn, hasAnyExternalAccount) {
        if (!isSignedIn && !hasAnyExternalAccount) {
            datesWithEvents = emptySet()
        }
        // When signing in, Phase 4 will populate datesWithEvents on next month load
        // or the sync completion effect below will trigger it
    }

    // Refresh events dates after sync completes (off main thread)
    LaunchedEffect(syncInProgress, multiAccountUiState.isSyncing) {
        val anySyncComplete = !syncInProgress && !multiAccountUiState.isSyncing
        val hasAccounts = isSignedIn || hasAnyExternalAccount
        if (anySyncComplete && hasAccounts) {
            val eventDates = withContext(Dispatchers.IO) {
                eventsViewModel.getDatesWithEventsAroundMonth(currentYearMonth)
            }
            datesWithEvents = eventDates
        }
    }

    // Load or create page when date changes (off main thread)
    // PERF: Check for pre-loaded ID from splash screen first (instant for today)
    LaunchedEffect(selectedDate) {
        val preloadedDate = GlobalAppSettings.preloadedDate
        val preloadedMemosId = GlobalAppSettings.preloadedMemosPageId
        val cachedPageId = appRepository.dayPageRepository.getCachedPageIdForDate(selectedDate)

        if (selectedDate == preloadedDate && preloadedMemosId != null) {
            // Use pre-loaded ID instantly - skip DB call
            currentPageId = preloadedMemosId
            isMemosPageLoading = false
            Log.i(TAG, "PERF: Using pre-loaded memos page ID: $preloadedMemosId")
        } else if (cachedPageId != null) {
            // Use in-memory cache instantly - skip DB call
            currentPageId = cachedPageId
            isMemosPageLoading = false
        } else {
            // Fallback to DB call for other dates
            isMemosPageLoading = true
            val pageId = withContext(Dispatchers.IO) {
                appRepository.dayPageRepository.getOrCreatePageForDate(selectedDate)
            }
            currentPageId = pageId
            isMemosPageLoading = false
        }

        // Multi-page indicator: Update memos page count for tab label
        val pageCount = withContext(Dispatchers.IO) {
            appRepository.dayPageRepository.getPageCount(selectedDate).coerceAtLeast(1)
        }
        memosPageCount = pageCount
    }

    // Collapse expanded events view when date changes (unless pinned)
    LaunchedEffect(selectedDate) {
        if (!isEventsPinned) {
            isEventsExpanded = false
        }
    }

    // OPTION C: No longer need to listen to DrawCanvas.memoModeChanged
    // MainScreen observes tabManager.currentTab directly (line 239)

    // PERFORMANCE: Pre-warm Tasks data when date changes (not just when on Tasks tab)
    // This ensures Tasks canvas can be created immediately when user switches tabs
    // PERF: Set pre-loaded ID immediately for faster canvas mounting
    LaunchedEffect(selectedDate) {
        Log.i(TAG, "PERF: Tasks pre-warm LaunchedEffect start at ${System.currentTimeMillis()}")
        val initialTaskPageIndex = pendingTaskPageNavigationIndex ?: 0
        currentTaskPageIndex = initialTaskPageIndex

        val materializationResult = appRepository.taskRepository.materializeRecurringTasksForDate(
            date = selectedDate,
            canvasHeight = GlobalAppSettings.tasksCanvasHeight
        )
        if (materializationResult.changed) {
            Log.i(
                TAG,
                "PERF: recurring materialization date=$selectedDate created=${materializationResult.createdCount}"
            )
        }

        // Check for pre-loaded ID - set immediately so canvas can mount faster
        val preloadedDate = GlobalAppSettings.preloadedDate
        val preloadedTasksId = GlobalAppSettings.preloadedTasksPageId

        if (initialTaskPageIndex == 0 && selectedDate == preloadedDate && preloadedTasksId != null) {
            // Set pre-loaded ID immediately - canvas can mount now
            currentTaskPageId = preloadedTasksId
            Log.i(TAG, "PERF: Using pre-loaded tasks page ID: $preloadedTasksId")
        }

        // Fast path: use in-memory tasks cache if available
        val cachedTasks = appRepository.taskRepository.getCachedTasksForPage(selectedDate, initialTaskPageIndex)
        if (cachedTasks != null) {
            val (cachedTaskList, cachedPageInfo) = cachedTasks
            currentTasks = cachedTaskList
            currentTaskPageInfo = cachedPageInfo
            currentTaskPageIndex = cachedPageInfo.currentIndex
            cachedTaskList.firstOrNull()?.pageId?.let { cachedPageId ->
                currentTaskPageId = cachedPageId
            }
            isTasksPageLoading = false
        } else {
            isTasksPageLoading = true
            val earlyTaskPageId = withContext(Dispatchers.IO) {
                appRepository.taskRepository.getOrCreateTaskPageId(selectedDate, initialTaskPageIndex)
            }
            if (earlyTaskPageId != null) {
                currentTaskPageId = earlyTaskPageId
            }
        }

        // Still fetch tasks list (needed for rendering) - but canvas can already mount
        val startDb = System.currentTimeMillis()
        val (tasks, pageInfo) = withContext(Dispatchers.IO) {
            appRepository.taskRepository.getOrCreateTasksForPage(selectedDate, initialTaskPageIndex)
        }
        Log.i(TAG, "PERF: getOrCreateTasksForPage took ${System.currentTimeMillis() - startDb}ms")
        currentTasks = tasks
        currentTaskPageInfo = pageInfo
        currentTaskPageIndex = pageInfo.currentIndex
        currentTaskPageId = tasks.firstOrNull()?.pageId
        isTasksPageLoading = false
        pendingTaskPageNavigationIndex = null
        if (pendingTaskCanvasForceRefresh) {
            // STROKE_CLEAR_FIX: Only emit forceUpdate when Tasks tab is active.
            // forceUpdate.emit(null) triggers a full close/open raw drawing cycle on the
            // ACTIVE canvas. If Memos is active, this wipes the Memos bitmap mid-load
            // (strokes not yet loaded from DB), causing stroke loss.
            // See EditorTabManager.kt:254,301 for prior instance of this exact bug.
            if (currentEditorTab == EditorTab.Tasks) {
                DrawCanvas.forceUpdate.emit(null)
            }
            pendingTaskCanvasForceRefresh = false
        }
        Log.i(TAG, "PERF: Tasks pre-warm LaunchedEffect end at ${System.currentTimeMillis()}")
    }

    // PERF: Preload preview bitmaps for neighboring days after current day loads
    // This eliminates disk I/O delay when switching between days
    LaunchedEffect(selectedDate, currentPageId, currentTaskPageId, isMemosPageLoading, isTasksPageLoading) {
        // Only preload when both tabs have finished loading
        if (isMemosPageLoading || isTasksPageLoading) return@LaunchedEffect
        if (currentPageId == null) return@LaunchedEffect

        // Small delay to not compete with initial rendering (reduced from 100ms for faster preload start)
        kotlinx.coroutines.delay(50)

        // Trigger preloading of neighboring days' preview bitmaps (Memos + Tasks)
        PageDataManager.preloadNeighboringPreviews(
            context = context,
            currentDate = selectedDate,
            currentTabPageId = currentPageId!!,
            otherTabPageId = currentTaskPageId,
            dayPageRepository = appRepository.dayPageRepository,
            taskRepository = taskRepository
        )
    }

    // Collapse expanded events view when toolbar is interacted with
    LaunchedEffect(Unit) {
        DrawCanvas.toolbarInteraction.collect {
            isEventsExpanded = false
            isEventsPinned = false
        }
    }

    // Collapse expanded events view when canvas is touched (if not pinned)
    LaunchedEffect(Unit) {
        DrawCanvas.canvasInteraction.collect {
            if (!isEventsPinned) {
                isEventsExpanded = false
            }
        }
    }

    // Signal to DrawCanvas when events overlay is expanded (to block stylus drawing)
    LaunchedEffect(isEventsExpanded) {
        Log.i(TAG, "MainScreen: Setting eventsOverlayExpanded=$isEventsExpanded (isPinned=$isEventsPinned)")
        DrawCanvas.eventsOverlayExpanded.value = isEventsExpanded
    }

    // Listen for Boox Notes signals from toolbar (only when feature is enabled)
    LaunchedEffect(Unit) {
        DrawCanvas.viewBooxNotes.collect {
            autoOpenBulkWizard = false
            showBooxNotesDialog = true
        }
    }

    // Listen for Bulk Smart Link signal from toolbar menu
    LaunchedEffect(Unit) {
        DrawCanvas.openBulkSmartLink.collect {
            autoOpenBulkWizard = true
            showBooxNotesDialog = true
        }
    }

    LaunchedEffect(Unit) {
        DrawCanvas.refreshBooxNotes.collect {
            // PERFORMANCE: Run I/O operations off main thread (filesystem scan + DB queries)
            val (result, dates) = withContext(Dispatchers.IO) {
                val scanResult = booxNoteIndexer.fullScan()
                // Invalidate links cache since note data may have changed
                booxNoteRepository.invalidateLinksCache()
                // Refresh the dates with Boox Notes for the current month
                val booxDates = booxNoteRepository.getDatesWithNotesForMonth(currentYearMonth)
                Pair(scanResult, booxDates)
            }
            datesWithBooxNotes = dates
            // Show result (on Main thread - correct for UI)
            val message = if (result.error != null) {
                "Scan failed: ${result.error}"
            } else {
                "Scanned ${result.totalScanned} notes (${result.newNotes} new)"
            }
            SnackState.globalSnackFlow.tryEmit(SnackConf(text = message, duration = SnackConf.DURATION_QUICK))
        }
    }


    // Show sync errors as brief banners while keeping user in the main screen
    LaunchedEffect(lastSyncMessage) {
        val msg = lastSyncMessage ?: return@LaunchedEffect
        if (msg.contains("failed", ignoreCase = true) ||
            msg.contains("error", ignoreCase = true) ||
            msg.contains("offline", ignoreCase = true) ||
            msg.contains("no internet", ignoreCase = true) ||
            msg.contains("no network", ignoreCase = true)
        ) {
            SnackState.globalSnackFlow.tryEmit(
                SnackConf(text = msg, duration = SnackConf.DURATION_ERROR)
            )
        }
    }

    // Action items and meeting-notes popups (Oct 2026). Placed out here: the InkaTheme content lambda
    // below is at the JVM method-size limit (64 KB) and takes no more code
    com.boox.day.actionitems.ActionItemsHost()
    com.boox.day.meetings.MeetingNotesHost()
    // In-app update popup (Oct 2026, update/AppUpdateGate)
    com.boox.day.ui.dialogs.AppUpdateHost()
    // "BooxDay closed unexpectedly" after a crash (Oct 2026, diagnostics/CrashReportGate)
    com.boox.day.ui.dialogs.CrashReportHost()
    // "AI and your calendar", once before the first AI feature that sends events (Oct 2026, ui/CalendarAiNotice)
    com.boox.day.ui.dialogs.CalendarAiNoticeHost(onOpenAiSettings = { showAiSettingsDialog = true })
    // "Reconnect Google Calendar" for accounts signed in before the narrower scopes (Oct 2026,
    // ui/GoogleReconnectGate): the same Google sign-in as Calendar Accounts → Reconnect
    com.boox.day.ui.dialogs.GoogleReconnectHost(onReconnect = { accountType ->
        activity?.let { act ->
            scope.launch {
                multiAccountSyncViewModel.getGoogleSignInIntent(act, accountType)?.let { multiProviderSignInLauncher.launch(it) }
            }
        }
    })
    // Full screen (Oct 2026, ui/FullScreenState): Back, tidy-up before a switch, full redraw after it
    com.boox.day.ui.FullScreenEffects(tabManager = tabManager, onEnter = { isEventsExpanded = false })

    InkaTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            // Hidden in full screen (Oct 2026); its own lambda also keeps this block out of the root lambda
            CalendarSection {
            // Upper section (40% of total) - Calendar and Toolbar
            // pointerInteropFilter detects finger touches to unfreeze e-ink immediately
            // This allows UI to respond instantly after drawing, instead of waiting 2 seconds
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isCompactLayout) Modifier else Modifier.weight(0.40f))
                    .pointerInteropFilter { event ->
                        // Trigger on initial touch - ANY touch in this area means user wants UI
                        // Whether finger or stylus, tapping calendar/toolbar = not drawing
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            DrawCanvas.forceUnfreeze()
                            // Track user activity for OCR idle detection - aborts any in-progress OCR
                            onUserActivity()
                        }
                        // Return false to let the event propagate to UI elements
                        false
                    }
            ) {
                // Jump to today (the Today button in the top bar)
                val goToToday: () -> Unit = {
                    selectedDate = today
                    viewingCalendar = Calendar.getInstance()
                    if (isSignedIn || hasAnyExternalAccount) {
                        if (hasAnyExternalAccount) {
                            scope.launch { multiAccountSyncViewModel.syncAllAccounts() }
                        } else if (isSignedIn) {
                            syncViewModel.syncMonthIfNeeded(
                                viewingCalendar.get(Calendar.YEAR),
                                viewingCalendar.get(Calendar.MONTH) + 1
                            )
                        }
                    }
                    SnackState.tryEmitRateLimited(
                        SnackConf(key = "nav-today", text = "Jumped to today", duration = SnackConf.DURATION_QUICK)
                    )
                }

                // Month header with toolbar - full width at top
                    MonthHeaderWithToolbar(
                        year = currentYear,
                        month = currentMonth,
                        onPreviousMonth = {
                            if (isCompactLayout) {
                                // Layout B: the arrows move the two-week strip (the month follows it)
                                DrawCanvas.flushUIUpdates()
                                compactWeekAnchor = compactWeekAnchor.plusWeeks(-2)
                            } else {
                            // Unfreeze UI while switching months if raw drawing is active
                            DrawCanvas.flushUIUpdates()
                            viewingCalendar = (viewingCalendar.clone() as Calendar).apply {
                                add(Calendar.MONTH, -1)
                            }
                            if (isSignedIn || hasAnyExternalAccount) {
                                if (hasAnyExternalAccount) {
                                    // Multi-account path handles all connected accounts (Google + Outlook)
                                    scope.launch { multiAccountSyncViewModel.syncAllAccounts() }
                                } else if (isSignedIn) {
                                    // Legacy path only when no multi-account records exist
                                    syncViewModel.syncMonthIfNeeded(
                                        viewingCalendar.get(Calendar.YEAR),
                                        viewingCalendar.get(Calendar.MONTH) + 1
                                    )
                                }
                            }
                        
                            }
                        },
                        onNextMonth = {
                            if (isCompactLayout) {
                                // Layout B: the arrows move the two-week strip (the month follows it)
                                DrawCanvas.flushUIUpdates()
                                compactWeekAnchor = compactWeekAnchor.plusWeeks(2)
                            } else {
                            // Unfreeze UI while switching months if raw drawing is active
                            DrawCanvas.flushUIUpdates()
                            viewingCalendar = (viewingCalendar.clone() as Calendar).apply {
                                add(Calendar.MONTH, 1)
                            }
                            if (isSignedIn || hasAnyExternalAccount) {
                                if (hasAnyExternalAccount) {
                                    scope.launch { multiAccountSyncViewModel.syncAllAccounts() }
                                } else if (isSignedIn) {
                                    syncViewModel.syncMonthIfNeeded(
                                        viewingCalendar.get(Calendar.YEAR),
                                        viewingCalendar.get(Calendar.MONTH) + 1
                                    )
                                }
                            }
                        
                            }
                        },
                    onTodayClick = goToToday,
                    onVoiceInput = {
                        // Voice input uses OpenRouter AI (Gemini), not Google Calendar
                        // Works in both Standalone and Hybrid modes - no sign-in required
                        // Needs the user's own OpenRouter key (Oct 2026); otherwise the key popup opens
                        if (com.boox.day.ui.AiKeyGate.requireKey()) {
                            parsedEventData = null  // Clear any previous parsed data
                            showVoiceEventDialog = true
                        }
                    },
                    onAddEventClick = {
                        // Allow adding events in standalone mode (no sign-in required)
                        editingEvent = null
                        parsedEventData = null  // Clear voice data when opening manually
                        showAddEventDialog = true
                    },
                    onSyncClick = {
                        // Check BOTH legacy sign-in AND new multi-provider accounts
                        val hasAccounts = isSignedIn || hasAnyExternalAccount

                        if (hasAccounts) {
                            // Use rate-limited emit with semantic key to prevent snackbar stacking
                            SnackState.tryEmitRateLimited(
                                SnackConf(
                                    key = "sync-calendars",
                                    text = "Syncing calendars...",
                                    duration = SnackConf.DURATION_QUICK
                                )
                            )
                            // Trigger sync via multi-account system (handles both Google and Outlook)
                            // Legacy Google sign-ins are auto-migrated to database on loadAccounts()
                            multiAccountSyncViewModel.syncAllAccounts()
                        } else {
                            showCalendarSettingsDialog = true
                        }
                    },
                    onScreenRefresh = {
                        refreshScreen()
                    },
                    onSearchClick = {
                        showSearchDialog = true
                    },
                    onPreferences = {
                        showPreferencesDialog = true
                    },
                    onCalendarSettings = {
                        showCalendarSettingsDialog = true
                    },
                    onCalendarAccounts = {
                        showMultiProviderSyncDialog = true
                    },
                    onAiSettings = {
                        showAiSettingsDialog = true
                    },
                    onVoiceSettings = {
                        showVoiceSettingsDialog = true
                    },
                    onDataBackup = {
                        showDataBackupDialog = true
                    },
                    onBooxNotesSettings = {
                        showBooxNotesSettingsDialog = true
                    },
                    onHelp = {
                        showHelpDialog = true
                    },
                    onAbout = {
                        showAboutDialog = true
                    },
                    onMonthYearSelected = { newYear, newMonth ->
                        viewingCalendar = Calendar.getInstance().apply {
                            set(Calendar.YEAR, newYear)
                            set(Calendar.MONTH, newMonth)
                            set(Calendar.DAY_OF_MONTH, 1)
                        }
                        // Also update selected date to first day of new month
                        selectedDate = String.format(
                            java.util.Locale.getDefault(),
                            "%04d-%02d-01",
                            newYear,
                            newMonth + 1
                        )
                        if (isSignedIn || hasAnyExternalAccount) {
                            if (hasAnyExternalAccount) {
                                scope.launch { multiAccountSyncViewModel.syncAllAccounts() }
                            } else if (isSignedIn) {
                                syncViewModel.syncMonthIfNeeded(newYear, newMonth + 1)
                            }
                        }
                    },
                    // Combine sync states from both systems
                    isSyncing = syncInProgress || multiAccountUiState.isSyncing,
                    lastSyncTime = lastSyncTime,
                    // Sync status indicators
                    isSignedIn = isSignedIn || hasAnyExternalAccount,
                    pendingSyncCount = pendingSyncCount, // Local events pending sync to cloud
                    hasSyncError = lastSyncMessage?.contains("failed", ignoreCase = true) == true ||
                                   lastSyncMessage?.contains("error", ignoreCase = true) == true ||
                                   lastSyncMessage?.contains("offline", ignoreCase = true) == true ||
                                   lastSyncMessage?.contains("no internet", ignoreCase = true) == true ||
                                   lastSyncMessage?.contains("no network", ignoreCase = true) == true,
                    // Disable "Calendar Accounts" menu when in Standalone mode
                    isStandaloneMode = syncModeSetting == AppSettings.SyncMode.STANDALONE,
                    // First-run onboarding setup checklist for badge display
                    setupChecklist = setupChecklist,
                    compact = isCompactLayout,
                    onMonthTitleClick = { showCompactMonthPopup = true },
                    onCompactPopupVisibleChange = { compactHeaderPopupVisible = it }
                )

                if (isCompactLayout) {
                    CompactCalendarRows(
                        anchorDate = compactWeekAnchor,
                        selectedDate = selectedDate,
                        today = today,
                        badges = CalendarBadges(
                            datesWithNotes = datesWithNotesSet,
                            datesWithEvents = datesWithEvents,
                            datesWithBooxNotes = datesWithBooxNotes,
                            datesWithTasks = datesWithTasks
                        ),
                        firstDayOfWeekSetting = firstDayOfWeek,
                        eventsCount = eventsPanelEventsCount,
                        eventsPreview = compactEventsPreview,
                        dueCount = eventsPanelDueCount,
                        duePreview = compactDuePreview,
                        onDateSelected = { date ->
                            if (date != selectedDate) {
                                selectedDate = date
                            }
                        },
                        onOpenList = { tab ->
                            selectedEventsTab = tab
                            compactEventsListDate = selectedDate
                            showCompactEventsPopup = true
                        }
                    )
                }

                // Main content row: Calendar (60%) | Events (40%)
                if (!isCompactLayout) Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                  // The Row's content is not re-indented on purpose (small diff): it lives in its own lambda class.
                  SplitOutOfMainScreen {
                    // Left column (60%; 56% on narrow screens): Day header + Weekday header + Calendar grid
                    // (week numbers are hidden on narrow screens such as the Go 6 inside DayOfWeekHeader / CalendarGrid)
                    Column(
                        modifier = Modifier
                            .weight(1f - EInkTheme.EventsPanel.widthFraction)
                            .fillMaxHeight()
                    ) {
                        // Selected day: big date and the marks of that day
                        SelectedDayHeader(
                            selectedDate = selectedDate,
                            datesWithBooxNotes = datesWithBooxNotes,
                            datesWithNotes = datesWithNotesSet,
                            datesWithTasks = datesWithTasks,
                            tabManager = tabManager,
                            memosPageId = currentPageId,
                            tasksPageId = currentTaskPageId
                        )

                        // Weekdays header with tab indicator
                        DayOfWeekHeader(
                            selectedDate = selectedDate,
                            firstDayOfWeekSetting = firstDayOfWeek,
                            showWeekNumbers = showWeekNumbers
                        )

                        // Calendar grid
                        CalendarGrid(
                            year = currentYear,
                            month = currentMonth,
                            selectedDate = selectedDate,
                            today = today,
                            badges = CalendarBadges(
                                datesWithNotes = datesWithNotesSet,
                                datesWithEvents = datesWithEvents,
                                datesWithBooxNotes = datesWithBooxNotes,
                                datesWithTasks = datesWithTasks
                            ),
                            onDateSelected = { date ->
                                if (PERF_LOGS) Log.i(TAG, "PERF_TAP: Date tapped: $date at ${System.currentTimeMillis()}")
                                // Close expanded events grid when tapping calendar (if not pinned)
                                if (isEventsExpanded && !isEventsPinned) {
                                    isEventsExpanded = false
                                }
                                if (date != selectedDate) {
                                    selectedDate = date
                                }
                            },
                            onDayLongPress = { date ->
                                Log.i(TAG, "Day long-pressed: $date")
                                quickViewDate = date
                                showDayQuickView = true
                            },
                            firstDayOfWeekSetting = firstDayOfWeek,
                            showWeekNumbers = showWeekNumbers,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }

                    // Right column (40%): Events header + Events grid in ONE bordered container
                    if (!isEventsExpanded) {
                        // E-ink (15 Oct 2026): one screen pixel of solid black (about 0.5 dp: 0.53 dp on the
                        // Go 6, 0.42 dp on the Go 10.3), on the pixel grid, drawn over the panel's content and
                        // inside its bounds. The 0.5 dp grey line drawn behind it lost its inner half under the
                        // content and showed as one light-grey pixel column, which the e-ink panel renders as
                        // white; a line thinner than a pixel is blended to grey the same way.
                        val borderColor = EInkTheme.Colors.primary
                        Column(
                            modifier = Modifier
                                .weight(EInkTheme.EventsPanel.widthFraction)
                                .fillMaxHeight()
                                .background(EInkTheme.EventsPanel.rowBackground)
                                // 3-sided border (top, left, right) - no bottom, MainScreen Divider serves as separator
                                .drawWithContent {
                                    drawContent()
                                    val strokeWidth = 1f  // one screen pixel
                                    val half = strokeWidth / 2f  // centre of the edge pixel
                                    // Top border
                                    drawLine(borderColor, Offset(0f, half), Offset(size.width, half), strokeWidth)
                                    // Left border
                                    drawLine(borderColor, Offset(half, 0f), Offset(half, size.height), strokeWidth)
                                    // Right border
                                    drawLine(borderColor, Offset(size.width - half, 0f), Offset(size.width - half, size.height), strokeWidth)
                                }
                        ) {
                            // Events header with tabs and filter icons/date
                            // CRITICAL: EventsPanelTabs is an isolated composable to prevent
                            // recomposition propagation when date changes. See docs/EVENTS_PANEL_TABS_RECOMPOSITION_FIX_PLAN.md
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 10.dp, end = 7.dp, top = 8.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Isolated tabs component - won't recompose when Events panel recomposes
                                EventsPanelTabs(
                                    selectedTab = selectedEventsTab,
                                    onTabSelected = { selectedEventsTab = it },
                                    eventsCount = eventsPanelEventsCount,  // Already an Int from count flow
                                    dueCount = eventsPanelDueCount
                                )
                                // Right side: Filter icons (when connected accounts AND Events tab) OR Date label
                                val showFilterIconsCollapsed = (hasGoogleAccount || hasOutlookAccount) && selectedEventsTab == "events"
                                if (LayoutProfiles.isNarrowScreen && !showFilterIconsCollapsed) {
                                    // Narrow screens (Go 6): no date label, the day header beside this panel
                                    // shows the selected date (Oct 2026)
                                } else if (showFilterIconsCollapsed) {
                                    // Source filter icons (compact for collapsed header)
                                    CalendarSourceFilterIcons(
                                        filter = sourceFilter,
                                        onToggleSource = { source ->
                                            val success = eventsViewModel.toggleSource(source)
                                            if (!success) {
                                                showHint("At least one calendar must be selected", scope)
                                            }
                                            success
                                        },
                                        hasGoogleAccount = hasGoogleAccount,
                                        hasOutlookAccount = hasOutlookAccount,
                                        selectedDayOfMonth = selectedDate.split("-").getOrNull(2)?.toIntOrNull() ?: 1,
                                        isCompact = true,
                                    )
                                } else {
                                    // Date label (standalone mode or Due tab)
                                    Text(
                                        text = remember(selectedDate) {
                                            try {
                                                val parts = selectedDate.split("-")
                                                val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                                                "${months[parts[1].toInt() - 1]} ${parts[2].toInt()}, ${parts[0]}"
                                            } catch (e: Exception) { selectedDate }
                                        },
                                        fontSize = EInkTheme.EventsPanel.timeFontSize,
                                        color = Color.Black
                                    )
                                }
                            }

                            // Events grid (without its own border or header)
                            // Note: Filter icons are now in the header above, not inside EventsGrid
                            EventsGrid(
                                selectedDate = selectedDate,
                                eventsViewModel = eventsViewModel,
                                booxNoteRepository = booxNoteRepository,
                                booxNotesIntegration = booxNotesIntegration,
                                showHeader = false,
                                onToggleExpanded = { isEventsExpanded = true },
                                onEventClick = { event ->
                                    selectedEventForDetails = event
                                    showEventDetailsDialog = true
                                },
                                noteLinkRefreshTrigger = noteLinkRefreshTrigger,
                                isSyncing = syncInProgress,
                                selectedEventsTab = selectedEventsTab,
                                focusEventId = agenticFocusEventId,
                                onFocusedEventShown = { agenticFocusEventId = null },
                                onDueTaskClick = { task ->
                                    // Open the task (its day, page and row) — plan T3
                                    goToTask(task.id, task.dayDate)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        }
                    } else {
                        // Empty space when expanded (overlay covers this)
                        Spacer(modifier = Modifier.weight(EInkTheme.EventsPanel.widthFraction))
                    }
                  }  // end SplitOutOfMainScreen
                }
            }

            Divider(color = EInkTheme.Colors.borderStrong, thickness = 0.5.dp)
            }  // end CalendarSection

            // Drawing Canvas Area (60% of total; the whole screen in full screen)
            // OPTION C: Single SharedToolbar + Dual Canvases
            // PERFORMANCE: Keep BOTH tabs' DrawCanvas instances alive to avoid recreating
            // TouchHelper (setupSurface ~130ms) on each tab switch. Use visibility + z-index
            // to show/hide instead of removing from composition tree.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (isCompactLayout || com.boox.day.ui.FullScreenState.isOn) 1f else 0.60f)
            ) {
                // Full screen: the selected day above the toolbar (the calendar header is hidden)
                com.boox.day.ui.FullScreenDateLine(selectedDate)
                // SHARED TOOLBAR (single instance for both canvases)
                // PERFORMANCE: Eliminates dual-toolbar sync overhead and latency
                // NOTE: Do NOT add pointerInteropFilter here - it interferes with toolbar's
                // own tool selection logic (forceUnfreeze re-enables drawing after 50ms,
                // undoing the toolbar's disable when switching to non-drawing tools like AI)
                com.boox.day.editor.ui.toolbar.SharedToolbar(
                    navController = navController,
                    tabManager = tabManager,
                    selectedDate = selectedDate,  // BUG FIX: Pass date to invalidate toolbar cache on day switch
                    memosPageId = currentPageId, // STALENESS FIX: Pass current memos page ID
                    tasksPageId = currentTaskPageId, // STALENESS FIX: Pass current tasks page ID
                    // Multi-page indicator: Show count in tab label when > 1 page exists
                    memosPageCount = memosPageCount,
                    tasksPageCount = currentTaskPageInfo?.totalPages ?: 1,
                    taskPageInfo = currentTaskPageInfo,
                    onTaskPageNav = { delta ->
                        navigateTaskPage(delta)
                    },
                    onQuickAddTask = {
                        // Full screen (Oct 2026): this tool runs in the normal view (ui/FullScreenState)
                        com.boox.day.ui.FullScreenState.exitThen {
                            // T13 (Oct 2026): ink counts — Quick Add never types into a row with
                            // handwriting (findFirstEmptyRow checks strokes, from the DB when the
                            // page isn't loaded), and following pages are checked the same way
                            // (row 1 of the next page used to be taken unchecked).
                            val quickAddCanvasHeight = actualCanvasHeight
                            val pageTasks = currentTasks
                            val startPageIndex = currentTaskPageInfo?.currentIndex ?: 0
                            scope.launch {
                                val (targetTask, otherPage) = withContext(Dispatchers.IO) {
                                    val repo = appRepository.taskRepository
                                    val rowHere = repo.findFirstEmptyRow(pageTasks, quickAddCanvasHeight)
                                    if (rowHere < 10) {
                                        pageTasks.getOrNull(rowHere) to null
                                    } else {
                                        // Next pages in order; the one after the last page is created
                                        val lastPageIndex = maxOf(repo.getMaxTaskPageIndex(selectedDate), startPageIndex) + 1
                                        var found: Pair<Task?, Pair<List<Task>, TaskPageInfo>?> = null to null
                                        for (pageIndex in (startPageIndex + 1)..lastPageIndex) {
                                            val (tasks, pageInfo) = repo.getOrCreateTasksForPage(selectedDate, pageIndex)
                                            val row = repo.findFirstEmptyRow(tasks, quickAddCanvasHeight)
                                            if (row < 10) {
                                                found = tasks.getOrNull(row) to (tasks to pageInfo)
                                                break
                                            }
                                        }
                                        found
                                    }
                                }
                                otherPage?.let { (tasks, pageInfo) ->
                                    currentTasks = tasks
                                    currentTaskPageInfo = pageInfo
                                    currentTaskPageIndex = pageInfo.currentIndex
                                    currentTaskPageId = tasks.firstOrNull()?.pageId
                                }
                                quickAddTargetTask = targetTask
                            }
                        }
                    },
                    // Tasks bulk actions - consolidated with scope selection in dialog
                    onTaskMoveTasks = {
                        // Full screen (Oct 2026): this tool runs in the normal view (ui/FullScreenState)
                        com.boox.day.ui.FullScreenState.exitThen {
                            activeTaskOperation = com.boox.day.editor.ui.TaskOperationType.MOVE
                        }
                    },
                    onTaskCopyTasks = {
                        // Full screen (Oct 2026): this tool runs in the normal view (ui/FullScreenState)
                        com.boox.day.ui.FullScreenState.exitThen {
                            activeTaskOperation = com.boox.day.editor.ui.TaskOperationType.COPY
                        }
                    },
                    onTaskDeleteTasks = {
                        // Full screen (Oct 2026): this tool runs in the normal view (ui/FullScreenState)
                        com.boox.day.ui.FullScreenState.exitThen {
                            activeTaskOperation = com.boox.day.editor.ui.TaskOperationType.DELETE
                        }
                    },
                    onStartDayReview = {
                        // Full screen (Oct 2026): this tool runs in the normal view (ui/FullScreenState)
                        com.boox.day.ui.FullScreenState.exitThen {
                            openStartDayReview(
                                lastReviewDay = GlobalAppSettings.current.lastStartDayReviewDay,
                                showWhenEmpty = true
                            )
                        }
                    },
                    onConvertHandwriting = {
                        // Full screen (Oct 2026): this tool runs in the normal view (ui/FullScreenState)
                        com.boox.day.ui.FullScreenState.exitThen {
                            showConvertHandwritingDialog = true
                        }
                    },
                    // Task page deletion (specialized logic for Task entities - triggered from hamburger menu)
                    onDeleteTaskPage = {
                        // Full screen (Oct 2026): this tool runs in the normal view (ui/FullScreenState)
                        com.boox.day.ui.FullScreenState.exitThen {
                            showDeleteTaskPageConfirmation = true
                        }
                    },
                    // AI callbacks for Tasks
                    onVoiceToTask = {
                        // Full screen (Oct 2026): this tool runs in the normal view (ui/FullScreenState)
                        com.boox.day.ui.FullScreenState.exitThen {
                            scope.launch {
                                // Free task rows for the results (TaskSlotCalculator: at most 10 task pages a day)
                                val slots = TaskSlotCalculator.getAvailableSlots(
                                    date = selectedDate,
                                    taskRepository = appRepository.taskRepository,
                                    strokeRepository = appRepository.strokeRepository,
                                    canvasHeight = actualCanvasHeight
                                )
                                if (slots.totalAvailable == 0) {
                                    showNoTaskSlotsDialog = true
                                    return@launch  // EXIT - No slots, don't waste an AI call
                                }

                                showVoiceToTaskDialog = true
                            }
                        }
                    },
                    onSortByPriority = {
                        // Full screen (Oct 2026): this tool runs in the normal view (ui/FullScreenState)
                        com.boox.day.ui.FullScreenState.exitThen {
                            showSortScopePopup = true
                        }
                    },
                    onDailySummary = {
                        // Full screen (Oct 2026): this tool runs in the normal view (ui/FullScreenState)
                        com.boox.day.ui.FullScreenState.exitThen {
                            dailySummaryProcessingState = DailySummaryProcessingState.Processing("Checking cache...")
                            dailySummaryResult = null
                            showDailySummaryDialog = true

                            scope.launch {
                                val cacheId = SummaryCache.dailyId(selectedDate)
                                val (tasksForDay, contentHash) = withContext(Dispatchers.IO) {
                                    val tasks = appRepository.taskRepository.getTasksForDay(selectedDate)
                                    val hash = TaskAiService.computeDailySummaryContentHash(tasks)
                                    tasks to hash
                                }
                                val tasksById = tasksForDay.associateBy { it.id }

                                // Check cache first (valid for 4 hours)
                                val cachedEntry = withContext(Dispatchers.IO) {
                                    summaryCacheRepository.getValidCache(cacheId, contentHash)
                                }

                                if (cachedEntry != null) {
                                    // Cache hit - deserialize and show
                                    dailySummaryProcessingState = DailySummaryProcessingState.Processing("Loading cached summary...")
                                    val cached = TaskAiService.deserializeDailySummary(
                                        json = cachedEntry.resultJson,
                                        taskLookup = { taskIds ->
                                            taskIds.mapNotNull { tasksById[it] }
                                        }
                                    )
                                    if (cached != null) {
                                        dailySummaryResult = cached
                                        dailySummaryProcessingState = DailySummaryProcessingState.Ready
                                        return@launch
                                    }
                                    // Cache invalid, fall through to generate fresh
                                }

                                // A new summary needs the user's own OpenRouter key (Oct 2026)
                                if (!com.boox.day.ui.AiKeyGate.requireKey()) {
                                    showDailySummaryDialog = false
                                    return@launch
                                }
                                // It sends today's events: the once-only notice (ui/CalendarAiNotice)
                                if (!com.boox.day.ui.CalendarAiNotice.awaitAllowed()) {
                                    showDailySummaryDialog = false
                                    return@launch
                                }

                                // Generate fresh summary using Intelligence Engine V3
                                dailySummaryProcessingState = DailySummaryProcessingState.Processing("Analyzing your day...")

                                // Get scored context from Intelligence Engine
                                val intelligenceEngine = IntelligenceEngine(context)
                                val scoredContext = intelligenceEngine.getScoredContext(
                                    feature = IntelligenceFeature.DAILY_SUMMARY,
                                    tasks = tasksForDay
                                )

                                val result = TaskAiService.generateDailySummaryV3(
                                    tasksForDay = tasksForDay,
                                    currentDate = LocalDate.parse(selectedDate),
                                    density = context.resources.displayMetrics.density,
                                    scoredContext = scoredContext,
                                    onStageChange = { stage ->
                                        dailySummaryProcessingState = DailySummaryProcessingState.Processing(stage)
                                    },
                                    context = context
                                )

                                // Save to cache and record usage if successful
                                if (result.error == null) {
                                    withContext(Dispatchers.IO) {
                                        val json = TaskAiService.serializeDailySummary(result)
                                        val refreshedTasks = appRepository.taskRepository.getTasksForDay(selectedDate)
                                        val cacheContentHash = TaskAiService.computeDailySummaryContentHash(refreshedTasks)
                                        summaryCacheRepository.save(SummaryCache(
                                            id = cacheId,
                                            type = SummaryCache.TYPE_DAILY,
                                            dateKey = selectedDate,
                                            contentHash = cacheContentHash,
                                            resultJson = json,
                                            generatedAt = System.currentTimeMillis(),
                                            validHours = SummaryCache.DAILY_VALID_HOURS
                                        ))
                                    }
                                }

                                dailySummaryResult = result
                                dailySummaryProcessingState = if (result.error != null) {
                                    DailySummaryProcessingState.Error(result.error!!)
                                } else {
                                    DailySummaryProcessingState.Ready
                                }
                            }
                        }
                    },
                    onWeeklySummary = {
                        // Full screen (Oct 2026): this tool runs in the normal view (ui/FullScreenState)
                        com.boox.day.ui.FullScreenState.exitThen {
                            launchWeeklySummary(forceRefresh = false)
                        }
                    },
                    onSmartSuggestions = {
                        // Full screen (Oct 2026): this tool runs in the normal view (ui/FullScreenState)
                        com.boox.day.ui.FullScreenState.exitThen {
                            // ─────────────────────────────────────────────────────────────────
                            // SMART SUGGESTIONS V2 - Full Context AI Personal Assistant
                            // ─────────────────────────────────────────────────────────────────

                            scope.launch {
                                // Needs the user's own OpenRouter key (Oct 2026)
                                if (!com.boox.day.ui.AiKeyGate.requireKey()) return@launch
                                // It sends events: the once-only notice (ui/CalendarAiNotice)
                                if (!com.boox.day.ui.CalendarAiNotice.awaitAllowed()) return@launch
                                // Free task rows for the results (TaskSlotCalculator: at most 10 task pages a day)
                                val slots = TaskSlotCalculator.getAvailableSlots(
                                    date = selectedDate,
                                    taskRepository = appRepository.taskRepository,
                                    strokeRepository = appRepository.strokeRepository,
                                    canvasHeight = actualCanvasHeight
                                )
                                if (slots.totalAvailable == 0) {
                                    showNoTaskSlotsDialog = true
                                    return@launch  // EXIT - No slots, don't waste an AI call
                                }

                                // Check if we should show proactive preferences prompt
                                if (aiPreferencesPromptManager.shouldShowProactivePrompt()) {
                                    aiPreferencesPromptManager.onProactivePromptShown()
                                    showAiPreferencesInvitePopup = true
                                }

                                // Initialize dialog state
                                smartSuggestionsProcessingState = SmartSuggestionsProcessingState.Processing("Gathering context...")
                                smartSuggestionsResult = null
                                showSmartSuggestionsDialog = true
                                val currentDate = LocalDate.parse(selectedDate)

                                // ─────────────────────────────────────────────────────────────────
                                // STAGE 1: Build OCR results for handwritten tasks (Digital Ink API)
                                // ─────────────────────────────────────────────────────────────────
                                smartSuggestionsProcessingState = SmartSuggestionsProcessingState.Processing("Reading handwritten tasks...")

                                val tasksToRecognize = currentTasks.filter {
                                    it.typedText.isNullOrBlank() && !it.isCompleted
                                }

                                val ocrBatchResult = TaskOcrService.getTextForTasks(
                                    tasks = tasksToRecognize,
                                    density = context.resources.displayMetrics.density
                                )

                                val ocrResults = ocrBatchResult.results
                                    .filter { it.value.isHandwritten && it.value.text.isNotBlank() }
                                    .mapValues { it.value.text }

                                // ─────────────────────────────────────────────────────────────────
                                // STAGE 2: Build full context using SuggestionContextBuilder
                                // ─────────────────────────────────────────────────────────────────
                                val suggestionContext = SuggestionContextBuilder.buildContext(
                                    context = context,
                                    currentDate = currentDate,
                                    pageDataManager = com.boox.day.data.PageDataManager,
                                    includeMemoContext = true,  // Enable memo OCR for richer context
                                    includePatternAnalysis = true,
                                    ocrResults = ocrResults,
                                    onStageChange = { stage ->
                                        smartSuggestionsProcessingState = SmartSuggestionsProcessingState.Processing(stage)
                                    }
                                )

                                // ─────────────────────────────────────────────────────────────────
                                // STAGE 2.5: Get scored context from Intelligence Engine
                                // ─────────────────────────────────────────────────────────────────
                                smartSuggestionsProcessingState = SmartSuggestionsProcessingState.Processing("Analyzing priorities...")
                                val intelligenceEngine = IntelligenceEngine(context)
                                val scoredContext = intelligenceEngine.getScoredContextFromExisting(
                                    feature = IntelligenceFeature.SMART_SUGGESTIONS,
                                    existingContext = suggestionContext,
                                    tasks = currentTasks,
                                    taskTextOverrides = ocrResults
                                )

                                // ─────────────────────────────────────────────────────────────────
                                // STAGE 3: Generate V3 smart suggestions with pre-scored context
                                // ─────────────────────────────────────────────────────────────────
                                val result = TaskAiService.generateSmartSuggestionsV3(
                                    context = context,
                                    scoredContext = scoredContext,
                                    suggestionContext = suggestionContext,
                                    onStageChange = { stage ->
                                        smartSuggestionsProcessingState = SmartSuggestionsProcessingState.Processing(stage)
                                    }
                                )

                                smartSuggestionsResult = result
                                smartSuggestionsProcessingState = if (result.error != null) {
                                    SmartSuggestionsProcessingState.Error(result.error!!)
                                } else {
                                    SmartSuggestionsProcessingState.Ready
                                }
                            }
                        }
                    },
                    onTaskBreakdown = {
                        // Full screen (Oct 2026): this tool runs in the normal view (ui/FullScreenState)
                        com.boox.day.ui.FullScreenState.exitThen {
                            scope.launch {
                                // Needs the user's own OpenRouter key (Oct 2026)
                                if (!com.boox.day.ui.AiKeyGate.requireKey()) return@launch
                                // Free task rows for the results (TaskSlotCalculator: at most 10 task pages a day)
                                val slots = TaskSlotCalculator.getAvailableSlots(
                                    date = selectedDate,
                                    taskRepository = appRepository.taskRepository,
                                    strokeRepository = appRepository.strokeRepository,
                                    canvasHeight = actualCanvasHeight
                                )
                                if (slots.totalAvailable == 0) {
                                    showNoTaskSlotsDialog = true
                                    return@launch  // EXIT - No slots, don't waste an AI call
                                }

                                // Start with task selection - first detect handwritten tasks via OCR
                                taskBreakdownProcessingState = TaskBreakdownProcessingState.ProcessingOcr
                                taskBreakdownResult = null
                                selectedTaskForBreakdown = null
                                tasksForBreakdown = emptyList()
                                showTaskBreakdownDialog = true
                                // Compute set of task IDs that already have children
                                // These parent tasks should not be eligible for breakdown again
                                val parentTaskIds = withContext(Dispatchers.IO) {
                                    currentTasks
                                        .filter { appRepository.taskRepository.hasChildren(it.id) }
                                        .map { it.id }
                                        .toSet()
                                }

                                val tasks = TaskAiService.getTasksForBreakdownWithOcr(
                                    tasks = currentTasks,
                                    density = context.resources.displayMetrics.density,
                                    parentTaskIds = parentTaskIds
                                )
                                tasksForBreakdown = tasks
                                taskBreakdownProcessingState = TaskBreakdownProcessingState.SelectTask
                            }
                        }
                    },
                    // Memos AI - Convert Memo to Tasks
                    onConvertMemoToTasks = {
                        // Full screen (Oct 2026): this tool runs in the normal view (ui/FullScreenState)
                        com.boox.day.ui.FullScreenState.exitThen {
                            scope.launch {
                                // Needs the user's own OpenRouter key (Oct 2026)
                                if (!com.boox.day.ui.AiKeyGate.requireKey()) return@launch
                                // Free task rows for the results (TaskSlotCalculator: at most 10 task pages a day)
                                val slots = TaskSlotCalculator.getAvailableSlots(
                                    date = selectedDate,
                                    taskRepository = appRepository.taskRepository,
                                    strokeRepository = appRepository.strokeRepository,
                                    canvasHeight = actualCanvasHeight
                                )
                                if (slots.totalAvailable == 0) {
                                    showNoTaskSlotsDialog = true
                                    return@launch  // EXIT - No slots, don't waste an AI call
                                }

                                // Reset state and show scope selection first
                                memoToTasksTargetDate = selectedDate
                                memoToTasksResult = null
                                memoToTasksProcessingState = MemoToTasksProcessingState.Processing("Starting...")
                                memoToTasksSkipScopeSelection = false  // Show scope selection for toolbar trigger
                                showMemoToTasksDialog = true
                            }
                        }
                    },
                    // Plan My Day - Agentic Planning System
                    onPlanMyDay = {
                        // Full screen (Oct 2026): this tool runs in the normal view (ui/FullScreenState)
                        com.boox.day.ui.FullScreenState.exitThen {
                            if (!com.boox.day.ui.AiKeyGate.requireKey()) {
                                // Needs the user's own OpenRouter key (Oct 2026): the key popup is open
                            } else com.boox.day.ui.CalendarAiNotice.afterNotice {  // sends events: notice once (Oct 2026)
                                agenticPlanProcessingState = com.boox.day.data.ai.AgenticPlanProcessingState.Processing("Initializing...")
                                agenticPlanResult = null
                                agenticPendingTaskRefreshTarget = null
                                agenticNeedsVisibleTaskFlush = false
                                showAgenticPlanDialog = true

                                scope.launch {
                                // Build context and generate plan using Intelligence Engine V3
                                val contextBuilder = com.boox.day.ai.AgenticContextBuilder(context)
                                val planningContext = contextBuilder.buildContext(
                                    planningDate = LocalDate.parse(selectedDate),
                                    onStageChange = { stage ->
                                        agenticPlanProcessingState = com.boox.day.data.ai.AgenticPlanProcessingState.Processing(stage)
                                    }
                                )

                                // Get scored context from Intelligence Engine
                                agenticPlanProcessingState = com.boox.day.data.ai.AgenticPlanProcessingState.Processing("Analyzing priorities...")
                                val allPlanningTasks = planningContext.todayTasks + planningContext.overdueTasks + planningContext.zombieTasks
                                val intelligenceEngine = IntelligenceEngine(context)
                                val scoredContext = intelligenceEngine.getScoredContext(
                                    feature = IntelligenceFeature.PLAN_MY_DAY,
                                    tasks = allPlanningTasks,
                                    referenceDate = planningContext.planningDate,
                                    referenceHour = planningContext.currentHour
                                )

                                // Generate proposals with V3
                                val result = com.boox.day.ai.AgenticPlanningService.generatePlanV3(
                                    context = planningContext,
                                    scoredContext = scoredContext,
                                    onStageChange = { stage ->
                                        agenticPlanProcessingState = com.boox.day.data.ai.AgenticPlanProcessingState.Processing(stage)
                                    }
                                )

                                agenticPlanResult = result
                                agenticPlanProcessingState = if (result.error != null) {
                                    com.boox.day.data.ai.AgenticPlanProcessingState.Error(result.error)
                                } else {
                                    com.boox.day.data.ai.AgenticPlanProcessingState.Ready
                                }
                                }
                            }
                        }
                    },
                    // Task stats
                    taskStats = remember(currentTasks, currentTaskPageInfo) {
                        val completed = currentTasks.count { it.isCompleted }
                        val withContent = currentTasks.count { task ->
                            task.typedText?.isNotBlank() == true ||
                            task.isCompleted ||
                            task.priority.isNotEmpty()
                        }
                        com.boox.day.editor.ui.toolbar.TaskStats(
                            completedCount = completed,
                            totalWithContent = withContent,
                            emptySlots = currentTasks.size - withContent,
                            totalPages = currentTaskPageInfo?.totalPages ?: 1
                        )
                    },
                    // Canvas height for stroke detection in task export
                    canvasHeight = actualCanvasHeight,
                    // Boox Notes scan callback - triggered when permission/toggle changes in dialog
                    onBooxNotesScanNeeded = triggerBooxNotesScan,
                    // Boox Notes enabled state - shared for sync between Toolbar and MainScreen dialogs
                    booxNotesEnabled = booxNotesEnabled,
                    onBooxNotesEnabledChanged = {
                        booxNotesEnabled = true
                        val newSettings = GlobalAppSettings.current.copy(booxNotesEnabled = true)
                        GlobalAppSettings.update(newSettings)
                        scope.launch(Dispatchers.IO) {
                            appRepository.kvProxy.setAppSettings(newSettings)
                        }
                    },
                    onBooxOcrSettingsChanged = { newOcrSettings ->
                        val newSettings = GlobalAppSettings.current.copy(ocrSettings = newOcrSettings)
                        GlobalAppSettings.update(newSettings)
                        scope.launch(Dispatchers.IO) {
                            appRepository.kvProxy.setAppSettings(newSettings)
                        }
                    },
                    ocrProcessingState = ocrProcessingState,
                    ocrRepository = ocrRepository,
                    onScreenRefresh = { refreshScreen() }
                )

                // Canvas container
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                val showMemos = currentEditorTab == EditorTab.Memos
                val showTasks = currentEditorTab == EditorTab.Tasks

                // Log tab switches for performance monitoring
                LaunchedEffect(currentEditorTab) {
                    if (PERF_LOGS) Log.i(TAG, "PERF: Tab switch to $currentEditorTab at ${System.currentTimeMillis()}")
                }

                if (currentPageId != null && currentTaskPageId != null) {
                    PersistentCanvasHost(
                        navController = navController,
                        selectedDate = selectedDate,
                        appRepository = appRepository,
                        snackManager = snackManager,
                        scope = scope,
                        tabManager = tabManager,
                        currentTab = currentEditorTab,
                        memosPageId = currentPageId!!,
                        tasksPageId = currentTaskPageId!!,
                        tasks = currentTasks,
                        taskPageInfo = currentTaskPageInfo,
                        memosIsLoading = isMemosPageLoading,
                        tasksIsLoading = isTasksPageLoading,
                        onToggleTaskCompleted = { task ->
                            DrawCanvas.forceUnfreeze()  // Unfreeze UI for immediate visual feedback
                            // GROUP-AWARE: If marking parent complete, update all children in UI
                            val newCompletedState = !task.isCompleted
                            val isParentTask = task.parentTaskId == null

                            scope.launch(Dispatchers.IO) {
                                // Only propagate to children if marking a PARENT task as COMPLETE
                                val taskIdsToUpdate = if (isParentTask && newCompletedState) {
                                    // Get the complete task group (parent + children) for UI update
                                    val taskGroup = appRepository.taskRepository.getTaskGroup(task.id)
                                    taskGroup.map { it.id }.toSet()
                                } else {
                                    // Only update the clicked task
                                    setOf(task.id)
                                }

                                // Update UI state on Main thread
                                withContext(Dispatchers.Main) {
                                    currentTasks = currentTasks.map { currentTask ->
                                        if (currentTask.id in taskIdsToUpdate) {
                                            currentTask.copy(isCompleted = newCompletedState)
                                        } else {
                                            currentTask
                                        }
                                    }
                                }

                                // T9 (Oct 2026): write the absolute value (a double tap no longer
                                // inverts the DB), then reload the page so parent/child cascades
                                // done by setCompleted() show immediately.
                                appRepository.taskRepository.setCompleted(task.id, newCompletedState)
                                val reloaded = appRepository.taskRepository.getTasksForPage(
                                    task.dayDate, task.taskPageIndex
                                )
                                withContext(Dispatchers.Main) {
                                    if (currentTaskPageId == task.pageId && reloaded.isNotEmpty()) {
                                        currentTasks = reloaded
                                    }
                                }
                            }
                        },
                        onChangeTaskPriority = { task, priority ->
                            DrawCanvas.forceUnfreeze()  // Unfreeze UI for immediate visual feedback
                            currentTasks = currentTasks.map {
                                if (it.id == task.id) it.copy(priority = priority) else it
                            }
                            // T2: pass the canvas height so handwriting-only rows are accepted
                            val canvasHeight = actualCanvasHeight
                            scope.launch(Dispatchers.IO) {
                                appRepository.taskRepository.setPriority(task.id, priority, canvasHeight)
                            }
                        },
                        onDueDateChanged = { task, dueDate ->
                            DrawCanvas.forceUnfreeze()  // Unfreeze UI for immediate visual feedback
                            // The picker returns epoch ms; tasks store the local day (v27)
                            val dueDay = TaskDueDates.fromMillis(dueDate)
                            val originalDueDay = task.dueDay
                            // Optimistic update
                            currentTasks = currentTasks.map {
                                if (it.id == task.id) it.copy(dueDay = dueDay) else it
                            }
                            val canvasHeight = actualCanvasHeight
                            scope.launch(Dispatchers.IO) {
                                val result = appRepository.taskRepository.setDueDay(task.id, dueDay, canvasHeight)
                                when (result) {
                                    is DueDateResult.BlockedByParent -> {
                                        // Revert optimistic update
                                        withContext(Dispatchers.Main) {
                                            currentTasks = currentTasks.map {
                                                if (it.id == task.id) it.copy(dueDay = originalDueDay) else it
                                            }
                                            snackManager.displaySnack(
                                                SnackConf(
                                                    text = "Child due date cannot be later than parent's due date",
                                                    duration = SnackConf.DURATION_ERROR
                                                )
                                            )
                                        }
                                    }
                                    is DueDateResult.Success -> {
                                        // If children were auto-adjusted, refresh the task list
                                        if (result.adjustedChildCount > 0) {
                                            withContext(Dispatchers.Main) {
                                                // Reload tasks to reflect adjusted children
                                                currentTasks = appRepository.taskRepository.getTasksForPage(
                                                    selectedDate,
                                                    currentTaskPageInfo?.currentIndex ?: 0
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        onMoveTask = { task, targetDate, targetPageIndex ->
                            currentTasks = currentTasks.filter { it.id != task.id }
                        },
                        onTaskTypedTextChanged = { task, typedText ->
                            currentTasks = currentTasks.map {
                                if (it.id == task.id) it.copy(typedText = typedText) else it
                            }
                        },
                        onTaskCleared = { task ->
                            currentTasks = currentTasks.map {
                                if (it.id == task.id) it.copy(
                                    typedText = null,
                                    convertedText = null,
                                    recognizedText = null,
                                    isCompleted = false,
                                    priority = "",
                                    dueDay = null,
                                    parentTaskId = null,
                                    templateId = null,
                                    estimatedMinutes = null,
                                    migrationCount = 0,
                                    originalDate = null,
                                    lastMigratedAt = null
                                ) else it
                            }
                        },
                        onTaskInkErased = { task ->
                            currentTasks = currentTasks.map {
                                if (it.id == task.id) it.copy(recognizedText = null) else it
                            }
                        },
                        onTaskPageNav = { delta -> navigateTaskPage(delta) },
                        // BUG FIX: Update actual canvas height for stroke migration
                        onCanvasHeightChanged = { height ->
                            actualCanvasHeight = height
                        },
                        // AI FEATURE: Receive bitmap capture provider when tasks page is ready
                        onTasksBitmapProviderReady = { provider ->
                            captureTasksBitmap = provider
                        },
                        // Refresh tasks from database after operations that modify multiple tasks (e.g., child task compaction)
                        // If pageIndex is provided, navigate to that page; otherwise refresh current page
                        onTasksNeedRefresh = { requestedPageIndex ->
                            scope.launch {
                                val targetPageIndex = requestedPageIndex ?: currentTaskPageIndex
                                val (tasks, pageInfo) = withContext(Dispatchers.IO) {
                                    appRepository.taskRepository.getOrCreateTasksForPage(selectedDate, targetPageIndex)
                                }
                                currentTasks = tasks
                                currentTaskPageInfo = pageInfo
                                currentTaskPageIndex = pageInfo.currentIndex
                                currentTaskPageId = tasks.firstOrNull()?.pageId
                                // Force canvas refresh if navigating to different page
                                if (requestedPageIndex != null) {
                                    DrawCanvas.forceUpdate.emit(null)
                                }
                            }
                        },
                        // Navigate to a different date (used by TaskDueDateDialog to jump to due date)
                        onDateChange = { newDate ->
                            selectedDate = newDate
                        },
                        // Convert lasso selection to tasks (Memos AI feature)
                        highlightedTaskId = highlightedTaskId,
                        onBreakDownTask = { task -> startTaskBreakdown(task) },
                        onConvertSelectionToTasks = convert@{ selectionStrokes ->
                            // Needs the user's own OpenRouter key (Oct 2026): the key popup opens instead
                            if (!com.boox.day.ui.AiKeyGate.requireKey()) return@convert
                            // Full screen Tasks (Oct 2026): the new tasks go on pages of 10; the day is back
                            // on them long before recognition and AI are done
                            if (com.boox.day.tasks.TaskPagination.current != null) com.boox.day.ui.FullScreenState.collapse()
                            // Trigger memo-to-tasks conversion with selected strokes
                            memoToTasksTargetDate = selectedDate
                            memoToTasksResult = null
                            memoToTasksProcessingState = MemoToTasksProcessingState.Processing("Starting...")
                            memoToTasksSkipScopeSelection = true  // Skip scope selection for lasso
                            showMemoToTasksDialog = true
                            memoToTasksProcessingStartTime = System.currentTimeMillis()

                            // Start conversion: Digital Ink → text → tasks
                            scope.launch {
                                // Stage 1: Use Digital Ink to recognize strokes (fast, on-device)
                                memoToTasksProcessingState = MemoToTasksProcessingState.Processing("Recognizing handwriting...")

                                val digitalInkService = com.boox.day.ocr.DigitalInkService.create(context, scope)
                                val recognitionResult = digitalInkService.recognizeStrokes(selectionStrokes)

                                if (recognitionResult.isEmpty) {
                                    memoToTasksProcessingState = MemoToTasksProcessingState.Error(
                                        "No text detected in selection. Please write some notes first."
                                    )
                                    return@launch
                                }

                                val recognizedText = recognitionResult.text

                                // Fetch semantic context for prompt enrichment
                                val dayContext = withContext(Dispatchers.IO) {
                                    try {
                                        semanticGeniusService.getDateContext(memoToTasksTargetDate)
                                    } catch (e: Exception) {
                                        null
                                    }
                                }

                                // Get existing tasks to avoid duplicates
                                val existingTasks = withContext(Dispatchers.IO) {
                                    appRepository.taskRepository
                                        .getTasksForDateRange(memoToTasksTargetDate, memoToTasksTargetDate)
                                        .mapNotNull { it.typedText ?: it.convertedText ?: it.recognizedText }
                                }

                                // Stage 2+3: Text correction + task conversion
                                val result = MemoToTasksService.convertRecognizedTextToTasks(
                                    recognizedText = recognizedText,
                                    dayContext = dayContext,
                                    existingTasks = existingTasks,
                                    onStageChange = { stage ->
                                        memoToTasksProcessingState = MemoToTasksProcessingState.Processing(stage)
                                    }
                                )

                                // Enforce minimum display time (9 seconds = 3 messages × 3 seconds)
                                val minDisplayMs = 9000L
                                val elapsed = System.currentTimeMillis() - memoToTasksProcessingStartTime
                                if (elapsed < minDisplayMs) {
                                    delay(minDisplayMs - elapsed)
                                }

                                // Update state with result
                                if (result.error != null) {
                                    memoToTasksProcessingState = MemoToTasksProcessingState.Error(result.error)
                                } else {
                                    memoToTasksResult = result
                                    memoToTasksProcessingState = MemoToTasksProcessingState.SelectTasks
                                }
                            }
                        }
                    )
                }
                }  // Close canvas Box
            }  // Close Column (SharedToolbar + Canvas)
        }

            // ========== EXPANDED EVENTS PANEL ==========
            // Expands from events area downward into canvas (not full screen)
            // Note: Dismissal is handled by CalendarGrid's onDateSelected callback
            // so tapping a day both closes the panel AND changes the date
            if (isEventsExpanded && !isCompactLayout) {
                // Backdrop to capture taps on the CANVAS AREA only (not calendar)
                // Calendar taps are handled by CalendarGrid.onDateSelected which already closes the panel
                // This only covers the bottom 60% (canvas area) to avoid blocking calendar interactions
                if (!isEventsPinned) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(0.60f)  // Only cover the canvas area (bottom 60%)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null  // No ripple effect
                            ) {
                                isEventsExpanded = false
                            }
                    )
                }

                // Expanded panel - positioned in right 40%, starting below month header
                // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                // !!! CRITICAL: DO NOT CHANGE THE padding(top = 56.dp) VALUE BELOW !!!
                // !!! This value was carefully calibrated to align the expanded panel
                // !!! border EXACTLY with the collapsed panel border. Changing this
                // !!! causes a visible "shift" when expanding/collapsing.
                // !!! Tested and verified: 52dp=too high, 57dp=too low, 56dp=PERFECT
                // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxWidth(EInkTheme.EventsPanel.widthFraction)
                        .fillMaxHeight(0.80f)  // From top to 80% down (covers events + more canvas)
                        .padding(top = 56.dp)  // !!! DO NOT CHANGE - see comment above !!!
                        .background(EInkTheme.EventsPanel.rowBackground)
                        .border(0.5.dp, EInkTheme.Colors.borderStrong)
                ) {
                    ExpandedEventsList(
                        selectedDate = selectedDate,
                        eventsViewModel = eventsViewModel,
                        booxNoteRepository = booxNoteRepository,
                        booxNotesIntegration = booxNotesIntegration,
                        onEventClick = { event ->
                            selectedEventForDetails = event
                            showEventDetailsDialog = true
                        },
                        onClose = {
                            isEventsExpanded = false
                            isEventsPinned = false  // Reset pin state on close
                        },
                        isPinned = isEventsPinned,
                        onTogglePin = {
                            if (isEventsPinned) {
                                // Unpinning also collapses
                                isEventsPinned = false
                                isEventsExpanded = false
                            } else {
                                isEventsPinned = true
                            }
                        },
                        noteLinkRefreshTrigger = noteLinkRefreshTrigger,
                        selectedEventsTab = selectedEventsTab,
                        onTabSelected = { selectedEventsTab = it },
                        onDueTaskClick = { task ->
                            // Open the task (its day, page and row) — plan T3
                            goToTask(task.id, task.dayDate)
                        },
                        // Source filter parameters
                        sourceFilter = sourceFilter,
                        onToggleSource = { source ->
                            val success = eventsViewModel.toggleSource(source)
                            if (!success) {
                                showHint("At least one calendar must be selected", scope)
                            }
                            success
                        },
                        hasGoogleAccount = hasGoogleAccount,
                        hasOutlookAccount = hasOutlookAccount,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ========== DIALOGS ==========

        // Compact layout popups (plan Phase 1.5): month grid and Events/Due list over the page
        if (isCompactLayout && showCompactMonthPopup) {
            CompactMonthPopup(
                year = currentYear,
                month = currentMonth,
                selectedDate = selectedDate,
                today = today,
                badges = CalendarBadges(
                    datesWithNotes = datesWithNotesSet,
                    datesWithEvents = datesWithEvents,
                    datesWithBooxNotes = datesWithBooxNotes,
                    datesWithTasks = datesWithTasks
                ),
                firstDayOfWeekSetting = firstDayOfWeek,
                showWeekNumbers = showWeekNumbers,
                onPreviousMonth = {
                    val cal = (viewingCalendar.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                    showCompactMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
                },
                onNextMonth = {
                    val cal = (viewingCalendar.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                    showCompactMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
                },
                onDateSelected = { date ->
                    showCompactMonthPopup = false
                    if (date != selectedDate) {
                        selectedDate = date
                    }
                },
                onDismiss = { showCompactMonthPopup = false }
            )
        }

        if (isCompactLayout) {
            // "+" in the list popup creates the event on the list's day
            LaunchedEffect(showAddEventDialog) {
                if (!showAddEventDialog) compactAddEventDate = null
            }
        }

        if (isCompactLayout && showCompactEventsPopup) {
            CompactDayListPopup(
                listDate = compactEventsListDate,
                onListDateChange = { compactEventsListDate = it },
                onAddEvent = {
                    compactAddEventDate = compactEventsListDate
                    editingEvent = null
                    parsedEventData = null
                    showAddEventDialog = true
                },
                onDismiss = { showCompactEventsPopup = false }
            ) {
                ExpandedEventsList(
                    selectedDate = compactEventsListDate,
                    eventsViewModel = eventsViewModel,
                    booxNoteRepository = booxNoteRepository,
                    booxNotesIntegration = booxNotesIntegration,
                    onEventClick = { event ->
                        selectedEventForDetails = event
                        showEventDetailsDialog = true
                    },
                    onClose = { showCompactEventsPopup = false },
                    isPinned = false,
                    onTogglePin = {},
                    showPinButton = false,
                    noteLinkRefreshTrigger = noteLinkRefreshTrigger,
                    selectedEventsTab = selectedEventsTab,
                    onTabSelected = { selectedEventsTab = it },
                    onDueTaskClick = { task ->
                        // Open the task (its day, page and row) — plan T3
                        showCompactEventsPopup = false
                        goToTask(task.id, task.dayDate)
                    },
                    sourceFilter = sourceFilter,
                    onToggleSource = { source ->
                        val success = eventsViewModel.toggleSource(source)
                        if (!success) {
                            showHint("At least one calendar must be selected", scope)
                        }
                        success
                    },
                    hasGoogleAccount = hasGoogleAccount,
                    hasOutlookAccount = hasOutlookAccount,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Startup Sync: No blocking dialog - sync runs in background
        // Events grid shows "Syncing..." when sync is in progress

        // Multi-Provider Calendar Sync Settings Dialog
        if (showMultiProviderSyncDialog) {
            CalendarSyncSettingsDialog(
                viewModel = multiAccountSyncViewModel,
                onDismiss = { showMultiProviderSyncDialog = false },
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
                    // Auto-switch to HYBRID mode when first cloud account is added
                    if (syncModeSetting == AppSettings.SyncMode.STANDALONE) {
                        Log.i(TAG, "First cloud account added - switching to HYBRID mode")
                        syncModeSetting = AppSettings.SyncMode.HYBRID
                        val newSettings = GlobalAppSettings.current.copy(syncMode = AppSettings.SyncMode.HYBRID)
                        appRepository.kvProxy.setAppSettings(newSettings)
                    }
                },
                // New callbacks for clear data decision when last account is disconnected
                onClearDataAndStandalone = {
                    // Clear synced events and switch to Standalone
                    Log.i(TAG, "User chose: Clear data and return to Standalone")
                    scope.launch {
                        // Clear all cloud-synced events from database
                        eventsViewModel.clearAllSyncedEvents()
                        // Switch to Standalone mode
                        syncModeSetting = AppSettings.SyncMode.STANDALONE
                        val newSettings = GlobalAppSettings.current.copy(syncMode = AppSettings.SyncMode.STANDALONE)
                        appRepository.kvProxy.setAppSettings(newSettings)
                        SnackState.globalSnackFlow.tryEmit(
                            SnackConf(text = "Synced events cleared. Standalone mode active.", duration = SnackConf.DURATION_STANDARD)
                        )
                    }
                },
                onKeepDataAndHybrid = {
                    // Keep synced events (read-only) and stay in Hybrid mode
                    Log.i(TAG, "User chose: Keep data and stay in Hybrid")
                    // Stay in Hybrid mode - no action needed, just don't change the mode
                    SnackState.globalSnackFlow.tryEmit(
                        SnackConf(text = "Events preserved. You can add accounts later.", duration = SnackConf.DURATION_STANDARD)
                    )
                },
                syncedEventCount = syncedEventCountForSwitch
            )
        }

        // Calendar Settings Dialog (unified settings with tabs)
        if (showCalendarSettingsDialog) {
            // Get connected account emails for mode switch warnings
            val multiAccountUiState by multiAccountSyncViewModel.uiState.collectAsState()
            val connectedEmails = remember(multiAccountUiState) {
                (multiAccountUiState.googleAccounts + multiAccountUiState.outlookAccounts)
                    .map { it.account.email }
            }

            // Build connected accounts summary from multi-account state
            val cloudAccountSummaries = remember(multiAccountUiState) {
                (multiAccountUiState.googleAccounts + multiAccountUiState.outlookAccounts).map { acctWithCal ->
                    CloudAccountSummary(
                        email = acctWithCal.account.email,
                        provider = if (acctWithCal.account.providerId == com.boox.day.data.model.ProviderId.GOOGLE) "Google" else "Outlook",
                        lastSyncTimeMillis = acctWithCal.account.lastSyncAt
                    )
                }
            }

            CalendarSettingsDialog(
                onDismiss = { showCalendarSettingsDialog = false },
                onSyncNow = {
                    // Use multi-account sync for proper accountId/providerId tagging
                    multiAccountSyncViewModel.syncAllAccounts()
                },
                onOpenCalendarAccounts = {
                    // Open the Calendar Accounts dialog
                    showMultiProviderSyncDialog = true
                },
                connectedAccounts = cloudAccountSummaries,
                syncInProgress = syncInProgress || multiAccountUiState.isSyncing,
                autoSyncEnabled = autoSyncEnabled,
                syncFrequency = syncFrequency,
                onAutoSyncChanged = { syncViewModel.setAutoSync(it) },
                onSyncFrequencyChanged = { syncViewModel.setSyncFrequency(it) },
                // Display settings (moved from General Settings)
                firstDayOfWeek = firstDayOfWeek,
                onFirstDayOfWeekChanged = { newValue ->
                    firstDayOfWeek = newValue
                    val newSettings = GlobalAppSettings.current.copy(firstDayOfWeek = newValue)
                    appRepository.kvProxy.setAppSettings(newSettings)
                },
                showWeekNumbers = showWeekNumbers,
                onShowWeekNumbersChanged = { newValue ->
                    showWeekNumbers = newValue
                    val newSettings = GlobalAppSettings.current.copy(showWeekNumbers = newValue)
                    appRepository.kvProxy.setAppSettings(newSettings)
                },
                // Sync mode settings
                syncMode = syncModeSetting,
                defaultSyncEnabled = defaultSyncEnabled,
                showSyncBadges = true, // TODO: Add to GlobalAppSettings
                offlineChangesWarning = true, // TODO: Add to GlobalAppSettings
                // Connected accounts info for mode switch warnings
                connectedAccountEmails = connectedEmails,
                syncedEventCount = syncedEventCountForSwitch,
                onSyncModeChanged = { newMode ->
                    // Simple mode change (popup logic is now handled inside CalendarSettingsDialog)
                    syncModeSetting = newMode
                    val newSettings = GlobalAppSettings.current.copy(syncMode = newMode)
                    appRepository.kvProxy.setAppSettings(newSettings)
                    markSetupComplete { it.copy(calendarModeChosen = true) }
                },
                // Callbacks for mode switch with data decisions (from SwitchToStandalonePopup)
                onSwitchToStandaloneKeepData = {
                    // Disconnect accounts, keep data, switch to Standalone
                    Log.i(TAG, "Switch to Standalone: Keep data")
                    markSetupComplete { it.copy(calendarModeChosen = true) }
                    scope.launch {
                        // Disconnect all accounts
                        multiAccountSyncViewModel.disconnectAllAccounts()
                        // Switch to Standalone mode
                        syncModeSetting = AppSettings.SyncMode.STANDALONE
                        val newSettings = GlobalAppSettings.current.copy(syncMode = AppSettings.SyncMode.STANDALONE)
                        appRepository.kvProxy.setAppSettings(newSettings)
                        SnackState.globalSnackFlow.tryEmit(
                            SnackConf(text = "Private Mode enabled. Synced events preserved (read-only).", duration = SnackConf.DURATION_STANDARD)
                        )
                    }
                },
                onSwitchToStandaloneClearData = {
                    // Disconnect accounts, clear data, switch to Standalone
                    Log.i(TAG, "Switch to Standalone: Clear data")
                    markSetupComplete { it.copy(calendarModeChosen = true) }
                    scope.launch {
                        // Clear all synced events
                        eventsViewModel.clearAllSyncedEvents()
                        // Disconnect all accounts
                        multiAccountSyncViewModel.disconnectAllAccounts()
                        // Switch to Standalone mode
                        syncModeSetting = AppSettings.SyncMode.STANDALONE
                        val newSettings = GlobalAppSettings.current.copy(syncMode = AppSettings.SyncMode.STANDALONE)
                        appRepository.kvProxy.setAppSettings(newSettings)
                        SnackState.globalSnackFlow.tryEmit(
                            SnackConf(text = "Synced events cleared. Standalone mode active.", duration = SnackConf.DURATION_STANDARD)
                        )
                    }
                },
                onDefaultSyncChanged = { newValue ->
                    defaultSyncEnabled = newValue
                    val newSettings = GlobalAppSettings.current.copy(defaultSyncEnabled = newValue)
                    appRepository.kvProxy.setAppSettings(newSettings)
                },
                onShowSyncBadgesChanged = { /* TODO: Implement */ },
                onOfflineWarningChanged = { /* TODO: Implement */ },
                // Local calendars (for Standalone mode)
                localCalendars = localCalendars,
                onAddLocalCalendar = { showAddCalendarDialog = true },
                onDeleteLocalCalendars = { calendarIds ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            localCalendarRepository.delete(calendarIds, localEventRepository)
                            localCalendars = localCalendarRepository.getAll()
                        }
                        SnackState.globalSnackFlow.tryEmit(
                            SnackConf(text = "${calendarIds.size} calendar(s) deleted", duration = SnackConf.DURATION_STANDARD)
                        )
                    }
                }
            )
        }

        // Add Calendar Dialog (for creating new local calendars)
        if (showAddCalendarDialog) {
            AddCalendarDialog(
                onDismiss = { showAddCalendarDialog = false },
                onAdd = { name, colorHex ->
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                localCalendarRepository.create(name, colorHex)
                                localCalendars = localCalendarRepository.getAll()
                            }
                            showAddCalendarDialog = false
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

        // Help Dialog
        if (showHelpDialog) {
            HelpDialog(
                onDismiss = { showHelpDialog = false }
            )
        }

        // About Dialog
        if (showAboutDialog) {
            AboutDialog(
                onDismiss = { showAboutDialog = false }
            )
        }

        // Welcome Popup (First-run onboarding - Play Store launch Jan 2026)
        // Shows once on first launch to direct users to badged settings
        if (onboardingDismissedAt == 0L) {
            WelcomePopup(
                onDismiss = {
                    val timestamp = System.currentTimeMillis()
                    onboardingDismissedAt = timestamp
                    DrawCanvas.notifyWelcomeDismissed()
                    val newSettings = GlobalAppSettings.current.copy(onboardingDismissedAt = timestamp)
                    GlobalAppSettings.update(newSettings)
                    scope.launch(Dispatchers.IO) {
                        appRepository.kvProxy.setAppSettings(newSettings)
                    }
                }
            )
        }

        // Preferences Dialog (Dec 2025 Settings Redesign)
        if (showPreferencesDialog) {
            PreferencesDialog(
                onDismiss = { showPreferencesDialog = false },
                dateFormat = dateFormatSetting,
                onDateFormatChanged = { newValue ->
                    dateFormatSetting = newValue
                    val newSettings = GlobalAppSettings.current.copy(dateFormat = newValue)
                    appRepository.kvProxy.setAppSettings(newSettings)
                    markSetupComplete { it.copy(preferencesReviewed = true) }
                },
                timeFormat = timeFormatSetting,
                onTimeFormatChanged = { newValue ->
                    timeFormatSetting = newValue
                    val newSettings = GlobalAppSettings.current.copy(timeFormat = newValue)
                    appRepository.kvProxy.setAppSettings(newSettings)
                    markSetupComplete { it.copy(preferencesReviewed = true) }
                },
                startTab = startTabSetting,
                onStartTabChanged = { newValue ->
                    startTabSetting = newValue
                    val newSettings = GlobalAppSettings.current.copy(startTab = newValue)
                    appRepository.kvProxy.setAppSettings(newSettings)
                    markSetupComplete { it.copy(preferencesReviewed = true) }
                },
                tipsEnabled = tipsEnabled,
                onTipsEnabledChanged = { newValue ->
                    tipsEnabled = newValue
                    val newSettings = GlobalAppSettings.current.copy(tipsEnabled = newValue)
                    appRepository.kvProxy.setAppSettings(newSettings)
                    markSetupComplete { it.copy(preferencesReviewed = true) }
                },
                tipInterval = tipInterval,
                onTipIntervalChanged = { newValue ->
                    tipInterval = newValue
                    val newSettings = GlobalAppSettings.current.copy(tipInterval = newValue)
                    appRepository.kvProxy.setAppSettings(newSettings)
                    if (tipsEnabled) {
                        TipsService.start()
                    }
                    markSetupComplete { it.copy(preferencesReviewed = true) }
                },
                // Notification settings - decoupled task and event toggles
                taskNotificationsEnabled = taskNotificationsEnabled,
                onTaskNotificationsEnabledChanged = { newValue ->
                    taskNotificationsEnabled = newValue
                    val newSettings = GlobalAppSettings.current.copy(taskNotificationsEnabled = newValue)
                    appRepository.kvProxy.setAppSettings(newSettings)
                    if (newValue) {
                        scope.launch(Dispatchers.IO) {
                            appRepository.taskRepository.scheduleNotificationsForExistingTasks()
                        }
                    }
                },
                eventNotificationsEnabled = eventNotificationsEnabled,
                onEventNotificationsEnabledChanged = { newValue ->
                    eventNotificationsEnabled = newValue
                    val newSettings = GlobalAppSettings.current.copy(eventNotificationsEnabled = newValue)
                    appRepository.kvProxy.setAppSettings(newSettings)
                    if (newValue) {
                        scope.launch(Dispatchers.IO) {
                            appRepository.localEventRepository.scheduleNotificationsForUpcomingEvents()
                        }
                    }
                },
                defaultTaskReminderHour = defaultTaskReminderHour,
                defaultTaskReminderMinute = defaultTaskReminderMinute,
                onDefaultTaskReminderChanged = { hour, minute ->
                    defaultTaskReminderHour = hour
                    defaultTaskReminderMinute = minute
                    val newSettings = GlobalAppSettings.current.copy(
                        defaultTaskReminderHour = hour,
                        defaultTaskReminderMinute = minute
                    )
                    appRepository.kvProxy.setAppSettings(newSettings)
                },
                defaultEventReminderMinutes = defaultEventReminderMinutes,
                onDefaultEventReminderChanged = { minutes ->
                    defaultEventReminderMinutes = minutes
                    val newSettings = GlobalAppSettings.current.copy(defaultEventReminderMinutes = minutes)
                    appRepository.kvProxy.setAppSettings(newSettings)
                },
                onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        NotificationPermissionHelper.requestNotificationPermission(notificationPermissionLauncher)
                    } else {
                        NotificationPermissionHelper.openNotificationSettings(context)
                    }
                },
                onOpenAlarmSettings = {
                    NotificationPermissionHelper.openExactAlarmSettings(context)
                },
                onOpenBatteryOptimizationSettings = {
                    NotificationPermissionHelper.openBatteryOptimizationSettings(context)
                }
            )
        }

        // "Add your OpenRouter key" (Oct 2026): AI features need the user's own key
        com.boox.day.ui.dialogs.AiKeyGateHost(onOpenAiSettings = { showAiSettingsDialog = true })

        // AI Settings Dialog (Dec 2025 Settings Redesign)
        if (showAiSettingsDialog) {
            AiSettingsDialog(
                onDismiss = { showAiSettingsDialog = false },
                aiEnabled = aiEnabled,
                onAiEnabledChanged = { newValue ->
                    aiEnabled = newValue
                    val newSettings = GlobalAppSettings.current.copy(aiEnabled = newValue)
                    appRepository.kvProxy.setAppSettings(newSettings)
                    markSetupComplete { it.copy(aiSettingsReviewed = true) }
                },
                onOpenAiPreferences = {
                    showAiSettingsDialog = false
                    showAiPreferencesDialog = true
                }
            )
        }

        // Voice-to-Text Settings Dialog (Dec 2025 TranscriptionService)
        if (showVoiceSettingsDialog) {
            val transcriptionService = remember { TranscriptionService(context, AudioRecorder(context)) }
            VoiceSettingsDialog(
                hasGoogleSearchApp = transcriptionService.isSpeechRecognitionAvailable(),
                currentMode = GlobalAppSettings.current.voiceTextMode,
                onSelectMode = { mode ->
                    val newSettings = GlobalAppSettings.current.copy(voiceTextMode = mode)
                    appRepository.kvProxy.setAppSettings(newSettings)
                    showVoiceSettingsDialog = false
                },
                onInstallGoogleSearch = {
                    // Dialog handles Play Store opening, just close our dialog
                    showVoiceSettingsDialog = false
                },
                onDismiss = { showVoiceSettingsDialog = false }
            )
        }

        // Data & Backup Dialog (Dec 2025 Settings Redesign)
        if (showDataBackupDialog) {
            DataBackupDialog(
                onDismiss = { showDataBackupDialog = false },
                onClearSyncedDataOnly = {
                    showDataBackupDialog = false
                    syncViewModel.clearSyncedData(performFreshSync = false)
                },
                onClearSyncedDataAndResync = {
                    showDataBackupDialog = false
                    syncViewModel.clearSyncedData(performFreshSync = true)
                },
                onWipeAllData = {
                    showDataBackupDialog = false
                    scope.launch(Dispatchers.IO) {
                        try {
                            com.boox.day.data.PageDataManager.clearAllPages()
                            withContext(Dispatchers.Main) {
                                SnackState.globalSnackFlow.tryEmit(
                                    SnackConf(
                                        text = "All data wiped. Please restart the app.",
                                        duration = SnackConf.DURATION_ERROR
                                    )
                                )
                                val pm = context.packageManager
                                val clearIntent = android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", context.packageName, null)
                                )
                                clearIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(clearIntent)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error wiping data: ${e.message}", e)
                            withContext(Dispatchers.Main) {
                                SnackState.globalSnackFlow.tryEmit(
                                    SnackConf(
                                        text = "Failed to wipe data: ${e.message}",
                                        duration = SnackConf.DURATION_ERROR
                                    )
                                )
                            }
                        }
                    }
                }
            )
        }

        // Search Dialog (FTS4 full-text search across memos, tasks, events, notes)
        if (showSearchDialog) {
            SearchDialog(
                ocrRepository = ocrRepository,
                onNavigateToDate = { date ->
                    selectedDate = date
                    showSearchDialog = false
                },
                onOpenTask = { taskId, date ->
                    showSearchDialog = false
                    goToTask(taskId, date)
                },
                onOpenBooxNote = { uniqueId ->
                    booxNotesIntegration.openNote(uniqueId)
                    showSearchDialog = false
                },
                onDismiss = { showSearchDialog = false }
            )
        }


        // ═══════════════════════════════════════════════════════════════════════════
        // TASK SLOT DIALOGS (AI features that add tasks)
        // ═══════════════════════════════════════════════════════════════════════════


        // No free task rows
        if (showNoTaskSlotsDialog) {
            NoTaskSlotsDialog(
                onDismiss = { showNoTaskSlotsDialog = false }
            )
        }

        // Partial Insert Dialog (when user selects more items than available slots)
        if (showPartialInsertDialog) {
            PartialInsertDialog(
                selectedCount = partialInsertDialogSelectedCount,
                availableSlots = partialInsertDialogAvailableSlots,
                itemName = partialInsertDialogItemName,
                onInsertPartial = {
                    showPartialInsertDialog = false
                    partialInsertDialogOnConfirm?.invoke()
                },
                onCancel = { showPartialInsertDialog = false }
            )
        }




        // AI Preferences Dialog
        if (showAiPreferencesDialog) {
            val currentPreferences = remember { aiPreferencesPromptManager.getPreferences() }
            com.boox.day.editor.ui.AiPreferencesDialog(
                initialPreferences = currentPreferences,
                onSave = { newPreferences ->
                    scope.launch(Dispatchers.IO) {
                        aiPreferencesPromptManager.savePreferences(newPreferences)
                        if (newPreferences.setupComplete) {
                            aiPreferencesPromptManager.onPreferencesCompleted()
                        }
                        withContext(Dispatchers.Main) {
                            showAiPreferencesDialog = false
                            SnackState.globalSnackFlow.tryEmit(
                                SnackConf(text = "AI Preferences saved", duration = SnackConf.DURATION_QUICK)
                            )
                        }
                    }
                },
                onDismiss = { showAiPreferencesDialog = false }
            )
        }


        // Boox Notes Settings Dialog
        if (showBooxNotesSettingsDialog) {
            com.boox.day.ui.dialogs.BooxNotesSettingsDialog(
                onDismiss = {
                    showBooxNotesSettingsDialog = false
                    markSetupComplete { it.copy(booxNotesConfigured = true) }
                },
                booxNotesEnabled = booxNotesEnabled,
                onBooxNotesEnabledChanged = {
                    booxNotesEnabled = true
                    // Update GlobalAppSettings immediately for UI responsiveness
                    val newSettings = GlobalAppSettings.current.copy(booxNotesEnabled = true)
                    GlobalAppSettings.update(newSettings)
                    // Persist to database async to avoid blocking UI (Room DAO is synchronous)
                    scope.launch(Dispatchers.IO) {
                        appRepository.kvProxy.setAppSettings(newSettings)
                    }
                    markSetupComplete { it.copy(booxNotesConfigured = true) }
                },
                onScanNeeded = triggerBooxNotesScan,
                onIndexNow = {
                    scope.launch {
                        ocrIdleProcessor.processNow()
                    }
                },
                onClearIndex = {
                    scope.launch(Dispatchers.IO) {
                        // Clear all unified OCR data
                        ocrRepository.clearAll()
                        // Also clear the scheduler queue
                        val scheduler = com.boox.day.ocr.UnifiedOcrScheduler(context)
                        scheduler.clearQueue()
                        // Reset quota counters so cleared pages can be re-indexed
                        val quotaManager = com.boox.day.ocr.OcrQuotaManager(context)
                        quotaManager.resetQuotaAfterClear()
                    }
                },
                onOcrSettingsChanged = { newOcrSettings ->
                    // Persist OCR settings changes to database
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

        // Tasks Bulk Actions: Consolidated dialog with scope selection
        activeTaskOperation?.let { operation ->
            // BUG FIX: Capture values immediately to avoid race condition
            val capturedPageIndex = currentTaskPageIndex
            val capturedDate = selectedDate

            com.boox.day.editor.ui.TaskOperationDialog(
                operationType = operation,
                currentDate = capturedDate,
                currentPageIndex = capturedPageIndex,
                onConfirm = { selectedScope, targetDate ->
                    activeTaskOperation = null
                    scope.launch(Dispatchers.IO) {
                        when (operation) {
                            com.boox.day.editor.ui.TaskOperationType.MOVE -> {
                                val result = when (selectedScope) {
                                    com.boox.day.editor.ui.TaskOperationScope.CURRENT_PAGE ->
                                        appRepository.taskRepository.moveAllTasksFromPage(
                                            sourceDate = capturedDate,
                                            sourcePageIndex = capturedPageIndex,
                                            targetDate = targetDate!!,
                                            canvasHeight = actualCanvasHeight
                                        )
                                    com.boox.day.editor.ui.TaskOperationScope.ALL_PAGES ->
                                        appRepository.taskRepository.moveAllTasksForDate(
                                            sourceDate = capturedDate,
                                            targetDate = targetDate!!,
                                            canvasHeight = actualCanvasHeight
                                        )
                                }
                                withContext(Dispatchers.Main) {
                                    if (result.isSuccess) {
                                        // Refresh task data
                                        val refreshPageIndex = when (selectedScope) {
                                            com.boox.day.editor.ui.TaskOperationScope.CURRENT_PAGE -> capturedPageIndex
                                            com.boox.day.editor.ui.TaskOperationScope.ALL_PAGES -> 0
                                        }
                                        val (tasks, pageInfo) = appRepository.taskRepository.getOrCreateTasksForPage(capturedDate, refreshPageIndex)
                                        currentTasks = tasks
                                        currentTaskPageInfo = pageInfo
                                        currentTaskPageIndex = pageInfo.currentIndex
                                        currentTaskPageId = tasks.firstOrNull()?.pageId
                                        TasksCanvasRefresh.request()  // coalesced with the task op's own refresh (plan §13.4)
                                        SnackState.globalSnackFlow.tryEmit(
                                            SnackConf(text = "Moved ${result.getOrNull() ?: 0} tasks to $targetDate", duration = SnackConf.DURATION_STANDARD)
                                        )
                                    } else {
                                        SnackState.globalSnackFlow.tryEmit(
                                            SnackConf(text = "Failed to move tasks: ${result.exceptionOrNull()?.message}", duration = SnackConf.DURATION_ERROR)
                                        )
                                    }
                                }
                            }
                            com.boox.day.editor.ui.TaskOperationType.COPY -> {
                                val result = when (selectedScope) {
                                    com.boox.day.editor.ui.TaskOperationScope.CURRENT_PAGE ->
                                        appRepository.taskRepository.copyAllTasksFromPage(
                                            sourceDate = capturedDate,
                                            sourcePageIndex = capturedPageIndex,
                                            targetDate = targetDate!!,
                                            canvasHeight = actualCanvasHeight
                                        )
                                    com.boox.day.editor.ui.TaskOperationScope.ALL_PAGES ->
                                        appRepository.taskRepository.copyAllTasksForDate(
                                            sourceDate = capturedDate,
                                            targetDate = targetDate!!,
                                            canvasHeight = actualCanvasHeight
                                        )
                                }
                                withContext(Dispatchers.Main) {
                                    if (result.isSuccess) {
                                        val copyResult = result.getOrNull()
                                        val message = if (copyResult != null) {
                                            val baseMsg = "Copied ${copyResult.copiedCount} tasks to $targetDate"
                                            // Add due date info if any were affected
                                            if (copyResult.dueDatesPreserved > 0 || copyResult.dueDatesCleared > 0) {
                                                val parts = mutableListOf<String>()
                                                if (copyResult.dueDatesPreserved > 0) {
                                                    parts.add("${copyResult.dueDatesPreserved} due dates kept")
                                                }
                                                if (copyResult.dueDatesCleared > 0) {
                                                    parts.add("${copyResult.dueDatesCleared} cleared")
                                                }
                                                "$baseMsg (${parts.joinToString(", ")})"
                                            } else {
                                                baseMsg
                                            }
                                        } else {
                                            "Copied 0 tasks to $targetDate"
                                        }
                                        SnackState.globalSnackFlow.tryEmit(
                                            SnackConf(text = message, duration = SnackConf.DURATION_STANDARD)
                                        )
                                    } else {
                                        SnackState.globalSnackFlow.tryEmit(
                                            SnackConf(text = "Failed to copy tasks: ${result.exceptionOrNull()?.message}", duration = SnackConf.DURATION_ERROR)
                                        )
                                    }
                                }
                            }
                            com.boox.day.editor.ui.TaskOperationType.DELETE -> {
                                val result = when (selectedScope) {
                                    com.boox.day.editor.ui.TaskOperationScope.CURRENT_PAGE ->
                                        appRepository.taskRepository.deleteTasksOnPage(capturedDate, capturedPageIndex)
                                    com.boox.day.editor.ui.TaskOperationScope.ALL_PAGES ->
                                        appRepository.taskRepository.deleteAllTasksForDate(capturedDate)
                                }
                                withContext(Dispatchers.Main) {
                                    if (result.isSuccess) {
                                        // Refresh task data - page may have been compacted or reset
                                        val refreshPageIndex = when (selectedScope) {
                                            com.boox.day.editor.ui.TaskOperationScope.CURRENT_PAGE -> {
                                                val maxIndex = appRepository.taskRepository.getMaxTaskPageIndex(capturedDate)
                                                if (capturedPageIndex > maxIndex) maxIndex.coerceAtLeast(0) else capturedPageIndex
                                            }
                                            com.boox.day.editor.ui.TaskOperationScope.ALL_PAGES -> 0
                                        }
                                        val (tasks, pageInfo) = appRepository.taskRepository.getOrCreateTasksForPage(capturedDate, refreshPageIndex)
                                        currentTasks = tasks
                                        currentTaskPageInfo = pageInfo
                                        currentTaskPageIndex = pageInfo.currentIndex
                                        currentTaskPageId = tasks.firstOrNull()?.pageId
                                        TasksCanvasRefresh.request()  // coalesced with the task op's own refresh (plan §13.4)
                                        val message = when (selectedScope) {
                                            com.boox.day.editor.ui.TaskOperationScope.CURRENT_PAGE ->
                                                "Deleted tasks on page ${capturedPageIndex + 1}"
                                            com.boox.day.editor.ui.TaskOperationScope.ALL_PAGES ->
                                                "Deleted all tasks for $capturedDate"
                                        }
                                        SnackState.globalSnackFlow.tryEmit(
                                            SnackConf(text = message, duration = SnackConf.DURATION_STANDARD)
                                        )
                                    } else {
                                        SnackState.globalSnackFlow.tryEmit(
                                            SnackConf(text = "Failed to delete tasks: ${result.exceptionOrNull()?.message}", duration = SnackConf.DURATION_ERROR)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                onDismiss = { activeTaskOperation = null }
            )
        }

        // Convert Handwriting Dialog
        if (showConvertHandwritingDialog) {
            val capturedDate = selectedDate
            val capturedPageIndex = currentTaskPageIndex

            com.boox.day.editor.ui.ConvertHandwritingDialog(
                currentPage = capturedPageIndex + 1,
                totalPages = currentTaskPageInfo?.totalPages ?: 1,
                processingState = convertProcessingState,
                onConvert = { convertScope ->
                    // Dismiss dialog immediately — conversion is near-instant (no OCR, just field copy + stroke delete)
                    showConvertHandwritingDialog = false
                    convertProcessingState = com.boox.day.editor.ui.RecognitionProcessingState.Idle

                    scope.launch(Dispatchers.IO) {
                        try {
                            val result = when (convertScope) {
                                com.boox.day.editor.ui.ConvertScope.CURRENT_PAGE ->
                                    appRepository.taskRepository.convertHandwritingOnPage(
                                        capturedDate, capturedPageIndex, actualCanvasHeight
                                    )
                                com.boox.day.editor.ui.ConvertScope.ALL_PAGES ->
                                    appRepository.taskRepository.convertHandwritingForDay(
                                        capturedDate, actualCanvasHeight
                                    )
                                com.boox.day.editor.ui.ConvertScope.THIS_WEEK -> {
                                    val today = java.time.LocalDate.parse(capturedDate)
                                    val start = today.with(java.time.DayOfWeek.MONDAY).toString()
                                    val end = today.with(java.time.DayOfWeek.SUNDAY).toString()
                                    appRepository.taskRepository.convertHandwritingForDateRange(
                                        start, end, actualCanvasHeight
                                    )
                                }
                                com.boox.day.editor.ui.ConvertScope.THIS_MONTH -> {
                                    val today = java.time.LocalDate.parse(capturedDate)
                                    val start = today.withDayOfMonth(1).toString()
                                    val end = today.withDayOfMonth(today.lengthOfMonth()).toString()
                                    appRepository.taskRepository.convertHandwritingForDateRange(
                                        start, end, actualCanvasHeight
                                    )
                                }
                            }

                            // Build result message
                            val msg = if (result.convertedCount > 0) {
                                "Converted ${result.convertedCount} task${if (result.convertedCount != 1) "s" else ""}"
                            } else {
                                "No handwritten tasks to convert"
                            }

                            withContext(Dispatchers.Main) {
                                // Refresh task page state
                                val (tasks, pageInfo) = appRepository.taskRepository.getOrCreateTasksForPage(capturedDate, capturedPageIndex)
                                currentTasks = tasks
                                currentTaskPageInfo = pageInfo
                                currentTaskPageIndex = capturedPageIndex
                                currentTaskPageId = tasks.firstOrNull()?.pageId

                                // STROKE_CLEAR_FIX: Only refresh canvas when Tasks tab is active
                                if (currentEditorTab == EditorTab.Tasks) {
                                    DrawCanvas.forceUpdate.emit(null)
                                }

                                // Show result as snackbar
                                SnackState.globalSnackFlow.tryEmit(
                                    SnackConf(text = msg, duration = SnackConf.DURATION_STANDARD)
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("MainScreen", "Convert handwriting failed", e)
                            withContext(Dispatchers.Main) {
                                SnackState.globalSnackFlow.tryEmit(
                                    SnackConf(text = "Conversion failed: ${e.message}", duration = SnackConf.DURATION_ERROR)
                                )
                            }
                        }
                    }
                },
                onDismiss = {
                    showConvertHandwritingDialog = false
                    convertProcessingState = com.boox.day.editor.ui.RecognitionProcessingState.Idle
                }
            )
        }

        // Quick Add Task Dialog - opened from toolbar Quick Add button
        quickAddTargetTask?.let { task ->
            com.boox.day.editor.ui.TypeTaskDialog(
                rowIndex = task.rowIndex,
                initialText = null,
                onSave = { typedText ->
                    // Update local state
                    currentTasks = currentTasks.map {
                        if (it.id == task.id) it.copy(typedText = typedText) else it
                    }
                    // Save to database
                    scope.launch(Dispatchers.IO) {
                        appRepository.taskRepository.setTypedText(task.id, typedText)
                    }
                    quickAddTargetTask = null
                },
                onDismiss = { quickAddTargetTask = null }
            )
        }

        // Start-the-day review (plan T4) + its day picker and delete confirmation
        startDayReview?.let { review ->
            com.boox.day.editor.ui.StartDayReviewPopup(
                data = review,
                inkFor = { task ->
                    withContext(Dispatchers.IO) {
                        StartDayReviewLoader.renderRowInk(context, task, GlobalAppSettings.tasksCanvasHeight)
                    }
                },
                onMove = { task, date -> reviewMove(task, date) },
                onPickDay = { task -> reviewPickDayTask = task },
                onDone = { task -> reviewDone(task) },
                onDelete = { task -> reviewDeleteTask = task },
                onOpenTask = { task ->
                    closeStartDayReview()
                    goToTask(task.id, task.dayDate)
                },
                onDismiss = { closeStartDayReview() }
            )
        }
        reviewPickDayTask?.let { task ->
            com.boox.day.editor.ui.TaskDatePickerDialog(
                title = "Move to day",
                currentDate = today,
                actionButtonLabel = "Move",
                onDateSelected = { date ->
                    reviewPickDayTask = null
                    reviewMove(task, date)
                },
                onDismiss = { reviewPickDayTask = null }
            )
        }
        reviewDeleteTask?.let { task ->
            com.boox.day.editor.ui.ConfirmationDialog(
                title = "Delete this task?",
                message = "Its handwriting, text, due date and reminder are removed.",
                confirmText = "Delete Task",
                isDestructive = true,
                onConfirm = {
                    reviewDeleteTask = null
                    reviewDelete(task)
                },
                onDismiss = { reviewDeleteTask = null }
            )
        }

        // Delete Task Page Confirmation Dialog
        if (showDeleteTaskPageConfirmation) {
            // Capture values for safe access in coroutine
            val capturedPageIndex = currentTaskPageIndex
            val capturedDate = selectedDate

            ShowSimpleConfirmationDialog(
                title = "Delete Task Page",
                message = "Delete this task page? This will permanently delete all tasks and handwritten content on page ${capturedPageIndex + 1}.",
                confirmButtonText = "Delete",
                cancelButtonText = "Cancel",
                onConfirm = {
                    showDeleteTaskPageConfirmation = false
                    scope.launch(Dispatchers.IO) {
                        // Check if this is the only page or if there are multiple pages
                        val maxPageIndex = appRepository.taskRepository.getMaxTaskPageIndex(capturedDate)

                        if (maxPageIndex > 0) {
                            // Multiple pages exist - delete the page completely
                            appRepository.taskRepository.deleteTaskPage(capturedDate, capturedPageIndex)

                            // Navigate to page 0 and refresh
                            withContext(Dispatchers.Main) {
                                val (tasks, pageInfo) = appRepository.taskRepository.getOrCreateTasksForPage(capturedDate, 0)
                                currentTasks = tasks
                                currentTaskPageInfo = pageInfo
                                currentTaskPageIndex = pageInfo.currentIndex
                                currentTaskPageId = tasks.firstOrNull()?.pageId
                                TasksCanvasRefresh.request()  // coalesced with the task op's own refresh (plan §13.4)
                                SnackState.globalSnackFlow.tryEmit(
                                    SnackConf(text = "Deleted page ${capturedPageIndex + 1}", duration = SnackConf.DURATION_STANDARD)
                                )
                            }
                        } else {
                            // Only one page - clear content but keep page structure
                            // Clear all tasks on this page
                            appRepository.taskRepository.clearTasksOnPage(capturedDate, capturedPageIndex, actualCanvasHeight)

                            // Refresh the page
                            withContext(Dispatchers.Main) {
                                val (refreshedTasks, pageInfo) = appRepository.taskRepository.getOrCreateTasksForPage(capturedDate, 0)
                                currentTasks = refreshedTasks
                                currentTaskPageInfo = pageInfo
                                currentTaskPageIndex = pageInfo.currentIndex
                                currentTaskPageId = refreshedTasks.firstOrNull()?.pageId
                                TasksCanvasRefresh.request()  // coalesced with the task op's own refresh (plan §13.4)
                                SnackState.globalSnackFlow.tryEmit(
                                    SnackConf(text = "Cleared page content", duration = SnackConf.DURATION_STANDARD)
                                )
                            }
                        }
                    }
                },
                onCancel = { showDeleteTaskPageConfirmation = false }
            )
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // TASK AI DIALOGS
        // ═══════════════════════════════════════════════════════════════════════════

        // Voice to Task Dialog - voice input to create tasks
        if (showVoiceToTaskDialog) {
            VoiceToTaskDialog(
                currentDate = LocalDate.parse(selectedDate),
                onTasksParsed = { result ->
                    voiceTaskResult = result
                    showVoiceToTaskDialog = false
                    showTaskPreviewDialog = true
                },
                onDismiss = {
                    showVoiceToTaskDialog = false
                }
            )
        }

        // Task Preview Dialog - confirm tasks before creating
        if (showTaskPreviewDialog && voiceTaskResult != null) {
            TaskPreviewDialog(
                parsedResult = voiceTaskResult!!,
                currentDate = LocalDate.parse(selectedDate),
                onCreateTasks = { tasksToCreate ->
                    showTaskPreviewDialog = false
                    // Create tasks in database
                    scope.launch {
                        var createdCount = 0
                        for (taskData in tasksToCreate) {
                            val targetDate = taskData.date.toString()
                            // Find first empty slot using centralized SlotAllocator
                            // This checks ALL content types: typedText, convertedText, recognizedText,
                            // isCompleted, priority, parentTaskId, and intentional strokes
                            val slot = SlotAllocator.allocateSingle(
                                date = targetDate,
                                canvasHeight = actualCanvasHeight,
                                taskRepository = appRepository.taskRepository,
                                startPageIndex = 0
                            )

                            if (slot != null) {
                                appRepository.taskRepository.setTypedText(slot.task.id, taskData.text)
                                // Set priority if specified
                                if (!taskData.priority.isNullOrBlank()) {
                                    appRepository.taskRepository.setPriority(slot.task.id, taskData.priority)
                                }
                                // Set due date if specified
                                if (taskData.dueDateOverride != null) {
                                    appRepository.taskRepository.setDueDay(slot.task.id, TaskDueDates.fromMillis(taskData.dueDateOverride))
                                }
                                createdCount++
                            }
                        }

                        // Refresh current view
                        withContext(Dispatchers.Main) {
                            val (tasks, pageInfo) = appRepository.taskRepository.getOrCreateTasksForPage(selectedDate, currentTaskPageIndex)
                            currentTasks = tasks
                            currentTaskPageInfo = pageInfo
                            SnackState.globalSnackFlow.tryEmit(
                                SnackConf(text = "Created $createdCount task(s)", duration = SnackConf.DURATION_STANDARD)
                            )
                        }
                    }
                    voiceTaskResult = null
                },
                onDismiss = {
                    showTaskPreviewDialog = false
                    voiceTaskResult = null
                }
            )
        }

        // Sort Scope Popup
        if (showSortScopePopup) {
            SortScopePopup(
                onDismiss = { showSortScopePopup = false },
                onScopeSelected = { sortScope ->
                    showSortScopePopup = false
                    sortProcessingState = SortProcessingState.Processing("Reading your task list...")
                    sortResult = null
                    placementPlan = null
                    showSortPreviewDialog = true

                    scope.launch {
                        // Track start time for minimum display duration (UX: show funny messages)
                        val processingStartTime = System.currentTimeMillis()

                        // 1. Gather tasks based on scope
                        val tasksToSort = if (sortScope == SortScope.PAGE) {
                            currentTasks
                        } else {
                            // Gather all tasks for the day - only iterate EXISTING pages
                            // CRITICAL: Do NOT iterate 0..9 blindly, as getOrCreateTasksForPage() will
                            // create new empty pages for indices beyond what exists.
                            val allTasks = mutableListOf<Task>()
                            withContext(Dispatchers.IO) {
                                val maxPageIndex = appRepository.taskRepository.getMaxTaskPageIndex(selectedDate)
                                if (maxPageIndex >= 0) {
                                    for (i in 0..maxPageIndex) {
                                        val (tasks, _) = appRepository.taskRepository.getOrCreateTasksForPage(selectedDate, i)
                                        allTasks.addAll(tasks)
                                    }
                                }
                            }
                            allTasks
                        }

                        // PRE-SORT CLEANUP: Remove orphaned metadata from empty slots
                        // This ensures empty slots don't have stale priority/dueDate that could
                        // cause them to be incorrectly scored or included in sorting
                        withContext(Dispatchers.IO) {
                            val cleanedCount = appRepository.taskRepository.cleanOrphanedMetadata(
                                selectedDate,
                                actualCanvasHeight
                            )
                            if (cleanedCount > 0) {
                                Log.i("MainScreen", "SMART_SORT: Cleaned $cleanedCount orphaned metadata slots")
                            }
                        }

                        // FILTER: Only score and sort tasks with REAL content
                        // Empty slots should NEVER be part of sorting, even if they have orphaned metadata
                        // Uses stricter check that ignores metadata (priority/dueDate/isCompleted)
                        val tasksWithContent = withContext(Dispatchers.IO) {
                            appRepository.taskRepository.filterTasksWithRealContent(tasksToSort, actualCanvasHeight)
                        }

                        if (tasksWithContent.isEmpty()) {
                            sortProcessingState = SortProcessingState.Error("No tasks with content to sort")
                            return@launch
                        }

                        Log.i("MainScreen", "SMART_SORT: Filtered ${tasksToSort.size} tasks to ${tasksWithContent.size} with content")

                        // 2. Run AI Sort using Intelligence Engine V3
                        sortProcessingState = SortProcessingState.Processing("Analyzing priorities...")

                        // Get scored context from Intelligence Engine (only content tasks)
                        val planningDate = LocalDate.parse(selectedDate)
                        val referenceHour = resolveTaskAiReferenceHour(planningDate)
                        val intelligenceEngine = IntelligenceEngine(context)
                        val scoredContext = intelligenceEngine.getScoredContext(
                            feature = IntelligenceFeature.SMART_PRIORITIZATION,
                            tasks = tasksWithContent,
                            referenceDate = planningDate,
                            referenceHour = referenceHour
                        )

                        // Store scored context for dialog display
                        sortScoredContext = scoredContext

                        // Build priority assignments based on scores (A/B/C) and store in state
                        sortPriorityAssignments = TaskAiService.buildPriorityAssignments(scoredContext)
                        Log.i("MainScreen", "SMART_SORT: Built priority assignments - " +
                            "A: ${sortPriorityAssignments.count { it.value == "A" }}, " +
                            "B: ${sortPriorityAssignments.count { it.value == "B" }}, " +
                            "C: ${sortPriorityAssignments.count { it.value == "C" }}")

                        // Sort tasks by pre-computed scores WITH parent-child grouping
                        sortProcessingState = SortProcessingState.Processing("Ranking tasks...")
                        val v3SortedTasks = TaskAiService.sortTasksByPriorityV3WithGrouping(
                            tasks = tasksWithContent,
                            scoredContext = scoredContext
                        )

                        // Build SortResult for UI compatibility
                        val sortedTasksList = v3SortedTasks.mapIndexed { index, task ->
                            val scoredItem = scoredContext.scoredItems.find { it.itemId == task.id }
                            val reason = scoredItem?.primarySignal ?: "Ranked by Intelligence Engine"
                            TaskAiService.SortedTask(
                                originalIndex = task.rowIndex,
                                originalPage = task.taskPageIndex,
                                text = task.typedText ?: task.recognizedText ?: task.convertedText ?: "",
                                suggestedPriority = index + 1,  // 1-based, lower = higher priority
                                reason = reason,
                                userPriority = task.priority,
                                isCompleted = task.isCompleted,
                                taskId = task.id,
                                isHandwritten = task.typedText.isNullOrBlank() && (task.recognizedText != null || task.convertedText != null)
                            )
                        }

                        val result = TaskAiService.SortResult(
                            sortedTasks = sortedTasksList,
                            reasoning = scoredContext.contextSummary
                        )

                        if (result.error != null) {
                            sortResult = result
                            sortProcessingState = SortProcessingState.Error(result.error!!)
                            return@launch
                        }

                        // 3. Pre-calculate Placement Plan - CONTIGUOUS placement
                        // Tasks are placed at rows 0, 1, 2, 3... with NO gaps
                        sortProcessingState = SortProcessingState.Processing("Calculating new order...")

                        val sortedTasks = result.sortedTasks.sortedBy { it.suggestedPriority }

                        withContext(Dispatchers.IO) {
                            val totalTasks = sortedTasks.size
                            val pagesNeeded = (totalTasks + 9) / 10  // Ceiling division

                            // Build contiguous placement plan starting at row 0 of the first page in scope
                            // T5 (Oct 2026): "This page only" sorts in place on that page (it used to
                            // always start on page 1 and overwrite it); the whole day starts on page 1.
                            val startPage = if (sortScope == SortScope.PAGE) {
                                sortedTasks.firstOrNull()?.originalPage ?: 0
                            } else {
                                0
                            }
                            val plan = mutableListOf<PlacedSortedTask>()
                            var taskIndex = 0

                            for (pageIdx in startPage until startPage + pagesNeeded) {
                                // Ensure page exists and get task slots
                                val (pageTasks, _) = appRepository.taskRepository.getOrCreateTasksForPage(
                                    selectedDate,
                                    pageIdx
                                )

                                for (rowIdx in 0 until 10) {
                                    if (taskIndex >= totalTasks) break

                                    // Get target task entity for this slot
                                    val targetTask = pageTasks.getOrNull(rowIdx)
                                    if (targetTask == null) {
                                        Log.w("MainScreen", "SMART_SORT: Missing task slot at page $pageIdx row $rowIdx")
                                        continue
                                    }

                                    plan.add(PlacedSortedTask(
                                        sortedTask = sortedTasks[taskIndex],
                                        targetPage = pageIdx,
                                        targetRow = rowIdx,
                                        targetTaskId = targetTask.id
                                    ))
                                    taskIndex++
                                }
                            }

                            Log.i("MainScreen", "SMART_SORT: Contiguous placement plan - ${plan.size} tasks at rows 0-${plan.size - 1}")

                            placementPlan = plan
                            sortResult = result
                        }

                        // Ensure minimum display time for processing UI (3 seconds)
                        // This gives users time to see the funny messages and perceive AI working
                        val minDisplayTimeMs = 3000L
                        val elapsedMs = System.currentTimeMillis() - processingStartTime
                        if (elapsedMs < minDisplayTimeMs) {
                            delay(minDisplayTimeMs - elapsedMs)
                        }

                        sortProcessingState = SortProcessingState.Ready
                    }
                }
            )
        }

        // Sort Preview Dialog - show AI-suggested task ordering
        if (showSortPreviewDialog) {
            SortPreviewDialog(
                sortResult = sortResult,
                placementPlan = placementPlan,
                scoredContext = sortScoredContext,
                priorityAssignments = sortPriorityAssignments,
                processingState = sortProcessingState,
                onApplySort = { result ->
                    showSortPreviewDialog = false
                    
                    // Use the pre-calculated placement plan to apply changes
                    val plan = placementPlan
                    if (plan == null) return@SortPreviewDialog

                    scope.launch {
                        // Change state to "Applying..." (handled by dialog dismissal, but good to know)
                        withContext(Dispatchers.Main) {
                            SnackState.globalSnackFlow.tryEmit(
                                SnackConf(text = "Applying new order...", duration = SnackConf.DURATION_STANDARD)
                            )
                        }

                        withContext(Dispatchers.IO) {
                            // Build map from taskId to source Task object
                            // We need to fetch fresh task objects to ensure we have valid entities
                            val taskIdToSource = mutableMapOf<String, Task>()
                            val sourcePageIndices = plan.map { it.sortedTask.originalPage }.distinct()
                            
                            for (pageIdx in sourcePageIndices) {
                                val (tasks, _) = appRepository.taskRepository.getOrCreateTasksForPage(selectedDate, pageIdx)
                                for (task in tasks) {
                                    taskIdToSource[task.id] = task
                                }
                            }

                            // Build reorder items from the Plan
                            // CRITICAL: Use placedTask.targetTaskId (the target SLOT's Task entity ID),
                            // NOT placedTask.sortedTask.taskId (the SOURCE task's ID).
                            // The targetTaskId tells reorderTasksWithStrokes() which database row to write to.
                            val reorderItems = plan.mapNotNull { placedTask ->
                                val sourceTask = taskIdToSource[placedTask.sortedTask.taskId] ?: return@mapNotNull null

                                com.boox.day.data.db.TaskRepository.TaskReorderItem(
                                    sourceTask = sourceTask,
                                    targetPageIndex = placedTask.targetPage,
                                    targetRowIndex = placedTask.targetRow,
                                    targetTaskId = placedTask.targetTaskId
                                )
                            }

                            // Execute reorder with full stroke migration + auto-priority assignment
                            val success = appRepository.taskRepository.reorderTasksWithStrokes(
                                date = selectedDate,
                                items = reorderItems,
                                canvasHeight = actualCanvasHeight,
                                priorityAssignments = sortPriorityAssignments
                            )

                            // Refresh view
                            withContext(Dispatchers.Main) {
                                // Navigate to the page containing Priority #1
                                val firstTargetPage = plan.firstOrNull()?.targetPage ?: 0
                                
                                // Only navigate if we're not already there
                                if (currentTaskPageIndex != firstTargetPage) {
                                    currentTaskPageIndex = firstTargetPage
                                }
                                
                                val (tasks, pageInfo) = appRepository.taskRepository.getOrCreateTasksForPage(selectedDate, currentTaskPageIndex)
                                currentTasks = tasks
                                currentTaskPageInfo = pageInfo
                                currentTaskPageId = tasks.firstOrNull()?.pageId
                                TasksCanvasRefresh.request()  // coalesced with the task op's own refresh (plan §13.4)

                                val message = if (success) {
                                    "Tasks reordered. Priority #1 is on Page ${firstTargetPage + 1}."
                                } else {
                                    "Failed to reorder tasks"
                                }
                                SnackState.globalSnackFlow.tryEmit(
                                    SnackConf(text = message, duration = SnackConf.DURATION_STANDARD)
                                )
                            }
                        }
                    }
                },
                onDismiss = {
                    showSortPreviewDialog = false
                    sortResult = null
                    placementPlan = null
                    sortScoredContext = null
                }
            )
        }

        // Daily Summary Dialog - end-of-day task review with migration option
        if (showDailySummaryDialog) {
            DailySummaryDialog(
                summary = dailySummaryResult,
                processingState = dailySummaryProcessingState,
                onMigrateTask = { task ->
                    val capturedDate = selectedDate
                    val capturedPageIndex = currentTaskPageIndex
                    val tomorrow = LocalDate.parse(capturedDate).plusDays(1)
                    val tomorrowStr = tomorrow.toString()

                    val result = withContext(Dispatchers.IO) {
                        appRepository.taskRepository.moveTaskWithStrokes(
                            task = task,
                            targetDate = tomorrowStr,
                            targetPageIndex = 0,
                            canvasHeight = actualCanvasHeight,
                            context = context
                        )
                    }

                    if (result.success) {
                        SnackState.globalSnackFlow.tryEmit(
                            SnackConf(text = "Task moved to tomorrow", duration = SnackConf.DURATION_STANDARD)
                        )

                        val (tasks, pageInfo) = withContext(Dispatchers.IO) {
                            appRepository.taskRepository.getOrCreateTasksForPage(capturedDate, capturedPageIndex)
                        }
                        currentTasks = tasks
                        currentTaskPageInfo = pageInfo
                        true
                    } else {
                        val errorMessage = when (result.errorReason) {
                            MoveTaskError.TARGET_PAGE_FULL -> "Target page is full"
                            MoveTaskError.SOURCE_PAGE_NOT_LOADED -> "Source page not loaded"
                            MoveTaskError.DATABASE_ERROR -> "Task move failed"
                            MoveTaskError.SAME_LOCATION -> "Task already on target day"
                            null -> "Task move failed"
                        }
                        SnackState.globalSnackFlow.tryEmit(
                            SnackConf(text = errorMessage, duration = SnackConf.DURATION_ERROR)
                        )
                        false
                    }
                },
                onBreakDownTask = { task ->
                    // Capture OCR text before clearing summary result
                    val summaryOcrMap = dailySummaryResult?.handwrittenOcrMap ?: emptyMap()
                    val taskText = summaryOcrMap[task.id] ?: task.typedText ?: ""
                    val isHandwritten = summaryOcrMap.containsKey(task.id)

                    // Close daily summary
                    showDailySummaryDialog = false
                    dailySummaryResult = null

                    if (taskText.isNotBlank()) {
                        // Pre-select this task and open breakdown dialog
                        selectedTaskForBreakdown = TaskAiService.TaskForBreakdown(
                            task = task,
                            displayText = taskText,
                            isHandwritten = isHandwritten
                        )
                        taskBreakdownProcessingState = TaskBreakdownProcessingState.ProcessingBreakdown
                        taskBreakdownResult = null
                        showTaskBreakdownDialog = true

                        // Generate breakdown for the selected task using Intelligence Engine V3
                        scope.launch {
                            // Get current tasks for context
                            val (tasksForContext, _) = appRepository.taskRepository.getOrCreateTasksForPage(selectedDate, currentTaskPageIndex)

                            // Get scored context with ComplexityScorer signals
                            val planningDate = LocalDate.parse(selectedDate)
                            val referenceHour = resolveTaskAiReferenceHour(planningDate)
                            val intelligenceEngine = IntelligenceEngine(context)
                            val scoredContext = intelligenceEngine.getScoredContext(
                                feature = IntelligenceFeature.BREAK_DOWN_TASK,
                                tasks = tasksForContext,
                                referenceDate = planningDate,
                                referenceHour = referenceHour
                            )

                            val result = TaskAiService.breakDownTaskV3(
                                taskText = taskText,
                                task = task,
                                existingTasks = tasksForContext,
                                currentDate = LocalDate.parse(selectedDate),
                                scoredContext = scoredContext
                            )
                            taskBreakdownResult = result
                            taskBreakdownProcessingState = if (result.error != null) {
                                TaskBreakdownProcessingState.Error(result.error!!)
                            } else {
                                TaskBreakdownProcessingState.Ready
                            }
                        }
                    }
                },
                onDismiss = {
                    showDailySummaryDialog = false
                    dailySummaryResult = null
                }
            )
        }

        // Weekly Summary Dialog - 7-day task review with trends and patterns
        if (showWeeklySummaryDialog) {
            WeeklySummaryDialog(
                summary = weeklySummaryResult,
                processingState = weeklySummaryProcessingState,
                cacheInfo = weeklySummaryCacheInfo,
                onRefresh = {
                    launchWeeklySummary(forceRefresh = true)
                },
                onBreakDownTask = { taskText ->
                    // Close weekly summary and open Task Breakdown for recurring task
                    showWeeklySummaryDialog = false
                    weeklySummaryResult = null
                    weeklySummaryCacheInfo = null

                    if (taskText.isNotBlank()) {
                        // Open breakdown dialog with the task text
                        selectedTaskForBreakdown = null // No specific task entity for recurring
                        taskBreakdownProcessingState = TaskBreakdownProcessingState.ProcessingBreakdown
                        taskBreakdownResult = null
                        showTaskBreakdownDialog = true

                        // Generate breakdown for the recurring task using Intelligence Engine V3
                        scope.launch {
                            // Get current tasks for context
                            val (tasksForContext, _) = appRepository.taskRepository.getOrCreateTasksForPage(selectedDate, currentTaskPageIndex)

                            // Create a temporary task for the V3 call (recurring tasks don't have a specific entity)
                            // The task ID won't match scored items, so complexity signals will be empty,
                            // but the V3 prompt structure is still superior to V1
                            val tempTask = Task(
                                dayDate = selectedDate,
                                taskPageIndex = currentTaskPageIndex,
                                rowIndex = 0,
                                pageId = "",
                                typedText = taskText
                            )

                            // Get scored context with ComplexityScorer signals
                            val planningDate = LocalDate.parse(selectedDate)
                            val referenceHour = resolveTaskAiReferenceHour(planningDate)
                            val intelligenceEngine = IntelligenceEngine(context)
                            val scoredContext = intelligenceEngine.getScoredContext(
                                feature = IntelligenceFeature.BREAK_DOWN_TASK,
                                tasks = tasksForContext,
                                referenceDate = planningDate,
                                referenceHour = referenceHour
                            )

                            val result = TaskAiService.breakDownTaskV3(
                                taskText = taskText,
                                task = tempTask,
                                existingTasks = tasksForContext,
                                currentDate = LocalDate.parse(selectedDate),
                                scoredContext = scoredContext
                            )
                            taskBreakdownResult = result
                            taskBreakdownProcessingState = if (result.error != null) {
                                TaskBreakdownProcessingState.Error(result.error!!)
                            } else {
                                TaskBreakdownProcessingState.Ready
                            }
                        }
                    }
                },
                onDismiss = {
                    showWeeklySummaryDialog = false
                    weeklySummaryResult = null
                    weeklySummaryCacheInfo = null
                }
            )
        }

        // Agentic Plan Dialog - AI-powered day planning with proposals
        if (showAgenticPlanDialog) {
            val planningDate = remember(showAgenticPlanDialog) {
                java.time.LocalDate.parse(selectedDate)
            }
            val planningDateString = remember(planningDate) { planningDate.toString() }
            val proposalStartPageIndex = remember(showAgenticPlanDialog) { currentTaskPageIndex }
            val proposalExecutor = remember(actualCanvasHeight, planningDate, proposalStartPageIndex) {
                com.boox.day.ai.ProposalExecutor(
                    context = context,
                    canvasHeight = actualCanvasHeight,
                    planningDate = planningDate,
                    startPageIndex = proposalStartPageIndex,
                    taskRepository = appRepository.taskRepository,
                    localEventRepository = appRepository.localEventRepository,
                    getInFlightProposalIds = { agenticInFlightProposalIds },
                    getCompletedProposalIds = { agenticCompletedProposalIds },
                    updateExecutionState = { inFlight, completed ->
                        agenticInFlightProposalIds = inFlight
                        agenticCompletedProposalIds = completed
                    }
                )
            }

            com.boox.day.editor.ui.AgenticPlanDialog(
                planResult = agenticPlanResult,
                processingState = agenticPlanProcessingState,
                completedProposalIds = agenticCompletedProposalIds,
                inFlightProposalIds = agenticInFlightProposalIds,
                onExecuteProposal = { proposal ->
                    scope.launch {
                        agenticPlanProcessingState = com.boox.day.data.ai.AgenticPlanProcessingState.Executing(proposal.id)

                        val result = proposalExecutor.execute(
                            proposal = proposal,
                            onNavigateToTask = { taskId, date ->
                                val task = appRepository.taskRepository.getById(taskId)
                                if (task == null) {
                                    false
                                } else {
                                    val targetPageIndex = task.taskPageIndex
                                    if (date != selectedDate) {
                                        pendingTaskPageNavigationIndex = targetPageIndex
                                        pendingTaskCanvasForceRefresh = true
                                        selectedDate = date
                                    } else {
                                        scope.launch {
                                            loadTasksPage(date, targetPageIndex)
                                            // STROKE_CLEAR_FIX: Only refresh canvas when Tasks tab is active
                                            if (currentEditorTab == EditorTab.Tasks) {
                                                DrawCanvas.forceUpdate.emit(null)
                                            }
                                        }
                                    }
                                    true
                                }
                            },
                            onOpenBreakdownDialog = { taskId ->
                                val task = appRepository.taskRepository.getById(taskId)
                                if (task == null) {
                                    false
                                } else {
                                    selectedTaskForBreakdown = TaskAiService.TaskForBreakdown(
                                        task = task,
                                        displayText = task.typedText ?: "",
                                        isHandwritten = false
                                    )
                                    taskBreakdownProcessingState = TaskBreakdownProcessingState.ProcessingBreakdown
                                    taskBreakdownResult = null
                                    showTaskBreakdownDialog = true
                                    showAgenticPlanDialog = false

                                    scope.launch {
                                        val (tasksForContext, _) = appRepository.taskRepository.getOrCreateTasksForPage(selectedDate, currentTaskPageIndex)

                                        val planningDate = LocalDate.parse(selectedDate)
                                        val referenceHour = resolveTaskAiReferenceHour(planningDate)
                                        val intelligenceEngine = IntelligenceEngine(context)
                                        val scoredContext = intelligenceEngine.getScoredContext(
                                            feature = IntelligenceFeature.BREAK_DOWN_TASK,
                                            tasks = tasksForContext,
                                            referenceDate = planningDate,
                                            referenceHour = referenceHour
                                        )

                                        val breakdownResult = TaskAiService.breakDownTaskV3(
                                            taskText = task.typedText ?: "",
                                            task = task,
                                            existingTasks = tasksForContext,
                                            currentDate = LocalDate.parse(selectedDate),
                                            scoredContext = scoredContext
                                        )
                                        taskBreakdownResult = breakdownResult
                                        taskBreakdownProcessingState = if (breakdownResult.error != null) {
                                            TaskBreakdownProcessingState.Error(breakdownResult.error!!)
                                        } else {
                                            TaskBreakdownProcessingState.Ready
                                        }
                                    }
                                    true
                                }
                            }
                        )

                        agenticPlanProcessingState = com.boox.day.data.ai.AgenticPlanProcessingState.Ready

                        if (result.success) {
                            when (proposal.action) {
                                ProposalAction.ADD_TASK -> {
                                    val targetDate = result.affectedDate ?: planningDateString
                                    val targetPageIndex = result.resultPageIndex ?: proposalStartPageIndex
                                    val createdTaskId = result.createdTaskId
                                    val addedTaskText = proposal.taskText?.trim().orEmpty()
                                    val isVisibleTarget =
                                        targetDate == selectedDate &&
                                            targetPageIndex == currentTaskPageIndex &&
                                            createdTaskId != null &&
                                            addedTaskText.isNotBlank()

                                    if (isVisibleTarget) {
                                        currentTasks = currentTasks.map { task ->
                                            if (task.id == createdTaskId) task.copy(typedText = addedTaskText) else task
                                        }
                                        agenticNeedsVisibleTaskFlush = true
                                        agenticPendingTaskRefreshTarget = null
                                    } else {
                                        agenticNeedsVisibleTaskFlush = false
                                        agenticPendingTaskRefreshTarget = targetDate to targetPageIndex
                                    }
                                }
                                ProposalAction.CREATE_TIMEBLOCK, ProposalAction.SCHEDULE_BUFFER -> {
                                    agenticFocusEventId = result.createdEventId
                                    result.affectedDate?.let { affectedDate ->
                                        if (affectedDate != selectedDate) {
                                            selectedDate = affectedDate
                                        }
                                    }
                                    eventsViewModel.refreshEvents()
                                }
                                ProposalAction.MOVE_TASK -> {
                                    val refreshDate = planningDateString
                                    val refreshPageIndex = if (result.affectedDate == refreshDate && result.resultPageIndex != null) {
                                        result.resultPageIndex
                                    } else {
                                        proposalStartPageIndex
                                    }
                                    if (refreshDate != selectedDate) {
                                        pendingTaskPageNavigationIndex = refreshPageIndex
                                        pendingTaskCanvasForceRefresh = true
                                        selectedDate = refreshDate
                                    } else {
                                        loadTasksPage(refreshDate, refreshPageIndex)
                                        // STROKE_CLEAR_FIX: Only refresh canvas when Tasks tab is active
                                        if (currentEditorTab == EditorTab.Tasks) {
                                            DrawCanvas.forceUpdate.emit(null)
                                        }
                                    }
                                }
                                else -> { /* Informational actions — no refresh needed */ }
                            }
                            showHint(result.message, scope)
                        } else {
                            showHint(result.error ?: "Action failed", scope)
                        }
                    }
                },
                onSkipProposal = { proposal ->
                    agenticCompletedProposalIds = agenticCompletedProposalIds + proposal.id
                    val message = when (proposal.action) {
                        com.boox.day.data.ai.ProposalAction.FLAG_ZOMBIE -> "Skipped zombie - consider addressing soon"
                        com.boox.day.data.ai.ProposalAction.DELEGATE_OR_DROP -> "Noted - task kept for now"
                        else -> "Skipped - will reconsider tomorrow"
                    }
                    showHint(message, scope)
                },
                onDismiss = {
                    showAgenticPlanDialog = false
                    agenticPlanResult = null
                },
                onRetry = {
                    // Re-trigger the plan generation using Intelligence Engine V3
                    agenticPlanProcessingState = com.boox.day.data.ai.AgenticPlanProcessingState.Processing("Retrying...")
                    agenticPlanResult = null

                    scope.launch {
                        val contextBuilder = com.boox.day.ai.AgenticContextBuilder(context)
                        val planningContext = contextBuilder.buildContext(
                            planningDate = LocalDate.parse(selectedDate),
                            onStageChange = { stage ->
                                agenticPlanProcessingState = com.boox.day.data.ai.AgenticPlanProcessingState.Processing(stage)
                            }
                        )

                        // Get scored context from Intelligence Engine
                        agenticPlanProcessingState = com.boox.day.data.ai.AgenticPlanProcessingState.Processing("Analyzing priorities...")
                        val allPlanningTasks = planningContext.todayTasks + planningContext.overdueTasks + planningContext.zombieTasks
                        val intelligenceEngine = IntelligenceEngine(context)
                        val scoredContext = intelligenceEngine.getScoredContext(
                            feature = IntelligenceFeature.PLAN_MY_DAY,
                            tasks = allPlanningTasks,
                            referenceDate = planningContext.planningDate,
                            referenceHour = planningContext.currentHour
                        )

                        val result = com.boox.day.ai.AgenticPlanningService.generatePlanV3(
                            context = planningContext,
                            scoredContext = scoredContext,
                            onStageChange = { stage ->
                                agenticPlanProcessingState = com.boox.day.data.ai.AgenticPlanProcessingState.Processing(stage)
                            }
                        )

                        agenticPlanResult = result
                        agenticPlanProcessingState = if (result.error != null) {
                            com.boox.day.data.ai.AgenticPlanProcessingState.Error(result.error)
                        } else {
                            com.boox.day.data.ai.AgenticPlanProcessingState.Ready
                        }
                    }
                }
            )
        }

        // Smart Suggestions Dialog - AI-powered task recommendations
        if (showSmartSuggestionsDialog) {
            SmartSuggestionsDialog(
                suggestionsResult = smartSuggestionsResult,
                processingState = smartSuggestionsProcessingState,
                onAddSuggestion = { suggestion ->
                    // Add single suggestion using centralized SlotAllocator
                    scope.launch {
                        // Use SlotAllocator for consistent slot detection across all features
                        val refreshPageIndex: Int
                        val slot = SlotAllocator.allocateSingle(
                            date = selectedDate,
                            canvasHeight = actualCanvasHeight,
                            taskRepository = appRepository.taskRepository,
                            startPageIndex = currentTaskPageIndex
                        )

                        if (slot != null) {
                            appRepository.taskRepository.setTypedText(slot.task.id, suggestion.text)
                            refreshPageIndex = slot.pageIndex
                            SnackState.globalSnackFlow.tryEmit(
                                SnackConf(text = "Task added", duration = SnackConf.DURATION_QUICK)
                            )
                        } else {
                            refreshPageIndex = currentTaskPageIndex
                            SnackState.globalSnackFlow.tryEmit(
                                SnackConf(text = "No empty slots available", duration = SnackConf.DURATION_STANDARD)
                            )
                        }

                        // Refresh the actual page where the suggestion landed.
                        loadTasksPage(selectedDate, refreshPageIndex)
                    }
                },
                onAddAll = { suggestions ->
                    // Add all remaining suggestions using centralized SlotAllocator
                    scope.launch {
                        // Use SlotAllocator.allocateMultiple for batch allocation
                        val slots = SlotAllocator.allocateMultiple(
                            date = selectedDate,
                            count = suggestions.size,
                            canvasHeight = actualCanvasHeight,
                            taskRepository = appRepository.taskRepository,
                            startPageIndex = currentTaskPageIndex
                        )

                        // Place suggestions in allocated slots
                        val refreshPageIndex = slots.maxOfOrNull { it.pageIndex } ?: currentTaskPageIndex
                        var addedCount = 0
                        for ((index, suggestion) in suggestions.withIndex()) {
                            val slot = slots.getOrNull(index)
                            if (slot != null) {
                                appRepository.taskRepository.setTypedText(slot.task.id, suggestion.text)
                                addedCount++
                            }
                        }
                        val skippedCount = suggestions.size - addedCount

                        // Show confirmation with detail
                        val message = when {
                            skippedCount > 0 && addedCount > 0 -> "$addedCount added, $skippedCount skipped (no slots)"
                            skippedCount > 0 -> "No empty slots available"
                            addedCount == 1 -> "Task added"
                            else -> "$addedCount tasks added"
                        }
                        SnackState.globalSnackFlow.tryEmit(
                            SnackConf(text = message, duration = SnackConf.DURATION_STANDARD)
                        )

                        // Refresh the last page affected by the batch add so the new tasks are visible.
                        loadTasksPage(selectedDate, refreshPageIndex)
                    }
                },
                onDismiss = {
                    showSmartSuggestionsDialog = false
                    smartSuggestionsResult = null
                },
                // AI Preferences reminder - show until user sets up preferences
                showPreferencesReminder = aiPreferencesPromptManager.shouldShowInDialogReminder(),
                onSetUpPreferences = {
                    showSmartSuggestionsDialog = false
                    smartSuggestionsResult = null
                    showAiPreferencesDialog = true
                }
            )
        }

        // AI Preferences Invite Popup - proactive prompt with backoff
        if (showAiPreferencesInvitePopup) {
            AiPreferencesInvitePopup(
                onSetUpNow = {
                    showAiPreferencesInvitePopup = false
                    showAiPreferencesDialog = true
                },
                onMaybeLater = {
                    showAiPreferencesInvitePopup = false
                    aiPreferencesPromptManager.onProactivePromptDismissed()
                },
                onDontAskAgain = {
                    showAiPreferencesInvitePopup = false
                    // Record as dismissed - backoff system will eventually deactivate
                    aiPreferencesPromptManager.onProactivePromptDismissed()
                }
            )
        }

        // Task Breakdown Dialog - break complex tasks into subtasks
        if (showTaskBreakdownDialog) {
            TaskBreakdownDialog(
                availableTasks = tasksForBreakdown,
                breakdownResult = taskBreakdownResult,
                processingState = taskBreakdownProcessingState,
                onSelectTask = { taskForBreakdown, convertHandwritten ->
                    // User selected a task - trigger AI breakdown
                    selectedTaskForBreakdown = taskForBreakdown
                    taskBreakdownProcessingState = TaskBreakdownProcessingState.ProcessingBreakdown
                    scope.launch {
                        // If user chose to convert handwritten tasks, do it FIRST
                        if (convertHandwritten) {
                            withContext(Dispatchers.IO) {
                                // Convert ALL handwritten tasks to typed text
                                for (hwTask in tasksForBreakdown.filter { it.isHandwritten }) {
                                    // Set typed text from OCR result
                                    appRepository.taskRepository.setTypedText(hwTask.task.id, hwTask.displayText)
                                    // Clear the handwritten strokes
                                    appRepository.taskRepository.clearStrokesForRow(hwTask.task, actualCanvasHeight)
                                }
                            }
                            // Refresh the current tasks after conversion
                            val (tasks, pageInfo) = appRepository.taskRepository.getOrCreateTasksForPage(selectedDate, currentTaskPageIndex)
                            currentTasks = tasks
                            currentTaskPageInfo = pageInfo
                        }

                        // Now proceed with AI breakdown using Intelligence Engine V3
                        // Get scored context with ComplexityScorer signals
                        val planningDate = LocalDate.parse(selectedDate)
                        val referenceHour = resolveTaskAiReferenceHour(planningDate)
                        val intelligenceEngine = IntelligenceEngine(context)
                        val scoredContext = intelligenceEngine.getScoredContext(
                            feature = IntelligenceFeature.BREAK_DOWN_TASK,
                            tasks = currentTasks,
                            referenceDate = planningDate,
                            referenceHour = referenceHour
                        )

                        val result = TaskAiService.breakDownTaskV3(
                            taskText = taskForBreakdown.displayText,
                            task = taskForBreakdown.task,
                            existingTasks = currentTasks,
                            currentDate = LocalDate.parse(selectedDate),
                            scoredContext = scoredContext
                        )
                        taskBreakdownResult = result
                        taskBreakdownProcessingState = if (result.error != null) {
                            TaskBreakdownProcessingState.Error(result.error!!)
                        } else {
                            TaskBreakdownProcessingState.Ready
                        }
                    }
                },
                onApplyBreakdown = { result ->
                    showTaskBreakdownDialog = false
                    scope.launch {
                        var placementPageIndex = currentTaskPageIndex
                        var placementStartRow = -1
                        var movedToNewPage = false
                        var applySucceeded = false
                        var applyError: String? = null

                        withContext(Dispatchers.IO) {
                            val originalTaskForBreakdown = selectedTaskForBreakdown
                            val slotsNeeded = 1 + result.subtasks.size // parent + subtasks
                            val excludeTaskIds = originalTaskForBreakdown?.let { setOf(it.task.id) } ?: emptySet()

                            // Find contiguous block using centralized SlotAllocator
                            // This checks ALL content types: typedText, convertedText, recognizedText,
                            // isCompleted, priority, parentTaskId, and intentional strokes
                            // excludeTaskIds treats the original task slot as empty (will be cleared)
                            val allocation = SlotAllocator.allocateContiguous(
                                date = selectedDate,
                                count = slotsNeeded,
                                canvasHeight = actualCanvasHeight,
                                taskRepository = appRepository.taskRepository,
                                excludeTaskIds = excludeTaskIds,
                                startPageIndex = currentTaskPageIndex,
                                maxPages = 10
                            )

                            if (allocation == null) {
                                applyError = "Not enough space for breakdown"
                                return@withContext
                            }

                            val parentSlot = allocation.slots.firstOrNull()
                            if (parentSlot == null) {
                                applyError = "Not enough space for breakdown"
                                return@withContext
                            }

                            placementPageIndex = allocation.pageIndex
                            placementStartRow = allocation.startRow
                            movedToNewPage = allocation.pageIndex != currentTaskPageIndex

                            val parentText = originalTaskForBreakdown?.displayText ?: result.originalTask
                            val db = AppDatabase.getDatabase(context)

                            try {
                                db.runInTransaction {
                                    val originalTask = originalTaskForBreakdown?.task
                                    // T15 (Oct 2026): the AI's keepOriginal keeps a header row as it is
                                    // (its own ink/text) when the parent lands on that same row
                                    val keepOriginalInPlace = result.keepOriginal &&
                                        originalTask != null && parentSlot.task.id == originalTask.id
                                    if (originalTask != null && !keepOriginalInPlace) {
                                        // Clear original task location completely (typed text AND strokes)
                                        appRepository.taskRepository.setTypedText(originalTask.id, null)
                                        appRepository.taskRepository.clearStrokesForRow(originalTask, actualCanvasHeight)
                                        // T15: its priority, due date (+ reminder), estimate and repeat
                                        // link go to the new parent; the old row is left empty
                                        if (parentSlot.task.id != originalTask.id) {
                                            appRepository.taskRepository.moveTaskSchedulingTo(originalTask, parentSlot.task.id)
                                        }
                                    }

                                    // Place parent task at the first slot
                                    if (!keepOriginalInPlace) {
                                        appRepository.taskRepository.setTypedText(parentSlot.task.id, parentText)
                                    }

                                    // Place subtasks and link them via parentTaskId
                                    for ((index, subtask) in result.subtasks.withIndex()) {
                                        val subtaskSlot = allocation.slots.getOrNull(index + 1) ?: continue
                                        appRepository.taskRepository.setTypedText(subtaskSlot.task.id, subtask.text)
                                        appRepository.taskRepository.setParentTask(subtaskSlot.task.id, parentSlot.task.id)
                                    }
                                }
                                applySucceeded = true
                            } catch (e: Exception) {
                                applyError = "Breakdown failed"
                            }
                        }

                        if (!applySucceeded) {
                            SnackState.globalSnackFlow.tryEmit(
                                SnackConf(
                                    text = applyError ?: "Breakdown failed",
                                    duration = SnackConf.DURATION_ERROR
                                )
                            )
                            return@launch
                        }

                        // Show confirmation with page info
                        val subtaskCount = result.subtasks.size
                        val pageNum = placementPageIndex + 1
                        val message = if (movedToNewPage) {
                            "Break down complete! $subtaskCount subtasks placed on page $pageNum"
                        } else {
                            "Break down complete! $subtaskCount subtasks added"
                        }
                        SnackState.globalSnackFlow.tryEmit(
                            SnackConf(text = message, duration = SnackConf.DURATION_STANDARD)
                        )

                        // ALWAYS navigate to the placement page so user can see the result
                        currentTaskPageIndex = placementPageIndex
                        val (tasks, pageInfo) = appRepository.taskRepository.getOrCreateTasksForPage(selectedDate, placementPageIndex)
                        currentTasks = tasks
                        currentTaskPageInfo = pageInfo
                        currentTaskPageId = tasks.firstOrNull()?.pageId
                        // STROKE_CLEAR_FIX: Only refresh canvas when Tasks tab is active
                        if (currentEditorTab == EditorTab.Tasks) {
                            DrawCanvas.forceUpdate.emit(null)
                        }
                    }
                },
                onDismiss = {
                    showTaskBreakdownDialog = false
                    taskBreakdownResult = null
                    selectedTaskForBreakdown = null
                    tasksForBreakdown = emptyList()
                }
            )
        }

        val onMemoToTasksScopeSelected: (AiScope) -> Unit = { scopeSelection ->
            memoToTasksProcessingState = MemoToTasksProcessingState.Processing("Starting...")
            memoToTasksProcessingStartTime = System.currentTimeMillis()

            scope.launch {
                // Fetch semantic context for prompt enrichment
                val dayContext = withContext(Dispatchers.IO) {
                    try {
                        semanticGeniusService.getDateContext(memoToTasksTargetDate)
                    } catch (e: Exception) {
                        null  // Proceed without context if unavailable
                    }
                }

                // Get existing tasks to avoid duplicates
                val existingTasks = withContext(Dispatchers.IO) {
                    appRepository.taskRepository
                        .getTasksForDateRange(memoToTasksTargetDate, memoToTasksTargetDate)
                        .mapNotNull { it.typedText ?: it.convertedText ?: it.recognizedText }
                }

                val result = when (scopeSelection) {
                    AiScope.CurrentPage -> {
                        val bitmap = tabManager.activeEditorState?.pageView?.windowedBitmap?.copy(
                            tabManager.activeEditorState?.pageView?.windowedBitmap?.config
                                ?: android.graphics.Bitmap.Config.ARGB_8888,
                            false
                        )
                        if (bitmap == null) {
                            memoToTasksProcessingState = MemoToTasksProcessingState.Error(
                                "Could not capture memos page. Please try again."
                            )
                            return@launch
                        }

                        val conversion = MemoToTasksService.convertMemoToTasks(
                            bitmap = bitmap,
                            dayContext = dayContext,
                            existingTasks = existingTasks,
                            onStageChange = { stage ->
                                memoToTasksProcessingState = MemoToTasksProcessingState.Processing(stage)
                            }
                        )
                        bitmap.recycle()
                        conversion
                    }
                    AiScope.AllPages -> {
                        val view = tabManager.activeEditorState?.pageView
                        val pageIds = tabManager.activeEditorState?.dayPageInfo?.pageIds.orEmpty()
                        val width = view?.windowedBitmap?.width ?: 0
                        val height = view?.windowedBitmap?.height ?: 0
                        if (width <= 0 || height <= 0 || pageIds.isEmpty()) {
                            memoToTasksProcessingState = MemoToTasksProcessingState.Error(
                                "Could not capture memos pages. Please try again."
                            )
                            return@launch
                        }

                        val previews = pageIds.mapIndexedNotNull { index, pageId ->
                            loadPreview(
                                context = context,
                                pageIdToLoad = pageId,
                                expectedWidth = width,
                                expectedHeight = height,
                                pageNumber = index + 1
                            )
                        }
                        if (previews.isEmpty()) {
                            memoToTasksProcessingState = MemoToTasksProcessingState.Error(
                                "No memo previews available for conversion."
                            )
                            return@launch
                        }

                        val multiPage = MemoToTasksService.convertMultiplePagesToTasks(
                            bitmaps = previews,
                            dayContext = dayContext,
                            existingTasks = existingTasks,
                            onPageProgress = { current, total ->
                                memoToTasksProcessingState = MemoToTasksProcessingState.Processing(
                                    "Processing page $current of $total..."
                                )
                            },
                            onStageChange = { stage ->
                                memoToTasksProcessingState = MemoToTasksProcessingState.Processing(stage)
                            }
                        )
                        previews.forEach { it.recycle() }
                        multiPage
                    }
                }

                // Enforce minimum display time (9 seconds = 3 messages × 3 seconds)
                val minDisplayMs = 9000L
                val elapsed = System.currentTimeMillis() - memoToTasksProcessingStartTime
                if (elapsed < minDisplayMs) {
                    delay(minDisplayMs - elapsed)
                }

                // Update state with result
                // Show error only if no tasks were extracted at all;
                // if some pages failed but others produced tasks, show the tasks
                if (result.error != null && result.tasks.isEmpty()) {
                    memoToTasksProcessingState = MemoToTasksProcessingState.Error(result.error)
                } else {
                    memoToTasksResult = result
                    memoToTasksProcessingState = MemoToTasksProcessingState.SelectTasks
                }
            }
        }

        // Memo to Tasks Dialog - Convert Memos handwriting to Tasks
        if (showMemoToTasksDialog) {
            MemoToTasksDialog(
                processingState = memoToTasksProcessingState,
                result = memoToTasksResult,
                defaultTargetDate = memoToTasksTargetDate,
                defaultDueDate = memoToTasksDueDate,
                totalMemoPages = tabManager.activeEditorState?.dayPageInfo?.totalPages ?: 1,
                onScopeSelected = onMemoToTasksScopeSelected,
                onDefaultTargetDateSelected = { date ->
                    memoToTasksTargetDate = date
                },
                onDefaultDueDateSelected = { dueDate ->
                    memoToTasksDueDate = dueDate
                },
                onTaskToggle = { index ->
                    memoToTasksResult = memoToTasksResult?.let { r ->
                        r.copy(tasks = r.tasks.mapIndexed { i, t ->
                            if (i == index) t.copy(isSelected = !t.isSelected) else t
                        })
                    }
                },
                onTaskTargetDateChange = { index, dateOverride ->
                    memoToTasksResult = memoToTasksResult?.let { r ->
                        r.copy(tasks = r.tasks.mapIndexed { i, t ->
                            if (i == index) t.copy(targetDateOverride = dateOverride) else t
                        })
                    }
                },
                onTaskDueDateChange = { index, dueDateOverride ->
                    memoToTasksResult = memoToTasksResult?.let { r ->
                        r.copy(tasks = r.tasks.mapIndexed { i, t ->
                            if (i == index) t.copy(dueDateOverride = dueDateOverride) else t
                        })
                    }
                },
                onTaskPriorityChange = { index, priority ->
                    memoToTasksResult = memoToTasksResult?.let { r ->
                        r.copy(tasks = r.tasks.mapIndexed { i, t ->
                            if (i == index) t.copy(suggestedPriority = priority) else t
                        })
                    }
                },
                onAddTasks = {
                    scope.launch {
                        val result = memoToTasksResult ?: return@launch
                        val selectedTasks = result.tasks.filter { it.isSelected }
                        if (selectedTasks.isEmpty()) {
                            showMemoToTasksDialog = false
                            return@launch
                        }

                        suspend fun performMemoToTasksInsert(tasksToInsert: List<com.boox.day.ai.ConvertedTask>) {
                            val addResult = withContext(Dispatchers.IO) {
                                appRepository.taskRepository.addConvertedTasksToDate(
                                    tasks = tasksToInsert,
                                    defaultTargetDate = memoToTasksTargetDate,
                                    canvasHeight = actualCanvasHeight,
                                    defaultDueDate = memoToTasksDueDate
                                )
                            }

                            showMemoToTasksDialog = false
                            memoToTasksResult = null
                            memoToTasksProcessingState = MemoToTasksProcessingState.Processing("")
                            memoToTasksDueDate = null

                            val refreshPageIndex = addResult.latestPageIndexByDate[selectedDate]
                            if (refreshPageIndex != null) {
                                val previousTaskPageId = currentTaskPageId
                                loadTasksPage(selectedDate, refreshPageIndex)
                                if (currentEditorTab == EditorTab.Tasks) {
                                    if (currentTaskPageId == previousTaskPageId) {
                                        DrawCanvas.forceUnfreeze()
                                    } else {
                                        DrawCanvas.forceUpdate.emit(null)
                                    }
                                }
                            }

                            val requestedCount = tasksToInsert.size
                            val addedCount = addResult.tasksAdded
                            val toastText = if (addResult.targetDates.size == 1) {
                                val targetDate = addResult.targetDates.first()
                                if (addedCount < requestedCount) {
                                    "Added $addedCount of $requestedCount tasks to $targetDate"
                                } else {
                                    "Added $addedCount task${if (addedCount != 1) "s" else ""} to $targetDate"
                                }
                            } else {
                                if (addedCount < requestedCount) {
                                    "Added $addedCount of $requestedCount tasks across ${addResult.targetDates.size} dates"
                                } else {
                                    "Added $addedCount task${if (addedCount != 1) "s" else ""} across ${addResult.targetDates.size} dates"
                                }
                            }
                            SnackState.globalSnackFlow.tryEmit(
                                SnackConf(
                                    text = toastText,
                                    duration = SnackConf.DURATION_STANDARD
                                )
                            )
                        }

                        val insertPlan = withContext(Dispatchers.IO) {
                            val tasksByDate = selectedTasks.groupBy { task ->
                                task.targetDateOverride ?: memoToTasksTargetDate
                            }

                            val availableByDate = mutableMapOf<String, Int>()
                            var singleDateHasNoSlots = false

                            for ((targetDate, _) in tasksByDate) {
                                val slotsResult = TaskSlotCalculator.getAvailableSlots(
                                    date = targetDate,
                                    taskRepository = appRepository.taskRepository,
                                    strokeRepository = appRepository.strokeRepository,
                                    canvasHeight = actualCanvasHeight
                                )
                                availableByDate[targetDate] = slotsResult.totalAvailable
                                if (tasksByDate.size == 1 && slotsResult.totalAvailable == 0) {
                                    singleDateHasNoSlots = true
                                }
                            }

                            val remainingByDate = availableByDate.toMutableMap()
                            val tasksToInsert = mutableListOf<com.boox.day.ai.ConvertedTask>()

                            for (task in selectedTasks) {
                                val effectiveTargetDate = task.targetDateOverride ?: memoToTasksTargetDate
                                val remaining = remainingByDate[effectiveTargetDate] ?: 0
                                if (remaining > 0) {
                                    tasksToInsert.add(task)
                                    remainingByDate[effectiveTargetDate] = remaining - 1
                                }
                            }

                            MemoToTasksInsertPlan(
                                tasksToInsert = tasksToInsert,
                                totalSelectedCount = selectedTasks.size,
                                totalAvailableCount = tasksToInsert.size,
                                singleDateHasNoSlots = singleDateHasNoSlots
                            )
                        }

                        if (insertPlan.totalAvailableCount == 0) {
                            if (insertPlan.singleDateHasNoSlots) {
                                showNoTaskSlotsDialog = true
                            } else {
                                SnackState.globalSnackFlow.tryEmit(
                                    SnackConf(
                                        text = "No empty task slots are available on the selected dates",
                                        duration = SnackConf.DURATION_ERROR
                                    )
                                )
                            }
                            return@launch
                        }

                        if (insertPlan.totalAvailableCount < insertPlan.totalSelectedCount) {
                            partialInsertDialogSelectedCount = insertPlan.totalSelectedCount
                            partialInsertDialogAvailableSlots = insertPlan.totalAvailableCount
                            partialInsertDialogItemName = "tasks"
                            partialInsertDialogOnConfirm = {
                                scope.launch {
                                    performMemoToTasksInsert(insertPlan.tasksToInsert)
                                }
                            }
                            showPartialInsertDialog = true
                            return@launch
                        }

                        performMemoToTasksInsert(insertPlan.tasksToInsert)
                    }
                },
                onDismiss = {
                    showMemoToTasksDialog = false
                    memoToTasksResult = null
                    memoToTasksProcessingState = MemoToTasksProcessingState.Processing("")
                    memoToTasksDueDate = null
                },
                skipScopeSelection = memoToTasksSkipScopeSelection
            )
        }

        // Voice Event Dialog - "AI Magic" voice-to-calendar
        if (showVoiceEventDialog) {
            VoiceEventDialog(
                currentDate = try {
                    java.time.LocalDate.parse(selectedDate)
                } catch (e: Exception) {
                    java.time.LocalDate.now()
                },
                onEventParsed = { parsed ->
                    parsedEventData = parsed
                    showVoiceEventDialog = false
                    // Open AddEditEventDialog with pre-filled data
                    editingEvent = null
                    showAddEventDialog = true
                },
                onDismiss = {
                    showVoiceEventDialog = false
                }
            )
        }

        // Add/Edit Event Dialog
        if (showAddEventDialog) {
            // Calculate end time from parsed data if available
            val calculatedEndTime = parsedEventData?.calculateEndTime()

            // Determine existing event's provider (for editing synced events)
            // Local events have calendarId="local" - they don't have a provider
            val existingEventProviderId = editingEvent?.let { event ->
                if (event.calendarId == "local") null
                else ProviderId.fromKey(event.providerId)
            }

            val primaryGoogleAccountId = multiAccountUiState.googleAccounts.firstOrNull()?.account?.id
            val primaryOutlookAccountId = multiAccountUiState.outlookAccounts.firstOrNull()?.account?.id
            val writableCalendarsForDialog = writableCalendars.filter { calendar ->
                (calendar.providerId == ProviderId.GOOGLE && calendar.accountId == primaryGoogleAccountId) ||
                    (calendar.providerId == ProviderId.OUTLOOK && calendar.accountId == primaryOutlookAccountId)
            }

            // For local events, look up the original LocalEvent to get local calendar ID
            var localEventForEdit by remember { mutableStateOf<LocalEvent?>(null) }
            LaunchedEffect(editingEvent) {
                val event = editingEvent  // Capture in local val for smart cast
                localEventForEdit = if (event != null && event.calendarId == "local") {
                    withContext(Dispatchers.IO) {
                        localEventRepository.getEventById(event.id)
                    }
                } else null
            }

            AddEditEventDialog(
                onDismiss = {
                    showAddEventDialog = false
                    editingEvent = null
                    parsedEventData = null  // Clear parsed data on dismiss
                },
                onSave = { calendarId, title, date, startHour, startMin, endHour, endMin, isAllDay, location, description, attendees, syncEnabled, localCalendarId, syncProviderId ->
                    android.util.Log.d("MainScreen", "=== onSave CALLED ===")
                    android.util.Log.d("MainScreen", "calendarId: $calendarId")
                    android.util.Log.d("MainScreen", "syncProviderId: $syncProviderId")
                    android.util.Log.d("MainScreen", "syncEnabled: $syncEnabled")
                    android.util.Log.d("MainScreen", "editingEvent: ${editingEvent?.id}, calendarId=${editingEvent?.calendarId}")
                    android.util.Log.d("MainScreen", "writableCalendarsForDialog count: ${writableCalendarsForDialog.size}")
                    writableCalendarsForDialog.forEach { cal ->
                        android.util.Log.d("MainScreen", "  Available calendar: ${cal.name} (id=${cal.id}, provider=${cal.providerId})")
                    }
                    val selectedCalendar = writableCalendarsForDialog.firstOrNull {
                        syncProviderId != null && it.providerId == syncProviderId && it.id == calendarId
                    }
                    android.util.Log.d("MainScreen", "selectedCalendar: ${selectedCalendar?.name ?: "NULL"}")
                    val syncAccountId = when {
                        editingEvent?.accountId?.isNotBlank() == true -> editingEvent!!.accountId
                        selectedCalendar != null -> selectedCalendar.accountId
                        syncProviderId == ProviderId.GOOGLE -> primaryGoogleAccountId
                        syncProviderId == ProviderId.OUTLOOK -> primaryOutlookAccountId
                        else -> null
                    }
                    android.util.Log.d("MainScreen", "syncAccountId: $syncAccountId")
                    if (editingEvent != null) {
                        // Update existing event
                        eventsViewModel.updateEvent(
                            eventId = editingEvent!!.id,
                            calendarId = calendarId,
                            title = title,
                            date = date,
                            startTimeHour = startHour,
                            startTimeMinute = startMin,
                            endTimeHour = endHour,
                            endTimeMinute = endMin,
                            isAllDay = isAllDay,
                            location = location?.takeIf { it.isNotBlank() },
                            description = description?.takeIf { it.isNotBlank() },
                            attendees = attendees?.takeIf { it.isNotBlank() },
                            syncEnabled = syncEnabled,
                            syncProviderId = syncProviderId,
                            syncAccountId = syncAccountId,
                            localCalendarId = localCalendarId
                        ) { result ->
                            when (result) {
                                is EventOperationResult.Success -> {
                                    showAddEventDialog = false
                                    editingEvent = null
                                    parsedEventData = null
                                    SnackState.globalSnackFlow.tryEmit(
                                        SnackConf(text = "Event updated", duration = SnackConf.DURATION_STANDARD)
                                    )
                                }
                                is EventOperationResult.Error -> {
                                    // Show error message to user - dialog stays open for retry
                                    val errorMessage = eventsViewModel.lastOperationMessage.value ?: result.message
                                    SnackState.globalSnackFlow.tryEmit(
                                        SnackConf(text = errorMessage, duration = SnackConf.DURATION_ERROR)
                                    )
                                }
                                else -> {
                                    // Other result types (NoNetwork, NotSignedIn, PermissionDenied) - show error
                                    val errorMessage = eventsViewModel.lastOperationMessage.value ?: "Operation failed"
                                    SnackState.globalSnackFlow.tryEmit(
                                        SnackConf(text = errorMessage, duration = SnackConf.DURATION_ERROR)
                                    )
                                }
                            }
                        }
                    } else {
                        // Create new event
                        eventsViewModel.createEvent(
                            calendarId = calendarId,
                            title = title,
                            date = date,
                            startTimeHour = startHour,
                            startTimeMinute = startMin,
                            endTimeHour = endHour,
                            endTimeMinute = endMin,
                            isAllDay = isAllDay,
                            location = location?.takeIf { it.isNotBlank() },
                            description = description?.takeIf { it.isNotBlank() },
                            attendees = attendees?.takeIf { it.isNotBlank() },
                            syncEnabled = syncEnabled,
                            syncProviderId = syncProviderId,
                            syncAccountId = syncAccountId,
                            localCalendarId = localCalendarId
                        ) { result ->
                            when (result) {
                                is EventOperationResult.Success -> {
                                    showAddEventDialog = false
                                    parsedEventData = null
                                    SnackState.globalSnackFlow.tryEmit(
                                        SnackConf(text = "Event created", duration = SnackConf.DURATION_STANDARD)
                                    )
                                }
                                is EventOperationResult.Error -> {
                                    // Show error message to user - dialog stays open for retry
                                    val errorMessage = eventsViewModel.lastOperationMessage.value ?: result.message
                                    SnackState.globalSnackFlow.tryEmit(
                                        SnackConf(text = errorMessage, duration = SnackConf.DURATION_ERROR)
                                    )
                                }
                                else -> {
                                    // Other result types (NoNetwork, NotSignedIn, PermissionDenied) - show error
                                    val errorMessage = eventsViewModel.lastOperationMessage.value ?: "Operation failed"
                                    SnackState.globalSnackFlow.tryEmit(
                                        SnackConf(text = errorMessage, duration = SnackConf.DURATION_ERROR)
                                    )
                                }
                            }
                        }
                    }
                },
                onMoveToLocal = { cloudEvent ->
                    scope.launch {
                        eventsViewModel.moveCloudToLocal(cloudEvent).fold(
                            onSuccess = { localEvent ->
                                showAddEventDialog = false
                                editingEvent = null
                                parsedEventData = null
                                selectedEventForDetails = localEvent.toCalendarEvent()
                                SnackState.globalSnackFlow.tryEmit(
                                    SnackConf(text = "Event moved to local only", duration = SnackConf.DURATION_STANDARD)
                                )
                            },
                            onFailure = { e ->
                                val message = e.message ?: "Failed to move event"
                                SnackState.globalSnackFlow.tryEmit(
                                    SnackConf(text = message, duration = SnackConf.DURATION_STANDARD)
                                )
                            }
                        )
                    }
                },
                // Voice input callback - opens voice dialog from within AddEditEventDialog
                onVoiceInput = {
                    // Without the user's OpenRouter key the event editor stays open under the key popup
                    if (com.boox.day.ui.AiKeyGate.requireKey()) {
                        showAddEventDialog = false
                        parsedEventData = null
                        showVoiceEventDialog = true
                    }
                },
                writableCalendars = writableCalendarsForDialog,
                existingEvent = editingEvent,
                initialDate = parsedEventData?.date ?: compactAddEventDate ?: selectedDate,
                isLoading = operationInProgress,
                // Pre-fill from voice input
                prefillTitle = parsedEventData?.title,
                prefillDate = parsedEventData?.date,
                prefillStartHour = parsedEventData?.startHour,
                prefillStartMinute = parsedEventData?.startMinute,
                prefillEndHour = calculatedEndTime?.first,
                prefillEndMinute = calculatedEndTime?.second,
                prefillIsAllDay = parsedEventData?.isAllDay,
                prefillLocation = parsedEventData?.location,
                // Use getDescriptionOnly() when syncing to avoid duplicating attendees
                // In local mode, getEffectiveDescription() includes attendees in description
                prefillDescription = if (syncModeSetting == AppSettings.SyncMode.HYBRID && hasAnyExternalAccount)
                    parsedEventData?.getDescriptionOnly()
                else
                    parsedEventData?.getEffectiveDescription(),
                prefillAttendees = parsedEventData?.attendees?.takeIf { it.isNotEmpty() },
                prefillConfidenceLow = parsedEventData?.let { !it.isHighConfidence } ?: false,
                // Sync mode settings
                syncMode = syncModeSetting,
                isSignedIn = hasAnyExternalAccount,  // Updated: use connected account check
                defaultSyncEnabled = defaultSyncEnabled,
                // For existing events: local events are not synced, cloud events are synced
                existingSyncEnabled = when (editingEvent?.calendarId) {
                    null -> null  // No event being edited
                    "local" -> false  // Local events are not synced
                    else -> true  // Cloud events (from CalendarEvent table) are synced
                },
                // Local calendars (for local-only events)
                localCalendars = localCalendars,
                existingLocalCalendarId = localEventForEdit?.localCalendarId,
                // Connected cloud accounts
                hasGoogleAccount = hasGoogleAccount,
                hasOutlookAccount = hasOutlookAccount,
                existingProviderId = existingEventProviderId
            )
        }

        // Event Details Dialog
        if (showEventDetailsDialog && selectedEventForDetails != null) {
            EventDetailsDialog(
                event = selectedEventForDetails!!,
                onDismiss = {
                    showEventDetailsDialog = false
                    selectedEventForDetails = null
                },
                onEdit = if (!selectedEventForDetails!!.isReadOnly) {
                    {
                        editingEvent = selectedEventForDetails
                        showEventDetailsDialog = false
                        showAddEventDialog = true
                    }
                } else null,
                onDelete = if (!selectedEventForDetails!!.isReadOnly) {
                    {
                        eventsViewModel.deleteEvent(
                            eventId = selectedEventForDetails!!.id,
                            calendarId = selectedEventForDetails!!.calendarId
                        ) { result ->
                            when (result) {
                                is EventOperationResult.Success -> {
                                    showEventDetailsDialog = false
                                    selectedEventForDetails = null
                                    // Use ViewModel's message (handles offline "will be deleted when online" case)
                                    val message = eventsViewModel.lastOperationMessage.value ?: "Event deleted"
                                    SnackState.globalSnackFlow.tryEmit(
                                        SnackConf(text = message, duration = SnackConf.DURATION_STANDARD)
                                    )
                                }
                                else -> {
                                    // Error handled by ViewModel's lastOperationMessage
                                    val errorMessage = eventsViewModel.lastOperationMessage.value ?: "Delete failed"
                                    SnackState.globalSnackFlow.tryEmit(
                                        SnackConf(text = errorMessage, duration = SnackConf.DURATION_STANDARD)
                                    )
                                }
                            }
                        }
                    }
                } else null,
                isLoading = operationInProgress,
                // Boox Notes linked to this event
                linkedBooxNotes = linkedBooxNotesForEvent,
                onBooxNoteClick = { note ->
                    // Direct launch: Open Boox Notes app with this note
                    booxNotesIntegration.openNote(note.uniqueId)
                },
                onCreateBooxNote = {
                    // Launch Boox Notes app to create a new note
                    booxNotesIntegration.createQuickNote()
                },
                // Sync mode and status display
                syncMode = syncModeSetting,
                isSignedIn = isSignedIn,
                syncEnabled = isSignedIn, // For events from Google Calendar, assume sync enabled if signed in
                syncStatus = null, // CalendarEvent doesn't have syncStatus yet - will be populated when using LocalEvent
                lastSyncedAt = selectedEventForDetails?.syncedAt,
                onToggleSync = null, // No toggle for CalendarEvent - requires migration to LocalEvent
                // Local calendar info (for Standalone mode)
                // TODO: Get actual local calendar info when using LocalEvent
                localCalendarName = localCalendars.find { it.isDefault }?.name,
                localCalendarColorHex = localCalendars.find { it.isDefault }?.colorHex
            )
        }

        // Boox Notes Dialog
        if (showBooxNotesDialog) {
            BooxNotesDialog(
                date = selectedDate,
                booxNoteRepository = booxNoteRepository,
                booxNotesIntegration = booxNotesIntegration,
                booxNoteIndexer = booxNoteIndexer,  // Smart scan on dialog open
                // Pass OCR repository only if OCR indexing is enabled (search tab appears)
                // To disable search: set this to null
                ocrRepository = if (GlobalAppSettings.current.ocrSettings.enabled) ocrRepository else null,
                isOcrIndexingInProgress = GlobalAppSettings.isBooxNotesScanning || (ocrProcessingState?.isProcessing == true),
                eventRepository = eventRepository,
                onDismiss = {
                    showBooxNotesDialog = false
                    autoOpenBulkWizard = false  // Reset for next open
                },
                onLinkChanged = {
                    // Refresh event links when a note-event link changes
                    noteLinkRefreshTrigger++
                },
                autoOpenBulkWizard = autoOpenBulkWizard,
                sourceFilter = sourceFilter,
                onOpenCalendarAccounts = {
                    showBooxNotesDialog = false
                    autoOpenBulkWizard = false
                    showMultiProviderSyncDialog = true
                }
            )
        }

        // ─────────────────────────────────────────────────────────────────────────────
        // Day Quick View Popup (long-press on calendar day)
        // Shows Boox Notes, events count, and tasks due count for the day
        // ─────────────────────────────────────────────────────────────────────────────
        if (showDayQuickView && quickViewData != null) {
            DayQuickViewPopup(
                data = quickViewData!!,
                onNavigateToEvents = { openQuickViewList("events") },
                onNavigateToTasksDue = { openQuickViewList("due") },
                onOpenBooxNote = { uniqueId, title ->
                    // Open the note in Boox Notes app (pass title for diagnostic logging)
                    booxNotesIntegration.openNote(uniqueId, title)
                    showDayQuickView = false
                },
                onManageBooxNote = { noteDate ->
                    // Open Boox Notes dialog pre-navigated to the note's date
                    selectedDate = noteDate
                    showBooxNotesDialog = true
                    showDayQuickView = false
                },
                onDismiss = {
                    showDayQuickView = false
                }
            )
        }
    }
}

/**
 * Shared canvas host that keeps a single SurfaceView/TouchHelper alive.
 * Swaps page/state/history between Memos and Tasks without recreating the SurfaceView.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PersistentCanvasHost(
    navController: NavController,
    selectedDate: String,
    appRepository: AppRepository,
    snackManager: SnackState,
    scope: kotlinx.coroutines.CoroutineScope,
    tabManager: EditorTabManager,
    currentTab: EditorTab,
    memosPageId: String,
    tasksPageId: String,
    tasks: List<Task>,
    taskPageInfo: TaskPageInfo?,
    memosIsLoading: Boolean,
    tasksIsLoading: Boolean,
    onToggleTaskCompleted: (Task) -> Unit,
    onChangeTaskPriority: (Task, String) -> Unit,
    onDueDateChanged: (Task, Long?) -> Unit,
    onMoveTask: (Task, String, Int) -> Unit,
    onTaskPageNav: (Int) -> Unit,
    onTaskTypedTextChanged: (Task, String?) -> Unit,
    onTaskCleared: (Task) -> Unit,
    onTaskInkErased: (Task) -> Unit,
    // BUG FIX: Callback to report actual canvas height for stroke migration calculations
    onCanvasHeightChanged: ((Float) -> Unit)? = null,
    // AI FEATURE: Provide a function to capture the current tasks page bitmap for OCR
    onTasksBitmapProviderReady: ((provider: () -> android.graphics.Bitmap?) -> Unit)? = null,
    // Callback to refresh tasks from database after operations that modify multiple tasks
    // Optional pageIndex parameter: if null, refresh current page; if provided, navigate to that page
    onTasksNeedRefresh: ((pageIndex: Int?) -> Unit)? = null,
    // Callback to navigate to a different date (used by TaskDueDateDialog)
    onDateChange: ((String) -> Unit)? = null,
    // Convert lasso selection to tasks (Memos AI feature)
    onConvertSelectionToTasks: ((List<com.boox.day.data.db.Stroke>) -> Unit)? = null,
    // Task card "Break down" (plan T3): AI break-down of one task; null hides the button
    onBreakDownTask: ((Task) -> Unit)? = null,
    // Go to task (plan T3): row to highlight briefly
    highlightedTaskId: String? = null
) {
    val context = LocalContext.current
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val surfaceSize by DrawCanvas.surfaceSize.collectAsState()
            val surfaceWidth = surfaceSize?.width?.takeIf { it > 0 }
            val surfaceHeight = surfaceSize?.height?.takeIf { it > 0 }
            val layoutHeight = convertDpToPixel(this.maxHeight, context).toInt()
            val height = surfaceHeight ?: layoutHeight
            val width = surfaceWidth ?: convertDpToPixel(this.maxWidth, context).toInt()
            // Report canvas height and top offset when they change
            val topOffsetPx = convertDpToPixel(TASKS_TOOLBAR_OFFSET, context)
            // Full screen (Oct 2026, ui/FullScreenState): Tasks rows keep the normal view's height, so
            // only the normal view's canvas height is reported — once the surface has caught up with
            // the layout, never a passing size during a switch
            val isFullScreen = com.boox.day.ui.FullScreenState.isOn
            val isNormalViewHeight = !isFullScreen && abs(layoutHeight - height) <= 2
            LaunchedEffect(height, isNormalViewHeight) {
                if (!isNormalViewHeight) return@LaunchedEffect
                onCanvasHeightChanged?.invoke(height.toFloat())
                // Store for background OCR (DigitalInkService, TaskTextExtractor)
                // so they can compute correct row boundaries matching the visual overlay
                GlobalAppSettings.setTasksCanvasHeight(height.toFloat())
                GlobalAppSettings.setTasksTopOffsetPx(topOffsetPx)
            }
            // Height of the Tasks rows: the canvas height in the normal view; in full screen the rows
            // keep that row height (as many as the page has), and the space under the last row is empty
            val tasksRowCount = if (tasks.isEmpty()) 10 else tasks.size
            val normalTasksHeight = GlobalAppSettings.tasksCanvasHeight
            val tasksRowsHeight = if (isFullScreen && normalTasksHeight > topOffsetPx) {
                (topOffsetPx + tasksRowCount * (normalTasksHeight - topOffsetPx) / 10f).roundToInt()
            } else {
                height
            }
            // Task repository calls take the normal view's height (10 rows) and work out the day's rows
            val tasksNormalHeight = if (normalTasksHeight > 0f) normalTasksHeight else height.toFloat()
            // Memo ink or images below the normal view (written in full screen): the full screen
            // button shows a small arrow, and the first time a page shows it, a hint says so
            LaunchedEffect(memosPageId, height, isNormalViewHeight, currentTab) {
                val fullScreen = com.boox.day.ui.FullScreenState
                if (!isNormalViewHeight) {
                    fullScreen.memoHasMoreBelow = false
                    return@LaunchedEffect
                }
                suspend fun check() {
                    val bottom = withContext(Dispatchers.Default) { pageContentBottom(memosPageId) }
                    val moreBelow = bottom > height + MORE_BELOW_MARGIN_PX
                    fullScreen.memoHasMoreBelow = moreBelow
                    if (moreBelow && currentTab == EditorTab.Memos && fullScreen.hintedPages.add(memosPageId)) {
                        snackManager.displaySnack(
                            SnackConf(text = "More of this page is below. Use Full screen to see it all.", duration = SnackConf.DURATION_STANDARD)
                        )
                    }
                }
                check()
                delay(1_500L)  // the page's ink may still be loading
                check()
                DrawCanvas.contentChanged.collect { check() }
            }

            // PERF: Protect both Memos and Tasks pages from cache eviction during tab switches
            LaunchedEffect(memosPageId, tasksPageId) {
                if (memosPageId.isNotEmpty() && tasksPageId.isNotEmpty()) {
                    com.boox.day.data.PageDataManager.setDualActivePages(memosPageId, tasksPageId)
                }
            }

            // MEMOS objects
        val memosPage = remember { mutableStateOf<PageView?>(null) }
        val memosState = remember { mutableStateOf<EditorState?>(null) }
        val memosHistory = remember { mutableStateOf<History?>(null) }
        val memosControlTower = remember { mutableStateOf<EditorControlTower?>(null) }

        // Save Memos state on dispose
        DisposableEffect(memosPageId) {
            onDispose {
                memosState.value?.let { oldState ->
                    android.util.Log.i("TOOL_STATE", "DisposableEffect: Saving Memos state")
                    tabManager.saveToolState(EditorTab.Memos, oldState)
                }
            }
        }

        // OPTIMIZATION: Reuse existing Memos objects when possible
        // ============================================================================
        // 1PX MISMATCH FIX (RESOLVED - December 2025)
        // ============================================================================
        // Original problem: Compose's BoxWithConstraints used toInt() truncation (1376),
        // but SurfaceView.surfaceChanged() used internal rounding (1377).
        //
        // Solution: Single source of truth via DrawCanvas.surfaceSize StateFlow.
        // MainScreen now uses surfaceSize as primary source, and PageView dimensions
        // are synced via syncToSurfaceSize(). The 1px tolerance below is now a safety net.
        //
        // Note: This is about dimension mismatch, NOT the separate "black lines" caching bug.
        // WARNING: Do NOT modify the dimension check or add currentTab back.
        //          See memory.md Section 12.1 for full documentation.
        // ============================================================================
        ResizeHiddenPageInPlace(memosPage.value, isActive = currentTab == EditorTab.Memos, surfaceSize = surfaceSize)
        LaunchedEffect(memosPageId, width, height) {
            val currentPage = memosPage.value
            val currentState = memosState.value
            val currentHistory = memosHistory.value
            val currentControl = memosControlTower.value

            // 1PX MISMATCH FIX: Use 1px tolerance for dimension check (safety net - see comment above)
            if (currentPage != null && currentState != null && currentHistory != null && currentControl != null &&
                abs(currentPage.viewWidth - width) <= 1 && abs(currentPage.viewHeight - height) <= 1) {

                // Dimensions match (with 1px tolerance) - REUSE existing PageView
                if (currentPage.currentPageId != memosPageId) {
                    // LAZY LOAD FIX: Only update if tab is active to prevent surface flicker
                    if (currentTab == EditorTab.Memos) {
                        currentPage.changePage(memosPageId)
                        currentState.dayDate = selectedDate
                        currentState.changePage(memosPageId)
                        currentState.refreshDayPageInfo()
                        currentHistory.cleanHistory()
                    }
                }
            } else {
                // Initialize or Recreate (first run or resize)
                android.util.Log.i("PERF", "Creating NEW Memos objects (init/resize)")
                
                // Dispose old if exists
                memosControlTower.value?.cancelObservers()
                memosHistory.value?.cleanHistory()
                memosPage.value?.disposeOldPage()

                val page = PageView(
                    context = context,
                    coroutineScope = scope,
                    currentPageId = memosPageId,
                    viewWidth = width,
                    viewHeight = height,
                    snackManager = snackManager,
                    appRepository = appRepository
                )
                val state = EditorState(
                    bookId = null,
                    dayDate = selectedDate,
                    pageId = memosPageId,
                    pageView = page,
                    appRepository = appRepository,
                    onPageChange = { }
                ).apply {
                    refreshDayPageInfo()
                    hasInternalToolbar = false
                    val restored = tabManager.restoreToolState(EditorTab.Memos, this)
                    if (!restored) {
                        android.util.Log.i("TOOL_STATE", "Fallback to Draw mode for Memos")
                        mode = com.boox.day.editor.state.Mode.Draw
                    }
                }
                val history = History(page)
                val controlTower = EditorControlTower(scope, page, history, state).apply { registerObservers() }

                memosPage.value = page
                memosState.value = state
                memosHistory.value = history
                memosControlTower.value = controlTower
            }
        }

        // Only register when all dependencies are ready
        val memosReady = memosPage.value != null &&
            memosState.value != null &&
            memosHistory.value != null &&
            memosControlTower.value != null
        if (memosReady) {
            val readyState = memosState.value!!
            val readyControl = memosControlTower.value!!
            val readyPage = memosPage.value!!
            val readyHistory = memosHistory.value!!
            
            // Register canvas with tab manager (only needs to happen once per instance)
            DisposableEffect(readyState) {
                tabManager.registerMemosCanvas(readyState, readyControl)
                onDispose {
                    tabManager.unregisterMemosCanvas()
                    // Don't dispose page here - we want to keep it alive for reuse!
                    // Cleanup happens in LaunchedEffect or parent dispose
                }
            }
            
            // Clean up when Host is destroyed
            DisposableEffect(Unit) {
                onDispose {
                    readyState.selectionState.applySelectionDisplace(readyPage)
                    readyState.textBoxState.reset()
                    readyHistory.cleanHistory()
                    readyControl.cancelObservers()
                    readyPage.disposeOldPage()
                }
            }
        }

        // TASKS objects
        var taskBeingMoved by remember { mutableStateOf<Task?>(null) }
        var taskBeingTyped by remember { mutableStateOf<Task?>(null) }
        var childTaskBeingCopied by remember { mutableStateOf<Task?>(null) }
        var dueDateTargetTask by remember { mutableStateOf<Task?>(null) }
        var dueDateTargetTemplate by remember { mutableStateOf<com.boox.day.data.db.RecurringTaskTemplate?>(null) }
        var dueDateTargetBlockedReason by remember { mutableStateOf<String?>(null) }
        var recurringSeriesDeleteTask by remember { mutableStateOf<Task?>(null) }
        var visibleRecurringTemplates by remember {
            mutableStateOf<Map<String, com.boox.day.data.db.RecurringTaskTemplate>>(emptyMap())
        }
        var taskContentVersion by remember { mutableIntStateOf(0) }

        // Task card (plan T3, Oct 2026): the chevron of a row with content opens one card with the
        // task's text, priority, due date, repeat, reminder time, subtasks, move/copy and delete
        var taskCard by remember { mutableStateOf<com.boox.day.editor.ui.TaskCardData?>(null) }
        var taskPendingDelete by remember { mutableStateOf<Task?>(null) }

        fun openTaskCard(task: Task) {
            scope.launch {
                taskCard = withContext(Dispatchers.IO) {
                    val fresh = appRepository.taskRepository.getTaskById(task.id) ?: task
                    val template = fresh.templateId?.let { appRepository.taskRepository.getRecurringTemplateById(it) }
                    val children = appRepository.taskRepository.getChildTasks(fresh.id)
                    com.boox.day.editor.ui.TaskCardData(
                        task = fresh,
                        repeatLabel = template?.let { com.boox.day.editor.ui.taskRepeatLabel(it) },
                        subtaskCount = children.size,
                        subtasksDone = children.count { it.isCompleted }
                    )
                }
            }
        }

        fun dismissTaskScheduleDialog() {
            dueDateTargetTask = null
            dueDateTargetTemplate = null
            dueDateTargetBlockedReason = null
        }

        fun openTaskScheduleDialog(task: Task) {
            scope.launch {
                val dialogData = withContext(Dispatchers.IO) {
                    val freshTask = appRepository.taskRepository.getTaskById(task.id) ?: task
                    Triple(
                        freshTask,
                        freshTask.templateId?.let { appRepository.taskRepository.getRecurringTemplateById(it) },
                        appRepository.taskRepository.getRecurringSeriesBlockedReason(freshTask)
                    )
                }
                dueDateTargetTask = dialogData.first
                dueDateTargetTemplate = dialogData.second
                dueDateTargetBlockedReason = dialogData.third
            }
        }

        // PERF: Two-phase task content detection for instant tab switching
        // Phase 1 (SYNC): Metadata-only map computed during composition - available immediately
        // This catches tasks with typedText, completion status, or priority set
        val metadataContentMap = remember(tasks) {
            tasks.associate { task ->
                task.id to (task.typedText?.isNotBlank() == true ||
                    task.isCompleted ||
                    task.priority.isNotEmpty())
            }
        }

        // Phase 2 (ASYNC): Full map with stroke detection - enhances metadata map
        // Tracks which task set it was computed for to detect staleness
        var asyncContentMap by remember { mutableStateOf<Map<String, Boolean>?>(null) }
        var asyncMapTaskIds by remember { mutableStateOf<Set<String>?>(null) }
        // Track which rows have strokes (for hiding recognizedText overlay when strokes exist)
        var asyncRowsWithStrokes by remember { mutableStateOf<Set<Int>>(emptySet()) }

        // Final taskContentMap: use async if computed for current tasks, else fall back to metadata-only
        val currentTaskIds = remember(tasks) { tasks.map { it.id }.toSet() }
        val taskContentMap = if (asyncMapTaskIds == currentTaskIds && asyncContentMap != null) {
            asyncContentMap!!
        } else {
            metadataContentMap
        }
        val tasksPage = remember { mutableStateOf<PageView?>(null) }
        val tasksState = remember { mutableStateOf<EditorState?>(null) }
        val tasksHistory = remember { mutableStateOf<History?>(null) }
        val tasksControlTower = remember { mutableStateOf<EditorControlTower?>(null) }

        // Save Tasks state on dispose
        DisposableEffect(tasksPageId) {
            onDispose {
                tasksState.value?.let { oldState ->
                    android.util.Log.i("TOOL_STATE", "DisposableEffect: Saving Tasks state")
                    tabManager.saveToolState(EditorTab.Tasks, oldState)
                }
            }
        }

        // OPTIMIZATION: Reuse existing Tasks objects when possible
        // ============================================================================
        // 1PX MISMATCH FIX (RESOLVED - December 2025) - DO NOT ADD currentTab TO DEPENDENCIES
        // ============================================================================
        // See Memos LaunchedEffect above for full explanation.
        // The same 1px tolerance dimension check is used here for Tasks.
        // ============================================================================
        ResizeHiddenPageInPlace(tasksPage.value, isActive = currentTab == EditorTab.Tasks, surfaceSize = surfaceSize)
        LaunchedEffect(tasksPageId, width, height) {
            val currentPage = tasksPage.value
            val currentState = tasksState.value
            val currentHistory = tasksHistory.value
            val currentControl = tasksControlTower.value

            // 1PX MISMATCH FIX: Use 1px tolerance for dimension check (safety net - see comment above)
            if (currentPage != null && currentState != null && currentHistory != null && currentControl != null &&
                abs(currentPage.viewWidth - width) <= 1 && abs(currentPage.viewHeight - height) <= 1) {

                // Dimensions match (with 1px tolerance) - REUSE existing PageView
                if (currentPage.currentPageId != tasksPageId) {
                    // LAZY LOAD FIX: Only update if tab is active to prevent surface flicker
                    if (currentTab == EditorTab.Tasks) {
                        currentPage.changePage(tasksPageId)
                        currentState.dayDate = selectedDate
                        currentState.changePage(tasksPageId)
                        currentState.refreshDayPageInfo()
                        currentHistory.cleanHistory()
                    }
                }
            } else {
                // Initialize or Recreate
                android.util.Log.i("PERF", "Creating NEW Tasks objects (init/resize)")
                
                tasksControlTower.value?.cancelObservers()
                tasksHistory.value?.cleanHistory()
                tasksPage.value?.disposeOldPage()

                val page = PageView(
                    context = context,
                    coroutineScope = scope,
                    currentPageId = tasksPageId,
                    viewWidth = width,
                    viewHeight = height,
                    snackManager = snackManager,
                    appRepository = appRepository
                )
                val state = EditorState(
                    bookId = null,
                    dayDate = selectedDate,
                    pageId = tasksPageId,
                    pageView = page,
                    appRepository = appRepository,
                    onPageChange = { }
                ).apply {
                    refreshDayPageInfo()
                    hasInternalToolbar = false
                    val restored = tabManager.restoreToolState(EditorTab.Tasks, this)
                    if (!restored) {
                        android.util.Log.i("TOOL_STATE", "Fallback to Draw mode for Tasks")
                        mode = com.boox.day.editor.state.Mode.Draw
                    }
                }
                val history = History(page)
                val controlTower = EditorControlTower(scope, page, history, state).apply { registerObservers() }

                tasksPage.value = page
                tasksState.value = state
                tasksHistory.value = history
                tasksControlTower.value = controlTower
            }
        }

        val tasksReady = tasksPage.value != null &&
            tasksState.value != null &&
            tasksHistory.value != null &&
            tasksControlTower.value != null
        if (tasksReady) {
            val readyState = tasksState.value!!
            val readyControl = tasksControlTower.value!!
            val readyPage = tasksPage.value!!
            val readyHistory = tasksHistory.value!!
            
            DisposableEffect(readyState) {
                tabManager.registerTasksCanvas(readyState, readyControl)
                onDispose {
                    tabManager.unregisterTasksCanvas()
                }
            }
            
            DisposableEffect(Unit) {
                onDispose {
                    readyState.selectionState.applySelectionDisplace(readyPage)
                    readyState.textBoxState.reset()
                    readyHistory.cleanHistory()
                    readyControl.cancelObservers()
                    readyPage.disposeOldPage()
                }
            }
        }

        // Full screen Tasks (Oct 2026): pages of more rows of the same height (tasks/TaskPagination)
        TasksFullScreenPages(
            isFullScreen = isFullScreen,
            isTasksTab = currentTab == EditorTab.Tasks,
            selectedDate = selectedDate,
            fullScreenHeight = if (isFullScreen && abs(layoutHeight - height) <= 2) height else 0,
            topOffsetPx = topOffsetPx,
            rowCount = tasksRowCount,
            rowsHeight = tasksRowsHeight,
            taskPageInfo = taskPageInfo,
            tasksHistory = tasksHistory.value,
            onTasksNeedRefresh = onTasksNeedRefresh
        )

        // AI FEATURE: Provide bitmap capture function when tasks page is ready
        LaunchedEffect(tasksPage.value) {
            val page = tasksPage.value
            if (page != null) {
                onTasksBitmapProviderReady?.invoke {
                    // Return a copy of the current tasks page bitmap for AI processing
                    page.windowedBitmap.copy(page.windowedBitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888, false)
                }
            }
        }

        // Sync completed row indices to Tasks PageView for stroke graying
        LaunchedEffect(tasks, tasksIsLoading, currentTab) {
            if (tasksIsLoading || tasks.isEmpty()) return@LaunchedEffect

            val completedRows = tasks.filter { it.isCompleted }.map { it.rowIndex }.toSet()
            val page = tasksPage.value
            if (page != null && page.completedRowIndices != completedRows) {
                page.completedRowIndices = completedRows
                // STROKE_CLEAR_FIX: Only emit forceUpdate when Tasks tab is active.
                // This LaunchedEffect fires on every `tasks` list change, including during
                // date switching (tasks briefly becomes emptyList()). forceUpdate.emit(null)
                // triggers a full close/open raw drawing cycle on the ACTIVE canvas.
                // If Memos is active, this wipes the Memos bitmap while strokes are still
                // loading from DB, causing the "strokes get cleared" bug.
                // See EditorTabManager.kt:254,301 for prior instance of this exact bug.
                if (currentTab == EditorTab.Tasks) {
                    DrawCanvas.forceUpdate.emit(null)
                }
            }
        }

        // Listen to contentChanged signal and trigger taskContentMap recalculation when on Tasks tab
        // This ensures chevron behavior updates after user draws strokes
        LaunchedEffect(tasksPageId, currentTab) {
            if (currentTab == EditorTab.Tasks) {
                DrawCanvas.contentChanged.collect {
                    taskContentVersion++
                }
            }
        }

        // PERF: Async stroke detection - enhances metadata-only map with stroke data
        // taskContentVersion is incremented when strokes are drawn to trigger recalculation
        // Uses optimized Spatial Hashing (Stroke -> Row) instead of iterating rows
        LaunchedEffect(tasks, tasksPageId, tasksRowsHeight, taskContentVersion) {
            val taskIdSet = tasks.map { it.id }.toSet()

            // Handle empty tasks case
            if (tasks.isEmpty()) {
                asyncContentMap = emptyMap()
                asyncMapTaskIds = taskIdSet
                asyncRowsWithStrokes = emptySet()
                return@LaunchedEffect
            }

            // Wait for valid height before computing
            if (tasksRowsHeight <= 0) return@LaunchedEffect

            val rowCount = tasks.size
            val (newMap, strokeRows) = withContext(Dispatchers.Default) {
                // 1. Get all strokes for the page once (O(1) access if cached)
                val allStrokes = com.boox.day.data.PageDataManager.getStrokes(tasksPageId)

                // 2. Map strokes to row indices using unified algorithm (O(Strokes * 10))
                // Uses getDominantRowIndex for correct row assignment matching visual overlay
                val rowsWithStrokes = mutableSetOf<Int>()
                allStrokes.forEach { stroke ->
                    val rowIndex = getDominantRowIndex(stroke, tasksRowsHeight.toFloat(), rowCount)
                    if (rowIndex in 0 until rowCount) {
                        rowsWithStrokes.add(rowIndex)
                    }
                }

                // 3. Build map (O(Rows)) - combines stroke detection with metadata
                val contentMap = tasks.associate { task ->
                    val hasContent = if (task.rowIndex in 0 until rowCount) {
                        // Check if row was marked by spatial hash OR has metadata
                        rowsWithStrokes.contains(task.rowIndex) ||
                        task.typedText?.isNotBlank() == true ||
                        task.isCompleted ||
                        task.priority.isNotEmpty()
                    } else {
                        // Fallback for invalid row index
                        task.typedText?.isNotBlank() == true ||
                        task.isCompleted ||
                        task.priority.isNotEmpty()
                    }
                    task.id to hasContent
                }
                contentMap to rowsWithStrokes.toSet()
            }
            asyncContentMap = newMap
            asyncMapTaskIds = taskIdSet
            asyncRowsWithStrokes = strokeRows
        }

        LaunchedEffect(tasks) {
            val templateIds = tasks.mapNotNull { it.templateId }.distinct()
            visibleRecurringTemplates = if (templateIds.isEmpty()) {
                emptyMap()
            } else {
                withContext(Dispatchers.IO) {
                    appRepository.taskRepository.getRecurringTemplatesByIds(templateIds)
                }
            }
        }

        // Guard: wait until both memo/task objects are initialized
        if (memosPage.value == null || memosState.value == null || memosHistory.value == null || memosControlTower.value == null ||
            tasksPage.value == null || tasksState.value == null || tasksHistory.value == null || tasksControlTower.value == null
        ) {
            return@BoxWithConstraints
        }

        // Active bindings
        val activePage: PageView
        val activeState: EditorState
        val activeHistory: History
        val activeControlTower: EditorControlTower
        val isMemosCanvas: Boolean
        if (currentTab == EditorTab.Memos) {
            activePage = memosPage.value!!
            activeState = memosState.value!!
            activeHistory = memosHistory.value!!
            activeControlTower = memosControlTower.value!!
            isMemosCanvas = true
        } else {
            activePage = tasksPage.value!!
            activeState = tasksState.value!!
            activeHistory = tasksHistory.value!!
            activeControlTower = tasksControlTower.value!!
            isMemosCanvas = false
        }

        // Update tab type on the existing canvas
        LaunchedEffect(currentTab) {
            com.boox.day.editor.DrawCanvas.memoModeChanged.value = (currentTab == EditorTab.Memos)
        }

        // Two-finger tap: Delete page confirmation dialog
        var showDeletePageDialog by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            DrawCanvas.requestDeletePageConfirmation.collect {
                showDeletePageDialog = true
            }
        }
        val isCanvasHostModalBlockingCanvas by remember {
            derivedStateOf {
                taskBeingMoved != null ||
                    taskCard != null ||
                    taskPendingDelete != null ||
                    taskBeingTyped != null ||
                    childTaskBeingCopied != null ||
                    dueDateTargetTask != null ||
                    recurringSeriesDeleteTask != null ||
                    showDeletePageDialog ||
                    (currentTab == EditorTab.Memos && memosIsLoading) ||
                    (currentTab == EditorTab.Tasks && tasksIsLoading)
            }
        }

        LaunchedEffect(isCanvasHostModalBlockingCanvas) {
            DrawCanvas.canvasHostModalVisible.value = isCanvasHostModalBlockingCanvas
        }

        DisposableEffect(Unit) {
            onDispose {
                DrawCanvas.canvasHostModalVisible.value = false
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Canvas always takes full width; we overlay the tasks column when needed
                EditorSurface(
                    state = activeState,
                    page = activePage,
                    history = activeHistory,
                    isMemosCanvas = isMemosCanvas
                )
                EditorGestureReceiver(controlTower = activeControlTower, editorState = activeState)
                SelectedBitmap(
                    context = context,
                    controlTower = activeControlTower,
                    // Only show "Convert to Tasks" on Memos tab - callback passed from MainScreen
                    onConvertSelectionToTasks = if (currentTab == EditorTab.Memos) onConvertSelectionToTasks else null
                )
                TextBoxOverlay(context = context, controlTower = activeControlTower)
                if (currentTab == EditorTab.Tasks) {
                  // Full screen (Oct 2026): rows keep the normal view's height, so the row overlays end
                  // at the last row and the space under it stays empty
                  val tasksRowsArea = if (isFullScreen) {
                      Modifier.height(with(LocalDensity.current) { tasksRowsHeight.toDp() })
                  } else {
                      Modifier.fillMaxHeight()
                  }
                  Box(modifier = Modifier.fillMaxWidth().then(tasksRowsArea)) {
                    // PERFORMANCE FIX: taskHasContent now reads from pre-computed map (O(1) lookup)
                    // The map is populated asynchronously by LaunchedEffect above
                    val rowCount = if (tasks.isEmpty()) 10 else tasks.size
                    val taskHasContent: (Task) -> Boolean = { task ->
                        // Instant lookup from pre-computed map; defaults to false if not yet calculated
                        taskContentMap[task.id] ?: false
                    }
                    val taskHasInteractiveContent: (Task) -> Boolean = { task ->
                        if (taskContentMap[task.id] == true) {
                            true
                        } else {
                            val hasMetadata = task.typedText?.isNotBlank() == true ||
                                task.recognizedText?.isNotBlank() == true ||
                                task.isCompleted ||
                                task.priority.isNotEmpty() ||
                                task.dueDay != null
                            if (hasMetadata) {
                                true
                            } else if (tasksRowsHeight > 0 && task.rowIndex in 0 until rowCount) {
                                val rowBounds = com.boox.day.editor.utils.calculateRowBounds(
                                    task.rowIndex, tasksRowsHeight.toFloat(), rowCount
                                )
                                val strokes = com.boox.day.data.PageDataManager.getStrokesInRectangle(
                                    rowBounds.toRect(), tasksPageId
                                )
                                com.boox.day.editor.utils.taskRowHasContent(task, strokes, rowBounds)
                            } else {
                                false
                            }
                        }
                    }

                    // ERASER ENHANCEMENT: Auto-clear metadata when task row becomes stroke-empty
                    // This LaunchedEffect observes eraser completions and checks affected rows
                    // Using rememberUpdatedState to avoid restarting collector on every tasks change
                    val currentTasksState = rememberUpdatedState(tasks)
                    LaunchedEffect(tasksPageId) {
                        DrawCanvas.eraserCompleted.collect { erasedRect ->
                            // Iterate through tasks to find rows affected by the erased area
                            for (task in currentTasksState.value) {
                                if (task.rowIndex !in 0 until rowCount) continue

                                // Calculate row bounds
                                val rowBounds = com.boox.day.editor.utils.calculateRowBounds(
                                    task.rowIndex, tasksRowsHeight.toFloat(), rowCount
                                )

                                // Check if erased area intersects with this row
                                val rowTop = rowBounds.topY.toInt()
                                val rowBottom = rowBounds.bottomY.toInt()
                                if (erasedRect.bottom < rowTop || erasedRect.top > rowBottom) continue

                                // Row was affected - keep typed/converted tasks intact
                                val hasTypedText = task.typedText?.isNotBlank() == true
                                val hasConvertedText = task.convertedText?.isNotBlank() == true
                                if (hasTypedText || hasConvertedText) continue

                                // Check if row still has strokes
                                val rect = android.graphics.Rect(
                                    0, rowBounds.topY.toInt(), Int.MAX_VALUE, rowBounds.bottomY.toInt()
                                )
                                val strokes = com.boox.day.data.PageDataManager.getStrokesInRectangle(rect, tasksPageId)
                                val hasStrokes = com.boox.day.editor.utils.hasIntentionalStrokeContent(strokes, rowBounds)

                                // T1 (Oct 2026): row ink erased (usually to rewrite it) -> drop only the
                                // recognized text; due date, priority, reminder and subtasks stay.
                                if (!hasStrokes) {
                                    scope.launch(Dispatchers.IO) {
                                        appRepository.taskRepository.clearRecognizedTextAfterErase(task)
                                    }
                                    onTaskInkErased(task)
                                }
                            }
                        }
                    }

                    // Cross out = done (plan T4 §13.6C, Oct 2026): a line drawn through a task's ink or
                    // typed text checks it; undoing (or erasing) that line unchecks it again. Runs after
                    // the stroke is saved (contentChanged) — never in the pen path.
                    val crossOutLines = remember(tasksPageId) { mutableMapOf<String, String>() }  // line id -> task id
                    val currentCanvasHeight = rememberUpdatedState(tasksRowsHeight)
                    val currentToggleCompleted = rememberUpdatedState(onToggleTaskCompleted)
                    val crossOutTextGeometry = with(LocalDensity.current) {
                        val columnEnd = TASKS_CONTROL_COLUMN_WIDTH.toPx()
                        TaskCrossOut.TextGeometry(
                            textStartPx = columnEnd + EInkTheme.TasksTab.typedTextPaddingStart.toPx(),
                            subtaskTextStartPx = columnEnd + EInkTheme.TasksTab.Subtask.paddingStart.toPx(),
                            textSizePx = EInkTheme.TasksTab.typedTextFontSize.toPx(),
                            subtaskTextSizePx = EInkTheme.TasksTab.Subtask.fontSize.toPx(),
                            maxRightPx = width - EInkTheme.TasksTab.typedTextPaddingEnd.toPx()
                        )
                    }
                    val currentTextGeometry = rememberUpdatedState(crossOutTextGeometry)
                    LaunchedEffect(tasksPageId) {
                        var knownIds = withContext(Dispatchers.Default) {
                            com.boox.day.data.PageDataManager.getStrokes(tasksPageId).mapTo(HashSet()) { it.id }
                        }
                        DrawCanvas.contentChanged.collect {
                            val pageStrokes = withContext(Dispatchers.Default) {
                                com.boox.day.data.PageDataManager.getStrokes(tasksPageId).toList()
                            }
                            val ids = pageStrokes.mapTo(HashSet()) { it.id }
                            val added = pageStrokes.filter { it.id !in knownIds }
                            val removed = knownIds.filter { it !in ids }
                            knownIds = ids
                            val canvasHeight = currentCanvasHeight.value.toFloat()
                            val pageTasks = currentTasksState.value
                            if (canvasHeight <= 0f || pageTasks.isEmpty()) return@collect

                            // Undo (or erase) of a cross-out line: uncheck its task again
                            for (id in removed) {
                                val taskId = crossOutLines[id] ?: continue
                                val task = pageTasks.firstOrNull { it.id == taskId && it.isCompleted } ?: continue
                                currentToggleCompleted.value(task)
                            }

                            // A pen stroke adds one stroke; page loads and moves add many
                            if (added.isEmpty() || added.size > 3) return@collect
                            val rows = pageTasks.size
                            val now = System.currentTimeMillis()
                            for (line in added) {
                                // Redo of a cross-out line: check its task again
                                val redoTaskId = crossOutLines[line.id]
                                if (redoTaskId != null) {
                                    val task = pageTasks.firstOrNull { it.id == redoTaskId && !it.isCompleted } ?: continue
                                    currentToggleCompleted.value(task)
                                    continue
                                }
                                if (!GlobalAppSettings.current.taskCrossOutEnabled) continue
                                if (now - line.createdAt.time > TaskCrossOut.FRESH_STROKE_MS) continue
                                val rowIndex = getDominantRowIndex(line, canvasHeight, rows)
                                if (rowIndex !in 0 until rows) continue
                                val task = pageTasks.firstOrNull { it.rowIndex == rowIndex } ?: continue
                                if (task.isCompleted) continue
                                val rowBounds = com.boox.day.editor.utils.calculateRowBounds(rowIndex, canvasHeight, rows)
                                // Earlier content: ink written before this line (rows without any are ignored)
                                val earlierInk = pageStrokes.filter {
                                    it.id != line.id &&
                                        it.createdAt.time <= line.createdAt.time - TaskCrossOut.EARLIER_CONTENT_MS &&
                                        getDominantRowIndex(it, canvasHeight, rows) == rowIndex
                                }
                                val typed = task.typedText?.takeIf { it.isNotBlank() }
                                    ?: task.convertedText?.takeIf { it.isNotBlank() }
                                val content = when {
                                    earlierInk.isNotEmpty() -> TaskCrossOut.inkExtent(earlierInk)
                                    typed != null -> TaskCrossOut.typedTextExtent(
                                        typed, task.parentTaskId != null, rowBounds.topY, rowBounds.bottomY,
                                        currentTextGeometry.value
                                    )
                                    else -> null
                                } ?: continue
                                if (TaskCrossOut.isCrossOut(line, content, minContentWidth = rowBounds.bottomY - rowBounds.topY)) {
                                    crossOutLines[line.id] = task.id
                                    currentToggleCompleted.value(task)
                                }
                            }
                        }
                    }

                    // Control column with checkbox, priority, and contextual chevron
                    // NOTE: forceUnfreeze() is called in the individual callbacks (onCheckboxToggle, etc.)
                    // instead of using pointerInteropFilter which blocks events from reaching children
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(TASKS_CONTROL_COLUMN_WIDTH)
                            .background(EInkTheme.TasksTab.controlColumnBackground)
                    ) {
                        TasksControlColumn(
                            tasks = tasks,
                            taskHasContent = taskHasContent,
                            onCheckboxToggle = { task ->
                                if (taskHasInteractiveContent(task)) {
                                    onToggleTaskCompleted(task)
                                }
                            },
                            onCheckboxLongPress = {
                                // Long-press checkbox: toggle all tasks on page
                                // If ANY task is unchecked → check all; if ALL checked → uncheck all
                                // T8 (Oct 2026): only rows with real content (text, subtask or ink);
                                // empty rows are never completed.
                                val pageTasks = tasks
                                val canvasHeight = tasksNormalHeight
                                scope.launch(Dispatchers.IO) {
                                    val contentTasks = pageTasks.filter {
                                        appRepository.taskRepository.taskHasRealContent(it, canvasHeight)
                                    }
                                    val newState = contentTasks.any { !it.isCompleted }
                                    contentTasks.forEach { task ->
                                        if (task.isCompleted != newState) {
                                            appRepository.taskRepository.setCompleted(task.id, newState)
                                        }
                                    }
                                    // Refresh UI to reflect changes
                                    onTasksNeedRefresh?.invoke(null)
                                }
                            },
                            onPriorityChange = { task, priority ->
                                if (taskHasInteractiveContent(task)) {
                                    onChangeTaskPriority(task, priority)
                                }
                            },
                            onDueDateTap = { task ->
                                if (taskHasInteractiveContent(task)) {
                                    openTaskScheduleDialog(task)
                                }
                            },
                            onChevronTap = { task ->
                                // Contextual: type if empty, move if has content
                                // IMPORTANT: Do fresh synchronous check here to avoid race condition
                                // Uses taskRowHasContent() which detects INTENTIONAL strokes (not overflow)
                                val hasContent = if (task.rowIndex in 0 until rowCount) {
                                    val rowBounds = com.boox.day.editor.utils.calculateRowBounds(
                                        task.rowIndex, tasksRowsHeight.toFloat(), rowCount
                                    )
                                    val strokes = com.boox.day.data.PageDataManager.getStrokesInRectangle(
                                        rowBounds.toRect(), tasksPageId
                                    )
                                    com.boox.day.editor.utils.taskRowHasContent(task, strokes, rowBounds)
                                } else {
                                    // Fallback for invalid row index - check typed text and metadata only
                                    task.typedText?.isNotBlank() == true ||
                                    task.isCompleted ||
                                    task.priority.isNotEmpty()
                                }

                                if (hasContent) {
                                    openTaskCard(task)  // T3: one card for everything about the task
                                } else {
                                    taskBeingTyped = task
                                }
                            },
                            onChevronLongPress = { task ->
                                // Long-press: directly open edit dialog (bypass move popup)
                                // Pre-fill with typedText or recognizedText (for handwriting conversion)
                                // Fetch fresh task from DB to get latest recognizedText
                                // (OCR may have updated it after the in-memory list was loaded)
                                scope.launch {
                                    val freshTask = withContext(Dispatchers.IO) {
                                        appRepository.taskRepository.getTaskById(task.id)
                                    } ?: task
                                    taskBeingTyped = freshTask
                                }
                            },
                            onLongPressRow = { task ->
                                // Clear the task slot
                                onTaskCleared(task)
                                scope.launch(Dispatchers.IO) {
                                    appRepository.taskRepository.clearTaskSlot(task, tasksNormalHeight)
                                    withContext(Dispatchers.Main) {
                                        snackManager.displaySnack(SnackConf(text = "Task cleared successfully", duration = SnackConf.DURATION_STANDARD))
                                    }
                                }
                            }
                        )
                    }
                    Divider(
                        color = EInkTheme.TasksTab.controlColumnBorderColor,
                        thickness = EInkTheme.TasksTab.controlColumnBorderWidth,
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(EInkTheme.TasksTab.controlColumnBorderWidth)
                            .align(Alignment.TopStart)
                            .offset(x = TASKS_CONTROL_COLUMN_WIDTH)
                    )
                    TasksLinesOverlay(
                        rowCount = if (tasks.isEmpty()) 10 else tasks.size,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = TASKS_CONTROL_COLUMN_WIDTH)
                    )
                    // Overlay for rendering typed text ONLY (no touch handling - drawing area is for stylus)
                    com.boox.day.editor.ui.TaskTypedTextOverlay(
                        tasks = tasks,
                        rowCount = if (tasks.isEmpty()) 10 else tasks.size,
                        canvasHeightPx = tasksRowsHeight,
                        rowsWithStrokes = asyncRowsWithStrokes,
                        recurringStateForTask = { task ->
                            task.templateId?.let { templateId ->
                                visibleRecurringTemplates[templateId]?.isActive ?: true
                            }
                        },
                        modifier = Modifier.padding(start = TASKS_CONTROL_COLUMN_WIDTH)
                    )
                    (taskBeingMoved ?: taskCard?.task ?: tasks.firstOrNull { it.id == highlightedTaskId })?.let { task ->
                        TaskRowHighlight(
                            rowIndex = task.rowIndex,
                            rowCount = if (tasks.isEmpty()) 10 else tasks.size,
                            modifier = Modifier.padding(start = TASKS_CONTROL_COLUMN_WIDTH)
                        )
                    }
                  }  // end Tasks rows area
                }
                // Hide stale content while the next date is loading.
                val showCanvasLoadingCover =
                    (currentTab == EditorTab.Memos && memosIsLoading) ||
                    (currentTab == EditorTab.Tasks && tasksIsLoading)
                if (showCanvasLoadingCover) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .pointerInteropFilter { true }
                    )
                }
            }
            // NOTE: Removed 45dp Tasks footer to prevent e-ink flicker on tab switch.

            // Show TypeTaskDialog when typing a new task
            taskBeingTyped?.let { taskToType ->
                com.boox.day.editor.ui.TypeTaskDialog(
                    rowIndex = taskToType.rowIndex,
                    initialText = taskToType.typedText ?: taskToType.recognizedText,
                    onSave = { text ->
                        // Update UI immediately (optimistic)
                        onTaskTypedTextChanged(taskToType, text)
                        // Persist to database and clear handwritten strokes
                        scope.launch(Dispatchers.IO) {
                            appRepository.taskRepository.setTypedText(taskToType.id, text)
                            // Clear handwritten strokes when typed text replaces them
                            val strokesCleared = appRepository.taskRepository.clearStrokesForRow(taskToType, tasksNormalHeight)
                            // Trigger canvas refresh to show cleared strokes
                            if (strokesCleared > 0) {
                                TasksCanvasRefresh.request()  // coalesced with the task op's own refresh (plan §13.4)
                            }
                        }
                        taskBeingTyped = null
                    },
                    onDismiss = { taskBeingTyped = null }
                )
            }

            // Show TaskDueDateDialog when setting/changing due date
            dueDateTargetTask?.let { taskForDueDate ->
                val activeRecurringTemplate = dueDateTargetTemplate
                com.boox.day.editor.ui.TaskDueDateDialog(
                    task = taskForDueDate,
                    recurringTemplate = activeRecurringTemplate,
                    recurringBlockedReason = dueDateTargetBlockedReason,
                    onDueDateSelected = { newDueDate ->
                        onDueDateChanged(taskForDueDate, newDueDate)
                    },
                    onRecurringSave = { recurringDraft ->
                        scope.launch(Dispatchers.IO) {
                            val result = appRepository.taskRepository.saveRecurringTemplateForTask(
                                taskForDueDate.id,
                                recurringDraft
                            )
                            withContext(Dispatchers.Main) {
                                if (result.isSuccess) {
                                    onTasksNeedRefresh?.invoke(null)
                                    snackManager.displaySnack(
                                        SnackConf(
                                            text = if (taskForDueDate.templateId != null) "Series updated" else "Series saved",
                                            duration = SnackConf.DURATION_STANDARD
                                        )
                                    )
                                } else {
                                    snackManager.displaySnack(
                                        SnackConf(
                                            text = result.blockedReason ?: "Could not save repeating task",
                                            duration = SnackConf.DURATION_ERROR
                                        )
                                    )
                                }
                            }
                        }
                    },
                    onRecurringToggleActive = activeRecurringTemplate?.let { template ->
                        {
                            dismissTaskScheduleDialog()
                            scope.launch(Dispatchers.IO) {
                                appRepository.taskRepository.setRecurringTemplateActive(
                                    template.id,
                                    !template.isActive
                                )
                                withContext(Dispatchers.Main) {
                                    onTasksNeedRefresh?.invoke(null)
                                    snackManager.displaySnack(
                                        SnackConf(
                                            text = if (template.isActive) "Series paused" else "Series resumed",
                                            duration = SnackConf.DURATION_STANDARD
                                        )
                                    )
                                }
                            }
                        }
                    },
                    onRecurringDelete = activeRecurringTemplate?.let {
                        {
                            dismissTaskScheduleDialog()
                            recurringSeriesDeleteTask = taskForDueDate
                        }
                    },
                    onDismiss = { dismissTaskScheduleDialog() },
                    onNavigateToDueDate = onDateChange?.let { dateChange ->
                        { dueDateString: String ->
                            // Navigate to the due date and switch to Tasks tab
                            dateChange(dueDateString)
                            // STALENESS FIX: Pass new date, pageId is null (will be loaded by MainScreen LaunchedEffect)
                            tabManager.switchToTasks(dueDateString, null)
                            dismissTaskScheduleDialog()
                        }
                    }
                )
            }

            recurringSeriesDeleteTask?.let { recurringTask ->
                com.boox.day.editor.ui.ConfirmationDialog(
                    title = "Delete Series",
                    message = "Stop future repeats and keep current tasks as one-off items?",
                    confirmText = "Delete Series",
                    isDestructive = true,
                    onConfirm = {
                        val templateId = recurringTask.templateId
                        recurringSeriesDeleteTask = null
                        if (templateId != null) {
                            scope.launch(Dispatchers.IO) {
                                appRepository.taskRepository.deleteRecurringSeries(templateId)
                                withContext(Dispatchers.Main) {
                                    onTasksNeedRefresh?.invoke(null)
                                    snackManager.displaySnack(
                                        SnackConf(
                                            text = "Series deleted",
                                            duration = SnackConf.DURATION_STANDARD
                                        )
                                    )
                                }
                            }
                        }
                    },
                    onDismiss = { recurringSeriesDeleteTask = null }
                )
            }

            // Show MoveTaskDialog or ChildTaskDialog when a task is selected
            taskCard?.let { card ->
                val cardTask = card.task
                val cardTitle = cardTask.typedText?.takeIf { it.isNotBlank() }
                    ?: cardTask.convertedText?.takeIf { it.isNotBlank() }
                val cardRecognized = cardTask.recognizedText?.takeIf { it.isNotBlank() }
                val reminderDefault = GlobalAppSettings.current.let {
                    it.defaultTaskReminderHour * 60 + it.defaultTaskReminderMinute
                }
                com.boox.day.editor.ui.TaskCardPopup(
                    task = cardTask,
                    titleText = cardTitle,
                    recognizedText = cardRecognized,
                    repeatLabel = card.repeatLabel,
                    subtaskCount = card.subtaskCount,
                    subtasksDone = card.subtasksDone,
                    defaultReminderMinuteOfDay = reminderDefault,
                    onEditText = {
                        taskCard = null
                        taskBeingTyped = cardTask
                    },
                    onConvertToText = if (cardTitle == null && cardRecognized != null) {
                        {
                            // Same as saving the type dialog with the recognized text
                            val text = cardRecognized.take(EInkTheme.TasksTab.typedTextMaxLength)
                            taskCard = null
                            onTaskTypedTextChanged(cardTask, text)
                            scope.launch(Dispatchers.IO) {
                                appRepository.taskRepository.setTypedText(cardTask.id, text)
                                appRepository.taskRepository.clearStrokesForRow(cardTask, tasksNormalHeight)
                                TasksCanvasRefresh.request()
                            }
                        }
                    } else null,
                    onPriorityChange = { priority ->
                        onChangeTaskPriority(cardTask, priority)
                        taskCard = card.copy(task = cardTask.copy(priority = priority))
                    },
                    onEditDueAndRepeat = {
                        taskCard = null
                        openTaskScheduleDialog(cardTask)
                    },
                    onClearDue = {
                        onDueDateChanged(cardTask, null)
                        taskCard = card.copy(task = cardTask.copy(dueDay = null))
                    },
                    onReminderChange = { minuteOfDay ->
                        taskCard = card.copy(task = cardTask.copy(reminderMinuteOfDay = minuteOfDay))
                        scope.launch(Dispatchers.IO) {
                            appRepository.taskRepository.setReminderMinuteOfDay(cardTask.id, minuteOfDay)
                        }
                    },
                    onBreakDown = if (onBreakDownTask != null && cardTask.parentTaskId == null &&
                        (cardTitle != null || cardRecognized != null)
                    ) {
                        {
                            taskCard = null
                            // Full screen (Oct 2026): runs in the normal view, on the task as it is there
                            com.boox.day.ui.FullScreenState.exitThen {
                                scope.launch {
                                    val fresh = withContext(Dispatchers.IO) {
                                        appRepository.taskRepository.getTaskById(cardTask.id)
                                    } ?: return@launch
                                    onBreakDownTask(fresh)
                                }
                            }
                        }
                    } else null,
                    onDelete = {
                        taskCard = null
                        taskPendingDelete = cardTask
                    },
                    onMove = {
                        taskCard = null
                        // Full screen (Oct 2026): moving and copying run in the normal view, on the task
                        // as it is there (its row may differ from the full screen one)
                        com.boox.day.ui.FullScreenState.exitThen {
                            scope.launch {
                                val fresh = withContext(Dispatchers.IO) {
                                    appRepository.taskRepository.getTaskById(cardTask.id)
                                } ?: return@launch
                                if (fresh.parentTaskId != null) {
                                    childTaskBeingCopied = fresh  // subtasks move with their parent
                                } else {
                                    taskBeingMoved = fresh
                                }
                            }
                        }
                    },
                    onDismiss = { taskCard = null },
                    moveLabel = if (cardTask.parentTaskId != null) "Copy" else "Move"
                )
            }

            taskPendingDelete?.let { toDelete ->
                com.boox.day.editor.ui.ConfirmationDialog(
                    title = "Delete this task?",
                    message = "Its handwriting, text, due date and reminder are removed.",
                    confirmText = "Delete Task",
                    isDestructive = true,
                    onConfirm = {
                        taskPendingDelete = null
                        val dayDate = toDelete.dayDate
                        val pageIndex = toDelete.taskPageIndex
                        val pageId = toDelete.pageId
                        if (toDelete.parentTaskId != null) {
                            // Subtask: delete and move the following siblings up (as the subtask dialog does)
                            scope.launch(Dispatchers.IO) {
                                appRepository.taskRepository.deleteChildTaskAndCompact(toDelete, tasksNormalHeight)
                                TaskTextIndexer.getInstanceOrNull()?.indexNow(dayDate, pageIndex, pageId)
                                withContext(Dispatchers.Main) {
                                    onTasksNeedRefresh?.invoke(null)
                                    snackManager.displaySnack(SnackConf(text = "Subtask deleted", duration = SnackConf.DURATION_STANDARD))
                                }
                            }
                        } else {
                            // Task: clear its row (as the task management dialog does)
                            onTaskCleared(toDelete)
                            scope.launch(Dispatchers.IO) {
                                appRepository.taskRepository.clearTaskSlot(toDelete, tasksNormalHeight)
                                TaskTextIndexer.getInstanceOrNull()?.indexNow(dayDate, pageIndex, pageId)
                                withContext(Dispatchers.Main) {
                                    snackManager.displaySnack(SnackConf(text = "Task deleted", duration = SnackConf.DURATION_STANDARD))
                                }
                            }
                        }
                    },
                    onDismiss = { taskPendingDelete = null }
                )
            }

            taskBeingMoved?.let { taskToMove ->

                val isChildTask = taskToMove.parentTaskId != null
                // Task has editable text if it has typed text OR recognized handwriting text
                val hasEditableText = taskToMove.typedText?.isNotBlank() == true ||
                                      taskToMove.recognizedText?.isNotBlank() == true

                if (isChildTask) {
                    // Child tasks (subtasks) can be edited or deleted, but not moved individually
                    com.boox.day.editor.ui.ChildTaskDialog(
                        onDelete = {
                            // Delete child task and compact siblings upward to maintain group contiguity
                            val dayDate = taskToMove.dayDate
                            val pageIndex = taskToMove.taskPageIndex
                            val pageId = taskToMove.pageId
                            scope.launch(Dispatchers.IO) {
                                appRepository.taskRepository.deleteChildTaskAndCompact(taskToMove, tasksNormalHeight)
                                // Re-index task page to update/remove OCR entries
                                TaskTextIndexer.getInstanceOrNull()?.indexNow(dayDate, pageIndex, pageId)
                                withContext(Dispatchers.Main) {
                                    // Refresh task list to show compacted siblings (null = current page)
                                    onTasksNeedRefresh?.invoke(null)
                                    snackManager.displaySnack(SnackConf(text = "Subtask deleted", duration = SnackConf.DURATION_STANDARD))
                                }
                            }
                            taskBeingMoved = null
                        },
                        // Edit button for subtasks with text (typed or recognized handwriting)
                        onEdit = if (hasEditableText) {
                            {
                                taskBeingMoved = null
                                taskBeingTyped = taskToMove
                            }
                        } else null,
                        onCopy = {
                            taskBeingMoved = null
                            childTaskBeingCopied = taskToMove
                        },
                        onDismiss = { taskBeingMoved = null }
                    )
                } else {
                    // Parent tasks and standalone tasks can be moved
                    val sourcePageIndex = taskToMove.taskPageIndex
                    val sourceDate = taskToMove.dayDate
                    val sourcePageId = taskToMove.pageId

                    com.boox.day.editor.ui.MoveTaskDialog(
                        currentDate = selectedDate,
                        onDateSelected = { targetDate ->
                            // Use moveTaskWithStrokes to migrate both metadata AND strokes
                            scope.launch(Dispatchers.IO) {
                                val result = appRepository.taskRepository.moveTaskWithStrokes(
                                    task = taskToMove,
                                    targetDate = targetDate,
                                    targetPageIndex = 0,
                                    canvasHeight = tasksNormalHeight,
                                    context = context
                                )
                                if (result.success) {
                                    Log.i(TAG, "Task moved with ${result.migratedStrokeCount} strokes migrated")

                                    // Register undo operations for same-date moves
                                    result.undoOperations?.let { undoOps ->
                                        withContext(Dispatchers.Main) {
                                            tasksHistory.value?.addOperationsToHistory(undoOps)
                                            Log.i(TAG, "UNDO: Registered ${undoOps.size} operations for task move undo")
                                        }
                                    }

                                    // Check if source page is now empty (no content in any row)
                                    val sourceTasks = appRepository.taskRepository.getTasksForPage(sourceDate, sourcePageIndex)
                                    val sourcePageHasContent = sourceTasks.any { task ->
                                        // Use unified content check (typed text, metadata, AND intentional strokes)
                                        val rowBounds = com.boox.day.editor.utils.calculateRowBounds(
                                            task.rowIndex, tasksNormalHeight, 10
                                        )
                                        val strokes = com.boox.day.data.PageDataManager.getStrokesInRectangle(
                                            rowBounds.toRect(), sourcePageId
                                        )
                                        com.boox.day.editor.utils.taskRowHasContent(task, strokes, rowBounds)
                                    }

                                    withContext(Dispatchers.Main) {
                                        if (!sourcePageHasContent && sourcePageIndex > 0) {
                                            // Source page is empty and not page 0 - navigate to page 0
                                            Log.i(TAG, "Source page $sourcePageIndex is now empty, navigating to page 0")
                                            onTasksNeedRefresh?.invoke(0)
                                            snackManager.displaySnack(SnackConf(text = "Task moved, empty page cleaned up", duration = SnackConf.DURATION_STANDARD))
                                        } else {
                                            // Source page still has content or is page 0 - just refresh current page
                                            onTasksNeedRefresh?.invoke(null)
                                            snackManager.displaySnack(SnackConf(text = "Task moved", duration = SnackConf.DURATION_STANDARD))
                                        }
                                    }
                                } else {
                                    Log.e(TAG, "Task move failed: ${result.errorReason}")
                                    val errorMessage = when (result.errorReason) {
                                        MoveTaskError.TARGET_PAGE_FULL -> "Target page is full"
                                        MoveTaskError.SOURCE_PAGE_NOT_LOADED -> "Source page not loaded"
                                        else -> "Move failed"
                                    }
                                    withContext(Dispatchers.Main) {
                                        snackManager.displaySnack(SnackConf(text = errorMessage, duration = SnackConf.DURATION_ERROR))
                                    }
                                }
                            }
                            taskBeingMoved = null
                        },
                        onCopy = { targetDate ->
                            scope.launch(Dispatchers.IO) {
                                val result = appRepository.taskRepository.copySingleTask(
                                    task = taskToMove,
                                    targetDate = targetDate,
                                    canvasHeight = tasksNormalHeight,
                                    copyChildren = true
                                )
                                withContext(Dispatchers.Main) {
                                    if (result.isSuccess) {
                                        val copyResult = result.getOrNull()
                                        val count = copyResult?.copiedCount ?: 0
                                        val msg = buildString {
                                            append("Copied $count task(s)")
                                            copyResult?.let {
                                                if (it.dueDatesPreserved > 0 || it.dueDatesCleared > 0) {
                                                    append(" (${it.dueDatesPreserved} due dates kept, ${it.dueDatesCleared} cleared)")
                                                }
                                            }
                                        }
                                        snackManager.displaySnack(SnackConf(text = msg, duration = SnackConf.DURATION_STANDARD))
                                    } else {
                                        val errorMsg = result.exceptionOrNull()?.message ?: "Copy failed"
                                        snackManager.displaySnack(SnackConf(text = errorMsg, duration = SnackConf.DURATION_ERROR))
                                    }
                                }
                            }
                            taskBeingMoved = null
                        },
                        // Delete and Edit live on the task card now (plan T3) — this dialog
                        // keeps [Cancel] [Copy] [Move] (DIALOG_RULEBOOK: at most 4 buttons)
                        onDismiss = { taskBeingMoved = null }
                    )
                }
            }

            // Show date picker for child task copy (two-step flow: ChildTaskDialog → date picker)
            childTaskBeingCopied?.let { childTask ->
                com.boox.day.editor.ui.TaskDatePickerDialog(
                    title = "Copy Subtask",
                    currentDate = selectedDate,
                    actionButtonLabel = "Copy",
                    onDateSelected = { targetDate ->
                        scope.launch(Dispatchers.IO) {
                            val result = appRepository.taskRepository.copySingleTask(
                                task = childTask,
                                targetDate = targetDate,
                                canvasHeight = tasksNormalHeight,
                                copyChildren = false
                            )
                            withContext(Dispatchers.Main) {
                                if (result.isSuccess) {
                                    snackManager.displaySnack(
                                        SnackConf(text = "Subtask copied", duration = SnackConf.DURATION_STANDARD)
                                    )
                                } else {
                                    snackManager.displaySnack(
                                        SnackConf(text = "Copy failed", duration = SnackConf.DURATION_ERROR)
                                    )
                                }
                            }
                        }
                        childTaskBeingCopied = null
                    },
                    onDismiss = { childTaskBeingCopied = null }
                )
            }

            // The layout shift from conditional footer caused heavy refresh artifacts.
            // Task rows now use the full available height.
        }

        // Show delete page confirmation dialog (rendered AFTER Column for proper z-order)
        if (showDeletePageDialog) {
            val pageInfo = activeState.dayPageInfo
            DeletePageConfirmDialog(
                pageNumber = (pageInfo?.currentIndex ?: 0) + 1,
                totalPages = pageInfo?.totalPages ?: 1,
                onDismiss = { showDeletePageDialog = false },
                onConfirm = {
                    showDeletePageDialog = false
                    activeControlTower.deleteCurrentDayPage()
                }
            )
        }
    }
}

/**
 * Thumbnail preview overlay for a Boox Note.
 * Shows a preview (180x270dp) with pagination and close button.
 * Positioned on the left side of the Events grid.
 */
@Composable
private fun NotePreviewOverlay(
    noteId: String,
    noteTitle: String?,  // Optional title to display above thumbnail
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    onClose: () -> Unit,
    onOpenInApp: () -> Unit,
    booxNoteRepository: BooxNoteRepository,
    modifier: Modifier = Modifier
) {
    // Load note data
    var noteData by remember { mutableStateOf<com.boox.day.data.db.BooxNoteIndex?>(null) }
    var pageIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var thumbnailBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Load note info
    LaunchedEffect(noteId) {
        isLoading = true
        noteData = booxNoteRepository.getNoteById(noteId)
        noteData?.let { note ->
            // Parse page IDs from JSON
            try {
                val array = org.json.JSONArray(note.pageIdsJson)
                pageIds = (0 until array.length()).map { array.getString(it) }
            } catch (e: Exception) {
                pageIds = emptyList()
            }
        }
        isLoading = false
    }

    // Load thumbnail for current page
    LaunchedEffect(noteId, currentPage, pageIds) {
        if (pageIds.isNotEmpty() && currentPage < pageIds.size) {
            thumbnailBitmap = null
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val booxBitmap = com.boox.day.booxnotes.BooxNative.renderPageOnDevice(
                        com.boox.day.booxnotes.BooxNative.KSYNC_BASE_PATH,
                        noteId,
                        pageIds[currentPage]
                    )
                    if (booxBitmap != null && booxBitmap.isValid()) {
                        val bitmap = android.graphics.Bitmap.createBitmap(
                            booxBitmap.width,
                            booxBitmap.height,
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(booxBitmap.buffer))
                        // Scale to preview size (180x216 - reduced 20% height)
                        val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 180, 216, true)
                        bitmap.recycle()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            thumbnailBitmap = scaled
                        }
                    }
                } catch (e: Throwable) {
                    // Failed to load (catch Throwable for UnsatisfiedLinkError)
                }
            }
        }
    }

    // Overlay box - larger size for better visibility
    Box(
        modifier = modifier
            .width(200.dp)
            .background(Color.White, RoundedCornerShape(6.dp))
            .border(1.dp, Color.Black, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Close button row - larger X for easier tapping
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close preview",
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(onClick = onClose),
                    tint = Color.DarkGray
                )
            }

            // Note title (above thumbnail, truncated to fit)
            if (!noteTitle.isNullOrEmpty()) {
                Text(
                    text = noteTitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.DarkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Thumbnail - 180x216dp (reduced 20% height)
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(216.dp)
                    .border(1.dp, EInkTheme.Colors.dividerHairline),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.Black,
                        strokeWidth = 3.dp
                    )
                } else if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap!!.asImageBitmap(),
                        contentDescription = "Note preview",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "No preview",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }

            // Pagination (if multiple pages)
            if (pageIds.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous",
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(enabled = currentPage > 0) {
                                if (currentPage > 0) onPageChange(currentPage - 1)
                            },
                        tint = if (currentPage > 0) Color.Black else Color.LightGray
                    )
                    Text(
                        text = "${currentPage + 1}/${pageIds.size}",
                        fontSize = 14.sp,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next",
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(enabled = currentPage < pageIds.size - 1) {
                                if (currentPage < pageIds.size - 1) onPageChange(currentPage + 1)
                            },
                        tint = if (currentPage < pageIds.size - 1) Color.Black else Color.LightGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Open in Boox Notes button - larger
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black, RoundedCornerShape(4.dp))
                    .clickable(onClick = onOpenInApp)
                    .padding(vertical = 10.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Open in Boox Notes",
                    fontSize = 14.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Moves the body of a Row out of MainScreen's root lambda into a lambda class of its own, with no
 * behaviour of its own. That root lambda was within 140 bytes of the JVM limit of 64 KB per method
 * (compile error "Method too large"), so adding the calendar's day header needed room. Wrap more
 * big blocks like this if it happens again.
 */
@Composable
private fun RowScope.SplitOutOfMainScreen(content: @Composable RowScope.() -> Unit) {
    content()
}

/**
 * The calendar section above the toolbar (Oct 2026): left out in full screen (ui/FullScreenState), so
 * the toolbar and canvas take the whole screen. Like [SplitOutOfMainScreen], its lambda also moves this
 * big block out of MainScreen's root lambda.
 */
@Composable
private fun ColumnScope.CalendarSection(content: @Composable ColumnScope.() -> Unit) {
    if (!com.boox.day.ui.FullScreenState.isOn) content()
}

/**
 * Full screen (Oct 2026): when the canvas really changes size, the surface resizes the active tab's
 * page itself (DrawCanvas.surfaceChanged → PageView.syncToSurfaceSize). This resizes the hidden tab's
 * page the same way, so the size check that follows reuses it — with its undo history and tool state —
 * instead of recreating it. Differences of a pixel or two (startup rounding) are left to that check.
 */
/**
 * Full screen Tasks (Oct 2026, tasks/TaskPagination). Once full screen shows Tasks at its full size, the
 * day goes on pages of more rows of the same height, showing the page that holds the same tasks; when
 * full screen ends (FullScreenState.collapse) the day is back on pages of 10 first and that page is shown
 * again. Undo history holds the old row positions, so it starts afresh. A day change leaves full screen.
 * Also tells the drawing code (completed-row grey, eraser block) which rows are on screen.
 */
@Composable
private fun TasksFullScreenPages(
    isFullScreen: Boolean,
    isTasksTab: Boolean,
    selectedDate: String,
    fullScreenHeight: Int,  // 0 until the canvas has its full screen size
    topOffsetPx: Float,
    rowCount: Int,
    rowsHeight: Int,
    taskPageInfo: TaskPageInfo?,
    tasksHistory: History?,
    onTasksNeedRefresh: ((pageIndex: Int?) -> Unit)?
) {
    val pagination = com.boox.day.tasks.TaskPagination
    val fullScreen = com.boox.day.ui.FullScreenState
    SideEffect {
        pagination.screenRowCount = if (isFullScreen) rowCount else pagination.NORMAL_ROWS
        pagination.screenRowsHeightPx = if (isFullScreen) rowsHeight.toFloat() else 0f
    }
    val currentPageInfo = rememberUpdatedState(taskPageInfo)
    val currentHistory = rememberUpdatedState(tasksHistory)
    val currentRefresh = rememberUpdatedState(onTasksNeedRefresh)
    val currentDate = rememberUpdatedState(selectedDate)
    val currentRowCount = rememberUpdatedState(rowCount)

    LaunchedEffect(isFullScreen, isTasksTab, selectedDate, fullScreenHeight) {
        if (!isFullScreen || !isTasksTab || fullScreenHeight <= 0) return@LaunchedEffect
        val normalHeight = GlobalAppSettings.tasksCanvasHeight
        val rows = pagination.fullScreenRows(fullScreenHeight.toFloat(), normalHeight, topOffsetPx)
        if (rows <= pagination.NORMAL_ROWS || pagination.isReflowed(selectedDate)) return@LaunchedEffect
        val topSlot = (currentPageInfo.value?.currentIndex ?: 0) * pagination.NORMAL_ROWS
        val expanded = fullScreen.whileSwitching {
            // Still in full screen after any switch queued before this one
            fullScreen.isOn && pagination.expand(selectedDate, rows, normalHeight, topOffsetPx)
        }
        if (expanded) {
            currentHistory.value?.cleanHistory()
            currentRefresh.value?.invoke(topSlot / rows)
        }
    }

    DisposableEffect(Unit) {
        fullScreen.afterTasksCollapsed = { record ->
            currentHistory.value?.cleanHistory()
            if (record.date == currentDate.value) {
                val topSlot = (currentPageInfo.value?.currentIndex ?: 0) * record.rows
                currentRefresh.value?.invoke(topSlot / pagination.NORMAL_ROWS)
                // A tool may run right after this: wait until the page of 10 rows is shown
                kotlinx.coroutines.withTimeoutOrNull(2_000L) {
                    androidx.compose.runtime.snapshotFlow { currentRowCount.value }
                        .first { it == pagination.NORMAL_ROWS }
                }
            }
        }
        onDispose { fullScreen.afterTasksCollapsed = null }
    }

    var shownDate by remember { mutableStateOf(selectedDate) }
    LaunchedEffect(selectedDate) {
        if (selectedDate == shownDate) return@LaunchedEffect
        shownDate = selectedDate
        if (fullScreen.isOn) fullScreen.collapse()
    }
}

/** Ink ending this close under the canvas edge is a stroke written at the edge, not content below. */
private const val MORE_BELOW_MARGIN_PX = 12

/** Lowest point of a page's ink and images, in canvas pixels (0 when empty), from the loaded page. */
private fun pageContentBottom(pageId: String): Float {
    val strokesBottom = com.boox.day.data.PageDataManager.getStrokes(pageId).maxOfOrNull { it.bottom } ?: 0f
    val imagesBottom = com.boox.day.data.PageDataManager.getImages(pageId).maxOfOrNull { (it.y + it.height).toFloat() } ?: 0f
    return maxOf(strokesBottom, imagesBottom)
}

@Composable
private fun ResizeHiddenPageInPlace(page: PageView?, isActive: Boolean, surfaceSize: com.boox.day.editor.SurfaceSize?) {
    LaunchedEffect(surfaceSize) {
        val size = surfaceSize ?: return@LaunchedEffect
        if (isActive || page == null) return@LaunchedEffect
        if (abs(page.viewWidth - size.width) > 2 || abs(page.viewHeight - size.height) > 2) {
            page.syncToSurfaceSize(size.width, size.height)
        }
    }
}

/**
 * Day header of the calendar area: big selected date and the marks of that day (Today is in the top bar).
 */
@Composable
private fun SelectedDayHeader(
    selectedDate: String,
    datesWithBooxNotes: Set<String>,
    datesWithNotes: Set<String>,
    datesWithTasks: Set<String>,
    tabManager: EditorTabManager,
    memosPageId: String?,
    tasksPageId: String?
) {
    DayHeader(
        selectedDate = selectedDate,
        hasBooxNotes = datesWithBooxNotes.contains(selectedDate),
        hasMemo = datesWithNotes.contains(selectedDate),
        hasTasks = datesWithTasks.contains(selectedDate),
        // Same signal as the toolbar's Notes button: MainScreen opens the Boox Notes dialog for the selected day
        onBooxNotesClick = { DrawCanvas.viewBooxNotes.tryEmit(Unit) },
        onMemoClick = {
            DrawCanvas.toolbarInteraction.tryEmit(Unit)  // Close expanded events panel (like the toolbar tab)
            tabManager.switchToMemos(selectedDate, memosPageId)
        },
        onTasksClick = {
            DrawCanvas.toolbarInteraction.tryEmit(Unit)  // Close expanded events panel (like the toolbar tab)
            tabManager.switchToTasks(selectedDate, tasksPageId)
        }
    )
}

/**
 * Events grid - shows Google Calendar events for the selected date
 * Features: Pagination (4 events/page), duration, virtual meeting indicator
 * Supports expansion via onToggleExpanded callback
 */
@Composable
private fun EventsGrid(
    selectedDate: String,
    eventsViewModel: EventsViewModel,
    booxNoteRepository: BooxNoteRepository,
    booxNotesIntegration: BooxNotesIntegration,
    showHeader: Boolean = true,
    onToggleExpanded: (() -> Unit)? = null,
    onEventClick: (CalendarEvent) -> Unit,
    noteLinkRefreshTrigger: Int = 0,  // Trigger to refresh note links
    isSyncing: Boolean = false,  // Show syncing indicator when true
    selectedEventsTab: String = "events",  // "events" or "due" - controls which content to show
    focusEventId: String? = null,
    onFocusedEventShown: (() -> Unit)? = null,
    onDueTaskClick: ((Task) -> Unit)? = null,  // Navigate to task's original date when clicking due task
    modifier: Modifier = Modifier
) {
    // PERFORMANCE: Use shared StateFlow to avoid redundant Room subscriptions
    // Multiple consumers (badge, EventsGrid, ExpandedEventsList) now share one Flow
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    LaunchedEffect(selectedDate) {
        eventsViewModel.getSharedEventsFlow(selectedDate).collect { eventsList ->
            events = eventsList
        }
    }

    // Collect due tasks for the "Due" tab
    // Plan §13.4 (Oct 2026): one Flow per day (it was rebuilt on every recomposition)
    val dueFlowToday = java.time.LocalDate.now().toString()
    val dueTasksData by remember(selectedDate, dueFlowToday) {
        eventsViewModel.getDueTasksForDate(selectedDate)
    }.collectAsState(initial = DueTasksData.EMPTY)


    // NOTE: Events are already sorted by the ViewModel's createEventsFlow()
    // No need to sort again here - sortedEvents just references events directly
    val sortedEvents = events

    // PERFORMANCE: Query all Boox Notes data in a single snapshot (replaces 3 separate calls)
    var eventToNoteLinks by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var eventNoteCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var eventsWithAttention by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(selectedDate, noteLinkRefreshTrigger, events) {
        // CROSS-DAY FIX: Query by event IDs to find ALL linked notes,
        // regardless of when the note was created
        val eventIds = events.map { it.id }
        if (eventIds.isEmpty()) {
            eventToNoteLinks = emptyMap()
            eventNoteCounts = emptyMap()
            eventsWithAttention = emptySet()
            return@LaunchedEffect
        }

        booxNoteRepository.observeEventBadgeData(eventIds).collect { badgeData ->
            eventToNoteLinks = badgeData.eventToNoteLinks
            eventNoteCounts = badgeData.noteCounts
            eventsWithAttention = badgeData.eventsWithAttention
        }
    }

    // Pagination state (Events tab)
    val eventsPerPage = 4
    var currentPage by remember { mutableStateOf(0) }
    val totalPages = remember(sortedEvents) {
        if (sortedEvents.isEmpty()) 1 else (sortedEvents.size + eventsPerPage - 1) / eventsPerPage
    }

    // Pagination state (Due Tasks tab)
    val dueTasksPerPage = 5
    var dueTasksCurrentPage by remember { mutableStateOf(0) }

    // Combined list of due tasks with overdue flag: (Task, isOverdue)
    val allDueTasks = remember(dueTasksData) {
        dueTasksData.dueTodayTasks.map { it to false } + dueTasksData.overdueTasks.map { it to true }
    }

    val dueTasksTotalPages = remember(allDueTasks) {
        if (allDueTasks.isEmpty()) 1 else (allDueTasks.size + dueTasksPerPage - 1) / dueTasksPerPage
    }

    // Get due tasks for current page
    val pageDueTasks = remember(allDueTasks, dueTasksCurrentPage) {
        val startIndex = dueTasksCurrentPage * dueTasksPerPage
        val endIndex = minOf(startIndex + dueTasksPerPage, allDueTasks.size)
        if (startIndex < allDueTasks.size) {
            allDueTasks.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }

    // Determine which section headers to show on current page
    val showDueTodayHeader = remember(pageDueTasks, dueTasksCurrentPage) {
        // Show "Due Today" header if this is the first page or if first item on page is due today
        pageDueTasks.isNotEmpty() && !pageDueTasks.first().second &&
            (dueTasksCurrentPage == 0 || allDueTasks.getOrNull(dueTasksCurrentPage * dueTasksPerPage - 1)?.second == true)
    }

    val showOverdueHeader = remember(pageDueTasks, allDueTasks, dueTasksCurrentPage) {
        // Show "Overdue" header if this page has overdue tasks and either:
        // 1. First overdue task appears on this page (transition from due today to overdue)
        // 2. First item on page is overdue (page starts with overdue section)
        val hasOverdueOnPage = pageDueTasks.any { it.second }
        if (!hasOverdueOnPage) return@remember false

        val pageStartIndex = dueTasksCurrentPage * dueTasksPerPage
        val firstOverdueIndexOnPage = pageDueTasks.indexOfFirst { it.second }
        if (firstOverdueIndexOnPage == -1) return@remember false

        // Check if this is where overdue section begins (previous item was due today or doesn't exist)
        val absoluteFirstOverdueIndex = pageStartIndex + firstOverdueIndexOnPage
        absoluteFirstOverdueIndex == 0 || (absoluteFirstOverdueIndex > 0 && !allDueTasks[absoluteFirstOverdueIndex - 1].second)
    }

    // Counts for header display
    val dueTodayCount = dueTasksData.dueTodayTasks.size
    val overdueCount = dueTasksData.overdueTasks.size

    // Note preview overlay state
    var previewNoteId by remember { mutableStateOf<String?>(null) }
    var previewNoteTitle by remember { mutableStateOf<String?>(null) }  // Title to display in popup
    var previewPageIndex by remember { mutableStateOf(0) }
    var previewIconY by remember { mutableStateOf(0f) }  // Y position of clicked icon

    // Track Events grid X position for popup horizontal placement
    var eventsGridX by remember { mutableStateOf(0f) }

    // Mini popup for multiple notes linked to an event
    var notesPopupEventId by remember { mutableStateOf<String?>(null) }
    var notesPopupEventTitle by remember { mutableStateOf<String?>(null) }
    var notesPopupNotes by remember { mutableStateOf<List<BooxNoteIndex>>(emptyList()) }
    var notesPopupY by remember { mutableStateOf(0f) }  // Y position for anchoring

    // Reset to first page when date changes
    LaunchedEffect(selectedDate) {
        currentPage = 0
        dueTasksCurrentPage = 0  // Also reset due tasks pagination
        previewNoteId = null  // Close preview when date changes
        previewNoteTitle = null
        notesPopupEventId = null  // Close notes popup when date changes
    }

    LaunchedEffect(sortedEvents.size) {
        if (currentPage >= totalPages) {
            currentPage = maxOf(0, totalPages - 1)
        }
    }

    LaunchedEffect(focusEventId, sortedEvents) {
        val targetEventId = focusEventId ?: return@LaunchedEffect
        val targetIndex = sortedEvents.indexOfFirst { it.id == targetEventId }
        if (targetIndex >= 0) {
            currentPage = targetIndex / eventsPerPage
            onFocusedEventShown?.invoke()
        }
    }

    // Reset due tasks page when task count changes (e.g., task completed/deleted)
    // Also clamp to last valid page if tasks are removed
    LaunchedEffect(dueTasksData.totalCount) {
        if (dueTasksCurrentPage >= dueTasksTotalPages) {
            dueTasksCurrentPage = maxOf(0, dueTasksTotalPages - 1)
        }
    }

    // Load note title when preview note changes
    LaunchedEffect(previewNoteId) {
        previewNoteId?.let { noteId ->
            val note = booxNoteRepository.getNoteById(noteId)
            previewNoteTitle = note?.title
        }
    }

    // Load notes when the notes popup event changes
    LaunchedEffect(notesPopupEventId) {
        notesPopupEventId?.let { eventId ->
            // Get event title
            val event = events.find { it.id == eventId }
            notesPopupEventTitle = event?.title
            // Get all notes linked to this event
            val notes = booxNoteRepository.getNotesLinkedToEvent(eventId)
            notesPopupNotes = notes
        }
    }

    // Get events for current page
    val pageEvents = remember(sortedEvents, currentPage) {
        val startIndex = currentPage * eventsPerPage
        val endIndex = minOf(startIndex + eventsPerPage, sortedEvents.size)
        if (startIndex < sortedEvents.size) {
            sortedEvents.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }

    Box(
        modifier = modifier
            .background(EInkTheme.EventsPanel.rowBackground)
            .onGloballyPositioned { coordinates ->
                // Track the X position of the events grid for popup placement
                eventsGridX = coordinates.positionInWindow().x
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 6.dp, top = EInkTheme.EventsPanel.rowPadding, bottom = 4.dp)  // Match expanded state padding
        ) {
            // Header - only show if requested
            if (showHeader) {
                Text(
                    text = "Events",
                    fontSize = EInkTheme.EventsPanel.headerFontSize,
                    fontWeight = FontWeight.SemiBold,  // Match expanded state
                    color = EInkTheme.EventsPanel.headerColor,
                    modifier = Modifier.padding(bottom = EInkTheme.EventsPanel.rowPadding)
                )
                Divider(color = EInkTheme.EventsPanel.rowDividerColor, thickness = EInkTheme.EventsPanel.rowDividerThickness)
            }

            // Content area - switches between Events and Due tabs
            when (selectedEventsTab) {
                "events" -> {
                    // Events list
                    if (sortedEvents.isEmpty()) {
                        // No events or syncing placeholder
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isSyncing) "Syncing..." else "No events",
                                fontSize = EInkTheme.EventsPanel.emptyStateTextSize,
                                color = EInkTheme.EventsPanel.emptyStateColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Capture current time once for all events (avoid repeated system calls)
                        val currentTime = System.currentTimeMillis()

                        // Events for current page
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            pageEvents.forEach { event ->
                                val linkedNoteId = eventToNoteLinks[event.id]
                                // Event is past if its end time has passed (use startTime if endTime is null)
                                val eventEndTime = event.endTime ?: event.startTime
                                val isPast = eventEndTime < currentTime
                                // NEW: Note badge info
                                val noteCount = eventNoteCounts[event.id] ?: 0
                                val hasAttention = eventsWithAttention.contains(event.id)
                                EventItem(
                                    event = event,
                                    selectedDate = selectedDate,
                                    linkedNoteId = linkedNoteId,
                                    onNoteClick = linkedNoteId?.let { noteId ->
                                        { yPos: Float ->
                                            if (noteCount > 1) {
                                                // Multiple notes: show mini popup to choose
                                                notesPopupEventId = event.id
                                                notesPopupY = yPos
                                            } else {
                                                // Single note: open directly in Boox Notes app
                                                booxNotesIntegration.openNote(noteId)
                                            }
                                        }
                                    },
                                    isPast = isPast,
                                    noteCount = noteCount,
                                    hasAttention = hasAttention,
                                    onClick = { onEventClick(event) }
                                )
                            }
                        }
                    }
                }
                "due" -> {
                    // Due tasks list (paginated)
                    if (dueTasksData.isEmpty) {
                        // Empty state
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = EInkTheme.Colors.gray
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "No tasks due",
                                    fontSize = EInkTheme.Typography.bodyMedium,
                                    color = EInkTheme.Colors.gray
                                )
                            }
                        }
                    } else {
                        // Paginated due tasks content
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Track if we've shown the overdue header on this page
                            var overdueHeaderShown = false

                            pageDueTasks.forEachIndexed { index, (task, isOverdue) ->
                                // Show "Due Today" header at the start of that section
                                if (index == 0 && showDueTodayHeader && !isOverdue) {
                                    DueTaskSectionHeader(title = "Due Today", count = dueTodayCount)
                                }

                                // Show "Overdue" header at the transition point
                                if (isOverdue && !overdueHeaderShown && showOverdueHeader) {
                                    if (index > 0 || !showDueTodayHeader) {
                                        // Add spacing before overdue section if not first item
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    DueTaskSectionHeader(title = "Overdue", count = overdueCount)
                                    overdueHeaderShown = true
                                }

                                DueTaskRow(
                                    task = task,
                                    isOverdue = isOverdue,
                                    onClick = { onDueTaskClick?.invoke(task) }
                                )
                            }
                        }
                    }
                }
            }

        // Footer row with event count, pagination, and expand icon (show when there are events on Events tab)
        // Note: Filter icons are in the header, not footer
        if (selectedEventsTab == "events" && sortedEvents.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: event count
                Text(
                    text = "${sortedEvents.size} event${if (sortedEvents.size > 1) "s" else ""}",
                    fontSize = EInkTheme.EventsPanel.timeFontSize,
                    color = EInkTheme.EventsPanel.timeColor,
                    modifier = Modifier.padding(start = 4.dp)
                )

                // Center: pagination controls (only when multiple pages)
                if (totalPages > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous page button
                        IconButton(
                            onClick = { if (currentPage > 0) currentPage-- },
                            enabled = currentPage > 0,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous page",
                                tint = if (currentPage > 0) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Page indicator
                        Text(
                            text = "${currentPage + 1} of $totalPages",
                            fontSize = 14.sp,
                            color = EInkTheme.EventsPanel.timeColor,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )

                        // Next page button
                        IconButton(
                            onClick = { if (currentPage < totalPages - 1) currentPage++ },
                            enabled = currentPage < totalPages - 1,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next page",
                                tint = if (currentPage < totalPages - 1) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                } else {
                    // Spacer to maintain layout when no pagination
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Right side: expand icon (only if callback provided)
                if (onToggleExpanded != null) {
                    IconButton(
                        onClick = onToggleExpanded,
                        modifier = Modifier.size(EInkTheme.EventsPanel.emptyStateIconSize)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.UnfoldMore,
                            contentDescription = "Expand events list",
                            tint = EInkTheme.EventsPanel.timeColor,
                            modifier = Modifier.size(EInkTheme.Global.iconSize)
                        )
                    }
                }
            }
        }

        // Footer row with task count, pagination, and expand icon (show when there are due tasks on Due tab)
        if (selectedEventsTab == "due" && allDueTasks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: task count
                Text(
                    text = "${allDueTasks.size} task${if (allDueTasks.size > 1) "s" else ""}",
                    fontSize = EInkTheme.EventsPanel.timeFontSize,
                    color = EInkTheme.EventsPanel.timeColor,
                    modifier = Modifier.padding(start = 4.dp)
                )

                // Center: pagination controls (only when multiple pages)
                if (dueTasksTotalPages > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous page button
                        IconButton(
                            onClick = { if (dueTasksCurrentPage > 0) dueTasksCurrentPage-- },
                            enabled = dueTasksCurrentPage > 0,
                            modifier = Modifier.size(EInkTheme.Global.iconSize)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous page",
                                tint = if (dueTasksCurrentPage > 0) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                                modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
                            )
                        }

                        // Page indicator
                        Text(
                            text = "${dueTasksCurrentPage + 1} of $dueTasksTotalPages",
                            fontSize = EInkTheme.EventsPanel.timeFontSize,
                            color = EInkTheme.EventsPanel.timeColor,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )

                        // Next page button
                        IconButton(
                            onClick = { if (dueTasksCurrentPage < dueTasksTotalPages - 1) dueTasksCurrentPage++ },
                            enabled = dueTasksCurrentPage < dueTasksTotalPages - 1,
                            modifier = Modifier.size(EInkTheme.Global.iconSize)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next page",
                                tint = if (dueTasksCurrentPage < dueTasksTotalPages - 1) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                                modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
                            )
                        }
                    }
                } else {
                    // Spacer to maintain layout when no pagination
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Right side: expand icon (only if callback provided)
                if (onToggleExpanded != null) {
                    IconButton(
                        onClick = onToggleExpanded,
                        modifier = Modifier.size(EInkTheme.EventsPanel.emptyStateIconSize)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.UnfoldMore,
                            contentDescription = "Expand tasks list",
                            tint = EInkTheme.EventsPanel.timeColor,
                            modifier = Modifier.size(EInkTheme.Global.iconSize)
                        )
                    }
                }
            }
        }
        }  // End Column

    }  // End Box

    // Mini popup for selecting from multiple notes linked to an event
    if (notesPopupEventId != null && notesPopupNotes.isNotEmpty()) {
        val density = LocalDensity.current
        val popupOffsetX = -50
        val popupOffsetY = (notesPopupY - with(density) { 30.dp.toPx() }).toInt().coerceAtLeast(50)

        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(popupOffsetX, popupOffsetY),
            onDismissRequest = { notesPopupEventId = null },
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            EventNotesPopup(
                eventTitle = notesPopupEventTitle ?: "Notes",
                notes = notesPopupNotes,
                onNoteClick = { note ->
                    booxNotesIntegration.openNote(note.uniqueId)
                    notesPopupEventId = null
                },
                onClose = { notesPopupEventId = null }
            )
        }
    }

    // Note thumbnail preview overlay - use Popup for proper z-ordering and overflow
    // Position: match expanded mode positioning (fixed offset to the left)
    if (previewNoteId != null) {
        val density = LocalDensity.current
        // Use same positioning as expanded mode for consistency
        val popupOffsetX = -100
        val popupOffsetY = (previewIconY - with(density) { 100.dp.toPx() }).toInt().coerceAtLeast(50)

        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(popupOffsetX, popupOffsetY),
            onDismissRequest = { previewNoteId = null },
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            NotePreviewOverlay(
                noteId = previewNoteId!!,
                noteTitle = previewNoteTitle,
                currentPage = previewPageIndex,
                onPageChange = { previewPageIndex = it },
                onClose = { previewNoteId = null },
                onOpenInApp = {
                    booxNotesIntegration.openNote(previewNoteId!!)
                    previewNoteId = null
                },
                booxNoteRepository = booxNoteRepository,
                modifier = Modifier
            )
        }
    }
}

/**
 * Mini popup for selecting from multiple notes linked to an event.
 * Simple, lightweight - tap a note row to open it directly in Boox Notes app.
 */
@Composable
private fun EventNotesPopup(
    eventTitle: String,
    notes: List<BooxNoteIndex>,
    onNoteClick: (BooxNoteIndex) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .background(EInkTheme.Colors.white, RoundedCornerShape(4.dp))
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.black, RoundedCornerShape(4.dp))
    ) {
        // Header with event title and close button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(EInkTheme.Colors.black)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = eventTitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = EInkTheme.Colors.white,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = EInkTheme.Colors.white,
                modifier = Modifier
                    .size(18.dp)
                    .padding(start = 8.dp)
                    .noRippleClickable { onClose() }
            )
        }

        // Notes list
        Column(
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            notes.forEach { note ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noRippleClickable { onNoteClick(note) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = note.title,
                        fontSize = 13.sp,
                        color = EInkTheme.Colors.black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // Page count badge
                    Text(
                        text = "${note.pageCount}p",
                        fontSize = 11.sp,
                        color = EInkTheme.Colors.gray,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                if (note != notes.last()) {
                    Divider(
                        color = EInkTheme.Colors.lightGray,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Expanded events list - shows all events for the day in a collapsible panel
 * Appears when user taps the expand icon in EventsGrid
 * Footer contains pin icon (left) and collapse icon (right)
 */
@Composable
private fun ExpandedEventsList(
    selectedDate: String,
    eventsViewModel: EventsViewModel,
    booxNoteRepository: BooxNoteRepository,
    booxNotesIntegration: BooxNotesIntegration,
    onEventClick: (CalendarEvent) -> Unit,
    onClose: () -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    noteLinkRefreshTrigger: Int = 0,
    selectedEventsTab: String,
    onTabSelected: (String) -> Unit,
    onDueTaskClick: ((Task) -> Unit)? = null,  // Navigate to task's original date when clicking due task
    // Source filter parameters
    sourceFilter: CalendarSourceFilter = CalendarSourceFilter(),
    onToggleSource: (CalendarSource) -> Boolean = { true },
    hasGoogleAccount: Boolean = false,
    hasOutlookAccount: Boolean = false,
    // Compact popup (plan Phase 1.5): pinning has no meaning there
    showPinButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    // PERFORMANCE: Use shared StateFlow to avoid redundant Room subscriptions
    // Same Flow instance as EventsGrid - no duplicate Room queries
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    LaunchedEffect(selectedDate) {
        eventsViewModel.getSharedEventsFlow(selectedDate).collect { eventsList ->
            events = eventsList
        }
    }

    // Collect due tasks for the "Due" tab
    // Plan §13.4 (Oct 2026): one Flow per day (it was rebuilt on every recomposition)
    val dueFlowToday = java.time.LocalDate.now().toString()
    val dueTasksData by remember(selectedDate, dueFlowToday) {
        eventsViewModel.getDueTasksForDate(selectedDate)
    }.collectAsState(initial = DueTasksData.EMPTY)


    // NOTE: Events are already sorted by the ViewModel's createEventsFlow()
    // No need to sort again here - sortedEvents just references events directly
    val sortedEvents = events

    // PERFORMANCE: Query all Boox Notes data in a single snapshot (replaces 3 separate calls)
    var eventToNoteLinks by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var eventNoteCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var eventsWithAttention by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(selectedDate, noteLinkRefreshTrigger, events) {
        // CROSS-DAY FIX: Query by event IDs to find ALL linked notes,
        // regardless of when the note was created
        val eventIds = events.map { it.id }
        if (eventIds.isEmpty()) {
            eventToNoteLinks = emptyMap()
            eventNoteCounts = emptyMap()
            eventsWithAttention = emptySet()
            return@LaunchedEffect
        }

        booxNoteRepository.observeEventBadgeData(eventIds).collect { badgeData ->
            eventToNoteLinks = badgeData.eventToNoteLinks
            eventNoteCounts = badgeData.noteCounts
            eventsWithAttention = badgeData.eventsWithAttention
        }
    }

    // Note preview overlay state
    var previewNoteId by remember { mutableStateOf<String?>(null) }
    var previewNoteTitle by remember { mutableStateOf<String?>(null) }  // Title to display in popup
    var previewPageIndex by remember { mutableStateOf(0) }
    var previewIconY by remember { mutableStateOf(0f) }  // Y position of clicked icon

    // Mini popup for multiple notes linked to an event
    var notesPopupEventId by remember { mutableStateOf<String?>(null) }
    var notesPopupEventTitle by remember { mutableStateOf<String?>(null) }
    var notesPopupNotes by remember { mutableStateOf<List<BooxNoteIndex>>(emptyList()) }
    var notesPopupY by remember { mutableStateOf(0f) }

    // Reset preview and popup when date changes
    LaunchedEffect(selectedDate) {
        previewNoteId = null
        previewNoteTitle = null
        notesPopupEventId = null
    }

    // Load notes when the notes popup event changes
    LaunchedEffect(notesPopupEventId) {
        notesPopupEventId?.let { eventId ->
            val event = events.find { it.id == eventId }
            notesPopupEventTitle = event?.title
            val notes = booxNoteRepository.getNotesLinkedToEvent(eventId)
            notesPopupNotes = notes
        }
    }

    // Load note title when preview note changes
    LaunchedEffect(previewNoteId) {
        previewNoteId?.let { noteId ->
            val note = booxNoteRepository.getNoteById(noteId)
            previewNoteTitle = note?.title
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 7.dp, top = 8.dp, bottom = 4.dp)
        ) {
            // Header row with tabs and date/filter icons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Isolated tabs component - won't recompose when Events panel recomposes
                // CRITICAL: Uses same EventsPanelTabs as collapsed panel for consistency
                // Compact (the Go 6 Events/Due popup, Oct 2026): finger-size tabs and filter icons
                val fingerSizeHeader = LayoutProfiles.isCompact
                EventsPanelTabs(
                    selectedTab = selectedEventsTab,
                    onTabSelected = onTabSelected,
                    eventsCount = sortedEvents.size,
                    dueCount = dueTasksData.totalCount,
                    large = fingerSizeHeader
                )
                // Right side: Filter icons (when connected accounts) OR Date label (standalone mode)
                val showFilterIcons = (hasGoogleAccount || hasOutlookAccount) && selectedEventsTab == "events"
                if (showFilterIcons) {
                    // Source filter icons
                    CalendarSourceFilterIcons(
                        filter = sourceFilter,
                        onToggleSource = onToggleSource,
                        hasGoogleAccount = hasGoogleAccount,
                        hasOutlookAccount = hasOutlookAccount,
                        selectedDayOfMonth = selectedDate.split("-").getOrNull(2)?.toIntOrNull() ?: 1,
                        isCompact = false,
                        large = fingerSizeHeader
                    )
                } else if (!LayoutProfiles.isNarrowScreen) {
                    // Date label (standalone mode or Due tab); not on narrow screens (Go 6), where it
                    // doesn't fit beside the tabs and the day header shows the date (Oct 2026)
                    Text(
                        text = remember(selectedDate) {
                            try {
                                val parts = selectedDate.split("-")
                                val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                                "${months[parts[1].toInt() - 1]} ${parts[2].toInt()}, ${parts[0]}"
                            } catch (e: Exception) { selectedDate }
                        },
                        fontSize = EInkTheme.EventsPanel.timeFontSize,
                        color = Color.Black
                    )
                }
            }

            // Content area - switches between Events and Due tabs
            when (selectedEventsTab) {
                "events" -> {
                    // Events list or empty state
                    if (sortedEvents.isEmpty()) {
                        // No events placeholder
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No events",
                                fontSize = 15.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Capture current time once for all events (avoid repeated system calls)
                        val currentTime = System.currentTimeMillis()



                        // Scrollable list of all events
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(sortedEvents) { event ->
                                val linkedNoteId = eventToNoteLinks[event.id]
                                // Event is past if its end time has passed (use startTime if endTime is null)
                                val eventEndTime = event.endTime ?: event.startTime
                                val isPast = eventEndTime < currentTime
                                // NEW: Note badge info
                                val noteCount = eventNoteCounts[event.id] ?: 0
                                val hasAttention = eventsWithAttention.contains(event.id)
                                EventItem(
                                    event = event,
                                    selectedDate = selectedDate,
                                    linkedNoteId = linkedNoteId,
                                    onNoteClick = linkedNoteId?.let { noteId ->
                                        { yPos: Float ->
                                            if (noteCount > 1) {
                                                // Multiple notes: show mini popup to choose
                                                notesPopupEventId = event.id
                                                notesPopupY = yPos
                                            } else {
                                                // Single note: open directly in Boox Notes app
                                                booxNotesIntegration.openNote(noteId)
                                            }
                                        }
                                    },
                                    isPast = isPast,
                                    noteCount = noteCount,
                                    hasAttention = hasAttention,
                                    onClick = { onEventClick(event) }
                                )
                            }
                        }
                    }
                }
                "due" -> {
                    // Due tasks list - uses scrollable LazyColumn for expanded view
                    if (dueTasksData.isEmpty) {
                        // Empty state
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = EInkTheme.Colors.gray
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "No tasks due",
                                    fontSize = 15.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Due Today section
                            val dueTodayTotal = dueTasksData.dueTodayTasks.size
                            val overdueTotal = dueTasksData.overdueTasks.size
                            if (dueTodayTotal > 0) {
                                item {
                                    DueTaskSectionHeader(title = "Due Today", count = dueTodayTotal)
                                }
                                items(dueTasksData.dueTodayTasks) { task ->
                                    DueTaskRow(task = task, isOverdue = false, onClick = { onDueTaskClick?.invoke(task) })
                                }
                            }
                            // Overdue section
                            if (overdueTotal > 0) {
                                if (dueTodayTotal > 0) {
                                    item { Spacer(Modifier.height(8.dp)) }
                                }
                                item {
                                    DueTaskSectionHeader(title = "Overdue", count = overdueTotal)
                                }
                                items(dueTasksData.overdueTasks) { task ->
                                    DueTaskRow(task = task, isOverdue = true, onClick = { onDueTaskClick?.invoke(task) })
                                }
                            }
                        }
                    }
                }
            }

            // Footer with pin icon (left), event count (center), and collapse icon (right)
            Divider(color = EInkTheme.Colors.dividerHairline, thickness = 1.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pin icon on the left (bigger tap target)
                if (!showPinButton) Spacer(modifier = Modifier.size(52.dp)) else IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(
                            id = if (isPinned) R.drawable.keep_40px else R.drawable.keep_off_40px
                        ),
                        contentDescription = if (isPinned) "Unpin events list" else "Pin events list",
                        tint = if (isPinned) Color.Black else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Item count in the center (shows events or due tasks count based on tab)
                Text(
                    text = when (selectedEventsTab) {
                        "events" -> "${sortedEvents.size} event${if (sortedEvents.size != 1) "s" else ""}"
                        "due" -> "${dueTasksData.totalCount} due task${if (dueTasksData.totalCount != 1) "s" else ""}"
                        else -> ""
                    },
                    fontSize = 14.sp,
                    color = EInkTheme.EventsPanel.timeColor
                )

                // Collapse icon on the right (bigger tap target, away from edge)
                // Disabled when pinned - user must unpin first to collapse
                IconButton(
                    onClick = onClose,
                    enabled = !isPinned,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.UnfoldLess,
                        contentDescription = if (isPinned) "Unpin to collapse" else "Collapse events list",
                        tint = if (isPinned) Color.LightGray else Color.DarkGray,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }  // End Column

    }  // End Box

    // Mini popup for selecting from multiple notes linked to an event
    if (notesPopupEventId != null && notesPopupNotes.isNotEmpty()) {
        val density = LocalDensity.current
        val popupOffsetX = -50
        val popupOffsetY = (notesPopupY - with(density) { 30.dp.toPx() }).toInt().coerceAtLeast(50)

        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(popupOffsetX, popupOffsetY),
            onDismissRequest = { notesPopupEventId = null },
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            EventNotesPopup(
                eventTitle = notesPopupEventTitle ?: "Notes",
                notes = notesPopupNotes,
                onNoteClick = { note ->
                    booxNotesIntegration.openNote(note.uniqueId)
                    notesPopupEventId = null
                },
                onClose = { notesPopupEventId = null }
            )
        }
    }

    // Note thumbnail preview overlay - use Popup for proper z-ordering and overflow
    // Position: popup to the left of expanded list, Y positioned above the icon
    if (previewNoteId != null) {
        val density = LocalDensity.current
        // Negative X offset moves popup more to the left
        val popupOffsetX = -100
        val popupOffsetY = (previewIconY - with(density) { 100.dp.toPx() }).toInt().coerceAtLeast(50)

        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(popupOffsetX, popupOffsetY),
            onDismissRequest = { previewNoteId = null },
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            NotePreviewOverlay(
                noteId = previewNoteId!!,
                noteTitle = previewNoteTitle,
                currentPage = previewPageIndex,
                onPageChange = { previewPageIndex = it },
                onClose = { previewNoteId = null },
                onOpenInApp = {
                    booxNotesIntegration.openNote(previewNoteId!!)
                    previewNoteId = null
                },
                booxNoteRepository = booxNoteRepository,
                modifier = Modifier
            )
        }
    }
}

@Composable
private fun EventItem(
    event: CalendarEvent,
    selectedDate: String = "",  // Current date being viewed (for multi-day "Day X/N" badge)
    linkedNoteId: String? = null,
    onNoteClick: ((Float) -> Unit)? = null,  // Now passes Y position
    isPast: Boolean = false,  // Event has ended (grayed out)
    // NEW: Note badge info (Dec 2025)
    noteCount: Int = 0,                      // Number of notes linked to this event
    hasAttention: Boolean = false,           // Has notes needing attention
    // Sync status for hybrid mode
    showSyncBadge: Boolean = false,  // Whether to show sync status indicator
    syncEnabled: Boolean = false,    // Event sync preference
    syncStatus: SyncStatus? = null,  // Current sync status
    // Multi-provider indicator (Phase 6)
    showProviderBadge: Boolean = true,  // Show G/O badge for event source
    onClick: () -> Unit
) {
    val isVirtualMeeting = remember(event.location, event.description) {
        isVirtualMeeting(event.location, event.description)
    }

    // Multi-day badge: "Day X/N" (e.g., "Day 2/5") — only for events spanning multiple days
    val multiDayBadge = remember(event.dayId, event.endDayId, selectedDate) {
        val endDayId = event.endDayId.ifEmpty { event.dayId }
        if (endDayId <= event.dayId || selectedDate.isEmpty()) {
            null // Single-day event or no selected date
        } else {
            try {
                val start = java.time.LocalDate.parse(event.dayId)
                val end = java.time.LocalDate.parse(endDayId)
                val current = java.time.LocalDate.parse(selectedDate)
                val totalDays = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1
                val currentDay = java.time.temporal.ChronoUnit.DAYS.between(start, current) + 1
                "Day $currentDay/$totalDays"
            } catch (_: Exception) {
                null
            }
        }
    }

    val duration = remember(event.startTime, event.endTime, event.isAllDay) {
        formatDuration(event.startTime, event.endTime, event.isAllDay)
    }

    // Check if note/memo icons are the only icons showing (for positioning)
    val hasOtherIcons = isVirtualMeeting || event.isReadOnly || duration.isNotEmpty() || linkedNoteId != null || showSyncBadge || showProviderBadge

    // Track the Y position of this item for popup positioning
    var itemYPosition by remember { mutableStateOf(0f) }

    // Colors based on past status
    val timeColor = if (isPast) Color(0xFF999999) else Color.DarkGray
    val titleColor = if (isPast) Color(0xFF888888) else Color.Black
    val iconColor = if (isPast) Color(0xFF999999) else Color.DarkGray

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable { onClick() }
            .padding(vertical = 6.dp)
            .onGloballyPositioned { coordinates ->
                // Get the Y position in the window
                itemYPosition = coordinates.positionInWindow().y
            }
    ) {
        // Row 1: Time, Duration, and Icons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time
            Text(
                text = if (event.isAllDay) "All day" else formatEventTime(event.startTime),
                fontSize = EInkTheme.EventsPanel.eventItemTimeFontSize,
                fontWeight = FontWeight.Medium,
                color = timeColor
            )

            // Duration and icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                // Add extra padding when note icon is the only icon (move away from edge)
                modifier = if (linkedNoteId != null && !hasOtherIcons) {
                    Modifier.padding(end = 8.dp)
                } else {
                    Modifier
                }
            ) {
                // Duration badge
                if (duration.isNotEmpty()) {
                    Text(
                        text = duration,
                        fontSize = EInkTheme.EventsPanel.eventItemDurationFontSize,
                        color = EInkTheme.Colors.darkGray   // #3A3A3A
                    )
                }

                // Multi-day badge: "Day X/N"
                if (multiDayBadge != null) {
                    Text(
                        text = multiDayBadge,
                        fontSize = EInkTheme.EventsPanel.eventItemDurationFontSize,
                        fontWeight = FontWeight.Medium,
                        color = timeColor
                    )
                }

                // Linked Boox Note indicator - tap to show thumbnail preview
                // Enhanced (Dec 2025): Shows note count badge and attention indicator
                if (linkedNoteId != null && onNoteClick != null) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .clickable { onNoteClick(itemYPosition) },
                        contentAlignment = Alignment.Center
                    ) {
                        // Main badge - white background with black icon
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(Color.White, RoundedCornerShape(3.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (noteCount > 1) {
                                // Show count when multiple notes
                                Text(
                                    text = noteCount.toString(),
                                    fontSize = EInkTheme.EventsPanel.eventItemBadgeCountFontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            } else {
                                // Single note - show B+Link icon (already white, no tint needed)
                                Image(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.boox_note_linked),
                                    contentDescription = "Linked Boox Note - tap to preview",
                                    modifier = Modifier.size(18.dp)  // Slightly larger for detail visibility
                                )
                            }
                        }

                        // Attention indicator (small red dot overlay)
                        if (hasAttention) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                                    .background(Color(0xFFE57373), CircleShape)  // Soft red
                            )
                        }
                    }
                }

                // Virtual meeting indicator
                if (isVirtualMeeting) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Virtual meeting",
                        modifier = Modifier.size(15.5.dp),
                        tint = iconColor
                    )
                }

                // Read-only indicator
                if (event.isReadOnly) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Read only",
                        modifier = Modifier.size(13.dp),
                        tint = if (isPast) Color(0xFFAAAAAA) else Color.Gray
                    )
                }

                // Sync status badge (hybrid mode)
                if (showSyncBadge && syncStatus != null) {
                    Icon(
                        imageVector = when (syncStatus) {
                            SyncStatus.SYNCED -> Icons.Default.CloudDone
                            SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE -> Icons.Default.CloudUpload
                            SyncStatus.PENDING_DELETE -> Icons.Default.DeleteForever
                            SyncStatus.CONFLICT -> Icons.Default.Warning
                            SyncStatus.LOCAL_ONLY -> if (syncEnabled) Icons.Default.CloudOff else Icons.Default.PhoneAndroid
                        },
                        contentDescription = when (syncStatus) {
                            SyncStatus.SYNCED -> "Synced"
                            SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE -> "Pending sync"
                            SyncStatus.PENDING_DELETE -> "Pending delete"
                            SyncStatus.CONFLICT -> "Sync conflict"
                            SyncStatus.LOCAL_ONLY -> if (syncEnabled) "Sync disabled" else "Local only"
                        },
                        modifier = Modifier.size(12.dp),
                        tint = when (syncStatus) {
                            SyncStatus.SYNCED -> if (isPast) Color(0xFFAAAAAA) else Color.DarkGray
                            SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE -> if (isPast) Color(0xFFAAAAAA) else Color.DarkGray
                            SyncStatus.CONFLICT -> Color(0xFFB71C1C) // Red for conflicts
                            else -> if (isPast) Color(0xFFAAAAAA) else Color.Gray
                        }
                    )
                }

                // Provider source icon (Google Calendar or Outlook)
                if (showProviderBadge && event.hasAccountInfo) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(
                            if (event.isGoogleEvent) R.drawable.ic_google_calendar
                            else if (event.isOutlookEvent) R.drawable.ic_outlook
                            else R.drawable.ic_google_calendar // fallback
                        ),
                        contentDescription = if (event.isGoogleEvent) "Google Calendar" else "Outlook",
                        modifier = Modifier.size(16.dp),
                        tint = Color.Unspecified
                    )
                }
                // Lock icon removed from event rows - the grayed text/icons +
                // larger lock on filter button provides sufficient visual indication
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Row 2: Title (can wrap to 2 lines)
        Text(
            text = event.title,
            fontSize = EInkTheme.EventsPanel.eventItemTitleFontSize,
            fontWeight = FontWeight.SemiBold,
            color = titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Divider(
            color = EInkTheme.Colors.borderStrong,
            thickness = 0.5.dp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/**
 * Check if event is a virtual meeting based on location or description
 */
private fun isVirtualMeeting(location: String?, description: String?): Boolean {
    val virtualKeywords = listOf(
        "zoom", "teams", "meet.google", "webex", "skype",
        "gotomeeting", "whereby", "jitsi", "hangouts",
        "https://zoom", "https://teams", "https://meet"
    )
    val combined = "${location.orEmpty()} ${description.orEmpty()}".lowercase()
    return virtualKeywords.any { keyword -> combined.contains(keyword) }
}

/**
 * Format duration between start and end time
 */
private fun formatDuration(startMillis: Long, endMillis: Long?, isAllDay: Boolean): String {
    if (isAllDay || endMillis == null) return ""

    return try {
        val durationMinutes = (endMillis - startMillis) / (1000 * 60)
        when {
            durationMinutes < 60 -> "${durationMinutes}m"
            durationMinutes % 60 == 0L -> "${durationMinutes / 60}h"
            else -> "${durationMinutes / 60}h ${durationMinutes % 60}m"
        }
    } catch (e: Exception) {
        ""
    }
}

/**
 * Format event time from milliseconds to display string.
 * Respects user's time format preference (12-hour or 24-hour).
 */
private fun formatEventTime(timeMillis: Long): String {
    return com.boox.day.utils.DateTimeFormatUtils.formatTimeFromMillis(timeMillis)
}

// ========== DUE TASKS TAB HELPERS ==========

/**
 * Section header for due tasks list (e.g., "Due Today (3)" or "Overdue (2)")
 */
@Composable
private fun DueTaskSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (title == "Overdue") Icons.Default.Warning else Icons.Default.Schedule,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (title == "Overdue") EInkTheme.Colors.primary else EInkTheme.Colors.darkGray
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "$title ($count)",
            fontSize = EInkTheme.Typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (title == "Overdue") EInkTheme.Colors.primary else EInkTheme.Colors.darkGray
        )
    }
}

/**
 * Single row displaying a due task.
 * Tapping navigates to the task's original date.
 */
@Composable
private fun DueTaskRow(
    task: Task,
    isOverdue: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Task indicator
        Icon(
            imageVector = Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (isOverdue) EInkTheme.Colors.primary else EInkTheme.Colors.gray
        )

        Spacer(Modifier.width(8.dp))

        // Task info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.typedText?.take(40)
                    ?: task.recognizedText?.take(40)
                    ?: "Task on row ${task.rowIndex + 1}",
                fontSize = EInkTheme.Typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = EInkTheme.Colors.primary
            )
            Text(
                text = "From: ${task.dayDate}",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray
            )
        }

        // Navigate arrow
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Go to task",
            modifier = Modifier.size(20.dp),
            tint = EInkTheme.Colors.gray
        )
    }
}

/**
 * Isolated tab component to prevent recomposition propagation.
 * Uses stable parameters and remembered shapes to avoid flickering.
 *
 * CRITICAL: This composable is extracted to its own recomposition scope.
 * When the Events panel recomposes due to date change, Compose can skip
 * recomposing this component if its parameters haven't changed.
 */
@Composable
private fun EventsPanelTabs(
    selectedTab: String,           // "events" or "due"
    onTabSelected: (String) -> Unit,
    eventsCount: Int = 0,          // Optional: show count badge on Events tab
    dueCount: Int = 0,             // Optional: show count badge on Due tab
    modifier: Modifier = Modifier,
    large: Boolean = false         // Finger-size tabs (Go 6 Events/Due popup, Oct 2026)
) {
    // DEBUG: Log every recomposition to verify isolation is working
    Log.d("EventsPanelTabs", "RECOMPOSE: selectedTab=$selectedTab eventsCount=$eventsCount dueCount=$dueCount at ${System.currentTimeMillis()}")

    // Remember shape to avoid recreation on every recomposition
    val tabShape = remember {
        RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    }

    // Pre-compute selection states (stable Boolean values)
    val isEventsSelected = selectedTab == "events"
    val isDueSelected = selectedTab == "due"

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,  // Align at bottom so height difference shows at top
        modifier = modifier
    ) {
        // Events tab with stable key
        key("events") {
            EventsPanelTabButton(
                label = "Events",
                count = eventsCount,
                isSelected = isEventsSelected,
                tabShape = tabShape,
                onClick = { onTabSelected("events") },
                width = if (large) 124.dp else 87.dp,
                large = large
            )
        }
        // Due tab with stable key
        key("due") {
            EventsPanelTabButton(
                label = "Due",
                count = dueCount,
                isSelected = isDueSelected,
                tabShape = tabShape,
                onClick = { onTabSelected("due") },
                width = if (large) 104.dp else 77.dp,  // 10dp narrower to make space for calendar icons
                large = large
            )
        }
    }
}

/**
 * Single tab button with stable primitive parameters.
 * Isolated to enable Compose to skip recomposition when unchanged.
 * Height varies: selected is 4dp taller than unselected (32dp vs 28dp).
 */
@Composable
private fun EventsPanelTabButton(
    label: String,
    count: Int,
    isSelected: Boolean,
    tabShape: Shape,
    onClick: () -> Unit,
    width: Dp = 87.dp,  // Default width, can be overridden per-tab
    iconRes: Int? = null,  // Shown instead of the label (the label stays as its description)
    large: Boolean = false  // Finger-size: 48/44dp tall, text 2sp larger (Go 6 Events/Due popup)
) {
    val selectedHeight = if (large) 48.dp else 32.dp   // 4dp taller than unselected
    val unselectedHeight = if (large) 44.dp else 28.dp

    // Font sizes: label uses 14/13sp, count uses 13/12sp (slightly smaller)
    val baseLabelSize = if (isSelected) EInkTheme.EventsPanel.tabLabelFontSizeSelected else EInkTheme.EventsPanel.tabLabelFontSizeUnselected
    val baseCountSize = if (isSelected) EInkTheme.EventsPanel.tabCountFontSizeSelected else EInkTheme.EventsPanel.tabCountFontSizeUnselected
    val labelSize = if (large) (baseLabelSize.value + 2f).sp else baseLabelSize
    val countSize = if (large) (baseCountSize.value + 2f).sp else baseCountSize
    val textColor = if (isSelected) EInkTheme.Selection.foreground else EInkTheme.Colors.gray
    val fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal

    Box(
        modifier = Modifier
            // [width] is a minimum (Oct 2026): a longer label such as "Events (11)" at the Go 6's
            // larger font scale widens its tab instead of wrapping to a second line
            .widthIn(min = width)
            .height(if (isSelected) selectedHeight else unselectedHeight)
            .background(
                color = if (isSelected) EInkTheme.Selection.background else Color.Transparent,
                shape = tabShape
            )
            .then(
                if (!isSelected) Modifier.border(1.dp, EInkTheme.Colors.gray, tabShape)
                else Modifier
            )
            .noRippleClickable {
                DrawCanvas.forceUnfreeze()  // Unfreeze UI for immediate visual feedback
                onClick()
            }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (iconRes != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(iconRes),
                    contentDescription = label,
                    tint = textColor,
                    modifier = Modifier.size(18.dp)
                )
                if (count > 0) {
                    Text(text = " ($count)", fontSize = countSize, fontWeight = fontWeight, color = textColor)
                }
            }
        } else Text(
            text = buildAnnotatedString {
                // Label part (e.g., "Events" or "Due")
                withStyle(SpanStyle(fontSize = labelSize, fontWeight = fontWeight)) {
                    append(label)
                }
                // Count part (e.g., " (3)") - slightly smaller font
                if (count > 0) {
                    withStyle(SpanStyle(fontSize = countSize, fontWeight = fontWeight)) {
                        append(" ($count)")
                    }
                }
            },
            color = textColor,
            maxLines = 1,
            softWrap = false
        )
    }
}
