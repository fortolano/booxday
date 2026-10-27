package com.boox.day.editor

/**
 * ⚠️ PERFORMANCE-CRITICAL FILE - READ BEFORE MODIFYING ⚠️
 *
 * This file contains carefully tuned fixes for severe performance issues.
 * Careless modifications WILL reintroduce bugs that cause:
 * - Stylus input loss during rapid day switching
 * - Progressive app slowdown (zombie coroutines)
 * - Strokes not being saved
 *
 * PROTECTED SECTIONS (search for these comments):
 * - "TAB-BASED FIX" - Stylus input routing (4 locations)
 * - "PERFORMANCE FIX" - Observer lifecycle management
 * - "observerScope" - All Flow observers must use this scope
 *
 * BEFORE MODIFYING:
 * 1. Read CLAUDE.md section "PERFORMANCE-CRITICAL CODE"
 * 2. Read memory.md section "10.6 Performance Fixes Architecture"
 * 3. Understand the dual-canvas architecture (Memos + Tasks)
 *
 * KEY RULES:
 * - ALL setRawDrawingEnabled(true) calls MUST check isThisCanvasActive
 * - ALL new observers MUST use observerScope!!.launch, NOT coroutineScope.launch
 * - NEVER remove cancelObservers() calls
 *
 * If unsure: ASK before changing.
 */

import com.boox.day.editor.state.TextBoxState

import android.content.Context
import android.view.MotionEvent
import java.lang.ref.WeakReference
import com.boox.day.compat.AndroidStylusInput
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toRect
import com.boox.day.PERF_LOGS
import com.boox.day.compat.OnyxCompat
import com.boox.day.data.AppRepository
import com.boox.day.data.PageDataManager
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.Attachment
import com.boox.day.data.db.Image
import com.boox.day.data.db.StrokePoint
import com.boox.day.data.model.SimplePointF
import com.boox.day.editor.drawing.drawImage
import com.boox.day.editor.drawing.selectPaint
import com.boox.day.editor.state.EditorState
import com.boox.day.editor.state.History
import com.boox.day.editor.state.Mode
import com.boox.day.editor.state.Operation
import com.boox.day.editor.state.PlacementMode
import com.boox.day.editor.utils.ERASER_WIDTH
import com.boox.day.editor.utils.Eraser
import com.boox.day.editor.utils.Pen
import com.boox.day.editor.utils.calculateBoundingBox
import com.boox.day.editor.utils.copyInput
import com.boox.day.editor.utils.copyInputToSimplePointF
import com.boox.day.editor.utils.getModifiedStrokeEndpoints
import com.boox.day.editor.utils.handleDraw
import com.boox.day.editor.utils.handleErase
import com.boox.day.editor.utils.handleScribbleToErase
import com.boox.day.editor.utils.handleSelect
import com.boox.day.editor.utils.loadPreview
import com.boox.day.editor.utils.onSurfaceChanged
import com.boox.day.editor.utils.onSurfaceDestroy
import com.boox.day.editor.utils.onSurfaceInit
import com.boox.day.editor.utils.partialRefreshRegionOnce
import com.boox.day.editor.utils.penToStroke
import com.boox.day.editor.utils.pointsToPath
import com.boox.day.editor.utils.prepareForPartialUpdate
import com.boox.day.editor.utils.refreshCanvasRegion
import com.boox.day.editor.utils.refreshScreenRegion
import com.boox.day.editor.utils.resetScreenFreeze
import com.boox.day.editor.utils.restoreDefaults
import com.boox.day.editor.utils.selectImage
import com.boox.day.editor.utils.selectImagesAndStrokes
import com.boox.day.editor.utils.selectImagesStrokesAndAttachments
import com.boox.day.editor.utils.setEraserTrail
import com.boox.day.editor.utils.setupSurface
import com.boox.day.editor.utils.toPageCoordinates
import com.boox.day.editor.utils.transformToLine
import com.boox.day.editor.ui.TASKS_CONTROL_COLUMN_WIDTH
import com.boox.day.editor.utils.waitForEpdRefresh
import com.boox.day.io.uriToBitmap
import com.boox.day.startup.StartupDiagnostics
import com.boox.day.startup.StartupStage
import com.boox.day.ui.SnackConf
import com.boox.day.ui.SnackState
import com.boox.day.ui.convertDpToPixel
import com.boox.day.ui.showHint
import com.boox.day.utils.logCallStack
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.extension.isNotNull
import com.onyx.android.sdk.extension.isNull
import com.onyx.android.sdk.extension.isNullOrEmpty
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList
import android.util.Log
import com.boox.day.utils.TaggedLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.system.measureTimeMillis

private const val TAG = "DrawCanvas"

data class SurfaceSize(val width: Int, val height: Int)


@Volatile var pressure: Float = OnyxCompat.DEFAULT_MAX_PRESSURE

