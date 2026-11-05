package com.boox.day.week

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskRepository
import com.boox.day.editor.TasksCanvasRefresh
import com.boox.day.editor.ui.StartDayReviewLoader
import com.boox.day.editor.ui.TaskDatePickerDialog
import com.boox.day.tasks.TaskPagination
import com.boox.day.ui.FullScreenState
import com.boox.day.ui.MainScreenRequest
import com.boox.day.ui.MainScreenRequests
import com.boox.day.ui.ScreenCentre
import com.boox.day.ui.ScreenTopLeft
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.theme.InkaTheme
import com.boox.day.viewmodel.EventsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The Week page, opened from the day header. MainScreen knows it only through AppOverlays. */
object WeekUi {
    var selected by mutableStateOf<LocalDate?>(null)
        private set

    val isOpen: Boolean get() = selected != null

    fun open(date: String) {
        selected = LocalDate.parse(date)
    }

    fun close() {
        selected = null
    }
}

private const val ROWS_PER_SHEET = 6
private const val OVERDUE = 7
/** Handwriting pictures stop here, and the rest say "Handwritten task": the page opens on time. */
private const val INK_BUDGET_MS = 350L

@Composable
fun WeekHost() {
    val selected = WeekUi.selected ?: return
    InkaTheme { WeekOverlay(selected) }
}

@Composable
private fun WeekOverlay(selected: LocalDate) {
    val context = LocalContext.current
    val eventsViewModel: EventsViewModel = viewModel()
    val settings = GlobalAppSettings.current
    val firstDay = settings.firstDayOfWeek
    val today = remember { LocalDate.now() }
    val repository = remember { TaskRepository(context.applicationContext) }
    val loader = remember { WeekLoader(context, events = eventsViewModel::shownEventsForDay) }
    val ink = remember { mutableStateMapOf<String, ImageBitmap>() }

    var start by remember { mutableStateOf(firstDay.weekStart(selected)) }
    var reload by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf<WeekData?>(null) }
    var openCell by remember { mutableStateOf<Int?>(null) }
    val spec = remember { mutableStateOf<WeekLayoutSpec?>(null) }

    // Nothing below the page refreshes while it's open; the Tasks page reads again once, at the end
    DisposableEffect(Unit) {
        TasksCanvasRefresh.hold()
        onDispose {
            TasksCanvasRefresh.release()
            MainScreenRequests.request(MainScreenRequest.ReloadTasks)
        }
    }

    // Load first, then show: the page changes once
    LaunchedEffect(start, reload) {
        val began = SystemClock.elapsedRealtime()
        val week = loader.load(start, today)
        val lines = spec.value?.linesPerDay ?: 8
        withContext(Dispatchers.IO) {
            val deadline = SystemClock.elapsedRealtime() + INK_BUDGET_MS
            (week.days.flatMap { day -> (day.tasks).take(lines) } + week.overdue.take(lines))
                .mapNotNull { item -> item.task?.takeIf { item.text == null && it.id !in ink } }
                .forEach { task ->
                    if (SystemClock.elapsedRealtime() < deadline) {
                        runCatching { StartDayReviewLoader.renderRowInk(context, task, GlobalAppSettings.tasksCanvasHeight) }
                            .getOrNull()?.let { ink[task.id] = it.asImageBitmap() }
                    }
                }
        }
        data = week
        android.util.Log.d("WeekPage", "Week of $start ready in ${SystemClock.elapsedRealtime() - began} ms")
    }

    val week = data ?: return
    val dayFirst = settings.dateFormat == AppSettings.DateFormat.DMY
    Popup(
        popupPositionProvider = ScreenTopLeft,
        onDismissRequest = WeekUi::close,
        properties = PopupProperties(focusable = true, clippingEnabled = false)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val layout = WeekLayoutSpec.of(maxWidth.value.toInt(), maxHeight.value.toInt())
            spec.value = layout
            WeekPage(
                page = WeekPresenter.page(week, today, selected, firstDay.dayOfWeek, dayFirst, ink),
                spec = layout,
                use24Hour = settings.timeFormat == AppSettings.TimeFormat.TwentyFourHour,
                onPrevious = { start = start.minusDays(7) },
                onNext = { start = start.plusDays(7) },
                onThisWeek = { start = firstDay.weekStart(today) },
                onClose = WeekUi::close,
                onCell = { openCell = it }
            )
        }
    }
    // Beside the page's Popup, not in it: a Popup inside a Popup got its position counted twice
    openCell?.let { cell ->
        WeekSheet(
            week = week,
            cell = cell,
            repository = repository,
            onClose = {
                openCell = null
                reload++
            },
            onOpenDay = { date ->
                WeekUi.close()
                MainScreenRequests.request(MainScreenRequest.ShowDay(date.toString()))
            }
        )
    }
}

