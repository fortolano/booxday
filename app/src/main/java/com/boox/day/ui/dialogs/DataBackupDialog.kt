package com.boox.day.ui.dialogs

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.Divider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.R
import com.boox.day.data.StorageCalculator
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.model.AccountType
import com.boox.day.sync.BackupCloud
import com.boox.day.sync.BackupStateDetector
import com.boox.day.sync.CloudBackupService
import com.boox.day.sync.DriveBackupService
import com.boox.day.sync.OneDriveAccount
import com.boox.day.sync.OneDriveBackupService
import com.boox.day.sync.OneDriveSignIn
import com.boox.day.sync.outlook.OutlookAuthManager
import com.boox.day.sync.ExportService
import com.boox.day.sync.GoogleAuthManager
import com.boox.day.sync.LocalBackupService
import com.boox.day.sync.PostRestore
import com.boox.day.sync.SignInErrorText
import com.boox.day.ui.components.CloudBackupCard
import com.boox.day.ui.components.EInkToggle
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.components.ExistingBackupDetectedCard
import com.boox.day.ui.components.LocalBackupCard
import com.boox.day.ui.components.keyboardHeight
import com.boox.day.ui.components.LocalRestoreConfirmationPopup
import com.boox.day.ui.components.RestoreCard
import com.boox.day.ui.components.ScrollableWithIndicator
import com.boox.day.ui.components.SettingCard
import com.boox.day.ui.components.StorageInfoCard
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.launch

/**
 * Data & Backup Dialog - Storage, backup, restore, and maintenance.
 *
 * Contents (2 tabs):
 * - Tab 1 "Storage & Backup": StorageInfoCard, CloudBackupCard, RestoreCard
 * - Tab 2 "Maintenance": Clear Synced Data, Wipe All Data (with confirmations)
 *
 * E-Ink optimized: Uses Popup (not Dialog), noRippleClickable, EInkTheme constants.
 */

private enum class DataBackupTab {
    BackupRestore,
    Maintenance
}

/**
 * Phases for the intent-based backup workflow.
 * Users progress through: Intent → Destination/Source → Action
 */
sealed class BackupDialogPhase {
    /** Initial screen: "What would you like to do?" */
    object IntentSelection : BackupDialogPhase()

    /** Backup flow: "Where do you want to save?" */
    object BackupDestination : BackupDialogPhase()

    /** Restore flow: "Where is your backup?" */
    object RestoreSource : BackupDialogPhase()

    /** Cloud backup configuration and execution, for Google Drive or OneDrive (Nov 2026) */
    data class CloudBackupFlow(val cloud: BackupCloud) : BackupDialogPhase()

    /** Local backup execution (SAF file picker) */
    object LocalBackupFlow : BackupDialogPhase()

    /** Cloud restore: list backups, select, confirm */
    data class CloudRestoreFlow(val cloud: BackupCloud) : BackupDialogPhase()

    /** Which Microsoft account keeps the OneDrive backups (owner decision 86) */
    data class MicrosoftAccountChoice(val forRestore: Boolean) : BackupDialogPhase()

    /** Local restore: file picker, preview, confirm */
    object LocalRestoreFlow : BackupDialogPhase()

    /** Processing state with message */
    data class Processing(val message: String) : BackupDialogPhase()

    /** Success state with title and optional detail items */
    data class Success(
        val title: String,
        val details: List<String> = emptyList()
    ) : BackupDialogPhase()

    /** Error state with message */
    data class Error(val message: String) : BackupDialogPhase()
}

