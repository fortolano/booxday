package com.boox.day.data.datastore

import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.Serializable


// Define the target page size (A4 in points: 595 x 842)
const val A4_WIDTH = 595
const val A4_HEIGHT = 842

// Toolbar button sizing (40dp compact for more toolbar space, still stylus-friendly)
const val BUTTON_SIZE = 40
const val ICON_SIZE = 28
const val ICON_PADDING = 6


object GlobalAppSettings {
    private val _current = mutableStateOf(AppSettings(version = 1))
    val current: AppSettings
        get() = _current.value

    fun update(settings: AppSettings) {
        _current.value = settings
    }

    // Session-level state (not persisted) - tracks if Boox Notes initial scan is complete.
    // NotesButton stays in setup/preparing flow until this becomes true.
    private val _isBooxNotesReady = mutableStateOf(false)
    val isBooxNotesReady: Boolean
        get() = _isBooxNotesReady.value

    fun setBooxNotesReady(ready: Boolean) {
        _isBooxNotesReady.value = ready
    }

    // Session-level state - tracks if Boox Notes scan is currently in progress
    // Used to provide feedback and prevent blocking UX during scan
    private val _isBooxNotesScanning = mutableStateOf(false)
    val isBooxNotesScanning: Boolean
        get() = _isBooxNotesScanning.value

    fun setBooxNotesScanning(scanning: Boolean) {
        _isBooxNotesScanning.value = scanning
    }

    // Pre-loaded page IDs from splash screen (session-level, not persisted)
    // Router pre-creates today's pages during splash and stores IDs here
    // MainScreen reads these directly to skip DB calls and mount canvas instantly
    private val _preloadedDate = mutableStateOf<String?>(null)
    private val _preloadedMemosPageId = mutableStateOf<String?>(null)
    private val _preloadedTasksPageId = mutableStateOf<String?>(null)

    val preloadedDate: String? get() = _preloadedDate.value
    val preloadedMemosPageId: String? get() = _preloadedMemosPageId.value
    val preloadedTasksPageId: String? get() = _preloadedTasksPageId.value

    fun setPreloadedPageIds(date: String, memosPageId: String, tasksPageId: String) {
        _preloadedDate.value = date
        _preloadedMemosPageId.value = memosPageId
        _preloadedTasksPageId.value = tasksPageId
    }

    fun clearPreloadedPageIds() {
        _preloadedDate.value = null
        _preloadedMemosPageId.value = null
        _preloadedTasksPageId.value = null
    }

    // Session-level state - measured Tasks canvas height in pixels.
    // Set by MainScreen when Tasks tab is displayed. Used by background OCR
    // (DigitalInkService, TaskTextExtractor) to compute correct row boundaries
    // matching the visual overlay. Not persisted — re-measured each session.
    private val _tasksCanvasHeight = mutableStateOf(0f)
    val tasksCanvasHeight: Float
        get() = _tasksCanvasHeight.value

    fun setTasksCanvasHeight(height: Float) {
        _tasksCanvasHeight.value = height
    }

    // Session-level state - measured Tasks top offset in pixels.
    // This is TASKS_TOOLBAR_OFFSET.toPx() (1.dp converted to px at actual density).
    // Used alongside tasksCanvasHeight by background OCR for correct row boundaries.
    private val _tasksTopOffsetPx = mutableStateOf(0f)
    val tasksTopOffsetPx: Float
        get() = _tasksTopOffsetPx.value

    fun setTasksTopOffsetPx(offset: Float) {
        _tasksTopOffsetPx.value = offset
    }
}


