package com.boox.day.editor.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.boox.day.data.db.TextAnnotation
import com.boox.day.data.model.RichTextSpan
import com.boox.day.data.model.RichTextSpanSerializer
import com.boox.day.editor.drawing.RichTextConverter

private const val TAG = "InlineRichTextEditor"

/**
 * Inline rich text editor that replaces the floating TextBoxEditor.
 * Positioned exactly over the text annotation for seamless editing experience.
 *
 * Key features:
 * - Uses AnnotatedString to show formatting while typing
 * - Handles span adjustment when text is inserted/deleted
 * - Uses local state to avoid e-ink flicker (only commits on save)
 * - Tracks selection range for selection-based formatting
 */
@Composable
fun InlineRichTextEditor(
    annotation: TextAnnotation,
    spans: List<RichTextSpan>,
    editText: String?,
    onTextChanged: (String, List<RichTextSpan>) -> Unit,
    onSelectionChanged: (IntRange?) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSpansInitialized: ((List<RichTextSpan>) -> Unit)? = null,
    onSnapshotForUndo: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Parse existing spans from annotation
    val initialSpans = remember(annotation.id) {
        RichTextSpanSerializer.decode(annotation.spans).also {
            Log.d(TAG, "Parsed ${it.size} spans from annotation ${annotation.id}")
        }
    }

    // LOCAL state - only committed when user confirms (not on every keystroke)
    // This prevents e-ink flicker during typing
    var localText by remember(annotation.id) { mutableStateOf(annotation.content) }
    var localSpans by remember(annotation.id) { mutableStateOf(initialSpans) }

    val keyboardController = LocalSoftwareKeyboardController.current

    // Track whether we have explicitly hidden the keyboard due to a non-collapsed selection.
    // This lets us auto-show it again once the user collapses the selection to resume typing.
    var keyboardHiddenForSelection by remember(annotation.id) { mutableStateOf(false) }

    // Track last known selection for span adjustment
    var lastSelection by remember(annotation.id) { mutableStateOf(TextRange.Zero) }

    // Monotonically increasing version used to know when spans/text changed so
    // the Android EditText can be updated with a fresh Spannable representation.
    var spanVersion by remember(annotation.id) { mutableStateOf(0) }

    // Keep local spans in sync with external state (e.g., toolbar bold/italic).
    // This ensures that selection-based formatting applied from the toolbar is
    // not lost on the next text edit and will be saved correctly.
    LaunchedEffect(spans) {
        // Only update if the reference actually changed to avoid redundant work.
        if (spans !== localSpans) {
            localSpans = spans
            spanVersion++
        }
    }

    // Keep local text in sync with external state (e.g., undo from toolbar).
    LaunchedEffect(editText) {
        if (editText != null && editText != localText) {
            localText = editText
            spanVersion++
        }
    }

    // CRITICAL: Report initial spans to parent immediately when editor is composed
    // This ensures textBoxState.currentSpans is set even if user doesn't type anything
    LaunchedEffect(annotation.id) {
        Log.d(TAG, "Reporting ${initialSpans.size} initial spans to parent")
        onSpansInitialized?.invoke(initialSpans)
    }

    // Ensure keyboard is visible when editing starts (caret mode)
    LaunchedEffect(annotation.id) {
        keyboardController?.show()
    }

    Box(
        modifier = modifier
            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.95f))
    ) {
        AndroidView<RichEditText>(
            modifier = Modifier.fillMaxSize(),
            factory = { context: Context ->
                RichEditText(context).apply {
                    // Remove default View padding so the visual content
                    // matches the non-editing Text rendering.
                    setPadding(0, 0, 0, 0)

                    // Apply initial rich formatting (spans) so the editor
                    // visually matches the text on the canvas.
                    val spanned = RichTextConverter.toSpanned(
                        localText,
                        localSpans,
                        annotation.fontSize
                    )
                    setText(spanned)
                    // Position caret at end initially
                    setSelection(text.length)

                    // Text appearance to roughly match Compose Text
                    setTextColor(annotation.textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, annotation.fontSize)
                    typeface = when {
                        annotation.fontWeight >= 700 && annotation.fontStyle == "italic" ->
                            android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD_ITALIC)
                        annotation.fontWeight >= 700 ->
                            android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                        annotation.fontStyle == "italic" ->
                            android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
                        else -> android.graphics.Typeface.DEFAULT
                    }

                    // Multi-line behavior
                    isSingleLine = false
                    maxLines = Int.MAX_VALUE
                    setHorizontallyScrolling(false)

                    // Transparent background - we draw background in Compose Box
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    // Apply initial alignment based on annotation
                    gravity = when (annotation.textAlign) {
                        "center" -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        "right" -> Gravity.TOP or Gravity.END
                        else -> Gravity.TOP or Gravity.START
                    }

                    setOnFocusChangeListener { _, hasFocus ->
                        onFocusChanged(hasFocus)
                    }

                    // Listen for selection changes (including long-press)
                    onSelectionChangedListener = { selStart, selEnd ->
                        handleSelectionChanged(
                            selStart = selStart,
                            selEnd = selEnd,
                            keyboardController = keyboardController,
                            keyboardHiddenForSelection = { keyboardHiddenForSelection },
                            setKeyboardHiddenForSelection = { keyboardHiddenForSelection = it },
                            onSelectionChanged = onSelectionChanged,
                            onCaretUpdated = { caretRange ->
                                lastSelection = caretRange
                            }
                        )
                    }

                    // Track text changes to adjust spans
                    var beforeChangeSelection: TextRange = lastSelection
                    val editText = this
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                        ) {
                            // Skip snapshot during programmatic updates (e.g., undo/redo, toolbar changes)
                            // Without this guard, pressing Undo would overwrite the snapshot with the
                            // just-restored state, making consecutive undos impossible.
                            if (editText.ignoreTextChanges) return

                            // Snapshot current state so that undo can restore
                            // text + spans if this edit is reverted.
                            onSnapshotForUndo?.invoke()
                            beforeChangeSelection = lastSelection
                        }

                        override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                        ) {
                        }

                        override fun afterTextChanged(s: Editable?) {
                            // Ignore programmatic text updates originating from Compose.
                            if (editText.ignoreTextChanges) return

                            val newText = s?.toString() ?: ""
                            val oldText = localText
                            if (newText == oldText) return

                            val updatedSpans = adjustSpansForTextChange(
                                oldText = oldText,
                                newText = newText,
                                oldSelection = beforeChangeSelection,
                                currentSpans = localSpans
                            )

                            localText = newText
                            localSpans = updatedSpans
                            spanVersion++

                            Log.d(
                                TAG,
                                "Text changed: '${oldText.take(20)}...' -> '${newText.take(20)}...'"
                            )
                            Log.d(TAG, "Spans adjusted: ${localSpans.size} spans")

                            onTextChanged(newText, updatedSpans)

                            // Update last selection based on current cursor/selection
                            val selStart = selectionStart.coerceIn(0, newText.length)
                            val selEnd = selectionEnd.coerceIn(0, newText.length)
                            lastSelection = TextRange(selStart, selEnd)

                            // Also propagate selection callbacks
                            handleSelectionChanged(
                                selStart = selStart,
                                selEnd = selEnd,
                                keyboardController = keyboardController,
                                keyboardHiddenForSelection = { keyboardHiddenForSelection },
                                setKeyboardHiddenForSelection = { keyboardHiddenForSelection = it },
                                onSelectionChanged = onSelectionChanged,
                                onCaretUpdated = { caretRange ->
                                    lastSelection = caretRange
                                }
                            )
                        }
                    })

                    // Request focus when view is created so keyboard can appear
                    requestFocus()
                }
            },
            update = { view ->
                val editText = view

                // Always keep base text color and size in sync with
                // the current annotation so whole-text color / size
                // changes are reflected even without span updates.
                editText.setTextColor(annotation.textColor)
                editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, annotation.fontSize)

                 // Keep alignment in sync with annotation while editing.
                 editText.gravity = when (annotation.textAlign) {
                     "center" -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                     "right" -> Gravity.TOP or Gravity.END
                     else -> Gravity.TOP or Gravity.START
                 }
                // Only reapply text + spans when our version changes or the
                // underlying plain text is out of sync.
                val needsUpdate =
                    editText.spanVersionApplied != spanVersion ||
                            editText.text.toString() != localText

                if (needsUpdate) {
                    editText.ignoreTextChanges = true

                    val spanned = RichTextConverter.toSpanned(
                        localText,
                        localSpans,
                        annotation.fontSize
                    )
                    editText.setText(spanned)

                    // Restore selection as best as possible.
                    val start = lastSelection.start.coerceIn(0, localText.length)
                    val end = lastSelection.end.coerceIn(start, localText.length)
                    editText.setSelection(start, end)

                    editText.spanVersionApplied = spanVersion
                    editText.ignoreTextChanges = false
                }
            }
        )
    }
}