@Composable
fun DataBackupDialog(
    onDismiss: () -> Unit,
    // Danger Zone callbacks
    onClearSyncedDataOnly: (() -> Unit)? = null,
    onClearSyncedDataAndResync: (() -> Unit)? = null,
    onWipeAllData: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(DataBackupTab.BackupRestore) }
    var showWipeConfirmation by remember { mutableStateOf(false) }
    var showClearSyncedConfirmation by remember { mutableStateOf(false) }

    // Phase-based navigation state for intent workflow
    var currentPhase by remember { mutableStateOf<BackupDialogPhase>(BackupDialogPhase.IntentSelection) }
    val phaseHistory = remember { mutableStateListOf<BackupDialogPhase>() }

    // Navigation helpers
    fun navigateTo(phase: BackupDialogPhase) {
        phaseHistory.add(currentPhase)
        currentPhase = phase
    }

    fun navigateBack() {
        if (phaseHistory.isNotEmpty()) {
            currentPhase = phaseHistory.removeAt(phaseHistory.lastIndex)
        }
    }

    fun resetToIntent() {
        phaseHistory.clear()
        currentPhase = BackupDialogPhase.IntentSelection
    }

    // Storage state
    var storageInfo by remember { mutableStateOf<StorageCalculator.StorageInfo?>(null) }
    var isLoadingStorage by remember { mutableStateOf(true) }

    // Google Auth Manager, Drive Backup Service and Export Service
    val authManager = remember { GoogleAuthManager(context) }
    val driveBackupService = remember { DriveBackupService(context) }
    val exportService = remember { ExportService(context) }

    // Cloud backup state
    var isSignedIn by remember { mutableStateOf(authManager.isSignedIn()) }
    var userEmail by remember { mutableStateOf(authManager.getUserEmail()) }
    // Why the last Google sign-in failed, shown in the Google Drive card in place of "Not connected"
    var signInError by remember { mutableStateOf<String?>(null) }
    // One backup cloud at a time (owner decision 85): Google Drive, OneDrive or none
    var backupDestination by remember { mutableStateOf(authManager.getBackupDestination()) }
    var backupFrequency by remember { mutableStateOf(authManager.getBackupFrequency()) }
    var lastBackupTime by remember { mutableStateOf(authManager.getLastBackupTime()) }
    var isBackupInProgress by remember { mutableStateOf(false) }
    var folderStatus by remember { mutableStateOf<String?>(null) }

    // OneDrive backup (owner decisions 85-88, Nov 2026)
    val oneDriveService = remember { OneDriveBackupService.create(context) }
    val outlookAuth = remember { OutlookAuthManager(context.applicationContext) }
    var oneDriveAccount by remember { mutableStateOf(authManager.getOneDriveAccount()) }
    var oneDriveSignInError by remember { mutableStateOf<String?>(null) }
    var oneDriveLastBackupTime by remember { mutableStateOf(authManager.getLastBackupTime(BackupCloud.ONEDRIVE)) }
    var oneDriveStatus by remember { mutableStateOf<String?>(null) }
    var calendarMicrosoftAccounts by remember { mutableStateOf<List<OneDriveAccount>>(emptyList()) }
    var isConnectingOneDrive by remember { mutableStateOf(false) }

    fun serviceFor(cloud: BackupCloud): CloudBackupService =
        if (cloud == BackupCloud.GOOGLE_DRIVE) driveBackupService else oneDriveService

    fun setDestination(cloud: BackupCloud?) {
        authManager.setBackupDestination(cloud)
        backupDestination = cloud
    }

    // Restore state
    var availableBackups by remember { mutableStateOf<List<DriveBackupService.BackupInfo>>(emptyList()) }
    var listedCloud by remember { mutableStateOf<BackupCloud?>(null) }
    var isLoadingBackups by remember { mutableStateOf(false) }
    var isRestoreInProgress by remember { mutableStateOf(false) }
    var restoreStatus by remember { mutableStateOf<String?>(null) }

    // Smart restore detection state (Phase 0)
    val backupStateDetector = remember { BackupStateDetector(context, driveBackupService) }
    var detectedBackupState by remember { mutableStateOf<BackupStateDetector.BackupState?>(null) }
    var isDetectingState by remember { mutableStateOf(false) }
    var showOverwriteConfirmation by remember { mutableStateOf(false) }

    // Local backup state
    val localBackupService = remember { LocalBackupService(context) }
    var isLocalSaveInProgress by remember { mutableStateOf(false) }
    var isLocalRestoreInProgress by remember { mutableStateOf(false) }
    var localSaveStatus by remember { mutableStateOf<String?>(null) }
    var localRestoreStatus by remember { mutableStateOf<String?>(null) }
    // A device file or a downloaded cloud backup waiting for "Restore from Backup?" (owner decision 87)
    val pendingRestore = remember { PendingRestore() }
    DisposableEffect(Unit) { onDispose { pendingRestore.cancel() } }
    var showBackupFilePicker by remember { mutableStateOf(false) }
    var lastLocalBackupTime by remember { mutableStateOf(GlobalAppSettings.current.lastLocalBackupTime) }

    // Function to detect backup state
    fun detectBackupState() {
        coroutineScope.launch {
            isDetectingState = true
            detectedBackupState = backupStateDetector.detectState()
            isDetectingState = false
        }
    }

    // Function to load backups from Drive (defined before enableBackupAfterOverwrite which calls it).
    // Nov 2026: the list of the cloud being shown, loaded when its list opens; a late answer from
    // the other cloud is dropped
    fun loadBackups(cloud: BackupCloud) {
        listedCloud = cloud
        availableBackups = emptyList()
        restoreStatus = null
        coroutineScope.launch {
            isLoadingBackups = true
            val result = serviceFor(cloud).listBackups()
            if (listedCloud != cloud) return@launch
            when (result) {
                is DriveBackupService.ListResult.Success -> {
                    availableBackups = result.backups
                }
                is DriveBackupService.ListResult.Error -> {
                    restoreStatus = "Failed to load backups: ${result.message}"
                }
            }
            isLoadingBackups = false
        }
    }

    // A cloud's list is read each time it opens (also back from a cancelled restore)
    LaunchedEffect(currentPhase) {
        (currentPhase as? BackupDialogPhase.CloudRestoreFlow)?.let { loadBackups(it.cloud) }
    }

    // "Back Up Now" for either cloud
    fun backUpNow(cloud: BackupCloud) {
        fun status(text: String?) {
            if (cloud == BackupCloud.GOOGLE_DRIVE) folderStatus = text else oneDriveStatus = text
        }
        coroutineScope.launch {
            isBackupInProgress = true
            status("Creating backup...")
            // Only to the account on screen: Turn Off Backup or another account meanwhile stops it
            val account = authManager.backupAccountOf(cloud)

            when (val exportResult = exportService.createBackup()) {
                is ExportService.ExportResult.Success -> {
                    status("Uploading to ${cloud.label}...")

                    val stillWanted = { account != null && authManager.backupAccountOf(cloud) == account }
                    when (val uploadResult = serviceFor(cloud).uploadBackup(exportResult.backupFile, stillWanted)) {
                        is DriveBackupService.UploadResult.Success -> {
                            val now = System.currentTimeMillis()
                            if (cloud == BackupCloud.GOOGLE_DRIVE) lastBackupTime = now else oneDriveLastBackupTime = now
                            status("✓ Backup complete: ${uploadResult.fileName}")
                            currentPhase = BackupDialogPhase.Success(
                                title = if (exportResult.warnings.isEmpty()) "Backup Complete" else "Backup Complete (with notes)",
                                details = listOf("Saved to ${cloud.label}: ${uploadResult.fileName}") + exportResult.warnings
                            )
                        }
                        is DriveBackupService.UploadResult.Error -> {
                            status("Upload failed: ${uploadResult.message}")
                        }
                        // Turned off or another account: the user has left this screen already
                        is DriveBackupService.UploadResult.Stopped -> status(null)
                    }
                }
                is ExportService.ExportResult.Error -> {
                    status("Backup failed: ${exportResult.message}")
                }
            }

            isBackupInProgress = false
        }
    }

    // Microsoft accounts already in Calendar Accounts, offered first (owner decision 86)
    LaunchedEffect(currentPhase) {
        if (currentPhase is BackupDialogPhase.MicrosoftAccountChoice) {
            calendarMicrosoftAccounts = OneDriveSignIn.calendarAccounts(context.applicationContext, outlookAuth)
        }
    }

    // Signs in to Microsoft for OneDrive backup, then opens OneDrive's backup or restore screen
    fun connectOneDrive(forRestore: Boolean, signIn: suspend (android.app.Activity) -> Result<OneDriveAccount>) {
        val activity = context as? android.app.Activity ?: return
        if (isConnectingOneDrive) return
        coroutineScope.launch {
            isConnectingOneDrive = true
            oneDriveSignInError = null
            signIn(activity).fold(
                onSuccess = { account ->
                    authManager.setOneDriveAccount(account)
                    oneDriveAccount = account
                    oneDriveLastBackupTime = authManager.getLastBackupTime(BackupCloud.ONEDRIVE)
                    oneDriveStatus = "✓ Connected to OneDrive"
                    // Like Google Drive: automatic backup starts by itself only when no cloud is
                    // chosen yet and the folder was read and has no backups to restore first; never
                    // on the way to restoring. A list that couldn't be read is said, Auto Backup stays off
                    val lookup = if (!forRestore && backupDestination == null) oneDriveService.listBackups() else null
                    if (OneDriveSignIn.turnsOnAutoBackup(forRestore, backupDestination, lookup)) {
                        setDestination(BackupCloud.ONEDRIVE)
                    } else if (lookup is DriveBackupService.ListResult.Error) {
                        oneDriveStatus = lookup.message
                    }
                    // Only from the account choice: the user may have gone elsewhere meanwhile
                    if (currentPhase is BackupDialogPhase.MicrosoftAccountChoice) {
                        navigateBack()
                        navigateTo(
                            if (forRestore) BackupDialogPhase.CloudRestoreFlow(BackupCloud.ONEDRIVE)
                            else BackupDialogPhase.CloudBackupFlow(BackupCloud.ONEDRIVE)
                        )
                    }
                },
                onFailure = { oneDriveSignInError = OneDriveSignIn.errorText(it) }
            )
            isConnectingOneDrive = false
        }
    }

    // Function to handle enabling backup after overwrite confirmation
    fun enableBackupAfterOverwrite() {
        coroutineScope.launch {
            folderStatus = "Deleting old backups..."
            val deleteResult = driveBackupService.deleteAllBackups()
            if (deleteResult.isSuccess) {
                val deletedCount = deleteResult.getOrNull() ?: 0
                folderStatus = "Deleted $deletedCount old backup(s)"

                // Enable backup
                setDestination(BackupCloud.GOOGLE_DRIVE)

                // Reset detected state to show normal UI
                detectedBackupState = null

                // Ensure folder exists
                when (val folderResult = driveBackupService.ensureBackupFolder()) {
                    is DriveBackupService.FolderResult.Success -> {
                        folderStatus = "✓ Backup enabled"
                    }
                    is DriveBackupService.FolderResult.Error -> {
                        folderStatus = "Folder setup failed: ${folderResult.message}"
                    }
                }
            } else {
                folderStatus = "Failed to delete old backups: ${deleteResult.exceptionOrNull()?.message}"
            }
        }
    }

    // Activity result launcher for Google sign-in
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // A failed or cancelled sign-in is said in the card (Oct 2026); it used to show nothing
        if (result.resultCode == android.app.Activity.RESULT_CANCELED && result.data == null) {
            signInError = SignInErrorText.google(SignInErrorText.CANCELLED)
            return@rememberLauncherForActivityResult
        }
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (account != null) {
                signInError = null
                authManager.setSelectedAccount(account)
                isSignedIn = true
                userEmail = account.email

                // DO NOT auto-enable backup - detect state first
                // State detection will determine if we should show restore UI or enable backup
                coroutineScope.launch {
                    folderStatus = "Checking for existing backups..."
                    isDetectingState = true
                    detectedBackupState = backupStateDetector.detectState()
                    isDetectingState = false

                    // If backup is already enabled OR no existing backups, proceed normally
                    val state = detectedBackupState
                    if (state != null && state.uiState == BackupStateDetector.UIState.NO_BACKUPS && backupDestination == null) {
                        // No existing backups - safe to enable backup automatically (never in place
                        // of OneDrive's: one cloud at a time, the user switches on its screen)
                        setDestination(BackupCloud.GOOGLE_DRIVE)

                        when (val folderResult = driveBackupService.ensureBackupFolder()) {
                            is DriveBackupService.FolderResult.Success -> {
                                folderStatus = if (folderResult.wasCreated) {
                                    "✓ Backup folder created on Google Drive"
                                } else {
                                    "✓ Connected to backup folder"
                                }
                            }
                            is DriveBackupService.FolderResult.Error -> {
                                folderStatus = "Folder setup failed: ${folderResult.message}"
                            }
                        }
                    } else if (state != null && state.uiState == BackupStateDetector.UIState.BACKUP_ENABLED) {
                        // Backup already enabled
                        folderStatus = "✓ Backup folder ready"
                    } else {
                        // Existing backups detected - UI will show ExistingBackupDetectedCard
                        folderStatus = null
                    }
                }
            }
        } catch (e: Exception) {
            // Sign-in failed - stay in disconnected state
            isSignedIn = false
            userEmail = null
            signInError = SignInErrorText.google((e as? com.google.android.gms.common.api.ApiException)?.statusCode)
        }
    }

    // SAF launcher for creating local backup archive (.zip)
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                // Navigate to Processing phase to show user we're working
                currentPhase = BackupDialogPhase.Processing("Creating backup...")
                phaseHistory.clear() // Clear history so back button doesn't interfere

                when (val result = localBackupService.saveBackupToUri(uri)) {
                    is LocalBackupService.SaveResult.Success -> {
                        lastLocalBackupTime = System.currentTimeMillis()
                        // Update global settings
                        GlobalAppSettings.update(
                            GlobalAppSettings.current.copy(lastLocalBackupTime = lastLocalBackupTime)
                        )
                        // Refresh storage info
                        storageInfo = StorageCalculator.calculateStorage(context)
                        // Navigate to success with file name + any media/stroke omission warnings (Decision D2)
                        currentPhase = BackupDialogPhase.Success(
                            title = if (result.warnings.isEmpty()) "Backup Complete" else "Backup Complete (with notes)",
                            details = listOf("Saved to: ${result.fileName}") + result.warnings
                        )
                    }
                    is LocalBackupService.SaveResult.Error -> {
                        currentPhase = BackupDialogPhase.Error("Backup failed: ${result.message}")
                    }
                }
            }
        }
    }



    // On initial load, detect backup state if already signed in
    LaunchedEffect(isSignedIn) {
        if (isSignedIn) {
            // Check if backup is already enabled
            if (backupDestination == BackupCloud.GOOGLE_DRIVE) {
                // Backup enabled - verify folder and show normal UI
                val existingFolderId = authManager.getBackupFolderId()
                if (existingFolderId != null) {
                    folderStatus = "✓ Backup folder ready"
                } else {
                    folderStatus = "Verifying backup folder..."
                    when (val folderResult = driveBackupService.ensureBackupFolder()) {
                        is DriveBackupService.FolderResult.Success -> {
                            folderStatus = if (folderResult.wasCreated) {
                                "✓ Backup folder created on Google Drive"
                            } else {
                                "✓ Connected to existing backup folder"
                            }
                        }
                        is DriveBackupService.FolderResult.Error -> {
                            folderStatus = "Folder setup failed: ${folderResult.message}"
                        }
                    }
                }
            } else {
                // Backup not enabled - detect state to determine UI
                folderStatus = "Checking for existing backups..."
                isDetectingState = true
                detectedBackupState = backupStateDetector.detectState()
                isDetectingState = false

                // Nov 2026: cleared either way; with backups found it stayed on "Checking..."
                folderStatus = null
            }
        }
    }

    // Load storage info on first composition
    LaunchedEffect(Unit) {
        isLoadingStorage = true
        storageInfo = StorageCalculator.calculateStorage(context)
        isLoadingStorage = false
    }

    // E-Ink: Use Popup instead of Dialog to avoid system scrim/dimming
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.PanelDialog.widthFraction)  // Oct 2026: was 0.85f
                    .fillMaxHeight(EInkTheme.PanelDialog.heightFraction)  // was 0.75f
                    // Oct 2026 (owner): as tall as the content, up to the panel height, with the
                    // top where a full panel's top is, so the header and tabs never move
                    .wrapContentHeight(Alignment.Top)
                    .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.PopupOverlay.padding)
            ) {
                // Dynamic header title based on phase
                val headerTitle = when {
                    selectedTab == DataBackupTab.Maintenance -> "Maintenance"
                    currentPhase is BackupDialogPhase.IntentSelection -> "Data & Backup"
                    currentPhase is BackupDialogPhase.BackupDestination -> "Create Backup"
                    currentPhase is BackupDialogPhase.RestoreSource -> "Restore Backup"
                    currentPhase is BackupDialogPhase.CloudBackupFlow -> "Cloud Backup"
                    currentPhase is BackupDialogPhase.CloudRestoreFlow -> "Cloud Restore"
                    currentPhase is BackupDialogPhase.MicrosoftAccountChoice -> "Microsoft Account"
                    currentPhase is BackupDialogPhase.LocalBackupFlow -> "Local Backup"
                    currentPhase is BackupDialogPhase.LocalRestoreFlow -> "Local Restore"
                    currentPhase is BackupDialogPhase.Processing -> "Processing"
                    currentPhase is BackupDialogPhase.Success -> "Complete"
                    currentPhase is BackupDialogPhase.Error -> "Error"
                    else -> "Data & Backup"
                }

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                    ) {
                        // Back button (only show when in sub-phase)
                        if (phaseHistory.isNotEmpty() && selectedTab == DataBackupTab.BackupRestore) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier
                                    .size(24.dp)
                                    .noRippleClickable { navigateBack() },
                                tint = EInkTheme.Colors.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "Data & Backup",
                                modifier = Modifier.size(24.dp),
                                tint = EInkTheme.Colors.primary
                            )
                        }
                        Text(
                            text = headerTitle,
                            fontSize = EInkTheme.PopupOverlay.titleFontSize,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.PopupOverlay.titleColor
                        )
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(EInkTheme.Button.height),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = EInkTheme.Button.textColor
                        ),
                        border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor),
                        shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                    ) {
                        Text(
                            text = "Close",
                            fontSize = EInkTheme.Typography.button,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Tab Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.borderStrong),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TabButton(
                        text = "Backup & Restore",
                        isSelected = selectedTab == DataBackupTab.BackupRestore,
                        onClick = {
                            selectedTab = DataBackupTab.BackupRestore
                            resetToIntent() // Reset to intent selection when switching tabs
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TabButton(
                        text = "Maintenance",
                        isSelected = selectedTab == DataBackupTab.Maintenance,
                        onClick = { selectedTab = DataBackupTab.Maintenance },
                        isDanger = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Tab Content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    when (selectedTab) {
                        DataBackupTab.BackupRestore -> {
                            // Phase-based rendering for backup/restore workflow
                            when (currentPhase) {
                                is BackupDialogPhase.IntentSelection -> IntentSelectionContent(
                                    storageInfo = storageInfo,
                                    isLoadingStorage = isLoadingStorage,
                                    onBackupSelected = { navigateTo(BackupDialogPhase.BackupDestination) },
                                    onRestoreSelected = { navigateTo(BackupDialogPhase.RestoreSource) }
                                )

                                is BackupDialogPhase.BackupDestination, is BackupDialogPhase.RestoreSource -> {
                                    val google = CloudCardInfo(BackupCloud.GOOGLE_DRIVE, isSignedIn, userEmail, signInError, backupDestination == BackupCloud.GOOGLE_DRIVE)
                                    val oneDrive = CloudCardInfo(BackupCloud.ONEDRIVE, oneDriveAccount != null, oneDriveAccount?.address, oneDriveSignInError, backupDestination == BackupCloud.ONEDRIVE)
                                    val forRestore = currentPhase is BackupDialogPhase.RestoreSource
                                    val onConnect: (BackupCloud) -> Unit = { cloud ->
                                        if (cloud == BackupCloud.GOOGLE_DRIVE) signInLauncher.launch(authManager.getSignInIntent())
                                        else navigateTo(BackupDialogPhase.MicrosoftAccountChoice(forRestore))
                                    }
                                    if (forRestore) {
                                        RestoreSourceContent(
                                            clouds = listOf(google, oneDrive),
                                            onCloudSelected = { navigateTo(BackupDialogPhase.CloudRestoreFlow(it)) },
                                            onLocalSelected = { showBackupFilePicker = true },
                                            onConnect = onConnect
                                        )
                                    } else {
                                        BackupDestinationContent(
                                            clouds = listOf(google, oneDrive),
                                            onCloudSelected = { navigateTo(BackupDialogPhase.CloudBackupFlow(it)) },
                                            onLocalSelected = {
                                                createBackupLauncher.launch(localBackupService.generateBackupFileName())
                                            },
                                            onConnect = onConnect
                                        )
                                    }
                                }

                                is BackupDialogPhase.MicrosoftAccountChoice -> {
                                    val forRestore = (currentPhase as BackupDialogPhase.MicrosoftAccountChoice).forRestore
                                    MicrosoftAccountChoiceContent(
                                        calendarAccounts = calendarMicrosoftAccounts,
                                        isConnecting = isConnectingOneDrive,
                                        error = oneDriveSignInError,
                                        onUseAccount = { account ->
                                            connectOneDrive(forRestore) { OneDriveSignIn.useCalendarAccount(it, outlookAuth, account) }
                                        },
                                        onSignIn = { type ->
                                            connectOneDrive(forRestore) { OneDriveSignIn.signIn(it, outlookAuth, type) }
                                        }
                                    )
                                }

                                is BackupDialogPhase.CloudBackupFlow -> {
                                    val cloud = (currentPhase as BackupDialogPhase.CloudBackupFlow).cloud
                                    val isGoogle = cloud == BackupCloud.GOOGLE_DRIVE
                                    CloudBackupFlowContent(
                                        cloud = cloud,
                                        userEmail = if (isGoogle) userEmail else oneDriveAccount?.address,
                                        isAutoBackupOn = backupDestination == cloud,
                                        otherCloudOn = backupDestination?.takeIf { it != cloud },
                                        backupFrequency = backupFrequency,
                                        lastBackupTime = if (isGoogle) lastBackupTime else oneDriveLastBackupTime,
                                        isBackupInProgress = isBackupInProgress,
                                        statusMessage = if (isGoogle) folderStatus else oneDriveStatus,
                                        // One cloud at a time (owner decision 85): on here stops the other's
                                        onBackupEnabledChanged = { enabled ->
                                            setDestination(if (enabled) cloud else backupDestination.takeIf { it != cloud })
                                        },
                                        onBackupFrequencyChanged = { frequency ->
                                            backupFrequency = frequency
                                            authManager.setBackupFrequency(frequency)
                                        },
                                        onBackupNowClick = { backUpNow(cloud) },
                                        // Owner decision 74 (Nov 2026): stops backup on this tablet only. It was a
                                        // "Sign Out" that withdrew every Google permission BooxDay had, so calendar
                                        // sync and Task Sync stopped too, and reset the sync settings. OneDrive's
                                        // forgets its account the same way, never signing it out of Microsoft.
                                        onTurnOffBackupClick = {
                                            if (backupDestination == cloud) setDestination(null)
                                            if (isGoogle) {
                                                authManager.setBackupFolderId(null)
                                                detectedBackupState = null
                                            } else {
                                                authManager.setOneDriveAccount(null)
                                                oneDriveAccount = null
                                                oneDriveLastBackupTime = 0L
                                                oneDriveStatus = null
                                            }
                                            resetToIntent()
                                        }
                                    )
                                }

                                is BackupDialogPhase.CloudRestoreFlow -> {
                                    val cloud = (currentPhase as BackupDialogPhase.CloudRestoreFlow).cloud
                                    CloudRestoreFlowContent(
                                        cloud = cloud,
                                        userEmail = if (cloud == BackupCloud.GOOGLE_DRIVE) userEmail else oneDriveAccount?.address,
                                        backups = availableBackups,
                                        isLoadingBackups = isLoadingBackups,
                                        isRestoreInProgress = isRestoreInProgress,
                                        restoreStatus = restoreStatus,
                                        onRefreshBackups = { loadBackups(cloud) },
                                        onRestoreBackup = { backup ->
                                            coroutineScope.launch {
                                                // Owner decision 87: downloaded and previewed, then the
                                                // same "Restore from Backup?" as a device file. Cancel
                                                // goes back to this list.
                                                navigateTo(BackupDialogPhase.Processing("Downloading backup from ${cloud.label}..."))
                                                val problem = pendingRestore.offerDownload(
                                                    { serviceFor(cloud).downloadBackupToFile(backup.fileId) },
                                                    { localBackupService.previewBackupFromFile(it) }
                                                )
                                                if (problem != null) {
                                                    phaseHistory.clear()
                                                    currentPhase = BackupDialogPhase.Error(problem)
                                                }
                                            }
                                        }
                                    )
                                }

                                is BackupDialogPhase.LocalBackupFlow,
                                is BackupDialogPhase.LocalRestoreFlow -> {
                                    // These are handled by SAF launchers, show processing state
                                    BackupProgressContent(
                                        message = if (isLocalSaveInProgress) "Creating backup..." else "Restoring data..."
                                    )
                                }

                                is BackupDialogPhase.Processing -> {
                                    val phase = currentPhase as BackupDialogPhase.Processing
                                    BackupProgressContent(message = phase.message)
                                }

                                is BackupDialogPhase.Success -> {
                                    val phase = currentPhase as BackupDialogPhase.Success
                                    BackupSuccessContent(
                                        title = phase.title,
                                        details = phase.details,
                                        onDone = { resetToIntent() }
                                    )
                                }

                                is BackupDialogPhase.Error -> {
                                    val phase = currentPhase as BackupDialogPhase.Error
                                    BackupErrorContent(
                                        message = phase.message,
                                        onRetry = { resetToIntent() }, // Reset to start so user can try again
                                        onDone = { resetToIntent() }
                                    )
                                }
                            }
                        }

                        DataBackupTab.Maintenance -> MaintenanceContent(
                            showClearSyncedOption = onClearSyncedDataOnly != null || onClearSyncedDataAndResync != null,
                            onClearSyncedData = { showClearSyncedConfirmation = true },
                            onWipeAllData = if (onWipeAllData != null) {
                                { showWipeConfirmation = true }
                            } else null
                        )
                    }
                }
            }
        }
    }

    // Wipe confirmation dialog with typed "delete" confirmation
    if (showWipeConfirmation) {
        WipeAllDataConfirmationDialog(
            onDismiss = { showWipeConfirmation = false },
            onConfirm = {
                showWipeConfirmation = false
                onWipeAllData?.invoke()
            }
        )
    }

    // Clear synced data confirmation dialog
    if (showClearSyncedConfirmation) {
        ClearSyncedDataConfirmationDialog(
            onDismiss = { showClearSyncedConfirmation = false },
            onClearOnly = {
                showClearSyncedConfirmation = false
                onClearSyncedDataOnly?.invoke()
            },
            onClearAndResync = {
                showClearSyncedConfirmation = false
                onClearSyncedDataAndResync?.invoke()
            }
        )
    }

    // Overwrite backup confirmation popup (Smart Restore Detection)
    if (showOverwriteConfirmation && detectedBackupState?.existingBackupInfo != null) {
        OverwriteBackupConfirmationPopup(
            existingBackupInfo = detectedBackupState!!.existingBackupInfo!!,
            onConfirm = {
                showOverwriteConfirmation = false
                enableBackupAfterOverwrite()
            },
            onCancel = {
                showOverwriteConfirmation = false
            }
        )
    }

    // In-app backup file picker (replaces SAF system picker)
    if (showBackupFilePicker) {
        BackupFilePickerDialog(
            onFileSelected = { file ->
                showBackupFilePicker = false
                coroutineScope.launch {
                    isLocalRestoreInProgress = true
                    localRestoreStatus = "Reading backup file..."

                    when (val result = localBackupService.previewBackupFromFile(file)) {
                        is LocalBackupService.PreviewResult.Success -> {
                            pendingRestore.offerFile(result.preview, file)
                            localRestoreStatus = null
                        }
                        is LocalBackupService.PreviewResult.Error -> {
                            localRestoreStatus = "Preview failed: ${result.message}"
                            currentPhase = BackupDialogPhase.Error("Cannot open backup: ${result.message}")
                        }
                    }

                    isLocalRestoreInProgress = false
                }
            },
            onDismiss = { showBackupFilePicker = false }
        )
    }

    // "Restore from Backup?" for a device file or a downloaded cloud backup (owner decision 87)
    pendingRestore.waiting?.let { waiting ->
        LocalRestoreConfirmationPopup(
            preview = waiting.preview,
            onConfirm = {
                coroutineScope.launch {
                    // Navigate to Processing phase to show user we're working
                    currentPhase = BackupDialogPhase.Processing("Restoring your data...")
                    phaseHistory.clear() // Clear history so back button doesn't interfere

                    when (val restoreResult = pendingRestore.confirm { localBackupService.restoreBackupFromFile(it) }) {
                        is LocalBackupService.RestoreResult.Success -> {
                            // Caches and page previews still hold the old data
                            PostRestore.invalidateCachesAndRestart(context)
                        }
                        is LocalBackupService.RestoreResult.Error -> {
                            currentPhase = BackupDialogPhase.Error("Restore failed: ${restoreResult.message}")
                        }
                        null -> resetToIntent()
                    }
                }
            },
            onCancel = {
                pendingRestore.cancel()
                // A cloud backup was downloaded on the Processing screen: back to its list
                if (waiting.isDownload && currentPhase is BackupDialogPhase.Processing) navigateBack()
            }
        )
    }
}

