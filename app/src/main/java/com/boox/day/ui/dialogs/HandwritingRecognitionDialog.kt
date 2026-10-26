package com.boox.day.ui.dialogs

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ai.AiHandwritingService
import com.boox.day.ai.NoTextFoundException
import com.boox.day.ai.RecognitionModelConfig
import com.boox.day.data.db.Stroke
import com.boox.day.ui.components.EinkLoadingDots
import com.boox.day.ui.components.RotatingFunMessage
import com.boox.day.ui.messages.AiFeedbackMessages
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.utils.MarkdownStripper
import kotlinx.coroutines.launch

/**
 * Actions that can be taken with recognized text
 */
sealed class RecognitionAction {
    data class CopyToClipboard(val text: String) : RecognitionAction()
    data class PlaceOnCurrentPage(val text: String, val strokeIds: List<String>) : RecognitionAction()
    data class ReplaceHandwriting(val text: String, val strokeIds: List<String>) : RecognitionAction()
}

/**
 * Dialog for AI-powered handwriting recognition.
 * Uses a two-stage AI pipeline:
 * 1. Nova 2 Lite (vision) - extracts text from handwriting image
 * 2. GPT-OSS-20B (text) - formats and enhances the extracted text
 *
 * Optimized for e-ink display with black/white theme.
 */
