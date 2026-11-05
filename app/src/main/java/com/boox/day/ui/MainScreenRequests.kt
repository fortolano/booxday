package com.boox.day.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.model.ParsedEventData
import kotlinx.coroutines.channels.Channel

/** Things only MainScreen can do, asked for from outside it (Nov 2026). */
sealed interface MainScreenRequest {
    /** Shows the day on the current tab and moves the month grid to it. */
    data class ShowDay(val date: String) : MainScreenRequest
    data class ShowEvent(val event: CalendarEvent) : MainScreenRequest
    /** The Add Event dialog, with this date filled in. */
    data class NewEvent(val date: String) : MainScreenRequest
    /** The Add Event dialog, filled in from what was read. */
    data class NewEventFrom(val draft: ParsedEventData) : MainScreenRequest
    /** The Tasks page on screen may have changed. */
    data object ReloadTasks : MainScreenRequest
    /** Pages were added to a day off the canvas: the page counter has to hear of it. */
    data object ReloadMemoPages : MainScreenRequest
}

/** Each request is handled once and in order, also when it comes before MainScreen is up. */
object MainScreenRequests {
    private val queue = Channel<MainScreenRequest>(Channel.UNLIMITED)

    fun request(request: MainScreenRequest) {
        queue.trySend(request)
    }

    internal suspend fun next(): MainScreenRequest = queue.receive()
}

/** MainScreen's listener, in a composable of its own: MainScreen is at its size limit. */
@Composable
fun MainScreenRequestsCollector(handle: (MainScreenRequest) -> Unit) {
    val current by rememberUpdatedState(handle)
    LaunchedEffect(Unit) {
        while (true) current(MainScreenRequests.next())
    }
}
