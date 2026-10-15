package com.boox.day.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import com.boox.day.ui.noRippleClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.ui.res.painterResource
import com.boox.day.R
import com.boox.day.ui.components.EInkToggle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.BooxNoteIndex
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.SyncStatus
import com.boox.day.data.model.ProviderId
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Dialog for viewing event details.
 * Shows full event information with edit button for writable events.
 * Optimized for e-ink display.
 */
@Composable
fun EventDetailsDialog(
    event: CalendarEvent,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,  // Only if event is editable
    onDelete: (() -> Unit)? = null,
    isLoading: Boolean = false,
    // Boox Notes linked to this event
    linkedBooxNotes: List<BooxNoteIndex> = emptyList(),
    onBooxNoteClick: ((BooxNoteIndex) -> Unit)? = null,
    onCreateBooxNote: (() -> Unit)? = null,
    // Sync mode and status (for hybrid/standalone mode)
    syncMode: AppSettings.SyncMode = AppSettings.SyncMode.STANDALONE,
    isSignedIn: Boolean = false,
    syncEnabled: Boolean = false,
    syncStatus: SyncStatus? = null,
    lastSyncedAt: Long? = null,
    onToggleSync: ((Boolean) -> Unit)? = null,
    // Local calendar info (for Standalone mode)
    localCalendarName: String? = null,
    localCalendarColorHex: String? = null
) {
    val context = LocalContext.current
    val canEdit = !event.isReadOnly && onEdit != null

    // Check if event is in the past (for showing Create Boox Note button)
    // Allow events within past 24 hours to still show the button
    val isEventInPast = remember(event.startTime, event.endTime) {
        val now = System.currentTimeMillis()
        val eventEnd = event.endTime ?: event.startTime
        val twentyFourHoursAgo = now - (24 * 60 * 60 * 1000L)
        eventEnd < twentyFourHoursAgo
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFullUrl by remember { mutableStateOf(false) }

    // Extract meeting info from location and description
    val meetingInfo = remember(event.location, event.description) {
        extractMeetingInfo(event.location, event.description)
    }

    // Clean description (remove ugly URLs)
    val cleanDescription = remember(event.description) {
        cleanDescriptionText(event.description)
    }

    // Calculate duration for display
    val durationText = remember(event.startTime, event.endTime, event.isAllDay) {
        if (event.isAllDay || event.endTime == null) {
            null
        } else {
            val durationMinutes = (event.endTime - event.startTime) / (1000 * 60)
            when {
                durationMinutes < 60 -> "${durationMinutes}min"
                durationMinutes % 60 == 0L -> "${durationMinutes / 60}h"
                else -> "${durationMinutes / 60}h ${durationMinutes % 60}min"
            }
        }
    }

    // E-Ink: Use Popup instead of Dialog to avoid system scrim/dimming
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(horizontal = EInkTheme.PopupOverlay.padding, vertical = EInkTheme.PopupOverlay.sectionSpacing)
                    .verticalScroll(rememberScrollState())
            ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // Title
                            Text(
                                text = event.title,
                                fontSize = EInkTheme.PopupOverlay.titleFontSize,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.PopupOverlay.titleColor
                            )

                            Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

                            // Calendar source with color indicator and access indicator
                            // Mode-aware: Shows different info for Standalone vs Hybrid mode
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                            ) {
                                // Determine which calendar info to display based on mode
                                val isStandaloneMode = syncMode == AppSettings.SyncMode.STANDALONE
                                val isLocalEvent = isStandaloneMode || syncStatus == SyncStatus.LOCAL_ONLY

                                // Calendar color indicator
                                val calendarColor = remember(event.colorHex, localCalendarColorHex, isLocalEvent) {
                                    val hexToUse = if (isLocalEvent) localCalendarColorHex else event.colorHex
                                    hexToUse?.let { hex ->
                                        try {
                                            Color(android.graphics.Color.parseColor(hex))
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(
                                            color = calendarColor ?: EInkTheme.Colors.disabled,
                                            shape = CircleShape
                                        )
                                )

                                // Mode indicator and calendar name
                                if (isStandaloneMode) {
                                    // STANDALONE MODE: "Local Event • Calendar: X"
                                    Text(
                                        text = "Local Event",
                                        fontSize = EInkTheme.Typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = EInkTheme.Colors.secondary
                                    )
                                    Text(
                                        text = "•",
                                        fontSize = EInkTheme.Typography.bodyMedium,
                                        color = EInkTheme.Colors.disabled
                                    )
                                    Text(
                                        text = localCalendarName ?: "Default",
                                        fontSize = EInkTheme.Typography.bodyMedium,
                                        color = EInkTheme.Colors.secondary
                                    )
                                } else {
                                    // HYBRID MODE: Show Google Calendar name or "Local Event"
                                    if (isLocalEvent) {
                                        Text(
                                            text = "Local Event",
                                            fontSize = EInkTheme.Typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = EInkTheme.Colors.secondary
                                        )
                                        if (localCalendarName != null) {
                                            Text(
                                                text = "•",
                                                fontSize = EInkTheme.Typography.bodyMedium,
                                                color = EInkTheme.Colors.disabled
                                            )
                                            Text(
                                                text = localCalendarName,
                                                fontSize = EInkTheme.Typography.bodyMedium,
                                                color = EInkTheme.Colors.secondary
                                            )
                                        }
                                    } else {
                                        // Synced event - show Google Calendar name
                                        Text(
                                            text = event.calendarName,
                                            fontSize = EInkTheme.Typography.bodyMedium,
                                            color = EInkTheme.Colors.secondary
                                        )
                                    }
                                }

                                // Sync status indicator (only in Hybrid mode when signed in)
                                if (!isStandaloneMode && isSignedIn && syncStatus != null) {
                                    Spacer(modifier = Modifier.width(EInkTheme.Global.spacingSmall))
                                    Icon(
                                        painter = painterResource(
                                            id = when (syncStatus) {
                                                SyncStatus.SYNCED -> R.drawable.cloud_done_40px
                                                SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE -> R.drawable.cloud_upload_40px
                                                SyncStatus.PENDING_DELETE -> R.drawable.cloud_off_40px
                                                SyncStatus.CONFLICT -> R.drawable.warning_40px
                                                SyncStatus.LOCAL_ONLY -> R.drawable.mobile_40px
                                            }
                                        ),
                                        contentDescription = "Sync status",
                                        modifier = Modifier.size(18.dp),
                                        tint = when (syncStatus) {
                                            SyncStatus.CONFLICT -> EInkTheme.Colors.primary
                                            SyncStatus.SYNCED, SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE -> EInkTheme.Colors.secondary
                                            else -> EInkTheme.Colors.disabled
                                        }
                                    )
                                }
                            }

                            // Read-only indicator on its own line to prevent overflow
                            if (event.isReadOnly) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.lock_40px),
                                        contentDescription = "Read only",
                                        modifier = Modifier.size(14.dp),
                                        tint = EInkTheme.Colors.disabled
                                    )
                                    Text(
                                        text = "View only",
                                        fontSize = EInkTheme.Typography.bodySmall,
                                        color = EInkTheme.Colors.disabled
                                    )
                                }
                            }
                        }

                        // Close button (top-right X icon)
                        Box(
                            modifier = Modifier
                                .size(EInkTheme.Global.touchTarget)
                                .noRippleClickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                modifier = Modifier.size(24.dp),
                                tint = EInkTheme.Colors.primary
                            )
                        }
                    }

                    // Divider after header - E-Ink: Use Black for visibility
                    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))
                    Divider(color = EInkTheme.Colors.primary, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                    // Date and time in a cleaner layout
                    DetailRow(
                        iconResId = R.drawable.calendar_today_40px,
                        label = "Date",
                        value = formatDate(event.dayId)
                    )

                    if (event.isAllDay) {
                        DetailRow(
                            iconResId = R.drawable.nest_clock_farsight_analog_40px,
                            label = "Time",
                            value = "All day"
                        )
                    } else {
                        // Time with duration badge
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = EInkTheme.Global.spacing),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.nest_clock_farsight_analog_40px),
                                contentDescription = "Time",
                                modifier = Modifier.size(20.dp),
                                tint = EInkTheme.Colors.secondary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Time",
                                    fontSize = EInkTheme.Typography.bodySmall,
                                    color = EInkTheme.Colors.secondary
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                                ) {
                                    Text(
                                        text = formatTimeRange(event.startTime, event.endTime),
                                        fontSize = EInkTheme.Typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = EInkTheme.Colors.primary
                                    )
                                    // Duration badge
                                    if (durationText != null) {
                                        Box(
                                            modifier = Modifier
                                                .background(EInkTheme.Colors.offWhite)
                                                .border(1.dp, EInkTheme.Colors.dividerHairline, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
                                                .padding(horizontal = EInkTheme.Global.spacingSmall, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = durationText,
                                                fontSize = EInkTheme.Typography.bodySmall,
                                                color = EInkTheme.Colors.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Meeting Link Section (for virtual meetings)
                    if (meetingInfo != null) {
                        Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))
                        MeetingLinkSection(
                            meetingInfo = meetingInfo,
                            showFullUrl = showFullUrl,
                            onToggleUrl = { showFullUrl = !showFullUrl },
                            onCopyUrl = {
                                copyToClipboard(context, meetingInfo.url)
                                Toast.makeText(context, "Meeting link copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    // Location (if not a virtual meeting URL) - with copy button
                    event.location?.let { location ->
                        val cleanLocation = if (meetingInfo != null) {
                            // Remove the meeting URL from location display
                            location.replace(meetingInfo.url, "").trim()
                        } else {
                            location
                        }
                        if (cleanLocation.isNotBlank() && !isJustUrl(cleanLocation)) {
                            DetailRowWithCopy(
                                iconResId = R.drawable.location_on_40px,
                                label = "Location",
                                value = cleanLocation,
                                onCopy = {
                                    copyToClipboard(context, cleanLocation)
                                    Toast.makeText(context, "Location copied", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    // Description (cleaned of URLs) - with copy button and scroll for long content
                    if (cleanDescription.isNotBlank()) {
                        Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Description",
                                fontSize = EInkTheme.Typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = EInkTheme.Colors.secondary
                            )
                            // E-Ink: Larger copy icon without border for better distinction
                            Box(
                                modifier = Modifier
                                    .size(EInkTheme.Global.touchTarget)
                                    .noRippleClickable {
                                        copyToClipboard(context, cleanDescription)
                                        Toast.makeText(context, "Description copied", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.content_copy_40px),
                                    contentDescription = "Copy description",
                                    modifier = Modifier.size(28.dp),
                                    tint = EInkTheme.Colors.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .background(EInkTheme.Colors.offWhite)
                                .border(1.dp, EInkTheme.Colors.borderStrong, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
                        ) {
                            Box(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(EInkTheme.Global.paddingMedium)
                            ) {
                                Text(
                                    text = cleanDescription,
                                    fontSize = EInkTheme.Typography.bodyMedium,
                                    color = EInkTheme.Colors.primary
                                )
                            }
                        }
                    }

                    // Attendees - tap to copy email
                    event.attendees?.let { attendees ->
                        if (attendees.isNotBlank()) {
                            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))
                            Text(
                                text = "Attendees (tap to copy)",
                                fontSize = EInkTheme.Typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = EInkTheme.Colors.secondary
                            )
                            Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

                            val attendeeList = attendees.split(",").map { it.trim() }
                            Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)) {
                                attendeeList.forEach { attendee ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(EInkTheme.Global.touchTarget)
                                            .border(1.dp, EInkTheme.Colors.borderStrong, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
                                            .noRippleClickable {
                                                copyToClipboard(context, attendee)
                                                Toast.makeText(context, "Email copied: $attendee", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = EInkTheme.Global.paddingMedium),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.person_40px),
                                            contentDescription = "Attendee",
                                            modifier = Modifier.size(18.dp),
                                            tint = EInkTheme.Colors.secondary
                                        )
                                        Text(
                                            text = attendee,
                                            fontSize = EInkTheme.Typography.bodyMedium,
                                            color = EInkTheme.Colors.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            painter = painterResource(R.drawable.content_copy_40px),
                                            contentDescription = "Copy email",
                                            modifier = Modifier.size(24.dp),
                                            tint = EInkTheme.Colors.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Linked Boox Notes Section - Each note has explicit "Open" button
                    if (linkedBooxNotes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))
                        Text(
                            text = "Linked Boox Notes (${linkedBooxNotes.size})",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.Colors.secondary
                        )
                        Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
                        Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)) {
                            linkedBooxNotes.forEach { note ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, EInkTheme.Colors.darkGray, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
                                        .padding(EInkTheme.Global.paddingSmall),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                                ) {
                                    // Note info (left side)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = note.title,
                                            fontSize = EInkTheme.Typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = EInkTheme.Colors.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        // Timestamp and page count inline
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = formatNoteTime(note.originalTimestamp),
                                                fontSize = EInkTheme.Typography.bodySmall,
                                                color = EInkTheme.Colors.gray
                                            )
                                            Text(
                                                text = "•",
                                                fontSize = EInkTheme.Typography.bodySmall,
                                                color = EInkTheme.Colors.gray
                                            )
                                            Text(
                                                text = "${note.pageCount} page${if (note.pageCount != 1) "s" else ""}",
                                                fontSize = EInkTheme.Typography.bodySmall,
                                                color = EInkTheme.Colors.gray
                                            )
                                        }
                                    }
                                    // "Open" button (right side) - explicit action, 48dp touch target
                                    if (onBooxNoteClick != null) {
                                        OutlinedButton(
                                            onClick = { onBooxNoteClick(note) },
                                            modifier = Modifier.height(EInkTheme.Global.touchTarget),
                                            contentPadding = PaddingValues(horizontal = EInkTheme.Global.paddingMedium),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = EInkTheme.Colors.primary
                                            ),
                                            border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary),
                                            shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.boox_notes_40px),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = EInkTheme.Colors.primary
                                                )
                                                Text(
                                                    text = "Open",
                                                    fontSize = EInkTheme.Typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Sync Status Section (only in Hybrid mode when signed in)
                    // In Standalone mode, we don't show sync status - all events are local
                    if (syncMode == AppSettings.SyncMode.HYBRID && isSignedIn && syncStatus != null) {
                        Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(EInkTheme.Colors.offWhite)
                                .border(1.dp, EInkTheme.Colors.borderStrong, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(EInkTheme.Global.paddingMedium),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            id = when {
                                                syncEnabled && syncStatus == SyncStatus.SYNCED -> R.drawable.cloud_done_40px
                                                syncEnabled -> R.drawable.cloud_upload_40px
                                                else -> R.drawable.cloud_off_40px
                                            }
                                        ),
                                        contentDescription = "Sync status",
                                        modifier = Modifier.size(24.dp),
                                        tint = if (syncEnabled) EInkTheme.Colors.primary else EInkTheme.Colors.disabled
                                    )
                                    Column {
                                        Text(
                                            text = when {
                                                syncStatus == SyncStatus.SYNCED -> "Synced with ${ProviderId.fromKey(event.providerId).displayName}"
                                                syncStatus == SyncStatus.PENDING_CREATE -> "Pending upload"
                                                syncStatus == SyncStatus.PENDING_UPDATE -> "Changes pending"
                                                syncStatus == SyncStatus.CONFLICT -> "Sync conflict"
                                                !syncEnabled -> "Local only"
                                                else -> "Not synced"
                                            },
                                            fontSize = EInkTheme.Typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = EInkTheme.Colors.primary
                                        )
                                        if (lastSyncedAt != null && syncStatus == SyncStatus.SYNCED) {
                                            Text(
                                                text = "Last synced: ${formatLastSync(lastSyncedAt)}",
                                                fontSize = EInkTheme.Typography.bodySmall,
                                                color = EInkTheme.Colors.disabled
                                            )
                                        }
                                    }
                                }
                                // Sync toggle (if callback provided)
                                if (onToggleSync != null && !event.isReadOnly) {
                                    EInkToggle(
                                        checked = syncEnabled,
                                        onCheckedChange = onToggleSync
                                    )
                                }
                            }
                        }
                    }

                    // Meeting notes (Oct 2026): take notes for this event on a Memos page, open them, their action items
                    com.boox.day.meetings.MeetingNotesSection(event = event, onDismiss = onDismiss)

                    // Divider before action buttons - E-Ink: Use Black for visibility
                    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))
                    Divider(color = EInkTheme.Colors.primary, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                    // Action buttons (compact - 42dp height)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                    ) {
                        // Delete button (if editable) - E-Ink: use inverted style for danger
                        if (canEdit && onDelete != null) {
                            OutlinedButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.height(EInkTheme.Global.touchTarget),
                                enabled = !isLoading,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = EInkTheme.Colors.primary
                                ),
                                border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary),
                                shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.delete_40px),
                                    contentDescription = "Delete",
                                    modifier = Modifier.size(20.dp),
                                    tint = EInkTheme.Colors.primary
                                )
                            }
                        }

                        // Share button - E-Ink: vector icon instead of Material icon
                        OutlinedButton(
                            onClick = {
                                val shareText = formatEventForSharing(event, cleanDescription)
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    putExtra(Intent.EXTRA_SUBJECT, event.title)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share event")
                                context.startActivity(shareIntent)
                            },
                            modifier = Modifier.height(EInkTheme.Global.touchTarget),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = EInkTheme.Colors.primary
                            ),
                            border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary),
                            shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.share_40px),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = EInkTheme.Colors.primary
                                )
                                Text(
                                    text = "Share",
                                    fontSize = EInkTheme.Typography.bodyMedium
                                )
                            }
                        }

                        // Create Boox Note button - only show for current/future events
                        if (onCreateBooxNote != null && !isEventInPast) {
                            Button(
                                onClick = { onCreateBooxNote.invoke() },
                                modifier = Modifier.height(EInkTheme.Global.touchTarget),
                                enabled = !isLoading,
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = EInkTheme.Colors.primary,
                                    contentColor = EInkTheme.Colors.background
                                ),
                                shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius),
                                elevation = ButtonDefaults.elevation(0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.edit_square_40px),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = EInkTheme.Colors.background
                                    )
                                    Text(
                                        text = "Create Boox Note of this event",
                                        fontSize = EInkTheme.Typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Edit button (if editable) - pushed to the right
                        if (canEdit) {
                            Button(
                                onClick = { onEdit?.invoke() },
                                modifier = Modifier.height(EInkTheme.Global.touchTarget),
                                enabled = !isLoading,
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = EInkTheme.Colors.primary,
                                    contentColor = EInkTheme.Colors.background
                                ),
                                shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius),
                                elevation = ButtonDefaults.elevation(0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.edit_square_40px),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = EInkTheme.Colors.background
                                    )
                                    Text(text = "Edit", fontSize = EInkTheme.Typography.bodyMedium)
                                }
                            }
                        }
                    }

                    // Read-only indicator
                    if (event.isReadOnly) {
                        Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(EInkTheme.Colors.offWhite)
                                .border(1.dp, EInkTheme.Colors.borderStrong, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
                        ) {
                            Row(
                                modifier = Modifier.padding(EInkTheme.Global.paddingMedium),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.lock_40px),
                                    contentDescription = "Read only",
                                    modifier = Modifier.size(18.dp),
                                    tint = EInkTheme.Colors.disabled
                                )
                                Text(
                                    text = "This event is from a shared calendar and cannot be edited",
                                    fontSize = EInkTheme.Typography.bodySmall,
                                    color = EInkTheme.Colors.disabled
                                )
                            }
                        }
                    }
                }
            }
        }

    // Delete confirmation dialog - E-Ink: Use Popup instead of AlertDialog
    // Sync-aware: shows different message for synced vs local-only events
    if (showDeleteConfirm && onDelete != null) {
        // Determine if this is a synced event (will be deleted from Google too)
        val isSyncedEvent = syncMode == AppSettings.SyncMode.HYBRID &&
                isSignedIn &&
                syncEnabled &&
                syncStatus != null &&
                syncStatus != SyncStatus.LOCAL_ONLY

        Popup(
            alignment = Alignment.Center,
            onDismissRequest = { showDeleteConfirm = false },
            properties = PopupProperties(focusable = true)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                        .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                        .padding(EInkTheme.PopupOverlay.padding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.warning_40px),
                            contentDescription = "Warning",
                            modifier = Modifier.size(24.dp),
                            tint = EInkTheme.PopupOverlay.titleColor
                        )
                        Text(
                            text = if (isSyncedEvent) "Delete Synced Event" else "Delete Event",
                            fontWeight = FontWeight.Bold,
                            fontSize = EInkTheme.PopupOverlay.titleFontSize,
                            color = EInkTheme.PopupOverlay.titleColor
                        )
                    }
                    Spacer(modifier = Modifier.height(EInkTheme.PopupOverlay.titlePaddingBottom))
                    Text(
                        text = if (isSyncedEvent) {
                            "Are you sure you want to delete \"${event.title}\"?\n\nThis event will also be deleted from ${ProviderId.fromKey(event.providerId).displayName}."
                        } else {
                            "Are you sure you want to delete \"${event.title}\"? This cannot be undone."
                        },
                        fontSize = EInkTheme.Typography.bodyLarge,
                        color = EInkTheme.Colors.primary
                    )
                    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium, Alignment.CenterHorizontally)
                    ) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = false },
                            modifier = Modifier.height(EInkTheme.Global.touchTarget),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EInkTheme.Colors.primary),
                            border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary),
                            shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                        ) {
                            Text("Cancel", fontSize = EInkTheme.Typography.bodyLarge)
                        }
                        Button(
                            onClick = {
                                showDeleteConfirm = false
                                onDelete()
                            },
                            modifier = Modifier.height(EInkTheme.Global.touchTarget),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = EInkTheme.Colors.primary,
                                contentColor = EInkTheme.Colors.background
                            ),
                            shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius),
                            elevation = ButtonDefaults.elevation(0.dp)
                        ) {
                            Text("Delete", fontSize = EInkTheme.Typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Meeting link section with platform indicator and copy button
 * E-Ink: Uses Box instead of Card, text symbols instead of Material Icons.
 */
@Composable
private fun MeetingLinkSection(
    meetingInfo: MeetingInfo,
    showFullUrl: Boolean,
    onToggleUrl: () -> Unit,
    onCopyUrl: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.offWhite)
            .border(1.dp, EInkTheme.Colors.borderStrong, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
    ) {
        Column(
            modifier = Modifier.padding(EInkTheme.Global.paddingMedium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Platform name with icon (E-Ink: vector drawable icon)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.hangout_video_40px),
                        contentDescription = "Video meeting",
                        modifier = Modifier.size(24.dp),
                        tint = EInkTheme.Colors.primary
                    )
                    Text(
                        text = meetingInfo.platformName,
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = EInkTheme.Colors.primary
                    )
                }

                // Copy button - E-Ink: 48dp touch target
                OutlinedButton(
                    onClick = onCopyUrl,
                    modifier = Modifier.height(EInkTheme.Global.touchTarget),
                    contentPadding = PaddingValues(horizontal = EInkTheme.Global.paddingMedium),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = EInkTheme.Colors.primary
                    ),
                    border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary),
                    shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.content_copy_40px),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = EInkTheme.Colors.primary
                        )
                        Text(
                            text = "Copy",
                            fontSize = EInkTheme.Typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

            // URL display (truncated or full)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .noRippleClickable(onClick = onToggleUrl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showFullUrl) {
                    Text(
                        text = meetingInfo.url,
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.disabled,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = meetingInfo.url,
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.disabled,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Icon(
                    painter = painterResource(
                        id = if (showFullUrl) R.drawable.expand_circle_up_40px else R.drawable.expand_circle_down_40px
                    ),
                    contentDescription = if (showFullUrl) "Collapse" else "Expand",
                    modifier = Modifier.size(18.dp),
                    tint = EInkTheme.Colors.disabled
                )
            }
        }
    }
}

