package com.boox.day.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * "Show this task" (plan T3, Oct 2026): open its day on the Tasks tab, on its page, with the
 * row highlighted. [taskId] may be null (then only the day opens).
 */
data class TaskFocusRequest(val taskId: String?, val dayDate: String)

/** Hand-off from places outside MainScreen (e.g. a reminder notification tap) to MainScreen. */
object TaskFocusRequests {
    private val _pending = MutableStateFlow<TaskFocusRequest?>(null)
    val pending: StateFlow<TaskFocusRequest?> = _pending

    fun request(request: TaskFocusRequest) {
        _pending.value = request
    }

    /** Takes the pending request (each request is handled once). */
    fun consume(): TaskFocusRequest? = _pending.getAndUpdate { null }
}
