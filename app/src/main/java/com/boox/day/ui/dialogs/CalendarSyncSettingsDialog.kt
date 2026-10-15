package com.boox.day.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.boox.day.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.model.AccountType
import com.boox.day.sync.SyncResult
import com.boox.day.ui.components.ScrollableWithIndicator
import com.boox.day.sync.MultiAccountSyncViewModel
import com.boox.day.ui.messages.AiFeedbackMessages
import android.util.Log
import com.boox.day.ui.components.AccountCalendarList
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * Tab options for the sync settings dialog.
 */
private enum class SyncSettingsTab(val label: String) {
    GOOGLE("Google"),
    OUTLOOK("Outlook")
}

/**
 * Multi-provider calendar sync settings dialog.
 * Supports Google and Outlook accounts with per-account calendar selection.
 *
 * E-Ink optimized:
 * - Uses Popup instead of Dialog (no scrim)
 * - No ripple effects
 * - High contrast styling
 * - 48dp minimum touch targets
 *
 * @param viewModel The multi-account sync ViewModel
 * @param onDismiss Callback when dialog should close
 * @param onGoogleSignIn Callback to initiate Google sign-in with account type
 * @param onOutlookSignIn Callback to initiate Outlook sign-in with account type
 * @param autoSyncEnabled Whether background auto-sync is enabled
 * @param syncFrequency Current sync frequency setting
 * @param onAutoSyncChanged Callback when auto-sync toggle changes
 * @param onSyncFrequencyChanged Callback when frequency selection changes
 * @param onFirstAccountAdded Callback when the first account is added (0 → 1+) - used to switch to hybrid mode
 * @param onClearDataAndStandalone Callback when user chooses to clear synced data and return to standalone
 * @param onKeepDataAndHybrid Callback when user chooses to keep synced data and stay in hybrid mode
 * @param syncedEventCount Number of synced events (for display in clear data popup)
 * @param modifier Modifier for the dialog
 */
