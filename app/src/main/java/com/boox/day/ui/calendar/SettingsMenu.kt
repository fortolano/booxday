package com.boox.day.ui.calendar

import com.boox.day.ui.LocalNestedPopupOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.R
import com.boox.day.data.datastore.SetupChecklist
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * Dropdown menu for the settings button in the calendar header.
 * Dec 2025 redesign: Sectioned structure with icons.
 *
 * Sections:
 * - General: Preferences
 * - Calendar: Calendar & Sync, Calendar Accounts
 * - AI & Voice: AI Assistant, Voice-to-Text
 * - Data: Data & Backup, Boox Notes
 * - Info: Task streak (narrow screens), Help, About
 *
 * @param setupChecklist Tracks which first-run setup items have been completed (for badge display)
 * @param isStandaloneMode When true, "Calendar Accounts" is grayed out and shows tooltip on tap
 */
@Composable
fun SettingsMenu(
    onPreferences: () -> Unit,
    onCalendarSettings: () -> Unit,
    onCalendarAccounts: () -> Unit,
    onAiSettings: () -> Unit,
    onVoiceSettings: () -> Unit,
    onDataBackup: () -> Unit,
    onBooxNotesSettings: () -> Unit,
    onHelp: () -> Unit,
    onAbout: () -> Unit,
    onClose: () -> Unit,
    setupChecklist: SetupChecklist = SetupChecklist(),
    isStandaloneMode: Boolean = false,
    // Narrow screens (Go 6, Oct 2026): the header's streak button doesn't fit, so the streak is
    // listed here (null hides it)
    taskStreak: Int? = null,
    onTaskStreak: () -> Unit = {}
) {
    // State for showing tooltip when tapping disabled Calendar Accounts
    var showStandaloneTooltip by remember { mutableStateOf(false) }

    Popup(
        alignment = Alignment.TopEnd,
        onDismissRequest = onClose,
        properties = PopupProperties(focusable = true),
        offset = IntOffset(-20, 120) + LocalNestedPopupOffset.current
    ) {
        Column(
            Modifier
                .width(EInkTheme.PopupMenu.width)
                .background(EInkTheme.PopupMenu.background)
                .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                .padding(EInkTheme.PopupMenu.padding)
        ) {
            // GENERAL SECTION
            SectionHeader("General")
            SettingsMenuItemWithIcon(
                label = "Preferences",
                icon = Icons.Default.Settings,
                showBadge = !setupChecklist.preferencesReviewed,
                onClick = { onPreferences() }
            )

            MenuDivider()

            // CALENDAR SECTION
            SectionHeader("Calendar")
            SettingsMenuItemWithIcon(
                label = "Calendar & Sync",
                icon = Icons.Default.CalendarToday,
                showBadge = !setupChecklist.calendarModeChosen,
                onClick = { onCalendarSettings() }
            )
            SettingsMenuItemWithIcon(
                label = "Calendar Accounts",
                icon = Icons.Default.AccountCircle,
                enabled = !isStandaloneMode,
                onClick = {
                    if (isStandaloneMode) {
                        showStandaloneTooltip = true
                    } else {
                        onCalendarAccounts()
                    }
                }
            )

            // Tooltip shown when tapping disabled Calendar Accounts
            if (showStandaloneTooltip) {
                StandaloneTooltip(
                    onDismiss = { showStandaloneTooltip = false },
                    onNavigateToCalendarSync = {
                        showStandaloneTooltip = false
                        onCalendarSettings()
                    }
                )
            }

            MenuDivider()

            // AI & VOICE SECTION
            SectionHeader("AI & Voice")
            SettingsMenuItemWithIcon(
                label = "AI Settings",
                icon = Icons.Default.Psychology,
                showBadge = !setupChecklist.aiSettingsReviewed,
                onClick = { onAiSettings() }
            )
            SettingsMenuItemWithIcon("Voice-to-Text", Icons.Default.Mic) { onVoiceSettings() }

            MenuDivider()

            // DATA SECTION
            SectionHeader("Data")
            SettingsMenuItemWithIcon("Data & Backup", Icons.Default.CloudSync) { onDataBackup() }
            SettingsMenuItemWithIcon(
                label = "Boox Notes",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                showBadge = !setupChecklist.booxNotesConfigured,
                onClick = { onBooxNotesSettings() }
            )

            MenuDivider()

            // INFO SECTION
            if (taskStreak != null) {
                SettingsMenuItemWithIcon(
                    label = if (taskStreak > 0) "Task streak: $taskStreak" else "Task streak",
                    icon = ImageVector.vectorResource(R.drawable.local_fire_department_40px),
                    onClick = onTaskStreak
                )
            }
            SettingsMenuItemWithIcon("Help", Icons.AutoMirrored.Filled.Help) { onHelp() }
            SettingsMenuItemWithIcon("About", Icons.Default.Info) { onAbout() }
        }
    }
}

