package com.boox.day.ui.dialogs

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.boox.day.ui.components.ScrollableWithIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.sync.SyncErrorType
import com.boox.day.sync.SyncResult
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.theme.EInkTheme
import androidx.compose.foundation.layout.Spacer

/**
 * Dialog for displaying detailed sync error information.
 * Shows error type, message, and appropriate recovery actions.
 *
 * E-Ink optimized: Uses Popup instead of Dialog, clear iconography.
 */
@Composable
fun SyncErrorDialog(
    syncResult: SyncResult.Error,
    onRetry: (() -> Unit)? = null,
    onSignIn: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val errorInfo = getErrorInfo(syncResult.errorType)

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                    .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.ModernDialog.padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = errorInfo.icon,
                        contentDescription = null,
                        tint = EInkTheme.ModernDialog.titleColor,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = errorInfo.title,
                        fontSize = EInkTheme.ModernDialog.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.ModernDialog.titleColor
                    )
                }

                Spacer(modifier = Modifier.height(EInkTheme.ModernDialog.contentSpacing))

                Divider(
                    color = EInkTheme.Divider.color,
                    thickness = EInkTheme.Divider.thickness
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Error message - E-Ink: use ScrollableWithIndicator for scroll feedback
                ScrollableWithIndicator(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = syncResult.message,
                            fontSize = EInkTheme.Typography.bodyLarge,
                            color = EInkTheme.Colors.primary
                        )

                        // Helpful description
                        Text(
                            text = errorInfo.description,
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.darkGray
                        )

                        // Suggestion
                        if (errorInfo.suggestion.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Suggestion:",
                                fontSize = EInkTheme.Typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.primary
                            )
                            Text(
                                text = errorInfo.suggestion,
                                fontSize = EInkTheme.Typography.bodyMedium,
                                color = EInkTheme.Colors.darkGray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(EInkTheme.ModernDialog.contentSpacing))

                // Action buttons - 40% / 20% / 40% layout
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EinkSecondaryButton(
                        text = "Close",
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.4f)
                    )

                    Spacer(modifier = Modifier.weight(0.2f))

                    // Show appropriate action button based on error type
                    when {
                        syncResult.errorType == SyncErrorType.AUTHENTICATION && onSignIn != null -> {
                            ActionButton(
                                text = "Sign In",
                                onClick = {
                                    onSignIn()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(0.4f)
                            )
                        }
                        syncResult.isRecoverable && onRetry != null -> {
                            ActionButton(
                                text = "Retry",
                                onClick = {
                                    onRetry()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(0.4f)
                            )
                        }
                        else -> {
                            Spacer(modifier = Modifier.weight(0.4f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog for showing partial sync results.
 * Lists which operations succeeded and failed.
 */
@Composable
fun PartialSyncResultDialog(
    result: SyncResult.PartialSuccess,
    onRetryFailed: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                    .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.ModernDialog.padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = EInkTheme.ModernDialog.titleColor,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "Partial Sync",
                        fontSize = EInkTheme.ModernDialog.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.ModernDialog.titleColor
                    )
                }

                Spacer(modifier = Modifier.height(EInkTheme.ModernDialog.contentSpacing))

                Divider(
                    color = EInkTheme.Divider.color,
                    thickness = EInkTheme.Divider.thickness
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Results summary
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Success count
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Synced successfully:",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.primary
                        )
                        Text(
                            text = "${result.successCount}",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.Colors.primary
                        )
                    }

                    // Failure count
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Failed:",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.primary
                        )
                        Text(
                            text = "${result.failureCount}",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.Colors.primary
                        )
                    }

                    // Error details
                    if (result.errors.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Errors:",
                            fontSize = EInkTheme.Typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.Colors.primary
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(EInkTheme.Colors.offWhite, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
                                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.borderStrong, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            result.errors.take(5).forEach { error ->
                                Text(
                                    text = "• $error",
                                    fontSize = EInkTheme.Typography.bodySmall,
                                    color = EInkTheme.Colors.darkGray
                                )
                            }
                            if (result.errors.size > 5) {
                                Text(
                                    text = "... and ${result.errors.size - 5} more",
                                    fontSize = EInkTheme.Typography.bodySmall,
                                    color = EInkTheme.Colors.gray
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(EInkTheme.ModernDialog.contentSpacing))

                // Action buttons - 40% / 20% / 40% layout
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EinkSecondaryButton(
                        text = "Close",
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.4f)
                    )

                    Spacer(modifier = Modifier.weight(0.2f))

                    if (onRetryFailed != null && result.failureCount > 0) {
                        ActionButton(
                            text = "Retry Failed",
                            onClick = {
                                onRetryFailed()
                                onDismiss()
                            },
                            modifier = Modifier.weight(0.4f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(0.4f))
                    }
                }
            }
        }
    }
}

