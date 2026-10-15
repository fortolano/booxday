package com.boox.day.ui.components

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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boox.day.R
import com.boox.day.data.model.CalendarSelection
import com.boox.day.data.model.ConnectedAccount
import com.boox.day.data.model.ProviderId
import com.boox.day.ui.messages.AiFeedbackMessages
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * Displays a connected account with its calendars for the sync settings dialog.
 * Provides controls for:
 * - Disconnecting the account
 * - Selecting which calendars to sync
 * - Setting the default calendar for new events
 * - Refreshing the calendar list from the provider
 *
 * E-Ink optimized:
 * - No ripple effects
 * - 48dp minimum touch targets
 * - High contrast black/white styling
 * - Vector icons only
 *
 * @param account The connected account to display
 * @param calendars List of calendars for this account
 * @param onDisconnectAccount Callback when user taps disconnect
 * @param onToggleCalendar Callback when user toggles a calendar's sync status
 * @param onSetDefaultCalendar Callback when user sets a calendar as default
 * @param onRefreshCalendars Callback when user taps refresh
 * @param isExpanded Whether the calendar list is expanded
 * @param onToggleExpanded Callback when user taps to expand/collapse
 * @param isLoadingCalendars Whether calendars are currently being loaded
 * @param isSyncing Whether events are currently being synced for this account
 * @param needsReauth Whether the account needs re-authentication (token expired)
 * @param error Error message for this account, if any
 * @param lastSyncMessage Last sync result message for display
 * @param onReauth Callback when user taps to re-authenticate
 * @param modifier Modifier for the root element
 */
