# Boox Day AI Integration Guide

> **Last Verified**: December 2025
> **Covers Files**: AiClient.kt, all feature services in ai/, TranscriptionService.kt, AudioRecorder.kt
> **API**: OpenRouter.ai (multi-model aggregator)

---

## Overview

Boox Day integrates AI for:
- **Handwriting recognition** (OCR from canvas ink)
- **Voice-to-text** (audio transcription)
- **Smart event parsing** (natural language → structured calendar event)
- **Task parsing** (voice/text → task items)
- **Text inference** (semantic correction of transcribed text)

**Architecture**:
```
Feature Services (HandwritingService, VoiceEventParser, TaskAiService)
    ↓
AiClient (unified request builder)
    ↓
OpenRouter API (multi-model aggregator)
    ↓
Models (Gemini, Nova, GPT-OSS, etc.)
    ↓
TextInferenceService (semantic correction layer - optional)
```

---

## OpenRouter Integration

### Why OpenRouter?

**Benefits**:
- Single API for 50+ models (Gemini, GPT, Claude, Nova, etc.)
- Free tier with generous limits
- Automatic fallback if model unavailable
- Unified billing across providers

**Website**: https://openrouter.ai

**Pricing**: https://openrouter.ai/docs/pricing

---

### Authentication

**API Key Storage**:
```kotlin
// Stored in GlobalAppSettings
val settings = GlobalAppSettings.current
val apiKey = settings.openRouterApiKey  // String?

// Set API key (user enters in settings)
val newSettings = settings.copy(openRouterApiKey = "sk-or-v1-...")
appRepository.kvProxy.setAppSettings(newSettings)
```

**API Key Format**: `sk-or-v1-...` (OpenRouter standard)

**No Key Provided**: Features gracefully degrade (show "API key required" message).

---

## Available Models

### Model Reference

| Model | ID | Capabilities | Speed | Cost | Best For |
|-------|-----|--------------|-------|------|----------|
| **Gemini 2.0 Flash Exp** | `google/gemini-2.0-flash-exp:free` | Vision, JSON | 1.85s | Free | Handwriting OCR, fast parsing |
| **Nova 2 Lite** | `amazon/nova-2-lite-v1:free` | Vision, Reasoning | 3.28s | Free | Reliable OCR, complex extraction |
| **GPT OSS 20B** | `openai/gpt-oss-20b:free` | Text, JSON, Reasoning | 3.42s | Free | Structured parsing, JSON output |
| **Gemini 2.0 Flash Lite** | `google/gemini-2.0-flash-lite-001` | Vision, **AUDIO** | ~2s | **PAID** | Voice transcription (ONLY model with audio) |

**Note**: Only Gemini 2.0 Flash Lite supports audio input. Required for voice features unless using device SpeechRecognizer.

**Benchmark Data**: See `docs/archive/ai-models.md` for detailed speed/quality comparisons.

---

### Model Capabilities

```kotlin
enum class Capability {
    VISION,         // Can process images
    AUDIO,          // Can process audio (only Gemini Flash Lite)
    JSON,           // Structured JSON output
    REASONING,      // Chain-of-thought reasoning
    STREAMING       // Real-time response streaming
}

// Check capabilities
if (ModelId.GEMINI_FLASH_LITE.capabilities().contains(Capability.AUDIO)) {
    // Use for voice
}
```

**Location**: `app/src/main/java/com/boox/day/api/ai/Models.kt`

---

## AiClient Usage

### Basic Request

```kotlin
val result = aiRequest {
    model = ModelId.GEMINI_FLASH_EXP
    prompt = "Extract text from this image"
    image = bitmap
    temperature = 0.3  // Lower = more deterministic
    maxTokens = 1000
}

when (result) {
    is AiResult.Success -> {
        val text = result.content
        Log.i(TAG, "Extracted: $text")
    }
    is AiResult.Error -> {
        Log.e(TAG, "AI error: ${result.message}")
    }
}
```

