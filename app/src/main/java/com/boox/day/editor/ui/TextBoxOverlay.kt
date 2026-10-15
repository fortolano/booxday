package com.boox.day.editor.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.R
import com.boox.day.data.PageDataManager
import com.boox.day.data.db.TextAnnotationRepository
import com.boox.day.data.model.RichTextSpanSerializer
import com.boox.day.editor.DrawCanvas
import com.boox.day.editor.EditorControlTower
import com.boox.day.ocr.TextAnnotationIndexer
import com.boox.day.editor.state.Operation
import com.boox.day.editor.state.ResizeHandle
import com.boox.day.editor.state.TextBoxToolbarMode
import compose.icons.feathericons.Scissors
import com.boox.day.data.datastore.BUTTON_SIZE
import com.boox.day.editor.ui.toolbar.ToolbarButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import compose.icons.FeatherIcons
import compose.icons.feathericons.Clipboard
import compose.icons.feathericons.Copy
import compose.icons.feathericons.Edit2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import com.boox.day.utils.TaggedLog

private val log = TaggedLog("TextBoxOverlay")

// Font size presets in sp (standard sizes users recognize)
// Stored internally as sp * density for proper rendering
private val FONT_SIZES_SP = listOf(12f, 14f, 16f, 18f, 24f, 36f, 48f, 72f)
private const val DEFAULT_FONT_SIZE_SP = 16f

// Text color options
private val TEXT_COLOR_OPTIONS = listOf(
    Color.Black,
    Color.DarkGray,
    Color.Gray,
    Color.Red,
    Color.Blue,
    Color.Green,
    Color.Cyan
)

/**
 * Overlay for text box interactions.
 * 100% IDENTICAL implementation to SelectedBitmap for lasso selection.
 */
