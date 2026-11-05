package com.boox.day.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.boox.day.data.db.CalendarEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** Things only MainScreen can do, asked for from outside it (Nov 2026). */
sealed interface MainScreenRequest {
    /** Shows the day on the current tab and moves the month grid to it. */
    data class ShowDay(val date: String) : MainScreenRequest
    data class ShowEvent(val event: CalendarEvent) : MainScreenRequest
    /** The Add Event dialog, with this date filled in. */
    data class NewEvent(val date: String) : MainScreenRequest
    /** The Tasks page on screen may have changed. */
    data object ReloadTasks : MainScreenRequest
}

/** Each request is handled once, also when it comes before MainScreen is up (as [TaskFocusRequests]). */
object MainScreenRequests {
    private val _pending = MutableStateFlow<MainScreenRequest?>(null)
    val pending: StateFlow<MainScreenRequest?> = _pending

    fun request(request: MainScreenRequest) {
        _pending.value = request
    }

    fun consume(): MainScreenRequest? = _pending.getAndUpdate { null }
}

/** MainScreen's listener, in a composable of its own: MainScreen is at its size limit. */
@Composable
fun MainScreenRequestsCollector(handle: (MainScreenRequest) -> Unit) {
    val current by rememberUpdatedState(handle)
    LaunchedEffect(Unit) {
        MainScreenRequests.pending.collect { pending ->
            if (pending != null) MainScreenRequests.consume()?.let { current(it) }
        }
    }
}
