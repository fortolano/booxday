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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.api.OpenRouterConnect
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.ui.AiKeyGate
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.launch

/** OpenRouter pages the steps link to (checked Oct 2026). */
object OpenRouterLinks {
    const val SIGN_UP = "https://openrouter.ai/sign-up"
    const val CREDITS = "https://openrouter.ai/settings/credits"
    const val KEYS = "https://openrouter.ai/settings/keys"
    const val BYOK = "https://openrouter.ai/settings/integrations"

    /** Opens [url] in the tablet's browser. False when there is no browser to open it. */
    fun open(context: Context, url: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        Log.w("OpenRouterLinks", "No browser: ${e.javaClass.simpleName}")
        false
    }

    const val NO_BROWSER = "Couldn't open a web browser on this tablet. You can paste an OpenRouter key here instead."

    /**
     * Starts Connect OpenRouter in the browser (Oct 2026). Without a browser the started connection
     * is dropped and the key card says why: before, nothing happened and nothing was said.
     */
    suspend fun connect(context: Context): Boolean {
        if (open(context, OpenRouterConnect.begin(context))) return true
        OpenRouterConnect.discardPending(context)
        OpenRouterConnect.reportProblem(NO_BROWSER)
        return false
    }
}

/**
 * MainScreen's host for the key popups ([AiKeyGate]): the OpenRouter introduction while the gate is
 * open, and afterwards the Connect OpenRouter result ("AI is on", or what went wrong) wherever the
 * user is, after a restart too. The waiting feature goes on from there. While AI Settings is open
 * ([aiSettingsOpen]) its key card shows the result instead. Calls [onOpenAiSettings] once after
 * "I already have a key". Its own composable keeps MainScreen's root lambda small.
 */
@Composable
fun AiKeyGateHost(aiSettingsOpen: Boolean, onOpenAiSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reason = AiKeyGate.openReason
    if (reason != null) {
        OpenRouterIntroPopup(
            rejected = reason == AiKeyGate.Reason.KEY_REJECTED,
            onNotNow = { AiKeyGate.close() },
            onContinue = {
                AiKeyGate.closeForConnect()
                // No browser: open AI Settings, where the key card says so and offers the paste field
                scope.launch { if (!OpenRouterLinks.connect(context)) AiKeyGate.requestAddKey() }
            },
            onHaveKey = { AiKeyGate.requestAddKey() }
        )
    }

    val resultCount = OpenRouterConnect.resultCount
    LaunchedEffect(resultCount, aiSettingsOpen) {
        if (resultCount <= AiKeyGate.handledResult) return@LaunchedEffect
        if (aiSettingsOpen && AiKeyGate.shownResult == null) AiKeyGate.markResultHandled(resultCount)
        else AiKeyGate.showResult(resultCount)
    }
    if (AiKeyGate.shownResult != null) {
        val status = OpenRouterConnect.status
        var retrying by remember { mutableStateOf(false) }
        LaunchedEffect(status) { if (status !is OpenRouterConnect.Status.Connecting) retrying = false }
        PopupFrame(onDismiss = { AiKeyGate.closeResult(resume = false) }) {
            ConnectResultCard(
                status = status,
                aiEnabled = GlobalAppSettings.current.aiEnabled,
                featureWaiting = AiKeyGate.hasPendingAction,
                retrying = retrying,
                onClose = { AiKeyGate.closeResult(resume = false) },
                onContinue = { AiKeyGate.closeResult(resume = true) },
                onTryAgain = {
                    retrying = true
                    scope.launch { OpenRouterConnect.retry(context) }
                },
                onConnectAgain = {
                    AiKeyGate.closeResultKeepWaiting()
                    scope.launch { if (!OpenRouterLinks.connect(context)) AiKeyGate.requestAddKey() }
                }
            )
        }
    }

    val addKeyPending = AiKeyGate.addKeyPending
    LaunchedEffect(addKeyPending) {
        if (addKeyPending && AiKeyGate.consumeAddKey()) onOpenAiSettings()
    }
}

/**
 * "Turn on AI in BooxDay" (Oct 2026): what OpenRouter is, what it costs and what happens next, before
 * the browser opens. Shown when an AI feature starts without a key (or OpenRouter rejected it) and by
 * Connect OpenRouter in AI Settings. [onHaveKey] null: no "I already have a key" line (the key card
 * has its own).
 */
@Composable
fun OpenRouterIntroPopup(
    rejected: Boolean,
    onNotNow: () -> Unit,
    onContinue: () -> Unit,
    onHaveKey: (() -> Unit)?
) {
    PopupFrame(onDismiss = onNotNow) {
        OpenRouterIntroCard(rejected, onNotNow, onContinue, onHaveKey)
    }
}

/** A centred popup; [content] is an [OpenRouterIntroCard] or a [ConnectResultCard]. */
@Composable
private fun PopupFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun PopupColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .width(EInkTheme.PopupOverlay.width)
            .heightIn(max = EInkTheme.PopupOverlay.maxHeight)
            .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .padding(EInkTheme.PopupOverlay.padding),
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium),
        content = content
    )
}

@Composable
private fun PopupTitle(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = EInkTheme.Colors.primary, modifier = Modifier.size(24.dp))
        Text(
            text = text,
            fontSize = EInkTheme.PopupOverlay.titleFontSize,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.PopupOverlay.titleColor
        )
    }
}

