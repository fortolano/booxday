package com.boox.day.editor.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.boox.day.data.PageDataManager
import com.boox.day.data.db.TextAnnotation
import com.boox.day.data.db.TextAnnotationRepository
import com.boox.day.editor.DrawCanvas
import com.boox.day.editor.state.EditorState
import com.boox.day.editor.state.ResizeHandle
import com.boox.day.editor.state.RichTextClipboard
import com.boox.day.utils.TaggedLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val log = TaggedLog("TextBoxTapDetector")

/**
 * Detects taps on text boxes and handles selection/editing.
 * This composable should be placed above the canvas but below other UI elements.
 */
@Composable
fun TextBoxTapDetector(
    state: EditorState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val textAnnotationRepository = remember { TextAnnotationRepository(context) }

    val textBoxState = state.textBoxState
    val pageId = state.currentPageId
    val scroll = state.pageView.scroll
    val zoomLevel by state.pageView.zoomLevel.collectAsState()

    // Note: Long-press is now handled by EditorGestureReceiver (unified with images/attachments)
    // This detector only handles taps for entering edit mode on already-selected text boxes
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(pageId, zoomLevel) {
                detectTapGestures(
                    onTap = { tapPosition ->
                        handleTap(
                            tapPosition = tapPosition,
                            pageId = pageId,
                            scroll = scroll,
                            zoomLevel = zoomLevel,
                            state = state,
                            scope = scope,
                            textAnnotationRepository = textAnnotationRepository
                        )
                    }
                    // Long-press removed - now handled by EditorGestureReceiver
                )
            }
    )
}

/**
 * Handle a tap on the canvas.
 * - If text placement mode is active: create new text annotation at tap position
 * - If tap is on a text box: select it (or enter edit mode if already selected)
 * - If tap is outside any text box: clear selection
 */
private fun handleTap(
    tapPosition: Offset,
    pageId: String,
    scroll: Offset,
    zoomLevel: Float,
    state: EditorState,
    scope: CoroutineScope,
    textAnnotationRepository: TextAnnotationRepository
) {
    val textBoxState = state.textBoxState

    // Convert screen coordinates to page coordinates
    val pageX = (tapPosition.x / zoomLevel) + scroll.x
    val pageY = (tapPosition.y / zoomLevel) + scroll.y

    log.d("Tap at screen (${tapPosition.x}, ${tapPosition.y}) -> page ($pageX, $pageY)")

    // Get all text annotations for this page
    val annotations = PageDataManager.getTextAnnotations(pageId)

    // Find which annotation (if any) was tapped
    val tappedAnnotation = findAnnotationAtPoint(annotations, pageX, pageY)

    when {
        tappedAnnotation != null -> {
            val currentlySelected = textBoxState.selectedTextBox

            if (currentlySelected?.id == tappedAnnotation.id) {
                // Already selected - check if we should enter edit mode or handle resize
                val handle = textBoxState.getHandleAtPoint(tappedAnnotation, pageX, pageY, 40f)
                if (handle == ResizeHandle.NONE) {
                    // Tap inside the box - enter edit mode
                    log.i("Entering edit mode for text box ${tappedAnnotation.id}")
                    textBoxState.startEditing(tappedAnnotation)
                } else {
                    // Tap on a handle - could start resize (handled by drag)
                    log.d("Tap on resize handle: $handle")
                }
            } else {
                // Select the tapped annotation
                log.i("Selecting text box ${tappedAnnotation.id}")
                textBoxState.selectTextBox(tappedAnnotation)
                // CRITICAL: Disable drawing IMMEDIATELY (same as lasso selection)
                state.isDrawing = false
                // CRITICAL: Refresh canvas to hide the selected text annotation
                DrawCanvas.forceUpdate.tryEmit(null)
            }
        }

        textBoxState.hasSelection() -> {
            // Tap outside any text box - clear selection and refresh canvas
            log.i("Clearing text box selection")
            textBoxState.clearSelection()
            // Re-enable drawing after deselection
            state.isDrawing = true
            // CRITICAL: Refresh canvas so the text box is rendered (was hidden while selected)
            DrawCanvas.forceUpdate.tryEmit(null)
        }

        else -> {
            // No text boxes involved - let other gesture handlers deal with it
            log.d("Tap not on any text box")
        }
    }
}

// Note: handleLongPress removed - long-press is now handled by EditorGestureReceiver
// which provides unified selection behavior for all inline elements (images, attachments, text boxes)

/**
 * Find the annotation at a given point (in page coordinates).
 * Returns the topmost annotation if multiple overlap.
 */
private fun findAnnotationAtPoint(
    annotations: List<TextAnnotation>,
    x: Float,
    y: Float
): TextAnnotation? {
    // Check in reverse order (last added = topmost)
    return annotations.findLast { annotation ->
        x >= annotation.x &&
        x <= annotation.x + annotation.width &&
        y >= annotation.y &&
        y <= annotation.y + annotation.height
    }
}

