package com.boox.day.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.noRippleClickable

/**
 * Dialog for typing a task.
 * Uses Popup for E-Ink optimization (avoids system scrim/dimming).
 * Simplified for e-ink displays - uses black, white, and gray only.
 * Single-line text input that triggers keyboard.
 */
@Composable
fun TypeTaskDialog(
    rowIndex: Int,
    initialText: String? = null,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText ?: "") }
    val focusRequester = remember { FocusRequester() }

    // Auto-focus the text field when dialog opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // E-Ink: Use Popup instead of Dialog to avoid system scrim/dimming
    // Position (Oct 2026): near the top of the SCREEN, 12% down, so the keyboard never covers Cancel and
    // Save. It was centred in the Tasks area and raised 330 px, which left the buttons under the
    // keyboard on the Go 6. Placed against the window, not the composable it is declared in.
    val topOffsetPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        (com.boox.day.ui.theme.LayoutProfiles.screenHeightDp * 0.12f).dp.roundToPx()
    }
    val positionProvider = remember(topOffsetPx) {
        object : androidx.compose.ui.window.PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: androidx.compose.ui.unit.IntRect,
                windowSize: androidx.compose.ui.unit.IntSize,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                popupContentSize: androidx.compose.ui.unit.IntSize
            ): IntOffset = IntOffset((windowSize.width - popupContentSize.width) / 2, topOffsetPx)
        }
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .width(EInkTheme.PopupOverlay.wideWidth)
                .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
        ) {
            Column(
                modifier = Modifier.padding(EInkTheme.PopupOverlay.padding)
            ) {
                // Title
                Text(
                    text = "Task ${rowIndex + 1}",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = EInkTheme.PopupOverlay.titleColor
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Text input field
                BasicTextField(
                    value = text,
                    onValueChange = { newText ->
                        // Single line - remove newlines, limit to typedTextMaxLength characters
                        val cleaned = newText.replace("\n", "")
                        if (cleaned.length <= EInkTheme.TasksTab.typedTextMaxLength) {
                            text = cleaned
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(EInkTheme.Divider.thickness, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                        .background(EInkTheme.Colors.white, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                        .padding(12.dp)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        fontSize = EInkTheme.PopupOverlay.titleFontSize,  // Larger font for e-ink readability
                        color = EInkTheme.Colors.primary
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(EInkTheme.Colors.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (text.isNotBlank()) {
                                onSave(text.trim())
                            }
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (text.isEmpty()) {
                                Text(
                                    text = "Enter task...",
                                    style = TextStyle(
                                        fontSize = EInkTheme.PopupOverlay.titleFontSize,
                                        color = EInkTheme.Colors.gray
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                // Character counter
                Text(
                    text = "${text.length}/${EInkTheme.TasksTab.typedTextMaxLength}",
                    fontSize = EInkTheme.Typography.labelSmall,
                    color = if (text.length >= EInkTheme.TasksTab.typedTextMaxLength) Color.Red else EInkTheme.Colors.gray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    textAlign = TextAlign.End
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    // Cancel button
                    Box(
                        modifier = Modifier
                            .height(EInkTheme.Button.height)
                            .background(EInkTheme.Button.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                            .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.Button.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                            .noRippleClickable { onDismiss() }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", color = EInkTheme.Button.textColor, fontSize = EInkTheme.Typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Save button
                    val canSave = text.isNotBlank()
                    Box(
                        modifier = Modifier
                            .height(EInkTheme.Button.height)
                            .background(
                                if (canSave) EInkTheme.Button.primaryBackground else EInkTheme.Colors.lightGray,
                                RoundedCornerShape(EInkTheme.Global.cornerRadius)
                            )
                            .then(
                                if (canSave) Modifier.noRippleClickable { onSave(text.trim()) }
                                else Modifier
                            )
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Save",
                            color = if (canSave) EInkTheme.Button.primaryTextColor else EInkTheme.Colors.darkGray,
                            fontSize = EInkTheme.Typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