/** What a sheet row is: an event, or a task that can be ticked and moved (a preview can't). */
private sealed interface SheetItem {
    data class Event(val item: WeekEventItem) : SheetItem
    data class Todo(val item: WeekTaskItem, val done: Boolean) : SheetItem
}

@Composable
private fun WeekSheet(
    week: WeekData,
    cell: Int,
    repository: TaskRepository,
    onClose: () -> Unit,
    onOpenDay: (LocalDate) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val day = week.days.getOrNull(cell)
    val items: List<SheetItem> = remember(week, cell) {
        if (day == null) {
            week.overdue.map { SheetItem.Todo(it, done = false) }
        } else {
            day.events.map { SheetItem.Event(it) } +
                day.tasks.map { SheetItem.Todo(it, done = false) } +
                day.finished.map { SheetItem.Todo(it, done = true) }
        }
    }
    val pages = maxOf(1, (items.size + ROWS_PER_SHEET - 1) / ROWS_PER_SHEET)
    var page by remember { mutableIntStateOf(1) }
    val ticked = remember { mutableStateMapOf<String, Boolean>() }
    val moved = remember { mutableStateMapOf<String, String>() }
    var moving by remember { mutableStateOf<Task?>(null) }
    var pickDate by remember { mutableStateOf<Task?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val shown = items.drop((page - 1) * ROWS_PER_SHEET).take(ROWS_PER_SHEET)
    val use24 = GlobalAppSettings.current.timeFormat == AppSettings.TimeFormat.TwentyFourHour

    fun moveTo(task: Task, target: String) {
        moving = null
        pickDate = null
        if (TaskPagination.isReflowed(task.dayDate) || TaskPagination.isReflowed(target)) {
            message = "Close full screen to move this task."
            return
        }
        FullScreenState.exitThen {
            scope.launch(Dispatchers.IO) {
                val result = repository.moveTaskWithStrokes(
                    task = task,
                    targetDate = target,
                    targetPageIndex = 0,
                    canvasHeight = GlobalAppSettings.tasksCanvasHeight,
                    context = context
                )
                if (result.success) repository.recordTaskMigration(result.movedTaskId ?: task.id, task.dayDate)
                withContext(Dispatchers.Main) {
                    if (result.success) moved[task.id] = target else message = "Couldn't move the task."
                }
            }
        }
    }

    Popup(popupPositionProvider = ScreenCentre, onDismissRequest = onClose, properties = PopupProperties(focusable = true)) {
        WeekDaySheetCard(
            title = day?.date?.let { DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()).format(it) } ?: "Overdue",
            rows = shown.map { item ->
                when (item) {
                    is SheetItem.Event -> WeekSheetRow(item.item.event.title, time = eventTime(item.item, use24))
                    is SheetItem.Todo -> {
                        val task = item.item.task
                        WeekSheetRow(
                            text = item.item.text ?: WeekPresenter.HANDWRITTEN,
                            ticked = task?.id?.let { ticked[it] } ?: item.done,
                            note = when {
                                task != null && moved[task.id] != null -> "Moved"
                                item.item.due -> "Due"
                                item.item.repeat -> "Repeats"
                                day == null -> task?.dueDay?.let { shortDate(it) }
                                else -> null
                            },
                            canAct = task != null && moved[task.id] == null
                        )
                    }
                }
            },
            page = page,
            pages = pages,
            showDayButtons = day != null,
            message = message,
            onRow = { index ->
                when (val item = shown[index]) {
                    is SheetItem.Event -> MainScreenRequests.request(MainScreenRequest.ShowEvent(item.item.event))
                    is SheetItem.Todo -> item.item.task?.takeIf { moved[it.id] == null }?.let { task ->
                        val now = !(ticked[task.id] ?: item.done)
                        ticked[task.id] = now
                        scope.launch(Dispatchers.IO) { repository.setCompleted(task.id, now) }
                    }
                }
            },
            onMove = { index -> (shown[index] as? SheetItem.Todo)?.item?.task?.let { moving = it } },
            onPage = { page = it.coerceIn(1, pages) },
            onClose = onClose,
            onAddEvent = { day?.let { MainScreenRequests.request(MainScreenRequest.NewEvent(it.date.toString())) } },
            onOpenDay = { day?.let { onOpenDay(it.date) } }
        )
    }

    moving?.let { task ->
        WeekMovePicker(
            week = week,
            task = task,
            onPickDate = {
                moving = null
                pickDate = task
            },
            onMove = { moveTo(task, it.toString()) },
            onClose = { moving = null }
        )
    }
    pickDate?.let { task ->
        TaskDatePickerDialog(
            title = "Move to day",
            currentDate = task.dayDate,
            actionButtonLabel = "Move",
            onDateSelected = { moveTo(task, it) },
            onDismiss = { pickDate = null }
        )
    }
}

