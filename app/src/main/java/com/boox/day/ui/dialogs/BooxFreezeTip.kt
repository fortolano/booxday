package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.Box
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.notifications.NotificationPermissionHelper
import com.boox.day.sync.BackgroundSyncWorker
import com.boox.day.sync.GoogleAuthManager
import com.boox.day.tasksync.TaskSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boox.day.ui.theme.EInkTheme

/**
 * "Reminders and sync on BOOX" (Nov 2026, owner decision 73): BOOX freezes apps it thinks the user
 * is done with ("Freeze new apps" is on, so BooxDay's switch is on from the install), and a frozen or
 * force-stopped BooxDay runs nothing in the background: no reminders, no background sync, until it
 * is opened again. BOOX gives apps no way to open its Freeze Settings, so the tip says where they are.
 */
object BooxFreezeTip {
    var isOpen by mutableStateOf(false)
        private set

    private const val PREFS = "boox_freeze_tip"
    private const val KEY_SHOWN = "shown"
    /** After the start-up popups (the update check comes 15 s after start). */
    private const val START_DELAY_MS = 20_000L

    /**
     * Once per install, on BOOX only, when something needs BooxDay in the background (owner decision
     * 73); never over the first-run welcome (it waits for a later start then).
     */
    internal fun shouldShow(
        isBoox: Boolean, alreadyShown: Boolean, welcomePending: Boolean,
        remindersOn: Boolean, backgroundSyncOn: Boolean, taskSyncOn: Boolean
    ): Boolean = isBoox && !alreadyShown && !welcomePending && (remindersOn || backgroundSyncOn || taskSyncOn)

    internal suspend fun showIfDue(context: Context) {
        delay(START_DELAY_MS)
        while (otherPopupOpen() || com.boox.day.editor.DrawCanvas.isStylusRecentlyActive()) delay(2_000L)
        val app = context.applicationContext
        val settings = GlobalAppSettings.current
        val due = withContext(Dispatchers.IO) {
            val auth = GoogleAuthManager(app)
            val hasAccounts = AppDatabase.getDatabase(app).connectedAccountDao().countActiveAccounts() > 0
            shouldShow(
                isBoox = NotificationPermissionHelper.isBooxDevice(),
                alreadyShown = prefs(app).getBoolean(KEY_SHOWN, false),
                welcomePending = settings.showWelcome || settings.onboardingDismissedAt == 0L,
                remindersOn = settings.taskNotificationsEnabled || settings.eventNotificationsEnabled,
                backgroundSyncOn = BackgroundSyncWorker.intervalHours(auth.isSyncEnabled(), hasAccounts, auth.getSyncFrequency()) != null,
                taskSyncOn = TaskSync.isOn(app)
            )
        }
        if (due) isOpen = true
    }

    fun close(context: Context) {
        isOpen = false
        prefs(context).edit().putBoolean(KEY_SHOWN, true).apply()
    }

    private fun otherPopupOpen(): Boolean =
        com.boox.day.update.AppUpdateGate.isOpen || com.boox.day.diagnostics.CrashReportGate.isOpen ||
            com.boox.day.ui.CalendarAiNotice.isOpen || com.boox.day.ui.AiKeyGate.isOpen || SupportPopup.isOpen

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** MainScreen's host for [BooxFreezeTip]; its own composable, outside MainScreen's root lambda. */
@Composable
fun BooxFreezeTipHost() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { BooxFreezeTip.showIfDue(context) }
    if (!BooxFreezeTip.isOpen) return
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = { BooxFreezeTip.close(context) },
        properties = PopupProperties(focusable = true)
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BooxFreezeTipCard(onClose = { BooxFreezeTip.close(context) })
        }
    }
}

@Composable
internal fun BooxFreezeTipCard(onClose: () -> Unit) {
    val shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
    Column(
        modifier = Modifier
            .width(EInkTheme.PopupOverlay.width)
            .heightIn(max = EInkTheme.PopupOverlay.maxHeight)
            .background(EInkTheme.PopupOverlay.background, shape)
            .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, shape)
            .padding(EInkTheme.PopupOverlay.padding),
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
        ) {
            Icon(
                imageVector = Icons.Default.AcUnit,
                contentDescription = null,
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Reminders and sync on BOOX",
                fontSize = EInkTheme.PopupOverlay.titleFontSize,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.PopupOverlay.titleColor
            )
        }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
        ) {
            TipText(
                "BOOX freezes apps it thinks you're done with. While BooxDay is frozen, its reminders " +
                    "and background sync stop until you open it again."
            )
            TipText("To keep them working:", bold = true)
            TipText("1. Open Apps, tap the snowflake at the top, and switch BooxDay off.")
            TipText("2. Leave BooxDay with the Home button, not the ✕ in Recents.")
            Text(
                text = "While the tablet sleeps, BOOX turns Wi-Fi off, so syncing waits until it wakes. " +
                    "This is also in Help → Data & Troubleshooting.",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )
        }
        ActionButton(text = "Close", onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TipText(text: String, bold: Boolean = false) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyMedium,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = EInkTheme.Colors.primary
    )
}
