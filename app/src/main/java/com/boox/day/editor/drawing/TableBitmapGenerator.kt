package com.boox.day.editor.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * Generates table grid bitmaps for insertion into the canvas.
 * Tables are rendered as bitmap images with grid lines for handwriting.
 */
object TableBitmapGenerator {

    // Default cell dimensions (comfortable for handwriting)
    private const val DEFAULT_CELL_WIDTH = 150
    private const val DEFAULT_CELL_HEIGHT = 100
    private const val DEFAULT_LINE_WIDTH = 2f
    private const val DEFAULT_LINE_COLOR = Color.BLACK
    private const val DEFAULT_CELL_COLOR = Color.WHITE

    /**
     * Creates a table bitmap with the specified number of rows and columns.
     * Inner dividers are drawn at half the specified thickness to appear
     * consistent with outer borders (since inner lines are shared between cells).
     *
     * @param rows Number of rows in the table
     * @param cols Number of columns in the table
     * @param cellWidth Width of each cell in pixels (default 150)
     * @param cellHeight Height of each cell in pixels (default 100)
     * @param lineWidth Width of outer border lines in pixels (default 2f)
     * @param lineColor Color of grid lines (default black)
     * @param cellColor Background color of cells (default white)
     * @return Bitmap containing the table grid
     */
    fun createTableBitmap(
        rows: Int,
        cols: Int,
        cellWidth: Int = DEFAULT_CELL_WIDTH,
        cellHeight: Int = DEFAULT_CELL_HEIGHT,
        lineWidth: Float = DEFAULT_LINE_WIDTH,
        lineColor: Int = DEFAULT_LINE_COLOR,
        cellColor: Int = DEFAULT_CELL_COLOR
    ): Bitmap {
        // Calculate total dimensions
        val totalWidth = cols * cellWidth
        val totalHeight = rows * cellHeight

        // Create bitmap with cell background color
        val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(cellColor)

        // Paint for outer borders (full thickness)
        val outerPaint = Paint().apply {
            color = lineColor
            strokeWidth = lineWidth
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        // Paint for inner dividers (half thickness to appear consistent)
        val innerPaint = Paint().apply {
            color = lineColor
            strokeWidth = (lineWidth / 2f).coerceAtLeast(1f)
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        // Draw inner vertical dividers (between columns)
        for (col in 1 until cols) {
            val x = col * cellWidth.toFloat()
            canvas.drawLine(x, 0f, x, totalHeight.toFloat(), innerPaint)
        }

        // Draw inner horizontal dividers (between rows)
        for (row in 1 until rows) {
            val y = row * cellHeight.toFloat()
            canvas.drawLine(0f, y, totalWidth.toFloat(), y, innerPaint)
        }

        // Draw outer border rectangle (full thickness)
        val halfStroke = lineWidth / 2f
        canvas.drawRect(
            halfStroke,
            halfStroke,
            totalWidth - halfStroke,
            totalHeight - halfStroke,
            outerPaint
        )

        return bitmap
    }
}
