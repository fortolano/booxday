package com.boox.day.editor.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.boox.day.R
import com.boox.day.booxnotes.BooxNotesDegradedReason
import com.boox.day.booxnotes.BooxNotesRuntimeEvaluator
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.editor.DrawCanvas
import com.boox.day.ui.SnackConf
import com.boox.day.ui.SnackState
import com.boox.day.ui.dialogs.BooxNotesPlaces
import com.boox.day.ui.theme.EInkTheme

/**
 * The B on the toolbar: one tap opens Boox Notes (Nov 2026; it opened a menu of three items, with
 * maintenance at the same rank as the notes). Those two tools now live in the window's ⋮ menu.
 *
 * [showAlert]: the "!" for access that was given and is gone, the same rule as the Settings gear.
 * Someone who never used Boox Notes has nothing to fix, so sees no alert.
 */
@Composable
fun NotesButton(
    modifier: Modifier = Modifier,
    showAlert: Boolean = false
) {
    val context = LocalContext.current
    Box(modifier = modifier) {
        ToolbarButton(
            iconId = R.drawable.boox_b_letter,  // Actual Boox "B" logo
            isSelected = false,
            onSelect = { openBooxNotes(context) },
            contentDescription = if (showAlert) "Boox Notes (needs file access)" else "Boox Notes",
            modifier = Modifier.size(44.dp * LocalToolbarIconScale.current),
            iconPadding = 11  // More padding for PNG image (reduced icon by 10%)
        )

        if (showAlert) {
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
    }
}

/**
 * What a tap on Boox Notes does, from the toolbar and from its menu (≡): the notes, or Set Up when
 * they aren't ready. The access check is fresh at tap time, a safety net for a stale badge.
 */
fun openBooxNotes(context: android.content.Context) {
    DrawCanvas.toolbarInteraction.tryEmit(Unit)
    val runtimeState = BooxNotesRuntimeEvaluator.evaluate(
        hasStoragePermission = com.boox.day.booxnotes.BooxNotesAccess.has(context),
        isScanning = GlobalAppSettings.isBooxNotesScanning,
        isReady = GlobalAppSettings.isBooxNotesReady,
        ocrEnabled = GlobalAppSettings.current.ocrSettings.enabled
    )
    when (runtimeState.booxReason) {
        BooxNotesDegradedReason.SCANNING_IN_PROGRESS -> SnackState.globalSnackFlow.tryEmit(
            SnackConf(key = "boox-scan", text = "Please wait, checking for notes…", duration = SnackConf.DURATION_QUICK)
        )
        BooxNotesDegradedReason.NO_PERMISSION,
        BooxNotesDegradedReason.INITIAL_SCAN_PENDING -> BooxNotesPlaces.openSetup()
        else -> DrawCanvas.viewBooxNotes.tryEmit(Unit)
    }
}
