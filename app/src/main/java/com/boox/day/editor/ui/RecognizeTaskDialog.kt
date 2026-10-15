package com.boox.day.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.components.EinkLoadingDotsInline
import com.boox.day.ui.components.EinkPrimaryButton
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.components.RotatingFunMessage
import com.boox.day.ui.messages.AiFeedbackMessages
import com.boox.day.ui.noRippleClickable

/**
 * Scope for the Convert Handwriting operation.
 */
enum class ConvertScope {
    CURRENT_PAGE,
    ALL_PAGES,
    THIS_WEEK,
    THIS_MONTH
}

/**
 * Convert Handwriting Dialog - Choose scope for handwriting-to-text conversion.
 *
 * Options:
 * - Current Page: Convert handwriting on the currently visible task page
 * - All Pages: Convert handwriting on all task pages for the day
 * - This Week: Convert handwriting on all task pages for the current week
 * - This Month: Convert handwriting on all task pages for the current month
 *
 * Promotes recognizedText to typedText and deletes ink strokes.
 * E-ink optimized: black/white, large touch targets, no animations.
 */
@Composable
fun ConvertHandwritingDialog(
    currentPage: Int,
    totalPages: Int,
    processingState: RecognitionProcessingState,
    onConvert: (ConvertScope) -> Unit,
    onDismiss: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = {
            // Only allow dismiss if not processing
            if (processingState !is RecognitionProcessingState.Processing) {
                onDismiss()
            }
        },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = processingState !is RecognitionProcessingState.Processing,
            dismissOnClickOutside = processingState !is RecognitionProcessingState.Processing
        )
    ) {
        Box(
            modifier = Modifier
                .width(EInkTheme.PopupOverlay.wideWidth)
                .background(EInkTheme.ModernDialog.background, RoundedCornerShape(10.dp))
                .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(10.dp))
        ) {
            Column(
                modifier = Modifier.padding(EInkTheme.ModernDialog.padding)
            ) {
                // Header
                Text(
                    text = "Convert Handwriting",
                    fontSize = EInkTheme.ModernDialog.titleFontSize,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.ModernDialog.titleColor,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Replace handwriting with recognized text",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.gray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                when (processingState) {
                    is RecognitionProcessingState.Idle -> {
                        // Option: Current Page
                        ConvertScopeOption(
                            icon = Icons.Default.Description,
                            title = "Current Page",
                            subtitle = "Page $currentPage only",
                            onClick = { onConvert(ConvertScope.CURRENT_PAGE) }
                        )

                        Spacer(Modifier.height(12.dp))

                        // Option: All Pages (only if multiple pages)
                        if (totalPages > 1) {
                            ConvertScopeOption(
                                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                                title = "All Pages",
                                subtitle = "All $totalPages task pages for today",
                                onClick = { onConvert(ConvertScope.ALL_PAGES) }
                            )

                            Spacer(Modifier.height(12.dp))
                        }

                        // Option: This Week
                        ConvertScopeOption(
                            icon = Icons.Default.DateRange,
                            title = "This Week",
                            subtitle = "Monday to Sunday",
                            onClick = { onConvert(ConvertScope.THIS_WEEK) }
                        )

                        Spacer(Modifier.height(12.dp))

                        // Option: This Month
                        ConvertScopeOption(
                            icon = Icons.Default.CalendarMonth,
                            title = "This Month",
                            subtitle = "All tasks this month",
                            onClick = { onConvert(ConvertScope.THIS_MONTH) }
                        )

                        Spacer(Modifier.height(20.dp))

                        // Cancel button
                        EinkSecondaryButton(
                            text = "Cancel",
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    is RecognitionProcessingState.Processing -> {
                        // Processing state
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            EinkLoadingDotsInline(color = EInkTheme.Colors.primary)

                            // Fun/Entertaining Message (Primary) - uses EInkTheme.AiProcessingFeedback defaults
                            RotatingFunMessage(
                                messageProvider = { AiFeedbackMessages.getRandomOcrMessage() }
                            )

                            // Technical Stage (Secondary)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = processingState.message,
                                    fontSize = EInkTheme.Typography.bodyLarge,
                                    color = EInkTheme.Colors.darkGray,
                                    textAlign = TextAlign.Center
                                )

                                if (processingState.progress != null) {
                                    Text(
                                        text = "Page ${processingState.progress.first} of ${processingState.progress.second}",
                                        fontSize = EInkTheme.Typography.bodyLarge,
                                        color = EInkTheme.Colors.darkGray
                                    )
                                }
                            }
                        }
                    }

                    is RecognitionProcessingState.Success -> {
                        // Success state
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Conversion Complete",
                                fontSize = EInkTheme.Typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.primary
                            )

                            Text(
                                text = processingState.message,
                                fontSize = EInkTheme.Typography.bodyLarge,
                                color = EInkTheme.Colors.darkGray,
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(12.dp))

                            EinkPrimaryButton(
                                text = "Done",
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth(),
                                height = 52.dp
                            )
                        }
                    }

                    is RecognitionProcessingState.Error -> {
                        // Error state
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Conversion Failed",
                                fontSize = EInkTheme.Typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.darkGray
                            )

                            Text(
                                text = processingState.message,
                                fontSize = EInkTheme.Typography.bodyLarge,
                                color = EInkTheme.Colors.gray,
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(12.dp))

                            EinkSecondaryButton(
                                text = "Close",
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth(),
                                height = 52.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConvertScopeOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.gray, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .noRippleClickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = EInkTheme.Colors.primary
            )

            Column {
                Text(
                    text = title,
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )
                Text(
                    text = subtitle,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )
            }
        }
    }
}

/**
 * Processing state for the convert handwriting dialog.
 */
sealed class RecognitionProcessingState {
    object Idle : RecognitionProcessingState()

    data class Processing(
        val message: String,
        val progress: Pair<Int, Int>? = null // (current, total)
    ) : RecognitionProcessingState()

    data class Success(
        val message: String,
        val recognizedCount: Int
    ) : RecognitionProcessingState()

    data class Error(
        val message: String
    ) : RecognitionProcessingState()
}
