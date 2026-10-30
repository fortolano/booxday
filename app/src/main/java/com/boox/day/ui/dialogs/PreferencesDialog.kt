package com.boox.day.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.KvProxy
import android.Manifest
import com.boox.day.notifications.NotificationChannelManager
import com.boox.day.notifications.ReminderScheduling
import com.boox.day.ui.permissions.AskState
import com.boox.day.ui.permissions.rememberPermissionAsk
import com.boox.day.notifications.NotificationPermissionHelper
import com.boox.day.ocr.DigitalInkModelManager
import com.boox.day.ui.components.EInkToggle
import com.boox.day.ui.components.IntervalChip
import com.boox.day.ui.components.RadioOption
import com.boox.day.ui.components.ScrollableWithIndicator
import com.boox.day.ui.components.SettingCard
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.delay

/**
 * Preferences Dialog - Consolidates display/format settings.
 *
 * Contents:
 * - Display Section: Date Format, Time Format
 * - Startup Section: Default Tab
 * - Tips Section: Show Tips toggle + Tip Frequency chips
 *
 * E-Ink optimized: Uses Popup (not Dialog), noRippleClickable, EInkTheme constants.
 */
@Composable
fun PreferencesDialog(
    onDismiss: () -> Unit,
    // Date/Time Format
    dateFormat: AppSettings.DateFormat = AppSettings.DateFormat.MDY,
    onDateFormatChanged: (AppSettings.DateFormat) -> Unit = {},
    timeFormat: AppSettings.TimeFormat = AppSettings.TimeFormat.TwelveHour,
    onTimeFormatChanged: (AppSettings.TimeFormat) -> Unit = {},
    // Default Tab
    startTab: AppSettings.StartTab = AppSettings.StartTab.Memos,
    onStartTabChanged: (AppSettings.StartTab) -> Unit = {},
    // Tips feature
    tipsEnabled: Boolean = true,
    onTipsEnabledChanged: (Boolean) -> Unit = {},
    tipInterval: AppSettings.TipInterval = AppSettings.TipInterval.EVERY_HOUR,
    onTipIntervalChanged: (AppSettings.TipInterval) -> Unit = {},
    // Notification settings - decoupled task and event toggles
    taskNotificationsEnabled: Boolean = false,
    onTaskNotificationsEnabledChanged: (Boolean) -> Unit = {},
    eventNotificationsEnabled: Boolean = false,
    onEventNotificationsEnabledChanged: (Boolean) -> Unit = {},
    defaultTaskReminderHour: Int = 9,
    defaultTaskReminderMinute: Int = 0,
    onDefaultTaskReminderChanged: (hour: Int, minute: Int) -> Unit = { _, _ -> },
    defaultEventReminderMinutes: Int = 15,
    onDefaultEventReminderChanged: (minutes: Int) -> Unit = {},
    onOpenAlarmSettings: () -> Unit = {},
    onOpenBatteryOptimizationSettings: () -> Unit = {}
) {
    val context = LocalContext.current

    // Poll for permission changes when user returns from settings
    var currentNotificationPermission by remember {
        mutableStateOf(NotificationPermissionHelper.hasNotificationPermission(context))
    }
    var currentExactAlarmPermission by remember {
        mutableStateOf(NotificationPermissionHelper.canScheduleExactAlarms(context))
    }
    var currentBatteryOptimizationExempt by remember {
        mutableStateOf(NotificationPermissionHelper.isBatteryOptimizationExempt(context))
    }
    var isTaskChannelEnabled by remember {
        mutableStateOf(
            NotificationChannelManager.isChannelEnabled(
                context,
                NotificationChannelManager.CHANNEL_ID_DUE_TASKS
            )
        )
    }
    var isEventChannelEnabled by remember {
        mutableStateOf(
            NotificationChannelManager.isChannelEnabled(
                context,
                NotificationChannelManager.CHANNEL_ID_EVENT_REMINDERS
            )
        )
    }

    // State for showing battery optimization explanation dialog
    var showBatteryExplanationDialog by remember { mutableStateOf(false) }

    // Detect if running on a Boox/Onyx device
    val isBooxDevice = remember { NotificationPermissionHelper.isBooxDevice() }

    // Oct 2026: asks Android, or opens Settings once Android stopped asking (the button did nothing);
    // on a grant, reminders that couldn't be scheduled are scheduled now
    val notificationAsk = rememberPermissionAsk(Manifest.permission.POST_NOTIFICATIONS) {
        currentNotificationPermission = true
        ReminderScheduling.scheduleAfterGrant(context)
    }

    // Convenience: either notification type is enabled
    val anyNotificationsEnabled = taskNotificationsEnabled || eventNotificationsEnabled
    val disabledChannels = mutableListOf<String>().apply {
        if (taskNotificationsEnabled && !isTaskChannelEnabled) {
            add("Task Due Date Reminders")
        }
        if (eventNotificationsEnabled && !isEventChannelEnabled) {
            add("Event Reminders")
        }
    }
    val disabledChannelsText = disabledChannels.joinToString(", ")
    val anyChannelDisabled = disabledChannels.isNotEmpty()

    // Poll every second when any notifications enabled but permission not granted
    // OR when battery optimization not exempt on Boox device
    LaunchedEffect(
        taskNotificationsEnabled,
        eventNotificationsEnabled,
        currentNotificationPermission,
        currentBatteryOptimizationExempt,
        isTaskChannelEnabled,
        isEventChannelEnabled
    ) {
        while (anyNotificationsEnabled &&
            (!currentNotificationPermission ||
                (isBooxDevice && !currentBatteryOptimizationExempt) ||
                (taskNotificationsEnabled && !isTaskChannelEnabled) ||
                (eventNotificationsEnabled && !isEventChannelEnabled))
        ) {
            delay(1000)
            val newNotificationPermission = NotificationPermissionHelper.hasNotificationPermission(context)
            val newExactAlarmPermission = NotificationPermissionHelper.canScheduleExactAlarms(context)
            val newBatteryOptimizationExempt = NotificationPermissionHelper.isBatteryOptimizationExempt(context)
            val newTaskChannelEnabled = NotificationChannelManager.isChannelEnabled(
                context,
                NotificationChannelManager.CHANNEL_ID_DUE_TASKS
            )
            val newEventChannelEnabled = NotificationChannelManager.isChannelEnabled(
                context,
                NotificationChannelManager.CHANNEL_ID_EVENT_REMINDERS
            )

            if (newNotificationPermission != currentNotificationPermission) {
                currentNotificationPermission = newNotificationPermission
            }
            if (newExactAlarmPermission != currentExactAlarmPermission) {
                currentExactAlarmPermission = newExactAlarmPermission
            }
            if (newBatteryOptimizationExempt != currentBatteryOptimizationExempt) {
                currentBatteryOptimizationExempt = newBatteryOptimizationExempt
            }
            if (newTaskChannelEnabled != isTaskChannelEnabled) {
                isTaskChannelEnabled = newTaskChannelEnabled
            }
            if (newEventChannelEnabled != isEventChannelEnabled) {
                isEventChannelEnabled = newEventChannelEnabled
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
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.PanelDialog.widthFraction)  // Oct 2026: was 0.85f
                    .fillMaxHeight(EInkTheme.PanelDialog.heightFraction)  // was 0.75f
                    .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.PopupOverlay.padding)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Preferences",
                            modifier = Modifier.size(24.dp),
                            tint = EInkTheme.Colors.primary
                        )
                        Text(
                            text = "Preferences",
                            fontSize = EInkTheme.PopupOverlay.titleFontSize,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.PopupOverlay.titleColor
                        )
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(EInkTheme.Button.height),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = EInkTheme.Button.textColor
                        ),
                        border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor),
                        shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                    ) {
                        Text(
                            text = "Close",
                            fontSize = EInkTheme.Typography.button,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Content
                ScrollableWithIndicator(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
                    ) {
                        // Date Format Setting
                        SettingCard(
                            title = "Date Format",
                            description = "Choose how dates are displayed"
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingLarge)
                            ) {
                                RadioOption(
                                    label = "MM/DD/YYYY",
                                    isSelected = dateFormat == AppSettings.DateFormat.MDY,
                                    onClick = { onDateFormatChanged(AppSettings.DateFormat.MDY) }
                                )
                                RadioOption(
                                    label = "DD/MM/YYYY",
                                    isSelected = dateFormat == AppSettings.DateFormat.DMY,
                                    onClick = { onDateFormatChanged(AppSettings.DateFormat.DMY) }
                                )
                            }
                        }

                        // Time Format Setting
                        SettingCard(
                            title = "Time Format",
                            description = "Choose how times are displayed"
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingLarge)
                            ) {
                                RadioOption(
                                    label = "12-hour",
                                    isSelected = timeFormat == AppSettings.TimeFormat.TwelveHour,
                                    onClick = { onTimeFormatChanged(AppSettings.TimeFormat.TwelveHour) }
                                )
                                RadioOption(
                                    label = "24-hour",
                                    isSelected = timeFormat == AppSettings.TimeFormat.TwentyFourHour,
                                    onClick = { onTimeFormatChanged(AppSettings.TimeFormat.TwentyFourHour) }
                                )
                            }
                        }

                        // Start Tab Setting
                        SettingCard(
                            title = "Default Tab",
                            description = "Choose which tab opens by default"
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingLarge)
                            ) {
                                RadioOption(
                                    label = "Memos",
                                    isSelected = startTab == AppSettings.StartTab.Memos,
                                    onClick = { onStartTabChanged(AppSettings.StartTab.Memos) }
                                )
                                RadioOption(
                                    label = "Tasks",
                                    isSelected = startTab == AppSettings.StartTab.Tasks,
                                    onClick = { onStartTabChanged(AppSettings.StartTab.Tasks) }
                                )
                            }
                        }

                        // Today's agenda on the BOOX sleep screen (Oct 2026; BOOX tablets only)
                        com.boox.day.sleepscreen.SleepAgendaSetting()

                        // A Markdown note for each day (Oct 2026, export/DailyNotes)
                        com.boox.day.export.DailyNotesSetting()

                        // Show Tips Setting
                        SettingCard(
                            title = "Show Tips",
                            description = "Display helpful tips about app features periodically"
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (tipsEnabled) "Enabled" else "Disabled",
                                        fontSize = EInkTheme.Typography.bodyMedium,
                                        color = EInkTheme.Colors.darkGray
                                    )
                                    EInkToggle(
                                        checked = tipsEnabled,
                                        onCheckedChange = onTipsEnabledChanged
                                    )
                                }

                                // Interval picker - only shown when tips are enabled
                                if (tipsEnabled) {
                                    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))
                                    Text(
                                        text = "Tip Frequency",
                                        fontSize = EInkTheme.Typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = EInkTheme.Colors.primary
                                    )
                                    Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
                                    // All chips on single row with equal distribution (90% width)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(0.9f),
                                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                                    ) {
                                        IntervalChip(
                                            interval = AppSettings.TipInterval.EVERY_15_MINUTES,
                                            isSelected = tipInterval == AppSettings.TipInterval.EVERY_15_MINUTES,
                                            onClick = { onTipIntervalChanged(AppSettings.TipInterval.EVERY_15_MINUTES) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        IntervalChip(
                                            interval = AppSettings.TipInterval.EVERY_30_MINUTES,
                                            isSelected = tipInterval == AppSettings.TipInterval.EVERY_30_MINUTES,
                                            onClick = { onTipIntervalChanged(AppSettings.TipInterval.EVERY_30_MINUTES) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        IntervalChip(
                                            interval = AppSettings.TipInterval.EVERY_HOUR,
                                            isSelected = tipInterval == AppSettings.TipInterval.EVERY_HOUR,
                                            onClick = { onTipIntervalChanged(AppSettings.TipInterval.EVERY_HOUR) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        IntervalChip(
                                            interval = AppSettings.TipInterval.EVERY_4_HOURS,
                                            isSelected = tipInterval == AppSettings.TipInterval.EVERY_4_HOURS,
                                            onClick = { onTipIntervalChanged(AppSettings.TipInterval.EVERY_4_HOURS) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        IntervalChip(
                                            interval = AppSettings.TipInterval.ONCE_A_DAY,
                                            isSelected = tipInterval == AppSettings.TipInterval.ONCE_A_DAY,
                                            onClick = { onTipIntervalChanged(AppSettings.TipInterval.ONCE_A_DAY) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        // ═══════════════════════════════════════════════════════════════
                        // NOTIFICATIONS SECTION
                        // ═══════════════════════════════════════════════════════════════

                        Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                        Text(
                            text = "Notifications",
                            fontSize = EInkTheme.Preferences.sectionHeaderFontSize,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.Preferences.sectionHeaderColor
                        )

                        Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

                        // Task Due Date Reminders Toggle
                        SettingCard(
                            title = "Task Due Date Reminders",
                            description = "Get notified when tasks are due"
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = reminderStatus(
                                            on = taskNotificationsEnabled,
                                            notificationsAllowed = currentNotificationPermission,
                                            channelOn = isTaskChannelEnabled,
                                            batteryOk = !isBooxDevice || currentBatteryOptimizationExempt
                                        ),
                                        fontSize = EInkTheme.Typography.bodyMedium,
                                        color = EInkTheme.Colors.darkGray
                                    )
                                    EInkToggle(
                                        checked = taskNotificationsEnabled,
                                        onCheckedChange = { enabled ->
                                            onTaskNotificationsEnabledChanged(enabled)
                                            if (enabled && NotificationPermissionHelper.shouldPromptBatteryExemption(context)) {
                                                showBatteryExplanationDialog = true
                                            }
                                        }
                                    )
                                }

                                // Task reminder time picker (shown when enabled AND has permission)
                                if (taskNotificationsEnabled && currentNotificationPermission) {
                                    TaskReminderTimePicker(
                                        defaultTaskReminderHour = defaultTaskReminderHour,
                                        defaultTaskReminderMinute = defaultTaskReminderMinute,
                                        onDefaultTaskReminderChanged = onDefaultTaskReminderChanged
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

                        // Event Reminders Toggle
                        SettingCard(
                            title = "Event Reminders",
                            description = "Get notified before events start"
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = reminderStatus(
                                            on = eventNotificationsEnabled,
                                            notificationsAllowed = currentNotificationPermission,
                                            channelOn = isEventChannelEnabled,
                                            batteryOk = !isBooxDevice || currentBatteryOptimizationExempt
                                        ),
                                        fontSize = EInkTheme.Typography.bodyMedium,
                                        color = EInkTheme.Colors.darkGray
                                    )
                                    EInkToggle(
                                        checked = eventNotificationsEnabled,
                                        onCheckedChange = { enabled ->
                                            onEventNotificationsEnabledChanged(enabled)
                                            if (enabled && NotificationPermissionHelper.shouldPromptBatteryExemption(context)) {
                                                showBatteryExplanationDialog = true
                                            }
                                        }
                                    )
                                }

                                // Event reminder time picker (shown when enabled AND has permission)
                                if (eventNotificationsEnabled && currentNotificationPermission) {
                                    EventReminderTimePicker(
                                        defaultEventReminderMinutes = defaultEventReminderMinutes,
                                        onDefaultEventReminderChanged = onDefaultEventReminderChanged
                                    )
                                }
                            }
                        }

                        // Permission request card (shown when any toggle enabled but no permission)
                        if (anyNotificationsEnabled && !currentNotificationPermission) {
                            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))
                            NotificationPermissionCard(
                                // No exact-alarm step on BOOX: reminders always use WorkManager there
                                showExactAlarmStep = !isBooxDevice && !currentExactAlarmPermission,
                                openSettingsInstead = notificationAsk.state == AskState.OPEN_SETTINGS,
                                onRequestNotificationPermission = { notificationAsk.request() },
                                onOpenAlarmSettings = onOpenAlarmSettings
                            )
                        }

                        if (anyNotificationsEnabled && currentNotificationPermission && anyChannelDisabled) {
                            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))
                            NotificationChannelWarningCard(
                                disabledChannelsText = disabledChannelsText,
                                onOpenNotificationSettings = {
                                    NotificationPermissionHelper.openNotificationSettings(context)
                                }
                            )
                        }

                        // Battery optimization warning (shown on Boox devices when notifications enabled
                        // but not exempt from battery optimization - WorkManager jobs get killed without this)
                        if (anyNotificationsEnabled && currentNotificationPermission &&
                            isBooxDevice && !currentBatteryOptimizationExempt) {
                            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))
                            BatteryOptimizationWarningCard(
                                onOpenBatterySettings = onOpenBatteryOptimizationSettings
                            )
                        }

                        // ═══════════════════════════════════════════════════════════════
                        // HANDWRITING RECOGNITION SECTION
                        // ═══════════════════════════════════════════════════════════════

                        Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                        Text(
                            text = "Handwriting Recognition",
                            fontSize = EInkTheme.Preferences.sectionHeaderFontSize,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.Preferences.sectionHeaderColor
                        )

                        Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

                        // Handwriting Language Status
                        HandwritingRecognitionSection()
                    }
                }
            }
        }
    }

    // Battery optimization explanation dialog
    if (showBatteryExplanationDialog) {
        BatteryExemptionExplanationDialog(
            onDismiss = { showBatteryExplanationDialog = false },
            onContinue = {
                showBatteryExplanationDialog = false
                NotificationPermissionHelper.requestBatteryExemption(context)
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// NOTIFICATION PERMISSION UI
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Permission request card - shown when notifications enabled but permission not granted.
 * Follows same pattern as BooxNotesSettingsDialog permission card.
 */
@Composable
private fun NotificationPermissionCard(
    showExactAlarmStep: Boolean,
    openSettingsInstead: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenAlarmSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = EInkTheme.Colors.background,
        border = BorderStroke(0.5.dp, EInkTheme.Colors.borderStrong),
        elevation = 0.dp,
        shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
    ) {
        Column(
            modifier = Modifier.padding(EInkTheme.Global.paddingMedium)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Permission needed",
                    modifier = Modifier.size(20.dp),
                    tint = EInkTheme.Colors.primary
                )
                Text(
                    text = "Permission Required",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = EInkTheme.Colors.primary
                )
            }

            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

            // Explanation
            Text(
                text = if (openSettingsInstead) {
                    "Notifications are turned off for BooxDay in Android settings. Open Settings and turn them on."
                } else {
                    "To send you reminders, BooxDay needs permission to show notifications. " +
                        "Your notification preferences stay on your device."
                },
                fontSize = EInkTheme.Preferences.explanationFontSize,
                color = EInkTheme.Colors.darkGray
            )

            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

            // Primary action: Notification permission (Android 13+)
            OutlinedButton(
                onClick = onRequestNotificationPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    backgroundColor = EInkTheme.Colors.primary,
                    contentColor = Color.White
                ),
                border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Colors.primary),
                shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (openSettingsInstead) "Open Settings" else "Allow Notifications",
                    fontSize = EInkTheme.Typography.button,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Secondary action: exact alarms (Android 12+), not on BOOX (see the call)
            if (showExactAlarmStep) {
                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))
                Text(
                    text = "For precise reminder timing, also enable exact alarms:",
                    fontSize = EInkTheme.Preferences.explanationFontSize,
                    color = EInkTheme.Colors.gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onOpenAlarmSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = EInkTheme.Colors.primary
                    ),
                    border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Colors.borderStrong),
                    shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                ) {
                    Text(
                        text = "Open Alarm Settings",
                        fontSize = EInkTheme.Typography.button,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Notification channel warning card - shown when notifications are enabled but a channel is disabled.
 */
@Composable
private fun NotificationChannelWarningCard(
    disabledChannelsText: String,
    onOpenNotificationSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = EInkTheme.Colors.background,
        border = BorderStroke(0.5.dp, EInkTheme.Colors.borderStrong),
        elevation = 0.dp,
        shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
    ) {
        Column(
            modifier = Modifier.padding(EInkTheme.Global.paddingMedium)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Channel disabled",
                    modifier = Modifier.size(20.dp),
                    tint = EInkTheme.Colors.primary
                )
                Text(
                    text = "Channel Disabled",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = EInkTheme.Colors.primary
                )
            }

            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

            // Explanation
            Text(
                text = "Notifications are enabled, but the system channels are off: " +
                    "$disabledChannelsText. Re-enable them to receive reminders.",
                fontSize = EInkTheme.Preferences.explanationFontSize,
                color = EInkTheme.Colors.darkGray
            )

            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

            // Action button
            OutlinedButton(
                onClick = onOpenNotificationSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    backgroundColor = EInkTheme.Colors.primary,
                    contentColor = Color.White
                ),
                border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Colors.primary),
                shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Open Notification Settings",
                    fontSize = EInkTheme.Typography.button,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Battery optimization warning card - shown on Boox devices when notifications are enabled
 * but the app is not exempt from battery optimization.
 *
 * Boox devices aggressively kill background processes including WorkManager jobs.
 * Without battery optimization exemption, notification scheduling will silently fail.
 */
@Composable
private fun BatteryOptimizationWarningCard(
    onOpenBatterySettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = EInkTheme.Colors.background,
        border = BorderStroke(0.5.dp, EInkTheme.Colors.borderStrong),
        elevation = 0.dp,
        shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
    ) {
        Column(
            modifier = Modifier.padding(EInkTheme.Global.paddingMedium)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Action needed",
                    modifier = Modifier.size(20.dp),
                    tint = EInkTheme.Colors.primary
                )
                Text(
                    text = "Action Required for Reliable Notifications",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = EInkTheme.Colors.primary
                )
            }

            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

            // Explanation
            Text(
                text = "Boox devices restrict background tasks by default. To ensure notifications " +
                        "are delivered reliably, please exempt BooxDay from battery optimization.",
                fontSize = EInkTheme.Preferences.explanationFontSize,
                color = EInkTheme.Colors.darkGray
            )

            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

            // Action button
            OutlinedButton(
                onClick = onOpenBatterySettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    backgroundColor = EInkTheme.Colors.primary,
                    contentColor = Color.White
                ),
                border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Colors.primary),
                shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Open Battery Settings",
                    fontSize = EInkTheme.Typography.button,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Time picker section for default reminder times.
 * Shows two pickers:
 * 1. Task reminder: Time of day on due date (e.g., 9:00 AM)
 * 2. Event reminder: Minutes before event (e.g., 15 min)
 */
