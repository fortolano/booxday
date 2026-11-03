package com.boox.day.ai

import android.graphics.Bitmap
import android.util.Base64
import com.boox.day.api.ModelConfigProvider
import com.boox.day.api.ai.AiCapability
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.AiProblem
import com.boox.day.api.ai.AiResponse
import com.boox.day.api.ai.LastAiProblem
import com.boox.day.api.ai.aiRequest
import com.boox.day.data.db.Stroke
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.semantic.model.DateSemanticContext
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.boox.day.utils.TaggedLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

private val log = TaggedLog("MemoToTasksService")

/**
 * A task converted from memo text.
 */
data class ConvertedTask(
    @SerializedName("text") val text: String,
    @SerializedName("suggestedPriority") val suggestedPriority: String = "",  // "", "A", "B", "C"
    @SerializedName("reason") val reason: String = "",
    @SerializedName("confidence") val confidence: Float = 0.5f,  // NEW
    @SerializedName("suggestedTime") val suggestedTime: String? = null,  // NEW
    @SerializedName("relatedEventId") val relatedEventId: String? = null,  // NEW
    val isSelected: Boolean = true,  // User can toggle in preview
    // Per-task date overrides (null = use defaults from dialog)
    val targetDateOverride: String? = null,  // yyyy-MM-dd format
    val dueDateOverride: Long? = null        // epoch ms, null = no due date
)

/**
 * Result of memo-to-tasks conversion.
 */
data class MemoToTasksResult(
    val originalText: String,       // Raw OCR text
    val cleanedText: String,        // After TextInferenceService
    val tasks: List<ConvertedTask>, // Generated tasks
    val error: String? = null,      // Null if success
    /** No clear to-dos (2 Nov 2026): [tasks] are the lines read, unticked, for the user to pick */
    val linesToPick: Boolean = false
)

/**
 * Processing states for the dialog.
 */
sealed class MemoToTasksProcessingState {
    data class Processing(val stageMessage: String) : MemoToTasksProcessingState()
    data object SelectTasks : MemoToTasksProcessingState()
    data class Error(val message: String) : MemoToTasksProcessingState()

    companion object {
        /** The dialog's screen for [result]: its tasks or lines to tick whenever there are any, else its error. */
        fun of(result: MemoToTasksResult): MemoToTasksProcessingState =
            if (result.error != null && result.tasks.isEmpty()) Error(result.error) else SelectTasks
    }
}

/**
 * Result of adding tasks to a date.
 */
data class TasksAddedResult(
    val tasksAdded: Int,
    val pagesUsed: Int,
    val targetDates: Set<String>,  // All dates that tasks were added to (supports per-task date overrides)
    val latestPageIndexByDate: Map<String, Int> = emptyMap()
)

// JSON response models for Gson parsing
private data class TasksResponseJson(
    @SerializedName("tasks") val tasks: List<TaskJson>
)

private data class TaskJson(
    @SerializedName("text") val text: String,
    @SerializedName("suggestedPriority") val suggestedPriority: String?,
    @SerializedName("reason") val reason: String?,
    @SerializedName("confidence") val confidence: Float?,
    @SerializedName("suggestedTime") val suggestedTime: String?,
    @SerializedName("relatedEventId") val relatedEventId: String?
)

private data class OcrTextResponseJson(
    @SerializedName("hasText") val hasText: Boolean = true,
    @SerializedName("text") val text: String = ""
)

/**
 * Service for converting handwritten memo content into structured, actionable tasks.
 *
 * Uses a four-stage pipeline:
 * 1. **Stage 0 (Capture)**: Encode bitmap to Base64 JPEG
 * 2. **Stage 1 (Vision OCR)**: Extract text from handwriting image
 * 3. **Stage 2 (Inference)**: Semantic correction via TextInferenceService
 * 4. **Stage 3 (Task Conversion)**: Convert text to actionable tasks with priorities
 *
 * Model fallback chains:
 * - Vision: NovaLite → Gemini2FlashExp → Gemma3_27B → NemotronNano → Gpt5Nano
 * - Task conversion: GptOss120B → GptOss20B → KimiK2 → Gpt5Nano
 */
