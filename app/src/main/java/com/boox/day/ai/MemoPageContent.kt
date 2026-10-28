package com.boox.day.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.createBitmap
import com.boox.day.SCREEN_WIDTH
import com.boox.day.data.AppRepository
import com.boox.day.data.PageDataManager
import com.boox.day.data.PagePersistenceWriter
import com.boox.day.data.db.Image
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.TextAnnotation
import com.boox.day.data.db.isUndecodable
import com.boox.day.editor.drawing.drawImage
import com.boox.day.editor.drawing.drawStroke
import com.boox.day.editor.drawing.drawTextAnnotation
import com.boox.day.utils.TaggedLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Everything on a memo page, for the AI features (Oct 2026). A page open this session comes from
 * memory (it may hold strokes not saved yet); any other page from the database, after the page
 * writes queued so far. Never the canvas image or a saved preview: both hold only the part of a
 * page that was on screen (ink written low in full screen, or moved out of view, was left out),
 * and a page never shown had no preview at all.
 */
object MemoPageContent {
    private val log = TaggedLog("MemoPageContent")

    /** White space around the content in [render]. */
    private const val PADDING = 40f

    /** A picture larger than this is drawn smaller: vision models shrink big images anyway. */
    private const val MAX_PIXELS = 8_000_000f

    data class Page(
        val pageId: String,
        val strokes: List<Stroke>,
        val images: List<Image>,
        val textAnnotations: List<TextAnnotation>
    ) {
        val isEmpty: Boolean get() = strokes.isEmpty() && images.isEmpty() && textAnnotations.isEmpty()
    }

    suspend fun load(context: Context, pageId: String): Page = withContext(Dispatchers.IO) {
        if (PageDataManager.isPageLoaded(pageId)) {
            return@withContext Page(
                pageId,
                PageDataManager.getStrokes(pageId),
                PageDataManager.getImages(pageId).toList(),
                PageDataManager.getTextAnnotations(pageId).toList()
            )
        }
        // The writer runs page writes in order: once this empty one has run, every earlier one has
        PagePersistenceWriter.submit("read $pageId for AI") {}.join()
        try {
            val repository = AppRepository(context)
            Page(
                pageId,
                // Strokes whose points can't be decoded are left out, as when the page is shown
                repository.pageRepository.getWithStrokeByIdSuspend(pageId).strokes.filter { !it.isUndecodable },
                repository.pageRepository.getWithImageById(pageId).images,
                repository.textAnnotationRepository.getByPageId(pageId)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w("Memo page $pageId could not be read: ${e.message}")
            Page(pageId, emptyList(), emptyList(), emptyList())
        }
    }

    /** Every page of [pageIds], in order. */
    suspend fun loadAll(context: Context, pageIds: List<String>): List<Page> = pageIds.map { load(context, it) }

    /** Page [pageId] as one whole picture, or null when there is nothing on it. */
    suspend fun renderWholePage(context: Context, pageId: String): Bitmap? {
        val page = load(context, pageId)
        if (page.isEmpty) return null
        return withContext(Dispatchers.Default) { render(context, page) }
    }

    /**
     * The whole page as one picture: from the page corner (or content above or left of it) to its
     * lowest and rightmost content, at least a screen wide, on white. Images, ink and text boxes as
     * the canvas draws them, markers in their own grey (the e-ink dots are for the panel only).
     * Off the main thread.
     */
    fun render(context: Context, page: Page): Bitmap {
        val content = contentBounds(page) ?: RectF(0f, 0f, 1f, 1f)
        val left = min(0f, content.left - PADDING)
        val top = min(0f, content.top - PADDING)
        val right = max(SCREEN_WIDTH.toFloat(), content.right + PADDING)
        val bottom = content.bottom + PADDING
        val scale = min(1f, sqrt(MAX_PIXELS / ((right - left) * (bottom - top))))
        val bitmap = createBitmap(
            ceil((right - left) * scale).toInt().coerceAtLeast(1),
            ceil((bottom - top) * scale).toInt().coerceAtLeast(1)
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.scale(scale, scale)
        // Content above or left of the page corner moves into the picture
        val shift = Offset(-left, -top)
        page.images.forEach { drawImage(context, canvas, it, shift) }
        page.strokes.forEach { drawStroke(canvas, it, shift) }
        page.textAnnotations.forEach { drawTextAnnotation(canvas, it, -shift) }  // takes the view's corner
        log.d("Memo page ${page.pageId} as ${bitmap.width}x${bitmap.height} (${page.strokes.size} strokes, ${page.textAnnotations.size} text boxes)")
        return bitmap
    }

    private fun contentBounds(page: Page): RectF? {
        var bounds: RectF? = null
        fun add(left: Float, top: Float, right: Float, bottom: Float) {
            bounds?.union(left, top, right, bottom) ?: run { bounds = RectF(left, top, right, bottom) }
        }
        page.strokes.forEach { add(it.left, it.top, it.right, it.bottom) }
        page.images.forEach { add(it.x.toFloat(), it.y.toFloat(), (it.x + it.width).toFloat(), (it.y + it.height).toFloat()) }
        page.textAnnotations.forEach { add(it.x, it.y, it.x + it.width, it.y + it.height) }
        return bounds
    }
}