@Composable
private fun TabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDanger: Boolean = false,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isSelected -> EInkTheme.Colors.primary
        else -> EInkTheme.Colors.background
    }
    val textColor = when {
        isSelected -> EInkTheme.Colors.white
        else -> EInkTheme.Colors.primary
    }
    // Show warning icon for danger tab when not selected
    val showWarning = isDanger && !isSelected

    Box(
        modifier = modifier
            .background(backgroundColor)
            .noRippleClickable(onClick = onClick)
            .padding(vertical = EInkTheme.Global.paddingSmall),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showWarning) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = text,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
        }
    }
}

@Composable
private fun MaintenanceContent(
    showClearSyncedOption: Boolean,
    onClearSyncedData: () -> Unit,
    onWipeAllData: (() -> Unit)?
) {
    ScrollableWithIndicator(
        modifier = Modifier.fillMaxWidth(),
        wrapHeight = true
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
        ) {
            // Warning banner - E-Ink styled (grayscale)
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = EInkTheme.Colors.offWhite,
                border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.darkGray),
                elevation = 0.dp,
                shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(EInkTheme.Global.paddingMedium),
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.warning_40px),
                        contentDescription = "Warning",
                        modifier = Modifier.size(24.dp),
                        tint = EInkTheme.Colors.primary
                    )
                    Text(
                        text = "Actions in this section can permanently delete your data. Proceed with caution.",
                        fontSize = EInkTheme.Preferences.explanationFontSize,
                        color = EInkTheme.Colors.primary
                    )
                }
            }

            // Clear Synced Data - styled like SettingCard
            if (showClearSyncedOption) {
                SettingCard(
                    title = "Clear All Synced Data",
                    description = "Remove all Google Calendar events that BooxDay has cached locally. Your events on Google Calendar will not be deleted."
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.black)
                            .noRippleClickable { onClearSyncedData() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Clear Synced Data",
                            fontSize = EInkTheme.Typography.button,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.Colors.black
                        )
                    }
                }
            }

            // Wipe All Data - styled like SettingCard but with warning emphasis
            if (onWipeAllData != null) {
                SettingCard(
                    title = "Wipe All App Data",
                    description = "Erase everything BooxDay keeps on this tablet: memos, tasks, events, Boox Notes links, settings and your OpenRouter key. BooxDay then closes. This CANNOT be undone."
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall),
                        modifier = Modifier.padding(bottom = EInkTheme.Global.spacingSmall)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.warning_40px),
                            contentDescription = "Warning",
                            modifier = Modifier.size(20.dp),
                            tint = EInkTheme.Colors.primary
                        )
                        Text(
                            text = "WARNING: This cannot be undone!",
                            fontSize = EInkTheme.Preferences.subLabelFontSize,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.Colors.primary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(EInkTheme.Colors.black)
                            .noRippleClickable { onWipeAllData() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Wipe Everything",
                            fontSize = EInkTheme.Typography.button,
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
 * Wipe All Data confirmation dialog with typed "delete" confirmation.
 */
@Composable
private fun WipeAllDataConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var typedText by remember { mutableStateOf("") }
    val confirmWord = "delete"
    val canConfirm = typedText.lowercase() == confirmWord

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        // The Popup gets no keyboard room of its own: center in the space above the keyboard
        // so the typed word and the buttons stay visible (scrolls if a small screen can't fit it)
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = keyboardHeight()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .background(EInkTheme.Colors.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .verticalScroll(rememberScrollState())
                    .padding(EInkTheme.Global.padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(R.drawable.warning_40px),
                    contentDescription = "Warning",
                    modifier = Modifier.size(48.dp),
                    tint = EInkTheme.Colors.primary
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                Text(
                    text = "PERMANENTLY DELETE ALL DATA?",
                    fontSize = EInkTheme.Typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = EInkTheme.Colors.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacingLarge))

                Text(
                    text = "This erases from this tablet:\n" +
                            "• Memos and handwriting\n" +
                            "• Tasks and task pages\n" +
                            "• Events, and the copies of your synced calendars\n" +
                            "• Boox Notes links and the search index\n" +
                            "• Settings, AI preferences and your OpenRouter key\n" +
                            "• Calendar account sign-ins",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacingLarge))

                // Oct 2026: say what stays, so nobody believes it is gone
                Text(
                    text = "Not erased: your Google, Outlook or iCloud calendars online, " +
                            (if (com.boox.day.BuildConfig.TASK_SYNC_ENABLED) "your tasks in Google Tasks or on your CalDAV server, " else "") +
                            "Google Drive and OneDrive backups and " +
                            "Android's own backup, files saved to Documents or Downloads (exports, backups, " +
                            "daily notes), and your OpenRouter key in your OpenRouter account " +
                            "(delete it at openrouter.ai/settings/keys).\n\nBooxDay closes when it's done.",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacingLarge))

                Text(
                    text = "This action CANNOT be undone.",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.padding))

                Text(
                    text = "Type \"delete\" to confirm:",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

                // Text input field
                BasicTextField(
                    value = typedText,
                    onValueChange = { typedText = it },
                    textStyle = TextStyle(
                        fontSize = EInkTheme.Typography.bodyLarge,
                        color = EInkTheme.Colors.primary,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.darkGray, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
                        .padding(EInkTheme.Global.paddingMedium),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (typedText.isEmpty()) {
                                Text(
                                    text = "delete",
                                    fontSize = EInkTheme.Typography.bodyLarge,
                                    color = EInkTheme.Colors.lightGray
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.padding))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Button.height),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = EInkTheme.Colors.primary
                        ),
                        border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Colors.primary),
                        shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = EInkTheme.Typography.button,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    OutlinedButton(
                        onClick = onConfirm,
                        enabled = canConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Button.height),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (canConfirm) EInkTheme.Colors.white else EInkTheme.Colors.gray,
                            backgroundColor = if (canConfirm) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                            disabledContentColor = EInkTheme.Colors.darkGray
                        ),
                        border = BorderStroke(
                            EInkTheme.Global.borderWidth,
                            if (canConfirm) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray
                        ),
                        shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Wipe All Data",
                                fontSize = EInkTheme.Typography.button,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Clear Synced Data confirmation dialog.
 */