object MemoToTasksService {
    private val gson = Gson()
    private val multiWhitespaceRegex = Regex("\\s+")
    private val explicitTimeRegex = Regex(
        """\b(\d{1,2}(:\d{2})?\s?(am|pm)|today|tomorrow|tonight|morning|afternoon|evening|monday|tuesday|wednesday|thursday|friday|saturday|sunday|jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december|by|before|after)\b""",
        RegexOption.IGNORE_CASE
    )
    private val plausibleEventIdRegex = Regex("""^[A-Za-z0-9][A-Za-z0-9:_-]{2,}$""")
    private const val MAX_TEXT_LENGTH = 8000

    /**
     * Why every model failed, for the dialog (Oct 2026). It said "All vision models failed to
     * extract text" or "All models failed to convert memo to tasks" whatever the cause, so no
     * Wi-Fi, no credit and a bad answer looked the same.
     */
    private class ModelFailures {
        private var lastError: AiResponse.Error? = null
        private var creditRefusal: AiResponse.Error.OutOfCredit? = null
        private var keyProblem: AiResponse.Error? = null

        /** Notes a model's answer that could not be used; true when no other model can help. */
        fun note(response: AiResponse<String>): Boolean {
            when (response) {
                // The model answered, but nothing usable came back
                is AiResponse.Success -> lastError = null
                is AiResponse.Error.NoApiKey,
                is AiResponse.Error.KeyRejected -> {
                    keyProblem = response
                    return true
                }
                is AiResponse.Error.OutOfCredit -> creditRefusal = response
                is AiResponse.Error -> lastError = response
            }
            return false
        }

        /** A key problem first, then a credit refusal (says to add credit), else the last model's problem. */
        fun message(): String =
            (keyProblem ?: creditRefusal ?: lastError)?.let { AiProblem.messageFor(it) } ?: AiProblem.MODEL_FAILED.message
    }

    // Stage 1 vision models - same as AiHandwritingService
    private fun getVisionModels(): List<AiModel> =
        ModelConfigProvider.getVisionFallbackChain()

    // Stage 3 task conversion models - text models with JSON output
    private fun getTaskConversionModels(): List<AiModel> =
        ModelConfigProvider.getTextFallbackChain()

    // Delimiters for parsing AI responses
    private const val START_TEXT_DELIMITER = "<<<START_TEXT>>>"
    private const val END_TEXT_DELIMITER = "<<<END_TEXT>>>"
    private const val NO_TEXT_MARKER = "<<<NO_TEXT>>>"

    // Stage 1 prompt: Extract ALL text from image exactly as written
    // Note: Stage 2 (TextInferenceService) handles spelling/grammar corrections
    private val STAGE1_PROMPT = """
You are a text extraction system for handwritten notes.

TASK: Extract ALL readable text from the image exactly as written.

RULES:
- Extract all text: handwritten, typed, printed
- Preserve line breaks between distinct items or thoughts
- Preserve list markers: bullets (•, -, *), checkboxes (☐, ✓), numbers (1., 2.)
- Use [unclear] for illegible words
- Do NOT correct spelling or grammar - preserve exactly as written
- IGNORE non-text elements: shapes, arrows, decorative lines, doodles
- If the handwriting is in a non-English language, transcribe in that language

OUTPUT:
- Return raw JSON only.
- If text exists, return {"hasText": true, "text": "..."}.
- If no readable text exists, return {"hasText": false, "text": ""}.
    """.trimIndent()

