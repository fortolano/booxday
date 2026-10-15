package com.boox.day.editor.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import com.boox.day.data.db.Stroke as DbStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.platform.LocalDensity
import com.boox.day.R
import com.boox.day.editor.DrawCanvas
import com.boox.day.editor.EditorControlTower
import com.boox.day.editor.state.ResizeHandle
import com.boox.day.editor.ui.toolbar.ToolbarButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import compose.icons.FeatherIcons
import compose.icons.feathericons.Copy
import compose.icons.feathericons.Scissors

val strokeStyle = Stroke(
    width = 2f,
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
)

@Composable
@ExperimentalComposeUiApi
@ExperimentalFoundationApi
fun SelectedBitmap(
    context: Context,
    controlTower: EditorControlTower,
    onConvertSelectionToTasks: ((List<DbStroke>) -> Unit)? = null
) {
    val density = LocalDensity.current
    val selectionState = controlTower.getSnapshotOfSelectionState()
    if (selectionState.selectedBitmap == null) return

    var selectionDisplaceOffset =
        controlTower.page.applyZoom(selectionState.selectionDisplaceOffset ?: return)
    val selectionRect =
        controlTower.page.toScreenCoordinates(selectionState.selectionRect ?: return)
    val selectionStartOffset =
        controlTower.page.applyZoom(selectionState.selectionStartOffset ?: IntOffset(0, 0))


    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()  // Prevent selection from overflowing beyond canvas bounds
            .noRippleClickable {
                controlTower.applySelectionDisplace()
                selectionState.reset()
                controlTower.setIsDrawing(true)
            }) {
        Image(
            bitmap = selectionState.selectedBitmap!!.asImageBitmap(),
            contentDescription = "Selection bitmap",
            modifier = Modifier
                .offset { selectionStartOffset + selectionDisplaceOffset }
                .drawBehind {
                    drawRect(
                        color = Color.Gray,
                        topLeft = Offset(0f, 0f),
                        size = size,
                        style = strokeStyle
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        selectionState.selectionDisplaceOffset =
                            controlTower.page.removeZoom(
                                selectionDisplaceOffset + dragAmount.round()
                            )
                        selectionDisplaceOffset =
                            controlTower.page.applyZoom(
                                selectionState.selectionDisplaceOffset ?: return@detectDragGestures
                            )
                    }
                }
                .combinedClickable(
                    indication = null, interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                    onDoubleClick = { controlTower.duplicateSelection() }
                )
        )

        // Corner resize handles (only for single image selection)
        if (selectionState.isResizable()) {
            val handleSize = 14.dp
            val handleSizePx = with(density) { handleSize.toPx() }
            val halfHandle = handleSizePx / 2
            val boxWidthPx = selectionRect.width().toFloat()
            val boxHeightPx = selectionRect.height().toFloat()

            // Helper composable for resize handles
            @Composable
            fun ResizeHandleBox(
                handleType: ResizeHandle,
                xOffset: Float,
                yOffset: Float
            ) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (selectionStartOffset.x + selectionDisplaceOffset.x + xOffset - halfHandle).toInt(),
                                (selectionStartOffset.y + selectionDisplaceOffset.y + yOffset - halfHandle).toInt()
                            )
                        }
                        .size(handleSize)
                        .background(Color.White)
                        .drawBehind {
                            drawRect(
                                color = Color.Black,
                                style = Stroke(width = 2f)
                            )
                        }
                        .pointerInput(handleType) {
                            detectDragGestures(
                                onDragStart = {
                                    selectionState.activeResizeHandle = handleType
                                },
                                onDragEnd = {
                                    selectionState.activeResizeHandle = ResizeHandle.NONE
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    // Convert drag to page coordinates
                                    val pageScaledDrag = controlTower.page.removeZoom(dragAmount.round())
                                    selectionState.applyImageResize(
                                        handleType,
                                        pageScaledDrag.x,
                                        pageScaledDrag.y,
                                        controlTower.page
                                    )
                                }
                            )
                        }
                )
            }

            // Draw 4 corner handles
            ResizeHandleBox(ResizeHandle.TOP_LEFT, 0f, 0f)
            ResizeHandleBox(ResizeHandle.TOP_RIGHT, boxWidthPx, 0f)
            ResizeHandleBox(ResizeHandle.BOTTOM_LEFT, 0f, boxHeightPx)
            ResizeHandleBox(ResizeHandle.BOTTOM_RIGHT, boxWidthPx, boxHeightPx)
        }

        // Mini toolbar for selection (15% larger icons for better touch targets)
        val miniButtonSize = 35  // Increased from 30 (15% larger)
        val miniIconSize = 28.dp  // Increased from 24dp (15% larger)
        // Base buttons: Delete, Recognize, Cut, Copy, Duplicate (resize via corner handles)
        // +1 if Convert to Tasks callback is provided
        val buttonCount = 5 + (if (onConvertSelectionToTasks != null) 1 else 0)
        val toolbarPadding = 3

        // If we can calculate offset of buttons show selection handling tools
        selectionStartOffset.let { startOffset ->
            selectionDisplaceOffset.let { displaceOffset ->
                val baseOffset = startOffset + displaceOffset

                // Approximate toolbar width in pixels (buttons + padding)
                val toolbarWidthPx = with(density) {
                    (buttonCount * miniButtonSize + toolbarPadding * 2).dp.toPx().toInt()
                }
                val viewWidthPx = controlTower.page.viewWidth
                val horizontalMarginPx = with(density) { 8.dp.toPx().toInt() }

                val minX = horizontalMarginPx
                val maxX = (viewWidthPx - toolbarWidthPx - horizontalMarginPx).coerceAtLeast(minX)
                val clampedX = baseOffset.x.coerceIn(minX, maxX)

                // Decide whether to place toolbar above or below the selection box
                val boxHeightPx = selectionRect.height()
                val toolbarHeightPx = with(density) { miniButtonSize.dp.toPx() }
                val viewHeightPx = controlTower.page.viewHeight
                val verticalMarginPx = with(density) { 8.dp.toPx().toInt() }

                val spaceAbove = baseOffset.y
                val spaceBelow = viewHeightPx - (baseOffset.y + boxHeightPx.toInt())

                val canPlaceAbove = spaceAbove >= toolbarHeightPx.toInt() + verticalMarginPx
                val canPlaceBelow = spaceBelow >= toolbarHeightPx.toInt() + verticalMarginPx
                val placeBelow = !canPlaceAbove && canPlaceBelow

                val offsetY = if (placeBelow) {
                    baseOffset.y + boxHeightPx.toInt() + verticalMarginPx
                } else {
                    baseOffset.y - (toolbarHeightPx.toInt() + verticalMarginPx)
                }

                val offset = IntOffset(x = clampedX, y = offsetY)
                // Overlay buttons near the selection box
                Row(
                    modifier = Modifier
                        .offset { offset }
                        .background(EInkTheme.TextBoxOverlay.miniToolbarBackground)
                        .border(EInkTheme.TextBoxOverlay.miniToolbarBorderWidth, EInkTheme.TextBoxOverlay.miniToolbarBorderColor)
                        .padding(toolbarPadding.dp)
                        .height(miniButtonSize.dp)
                ) {
                    ToolbarButton(
                        iconId = R.drawable.delete,
                        isSelected = false,
                        onSelect = {
                            controlTower.deleteSelection()
                        },
                        modifier = Modifier.size(miniButtonSize.dp),
                        iconSize = miniIconSize
                    )
                    ToolbarButton(
                        iconId = R.drawable.ai_rectangle,
                        contentDescription = "AI Recognize",
                        isSelected = false,
                        onSelect = {
                            DrawCanvas.openHandwritingRecognitionSignal.tryEmit(Unit)
                        },
                        modifier = Modifier.size(miniButtonSize.dp),
                        iconSize = miniIconSize
                    )
                    ToolbarButton(
                        vectorIcon = FeatherIcons.Scissors,
                        isSelected = false,
                        onSelect = { controlTower.cutSelectionToClipboard(context) },
                        modifier = Modifier.size(miniButtonSize.dp),
                        iconSize = miniIconSize
                    )
                    ToolbarButton(
                        vectorIcon = FeatherIcons.Copy,
                        contentDescription = "Copy",
                        isSelected = false,
                        onSelect = { controlTower.copySelectionToClipboard(context) },
                        modifier = Modifier.size(miniButtonSize.dp),
                        iconSize = miniIconSize
                    )
                    ToolbarButton(
                        iconId = R.drawable.stack_48px,
                        contentDescription = "Duplicate",
                        isSelected = false,
                        onSelect = { controlTower.duplicateSelection() },
                        modifier = Modifier.size(miniButtonSize.dp),
                        iconSize = miniIconSize
                    )
                    // Convert Selection to Tasks button
                    if (onConvertSelectionToTasks != null) {
                        ToolbarButton(
                            iconId = R.drawable.ai_memo_to_task_lasso,
                            contentDescription = "Convert to Tasks",
                            isSelected = false,
                            onSelect = {
                                // Pass selected strokes for Digital Ink recognition
                                selectionState.selectedStrokes?.let { strokes ->
                                    if (strokes.isNotEmpty()) {
                                        // Clear selection BEFORE calling callback
                                        // Dialog will open - no need to keep lasso active behind it
                                        // 1. Apply displacement to put strokes back on canvas
                                        // 2. Reset selection state to clear UI
                                        // 3. Enable drawing mode
                                        // The 'strokes' reference is already captured, safe to reset
                                        controlTower.applySelectionDisplace()
                                        selectionState.reset()
                                        controlTower.setIsDrawing(true)

                                        onConvertSelectionToTasks(strokes)
                                    }
                                }
                            },
                            modifier = Modifier.size(miniButtonSize.dp),
                            iconSize = miniIconSize
                        )
                    }
                }
            }
        }

    }
}
