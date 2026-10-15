package com.boox.day.editor.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.PageDataManager
import com.boox.day.data.db.StrokeRepository
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskDueDates
import com.boox.day.data.db.TaskRepository
import com.boox.day.editor.drawing.drawStroke
import com.boox.day.editor.utils.calculateRowBounds
import com.boox.day.editor.utils.extractStrokesForRow
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.theme.LayoutProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Start-the-day review (plan T4 §13.6B, Oct 2026): opens once, the first time today's Tasks are
 * shown each day, and from the task actions menu. Lists unfinished tasks from earlier days — each
 * in the user's own handwriting, with Today · Tomorrow · Pick day · Done · Delete — and tasks due
 * today that were written on other days (tap → go to the task). Paginated, no scrolling.
 */

/** What the review shows. */
data class StartDayReviewData(
    val today: String,
    val unfinished: List<Task>,
    val dueToday: List<Task>
) {
    val isEmpty: Boolean get() = unfinished.isEmpty() && dueToday.isEmpty()
}

object StartDayReviewLoader {
    /** How far back the review looks: since the last review, but at least this many days... */
    private const val MIN_DAYS_BACK = 7L

    /** ...and at most this many. */
    private const val MAX_DAYS_BACK = 30L

    /**
     * Unfinished top-level tasks with real content (text or ink) from earlier days, and tasks due
     * today written on other days. Recurring occurrences are left out: today has its own.
     */
    suspend fun load(
        repository: TaskRepository,
        today: LocalDate,
        lastReviewDay: String?,
        canvasHeight: Float
    ): StartDayReviewData = withContext(Dispatchers.IO) {
        val lastReview = lastReviewDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        var from = today.minusDays(MIN_DAYS_BACK)
        if (lastReview != null && lastReview < from) from = lastReview
        if (from < today.minusDays(MAX_DAYS_BACK)) from = today.minusDays(MAX_DAYS_BACK)

        val unfinished = repository.getTasksForDateRange(from.toString(), today.minusDays(1).toString())
            .filter { !it.isCompleted && it.parentTaskId == null && it.templateId == null }
            .filter { repository.taskHasRealContent(it, canvasHeight) }
            .sortedWith(compareBy<Task>({ it.dayDate }, { it.taskPageIndex }, { it.rowIndex }))
        val todayKey = today.toString()
        val dueToday = repository.getTasksDueOnDateFlow(todayKey).first()
            .filter { it.dayDate != todayKey && !it.isCompleted }
        StartDayReviewData(today = todayKey, unfinished = unfinished, dueToday = dueToday)
    }

    /** The task's row as it was written (null when the row has no ink). Call off the main thread. */
    fun renderRowInk(context: Context, task: Task, canvasHeight: Float): Bitmap? {
        if (canvasHeight <= 0f || task.rowIndex !in 0 until 10) return null
        val pageStrokes = PageDataManager.getStrokesInRectangle(Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE), task.pageId)
            ?: StrokeRepository(context).getAllForPage(task.pageId)
        val bounds = calculateRowBounds(task.rowIndex, canvasHeight, 10)
        val row = extractStrokesForRow(pageStrokes, bounds, canvasHeight)
        if (row.isEmpty()) return null
        // Crop to the ink itself (not the row), so strokes that reach over a row line aren't cut
        val pad = 8f
        val left = row.minOf { it.left } - pad
        val right = row.maxOf { it.right } + pad
        val top = row.minOf { it.top } - pad
        val width = (right - left).toInt().coerceIn(1, 4000)
        val height = (row.maxOf { it.bottom } + pad - top).toInt().coerceIn(1, 2000)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        row.forEach { drawStroke(canvas, it, Offset(-left, -top)) }
        return bitmap
    }
}

private sealed class ReviewPage {
    data class Unfinished(val tasks: List<Task>) : ReviewPage()
    data class Due(val tasks: List<Task>) : ReviewPage()
}

