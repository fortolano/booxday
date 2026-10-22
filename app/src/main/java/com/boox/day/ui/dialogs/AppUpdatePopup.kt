package com.boox.day.ui.dialogs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.BuildConfig
import com.boox.day.ProjectInfo
import com.boox.day.R
import com.boox.day.update.AppUpdateChecker
import com.boox.day.update.AppUpdateGate
import com.boox.day.update.AppUpdateGate.State
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * MainScreen's host for the update popup: shows it while [AppUpdateGate] has something to say.
 * Its own composable, outside MainScreen's root lambda (at the JVM method-size limit).
 */
@Composable
fun AppUpdateHost() {
    val state = AppUpdateGate.state
    if (state !is State.Idle) AppUpdatePopup(state)
}

/**
 * The in-app update popup (Oct 2026): offer, one-time permission, download, install, result.
 * Plain words for non-technical users; buttons follow docs/DIALOG_RULEBOOK.md (escape left, main
 * action right).
 */
@Composable
private fun AppUpdatePopup(state: State) {
    val context = LocalContext.current
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = {
            when (state) {
                is State.Available -> AppUpdateGate.notNow(context)
                is State.Checking, is State.UpToDate, is State.Failed -> AppUpdateGate.close()
                else -> Unit  // downloading or installing: only the buttons close it
            }
        },
        properties = PopupProperties(focusable = true)
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(EInkTheme.PopupOverlay.width)
                    .heightIn(max = EInkTheme.PopupOverlay.maxHeight)
                    .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.PopupOverlay.padding),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
            ) {
                when (state) {
                    State.Idle -> Unit
                    State.Checking -> {
                        Title("Checking for updates")
                        Body("Asking for the latest version of Boox Day…")
                        Buttons(escape = "Close" to { AppUpdateGate.close() })
                    }
                    State.UpToDate -> {
                        Title("Boox Day is up to date")
                        Body(stringResource(R.string.app_latest_version) + " (version ${BuildConfig.VERSION_NAME})")
                        Buttons(main = "Close" to { AppUpdateGate.close() })
                    }
                    is State.Available -> {
                        Title("Update available")
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                        ) {
                            Body("Boox Day ${state.info.version} is ready, with performance improvements and bug fixes.")
                            if (state.info.notes.isNotEmpty()) {
                                Text(
                                    text = "What's new",
                                    fontSize = EInkTheme.Typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = EInkTheme.Colors.primary
                                )
                                state.info.notes.forEach { Bullet(it) }
                            }
                            Body("${megabytes(state.info)} download. Your memos, tasks and settings stay as they are.")
                        }
                        Buttons(
                            escape = "Not Now" to { AppUpdateGate.notNow(context) },
                            main = "Update Now" to { AppUpdateGate.updateNow(context) }
                        )
                    }
                    is State.NeedsPermission -> {
                        Title("One-time permission")
                        Body("To install updates, Boox Day needs your permission once:")
                        Bullet("Tap Open Settings.")
                        Bullet("Turn on \"Allow from this source\".")
                        Bullet("Come back to Boox Day. The update then starts by itself.")
                        Buttons(
                            escape = "Not Now" to { AppUpdateGate.notNow(context) },
                            main = "Open Settings" to { AppUpdateGate.openInstallPermission(context) }
                        )
                    }
                    is State.Downloading -> {
                        Title("Downloading update")
                        Body("Boox Day ${state.info.version}: ${state.percent}%")
                        ProgressBar(state.percent)
                        Body("This takes a minute or two on Wi-Fi.")
                        Buttons(escape = "Cancel" to { AppUpdateGate.cancel() })
                    }
                    is State.Installing -> {
                        Title("Installing update")
                        Body(
                            "Boox Day will close and update itself. If Android asks, tap Update. " +
                                "Then open Boox Day again."
                        )
                        Buttons(escape = "Close" to { AppUpdateGate.close() })
                    }
                    is State.Failed -> {
                        Title(if (state.info == null) "Couldn't check for updates" else "Update not installed")
                        Body(state.message)
                        if (state.info != null) {
                            Buttons(
                                escape = "Close" to { AppUpdateGate.close() },
                                main = "Try Again" to { AppUpdateGate.updateNow(context) }
                            )
                            WebsiteLink {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(ProjectInfo.RELEASES_URL))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        } else {
                            Buttons(main = "Close" to { AppUpdateGate.close() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
    ) {
        Icon(
            imageVector = Icons.Default.SystemUpdate,
            contentDescription = null,
            tint = EInkTheme.Colors.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = text,
            fontSize = EInkTheme.PopupOverlay.titleFontSize,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.PopupOverlay.titleColor
        )
    }
}

@Composable
private fun Body(text: String) {
    Text(text = text, fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.primary)
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
        Text(text = "•", fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.primary)
        Text(text = text, fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.primary, modifier = Modifier.weight(1f))
    }
}

/** Escape button on the left, main action on the right (either may be absent). */
@Composable
private fun Buttons(escape: Pair<String, () -> Unit>? = null, main: Pair<String, () -> Unit>? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing, Alignment.End)
    ) {
        if (escape != null) {
            EinkSecondaryButton(
                text = escape.first,
                onClick = escape.second,
                modifier = if (main != null) Modifier.weight(1f) else Modifier
            )
        }
        if (main != null) {
            ActionButton(
                text = main.first,
                onClick = main.second,
                modifier = if (escape != null) Modifier.weight(1f) else Modifier
            )
        }
    }
}

/** A plain bar: black fill on a bordered white track (no animation, e-ink friendly). */
@Composable
private fun ProgressBar(percent: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .border(1.dp, EInkTheme.Colors.primary, RoundedCornerShape(4.dp))
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .fillMaxHeight()
                .background(EInkTheme.Colors.primary, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun WebsiteLink(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = EInkTheme.Global.touchTarget)
            .noRippleClickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Download it from the website instead",
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary,
            textDecoration = TextDecoration.Underline
        )
    }
}

private fun megabytes(info: AppUpdateChecker.UpdateInfo): String = "${(info.sizeBytes + 512 * 1024) / (1024 * 1024)} MB"
