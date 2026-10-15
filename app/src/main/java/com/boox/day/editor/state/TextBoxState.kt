package com.boox.day.editor.state

import com.boox.day.utils.TaggedLog

import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import com.boox.day.data.db.TextAnnotation
import com.boox.day.data.model.RichTextSpan
import kotlin.math.abs

/**
 * Resize handle positions for text boxes
 */
enum class ResizeHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, BOTTOM, LEFT, RIGHT,
    NONE
}

/**
 * Toolbar mode for text boxes:
 * - BASIC: Shows on initial long-press selection (Delete, Copy, Cut, Duplicate)
 * - ADVANCED: Shows when user taps inside selected text box (+ formatting controls)
 */
enum class TextBoxToolbarMode {
    BASIC,    // Initial selection - basic actions only
    ADVANCED  // Editing mode - full formatting toolbar
}

/**
 * State management for text box selection, editing, and interaction.
 * Used by both handwriting recognition and voice input features.
 */
private val textBoxStateLog = TaggedLog("TextBoxState")

/**
 * App-local clipboard for rich text content (text + spans).
 * System clipboard only supports plain text, so we keep spans here.
 */
object RichTextClipboard {
    var text: String? = null
        private set
    var spans: List<RichTextSpan> = emptyList()
        private set

    fun copy(text: String, spans: List<RichTextSpan>) {
        this.text = text
        this.spans = spans.toList()
        textBoxStateLog.d("RichTextClipboard: copied ${text.length} chars, ${spans.size} spans")
    }

    fun hasContent(): Boolean = !text.isNullOrEmpty()

    fun clear() {
        text = null
        spans = emptyList()
    }
}

class TextBoxState {

    companion object {
        /**
         * GLOBAL selected text box ID - used by canvas drawing to skip rendering.
         * Using companion object ensures this value is accessible from anywhere.
         */
        @Volatile
        @JvmStatic
        var globalSelectedTextBoxId: String? = null
            private set

        @JvmStatic
        fun setGlobalSelectedId(id: String?) {
            textBoxStateLog.d("setGlobalSelectedId: $id (was: $globalSelectedTextBoxId)")
            globalSelectedTextBoxId = id
        }
    }

    // Currently selected text box (publicly settable for styling changes)
    var selectedTextBox by mutableStateOf<TextAnnotation?>(null)

    // Toolbar mode: BASIC on initial selection, ADVANCED when tapped inside
    var toolbarMode by mutableStateOf(TextBoxToolbarMode.BASIC)
    
    // Non-Compose property for reading selected ID from drawing code
    // (mutableStateOf doesn't work correctly outside Compose snapshots)
    @Volatile
    var selectedTextBoxIdForDraw: String? = null
        private set

    // Currently editing text box (keyboard open)
    var editingTextBox by mutableStateOf<TextAnnotation?>(null)
        private set

    // IDENTICAL to SelectionState: displacement offset for dragging (in page coordinates)
    var textBoxDisplaceOffset by mutableStateOf<IntOffset?>(null)

    // IDENTICAL to SelectionState: start offset (top-left corner in page coordinates)
    var textBoxStartOffset by mutableStateOf<IntOffset?>(null)

    // IDENTICAL to SelectionState: bounding rect (in page coordinates)
    var textBoxRect by mutableStateOf<Rect?>(null)

    // Active resize handle being dragged
    var activeHandle by mutableStateOf(ResizeHandle.NONE)
        private set

    // Is user dragging the text box
    var isDragging by mutableStateOf(false)
        private set

    // Drag start position
    var dragStartX by mutableStateOf(0f)
        private set
    var dragStartY by mutableStateOf(0f)
        private set

    // Show context menu (long-press)
    var showContextMenu by mutableStateOf(false)
        private set
    var contextMenuX by mutableStateOf(0f)
        private set
    var contextMenuY by mutableStateOf(0f)
        private set

    // Show styling popup
    var showStylePopup by mutableStateOf(false)
        private set

