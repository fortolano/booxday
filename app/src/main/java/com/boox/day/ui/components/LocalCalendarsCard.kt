package com.boox.day.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boox.day.data.db.LocalCalendar
import com.boox.day.ui.theme.EInkTheme

/**
 * Card displaying local calendars with add/delete functionality.
 * Used in the Calendar Settings > Local tab.
 *
 * E-Ink optimized: High contrast, no colors beyond grayscale.
 */
@Composable
fun LocalCalendarsCard(
    calendars: List<LocalCalendar>,
    maxCalendars: Int = 9,
    onAddCalendar: () -> Unit,
    onDeleteCalendars: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    // Filter out default calendar for selection (it cannot be deleted)
    val deletableCalendars = calendars.filter { !it.isDefault }
    val canAddMore = calendars.size < maxCalendars

    Card(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = EInkTheme.Colors.background,
        border = BorderStroke(0.5.dp, EInkTheme.Colors.borderStrong),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Calendars",
                        modifier = Modifier.size(EInkTheme.Global.iconSizeSmall),
                        tint = EInkTheme.Colors.primary
                    )
                    Text(
                        text = "On-Device Calendars",
                        fontSize = EInkTheme.Typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.primary
                    )
                }

                Text(
                    text = "${calendars.size} of $maxCalendars",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Organize your local events into categories",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Calendar list
            if (calendars.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No calendars yet. Add your first calendar!",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.gray
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, EInkTheme.Colors.lightGray, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
                ) {
                    calendars.forEachIndexed { index, calendar ->
                        CalendarListItem(
                            calendar = calendar,
                            isSelected = calendar.id in selectedIds,
                            onSelectionChanged = { selected ->
                                selectedIds = if (selected) {
                                    selectedIds + calendar.id
                                } else {
                                    selectedIds - calendar.id
                                }
                            }
                        )

                        if (index < calendars.size - 1) {
                            Divider(color = EInkTheme.Colors.lightGray, thickness = 1.dp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Add button
                OutlinedButton(
                    onClick = onAddCalendar,
                    modifier = Modifier
                        .weight(1f)
                        .height(EInkTheme.Button.height),
                    enabled = canAddMore,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = EInkTheme.Button.textColor,
                        disabledContentColor = EInkTheme.Colors.darkGray
                    ),
                    border = BorderStroke(
                        EInkTheme.Button.borderWidth,
                        if (canAddMore) EInkTheme.Button.borderColor else EInkTheme.Colors.lightGray
                    ),
                    shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add Calendar",
                        fontSize = EInkTheme.Typography.bodyMedium
                    )
                }

                // Delete button (only enabled when items selected)
                OutlinedButton(
                    onClick = {
                        onDeleteCalendars(selectedIds.toList())
                        selectedIds = emptySet()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(EInkTheme.Button.height),
                    enabled = selectedIds.isNotEmpty(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = EInkTheme.Button.textColor,
                        disabledContentColor = EInkTheme.Colors.darkGray
                    ),
                    border = BorderStroke(
                        EInkTheme.Button.borderWidth,
                        if (selectedIds.isNotEmpty()) EInkTheme.Button.borderColor else EInkTheme.Colors.lightGray
                    ),
                    shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (selectedIds.isEmpty()) "Delete" else "Delete (${selectedIds.size})",
                        fontSize = EInkTheme.Typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * Single calendar item in the list.
 */
@Composable
private fun CalendarListItem(
    calendar: LocalCalendar,
    isSelected: Boolean,
    onSelectionChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) EInkTheme.Colors.offWhite else EInkTheme.Colors.background
            )
            .clickable(enabled = !calendar.isDefault) {
                onSelectionChanged(!isSelected)
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Color indicator (small circle)
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(
                    calendar.colorHex?.let { parseColorHex(it) } ?: EInkTheme.Colors.darkGray
                )
                .border(1.dp, EInkTheme.Colors.primary, CircleShape)
        )

        // Calendar name
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = calendar.name,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = if (calendar.isDefault) FontWeight.Bold else FontWeight.Normal,
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

        // Selection checkbox (disabled for default calendar)
        if (calendar.isDefault) {
            // Show a dash or nothing to indicate non-deletable
            Box(modifier = Modifier.size(24.dp)) {
                Text(
                    text = "—",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.lightGray,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectionChanged,
                colors = CheckboxDefaults.colors(
                    checkedColor = EInkTheme.Checkbox.checkedBackground,
                    uncheckedColor = EInkTheme.Checkbox.borderColor,
                    checkmarkColor = EInkTheme.Checkbox.checkmarkColor
                )
            )
        }
    }
}

/**
 * Info card explaining on-device calendars feature and their relationship
 * to Private Mode.
 */
@Composable
fun LocalCalendarsInfoCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = EInkTheme.Colors.offWhite,
        border = BorderStroke(0.5.dp, EInkTheme.Colors.borderStrong),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    modifier = Modifier.size(EInkTheme.Global.iconSizeSmall),
                    tint = EInkTheme.Colors.darkGray
                )
                Text(
                    text = "About On-Device Calendars",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "On-device calendars are stored privately on your device. They never sync to the cloud — even in Cloud Sync mode.",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "• Create categories like Work, Family, or Personal",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )

            Text(
                text = "• Events stay on this device only",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )

            Text(
                text = "• Deleting a calendar moves events to \"Default\"",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )
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