@Composable
fun AccountCalendarList(
    account: ConnectedAccount,
    calendars: List<CalendarSelection>,
    onDisconnectAccount: () -> Unit,
    onToggleCalendar: (calendarId: String) -> Unit,
    onSetDefaultCalendar: (calendarId: String) -> Unit,
    onRefreshCalendars: () -> Unit,
    isExpanded: Boolean = true,
    onToggleExpanded: () -> Unit = {},
    isLoadingCalendars: Boolean = false,
    isSyncing: Boolean = false,
    /** Current sync progress for this account (phase + counts) */
    syncProgress: com.boox.day.sync.MultiAccountSyncViewModel.SyncProgress? = null,
    needsReauth: Boolean = false,
    error: String? = null,
    lastSyncMessage: String? = null,
    onReauth: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    // Legacy parameter name for backward compatibility - maps to onDisconnectAccount
    onRemoveAccount: (() -> Unit)? = null
) {
    // Use legacy parameter if provided, otherwise use new parameter
    val handleDisconnect = onRemoveAccount ?: onDisconnectAccount
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = EInkTheme.Global.borderWidth,
                color = EInkTheme.Colors.lightGray,
                shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .background(
                color = EInkTheme.Colors.background,
                shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
    ) {
        // Account Header with calendar count for collapsed state
        AccountHeader(
            account = account,
            isExpanded = isExpanded,
            onToggleExpanded = onToggleExpanded,
            onDisconnectAccount = handleDisconnect,
            calendarCount = calendars.size,
            isSyncing = isSyncing,
            syncProgress = syncProgress,
            needsReauth = needsReauth,
            error = error,
            lastSyncMessage = lastSyncMessage,
            onReauth = onReauth
        )

        // Expanded content
        if (isExpanded) {
            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EInkTheme.Divider.thickness)
                    .background(EInkTheme.Colors.lightGray)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(EInkTheme.Global.paddingMedium),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
            ) {
                // Default calendar selector
                if (calendars.isNotEmpty()) {
                    DefaultCalendarSelector(
                        calendars = calendars,
                        onSetDefault = onSetDefaultCalendar,
                        onRefresh = onRefreshCalendars,
                        isLoading = isLoadingCalendars
                    )

                    Spacer(modifier = Modifier.height(4.dp))  // Tightened from spacingSmall

                    // Calendars to sync label
                    Text(
                        text = "Calendars to sync:",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.darkGray
                    )

                    // Calendar list
                    CalendarCheckboxList(
                        calendars = calendars,
                        onToggleCalendar = onToggleCalendar
                    )
                } else {
                    // Empty state
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isLoadingCalendars) "Loading calendars..." else "No calendars found",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.gray
                        )
                        if (!isLoadingCalendars) {
                            Box(
                                modifier = Modifier
                                    .sizeIn(minWidth = EInkTheme.Global.touchTarget, minHeight = EInkTheme.Global.touchTarget)
                                    .noRippleClickable(onClick = onRefreshCalendars),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh calendars",
                                    tint = EInkTheme.Colors.primary,
                                    modifier = Modifier.size(EInkTheme.Global.iconSize)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Account header with email, type badge, expand/collapse button, and disconnect button.
 * Uses explicit "Show/Hide" button instead of ambiguous chevron for better discoverability.
 * Shows sync status when events are being fetched.
 * Shows warning icon and re-auth button when authentication expires.
 * Shows error message when sync fails.
 */
@Composable
private fun AccountHeader(
    account: ConnectedAccount,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDisconnectAccount: () -> Unit,
    calendarCount: Int = 0,
    isSyncing: Boolean = false,
    syncProgress: com.boox.day.sync.MultiAccountSyncViewModel.SyncProgress? = null,
    needsReauth: Boolean = false,
    error: String? = null,
    lastSyncMessage: String? = null,
    onReauth: (() -> Unit)? = null
) {
    var showDisconnectConfirmation by remember { mutableStateOf(false) }

    // Fun messages - stored as state so they can be refreshed each sync cycle
    var syncingFunMessage by remember { mutableStateOf(AiFeedbackMessages.getRandomSyncingMessage()) }
    var syncCompleteFunMessage by remember { mutableStateOf(AiFeedbackMessages.getRandomSyncCompleteMessage()) }

    // Track sync completion to show success message for 2 seconds
    var showSyncComplete by remember { mutableStateOf(false) }
    var wasSyncing by remember { mutableStateOf(false) }

    // Detect sync state changes and rotate messages every 4.5 seconds
    // BUG FIX: Previously, LaunchedEffect only fired once on state change, picking one
    // message for the entire sync duration. Now uses a while loop that auto-cancels
    // when isSyncing changes (LaunchedEffect re-launches on key change).
    LaunchedEffect(isSyncing) {
        if (!wasSyncing && isSyncing) {
            // Sync just started - pick first message, then rotate
            syncingFunMessage = AiFeedbackMessages.getRandomSyncingMessage()
            while (true) {
                delay(4500)
                syncingFunMessage = AiFeedbackMessages.getRandomSyncingMessage()
            }
        } else if (wasSyncing && !isSyncing) {
            // Sync just completed - pick a new complete message and show for 3 seconds
            syncCompleteFunMessage = AiFeedbackMessages.getRandomSyncCompleteMessage()
            showSyncComplete = true
            delay(3000)
            showSyncComplete = false
        }
        wasSyncing = isSyncing
    }

    // Wrap in Column to allow full-width syncing message below the header row
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EInkTheme.Global.paddingMedium, vertical = EInkTheme.Global.paddingSmall)
    ) {
        // Main header row: icon + info + buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = EInkTheme.Global.touchTarget),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Provider icon + email + type badge
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Provider icon (Google Calendar or Outlook) with optional warning overlay
                Box(contentAlignment = Alignment.BottomEnd) {
                    Icon(
                        painter = painterResource(
                            when (account.providerId) {
                                ProviderId.GOOGLE -> R.drawable.ic_google_calendar
                                ProviderId.OUTLOOK -> R.drawable.ic_outlook
                            }
                        ),
                        contentDescription = "${account.providerId.displayName} account",
                        modifier = Modifier.size(24.dp),
                        tint = androidx.compose.ui.graphics.Color.Unspecified // Preserve original icon colors
                    )
                    // Warning badge when re-auth needed
                    if (needsReauth) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Re-authentication required",
                            modifier = Modifier.size(14.dp),
                            tint = EInkTheme.Colors.primary
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.email,
                        fontSize = EInkTheme.Typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.primary,
                        maxLines = 1
                    )
                    // Account type + status indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = account.accountType.displayName,
                            fontSize = EInkTheme.Typography.bodySmall,
                            color = EInkTheme.Colors.darkGray
                        )
                        // Show status indicators (not the fun message - that goes below)
                        when {
                            isSyncing -> {
                                androidx.compose.material.CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = EInkTheme.Colors.primary,
                                    strokeWidth = 1.5.dp
                                )
                            }
                            needsReauth -> {
                                Text(
                                    text = "• Sign-in expired",
                                    fontSize = EInkTheme.Typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = EInkTheme.Colors.primary
                                )
                            }
                            error != null -> {
                                Text(
                                    text = "• $error",
                                    fontSize = EInkTheme.Typography.bodySmall,
                                    color = EInkTheme.Colors.primary,
                                    maxLines = 1
                                )
                            }
                            lastSyncMessage != null && isExpanded -> {
                                Text(
                                    text = "• $lastSyncMessage",
                                    fontSize = EInkTheme.Typography.bodySmall,
                                    color = EInkTheme.Colors.gray,
                                    maxLines = 1
                                )
                            }
                            !isExpanded && calendarCount > 0 -> {
                                Text(
                                    text = "• $calendarCount calendar${if (calendarCount > 1) "s" else ""}",
                                    fontSize = EInkTheme.Typography.bodySmall,
                                    color = EInkTheme.Colors.gray
                                )
                            }
                        }
                    }
                }
            }

            // Spacer for visual separation
            Spacer(modifier = Modifier.width(EInkTheme.Global.spacing))

        // Re-auth button (when needed) OR Show/Hide button
        if (needsReauth && onReauth != null) {
            // Re-authenticate button - emphasized with filled background
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .background(
                        EInkTheme.Colors.primary,
                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                    .noRippleClickable(onClick = onReauth)
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = EInkTheme.Colors.white,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Re-auth",
                        fontSize = EInkTheme.Typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.white
                    )
                }
            }
        } else {
            // Show/Hide button - explicit and discoverable (fixed width to prevent layout shift)
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .border(
                        width = EInkTheme.Global.borderWidth,
                        color = EInkTheme.Colors.darkGray,
                        shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                    .noRippleClickable(onClick = onToggleExpanded)
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = EInkTheme.Colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isExpanded) "Hide" else "Show",
                        fontSize = EInkTheme.Typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        // Disconnect button - same style and width as Show/Hide for symmetry
        Box(
            modifier = Modifier
                .width(88.dp)
                .border(
                    width = EInkTheme.Global.borderWidth,
                    color = EInkTheme.Colors.darkGray,
                    shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
                )
                .noRippleClickable(onClick = { showDisconnectConfirmation = true })
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Disconnect",
                fontSize = EInkTheme.Typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )
        }
        } // End main header Row

        // Fixed-height message area - prevents vertical shifting when messages appear/disappear
        // Always reserves space; content shown only when syncing or just completed
        // Height increased to 48dp to fit fun message + phase progress line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)  // Fixed height for message + phase row
                .padding(top = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isSyncing || showSyncComplete) {
                if (isSyncing) {
                    // Syncing state - fun message + phase progress
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = EInkTheme.Colors.darkGray,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(top = 2.dp)
                        )
                        Column {
                            // Line 1: Rotating fun message
                            Text(
                                text = syncingFunMessage,
                                fontSize = EInkTheme.Typography.bodyMedium,
                                color = EInkTheme.Colors.darkGray,
                                maxLines = 1
                            )
                            // Line 2: Phase progress
                            Text(
                                text = when (syncProgress?.phase) {
                                    com.boox.day.sync.MultiAccountSyncViewModel.SyncPhase.INDEXING ->
                                        "Indexing ${syncProgress.current} of ${syncProgress.total} events"
                                    com.boox.day.sync.MultiAccountSyncViewModel.SyncPhase.FETCHING ->
                                        "Fetching events..."
                                    null -> "Fetching events..."
                                },
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.gray,
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    // Sync complete state - checkmark icon
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EInkTheme.Colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = syncCompleteFunMessage,
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.primary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    } // End outer Column

    // Disconnect confirmation dialog
    if (showDisconnectConfirmation) {
        DisconnectAccountConfirmation(
            email = account.email,
            onConfirm = {
                showDisconnectConfirmation = false
                onDisconnectAccount()
            },
            onDismiss = { showDisconnectConfirmation = false }
        )
    }
}

/**
 * Confirmation dialog for disconnecting an account.
 * Explains that events will remain due to potential links with Boox Notes and Tasks.
 */
@Composable
private fun DisconnectAccountConfirmation(
    email: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .background(
                    EInkTheme.Colors.background,
                    RoundedCornerShape(EInkTheme.Global.cornerRadius)
                )
                .border(
                    EInkTheme.Global.borderWidth,
                    EInkTheme.Colors.primary,
                    RoundedCornerShape(EInkTheme.Global.cornerRadius)
                )
                .padding(EInkTheme.Global.padding)
        ) {
            // Title
            Text(
                text = "Disconnect Account?",
                fontSize = EInkTheme.Typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary
            )

            Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

            // Email being disconnected
            Text(
                text = email,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.darkGray
            )

            Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

            // Explanation
            Text(
                text = "This account will be disconnected, but synced events will remain in your calendar (read-only). Events are preserved because they may be linked to your Boox Notes and Tasks.",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

            Text(
                text = "To also clear synced events, go to Settings → Data & Backup → Maintenance.",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
            ) {
                // Cancel button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(EInkTheme.Global.touchTarget)
                        .border(
                            EInkTheme.Global.borderWidth,
                            EInkTheme.Colors.darkGray,
                            RoundedCornerShape(EInkTheme.Global.cornerRadius)
                        )
                        .noRippleClickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.primary
                    )
                }

                // Disconnect button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(EInkTheme.Global.touchTarget)
                        .background(
                            EInkTheme.Colors.primary,
                            RoundedCornerShape(EInkTheme.Global.cornerRadius)
                        )
                        .noRippleClickable(onClick = onConfirm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Disconnect",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.white
                    )
                }
            }
        }
    }
}