    // Rich text editing state - for selection-based formatting
    // Selection range within text (for applying formatting to selected text only)
    var currentSelectionRange by mutableStateOf<IntRange?>(null)

    // Current rich text spans (populated when editing starts, updated during editing)
    var currentSpans by mutableStateOf<List<RichTextSpan>>(emptyList())

    // Current text content during editing (local until save - prevents e-ink flicker)
    var currentEditText by mutableStateOf<String?>(null)

    // Single-level undo/redo snapshot for text + spans during editing.
    var undoText: String? = null
    var undoSpans: List<RichTextSpan>? = null
    var redoText: String? = null
    var redoSpans: List<RichTextSpan>? = null

    /**
     * Select a text box - IDENTICAL pattern to SelectionState
     * Sets toolbar to BASIC mode (basic actions only)
     */
    fun selectTextBox(annotation: TextAnnotation?) {
        selectedTextBox = annotation
        selectedTextBoxIdForDraw = annotation?.id  // Also update non-Compose property
        setGlobalSelectedId(annotation?.id)  // Update GLOBAL singleton
        textBoxStateLog.d("selectTextBox: set selectedTextBoxIdForDraw=${selectedTextBoxIdForDraw}, this=${System.identityHashCode(this)}")
        editingTextBox = null
        showContextMenu = false
        showStylePopup = false
        toolbarMode = TextBoxToolbarMode.BASIC  // Start with basic toolbar

        // Initialize offsets like SelectionState does
        if (annotation != null) {
            textBoxStartOffset = IntOffset(annotation.x.toInt(), annotation.y.toInt())
            textBoxDisplaceOffset = IntOffset.Zero
            textBoxRect = Rect(
                annotation.x.toInt(),
                annotation.y.toInt(),
                (annotation.x + annotation.width).toInt(),
                (annotation.y + annotation.height).toInt()
            )
        } else {
            textBoxStartOffset = null
            textBoxDisplaceOffset = null
            textBoxRect = null
        }
    }

    /**
     * Update the selected text box after styling changes (bold, italic, etc.).
     * Does not modify any pending displacement so that dragging performed while
     * editing can still be applied when the user exits the overlay.
     */
    fun updateSelectedTextBox(annotation: TextAnnotation) {
        selectedTextBox = annotation
        setGlobalSelectedId(annotation.id)  // Keep global in sync
        // Update start offset to match new position
        textBoxStartOffset = IntOffset(annotation.x.toInt(), annotation.y.toInt())
        // Update rect to match new bounds
        textBoxRect = Rect(
            annotation.x.toInt(),
            annotation.y.toInt(),
            (annotation.x + annotation.width).toInt(),
            (annotation.y + annotation.height).toInt()
        )
    }

    /**
     * Reset all state - IDENTICAL to SelectionState.reset()
     */
    fun reset() {
        selectedTextBox = null
        selectedTextBoxIdForDraw = null  // Also clear non-Compose property
        setGlobalSelectedId(null)  // Clear GLOBAL singleton
        editingTextBox = null
        textBoxDisplaceOffset = null
        textBoxStartOffset = null
        textBoxRect = null
        activeHandle = ResizeHandle.NONE
        isDragging = false
        showContextMenu = false
        showStylePopup = false
        toolbarMode = TextBoxToolbarMode.BASIC  // Reset to basic
    }

    /**
     * Start editing a text box (opens keyboard)
     * Sets toolbar to ADVANCED mode (full formatting controls)
     */
    fun startEditing(annotation: TextAnnotation) {
        setGlobalSelectedId(annotation.id)  // Keep global in sync
        selectedTextBox = annotation
        editingTextBox = annotation
        currentEditText = annotation.content  // Initialize local text state
        showContextMenu = false
        toolbarMode = TextBoxToolbarMode.ADVANCED  // Switch to advanced toolbar
    }

    /**
     * Stop editing (close keyboard)
     */
    fun stopEditing() {
        editingTextBox = null
        clearEditingState()
    }

    /**
     * Update the current selection range for selection-based formatting
     */
    fun updateSelectionRange(range: IntRange?) {
        currentSelectionRange = range
    }

