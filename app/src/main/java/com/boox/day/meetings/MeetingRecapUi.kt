package com.boox.day.meetings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.actionitems.ActionItemsUi
import com.boox.day.ai.MeetingRecap
import com.boox.day.ai.MeetingRecapResult
import com.boox.day.ai.MeetingRecapService
import com.boox.day.api.ai.AiProblem
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.CalendarEvent
import com.boox.day.io.MeetingExport
import com.boox.day.proposals.ProposalValidator
import com.boox.day.proposals.ScheduleItemAnswer
import com.boox.day.proposals.ScheduleUi
import com.boox.day.security.UserOpenRouterKey
import com.boox.day.ui.AiKeyGate
import com.boox.day.ui.CalendarAiNotice
import com.boox.day.ui.ScreenCentre
import com.boox.day.ui.SnackConf
import com.boox.day.ui.SnackState
import com.boox.day.ui.theme.InkaTheme
import com.boox.day.ui.theme.LayoutProfiles
import com.boox.day.utils.DateTimeFormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Meeting Recap (Nov 2026): opened from the Meeting notes card. */
object MeetingRecapUi {
    var event by mutableStateOf<CalendarEvent?>(null)
        private set

    val isOpen: Boolean get() = event != null

    fun open(meeting: CalendarEvent) {
        event = meeting
    }

    fun close() {
        event = null
    }
}

@Composable
fun MeetingRecapHost() {
    val event = MeetingRecapUi.event ?: return
    InkaTheme { key(event.id) { RecapFlow(event) } }
}

private sealed interface RecapScreen {
    data object Loading : RecapScreen
    data class Reading(val pages: Int) : RecapScreen
    data class Show(val recap: MeetingRecap, val changed: Boolean) : RecapScreen
    data class Blocked(val message: String) : RecapScreen
    data object AskUpdate : RecapScreen
}

private const val AI_OFF = "Recap needs your AI, and AI is off in Settings. Your notes are safe, and Action Items and Export work without it."
private const val OFFLINE = "Recap needs a connection. Your notes are safe, and Action Items and Export work without it."
private const val NO_NOTES = "There's nothing in these notes to read yet."
private const val UPDATE_ASK = "Update Recap writes a new recap. If you saved this one and changed it by hand, your changes will be replaced."

/** Rows of the sheet that fit on a page, in rough units of one text line. */
internal fun recapPages(recap: MeetingRecap, capacity: Int): List<RecapPageUi> {
    val pages = mutableListOf<RecapPageUi>()
    var page = RecapPageUi()
    var used = 0
    fun room(cost: Int) {
        if (used + cost > capacity && used > 0) {
            pages += page
            page = RecapPageUi()
            used = 0
        }
        used += cost
    }
    room(2 + recap.summary.length / 60)
    page = page.copy(summary = recap.summary)
    recap.decisions.forEachIndexed { index, item ->
        room(if (index == 0) 2 else 1)
        page = page.copy(decisions = page.decisions + item)
    }
    recap.actionItems.forEachIndexed { index, item ->
        room(if (index == 0) 4 else 2)
        val due = item.dueDate.takeIf { it.isNotBlank() }?.let { shortDay(it) }
        page = page.copy(actions = page.actions + RecapActionUi(item.text, due))
    }
    recap.followUps.forEachIndexed { index, item ->
        room(if (index == 0) 2 else 1)
        page = page.copy(followUps = page.followUps + item)
    }
    recap.openQuestions.forEachIndexed { index, item ->
        room(if (index == 0) 2 else 1)
        page = page.copy(questions = page.questions + item)
    }
    pages += page
    return pages
}

private fun shortDay(day: String): String =
    runCatching { DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()).format(LocalDate.parse(day)) }.getOrDefault(day)

internal fun meetingLine(event: CalendarEvent): String {
    val day = runCatching { DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()).format(LocalDate.parse(event.dayId)) }.getOrDefault(event.dayId)
    val time = if (event.isAllDay) "All day" else DateTimeFormatUtils.formatTimeRange(event.startTime, event.endTime)
    return listOfNotNull(day, time, event.location?.takeIf { it.isNotBlank() }).joinToString(" · ")
}