/**
 * Custom EditText that disables the standard selection/copy/paste toolbar
 * while still allowing text selection handles for formatting.
 */
private class RichEditText(context: Context) : EditText(context) {

    var onSelectionChangedListener: ((Int, Int) -> Unit)? = null

    // When true, TextWatcher callbacks are ignored. Used while we are
    // programmatically updating the text from Compose.
    var ignoreTextChanges: Boolean = false

    // Last spanVersion that has been applied to this view.
    var spanVersionApplied: Int = -1

    init {
        // Disable selection and insertion action mode toolbars
        setCustomSelectionActionModeCallback(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.clear()
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.clear()
                return false
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false

            override fun onDestroyActionMode(mode: ActionMode) {}
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setCustomInsertionActionModeCallback(object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    menu.clear()
                    return true
                }

                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                    menu.clear()
                    return false
                }

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false

                override fun onDestroyActionMode(mode: ActionMode) {}
            })
        }

        // Remove extra font padding and ensure text starts at the top-left
        // so the inline editor matches the non-editing canvas text box.
        includeFontPadding = false
        setLineSpacing(0f, 1f)
        gravity = Gravity.TOP or Gravity.START
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        // When we are updating the text programmatically from Compose,
        // ignore selection change callbacks so that we don't overwrite
        // the last known caret position with transient values like (0, 0).
        if (!ignoreTextChanges) {
            onSelectionChangedListener?.invoke(selStart, selEnd)
        }
    }
}

