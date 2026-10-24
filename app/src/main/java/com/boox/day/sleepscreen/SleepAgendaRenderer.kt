package com.boox.day.sleepscreen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.boox.day.today.DayDigest
import com.boox.day.today.DigestEvent
import com.boox.day.today.DigestTask
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the sleep-screen agenda (Oct 2026): today's date, schedule and tasks, the memo page and
 * the next days on large screens, in pure black and white (grey would dither on the panel).
 * Hierarchy comes from size, weight, rules and inverted badges only. What is given up when space
 * runs short is decided by [SleepAgendaLayout]; sizes come from [SleepAgendaStyle].
 */
class SleepAgendaRenderer(private val spec: SleepAgendaStyle) {

    data class Input(
        val digest: DayDigest,
        val widthPx: Int,
        val heightPx: Int,
        val nowMs: Long,
        val zone: ZoneId = ZoneId.systemDefault(),
        val use24Hour: Boolean = false,
        /** Today's memo page, or null. Shown on medium and large screens when it fits. */
        val memo: Bitmap? = null,
        val locale: Locale = Locale.getDefault(),
    )

    // ── Paints ────────────────────────────────────────────────────────────────────────────────
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val bold = Typeface.create("sans-serif", Typeface.BOLD)
    private val light = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val italic = Typeface.create("sans-serif", Typeface.ITALIC)

