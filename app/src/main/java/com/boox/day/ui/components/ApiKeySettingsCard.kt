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
import androidx.compose.ui.unit.dp
import com.boox.day.api.OpenRouterKeyCheck
import com.boox.day.security.UserOpenRouterKey
import com.boox.day.ui.dialogs.ActionButton
import com.boox.day.ui.dialogs.OpenRouterKeySteps
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
 * - No key: paste field, Paste and Show (44 dp), Save Key, and the steps to get a key.
 * - Saved: masked key, what OpenRouter said about it, Remove Key and Replace Key.
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
    var replacing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var confirmingRemove by remember { mutableStateOf(false) }
    // What OpenRouter last said about the saved key (null = not checked yet in this popup)
    var lastCheck by remember { mutableStateOf<OpenRouterKeyCheck.Result?>(null) }
    var entryMessage by remember { mutableStateOf<String?>(null) }

    // Saved key: ask OpenRouter once when the card opens (status and spending limit)
    LaunchedEffect(savedKey) {
        lastCheck = if (savedKey.isNullOrBlank()) null else OpenRouterKeyCheck.check(savedKey)
    }

    fun saveKey() {
        val key = input.trim()
        if (key.isEmpty() || checking) return
        checking = true
        entryMessage = null
        scope.launch {
            val result = OpenRouterKeyCheck.check(key)
            if (result is OpenRouterKeyCheck.Result.Rejected) {
                entryMessage = "OpenRouter did not accept this key. Check that you copied all of it."
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
        description = "Every AI feature runs on your own OpenRouter account. AI stays off until you add a key.",
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
            if (!savedKey.isNullOrBlank() && !replacing) {
                // ── Saved key ──
                val check = lastCheck
                when (check) {
                    is OpenRouterKeyCheck.Result.Accepted -> StatusLine(Icons.Default.Check, "OpenRouter accepted this key")
                    OpenRouterKeyCheck.Result.Rejected -> StatusLine(Icons.Default.Info, "OpenRouter did not accept this key. Replace it with a working one.", bold = true)
                    is OpenRouterKeyCheck.Result.Unreachable -> StatusLine(Icons.Default.Info, "Key saved. Could not reach OpenRouter to check it.")
                    null -> StatusLine(Icons.Default.Info, "Key saved. Checking with OpenRouter...")
                }
                KeyBox {
                    Text(
                        text = maskKey(savedKey),
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.darkGray
                    )
                }
                if (check is OpenRouterKeyCheck.Result.Accepted) {
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
                StatusLine(Icons.Default.Info, if (replacing) "Paste the new key" else "No key saved")
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
                                        text = "sk-or-v1-...",
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
                        ActionButton(
                            text = "Save Key",
                            enabled = input.isNotBlank(),
                            isLoading = checking,
                            onClick = { saveKey() },
                            modifier = Modifier.weight(0.4f)
                        )
                    }
                } else {
                    ActionButton(
                        text = "Save Key",
                        enabled = input.isNotBlank(),
                        isLoading = checking,
                        onClick = { saveKey() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Divider(color = EInkTheme.Divider.subtleColor, thickness = EInkTheme.Divider.subtleThickness)
                Text(
                    text = "How to get a key",
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

private fun limitText(check: OpenRouterKeyCheck.Result.Accepted): String {
    fun dollars(v: Double) = "$" + String.format(Locale.US, "%.2f", v)
    return when {
        check.isFreeTier -> "No credit bought yet: free models only, up to 50 requests a day."
        check.limit != null && check.limitRemaining != null ->
            "Spending limit on this key: ${dollars(check.limitRemaining)} left of ${dollars(check.limit)}."
        else -> "No spending limit on this key."
    }
}
