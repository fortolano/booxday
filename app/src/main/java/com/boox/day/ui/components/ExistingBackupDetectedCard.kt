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
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.boox.day.sync.BackupType
import com.boox.day.sync.DriveBackupService
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Card shown when existing backups are detected in Google Drive
 * but backup is not yet enabled locally.
 *
 * Two variants based on local database state:
 * - Fresh install: Prominent restore option, secondary "start fresh"
 * - Has data: Warning about overwriting, confirm to proceed
 *
 * E-Ink optimized: Uses noRippleClickable, EInkTheme constants, no shadows.
 */
@Composable
fun ExistingBackupDetectedCard(
    backupSummary: DriveBackupService.ExistingBackupSummary,
    isLocalDatabaseFresh: Boolean,
    onRestoreBackup: () -> Unit,
    onEnableNewBackup: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }
    val formattedDate = remember(backupSummary.newestBackupDate) {
        if (backupSummary.newestBackupDate > 0) {
            dateFormat.format(Date(backupSummary.newestBackupDate))
        } else {
            "Unknown date"
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = EInkTheme.Colors.white,
        border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary),
        elevation = 0.dp,
        shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLocalDatabaseFresh) {
                // STATE A: Fresh Install - Restore-first UI
                FreshInstallContent(
                    backupSummary = backupSummary,
                    formattedDate = formattedDate,
                    onRestoreBackup = onRestoreBackup,
                    onEnableNewBackup = onEnableNewBackup
                )
            } else {
                // STATE B: Has Data - Warning UI
                HasDataContent(
                    backupSummary = backupSummary,
                    formattedDate = formattedDate,
                    onEnableNewBackup = onEnableNewBackup,
                    onCancel = onCancel
                )
            }
        }
    }
}

/**
 * Fresh install content: Prominent restore option with secondary start fresh.
 */
@Composable
private fun FreshInstallContent(
    backupSummary: DriveBackupService.ExistingBackupSummary,
    formattedDate: String,
    onRestoreBackup: () -> Unit,
    onEnableNewBackup: () -> Unit
) {
    // Header
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CloudDownload,
            contentDescription = null,
            tint = EInkTheme.Colors.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = "Restore from Backup",
            fontSize = EInkTheme.Typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary
        )
    }

    Text(
        text = "We found an existing backup from your account:",
        fontSize = EInkTheme.Typography.bodyMedium,
        color = EInkTheme.Colors.darkGray
    )

    // Backup info card
    BackupInfoBox(
        backupSummary = backupSummary,
        formattedDate = formattedDate
    )

    // Primary action: Restore
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.Button.height)
            .background(EInkTheme.Colors.primary, RoundedCornerShape(6.dp))
            .noRippleClickable { onRestoreBackup() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Restore This Backup",
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.white
        )
    }

    // Divider with "OR"
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(EInkTheme.Colors.lightGray)
        )
        Text(
            text = "  OR  ",
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.gray
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(EInkTheme.Colors.lightGray)
        )
    }

    // Secondary action: Start fresh
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Start fresh and enable new backups:",
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.darkGray
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = EInkTheme.Colors.darkGray,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "This will DELETE your existing backup files.",
                fontSize = EInkTheme.Typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.darkGray
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(EInkTheme.Button.height)
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.darkGray, RoundedCornerShape(6.dp))
                .noRippleClickable { onEnableNewBackup() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Enable New Backup (Overwrites Old)",
                fontSize = EInkTheme.Typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.darkGray
            )
        }
    }
}

/**
 * Has data content: Warning about overwriting existing backups.
 */
@Composable
private fun HasDataContent(
    backupSummary: DriveBackupService.ExistingBackupSummary,
    formattedDate: String,
    onEnableNewBackup: () -> Unit,
    onCancel: () -> Unit
) {
    // Header with warning
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = EInkTheme.Colors.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = "Existing Backup Detected",
            fontSize = EInkTheme.Typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary
        )
    }

    Text(
        text = "We found backup files from a previous installation:",
        fontSize = EInkTheme.Typography.bodyMedium,
        color = EInkTheme.Colors.darkGray
    )

    // Backup summary
    BackupInfoBox(
        backupSummary = backupSummary,
        formattedDate = formattedDate
    )

    // Warning box
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.offWhite, RoundedCornerShape(4.dp))
            .border(1.dp, EInkTheme.Colors.darkGray, RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = EInkTheme.Colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                text = "Enabling backup will REPLACE these files with your current data.",
                fontSize = EInkTheme.Typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "This action cannot be undone.",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )
        }
    }

    // Buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Cancel button
        Box(
            modifier = Modifier
                .weight(1f)
                .height(EInkTheme.Button.height)
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(6.dp))
                .noRippleClickable { onCancel() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Cancel",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )
        }

        // Enable backup button
        Box(
            modifier = Modifier
                .weight(1f)
                .height(EInkTheme.Button.height)
                .background(EInkTheme.Colors.primary, RoundedCornerShape(6.dp))
                .noRippleClickable { onEnableNewBackup() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Enable Backup",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.white
            )
        }
    }
}

/**
 * Box displaying backup summary information.
 */
@Composable
private fun BackupInfoBox(
    backupSummary: DriveBackupService.ExistingBackupSummary,
    formattedDate: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, EInkTheme.Colors.lightGray, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Date and badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formattedDate,
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary
            )

            // Backup type badge
            Box(
                modifier = Modifier
                    .background(
                        if (backupSummary.newestBackupType == BackupType.FULL_DATABASE)
                            EInkTheme.Colors.primary
                        else
                            EInkTheme.Colors.offWhite,
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        1.dp,
                        if (backupSummary.newestBackupType == BackupType.FULL_DATABASE)
                            EInkTheme.Colors.primary
                        else
                            EInkTheme.Colors.darkGray,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (backupSummary.newestBackupType == BackupType.FULL_DATABASE) "FULL" else "JSON",
                    fontSize = EInkTheme.Typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (backupSummary.newestBackupType == BackupType.FULL_DATABASE)
                        EInkTheme.Colors.white
                    else
                        EInkTheme.Colors.darkGray
                )
            }
        }

        // Stats (only shown if available from full backup metadata)
        if (backupSummary.memoPageCount != null ||
            backupSummary.taskCount != null ||
            backupSummary.ocrPageCount != null
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                backupSummary.memoPageCount?.let { count ->
                    Text(
                        text = "\u2022 $count memo pages",
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.darkGray
                    )
                }
                backupSummary.taskCount?.let { count ->
                    Text(
                        text = "\u2022 $count tasks",
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.darkGray
                    )
                }
                backupSummary.ocrPageCount?.let { count ->
                    if (count > 0) {
                        Text(
                            text = "\u2022 $count OCR-indexed pages",
                            fontSize = EInkTheme.Typography.bodySmall,
                            color = EInkTheme.Colors.darkGray
                        )
                    }
                }
            }
        } else {
            // For JSON backups without metadata
            Text(
                text = "${backupSummary.totalBackupCount} backup(s) available",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )
        }
    }
}
