package com.boox.day.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.utils.TextSplitUtils

/**
 * Actions available for AI results (Summarize/Translate).
 */
sealed class AiResultAction {
    /** Copy the result text to clipboard */
    data class CopyToClipboard(val text: String) : AiResultAction()

    /** Place result on new memo page(s) - auto-splits if needed */
    data class PlaceOnNewPage(val text: String) : AiResultAction()
}

/**
 * Result dialog for AI Summarize and Translate features.
 *
 * Shows:
 * - Read-only text field with the cleaned result (markdown stripped)
 * - Two action buttons: Copy to clipboard, Place on New Memo Page
 * - Quota gating: "Place on New Page" disabled if user at page limit
 *
 * The dialog receives two versions of the text:
 * - markdownText: Original AI output with markdown formatting (for placing on page)
 * - displayText: Cleaned version without markdown symbols (for display and clipboard)
 *
 * Users can edit the text later on the canvas if they place it on a page.
 *
 * Follows HandwritingRecognitionDialog pattern for consistency.
 *
 * @param title Dialog title (e.g., "Summary", "Translation")
 * @param markdownText Original AI text with markdown formatting (used for placing on page)
 * @param displayText Cleaned text without markdown symbols (used for display and clipboard)
 * @param onAction Callback when user selects an action
 * @param onDismiss Callback when dialog is dismissed
 */
@Composable
fun AiResultDialog(
    title: String,
    markdownText: String,
    displayText: String,
    onAction: (AiResultAction) -> Unit,
    onDismiss: () -> Unit
) {
    // Calculate how many pages are needed for the markdown text (what gets placed on page)
    val requiredPages = remember(markdownText) {
        TextSplitUtils.calculateRequiredPages(markdownText)
    }

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
                    .background(
                        EInkTheme.PopupOverlay.background,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .border(
                        EInkTheme.PopupOverlay.borderWidth,
                        EInkTheme.PopupOverlay.borderColor,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .padding(28.dp) // Generous padding for tablet
            ) {
                // Header with title and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            title,
                            fontSize = EInkTheme.PopupOverlay.titleFontSize,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.PopupOverlay.titleColor
                        )
                        Text(
                            "AI-generated result ready for use",
                            fontSize = EInkTheme.Typography.bodySmall,
                            color = EInkTheme.Colors.gray
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Close,
                            "Close",
                            modifier = Modifier.size(24.dp),
                            tint = EInkTheme.Colors.primary
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Read-only text field showing the cleaned result
                OutlinedTextField(
                    value = displayText,
                    onValueChange = { /* Read-only */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 320.dp),
                    readOnly = true,
                    label = { Text("${title} result") },
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

                // Page count info if result spans multiple pages
                if (requiredPages > 1) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "This content will be split across $requiredPages memo pages",
                        fontSize = EInkTheme.Typography.labelSmall,
                        color = EInkTheme.Colors.darkGray,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(28.dp))

                // Action section
                Text(
                    "Choose an action",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Select what to do with your ${title.lowercase()}",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray
                )
                Spacer(Modifier.height(16.dp))

                // Copy to clipboard - uses clean displayText (no markdown symbols)
                AiResultActionRow(
                    icon = Icons.Default.ContentCopy,
                    text = "Copy to clipboard",
                    description = "Copy the clean text to paste elsewhere",
                    enabled = true,
                    onClick = { onAction(AiResultAction.CopyToClipboard(displayText)) }
                )

                Spacer(Modifier.height(12.dp))

                // Place on New Memo Page - uses markdownText for formatting support
                AiResultActionRow(
                    icon = Icons.Default.NoteAdd,
                    text = "Place on new memo page",
                    description = "Create a new page with formatted content",
                    enabled = true,
                    onClick = { onAction(AiResultAction.PlaceOnNewPage(markdownText)) }
                )
            }
        }
    }
}

/**
 * Action row for AI result dialog - tablet-optimized touch target.
 * Shows icon, text, and description with enabled/disabled state.
 *
 * @param icon The icon to display
 * @param text The action label
 * @param description Explains what this action does
 * @param enabled Whether the action is available
 * @param onClick Callback when tapped (only fires if enabled)
 */
@Composable
private fun AiResultActionRow(
    icon: ImageVector,
    text: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val contentColor = if (enabled) EInkTheme.Colors.primary else EInkTheme.Colors.gray
    val borderColor = if (enabled) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(enabled = enabled, onClick = onClick),
        backgroundColor = EInkTheme.Colors.background,
        elevation = 0.dp,
        border = BorderStroke(1.dp, borderColor),
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
                tint = contentColor
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text,
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = contentColor
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