/**
 * Shared helper to handle selection changes: updates Compose selection state and
 * manages keyboard visibility so the system toolbar cannot overlap the custom toolbar.
 */
private fun handleSelectionChanged(
    selStart: Int,
    selEnd: Int,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?,
    keyboardHiddenForSelection: () -> Boolean,
    setKeyboardHiddenForSelection: (Boolean) -> Unit,
    onSelectionChanged: (IntRange?) -> Unit,
    onCaretUpdated: (TextRange) -> Unit
) {
    val lengthSafeStart = selStart.coerceAtLeast(0)
    val lengthSafeEnd = selEnd.coerceAtLeast(0)
    val caretRange = TextRange(lengthSafeStart, lengthSafeEnd)

    // Update caret/selection position used by the editor to restore
    // cursor location after span or text changes.
    onCaretUpdated(caretRange)

    // For formatting tools, we only treat non-collapsed ranges as a
    // selection; a collapsed caret reports null here.
    val selectionRange: IntRange? = if (lengthSafeStart == lengthSafeEnd) {
        null
    } else {
        val start = minOf(lengthSafeStart, lengthSafeEnd)
        val end = maxOf(lengthSafeStart, lengthSafeEnd)
        start..end
    }

    onSelectionChanged(selectionRange)

    if (selectionRange == null) {
        if (keyboardHiddenForSelection()) {
            setKeyboardHiddenForSelection(false)
            keyboardController?.show()
        }
    } else {
        if (!keyboardHiddenForSelection()) {
            setKeyboardHiddenForSelection(true)
            keyboardController?.hide()
        }
    }
}

/**
 * Adjust spans when text is inserted or deleted.
 * This ensures formatting stays with the correct text portions.
 */
