package com.boox.day.ai

import android.util.Log
import com.boox.day.api.ModelConfigProvider
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.AiResponse
import com.boox.day.api.ai.aiRequest
import com.boox.day.data.model.ParsedEventData
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * AI-powered parser for converting voice input to structured calendar events.
 *
 * Uses a **three-stage pipeline** (matching TaskAiService's proven pattern):
 * 1. **Stage 1 (Gemini 2.0 Flash Lite)**: Audio → raw transcription
 * 2. **Stage 2 (TextInferenceService)**: Raw text → corrected/inferred text
 * 3. **Stage 3 (GPT-OSS-120B → 20B fallback)**: Corrected text → structured ParsedEventData
 *
 * **CRITICAL**: The AI must INFER all mandatory fields even when not explicitly stated.
 * Users don't have to say everything - the AI fills in the gaps intelligently.
 */
object VoiceEventParser {
    private const val TAG = "VoiceEventParser"
    private val gson = Gson()

    private fun getReasoningModels(): List<AiModel> =
        ModelConfigProvider.getReasoningFallbackChain()

    /** The voice job's models: the user's pick, then Boox Day's choice (a backup since Oct 2026) */
    private fun getAudioModels(): List<AiModel> =
        ModelConfigProvider.getAudioFallbackChain()

    /**
     * JSON structure expected from LLM for event parsing.
     * All fields have defaults to handle Gson's null behavior with Kotlin.
     */
    private data class EventResponse(
        @SerializedName("title") val title: String? = null,
        @SerializedName("date") val date: String? = null,
        @SerializedName("startHour") val startHour: Int? = null,
        @SerializedName("startMinute") val startMinute: Int? = null,
        @SerializedName("endHour") val endHour: Int? = null,
        @SerializedName("endMinute") val endMinute: Int? = null,
        @SerializedName("durationMinutes") val durationMinutes: Int? = null,
        @SerializedName("isAllDay") val isAllDay: Boolean? = null,
        @SerializedName("location") val location: String? = null,
        @SerializedName("description") val description: String? = null,
        @SerializedName("attendees") val attendees: List<String>? = null,
        @SerializedName("confidence") val confidence: Float? = null,
        @SerializedName("uncertainFields") val uncertainFields: List<String>? = null,
        @SerializedName("originalIntent") val originalIntent: String? = null
    )

    /**
     * Parse audio directly into structured event data using a three-stage pipeline.
     *
     * @param audioBase64 Base64-encoded WAV audio data
     * @param currentDate The date currently being viewed in the calendar (for relative date parsing)
     * @param mimeType Audio MIME type (default: "audio/wav")
     * @return ParsedEventData with extracted fields and confidence score
     */
    suspend fun parseAudioInput(
        audioBase64: String,
        currentDate: LocalDate = LocalDate.now(),
        mimeType: String = "audio/wav"
    ): ParsedEventData = parseAudioInput(audioBase64, currentDate, mimeType) { /* no-op */ }

    /**
     * Parse audio with stage progress callback for UI feedback.
     */
    suspend fun parseAudioInput(
        audioBase64: String,
        currentDate: LocalDate = LocalDate.now(),
        mimeType: String = "audio/wav",
        onStageChange: (String) -> Unit
    ): ParsedEventData {
        if (audioBase64.isBlank()) {
            return ParsedEventData.empty("No audio recorded")
        }

        Log.d(TAG, "Parsing audio input (${audioBase64.length} chars base64)")

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 1: Audio → Raw Transcription (Gemini 2.0 Flash Lite)
        // ─────────────────────────────────────────────────────────────────────
        onStageChange("Transcribing your voice...")
        Log.d(TAG, "Stage 1: Transcribing audio...")

        val transcriptionResponse = AiRetryExecutor.executeWithBackup(getAudioModels()) { audioModel ->
            aiRequest {
                model(audioModel)
                systemPrompt(buildTranscriptionSystemPrompt())
                userMessageWithAudio(
                    text = "Transcribe this audio. The user is creating a calendar event. Return ONLY the transcription, nothing else.",
                    audioBase64 = audioBase64,
                    mimeType = mimeType
                )
                temperature(0.2f)
            }
        }

        val rawTranscript = when (val response = transcriptionResponse) {
            is AiResponse.Success -> {
                val transcript = response.data.trim()
                Log.d(TAG, "Stage 1 complete: Raw transcript = '$transcript'")
                transcript
            }
            is AiResponse.Error.RateLimitError -> {
                Log.w(TAG, "Stage 1: Rate limited")
                return ParsedEventData.empty("Service busy, please try again")
            }
            is AiResponse.Error.NetworkError -> {
                Log.e(TAG, "Stage 1: Network error: ${response.message}")
                return ParsedEventData.empty("No internet connection")
            }
            is AiResponse.Error -> {
                Log.e(TAG, "Stage 1: AI error: ${response.message}")
                return ParsedEventData.empty("Could not transcribe audio: ${response.message}")
            }
        }

        if (rawTranscript.isBlank()) {
            return ParsedEventData.empty("No speech detected")
        }

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 2: Raw Text → Corrected Text (TextInferenceService)
        // ─────────────────────────────────────────────────────────────────────
        onStageChange("Understanding what you said...")
        Log.d(TAG, "Stage 2: Refining transcript with TextInferenceService...")

        val inferenceResult = TextInferenceService.refineText(
            rawText = rawTranscript,
            context = InferenceContext.CALENDAR_EVENT,
            config = InferenceConfig.DEFAULT
        )

        val correctedText = inferenceResult.refinedText
        Log.d(TAG, "Stage 2 complete: Corrected = '$correctedText'")

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 3: Corrected Text → Structured Event (with paid safety net)
        // ─────────────────────────────────────────────────────────────────────
        onStageChange("Creating your event...")
        Log.d(TAG, "Stage 3: Parsing corrected text to structured event...")

        // Models to try - uses 3×3 retry via AiRetryExecutor
        val modelsToTry = getReasoningModels()
        val eventParsingPrompt = buildEventParsingPrompt(currentDate)

        return when (val result = AiRetryExecutor.execute(
            models = modelsToTry,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(eventParsingPrompt)
                    userMessage(correctedText)
                    jsonOutput()
                    temperature(0.3f)
                    if (model.hasCapability(com.boox.day.api.ai.AiCapability.REASONING)) {
                        enableReasoning()
                    }
                }
            },
            processResponse = { response, model ->
                Log.d(TAG, "Stage 3 raw response: ${response.data}")
                val parsed = parseJsonResponse(response.data, correctedText)
                Log.d(TAG, "Stage 3 parsed result: title='${parsed.title}', attendees=${parsed.attendees}")
                if (parsed.title.isNotBlank()) parsed else null
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> result.data
            is AiRetryExecutor.RetryResult.Failure -> ParsedEventData.empty(result.message)
        }
    }

    private fun buildTranscriptionSystemPrompt(): String {
        return """
You are a speech-to-text transcription system.
Transcribe the user's speech accurately and completely.

RULES:
- Output ONLY the transcription - no explanations, no formatting
- Preserve the user's exact words
- Include filler words (um, uh) only if they're significant
- Do NOT interpret or parse the content - just transcribe

OUTPUT: Plain text transcription only.
""".trimIndent()
    }

    /**
     * System prompt for Stage 3: Structured event parsing with MANDATORY INFERENCE.
     */
    private fun buildEventParsingPrompt(currentDate: LocalDate): String {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        return """
You are a calendar event parser. Extract AND INFER event details from voice input.

TODAY: ${today.format(dateFormatter)}
TOMORROW: ${tomorrow.format(dateFormatter)}
CURRENTLY VIEWING: ${currentDate.format(dateFormatter)}

## CRITICAL: MANDATORY INFERENCE

You MUST provide ALL of these fields. If not explicitly stated, INFER intelligently:

1. **title** (MANDATORY) - Short event name, 2-5 words
2. **attendees** (MANDATORY) - Extract ALL names mentioned. NEVER empty if people are mentioned.
3. **description** (MANDATORY) - ALWAYS provide. Infer purpose/context if not explicit.
4. **location** (MANDATORY if mentioned) - Any place, venue, platform
5. **durationMinutes** (MANDATORY) - Infer based on event type if not stated
6. **date** - Parse relative dates or null if truly not mentioned
7. **startHour/startMinute** - Parse time or null if not mentioned

## INFERENCE RULES - USE YOUR AI BRAIN!

### ATTENDEES - Extract ALL people mentioned:
- "with Sarah" → ["Sarah"]
- "Sarah and Bob from HR are joining" → ["Sarah", "Bob"]
- "call mom" → ["Mom"]
- "team meeting" → [] (no specific names)

### DESCRIPTION - ALWAYS INFER if not explicit:
The user NEVER needs to say the description. YOU figure it out!

Examples of INFERENCE:
- "Lunch with Sarah at Starbucks" → description: "Catch up with Sarah over lunch"
- "Meeting with John about the project" → description: "Discuss the project"
- "Dentist appointment" → description: "Dental checkup"
- "Call mom" → description: "Phone call with Mom"
- "Team standup tomorrow" → description: "Daily team sync meeting"
- "Coffee with Bob" → description: "Casual coffee catch-up with Bob"
- "Interview at 2pm" → description: "Job interview"
- "Doctor at 3pm" → description: "Medical appointment"
- "Lunch to discuss promotion and salary" → description: "Discuss promotion and salary increase"
- "Flight to NYC" → description: "Travel to New York City"

If they mention a REASON ("to discuss X", "about Y", "regarding Z", "the reason is"), use that!
If NO reason given, INFER a sensible one-liner based on event type and context.

### DURATION - INFER based on event type:
- Lunch/Dinner/Meal → 60-90 minutes
- Coffee/Quick catch-up → 30-45 minutes
- Meeting (general) → 60 minutes
- Doctor/Dentist → 60 minutes
- Call/Phone → 30 minutes
- Interview → 60 minutes
- All-day events → null (isAllDay: true)

### LOCATION - Look for these patterns:
- "at [place]" → location
- "in [place]" → location
- "on Zoom/Teams/Meet" → location
- "via [platform]" → location

## DATE/TIME PARSING

- "today" → ${today.format(dateFormatter)}
- "tomorrow" → ${tomorrow.format(dateFormatter)}
- "next Monday" → calculate from today
- "Friday" → this Friday or next Friday (whichever is future)
- "at 3pm" → startHour: 15, startMinute: 0
- "at noon" or "12 noon" → startHour: 12, startMinute: 0
- "morning" → startHour: 9 (add "startHour" to uncertainFields)
- "afternoon" → startHour: 14 (add "startHour" to uncertainFields)
- "evening" → startHour: 18 (add "startHour" to uncertainFields)

## OUTPUT FORMAT

Return ONLY raw JSON. Do NOT wrap in markdown code blocks.

{
  "title": "Short event name",
  "date": "yyyy-MM-dd or null",
  "startHour": 0-23 or null,
  "startMinute": 0-59 or null,
  "endHour": 0-23 or null,
  "endMinute": 0-59 or null,
  "durationMinutes": integer (ALWAYS provide, infer if needed),
  "isAllDay": false,
  "location": "place/venue or null",
  "description": "ALWAYS provide - infer if not explicit",
  "attendees": ["name1", "name2"] (extract ALL names mentioned),
  "confidence": 0.0-1.0,
  "uncertainFields": ["fields that were inferred"],
  "originalIntent": "Brief summary of what user said"
}

## EXAMPLE

Input: "Lunch with Sarah on Friday at noon at Starbucks. Bob and Mary from HR are also joining. The reason is to discuss her promotion and salary increase."

Output:
{
  "title": "Lunch with Sarah",
  "date": "2024-12-13",
  "startHour": 12,
  "startMinute": 0,
  "endHour": 13,
  "endMinute": 0,
  "durationMinutes": 60,
  "isAllDay": false,
  "location": "Starbucks",
  "description": "Discuss Sarah's promotion and salary increase",
  "attendees": ["Sarah", "Bob", "Mary"],
  "confidence": 0.95,
  "uncertainFields": [],
  "originalIntent": "Lunch meeting at Starbucks on Friday at noon with Sarah, Bob and Mary from HR to discuss promotion and salary"
}

Remember: The user is counting on you to be SMART. Fill in the gaps intelligently!
""".trimIndent()
    }

    /**
     * Parse the JSON response from the AI into ParsedEventData.
     * Handles null values from Gson and provides sensible defaults.
     */
    private fun parseJsonResponse(json: String, originalTranscript: String): ParsedEventData {
        val cleanJson = JsonExtractor.extract(json)
        Log.d(TAG, "Parsing JSON: $cleanJson")

        return try {
            val parsed = gson.fromJson(cleanJson, EventResponse::class.java)

            if (parsed == null) {
                Log.e(TAG, "Gson returned null for JSON: $cleanJson")
                return ParsedEventData.empty("Could not parse response").copy(
                    title = originalTranscript.take(100),
                    originalIntent = originalTranscript
                )
            }

            val title = parsed.title?.takeIf { it.isNotBlank() }

            if (title == null) {
                Log.w(TAG, "No title extracted from: $cleanJson")
                return ParsedEventData(
                    title = originalTranscript.take(100),
                    confidence = 0.2f,
                    originalIntent = "Couldn't extract details from: $originalTranscript"
                )
            }

            Log.d(TAG, "Successfully parsed - title: $title, attendees: ${parsed.attendees}, description: ${parsed.description}")

            ParsedEventData(
                title = title,
                date = parsed.date,
                startHour = parsed.startHour,
                startMinute = parsed.startMinute,
                endHour = parsed.endHour,
                endMinute = parsed.endMinute,
                durationMinutes = parsed.durationMinutes ?: 60, // Default 1 hour
                isAllDay = parsed.isAllDay ?: false,
                location = parsed.location,
                description = parsed.description ?: inferDescription(title, parsed.attendees),
                attendees = parsed.attendees ?: emptyList(),
                confidence = parsed.confidence ?: 0.7f,
                uncertainFields = parsed.uncertainFields ?: emptyList(),
                originalIntent = parsed.originalIntent ?: originalTranscript
            )
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "JSON syntax error parsing: $cleanJson", e)
            ParsedEventData.empty("Could not understand response").copy(
                title = originalTranscript.take(100),
                confidence = 0.1f,
                originalIntent = originalTranscript
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error parsing response: ${e.message}", e)
            ParsedEventData.empty("Processing error").copy(
                originalIntent = originalTranscript
            )
        }
    }

    /**
     * Fallback description inference if AI didn't provide one.
     */
    private fun inferDescription(title: String, attendees: List<String>?): String {
        val titleLower = title.lowercase()

        return when {
            titleLower.contains("lunch") -> {
                if (!attendees.isNullOrEmpty()) "Lunch with ${attendees.joinToString(", ")}"
                else "Lunch meeting"
            }
            titleLower.contains("dinner") -> {
                if (!attendees.isNullOrEmpty()) "Dinner with ${attendees.joinToString(", ")}"
                else "Dinner"
            }
            titleLower.contains("coffee") -> {
                if (!attendees.isNullOrEmpty()) "Coffee catch-up with ${attendees.joinToString(", ")}"
                else "Coffee meeting"
            }
            titleLower.contains("meeting") -> {
                if (!attendees.isNullOrEmpty()) "Meeting with ${attendees.joinToString(", ")}"
                else "Meeting"
            }
            titleLower.contains("call") -> {
                if (!attendees.isNullOrEmpty()) "Phone call with ${attendees.joinToString(", ")}"
                else "Phone call"
            }
            titleLower.contains("dentist") -> "Dental appointment"
            titleLower.contains("doctor") -> "Medical appointment"
            titleLower.contains("interview") -> "Job interview"
            titleLower.contains("standup") || titleLower.contains("stand-up") -> "Daily team sync"
            !attendees.isNullOrEmpty() -> "Meeting with ${attendees.joinToString(", ")}"
            else -> "Calendar event"
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NEW UNIFIED API - Used by TranscriptionService integration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parse pre-transcribed text into structured event data.
     *
     * This is the NEW primary API for TranscriptionService integration.
     * Skips Stage 1 (audio transcription) since TranscriptionService already handled it.
     *
     * Pipeline:
     * - Stage 1: SKIPPED (text already transcribed by TranscriptionService)
     * - Stage 2: TextInferenceService (semantic correction)
     * - Stage 3: Structured JSON parsing (GPT models with fallback)
     *
     * @param transcribedText Pre-transcribed text from SpeechRecognizer or Gemini
     * @param currentDate The date currently being viewed in the calendar
     * @param onStageChange Callback for UI progress feedback
     * @return ParsedEventData with extracted fields and confidence score
     */
    suspend fun parseTranscribedText(
        transcribedText: String,
        currentDate: LocalDate = LocalDate.now(),
        onStageChange: (String) -> Unit = {}
    ): ParsedEventData {
        if (transcribedText.isBlank()) {
            return ParsedEventData.empty("No speech detected")
        }

        Log.d(TAG, "parseTranscribedText: '${transcribedText.take(100)}...'")

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 1: SKIPPED - TranscriptionService already transcribed
        // ─────────────────────────────────────────────────────────────────────

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 2: Raw Text → Corrected Text (TextInferenceService)
        // ─────────────────────────────────────────────────────────────────────
        onStageChange("Understanding what you said...")
        Log.d(TAG, "Stage 2: Refining transcript with TextInferenceService...")

        val inferenceResult = TextInferenceService.refineText(
            rawText = transcribedText,
            context = InferenceContext.CALENDAR_EVENT,
            config = InferenceConfig.DEFAULT
        )

        val correctedText = inferenceResult.refinedText
        Log.d(TAG, "Stage 2 complete: Corrected = '$correctedText'")

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 3: Corrected Text → Structured Event (with paid safety net)
        // ─────────────────────────────────────────────────────────────────────
        onStageChange("Creating your event...")
        Log.d(TAG, "Stage 3: Parsing corrected text to structured event...")

        val modelsToTry = getReasoningModels()
        val eventParsingPrompt = buildEventParsingPrompt(currentDate)

        return when (val result = AiRetryExecutor.execute(
            models = modelsToTry,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(eventParsingPrompt)
                    userMessage(correctedText)
                    jsonOutput()
                    temperature(0.3f)
                    if (model.hasCapability(com.boox.day.api.ai.AiCapability.REASONING)) {
                        enableReasoning()
                    }
                }
            },
            processResponse = { response, model ->
                Log.d(TAG, "Stage 3 raw response: ${response.data}")
                val parsed = parseJsonResponse(response.data, correctedText)
                Log.d(TAG, "Stage 3 parsed: title='${parsed.title}'")
                if (parsed.title.isNotBlank()) parsed else null
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> result.data
            is AiRetryExecutor.RetryResult.Failure -> ParsedEventData.empty(result.message)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LEGACY API - Kept for backward compatibility, consider deprecating
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parse a text transcript into structured event data (for pre-transcribed text).
     * @deprecated Use [parseTranscribedText] instead for new code.
     */
    suspend fun parseVoiceInput(
        transcript: String,
        currentDate: LocalDate = LocalDate.now()
    ): ParsedEventData {
        if (transcript.isBlank()) {
            return ParsedEventData.empty("No speech detected")
        }

        Log.d(TAG, "Text mode - Stage 1: Refining transcript...")
        val inferenceResult = TextInferenceService.refineText(
            rawText = transcript,
            context = InferenceContext.CALENDAR_EVENT,
            config = InferenceConfig.DEFAULT
        )
        val correctedText = inferenceResult.refinedText
        Log.d(TAG, "Stage 1 complete: '$correctedText'")

        Log.d(TAG, "Text mode - Stage 2: Parsing to structured event...")

        // Uses 3×3 retry via AiRetryExecutor
        val modelsToTry = getReasoningModels()
        val eventParsingPrompt = buildEventParsingPrompt(currentDate)

        return when (val result = AiRetryExecutor.execute(
            models = modelsToTry,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(eventParsingPrompt)
                    userMessage(correctedText)
                    jsonOutput()
                    temperature(0.3f)
                    if (model.hasCapability(com.boox.day.api.ai.AiCapability.REASONING)) {
                        enableReasoning()
                    }
                }
            },
            processResponse = { response, _ ->
                val parsed = parseJsonResponse(response.data, correctedText)
                if (parsed.title.isNotBlank()) parsed else null
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> result.data
            is AiRetryExecutor.RetryResult.Failure -> {
                ParsedEventData.empty(result.message).copy(originalIntent = correctedText)
            }
        }
    }
}