/**
 * Dropdown to select the default calendar for new events, with refresh button.
 */
@Composable
private fun DefaultCalendarSelector(
    calendars: List<CalendarSelection>,
    onSetDefault: (calendarId: String) -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean
) {
    var showDropdown by remember { mutableStateOf(false) }
    val defaultCalendar = calendars.find { it.isDefaultForAccount }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Default calendar for new events:",
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.darkGray
        )

        Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dropdown button
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EInkTheme.Global.touchTarget)
                        .border(
                            width = EInkTheme.Global.borderWidth,
                            color = EInkTheme.Colors.darkGray,
                            shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
                        )
                        .noRippleClickable(onClick = { showDropdown = true })
                        .padding(horizontal = EInkTheme.Global.paddingMedium),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = defaultCalendar?.calendarName ?: "Select calendar",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = if (defaultCalendar != null) EInkTheme.Colors.primary else EInkTheme.Colors.gray,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select",
                        tint = EInkTheme.Colors.darkGray,
                        modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
                    )
                }

                // Dropdown menu
                DropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false },
                    modifier = Modifier
                        .background(EInkTheme.Colors.background)
                        .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary)
                ) {
                    calendars.forEach { calendar ->
                        DropdownMenuItem(
                            onClick = {
                                onSetDefault(calendar.calendarId)
                                showDropdown = false
                            },
                            modifier = Modifier.height(EInkTheme.Global.touchTarget)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = calendar.calendarName,
                                    fontSize = EInkTheme.Typography.bodyMedium,
                                    color = EInkTheme.Colors.primary
                                )
                                if (calendar.isDefaultForAccount) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Current default",
                                        tint = EInkTheme.Colors.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Refresh button
            Box(
                modifier = Modifier
                    .size(EInkTheme.Global.touchTarget)
                    .border(
                        width = EInkTheme.Global.borderWidth,
                        color = EInkTheme.Colors.darkGray,
                        shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                    .noRippleClickable(enabled = !isLoading, onClick = onRefresh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh calendars",
                    tint = if (isLoading) EInkTheme.Colors.gray else EInkTheme.Colors.primary,
                    modifier = Modifier.size(EInkTheme.Global.iconSize)
                )
            }
        }
    }
}

