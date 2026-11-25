package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.Box
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.theme.EInkTheme

/**
 * Dialog to confirm clearing all locally synced Google Calendar data.
 */
@Composable
fun ClearSyncedDataDialog(
    onDismiss: () -> Unit,
    onClearOnly: () -> Unit,
    onClearAndResync: () -> Unit
) {
    // E-Ink: Use Popup instead of Dialog to avoid system scrim/dimming
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            ClearSyncedDataDialogContent(
                onDismiss = onDismiss,
                onClearOnly = onClearOnly,
                onClearAndResync = onClearAndResync,
                modifier = Modifier.fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
            )
        }
    }
}

@Composable
internal fun ClearSyncedDataDialogContent(
    onDismiss: () -> Unit,
    onClearOnly: () -> Unit,
    onClearAndResync: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .padding(EInkTheme.ModernDialog.padding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Clear synced calendar data",
            fontWeight = FontWeight.Bold,
            fontSize = EInkTheme.ModernDialog.titleFontSize,
            color = EInkTheme.ModernDialog.titleColor
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "This will remove all synced calendar events (Google, Outlook, iCloud and others) that BooxDay has cached locally.\n\n" +
                    "Your events online will not be deleted.",
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "You can optionally run a fresh sync right after clearing.",
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.darkGray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Dialog Rulebook: 1 escape left, 1 secondary/destructive middle, 1 primary right (P6)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EinkSecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
                width = 0.dp,
                modifier = Modifier.weight(1f)
            )
            EinkSecondaryButton(
                text = "Clear Only",
                onClick = onClearOnly,
                width = 0.dp,
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                text = "Clear & Sync",
                onClick = onClearAndResync,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

