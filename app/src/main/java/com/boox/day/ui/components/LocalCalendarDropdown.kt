package com.boox.day.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.boox.day.ui.noRippleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boox.day.data.db.LocalCalendar
import com.boox.day.ui.theme.EInkTheme

/**
 * Dropdown for selecting a local calendar.
 * Used in AddEditEventDialog for Standalone mode.
 *
 * E-Ink optimized: High contrast, no colors beyond grayscale.
 */
@Composable
fun LocalCalendarDropdown(
    calendars: List<LocalCalendar>,
    selectedCalendarId: String?,
    onCalendarSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    // Find selected calendar or default to first
    val selectedCalendar = calendars.find { it.id == selectedCalendarId }
        ?: calendars.find { it.isDefault }
        ?: calendars.firstOrNull()

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { if (enabled) expanded = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && calendars.isNotEmpty(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = EInkTheme.Button.textColor,
                disabledContentColor = EInkTheme.Colors.darkGray
            ),
            border = BorderStroke(
                EInkTheme.Button.borderWidth,
                if (enabled) EInkTheme.TextField.borderColor else EInkTheme.Colors.lightGray
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Calendar color indicator
                    if (selectedCalendar != null) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    selectedCalendar.colorHex?.let { parseColorHex(it) }
                                        ?: EInkTheme.Colors.darkGray
                                )
                                .border(1.dp, EInkTheme.Colors.primary, CircleShape)
                        )
                    }

                    Text(
                        text = selectedCalendar?.name ?: "Select calendar",
                        fontSize = 16.sp,
                        color = if (enabled) EInkTheme.Colors.primary else EInkTheme.Colors.gray
                    )

                    // Show "(default)" label
                    if (selectedCalendar?.isDefault == true) {
                        Text(
                            text = "(default)",
                            fontSize = EInkTheme.Typography.labelSmall,
                            color = EInkTheme.Colors.gray
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select",
                    modifier = Modifier.size(24.dp),
                    tint = if (enabled) EInkTheme.Colors.primary else EInkTheme.Colors.gray
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(EInkTheme.Colors.background)
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary)
                .heightIn(max = 250.dp)
        ) {
            calendars.forEach { calendar ->
                DropdownMenuItem(
                    onClick = {
                        onCalendarSelected(calendar.id)
                        expanded = false
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Calendar color indicator
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    calendar.colorHex?.let { parseColorHex(it) }
                                        ?: EInkTheme.Colors.darkGray
                                )
                                .border(1.dp, EInkTheme.Colors.primary, CircleShape)
                        )

                        Text(
                            text = calendar.name,
                            fontSize = EInkTheme.Typography.bodyLarge,
                            fontWeight = if (calendar.id == selectedCalendarId) FontWeight.Bold else FontWeight.Normal,
                            color = EInkTheme.Colors.primary
                        )

                        if (calendar.isDefault) {
                            Text(
                                text = "(default)",
                                fontSize = EInkTheme.Typography.labelSmall,
                                color = EInkTheme.Colors.gray
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact version of the calendar dropdown for inline use.
 * Shows just the calendar name with a small dropdown arrow.
 */
@Composable
fun LocalCalendarDropdownCompact(
    calendars: List<LocalCalendar>,
    selectedCalendarId: String?,
    onCalendarSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedCalendar = calendars.find { it.id == selectedCalendarId }
        ?: calendars.find { it.isDefault }
        ?: calendars.firstOrNull()

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .border(1.dp, EInkTheme.Colors.lightGray)
                .background(EInkTheme.Colors.offWhite)
                .then(
                    if (calendars.isNotEmpty()) {
                        Modifier.noRippleClickable { expanded = true }
                    } else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Small color dot
            if (selectedCalendar != null) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            selectedCalendar.colorHex?.let { parseColorHex(it) }
                                ?: EInkTheme.Colors.darkGray
                        )
                )
            }

            Text(
                text = selectedCalendar?.name ?: "Calendar",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.primary
            )

            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Select",
                modifier = Modifier.size(16.dp),
                tint = EInkTheme.Colors.darkGray
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(EInkTheme.Colors.background)
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary)
        ) {
            calendars.forEach { calendar ->
                DropdownMenuItem(
                    onClick = {
                        onCalendarSelected(calendar.id)
                        expanded = false
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    calendar.colorHex?.let { parseColorHex(it) }
                                        ?: EInkTheme.Colors.darkGray
                                )
                        )
                        Text(
                            text = calendar.name,
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Parse a hex color string to a Color.
 */
private fun parseColorHex(hex: String): Color {
    return try {
        val colorInt = android.graphics.Color.parseColor(hex)
        Color(colorInt)
    } catch (e: Exception) {
        Color.Gray
    }
}
