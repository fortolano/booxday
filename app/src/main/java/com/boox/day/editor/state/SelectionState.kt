package com.boox.day.editor.state

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toOffset
import androidx.core.graphics.createBitmap
import com.boox.day.TAG
import com.boox.day.data.db.Attachment
import com.boox.day.data.db.Image
import com.boox.day.data.db.Stroke
import com.boox.day.data.model.SimplePointF
import com.boox.day.editor.PageView
import com.boox.day.editor.drawing.drawImage
import com.boox.day.editor.utils.imageBoundsInt
import com.boox.day.editor.utils.offsetAttachment
import com.boox.day.editor.utils.offsetImage
import com.boox.day.editor.utils.offsetStroke
import com.boox.day.editor.utils.AnimationModeOwners
import com.boox.day.io.copyBitmapToClipboard
import com.boox.day.ui.showHint
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import java.util.Date
import java.util.UUID
import kotlin.math.max

class SelectionState {
    // Active resize handle being dragged (for corner resize)
    var activeResizeHandle by mutableStateOf(ResizeHandle.NONE)
    // all coordinates should be in page coordinates
    var firstPageCut by mutableStateOf<List<SimplePointF>?>(null)
    var secondPageCut by mutableStateOf<List<SimplePointF>?>(null)
    var selectedStrokes by mutableStateOf<List<Stroke>?>(null)
    var selectedImages by mutableStateOf<List<Image>?>(null)
    var selectedAttachments by mutableStateOf<List<Attachment>?>(null)

    // TODO: Bitmap should be change, if scale changes.
    var selectedBitmap by mutableStateOf<Bitmap?>(null)

    var selectionStartOffset by mutableStateOf<IntOffset?>(null)
    var selectionDisplaceOffset by mutableStateOf<IntOffset?>(null)
    var selectionRect by mutableStateOf<Rect?>(null)
    var placementMode by mutableStateOf<PlacementMode?>(null)

    fun reset() {
        selectedStrokes = null
        selectedImages = null
        selectedAttachments = null
        secondPageCut = null
        firstPageCut = null
        selectedBitmap = null
        selectionRect = null
        selectionStartOffset = null
        selectionDisplaceOffset = null
        placementMode = null
        activeResizeHandle = ResizeHandle.NONE
        AnimationModeOwners.release(SELECTION_OWNER)
    }

    fun isNonEmpty(): Boolean {
        return !selectedStrokes.isNullOrEmpty() || !selectedImages.isNullOrEmpty() || !selectedAttachments.isNullOrEmpty()
    }

    fun isResizable(): Boolean {
        return selectedImages?.count() == 1 && selectedStrokes.isNullOrEmpty()
    }

    fun resizeImages(scale: Int, scope: CoroutineScope, page: PageView) {
        val selectedImagesCopy = selectedImages?.map { image ->
            image.copy(
                height = image.height + (image.height * scale / 100),
                width = image.width + (image.width * scale / 100)
            )
        }

        // Ensure selected images are not null or empty
        if (selectedImagesCopy.isNullOrEmpty()) {
            showHint("For now, strokes cannot be resized", scope)
            return
        }

        selectedImages = selectedImagesCopy
        // Adjust displacement offset by half the size change
        val sizeChange = selectedImagesCopy.firstOrNull()?.let { image ->
            IntOffset(
                x = (image.width * scale / 200),
                y = (image.height * scale / 200)
            )
        } ?: IntOffset.Zero

        val pageBounds = imageBoundsInt(selectedImagesCopy)
        selectionRect = page.toScreenCoordinates(pageBounds)

        selectionDisplaceOffset =
            selectionDisplaceOffset?.let { it - sizeChange }
                ?: IntOffset.Zero

        val selectedBitmapNew = createBitmap(pageBounds.width(), pageBounds.height())
        val selectedCanvas = Canvas(selectedBitmapNew)
        selectedImagesCopy.forEach {
            drawImage(
                page.context,
                selectedCanvas,
                it,
                Offset(-it.x.toFloat(), -it.y.toFloat())
            )
        }

        // set state
        selectedBitmap = selectedBitmapNew
    }

    @Suppress("UNUSED_PARAMETER")
    fun resizeStrokes(scale: Int, scope: CoroutineScope, page: PageView) {
        //TODO: implement this
    }

