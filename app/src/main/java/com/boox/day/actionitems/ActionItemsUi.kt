package com.boox.day.actionitems

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ai.ConvertedTask
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.TaskDueDates
import com.boox.day.data.db.TaskRepository
import com.boox.day.ui.SnackConf
import com.boox.day.ui.SnackState
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.theme.InkaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Action items (Oct 2026): the things to do written on a day's memos, each one added to Tasks with a
 * tap. Opened from a meeting's notes in event details (its toolbar button was removed
 * 14 Oct 2026 to make room on the Go 10.3 bar); MainScreen places [ActionItemsHost] once and keeps
 * raw drawing off while [isOpen]. E-ink: a Popup (no Dialog), no ripples, buttons per DIALOG_RULEBOOK.
 */
object ActionItemsUi {
    /** A day's memos ([pageIds] null) or some of its pages (a meeting's notes, [title] names them). */
    data class Request(val date: String, val pageIds: List<String>? = null, val title: String? = null)

    var request by mutableStateOf<Request?>(null)
        private set

    /** Goes up each time rows were added to Tasks: MainScreen reloads the Tasks page it shows. */
    var tasksAdded by mutableIntStateOf(0)
        private set

    val isOpen: Boolean get() = request != null

    fun open(date: String, pageIds: List<String>? = null, title: String? = null) {
        request = Request(date, pageIds, title)
    }

    fun close() {
        request = null
    }

    internal fun onTasksAdded() {
        tasksAdded++
    }
}

@Composable
fun ActionItemsHost() {
    val request = ActionItemsUi.request ?: return
    InkaTheme {
        key(request) { ActionItemsPopup(request, onDismiss = ActionItemsUi::close) }
    }
}

/** The list: [Hide] [Add to Tasks] on each line, [Close] at the bottom. */
@Composable
private fun ActionItemsPopup(request: ActionItemsUi.Request, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<ActionItemsReader.Result?>(null) }
    var alreadyHandled by remember { mutableIntStateOf(0) }
    val hidden = remember { mutableStateMapOf<String, Boolean>() }
    val done = remember { mutableStateMapOf<String, String>() }  // key → ADDED, this time

    LaunchedEffect(request) {
        val read = withContext(Dispatchers.Default) {
            ActionItemsReader.read(context, scope, request.date, request.pageIds)
        }
        val handled = withContext(Dispatchers.IO) { ActionItemsMemory.handled(context, request.date) }
        alreadyHandled = read.items.count { it.key in handled }
        result = read.copy(items = read.items.filter { it.key !in handled })
    }

    fun mark(item: ActionItem, state: String) {
        ActionItemsMemory.mark(context, request.date, item.key, state)
        if (state == ActionItemsMemory.HIDDEN) hidden[item.key] = true else done[item.key] = state
    }

    fun addToTasks(item: ActionItem) {
        scope.launch {
            val added = withContext(Dispatchers.IO) {
                runCatching {
                    TaskRepository(context.applicationContext).addConvertedTasksToDate(
                        tasks = listOf(ConvertedTask(text = taskTitle(item.text), dueDateOverride = TaskDueDates.startOfDayMillis(item.dueDay))),
                        defaultTargetDate = request.date,
                        canvasHeight = GlobalAppSettings.tasksCanvasHeight
                    ).tasksAdded
                }.getOrDefault(0)
            }
            if (added > 0) {
                mark(item, ActionItemsMemory.ADDED)
                ActionItemsUi.onTasksAdded()
            } else {
                SnackState.globalSnackFlow.tryEmit(SnackConf(text = "Couldn't add it to Tasks", duration = SnackConf.DURATION_STANDARD))
            }
        }
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .noRippleClickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(vertical = 8.dp)
                    .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .noRippleClickable { }  // taps inside don't dismiss
                    .padding(EInkTheme.ModernDialog.padding)
            ) {
                Text("Action items", fontSize = EInkTheme.ModernDialog.titleFontSize, fontWeight = FontWeight.Bold, color = EInkTheme.ModernDialog.titleColor)
                Text(
                    text = request.title ?: "From your memos · ${dayLabel(request.date)}",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray
                )
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .heightIn(max = EInkTheme.PopupOverlay.maxHeight - 150.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    val read = result
                    val shown = read?.items?.filter { hidden[it.key] != true }.orEmpty()
                    when {
                        read == null -> Message("Reading your handwriting…", italic = true)
                        shown.isEmpty() -> {
                            Message(
                                when {
                                    read.handwritingUnavailable -> "Handwriting recognition is still downloading on this tablet (it needs Wi-Fi). Try again in a few minutes."
                                    read.pagesRead == 0 -> "No memos on this day yet."
                                    alreadyHandled > 0 -> "Nothing new on this day's memos."
                                    else -> "No action items found. Lines that start with a doing word (“call Mike”), an empty box or “TODO:” show up here."
                                }
                            )
                        }
                        else -> shown.forEachIndexed { index, item ->
                            if (index > 0) Divider(color = EInkTheme.Colors.borderStrong, thickness = 0.5.dp)
                            ItemRow(
                                item = item,
                                showPage = read.pagesRead > 1,
                                state = done[item.key],
                                onHide = { mark(item, ActionItemsMemory.HIDDEN) },
                                onAddToTasks = { addToTasks(item) }
                            )
                        }
                    }
                    if (read != null && alreadyHandled > 0) {
                        Message("$alreadyHandled already added or hidden.")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Divider(color = EInkTheme.Colors.borderStrong, thickness = 0.5.dp)
                Spacer(Modifier.height(12.dp))
                EinkSecondaryButton(text = "Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: ActionItem,
    showPage: Boolean,
    state: String?,
    onHide: () -> Unit,
    onAddToTasks: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(item.text, fontSize = EInkTheme.Typography.bodyLarge, color = EInkTheme.Colors.primary)
        val details = listOfNotNull(
            "Page ${item.pageNumber}".takeIf { showPage },
            item.dueDay?.let { "Due ${dayLabel(it)}" }
        )
        if (details.isNotEmpty()) {
            Text(details.joinToString(" · "), fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.darkGray)
        }
        Spacer(Modifier.height(6.dp))
        if (state != null) {
            Text(
                text = "Added to Tasks",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = EInkTheme.Colors.darkGray
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EinkSecondaryButton(text = "Hide", onClick = onHide, modifier = Modifier.weight(0.8f))
                EinkSecondaryButton(text = "Add to Tasks", onClick = onAddToTasks, modifier = Modifier.weight(1.2f))
            }
        }
    }
}

@Composable
private fun Message(text: String, italic: Boolean = false) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyMedium,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        color = EInkTheme.Colors.darkGray,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

/** A Tasks row holds a short title: long lines are cut at a word. */
private fun taskTitle(text: String): String {
    val max = EInkTheme.TasksTab.typedTextMaxLength
    if (text.length <= max) return text
    val cut = text.take(max).substringBeforeLast(' ').takeIf { it.length >= max / 2 } ?: text.take(max)
    return cut.trimEnd(',', ';', ':', '-', ' ')
}

private fun dayLabel(date: String): String =
    runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())) }.getOrDefault(date)