### JSON Output

```kotlin
val result = aiRequest {
    model = ModelId.GPT_OSS_20B
    prompt = """
        Parse this into JSON:
        {
          "title": "Meeting title",
          "startTime": "2025-01-15T14:00:00Z",
          "endTime": "2025-01-15T15:00:00Z"
        }

        Input: "Team meeting tomorrow at 2pm for 1 hour"
    """.trimIndent()
    jsonMode = true  // Force JSON output
}

// Parse result
val json = Json.decodeFromString<CalendarEventJson>(result.content)
```

### With Image (Vision)

```kotlin
// Capture canvas region as bitmap
val bitmap = captureCanvasBitmap(x, y, width, height)

val result = aiRequest {
    model = ModelId.GEMINI_FLASH_EXP
    prompt = "Read the handwritten text in this image"
    image = bitmap
}
```

### With Audio

```kotlin
// Record audio
val audioBytes = audioRecorder.getWavBytes()  // PCM → WAV
val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

val result = aiRequest {
    model = ModelId.GEMINI_FLASH_LITE  // ONLY model with audio
    prompt = "Transcribe this audio"
    audioData = base64Audio
    audioFormat = "wav"
}
```

**Location**: `app/src/main/java/com/boox/day/api/ai/AiClient.kt`

---

## Feature Services

### 1. HandwritingService (OCR)

**Purpose**: Extract text from canvas ink strokes.

**Flow**:
```
User draws text on canvas
    ↓
User selects "Recognize Text"
    ↓
HandwritingService.recognizeHandwriting(bitmap)
    ↓
AiClient → Gemini Flash Exp (vision)
    ↓
TextInferenceService.refineText() [optional]
    ↓
Return recognized text
```

**Usage**:
```kotlin
suspend fun recognizeHandwriting(
    bitmap: Bitmap,
    context: InferenceContext = InferenceContext.NOTE
): HandwritingResult {
    // 1. OCR via AI
    val rawText = aiRequest {
        model = ModelId.GEMINI_FLASH_EXP
        prompt = "Extract handwritten text from this image..."
        image = bitmap
    }

    // 2. Semantic refinement
    val refined = TextInferenceService.refineText(
        rawText = rawText,
        context = context
    )

    return HandwritingResult(
        text = refined.refinedText,
        confidence = refined.confidence
    )
}
```

**Location**: `app/src/main/java/com/boox/day/ai/HandwritingService.kt`

---

### 2. VoiceEventParser

**Purpose**: Parse voice input into calendar event.

**Flow**:
```
User taps voice button
    ↓
TranscriptionService.startTranscribing()
    ↓ (Auto-detect: SpeechRecognizer OR Gemini)
User speaks: "Meeting with John tomorrow at 2pm"
    ↓
TranscriptionService returns transcript
    ↓
VoiceEventParser.parseTranscribedText(transcript)
    ↓
AiClient → GPT-OSS-20B (JSON structured parsing)
    ↓
Return CalendarEventJson
```

**Usage**:
```kotlin
suspend fun parseTranscribedText(
    transcript: String
): Result<CalendarEventJson> {
    // 1. Refine transcript (optional but recommended)
    val refined = TextInferenceService.refineText(
        rawText = transcript,
        context = InferenceContext.CALENDAR_EVENT
    )

    // 2. Structured parsing
    val result = aiRequest {
        model = ModelId.GPT_OSS_20B
        prompt = """
            Parse this into a calendar event (JSON):
            {
              "title": string,
              "startTime": ISO8601,
              "endTime": ISO8601,
              "location": string? (optional),
              "description": string? (optional)
            }

            Input: "${refined.refinedText}"
        """.trimIndent()
        jsonMode = true
    }

    return Result.success(Json.decodeFromString(result.content))
}
```

**Location**: `app/src/main/java/com/boox/day/ai/VoiceEventParser.kt`

---

### 3. TaskAiService

