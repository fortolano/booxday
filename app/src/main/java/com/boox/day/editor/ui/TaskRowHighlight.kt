package com.boox.day.editor.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Overlay composable that highlights a specific task row with a dashed border.
 * Used to show which row will be moved when the user taps the move chevron.
 *
 * Uses Spacer with drawWithContent to ensure touches pass through to canvas below.
 *
 * E-ink optimized: Simple dashed border, no animations, high contrast.
 */
@Composable
fun TaskRowHighlight(
    rowIndex: Int,
    rowCount: Int = 10,
    modifier: Modifier = Modifier
) {
    if (rowIndex < 0 || rowIndex >= rowCount) return

    val highlightColor = Color.DarkGray
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

    // Use Spacer instead of Box - Spacer doesn't intercept touch events
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                val topOffsetPx = TASKS_TOOLBAR_OFFSET.toPx()
                val visibleHeight = size.height - topOffsetPx
                val rowHeight = visibleHeight / rowCount

                // Calculate the bounds of the highlighted row
                val rowTop = topOffsetPx + rowIndex * rowHeight
                val rowBottom = topOffsetPx + (rowIndex + 1) * rowHeight

                // Padding inside the row for visual clarity
                val padding = 4.dp.toPx()

                // Draw dashed rectangle around the row
                drawRect(
                    color = highlightColor,
                    topLeft = Offset(padding, rowTop + padding),
                    size = Size(
                        width = size.width - (padding * 2),
                        height = rowHeight - (padding * 2)
                    ),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = dashEffect
                    )
                )

                // (No translucent fill: e-ink has no alpha — plan T3 / 13.3)
            }
    )
}

/**
 * Highlight with canvas height parameter for calculating row bounds.
 * This version allows explicit height specification for accurate positioning.
 * Uses Spacer to ensure touches pass through.
 */
@Composable
fun TaskRowHighlightWithHeight(
    rowIndex: Int,
    canvasHeight: Float,
    rowCount: Int = 10,
    modifier: Modifier = Modifier
) {
    if (rowIndex < 0 || rowIndex >= rowCount || canvasHeight <= 0) return

    val highlightColor = Color.DarkGray
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                val topOffsetPx = TASKS_TOOLBAR_OFFSET.toPx()
                val visibleHeight = canvasHeight - topOffsetPx
                val rowHeight = visibleHeight / rowCount

                // Calculate the bounds of the highlighted row
                val rowTop = topOffsetPx + rowIndex * rowHeight

                // Padding inside the row for visual clarity
                val padding = 4.dp.toPx()

                // Draw dashed rectangle around the row
                drawRect(
                    color = highlightColor,
                    topLeft = Offset(padding, rowTop + padding),
                    size = Size(
                        width = size.width - (padding * 2),
                        height = rowHeight - (padding * 2)
                    ),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = dashEffect
                    )
                )

                // (No translucent fill: e-ink has no alpha — plan T3 / 13.3)
            }
    )
}
