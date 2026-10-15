package com.boox.day.editor.state

import android.graphics.Rect
import com.boox.day.data.db.Attachment
import com.boox.day.data.db.Image
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.TextAnnotation
import com.boox.day.editor.DrawCanvas
import com.boox.day.editor.PageView
import com.boox.day.editor.utils.attachmentBoundsInt
import com.boox.day.editor.utils.imageBoundsInt
import com.boox.day.editor.utils.strokeBounds
import com.boox.day.editor.utils.textAnnotationBoundsInt
import com.boox.day.ui.SnackConf
import com.boox.day.ui.SnackState
import com.boox.day.utils.logCallStack
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull


sealed class Operation {
    data class DeleteStroke(val strokeIds: List<String>) : Operation()
    data class AddStroke(val strokes: List<Stroke>) : Operation()
    data class AddImage(val images: List<Image>) : Operation()
    data class DeleteImage(val imageIds: List<String>) : Operation()
    data class AddTextAnnotation(val annotations: List<TextAnnotation>) : Operation()
    data class DeleteTextAnnotation(val annotationIds: List<String>) : Operation()
    data class AddAttachment(val attachments: List<Attachment>) : Operation()
    data class DeleteAttachment(val attachmentIds: List<String>) : Operation()
    // Update operations for position/size changes (used by text box move, image resize)
    data class UpdateTextAnnotation(val before: TextAnnotation, val after: TextAnnotation) : Operation()
    data class UpdateImage(val before: Image, val after: Image) : Operation()
}

typealias OperationBlock = List<Operation>
typealias OperationList = MutableList<OperationBlock>

enum class UndoRedoType {
    Undo,
    Redo
}

sealed class HistoryBusActions {
    data class RegisterHistoryOperationBlock(val operationBlock: OperationBlock) :
        HistoryBusActions()

    data class MoveHistory(val type: UndoRedoType) : HistoryBusActions()
}

class History(pageView: PageView) {
    private var undoList: OperationList = mutableListOf()
    private var redoList: OperationList = mutableListOf()
    private val pageModel = pageView

    suspend fun handleHistoryBusActions(actions: HistoryBusActions) {
        when (actions) {
            is HistoryBusActions.MoveHistory -> {
                // Wait for commit to history to complete
                if (actions.type == UndoRedoType.Undo) {
                    DrawCanvas.commitCompletion = CompletableDeferred()
                    DrawCanvas.commitHistorySignalImmediately.emit(Unit)
                    // Timeout after 200ms - proceed even if collector is slow/unavailable
                    val completed = withTimeoutOrNull(200L) {
                        DrawCanvas.commitCompletion.await()
                    }
                    if (completed == null) {
                        android.util.Log.w("History", "Commit await timeout - proceeding with undo")
                    }
                }
                val zoneAffected = undoRedo(type = actions.type)
                if (zoneAffected != null) {
                    pageModel.drawAreaPageCoordinates(zoneAffected)
                    //moved to refresh after drawing
                    DrawCanvas.refreshUi.emit(Unit)
                    // E-ink fix: Force display refresh after undo/redo
                    // Without this, bitmap updates but frozen display doesn't show them
                    DrawCanvas.forceUnfreeze()
                } else {
                    SnackState.globalSnackFlow.emit(
                        SnackConf(
                            key = "history-action",
                            text = "Nothing to undo/redo",
                            duration = SnackConf.DURATION_QUICK,
                        )
                    )
                }
            }

            is HistoryBusActions.RegisterHistoryOperationBlock -> {
                addOperationsToHistory(actions.operationBlock)
            }

        }
    }


    fun cleanHistory() {
        undoList.clear()
        redoList.clear()
    }

