package com.boox.day.ocr

import android.content.Context
import android.util.Log
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokeDao
import com.boox.day.editor.utils.calculateRowBounds
import com.boox.day.editor.utils.groupStrokesByRow
import com.boox.day.editor.utils.tasksTopOffset
import com.google.mlkit.vision.digitalink.recognition.Ink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Production Digital Ink Recognition service for Tasks pages.
 *
 * Converts stroke data to text using ML Kit Digital Ink Recognition.
 * Designed for the Tasks page layout (10 rows, control column on left).
 *
 * Key features:
 * - Parallel recognition with fault isolation (supervisorScope)
 * - Non-blocking, fire-and-forget API
 * - Integrated text normalization (spell correction, word segmentation)
 *
 * Thread Safety: All public methods are thread-safe.
 */
class DigitalInkService(
    private val context: Context,
    private val strokeDao: StrokeDao,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "DigitalInkService"

        private const val ROWS_PER_PAGE = 10

        /**
         * Create an instance using application context.
         */
        fun create(context: Context, scope: CoroutineScope): DigitalInkService {
            val db = AppDatabase.getDatabase(context)
            return DigitalInkService(context, db.strokeDao(), scope)
        }
    }

    /**
     * Result for a single task slot.
     */
    data class SlotResult(
        val slotIndex: Int,
        val text: String,
        val confidence: Float,
        val isEmpty: Boolean
    )

    /**
     * Extract text from a task page. Fire-and-forget.
     *
     * @param pageId Task page UUID
     * @param canvasHeight Actual canvas height in pixels (from DrawCanvas.surfaceSize or GlobalAppSettings)
     * @param controlColumnPx Control column width in pixels (strokes with right < this are excluded)
     * @param onComplete Callback with results (called on Default dispatcher)
     */
    fun extractPageAsync(
        pageId: String,
        canvasHeight: Float,
        controlColumnPx: Float,
        topOffset: Float = tasksTopOffset(),
        onComplete: ((List<SlotResult>) -> Unit)? = null
    ) {
        scope.launch(Dispatchers.Default) {
            val results = extractPage(pageId, canvasHeight, controlColumnPx, topOffset)
            onComplete?.invoke(results)
        }
    }

    /**
     * Extract text from a task page (suspending version).
     *
     * @param pageId Task page UUID
     * @param canvasHeight Actual canvas height in pixels (from DrawCanvas.surfaceSize or GlobalAppSettings)
     * @param controlColumnPx Control column width in pixels (strokes with right < this are excluded)
     * @param topOffset Top offset in pixels matching TASKS_TOOLBAR_OFFSET.toPx()
     * @return List of SlotResult for each of the 10 slots
     */
    suspend fun extractPage(pageId: String, canvasHeight: Float, controlColumnPx: Float, topOffset: Float = tasksTopOffset()): List<SlotResult> {
        val startTime = System.currentTimeMillis()

        if (!isReady()) {
            Log.w(TAG, "Model not ready, skipping extraction for $pageId")
            return emptySlotResults()
        }

        // Load strokes from database
        val allStrokes = withContext(Dispatchers.IO) {
            strokeDao.getAllForPage(pageId)
        }

        if (allStrokes.isEmpty()) {
            Log.d(TAG, "No strokes for page $pageId")
            return emptySlotResults()
        }

        Log.d(TAG, "Extracting page $pageId: ${allStrokes.size} strokes, canvasHeight=$canvasHeight")

        // Group strokes by slot using unified algorithm (dominant vertical overlap,
        // matching visual overlay row boundaries exactly via calculateRowBounds)
        val strokesBySlot = groupStrokesByRow(allStrokes, canvasHeight, controlColumnPx, topOffset = topOffset)

        // Recognize ALL slots in PARALLEL with fault isolation
        // supervisorScope ensures one slot failure doesn't cancel others
        val results = supervisorScope {
            (0 until ROWS_PER_PAGE).map { slotIndex ->
                async(Dispatchers.Default) {
                    try {
                        recognizeSlot(
                            strokes = strokesBySlot[slotIndex] ?: emptyList(),
                            slotIndex = slotIndex,
                            controlColumnPx = controlColumnPx,
                            canvasHeight = canvasHeight,
                            topOffset = topOffset
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Slot $slotIndex recognition failed: ${e.message}")
                        SlotResult(slotIndex, "", 0f, true)  // Return empty on failure
                    }
                }
            }.map { it.await() }
        }

        val elapsed = System.currentTimeMillis() - startTime
        val nonEmptyCount = results.count { !it.isEmpty }
        Log.i(TAG, "Extraction complete for $pageId: $nonEmptyCount/${ROWS_PER_PAGE} slots with text (${elapsed}ms)")

        return results
    }

    /**
     * Check if service is ready (model downloaded).
     */
    suspend fun isReady(): Boolean = DigitalInkModelManager.isModelReady()

    // ═══════════════════════════════════════════════════════════════════════════
    // Generic Stroke Recognition (for lasso selection, etc.)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Result of generic stroke recognition.
     */
    data class RecognitionResult(
        val text: String,
        val confidence: Float,
        val isEmpty: Boolean
    )

    /**
     * Recognize text from arbitrary strokes (not slot-based).
     *
     * Use this for lasso selections, free-form handwriting, etc.
     * Coordinates are normalized to the strokes' bounding box origin.
     *
     * @param strokes List of strokes to recognize
     * @return RecognitionResult with recognized text
     */
    suspend fun recognizeStrokes(strokes: List<Stroke>): RecognitionResult {
        if (strokes.isEmpty()) {
            Log.d(TAG, "recognizeStrokes: No strokes provided")
            return RecognitionResult("", 0f, true)
        }

        if (!isReady()) {
            Log.w(TAG, "recognizeStrokes: Model not ready")
            return RecognitionResult("", 0f, true)
        }

        val recognizer = DigitalInkModelManager.getActiveRecognizer()
        if (recognizer == null) {
            Log.w(TAG, "recognizeStrokes: No recognizer available")
            return RecognitionResult("", 0f, true)
        }

        val startTime = System.currentTimeMillis()
        Log.d(TAG, "recognizeStrokes: Processing ${strokes.size} strokes")

        // Calculate bounding box to normalize coordinates to origin
        val bounds = calculateStrokesBounds(strokes)

        // Convert strokes to ML Kit Ink format (normalized to bounding box origin)
        val ink = convertToInk(strokes, bounds.left, bounds.top)

        return try {
            val result = recognizer.recognize(ink).await()
            val candidates = result.candidates.map { it.text }
            val bestResult = candidates.firstOrNull() ?: ""

            // Clean and normalize the result
            val activeLanguage = DigitalInkModelManager.activeLanguage.value
            val cleanedResult = cleanRecognitionResult(bestResult, activeLanguage)
            val normalizedResult = if (HandwritingNormalizerFactory.hasLanguageSupport(context, activeLanguage)) {
                val normalizer = HandwritingNormalizerFactory.getInstance(context, activeLanguage)
                normalizer?.normalize(cleanedResult) ?: cleanedResult
            } else {
                UniversalHandwritingNormalizer.normalize(cleanedResult, activeLanguage)
            }

            // Calculate confidence
            val confidence = if (candidates.isNotEmpty()) {
                val lengthFactor = normalizedResult.length.coerceIn(1, 50) / 50f
                val candidateFactor = 1f - (candidates.size.coerceIn(1, 10) / 20f)
                (lengthFactor * 0.5f + candidateFactor * 0.5f).coerceIn(0.1f, 1f)
            } else 0f

            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "recognizeStrokes: '${normalizedResult.take(50)}...' (${elapsed}ms, confidence=$confidence)")

            RecognitionResult(
                text = normalizedResult,
                confidence = confidence,
                isEmpty = normalizedResult.isBlank()
            )
        } catch (e: Exception) {
            Log.e(TAG, "recognizeStrokes failed: ${e.message}")
            RecognitionResult("", 0f, true)
        }
    }

    /**
     * Calculate bounding box of strokes.
     */
    private fun calculateStrokesBounds(strokes: List<Stroke>): StrokeBounds {
        if (strokes.isEmpty()) {
            return StrokeBounds(0f, 0f, 0f, 0f)
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (stroke in strokes) {
            minX = minOf(minX, stroke.left)
            minY = minOf(minY, stroke.top)
            maxX = maxOf(maxX, stroke.right)
            maxY = maxOf(maxY, stroke.bottom)
        }

        return StrokeBounds(minX, minY, maxX, maxY)
    }

    private data class StrokeBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // Private Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private fun emptySlotResults(): List<SlotResult> {
        return (0 until ROWS_PER_PAGE).map { SlotResult(it, "", 0f, true) }
    }

    /**
     * Recognize text in a single slot.
     */
    private suspend fun recognizeSlot(
        strokes: List<Stroke>,
        slotIndex: Int,
        controlColumnPx: Float,
        canvasHeight: Float,
        topOffset: Float = tasksTopOffset()
    ): SlotResult {
        if (strokes.isEmpty()) {
            return SlotResult(slotIndex, "", 0f, true)
        }

        val recognizer = DigitalInkModelManager.getActiveRecognizer()
        if (recognizer == null) {
            Log.w(TAG, "No recognizer available for slot $slotIndex")
            return SlotResult(slotIndex, "", 0f, true)
        }

        // Convert strokes to ML Kit Ink format
        // Use calculateRowBounds for correct Y offset matching visual overlay
        val rowBounds = calculateRowBounds(slotIndex, canvasHeight, topOffset = topOffset)
        val ink = convertToInk(strokes, controlColumnPx, rowBounds.topY)

        // Recognize
        return try {
            val result = recognizer.recognize(ink).await()
            val candidates = result.candidates.map { it.text }
            val bestResult = candidates.firstOrNull() ?: ""

            // Clean and normalize the result (language-aware routing)
            val activeLanguage = DigitalInkModelManager.activeLanguage.value
            val cleanedResult = cleanRecognitionResult(bestResult, activeLanguage)
            val normalizedResult = if (HandwritingNormalizerFactory.hasLanguageSupport(context, activeLanguage)) {
                // Use full dictionary-based normalization for supported languages
                val normalizer = HandwritingNormalizerFactory.getInstance(context, activeLanguage)
                normalizer?.normalize(cleanedResult) ?: cleanedResult
            } else {
                // Fallback to universal lightweight normalizer for unsupported languages
                UniversalHandwritingNormalizer.normalize(cleanedResult, activeLanguage)
            }

            // Calculate confidence (simple average from candidates)
            val confidence = if (candidates.isNotEmpty()) {
                // ML Kit doesn't provide individual confidence, so estimate based on
                // number of candidates and string length
                val lengthFactor = normalizedResult.length.coerceIn(1, 50) / 50f
                val candidateFactor = 1f - (candidates.size.coerceIn(1, 10) / 20f)
                (lengthFactor * 0.5f + candidateFactor * 0.5f).coerceIn(0.1f, 1f)
            } else 0f

            SlotResult(
                slotIndex = slotIndex,
                text = normalizedResult,
                confidence = confidence,
                isEmpty = normalizedResult.isBlank()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Recognition failed for slot $slotIndex: ${e.message}")
            SlotResult(slotIndex, "", 0f, true)
        }
    }

    /**
     * Convert BooxDay strokes to ML Kit Ink format.
     *
     * @param strokes List of strokes to convert
     * @param offsetX X offset to subtract (control column width)
     * @param offsetY Y offset to subtract (slot top position)
     */
    private fun convertToInk(strokes: List<Stroke>, offsetX: Float, offsetY: Float): Ink {
        val inkBuilder = Ink.builder()

        for (stroke in strokes) {
            val strokeBuilder = Ink.Stroke.builder()
            // Phase 4 (Oct 2026): when the stroke was written (createdAt is its pen-up; dt counts from
            // its first point), so strokes keep their real order and spacing in time
            val lastDt = stroke.points.lastOrNull()?.dt?.toLong() ?: 0L
            val baseTimestamp = stroke.createdAt.time - lastDt

            for ((index, point) in stroke.points.withIndex()) {
                // Offset coordinates to be relative to the slot
                val x = point.x - offsetX
                val y = point.y - offsetY

                // Calculate timestamp from delta time
                val timestamp = if (point.dt != null) {
                    baseTimestamp + point.dt.toLong()
                } else {
                    // Fallback: estimate based on index (~5ms per point)
                    baseTimestamp + (index * 5L)
                }

                strokeBuilder.addPoint(Ink.Point.create(x, y, timestamp))
            }

            inkBuilder.addStroke(strokeBuilder.build())
        }

        return inkBuilder.build()
    }

    /**
     * Clean up recognition result by removing noise artifacts.
     *
     * @param text Raw recognition result
     * @param languageTag BCP-47 language tag for language-specific filtering
     */
    private fun cleanRecognitionResult(text: String, languageTag: String): String {
        if (text.isBlank()) return ""

        var cleaned = text
            // Normalize whitespace (language-agnostic)
            .replace(Regex("\\s+"), " ")
            .trim()

        // English-specific word validation (skip for other languages)
        val isEnglish = languageTag.startsWith("en")
        if (isEnglish) {
            // Remove trailing single letter (except valid ones like "I")
            val words = cleaned.split(" ")
            if (words.size > 1) {
                val lastWord = words.last().replace(Regex("[^a-zA-Z0-9]"), "")
                if (lastWord.length == 1 && !isValidTrailingSingleChar(lastWord)) {
                    cleaned = words.dropLast(1).joinToString(" ")
                } else if (lastWord.length == 2 && !isValidTrailingTwoLetterWord(lastWord)) {
                    cleaned = words.dropLast(1).joinToString(" ")
                }
            }

            // Remove leading single-letter noise (except "I" or "A")
            val firstWords = cleaned.split(" ")
            if (firstWords.size > 1) {
                val firstWord = firstWords.first().replace(Regex("[^a-zA-Z0-9]"), "")
                if (firstWord.length == 1 && firstWord.lowercase() !in setOf("i", "a")) {
                    cleaned = firstWords.drop(1).joinToString(" ")
                }
            }
        }

        // Final trim and remove trailing punctuation noise (language-agnostic)
        cleaned = cleaned.trim().trimEnd('.', ',', ';', ':', '!', '?')

        return cleaned.trim()
    }

    /**
     * Check if a single character is valid at the end of a phrase.
     */
    private fun isValidTrailingSingleChar(char: String): Boolean {
        val c = char.trim()
        if (c.length != 1) return false
        // Keep "I" (pronoun) and all digits
        return c == "I" || c == "i" || c[0].isDigit()
    }

    /**
     * Check if a 2-letter word is valid at the end of a phrase.
     */
    private fun isValidTrailingTwoLetterWord(word: String): Boolean {
        val cleanWord = word.lowercase()

        // Any two-digit number is valid (10-99) - don't strip "room 42", "task 21", etc.
        if (cleanWord.length == 2 && cleanWord.all { it.isDigit() }) {
            return true
        }

        return cleanWord in setOf(
            "to", "do", "go", "be", "we", "he", "me", "my", "no", "so",
            "on", "in", "it", "is", "as", "at", "or", "an", "of", "if",
            "up", "by", "ok", "am", "us", "id"
        )
    }
}