**Purpose**: Parse voice/text into task items.

**Flow**:
```
User speaks: "Buy milk, call dentist, finish report"
    ↓
TranscriptionService → transcript
    ↓
TaskAiService.parseTranscribedText(transcript)
    ↓
AiClient → GPT-OSS-20B (JSON list parsing)
    ↓
Return List<TaskJson>
```

**Usage**:
```kotlin
suspend fun parseTranscribedText(
    transcript: String
): Result<List<TaskJson>> {
    val refined = TextInferenceService.refineText(
        rawText = transcript,
        context = InferenceContext.TASK
    )

    val result = aiRequest {
        model = ModelId.GPT_OSS_20B
        prompt = """
            Parse this into a list of tasks (JSON):
            [
              {
                "title": string,
                "priority": 0-3 (0=none, 1=low, 2=med, 3=high),
                "dueDate": ISO8601? (optional)
              }
            ]

            Input: "${refined.refinedText}"
        """.trimIndent()
        jsonMode = true
    }

    return Result.success(Json.decodeFromString(result.content))
}
```

**Location**: `app/src/main/java/com/boox/day/ai/TaskAiService.kt`

---

## TextInferenceService

**Purpose**: Semantic correction of AI transcriptions.

**Problem**: Voice/OCR transcriptions often have errors:
- "Mooting with JO&un" → "Meeting with John"
- "Buy mill" → "Buy milk"
- "Coll dentist" → "Call dentist"

**Solution**: AI-powered semantic correction using context.

### Usage

```kotlin
val result = TextInferenceService.refineText(
    rawText = "Mooting with JO&un tomorrow",
    context = InferenceContext.CALENDAR_EVENT
)

result.refinedText      // "Meeting with John tomorrow"
result.confidence       // 0.95 (high confidence)
result.wasModified      // true
```

### Inference Contexts

```kotlin
enum class InferenceContext(val systemPrompt: String) {
    CALENDAR_EVENT(
        "You are correcting calendar event transcriptions. " +
        "Common terms: meeting, appointment, call, lunch, dinner, etc."
    ),
    TASK(
        "You are correcting task transcriptions. " +
        "Common terms: buy, call, finish, send, email, etc."
    ),
    NOTE(
        "You are correcting general note transcriptions."
    ),
    SHOPPING_LIST(
        "You are correcting shopping list transcriptions. " +
        "Common terms: milk, bread, eggs, etc."
    ),
    MEETING_NOTES(
        "You are correcting meeting notes transcriptions. " +
        "Common terms: discussed, action items, next steps, etc."
    )
}
```

### Model Selection

```kotlin
// Uses fast, cheap model for refinement
val refinementModel = ModelId.GPT_OSS_20B  // Fast, free

val result = aiRequest {
    model = refinementModel
    prompt = """
        ${context.systemPrompt}

        Correct any errors in this transcript:
        "$rawText"

        Return only the corrected text, no explanation.
    """.trimIndent()
    temperature = 0.2  // Low temp = more deterministic
}
```

**Location**: `app/src/main/java/com/boox/day/ai/TextInferenceService.kt`

---

## TranscriptionService (Voice Input)

**Purpose**: Unified voice-to-text service supporting multiple backends.

### Architecture

```
TranscriptionService (unified API)
    ├─→ VoiceInputService (SpeechRecognizer - free, unlimited)
    └─→ AudioRecorder + Gemini AI (metered: 3/day free, 20/day Pro)
```

### VoiceTextMode

```kotlin
enum class VoiceTextMode {
    AUTO,              // SpeechRecognizer if available, else prompt
    SPEECH_RECOGNIZER, // Force SpeechRecognizer only
    GEMINI             // Force Gemini only (metered)
}

// Set in GlobalAppSettings
val settings = GlobalAppSettings.current.copy(
    voiceTextMode = VoiceTextMode.AUTO
)
```

### Usage

