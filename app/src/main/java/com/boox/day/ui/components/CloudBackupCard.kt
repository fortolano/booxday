package com.boox.day.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import com.boox.day.ui.components.EInkToggle
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boox.day.sync.BackupType
import com.boox.day.sync.GoogleAuthManager
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cloud Backup card for the Advanced settings tab.
 * Allows users to backup data to Google Drive independently from Calendar sync.
 *
 * E-Ink optimized: High contrast, clear typography, large touch targets.
 */
@Composable
fun CloudBackupCard(
    isSignedIn: Boolean,
    userEmail: String?,
    isDriveBackupEnabled: Boolean,
    backupFrequency: String,
    lastBackupTime: Long,
    isBackupInProgress: Boolean,
    statusMessage: String? = null,
    // Backup type selection (Phase 3)
    selectedBackupType: BackupType = BackupType.JSON,
    estimatedFullBackupSize: Long = 0L,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onBackupEnabledChanged: (Boolean) -> Unit,
    onBackupFrequencyChanged: (String) -> Unit,
    onBackupNowClick: () -> Unit,
    onBackupTypeChanged: ((BackupType) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = EInkTheme.Colors.background,
        border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.lightGray),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isDriveBackupEnabled && isSignedIn) Icons.Default.CloudDone
                                 else if (isSignedIn) Icons.Default.Cloud
                                 else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = EInkTheme.Colors.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Cloud Backup",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Backup your data to Google Drive",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Account Section
            AccountSection(
                isSignedIn = isSignedIn,
                userEmail = userEmail,
                onSignInClick = onSignInClick,
                onSignOutClick = onSignOutClick
            )

            // Status message (folder setup, errors, success)
            if (statusMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val isSuccess = statusMessage.startsWith("✓")
                val isLoading = statusMessage.startsWith("Setting up") || statusMessage.startsWith("Verifying")
                val isError = statusMessage.contains("failed")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                isSuccess -> EInkTheme.Colors.offWhite
                                isError -> Color(0xFFFFF3E0) // Light amber for errors
                                else -> EInkTheme.Colors.offWhite
                            },
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = EInkTheme.Colors.primary,
                                strokeWidth = 2.dp
                            )
                        }
                        isSuccess -> {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = EInkTheme.Colors.primary
                            )
                        }
                    }
                    Text(
                        text = if (isSuccess) statusMessage.removePrefix("✓ ") else statusMessage,
                        fontSize = EInkTheme.Typography.labelSmall,
                        color = when {
                            isError -> Color(0xFFE65100) // Dark amber for error text
                            isSuccess -> EInkTheme.Colors.primary
                            else -> EInkTheme.Colors.gray
                        }
                    )
                }
            }

            // Only show backup options if signed in
            if (isSignedIn) {
                Divider(
                    color = EInkTheme.Divider.subtleColor,
                    thickness = EInkTheme.Divider.subtleThickness,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Backup Enable Toggle
                BackupToggleRow(
                    isEnabled = isDriveBackupEnabled,
                    onEnabledChanged = onBackupEnabledChanged
                )

                // Only show frequency and backup button if enabled
                if (isDriveBackupEnabled) {
                    // Backup Type Selector (if callback provided)
                    if (onBackupTypeChanged != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        BackupTypeRow(
                            selectedType = selectedBackupType,
                            estimatedFullSize = estimatedFullBackupSize,
                            onTypeChanged = onBackupTypeChanged
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Frequency Selector
                    BackupFrequencyRow(
                        selectedFrequency = backupFrequency,
                        onFrequencyChanged = onBackupFrequencyChanged
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Last Backup Time
                    LastBackupRow(lastBackupTime = lastBackupTime)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Backup Now Button
                    BackupNowButton(
                        isInProgress = isBackupInProgress,
                        backupType = selectedBackupType,
                        onClick = onBackupNowClick
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountSection(
    isSignedIn: Boolean,
    userEmail: String?,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Google Account",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )
            Text(
                text = if (isSignedIn && userEmail != null) userEmail else "Not connected",
                fontSize = EInkTheme.Typography.labelSmall,
                color = if (isSignedIn) EInkTheme.Colors.gray else EInkTheme.Colors.darkGray
            )
        }

        if (isSignedIn) {
            // Sign Out button (subtle)
            Box(
                modifier = Modifier
                    .background(EInkTheme.Colors.offWhite, RoundedCornerShape(6.dp))
                    .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.lightGray, RoundedCornerShape(6.dp))
                    .noRippleClickable { onSignOutClick() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Sign Out",
                    fontSize = EInkTheme.Typography.labelSmall,
                    color = EInkTheme.Colors.gray
                )
            }
        } else {
            // Connect button (prominent)
            Box(
                modifier = Modifier
                    .background(EInkTheme.Colors.primary, RoundedCornerShape(6.dp))
                    .noRippleClickable { onSignInClick() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Connect",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.white
                )
            }
        }
    }
}

@Composable
private fun BackupToggleRow(
    isEnabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Auto Backup",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )
            Text(
                text = "Automatically backup your data to Google Drive",
                fontSize = EInkTheme.Typography.labelSmall,
                color = EInkTheme.Colors.gray
            )
        }

        EInkToggle(
            checked = isEnabled,
            onCheckedChange = onEnabledChanged
        )
    }
}

@Composable
private fun BackupFrequencyRow(
    selectedFrequency: String,
    onFrequencyChanged: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Frequency",
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GoogleAuthManager.BACKUP_FREQUENCY_OPTIONS.forEach { frequency ->
                val isSelected = frequency == selectedFrequency
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.offWhite,
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            EInkTheme.Global.borderWidth,
                            if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                            RoundedCornerShape(6.dp)
                        )
                        .noRippleClickable { onFrequencyChanged(frequency) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = frequency,
                        fontSize = EInkTheme.Typography.labelSmall,
                        color = if (isSelected) EInkTheme.Colors.white else EInkTheme.Colors.primary
                    )
                }
            }
        }
    }
}

