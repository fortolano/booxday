package com.boox.day.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.boox.day.api.ModelConfigProvider
import com.boox.day.api.ai.AiClient
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.AiResponse
import com.boox.day.api.ai.aiRequest
import com.boox.day.data.PageDataManager
import com.boox.day.data.ai.MemoContextResult
import com.boox.day.data.ai.MemoPageSelection
import com.boox.day.data.db.DayPageRepository
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Extracts context from recent memo pages using parallel OCR.
 *
 * Pipeline:
 * 1. Select memo pages (3-7 days back, max 6 pages)
 * 2. Parallel OCR with Semaphore(3) for 2-3 concurrent API calls
 * 3. Combine results and extract insights (action items, topics, people)
 *
 * Uses Gemini 2.0 Flash Exp for fast, free vision OCR.
 */
object MemoContextExtractor {
    private const val TAG = "MemoContextExtractor"
    private val gson = Gson()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // Concurrency control - max 3 parallel OCR calls
    private val ocrSemaphore = Semaphore(3)

    // Configuration
    private const val MAX_PAGES = 6
    private const val MAX_DAYS_BACK = 7
    private const val MIN_DAYS_BACK = 1  // Start from yesterday

    /**
     * Extract context from recent memo pages.
     *
     * @param context Android context
     * @param currentDate Current date (memo pages are selected from days before this)
     * @param pageDataManager For rendering page bitmaps
     * @param onProgress Progress callback with current/total pages
     * @return MemoContextResult with extracted insights
     */
    suspend fun extractMemoContext(
        context: Context,
        currentDate: LocalDate,
        pageDataManager: PageDataManager,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): MemoContextResult = coroutineScope {
        Log.d(TAG, "Starting memo context extraction for $currentDate")

        // Step 1: Select memo pages
        val dayPageRepo = DayPageRepository(context)
        val selectedPages = selectMemoPages(dayPageRepo, currentDate)

        if (selectedPages.isEmpty()) {
            Log.d(TAG, "No memo pages found for context extraction")
            return@coroutineScope MemoContextResult(
                normalizedText = "",
                actionItems = emptyList(),
                keyTopics = emptyList(),
                mentionedPeople = emptyList(),
                mentionedDates = emptyList(),
                projectReferences = emptyList(),
                pagesScanned = 0,
                errors = emptyList()
            )
        }

        Log.d(TAG, "Selected ${selectedPages.size} pages for OCR")
        onProgress(0, selectedPages.size)

        // Step 2: Parallel OCR with semaphore
        val ocrResults = mutableListOf<PageOcrResult>()
        val errors = mutableListOf<String>()
        var completedCount = 0

        val jobs = selectedPages.map { page ->
            async {
                ocrSemaphore.withPermit {
                    try {
                        val bitmap = pageDataManager.getCachedBitmap(page.pageId)
                        if (bitmap != null) {
                            val result = performOcr(bitmap, page)
                            synchronized(ocrResults) {
                                ocrResults.add(result)
                            }
                        } else {
                            synchronized(errors) {
                                errors.add("Could not load page ${page.pageId}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "OCR error for page ${page.pageId}", e)
                        synchronized(errors) {
                            errors.add("OCR failed for ${page.date}: ${e.message}")
                        }
                    } finally {
                        synchronized(this@coroutineScope) {
                            completedCount++
                            onProgress(completedCount, selectedPages.size)
                        }
                    }
                }
            }
        }

        jobs.awaitAll()

        Log.d(TAG, "OCR complete: ${ocrResults.size} successful, ${errors.size} errors")

        // Step 3: Combine results and extract insights
        combineAndExtractInsights(ocrResults, errors)
    }

    /**
     * Select memo pages for OCR.
     * Strategy: Scan 3-7 days back, select first 1-2 pages per day, max 6 total.
     */
    private fun selectMemoPages(
        dayPageRepo: DayPageRepository,
        currentDate: LocalDate
    ): List<MemoPageSelection> {
        val selections = mutableListOf<MemoPageSelection>()

        // Scan from yesterday backwards
        for (daysBack in MIN_DAYS_BACK..MAX_DAYS_BACK) {
            if (selections.size >= MAX_PAGES) break

            val targetDate = currentDate.minusDays(daysBack.toLong())
            val dateStr = targetDate.format(dateFormatter)

            val dayPage = dayPageRepo.getByDate(dateStr)
            if (dayPage != null && dayPage.pageIds.isNotEmpty()) {
                // Take first 1-2 pages from this day
                val pagesToTake = minOf(2, dayPage.pageIds.size, MAX_PAGES - selections.size)

                dayPage.pageIds.take(pagesToTake).forEachIndexed { index, pageId ->
                    selections.add(
                        MemoPageSelection(
                            pageId = pageId,
                            date = targetDate,
                            pageIndex = index,
                            hasStrokes = true  // Assume has content if page exists
                        )
                    )
                }
            }
        }

        return selections
    }

    /**
     * Perform OCR on a single page bitmap.
     */
    private suspend fun performOcr(bitmap: Bitmap, page: MemoPageSelection): PageOcrResult {
        Log.d(TAG, "Performing OCR on page ${page.pageId} (${page.date})")

        val base64 = bitmapToBase64(bitmap)

        // Try up to 2 times (primary + 1 retry on transient errors)
        var lastError: String? = null
        for (attempt in 1..2) {
            val request = aiRequest {
                model(ModelConfigProvider.getPrimaryVisionModel())
                systemPrompt(buildOcrSystemPrompt())
                userMessageWithImage(
                    text = "Extract all text from this handwritten memo page. Include any action items, names, dates, or topics you find.",
                    imageBase64 = base64
                )
                temperature(0.2f)
            }

            when (val response = AiClient.execute(request)) {
                is AiResponse.Success -> {
                    Log.d(TAG, "OCR success for ${page.date} (attempt $attempt): ${response.data.take(100)}...")
                    return PageOcrResult(
                        pageId = page.pageId,
                        date = page.date,
                        text = response.data.trim(),
                        success = true
                    )
                }
                is AiResponse.Error -> {
                    lastError = response.message
                    Log.w(TAG, "OCR failed for ${page.date} (attempt $attempt): ${response.message}")
                    // Only retry on transient errors
                    val isTransient = response is AiResponse.Error.RateLimitError ||
                        response is AiResponse.Error.NetworkError
                    if (!isTransient || attempt >= 2) break
                    kotlinx.coroutines.delay(500)
                }
            }
        }

        return PageOcrResult(
            pageId = page.pageId,
            date = page.date,
            text = "",
            success = false,
            error = lastError
        )
    }

    private fun buildOcrSystemPrompt(): String {
        return """
You are a handwriting recognition system optimized for personal notes and memos.

Extract ALL text from the image, including:
- Main content and notes
- Action items (marked with checkboxes, bullets, "TODO", etc.)
- Names of people mentioned
- Dates and times referenced
- Project or topic names

Output the extracted text as-is, preserving structure where possible.
If you find action items, prefix them with "ACTION:" on their own line.
If you find dates, preserve them as written.

If the page is blank or has no readable text, respond with: [BLANK PAGE]
""".trimIndent()
    }

    /**
     * Combine OCR results and extract structured insights.
     */
    private suspend fun combineAndExtractInsights(
        ocrResults: List<PageOcrResult>,
        errors: List<String>
    ): MemoContextResult {
        // Combine all text
        val combinedText = ocrResults
            .filter { it.success && it.text.isNotBlank() && it.text != "[BLANK PAGE]" }
            .sortedByDescending { it.date }  // Most recent first
            .joinToString("\n\n---\n\n") { result ->
                "[${result.date}]\n${result.text}"
            }

        if (combinedText.isBlank()) {
            return MemoContextResult(
                normalizedText = "",
                actionItems = emptyList(),
                keyTopics = emptyList(),
                mentionedPeople = emptyList(),
                mentionedDates = emptyList(),
                projectReferences = emptyList(),
                pagesScanned = ocrResults.count { it.success },
                errors = errors
            )
        }

        // Use AI to extract structured insights
        val insights = extractInsightsWithAi(combinedText)

        return MemoContextResult(
            normalizedText = combinedText,
            actionItems = insights.actionItems,
            keyTopics = insights.keyTopics,
            mentionedPeople = insights.mentionedPeople,
            mentionedDates = insights.mentionedDates,
            projectReferences = insights.projectReferences,
            pagesScanned = ocrResults.count { it.success },
            errors = errors
        )
    }

    /**
     * Use AI to extract structured insights from combined memo text.
     */
    private suspend fun extractInsightsWithAi(combinedText: String): ExtractedInsights {
        val request = aiRequest {
            model(ModelConfigProvider.getPrimaryTextModel())
            systemPrompt(buildInsightsSystemPrompt())
            userMessage(combinedText)
            jsonOutput()
            temperature(0.3f)
        }

        return when (val response = AiClient.execute(request)) {
            is AiResponse.Success -> {
                try {
                    val cleanJson = JsonExtractor.extract(response.data)
                    gson.fromJson(cleanJson, ExtractedInsights::class.java) ?: ExtractedInsights()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse insights JSON", e)
                    ExtractedInsights()
                }
            }
            is AiResponse.Error -> {
                Log.w(TAG, "Insights extraction failed: ${response.message}")
                ExtractedInsights()
            }
        }
    }

    private fun buildInsightsSystemPrompt(): String {
        return """
You are analyzing personal memo notes to extract actionable insights.

From the provided memo text, extract:
1. actionItems: Specific tasks or to-dos mentioned (e.g., "Call John", "Review proposal")
2. keyTopics: Main topics/themes discussed (e.g., "budget", "Q1 planning", "marketing")
3. mentionedPeople: Names of people referenced (e.g., "John", "Sarah", "Dr. Smith")
4. mentionedDates: Date references found (e.g., "Friday", "Dec 20", "next week")
5. projectReferences: Project or work item names (e.g., "Project Alpha", "Website redesign")

Return JSON:
{
  "actionItems": ["Call John about meeting", "Review Q4 report"],
  "keyTopics": ["budget", "team meeting", "product launch"],
  "mentionedPeople": ["John", "Sarah"],
  "mentionedDates": ["Friday", "December 20"],
  "projectReferences": ["Project Alpha"]
}

Extract only what's explicitly mentioned. Keep items concise.
""".trimIndent()
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    // Internal data classes
    private data class PageOcrResult(
        val pageId: String,
        val date: LocalDate,
        val text: String,
        val success: Boolean,
        val error: String? = null
    )

    private data class ExtractedInsights(
        @SerializedName("actionItems") val actionItems: List<String> = emptyList(),
        @SerializedName("keyTopics") val keyTopics: List<String> = emptyList(),
        @SerializedName("mentionedPeople") val mentionedPeople: List<String> = emptyList(),
        @SerializedName("mentionedDates") val mentionedDates: List<String> = emptyList(),
        @SerializedName("projectReferences") val projectReferences: List<String> = emptyList()
    )
}
