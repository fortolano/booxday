package com.boox.day.ai

import android.util.Log
import com.boox.day.api.ModelConfigProvider
import com.boox.day.api.ai.AiCapability
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.AiProblem
import com.boox.day.api.ai.AiResponse
import com.boox.day.api.ai.aiRequest
import com.boox.day.data.ai.AiPreferences
import com.boox.day.data.ai.EnergyPattern
import com.boox.day.data.ai.Industry
import com.boox.day.data.ai.Interest
import com.boox.day.data.ai.LifeSituation
import com.boox.day.data.ai.LivingEnvironment
import com.boox.day.data.ai.Profession
import com.boox.day.data.ai.SuggestionCount
import com.boox.day.data.ai.SuggestionStyle
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName

/**
 * AI-powered parser for converting voice input to structured AI Preferences.
 *
 * Uses a **three-stage pipeline** (matching TaskAiService's proven pattern):
 * 1. **Stage 1 (Gemini 2.0 Flash Lite)**: Audio → raw transcription
 * 2. **Stage 2 (TextInferenceService)**: Raw text → corrected/inferred text
 * 3. **Stage 3 (GPT-OSS-120B → fallback)**: Corrected text → structured AiPreferences
 *
 * Allows users to describe themselves naturally:
 * - "I'm a working professional with two kids. Health is my top priority but I also care
 *    about career growth. I'm a morning person, live in the suburbs, and love hiking."
 *
 * The AI infers:
 * - Life situation: WORKING_PROFESSIONAL
 * - hasKids: true
 * - healthPriority: 5, workPriority: 4
 * - energyPattern: EARLY_BIRD
 * - livingEnvironment: SUBURBAN
 * - interests: [HIKING, FITNESS]
 */
object VoicePreferencesParser {
    private const val TAG = "VoicePreferencesParser"
    private val gson = Gson()

    private fun getReasoningModels(): List<AiModel> =
        ModelConfigProvider.getReasoningFallbackChain()

    /** The voice job's models: the user's pick, then BooxDay's choice (a backup since Oct 2026) */
    private fun getAudioModels(): List<AiModel> =
        ModelConfigProvider.getAudioFallbackChain()

    /**
     * Result of voice preferences parsing.
     */
    data class ParseResult(
        val preferences: AiPreferences?,
        val confidence: Float,
        val originalTranscript: String,
        val inferredFields: List<String>,
        val error: String? = null
    ) {
        val isSuccess: Boolean get() = preferences != null && error == null

        companion object {
            fun empty(error: String) = ParseResult(
                preferences = null,
                confidence = 0f,
                originalTranscript = "",
                inferredFields = emptyList(),
                error = error
            )
        }
    }

