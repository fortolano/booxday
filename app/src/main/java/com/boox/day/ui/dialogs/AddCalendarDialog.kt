package com.boox.day.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Spacer
import com.boox.day.ui.noRippleClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.theme.EInkTheme

/**
 * Dialog for adding a new local calendar.
 * Simple form with name input and optional color selection.
 *
 * E-Ink optimized: High contrast, no colors beyond grayscale.
 */
@Composable
fun AddCalendarDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, colorHex: String?) -> Unit,
    existingNames: Set<String> = emptySet()
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val focusRequester = remember { FocusRequester() }

    // Auto-focus the text field
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Validate name
    val isNameValid = name.isNotBlank() &&
            !existingNames.any { it.equals(name.trim(), ignoreCase = true) }

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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(EInkTheme.Global.iconSize),
                        tint = EInkTheme.ModernDialog.titleColor
                    )
                    Text(
                        text = "Add Calendar",
                        fontSize = EInkTheme.ModernDialog.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.ModernDialog.titleColor
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Name input
                Text(
                    text = "Name",
                    fontSize = EInkTheme.Typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            text = "e.g., Work, Family, Church",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.gray
                        )
                    },
                    singleLine = true,
                    isError = errorMessage != null,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = EInkTheme.TextField.textColor,
                        backgroundColor = EInkTheme.TextField.background,
                        cursorColor = EInkTheme.Colors.primary,
                        focusedBorderColor = EInkTheme.TextField.focusedBorderColor,
                        unfocusedBorderColor = EInkTheme.TextField.borderColor,
                        errorBorderColor = EInkTheme.Colors.primary,
                        placeholderColor = EInkTheme.TextField.hintColor
                    )
                )

                // Error message
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMessage!!,
                        fontSize = EInkTheme.Typography.labelSmall,
                        color = EInkTheme.Colors.primary
                    )
                } else if (name.isNotBlank() && existingNames.any { it.equals(name.trim(), ignoreCase = true) }) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "A calendar with this name already exists",
                        fontSize = EInkTheme.Typography.labelSmall,
                        color = EInkTheme.Colors.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Color selection (optional)
                Text(
                    text = "Color (optional)",
                    fontSize = EInkTheme.Typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                ColorSelector(
                    selectedColor = selectedColor,
                    onColorSelected = { selectedColor = it }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons - 40% / 20% / 40% layout
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(0.4f)
                            .height(EInkTheme.Button.height),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = EInkTheme.ModernDialog.CancelLink.textColor
                        ),
                        border = BorderStroke(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor),
                        shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = EInkTheme.Typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.weight(0.2f))

                    Button(
                        onClick = {
                            val trimmedName = name.trim()
                            if (trimmedName.isBlank()) {
                                errorMessage = "Please enter a name"
                            } else if (existingNames.any { it.equals(trimmedName, ignoreCase = true) }) {
                                errorMessage = "A calendar with this name already exists"
                            } else {
                                onAdd(trimmedName, selectedColor)
                            }
                        },
                        modifier = Modifier
                            .weight(0.4f)
                            .height(EInkTheme.Button.height),
                        enabled = isNameValid,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = EInkTheme.ModernDialog.PrimaryButton.background,
                            contentColor = EInkTheme.ModernDialog.PrimaryButton.textColor,
                            disabledBackgroundColor = EInkTheme.Colors.lightGray,
                            disabledContentColor = EInkTheme.Colors.darkGray
                        ),
                        shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                    ) {
                        Text(
                            text = "Add",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Color selector for calendar color.
 * Shows a row of color options including "None".
 */
@Composable
private fun ColorSelector(
    selectedColor: String?,
    onColorSelected: (String?) -> Unit
) {
    // Predefined calendar colors (grayscale-friendly with good contrast)
    val colors = listOf(
        null to "None",           // No color
        "#000000" to "Black",
        "#666666" to "Gray",
        "#FF5722" to "Orange",
        "#4CAF50" to "Green",
        "#2196F3" to "Blue",
        "#9C27B0" to "Purple",
        "#F44336" to "Red"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        colors.forEach { (colorHex, label) ->
            ColorOption(
                colorHex = colorHex,
                label = label,
                isSelected = selectedColor == colorHex,
                onClick = { onColorSelected(colorHex) }
            )
        }
    }
}

@Composable
private fun ColorOption(
    colorHex: String?,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.noRippleClickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    colorHex?.let { parseColorHex(it) } ?: EInkTheme.Colors.lightGray
                )
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.darkGray,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    modifier = Modifier.size(18.dp),
                    tint = if (colorHex == "#000000" || colorHex == "#666666")
                        Color.White else EInkTheme.Colors.primary
                )
            } else if (colorHex == null) {
                // Show "X" for None option when not selected
                Text(
                    text = "—",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.darkGray
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = label,
            fontSize = EInkTheme.Typography.labelSmall,
            color = if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.darkGray
        )
    }
}

/**
 * Parse a hex color string to a Color.
 */
private fun parseColorHex(hex: String): Color {
    return try {
        val colorInt = android.graphics.Color.parseColor(hex)
        Color(colorInt)
    } catch (e: Exception) {
        Color.Gray
    }
}