@Composable
fun CalendarSyncSettingsDialog(
    viewModel: MultiAccountSyncViewModel,
    onDismiss: () -> Unit,
    onGoogleSignIn: (AccountType) -> Unit,
    onOutlookSignIn: (AccountType) -> Unit,
    onFirstAccountAdded: () -> Unit = {},
    onClearDataAndStandalone: () -> Unit = {},
    onKeepDataAndHybrid: () -> Unit = {},
    syncedEventCount: Int = 0,
    // Legacy callback - kept for backward compatibility
    onAllAccountsRemoved: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    // Smart tab default: if Google has no accounts but Outlook does, start on Outlook tab.
    // Only computed once on dialog open — doesn't reset when user manually switches tabs.
    var selectedTab by remember {
        val default = if (viewModel.uiState.value.googleAccounts.isEmpty() &&
            viewModel.uiState.value.outlookAccounts.isNotEmpty()) {
            SyncSettingsTab.OUTLOOK
        } else {
            SyncSettingsTab.GOOGLE
        }
        mutableStateOf(default)
    }

    // Track previous account count to detect when all accounts are removed
    var previousAccountCount by remember { mutableStateOf<Int?>(null) }

    // State for showing the "Clear synced data?" popup
    var showClearDataPopup by remember { mutableStateOf(false) }

    // ═══════════════════════════════════════════════════════════════════════════
    // SUCCESS BANNER STATE (Option A + B)
    // Track sync completion for showing success banner and transforming button
    // ═══════════════════════════════════════════════════════════════════════════
    var showSuccessBanner by remember { mutableStateOf(false) }
    var wasSyncing by remember { mutableStateOf(false) }
    var successBannerSubtext by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf(AiFeedbackMessages.getRandomSyncCompleteMessage()) }

    // Detect sync completion (was syncing → not syncing)
    LaunchedEffect(uiState.isSyncing) {
        if (wasSyncing && !uiState.isSyncing) {
            // Calculate meaningful counts from sync results
            val successResults = uiState.syncResults.values.filterIsInstance<SyncResult.Success>()
            val totalNew = successResults.sumOf { it.newEvents }
            val totalUpdated = successResults.sumOf { it.updatedEvents }

            successBannerSubtext = when {
                totalNew > 0 && totalUpdated > 0 -> "$totalNew new, $totalUpdated updated"
                totalNew > 0 -> "$totalNew new events"
                totalUpdated > 0 -> "$totalUpdated events updated"
                else -> "Events up to date"
            }

            successMessage = AiFeedbackMessages.getRandomSyncCompleteMessage()
            showSuccessBanner = true
            Log.i("CalendarSyncSettings", "Sync completed - showing success banner ($totalNew new, $totalUpdated updated)")

            // Auto-hide banner after 20 seconds
            delay(20000)
            showSuccessBanner = false
        }
        wasSyncing = uiState.isSyncing
    }

    // Load accounts when dialog opens
    LaunchedEffect(Unit) {
        viewModel.loadAccounts()
    }

    // Detect when account count changes to trigger mode switches
    LaunchedEffect(uiState.accountCount) {
        val current = uiState.accountCount
        val previous = previousAccountCount

        // Only trigger callbacks after initial load (previous != null)
        if (previous != null) {
            // First account added (0 → 1+): switch to HYBRID mode
            if (previous == 0 && current > 0) {
                Log.i("CalendarSyncSettings", "First account added (was $previous, now $current) - triggering hybrid mode switch")
                onFirstAccountAdded()
            }
            // All accounts removed (>0 → 0): show clear data popup
            else if (previous > 0 && current == 0) {
                Log.i("CalendarSyncSettings", "All accounts disconnected (was $previous, now $current) - showing clear data popup")
                showClearDataPopup = true
            }
        }

        previousAccountCount = current
    }

    // Show "Clear synced data?" popup when last account is disconnected
    if (showClearDataPopup) {
        ClearSyncedDataPopup(
            syncedEventCount = syncedEventCount,
            onChoice = { choice ->
                showClearDataPopup = false
                when (choice) {
                    ClearSyncedDataChoice.CLEAR_AND_STANDALONE -> {
                        Log.i("CalendarSyncSettings", "User chose: Clear data and return to Standalone")
                        onClearDataAndStandalone()
                        onDismiss() // Close main dialog - user switched to standalone
                    }
                    ClearSyncedDataChoice.KEEP_AND_HYBRID -> {
                        Log.i("CalendarSyncSettings", "User chose: Keep data and stay in Hybrid")
                        onKeepDataAndHybrid()
                        onDismiss() // Close main dialog - decision complete
                    }
                    ClearSyncedDataChoice.CANCEL -> {
                        Log.i("CalendarSyncSettings", "User cancelled clear data decision")
                        // Do nothing - user cancelled, keep dialog open
                    }
                }
            }
        )
    }

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
                modifier = modifier
                    .fillMaxWidth(EInkTheme.PanelDialog.widthFraction)  // Oct 2026: was 0.78f
                    .fillMaxHeight(EInkTheme.PanelDialog.heightFraction)  // was 0.72f
                    .background(
                        EInkTheme.PopupOverlay.background,
                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                    .border(
                        EInkTheme.PopupOverlay.borderWidth,
                        EInkTheme.PopupOverlay.borderColor,
                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                    .padding(EInkTheme.PopupOverlay.padding)
            ) {
                // Header
                DialogHeader(
                    onDismiss = onDismiss
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

                // Tab selector
                TabSelector(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

                // ═══════════════════════════════════════════════════════════════
                // SUCCESS BANNER - Fixed height to prevent layout shifting
                // Shows after sync completes with fun message + Done button
                // ═══════════════════════════════════════════════════════════════
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp), // Fixed height - always reserved
                    contentAlignment = Alignment.Center
                ) {
                    if (showSuccessBanner) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = EInkTheme.Global.paddingSmall, vertical = 8.dp)
                                .background(
                                    EInkTheme.Colors.offWhite,
                                    RoundedCornerShape(EInkTheme.Global.cornerRadius)
                                )
                                .border(
                                    EInkTheme.Global.borderWidth,
                                    EInkTheme.Colors.primary,
                                    RoundedCornerShape(EInkTheme.Global.cornerRadius)
                                )
                                .padding(horizontal = EInkTheme.Global.paddingSmall, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EInkTheme.Colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = successMessage,
                                        fontSize = EInkTheme.Typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = EInkTheme.Colors.primary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = successBannerSubtext,
                                        fontSize = EInkTheme.Typography.labelSmall,
                                        color = EInkTheme.Colors.darkGray
                                    )
                                }
                            }
                            // Done button inside banner
                            Box(
                                modifier = Modifier
                                    .background(
                                        EInkTheme.Colors.primary,
                                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                                    )
                                    .noRippleClickable {
                                        showSuccessBanner = false
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Done",
                                    fontSize = EInkTheme.Typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = EInkTheme.Colors.white
                                )
                            }
                        }
                    }
                }

                // Tab content (scrollable with indicator)
                ScrollableWithIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                    ) {
                        when (selectedTab) {
                            SyncSettingsTab.GOOGLE -> GoogleTabContent(
                                accounts = uiState.googleAccounts,
                                isLoading = uiState.isLoading,
                                error = uiState.error,
                                syncResults = uiState.syncResults,
                                onSignIn = onGoogleSignIn,
                                onRemoveAccount = { viewModel.removeAccount(it) },
                                onToggleCalendar = { accountId, calendarId ->
                                    viewModel.toggleCalendarEnabled(accountId, calendarId)
                                },
                                onSetDefaultCalendar = { accountId, calendarId ->
                                    viewModel.setDefaultCalendar(accountId, calendarId)
                                },
                                onRefreshCalendars = { viewModel.refreshCalendars(it) },
                                onToggleExpanded = { viewModel.toggleAccountExpanded(it) },
                                // Re-auth: trigger sign-in flow again for the account's type
                                onReauth = { accountId ->
                                    val account = uiState.googleAccounts.find { it.account.id == accountId }
                                    account?.let { onGoogleSignIn(it.account.accountType) }
                                }
                            )
                            SyncSettingsTab.OUTLOOK -> OutlookTabContent(
                                accounts = uiState.outlookAccounts,
                                isLoading = uiState.isLoading,
                                error = uiState.error,
                                syncResults = uiState.syncResults,
                                onSignIn = onOutlookSignIn,
                                onRemoveAccount = { viewModel.removeAccount(it) },
                                onToggleCalendar = { accountId, calendarId ->
                                    viewModel.toggleCalendarEnabled(accountId, calendarId)
                                },
                                onSetDefaultCalendar = { accountId, calendarId ->
                                    viewModel.setDefaultCalendar(accountId, calendarId)
                                },
                                onRefreshCalendars = { viewModel.refreshCalendars(it) },
                                onToggleExpanded = { viewModel.toggleAccountExpanded(it) },
                                // Re-auth: trigger sign-in flow again for the account's type
                                onReauth = { accountId ->
                                    val account = uiState.outlookAccounts.find { it.account.id == accountId }
                                    account?.let { onOutlookSignIn(it.account.accountType) }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))

                // Footer: Close button (left) + Sync All button (right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                ) {
                    // Close button (leftmost - escape action per Dialog Rulebook)
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(0.4f)
                            .height(EInkTheme.Global.touchTarget),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = EInkTheme.Button.textColor
                        ),
                        border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor),
                        shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    ) {
                        Text(
                            text = "Close",
                            fontSize = EInkTheme.Typography.button,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Sync All button (rightmost - primary action)
                    Box(modifier = Modifier.weight(0.6f)) {
                        SyncAllButton(
                            isSyncing = uiState.isSyncing,
                            syncProgress = uiState.syncProgress,
                            hasAccounts = uiState.accountCount > 0,
                            showDoneState = showSuccessBanner,
                            onSyncAll = { viewModel.syncAllAccounts() },
                            onDone = {
                                showSuccessBanner = false
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog header with title and close button.
 */
@Composable
private fun DialogHeader(
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
        ) {
            // E-ink: No spinner - just show refresh icon always
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.size(EInkTheme.Global.iconSize)
            )
            Text(
                text = "Calendar Sync Settings",
                fontSize = EInkTheme.PopupOverlay.titleFontSize,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.PopupOverlay.titleColor
            )
        }

        Box(
            modifier = Modifier
                .size(EInkTheme.Global.touchTarget)
                .noRippleClickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.size(EInkTheme.Global.iconSize)
            )
        }
    }
}

/**
 * Tab selector for Google/Outlook.
 * E-Ink optimized: icon-only buttons with inverted colors for selection.
 * Uses provider logos (ic_google_provider, ic_outlook_provider) that invert
 * from black to white when selected.
 */
@Composable
private fun TabSelector(
    selectedTab: SyncSettingsTab,
    onTabSelected: (SyncSettingsTab) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
    ) {
        SyncSettingsTab.entries.forEach { tab ->
            val isSelected = selectedTab == tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(EInkTheme.Global.touchTarget)
                    .background(
                        if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.background,
                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                    .border(
                        EInkTheme.Global.borderWidth,
                        EInkTheme.Colors.primary,
                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                    .noRippleClickable(onClick = { onTabSelected(tab) }),
                contentAlignment = Alignment.Center
            ) {
                // Provider wordmark logo - height constrained, width scales with aspect ratio
                // E-ink: black when unselected, white when selected (inverted)
                Icon(
                    painter = painterResource(
                        when (tab) {
                            SyncSettingsTab.GOOGLE -> R.drawable.ic_google_provider
                            SyncSettingsTab.OUTLOOK -> R.drawable.ic_outlook_provider
                        }
                    ),
                    contentDescription = tab.label,
                    modifier = Modifier.height(20.dp),
                    tint = if (isSelected) EInkTheme.Colors.white else EInkTheme.Colors.primary
                )
            }
        }
    }
}

/**
 * Google tab content: accounts list + add buttons.
 */
@Composable
private fun GoogleTabContent(
    accounts: List<MultiAccountSyncViewModel.AccountWithCalendars>,
    isLoading: Boolean,
    error: String?,
    syncResults: Map<String, com.boox.day.sync.SyncResult>,
    onSignIn: (AccountType) -> Unit,
    onRemoveAccount: (accountId: String) -> Unit,
    onToggleCalendar: (accountId: String, calendarId: String) -> Unit,
    onSetDefaultCalendar: (accountId: String, calendarId: String) -> Unit,
    onRefreshCalendars: (accountId: String) -> Unit,
    onToggleExpanded: (accountId: String) -> Unit,
    onReauth: (accountId: String) -> Unit
) {
    // Loading state
    if (isLoading && accounts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(EInkTheme.Global.padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
            ) {
                CircularProgressIndicator(
                    color = EInkTheme.Colors.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Loading accounts...",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.gray
                )
            }
        }
        return
    }

    // Error state
    error?.let { errorMsg ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(EInkTheme.Colors.offWhite)
                .padding(EInkTheme.Global.paddingMedium)
        ) {
            Text(
                text = errorMsg,
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.primary
            )
        }
    }

    // Accounts list
    if (accounts.isEmpty()) {
        EmptyStateMessage(
            message = "No Google accounts connected.\nAdd an account to sync your Google Calendar."
        )
    } else {
        accounts.forEach { accountWithCalendars ->
            // Get sync result for this account
            val syncResult = syncResults[accountWithCalendars.account.id]
            val lastSyncMessage = syncResult?.getDisplayMessage()

            AccountCalendarList(
                account = accountWithCalendars.account,
                calendars = accountWithCalendars.calendars,
                onDisconnectAccount = { onRemoveAccount(accountWithCalendars.account.id) },
                onToggleCalendar = { calendarId ->
                    onToggleCalendar(accountWithCalendars.account.id, calendarId)
                },
                onSetDefaultCalendar = { calendarId ->
                    onSetDefaultCalendar(accountWithCalendars.account.id, calendarId)
                },
                onRefreshCalendars = { onRefreshCalendars(accountWithCalendars.account.id) },
                isExpanded = accountWithCalendars.isExpanded,
                onToggleExpanded = { onToggleExpanded(accountWithCalendars.account.id) },
                isLoadingCalendars = accountWithCalendars.isLoadingCalendars,
                isSyncing = accountWithCalendars.isSyncing,
                syncProgress = accountWithCalendars.syncProgress,
                needsReauth = accountWithCalendars.needsReauth,
                error = accountWithCalendars.error,
                lastSyncMessage = lastSyncMessage,
                onReauth = { onReauth(accountWithCalendars.account.id) }
            )
        }
    }

    Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

    // Add account buttons - one account per TYPE (Gmail + Workspace can coexist)
    val hasPersonalAccount = accounts.any { it.account.accountType == AccountType.GOOGLE_PERSONAL }
    val hasWorkspaceAccount = accounts.any { it.account.accountType == AccountType.GOOGLE_WORKSPACE }

    AddAccountButtons(
        onAddPersonal = { onSignIn(AccountType.GOOGLE_PERSONAL) },
        onAddWorkspace = { onSignIn(AccountType.GOOGLE_WORKSPACE) },
        personalLabel = if (hasPersonalAccount) "Gmail Connected" else "Add Gmail Account",
        workspaceLabel = if (hasWorkspaceAccount) "Workspace Connected" else "Add Workspace Account",
        personalDescription = when {
            hasPersonalAccount -> "Account already connected"
            else -> "Regular Free Gmail Account"
        },
        workspaceDescription = when {
            hasWorkspaceAccount -> "Account already connected"
            else -> "Google Business Account"
        },
        personalEnabled = !hasPersonalAccount,
        workspaceEnabled = !hasWorkspaceAccount,
        // Visual distinction: connected type vs unavailable type
        personalConnected = hasPersonalAccount,
        workspaceConnected = hasWorkspaceAccount
    )
}