    /**
     * Update the current rich text spans during editing
     */
    fun updateSpans(spans: List<RichTextSpan>) {
        currentSpans = spans
    }

    /**
     * Update the current text content during editing (local state)
     */
    fun updateEditText(text: String) {
        currentEditText = text
    }

    /**
     * Clear all editing-related state
     */
    fun clearEditingState() {
        currentSelectionRange = null
        currentSpans = emptyList()
        currentEditText = null
        undoText = null
        undoSpans = null
        redoText = null
        redoSpans = null
    }

    /**
     * Clear all selection state
     */
    fun clearSelection() {
        selectedTextBox = null
        selectedTextBoxIdForDraw = null  // Also clear non-Compose property
        setGlobalSelectedId(null)  // Clear GLOBAL singleton
        editingTextBox = null
        activeHandle = ResizeHandle.NONE
        isDragging = false
        showContextMenu = false
        showStylePopup = false
    }

    /**
     * Capture the current text and spans so that a subsequent editing action
     * (typing or formatting) can be undone. Only a single level of undo is kept.
     */
    fun snapshotForUndo() {
        undoText = currentEditText ?: selectedTextBox?.content
        undoSpans = currentSpans.toList()
    }

    /**
     * Restore the last snapshot of text + spans, if any.
     * Saves current state to redo before restoring.
     */
    fun undoLastEdit() {
        val textSnapshot = undoText
        val spansSnapshot = undoSpans
        if (textSnapshot == null && spansSnapshot == null) return

        // Save current state to redo before restoring
        redoText = currentEditText ?: selectedTextBox?.content
        redoSpans = currentSpans.toList()

        if (textSnapshot != null) {
            currentEditText = textSnapshot
        }
        if (spansSnapshot != null) {
            currentSpans = spansSnapshot
        }

        undoText = null
        undoSpans = null
    }

    /**
     * Restore the last redo snapshot of text + spans, if any.
     * Saves current state to undo before restoring.
     */
    fun redoLastEdit() {
        val textSnapshot = redoText
        val spansSnapshot = redoSpans
        if (textSnapshot == null && spansSnapshot == null) return

        // Save current state to undo before restoring
        undoText = currentEditText ?: selectedTextBox?.content
        undoSpans = currentSpans.toList()

        if (textSnapshot != null) {
            currentEditText = textSnapshot
        }
        if (spansSnapshot != null) {
            currentSpans = spansSnapshot
        }

        redoText = null
        redoSpans = null
    }

    /**
     * Check if redo is available
     */
    fun canRedo(): Boolean = redoText != null || redoSpans != null

    /**
     * Check if undo is available
     */
    fun canUndo(): Boolean = undoText != null || undoSpans != null

    /**
     * Start dragging the selected text box
     */
    fun startDragging(x: Float, y: Float) {
        isDragging = true
        dragStartX = x
        dragStartY = y
    }

    /**
     * Stop dragging
     */
    fun stopDragging() {
        isDragging = false
    }

    /**
     * Start resizing with a specific handle
     */
    fun startResizing(handle: ResizeHandle, x: Float, y: Float) {
        activeHandle = handle
        dragStartX = x
        dragStartY = y
    }

    /**
     * Stop resizing
     */
    fun stopResizing() {
        activeHandle = ResizeHandle.NONE
    }

    /**
     * Show context menu at position
     */
    fun showContextMenuAt(x: Float, y: Float) {
        contextMenuX = x
        contextMenuY = y
        showContextMenu = true
    }

    /**
     * Hide context menu
     */
    fun hideContextMenu() {
        showContextMenu = false
    }

    /**
     * Toggle style popup
     */
    fun toggleStylePopup() {
        showStylePopup = !showStylePopup
    }

    /**
     * Check if an annotation is currently selected
     */
    fun isSelected(annotation: TextAnnotation): Boolean {
        return selectedTextBox?.id == annotation.id
    }