    private fun treatOperation(operation: Operation): Pair<Operation, Rect> {
        when (operation) {
            is Operation.AddStroke -> {
                pageModel.addStrokes(operation.strokes)
                return Operation.DeleteStroke(strokeIds = operation.strokes.map { it.id }) to strokeBounds(
                    operation.strokes
                )
            }

            is Operation.DeleteStroke -> {
                val strokes = pageModel.getStrokes(operation.strokeIds).filterNotNull()
                // CROSS-DAY UNDO FIX (A1, Oct 2026): remove only the strokes found on this page;
                // IDs that aren't here belong to another page and must not be deleted.
                if (strokes.isNotEmpty()) {
                    pageModel.removeStrokes(strokes.map { it.id })
                }
                return Operation.AddStroke(strokes = strokes) to strokeBounds(strokes)
            }

            is Operation.AddImage -> {
                pageModel.addImage(operation.images)
                return Operation.DeleteImage(imageIds = operation.images.map { it.id }) to imageBoundsInt(
                    operation.images
                )
            }

            is Operation.DeleteImage -> {
                val images = pageModel.getImages(operation.imageIds).filterNotNull()
                pageModel.removeImages(operation.imageIds)
                return Operation.AddImage(images = images) to imageBoundsInt(images)
            }

            is Operation.AddTextAnnotation -> {
                pageModel.addTextAnnotations(operation.annotations)
                return Operation.DeleteTextAnnotation(annotationIds = operation.annotations.map { it.id }) to textAnnotationBoundsInt(
                    operation.annotations
                )
            }

            is Operation.DeleteTextAnnotation -> {
                val annotations = pageModel.getTextAnnotations(operation.annotationIds).filterNotNull()
                pageModel.removeTextAnnotations(operation.annotationIds)
                return Operation.AddTextAnnotation(annotations = annotations) to textAnnotationBoundsInt(annotations)
            }

            is Operation.AddAttachment -> {
                pageModel.addAttachments(operation.attachments)
                return Operation.DeleteAttachment(attachmentIds = operation.attachments.map { it.id }) to attachmentBoundsInt(
                    operation.attachments
                )
            }

            is Operation.DeleteAttachment -> {
                val attachments = pageModel.getAttachments(operation.attachmentIds).filterNotNull()
                pageModel.removeAttachments(operation.attachmentIds)
                return Operation.AddAttachment(attachments = attachments) to attachmentBoundsInt(attachments)
            }

            is Operation.UpdateTextAnnotation -> {
                pageModel.removeTextAnnotations(listOf(operation.after.id))
                pageModel.addTextAnnotations(listOf(operation.before))
                return Operation.UpdateTextAnnotation(
                    before = operation.after,
                    after = operation.before
                ) to textAnnotationBoundsInt(listOf(operation.before, operation.after))
            }

            is Operation.UpdateImage -> {
                pageModel.removeImages(listOf(operation.after.id))
                pageModel.addImage(listOf(operation.before))
                return Operation.UpdateImage(
                    before = operation.after,
                    after = operation.before
                ) to imageBoundsInt(listOf(operation.before, operation.after))
            }
        }
    }

    private fun undoRedo(type: UndoRedoType): Rect? {
        val originList =
            if (type == UndoRedoType.Undo) undoList else redoList
        val targetList =
            if (type == UndoRedoType.Undo) redoList else undoList

        if (originList.isEmpty()) return null

        val operationBlock = originList.removeAt(originList.lastIndex)
        val revertOperations = mutableListOf<Operation>()
        val zoneAffected = Rect()
        for (operation in operationBlock) {
            val (cancelOperation, thisZoneAffected) = treatOperation(operation = operation)
            revertOperations.add(cancelOperation)
            zoneAffected.union(thisZoneAffected)
        }
        targetList.add(revertOperations.reversed())

        // update the affected zone
        return zoneAffected
    }

    fun addOperationsToHistory(operations: OperationBlock) {
        if (operations.isEmpty()) {
            logCallStack("History: No operations to add to history")
            return
        }
        undoList.add(operations)
        if (undoList.size > 10) undoList.removeAt(0)
        redoList.clear()
    }
}