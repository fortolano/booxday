package com.boox.day.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.boox.day.notifications.NotificationPermissionHelper

/** Where a runtime permission stands, for the button that asks for it. */
enum class AskState {
    GRANTED,

    /** Android's own prompt can be shown. */
    CAN_ASK,

    /** Android no longer shows its prompt (denied for good): only Android settings can allow it. */
    OPEN_SETTINGS
}

/**
 * One way to ask for a runtime permission (Oct 2026). A button that asked Android after it had
 * stopped showing its prompt did nothing at all ("Grant Permission", "Tap to Allow Microphone").
 *
 * The prompt is always tried first. Only when Android answers "denied" at once, without showing it,
 * is the permission denied for good; the same tap then opens Android settings, and the button says
 * "Open Settings" from then on. A prompt the user merely dismissed is not mistaken for that.
 * The state is checked again whenever the app comes back, so allowing it in Settings shows at once.
 */
class PermissionAsk internal constructor(val state: AskState, private val onRequest: () -> Unit) {
    val granted: Boolean get() = state == AskState.GRANTED

    fun request() = onRequest()
}

object PermissionAsks {
    private const val PREFS = "permission_asks"

    /** An answer quicker than this came without Android's prompt on screen. */
    internal const val INSTANT_DENIAL_MS = 350L

    internal fun stateFor(granted: Boolean, instantDenialSeen: Boolean): AskState = when {
        granted -> AskState.GRANTED
        instantDenialSeen -> AskState.OPEN_SETTINGS
        else -> AskState.CAN_ASK
    }

    internal fun isInstantDenial(granted: Boolean, answerAfterMs: Long): Boolean = !granted && answerAfterMs < INSTANT_DENIAL_MS

    /** Notifications below Android 13 have no prompt: they are the app's switch in Android settings. */
    private fun hasNoPrompt(permission: String) =
        permission == Manifest.permission.POST_NOTIFICATIONS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    fun isGranted(context: Context, permission: String): Boolean =
        if (permission == Manifest.permission.POST_NOTIFICATIONS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

    fun openSettings(context: Context, permission: String) {
        runCatching {
            if (permission == Manifest.permission.POST_NOTIFICATIONS) {
                NotificationPermissionHelper.openNotificationSettings(context)
            } else {
                NotificationPermissionHelper.openAppSettings(context)
            }
        }
    }

    internal fun instantDenialSeen(context: Context, permission: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(permission, false)

    internal fun setInstantDenialSeen(context: Context, permission: String, seen: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(permission, seen).apply()
    }

    internal fun noPrompt(permission: String) = hasNoPrompt(permission)
}

/** [onGranted] runs once each time the permission becomes allowed (in the prompt or in Settings). */
@Composable
fun rememberPermissionAsk(permission: String, onGranted: () -> Unit = {}): PermissionAsk {
    val context = LocalContext.current
    val latestOnGranted by rememberUpdatedState(onGranted)
    var granted by remember(permission) { mutableStateOf(PermissionAsks.isGranted(context, permission)) }
    var instantDenial by remember(permission) { mutableStateOf(PermissionAsks.instantDenialSeen(context, permission)) }
    var askedAt by remember { mutableLongStateOf(0L) }

    fun becameGranted() {
        granted = true
        instantDenial = false
        PermissionAsks.setInstantDenialSeen(context, permission, false)
        latestOnGranted()
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            becameGranted()
        } else if (PermissionAsks.isInstantDenial(false, SystemClock.elapsedRealtime() - askedAt)) {
            // No prompt was shown: denied for good. Go to Settings now, so the tap still does something
            instantDenial = true
            PermissionAsks.setInstantDenialSeen(context, permission, true)
            PermissionAsks.openSettings(context, permission)
        }
    }

    // Back from Android settings (or anywhere): read the state again
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, permission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = PermissionAsks.isGranted(context, permission)
                if (now && !granted) becameGranted() else granted = now
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val state = PermissionAsks.stateFor(granted, instantDenial)
    return PermissionAsk(state) {
        when {
            state == AskState.GRANTED -> Unit
            state == AskState.OPEN_SETTINGS || PermissionAsks.noPrompt(permission) ->
                PermissionAsks.openSettings(context, permission)
            else -> {
                askedAt = SystemClock.elapsedRealtime()
                launcher.launch(permission)
            }
        }
    }
}
