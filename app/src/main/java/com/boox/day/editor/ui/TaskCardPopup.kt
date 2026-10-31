package com.boox.day.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.RecurringTaskDraft
import com.boox.day.data.db.RecurringTaskTemplate
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskDueDates
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.dialogs.ActionButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Task card (31 Oct 2026, task dialogs review option B; first built as plan T3 / 13.3): opened from
 * a row's chevron, or at its Due screen from the row's calendar icon. The main list shows each
 * setting's value; tapping a line opens that one setting inside the same popup, and Back returns to
 * the list, so no popup opens on top of another. Only shows values and calls back; MainScreen does
 * the work through the repository.
 *
 * Saving: priority and reminder save on tap; Due, Repeat and Other time need Save; Back keeps the
 * old value. E-ink: Popup (no Dialog), no ripples, ≥44 dp targets, buttons per DIALOG_RULEBOOK.
 */
enum class TaskCardScreen(val title: String) {
    Main(""), Text("Text"), Due("Due date"), Reminder("Reminder"), ReminderTime("Reminder time"),
    Repeat("Repeat"), RepeatStart("Repeat starts"), RepeatEnd("Repeat ends"), Subtasks("Subtasks"),
    List("Google Tasks list");

    /** Where Back goes; null on the main list (Back closes the card). */
    val parent: TaskCardScreen?
        get() = when (this) {
            Main -> null
            ReminderTime -> Reminder
            RepeatStart, RepeatEnd -> Repeat
            else -> Main
        }
}

data class TaskCardSubtask(val words: String, val done: Boolean)

/** What the task card shows besides the task row itself (loaded when the card opens). */
data class TaskCardData(
    val task: Task,
    val template: RecurringTaskTemplate?,
    /** Why this task can't repeat (subtask, has subtasks, no words), or null. */
    val repeatBlockedReason: String?,
    val subtasks: List<TaskCardSubtask>,
    /** The parent task's words, for a subtask. */
    val parentWords: String?,
    val startScreen: TaskCardScreen = TaskCardScreen.Main,
    /** The task's Google Tasks list (task sync, owner decisions 47 and 53); null hides the List line. */
    val list: TaskCardList? = null
)

data class TaskCardList(
    /** The lists BooxDay syncs: id to name. */
    val lists: List<Pair<String, String>>,
    /** The list it is in, or goes to when it first syncs. */
    val currentId: String?,
    /** Why it can't be changed here ("with its main task"), or null. */
    val fixedReason: String? = null,
    /** The list it is being moved to right now (owner decision 57), or null. */
    val movingToId: String? = null
)

class TaskCardActions(
    val onEditText: () -> Unit,
    /** Turn recognized handwriting into typed text; null when there is nothing to convert. */
    val onConvertToText: (() -> Unit)?,
    val onPriorityChange: (String) -> Unit,
    val onSaveDue: (LocalDate) -> Unit,
    val onClearDue: () -> Unit,
    /** Go to that day on the Tasks page; null hides Show Day. */
    val onShowDay: ((LocalDate) -> Unit)?,
    /** Minutes after midnight, or null for the usual time (Preferences). */
    val onReminderChange: (Int?) -> Unit,
    val onSaveRepeat: (RecurringTaskDraft) -> Unit,
    /** Pause or resume the series; null when the task doesn't repeat. */
    val onPauseRepeat: (() -> Unit)?,
    /** Stop the series (asks first; tasks already made stay); null when the task doesn't repeat. */
    val onStopRepeat: (() -> Unit)?,
    /** AI break-down into subtasks; null hides it. */
    val onBreakDown: (() -> Unit)?,
    val onDelete: () -> Unit,
    val onMove: () -> Unit,
    val onDismiss: () -> Unit,
    /** "Move", or "Copy" for subtasks (they move with their parent). */
    val moveLabel: String = "Move",
    /** Move the task to that Google Tasks list (its subtasks along); null: can't be changed. */
    val onListChange: ((String) -> Unit)? = null
)

