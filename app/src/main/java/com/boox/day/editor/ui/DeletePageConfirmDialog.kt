package com.boox.day.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.theme.EInkTheme

/**
 * Confirmation dialog for deleting the current page.
 * Triggered by two-finger tap gesture.
 *
 * Behavior:
 * - Multiple pages: Deletes current page and switches to adjacent page
 * - Single page: Clears page content (cannot delete the only page)
 *
 * E-Ink optimized: High contrast, no colors beyond grayscale.
 */
@Composable
fun DeletePageConfirmDialog(
    pageNumber: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val isSinglePage = totalPages <= 1
    val title = if (isSinglePage) "Clear Page?" else "Delete Page?"
    val message = if (isSinglePage) {
        "This will clear all content on the current page.\n\nThis action cannot be undone."
    } else {
        "Delete page $pageNumber of $totalPages?\n\nThis will permanently remove all content on this page. This action cannot be undone."
    }
    val confirmText = if (isSinglePage) "Clear" else "Delete"

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.70f)
                    .background(EInkTheme.PopupPanel.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.PopupPanel.borderWidth, EInkTheme.PopupPanel.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.PopupPanel.padding)
            ) {
                // Title with warning icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = EInkTheme.PopupPanel.titleColor,
                        modifier = Modifier.size(EInkTheme.Global.iconSize)
                    )
                    Text(
                        text = title,
                        fontSize = EInkTheme.PopupPanel.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.PopupPanel.titleColor
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Message
                Text(
                    text = message,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Button.height),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = EInkTheme.Button.textColor
                        ),
                        border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor),
                        shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                    ) {
                        Text(text = "Cancel", fontSize = EInkTheme.Typography.button)
                    }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Button.height),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = EInkTheme.Button.primaryBackground,
                            contentColor = EInkTheme.Button.primaryTextColor
                        ),
                        shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                    ) {
                        Text(
                            text = confirmText,
                            fontSize = EInkTheme.Typography.button,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
