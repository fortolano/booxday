package com.boox.day.editor.ui.toolbar

import com.boox.day.ui.LocalNestedPopupOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import android.os.Build
import android.os.Environment
import com.boox.day.R
import com.boox.day.booxnotes.BooxNotesDegradedReason
import com.boox.day.booxnotes.BooxNotesRuntimeEvaluator
import com.boox.day.data.datastore.BUTTON_SIZE
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.editor.DrawCanvas
import com.boox.day.ocr.OcrQuotaManager
import com.boox.day.ui.SnackConf
import com.boox.day.ui.SnackState
import com.boox.day.ui.convertDpToPixel
import com.boox.day.ui.theme.EInkTheme

/**
 * Notes/Boox Notes button with dropdown menu.
 * Single-tap behavior:
 * - Tap: Opens Boox Notes viewer, button becomes dark, AND dropdown menu appears
 * - Mutually exclusive with other mode buttons (draw, erase, select, insert, ai, menu)
 *
 * When hasPermission is false, shows a red "!" indicator and calls onPermissionNeeded
 * instead of showing the dropdown.
 */
@Composable
fun NotesButton(
    modifier: Modifier = Modifier,
    hasPermission: Boolean = true,
    onPermissionNeeded: () -> Unit = {},
    isActive: Boolean = false,
    onActivate: () -> Unit = {},
    isOpen: Boolean = false,
    onOpenChange: ((Boolean) -> Unit)? = null,
    onMenuOpenChange: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current

    // Use external state if provided, otherwise internal
    var internalShowDropdown by remember { mutableStateOf(false) }
    val showDropdown = if (onOpenChange != null) isOpen else internalShowDropdown
    val setShowDropdown: (Boolean) -> Unit = { value ->
        if (onOpenChange != null) {
            onOpenChange(value)
        } else {
            internalShowDropdown = value
        }
    }

    // Notify when menu open state changes (to disable drawing)
    if (onMenuOpenChange != null) {
        androidx.compose.runtime.LaunchedEffect(showDropdown) {
            onMenuOpenChange(showDropdown)
        }
    }

    Box(modifier = modifier) {
        ToolbarButton(
            iconId = R.drawable.boox_b_letter,  // Actual Boox "B" logo
            isSelected = isActive && hasPermission,  // Dark when active (only if has permission)
            onSelect = {
                DrawCanvas.toolbarInteraction.tryEmit(Unit)
                if (booxNotesMenuMayOpen(context, onPermissionNeeded)) {
                    onActivate()
                    setShowDropdown(true)
                }
            },
            contentDescription = if (hasPermission) "Boox Notes" else "Boox Notes (Setup Required)",
            hasDropdown = true,
            modifier = Modifier.size(44.dp * LocalToolbarIconScale.current),
            iconPadding = 11  // More padding for PNG image (reduced icon by 10%)
        )

        // Red "!" indicator when permission not granted
        if (!hasPermission) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = 4.dp)
                    .size(EInkTheme.AlertBadge.size)
                    .background(EInkTheme.AlertBadge.background, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "!",
                    color = EInkTheme.AlertBadge.textColor,
                    fontSize = EInkTheme.AlertBadge.fontSize,
                    fontWeight = EInkTheme.AlertBadge.fontWeight,
                    lineHeight = EInkTheme.AlertBadge.lineHeight
                )
            }
        }

        // Only show dropdown when showDropdown is true
        // Permission is validated at click time before setting showDropdown=true
        if (showDropdown) {
            Popup(
                offset = IntOffset(0, convertDpToPixel(EInkTheme.PopupMenu.offsetY, context).toInt()) + LocalNestedPopupOffset.current,
                onDismissRequest = { setShowDropdown(false) },
                properties = PopupProperties(focusable = false),  // Non-focusable allows taps to reach other buttons
                alignment = Alignment.TopStart
            ) {
                Column(
                    modifier = Modifier
                        .background(EInkTheme.PopupMenu.background)
                        .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor)
                        .width(EInkTheme.PopupMenu.width)
                ) {
                    BooxNotesMenuItems(onDone = { setShowDropdown(false) })
                }
            }
        }
    }
}

/**
 * What a tap on Boox Notes does before its menu opens, shared by the toolbar button and the toolbar
 * menu (≡) (Oct 2026): a fresh permission check at tap time (safety net for a stale badge). Shows a
 * hint while Boox Notes is being scanned, calls [onPermissionNeeded] when setup is missing, and
 * returns true when the menu may open.
 */