@Composable
private fun NotificationTimePickerSection(
    defaultTaskReminderHour: Int,
    defaultTaskReminderMinute: Int,
    onDefaultTaskReminderChanged: (hour: Int, minute: Int) -> Unit,
    defaultEventReminderMinutes: Int,
    onDefaultEventReminderChanged: (minutes: Int) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
    ) {
        // Task reminder time picker
        Column {
            Text(
                text = "Task Reminder Time",
                fontSize = EInkTheme.Preferences.subLabelFontSize,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )
            Text(
                text = "When to notify on the due date",
                fontSize = EInkTheme.Preferences.explanationFontSize,
                color = EInkTheme.Colors.gray
            )
            Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

            // Time display with hour/minute dropdowns
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hour picker chips (common times)
                val hourOptions = listOf(6, 7, 8, 9, 12, 18)
                hourOptions.forEach { hour ->
                    val isSelected = defaultTaskReminderHour == hour && defaultTaskReminderMinute == 0
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .background(
                                if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.background,
                                RoundedCornerShape(4.dp)
                            )
                            .border(1.dp, EInkTheme.Colors.borderStrong, RoundedCornerShape(4.dp))
                            .noRippleClickable { onDefaultTaskReminderChanged(hour, 0) }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (hour) {
                                6 -> "6 AM"
                                7 -> "7 AM"
                                8 -> "8 AM"
                                9 -> "9 AM"
                                12 -> "Noon"
                                18 -> "6 PM"
                                else -> "$hour:00"
                            },
                            fontSize = EInkTheme.Preferences.chipFontSize,
                            color = if (isSelected) Color.White else EInkTheme.Colors.darkGray
                        )
                    }
                }
            }
        }

        Divider(color = EInkTheme.Colors.dividerHairline, thickness = 1.dp)

        // Event reminder preset chips
        Column {
            Text(
                text = "Event Reminder",
                fontSize = EInkTheme.Preferences.subLabelFontSize,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )
            Text(
                text = "How long before the event",
                fontSize = EInkTheme.Preferences.explanationFontSize,
                color = EInkTheme.Colors.gray
            )
            Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

            // Preset chips (same as AddEditEventDialog)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val presets = listOf(5, 15, 30, 60, 1440)
                val labels = listOf("5m", "15m", "30m", "1h", "1d")

                presets.forEachIndexed { index, minutes ->
                    val isSelected = defaultEventReminderMinutes == minutes
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(
                                if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.background,
                                RoundedCornerShape(4.dp)
                            )
                            .border(1.dp, EInkTheme.Colors.borderStrong, RoundedCornerShape(4.dp))
                            .noRippleClickable { onDefaultEventReminderChanged(minutes) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = labels[index],
                            fontSize = EInkTheme.Preferences.chipFontSize,
                            color = if (isSelected) Color.White else EInkTheme.Colors.darkGray
                        )
                    }
                }
            }
        }
    }
}