    private fun text(pt: Float, face: Typeface, tabular: Boolean = false, spacing: Float = 0f) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = spec.px(pt)
            typeface = face
            letterSpacing = spacing
            if (tabular) fontFeatureSettings = "tnum"
        }

    private val numeralPaint = text(spec.numeralPt, light, tabular = true, spacing = -0.02f)
    private val weekdayPaint = text(spec.weekdayPt, bold)
    private val monthPaint = text(spec.monthPt, regular)
    private val labelPaint = text(spec.labelPt, bold, spacing = 0.14f)
    private val bodyPaint = text(spec.bodyPt, regular)
    private val bodyStrongPaint = text(spec.bodyPt, bold)
    private val timePaint = text(spec.bodyPt, medium, tabular = true)
    private val secondaryPaint = text(spec.secondaryPt, regular, tabular = true)
    private val secondaryStrongPaint = text(spec.secondaryPt, bold, tabular = true)
    private val secondaryItalicPaint = text(spec.secondaryPt, italic)
    private val bodyItalicPaint = text(spec.bodyPt, italic)
    private val badgePaint = text(spec.secondaryPt * 0.9f, bold).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }
    private val hair = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; this.style = Paint.Style.STROKE; strokeWidth = this@SleepAgendaRenderer.spec.hairline
    }
    private val heavy = Paint().apply { color = Color.BLACK; this.style = Paint.Style.FILL }
    private val whiteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; this.style = Paint.Style.FILL }

    private fun lineHeight(p: Paint): Float = p.fontMetrics.let { it.descent - it.ascent }
    private fun capHeight(p: Paint): Float {
        val r = Rect(); p.getTextBounds("H", 0, 1, r); return r.height().toFloat()
    }

    private val margin = spec.mm(spec.marginMm)
    private val gap = spec.mm(spec.gapMm)
    private val rowHeight = lineHeight(bodyPaint) * if (spec.sizeClass == SleepSizeClass.COMPACT) 1.36f else 1.45f
    private val secondLine = lineHeight(secondaryPaint) * 1.1f

    private val metrics = SleepAgendaLayout.Metrics(
        sectionHeader = lineHeight(labelPaint) + gap * 0.9f,
        row = rowHeight,
        secondLine = secondLine,
        allDayRow = lineHeight(secondaryPaint) * 1.9f + gap * 0.4f,
        note = rowHeight,
        memoPanelMin = spec.mm(26f),
        nextDaysPanel = lineHeight(labelPaint) + gap * 0.9f + lineHeight(bodyStrongPaint) * 1.3f + 3 * lineHeight(secondaryPaint) * 1.35f,
        monthPanel = lineHeight(labelPaint) + gap * 0.9f + 7 * monthCell(),
        gap = gap * 1.6f,
    )

    /** Height of one row of the month calendar (weekday letters, then up to six weeks). */
    private fun monthCell() = lineHeight(secondaryPaint) * if (spec.sizeClass == SleepSizeClass.COMPACT) 1.3f else 1.55f

    fun render(input: Input): Bitmap {
        val bitmap = Bitmap.createBitmap(input.widthPx, input.heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val w = input.widthPx.toFloat()
        val h = input.heightPx.toFloat()
        val now = Instant.ofEpochMilli(input.nowMs).atZone(input.zone)

        val headerBottom = drawHeader(canvas, input, w)
        val footerTop = drawFooter(canvas, input, w, h, now.toLocalTime().format(timeFormatter(input)))

        val bodyTop = headerBottom + gap * 1.4f
        val bodyBottom = footerTop - gap * 1.2f
        drawBody(canvas, input, margin, bodyTop, w - margin, bodyBottom)

        toBlackAndWhite(bitmap)
        return bitmap
    }

    // ── Header: big numeral, weekday over month and year, week number ─────────────────────────
    private fun drawHeader(canvas: Canvas, input: Input, w: Float): Float {
        val date = input.digest.date
        val numeral = date.dayOfMonth.toString()
        val numeralCap = capHeight(numeralPaint)
        val numeralBaseline = margin + numeralCap
        canvas.drawText(numeral, margin - spec.mm(0.6f), numeralBaseline, numeralPaint)

        val textX = margin + numeralPaint.measureText(numeral) + spec.mm(4f)
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, input.locale)
        val monthYear = date.month.getDisplayName(TextStyle.FULL, input.locale) + " " + date.year
        val weekdayBaseline = margin + capHeight(weekdayPaint)
        canvas.drawText(weekday, textX, weekdayBaseline, weekdayPaint)
        canvas.drawText(monthYear, textX, numeralBaseline, monthPaint)

        val week = "Week " + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        canvas.drawText(week, w - margin - secondaryPaint.measureText(week), weekdayBaseline, secondaryPaint)

        val ruleTop = numeralBaseline + gap * 1.2f
        canvas.drawRect(margin, ruleTop, w - margin, ruleTop + spec.heavy, heavy)
        return ruleTop + spec.heavy
    }

    // ── Footer: progress, streak, when it was drawn ───────────────────────────────────────────
    private fun drawFooter(canvas: Canvas, input: Input, w: Float, h: Float, timeText: String): Float {
        val baseline = h - margin - secondaryPaint.fontMetrics.descent
        val top = baseline - capHeight(secondaryPaint) - gap
        canvas.drawLine(margin, top, w - margin, top, hair)

        // Left: progress and streak. Right: when it was drawn. Narrow screens drop the
        // app name, then the streak, so the two sides never meet.
        val digest = input.digest
        val done = if (digest.topLevelCount > 0) "${digest.doneCount} of ${digest.topLevelCount} done" else null
        val barW = spec.mm(if (spec.sizeClass == SleepSizeClass.COMPACT) 12f else 24f)
        val barSpace = barW + spec.mm(2.5f)
        val streak = if (digest.streakDays > 1) "${digest.streakDays}-day streak" else null
        fun leftWidth(withStreak: Boolean): Float {
            var lw = if (done != null) barSpace + secondaryStrongPaint.measureText(done) else 0f
            if (withStreak && streak != null) lw += secondaryPaint.measureText((if (done != null) "  ·  " else "") + streak)
            return lw
        }
        val minGap = spec.mm(4f)
        var right = "Updated $timeText  ·  Boox Day"
        if (leftWidth(true) + minGap + secondaryPaint.measureText(right) > w - 2 * margin) right = "Updated $timeText"
        val showStreak = leftWidth(true) + minGap + secondaryPaint.measureText(right) <= w - 2 * margin
        canvas.drawText(right, w - margin - secondaryPaint.measureText(right), baseline, secondaryPaint)

        var x = margin
        if (done != null) {
            val barH = spec.mm(2.2f)
            val barTop = baseline - capHeight(secondaryPaint) / 2f - barH / 2f
            val track = RectF(x, barTop, x + barW, barTop + barH)
            canvas.drawRoundRect(track, barH / 2f, barH / 2f, hair)
            val fraction = digest.doneCount.toFloat() / digest.topLevelCount
            if (fraction > 0f) {
                val inset = spec.hairline * 1.5f
                val inner = RectF(track.left + inset, track.top + inset, track.left + inset + (barW - 2 * inset) * fraction, track.bottom - inset)
                canvas.drawRoundRect(inner, inner.height() / 2f, inner.height() / 2f, fill)
            }
            x += barSpace
            canvas.drawText(done, x, baseline, secondaryStrongPaint)
            x += secondaryStrongPaint.measureText(done)
        }
        if (streak != null && showStreak) {
            val sep = if (done != null) "  ·  " else ""
            canvas.drawText(sep + streak, x, baseline, secondaryPaint)
        }
        return top
    }

    // ── Body ──────────────────────────────────────────────────────────────────────────────────
    private fun drawBody(canvas: Canvas, input: Input, left: Float, top: Float, right: Float, bottom: Float) {
        val digest = input.digest
        val nowMs = input.nowMs
        if (digest.events.isEmpty() && digest.tasks.isEmpty() && input.memo == null) {
            val message = "Nothing planned today."
            val y = top + (bottom - top) * 0.3f
            canvas.drawText(message, (left + right) / 2f - weekdayPaint.measureText(message) / 2f, y, weekdayPaint)
            if (bottom - y - metrics.gap * 2 >= metrics.monthPanel) {
                drawMonth(canvas, input, left, bottom - metrics.monthPanel, right)
            }
            return
        }

        val allDay = digest.events.filter { it.allDay }
        val timed = digest.events.filter { !it.allDay }
        val past = timed.filter { (it.endMs ?: it.startMs) <= nowMs }
        val coming = timed - past.toSet()
        val content = SleepAgendaLayout.Content(
            allDayEvents = allDay.size,
            pastTimedEvents = past.size,
            comingTimedEvents = coming.size,
            eventsWithLocation = timed.count { secondLineOf(it, input) != null },
            openTasks = digest.tasks.count { !it.completed && !it.isSubtask },
            doneTasks = digest.tasks.count { it.completed && !it.isSubtask },
            subtasks = digest.tasks.count { it.isSubtask },
            hasMemo = input.memo != null,
            hasNextDays = digest.nextDays.isNotEmpty(),
        )
        val plan = SleepAgendaLayout.plan(spec.sizeClass, content, metrics, bottom - top)

        if (spec.sizeClass == SleepSizeClass.COMPACT) {
            val scheduleH = SleepAgendaLayout.scheduleHeight(content, metrics, plan, plan.eventRows)
            drawSchedule(canvas, input, plan, left, top, right, allDay, past, coming)
            drawTasks(canvas, input, plan, left, top + scheduleH + metrics.gap, right)
            if (plan.showMonth) drawMonth(canvas, input, left, bottom - metrics.monthPanel, right)
            return
        }

        val split = left + (right - left) * 0.54f
        val columnGap = spec.mm(spec.gapMm * 1.2f)
        drawSchedule(canvas, input, plan, left, top, split - columnGap / 2f, allDay, past, coming)
        drawTasks(canvas, input, plan, split + columnGap / 2f, top, right)
        // A hairline between the columns, over the height they use
        val columnsH = max(
            SleepAgendaLayout.scheduleHeight(content, metrics, plan, plan.eventRows),
            SleepAgendaLayout.tasksHeight(content, metrics, plan, plan.taskRows)
        )
        canvas.drawLine(split, top + metrics.sectionHeader, split, top + columnsH, hair)

        var panelBottom = bottom
        if (plan.showMonth) {
            val panelTop = bottom - metrics.monthPanel
            drawMonth(canvas, input, left, panelTop, right)
            panelBottom = panelTop - metrics.gap
        }
        if (plan.showNextDays) {
            val panelTop = panelBottom - metrics.nextDaysPanel
            drawNextDays(canvas, input, left, panelTop, right)
            panelBottom = panelTop - metrics.gap
        }
        val memo = input.memo
        if (plan.showMemo && memo != null) {
            drawMemo(canvas, memo, left, panelBottom - plan.memoHeight, right, panelBottom)
        }
    }

    private fun sectionHeader(canvas: Canvas, label: String, aside: String?, left: Float, top: Float, right: Float): Float {
        val baseline = top + capHeight(labelPaint)
        canvas.drawText(label.uppercase(Locale.ROOT), left, baseline, labelPaint)
        if (aside != null) canvas.drawText(aside, right - secondaryPaint.measureText(aside), baseline, secondaryPaint)
        val ruleY = baseline + gap * 0.45f
        canvas.drawLine(left, ruleY, right, ruleY, hair)
        return top + metrics.sectionHeader
    }

    // ── Schedule ──────────────────────────────────────────────────────────────────────────────
    private fun drawSchedule(
        canvas: Canvas, input: Input, plan: SleepAgendaLayout.Plan, left: Float, top: Float, right: Float,
        allDay: List<DigestEvent>, past: List<DigestEvent>, coming: List<DigestEvent>,
    ) {
        val count = allDay.size + past.size + coming.size
        var y = sectionHeader(canvas, "Schedule", if (count > 0) plural(count, "event") else null, left, top, right)
        if (count == 0) {
            canvas.drawText("No events today.", left, y + baselineIn(rowHeight, secondaryItalicPaint), secondaryItalicPaint)
            return
        }
        if (allDay.isNotEmpty()) {
            drawPills(canvas, allDay.map { it.title }, left, y, right)
            y += metrics.allDayRow
        }

        // Rows: an "N earlier" row for the past when collapsed, then the events
        data class Row(val event: DigestEvent?, val note: String?)
        val rows = mutableListOf<Row>()
        if (plan.collapsePast) {
            if (past.isNotEmpty()) rows += Row(null, if (past.size == 1) "1 earlier event" else "${past.size} earlier events")
        } else {
            past.forEach { rows += Row(it, null) }
        }
        coming.forEach { rows += Row(it, null) }
        val limit = plan.eventRows
        val shown = if (limit != null && limit < rows.size) rows.take(limit - 1) + Row(null, "+${rows.size - limit + 1} more") else rows
        if (shown.isEmpty()) return

        val timeTexts = shown.mapNotNull { it.event?.let { e -> formatTime(e.startMs, input) } }
        val timeW = (timeTexts.maxOfOrNull { timePaint.measureText(it) } ?: 0f)
        val lineX = left + timeW + spec.mm(3.5f)
        val titleX = lineX + spec.mm(3.5f)
        val dotR = spec.mm(1.05f)

        // The timeline: a hairline through the row centres
        val firstCentre = y + rowHeight / 2f
        var lastCentre = firstCentre
        var rowY = y
        val centres = mutableListOf<Float>()
        for (row in shown) {
            val second = row.event?.let { if (plan.showLocations) secondLineOf(it, input) else null }
            centres += rowY + rowHeight / 2f
            lastCentre = rowY + rowHeight / 2f
            rowY += rowHeight + if (second != null) secondLine else 0f
        }
        if (shown.size > 1) canvas.drawLine(lineX, firstCentre, lineX, lastCentre, hair)

        rowY = y
        shown.forEachIndexed { i, row ->
            val centre = centres[i]
            val baseline = rowY + baselineIn(rowHeight, bodyPaint)
            val event = row.event
            if (event == null) {
                // "N earlier" or "+N more": a hollow dot and a quiet note
                canvas.drawCircle(lineX, centre, dotR * 0.8f, whiteFill)
                canvas.drawCircle(lineX, centre, dotR * 0.8f, hair)
                canvas.drawText(row.note ?: "", titleX, rowY + baselineIn(rowHeight, secondaryItalicPaint), secondaryItalicPaint)
                rowY += rowHeight
                return@forEachIndexed
            }
            val isPast = (event.endMs ?: event.startMs) <= input.nowMs
            val isNow = !isPast && event.startMs <= input.nowMs
            val time = formatTime(event.startMs, input)
            canvas.drawText(time, lineX - spec.mm(3.5f) - timePaint.measureText(time), baseline, timePaint)

            canvas.drawCircle(lineX, centre, if (isNow) dotR * 1.45f else dotR, whiteFill)
            if (isPast) canvas.drawCircle(lineX, centre, dotR, hair)
            else canvas.drawCircle(lineX, centre, if (isNow) dotR * 1.45f else dotR, fill)

            val titlePaint = if (isNow) bodyStrongPaint else bodyPaint
            val badgeW = if (event.booxNoteTitles.isNotEmpty()) badgeSize() + spec.mm(2.5f) else 0f
            val title = ellipsize(event.title, titlePaint, right - titleX - badgeW)
            canvas.drawText(title, titleX, baseline, titlePaint)
            if (badgeW > 0f) {
                drawBadge(canvas, "B", titleX + titlePaint.measureText(title) + spec.mm(2.5f) + badgeSize() / 2f, centre)
            }
            val second = if (plan.showLocations) secondLineOf(event, input) else null
            if (second != null) {
                val secondBaseline = rowY + rowHeight + baselineIn(secondLine, secondaryPaint) - secondLine * 0.35f
                canvas.drawText(ellipsize(second, secondaryPaint, right - titleX), titleX, secondBaseline, secondaryPaint)
                rowY += secondLine
            }
            rowY += rowHeight
        }
    }

    /** All-day events as outlined pills on one line, then "+N". */
    private fun drawPills(canvas: Canvas, titles: List<String>, left: Float, top: Float, right: Float) {
        val padX = spec.mm(2.2f)
        val pillH = lineHeight(secondaryPaint) * 1.55f
        val pillTop = top + (metrics.allDayRow - pillH) / 2f - gap * 0.2f
        val baseline = pillTop + baselineIn(pillH, secondaryStrongPaint)
        var x = left
        val maxPill = (right - left) * 0.6f
        titles.forEachIndexed { i, raw ->
            val remaining = titles.size - i
            val moreW = secondaryPaint.measureText("+$remaining") + spec.mm(2f)
            val text = ellipsize(raw, secondaryStrongPaint, maxPill - 2 * padX)
            val pillW = secondaryStrongPaint.measureText(text) + 2 * padX
            val roomNeeded = if (remaining > 1) pillW + moreW else pillW
            if (x + roomNeeded > right && i > 0) {
                canvas.drawText("+$remaining", x, baseline, secondaryPaint)
                return
            }
            val rect = RectF(x, pillTop, min(x + pillW, right), pillTop + pillH)
            canvas.drawRoundRect(rect, pillH / 2f, pillH / 2f, hair)
            canvas.drawText(text, x + padX, baseline, secondaryStrongPaint)
            x = rect.right + spec.mm(2f)
        }
    }

    // ── Tasks ─────────────────────────────────────────────────────────────────────────────────
    private fun drawTasks(canvas: Canvas, input: Input, plan: SleepAgendaLayout.Plan, left: Float, top: Float, right: Float) {
        val tasks = input.digest.tasks
        val open = tasks.count { !it.completed && !it.isSubtask }
        var y = sectionHeader(canvas, "Tasks", if (tasks.isNotEmpty()) "$open open" else null, left, top, right)
        if (tasks.isEmpty()) {
            canvas.drawText("No tasks for today.", left, y + baselineIn(rowHeight, secondaryItalicPaint), secondaryItalicPaint)
            return
        }

        // Rows in page order; done tasks moved to one "N done" line when collapsed
        data class Row(val task: DigestTask?, val note: String?, val doneMark: Boolean = false)
        val rows = mutableListOf<Row>()
        var hiddenDone = 0
        for (task in tasks) {
            if (task.isSubtask && plan.hideSubtasks) continue
            if (task.completed && plan.collapseDone) {
                if (!task.isSubtask) hiddenDone++
                continue
            }
            rows += Row(task, null)
        }
        if (hiddenDone > 0) rows += Row(null, "$hiddenDone done", doneMark = true)
        val limit = plan.taskRows
        val shown = if (limit != null && limit < rows.size) rows.take(limit - 1) + Row(null, "+${rows.size - limit + 1} more") else rows

        val box = spec.px(spec.bodyPt) * 0.78f
        for (row in shown) {
            val centre = y + rowHeight / 2f
            val baseline = y + baselineIn(rowHeight, bodyPaint)
            val task = row.task
            if (task == null) {
                if (row.doneMark) drawCheckbox(canvas, left, centre, box * 0.8f, done = true)
                val noteX = if (row.doneMark) left + box + spec.mm(3f) else left
                canvas.drawText(row.note ?: "", noteX, y + baselineIn(rowHeight, secondaryItalicPaint), secondaryItalicPaint)
                y += rowHeight
                continue
            }
            val indent = if (task.isSubtask) spec.mm(6f) else 0f
            val size = if (task.isSubtask) box * 0.82f else box
            drawCheckbox(canvas, left + indent, centre, size, task.completed)
            var x = left + indent + size + spec.mm(3f)
            if (task.priority.isNotEmpty()) {
                drawBadge(canvas, task.priority, x + badgeSize() / 2f, centre)
                x += badgeSize() + spec.mm(2f)
            }
            val paint = when {
                task.title == null -> bodyItalicPaint
                else -> bodyPaint
            }
            val text = ellipsize(task.title ?: "Handwritten task", paint, right - x)
            canvas.drawText(text, x, baseline, paint)
            if (task.completed) {
                val strikeY = baseline - capHeight(paint) * 0.42f
                canvas.drawRect(x, strikeY - spec.hairline / 2f, x + paint.measureText(text), strikeY + spec.hairline / 2f, heavy)
            }
            y += rowHeight
        }
    }

    private fun drawCheckbox(canvas: Canvas, left: Float, centre: Float, size: Float, done: Boolean) {
        val rect = RectF(left, centre - size / 2f, left + size, centre + size / 2f)
        val radius = size * 0.18f
        if (!done) {
            val inset = spec.hairline / 2f
            canvas.drawRoundRect(RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset), radius, radius,
                Paint(hair).apply { strokeWidth = spec.hairline * 1.25f })
            return
        }
        canvas.drawRoundRect(rect, radius, radius, fill)
        val tick = Path().apply {
            moveTo(rect.left + size * 0.22f, rect.top + size * 0.52f)
            lineTo(rect.left + size * 0.42f, rect.top + size * 0.72f)
            lineTo(rect.left + size * 0.79f, rect.top + size * 0.30f)
        }
        canvas.drawPath(tick, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; this.style = Paint.Style.STROKE; strokeWidth = size * 0.14f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        })
    }

    private fun badgeSize() = lineHeight(badgePaint) * 1.05f

    /** A small inverted square with one letter: priority (A/B/C) or "B" for a linked BOOX Note. */
    private fun drawBadge(canvas: Canvas, letter: String, centreX: Float, centreY: Float) {
        val s = badgeSize()
        val rect = RectF(centreX - s / 2f, centreY - s / 2f, centreX + s / 2f, centreY + s / 2f)
        canvas.drawRoundRect(rect, s * 0.2f, s * 0.2f, fill)
        canvas.drawText(letter, centreX, centreY + capHeight(badgePaint) / 2f, badgePaint)
    }

    // ── Memo panel ────────────────────────────────────────────────────────────────────────────
    private fun drawMemo(canvas: Canvas, memo: Bitmap, left: Float, top: Float, right: Float, bottom: Float) {
        val y = sectionHeader(canvas, "Today's memo", null, left, top, right)
        val frame = RectF(left, y, right, bottom)
        val radius = spec.mm(2f)
        val pad = spec.mm(2.5f)
        val inner = RectF(frame.left + pad, frame.top + pad, frame.right - pad, frame.bottom - pad)
        // Where the writing is, from its first line: scaled to the panel's width (at most 1.3x),
        // cut at the panel's bottom. The page's empty margins are left out.
        val ink = inkBounds(memo) ?: Rect(0, 0, memo.width, memo.height)
        val scale = min(inner.width() / ink.width(), 1.3f)
        val srcH = min(ink.height().toFloat(), inner.height() / scale)
        val drawW = ink.width() * scale
        val dst = RectF(inner.left + (inner.width() - drawW) / 2f, inner.top, inner.left + (inner.width() + drawW) / 2f, inner.top + srcH * scale)
        canvas.save()
        canvas.clipRect(inner)
        canvas.drawBitmap(memo, Rect(ink.left, ink.top, ink.right, ink.top + srcH.roundToInt()), dst, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        canvas.drawRoundRect(frame, radius, radius, hair)
    }

    /** The box around the dark pixels of [page] with a small margin, or null for a blank page. */
    private fun inkBounds(page: Bitmap): Rect? {
        val step = 3
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = -1; var maxY = -1
        val row = IntArray(page.width)
        for (y in 0 until page.height step step) {
            page.getPixels(row, 0, page.width, 0, y, page.width, 1)
            for (x in 0 until page.width step step) {
                val c = row[x]
                if (Color.alpha(c) > 128 && (Color.red(c) + Color.green(c) + Color.blue(c)) < 3 * 128) {
                    if (x < minX) minX = x; if (x > maxX) maxX = x
                    if (y < minY) minY = y; if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return null
        val pad = page.width / 40
        return Rect(max(0, minX - pad), max(0, minY - pad), min(page.width, maxX + pad), min(page.height, maxY + pad))
    }

    // ── Month calendar (when there is room) ───────────────────────────────────────────────────
    /** This month as a small grid, today in an inverted circle; weeks start on the locale's first day. */
    private fun drawMonth(canvas: Canvas, input: Input, left: Float, top: Float, right: Float) {
        val date = input.digest.date
        val label = date.month.getDisplayName(TextStyle.FULL, input.locale) + " " + date.year
        val gridW = min(right - left, spec.mm(if (spec.sizeClass == SleepSizeClass.COMPACT) 80f else 110f))
        val y = sectionHeader(canvas, label, null, left, top, left + gridW)
        val firstDay = java.time.temporal.WeekFields.of(input.locale).firstDayOfWeek
        val cellW = gridW / 7f
        val cellH = monthCell()
        val centre = { col: Int -> left + cellW * (col + 0.5f) }
        val numberPaint = TextPaint(secondaryPaint).apply { textAlign = Paint.Align.CENTER }
        val headPaint = TextPaint(secondaryStrongPaint).apply { textAlign = Paint.Align.CENTER }
        for (col in 0 until 7) {
            val day = firstDay.plus(col.toLong())
            canvas.drawText(day.getDisplayName(TextStyle.NARROW, input.locale), centre(col), y + baselineIn(cellH, headPaint), headPaint)
        }
        val first = date.withDayOfMonth(1)
        val offset = (first.dayOfWeek.value - firstDay.value + 7) % 7
        for (dayOfMonth in 1..date.lengthOfMonth()) {
            val index = offset + dayOfMonth - 1
            val col = index % 7
            val rowTop = y + cellH * (1 + index / 7)
            val cx = centre(col)
            val cy = rowTop + cellH / 2f
            if (dayOfMonth == date.dayOfMonth) {
                canvas.drawCircle(cx, cy, cellH * 0.42f, fill)
                val todayPaint = TextPaint(secondaryStrongPaint).apply { textAlign = Paint.Align.CENTER; color = Color.WHITE }
                canvas.drawText(dayOfMonth.toString(), cx, rowTop + baselineIn(cellH, todayPaint), todayPaint)
            } else {
                canvas.drawText(dayOfMonth.toString(), cx, rowTop + baselineIn(cellH, numberPaint), numberPaint)
            }
        }
    }

    // ── Next days (large screens) ─────────────────────────────────────────────────────────────
    private fun drawNextDays(canvas: Canvas, input: Input, left: Float, top: Float, right: Float) {
        val y = sectionHeader(canvas, "Next days", null, left, top, right)
        val days = input.digest.nextDays.take(3)
        if (days.isEmpty()) return
        val colGap = spec.mm(5f)
        val colW = (right - left - colGap * (days.size - 1)) / days.size
        days.forEachIndexed { i, (date, events) ->
            val x = left + i * (colW + colGap)
            var baseline = y + capHeight(bodyStrongPaint) + gap * 0.3f
            val head = date.dayOfWeek.getDisplayName(TextStyle.SHORT, input.locale) + " " + date.dayOfMonth
            canvas.drawText(head, x, baseline, bodyStrongPaint)
            val lineH = lineHeight(secondaryPaint) * 1.35f
            baseline += lineHeight(bodyStrongPaint) * 0.45f
            if (events.isEmpty()) {
                canvas.drawText("Nothing planned", x, baseline + lineH, secondaryItalicPaint)
            } else {
                val shown = if (events.size > 3) events.take(2) else events
                shown.forEach { e ->
                    baseline += lineH
                    val time = if (e.allDay) "All day" else formatTime(e.startMs, input)
                    canvas.drawText(time, x, baseline, secondaryStrongPaint)
                    val tx = x + secondaryStrongPaint.measureText(time) + spec.mm(2f)
                    canvas.drawText(ellipsize(e.title, secondaryPaint, x + colW - tx), tx, baseline, secondaryPaint)
                }
                if (events.size > 3) canvas.drawText("+${events.size - 2} more", x, baseline + lineH, secondaryItalicPaint)
            }
            if (i > 0) canvas.drawLine(x - colGap / 2f, y, x - colGap / 2f, y + metrics.nextDaysPanel - metrics.sectionHeader, hair)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────
    /** "until 10:00 · Room 4": the end time and the place, or null when there is neither. */
    private fun secondLineOf(event: DigestEvent, input: Input): String? {
        val parts = mutableListOf<String>()
        event.endMs?.let { if (!event.allDay && it > event.startMs) parts += "until " + formatTime(it, input) }
        event.location?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += it }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
    }

    private fun timeFormatter(input: Input): DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (input.use24Hour) "H:mm" else "h:mm a", input.locale)

    private fun formatTime(ms: Long, input: Input): String =
        Instant.ofEpochMilli(ms).atZone(input.zone).format(timeFormatter(input))

    private fun baselineIn(boxHeight: Float, paint: Paint): Float {
        val fm = paint.fontMetrics
        return boxHeight / 2f - (fm.ascent + fm.descent) / 2f
    }

    private fun ellipsize(text: String, paint: TextPaint, width: Float): String =
        if (width <= 0f) "" else TextUtils.ellipsize(text.replace('\n', ' '), paint, width, TextUtils.TruncateAt.END).toString()

    private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

    /** Pure black and white: anti-aliased edges are cut at mid-grey, so nothing dithers. */
    private fun toBlackAndWhite(bitmap: Bitmap) {
        val w = bitmap.width
        val row = IntArray(w)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val c = row[x]
                val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                row[x] = if (lum < 150) Color.BLACK else Color.WHITE
            }
            bitmap.setPixels(row, 0, w, 0, y, w, 1)
        }
    }

    companion object {
        /** The day after [date] and the next ones, for [DayDigest.nextDays]. */
        fun nextDates(date: LocalDate, count: Int = 3): List<LocalDate> = (1..count).map { date.plusDays(it.toLong()) }
    }
}