/**
 * Detail row component with improved e-ink contrast.
 * E-Ink: Uses vector drawable icon for crisp rendering on e-ink displays.
 */
@Composable
private fun DetailRow(
    iconResId: Int,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = EInkTheme.Global.spacing),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = EInkTheme.Colors.secondary
        )
        Column {
            Text(
                text = label,
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.secondary
            )
            Text(
                text = value,
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )
        }
    }
}

/**
 * Data class for meeting information
 */
private data class MeetingInfo(
    val platformName: String,
    val url: String
)

/**
 * Extract meeting URL and platform from location/description
 */
private fun extractMeetingInfo(location: String?, description: String?): MeetingInfo? {
    val combined = "${location.orEmpty()}\n${description.orEmpty()}"

    // Regex patterns for different meeting platforms
    val patterns = listOf(
        // Microsoft Teams
        Regex("""(https://teams\.microsoft\.com/l/meetup-join/[^\s<>"]+)""", RegexOption.IGNORE_CASE) to "Microsoft Teams",
        Regex("""(https://[a-z0-9-]+\.teams\.microsoft\.com/[^\s<>"]+)""", RegexOption.IGNORE_CASE) to "Microsoft Teams",
        // Zoom
        Regex("""(https://[a-z0-9-]*\.?zoom\.us/j/[^\s<>"]+)""", RegexOption.IGNORE_CASE) to "Zoom Meeting",
        Regex("""(https://[a-z0-9-]*\.?zoom\.us/my/[^\s<>"]+)""", RegexOption.IGNORE_CASE) to "Zoom Meeting",
        // Google Meet
        Regex("""(https://meet\.google\.com/[a-z-]+)""", RegexOption.IGNORE_CASE) to "Google Meet",
        // WebEx
        Regex("""(https://[a-z0-9-]+\.webex\.com/[^\s<>"]+)""", RegexOption.IGNORE_CASE) to "Cisco WebEx",
        // GoToMeeting
        Regex("""(https://[a-z0-9-]+\.gotomeeting\.com/[^\s<>"]+)""", RegexOption.IGNORE_CASE) to "GoToMeeting",
        // Skype
        Regex("""(https://join\.skype\.com/[^\s<>"]+)""", RegexOption.IGNORE_CASE) to "Skype Meeting"
    )

    for ((pattern, platformName) in patterns) {
        val match = pattern.find(combined)
        if (match != null) {
            return MeetingInfo(platformName, match.groupValues[1])
        }
    }

    return null
}