@Composable
fun TaskCardPopup(
    data: TaskCardData,
    /** Typed or AI-converted text (null for handwriting). */
    titleText: String?,
    /** Text recognized from the row's handwriting, when any. */
    recognizedText: String?,
    defaultReminderMinuteOfDay: Int,
    actions: TaskCardActions
) {
    var screen by remember(data.task.id, data.startScreen) { mutableStateOf(data.startScreen) }
    Popup(
        alignment = Alignment.Center,
        // The content fills the window, so this is the Back key only: one screen up, then close
        onDismissRequest = { screen.parent?.let { screen = it } ?: actions.onDismiss() },
        properties = PopupProperties(focusable = true, dismissOnBackPress = true)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().noRippleClickable { actions.onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            TaskCardContent(
                data = data,
                titleText = titleText,
                recognizedText = recognizedText,
                defaultReminderMinuteOfDay = defaultReminderMinuteOfDay,
                screen = screen,
                onScreen = { screen = it },
                actions = actions,
                modifier = Modifier.padding(horizontal = 16.dp).noRippleClickable { }  // taps inside don't close
            )
        }
    }
}

/** The card without its popup (render tests draw this). */
@Composable
fun TaskCardContent(
    data: TaskCardData,
    titleText: String?,
    recognizedText: String?,
    defaultReminderMinuteOfDay: Int,
    screen: TaskCardScreen,
    onScreen: (TaskCardScreen) -> Unit,
    actions: TaskCardActions,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now()
) {
    val task = data.task
    val taskDay = TaskDueDates.toLocalDate(task.dayDate) ?: today
    // Repeat edits are shared by the Repeat, Starts and Ends screens; fresh each time Repeat opens from the list
    var repeatForm by remember(task.id, data.template) { mutableStateOf(RepeatForm.of(data.template, taskDay)) }
    val go: (TaskCardScreen) -> Unit = { next ->
        if (next == TaskCardScreen.Repeat && screen == TaskCardScreen.Main) repeatForm = RepeatForm.of(data.template, taskDay)
        onScreen(next)
    }
    val words = titleText ?: recognizedText
    val subtitle = words ?: "Handwritten task"

    Column(
        modifier = modifier
            .widthIn(max = EInkTheme.PopupOverlay.width)
            .fillMaxWidth()
            .heightIn(max = EInkTheme.PopupOverlay.maxHeight)
            .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .padding(EInkTheme.ModernDialog.padding)
    ) {
        when (screen) {
            TaskCardScreen.Main -> {
                val dayLine = "On " + taskDay.format(LONG_DAY) +
                    (data.parentWords?.let { " · subtask of “$it”" } ?: "")
                Header(title = words ?: "Handwritten task", subtitle = dayLine, italic = titleText == null && words != null)
                Body { MainList(data, titleText, defaultReminderMinuteOfDay, today, go, actions) }
                Footer3(actions.onDismiss, "Close", actions.onDelete, "Delete", actions.onMove, actions.moveLabel)
            }
            TaskCardScreen.Text -> {
                Header(screen.title, subtitle)
                Body {
                    if (recognizedText != null) {
                        Note("Read from your handwriting:")
                        Box(
                            Modifier.fillMaxWidth()
                                .border(1.dp, EInkTheme.Colors.borderStrong, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                                .padding(12.dp)
                        ) {
                            Text(recognizedText, fontSize = EInkTheme.Typography.bodyLarge, fontStyle = FontStyle.Italic, color = EInkTheme.Colors.primary)
                        }
                        Note("Convert to Text puts these words in as typed text and erases the handwriting. Edit Text lets you correct them first.")
                    } else {
                        Note("No words have been read from this handwriting yet. Edit Text lets you type them.")
                    }
                }
                val convert = actions.onConvertToText
                if (convert != null) {
                    Footer3({ go(TaskCardScreen.Main) }, "Back", actions.onEditText, "Edit Text", convert, "Convert to Text")
                } else {
                    Footer2({ go(TaskCardScreen.Main) }, "Back", actions.onEditText, "Edit Text")
                }
            }
            TaskCardScreen.Due -> DueScreen(task, subtitle, today, go, actions)
            TaskCardScreen.Reminder -> ReminderScreen(task, subtitle, defaultReminderMinuteOfDay, go, actions)
            TaskCardScreen.ReminderTime -> ReminderTimeScreen(task, subtitle, go, actions)
            TaskCardScreen.Repeat -> RepeatScreen(data, subtitle, repeatForm, { repeatForm = it }, go, actions)
            TaskCardScreen.RepeatStart -> {
                var picked by remember { mutableStateOf<LocalDate?>(repeatForm.start) }
                Header(screen.title, subtitle)
                Body { MonthGrid(picked, today) { picked = it } }
                Footer2({ go(TaskCardScreen.Repeat) }, "Back", {
                    picked?.let { repeatForm = repeatForm.withStart(it) }
                    go(TaskCardScreen.Repeat)
                }, "Use Date", enabled = picked != null)
            }
            TaskCardScreen.RepeatEnd -> {
                var picked by remember { mutableStateOf(repeatForm.end) }
                Header(screen.title, subtitle)
                Body { MonthGrid(picked, today, initialMonth = picked ?: repeatForm.start) { picked = it } }
                Footer3({ go(TaskCardScreen.Repeat) }, "Back", {
                    repeatForm = repeatForm.copy(end = null)
                    go(TaskCardScreen.Repeat)
                }, "No End Date", {
                    repeatForm = repeatForm.copy(end = picked)
                    go(TaskCardScreen.Repeat)
                }, "Use Date", enabled = picked != null && !picked!!.isBefore(repeatForm.start))
            }
            TaskCardScreen.List -> data.list?.let { ListScreen(it, subtitle, go, actions) }
            TaskCardScreen.Subtasks -> {
                Header(screen.title, subtitle)
                Body {
                    if (data.subtasks.isNotEmpty()) {
                        data.subtasks.forEach { SubtaskLine(it) }
                        Note("They are the rows under this task on the Tasks page. Tick them there.")
                    } else {
                        Note("No subtasks yet. Break Down asks AI to split this task into small steps and adds them under it.")
                    }
                }
                val breakDown = actions.onBreakDown
                if (breakDown != null) Footer2({ go(TaskCardScreen.Main) }, "Back", breakDown, "Break Down")
                else Footer1({ go(TaskCardScreen.Main) }, "Back")
            }
        }
    }
}

// ---- Screens ------------------------------------------------------------------------------------------

@Composable
private fun MainList(
    data: TaskCardData,
    titleText: String?,
    defaultReminderMinuteOfDay: Int,
    today: LocalDate,
    go: (TaskCardScreen) -> Unit,
    actions: TaskCardActions
) {
    val task = data.task
    // Typed tasks go straight to the typing popup; handwriting gets the Text screen (Convert to Text)
    ValueLine("Text", if (titleText != null) "Typed" else "Handwritten") {
        if (titleText != null) actions.onEditText() else go(TaskCardScreen.Text)
    }
    PriorityLine(task.priority, actions.onPriorityChange)
    val due = TaskDueDates.toLocalDate(task.dueDay)
    val overdue = TaskDueDates.isOverdue(task.dueDay, task.isCompleted, today.toString())
    ValueLine(
        "Due",
        due?.let { if (overdue) "Overdue: ${it.format(SHORT_DAY)}" else it.format(SHORT_DAY) } ?: "None",
        bold = overdue
    ) { go(TaskCardScreen.Due) }
    if (due != null) {
        val custom = task.reminderMinuteOfDay
        ValueLine("Reminder", custom?.let { formatMinuteOfDay(it) } ?: "${formatMinuteOfDay(defaultReminderMinuteOfDay)} (usual time)") {
            go(TaskCardScreen.Reminder)
        }
    }
    val taskDay = TaskDueDates.toLocalDate(task.dayDate) ?: today
    ValueLine("Repeat", data.template?.let { taskRepeatLabel(it, taskDay) } ?: "Off") { go(TaskCardScreen.Repeat) }
    if (data.subtasks.isNotEmpty() || actions.onBreakDown != null) {
        val done = data.subtasks.count { it.done }
        ValueLine("Subtasks", if (data.subtasks.isEmpty()) "None" else "${data.subtasks.size}, $done done") {
            go(TaskCardScreen.Subtasks)
        }
    }
    data.list?.let { list ->
        val name = list.lists.firstOrNull { it.first == list.currentId }?.second ?: "None"
        val moving = list.movingToId?.let { id -> list.lists.firstOrNull { it.first == id }?.second }
        if (moving != null) InfoLine("List", "Moving to $moving…")
        else if (list.fixedReason == null && actions.onListChange != null) ValueLine("List", name) { go(TaskCardScreen.List) }
        else InfoLine("List", list.fixedReason?.let { "$name ($it)" } ?: name)
    }
}

/** The Google Tasks lists BooxDay syncs; picking one moves the task there (owner decision 47). */
@Composable
private fun ColumnScope.ListScreen(list: TaskCardList, subtitle: String, go: (TaskCardScreen) -> Unit, actions: TaskCardActions) {
    Header(TaskCardScreen.List.title, subtitle)
    for ((id, name) in list.lists) {
        ChoiceLine(name, id == list.currentId) {
            if (id != list.currentId) actions.onListChange?.invoke(id)
            go(TaskCardScreen.Main)
        }
    }
    Footer1({ go(TaskCardScreen.Main) }, "Back")
}

@Composable
private fun ColumnScope.DueScreen(task: Task, subtitle: String, today: LocalDate, go: (TaskCardScreen) -> Unit, actions: TaskCardActions) {
    val saved = TaskDueDates.toLocalDate(task.dueDay)
    var picked by remember(task.id) { mutableStateOf(saved) }
    Header(TaskCardScreen.Due.title, subtitle)
    Body {
        Row(Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
            Text(
                picked?.let { "${it.format(SHORT_DAY)} · ${relativeDay(it, today)}" } ?: "No due date",
                fontSize = EInkTheme.Typography.bodyLarge, fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary, modifier = Modifier.weight(1f)
            )
            val showDay = actions.onShowDay
            val day = picked
            if (showDay != null && day != null) EinkSecondaryButton(text = "Show Day", onClick = { showDay(day) })
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Today" to today, "Tomorrow" to today.plusDays(1), "Next Week" to today.plusWeeks(1)).forEach { (label, day) ->
                Chip(label, picked == day, Modifier.weight(1f)) { picked = day }
            }
        }
        Thin()
        MonthGrid(picked, today) { picked = it }
    }
    val save = { picked?.let { actions.onSaveDue(it) }; go(TaskCardScreen.Main) }
    val canSave = picked != null && picked != saved
    if (saved != null) {
        Footer3({ go(TaskCardScreen.Main) }, "Back", { actions.onClearDue(); go(TaskCardScreen.Main) }, "Remove Due Date", save, "Save", enabled = canSave)
    } else {
        Footer2({ go(TaskCardScreen.Main) }, "Back", save, "Save", enabled = canSave)
    }
}