@Composable
private fun ClearSyncedDataConfirmationDialog(
    onDismiss: () -> Unit,
    onClearOnly: () -> Unit,
    onClearAndResync: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .background(EInkTheme.Colors.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.darkGray, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
                    .padding(EInkTheme.Global.padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(R.drawable.warning_40px),
                    contentDescription = "Warning",
                    modifier = Modifier.size(40.dp),
                    tint = EInkTheme.Colors.primary
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                Text(
                    text = "Clear Synced Calendar Data",
                    fontSize = EInkTheme.Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacingLarge))

                Text(
                    text = "This will remove all Google Calendar events that BooxDay has cached locally.\n\n" +
                            "Your events on Google Calendar will not be deleted.",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                Text(
                    text = "You can optionally run a fresh sync right after clearing.",
                    fontSize = EInkTheme.Preferences.explanationFontSize,
                    color = EInkTheme.Colors.darkGray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

                // Three button row: Cancel, Clear Only, Clear & Sync
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                ) {
                    // Cancel button
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Button.height),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = EInkTheme.Colors.primary
                        ),
                        border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Colors.primary),
                        shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = EInkTheme.Typography.button,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Clear only button
                    OutlinedButton(
                        onClick = onClearOnly,
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Button.height),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = EInkTheme.Colors.primary
                        ),
                        border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary),
                        shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                    ) {
                        Text(
                            text = "Clear",
                            fontSize = EInkTheme.Typography.button,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Clear & Sync button
                    OutlinedButton(
                        onClick = onClearAndResync,
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Button.height),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = EInkTheme.Colors.white,
                            backgroundColor = EInkTheme.Colors.primary
                        ),
                        border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary),
                        shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                    ) {
                        Text(
                            text = "Clear & Sync",
                            fontSize = EInkTheme.Typography.button,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Phase Content Composables
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Initial intent selection: "What would you like to do?"
 */
@Composable
private fun IntentSelectionContent(
    storageInfo: StorageCalculator.StorageInfo?,
    isLoadingStorage: Boolean,
    onBackupSelected: () -> Unit,
    onRestoreSelected: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
    ) {
        // Question prompt
        Text(
            text = "What would you like to do?",
            fontSize = EInkTheme.Typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary,
            modifier = Modifier.padding(bottom = EInkTheme.Global.paddingSmall)
        )

        // Two option cards side by side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
        ) {
            // Create Backup option
            IntentOptionCard(
                icon = Icons.Default.Backup,
                title = "Create Backup",
                description = "Save your data to keep it safe",
                onClick = onBackupSelected,
                modifier = Modifier.weight(1f)
            )

            // Restore Backup option
            IntentOptionCard(
                icon = Icons.Default.Restore,
                title = "Restore Backup",
                description = "Get your data back from a backup",
                onClick = onRestoreSelected,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

        // Storage summary below the options (Oct 2026: the panel is as tall as its content)
        Divider(
            color = EInkTheme.Divider.subtleColor,
            thickness = EInkTheme.Divider.subtleThickness
        )

        StorageSummaryRow(
            storageInfo = storageInfo,
            isLoading = isLoadingStorage
        )
    }
}

/**
 * Clickable card for intent selection (Backup or Restore).
 */
@Composable
private fun IntentOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onClick),
        backgroundColor = EInkTheme.Colors.background,
        border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .padding(EInkTheme.Global.paddingMedium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = EInkTheme.Colors.primary
            )
            Text(
                text = title,
                fontSize = EInkTheme.Preferences.cardTitleFontSize,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                fontSize = EInkTheme.Preferences.explanationFontSize,
                color = EInkTheme.Colors.gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Compact storage summary row.
 */
@Composable
private fun StorageSummaryRow(
    storageInfo: StorageCalculator.StorageInfo?,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = EInkTheme.Global.paddingSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
    ) {
        Text(
            text = "Your Data:",
            fontSize = EInkTheme.Preferences.subLabelFontSize,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.Colors.primary
        )

        if (isLoading) {
            Text(
                text = "Loading...",
                fontSize = EInkTheme.Preferences.explanationFontSize,
                color = EInkTheme.Colors.darkGray
            )
        } else if (storageInfo != null) {
            Text(
                text = "${storageInfo.memoPagesCount} pages, ${storageInfo.strokesCount} strokes, ${storageInfo.tasksCount} tasks, ${storageInfo.eventsCount} events",
                fontSize = EInkTheme.Preferences.explanationFontSize,
                color = EInkTheme.Colors.gray
            )
        }
    }
}

/** One cloud's card on "where to save" and "where is your backup" (Nov 2026: Google Drive or OneDrive). */
internal class CloudCardInfo(
    val cloud: BackupCloud,
    val isConnected: Boolean,
    val address: String?,
    /** Why the last sign-in failed, shown in place of "Not connected". */
    val signInError: String?,
    /** This cloud is where automatic backup goes (owner decision 85). */
    val isAutoBackupOn: Boolean
)

@Composable
private fun CloudCard(info: CloudCardInfo, forRestore: Boolean, onOpen: () -> Unit, onConnect: () -> Unit) {
    val isGoogle = info.cloud == BackupCloud.GOOGLE_DRIVE
    DestinationCard(
        icon = Icons.Default.Cloud,
        title = info.cloud.label,
        subtitle = when {
            !info.isConnected -> info.signInError ?: "Not connected"
            info.isAutoBackupOn && !forRestore -> listOfNotNull(info.address, "Auto Backup on").joinToString(" · ")
            else -> info.address
        },
        description = when {
            forRestore && isGoogle -> listOf("Restore from your cloud backups")
            forRestore -> listOf("Restore from OneDrive › Apps › BooxDay")
            isGoogle -> listOf("Automatic backups", "Access from anywhere", "Syncs across devices")
            else -> listOf("Automatic backups", "Kept in OneDrive › Apps › BooxDay", "Personal, work or school account")
        },
        isRecommended = !forRestore,
        actionLabel = when {
            !info.isConnected -> if (isGoogle) "Connect Google Account" else "Connect Microsoft Account"
            forRestore -> "View Available Backups"
            else -> "Configure Backup"
        },
        onClick = if (info.isConnected) onOpen else onConnect
    )
}

/**
 * Backup destination selection: "Where do you want to save?"
 */
@Composable
internal fun BackupDestinationContent(
    clouds: List<CloudCardInfo>,
    onCloudSelected: (BackupCloud) -> Unit,
    onLocalSelected: () -> Unit,
    onConnect: (BackupCloud) -> Unit
) {
    ScrollableWithIndicator(
        modifier = Modifier.fillMaxWidth(),
        wrapHeight = true
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
        ) {
            Text(
                text = "Choose where to save your backup:",
                fontSize = EInkTheme.Preferences.cardTitleFontSize,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )

            // Google Drive and OneDrive
            clouds.forEach { info ->
                CloudCard(info, forRestore = false, onOpen = { onCloudSelected(info.cloud) }, onConnect = { onConnect(info.cloud) })
            }

            // Device Storage option
            DestinationCard(
                icon = Icons.Default.PhoneAndroid,
                title = "Device Storage",
                subtitle = null,
                description = listOf(
                    "Save to Downloads or SD card",
                    "You manage the backup file",
                    "Transfer manually between devices"
                ),
                isRecommended = false,
                actionLabel = "Save to Device",
                onClick = onLocalSelected
            )

            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

            // What's included section
            WhatsIncludedSection()
        }
    }
}

