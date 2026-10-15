package com.boox.day.ui.dialogs

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.db.TextAnnotation
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * Dialog for styling text boxes.
 * Allows changing font size, family, weight, style, and alignment.
 * E-ink optimized with black/white theme.
 */
@Composable
fun TextStyleDialog(
    annotation: TextAnnotation,
    onStyleChanged: (TextAnnotation) -> Unit,
    onDismiss: () -> Unit
) {
    var fontSize by remember { mutableStateOf(annotation.fontSize) }
    var fontFamily by remember { mutableStateOf(annotation.fontFamily) }
    var isBold by remember { mutableStateOf(annotation.fontWeight >= 700) }
    var isItalic by remember { mutableStateOf(annotation.fontStyle == "italic") }
    var textAlign by remember { mutableStateOf(annotation.textAlign) }

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
                    .fillMaxWidth(0.85f)
                    .background(EInkTheme.PopupPanel.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.PopupPanel.borderWidth, EInkTheme.PopupPanel.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.PopupPanel.padding)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Text Style",
                        fontWeight = FontWeight.Bold,
                        fontSize = EInkTheme.PopupPanel.titleFontSize,
                        color = EInkTheme.PopupPanel.titleColor
                    )
                    Box(
                        modifier = Modifier
                            .size(EInkTheme.Global.touchTarget)
                            .noRippleClickable(onClick = onDismiss),
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

                Spacer(Modifier.height(EInkTheme.Global.spacingLarge))

                // Font size slider
                Text("Font Size", fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.gray)
                Spacer(Modifier.height(EInkTheme.Global.spacingSmall))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = fontSize,
                        onValueChange = { fontSize = it },
                        valueRange = 10f..48f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = EInkTheme.Colors.primary,
                            activeTrackColor = EInkTheme.Colors.primary,
                            inactiveTrackColor = EInkTheme.Colors.lightGray
                        )
                    )
                    Spacer(Modifier.width(EInkTheme.Global.spacing))
                    Text(
                        "${fontSize.toInt()}sp",
                        fontSize = EInkTheme.Typography.bodySmall,
                        modifier = Modifier.width(40.dp)
                    )
                }

                Spacer(Modifier.height(EInkTheme.Global.spacingLarge))

                // Font family buttons
                Text("Font", fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.gray)
                Spacer(Modifier.height(EInkTheme.Global.spacing))
                Row(horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
                    FontFamilyButton(
                        label = "Sans",
                        value = "sans-serif",
                        selected = fontFamily,
                        onClick = { fontFamily = it }
                    )
                    FontFamilyButton(
                        label = "Serif",
                        value = "serif",
                        selected = fontFamily,
                        onClick = { fontFamily = it }
                    )
                    FontFamilyButton(
                        label = "Mono",
                        value = "monospace",
                        selected = fontFamily,
                        onClick = { fontFamily = it }
                    )
                }

                Spacer(Modifier.height(EInkTheme.Global.spacingLarge))

                // Style toggles (Bold, Italic)
                Text("Style", fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.gray)
                Spacer(Modifier.height(EInkTheme.Global.spacing))
                Row(horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
                    StyleToggleButton(
                        symbol = "B",
                        description = "Bold",
                        selected = isBold,
                        useBoldStyle = true,
                        onClick = { isBold = !isBold }
                    )
                    StyleToggleButton(
                        symbol = "I",
                        description = "Italic",
                        selected = isItalic,
                        useItalicStyle = true,
                        onClick = { isItalic = !isItalic }
                    )
                }

                Spacer(Modifier.height(EInkTheme.Global.spacingLarge))

                // Alignment buttons
                Text("Alignment", fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.gray)
                Spacer(Modifier.height(EInkTheme.Global.spacing))
                Row(horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
                    AlignmentButton(
                        symbol = "⊏",
                        value = "left",
                        selected = textAlign,
                        onClick = { textAlign = it }
                    )
                    AlignmentButton(
                        symbol = "≡",
                        value = "center",
                        selected = textAlign,
                        onClick = { textAlign = it }
                    )
                    AlignmentButton(
                        symbol = "⊐",
                        value = "right",
                        selected = textAlign,
                        onClick = { textAlign = it }
                    )
                }

                Spacer(Modifier.height(EInkTheme.Global.paddingLarge))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EInkTheme.Colors.primary),
                        border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary),
                        shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                    ) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(EInkTheme.Global.spacing))
                    Button(
                        onClick = {
                            onStyleChanged(annotation.copy(
                                fontSize = fontSize,
                                fontFamily = fontFamily,
                                fontWeight = if (isBold) 700 else 400,
                                fontStyle = if (isItalic) "italic" else "normal",
                                textAlign = textAlign
                            ))
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = EInkTheme.Button.primaryBackground,
                            contentColor = EInkTheme.Button.primaryTextColor
                        ),
                        shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius),
                        elevation = ButtonDefaults.elevation(0.dp)
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@Composable
private fun FontFamilyButton(
    label: String,
    value: String,
    selected: String,
    onClick: (String) -> Unit
) {
    val isSelected = selected == value
    Box(
        modifier = Modifier
            .background(if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.background)
            .border(
                width = if (isSelected) EInkTheme.Global.borderWidth else 1.dp,
                color = if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
            )
            .noRippleClickable { onClick(value) }
            .padding(horizontal = EInkTheme.Global.spacingLarge, vertical = EInkTheme.Global.spacing)
    ) {
        Text(
            label,
            fontSize = EInkTheme.Typography.bodySmall,
            color = if (isSelected) EInkTheme.Colors.white else EInkTheme.Colors.primary
        )
    }
}

@Composable
private fun StyleToggleButton(
    symbol: String,
    description: String,
    selected: Boolean,
    useBoldStyle: Boolean = false,
    useItalicStyle: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(EInkTheme.Global.touchTarget)
            .background(if (selected) EInkTheme.Colors.primary else EInkTheme.Colors.background)
            .border(
                width = if (selected) EInkTheme.Global.borderWidth else 1.dp,
                color = if (selected) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
            )
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            fontSize = EInkTheme.Typography.bodyLarge,
            fontWeight = if (useBoldStyle) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (useItalicStyle) FontStyle.Italic else FontStyle.Normal,
            color = if (selected) EInkTheme.Colors.white else EInkTheme.Colors.primary
        )
    }
}

@Composable
private fun AlignmentButton(
    symbol: String,
    value: String,
    selected: String,
    onClick: (String) -> Unit
) {
    val isSelected = selected == value
    Box(
        modifier = Modifier
            .size(EInkTheme.Global.touchTarget)
            .background(if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.background)
            .border(
                width = if (isSelected) EInkTheme.Global.borderWidth else 1.dp,
                color = if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
            )
            .noRippleClickable { onClick(value) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            fontSize = EInkTheme.Typography.bodyLarge,
            color = if (isSelected) EInkTheme.Colors.white else EInkTheme.Colors.primary
        )
    }
}