@Composable
fun HandwritingRecognitionDialog(
    strokes: List<Stroke>,
    onAction: (RecognitionAction) -> Unit,
    onDismiss: () -> Unit,
    config: RecognitionModelConfig = RecognitionModelConfig.DEFAULT
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val aiService = remember { AiHandwritingService() }

    var recognitionState by remember {
        mutableStateOf<AiHandwritingService.RecognitionState>(AiHandwritingService.RecognitionState.Idle)
    }
    // Store both versions: markdown for placing on page, display for showing to user
    var markdownText by remember { mutableStateOf("") }
    var displayText by remember { mutableStateOf("") }

    // Start recognition when dialog opens
    LaunchedEffect(strokes) {
        scope.launch {
            aiService.recognizeStrokes(
                strokes = strokes,
                config = config,
                context = context
            ) { state ->
                recognitionState = state
                // Store both text versions when success and consume quota
                if (state is AiHandwritingService.RecognitionState.Success) {
                    markdownText = state.result.formattedText
                    displayText = MarkdownStripper.strip(state.result.formattedText)
                }
            }
        }
    }

    // E-Ink: Use Popup instead of Dialog to avoid system scrim/dimming
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        // fillMaxSize() ensures centering is based on full screen, not parent composable
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(EInkTheme.PopupOverlay.wideWidth) // Tablet-appropriate width, within the screen (Oct 2026: was 520 dp)
                    .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(28.dp) // Generous tablet padding
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Recognize Handwriting",
                            fontSize = EInkTheme.PopupOverlay.titleFontSize,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.PopupOverlay.titleColor
                        )
                        Text(
                            "AI-powered text extraction from your handwriting",
                            fontSize = EInkTheme.Typography.bodySmall,
                            color = EInkTheme.Colors.gray
                        )
                    }
                    Box(
                        modifier = Modifier.size(40.dp).noRippleClickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, "Close", modifier = Modifier.size(24.dp), tint = EInkTheme.Colors.primary)
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Content based on recognition state
                when (val state = recognitionState) {
                    is AiHandwritingService.RecognitionState.Idle,
                    is AiHandwritingService.RecognitionState.CapturingImage -> {
                        ProgressContent("Preparing image...")
                    }

                    is AiHandwritingService.RecognitionState.ExtractingText -> {
                        ProgressContent("Extracting text from handwriting...")
                    }

                    is AiHandwritingService.RecognitionState.InferringMeaning -> {
                        ProgressContent("Understanding handwritten content...")
                    }

                    is AiHandwritingService.RecognitionState.FormattingText -> {
                        ProgressContent("Formatting and cleaning up text...")
                    }

                    is AiHandwritingService.RecognitionState.NoTextFound -> {
                        NoTextContent(
                            message = state.message,
                            onDismiss = onDismiss
                        )
                    }

                    is AiHandwritingService.RecognitionState.Error -> {
                        ErrorContent(
                            message = state.message,
                            onRetry = {
                                recognitionState = AiHandwritingService.RecognitionState.Idle
                                scope.launch {
                                    aiService.recognizeStrokes(
                                        strokes = strokes,
                                        config = config,
                                        context = context
                                    ) { newState ->
                                        recognitionState = newState
                                        if (newState is AiHandwritingService.RecognitionState.Success) {
                                            markdownText = newState.result.formattedText
                                            displayText = MarkdownStripper.strip(newState.result.formattedText)
                                        }
                                    }
                                }
                            }
                        )
                    }

                    is AiHandwritingService.RecognitionState.Success -> {
                        SuccessContent(
                            displayText = displayText,
                            hasFormatting = state.result.hasFormatting,
                            onCopy = {
                                // Copy uses clean displayText (no markdown symbols)
                                onAction(RecognitionAction.CopyToClipboard(displayText))
                            },
                            onReplace = {
                                // Replace uses markdownText for formatting support
                                onAction(RecognitionAction.ReplaceHandwriting(
                                    markdownText,
                                    strokes.map { it.id }
                                ))
                            },
                            onPlaceBelow = {
                                // Place uses markdownText for formatting support
                                onAction(RecognitionAction.PlaceOnCurrentPage(
                                    markdownText,
                                    strokes.map { it.id }
                                ))
                            }
                        )
                        // Read on the tablet because AI Features is off or no OpenRouter key is saved (Oct 2026)
                        val aiOff = !com.boox.day.data.datastore.GlobalAppSettings.current.aiEnabled
                        if (aiOff || !com.boox.day.security.UserOpenRouterKey.hasKey) {
                            Text(
                                text = if (aiOff) "Read on the tablet, because AI Features is off."
                                    else "Read on the tablet. Add an OpenRouter key in AI Settings for better results.",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.darkGray
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Progress indicator with message - tablet-optimized layout.
 * Uses EinkLoadingDots instead of CircularProgressIndicator
 * because e-ink displays can't render smooth animations.
 */
@Composable
private fun ProgressContent(technicalMessage: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp), // More spacious for tablet
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EinkLoadingDots()
            Spacer(Modifier.height(24.dp))

            // Fun Message (Primary) - uses EInkTheme.AiProcessingFeedback defaults
            RotatingFunMessage(
                messageProvider = { AiFeedbackMessages.getRandomOcrMessage() }
            )

            Spacer(Modifier.height(12.dp))

            // Technical Message (Secondary)
            Text(
                technicalMessage,
                fontSize = EInkTheme.AiProcessingFeedback.technicalFontSize,
                color = EInkTheme.AiProcessingFeedback.technicalColor
            )

            Spacer(Modifier.height(16.dp))

            // Helpful info text
            Text(
                "This may take a moment. Please wait...",
                fontSize = EInkTheme.AiProcessingFeedback.helperFontSize,
                color = EInkTheme.AiProcessingFeedback.helperColor
            )
        }
    }
}

/**
 * Content shown when no text is found in selection
 */
@Composable
private fun NoTextContent(
    message: String,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = EInkTheme.Colors.gray,
            modifier = Modifier.size(EInkTheme.Global.iconSizeLarge)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            color = EInkTheme.Colors.darkGray,
            fontSize = EInkTheme.Typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onDismiss,
            border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = EInkTheme.Colors.primary)
        ) {
            Text("Close")
        }
    }
}

/**
 * Content shown on error with retry option
 */
