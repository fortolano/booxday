package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import com.boox.day.ui.noRippleClickable
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.io.ExportFormat
import com.boox.day.io.ExportLayout
import com.boox.day.ui.theme.EInkTheme

/**
 * Export dialog for Boox Day - modern E-Ink optimized design with gray palette.
 * Uses EInkTheme.ModernDialog for consistent, refined styling.
 */
@Composable
fun ExportDialog(
    totalPages: Int,
    onExport: (format: ExportFormat, exportAll: Boolean, layout: ExportLayout) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFormat by remember { mutableStateOf(ExportFormat.PDF) }
    var exportAll by remember { mutableStateOf(false) }
    var selectedLayout by remember { mutableStateOf(ExportLayout.COMPACT) }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                    .background(
                        EInkTheme.ModernDialog.background,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .border(
                        EInkTheme.ModernDialog.borderWidth,
                        EInkTheme.ModernDialog.borderColor,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .padding(EInkTheme.ModernDialog.padding),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.ModernDialog.contentSpacing)
            ) {
                // Title
                Text(
                    text = "Export Memo",
                    fontWeight = FontWeight.Bold,
                    fontSize = EInkTheme.ModernDialog.titleFontSize,
                    color = EInkTheme.ModernDialog.titleColor
                )

                // Format segmented control
                FormatSegmentedControl(
                    selectedFormat = selectedFormat,
                    onFormatSelected = { selectedFormat = it }
                )

                // Page selection card
                OptionCard {
                    OptionRow(
                        text = "Current page",
                        selected = !exportAll,
                        onClick = { exportAll = false },
                        showTopBorder = false
                    )
                    OptionRow(
                        text = "All $totalPages ${if (totalPages == 1) "page" else "pages"}",
                        selected = exportAll,
                        onClick = { exportAll = true },
                        showTopBorder = true
                    )
                }

                // Layout selection (PDF only)
                if (selectedFormat == ExportFormat.PDF) {
                    OptionCard {
                        OptionRow(
                            text = "Compact layout",
                            selected = selectedLayout == ExportLayout.COMPACT,
                            onClick = { selectedLayout = ExportLayout.COMPACT },
                            showTopBorder = false
                        )
                        OptionRow(
                            text = "One memo per page",
                            selected = selectedLayout == ExportLayout.ONE_PER_PAGE,
                            onClick = { selectedLayout = ExportLayout.ONE_PER_PAGE },
                            showTopBorder = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Button row - Cancel left (40%), space (20%), Export right (40%)
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Cancel button (outlined style) - 40%
                    Box(
                        modifier = Modifier
                            .weight(0.4f)
                            .height(EInkTheme.ModernDialog.PrimaryButton.height)
                            .border(
                                EInkTheme.ModernDialog.OptionCard.borderWidth,
                                EInkTheme.ModernDialog.CancelLink.textColor,
                                RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                            )
                            .noRippleClickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            color = EInkTheme.ModernDialog.CancelLink.textColor,
                            fontWeight = FontWeight.Normal,
                            fontSize = EInkTheme.ModernDialog.PrimaryButton.fontSize
                        )
                    }

                    // Space in the middle - 20%
                    Spacer(modifier = Modifier.weight(0.2f))

                    // Export button (filled dark gray) - 40%
                    Box(
                        modifier = Modifier
                            .weight(0.4f)
                            .height(EInkTheme.ModernDialog.PrimaryButton.height)
                            .background(
                                EInkTheme.ModernDialog.PrimaryButton.background,
                                RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                            )
                            .noRippleClickable { onExport(selectedFormat, exportAll, selectedLayout) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Export",
                            color = EInkTheme.ModernDialog.PrimaryButton.textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = EInkTheme.ModernDialog.PrimaryButton.fontSize
                        )
                    }
                }
            }
        }
    }
}

/**
 * Segmented control for format selection (PDF | PNG | JPG)
 * Uses subtle borders and dark gray for selected state.
 */
@Composable
private fun FormatSegmentedControl(
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
        FormatSegment(
            label = "PDF",
            selected = selectedFormat == ExportFormat.PDF,
            onClick = { onFormatSelected(ExportFormat.PDF) },
            modifier = Modifier.weight(1f)
        )
        // Vertical divider
        Box(
            modifier = Modifier
                .width(EInkTheme.ModernDialog.SegmentedControl.dividerWidth)
                .fillMaxHeight()
                .background(EInkTheme.ModernDialog.SegmentedControl.dividerColor)
        )
        FormatSegment(
            label = "PNG",
            selected = selectedFormat == ExportFormat.PNG,
            onClick = { onFormatSelected(ExportFormat.PNG) },
            modifier = Modifier.weight(1f)
        )
        // Vertical divider
        Box(
            modifier = Modifier
                .width(EInkTheme.ModernDialog.SegmentedControl.dividerWidth)
                .fillMaxHeight()
                .background(EInkTheme.ModernDialog.SegmentedControl.dividerColor)
        )
        FormatSegment(
            label = "JPG",
            selected = selectedFormat == ExportFormat.JPEG,
            onClick = { onFormatSelected(ExportFormat.JPEG) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FormatSegment(
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

/**
 * Card container for option rows - subtle border styling
 */
@Composable
private fun OptionCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                EInkTheme.ModernDialog.OptionCard.borderWidth,
                EInkTheme.ModernDialog.OptionCard.borderColor,
                RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius)
            )
    ) {
        content()
    }
}

/**
 * Single option row with radio indicator - soft gray styling
 */
@Composable
private fun OptionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    showTopBorder: Boolean
) {
    Column {
        if (showTopBorder) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EInkTheme.ModernDialog.OptionCard.dividerHeight)
                    .background(EInkTheme.ModernDialog.OptionCard.dividerColor)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .noRippleClickable { onClick() }
                .padding(
                    horizontal = EInkTheme.ModernDialog.OptionRow.paddingHorizontal,
                    vertical = EInkTheme.ModernDialog.OptionRow.paddingVertical
                )
        ) {
            // Custom radio indicator (square or circle based on cornerRadius)
            Box(
                modifier = Modifier
                    .size(EInkTheme.ModernDialog.RadioIndicator.size)
                    .border(
                        EInkTheme.ModernDialog.RadioIndicator.borderWidth,
                        EInkTheme.ModernDialog.RadioIndicator.borderColor,
                        RoundedCornerShape(EInkTheme.ModernDialog.RadioIndicator.cornerRadius)
                    )
                    .padding(EInkTheme.ModernDialog.RadioIndicator.innerPadding)
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                EInkTheme.ModernDialog.RadioIndicator.fillColor,
                                RoundedCornerShape(EInkTheme.ModernDialog.RadioIndicator.cornerRadius)
                            )
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = text,
                color = if (selected) EInkTheme.ModernDialog.OptionRow.textColorSelected
                        else EInkTheme.ModernDialog.OptionRow.textColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = EInkTheme.ModernDialog.OptionRow.fontSize
            )
        }
    }
}
