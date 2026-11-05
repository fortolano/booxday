package com.boox.day.proposals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boox.day.ai.ScheduleItemsService
import com.boox.day.ai.ScheduleResult
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.TaskRepository
import com.boox.day.data.model.ParsedEventData
import com.boox.day.editor.ui.TaskDatePickerDialog
import com.boox.day.events.EventDraftSaver
import com.boox.day.events.EventTargets
import com.boox.day.ui.AiFeature
import com.boox.day.ui.FullScreenState
import com.boox.day.ui.MainScreenRequest
import com.boox.day.ui.MainScreenRequests
import com.boox.day.ui.ScreenCentre
import com.boox.day.ui.SnackConf
import com.boox.day.ui.SnackState
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.components.keyboardHeight
import com.boox.day.ui.dialogs.ActionButton
import com.boox.day.ui.dialogs.TimeSelector
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.theme.InkaTheme
import com.boox.day.ui.theme.LayoutProfiles
import com.boox.day.viewmodel.EventsViewModel
import com.boox.day.week.WeekLayoutSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Write It, Schedule It (Nov 2026): the selection bar's calendar button starts here. */
object ScheduleUi {
    /** [ready]: items found elsewhere (a recap's action items), to review with no reading. */
    data class Job(val strokes: List<Stroke>, val memoDay: LocalDate, val ready: ScheduleResult.Found? = null)

    var job by mutableStateOf<Job?>(null)
        private set

    val isOpen: Boolean get() = job != null

    /** The strokes are kept, so connecting OpenRouter carries on with no second lasso. */
    fun start(strokes: List<Stroke>, memoDay: String) {
        if (strokes.isEmpty()) {
            SnackState.globalSnackFlow.tryEmit(SnackConf(text = NO_STROKES, duration = SnackConf.DURATION_STANDARD))
            return
        }
        val request = Job(strokes, LocalDate.parse(memoDay))
        AiFeature.start(sendsCalendar = false) { job = request }
    }

    fun review(proposals: List<Proposal>, readAs: String, memoDay: String) {
        job = Job(emptyList(), LocalDate.parse(memoDay), ScheduleResult.Found(readAs, proposals))
    }

    fun close() {
        job = null
    }
}

@Composable
fun ScheduleHost() {
    val job = ScheduleUi.job ?: return
    InkaTheme { androidx.compose.runtime.key(job) { ScheduleFlow(job) } }
}

private sealed interface Screen {
    data object Reading : Screen
    data class Review(val readAs: String) : Screen
    data class Nothing(val message: String, val title: String) : Screen
    data class Failed(val message: String) : Screen
}

/** Marks a draft for Add Event's "check the details" line. */
const val FROM_HANDWRITING = "handwriting"

private const val NO_DATE = "No date or time found."
private const val OFFLINE = "BooxDay needs a connection to read this. Your notes are safe."
private const val NO_STROKES = "Select some handwriting first."