private val REMINDER_PRESETS = listOf(8 * 60, 12 * 60, 15 * 60)

@Composable
private fun ColumnScope.ReminderScreen(
    task: Task, subtitle: String, defaultReminderMinuteOfDay: Int, go: (TaskCardScreen) -> Unit, actions: TaskCardActions
) {
    val custom = task.reminderMinuteOfDay
    val pick = { minute: Int? -> actions.onReminderChange(minute); go(TaskCardScreen.Main) }
    Header(TaskCardScreen.Reminder.title, subtitle)
    Body {
        val due = TaskDueDates.toLocalDate(task.dueDay)
        Note(due?.let { "On the due day, ${it.format(SHORT_DAY)}, at:" } ?: "On the due day, at:")
        Thin()
        ChoiceLine("Usual time (${formatMinuteOfDay(defaultReminderMinuteOfDay)})", custom == null) { pick(null) }
        REMINDER_PRESETS.forEach { preset -> ChoiceLine(formatMinuteOfDay(preset), custom == preset) { pick(preset) } }
        val other = custom != null && custom !in REMINDER_PRESETS
        ChoiceLine("Other time", other, trailing = if (other) formatMinuteOfDay(custom!!) else "") { go(TaskCardScreen.ReminderTime) }
    }
    Footer1({ go(TaskCardScreen.Main) }, "Back")
}

