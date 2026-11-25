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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
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
    onConvertSelectionToTasks: ((List<DbStroke>) -> Unit)? = null,
    onScheduleSelection: ((List<DbStroke>) -> Unit)? = null
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
        val resizable = selectionState.isResizable()
        Image(
            bitmap = selectionState.selectedBitmap!!.asImageBitmap(),
            contentDescription = "Selection bitmap",
            contentScale = if (resizable) ContentScale.FillBounds else ContentScale.None,
            modifier = Modifier
                .offset { selectionStartOffset + selectionDisplaceOffset }
                .then(
                    if (resizable) {
                        Modifier.size(
                            width = with(density) { selectionRect.width().coerceAtLeast(1).toDp() },
                            height = with(density) { selectionRect.height().coerceAtLeast(1).toDp() }
                        )
                    } else {
                        Modifier
                    }
                )
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
                                    selectionState.finishImageResize(controlTower.page)
                                },
                                onDragCancel = {
                                    selectionState.activeResizeHandle = ResizeHandle.NONE
                                    selectionState.finishImageResize(controlTower.page)
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

        // Selection bar (Phase 4 / Decision D3 P1 Option B & P7):
        // 44dp edit icon buttons (Cut, Copy, Duplicate, separated Delete) + labelled 44dp AI chips
        // ("Read", "Tasks", "Schedule"), with contentDescription on all 7 actions and measured bounds clamping.
        var barSizePx by remember { mutableStateOf(IntSize.Zero) }

        selectionStartOffset.let { startOffset ->
            selectionDisplaceOffset.let { displaceOffset ->
                val baseOffset = startOffset + displaceOffset
                val viewWidthPx = controlTower.page.viewWidth
                val viewHeightPx = controlTower.page.viewHeight
                val horizontalMarginPx = with(density) { 8.dp.toPx().toInt() }
                val verticalMarginPx = with(density) { 8.dp.toPx().toInt() }
                val availableWidthDp = with(density) { (viewWidthPx - horizontalMarginPx * 2).coerceAtLeast(240).toDp() }

                val fallbackWidthPx = with(density) { 460.dp.toPx().toInt() }
                val fallbackHeightPx = with(density) { 52.dp.toPx().toInt() }
                val toolbarWidthPx = if (barSizePx.width > 0) barSizePx.width else fallbackWidthPx
                val toolbarHeightPx = if (barSizePx.height > 0) barSizePx.height else fallbackHeightPx

                val minX = horizontalMarginPx
                val maxX = (viewWidthPx - toolbarWidthPx - horizontalMarginPx).coerceAtLeast(minX)
                val clampedX = baseOffset.x.coerceIn(minX, maxX)

                val boxHeightPx = selectionRect.height()
                val spaceAbove = baseOffset.y
                val spaceBelow = viewHeightPx - (baseOffset.y + boxHeightPx.toInt())

                val canPlaceAbove = spaceAbove >= toolbarHeightPx + verticalMarginPx
                val canPlaceBelow = spaceBelow >= toolbarHeightPx + verticalMarginPx
                val placeBelow = !canPlaceAbove && canPlaceBelow

                val offsetY = (if (placeBelow) {
                    baseOffset.y + boxHeightPx.toInt() + verticalMarginPx
                } else {
                    baseOffset.y - (toolbarHeightPx + verticalMarginPx)
                }).coerceIn(verticalMarginPx, (viewHeightPx - toolbarHeightPx - verticalMarginPx).coerceAtLeast(verticalMarginPx))

                val offset = IntOffset(x = clampedX, y = offsetY)
                LassoSelectionBar(
                    onCut = { controlTower.cutSelectionToClipboard(context) },
                    onCopy = { controlTower.copySelectionToClipboard(context) },
                    onDuplicate = { controlTower.duplicateSelection() },
                    onDelete = { controlTower.deleteSelection() },
                    onRead = { DrawCanvas.openHandwritingRecognitionSignal.tryEmit(Unit) },
                    onConvertToTasks = onConvertSelectionToTasks?.let { handler ->
                        {
                            selectionState.selectedStrokes?.let { strokes ->
                                if (strokes.isNotEmpty()) {
                                    controlTower.applySelectionDisplace()
                                    selectionState.reset()
                                    controlTower.setIsDrawing(true)
                                    handler(strokes)
                                }
                            }
                        }
                    },
                    onSchedule = onScheduleSelection?.let { handler ->
                        {
                            val strokes = selectionState.selectedStrokes.orEmpty()
                            if (strokes.isNotEmpty()) {
                                controlTower.applySelectionDisplace()
                                selectionState.reset()
                                controlTower.setIsDrawing(true)
                            }
                            handler(strokes)
                        }
                    },
                    compactTwoRow = availableWidthDp < 470.dp || (availableWidthDp < 540.dp && density.fontScale > 1.1f),
                    modifier = Modifier
                        .offset { offset }
                        .onSizeChanged { barSizePx = it }
                )
            }
        }

    }
}