@Composable
private fun ScheduleFlow(job: ScheduleUi.Job) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val eventsViewModel: EventsViewModel = viewModel()
    var screen by remember { mutableStateOf<Screen>(Screen.Reading) }
    var attempt by remember { mutableIntStateOf(0) }
    val items = remember { mutableStateMapOf<String, Proposal>() }
    val overlaps = remember { mutableStateMapOf<String, String>() }
    var order by remember { mutableStateOf<List<String>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var editing by remember { mutableStateOf<String?>(null) }
    var pickingDate by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val memoDay = job.memoDay.toString()
    val target = remember { EventTargets.current() }
    val perPage = if (LayoutProfiles.isNarrowScreen) 2 else if (LayoutProfiles.isSmallTablet) 3 else 4

    LaunchedEffect(attempt) {
        screen = Screen.Reading
        when (val result = job.ready ?: ScheduleItemsService.read(job.strokes, job.memoDay)) {
            is ScheduleResult.Found -> {
                result.proposals.forEach { items[it.id] = it }
                order = result.proposals.map { it.id }
                screen = Screen.Review(result.readAs)
            }
            is ScheduleResult.NothingDated -> screen = Screen.Nothing(NO_DATE, result.readAs.lineSequence().firstOrNull().orEmpty())
            ScheduleResult.NoStrokes -> screen = Screen.Nothing(NO_STROKES, "")
            ScheduleResult.Offline -> screen = Screen.Nothing(OFFLINE, "")
            is ScheduleResult.Failed -> screen = Screen.Failed(result.message)
        }
    }

    // Overlaps are looked up on the tablet: the AI never sees the calendar here
    LaunchedEffect(items.values.toList()) {
        items.values.filterIsInstance<Proposal.Event>().forEach { event ->
            val others = event.date?.let { day ->
                eventsViewModel.shownEventsForDay(day).filter { !it.isAllDay }.map { other ->
                    val start = java.time.Instant.ofEpochMilli(other.startTime).atZone(java.time.ZoneId.systemDefault())
                    val end = java.time.Instant.ofEpochMilli(other.endTime ?: other.startTime).atZone(java.time.ZoneId.systemDefault())
                    Triple(other.title, start.hour * 60 + start.minute, end.hour * 60 + end.minute)
                }
            }.orEmpty()
            val hit = ProposalValidator.overlapping(event, others)
            if (hit != null) overlaps[event.id] = hit else overlaps.remove(event.id)
        }
    }

    fun openByHand(title: String) {
        ScheduleUi.close()
        MainScreenRequests.request(MainScreenRequest.NewEventFrom(ParsedEventData(title = title, date = memoDay, confidence = 1f)))
    }

    fun add() {
        val kept = order.mapNotNull { items[it] }.filter { it.accepted }
        if (kept.isEmpty() || saving) return
        saving = true
        val events = kept.filterIsInstance<Proposal.Event>()
        val tasks = kept.filterIsInstance<Proposal.Task>()
        FullScreenState.exitThen {
            scope.launch {
                events.forEach { event ->
                    EventDraftSaver.create(eventsViewModel, ProposalApplier.draft(event, memoDay, target), successText = null) {}
                }
                withContext(Dispatchers.IO) {
                    ProposalApplier.addTasks(TaskRepository(context.applicationContext), tasks, memoDay, GlobalAppSettings.tasksCanvasHeight)
                }
                val first = (kept.first() as? Proposal.Event)?.date ?: (kept.first() as? Proposal.Task)?.day ?: memoDay
                val label = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()).format(LocalDate.parse(first))
                SnackState.globalSnackFlow.tryEmit(
                    SnackConf(
                        text = if (kept.size == 1) "Added to $label" else "Added ${kept.size}",
                        duration = SnackConf.DURATION_STANDARD,
                        actions = listOf("Open" to { MainScreenRequests.request(MainScreenRequest.ShowDay(first)) })
                    )
                )
                MainScreenRequests.request(MainScreenRequest.ReloadTasks)
                ScheduleUi.close()
            }
        }
    }

    val sheetWidth = (LayoutProfiles.screenWidthDp * EInkTheme.ModernDialog.widthFraction).dp
    val frame = Modifier.width(minOf(sheetWidth, 700.dp))
    Popup(popupPositionProvider = ScreenCentre, onDismissRequest = ScheduleUi::close, properties = PopupProperties(focusable = true)) {
        when (val now = screen) {
            Screen.Reading -> ProposalReadingCard(frame, onCancel = ScheduleUi::close)
            is Screen.Nothing -> ProposalNothingCard(now.message, frame, onClose = ScheduleUi::close, onByHand = { openByHand(now.title) })
            is Screen.Failed -> ProposalFailedCard(now.message, frame, onClose = ScheduleUi::close, onRetry = { attempt++ })
            is Screen.Review -> {
                val all = order.mapNotNull { items[it] }
                val pages = maxOf(1, (all.size + perPage - 1) / perPage)
                val shown = all.drop((page - 1) * perPage).take(perPage)
                val kept = all.count { it.accepted }
                val single = all.singleOrNull()
                ProposalReviewCard(
                    readAs = now.readAs.replace('\n', ' '),
                    cards = shown.map { cardFor(it, overlaps[it.id], target.label) },
                    page = page,
                    pages = pages,
                    mainLabel = when {
                        single is Proposal.Event -> "Add Event"
                        single is Proposal.Task -> "Add Task"
                        else -> "Add $kept"
                    },
                    modifier = frame,
                    showMoreDetails = single is Proposal.Event,
                    canAdd = kept > 0 && !saving,
                    onTick = { index ->
                        val item = shown[index]
                        items[item.id] = when (item) {
                            is Proposal.Event -> item.copy(accepted = !item.accepted)
                            is Proposal.Task -> item.copy(accepted = !item.accepted)
                        }
                    },
                    onKind = { index -> shown[index].let { items[it.id] = ProposalValidator.toggled(it) } },
                    onTitle = { index -> editing = shown[index].id },
                    onWhen = { index -> pickingDate = shown[index].id },
                    onPage = { page = it.coerceIn(1, pages) },
                    onCancel = ScheduleUi::close,
                    onMore = {
                        (single as? Proposal.Event)?.let { event ->
                            ScheduleUi.close()
                            MainScreenRequests.request(MainScreenRequest.NewEventFrom(parsedFor(event)))
                        }
                    },
                    onAdd = ::add
                )
            }
        }
    }

    editing?.let { id ->
        items[id]?.let { item ->
            ProposalEditPopup(item, onDone = { edited ->
                items[id] = edited
                editing = null
            }, onCancel = { editing = null })
        }
    }
    pickingDate?.let { id ->
        val item = items[id]
        TaskDatePickerDialog(
            title = "Pick the day",
            currentDate = (item as? Proposal.Event)?.date ?: (item as? Proposal.Task)?.day ?: memoDay,
            actionButtonLabel = "Use",
            onDateSelected = { date ->
                items[id] = when (item) {
                    is Proposal.Event -> item.copy(date = date, needsCheck = null)
                    is Proposal.Task -> item.copy(day = date, dueDay = item.dueDay?.let { date }, needsCheck = null)
                    null -> return@TaskDatePickerDialog
                }
                pickingDate = null
            },
            onDismiss = { pickingDate = null }
        )
    }
}

