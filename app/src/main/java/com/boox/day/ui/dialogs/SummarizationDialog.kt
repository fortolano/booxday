package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import com.boox.day.ui.noRippleClickable
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
import com.boox.day.ai.AiSummarizationService
import com.boox.day.ai.MemoPageContent
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.TextAnnotation
import com.boox.day.ui.components.EinkLoadingDots
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.components.RotatingFunMessage
import com.boox.day.ui.messages.AiFeedbackMessages
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.utils.MarkdownStripper
import kotlinx.coroutines.launch

/**
 * Result of summarization to be handled by caller.
 * Now includes action type for user-controlled result handling.
 */
sealed class SummarizationAction {
    /** Copy summary to clipboard */
    data class CopyToClipboard(val summary: String) : SummarizationAction()

    /** Place summary on new memo page(s) */
    data class PlaceOnNewPage(val summary: String) : SummarizationAction()
}

/**
 * Data for multiple pages to be summarized
 */
data class PageDataForSummarization(
    val pageId: String,
    val pageIndex: Int,
    val strokes: List<Stroke>,
    val textAnnotations: List<TextAnnotation>
)

/**
 * Summarization Dialog - AI-powered notes summarization with scope selection.
 *
 * Flow:
 * 1. Show scope selection (Current Page / All Pages)
 * 2. Process based on selected scope
 * 3. Show editable result dialog
 * 4. User picks action: Copy to clipboard OR Place on new page
 *
 * @param currentPageStrokes Strokes on the currently active page
 * @param currentPageTextAnnotations Text annotations on current page
 * @param allPagesData All pages for the current day (for "All Pages" scope)
 * @param totalPages Total number of pages for the day
 * @param onAction Callback with user's chosen action
 * @param onDismiss Callback when dialog is dismissed
 */
@Composable
fun SummarizationDialog(
    currentPageStrokes: List<Stroke>,
    currentPageTextAnnotations: List<TextAnnotation> = emptyList(),
    allPagesData: List<PageDataForSummarization> = emptyList(),
    totalPages: Int = 1,
    onAction: (SummarizationAction) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Dialog state machine
    var dialogPhase by remember { mutableStateOf(DialogPhase.ScopeSelection) }
    var selectedScope by remember { mutableStateOf<AiScope?>(null) }
    var summaryResult by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var processingProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) } // current, total
    var processingStartTime by remember { mutableStateOf(0L) }
    var processingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Minimum display time for processing dialog (3 messages × 3 seconds = 9 seconds)
    val minProcessingDisplayMs = 9000L

    fun onCancel() {
        processingJob?.cancel()
        processingJob = null
        onDismiss()
    }

    // Handle scope selection
    fun onScopeSelected(aiScope: AiScope) {
        selectedScope = aiScope
        dialogPhase = DialogPhase.Processing
        errorMessage = null
        processingStartTime = System.currentTimeMillis()

        processingJob = scope.launch {
            when (aiScope) {
                is AiScope.CurrentPage -> {
                    // Single page summarization
                    val content = AiSummarizationService.SummarizationContent(
                        currentPageStrokes,
                        currentPageTextAnnotations
                    )
                    AiSummarizationService.summarizeContent(content) { state ->
                        when (state) {
                            is AiSummarizationService.SummarizationState.Success -> {
                                scope.launch {
                                    // Ensure minimum display time for fun messages
                                    val elapsed = System.currentTimeMillis() - processingStartTime
                                    if (elapsed < minProcessingDisplayMs) {
                                        kotlinx.coroutines.delay(minProcessingDisplayMs - elapsed)
                                    }
                                    summaryResult = state.summary
                                    dialogPhase = DialogPhase.Result
                                }
                            }
                            is AiSummarizationService.SummarizationState.Error -> {
                                errorMessage = state.message
                                dialogPhase = DialogPhase.Error
                            }
                            is AiSummarizationService.SummarizationState.NoContent -> {
                                errorMessage = state.message
                                dialogPhase = DialogPhase.Error
                            }
                            else -> { /* Processing states - handled by UI */ }
                        }
                    }
                }
                is AiScope.AllPages -> {
                    // Multi-page summarization: every page of the day, read whole (Oct 2026). Pages not
                    // opened this session have no strokes in memory, so they were left out silently.
                    val pages = allPagesData.map { pageData ->
                        val page = MemoPageContent.load(context, pageData.pageId)
                        AiSummarizationService.PageSummarizationData(
                            pageId = pageData.pageId,
                            pageIndex = pageData.pageIndex,
                            strokes = page.strokes,
                            textAnnotations = page.textAnnotations
                        )
                    }
                    AiSummarizationService.summarizeMultiplePages(pages) { state ->
                        when (state) {
                            is AiSummarizationService.MultiPageSummarizationState.ProcessingPage -> {
                                processingProgress = state.current to state.total
                            }
                            is AiSummarizationService.MultiPageSummarizationState.AggregatingResults -> {
                                processingProgress = null // Clear page progress
                            }
                            is AiSummarizationService.MultiPageSummarizationState.Success -> {
                                scope.launch {
                                    // Ensure minimum display time for fun messages
                                    val elapsed = System.currentTimeMillis() - processingStartTime
                                    if (elapsed < minProcessingDisplayMs) {
                                        kotlinx.coroutines.delay(minProcessingDisplayMs - elapsed)
                                    }
                                    summaryResult = state.finalSummary
                                    dialogPhase = DialogPhase.Result
                                }
                            }
                            is AiSummarizationService.MultiPageSummarizationState.Error -> {
                                errorMessage = state.message
                                dialogPhase = DialogPhase.Error
                            }
                            is AiSummarizationService.MultiPageSummarizationState.NoContent -> {
                                errorMessage = state.message
                                dialogPhase = DialogPhase.Error
                            }
                            else -> { /* Idle state */ }
                        }
                    }
                }
            }
        }
    }

    // Retry handler
    fun onRetry() {
        selectedScope?.let { onScopeSelected(it) }
    }

    // Handle result action
    fun onResultAction(action: AiResultAction) {
        when (action) {
            is AiResultAction.CopyToClipboard -> {
                onAction(SummarizationAction.CopyToClipboard(action.text))
            }
            is AiResultAction.PlaceOnNewPage -> {
                onAction(SummarizationAction.PlaceOnNewPage(action.text))
            }
        }
        onDismiss()
    }

    // Render based on current phase
    when (dialogPhase) {
        DialogPhase.ScopeSelection -> {
            AiScopeSelectionDialog(
                featureName = "Summarize",
                totalPages = totalPages,
                onScopeSelected = { onScopeSelected(it) },
                onDismiss = onDismiss
            )
        }

        DialogPhase.Processing -> {
            SummarizationProcessingDialog(
                scope = selectedScope,
                progress = processingProgress,
                onCancel = { onCancel() }
            )
        }

        DialogPhase.Error -> {
            SummarizationErrorDialog(
                message = errorMessage ?: "An error occurred",
                onRetry = { onRetry() },
                onDismiss = onDismiss
            )
        }

        DialogPhase.Result -> {
            // Strip markdown for display, keep original for placing on page
            val displayText = remember(summaryResult) {
                MarkdownStripper.strip(summaryResult)
            }
            AiResultDialog(
                title = "Summary",
                markdownText = summaryResult,
                displayText = displayText,
                onAction = { onResultAction(it) },
                onDismiss = onDismiss
            )
        }
    }
}

