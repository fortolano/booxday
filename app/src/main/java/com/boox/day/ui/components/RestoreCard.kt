package com.boox.day.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.sync.DriveBackupService
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Card for restoring backups from Google Drive.
 * Shows a list of available backups (JSON and Full Database) and allows selection for restore.
 * Includes confirmation popup before restoring.
 *
 * Full database backups are shown in a separate section with "FULL" badge.
 */
@Composable
fun RestoreCard(
    isSignedIn: Boolean,
    backups: List<DriveBackupService.BackupInfo>,
    isLoadingBackups: Boolean,
    isRestoreInProgress: Boolean,
    restoreStatus: String?,
    onRefreshBackups: () -> Unit,
    onRestoreBackup: (DriveBackupService.BackupInfo) -> Unit,
    // Full backup support (optional - if not provided, full backups section won't show)
    fullBackups: List<DriveBackupService.FullBackupInfo> = emptyList(),
    onRestoreFullBackup: ((DriveBackupService.FullBackupInfo) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Confirmation state
    var showConfirmation by remember { mutableStateOf(false) }
    var selectedBackup by remember { mutableStateOf<DriveBackupService.BackupInfo?>(null) }
    var selectedFullBackup by remember { mutableStateOf<DriveBackupService.FullBackupInfo?>(null) }
    var showFullBackupConfirmation by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = EInkTheme.Colors.white,
        border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.lightGray),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = EInkTheme.Colors.black,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Restore from Backup",
                        fontSize = EInkTheme.Typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = EInkTheme.Colors.black
                    )
                }

                // Refresh button
                if (isSignedIn && !isLoadingBackups && !isRestoreInProgress) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .noRippleClickable { onRefreshBackups() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = EInkTheme.Colors.darkGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Status message
            if (restoreStatus != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (restoreStatus.startsWith("✓")) EInkTheme.Colors.offWhite
                            else if (restoreStatus.contains("failed", ignoreCase = true)) EInkTheme.Colors.offWhite
                            else EInkTheme.Colors.white
                        )
                        .border(1.dp, EInkTheme.Colors.lightGray)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (restoreStatus.startsWith("✓")) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = EInkTheme.Colors.black,
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (restoreStatus.contains("failed", ignoreCase = true)) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = EInkTheme.Colors.darkGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = restoreStatus,
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.darkGray
                    )
                }
            }

            // Content
            if (!isSignedIn) {
                Text(
                    text = "Connect your Google account above to restore from cloud backups",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (isLoadingBackups) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = EInkTheme.Colors.darkGray
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Loading backups...",
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.gray
                    )
                }
            } else if (isRestoreInProgress) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = EInkTheme.Colors.darkGray
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Restoring backup...",
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.gray
                    )
                }
            } else if (backups.isEmpty() && fullBackups.isEmpty()) {
                Text(
                    text = "No backups found. Create a backup first using the button above.",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Full Database Backups (show first if available)
                if (fullBackups.isNotEmpty() && onRestoreFullBackup != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = EInkTheme.Colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Full Database Backups (${fullBackups.size})",
                            fontSize = EInkTheme.Typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.Colors.primary
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        fullBackups.take(3).forEach { fullBackup ->
                            FullBackupItem(
                                backup = fullBackup,
                                onRestore = {
                                    selectedFullBackup = fullBackup
                                    showFullBackupConfirmation = true
                                }
                            )
                        }
                    }

                    if (backups.isNotEmpty()) {
                        Divider(
                            color = EInkTheme.Colors.lightGray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                // JSON Backups
                if (backups.isNotEmpty()) {
                    Text(
                        text = "Basic Backups (${backups.size})",
                        fontSize = EInkTheme.Typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.darkGray
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Show up to 5 most recent backups
                        backups.take(5).forEach { backup ->
                            BackupItem(
                                backup = backup,
                                onRestore = {
                                    selectedBackup = backup
                                    showConfirmation = true
                                }
                            )
                        }

                        if (backups.size > 5) {
                            Text(
                                text = "... and ${backups.size - 5} more",
                                fontSize = EInkTheme.Typography.labelSmall,
                                color = EInkTheme.Colors.gray,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // JSON backup confirmation popup
    if (showConfirmation && selectedBackup != null) {
        RestoreConfirmationPopup(
            backup = selectedBackup!!,
            onConfirm = {
                showConfirmation = false
                selectedBackup?.let { onRestoreBackup(it) }
                selectedBackup = null
            },
            onDismiss = {
                showConfirmation = false
                selectedBackup = null
            }
        )
    }

    // Full backup confirmation popup
    if (showFullBackupConfirmation && selectedFullBackup != null && onRestoreFullBackup != null) {
        com.boox.day.ui.dialogs.FullRestoreConfirmationPopup(
            backup = selectedFullBackup!!,
            onConfirm = {
                showFullBackupConfirmation = false
                selectedFullBackup?.let { onRestoreFullBackup(it) }
                selectedFullBackup = null
            },
            onCancel = {
                showFullBackupConfirmation = false
                selectedFullBackup = null
            }
        )
    }
}

/**
 * Confirmation popup before restoring a backup.
 * Warns the user that all current data will be replaced.
 */
@Composable
private fun RestoreConfirmationPopup(
    backup: DriveBackupService.BackupInfo,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }
    val formattedDate = remember(backup.createdTime) {
        if (backup.createdTime > 0) dateFormat.format(Date(backup.createdTime)) else "Unknown date"
    }

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
                    .fillMaxWidth(0.85f)
                    .background(EInkTheme.Colors.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.black, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Warning icon
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = EInkTheme.Colors.black,
                    modifier = Modifier.size(40.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Title
                Text(
                    text = "Restore from Backup?",
                    fontSize = EInkTheme.Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.black,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Warning message
                Text(
                    text = "This will REPLACE all your current data with the backup from:",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Backup date
                Text(
                    text = formattedDate,
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.black,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Detailed warning
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EInkTheme.Colors.offWhite)
                        .border(1.dp, EInkTheme.Colors.darkGray)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "The following will be replaced:",
                        fontSize = EInkTheme.Typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• All memos and handwritten notes\n" +
                                "• All tasks and task pages\n" +
                                "• All local calendar events\n" +
                                "• All app settings",
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.darkGray
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Any changes made since this backup will be lost.",
                    fontSize = EInkTheme.Typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.black,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.black)
                            .noRippleClickable { onDismiss() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = EInkTheme.Typography.button,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.Colors.black
                        )
                    }

                    // Restore button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(EInkTheme.Colors.black)
                            .noRippleClickable { onConfirm() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Restore",
                            fontSize = EInkTheme.Typography.button,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.Colors.white
                        )
                    }
                }
            }
        }
    }
}

/**
 * Item for displaying a full database backup with FULL badge.
 */
@Composable
private fun FullBackupItem(
    backup: DriveBackupService.FullBackupInfo,
    onRestore: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }
    val formattedDate = remember(backup.createdTime) {
        if (backup.createdTime > 0) {
            dateFormat.format(Date(backup.createdTime))
        } else {
            "Unknown date"
        }
    }
    val formattedSize = remember(backup.databaseSizeBytes) {
        when {
            backup.databaseSizeBytes < 1024 -> "${backup.databaseSizeBytes} B"
            backup.databaseSizeBytes < 1024 * 1024 -> "${backup.databaseSizeBytes / 1024} KB"
            else -> String.format("%.1f MB", backup.databaseSizeBytes / (1024.0 * 1024.0))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary)
            .noRippleClickable { onRestore() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // FULL badge
                Box(
                    modifier = Modifier
                        .background(EInkTheme.Colors.primary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "FULL",
                        fontSize = EInkTheme.Typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.white
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formattedSize,
                    fontSize = EInkTheme.Typography.labelSmall,
                    color = EInkTheme.Colors.gray
                )
                backup.metadata?.let { meta ->
                    Text(
                        text = "v${meta.databaseVersion}",
                        fontSize = EInkTheme.Typography.labelSmall,
                        color = EInkTheme.Colors.gray
                    )
                }
            }
        }

        Text(
            text = "Restore",
            fontSize = EInkTheme.Typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.Colors.white,
            modifier = Modifier
                .background(EInkTheme.Colors.primary, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun BackupItem(
    backup: DriveBackupService.BackupInfo,
    onRestore: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }
    val formattedDate = remember(backup.createdTime) {
        if (backup.createdTime > 0) {
            dateFormat.format(Date(backup.createdTime))
        } else {
            "Unknown date"
        }
    }
    val formattedSize = remember(backup.sizeBytes) {
        when {
            backup.sizeBytes < 1024 -> "${backup.sizeBytes} B"
            backup.sizeBytes < 1024 * 1024 -> "${backup.sizeBytes / 1024} KB"
            else -> String.format("%.1f MB", backup.sizeBytes / (1024.0 * 1024.0))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, EInkTheme.Colors.lightGray)
            .noRippleClickable { onRestore() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = formattedDate,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formattedSize,
                fontSize = EInkTheme.Typography.labelSmall,
                color = EInkTheme.Colors.gray
            )
        }

        Text(
            text = "Restore",
            fontSize = EInkTheme.Typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.Colors.black,
            modifier = Modifier
                .border(1.dp, EInkTheme.Colors.black)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