    /**
     * Calculate new bounds after corner resize for images.
     * Similar to TextBoxState.calculateResizedBounds but for images (Int coords).
     */
    fun calculateImageResizedBounds(
        image: Image,
        handle: ResizeHandle,
        deltaX: Int,
        deltaY: Int,
        minWidth: Int = 50,
        minHeight: Int = 50
    ): Rect {
        var left = image.x
        var top = image.y
        var right = image.x + image.width
        var bottom = image.y + image.height

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
            else -> { /* Only corner handles supported for images */ }
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

        return Rect(left, top, right, bottom)
    }

    /**
     * Apply resize to the selected image based on corner drag.
     * Updates selectedImages, selectionRect, selectionStartOffset, and selectedBitmap.
     */
    fun applyImageResize(
        handle: ResizeHandle,
        deltaX: Int,
        deltaY: Int,
        page: PageView
    ) {
        val currentImage = selectedImages?.firstOrNull() ?: return

        val newBounds = calculateImageResizedBounds(currentImage, handle, deltaX, deltaY)
        val newWidth = newBounds.width()
        val newHeight = newBounds.height()

        // Create updated image with new bounds
        val updatedImage = currentImage.copy(
            x = newBounds.left,
            y = newBounds.top,
            width = newWidth,
            height = newHeight
        )

        // Update selection state
        selectedImages = listOf(updatedImage)
        selectionStartOffset = IntOffset(newBounds.left, newBounds.top)
        selectionRect = newBounds

        // Regenerate bitmap at new size
        val newBitmap = createBitmap(max(1, newWidth), max(1, newHeight))
        val canvas = Canvas(newBitmap)
        drawImage(
            page.context,
            canvas,
            updatedImage,
            Offset(-updatedImage.x.toFloat(), -updatedImage.y.toFloat())
        )
        selectedBitmap = newBitmap

        // Reset displacement since we're applying resize directly to position
        selectionDisplaceOffset = IntOffset.Zero
    }

    /**
     * Deletes the currently selected strokes, images, and attachments from the page.
     *
     * This function identifies the selected images, strokes, and attachments, removes them from the given [page],
     * and creates a list of undo [Operation]s. After deletion, it resets the selection state.
     *
     * @param page The [PageView] from which the selected items should be removed.
     * @return A list of [Operation]s that can be used to undo the deletion (e.g., re-adding the deleted items).
     */
    fun deleteSelection(page: PageView): List<Operation> {
        val operationList = mutableListOf<Operation>()

        // Save the selection area before reset() clears it
        val areaToRedraw = selectionRect?.let { Rect(it) }

        val selectedImagesToRemove = selectedImages
        if (!selectedImagesToRemove.isNullOrEmpty()) {
            val imageIds: List<String> = selectedImagesToRemove.map { it.id }
            Log.i(TAG, "removing images")
            page.removeImages(imageIds)
            operationList += Operation.AddImage(selectedImagesToRemove)
        }
        val selectedStrokesToRemove = selectedStrokes
        if (!selectedStrokesToRemove.isNullOrEmpty()) {
            val strokeIds: List<String> = selectedStrokesToRemove.map { it.id }
            Log.i(TAG, "removing strokes")
            page.removeStrokes(strokeIds)
            operationList += Operation.AddStroke(selectedStrokesToRemove)
        }
        val selectedAttachmentsToRemove = selectedAttachments
        if (!selectedAttachmentsToRemove.isNullOrEmpty()) {
            val attachmentIds: List<String> = selectedAttachmentsToRemove.map { it.id }
            Log.i(TAG, "removing attachments")
            page.removeAttachments(attachmentIds)
            operationList += Operation.AddAttachment(selectedAttachmentsToRemove)
        }

        // Redraw the area to clear ghost borders after deletion
        areaToRedraw?.let { page.drawAreaPageCoordinates(it) }

        reset()
        return operationList
    }

    fun duplicateSelection() {
        // set operation to paste only
        placementMode = PlacementMode.Paste
        if (!selectedStrokes.isNullOrEmpty())
        // change the selected stokes' ids - it's a copy
            selectedStrokes = selectedStrokes!!.map {
                it.copy(
                    id = UUID
                        .randomUUID()
                        .toString(),
                    createdAt = Date()
                )
            }
        if (!selectedImages.isNullOrEmpty())
            selectedImages = selectedImages!!.map {
                it.copy(
                    id = UUID
                        .randomUUID()
                        .toString(),
                    createdAt = Date()
                )
            }
        if (!selectedAttachments.isNullOrEmpty())
            selectedAttachments = selectedAttachments!!.map {
                it.copy(
                    id = UUID
                        .randomUUID()
                        .toString(),
                    createdAt = Date()
                )
            }
        // move the selection a bit, to show the copy
        selectionDisplaceOffset = IntOffset(
            x = selectionDisplaceOffset!!.x + 50,
            y = selectionDisplaceOffset!!.y + 50,
        )
    }