@Composable
private fun ColumnScope.ReminderTimeScreen(task: Task, subtitle: String, go: (TaskCardScreen) -> Unit, actions: TaskCardActions) {
    val current = task.reminderMinuteOfDay
    var hour by remember(task.id) { mutableStateOf(current?.div(60)?.takeIf { it in 6..21 }) }
    var minute by remember(task.id) { mutableStateOf(current?.rem(60)?.takeIf { it % 15 == 0 }) }
    Header(TaskCardScreen.ReminderTime.title, subtitle)
    Body {
        Note("Hour")
        (6..21).chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { h -> Chip(formatHour(h), hour == h, Modifier.weight(1f)) { hour = h } }
            }
        }
        Note("Minutes")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 15, 30, 45).forEach { m -> Chip(":%02d".format(m), minute == m, Modifier.weight(1f)) { minute = m } }
        }
    }
    val h = hour
    val m = minute
    Footer2(
        { go(TaskCardScreen.Reminder) }, "Back",
        { if (h != null && m != null) { actions.onReminderChange(h * 60 + m); go(TaskCardScreen.Main) } },
        if (h != null && m != null) "Set ${formatMinuteOfDay(h * 60 + m)}" else "Set Time",
        enabled = h != null && m != null
    )
}

@Composable
private fun ColumnScope.RepeatScreen(
    data: TaskCardData,
    subtitle: String,
    form: RepeatForm,
    onForm: (RepeatForm) -> Unit,
    go: (TaskCardScreen) -> Unit,
    actions: TaskCardActions
) {
    val series = data.template
    val blocked = data.repeatBlockedReason != null && series == null
    Header(TaskCardScreen.Repeat.title, subtitle)
    Body {
        if (blocked) {
            Note(data.repeatBlockedReason ?: "")
            return@Body
        }
        RepeatChoice.entries.forEach { choice ->
            ChoiceLine(repeatChoiceLabel(choice, form), form.choice == choice) { onForm(form.copy(choice = choice)) }
            if (choice == RepeatChoice.SOME_DAYS && form.choice == RepeatChoice.SOME_DAYS) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        val on = day.value in form.days
                        Chip(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()), on, Modifier.weight(1f), padding = 0.dp) {
                            onForm(form.copy(days = if (on) form.days - day.value else (form.days + day.value).sorted()))
                        }
                    }
                }
                Thin()
            }
        }
        if (form.choice != RepeatChoice.OFF) {
            ValueLine("Starts", form.start.format(SHORT_DAY)) { go(TaskCardScreen.RepeatStart) }
            ValueLine("Ends", form.end?.format(SHORT_DAY) ?: "Never") { go(TaskCardScreen.RepeatEnd) }
        }
    }
    val back = { go(TaskCardScreen.Main) }
    if (blocked) {
        Footer1(back, "Back")
        return
    }
    val canSave = when (form.choice) {
        RepeatChoice.OFF -> series != null  // Off on a series stops it (asks first)
        RepeatChoice.SOME_DAYS -> form.days.isNotEmpty()
        else -> true
    }
    val save = {
        val draft = form.toDraft()
        if (draft == null) actions.onStopRepeat?.invoke() else actions.onSaveRepeat(draft)
        go(TaskCardScreen.Main)
    }
    val pause = actions.onPauseRepeat
    if (series != null && pause != null) {
        Footer3(back, "Back", { pause(); go(TaskCardScreen.Main) }, if (series.isActive) "Pause Repeats" else "Resume Repeats", save, "Save", enabled = canSave)
    } else {
        Footer2(back, "Back", save, "Save", enabled = canSave)
    }
}

