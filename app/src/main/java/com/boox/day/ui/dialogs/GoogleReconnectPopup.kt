package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.model.AccountType
import com.boox.day.diagnostics.CrashReportGate
import com.boox.day.ui.AiKeyGate
import com.boox.day.ui.GoogleReconnectGate
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.update.AppUpdateGate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** The check waits this long after start, so it doesn't land on top of start-up screens. */
private const val RECONNECT_CHECK_DELAY_MS = 8_000L

/**
 * MainScreen's host for [GoogleReconnectPopup]: checks once per app run, after start-up and once
 * no other start-up popup is open ([GoogleReconnectGate]). Reconnect calls [onReconnect] with the
 * account's type, which starts the same Google sign-in as Calendar Accounts → Reconnect.
 */
@Composable
fun GoogleReconnectHost(onReconnect: (AccountType) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        delay(RECONNECT_CHECK_DELAY_MS)
        snapshotFlow { CrashReportGate.isOpen || AppUpdateGate.isOpen || AiKeyGate.isOpen }.first { !it }
        GoogleReconnectGate.checkOnce(context)
    }
    val accountType = GoogleReconnectGate.accountType
    if (accountType != null) {
        GoogleReconnectPopup(
            onLater = { GoogleReconnectGate.close() },
            onReconnect = {
                GoogleReconnectGate.close()
                onReconnect(accountType)
            }
        )
    }
}

/**
 * "Reconnect Google Calendar" (Oct 2026, owner-approved text): BooxDay asks Google for less calendar
 * access than older versions, and Google needs the user to allow it once.
 */
@Composable
fun GoogleReconnectPopup(
    onLater: () -> Unit,
    onReconnect: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onLater,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(EInkTheme.PopupOverlay.width)
                    .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.PopupOverlay.padding),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = EInkTheme.Colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Reconnect Google Calendar",
                        fontSize = EInkTheme.PopupOverlay.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.PopupOverlay.titleColor
                    )
                }
                Text(
                    text = "BooxDay now asks Google for less access to your calendar. Allow it once so your " +
                        "Google events keep updating.",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                ) {
                    EinkSecondaryButton(
                        text = "Later",
                        onClick = onLater,
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        text = "Reconnect",
                        onClick = onReconnect,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