/**
 * Clean description text by removing URLs and meeting boilerplate
 */
private fun cleanDescriptionText(description: String?): String {
    if (description.isNullOrBlank()) return ""

    var cleaned = description

    // Remove common meeting URLs
    val urlPatterns = listOf(
        Regex("""https://teams\.microsoft\.com/l/meetup-join/[^\s<>"]*""", RegexOption.IGNORE_CASE),
        Regex("""https://[a-z0-9-]+\.teams\.microsoft\.com/[^\s<>"]*""", RegexOption.IGNORE_CASE),
        Regex("""https://[a-z0-9-]*\.?zoom\.us/[^\s<>"]*""", RegexOption.IGNORE_CASE),
        Regex("""https://meet\.google\.com/[a-z-]+""", RegexOption.IGNORE_CASE),
        Regex("""https://[a-z0-9-]+\.webex\.com/[^\s<>"]*""", RegexOption.IGNORE_CASE),
        Regex("""https://[a-z0-9-]+\.gotomeeting\.com/[^\s<>"]*""", RegexOption.IGNORE_CASE),
        Regex("""https://join\.skype\.com/[^\s<>"]*""", RegexOption.IGNORE_CASE)
    )

    for (pattern in urlPatterns) {
        cleaned = cleaned?.replace(pattern, "")
    }

    // Remove Teams meeting boilerplate text
    val boilerplatePatterns = listOf(
        Regex("""_{10,}.*?_{10,}""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
        Regex("""Microsoft Teams meeting.*?Learn more about Teams""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
        Regex("""Join on your computer,? mobile app or room device.*?Click here to join the meeting""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
        Regex("""Meeting ID:.*?Passcode:.*?\n""", setOf(RegexOption.IGNORE_CASE)),
        Regex("""<[^>]+>"""),  // HTML tags
        Regex("""\[image:[^\]]*\]""", RegexOption.IGNORE_CASE),  // Image placeholders
        Regex("""Join with Google Meet.*?meet\.google\.com""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    )

    for (pattern in boilerplatePatterns) {
        cleaned = cleaned?.replace(pattern, "")
    }

    // Clean up multiple newlines and whitespace
    cleaned = cleaned
        ?.replace(Regex("""\n{3,}"""), "\n\n")
        ?.replace(Regex("""[ \t]{2,}"""), " ")
        ?.trim()

    return cleaned ?: ""
}

/**
 * Check if a string is just a URL
 */
private fun isJustUrl(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.startsWith("http://") || trimmed.startsWith("https://")
}

/**
 * Copy text to clipboard
 */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Meeting Link", text)
    clipboard.setPrimaryClip(clip)
}