private fun adjustSpansForTextChange(
    oldText: String,
    newText: String,
    oldSelection: TextRange,
    currentSpans: List<RichTextSpan>
): List<RichTextSpan> {
    val selectionStart = oldSelection.min
    val selectionEnd = oldSelection.max

    // If selection was collapsed (cursor position), this is an insertion/deletion at cursor
    // If selection was not collapsed, this is a replacement of selected text
    val lengthDiff = newText.length - oldText.length

    return if (lengthDiff > 0) {
        // Text was inserted
        val insertPosition = selectionStart
        val insertLength = lengthDiff
        RichTextSpanSerializer.adjustForInsertion(currentSpans, insertPosition, insertLength)
    } else if (lengthDiff < 0) {
        // Text was deleted
        val deleteStart = selectionStart
        val deleteEnd = if (oldSelection.collapsed) {
            // Backspace or delete key - check which direction
            if (selectionStart > 0 && oldText.length > newText.length) {
                // Backspace - deleted character before cursor
                selectionStart
            } else {
                selectionStart - lengthDiff
            }
        } else {
            selectionEnd
        }
        RichTextSpanSerializer.adjustForDeletion(currentSpans, minOf(deleteStart, deleteEnd), maxOf(deleteStart, deleteEnd))
    } else {
        // Length unchanged but text changed (e.g., character replacement)
        currentSpans
    }
}

/**
 * Apply bold formatting to selected text range.
 * Creates a new BoldSpan for the selection.
 */
fun applyBoldToSelection(
    currentSpans: List<RichTextSpan>,
    selectionRange: IntRange
): List<RichTextSpan> {
    if (selectionRange.isEmpty()) return currentSpans
    val selStart = selectionRange.first
    val selEnd = selectionRange.last
    if (selStart >= selEnd) return currentSpans

    val boldSpans = currentSpans.filterIsInstance<com.boox.day.data.model.BoldSpan>()

    // Determine how much of the selection is currently bold by computing
    // the union of all bold spans intersecting the selection.
    val intersections = boldSpans.mapNotNull { span ->
        val start = maxOf(span.start, selStart)
        val end = minOf(span.end, selEnd)
        if (start < end) start to end else null
    }.sortedBy { it.first }

    var coveredLength = 0
    var currentStart = -1
    var currentEnd = -1
    for ((start, end) in intersections) {
        if (currentStart == -1) {
            currentStart = start
            currentEnd = end
        } else if (start <= currentEnd) {
            currentEnd = maxOf(currentEnd, end)
        } else {
            coveredLength += (currentEnd - currentStart)
            currentStart = start
            currentEnd = end
        }
    }
    if (currentStart != -1) {
        coveredLength += (currentEnd - currentStart)
    }

    val selectionLength = selEnd - selStart
    val isFullyBold = coveredLength >= selectionLength

    return if (!isFullyBold) {
        // PARTIAL OR NO BOLD: apply bold to the entire selection.
        // Remove any overlapping bold spans first, then add a single span.
        val selectionSpan = com.boox.day.data.model.BoldSpan(selStart, selEnd)
        val filtered = currentSpans.filterNot { span ->
            span is com.boox.day.data.model.BoldSpan &&
                    RichTextSpanSerializer.overlaps(span, selectionSpan)
        }
        filtered + selectionSpan
    } else {
        // FULLY BOLD: toggle bold off within the selection by splitting spans
        // at the selection boundaries and keeping only the parts outside.
        val result = mutableListOf<RichTextSpan>()
        for (span in currentSpans) {
            if (span is com.boox.day.data.model.BoldSpan) {
                if (span.end <= selStart || span.start >= selEnd) {
                    // No overlap
                    result += span
                } else {
                    // Overlaps selection; keep pieces outside [selStart, selEnd)
                    if (span.start < selStart) {
                        result += span.copy(end = selStart)
                    }
                    if (span.end > selEnd) {
                        result += span.copy(start = selEnd)
                    }
                }
            } else {
                result += span
            }
        }
        result
    }
}

/**
 * Apply italic formatting to selected text range.
 */