```kotlin
val transcriptionService = TranscriptionService(context, audioRecorder)

// Start recording
when (transcriptionService.startTranscribing()) {
    StartResult.Started -> {
        // Recording in progress
        showRecordingUI()
    }
    StartResult.NeedsConfiguration -> {
        // Show VoiceSettingsDialog
        showVoiceSettingsDialog = true
    }
    StartResult.GeminiLimitReached -> {
        // Show GeminiLimitReachedDialog
        showLimitDialog = true
    }
}

// Stop and get result
when (val result = transcriptionService.stopAndGetResult()) {
    is TranscriptionResult.Success -> {
        val transcript = result.text
        // Use transcript
    }
    is TranscriptionResult.Error -> {
        showError(result.message)
    }
    TranscriptionResult.GeminiLimitReached -> {
        showLimitDialog = true
    }
}
```

### Gemini Voice Metering

| Tier | Daily Limit |
|------|-------------|
| **Free** | 3 uses/day |
| **Pro** | 20 uses/day |

**Storage**:
```kotlin
val settings = GlobalAppSettings.current
val usesToday = settings.dailyGeminiVoiceUses  // Int
val lastUseDate = settings.lastGeminiUseDate   // String ("yyyy-MM-dd")

// Check if limit reached
fun isGeminiLimitReached(): Boolean {
    val today = LocalDate.now().toString()
    val uses = if (settings.lastGeminiUseDate == today) {
        settings.dailyGeminiVoiceUses
    } else {
        0  // Reset on new day
    }

    val limit = if (settings.isProUser) 20 else 3
    return uses >= limit
}
```

**Location**: `app/src/main/java/com/boox/day/speech/TranscriptionService.kt`

---

## AudioRecorder

**Purpose**: Capture audio for Gemini voice input.

### Configuration

```kotlin
val audioRecorder = AudioRecorder(
    sampleRate = 16000,        // 16kHz (Gemini requirement)
    channels = 1,              // Mono
    encoding = AudioFormat.ENCODING_PCM_16BIT
)
```

### Usage

```kotlin
// Start recording
audioRecorder.startRecording { state ->
    when (state) {
        RecordingState.Idle -> {}
        RecordingState.Recording -> showRecordingUI()
        RecordingState.Speaking -> showSpeakingIndicator()
        RecordingState.SilenceDetected -> {
            // Auto-stop after 2.5-4s silence
            autoStopSoon()
        }
        RecordingState.Processing -> showProcessingUI()
    }
}

// Get audio bytes
val audioBytes = audioRecorder.getWavBytes()  // PCM → WAV format

// Convert to base64 for API
val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
```

### Silence Detection

**Adaptive threshold**:
- Initial: 2.5 seconds
- After 5s of speech: 3.5 seconds
- After 10s: 4 seconds

**Purpose**: Natural pause detection (user finished speaking).

**Location**: `app/src/main/java/com/boox/day/speech/AudioRecorder.kt`

---

## Error Handling

### Common Errors

| Error | Cause | Handling |
|-------|-------|----------|
| **API Key Missing** | User hasn't set key | Show settings dialog |
| **Rate Limit** | Too many requests | Show "Try again in X minutes" |
| **Network Error** | No connection | Show "Check internet connection" |
| **Model Unavailable** | OpenRouter model down | Fallback to alternate model |
| **Invalid Response** | Parsing error | Retry with different prompt |
| **Gemini Limit Reached** | Daily metered limit | Show upgrade or wait dialog |

### Retry Strategy

```kotlin
suspend fun <T> withRetry(
    maxRetries: Int = 3,
    delayMs: Long = 1000,
    block: suspend () -> T
): Result<T> {
    repeat(maxRetries) { attempt ->
        try {
            return Result.success(block())
        } catch (e: Exception) {
            if (attempt == maxRetries - 1) {
                return Result.failure(e)
            }
            delay(delayMs * (attempt + 1))  // Exponential backoff
        }
    }
    error("Should not reach here")
}

// Usage
val result = withRetry {
    aiRequest { /* ... */ }
}
```

