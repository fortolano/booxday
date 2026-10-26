package com.boox.day.speech

import android.content.Context
import android.util.Log
import com.boox.day.ai.AiRetryExecutor
import com.boox.day.api.Modality
import com.boox.day.api.ModelConfigProvider
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.AiProblem
import com.boox.day.api.ai.AiResponse
import com.boox.day.api.ai.aiRequest
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.datastore.VoiceTextMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * TranscriptionService - THE unified voice-to-text service for all voice features.
 *
 * This service is the SINGLE entry point for all voice transcription in the app.
 * All voice dialogs (VoiceEventDialog, VoiceInputDialog, VoiceToTaskDialog) use this service.
 *
 * ## Architecture
 *
 * ```
 * Voice Feature Dialog
 *         │
 *         ▼
 *         │
 *         ├──► VoiceInputService (SpeechRecognizer - free, unlimited)
 *         │    └── Uses Google's SpeechRecognizer (requires Google Search app)
 *         │
 *         └──► AudioRecorder + Gemini AI (on the user's OpenRouter key)
 *              └── Records audio locally, sends to Gemini for transcription
 * ```
 *
 * ## CRITICAL: Microphone Contention
 *
 * AudioRecorder and SpeechRecognizer CANNOT run simultaneously!
 * Both require exclusive microphone access. Running both causes SpeechRecognizer
 * to receive no audio data.
 *
 * Therefore, these services are MUTUALLY EXCLUSIVE:
 * - SpeechRecognizer mode: Only VoiceInputService runs
 * - Gemini mode: Only AudioRecorder runs
 *
 * ## User Experience Flows
 *
 * **Flow A: Has Google Search (90%+ of users)**
 * - SpeechRecognizer is used (free, unlimited)
 * - If SpeechRecognizer fails, user sees error and can retry
 * - No automatic Gemini fallback (would need mic, causing contention)
 *
 * **Flow B: No Google Search + First Time**
 * - Returns NeedsConfiguration
 * - Dialog shows: Install Google Search OR Use Gemini
 *
 * **Flow C: Gemini-Only User**
 * - Valid choice for users who prefer AI transcription
 * - No daily limit: it runs on the user's own OpenRouter key (Oct 2026)
 *
 * @param context Android context
 * @param audioRecorder AudioRecorder instance (used only in Gemini mode)
 */
