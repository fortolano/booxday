package com.boox.day.io

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.net.toUri
import com.boox.day.ai.MeetingRecap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

/** One meeting as a PDF: the meeting and its recap, then each page of notes (Nov 2026). */
object MeetingExport {
    private const val MARGIN = 54f
    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842

    data class Meeting(val title: String, val line: String, val date: String, val notes: List<String>, val recap: MeetingRecap?)

    /** "2026-11-03 Team sync.pdf", without what a file name can't hold. */
    fun fileName(date: String, title: String): String =
        "$date ${title.replace(Regex("""[\\/:*?"<>|]"""), " ").trim().take(80)}.pdf"

    /** Saves to Documents/BooxDay/Meetings/2026-11. */
    internal suspend fun save(context: Context, meeting: Meeting): ExportFiles.Saved {
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "BooxDay/Meetings/${meeting.date.take(7)}")
        return ExportFiles.save(context, folder.toUri(), fileName(meeting.date, meeting.title), "application/pdf", overwrite = false) { write(context, meeting, it) }
    }

    /** Hands a copy to another app, keeping none. */
    suspend fun share(context: Context, meeting: Meeting) {
        val folder = withContext(Dispatchers.IO) { File(context.cacheDir, "exports/shared").apply { deleteRecursively(); mkdirs() } }
        val saved = ExportFiles.save(context, folder.toUri(), fileName(meeting.date, meeting.title), "application/pdf", overwrite = true) { write(context, meeting, it) }
        val uri: Uri = saved.uri
        withContext(Dispatchers.Main) { FileShare.share(context, listOf(uri), "application/pdf") }
    }

    private suspend fun write(context: Context, meeting: Meeting, out: OutputStream) {
        val document = PdfDocument()
        try {
            var number = 1
            val first = document.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, number).create())
            drawFirstPage(first.canvas, meeting)
            document.finishPage(first)
            number++
            val engine = ExportEngine(context)
            for (pageId in meeting.notes) number = engine.writePageToPdfDocument(document, pageId, number)
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    internal fun drawFirstPage(canvas: Canvas, meeting: Meeting) {
        var y = MARGIN
        val width = (A4_WIDTH - 2 * MARGIN).toInt()
        fun text(value: String, size: Float, bold: Boolean = false, gap: Float = 6f) {
            if (value.isBlank()) return
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                textSize = size
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            canvas.save()
            canvas.translate(MARGIN, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + gap
        }
        text(meeting.title, 24f, bold = true, gap = 4f)
        text(meeting.line, 13f, gap = 12f)
        val rule = Paint().apply { color = android.graphics.Color.BLACK; strokeWidth = 1f }
        canvas.drawLine(MARGIN, y, A4_WIDTH - MARGIN, y, rule)
        y += 14f
        val recap = meeting.recap ?: return
        fun section(name: String, items: List<String>) {
            if (items.isEmpty()) return
            text(name, 15f, bold = true)
            items.forEach { text("•  $it", 13f, gap = 4f) }
            y += 10f
        }
        text("Summary", 15f, bold = true)
        text(recap.summary, 13f, gap = 14f)
        section("Decisions", recap.decisions)
        section("Action items", recap.actionItems.map { action -> listOf(action.text, action.owner, action.dueDate).filter { it.isNotBlank() }.joinToString(" · ") })
        section("Follow-ups", recap.followUps)
        section("Open questions", recap.openQuestions)
    }
}
