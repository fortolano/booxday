package com.boox.day.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.theme.LayoutProfiles
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boox.day.R
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.state.CalendarSource
import com.boox.day.ui.state.CalendarSourceFilter
import com.boox.day.ui.theme.EInkTheme

/**
 * The events filter as one button for the Go 6 (Nov 2026, owner's option A): its panel is too
 * narrow for an icon per source beside the Events and Due tabs. The button opens a short list with
 * a tick per source. Black when a source is hidden, so a filter in use shows. Picture approved
 * 2 Nov 2026 (EventsHeaderFitRenderTest).
 */
@Composable
internal fun CalendarSourceFilterButton(filtered: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(if (filtered) EInkTheme.Colors.primary else Color.White, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .border(1.dp, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .noRippleClickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.FilterList, contentDescription = "Calendars shown",
            tint = if (filtered) Color.White else EInkTheme.Colors.primary, modifier = Modifier.size(20.dp)
        )
    }
}

/** The list the button opens: one row per connected source, ticked when its events show. */
@Composable
internal fun CalendarSourceFilterList(
    filter: CalendarSourceFilter,
    hasGoogleAccount: Boolean,
    hasOutlookAccount: Boolean,
    hasCalDavAccount: Boolean,
    onToggleSource: (CalendarSource) -> Unit
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .background(Color.White, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .border(1.dp, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .padding(vertical = 4.dp)
    ) {
        Text(
            "Show events from", fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.darkGray,
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp)
        )
        FilterRow("This device", filter.showLocal, { Icon(Icons.Outlined.CalendarToday, null, tint = EInkTheme.Colors.primary, modifier = Modifier.size(22.dp)) }) {
            onToggleSource(CalendarSource.LOCAL)
        }
        if (hasGoogleAccount) FilterRow("Google", filter.showGoogle, {
            Icon(painterResource(R.drawable.ic_google_calendar), null, tint = Color.Unspecified, modifier = Modifier.size(22.dp))
        }) { onToggleSource(CalendarSource.GOOGLE) }
        if (hasOutlookAccount) FilterRow("Outlook", filter.showOutlook, {
            Icon(painterResource(R.drawable.ic_outlook), null, tint = Color.Unspecified, modifier = Modifier.size(22.dp))
        }) { onToggleSource(CalendarSource.OUTLOOK) }
        if (hasCalDavAccount) FilterRow(CALDAV_TAB_LABEL, filter.showCalDav, { CalDavMark(22.dp) }) {
            onToggleSource(CalendarSource.CALDAV)
        }
    }
}

@Composable
private fun FilterRow(label: String, shown: Boolean, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = EInkTheme.Global.touchTarget).noRippleClickable { onClick() }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            if (shown) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, contentDescription = null,
            tint = EInkTheme.Colors.primary, modifier = Modifier.size(22.dp)
        )
        icon()
        Text(label, fontSize = EInkTheme.Typography.bodyMedium, fontWeight = FontWeight.Medium, color = EInkTheme.Colors.primary)
    }
}

/**
 * The events panel's source filter: an icon per source, or on narrow screens (Go 6, which can't
 * fit them beside the tabs) the one button and its list. Same parameters as [CalendarSourceFilterIcons].
 */
@Composable
fun CalendarSourceFilterControl(
    filter: CalendarSourceFilter,
    onToggleSource: (CalendarSource) -> Boolean,
    hasGoogleAccount: Boolean,
    hasOutlookAccount: Boolean,
    hasCalDavAccount: Boolean = false,
    selectedDayOfMonth: Int,
    isCompact: Boolean = false,
    large: Boolean = false
) {
    if (!LayoutProfiles.isNarrowScreen || large) {
        CalendarSourceFilterIcons(
            filter = filter, onToggleSource = onToggleSource, hasGoogleAccount = hasGoogleAccount,
            hasOutlookAccount = hasOutlookAccount, hasCalDavAccount = hasCalDavAccount,
            selectedDayOfMonth = selectedDayOfMonth, isCompact = isCompact, large = large
        )
        return
    }
    var open by remember { mutableStateOf(false) }
    val filtered = !filter.showLocal || (hasGoogleAccount && !filter.showGoogle) ||
        (hasOutlookAccount && !filter.showOutlook) || (hasCalDavAccount && !filter.showCalDav)
    Box {
        CalendarSourceFilterButton(filtered = filtered) { open = !open }
        if (open) {
            val below = with(LocalDensity.current) { 32.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, below),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true)
            ) {
                CalendarSourceFilterList(filter, hasGoogleAccount, hasOutlookAccount, hasCalDavAccount) { onToggleSource(it) }
            }
        }
    }
}
