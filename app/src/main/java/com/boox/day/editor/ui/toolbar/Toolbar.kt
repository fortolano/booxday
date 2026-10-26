package com.boox.day.editor.ui.toolbar


import androidx.compose.ui.platform.LocalView
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import com.boox.day.ui.theme.LayoutProfiles
import android.Manifest
import android.content.ClipboardManager
import com.boox.day.BuildConfig
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.boox.day.R
import com.boox.day.api.OpenRouterClient
import com.boox.day.data.AppRepository
import com.boox.day.data.db.DayPageRepository
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.data.copyImageToDatabase
import com.boox.day.data.saveBitmapToImagesFolder
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.BUTTON_SIZE
import com.boox.day.data.datastore.EditorSettingCacheManager
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.PERF_LOGS
import com.boox.day.editor.DrawCanvas
import com.boox.day.editor.EditorControlTower
import com.boox.day.editor.EditorTabManager
import com.boox.day.editor.state.EditorState
import com.boox.day.editor.state.Mode
import com.boox.day.editor.utils.Pen
import com.boox.day.editor.utils.PenSetting
import com.boox.day.editor.utils.refreshScreen
import androidx.core.content.ContextCompat
import com.boox.day.ocr.UnifiedOcrIdleProcessor
import com.boox.day.ocr.TextAnnotationIndexer
import com.boox.day.ui.dialogs.AttachmentPickerDialog
import com.boox.day.ui.dialogs.LinkFilePermissionDialog
import com.boox.day.ui.dialogs.BooxNotesSettingsDialog
import com.boox.day.ui.dialogs.ImagePickerDialog
import com.boox.day.ui.dialogs.ImagePickerPermissionDialog
import com.boox.day.ui.dialogs.NativeBackgroundDialog
import com.boox.day.ui.dialogs.TableCreationDialog
import com.boox.day.editor.drawing.TableBitmapGenerator
import com.boox.day.editor.drawing.RichTextConverter
import com.boox.day.data.model.RichTextSpanSerializer
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.FullScreenState
import com.boox.day.ui.dialogs.VoiceInputDialog
import com.boox.day.ui.dialogs.VoiceTextResultDialog
import com.boox.day.ui.dialogs.VoiceTextAction
import com.boox.day.ui.dialogs.HandwritingRecognitionDialog
import com.boox.day.ui.dialogs.AiProcessingDialog
import com.boox.day.ui.dialogs.RecognitionAction
import com.boox.day.ai.RecognitionModelConfig
import com.boox.day.ui.dialogs.SummarizationDialog
import com.boox.day.ui.dialogs.SummarizationAction
import com.boox.day.ui.dialogs.PageDataForSummarization
import com.boox.day.ui.dialogs.PageManagementDialog
import com.boox.day.ui.dialogs.TranslationDialog
import com.boox.day.ui.dialogs.TranslationAction
import com.boox.day.ui.dialogs.PageDataForTranslation
import com.boox.day.utils.TextSplitUtils
import com.boox.day.data.PageDataManager
import com.boox.day.data.db.Attachment
import com.boox.day.data.db.TextAnnotation
import com.boox.day.data.db.TextAnnotationRepository
import com.boox.day.data.db.TaskPageInfo
import com.boox.day.editor.utils.calculateStrokesBoundingBox
import com.boox.day.editor.utils.findOptimalPlacement
import com.boox.day.ui.views.EditorTab
import com.boox.day.ui.theme.EInkTheme
import compose.icons.feathericons.Type
import com.boox.day.utils.TaggedLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.boox.day.ui.showHint

private val toolbarLog = TaggedLog("Toolbar")

/**
 * Data class for task statistics shown in TaskStatsButton.
 */
data class TaskStats(
    val completedCount: Int,
    val totalWithContent: Int,
    val emptySlots: Int,
    val totalPages: Int
)

private data class HandwritingRecognitionRequest(
    val strokes: List<com.boox.day.data.db.Stroke>,
    val fromLassoSelection: Boolean
)

private data class SummarizationRequest(
    val currentPageStrokes: List<com.boox.day.data.db.Stroke>,
    val currentPageTextAnnotations: List<TextAnnotation>,
    val allPagesData: List<PageDataForSummarization>,
    val totalPages: Int
)

private data class TranslationRequest(
    val currentPageStrokes: List<com.boox.day.data.db.Stroke>,
    val currentPageTextAnnotations: List<TextAnnotation>,
    val allPagesData: List<PageDataForTranslation>,
    val totalPages: Int
)