fun booxNotesMenuMayOpen(context: android.content.Context, onPermissionNeeded: () -> Unit): Boolean {
    val storagePermission = com.boox.day.booxnotes.BooxNotesAccess.has(context)
    val isScanning = GlobalAppSettings.isBooxNotesScanning
    val isReady = GlobalAppSettings.isBooxNotesReady
    val runtimeState = BooxNotesRuntimeEvaluator.evaluate(
        hasStoragePermission = storagePermission,
        isScanning = isScanning,
        isReady = isReady,
        ocrEnabled = GlobalAppSettings.current.ocrSettings.enabled
    )

    return when (runtimeState.booxReason) {
        BooxNotesDegradedReason.SCANNING_IN_PROGRESS -> {
            SnackState.globalSnackFlow.tryEmit(
                SnackConf(key = "boox-scan", text = "Please wait, scanning Boox Notes...", duration = SnackConf.DURATION_QUICK)
            )
            false
        }
        BooxNotesDegradedReason.NO_PERMISSION,
        BooxNotesDegradedReason.INITIAL_SCAN_PENDING -> {
            onPermissionNeeded()
            false
        }
        else -> true
    }
}

/**
 * The Boox Notes menu's items, shared by the toolbar dropdown and the toolbar menu (≡) when Boox
 * Notes is not on the bar (Oct 2026). [onDone] closes whichever menu shows them.
 */
@Composable
fun BooxNotesMenuItems(onDone: () -> Unit) {
    val context = LocalContext.current

    // Check if any calendar events exist (for guidance hint)
    val eventRepo = remember { com.boox.day.data.db.CalendarEventRepository(context) }
    val hasEvents by androidx.compose.runtime.produceState(initialValue = true) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            eventRepo.getEventCount() > 0
        }
    }

    // SMART LINK BACKFILL FIX: Check if OCR backfill is complete
    // These features need semantic OCR data to work effectively
    val quotaManager = remember { OcrQuotaManager(context) }
    val isBackfillComplete = quotaManager.isBackfillComplete()
    val isOcrEnabled = GlobalAppSettings.current.ocrSettings.enabled

    // Features are available when:
    // - OCR is disabled (user doesn't want indexing, fall back to basic matching)
    // - OR backfill is complete (semantic data available)
    val smartLinkAvailable = !isOcrEnabled || isBackfillComplete

    // Hint when no calendar events exist
    if (!hasEvents) {
        Text(
            text = "Connect a calendar account in Settings to enable note matching",
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.gray,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EInkTheme.MenuItem.horizontalPadding, vertical = 6.dp)
        )
        Divider(color = EInkTheme.Divider.subtleColor, thickness = EInkTheme.Divider.subtleThickness)
    }

    // Boox Notes… (opens the Boox Notes dialog; was "Link Notes to Events", Oct 2026)
    // GATED: Only available after backfill completes (or if OCR disabled)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .then(
                if (smartLinkAvailable) {
                    Modifier.clickable {
                        onDone()
                        DrawCanvas.viewBooxNotes.tryEmit(Unit)
                    }
                } else Modifier
            )
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.MenuItem.iconTextSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = "Boox Notes",
            modifier = Modifier.size(EInkTheme.MenuItem.iconSize),
            tint = if (smartLinkAvailable) EInkTheme.MenuItem.iconColor else EInkTheme.MenuItem.iconColorDisabled
        )
        Text(
            text = if (smartLinkAvailable) "Boox Notes…" else "Boox Notes\nReading notes, please wait",
            fontSize = EInkTheme.MenuItem.fontSize,
            color = if (smartLinkAvailable) EInkTheme.MenuItem.textColor else EInkTheme.MenuItem.textColorDisabled
        )
    }

    Divider(color = EInkTheme.Divider.subtleColor, thickness = EInkTheme.Divider.subtleThickness)

    // Review link suggestions (Smart Link for all notes; was "AI Note Matching": it makes no AI calls)
    // GATED: Only available after backfill completes (or if OCR disabled)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .then(
                if (smartLinkAvailable) {
                    Modifier.clickable {
                        onDone()
                        DrawCanvas.openBulkSmartLink.tryEmit(Unit)
                    }
                } else Modifier
            )
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.MenuItem.iconTextSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Link,
            contentDescription = "Review link suggestions",
            modifier = Modifier.size(EInkTheme.MenuItem.iconSize),
            tint = if (smartLinkAvailable) EInkTheme.MenuItem.iconColor else EInkTheme.MenuItem.iconColorDisabled
        )
        Text(
            text = if (smartLinkAvailable) "Review link suggestions" else "Link suggestions\nReading notes, please wait",
            fontSize = EInkTheme.MenuItem.fontSize,
            color = if (smartLinkAvailable) EInkTheme.MenuItem.textColor else EInkTheme.MenuItem.textColorDisabled
        )
    }

    Divider(color = EInkTheme.Divider.subtleColor, thickness = EInkTheme.Divider.subtleThickness)

    // Rescan notes
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .clickable {
                onDone()
                DrawCanvas.refreshBooxNotes.tryEmit(Unit)
            }
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.MenuItem.iconTextSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Rescan notes",
            modifier = Modifier.size(EInkTheme.MenuItem.iconSize),
            tint = EInkTheme.MenuItem.iconColor
        )
        Text(
            text = "Rescan notes",
            fontSize = EInkTheme.MenuItem.fontSize,
            color = EInkTheme.MenuItem.textColor
        )
    }
}
