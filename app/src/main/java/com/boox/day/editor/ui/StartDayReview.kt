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
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.boox.day.ui.components.DialogCloseButton
import com.boox.day.ui.dialogs.ActionButton
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
 * shown each day, and from the task actions menu. Lists unfinished tasks from earlier days, each
 * in the user's own handwriting with Move to Today · Tomorrow · Other day… · Done · Delete, and tasks
 * due today that were written on other days (tap → go to the task). Paginated, no scrolling.
 * Redesigned 26 Oct 2026 (feedback plan 4.12), see StartDayReviewPopup.
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

/*
 * Oct 2026 (plan 4.12; the owner found it confusing and plain): it says why it opened and what it
 * asks. Each task has one black main action (Move to Today), Tomorrow and Other day… beside it, and
 * Done and Delete set apart with icons. A handled task keeps its place as one line ("Moved to today"),
 * so the page doesn't jump on e-ink. Move all to today handles the rest at once; Decide Later says
 * the tasks stay on their day. Black on white only: grey labels looked disabled.
 */
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
    // The tasks as the review opened: MainScreen takes a handled task out of [data] at once (or after
    // the day picker / delete confirmation), and the card then turns into its one-line outcome
    val shownUnfinished = remember { data.unfinished }
    val shownDue = remember { data.dueToday }
    val outcomes = remember { mutableStateMapOf<String, String>() }
    val openIds = remember(data) { (data.unfinished + data.dueToday).map { it.id }.toSet() }
    val pages = remember {
        shownUnfinished.chunked(if (compact) 3 else 4).map { ReviewPage.Unfinished(it) } +
            shownDue.chunked(if (compact) 5 else 7).map { ReviewPage.Due(it) }
    }
    var pageIndex by remember { mutableStateOf(0) }
    val shownIndex = pageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val today = remember(data.today) { LocalDate.parse(data.today) }
    val firstDuePage = pages.indexOfFirst { it is ReviewPage.Due }

    fun record(task: Task, outcome: String) { outcomes[task.id] = outcome }

    // The dialog never gets shorter while open (seen on the Go 10.3): a handled card turns into one
    // line, and a centered dialog that shrank moved its header down the screen
    var tallestPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .widthIn(max = 640.dp)
                .heightIn(min = with(density) { tallestPx.toDp() }, max = EInkTheme.PopupOverlay.maxHeight)
                .onSizeChanged { if (it.height > tallestPx) tallestPx = it.height }
                .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                .border(1.5.dp, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                .noRippleClickable { }  // taps inside don't dismiss
                .padding(EInkTheme.ModernDialog.padding)
        ) {
            // Header: what this is, today's date, and the X
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.WbSunny,
                    contentDescription = null,
                    tint = EInkTheme.Colors.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Start your day",
                    fontSize = EInkTheme.ModernDialog.titleFontSize,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = today.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault())),
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.primary
                )
                DialogCloseButton(onClose = onDismiss)
            }
            Text(
                text = when {
                    data.isEmpty && outcomes.isEmpty() -> "Nothing left over from earlier days."
                    shownUnfinished.isNotEmpty() ->
                        "These tasks from earlier days aren't done yet. Choose what to do with each, or leave them where they are."
                    else -> "These tasks are due today but were written on other days."
                },
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.primary,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )

            when (val page = pages.getOrNull(shownIndex)) {
                null -> Unit
                is ReviewPage.Unfinished -> {
                    val stillOpen = data.unfinished
                    if (stillOpen.size >= 2) {
                        OutlinedChoice(
                            text = "Move all to today (${stillOpen.size})",
                            onClick = {
                                stillOpen.forEach { task ->
                                    record(task, "Moved to today")
                                    onMove(task, today.toString())
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        )
                    }
                    page.tasks.forEach { task ->
                        if (task.id in openIds) {
                            UnfinishedCard(
                                task = task,
                                inkFor = inkFor,
                                onToday = { record(task, "Moved to today"); onMove(task, today.toString()) },
                                onTomorrow = { record(task, "Moved to tomorrow"); onMove(task, today.plusDays(1).toString()) },
                                onOtherDay = { record(task, "Moved to another day"); onPickDay(task) },
                                onDone = { record(task, "Marked done"); onDone(task) },
                                onDelete = { record(task, "Deleted"); onDelete(task) }
                            )
                        } else {
                            HandledLine(task = task, outcome = outcomes[task.id] ?: "Done", inkFor = inkFor)
                        }
                    }
                    if (firstDuePage > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = EInkTheme.Global.touchTarget)
                                .noRippleClickable { pageIndex = firstDuePage },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Also due today: ${shownDue.size} written on other days",
                                fontSize = EInkTheme.Typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = EInkTheme.Colors.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Show them",
                                tint = EInkTheme.Colors.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
                is ReviewPage.Due -> {
                    SectionTitle("Due today, written on other days (${shownDue.size})")
                    page.tasks.forEach { task -> DueItem(task = task, onOpenTask = onOpenTask) }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(1.dp)
                    .background(EInkTheme.Colors.primary)
            )
            Spacer(Modifier.height(10.dp))

            // Footer: Decide Later (the escape, left) and what it means; page arrows on the right.
            // Once everything is handled: "All set for today." and a black Close.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (data.isEmpty) {
                    Text(
                        text = "All set for today.",
                        fontSize = EInkTheme.Typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.primary,
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(text = "Close", onClick = onDismiss)
                } else {
                    OutlinedChoice(text = "Decide Later", onClick = onDismiss)
                    Text(
                        text = "They stay on their day. Open this again from the task actions menu.",
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.primary,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                    )
                    if (pages.size > 1) {
                        PagerArrow(left = true, enabled = shownIndex > 0) { pageIndex = shownIndex - 1 }
                        Text(
                            text = "${shownIndex + 1} of ${pages.size}",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.primary
                        )
                        PagerArrow(left = false, enabled = shownIndex < pages.size - 1) { pageIndex = shownIndex + 1 }
                    }
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

/** One unfinished task: what was written, where it's from, and what to do with it. */
@Composable
private fun UnfinishedCard(
    task: Task,
    inkFor: suspend (Task) -> Bitmap?,
    onToday: () -> Unit,
    onTomorrow: () -> Unit,
    onOtherDay: () -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .border(1.dp, EInkTheme.Colors.primary, shape)
            .padding(12.dp)
    ) {
        TaskContent(task = task, inkFor = inkFor)
        Text(
            text = buildString {
                append("From ${dayLabel(task.dayDate)}")
                TaskDueDates.toLocalDate(task.dueDay)?.let { append(" · due ${it.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))}") }
                when (task.migrationCount) {
                    0 -> Unit
                    1 -> append(" · put off once")
                    else -> append(" · put off ${task.migrationCount} times")
                }
            },
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.primary,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(text = "Move to Today", onClick = onToday, modifier = Modifier.weight(1.4f))
            OutlinedChoice(text = "Tomorrow", onClick = onTomorrow, modifier = Modifier.weight(1f))
            OutlinedChoice(text = "Other day…", onClick = onOtherDay, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .width(1.dp)
                    .height(EInkTheme.Button.height)
                    .background(EInkTheme.Colors.primary)
            )
            IconChoice(icon = Icons.Default.Check, label = "Done", onClick = onDone)
            IconChoice(icon = Icons.Default.Delete, label = "Delete", onClick = onDelete)
        }
    }
}

/** A handled task keeps its place as one line, so the page doesn't move. */
@Composable
private fun HandledLine(task: Task, outcome: String, inkFor: suspend (Task) -> Bitmap?) {
    val text = task.typedText?.takeIf { it.isNotBlank() } ?: task.convertedText?.takeIf { it.isNotBlank() }
        ?: task.recognizedText?.takeIf { it.isNotBlank() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .border(1.dp, EInkTheme.Colors.primary, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = EInkTheme.Colors.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (text != null) {
                Text(
                    text = text,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                val ink by produceState<Bitmap?>(initialValue = null, task.id) { value = inkFor(task) }
                ink?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Handwritten task",
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        modifier = Modifier.fillMaxWidth().height(28.dp)
                    )
                }
            }
        }
        Text(
            text = outcome,
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.Colors.primary
        )
    }
}

/** A black outlined button with black text (the shared secondary button's grey text looks disabled). */
@Composable
private fun OutlinedChoice(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(EInkTheme.Button.height)
            .border(1.dp, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius))
            .noRippleClickable { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = EInkTheme.Typography.button,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.Colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Done and Delete: an icon over a short label, set apart from the moves. */
@Composable
private fun IconChoice(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .height(EInkTheme.Button.height)
            .widthIn(min = 56.dp)
            .border(1.dp, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius))
            .noRippleClickable { onClick() }
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = EInkTheme.Colors.primary, modifier = Modifier.size(20.dp))
        Text(text = label, fontSize = EInkTheme.Typography.labelMedium, color = EInkTheme.Colors.primary)
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
            modifier = Modifier.fillMaxWidth().height(56.dp)
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
                text = "Written ${dayLabel(task.dayDate)}",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.primary
            )
        }
        Text(
            text = "Go to task",
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.primary
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
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
    runCatching { LocalDate.parse(dayDate).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())) }
        .getOrDefault(dayDate)
