package com.boox.day.ui.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.R
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.data.db.UnifiedOcrStatistics
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.datastore.OcrSettings
import com.boox.day.ocr.OcrQuotaManager
import com.boox.day.ocr.UnifiedOcrIdleProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.boox.day.ui.components.EInkToggle
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.noRippleClickable
import androidx.compose.material.icons.filled.Close
import com.boox.day.ui.components.SettingCard
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.delay

/**
 * Settings dialog for Boox Notes integration.
 *
 * This feature requires MANAGE_EXTERNAL_STORAGE permission to read
 * the .ksync folder from Boox Notes app.
 *
 * The dialog explains the feature and guides user through permission grant.
 *
 * E-Ink optimized: Uses Popup (standard) now that DrawCanvas lifecycle issue is fixed.
 */
@Composable
fun BooxNotesSettingsDialog(
    onDismiss: () -> Unit,
    booxNotesEnabled: Boolean,
    onBooxNotesEnabledChanged: (Boolean) -> Unit,
    onScanNeeded: (() -> Unit)? = null,  // Callback when scan should be triggered
    onIndexNow: (() -> Unit)? = null,    // Callback to trigger immediate OCR indexing
    onClearIndex: (() -> Unit)? = null,  // Callback to clear all OCR data
    onOcrSettingsChanged: ((OcrSettings) -> Unit)? = null,  // Callback to persist OCR settings changes
    ocrProcessingState: UnifiedOcrIdleProcessor.ProcessingState? = null,  // Full processing state for progress display
    ocrRepository: UnifiedOcrRepository? = null  // Repository for loading OCR stats
) {
    val localContext = LocalContext.current

    // Pages are read by AI on the user's own OpenRouter key (Oct 2026); without one indexing waits
    val isStandaloneWithoutOcrKey = !com.boox.day.security.UserOpenRouterKey.hasKey

    // Check current permission status
    var hasPermission by remember {
        mutableStateOf(checkAllFilesAccessPermission())
    }

    // Boox Notes integration is now baseline-on for all users.
    LaunchedEffect(booxNotesEnabled) {
        if (!booxNotesEnabled) {
            onBooxNotesEnabledChanged(true)
        }
    }

    // ActivityResult launcher for storage permission settings
    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = checkAllFilesAccessPermission()
        if (granted != hasPermission) {
            hasPermission = granted
            if (granted) {
                if (!GlobalAppSettings.isBooxNotesReady) {
                    onScanNeeded?.invoke()
                }
            }
        }
    }

    // Safety net polling in case ActivityResult does not fire
    LaunchedEffect(hasPermission) {
        while (!hasPermission) {
            delay(3000) // Check every 3 seconds
            val newPermissionState = checkAllFilesAccessPermission()
            if (newPermissionState && !hasPermission) {
                // Permission just granted - trigger scan immediately
                hasPermission = true
                if (!GlobalAppSettings.isBooxNotesReady) {
                    onScanNeeded?.invoke()
                }
            } else if (newPermissionState != hasPermission) {
                hasPermission = newPermissionState
            }
        }
    }

    // Use Popup as intended by design system
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.01f)) // Invisible scrim to catch clicks
                .noRippleClickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    // Oct 2026: panel size, capped and scrolling (it had no height limit and ran off
                    // a small screen)
                    .fillMaxWidth(EInkTheme.PanelDialog.widthFraction)
                    .heightIn(max = EInkTheme.PanelDialog.maxHeight)
                    .noRippleClickable { /* consume clicks */ }
                    .background(
                        EInkTheme.PopupOverlay.background,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .border(
                        EInkTheme.PopupOverlay.borderWidth,
                        EInkTheme.PopupOverlay.borderColor,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(EInkTheme.PopupOverlay.padding)
            ) {
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
                        Icon(
                            painter = painterResource(R.drawable.boox_b_letter),
                            contentDescription = "Boox Notes",
                            modifier = Modifier.size(24.dp),
                            tint = EInkTheme.Colors.primary
                        )
                        Text(
                            text = "Boox Notes Integration",
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
                            modifier = Modifier.size(28.dp),
                            tint = EInkTheme.Colors.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Description
                Text(
                    text = "Link your Boox Notes to calendar events. View and access notes directly from BooxDay.",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Boox Notes baseline card (always included)
                SettingCard(
                    title = "Boox Notes",
                    description = if (hasPermission) {
                        "Your Boox notes show up on the days you wrote them"
                    } else {
                        "Requires 'All Files Access' permission"
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                hasPermission && GlobalAppSettings.isBooxNotesScanning -> "Scanning"
                                hasPermission && GlobalAppSettings.isBooxNotesReady -> "Active"
                                hasPermission -> "Preparing"
                                else -> "Setup Required"
                            },
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.darkGray
                        )
                        EinkSecondaryButton(
                            text = if (hasPermission) "Scan Now" else "Grant Access",
                            onClick = {
                                if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    try {
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${localContext.packageName}")
                                        }
                                        allFilesAccessLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        try {
                                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                            allFilesAccessLauncher.launch(intent)
                                        } catch (e2: Exception) {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.parse("package:${localContext.packageName}")
                                            }
                                            allFilesAccessLauncher.launch(intent)
                                        }
                                    }
                                } else {
                                    onScanNeeded?.invoke()
                                }
                            },
                            enabled = !GlobalAppSettings.isBooxNotesScanning
                        )
                    }
                }

                // Permission status / warning
                if (!hasPermission) {
                    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                    // Permission instructions card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = EInkTheme.Colors.background,
                        border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.darkGray),
                        elevation = 0.dp,
                        shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                    ) {
                        Column(
                            modifier = Modifier.padding(EInkTheme.Global.paddingMedium)
                        ) {
                            // Header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Permission needed",
                                    modifier = Modifier.size(20.dp),
                                    tint = EInkTheme.Colors.primary
                                )
                                Text(
                                    text = "One-Time Setup Required",
                                    fontSize = EInkTheme.Typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EInkTheme.Colors.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                            // Step-by-step instructions
                            Text(
                                text = "To access your Boox Notes, please grant file access permission:",
                                fontSize = EInkTheme.Preferences.explanationFontSize,
                                fontWeight = FontWeight.Medium,
                                color = EInkTheme.Colors.primary
                            )

                            Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

                            // Step 1
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "1.",
                                    fontSize = EInkTheme.Preferences.explanationFontSize,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EInkTheme.Colors.darkGray
                                )
                                Text(
                                    text = "Tap the button below to open Android settings",
                                    fontSize = EInkTheme.Preferences.explanationFontSize,
                                    color = EInkTheme.Colors.darkGray
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Step 2
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "2.",
                                    fontSize = EInkTheme.Preferences.explanationFontSize,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EInkTheme.Colors.darkGray
                                )
                                Text(
                                    text = "Toggle \"Allow access to manage all files\" to ON",
                                    fontSize = EInkTheme.Preferences.explanationFontSize,
                                    color = EInkTheme.Colors.darkGray
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Step 3
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "3.",
                                    fontSize = EInkTheme.Preferences.explanationFontSize,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EInkTheme.Colors.darkGray
                                )
                                Text(
                                    text = "Tap \"\u2190 All files access\" at the top to return",
                                    fontSize = EInkTheme.Preferences.explanationFontSize,
                                    color = EInkTheme.Colors.darkGray
                                )
                            }

                            Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

                            // Result note with B icon
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "The",
                                    fontSize = EInkTheme.Preferences.explanationFontSize,
                                    color = EInkTheme.Colors.darkGray
                                )
                                Icon(
                                    painter = painterResource(R.drawable.boox_b_letter),
                                    contentDescription = "B button",
                                    modifier = Modifier.size(16.dp),
                                    tint = EInkTheme.Colors.primary
                                )
                                Text(
                                    text = "button in the toolbar will then be enabled.",
                                    fontSize = EInkTheme.Preferences.explanationFontSize,
                                    color = EInkTheme.Colors.darkGray
                                )
                            }

                            Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                            // Action button
                            ActionButton(
                                text = "Open Permission Settings",
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        try {
                                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                data = Uri.parse("package:${localContext.packageName}")
                                            }
                                            allFilesAccessLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            try {
                                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                                allFilesAccessLauncher.launch(intent)
                                            } catch (e2: Exception) {
                                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.parse("package:${localContext.packageName}")
                                                }
                                                allFilesAccessLauncher.launch(intent)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // Scanning progress
                if (hasPermission && GlobalAppSettings.isBooxNotesScanning) {
                    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = EInkTheme.Colors.offWhite,
                        border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.borderStrong),
                        elevation = 0.dp,
                        shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(EInkTheme.Global.paddingMedium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = EInkTheme.Colors.primary,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Scanning Boox Notes...",
                                fontSize = EInkTheme.Typography.bodyMedium,
                                color = EInkTheme.Colors.darkGray
                            )
                        }
                    }
                }

                // First-run status (permission granted, waiting for first successful scan)
                if (hasPermission && !GlobalAppSettings.isBooxNotesScanning && !GlobalAppSettings.isBooxNotesReady) {
                    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = EInkTheme.Colors.offWhite,
                        border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.borderStrong),
                        elevation = 0.dp,
                        shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(EInkTheme.Global.paddingMedium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Preparing",
                                modifier = Modifier.size(20.dp),
                                tint = EInkTheme.Colors.primary
                            )
                            Text(
                                text = "Permission granted. Run a scan to load your Boox notes.",
                                fontSize = EInkTheme.Typography.bodyMedium,
                                color = EInkTheme.Colors.darkGray
                            )
                        }
                    }
                }

                // Handwriting reading (pages read by AI on the user's OpenRouter key)
                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))
                OcrSettingsSection(
                    onIndexNow = onIndexNow,
                    onClearIndex = onClearIndex,
                    onOcrSettingsChanged = onOcrSettingsChanged,
                    processingState = ocrProcessingState,
                    ocrRepository = ocrRepository,
                    indexingControlsEnabled = hasPermission,
                    isStandaloneWithoutOcrKey = isStandaloneWithoutOcrKey
                )
            }
        }
    }
}