/**
 * Task reminder time picker - shows preset hour chips for when to remind on due date.
 */
@Composable
private fun TaskReminderTimePicker(
    defaultTaskReminderHour: Int,
    defaultTaskReminderMinute: Int,
    onDefaultTaskReminderChanged: (hour: Int, minute: Int) -> Unit
) {
    Column {
        Text(
            text = "Reminder Time",
            fontSize = EInkTheme.Preferences.subLabelFontSize,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.Colors.gray
        )
        Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

        // Hour picker chips (common times)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val hourOptions = listOf(6, 7, 8, 9, 12, 18)
            hourOptions.forEach { hour ->
                val isSelected = defaultTaskReminderHour == hour && defaultTaskReminderMinute == 0
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .background(
                            if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.background,
                            RoundedCornerShape(4.dp)
                        )
                        .border(1.dp, EInkTheme.Colors.borderStrong, RoundedCornerShape(4.dp))
                        .noRippleClickable { onDefaultTaskReminderChanged(hour, 0) }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (hour) {
                            6 -> "6 AM"
                            7 -> "7 AM"
                            8 -> "8 AM"
                            9 -> "9 AM"
                            12 -> "Noon"
                            18 -> "6 PM"
                            else -> "$hour:00"
                        },
                        fontSize = EInkTheme.Preferences.chipFontSize,
                        color = if (isSelected) Color.White else EInkTheme.Colors.darkGray
                    )
                }
            }
        }
    }
}