@Serializable
data class AppSettings(
    val version: Int,
    val monitorBgFiles: Boolean = false,
    val showWelcome: Boolean = true,
    val defaultNativeTemplate: String = "blank",
    val quickNavPages: List<String> = listOf(),
    val debugMode: Boolean = false,
    val neoTools: Boolean = false,
    val toolbarPosition: Position = Position.Top,
    val smoothScroll: Boolean = true,
    val monochromeMode: Boolean = false,
    val continuousZoom: Boolean = false,
    val visualizePdfPagination: Boolean = false,
    val paginatePdf: Boolean = true,
    val scribbleToEraseEnabled: Boolean = false,
    val simpleRendering: Boolean = false,
    val muPdfRendering: Boolean = true,

    val doubleTapAction: GestureAction? = defaultDoubleTapAction,
    val twoFingerTapAction: GestureAction? = defaultTwoFingerTapAction,
    val swipeLeftAction: GestureAction? = defaultSwipeLeftAction,
    val swipeRightAction: GestureAction? = defaultSwipeRightAction,
    val twoFingerSwipeLeftAction: GestureAction? = defaultTwoFingerSwipeLeftAction,
    val twoFingerSwipeRightAction: GestureAction? = defaultTwoFingerSwipeRightAction,
    val holdAction: GestureAction? = defaultHoldAction,
    val continuousStrokeSlider: Boolean = false,

    // General settings
    val firstDayOfWeek: FirstDayOfWeek = FirstDayOfWeek.Sunday,
    val dateFormat: DateFormat = DateFormat.MDY,
    val timeFormat: TimeFormat = TimeFormat.TwelveHour,
    val showWeekNumbers: Boolean = false,
    val startTab: StartTab = StartTab.Memos,
    // OPTION C: Remember last active tab (Memos/Tasks) for session persistence
    val lastActiveTab: StartTab = StartTab.Memos,

    // Sync mode settings (Standalone Mode feature)
    val syncMode: SyncMode = SyncMode.STANDALONE,     // Default to standalone (no Google required)
    val defaultSyncEnabled: Boolean = false,           // Default sync toggle for new events in HYBRID mode
    val showSyncBadges: Boolean = true,               // Show sync status icons on events
    val offlineChangesWarning: Boolean = true,        // Warn about pending sync changes

    // Cloud backup settings (independent from sync mode)
    val driveBackupEnabled: Boolean = false,          // Auto-backup to Google Drive
    val lastLocalBackupTime: Long = 0L,               // Last local device backup timestamp (epoch ms)

    // AI API settings (for users who want to use their own API keys for privacy)
    // Phase 3 (Oct 2026): memory only — stored in api_key_prefs, never in the settings JSON
    // (which is saved to the database, exported in backups and was logged)
    @kotlinx.serialization.Transient
    val userOpenRouterApiKey: String? = null,         // User's own OpenRouter API key (null = use app default)

    // Tips feature - show helpful tips periodically
    val tipsEnabled: Boolean = true,                  // Enabled by default for discoverability
    val tipInterval: TipInterval = TipInterval.EVERY_HOUR,  // Default: hourly tips

    // AI Features - allow users to disable all AI-powered features (voice input, OCR, summaries, etc.)
    val aiEnabled: Boolean = true,                    // Enabled by default

    /** The OpenRouter model the user picked for each AI job; empty = BooxDay's choice (Oct 2026) */
    val aiModels: AiModelChoices = AiModelChoices(),

    // Boox Notes Integration baseline flag (kept for compatibility).
    // Boox Notes is now always-on; permission still required for filesystem access.
    val booxNotesEnabled: Boolean = true,


    // ═══════════════════════════════════════════════════════════════════════════
    // Voice-to-Text Settings (TranscriptionService)
    // ═══════════════════════════════════════════════════════════════════════════

    // Voice input mode: AUTO uses SpeechRecognizer if available, GEMINI uses AI transcription
    val voiceTextMode: VoiceTextMode = VoiceTextMode.AUTO,


    // ═══════════════════════════════════════════════════════════════════════════
    // Notification Settings (Due Date & Event Reminders)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Master notifications toggle (kept for backwards compatibility, controls permission prompts) */
    @Deprecated("Use taskNotificationsEnabled and eventNotificationsEnabled instead")
    val notificationsEnabled: Boolean = false,

    /** Enable notifications for task due dates */
    val taskNotificationsEnabled: Boolean = false,

    /** Enable notifications for event reminders */
    val eventNotificationsEnabled: Boolean = false,

    /** Default hour for task due date reminders (0-23, default 9 AM) */
    val defaultTaskReminderHour: Int = 9,

    /** Default minute for task due date reminders (0-59, default 0) */
    val defaultTaskReminderMinute: Int = 0,

    /** Default reminder time for events in minutes before start (5, 15, 30, 60, 1440 = 1 day) */
    val defaultEventReminderMinutes: Int = 15,

    /** Tasks: a line drawn through a task's ink or text checks it (plan T4, Oct 2026) */
    val taskCrossOutEnabled: Boolean = true,

    /** Today's agenda as the BOOX sleep screen (Oct 2026, sleepscreen/SleepAgenda). Off until the user turns it on. */
    val sleepScreenAgendaEnabled: Boolean = false,

    /** A Markdown note for each day, written by itself (Oct 2026, export/DailyNotes). Off until turned on. */
    val dailyNotesEnabled: Boolean = false,

    /** Tasks: day (yyyy-MM-dd) the start-the-day review was last shown (plan T4, Oct 2026) */
    val lastStartDayReviewDay: String? = null,

    /**
     * Tasks: canvas height and top offset (px) the stored Tasks ink is laid out for; 0 = not
     * recorded yet. TasksInkLayout rescales the ink when the measured geometry differs (Oct 2026).
     */
    val tasksInkCanvasHeight: Float = 0f,
    val tasksInkTopOffset: Float = 0f,

    // ═══════════════════════════════════════════════════════════════════════════
    // Boox Notes OCR Indexing Settings (v19+)
    // ═══════════════════════════════════════════════════════════════════════════

    /** OCR settings for Boox Notes full-text search indexing */
    val ocrSettings: OcrSettings = OcrSettings(),

    // ═══════════════════════════════════════════════════════════════════════════
    // Handwriting Recognition Settings
    // ═══════════════════════════════════════════════════════════════════════════

    /** Active handwriting recognition language (BCP-47 tag, default: en-US) */
    val handwritingLanguage: String = "en-US",

    // ═══════════════════════════════════════════════════════════════════════════
    // First-Run Onboarding (Play Store Launch - Jan 2026)
    // ═══════════════════════════════════════════════════════════════════════════

    /** When the welcome popup was dismissed (epoch ms). 0 = never shown/dismissed */
    val onboardingDismissedAt: Long = 0L,

    /** Tracks which first-run setup items have been completed (for badge display) */
    val setupChecklist: SetupChecklist = SetupChecklist(),

    ) {
    companion object {
        // ⚠️ GESTURE CONFIGURATION (Dec 2025)
        // Most gestures disabled for palm rejection - palm touches during writing triggered
        // accidental actions. Two-finger gestures remain disabled. Single-finger swipes
        // re-enabled for page navigation (constrained to canvas area only).
        // See: CLAUDE.md "Critical Bug Fixes" and memory.md section 14
        val defaultDoubleTapAction get() = GestureAction.None
        // Two-finger tap: Paste from clipboard (quick paste action)
        val defaultTwoFingerTapAction get() = GestureAction.Paste
        // Single-finger swipes: Re-enabled for day page navigation (canvas area only)
        // Swipe left = next page (creates new if on last), Swipe right = previous page
        val defaultSwipeLeftAction get() = GestureAction.NextPage
        val defaultSwipeRightAction get() = GestureAction.PreviousPage
        // Two-finger swipes: Remain disabled (palm rejection)
        val defaultTwoFingerSwipeLeftAction get() = GestureAction.None
        val defaultTwoFingerSwipeRightAction get() = GestureAction.None
        val defaultHoldAction get() = GestureAction.Select
    }

    enum class GestureAction {
        None, Undo, Redo, PreviousPage, NextPage, ChangeTool, ToggleZen, Select, DeletePage, Paste
    }

    enum class Position {
        Top, Bottom, // Left,Right,
    }

    enum class FirstDayOfWeek {
        Sunday,  // 0 - Calendar.SUNDAY
        Monday   // 1 - Calendar.MONDAY
    }

    enum class DateFormat {
        MDY,  // MM/DD/YYYY (US)
        DMY   // DD/MM/YYYY (International)
    }

    enum class TimeFormat {
        TwelveHour,   // 12-hour with AM/PM
        TwentyFourHour // 24-hour
    }

    enum class StartTab {
        Memos,
        Tasks
    }

    /**
     * Sync mode for calendar events.
     * STANDALONE: No Google account, all events local-only
     * HYBRID: Google account configured, per-event sync choice
     */
    enum class SyncMode {
        STANDALONE,  // No Google account, all events local-only
        HYBRID       // Google account configured, per-event sync choice
    }

    /**
     * Interval for displaying tips.
     * Each value represents the time between tips in minutes.
     */
    enum class TipInterval(val minutes: Int, val displayName: String) {
        EVERY_15_MINUTES(15, "Every 15 minutes"),
        EVERY_30_MINUTES(30, "Every 30 minutes"),
        EVERY_HOUR(60, "Every hour"),
        EVERY_4_HOURS(240, "Every 4 hours"),
        ONCE_A_DAY(1440, "Once a day");

        val milliseconds: Long get() = minutes * 60 * 1000L
    }
}

