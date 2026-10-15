package com.boox.day.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * AudioRecorder - Records audio and produces WAV-encoded base64 for AI processing.
 *
 * This recorder captures raw PCM audio and converts it to WAV format suitable for
 * sending to Gemini 2.0 Flash or other audio-capable AI models via OpenRouter.
 *
 * Features:
 * - Captures 16kHz mono PCM audio (optimal for speech recognition)
 * - Real-time RMS level monitoring for waveform visualization
 * - Smart silence detection with configurable thresholds
 * - Outputs WAV-formatted base64 string for AI APIs
 *
 * Usage:
 * ```kotlin
 * val recorder = AudioRecorder(context)
 * recorder.startRecording()
 *
 * // Monitor state and RMS
 * recorder.state.collect { state -> ... }
 * recorder.rmsLevel.collect { rms -> ... }
 *
 * // When done (or auto-triggered by silence)
 * val audioBase64 = recorder.stopAndGetBase64()
 * ```
 */
class AudioRecorder(private val context: Context) {
    companion object {
        private const val TAG = "AudioRecorder"

        // Audio format constants - 16kHz mono is optimal for speech AI
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BITS_PER_SAMPLE = 16
        private const val NUM_CHANNELS = 1

        // ═══════════════════════════════════════════════════════════════════════
        // NO-TOUCH ZONE: RMS Threshold Calibration (Jan 2026)
        // ═══════════════════════════════════════════════════════════════════════
        // These thresholds were carefully calibrated to match actual raw PCM RMS values.
        // Raw RMS from microphone: quiet ~0.001-0.002, speech ~0.02-0.05
        //
        // BUG FIXED: Original thresholds (0.02/0.05) were too high, causing:
        // - State never transitioning from Recording to Speaking
        // - WaveformCanvas showing "breathing" animation instead of dynamic bars
        // - UI appearing "dead" during voice recording in Gemini mode
        //
        // DO NOT raise these thresholds - it will break waveform visualization!
        // ═══════════════════════════════════════════════════════════════════════
        private const val SILENCE_RMS_THRESHOLD = 0.005f  // Below this is considered silence
        private const val SPEECH_RMS_THRESHOLD = 0.015f   // Above this is considered speech
        private const val SILENCE_DURATION_BASE_MS = 2500L  // 2.5s base silence triggers auto-stop
        private const val SILENCE_DURATION_MAX_MS = 4000L   // Never wait more than 4s
        private const val GRACE_PERIOD_PER_SECOND_MS = 150L // +150ms per second of speech

        // Maximum recording duration (safety limit)
        private const val MAX_RECORDING_DURATION_MS = 60_000L  // 60 seconds max

        /**
         * Check if audio recording is available.
         */
        fun isAvailable(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Recording state.
     */
    sealed class State {
        object Idle : State()
        object Recording : State()
        object Speaking : State()
        object SilenceDetected : State()
        object Processing : State()
        data class Error(val message: String) : State()
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val recordingScope = CoroutineScope(Dispatchers.IO)

    // Accumulated PCM data
    private val pcmBuffer = ByteArrayOutputStream()
    private val bufferLock = Any()

    // State tracking
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    // Silence detection state
    private var speechStartTime: Long = 0
    private var lastSpeechTime: Long = 0
    private var hasSpeechStarted = false

    // Auto-complete callback
    private var onAutoComplete: ((String) -> Unit)? = null

    /**
     * Set callback for when recording auto-completes due to silence.
     */
    fun setOnAutoCompleteListener(listener: ((String) -> Unit)?) {
        onAutoComplete = listener
    }

    /**
     * Start recording audio.
     */
    fun startRecording() {
        Log.i(TAG, "████ startRecording() ENTER - current state: ${_state.value}")

        if (_state.value is State.Recording || _state.value is State.Speaking) {
            Log.w(TAG, "████ Already recording - returning early")
            return
        }

        Log.i(TAG, "████ Checking permission...")
        if (!isAvailable(context)) {
            Log.e(TAG, "████ Permission NOT granted!")
            _state.value = State.Error("Audio permission not granted")
            return
        }
        Log.i(TAG, "████ Permission granted ✓")

        try {
            Log.i(TAG, "████ Getting minBufferSize...")
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            Log.i(TAG, "████ minBufferSize = $minBufferSize")
            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "████ Failed to calculate buffer size!")
                _state.value = State.Error("Failed to calculate buffer size")
                return
            }

            // Use larger buffer for stability
            val bufferSize = minBufferSize * 2
            Log.i(TAG, "████ Using bufferSize = $bufferSize")

            Log.i(TAG, "████ Creating AudioRecord instance...")
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            Log.i(TAG, "████ AudioRecord created, state = ${audioRecord?.state} (expected STATE_INITIALIZED=1)")

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "████ AudioRecord NOT initialized! state=${audioRecord?.state}")
                _state.value = State.Error("Failed to initialize AudioRecord")
                audioRecord?.release()
                audioRecord = null
                return
            }
            Log.i(TAG, "████ AudioRecord initialized ✓")

            // Clear previous data
            Log.i(TAG, "████ Clearing previous data...")
            synchronized(bufferLock) {
                pcmBuffer.reset()
            }
            hasSpeechStarted = false
            speechStartTime = 0
            lastSpeechTime = 0

            Log.i(TAG, "████ Calling audioRecord.startRecording()...")
            audioRecord?.startRecording()
            Log.i(TAG, "████ audioRecord.startRecording() returned, recordingState = ${audioRecord?.recordingState} (expected RECORDSTATE_RECORDING=3)")

            // ═══════════════════════════════════════════════════════════════════
            // NO-TOUCH ZONE: Recording Start Verification (Jan 2026)
            // ═══════════════════════════════════════════════════════════════════
            // AudioRecord.startRecording() can FAIL SILENTLY - the method returns
            // void and doesn't throw exceptions even when microphone is busy.
            // We MUST verify recordingState to detect silent failures.
            //
            // BUG FIXED: Without this check, dialog would appear to start but
            // recording loop would never receive audio data, causing UI to hang.
            // ═══════════════════════════════════════════════════════════════════
            if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "████ AudioRecord.startRecording() FAILED! recordingState is ${audioRecord?.recordingState}, expected RECORDSTATE_RECORDING (3)")
                _state.value = State.Error("Microphone busy or unavailable")
                audioRecord?.release()
                audioRecord = null
                return
            }
            Log.i(TAG, "████ AudioRecord recording confirmed ✓")

            _state.value = State.Recording
            Log.i(TAG, "████ State set to Recording ✓")
            Log.i(TAG, "████ Recording started successfully - about to launch recording loop")

            // Start recording loop
            recordingJob = recordingScope.launch {
                Log.i(TAG, "████ Recording loop STARTED on IO thread")
                val buffer = ShortArray(minBufferSize / 2)  // 16-bit samples
                val recordingStartTime = System.currentTimeMillis()
                var loopCount = 0

                try {
                    Log.i(TAG, "████ Entering while loop - isActive=$isActive, recordingState=${audioRecord?.recordingState}")
                    while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        loopCount++

                        // Log first few iterations and then every 50th
                        if (loopCount <= 3 || loopCount % 50 == 0) {
                            Log.d(TAG, "████ Loop iteration #$loopCount: readCount=$readCount")
                        }

                        if (readCount > 0) {
                            // Convert shorts to bytes and store
                            val byteBuffer = ByteBuffer.allocate(readCount * 2)
                            byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until readCount) {
                                byteBuffer.putShort(buffer[i])
                            }
                            synchronized(bufferLock) {
                                pcmBuffer.write(byteBuffer.array())
                            }

                            // Calculate RMS for this buffer (raw value for silence detection)
                            val rms = calculateRms(buffer, readCount)

                            // ═══════════════════════════════════════════════════════════
                            // NO-TOUCH ZONE: RMS Amplification for UI (Jan 2026)
                            // ═══════════════════════════════════════════════════════════
                            // Raw PCM RMS values (~0.001-0.04) are too small for visible
                            // waveform bars. WaveformCanvas needs ~0.2-0.8 to draw bars.
                            // 20x amplification matches VoiceInputService's normalization.
                            //
                            // BUG FIXED: Without amplification, waveform appeared "dead"
                            // even though audio was being recorded successfully.
                            //
                            // NOTE: Raw RMS is still used for state transitions and
                            // silence detection (see updateStateFromRms below).
                            // ═══════════════════════════════════════════════════════════
                            val amplifiedRms = (rms * 20f).coerceIn(0f, 1f)
                            _rmsLevel.value = amplifiedRms

                            // Log first few RMS values
                            if (loopCount <= 5) {
                                Log.d(TAG, "████ RMS update #$loopCount: rawRms=$rms, amplifiedRms=$amplifiedRms, state=${_state.value}")
                            }

                            // Update state based on audio level
                            updateStateFromRms(rms)

                            // Check for auto-stop conditions
                            val now = System.currentTimeMillis()

                            // Safety limit - stop after max duration
                            if (now - recordingStartTime > MAX_RECORDING_DURATION_MS) {
                                Log.i(TAG, "Max recording duration reached")
                                triggerAutoComplete()
                                break
                            }

                            // Silence detection (only after speech has started)
                            if (hasSpeechStarted && rms < SILENCE_RMS_THRESHOLD) {
                                val speechDuration = (lastSpeechTime - speechStartTime).coerceAtLeast(0)
                                val adaptiveTimeout = calculateAdaptiveTimeout(speechDuration)
                                val silenceDuration = now - lastSpeechTime

                                if (silenceDuration > adaptiveTimeout) {
                                    Log.i(TAG, "Silence detected for ${silenceDuration}ms (threshold: ${adaptiveTimeout}ms)")
                                    _state.value = State.SilenceDetected
                                    triggerAutoComplete()
                                    break
                                }
                            }
                        }
                    }
                    Log.i(TAG, "████ Recording loop exited normally after $loopCount iterations")
                } finally {
                    // ═══════════════════════════════════════════════════════════
                    // NO-TOUCH ZONE: Recording Loop State Reset (Jan 2026)
                    // ═══════════════════════════════════════════════════════════
                    // This finally block ensures state doesn't get stuck when the
                    // recording loop exits unexpectedly (e.g., coroutine cancelled,
                    // AudioRecord released externally, or unexpected errors).
                    //
                    // BUG FIXED: Without this reset, state would remain "Recording"
                    // even after loop exited, blocking subsequent startRecording()
                    // calls and making the UI appear permanently stuck.
                    //
                    // The check for Recording/Speaking states ensures we don't
                    // interfere with normal stop/pause/cancel paths that set
                    // their own final states.
                    // ═══════════════════════════════════════════════════════════
                    val currentState = _state.value
                    Log.i(TAG, "████ Recording loop FINALLY block - loopCount=$loopCount, currentState=$currentState, isActive=$isActive, recordingState=${audioRecord?.recordingState}")
                    if (currentState is State.Recording || currentState is State.Speaking) {
                        Log.w(TAG, "████ Recording loop exited unexpectedly (state was $currentState) - resetting to Idle")
                        _state.value = State.Idle
                        _rmsLevel.value = 0f
                    } else {
                        Log.i(TAG, "████ Recording loop exited with state=$currentState - no reset needed")
                    }
                }
            }
            Log.i(TAG, "████ startRecording() EXIT - recordingJob launched")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting recording", e)
            _state.value = State.Error("Permission denied")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            _state.value = State.Error("Failed to start recording: ${e.message}")
        }
    }

    /**
     * Pause recording (keeps accumulated data).
     */
    fun pauseRecording() {
        audioRecord?.stop()
        recordingJob?.cancel()
        recordingJob = null
        _state.value = State.Idle
        _rmsLevel.value = 0f
        Log.i(TAG, "Recording paused")
    }

    /**
     * Stop recording and get the audio as base64-encoded WAV.
     */
    fun stopAndGetBase64(): String? {
        _state.value = State.Processing

        try {
            audioRecord?.stop()
            recordingJob?.cancel()
            recordingJob = null

            val pcmData: ByteArray
            synchronized(bufferLock) {
                pcmData = pcmBuffer.toByteArray()
                pcmBuffer.reset()
            }

            if (pcmData.isEmpty()) {
                Log.w(TAG, "No audio data recorded")
                _state.value = State.Idle
                return null
            }

            // Convert PCM to WAV
            val wavData = pcmToWav(pcmData)
            val base64 = Base64.encodeToString(wavData, Base64.NO_WRAP)

            Log.i(TAG, "Recording stopped. PCM: ${pcmData.size} bytes, WAV: ${wavData.size} bytes, Base64: ${base64.length} chars")

            _state.value = State.Idle
            _rmsLevel.value = 0f
            return base64
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            _state.value = State.Error("Failed to process audio: ${e.message}")
            return null
        }
    }

    /**
     * Cancel recording and discard data.
     */
    fun cancel() {
        audioRecord?.stop()
        recordingJob?.cancel()
        recordingJob = null
        synchronized(bufferLock) {
            pcmBuffer.reset()
        }
        _state.value = State.Idle
        _rmsLevel.value = 0f
        Log.i(TAG, "Recording cancelled")
    }

    /**
     * Release resources.
     */
    fun release() {
        cancel()
        audioRecord?.release()
        audioRecord = null
        onAutoComplete = null
    }

    /**
     * Clear accumulated audio data (for restart).
     */
    fun clearData() {
        synchronized(bufferLock) {
            pcmBuffer.reset()
        }
        hasSpeechStarted = false
        speechStartTime = 0
        lastSpeechTime = 0
    }

    /**
     * Calculate RMS (root mean square) of audio samples.
     * Returns a normalized value between 0 and 1.
     */
    private fun calculateRms(samples: ShortArray, count: Int): Float {
        if (count == 0) return 0f

        var sumSquares = 0.0
        for (i in 0 until count) {
            val normalized = samples[i].toDouble() / Short.MAX_VALUE
            sumSquares += normalized * normalized
        }

        return sqrt(sumSquares / count).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Update state based on RMS level.
     */
    private fun updateStateFromRms(rms: Float) {
        val now = System.currentTimeMillis()

        when {
            rms >= SPEECH_RMS_THRESHOLD -> {
                if (!hasSpeechStarted) {
                    hasSpeechStarted = true
                    speechStartTime = now
                    Log.d(TAG, "Speech started")
                }
                lastSpeechTime = now
                _state.value = State.Speaking
            }
            rms >= SILENCE_RMS_THRESHOLD -> {
                // Borderline - keep current state or move to recording
                if (_state.value !is State.Speaking) {
                    _state.value = State.Recording
                }
            }
            else -> {
                // Silence - keep previous state for a moment (handled by silence detection)
            }
        }
    }

    /**
     * Calculate adaptive timeout based on speech duration.
     * Longer speech gets slightly more patience.
     */
    private fun calculateAdaptiveTimeout(speechDurationMs: Long): Long {
        val speechSeconds = speechDurationMs / 1000
        val adaptiveBonus = speechSeconds * GRACE_PERIOD_PER_SECOND_MS
        return (SILENCE_DURATION_BASE_MS + adaptiveBonus).coerceAtMost(SILENCE_DURATION_MAX_MS)
    }

    /**
     * Trigger auto-complete callback with recorded audio.
     */
    private fun triggerAutoComplete() {
        val base64 = stopAndGetBase64()
        if (base64 != null) {
            onAutoComplete?.invoke(base64)
        }
    }

    /**
     * Convert PCM data to WAV format with proper headers.
     */
    private fun pcmToWav(pcmData: ByteArray): ByteArray {
        val wavOutput = ByteArrayOutputStream()

        val totalDataLen = pcmData.size + 36  // 36 = header size minus 8
        val byteRate = SAMPLE_RATE * NUM_CHANNELS * BITS_PER_SAMPLE / 8
        val blockAlign = NUM_CHANNELS * BITS_PER_SAMPLE / 8

        // WAV header (44 bytes)
        val header = ByteBuffer.allocate(44)
        header.order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk descriptor
        header.put("RIFF".toByteArray())
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray())

        // fmt sub-chunk
        header.put("fmt ".toByteArray())
        header.putInt(16)  // Sub-chunk size for PCM
        header.putShort(1)  // Audio format: PCM
        header.putShort(NUM_CHANNELS.toShort())
        header.putInt(SAMPLE_RATE)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(BITS_PER_SAMPLE.toShort())

        // data sub-chunk
        header.put("data".toByteArray())
        header.putInt(pcmData.size)

        wavOutput.write(header.array())
        wavOutput.write(pcmData)

        return wavOutput.toByteArray()
    }
}
