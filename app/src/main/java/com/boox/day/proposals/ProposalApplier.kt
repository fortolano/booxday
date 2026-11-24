package com.boox.day.proposals

import com.boox.day.ai.ConvertedTask
import com.boox.day.data.db.TaskDueDates
import com.boox.day.data.db.TaskRepository
import com.boox.day.events.EventDraft
import com.boox.day.events.EventTarget

/** Makes what the user kept. Unticked items make nothing. */
object ProposalApplier {
    /** The draft for an event; an all-day one has no end, as in Add Event. */
    fun draft(event: Proposal.Event, memoDay: String, target: EventTarget): EventDraft {
        val start = event.startMinute ?: 0
        val end = event.endMinute
        val calendar = target.calendar
        return EventDraft(
            calendarId = calendar?.id.orEmpty(),
            title = event.title.trim(),
            date = event.date ?: memoDay,
            startHour = start / 60,
            startMinute = start % 60,
            endHour = end?.let { it / 60 },
            endMinute = end?.let { it % 60 },
            isAllDay = event.allDay,
            location = event.location,
            description = null,
            attendees = null,  // never from the AI
            syncEnabled = target.syncEnabled,
            syncProviderId = calendar?.providerId,
            syncAccountId = calendar?.accountId,
            localCalendarId = target.localCalendarId
        )
    }

    /** Adds one accepted task, returning true only when it was actually inserted into a row. */
    suspend fun addTask(repository: TaskRepository, task: Proposal.Task, memoDay: String, canvasHeight: Float): Boolean {
        if (!task.accepted) return false
        val day = task.day ?: memoDay
        val result = repository.addConvertedTasksToDate(
            tasks = listOf(ConvertedTask(text = task.text.trim(), targetDateOverride = day, dueDateOverride = TaskDueDates.startOfDayMillis(task.dueDay))),
            defaultTargetDate = day,
            canvasHeight = canvasHeight
        )
        val id = result.taskIds.firstOrNull()
        if (id != null && task.reminderMinute != null) repository.setReminderMinuteOfDay(id, task.reminderMinute)
        return result.tasksAdded > 0
    }

    /** Adds the tasks that are ticked, each on its day, with its due day and reminder. Off the main thread. */
    suspend fun addTasks(repository: TaskRepository, tasks: List<Proposal.Task>, memoDay: String, canvasHeight: Float): Int {
        var added = 0
        for (task in tasks.filter { it.accepted }) {
            if (addTask(repository, task, memoDay, canvasHeight)) added++
        }
        return added
    }

    /** Outcome of saving a batch of accepted proposals (Phase 1 H3). */
    data class BatchSaveResult(
        val succeededIds: List<String>,
        val failedIds: List<String>,
        val firstSavedDay: String?,
        val anyTaskAdded: Boolean,
        val lastError: String?,
    ) {
        val allSucceeded: Boolean get() = failedIds.isEmpty() && succeededIds.isNotEmpty()
    }

    /**
     * Runs each accepted proposal sequentially, collecting exact per-item success/failure so the UI
     * reports only confirmed saves and retains failed items + shared inbox for selective retry.
     */
    suspend fun applySelected(
        kept: List<Proposal>,
        memoDay: String,
        saveEvent: suspend (Proposal.Event) -> Pair<Boolean, String?>,
        saveTask: suspend (Proposal.Task) -> Boolean,
        saveNote: suspend () -> Boolean,
    ): BatchSaveResult {
        val succeededIds = mutableListOf<String>()
        val failedIds = mutableListOf<String>()
        var firstSavedDay: String? = null
        var anyTaskAdded = false
        var lastError: String? = null

        for (item in kept) {
            when (item) {
                is Proposal.Event -> {
                    val (ok, err) = runCatching { saveEvent(item) }.getOrElse { false to it.message }
                    if (ok) {
                        succeededIds += item.id
                        if (firstSavedDay == null) firstSavedDay = item.date ?: memoDay
                    } else {
                        failedIds += item.id
                        if (!err.isNullOrBlank()) lastError = err
                    }
                }
                is Proposal.Task -> {
                    val ok = runCatching { saveTask(item) }.getOrDefault(false)
                    if (ok) {
                        succeededIds += item.id
                        anyTaskAdded = true
                        if (firstSavedDay == null) firstSavedDay = item.day ?: memoDay
                    } else {
                        failedIds += item.id
                        lastError = lastError ?: "Couldn't add task"
                    }
                }
                is Proposal.Note -> {
                    val ok = runCatching { saveNote() }.getOrDefault(false)
                    if (ok) {
                        succeededIds += item.id
                        if (firstSavedDay == null) firstSavedDay = memoDay
                    } else {
                        failedIds += item.id
                        lastError = lastError ?: "Couldn't save the shared note"
                    }
                }
            }
        }
        return BatchSaveResult(succeededIds, failedIds, firstSavedDay, anyTaskAdded, lastError)
    }
}