    // Stage 3 prompt: Convert text to actionable tasks
    // Key principle: Preserve user's intent, don't over-transform
private val TASK_CONVERSION_PROMPT = """
You are a task extraction assistant. Convert notes into actionable tasks.

JOB:
1. Extract only actionable items: tasks, reminders, appointments, deadlines, and follow-ups.
2. Preserve the user's wording whenever possible.
3. Add a missing action verb only when the intended action is obvious.
4. When the source contains multiple distinct actionable items, return one task per distinct item.

HARD RULES:
1. Do not invent names, dates, times, places, priorities, or extra details.
2. Do not turn observations, status updates, journal notes, brainstorming, or meeting summaries into tasks unless they clearly ask for an action.
3. Do not split one natural task into many smaller tasks.
4. Keep duplicate mentions as one task only.
5. Keep times, quantities, and names exactly when present.
6. For each returned task, include every field: text, suggestedPriority, reason, confidence, suggestedTime, relatedEventId.
7. Set suggestedPriority when the note clearly supports it.
8. Use "A" only for clearly urgent or explicitly time-critical items.
9. Use "" when priority is unclear instead of guessing.
10. Use lower confidence for ambiguous items, and prefer skipping them instead of forcing a task.
11. Set suggestedTime to an explicit time or date phrase from the note worth preserving, else "".
12. Do not output generic suggestedTime values like "today", "soon", "later", or the whole task text.
13. Set relatedEventId to "" unless the input already contains a real event ID. Never make up IDs from task text.
14. Return raw JSON only and match the schema exactly: "" for a field with nothing to say, never null.
15. If no actionable tasks are present, return {"tasks": []}.

TASK FILTER:
- A task must describe something the user should do, attend, send, buy, call, review, schedule, or follow up on.
- Separate bullet points, checklist items, numbered items, and distinct actionable sentences into separate tasks when they describe different actions.
- If one paragraph contains several different actions, split only at those natural action boundaries.
- Do not convert pure information into a task.
- Do not convert lines like "meeting notes", "launch moved to March", or other status statements into tasks unless an action is also stated.
- Do not assign priority "A" to normal errands or routine tasks unless urgency is directly stated.
- If you are unsure whether a line is actionable, leave it out.
- Extract tasks in the same language as the source content.

CONFIDENCE GUIDE:
- 0.9-1.0: Clearly actionable with explicit details (deadline, person, time)
- 0.7-0.89: Likely a task, minor inference needed
- 0.5-0.69: Possibly a task, some ambiguity
- 0.3-0.49: Uncertain if actionable — prefer skipping over forcing
- below 0.3: Probably not a task (will be filtered)

EXAMPLE:
Input: "Meeting with John tomorrow 3pm / need to buy groceries milk eggs / call dentist!! reschedule / review q4 budget before friday / nice weather today"
Output:
{"tasks": [
  {"text": "Meeting with John at 3pm tomorrow", "suggestedPriority": "A", "reason": "Time-sensitive appointment", "confidence": 0.95, "suggestedTime": "3pm tomorrow", "relatedEventId": ""},
  {"text": "Buy groceries: milk, eggs", "suggestedPriority": "", "reason": "Shopping errand", "confidence": 0.9, "suggestedTime": "", "relatedEventId": ""},
  {"text": "Call dentist to reschedule appointment", "suggestedPriority": "A", "reason": "Marked urgent with '!!'", "confidence": 0.95, "suggestedTime": "", "relatedEventId": ""},
  {"text": "Review Q4 budget before Friday", "suggestedPriority": "A", "reason": "Has deadline", "confidence": 0.9, "suggestedTime": "before Friday", "relatedEventId": ""}
]}
Note: "nice weather today" skipped — not actionable.
    """.trimIndent()

