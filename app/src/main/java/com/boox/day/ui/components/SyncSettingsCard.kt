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
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boox.day.data.datastore.AppSettings
import com.boox.day.ui.theme.EInkTheme

/**
 * Card showing current sync mode and status.
 * Used in settings dialogs to display sync configuration.
 *
 * E-Ink optimized: High contrast, no colors beyond grayscale.
 */
@Composable
fun SyncModeCard(
    syncMode: AppSettings.SyncMode,
    isSignedIn: Boolean,
    userEmail: String?,
    onOpenSyncSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = EInkTheme.Colors.background,
        border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenSyncSettings() }
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status icon
                Icon(
                    imageVector = when {
                        syncMode == AppSettings.SyncMode.HYBRID && isSignedIn -> Icons.Filled.CloudDone
                        syncMode == AppSettings.SyncMode.HYBRID -> Icons.Filled.Sync
                        else -> Icons.Filled.PhoneAndroid
                    },
                    contentDescription = null,
                    tint = EInkTheme.Colors.primary,
                    modifier = Modifier.size(28.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            syncMode == AppSettings.SyncMode.HYBRID && isSignedIn -> "Cloud Sync Active"
                            syncMode == AppSettings.SyncMode.HYBRID -> "Cloud Sync (Not connected)"
                            else -> "Private Mode"
                        },
                        fontSize = EInkTheme.Typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = EInkTheme.Colors.primary
                    )

                    Text(
                        text = when {
                            syncMode == AppSettings.SyncMode.HYBRID && isSignedIn -> userEmail ?: "Connected"
                            syncMode == AppSettings.SyncMode.HYBRID -> "Connect an account to sync"
                            else -> "All calendars stay on your device"
                        },
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.darkGray
                    )
                }

                // Arrow indicator
                Text(
                    text = "›",
                    fontSize = EInkTheme.Typography.titleLarge,
                    color = EInkTheme.Colors.darkGray
                )
            }
        }
    }
}

/**
 * Mode selector for choosing between Private Mode and Cloud Sync.
 * Shows clear descriptions of each mode.
 */
@Composable
fun SyncModeSelector(
    currentMode: AppSettings.SyncMode,
    isSignedIn: Boolean,
    onModeSelected: (AppSettings.SyncMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Calendar Mode",
            fontSize = EInkTheme.Typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary
        )

        // Private Mode option
        ModeOption(
            icon = Icons.Filled.PhoneAndroid,
            title = "Private Mode",
            description = "Keep everything on your device. No account needed, no internet required. Your calendars are completely private.",
            isSelected = currentMode == AppSettings.SyncMode.STANDALONE,
            onClick = { onModeSelected(AppSettings.SyncMode.STANDALONE) }
        )

        // Cloud Sync option
        ModeOption(
            icon = Icons.Filled.Sync,
            title = "Cloud Sync",
            description = if (isSignedIn) {
                "Your Google or Outlook calendars appear alongside your on-device calendars. Only sync what you choose."
            } else {
                "Connect Google or Outlook to see all your events in one place. Your on-device calendars stay private."
            },
            isSelected = currentMode == AppSettings.SyncMode.HYBRID,
            onClick = { onModeSelected(AppSettings.SyncMode.HYBRID) }
        )
    }
}

@Composable
private fun ModeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) EInkTheme.Colors.offWhite else EInkTheme.Colors.background,
                RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius)
            )
            .border(
                EInkTheme.Global.borderWidth,
                if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius)
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.darkGray,
            modifier = Modifier.size(24.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = EInkTheme.Colors.primary
            )
            Text(
                text = description,
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )
        }

        // Selection indicator
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(2.dp, EInkTheme.Colors.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(EInkTheme.Colors.primary)
                )
            }
        }
    }
}

/**
 * Settings section for sync preferences in Cloud Sync mode.
 * Shows default sync toggle, badge visibility, etc.
 */
@Composable
fun HybridModeSettings(
    defaultSyncEnabled: Boolean,
    showSyncBadges: Boolean,
    offlineChangesWarning: Boolean,
    onDefaultSyncChanged: (Boolean) -> Unit,
    onShowSyncBadgesChanged: (Boolean) -> Unit,
    onOfflineWarningChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Cloud Sync Settings",
            fontSize = EInkTheme.Typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Default sync toggle
        SwitchSettingRow(
            title = "Sync new events by default",
            description = "New events will sync to cloud calendars automatically",
            checked = defaultSyncEnabled,
            onCheckedChange = onDefaultSyncChanged
        )

        Divider(color = EInkTheme.Colors.lightGray, thickness = 1.dp)

        // Show sync badges
        SwitchSettingRow(
            title = "Show sync status badges",
            description = "Display sync icons on event cards",
            checked = showSyncBadges,
            onCheckedChange = onShowSyncBadgesChanged
        )

        Divider(color = EInkTheme.Colors.lightGray, thickness = 1.dp)

        // Offline warning
        SwitchSettingRow(
            title = "Offline changes warning",
            description = "Notify when changes are pending sync",
            checked = offlineChangesWarning,
            onCheckedChange = onOfflineWarningChanged
        )
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )
            Text(
                text = description,
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        EInkToggle(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * Sync frequency selector for periodic background sync.
 */
@Composable
fun SyncFrequencySelector(
    currentFrequency: String,
    onFrequencySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Auto-sync Frequency",
            fontSize = EInkTheme.Typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary
        )

        val frequencies = listOf(
            "1_hour" to "Every hour",
            "2_hours" to "Every 2 hours",
            "4_hours" to "Every 4 hours",
            "8_hours" to "Every 8 hours",
            "daily" to "Once a day"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.lightGray, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
        ) {
            frequencies.forEachIndexed { index, (value, label) ->
                FrequencyOption(
                    label = label,
                    isSelected = currentFrequency == value,
                    onClick = { onFrequencySelected(value) }
                )

                if (index < frequencies.size - 1) {
                    Divider(color = EInkTheme.Colors.lightGray, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun FrequencyOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) EInkTheme.Colors.offWhite else EInkTheme.Colors.background
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = EInkTheme.Colors.primary
        )

        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
