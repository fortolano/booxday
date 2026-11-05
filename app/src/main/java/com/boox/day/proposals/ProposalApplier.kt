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

    /** Adds the tasks that are ticked, each on its day, with its due day and reminder. Off the main thread. */
    suspend fun addTasks(repository: TaskRepository, tasks: List<Proposal.Task>, memoDay: String, canvasHeight: Float): Int {
        var added = 0
        for (task in tasks.filter { it.accepted }) {
            val day = task.day ?: memoDay
            val result = repository.addConvertedTasksToDate(
                tasks = listOf(ConvertedTask(text = task.text.trim(), targetDateOverride = day, dueDateOverride = TaskDueDates.startOfDayMillis(task.dueDay))),
                defaultTargetDate = day,
                canvasHeight = canvasHeight
            )
            added += result.tasksAdded
            val id = result.taskIds.firstOrNull()
            if (id != null && task.reminderMinute != null) repository.setReminderMinuteOfDay(id, task.reminderMinute)
        }
        return added
    }
}
