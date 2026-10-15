package com.boox.day.editor.drawing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.toRect
import androidx.core.graphics.withClip
import androidx.core.net.toUri
import com.boox.day.data.datastore.GlobalAppSettings
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.boox.day.data.db.Attachment
import com.boox.day.data.db.Image
import com.boox.day.data.db.TextAnnotation
import com.boox.day.data.db.getBackgroundType
import com.boox.day.data.model.BackgroundType
import com.boox.day.editor.DrawCanvas
import com.boox.day.editor.PageView
import com.boox.day.editor.utils.getDominantRowIndex
import com.boox.day.editor.utils.imageBounds
import com.boox.day.editor.utils.plus
import com.boox.day.editor.utils.strokeBounds
import com.boox.day.io.uriToBitmap
import com.boox.day.ui.showHint
import com.boox.day.utils.TaggedLog

private val pageDrawingLog = TaggedLog("PageDrawingLog")

// LRU cache for image bitmaps - uses 1/8th of available memory
private val imageBitmapCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(
    ((Runtime.getRuntime().maxMemory() / 1024) / 8).toInt()
) {
    override fun sizeOf(key: String, bitmap: Bitmap): Int {
        return bitmap.byteCount / 1024
    }
}

/**
 * Clears the image bitmap cache. Call when switching pages or on low memory.
 */
fun clearImageCache() {
    imageBitmapCache.evictAll()
    pageDrawingLog.d("Image bitmap cache cleared")
}

/**
 * Removes a specific image from the cache (e.g., when image is deleted).
 */
fun removeImageFromCache(uri: String) {
    imageBitmapCache.remove(uri)
}


/**
 * Draws an image onto the provided Canvas at a specified location and size, using its URI.
 *
 * This function performs the following steps:
 * 1. Converts the URI of the image into a `Bitmap` object.
 * 2. Converts the `ImageBitmap` to a software-backed `Bitmap` for compatibility.
 * 3. Clears the value of `DrawCanvas.addImageByUri` to null.
 * 4. Draws the specified bitmap onto the provided Canvas within a destination rectangle
 *    defined by the `Image` object coordinates (`x`, `y`) and its dimensions (`width`, `height`),
 *    adjusted by the `offset`.
 * 5. Logs the success or failure of the operation.
 *
 * @param context The context used to retrieve the image from the URI.
 * @param canvas The Canvas object where the image will be drawn.
 * @param image The `Image` object containing details about the image (URI, position, and size).
 * @param offset The `IntOffset` used to adjust the drawing position relative to the Canvas.
 */
fun drawImage(context: Context, canvas: Canvas, image: Image, offset: Offset) {
    if (image.uri.isNullOrEmpty())
        return

    val uri = image.uri

    // Check cache first
    var softwareBitmap = imageBitmapCache.get(uri)?.takeIf { !it.isRecycled }

    if (softwareBitmap == null) {
        // Load from URI and cache
        val imageBitmap = uriToBitmap(context, uri.toUri())?.asImageBitmap()
        if (imageBitmap != null) {
            softwareBitmap = imageBitmap.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, true)
            imageBitmapCache.put(uri, softwareBitmap)
            pageDrawingLog.d("Image cached: $uri")
        } else {
            pageDrawingLog.e("Could not get image from: $uri")
            return
        }
    }

    DrawCanvas.addImageByUri.value = null

    val rectOnImage = Rect(0, 0, softwareBitmap.width, softwareBitmap.height)
    val rectOnCanvas =
        Rect(image.x, image.y, image.x + image.width, image.y + image.height) + offset
    // Draw the bitmap on the canvas
    canvas.drawBitmap(softwareBitmap, rectOnImage, rectOnCanvas, null)

    pageDrawingLog.i("Image drawn successfully!")
}


/**
 * Gets the drawable resource ID for a given file type.
 */
private fun getFileIconResourceId(fileType: String): Int {
    return when (fileType.lowercase()) {
        // Documents
        "pdf" -> com.boox.day.R.drawable.ic_file_pdf
        "doc", "docx", "odt", "rtf" -> com.boox.day.R.drawable.ic_file_document
        "txt", "md", "log", "json", "xml", "html", "css", "js", "kt", "java", "py" -> com.boox.day.R.drawable.ic_file_text

        // Audio
        "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma" -> com.boox.day.R.drawable.ic_file_audio

        // Ebooks
        "epub", "mobi", "azw3", "djvu", "cbz", "cbr" -> com.boox.day.R.drawable.ic_file_text

        // Default fallback
        else -> com.boox.day.R.drawable.ic_file_generic
    }
}