/**
 * Backup type selector row.
 * Allows choosing between JSON (basic) and Full Database backup.
 */
@Composable
private fun BackupTypeRow(
    selectedType: BackupType,
    estimatedFullSize: Long,
    onTypeChanged: (BackupType) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Backup Type",
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // JSON option
            BackupTypeOption(
                type = BackupType.JSON,
                isSelected = selectedType == BackupType.JSON,
                title = "Basic",
                subtitle = "Events, Tasks, Strokes",
                onSelect = { onTypeChanged(BackupType.JSON) },
                modifier = Modifier.weight(1f)
            )

            // Full Database option
            BackupTypeOption(
                type = BackupType.FULL_DATABASE,
                isSelected = selectedType == BackupType.FULL_DATABASE,
                title = "Full",
                subtitle = if (estimatedFullSize > 0) {
                    "All data • ${formatFileSize(estimatedFullSize)}"
                } else {
                    "All data + OCR"
                },
                onSelect = { onTypeChanged(BackupType.FULL_DATABASE) },
                modifier = Modifier.weight(1f)
            )
        }

        // Info text
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(EInkTheme.Colors.offWhite, RoundedCornerShape(4.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = EInkTheme.Colors.gray,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (selectedType == BackupType.JSON) {
                    "Basic backup includes events, tasks, pages, strokes, and text annotations."
                } else {
                    "Full backup includes everything: OCR data, Boox Notes links, settings, and all app data. Restoring requires app restart."
                },
                fontSize = EInkTheme.Typography.labelSmall,
                color = EInkTheme.Colors.gray
            )
        }
    }
}

@Composable
private fun BackupTypeOption(
    type: BackupType,
    isSelected: Boolean,
    title: String,
    subtitle: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.offWhite,
                RoundedCornerShape(6.dp)
            )
            .border(
                EInkTheme.Global.borderWidth,
                if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                RoundedCornerShape(6.dp)
            )
            .noRippleClickable { onSelect() }
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = title,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) EInkTheme.Colors.white else EInkTheme.Colors.primary
            )
            Text(
                text = subtitle,
                fontSize = EInkTheme.Typography.labelSmall,
                color = if (isSelected) EInkTheme.Colors.white.copy(alpha = 0.8f) else EInkTheme.Colors.gray
            )
        }
    }
}

@Composable
private fun LastBackupRow(lastBackupTime: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Last Backup",
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary
        )

        Text(
            text = if (lastBackupTime > 0) {
                formatBackupTime(lastBackupTime)
            } else {
                "Never"
            },
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.gray
        )
    }
}

@Composable
private fun BackupNowButton(
    isInProgress: Boolean,
    backupType: BackupType = BackupType.JSON,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.Button.height)
            .background(
                if (isInProgress) EInkTheme.Colors.lightGray else EInkTheme.Colors.primary,
                RoundedCornerShape(6.dp)
            )
            .then(
                if (isInProgress) Modifier else Modifier.noRippleClickable { onClick() }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isInProgress) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = EInkTheme.Colors.primary,
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Backing up...",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary
                )
            }
        } else {
            Text(
                text = if (backupType == BackupType.FULL_DATABASE) "Create Full Backup" else "Backup Now",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.white
            )
        }
    }
}

/**
 * Format backup timestamp for display
 */
private fun formatBackupTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60 * 1000 -> "Just now"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} minutes ago"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} hours ago"
        diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)} days ago"
        else -> {
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

/**
 * Format file size for display.
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
        else -> "$bytes bytes"
    }
}