/**
 * Floating lasso selection toolbar (Phase 4 / Decision D3 P1 Option B):
 * - Left: 44dp icon buttons for Cut, Copy, Duplicate, separated from Delete by a vertical rule.
 * - Right (or second row on ultra-narrow / large-text layouts): 44dp labelled chips ("Read",
 *   "Tasks", "Schedule") so the three AI actions are self-teaching without hover on E-Ink.
 */
@Composable
internal fun LassoSelectionBar(
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onRead: () -> Unit,
    onConvertToTasks: (() -> Unit)?,
    onSchedule: (() -> Unit)?,
    compactTwoRow: Boolean = false,
    modifier: Modifier = Modifier
) {
    val buttonSize = EInkTheme.Global.touchTarget  // 44.dp
    val iconSize = 24.dp
    val shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)

    val editActions: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ToolbarButton(
                vectorIcon = FeatherIcons.Scissors,
                contentDescription = "Cut",
                isSelected = false,
                onSelect = onCut,
                modifier = Modifier.size(buttonSize),
                iconSize = iconSize
            )
            ToolbarButton(
                vectorIcon = FeatherIcons.Copy,
                contentDescription = "Copy",
                isSelected = false,
                onSelect = onCopy,
                modifier = Modifier.size(buttonSize),
                iconSize = iconSize
            )
            ToolbarButton(
                iconId = R.drawable.stack_48px,
                contentDescription = "Duplicate",
                isSelected = false,
                onSelect = onDuplicate,
                modifier = Modifier.size(buttonSize),
                iconSize = iconSize
            )
            // Separate destructive Delete from Cut/Copy/Duplicate and from AI actions
            LassoBarDivider()
            ToolbarButton(
                iconId = R.drawable.delete,
                contentDescription = "Delete",
                isSelected = false,
                onSelect = onDelete,
                modifier = Modifier.size(buttonSize),
                iconSize = iconSize
            )
        }
    }

    val aiActions: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LassoAiChip(
                iconId = R.drawable.ai_rectangle,
                label = "Read",
                contentDescription = "Read handwriting with AI",
                onClick = onRead
            )
            if (onConvertToTasks != null) {
                LassoAiChip(
                    iconId = R.drawable.ai_memo_to_task_lasso,
                    label = "Tasks",
                    contentDescription = "Convert selection to tasks",
                    onClick = onConvertToTasks
                )
            }
            if (onSchedule != null) {
                LassoAiChip(
                    iconId = R.drawable.ai_schedule_lasso,
                    label = "Schedule",
                    contentDescription = "Add selection to calendar",
                    onClick = onSchedule
                )
            }
        }
    }

    if (compactTwoRow) {
        Column(
            modifier = modifier
                .background(EInkTheme.TextBoxOverlay.miniToolbarBackground, shape)
                .border(EInkTheme.Global.borderWidth, EInkTheme.TextBoxOverlay.miniToolbarBorderColor, shape)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            editActions()
            Box(
                modifier = Modifier
                    .height(1.dp)
                    .width(200.dp)
                    .background(EInkTheme.Divider.subtleColor)
            )
            aiActions()
        }
    } else {
        Row(
            modifier = modifier
                .background(EInkTheme.TextBoxOverlay.miniToolbarBackground, shape)
                .border(EInkTheme.Global.borderWidth, EInkTheme.TextBoxOverlay.miniToolbarBorderColor, shape)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            editActions()
            LassoBarDivider()
            aiActions()
        }
    }
}

@Composable
private fun LassoBarDivider() {
    Spacer(modifier = Modifier.width(4.dp))
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(EInkTheme.Colors.borderStrong)
    )
    Spacer(modifier = Modifier.width(4.dp))
}

@Composable
private fun LassoAiChip(
    iconId: Int,
    label: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    val chipShape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
    Row(
        modifier = Modifier
            .height(EInkTheme.Global.touchTarget)
            .background(EInkTheme.Colors.background, chipShape)
            .border(1.dp, EInkTheme.Colors.primary, chipShape)
            .semantics(mergeDescendants = true) {
                this.role = Role.Button
                this.contentDescription = contentDescription
            }
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(iconId),
            contentDescription = null,
            tint = EInkTheme.Colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.Colors.primary,
            maxLines = 1,
            softWrap = false
        )
    }
}