@Composable
private fun PopupText(text: String, bold: Boolean = false) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyMedium,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = EInkTheme.Colors.primary
    )
}

/** A question in bold, then its answer. */
@Composable
private fun QuestionAndAnswer(question: String, answer: String) {
    Column {
        PopupText(question, bold = true)
        PopupText(answer)
    }
}

/** The introduction's content; its own composable for the render test. */
@Composable
internal fun OpenRouterIntroCard(
    rejected: Boolean,
    onNotNow: () -> Unit,
    onContinue: () -> Unit,
    onHaveKey: (() -> Unit)?
) {
    PopupColumn {
        PopupTitle(Icons.Default.AutoAwesome, "Turn on AI in BooxDay")
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
        ) {
            if (rejected) {
                PopupText("OpenRouter did not accept your saved key. Connect again to get a new one.", bold = true)
            }
            PopupText(
                "BooxDay can plan your day, read your handwriting and suggest tasks with AI. The AI comes " +
                    "from companies like Google and OpenAI, through a service called OpenRouter."
            )
            QuestionAndAnswer(
                "What is OpenRouter?",
                "One account for many AI models. BooxDay connects to your account. It has no server of its own."
            )
            QuestionAndAnswer(
                "Is it free?",
                // Measured 29 Oct 2026 with BooxDay's models: 6 of 7 actions under a cent (Voice to Task 1.7)
                "Signing up is free. Free models allow 50 requests a day and are often busy. Paid models use " +
                    "credit you add on OpenRouter, from \$5; with BooxDay's choices most actions cost less than a cent."
            )
            QuestionAndAnswer(
                "What happens next?",
                "OpenRouter opens in your browser. Sign in or create an account, tap Authorize, then Open BooxDay."
            )
        }
        // The main button is wider: "Continue to OpenRouter" must fit on the Go 6 too
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
        ) {
            EinkSecondaryButton(text = "Not Now", onClick = onNotNow, modifier = Modifier.weight(0.35f))
            ActionButton(text = "Continue to OpenRouter", onClick = onContinue, modifier = Modifier.weight(0.65f))
        }
        if (onHaveKey != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = EInkTheme.Global.touchTarget)
                    .noRippleClickable { onHaveKey() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "I already have a key",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary,
                    textDecoration = TextDecoration.Underline
                )
            }
        }
    }
}

/**
 * The Connect OpenRouter result: "AI is on" (with Continue when a feature waits for it), or why it
 * didn't connect, with Try Again (the same code, no browser) or Connect Again.
 */
@Composable
internal fun ConnectResultCard(
    status: OpenRouterConnect.Status,
    aiEnabled: Boolean,
    featureWaiting: Boolean,
    retrying: Boolean,
    onClose: () -> Unit,
    onContinue: () -> Unit,
    onTryAgain: () -> Unit,
    onConnectAgain: () -> Unit
) {
    PopupColumn {
        when (status) {
            is OpenRouterConnect.Status.Connected -> {
                PopupTitle(Icons.Default.Check, "AI is on")
                PopupText("BooxDay is connected to your OpenRouter account.")
                when {
                    !status.checked -> PopupText(
                        "OpenRouter couldn't be reached to check the key. It's saved; BooxDay checks it the next time you use AI."
                    )
                    status.isFreeTier -> PopupText(
                        "No credit yet: AI uses free models, up to 50 requests a day. Search indexing starts when you add credit."
                    )
                }
                if (!aiEnabled) PopupText("AI Features is off in AI Settings, so nothing is sent to OpenRouter yet.", bold = true)
                if (featureWaiting && aiEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                    ) {
                        EinkSecondaryButton(text = "Not Now", onClick = onClose, modifier = Modifier.weight(0.4f))
                        ActionButton(text = "Continue", onClick = onContinue, modifier = Modifier.weight(0.6f))
                    }
                } else {
                    ActionButton(text = "Close", onClick = onClose, modifier = Modifier.fillMaxWidth())
                }
            }
            is OpenRouterConnect.Status.Failed -> {
                PopupTitle(Icons.Default.Info, "Not connected")
                PopupText(status.message)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                ) {
                    EinkSecondaryButton(text = "Close", onClick = onClose, modifier = Modifier.weight(0.4f))
                    if (status.canRetry) {
                        ActionButton(
                            text = if (retrying) "Connecting…" else "Try Again",
                            onClick = { if (!retrying) onTryAgain() },
                            modifier = Modifier.weight(0.6f)
                        )
                    } else {
                        ActionButton(text = "Connect Again", onClick = onConnectAgain, modifier = Modifier.weight(0.6f))
                    }
                }
            }
            else -> {
                // Try Again is running
                PopupTitle(Icons.Default.Info, "Connecting to OpenRouter…")
                ActionButton(text = "Close", onClick = onClose, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * The four steps to an OpenRouter key, with links: the key card's "I already have a key" (the last
 * step says where to paste).
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
            detail = " for paid models (Help → AI Features → Paying for AI). Free models work without credit, up to 50 requests a day."
        ) {
            StepLink(text = "openrouter.ai/settings/credits") { OpenRouterLinks.open(context, OpenRouterLinks.CREDITS) }
        }
        KeyStep(
            number = 3,
            title = "Create a key",
            detail = " named \"BooxDay\" and copy it. You can give it a spending limit."
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