// ---- Building blocks ----------------------------------------------------------------------------------

private val ROW_HEIGHT = 56.dp
private val LABEL_WIDTH = 110.dp
private val SHORT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
private val LONG_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())

@Composable
private fun Header(title: String, subtitle: String, italic: Boolean = false) {
    Text(
        title, fontSize = EInkTheme.ModernDialog.titleFontSize, fontWeight = FontWeight.Bold,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        color = EInkTheme.Colors.primary, maxLines = 3, overflow = TextOverflow.Ellipsis
    )
    Spacer(Modifier.height(4.dp))
    Text(subtitle, fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.darkGray, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(12.dp))
    Divider(color = EInkTheme.Colors.primary, thickness = 1.dp)
}

/** The part between header and buttons; scrolls if a long subtask list or big font makes it too tall. */
@Composable
private fun ColumnScope.Body(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
}

@Composable
private fun Thin() = Divider(color = EInkTheme.Colors.borderStrong, thickness = 0.5.dp)

@Composable
private fun Note(text: String) {
    Text(text, fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.darkGray, modifier = Modifier.padding(vertical = 10.dp))
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = EInkTheme.Colors.primary, modifier = Modifier.size(EInkTheme.Global.iconSize))
}

@Composable
private fun ValueLine(label: String, value: String, bold: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).noRippleClickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = EInkTheme.Typography.bodyLarge, color = EInkTheme.Colors.darkGray, modifier = Modifier.width(LABEL_WIDTH))
        Text(
            value, fontSize = EInkTheme.Typography.bodyLarge, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = EInkTheme.Colors.primary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Chevron()
    }
    Thin()
}

