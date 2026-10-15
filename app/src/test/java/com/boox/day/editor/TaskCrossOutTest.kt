package com.boox.day.editor

import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.editor.utils.Pen
import com.boox.day.editor.utils.TaskCrossOut
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cross out = done (plan T4 §13.6C): which strokes count as crossing a task out. */
class TaskCrossOutTest {

    // A written word spanning x 300..700, y 220..280 in a row 200..300 (row height 100)
    private val word = TaskCrossOut.Extent(left = 300f, top = 220f, right = 700f, bottom = 280f)
    private val rowHeight = 100f

    private fun stroke(vararg xy: Pair<Float, Float>, pen: Pen = Pen.BALLPEN): Stroke {
        val points = xy.map { (x, y) -> StrokePoint(x, y) }
        return Stroke(
            size = 3f,
            pen = pen,
            top = points.minOf { it.y },
            bottom = points.maxOf { it.y },
            left = points.minOf { it.x },
            right = points.maxOf { it.x },
            points = points,
            pageId = "page"
        )
    }

    @Test
    fun lineThroughTheMiddle_isCrossOut() {
        val line = stroke(290f to 252f, 450f to 248f, 600f to 251f, 710f to 247f)
        assertTrue(TaskCrossOut.isCrossOut(line, word, rowHeight))
    }

    @Test
    fun slightlySlantedLine_isCrossOut() {
        val line = stroke(300f to 265f, 500f to 250f, 700f to 235f)
        assertTrue(TaskCrossOut.isCrossOut(line, word, rowHeight))
    }

    @Test
    fun underline_isNotCrossOut() {
        val line = stroke(300f to 284f, 500f to 286f, 700f to 285f)
        assertFalse(TaskCrossOut.isCrossOut(line, word, rowHeight))
    }

    @Test
    fun shortLineOverPartOfTheWord_isNotCrossOut() {
        val line = stroke(300f to 250f, 450f to 250f)
        assertFalse(TaskCrossOut.isCrossOut(line, word, rowHeight))
    }

    @Test
    fun scribble_isNotCrossOut() {
        val line = stroke(300f to 230f, 700f to 270f, 320f to 240f, 690f to 260f)
        assertFalse(TaskCrossOut.isCrossOut(line, word, rowHeight))
    }

    @Test
    fun steepLine_isNotCrossOut() {
        // ~20°: steeper than the ~17° a cross-out may have
        val line = stroke(300f to 205f, 700f to 350f)
        assertFalse(TaskCrossOut.isCrossOut(line, word, rowHeight))
    }

    @Test
    fun highlighter_isNotCrossOut() {
        val line = stroke(290f to 250f, 710f to 250f, pen = Pen.MARKER)
        assertFalse(TaskCrossOut.isCrossOut(line, word, rowHeight))
    }

    @Test
    fun barOfATOnASingleLetter_isNotCrossOut() {
        // The only earlier content is one narrow letter: narrower than a row height
        val letter = TaskCrossOut.Extent(left = 300f, top = 220f, right = 340f, bottom = 280f)
        val bar = stroke(290f to 232f, 350f to 231f)
        assertFalse(TaskCrossOut.isCrossOut(bar, letter, rowHeight))
    }
}
