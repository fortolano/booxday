package com.boox.day.ui.dialogs

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * Scope selection for Summarize and Translate features.
 *
 * Presents two options:
 * - "Current Page" - Process only the active memo page
 * - "All Pages" - Process all memo pages for the current day
 *
 * Uses Popup (not Dialog) per E-Ink rules to avoid gray overlay.
 */
sealed class AiScope {
    object CurrentPage : AiScope()
    object AllPages : AiScope()
}

/**
 * Dialog for selecting AI processing scope.
 *
 * @param featureName The feature name to display (e.g., "Summarize", "Translate")
 * @param totalPages Total number of pages for the current day
 * @param onScopeSelected Callback when user selects a scope
 * @param onDismiss Callback when dialog is dismissed
 */
@Composable
fun AiScopeSelectionDialog(
    featureName: String,
    totalPages: Int,
    onScopeSelected: (AiScope) -> Unit,
    onDismiss: () -> Unit
) {
    // Determine the verb form for better UX text
    val actionVerb = featureName.lowercase()

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
                    .padding(28.dp), // Generous tablet padding
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = featureName,
                    fontWeight = FontWeight.Bold,
                    fontSize = EInkTheme.ModernDialog.titleFontSize,
                    color = EInkTheme.ModernDialog.titleColor,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Explanatory subtitle
                Text(
                    text = "Choose which content to $actionVerb",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Current Page option
                ScopeOptionButton(
                    icon = Icons.Default.Article,
                    title = "Current Page",
                    description = "Only $actionVerb the notes on this page",
                    onClick = { onScopeSelected(AiScope.CurrentPage) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // All Pages option (show page count)
                ScopeOptionButton(
                    icon = Icons.Default.LibraryBooks,
                    title = "All Pages",
                    description = if (totalPages == 1)
                        "Combine all content (1 page)"
                    else
                        "Combine all content ($totalPages pages)",
                    onClick = { onScopeSelected(AiScope.AllPages) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Cancel link with better touch target
                Text(
                    text = "Cancel",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.gray,
                    modifier = Modifier
                        .noRippleClickable { onDismiss() }
                        .padding(vertical = 12.dp, horizontal = 24.dp)
                )
            }
        }
    }
}

/**
 * Scope option button with icon and description - tablet-optimized touch target.
 */
@Composable
private fun ScopeOptionButton(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                EInkTheme.Button.borderWidth,
                EInkTheme.Button.borderColor,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .noRippleClickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 18.dp) // Larger touch target for tablet
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp), // Larger icon for tablet
                tint = EInkTheme.Colors.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = title,
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )
            }
        }
    }
}