    // Lasso → Convert to Tasks (2 Nov 2026, owner's decision): the user chose the text, so every
    // item is a task. With the prompt above a selected sentence that read like a note gave no tasks
    // and the dialog said "Could not convert memo".
    private val SELECTION_CONVERSION_PROMPT = """
You turn text the user selected on their handwritten memo into tasks. They selected it and asked for tasks, so every item in it is a task.

JOB:
1. Return one task per item. An item is a list item, a checklist line, a numbered line or a sentence. A sentence that continues on the next line is one item.
2. Never leave an item out and never return an empty list. When an item reads like a note rather than an action, keep its words as the task.
3. Keep the user's wording. Fix only obvious reading errors, and add an action verb only when the intended action is obvious.

RULES:
1. Do not invent names, dates, times, places, priorities, or extra details.
2. Keep times, quantities, and names exactly when present.
3. Keep duplicate mentions as one task only.
4. For each returned task, include every field: text, suggestedPriority, reason, confidence, suggestedTime, relatedEventId.
5. Use "A" only for clearly urgent or explicitly time-critical items, "" when priority is unclear.
6. Set suggestedTime to an explicit time or date phrase from the item worth preserving, else "". Never "today", "soon", "later", or the whole task text.
7. Set relatedEventId to "" unless the input already contains a real event ID.
8. Set reason to a few words, e.g. "Selected by you".
9. Return raw JSON only and match the schema exactly: "" for a field with nothing to say, never null.
10. Write tasks in the same language as the text.
    """.trimIndent()

    /** Where the text to convert came from (2 Nov 2026). */
    internal enum class Source {
        /** AI menu → Convert Memo to Tasks: the AI picks the to-dos; none found → the lines read, to tick */
        PAGE,
        /** Lasso → Convert to Tasks: the selection decides, every item is a task */
        SELECTION
    }

    private fun buildTaskConversionPrompt(source: Source): String = when (source) {
        Source.PAGE -> TASK_CONVERSION_PROMPT
        Source.SELECTION -> SELECTION_CONVERSION_PROMPT
    }

    private fun buildContextSection(context: DateSemanticContext): String {
        val events = context.pendingActionItems.take(5)
            .map { "${it.actionItem.verb} ${it.actionItem.objectText}" }
            .joinToString(", ")
            .ifBlank { "None" }

        val keywords = context.allKeywords.take(10)
            .map { it.term }
            .joinToString(", ")
            .ifBlank { "None" }

        val domains = context.dominantDomains.take(3)
            .map { "${it.domain.name} (${it.count} items)" }
            .joinToString(", ")
            .ifBlank { "General" }

        return """

## TODAY'S CONTEXT
Scheduled Events: $events
Active Topics: $domains
Key Keywords: $keywords

Use this context to:
- Suggest times that don't conflict with events
- Align task priorities with today's themes
- Identify follow-up tasks from meetings
"""
    }

    /**
     * AI menu → Convert Memo to Tasks: a memo page's picture to tasks. When the AI finds no clear
     * to-do, the lines read come back unticked for the user to pick ([MemoToTasksResult.linesToPick]).
     */
    suspend fun convertMemoToTasks(
        bitmap: Bitmap,
        dayContext: DateSemanticContext? = null,
        existingTasks: List<String> = emptyList(),
        onStageChange: (String) -> Unit
    ): MemoToTasksResult = convertPicture(bitmap, Source.PAGE, dayContext, existingTasks, onStageChange)

    /**
     * Lasso → Convert to Tasks (2 Nov 2026): the selected [strokes] are read by the same AI
     * handwriting models as a whole page and AI Recognize, and every item read becomes a task.
     * They were read by ML Kit as one line of text, which garbles a selection of more than one
     * line (ML Kit assumes a single line, developers.google.com/ml-kit/vision/digital-ink-recognition).
     */
    suspend fun convertSelectionToTasks(
        strokes: List<Stroke>,
        dayContext: DateSemanticContext? = null,
        onStageChange: (String) -> Unit
    ): MemoToTasksResult {
        val bitmap = withContext(Dispatchers.Default) { MemoPageContent.renderSelection(strokes) }
            ?: return failed(NO_TEXT_IN_SELECTION)
        return try {
            // Tasks already on the day don't count: the user chose this text
            convertPicture(bitmap, Source.SELECTION, dayContext, emptyList(), onStageChange)
        } finally {
            bitmap.recycle()
        }
    }