fun applyItalicToSelection(
    currentSpans: List<RichTextSpan>,
    selectionRange: IntRange
): List<RichTextSpan> {
    if (selectionRange.isEmpty()) return currentSpans
    val selStart = selectionRange.first
    val selEnd = selectionRange.last
    if (selStart >= selEnd) return currentSpans

    val italicSpans = currentSpans.filterIsInstance<com.boox.day.data.model.ItalicSpan>()

    // Compute coverage of italic spans over the selection.
    val intersections = italicSpans.mapNotNull { span ->
        val start = maxOf(span.start, selStart)
        val end = minOf(span.end, selEnd)
        if (start < end) start to end else null
    }.sortedBy { it.first }

    var coveredLength = 0
    var currentStart = -1
    var currentEnd = -1
    for ((start, end) in intersections) {
        if (currentStart == -1) {
            currentStart = start
            currentEnd = end
        } else if (start <= currentEnd) {
            currentEnd = maxOf(currentEnd, end)
        } else {
            coveredLength += (currentEnd - currentStart)
            currentStart = start
            currentEnd = end
        }
    }
    if (currentStart != -1) {
        coveredLength += (currentEnd - currentStart)
    }

    val selectionLength = selEnd - selStart
    val isFullyItalic = coveredLength >= selectionLength

    return if (!isFullyItalic) {
        // PARTIAL OR NO ITALIC: apply italic to entire selection.
        val selectionSpan = com.boox.day.data.model.ItalicSpan(selStart, selEnd)
        val filtered = currentSpans.filterNot { span ->
            span is com.boox.day.data.model.ItalicSpan &&
                    RichTextSpanSerializer.overlaps(span, selectionSpan)
        }
        filtered + selectionSpan
    } else {
        // FULLY ITALIC: toggle italic off within the selection by splitting spans.
        val result = mutableListOf<RichTextSpan>()
        for (span in currentSpans) {
            if (span is com.boox.day.data.model.ItalicSpan) {
                if (span.end <= selStart || span.start >= selEnd) {
                    result += span
                } else {
                    if (span.start < selStart) {
                        result += span.copy(end = selStart)
                    }
                    if (span.end > selEnd) {
                        result += span.copy(start = selEnd)
                    }
                }
            } else {
                result += span
            }
        }
        result
    }
}

/**
 * Apply underline formatting to selected text range.
 */
fun applyUnderlineToSelection(
    currentSpans: List<RichTextSpan>,
    selectionRange: IntRange
): List<RichTextSpan> {
    if (selectionRange.isEmpty()) return currentSpans
    val selStart = selectionRange.first
    val selEnd = selectionRange.last
    if (selStart >= selEnd) return currentSpans

    val underlineSpans = currentSpans.filterIsInstance<com.boox.day.data.model.UnderlineSpan>()

    val intersections = underlineSpans.mapNotNull { span ->
        val start = maxOf(span.start, selStart)
        val end = minOf(span.end, selEnd)
        if (start < end) start to end else null
    }.sortedBy { it.first }

    var coveredLength = 0
    var currentStart = -1
    var currentEnd = -1
    for ((start, end) in intersections) {
        if (currentStart == -1) {
            currentStart = start
            currentEnd = end
        } else if (start <= currentEnd) {
            currentEnd = maxOf(currentEnd, end)
        } else {
            coveredLength += (currentEnd - currentStart)
            currentStart = start
            currentEnd = end
        }
    }
    if (currentStart != -1) {
        coveredLength += (currentEnd - currentStart)
    }

    val selectionLength = selEnd - selStart
    val isFullyUnderlined = coveredLength >= selectionLength

    return if (!isFullyUnderlined) {
        // PARTIAL OR NO UNDERLINE: apply underline to entire selection.
        val selectionSpan = com.boox.day.data.model.UnderlineSpan(selStart, selEnd)
        val filtered = currentSpans.filterNot { span ->
            span is com.boox.day.data.model.UnderlineSpan &&
                    RichTextSpanSerializer.overlaps(span, selectionSpan)
        }
        filtered + selectionSpan
    } else {
        // FULLY UNDERLINED: remove underline only within the selection.
        val result = mutableListOf<RichTextSpan>()
        for (span in currentSpans) {
            if (span is com.boox.day.data.model.UnderlineSpan) {
                if (span.end <= selStart || span.start >= selEnd) {
                    result += span
                } else {
                    if (span.start < selStart) {
                        result += span.copy(end = selStart)
                    }
                    if (span.end > selEnd) {
                        result += span.copy(start = selEnd)
                    }
                }
            } else {
                result += span
            }
        }
        result
    }
}

