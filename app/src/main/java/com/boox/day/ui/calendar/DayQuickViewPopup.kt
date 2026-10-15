package com.boox.day.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.R
import com.boox.day.data.db.CalendarEvent
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Popup that appears when user long-presses a day in the calendar.
 * Shows a quick summary of:
 * - Calendar events count with navigation
 * - Tasks due count with navigation
 * - Boox Notes list with "Open in Boox Notes" buttons
 *
 * E-INK OPTIMIZED:
 * - Uses Popup (not Dialog) to avoid 500ms GC refresh
 * - Uses noRippleClickable to prevent ghosting
 * - High contrast black/white design
 */
@Composable
fun DayQuickViewPopup(
    data: DayQuickViewData,
    onNavigateToEvents: () -> Unit,
    onNavigateToTasksDue: () -> Unit,
    onOpenBooxNote: (uniqueId: String, title: String) -> Unit,
    onManageBooxNote: (noteDate: String) -> Unit,
    onDismiss: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .width(EInkTheme.PopupOverlay.width)
                .background(
                    EInkTheme.PopupOverlay.background,
                    RoundedCornerShape(EInkTheme.Global.cornerRadius)
                )
                .border(
                    EInkTheme.PopupOverlay.borderWidth,
                    EInkTheme.PopupOverlay.borderColor,
                    RoundedCornerShape(EInkTheme.Global.cornerRadius)
                )
        ) {
            Column(
                modifier = Modifier.padding(EInkTheme.PopupOverlay.padding)
            ) {
                // ─────────────────────────────────────────────────────────────
                // Header: Date + Close button
                // ─────────────────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = EInkTheme.Colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = data.displayDate,
                            fontSize = EInkTheme.PopupOverlay.titleFontSize,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.PopupOverlay.titleColor
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(EInkTheme.Global.touchTarget)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = EInkTheme.Colors.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ─────────────────────────────────────────────────────────────
                // Summary Section: Events + Tasks Due
                // ─────────────────────────────────────────────────────────────
                SummarySection(
                    eventCount = data.eventCount,
                    tasksDueCount = data.tasksDueCount,
                    onNavigateToEvents = onNavigateToEvents,
                    onNavigateToTasksDue = onNavigateToTasksDue
                )

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = EInkTheme.Colors.lightGray, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // ─────────────────────────────────────────────────────────────
                // Boox Notes Section
                // ─────────────────────────────────────────────────────────────
                BooxNotesSection(
                    booxNotes = data.booxNotes,
                    onOpenBooxNote = onOpenBooxNote,
                    onManageBooxNote = onManageBooxNote
                )
            }
        }
    }
}

@Composable
private fun SummarySection(
    eventCount: Int,
    tasksDueCount: Int,
    onNavigateToEvents: () -> Unit,
    onNavigateToTasksDue: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Calendar Events Row
        SummaryRow(
            icon = {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = EInkTheme.Colors.primary,
                    modifier = Modifier.size(18.dp)
                )
            },
            label = if (eventCount == 1) "1 calendar event" else "$eventCount calendar events",
            showViewButton = eventCount > 0,
            onViewClick = onNavigateToEvents
        )

        // Tasks Due Row
        SummaryRow(
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = EInkTheme.Colors.primary,
                    modifier = Modifier.size(18.dp)
                )
            },
            label = if (tasksDueCount == 1) "1 task due" else "$tasksDueCount tasks due",
            showViewButton = tasksDueCount > 0,
            onViewClick = onNavigateToTasksDue
        )
    }
}

@Composable
private fun SummaryRow(
    icon: @Composable () -> Unit,
    label: String,
    showViewButton: Boolean,
    onViewClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.offWhite, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            icon()
            Text(
                text = label,
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.primary
            )
        }

        if (showViewButton) {
            Row(
                modifier = Modifier
                    .noRippleClickable { onViewClick() }
                    .background(EInkTheme.Colors.primary, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "View",
                    fontSize = EInkTheme.Typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.background
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = EInkTheme.Colors.background,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun BooxNotesSection(
    booxNotes: List<BooxNoteWithEvent>,
    onOpenBooxNote: (uniqueId: String, title: String) -> Unit,
    onManageBooxNote: (noteDate: String) -> Unit
) {
    Column {
        // Section Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.boox_b_letter_tiny),
                contentDescription = null,
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Boox Notes (${booxNotes.size})",
                fontSize = EInkTheme.Typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = EInkTheme.Colors.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (booxNotes.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EInkTheme.Colors.offWhite, RoundedCornerShape(8.dp))
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No Boox Notes for this day",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.gray
                )
            }
        } else {
            // Notes List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp),  // Increased to accommodate linked event info
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(booxNotes, key = { it.note.uniqueId }) { noteWithEvent ->
                    BooxNoteRow(
                        noteWithEvent = noteWithEvent,
                        onManageClick = { onManageBooxNote(noteWithEvent.note.originalDate) },
                        onOpenClick = { onOpenBooxNote(noteWithEvent.note.uniqueId, noteWithEvent.note.title) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BooxNoteRow(
    noteWithEvent: BooxNoteWithEvent,
    onManageClick: () -> Unit,
    onOpenClick: () -> Unit
) {
    val note = noteWithEvent.note
    val linkedEvent = noteWithEvent.linkedEvent

    // Format linked event date/time: "Dec 18, 2025 at 10:00 AM"
    val linkedEventDisplay = remember(linkedEvent) {
        linkedEvent?.let { event ->
            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val dateStr = dateFormat.format(Date(event.startTime))
            if (event.isAllDay) {
                "${event.title} • $dateStr"
            } else {
                val timeStr = timeFormat.format(Date(event.startTime))
                "${event.title} • $dateStr at $timeStr"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.offWhite, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Top Row: Note info + buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Note title (with ellipsis if too long)
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = note.title.ifEmpty { "Untitled Note" },
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (note.pageCount > 1) {
                    Text(
                        text = "${note.pageCount} pages",
                        fontSize = EInkTheme.Typography.labelSmall,
                        color = EInkTheme.Colors.gray
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Buttons row: Manage + Open
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Manage button
                Row(
                    modifier = Modifier
                        .noRippleClickable { onManageClick() }
                        .border(1.dp, EInkTheme.Colors.primary, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Link this note to an event",
                        tint = EInkTheme.Colors.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Link…",
                        fontSize = EInkTheme.Typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.primary
                    )
                }

                // Open button
                Row(
                    modifier = Modifier
                        .noRippleClickable { onOpenClick() }
                        .border(1.dp, EInkTheme.Colors.primary, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Open",
                        fontSize = EInkTheme.Typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.primary
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open in Boox Notes",
                        tint = EInkTheme.Colors.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Linked Event Row (only shown if note is linked to an event)
        if (linkedEventDisplay != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = "Linked to event",
                    tint = EInkTheme.Colors.gray,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = linkedEventDisplay,
                    fontSize = 11.sp,  // Slightly larger than labelSmall (10.sp) for readability
                    color = EInkTheme.Colors.gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