/**
 * Event reminder time picker - shows preset chips for how long before event to remind.
 */
@Composable
private fun EventReminderTimePicker(
    defaultEventReminderMinutes: Int,
    onDefaultEventReminderChanged: (minutes: Int) -> Unit
) {
    Column {
        Text(
            text = "Remind Before",
            fontSize = EInkTheme.Preferences.subLabelFontSize,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.Colors.gray
        )
        Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

        // Preset chips (same as AddEditEventDialog)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val presets = listOf(5, 15, 30, 60, 1440)
            val labels = listOf("5m", "15m", "30m", "1h", "1d")

            presets.forEachIndexed { index, minutes ->
                val isSelected = defaultEventReminderMinutes == minutes
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .background(
                            if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.background,
                            RoundedCornerShape(4.dp)
                        )
                        .border(1.dp, EInkTheme.Colors.borderStrong, RoundedCornerShape(4.dp))
                        .noRippleClickable { onDefaultEventReminderChanged(minutes) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labels[index],
                        fontSize = EInkTheme.Preferences.chipFontSize,
                        color = if (isSelected) Color.White else EInkTheme.Colors.darkGray
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HANDWRITING RECOGNITION SECTION
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Handwriting Recognition section - manages handwriting language selection.
 * Shows active language with "Change" button that opens Language Pack Manager.
 * Supports 5 languages: English (Full), Spanish/French/German/Chinese (Basic).
 */
@Composable
private fun HandwritingRecognitionSection() {
    val context = LocalContext.current
    val activeLanguage by DigitalInkModelManager.activeLanguage.collectAsState()
    val languageStates by DigitalInkModelManager.languageStates.collectAsState()
    var showLanguageManager by remember { mutableStateOf(false) }

    val activeLanguageInfo = languageStates.find { it.isActive }

    SettingCard(
        title = "Task Handwriting",
        description = "Recognizes handwritten text on Task pages for search and Smart Link"
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
        ) {
            // Active language display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Active Language",
                        fontSize = EInkTheme.Preferences.subLabelFontSize,
                        color = EInkTheme.Colors.gray
                    )
                    Text(
                        activeLanguageInfo?.displayName ?: "Loading...",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.primary
                    )
                }
                OutlinedButton(
                    onClick = { showLanguageManager = true },
                    modifier = Modifier.height(EInkTheme.Button.height),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = EInkTheme.Colors.primary
                    ),
                    border = BorderStroke(
                        EInkTheme.Button.borderWidth,
                        EInkTheme.Button.borderColor
                    ),
                    shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                ) {
                    Text(
                        "Change",
                        fontSize = EInkTheme.Typography.button,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // Language Pack Manager Popup
    if (showLanguageManager) {
        LanguagePackManagerPopup(
            onDismiss = { showLanguageManager = false },
            onLanguageSelected = { languageTag ->
                // Persist language selection to settings
                KvProxy(context).setAppSettings(
                    GlobalAppSettings.current.copy(handwritingLanguage = languageTag)
                )
                // Popup remains open so user can see updated active state
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// BATTERY EXEMPTION EXPLANATION DIALOG
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Pre-prompt explanation dialog shown before the Android system battery optimization dialog.
 *
 * Explains why the exemption is needed and reassures users that it won't drain battery.
 * This improves conversion rate on the scary system dialog that follows.
 */
@Composable
private fun BatteryExemptionExplanationDialog(
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f),
                backgroundColor = EInkTheme.Colors.background,
                border = BorderStroke(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor),
                elevation = 0.dp,
                shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
            ) {
                Column(
                    modifier = Modifier.padding(EInkTheme.Global.paddingLarge),
                    verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = EInkTheme.Colors.primary
                        )
                        Text(
                            text = "Enable Reliable Notifications",
                            fontSize = EInkTheme.Typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.Colors.primary
                        )
                    }

                    // Explanation
                    Column(
                        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
                    ) {
                        Text(
                            text = "Boox devices aggressively stop background apps to save battery. " +
                                    "This means scheduled notifications may not be delivered.",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.darkGray
                        )

                        Text(
                            text = "To fix this, Android will ask you to allow BooxDay to run in the background.",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.darkGray
                        )

                        // Reassurance box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    EInkTheme.Colors.primary.copy(alpha = 0.08f),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    EInkTheme.Colors.primary.copy(alpha = 0.2f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(EInkTheme.Global.paddingMedium)
                        ) {
                            Text(
                                text = "This will NOT drain your battery. BooxDay only wakes briefly " +
                                        "to deliver scheduled notifications.",
                                fontSize = EInkTheme.Preferences.explanationFontSize,
                                fontWeight = FontWeight.Medium,
                                color = EInkTheme.Colors.primary
                            )
                        }

                        Text(
                            text = "On the next screen, tap \"Allow\" so BooxDay can send reminders on time.",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.Colors.darkGray
                        )
                    }

                    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
                    ) {
                        // Cancel button
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(EInkTheme.Button.height),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = EInkTheme.Button.textColor
                            ),
                            border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor),
                            shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                        ) {
                            Text(
                                text = "Not Now",
                                fontSize = EInkTheme.Typography.button,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Continue button
                        OutlinedButton(
                            onClick = onContinue,
                            modifier = Modifier
                                .weight(1f)
                                .height(EInkTheme.Button.height),
                            colors = ButtonDefaults.outlinedButtonColors(
                                backgroundColor = EInkTheme.Colors.primary,
                                contentColor = Color.White
                            ),
                            border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Colors.primary),
                            shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                        ) {
                            Text(
                                text = "Allow in Android",
                                fontSize = EInkTheme.Typography.button,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}


/**
 * A reminder row's status (Oct 2026). It said "Enabled" while a card below said reminders wouldn't
 * arrive; now it says what stands in the way.
 */
internal fun reminderStatus(on: Boolean, notificationsAllowed: Boolean, channelOn: Boolean, batteryOk: Boolean): String = when {
    !on -> "Off"
    !notificationsAllowed -> "On, but notifications are off for BooxDay"
    !channelOn -> "On, but this reminder type is off in Android settings"
    !batteryOk -> "On, but reminders may be late (battery saving)"
    else -> "On"
}