/**
 * Restore source selection: "Where is your backup?"
 */
@Composable
internal fun RestoreSourceContent(
    clouds: List<CloudCardInfo>,
    onCloudSelected: (BackupCloud) -> Unit,
    onLocalSelected: () -> Unit,
    onConnect: (BackupCloud) -> Unit
) {
    ScrollableWithIndicator(
        modifier = Modifier.fillMaxWidth(),
        wrapHeight = true
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
        ) {
            Text(
                text = "Where is your backup stored?",
                fontSize = EInkTheme.Preferences.cardTitleFontSize,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )

            // Google Drive and OneDrive
            clouds.forEach { info ->
                CloudCard(info, forRestore = true, onOpen = { onCloudSelected(info.cloud) }, onConnect = { onConnect(info.cloud) })
            }

            // Device File option
            DestinationCard(
                icon = Icons.Default.PhoneAndroid,
                title = "Device File",
                subtitle = null,
                description = listOf("Restore from a backup file on this device"),
                isRecommended = false,
                actionLabel = "Select Backup File",
                onClick = onLocalSelected
            )

            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

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
                Text(
                    text = "Restoring will REPLACE all current data.",
                    fontSize = EInkTheme.Preferences.explanationFontSize,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )
            }
        }
    }
}

/**
 * Which Microsoft account keeps the OneDrive backups (owner decision 86, Nov 2026): an Outlook
 * account already in Calendar Accounts first (Microsoft then shows only its consent page), or a
 * new sign-in with a personal or a work or school account.
 */