/**
 * Outlook tab content: accounts list + add buttons.
 */
@Composable
private fun OutlookTabContent(
    accounts: List<MultiAccountSyncViewModel.AccountWithCalendars>,
    isLoading: Boolean,
    error: String?,
    syncResults: Map<String, com.boox.day.sync.SyncResult>,
    onSignIn: (AccountType) -> Unit,
    onRemoveAccount: (accountId: String) -> Unit,
    onToggleCalendar: (accountId: String, calendarId: String) -> Unit,
    onSetDefaultCalendar: (accountId: String, calendarId: String) -> Unit,
    onRefreshCalendars: (accountId: String) -> Unit,
    onToggleExpanded: (accountId: String) -> Unit,
    onReauth: (accountId: String) -> Unit
) {
    // Loading state
    if (isLoading && accounts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(EInkTheme.Global.padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
            ) {
                CircularProgressIndicator(
                    color = EInkTheme.Colors.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Loading accounts...",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.gray
                )
            }
        }
        return
    }

    // Error state
    error?.let { errorMsg ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(EInkTheme.Colors.offWhite)
                .padding(EInkTheme.Global.paddingMedium)
        ) {
            Text(
                text = errorMsg,
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.primary
            )
        }
    }

    // Accounts list
    if (accounts.isEmpty()) {
        EmptyStateMessage(
            message = "No Outlook accounts connected.\nAdd an account to sync your Outlook Calendar."
        )
    } else {
        accounts.forEach { accountWithCalendars ->
            // Get sync result for this account
            val syncResult = syncResults[accountWithCalendars.account.id]
            val lastSyncMessage = syncResult?.getDisplayMessage()

            AccountCalendarList(
                account = accountWithCalendars.account,
                calendars = accountWithCalendars.calendars,
                onDisconnectAccount = { onRemoveAccount(accountWithCalendars.account.id) },
                onToggleCalendar = { calendarId ->
                    onToggleCalendar(accountWithCalendars.account.id, calendarId)
                },
                onSetDefaultCalendar = { calendarId ->
                    onSetDefaultCalendar(accountWithCalendars.account.id, calendarId)
                },
                onRefreshCalendars = { onRefreshCalendars(accountWithCalendars.account.id) },
                isExpanded = accountWithCalendars.isExpanded,
                onToggleExpanded = { onToggleExpanded(accountWithCalendars.account.id) },
                isLoadingCalendars = accountWithCalendars.isLoadingCalendars,
                isSyncing = accountWithCalendars.isSyncing,
                syncProgress = accountWithCalendars.syncProgress,
                needsReauth = accountWithCalendars.needsReauth,
                error = accountWithCalendars.error,
                lastSyncMessage = lastSyncMessage,
                onReauth = { onReauth(accountWithCalendars.account.id) }
            )
        }
    }

    Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

    // Add account buttons - one account per provider rule
    // When an account exists: connected type shows "Connected", other type is grayed out
    // Add account buttons - one account per TYPE (Personal + Work/School can coexist)
    val hasPersonalAccount = accounts.any { it.account.accountType == AccountType.OUTLOOK_PERSONAL }
    val hasWorkspaceAccount = accounts.any { it.account.accountType == AccountType.OUTLOOK_WORK_SCHOOL }

    AddAccountButtons(
        onAddPersonal = { onSignIn(AccountType.OUTLOOK_PERSONAL) },
        onAddWorkspace = { onSignIn(AccountType.OUTLOOK_WORK_SCHOOL) },
        personalLabel = if (hasPersonalAccount) "Outlook.com Connected" else "Add Outlook.com Account",
        workspaceLabel = if (hasWorkspaceAccount) "Work/School Connected" else "Add Work/School Account",
        personalDescription = when {
            hasPersonalAccount -> "Account already connected"
            else -> "Personal Microsoft Account"
        },
        workspaceDescription = when {
            hasWorkspaceAccount -> "Account already connected"
            else -> "Microsoft 365 Business"
        },
        personalEnabled = !hasPersonalAccount,
        workspaceEnabled = !hasWorkspaceAccount,
        // Visual distinction: connected type vs unavailable type
        personalConnected = hasPersonalAccount,
        workspaceConnected = hasWorkspaceAccount
    )
}

