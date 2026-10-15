package com.boox.day.editor.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ai.TaskAiService
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.datastore.VoiceTextMode
import com.boox.day.speech.AudioRecorder
import com.boox.day.speech.StartResult
import com.boox.day.speech.TranscriptionResult
import com.boox.day.speech.TranscriptionService
import com.boox.day.speech.TranscriptionSource
import com.boox.day.speech.TranscriptionState
import com.boox.day.speech.VoiceInputState
import com.boox.day.ui.components.EinkLoadingDotsInline
import com.boox.day.ui.components.EinkPrimaryButton
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.components.RotatingFunMessage
import com.boox.day.ui.components.WaveformCanvas
import com.boox.day.ui.dialogs.VoiceSettingsDialog
import com.boox.day.ui.messages.AiFeedbackMessages
import com.boox.day.ui.noRippleClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Voice to Task Dialog - Voice input to create tasks.
 *
 * This dialog provides a seamless voice input experience for tasks using
 * the unified TranscriptionService:
 *
 * 1. TranscriptionService handles voice-to-text:
 *    - SpeechRecognizer (free, unlimited) when Google Search app is installed
 *    - Gemini 2.0 Flash Lite (daily limit per voice feature) as fallback
 * 2. Shows animated waveform responding to voice
 * 3. Smart silence detection auto-sends to AI when user finishes speaking
 * 4. Two-stage AI pipeline: transcription (via TranscriptionService) → parse
 * 5. Returns parsed tasks to caller for preview/confirmation
 *
 * Example voice inputs:
 * - "Add task buy milk"
 * - "Create task call mom for tomorrow"
 * - "Add buy groceries and pick up dry cleaning" (multiple tasks)
 * - "Create task finish report for December 20th and follow up on December 22nd"
 */
