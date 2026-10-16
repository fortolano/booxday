package com.boox.day.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.boox.day.api.OpenRouterConnect
import com.boox.day.api.OpenRouterKeyCheck
import com.boox.day.security.UserOpenRouterKey
import com.boox.day.ui.dialogs.ActionButton
import com.boox.day.ui.dialogs.OpenRouterKeySteps
import com.boox.day.ui.dialogs.OpenRouterLinks
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The OpenRouter key card in AI Settings (Oct 2026): every AI feature runs on the user's own
 * OpenRouter account, so there is no app key to fall back to.
 *
 * - No key: Connect OpenRouter (OpenRouter's sign-in for apps, [OpenRouterConnect]); or a paste
 *   field for a key or the return page's connection code, Paste and Show (44 dp), Save Key, and
 *   the steps to get a key by hand.
 * - Saved: whether AI is ready (credit or free models only), masked key, spending limit,
 *   Remove Key and Replace Key.
 *
 * Save Key first asks OpenRouter whether the key works ([OpenRouterKeyCheck], no tokens used);
 * a rejected key is not saved, and a key that could not be checked (offline) is saved anyway.
 * The key itself lives in encrypted storage ([UserOpenRouterKey]), never in the settings JSON.
 */
@Composable
fun ApiKeySettingsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val savedKey = UserOpenRouterKey.value
    val connectStatus = OpenRouterConnect.status
    var replacing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var startingConnect by remember { mutableStateOf(false) }
    var confirmingRemove by remember { mutableStateOf(false) }
    // What OpenRouter last said about the saved key (null = not checked yet in this popup)
    var lastCheck by remember { mutableStateOf<OpenRouterKeyCheck.Result?>(null) }
    var entryMessage by remember { mutableStateOf<String?>(null) }

    // Saved key: ask OpenRouter once when the card opens (status and spending limit). A key saved
    // by Connect OpenRouter while the card is open shows here too.
    LaunchedEffect(savedKey) {
        replacing = false
        lastCheck = null
        lastCheck = if (savedKey.isNullOrBlank()) null else OpenRouterKeyCheck.check(savedKey)
    }

    fun startConnect() {
        if (startingConnect) return
        startingConnect = true
        entryMessage = null
        scope.launch {
            OpenRouterLinks.open(context, OpenRouterConnect.begin(context))
            startingConnect = false
        }
    }

    fun saveKey() {
        val text = input.trim()
        if (text.isEmpty() || checking) return
        checking = true
        entryMessage = null
        scope.launch {
            // Oct 2026: the same field takes the connection code the return page shows when its
            // button can't open the app; anything else is checked as a key, as before
            if (!OpenRouterConnect.looksLikeKey(text) && OpenRouterConnect.hasPending(context)) {
                if (OpenRouterConnect.finishWithCode(context, text) is OpenRouterConnect.Status.Connected) {
                    input = ""
                    showKey = false
                }
                checking = false
                return@launch
            }
            val key = text
            val result = OpenRouterKeyCheck.check(key)
            if (result is OpenRouterKeyCheck.Result.Rejected) {
                entryMessage = if (OpenRouterConnect.looksLikeKey(key)) {
                    "OpenRouter did not accept this key. Check that you copied all of it."
                } else {
                    // Not a key: most likely a connection code whose 10 minutes ran out
                    "That isn't a working key. If it's a connection code, it has timed out: tap Connect OpenRouter again."
                }
            } else {
                withContext(Dispatchers.IO) { UserOpenRouterKey.save(context, key) }
                lastCheck = result
                input = ""
                showKey = false
                replacing = false
                entryMessage = null
            }
            checking = false
        }
    }

    SettingCard(
        title = "OpenRouter key",
        description = "Every AI feature runs on your own OpenRouter account.",
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
            if (!savedKey.isNullOrBlank() && !replacing) {
                // ── Saved key ──
                val check = lastCheck
                when {
                    check is OpenRouterKeyCheck.Result.Accepted && check.isFreeTier -> StatusLine(
                        Icons.Default.Check,
                        "Connected. No credit yet: AI uses free models, up to 50 requests a day. " +
                            "Search indexing starts when you add credit."
                    )
                    check is OpenRouterKeyCheck.Result.Accepted -> StatusLine(Icons.Default.Check, "Connected. AI is ready.")
                    check == OpenRouterKeyCheck.Result.Rejected -> StatusLine(Icons.Default.Info, "OpenRouter did not accept this key. Replace it with a working one.", bold = true)
                    check is OpenRouterKeyCheck.Result.Unreachable -> StatusLine(Icons.Default.Info, "Connected. The key is saved; OpenRouter couldn't be reached to check it.")
                    else -> StatusLine(Icons.Default.Info, "Key saved. Checking with OpenRouter...")
                }
                if (check is OpenRouterKeyCheck.Result.Accepted && check.isFreeTier) {
                    OpenRouterLink("Add credit: openrouter.ai/settings/credits") {
                        OpenRouterLinks.open(context, OpenRouterLinks.CREDITS)
                    }
                }
                KeyBox {
                    Text(
                        text = maskKey(savedKey),
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.darkGray
                    )
                }
                if (check is OpenRouterKeyCheck.Result.Accepted && !check.isFreeTier) {
                    Text(
                        text = limitText(check),
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.darkGray
                    )
                }
                if (confirmingRemove) {
                    Text(
                        text = "Remove the key from this tablet? AI features stop until you add one again.",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.primary
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        EinkSecondaryButton(
                            text = "Cancel",
                            onClick = { confirmingRemove = false },
                            modifier = Modifier.weight(0.4f)
                        )
                        Spacer(modifier = Modifier.weight(0.2f))
                        ActionButton(
                            text = "Remove Key",
                            onClick = {
                                confirmingRemove = false
                                scope.launch { withContext(Dispatchers.IO) { UserOpenRouterKey.save(context, null) } }
                            },
                            modifier = Modifier.weight(0.4f)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                    ) {
                        EinkSecondaryButton(
                            text = "Remove Key",
                            onClick = { confirmingRemove = true },
                            modifier = Modifier.weight(1f)
                        )
                        EinkSecondaryButton(
                            text = "Replace Key",
                            onClick = {
                                replacing = true
                                input = ""
                                entryMessage = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                // ── No key (or replacing it) ──
                when (connectStatus) {
                    OpenRouterConnect.Status.Connecting -> StatusLine(Icons.Default.Info, "Connecting to OpenRouter...")
                    is OpenRouterConnect.Status.Failed -> StatusLine(Icons.Default.Info, connectStatus.message, bold = true)
                    else -> StatusLine(Icons.Default.Info, if (replacing) "Connect again, or paste the new key" else "No key saved")
                }

                // Oct 2026: OpenRouter's sign-in for apps, so nothing has to be copied on e-ink
                ActionButton(
                    text = "Connect OpenRouter",
                    isLoading = startingConnect || connectStatus == OpenRouterConnect.Status.Connecting,
                    onClick = { startConnect() },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Opens OpenRouter in the browser: sign in, tap Authorize, then tap Open Boox Day.",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.darkGray
                )

                Text(
                    text = "Or paste a key or connection code",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                ) {
                    KeyBox(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = input,
                            onValueChange = {
                                input = it
                                entryMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                fontSize = EInkTheme.Typography.bodyMedium,
                                color = EInkTheme.Colors.primary
                            ),
                            singleLine = true,
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            decorationBox = { innerTextField ->
                                if (input.isEmpty()) {
                                    Text(
                                        text = "sk-or-v1-... or a code",
                                        fontSize = EInkTheme.Typography.bodyMedium,
                                        color = EInkTheme.Colors.gray
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                    SquareIconButton(Icons.Default.ContentPaste, "Paste") {
                        clipboardManager.getText()?.let { text ->
                            input = text.toString().trim()
                            entryMessage = null
                        }
                    }
                    SquareIconButton(
                        if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        if (showKey) "Hide key" else "Show key"
                    ) { showKey = !showKey }
                }
                entryMessage?.let {
                    Text(
                        text = it,
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.primary
                    )
                }
                // Connect OpenRouter is the main button now, so Save Key is the outlined one
                val saveLabel = if (checking) "Checking..." else "Save Key"
                if (replacing) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        EinkSecondaryButton(
                            text = "Cancel",
                            onClick = {
                                replacing = false
                                input = ""
                                entryMessage = null
                            },
                            modifier = Modifier.weight(0.4f)
                        )
                        Spacer(modifier = Modifier.weight(0.2f))
                        EinkSecondaryButton(
                            text = saveLabel,
                            enabled = input.isNotBlank() && !checking,
                            onClick = { saveKey() },
                            modifier = Modifier.weight(0.4f)
                        )
                    }
                } else {
                    EinkSecondaryButton(
                        text = saveLabel,
                        enabled = input.isNotBlank() && !checking,
                        onClick = { saveKey() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Divider(color = EInkTheme.Divider.subtleColor, thickness = EInkTheme.Divider.subtleThickness)
                Text(
                    text = "How to get a key by hand",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )
                OpenRouterKeySteps(
                    context = context,
                    lastStepTitle = "Paste it above",
                    lastStepDetail = " and tap Save Key."
                )
                Text(
                    text = "Free models need no credit but allow 50 requests a day (1,000 after you buy \$10 of credit).",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.darkGray
                )
            }
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, text: String, bold: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = EInkTheme.Colors.primary, modifier = Modifier.size(20.dp))
        Text(
            text = text,
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = EInkTheme.Colors.primary
        )
    }
}

/** The key's text area: 44 dp tall, bordered. */
@Composable
private fun KeyBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(EInkTheme.Global.touchTarget)
            .border(1.dp, EInkTheme.Colors.darkGray)
            .background(EInkTheme.Colors.white)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        content()
    }
}

@Composable
private fun SquareIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(EInkTheme.Global.touchTarget)
            .border(1.dp, EInkTheme.Colors.lightGray, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .noRippleClickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = EInkTheme.Colors.darkGray, modifier = Modifier.size(20.dp))
    }
}

/** "sk-or-v1-••••••••7f3a": enough to recognise the key, never the whole of it. */
private fun maskKey(key: String): String {
    val k = key.trim()
    if (k.length <= 13) return "••••••••"
    return k.take(9) + "••••••••" + k.takeLast(4)
}

/** A link that opens OpenRouter in the browser: 44 dp tall, underlined, with the open icon. */
@Composable
private fun OpenRouterLink(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = EInkTheme.Global.touchTarget)
            .noRippleClickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
    ) {
        Icon(
            imageVector = Icons.Default.OpenInNew,
            contentDescription = "Open in browser",
            tint = EInkTheme.Colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary,
            textDecoration = TextDecoration.Underline
        )
    }
}

/** Spending limit of a key that has bought credit (the free-tier case has its own status line). */
private fun limitText(check: OpenRouterKeyCheck.Result.Accepted): String {
    fun dollars(v: Double) = "$" + String.format(Locale.US, "%.2f", v)
    return when {
        check.limit != null && check.limitRemaining != null ->
            "Spending limit on this key: ${dollars(check.limitRemaining)} left of ${dollars(check.limit)}."
        else -> "No spending limit on this key."
    }
}