/**
 * Format date for display. Respects user's date format preference.
 */
private fun formatDate(dayId: String): String {
    return com.boox.day.utils.DateTimeFormatUtils.formatDateFullFromString(dayId)
}

/**
 * Format time range for display. Respects user's time format preference (12h/24h).
 */
private fun formatTimeRange(startMillis: Long, endMillis: Long?): String {
    return com.boox.day.utils.DateTimeFormatUtils.formatTimeRange(startMillis, endMillis)
}

/**
 * Detail row with a copy button for e-ink optimized display.
 * E-Ink: Uses vector drawable icon for crisp rendering on e-ink displays.
 */
@Composable
private fun DetailRowWithCopy(
    iconResId: Int,
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = EInkTheme.Global.spacing),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = EInkTheme.Colors.secondary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.secondary
            )
            Text(
                text = value,
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )
        }
        // E-Ink: Larger copy icon without border for better distinction
        Box(
            modifier = Modifier
                .size(EInkTheme.Global.touchTarget)
                .noRippleClickable(onClick = onCopy),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.content_copy_40px),
                contentDescription = "Copy",
                modifier = Modifier.size(28.dp),
                tint = EInkTheme.Colors.primary
            )
        }
    }
}

/**
 * Format event details as shareable text.
 */
private fun formatEventForSharing(event: CalendarEvent, cleanDescription: String): String {
    val sb = StringBuilder()

    // Title
    sb.appendLine("📅 ${event.title}")
    sb.appendLine()

    // Date and time
    sb.appendLine("🗓 ${formatDate(event.dayId)}")
    if (event.isAllDay) {
        sb.appendLine("⏰ All day")
    } else {
        sb.appendLine("⏰ ${formatTimeRange(event.startTime, event.endTime)}")
    }

    // Location
    event.location?.let { location ->
        if (location.isNotBlank()) {
            sb.appendLine("📍 $location")
        }
    }

    // Description
    if (cleanDescription.isNotBlank()) {
        sb.appendLine()
        sb.appendLine("📝 Description:")
        sb.appendLine(cleanDescription)
    }

    // Attendees
    event.attendees?.let { attendees ->
        if (attendees.isNotBlank()) {
            sb.appendLine()
            sb.appendLine("👥 Attendees:")
            attendees.split(",").map { it.trim() }.forEach { attendee ->
                sb.appendLine("  • $attendee")
            }
        }
    }

    // Calendar source
    sb.appendLine()
    sb.appendLine("From: ${event.calendarName}")

    return sb.toString()
}

/**
 * Format last sync timestamp for display
 */
private fun formatLastSync(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestamp
    val diffMinutes = diffMs / (1000 * 60)
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24

    return when {
        diffMinutes < 1 -> "just now"
        diffMinutes < 60 -> "${diffMinutes}m ago"
        diffHours < 24 -> "${diffHours}h ago"
        diffDays < 7 -> "${diffDays}d ago"
        else -> {
            val instant = Instant.ofEpochMilli(timestamp)
            val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
            date.format(DateTimeFormatter.ofPattern("MMM d"))
        }
    }
}

/**
 * Format note timestamp for display
 */
private fun formatNoteTime(timestamp: Long): String {
    val instant = Instant.ofEpochMilli(timestamp)
    val time = instant.atZone(ZoneId.systemDefault()).toLocalTime()
    val hour = if (time.hour == 0) 12 else if (time.hour > 12) time.hour - 12 else time.hour
    val amPm = if (time.hour < 12) "AM" else "PM"
    return String.format("%d:%02d %s", hour, time.minute, amPm)
}
