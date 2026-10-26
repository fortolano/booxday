package com.boox.day.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import com.boox.day.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.LocalCalendar
import com.boox.day.sync.GoogleAuthManager
import com.boox.day.ui.components.EInkToggle
import com.boox.day.ui.components.LocalCalendarsCard
import com.boox.day.ui.components.LocalCalendarsInfoCard
import com.boox.day.ui.components.RadioOption
import com.boox.day.ui.components.ScrollableWithIndicator
import com.boox.day.ui.components.SettingCard
import com.boox.day.ui.components.SyncModeSelector
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * Unified Calendar Settings Dialog for BooxDay
 *
 * RESTRUCTURED (Dec 2025 - Settings Redesign Phase 3):
 * - Display & Mode: First Day of Week, Show Week Numbers, Sync Mode
 * - Calendars: Local calendars management + Google sync
 *
 * Mode switching behavior:
 * - Standalone → Hybrid: Shows AddAccountPromptPopup
 * - Hybrid → Standalone (with accounts): Shows SwitchToStandalonePopup with data options
 *
 * Optimized for e-ink display.
 */
/**
 * Summary of a connected cloud account for display in Calendar Settings.
 */
data class CloudAccountSummary(
    val email: String,
    val provider: String, // "Google" or "Outlook"
    val lastSyncTimeMillis: Long? = null
)

