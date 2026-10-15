package com.boox.day.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boox.day.data.db.SyncStatus
import com.boox.day.ui.theme.EInkTheme

/**
 * Badge indicator showing sync status for individual events.
 * Used in event list items to show if an event has pending changes.
 *
 * E-Ink optimized: Small, high-contrast icons with clear meaning.
 */
@Composable
fun EventSyncBadge(
    syncStatus: SyncStatus,
    syncEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    // Don't show badge for local-only events when sync is disabled
    if (!syncEnabled && syncStatus == SyncStatus.LOCAL_ONLY) {
        return
    }

    val (icon, tint, description) = when {
        !syncEnabled -> Triple(
            Icons.Filled.CloudOff,
            EInkTheme.Colors.gray,
            "Local only"
        )
        syncStatus == SyncStatus.SYNCED -> Triple(
            Icons.Filled.CloudDone,
            EInkTheme.Colors.gray,
            "Synced"
        )
        syncStatus == SyncStatus.PENDING_CREATE -> Triple(
            Icons.Filled.CloudUpload,
            EInkTheme.Colors.primary,
            "Pending upload"
        )
        syncStatus == SyncStatus.PENDING_UPDATE -> Triple(
            Icons.Filled.CloudUpload,
            EInkTheme.Colors.primary,
            "Pending update"
        )
        syncStatus == SyncStatus.PENDING_DELETE -> Triple(
            Icons.Filled.CloudUpload,
            EInkTheme.Colors.darkGray,
            "Pending delete"
        )
        syncStatus == SyncStatus.CONFLICT -> Triple(
            Icons.Filled.Error,
            EInkTheme.Colors.primary,
            "Conflict"
        )
        else -> Triple(
            Icons.Filled.CloudQueue,
            EInkTheme.Colors.gray,
            "Local"
        )
    }

    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = modifier.size(16.dp)
    )
}

/**
 * Larger pending sync indicator with count badge.
 * Used in toolbar or header to show overall pending changes.
 */
@Composable
fun PendingSyncCounter(
    pendingCount: Int,
    isSyncing: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (pendingCount == 0 && !isSyncing) {
        return
    }

    Box(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isSyncing) Icons.Filled.Sync else Icons.Filled.CloudUpload,
                contentDescription = if (isSyncing) "Syncing" else "$pendingCount pending",
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
            )

            if (!isSyncing && pendingCount > 0) {
                Text(
                    text = pendingCount.toString(),
                    fontSize = EInkTheme.Typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )
            }
        }
    }
}

/**
 * Sync status row for event details dialog.
 * Shows sync status with action button if needed.
 */
@Composable
fun SyncStatusRow(
    syncStatus: SyncStatus,
    syncEnabled: Boolean,
    googleEventId: String?,
    lastSyncedAt: Long?,
    onToggleSync: ((Boolean) -> Unit)? = null,
    onSyncNow: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.lightGray, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
            .padding(EInkTheme.Global.paddingSmall),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EventSyncBadge(
                syncStatus = syncStatus,
                syncEnabled = syncEnabled
            )

            Text(
                text = "Sync Status",
                fontSize = EInkTheme.Typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary,
                modifier = Modifier.weight(1f)
            )

            // Sync toggle
            if (onToggleSync != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (syncEnabled) "On" else "Off",
                        fontSize = EInkTheme.Typography.labelMedium,
                        color = EInkTheme.Colors.darkGray
                    )
                    EInkToggle(
                        checked = syncEnabled,
                        onCheckedChange = onToggleSync
                    )
                }
            }
        }

        // Status text
        Text(
            text = getSyncStatusDescription(syncStatus, syncEnabled, googleEventId, lastSyncedAt),
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.darkGray
        )

        // Sync now button for pending changes
        if (syncEnabled && onSyncNow != null && syncStatus.isPending()) {
            EinkSecondaryButton(
                text = "Sync Now",
                onClick = onSyncNow,
                width = 90.dp,
                height = 32.dp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// =============================================================================
// Helper Functions
// =============================================================================

private fun getSyncStatusDescription(
    syncStatus: SyncStatus,
    syncEnabled: Boolean,
    googleEventId: String?,
    lastSyncedAt: Long?
): String {
    return when {
        !syncEnabled -> "This event is local only and won't sync with Google Calendar."
        syncStatus == SyncStatus.SYNCED -> {
            val lastSync = lastSyncedAt?.let { formatLastSyncTime(it) } ?: "unknown"
            "Synced with Google Calendar. Last sync: $lastSync"
        }
        syncStatus == SyncStatus.PENDING_CREATE -> "Will be created on Google Calendar on next sync."
        syncStatus == SyncStatus.PENDING_UPDATE -> "Changes will be uploaded on next sync."
        syncStatus == SyncStatus.PENDING_DELETE -> "Will be deleted from Google Calendar on next sync."
        syncStatus == SyncStatus.CONFLICT -> "This event has conflicting changes. Please resolve the conflict."
        syncStatus == SyncStatus.LOCAL_ONLY -> "Enable sync to upload this event to Google Calendar."
        else -> "Status unknown"
    }
}

private fun formatLastSyncTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "just now"
        diff < 3600_000 -> "${diff / 60_000} min ago"
        diff < 86400_000 -> "${diff / 3600_000} hours ago"
        else -> "${diff / 86400_000} days ago"
    }
}

private fun SyncStatus.isPending(): Boolean {
    return this == SyncStatus.PENDING_CREATE ||
           this == SyncStatus.PENDING_UPDATE ||
           this == SyncStatus.PENDING_DELETE
}