@Composable
fun StartDayReviewPopup(
    data: StartDayReviewData,
    inkFor: suspend (Task) -> Bitmap?,
    onMove: (Task, String) -> Unit,
    onPickDay: (Task) -> Unit,
    onDone: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    onOpenTask: (Task) -> Unit,
    onDismiss: () -> Unit
) {
    val compact = LayoutProfiles.isNarrowScreen
    val pages = remember(data) {
        data.unfinished.chunked(if (compact) 3 else 4).map { ReviewPage.Unfinished(it) } +
            data.dueToday.chunked(if (compact) 5 else 7).map { ReviewPage.Due(it) }
    }
    var pageIndex by remember { mutableStateOf(0) }
    val shownIndex = pageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val today = remember(data.today) { LocalDate.parse(data.today) }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .widthIn(max = 640.dp)
                .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                .border(
                    EInkTheme.ModernDialog.borderWidth,
                    EInkTheme.ModernDialog.borderColor,
                    RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                )
                .noRippleClickable { }  // taps inside don't dismiss
                .padding(EInkTheme.ModernDialog.padding)
        ) {
            Text(
                text = "Start the day",
                fontSize = EInkTheme.ModernDialog.titleFontSize,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.ModernDialog.titleColor
            )
            Text(
                text = today.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())),
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray
            )
            Spacer(Modifier.height(10.dp))

            when (val page = pages.getOrNull(shownIndex)) {
                null -> Text(
                    text = "All caught up — nothing left over from earlier days.",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
                is ReviewPage.Unfinished -> {
                    SectionTitle("Unfinished from earlier days (${data.unfinished.size})")
                    page.tasks.forEach { task ->
                        UnfinishedItem(
                            task = task,
                            today = today,
                            inkFor = inkFor,
                            onMove = onMove,
                            onPickDay = onPickDay,
                            onDone = onDone,
                            onDelete = onDelete
                        )
                    }
                }
                is ReviewPage.Due -> {
                    SectionTitle("Due today, written on other days (${data.dueToday.size})")
                    page.tasks.forEach { task -> DueItem(task = task, onOpenTask = onOpenTask) }
                }
            }

            Divider(color = EInkTheme.Colors.borderStrong, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

            // [Close] on the left (DIALOG_RULEBOOK); page arrows on the right
            Row(verticalAlignment = Alignment.CenterVertically) {
                EinkSecondaryButton(text = "Close", onClick = onDismiss, width = 120.dp)
                Spacer(Modifier.weight(1f))
                if (pages.size > 1) {
                    PagerArrow(left = true, enabled = shownIndex > 0) { pageIndex = shownIndex - 1 }
                    Text(
                        text = "${shownIndex + 1}/${pages.size}",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.primary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    PagerArrow(left = false, enabled = shownIndex < pages.size - 1) { pageIndex = shownIndex + 1 }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = EInkTheme.Colors.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun UnfinishedItem(
    task: Task,
    today: LocalDate,
    inkFor: suspend (Task) -> Bitmap?,
    onMove: (Task, String) -> Unit,
    onPickDay: (Task) -> Unit,
    onDone: (Task) -> Unit,
    onDelete: (Task) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = buildString {
                append(dayLabel(task.dayDate))
                TaskDueDates.toLocalDate(task.dueDay)?.let { append(" · due ${it.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))}") }
                if (task.migrationCount > 0) append(" · moved ${task.migrationCount}×")
            },
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.gray
        )
        TaskContent(task = task, inkFor = inkFor)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val action = Modifier.weight(1f)
            EinkSecondaryButton(text = "Today", onClick = { onMove(task, today.toString()) }, modifier = action, width = 0.dp)
            EinkSecondaryButton(text = "Tomorrow", onClick = { onMove(task, today.plusDays(1).toString()) }, modifier = action, width = 0.dp)
            EinkSecondaryButton(text = "Pick day", onClick = { onPickDay(task) }, modifier = action, width = 0.dp)
            EinkSecondaryButton(text = "Done", onClick = { onDone(task) }, modifier = action, width = 0.dp)
            EinkSecondaryButton(text = "Delete", onClick = { onDelete(task) }, modifier = action, width = 0.dp)
        }
        Divider(color = EInkTheme.Colors.borderStrong, thickness = 0.5.dp, modifier = Modifier.padding(top = 10.dp))
    }
}

/** The task as written: typed/converted text, else its ink (row image), else recognized text. */
@Composable
private fun TaskContent(task: Task, inkFor: suspend (Task) -> Bitmap?) {
    val text = task.typedText?.takeIf { it.isNotBlank() } ?: task.convertedText?.takeIf { it.isNotBlank() }
    if (text != null) {
        Text(
            text = text,
            fontSize = EInkTheme.Typography.bodyLarge,
            color = EInkTheme.Colors.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        return
    }
    val ink by produceState<Bitmap?>(initialValue = null, task.id) { value = inkFor(task) }
    val fallback = task.recognizedText?.takeIf { it.isNotBlank() }
    val bitmap = ink
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = fallback ?: "Handwritten task",
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        )
    } else {
        Text(
            text = fallback ?: "Handwritten task",
            fontSize = EInkTheme.Typography.bodyLarge,
            fontStyle = if (fallback == null) FontStyle.Italic else FontStyle.Normal,
            color = EInkTheme.Colors.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DueItem(task: Task, onOpenTask: (Task) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .noRippleClickable { onOpenTask(task) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.typedText?.takeIf { it.isNotBlank() }
                    ?: task.convertedText?.takeIf { it.isNotBlank() }
                    ?: task.recognizedText?.takeIf { it.isNotBlank() }
                    ?: "Handwritten task",
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "written ${dayLabel(task.dayDate)}",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Go to task",
            tint = EInkTheme.Colors.primary,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun PagerArrow(left: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(EInkTheme.Global.touchTarget)
            .noRippleClickable { if (enabled) onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (left) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (left) "Previous page" else "Next page",
            tint = if (enabled) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
            modifier = Modifier.size(32.dp)
        )
    }
}

private fun dayLabel(dayDate: String): String =
    runCatching { LocalDate.parse(dayDate).format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())) }
        .getOrDefault(dayDate)
