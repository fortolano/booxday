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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.model.serverHost
import com.boox.day.sync.caldav.CalDavSignInOutcome
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
 * 2026): the form, then "Trust This Server?" when the server's certificate is its own. Closes once
 * connected. [signIn] gets the form, the server address and a certificate the user just trusted.
 */
@Composable
internal fun CalDavSignInPopup(
    initial: CalDavSignInState,
    signIn: suspend (form: CalDavSignInState, address: String, trust: String?) -> CalDavSignInOutcome,
    onDismiss: () -> Unit
) {
    var form by remember { mutableStateOf(initial) }
    var asking by remember { mutableStateOf<CalDavSignInOutcome.AskTrust?>(null) }
    var showDetails by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    // iCloud and Fastmail start from their own address; signing in again uses the account's server
    val address = if (form.again || calDavFormText(form.type).askAddress) form.address.trim() else form.type.serverAddress.orEmpty()

    fun connect(trust: String?) {
        form = form.copy(connecting = true, message = null)
        scope.launch {
            when (val outcome = signIn(form, address, trust)) {
                is CalDavSignInOutcome.Connected -> onDismiss()
                is CalDavSignInOutcome.AskTrust -> {
                    showDetails = false
                    asking = outcome
                    form = form.copy(connecting = false)
                }
                is CalDavSignInOutcome.Failed -> form = form.copy(connecting = false, message = outcome.message)
            }
        }
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