/** Like [ValueLine], for a value that can't be changed here: no arrow, no tap. */
@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = EInkTheme.Typography.bodyLarge, color = EInkTheme.Colors.darkGray, modifier = Modifier.width(LABEL_WIDTH))
        Text(
            value, fontSize = EInkTheme.Typography.bodyLarge, color = EInkTheme.Colors.primary,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
    Thin()
}

/** One choice of several: a round mark, the chosen one filled. */
@Composable
private fun ChoiceLine(text: String, selected: Boolean, trailing: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).noRippleClickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(24.dp).border(2.dp, EInkTheme.Colors.primary, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(12.dp).background(EInkTheme.Colors.primary, CircleShape))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text, fontSize = EInkTheme.Typography.bodyLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = EInkTheme.Colors.primary, modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(trailing, fontSize = EInkTheme.Typography.bodyLarge, color = EInkTheme.Colors.primary)
            Chevron()
        }
    }
    Thin()
}

@Composable
private fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, padding: androidx.compose.ui.unit.Dp = 8.dp, onClick: () -> Unit) {
    Box(
        modifier
            .height(EInkTheme.Global.touchTarget)
            .widthIn(min = EInkTheme.Global.touchTarget)
            .background(if (selected) EInkTheme.Colors.primary else EInkTheme.Colors.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .border(1.dp, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .noRippleClickable { if (!selected) onClick() }
            .padding(horizontal = padding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, fontSize = EInkTheme.Typography.bodyLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) EInkTheme.Colors.white else EInkTheme.Colors.primary, maxLines = 1
        )
    }
}

@Composable
private fun PriorityLine(priority: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        Text("Priority", fontSize = EInkTheme.Typography.bodyLarge, color = EInkTheme.Colors.darkGray, modifier = Modifier.width(LABEL_WIDTH))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("None" to "", "A" to "A", "B" to "B", "C" to "C").forEach { (label, value) ->
                Chip(label, priority == value) { onChange(value) }
            }
        }
    }
    Thin()
}

@Composable
private fun SubtaskLine(subtask: TaskCardSubtask) {
    Row(Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(24.dp)
                .background(if (subtask.done) EInkTheme.Colors.primary else EInkTheme.Colors.background, RoundedCornerShape(4.dp))
                .border(2.dp, EInkTheme.Colors.primary, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (subtask.done) Icon(Icons.Default.Check, contentDescription = "Done", tint = EInkTheme.Colors.white, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            subtask.words, fontSize = EInkTheme.Typography.bodyLarge,
            color = if (subtask.done) EInkTheme.Colors.darkGray else EInkTheme.Colors.primary,
            textDecoration = if (subtask.done) TextDecoration.LineThrough else null,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
    Thin()
}

/**
 * Month calendar: opens on the chosen day's month (else today's), 44 dp days, every day shown can be
 * tapped (the next month's first days too), only the weeks the month needs, week start from Settings.
 */
@Composable
private fun MonthGrid(selected: LocalDate?, today: LocalDate, initialMonth: LocalDate? = null, onPick: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf((initialMonth ?: selected ?: today).withDayOfMonth(1)) }
    Row(Modifier.fillMaxWidth().height(EInkTheme.Global.touchTarget), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(EInkTheme.Global.touchTarget).noRippleClickable { month = month.minusMonths(1) }, contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month", tint = EInkTheme.Colors.primary, modifier = Modifier.size(28.dp))
        }
        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
            fontSize = EInkTheme.Typography.bodyLarge, fontWeight = FontWeight.Bold, color = EInkTheme.Colors.primary,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
        )
        Box(Modifier.size(EInkTheme.Global.touchTarget).noRippleClickable { month = month.plusMonths(1) }, contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month", tint = EInkTheme.Colors.primary, modifier = Modifier.size(28.dp))
        }
    }
    val firstDayOfWeek = GlobalAppSettings.current.firstDayOfWeek
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        weekdayInitials(firstDayOfWeek).forEach {
            Text(it, fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.darkGray, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
    val days = monthGridDays(month, firstDayOfWeek)
    days.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            week.forEach { day ->
                val isSelected = day == selected
                val isToday = day == today
                Box(
                    Modifier.weight(1f).height(EInkTheme.Global.touchTarget).padding(2.dp)
                        .background(if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                        .then(if (isToday && !isSelected) Modifier.border(1.5.dp, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius)) else Modifier)
                        .noRippleClickable { onPick(day) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        day.dayOfMonth.toString(), fontSize = EInkTheme.Typography.bodyLarge,
                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isSelected -> EInkTheme.Colors.white
                            day.month == month.month -> EInkTheme.Colors.primary
                            else -> EInkTheme.Colors.darkGray
                        }
                    )
                }
            }
        }
    }
}