/**
 * Individual menu item with optional enabled state.
 * When disabled, shows grayed text but still responds to taps (for tooltip).
 */
@Composable
private fun SettingsMenuItem(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .noRippleClickable { onClick() }
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            color = if (enabled) EInkTheme.MenuItem.textColor else EInkTheme.Colors.gray,
            fontSize = EInkTheme.MenuItem.fontSize
        )
    }
}

@Composable
private fun MenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding)
            .height(EInkTheme.Divider.thickness)
            .background(EInkTheme.Divider.color)
    )
}

/**
 * Section header for grouping menu items.
 * E-Ink optimized: uppercase, subtle gray, smaller text.
 */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = EInkTheme.Typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = EInkTheme.Colors.gray,
        letterSpacing = 1.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding)
            .padding(top = EInkTheme.Global.paddingSmall, bottom = EInkTheme.Global.spacingSmall)
    )
}

/**
 * Menu item with icon for better visual hierarchy.
 * E-Ink optimized: uses noRippleClickable, proper touch targets.
 *
 * @param showBadge When true, displays a badge dot indicating this setting needs attention
 */
@Composable
private fun SettingsMenuItemWithIcon(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    showBadge: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .noRippleClickable { onClick() }
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
    ) {
        // Badge dot (shown before icon when setting needs attention)
        if (showBadge) {
            BadgeDot()
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) EInkTheme.Colors.darkGray else EInkTheme.Colors.gray,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = if (enabled) EInkTheme.MenuItem.textColor else EInkTheme.Colors.gray,
            fontSize = EInkTheme.MenuItem.fontSize
        )
    }
}

/**
 * Small badge dot indicating a setting needs attention.
 * E-ink optimized: solid black circle, no animation.
 */
@Composable
private fun BadgeDot(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(8.dp)
            .background(EInkTheme.Colors.primary, CircleShape)
    )
}

/**
 * Tooltip popup explaining why Calendar Accounts is disabled in Private Mode.
 * E-Ink optimized: high contrast, no animations.
 */
@Composable
private fun StandaloneTooltip(
    onDismiss: () -> Unit,
    onNavigateToCalendarSync: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        // Outer wrapper with start padding to keep dialog away from left screen edge
        Box(modifier = Modifier.padding(start = 60.dp)) {
            Box(
                modifier = Modifier
                    .width(300.dp)
                .background(
                    EInkTheme.Colors.background,
                    RoundedCornerShape(EInkTheme.Global.cornerRadius)
                )
                .border(
                    EInkTheme.Global.borderWidth,
                    EInkTheme.Colors.primary,
                    RoundedCornerShape(EInkTheme.Global.cornerRadius)
                )
                .padding(EInkTheme.Global.padding)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = EInkTheme.Colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Private Mode",
                        fontSize = EInkTheme.Typography.bodyLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = EInkTheme.Colors.primary
                    )
                }

                Text(
                    text = "Google and Outlook accounts can only be connected in Cloud Sync mode.",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )

                Text(
                    text = "Private Mode keeps all calendars on your device. Enable Cloud Sync to connect external calendars.",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray,
                    lineHeight = 18.sp
                )

                // Button row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                ) {
                    // OK button (secondary - outlined)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Global.touchTarget)
                            .border(
                                EInkTheme.Global.borderWidth,
                                EInkTheme.Colors.primary,
                                RoundedCornerShape(EInkTheme.Global.cornerRadius)
                            )
                            .noRippleClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Close",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.Colors.primary
                        )
                    }

                    // Take me there button (primary - filled)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Global.touchTarget)
                            .background(
                                EInkTheme.Colors.primary,
                                RoundedCornerShape(EInkTheme.Global.cornerRadius)
                            )
                            .noRippleClickable(onClick = onNavigateToCalendarSync),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Take me there",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.Colors.white
                        )
                    }
                }
            }
        }
        }
    }
}