@Composable
@ExperimentalComposeUiApi
@ExperimentalFoundationApi
fun TextBoxOverlay(
    context: Context,
    controlTower: EditorControlTower
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val textAnnotationRepository = remember { TextAnnotationRepository(context) }

    // State for dropdowns
    var showColorPicker by remember { mutableStateOf(false) }
    var showSizePicker by remember { mutableStateOf(false) }
    var showAlignPicker by remember { mutableStateOf(false) }

    // Get text box state - IDENTICAL pattern to selectionState
    val textBoxState = controlTower.getTextBoxState()
    val selectedTextBox = textBoxState.selectedTextBox

    // IDENTICAL guard to SelectedBitmap: if (selectionState.selectedBitmap == null) return
    if (selectedTextBox == null) return

    // Log rendering for debugging
    log.d("RENDERING: content='${selectedTextBox.content.take(30)}', editingTextBox=${textBoxState.editingTextBox?.id}")

    // IDENTICAL pattern: get displacement offset with early return
    var textBoxDisplaceOffset =
        controlTower.page.applyZoom(textBoxState.textBoxDisplaceOffset ?: return)

    // IDENTICAL pattern: get rect for toolbar positioning
    val textBoxRect =
        controlTower.page.toScreenCoordinates(textBoxState.textBoxRect ?: return)

    // IDENTICAL pattern: get start offset
    val textBoxStartOffset =
        controlTower.page.applyZoom(textBoxState.textBoxStartOffset ?: IntOffset(0, 0))

    // Calculate box size in screen coordinates for drawing.
    // DRAG_PADDING was previously adding extra vertical space above/below the
    // text box, which caused the dashed selection rectangle to be offset from
    // the final text position. We now use zero padding so the dashed box
    // aligns exactly with the annotation's bounds.
    val DRAG_PADDING = 0f
    val boxWidthPx = textBoxRect.width()
    val boxHeightPx = textBoxRect.height() + (DRAG_PADDING * 2)
    val boxWidthDp = with(density) { boxWidthPx.toDp() }
    val boxHeightDp = with(density) { boxHeightPx.toDp() }

    // Adjust start offset (no extra vertical padding anymore)
    val paddedStartOffset = IntOffset(
        textBoxStartOffset.x,
        textBoxStartOffset.y - DRAG_PADDING.toInt()
    )

    // IDENTICAL structure to SelectedBitmap
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()  // Prevent selection from overflowing beyond canvas bounds
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tapPosition ->
                        log.d("OUTER_BOX_CLICK: editingTextBox=${textBoxState.editingTextBox?.id}")

                        // If editing, save first THEN apply displacement and reset
                        if (textBoxState.editingTextBox != null) {
                            log.d("OUTER_BOX_CLICK: Saving and stopping edit before reset")
                            saveAndStopEditing(controlTower, textBoxState, scope, textAnnotationRepository)
                        }

                        // IDENTICAL to SelectedBitmap: apply displacement, reset, re-enable drawing
                        log.d("OUTER_BOX_CLICK: Applying displace and reset")
                        applyTextBoxDisplace(controlTower, textBoxState, scope, textAnnotationRepository)
                        textBoxState.reset()
                        controlTower.setIsDrawing(true)
                    }
                )
            }) {

        // Enforce drawing disabled while selection is active
        androidx.compose.runtime.SideEffect {
            controlTower.setIsDrawing(false)
        }

        // Box with dashed border - IDENTICAL to Image in SelectedBitmap
        Box(
            modifier = Modifier
                .offset { paddedStartOffset + textBoxDisplaceOffset }
                .size(boxWidthDp, boxHeightDp)
                .zIndex(-200f)  // Much lower than toolbar to ensure toolbar is always visible
                .drawBehind {
                    // IDENTICAL: draw dashed rect using strokeStyle
                    drawRect(
                        color = Color.Gray,
                        topLeft = Offset(0f, 0f),
                        size = size,
                        style = strokeStyle
                    )
                }
                // IDENTICAL drag handling to SelectedBitmap
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()

                        // FIX: Use current state as base, not the captured local variable
                        val currentDisplace = controlTower.page.applyZoom(
                            textBoxState.textBoxDisplaceOffset ?: IntOffset.Zero
                        )

                        textBoxState.textBoxDisplaceOffset =
                            controlTower.page.removeZoom(
                                currentDisplace + dragAmount.round()
                            )
                    }
                }
        ) {
            // Inner Box positions text content at correct visual location
            // Convert DRAG_PADDING from pixels to dp for consistent units
            val dragPaddingDp = with(density) { DRAG_PADDING.toDp() }
            Box(
                modifier = Modifier
                    .offset(y = dragPaddingDp)
                    .size(boxWidthDp, boxHeightDp - (dragPaddingDp * 2))
                    .padding(horizontal = selectedTextBox.padding.dp)
                    .noRippleClickable {
                        // Tap on text content enters edit mode
                        if (textBoxState.editingTextBox == null) {
                            log.d("TEXT_CONTENT_CLICK: Entering edit mode")
                            textBoxState.startEditing(selectedTextBox)
                        }
                    }
            ) {
                val isEditing = textBoxState.editingTextBox != null

                if (isEditing) {
                    // EDITING MODE: Show InlineRichTextEditor
                    InlineRichTextEditor(
                        annotation = selectedTextBox,
                        spans = textBoxState.currentSpans,
                        editText = textBoxState.currentEditText ?: selectedTextBox.content,
                        onTextChanged = { newText, newSpans ->
                            textBoxState.updateEditText(newText)
                            textBoxState.updateSpans(newSpans)
                        },
                        onSelectionChanged = { range ->
                            textBoxState.updateSelectionRange(range)
                        },
                        onFocusChanged = { hasFocus ->
                            // Don't auto-save on focus loss - causes immediate close on e-ink
                            // Save happens when user taps outside (outer box click handler)
                            log.d("InlineRichTextEditor focus changed: $hasFocus")
                        },
                        onSpansInitialized = { spans ->
                            textBoxState.updateSpans(spans)
                        },
                        onSnapshotForUndo = {
                            textBoxState.snapshotForUndo()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // VIEW MODE: Show text with stored rich formatting spans so
                    // the overlay matches the canvas rendering before editing.
                    val viewSpans = remember(
                        selectedTextBox.id,
                        selectedTextBox.spans
                    ) {
                        RichTextSpanSerializer.decode(selectedTextBox.spans)
                    }
                    val annotated = remember(
                        selectedTextBox.id,
                        selectedTextBox.content,
                        selectedTextBox.spans
                    ) {
                        com.boox.day.editor.drawing.RichTextConverter.toAnnotatedString(
                            selectedTextBox.content,
                            viewSpans
                        )
                    }
                    val textAlign = when (selectedTextBox.textAlign) {
                        "center" -> TextAlign.Center
                        "right" -> TextAlign.Right
                        else -> TextAlign.Left
                    }
                    Text(
                        text = annotated,
                        modifier = Modifier.fillMaxSize(),
                        color = Color(selectedTextBox.textColor),
                        fontSize = (selectedTextBox.fontSize / density.density).sp,
                        fontWeight = if (selectedTextBox.fontWeight >= 700) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (selectedTextBox.fontStyle == "italic") FontStyle.Italic else FontStyle.Normal,
                        textAlign = textAlign
                    )
                }
            }
        }

        // Resize handles at corners
        val handleSize = EInkTheme.TextBoxOverlay.handleSize
        val handleSizePx = with(density) { handleSize.toPx() }
        val halfHandle = handleSizePx / 2

        // Helper to create resize handle at given position
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
                            (paddedStartOffset.x + textBoxDisplaceOffset.x + xOffset - halfHandle).toInt(),
                            (paddedStartOffset.y + textBoxDisplaceOffset.y + yOffset - halfHandle).toInt()
                        )
                    }
                    .size(handleSize)
                    .zIndex(-100f)  // Below everything else
                    .background(EInkTheme.Colors.background)
                    .drawBehind {
                        drawRect(
                            color = EInkTheme.TextBoxOverlay.handleColor,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = EInkTheme.TextBoxOverlay.borderWidth.value)
                        )
                    }
                    .pointerInput(handleType) {
                        detectDragGestures(
                            onDragStart = {
                                textBoxState.startResizing(handleType, it.x, it.y)
                            },
                            onDragEnd = {
                                textBoxState.stopResizing()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val currentBox = textBoxState.selectedTextBox ?: return@detectDragGestures

                                // Convert drag to page coordinates
                                val pageScaledDrag = controlTower.page.removeZoom(dragAmount.round())

                                val newBounds = textBoxState.calculateResizedBounds(
                                    currentBox,
                                    handleType,
                                    pageScaledDrag.x.toFloat(),
                                    pageScaledDrag.y.toFloat()
                                )

                                // Update the annotation with new bounds
                                val updated = currentBox.copy(
                                    x = newBounds.left,
                                    y = newBounds.top,
                                    width = newBounds.width(),
                                    height = newBounds.height()
                                )

                                // Update state (keeps visual in sync)
                                textBoxState.selectedTextBox = updated
                                textBoxState.textBoxStartOffset = IntOffset(updated.x.toInt(), updated.y.toInt())
                                textBoxState.textBoxRect = android.graphics.Rect(
                                    updated.x.toInt(),
                                    updated.y.toInt(),
                                    (updated.x + updated.width).toInt(),
                                    (updated.y + updated.height).toInt()
                                )
                            }
                        )
                    }
            )
        }

        // Draw 4 corner handles
        ResizeHandleBox(ResizeHandle.TOP_LEFT, 0f, DRAG_PADDING)
        ResizeHandleBox(ResizeHandle.TOP_RIGHT, boxWidthPx.toFloat(), DRAG_PADDING)
        ResizeHandleBox(ResizeHandle.BOTTOM_LEFT, 0f, boxHeightPx - DRAG_PADDING)
        ResizeHandleBox(ResizeHandle.BOTTOM_RIGHT, boxWidthPx.toFloat(), boxHeightPx - DRAG_PADDING)

        // Mini toolbar - aligned near the top-left of the text box
        val miniButtonSize = 35  // Increased from 30 (15% larger for better touch targets)
        val miniIconSize = 28.dp  // Increased from 24dp (15% larger)
        // Two rows, 5 buttons each: B, I, U, Color, Size | Align, Undo, Copy, Paste, Delete
        val buttonCount = 5
        val toolbarPadding = 3

        // Determine toolbar mode early so we can calculate correct height
        val isAdvancedMode = textBoxState.toolbarMode == TextBoxToolbarMode.ADVANCED

        textBoxStartOffset.let { startOffset ->
            textBoxDisplaceOffset.let { displaceOffset ->
                // Base offset: top-left corner of text box in screen coordinates
                val baseOffset = startOffset + displaceOffset

                // Approximate toolbar width in pixels (buttons + padding)
                val toolbarWidthPx = with(density) {
                    (buttonCount * miniButtonSize + toolbarPadding * 2).dp.toPx().toInt()
                }
                val viewWidthPx = controlTower.page.viewWidth
                val horizontalMarginPx = with(density) { 8.dp.toPx().toInt() }

                // Clamp toolbar so it stays within screen bounds horizontally
                val minX = horizontalMarginPx
                val maxX = (viewWidthPx - toolbarWidthPx - horizontalMarginPx).coerceAtLeast(minX)
                val clampedX = baseOffset.x.coerceIn(minX, maxX)

                // Decide whether to place toolbar above or below the text box
                // Use different heights for edit mode (2-row) vs select mode (1-row)
                val toolbarHeight = if (isAdvancedMode) {
                    EInkTheme.TextBoxOverlay.miniToolbarHeightAdvanced
                } else {
                    EInkTheme.TextBoxOverlay.miniToolbarHeightBasic
                }
                val toolbarHeightPx = with(density) { toolbarHeight.toPx() }
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

                if (isAdvancedMode) {
                    // ADVANCED mode: Full two-row toolbar with formatting controls
                    Column(
                        modifier = Modifier
                            .graphicsLayer { clip = false }
                            .zIndex(Float.MAX_VALUE)
                            .offset { offset }
                            .background(EInkTheme.TextBoxOverlay.miniToolbarBackground)
                            .border(EInkTheme.TextBoxOverlay.miniToolbarBorderWidth, EInkTheme.TextBoxOverlay.miniToolbarBorderColor)
                            .padding(toolbarPadding.dp)
                    ) {
                        // Top row: formatting controls
                        Row(modifier = Modifier.height(miniButtonSize.dp)) {
                            // Bold button
                            ToolbarButton(
                                iconId = R.drawable.format_bold_40px,
                                isSelected = selectedTextBox.fontWeight >= 700,
                                onSelect = {
                                    keyboardController?.hide()
                                    toggleBold(controlTower, textBoxState, scope, textAnnotationRepository)
                                },
                                modifier = Modifier.size(miniButtonSize.dp),
                                iconSize = miniIconSize
                            )

                            // Italic button
                            ToolbarButton(
                                iconId = R.drawable.format_italic_40px,
                                isSelected = selectedTextBox.fontStyle == "italic",
                                onSelect = {
                                    keyboardController?.hide()
                                    toggleItalic(controlTower, textBoxState, scope, textAnnotationRepository)
                                },
                                modifier = Modifier.size(miniButtonSize.dp),
                                iconSize = miniIconSize
                            )

                            // Underline button
                            ToolbarButton(
                                iconId = R.drawable.format_underlined_40px,
                                isSelected = false,
                                onSelect = { toggleUnderline(controlTower, textBoxState) },
                                modifier = Modifier.size(miniButtonSize.dp),
                                iconSize = miniIconSize
                            )

                            // Bullet list button
                            ToolbarButton(
                                iconId = R.drawable.format_list_bulleted_48px,
                                isSelected = false,
                                onSelect = { toggleBulletList(controlTower, textBoxState) },
                                modifier = Modifier.size(miniButtonSize.dp),
                                iconSize = miniIconSize
                            )

                            // Text color button
                            Box {
                                ToolbarButton(
                                    iconId = R.drawable.format_color_text_40px,
                                    isSelected = showColorPicker,
                                    onSelect = {
                                        showColorPicker = !showColorPicker
                                        showSizePicker = false
                                    },
                                    modifier = Modifier.size(miniButtonSize.dp),
                                    iconSize = miniIconSize
                                )
                                if (showColorPicker) {
                                    Popup(
                                        alignment = Alignment.TopCenter,
                                        offset = IntOffset(0, miniButtonSize + 10),
                                        onDismissRequest = { showColorPicker = false },
                                        properties = PopupProperties(focusable = true)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .background(EInkTheme.PopupMenu.background)
                                                .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor)
                                                .padding(EInkTheme.PopupMenu.padding),
                                            horizontalArrangement = Arrangement.spacedBy(EInkTheme.ColorPicker.swatchSpacing)
                                        ) {
                                            TEXT_COLOR_OPTIONS.forEach { color ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(EInkTheme.ColorPicker.swatchSize)
                                                        .clickable {
                                                            setTextColor(controlTower, textBoxState, color.toArgb(), scope, textAnnotationRepository)
                                                            showColorPicker = false
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    val isSelected = color.toArgb() == selectedTextBox.textColor
                                                    Box(
                                                        modifier = Modifier
                                                            .size(22.dp)
                                                            .background(color, CircleShape)
                                                            .border(
                                                                width = if (isSelected) EInkTheme.ColorPicker.selectedBorderWidth else EInkTheme.ColorPicker.swatchBorderWidth,
                                                                color = if (isSelected) EInkTheme.Selection.background else EInkTheme.Colors.darkGray,
                                                                shape = CircleShape
                                                            )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Font size button
                            Box {
                                ToolbarButton(
                                    iconId = R.drawable.format_size_40px,
                                    isSelected = showSizePicker,
                                    onSelect = {
                                        showSizePicker = !showSizePicker
                                        showColorPicker = false
                                    },
                                    modifier = Modifier.size(miniButtonSize.dp),
                                    iconSize = miniIconSize
                                )
                                if (showSizePicker) {
                                    Popup(
                                        alignment = Alignment.TopCenter,
                                        offset = IntOffset(0, miniButtonSize + 10),
                                        onDismissRequest = { showSizePicker = false },
                                        properties = PopupProperties(focusable = true)
                                    ) {
                                        // Convert stored fontSize to sp for comparison
                                        val currentSizeSp = selectedTextBox.fontSize / density.density

                                        // Helper to check if a size is currently selected
                                        fun isSelectedSize(targetSp: Float): Boolean {
                                            val tolerance = 2f
                                            return kotlin.math.abs(currentSizeSp - targetSp) < tolerance
                                        }

                                        Column(
                                            modifier = Modifier
                                                .background(EInkTheme.PopupMenu.background)
                                                .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor)
                                                .padding(EInkTheme.PopupMenu.padding),
                                            verticalArrangement = Arrangement.spacedBy(EInkTheme.SizePicker.buttonSpacing)
                                        ) {
                                            // Top row: 12, 14, 16, 18
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(EInkTheme.SizePicker.buttonSpacing)
                                            ) {
                                                FONT_SIZES_SP.take(4).forEach { sizeSp ->
                                                    val isCurrentSize = isSelectedSize(sizeSp)
                                                    // Store as sp * density for proper rendering
                                                    val storedSize = sizeSp * density.density
                                                    Box(
                                                        modifier = Modifier
                                                            .size(EInkTheme.SizePicker.buttonSize)
                                                            .background(if (isCurrentSize) EInkTheme.SizePicker.selectedBackground else EInkTheme.Colors.background)
                                                            .border(EInkTheme.SizePicker.buttonBorderWidth, EInkTheme.SizePicker.buttonBorderColor)
                                                            .noRippleClickable {
                                                                setFontSize(controlTower, textBoxState, storedSize, scope, textAnnotationRepository)
                                                                showSizePicker = false
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = sizeSp.toInt().toString(),
                                                            color = if (isCurrentSize) EInkTheme.SizePicker.selectedLabelColor else EInkTheme.SizePicker.labelColor,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = EInkTheme.SizePicker.labelFontSize
                                                        )
                                                    }
                                                }
                                            }
                                            // Bottom row: 24, 36, 48, 72
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(EInkTheme.SizePicker.buttonSpacing)
                                            ) {
                                                FONT_SIZES_SP.drop(4).forEach { sizeSp ->
                                                    val isCurrentSize = isSelectedSize(sizeSp)
                                                    // Store as sp * density for proper rendering
                                                    val storedSize = sizeSp * density.density
                                                    Box(
                                                        modifier = Modifier
                                                            .size(EInkTheme.SizePicker.buttonSize)
                                                            .background(if (isCurrentSize) EInkTheme.SizePicker.selectedBackground else EInkTheme.Colors.background)
                                                            .border(EInkTheme.SizePicker.buttonBorderWidth, EInkTheme.SizePicker.buttonBorderColor)
                                                            .noRippleClickable {
                                                                setFontSize(controlTower, textBoxState, storedSize, scope, textAnnotationRepository)
                                                                showSizePicker = false
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = sizeSp.toInt().toString(),
                                                            color = if (isCurrentSize) EInkTheme.SizePicker.selectedLabelColor else EInkTheme.SizePicker.labelColor,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = EInkTheme.SizePicker.labelFontSize
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom row: actions + align
                        Row(modifier = Modifier.height(miniButtonSize.dp)) {
                            // Text alignment button
                            Box {
                                val alignIconId = when (selectedTextBox.textAlign) {
                                    "center" -> R.drawable.format_align_center_40px
                                    "right" -> R.drawable.format_align_right_40px
                                    else -> R.drawable.format_align_left_40px
                                }
                                ToolbarButton(
                                    iconId = alignIconId,
                                    isSelected = showAlignPicker,
                                    onSelect = {
                                        showAlignPicker = !showAlignPicker
                                        showColorPicker = false
                                        showSizePicker = false
                                    },
                                    modifier = Modifier.size(miniButtonSize.dp),
                                    iconSize = miniIconSize
                                )
                                if (showAlignPicker) {
                                    Popup(
                                        alignment = Alignment.TopCenter,
                                        offset = IntOffset(0, miniButtonSize + 10),
                                        onDismissRequest = { showAlignPicker = false },
                                        properties = PopupProperties(focusable = true)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .background(EInkTheme.PopupMenu.background)
                                                .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor)
                                                .padding(EInkTheme.PopupMenu.padding),
                                            horizontalArrangement = Arrangement.spacedBy(EInkTheme.SizePicker.buttonSpacing)
                                        ) {
                                            listOf(
                                                Triple(R.drawable.format_align_left_40px, "left", "Align left"),
                                                Triple(R.drawable.format_align_center_40px, "center", "Align center"),
                                                Triple(R.drawable.format_align_right_40px, "right", "Align right")
                                            ).forEach { (iconId, align, _) ->
                                                val isCurrent = selectedTextBox.textAlign == align
                                                Box(
                                                    modifier = Modifier
                                                        .size(EInkTheme.SizePicker.buttonSize)
                                                        .background(if (isCurrent) EInkTheme.Selection.background else EInkTheme.Colors.background)
                                                        .border(EInkTheme.SizePicker.buttonBorderWidth, EInkTheme.SizePicker.buttonBorderColor)
                                                        .noRippleClickable {
                                                            setTextAlign(textBoxState, align)
                                                            showAlignPicker = false
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    androidx.compose.material.Icon(
                                                        painter = androidx.compose.ui.res.painterResource(iconId),
                                                        contentDescription = null,
                                                        tint = if (isCurrent) EInkTheme.Selection.foreground else EInkTheme.Colors.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Undo last edit (text + spans)
                            ToolbarButton(
                                iconId = R.drawable.outline_undo_24,
                                isSelected = false,
                                onSelect = { textBoxState.undoLastEdit() },
                                modifier = Modifier.size(miniButtonSize.dp),
                                iconSize = miniIconSize
                            )

                            // Redo last edit (text + spans)
                            ToolbarButton(
                                iconId = R.drawable.redo_40px,
                                isSelected = false,
                                onSelect = { textBoxState.redoLastEdit() },
                                modifier = Modifier.size(miniButtonSize.dp),
                                iconSize = miniIconSize
                            )

                            // Copy text to clipboard
                            ToolbarButton(
                                vectorIcon = FeatherIcons.Copy,
                                isSelected = false,
                                onSelect = { copyTextToClipboard(context, textBoxState, controlTower) },
                                modifier = Modifier.size(miniButtonSize.dp),
                                iconSize = miniIconSize
                            )

                            // Paste text from clipboard
                            ToolbarButton(
                                vectorIcon = FeatherIcons.Clipboard,
                                isSelected = false,
                                onSelect = {
                                    if (pasteTextFromClipboard(context, textBoxState, scope, textAnnotationRepository)) {
                                        android.widget.Toast.makeText(context, "Text pasted", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(miniButtonSize.dp),
                                iconSize = miniIconSize
                            )

                            // Delete button
                            ToolbarButton(
                                iconId = R.drawable.delete,
                                isSelected = false,
                                onSelect = { deleteTextBox(controlTower, textBoxState, scope, textAnnotationRepository) },
                                modifier = Modifier.size(miniButtonSize.dp),
                                iconSize = miniIconSize
                            )
                        }
                    }
                } else {
                    // BASIC mode: Single row with basic actions (like image selection toolbar)
                    Row(
                        modifier = Modifier
                            .graphicsLayer { clip = false }
                            .zIndex(Float.MAX_VALUE)
                            .offset { offset }
                            .background(EInkTheme.TextBoxOverlay.miniToolbarBackground)
                            .border(EInkTheme.TextBoxOverlay.miniToolbarBorderWidth, EInkTheme.TextBoxOverlay.miniToolbarBorderColor)
                            .padding(toolbarPadding.dp)
                            .height(miniButtonSize.dp)
                    ) {
                        // Delete
                        ToolbarButton(
                            iconId = R.drawable.delete,
                            isSelected = false,
                            onSelect = { deleteTextBox(controlTower, textBoxState, scope, textAnnotationRepository) },
                            modifier = Modifier.size(miniButtonSize.dp),
                            iconSize = miniIconSize
                        )

                        // Cut
                        ToolbarButton(
                            vectorIcon = FeatherIcons.Scissors,
                            isSelected = false,
                            onSelect = {
                                copyTextToClipboard(context, textBoxState, controlTower)
                                deleteTextBox(controlTower, textBoxState, scope, textAnnotationRepository)
                            },
                            modifier = Modifier.size(miniButtonSize.dp),
                            iconSize = miniIconSize
                        )

                        // Copy
                        ToolbarButton(
                            vectorIcon = FeatherIcons.Copy,
                            isSelected = false,
                            onSelect = { copyTextToClipboard(context, textBoxState, controlTower) },
                            modifier = Modifier.size(miniButtonSize.dp),
                            iconSize = miniIconSize
                        )

                        // Duplicate
                        ToolbarButton(
                            iconId = R.drawable.stack_48px,
                            contentDescription = "Duplicate",
                            isSelected = false,
                            onSelect = { duplicateTextBox(controlTower, textBoxState, scope, textAnnotationRepository) },
                            modifier = Modifier.size(miniButtonSize.dp),
                            iconSize = miniIconSize
                        )

                        // Edit - enters edit mode with advanced toolbar
                        ToolbarButton(
                            vectorIcon = FeatherIcons.Edit2,
                            contentDescription = "Edit",
                            isSelected = false,
                            onSelect = { textBoxState.startEditing(selectedTextBox) },
                            modifier = Modifier.size(miniButtonSize.dp),
                            iconSize = miniIconSize
                        )
                    }
                }
            }
        }
    }
}

// Helper functions - keep logic separate from composable

private fun applyTextBoxDisplace(
    controlTower: EditorControlTower,
    textBoxState: com.boox.day.editor.state.TextBoxState,
    scope: kotlinx.coroutines.CoroutineScope,
    textAnnotationRepository: TextAnnotationRepository
) {
    val selectedTextBox = textBoxState.selectedTextBox ?: return
    val displaceOffset = textBoxState.textBoxDisplaceOffset ?: IntOffset.Zero

    // Get original annotation from PageDataManager to compare
    val originalId = selectedTextBox.id
    val pageId = controlTower.getCurrentPageId()

    // Create final annotation with position offset applied (selectedTextBox already has style changes)
    val updated = if (displaceOffset != IntOffset.Zero) {
        selectedTextBox.copy(
            x = selectedTextBox.x + displaceOffset.x,
            y = selectedTextBox.y + displaceOffset.y
        )
    } else {
        selectedTextBox
    }

    // Update in memory - remove old, add updated (with position + styles)
    PageDataManager.removeTextAnnotation(pageId, originalId)
    PageDataManager.addTextAnnotation(pageId, updated)

    // Save to database
    scope.launch(Dispatchers.IO) {
        textAnnotationRepository.update(updated)
    }

    // Refresh canvas to show the finalized text
    scope.launch {
        DrawCanvas.forceUpdate.emit(null)
    }
}

private fun deleteTextBox(
    controlTower: EditorControlTower,
    textBoxState: com.boox.day.editor.state.TextBoxState,
    scope: kotlinx.coroutines.CoroutineScope,
    textAnnotationRepository: TextAnnotationRepository
) {
    val selectedTextBox = textBoxState.selectedTextBox ?: return

    log.w("DELETE_TEXTBOX: Deleting text box with content='${selectedTextBox.content.take(50)}'")

    // Register with history BEFORE deletion for undo support
    // AddTextAnnotation is the reverse operation (to restore on undo)
    controlTower.registerHistoryOperations(listOf(Operation.AddTextAnnotation(listOf(selectedTextBox))))

    // Remove from memory
    PageDataManager.removeTextAnnotation(controlTower.getCurrentPageId(), selectedTextBox.id)

    // Reset state and re-enable drawing - IDENTICAL to SelectedBitmap pattern
    textBoxState.reset()
    controlTower.setIsDrawing(true)

    // Delete from database
    scope.launch(Dispatchers.IO) {
        textAnnotationRepository.deleteById(selectedTextBox.id)
    }

    // Remove from semantic search index
    TextAnnotationIndexer.getInstanceOrNull()?.onAnnotationDeleted(
        selectedTextBox.id,
        selectedTextBox.pageId
    )

    // Refresh canvas
    scope.launch {
        DrawCanvas.forceUpdate.emit(null)
    }
}

private fun duplicateTextBox(
    controlTower: EditorControlTower,
    textBoxState: com.boox.day.editor.state.TextBoxState,
    scope: kotlinx.coroutines.CoroutineScope,
    textAnnotationRepository: TextAnnotationRepository
) {
    val selectedTextBox = textBoxState.selectedTextBox ?: return

    val duplicated = selectedTextBox.copy(
        id = UUID.randomUUID().toString(),
        x = selectedTextBox.x + 30f,
        y = selectedTextBox.y + 30f
    )

    PageDataManager.addTextAnnotation(controlTower.getCurrentPageId(), duplicated)
    textBoxState.selectTextBox(duplicated)

    scope.launch(Dispatchers.IO) {
        textAnnotationRepository.insert(duplicated)
    }

    scope.launch {
        DrawCanvas.forceUpdate.emit(null)
    }
}

private fun copyTextToClipboard(
    context: Context,
    textBoxState: com.boox.day.editor.state.TextBoxState,
    controlTower: com.boox.day.editor.EditorControlTower
) {
    val selectedTextBox = textBoxState.selectedTextBox ?: return
    val text = textBoxState.currentEditText ?: selectedTextBox.content

    // Get spans - either from current editing state or from the annotation
    val spans = if (textBoxState.currentSpans.isNotEmpty()) {
        textBoxState.currentSpans
    } else {
        RichTextSpanSerializer.decode(selectedTextBox.spans)
    }

    // Store in system clipboard (plain text only)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText("Text", text)
    clipboard.setPrimaryClip(clip)

    // Also store in app-local rich text clipboard (text + spans)
    com.boox.day.editor.state.RichTextClipboard.copy(text, spans)

    // CRITICAL: Clear the drawing clipboard so paste button shows text icon
    // This ensures the most recent copy operation (text) takes precedence
    controlTower.clearDrawingClipboard()

    // Show feedback to user
    android.widget.Toast.makeText(context, "Text copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
}

/**
 * Paste text from clipboard into current text box.
 * Uses app-local RichTextClipboard if available (preserves spans),
 * otherwise falls back to system clipboard (plain text only).
 * Appends to existing text at the end.
 *
 * Works in both modes:
 * - Editor mode: Updates editing state (currentEditText, currentSpans)
 * - Select mode: Directly updates annotation and saves to DB
 *
 * @return true if text was pasted, false if clipboard empty or unavailable
 */
private fun pasteTextFromClipboard(
    context: Context,
    textBoxState: com.boox.day.editor.state.TextBoxState,
    scope: kotlinx.coroutines.CoroutineScope,
    textAnnotationRepository: TextAnnotationRepository
): Boolean {
    val selectedTextBox = textBoxState.selectedTextBox ?: return false
    val richClipboard = com.boox.day.editor.state.RichTextClipboard

    // Prefer app-local rich text clipboard (has spans)
    val pastedText: String
    val pastedSpans: List<com.boox.day.data.model.RichTextSpan>

    if (richClipboard.hasContent()) {
        pastedText = richClipboard.text ?: return false
        pastedSpans = richClipboard.spans
    } else {
        // Fall back to system clipboard (plain text only)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        val clipData = clipboard.primaryClip ?: return false
        if (clipData.itemCount == 0) return false

        pastedText = clipData.getItemAt(0).coerceToText(context).toString()
        if (pastedText.isEmpty()) return false
        pastedSpans = emptyList()
    }

    val isEditing = textBoxState.editingTextBox != null

    // Get current text and spans based on mode
    val currentText: String
    val currentSpans: List<com.boox.day.data.model.RichTextSpan>

    if (isEditing) {
        // Editor mode: use editing state
        currentText = textBoxState.currentEditText ?: selectedTextBox.content
        currentSpans = textBoxState.currentSpans.ifEmpty {
            RichTextSpanSerializer.decode(selectedTextBox.spans)
        }
    } else {
        // Select mode: use annotation directly
        currentText = selectedTextBox.content
        currentSpans = RichTextSpanSerializer.decode(selectedTextBox.spans)
    }

    // Calculate offset for pasted spans (they start after current text)
    val offset = currentText.length

    // Adjust pasted span positions by offset
    val adjustedPastedSpans = pastedSpans.map { span ->
        when (span) {
            is com.boox.day.data.model.BoldSpan -> span.copy(start = span.start + offset, end = span.end + offset)
            is com.boox.day.data.model.ItalicSpan -> span.copy(start = span.start + offset, end = span.end + offset)
            is com.boox.day.data.model.UnderlineSpan -> span.copy(start = span.start + offset, end = span.end + offset)
            is com.boox.day.data.model.ColorSpan -> span.copy(start = span.start + offset, end = span.end + offset)
            is com.boox.day.data.model.SizeSpan -> span.copy(start = span.start + offset, end = span.end + offset)
            is com.boox.day.data.model.HeaderSpan -> span.copy(start = span.start + offset, end = span.end + offset)
            is com.boox.day.data.model.BulletSpan -> span.copy(start = span.start + offset, end = span.end + offset)
            is com.boox.day.data.model.NumberedListSpan -> span.copy(start = span.start + offset, end = span.end + offset)
            is com.boox.day.data.model.StrikethroughSpan -> span.copy(start = span.start + offset, end = span.end + offset)
        }
    }

    // Merge current spans with pasted spans
    val newSpans = currentSpans + adjustedPastedSpans
    val newText = currentText + pastedText
    val newSpansJson = RichTextSpanSerializer.encode(newSpans)

    if (isEditing) {
        // Editor mode: update editing state (will be saved when editor closes)
        textBoxState.snapshotForUndo()
        textBoxState.updateEditText(newText)
        textBoxState.updateSpans(newSpans)
    } else {
        // Select mode: directly update annotation and save to DB
        val updated = selectedTextBox.copy(
            content = newText,
            spans = newSpansJson
        )

        // Update in-memory state
        textBoxState.updateSelectedTextBox(updated)

        // Update PageDataManager
        com.boox.day.data.PageDataManager.removeTextAnnotation(selectedTextBox.pageId, selectedTextBox.id)
        com.boox.day.data.PageDataManager.addTextAnnotation(selectedTextBox.pageId, updated)

        // Save to database
        scope.launch(Dispatchers.IO) {
            textAnnotationRepository.update(updated)
        }

        // Index for semantic search (content changed)
        TextAnnotationIndexer.getInstanceOrNull()?.onAnnotationChanged(updated)

        // Refresh canvas
        scope.launch {
            DrawCanvas.forceUpdate.emit(null)
        }
    }

    return true
}

private fun toggleBold(
    controlTower: EditorControlTower,
    textBoxState: com.boox.day.editor.state.TextBoxState,
    scope: kotlinx.coroutines.CoroutineScope,
    textAnnotationRepository: TextAnnotationRepository
) {
    val selectedTextBox = textBoxState.selectedTextBox ?: run {
        log.w("TOGGLE_BOLD: selectedTextBox is null!")
        return
    }

    val selectionRange = textBoxState.currentSelectionRange
    log.d("TOGGLE_BOLD: currentSelectionRange=$selectionRange, currentSpans=${textBoxState.currentSpans.size}")

    if (selectionRange != null && !selectionRange.isEmpty()) {
        // SELECTION MODE: Apply bold to selected text only
        textBoxState.snapshotForUndo()
        val currentSpans = textBoxState.currentSpans
        val newSpans = applyBoldToSelection(currentSpans, selectionRange)
        textBoxState.updateSpans(newSpans)
        log.d("TOGGLE_BOLD: Applied to selection $selectionRange, spans count=${newSpans.size}")
    } else {
        // WHOLE TEXT MODE: Toggle bold on entire text box (legacy behavior)
        log.d("TOGGLE_BOLD: START - content='${selectedTextBox.content.take(30)}', fontWeight=${selectedTextBox.fontWeight}")

        val newWeight = if (selectedTextBox.fontWeight >= 700) 400 else 700
        val updated = selectedTextBox.copy(fontWeight = newWeight)

        log.d("TOGGLE_BOLD: END - content='${updated.content.take(30)}', newWeight=$newWeight")

        // Update only the local selection state - the overlay will render the styled text
        textBoxState.selectedTextBox = updated
    }
}

private fun toggleItalic(
    controlTower: EditorControlTower,
    textBoxState: com.boox.day.editor.state.TextBoxState,
    scope: kotlinx.coroutines.CoroutineScope,
    textAnnotationRepository: TextAnnotationRepository
) {
    val selectedTextBox = textBoxState.selectedTextBox ?: return
    val selectionRange = textBoxState.currentSelectionRange

    if (selectionRange != null && !selectionRange.isEmpty()) {
        // SELECTION MODE: Apply italic to selected text only
        textBoxState.snapshotForUndo()
        val currentSpans = textBoxState.currentSpans
        val newSpans = applyItalicToSelection(currentSpans, selectionRange)
        textBoxState.updateSpans(newSpans)
        log.d("TOGGLE_ITALIC: Applied to selection $selectionRange, spans count=${newSpans.size}")
    } else {
        // WHOLE TEXT MODE: Toggle italic on entire text box (legacy behavior)
        val newStyle = if (selectedTextBox.fontStyle == "italic") "normal" else "italic"
        val updated = selectedTextBox.copy(fontStyle = newStyle)

        // Update only the local selection state - the overlay will render the styled text
        textBoxState.selectedTextBox = updated
    }
}

private fun saveAndStopEditing(
    controlTower: EditorControlTower,
    textBoxState: com.boox.day.editor.state.TextBoxState,
    scope: kotlinx.coroutines.CoroutineScope,
    textAnnotationRepository: TextAnnotationRepository
) {
    val editingTextBox = textBoxState.editingTextBox ?: return
    val baseAnnotation = textBoxState.selectedTextBox ?: editingTextBox
    val newText = textBoxState.currentEditText ?: baseAnnotation.content
    val newSpans = textBoxState.currentSpans

    // Only save if text changed or spans exist
    if (newText != baseAnnotation.content || newSpans.isNotEmpty()) {
        // Base the updated annotation on the latest selectedTextBox so that
        // changes like alignment, color, or font size performed while editing
        // are preserved together with the new text and spans.
        val updated = baseAnnotation.copy(
            content = newText,
            spans = RichTextSpanSerializer.encode(newSpans)
        )

        // Update in memory
        val pageId = controlTower.getCurrentPageId()
        PageDataManager.removeTextAnnotation(pageId, editingTextBox.id)
        PageDataManager.addTextAnnotation(pageId, updated)

        // Update state
        textBoxState.updateSelectedTextBox(updated)

        // Save to database
        scope.launch(Dispatchers.IO) {
            textAnnotationRepository.update(updated)
        }

        // Index for semantic search (content changed)
        TextAnnotationIndexer.getInstanceOrNull()?.onAnnotationChanged(updated)

        // Refresh canvas
        scope.launch {
            DrawCanvas.forceUpdate.emit(null)
        }
    }

    textBoxState.stopEditing()
}

private fun saveEditedText(
    controlTower: EditorControlTower,
    textBoxState: com.boox.day.editor.state.TextBoxState,
    newText: String,
    scope: kotlinx.coroutines.CoroutineScope,
    textAnnotationRepository: TextAnnotationRepository
) {
    val selectedTextBox = textBoxState.selectedTextBox ?: run {
        log.w("SAVE_TEXT: selectedTextBox is null!")
        return
    }
    val pageId = controlTower.getCurrentPageId()

    log.d("SAVE_TEXT: newText='${newText.take(50)}', selectedTextBox.content='${selectedTextBox.content.take(50)}'")

    // Update the annotation with new text content
    val updated = selectedTextBox.copy(content = newText)

    // Update in memory
    PageDataManager.removeTextAnnotation(pageId, selectedTextBox.id)
    PageDataManager.addTextAnnotation(pageId, updated)

    // Update local state
    textBoxState.selectedTextBox = updated
    textBoxState.stopEditing()

    // Save to database
    scope.launch(Dispatchers.IO) {
        textAnnotationRepository.update(updated)
    }

    // Index for semantic search (content changed)
    TextAnnotationIndexer.getInstanceOrNull()?.onAnnotationChanged(updated)

    // Refresh canvas
    scope.launch {
        DrawCanvas.forceUpdate.emit(null)
    }
}

private fun setTextColor(
    controlTower: EditorControlTower,
    textBoxState: com.boox.day.editor.state.TextBoxState,
    color: Int,
    scope: kotlinx.coroutines.CoroutineScope,
    textAnnotationRepository: TextAnnotationRepository
) {
    val selectionRange = textBoxState.currentSelectionRange

    if (selectionRange != null && !selectionRange.isEmpty()) {
        // SELECTION MODE: apply color to selected text only via spans
        textBoxState.snapshotForUndo()
        val currentSpans = textBoxState.currentSpans
        val newSpans = applyColorToSelection(currentSpans, selectionRange, color)
        textBoxState.updateSpans(newSpans)
        log.d("SET_TEXT_COLOR: Applied to selection $selectionRange, spans count=${newSpans.size}")
    } else {
        val selectedTextBox = textBoxState.selectedTextBox ?: return

        // WHOLE TEXT MODE: Only change the color - do NOT touch position or save yet
        val updated = selectedTextBox.copy(textColor = color)

        // Update only the local selection state - the overlay will render the styled text
        textBoxState.selectedTextBox = updated
    }
}

private fun setFontSize(
    controlTower: EditorControlTower,
    textBoxState: com.boox.day.editor.state.TextBoxState,
    fontSize: Float,
    scope: kotlinx.coroutines.CoroutineScope,
    textAnnotationRepository: TextAnnotationRepository
) {
    val selectionRange = textBoxState.currentSelectionRange
    val selectedTextBox = textBoxState.selectedTextBox ?: return

    if (selectionRange != null && !selectionRange.isEmpty()) {
        // SELECTION MODE: apply relative size span so the selected text
        // scales compared to the base annotation font size.
        val baseSize = if (selectedTextBox.fontSize > 0f) {
            selectedTextBox.fontSize
        } else {
            fontSize
        }
        val sizeMultiplier = if (baseSize > 0f) {
            fontSize / baseSize
        } else {
            1f
        }

        textBoxState.snapshotForUndo()
        val currentSpans = textBoxState.currentSpans
        val newSpans = applySizeToSelection(currentSpans, selectionRange, sizeMultiplier)
        textBoxState.updateSpans(newSpans)
        log.d("SET_FONT_SIZE: Applied to selection $selectionRange, size=$fontSize, base=$baseSize, mult=$sizeMultiplier, spans=${newSpans.size}")
    } else {
        // WHOLE TEXT MODE: Only change the font size - position is committed later.
        val updated = selectedTextBox.copy(fontSize = fontSize)
        textBoxState.selectedTextBox = updated
    }
}

private fun toggleUnderline(
    controlTower: EditorControlTower,
    textBoxState: com.boox.day.editor.state.TextBoxState
) {
    val selectedTextBox = textBoxState.selectedTextBox ?: return
    val selectionRange = textBoxState.currentSelectionRange

    textBoxState.snapshotForUndo()
    val currentSpans = textBoxState.currentSpans

    if (selectionRange != null && !selectionRange.isEmpty()) {
        // SELECTION MODE: Apply underline to selected text only
        val newSpans = applyUnderlineToSelection(currentSpans, selectionRange)
        textBoxState.updateSpans(newSpans)
    } else {
        // WHOLE TEXT MODE: Apply/toggle underline span for entire text
        val textLength = (textBoxState.currentEditText ?: selectedTextBox.content).length
        if (textLength == 0) return

        val wholeTextRange = 0..textLength
        val newSpans = applyUnderlineToSelection(currentSpans, wholeTextRange)
        textBoxState.updateSpans(newSpans)
    }
}

private fun toggleBulletList(
    controlTower: EditorControlTower,
    textBoxState: com.boox.day.editor.state.TextBoxState
) {
    val selectedTextBox = textBoxState.selectedTextBox ?: return

    textBoxState.snapshotForUndo()

    // Get current text
    val currentText = textBoxState.currentEditText ?: selectedTextBox.content
    val currentSpans = textBoxState.currentSpans

    // Process each line - toggle bullets
    val lines = currentText.split("\n")
    val allHaveBullets = lines.all { it.trimStart().startsWith("• ") || it.isBlank() }

    val newLines = if (allHaveBullets) {
        // REMOVE bullets from all lines
        lines.map { line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("• ")) {
                val leadingSpaces = line.length - line.trimStart().length
                " ".repeat(leadingSpaces) + trimmed.removePrefix("• ")
            } else {
                line
            }
        }
    } else {
        // ADD bullets to all non-empty lines that don't have them
        lines.map { line ->
            val trimmed = line.trimStart()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("• ")) {
                val leadingSpaces = line.length - line.trimStart().length
                " ".repeat(leadingSpaces) + "• " + trimmed
            } else {
                line
            }
        }
    }

    val newText = newLines.joinToString("\n")

    // Adjust spans for text length change
    val lengthDelta = newText.length - currentText.length
    val adjustedSpans = if (lengthDelta != 0) {
        // Simple adjustment: shift all spans by the delta
        // This is a simplification - proper implementation would track per-line changes
        currentSpans.map { span ->
            when (span) {
                is com.boox.day.data.model.BoldSpan -> span.copy(
                    start = (span.start + lengthDelta).coerceAtLeast(0),
                    end = (span.end + lengthDelta).coerceAtLeast(0)
                )
                is com.boox.day.data.model.ItalicSpan -> span.copy(
                    start = (span.start + lengthDelta).coerceAtLeast(0),
                    end = (span.end + lengthDelta).coerceAtLeast(0)
                )
                is com.boox.day.data.model.UnderlineSpan -> span.copy(
                    start = (span.start + lengthDelta).coerceAtLeast(0),
                    end = (span.end + lengthDelta).coerceAtLeast(0)
                )
                is com.boox.day.data.model.ColorSpan -> span.copy(
                    start = (span.start + lengthDelta).coerceAtLeast(0),
                    end = (span.end + lengthDelta).coerceAtLeast(0)
                )
                is com.boox.day.data.model.SizeSpan -> span.copy(
                    start = (span.start + lengthDelta).coerceAtLeast(0),
                    end = (span.end + lengthDelta).coerceAtLeast(0)
                )
                else -> span
            }
        }.filter { it.start < it.end && it.end <= newText.length }
    } else {
        currentSpans
    }

    // Update text and spans
    textBoxState.updateEditText(newText)
    textBoxState.updateSpans(adjustedSpans)
}

private fun setTextAlign(
    textBoxState: com.boox.day.editor.state.TextBoxState,
    align: String
) {
    val selectedTextBox = textBoxState.selectedTextBox ?: return
    val updated = selectedTextBox.copy(textAlign = align)
    textBoxState.selectedTextBox = updated
}