/**
 * Draws an attachment chip onto the canvas.
 * Attachments are rendered as clickable chips with a file-type icon and display name.
 *
 * @param canvas The Canvas object where the attachment will be drawn.
 * @param attachment The Attachment object containing details (position, display name, file type).
 * @param offset The offset used to adjust the drawing position relative to scroll.
 */
fun drawAttachment(canvas: Canvas, attachment: Attachment, offset: Offset, context: Context? = null) {
    val chipX = attachment.x + offset.x
    val chipY = attachment.y + offset.y
    val chipWidth = attachment.width.toFloat()
    val chipHeight = attachment.height.toFloat()

    // Colors
    val bgColor = Color.WHITE
    val borderColor = Color.parseColor("#999999") // Medium gray border
    val iconBgColor = Color.parseColor("#F5F5F5") // Light gray icon background
    val textColor = Color.parseColor("#333333") // Dark gray text
    val badgeColor = Color.parseColor("#666666") // Medium gray for badge

    // Background paint (white fill)
    val bgPaint = Paint().apply {
        color = bgColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Border paint (subtle gray stroke)
    val borderPaint = Paint().apply {
        color = borderColor
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    // Icon background paint
    val iconBgPaint = Paint().apply {
        color = iconBgColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Text paint for display name
    val textPaint = Paint().apply {
        color = textColor
        textSize = 32f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    // Badge text paint (file extension)
    val badgePaint = Paint().apply {
        color = badgeColor
        textSize = 22f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val rect = RectF(chipX, chipY, chipX + chipWidth, chipY + chipHeight)
    val cornerRadius = 16f

    // Draw chip background and border
    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

    // Draw icon area background (left side)
    val iconAreaWidth = 85f
    val iconAreaRect = RectF(chipX, chipY, chipX + iconAreaWidth, chipY + chipHeight)
    val iconAreaPath = android.graphics.Path().apply {
        addRoundRect(iconAreaRect, floatArrayOf(cornerRadius, cornerRadius, 0f, 0f, 0f, 0f, cornerRadius, cornerRadius), android.graphics.Path.Direction.CW)
    }
    canvas.drawPath(iconAreaPath, iconBgPaint)

    // Draw vertical separator line
    val separatorPaint = Paint().apply {
        color = borderColor
        strokeWidth = 1f
    }
    canvas.drawLine(chipX + iconAreaWidth, chipY + 8f, chipX + iconAreaWidth, chipY + chipHeight - 8f, separatorPaint)

    // Draw file type icon
    val iconSize = 50
    val iconLeft = (chipX + (iconAreaWidth - iconSize) / 2f).toInt()
    val iconTop = (chipY + (chipHeight - iconSize) / 2f).toInt()

    if (context != null) {
        try {
            val iconResId = getFileIconResourceId(attachment.fileType)
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, iconResId)
            drawable?.let {
                it.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                it.setTint(Color.parseColor("#444444"))
                it.draw(canvas)
            }
        } catch (e: Exception) {
            // Fallback to generic icon
            try {
                val drawable = androidx.core.content.ContextCompat.getDrawable(context, com.boox.day.R.drawable.ic_file_generic)
                drawable?.let {
                    it.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                    it.setTint(Color.parseColor("#444444"))
                    it.draw(canvas)
                }
            } catch (e2: Exception) {
                // Final fallback - draw nothing
            }
        }
    }

    // Text area calculations
    val textStartX = chipX + iconAreaWidth + 14f
    val textAreaWidth = (chipWidth - iconAreaWidth - 28f) * 1.1f

    // Draw display name (truncated if needed)
    val displayText = truncateTextForWidth(attachment.displayName, textPaint, textAreaWidth)
    val textY = chipY + (chipHeight / 2f) + (textPaint.textSize / 3f) - 6f
    canvas.drawText(displayText, textStartX, textY, textPaint)

    // Draw file type badge in top-right corner
    val fileExt = attachment.fileType.uppercase()
    if (fileExt.isNotEmpty() && fileExt != "FILE") {
        val badgeText = if (fileExt.length > 4) fileExt.take(4) else fileExt
        val badgeWidth = badgePaint.measureText(badgeText) + 16f
        val badgeHeight = 24f
        val badgeX = chipX + chipWidth - badgeWidth - 10f
        val badgeY = chipY + 8f

        // Badge background
        val badgeBgPaint = Paint().apply {
            color = Color.parseColor("#E8E8E8")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val badgeRect = RectF(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight)
        canvas.drawRoundRect(badgeRect, 6f, 6f, badgeBgPaint)

        // Badge text
        val badgeTextX = badgeX + 8f
        val badgeTextY = badgeY + badgeHeight - 6f
        canvas.drawText(badgeText, badgeTextX, badgeTextY, badgePaint)
    }
}

/**
 * Truncates text to fit within a specified width, adding ellipsis if needed.
 */
private fun truncateTextForWidth(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) {
        return text
    }

    val ellipsis = "..."
    val ellipsisWidth = paint.measureText(ellipsis)
    val availableWidth = maxWidth - ellipsisWidth

    var truncated = text
    while (truncated.isNotEmpty() && paint.measureText(truncated) > availableWidth) {
        truncated = truncated.dropLast(1)
    }

    return truncated + ellipsis
}

/**
 * Returns the bounds of an attachment as a RectF.
 */
fun attachmentBounds(attachment: Attachment): RectF {
    return RectF(
        attachment.x.toFloat(),
        attachment.y.toFloat(),
        (attachment.x + attachment.width).toFloat(),
        (attachment.y + attachment.height).toFloat()
    )
}


fun drawDebugRectWithLabels(
    canvas: Canvas,
    rect: RectF,
    rectColor: Int = Color.RED,
    labelColor: Int = Color.BLUE
) {
    val rectPaint = Paint().apply {
        color = rectColor
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    pageDrawingLog.w("Drawing debug rect $rect")
    // Draw rectangle outline
    canvas.drawRect(rect, rectPaint)

    // Setup label paint
    val labelPaint = Paint().apply {
        color = labelColor
        textAlign = Paint.Align.LEFT
        textSize = 40f
        isAntiAlias = true
    }

    // Helper to format text
    fun format(x: Float, y: Float) = "(${x.toInt()}, ${y.toInt()})"

    val topLeftLabel = format(rect.left, rect.top)
    val topRightLabel = format(rect.right, rect.top)
    val bottomLeftLabel = format(rect.left, rect.bottom)
    val bottomRightLabel = format(rect.right, rect.bottom)

    val topRightTextWidth = labelPaint.measureText(topRightLabel)
    val bottomRightTextWidth = labelPaint.measureText(bottomRightLabel)

    // Draw coordinate labels at corners
    canvas.drawText(topLeftLabel, rect.left + 8f, rect.top + labelPaint.textSize, labelPaint)
    canvas.drawText(
        topRightLabel,
        rect.right - topRightTextWidth - 8f,
        rect.top + labelPaint.textSize,
        labelPaint
    )
    canvas.drawText(bottomLeftLabel, rect.left + 8f, rect.bottom - 8f, labelPaint)
    canvas.drawText(
        bottomRightLabel,
        rect.right - bottomRightTextWidth - 8f,
        rect.bottom - 8f,
        labelPaint
    )
}


/**
 * Draw a single text annotation on the canvas.
 * Rendering priority:
 * 1. If spans field is not null -> use RichTextConverter.toSpanned()
 * 2. Else if sourceFormat is markdown -> use MarkdownHelper (legacy)
 * 3. Else -> plain text
 */
fun drawTextAnnotation(canvas: Canvas, annotation: TextAnnotation, scroll: Offset) {
    val textPaint = TextPaint().apply {
        isAntiAlias = true
        textSize = annotation.fontSize
        color = annotation.textColor
        typeface = getTypeface(annotation.fontFamily, annotation.fontWeight, annotation.fontStyle)
    }

    val screenX = annotation.x - scroll.x
    val screenY = annotation.y - scroll.y
    val textWidth = (annotation.width - annotation.padding * 2).toInt()

    if (textWidth <= 0 || annotation.content.isEmpty()) return

    // Draw background if not transparent
    if (annotation.backgroundColor != Color.TRANSPARENT && annotation.backgroundColor != 0) {
        val bgPaint = Paint().apply {
            color = annotation.backgroundColor
            style = Paint.Style.FILL
        }
        canvas.drawRect(
            screenX, screenY,
            screenX + annotation.width, screenY + annotation.height,
            bgPaint
        )
    }

    // Draw text with appropriate formatting
    val alignment = when (annotation.textAlign) {
        "center" -> Layout.Alignment.ALIGN_CENTER
        "right" -> Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_NORMAL
    }

    // Rendering priority:
    // 1. Use stored spans if available (new rich text system)
    // 2. Fall back to Markwon for markdown sourceFormat (legacy)
    // 3. Plain text otherwise
    val textContent: CharSequence = when {
        !annotation.spans.isNullOrEmpty() -> {
            // New rich text system - use stored spans
            RichTextConverter.jsonToSpanned(annotation.content, annotation.spans, annotation.fontSize)
        }
        annotation.sourceFormat == "markdown" && MarkdownHelper.isInitialized() -> {
            // Legacy markdown rendering
            MarkdownHelper.toSpanned(annotation.content)
        }
        else -> {
            // Plain text
            annotation.content
        }
    }

    val staticLayout = StaticLayout.Builder
        .obtain(textContent, 0, textContent.length, textPaint, textWidth)
        .setAlignment(alignment)
        .setIncludePad(true)
        .build()

    canvas.save()
    canvas.translate(screenX + annotation.padding, screenY + annotation.padding)
    staticLayout.draw(canvas)
    canvas.restore()
}

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

fun drawOnCanvasFromPage(
    page: PageView,
    canvas: Canvas,
    canvasClipBounds: Rect,
    pageArea: Rect,
    ignoredStrokeIds: List<String> = listOf(),
    ignoredImageIds: List<String> = listOf(),
    ignoredTextAnnotationIds: List<String> = listOf(),
    ignoredAttachmentIds: List<String> = listOf(),
) {
    val zoomLevel = page.zoomLevel.value
    val backgroundType = page.pageFromDb?.getBackgroundType() ?: BackgroundType.Native
    val background = page.pageFromDb?.background ?: "blank"
    pageDrawingLog.d("drawOnCanvasFromPage, zoom: $zoomLevel, background: $background, type: $backgroundType")

    // Canvas is scaled, it will scale page area.
    canvas.withClip(canvasClipBounds) {
        drawColor(Color.WHITE)

        drawBg(page.context, this, backgroundType, background, page.scroll, zoomLevel, page)
        if (GlobalAppSettings.current.debugMode) {
            drawDebugRectWithLabels(canvas, RectF(canvasClipBounds), Color.BLACK)
        }
        try {
            page.images.forEach { image ->
                if (ignoredImageIds.contains(image.id)) return@forEach
                pageDrawingLog.i("PageView.kt: drawing image!")
                val bounds = imageBounds(image)
                // if stroke is not inside page section
                if (!bounds.toRect().intersect(pageArea)) return@forEach
                drawImage(page.context, this, image, -page.scroll)

            }
        } catch (e: Exception) {
            pageDrawingLog.e("PageView.kt(${page.currentPageId}): Drawing images failed: ${e.message}", e)

            val errorMessage = if (e.message?.contains("does not have permission") == true) {
                "Permission error: Unable to access image."
            } else {
                "Failed to load images."
            }
            showHint(errorMessage, page.coroutineScope)
        }

        // Draw attachments (file link chips)
        try {
            page.attachments.forEach { attachment ->
                if (ignoredAttachmentIds.contains(attachment.id)) return@forEach
                val bounds = attachmentBounds(attachment)
                // if attachment is not inside page section
                if (!bounds.toRect().intersect(pageArea)) return@forEach
                drawAttachment(this, attachment, -page.scroll, page.context)
            }
        } catch (e: Exception) {
            pageDrawingLog.e("PageView.kt: Drawing attachments failed: ${e.message}", e)
        }

        try {
            val completedRows = page.completedRowIndices
            val completedColor: Int? = if (completedRows.isNotEmpty()) {
                0xFFB8B8B8.toInt()  // EInkTheme.TasksTab.completedStrokeColor (Colors.lightGray)
            } else null

            page.strokes.forEach { stroke ->
                if (ignoredStrokeIds.contains(stroke.id)) return@forEach
                val bounds = strokeBounds(stroke)
                // if stroke is not inside page section
                if (!bounds.toRect().intersect(pageArea)) return@forEach

                val override = if (completedColor != null) {
                    val rowIdx = getDominantRowIndex(stroke, page.viewHeight.toFloat())
                    if (rowIdx in completedRows) completedColor else null
                } else null

                drawStroke(this, stroke, -page.scroll, override)
            }
        } catch (e: Exception) {
            pageDrawingLog.e("PageView.kt: Drawing strokes failed: ${e.message}", e)
            showHint("Error drawing strokes", page.coroutineScope)
        }

        // Draw text annotations
        try {
            page.textAnnotations.forEach { annotation ->
                if (ignoredTextAnnotationIds.contains(annotation.id)) return@forEach
                val bounds = RectF(annotation.x, annotation.y, annotation.x + annotation.width, annotation.y + annotation.height)
                // Check if annotation is in visible page area
                if (!bounds.toRect().intersect(pageArea)) return@forEach
                drawTextAnnotation(this, annotation, page.scroll)
            }
        } catch (e: Exception) {
            pageDrawingLog.e("PageView.kt: Drawing text annotations failed: ${e.message}", e)
        }
    }
}
