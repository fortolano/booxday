package com.boox.day.actionitems

import android.content.Context
import com.boox.day.data.PageDataManager
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokeRepository
import com.boox.day.data.db.TextAnnotation
import com.boox.day.data.db.TextAnnotationRepository
import com.boox.day.ocr.DigitalInkService
import com.boox.day.sync.EventMemoService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * A memo page's text, read on the tablet only (ML Kit Digital Ink): nothing goes to Cloud Vision or
 * an AI. Used by action items.
 */
object MemoPageText {

    /**
     * A memo page's lines, top to bottom: its handwriting recognized line by line (when [ink] is given)
     * and its typed text boxes, without the meeting header BooxDay stamps. The page's latest content when
     * it is open on the canvas, else what's saved.
     */
    suspend fun pageLines(context: Context, ink: DigitalInkService?, pageId: String): List<String> {
        val app = context.applicationContext
        val (strokes, boxes) = withContext(Dispatchers.IO) {
            if (PageDataManager.isPageLoaded(pageId)) {
                PageDataManager.getStrokes(pageId) to PageDataManager.getTextAnnotations(pageId)
            } else {
                StrokeRepository(app).getAllForPage(pageId) to TextAnnotationRepository(app).getByPageId(pageId)
            }
        }
        val found = mutableListOf<Pair<Float, String>>()  // (top on the page, text)
        if (ink != null && strokes.isNotEmpty()) {
            val written = lines(strokes)
            val texts = coroutineScope {
                written.map { line -> async(Dispatchers.Default) { runCatching { ink.recognizeStrokes(line).text.trim() }.getOrDefault("") } }.awaitAll()
            }
            written.zip(texts).forEach { (line, text) -> if (text.isNotEmpty()) found += line.minOf { it.top } to text }
        }
        boxes.filter { it.sourceType != EventMemoService.HEADER_SOURCE }.forEach { box: TextAnnotation ->
            box.content.lines().forEachIndexed { i, text -> if (text.isNotBlank()) found += (box.y + i) to text.trim() }
        }
        return found.sortedBy { it.first }.map { it.second }
    }

    /**
     * Groups strokes into written lines: a stroke joins the line its vertical middle falls in, top to
     * bottom; inside a line, strokes keep writing order (left to right by time).
     */
    internal fun lines(strokes: List<Stroke>): List<List<Stroke>> {
        val lines = mutableListOf<MutableList<Stroke>>()
        val bounds = mutableListOf<FloatArray>()  // [top, bottom] of each line
        for (stroke in strokes.sortedBy { (it.top + it.bottom) / 2f }) {
            val middle = (stroke.top + stroke.bottom) / 2f
            val index = bounds.indexOfLast { middle >= it[0] && middle <= it[1] }
            if (index >= 0) {
                lines[index].add(stroke)
                bounds[index][0] = minOf(bounds[index][0], stroke.top)
                bounds[index][1] = maxOf(bounds[index][1], stroke.bottom)
            } else {
                lines.add(mutableListOf(stroke))
                bounds.add(floatArrayOf(stroke.top, stroke.bottom))
            }
        }
        return lines.map { line -> line.sortedBy { it.createdAt } }
    }
}
