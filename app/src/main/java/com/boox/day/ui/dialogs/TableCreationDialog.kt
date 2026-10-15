package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import com.boox.day.ui.noRippleClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.theme.EInkTheme

private const val MAX_ROWS = 6
private const val MAX_COLS = 6
private val CELL_SIZE = 32.dp

// E-Ink grayscale border colors
private val BORDER_COLORS = listOf(
    Color.Black,
    Color(0xFF444444),
    Color(0xFF888888),
    Color(0xFFBBBBBB),
)

// E-Ink grayscale cell background colors
private val CELL_COLORS = listOf(
    Color.White,
    Color(0xFFF0F0F0),
    Color(0xFFDDDDDD),
    Color(0xFFBBBBBB),
)

// Border thickness options in pixels
private val THICKNESS_OPTIONS = listOf(1f, 2f, 4f, 6f)

/**
 * Dialog for creating a table with visual grid picker.
 * Horizontal layout: Grid on left, options on right.
 */
@Composable
fun TableCreationDialog(
    onTableCreated: (rows: Int, cols: Int, borderColor: Int, borderThickness: Float, cellColor: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedRows by remember { mutableStateOf(0) }
    var selectedCols by remember { mutableStateOf(0) }
    var selectedBorderColor by remember { mutableStateOf(Color.Black) }
    var selectedCellColor by remember { mutableStateOf(Color.White) }
    var selectedThickness by remember { mutableStateOf(2f) }
    val density = LocalDensity.current
    val cellSizePx = with(density) { CELL_SIZE.toPx() }

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
                    .background(
                        EInkTheme.ModernDialog.background,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .border(
                        EInkTheme.ModernDialog.borderWidth,
                        EInkTheme.ModernDialog.borderColor,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
            ) {
                // Header row with close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = EInkTheme.ModernDialog.padding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(44.dp))
                    Text(
                        "Insert Table",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = EInkTheme.ModernDialog.titleFontSize,
                        color = EInkTheme.ModernDialog.titleColor
                    )
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .noRippleClickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(24.dp),
                            tint = EInkTheme.Colors.darkGray
                        )
                    }
                }

                // Subtle divider
                Box(
                    Modifier
                        .height(EInkTheme.ModernDialog.borderWidth)
                        .fillMaxWidth()
                        .background(EInkTheme.ModernDialog.borderColor)
                )

                // Main content - horizontal layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(EInkTheme.ModernDialog.padding),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // LEFT SIDE: Grid picker
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Grid picker
                        Box(
                            modifier = Modifier
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        val col = (offset.x / cellSizePx).toInt().coerceIn(0, MAX_COLS - 1) + 1
                                        val row = (offset.y / cellSizePx).toInt().coerceIn(0, MAX_ROWS - 1) + 1
                                        selectedCols = col
                                        selectedRows = row
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures { change, _ ->
                                        change.consume()
                                        val col = (change.position.x / cellSizePx).toInt().coerceIn(0, MAX_COLS - 1) + 1
                                        val row = (change.position.y / cellSizePx).toInt().coerceIn(0, MAX_ROWS - 1) + 1
                                        selectedCols = col
                                        selectedRows = row
                                    }
                                }
                        ) {
                            Column {
                                for (row in 0 until MAX_ROWS) {
                                    Row {
                                        for (col in 0 until MAX_COLS) {
                                            val isSelected = row < selectedRows && col < selectedCols
                                            Box(
                                                modifier = Modifier
                                                    .size(CELL_SIZE)
                                                    .background(
                                                        if (isSelected) EInkTheme.Colors.darkGray else EInkTheme.Colors.background
                                                    )
                                                    .border(
                                                        EInkTheme.ModernDialog.borderWidth,
                                                        EInkTheme.ModernDialog.borderColor
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Size indicator
                        Text(
                            text = if (selectedRows > 0 && selectedCols > 0) {
                                "$selectedRows × $selectedCols"
                            } else {
                                "Select size"
                            },
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = if (selectedRows > 0) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedRows > 0 && selectedCols > 0) EInkTheme.Colors.darkGray else EInkTheme.Colors.gray
                        )
                    }

                    // Vertical divider
                    Box(
                        Modifier
                            .width(EInkTheme.ModernDialog.borderWidth)
                            .height(190.dp)
                            .background(EInkTheme.ModernDialog.borderColor)
                    )

                    // RIGHT SIDE: Options
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Border Color Section
                        Column {
                            Text(
                                text = "Border Color",
                                fontSize = EInkTheme.Typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = EInkTheme.ModernDialog.titleColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                BORDER_COLORS.forEach { color ->
                                    val isColorSelected = selectedBorderColor == color
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (isColorSelected) 2.dp else EInkTheme.ModernDialog.borderWidth,
                                                color = if (isColorSelected) EInkTheme.Colors.darkGray else EInkTheme.ModernDialog.borderColor,
                                                shape = CircleShape
                                            )
                                            .noRippleClickable { selectedBorderColor = color }
                                    )
                                }
                            }
                        }

                        // Cell Background Color Section
                        Column {
                            Text(
                                text = "Cell Color",
                                fontSize = EInkTheme.Typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = EInkTheme.ModernDialog.titleColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CELL_COLORS.forEach { color ->
                                    val isColorSelected = selectedCellColor == color
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (isColorSelected) 2.dp else EInkTheme.ModernDialog.borderWidth,
                                                color = if (isColorSelected) EInkTheme.Colors.darkGray else EInkTheme.ModernDialog.borderColor,
                                                shape = CircleShape
                                            )
                                            .noRippleClickable { selectedCellColor = color }
                                    )
                                }
                            }
                        }

                        // Border Thickness Section
                        Column {
                            Text(
                                text = "Border Thickness",
                                fontSize = EInkTheme.Typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = EInkTheme.ModernDialog.titleColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                THICKNESS_OPTIONS.forEach { thickness ->
                                    val isThicknessSelected = selectedThickness == thickness
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(
                                                if (isThicknessSelected) EInkTheme.Colors.darkGray else EInkTheme.Colors.background
                                            )
                                            .border(
                                                EInkTheme.ModernDialog.borderWidth,
                                                if (isThicknessSelected) EInkTheme.Colors.darkGray else EInkTheme.ModernDialog.borderColor
                                            )
                                            .noRippleClickable { selectedThickness = thickness },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${thickness.toInt()}",
                                            fontSize = EInkTheme.Typography.bodySmall,
                                            fontWeight = if (isThicknessSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isThicknessSelected) EInkTheme.Colors.white else EInkTheme.Colors.darkGray
                                        )
                                    }
                                }
                            }
                        }

                        // Live preview section
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Preview",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.gray
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            // 3x3 preview grid
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .border(
                                        selectedThickness.dp,
                                        selectedBorderColor,
                                        RoundedCornerShape(2.dp)
                                    )
                            ) {
                                Column {
                                    repeat(3) { row ->
                                        Row(modifier = Modifier.weight(1f)) {
                                            repeat(3) { col ->
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                        .background(selectedCellColor)
                                                        .border(
                                                            (selectedThickness / 2).dp,
                                                            selectedBorderColor
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Insert button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(EInkTheme.Button.height)
                                .background(
                                    if (selectedRows > 0 && selectedCols > 0)
                                        EInkTheme.ModernDialog.PrimaryButton.background
                                    else
                                        EInkTheme.Colors.lightGray,
                                    RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                                )
                                .noRippleClickable {
                                    if (selectedRows > 0 && selectedCols > 0) {
                                        onTableCreated(
                                            selectedRows,
                                            selectedCols,
                                            selectedBorderColor.toArgb(),
                                            selectedThickness,
                                            selectedCellColor.toArgb()
                                        )
                                        onDismiss()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Insert Table",
                                fontSize = EInkTheme.Typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = EInkTheme.Colors.white
                            )
                        }
                    }
                }
            }
        }
    }
}
