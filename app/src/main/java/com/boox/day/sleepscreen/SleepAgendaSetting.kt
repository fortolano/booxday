package com.boox.day.sleepscreen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.KvProxy
import com.boox.day.ui.components.EInkToggle
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.components.SettingCard
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Preferences card "Today on sleep screen" (Oct 2026): the switch, and a preview of the image.
 * Keeps its own state, so the Preferences dialog and MainScreen (at its method-size limit) need no
 * new parameters. Shown on BOOX tablets only.
 */
@Composable
fun SleepAgendaSetting() {
    if (!SleepAgenda.isSupported) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(GlobalAppSettings.current.sleepScreenAgendaEnabled) }
    var justTurnedOff by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var loadingPreview by remember { mutableStateOf(false) }

    SettingCard(
        title = "Today on sleep screen",
        description = "Show today's events and tasks when the tablet sleeps. It updates when they change. " +
            "Anyone who picks up the tablet can see them."
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (enabled) "Enabled" else "Disabled",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )
                EInkToggle(
                    checked = enabled,
                    onCheckedChange = { on ->
                        enabled = on
                        justTurnedOff = !on
                        KvProxy(context).setAppSettings(GlobalAppSettings.current.copy(sleepScreenAgendaEnabled = on))
                        SleepAgenda.setEnabled(context, on)
                    }
                )
            }
            // Oct 2026 (plan B2): it updates unseen, so say when (or what went wrong)
            if (enabled) {
                Text(
                    text = com.boox.day.today.LastWrite.statusText(
                        SleepScreenPublisher.lastWrite.read(context), done = "updated",
                        use24Hour = GlobalAppSettings.current.timeFormat == com.boox.day.data.datastore.AppSettings.TimeFormat.TwentyFourHour
                    ),
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.primary
                )
            }
            if (justTurnedOff) {
                Text(
                    text = "Turned off. The sleep screen shows BOOX's own image again. To pick your own: BOOX Settings, then Screensaver.",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.darkGray
                )
            }
            EinkSecondaryButton(
                text = if (loadingPreview) "Preparing preview…" else "Preview",
                width = 190.dp,
                onClick = {
                    if (!loadingPreview) {
                        loadingPreview = true
                        scope.launch {
                            preview = withContext(Dispatchers.Default) { SleepScreenPublisher.preview(context) }
                            loadingPreview = false
                        }
                    }
                }
            )
            if (enabled) ContentChoices()
        }
    }

    preview?.let { bitmap ->
        SleepAgendaPreviewPopup(bitmap) {
            preview = null
            bitmap.recycle()
        }
    }
}

/** What to show, one switch each; the last one on can't be turned off. Saved at once, and the image follows. */
@Composable
private fun ContentChoices() {
    val context = LocalContext.current
    var choice by remember { mutableStateOf(GlobalAppSettings.current.sleepScreenContent) }
    val withMemo = !com.boox.day.ui.theme.LayoutProfiles.isSmallTablet  // 6 and 7 inch: no room for it
    SleepContentRows(choice, withMemo) { changed ->
        choice = changed
        KvProxy(context).setAppSettings(GlobalAppSettings.current.copy(sleepScreenContent = changed))
        SleepAgenda.requestRefresh(context, force = true)
    }
}

@Composable
internal fun SleepContentRows(choice: SleepScreenContent, withMemo: Boolean, onChange: (SleepScreenContent) -> Unit) {
    val lastOne = choice.switchedOn(withMemo) == 1
    Column {
        Text(
            text = "What to show",
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary
        )
        ContentRow(
            "Month calendar", choice.month == true, lastOne,
            note = if (choice.month == null) "Now shown when there's room" else null
        ) { onChange(choice.copy(month = it)) }
        ContentRow("Events", choice.events, lastOne) { onChange(choice.copy(events = it)) }
        ContentRow("Tasks", choice.tasks, lastOne) { onChange(choice.copy(tasks = it)) }
        if (withMemo) ContentRow("Memo page", choice.memo, lastOne) { onChange(choice.copy(memo = it)) }
        if (lastOne) {
            Text(
                text = "Keep at least one.",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.darkGray
            )
        }
    }
}

@Composable
private fun ContentRow(label: String, on: Boolean, lastOne: Boolean, note: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = EInkTheme.Global.touchTarget),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label, fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.primary)
            note?.let { Text(text = it, fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.darkGray) }
        }
        EInkToggle(checked = on, onCheckedChange = onChange, enabled = !(on && lastOne))
    }
}

/** The sleep-screen image, scaled to fit, with a Close button. */
@Composable
private fun SleepAgendaPreviewPopup(bitmap: Bitmap, onClose: () -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Popup(alignment = Alignment.Center, onDismissRequest = onClose, properties = PopupProperties(focusable = true)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(EInkTheme.PopupOverlay.width)
                    .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.PopupOverlay.padding),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sleep screen preview",
                    fontSize = EInkTheme.PopupOverlay.titleFontSize,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.PopupOverlay.titleColor,
                    modifier = Modifier.fillMaxWidth()
                )
                Image(
                    bitmap = image,
                    contentDescription = "Today's agenda as it shows on the sleep screen",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .aspectRatio(bitmap.width.toFloat() / bitmap.height)
                        .border(1.dp, EInkTheme.Colors.primary)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    EinkSecondaryButton(text = "Close", onClick = onClose)
                }
            }
        }
    }
}