/**
 * Simple offline notification dialog.
 */
@Composable
fun OfflineModeDialog(
    pendingChanges: Int,
    onDismiss: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                    .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.ModernDialog.padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = EInkTheme.ModernDialog.titleColor,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "Offline Mode",
                        fontSize = EInkTheme.ModernDialog.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.ModernDialog.titleColor
                    )
                }

                Spacer(modifier = Modifier.height(EInkTheme.ModernDialog.contentSpacing))

                Text(
                    text = "You're currently offline. Your changes are saved locally and will sync when you reconnect.",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary
                )

                if (pendingChanges > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$pendingChanges change(s) pending sync",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.primary
                    )
                }

                Spacer(modifier = Modifier.height(EInkTheme.ModernDialog.contentSpacing))

                ActionButton(
                    text = "Close",
                    onClick = onDismiss
                )
            }
        }
    }
}

// =============================================================================
// Helper Functions
// =============================================================================

private data class ErrorInfo(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val suggestion: String
)

private fun getErrorInfo(errorType: SyncErrorType): ErrorInfo {
    return when (errorType) {
        SyncErrorType.NETWORK -> ErrorInfo(
            icon = Icons.Filled.CloudOff,
            title = "Network Error",
            description = "Unable to connect to your calendar service. Please check your internet connection.",
            suggestion = "Make sure you have a stable internet connection and try again."
        )
        SyncErrorType.AUTHENTICATION -> ErrorInfo(
            icon = Icons.Filled.Lock,
            title = "Authentication Required",
            description = "Your calendar sign-in has expired or is invalid.",
            suggestion = "Please sign in again to continue syncing."
        )
        SyncErrorType.PERMISSION -> ErrorInfo(
            icon = Icons.Filled.Lock,
            title = "Permission Denied",
            description = "You don't have permission to access this calendar.",
            suggestion = "Check your calendar sharing settings or select a different calendar."
        )
        SyncErrorType.QUOTA -> ErrorInfo(
            icon = Icons.Filled.Speed,
            title = "Rate Limit Exceeded",
            description = "Too many requests to the calendar service. Please wait before trying again.",
            suggestion = "Wait a few minutes and try syncing again."
        )
        SyncErrorType.CONFLICT -> ErrorInfo(
            icon = Icons.Filled.Warning,
            title = "Sync Conflict",
            description = "This event was modified both locally and on your cloud calendar.",
            suggestion = "Review the changes and choose which version to keep."
        )
        SyncErrorType.SERVER -> ErrorInfo(
            icon = Icons.Filled.Error,
            title = "Server Error",
            description = "The calendar service is temporarily unavailable.",
            suggestion = "Try Again later. If the problem persists, check the service status."
        )
        SyncErrorType.CLIENT -> ErrorInfo(
            icon = Icons.Filled.Error,
            title = "Invalid Data",
            description = "The event data is invalid and cannot be synced.",
            suggestion = "Check the event details and make sure all required fields are filled."
        )
        SyncErrorType.NOT_FOUND -> ErrorInfo(
            icon = Icons.Filled.Error,
            title = "Calendar Removed",
            description = "One of your selected calendars no longer exists in Google Calendar.",
            suggestion = "Refresh your calendar list or re-enable the calendars you still want to sync."
        )
        SyncErrorType.UNKNOWN -> ErrorInfo(
            icon = Icons.Filled.Error,
            title = "Sync Error",
            description = "An unexpected error occurred during sync.",
            suggestion = "Try Again. If the problem persists, check the app logs."
        )
    }
}
