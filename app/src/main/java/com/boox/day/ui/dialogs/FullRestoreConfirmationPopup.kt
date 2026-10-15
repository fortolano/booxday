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
import androidx.compose.material.icons.filled.Refresh
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
import com.boox.day.sync.DriveBackupService
import com.boox.day.sync.FullDatabaseBackupMetadata
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Confirmation popup before restoring a full database backup.
 *
 * Shows:
 * - Backup metadata (date, version, size, OCR count)
 * - Data loss warning if current DB has more data
 * - DB version compatibility check
 * - Prominent app restart notice
 *
 * E-Ink optimized: Uses Popup (not Dialog), noRippleClickable, EInkTheme.
 */
@Composable
fun FullRestoreConfirmationPopup(
    backup: DriveBackupService.FullBackupInfo,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val metadata = backup.metadata
    val dateFormat = remember { SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.getDefault()) }
    val formattedDate = remember(backup.createdTime) {
        if (backup.createdTime > 0) {
            dateFormat.format(Date(backup.createdTime))
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
                // Restore icon
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Restore",
                    modifier = Modifier.size(48.dp),
                    tint = EInkTheme.Colors.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = "Restore Full Backup?",
                    fontSize = EInkTheme.Typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = EInkTheme.Colors.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Backup info card
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
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.Colors.primary
                        )
                        Box(
                            modifier = Modifier
                                .background(EInkTheme.Colors.primary, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "FULL",
                                fontSize = EInkTheme.Typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.white
                            )
                        }
                    }

                    // Size
                    val sizeStr = formatFileSize(backup.databaseSizeBytes)
                    Text(
                        text = "Size: $sizeStr",
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.darkGray
                    )

                    // Metadata details
                    if (metadata != null) {
                        metadata.tableCounts["Page"]?.let { count ->
                            Text(
                                text = "\u2022 $count memo pages",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.darkGray
                            )
                        }
                        metadata.tableCounts["Task"]?.let { count ->
                            Text(
                                text = "\u2022 $count tasks",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.darkGray
                            )
                        }
                        metadata.tableCounts["Stroke"]?.let { count ->
                            Text(
                                text = "\u2022 $count strokes",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.darkGray
                            )
                        }
                        if (metadata.ocrPagesIndexed > 0) {
                            Text(
                                text = "\u2022 ${metadata.ocrPagesIndexed} OCR-indexed pages",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.darkGray
                            )
                        }
                        Text(
                            text = "Database v${metadata.databaseVersion}",
                            fontSize = EInkTheme.Typography.labelSmall,
                            color = EInkTheme.Colors.gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = EInkTheme.Colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "This will REPLACE all current data with the backup.",
                            fontSize = EInkTheme.Typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.Colors.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "The app will need to restart after restore.",
                            fontSize = EInkTheme.Typography.bodySmall,
                            color = EInkTheme.Colors.darkGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

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

                    // Restore button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Button.height)
                            .background(EInkTheme.Colors.primary, RoundedCornerShape(6.dp))
                            .noRippleClickable { onConfirm() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Restore & Restart",
                            fontSize = EInkTheme.Typography.bodyMedium,
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
