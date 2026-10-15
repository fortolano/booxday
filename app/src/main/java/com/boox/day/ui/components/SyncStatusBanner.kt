package com.boox.day.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boox.day.sync.SyncJobStatus
import com.boox.day.sync.SyncResult
import com.boox.day.ui.messages.AiFeedbackMessages
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column

/**
 * Sync status banner that displays at the top of the events panel.
 * Shows current sync state with appropriate icon and message.
 *
 * E-Ink optimized: Uses high-contrast colors and clear iconography.
 */
@Composable
fun SyncStatusBanner(
    syncResult: SyncResult?,
    syncJobStatus: SyncJobStatus?,
    onRetryClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Determine what to show based on status
    val bannerState = when {
        syncJobStatus is SyncJobStatus.Running -> BannerState.Syncing
        syncResult is SyncResult.Error -> BannerState.Error(syncResult.message, syncResult.isRecoverable)
        syncResult is SyncResult.NoNetwork -> BannerState.Offline(syncResult.pendingChanges)
        syncResult is SyncResult.PartialSuccess -> BannerState.PartialSuccess(syncResult.successCount, syncResult.failureCount)
        syncResult is SyncResult.Success && syncResult.hasConflicts -> BannerState.HasConflicts(syncResult.conflictCount)
        syncResult is SyncResult.Success -> BannerState.Success(syncResult.toDisplayMessage())
        syncResult is SyncResult.NotSignedIn -> BannerState.NotSignedIn
        else -> null
    }

    AnimatedVisibility(
        visible = bannerState != null,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        bannerState?.let { state ->
            SyncBannerContent(
                state = state,
                onRetryClick = onRetryClick,
                onDismiss = onDismiss
            )
        }
    }
}

/**
 * Compact sync indicator for toolbar or header area.
 * Shows just an icon with tooltip-style info.
 */
@Composable
fun SyncStatusIndicator(
    syncJobStatus: SyncJobStatus?,
    pendingChanges: Int = 0,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (icon, tint, description) = when {
        syncJobStatus is SyncJobStatus.Running -> Triple(
            Icons.Filled.Sync,
            EInkTheme.Colors.primary,
            "Syncing..."
        )
        syncJobStatus is SyncJobStatus.Failed -> Triple(
            Icons.Filled.Error,
            EInkTheme.Colors.primary,
            "Sync failed"
        )
        pendingChanges > 0 -> Triple(
            Icons.Filled.CloudSync,
            EInkTheme.Colors.darkGray,
            "$pendingChanges pending"
        )
        else -> Triple(
            Icons.Filled.CheckCircle,
            EInkTheme.Colors.gray,
            "Synced"
        )
    }

    Box(
        modifier = modifier
            .size(EInkTheme.Global.touchTarget)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(EInkTheme.Global.iconSize)
        )
    }
}

// =============================================================================
// Internal Components
// =============================================================================

private sealed class BannerState {
    object Syncing : BannerState()
    object NotSignedIn : BannerState()
    data class Success(val message: String) : BannerState()
    data class Error(val message: String, val isRecoverable: Boolean) : BannerState()
    data class Offline(val pendingChanges: Int) : BannerState()
    data class PartialSuccess(val successCount: Int, val failureCount: Int) : BannerState()
    data class HasConflicts(val conflictCount: Int) : BannerState()
}

@Composable
private fun SyncBannerContent(
    state: BannerState,
    onRetryClick: (() -> Unit)?,
    onDismiss: (() -> Unit)?
) {
    // Fun messages - remembered to stay stable during recomposition
    val syncingFunMessage = remember { AiFeedbackMessages.getRandomSyncingMessage() }
    val syncCompleteFunMessage = remember { AiFeedbackMessages.getRandomSyncCompleteMessage() }

    val config = when (state) {
        is BannerState.Syncing -> BannerConfig(
            icon = Icons.Filled.Sync,
            iconTint = EInkTheme.Colors.primary,
            message = syncingFunMessage,
            subtitle = "Fetching events from your cloud calendars...",
            showRetry = false
        )
        is BannerState.NotSignedIn -> BannerConfig(
            icon = Icons.Filled.CloudOff,
            iconTint = EInkTheme.Colors.gray,
            message = "Sign in to sync events",
            showRetry = false
        )
        is BannerState.Success -> BannerConfig(
            icon = Icons.Filled.CheckCircle,
            iconTint = EInkTheme.Colors.primary,
            message = syncCompleteFunMessage,
            subtitle = state.message,
            showRetry = false
        )
        is BannerState.Error -> BannerConfig(
            icon = Icons.Filled.Error,
            iconTint = EInkTheme.Colors.primary,
            message = state.message,
            showRetry = state.isRecoverable && onRetryClick != null
        )
        is BannerState.Offline -> BannerConfig(
            icon = Icons.Filled.CloudOff,
            iconTint = EInkTheme.Colors.darkGray,
            message = if (state.pendingChanges > 0) {
                "Offline - ${state.pendingChanges} change(s) pending"
            } else {
                "No internet connection"
            },
            showRetry = onRetryClick != null
        )
        is BannerState.PartialSuccess -> BannerConfig(
            icon = Icons.Filled.Warning,
            iconTint = EInkTheme.Colors.primary,
            message = "Synced ${state.successCount}, ${state.failureCount} failed",
            showRetry = onRetryClick != null
        )
        is BannerState.HasConflicts -> BannerConfig(
            icon = Icons.Filled.Warning,
            iconTint = EInkTheme.Colors.primary,
            message = "${state.conflictCount} conflict(s) need resolution",
            showRetry = false
        )
    }
    val (icon, iconTint, message, subtitle, showRetry) = config

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.offWhite)
            .border(
                width = EInkTheme.Global.borderWidth,
                color = EInkTheme.Colors.lightGray,
                shape = RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius)
            )
            .padding(horizontal = EInkTheme.Global.paddingSmall, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
        )

        // Message with optional subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message,
                fontSize = EInkTheme.Typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = EInkTheme.Typography.labelSmall,
                    color = EInkTheme.Colors.darkGray
                )
            }
        }

        if (showRetry && onRetryClick != null) {
            Text(
                text = "Retry",
                fontSize = EInkTheme.Typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary,
                modifier = Modifier
                    .clickable { onRetryClick() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        if (onDismiss != null && state !is BannerState.Syncing) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = EInkTheme.Colors.darkGray,
                modifier = Modifier
                    .size(18.dp)
                    .noRippleClickable { onDismiss() }
                    .padding(horizontal = 4.dp)
            )
        }
    }
}

private data class BannerConfig(
    val icon: ImageVector,
    val iconTint: Color,
    val message: String,
    val subtitle: String? = null, // Optional technical details below the fun message
    val showRetry: Boolean
)
