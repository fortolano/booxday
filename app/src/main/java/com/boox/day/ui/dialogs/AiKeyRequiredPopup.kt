package com.boox.day.ui.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.AiKeyGate
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/** OpenRouter pages the steps link to (checked Oct 2026). */
object OpenRouterLinks {
    const val SIGN_UP = "https://openrouter.ai/sign-up"
    const val CREDITS = "https://openrouter.ai/settings/credits"
    const val KEYS = "https://openrouter.ai/settings/keys"
    const val BYOK = "https://openrouter.ai/settings/integrations"

    /** Opens [url] in the tablet's browser. */
    fun open(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w("OpenRouterLinks", "No browser for $url: ${e.message}")
        }
    }
}

/**
 * MainScreen's host for [AiKeyRequiredPopup]: shows it while [AiKeyGate] is open and calls
 * [onOpenAiSettings] once after Add Key. Its own composable keeps MainScreen's root lambda small.
 */
@Composable
fun AiKeyGateHost(onOpenAiSettings: () -> Unit) {
    val reason = AiKeyGate.openReason
    if (reason != null) {
        AiKeyRequiredPopup(
            reason = reason,
            onClose = { AiKeyGate.close() },
            onAddKey = { AiKeyGate.requestAddKey() }
        )
    }
    val addKeyPending = AiKeyGate.addKeyPending
    LaunchedEffect(addKeyPending) {
        if (addKeyPending && AiKeyGate.consumeAddKey()) onOpenAiSettings()
    }
}

/**
 * "Add your OpenRouter key" (Oct 2026): shown when an AI feature starts without the user's own
 * key (or OpenRouter rejected it). Steps to create an account, add credit and create a key, and
 * Add Key, which opens AI Settings at the key field. MainScreen hosts it (see [AiKeyGate]).
 */
@Composable
fun AiKeyRequiredPopup(
    reason: AiKeyGate.Reason,
    onClose: () -> Unit,
    onAddKey: () -> Unit
) {
    val context = LocalContext.current
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onClose,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(EInkTheme.PopupOverlay.width)
                    .heightIn(max = EInkTheme.PopupOverlay.maxHeight)
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
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = EInkTheme.Colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Add your OpenRouter key",
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
                    if (reason == AiKeyGate.Reason.KEY_REJECTED) {
                        Text(
                            text = "OpenRouter did not accept your saved key. Check it, or create a new one.",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.Colors.primary
                        )
                    }
                    Text(
                        text = "AI features in Boox Day run on your own OpenRouter account. OpenRouter bills you " +
                            "for what you use. With the default models a typical action costs about a cent or less.",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.primary
                    )
                    OpenRouterKeySteps(
                        context = context,
                        lastStepTitle = "Paste it here:",
                        lastStepDetail = " tap Add Key, then Paste and Save Key."
                    )
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    EinkSecondaryButton(
                        text = "Close",
                        onClick = onClose,
                        modifier = Modifier.weight(0.4f)
                    )
                    Spacer(modifier = Modifier.weight(0.2f))
                    ActionButton(
                        text = "Add Key",
                        onClick = onAddKey,
                        modifier = Modifier.weight(0.4f)
                    )
                }
            }
        }
    }
}

/**
 * The four steps to an OpenRouter key, with links. Shared by [AiKeyRequiredPopup] and the key
 * card in AI Settings (the last step says where to paste).
 */
@Composable
fun OpenRouterKeySteps(context: Context, lastStepTitle: String, lastStepDetail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
        KeyStep(number = 1, title = "Create an account.", detail = null) {
            StepLink(text = "openrouter.ai/sign-up") { OpenRouterLinks.open(context, OpenRouterLinks.SIGN_UP) }
        }
        KeyStep(
            number = 2,
            title = "Add credit",
            detail = " by card, AliPay or USDC. Free models work without credit, up to 50 requests a day."
        ) {
            StepLink(text = "openrouter.ai/settings/credits") { OpenRouterLinks.open(context, OpenRouterLinks.CREDITS) }
        }
        KeyStep(
            number = 3,
            title = "Create a key",
            detail = " named \"Boox Day\" and copy it. You can give it a spending limit."
        ) {
            StepLink(text = "openrouter.ai/settings/keys") { OpenRouterLinks.open(context, OpenRouterLinks.KEYS) }
        }
        KeyStep(number = 4, title = lastStepTitle, detail = lastStepDetail, content = null)
    }
}

@Composable
private fun KeyStep(
    number: Int,
    title: String,
    detail: String?,
    content: (@Composable () -> Unit)?
) {
    Row(horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .border(1.5.dp, EInkTheme.Colors.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                fontSize = EInkTheme.Typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = androidx.compose.ui.text.buildAnnotatedString {
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
                    append(title)
                    pop()
                    if (detail != null) append(detail)
                },
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
            content?.invoke()
        }
    }
}

@Composable
private fun StepLink(text: String, onClick: () -> Unit) {
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