@Composable
fun CalendarSettingsDialog(
    onDismiss: () -> Unit,
    // Cloud sync actions (multi-account)
    onSyncNow: () -> Unit,
    onOpenCalendarAccounts: () -> Unit = {},
    // Cloud accounts summary (replaces legacy single-account params)
    connectedAccounts: List<CloudAccountSummary> = emptyList(),
    syncInProgress: Boolean = false,
    autoSyncEnabled: Boolean = true,
    syncFrequency: String = GoogleAuthManager.FREQUENCY_1_HOUR,
    onAutoSyncChanged: (Boolean) -> Unit = {},
    onSyncFrequencyChanged: (String) -> Unit = {},
    // Mode settings
    syncMode: AppSettings.SyncMode = AppSettings.SyncMode.STANDALONE,
    onSyncModeChanged: (AppSettings.SyncMode) -> Unit = {},
    // Connected accounts info (for mode switch warnings)
    connectedAccountEmails: List<String> = emptyList(),
    syncedEventCount: Int = 0,
    // Callbacks for mode switch with data decisions
    onSwitchToStandaloneKeepData: () -> Unit = {},
    onSwitchToStandaloneClearData: () -> Unit = {},
    // Display settings (moved from General Settings)
    firstDayOfWeek: AppSettings.FirstDayOfWeek = AppSettings.FirstDayOfWeek.Sunday,
    onFirstDayOfWeekChanged: (AppSettings.FirstDayOfWeek) -> Unit = {},
    showWeekNumbers: Boolean = false,
    onShowWeekNumbersChanged: (Boolean) -> Unit = {},
    // Local calendars
    localCalendars: List<LocalCalendar> = emptyList(),
    onAddLocalCalendar: () -> Unit = {},
    onDeleteLocalCalendars: (List<String>) -> Unit = {},
    // Legacy settings (kept for backward compatibility but will be removed later)
    defaultSyncEnabled: Boolean = false,
    showSyncBadges: Boolean = true,
    offlineChangesWarning: Boolean = true,
    onDefaultSyncChanged: (Boolean) -> Unit = {},
    onShowSyncBadgesChanged: (Boolean) -> Unit = {},
    onOfflineWarningChanged: (Boolean) -> Unit = {},
    // Initial tab selection
    initialTab: CalendarSettingsTab = CalendarSettingsTab.DISPLAY_MODE
) {
    var selectedTab by remember { mutableStateOf(initialTab) }

    // Google section is visible when in Hybrid mode
    val showGoogleSection = syncMode == AppSettings.SyncMode.HYBRID

    // Popup states for mode switching
    var showAddAccountPrompt by remember { mutableStateOf(false) }
    var showSwitchToStandaloneWarning by remember { mutableStateOf(false) }

    // Intercept mode changes to show appropriate popups
    val handleModeChange: (AppSettings.SyncMode) -> Unit = { newMode ->
        when {
            // Switching from Standalone to Hybrid - show add account prompt
            syncMode == AppSettings.SyncMode.STANDALONE && newMode == AppSettings.SyncMode.HYBRID -> {
                onSyncModeChanged(newMode) // Switch immediately
                showAddAccountPrompt = true // Then show prompt
            }
            // Switching from Hybrid to Standalone WITH connected accounts - show warning
            syncMode == AppSettings.SyncMode.HYBRID && newMode == AppSettings.SyncMode.STANDALONE && connectedAccountEmails.isNotEmpty() -> {
                showSwitchToStandaloneWarning = true // Show warning first, don't switch yet
            }
            // No special handling needed
            else -> {
                onSyncModeChanged(newMode)
            }
        }
    }

    // Add Account Prompt popup (shown after switching to Hybrid)
    if (showAddAccountPrompt) {
        AddAccountPromptPopup(
            onAddAccount = {
                showAddAccountPrompt = false
                onDismiss() // Close this dialog
                onOpenCalendarAccounts() // Open Calendar Accounts dialog
            },
            onLater = {
                showAddAccountPrompt = false
            }
        )
    }

    // Switch to Standalone Warning popup (shown when trying to switch with connected accounts)
    if (showSwitchToStandaloneWarning) {
        SwitchToStandalonePopup(
            connectedEmails = connectedAccountEmails,
            syncedEventCount = syncedEventCount,
            onChoice = { choice ->
                showSwitchToStandaloneWarning = false
                when (choice) {
                    SwitchToStandaloneChoice.KEEP_DATA -> {
                        onSwitchToStandaloneKeepData()
                    }
                    SwitchToStandaloneChoice.CLEAR_DATA -> {
                        onSwitchToStandaloneClearData()
                    }
                    SwitchToStandaloneChoice.CANCEL -> {
                        // Do nothing - stay in Hybrid mode
                    }
                }
            }
        )
    }

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
                    .fillMaxWidth(EInkTheme.PanelDialog.widthFraction)  // Oct 2026: was 0.75f
                    .fillMaxHeight(EInkTheme.PanelDialog.heightFraction)  // was 0.70f
                    .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.PopupOverlay.padding)
            ) {
                // Header (E-Ink: text symbol instead of Material Icon)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.calendar_today_40px),
                            contentDescription = "Calendar",
                            modifier = Modifier.size(24.dp),
                            tint = EInkTheme.Colors.primary
                        )
                        Text(
                            text = "Calendar Settings",
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

                // Tab selector - e-ink optimized (2 tabs)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = EInkTheme.Global.paddingMedium),
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                ) {
                    CalendarSettingsTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab

                        // E-Ink: Use Box + noRippleClickable instead of Button (avoids ripple)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(EInkTheme.Global.touchTarget)
                                .background(
                                    if (isSelected) EInkTheme.Selection.background else EInkTheme.Selection.foreground,
                                    RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                                )
                                .then(
                                    if (!isSelected) {
                                        Modifier.border(
                                            EInkTheme.Global.borderWidth,
                                            EInkTheme.Colors.primary,
                                            RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                                        )
                                    } else Modifier
                                )
                                .noRippleClickable { selectedTab = tab },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.label,
                                fontSize = EInkTheme.Typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) EInkTheme.Selection.foreground else EInkTheme.Selection.background
                            )
                        }
                    }
                }

                // Tab content - E-Ink: use ScrollableWithIndicator for scroll feedback
                ScrollableWithIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
                    ) {
                        when (selectedTab) {
                            CalendarSettingsTab.DISPLAY_MODE -> DisplayModeTabContent(
                                firstDayOfWeek = firstDayOfWeek,
                                onFirstDayOfWeekChanged = onFirstDayOfWeekChanged,
                                showWeekNumbers = showWeekNumbers,
                                onShowWeekNumbersChanged = onShowWeekNumbersChanged,
                                syncMode = syncMode,
                                isSignedIn = connectedAccounts.isNotEmpty(),
                                onSyncModeChanged = handleModeChange // Use interceptor for popups
                            )

                            CalendarSettingsTab.CALENDARS -> CalendarsTabContent(
                                localCalendars = localCalendars,
                                onAddCalendar = onAddLocalCalendar,
                                onDeleteCalendars = onDeleteLocalCalendars,
                                showCloudSection = showGoogleSection,
                                connectedAccounts = connectedAccounts,
                                syncInProgress = syncInProgress,
                                autoSyncEnabled = autoSyncEnabled,
                                syncFrequency = syncFrequency,
                                onAutoSyncChanged = onAutoSyncChanged,
                                onSyncFrequencyChanged = onSyncFrequencyChanged,
                                onSyncNow = onSyncNow,
                                onOpenCalendarAccounts = onOpenCalendarAccounts,
                                onSyncModeChanged = handleModeChange // Use interceptor for popups
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab options for calendar settings.
 * RESTRUCTURED: Mode & Display, Your Calendars (consolidated from 3 tabs)
 */
enum class CalendarSettingsTab(val label: String) {
    DISPLAY_MODE("Mode & Display"),
    CALENDARS("Your Calendars")
}

/**
 * Display & Mode tab - First Day of Week, Show Week Numbers, Sync Mode
 * RESTRUCTURED: Combines display settings from General Settings + Mode selector
 */
@Composable
private fun DisplayModeTabContent(
    firstDayOfWeek: AppSettings.FirstDayOfWeek,
    onFirstDayOfWeekChanged: (AppSettings.FirstDayOfWeek) -> Unit,
    showWeekNumbers: Boolean,
    onShowWeekNumbersChanged: (Boolean) -> Unit,
    syncMode: AppSettings.SyncMode,
    isSignedIn: Boolean,
    onSyncModeChanged: (AppSettings.SyncMode) -> Unit
) {
    // First Day of Week Setting (moved from General Settings)
    SettingCard(
        title = "First Day of Week",
        description = "Choose which day starts the week on the calendar"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingLarge)
        ) {
            RadioOption(
                label = "Sunday",
                isSelected = firstDayOfWeek == AppSettings.FirstDayOfWeek.Sunday,
                onClick = { onFirstDayOfWeekChanged(AppSettings.FirstDayOfWeek.Sunday) }
            )
            RadioOption(
                label = "Monday",
                isSelected = firstDayOfWeek == AppSettings.FirstDayOfWeek.Monday,
                onClick = { onFirstDayOfWeekChanged(AppSettings.FirstDayOfWeek.Monday) }
            )
        }
    }

    // Show Week Numbers Setting (moved from General Settings)
    SettingCard(
        title = "Show Week Numbers",
        description = "Display week numbers on the calendar grid"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showWeekNumbers) "Enabled" else "Disabled",
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.darkGray
            )
            EInkToggle(
                checked = showWeekNumbers,
                onCheckedChange = onShowWeekNumbersChanged
            )
        }
    }

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

    // Mode selector
    SyncModeSelector(
        currentMode = syncMode,
        isSignedIn = isSignedIn,
        onModeSelected = onSyncModeChanged
    )

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    // Which Mode is Right for You? info box
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.borderStrong, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
    ) {
        Column(modifier = Modifier.padding(EInkTheme.Global.paddingMedium)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
            ) {
                Icon(
                    painter = painterResource(R.drawable.info_40px),
                    contentDescription = "Info",
                    modifier = Modifier.size(16.dp),
                    tint = EInkTheme.Colors.darkGray
                )
                Text(
                    text = "Which Mode is Right for You?",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )
            }

            Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

            Text(
                text = "Choose Private Mode if you...",
                fontSize = EInkTheme.Typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = EInkTheme.Colors.primary
            )
            Text(
                text = "• Want maximum privacy (no cloud connection)",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )
            Text(
                text = "• Use BooxDay as your only calendar",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )
            Text(
                text = "• Prefer offline-first simplicity",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )

            Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

            Text(
                text = "Choose Cloud Sync if you...",
                fontSize = EInkTheme.Typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = EInkTheme.Colors.primary
            )
            Text(
                text = "• Already use Google or Outlook calendars",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )
            Text(
                text = "• Want to see all your events in one place",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )
            Text(
                text = "• Need to share schedules with others",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )
        }
    }
}