@Composable
private fun NavChevronLeftIcon(modifier: Modifier = Modifier, color: Color = Color.Black) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.06f
        val path = Path().apply {
            moveTo(w * 0.70f, h * 0.24f)
            lineTo(w * 0.32f, h * 0.50f)
            lineTo(w * 0.70f, h * 0.76f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun NavChevronRightIcon(modifier: Modifier = Modifier, color: Color = Color.Black) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.06f
        val path = Path().apply {
            moveTo(w * 0.30f, h * 0.24f)
            lineTo(w * 0.68f, h * 0.50f)
            lineTo(w * 0.30f, h * 0.76f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
private fun activeToolbarButtonForMode(mode: Mode): String = when (mode) {
    Mode.Draw -> "draw"
    Mode.Line -> "draw"
    Mode.Erase -> "erase"
    Mode.Select -> "select"
}

fun presentlyUsedToolIcon(mode: Mode, pen: Pen): Int {
    return when (mode) {
        Mode.Draw -> {
            when (pen) {
                Pen.BALLPEN -> R.drawable.ballpen
                Pen.REDBALLPEN -> R.drawable.ballpenred
                Pen.BLUEBALLPEN -> R.drawable.ballpenblue
                Pen.GREENBALLPEN -> R.drawable.ballpengreen
                Pen.FOUNTAIN -> R.drawable.fountain
                Pen.BRUSH -> R.drawable.brush
                Pen.MARKER -> R.drawable.marker
                Pen.PENCIL -> R.drawable.pencil
            }
        }

        Mode.Erase -> R.drawable.eraser
        Mode.Select -> R.drawable.lasso
        Mode.Line -> R.drawable.line
    }
}

fun isSelected(state: EditorState, penType: Pen): Boolean {
    return if (state.mode == Mode.Draw && state.pen == penType) {
        true
    } else if (state.mode == Mode.Line && state.pen == penType) {
        true
    } else {
        false
    }
}


private val SIZES_STROKES_DEFAULT = listOf(
    R.drawable.pen_size_1_40px to 2f,
    R.drawable.pen_size_2_40px to 5f,
    R.drawable.pen_size_3_40px to 10f,
    R.drawable.pen_size_4_40px to 25f,
    R.drawable.pen_size_5_40px to 50f
)
private val SIZES_MARKER_DEFAULT = listOf(
    R.drawable.pen_size_1_40px to 20f,
    R.drawable.pen_size_2_40px to 40f,
    R.drawable.pen_size_3_40px to 60f,
    R.drawable.pen_size_4_40px to 80f,
    R.drawable.pen_size_5_40px to 100f
)

/**
 * SharedToolbar - Single toolbar for Option C unified architecture.
 * Routes actions dynamically to the active canvas (Memos or Tasks).
 *
 * PERFORMANCE: Eliminates dual-toolbar overhead by getting state/controlTower
 * dynamically from EditorTabManager. Zero sync latency, direct routing.
 */
@Composable
@ExperimentalComposeUiApi
fun SharedToolbar(
    navController: NavController,
    tabManager: EditorTabManager,
    selectedDate: String,  // BUG FIX: Required for cache invalidation on day switch
    memosPageId: String? = null, // STALENESS FIX: Needed for active staleness check
    tasksPageId: String? = null, // STALENESS FIX: Needed for active staleness check
    // Multi-page indicator: Show count in tab label when > 1 page exists
    memosPageCount: Int = 1,
    tasksPageCount: Int = 1,
    taskPageInfo: TaskPageInfo? = null,
    onTaskPageNav: ((Int) -> Unit)? = null,
    // Tasks bulk actions callbacks (consolidated with scope selection in dialog)
    onTaskMoveTasks: (() -> Unit)? = null,
    onTaskCopyTasks: (() -> Unit)? = null,
    onTaskDeleteTasks: (() -> Unit)? = null,
    onConvertHandwriting: (() -> Unit)? = null,
    // Start-the-day review (plan T4), from the task actions menu
    onStartDayReview: (() -> Unit)? = null,
    // Task page deletion (specialized logic for Task entities)
    onDeleteTaskPage: (() -> Unit)? = null,
    // Quick Add callback - finds empty row or creates new page
    onQuickAddTask: (() -> Unit)? = null,
    // AI callbacks for Tasks
    onVoiceToTask: (() -> Unit)? = null,
    onSortByPriority: (() -> Unit)? = null,
    onDailySummary: (() -> Unit)? = null,
    onWeeklySummary: (() -> Unit)? = null,
    onSmartSuggestions: (() -> Unit)? = null,
    onTaskBreakdown: (() -> Unit)? = null,
    onPlanMyDay: (() -> Unit)? = null,
    // Memos AI callback - Convert memo to tasks
    onConvertMemoToTasks: (() -> Unit)? = null,
    // Stats data for Tasks
    taskStats: TaskStats? = null,
    // Canvas height for stroke detection in task export
    canvasHeight: Float = 0f,
    // Boox Notes scan callback - triggered when permission/toggle changes      
    onBooxNotesScanNeeded: (() -> Unit)? = null,
    // Boox Notes enabled state - shared with MainScreen for sync
    booxNotesEnabled: Boolean = GlobalAppSettings.current.booxNotesEnabled,     
    onBooxNotesEnabledChanged: ((Boolean) -> Unit)? = null,
    onBooxOcrSettingsChanged: ((com.boox.day.data.datastore.OcrSettings) -> Unit)? = null,
    ocrProcessingState: UnifiedOcrIdleProcessor.ProcessingState? = null,
    ocrRepository: UnifiedOcrRepository? = null,
    // Screen refresh (moved from calendar toolbar to hamburger menu)
    onScreenRefresh: () -> Unit = {}
) {
    // PERFORMANCE: Get active state and controlTower dynamically from tabManager
    // REACTIVE: Observe all flows to trigger recomposition when canvases register
    val currentTab by tabManager.currentTab.collectAsState()
    val controlTower by tabManager.activeControlTowerFlow.collectAsState(initial = null)
    val state by tabManager.activeEditorStateFlow.collectAsState(initial = null)

    // CACHE: Remember last known good values to prevent flashing during tab switches
    // This ensures smooth transitions - skeleton only shows on true initial load
    // BUG FIX: Key by selectedDate to invalidate cache when day changes (prevents stale page indicator)
    var cachedState by remember(selectedDate) { mutableStateOf<EditorState?>(null) }
    var cachedTower by remember(selectedDate) { mutableStateOf<EditorControlTower?>(null) }

    // Update cache when we get valid values
    LaunchedEffect(state, controlTower) {
        if (state != null) cachedState = state
        if (controlTower != null) cachedTower = controlTower
    }

    // Use current values, falling back to cached values during brief transitions
    val activeState = state ?: cachedState
    val activeTower = controlTower ?: cachedTower

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pendingStorageAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingStorageAction?.invoke()
        }
        pendingStorageAction = null
    }

    fun hasLegacyStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    // If canvas not mounted yet AND no cached values, show empty toolbar skeleton
    // This only happens on true initial load, not during tab switches
    if (activeState == null || activeTower == null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height((BUTTON_SIZE + 2).dp)  // Match actual toolbar height
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(EInkTheme.Colors.primary))
            Row(
                Modifier
                    .background(EInkTheme.Toolbar.background)
                    .height(BUTTON_SIZE.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {}
        }
        return
    }

    // Observe zoom level to decide button visibility
    val zoomLevel by activeState.pageView.zoomLevel.collectAsState()

    // State for in-app image picker dialog
    var isImagePickerOpen by remember { mutableStateOf(false) }

    // State for image picker permission dialog (shown when no permission)
    var isImagePickerPermissionDialogOpen by remember { mutableStateOf(false) }

    // State for native background selection dialog
    var isNativeBackgroundDialogOpen by remember { mutableStateOf(false) }

    // State for table creation dialog
    var isTableDialogOpen by remember { mutableStateOf(false) }

    // State for attachment picker dialog
    var isAttachmentDialogOpen by remember { mutableStateOf(false) }

    // State for link file permission dialog (shown when no permission)
    var isLinkFilePermissionDialogOpen by remember { mutableStateOf(false) }



    // State for Boox Notes settings dialog (shown when B button tapped without permission)
    var isBooxNotesSettingsDialogOpen by remember { mutableStateOf(false) }

    // State for voice input dialog
    var isVoiceInputDialogOpen by remember { mutableStateOf(false) }

    // State for voice text result dialog (shows after transcription)
    var voiceResultText by remember { mutableStateOf<String?>(null) }
    var voiceResultSpans by remember { mutableStateOf<String?>(null) }

    // Unified handwriting recognition state
    // Supports both lasso selection and page-level recognition with smart model selection
    var handwritingRecognitionRequest by remember { mutableStateOf<HandwritingRecognitionRequest?>(null) }

    // Smart config selector: Digital Ink for small content, Cloud LLM for larger content
    // Threshold: 60 strokes ≈ 1 paragraph, above that use Cloud LLM for better accuracy
    val handwritingConfig = remember(handwritingRecognitionRequest) {
        val strokeCount = handwritingRecognitionRequest?.strokes?.size ?: 0
        // Without the user's own OpenRouter key, or with AI Features off, the tablet reads any length
        // (Oct 2026): the selection bar's AI Recognize stays usable and sends nothing
        if (strokeCount <= 60 || !com.boox.day.security.UserOpenRouterKey.hasKey ||
            !GlobalAppSettings.current.aiEnabled
        ) {
            RecognitionModelConfig.DIGITAL_INK  // Fast on-device for sentences/paragraphs
        } else {
            RecognitionModelConfig.DEFAULT  // Cloud LLM for dense content
        }
    }

    // State for summarization dialog
    var summarizationRequest by remember { mutableStateOf<SummarizationRequest?>(null) }

    // State for translation dialog
    var translationRequest by remember { mutableStateOf<TranslationRequest?>(null) }

    // State for AI processing dialog
    var isAiProcessing by remember { mutableStateOf(false) }

    // State for page management dialog (Memos only)
    var isPageManagementDialogOpen by remember { mutableStateOf(false) }
    // Mutual exclusivity: track which tool dropdown is open (Insert, AI, Notes)
    // Only one can be open at a time
    var activeToolDropdown by remember { mutableStateOf<String?>(null) }
    // Narrow screens keep every control at touch size and move what doesn't fit into the
    // toolbar menu (≡)
    val isCompactToolbar = remember { LayoutProfiles.isNarrowScreen }
    // Smallest gap between neighbouring controls on the bar (see ToolbarGap)
    val toolGap = if (isCompactToolbar) COMPACT_TOOL_GAP else REGULAR_TOOL_GAP
    val isToolbarModalBlockingCanvas by remember {
        derivedStateOf {
            isImagePickerOpen ||
                isImagePickerPermissionDialogOpen ||
                isNativeBackgroundDialogOpen ||
                isTableDialogOpen ||
                isAttachmentDialogOpen ||
                isLinkFilePermissionDialogOpen ||
                isBooxNotesSettingsDialogOpen ||
                isVoiceInputDialogOpen ||
                voiceResultText != null ||
                handwritingRecognitionRequest != null ||
                summarizationRequest != null ||
                translationRequest != null ||
                isAiProcessing ||
                isPageManagementDialogOpen ||
                activeToolDropdown != null
        }
    }

    LaunchedEffect(isToolbarModalBlockingCanvas) {
        DrawCanvas.toolbarModalVisible.value = isToolbarModalBlockingCanvas
    }

    DisposableEffect(Unit) {
        onDispose {
            DrawCanvas.toolbarModalVisible.value = false
        }
    }

    // Canvas conversion removed - feature disabled

    // Repository for text annotations
    val textAnnotationRepository = remember { TextAnnotationRepository(context) }

    // Repository for page quota checks (memo page limits)
    val dayPageRepository = remember { DayPageRepository(context) }

    // Listen for handwriting recognition requests from mini selection toolbar (lasso)
    LaunchedEffect(Unit) {
        DrawCanvas.openHandwritingRecognitionSignal.collect {
            val selectedStrokes = activeState.selectionState.selectedStrokes
            if (!selectedStrokes.isNullOrEmpty()) {
                toolbarLog.i("Opening handwriting recognition dialog from lasso (${selectedStrokes.size} strokes)...")
                handwritingRecognitionRequest = HandwritingRecognitionRequest(
                    strokes = selectedStrokes.toList(),
                    fromLassoSelection = true
                )
            }
        }
    }

    // on exit of toolbar, update drawing state
    LaunchedEffect(activeState.menuStates.isMenuOpen) {
        toolbarLog.i("Updating drawing state")
        activeState.checkForSelectionsAndMenus()
    }

    // Helper to persist editor settings (pen, mode, penSettings, etc.)
    fun persistEditorSettings() {
        EditorSettingCacheManager.setEditorSettings(
            context,
            EditorSettingCacheManager.EditorSettings(
                isToolbarOpen = activeState.isToolbarOpen,
                pen = activeState.pen,
                eraser = activeState.eraser,
                penSettings = activeState.penSettings,
                mode = activeState.mode
            )
        )
    }

    fun handleChangePen(pen: Pen, setActiveButton: (String?) -> Unit) {
        // Save and exit any active text box before changing pen/mode
        activeTower.saveAndExitTextBox()
        // Signal toolbar interaction (for collapsing expanded events panel)
        DrawCanvas.toolbarInteraction.tryEmit(Unit)
        // ALWAYS enable drawing when selecting a pen tool
        activeState.isDrawing = true
        setActiveButton("draw")  // Set as active button
        if (activeState.mode == Mode.Draw && activeState.pen == pen) {
            activeState.menuStates.isStrokeSelectionOpen = true
        } else {
            activeState.mode = Mode.Draw
            activeState.pen = pen
            persistEditorSettings()  // Persist pen/mode change
        }
        // VISUAL STATE FIX: Unfreeze e-ink display so button highlight renders immediately.
        // Raw drawing freeze blocks View rendering; this bypasses the 2s stylus guard.
        // Safe for drawing tools because they want raw drawing re-enabled (after 50ms).
        DrawCanvas.forceUnfreeze()
    }

    fun handleEraser(setActiveButton: (String?) -> Unit) {
        // Save and exit any active text box before changing mode
        activeTower.saveAndExitTextBox()
        // Signal toolbar interaction (for collapsing expanded events panel)
        DrawCanvas.toolbarInteraction.tryEmit(Unit)
        activeState.mode = Mode.Erase
        activeState.isDrawing = true  // Enable drawing when selecting eraser
        setActiveButton("erase")  // Set as active button
        persistEditorSettings()  // Persist mode change
        // VISUAL STATE FIX: Unfreeze e-ink display so button highlight renders immediately.
        // Raw drawing freeze blocks View rendering; this bypasses the 2s stylus guard.
        // Safe for drawing tools because they want raw drawing re-enabled (after 50ms).
        DrawCanvas.forceUnfreeze()
    }

    fun handleSelection(setActiveButton: (String?) -> Unit) {
        // Save and exit any active text box before changing mode
        activeTower.saveAndExitTextBox()
        // Signal toolbar interaction (for collapsing expanded events panel)
        DrawCanvas.toolbarInteraction.tryEmit(Unit)
        activeState.mode = Mode.Select
        activeState.isDrawing = true  // Enable drawing when selecting lasso
        setActiveButton("select")  // Set as active button
        persistEditorSettings()  // Persist mode change
        // VISUAL STATE FIX: Unfreeze e-ink display so button highlight renders immediately.
        // Raw drawing freeze blocks View rendering; this bypasses the 2s stylus guard.
        // Safe for drawing tools because they want raw drawing re-enabled (after 50ms).
        DrawCanvas.forceUnfreeze()
    }

    fun handleLine(setActiveButton: (String?) -> Unit) {
        // Save and exit any active text box before changing mode
        activeTower.saveAndExitTextBox()
        // Signal toolbar interaction (for collapsing expanded events panel)
        DrawCanvas.toolbarInteraction.tryEmit(Unit)
        activeState.mode = Mode.Line
        activeState.isDrawing = true  // Enable drawing when selecting line mode
        setActiveButton("draw")  // Line mode uses draw button
        persistEditorSettings()  // Persist mode change
        // VISUAL STATE FIX: Unfreeze e-ink display so button highlight renders immediately.
        // Raw drawing freeze blocks View rendering; this bypasses the 2s stylus guard.
        // Safe for drawing tools because they want raw drawing re-enabled (after 50ms).
        DrawCanvas.forceUnfreeze()
    }

    fun onChangeStrokeSetting(penName: String, setting: PenSetting) {
        val settings = activeState.penSettings.toMutableMap()
        settings[penName] = setting.copy()
        activeState.penSettings = settings
        persistEditorSettings()  // Persist stroke settings change
    }

    // The toolbar's dialogs, kept out of SharedToolbar's own method (see SplitOutOfSharedToolbar)
    SplitOutOfSharedToolbar {
    // In-app image picker dialog
    if (isImagePickerOpen) {
        ImagePickerDialog(
            onImageSelected = { uri ->
                scope.launch(Dispatchers.IO) {
                    try {
                        // Copy image to documents/booxdaydb/images/filename
                        val copiedFile = copyImageToDatabase(context, uri)
                        toolbarLog.i("Image was received and copied, it is now at:${copiedFile.toUri()}")
                        DrawCanvas.addImageByUri.value = copiedFile.toUri()
                    } catch (e: Exception) {
                        toolbarLog.e("ImagePicker: copy failed: ${e.message}", e)
                        showHint("Cannot import this file. It may have been deleted or is unavailable.", scope)
                    }
                }
            },
            onDismiss = { isImagePickerOpen = false }
        )
    }

    // Image picker permission dialog (shown when user taps Insert Image without permission)
    if (isImagePickerPermissionDialogOpen) {
        ImagePickerPermissionDialog(
            onDismiss = { isImagePickerPermissionDialogOpen = false },
            onPermissionGranted = {
                // Permission granted - close this dialog and open image picker
                isImagePickerPermissionDialogOpen = false
                toolbarLog.i("Permission granted, opening image picker dialog...")
                isImagePickerOpen = true
            }
        )
    }

    // Native background selection dialog
    if (isNativeBackgroundDialogOpen) {
        NativeBackgroundDialog(
            currentBackground = activeState.pageView.pageFromDb?.background ?: "blank",
            onBackgroundSelected = { background ->
                val updatedPage = activeState.pageView.pageFromDb?.copy(
                    background = background,
                    backgroundType = "native"
                )
                updatedPage?.let { activeState.pageView.updatePageSettings(it) }
                scope.launch { DrawCanvas.refreshUi.emit(Unit) }
                isNativeBackgroundDialogOpen = false
            },
            onDismiss = { isNativeBackgroundDialogOpen = false }
        )
    }

    // Table creation dialog
    if (isTableDialogOpen) {
        TableCreationDialog(
            onTableCreated = { rows, cols, borderColor, borderThickness, cellColor ->
                scope.launch(Dispatchers.IO) {
                    try {
                        // Generate table bitmap with custom border and cell settings
                        val bitmap = TableBitmapGenerator.createTableBitmap(
                            rows = rows,
                            cols = cols,
                            lineWidth = borderThickness,
                            lineColor = borderColor,
                            cellColor = cellColor
                        )
                        // Save to images folder
                        val filename = "table_${System.currentTimeMillis()}.png"
                        val file = saveBitmapToImagesFolder(context, bitmap, filename)
                        // Insert via DrawCanvas
                        DrawCanvas.addImageByUri.value = file.toUri()
                        toolbarLog.i("Table created: ${rows}x${cols}, borderColor=$borderColor, thickness=$borderThickness, cellColor=$cellColor, saved to ${file.absolutePath}")
                    } catch (e: Exception) {
                        toolbarLog.e("Failed to create table: ${e.message}", e)
                    }
                }
            },
            onDismiss = { isTableDialogOpen = false }
        )
    }

    // Attachment picker dialog
    if (isAttachmentDialogOpen) {
        AttachmentPickerDialog(
            onAttachmentSelected = { filePath, displayName, fileType ->
                // Find optimal position avoiding existing content
                val (x, y) = findOptimalPlacement(
                    page = activeState.pageView,
                    elementWidth = 340,
                    elementHeight = 130,
                    padding = 20
                )
                val attachment = Attachment(
                    x = x,
                    y = y,
                    filePath = filePath,
                    displayName = displayName,
                    fileType = fileType,
                    pageId = activeState.currentPageId
                )
                activeState.pageView.addAttachment(attachment)
                toolbarLog.i("Attachment added: $displayName ($fileType)")
                scope.launch { DrawCanvas.forceUpdate.emit(null) }
                isAttachmentDialogOpen = false
            },
            onDismiss = { isAttachmentDialogOpen = false }
        )
    }

    // Link file permission dialog (shown when user taps Link File without permission)
    if (isLinkFilePermissionDialogOpen) {
        LinkFilePermissionDialog(
            onDismiss = { isLinkFilePermissionDialogOpen = false },
            onPermissionGranted = {
                // Permission granted - close this dialog and open file picker
                isLinkFilePermissionDialogOpen = false
                toolbarLog.i("Permission granted, opening attachment picker dialog...")
                isAttachmentDialogOpen = true
            }
        )
    }

    // Boox Notes settings dialog (shown when B button tapped without permission)
    // Uses shared state from MainScreen to stay in sync with other entry points
    if (isBooxNotesSettingsDialogOpen) {
        BooxNotesSettingsDialog(
            onDismiss = { isBooxNotesSettingsDialogOpen = false },
            booxNotesEnabled = booxNotesEnabled,
            onBooxNotesEnabledChanged = { enabled ->
                // Delegate to MainScreen's handler for proper state sync       
                onBooxNotesEnabledChanged?.invoke(enabled)
            },
            onScanNeeded = onBooxNotesScanNeeded,
            onOcrSettingsChanged = onBooxOcrSettingsChanged,
            ocrProcessingState = ocrProcessingState,
            ocrRepository = ocrRepository
        )
    }

    // AI processing dialog
    AiProcessingDialog(isVisible = isAiProcessing)

    // Voice input dialog (uses Gemini for AI-enhanced transcription)
    if (isVoiceInputDialogOpen) {
        VoiceInputDialog(
            onTextReceived = { text ->
                // Fallback: plain text without formatting
                voiceResultText = text
                voiceResultSpans = null
                toolbarLog.i("Voice text received (plain): $text")
                isVoiceInputDialogOpen = false
            },
            onFormattedTextReceived = { plainText, markdownText, spansJson ->
                // AI-enhanced: formatted text with spans
                voiceResultText = plainText
                voiceResultSpans = spansJson.ifEmpty { null }
                toolbarLog.i("Voice text received (formatted): $plainText, spans: ${spansJson.take(100)}...")
                isVoiceInputDialogOpen = false
            },
            onDismiss = { isVoiceInputDialogOpen = false }
        )
    }

    // Voice text result dialog (shows after transcription)
    voiceResultText?.let { text ->
        VoiceTextResultDialog(
            transcribedText = text,
            onAction = { action ->
                when (action) {
                    is VoiceTextAction.CopyToClipboard -> {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Voice Text", action.text)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Text copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        toolbarLog.i("Voice text copied: ${action.text}")
                    }
                    is VoiceTextAction.PlaceOnCurrentPage -> {
                        val pageId = state?.currentPageId ?: return@VoiceTextResultDialog

                        // Create text annotation at a default position (top-left with margin)
                        val annotation = TextAnnotation(
                            content = action.text,
                            x = 50f,
                            y = 100f,
                            width = 600f,
                            height = 150f,
                            fontSize = 48f,
                            pageId = pageId,
                            sourceType = "voice",
                            sourceStrokeIds = null,
                            spans = voiceResultSpans,
                            sourceFormat = if (voiceResultSpans != null) "rich" else "plain"
                        )

                        // SELECT the text box immediately so toolbar appears
                        state?.textBoxState?.selectTextBox(annotation)

                        // Disable drawing to allow text box interaction
                        state?.isDrawing = false

                        // Save to database for persistence
                        scope.launch(Dispatchers.IO) {
                            textAnnotationRepository.insert(annotation)
                        }

                        // Index for semantic search
                        TextAnnotationIndexer.getInstanceOrNull()?.onAnnotationChanged(annotation)

                        // Trigger canvas refresh
                        scope.launch {
                            DrawCanvas.forceUpdate.emit(null)
                        }

                        android.widget.Toast.makeText(context, "Text placed on page", android.widget.Toast.LENGTH_SHORT).show()
                        toolbarLog.i("Voice text placed on current page: ${action.text}")
                    }
                    is VoiceTextAction.PlaceOnNewPage -> {
                        // Create new page in current day and place text there
                        val newPageId = if (state?.dayDate != null) {
                            state?.createNewDayPage()
                        } else {
                            null
                        }

                        if (newPageId != null) {
                            // Create text annotation for the new page
                            val annotation = TextAnnotation(
                                content = action.text,
                                x = 50f,
                                y = 100f,
                                width = 600f,
                                height = 150f,
                                fontSize = 48f,
                                pageId = newPageId,
                                sourceType = "voice",
                                sourceStrokeIds = null,
                                spans = voiceResultSpans,
                                sourceFormat = if (voiceResultSpans != null) "rich" else "plain"
                            )

                            // Add to PageDataManager for the new page
                            com.boox.day.data.PageDataManager.addTextAnnotation(newPageId, annotation)

                            // Save to database
                            scope.launch(Dispatchers.IO) {
                                textAnnotationRepository.insert(annotation)
                            }

                            // Index for semantic search
                            TextAnnotationIndexer.getInstanceOrNull()?.onAnnotationChanged(annotation)

                            // Navigate to new page directly (don't use goToNextDayPage
                            // which would create yet another blank page since createNewDayPage
                            // already sets openPageId to the new page)
                            state?.refreshDayPageInfo()
                            controlTower?.navigateToPage(newPageId)

                            android.widget.Toast.makeText(context, "Text placed on new page", android.widget.Toast.LENGTH_SHORT).show()
                            toolbarLog.i("Voice text placed on new page: ${action.text}")
                        } else {
                            // Fallback: copy to clipboard if not in calendar mode
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Voice Text", action.text)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, "Text copied to clipboard (not in calendar mode)", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                voiceResultText = null  // Close result dialog
                voiceResultSpans = null
            },
            onDismiss = {
                voiceResultText = null
                voiceResultSpans = null
            }
        )
    }

    // Unified handwriting recognition dialog
    // Supports both lasso selection and page-level recognition with smart model selection:
    // - ≤60 strokes: Digital Ink (fast, on-device) - good for up to ~1 paragraph
    // - >60 strokes: Cloud LLM (better accuracy for dense content)
    val handwritingRequest = handwritingRecognitionRequest
    if (handwritingRequest != null) {
        HandwritingRecognitionDialog(
            strokes = handwritingRequest.strokes,
            onAction = { action ->
                when (action) {
                    is RecognitionAction.CopyToClipboard -> {
                        // Only restore selection if this was from lasso (strokes hidden during selection)
                        if (handwritingRequest.fromLassoSelection) {
                            activeTower.applySelectionDisplace()
                        }

                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Recognized Text", action.text)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Text copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        toolbarLog.i("Recognized text copied: ${action.text}")
                    }
                    is RecognitionAction.ReplaceHandwriting -> {
                        // Calculate bounding box from the strokes being processed
                        val bounds = calculateStrokesBoundingBox(handwritingRequest.strokes)
                        val pageId = activeState.currentPageId

                        // Remove from PageDataManager first (for immediate UI update)
                        action.strokeIds.forEach { strokeId ->
                            com.boox.day.data.PageDataManager.removeStroke(pageId, strokeId)
                        }

                        // Delete the original strokes from database
                        scope.launch(Dispatchers.IO) {
                            activeState.appRepository.strokeRepository.deleteAll(action.strokeIds)
                        }

                        // Convert markdown to plain text + rich text spans
                        val (plainText, spans) = RichTextConverter.markdownToSpans(action.text)
                        val spansJson = if (spans.isNotEmpty()) {
                            RichTextSpanSerializer.encode(spans)
                        } else {
                            null
                        }

                        // Create text annotation at the stroke location with rich text formatting
                        val annotation = TextAnnotation(
                            content = plainText,
                            x = bounds.left,
                            y = bounds.top,
                            width = maxOf(bounds.width(), 200f),
                            height = maxOf(bounds.height(), 80f),
                            fontSize = 48f,
                            pageId = pageId,
                            sourceType = "handwriting",
                            sourceStrokeIds = action.strokeIds.joinToString(","),
                            spans = spansJson
                        )

                        // SELECT the text box immediately so toolbar appears
                        activeState.textBoxState.selectTextBox(annotation)

                        // CRITICAL: Disable drawing IMMEDIATELY
                        activeState.isDrawing = false

                        // Save to database for persistence
                        scope.launch(Dispatchers.IO) {
                            textAnnotationRepository.insert(annotation)
                        }

                        // Index for semantic search
                        TextAnnotationIndexer.getInstanceOrNull()?.onAnnotationChanged(annotation)

                        // Trigger canvas refresh
                        scope.launch {
                            DrawCanvas.forceUpdate.emit(null)
                        }

                        toolbarLog.i("Handwriting replaced: ${action.text}")
                    }
                    is RecognitionAction.PlaceOnCurrentPage -> {
                        // Only restore selection if this was from lasso (strokes hidden during selection)
                        if (handwritingRequest.fromLassoSelection) {
                            activeTower.applySelectionDisplace()
                        }

                        // Calculate bounding box from the strokes being processed
                        val bounds = calculateStrokesBoundingBox(handwritingRequest.strokes)
                        val pageId = activeState.currentPageId

                        // Place text BELOW the handwritten strokes
                        val textY = bounds.bottom + 20f  // 20px gap below strokes

                        // Convert markdown to plain text + rich text spans
                        val (plainText, spans) = RichTextConverter.markdownToSpans(action.text)
                        val spansJson = if (spans.isNotEmpty()) {
                            RichTextSpanSerializer.encode(spans)
                        } else {
                            null
                        }

                        // Create text annotation below the strokes with rich text formatting
                        val annotation = TextAnnotation(
                            content = plainText,
                            x = bounds.left,
                            y = textY,
                            width = maxOf(bounds.width(), 200f),
                            height = 80f,
                            fontSize = 48f,
                            pageId = pageId,
                            sourceType = "handwriting",
                            sourceStrokeIds = action.strokeIds.joinToString(","),
                            spans = spansJson
                        )

                        // SELECT the text box immediately so toolbar appears
                        activeState.textBoxState.selectTextBox(annotation)

                        // CRITICAL: Disable drawing IMMEDIATELY
                        activeState.isDrawing = false

                        // Save to database for persistence
                        scope.launch(Dispatchers.IO) {
                            textAnnotationRepository.insert(annotation)
                        }

                        // Index for semantic search
                        TextAnnotationIndexer.getInstanceOrNull()?.onAnnotationChanged(annotation)

                        // Trigger canvas refresh
                        scope.launch {
                            DrawCanvas.forceUpdate.emit(null)
                        }

                        toolbarLog.i("Text annotation created below strokes")
                    }
                }
                // Only reset selection state if this was from lasso selection
                if (handwritingRequest.fromLassoSelection) {
                    activeState.selectionState.reset()
                }
                // Clear the unified state
                handwritingRecognitionRequest = null
            },
            onDismiss = {
                handwritingRecognitionRequest = null
            },
            config = handwritingConfig  // Smart selection: Digital Ink for small, Cloud LLM for large
        )
    }

    // Summarization dialog - supports scope selection (Current Page / All Pages) and result actions
    val currentSummarizationRequest = summarizationRequest
    if (currentSummarizationRequest != null) {
        SummarizationDialog(
            currentPageStrokes = currentSummarizationRequest.currentPageStrokes,
            currentPageTextAnnotations = currentSummarizationRequest.currentPageTextAnnotations,
            allPagesData = currentSummarizationRequest.allPagesData,
            totalPages = currentSummarizationRequest.totalPages,
            onAction = { action ->
                when (action) {
                    is SummarizationAction.CopyToClipboard -> {
                        // Copy summary to clipboard
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Summary", action.summary)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Summary copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        toolbarLog.i("Summary copied to clipboard: ${action.summary.take(50)}...")
                    }
                    is SummarizationAction.PlaceOnNewPage -> {
                        // Split text if needed and place on new page(s)
                        val textChunks = TextSplitUtils.splitForPages(action.summary)

                        scope.launch(Dispatchers.IO) {
                            var firstNewPageId: String? = null

                            for ((index, chunk) in textChunks.withIndex()) {
                                // Create new page for this chunk
                                val newPageId = activeState.createNewDayPage()
                                if (newPageId != null) {
                                    if (firstNewPageId == null) firstNewPageId = newPageId

                                    // Convert markdown to plain text + spans
                                    val (plainText, spans) = RichTextConverter.markdownToSpans(chunk)
                                    val spansJson = if (spans.isNotEmpty()) {
                                        RichTextSpanSerializer.encode(spans)
                                    } else {
                                        null
                                    }

                                    // Create text annotation at top of new page
                                    val annotation = TextAnnotation(
                                        content = plainText,
                                        x = 50f,
                                        y = 50f,
                                        width = 800f,
                                        height = 600f,
                                        fontSize = 48f,
                                        pageId = newPageId,
                                        sourceType = "summarization",
                                        sourceStrokeIds = null,
                                        spans = spansJson
                                    )

                                    // Add to PageDataManager for immediate display
                                    PageDataManager.addTextAnnotation(newPageId, annotation)

                                    // Save to database
                                    textAnnotationRepository.insert(annotation)

                                    // Index for semantic search
                                    TextAnnotationIndexer.getInstanceOrNull()?.onAnnotationChanged(annotation)

                                    toolbarLog.i("Summary chunk ${index + 1}/${textChunks.size} placed on new page: $newPageId")
                                }
                            }

                            // Navigate to first new page directly (don't use goToNextDayPage
                            // which would create yet another blank page since createNewDayPage
                            // already sets openPageId to the new page)
                            if (firstNewPageId != null) {
                                withContext(Dispatchers.Main) {
                                    activeState.refreshDayPageInfo()
                                    activeTower.navigateToPage(firstNewPageId)
                                    DrawCanvas.forceUpdate.emit(null)
                                    android.widget.Toast.makeText(
                                        context,
                                        if (textChunks.size > 1) "Summary placed on ${textChunks.size} new pages"
                                        else "Summary placed on new page",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                }
                summarizationRequest = null
            },
            onDismiss = { summarizationRequest = null }
        )
    }

    // Translation dialog - supports scope selection (Current Page / All Pages) and result actions
    val currentTranslationRequest = translationRequest
    if (currentTranslationRequest != null) {
        TranslationDialog(
            currentPageStrokes = currentTranslationRequest.currentPageStrokes,
            currentPageTextAnnotations = currentTranslationRequest.currentPageTextAnnotations,
            allPagesData = currentTranslationRequest.allPagesData,
            totalPages = currentTranslationRequest.totalPages,
            onAction = { action ->
                when (action) {
                    is TranslationAction.CopyToClipboard -> {
                        // Copy translation to clipboard
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Translation", action.translation)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Translation copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        toolbarLog.i("Translation copied (${action.detectedLanguages}): ${action.translation.take(50)}...")
                    }
                    is TranslationAction.PlaceOnNewPage -> {
                        // Split text if needed and place on new page(s)
                        val textChunks = TextSplitUtils.splitForPages(action.translation)

                        scope.launch(Dispatchers.IO) {
                            var firstNewPageId: String? = null

                            for ((index, chunk) in textChunks.withIndex()) {
                                // Create new page for this chunk
                                val newPageId = activeState.createNewDayPage()
                                if (newPageId != null) {
                                    if (firstNewPageId == null) firstNewPageId = newPageId

                                    // Convert markdown to plain text + spans
                                    val (plainText, spans) = RichTextConverter.markdownToSpans(chunk)
                                    val spansJson = if (spans.isNotEmpty()) {
                                        RichTextSpanSerializer.encode(spans)
                                    } else {
                                        null
                                    }

                                    // Create text annotation at top of new page
                                    val annotation = TextAnnotation(
                                        content = plainText,
                                        x = 50f,
                                        y = 50f,
                                        width = 800f,
                                        height = 600f,
                                        fontSize = 48f,
                                        pageId = newPageId,
                                        sourceType = "translation",
                                        sourceStrokeIds = null,
                                        spans = spansJson
                                    )

                                    // Add to PageDataManager for immediate display
                                    PageDataManager.addTextAnnotation(newPageId, annotation)

                                    // Save to database
                                    textAnnotationRepository.insert(annotation)

                                    toolbarLog.i("Translation chunk ${index + 1}/${textChunks.size} placed on new page: $newPageId")
                                }
                            }

                            // Navigate to first new page directly (don't use goToNextDayPage
                            // which would create yet another blank page since createNewDayPage
                            // already sets openPageId to the new page)
                            if (firstNewPageId != null) {
                                withContext(Dispatchers.Main) {
                                    activeState.refreshDayPageInfo()
                                    activeTower.navigateToPage(firstNewPageId)
                                    DrawCanvas.forceUpdate.emit(null)
                                    android.widget.Toast.makeText(
                                        context,
                                        if (textChunks.size > 1) "Translation placed on ${textChunks.size} new pages"
                                        else "Translation placed on new page",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                }
                translationRequest = null
            },
            onDismiss = { translationRequest = null }
        )
    }
    }  // end SplitOutOfSharedToolbar

    // Toolbar is always visible (Zen mode / toolbar hiding disabled)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height((BUTTON_SIZE + 2).dp)  // 46dp: Row (44dp) + bottom border (2dp)
            .padding(bottom = 1.dp)
    ) {
            // NOTE: Top border removed - MainScreen Divider serves as the separator
            Row(
                Modifier
                    .background(EInkTheme.Toolbar.background)
                    .height(BUTTON_SIZE.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 1.dp),  // Minimal padding for maximum space
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ═══════════════════════════════════════════════════════════════
                // LEFT SECTION: Tool Groups (grouped dropdowns)
                // ═══════════════════════════════════════════════════════════════

                // 0. Memos/Tasks Mode Toggle (fixed-width dynamic text button)
                // Use collectAsState() to always stay in sync with the StateFlow
                // This replaces the manual remember + LaunchedEffect pattern which could desync
                // currentTab from tabManager above

                // Mutual exclusivity: track which mode button is active (for visual state)
                // Only one of: draw, erase, select, insert, ai, notes, menu can be dark at a time
                var activeButton by remember(activeState) {
                    mutableStateOf<String?>(activeToolbarButtonForMode(activeState.mode))
                }
                LaunchedEffect(activeState.mode, activeState.menuStates.isMenuOpen) {
                    activeButton = if (activeState.menuStates.isMenuOpen) {
                        "menu"
                    } else {
                        activeToolbarButtonForMode(activeState.mode)
                    }
                }

                // Toolbar groups (plan Phase 1.5): the Regular row calls them in the same order as
                // before; compact screens keep pen/eraser/undo/redo in the row and show the rest
                // in the "more tools" popup.
                val currentPenSetting = activeState.penSettings[activeState.pen.penName]

                val penTool: @Composable () -> Unit = {
                    DrawingToolsButton(
                        currentPen = activeState.pen,
                        currentColor = currentPenSetting?.color ?: android.graphics.Color.BLACK,
                        currentMode = activeState.mode,
                        onPenSelect = { pen ->
                            handleChangePen(pen) { activeButton = it }
                        },
                        onActivate = { activeButton = "draw" },  // Highlight button on tap
                        onMenuOpenChange = { activeState.isDrawing = !it },
                        modifier = Modifier.size(EInkTheme.Toolbar.buttonSizeLarge),  // Enlarged for touch ergonomics
                        isActive = activeButton == "draw"
                    )
                }

                val toolSettingsIndicator: @Composable () -> Unit = toolSettingsIndicator@{
                    // 2. Tool Settings Indicator (pen name, color dot, size)
                    // Clicking opens StrokeMenu for the current pen
                    var isStrokeMenuOpen by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier.width(70.dp) // Compressed for space efficiency
                    ) {
                        ToolSettingsIndicator(
                            currentPen = activeState.pen,
                            penSetting = currentPenSetting ?: return@toolSettingsIndicator,
                            onClick = { isStrokeMenuOpen = !isStrokeMenuOpen },
                            onMenuOpenChange = { activeState.isDrawing = !it }
                        )

                        if (isStrokeMenuOpen) {
                            val sizes = if (activeState.pen == Pen.MARKER) SIZES_MARKER_DEFAULT else SIZES_STROKES_DEFAULT
                            StrokeMenu(
                                value = currentPenSetting ?: return@toolSettingsIndicator,
                                onChange = { setting ->
                                    onChangeStrokeSetting(activeState.pen.penName, setting)
                                },
                                onClose = { isStrokeMenuOpen = false },
                                sizeOptions = sizes,
                                colorOptions = STROKE_MENU_COLORS
                            )
                        }
                    }
                }

                val eraserTool: @Composable () -> Unit = {
                    // 3. Eraser Tools Dropdown (eraser modes + clear/delete page)
                    EraserToolsButton(
                        currentEraser = activeState.eraser,
                        currentMode = activeState.mode,
                        scribbleToErase = GlobalAppSettings.current.scribbleToEraseEnabled,
                        onEraserSelect = { eraser ->
                            activeState.eraser = eraser
                            handleEraser { activeButton = it }
                        },
                        onScribbleToggle = { enabled ->
                            val kvProxy = com.boox.day.data.db.KvProxy(context)
                            kvProxy.setAppSettings(
                                GlobalAppSettings.current.copy(scribbleToEraseEnabled = enabled)
                            )
                        },
                        onMenuOpenChange = { activeState.isDrawing = !it },
                        modifier = Modifier.size(EInkTheme.Toolbar.buttonSizeMedium),  // Enlarged for touch
                        isActive = activeButton == "erase"
                    )
                }

                // Tool pieces (Oct 2026, layout B): the Regular row shows them through midTools in
                // the same order as before; the compact bar puts each on the bar or in More.
                val selectionTool: @Composable () -> Unit = {
                        SelectionButton(
                            isSelectionActive = activeButton == "select",
                            onLassoSelect = { handleSelection { activeButton = it } },
                            modifier = Modifier.size(EInkTheme.Toolbar.buttonSizeMedium * LocalToolbarIconScale.current)  // Enlarged for touch
                        )
                }

                val taskActionsTool: @Composable () -> Unit = {
                        // Tasks bulk actions dropdown
                        TaskBulkActionsButton(
                            onMoveTasks = { onTaskMoveTasks?.invoke() },
                            onCopyTasks = { onTaskCopyTasks?.invoke() },
                            onDeleteTasks = { onTaskDeleteTasks?.invoke() },
                            onConvertHandwriting = { onConvertHandwriting?.invoke() },
                            onStartDayReview = onStartDayReview,
                            crossOutEnabled = GlobalAppSettings.current.taskCrossOutEnabled,
                            onCrossOutToggle = { enabled ->
                                com.boox.day.data.db.KvProxy(context).setAppSettings(
                                    GlobalAppSettings.current.copy(taskCrossOutEnabled = enabled)
                                )
                            },
                            // Only disable drawing when menu opens, don't re-enable when it closes
                            onMenuOpenChange = { isOpen -> if (isOpen) activeState.isDrawing = false },
                            modifier = Modifier.size(EInkTheme.Toolbar.buttonSizeMedium * LocalToolbarIconScale.current),  // Must match other tool buttons (44dp)
                            isActive = activeButton == "taskActions",
                            onActivate = {
                                activeButton = "taskActions"
                                activeState.isDrawing = false  // Disable drawing when Task Actions is active
                                activeToolDropdown = "taskActions"
                            },
                            isOpen = activeToolDropdown == "taskActions",
                            onOpenChange = { isOpen ->
                                activeToolDropdown = if (isOpen) "taskActions" else null
                            }
                        )
                }

                // Insert actions (Oct 2026): shared by the Insert button and its page in the toolbar menu (≡)
                val onImageInsert: () -> Unit = {
                    DrawCanvas.toolbarInteraction.tryEmit(Unit)
                    val hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Environment.isExternalStorageManager()
                    } else {
                        hasLegacyStoragePermission()
                    }

                    if (hasStoragePermission) {
                        toolbarLog.i("Opening image picker dialog...")
                        isImagePickerOpen = true
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            // Show Image permission dialog
                            toolbarLog.i("Showing Image permission dialog...")
                            isImagePickerPermissionDialogOpen = true
                        } else {
                            pendingStorageAction = { isImagePickerOpen = true }
                            storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }
                }
                val onBackgroundSelect: () -> Unit = {
                    DrawCanvas.toolbarInteraction.tryEmit(Unit)
                    isNativeBackgroundDialogOpen = true
                }
                val onTableInsert: () -> Unit = {
                    toolbarLog.i("Opening table creation dialog...")
                    DrawCanvas.toolbarInteraction.tryEmit(Unit)
                    isTableDialogOpen = true
                }
                val onAttachmentInsert: () -> Unit = {
                    DrawCanvas.toolbarInteraction.tryEmit(Unit)
                    val hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Environment.isExternalStorageManager()
                    } else {
                        hasLegacyStoragePermission()
                    }

                    if (hasStoragePermission) {
                        toolbarLog.i("Opening attachment picker dialog...")
                        isAttachmentDialogOpen = true
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            // Show Link File permission dialog
                            toolbarLog.i("Showing Link File permission dialog...")
                            isLinkFilePermissionDialogOpen = true
                        } else {
                            pendingStorageAction = { isAttachmentDialogOpen = true }
                            storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }
                }
                val onTextInsert: () -> Unit = {
                    toolbarLog.i("Inserting empty text box...")
                    DrawCanvas.toolbarInteraction.tryEmit(Unit)
                    controlTower?.insertEmptyTextBox()
                }
                // Image/Link File badges only need storage permission (not Boox Notes scan)
                val insertHasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else true

                val insertTool: @Composable () -> Unit = {
                    // 5. Insert Tools Dropdown (image, background, table, attachment, text)
                    InsertToolsButton(
                        onImageInsert = onImageInsert,
                        onBackgroundSelect = onBackgroundSelect,
                        onTableInsert = onTableInsert,
                        onAttachmentInsert = onAttachmentInsert,
                        onTextInsert = onTextInsert,
                        // Only disable drawing when menu opens, don't re-enable when it closes
                        onMenuOpenChange = { isOpen -> if (isOpen) activeState.isDrawing = false },
                        modifier = Modifier.size(EInkTheme.Toolbar.buttonSizeMedium * LocalToolbarIconScale.current),  // Must match other tool buttons (44dp)
                        isActive = activeButton == "insert",
                        onActivate = {
                            activeButton = "insert"
                            activeState.isDrawing = false  // Disable drawing when Insert is active
                            activeToolDropdown = "insert"  // Explicitly set dropdown state
                        },
                        isOpen = activeToolDropdown == "insert",
                        onOpenChange = { isOpen ->
                            activeToolDropdown = if (isOpen) "insert" else null
                        },
                        hasBooxPermission = insertHasStoragePermission
                    )
                }

                // Meeting notes (Oct 2026): notes for the meeting happening now, or one of the day's events
                val onMeetingNotes: () -> Unit = {
                    DrawCanvas.toolbarInteraction.tryEmit(Unit)
                    activeTower.saveAndExitTextBox()
                    // Full screen (Oct 2026): action items can become tasks, placed on pages of 10
                    FullScreenState.exitThen { com.boox.day.meetings.MeetingNotesUi.openPicker(selectedDate) }
                }
                val meetingNotesTool: @Composable () -> Unit = {
                    ToolbarButton(
                        vectorIcon = Icons.Outlined.Groups,
                        contentDescription = "Meeting notes",
                        modifier = Modifier.size(EInkTheme.Toolbar.buttonSizeMedium * LocalToolbarIconScale.current),
                        onSelect = onMeetingNotes
                    )
                }

                // Memos AI actions (Oct 2026): shared by the AI button and its page in the toolbar menu (≡)
                // AI is on when the user hasn't turned it off (aiEnabled)
                val memosAiEnabled = GlobalAppSettings.current.aiEnabled
                val onRecognizePage: () -> Unit = {
                    val pageStrokes = activeState.pageView.strokes
                    if (pageStrokes.isEmpty()) {
                        showHint("Page is empty")
                    } else {
                        toolbarLog.i("Opening page-level handwriting recognition dialog (${pageStrokes.size} strokes)...")
                        DrawCanvas.toolbarInteraction.tryEmit(Unit)
                        handwritingRecognitionRequest = HandwritingRecognitionRequest(
                            strokes = pageStrokes.toList(),
                            fromLassoSelection = false
                        )
                    }
                }
                val onVoiceInput: () -> Unit = {
                    toolbarLog.i("Opening voice input dialog from AI menu...")
                    DrawCanvas.toolbarInteraction.tryEmit(Unit)
                    isVoiceInputDialogOpen = true
                }
                val onSummarize: () -> Unit = summarize@{
                    // AI needs the user's own OpenRouter key (Oct 2026)
                    if (!com.boox.day.ui.AiKeyGate.requireKey()) return@summarize
                    toolbarLog.i("Opening summarization dialog from AI menu...")
                    DrawCanvas.toolbarInteraction.tryEmit(Unit)
                    summarizationRequest = SummarizationRequest(
                        currentPageStrokes = activeState.pageView.strokes.toList(),
                        currentPageTextAnnotations = activeState.pageView.textAnnotations.toList(),
                        allPagesData = activeState.dayPageInfo?.pageIds?.mapIndexed { index, pageId ->
                            PageDataForSummarization(
                                pageId = pageId,
                                pageIndex = index + 1,
                                strokes = PageDataManager.getStrokes(pageId).toList(),
                                textAnnotations = PageDataManager.getTextAnnotations(pageId).toList()
                            )
                        } ?: emptyList(),
                        totalPages = activeState.dayPageInfo?.totalPages ?: 1
                    )
                }
                val onTranslate: () -> Unit = translate@{
                    if (!com.boox.day.ui.AiKeyGate.requireKey()) return@translate
                    toolbarLog.i("Opening translation dialog from AI menu...")
                    DrawCanvas.toolbarInteraction.tryEmit(Unit)
                    translationRequest = TranslationRequest(
                        currentPageStrokes = activeState.pageView.strokes.toList(),
                        currentPageTextAnnotations = activeState.pageView.textAnnotations.toList(),
                        allPagesData = activeState.dayPageInfo?.pageIds?.mapIndexed { index, pageId ->
                            PageDataForTranslation(
                                pageId = pageId,
                                pageIndex = index + 1,
                                strokes = PageDataManager.getStrokes(pageId).toList(),
                                textAnnotations = PageDataManager.getTextAnnotations(pageId).toList()
                            )
                        } ?: emptyList(),
                        totalPages = activeState.dayPageInfo?.totalPages ?: 1
                    )
                }
                val onConvertMemoToTasksAction: () -> Unit = {
                    toolbarLog.i("Opening memo to tasks dialog from AI menu...")
                    DrawCanvas.toolbarInteraction.tryEmit(Unit)
                    onConvertMemoToTasks?.invoke()
                }

                val aiTool: @Composable () -> Unit = {
                    // 6. AI Tools - dropdown menu with AI features
                    AiToolbarButton(
                        hasSelectedStrokes = !activeState.selectionState.selectedStrokes.isNullOrEmpty(),
                        hasPageContent = activeState.pageView.strokes.isNotEmpty(),
                        hasTextAnnotations = activeState.pageView.textAnnotations.isNotEmpty(),
                        enabled = memosAiEnabled,
                        onRecognizePage = onRecognizePage,
                        onVoiceInput = onVoiceInput,
                        onSummarize = onSummarize,
                        onTranslate = onTranslate,
                        onConvertMemoToTasks = onConvertMemoToTasksAction,
                        // Only disable drawing when menu opens, don't re-enable when it closes
                        // Drawing is re-enabled when user explicitly selects a drawing tool
                        onMenuOpenChange = { isOpen -> if (isOpen) activeState.isDrawing = false },
                        modifier = Modifier.size(EInkTheme.Toolbar.buttonSizeMedium * LocalToolbarIconScale.current),  // Enlarged for touch
                        isActive = activeButton == "ai",
                        onActivate = {
                            activeButton = "ai"
                            activeState.isDrawing = false  // Disable drawing when AI is active
                            activeToolDropdown = "ai"  // Explicitly set dropdown state
                        },
                        isOpen = activeToolDropdown == "ai",
                        onOpenChange = { isOpen ->
                            activeToolDropdown = if (isOpen) "ai" else null
                        }
                    )
                }

                // Boox Notes (Oct 2026): shared by the Boox Notes button and its page in the toolbar menu (≡)
                // Check if we have file access permission
                val hasBooxPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else true
                val onBooxNotesPermissionNeeded: () -> Unit = {
                    toolbarLog.i("Boox Notes permission needed, showing settings dialog...")
                    isBooxNotesSettingsDialogOpen = true
                }

                val notesTool: @Composable () -> Unit = {
                        NotesButton(
                            modifier = Modifier.size(EInkTheme.Toolbar.buttonSizeMedium * LocalToolbarIconScale.current),  // Enlarged for touch
                            hasPermission = hasBooxPermission,
                            onPermissionNeeded = onBooxNotesPermissionNeeded,
                            isActive = activeButton == "notes",
                            onActivate = {
                                activeButton = "notes"
                                activeState.isDrawing = false  // Disable drawing when Notes is active
                                activeToolDropdown = "notes"  // Explicitly set dropdown state
                            },
                            isOpen = activeToolDropdown == "notes",
                            onOpenChange = { isOpen ->
                                activeToolDropdown = if (isOpen) "notes" else null
                            },
                            // Only disable drawing when menu opens, don't re-enable when it closes
                            onMenuOpenChange = { isOpen -> if (isOpen) activeState.isDrawing = false }
                        )
                }

                // Quick Add - opens TypeTaskDialog for next empty row (or creates new page)
                val onQuickAdd: () -> Unit = {
                    activeButton = "quickAdd"  // Set button as active
                    activeState.isDrawing = false  // Disable drawing when Quick Add is active
                    onQuickAddTask?.invoke()
                }
                val quickAddTool: @Composable () -> Unit = {
                        ToolbarButton(
                            iconId = R.drawable.add_task_40px,
                            contentDescription = "Quick add task",
                            modifier = Modifier.size(EInkTheme.Toolbar.buttonSizeMedium * LocalToolbarIconScale.current),  // Must match other tool buttons (44dp)
                            isSelected = activeButton == "quickAdd",  // Show active state when button is active
                            onSelect = onQuickAdd
                        )
                }

                val taskAiTool: @Composable () -> Unit = {
                        // AI Tools for Tasks
                        TaskAiButton(
                            onVoiceToTask = { onVoiceToTask?.invoke() },
                            onSortByPriority = { onSortByPriority?.invoke() },
                            onSmartSuggestions = { onSmartSuggestions?.invoke() },
                            onTaskBreakdown = { onTaskBreakdown?.invoke() },
                            onDailySummary = { onDailySummary?.invoke() },
                            onWeeklySummary = { onWeeklySummary?.invoke() },
                            onPlanMyDay = { onPlanMyDay?.invoke() },
                            // AI is on when the user hasn't turned it off (aiEnabled)
                            enabled = GlobalAppSettings.current.aiEnabled,
                            // Only disable drawing when menu opens, don't re-enable when it closes
                            onMenuOpenChange = { isOpen -> if (isOpen) activeState.isDrawing = false },
                            modifier = Modifier.size(EInkTheme.Toolbar.buttonSizeMedium * LocalToolbarIconScale.current),  // Must match other tool buttons (44dp)
                            isActive = activeButton == "taskAi",
                            onActivate = {
                                activeButton = "taskAi"
                                activeState.isDrawing = false  // Disable drawing when Task AI is active
                                activeToolDropdown = "taskAi"
                            },
                            isOpen = activeToolDropdown == "taskAi",
                            onOpenChange = { isOpen ->
                                activeToolDropdown = if (isOpen) "taskAi" else null
                            }
                        )
                }

                // Regular bar (Oct 2026): the tools that don't fit beside page navigation and "…" go into "…"
                val barOverflow: Set<BarTool> = if (isCompactToolbar) emptySet() else {
                    val barWidth = with(LocalDensity.current) { context.resources.displayMetrics.widthPixels.toDp() }
                    regularToolbarOverflow(
                        barWidth = barWidth,
                        isMemos = currentTab == EditorTab.Memos,
                        hasPageNav = activeState.dayDate != null
                    )
                }

                // Regular bar, after the eraser: each tool follows a gap (no dividers since Oct 2026)
                val midTools: @Composable () -> Unit = {
                    // 4. Selection Button (Memos only - lasso not useful for row-based Tasks)
                    if (currentTab == EditorTab.Memos && BarTool.Lasso !in barOverflow) {
                        ToolbarGap(REGULAR_TOOL_GAP)
                        selectionTool()
                    }

                    // TASKS-ONLY: Task Actions dropdown (Clear All, Move)
                    if (currentTab == EditorTab.Tasks && BarTool.TaskActions !in barOverflow) {
                        ToolbarGap(REGULAR_TOOL_GAP)
                        taskActionsTool()
                    }

                    // OPTION C: Memos-only features (Insert, AI, Notes)
                    // PERFORMANCE: Simplifies Tasks toolbar
                    if (currentTab == EditorTab.Memos) {
                        if (BarTool.Insert !in barOverflow) { ToolbarGap(REGULAR_TOOL_GAP); insertTool() }
                        if (BarTool.Ai !in barOverflow) { ToolbarGap(REGULAR_TOOL_GAP); aiTool() }
                        if (BarTool.MeetingNotes !in barOverflow) { ToolbarGap(REGULAR_TOOL_GAP); meetingNotesTool() }
                    }

                    // TASKS-ONLY: Quick Add and AI buttons
                    if (currentTab == EditorTab.Tasks) {
                        if (BarTool.QuickAdd !in barOverflow) { ToolbarGap(REGULAR_TOOL_GAP); quickAddTool() }
                        if (BarTool.TaskAi !in barOverflow) { ToolbarGap(REGULAR_TOOL_GAP); taskAiTool() }
                    }

                    // Notes Button (Boox Notes integration), last of the tools on both tabs
                    ToolbarGap(REGULAR_TOOL_GAP)
                    notesTool()

                    // CENTER SECTION: Reset Zoom (Memos only, when zoomed)
                    // Paste button REMOVED - now accessible via two-finger tap gesture + hamburger menu
                    // ═══════════════════════════════════════════════════════════════
                    val isZoomed = zoomLevel != 1.0f

                    if (currentTab == EditorTab.Memos && isZoomed) {
                        ToolbarGap(REGULAR_TOOL_GAP)
                        // Reset Zoom button (only visible when zoomed)
                        ToolbarButton(
                            iconId = R.drawable.refresh_40px,
                            contentDescription = "reset zoom",
                            modifier = Modifier.size(BUTTON_SIZE.dp),
                            onSelect = {
                                DrawCanvas.toolbarInteraction.tryEmit(Unit)
                                activeTower.resetZoom()
                            }
                        )
                    }
                }

                val undoRedo: @Composable () -> Unit = {
                    // Undo (48dp with reduced padding for larger icon)
                    ToolbarButton(
                        modifier = Modifier.size(EInkTheme.Toolbar.buttonSizeLarge),
                        onSelect = {
                            DrawCanvas.toolbarInteraction.tryEmit(Unit)
                            activeTower.saveAndExitTextBox()
                            scope.launch { activeTower.undo() }
                        },
                        iconId = R.drawable.undo_40px,
                        contentDescription = "undo",
                        iconPadding = 4
                    )

                    ToolbarGap(toolGap)

                    // Redo (48dp with reduced padding for larger icon)
                    ToolbarButton(
                        modifier = Modifier.size(EInkTheme.Toolbar.buttonSizeLarge),
                        onSelect = {
                            DrawCanvas.toolbarInteraction.tryEmit(Unit)
                            activeTower.saveAndExitTextBox()
                            scope.launch { activeTower.redo() }
                        },
                        iconId = R.drawable.redo_40px,
                        contentDescription = "redo",
                        iconPadding = 4
                    )
                }

                val pageNav: @Composable () -> Unit = {
                    // Day page navigation (calendar mode) - Optimized for finger and stylus tapping
                    // BUG FIX: Memos and Tasks have INDEPENDENT page systems
                    // - Memos: uses activeState.dayPageInfo (DayPage entities)
                    // - Tasks: uses taskPageInfo parameter (Task entities with taskPageIndex)
                    if (activeState.dayDate != null) {
                        // Choose page info based on active tab
                        val isTasksTab = currentTab == EditorTab.Tasks
                        // Extract values from the appropriate page info type
                        val pageNumber = if (isTasksTab) {
                            (taskPageInfo?.currentIndex ?: 0) + 1
                        } else {
                            (activeState.dayPageInfo?.currentIndex ?: 0) + 1
                        }
                        val totalPages = if (isTasksTab) {
                            taskPageInfo?.totalPages ?: 1
                        } else {
                            activeState.dayPageInfo?.totalPages ?: 1
                        }
                        val navButtonHeight = BUTTON_SIZE.dp
                        // Compact bar (layout B): full 44dp finger targets; Regular keeps its 30dp arrows
                        val navChevronWidth = if (isCompactToolbar) EInkTheme.Global.touchTarget else EInkTheme.Toolbar.navChevronWidth
                        val navLabelWidth = 32.dp

                        // Left chevron - press state for visual feedback (E-Ink friendly)
                        val leftChevronInteractionSource = remember { MutableInteractionSource() }
                        val isLeftChevronPressed by leftChevronInteractionSource.collectIsPressedAsState()

                        Box(
                            modifier = Modifier
                                .height(navButtonHeight)
                                .width(navChevronWidth)
                                .clickable(
                                    indication = null,  // No ripple - E-Ink friendly
                                    interactionSource = leftChevronInteractionSource
                                ) {
                                    DrawCanvas.toolbarInteraction.tryEmit(Unit)
                                    activeTower.saveAndExitTextBox()
                                    if (isTasksTab && onTaskPageNav != null) {
                                        onTaskPageNav(-1)  // Previous page for Tasks
                                    } else {
                                        activeTower.goToPreviousDayPage()  // Memos navigation
                                    }
                                }
                                .background(
                                    color = if (isLeftChevronPressed) EInkTheme.Selection.background else Color.Transparent,
                                    shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            NavChevronLeftIcon(
                                modifier = Modifier.size(NAV_CHEVRON_ICON_SIZE),
                                color = if (isLeftChevronPressed) EInkTheme.Selection.foreground else Color.Black
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(navButtonHeight)
                                .width(navLabelWidth)
                                .then(
                                    // Only enable click for Memos tab when totalPages > 1
                                    if (!isTasksTab && totalPages > 1) {
                                        Modifier.noRippleClickable {
                                            DrawCanvas.toolbarInteraction.tryEmit(Unit)
                                            activeTower.saveAndExitTextBox()
                                            isPageManagementDialogOpen = true
                                        }
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$pageNumber/$totalPages",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Light,
                                textAlign = TextAlign.Center,
                                // Visual hint that it's clickable (underline when clickable)
                                textDecoration = if (!isTasksTab && totalPages > 1)
                                    androidx.compose.ui.text.style.TextDecoration.Underline
                                else null
                            )
                        }

                        // Right chevron - press state for visual feedback (E-Ink friendly)
                        val rightChevronInteractionSource = remember { MutableInteractionSource() }
                        val isRightChevronPressed by rightChevronInteractionSource.collectIsPressedAsState()

                        Box(
                            modifier = Modifier
                                .height(navButtonHeight)
                                .width(navChevronWidth)
                                .clickable(
                                    indication = null,  // No ripple - E-Ink friendly
                                    interactionSource = rightChevronInteractionSource
                                ) {
                                    DrawCanvas.toolbarInteraction.tryEmit(Unit)
                                    activeTower.saveAndExitTextBox()
                                    if (isTasksTab && onTaskPageNav != null) {
                                        onTaskPageNav(1)  // Next page for Tasks
                                    } else {
                                        activeTower.goToNextDayPage()  // Memos navigation
                                    }
                                }
                                .background(
                                    color = if (isRightChevronPressed) EInkTheme.Selection.background else Color.Transparent,
                                    shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            NavChevronRightIcon(
                                modifier = Modifier.size(NAV_CHEVRON_ICON_SIZE),
                                color = if (isRightChevronPressed) EInkTheme.Selection.foreground else Color.Black
                            )
                        }
                    }

                    // Book page navigation (book mode) - Async DB fetch to avoid main thread blocking
                    val bookInfo = remember { mutableStateOf<Pair<Int, Int>?>(null) } // (pageNumber, totalPages)

                    LaunchedEffect(activeState.bookId, activeState.currentPageId) {
                        if (activeState.bookId == null) {
                            bookInfo.value = null
                            return@LaunchedEffect
                        }
                        withContext(Dispatchers.IO) {
                            val book = AppRepository(context).bookRepository.getById(activeState.bookId)
                            if (book != null) {
                                val pageNum = book.pageIds.indexOf(activeState.currentPageId) + 1
                                bookInfo.value = Pair(pageNum, book.pageIds.size)
                            }
                        }
                    }

                    if (activeState.bookId != null && bookInfo.value != null) {
                        val (pageNumber, totalPageNumber) = bookInfo.value!!

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.height(35.dp).padding(10.dp, 0.dp)
                        ) {
                            Text(
                                text = "${pageNumber}/${totalPageNumber}",
                                fontWeight = FontWeight.Light,
                                modifier = Modifier.noRippleClickable {
                                    navController.navigate("books/${activeState.bookId}/pages")
                                },
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Toolbar menu (≡, Oct 2026): the tools that are not on the bar, then the page items.
                // menuMaxHeight is the space below the bar, measured where the ≡ button sits.
                var menuMaxHeight by remember { mutableStateOf(Dp.Unspecified) }
                val closeToolbarMenu: () -> Unit = {
                    activeState.menuStates.isMenuOpen = false
                    activeButton = activeToolbarButtonForMode(activeState.mode)
                }
                val pageMenuPopup: @Composable (List<ToolbarMenuTool>) -> Unit = { menuTools ->
                            ToolbarMenu(
                                navController = navController,
                                state = activeState,
                                controlTower = activeTower,
                                activeTab = currentTab,
                                taskPageInfo = taskPageInfo,
                                canvasHeight = canvasHeight,
                                tools = menuTools,
                                maxHeight = menuMaxHeight,
                                onClearPage = { activeTower.clearCurrentPage() },
                                onDeletePage = {
                                    // Tab-aware delete page: Memos uses DayPage, Tasks uses Task entities
                                    if (currentTab == EditorTab.Tasks) {
                                        onDeleteTaskPage?.invoke()
                                    } else {
                                        activeTower.deleteCurrentDayPage()
                                    }
                                },
                                onScreenRefresh = onScreenRefresh,
                                onClose = closeToolbarMenu
                            )
                }

                // Tab-like Memos/Tasks switcher
                // Fixed width with internal weight distribution for equal tab sizes
                // 152dp total = 75dp per tab + 2dp gap between (enlarged for touch ergonomics)
                Row(
                    modifier = Modifier
                        .width(EInkTheme.Toolbar.tabTotalWidth)
                        .height(BUTTON_SIZE.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Memos tab - full height when selected, shorter when unselected
                    Box(
                        modifier = Modifier
                            .then(if (currentTab == EditorTab.Memos) Modifier.fillMaxHeight() else Modifier.height((BUTTON_SIZE - 5).dp))
                            .weight(1f)
                            .noRippleClickable {
                                DrawCanvas.toolbarInteraction.tryEmit(Unit)  // Close expanded events panel
                                if (currentTab != EditorTab.Memos) {
                                    if (PERF_LOGS) {
                                        toolbarLog.i("PERF: Memos tab clicked at ${System.currentTimeMillis()}")
                                    }
                                    // STALENESS FIX: Pass current date/pageId to force reload if needed
                                    tabManager.switchToMemos(selectedDate, memosPageId)
                                    toolbarLog.i("Mode switched to: Memos")
                                }
                            }
                            .then(
                                if (currentTab == EditorTab.Memos) {
                                    Modifier.background(EInkTheme.Toolbar.buttonBackgroundSelected, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                } else {
                                    // Unselected: transparent bg + gray border to visually recede
                                    Modifier
                                        .background(Color.Transparent, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                        .border(1.dp, EInkTheme.Colors.gray, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                append("Memos")
                                if (memosPageCount > 1) {
                                    withStyle(SpanStyle(
                                        fontSize = if (currentTab == EditorTab.Memos)
                                            EInkTheme.EventsPanel.tabCountFontSizeSelected
                                        else
                                            EInkTheme.EventsPanel.tabCountFontSizeUnselected
                                    )) {
                                        append(" · $memosPageCount")
                                    }
                                }
                            },
                            fontSize = if (currentTab == EditorTab.Memos) 14.5.sp else 13.sp,
                            fontWeight = if (currentTab == EditorTab.Memos) FontWeight.Bold else FontWeight.Normal,
                            color = if (currentTab == EditorTab.Memos) EInkTheme.Toolbar.iconColorSelected else EInkTheme.Colors.gray
                        )
                    }

                    // Tasks tab - full height when selected, shorter when unselected
                    Box(
                        modifier = Modifier
                            .then(if (currentTab == EditorTab.Tasks) Modifier.fillMaxHeight() else Modifier.height((BUTTON_SIZE - 5).dp))
                            .weight(1f)
                            .noRippleClickable {
                                DrawCanvas.toolbarInteraction.tryEmit(Unit)  // Close expanded events panel
                                if (currentTab == EditorTab.Memos) {
                                    if (PERF_LOGS) {
                                        toolbarLog.i("PERF: Tasks tab clicked at ${System.currentTimeMillis()}")
                                    }
                                    // STALENESS FIX: Pass current date/pageId to force reload if needed
                                    tabManager.switchToTasks(selectedDate, tasksPageId)
                                    toolbarLog.i("Mode switched to: Tasks")
                                }
                            }
                            .then(
                                if (currentTab == EditorTab.Tasks) {
                                    Modifier.background(EInkTheme.Toolbar.buttonBackgroundSelected, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                } else {
                                    // Unselected: transparent bg + gray border to visually recede
                                    Modifier
                                        .background(Color.Transparent, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                        .border(1.dp, EInkTheme.Colors.gray, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                append("Tasks")
                                if (tasksPageCount > 1) {
                                    withStyle(SpanStyle(
                                        fontSize = if (currentTab == EditorTab.Tasks)
                                            EInkTheme.EventsPanel.tabCountFontSizeSelected
                                        else
                                            EInkTheme.EventsPanel.tabCountFontSizeUnselected
                                    )) {
                                        append(" · $tasksPageCount")
                                    }
                                }
                            },
                            fontSize = if (currentTab == EditorTab.Tasks) 14.5.sp else 13.sp,
                            fontWeight = if (currentTab == EditorTab.Tasks) FontWeight.Bold else FontWeight.Normal,
                            color = if (currentTab == EditorTab.Tasks) EInkTheme.Toolbar.iconColorSelected else EInkTheme.Colors.gray
                        )
                    }
                }

                // Every control on the bar is followed by a gap (ToolbarGap, Oct 2026): at least toolGap
                // plus an equal share of the free width, so the controls spread evenly across the bar.
                // No dividers: too thin to see on black-and-white and Kaleido 3 screens.
                val hasPageNav = activeState.dayDate != null
                ToolbarGap(toolGap)

                // Compact bar (Oct 2026, layout B): the optional tools that fit beside the fixed ones at
                // this screen width; the rest go to the toolbar menu (≡). Priority: pen size & color, then
                // Boox Notes / task actions, then lasso / quick add. Page navigation always stays on the bar.
                val toolbarHostView = LocalView.current  // the screen, for the toolbar menu's height
                val compactFit = if (isCompactToolbar) {
                    val barWidth = with(LocalDensity.current) { context.resources.displayMetrics.widthPixels.toDp() }
                    compactToolbarFit(
                        barWidth = barWidth,
                        hasPageNav = hasPageNav,
                        hasPenSettings = currentPenSetting != null
                    )
                } else {
                    CompactToolbarFit.ALL
                }

                penTool()

                if (!isCompactToolbar) {
                    // Same early exit as before when the current pen has no settings
                    if (currentPenSetting == null) return
                    ToolbarGap(toolGap)
                    toolSettingsIndicator()
                } else if (compactFit.penSettings && currentPenSetting != null) {
                    ToolbarGap(toolGap)
                    toolSettingsIndicator()
                }

                ToolbarGap(toolGap)
                eraserTool()

                if (!isCompactToolbar) {
                    midTools()
                } else {
                    // Memos: lasso + Boox Notes; Tasks: quick add + task actions; then page navigation
                    // (a finger-width gap between neighbours so one tap never hits two)
                    if (compactFit.first) {
                        ToolbarGap(toolGap)
                        if (currentTab == EditorTab.Memos) selectionTool() else quickAddTool()
                    }
                    if (compactFit.second) {
                        ToolbarGap(toolGap)
                        if (currentTab == EditorTab.Memos) notesTool() else taskActionsTool()
                    }
                    if (hasPageNav) ToolbarGap(toolGap)
                    pageNav()
                }

                // Debug builds (Oct 2026): warn when the bar's controls don't fit — ≡ is the last
                // control, so it is the first one squeezed
                val toolbarDensity = LocalDensity.current
                val moreButtonPx = with(toolbarDensity) { EInkTheme.Toolbar.buttonSizeLarge.roundToPx() }

                // ═══════════════════════════════════════════════════════════════
                // RIGHT SECTION: Edit Controls + Navigation (ultra-compact)
                // ═══════════════════════════════════════════════════════════════

                ToolbarGap(toolGap)
                undoRedo()

                // Regular bar: page navigation at the end, before ≡ (the compact bar has it beside the tools)
                if (!isCompactToolbar) {
                    if (hasPageNav) ToolbarGap(toolGap)
                    pageNav()
                }

                // Full screen switch, just before ≡ (Oct 2026, FullScreenButton.kt); in ≡ when the compact
                // bar has no room for it
                if (compactFit.fullScreen) {
                    ToolbarGap(toolGap)
                    FullScreenToolbarButton()
                }

                // Toolbar menu (≡) at the end of both bars (Oct 2026; replaces "…", which opened a row of
                // icons and a second menu): the tools above, with labels, then the page items
                ToolbarGap(toolGap)
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            // The menu opens PopupMenu.offsetY below the top of this button and may use
                            // the rest of the screen below it
                            val top = coords.positionInWindow().y
                            val space = with(toolbarDensity) { (toolbarHostView.height - top).toDp() } -
                                EInkTheme.PopupMenu.offsetY - MENU_BOTTOM_MARGIN
                            if (space != menuMaxHeight) menuMaxHeight = space
                        }
                        .then(
                            if (BuildConfig.DEBUG) Modifier.onSizeChanged { size ->
                                if (size.width < moreButtonPx) {
                                    android.util.Log.w(
                                        "Toolbar",
                                        "Toolbar wider than the screen: the menu button squeezed to ${with(toolbarDensity) { size.width.toDp() }} " +
                                            "— move a tool into the menu (regularToolbarOverflow / compactToolbarFit)"
                                    )
                                }
                            } else Modifier
                        )
                ) {
                    ToolbarButton(
                        onSelect = {
                            val isOpeningMenu = !activeState.menuStates.isMenuOpen
                            activeButton = if (isOpeningMenu) "menu" else activeToolbarButtonForMode(activeState.mode)
                            if (isOpeningMenu) {
                                activeState.isDrawing = false  // Disable drawing when menu is active
                                activeToolDropdown = null  // One menu at a time
                            }
                            activeState.menuStates.isMenuOpen = isOpeningMenu
                        },
                        iconId = R.drawable.menu_40px,
                        isSelected = activeButton == "menu",
                        contentDescription = "Menu",
                        // Larger than the other tools: it's at the screen edge and opens everything else
                        modifier = Modifier.size(EInkTheme.Toolbar.buttonSizeLarge),
                        iconSize = MENU_BUTTON_ICON_SIZE,
                        iconPadding = 4
                    )
                    // The menu's tools are worked out only while it is open
                    if (activeState.menuStates.isMenuOpen) {
                        pageMenuPopup(
                            toolbarMenuTools(
                                context = context,
                                state = activeState,
                                isMemosTab = currentTab == EditorTab.Memos,
                                isCompactToolbar = isCompactToolbar,
                                compactFit = compactFit,
                                barOverflow = barOverflow,
                                penSetting = currentPenSetting,
                                onChangePenSetting = { setting -> onChangeStrokeSetting(activeState.pen.penName, setting) },
                                onLasso = { handleSelection { activeButton = it } },
                                onImageInsert = onImageInsert,
                                onBackgroundSelect = onBackgroundSelect,
                                onTableInsert = onTableInsert,
                                onAttachmentInsert = onAttachmentInsert,
                                onTextInsert = onTextInsert,
                                insertHasStoragePermission = insertHasStoragePermission,
                                memosAiEnabled = memosAiEnabled,
                                onRecognizePage = onRecognizePage,
                                onVoiceInput = onVoiceInput,
                                onSummarize = onSummarize,
                                onTranslate = onTranslate,
                                onConvertMemoToTasks = onConvertMemoToTasksAction,
                                onMeetingNotes = onMeetingNotes,
                                onTaskMoveTasks = onTaskMoveTasks,
                                onTaskCopyTasks = onTaskCopyTasks,
                                onTaskDeleteTasks = onTaskDeleteTasks,
                                onConvertHandwriting = onConvertHandwriting,
                                onStartDayReview = onStartDayReview,
                                onQuickAdd = onQuickAdd,
                                onPlanMyDay = onPlanMyDay,
                                onSortByPriority = onSortByPriority,
                                onSmartSuggestions = onSmartSuggestions,
                                onTaskBreakdown = onTaskBreakdown,
                                onDailySummary = onDailySummary,
                                onWeeklySummary = onWeeklySummary,
                                hasBooxPermission = hasBooxPermission,
                                onBooxNotesPermissionNeeded = onBooxNotesPermissionNeeded,
                                closeToolbarMenu = closeToolbarMenu
                            )
                        )
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(EInkTheme.Colors.primary)
            )
        }

        // Page Management Dialog (Memos only, multiple pages)
        if (isPageManagementDialogOpen && currentTab == EditorTab.Memos && activeState.dayPageInfo != null) {
            val pageInfo = activeState.dayPageInfo!!
            PageManagementDialog(
                dayDate = pageInfo.date,
                pageIds = pageInfo.pageIds,
                currentPageId = activeState.currentPageId,
                onReorderPages = { newPageIds ->
                    activeTower.reorderPages(newPageIds)
                },
                onDeletePage = { pageId ->
                    activeTower.deletePageById(pageId)
                },
                onNavigateToPage = { pageId ->
                    activeTower.navigateToPage(pageId)
                },
                onDismiss = { isPageManagementDialogOpen = false }
            )
        }
}

/**
 * Moves a block out of [SharedToolbar]'s own method into a lambda class of its own, with no
 * behaviour of its own. ART does not JIT-compile a method of 16,383 dex code units or more ("Method
 * exceeds compiler instruction limit" in logcat) and runs it interpreted; SharedToolbar reached
 * 18,008 in Oct 2026. Wrap more big blocks like this if logcat shows it again.
 */
@Composable
private fun SplitOutOfSharedToolbar(content: @Composable () -> Unit) {
    content()
}

/**
 * The gap between two neighbouring controls on the toolbar (Oct 2026, replaces the thin dividers,
 * hard to see on black-and-white and Kaleido 3 screens): [min] at least, plus an equal share of the
 * bar's free width, so the controls spread evenly across the bar instead of leaving the free width
 * in one place. Sizes of the controls are unchanged.
 */
@Composable
private fun RowScope.ToolbarGap(min: Dp) {
    Spacer(Modifier.width(min))
    Spacer(Modifier.weight(1f))
}

/** Smallest gap between neighbouring controls on the regular toolbar (Go 10.3); the compact bar uses [COMPACT_TOOL_GAP]. */
private val REGULAR_TOOL_GAP = 4.dp

/** Icon size of the toolbar menu button (≡) at the end of the bar (Oct 2026). */
private val MENU_BUTTON_ICON_SIZE = 30.dp

/**
 * Drawing box of the page navigation arrows (Oct 2026): their glyphs now match the bar's other
 * icons; they filled their whole 40-44 dp button before and looked about twice as big. The touch
 * area is still the full button.
 */
private val NAV_CHEVRON_ICON_SIZE = 28.dp

/** Space left free below the toolbar menu (≡) when it is as tall as the screen allows. */
private val MENU_BOTTOM_MARGIN = 8.dp

/** Pen colors offered by the stroke menu, on the bar and in the toolbar menu (≡). */
private val STROKE_MENU_COLORS = listOf(
    Color.Red,
    Color.Green,
    Color.Blue,
    Color.Cyan,
    Color.Magenta,
    Color.Yellow,
    Color.Gray,
    Color.DarkGray,
    Color.Black,
)

/**
 * A tool's icon in a toolbar menu (≡) row, filling the row's icon box, or [size] for an image that
 * fills its whole canvas (the Boox Notes B looked larger than the others).
 */
@Composable
private fun MenuIcon(iconId: Int, tint: Color, size: Dp = Dp.Unspecified) {
    Icon(
        painter = painterResource(iconId),
        contentDescription = null,
        modifier = if (size == Dp.Unspecified) Modifier.fillMaxSize() else Modifier.size(size),
        tint = tint
    )
}

/**
 * The tools that are not on the bar at this width, listed in the toolbar menu (≡) in bar order (Oct
 * 2026). Compact bar: Memos — insert, AI, meeting notes; Tasks — task AI, Boox Notes; plus whatever
 * [compactToolbarFit] left off (full screen on the Go 6). Regular bar: [regularToolbarOverflow] (Go 10.3 at
 * 385 dpi: Memos — meeting notes, insert; Tasks — task AI). Kept out of [SharedToolbar], which is at ART's JIT method-size limit
 * (above it, ART runs the whole toolbar composable interpreted).
 */
private fun toolbarMenuTools(
    context: android.content.Context,
    state: EditorState,
    isMemosTab: Boolean,
    isCompactToolbar: Boolean,
    compactFit: CompactToolbarFit,
    barOverflow: Set<BarTool>,
    penSetting: PenSetting?,
    onChangePenSetting: (PenSetting) -> Unit,
    onLasso: () -> Unit,
    onImageInsert: () -> Unit,
    onBackgroundSelect: () -> Unit,
    onTableInsert: () -> Unit,
    onAttachmentInsert: () -> Unit,
    onTextInsert: () -> Unit,
    insertHasStoragePermission: Boolean,
    memosAiEnabled: Boolean,
    onRecognizePage: () -> Unit,
    onVoiceInput: () -> Unit,
    onSummarize: () -> Unit,
    onTranslate: () -> Unit,
    onConvertMemoToTasks: () -> Unit,
    onMeetingNotes: () -> Unit,
    onTaskMoveTasks: (() -> Unit)?,
    onTaskCopyTasks: (() -> Unit)?,
    onTaskDeleteTasks: (() -> Unit)?,
    onConvertHandwriting: (() -> Unit)?,
    onStartDayReview: (() -> Unit)?,
    onQuickAdd: () -> Unit,
    onPlanMyDay: (() -> Unit)?,
    onSortByPriority: (() -> Unit)?,
    onSmartSuggestions: (() -> Unit)?,
    onTaskBreakdown: (() -> Unit)?,
    onDailySummary: (() -> Unit)?,
    onWeeklySummary: (() -> Unit)?,
    hasBooxPermission: Boolean,
    onBooxNotesPermissionNeeded: () -> Unit,
    closeToolbarMenu: () -> Unit
): List<ToolbarMenuTool> = buildList {
    if (isCompactToolbar && !compactFit.penSettings && penSetting != null) {
        add(ToolbarMenuTool(
            label = "Pen size and color",
            icon = {
                Box(
                    Modifier
                        .size(EInkTheme.ToolSettingsIndicator.colorDotSize)
                        .background(Color(penSetting.color), CircleShape)
                        .border(EInkTheme.ToolSettingsIndicator.colorDotBorderWidth, EInkTheme.ToolSettingsIndicator.colorDotBorderColor, CircleShape)
                )
            },
            page = {
                StrokeMenuContent(
                    value = penSetting,
                    onChange = onChangePenSetting,
                    sizeOptions = if (state.pen == Pen.MARKER) SIZES_MARKER_DEFAULT else SIZES_STROKES_DEFAULT,
                    colorOptions = STROKE_MENU_COLORS
                )
            },
            pageWrapsContent = true
        ))
    }
    if (isMemosTab) {
        if (if (isCompactToolbar) !compactFit.first else BarTool.Lasso in barOverflow) {
            add(ToolbarMenuTool(
                label = "Lasso select",
                icon = { tint -> MenuIcon(R.drawable.lasso_select_40px, tint) },
                selected = activeToolbarButtonForMode(state.mode) == "select",
                onClick = onLasso
            ))
        }
        if (isCompactToolbar || BarTool.Insert in barOverflow) {
            add(ToolbarMenuTool(
                label = "Insert",
                icon = { tint -> MenuIcon(R.drawable.image_inset_40px, tint) },
                page = { closeMenu ->
                    InsertMenuItems(
                        onImageInsert = onImageInsert,
                        onBackgroundSelect = onBackgroundSelect,
                        onTableInsert = onTableInsert,
                        onAttachmentInsert = onAttachmentInsert,
                        onTextInsert = onTextInsert,
                        hasBooxPermission = insertHasStoragePermission,
                        onDone = closeMenu
                    )
                }
            ))
        }
        if (isCompactToolbar || BarTool.Ai in barOverflow) {
            add(ToolbarMenuTool(
                label = "AI tools",
                icon = { tint -> MenuIcon(R.drawable.ai_sparkles_text, tint) },
                enabled = memosAiEnabled,
                page = { closeMenu ->
                    AiMenuItems(
                        hasSelectedStrokes = !state.selectionState.selectedStrokes.isNullOrEmpty(),
                        hasPageContent = state.pageView.strokes.isNotEmpty(),
                        hasTextAnnotations = state.pageView.textAnnotations.isNotEmpty(),
                        onRecognizePage = onRecognizePage,
                        onVoiceInput = onVoiceInput,
                        onSummarize = onSummarize,
                        onTranslate = onTranslate,
                        onConvertMemoToTasks = onConvertMemoToTasks,
                        onDone = closeMenu
                    )
                }
            ))
        }
        if (isCompactToolbar || BarTool.MeetingNotes in barOverflow) {
            add(ToolbarMenuTool(
                label = "Meeting notes",
                icon = { tint -> Icon(Icons.Outlined.Groups, contentDescription = null, modifier = Modifier.fillMaxSize(), tint = tint) },
                onClick = onMeetingNotes
            ))
        }
    } else {
        if (if (isCompactToolbar) !compactFit.second else BarTool.TaskActions in barOverflow) {
            add(ToolbarMenuTool(
                label = "Task actions",
                icon = { tint -> MenuIcon(R.drawable.checklist_40px, tint) },
                page = { closeMenu ->
                    TaskBulkActionsMenuItems(
                        onMoveTasks = { onTaskMoveTasks?.invoke() },
                        onCopyTasks = { onTaskCopyTasks?.invoke() },
                        onDeleteTasks = { onTaskDeleteTasks?.invoke() },
                        onConvertHandwriting = { onConvertHandwriting?.invoke() },
                        onStartDayReview = onStartDayReview,
                        crossOutEnabled = GlobalAppSettings.current.taskCrossOutEnabled,
                        onCrossOutToggle = { enabled ->
                            com.boox.day.data.db.KvProxy(context).setAppSettings(
                                GlobalAppSettings.current.copy(taskCrossOutEnabled = enabled)
                            )
                        },
                        onDone = closeMenu
                    )
                }
            ))
        }
        if (if (isCompactToolbar) !compactFit.first else BarTool.QuickAdd in barOverflow) {
            add(ToolbarMenuTool(
                label = "Quick add task",
                icon = { tint -> MenuIcon(R.drawable.add_task_40px, tint) },
                onClick = onQuickAdd
            ))
        }
        if (isCompactToolbar || BarTool.TaskAi in barOverflow) {
            add(ToolbarMenuTool(
                label = "Task AI",
                icon = { tint -> MenuIcon(R.drawable.ai_sparkles_text, tint) },
                // AI is on when the user hasn't turned it off (aiEnabled)
                enabled = GlobalAppSettings.current.aiEnabled,
                page = { closeMenu ->
                    TaskAiMenuItems(
                        onPlanMyDay = { onPlanMyDay?.invoke() },
                        onSortByPriority = { onSortByPriority?.invoke() },
                        onSmartSuggestions = { onSmartSuggestions?.invoke() },
                        onTaskBreakdown = { onTaskBreakdown?.invoke() },
                        onDailySummary = { onDailySummary?.invoke() },
                        onWeeklySummary = { onWeeklySummary?.invoke() },
                        onDone = closeMenu
                    )
                }
            ))
        }
    }
    // Boox Notes is on the regular bar of both tabs; on the compact bar Memos may fit it
    if (isCompactToolbar && (!isMemosTab || !compactFit.second)) {
        add(ToolbarMenuTool(
            label = "Boox Notes",
            icon = { tint -> MenuIcon(R.drawable.boox_b_letter, tint, size = 18.dp) },
            badge = !hasBooxPermission,
            mayOpenPage = {
                DrawCanvas.toolbarInteraction.tryEmit(Unit)
                booxNotesMenuMayOpen(onPermissionNeeded = {
                    closeToolbarMenu()
                    onBooxNotesPermissionNeeded()
                })
            },
            page = { closeMenu -> BooxNotesMenuItems(onDone = closeMenu) }
        ))
    }
    // Full screen (Oct 2026): here when the compact bar has no room for its button (Go 6)
    if (isCompactToolbar && !compactFit.fullScreen) {
        val isOn = FullScreenState.isOn
        add(ToolbarMenuTool(
            label = when {
                isOn -> "Normal view"
                isMemosTab && FullScreenState.memoHasMoreBelow -> "Full screen (more below)"
                else -> "Full screen"
            },
            icon = { tint ->
                Icon(
                    imageVector = if (isOn) Icons.Filled.CloseFullscreen else Icons.Filled.OpenInFull,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    tint = tint
                )
            },
            onClick = { FullScreenState.toggle() }
        ))
    }
}

/** Gap between neighbouring controls on the compact bar, so a finger tap never lands on two. */
private val COMPACT_TOOL_GAP = 8.dp

/** Which optional tools the compact toolbar shows on the bar (Oct 2026, layout B); the rest go to the toolbar menu (≡). */
private data class CompactToolbarFit(
    /** Lasso (Memos) / quick add (Tasks) */
    val first: Boolean,
    /** Boox Notes (Memos) / task actions (Tasks) */
    val second: Boolean,
    /** Pen size & color */
    val penSettings: Boolean,
    /** Full screen switch (Oct 2026) */
    val fullScreen: Boolean
) {
    companion object {
        val ALL = CompactToolbarFit(first = true, second = true, penSettings = true, fullScreen = true)
    }
}

/**
 * Fits the compact bar to [barWidth]: tabs, pen, eraser, page navigation, undo, redo and the menu (≡) always
 * stay at their full touch sizes. The optional controls are placed by priority — full screen, pen size &
 * color, then Boox Notes / task actions, then lasso / quick add — each one that still fits takes its place, so
 * a gap left by a wide tool is used by a narrower one. The rest go to the toolbar menu (≡); on the Go 6
 * (572 dp, with page navigation) none fit, so full screen is in the menu there.
 * The widths are those of the composables in [Toolbar] — keep them in step when sizes change.
 */
private fun compactToolbarFit(barWidth: Dp, hasPageNav: Boolean, hasPenSettings: Boolean): CompactToolbarFit {
    val t = EInkTheme.Toolbar
    val gap = COMPACT_TOOL_GAP
    // Each control after the tabs follows a gap of at least COMPACT_TOOL_GAP (ToolbarGap)
    val fixed = 2.dp + t.tabTotalWidth +
        gap + t.buttonSizeLarge + gap + t.buttonSizeMedium +                          // pen, eraser
        (if (hasPageNav) gap + EInkTheme.Global.touchTarget * 2 + 32.dp else 0.dp) +  // page navigation (label 32dp)
        gap + t.buttonSizeLarge + gap + t.buttonSizeLarge + gap + t.buttonSizeLarge   // undo, redo, menu ≡ (48dp)
    var spare = barWidth - fixed
    val fullScreenWidth = gap + t.buttonSizeMedium
    val fullScreen = fullScreenWidth <= spare
    if (fullScreen) spare -= fullScreenWidth
    val penSettingsWidth = gap + 70.dp  // pen settings indicator
    val penSettings = hasPenSettings && penSettingsWidth <= spare
    if (penSettings) spare -= penSettingsWidth
    val toolWidth = gap + t.buttonSizeMedium
    val second = toolWidth <= spare
    if (second) spare -= toolWidth
    val first = toolWidth <= spare
    return CompactToolbarFit(first = first, second = second, penSettings = penSettings, fullScreen = fullScreen)
}

/** The optional tools of the regular toolbar, which go into the toolbar menu (≡) when the bar is too narrow (Oct 2026). */
private enum class BarTool { Lasso, Insert, Ai, MeetingNotes, TaskActions, QuickAdd, TaskAi }

/**
 * The tools that go into the toolbar menu (≡) on the regular bar (Oct 2026): the 7 Oct Action items and
 * Meeting notes buttons pushed page navigation and the page menu off the Go 10.3 (then 720 dp at 413 dpi).
 * Tabs, pen, pen size, eraser, Boox Notes, undo, redo, page navigation, full screen and ≡ always stay; the
 * optional tools leave the bar lowest priority first — Memos: Meeting notes, Insert, AI, lasso; Tasks: Task AI,
 * quick add, task actions. Every control keeps a gap of at least REGULAR_TOOL_GAP. Go 10.3 at 385 dpi
 * (773 dp), since the full screen button (Oct 2026): Memos — Meeting notes and Insert; Tasks — Task AI.
 * The widths are those of the composables in [SharedToolbar] (every tool 44 dp, its dropdown buttons
 * included) — keep them in step when sizes change; debug builds log a warning when ≡ gets squeezed.
 */
private fun regularToolbarOverflow(barWidth: Dp, isMemos: Boolean, hasPageNav: Boolean): Set<BarTool> {
    val t = EInkTheme.Toolbar
    // Each control after the tabs follows a gap of at least REGULAR_TOOL_GAP (ToolbarGap)
    val gap = REGULAR_TOOL_GAP
    val fixed = 2.dp + t.tabTotalWidth +                                             // padding, tabs
        gap + t.buttonSizeLarge + gap + 70.dp + gap + t.buttonSizeMedium +           // pen, pen size, eraser
        gap + t.buttonSizeMedium +                                                   // Boox Notes
        gap + t.buttonSizeLarge + gap + t.buttonSizeLarge +                          // undo, redo
        (if (hasPageNav) gap + t.navChevronWidth * 2 + 32.dp else 0.dp) +           // page navigation (label 32dp)
        gap + t.buttonSizeMedium +                                                   // full screen
        gap + t.buttonSizeLarge                                                      // ≡
    val leaveFirst = if (isMemos) {
        listOf(BarTool.MeetingNotes, BarTool.Insert, BarTool.Ai, BarTool.Lasso)
    } else {
        listOf(BarTool.TaskAi, BarTool.QuickAdd, BarTool.TaskActions)
    }
    val toolWidth = gap + t.buttonSizeMedium
    var width = fixed + toolWidth * leaveFirst.size
    val overflow = mutableSetOf<BarTool>()
    for (tool in leaveFirst) {
        if (width <= barWidth) break
        overflow += tool
        width -= toolWidth
    }
    return overflow
}
