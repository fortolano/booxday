package com.boox.day.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import com.boox.day.meetings.MeetingRecapHost
import com.boox.day.meetings.MeetingRecapUi
import com.boox.day.proposals.ScheduleHost
import com.boox.day.proposals.ScheduleUi
import com.boox.day.week.WeekHost
import com.boox.day.week.WeekUi

/**
 * Overlays that MainScreen only knows as one line (Nov 2026): the pen goes off while any is open,
 * and new overlays are added here, not to MainScreen.
 */
object AppOverlays {
    val isOpen: Boolean get() = WeekUi.isOpen || ScheduleUi.isOpen || MeetingRecapUi.isOpen
}

@Composable
fun AppOverlaysHost() {
    WeekHost()
    ScheduleHost()
    MeetingRecapHost()
}

/**
 * The middle of the screen. Alignment.Center means the middle of the parent, which for something inside
 * a scrolling or low card put popups at the bottom of the screen.
 */
object ScreenCentre : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset((windowSize.width - popupContentSize.width) / 2, (windowSize.height - popupContentSize.height) / 2)
}

/** The whole window, for a full-page overlay. */
object ScreenTopLeft : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset(0, 0)
}