    /**
     * JSON structure expected from LLM for preferences parsing.
     * Uses nullable types with defaults to handle Gson's null behavior.
     */
    private data class PreferencesResponse(
        // Life Priorities (1-5 scale)
        @SerializedName("workPriority") val workPriority: Int? = null,
        @SerializedName("educationPriority") val educationPriority: Int? = null,
        @SerializedName("familyPriority") val familyPriority: Int? = null,
        @SerializedName("healthPriority") val healthPriority: Int? = null,
        @SerializedName("socialPriority") val socialPriority: Int? = null,
        @SerializedName("hobbyPriority") val hobbyPriority: Int? = null,
        @SerializedName("financePriority") val financePriority: Int? = null,

        // About Me
        @SerializedName("lifeSituation") val lifeSituation: String? = null,
        @SerializedName("energyPattern") val energyPattern: String? = null,
        @SerializedName("livingEnvironment") val livingEnvironment: String? = null,
        @SerializedName("industry") val industry: String? = null,
        @SerializedName("profession") val profession: String? = null,

        // Lifestyle vibes
        @SerializedName("isActiveLifestyle") val isActiveLifestyle: Boolean? = null,
        @SerializedName("isWeekendWarrior") val isWeekendWarrior: Boolean? = null,
        @SerializedName("isHomebody") val isHomebody: Boolean? = null,
        @SerializedName("isSocialButterfly") val isSocialButterfly: Boolean? = null,
        @SerializedName("isQuietIntrovert") val isQuietIntrovert: Boolean? = null,
        @SerializedName("isOutdoorsLover") val isOutdoorsLover: Boolean? = null,
        @SerializedName("isFoodie") val isFoodie: Boolean? = null,
        @SerializedName("isDigitalNative") val isDigitalNative: Boolean? = null,

        // Situation flags
        @SerializedName("hasKids") val hasKids: Boolean? = null,
        @SerializedName("hasPets") val hasPets: Boolean? = null,
        @SerializedName("isCareGiver") val isCareGiver: Boolean? = null,
        @SerializedName("livesAlone") val livesAlone: Boolean? = null,

        // Interests
        @SerializedName("interests") val interests: List<String>? = null,

        // AI Behavior
        @SerializedName("suggestionCount") val suggestionCount: String? = null,
        @SerializedName("suggestionStyle") val suggestionStyle: String? = null,

        // Meta
        @SerializedName("confidence") val confidence: Float? = null,
        @SerializedName("inferredFields") val inferredFields: List<String>? = null
    )

    /**
     * Parse audio directly into structured preferences using a three-stage pipeline.
     *
     * @param audioBase64 Base64-encoded WAV audio data
     * @param mimeType Audio MIME type (default: "audio/wav")
     * @return ParseResult with extracted preferences
     */
    suspend fun parseAudioInput(
        audioBase64: String,
        mimeType: String = "audio/wav"
    ): ParseResult = parseAudioInput(audioBase64, mimeType) { /* no-op */ }

