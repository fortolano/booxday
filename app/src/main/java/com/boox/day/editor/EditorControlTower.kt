package com.boox.day.editor

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import com.boox.day.data.ClearedPages
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.editor.state.EditorState
import com.boox.day.editor.state.History
import com.boox.day.editor.state.HistoryBusActions
import com.boox.day.editor.state.Mode
import com.boox.day.editor.state.Operation
import com.boox.day.editor.state.PlacementMode
import com.boox.day.editor.state.SelectionState
import com.boox.day.editor.state.UndoRedoType
import com.boox.day.editor.utils.offsetStroke
import com.boox.day.editor.utils.refreshScreen
import com.boox.day.editor.utils.selectImagesAndStrokes
import com.boox.day.ui.showHint
import com.boox.day.utils.TaggedLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.boox.day.data.db.TaskRepository
import com.boox.day.data.db.TextAnnotation
import com.boox.day.data.db.TextAnnotationRepository
import java.util.Date
import java.util.UUID

class EditorControlTower(
    private val scope: CoroutineScope,
    val page: PageView,
    private var history: History,
    private val state: EditorState
) {
    private var scrollInProgress = Mutex()
    private var scrollJob: Job? = null
    private val logEditorControlTower = TaggedLog("EditorControlTower")

    /** Moves the view (Memos: two-finger drag, page map; Oct 2026). Unused on Tasks, which never moves. */
    val navigator: MemoNavigator by lazy { MemoNavigator(scope, page) }

    // PERFORMANCE FIX: Track observer job for proper lifecycle management
    // When EditorControlTower is replaced (pageId changes), cancel old observers
    private var observerJob: Job? = null

    companion object {
        val changePage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    }

    fun registerObservers() {
        // Cancel any previous observers before registering new ones
        cancelObservers()

        // Create a child job so we can cancel observers independently
        observerJob = Job(scope.coroutineContext[Job])

        scope.launch(observerJob!!) {
            changePage.collect { pageId ->
                logEditorControlTower.d("Change to page $pageId")
                switchPage(pageId)
                page.changePage(pageId)
                refreshScreen()
            }
        }
    }

    /**
     * PERFORMANCE FIX: Cancel observer coroutines to prevent zombie accumulation.
     * Called from DisposableEffect.onDispose in MainScreen when pageId changes.
     */
    fun cancelObservers() {
        observerJob?.let { job ->
            if (job.isActive) {
                logEditorControlTower.i("Cancelling EditorControlTower observers")
                job.cancel()
            }
        }
        observerJob = null
    }

    // returns delta if could not scroll, to be added to next request,
    // Scrolling disabled - pages are fixed-size with pagination system
    // Users can add new pages instead of scrolling
    fun processScroll(delta: Offset): Offset {
        // Scrolling is disabled - return immediately
        return Offset.Zero
    }

    /**
     * Switches the currently active page.
     * Public wrapper for switchPage to allow external controllers (EditorTabManager)
     * to force a page load when stale content is detected.
     *
     * MIRRORS MainScreen LaunchedEffect logic exactly.
     */
    fun loadPage(pageId: String, date: String) {
        page.changePage(pageId)           // Update PageView
        state.dayDate = date              // Update date STRING

        // Only call changePage if pageId actually differs (avoids "same page" snackbar)
        if (state.currentPageId != pageId) {
            state.changePage(pageId)      // Update EditorState.pageId
        }

        state.refreshDayPageInfo()        // Refresh day page info
        history.cleanHistory()            // Clear undo history

        // Notify UI to redraw
        scope.launch {
            DrawCanvas.refreshUi.emit(Unit)
        }
    }

    /**
     * Switches the currently active page.
     *
     * This function updates the editor state, clears the undo/redo history,
     * and instructs the page view to load and display the content of the new page.
     *
     * @param id The unique identifier of the page to switch to.
     */
    private fun switchPage(id: String) {
        state.changePage(id)
        history.cleanHistory()
        page.changePage(id)
    }

    fun setIsDrawing(value: Boolean) {
        if (state.isDrawing == value) {
            logEditorControlTower.w("IsDrawing already set to $value")
            return
        }
        state.isDrawing = value
    }

    fun toggleTool() {
        state.mode = if (state.mode == Mode.Draw) Mode.Erase else Mode.Draw
    }

    fun toggleZen() {
        // Zen mode (toolbar hiding) is disabled - toolbar is always visible
        // This function is kept for gesture mapping compatibility but does nothing
    }

    fun getSnapshotOfSelectionState(): SelectionState {
        return state.selectionState
    }

    // IDENTICAL pattern for text box state
    fun getTextBoxState(): com.boox.day.editor.state.TextBoxState {
        return state.textBoxState
    }

    // Get current page ID for text annotation operations
    fun getCurrentPageId(): String {
        return state.currentPageId
    }

    /**
     * Clear the drawing clipboard (internal app clipboard for strokes/images).
     * Used when copying text to ensure paste button shows text icon.
     */
    fun clearDrawingClipboard() {
        state.clipboard = null
    }

    fun getSelectedBitmap(): Bitmap {
        return state.selectionState.selectedBitmap!!
    }

    fun goToNextPage() {
        logEditorControlTower.i("Going to next page")
        val next = state.getNextPage()
        if (next != null)
            switchPage(next)

    }

    fun goToPreviousPage() {
        logEditorControlTower.i("Going to previous page")
        val previous = state.getPreviousPage()
        if (previous != null)
            switchPage(previous)
    }

    // Day page navigation (for calendar mode)
    fun goToNextDayPage() {
        logEditorControlTower.i("Going to next day page")
        val next = state.getNextDayPage()
        if (next != null) {
            switchDayPage(next)
        } else if (state.isOnLastDayPage()) {
            // On the last page: add a new one
            if (state.dayDate == null) return
            val meetingId = state.appRepository.pageRepository.getById(state.currentPageId)?.linkedEventId
            val newPageId = state.createNewDayPage()
            if (newPageId != null) {
                // Meeting notes run on (Oct 2026): a page added after a meeting's page belongs to that meeting
                meetingId?.let { eventId ->
                    scope.launch(Dispatchers.IO) { state.appRepository.pageRepository.setLinkedEventId(newPageId, eventId) }
                }
                switchDayPage(newPageId)
            }
        }
    }

    fun goToPreviousDayPage() {
        logEditorControlTower.i("Going to previous day page")
        val previous = state.getPreviousDayPage()
        if (previous != null) {
            switchDayPage(previous)
        }
    }

    /**
     * Navigate directly to a specific page by ID.
     * Used by PageManagementDialog when user taps a page thumbnail.
     */
    fun navigateToPage(pageId: String) {
        val dayDate = state.dayDate ?: return
        val pageInfo = state.dayPageInfo ?: return
        if (pageId !in pageInfo.pageIds) {
            logEditorControlTower.w("navigateToPage: pageId $pageId not in current day")
            return
        }
        switchDayPage(pageId)
    }

    /**
     * Reorder pages for the current day.
     * Called from PageManagementDialog.
     */
    fun reorderPages(newPageIds: List<String>) {
        val dayDate = state.dayDate ?: return
        state.appRepository.dayPageRepository.reorderPages(dayDate, newPageIds)
        state.refreshDayPageInfo()
    }

    /**
     * Delete a specific page by ID.
     * Returns the page ID to navigate to after deletion, or null if no pages remain.
     */
    fun deletePageById(pageId: String): String? {
        val dayDate = state.dayDate ?: return null
        val dayPageRepo = state.appRepository.dayPageRepository

        val newPageId = dayPageRepo.removePage(dayDate, pageId)

        // If we deleted the current page, switch to the new page
        if (pageId == state.currentPageId && newPageId != null) {
            switchDayPage(newPageId)
        } else {
            // Just refresh the page info
            state.refreshDayPageInfo()
        }

        scope.launch {
            DrawCanvas.contentChanged.emit(Unit)
        }
        return newPageId
    }

    /**
     * Clear all strokes and images from ALL pages for the current day.
     * This includes both Memos pages (from DayPage entity) and Tasks pages (from Task entity).
     * Returns the total number of pages cleared.
     */
    fun clearAllDayPages(): Int {
        val dayDate = state.dayDate ?: return 0
        val dayPageRepo = state.appRepository.dayPageRepository
        val taskRepo = state.appRepository.taskRepository

        // Collect all page IDs to clear
        val allPageIds = mutableListOf<String>()

        // 1. Get Memos page IDs from DayPage entity
        val dayPage = dayPageRepo.getByDate(dayDate)
        if (dayPage != null) {
            allPageIds.addAll(dayPage.pageIds)
        }

        // 2. Get Tasks page IDs from Task entity
        val taskPageIds = taskRepo.getDistinctPageIdsForDate(dayDate)
        allPageIds.addAll(taskPageIds)

        // Remove duplicates (shouldn't happen, but just in case)
        val uniquePageIds = allPageIds.distinct()

        logEditorControlTower.i("Clear all day pages: date=$dayDate, memoPages=${dayPage?.pageIds?.size ?: 0}, taskPages=${taskPageIds.size}, total=${uniquePageIds.size}")

        // Clear content from all pages in DB
        uniquePageIds.forEach { pageId ->
            dayPageRepo.clearPageContent(pageId)
        }

        // Clear current page in-memory state and redraw
        page.clearAllContent()
        history.cleanHistory()

        // Notify changes
        DrawCanvas.contentChanged.tryEmit(Unit)
        scope.launch {
            DrawCanvas.refreshUi.emit(Unit)
        }

        return uniquePageIds.size
    }

    /**
     * Clear only MEMO pages for the current day.
     * Clears DayPage entity pages only (not Tasks).
     * Returns the number of memo pages cleared.
     */
    fun clearMemoPages(): Int {
        val dayDate = state.dayDate ?: return 0
        val dayPageRepo = state.appRepository.dayPageRepository

        // Get Memos page IDs from DayPage entity
        val dayPage = dayPageRepo.getByDate(dayDate)
        if (dayPage == null || dayPage.pageIds.isEmpty()) {
            logEditorControlTower.i("Clear memo pages: no memo pages found for $dayDate")
            return 0
        }

        val memoPageIds = dayPage.pageIds
        logEditorControlTower.i("Clear memo pages: date=$dayDate, pageCount=${memoPageIds.size}")

        // Clear content from all memo pages in DB
        memoPageIds.forEach { pageId ->
            dayPageRepo.clearPageContent(pageId)
        }

        // If currently viewing a memo page, clear in-memory state
        if (state.currentPageId in memoPageIds) {
            page.clearAllContent()
            history.cleanHistory()
        }
        // The other pages kept their ink in memory and on disk, and search kept their text (Oct 2026)
        ClearedPages.forgetLoadedContent(page.context, memoPageIds - state.currentPageId)
        scope.launch { ClearedPages.forgetSearchText(page.context, dayDate, memoPageIds) }

        // Notify changes
        DrawCanvas.contentChanged.tryEmit(Unit)
        scope.launch {
            DrawCanvas.refreshUi.emit(Unit)
        }

        return memoPageIds.size
    }

    /**
     * Clear only TASK pages for the current day.
     * Clears Task entity pages only (not Memos).
     * This resets all task metadata AND deletes strokes from task pages.
     * Returns the number of task pages cleared.
     */
    fun clearTaskPages(): Int {
        val dayDate = state.dayDate ?: return 0
        val taskRepo = state.appRepository.taskRepository
        val dayPageRepo = state.appRepository.dayPageRepository

        // Get Tasks page IDs from Task entity
        val taskPageIds = taskRepo.getDistinctPageIdsForDate(dayDate)
        if (taskPageIds.isEmpty()) {
            logEditorControlTower.i("Clear task pages: no task pages found for $dayDate")
            return 0
        }

        logEditorControlTower.i("Clear task pages: date=$dayDate, pageCount=${taskPageIds.size}")

        // Clear content from all task pages in DB
        taskPageIds.forEach { pageId ->
            dayPageRepo.clearPageContent(pageId)
        }

        // Also use TaskRepository's deleteAllTasksForDate to reset metadata
        // This clears completed/priority/typedText but keeps the task slots
        taskRepo.deleteAllTasksForDate(dayDate)

        // If currently viewing a task page, clear in-memory state
        if (state.currentPageId in taskPageIds) {
            page.clearAllContent()
            history.cleanHistory()
        }
        // The day may have fewer pages now: MainScreen reloads its page count (Oct 2026)
        TaskRepository.notifyDayCleared(dayDate)

        // Notify changes
        DrawCanvas.contentChanged.tryEmit(Unit)
        scope.launch {
            DrawCanvas.refreshUi.emit(Unit)
        }

        return taskPageIds.size
    }

    /**
     * Clear content from current page only (keeps the page, just removes strokes/images)
     */
    fun clearCurrentPage() {
        val dayDate = state.dayDate ?: return
        val currentPageId = state.currentPageId
        val dayPageRepo = state.appRepository.dayPageRepository

        if (dayPageRepo.pageHasContent(currentPageId)) {
            logEditorControlTower.i("Clear current page: date=$dayDate, pageId=$currentPageId")
            dayPageRepo.clearPageContent(currentPageId)
            page.clearAllContent()
            history.cleanHistory()
            scope.launch { ClearedPages.forgetSearchText(page.context, dayDate, listOf(currentPageId)) }
            DrawCanvas.contentChanged.tryEmit(Unit)
            scope.launch {
                DrawCanvas.refreshUi.emit(Unit)
            }
        } else {
            com.boox.day.ui.showHint("Page is already empty", scope)
        }
    }

    fun deleteCurrentDayPage() {
        val dayDate = state.dayDate ?: return
        val currentPageId = state.currentPageId
        val pageInfo = state.dayPageInfo ?: return
        val dayPageRepo = state.appRepository.dayPageRepository
        val pageRepo = state.appRepository.pageRepository

        logEditorControlTower.i("Delete day page: date=$dayDate, pageId=$currentPageId, totalPages=${pageInfo.totalPages}")

        if (pageInfo.totalPages > 1) {
            // Multiple pages - delete current page
            val newPageId = dayPageRepo.removePage(dayDate, currentPageId)
            if (newPageId != null) {
                switchDayPage(newPageId)
            }
            DrawCanvas.contentChanged.tryEmit(Unit)
        } else {
            // Single page - clear content if it has any
            if (dayPageRepo.pageHasContent(currentPageId)) {
                dayPageRepo.clearPageContent(currentPageId)
                // Also clear any event-memo link (so memo icon disappears from Events grid)
                scope.launch(Dispatchers.IO) {
                    pageRepo.clearLinkedEventId(currentPageId)
                }
                page.clearAllContent()
                history.cleanHistory()
                scope.launch { ClearedPages.forgetSearchText(page.context, dayDate, listOf(currentPageId)) }
                DrawCanvas.contentChanged.tryEmit(Unit)
                scope.launch {
                    DrawCanvas.refreshUi.emit(Unit)
                }
            } else {
                // Already empty - show message
                com.boox.day.ui.showHint("Page is already empty", scope)
            }
        }
    }

    private fun switchDayPage(newPageId: String) {
        val dayDate = state.dayDate ?: return
        state.appRepository.dayPageRepository.setOpenPageId(dayDate, newPageId)
        state.changePage(newPageId)
        state.refreshDayPageInfo()
        history.cleanHistory()
        page.changePage(newPageId)
        scope.launch {
            DrawCanvas.refreshUi.emit(Unit)
        }
    }

    fun undo() {
        scope.launch {
            logEditorControlTower.i("Undo called")
            history.handleHistoryBusActions(HistoryBusActions.MoveHistory(UndoRedoType.Undo))
//            DrawCanvas.refreshUi.emit(Unit)
        }
    }

    fun redo() {
        scope.launch {
            logEditorControlTower.i("Redo called")
            history.handleHistoryBusActions(HistoryBusActions.MoveHistory(UndoRedoType.Redo))
//            DrawCanvas.refreshUi.emit(Unit)
        }
    }

    fun onPinchToZoom(delta: Float, center: Offset?) {
        if (!page.isTransformationAllowed) return
        if (state.mode == Mode.Select)
            return
        scope.launch {
            scrollInProgress.withLock {
                if (GlobalAppSettings.current.simpleRendering || !GlobalAppSettings.current.continuousZoom)
                    page.simpleUpdateZoom(delta)
                else
                    page.updateZoom(delta, center)
            }
            DrawCanvas.refreshUiImmediately.emit(Unit)
        }
    }

    // Resets zoom to 1.0x (100%)
    fun resetZoom() {
        scope.launch {
            page.applyZoomAndRedraw(1f)
            DrawCanvas.refreshUiImmediately.emit(Unit)
        }
    }

    private suspend fun onPageScroll(dragDelta: Offset) {
        // scroll is in Page coordinates
        if (GlobalAppSettings.current.simpleRendering)
            page.simpleUpdateScroll(dragDelta)
        else
            page.updateScroll(dragDelta)
    }


    // when selection is moved, we need to redraw canvas
    fun applySelectionDisplace() {
        val operationList = state.selectionState.applySelectionDisplace(page)
        if (!operationList.isNullOrEmpty()) {
            history.addOperationsToHistory(operationList)
        }
        scope.launch {
            DrawCanvas.refreshUi.emit(Unit)
        }
    }

    fun deleteSelection() {
        val operationList = state.selectionState.deleteSelection(page)
        history.addOperationsToHistory(operationList)
        state.isDrawing = true
        scope.launch {
            DrawCanvas.refreshUi.emit(Unit)
        }
    }

    /**
     * Register operations with history for undo/redo support.
     * Used by external components (e.g., TextBoxOverlay) to register operations.
     */
    fun registerHistoryOperations(operations: List<Operation>) {
        history.addOperationsToHistory(operations)
    }

    fun changeSizeOfSelection(scale: Int) {
        if (!state.selectionState.selectedImages.isNullOrEmpty())
            state.selectionState.resizeImages(scale, scope, page)
        if (!state.selectionState.selectedStrokes.isNullOrEmpty())
            state.selectionState.resizeStrokes(scale, scope, page)
        // Emit a refresh signal to update UI
        scope.launch {
            DrawCanvas.refreshUi.emit(Unit)
        }
    }

    fun duplicateSelection() {
        // finish ongoing movement
        applySelectionDisplace()
        state.selectionState.duplicateSelection()

    }

    fun cutSelectionToClipboard(context: Context) {
        state.clipboard = state.selectionState.selectionToClipboard(page.scroll, context)
        deleteSelection()
        showHint("Content cut to clipboard", scope)
    }

    fun copySelectionToClipboard(context: Context) {
        state.clipboard = state.selectionState.selectionToClipboard(page.scroll, context)

        // CRITICAL: Clear system text clipboard so paste button shows drawing icon
        // This ensures the most recent copy operation (drawing) takes precedence
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.clearPrimaryClip()
        } catch (e: Exception) {
            logEditorControlTower.w("Could not clear system clipboard: ${e.message}")
        }
    }


    fun pasteFromClipboard() {
        // finish ongoing movement
        applySelectionDisplace()

        val (strokes, images) = state.clipboard ?: return

        val now = Date()
        val scrollPos = page.scroll

        val pastedStrokes = strokes.map {
            offsetStroke(it, offset = scrollPos).copy(
                // change the pasted strokes' ids - it's a copy
                id = UUID
                    .randomUUID()
                    .toString(),
                createdAt = now,
                // set the pageId to the current page
                pageId = this.page.currentPageId
            )
        }

        val pastedImages = images.map {
            it.copy(
                // change the pasted images' ids - it's a copy
                id = UUID
                    .randomUUID()
                    .toString(),
                x = it.x + scrollPos.x.toInt(),
                y = it.y + scrollPos.y.toInt(),
                createdAt = now,
                // set the pageId to the current page
                pageId = this.page.currentPageId
            )
        }

        selectImagesAndStrokes(scope, page, state, pastedImages, pastedStrokes)
        state.selectionState.placementMode = PlacementMode.Paste

        showHint("Pasted content from clipboard", scope)
    }

    /**
     * Clipboard content type for unified paste button.
     */
    enum class ClipboardType {
        EMPTY, DRAWING, TEXT, BOTH
    }

    /**
     * Detect what type of content is available in clipboards.
     * @param context Context for accessing system clipboard
     * @return ClipboardType indicating what can be pasted
     */
    fun detectClipboardType(context: Context): ClipboardType {
        val hasDrawing = state.clipboard != null
        val hasText = hasSystemClipboardText(context)

        return when {
            hasDrawing && hasText -> ClipboardType.BOTH
            hasDrawing -> ClipboardType.DRAWING
            hasText -> ClipboardType.TEXT
            else -> ClipboardType.EMPTY
        }
    }

    /**
     * Check if system clipboard has text content.
     */
    private fun hasSystemClipboardText(context: Context): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        return clipboard.hasPrimaryClip() &&
               clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
    }

    /**
     * Unified paste function - pastes either drawing or text based on clipboard content.
     * Drawing clipboard takes priority over text clipboard.
     * @param context Context for accessing system clipboard
     */
    fun unifiedPaste(context: Context) {
        when (detectClipboardType(context)) {
            ClipboardType.DRAWING, ClipboardType.BOTH -> pasteFromClipboard()
            ClipboardType.TEXT -> pasteTextFromClipboard(context)
            ClipboardType.EMPTY -> {} // Nothing to paste
        }
    }

    /**
     * Paste text from system clipboard as a new TextAnnotation.
     * Auto-positions at CENTER of visible area so mini toolbar is visible.
     * Text box is selected with drag handles for easy repositioning.
     * @param context Context for clipboard access
     * @return true if text was pasted successfully
     */
    fun pasteTextFromClipboard(context: Context): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        val clipData = clipboard.primaryClip ?: return false
        if (clipData.itemCount == 0) return false

        val text = clipData.getItemAt(0).coerceToText(context).toString()
        if (text.isEmpty()) return false

        logEditorControlTower.i("Pasting text from clipboard: ${text.length} chars")

        // Calculate visible area in page coordinates (accounting for zoom)
        val zoomLevel = page.zoomLevel.value
        val visibleWidth = page.viewWidth / zoomLevel
        val visibleHeight = page.viewHeight / zoomLevel

        // Text box dimensions
        val boxWidth = 400f
        val boxHeight = 100f
        val miniToolbarHeight = 80f  // Space needed above text box for mini toolbar

        // Center horizontally, position vertically with room for mini toolbar above
        val scrollOffset = page.scroll
        val x = scrollOffset.x + (visibleWidth - boxWidth) / 2f
        // Place slightly above center to ensure mini toolbar is fully visible
        val y = scrollOffset.y + miniToolbarHeight + (visibleHeight - boxHeight - miniToolbarHeight) / 2f

        // Get rich text spans from RichTextClipboard if available
        val richClipboard = com.boox.day.editor.state.RichTextClipboard
        val spans = if (richClipboard.hasContent() && richClipboard.text == text) {
            com.boox.day.data.model.RichTextSpanSerializer.encode(richClipboard.spans)
        } else {
            null
        }

        val pageId = state.currentPageId
        val annotation = TextAnnotation(
            id = UUID.randomUUID().toString(),
            content = text,
            x = x,
            y = y,
            width = 400f,
            height = 100f,
            fontSize = 48f,
            pageId = pageId,
            sourceType = "pasted",
            spans = spans
        )

        // Add to PageDataManager for immediate display
        com.boox.day.data.PageDataManager.addTextAnnotation(pageId, annotation)

        // Select the new text box so user can drag-to-adjust
        state.textBoxState.selectTextBox(annotation)
        state.isDrawing = false

        // Save to database
        scope.launch(Dispatchers.IO) {
            val textRepo = TextAnnotationRepository(page.context)
            textRepo.insert(annotation)
        }

        // Refresh canvas and notify calendar badges that content changed
        scope.launch {
            DrawCanvas.forceUpdate.emit(null)
            DrawCanvas.contentChanged.tryEmit(Unit)
        }

        showHint("Pasted text from clipboard", scope)
        return true
    }

    /**
     * Insert an empty text box at the center of the visible canvas area.
     * The text box is immediately selected so user can drag to reposition,
     * then tap inside to start typing.
     */
    fun insertEmptyTextBox() {
        logEditorControlTower.i("Inserting empty text box")

        // Calculate visible area in page coordinates (accounting for zoom)
        val zoomLevel = page.zoomLevel.value
        val visibleWidth = page.viewWidth / zoomLevel
        val visibleHeight = page.viewHeight / zoomLevel

        // Text box dimensions - larger size for easy touch interaction
        val boxWidth = 450f
        val boxHeight = 150f
        val miniToolbarHeight = 80f  // Space needed above text box for mini toolbar

        // Center horizontally, position vertically with room for mini toolbar above
        val scrollOffset = page.scroll
        val x = scrollOffset.x + (visibleWidth - boxWidth) / 2f
        // Place slightly above center to ensure mini toolbar is fully visible
        val y = scrollOffset.y + miniToolbarHeight + (visibleHeight - boxHeight - miniToolbarHeight) / 2f

        val pageId = state.currentPageId
        val annotation = TextAnnotation(
            id = UUID.randomUUID().toString(),
            content = "",  // Empty - user will type content
            x = x,
            y = y,
            width = boxWidth,
            height = boxHeight,
            fontSize = 16f * page.context.resources.displayMetrics.density,  // 16sp default
            pageId = pageId,
            sourceType = "manual"  // Mark as manually inserted
        )

        // Add to PageDataManager for immediate display
        com.boox.day.data.PageDataManager.addTextAnnotation(pageId, annotation)

        // Select the new text box so user can see it and drag to adjust position
        state.textBoxState.selectTextBox(annotation)
        state.isDrawing = false

        // Save to database
        scope.launch(Dispatchers.IO) {
            val textRepo = TextAnnotationRepository(page.context)
            textRepo.insert(annotation)
        }

        // Refresh canvas and notify calendar badges that content changed
        scope.launch {
            DrawCanvas.forceUpdate.emit(null)
            DrawCanvas.contentChanged.tryEmit(Unit)
        }

        showHint("Tap inside to add text", scope)
    }

    /**
     * Save and exit any active text box (selected or editing).
     * Call this before any toolbar action that might conflict with text box state
     * (e.g., page navigation, mode changes, undo/redo).
     *
     * This ensures the text box is saved to the CURRENT page before the action
     * is performed, preventing it from being incorrectly carried to a new page.
     */
    fun saveAndExitTextBox() {
        val textBoxState = state.textBoxState
        val selectedTextBox = textBoxState.selectedTextBox ?: return

        logEditorControlTower.d("saveAndExitTextBox: saving text box ${selectedTextBox.id}")

        // If editing, save the edited content first
        if (textBoxState.editingTextBox != null) {
            val editedText = textBoxState.currentEditText ?: selectedTextBox.content
            val editedSpans = if (textBoxState.currentSpans.isNotEmpty()) {
                com.boox.day.data.model.RichTextSpanSerializer.encode(textBoxState.currentSpans)
            } else {
                selectedTextBox.spans
            }

            val updated = selectedTextBox.copy(
                content = editedText,
                spans = editedSpans
            )

            // Update PageDataManager
            com.boox.day.data.PageDataManager.removeTextAnnotation(selectedTextBox.pageId, selectedTextBox.id)
            com.boox.day.data.PageDataManager.addTextAnnotation(selectedTextBox.pageId, updated)

            // Save to database
            scope.launch(Dispatchers.IO) {
                val textRepo = TextAnnotationRepository(page.context)
                textRepo.update(updated)
            }

            textBoxState.stopEditing()
        }

        // Apply any displacement (position changes)
        val displaceOffset = textBoxState.textBoxDisplaceOffset
        if (displaceOffset != null && (displaceOffset.x != 0 || displaceOffset.y != 0)) {
            val newX = selectedTextBox.x + displaceOffset.x
            val newY = selectedTextBox.y + displaceOffset.y

            val updated = selectedTextBox.copy(x = newX, y = newY)

            // Update PageDataManager
            com.boox.day.data.PageDataManager.removeTextAnnotation(selectedTextBox.pageId, selectedTextBox.id)
            com.boox.day.data.PageDataManager.addTextAnnotation(selectedTextBox.pageId, updated)

            // Save to database
            scope.launch(Dispatchers.IO) {
                val textRepo = TextAnnotationRepository(page.context)
                textRepo.update(updated)
            }
        }

        // Clear selection and reset state
        textBoxState.reset()
        state.isDrawing = true

        // Refresh canvas
        scope.launch {
            DrawCanvas.forceUpdate.emit(null)
        }
    }

    /**
     * Check if there's an active text box (selected or editing).
     */
    fun hasActiveTextBox(): Boolean {
        return state.textBoxState.selectedTextBox != null
    }
}
