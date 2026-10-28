package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import com.boox.day.ui.noRippleClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.boox.day.ai.AiTranslationService
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
 * Result of translation to be handled by caller.
 * Now includes action type for user-controlled result handling.
 */
sealed class TranslationAction {
    /** Copy translation to clipboard */
    data class CopyToClipboard(val translation: String, val detectedLanguages: String) : TranslationAction()

    /** Place translation on new memo page(s) */
    data class PlaceOnNewPage(val translation: String, val detectedLanguages: String) : TranslationAction()
}

/**
 * Data for multiple pages to be translated
 */
data class PageDataForTranslation(
    val pageId: String,
    val pageIndex: Int,
    val strokes: List<Stroke>,
    val textAnnotations: List<TextAnnotation>
)

/**
 * Available target languages for translation.
 * English first (most common target), then alphabetized.
 */
val SUPPORTED_LANGUAGES = listOf(
    "English",
    "Arabic",
    "Cantonese",
    "Danish",
    "Dutch",
    "French",
    "German",
    "Greek",
    "Hebrew",
    "Hindi",
    "Indonesian",
    "Italian",
    "Japanese",
    "Korean",
    "Mandarin",
    "Norwegian",
    "Polish",
    "Portuguese",
    "Romanian",
    "Russian",
    "Spanish",
    "Swedish",
    "Tagalog",
    "Thai",
    "Turkish",
    "Ukrainian",
    "Vietnamese"
)

/**
 * Translation Dialog - AI-powered translation with scope selection.
 *
 * Flow:
 * 1. Show language selection
 * 2. Show scope selection (Current Page / All Pages)
 * 3. Process based on selected scope
 * 4. Show editable result dialog
 * 5. User picks action: Copy to clipboard OR Place on new page
 *
 * @param currentPageStrokes Strokes on the currently active page
 * @param currentPageTextAnnotations Text annotations on current page
 * @param allPagesData All pages for the current day (for "All Pages" scope)
 * @param totalPages Total number of pages for the day
 * @param onAction Callback with user's chosen action
 * @param onDismiss Callback when dialog is dismissed
 */