/**
 * OCR Settings section for full-text search indexing.
 * Pages are read by AI on the user's own OpenRouter key (Oct 2026; was Cloud Vision).
 */
@Composable
private fun OcrSettingsSection(
    onIndexNow: (() -> Unit)? = null,
    onClearIndex: (() -> Unit)? = null,
    onOcrSettingsChanged: ((OcrSettings) -> Unit)? = null,  // Callback to persist OCR settings changes
    processingState: UnifiedOcrIdleProcessor.ProcessingState? = null,
    ocrRepository: UnifiedOcrRepository? = null,
    indexingControlsEnabled: Boolean = true,
    isStandaloneWithoutOcrKey: Boolean = false
) {
    val localContext = LocalContext.current
    val settings = GlobalAppSettings.current
    val ocrSettings = settings.ocrSettings
    val isProcessing = processingState?.isProcessing == true

    // Confirmation dialog state
    var showClearIndexConfirmation by remember { mutableStateOf(false) }

    // OCR stats loading
    var ocrStats by remember { mutableStateOf<UnifiedOcrStatistics?>(null) }

    // Pages read by AI on the user's OpenRouter key this month (Oct 2026)
    var aiReadsUsed by remember { mutableStateOf(0) }

    // Load stats when OCR is enabled and repository is available
    LaunchedEffect(ocrSettings.enabled, isProcessing) {
        if (ocrSettings.enabled && ocrRepository != null) {
            do {
                val loadedStats = withContext(Dispatchers.IO) {
                    ocrRepository.getStatistics()
                }

                val quotaManager = OcrQuotaManager(localContext)
                ocrStats = loadedStats
                aiReadsUsed = quotaManager.getAiReadsThisMonth()

                if (isProcessing) {
                    delay(2_000L)
                }
            } while (isProcessing)
        } else {
            ocrStats = null
        }
    }

    // Header
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Search",
            modifier = Modifier.size(20.dp),
            tint = EInkTheme.Colors.primary
        )
        Text(
            text = "Handwriting reading",
            fontSize = EInkTheme.Preferences.sectionHeaderFontSize,
            fontWeight = FontWeight.SemiBold,
            color = EInkTheme.Colors.primary
        )
    }

    Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

    Text(
        text = "BooxDay reads your handwritten pages with AI, so you can search them and Smart Link can match notes by what they say.",
        fontSize = EInkTheme.Preferences.explanationFontSize,
        color = EInkTheme.Colors.darkGray
    )

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    // No OpenRouter key: indexing waits for one
    if (isStandaloneWithoutOcrKey) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = EInkTheme.Colors.offWhite,
            border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.darkGray),
            elevation = 0.dp,
            shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
        ) {
            Column(
                modifier = Modifier.padding(EInkTheme.Global.paddingMedium)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "OCR unavailable",
                        modifier = Modifier.size(20.dp),
                        tint = EInkTheme.Colors.darkGray
                    )
                    Text(
                        text = "OpenRouter Key Needed",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = EInkTheme.Colors.darkGray
                    )
                }
                Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
                Text(
                    text = "Pages are read by AI on your own OpenRouter key. Add one in AI Settings to index new pages. Pages already indexed stay searchable, and basic note linking still works.",
                    fontSize = EInkTheme.Preferences.explanationFontSize,
                    color = EInkTheme.Colors.gray
                )
            }
        }
        Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
    }

    // FIX 4: Visual indicator for incomplete setup
    // Show warning when indexing is disabled (safety net for edge cases)
    if (!ocrSettings.enabled) {
        val warningText = if (indexingControlsEnabled) {
            "Enable indexing below to unlock Smart Link and AI features"
        } else {
            "Grant Boox file access above to unlock indexing"
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = EInkTheme.Colors.background,
            border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary),
            elevation = 0.dp,
            shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
        ) {
            Row(
                modifier = Modifier.padding(EInkTheme.Global.paddingMedium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Setup incomplete",
                    modifier = Modifier.size(20.dp),
                    tint = EInkTheme.Colors.primary
                )
                Text(
                    text = warningText,
                    fontSize = EInkTheme.Preferences.explanationFontSize,
                    color = EInkTheme.Colors.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
    }

    // OCR Enable toggle
    SettingCard(
        title = "Enable Indexing",
        description = if (isStandaloneWithoutOcrKey) {
            "Reads pages with AI on your OpenRouter key: add one in AI Settings"
        } else {
            "Process notes, memos, and tasks during idle time"
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    isStandaloneWithoutOcrKey && ocrSettings.enabled -> "On, waiting for a key"
                    isStandaloneWithoutOcrKey -> "Off, needs a key"
                    ocrSettings.enabled -> "Enabled"
                    else -> "Disabled"
                },
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.darkGray
            )
            EInkToggle(
                checked = ocrSettings.enabled,
                onCheckedChange = { enabled ->
                    val newOcrSettings = ocrSettings.copy(enabled = enabled)
                    GlobalAppSettings.update(settings.copy(ocrSettings = newOcrSettings))
                    onOcrSettingsChanged?.invoke(newOcrSettings)  // Persist the change
                },
                enabled = indexingControlsEnabled
            )
        }
    }

    if (ocrSettings.enabled && ocrStats == null && ocrRepository != null) {
        Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(EInkTheme.Colors.offWhite, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Loading stats...",
                fontSize = EInkTheme.Preferences.explanationFontSize,
                color = EInkTheme.Colors.gray
            )
        }
    }

    // OCR Stats display (when enabled, has access, and stats available)
    val loadedOcrStats = ocrStats
    if (ocrSettings.enabled && loadedOcrStats != null) {
        Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(EInkTheme.Colors.offWhite, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Pages indexed per source
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Monthly Notes pages
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Notes",
                        modifier = Modifier.size(20.dp),
                        tint = EInkTheme.Colors.darkGray
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${loadedOcrStats.booxNotesScanned}",
                        fontSize = EInkTheme.Preferences.cardTitleFontSize,
                        fontWeight = FontWeight.SemiBold,
                        color = EInkTheme.Colors.darkGray
                    )
                    Text(
                        text = "Note pages read",
                        fontSize = EInkTheme.Preferences.subLabelFontSize,
                        color = EInkTheme.Colors.gray
                    )
                }

                // Monthly Memos pages
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Memos",
                        modifier = Modifier.size(20.dp),
                        tint = EInkTheme.Colors.darkGray
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${loadedOcrStats.memosScanned}",
                        fontSize = EInkTheme.Preferences.cardTitleFontSize,
                        fontWeight = FontWeight.SemiBold,
                        color = EInkTheme.Colors.darkGray
                    )
                    Text(
                        text = "Memo pages read",
                        fontSize = EInkTheme.Preferences.subLabelFontSize,
                        color = EInkTheme.Colors.gray
                    )
                }

                // Monthly Tasks pages
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CheckBox,
                        contentDescription = "Tasks",
                        modifier = Modifier.size(20.dp),
                        tint = EInkTheme.Colors.darkGray
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${loadedOcrStats.tasksScanned}",
                        fontSize = EInkTheme.Preferences.cardTitleFontSize,
                        fontWeight = FontWeight.SemiBold,
                        color = EInkTheme.Colors.darkGray
                    )
                    Text(
                        text = "Task pages read",
                        fontSize = EInkTheme.Preferences.subLabelFontSize,
                        color = EInkTheme.Colors.gray
                    )
                }
            }

            // Totals: indexed, queued, failed
            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = EInkTheme.Colors.dividerHairline, thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${loadedOcrStats.totalIndexed}",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = EInkTheme.Colors.darkGray
                    )
                    Text(
                        text = "Pages read",
                        fontSize = EInkTheme.Preferences.infoLabelFontSize,
                        color = EInkTheme.Colors.gray
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${loadedOcrStats.totalPending}",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = EInkTheme.Colors.darkGray
                    )
                    Text(
                        text = "Waiting",
                        fontSize = EInkTheme.Preferences.infoLabelFontSize,
                        color = EInkTheme.Colors.gray
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${loadedOcrStats.totalFailed}",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = EInkTheme.Colors.darkGray
                    )
                    Text(
                        text = "Failed",
                        fontSize = EInkTheme.Preferences.infoLabelFontSize,
                        color = EInkTheme.Colors.gray
                    )
                }
            }

            // AI page reads on the user's OpenRouter credit (Oct 2026): about $0.0005 a page
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "AI page reads this month: $aiReadsUsed of ${OcrQuotaManager.MONTHLY_AI_READ_LIMIT}, " +
                    "about \$" + String.format(java.util.Locale.US, "%.2f", aiReadsUsed * 0.0005) +
                    " of your OpenRouter credit",
                fontSize = EInkTheme.Preferences.explanationFontSize,
                color = EInkTheme.Colors.darkGray
            )
        }
    }

    // Index Now and Clear Index buttons (only when OCR is enabled AND user has access)
    if (ocrSettings.enabled) {
        Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Index Now button
            if (onIndexNow != null) {
                EinkSecondaryButton(
                    text = if (isProcessing) "Indexing…" else "Index Now",
                    onClick = { if (!isProcessing) onIndexNow() },
                    enabled = !isProcessing && indexingControlsEnabled,
                    modifier = Modifier.weight(1f)
                )
            }

            // Clear Index button (asks first)
            if (onClearIndex != null) {
                EinkSecondaryButton(
                    text = "Clear Index",
                    onClick = { if (!isProcessing) showClearIndexConfirmation = true },
                    enabled = !isProcessing && indexingControlsEnabled,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Status message
    Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = EInkTheme.Colors.primary
        )
        Text(
            text = when {
                isProcessing -> {
                    // processingState is guaranteed non-null when isProcessing is true
                    val source = processingState!!.currentSource ?: "Processing"
                    "$source • Page ${processingState.currentPage}/${processingState.totalPages}"
                }
                ocrStats != null && ocrStats!!.totalPending == 0 && ocrStats!!.totalIndexed > 0 -> {
                    // Index is complete - show friendly message
                    "Index up to date • New content will be indexed during idle time"
                }
                ocrStats != null && ocrStats!!.totalPending > 0 -> {
                    // Items pending
                    "${ocrStats!!.totalPending} items pending • Processing during idle time"
                }
                else -> "Processing happens during idle time only"
            },
            fontSize = EInkTheme.Preferences.infoLabelFontSize,
            color = EInkTheme.Colors.gray
        )
    }

    // Clear Index confirmation popup
    if (showClearIndexConfirmation && onClearIndex != null) {
        ClearIndexConfirmationPopup(
            onDismiss = { showClearIndexConfirmation = false },
            onConfirm = {
                showClearIndexConfirmation = false
                onClearIndex()
            }
        )
    }
}

