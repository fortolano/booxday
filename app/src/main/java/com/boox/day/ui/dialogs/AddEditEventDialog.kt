package com.boox.day.ui.dialogs

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.ui.res.painterResource
import com.boox.day.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.LocalCalendar
import com.boox.day.data.model.ProviderId
import com.boox.day.data.model.WritableCalendar
import com.boox.day.ui.components.EInkToggle
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.components.LocalCalendarDropdown
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

/**
 * Dialog for creating or editing calendar events.
 * Supports local-only events and cloud sync to Google/Outlook.
 * Optimized for e-ink display.
 *
 * Now includes voice input option - tap the mic icon to speak your event.
 *
 * ## Sync Behavior
 * - **Sync OFF**: Event saved to local calendar (from localCalendars list)
 * - **Sync ON**: Event synced to selected cloud provider's calendar
 * - **Edit synced event**: Provider shown read-only, cannot change provider
 */
@Composable
fun AddEditEventDialog(
    onDismiss: () -> Unit,
    onSave: (
        calendarId: String,       // Cloud Calendar ID (empty if local-only)
        title: String,
        date: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int?,
        endMinute: Int?,
        isAllDay: Boolean,
        location: String?,
        description: String?,
        attendees: String?,       // Comma-separated emails (for cloud sync)
        syncEnabled: Boolean,     // Whether to sync to cloud
        localCalendarId: String?, // Local calendar ID (when sync OFF)
        syncProviderId: ProviderId? // Which cloud provider to sync to (when sync ON)
    ) -> Unit,
    onDelete: (() -> Unit)? = null,  // Only for edit mode
    onMoveToLocal: ((CalendarEvent) -> Unit)? = null,
    onVoiceInput: (() -> Unit)? = null,  // Voice input callback
    writableCalendars: List<WritableCalendar>,
    initialDate: String,  // "yyyy-MM-dd"
    existingEvent: CalendarEvent? = null,  // Null for create mode
    isLoading: Boolean = false,
    // Pre-fill data from voice input
    prefillTitle: String? = null,
    prefillDate: String? = null,
    prefillStartHour: Int? = null,
    prefillStartMinute: Int? = null,
    prefillEndHour: Int? = null,
    prefillEndMinute: Int? = null,
    prefillIsAllDay: Boolean? = null,
    prefillLocation: String? = null,
    prefillDescription: String? = null,
    prefillAttendees: List<String>? = null,  // Attendee names from voice input
    prefillConfidenceLow: Boolean = false,  // Show warning if AI confidence was low
    // Sync mode settings (Standalone/Hybrid)
    syncMode: AppSettings.SyncMode = AppSettings.SyncMode.STANDALONE,  // Current sync mode
    isSignedIn: Boolean = false,                // Is user signed into any cloud account
    defaultSyncEnabled: Boolean = false,        // Default sync toggle value for new events
    existingSyncEnabled: Boolean? = null,       // Current sync state for existing event (null = use default)
    // Local calendars (for local-only events)
    localCalendars: List<LocalCalendar> = emptyList(),
    existingLocalCalendarId: String? = null,    // Local calendar ID for existing event
    // Connected cloud accounts
    hasGoogleAccount: Boolean = false,          // Is there a connected Google account?
    hasOutlookAccount: Boolean = false,         // Is there a connected Outlook account?
    existingProviderId: ProviderId? = null      // Provider of existing synced event (read-only in edit mode)
) {
    val isEditMode = existingEvent != null
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Form state - Uses prefill values from voice input, then existing event, then defaults
    var selectedCalendarId by remember {
        mutableStateOf(existingEvent?.calendarId ?: "")
    }
    var title by remember { mutableStateOf(prefillTitle ?: existingEvent?.title ?: "") }
    var date by remember { mutableStateOf(prefillDate ?: existingEvent?.dayId ?: initialDate) }
    var isAllDay by remember { mutableStateOf(prefillIsAllDay ?: existingEvent?.isAllDay ?: false) }

    // Parse existing event time or default to next hour (rounded)
    val defaultTime = LocalTime.now().let {
        LocalTime.of((it.hour + 1) % 24, 0) // Round to next hour
    }

    // Calculate initial values - prefill > existing event > defaults
    val initialStartHour = prefillStartHour ?: if (existingEvent != null && !existingEvent.isAllDay) {
        java.time.Instant.ofEpochMilli(existingEvent.startTime)
            .atZone(ZoneId.systemDefault())
            .toLocalTime().hour
    } else {
        defaultTime.hour
    }
    val initialStartMinute = prefillStartMinute ?: if (existingEvent != null && !existingEvent.isAllDay) {
        java.time.Instant.ofEpochMilli(existingEvent.startTime)
            .atZone(ZoneId.systemDefault())
            .toLocalTime().minute
    } else {
        0
    }
    val initialEndHour = prefillEndHour ?: if (existingEvent != null && existingEvent.endTime != null && !existingEvent.isAllDay) {
        java.time.Instant.ofEpochMilli(existingEvent.endTime)
            .atZone(ZoneId.systemDefault())
            .toLocalTime().hour
    } else {
        (initialStartHour + 1) % 24
    }
    val initialEndMinute = prefillEndMinute ?: if (existingEvent != null && existingEvent.endTime != null && !existingEvent.isAllDay) {
        java.time.Instant.ofEpochMilli(existingEvent.endTime)
            .atZone(ZoneId.systemDefault())
            .toLocalTime().minute
    } else {
        0
    }

    var startHour by remember { mutableStateOf(initialStartHour) }
    var startMinute by remember { mutableStateOf(initialStartMinute) }
    var endHour by remember { mutableStateOf(initialEndHour) }
    var endMinute by remember { mutableStateOf(initialEndMinute) }

    // Track duration for smart end time adjustment (in minutes)
    var durationMinutes by remember {
        mutableStateOf(
            ((initialEndHour * 60 + initialEndMinute) - (initialStartHour * 60 + initialStartMinute)).let {
                if (it <= 0) it + 24 * 60 else it  // Handle overnight events
            }
        )
    }

    var location by remember { mutableStateOf(prefillLocation ?: existingEvent?.location ?: "") }
    var description by remember { mutableStateOf(prefillDescription ?: existingEvent?.description ?: "") }
    // Attendees - stored as comma-separated names/emails
    // Voice input provides names, user can edit to add emails for Google sync
    var attendees by remember {
        mutableStateOf(
            prefillAttendees?.joinToString(", ")
                ?: existingEvent?.attendees
                ?: ""
        )
    }

    // Determine which mode we're in
    val isStandaloneMode = syncMode == AppSettings.SyncMode.STANDALONE
    val isHybridMode = syncMode == AppSettings.SyncMode.HYBRID

    // Check if editing an existing synced event (provider cannot be changed)
    val isEditingSyncedEvent = isEditMode && existingProviderId != null

    // How many cloud providers are connected?
    val connectedProviderCount = listOf(hasGoogleAccount, hasOutlookAccount).count { it }
    val hasMultipleProviders = connectedProviderCount >= 2
    val hasAnyCloudAccount = connectedProviderCount > 0

    // Whether sync toggle will be shown (affects dialog height)
    // Don't show for editing synced events - they stay synced to their provider
    val showSyncToggle = isHybridMode && hasAnyCloudAccount && !isEditingSyncedEvent

    // Sync toggle state for hybrid mode
    // In Hybrid mode: default to sync ON if cloud account exists
    // For existing synced events: always ON (cannot unsync in this dialog)
    // For existing local events: preserve their current sync state
    var syncEnabled by remember {
        mutableStateOf(
            when {
                isEditingSyncedEvent -> true  // Synced events stay synced
                existingSyncEnabled != null -> existingSyncEnabled
                isHybridMode && hasAnyCloudAccount -> true  // Hybrid mode defaults to sync ON
                else -> false
            }
        )
    }

    // Selected cloud provider for sync (when syncEnabled = true)
    // Default: Google if available, else Outlook
    // For existing synced events: use the existing provider (read-only)
    var selectedProviderId by remember {
        mutableStateOf(
            existingProviderId ?: when {
                hasGoogleAccount -> ProviderId.GOOGLE
                hasOutlookAccount -> ProviderId.OUTLOOK
                else -> null
            }
        )
    }

    val calendarsForSelectedProvider = remember(writableCalendars, selectedProviderId) {
        selectedProviderId?.let { provider ->
            writableCalendars.filter { it.providerId == provider }
        } ?: emptyList()
    }

    LaunchedEffect(selectedProviderId, writableCalendars, isEditingSyncedEvent) {
        if (isEditingSyncedEvent) return@LaunchedEffect
        if (selectedProviderId == null) {
            selectedCalendarId = ""
            return@LaunchedEffect
        }
        // Re-compute calendars within effect to ensure fresh data
        // This guards against closure capturing stale values
        val currentProviderCalendars = selectedProviderId.let { provider ->
            writableCalendars.filter { it.providerId == provider }
        }
        android.util.Log.d("AddEditEventDialog", "Provider changed to: $selectedProviderId")
        android.util.Log.d("AddEditEventDialog", "Total writableCalendars: ${writableCalendars.size}")
        android.util.Log.d("AddEditEventDialog", "Calendars for provider $selectedProviderId: ${currentProviderCalendars.size}")
        currentProviderCalendars.forEach { cal ->
            android.util.Log.d("AddEditEventDialog", "  - Calendar: ${cal.name} (id=${cal.id}, accountId=${cal.accountId})")
        }
        android.util.Log.d("AddEditEventDialog", "Current selectedCalendarId: $selectedCalendarId")
        if (currentProviderCalendars.none { it.id == selectedCalendarId }) {
            val newCalendarId = currentProviderCalendars.find { it.isPrimary }?.id
                ?: currentProviderCalendars.firstOrNull()?.id
                ?: ""
            android.util.Log.d("AddEditEventDialog", "Updating selectedCalendarId from '$selectedCalendarId' to '$newCalendarId'")
            selectedCalendarId = newCalendarId
        }
    }

    // Local calendar selection (for sync OFF / Standalone mode)
    // Always auto-select default calendar
    var selectedLocalCalendarId by remember {
        mutableStateOf(
            existingLocalCalendarId
                ?: localCalendars.find { it.isDefault }?.id
                ?: localCalendars.firstOrNull()?.id
        )
    }

    // Time validation - check if end time is before start time (same day)
    val endBeforeStart = !isAllDay && run {
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute
        endMinutes <= startMinutes
    }

    // Validation
    // Need local calendar when sync is OFF, or cloud calendar that belongs to selected provider when sync is ON
    // The calendar validation prevents race condition: when switching providers, selectedCalendarId
    // may still have the old provider's calendar ID until LaunchedEffect updates it
    val isValid = title.isNotBlank() && !endBeforeStart && when {
        syncEnabled -> selectedProviderId != null &&
            calendarsForSelectedProvider.any { it.id == selectedCalendarId }
        else -> selectedLocalCalendarId != null || localCalendars.isEmpty()  // Need local calendar (or allow empty if no calendars)
    }

    // Dropdown and picker states
    var showCalendarDropdown by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showMoveToLocalConfirm by remember { mutableStateOf(false) }

    // E-Ink: Use Popup instead of Dialog to avoid system scrim/dimming
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                    // Extra height when sync section visible; narrow screens (Go 6, Oct 2026) use the panel
                    // height so the form needs less scrolling
                    .fillMaxHeight(
                        if (com.boox.day.ui.theme.LayoutProfiles.isNarrowScreen) EInkTheme.PanelDialog.heightFraction
                        else if (showSyncToggle || isEditingSyncedEvent) 0.81f else 0.78f
                    )
                    .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = EInkTheme.ModernDialog.padding, vertical = EInkTheme.Global.spacing)
                ) {
                    // Header (compact)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEditMode) "Edit Event" else "New Event",
                            fontSize = EInkTheme.Typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.ModernDialog.titleColor
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Voice input button (only for new events) - respects AI enabled setting
                            // Uses same icon as CalendarView toolbar for consistency
                            if (!isEditMode && onVoiceInput != null) {
                                val aiEnabled = GlobalAppSettings.current.aiEnabled
                                IconButton(
                                    onClick = { if (aiEnabled) onVoiceInput() },
                                    enabled = aiEnabled,
                                    modifier = Modifier
                                        .size(EInkTheme.Global.touchTarget)
                                        .alpha(if (aiEnabled) 1f else 0.35f)  // More obvious disabled state
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_voice_input),
                                        contentDescription = "Voice input",
                                        tint = if (aiEnabled) EInkTheme.Colors.primary else EInkTheme.Toolbar.iconColorDisabled,
                                        modifier = Modifier.size(EInkTheme.CalendarToolbar.iconSizeStandard)
                                    )
                                }
                            }
                        }
                    }

                    // Low confidence warning from voice input
                    if (prefillConfidenceLow && prefillTitle != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            backgroundColor = EInkTheme.Warning.background,
                            elevation = 0.dp,
                            border = BorderStroke(EInkTheme.Warning.borderWidth, EInkTheme.Warning.borderColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.info_40px),
                                    contentDescription = "Info",
                                    tint = EInkTheme.Warning.iconColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Please verify the details - some were inferred from your speech.",
                                    fontSize = 12.sp,
                                    color = EInkTheme.Warning.textColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Scrollable content with tighter spacing
                    val scrollState = rememberScrollState()
                    val showScrollHint = scrollState.value == 0 && scrollState.maxValue > 0

                    Box(modifier = Modifier.weight(1f)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                        // Calendar + Date row - combined for space efficiency
                        // Shows local calendar dropdown when sync is OFF
                        // When sync is ON, cloud provider's primary calendar is used (no dropdown needed)
                        val showLocalCalendarSelector = !syncEnabled && localCalendars.isNotEmpty()

                        if (showLocalCalendarSelector) {
                            // Combined row: Local Calendar (left) + Date (right)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Local calendar selector column (60% width)
                                Column(modifier = Modifier.weight(0.6f)) {
                                    Text(
                                        text = "Calendar",
                                        fontSize = EInkTheme.TextField.labelFontSize,
                                        fontWeight = FontWeight.Medium,
                                        color = EInkTheme.TextField.labelColor
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LocalCalendarDropdown(
                                        calendars = localCalendars,
                                        selectedCalendarId = selectedLocalCalendarId,
                                        onCalendarSelected = { selectedLocalCalendarId = it },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // Date selector column (40% width)
                                Column(modifier = Modifier.weight(0.4f)) {
                                    Text(
                                        text = "Date",
                                        fontSize = EInkTheme.TextField.labelFontSize,
                                        fontWeight = FontWeight.Medium,
                                        color = EInkTheme.TextField.labelColor
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .noRippleClickable { showDatePicker = true },
                                        backgroundColor = EInkTheme.Colors.background,
                                        elevation = 0.dp,
                                        border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.TextField.borderColor)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.calendar_today_40px),
                                                contentDescription = "Date",
                                                modifier = Modifier.size(18.dp),
                                                tint = EInkTheme.Colors.secondary
                                            )
                                            Text(
                                                text = formatDateCompact(date),
                                                fontSize = 14.sp,
                                                color = EInkTheme.Colors.primary
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // No local calendar selector needed - either:
                            // - syncEnabled = true (cloud provider selection is in sync section below)
                            // - or no local calendars exist
                            // Just show date selector (full width)
                            Text(
                                text = "Date",
                                fontSize = EInkTheme.TextField.labelFontSize,
                                fontWeight = FontWeight.Medium,
                                color = EInkTheme.TextField.labelColor
                            )
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .noRippleClickable { showDatePicker = true },
                                backgroundColor = EInkTheme.Colors.background,
                                elevation = 0.dp,
                                border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.TextField.borderColor)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.calendar_today_40px),
                                            contentDescription = "Date",
                                            modifier = Modifier.size(20.dp),
                                            tint = EInkTheme.Colors.secondary
                                        )
                                        Text(
                                            text = formatDateDisplay(date),
                                            fontSize = EInkTheme.Typography.bodyMedium,
                                            color = EInkTheme.Colors.primary
                                        )
                                    }
                                    Icon(
                                        painter = painterResource(R.drawable.edit_square_40px),
                                        contentDescription = "Change date",
                                        tint = EInkTheme.Colors.disabled,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Title input
                        Text(
                            text = "Title *",
                            fontSize = EInkTheme.TextField.labelFontSize,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.TextField.labelColor
                        )
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Event title", color = EInkTheme.TextField.hintColor) },
                            singleLine = true,
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                textColor = EInkTheme.TextField.textColor,
                                cursorColor = EInkTheme.Colors.primary,
                                focusedBorderColor = EInkTheme.TextField.focusedBorderColor,
                                unfocusedBorderColor = EInkTheme.TextField.borderColor
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )

                        // All-day toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "All-day event",
                                fontSize = EInkTheme.Typography.bodyLarge,
                                color = EInkTheme.Colors.primary
                            )
                            EInkToggle(
                                checked = isAllDay,
                                onCheckedChange = { isAllDay = it }
                            )
                        }

                        // Time selectors (only if not all-day) - compact single row
                        if (!isAllDay) {
                            // Duration dropdown state
                            var showDurationDropdown by remember { mutableStateOf(false) }
                            val durationOptions = listOf(
                                15 to "15m",
                                30 to "30m",
                                45 to "45m",
                                60 to "1h",
                                90 to "1.5h",
                                120 to "2h",
                                180 to "3h",
                                240 to "4h"
                            )
                            val selectedDurationLabel = durationOptions.find { it.first == durationMinutes }?.second
                                ?: "${durationMinutes}m"

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Start time - compact
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Start",
                                        fontSize = EInkTheme.Typography.labelSmall,
                                        color = EInkTheme.TextField.labelColor
                                    )
                                    TimeSelector(
                                        hour = startHour,
                                        minute = startMinute,
                                        onHourChange = { newStartHour ->
                                            val newStartMinutes = newStartHour * 60 + startMinute
                                            startHour = newStartHour
                                            // Smart end time: maintain duration
                                            val newEndMinutes = newStartMinutes + durationMinutes
                                            endHour = (newEndMinutes / 60) % 24
                                            endMinute = newEndMinutes % 60
                                        },
                                        onMinuteChange = { newStartMinute ->
                                            val newStartMinutes = startHour * 60 + newStartMinute
                                            startMinute = newStartMinute
                                            // Smart end time: maintain duration
                                            val newEndMinutes = newStartMinutes + durationMinutes
                                            endHour = (newEndMinutes / 60) % 24
                                            endMinute = newEndMinutes % 60
                                        },
                                        compact = true
                                    )
                                }

                                // End time - compact
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "End",
                                        fontSize = EInkTheme.Typography.labelSmall,
                                        color = EInkTheme.TextField.labelColor
                                    )
                                    TimeSelector(
                                        hour = endHour,
                                        minute = endMinute,
                                        onHourChange = { newEndHour ->
                                            endHour = newEndHour
                                            // Update tracked duration
                                            val startMinutes = startHour * 60 + startMinute
                                            val endMinutes = newEndHour * 60 + endMinute
                                            durationMinutes = if (endMinutes > startMinutes) {
                                                endMinutes - startMinutes
                                            } else {
                                                (endMinutes + 24 * 60) - startMinutes
                                            }
                                        },
                                        onMinuteChange = { newEndMinute ->
                                            endMinute = newEndMinute
                                            // Update tracked duration
                                            val startMinutes = startHour * 60 + startMinute
                                            val endMinutes = endHour * 60 + newEndMinute
                                            durationMinutes = if (endMinutes > startMinutes) {
                                                endMinutes - startMinutes
                                            } else {
                                                (endMinutes + 24 * 60) - startMinutes
                                            }
                                        },
                                        compact = true
                                    )
                                }

                                // Duration dropdown - compact
                                Box {
                                    Column {
                                        Text(
                                            text = "Duration",
                                            fontSize = EInkTheme.Typography.labelSmall,
                                            color = EInkTheme.TextField.labelColor
                                        )
                                        OutlinedButton(
                                            onClick = { showDurationDropdown = true },
                                            modifier = Modifier.height(42.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = EInkTheme.Colors.primary
                                            ),
                                            border = BorderStroke(1.dp, EInkTheme.TextField.borderColor),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = selectedDurationLabel,
                                                fontSize = EInkTheme.Typography.bodyMedium
                                            )
                                            Icon(
                                                painter = painterResource(R.drawable.expand_circle_down_40px),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // Duration dropdown menu
                                    DropdownMenu(
                                        expanded = showDurationDropdown,
                                        onDismissRequest = { showDurationDropdown = false }
                                    ) {
                                        durationOptions.forEach { (mins, label) ->
                                            DropdownMenuItem(
                                                onClick = {
                                                    durationMinutes = mins
                                                    val startMinutes = startHour * 60 + startMinute
                                                    val newEndMinutes = startMinutes + mins
                                                    endHour = (newEndMinutes / 60) % 24
                                                    endMinute = newEndMinutes % 60
                                                    showDurationDropdown = false
                                                }
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontWeight = if (mins == durationMinutes) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Time validation warning
                            if (endBeforeStart) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.warning_40px),
                                        contentDescription = "Warning",
                                        tint = EInkTheme.Error.iconColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "End time must be after start time",
                                        fontSize = 12.sp,
                                        color = EInkTheme.Error.textColor
                                    )
                                }
                            }
                        }

                        // Attendees input (only visible when syncing to Google Calendar)
                        // Google Calendar requires email addresses for attendees
                        // Placed above Location for logical grouping (who, where, what)
                        if (showSyncToggle && syncEnabled) {
                            Text(
                                text = "Attendees",
                                fontSize = EInkTheme.TextField.labelFontSize,
                                fontWeight = FontWeight.Medium,
                                color = EInkTheme.TextField.labelColor
                            )
                            OutlinedTextField(
                                value = attendees,
                                onValueChange = { attendees = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        "Names or emails, comma-separated",
                                        color = EInkTheme.TextField.hintColor,
                                        fontSize = 14.sp
                                    )
                                },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.person_40px),
                                        contentDescription = "Attendees",
                                        tint = EInkTheme.Colors.disabled,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    textColor = EInkTheme.TextField.textColor,
                                    cursorColor = EInkTheme.Colors.primary,
                                    focusedBorderColor = EInkTheme.TextField.focusedBorderColor,
                                    unfocusedBorderColor = EInkTheme.TextField.borderColor
                                )
                            )
                            // Helper text for attendees
                            Text(
                                text = "For calendar invites, use email addresses",
                                fontSize = EInkTheme.Typography.labelSmall,
                                color = EInkTheme.Colors.secondary
                            )
                        }

                        // Location input - compact row with inline buttons
                        Text(
                            text = "Location",
                            fontSize = EInkTheme.TextField.labelFontSize,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.TextField.labelColor
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(EInkTheme.TextField.InlineAction.spacing),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = location,
                                onValueChange = { location = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Location or link", color = EInkTheme.TextField.hintColor, fontSize = 14.sp) },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.location_on_40px),
                                        contentDescription = "Location",
                                        tint = EInkTheme.Colors.disabled,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    textColor = EInkTheme.TextField.textColor,
                                    cursorColor = EInkTheme.Colors.primary,
                                    focusedBorderColor = EInkTheme.TextField.focusedBorderColor,
                                    unfocusedBorderColor = EInkTheme.TextField.borderColor
                                )
                            )
                            // Paste button - icon only (themed)
                            IconButton(
                                onClick = {
                                    val clipText = clipboardManager.getText()?.text
                                    if (!clipText.isNullOrBlank()) {
                                        location = clipText
                                        Toast.makeText(context, "Pasted", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Clipboard empty", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(EInkTheme.TextField.InlineAction.buttonSize)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.content_paste_40px),
                                    contentDescription = "Paste",
                                    modifier = Modifier.size(EInkTheme.TextField.InlineAction.iconSize),
                                    tint = EInkTheme.TextField.InlineAction.iconColor
                                )
                            }
                            // Meet button - icon only (themed)
                            IconButton(
                                onClick = { location = "Google Meet (link will be added)" },
                                modifier = Modifier.size(EInkTheme.TextField.InlineAction.buttonSize)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.videocam_40px),
                                    contentDescription = "Add Meet",
                                    modifier = Modifier.size(EInkTheme.TextField.InlineAction.iconSize),
                                    tint = EInkTheme.TextField.InlineAction.iconColor
                                )
                            }
                        }

                        // Description input - compact row with inline paste button
                        Text(
                            text = "Description",
                            fontSize = EInkTheme.TextField.labelFontSize,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.TextField.labelColor
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(EInkTheme.TextField.InlineAction.spacing),
                            verticalAlignment = Alignment.Top
                        ) {
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp),  // 3 lines
                                placeholder = { Text("Notes", color = EInkTheme.TextField.hintColor, fontSize = 14.sp) },
                                maxLines = 3,
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    textColor = EInkTheme.TextField.textColor,
                                    cursorColor = EInkTheme.Colors.primary,
                                    focusedBorderColor = EInkTheme.TextField.focusedBorderColor,
                                    unfocusedBorderColor = EInkTheme.TextField.borderColor
                                )
                            )
                            // Paste button - icon only (themed)
                            IconButton(
                                onClick = {
                                    val clipText = clipboardManager.getText()?.text
                                    if (!clipText.isNullOrBlank()) {
                                        description = if (description.isBlank()) clipText else "$description\n$clipText"
                                        Toast.makeText(context, "Pasted", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Clipboard empty", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(EInkTheme.TextField.InlineAction.buttonSize)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.content_paste_40px),
                                    contentDescription = "Paste",
                                    modifier = Modifier.size(EInkTheme.TextField.InlineAction.iconSize),
                                    tint = EInkTheme.TextField.InlineAction.iconColor
                                )
                            }
                        }

                        // Sync section - shows either:
                        // 1. Toggle (new/local events in Hybrid mode)
                        // 2. Read-only provider info (editing synced events)
                        // 3. Nothing (Standalone mode or no cloud accounts)

                        // Case 1: Editing a synced event - show current sync state and move-to-local option
                        if (isEditingSyncedEvent && existingProviderId != null) {
                            Divider(color = EInkTheme.Divider.subtleColor, thickness = EInkTheme.Divider.thickness)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Current sync state - clear indicator
                                Text(
                                    text = "Event Location",
                                    fontSize = EInkTheme.TextField.labelFontSize,
                                    fontWeight = FontWeight.Medium,
                                    color = EInkTheme.TextField.labelColor
                                )

                                // Current state card - shows where event is synced
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    backgroundColor = EInkTheme.Colors.lightGray.copy(alpha = 0.3f),
                                    elevation = 0.dp,
                                    border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.lightGray)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.cloud_40px),
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = EInkTheme.Colors.primary
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Synced to ${existingProviderId.displayName}",
                                                fontSize = EInkTheme.Typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                                color = EInkTheme.Colors.primary
                                            )
                                            Text(
                                                text = "Changes sync across all your devices",
                                                fontSize = EInkTheme.Typography.bodySmall,
                                                color = EInkTheme.Colors.secondary
                                            )
                                        }
                                    }
                                }

                                // Move to local option - clear actionable card
                                if (onMoveToLocal != null && existingEvent != null) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .noRippleClickable { showMoveToLocalConfirm = true },
                                        backgroundColor = EInkTheme.Colors.background,
                                        elevation = 0.dp,
                                        border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.TextField.borderColor)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PhoneAndroid,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = EInkTheme.Colors.primary
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Move to this device only",
                                                    fontSize = EInkTheme.Typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium,
                                                    color = EInkTheme.Colors.primary
                                                )
                                                Text(
                                                    text = "Remove from ${existingProviderId.displayName}, keep only here",
                                                    fontSize = EInkTheme.Typography.bodySmall,
                                                    color = EInkTheme.Colors.secondary
                                                )
                                            }
                                            Icon(
                                                painter = painterResource(R.drawable.expand_circle_right_40px),
                                                contentDescription = "Move to local",
                                                modifier = Modifier.size(20.dp),
                                                tint = EInkTheme.Colors.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // Case 2: New/local event with sync selection available (card-based UI)
                        else if (showSyncToggle) {
                            Divider(color = EInkTheme.Divider.subtleColor, thickness = EInkTheme.Divider.thickness)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Section header
                                Text(
                                    text = "Save to:",
                                    fontSize = EInkTheme.TextField.labelFontSize,
                                    fontWeight = FontWeight.Medium,
                                    color = EInkTheme.TextField.labelColor
                                )

                                // Card 1: This device only (local)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .noRippleClickable { syncEnabled = false },
                                    backgroundColor = if (!syncEnabled)
                                        EInkTheme.Colors.lightGray.copy(alpha = 0.3f)
                                    else
                                        EInkTheme.Colors.background,
                                    elevation = 0.dp,
                                    border = BorderStroke(
                                        if (!syncEnabled) 2.dp else EInkTheme.Global.borderWidth,
                                        if (!syncEnabled) EInkTheme.Colors.primary else EInkTheme.TextField.borderColor
                                    )
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PhoneAndroid,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = EInkTheme.Colors.primary
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "This device only",
                                                    fontSize = EInkTheme.Typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium,
                                                    color = EInkTheme.Colors.primary
                                                )
                                                Text(
                                                    text = "Private — won't sync to cloud calendars",
                                                    fontSize = EInkTheme.Typography.bodySmall,
                                                    color = EInkTheme.Colors.secondary
                                                )
                                            }
                                            if (!syncEnabled) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    modifier = Modifier.size(20.dp),
                                                    tint = EInkTheme.Colors.primary
                                                )
                                            }
                                        }

                                        // Local calendar selection (only when local is selected and there are calendars)
                                        if (!syncEnabled && localCalendars.isNotEmpty()) {
                                            Divider(
                                                color = EInkTheme.Divider.subtleColor,
                                                thickness = EInkTheme.Divider.thickness,
                                                modifier = Modifier.padding(horizontal = 12.dp)
                                            )

                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "Calendar:",
                                                    fontSize = EInkTheme.TextField.labelFontSize,
                                                    fontWeight = FontWeight.Medium,
                                                    color = EInkTheme.TextField.labelColor
                                                )

                                                LocalCalendarDropdown(
                                                    calendars = localCalendars,
                                                    selectedCalendarId = selectedLocalCalendarId,
                                                    onCalendarSelected = { selectedLocalCalendarId = it },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }

                                // Card 2: Cloud Calendar (sync enabled)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .noRippleClickable { syncEnabled = true },
                                    backgroundColor = if (syncEnabled)
                                        EInkTheme.Colors.lightGray.copy(alpha = 0.3f)
                                    else
                                        EInkTheme.Colors.background,
                                    elevation = 0.dp,
                                    border = BorderStroke(
                                        if (syncEnabled) 2.dp else EInkTheme.Global.borderWidth,
                                        if (syncEnabled) EInkTheme.Colors.primary else EInkTheme.TextField.borderColor
                                    )
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.cloud_40px),
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = EInkTheme.Colors.primary
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Cloud Calendar",
                                                    fontSize = EInkTheme.Typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium,
                                                    color = EInkTheme.Colors.primary
                                                )
                                                Text(
                                                    text = "Available on phone, computer & other devices",
                                                    fontSize = EInkTheme.Typography.bodySmall,
                                                    color = EInkTheme.Colors.secondary
                                                )
                                            }
                                            if (syncEnabled) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    modifier = Modifier.size(20.dp),
                                                    tint = EInkTheme.Colors.primary
                                                )
                                            }
                                        }

                                        // Provider and calendar selection (only when cloud selected)
                                        if (syncEnabled) {
                                            Divider(
                                                color = EInkTheme.Divider.subtleColor,
                                                thickness = EInkTheme.Divider.thickness,
                                                modifier = Modifier.padding(horizontal = 12.dp)
                                            )

                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Provider selection (if multiple providers)
                                                if (hasMultipleProviders) {
                                                    val googleAccountEmail = writableCalendars
                                                        .firstOrNull { it.providerId == ProviderId.GOOGLE }?.accountEmail
                                                    val outlookAccountEmail = writableCalendars
                                                        .firstOrNull { it.providerId == ProviderId.OUTLOOK }?.accountEmail

                                                    Text(
                                                        text = "Account:",
                                                        fontSize = EInkTheme.TextField.labelFontSize,
                                                        fontWeight = FontWeight.Medium,
                                                        color = EInkTheme.TextField.labelColor
                                                    )

                                                    // Google option
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .noRippleClickable { selectedProviderId = ProviderId.GOOGLE },
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        RadioButton(
                                                            selected = selectedProviderId == ProviderId.GOOGLE,
                                                            onClick = { selectedProviderId = ProviderId.GOOGLE },
                                                            colors = RadioButtonDefaults.colors(
                                                                selectedColor = EInkTheme.Colors.primary,
                                                                unselectedColor = EInkTheme.Colors.secondary
                                                            ),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Text(
                                                            text = "Google",
                                                            fontSize = EInkTheme.Typography.bodyMedium,
                                                            fontWeight = FontWeight.Medium,
                                                            color = EInkTheme.Colors.primary
                                                        )
                                                        if (googleAccountEmail != null) {
                                                            Text(
                                                                text = googleAccountEmail,
                                                                fontSize = EInkTheme.Typography.bodySmall,
                                                                color = EInkTheme.Colors.secondary
                                                            )
                                                        }
                                                    }

                                                    // Outlook option
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .noRippleClickable { selectedProviderId = ProviderId.OUTLOOK },
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        RadioButton(
                                                            selected = selectedProviderId == ProviderId.OUTLOOK,
                                                            onClick = { selectedProviderId = ProviderId.OUTLOOK },
                                                            colors = RadioButtonDefaults.colors(
                                                                selectedColor = EInkTheme.Colors.primary,
                                                                unselectedColor = EInkTheme.Colors.secondary
                                                            ),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Text(
                                                            text = "Outlook",
                                                            fontSize = EInkTheme.Typography.bodyMedium,
                                                            fontWeight = FontWeight.Medium,
                                                            color = EInkTheme.Colors.primary
                                                        )
                                                        if (outlookAccountEmail != null) {
                                                            Text(
                                                                text = outlookAccountEmail,
                                                                fontSize = EInkTheme.Typography.bodySmall,
                                                                color = EInkTheme.Colors.secondary
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(4.dp))
                                                }
                                                // Single provider - show which one
                                                else if (selectedProviderId != null) {
                                                    val accountEmail = writableCalendars
                                                        .firstOrNull { it.providerId == selectedProviderId }?.accountEmail
                                                    Text(
                                                        text = if (accountEmail != null)
                                                            "Syncs to ${selectedProviderId!!.displayName} · $accountEmail"
                                                        else
                                                            "Syncs to ${selectedProviderId!!.displayName}",
                                                        fontSize = EInkTheme.Typography.bodySmall,
                                                        color = EInkTheme.Colors.secondary
                                                    )
                                                }

                                                // Calendar selection dropdown
                                                if (selectedProviderId != null) {
                                                    Text(
                                                        text = "Calendar:",
                                                        fontSize = EInkTheme.TextField.labelFontSize,
                                                        fontWeight = FontWeight.Medium,
                                                        color = EInkTheme.TextField.labelColor
                                                    )

                                                    val selectedCalendar = calendarsForSelectedProvider
                                                        .firstOrNull { it.id == selectedCalendarId }
                                                    val selectedCalendarName = when {
                                                        selectedCalendar == null -> "Select calendar"
                                                        selectedCalendar.isPrimary -> "${selectedCalendar.name} (Primary)"
                                                        else -> selectedCalendar.name
                                                    }

                                                    Box {
                                                        Card(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .noRippleClickable { showCalendarDropdown = true },
                                                            backgroundColor = EInkTheme.Colors.background,
                                                            elevation = 0.dp,
                                                            border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.TextField.borderColor)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text(
                                                                    text = selectedCalendarName,
                                                                    fontSize = EInkTheme.Typography.bodyMedium,
                                                                    color = EInkTheme.Colors.primary
                                                                )
                                                                Icon(
                                                                    painter = painterResource(R.drawable.expand_circle_down_40px),
                                                                    contentDescription = null,
                                                                    tint = EInkTheme.Colors.secondary,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                        DropdownMenu(
                                                            expanded = showCalendarDropdown,
                                                            onDismissRequest = { showCalendarDropdown = false }
                                                        ) {
                                                            calendarsForSelectedProvider.forEach { calendar ->
                                                                val displayName = if (calendar.isPrimary) {
                                                                    "${calendar.name} (Primary)"
                                                                } else {
                                                                    calendar.name
                                                                }
                                                                DropdownMenuItem(
                                                                    onClick = {
                                                                        selectedCalendarId = calendar.id
                                                                        showCalendarDropdown = false
                                                                    }
                                                                ) {
                                                                    Text(
                                                                        text = displayName,
                                                                        fontSize = EInkTheme.Typography.bodyMedium,
                                                                        fontWeight = if (calendar.isPrimary) FontWeight.Medium else FontWeight.Normal
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }

                        if (showMoveToLocalConfirm && existingEvent != null && existingProviderId != null) {
                            MoveToLocalConfirmDialog(
                                providerName = existingProviderId.displayName,
                                eventTitle = title,
                                onConfirm = {
                                    showMoveToLocalConfirm = false
                                    onMoveToLocal?.invoke(existingEvent)
                                },
                                onCancel = { showMoveToLocalConfirm = false }
                            )
                        }

                        // Scroll hint at bottom when more content exists
                        if (showScrollHint) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.expand_circle_down_40px),
                                    contentDescription = "Scroll for more",
                                    modifier = Modifier.size(16.dp),
                                    tint = EInkTheme.Colors.disabled
                                )
                                Text(
                                    text = "scroll for more",
                                    fontSize = EInkTheme.Typography.labelSmall,
                                    color = EInkTheme.Colors.disabled
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action buttons - Dialog Rulebook compliant
                    // 2-button: [Cancel (40%)] [gap (20%)] [Save (40%)]
                    // 3-button: [Cancel] [Delete] [Save] with equal weights + spacing
                    if (isEditMode && onDelete != null) {
                        // 3-button layout: Cancel | Delete | Save
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Cancel button (leftmost - escape action)
                            EinkSecondaryButton(
                                text = "Cancel",
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                enabled = !isLoading
                            )

                            // Delete button (middle - destructive action)
                            EinkSecondaryButton(
                                text = "Delete",
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.weight(1f),
                                enabled = !isLoading
                            )

                            // Save button (rightmost - primary constructive action)
                            ActionButton(
                                text = "Save",
                                modifier = Modifier.weight(1f),
                                enabled = isValid && !isLoading,
                                isLoading = isLoading,
                                onClick = {
                                    val cloudCalendarId = if (syncEnabled) selectedCalendarId else ""
                                    val providerId = if (syncEnabled) selectedProviderId else null
                                    android.util.Log.d("AddEditEventDialog", "=== SAVE CLICKED ===")
                                    android.util.Log.d("AddEditEventDialog", "syncEnabled: $syncEnabled")
                                    android.util.Log.d("AddEditEventDialog", "selectedProviderId: $selectedProviderId")
                                    android.util.Log.d("AddEditEventDialog", "providerId (passed): $providerId")
                                    android.util.Log.d("AddEditEventDialog", "selectedCalendarId: $selectedCalendarId")
                                    android.util.Log.d("AddEditEventDialog", "cloudCalendarId (passed): $cloudCalendarId")
                                    android.util.Log.d("AddEditEventDialog", "title: ${title.trim()}")
                                    android.util.Log.d("AddEditEventDialog", "isValid: $isValid")
                                    onSave(
                                        cloudCalendarId,
                                        title.trim(),
                                        date,
                                        startHour,
                                        startMinute,
                                        if (isAllDay) null else endHour,
                                        if (isAllDay) null else endMinute,
                                        isAllDay,
                                        location.trim().ifBlank { null },
                                        description.trim().ifBlank { null },
                                        attendees.trim().ifBlank { null },
                                        syncEnabled,
                                        selectedLocalCalendarId,
                                        providerId
                                    )
                                }
                            )
                        }
                    } else {
                        // 2-button layout: Cancel | gap | Create/Update Event
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Cancel button (leftmost - escape action)
                            EinkSecondaryButton(
                                text = "Cancel",
                                onClick = onDismiss,
                                modifier = Modifier.weight(0.4f),
                                enabled = !isLoading
                            )

                            Spacer(modifier = Modifier.weight(0.2f))

                            // Create/Update button (rightmost - primary constructive action)
                            ActionButton(
                                text = if (isEditMode) "Update Event" else "Create Event",
                                modifier = Modifier.weight(0.4f),
                                enabled = isValid && !isLoading,
                                isLoading = isLoading,
                                onClick = {
                                    val cloudCalendarId = if (syncEnabled) selectedCalendarId else ""
                                    onSave(
                                        cloudCalendarId,
                                        title.trim(),
                                        date,
                                        startHour,
                                        startMinute,
                                        if (isAllDay) null else endHour,
                                        if (isAllDay) null else endMinute,
                                        isAllDay,
                                        location.trim().ifBlank { null },
                                        description.trim().ifBlank { null },
                                        attendees.trim().ifBlank { null },
                                        syncEnabled,
                                        selectedLocalCalendarId,
                                        if (syncEnabled) selectedProviderId else null
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog - E-Ink styled
    if (showDeleteConfirm && onDelete != null) {
        Popup(
            alignment = Alignment.Center,
            onDismissRequest = { showDeleteConfirm = false },
            properties = PopupProperties(focusable = true)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                        .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                        .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                        .padding(EInkTheme.ModernDialog.padding),
                    verticalArrangement = Arrangement.spacedBy(EInkTheme.ModernDialog.contentSpacing)
                ) {
                    Text(
                        text = "Delete Event",
                        fontWeight = FontWeight.Bold,
                        fontSize = EInkTheme.ModernDialog.titleFontSize,
                        color = EInkTheme.ModernDialog.titleColor
                    )
                    Text(
                        text = "Are you sure you want to delete this event? This cannot be undone.",
                        fontSize = EInkTheme.Typography.bodyLarge,
                        color = EInkTheme.Colors.primary
                    )
                    // 2-button layout per Dialog Rulebook: Cancel (left) | Delete (right)
                    // Destructive action on right is acceptable for pure deletion confirmation
                    Row(modifier = Modifier.fillMaxWidth()) {
                        EinkSecondaryButton(
                            text = "Cancel",
                            onClick = { showDeleteConfirm = false },
                            modifier = Modifier.weight(0.4f)
                        )
                        Spacer(modifier = Modifier.weight(0.2f))
                        ActionButton(
                            text = "Delete Event",
                            onClick = {
                                showDeleteConfirm = false
                                onDelete()
                            },
                            modifier = Modifier.weight(0.4f)
                        )
                    }
                }
            }
        }
    }

    // Date picker popup - E-Ink styled
    if (showDatePicker) {
        Popup(
            alignment = Alignment.Center,
            onDismissRequest = { showDatePicker = false },
            properties = PopupProperties(focusable = true)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .wrapContentHeight()
                        .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                        .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                        .padding(EInkTheme.ModernDialog.padding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Text(
                        text = "Select Date",
                        fontSize = EInkTheme.Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.ModernDialog.titleColor
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Simple month/day picker
                    val currentDate = try {
                        LocalDate.parse(date)
                    } catch (e: Exception) {
                        LocalDate.now()
                    }

                    var pickerYear by remember { mutableStateOf(currentDate.year) }
                    var pickerMonth by remember { mutableStateOf(currentDate.monthValue) }
                    var pickerDay by remember { mutableStateOf(currentDate.dayOfMonth) }

                    // Month/Year navigation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (pickerMonth == 1) {
                                pickerMonth = 12
                                pickerYear--
                            } else {
                                pickerMonth--
                            }
                            // Adjust day if needed
                            val maxDays = LocalDate.of(pickerYear, pickerMonth, 1).lengthOfMonth()
                            if (pickerDay > maxDays) pickerDay = maxDays
                        }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month")
                        }

                        Text(
                            text = "${java.time.Month.of(pickerMonth).name.lowercase()
                                .replaceFirstChar { it.uppercase() }} $pickerYear",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )

                        IconButton(onClick = {
                            if (pickerMonth == 12) {
                                pickerMonth = 1
                                pickerYear++
                            } else {
                                pickerMonth++
                            }
                            // Adjust day if needed
                            val maxDays = LocalDate.of(pickerYear, pickerMonth, 1).lengthOfMonth()
                            if (pickerDay > maxDays) pickerDay = maxDays
                        }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Day of week headers
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                            Text(
                                text = day,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                fontSize = EInkTheme.DatePicker.weekdayFontSize,
                                color = EInkTheme.DatePicker.weekdayColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Calendar grid
                    val firstDayOfMonth = LocalDate.of(pickerYear, pickerMonth, 1)
                    val daysInMonth = firstDayOfMonth.lengthOfMonth()
                    val startDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7 // Sunday = 0

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        userScrollEnabled = false
                    ) {
                        // Empty cells before first day
                        items(startDayOfWeek) {
                            Box(modifier = Modifier.aspectRatio(1f))
                        }

                        // Days of month
                        items(daysInMonth) { index ->
                            val day = index + 1
                            val isSelected = day == pickerDay
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .background(
                                        if (isSelected) EInkTheme.DatePicker.selectedBackground else EInkTheme.Colors.background,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .noRippleClickable { pickerDay = day },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day.toString(),
                                    fontSize = EInkTheme.DatePicker.cellFontSize,
                                    color = if (isSelected) EInkTheme.DatePicker.selectedForeground else EInkTheme.DatePicker.cellColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Button.spacing, Alignment.End)
                    ) {
                        OutlinedButton(
                            onClick = { showDatePicker = false },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EInkTheme.Button.textColor),
                            border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor)
                        ) {
                            Text("Cancel", fontSize = EInkTheme.Typography.button)
                        }
                        Button(
                            onClick = {
                                date = String.format("%04d-%02d-%02d", pickerYear, pickerMonth, pickerDay)
                                showDatePicker = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = EInkTheme.ModernDialog.PrimaryButton.background,
                                contentColor = EInkTheme.ModernDialog.PrimaryButton.textColor
                            )
                        ) {
                            Text("Select", fontSize = EInkTheme.Typography.button)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Time selector that respects user's time format preference (12h/24h).
 * Internal values are always 24-hour (0-23), but display adapts to preference.
 * @param compact When true, uses smaller buttons for inline row layout
 */
// Height of one hour in the list: DropdownMenuItem's minimum (the menu's own padding is outside its scroll)
private val HOUR_ITEM_HEIGHT = 48.dp

@Composable
private fun TimeSelector(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    compact: Boolean = false
) {
    var showHourDropdown by remember { mutableStateOf(false) }
    var showMinuteDropdown by remember { mutableStateOf(false) }
    var showAmPmDropdown by remember { mutableStateOf(false) }

    val is24Hour = com.boox.day.utils.DateTimeFormatUtils.is24HourFormat()
    val isAm = hour < 12

    // Format hour for display
    val displayHour = if (is24Hour) {
        String.format("%02d", hour)
    } else {
        val h = com.boox.day.utils.DateTimeFormatUtils.getDisplayHour(hour)
        h.toString()
    }

    Row(
        modifier = if (compact) Modifier else Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hour selector
        Box(modifier = if (compact) Modifier else Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { showHourDropdown = true },
                modifier = if (compact) Modifier.height(42.dp) else Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = EInkTheme.Button.textColor),
                border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.TextField.borderColor),
                contentPadding = PaddingValues(horizontal = if (compact) 8.dp else 8.dp, vertical = if (compact) 4.dp else 8.dp)
            ) {
                Text(text = displayHour, fontSize = if (compact) 14.sp else EInkTheme.TimePicker.fontSize)
            }
            // Opens at the chosen hour (Oct 2026): after an 11:00 start, the end list began at 00 again.
            // A new state per opening, already in place, so there is no scroll animation on e-ink.
            val density = LocalDensity.current
            val selectedHourIndex = if (is24Hour) hour else com.boox.day.utils.DateTimeFormatUtils.getDisplayHour(hour) - 1
            val hourScrollState = remember(showHourDropdown) {
                ScrollState(with(density) { (HOUR_ITEM_HEIGHT * selectedHourIndex).roundToPx() })
            }
            DropdownMenu(
                expanded = showHourDropdown,
                onDismissRequest = { showHourDropdown = false },
                modifier = Modifier
                    .background(EInkTheme.Colors.background)
                    .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary)
                    .heightIn(max = 200.dp),
                scrollState = hourScrollState
            ) {
                if (is24Hour) {
                    // 24-hour format: show 00-23
                    (0..23).forEach { h ->
                        DropdownMenuItem(onClick = {
                            onHourChange(h)
                            showHourDropdown = false
                        }) {
                            Text(String.format("%02d", h), fontSize = 16.sp)
                        }
                    }
                } else {
                    // 12-hour format: show 1-12
                    (1..12).forEach { displayH ->
                        DropdownMenuItem(onClick = {
                            // Convert display hour to 24-hour format
                            val newHour = when {
                                displayH == 12 && isAm -> 0   // 12 AM = 00
                                displayH == 12 && !isAm -> 12 // 12 PM = 12
                                isAm -> displayH              // AM: 1-11 stays same
                                else -> displayH + 12         // PM: 1-11 + 12
                            }
                            onHourChange(newHour)
                            showHourDropdown = false
                        }) {
                            Text(displayH.toString(), fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        Text(text = ":", fontSize = if (compact) 16.sp else 20.sp, fontWeight = FontWeight.Bold)

        // Minute selector
        Box(modifier = if (compact) Modifier else Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { showMinuteDropdown = true },
                modifier = if (compact) Modifier.height(42.dp) else Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = EInkTheme.Colors.primary),
                border = BorderStroke(1.dp, EInkTheme.Colors.disabled),
                contentPadding = PaddingValues(horizontal = if (compact) 8.dp else 8.dp, vertical = if (compact) 4.dp else 8.dp)
            ) {
                Text(text = String.format("%02d", minute), fontSize = if (compact) 14.sp else 18.sp)
            }
            DropdownMenu(
                expanded = showMinuteDropdown,
                onDismissRequest = { showMinuteDropdown = false },
                modifier = Modifier
                    .background(EInkTheme.Colors.background)
                    .border(1.dp, EInkTheme.Colors.primary)
                    .heightIn(max = 200.dp)
            ) {
                listOf(0, 15, 30, 45).forEach { m ->
                    DropdownMenuItem(onClick = {
                        onMinuteChange(m)
                        showMinuteDropdown = false
                    }) {
                        Text(String.format("%02d", m), fontSize = 16.sp)
                    }
                }
            }
        }

        // AM/PM selector (only for 12-hour format)
        if (!is24Hour) {
            Spacer(modifier = Modifier.width(if (compact) 2.dp else 4.dp))
            Box {
                OutlinedButton(
                    onClick = { showAmPmDropdown = true },
                    modifier = if (compact) Modifier.height(42.dp) else Modifier,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EInkTheme.Colors.primary),
                    border = BorderStroke(1.dp, EInkTheme.Colors.disabled),
                    contentPadding = PaddingValues(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 4.dp else 8.dp)
                ) {
                    Text(text = if (isAm) "AM" else "PM", fontSize = if (compact) 12.sp else 14.sp)
                }
                DropdownMenu(
                    expanded = showAmPmDropdown,
                    onDismissRequest = { showAmPmDropdown = false },
                    modifier = Modifier
                        .background(EInkTheme.Colors.background)
                        .border(1.dp, EInkTheme.Colors.primary)
                ) {
                    DropdownMenuItem(onClick = {
                        // Switch to AM: if currently PM, subtract 12
                        if (!isAm) {
                            onHourChange(if (hour == 12) 0 else hour - 12)
                        }
                        showAmPmDropdown = false
                    }) {
                        Text("AM", fontSize = 16.sp)
                    }
                    DropdownMenuItem(onClick = {
                        // Switch to PM: if currently AM, add 12
                        if (isAm) {
                            onHourChange(if (hour == 0) 12 else hour + 12)
                        }
                        showAmPmDropdown = false
                    }) {
                        Text("PM", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveToLocalConfirmDialog(
    providerName: String,
    eventTitle: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onCancel,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                    .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(
                        EInkTheme.ModernDialog.borderWidth,
                        EInkTheme.ModernDialog.borderColor,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .padding(EInkTheme.ModernDialog.padding),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.ModernDialog.contentSpacing)
            ) {
                Text(
                    text = "Move to Local Only?",
                    fontWeight = FontWeight.Bold,
                    fontSize = EInkTheme.ModernDialog.titleFontSize,
                    color = EInkTheme.ModernDialog.titleColor
                )
                Text(
                    text = "This will permanently remove \"$eventTitle\" from $providerName.",
                    fontWeight = FontWeight.Bold,
                    fontSize = EInkTheme.Typography.bodyLarge,
                    color = EInkTheme.Colors.primary
                )
                Text(
                    text = "The event will only exist on this device. This cannot be undone.",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    color = EInkTheme.Error.textColor
                )
                // 2-button layout per Dialog Rulebook: Cancel (left) | Move to Local (right)
                Row(modifier = Modifier.fillMaxWidth()) {
                    EinkSecondaryButton(
                        text = "Cancel",
                        onClick = onCancel,
                        modifier = Modifier.weight(0.4f)
                    )
                    Spacer(modifier = Modifier.weight(0.2f))
                    ActionButton(
                        text = "Move to Local",
                        onClick = onConfirm,
                        modifier = Modifier.weight(0.4f)
                    )
                }
            }
        }
    }
}

/**
 * Format date for display in dialog. Respects user's date format preference.
 */
private fun formatDateDisplay(date: String): String {
    return com.boox.day.utils.DateTimeFormatUtils.formatDateFullFromString(date)
}

/**
 * Format date in compact form for inline display (e.g., "Dec 11" or "11 Dec")
 */
private fun formatDateCompact(date: String): String {
    return try {
        val localDate = LocalDate.parse(date)
        val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        localDate.format(formatter)
    } catch (e: Exception) {
        date
    }
}