private fun parsedFor(event: Proposal.Event) = ParsedEventData(
    title = event.title,
    date = event.date,
    startHour = event.startMinute?.let { it / 60 },
    startMinute = event.startMinute?.let { it % 60 },
    endHour = event.endMinute?.let { it / 60 },
    endMinute = event.endMinute?.let { it % 60 },
    isAllDay = event.allDay,
    location = event.location,
    confidence = 0f,
    originalIntent = FROM_HANDWRITING
)

private fun dayLabel(day: String?): String =
    day?.let { runCatching { DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()).format(LocalDate.parse(it)) }.getOrNull() } ?: "No date"

internal fun cardFor(item: Proposal, overlap: String?, targetLabel: String?): ProposalCardUi {
    val use24 = GlobalAppSettings.current.timeFormat == AppSettings.TimeFormat.TwentyFourHour
    fun time(minute: Int) = WeekLayoutSpec.timeText(minute, use24, compact = false)
    return when (item) {
        is Proposal.Event -> ProposalCardUi(
            kind = "Event",
            title = item.title,
            whenLine = dayLabel(item.date) + when {
                item.allDay -> " · All day"
                item.startMinute != null && item.endMinute != null -> " · ${time(item.startMinute)} – ${time(item.endMinute)}"
                else -> ""
            },
            target = targetLabel,
            note = overlap?.let { "Overlaps $it" },
            check = item.needsCheck,
            ticked = item.accepted
        )
        is Proposal.Task -> ProposalCardUi(
            kind = "Task",
            title = item.text,
            whenLine = dayLabel(item.dueDay ?: item.day) + (item.reminderMinute?.let { " · reminder ${time(it)}" } ?: ""),
            check = item.needsCheck,
            ticked = item.accepted
        )
    }
}

/** The words, and an event's times. The keyboard pushes it up, never over it. */
@Composable
private fun ProposalEditPopup(item: Proposal, onDone: (Proposal) -> Unit, onCancel: () -> Unit) {
    var title by remember { mutableStateOf(if (item is Proposal.Event) item.title else (item as Proposal.Task).text) }
    val event = item as? Proposal.Event
    var start by remember { mutableIntStateOf(event?.startMinute ?: 9 * 60) }
    var end by remember { mutableIntStateOf(event?.endMinute ?: 10 * 60) }
    val keyboard = keyboardHeight()
    Popup(popupPositionProvider = ScreenCentre, onDismissRequest = onCancel, properties = PopupProperties(focusable = true)) {
        Column(
            modifier = Modifier
                .padding(bottom = keyboard)
                .width(EInkTheme.PopupOverlay.width)
                .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                .padding(EInkTheme.PopupOverlay.padding),
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
        ) {
            Text(text = "Change", fontSize = EInkTheme.Typography.titleMedium, fontWeight = FontWeight.Bold, color = EInkTheme.Colors.primary)
            OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (event != null && !event.allDay) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Start", fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.primary, modifier = Modifier.width(44.dp))
                    TimeSelector(start / 60, start % 60, { start = it * 60 + start % 60 }, { start = start / 60 * 60 + it }, compact = true)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "End", fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.primary, modifier = Modifier.width(44.dp))
                    TimeSelector(end / 60, end % 60, { end = it * 60 + end % 60 }, { end = end / 60 * 60 + it }, compact = true)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                EinkSecondaryButton(text = "Cancel", onClick = onCancel)
                Spacer(Modifier.weight(1f))
                ActionButton(text = "Save", enabled = title.isNotBlank(), onClick = {
                    onDone(
                        when (item) {
                            is Proposal.Event -> item.copy(
                                title = title,
                                startMinute = start.takeIf { !item.allDay },
                                endMinute = if (item.allDay) null else ProposalValidator.endOf(start, end)
                            )
                            is Proposal.Task -> item.copy(text = title)
                        }
                    )
                })
            }
            Spacer(Modifier.height(0.dp))
        }
    }
}