// TODO: Do not recreate surface on every page change
class DrawCanvas(
    context: Context,
    val coroutineScope: CoroutineScope,
    // PERFORMANCE FIX: Made mutable for lifecycle management
    // When date changes, AndroidView.update block calls updateState() to swap in new instances
    // without recreating the entire DrawCanvas (which would trigger expensive TouchHelper setup)
    var state: EditorState,
    var page: PageView,
    var history: History
) : SurfaceView(context) {
    private val strokeHistoryBatch = mutableListOf<String>()
    private val logCanvasObserver = TaggedLog("CanvasObservers")
    private val log = TaggedLog("DrawCanvas")
    var lastStrokeEndTime: Long = 0
    // PERFORMANCE: Prevent redundant setupSurface calls
    private var surfaceUpdateInProgress = false
    private var lastSurfaceUpdateCompletionTime: Long = 0
    // Track when the surface has completed initial setup to avoid early activation races
    private var surfaceReady = false
    @Volatile private var surfaceSetupInProgress = false
    @Volatile private var startupInitializationFailed = false
    @Volatile private var hasCompletedInitialSurfaceSetup = false

    // TAB-BASED FIX: Identifies whether this canvas belongs to Memos or Tasks tab
    // Used to determine if this canvas should receive stylus input based on active tab
    // This replaces the fragile pageId-based check that caused race conditions during rapid day switching
    var isMemosCanvas: Boolean = true  // Default to Memos since it's the default tab

    // DAY CHANGE FIX: Track pageId from last updateState() or initialization
    // Used by EditorSurface.update to detect day changes when PageView is reused and mutated in place
    // Without this, the comparison view.page.currentPageId != page.currentPageId always returns false
    // because both reference the same mutated object after changePage() mutates it
    var lastUpdatedPageId: String = page.currentPageId

    // PERFORMANCE FIX: Job to track and cancel observer coroutines
    // When DrawCanvas is destroyed, all Flow collectors must be cancelled
    // to prevent memory leaks and zombie coroutines accumulating over time
    private var observerJob: Job? = null
    private var observerScope: CoroutineScope? = null

    override fun onAttachedToWindow() {
        log.d("Attached to window")
        super.onAttachedToWindow()
        if (::touchHelper.isInitialized && observerJob?.isActive != true) registerObservers()
    }

    override fun onDetachedFromWindow() {
        log.d("Detached from window")
        cancelAndroidPen()
        surfaceReady = false
        finishStylusContact()
        // PERFORMANCE FIX: Cancel all observer coroutines when detached
        cancelObservers()
        super.onDetachedFromWindow()
    }

    /**
     * Cancels all observer coroutines to prevent memory leaks.
     * Called when the canvas is detached or surface is destroyed.
     */
    private fun cancelObservers() {
        if (PERF_LOGS) log.i("PERF_DEBUG: cancelObservers() called - observerJob=$observerJob, isActive=${observerJob?.isActive}")
        observerJob?.let { job ->
            if (job.isActive) {
                if (PERF_LOGS) {
                    val childCount = job.children.count()
                    log.i("PERF_DEBUG: Cancelling $childCount observer coroutines")
                }
                job.cancel()
                if (PERF_LOGS) log.i("PERF_DEBUG: job.cancel() called, isActive now=${job.isActive}")
            } else {
                if (PERF_LOGS) log.i("PERF_DEBUG: observerJob exists but isActive=false, nothing to cancel")
            }
        }
        if (PERF_LOGS && observerJob == null) log.i("PERF_DEBUG: observerJob is null, nothing to cancel")
        observerJob = null
        observerScope = null
        // FLICK FIX: Reset memo mode tracking so next registration detects first emission
        lastKnownMemoMode = null
        deferredStartupRawDrawingJob?.cancel()
        deferredStartupRawDrawingJob = null
        deferredStartupRawDrawingOpen = false
    }

    /**
     * PERFORMANCE FIX: Updates state references when date/page changes.
     *
     * AndroidView.factory runs only ONCE, but remember(pageId) creates NEW page/state/history
     * instances when the date changes. Without this method, DrawCanvas would hold STALE references
     * and ~20 zombie observer coroutines would accumulate per date change.
     *
     * This method:
     * 1. Cancels existing observer coroutines (prevents zombie accumulation)
     * 2. Updates references to new instances
     * 3. Re-registers observers with new state
     *
     * Called from AndroidView.update block in MainScreen.kt when pageId changes.
     */
    fun updateState(newPage: PageView, newState: EditorState, newHistory: History) {
        cancelAndroidPen()
        // 1PX MISMATCH FIX (RESOLVED): Sync PageView dimensions with actual SurfaceView.
        // When a tab becomes active, its PageView may have stale dimensions from when it was
        // created (before surfaceChanged fired). The active tab gets surfaceChanged, but the
        // inactive tab's PageView never receives it. Sync here to ensure bitmap matches surface.
        // Note: This is about dimension mismatch, NOT the separate "black lines" caching bug.
        val surfaceWidth = this.width
        val surfaceHeight = this.height
        if (surfaceWidth > 0 && surfaceHeight > 0) {
            newPage.syncToSurfaceSize(surfaceWidth, surfaceHeight)
        }
        // Cancel all existing observers (they reference old state)
        cancelObservers()

        // Update references
        page = newPage
        state = newState
        history = newHistory

        // Re-register observers with new state
        registerObservers()

        // ⚠️ NO-TOUCH ZONE: STALE STROKE STYLE FIX (Dec 2025 - Codex)
        // Observers use drop(1), so they skip initial synchronization on day switch.
        // Without this, first stroke after day switch uses stale style (gray/eraser).
        // See: CLAUDE.md "Critical Bug Fixes" and memory.md section 14
        updatePenAndStroke()

        // Redraw with new page content
        drawCanvasToView(null)

        // DAY CHANGE FIX: Track pageId for EditorSurface detection
        lastUpdatedPageId = newPage.currentPageId
    }

    var isErasing: Boolean = false

    companion object {
        // PERF_DEBUG: Track total observer registrations across all DrawCanvas instances
        @Volatile var totalObserverRegistrations = 0
        @Volatile var totalObserverCancellations = 0

        // SPLASH/WELCOME GHOST FIX (Mar 2026):
        // Gate the first raw drawing open on actual splash/welcome dismissal timing.
        private const val STARTUP_RAW_DRAWING_SETTLE_MS = 500L
        @Volatile private var splashDismissedAtMs: Long = 0L
        @Volatile private var welcomePopupVisible: Boolean = false
        @Volatile private var welcomeDismissedAtMs: Long = 0L
        @Volatile private var ignoreWelcomePopupUntilStartupCanvasReady: Boolean = false
        private val startupCanvasReady = MutableStateFlow(false)

        fun prepareStartupSplash(ignoreWelcomePopupUntilCanvasReady: Boolean = false) {
            splashDismissedAtMs = 0L
            ignoreWelcomePopupUntilStartupCanvasReady = ignoreWelcomePopupUntilCanvasReady
            startupCanvasReady.value = false
        }

        fun notifySplashDismissed() {
            splashDismissedAtMs = SystemClock.elapsedRealtime()
        }

        fun setWelcomePopupVisible(visible: Boolean) {
            if (visible) {
                welcomePopupVisible = true
                welcomeDismissedAtMs = 0L
            } else {
                welcomePopupVisible = false
                if (welcomeDismissedAtMs == 0L) {
                    welcomeDismissedAtMs = SystemClock.elapsedRealtime()
                }
            }
        }

        fun notifyWelcomeDismissed() {
            welcomePopupVisible = false
            welcomeDismissedAtMs = SystemClock.elapsedRealtime()
        }

        suspend fun awaitStartupCanvasReady(timeoutMs: Long): Boolean {
            if (startupCanvasReady.value) return true
            return withTimeoutOrNull(timeoutMs) {
                startupCanvasReady.filter { it }.first()
            } != null
        }

        fun finishStartupSplashHandoff() {
            ignoreWelcomePopupUntilStartupCanvasReady = false
        }

        private fun notifyStartupCanvasReady() {
            ignoreWelcomePopupUntilStartupCanvasReady = false
            startupCanvasReady.value = true
        }

        // RAW DRAWING OWNERSHIP: Serialize Onyx global state mutations
        private val rawDrawingMutex = Mutex()
        @Volatile private var rawDrawingOwner: String? = null

        // SINGLE TOUCHHELPER CONTROLLER
        private val touchHelperMutex = Mutex()
        private var sharedTouchHelper: TouchHelper? = null
        private var sharedCanvasHash: Int? = null
        private var sharedCanvas = WeakReference<DrawCanvas>(null)

        fun dispatchAndroidStylusEvent(event: MotionEvent): Boolean =
            sharedCanvas.get()?.dispatchAndroidPen(event) ?: false

        fun cancelAndroidStylusInput() { sharedCanvas.get()?.cancelAndroidPen() }
        val surfaceSize = MutableStateFlow<SurfaceSize?>(null)

        // ⚠️ NO-TOUCH ZONE: TAB SWITCH LIMIT RECT FIX (Dec 2025)
        // Track last active canvas type to detect tab type changes globally.
        // Tasks and Memos have different limit rects (Tasks excludes 130dp control column).
        // Without this, first tab switch OR switching back fails to set correct limit rect.
        // Static variable required because canvas instances are recreated but state must persist.
        // See: CLAUDE.md "Critical Bug Fixes" and memory.md section 14
        @Volatile private var lastActiveCanvasWasMemos: Boolean? = null

        private suspend fun acquireTouchHelper(canvas: DrawCanvas): Pair<TouchHelper, Boolean> {
            return touchHelperMutex.withLock {
                val currentHash = canvas.hashCode()
                if (sharedTouchHelper != null && sharedCanvasHash == currentHash) {
                    return@withLock sharedTouchHelper!! to false
                }
                // Close and drop previous helper if bound to another canvas
                sharedTouchHelper?.let {
                    try {
                        it.setRawDrawingEnabled(false)
                        it.closeRawDrawing()
                    } catch (_: Throwable) {
                    }
                }
                sharedCanvas.get()?.cancelAndroidPen()
                canvas.androidPenBackend = OnyxCompat.usesAndroidPenInput(canvas.context)
                val newHelper = OnyxCompat.createTouchHelper(canvas,
                    if (canvas.androidPenBackend) AndroidStylusInput.previewCallback else canvas.inputCallback,
                    canvas.androidPenBackend)
                sharedCanvas = WeakReference(canvas)
                sharedTouchHelper = newHelper
                sharedCanvasHash = currentHash
                newHelper to true
            }
        }

        var forceUpdate = MutableSharedFlow<Rect?>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST) // null for full redraw
        // CRITICAL: extraBufferCapacity=1 prevents deadlock - called from many external places
        var refreshUi = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var refreshUiImmediately = MutableSharedFlow<Unit>(
            replay = 1, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        var isDrawing = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
        var restartAfterConfChange = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        // used for managing drawing state on regain focus
        val onFocusChange = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

        // before undo we need to commit changes
        val commitHistorySignal = MutableSharedFlow<Unit>()
        // CRITICAL: extraBufferCapacity=1 prevents deadlock when emit() is called
        // before collector is ready (static flow, instance collector race condition)
        val commitHistorySignalImmediately = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        // used for checking if commit was completed
        var commitCompletion = CompletableDeferred<Unit>()

        // It might be bad idea, but plan is to insert graphic in this, and then take it from it
        // There is probably better way
        var addImageByUri = MutableStateFlow<Uri?>(null)
        var rectangleToSelectByGesture = MutableStateFlow<Rect?>(null)
        var selectSingleAttachment = MutableSharedFlow<Attachment>(extraBufferCapacity = 1)
        var selectSingleImage = MutableSharedFlow<Image>(extraBufferCapacity = 1)
        var drawingInProgress = Mutex()
        @Volatile private var drawingLockOwner: String? = null
        @Volatile private var drawingLockAcquiredAtMs: Long = 0L

        private fun drawingLockState(nowMs: Long = SystemClock.uptimeMillis()): String {
            val owner = drawingLockOwner ?: "unknown"
            val heldForMs = drawingLockAcquiredAtMs.takeIf { it > 0L }?.let { nowMs - it } ?: 0L
            return "owner=$owner held=${heldForMs}ms"
        }

        private suspend inline fun <T> withDrawingLock(owner: String, block: () -> T): T {
            val waitStart = SystemClock.uptimeMillis()
            return drawingInProgress.withLock {
                val acquiredAt = SystemClock.uptimeMillis()
                val waitMs = acquiredAt - waitStart
                if (waitMs > 100) {
                    Log.w(
                        "DrawCanvas.drawingLock",
                        "Waited ${waitMs}ms to acquire drawing lock for $owner (${drawingLockState(acquiredAt)})"
                    )
                }

                drawingLockOwner = owner
                drawingLockAcquiredAtMs = acquiredAt
                try {
                    block()
                } finally {
                    val heldMs = SystemClock.uptimeMillis() - acquiredAt
                    when {
                        heldMs > 3000 -> Log.e(
                            "DrawCanvas.drawingLock",
                            "Held drawing lock for ${heldMs}ms in $owner"
                        )

                        heldMs > 100 -> Log.w(
                            "DrawCanvas.drawingLock",
                            "Held drawing lock for ${heldMs}ms in $owner"
                        )
                    }
                    drawingLockOwner = null
                    drawingLockAcquiredAtMs = 0L
                }
            }
        }

        // ⚠️ NO-TOUCH ZONE: FAST DRAWING STROKE LOSS FIX (Dec 2025)
        // Track when stylus was last used. Used by flushUIUpdates() to skip disabling
        // raw drawing during active drawing (which caused stroke loss during fast writing).
        // 2 seconds covers brief pauses between strokes while allowing badge updates when idle.
        // DO NOT reduce below 1.5 seconds - fast writing needs protection from flush interrupts.
        // See: CLAUDE.md "Critical Bug Fixes" and memory.md section 14
        private val stylusContact = StylusContactGuard()
        @Volatile var lastStylusActivityMs: Long = 0L
        const val PALM_REJECTION_WINDOW_MS = 2000L  // 2 seconds - DO NOT REDUCE

        fun isStylusRecentlyActive(): Boolean {
            return stylusContact.isActive ||
                System.currentTimeMillis() - lastStylusActivityMs < PALM_REJECTION_WINDOW_MS
        }

        // How long the pen must rest before completed strokes are posted to the surface.
        // Above the ~190 ms a stroke's live ink stays on the panel, well below the system's own
        // pen-up repaint (~570 ms). See schedulePenRestBlit().
        private const val PEN_REST_BLIT_DELAY_MS = 250L

        // How long the pen's render stays paused after an erase before it resumes: BOOX Notes'
        // erase finish waits 300 ms (EraseFinishAction: setResumeDelayTime(300)). The screen has to
        // finish the clean page's update first; on the Go 6 a full-canvas update took ~530 ms
        // (kernel waveform cleanup, Oct 2026 log), so a larger area waits longer, up to the full
        // canvas's 300 + 250 ms. See commitErasedPage().
        private const val ERASE_RESUME_DELAY_MS = 300L
        private const val ERASE_RESUME_FULL_CANVAS_EXTRA_MS = 250L
        // A resume with the pen down would toggle the render mid-stroke: wait for pen-up, this long at most.
        private const val ERASE_RESUME_PEN_WAIT_MAX_MS = 10_000L

        // Signal when strokes/images are added or removed (for updating calendar badges)
        val contentChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        // Signal when eraser completes in Tasks mode - carries the affected Rect
        // Used by MainScreen to check if task rows became empty and should auto-clear metadata
        // IMPORTANT: Only emitted when isMemosCanvas=false (Tasks mode) for isolation
        val eraserCompleted = MutableSharedFlow<Rect>(extraBufferCapacity = 1)

        // Signal to refresh only the canvas region with high-quality GC mode
        // Triggered from eraser menu "Refresh Canvas" option
        val refreshCanvasSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        // Signal when toolbar icons are tapped (for collapsing expanded events panel)
        val toolbarInteraction = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        // Signal when canvas receives touch/stylus input (for collapsing expanded events panel)
        val canvasInteraction = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        // Signal to open handwriting recognition dialog from mini selection toolbar
        val openHandwritingRecognitionSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        // Signals for Boox Notes integration
        val viewBooxNotes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val openBulkSmartLink = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        // OPTION C: Active canvas control - ensures only one canvas's TouchHelper receives input
        // When multiple canvases exist (Memos/Tasks), only the active one should have raw drawing enabled
        // Set to the pageId of the currently active canvas; others will disable their TouchHelper
        val activeCanvasPageId = MutableStateFlow<String?>(null)
        val refreshBooxNotes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        // Signal when expanded Events panel is covering part of the canvas
        // When true, stylus input should be blocked to prevent drawing under the overlay
        val eventsOverlayExpanded = MutableStateFlow(false)
        val toolbarModalVisible = MutableStateFlow(false)
        val mainScreenModalVisible = MutableStateFlow(false)
        val canvasHostModalVisible = MutableStateFlow(false)

        // The Memos page map is open (Oct 2026, Phase 0 spike): the pen layer is off like under any popup,
        // but the map's own session turns it off and back on (beginMapSession / endMapSession), in the
        // order that keeps the map out of the pen layer. So it isn't one of the overlay observer's flows.
        val pageMapOpen = MutableStateFlow(false)

        // The Memos view is moving (two-finger drag, page map): the pen's render is paused and
        // nothing may resume it or toggle raw drawing until the move's finish (endViewportMove).
        @Volatile var viewportMoving = false

        /** The canvas that holds the pen (TouchHelper), i.e. the shown one. */
        fun active(): DrawCanvas? = sharedCanvas.get()

        /** The pen touches the screen now or lifted less than [ms] ago. */
        fun isStylusActiveWithin(ms: Long): Boolean =
            stylusContact.isActive || System.currentTimeMillis() - lastStylusActivityMs < ms

        fun isCanvasInputBlockedByOverlay(): Boolean {
            return eventsOverlayExpanded.value ||
                toolbarModalVisible.value ||
                mainScreenModalVisible.value ||
                canvasHostModalVisible.value ||
                pageMapOpen.value
        }

        // For QuickNav scrolling with previews
        val saveCurrent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val previewPage = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val restoreCanvas = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        // Two-finger tap gesture: Request page deletion confirmation
        // Emitted by EditorGestureReceiver, collected by PersistentCanvasHost to show dialog
        val requestDeletePageConfirmation = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        // Signal for Memos/Tasks mode changes (emitted by Toolbar, collected by MainScreen)
        // true = Memos mode, false = Tasks mode
        // Using StateFlow so new Toolbar instances can read the current value
        val memoModeChanged = MutableStateFlow(true)  // Default to Memos mode

        // TASKS-ONLY: Row indices of completed tasks (0-9) - used to block erasing
        // MainScreen updates this when tasks change; DrawCanvas reads before erasing
        // Using StateFlow for immediate reads without suspension
        val completedTaskRows = MutableStateFlow<Set<Int>>(emptySet())

        /**
         * Briefly toggle raw drawing off/on to flush pending UI updates to the framebuffer.
         *
         * Onyx's raw drawing mode freezes the Android View rendering pipeline to prevent
         * resource contention during stylus input. This means Compose recomposition happens
         * but pixels never reach the screen. Toggling raw drawing momentarily unblocks
         * the render pipeline, allowing badge updates and other UI changes to appear.
         *
         * Called from MainScreen when badge state changes while drawing is active.
         *
         * ⚠️ NO-TOUCH ZONE: The isStylusRecentlyActive() guard is CRITICAL.
         * Without it, the 50ms disable cycle causes stroke loss during fast drawing.
         * See: CLAUDE.md "Critical Bug Fixes" and memory.md section 14
         */
        fun flushUIUpdates() {
            val helper = sharedTouchHelper ?: return
            // ⚠️ CRITICAL GUARD: DO NOT REMOVE - prevents stroke loss during fast drawing
            if (isStylusRecentlyActive()) {
                Log.v("DrawCanvas", "flushUIUpdates skipped - stylus recently active")
                return
            }
            // A view move holds the pen paused; its finish shows the screen anyway
            if (viewportMoving) return
            PageDataManager.setTransitioning(true)
            try {
                helper.setRawDrawingEnabled(false)
                // Post re-enable to give Android one frame to rasterize
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        if (isCanvasInputBlockedByOverlay()) {
                            Log.i("DrawCanvas", "RACE_DEBUG: flushUIUpdates leaving raw drawing disabled because a blocking overlay is open")
                        } else {
                            Log.i("DrawCanvas", "RACE_DEBUG: flushUIUpdates re-enabling raw drawing")
                            helper.setRawDrawingEnabled(true)
                            setEraserTrail(helper)  // The firmware forgets it on enable
                        }
                    } finally {
                        PageDataManager.setTransitioning(false)
                    }
                }, 50)
            } catch (e: Exception) {
                Log.w("DrawCanvas", "flushUIUpdates failed: ${e.message}")
                PageDataManager.setTransitioning(false)
            }
        }

        /**
         * Force unfreeze the e-ink display immediately, bypassing the 2-second stylus guard.
         *
         * Called when user taps OUTSIDE the canvas area (calendar, toolbar, etc.).
         * This is a clear signal they want to interact with UI, not continue drawing.
         *
         * Unlike flushUIUpdates(), this does NOT check isStylusRecentlyActive() because
         * the user's intent is unambiguous - they're done drawing and want UI access.
         */
        fun forceUnfreeze() {
            val helper = sharedTouchHelper ?: return
            if (viewportMoving) return  // see flushUIUpdates
            try {
                Log.i("DrawCanvas", "UNFREEZE: Force unfreezing e-ink (tap outside canvas)")
                helper.setRawDrawingEnabled(false)
                // Post re-enable to give Android one frame to rasterize
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (isCanvasInputBlockedByOverlay()) {
                        Log.i("DrawCanvas", "UNFREEZE: Keeping raw drawing disabled because a blocking overlay is open")
                    } else {
                        Log.i("DrawCanvas", "UNFREEZE: Re-enabling raw drawing after force unfreeze")
                        helper.setRawDrawingEnabled(true)
                        setEraserTrail(helper)  // The firmware forgets it on enable
                    }
                }, 50)
            } catch (e: Exception) {
                Log.w("DrawCanvas", "forceUnfreeze failed: ${e.message}")
            }
        }

        private suspend fun waitForDrawing() {
            Log.d(
                "DrawCanvas.waitForDrawing", "waiting"
            )
            val elapsed = measureTimeMillis {
                withTimeoutOrNull(3000) {
                    // Just to make sure wait 1ms before checking lock.
                    delay(1)
                    // Wait until drawingInProgress is unlocked before proceeding
                    while (drawingInProgress.isLocked) {
                        delay(5)
                    }
                } ?: Log.e(
                    "DrawCanvas.waitForDrawing",
                    "Timeout while waiting for drawing lock. Potential deadlock. ${drawingLockState()}"
                )

            }
            when {
                elapsed > 3000 -> Log.e(
                    "DrawCanvas.waitForDrawing", "Exceeded timeout ($elapsed ms) while ${drawingLockState()}"
                )

                elapsed > 100 -> Log.w(
                    "DrawCanvas.waitForDrawing",
                    "Took too long: $elapsed ms while ${drawingLockState()}"
                )

                else -> Log.d("DrawCanvas.waitForDrawing", "Finished waiting in $elapsed ms")
            }

        }

        suspend fun waitForDrawingWithSnack() {
            if (drawingInProgress.isLocked) {
                val snack = SnackConf(text = "Waiting for drawing to finish…", duration = 60000)
                SnackState.globalSnackFlow.emit(snack)
                waitForDrawing()
                SnackState.cancelGlobalSnack.emit(snack.id)
            }
        }
    }

    fun getActualState(): EditorState {
        return this.state
    }

    @Suppress("RedundantOverride")
    private val inputCallback: RawInputCallback = object : RawInputCallback() {
        // Documentation: https://github.com/onyx-intl/OnyxAndroidDemo/blob/d3a1ffd3af231fe4de60a2a0da692c17cb35ce31/doc/Onyx-Pen-SDK.md#L40-L62
        // - pen : `onBeginRawDrawing()` -> `onRawDrawingTouchPointMoveReceived()` -> `onRawDrawingTouchPointListReceived()` -> `onEndRawDrawing()`
        // - erase :  `onBeginRawErasing()` -> `onRawErasingTouchPointMoveReceived()` -> `onRawErasingTouchPointListReceived()` -> `onEndRawErasing()`

        override fun onBeginRawDrawing(p0: Boolean, p1: TouchPoint?) {
            beginStylusContact()
            log.i("RAW_IN[${canvasOwnerId()}]: onBeginRawDrawing p0=$p0")
        }

        override fun onEndRawDrawing(p0: Boolean, p1: TouchPoint?) {
            finishStylusContact()
            log.i("RAW_IN[${canvasOwnerId()}]: onEndRawDrawing p0=$p0")
        }

        override fun onRawDrawingTouchPointMoveReceived(p0: TouchPoint?) {
        }

    override fun onRawDrawingTouchPointListReceived(plist: TouchPointList) {
        // The completed list arrives before onEnd: allow its immediate persistence blit.
        finishStylusContact()
        // - pen : `onBeginRawDrawing()` -> `onRawDrawingTouchPointMoveReceived()` -> `onRawDrawingTouchPointListReceived()` -> `onEndRawDrawing()`

        if (PERF_LOGS) log.i("RAW_IN[${canvasOwnerId()}]: onRawDrawingTouchPointListReceived - isDrawing=${state.isDrawing}, inputBlocked=${isCanvasInputBlockedByOverlay()}, eventsOverlayExpanded=${eventsOverlayExpanded.value}")
        if (plist.points.isEmpty()) {
            if (PERF_LOGS) log.w("RAW_IN[${canvasOwnerId()}]: onRawDrawingTouchPointListReceived empty list - skipping")
            return
        }

            // GUARD: Block stylus input when drawing is disabled (text box selected, etc.)
            if (!state.isDrawing) {
                if (PERF_LOGS) log.d("Blocking stylus input - drawing disabled")
                return
            }

            // GUARD: Block stylus input when expanded Events panel is covering the canvas
            // Signal to close the events panel on touch, then return
            if (isCanvasInputBlockedByOverlay()) {
                if (eventsOverlayExpanded.value) {
                    if (PERF_LOGS) log.i("Canvas touched while events expanded - signaling to close")
                    canvasInteraction.tryEmit(Unit)
                } else if (PERF_LOGS) {
                    log.i("Blocking stylus input - modal overlay is open")
                }
                return
            }

            // A stroke made while the view moves showed no ink (the pen's render is paused): dropped,
            // so nothing appears that wasn't seen. Its points would also belong to an older view.
            if (viewportMoving) {
                log.i("PAN_SPIKE: stroke while the view moves - dropped (${plist.points.size} points)")
                return
            }

            val currentLastStrokeEndTime = lastStrokeEndTime
            lastStrokeEndTime = System.currentTimeMillis()
            // sometimes UI will get refreshed and frozen before we draw all the strokes.
            // I think, its because of doing it in separate thread. Commented it for now, to
            // observe app behavior, and determine if it fixed this bug,
            // as I do not know reliable way to reproduce it
            // Need testing if it will be better to do in main thread on, in separate.
            // thread(start = true, isDaemon = false, priority = Thread.MAX_PRIORITY) {

            when (getActualState().mode) {
                Mode.Erase -> onRawErasingTouchPointListReceived(plist)
                Mode.Select -> {
                    thread {
                        val points =
                            copyInputToSimplePointF(plist.points, page.scroll, page.zoomLevel.value)
                        handleSelect(
                            coroutineScope, this@DrawCanvas.page, getActualState(), points
                        )
                        val boundingBox = calculateBoundingBox(points) { Pair(it.x, it.y) }.toRect()
                        val padding = 10
                        val dirtyRect = Rect(
                            boundingBox.left - padding,
                            boundingBox.top - padding,
                            boundingBox.right + padding,
                            boundingBox.bottom + padding
                        )
                        refreshUi(dirtyRect)
                    }
                }

                // After each stroke ends, we draw it on our canvas.
                // This way, when screen unfreezes the strokes are shown.
                // When in scribble mode, ui want be refreshed.
                // If we UI will be refreshed and frozen before we manage to draw
                // strokes want be visible, so we need to ensure that it will be done
                // before anything else happens.
                Mode.Line -> {
                    val pageScroll = page.scroll
                    val pageZoom = page.zoomLevel.value
                    val currentState = getActualState()
                    val currentPen = currentState.pen
                    val currentPenSettings = currentState.penSettings[currentPen.penName]!!
                    val (startPoint, endPoint) = getModifiedStrokeEndpoints(
                        plist.points,
                        pageScroll,
                        pageZoom
                    )
                    val linePoints = transformToLine(startPoint, endPoint)
                    val dirtyRect = Rect(
                        min(startPoint.x, endPoint.x).toInt(),
                        min(startPoint.y, endPoint.y).toInt(),
                        max(startPoint.x, endPoint.x).toInt(),
                        max(startPoint.y, endPoint.y).toInt()
                    )

                    coroutineScope.launch(Dispatchers.Main.immediate) {
                        withDrawingLock("raw-line") {
                            handleDraw(
                                this@DrawCanvas.page,
                                strokeHistoryBatch,
                                currentPenSettings.strokeSize,
                                currentPenSettings.color,
                                currentPen,
                                linePoints
                            )
                        }

                        coroutineScope.launch(Dispatchers.Default) {
//                                partialRefreshRegionOnce(this@DrawCanvas, dirtyRect)
                            refreshUi(dirtyRect)
                            commitHistorySignal.emit(Unit)
                        }
                    }
                }

                Mode.Draw -> {
                    val pageScroll = page.scroll
                    val pageZoom = page.zoomLevel.value
                    val currentState = getActualState()
                    val currentPen = currentState.pen
                    val currentPenSettings = currentState.penSettings[currentPen.penName]!!
                    val scaledPoints = copyInput(plist.points, pageScroll, pageZoom)
                    val firstPointTime = plist.points.first().timestamp

                    coroutineScope.launch(Dispatchers.Main.immediate) {
                        withDrawingLock("raw-draw") {
                            val erasedByScribbleDirtyRect = handleScribbleToErase(
                                page,
                                scaledPoints,
                                history,
                                currentPen,
                                currentLastStrokeEndTime,
                                firstPointTime
                            )
                            if (erasedByScribbleDirtyRect.isNullOrEmpty()) {
                                // draw the stroke
                                handleDraw(
                                    this@DrawCanvas.page,
                                    strokeHistoryBatch,
                                    currentPenSettings.strokeSize,
                                    currentPenSettings.color,
                                    currentPen,
                                    scaledPoints
                                )
                                // ╔══════════════════════════════════════════════════════════════════════════╗
                                // ║  FLICK BUG FIX (Dec 2025), TIMING CHANGED (Oct 2026) - read before edit  ║
                                // ║                                                                          ║
                                // ║  The stroke must reach the surface before the system's pen-up repaint    ║
                                // ║  (~570 ms after the last pen-up), which repaints from the surface. The   ║
                                // ║  old 500 ms history blit came too late: on an empty canvas the stroke    ║
                                // ║  flashed white ("first stroke flick").                                   ║
                                // ║                                                                          ║
                                // ║  It must NOT reach it after every stroke: each SurfaceView post is an    ║
                                // ║  e-ink commit, and the next stroke's live ink queues behind it (100-300  ║
                                // ║  ms with no ink, Go 10.3 trace, Oct 2026).                               ║
                                // ║                                                                          ║
                                // ║  So the system-ink backend posts once the pen has rested (250 ms). The   ║
                                // ║  Android pen backend draws its own ink and keeps the immediate post.     ║
                                // ╚══════════════════════════════════════════════════════════════════════════╝
                                if (androidPenBackend) drawCanvasToView(null) else schedulePenRestBlit()
                            } else {
                                // Scribble-to-erase detected: the scribble and the strokes it erased
                                // leave the screen like any erase away from the pen's trail.
                                if (androidPenBackend) {
                                    drawCanvasToView(erasedByScribbleDirtyRect)
                                    partialRefreshRegionOnce(
                                        this@DrawCanvas,
                                        erasedByScribbleDirtyRect,
                                        touchHelper
                                    )
                                } else {
                                    // The scribble itself was live ink, so its area is pushed too, with
                                    // room for half the pen's width past its points.
                                    val scribblePad = (currentPenSettings.strokeSize * pageZoom / 2).toInt() + 15
                                    val scribbleArea = calculateBoundingBox(plist.points) { Pair(it.x, it.y) }
                                        .toRect().apply { inset(-scribblePad, -scribblePad) }
                                    commitErasedPage(scribbleArea.apply {
                                        union(page.toScreenCoordinates(erasedByScribbleDirtyRect))
                                    })
                                }
                            }

                        }
                        coroutineScope.launch(Dispatchers.Default) {
                            commitHistorySignal.emit(Unit)
                        }
                    }
                }
            }
        }

        // Handle button/eraser tip of the pen:
        override fun onBeginRawErasing(p0: Boolean, p1: TouchPoint?) {
            beginStylusContact()
            // Phase 4 (Oct 2026): no prepareForPartialUpdate() here any more. It switched the view
            // to A2 and EPD turbo 100 on every erase, but nothing switched them back
            // (restoreDefaults only resets the display scheme) — a likely cause of ghosting after
            // erasing. The GL renderer it was meant for is never constructed. (Notable did the same.)
            isErasing = true
            // Re-send the eraser end's trail: some raw drawing re-enables don't (Notable does the same).
            if (!androidPenBackend) setEraserTrail(touchHelper, state.eraser)
        }

        override fun onEndRawErasing(p0: Boolean, p1: TouchPoint?) {
            finishStylusContact()
            isErasing = false
            // Phase 6 (Oct 2026): the unused OpenGL eraser renderer is gone; its setting was on by
            // default, so the display scheme reset after erasing stays exactly as it was.
            restoreDefaults(this@DrawCanvas)
        }

        override fun onRawErasingTouchPointListReceived(plist: TouchPointList?) {
            finishStylusContact()
            // GUARD: Block eraser input when drawing is disabled (text box selected, etc.)
            if (!state.isDrawing) {
                log.d("Blocking eraser input - drawing disabled")
                return
            }

            // GUARD: Block eraser input when expanded Events panel is covering the canvas
            // Signal to close the events panel on touch, then return
            if (isCanvasInputBlockedByOverlay()) {
                if (eventsOverlayExpanded.value) {
                    log.i("Canvas touched (eraser) while events expanded - signaling to close")
                    canvasInteraction.tryEmit(Unit)
                } else {
                    log.i("Blocking eraser input - modal overlay is open")
                }
                return
            }

            val eraserEnd = isErasing  // Set by onBeginRawErasing: the pen's eraser end, not the tip
            isErasing = false
            val penUpMs = SystemClock.uptimeMillis()

            // Erasing while the view moves: dropped, like a stroke (onRawDrawingTouchPointListReceived)
            if (viewportMoving) {
                log.i("PAN_SPIKE: erase while the view moves - dropped")
                return
            }

            if (plist == null) return
            if (plist.points.isEmpty()) {
                if (PERF_LOGS) log.w("RAW_IN[${canvasOwnerId()}]: onRawErasingTouchPointListReceived empty list - skipping")
                return
            }
            plist.points

            val points = copyInputToSimplePointF(plist.points, page.scroll, page.zoomLevel.value)
            val boundingBox = (calculateBoundingBox(plist.points) { Pair(it.x, it.y) }).toRect()
            // The Standard Eraser's trail reaches half its width past the points.
            val trailArea = Rect(boundingBox).apply {
                inset(-(ERASER_WIDTH / 2).toInt() - 10, -(ERASER_WIDTH / 2).toInt() - 10)
            }

            // TASKS-ONLY: Block erasing in completed task rows
            if (!isMemosCanvas) {
                val completedRows = completedTaskRows.value
                if (completedRows.isNotEmpty()) {
                    // Rows on screen (more in full screen, Oct 2026): the canvas height ÷ 10 in the normal view
                    val canvasHeight = com.boox.day.tasks.TaskPagination.screenRowsHeight(height.toFloat())
                    val rowCount = com.boox.day.tasks.TaskPagination.screenRowCount
                    val topOffset = 1f
                    val visibleHeight = canvasHeight - topOffset
                    val rowHeight = visibleHeight / rowCount

                    // Check if erased area intersects any completed row
                    for (rowIndex in completedRows) {
                        val rowTop = (topOffset + rowIndex * rowHeight).toInt()
                        val rowBottom = (topOffset + (rowIndex + 1) * rowHeight).toInt()
                        // If bounding box intersects this completed row, block the erase
                        if (boundingBox.bottom > rowTop && boundingBox.top < rowBottom) {
                            log.i("ERASER_BLOCKED: Attempted to erase in completed task row $rowIndex")
                            showErasedPage(penUpMs, eraserEnd, trailArea, null)  // The ink stays
                            return  // Block the erase entirely
                        }
                    }
                }
            }

            // Phase 3 (Oct 2026): which thread erases — decides whether erase moves under withDrawingLock
            log.d("ERASE_THREAD: ${Thread.currentThread().name}")
            val zoneEffected = handleErase(
                this@DrawCanvas.page,
                history,
                points,
                eraser = getActualState().eraser
            )
            showErasedPage(penUpMs, eraserEnd, trailArea, zoneEffected)
            if (zoneEffected != null) {
                // TASKS-ONLY: Signal eraser completion for auto-clearing empty task rows
                // Only emit in Tasks mode (!isMemosCanvas) to ensure Memos is completely unaffected
                if (!isMemosCanvas) {
                    eraserCompleted.tryEmit(zoneEffected)
                }
            }
        }

        override fun onRawErasingTouchPointMoveReceived(p0: TouchPoint?) {
//            if (p0 == null) return
        }

        override fun onPenUpRefresh(refreshRect: RectF?) {
            super.onPenUpRefresh(refreshRect)
        }

        override fun onPenActive(point: TouchPoint?) {
            super.onPenActive(point)
        }
    }

    private lateinit var touchHelper: TouchHelper
    private var androidPenBackend = false
    private val androidPenInput by lazy {
        AndroidStylusInput(inputCallback, { pressure }) { action, point, normalizedPressure, erasing ->
            // Use the SDK's pressure-aware EPD commands synchronously. Its AppTouchRender
            // queues MotionEvent references on Rx; Android may recycle those before use.
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    // The eraser end draws the same trail as the eraser tool.
                    if (erasing) setEraserPenStyle()
                    val width = when {
                        erasing || state.mode == Mode.Erase ->
                            if (state.eraser == Eraser.PEN) ERASER_WIDTH else 3f
                        state.mode == Mode.Select -> 4f
                        else -> state.penSettings[state.pen.penName]!!.strokeSize * page.zoomLevel.value
                    }
                    EpdController.moveTo(this, point!!.x, point.y, width, normalizedPressure)
                }
                MotionEvent.ACTION_MOVE -> EpdController.quadTo(this, point!!.x, point.y,
                    com.onyx.android.sdk.api.device.epd.UpdateMode.DU, normalizedPressure)
                MotionEvent.ACTION_UP -> EpdController.penUp()
                MotionEvent.ACTION_CANCEL -> {
                    EpdController.penUp()
                    // Drop transient ink on palm cancellation, focus loss or a lost UP.
                    if (surfaceReady && sharedCanvasHash == hashCode()) drawCanvasToView(null)
                }
            }
        }
    }

    private fun cancelAndroidPen() {
        if (!androidPenBackend || !androidPenInput.isCaptured) return
        androidPenInput.cancel()
        finishStylusContact()
        if (::touchHelper.isInitialized && sharedCanvasHash == hashCode()) updatePenAndStroke()
    }

    private fun dispatchAndroidPen(event: MotionEvent): Boolean {
        if (!androidPenBackend || !::touchHelper.isInitialized) return false
        val wasCaptured = androidPenInput.isCaptured
        if (!isAttachedToWindow || !hasWindowFocus() || !surfaceReady || surfaceSetupInProgress ||
            !rawDrawingOpened || !touchHelper.isRawDrawingInputEnabled || !state.isDrawing ||
            memoModeChanged.value != isMemosCanvas || isCanvasInputBlockedByOverlay() ||
            isStartupRawDrawingCaptureBlocked()) {
            cancelAndroidPen()
            return wasCaptured
        }
        val location = IntArray(2)
        getLocationInWindow(location)
        val local = MotionEvent.obtain(event)
        try {
            local.offsetLocation(-location[0].toFloat(), -location[1].toFloat())
            val toolbar = if (state.hasInternalToolbar && state.isToolbarOpen)
                convertDpToPixel(40.dp, context).toInt() else 0
            val left = if (isMemosCanvas) 0 else convertDpToPixel(TASKS_CONTROL_COLUMN_WIDTH, context).toInt()
            val top = if (GlobalAppSettings.current.toolbarPosition == AppSettings.Position.Top) toolbar else 0
            val bottom = if (top == 0) height - toolbar else height
            val bounds = Rect(left, top, width, bottom)
            val visible = Rect()
            if (!getLocalVisibleRect(visible) || !bounds.intersect(visible)) {
                cancelAndroidPen()
                return wasCaptured
            }
            val handled = androidPenInput.onTouchEvent(local, bounds)
            if (handled && !androidPenInput.isCaptured) {
                updatePenAndStroke()
            }
            return handled
        } finally { local.recycle() }
    }

    /**
     * Ensures this canvas is holding the currently owned TouchHelper.
     * Returns true if a new helper was created or swapped in.
     */
    private suspend fun ensureTouchHelper(): Boolean {
        val (helper, createdNew) = acquireTouchHelper(this)
        val helperChanged = !::touchHelper.isInitialized || touchHelper !== helper
        if (helperChanged) {
            val tabName = if (isMemosCanvas) "Memos" else "Tasks"
            if (PERF_LOGS) log.i("TOUCH_TRACE[$tabName]: Swapping TouchHelper to ${helper.hashCode()} (createdNew=$createdNew)")
            touchHelper = helper
            // New helper starts with no raw drawing session
            rawDrawingOpened = false
            // Reset op counter for clearer logging per helper instance
            enableDisableCounter = 0
            // Ensure the freshly swapped helper has the correct stroke style
            updatePenAndStroke()
        }
        return helperChanged || createdNew
    }

    // Track whether openRawDrawing() has been called on this instance
    // If overlay is expanded during setup, we skip opening raw drawing
    // We need to open it later when overlay collapses
    private var rawDrawingOpened = false
    private var enableDisableCounter = 0
    private fun nextOpId(): Int = ++enableDisableCounter

    // FLICK FIX: Track last known memo mode to detect ACTUAL tab changes
    // vs. mere observer re-registration (which happens on every day change).
    // StateFlow emits current value to new collectors, triggering reactivateRawDrawing()
    // even when tab hasn't changed. This unnecessary close→open cycle causes the flick.
    private var lastKnownMemoMode: Boolean? = null
    @Volatile private var deferredStartupRawDrawingOpen = false
    private var deferredStartupRawDrawingJob: Job? = null
    private var penRestBlitJob: Job? = null
    // Counts erase commits: only the newest one resumes the pen render (commitErasedPage).
    private var eraseCommitGeneration = 0
    // A completed stroke is in the page bitmap but not yet on the surface. The generation lets a
    // full blit that started before the latest stroke leave the flag set.
    @Volatile private var surfaceStale = false
    @Volatile private var surfaceStaleGeneration = 0

    private fun beginStylusContact() {
        stylusContact.begin(canvasOwnerId())
        lastStylusActivityMs = System.currentTimeMillis()
    }

    private fun finishStylusContact() {
        if (stylusContact.end(canvasOwnerId())) {
            lastStylusActivityMs = System.currentTimeMillis()
            // A pen-rest blit skipped while this contact lasted must still run.
            if (surfaceStale) schedulePenRestBlit()
        }
    }

    /**
     * Posts the completed strokes to the surface once the pen has rested, not after every stroke.
     *
     * On BOOX a SurfaceView post becomes an e-ink commit. The commit waits until the previous
     * stroke's live ink has finished (~190 ms), and the next stroke's live ink queues behind it:
     * a stroke started soon after the last one showed no ink for 100-300 ms (Go 10.3 trace,
     * Oct 2026). BOOX Notes and Notable post nothing while the pen is writing.
     * The post must still come before the system's pen-up repaint (~570 ms after the last pen-up),
     * which repaints from the surface: a post that late was the "first stroke flick" (Dec 2025).
     * Erasing passes a shorter [delayMs]: it already spent time cutting strokes after pen-up.
     */
    private fun schedulePenRestBlit(delayMs: Long = PEN_REST_BLIT_DELAY_MS) {
        surfaceStaleGeneration++
        surfaceStale = true
        penRestBlitJob?.cancel()
        val pageId = page.currentPageId
        penRestBlitJob = coroutineScope.launch(Dispatchers.Main) {
            delay(delayMs.coerceAtLeast(0L))
            // A stroke in progress re-arms this when it ends; another full blit may have run already.
            if (stylusContact.isActive || !surfaceStale || page.currentPageId != pageId) return@launch
            withDrawingLock("pen-rest-blit") {
                // The pen may have come down while this waited for the lock.
                if (!stylusContact.isActive) drawCanvasToView(null)
            }
        }
    }

    /**
     * Shows the page after an erase (Oct 2026, Go 6 tests with the owner).
     *
     * Pen tip, Standard Eraser: the white trail paints over the erased ink inside the pen layer, so
     * the page is simply posted like a pen stroke once the pen rests. Any pause of the pen layer
     * here flashed erased ink.
     *
     * Everything else on the BOOX pen engine, where ink is removed away from the pen's own trail
     * (Stroke Eraser loops, the pen's eraser end, Scribble to Erase): [commitErasedPage]. The
     * Stroke Eraser always commits, even over nothing, to clear its loop's trail. The Android pen
     * backend has no BOOX pen layer and keeps refreshUi.
     */
    private fun showErasedPage(penUpMs: Long, eraserEnd: Boolean, trailArea: Rect, erasedArea: Rect?) {
        when {
            androidPenBackend ->
                refreshUi(erasedArea?.let { Rect(trailArea).apply { union(it) } } ?: trailArea)
            eraserEnd || getActualState().eraser == Eraser.SELECT ->
                commitErasedPage(erasedArea?.let { Rect(trailArea).apply { union(it) } } ?: trailArea)
            else -> schedulePenRestBlit(PEN_REST_BLIT_DELAY_MS - (SystemClock.uptimeMillis() - penUpMs))
        }
    }

    /**
     * Shows an erase that removed ink away from the pen's own trail, the way BOOX Notes finishes an
     * erase (decompiled knote2: EraseFinishAction, InvalidateViewWithPenControlAction) (Oct 2026).
     *
     * While the pen layer is on, the screen driver is in handwriting mode: it rejects ordinary
     * updates, and the hardware keeps its own copy of every stroke drawn live. BooxDay deleted the
     * strokes from its page, but that copy still had them, so each time the screen left handwriting
     * mode they blinked back, every stroke erased so far at once. Pushing the clean page, pausing,
     * or reopening the pen did not help while it came too early or too late (0 ms, 100 ms, never):
     * the hardware takes its copy from the screen when the pen resumes, so the clean page must have
     * finished updating first (~530 ms for a full canvas on the Go 6). BOOX Notes' order:
     * 1. Push the changed area through the pen layer (enablePost), like its forced render.
     * 2. Pause only the pen's render. The input reader keeps running, so a stroke started now is
     *    still recorded (closing the pen session lost it, Oct 2026 review).
     * 3. Post the area again while the pen is paused (BOOX Notes' view invalidate).
     * 4. After [ERASE_RESUME_DELAY_MS] (longer for a bigger area), resume the render, and the
     *    hardware copies the clean screen. Never with the pen down (a mid-stroke render toggle
     *    flicks): wait for pen-up. Only the newest commit resumes, from a finally, so the pause
     *    can't be left on by a cancelled job.
     */
    private fun commitErasedPage(changedArea: Rect) {
        if (width <= 0 || height <= 0) return
        val pageId = page.currentPageId
        val area = Rect(changedArea)
        // A stroke written less than 250 ms ago is on the page but not on the surface yet. Push the
        // whole canvas now and drop the pending pen-rest post, which would start a second full
        // update inside the pause.
        if (surfaceStale) {
            penRestBlitJob?.cancel()
            area.set(0, 0, width, height)
        }
        // Inside the surface only; an empty area means the whole canvas.
        if (!area.intersect(0, 0, width, height) || area.isEmpty) area.set(0, 0, width, height)
        // A bigger area takes the screen longer to update: 300 ms, up to 550 ms for the whole canvas.
        val fraction = area.width().toFloat() * area.height() / (width.toFloat() * height)
        val resumeAfterMs = ERASE_RESUME_DELAY_MS + (ERASE_RESUME_FULL_CANVAS_EXTRA_MS * fraction).toLong()
        val generation = ++eraseCommitGeneration
        coroutineScope.launch(Dispatchers.Main.immediate) {
            try {
                withDrawingLock("erase-push") {
                    val pushed = drawCanvasToView(area, forcePost = true)
                    log.i("ERASE_COMMIT: pushed $area through the pen layer (ok=$pushed), resume in $resumeAfterMs ms")
                }
                touchHelper.isRawDrawingRenderEnabled = false
                withDrawingLock("erase-invalidate") { drawCanvasToView(area) }
                log.i("ERASE_COMMIT: pen render paused, posted while paused")
                delay(resumeAfterMs)
                // A newer erase ends this wait too: it has paused the pen again and resumes it.
                var waitedMs = 0L
                while (stylusContact.isActive && generation == eraseCommitGeneration &&
                    waitedMs < ERASE_RESUME_PEN_WAIT_MAX_MS) {
                    delay(50)
                    waitedMs += 50
                }
            } finally {
                if (generation == eraseCommitGeneration) resumePenRenderAfterErase(pageId)
            }
        }
    }

    private fun resumePenRenderAfterErase(pageId: String) {
        // A page or tab change, a popup or a text box has taken over the pen: its own flow turns
        // raw drawing back on.
        if (page.currentPageId != pageId || memoModeChanged.value != isMemosCanvas ||
            !state.isDrawing || isCanvasInputBlockedByOverlay() || !rawDrawingOpened) {
            log.i("ERASE_COMMIT: not resuming the pen render, something else owns it now")
            return
        }
        touchHelper.isRawDrawingRenderEnabled = true
        log.i("ERASE_COMMIT: pen render resumed")
    }

    // ── Memos view moves (Oct 2026, memo canvas navigation plan, Phase 0 spike) ──────────────────────
    //
    // Every move of the Memos view (two-finger drag, page map) ends in the erase finish above. The BOOX
    // pen hardware keeps its own copy of every stroke drawn live; for that copy a move is an erase of
    // every stroke plus a redraw elsewhere, and only commitErasedPage's order (push, pen render paused,
    // post, resume once the panel has settled) kept old ink from flashing back (Go 6, 27 Oct 2026).

    private var viewportMoveStartMs = 0L

    private fun canStartViewportSession(): Boolean =
        isMemosCanvas && memoModeChanged.value == isMemosCanvas && surfaceReady && rawDrawingOpened &&
            state.isDrawing && !isCanvasInputBlockedByOverlay() && !viewportMoving

    /**
     * A two-finger drag starts: the pen's render pauses (BOOX Notes pauses it when scrolling starts), so
     * the frames reach the panel, in a fast e-ink mode. False when the canvas can't move now.
     */
    fun beginViewportMove(): Boolean {
        if (!canStartViewportSession()) return false
        PanSpikeSettings.load(context)
        penRestBlitJob?.cancel()  // the first frame posts the whole page anyway
        viewportMoving = true
        viewportMoveStartMs = SystemClock.uptimeMillis()
        touchHelper.isRawDrawingRenderEnabled = false
        applyViewportFastMode(true)
        log.i("PAN_SPIKE: move begins, pen render paused, fast mode ${PanSpikeSettings.modeName()}")
        return true
    }

    /** One frame of a move or a page map jump: posts the page as it is drawn now. */
    fun showViewportFrame(): Boolean = drawCanvasToView(null)

    /** The two-finger drag ended: the whole view drawn at its place, then the erase finish brings the pen back. */
    fun endViewportMove() {
        if (!viewportMoving) return
        page.drawAreaScreenCoordinates(Rect(0, 0, page.viewWidth, page.viewHeight))
        applyViewportFastMode(false)
        commitErasedPage(Rect(0, 0, width, height))
        viewportMoving = false
        log.i("PAN_SPIKE: move ends after ${SystemClock.uptimeMillis() - viewportMoveStartMs} ms, finish started")
    }

    private fun applyViewportFastMode(on: Boolean) {
        try {
            if (on) {
                EpdController.applyTransientUpdate(
                    if (PanSpikeSettings.qualityMode) com.onyx.android.sdk.api.device.epd.UpdateMode.ANIMATION_QUALITY
                    else com.onyx.android.sdk.api.device.epd.UpdateMode.ANIMATION_X
                )
            } else {
                EpdController.clearTransientUpdate(true)
            }
        } catch (t: Throwable) {
            log.w("PAN_SPIKE: fast mode ${if (on) "on" else "off"} failed: ${t.message}")
        }
    }

    /**
     * The page map opens: the page is posted, then the pen layer goes off as under any popup, so the pen
     * and fingers can use the map and the canvas's posts reach the panel. False when it can't open now.
     */
    fun beginMapSession(): Boolean {
        if (!canStartViewportSession()) return false
        penRestBlitJob?.cancel()
        pageMapOpen.value = true
        drawCanvasToView(null)
        touchHelper.setRawDrawingEnabled(false)
        viewportMoveStartMs = SystemClock.uptimeMillis()
        log.i("PAN_SPIKE: page map opens, pen layer off")
        return true
    }

    /**
     * The page map has closed and left the screen: the pen layer comes back with its render paused (strokes
     * are recorded again at once), then the erase finish posts the page and resumes the render once the
     * panel has settled, so neither the map nor old ink gets into the pen's copy of the screen.
     */
    fun endMapSession() {
        if (!pageMapOpen.value) return
        page.drawAreaScreenCoordinates(Rect(0, 0, page.viewWidth, page.viewHeight))
        val otherPopup = eventsOverlayExpanded.value || toolbarModalVisible.value ||
            mainScreenModalVisible.value || canvasHostModalVisible.value
        val penBack = !otherPopup && state.isDrawing && memoModeChanged.value == isMemosCanvas && rawDrawingOpened
        if (penBack) {
            touchHelper.setRawDrawingEnabled(true)
            touchHelper.isRawDrawingRenderEnabled = false
            updatePenAndStroke()  // the pen's style and the eraser trail (enable forgets it)
        }
        pageMapOpen.value = false
        if (penBack) commitErasedPage(Rect(0, 0, width, height)) else drawCanvasToView(null)
        log.i("PAN_SPIKE: page map closed after ${SystemClock.uptimeMillis() - viewportMoveStartMs} ms (pen back=$penBack)")
    }

    // Unique identifier for raw drawing ownership logs
    private fun canvasOwnerId(): String {
        val tabName = if (isMemosCanvas) "Memos" else "Tasks"
        return "$tabName#${this.hashCode()}"
    }

    // Ensure only one canvas mutates Onyx raw drawing state at a time
    private suspend fun <T> withRawDrawingOwner(reason: String, block: suspend () -> T): T {
        val ownerId = canvasOwnerId()
        return rawDrawingMutex.withLock {
            if (rawDrawingOwner != null && rawDrawingOwner != ownerId) {
                log.i("RAW_OWNER: $ownerId waiting (current=$rawDrawingOwner) for $reason")
            }
            rawDrawingOwner = ownerId
            log.i("RAW_OWNER: $ownerId acquired for $reason")
            try {
                block()
            } finally {
                log.i("RAW_OWNER: $ownerId released after $reason")
                rawDrawingOwner = null
            }
        }
    }

    private fun isStartupRawDrawingCaptureBlocked(): Boolean {
        if (rawDrawingOpened) return false
        val now = SystemClock.elapsedRealtime()

        if (splashDismissedAtMs == 0L) return true
        if (now - splashDismissedAtMs < STARTUP_RAW_DRAWING_SETTLE_MS) return true

        if (ignoreWelcomePopupUntilStartupCanvasReady) return false

        if (welcomePopupVisible) return true
        if (welcomeDismissedAtMs == 0L) return true
        return now - welcomeDismissedAtMs < STARTUP_RAW_DRAWING_SETTLE_MS
    }

    private suspend fun tryActivateDeferredStartupRawDrawing(trigger: String) {
        if (!deferredStartupRawDrawingOpen || rawDrawingOpened) return
        if (isStartupRawDrawingCaptureBlocked()) return
        if (!state.isDrawing || isCanvasInputBlockedByOverlay()) return
        val isMemosModeActive = memoModeChanged.value
        val isThisCanvasActive = (isMemosModeActive == isMemosCanvas)
        if (!isThisCanvasActive) return

        val canvasName = if (isMemosCanvas) "Memos" else "Tasks"
        log.i("SPLASH_FIX[$canvasName]: activating deferred startup raw drawing (trigger=$trigger)")

        // Run the normal surface activation path first (includes onSurfaceInit + setupSurface).
        updateActiveSurface()
        delay(50)
        if (!surfaceReady || surfaceSetupInProgress) return

        // Fallback: if setup path didn't open raw drawing yet, force activation now.
        if (!rawDrawingOpened) {
            reactivateRawDrawing(clearGhosts = false)
        }

        if (rawDrawingOpened) {
            deferredStartupRawDrawingOpen = false
            notifyStartupCanvasReady()
        } else {
            log.w("SPLASH_FIX[$canvasName]: deferred activation attempt did not open raw drawing yet (trigger=$trigger)")
        }
    }

    private fun scheduleDeferredStartupRawDrawingOpen(reason: String) {
        deferredStartupRawDrawingOpen = true
        if (deferredStartupRawDrawingJob?.isActive == true) return
        deferredStartupRawDrawingJob = coroutineScope.launch(Dispatchers.Main.immediate) {
            try {
                val canvasName = if (isMemosCanvas) "Memos" else "Tasks"
                log.i("SPLASH_FIX[$canvasName]: scheduling deferred startup raw drawing (reason=$reason)")
                while (deferredStartupRawDrawingOpen && !rawDrawingOpened) {
                    tryActivateDeferredStartupRawDrawing("poll:$reason")
                    delay(100)
                }
            } finally {
                deferredStartupRawDrawingJob = null
            }
        }
    }

    fun init(): Boolean {
        log.i("Initializing Canvas")
        return try {
            pressure = OnyxCompat.getMaxTouchPressureOrDefault(pressure)

            // Ensure shared TouchHelper is ready before any callbacks or observer use
            runBlocking { ensureTouchHelper() }

            // Touches reaching the canvas view are consumed, as the removed OpenGL renderer's listener
            // did (Phase 6). In practice EditorGestureReceiver takes them first; Onyx raw drawing reads
            // the pen directly.
            setOnTouchListener { _, _ -> true }

            val surfaceCallback: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    val currentWidth = this@DrawCanvas.width
                    val currentHeight = this@DrawCanvas.height
                    if (currentWidth > 0 && currentHeight > 0) {
                        surfaceSize.value = SurfaceSize(currentWidth, currentHeight)
                        page.syncToSurfaceSize(currentWidth, currentHeight)
                    }
                    // set up the drawing surface
                    updateActiveSurface(force = true)
                    // Restore the correct stroke size and style.
                    updatePenAndStroke()
                }

                override fun surfaceChanged(
                    holder: SurfaceHolder, format: Int, width: Int, height: Int
                ) {
                    val sizeChanged = page.viewWidth != width || page.viewHeight != height
                    if (sizeChanged) cancelAndroidPen()
                    surfaceSize.value = SurfaceSize(width, height)
                    page.syncToSurfaceSize(width, height)
                    if (!sizeChanged) {
                        return
                    }

                    // Update page dimensions, redraw and refresh
                    updateActiveSurface(force = true)
                    onSurfaceChanged(this@DrawCanvas)
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    finishStylusContact()
                    val canvasName = if (isMemosCanvas) "Memos" else "Tasks"
                    surfaceReady = false
                    if (::touchHelper.isInitialized && PERF_LOGS) {
                        log.i("TOUCH_TRACE[$canvasName]: surfaceDestroyed - hashCode=${this@DrawCanvas.hashCode()}, touchHelper=${touchHelper.hashCode()}, rawDrawingOpened=$rawDrawingOpened")
                    }
                    // Keep the callback registered for surface recreation (resize/app return).
                    cancelAndroidPen()
                    // CRITICAL FIX: Always close raw drawing for THIS canvas's TouchHelper
                    // Previously checked referencedSurfaceView which is a global variable that
                    // gets overwritten by whichever canvas created its TouchHelper last.
                    // This caused orphaned TouchHelpers that corrupted the Onyx SDK state.
                    if (::touchHelper.isInitialized && rawDrawingOpened) {
                        if (PERF_LOGS) log.i("TOUCH_TRACE[$canvasName]: Closing raw drawing on surface destroy")
                        touchHelper.closeRawDrawing()
                        rawDrawingOpened = false
                    }
                    if (::touchHelper.isInitialized) {
                        onSurfaceDestroy(this@DrawCanvas, touchHelper)
                    }
                }
            }

            this.holder.addCallback(surfaceCallback)
            true
        } catch (t: Throwable) {
            startupInitializationFailed = true
            StartupDiagnostics.reportFatal(
                StartupStage.CANVAS_INIT,
                t,
                "Failed to initialize the drawing surface."
            )
            false
        }
    }

    @OptIn(FlowPreview::class)
    fun registerObservers() {
        if (startupInitializationFailed) {
            log.w("Skipping observer registration after fatal canvas startup failure")
            return
        }
        totalObserverRegistrations++
        if (PERF_LOGS) log.i("PERF_DEBUG: registerObservers() called - page=${page.currentPageId}, totalRegistrations=$totalObserverRegistrations, totalCancellations=$totalObserverCancellations")

        // PERFORMANCE FIX: Cancel any previous observers before registering new ones
        // This prevents accumulation of zombie coroutines when registerObservers is called multiple times
        cancelObservers()
        totalObserverCancellations++

        // Create a child job so we can cancel all observers together
        // SupervisorJob allows individual collectors to fail without cancelling siblings
        observerJob = SupervisorJob(coroutineScope.coroutineContext[Job])
        observerScope = CoroutineScope(coroutineScope.coroutineContext + observerJob!!)

        if (PERF_LOGS) log.i("PERF_DEBUG: Created new observerJob and observerScope")

        observerScope!!.launch {
            refreshUiImmediately.collect {
                val zoneToRedraw = Rect(0, 0, page.viewWidth, page.viewHeight)
                refreshUi(zoneToRedraw)
            }
        }

        // observe forceUpdate, takes rect in screen coordinates
        // given null it will redraw whole page
        // BE CAREFUL: partial update is not tested fairly -- might not work in some situations.
        observerScope!!.launch(Dispatchers.Main.immediate) {
            forceUpdate.collect { dirtyRectangle ->
                // On loading, make sure that the loaded strokes are visible to it.
                val zoneToRedraw = dirtyRectangle ?: Rect(0, 0, page.viewWidth, page.viewHeight)
                // Skip drawing selected text annotation (it's rendered by TextBoxOverlay)
                // Use selectedTextBoxIdForDraw (non-Compose) for reliable reading outside Compose
                val ignoredTextAnnotationIds = TextBoxState.globalSelectedTextBoxId?.let { listOf(it) } ?: emptyList()
                page.drawAreaScreenCoordinates(zoneToRedraw, ignoredTextAnnotationIds = ignoredTextAnnotationIds)
                // Finish each reset before collecting the next one. Launching child jobs here
                // let page loads overlap close/open calls with surface and tab activation.
                withContext(Dispatchers.Main.immediate) {
                    if (dirtyRectangle.isNull()) {
                        // ⚠️ NO-TOUCH ZONE: E-INK SPARKLE/GHOSTING FIX (Jan 2026)
                        // ═══════════════════════════════════════════════════════════════════════
                        // CRITICAL FIX that took months to discover. DO NOT MODIFY without
                        // understanding the full root cause documented in memory.md Section 12.8.
                        //
                        // ROOT CAUSE: Onyx's openRawDrawing() captures the current framebuffer.
                        // When switching days/tabs OR when task content changes significantly
                        // (move/delete/clear operations), the Tasks overlay (ruled lines,
                        // checkboxes, typed text) may still be visible on the EPD at capture
                        // time. These ghost pixels get "baked into" the raw drawing background,
                        // causing sparkle/jitter as stylus strokes composite with stale content.
                        //
                        // FIX (two-part, Jan 2026 enhancement):
                        // 1. Full close→open cycle forces new framebuffer capture (not just toggle)
                        // 2. Delay runs on Main thread to yield to Compose recomposition
                        //
                        // SYMPTOMS IF REMOVED: Sparkle on strokes, jittering lines, ghost ruled
                        // lines from Tasks appearing on Memos canvas after tab switch, or ghost
                        // task content appearing after move/delete operations.
                        //
                        // See: CLAUDE.md NO-TOUCH zones, memory.md 12.8, AGENTS.md
                        // ═══════════════════════════════════════════════════════════════════════
                        if (rawDrawingOpened) {
                            withRawDrawingOwner("forceUpdate-full") {
                                // A queued refresh must not take ownership from another active tab.
                                if (!surfaceReady || memoModeChanged.value != isMemosCanvas) {
                                    return@withRawDrawingOwner
                                }
                                log.i("SPARKLE_FIX: forceUpdate full page - close/open cycle for framebuffer refresh")
                                // Part 1: Close current raw drawing session
                                if (rawDrawingOpened) {
                                    touchHelper.setRawDrawingEnabled(false)
                                    touchHelper.closeRawDrawing()
                                    rawDrawingOpened = false
                                }

                                // Part 2: Blit clean bitmap and wait for Compose/EPD
                                drawCanvasToView(null)
                                // CRITICAL: Use Main thread for delay to ensure Compose finishes recomposition
                                // The old delay on Default didn't yield to Main, causing race with TasksControlColumn
                                withContext(Dispatchers.Main) {
                                    delay(100)  // Allow Compose to finish + EPD sync - DO NOT REDUCE
                                }

                                // Part 2.5: Set limit rect BEFORE reopening (LIMIT_RECT_FIX Jan 2026)
                                // ═══════════════════════════════════════════════════════════════════════
                                // BUG FIX: The Onyx SDK only applies limit rect at openRawDrawing() time.
                                // Without this, after page navigation/creation, the SDK reuses whatever
                                // cached limit rect exists from previous sessions. If that was from Tasks
                                // (with 152dp control column exclusion), strokes in the left 152dp of
                                // Memos canvas won't render.
                                //
                                // This replicates the limit rect calculation from reactivateRawDrawing().
                                // ═══════════════════════════════════════════════════════════════════════
                                val viewWidth = this@DrawCanvas.width
                                val viewHeight = this@DrawCanvas.height
                                val toolbarHeight = if (state.hasInternalToolbar && state.isToolbarOpen)
                                    convertDpToPixel(40.dp, context).toInt() else 0
                                val screenWidth = context.resources.displayMetrics.widthPixels
                                val excludeRect = if (GlobalAppSettings.current.toolbarPosition == AppSettings.Position.Top) {
                                    Rect(0, 0, screenWidth, toolbarHeight)
                                } else {
                                    Rect(0, viewHeight - toolbarHeight, screenWidth, viewHeight)
                                }
                                val controlWidthPx = convertDpToPixel(TASKS_CONTROL_COLUMN_WIDTH, context).toInt()
                                val limitRect = if (GlobalAppSettings.current.toolbarPosition == AppSettings.Position.Top) {
                                    if (!isMemosCanvas) Rect(controlWidthPx, toolbarHeight, viewWidth, viewHeight)
                                    else Rect(0, toolbarHeight, viewWidth, viewHeight)
                                } else {
                                    if (!isMemosCanvas) Rect(controlWidthPx, 0, viewWidth, viewHeight - toolbarHeight)
                                    else Rect(0, 0, viewWidth, viewHeight - toolbarHeight)
                                }
                                val canvasName = if (isMemosCanvas) "Memos" else "Tasks"
                                log.i("LIMIT_RECT_FIX[$canvasName]: forceUpdate setting limitRect=$limitRect before openRawDrawing")
                                touchHelper.setLimitRect(mutableListOf(limitRect)).setExcludeRect(listOf(excludeRect))

                                // Part 3: Reopen raw drawing to capture fresh framebuffer
                                touchHelper.openRawDrawing()
                                rawDrawingOpened = true
                                // A fresh SDK session needs the same style setup as normal activation.
                                updatePenAndStroke()
                                log.i("PAGE_RESET: reopened page=${page.currentPageId} mode=${state.mode} pen=${state.pen.penName} onMain=${Looper.getMainLooper().isCurrentThread}")
                                // OVERLAY_FIX: Keep SDK lifecycle (open) but don't enable input when overlay is blocking
                                if (state.isDrawing && memoModeChanged.value == isMemosCanvas &&
                                    !isCanvasInputBlockedByOverlay()) {
                                    touchHelper.setRawDrawingEnabled(true)
                                } else {
                                    log.i("OVERLAY_FIX[$canvasName]: forceUpdate opened raw drawing but leaving disabled - blocking overlay is open")
                                }
                            }
                        } else {
                            refreshUiSuspend()
                            tryActivateDeferredStartupRawDrawing("forceUpdate")
                        }
                    } else {
                        partialRefreshRegionOnce(this@DrawCanvas, zoneToRedraw, touchHelper)
                    }
                }
            }
        }

        // observe refreshUi
        observerScope!!.launch(Dispatchers.Default) {
            refreshUi.collect {
                refreshUiSuspend()
            }
        }

        observerScope!!.launch {
            onFocusChange.collect { hasFocus ->
                if (hasFocus) {
                    state.checkForSelectionsAndMenus()
                    updatePenAndStroke() // The setting might been changed by other app.
                    drawCanvasToView(null)
                } else {
                    isDrawing.emit(false)
                }
            }
        }
        observerScope!!.launch {
            page.zoomLevel.drop(1).collect {
                PageDataManager.setPageZoom(page.currentPageId, page.zoomLevel.value)
                updatePenAndStroke()
            }
        }

        observerScope!!.launch {
            isDrawing.collect {
                state.isDrawing = it
            }
        }


        observerScope!!.launch {
            addImageByUri.drop(1).collect { imageUri ->
                // TAB-BASED FIX: Only the active canvas should process the image
                // This prevents the inactive canvas (e.g., Tasks) from "stealing" the image event
                // when the user is actually on the Memos tab (or vice versa).
                val isMemosModeActive = memoModeChanged.value
                val isThisCanvasActive = (isMemosModeActive == isMemosCanvas)

                if (imageUri != null && isThisCanvasActive) {
                    handleImage(imageUri)
                } 
                // Note: We don't log "Image uri is empty" as it spams logs
            }
        }
        observerScope!!.launch {
            rectangleToSelectByGesture.drop(1).collect {
                if (it != null) {
                    selectRectangle(it)
                }
            }
        }

        // Handle single attachment selection (from long-press on attachment)
        observerScope!!.launch {
            selectSingleAttachment.collect { attachment ->
                selectImagesStrokesAndAttachments(
                    coroutineScope,
                    page,
                    state,
                    imagesToSelect = emptyList(),
                    strokesToSelect = emptyList(),
                    attachmentsToSelect = listOf(attachment)
                )
            }
        }

        // Handle single image selection (from long-press on image/table)
        observerScope!!.launch {
            selectSingleImage.collect { image ->
                selectImagesStrokesAndAttachments(
                    coroutineScope,
                    page,
                    state,
                    imagesToSelect = listOf(image),
                    strokesToSelect = emptyList(),
                    attachmentsToSelect = emptyList()
                )
            }
        }

        // Listen for canvas-only refresh signal (from eraser menu "Refresh Canvas")
        observerScope!!.launch {
            refreshCanvasSignal.collect {
                refreshCanvasRegion(this@DrawCanvas)
            }
        }

        observerScope!!.launch {
            restartAfterConfChange.collect {
                init()
                drawCanvasToView(null)
            }
        }

        // observe pen and stroke size
        observerScope!!.launch {
            snapshotFlow { state.pen }.drop(1).collect {
                updatePenAndStroke()
                refreshUiSuspend()
            }
        }
        observerScope!!.launch {
            snapshotFlow { state.penSettings.toMap() }.drop(1).collect {
                updatePenAndStroke()
                refreshUiSuspend()
            }
        }
        observerScope!!.launch {
            snapshotFlow { state.eraser }.drop(1).collect {
                updatePenAndStroke()
                refreshUiSuspend()
            }
        }

        // observe is drawing
        observerScope!!.launch {
            snapshotFlow { state.isDrawing }.drop(1).collect {
                // We need to close all menus
                if (it) {
//                    logCallStack("Closing all menus")
                    state.closeAllMenus()
//                    EpdController.waitForUpdateFinished() // it does not work.
                    waitForEpdRefresh()
                }
                updateIsDrawing()
            }
        }

        // observe toolbar open
        observerScope!!.launch {
            snapshotFlow { state.isToolbarOpen }.drop(1).collect {
                updateActiveSurface()
                updatePenAndStroke()
                refreshUi(null)
            }
        }

        // observe events overlay expanded - disable stylus input when overlay covers canvas
        observerScope!!.launch {
            log.i(
                "Observer for overlay input blocker starting - events=${eventsOverlayExpanded.value}, " +
                    "toolbar=${toolbarModalVisible.value}, main=${mainScreenModalVisible.value}, canvasHost=${canvasHostModalVisible.value}"
            )
            combine(
                eventsOverlayExpanded,
                toolbarModalVisible,
                mainScreenModalVisible,
                canvasHostModalVisible
            ) { isEventsExpanded, isToolbarModalVisible, isMainScreenModalVisible, isCanvasHostModalVisible ->
                isEventsExpanded || isToolbarModalVisible || isMainScreenModalVisible || isCanvasHostModalVisible
            }.collect { isInputBlocked ->
                log.i("Observer received overlayInputBlocked=$isInputBlocked, state.isDrawing=${state.isDrawing}, rawDrawingOpened=$rawDrawingOpened")
                if (isInputBlocked) {
                    log.i("Blocking overlay open - disabling raw drawing")
                    touchHelper.setRawDrawingEnabled(false)
                } else if (pageMapOpen.value || viewportMoving) {
                    // The page map's or a view move's own finish turns the pen back on, in its order
                    log.i("Blocking overlay dismissed - page map open or view moving, the pen stays as it is")
                } else if (state.isDrawing) {
                    if (!rawDrawingOpened) {
                        log.i("Blocking overlay dismissed - raw drawing not opened yet, calling updateActiveSurface")
                        updateActiveSurface()
                    } else {
                        val isMemosModeActive = memoModeChanged.value
                        val isThisCanvasActive = (isMemosModeActive == isMemosCanvas)
                        if (isThisCanvasActive) {
                            log.i("Blocking overlay dismissed - RE-ENABLING raw drawing (active canvas)")
                            touchHelper.setRawDrawingEnabled(true)
                            updatePenAndStroke()
                        } else {
                            log.i("TAB_FIX: overlay blocker - Skipping enable - inactive canvas (isMemosModeActive=$isMemosModeActive, isMemosCanvas=$isMemosCanvas)")
                        }
                    }
                }
            }
        }

        // observe mode
        observerScope!!.launch {
            snapshotFlow { getActualState().mode }.drop(1).collect {
                updatePenAndStroke()
                refreshUiSuspend()
                tryActivateDeferredStartupRawDrawing("modeChanged")
            }
        }

        // TAB-BASED FIX: Observe active TAB (not pageId) to enable/disable TouchHelper
        // This fixes the race condition where rapid day switching caused stylus input loss.
        // The old pageId-based check failed because:
        // 1. During rapid switching, activeCanvasPageId changed to new pageId
        // 2. Existing canvas observers saw mismatched pageIds and disabled raw drawing
        // 3. By the time the new canvas was ready, the cycle repeated
        // Using tab-based checking is robust because tab type doesn't change during day switching.
        observerScope!!.launch {
            memoModeChanged.collect { isMemosModeActive ->
                val tabName = if (isMemosCanvas) "Memos" else "Tasks"
                val activeTabName = if (isMemosModeActive) "Memos" else "Tasks"

                if (!surfaceReady) {
                    log.i("TAB_DEBUG: Skipping activation - surface not ready yet")
                    return@collect
                }
                // This canvas should be active if its type matches the active tab
                val isThisCanvasActive = (isMemosModeActive == isMemosCanvas)

                // FLICK FIX: Detect if this is an ACTUAL tab change vs. observer re-registration
                // StateFlow emits current value to new collectors, which happens on every day change
                // when observers are re-registered. We only need the close→open cycle when the
                // tab actually changes (limit rect differs between Memos and Tasks).
                val isActualTabChange = lastKnownMemoMode != null && lastKnownMemoMode != isMemosModeActive
                val isFirstEmission = lastKnownMemoMode == null
                lastKnownMemoMode = isMemosModeActive

                log.i("Tab mode changed: activeTab=$activeTabName, thisCanvas=$tabName, isActive=$isThisCanvasActive, isActualChange=$isActualTabChange, isFirstEmission=$isFirstEmission")

                if (isThisCanvasActive) {
                    // This canvas should be active - enable TouchHelper if in drawing mode
                    log.i("TAB_DEBUG: $tabName canvas becoming active. state.isDrawing=${state.isDrawing}, inputBlocked=${isCanvasInputBlockedByOverlay()}, eventsOverlayExpanded=${eventsOverlayExpanded.value}")
                    if (state.isDrawing && !isCanvasInputBlockedByOverlay()) {
                        // RACE CONDITION FIX: Small delay to ensure inactive canvas disables first
                        // Both observers fire simultaneously, but we need the inactive one to
                        // complete setRawDrawingEnabled(false) before we re-enable this one
                        delay(50)
                        log.i("PERF_TAP: Canvas READY for input at ${System.currentTimeMillis()} - page ${page.currentPageId}")

                        // ⚠️ NO-TOUCH ZONE: TAB SWITCH LIMIT RECT FIX (Dec 2025)
                        // Check if canvas TYPE changed (not just tab state). Tasks excludes 130dp control column.
                        // This logic fixes: (1) first tab activation, (2) switching back from Tasks to Memos.
                        // Without canvasTypeChanged check, limit rect wasn't updated on canvas TYPE changes.
                        // See: CLAUDE.md "Critical Bug Fixes" and memory.md section 14
                        val canvasTypeChanged = lastActiveCanvasWasMemos != null && lastActiveCanvasWasMemos != isMemosCanvas
                        val needsLimitRectUpdate = isActualTabChange || canvasTypeChanged || (isFirstEmission && lastActiveCanvasWasMemos == null)
                        lastActiveCanvasWasMemos = isMemosCanvas  // Update for next check - DO NOT REMOVE

                        // Force pen tool on tab switch or first activation so users always start in draw mode
                        if (needsLimitRectUpdate && state.mode != Mode.Draw) {
                            log.i("TAB_SWITCH: Forcing pen tool on tab switch (was mode=${state.mode})")
                            state.mode = Mode.Draw
                        }

                        if (needsLimitRectUpdate) {
                            // Tab change, canvas type change, or very first activation - need full close→open cycle to apply correct limit rect
                            // E-INK GHOSTING FIX (Jan 2026): Pass clearGhosts=true to clear Tasks overlay ghost pixels
                            log.i("FLICK_FIX: Limit rect update needed (tabChange=$isActualTabChange, typeChange=$canvasTypeChanged, firstEmission=$isFirstEmission) - doing full reactivateRawDrawing(clearGhosts=true)")
                            reactivateRawDrawing(clearGhosts = true)
                        } else {
                            // Observer re-registration (day change) - skip close→open cycle
                            // Just ensure raw drawing is enabled and canvas is drawn
                            log.i("FLICK_FIX: Observer re-registration (not tab change) - skipping close→open cycle")
                            // FIX: Removed forced Mode.Draw reset here to allow tool persistence across day switches
                            ensureTouchHelper()
                            if (!rawDrawingOpened) {
                                // Raw drawing not yet opened - need to open it
                                log.i("FLICK_FIX: Raw drawing not opened yet - opening without close cycle")
                                reactivateRawDrawing(clearGhosts = true)
                            } else {
                                // ⚠️ NO-TOUCH ZONE: E-INK SPARKLE/GHOSTING FIX (Jan 2026)
                                // ═══════════════════════════════════════════════════════════
                                // Part of the critical sparkle fix. On day change (same tab),
                                // raw drawing session is already open but framebuffer contains
                                // stale page content. Must regenerate bitmap and toggle raw
                                // drawing to force clean framebuffer recapture.
                                //
                                // DO NOT remove or modify without reading memory.md 12.8.
                                // SYMPTOMS IF REMOVED: Ghost strokes from previous day appear
                                // when drawing, sparkle/jitter on stylus input.
                                // ═══════════════════════════════════════════════════════════
                                log.i("SPARKLE_FIX: Day change - regenerating bitmap before drawCanvasToView")
                                val fullRect = Rect(0, 0, page.windowedBitmap.width, page.windowedBitmap.height)
                                page.drawAreaScreenCoordinates(fullRect)
                                touchHelper.setRawDrawingEnabled(false)
                                drawCanvasToView(null)
                                delay(100)  // Allow EPD to sync - DO NOT REDUCE
                                // OVERLAY_FIX: Don't re-enable if a dialog is blocking input
                                if (!isCanvasInputBlockedByOverlay()) {
                                    touchHelper.setRawDrawingEnabled(true)
                                } else {
                                    log.i("OVERLAY_FIX: memoModeChanged clearGhosts leaving raw drawing disabled - blocking overlay is open")
                                }
                            }
                        }
                        // Always reassert stroke style after activation
                        updatePenAndStroke()
                    } else {
                        log.w("TAB_DEBUG: SKIPPED reactivateRawDrawing! state.isDrawing=${state.isDrawing}, inputBlocked=${isCanvasInputBlockedByOverlay()}, eventsOverlayExpanded=${eventsOverlayExpanded.value}")
                    }
                } else {
                    // Inactive tab: still commit/cache, but KEEP raw drawing session enabled to avoid
                    // Onyx router redirects caused by rapid enable/disable churn.
                    if (PERF_LOGS) log.i("TOUCH_TRACE[$tabName]: INACTIVE - committing/caching without disabling raw drawing")
                    commitToHistory()
                    PageDataManager.cacheBitmapValidated(
                        page.currentPageId,
                        page.windowedBitmap,
                        page.viewWidth,
                        page.viewHeight
                    )
                }
            }
        }

        observerScope!!.launch {
            //After 500ms add to history strokes
            commitHistorySignal.debounce(500).collect {
                commitToHistory()
            }
        }
        observerScope!!.launch {
            commitHistorySignalImmediately.collect {
                commitToHistory()
                commitCompletion.complete(Unit)
            }
        }


            observerScope!!.launch {
                saveCurrent.collect {
                    // Push current bitmap to persist layer so preview has something to load
                    PageDataManager.cacheBitmapValidated(
                        page.currentPageId,
                        page.windowedBitmap,
                        page.viewWidth,
                        page.viewHeight
                    )
                    PageDataManager.saveTopic.tryEmit(page.currentPageId)
                }
            }

        observerScope!!.launch {
            previewPage.debounce(50).collectLatest { pageId ->
                // Day pages don't have a notebookId, use -1
                val notebookId = page.pageFromDb?.notebookId
                val pageNumber = if (notebookId != null) {
                    // PERFORMANCE FIX: Use PageView's appRepository instead of creating new instance
                    page.appRepository.getPageNumber(notebookId, pageId)
                } else -1
                Log.d("QuickNav", "Previewing page($pageNumber): $pageId")
                // Load and prepare a preview bitmap sized for the visible view area (IO thread)
                val previewBitmap = withContext(Dispatchers.IO) {
                    loadPreview(
                        context = context,
                        pageIdToLoad = pageId,
                        expectedWidth = page.viewWidth,
                        expectedHeight = page.viewHeight,
                        pageNumber = pageNumber
                    )
                }

                if (previewBitmap.isRecycled) {
                    Log.e("QuickNav", "Failed to preview page for $pageId, skipping draw")
                    return@collectLatest
                }

                val zoneToRedraw = Rect(0, 0, page.viewWidth, page.viewHeight)
                restoreCanvas(zoneToRedraw, previewBitmap)
            }
        }

        observerScope!!.launch {
            restoreCanvas.collect {
                val zoneToRedraw = Rect(0, 0, page.viewWidth, page.viewHeight)
                restoreCanvas(zoneToRedraw)
            }
        }

    }

    /**
     * handles selection, and decide if we should exit the animation mode
     */
    private suspend fun selectRectangle(rectToSelect: Rect) {
        val inPageCoordinates = toPageCoordinates(rectToSelect, page.zoomLevel.value, page.scroll)

        val imagesToSelect = PageDataManager.getImagesInRectangle(inPageCoordinates, page.currentPageId)
        val strokesToSelect = PageDataManager.getStrokesInRectangle(inPageCoordinates, page.currentPageId)
        val attachmentsToSelect = PageDataManager.getAttachmentsInRectangle(inPageCoordinates, page.currentPageId)
        if (imagesToSelect.isNotNull() && strokesToSelect.isNotNull() && attachmentsToSelect.isNotNull()) {
            rectangleToSelectByGesture.value = null
            if (imagesToSelect.isNotEmpty() || strokesToSelect.isNotEmpty() || attachmentsToSelect.isNotEmpty()) {
                selectImagesStrokesAndAttachments(coroutineScope, page, state, imagesToSelect, strokesToSelect, attachmentsToSelect)
            } else {
                com.boox.day.editor.utils.AnimationModeOwners.release(com.boox.day.editor.state.GESTURE_OWNER)
                SnackState.globalSnackFlow.emit(
                    SnackConf(
                        text = "No items in the selected area.",
                        duration = SnackConf.DURATION_QUICK,
                    )
                )
            }
        } else SnackState.globalSnackFlow.emit(
            SnackConf(
                text = "Page is empty!",
                duration = SnackConf.DURATION_QUICK,
            )
        )

    }

    private fun commitToHistory() {
        if (strokeHistoryBatch.isNotEmpty()) {
            // CROSS-DAY UNDO FIX (A1, Oct 2026): if the day/page changed within the commit delay,
            // the batch still holds strokes drawn on the previous page. Only strokes on the current
            // page may enter this page's undo history; the rest are dropped with the batch.
            val strokeIdsOnPage = page.strokes.mapTo(HashSet()) { it.id }
            val batchOnPage = strokeHistoryBatch.filter { it in strokeIdsOnPage }
            if (batchOnPage.isNotEmpty()) {
                history.addOperationsToHistory(
                    operations = listOf(
                        Operation.DeleteStroke(batchOnPage)
                    )
                )
            }
            strokeHistoryBatch.clear()
            // History changes no pixels. The pen-rest blit shows the strokes: post here only when none
            // is pending (the Android pen backend keeps its old post), never over a live stroke.
            // Its 500 ms debounce can end right after a stroke, while the user is still writing.
            val penRestBlitPending = penRestBlitJob?.isActive == true
            if (!stylusContact.isActive && (androidPenBackend || (surfaceStale && !penRestBlitPending))) {
                drawCanvasToView(null)
            }
        }
    }

    private fun refreshUi(dirtyRect: Rect?) {
        log.d("refreshUi: scroll: ${page.scroll}, zoom: ${page.zoomLevel.value}")

        if (!surfaceReady || surfaceSetupInProgress) {
            log.w("refreshUi: surface not ready (ready=$surfaceReady, setup=$surfaceSetupInProgress) - skipping draw")
            return
        }

        // post what page drawn to visible surface
        val drawSucceeded = drawCanvasToView(dirtyRect)
        if (!drawSucceeded) {
            log.w("refreshUi: draw skipped (lockCanvas null) - skip resetScreenFreeze")
            return
        }

        // Use only if you have confidence that there are no strokes being drawn at the moment
        if (!state.isDrawing) {
            log.w("Not in drawing mode, skipping unfreezing")
            return
        }

        // FIX: Skip resetScreenFreeze() if stylus is actively drawing
        // This prevents the "flick" caused by refreshUiImmediately replay or other refresh triggers
        // interrupting active drawing with isRawDrawingRenderEnabled toggle.
        if (stylusContact.isActive || drawingInProgress.isLocked) {
            log.i("FLICK_FIX: Skipping resetScreenFreeze() in refreshUi - stylus actively drawing")
            return
        }
        // A view move keeps the pen's render paused until its finish: no toggle in between
        if (viewportMoving || pageMapOpen.value) return
        // reset screen freeze
        resetScreenFreeze(touchHelper)
    }

    private suspend fun refreshUiSuspend() {
        // Do not use, if refresh need to be preformed without delay.
        // This function waits for strokes to be fully rendered.
        if (!surfaceReady || surfaceSetupInProgress) {
            log.d("refreshUiSuspend: surface not ready (ready=$surfaceReady, setup=$surfaceSetupInProgress) - skipping draw")
            return
        }
        if (!state.isDrawing) {
            waitForDrawing()
            val drawSucceeded = drawCanvasToView(null)
            if (!drawSucceeded) {
                log.w("refreshUiSuspend: draw skipped (lockCanvas null) - skip resetScreenFreeze")
                return
            }
            log.w("Not in drawing mode -- refreshUi ")
            return
        }
        if (Looper.getMainLooper().isCurrentThread) {
            log.w(
                "refreshUiSuspend() is called from the main thread."
            )
            logCallStack("refreshUiSuspend_main_thread")
        } else
            log.v(
                "refreshUiSuspend() is called from the non-main thread."
            )
        waitForDrawing()
        val drawSucceeded = drawCanvasToView(null)
        if (!drawSucceeded) {
            log.w("refreshUiSuspend: draw skipped (lockCanvas null)")
            return
        }

        // SPARKLE FIX (Dec 2025): Removed resetScreenFreeze() from this async function.
        // The toggle was firing within 1ms of new stroke starts after day switch, causing sparkle.
        // Display sync is handled by refreshUi() (which keeps its toggle) and reactivateRawDrawing().
    }

    private fun handleImage(imageUri: Uri) {
        // Convert the image to a software-backed bitmap
        val imageBitmap = uriToBitmap(context, imageUri)?.asImageBitmap()
        if (imageBitmap == null)
            showHint("There was an error during image processing.", coroutineScope)
        val softwareBitmap =
            imageBitmap?.asAndroidBitmap()?.copy(Bitmap.Config.ARGB_8888, true)
        if (softwareBitmap != null) {
            addImageByUri.value = null

            // Get the original image dimensions
            val originalWidth = softwareBitmap.width
            val originalHeight = softwareBitmap.height

            // Calculate available canvas space with margins for the mini toolbar
            // The selection toolbar appears above the selection
            val topMargin = 200  // Space for mini toolbar above the image
            val sideMargin = 20
            val bottomMargin = 20
            val availableWidth = page.viewWidth - (sideMargin * 2)
            val availableHeight = page.viewHeight - topMargin - bottomMargin

            // Calculate scale factor if image is larger than available space
            var finalWidth = originalWidth
            var finalHeight = originalHeight
            var bitmapToUse = softwareBitmap

            if (originalWidth > availableWidth || originalHeight > availableHeight) {
                val scaleX = availableWidth.toFloat() / originalWidth
                val scaleY = availableHeight.toFloat() / originalHeight
                val scale = minOf(scaleX, scaleY)

                finalWidth = (originalWidth * scale).toInt()
                finalHeight = (originalHeight * scale).toInt()

                // Scale the bitmap
                bitmapToUse = Bitmap.createScaledBitmap(softwareBitmap, finalWidth, finalHeight, true)
            }

            // Calculate position: center horizontally, but push down to leave room for toolbar at top
            val centerX = (page.viewWidth - finalWidth) / 2 + page.scroll.x.toInt()
            // Position image below the toolbar space, centered in remaining area
            val centerY = topMargin + (availableHeight - finalHeight) / 2 + page.scroll.y.toInt()
            val imageToSave = Image(
                x = centerX,
                y = centerY,
                height = finalHeight,
                width = finalWidth,
                uri = imageUri.toString(),
                pageId = page.currentPageId
            )
            drawImage(
                context, page.windowedCanvas, imageToSave, -page.scroll
            )
            selectImage(coroutineScope, page, state, imageToSave)
            // image will be added to database when released, the same as with paste element.
            state.selectionState.placementMode = PlacementMode.Paste
            // make sure, that after regaining focus, we wont go back to drawing mode
        } else {
            // Handle cases where the bitmap could not be created
            Log.e("ImageProcessing", "Failed to create software bitmap from URI.")
        }
    }


    private fun logSurfaceSkip(operation: String, detail: String) {
        val canvasName = if (isMemosCanvas) "Memos" else "Tasks"
        val surfaceValid = holder.surface.isValid
        log.w(
            "SURFACE_GUARD[$operation][$canvasName]: $detail " +
                "page=${page.currentPageId} ready=$surfaceReady setup=$surfaceSetupInProgress " +
                "valid=$surfaceValid size=${width}x${height} thread=${Thread.currentThread().name}"
        )
    }

    private fun canDrawToSurface(operation: String, allowDuringSetup: Boolean = false): Boolean {
        val surface = holder.surface
        val blockedBySetup = surfaceSetupInProgress && !allowDuringSetup
        if (!surfaceReady || blockedBySetup || !surface.isValid || width <= 0 || height <= 0) {
            logSurfaceSkip(operation, "surface unavailable")
            return false
        }
        return true
    }

    /**
     * [forcePost]: make the post reach the panel while the pen layer is on, where BOOX otherwise
     * swallows it (Go 6 log: "Post enabled!" only when the pen layer pauses). BOOX Notes' renderer
     * does this before every post (BaseRenderer.beforeUnlockCanvas: enablePost(view, 1)).
     */
    fun drawCanvasToView(dirtyRect: Rect?, allowDuringSetup: Boolean = false, forcePost: Boolean = false): Boolean {
        if (!canDrawToSurface("drawCanvasToView", allowDuringSetup)) {
            return false
        }

        // 1PX MISMATCH FIX: Use actual SurfaceView dimensions for the zone, not PageView dimensions.
        // This prevents a 1-pixel gap when the bitmap is smaller than the SurfaceView.
        // The gap would show uninitialized black pixels.
        val surfaceWidth = this.width
        val surfaceHeight = this.height
        if (surfaceWidth > 0 && surfaceHeight > 0) {
            if (page.viewWidth != surfaceWidth || page.viewHeight != surfaceHeight ||
                page.windowedBitmap.width != surfaceWidth || page.windowedBitmap.height != surfaceHeight) {
                page.syncToSurfaceSize(surfaceWidth, surfaceHeight)
            }
            if (page.viewWidth != surfaceWidth || page.viewHeight != surfaceHeight ||
                page.windowedBitmap.width != surfaceWidth || page.windowedBitmap.height != surfaceHeight) {
                return false
            }
        }
        val isFullPageRect = dirtyRect != null &&
            abs(dirtyRect.width() - page.viewWidth) <= 1 &&
            abs(dirtyRect.height() - page.viewHeight) <= 1 &&
            surfaceWidth > 0 && surfaceHeight > 0
        val zoneToRedraw = if (dirtyRect == null || isFullPageRect) {
            Rect(0, 0, surfaceWidth, surfaceHeight)
        } else {
            dirtyRect
        }

        // Lock the canvas for the full zone
        val surfaceHolder = this.holder
        val canvas = try {
            surfaceHolder.lockCanvas(zoneToRedraw)
        } catch (e: IllegalStateException) {
            logSurfaceSkip("drawCanvasToView.lockCanvas", "lockCanvas failed: ${e.message}")
            return false
        }

        if (canvas == null) {
            logSurfaceSkip("drawCanvasToView.lockCanvas", "lockCanvas returned null")
            return false
        }

        var unlockAttempted = false
        val staleGeneration = surfaceStaleGeneration
        try {
            // 1PX MISMATCH FIX: Clear canvas to white first to prevent black artifacts.
            // This ensures any gap between bitmap and surface is white, not uninitialized black pixels.
            canvas.drawColor(Color.WHITE)

            // Draw the bitmap - use its actual dimensions for the source rect
            val bitmapRect = Rect(0, 0, page.windowedBitmap.width, page.windowedBitmap.height)
            canvas.drawBitmap(page.windowedBitmap, bitmapRect, bitmapRect, Paint())

            if (getActualState().mode == Mode.Select) {
                // render selection, but only within dirtyRect
                getActualState().selectionState.firstPageCut?.let { cutPoints ->
                    log.i("render cut")
                    val path = pointsToPath(cutPoints.map {
                        SimplePointF(
                            it.x - page.scroll.x, it.y - page.scroll.y
                        )
                    })
                    canvas.drawPath(path, selectPaint)
                }
            }

            // finish rendering
            if (forcePost) EpdController.enablePost(this, 1)
            unlockAttempted = true
            surfaceHolder.unlockCanvasAndPost(canvas)
            if (zoneToRedraw.width() >= surfaceWidth && zoneToRedraw.height() >= surfaceHeight &&
                staleGeneration == surfaceStaleGeneration) {
                surfaceStale = false
            }
            return true
        } catch (e: IllegalStateException) {
            logSurfaceSkip("drawCanvasToView.post", "unlockCanvasAndPost failed: ${e.message}")
            return false
        } catch (t: Throwable) {
            log.e("drawCanvasToView failed: ${t.message}", t)
            return false
        } finally {
            if (!unlockAttempted) {
                try {
                    surfaceHolder.unlockCanvasAndPost(canvas)
                } catch (e: IllegalStateException) {
                    logSurfaceSkip("drawCanvasToView.cleanup", "cleanup unlock failed: ${e.message}")
                } catch (t: Throwable) {
                    log.e("drawCanvasToView cleanup failed: ${t.message}", t)
                }
            }
        }
    }

    private suspend fun updateIsDrawing() {
        log.i("updateIsDrawing called - state.isDrawing=${state.isDrawing}, inputBlocked=${isCanvasInputBlockedByOverlay()}, eventsOverlayExpanded=${eventsOverlayExpanded.value}")
        if (state.isDrawing) {
            // Only enable raw drawing if no blocking overlay is visible
            if (!isCanvasInputBlockedByOverlay()) {
                // TAB-BASED FIX: Only enable raw drawing if this canvas is the active one
                val isMemosModeActive = memoModeChanged.value
                val isThisCanvasActive = (isMemosModeActive == isMemosCanvas)
                if (isThisCanvasActive) {
                    log.i("updateIsDrawing - ENABLING raw drawing (active canvas)")
                    touchHelper.setRawDrawingEnabled(true)
                    // Reassert stroke style after enabling
                    updatePenAndStroke()
                } else {
                    log.i("TAB_FIX: updateIsDrawing - Skipping enable - inactive canvas (isMemosModeActive=$isMemosModeActive, isMemosCanvas=$isMemosCanvas)")
                }
            } else {
                log.i("updateIsDrawing - Skipping raw drawing enable - blocking overlay is open")
            }
        } else {
            // Check if drawing is completed
            waitForDrawing()
            // draw to view, before showing drawing, avoid stutter
            drawCanvasToView(null)
            touchHelper.setRawDrawingEnabled(false)
        }
    }

    fun updatePenAndStroke() {
        // it takes around 11 ms to run on Note 4c.
        if (PERF_LOGS) log.i("STYLE_DEBUG: updatePenAndStroke mode=${state.mode}, pen=${state.pen.penName}, eraser=${state.eraser}")
        when (state.mode) {
            // we need to change size according to zoom level before drawing on screen
            Mode.Draw, Mode.Line -> touchHelper.setStrokeStyle(penToStroke(state.pen))
                ?.setStrokeWidth(state.penSettings[state.pen.penName]!!.strokeSize * page.zoomLevel.value)
                ?.setStrokeColor(state.penSettings[state.pen.penName]!!.color)

            Mode.Erase -> setEraserPenStyle()

            Mode.Select -> touchHelper.setStrokeStyle(penToStroke(Pen.BALLPEN))?.setStrokeWidth(4f)
                ?.setStrokeColor(Color.DKGRAY)  // Darker gray for better visibility on e-ink
        }
        // The Android pen backend draws its own eraser trail (androidPenInput).
        if (!androidPenBackend) setEraserTrail(touchHelper, state.eraser)
    }

    private fun setEraserPenStyle() {
        when (state.eraser) {
            // Solid white line (Oct 2026, from the inkit test app): the ink vanishes under the
            // tip as it is erased, and the strip cut at pen-up is the same width.
            Eraser.PEN -> touchHelper.setStrokeStyle(penToStroke(Pen.BALLPEN))
                ?.setStrokeWidth(ERASER_WIDTH)
                ?.setStrokeColor(Color.WHITE)

            Eraser.SELECT -> touchHelper.setStrokeStyle(penToStroke(Pen.BALLPEN))
                ?.setStrokeWidth(3f)
                ?.setStrokeColor(Color.GRAY)
        }
    }

    /**
     * Re-activates raw drawing with the correct limit rect for this canvas.
     *
     * ONYX SDK BEHAVIOR: The limit rect is only applied at openRawDrawing() time.
     * Calling setLimitRect() on an already-open session does NOT update the limit rect.
     * Calling openRawDrawing() on an already-open session causes undefined behavior
     * and may corrupt SDK state, causing stylus input to fall through to Compose.
     *
     * SOLUTION: Always do the full close → setLimitRect → open → enable cycle.
     * This ensures the new limit rect (Memos: full width, Tasks: offset for control column)
     * is properly applied on each tab switch.
     *
     * SEQUENCE:
     * 1. setRawDrawingEnabled(false) - disable current session
     * 2. closeRawDrawing() - close current session
     * 3. setLimitRect/setExcludeRect - set new rects (pending until open)
     * 4. openRawDrawing() - open fresh session (limit rect applied HERE)
     * 5. setRawDrawingEnabled(true) - enable the new session
     *
     * @param clearGhosts If true, uses handwritingRepaint to clear ghosted overlay pixels.
     *                    Use on actual tab switches to clear Tasks ruled lines artifacts.
     *                    E-INK GHOSTING FIX (Jan 2026): The Tasks overlay is Compose-only,
     *                    but its pixels persist on the EPD panel after tab switch. Without
     *                    clearing, partial stylus updates reveal these ghost lines.
     */
    private suspend fun reactivateRawDrawing(clearGhosts: Boolean = false) {
        val canvasName = if (isMemosCanvas) "Memos" else "Tasks"
        PageDataManager.setTransitioning(true)
        try {
            ensureTouchHelper()

            if (isStartupRawDrawingCaptureBlocked()) {
                log.i("SPLASH_FIX[$canvasName]: deferring reactivateRawDrawing(clearGhosts=$clearGhosts) until startup framebuffer settles")
                scheduleDeferredStartupRawDrawingOpen("reactivate-$canvasName")
                return
            }

            val viewWidth = this.width
            val viewHeight = this.height

            if (viewWidth <= 0 || viewHeight <= 0) {
                log.w("reactivateRawDrawing: Invalid view dimensions ${viewWidth}x${viewHeight}, falling back to setRawDrawingEnabled")
                // OVERLAY_FIX: Don't enable if a dialog is blocking input
                if (!isCanvasInputBlockedByOverlay()) {
                    touchHelper.setRawDrawingEnabled(true)
                } else {
                    log.i("OVERLAY_FIX[$canvasName]: reactivateRawDrawing fallback leaving disabled - blocking overlay is open")
                }
                return
            }

            // Calculate rects same as setupSurface() in einkHelper.kt
            // NOTE: Uses VIEW-RELATIVE coordinates - screen-absolute breaks stylus completely
            val toolbarHeight = if (state.hasInternalToolbar && state.isToolbarOpen)
                convertDpToPixel(40.dp, context).toInt() else 0

            val screenWidth = context.resources.displayMetrics.widthPixels
            val excludeRect =
                if (GlobalAppSettings.current.toolbarPosition == AppSettings.Position.Top) {
                    Rect(0, 0, screenWidth, toolbarHeight)
                } else {
                    Rect(0, viewHeight - toolbarHeight, screenWidth, viewHeight)
                }

            // For Tasks tab, exclude the left control column from the raw input area
            val controlWidthPx = convertDpToPixel(TASKS_CONTROL_COLUMN_WIDTH, context).toInt()
            val limitRect = if (GlobalAppSettings.current.toolbarPosition == AppSettings.Position.Top) {
                if (!isMemosCanvas) Rect(controlWidthPx, toolbarHeight, viewWidth, viewHeight)
                else Rect(0, toolbarHeight, viewWidth, viewHeight)
            } else {
                if (!isMemosCanvas) Rect(controlWidthPx, 0, viewWidth, viewHeight - toolbarHeight)
                else Rect(0, 0, viewWidth, viewHeight - toolbarHeight)
            }

            if (PERF_LOGS) log.i("TOUCH_TRACE[$canvasName]: reactivateRawDrawing START - limitRect=$limitRect, rawDrawingOpened=$rawDrawingOpened, touchHelper=${touchHelper.hashCode()}")

            // CRITICAL FIX: Close existing session before re-opening.
            // The Onyx SDK only applies limit rect at openRawDrawing() time.
            // Calling openRawDrawing() on an already-open session is undefined behavior
            // and causes stylus input to fall through to Compose (the "Redirecting stylus input" issue).
            // We MUST do the full close → setLimitRect → open cycle to apply the new limit rect.
            try {
                withRawDrawingOwner("reactivate-$canvasName") {
                    var opId = nextOpId()

                    // Step 1: Close existing session if open
                    if (rawDrawingOpened) {
                        if (PERF_LOGS) log.i("TOUCH_TRACE[$canvasName]: [$opId] Closing existing session before reopen")
                        touchHelper.setRawDrawingEnabled(false)
                        touchHelper.closeRawDrawing()
                        rawDrawingOpened = false
                        opId = nextOpId()
                    }

                    // ⚠️ NO-TOUCH ZONE: E-INK SPARKLE/GHOSTING FIX - Step 2 (Jan 2026)
                    // ═══════════════════════════════════════════════════════════════════════════════
                    // CRITICAL FIX that took months to discover. DO NOT MODIFY without understanding
                    // the full root cause documented in memory.md Section 12.8 and CLAUDE.md.
                    //
                    // ROOT CAUSE: Onyx's openRawDrawing() captures the current EPD framebuffer state.
                    // When switching from Tasks to Memos, the Tasks overlay (Compose-rendered ruled
                    // lines, checkboxes, row highlights) may still be visible on the e-ink panel at
                    // capture time. These ghost pixels get "baked into" the raw drawing background
                    // layer, causing:
                    //   - Sparkle/jitter on stylus strokes as they composite with stale content
                    //   - Ghost ruled lines from Tasks appearing on Memos canvas
                    //   - Visual artifacts that persist until app restart
                    //
                    // FIX: BEFORE opening raw drawing, regenerate bitmap from scratch and blit to
                    // SurfaceView. The 100ms delay lets Compose finish rendering and EPD sync the
                    // clean content. This ensures openRawDrawing() captures a clean framebuffer.
                    //
                    // SYMPTOMS IF REMOVED OR MODIFIED:
                    //   - Sparkle on strokes after tab switch
                    //   - Jittering/dancing lines when drawing
                    //   - Ghost Tasks ruled lines on Memos canvas
                    //   - User-visible corruption that destroys trust in the app
                    //
                    // This fix is also applied in:
                    //   - forceUpdate observer (day change full redraw)
                    //   - memoModeChanged observer (day change with open session)
                    //
                    // See: CLAUDE.md NO-TOUCH zones, memory.md 12.8, AGENTS.md Critical Bug Fixes
                    // ═══════════════════════════════════════════════════════════════════════════════
                    if (clearGhosts) {
                        if (PERF_LOGS) log.i("TOUCH_TRACE[$canvasName]: SPARKLE_FIX - Regenerating bitmap before openRawDrawing")
                        val fullRect = Rect(0, 0, page.windowedBitmap.width, page.windowedBitmap.height)
                        page.drawAreaScreenCoordinates(fullRect)  // Regenerate bitmap with current strokes
                        drawCanvasToView(null)  // Blit to SurfaceView
                        delay(100)  // Allow Compose to complete render pass and EPD to sync - DO NOT REDUCE
                        if (PERF_LOGS) log.i("TOUCH_TRACE[$canvasName]: SPARKLE_FIX - Bitmap regenerated, proceeding with openRawDrawing")
                    }

                    // Step 3: Set new limit/exclude rects (will be applied on next open)
                    opId = nextOpId()
                    if (PERF_LOGS) log.i("TOUCH_TRACE[$canvasName]: [$opId] Calling setLimitRect($limitRect) & setExcludeRect($excludeRect)")
                    touchHelper.setLimitRect(mutableListOf(limitRect)).setExcludeRect(listOf(excludeRect))
                    if (PERF_LOGS) log.i("TOUCH_TRACE[$canvasName]: [$opId] setLimitRect/setExcludeRect completed")

                    // Step 4: Open fresh session - limit rect is applied HERE
                    opId = nextOpId()
                    if (PERF_LOGS) log.i("TOUCH_TRACE[$canvasName]: [$opId] Calling openRawDrawing()")
                    touchHelper.openRawDrawing()
                    if (PERF_LOGS) log.i("TOUCH_TRACE[$canvasName]: [$opId] openRawDrawing() completed")
                    rawDrawingOpened = true

                    // Step 5: Draw canvas content BEFORE enabling raw drawing (if not done in step 2)
                    // FIX: Drawing after setRawDrawingEnabled(true) causes the "flick" bug.
                    // The update gets queued behind the SDK's frozen layer and only becomes
                    // visible when first stylus touch triggers SDK compositing.
                    // By drawing BEFORE the freeze, the content is already visible.
                    if (!clearGhosts) {
                        if (PERF_LOGS) log.i("TOUCH_TRACE[$canvasName]: Drawing canvas content before freeze")
                        drawCanvasToView(null)
                    }

                    // ⚠️ NO-TOUCH ZONE: STALE STROKE STYLE FIX (Dec 2025 - Codex)
                    // Ensure stroke style is applied to the fresh raw drawing session.
                    // Without this, first stroke after tab switch uses stale style (gray/eraser).
                    // See: CLAUDE.md "Critical Bug Fixes" and memory.md section 14
                    updatePenAndStroke()

                    // Step 6: Enable raw drawing (freezes screen for stylus input)
                    // OVERLAY_FIX: Don't enable if a dialog is blocking input.
                    // SDK lifecycle (steps 1-5) is complete — session is open, pen is set.
                    // Overlay dismiss handler will call setRawDrawingEnabled(true) later.
                    if (!isCanvasInputBlockedByOverlay()) {
                        opId = nextOpId()
                        if (PERF_LOGS) log.i("TOUCH_TRACE[$canvasName]: [$opId] Calling setRawDrawingEnabled(true)")
                        touchHelper.setRawDrawingEnabled(true)
                        if (PERF_LOGS) log.i("TOUCH_TRACE[$canvasName]: [$opId] setRawDrawingEnabled(true) completed")
                    } else {
                        log.i("OVERLAY_FIX[$canvasName]: reactivateRawDrawing opened session but leaving disabled - blocking overlay is open")
                    }

                    if (PERF_LOGS) log.i("TOUCH_TRACE[$canvasName]: reactivateRawDrawing SUCCESS")
                }
            } catch (e: Exception) {
                log.e("TOUCH_TRACE[$canvasName]: reactivateRawDrawing FAILED: ${e.message}", e)
            }
        } finally {
            PageDataManager.setTransitioning(false)
        }
    }

    private var surfaceUpdatePending = false

    fun updateActiveSurface(force: Boolean = false) {
        if (startupInitializationFailed || StartupDiagnostics.hasFatalFailure()) {
            return
        }

        val now = System.currentTimeMillis()
        if (PERF_LOGS) log.i("PERF: updateActiveSurface called at $now")

        if (surfaceUpdateInProgress) {
            if (force) surfaceUpdatePending = true
            if (PERF_LOGS) log.i("PERF: updateActiveSurface SKIPPED (already in progress)")
            return
        }

        // FIX: Only debounce if surface was already successfully initialized.
        // If surfaceReady is false, we MUST keep trying - don't debounce.
        // This fixes a race where observer fires before surfaceCreated, sets surfaceReady=false,
        // and then surfaceCreated call gets debounced, leaving surface stuck uninitialized.
        if (!force && surfaceReady && now - lastSurfaceUpdateCompletionTime < 200) {
            if (PERF_LOGS) log.i("PERF: updateActiveSurface DEBOUNCED (last completed ${now - lastSurfaceUpdateCompletionTime}ms ago)")
            return
        }

        surfaceUpdateInProgress = true
        coroutineScope.launch {
            try {
                // FIX: Don't set surfaceReady = false here - wait until after validation.
                // Setting it too early causes race where early return leaves it stuck at false.
                surfaceSetupInProgress = true
                val isMemosModeActive = memoModeChanged.value
                val isThisCanvasActive = (isMemosModeActive == isMemosCanvas)
                val canvasName = if (isMemosCanvas) "Memos" else "Tasks"
                if (!isThisCanvasActive) {
                    log.i("TAB_FIX: Skipping updateActiveSurface - inactive canvas (activeTab=${if (isMemosModeActive) "Memos" else "Tasks"}, thisCanvas=$canvasName)")
                    rawDrawingOpened = false
                    // Don't touch surfaceReady - inactive canvas check doesn't mean surface is invalid
                    return@launch
                }
                val surface = holder.surface
                if (!surface.isValid || width <= 0 || height <= 0) {
                    log.d("updateActiveSurface: surface not ready (valid=${surface.isValid}, size=${width}x${height}) - skip setup")
                    // Don't touch surfaceReady - we'll retry when surface is valid
                    return@launch
                }

                // Validation passed - NOW mark surfaceReady = false during setup
                surfaceReady = false

                // Ensure the active canvas owns the shared TouchHelper before setup
                ensureTouchHelper()

                // STARTUP FLASH GUARD: During splash/welcome settle, skip EPD-affecting
                // surface init/open/blit work. Defer full activation until gate opens.
                if (isStartupRawDrawingCaptureBlocked()) {
                    log.i("SPLASH_FIX[$canvasName]: startup guard active - deferring onSurfaceInit/setupSurface/draw")
                    rawDrawingOpened = false
                    surfaceReady = true
                    // Keep visible canvas content while deferring raw-drawing startup activation.
                    drawCanvasToView(null, allowDuringSetup = true)
                    scheduleDeferredStartupRawDrawingOpen("updateActiveSurface-$canvasName")
                    return@launch
                }

                val startSurfaceInit = if (PERF_LOGS) System.currentTimeMillis() else 0L
                onSurfaceInit(this@DrawCanvas)
                if (PERF_LOGS) log.i("PERF: onSurfaceInit took ${System.currentTimeMillis() - startSurfaceInit}ms")

                val toolbarHeight =
                    if (state.hasInternalToolbar && state.isToolbarOpen) convertDpToPixel(40.dp, context).toInt() else 0

                val startSetup = if (PERF_LOGS) System.currentTimeMillis() else 0L
                val allowStartupRawDrawingOpen = !isStartupRawDrawingCaptureBlocked()
                withRawDrawingOwner("setupSurface-$canvasName") {
                    rawDrawingOpened = setupSurface(
                        this@DrawCanvas,
                        touchHelper,
                        toolbarHeight,
                        allowRawDrawingOpen = allowStartupRawDrawingOpen
                    )
                }
                if (!rawDrawingOpened && !isCanvasInputBlockedByOverlay() && !allowStartupRawDrawingOpen) {
                    log.i("SPLASH_FIX[$canvasName]: setupSurface deferred initial raw drawing open")
                    scheduleDeferredStartupRawDrawingOpen("setupSurface-$canvasName")
                }
                
                // ⚠️ NO-TOUCH ZONE: STALE STROKE STYLE FIX (Dec 2025 - Codex)
                // Ensure stroke style is applied after surface setup.
                // Covers app start, rotation, and background/foreground transitions.
                // See: CLAUDE.md "Critical Bug Fixes" and memory.md section 14
                if (rawDrawingOpened) {
                    updatePenAndStroke()
                }
                surfaceReady = true
                // FIX: Redraw content immediately. Fixes black screen when returning from background.
                drawCanvasToView(null, allowDuringSetup = true)
                if (rawDrawingOpened) {
                    notifyStartupCanvasReady()
                }

                if (PERF_LOGS) log.i("PERF: setupSurface took ${System.currentTimeMillis() - startSetup}ms")
                if (PERF_LOGS) log.i("setupSurface returned rawDrawingOpened=$rawDrawingOpened")
                hasCompletedInitialSurfaceSetup = true
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                if (!hasCompletedInitialSurfaceSetup) {
                    startupInitializationFailed = true
                    StartupDiagnostics.reportFatal(
                        StartupStage.CANVAS_SURFACE,
                        t,
                        "Failed to activate the first drawing surface."
                    )
                } else {
                    log.e("updateActiveSurface failed after startup: ${t.message}", t)
                }
            } finally {
                // Ensure Onyx debug overlay is always disabled after setup, even on early returns.
                if (::touchHelper.isInitialized) {
                    touchHelper.debugLog(false)
                }
                surfaceSetupInProgress = false
                lastSurfaceUpdateCompletionTime = System.currentTimeMillis()
                surfaceUpdateInProgress = false
                if (surfaceUpdatePending) {
                    surfaceUpdatePending = false
                    post { if (isAttachedToWindow) updateActiveSurface(force = true) }
                }
            }
        }
    }

    private fun restoreCanvas(dirtyRect: Rect, bitmap: Bitmap = page.windowedBitmap) {
        post {
            if (!canDrawToSurface("restoreCanvas")) {
                return@post
            }
            val holder = this@DrawCanvas.holder
            var surfaceCanvas: Canvas? = null
            var unlockAttempted = false
            try {
                surfaceCanvas = holder.lockCanvas(dirtyRect)
                if (surfaceCanvas == null) {
                    logSurfaceSkip("restoreCanvas.lockCanvas", "lockCanvas returned null")
                    return@post
                }
                // Draw the preview bitmap scaled to fit the dirty rect
                surfaceCanvas.drawBitmap(bitmap, dirtyRect, dirtyRect, null)
                unlockAttempted = true
                holder.unlockCanvasAndPost(surfaceCanvas)
            } catch (e: IllegalStateException) {
                logSurfaceSkip("restoreCanvas.post", "surface post failed: ${e.message}")
            } catch (e: Exception) {
                Log.e("DrawCanvas", "Canvas restore failed: ${e.message}", e)
            } finally {
                if (surfaceCanvas != null && !unlockAttempted) {
                    try {
                        holder.unlockCanvasAndPost(surfaceCanvas)
                    } catch (e: IllegalStateException) {
                        logSurfaceSkip("restoreCanvas.cleanup", "cleanup unlock failed: ${e.message}")
                    } catch (t: Throwable) {
                        log.e("restoreCanvas cleanup failed: ${t.message}", t)
                    }
                }
                // Trigger partial refresh
                refreshScreenRegion(this@DrawCanvas, dirtyRect)
            }
        }
    }

}