private fun eventTime(item: WeekEventItem, use24: Boolean): String {
    val minute = item.minute ?: return "All day"
    val start = WeekLayoutSpec.timeText(minute, use24, compact = false)
    val endMs = item.event.endTime ?: return start
    val end = java.time.Instant.ofEpochMilli(endMs).atZone(java.time.ZoneId.systemDefault())
    val endText = WeekLayoutSpec.timeText(end.hour * 60 + end.minute, use24, compact = false)
    // "10:00 – 10:45 AM": one AM or PM when both ends share it
    val sameHalf = !use24 && (minute < 720) == (end.hour < 12)
    return "${if (sameHalf) start.substringBefore(' ') else start} – $endText"
}

private fun shortDate(day: String): String? =
    runCatching { DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).format(LocalDate.parse(day)) }.getOrNull()

/** The week's days to move a task to, and Pick a Date for any other. */
@Composable
private fun WeekMovePicker(
    week: WeekData,
    task: Task,
    onPickDate: () -> Unit,
    onMove: (LocalDate) -> Unit,
    onClose: () -> Unit
) {
    Popup(popupPositionProvider = ScreenCentre, onDismissRequest = onClose, properties = PopupProperties(focusable = true)) {
        Column(
            modifier = Modifier
                .width(EInkTheme.PopupOverlay.width)
                .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                .padding(EInkTheme.PopupOverlay.padding),
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
        ) {
            Text(text = "Move to", fontSize = EInkTheme.Typography.titleMedium, fontWeight = FontWeight.Bold, color = EInkTheme.Colors.primary)
            week.days.filter { it.date.toString() != task.dayDate }.forEach { day ->
                Text(
                    text = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()).format(day.date),
                    fontSize = EInkTheme.Typography.bodyLarge,
                    color = EInkTheme.Colors.primary,
                    modifier = Modifier.fillMaxWidth().height(44.dp).noRippleClickable { onMove(day.date) }.padding(vertical = 10.dp)
                )
            }
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                EinkSecondaryButton(text = "Close", onClick = onClose)
                EinkSecondaryButton(text = "Pick a Date", onClick = onPickDate, width = 130.dp)
            }
        }
    }
}