---

## Cost Management

### Free Tier Limits

**OpenRouter Free Tier**:
- 200 requests/day (across all models)
- 50,000 tokens/day
- Rate: 20 requests/minute

**Gemini Voice (via OpenRouter)**:
- 3 requests/day (free)
- 20 requests/day (Pro - $5/month)

### Cost Optimization

1. **Use free models when possible**:
   - OCR: Gemini Flash Exp (free)
   - Parsing: GPT-OSS-20B (free)
   - Voice: SpeechRecognizer (free, unlimited)

2. **Cache results**:
   ```kotlin
   val cache = mutableMapOf<String, String>()

   suspend fun cachedOcr(bitmap: Bitmap): String {
       val hash = bitmap.hashCode().toString()
       return cache.getOrPut(hash) {
           recognizeHandwriting(bitmap)
       }
   }
   ```

3. **Batch requests** when possible.

4. **Use TextInferenceService sparingly** (adds extra API call).

---

## Testing

### Mock AI Responses

```kotlin
class MockAiClient : AiClient {
    override suspend fun request(params: AiRequestParams): AiResult {
        return AiResult.Success(
            content = "Mocked response",
            model = params.model,
            tokensUsed = 10
        )
    }
}

// Inject in tests
val testModule = module {
    single<AiClient> { MockAiClient() }
}
```

### Test Data

```kotlin
// Sample handwriting bitmap
val testBitmap = createTestBitmap("Hello World")

// Sample voice transcript
val testTranscript = "Meeting with John tomorrow at 2pm"

// Sample event JSON
val testEventJson = """
{
  "title": "Meeting with John",
  "startTime": "2025-01-16T14:00:00Z",
  "endTime": "2025-01-16T15:00:00Z"
}
""".trimIndent()
```

---

## Advanced Usage

### Custom Prompts

```kotlin
// More specific prompt for better results
val result = aiRequest {
    model = ModelId.GEMINI_FLASH_EXP
    prompt = """
        You are an expert at reading handwritten notes.

        Instructions:
        1. Extract all visible text from the image
        2. Preserve line breaks and formatting
        3. If text is unclear, make your best guess
        4. Do not add any commentary

        Image: [provided below]
    """.trimIndent()
    image = bitmap
}
```

### Temperature Control

```kotlin
// More deterministic (for structured data)
temperature = 0.1

// More creative (for summaries, suggestions)
temperature = 0.7
```

### Streaming Responses

```kotlin
val result = aiRequest {
    model = ModelId.GPT_OSS_20B
    prompt = "Summarize this long text..."
    streaming = true
    onChunk = { chunk ->
        // Update UI incrementally
        appendText(chunk)
    }
}
```

---

## Related Documentation

- **CLAUDE.md** - Quick AI reference
- **memory.md** - Section 10 (AI Module)
- **docs/archive/ai-models.md** - Model benchmarks
- **.claude/docs/ai-context.md** - Detailed AI context
- **OpenRouter Docs** - https://openrouter.ai/docs

---

## Key Takeaways

1. **OpenRouter aggregates 50+ models** - single API for all
2. **Gemini Flash Lite ONLY model with audio** - required for voice
3. **Free models available** - Gemini Flash Exp, Nova Lite, GPT-OSS-20B
4. **TranscriptionService is THE voice API** - handles SpeechRecognizer + Gemini
5. **TextInferenceService refines transcripts** - optional but improves quality
6. **3 requests/day free for Gemini voice** - use SpeechRecognizer when possible
7. **Metering tracked in GlobalAppSettings** - resets daily
8. **Always use InferenceContext** - improves semantic correction
9. **JSON mode for structured data** - forces valid JSON output
10. **Retry with exponential backoff** - handle transient errors

---

**Last Updated**: December 2025
**Maintainer**: Claude Code
**Questions**: Reference memory.md Section 10 or .claude/docs/ai-context.md