@Composable
internal fun MicrosoftAccountChoiceContent(
    calendarAccounts: List<OneDriveAccount>,
    isConnecting: Boolean,
    error: String?,
    onUseAccount: (OneDriveAccount) -> Unit,
    onSignIn: (AccountType) -> Unit
) {
    ScrollableWithIndicator(
        modifier = Modifier.fillMaxWidth(),
        wrapHeight = true
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
        ) {
            Text(
                text = "Which Microsoft account should keep your backups?",
                fontSize = EInkTheme.Preferences.cardTitleFontSize,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary
            )

            if (calendarAccounts.isNotEmpty()) {
                Text(
                    text = "Already in Calendar Accounts, no password needed:",
                    fontSize = EInkTheme.Preferences.explanationFontSize,
                    color = EInkTheme.Colors.gray
                )
                calendarAccounts.forEach { account ->
                    AccountChoiceButton(
                        label = "Use ${account.address}",
                        caption = null,
                        filled = true,
                        enabled = !isConnecting,
                        onClick = { onUseAccount(account) }
                    )
                }
                Text(
                    text = "Or sign in with another account:",
                    fontSize = EInkTheme.Preferences.explanationFontSize,
                    color = EInkTheme.Colors.gray
                )
            }

            AccountChoiceButton(
                label = "Personal Account",
                caption = "Outlook.com, Hotmail or Live",
                filled = calendarAccounts.isEmpty(),
                enabled = !isConnecting,
                onClick = { onSignIn(AccountType.OUTLOOK_PERSONAL) }
            )
            AccountChoiceButton(
                label = "Work or School Account",
                caption = "Microsoft 365 at work or school",
                filled = false,
                enabled = !isConnecting,
                onClick = { onSignIn(AccountType.OUTLOOK_WORK_SCHOOL) }
            )

            if (isConnecting) {
                Text(
                    text = "Finish signing in on Microsoft's page...",
                    fontSize = EInkTheme.Preferences.explanationFontSize,
                    color = EInkTheme.Colors.primary
                )
            } else if (error != null) {
                Text(
                    text = error,
                    fontSize = EInkTheme.Preferences.explanationFontSize,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )
            }

            Text(
                text = "BooxDay can see only its own folder in your OneDrive: Apps › BooxDay.",
                fontSize = EInkTheme.Preferences.explanationFontSize,
                color = EInkTheme.Colors.gray,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EInkTheme.Colors.offWhite, RoundedCornerShape(6.dp))
                    .padding(12.dp)
            )
        }
    }
}