    private const val NO_TEXT_IN_MEMO = "No text detected in memo. Please write some notes first."
    private const val NO_TEXT_IN_SELECTION = "No text detected in selection. Please write some notes first."

    private fun noText(source: Source) = if (source == Source.SELECTION) NO_TEXT_IN_SELECTION else NO_TEXT_IN_MEMO

    /** A result the dialog shows as "Could not convert memo", kept as the last AI problem (2 Nov 2026). */
    private fun failed(message: String, originalText: String = ""): MemoToTasksResult {
        LastAiProblem.noteShown("Convert to Tasks", message)
        return MemoToTasksResult(originalText, "", emptyList(), error = message)
    }

    private suspend fun convertPicture(
        bitmap: Bitmap,
        source: Source,
        dayContext: DateSemanticContext?,
        existingTasks: List<String>,
        onStageChange: (String) -> Unit
    ): MemoToTasksResult {
        val rawText = try {
            onStageChange(if (source == Source.SELECTION) "Capturing your selection..." else "Capturing your memo...")
            val base64Image = encodeBitmapToBase64(bitmap)
            log.i("Encoded bitmap: ${base64Image.length} chars")

            onStageChange("Extracting text from handwriting...")
            extractTextFromImage(base64Image)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e("Reading the handwriting failed: ${e.message}")
            return failed(e.message ?: "Conversion failed. Please try again.")
        }
        if (rawText.isBlank()) {
            log.w("No text detected")
            return failed(noText(source))
        }
        return convertReadText(rawText, source, dayContext, existingTasks, onStageChange)
    }