@Composable
fun TranslationDialog(
    currentPageStrokes: List<Stroke>,
    currentPageTextAnnotations: List<TextAnnotation> = emptyList(),
    allPagesData: List<PageDataForTranslation> = emptyList(),
    totalPages: Int = 1,
    onAction: (TranslationAction) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Dialog state machine
    var dialogPhase by remember { mutableStateOf(TranslationDialogPhase.LanguageSelection) }
    var selectedLanguage by remember { mutableStateOf("English") }
    var selectedScope by remember { mutableStateOf<AiScope?>(null) }
    var translationResult by remember { mutableStateOf("") }
    var detectedLanguages by remember { mutableStateOf("") }
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

    // Handle language selection and move to scope selection
    fun onLanguageConfirmed(language: String) {
        selectedLanguage = language
        dialogPhase = TranslationDialogPhase.ScopeSelection
    }

    // Handle scope selection
    fun onScopeSelected(aiScope: AiScope) {
        selectedScope = aiScope
        dialogPhase = TranslationDialogPhase.Processing
        errorMessage = null
        processingStartTime = System.currentTimeMillis()

        processingJob = scope.launch {
            when (aiScope) {
                is AiScope.CurrentPage -> {
                    // Single page translation
                    val content = AiTranslationService.TranslationContent(
                        currentPageStrokes,
                        currentPageTextAnnotations
                    )
                    AiTranslationService.translateContent(
                        content = content,
                        targetLanguage = selectedLanguage
                    ) { state ->
                        when (state) {
                            is AiTranslationService.TranslationState.Success -> {
                                scope.launch {
                                    // Ensure minimum display time for fun messages
                                    val elapsed = System.currentTimeMillis() - processingStartTime
                                    if (elapsed < minProcessingDisplayMs) {
                                        kotlinx.coroutines.delay(minProcessingDisplayMs - elapsed)
                                    }
                                    translationResult = state.translatedText
                                    detectedLanguages = state.detectedLanguages
                                    dialogPhase = TranslationDialogPhase.Result
                                }
                            }
                            is AiTranslationService.TranslationState.Error -> {
                                errorMessage = state.message
                                dialogPhase = TranslationDialogPhase.Error
                            }
                            is AiTranslationService.TranslationState.NoContent -> {
                                errorMessage = state.message
                                dialogPhase = TranslationDialogPhase.Error
                            }
                            else -> { /* Processing states - handled by UI */ }
                        }
                    }
                }
                is AiScope.AllPages -> {
                    // Multi-page translation: every page of the day, read whole (Oct 2026). Pages not
                    // opened this session have no strokes in memory, so they were left out silently.
                    val pages = allPagesData.map { pageData ->
                        val page = MemoPageContent.load(context, pageData.pageId)
                        AiTranslationService.PageTranslationData(
                            pageId = pageData.pageId,
                            pageIndex = pageData.pageIndex,
                            strokes = page.strokes,
                            textAnnotations = page.textAnnotations
                        )
                    }
                    AiTranslationService.translateMultiplePages(
                        pages = pages,
                        targetLanguage = selectedLanguage
                    ) { state ->
                        when (state) {
                            is AiTranslationService.MultiPageTranslationState.ProcessingPage -> {
                                processingProgress = state.current to state.total
                            }
                            is AiTranslationService.MultiPageTranslationState.Success -> {
                                scope.launch {
                                    // Ensure minimum display time for fun messages
                                    val elapsed = System.currentTimeMillis() - processingStartTime
                                    if (elapsed < minProcessingDisplayMs) {
                                        kotlinx.coroutines.delay(minProcessingDisplayMs - elapsed)
                                    }
                                    translationResult = state.fullTranslation
                                    detectedLanguages = state.detectedLanguages
                                    dialogPhase = TranslationDialogPhase.Result
                                }
                            }
                            is AiTranslationService.MultiPageTranslationState.Error -> {
                                errorMessage = state.message
                                dialogPhase = TranslationDialogPhase.Error
                            }
                            is AiTranslationService.MultiPageTranslationState.NoContent -> {
                                errorMessage = state.message
                                dialogPhase = TranslationDialogPhase.Error
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
                onAction(TranslationAction.CopyToClipboard(action.text, detectedLanguages))
            }
            is AiResultAction.PlaceOnNewPage -> {
                onAction(TranslationAction.PlaceOnNewPage(action.text, detectedLanguages))
            }
        }
        onDismiss()
    }

    // Render based on current phase
    when (dialogPhase) {
        TranslationDialogPhase.LanguageSelection -> {
            TranslationLanguageSelectionDialog(
                selectedLanguage = selectedLanguage,
                onLanguageSelected = { onLanguageConfirmed(it) },
                onDismiss = onDismiss
            )
        }

        TranslationDialogPhase.ScopeSelection -> {
            AiScopeSelectionDialog(
                featureName = "Translate",
                totalPages = totalPages,
                onScopeSelected = { onScopeSelected(it) },
                onDismiss = onDismiss
            )
        }

        TranslationDialogPhase.Processing -> {
            TranslationProcessingDialog(
                targetLanguage = selectedLanguage,
                scope = selectedScope,
                progress = processingProgress,
                onCancel = { onCancel() }
            )
        }

        TranslationDialogPhase.Error -> {
            TranslationErrorDialog(
                message = errorMessage ?: "An error occurred",
                onRetry = { onRetry() },
                onDismiss = onDismiss
            )
        }

        TranslationDialogPhase.Result -> {
            // Strip markdown for display, keep original for placing on page
            val displayText = remember(translationResult) {
                MarkdownStripper.strip(translationResult)
            }
            AiResultDialog(
                title = "Translation",
                markdownText = translationResult,
                displayText = displayText,
                onAction = { onResultAction(it) },
                onDismiss = onDismiss
            )
        }
    }
}

/**
 * Dialog phases for translation state machine
 */
private enum class TranslationDialogPhase {
    LanguageSelection,
    ScopeSelection,
    Processing,
    Error,
    Result
}

/**
 * Language selection dialog
 */
@Composable
private fun TranslationLanguageSelectionDialog(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentSelection by remember { mutableStateOf(selectedLanguage) }
    var showDropdown by remember { mutableStateOf(false) }

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
                    .width(EInkTheme.PopupOverlay.maxWidth) // Tablet-appropriate width, within the screen (Oct 2026: was 480 dp)
                    .background(
                        EInkTheme.ModernDialog.background,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .border(
                        EInkTheme.ModernDialog.borderWidth,
                        EInkTheme.ModernDialog.borderColor,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .padding(EInkTheme.ModernDialog.padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(EInkTheme.ModernDialog.contentSpacing)
            ) {
                // Title
                Text(
                    text = "Translate",
                    fontWeight = FontWeight.Bold,
                    fontSize = EInkTheme.ModernDialog.titleFontSize,
                    color = EInkTheme.ModernDialog.titleColor,
                    textAlign = TextAlign.Center
                )

                // Subtitle
                Text(
                    text = "Select target language",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Language dropdown
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Dropdown button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                            .border(
                                EInkTheme.Global.borderWidth,
                                EInkTheme.Colors.gray,
                                RoundedCornerShape(EInkTheme.Global.cornerRadius)
                            )
                            .noRippleClickable { showDropdown = !showDropdown }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentSelection,
                                fontSize = EInkTheme.Typography.bodyMedium,
                                color = EInkTheme.Colors.primary
                            )
                            Icon(
                                imageVector = if (showDropdown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand",
                                tint = EInkTheme.Colors.gray
                            )
                        }
                    }

                    // Dropdown list
                    if (showDropdown) {
                        Spacer(Modifier.height(4.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .background(EInkTheme.PopupMenu.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                .border(
                                    EInkTheme.PopupMenu.borderWidth,
                                    EInkTheme.PopupMenu.borderColor,
                                    RoundedCornerShape(EInkTheme.Global.cornerRadius)
                                )
                                .verticalScroll(rememberScrollState())
                        ) {
                            SUPPORTED_LANGUAGES.forEach { language ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(EInkTheme.MenuItem.height)
                                        .background(
                                            if (language == currentSelection) EInkTheme.Selection.background
                                            else EInkTheme.Colors.white
                                        )
                                        .noRippleClickable {
                                            currentSelection = language
                                            showDropdown = false
                                        }
                                        .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = language,
                                        fontSize = EInkTheme.MenuItem.fontSize,
                                        color = if (language == currentSelection)
                                            EInkTheme.Selection.foreground
                                        else
                                            EInkTheme.MenuItem.textColor
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel button
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
                        Text(
                            "Cancel",
                            color = EInkTheme.Button.textColor,
                            fontSize = EInkTheme.Typography.bodyMedium
                        )
                    }

                    // Continue button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Button.height)
                            .background(EInkTheme.Button.primaryBackground, RoundedCornerShape(6.dp))
                            .noRippleClickable { onLanguageSelected(currentSelection) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = EInkTheme.Button.primaryTextColor
                            )
                            Text(
                                "Continue",
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

/**
 * Processing dialog showing progress - tablet-optimized layout
 */
@Composable
private fun TranslationProcessingDialog(
    targetLanguage: String,
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
                        text = "Translating Your Notes",
                        fontSize = EInkTheme.PopupOverlay.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.PopupOverlay.titleColor,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(12.dp))

                    // Scope and language indicator with more context
                    val scopeText = when (scope) {
                        is AiScope.CurrentPage -> "Translating the current memo page to $targetLanguage"
                        is AiScope.AllPages -> "Translating all memo pages to $targetLanguage"
                        null -> "Preparing translation..."
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

                    // Fun message - translation-specific quotes, rotates every 3 seconds
                    RotatingFunMessage(
                        messageProvider = { AiFeedbackMessages.getRandomTranslationMessage() },
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
private fun TranslationErrorDialog(
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

