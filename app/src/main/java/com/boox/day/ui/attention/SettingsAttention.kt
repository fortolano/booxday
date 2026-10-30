package com.boox.day.ui.attention

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.boox.day.api.AiPickStatus
import com.boox.day.booxnotes.BooxNotesAccess
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.notifications.NotificationChannelManager
import com.boox.day.notifications.NotificationPermissionHelper
import com.boox.day.security.UserOpenRouterKey

/** The Settings menu entries that can need the user (Oct 2026, plan B3). */
enum class SettingsPlace { PREFERENCES, CALENDAR_ACCOUNTS, AI_SETTINGS, BOOX_NOTES }

/** Something that stops a feature the user turned on, and where to fix it. */
data class AttentionItem(val place: SettingsPlace, val reason: String)

/**
 * What needs the user's action in Settings (Oct 2026, plan B3). The gear's number counts these and
 * the menu marks their entries with a dot and the reason. Before, the number counted settings screens
 * not opened yet, so it stayed up after setup and said nothing about real problems.
 */
data class SettingsAttention(val items: List<AttentionItem> = emptyList()) {
    val count: Int get() = items.size
    fun reasonFor(place: SettingsPlace): String? = items.firstOrNull { it.place == place }?.reason
}

/**
 * A Settings entry opened from its dot (Oct 2026, plan B3): its dialog scrolls once to the card that
 * needs the user (Preferences: reminders; AI Settings: AI Models when a pick fails).
 */
object SettingsJump {
    private var target: SettingsPlace? = null

    fun request(place: SettingsPlace) {
        target = place
    }

    /** True once after [request] for [place]. */
    fun consume(place: SettingsPlace): Boolean {
        if (target != place) return false
        target = null
        return true
    }
}

/** The facts the rules read; plain values, so the rules are tested without Android. */
data class AttentionInputs(
    val aiEnabled: Boolean,
    val hasKey: Boolean,
    val failingPicks: Int,
    val remindersOn: Boolean,
    val notificationsAllowed: Boolean,
    val reminderChannelsOn: Boolean,
    val batteryOk: Boolean,
    /** File access was allowed once (the Boox Notes dialog marks it) and Boox Notes is on. */
    val booxNotesUsed: Boolean,
    val booxNotesAccess: Boolean,
    val accountsToReconnect: Int
)

object SettingsAttentionRules {

    fun items(inputs: AttentionInputs): List<AttentionItem> = buildList {
        with(inputs) {
            if (remindersOn) {
                when {
                    !notificationsAllowed -> add(AttentionItem(SettingsPlace.PREFERENCES, "Reminders are on, but notifications are off"))
                    !reminderChannelsOn -> add(AttentionItem(SettingsPlace.PREFERENCES, "A reminder type is off in Android settings"))
                    !batteryOk -> add(AttentionItem(SettingsPlace.PREFERENCES, "Reminders may be late (battery saving)"))
                }
            }
            if (accountsToReconnect > 0) {
                add(AttentionItem(SettingsPlace.CALENDAR_ACCOUNTS, if (accountsToReconnect == 1) "An account needs to reconnect" else "$accountsToReconnect accounts need to reconnect"))
            }
            if (aiEnabled && !hasKey) {
                add(AttentionItem(SettingsPlace.AI_SETTINGS, "Connect OpenRouter, or turn AI Features off"))
            } else if (aiEnabled && failingPicks > 0) {
                add(AttentionItem(SettingsPlace.AI_SETTINGS, "A model you picked isn't working"))
            }
            // Only access that was allowed and is gone: never using Boox Notes needs no action
            if (booxNotesUsed && !booxNotesAccess) {
                add(AttentionItem(SettingsPlace.BOOX_NOTES, "Boox Notes lost its file access"))
            }
        }
    }
}

/**
 * The live list for the main screen. Settings and the key are Compose state; Android permissions
 * aren't, so they are read again each time BooxDay comes back to the front (no polling).
 */
@Composable
fun rememberSettingsAttention(accountsToReconnect: Int): SettingsAttention {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumes by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumes++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val settings = GlobalAppSettings.current
    val hasKey = UserOpenRouterKey.hasKey
    val failingPicks = AiPickStatus.failures.size
    return remember(settings, hasKey, failingPicks, accountsToReconnect, resumes) {
        SettingsAttention(SettingsAttentionRules.items(inputsFor(context, accountsToReconnect, hasKey, failingPicks)))
    }
}

private fun inputsFor(context: Context, accountsToReconnect: Int, hasKey: Boolean, failingPicks: Int): AttentionInputs {
    val settings = GlobalAppSettings.current
    val tasksOn = settings.taskNotificationsEnabled
    val eventsOn = settings.eventNotificationsEnabled
    val remindersOn = tasksOn || eventsOn
    val channelsOn = !remindersOn ||
        ((!tasksOn || NotificationChannelManager.isChannelEnabled(context, NotificationChannelManager.CHANNEL_ID_DUE_TASKS)) &&
            (!eventsOn || NotificationChannelManager.isChannelEnabled(context, NotificationChannelManager.CHANNEL_ID_EVENT_REMINDERS)))
    return AttentionInputs(
        aiEnabled = settings.aiEnabled,
        hasKey = hasKey,
        failingPicks = failingPicks,
        remindersOn = remindersOn,
        notificationsAllowed = !remindersOn || NotificationPermissionHelper.hasNotificationPermission(context),
        reminderChannelsOn = channelsOn,
        batteryOk = !remindersOn || !NotificationPermissionHelper.isBooxDevice() ||
            NotificationPermissionHelper.isBatteryOptimizationExempt(context),
        booxNotesUsed = settings.booxNotesEnabled && settings.setupChecklist.booxNotesConfigured,
        booxNotesAccess = !settings.booxNotesEnabled || BooxNotesAccess.has(context),
        accountsToReconnect = accountsToReconnect
    )
}
