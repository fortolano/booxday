package com.boox.day.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boox.day.data.StorageCalculator
import com.boox.day.data.datastore.AppSettings
import com.boox.day.ui.theme.EInkTheme

/**
 * Storage information card for the Advanced settings tab.
 * Shows a user-friendly breakdown of app content and storage.
 *
 * E-Ink optimized: High contrast, clear typography, no unnecessary animations.
 */
@Composable
fun StorageInfoCard(
    storageInfo: StorageCalculator.StorageInfo?,
    isLoading: Boolean,
    syncMode: AppSettings.SyncMode = AppSettings.SyncMode.STANDALONE,
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
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = EInkTheme.Colors.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Storage Usage",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                // Loading state
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = EInkTheme.Colors.primary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Calculating storage...",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.gray
                    )
                }
            } else if (storageInfo != null) {
                // Total storage
                Text(
                    text = "Total: ${StorageCalculator.formatBytes(storageInfo.totalBytes)}",
                    fontSize = EInkTheme.Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Content breakdown - matching LocalBackupCard categories

                // 1. Memos & Drawings (pages, strokes, images)
                ContentGroupRow(
                    emoji = "📝",
                    label = "Memos & Drawings",
                    detail = "${storageInfo.memoPagesCount} pages, ${storageInfo.strokesCount} strokes"
                )
                if (storageInfo.imagesBytes > 0) {
                    Text(
                        text = "    + ${StorageCalculator.formatBytes(storageInfo.imagesBytes)} images",
                        fontSize = EInkTheme.Typography.labelSmall,
                        color = EInkTheme.Colors.gray,
                        modifier = Modifier.padding(start = 36.dp)
                    )
                }

                Divider(
                    color = EInkTheme.Divider.subtleColor,
                    thickness = EInkTheme.Divider.subtleThickness,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // 2. Tasks
                ContentCountRow(
                    emoji = "✓",
                    label = "Tasks",
                    count = storageInfo.tasksCount,
                    unit = "tasks"
                )

                Divider(
                    color = EInkTheme.Divider.subtleColor,
                    thickness = EInkTheme.Divider.subtleThickness,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // 3. Events & Calendars
                val eventsLabel = if (syncMode == AppSettings.SyncMode.HYBRID) {
                    "Events & Calendars"
                } else {
                    "Local Events & Calendars"
                }
                ContentGroupRow(
                    emoji = "📅",
                    label = eventsLabel,
                    detail = "${storageInfo.eventsCount} events, ${storageInfo.calendarsCount} calendars"
                )

                // 4. Notebooks & Folders (only show if there are any)
                if (storageInfo.notebooksCount > 0 || storageInfo.foldersCount > 0) {
                    Divider(
                        color = EInkTheme.Divider.subtleColor,
                        thickness = EInkTheme.Divider.subtleThickness,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    ContentGroupRow(
                        emoji = "📁",
                        label = "Notebooks & Folders",
                        detail = "${storageInfo.notebooksCount} notebooks, ${storageInfo.foldersCount} folders"
                    )
                }

                // 5. OCR & Semantic Data (only show if there are any)
                if (storageInfo.ocrIndexedCount > 0) {
                    Divider(
                        color = EInkTheme.Divider.subtleColor,
                        thickness = EInkTheme.Divider.subtleThickness,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    ContentCountRow(
                        emoji = "🔍",
                        label = "OCR & Semantic Data",
                        count = storageInfo.ocrIndexedCount,
                        unit = "indexed pages"
                    )
                }

                // 6. Daily Summaries (only show if there are any)
                if (storageInfo.summariesCount > 0) {
                    Divider(
                        color = EInkTheme.Divider.subtleColor,
                        thickness = EInkTheme.Divider.subtleThickness,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    ContentCountRow(
                        emoji = "📊",
                        label = "Daily Summaries",
                        count = storageInfo.summariesCount,
                        unit = "summaries"
                    )
                }
            } else {
                // Error/empty state
                Text(
                    text = "Unable to calculate storage",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.gray
                )
            }
        }
    }
}

/**
 * Row showing a content count (e.g., "Events: 42 calendar events")
 */
@Composable
private fun ContentCountRow(
    emoji: String,
    label: String,
    count: Int,
    unit: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji badge
            Box(
                modifier = Modifier
                    .background(
                        EInkTheme.Colors.lightGray,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = emoji,
                    fontSize = EInkTheme.Typography.bodySmall
                )
            }
            Text(
                text = label,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )
        }
        Text(
            text = "$count $unit",
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.gray
        )
    }
}

/**
 * Row showing a content group with custom detail text (e.g., "Memos & Drawings: 45 pages, 230 strokes")
 */
@Composable
private fun ContentGroupRow(
    emoji: String,
    label: String,
    detail: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji badge
            Box(
                modifier = Modifier
                    .background(
                        EInkTheme.Colors.lightGray,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = emoji,
                    fontSize = EInkTheme.Typography.bodySmall
                )
            }
            Text(
                text = label,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )
        }
        Text(
            text = detail,
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.gray
        )
    }
}

/**
 * Row showing storage size (e.g., "Images: 12.5 MB")
 */
@Composable
private fun StorageSizeRow(
    emoji: String,
    label: String,
    bytes: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji badge
            Box(
                modifier = Modifier
                    .background(
                        EInkTheme.Colors.lightGray,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = emoji,
                    fontSize = EInkTheme.Typography.bodySmall
                )
            }
            Text(
                text = label,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )
        }
        Text(
            text = StorageCalculator.formatBytes(bytes),
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.gray
        )
    }
}
