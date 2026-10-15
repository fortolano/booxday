package com.boox.day.speech

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Enhanced voice input service with smart silence detection.
 *
 * Key features:
 * - Exposes RMS (audio level) for waveform visualization
 * - Smart silence detection with adaptive timeouts for ESL/slow speakers
 * - Auto-complete callback when user finishes speaking
 * - Pause/Resume support for natural speech patterns
 *
 * The silence detection algorithm:
 * 1. Base timeout: 3 seconds after speech ends
 * 2. Grace period: +200ms per second of speech (rewards longer utterances)
 * 3. Maximum wait: 5 seconds (prevents indefinite waiting)
 * 4. ESL-friendly: Longer pauses within speech don't trigger premature cutoff
 */
class VoiceInputService(private val context: Context) {
    companion object {
        private const val TAG = "VoiceInputService"

        // Google Search app package (required for SpeechRecognizer)
        private const val GOOGLE_SEARCH_PACKAGE = "com.google.android.googlequicksearchbox"

        // Smart silence detection configuration
        private const val INITIAL_SILENCE_TIMEOUT_MS = 10_000L  // 10s before "no speech" error
        private const val POST_SPEECH_SILENCE_BASE_MS = 3_000L  // 3s base after speech ends
        private const val GRACE_PERIOD_PER_SECOND_MS = 200L     // +200ms per second spoken
        private const val MAX_SILENCE_TIMEOUT_MS = 5_000L       // Never wait more than 5s
        private const val MICRO_PAUSE_THRESHOLD_MS = 1_000L     // Brief pauses don't trigger end

        /**
         * Check if SpeechRecognizer is available on this device.
         */
        fun isAvailable(context: Context): Boolean {
            return SpeechRecognizer.isRecognitionAvailable(context)
        }

        /**
         * Check if Google Search app is installed.
         *
         * The Google Search app (com.google.android.googlequicksearchbox) provides the
         * SpeechRecognizer service on most Android devices. Without it, SpeechRecognizer
         * won't work reliably.
         *
         * @return true if Google Search is installed, false otherwise
         */
        fun hasGoogleSearchApp(context: Context): Boolean {
            return try {
                context.packageManager.getPackageInfo(GOOGLE_SEARCH_PACKAGE, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // State flows
    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val state: StateFlow<VoiceInputState> = _state

    private val _rmsLevel = MutableStateFlow(0f)
    /** Normalized RMS level (0.0-1.0) for waveform visualization */
    val rmsLevel: StateFlow<Float> = _rmsLevel

    private val _partialResult = MutableStateFlow("")
    val partialResult: StateFlow<String> = _partialResult

    private val _finalResult = MutableStateFlow<String?>(null)
    val finalResult: StateFlow<String?> = _finalResult

    private val _accumulatedText = MutableStateFlow("")
    /** All text accumulated across Start/Pause cycles */
    val accumulatedText: StateFlow<String> = _accumulatedText

    // Callback for auto-complete (when smart silence detection fires)
    private var onAutoComplete: ((String) -> Unit)? = null

    // Smart silence detection state
    private var speechStartTime: Long? = null
    private var lastSpeechTime: Long? = null
    private var silenceTimerJob: Job? = null
    private var initialSilenceJob: Job? = null
    private var hasSpeechStarted = false

    /**
     * Set callback for when auto-complete triggers (user finished speaking).
     * This is called automatically after smart silence detection.
     */
    fun setOnAutoCompleteListener(listener: ((String) -> Unit)?) {
        onAutoComplete = listener
    }

    /**
     * Start listening for speech.
     * Will automatically call onAutoComplete when user finishes speaking.
     *
     * Note: We intentionally DO NOT check SpeechRecognizer.isRecognitionAvailable() here.
     * That API is unreliable and can return false even when Google Search is installed
     * and SpeechRecognizer works correctly. TranscriptionService already validates
     * availability via hasGoogleSearchApp() before calling this method.
     * If SpeechRecognizer truly fails, onError() will handle it gracefully.
     */
    fun startListening(locale: Locale = Locale.getDefault()) {
        // Cancel any pending timers
        silenceTimerJob?.cancel()
        initialSilenceJob?.cancel()

        // Reset speech tracking for new session
        hasSpeechStarted = false

        // Create new recognizer
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createListener())
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            // Extended timeouts for ESL speakers
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }

        _state.value = VoiceInputState.Listening
        _partialResult.value = ""
        _finalResult.value = null
        _rmsLevel.value = 0f

        speechRecognizer?.startListening(intent)

        // Start initial silence timeout (if user never speaks)
        startInitialSilenceTimer()
    }

    /**
     * Pause listening (user pressed pause button).
     * Accumulated text is preserved, can resume later.
     */
    fun pause() {
        silenceTimerJob?.cancel()
        initialSilenceJob?.cancel()
        speechRecognizer?.stopListening()
        _state.value = VoiceInputState.Paused
        _rmsLevel.value = 0f
    }

    /**
     * Resume listening after pause.
     */
    fun resume(locale: Locale = Locale.getDefault()) {
        startListening(locale)
    }

    /**
     * Stop listening and trigger completion.
     * Use this when user explicitly wants to finish (not auto-complete).
     */
    fun stop() {
        silenceTimerJob?.cancel()
        initialSilenceJob?.cancel()
        speechRecognizer?.stopListening()
        _state.value = VoiceInputState.Processing
        _rmsLevel.value = 0f
    }

    /**
     * Get final text and trigger auto-complete callback.
     * Called internally by smart silence detection or manually.
     */
    fun completeAndSend() {
        silenceTimerJob?.cancel()
        initialSilenceJob?.cancel()

        val finalText = _accumulatedText.value.ifEmpty { _partialResult.value }

        if (finalText.isNotEmpty()) {
            _state.value = VoiceInputState.AutoComplete
            Log.d(TAG, "Auto-complete triggered with text: $finalText")
            onAutoComplete?.invoke(finalText)
        } else {
            _state.value = VoiceInputState.Error("No speech detected")
        }
    }

    /**
     * Cancel everything and reset.
     */
    fun cancel() {
        silenceTimerJob?.cancel()
        initialSilenceJob?.cancel()
        speechRecognizer?.cancel()
        _state.value = VoiceInputState.Idle
        _partialResult.value = ""
        _accumulatedText.value = ""
        _rmsLevel.value = 0f
        hasSpeechStarted = false
    }

    /**
     * Clear accumulated text (for restart).
     */
    fun clearAccumulated() {
        _accumulatedText.value = ""
        _partialResult.value = ""
    }

    /**
     * Get accumulated text without triggering callback.
     *
     * This is used by TranscriptionService to retrieve the transcribed text
     * after stopListening() without triggering the auto-complete callback.
     *
     * @return The accumulated transcription text, including any current partial result
     */
    fun getAccumulatedText(): String {
        val accumulated = _accumulatedText.value
        val partial = _partialResult.value
        return when {
            accumulated.isNotEmpty() && partial.isNotEmpty() -> "$accumulated $partial"
            accumulated.isNotEmpty() -> accumulated
            else -> partial
        }
    }

    /**
     * Stop listening without triggering completion.
     * Used by TranscriptionService for manual stop control.
     */
    fun stopListening() {
        silenceTimerJob?.cancel()
        initialSilenceJob?.cancel()
        speechRecognizer?.stopListening()
        _state.value = VoiceInputState.Idle
        _rmsLevel.value = 0f
    }

    /**
     * Release resources.
     */
    fun destroy() {
        silenceTimerJob?.cancel()
        initialSilenceJob?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        scope.cancel()
    }

    // =========================================================================
    // SMART SILENCE DETECTION
    // =========================================================================

    /**
     * Start timer for initial silence (user never speaks).
     */
    private fun startInitialSilenceTimer() {
        initialSilenceJob?.cancel()
        initialSilenceJob = scope.launch {
            delay(INITIAL_SILENCE_TIMEOUT_MS)
            if (!hasSpeechStarted && _state.value is VoiceInputState.Listening) {
                Log.d(TAG, "Initial silence timeout - no speech detected")
                _state.value = VoiceInputState.Error("I didn't hear anything. Tap to try again.")
            }
        }
    }

    /**
     * Calculate adaptive silence timeout based on speech duration.
     *
     * Longer utterances get more grace period because:
     * - User is clearly engaged and might have more to say
     * - ESL speakers often pause to think mid-sentence
     * - Complex thoughts take longer to articulate
     */
    private fun calculateSilenceTimeout(speechDurationMs: Long): Long {
        val gracePeriod = (speechDurationMs / 1000) * GRACE_PERIOD_PER_SECOND_MS
        val total = POST_SPEECH_SILENCE_BASE_MS + gracePeriod
        return total.coerceAtMost(MAX_SILENCE_TIMEOUT_MS)
    }

    /**
     * Start smart silence timer after speech ends.
     */
    private fun startSmartSilenceTimer() {
        val speechDuration = speechStartTime?.let { System.currentTimeMillis() - it } ?: 0
        val timeout = calculateSilenceTimeout(speechDuration)

        Log.d(TAG, "Starting silence timer: ${timeout}ms (speech duration: ${speechDuration}ms)")

        silenceTimerJob?.cancel()
        silenceTimerJob = scope.launch {
            delay(timeout)
            // Check if we're still in a state where auto-complete makes sense
            val currentState = _state.value
            if (currentState is VoiceInputState.SilenceDetected ||
                currentState is VoiceInputState.Processing) {
                Log.d(TAG, "Smart silence timeout - completing")
                completeAndSend()
            }
        }
    }

    // =========================================================================
    // RECOGNITION LISTENER
    // =========================================================================

    private fun createListener() = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.i(TAG, "════════════════════════════════════════════════════════════")
            Log.i(TAG, "SpeechRecognizer: Ready for speech")
            Log.i(TAG, "  Microphone should now be active and listening")
            _state.value = VoiceInputState.Listening
        }

        override fun onBeginningOfSpeech() {
            Log.i(TAG, "SpeechRecognizer: Speech STARTED (user is speaking)")
            hasSpeechStarted = true
            speechStartTime = System.currentTimeMillis()
            lastSpeechTime = speechStartTime
            initialSilenceJob?.cancel()
            silenceTimerJob?.cancel()
            _state.value = VoiceInputState.Speaking
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Normalize RMS to 0-1 range for waveform
            // Typical rmsdB range: -2 to 10
            val normalized = ((rmsdB + 2) / 12f).coerceIn(0f, 1f)
            _rmsLevel.value = normalized

            // Track that we're still receiving audio
            if (normalized > 0.1f) {
                lastSpeechTime = System.currentTimeMillis()
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Not used
        }

        override fun onEndOfSpeech() {
            val speechDuration = speechStartTime?.let { System.currentTimeMillis() - it } ?: 0
            Log.i(TAG, "SpeechRecognizer: End of speech detected")
            Log.i(TAG, "  Speech duration: ${speechDuration}ms")
            Log.i(TAG, "  hasSpeechStarted: $hasSpeechStarted")
            _state.value = VoiceInputState.SilenceDetected

            // Start smart silence timer
            startSmartSilenceTimer()
        }

        override fun onError(error: Int) {
            val errorName = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
                SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
                SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
                SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
                SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
                else -> "UNKNOWN($error)"
            }

            Log.w(TAG, "════════════════════════════════════════════════════════════")
            Log.w(TAG, "SpeechRecognizer ERROR: $errorName (code: $error)")
            Log.w(TAG, "  hasSpeechStarted: $hasSpeechStarted")
            Log.d(TAG, "  accumulatedText: '${_accumulatedText.value}'")  // PII - stripped in release
            Log.d(TAG, "  partialResult: '${_partialResult.value}'")  // PII - stripped in release

            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> {
                    Log.e(TAG, "  DIAGNOSIS: Microphone/audio issue - check if another app is using mic")
                    "Audio recording error"
                }
                SpeechRecognizer.ERROR_CLIENT -> {
                    Log.e(TAG, "  DIAGNOSIS: Client-side error - possibly mic contention or invalid state")
                    "Client error"
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    Log.e(TAG, "  DIAGNOSIS: Missing RECORD_AUDIO permission")
                    "Permission denied"
                }
                SpeechRecognizer.ERROR_NETWORK -> {
                    Log.e(TAG, "  DIAGNOSIS: Network unavailable - check WiFi/mobile data")
                    "Network error - check your connection"
                }
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                    Log.e(TAG, "  DIAGNOSIS: Network request timed out - slow connection?")
                    "Network timeout"
                }
                SpeechRecognizer.ERROR_NO_MATCH -> {
                    Log.w(TAG, "  DIAGNOSIS: No speech recognized - user may have been too quiet or background noise")
                    // No match can mean:
                    // 1. User stopped speaking - this is expected, not an error
                    // 2. Speech was too quiet
                    // 3. Background noise confused the recognizer
                    if (hasSpeechStarted) {
                        // User spoke but recognition failed - try to use what we have
                        Log.d(TAG, "  Attempting to use partial/accumulated text")
                        completeAndSend()
                        return
                    }
                    "No speech detected"
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    Log.e(TAG, "  DIAGNOSIS: SpeechRecognizer is busy - maybe previous session not cleaned up")
                    "Recognizer busy"
                }
                SpeechRecognizer.ERROR_SERVER -> {
                    Log.e(TAG, "  DIAGNOSIS: Google's speech server returned an error")
                    "Server error"
                }
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    Log.w(TAG, "  DIAGNOSIS: User was silent too long before speaking")
                    // Speech timeout - user was silent too long
                    if (hasSpeechStarted && _accumulatedText.value.isNotEmpty()) {
                        Log.d(TAG, "  Attempting to use accumulated text")
                        completeAndSend()
                        return
                    }
                    "No speech input detected"
                }
                else -> "Unknown error ($error)"
            }

            // If we have accumulated text, still offer to use it
            if (_accumulatedText.value.isNotEmpty() || _partialResult.value.isNotEmpty()) {
                Log.i(TAG, "  Recovery: Have partial data, transitioning to Paused state")
                _state.value = VoiceInputState.Paused
            } else {
                Log.w(TAG, "  No recovery possible: No accumulated/partial text available")
                _state.value = VoiceInputState.Error(errorMessage)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
            val text = matches?.firstOrNull() ?: ""

            Log.i(TAG, "════════════════════════════════════════════════════════════")
            Log.i(TAG, "SpeechRecognizer: FINAL RESULTS received")
            Log.d(TAG, "  Text: '${text.take(100)}${if (text.length > 100) "..." else ""}'")  // PII - stripped in release
            Log.d(TAG, "  Text length: ${text.length} chars")
            Log.d(TAG, "  Matches count: ${matches?.size ?: 0}")
            if (confidences != null && confidences.isNotEmpty()) {
                Log.d(TAG, "  Top confidence: ${confidences[0]}")
            }

            _finalResult.value = text

            // Accumulate text
            val currentAccumulated = _accumulatedText.value
            _accumulatedText.value = if (currentAccumulated.isEmpty()) {
                text
            } else {
                "$currentAccumulated $text"
            }
            Log.i(TAG, "  Total accumulated: ${_accumulatedText.value.length} chars")

            // Clear partial since we have final
            _partialResult.value = ""

            // Transition to silence detected (timer will auto-complete)
            _state.value = VoiceInputState.SilenceDetected
            startSmartSilenceTimer()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull() ?: ""

            // Only log if there's actual content (avoid spamming empty partials)
            if (partial.isNotEmpty() && partial != _partialResult.value) {
                Log.d(TAG, "SpeechRecognizer: Partial result: '$partial'")
            }

            _partialResult.value = partial

            // Reset silence timer when we get new partial results
            // (user is still speaking)
            if (partial.isNotEmpty()) {
                lastSpeechTime = System.currentTimeMillis()
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            Log.d(TAG, "SpeechRecognizer: Event $eventType")
        }
    }
}

/**
 * Voice input states for the enhanced state machine.
 */
sealed class VoiceInputState {
    /** Initial state - not recording */
    object Idle : VoiceInputState()

    /** Listening but no speech detected yet */
    object Listening : VoiceInputState()

    /** User is actively speaking */
    object Speaking : VoiceInputState()

    /** User stopped speaking, waiting to see if they continue */
    object SilenceDetected : VoiceInputState()

    /** Processing final results */
    object Processing : VoiceInputState()

    /** User manually paused - can resume */
    object Paused : VoiceInputState()

    /** Auto-complete triggered - ready to send to AI */
    object AutoComplete : VoiceInputState()

    /** Error occurred */
    data class Error(val message: String) : VoiceInputState()
}