/**
 * Dialog phases for state machine
 */
private enum class DialogPhase {
    ScopeSelection,
    Processing,
    Error,
    Result
}

/**
 * Processing dialog showing progress - tablet-optimized layout
 */
@Composable
private fun SummarizationProcessingDialog(
    scope: AiScope?,
    progress: Pair<Int, Int>?,  // current, total
    onCancel: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = { /* Don't dismiss during processing */ },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        // fillMaxSize() ensures centering is based on full screen, not parent composable
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(EInkTheme.PopupOverlay.maxWidth) // Tablet-appropriate width, within the screen (Oct 2026: was 480 dp)
                    .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                    .border(
                        EInkTheme.PopupOverlay.borderWidth,
                        EInkTheme.PopupOverlay.borderColor,
                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()  // Fill the box width so centering works
                        .padding(32.dp), // Generous tablet padding
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title
                    Text(
                        text = "Summarizing Your Notes",
                        fontSize = EInkTheme.PopupOverlay.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.PopupOverlay.titleColor,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(12.dp))

                    // Scope indicator with more context
                    val scopeText = when (scope) {
                        is AiScope.CurrentPage -> "Analyzing the current memo page"
                        is AiScope.AllPages -> "Analyzing all memo pages for today"
                        null -> "Preparing analysis..."
                    }
                    Text(
                        text = scopeText,
                        fontSize = EInkTheme.Typography.bodyLarge,
                        color = EInkTheme.Colors.gray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(32.dp))

                    // Loading indicator
                    EinkLoadingDots()

                    Spacer(Modifier.height(28.dp))

                    // Fun message - summarization-specific quotes, rotates every 3 seconds
                    RotatingFunMessage(
                        messageProvider = { AiFeedbackMessages.getRandomSummarizationMessage() },
                        intervalMs = 3000L
                    )

                    // Progress indicator for multi-page
                    if (progress != null) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(8.dp),
                                shape = CircleShape,
                                color = EInkTheme.Colors.gray
                            ) {}
                            Text(
                                text = "Processing page ${progress.first} of ${progress.second}",
                                fontSize = EInkTheme.Typography.bodyLarge,
                                color = EInkTheme.Colors.gray
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Cancel button
                    EinkSecondaryButton(
                        text = "Cancel",
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Error dialog with retry option
 */
@Composable
private fun SummarizationErrorDialog(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .width(EInkTheme.PopupOverlay.wideWidth)
                .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .border(
                    EInkTheme.PopupOverlay.borderWidth,
                    EInkTheme.PopupOverlay.borderColor,
                    RoundedCornerShape(EInkTheme.Global.cornerRadius)
                )
        ) {
            Column(
                modifier = Modifier.padding(EInkTheme.PopupOverlay.padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = EInkTheme.Colors.darkGray
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = message,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                // Button row: [Close (escape)] [Retry (primary)]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    // Close button (escape — leftmost)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Button.height)
                            .background(EInkTheme.Button.background, RoundedCornerShape(6.dp))
                            .border(
                                EInkTheme.Button.borderWidth,
                                EInkTheme.Button.borderColor,
                                RoundedCornerShape(6.dp)
                            )
                            .noRippleClickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Close", color = EInkTheme.Button.textColor, fontSize = EInkTheme.Typography.bodyMedium)
                    }

                    // Retry button (primary — rightmost)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Button.height)
                            .background(EInkTheme.Button.primaryBackground, RoundedCornerShape(6.dp))
                            .noRippleClickable { onRetry() },
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
                                tint = EInkTheme.Button.primaryTextColor
                            )
                            Text(
                                "Retry",
                                color = EInkTheme.Button.primaryTextColor,
                                fontSize = EInkTheme.Typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