/**
 * Empty state message for when no accounts are connected.
 */
@Composable
private fun EmptyStateMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(EInkTheme.Global.padding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.gray,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Add account buttons (personal + workspace) with descriptions.
 * Shows three visual states:
 * - Connected: checkmark icon, primary border, disabled (account already linked)
 * - Unavailable: add icon, gray border, disabled (one account per provider rule)
 * - Available: add icon, primary border, enabled (can add this account type)
 */
@Composable
private fun AddAccountButtons(
    onAddPersonal: () -> Unit,
    onAddWorkspace: () -> Unit,
    personalLabel: String,
    workspaceLabel: String,
    personalDescription: String = "",
    workspaceDescription: String = "",
    personalEnabled: Boolean = true,
    workspaceEnabled: Boolean = true,
    personalConnected: Boolean = false,
    workspaceConnected: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
    ) {
        // Personal account column
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Connected state uses primary color for visual distinction
            val personalBorderColor = when {
                personalConnected -> EInkTheme.Colors.primary  // Connected - emphasis
                personalEnabled -> EInkTheme.Colors.primary    // Available
                else -> EInkTheme.Colors.gray                  // Unavailable - grayed out
            }
            val personalContentColor = when {
                personalConnected -> EInkTheme.Colors.primary  // Connected - emphasis
                personalEnabled -> EInkTheme.Colors.primary    // Available
                else -> EInkTheme.Colors.gray                  // Unavailable - grayed out
            }

            OutlinedButton(
                onClick = onAddPersonal,
                enabled = personalEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EInkTheme.Global.touchTarget),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = EInkTheme.Colors.primary,
                    disabledContentColor = personalContentColor
                ),
                border = BorderStroke(EInkTheme.Global.borderWidth, personalBorderColor),
                shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
            ) {
                Icon(
                    imageVector = if (personalConnected) Icons.Default.CheckCircle else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(EInkTheme.Global.spacingSmall))
                Text(
                    text = personalLabel,
                    fontSize = EInkTheme.Typography.bodySmall,
                    maxLines = 1
                )
            }
            if (personalDescription.isNotEmpty()) {
                Text(
                    text = personalDescription,
                    fontSize = 11.sp,
                    color = if (personalConnected) EInkTheme.Colors.darkGray else EInkTheme.Colors.gray,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Workspace account column
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Connected state uses primary color for visual distinction
            val workspaceBorderColor = when {
                workspaceConnected -> EInkTheme.Colors.primary  // Connected - emphasis
                workspaceEnabled -> EInkTheme.Colors.primary    // Available
                else -> EInkTheme.Colors.gray                   // Unavailable - grayed out
            }
            val workspaceContentColor = when {
                workspaceConnected -> EInkTheme.Colors.primary  // Connected - emphasis
                workspaceEnabled -> EInkTheme.Colors.primary    // Available
                else -> EInkTheme.Colors.gray                   // Unavailable - grayed out
            }

            OutlinedButton(
                onClick = onAddWorkspace,
                enabled = workspaceEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EInkTheme.Global.touchTarget),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = EInkTheme.Colors.primary,
                    disabledContentColor = workspaceContentColor
                ),
                border = BorderStroke(EInkTheme.Global.borderWidth, workspaceBorderColor),
                shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
            ) {
                Icon(
                    imageVector = if (workspaceConnected) Icons.Default.CheckCircle else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(EInkTheme.Global.spacingSmall))
                Text(
                    text = workspaceLabel,
                    fontSize = EInkTheme.Typography.bodySmall,
                    maxLines = 1
                )
            }
            if (workspaceDescription.isNotEmpty()) {
                Text(
                    text = workspaceDescription,
                    fontSize = 11.sp,
                    color = if (workspaceConnected) EInkTheme.Colors.darkGray else EInkTheme.Colors.gray,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * Sync All button.
 * Shows phase-aware text during sync (Fetching/Indexing), transforms to "Done - Close" after.
 */
@Composable
private fun SyncAllButton(
    isSyncing: Boolean,
    syncProgress: MultiAccountSyncViewModel.SyncProgress? = null,
    hasAccounts: Boolean,
    showDoneState: Boolean = false,
    onSyncAll: () -> Unit,
    onDone: () -> Unit = {}
) {
    Button(
        onClick = {
            if (showDoneState) {
                onDone()
            } else {
                onSyncAll()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.Global.touchTarget),
        enabled = showDoneState || (!isSyncing && hasAccounts),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = EInkTheme.Colors.primary,
            contentColor = EInkTheme.Colors.white,
            // E-ink: Keep same colors when disabled (syncing) - lightGray looks bad on Kaleido 3
            disabledBackgroundColor = EInkTheme.Colors.primary,
            disabledContentColor = EInkTheme.Colors.white
        ),
        shape = RoundedCornerShape(EInkTheme.Global.cornerRadius),
        elevation = ButtonDefaults.elevation(0.dp)
    ) {
        when {
            isSyncing -> {
                // Phase-aware text during sync (no spinner - causes ghosting on Kaleido 3)
                Text(
                    text = when (syncProgress?.phase) {
                        MultiAccountSyncViewModel.SyncPhase.INDEXING ->
                            "Indexing ${syncProgress.current} of ${syncProgress.total}..."
                        MultiAccountSyncViewModel.SyncPhase.FETCHING ->
                            "Fetching events..."
                        null -> "Syncing..."
                    },
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            showDoneState -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(EInkTheme.Global.spacingSmall))
                Text(
                    text = "Done - Close",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(EInkTheme.Global.spacingSmall))
                Text(
                    text = "Sync All Now",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