/** The days a month grid shows: whole weeks from the week start before the 1st to the week of the last day. */
internal fun monthGridDays(month: LocalDate, firstDayOfWeek: com.boox.day.data.datastore.AppSettings.FirstDayOfWeek): List<LocalDate> {
    val first = month.withDayOfMonth(1)
    val start = first.minusDays(leadingDays(first, firstDayOfWeek).toLong())
    val last = first.withDayOfMonth(first.lengthOfMonth())
    val weeks = (ChronoUnit.DAYS.between(start, last) / 7 + 1).toInt()
    return (0 until weeks * 7).map { start.plusDays(it.toLong()) }
}

@Composable
private fun Footer1(onBack: () -> Unit, back: String) {
    Spacer(Modifier.height(EInkTheme.ModernDialog.contentSpacing))
    Row(Modifier.fillMaxWidth()) {
        EinkSecondaryButton(text = back, onClick = onBack, modifier = Modifier.weight(0.4f))
        Spacer(Modifier.weight(0.6f))
    }
}

@Composable
private fun Footer2(onBack: () -> Unit, back: String, onPrimary: () -> Unit, primary: String, enabled: Boolean = true) {
    Spacer(Modifier.height(EInkTheme.ModernDialog.contentSpacing))
    Row(Modifier.fillMaxWidth()) {
        EinkSecondaryButton(text = back, onClick = onBack, modifier = Modifier.weight(0.4f))
        Spacer(Modifier.weight(0.2f))
        ActionButton(text = primary, onClick = onPrimary, enabled = enabled, modifier = Modifier.weight(0.4f))
    }
}

/** DIALOG_RULEBOOK 3-button layout: escape left, secondary or destructive middle, primary right. */
@Composable
private fun Footer3(
    onBack: () -> Unit, back: String, onMiddle: () -> Unit, middle: String, onPrimary: () -> Unit, primary: String, enabled: Boolean = true
) {
    Spacer(Modifier.height(EInkTheme.ModernDialog.contentSpacing))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EinkSecondaryButton(text = back, onClick = onBack, modifier = Modifier.weight(1f))
        EinkSecondaryButton(text = middle, onClick = onMiddle, modifier = Modifier.weight(1.3f))
        ActionButton(text = primary, onClick = onPrimary, enabled = enabled, modifier = Modifier.weight(1f))
    }
}

private fun relativeDay(day: LocalDate, today: LocalDate): String = when (val n = ChronoUnit.DAYS.between(today, day)) {
    0L -> "today"
    1L -> "tomorrow"
    -1L -> "yesterday"
    else -> if (n > 0) "in $n days" else "${-n} days ago"
}

private fun formatHour(hour: Int): String =
    LocalTime.of(hour, 0).format(DateTimeFormatter.ofPattern("h a", Locale.getDefault()))

private fun formatMinuteOfDay(minuteOfDay: Int): String =
    LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()))
