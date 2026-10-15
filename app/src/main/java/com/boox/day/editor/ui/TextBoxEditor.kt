package com.boox.day.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boox.day.data.db.TextAnnotation

/**
 * Editor overlay for editing text annotation content.
 * Appears when user taps inside a selected text box.
 * Shows keyboard and allows text editing.
 */
@Composable
fun TextBoxEditor(
    annotation: TextAnnotation,
    screenX: Float,
    screenY: Float,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editedText by remember(annotation.id) { mutableStateOf(annotation.content) }
    val focusRequester = remember { FocusRequester() }

    // Use fixed font size for consistent editing experience
    // The actual annotation font size is applied when displaying, not editing
    val editorFontSize = 18.sp

    // Request focus when editor appears
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Card(
        modifier = modifier
            .offset {
                // screenX/screenY are already in pixels, use IntOffset directly
                IntOffset(
                    x = screenX.toInt().coerceAtLeast(8),
                    y = screenY.toInt().coerceAtLeast(8)
                )
            }
            .widthIn(min = 200.dp, max = 400.dp),
        backgroundColor = Color.White,
        elevation = 0.dp,
        border = BorderStroke(2.dp, Color.Black),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Header with save/cancel buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit Text",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { onSave(editedText) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Save",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Text input field
            BasicTextField(
                value = editedText,
                onValueChange = { editedText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 200.dp)
                    .background(Color(0xFFF5F5F5), RoundedCornerShape(4.dp))
                    .padding(8.dp)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    fontSize = editorFontSize,
                    fontWeight = if (annotation.fontWeight >= 700) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (annotation.fontStyle == "italic") FontStyle.Italic else FontStyle.Normal,
                    color = Color.Black
                ),
                cursorBrush = SolidColor(Color.Black),
                decorationBox = { innerTextField ->
                    Box {
                        if (editedText.isEmpty()) {
                            Text(
                                text = "Enter text...",
                                color = Color.Gray,
                                fontSize = editorFontSize
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            // Quick action buttons - e-ink optimized
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                    border = BorderStroke(1.dp, Color.Black)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSave(editedText) },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color.Black,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.elevation(0.dp)
                ) {
                    Text("Save", fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Inline text editor that appears directly over the text box.
 * More minimal design for quick edits.
 */
@Composable
fun InlineTextBoxEditor(
    annotation: TextAnnotation,
    onTextChanged: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editedText by remember(annotation.id) { mutableStateOf(annotation.content) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .background(Color.White)
            .padding(annotation.padding.dp)
    ) {
        BasicTextField(
            value = editedText,
            onValueChange = {
                editedText = it
                onTextChanged(it)
            },
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester),
            textStyle = TextStyle(
                fontSize = annotation.fontSize.sp,
                fontWeight = if (annotation.fontWeight >= 700) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (annotation.fontStyle == "italic") FontStyle.Italic else FontStyle.Normal,
                color = Color(annotation.textColor)
            ),
            cursorBrush = SolidColor(Color.Black)
        )
    }
}