/**
 * Confirmation popup for clearing the search index.
 * Explains what will be lost in user-friendly terms.
 */
@Composable
private fun ClearIndexConfirmationPopup(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
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
                    .fillMaxWidth(0.85f)
                    .background(EInkTheme.Colors.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.Global.padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = EInkTheme.Colors.primary
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                Text(
                    text = "Clear Smart Search Index?",
                    fontSize = EInkTheme.Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacingLarge))

                Text(
                    text = "This will remove all the smart search data that BooxDay has carefully built from your handwritten notes:\n\n" +
                            "• Recognized text from your handwriting\n" +
                            "• Discovered keywords and topics\n" +
                            "• Smart summaries and insights",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                Text(
                    text = "Your original notes and drawings are safe — only the search index is cleared. You can rebuild it anytime with \"Index Now\".",
                    fontSize = EInkTheme.Preferences.explanationFontSize,
                    color = EInkTheme.Colors.darkGray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                ) {
                    EinkSecondaryButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                    // The confirmation's only action, so it may sit on the right (DIALOG_RULEBOOK rule 5)
                    ActionButton(text = "Clear Index", onClick = onConfirm, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Check if the app has MANAGE_EXTERNAL_STORAGE permission (All Files Access).
 * Required for reading Boox Notes .ksync folder.
 */
private fun checkAllFilesAccessPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        // On Android 10 and below, we don't need this special permission
        true
    }
}

/**
 * Launch system settings to request All Files Access permission.
 * Opens the app-specific permission page if possible, otherwise general storage settings.
 */
private fun requestAllFilesAccessPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:${context.packageName}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general storage settings
            try {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e2: Exception) {
                // Last resort: open app settings
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:${context.packageName}")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }
}