    /**
     * Check if an annotation is currently being edited
     */
    fun isEditing(annotation: TextAnnotation): Boolean {
        return editingTextBox?.id == annotation.id
    }

    /**
     * Check if any text box is selected
     */
    fun hasSelection(): Boolean = selectedTextBox != null

    /**
     * Get the resize handle at a point (with touch tolerance)
     */
    fun getHandleAtPoint(
        annotation: TextAnnotation,
        x: Float,
        y: Float,
        tolerance: Float = 30f
    ): ResizeHandle {
        val rect = RectF(
            annotation.x,
            annotation.y,
            annotation.x + annotation.width,
            annotation.y + annotation.height
        )

        // Check corners first (they take priority)
        if (isNearPoint(x, y, rect.left, rect.top, tolerance)) return ResizeHandle.TOP_LEFT
        if (isNearPoint(x, y, rect.right, rect.top, tolerance)) return ResizeHandle.TOP_RIGHT
        if (isNearPoint(x, y, rect.left, rect.bottom, tolerance)) return ResizeHandle.BOTTOM_LEFT
        if (isNearPoint(x, y, rect.right, rect.bottom, tolerance)) return ResizeHandle.BOTTOM_RIGHT

        // Check edge midpoints
        if (isNearPoint(x, y, rect.centerX(), rect.top, tolerance)) return ResizeHandle.TOP
        if (isNearPoint(x, y, rect.centerX(), rect.bottom, tolerance)) return ResizeHandle.BOTTOM
        if (isNearPoint(x, y, rect.left, rect.centerY(), tolerance)) return ResizeHandle.LEFT
        if (isNearPoint(x, y, rect.right, rect.centerY(), tolerance)) return ResizeHandle.RIGHT

        return ResizeHandle.NONE
    }

    /**
     * Check if a point is inside a text box
     */
    fun isPointInsideTextBox(annotation: TextAnnotation, x: Float, y: Float): Boolean {
        return x >= annotation.x && x <= annotation.x + annotation.width &&
               y >= annotation.y && y <= annotation.y + annotation.height
    }

    /**
     * Calculate new bounds after resize
     */
    fun calculateResizedBounds(
        annotation: TextAnnotation,
        handle: ResizeHandle,
        deltaX: Float,
        deltaY: Float,
        minWidth: Float = 50f,
        minHeight: Float = 30f
    ): RectF {
        var left = annotation.x
        var top = annotation.y
        var right = annotation.x + annotation.width
        var bottom = annotation.y + annotation.height

        when (handle) {
            ResizeHandle.TOP_LEFT -> {
                left += deltaX
                top += deltaY
            }
            ResizeHandle.TOP_RIGHT -> {
                right += deltaX
                top += deltaY
            }
            ResizeHandle.BOTTOM_LEFT -> {
                left += deltaX
                bottom += deltaY
            }
            ResizeHandle.BOTTOM_RIGHT -> {
                right += deltaX
                bottom += deltaY
            }
            ResizeHandle.TOP -> top += deltaY
            ResizeHandle.BOTTOM -> bottom += deltaY
            ResizeHandle.LEFT -> left += deltaX
            ResizeHandle.RIGHT -> right += deltaX
            ResizeHandle.NONE -> { /* no change */ }
        }

        // Enforce minimum size
        if (right - left < minWidth) {
            if (handle == ResizeHandle.LEFT || handle == ResizeHandle.TOP_LEFT || handle == ResizeHandle.BOTTOM_LEFT) {
                left = right - minWidth
            } else {
                right = left + minWidth
            }
        }
        if (bottom - top < minHeight) {
            if (handle == ResizeHandle.TOP || handle == ResizeHandle.TOP_LEFT || handle == ResizeHandle.TOP_RIGHT) {
                top = bottom - minHeight
            } else {
                bottom = top + minHeight
            }
        }

        return RectF(left, top, right, bottom)
    }

    private fun isNearPoint(x: Float, y: Float, px: Float, py: Float, tolerance: Float): Boolean {
        return abs(x - px) < tolerance && abs(y - py) < tolerance
    }
}
