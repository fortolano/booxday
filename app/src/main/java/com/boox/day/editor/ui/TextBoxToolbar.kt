package com.boox.day.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boox.day.data.db.TextAnnotation

/**
 * Font size options for text boxes
 */
enum class FontSizeOption(val label: String, val size: Float) {
    SMALL("S", 18f),
    MEDIUM("M", 24f),
    LARGE("L", 36f)
}

/**
 * Mini toolbar that appears when a text box is selected.
 * Provides font size, bold, italic, delete, and duplicate options.
 * Optimized for e-ink display with black/white styling.
 */
@Composable
fun TextBoxToolbar(
    annotation: TextAnnotation,
    onUpdateAnnotation: (TextAnnotation) -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var fontSizeExpanded by remember { mutableStateOf(false) }

    // Determine current font size option
    val currentFontSize = when {
        annotation.fontSize <= 20f -> FontSizeOption.SMALL
        annotation.fontSize <= 30f -> FontSizeOption.MEDIUM
        else -> FontSizeOption.LARGE
    }

    val isBold = annotation.fontWeight >= 700
    val isItalic = annotation.fontStyle == "italic"

    Card(
        modifier = modifier
            .wrapContentSize(),
        backgroundColor = Color.White,
        elevation = 0.dp,
        border = BorderStroke(1.dp, Color.Black),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Font size dropdown
            Box {
                ToolbarIconButton(
                    onClick = { fontSizeExpanded = true },
                    isSelected = false
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentFontSize.label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Font size",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = fontSizeExpanded,
                    onDismissRequest = { fontSizeExpanded = false },
                    modifier = Modifier
                        .background(Color.White)
                        .border(1.dp, Color.LightGray)
                ) {
                    FontSizeOption.values().forEach { option ->
                        DropdownMenuItem(
                            onClick = {
                                onUpdateAnnotation(annotation.copy(fontSize = option.size))
                                fontSizeExpanded = false
                            }
                        ) {
                            Text(
                                text = "${option.label} - ${option.label.let {
                                    when(it) {
                                        "S" -> "Small"
                                        "M" -> "Medium"
                                        "L" -> "Large"
                                        else -> it
                                    }
                                }}",
                                fontSize = 14.sp,
                                color = if (currentFontSize == option) Color.Black else Color.Gray
                            )
                        }
                    }
                }
            }

            VerticalDivider()

            // Bold button
            ToolbarIconButton(
                onClick = {
                    val newWeight = if (isBold) 400 else 700
                    onUpdateAnnotation(annotation.copy(fontWeight = newWeight))
                },
                isSelected = isBold
            ) {
                Text(
                    text = "B",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Italic button
            ToolbarIconButton(
                onClick = {
                    val newStyle = if (isItalic) "normal" else "italic"
                    onUpdateAnnotation(annotation.copy(fontStyle = newStyle))
                },
                isSelected = isItalic
            ) {
                Text(
                    text = "I",
                    fontSize = 16.sp,
                    fontStyle = FontStyle.Italic
                )
            }

            VerticalDivider()

            // Delete button
            ToolbarIconButton(
                onClick = onDelete,
                isSelected = false
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Duplicate button
            ToolbarIconButton(
                onClick = onDuplicate,
                isSelected = false
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Duplicate",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ToolbarIconButton(
    onClick: () -> Unit,
    isSelected: Boolean,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(
                if (isSelected) Color.LightGray else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(24.dp)
            .background(Color.LightGray)
    )
}