    /**
     * Stages 2 and 3 for text already read. No task from the AI is not a failure (2 Nov 2026: it
     * showed "Could not convert memo" with a Retry that asked again): a page's lines come back to
     * tick, a selection's lines are its tasks.
     */
    internal suspend fun convertReadText(
        rawText: String,
        source: Source,
        dayContext: DateSemanticContext?,
        existingTasks: List<String>,
        onStageChange: (String) -> Unit
    ): MemoToTasksResult {
        log.i("Read ${rawText.length} chars")
        log.d("Read text: $rawText")
        return try {
            // Stage 2: Semantic correction
            onStageChange("Cleaning up and fixing errors...")
            val cleanedText = TextInferenceService.refineText(rawText = rawText, context = InferenceContext.TASK).refinedText
            log.d("Cleaned text: $cleanedText")

            // Stage 3: Task conversion
            onStageChange("Converting to actionable tasks...")
            val tasks = convertTextToTasks(cleanedText, source, dayContext, existingTasks)
            log.i("Generated ${tasks.size} tasks ($source)")
            if (tasks.isNotEmpty()) return MemoToTasksResult(rawText, cleanedText, tasks)

            val lines = lineItems(cleanedText).ifEmpty { lineItems(rawText) }
            if (lines.isEmpty()) return failed(noText(source), rawText)
            val chosen = source == Source.SELECTION
            MemoToTasksResult(
                originalText = rawText,
                cleanedText = cleanedText,
                tasks = lines.map { ConvertedTask(text = it, reason = if (chosen) "Selected by you" else "", isSelected = chosen) },
                linesToPick = !chosen
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e("Text to tasks conversion failed: ${e.message}")
            failed(e.message ?: "Conversion failed. Please try again.", rawText)
        }
    }

    // A list marker at the start of a line: a bullet or box, or a number followed by "." or ")"
    private val listMarkerRegex = Regex("""^(?:[-•*·▪◦–—☐☑✓✔]+|\[[ xX]?]|\d{1,2}[.)](?=\s))\s*""")

    // English words a sentence doesn't end on: the line after one goes on with the sentence, even
    // when it starts with a name ("Ask Sam about the / Lisbon trip")
    private val joiningWords = setOf(
        "a", "an", "the", "to", "of", "for", "with", "about", "and", "or", "but", "at", "on", "in", "by",
        "from", "into", "my", "your", "his", "her", "our", "their", "its", "this", "that", "w/"
    )

    /**
     * The items of [text], one per line, for the user to tick or as a selection's tasks (2 Nov 2026):
     * list markers dropped, a line joined to the one before when that one has no end punctuation and
     * this one starts in lower case or follows a word like "the" (a sentence written over two lines),
     * blank lines ending an item, repeats left out.
     */
    internal fun lineItems(text: String): List<String> {
        val items = mutableListOf<String>()
        var canContinue = false
        for (rawLine in text.lines()) {
            val line = collapseWhitespace(rawLine)
            if (line.isEmpty()) {
                canContinue = false
                continue
            }
            val marker = listMarkerRegex.find(line)
            val body = if (marker != null) line.substring(marker.range.last + 1).trim() else line
            if (body.isEmpty()) continue
            val goesOn = body.first().isLowerCase() ||
                items.lastOrNull()?.substringAfterLast(' ')?.lowercase() in joiningWords
            if (marker == null && canContinue && goesOn) {
                items[items.lastIndex] = items.last() + " " + body
            } else {
                items += body
            }
            canContinue = items.last().last() !in ".!?:;"
        }
        return items.distinctBy { normalizeTaskKey(it) }.take(StructuredOutputSchemas.MEMO_TO_TASKS_MAX_ITEMS)
    }

    /**
     * Encodes a bitmap to Base64 JPEG string.
     */
    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Stage 1: Extract text from image using vision models with fallback.
     */
    private suspend fun extractTextFromImage(base64Image: String): String {
        val failures = ModelFailures()
        for (model in getVisionModels()) {
            try {
                log.i("Trying vision model: ${model.id}")

                val request = aiRequest {
                    model(model)
                    systemPrompt(STAGE1_PROMPT)
                    userMessageWithImage("Extract all text from this handwritten memo:", base64Image)
                    jsonSchema("ocr_text", StructuredOutputSchemas.ocrTextSchema)
                    if (model.hasCapability(AiCapability.REASONING)) {
                        enableReasoning()
                    }
                }

                // AiClient.execute; unit tests replace it
                val response = AiRetryExecutor.send(request, true)

                if (response is AiResponse.Success) {
                    val content = response.data
                    log.i("Vision model ${model.id} returned ${content.length} chars")

                    // Check for no text marker
                    if (content.contains(NO_TEXT_MARKER)) {
                        log.i("Model detected no text in image")
                        return ""
                    }

                    val structuredText = parseStructuredOcrResponse(content)
                    if (structuredText != null) {
                        return structuredText
                    }

                    // Extract text between delimiters
                    val extractedText = extractBetweenDelimiters(content)
                    if (extractedText.isNotBlank()) {
                        return extractedText
                    }
                    // Any other answer goes to the next model (2 Nov 2026): the whole answer was
                    // taken as the memo's text, so a model's own words could become the user's tasks
                }
                if (response is AiResponse.Success) LastAiProblem.noteTry(request, AiRetryExecutor.UNUSABLE_ANSWER)
                if (failures.note(response)) break

                log.w("Model ${model.id} failed or returned empty, trying next...")

            } catch (e: Exception) {
                log.w("Model ${model.id} threw exception: ${e.message}")
                continue
            }
        }

        throw Exception(failures.message())
    }

    private fun parseStructuredOcrResponse(response: String): String? {
        return try {
            val cleanJson = JsonExtractor.extract(response)
            val parsed = gson.fromJson(cleanJson, OcrTextResponseJson::class.java)
            if (parsed?.hasText == false) {
                ""
            } else {
                collapseWhitespacePreservingLines(parsed?.text)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Stage 3: Convert cleaned text to actionable tasks.
     */
    private suspend fun convertTextToTasks(
        text: String,
        source: Source,
        dayContext: DateSemanticContext?,
        existingTasks: List<String>
    ): List<ConvertedTask> {
        val failures = ModelFailures()
        val taskScopeSection = if (existingTasks.isNotEmpty()) {
            "Existing tasks (do not duplicate):\n" +
                existingTasks.take(10).joinToString("\n") { "- $it" }
        } else null

        val contextDate = dayContext?.date
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()
        val semanticFallbackSection = dayContext?.let { buildContextSection(it).trim() }

        val contextBlock = TaskAiContextBuilderV1.buildContextBlockOrEmpty(
            context = null,
            date = contextDate,
            includeBooxNotes = false,
            includeSourceTypes = setOf(OcrSource.TASK, OcrSource.MEMO),
            taskScopeSection = taskScopeSection,
            semanticFallbackSection = semanticFallbackSection
        )
        val systemPrompt = buildTaskConversionPrompt(source)
        val truncatedText = if (text.length > MAX_TEXT_LENGTH) {
            log.w("Text truncated from ${text.length} to $MAX_TEXT_LENGTH chars")
            text.take(MAX_TEXT_LENGTH) + "\n...[content truncated]"
        } else text
        val userPrompt = buildString {
            if (contextBlock.isNotBlank()) {
                // Oct 2026: reference only, so tasks come from the memo, not from the context
                appendLine(TaskAiContextBuilderV1.REFERENCE_ONLY_HEADER)
                appendLine(contextBlock)
                appendLine()
            }
            appendLine(
                if (source == Source.SELECTION) "Turn this selected text into tasks (make tasks only from this text):"
                else "Convert this memo to tasks (make tasks only from this memo):"
            )
            appendLine()
            append(truncatedText)
        }

        for (model in getTaskConversionModels()) {
            try {
                log.i("Trying task conversion model: ${model.id}")

                val request = aiRequest {
                    model(model)
                    systemPrompt(systemPrompt)
                    userMessage(userPrompt)
                    jsonSchema("memo_to_tasks", StructuredOutputSchemas.memoToTasksSchema)
                    if (model.hasCapability(AiCapability.REASONING)) {
                        enableReasoning()
                    }
                }

                // AiClient.execute; unit tests replace it
                val response = AiRetryExecutor.send(request, true)

                if (response is AiResponse.Success) {
                    val content = response.data
                    log.i("Task conversion model ${model.id} returned ${content.length} chars")

                    // A selection keeps every item, however unsure the model is of it
                    val tasks = parseTasksResponse(content, minConfidence = if (source == Source.SELECTION) 0f else 0.3f)
                    if (tasks != null) {
                        return tasks
                    }
                }
                if (response is AiResponse.Success) LastAiProblem.noteTry(request, AiRetryExecutor.UNUSABLE_ANSWER)
                if (failures.note(response)) break

                log.w("Model ${model.id} failed or returned empty tasks, trying next...")

            } catch (e: Exception) {
                log.w("Model ${model.id} threw exception: ${e.message}")
                continue
            }
        }

        throw Exception(failures.message())
    }

    /**
     * Parse JSON response into ConvertedTask list.
     */
    private fun parseTasksResponse(json: String, minConfidence: Float): List<ConvertedTask>? {
        try {
            val cleaned = JsonExtractor.extract(json)
            log.d("Parsing JSON: ${cleaned.take(500)}...")

            val parsed = gson.fromJson(cleaned, TasksResponseJson::class.java)
            val seen = mutableSetOf<String>()

            return parsed.tasks.mapNotNull { task ->
                val converted = sanitizeConvertedTask(task) ?: return@mapNotNull null
                val key = normalizeTaskKey(converted.text)
                if (!seen.add(key) || converted.confidence < minConfidence) {
                    null
                } else {
                    converted
                }
            }.take(StructuredOutputSchemas.MEMO_TO_TASKS_MAX_ITEMS)

        } catch (e: Exception) {
            log.e("Failed to parse tasks JSON", e)
            return null
        }
    }

    /**
     * Converts several memo pages, one at a time: each page's picture is drawn by its [pages] entry
     * just before it is read and freed right after, so only one whole page is in memory (Oct 2026).
     */
    suspend fun convertMultiplePagesToTasks(
        pages: List<suspend () -> Bitmap>,
        dayContext: DateSemanticContext?,
        existingTasks: List<String>,
        onPageProgress: (current: Int, total: Int) -> Unit,
        onStageChange: (String) -> Unit
    ): MemoToTasksResult {
        val allTasks = mutableListOf<ConvertedTask>()
        val total = pages.size
        var lastError: String? = null

        pages.forEachIndexed { index, drawPage ->
            onPageProgress(index + 1, total)
            val bitmap = drawPage()
            val result = try {
                convertMemoToTasks(
                    bitmap = bitmap,
                    dayContext = dayContext,
                    // Lines offered to tick are not tasks yet
                    existingTasks = existingTasks + allTasks.filter { it.isSelected }.map { it.text },
                    onStageChange = onStageChange
                )
            } finally {
                bitmap.recycle()
            }
            if (result.error != null) {
                lastError = result.error
            } else {
                allTasks.addAll(result.tasks)
            }
        }

        val deduped = allTasks.distinctBy { normalizeTaskKey(it.text) }.take(StructuredOutputSchemas.MEMO_TO_TASKS_MAX_ITEMS)
        return MemoToTasksResult(
            originalText = "",
            cleanedText = "",
            tasks = deduped,
            error = lastError,
            // No page had a clear to-do: the dialog says so above the lines
            linesToPick = deduped.isNotEmpty() && deduped.none { it.isSelected }
        )
    }

    private fun collapseWhitespace(text: String?): String {
        return text?.replace(multiWhitespaceRegex, " ")?.trim().orEmpty()
    }

    private fun collapseWhitespacePreservingLines(text: String?): String {
        return text
            ?.lines()
            ?.joinToString("\n") { line -> collapseWhitespace(line) }
            ?.trim()
            .orEmpty()
    }

    private fun normalizeTaskKey(text: String): String {
        return collapseWhitespace(text).lowercase()
    }

    private fun normalizePriority(priority: String?): String {
        return when (collapseWhitespace(priority).uppercase()) {
            "A", "B", "C" -> collapseWhitespace(priority).uppercase()
            else -> ""
        }
    }

    private fun sanitizeSuggestedTime(value: String?, taskText: String): String? {
        val cleaned = collapseWhitespace(value)
        if (cleaned.isBlank()) return null

        val normalized = normalizeTaskKey(cleaned)
        if (normalized == normalizeTaskKey(taskText)) return null
        if (normalized in setOf("today", "soon", "later", "asap", "anytime")) return null
        if (!explicitTimeRegex.containsMatchIn(cleaned)) return null

        return cleaned
    }

    private fun sanitizeRelatedEventId(value: String?, taskText: String): String? {
        val cleaned = collapseWhitespace(value)
        if (cleaned.isBlank()) return null
        if (normalizeTaskKey(cleaned) == normalizeTaskKey(taskText)) return null
        if (!plausibleEventIdRegex.matches(cleaned)) return null

        return cleaned
    }

    private fun sanitizeConvertedTask(task: TaskJson): ConvertedTask? {
        val text = collapseWhitespace(task.text)
        if (text.isBlank()) return null

        return ConvertedTask(
            text = text,
            suggestedPriority = normalizePriority(task.suggestedPriority),
            reason = collapseWhitespace(task.reason),
            confidence = (task.confidence ?: 0.5f).coerceIn(0f, 1f),
            suggestedTime = sanitizeSuggestedTime(task.suggestedTime, text),
            relatedEventId = sanitizeRelatedEventId(task.relatedEventId, text),
            isSelected = true
        )
    }

    /**
     * Extract text between START_TEXT and END_TEXT delimiters.
     */
    private fun extractBetweenDelimiters(content: String): String {
        val startIndex = content.indexOf(START_TEXT_DELIMITER)
        val endIndex = content.indexOf(END_TEXT_DELIMITER)

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return content.substring(
                startIndex + START_TEXT_DELIMITER.length,
                endIndex
            ).trim()
        }

        return ""
    }
}
