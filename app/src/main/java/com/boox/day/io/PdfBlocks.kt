package com.boox.day.io

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.boox.day.data.datastore.A4_WIDTH
import kotlin.math.ceil

/**
 * What goes on the pages of a PDF, in reading order (Nov 2026). Heights are PDF points. The engine
 * packs blocks onto pages; [split] lets a block run on to the next one.
 */
internal sealed interface SheetBlock {
    val date: String
    val height: Int

    /** One memo per page: this block opens a new PDF page. */
    val startsSheet: Boolean get() = false

    /** Stays on the same PDF page as the block after it. */
    val keepWithNext: Boolean get() = false

    /** This block cut to fit [height], and the rest; null when it can't be cut there. */
    fun split(height: Int): Pair<SheetBlock, SheetBlock>? = null
}

/** A slice of a memo page at full page width, drawn as vectors. One memo per page. */
internal data class PageSliceBlock(
    override val date: String,
    val pageId: String,
    val left: Float,
    val top: Float,
    val bottom: Float,
    val scale: Float,
    override val startsSheet: Boolean,
    /** Blank rows of the page, where it can be cut without going through writing. */
    val blankRows: List<Float> = emptyList()
) : SheetBlock {
    override val height: Int get() = ceil((bottom - top) * scale).toInt()

    override fun split(height: Int): Pair<SheetBlock, SheetBlock>? {
        if (height < MIN_SLICE) return null
        val limit = top + height / scale
        val cut = blankRows.lastOrNull { it > top && it <= limit } ?: limit
        return copy(bottom = cut) to copy(top = cut, startsSheet = false)
    }

    private companion object {
        const val MIN_SLICE = 72
    }
}

/** A line of text in a block. [ticked]: null for no box, else a task's box. */
internal data class TextRow(val text: String, val ticked: Boolean? = null)

/** The day's events and tasks, side by side, at the start of the day. */
internal class DayInfoBlock(
    override val date: String,
    private val events: List<TextRow>,
    private val tasks: List<TextRow>,
    override val startsSheet: Boolean = false
) : SheetBlock {
    private val left = PdfText.column("Events", events)
    private val right = PdfText.column("Tasks", tasks)

    override val height: Int = ceil(maxOf(left.height, right.height) + PdfText.BLOCK_PADDING).toInt()
    override val keepWithNext: Boolean get() = true

    override fun split(height: Int): Pair<SheetBlock, SheetBlock>? {
        val rows = (1 until maxOf(events.size, tasks.size)).lastOrNull { n ->
            DayInfoBlock(date, events.take(n), tasks.take(n)).height <= height
        } ?: return null
        return DayInfoBlock(date, events.take(rows), tasks.take(rows), startsSheet) to
            DayInfoBlock(date, events.drop(rows), tasks.drop(rows))
    }

    fun draw(canvas: Canvas, y: Float) {
        left.draw(canvas, PdfText.SIDE, y)
        right.draw(canvas, PdfText.SIDE + PdfText.COLUMN + PdfText.GAP, y)
        PdfText.divider(canvas, y + height - PdfText.BLOCK_PADDING / 2)
    }
}

/** Text in two balanced columns under a [heading] (typed text under a memo page). */
internal class TextFlowBlock(
    override val date: String,
    private val heading: String?,
    private val rows: List<TextRow>
) : SheetBlock {
    private val headingHeight = if (heading != null) PdfText.TITLE_LINE else 0f
    private val left = PdfText.column(null, rows.take((rows.size + 1) / 2))
    private val right = PdfText.column(null, rows.drop((rows.size + 1) / 2))

    override val height: Int =
        ceil(PdfText.BLOCK_PADDING + headingHeight + maxOf(left.height, right.height)).toInt()

    override fun split(height: Int): Pair<SheetBlock, SheetBlock>? {
        val count = (1 until rows.size).lastOrNull { TextFlowBlock(date, heading, rows.take(it)).height <= height }
            ?: return null
        return TextFlowBlock(date, heading, rows.take(count)) to TextFlowBlock(date, null, rows.drop(count))
    }

    fun draw(canvas: Canvas, y: Float) {
        PdfText.divider(canvas, y + PdfText.BLOCK_PADDING / 2)
        val top = y + PdfText.BLOCK_PADDING
        heading?.let { PdfText.title(canvas, it, PdfText.SIDE, top) }
        left.draw(canvas, PdfText.SIDE, top + headingHeight)
        right.draw(canvas, PdfText.SIDE + PdfText.COLUMN + PdfText.GAP, top + headingHeight)
    }
}

/** Text in a PDF: real text, so it can be selected and searched. */
internal object PdfText {
    const val SIDE = 32f
    const val GAP = 16f
    val COLUMN = (A4_WIDTH - 2 * SIDE - GAP) / 2f
    const val BLOCK_PADDING = 12f
    const val TITLE_LINE = 15f

    private const val TEXT_SIZE = 9.5f
    private const val TITLE_SIZE = 10.5f
    private const val ROW_GAP = 2f
    private const val BOX = 8f
    private const val BOX_INDENT = 13f

    private val body = TextPaint().apply {
        color = Color.BLACK
        textSize = TEXT_SIZE
        typeface = Typeface.DEFAULT
        isAntiAlias = true
    }
    private val bold = TextPaint(body).apply {
        textSize = TITLE_SIZE
        typeface = Typeface.DEFAULT_BOLD
    }
    private val line = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 0.5f
    }
    private val box = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        isAntiAlias = true
    }

    /** A column of [rows] under an optional [title]. */
    fun column(title: String?, rows: List<TextRow>): Column = Column(title, rows.map { row ->
        val indent = if (row.ticked != null) BOX_INDENT else 0f
        LaidOutRow(layout(row.text, (COLUMN - indent).toInt()), row.ticked, indent)
    })

    private fun layout(text: String, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, body, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

    fun title(canvas: Canvas, text: String, x: Float, y: Float) {
        canvas.drawText(text, x, y + TITLE_SIZE, bold)
    }

    fun divider(canvas: Canvas, y: Float) {
        canvas.drawLine(SIDE, y, A4_WIDTH - SIDE, y, line)
    }

    internal class LaidOutRow(val layout: StaticLayout, val ticked: Boolean?, val indent: Float) {
        val height: Float get() = layout.height + ROW_GAP
    }

    class Column internal constructor(private val title: String?, private val rows: List<LaidOutRow>) {
        val height: Float
            get() = if (rows.isEmpty()) 0f else rows.sumOf { it.height.toDouble() }.toFloat() + if (title != null) TITLE_LINE else 0f

        fun draw(canvas: Canvas, x: Float, y: Float) {
            if (rows.isEmpty()) return
            var top = y
            title?.let {
                title(canvas, it, x, top)
                top += TITLE_LINE
            }
            for (row in rows) {
                row.ticked?.let { drawBox(canvas, x, top + 1f, it) }
                canvas.save()
                canvas.translate(x + row.indent, top)
                row.layout.draw(canvas)
                canvas.restore()
                top += row.height
            }
        }

        private fun drawBox(canvas: Canvas, x: Float, y: Float, ticked: Boolean) {
            canvas.drawRect(x, y, x + BOX, y + BOX, box)
            if (ticked) {
                val tick = Path().apply {
                    moveTo(x + 1.6f, y + 4f)
                    lineTo(x + 3.4f, y + 6.2f)
                    lineTo(x + 6.6f, y + 1.6f)
                }
                canvas.drawPath(tick, box)
            }
        }
    }
}