    /**
     * Parse audio with stage progress callback for UI feedback.
     */
    suspend fun parseAudioInput(
        audioBase64: String,
        mimeType: String = "audio/wav",
        onStageChange: (String) -> Unit
    ): ParseResult {
        if (audioBase64.isBlank()) {
            return ParseResult.empty("No audio recorded")
        }

        Log.d(TAG, "Parsing audio input (${audioBase64.length} chars base64)")

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 1: Audio → Raw Transcription (Gemini 2.0 Flash Lite)
        // ─────────────────────────────────────────────────────────────────────
        onStageChange("Listening to you...")
        Log.d(TAG, "Stage 1: Transcribing audio...")

        val transcriptionResponse = AiRetryExecutor.executeWithBackup(getAudioModels()) { audioModel ->
            aiRequest {
                model(audioModel)
                systemPrompt(buildTranscriptionSystemPrompt())
                userMessageWithAudio(
                    text = "Transcribe this audio. The user is describing themselves and their preferences. Return ONLY the transcription, nothing else.",
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
            // What went wrong in plain words (Oct 2026: OpenRouter timeouts said "No internet
            // connection", other errors showed OpenRouter's raw HTTP answer)
            is AiResponse.Error -> {
                Log.e(TAG, "Stage 1: AI error: ${response.message}")
                return ParseResult.empty(AiProblem.messageFor(response))
            }
        }

        if (rawTranscript.isBlank()) {
            return ParseResult.empty("No speech detected")
        }

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 2: Raw Text → Corrected Text (TextInferenceService)
        // ─────────────────────────────────────────────────────────────────────
        onStageChange("Understanding what you said...")
        Log.d(TAG, "Stage 2: Refining transcript with TextInferenceService...")

        val inferenceResult = TextInferenceService.refineText(
            rawText = rawTranscript,
            context = InferenceContext.GENERAL,
            config = InferenceConfig.DEFAULT
        )

        val correctedText = inferenceResult.refinedText
        Log.d(TAG, "Stage 2 complete: Corrected = '$correctedText'")

        // ─────────────────────────────────────────────────────────────────────
        // STAGE 3: Corrected Text → Structured Preferences (with fallback)
        // ─────────────────────────────────────────────────────────────────────
        onStageChange("Learning about you...")
        Log.d(TAG, "Stage 3: Parsing corrected text to structured preferences...")

        // Uses 3×3 retry via AiRetryExecutor
        val modelsToTry = getReasoningModels()
        val preferencesPrompt = buildPreferencesParsingPrompt()

        return when (val result = AiRetryExecutor.execute(
            models = modelsToTry,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(preferencesPrompt)
                    userMessage(correctedText)
                    jsonOutput()
                    temperature(0.3f)
                    if (model.hasCapability(AiCapability.REASONING)) {
                        enableReasoning()
                    }
                }
            },
            processResponse = { response, model ->
                Log.d(TAG, "Stage 3 raw response: ${response.data}")
                val parsed = parseJsonResponse(response.data, correctedText)
                if (parsed.isSuccess) parsed else null
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> result.data
            is AiRetryExecutor.RetryResult.Failure -> ParseResult.empty(result.message)
        }
    }

    /**
     * Parse a text description into structured preferences (for pre-transcribed text).
     */
    suspend fun parseTextInput(
        text: String,
        onStageChange: (String) -> Unit = {}
    ): ParseResult {
        if (text.isBlank()) {
            return ParseResult.empty("No text provided")
        }

        Log.d(TAG, "Text mode - parsing preferences from: ${text.take(100)}...")

        // Skip Stage 1 (transcription) - go directly to inference
        onStageChange("Understanding your preferences...")
        Log.d(TAG, "Stage 2: Refining text...")

        val inferenceResult = TextInferenceService.refineText(
            rawText = text,
            context = InferenceContext.GENERAL,
            config = InferenceConfig.FAST
        )
        val correctedText = inferenceResult.refinedText

        // Stage 3: Parse to structured - uses 3×3 retry via AiRetryExecutor
        onStageChange("Learning about you...")
        Log.d(TAG, "Stage 3: Parsing to structured preferences...")

        val modelsToTry = getReasoningModels()
        val preferencesPrompt = buildPreferencesParsingPrompt()

        return when (val result = AiRetryExecutor.execute(
            models = modelsToTry,
            buildRequest = { model ->
                aiRequest {
                    model(model)
                    systemPrompt(preferencesPrompt)
                    userMessage(correctedText)
                    jsonOutput()
                    temperature(0.3f)
                    if (model.hasCapability(AiCapability.REASONING)) {
                        enableReasoning()
                    }
                }
            },
            processResponse = { response, _ ->
                val parsed = parseJsonResponse(response.data, correctedText)
                if (parsed.isSuccess) parsed else null
            }
        )) {
            is AiRetryExecutor.RetryResult.Success -> result.data
            is AiRetryExecutor.RetryResult.Failure -> ParseResult.empty(result.message)
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
     * System prompt for Stage 3: Structured preferences parsing.
     */
    private fun buildPreferencesParsingPrompt(): String {
        return """
You are an AI preferences parser. Extract AND INFER user preferences from their self-description.

## YOUR TASK

The user is describing themselves, their lifestyle, priorities, and preferences. Your job is to extract structured preferences that will help personalize AI suggestions.

## EXTRACTION RULES

### PRIORITIES (1-5 scale, 3 = neutral)
Extract how important each area is to the user:
- workPriority: Career, professional growth, work tasks
- educationPriority: Learning, studying, courses, degrees
- familyPriority: Family time, kids, relatives
- healthPriority: Exercise, diet, wellness, medical
- socialPriority: Friends, socializing, networking
- hobbyPriority: Personal interests, creative pursuits
- financePriority: Money, budgeting, investments

Examples:
- "Health is my top priority" → healthPriority: 5
- "I'm very career focused" → workPriority: 5
- "Family comes first" → familyPriority: 5
- "I don't care much about..." → that priority: 1-2

### LIFE SITUATION
- STUDENT: In school, college, university
- WORKING_PROFESSIONAL: Employed, career-focused
- ENTREPRENEUR: Own business, startup founder
- FREELANCER: Self-employed, contract work
- STAY_AT_HOME_PARENT: Primary caregiver
- RETIRED: No longer working
- CAREER_TRANSITION: Between jobs, changing fields

### ENERGY PATTERN
- EARLY_BIRD: Morning person, "I'm most productive in the morning"
- NIGHT_OWL: Evening person, "I work best at night"
- STEADY: Consistent energy throughout day
- VARIABLE: Energy depends on day/context

### LIVING ENVIRONMENT
- URBAN: City dweller
- SUBURBAN: Suburbs, residential area
- RURAL: Countryside, small town
- REMOTE: Works/lives remotely, digital nomad

### INDUSTRY
- TECHNOLOGY
- HEALTHCARE
- EDUCATION
- FINANCE
- GOVERNMENT
- RETAIL
- MANUFACTURING
- CONSTRUCTION_REAL_ESTATE
- MEDIA
- PROFESSIONAL_SERVICES
- HOSPITALITY
- NONPROFIT
- OTHER

### PROFESSION
- ENGINEERING_IT
- PRODUCT_PROJECT
- DESIGN_CREATIVE
- MARKETING_COMMUNICATIONS
- SALES_BUSINESS_DEV
- OPERATIONS_ADMIN
- FINANCE_ACCOUNTING
- HR_PEOPLE_OPS
- HEALTHCARE_CLINICAL
- EDUCATION_RESEARCH
- LEGAL_COMPLIANCE
- ENTREPRENEUR
- STUDENT
- OTHER

### LIFESTYLE FLAGS (boolean)
- isActiveLifestyle: Exercises regularly, fitness-focused
- isWeekendWarrior: Active mainly on weekends
- isHomebody: Prefers staying home
- isSocialButterfly: Very social, loves gatherings
- isQuietIntrovert: Prefers quiet, alone time
- isOutdoorsLover: Hiking, camping, nature
- isFoodie: Food enthusiast, cooking, dining
- isDigitalNative: Tech-savvy, gadget lover

### SITUATION FLAGS (boolean)
- hasKids: Has children
- hasPets: Has pets (dogs, cats, etc.)
- isCareGiver: Takes care of elderly/sick family
- livesAlone: Lives alone

### INTERESTS
Extract mentioned hobbies/interests. Valid values:
READING, WRITING, MUSIC, ART, PHOTOGRAPHY, COOKING, BAKING, GARDENING,
HIKING, CYCLING, RUNNING, SWIMMING, YOGA, GYM, TEAM_SPORTS, GOLF, TENNIS,
GAMING, MOVIES, TV_SHOWS, PODCASTS, TRAVEL, CAMPING, FISHING, DIY,
WOODWORKING, CODING, TECH, INVESTING, FASHION, BEAUTY, PETS, VOLUNTEERING,
MEDITATION, CRAFTS, COLLECTING, DANCING, MARTIAL_ARTS, BOARD_GAMES

### AI BEHAVIOR (optional)
- suggestionCount: MINIMAL (3), MODERATE (5-7), COMPREHENSIVE (10+)
- suggestionStyle: PRACTICAL (action-focused), BALANCED (mix), ASPIRATIONAL (growth-focused)

## OUTPUT FORMAT

Return ONLY raw JSON. Do NOT wrap in markdown code blocks.

{
  "workPriority": 3,
  "educationPriority": 3,
  "familyPriority": 3,
  "healthPriority": 3,
  "socialPriority": 3,
  "hobbyPriority": 3,
  "financePriority": 3,
  "lifeSituation": "WORKING_PROFESSIONAL",
  "energyPattern": "STEADY",
  "livingEnvironment": "SUBURBAN",
  "industry": "TECHNOLOGY",
  "profession": "ENGINEERING_IT",
  "isActiveLifestyle": false,
  "isWeekendWarrior": false,
  "isHomebody": false,
  "isSocialButterfly": false,
  "isQuietIntrovert": false,
  "isOutdoorsLover": false,
  "isFoodie": false,
  "isDigitalNative": false,
  "hasKids": false,
  "hasPets": false,
  "isCareGiver": false,
  "livesAlone": false,
  "interests": ["HIKING", "READING"],
  "suggestionCount": "MODERATE",
  "suggestionStyle": "BALANCED",
  "confidence": 0.85,
  "inferredFields": ["livingEnvironment", "socialPriority"]
}

## INFERENCE RULES

- Only include fields explicitly mentioned OR strongly implied
- Use null for fields that cannot be determined
- List fields that were inferred (not explicitly stated) in "inferredFields"
- Confidence: 0.9+ for explicit statements, 0.7-0.9 for strong inference, 0.5-0.7 for weak inference

## EXAMPLE

Input: "I'm a software developer with two young kids. I try to exercise in the mornings before work. On weekends I love hiking with my family. We live in the suburbs and I'm definitely more of a homebody during the week."

Output:
{
  "workPriority": 4,
  "familyPriority": 5,
  "healthPriority": 4,
  "lifeSituation": "WORKING_PROFESSIONAL",
  "energyPattern": "EARLY_BIRD",
  "livingEnvironment": "SUBURBAN",
  "industry": "TECHNOLOGY",
  "profession": "ENGINEERING_IT",
  "isActiveLifestyle": true,
  "isWeekendWarrior": true,
  "isHomebody": true,
  "isOutdoorsLover": true,
  "isDigitalNative": true,
  "hasKids": true,
  "interests": ["HIKING", "GYM", "CODING"],
  "confidence": 0.9,
  "inferredFields": ["isDigitalNative", "workPriority"]
}
""".trimIndent()
    }

    /**
     * Parse the JSON response into ParseResult.
     */
    private fun parseJsonResponse(json: String, originalTranscript: String): ParseResult {
        val cleanJson = JsonExtractor.extract(json)
        Log.d(TAG, "Parsing JSON: $cleanJson")

        return try {
            val parsed = gson.fromJson(cleanJson, PreferencesResponse::class.java)

            if (parsed == null) {
                // Length only (Oct 2026): the answer is what the user said about themselves, and
                // error lines stay in release builds and in crash reports
                Log.e(TAG, "Gson returned null for JSON (${cleanJson.length} chars)")
                return ParseResult.empty("Could not parse response")
            }

            // Build AiPreferences from parsed response
            val preferences = AiPreferences(
                // Priorities (default to 3 if not specified)
                workPriority = parsed.workPriority?.coerceIn(1, 5) ?: 3,
                educationPriority = parsed.educationPriority?.coerceIn(1, 5) ?: 3,
                familyPriority = parsed.familyPriority?.coerceIn(1, 5) ?: 3,
                healthPriority = parsed.healthPriority?.coerceIn(1, 5) ?: 3,
                socialPriority = parsed.socialPriority?.coerceIn(1, 5) ?: 3,
                hobbyPriority = parsed.hobbyPriority?.coerceIn(1, 5) ?: 3,
                financePriority = parsed.financePriority?.coerceIn(1, 5) ?: 3,

                // About Me
                lifeSituations = parseLifeSituations(parsed.lifeSituation),
                energyPattern = parseEnergyPattern(parsed.energyPattern),
                livingEnvironment = parseLivingEnvironment(parsed.livingEnvironment),
                industry = parseIndustry(parsed.industry),
                profession = parseProfession(parsed.profession),

                // Lifestyle flags
                isActiveLifestyle = parsed.isActiveLifestyle ?: false,
                isWeekendWarrior = parsed.isWeekendWarrior ?: false,
                isHomebody = parsed.isHomebody ?: false,
                isSocialButterfly = parsed.isSocialButterfly ?: false,
                isQuietIntrovert = parsed.isQuietIntrovert ?: false,
                isOutdoorsLover = parsed.isOutdoorsLover ?: false,
                isFoodie = parsed.isFoodie ?: false,
                isDigitalNative = parsed.isDigitalNative ?: false,

                // Situation flags
                hasKids = parsed.hasKids ?: false,
                hasPets = parsed.hasPets ?: false,
                isCareGiver = parsed.isCareGiver ?: false,
                livesAlone = parsed.livesAlone ?: false,

                // Interests
                interests = parseInterests(parsed.interests),

                // AI Behavior
                suggestionCount = parseSuggestionCount(parsed.suggestionCount),
                suggestionStyle = parseSuggestionStyle(parsed.suggestionStyle),

                // Meta
                setupComplete = true,
                lastUpdated = System.currentTimeMillis()
            )

            Log.d(TAG, "Successfully parsed preferences: $preferences")

            ParseResult(
                preferences = preferences,
                confidence = parsed.confidence ?: 0.7f,
                originalTranscript = originalTranscript,
                inferredFields = parsed.inferredFields ?: emptyList()
            )
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "JSON syntax error parsing (${cleanJson.length} chars)", e)
            ParseResult.empty("Could not understand response")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error parsing response: ${e.message}", e)
            ParseResult.empty("Processing error")
        }
    }

    private fun parseLifeSituations(value: String?): Set<LifeSituation> {
        if (value == null) return setOf(LifeSituation.WORKING_PROFESSIONAL)
        return try {
            setOf(LifeSituation.valueOf(value.uppercase().replace(" ", "_")))
        } catch (e: IllegalArgumentException) {
            setOf(LifeSituation.WORKING_PROFESSIONAL)
        }
    }

    private fun parseEnergyPattern(value: String?): EnergyPattern {
        if (value == null) return EnergyPattern.STEADY
        return try {
            EnergyPattern.valueOf(value.uppercase().replace(" ", "_"))
        } catch (e: IllegalArgumentException) {
            EnergyPattern.STEADY
        }
    }

    private fun parseLivingEnvironment(value: String?): LivingEnvironment {
        if (value == null) return LivingEnvironment.SUBURBAN
        return try {
            LivingEnvironment.valueOf(value.uppercase().replace(" ", "_"))
        } catch (e: IllegalArgumentException) {
            LivingEnvironment.SUBURBAN
        }
    }

    private fun parseIndustry(value: String?): Industry {
        if (value == null) return Industry.NOT_SET
        return try {
            Industry.valueOf(value.uppercase().replace(" ", "_"))
        } catch (e: IllegalArgumentException) {
            Industry.NOT_SET
        }
    }

    private fun parseProfession(value: String?): Profession {
        if (value == null) return Profession.NOT_SET
        return try {
            Profession.valueOf(value.uppercase().replace(" ", "_"))
        } catch (e: IllegalArgumentException) {
            Profession.NOT_SET
        }
    }

    private fun parseInterests(values: List<String>?): Set<Interest> {
        if (values.isNullOrEmpty()) return emptySet()
        return values.mapNotNull { value ->
            try {
                Interest.valueOf(value.uppercase().replace(" ", "_"))
            } catch (e: IllegalArgumentException) {
                null
            }
        }.toSet()
    }

    private fun parseSuggestionCount(value: String?): SuggestionCount {
        if (value == null) return SuggestionCount.MODERATE
        return try {
            SuggestionCount.valueOf(value.uppercase())
        } catch (e: IllegalArgumentException) {
            SuggestionCount.MODERATE
        }
    }

    private fun parseSuggestionStyle(value: String?): SuggestionStyle {
        if (value == null) return SuggestionStyle.BALANCED
        return try {
            SuggestionStyle.valueOf(value.uppercase())
        } catch (e: IllegalArgumentException) {
            SuggestionStyle.BALANCED
        }
    }
}
