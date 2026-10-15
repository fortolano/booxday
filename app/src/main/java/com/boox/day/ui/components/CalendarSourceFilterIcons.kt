package com.boox.day.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boox.day.R
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.state.CalendarSource
import com.boox.day.ui.state.CalendarSourceFilter
import com.boox.day.ui.theme.EInkTheme

/**
 * Calendar source filter icons component.
 * Displays toggleable filter icons for Local, Google, and Outlook calendars.
 *
 * - Only shows icons for available sources (Local is always available, Google/Outlook only when connected)
 * - Selected icons have full color, white background, black border
 * - Unselected icons are grayed out with light background
 * - Local calendar icon displays the selected day number inside (like toolbar's Today button)
 * - E-Ink optimized: No animations, high contrast states
 *
 * @param filter Current filter state
 * @param onToggleSource Callback when a source is toggled (returns false if last source)
 * @param hasGoogleAccount Whether Google Calendar is connected
 * @param hasOutlookAccount Whether Outlook Calendar is connected
 * @param selectedDayOfMonth The day number to display inside the local calendar icon (1-31)
 * @param isCompact Unused - kept for API compatibility (collapsed/expanded use same sizing)
 * @param modifier Modifier for the row
 */
@Composable
fun CalendarSourceFilterIcons(
    filter: CalendarSourceFilter,
    onToggleSource: (CalendarSource) -> Boolean,
    hasGoogleAccount: Boolean,
    hasOutlookAccount: Boolean,
    selectedDayOfMonth: Int,  // Day number to show inside local calendar icon
    isCompact: Boolean = false,  // Unused - unified sizing for both modes
    modifier: Modifier = Modifier,
    // Finger-size icons (Go 6 Events/Due popup, Oct 2026): 44dp targets 10dp apart
    large: Boolean = false
) {
    // Sizing for filter icons
    val containerSize = if (large) 44.dp else 30.dp
    val localIconSize = if (large) 30.dp else 23.dp      // Slightly smaller than toolbar (24dp)
    val providerIconSize = if (large) 32.dp else 25.5.dp // Slightly larger for Google/Outlook logos
    val spacing = if (large) 10.dp else 5.0.dp           // Increased from 4.5dp for better touch separation
    val cornerRadius = EInkTheme.Global.cornerRadius

    Row(
        modifier = modifier.padding(start = 0.5.dp),  // Reduced from 1dp for tighter coupling with tabs
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Local calendar filter (always shown) - shows selected day number inside icon
        LocalCalendarFilterIcon(
            isSelected = filter.showLocal,
            onClick = { onToggleSource(CalendarSource.LOCAL) },
            dayOfMonth = selectedDayOfMonth,
            containerSize = containerSize,
            iconSize = localIconSize,
            cornerRadius = cornerRadius,
            large = large
        )

        // Google calendar filter (only when account connected)
        if (hasGoogleAccount) {
            FilterIconButton(
                iconRes = R.drawable.ic_google_calendar,
                contentDesc = "Google calendar",
                isSelected = filter.showGoogle,
                onClick = { onToggleSource(CalendarSource.GOOGLE) },
                containerSize = containerSize,
                iconSize = providerIconSize,
                cornerRadius = cornerRadius,
                tintColor = null // Multi-color icon - no tint when selected
            )
        }

        // Outlook calendar filter (only when account connected)
        if (hasOutlookAccount) {
            FilterIconButton(
                iconRes = R.drawable.ic_outlook,
                contentDesc = "Outlook calendar",
                isSelected = filter.showOutlook,
                onClick = { onToggleSource(CalendarSource.OUTLOOK) },
                containerSize = containerSize,
                iconSize = providerIconSize,
                cornerRadius = cornerRadius,
                tintColor = null // Colored icon - no tint when selected
            )
        }
    }
}

/**
 * Individual filter icon button with selected/unselected states.
 * E-Ink optimized with high contrast visual feedback.
 */
@Composable
private fun FilterIconButton(
    iconRes: Int,
    contentDesc: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    containerSize: Dp,
    iconSize: Dp,
    cornerRadius: Dp,
    tintColor: Color? // null = use original icon colors
) {
    val shape = RoundedCornerShape(cornerRadius)

    // Visual states for E-Ink clarity (no border for cleaner look)
    val backgroundColor = if (isSelected) Color.White else Color(0xFFF0F0F0)
    val iconAlpha = if (isSelected) 1f else 0.5f

    // Determine icon tint
    val actualTint = when {
        !isSelected -> EInkTheme.Colors.gray // Gray when unselected
        tintColor != null -> tintColor // Use specified tint
        else -> Color.Unspecified // No tint (multi-color icons)
    }

    Box(
        modifier = Modifier
            .size(containerSize)
            .background(backgroundColor, shape)
            .noRippleClickable { onClick() }
            .semantics {
                role = Role.Checkbox
                contentDescription = contentDesc
                stateDescription = if (isSelected) "selected" else "unselected"
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null, // Handled by parent semantics
            modifier = Modifier
                .size(iconSize)
                .alpha(iconAlpha),
            tint = actualTint
        )
    }
}

/**
 * Local calendar filter icon with day number overlay.
 * Similar to the toolbar's Today button, shows the selected day inside the calendar icon.
 */
@Composable
private fun LocalCalendarFilterIcon(
    isSelected: Boolean,
    onClick: () -> Unit,
    dayOfMonth: Int,
    containerSize: Dp,
    iconSize: Dp,
    cornerRadius: Dp,
    large: Boolean = false
) {
    val shape = RoundedCornerShape(cornerRadius)

    // Visual states for E-Ink clarity
    val backgroundColor = if (isSelected) Color.White else Color(0xFFF0F0F0)
    val iconAlpha = if (isSelected) 1f else 0.5f
    val iconTint = if (isSelected) Color.Black else EInkTheme.Colors.gray
    val textColor = if (isSelected) Color.Black else EInkTheme.Colors.gray

    Box(
        modifier = Modifier
            .size(containerSize)
            .background(backgroundColor, shape)
            .noRippleClickable { onClick() }
            .semantics {
                role = Role.Checkbox
                contentDescription = "Local calendar, day $dayOfMonth"
                stateDescription = if (isSelected) "selected" else "unselected"
            },
        contentAlignment = Alignment.Center
    ) {
        // Calendar icon
        Icon(
            painter = painterResource(id = R.drawable.calendar_today_thin),
            contentDescription = null,
            modifier = Modifier
                .size(iconSize)
                .alpha(iconAlpha),
            tint = iconTint
        )
        // Day number positioned inside the calendar icon (matching toolbar style)
        Text(
            text = dayOfMonth.toString(),
            fontSize = if (large) EInkTheme.Typography.bodySmall else EInkTheme.Typography.labelSmall,  // 10.sp
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier
                .alpha(iconAlpha)
                .padding(top = if (large) 8.dp else 6.dp)  // Adjusted for 23dp icon (vs 7dp for 24dp toolbar icon)
        )
    }
}

