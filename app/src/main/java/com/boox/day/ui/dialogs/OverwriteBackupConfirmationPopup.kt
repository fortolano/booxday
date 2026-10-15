package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.sync.BackupType
import com.boox.day.sync.DriveBackupService
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Confirmation popup before overwriting existing backup files.
 * Shows what will be lost and requires explicit confirmation.
 *
 * Displayed when user chooses "Enable New Backup" when existing backups exist.
 *
 * E-Ink optimized: Uses Popup (not Dialog), noRippleClickable, EInkTheme.
 */
@Composable
fun OverwriteBackupConfirmationPopup(
    existingBackupInfo: DriveBackupService.ExistingBackupSummary,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }
    val formattedDate = remember(existingBackupInfo.newestBackupDate) {
        if (existingBackupInfo.newestBackupDate > 0) {
            dateFormat.format(Date(existingBackupInfo.newestBackupDate))
        } else {
            "Unknown date"
        }
    }

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
                    .fillMaxWidth(0.85f)
                    .background(
                        EInkTheme.Colors.background,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .border(
                        EInkTheme.Global.borderWidth,
                        EInkTheme.Colors.primary,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Warning icon
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    modifier = Modifier.size(48.dp),
                    tint = EInkTheme.Colors.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = "Delete Existing Backups?",
                    fontSize = EInkTheme.Typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = EInkTheme.Colors.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // What will be deleted
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EInkTheme.Colors.offWhite, RoundedCornerShape(8.dp))
                        .border(1.dp, EInkTheme.Colors.darkGray, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "The following will be permanently deleted:",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.primary
                    )

                    // Backup date
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\u2022",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.darkGray
                        )
                        Text(
                            text = "Backup from $formattedDate",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.darkGray
                        )
                        // Badge
                        Box(
                            modifier = Modifier
                                .background(
                                    if (existingBackupInfo.newestBackupType == BackupType.FULL_DATABASE)
                                        EInkTheme.Colors.primary
                                    else
                                        EInkTheme.Colors.lightGray,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (existingBackupInfo.newestBackupType == BackupType.FULL_DATABASE)
                                    "FULL" else "JSON",
                                fontSize = EInkTheme.Typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (existingBackupInfo.newestBackupType == BackupType.FULL_DATABASE)
                                    EInkTheme.Colors.white
                                else
                                    EInkTheme.Colors.darkGray
                            )
                        }
                    }

                    // Stats if available
                    existingBackupInfo.memoPageCount?.let { count ->
                        Text(
                            text = "\u2022 $count memo pages",
                            fontSize = EInkTheme.Typography.bodySmall,
                            color = EInkTheme.Colors.darkGray
                        )
                    }
                    existingBackupInfo.taskCount?.let { count ->
                        Text(
                            text = "\u2022 $count tasks",
                            fontSize = EInkTheme.Typography.bodySmall,
                            color = EInkTheme.Colors.darkGray
                        )
                    }
                    existingBackupInfo.ocrPageCount?.let { count ->
                        if (count > 0) {
                            Text(
                                text = "\u2022 $count OCR-indexed pages",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.darkGray
                            )
                        }
                    }

                    // Total backup count
                    if (existingBackupInfo.totalBackupCount > 1) {
                        Text(
                            text = "\u2022 ${existingBackupInfo.totalBackupCount} total backup files",
                            fontSize = EInkTheme.Typography.bodySmall,
                            color = EInkTheme.Colors.darkGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Warning text
                Text(
                    text = "This action CANNOT be undone.",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel button (prominent - this is the safer option)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Button.height)
                            .background(EInkTheme.Colors.primary, RoundedCornerShape(6.dp))
                            .noRippleClickable { onCancel() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.Colors.white
                        )
                    }

                    // Confirm delete button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Button.height)
                            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(6.dp))
                            .noRippleClickable { onConfirm() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Delete & Continue",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.Colors.primary
                        )
                    }
                }
            }
        }
    }
}
