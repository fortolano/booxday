package com.boox.day.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.sync.ImportService
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Confirmation popup showing detailed backup contents before restore.
 *
 * E-Ink optimized: Uses Popup (not Dialog), noRippleClickable, high contrast.
 */
@Composable
fun LocalRestoreConfirmationPopup(
    preview: ImportService.BackupPreview,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
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
                    .background(EInkTheme.Colors.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.Global.padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Warning Icon
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    modifier = Modifier.size(40.dp),
                    tint = EInkTheme.Colors.primary
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Title
                Text(
                    text = "Restore from Backup?",
                    fontSize = EInkTheme.Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Backup creation date
                Text(
                    text = "Created: ${formatDateTime(preview.metadata.createdAt)}",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "App version: ${preview.metadata.appVersion}",
                    fontSize = EInkTheme.Typography.labelSmall,
                    color = EInkTheme.Colors.gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Backup contents summary
                BackupContentsSummary(preview = preview)

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Warning message
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EInkTheme.Colors.offWhite, RoundedCornerShape(6.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = EInkTheme.Colors.primary
                    )
                    Column {
                        Text(
                            text = "This will REPLACE all current data.",
                            fontSize = EInkTheme.Typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.Colors.primary
                        )
                        Text(
                            text = "Changes since this backup will be lost.",
                            fontSize = EInkTheme.Typography.labelSmall,
                            color = EInkTheme.Colors.gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
                ) {
                    // Cancel button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Button.height)
                            .background(EInkTheme.Colors.offWhite, RoundedCornerShape(6.dp))
                            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(6.dp))
                            .noRippleClickable { onCancel() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = EInkTheme.Typography.button,
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
                            text = "Restore",
                            fontSize = EInkTheme.Typography.button,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.Colors.white
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupContentsSummary(preview: ImportService.BackupPreview) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.lightGray, RoundedCornerShape(6.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Backup Contents:",
            fontSize = EInkTheme.Typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Core data
        if (preview.pageCount > 0 || preview.strokeCount > 0) {
            ContentRow(
                label = "\u2022 ${preview.pageCount} Memo Pages, ${preview.strokeCount} Strokes"
            )
        }

        if (preview.taskCount > 0) {
            ContentRow(
                label = "\u2022 ${preview.taskCount} Tasks"
            )
        }

        if (preview.eventCount > 0 || preview.calendarCount > 0) {
            ContentRow(
                label = "\u2022 ${preview.eventCount} Local Events, ${preview.calendarCount} Calendars"
            )
        }

        // v2 data
        if (preview.notebookCount > 0 || preview.folderCount > 0) {
            ContentRow(
                label = "\u2022 ${preview.notebookCount} Notebooks, ${preview.folderCount} Folders"
            )
        }

        if (preview.attachmentCount > 0) {
            ContentRow(
                label = "\u2022 ${preview.attachmentCount} Attachments"
            )
        }

        if (preview.ocrPageCount > 0 || preview.ocrTextCount > 0) {
            ContentRow(
                label = "\u2022 ${preview.ocrTextCount} OCR Records (${preview.ocrPageCount} pages)"
            )
        }

        if (preview.summaryCount > 0) {
            ContentRow(
                label = "\u2022 ${preview.summaryCount} Daily Summaries"
            )
        }

        if (preview.settingCount > 0) {
            ContentRow(
                label = "\u2022 App Settings"
            )
        }
    }
}

@Composable
private fun ContentRow(label: String) {
    Text(
        text = label,
        fontSize = EInkTheme.Typography.labelSmall,
        color = EInkTheme.Colors.gray
    )
}

/**
 * Format timestamp to readable date/time string.
 */
private fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