@Composable
private fun AccountChoiceButton(label: String, caption: String?, filled: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(EInkTheme.Button.height)
                .background(if (filled) EInkTheme.Colors.primary else EInkTheme.Colors.background, RoundedCornerShape(6.dp))
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(6.dp))
                .then(if (enabled) Modifier.noRippleClickable(onClick = onClick) else Modifier)
                .padding(horizontal = EInkTheme.Global.paddingMedium),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (filled) EInkTheme.Colors.white else EInkTheme.Colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (caption != null) {
            Text(
                text = caption,
                fontSize = EInkTheme.Preferences.infoLabelFontSize,
                color = EInkTheme.Colors.gray
            )
        }
    }
}

/**
 * Destination/source selection card (Google Drive or Device).
 */
@Composable
private fun DestinationCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    description: List<String>,
    isRecommended: Boolean,
    actionLabel: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = EInkTheme.Colors.background,
        border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.lightGray),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(EInkTheme.Global.paddingMedium),
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = EInkTheme.Colors.primary
                    )
                    Text(
                        text = title,
                        fontSize = EInkTheme.Preferences.cardTitleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.primary
                    )
                }
                if (isRecommended) {
                    Text(
                        text = "(Recommended)",
                        fontSize = EInkTheme.Preferences.infoLabelFontSize,
                        color = EInkTheme.Colors.gray
                    )
                }
            }

            // Subtitle (email or status)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = EInkTheme.Preferences.explanationFontSize,
                    color = EInkTheme.Colors.gray
                )
            }

            // Description bullets
            description.forEach { item ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\u2022",
                        fontSize = EInkTheme.Preferences.explanationFontSize,
                        color = EInkTheme.Colors.gray
                    )
                    Text(
                        text = item,
                        fontSize = EInkTheme.Preferences.explanationFontSize,
                        color = EInkTheme.Colors.gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

            // Action button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EInkTheme.Button.height)
                    .background(EInkTheme.Colors.primary, RoundedCornerShape(6.dp))
                    .noRippleClickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionLabel,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.white
                )
            }
        }
    }
}

/**
 * What's included in backup section.
 */
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
            fontSize = EInkTheme.Preferences.subLabelFontSize,
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
                    fontSize = EInkTheme.Preferences.explanationFontSize,
                    color = EInkTheme.Colors.gray
                )
                Text(
                    text = item,
                    fontSize = EInkTheme.Preferences.explanationFontSize,
                    color = EInkTheme.Colors.gray
                )
            }
        }
    }
}

/**
 * Cloud backup configuration flow.
 */
@Composable
internal fun CloudBackupFlowContent(
    cloud: BackupCloud,
    userEmail: String?,
    /** This cloud is where automatic backup goes. */
    isAutoBackupOn: Boolean,
    /** The other cloud, when automatic backup goes there now (owner decision 85: one at a time). */
    otherCloudOn: BackupCloud?,
    backupFrequency: String,
    lastBackupTime: Long,
    isBackupInProgress: Boolean,
    statusMessage: String?,
    onBackupEnabledChanged: (Boolean) -> Unit,
    onBackupFrequencyChanged: (String) -> Unit,
    onBackupNowClick: () -> Unit,
    onTurnOffBackupClick: () -> Unit
) {
    ScrollableWithIndicator(
        modifier = Modifier.fillMaxWidth(),
        wrapHeight = true
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
        ) {
            // Header with account info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = EInkTheme.Colors.primary
                    )
                    Column {
                        Text(
                            text = "${cloud.label} Backup",
                            fontSize = EInkTheme.Preferences.cardTitleFontSize,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.Colors.primary
                        )
                        Text(
                            text = "Connected: ${userEmail ?: "Unknown"}",
                            fontSize = EInkTheme.Preferences.infoLabelFontSize,
                            color = EInkTheme.Colors.gray
                        )
                        if (cloud == BackupCloud.ONEDRIVE) {
                            Text(
                                text = "Kept in OneDrive › Apps › BooxDay",
                                fontSize = EInkTheme.Preferences.infoLabelFontSize,
                                color = EInkTheme.Colors.gray
                            )
                        }
                    }
                }

                // Review, Nov 2026: a button of the standard 44 dp height; the text alone was a
                // touch target well under the 42 dp minimum
                EinkSecondaryButton(
                    text = "Turn Off Backup",
                    onClick = onTurnOffBackupClick
                )
            }

            Divider(
                color = EInkTheme.Divider.subtleColor,
                thickness = EInkTheme.Divider.subtleThickness
            )

            // Auto Backup toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Auto Backup",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.primary
                    )
                    Text(
                        text = "Automatically backup your data",
                        fontSize = EInkTheme.Preferences.infoLabelFontSize,
                        color = EInkTheme.Colors.gray
                    )
                }
                EInkToggle(
                    checked = isAutoBackupOn,
                    onCheckedChange = { onBackupEnabledChanged(it) }
                )
            }

            // Said before the switch is turned on: one backup cloud at a time (owner decision 85)
            if (otherCloudOn != null && !isAutoBackupOn) {
                Text(
                    text = "Auto Backup goes to ${otherCloudOn.label} now. Turning it on here stops that; " +
                        "the backups on ${otherCloudOn.label} stay there.",
                    fontSize = EInkTheme.Preferences.explanationFontSize,
                    color = EInkTheme.Colors.primary
                )
            }

            // Frequency selector (if enabled)
            if (isAutoBackupOn) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Frequency",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.primary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Daily", "Weekly", "Monthly").forEach { freq ->
                            val isSelected = backupFrequency == freq
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.offWhite,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .border(
                                        EInkTheme.Global.borderWidth,
                                        if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .noRippleClickable { onBackupFrequencyChanged(freq) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = freq,
                                    fontSize = EInkTheme.Preferences.chipFontSize,
                                    color = if (isSelected) EInkTheme.Colors.white else EInkTheme.Colors.primary
                                )
                            }
                        }
                    }
                }
            }

            // Last backup info
            if (lastBackupTime > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Last Backup",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.primary
                    )
                    Text(
                        text = formatBackupTimestamp(lastBackupTime),
                        fontSize = EInkTheme.Preferences.infoValueFontSize,
                        color = EInkTheme.Colors.gray
                    )
                }
            }

            Divider(
                color = EInkTheme.Divider.subtleColor,
                thickness = EInkTheme.Divider.subtleThickness
            )

            // Status message
            if (statusMessage != null) {
                Text(
                    text = statusMessage,
                    fontSize = EInkTheme.Preferences.explanationFontSize,
                    color = if (statusMessage.startsWith("\u2713")) EInkTheme.Colors.primary else EInkTheme.Colors.gray
                )
            }

            // Backup Now button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EInkTheme.Button.height)
                    .background(
                        if (isBackupInProgress) EInkTheme.Colors.lightGray else EInkTheme.Colors.primary,
                        RoundedCornerShape(6.dp)
                    )
                    .then(
                        if (isBackupInProgress) Modifier else Modifier.noRippleClickable(onClick = onBackupNowClick)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isBackupInProgress) {
                    Text(
                        text = "Backing up...",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.primary
                    )
                } else {
                    Text(
                        text = "Backup Now",
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
 * Cloud restore flow - list and select backups.
 */
@Composable
internal fun CloudRestoreFlowContent(
    cloud: BackupCloud,
    userEmail: String?,
    backups: List<DriveBackupService.BackupInfo>,
    isLoadingBackups: Boolean,
    isRestoreInProgress: Boolean,
    restoreStatus: String?,
    onRefreshBackups: () -> Unit,
    onRestoreBackup: (DriveBackupService.BackupInfo) -> Unit
) {
    ScrollableWithIndicator(
        modifier = Modifier.fillMaxWidth(),
        wrapHeight = true
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Backups on ${cloud.label}",
                        fontSize = EInkTheme.Preferences.cardTitleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.primary
                    )
                    Text(
                        text = "From: ${userEmail ?: "Unknown"}",
                        fontSize = EInkTheme.Preferences.infoLabelFontSize,
                        color = EInkTheme.Colors.gray
                    )
                }
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    modifier = Modifier
                        .size(24.dp)
                        .noRippleClickable(onClick = onRefreshBackups),
                    tint = EInkTheme.Colors.primary
                )
            }

            Divider(
                color = EInkTheme.Divider.subtleColor,
                thickness = EInkTheme.Divider.subtleThickness
            )

            // Status message
            if (restoreStatus != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EInkTheme.Colors.offWhite, RoundedCornerShape(4.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isRestoreInProgress && restoreStatus.startsWith("\u2713")) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = EInkTheme.Colors.primary
                        )
                    }
                    Text(
                        text = restoreStatus,
                        fontSize = EInkTheme.Preferences.infoLabelFontSize,
                        color = EInkTheme.Colors.primary
                    )
                }
            }

            // Loading state
            if (isLoadingBackups) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = EInkTheme.Global.paddingLarge),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading backups...",
                        fontSize = EInkTheme.Preferences.explanationFontSize,
                        color = EInkTheme.Colors.darkGray
                    )
                }
            } else if (backups.isEmpty()) {
                Text(
                    text = "No backups found on ${cloud.label}.",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.gray,
                    modifier = Modifier.padding(vertical = EInkTheme.Global.paddingMedium)
                )
            } else {
                // Backup list
                backups.forEach { backup ->
                    BackupListItem(
                        backup = backup,
                        isRestoreInProgress = isRestoreInProgress,
                        onRestore = { onRestoreBackup(backup) }
                    )
                }
            }
        }
    }
}

