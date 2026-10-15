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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local Backup card for device storage backup/restore.
 * Allows users to save backups to and restore from local device storage via SAF.
 *
 * E-Ink optimized: High contrast, clear typography, large touch targets.
 */
@Composable
fun LocalBackupCard(
    lastLocalBackupTime: Long,
    isSaveInProgress: Boolean,
    isRestoreInProgress: Boolean,
    saveStatus: String?,
    restoreStatus: String?,
    onSaveBackupClick: () -> Unit,
    onSelectFileClick: () -> Unit,
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
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = EInkTheme.Colors.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Local Backup",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Save to and restore from device storage",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            // What's Included Section
            WhatsIncludedSection()

            Divider(
                color = EInkTheme.Divider.subtleColor,
                thickness = EInkTheme.Divider.subtleThickness,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Save to Device Section
            SaveToDeviceSection(
                lastBackupTime = lastLocalBackupTime,
                isInProgress = isSaveInProgress,
                status = saveStatus,
                onSaveClick = onSaveBackupClick
            )

            Divider(
                color = EInkTheme.Divider.subtleColor,
                thickness = EInkTheme.Divider.subtleThickness,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Restore from File Section
            RestoreFromFileSection(
                isInProgress = isRestoreInProgress,
                status = restoreStatus,
                onSelectFileClick = onSelectFileClick
            )
        }
    }
}

@Composable
private fun WhatsIncludedSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.offWhite, RoundedCornerShape(6.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "What's included:",
            fontSize = EInkTheme.Typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary
        )

        val items = listOf(
            "Memos & Drawings (pages, strokes, images)",
            "Tasks & Due Dates",
            "Local Events & Calendars",
            "Notebooks & Folders",
            "OCR Data & Semantic Analysis",
            "Daily Summaries",
            "App Settings"
        )

        items.forEach { item ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\u2022",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray
                )
                Text(
                    text = item,
                    fontSize = EInkTheme.Typography.labelSmall,
                    color = EInkTheme.Colors.gray
                )
            }
        }
    }
}

@Composable
private fun SaveToDeviceSection(
    lastBackupTime: Long,
    isInProgress: Boolean,
    status: String?,
    onSaveClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Save to Device",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )

            Text(
                text = if (lastBackupTime > 0) {
                    "Last: ${formatBackupTime(lastBackupTime)}"
                } else {
                    "Never backed up"
                },
                fontSize = EInkTheme.Typography.labelSmall,
                color = EInkTheme.Colors.gray
            )
        }

        // Status message
        if (status != null) {
            StatusMessage(status = status)
        }

        // Save Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(EInkTheme.Button.height)
                .background(
                    if (isInProgress) EInkTheme.Colors.lightGray else EInkTheme.Colors.primary,
                    RoundedCornerShape(6.dp)
                )
                .then(
                    if (isInProgress) Modifier else Modifier.noRippleClickable { onSaveClick() }
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
                        text = "Creating backup...",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.primary
                    )
                }
            } else {
                Text(
                    text = "Save Backup to Device",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.white
                )
            }
        }
    }
}

@Composable
private fun RestoreFromFileSection(
    isInProgress: Boolean,
    status: String?,
    onSelectFileClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Restore from File",
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.Colors.primary
        )

        Text(
            text = "Select a backup file from your device",
            fontSize = EInkTheme.Typography.labelSmall,
            color = EInkTheme.Colors.gray
        )

        // Status message
        if (status != null) {
            StatusMessage(status = status)
        }

        // Select File Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(EInkTheme.Button.height)
                .background(EInkTheme.Colors.offWhite, RoundedCornerShape(6.dp))
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(6.dp))
                .then(
                    if (isInProgress) Modifier else Modifier.noRippleClickable { onSelectFileClick() }
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
                        text = "Restoring...",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.primary
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = EInkTheme.Colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Select Backup File",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusMessage(status: String) {
    val isSuccess = status.startsWith("\u2713") || status.startsWith("Restored")
    val isError = status.contains("failed", ignoreCase = true) || status.contains("error", ignoreCase = true)
    val isLoading = status.contains("Creating") || status.contains("Restoring") || status.contains("...")

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
            text = status,
            fontSize = EInkTheme.Typography.labelSmall,
            color = when {
                isError -> Color(0xFFE65100) // Dark amber for error text
                isSuccess -> EInkTheme.Colors.primary
                else -> EInkTheme.Colors.gray
            }
        )
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
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} min ago"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}h ago"
        diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}d ago"
        else -> {
            val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