@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = EInkTheme.Colors.darkGray,
            modifier = Modifier.size(EInkTheme.Global.iconSizeLarge)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            color = EInkTheme.Colors.darkGray,
            fontSize = EInkTheme.Typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) {
            Text("Retry", color = EInkTheme.Colors.primary)
        }
    }
}

/**
 * Content shown on successful recognition with action buttons - tablet-optimized.
 * Text field is read-only - user can edit on canvas after placing.
 *
 * @param displayText Clean text without markdown (for display and clipboard)
 * @param hasFormatting Whether the AI detected formatting
 * @param onCopy Copy clean text to clipboard
 * @param onReplace Replace handwriting with formatted text
 * @param onPlaceBelow Place formatted text below handwriting
 */
@Composable
private fun SuccessContent(
    displayText: String,
    hasFormatting: Boolean,
    onCopy: () -> Unit,
    onReplace: () -> Unit,
    onPlaceBelow: () -> Unit
) {
    // Read-only text field showing the cleaned result
    OutlinedTextField(
        value = displayText,
        onValueChange = { /* Read-only */ },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp, max = 220.dp), // Larger for tablet
        readOnly = true,
        label = { Text("Recognized text") },
        colors = TextFieldDefaults.outlinedTextFieldColors(
            focusedBorderColor = EInkTheme.TextField.borderColor,
            unfocusedBorderColor = EInkTheme.TextField.borderColor,
            cursorColor = EInkTheme.Colors.primary,
            focusedLabelColor = EInkTheme.Colors.primary,
            disabledTextColor = EInkTheme.Colors.primary,
            disabledLabelColor = EInkTheme.Colors.gray
        )
    )

    // Helper text below field
    Spacer(Modifier.height(8.dp))
    Text(
        "This text is ready for use. If you place it on a page, you can edit it there.",
        fontSize = EInkTheme.Typography.labelSmall,
        color = EInkTheme.Colors.gray
    )

    if (hasFormatting) {
        Spacer(Modifier.height(4.dp))
        Text(
            "The AI has formatted and cleaned up your text",
            fontSize = EInkTheme.Typography.labelSmall,
            color = EInkTheme.Colors.darkGray,
            fontWeight = FontWeight.Medium
        )
    }

    Spacer(Modifier.height(24.dp))

    // Action section
    Text(
        "Choose an action",
        fontSize = EInkTheme.Typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = EInkTheme.Colors.primary
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "Select what to do with the recognized text",
        fontSize = EInkTheme.Typography.bodySmall,
        color = EInkTheme.Colors.gray
    )
    Spacer(Modifier.height(12.dp))

    // Copy to clipboard - uses clean displayText
    RecognitionActionRow(
        icon = Icons.Default.ContentCopy,
        text = "Copy to clipboard",
        description = "Copy the clean text to paste elsewhere",
        onClick = onCopy
    )

    Spacer(Modifier.height(8.dp))

    // Replace handwriting with text - uses markdownText
    RecognitionActionRow(
        icon = Icons.Default.SwapHoriz,
        text = "Replace handwriting with text",
        description = "Removes the handwriting and inserts formatted text",
        onClick = onReplace
    )

    Spacer(Modifier.height(8.dp))

    // Place on current page (below strokes) - uses markdownText
    RecognitionActionRow(
        icon = Icons.Default.NoteAdd,
        text = "Place below handwriting",
        description = "Adds formatted text below your original notes",
        onClick = onPlaceBelow
    )
}

/**
 * List-style action row for recognition results - tablet-optimized touch target.
 * Shows icon, text, and description with comfortable spacing.
 * @param description Explains what this action does
 */
@Composable
private fun RecognitionActionRow(
    icon: ImageVector,
    text: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onClick),
        backgroundColor = EInkTheme.Colors.background,
        elevation = 0.dp,
        border = BorderStroke(1.dp, EInkTheme.Colors.primary),
        shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), // Larger touch target
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp), // Larger icon for tablet
                tint = EInkTheme.Colors.primary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text,
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray
                )
            }
        }
    }
}