/**
 * Single backup item in the list.
 */
@Composable
private fun BackupListItem(
    backup: DriveBackupService.BackupInfo,
    isRestoreInProgress: Boolean,
    onRestore: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = EInkTheme.Colors.background,
        border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.lightGray),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(EInkTheme.Global.paddingMedium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = backup.fileName,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )
                Text(
                    text = "Created: ${formatBackupTimestamp(backup.createdTime)}",
                    fontSize = EInkTheme.Preferences.infoLabelFontSize,
                    color = EInkTheme.Colors.gray
                )
                Text(
                    text = "Size: ${formatFileSize(backup.sizeBytes)}",
                    fontSize = EInkTheme.Preferences.infoLabelFontSize,
                    color = EInkTheme.Colors.gray
                )
            }

            Box(
                modifier = Modifier
                    .background(
                        if (isRestoreInProgress) EInkTheme.Colors.lightGray else EInkTheme.Colors.primary,
                        RoundedCornerShape(4.dp)
                    )
                    .then(
                        if (isRestoreInProgress) Modifier else Modifier.noRippleClickable(onClick = onRestore)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Restore",
                    fontSize = EInkTheme.Preferences.chipFontSize,
                    fontWeight = FontWeight.Medium,
                    color = if (isRestoreInProgress) EInkTheme.Colors.gray else EInkTheme.Colors.white
                )
            }
        }
    }
}

/**
 * Progress content during backup/restore operations.
 * E-Ink optimized: Uses static icon instead of animated spinner.
 */
@Composable
internal fun BackupProgressContent(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = EInkTheme.Global.padding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .background(EInkTheme.Colors.offWhite, RoundedCornerShape(12.dp))
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.lightGray, RoundedCornerShape(12.dp))
                .padding(EInkTheme.Global.paddingLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
        ) {
            // Static sync icon instead of animated spinner (e-ink friendly)
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "Processing",
                modifier = Modifier.size(48.dp),
                tint = EInkTheme.Colors.primary
            )
            Text(
                text = message,
                fontSize = EInkTheme.Typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Please wait...",
                fontSize = EInkTheme.Preferences.explanationFontSize,
                color = EInkTheme.Colors.gray
            )
        }
    }
}

/**
 * Success content after backup/restore completes.
 * E-Ink optimized: Clean layout with structured details display.
 */
@Composable
private fun BackupSuccessContent(
    title: String,
    details: List<String>,
    onDone: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = EInkTheme.Global.padding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(EInkTheme.Colors.background, RoundedCornerShape(12.dp))
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(12.dp))
                .padding(EInkTheme.Global.paddingLarge),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Success icon with checkmark
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(EInkTheme.Colors.offWhite, RoundedCornerShape(28.dp))
                    .border(2.dp, EInkTheme.Colors.primary, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Success",
                    modifier = Modifier.size(32.dp),
                    tint = EInkTheme.Colors.primary
                )
            }

            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

            // Title
            Text(
                text = title,
                fontSize = EInkTheme.Typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary,
                textAlign = TextAlign.Center
            )

            // Details list (if any)
            if (details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EInkTheme.Colors.offWhite, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    details.forEach { detail ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "\u2022",
                                fontSize = EInkTheme.Typography.bodyMedium,
                                color = EInkTheme.Colors.primary
                            )
                            Text(
                                text = detail,
                                fontSize = EInkTheme.Preferences.explanationFontSize,
                                color = EInkTheme.Colors.gray
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

            // Close button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EInkTheme.Button.height)
                    .background(EInkTheme.Colors.primary, RoundedCornerShape(6.dp))
                    .noRippleClickable(onClick = onDone),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Close",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.white
                )
            }
        }
    }
}

/**
 * Error content when backup/restore fails.
 */
@Composable
private fun BackupErrorContent(
    message: String,
    onRetry: () -> Unit,
    onDone: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = EInkTheme.Global.padding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error",
                modifier = Modifier.size(64.dp),
                tint = EInkTheme.Colors.primary
            )
            Text(
                text = "Something went wrong",
                fontSize = EInkTheme.Typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary
            )
            Text(
                text = message,
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

            Row(
                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
            ) {
                Box(
                    modifier = Modifier
                        .height(EInkTheme.Button.height)
                        .background(EInkTheme.Colors.offWhite, RoundedCornerShape(6.dp))
                        .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(6.dp))
                        .noRippleClickable(onClick = onRetry)
                        .padding(horizontal = EInkTheme.Global.paddingLarge),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Try Again",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.primary
                    )
                }

                Box(
                    modifier = Modifier
                        .height(EInkTheme.Button.height)
                        .background(EInkTheme.Colors.primary, RoundedCornerShape(6.dp))
                        .noRippleClickable(onClick = onDone)
                        .padding(horizontal = EInkTheme.Global.paddingLarge),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Go Back",
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
 * Format timestamp for display.
 */
private fun formatBackupTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d, yyyy 'at' h:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

/**
 * Format file size for display.
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
