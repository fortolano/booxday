package com.boox.day.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import com.boox.day.ui.noRippleClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.theme.EInkTheme

/**
 * Actions that can be taken with voice-transcribed text.
 * Similar to RecognitionAction but without stroke-related options.
 */
sealed class VoiceTextAction {
    data class CopyToClipboard(val text: String) : VoiceTextAction()
    data class PlaceOnCurrentPage(val text: String) : VoiceTextAction()
    data class PlaceOnNewPage(val text: String) : VoiceTextAction()
}

/**
 * Dialog for showing voice transcription results with action options.
 * Allows user to edit the text and choose where to place it.
 * Optimized for e-ink display with black/white theme.
 */
@Composable
fun VoiceTextResultDialog(
    transcribedText: String,
    onAction: (VoiceTextAction) -> Unit,
    onDismiss: () -> Unit
) {
    var editedText by remember { mutableStateOf(transcribedText) }

    // E-Ink: Use Popup instead of Dialog to avoid system scrim/dimming
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
                    .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                    .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.ModernDialog.padding)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Voice Transcription",
                        fontSize = EInkTheme.ModernDialog.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.ModernDialog.titleColor
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Close", modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Editable text field
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 150.dp),
                    label = { Text("Transcribed Text") },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = EInkTheme.Colors.primary,
                        unfocusedBorderColor = EInkTheme.Colors.primary,
                        cursorColor = EInkTheme.Colors.primary,
                        focusedLabelColor = EInkTheme.Colors.primary
                    )
                )

                Spacer(Modifier.height(16.dp))

                // Action buttons
                Text("What would you like to do?", fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.gray)
                Spacer(Modifier.height(8.dp))

                // Copy to clipboard
                VoiceActionRow(
                    icon = Icons.Default.ContentCopy,
                    text = "Copy to clipboard",
                    onClick = {
                        onAction(VoiceTextAction.CopyToClipboard(editedText))
                    }
                )

                // Place on current page
                VoiceActionRow(
                    icon = Icons.Default.NoteAdd,
                    text = "Place on current page",
                    onClick = {
                        onAction(VoiceTextAction.PlaceOnCurrentPage(editedText))
                    }
                )

                // Place on new page
                VoiceActionRow(
                    icon = Icons.Default.PostAdd,
                    text = "Place on new page",
                    onClick = {
                        onAction(VoiceTextAction.PlaceOnNewPage(editedText))
                    }
                )
            }
        }
    }
}

/**
 * Action row for voice text results.
 * Shows icon + text in a clickable card.
 */
@Composable
private fun VoiceActionRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .noRippleClickable(onClick = onClick),
        backgroundColor = EInkTheme.Colors.white,
        elevation = 0.dp,
        border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary),
        shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = EInkTheme.Colors.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(text, fontSize = EInkTheme.Typography.bodyMedium)
        }
    }
}
