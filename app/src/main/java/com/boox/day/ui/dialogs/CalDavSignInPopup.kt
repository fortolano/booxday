package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.model.serverHost
import com.boox.day.data.model.ProviderId
import com.boox.day.data.model.serviceName
import com.boox.day.sync.caldav.CalDavSignInOutcome
import com.boox.day.sync.caldav.CalDavUseChoice
import com.boox.day.tasksync.TaskSyncStart
import com.boox.day.tasksync.TaskSyncTarget
import com.boox.day.ui.components.CalDavUseForContent
import com.boox.day.ui.components.TaskSyncSwitchContent
import com.boox.day.ui.components.CalDavSignInContent
import com.boox.day.ui.components.CalDavSignInState
import com.boox.day.ui.components.TrustServerContent
import com.boox.day.ui.components.calDavFormText
import com.boox.day.ui.components.keyboardHeight
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Signing in to a CalDAV account from Calendar Accounts' "iCloud & more" tab (plan step 3.6, Nov
 * 2026): the form, then "Trust This Server?" when the server's certificate is its own. A server with
 * calendars and task lists asks what to use it for, and Tasks starts Task Sync with the account,
 * asking first when Task Sync uses another service (owner decision 78). Closes once connected.
 * [signIn] gets the form, the server address, a certificate the user just trusted and the choice.
 */
@Composable
internal fun CalDavSignInPopup(
    initial: CalDavSignInState,
    signIn: suspend (form: CalDavSignInState, address: String, trust: String?, choice: CalDavUseChoice?) -> CalDavSignInOutcome,
    onDismiss: () -> Unit
) {
    var form by remember { mutableStateOf(initial) }
    var asking by remember { mutableStateOf<CalDavSignInOutcome.AskTrust?>(null) }
    var showDetails by remember { mutableStateOf(false) }
    // "Use … For" (owner decision 78): the counts, and the ticks, both on to start
    var choosing by remember { mutableStateOf<CalDavSignInOutcome.ChooseUses?>(null) }
    var useCalendars by remember { mutableStateOf(true) }
    var useTasks by remember { mutableStateOf(true) }
    // Task Sync uses another service: the account to switch to, and that service
    var switching by remember { mutableStateOf<Pair<TaskSyncTarget, TaskSyncTarget>?>(null) }
    var trusted by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    // iCloud and Fastmail start from their own address; signing in again uses the account's server
    val address = if (form.again || calDavFormText(form.type).askAddress) form.address.trim() else form.type.serverAddress.orEmpty()

    /** Task Sync on with the new account; closes when done, or says why not on the form. */
    suspend fun startTasks(target: TaskSyncTarget) {
        val why = TaskSyncStart.turnOn(context, target)
        if (why == null) onDismiss() else {
            choosing = null
            form = form.copy(connecting = false, message = "Connected. $why")
        }
    }

    fun connect(trust: String?, choice: CalDavUseChoice? = null) {
        if (trust != null) trusted = trust
        form = form.copy(connecting = true, message = null)
        scope.launch {
            when (val outcome = signIn(form, address, trust ?: trusted, choice)) {
                is CalDavSignInOutcome.Connected -> {
                    val target = TaskSyncTarget.calDav(outcome.account)
                    val other = if (outcome.startTasks) TaskSyncStart.otherServiceOn(context, target) else null
                    when {
                        !outcome.startTasks -> onDismiss()
                        other != null -> {
                            choosing = null
                            switching = target to other
                        }
                        else -> startTasks(target)
                    }
                }
                is CalDavSignInOutcome.ChooseUses -> {
                    choosing = outcome
                    form = form.copy(connecting = false)
                }
                is CalDavSignInOutcome.AskTrust -> {
                    showDetails = false
                    asking = outcome
                    form = form.copy(connecting = false)
                }
                is CalDavSignInOutcome.Failed -> {
                    choosing = null
                    form = form.copy(connecting = false, message = outcome.message)
                }
            }
        }
    }

    switching?.let { (target, other) ->
        PopupFrame(onDismissRequest = { if (!form.connecting) onDismiss() }) { _ ->
            TaskSyncSwitchContent(
                currentService = other.nameBeside(target),
                newServer = target.nameBeside(other),
                // The account stays connected for what else it was picked for
                onKeep = onDismiss,
                onSwitch = {
                    switching = null
                    form = form.copy(connecting = true)
                    scope.launch { startTasks(target) }
                }
            )
        }
        return
    }

    choosing?.let { found ->
        PopupFrame(onDismissRequest = { if (!form.connecting) onDismiss() }) { _ ->
            CalDavUseForContent(
                // The server's name for Other, as on the account row ("localhost", not "CalDAV")
                server = serviceName(ProviderId.CALDAV, form.type, if ("://" in address) address else "https://$address"),
                calendarCount = found.calendarCount,
                taskListCount = found.taskListCount,
                useCalendars = useCalendars,
                useTasks = useTasks,
                connecting = form.connecting,
                onCalendars = { useCalendars = !useCalendars },
                onTasks = { useTasks = !useTasks },
                onCancel = onDismiss,
                onConnect = { connect(trust = null, choice = CalDavUseChoice(useCalendars, useTasks)) }
            )
        }
        return
    }

    PopupFrame(onDismissRequest = { if (!form.connecting) onDismiss() }) { keyboard ->
        CalDavSignInContent(
            state = form,
            onAddress = { form = form.copy(address = it, message = null) },
            onName = { form = form.copy(name = it, message = null) },
            onPassword = { form = form.copy(password = it, message = null) },
            onShowPassword = { form = form.copy(showPassword = !form.showPassword) },
            onPaste = { clipboard.getText()?.text?.trim()?.let { form = form.copy(password = it, message = null) } },
            onCancel = { keyboard.closeAfter(onDismiss) },
            // the keyboard is down by the time the server answers, so closing on success is safe
            onConnect = {
                keyboard.hide()
                connect(trust = null)
            }
        )
    }

    asking?.let { question ->
        PopupFrame(onDismissRequest = { asking = null }) { _ ->
            TrustServerContent(
                host = serverHost(if ("://" in address) address else "https://$address").ifEmpty { address },
                issuedTo = question.issuedTo,
                sha256 = question.sha256,
                changed = question.changed,
                showDetails = showDetails,
                onToggleDetails = { showDetails = !showDetails },
                onCancel = { asking = null },
                onTrust = {
                    asking = null
                    connect(trust = question.sha256)
                }
            )
        }
    }
}