@Composable
private fun RecapFlow(event: CalendarEvent) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<RecapScreen>(RecapScreen.Loading) }
    var attempt by remember { mutableIntStateOf(0) }
    var force by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(1) }
    var skipped by remember { mutableIntStateOf(0) }
    var noteCount by remember { mutableIntStateOf(0) }

    suspend fun generate(pages: MeetingPages.Pages, fingerprint: String) {
        screen = RecapScreen.Reading(pages.notes.size)
        try {
            val read = MeetingPages.readText(context, pages.date, pages.notes)
            skipped = read.skipped
            if (read.text.isBlank()) {
                screen = RecapScreen.Blocked(NO_NOTES)
                return
            }
            screen = when (val result = MeetingRecapService.make(context, event.id, event.title, pages.date, read.text, fingerprint)) {
                is MeetingRecapResult.Done -> RecapScreen.Show(result.recap, changed = false)
                MeetingRecapResult.Offline -> RecapScreen.Blocked(OFFLINE)
                is MeetingRecapResult.Failed -> RecapScreen.Blocked(result.message)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            screen = when (MeetingRecapService.failed(e.message ?: AiProblem.MODEL_FAILED.message)) {
                MeetingRecapResult.Offline -> RecapScreen.Blocked(OFFLINE)
                else -> RecapScreen.Blocked(e.message ?: AiProblem.MODEL_FAILED.message)
            }
        }
    }

    LaunchedEffect(attempt) {
        val pages = MeetingPages.of(context, event.id)
        if (pages == null || pages.notes.isEmpty()) {
            screen = RecapScreen.Blocked(NO_NOTES)
            return@LaunchedEffect
        }
        noteCount = pages.notes.size
        saved = pages.recap.isNotEmpty()
        val fingerprint = MeetingPages.fingerprint(context, pages.notes)
        val cached = withContext(Dispatchers.IO) { MeetingRecapService.cached(context, event.id) }
        when {
            // Unchanged notes: no AI call, and no key needed
            cached != null && !force -> screen = RecapScreen.Show(cached.first, changed = cached.second != fingerprint)
            !GlobalAppSettings.current.aiEnabled -> screen = RecapScreen.Blocked(AI_OFF)
            !UserOpenRouterKey.hasKey -> {
                MeetingRecapUi.close()
                AiKeyGate.requireKey { MeetingRecapUi.open(event) }
            }
            !CalendarAiNotice.awaitAllowed() -> MeetingRecapUi.close()
            else -> generate(pages, fingerprint)
        }
    }

    fun meeting(recap: MeetingRecap?, pages: MeetingPages.Pages) =
        MeetingExport.Meeting(event.title, meetingLine(event), pages.date, pages.notes, recap)

    fun tasks(recap: MeetingRecap, date: String) {
        val today = LocalDate.now()
        val memoDay = LocalDate.parse(date)
        val proposals = recap.actionItems.mapIndexed { index, action ->
            ProposalValidator.check(
                ScheduleItemAnswer(kind = "task", title = action.text, date = action.dueDate, isDeadline = true),
                index.toString(), memoDay, today
            )
        }
        ScheduleUi.review(proposals, event.title, date)
    }

    val width = Modifier.width((LayoutProfiles.screenWidthDp * if (LayoutProfiles.isNarrowScreen) 0.94f else 0.85f).dp.coerceAtMost(700.dp))
    val title = event.title
    Popup(popupPositionProvider = ScreenCentre, onDismissRequest = MeetingRecapUi::close, properties = PopupProperties(focusable = true)) {
        when (val now = screen) {
            RecapScreen.Loading -> Unit
            is RecapScreen.Reading -> MeetingReadingCard(
                "Recap · $title",
                "Your AI is reading ${now.pages} ${if (now.pages == 1) "page" else "pages"} of notes…",
                width,
                onCancel = MeetingRecapUi::close
            )
            is RecapScreen.Blocked -> MeetingRecapBlockedCard(
                title, now.message, width,
                onClose = MeetingRecapUi::close,
                onExport = {
                    scope.launch {
                        MeetingPages.of(context, event.id)?.let { pages -> exportNow(context, meeting(null, pages)) }
                        MeetingRecapUi.close()
                    }
                },
                onActionItems = {
                    scope.launch {
                        MeetingPages.of(context, event.id)?.let { pages ->
                            ActionItemsUi.open(pages.date, pages.notes, title = "From your notes · $title")
                        }
                        MeetingRecapUi.close()
                    }
                }
            )
            RecapScreen.AskUpdate -> MeetingAskCard(
                "Update Recap?", UPDATE_ASK, width,
                onCancel = { attempt++ },
                onUpdate = {
                    force = true
                    attempt++
                }
            )
            is RecapScreen.Show -> {
                val capacity = (((LayoutProfiles.screenHeightDp * 0.82f) - 190f) / 26f).toInt()
                val pages = recapPages(now.recap, capacity)
                val shown = page.coerceIn(1, pages.size)
                val line = "${meetingLine(event).substringBeforeLast(" · ").ifBlank { meetingLine(event) }} · from $noteCount ${if (noteCount == 1) "page" else "pages"}" +
                    if (skipped > 0) " (first ${MeetingPages.MAX_PAGES} read)" else ""
                MeetingRecapCard(
                    title = title,
                    line = line,
                    content = pages[shown - 1],
                    page = shown,
                    pages = pages.size,
                    modifier = width,
                    changedNotes = now.changed,
                    saved = saved && !now.changed,
                    onClose = MeetingRecapUi::close,
                    onPage = { page = it.coerceIn(1, pages.size) },
                    onUpdate = { screen = RecapScreen.AskUpdate },
                    onAddTasks = {
                        scope.launch {
                            MeetingPages.of(context, event.id)?.let { tasks(now.recap, it.date) }
                            MeetingRecapUi.close()
                        }
                    },
                    onShare = {
                        scope.launch {
                            MeetingPages.of(context, event.id)?.let { MeetingExport.share(context, meeting(now.recap, it)) }
                        }
                    },
                    onSave = {
                        scope.launch {
                            MeetingPages.of(context, event.id)?.let { MeetingRecapSaver.save(context, event.id, title, it, now.recap) }
                            saved = true
                            com.boox.day.ui.MainScreenRequests.request(com.boox.day.ui.MainScreenRequest.ReloadMemoPages)
                            SnackState.globalSnackFlow.tryEmit(SnackConf(text = "Saved after your notes", duration = SnackConf.DURATION_STANDARD))
                        }
                    }
                )
            }
        }
    }
}

/** Saves the meeting's PDF and says where, with Share beside it. */
internal suspend fun exportNow(context: android.content.Context, meeting: MeetingExport.Meeting) {
    val saved = runCatching { MeetingExport.save(context, meeting) }
    SnackState.globalSnackFlow.tryEmit(
        SnackConf(
            text = if (saved.isSuccess) "Saved to Documents/BooxDay/Meetings" else "Couldn't save the PDF",
            duration = SnackConf.DURATION_STANDARD
        )
    )
}