class TranscriptionService(
    private val context: Context,
    private val audioRecorder: AudioRecorder
) {
    companion object {
        private const val TAG = "TranscriptionService"
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DEPENDENCIES
    // ═══════════════════════════════════════════════════════════════════════════

    private val voiceInputService = VoiceInputService(context)

    // ═══════════════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════════════

    private val _state = MutableStateFlow(TranscriptionState.IDLE)
    /** Current transcription state */
    val state: StateFlow<TranscriptionState> = _state.asStateFlow()

    private val _activeSource = MutableStateFlow<TranscriptionSource?>(null)
    /** The transcription source currently being used (null if not started) */
    val activeSource: StateFlow<TranscriptionSource?> = _activeSource.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════════════
    // VOICE INPUT SERVICE STATE (for UI when using SpeechRecognizer)
    // ═══════════════════════════════════════════════════════════════════════════

    /** VoiceInputService state - use this for UI status when activeSource == SPEECH_RECOGNIZER */
    val speechRecognizerState: StateFlow<VoiceInputState> = voiceInputService.state

    /** VoiceInputService RMS level - use this for waveform when activeSource == SPEECH_RECOGNIZER */
    val speechRecognizerRmsLevel: StateFlow<Float> = voiceInputService.rmsLevel

    // ═══════════════════════════════════════════════════════════════════════════
    // UNIFIED AUTO-COMPLETE CALLBACK
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Set unified auto-complete listener that works for BOTH transcription modes.
     *
     * This is the ONLY way to receive auto-complete callbacks. The callback is triggered when:
     * - AudioRecorder detects silence (Gemini mode) - passes base64 audio
     * - VoiceInputService detects silence (SpeechRecognizer mode) - passes null (text already in service)
     *
     * @param listener Callback receiving optional base64 audio. In SpeechRecognizer mode, this
     *                 is null since text is already available via stopAndGetResult().
     */
    fun setOnAutoCompleteListener(listener: ((String?) -> Unit)?) {
        // Wire up AudioRecorder (for Gemini mode)
        audioRecorder.setOnAutoCompleteListener { base64 ->
            Log.d(TAG, "════════════════════════════════════════════════════════════")
            Log.d(TAG, "AUTO-COMPLETE: AudioRecorder triggered (Gemini mode)")
            Log.d(TAG, "  base64 length: ${base64?.length ?: 0}")
            listener?.invoke(base64)
        }

        // Wire up VoiceInputService (for SpeechRecognizer mode)
        voiceInputService.setOnAutoCompleteListener { text ->
            Log.d(TAG, "════════════════════════════════════════════════════════════")
            Log.d(TAG, "AUTO-COMPLETE: VoiceInputService triggered (SpeechRecognizer mode)")
            Log.d(TAG, "  Transcribed text: '${text.take(100)}${if (text.length > 100) "..." else ""}'")
            Log.d(TAG, "  Text length: ${text.length} chars")
            // Pass null because we don't have/need base64 - text is already transcribed
            listener?.invoke(null)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if Google Search app is installed (enables SpeechRecognizer).
     */
    fun hasGoogleSearchApp(): Boolean {
        return VoiceInputService.hasGoogleSearchApp(context)
    }

    /**
     * Check if SpeechRecognizer is available on this device.
     *
     * Returns true if EITHER:
     * - Google Search app is installed (com.google.android.googlequicksearchbox)
     * - Android reports speech recognition is available (SpeechRecognizer.isRecognitionAvailable)
     *
     * We check BOTH because:
     * - hasGoogleSearchApp() can return false even when speech recognition works
     *   (some devices have speech recognition via Google Play Services or other providers)
     * - SpeechRecognizer.isRecognitionAvailable() can return false on some devices
     *   even when Google Search is installed and speech recognition works
     *
     * By using OR logic, we maximize compatibility across different Android devices.
     */
    fun isSpeechRecognitionAvailable(): Boolean {
        val hasGoogleSearch = VoiceInputService.hasGoogleSearchApp(context)
        val systemReportsAvailable = VoiceInputService.isAvailable(context)
        Log.d(TAG, "isSpeechRecognitionAvailable: hasGoogleSearch=$hasGoogleSearch, systemReportsAvailable=$systemReportsAvailable")
        return hasGoogleSearch || systemReportsAvailable
    }



    /**
     * Start transcription.
     *
     * This method validates configuration and starts the appropriate transcription service.
     *
     * IMPORTANT: AudioRecorder and SpeechRecognizer CANNOT run simultaneously!
     * They both need exclusive microphone access. Running both causes SpeechRecognizer
     * to receive no audio data (mic contention).
     *
     * - SpeechRecognizer mode: Only VoiceInputService runs (free, unlimited)
     * - Gemini mode: Only AudioRecorder runs (on the user's OpenRouter key)
     *
     * @return StartResult indicating success or what action is needed
     */
    suspend fun startTranscribing(): StartResult {
        val settings = GlobalAppSettings.current
        val speechRecognitionAvailable = isSpeechRecognitionAvailable()

        Log.i(TAG, "════════════════════════════════════════════════════════════")
        Log.i(TAG, "startTranscribing: mode=${settings.voiceTextMode}, speechRecognitionAvailable=$speechRecognitionAvailable")

        // ─────────────────────────────────────────────────────────────────────
        // STEP 1: Validate configuration based on scenario matrix
        // ─────────────────────────────────────────────────────────────────────

        when (settings.voiceTextMode) {
            VoiceTextMode.AUTO -> {
                if (!speechRecognitionAvailable) {
                    // No speech recognition available + AUTO = must configure
                    Log.w(TAG, "AUTO mode without speech recognition - needs configuration")
                    return StartResult.NeedsConfiguration
                }
                // Speech recognition available + AUTO = proceed with SpeechRecognizer
                Log.d(TAG, "AUTO mode with speech recognition available - will use SpeechRecognizer")
            }

            VoiceTextMode.SPEECH_RECOGNIZER -> {
                if (!speechRecognitionAvailable) {
                    // User chose SpeechRecognizer but it's unavailable
                    Log.w(TAG, "SPEECH_RECOGNIZER mode without speech recognition - needs configuration")
                    return StartResult.NeedsConfiguration
                }
                Log.d(TAG, "SPEECH_RECOGNIZER mode - speech recognition available")
            }

            VoiceTextMode.GEMINI -> {
                // AI transcription runs on the user's own OpenRouter key (Oct 2026)
                if (!com.boox.day.security.UserOpenRouterKey.hasKey) {
                    Log.w(TAG, "GEMINI mode without an OpenRouter key")
                    com.boox.day.ui.AiKeyGate.open(com.boox.day.ui.AiKeyGate.Reason.NO_KEY)
                    return StartResult.NeedsApiKey
                }
                Log.d(TAG, "GEMINI mode - on the user's OpenRouter key")
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // STEP 2: Determine which service to use
        // ─────────────────────────────────────────────────────────────────────

        val useSpeechRecognizer = when (settings.voiceTextMode) {
            VoiceTextMode.AUTO -> speechRecognitionAvailable
            VoiceTextMode.SPEECH_RECOGNIZER -> true
            VoiceTextMode.GEMINI -> false
        }

        // ─────────────────────────────────────────────────────────────────────
        // STEP 3: Start the appropriate service (MUTUALLY EXCLUSIVE!)
        //
        // CRITICAL: Do NOT start both services simultaneously!
        // AudioRecorder grabs the mic exclusively, leaving SpeechRecognizer
        // with no audio data (mic contention issue).
        // ─────────────────────────────────────────────────────────────────────

        if (useSpeechRecognizer) {
            // SpeechRecognizer mode - DO NOT start AudioRecorder
            Log.i(TAG, "████ ▶ Starting SpeechRecognizer ONLY (no AudioRecorder to avoid mic contention)")
            voiceInputService.startListening()
            _activeSource.value = TranscriptionSource.SPEECH_RECOGNIZER
            Log.i(TAG, "████ SpeechRecognizer started, activeSource=${_activeSource.value}")
        } else {
            // Gemini mode - start AudioRecorder only
            Log.i(TAG, "████ ▶ Starting AudioRecorder ONLY (Gemini mode)")
            Log.i(TAG, "████ AudioRecorder current state BEFORE start: ${audioRecorder.state.value}")
            audioRecorder.startRecording()
            Log.i(TAG, "████ AudioRecorder.startRecording() returned")
            Log.i(TAG, "████ AudioRecorder current state AFTER start: ${audioRecorder.state.value}")
            _activeSource.value = TranscriptionSource.GEMINI
            Log.i(TAG, "████ activeSource set to GEMINI")
        }

        _state.value = TranscriptionState.LISTENING
        Log.i(TAG, "████ Transcription started: source=${_activeSource.value}, state=${_state.value}")
        Log.i(TAG, "████ AudioRecorder.state=${audioRecorder.state.value}, AudioRecorder.rmsLevel=${audioRecorder.rmsLevel.value}")
        return StartResult.Started
    }

    /**
     * Pause transcription (preserves accumulated data).
     */
    fun pause() {
        when (_activeSource.value) {
            TranscriptionSource.SPEECH_RECOGNIZER -> {
                voiceInputService.pause()
                Log.d(TAG, "SpeechRecognizer paused")
            }
            TranscriptionSource.GEMINI -> {
                audioRecorder.pauseRecording()
                Log.d(TAG, "AudioRecorder paused")
            }
            null -> Log.w(TAG, "pause() called but no active source")
        }
        _state.value = TranscriptionState.IDLE
    }

    /**
     * Resume transcription after pause.
     */
    fun resume() {
        when (_activeSource.value) {
            TranscriptionSource.SPEECH_RECOGNIZER -> {
                voiceInputService.resume()
                Log.d(TAG, "SpeechRecognizer resumed")
            }
            TranscriptionSource.GEMINI -> {
                audioRecorder.startRecording()
                Log.d(TAG, "AudioRecorder resumed")
            }
            null -> Log.w(TAG, "resume() called but no active source")
        }
        _state.value = TranscriptionState.LISTENING
    }

    /**
     * Stop transcription and get the result.
     *
     * - SpeechRecognizer mode: Returns SpeechRecognizer result (no Gemini fallback available
     *   since AudioRecorder wasn't running to avoid mic contention)
     * - Gemini mode: Returns Gemini transcription of recorded audio
     *
     * @param preCapturedAudio Optional pre-captured audio base64 from auto-complete callback.
     *                         Only used in Gemini mode. In SpeechRecognizer mode, this is ignored
     *                         since AudioRecorder wasn't running.
     * @return TranscriptionResult with the transcribed text or error
     */
    suspend fun stopAndGetResult(preCapturedAudio: String? = null): TranscriptionResult {
        _state.value = TranscriptionState.PROCESSING
        Log.i(TAG, "────────────────────────────────────────────────────────────")
        Log.i(TAG, "stopAndGetResult: activeSource=${_activeSource.value}")

        return when (_activeSource.value) {
            TranscriptionSource.SPEECH_RECOGNIZER -> {
                // Get text from SpeechRecognizer
                val text = voiceInputService.getAccumulatedText()
                val partialResult = voiceInputService.partialResult.value
                val state = voiceInputService.state.value

                voiceInputService.stopListening()

                Log.d(TAG, "SpeechRecognizer results:")
                Log.d(TAG, "  - accumulatedText: '${text.take(100)}${if (text.length > 100) "..." else ""}'")
                Log.d(TAG, "  - partialResult: '$partialResult'")
                Log.d(TAG, "  - finalState: $state")

                if (text.isNotBlank()) {
                    Log.i(TAG, "✓ SpeechRecognizer SUCCESS: ${text.length} chars")
                    _state.value = TranscriptionState.IDLE
                    TranscriptionResult.Success(text.trim(), TranscriptionSource.SPEECH_RECOGNIZER)
                } else {
                    // SpeechRecognizer failed - NO Gemini fallback available!
                    // AudioRecorder wasn't running (to avoid mic contention), so we have no audio.
                    Log.w(TAG, "✗ SpeechRecognizer FAILED: returned empty text")
                    Log.w(TAG, "  Gemini fallback NOT available (AudioRecorder wasn't running)")
                    Log.w(TAG, "  Possible causes:")
                    Log.w(TAG, "    1. User didn't speak loudly enough")
                    Log.w(TAG, "    2. Network issue with Google's speech service")
                    Log.w(TAG, "    3. Background noise confused the recognizer")
                    Log.w(TAG, "    4. Language/accent not recognized")

                    _state.value = TranscriptionState.ERROR
                    TranscriptionResult.Error("Speech not recognized. Please try again and speak clearly.")
                }
            }

            TranscriptionSource.GEMINI -> {
                // Gemini mode - use pre-captured audio or stop and get
                val audioBase64 = preCapturedAudio ?: audioRecorder.stopAndGetBase64()
                Log.i(TAG, "AudioRecorder stopped: ${audioBase64?.length ?: 0} chars")

                if (audioBase64 == null) {
                    Log.w(TAG, "✗ No audio recorded for Gemini transcription")
                    _state.value = TranscriptionState.ERROR
                    return TranscriptionResult.Error("No audio recorded")
                }

                transcribeWithGemini(audioBase64)
            }

            null -> {
                Log.e(TAG, "✗ stopAndGetResult called but transcription was never started!")
                _state.value = TranscriptionState.ERROR
                TranscriptionResult.Error("Transcription not started")
            }
        }
    }

    /**
     * Cancel transcription and discard all data.
     */
    fun cancel() {
        when (_activeSource.value) {
            TranscriptionSource.SPEECH_RECOGNIZER -> voiceInputService.cancel()
            TranscriptionSource.GEMINI -> audioRecorder.cancel()
            null -> { /* Nothing to cancel */ }
        }
        _state.value = TranscriptionState.IDLE
        _activeSource.value = null
        Log.d(TAG, "Transcription cancelled")
    }

    /**
     * Release all resources. Call when dialog is dismissed.
     */
    fun release() {
        // Clear auto-complete listeners first
        audioRecorder.setOnAutoCompleteListener(null)
        voiceInputService.setOnAutoCompleteListener(null)

        voiceInputService.destroy()
        audioRecorder.release()
        _state.value = TranscriptionState.IDLE
        _activeSource.value = null
        Log.d(TAG, "TranscriptionService released")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTERNAL: GEMINI TRANSCRIPTION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Transcribe audio using Gemini AI.
     * Uses server-side audio modality config when available.
     */
    private suspend fun transcribeWithGemini(audioBase64: String?): TranscriptionResult {
        if (audioBase64 == null) {
            _state.value = TranscriptionState.ERROR
            return TranscriptionResult.Error("No audio recorded")
        }

        // The voice job's models: the user's pick, then BooxDay's choice (a backup since Oct 2026)
        val audioModels = ModelConfigProvider.getAudioFallbackChain()
        Log.d(TAG, "Transcribing with ${audioModels.map { it.id }} (${audioBase64.length} chars)")

        val response = AiRetryExecutor.executeWithBackup(audioModels) { audioModel ->
            aiRequest {
                model(audioModel)
                systemPrompt(TRANSCRIPTION_SYSTEM_PROMPT)
                userMessageWithAudio(
                    text = "Transcribe this audio accurately. Return ONLY the transcription, nothing else.",
                    audioBase64 = audioBase64,
                    mimeType = "audio/wav"
                )
                temperature(0.2f)
            }
        }

        return when (response) {
            is AiResponse.Success -> {
                val text = response.data.trim()
                Log.d(TAG, "Gemini transcription success: '$text'")

                if (text.isBlank()) {
                    _state.value = TranscriptionState.IDLE
                    TranscriptionResult.Error("No speech detected")
                } else {
                    _state.value = TranscriptionState.IDLE
                    TranscriptionResult.Success(text, TranscriptionSource.GEMINI)
                }
            }

            // What went wrong in plain words (Oct 2026: OpenRouter timeouts said "No internet
            // connection", other errors showed OpenRouter's raw HTTP answer)
            is AiResponse.Error -> {
                Log.e(TAG, "Gemini error: ${response.message}")
                _state.value = TranscriptionState.ERROR
                TranscriptionResult.Error(AiProblem.messageFor(response))
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════════
// RESULT TYPES
// ═════════════════════════════════════════════════════════════════════════════════

/**
 * Result of calling startTranscribing().
 */
sealed class StartResult {
    /** Transcription started successfully */
    object Started : StartResult()

    /** Configuration needed - show VoiceSettingsDialog */
    object NeedsConfiguration : StartResult()

    /** AI voice mode without the user's own OpenRouter key (Oct 2026) - the key popup is open */
    object NeedsApiKey : StartResult()
}

/**
 * Result of calling stopAndGetResult().
 */
sealed class TranscriptionResult {
    /** Transcription succeeded */
    data class Success(
        val text: String,
        val source: TranscriptionSource
    ) : TranscriptionResult()

    /** Transcription failed with an error */
    data class Error(val message: String) : TranscriptionResult()
}

/**
 * Source of the transcription.
 */
enum class TranscriptionSource {
    /** Android SpeechRecognizer (via Google Search app) - free, unlimited */
    SPEECH_RECOGNIZER,

    /** Gemini AI transcription on the user's own OpenRouter key */
    GEMINI
}

/**
 * State of the transcription service.
 */
enum class TranscriptionState {
    /** Not transcribing */
    IDLE,

    /** Actively listening for speech */
    LISTENING,

    /** Processing audio / getting result */
    PROCESSING,

    /** Error occurred */
    ERROR
}

// ═════════════════════════════════════════════════════════════════════════════════
// PROMPTS
// ═════════════════════════════════════════════════════════════════════════════════

private const val TRANSCRIPTION_SYSTEM_PROMPT = """
You are a speech-to-text transcription system.
Transcribe the user's speech accurately and completely.

RULES:
- Output ONLY the transcription - no explanations, no formatting
- Preserve the user's exact words
- Include filler words (um, uh) only if they're significant
- Do NOT interpret or parse the content - just transcribe
- If you can't understand part of the audio, write [unclear]

OUTPUT: Plain text transcription only.
"""