/**
 * Voice-to-text mode for TranscriptionService.
 *
 * This determines how voice input is transcribed:
 * - AUTO: Use SpeechRecognizer if Google Search is installed, else prompt to configure
 * - SPEECH_RECOGNIZER: Force SpeechRecognizer only (fails if unavailable)
 * - GEMINI: Force Gemini AI transcription (metered: 3/day free, 20/day Pro)
 */
@Serializable
enum class VoiceTextMode {
    /** SpeechRecognizer if available, else prompt to configure. Default for most users. */
    AUTO,

    /** Force SpeechRecognizer only. Fails if Google Search app not installed. */
    SPEECH_RECOGNIZER,

    /** AI transcription on the user's OpenRouter key, only while the tablet has no speech recognition (voiceSource). */
    GEMINI
}

/**
 * Settings for Boox Notes OCR indexing.
 *
 * SIMPLIFIED: No user-configurable windows/folders/network - system is intelligent.
 * OCR uses Google Cloud Vision API exclusively (no ML Kit, no LLM fallback).
 */
@Serializable
data class OcrSettings(
    /** Master toggle for OCR indexing (default OFF) */
    val enabled: Boolean = false,

    /** Internal retry limit for failed pages */
    val maxRetries: Int = 3,

    /** Debug mode for development (shows extra logging) */
    val debugMode: Boolean = false,

    // ═══════════════════════════════════════════════════════════════════════════
    // Quota Tracking - Backfill Phase (one-time historical indexing)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Whether backfill gating is complete (menu unlock threshold) */
    val backfillCompleted: Boolean = false,

    /** When backfill was completed (epoch ms) */
    val backfillCompletedAt: Long = 0L,

    /** Whether backfill budget is fully consumed (controls budget phase) */
    val backfillBudgetCompleted: Boolean = false,

    /** Number of pages indexed during backfill (limit is server-configurable) */
    val backfillPagesIndexed: Int = 0,

    // ═══════════════════════════════════════════════════════════════════════════
    // Quota Tracking - Monthly Budget (ongoing indexing after backfill)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Number of NEW pages indexed this month - TOTAL across all sources (budget is server-configurable) */
    val monthlyPagesUsed: Int = 0,

    /** Monthly pages used per source type */
    val monthlyNotesUsed: Int = 0,
    val monthlyMemosUsed: Int = 0,
    val monthlyTasksUsed: Int = 0,

    /** When the monthly budget resets (epoch ms of next reset date) */
    val monthlyBudgetResetDate: Long = 0L,

    /**
     * Pages read by AI on the user's own OpenRouter key this calendar month (Oct 2026): new pages
     * and re-reads of edited pages both cost money, so both count ([aiReadsMonth] = "yyyy-MM").
     */
    val aiReadsThisMonth: Int = 0,
    val aiReadsMonth: String = "",

    /** Day of month for budget reset (1-31), derived from billing cycle */
    val billingAnniversaryDay: Int = 0
)

/**
 * Tracks which first-run setup items have been completed.
 * Used to show badge indicators on settings that need attention.
 *
 * Badge clearing conditions:
 * - preferencesReviewed: User changes ANY setting in PreferencesDialog
 * - calendarModeChosen: User explicitly taps Standalone or Hybrid mode
 * - aiSettingsReviewed: User toggles AI enabled OR opens AiSettingsDialog
 * - booxNotesConfigured: User toggles Boox Notes enabled
 */
@Serializable
data class SetupChecklist(
    val preferencesReviewed: Boolean = false,
    val calendarModeChosen: Boolean = false,
    val aiSettingsReviewed: Boolean = false,
    val booxNotesConfigured: Boolean = false,
) {
    /** Count of items still pending setup */
    val pendingCount: Int
        get() = listOf(
            preferencesReviewed,
            calendarModeChosen,
            aiSettingsReviewed,
            booxNotesConfigured
        ).count { !it }

    /** True if all setup items have been completed */
    val isComplete: Boolean
        get() = pendingCount == 0
}