/**
 * Calendars tab - Local calendars management + Cloud accounts summary (if Hybrid mode)
 * Multi-account aware: shows all connected accounts, delegates management to CalendarSyncSettingsDialog.
 */
@Composable
private fun CalendarsTabContent(
    localCalendars: List<LocalCalendar>,
    onAddCalendar: () -> Unit,
    onDeleteCalendars: (List<String>) -> Unit,
    showCloudSection: Boolean,
    connectedAccounts: List<CloudAccountSummary>,
    syncInProgress: Boolean,
    autoSyncEnabled: Boolean,
    syncFrequency: String,
    onAutoSyncChanged: (Boolean) -> Unit,
    onSyncFrequencyChanged: (String) -> Unit,
    onSyncNow: () -> Unit,
    onOpenCalendarAccounts: () -> Unit,
    onSyncModeChanged: (AppSettings.SyncMode) -> Unit
) {
    // On-Device calendars section
    Text(
        text = "On-Device Calendars",
        fontSize = EInkTheme.Typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = EInkTheme.Colors.primary
    )

    Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

    // Info card (shown first as introduction)
    LocalCalendarsInfoCard()

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

    // On-device calendars card
    LocalCalendarsCard(
        calendars = localCalendars,
        maxCalendars = 9,
        onAddCalendar = onAddCalendar,
        onDeleteCalendars = onDeleteCalendars
    )

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // Cloud sync section (Google & Outlook)
    Text(
        text = "Cloud Calendars",
        fontSize = EInkTheme.Typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = EInkTheme.Colors.primary
    )

    Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

    if (showCloudSection) {
        CloudCalendarsSummary(
            connectedAccounts = connectedAccounts,
            syncInProgress = syncInProgress,
            autoSyncEnabled = autoSyncEnabled,
            syncFrequency = syncFrequency,
            onAutoSyncChanged = onAutoSyncChanged,
            onSyncFrequencyChanged = onSyncFrequencyChanged,
            onSyncNow = onSyncNow,
            onOpenCalendarAccounts = onOpenCalendarAccounts
        )
    } else {
        // Private Mode - explain cloud sync is not available
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.borderStrong, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(EInkTheme.Global.paddingMedium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(R.drawable.cloud_off_40px),
                    contentDescription = "Private Mode",
                    modifier = Modifier.size(32.dp),
                    tint = EInkTheme.Colors.gray
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

                Text(
                    text = "Private Mode Active",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

                Text(
                    text = "All your calendars stay on this device. To connect Google or Outlook calendars, switch to Cloud Sync in the Mode & Display tab.",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.darkGray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

                OutlinedButton(
                    onClick = { onSyncModeChanged(AppSettings.SyncMode.HYBRID) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = EInkTheme.Button.textColor
                    ),
                    border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor),
                    shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                ) {
                    Text(
                        text = "Enable Cloud Sync",
                        fontSize = EInkTheme.Typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * Cloud Calendars summary - shows all connected accounts and global sync settings.
 * Detailed per-account management is handled by CalendarSyncSettingsDialog.
 */
@Composable
private fun CloudCalendarsSummary(
    connectedAccounts: List<CloudAccountSummary>,
    syncInProgress: Boolean,
    autoSyncEnabled: Boolean,
    syncFrequency: String,
    onAutoSyncChanged: (Boolean) -> Unit,
    onSyncFrequencyChanged: (String) -> Unit,
    onSyncNow: () -> Unit,
    onOpenCalendarAccounts: () -> Unit
) {
    if (connectedAccounts.isEmpty()) {
        // No accounts connected - show prompt to add
        NoAccountsContent(onOpenCalendarAccounts = onOpenCalendarAccounts)
    } else {
        // Show connected accounts summary + sync settings
        ConnectedAccountsSummary(
            connectedAccounts = connectedAccounts,
            syncInProgress = syncInProgress,
            autoSyncEnabled = autoSyncEnabled,
            syncFrequency = syncFrequency,
            onAutoSyncChanged = onAutoSyncChanged,
            onSyncFrequencyChanged = onSyncFrequencyChanged,
            onSyncNow = onSyncNow,
            onOpenCalendarAccounts = onOpenCalendarAccounts
        )
    }
}

@Composable
private fun NoAccountsContent(onOpenCalendarAccounts: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
    ) {
        Icon(
            painter = painterResource(R.drawable.cloud_off_40px),
            contentDescription = "Not connected",
            modifier = Modifier.size(32.dp),
            tint = EInkTheme.Colors.gray
        )

        Text(
            text = "Cloud Sync Ready",
            fontSize = EInkTheme.Typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.Colors.primary
        )

        Text(
            text = "Connect a Google or Outlook account to see your cloud calendars alongside your on-device calendars.",
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.darkGray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

        Button(
            onClick = onOpenCalendarAccounts,
            modifier = Modifier
                .fillMaxWidth()
                .height(EInkTheme.Button.height),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = EInkTheme.Button.primaryBackground,
                contentColor = EInkTheme.Button.primaryTextColor
            ),
            shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(EInkTheme.Global.spacing))
            Text(
                text = "Connect Account",
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

        // Benefits of syncing (E-Ink: Box instead of Card)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.borderStrong, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
        ) {
            Column(modifier = Modifier.padding(EInkTheme.Global.paddingMedium)) {
                Text(
                    text = "What Cloud Sync gives you:",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )
                Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
                Text(
                    text = "• See events from Google/Outlook calendars",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.darkGray
                )
                Text(
                    text = "• Your on-device calendars stay private",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.darkGray
                )
                Text(
                    text = "• Choose which calendars to display",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.darkGray
                )
            }
        }
    }
}

/**
 * Connected accounts summary with sync controls.
 * Shows all connected cloud accounts and global sync settings.
 * Detailed per-account management via "Manage Accounts" button.
 */
@Composable
private fun ConnectedAccountsSummary(
    connectedAccounts: List<CloudAccountSummary>,
    syncInProgress: Boolean,
    autoSyncEnabled: Boolean,
    syncFrequency: String,
    onAutoSyncChanged: (Boolean) -> Unit,
    onSyncFrequencyChanged: (String) -> Unit,
    onSyncNow: () -> Unit,
    onOpenCalendarAccounts: () -> Unit
) {
    // Provider-grouped account cards
    val accountsByProvider = connectedAccounts.groupBy { it.provider }

    accountsByProvider.forEach { (provider, accounts) ->
        val providerIcon = when (provider) {
            "Google" -> R.drawable.ic_google_provider
            "Outlook" -> R.drawable.ic_outlook_provider
            else -> R.drawable.ic_google_provider
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(EInkTheme.Colors.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.borderStrong, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
        ) {
            Column(modifier = Modifier.padding(EInkTheme.Global.paddingMedium)) {
                // Provider header with wordmark icon
                Icon(
                    painter = painterResource(providerIcon),
                    contentDescription = provider,
                    modifier = Modifier.height(14.dp),
                    tint = EInkTheme.Colors.darkGray
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

                // Account rows
                accounts.forEach { account ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Connected",
                                tint = EInkTheme.Colors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = account.email,
                                fontSize = EInkTheme.Typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = EInkTheme.Colors.primary
                            )
                        }
                        account.lastSyncTimeMillis?.takeIf { it > 0L }?.let { ts ->
                            val lastText = remember(ts) { formatLastSyncTime(ts) }
                            Text(
                                text = lastText,
                                fontSize = EInkTheme.Typography.labelSmall,
                                color = EInkTheme.Colors.gray
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
    }

    // Auto-sync toggle
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.borderStrong, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(EInkTheme.Global.paddingMedium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Background auto-sync",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )
                Text(
                    text = "Automatically sync in background",
                    fontSize = EInkTheme.Typography.labelSmall,
                    color = EInkTheme.Colors.gray
                )
            }
            EInkToggle(
                checked = autoSyncEnabled,
                onCheckedChange = onAutoSyncChanged
            )
        }
    }

    // Sync frequency (only show if auto-sync enabled)
    if (autoSyncEnabled) {
        var showFrequencyMenu by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(EInkTheme.Colors.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.borderStrong, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(EInkTheme.Global.paddingMedium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sync frequency",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary
                )
                Box {
                    TextButton(
                        onClick = { showFrequencyMenu = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = EInkTheme.Colors.primary)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = syncFrequency, fontSize = EInkTheme.Typography.bodyMedium)
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Expand",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showFrequencyMenu,
                        onDismissRequest = { showFrequencyMenu = false },
                        modifier = Modifier
                            .background(EInkTheme.Colors.background)
                            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary)
                    ) {
                        GoogleAuthManager.FREQUENCY_OPTIONS.forEach { freq ->
                            DropdownMenuItem(
                                onClick = {
                                    onSyncFrequencyChanged(freq)
                                    showFrequencyMenu = false
                                }
                            ) {
                                Text(text = freq, fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

    // Action buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
    ) {
        Button(
            onClick = onSyncNow,
            modifier = Modifier
                .weight(1f)
                .height(EInkTheme.Button.height),
            enabled = !syncInProgress,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = EInkTheme.Button.primaryBackground,
                contentColor = EInkTheme.Button.primaryTextColor,
                disabledBackgroundColor = EInkTheme.Colors.lightGray,
                disabledContentColor = EInkTheme.Colors.darkGray
            )
        ) {
            if (syncInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = EInkTheme.Button.primaryTextColor,
                    strokeWidth = 2.dp
                )
            } else {
                Text(text = "Sync Now", fontSize = EInkTheme.Typography.bodyMedium)
            }
        }

        OutlinedButton(
            onClick = onOpenCalendarAccounts,
            modifier = Modifier
                .weight(1f)
                .height(EInkTheme.Button.height),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = EInkTheme.Button.textColor),
            border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor)
        ) {
            Text(text = "Manage Accounts", fontSize = EInkTheme.Typography.bodyMedium)
            Spacer(modifier = Modifier.width(EInkTheme.Global.spacingSmall))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun formatLastSyncTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    if (timestamp <= 0L || timestamp > now) return "unknown"

    val diff = now - timestamp
    val minuteMillis = 60_000L
    val hourMillis = 60 * minuteMillis
    val dayMillis = 24 * hourMillis

    return when {
        diff < minuteMillis -> "just now"
        diff < hourMillis -> {
            val minutes = diff / minuteMillis
            if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
        }
        diff < dayMillis -> {
            val hours = diff / hourMillis
            if (hours == 1L) "1 hour ago" else "$hours hours ago"
        }
        else -> {
            val days = diff / dayMillis
            if (days == 1L) "1 day ago" else "$days days ago"
        }
    }
}