/**
 * List of calendars with checkboxes to enable/disable sync.
 * Default calendar is marked with a star.
 */
@Composable
private fun CalendarCheckboxList(
    calendars: List<CalendarSelection>,
    onToggleCalendar: (calendarId: String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        calendars.forEach { calendar ->
            CalendarCheckboxRow(
                calendar = calendar,
                onToggle = { onToggleCalendar(calendar.calendarId) }
            )
        }
    }
}

/**
 * Single calendar row with checkbox and default indicator.
 */
@Composable
private fun CalendarCheckboxRow(
    calendar: CalendarSelection,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 40.dp)  // Reduced from touchTarget (48dp) - checkbox provides its own touch area
            .noRippleClickable(onClick = onToggle)
            .padding(vertical = 2.dp),  // Tightened from spacingSmall (8dp)
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = calendar.isFetchEnabled,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = EInkTheme.Colors.primary,
                    uncheckedColor = EInkTheme.Colors.darkGray,
                    checkmarkColor = EInkTheme.Colors.white
                )
            )

            Text(
                text = calendar.calendarName,
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.primary,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }

        // Default calendar indicator (star)
        if (calendar.isDefaultForAccount) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Default calendar",
                tint = EInkTheme.Colors.primary,
                modifier = Modifier
                    .size(EInkTheme.Global.iconSizeSmall)
                    .padding(end = EInkTheme.Global.spacingSmall)
            )
        }
    }
}
