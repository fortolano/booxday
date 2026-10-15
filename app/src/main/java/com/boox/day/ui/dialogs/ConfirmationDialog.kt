package com.boox.day.ui.dialogs

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Alignment as ComposeAlignment
import com.boox.day.io.ExportEngine
import com.boox.day.io.ExportFormat
import com.boox.day.io.ExportOptions
import com.boox.day.io.ExportTarget
import com.boox.day.ui.SnackState
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ShowSimpleConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    confirmButtonText: String,
    cancelButtonText: String = "Cancel"
) {
    ShowConfirmationDialog(
        title = title,
        content = { Text(text = message, fontSize = EInkTheme.Typography.bodyLarge) },
        onConfirm = onConfirm,
        onCancel = onCancel,
        onDismiss = onCancel,
        confirmButtonText = confirmButtonText,
        cancelButtonText = cancelButtonText
    )
}


@Composable
fun ShowConfirmationDialog(
    title: String,
    content: @Composable (() -> Unit),
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit = onCancel,
    confirmButtonText: String,
    cancelButtonText: String = "Cancel"
) {
    // E-Ink: Use Popup instead of Dialog to avoid system scrim/dimming
    Popup(
        alignment = ComposeAlignment.Center,
        onDismissRequest = { onDismiss() },
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = ComposeAlignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                    .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.ModernDialog.padding),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.ModernDialog.contentSpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = EInkTheme.ModernDialog.titleFontSize,
                    color = EInkTheme.ModernDialog.titleColor
                )
                content()
                // 40% / 20% / 40% button layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = EInkTheme.ModernDialog.contentSpacing)
                ) {
                    EinkSecondaryButton(
                        text = cancelButtonText,
                        onClick = onCancel,
                        modifier = Modifier.weight(0.4f)
                    )
                    Spacer(modifier = Modifier.weight(0.2f))
                    ActionButton(
                        text = confirmButtonText,
                        onClick = onConfirm,
                        modifier = Modifier.weight(0.4f)
                    )
                }
            }
        }
    }
}

@Composable
fun ShowExportDialog(
    snackManager: SnackState,
    bookId: String,
    context: Context,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val selectedFormat = remember { mutableStateOf(ExportFormat.PDF) }

    // E-Ink: Use Popup instead of Dialog to avoid system scrim/dimming
    Popup(
        alignment = ComposeAlignment.Center,
        onDismissRequest = { onCancel() },
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = ComposeAlignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                    .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.ModernDialog.padding),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.ModernDialog.contentSpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Choose Export Format",
                    fontWeight = FontWeight.Bold,
                    fontSize = EInkTheme.ModernDialog.titleFontSize,
                    color = EInkTheme.ModernDialog.titleColor
                )
                Text(
                    text = "Select the format in which you want to export the book:\n" + "- Xopp: Preserves all data and can be imported. " + "However, if opened and saved by Xournal++, tool-specific information will be lost, " + "and all strokes will be interpreted as ballpoint pen.\n" + "- PDF: A standard format for document sharing.",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    color = EInkTheme.Colors.primary
                )
                ExportFormatSegmentedControl(
                    selectedFormat = selectedFormat.value,
                    onFormatSelected = { selectedFormat.value = it }
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = EInkTheme.ModernDialog.contentSpacing)
                ) {
                    EinkSecondaryButton(
                        text = "Cancel", onClick = onCancel
                    )
                    ActionButton(
                        text = "Export", onClick = {
                            if (selectedFormat.value == ExportFormat.PDF) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    snackManager.runWithSnack(
                                        "Exporting book to PDF.."
                                    ) {
                                        ExportEngine(context).export(
                                            target = ExportTarget.Book(bookId = bookId),
                                            format = ExportFormat.PDF,
                                            options = ExportOptions(
                                                copyToClipboard = false,
                                            )
                                        )
                                    }
                                }
                            } else {
                                CoroutineScope(Dispatchers.IO).launch {
                                    snackManager.runWithSnack(
                                        "Exporting the book to xopp..."
                                    ) {
                                        ExportEngine(context).export(
                                            target = ExportTarget.Book(bookId = bookId),
                                            format = ExportFormat.XOPP,
                                        )
                                    }
                                }
                            }
                            onConfirm()
                        })
                }
            }
        }
    }
}

@Composable
private fun ExportFormatSegmentedControl(
    selectedFormat: ExportFormat,
    onFormatSelected: (ExportFormat) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.ModernDialog.SegmentedControl.height)
            .border(
                EInkTheme.ModernDialog.SegmentedControl.borderWidth,
                EInkTheme.ModernDialog.SegmentedControl.borderColor,
                RoundedCornerShape(EInkTheme.ModernDialog.SegmentedControl.cornerRadius)
            )
    ) {
        ExportFormatSegment(
            label = "PDF",
            selected = selectedFormat == ExportFormat.PDF,
            onClick = { onFormatSelected(ExportFormat.PDF) },
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(EInkTheme.ModernDialog.SegmentedControl.dividerWidth)
                .fillMaxHeight()
                .background(EInkTheme.ModernDialog.SegmentedControl.dividerColor)
        )
        ExportFormatSegment(
            label = "Xopp",
            selected = selectedFormat == ExportFormat.XOPP,
            onClick = { onFormatSelected(ExportFormat.XOPP) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ExportFormatSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(
                if (selected) EInkTheme.ModernDialog.SegmentedControl.backgroundSelected
                else EInkTheme.ModernDialog.SegmentedControl.background
            )
            .noRippleClickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) EInkTheme.ModernDialog.SegmentedControl.textColorSelected
            else EInkTheme.ModernDialog.SegmentedControl.textColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = EInkTheme.ModernDialog.SegmentedControl.fontSize
        )
    }
}
