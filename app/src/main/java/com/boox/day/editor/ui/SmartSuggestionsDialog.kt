package com.boox.day.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.R
import com.boox.day.ai.TaskAiService
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.components.EinkLoadingDotsInline
import com.boox.day.ui.components.EinkPrimaryButton
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.components.RotatingFunMessage
import com.boox.day.ui.messages.AiFeedbackMessages
import com.boox.day.ui.noRippleClickable

/**
 * Smart Suggestions Dialog - AI-powered task recommendations.
 *
 * Provides context-aware task suggestions based on:
 * - Time of day (morning routines, end-of-day tasks)
 * - Day of week (weekly patterns)
 * - Existing tasks (related follow-ups)
 * - Historical patterns (frequently created tasks)
 *
 * E-ink optimized: black/white, large touch targets, no animations.
 */
@Composable
fun SmartSuggestionsDialog(
    suggestionsResult: TaskAiService.SuggestionsResult?,
    processingState: SmartSuggestionsProcessingState,
    onAddSuggestion: (TaskAiService.TaskSuggestion) -> Unit,
    onAddAll: (List<TaskAiService.TaskSuggestion>) -> Unit,
    onDismiss: () -> Unit,
    // AI Preferences reminder
    showPreferencesReminder: Boolean = false,
    onSetUpPreferences: () -> Unit = {}
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = {
            if (processingState !is SmartSuggestionsProcessingState.Processing) {
                onDismiss()
            }
        },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = processingState !is SmartSuggestionsProcessingState.Processing,
            dismissOnClickOutside = processingState !is SmartSuggestionsProcessingState.Processing
        )
    ) {
        Box(
            modifier = Modifier
                .width(EInkTheme.PopupOverlay.wideWidth)
                .heightIn(max = EInkTheme.PopupOverlay.maxHeight)  // Oct 2026: was 720 dp
                .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
        ) {
            Column(
                modifier = Modifier.padding(EInkTheme.ModernDialog.padding)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Smart Suggestions",
                        fontSize = EInkTheme.ModernDialog.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.ModernDialog.titleColor
                    )

                    if (processingState !is SmartSuggestionsProcessingState.Processing) {
                        Box(
                            modifier = Modifier
                                .size(EInkTheme.Global.touchTarget)
                                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.gray, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                .noRippleClickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = EInkTheme.Colors.primary,
                                modifier = Modifier.size(EInkTheme.Global.iconSize)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Tasks you might want to add today",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray
                )

                // AI Preferences reminder banner
                if (showPreferencesReminder) {
                    Spacer(Modifier.height(12.dp))
                    PreferencesReminderBanner(
                        onClick = {
                            onDismiss()
                            onSetUpPreferences()
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))

                when (processingState) {
                    is SmartSuggestionsProcessingState.Processing -> {
                        // Loading state - show dynamic stage message
                        val stageMessage = processingState.stageMessage
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            EinkLoadingDotsInline(color = EInkTheme.Colors.primary)

                            // Fun/Entertaining Message (Primary) - uses EInkTheme.AiProcessingFeedback defaults
                            RotatingFunMessage(
                                messageProvider = { AiFeedbackMessages.getRandomSuggestionsMessage() }
                            )

                            // Technical Stage (Secondary)
                            Text(
                                text = stageMessage,
                                fontSize = EInkTheme.Typography.bodyLarge,
                                color = EInkTheme.Colors.gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is SmartSuggestionsProcessingState.Error -> {
                        // Error state
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Could not generate suggestions",
                                fontSize = EInkTheme.Typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.darkGray
                            )
                            Text(
                                text = processingState.message,
                                fontSize = EInkTheme.Typography.bodyMedium,
                                color = EInkTheme.Colors.gray,
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(8.dp))

                            EinkSecondaryButton(
                                text = "Close",
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    is SmartSuggestionsProcessingState.Ready -> {
                        if (suggestionsResult == null || suggestionsResult.suggestions.isEmpty()) {
                            // No suggestions
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No suggestions available",
                                    fontSize = EInkTheme.Typography.bodyMedium,
                                    color = EInkTheme.Colors.gray
                                )

                                Spacer(Modifier.height(16.dp))

                                EinkSecondaryButton(
                                    text = "Close",
                                    onClick = onDismiss,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            // Show suggestions
                            SmartSuggestionsContent(
                                modifier = Modifier.weight(1f, fill = false),
                                suggestions = suggestionsResult.suggestions,
                                onAddSuggestion = onAddSuggestion,
                                onAddAll = onAddAll,
                                onDone = onDismiss
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartSuggestionsContent(
    modifier: Modifier = Modifier,
    suggestions: List<TaskAiService.TaskSuggestion>,
    onAddSuggestion: (TaskAiService.TaskSuggestion) -> Unit,
    onAddAll: (List<TaskAiService.TaskSuggestion>) -> Unit,
    onDone: () -> Unit
) {
    // Track which suggestions have been added
    var addedSuggestions by remember { mutableStateOf(setOf<String>()) }

    // Calculate remaining suggestions
    val remainingSuggestions = suggestions.filter { it.text !in addedSuggestions }

    // Scroll state
    val scrollState = rememberLazyListState()
    val showScrollIndicator by remember {
        derivedStateOf {
            val lastVisibleItem = scrollState.layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = scrollState.layoutInfo.totalItemsCount
            lastVisibleItem != null && lastVisibleItem.index < totalItems - 1
        }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Suggestions list - fixed height
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)  // Oct 2026: keeps its height where it fits, shrinks so the buttons stay on a small screen
                .height(400.dp) // Fixed height for consistency
        ) {
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (showScrollIndicator) 24.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(suggestions) { suggestion ->
                    SuggestionItem(
                        suggestion = suggestion,
                        isAdded = addedSuggestions.contains(suggestion.text),
                        onAdd = {
                            addedSuggestions = addedSuggestions.plus(suggestion.text)
                            onAddSuggestion(suggestion)
                        }
                    )
                }
            }

            // Scroll indicator - E-Ink optimized
            if (showScrollIndicator) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(EInkTheme.Colors.background)
                        .drawBehind {
                            drawLine(
                                color = EInkTheme.Colors.lightGray,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.dp.toPx()
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = EInkTheme.Colors.darkGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "more",
                            fontSize = EInkTheme.Typography.labelSmall,
                            color = EInkTheme.Colors.darkGray
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Add All button (only if there are remaining suggestions)
            if (remainingSuggestions.isNotEmpty()) {
                EinkSecondaryButton(
                    text = "Add All (${remainingSuggestions.size})",
                    onClick = {
                        remainingSuggestions.forEach { suggestion ->
                            addedSuggestions = addedSuggestions.plus(suggestion.text)
                        }
                        onAddAll(remainingSuggestions)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Done button
            EinkPrimaryButton(
                text = "Done",
                onClick = onDone,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SuggestionItem(
    suggestion: TaskAiService.TaskSuggestion,
    isAdded: Boolean,
    onAdd: () -> Unit
) {
    val category = suggestion.categoryEnum()
    val confidencePercent = (suggestion.confidence * 100).toInt()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isAdded) EInkTheme.Colors.lightGray else EInkTheme.Colors.offWhite,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .border(
                EInkTheme.Global.borderWidth,
                if (isAdded) EInkTheme.Colors.gray else EInkTheme.Colors.darkGray,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Task details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Category badge + task text row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Category emoji badge
                    Box(
                        modifier = Modifier
                            .background(
                                if (isAdded) EInkTheme.Colors.gray.copy(alpha = 0.2f)
                                else EInkTheme.Colors.primary.copy(alpha = 0.1f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = category.emoji,
                            fontSize = 12.sp
                        )
                    }

                    // Follow-up indicator
                    if (suggestion.isFollowUp) {
                        Text(
                            text = "↩",
                            fontSize = 12.sp,
                            color = EInkTheme.Colors.gray
                        )
                    }

                    // Task text
                    Text(
                        text = suggestion.text,
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isAdded) EInkTheme.Colors.gray else EInkTheme.Colors.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Reason + confidence row
                if (suggestion.reason.isNotBlank() || confidencePercent > 0) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Reason text
                        if (suggestion.reason.isNotBlank()) {
                            Text(
                                text = suggestion.reason,
                                fontSize = EInkTheme.Typography.labelSmall,
                                color = EInkTheme.Colors.gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Confidence indicator (dot-based for e-ink)
                        if (confidencePercent >= 70) {
                            val filledCount = when {
                                confidencePercent >= 90 -> 3
                                confidencePercent >= 80 -> 2
                                else -> 1
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                repeat(3) { index ->
                                    Icon(
                                        imageVector = if (index < filledCount) Icons.Default.FiberManualRecord else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = EInkTheme.Colors.gray,
                                        modifier = Modifier.size(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Add button
            if (isAdded) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Added",
                    modifier = Modifier.size(24.dp),
                    tint = EInkTheme.Colors.gray
                )
            } else {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                            .noRippleClickable { onAdd() },
                                        contentAlignment = Alignment.Center
                                    ) {                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add task",
                        modifier = Modifier.size(20.dp),
                        tint = EInkTheme.Colors.white
                    )
                }
            }
        }
    }
}

/**
 * Processing state for the smart suggestions dialog.
 * Includes stage message for rich progress feedback.
 */
sealed class SmartSuggestionsProcessingState {
    /**
     * Processing with detailed stage message for user feedback.
     * @param stageMessage Human-readable message about current stage
     */
    data class Processing(val stageMessage: String = "Analyzing your task patterns...") : SmartSuggestionsProcessingState()
    data object Ready : SmartSuggestionsProcessingState()
    data class Error(val message: String) : SmartSuggestionsProcessingState()
}

/**
 * Banner encouraging users to set up AI Preferences for better suggestions.
 * Shown at the top of SmartSuggestionsDialog when preferences aren't configured.
 */
@Composable
private fun PreferencesReminderBanner(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                EInkTheme.Colors.offWhite,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .border(
                EInkTheme.Global.borderWidth,
                EInkTheme.Colors.gray,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .noRippleClickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ai_sparkles),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = EInkTheme.Colors.primary
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Get personalized suggestions",
                    fontSize = EInkTheme.Typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )
                Text(
                    text = "Set up AI Preferences →",
                    fontSize = EInkTheme.Typography.labelSmall,
                    color = EInkTheme.Colors.darkGray
                )
            }

            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Set up",
                modifier = Modifier.size(20.dp),
                tint = EInkTheme.Colors.gray
            )
        }
    }
}