@Composable
fun VoiceToTaskDialog(
    currentDate: LocalDate,
    onTasksParsed: (TaskAiService.VoiceTaskResult) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Permission state
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TRANSCRIPTION SERVICE (THE unified voice-to-text service)
    // ═══════════════════════════════════════════════════════════════════════════

    val audioRecorder = remember { AudioRecorder(context) }
    val transcriptionService = remember { TranscriptionService(context, audioRecorder) }

    // TranscriptionService state
    val transcriptionState by transcriptionService.state.collectAsState()
    val activeSource by transcriptionService.activeSource.collectAsState()

    // AudioRecorder state (for waveform visualization in Gemini mode)
    val recorderState by audioRecorder.state.collectAsState()
    val rmsLevel by audioRecorder.rmsLevel.collectAsState()

    // SpeechRecognizer state (for waveform visualization in SpeechRecognizer mode)
    val speechRecognizerState by transcriptionService.speechRecognizerState.collectAsState()
    val speechRecognizerRmsLevel by transcriptionService.speechRecognizerRmsLevel.collectAsState()

    // Effective state based on active source (use correct service's state)
    val effectiveRmsLevel = when (activeSource) {
        TranscriptionSource.SPEECH_RECOGNIZER -> speechRecognizerRmsLevel
        else -> rmsLevel
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DIALOG STATES
    // ═══════════════════════════════════════════════════════════════════════════

    var showSettingsDialog by remember { mutableStateOf(false) }

    // AI processing state with stage tracking for user feedback
    var isAiProcessing by remember { mutableStateOf(false) }
    var aiProcessingStage by remember { mutableStateOf("") }
    var aiError by remember { mutableStateOf<String?>(null) }

    // Status message - use correct state based on activeSource (technical message only)
    val statusMessage = when {
        isAiProcessing -> aiProcessingStage.ifEmpty { "Processing..." }
        aiError != null -> aiError!!
        transcriptionState == TranscriptionState.PROCESSING -> "Processing transcription..."
        activeSource == TranscriptionSource.SPEECH_RECOGNIZER -> when (speechRecognizerState) {
            VoiceInputState.Idle -> "Getting microphone ready..."
            VoiceInputState.Listening -> "Listening — speak now"
            VoiceInputState.Speaking -> "Hearing you clearly..."
            VoiceInputState.SilenceDetected -> "Finishing up..."
            VoiceInputState.Processing -> "Processing..."
            VoiceInputState.Paused -> "Paused"
            VoiceInputState.AutoComplete -> "Almost done..."
            is VoiceInputState.Error -> (speechRecognizerState as VoiceInputState.Error).message
        }
        else -> when (recorderState) {
            is AudioRecorder.State.Idle -> "Getting microphone ready..."
            is AudioRecorder.State.Recording -> "Listening — speak now"
            is AudioRecorder.State.Speaking -> "Hearing you clearly..."
            is AudioRecorder.State.SilenceDetected -> "Finishing up..."
            is AudioRecorder.State.Processing -> "Preparing audio..."
            is AudioRecorder.State.Error -> (recorderState as AudioRecorder.State.Error).message
        }
    }

    // Effective recording state based on active source (for button states)
    val isEffectivelyRecording = when (activeSource) {
        TranscriptionSource.SPEECH_RECOGNIZER ->
            speechRecognizerState is VoiceInputState.Listening ||
            speechRecognizerState is VoiceInputState.Speaking ||
            speechRecognizerState is VoiceInputState.SilenceDetected
        else ->
            recorderState is AudioRecorder.State.Recording ||
            recorderState is AudioRecorder.State.Speaking ||
            recorderState is AudioRecorder.State.SilenceDetected
    }

    val isEffectivelyPaused = when (activeSource) {
        TranscriptionSource.SPEECH_RECOGNIZER ->
            speechRecognizerState is VoiceInputState.Paused ||
            (speechRecognizerState is VoiceInputState.Idle && !isAiProcessing)
        else ->
            recorderState is AudioRecorder.State.Idle && !isAiProcessing
    }

    val hasEffectiveError = when (activeSource) {
        TranscriptionSource.SPEECH_RECOGNIZER ->
            aiError != null || speechRecognizerState is VoiceInputState.Error
        else ->
            aiError != null || recorderState is AudioRecorder.State.Error
    }

    val isEffectivelyProcessing = when (activeSource) {
        TranscriptionSource.SPEECH_RECOGNIZER ->
            isAiProcessing || speechRecognizerState is VoiceInputState.Processing
        else ->
            isAiProcessing || recorderState is AudioRecorder.State.Processing
    }

    // Show fun messages during AI processing OR transcription processing
    val showFunMessage = isAiProcessing || transcriptionState == TranscriptionState.PROCESSING

    // ═══════════════════════════════════════════════════════════════════════════
    // TRANSCRIPTION CONTROL
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Start transcription using TranscriptionService.
     * Handles all StartResult cases (Started, NeedsConfiguration, NeedsApiKey).
     */
    fun startRecording() {
        scope.launch {
            aiError = null
            when (val result = transcriptionService.startTranscribing()) {
                StartResult.Started -> {
                    // Recording in progress - UI shows waveform
                }
                StartResult.NeedsConfiguration -> {
                    showSettingsDialog = true
                }
                StartResult.NeedsApiKey -> {
                    // AI voice mode needs the user's OpenRouter key: the key popup is open instead
                    onDismiss()
                }
            }
        }
    }

    /**
     * Stop transcription and process the result.
     * Handles all TranscriptionResult cases.
     *
     * @param preCapturedAudio Optional pre-captured audio from auto-complete callback.
     *                         Pass this to avoid double-consumption of audio buffer.
     */
    fun onComplete(preCapturedAudio: String? = null) {
        scope.launch {
            isAiProcessing = true
            aiError = null

            when (val result = transcriptionService.stopAndGetResult(preCapturedAudio)) {
                is TranscriptionResult.Success -> {
                    // Parse the transcribed text (same method regardless of source)
                    aiProcessingStage = "Understanding what you said..."
                    val parsed = TaskAiService.parseTranscribedText(
                        transcribedText = result.text,
                        currentDate = currentDate,
                        onStageChange = { stage -> aiProcessingStage = stage }
                    )

                    isAiProcessing = false

                    if (parsed.hasValidTasks) {
                        onTasksParsed(parsed)
                        onDismiss()
                    } else {
                        aiError = parsed.error ?: parsed.originalIntent.ifBlank {
                            "Couldn't understand. Please try again."
                        }
                    }
                }

                is TranscriptionResult.Error -> {
                    isAiProcessing = false
                    aiError = result.message
                }
            }
        }
    }

    // Set up UNIFIED auto-complete callback (works for both SpeechRecognizer and Gemini modes)
    LaunchedEffect(Unit) {
        transcriptionService.setOnAutoCompleteListener { base64 ->
            // Auto-complete triggered:
            // - SpeechRecognizer mode: base64 is null (text already in VoiceInputService)
            // - Gemini mode: base64 has the audio data
            onComplete(preCapturedAudio = base64)
        }
    }

    // Auto-start recording after dialog opens
    LaunchedEffect(hasPermission) {
        if (hasPermission && AudioRecorder.isAvailable(context)) {
            delay(500)
            startRecording()
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            transcriptionService.release()  // This clears listeners internally
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UI
    // ═══════════════════════════════════════════════════════════════════════════

    // Use Popup for e-ink friendly (no gray overlay)
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = {
            transcriptionService.cancel()
            onDismiss()
        },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = false // Don't dismiss on outside click during recording
        )
    ) {
        Box(
            modifier = Modifier
                .width(EInkTheme.PopupOverlay.wideWidth)
                .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(EInkTheme.ModernDialog.padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = "Voice to Task",
                    fontSize = EInkTheme.ModernDialog.titleFontSize,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.ModernDialog.titleColor
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Say your task, like \"Buy milk\" or \"Call mom tomorrow\"",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                if (!hasPermission) {
                    // Permission request
                    VoiceTaskPermissionContent(
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                } else if (!AudioRecorder.isAvailable(context)) {
                    // Not available
                    VoiceTaskNotAvailableContent()
                } else {
                    // Main voice input UI
                    VoiceTaskInputContent(
                        rmsLevel = effectiveRmsLevel,
                        statusMessage = statusMessage,
                        isRecording = isEffectivelyRecording,
                        isPaused = isEffectivelyPaused,
                        isProcessing = isEffectivelyProcessing,
                        hasError = hasEffectiveError,
                        showFunMessage = showFunMessage,
                        voiceInputState = if (activeSource == TranscriptionSource.SPEECH_RECOGNIZER) speechRecognizerState else null,
                        recorderState = if (activeSource != TranscriptionSource.SPEECH_RECOGNIZER) recorderState else null,
                        onPause = { transcriptionService.pause() },
                        onResume = { transcriptionService.resume() },
                        onRestart = {
                            aiError = null
                            transcriptionService.cancel()
                            audioRecorder.clearData()
                            startRecording()
                        }
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Cancel button
                EinkSecondaryButton(
                    text = "Cancel",
                    onClick = {
                        transcriptionService.cancel()
                        onDismiss()
                    },
                    width = 120.dp
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NESTED DIALOGS
    // ═══════════════════════════════════════════════════════════════════════════

    if (showSettingsDialog) {
        val settings = GlobalAppSettings.current
        VoiceSettingsDialog(
            hasGoogleSearchApp = transcriptionService.isSpeechRecognitionAvailable(),
            currentMode = settings.voiceTextMode,
            onSelectMode = { mode ->
                GlobalAppSettings.update(settings.copy(voiceTextMode = mode))
                showSettingsDialog = false
                // Retry after configuration
                startRecording()
            },
            onInstallGoogleSearch = {
                // User went to Play Store - keep dialog open
            },
            onDismiss = {
                showSettingsDialog = false
                // Can't proceed without config - dismiss main dialog
                onDismiss()
            }
        )
    }

}

@Composable
private fun VoiceTaskPermissionContent(
    onRequestPermission: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.MicOff,
            contentDescription = "Microphone",
            modifier = Modifier.size(48.dp),
            tint = EInkTheme.Colors.gray
        )

        Text(
            text = "Microphone permission required",
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.gray,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Voice input needs microphone access to hear and transcribe your speech.",
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.gray,
            textAlign = TextAlign.Center
        )

        EinkPrimaryButton(
            text = "Grant Permission",
            onClick = onRequestPermission,
            width = 200.dp
        )
    }
}

@Composable
private fun VoiceTaskNotAvailableContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Warning",
            modifier = Modifier.size(48.dp),
            tint = EInkTheme.Colors.gray
        )

        Text(
            text = "Speech recognition is not available on this device",
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun VoiceTaskInputContent(
    rmsLevel: Float,
    statusMessage: String,
    isRecording: Boolean,
    isPaused: Boolean,
    isProcessing: Boolean,
    hasError: Boolean,
    showFunMessage: Boolean,
    voiceInputState: VoiceInputState?,  // Non-null when using SpeechRecognizer
    recorderState: AudioRecorder.State?, // Non-null when using AudioRecorder/Gemini
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Waveform visualization
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.gray, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            VoiceTaskWaveformCanvas(
                rmsLevel = rmsLevel,
                voiceInputState = voiceInputState,
                recorderState = recorderState,
                isProcessing = isProcessing,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(Modifier.height(12.dp))

        // Fun message during processing (shown above technical status)
        if (showFunMessage) {
            RotatingFunMessage(
                messageProvider = { AiFeedbackMessages.getRandomAudioMessage() }
            )
            Spacer(Modifier.height(8.dp))
        }

        // Status indicator (technical message)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status dot
            Surface(
                modifier = Modifier.size(8.dp),
                shape = CircleShape,
                color = when {
                    hasError -> EInkTheme.Colors.darkGray
                    isProcessing -> EInkTheme.Colors.gray
                    isRecording -> EInkTheme.Colors.primary
                    isPaused -> EInkTheme.Colors.gray
                    else -> EInkTheme.Colors.lightGray
                }
            ) {}

            Text(
                text = statusMessage,
                fontSize = EInkTheme.Typography.bodyLarge,
                color = if (hasError) EInkTheme.Colors.darkGray else EInkTheme.Colors.gray
            )

            if (isProcessing) {
                EinkLoadingDotsInline(color = EInkTheme.Colors.primary)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            if (!isProcessing) {
                if (isRecording) {
                    // Pause button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Button.height)
                            .background(EInkTheme.Button.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                            .border(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                            .noRippleClickable { onPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = EInkTheme.Button.textColor
                            )
                            Text("Pause", color = EInkTheme.Button.textColor, fontSize = EInkTheme.Typography.bodyMedium)
                        }
                    }
                } else if (isPaused || hasError) {
                    // Resume/Try Again button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Button.height)
                            .background(EInkTheme.Button.primaryBackground, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                            .noRippleClickable { if (hasError) onRestart() else onResume() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = EInkTheme.Button.primaryTextColor
                            )
                            Text(
                                if (hasError) "Try Again" else "Resume",
                                color = EInkTheme.Button.primaryTextColor,
                                fontSize = EInkTheme.Typography.bodyMedium
                            )
                        }
                    }
                }

                // Restart button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(EInkTheme.Button.height)
                        .background(EInkTheme.Button.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                        .border(EInkTheme.Button.borderWidth, EInkTheme.Colors.gray, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                        .noRippleClickable { onRestart() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = EInkTheme.Button.textColor
                        )
                        Text("Restart", color = EInkTheme.Button.textColor, fontSize = EInkTheme.Typography.bodyMedium)
                    }
                }
            } else {
                // Processing - disabled button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EInkTheme.Button.height)
                        .background(EInkTheme.Button.disabledBackground, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                        .border(EInkTheme.Button.borderWidth, EInkTheme.Button.disabledBorderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EinkLoadingDotsInline(color = EInkTheme.Button.disabledTextColor)
                        Text("Processing...", color = EInkTheme.Button.disabledTextColor, fontSize = EInkTheme.Typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceTaskWaveformCanvas(
    rmsLevel: Float,
    voiceInputState: VoiceInputState?,  // Non-null when using SpeechRecognizer
    recorderState: AudioRecorder.State?, // Non-null when using AudioRecorder/Gemini
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    // Map state to VoiceInputState for WaveformCanvas
    // Use VoiceInputState directly if available, otherwise map from AudioRecorder.State
    val mappedState = if (voiceInputState != null) {
        // SpeechRecognizer mode - use state directly
        voiceInputState
    } else {
        // AudioRecorder/Gemini mode - map to VoiceInputState
        when {
            isProcessing -> VoiceInputState.Processing
            recorderState is AudioRecorder.State.Speaking -> VoiceInputState.Speaking
            recorderState is AudioRecorder.State.Recording -> VoiceInputState.Listening
            recorderState is AudioRecorder.State.SilenceDetected -> VoiceInputState.SilenceDetected
            recorderState is AudioRecorder.State.Error -> VoiceInputState.Error(
                (recorderState as AudioRecorder.State.Error).message
            )
            else -> VoiceInputState.Idle
        }
    }

    WaveformCanvas(
        rmsLevel = rmsLevel,
        state = mappedState,
        modifier = modifier
    )
}