/** Lets a [PopupFrame]'s content put the keyboard away, or close only once it is down. */
private class FrameKeyboard(val hide: () -> Unit, val closeAfter: (close: () -> Unit) -> Unit)

@Composable
private fun PopupFrame(onDismissRequest: () -> Unit, content: @Composable (FrameKeyboard) -> Unit) {
    // Closing while the keyboard is open left Calendar Accounts behind at the keyboard-up size
    // (Go 6, Nov 2026), so every close waits for the keyboard to go down first (1 s at most)
    val closer = remember { arrayOfNulls<FrameKeyboard>(1) }
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = { closer[0]?.closeAfter?.invoke(onDismissRequest) ?: onDismissRequest() },
        properties = PopupProperties(focusable = true)
    ) {
        // The Popup gets no keyboard room of its own: while the keyboard is open, center the form in
        // the space above it so Cancel and Connect stay visible (it scrolls if that space is too small)
        val keyboard = keyboardHeight()
        val keyboardOpen by rememberUpdatedState(keyboard > 0.dp)
        val controller = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        val scope = rememberCoroutineScope()
        val frameKeyboard = remember(controller, focus, scope) {
            val hide: () -> Unit = {
                focus.clearFocus(force = true)
                controller?.hide()
            }
            FrameKeyboard(hide = hide, closeAfter = { close ->
                if (!keyboardOpen) close() else {
                    hide()
                    scope.launch {
                        withTimeoutOrNull(1_000L) { snapshotFlow { keyboardOpen }.first { !it } }
                        close()
                    }
                }
            })
        }
        closer[0] = frameKeyboard
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (keyboard > 0.dp) Modifier.fillMaxHeight().padding(bottom = keyboard) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(EInkTheme.PopupOverlay.width)
                    .heightIn(max = EInkTheme.PopupOverlay.maxHeight)
                    .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                    .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                    .verticalScroll(rememberScrollState())
                    .padding(EInkTheme.PopupOverlay.padding)
            ) { content(frameKeyboard) }
        }
    }
}
