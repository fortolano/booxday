package com.boox.day.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.KvProxy
import com.boox.day.ui.components.EInkToggle
import com.boox.day.ui.components.SettingCard
import com.boox.day.ui.theme.EInkTheme

/**
 * Preferences card "Tasks feed for other apps" (Phase 1 H16 / Decisions D1 & D9).
 * Off by default so other installed apps cannot read task text unless the user opts in.
 * Self-contained state so PreferencesDialog and MainScreen need no new parameters.
 */
@Composable
fun TasksFeedSetting() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(GlobalAppSettings.current.tasksFeedEnabled) }

    SettingCard(
        title = "Tasks feed for other apps",
        description = "Let launchers, widgets and other apps on this tablet read your task text and " +
            "due dates (read-only). Off by default; BooxDay's sleep screen and daily notes always work."
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (enabled) "Shared (read-only)" else "Off",
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.darkGray
            )
            EInkToggle(
                checked = enabled,
                onCheckedChange = { on ->
                    enabled = on
                    KvProxy(context).setAppSettings(GlobalAppSettings.current.copy(tasksFeedEnabled = on))
                    runCatching { context.contentResolver.notifyChange(TasksFeedContract.BASE_URI, null) }
                }
            )
        }
    }
}
