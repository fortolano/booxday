package com.boox.day.editor.drawing

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.boox.day.data.db.TextAnnotation
import com.boox.day.editor.state.TextBoxState
import android.text.Spanned

/**
 * Renders text annotations on the canvas.
 * Handles text rendering, selection borders, and resize handles.
 * Optimized for e-ink display with black/white styling.
 */
class TextBoxRenderer {

    private val textPaint = TextPaint().apply {
        isAntiAlias = true
    }

    private val selectionPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.BLACK
    }

    private val handlePaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }

    private val handleOutlinePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.WHITE
    }

    private val handleSize = 14f

    /**
     * Draw all text annotations for a page
     */
    fun drawAllTextBoxes(
        canvas: Canvas,
        annotations: List<TextAnnotation>,
        textBoxState: TextBoxState,
        scale: Float = 1f,
        offsetX: Float = 0f,
        offsetY: Float = 0f
    ) {
        annotations.forEach { annotation ->
            drawTextBox(canvas, annotation, textBoxState, scale, offsetX, offsetY)
        }
    }

    /**
     * Draw a single text annotation
     */
    fun drawTextBox(
        canvas: Canvas,
        annotation: TextAnnotation,
        textBoxState: TextBoxState,
        scale: Float = 1f,
        offsetX: Float = 0f,
        offsetY: Float = 0f
    ) {
        val isSelected = textBoxState.isSelected(annotation)
        val isEditing = textBoxState.isEditing(annotation)

        // CRITICAL: Skip rendering if selected - TextBoxOverlay will render it instead
        // This is IDENTICAL to how lasso selection works - selected content is rendered
        // in the overlay (SelectedBitmap), not on the main canvas
        if (isSelected) return

        // Calculate screen coordinates
        val screenX = (annotation.x - offsetX) * scale
        val screenY = (annotation.y - offsetY) * scale
        val screenWidth = annotation.width * scale
        val screenHeight = annotation.height * scale

        val rect = RectF(screenX, screenY, screenX + screenWidth, screenY + screenHeight)

        // Draw background if not transparent
        if (annotation.backgroundColor != Color.TRANSPARENT && annotation.backgroundColor != 0) {
            val bgPaint = Paint().apply {
                color = annotation.backgroundColor
                style = Paint.Style.FILL
            }
            canvas.drawRect(rect, bgPaint)
        }

        // Configure text paint
        textPaint.apply {
            textSize = annotation.fontSize * scale
            color = annotation.textColor
            typeface = getTypeface(annotation.fontFamily, annotation.fontWeight, annotation.fontStyle)
        }

        // Draw text with padding
        val padding = annotation.padding * scale
        val textWidth = (screenWidth - padding * 2).toInt()

        if (textWidth > 0 && annotation.content.isNotEmpty()) {
            val alignment = when (annotation.textAlign) {
                "center" -> Layout.Alignment.ALIGN_CENTER
                "right" -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL
            }

            // Rendering priority:
            // 1. Use stored spans if available (new rich text system)
            // 2. Fall back to Markwon for markdown sourceFormat (legacy)
            // 3. Plain text otherwise
            val spannedText: CharSequence = when {
                !annotation.spans.isNullOrEmpty() -> {
                    Log.d("TextBoxRenderer", "Using RichTextConverter with stored spans")
                    RichTextConverter.jsonToSpanned(annotation.content, annotation.spans, annotation.fontSize * scale)
                }
                annotation.sourceFormat == "markdown" && MarkdownHelper.isInitialized() -> {
                    Log.d("TextBoxRenderer", "Using Markwon for markdown rendering (legacy)")
                    MarkdownHelper.toSpanned(annotation.content)
                }
                else -> {
                    Log.d("TextBoxRenderer", "Using plain text")
                    annotation.content
                }
            }

            val staticLayout = StaticLayout.Builder
                .obtain(spannedText, 0, spannedText.length, textPaint, textWidth)
                .setAlignment(alignment)
                .setIncludePad(true)
                .build()

            canvas.save()
            canvas.translate(screenX + padding, screenY + padding)
            staticLayout.draw(canvas)
            canvas.restore()
        }

        // Draw selection border and handles if selected
        if (isSelected && !isEditing) {
            drawSelectionBorder(canvas, rect)
            drawResizeHandles(canvas, rect)
        }

        // Solid border when editing
        if (isEditing) {
            selectionPaint.pathEffect = null
            selectionPaint.strokeWidth = 2f
            canvas.drawRect(rect, selectionPaint)
        }
    }

    /**
     * Draw dashed selection border
     */
    private fun drawSelectionBorder(canvas: Canvas, rect: RectF) {
        selectionPaint.pathEffect = DashPathEffect(floatArrayOf(8f, 4f), 0f)
        selectionPaint.strokeWidth = 2f
        canvas.drawRect(rect, selectionPaint)
        selectionPaint.pathEffect = null
    }

    /**
     * Draw resize handles at corners and edges
     */
    private fun drawResizeHandles(canvas: Canvas, rect: RectF) {
        // Corner handles
        drawHandle(canvas, rect.left, rect.top)
        drawHandle(canvas, rect.right, rect.top)
        drawHandle(canvas, rect.left, rect.bottom)
        drawHandle(canvas, rect.right, rect.bottom)

        // Edge midpoint handles
        drawHandle(canvas, rect.centerX(), rect.top)
        drawHandle(canvas, rect.centerX(), rect.bottom)
        drawHandle(canvas, rect.left, rect.centerY())
        drawHandle(canvas, rect.right, rect.centerY())
    }

    /**
     * Draw a single resize handle
     */
    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        val halfSize = handleSize / 2
        val handleRect = RectF(x - halfSize, y - halfSize, x + halfSize, y + halfSize)

        // Draw filled handle
        canvas.drawRect(handleRect, handlePaint)

        // Draw outline for contrast
        canvas.drawRect(handleRect, handleOutlinePaint)
    }

    /**
     * Get appropriate typeface based on font settings
     */
    private fun getTypeface(family: String, weight: Int, style: String): Typeface {
        val baseTypeface = when (family.lowercase()) {
            "serif" -> Typeface.SERIF
            "monospace" -> Typeface.MONOSPACE
            else -> Typeface.SANS_SERIF
        }

        val typefaceStyle = when {
            weight >= 700 && style == "italic" -> Typeface.BOLD_ITALIC
            weight >= 700 -> Typeface.BOLD
            style == "italic" -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }

        return Typeface.create(baseTypeface, typefaceStyle)
    }

    /**
     * Check if a point is inside a text box (in page coordinates)
     */
    fun hitTest(annotation: TextAnnotation, x: Float, y: Float): Boolean {
        return x >= annotation.x && x <= annotation.x + annotation.width &&
               y >= annotation.y && y <= annotation.y + annotation.height
    }

    /**
     * Find which text annotation (if any) is at a given point
     */
    fun findTextBoxAt(
        annotations: List<TextAnnotation>,
        x: Float,
        y: Float
    ): TextAnnotation? {
        // Check in reverse order (top-most first)
        return annotations.findLast { hitTest(it, x, y) }
    }

    /**
     * Calculate required height for text content
     */
    fun calculateTextHeight(
        content: String,
        width: Float,
        fontSize: Float,
        fontFamily: String,
        fontWeight: Int,
        fontStyle: String,
        padding: Float
    ): Float {
        if (content.isEmpty() || width <= padding * 2) return padding * 2 + fontSize

        textPaint.textSize = fontSize
        textPaint.typeface = getTypeface(fontFamily, fontWeight, fontStyle)

        // Convert markdown to Spanned for accurate height calculation
        val spannedText: CharSequence = if (MarkdownHelper.isInitialized()) {
            MarkdownHelper.toSpanned(content)
        } else {
            content
        }

        val textWidth = (width - padding * 2).toInt()
        val staticLayout = StaticLayout.Builder
            .obtain(spannedText, 0, spannedText.length, textPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            .build()

        return staticLayout.height + padding * 2
    }
}