/**
 * Apply color to selected text range.
 */
fun applyColorToSelection(
    currentSpans: List<RichTextSpan>,
    selectionRange: IntRange,
    color: Int
): List<RichTextSpan> {
    if (selectionRange.isEmpty()) return currentSpans

    // Remove any existing color spans that overlap with selection
    val filteredSpans = currentSpans.filterNot { span ->
        span is com.boox.day.data.model.ColorSpan &&
                RichTextSpanSerializer.overlaps(span, com.boox.day.data.model.ColorSpan(selectionRange.first, selectionRange.last, color))
    }

    val newSpan = com.boox.day.data.model.ColorSpan(selectionRange.first, selectionRange.last, color)
    return filteredSpans + newSpan
}

/**
 * Apply relative size to selected text range.
 */
fun applySizeToSelection(
    currentSpans: List<RichTextSpan>,
    selectionRange: IntRange,
    sizeMultiplier: Float
): List<RichTextSpan> {
    if (selectionRange.isEmpty()) return currentSpans

    val newSpan = com.boox.day.data.model.SizeSpan(selectionRange.first, selectionRange.last, sizeMultiplier)

    // Remove any existing size-related spans (SizeSpan, HeaderSpan) that overlap
    // with the selection so that the entire range is normalized to a single size.
    val filteredSpans = currentSpans.filterNot { span ->
        (span is com.boox.day.data.model.SizeSpan ||
                span is com.boox.day.data.model.HeaderSpan) &&
            RichTextSpanSerializer.overlaps(span, newSpan)
    }

    return filteredSpans + newSpan
}

/**
 * Apply bullet list formatting to selected text range.
 * Toggles: if fully bulleted, removes bullets; otherwise applies bullets.
 */
fun applyBulletToSelection(
    currentSpans: List<RichTextSpan>,
    selectionRange: IntRange
): List<RichTextSpan> {
    if (selectionRange.isEmpty()) return currentSpans
    val selStart = selectionRange.first
    val selEnd = selectionRange.last
    if (selStart >= selEnd) return currentSpans

    val bulletSpans = currentSpans.filterIsInstance<com.boox.day.data.model.BulletSpan>()

    // Calculate how much of the selection is already covered by bullet spans
    val intersections = bulletSpans.mapNotNull { span ->
        val start = maxOf(span.start, selStart)
        val end = minOf(span.end, selEnd)
        if (start < end) start to end else null
    }.sortedBy { it.first }

    var coveredLength = 0
    var currentStart = -1
    var currentEnd = -1
    for ((start, end) in intersections) {
        if (currentStart == -1) {
            currentStart = start
            currentEnd = end
        } else if (start <= currentEnd) {
            currentEnd = maxOf(currentEnd, end)
        } else {
            coveredLength += (currentEnd - currentStart)
            currentStart = start
            currentEnd = end
        }
    }
    if (currentStart != -1) {
        coveredLength += (currentEnd - currentStart)
    }

    val selectionLength = selEnd - selStart
    val isFullyBulleted = coveredLength >= selectionLength

    return if (!isFullyBulleted) {
        // PARTIAL OR NO BULLETS: apply bullet to entire selection.
        val selectionSpan = com.boox.day.data.model.BulletSpan(selStart, selEnd)
        val filtered = currentSpans.filterNot { span ->
            span is com.boox.day.data.model.BulletSpan &&
                    RichTextSpanSerializer.overlaps(span, selectionSpan)
        }
        filtered + selectionSpan
    } else {
        // FULLY BULLETED: remove bullets only within the selection.
        val result = mutableListOf<RichTextSpan>()
        for (span in currentSpans) {
            if (span is com.boox.day.data.model.BulletSpan) {
                if (span.end <= selStart || span.start >= selEnd) {
                    result += span
                } else {
                    if (span.start < selStart) {
                        result += span.copy(end = selStart)
                    }
                    if (span.end > selEnd) {
                        result += span.copy(start = selEnd)
                    }
                }
            } else {
                result += span
            }
        }
        result
    }
}