    // Moves strokes, images, and attachments, and redraws canvas.
    fun applySelectionDisplace(page: PageView): List<Operation>? {

        if (selectionDisplaceOffset == null) return null
        if (selectionRect == null) return null

        // get snapshot of the selection
        val selectedStrokesCopy = selectedStrokes
        val selectedImagesCopy = selectedImages
        val selectedAttachmentsCopy = selectedAttachments
        val offset = selectionDisplaceOffset!!

        // Save original zone before offsetting (to redraw/clear the old position)
        val originalZone = Rect(selectionRect!!)
        val finalZone = selectionRect!!
        finalZone.offset(offset.x, offset.y)

        // collect undo operations for strokes, images, and attachments together, as a single change
        val operationList = mutableListOf<Operation>()

        if (!selectedStrokesCopy.isNullOrEmpty()) {
            val displacedStrokes = selectedStrokesCopy.map {
                offsetStroke(it, offset = offset.toOffset())
            }

            if (placementMode == PlacementMode.Move)
                page.removeStrokes(selectedStrokesCopy.map { it.id })

            page.addStrokes(displacedStrokes)


            if (offset.x != 0 || offset.y != 0 || placementMode == PlacementMode.Paste) {
                // A displacement happened or this is a paste commit - create history for this
                operationList += Operation.DeleteStroke(displacedStrokes.map { it.id })
                // in case we are on a move operation, this history point re-adds the original strokes
                if (placementMode == PlacementMode.Move)
                    operationList += Operation.AddStroke(selectedStrokesCopy)
            }
        }
        if (!selectedImagesCopy.isNullOrEmpty()) {
            Log.i(TAG, "Commit images to history.")

            val displacedImages = selectedImagesCopy.map {
                offsetImage(it, offset = offset.toOffset())
            }
            if (placementMode == PlacementMode.Move)
                page.removeImages(selectedImagesCopy.map { it.id })

            page.addImage(displacedImages)

            if (offset.x != 0 || offset.y != 0 || placementMode == PlacementMode.Paste) {
                // TODO: find why sometimes we add two times same operation.
                // A displacement happened or this is a paste commit - create history for this
                // To undo changes we first remove image
                operationList += Operation.DeleteImage(displacedImages.map { it.id })
                // then add the original images, only if we intended to move it.
                if (placementMode == PlacementMode.Move)
                    operationList += Operation.AddImage(selectedImagesCopy)
            }
        }
        if (!selectedAttachmentsCopy.isNullOrEmpty()) {
            Log.i(TAG, "Commit attachments to history.")

            val displacedAttachments = selectedAttachmentsCopy.map {
                offsetAttachment(it, offset = offset.toOffset())
            }
            if (placementMode == PlacementMode.Move)
                page.removeAttachments(selectedAttachmentsCopy.map { it.id })

            page.addAttachments(displacedAttachments)

            if (offset.x != 0 || offset.y != 0 || placementMode == PlacementMode.Paste) {
                // A displacement happened or this is a paste commit - create history for this
                operationList += Operation.DeleteAttachment(displacedAttachments.map { it.id })
                if (placementMode == PlacementMode.Move)
                    operationList += Operation.AddAttachment(selectedAttachmentsCopy)
            }
        }
        // Redraw both the original position (to clear ghost artifacts) and the final position
        page.drawAreaPageCoordinates(originalZone)
        page.drawAreaPageCoordinates(finalZone)
        return operationList
    }

    fun selectionToClipboard(scrollPos: Offset, context: Context): ClipboardContent {

        val strokes = selectedStrokes?.map {
            offsetStroke(it, offset = -scrollPos)
        }

        val images = selectedImages?.map {
            it.copy(x = it.x - scrollPos.x.toInt(), y = it.y - scrollPos.y.toInt())
        }

        selectedBitmap?.let {
            copyBitmapToClipboard(context, it)
        }
        return ClipboardContent(
            strokes = strokes ?: emptyList(),
            images = images ?: emptyList()
        )
    }
}

/** AnimationModeOwners name for a selection being moved/resized (Phase 4). */
const val SELECTION_OWNER = "selection"
