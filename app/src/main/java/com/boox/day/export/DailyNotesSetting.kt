package com.boox.day.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.KvProxy
import com.boox.day.ui.components.EInkToggle
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.components.SettingCard
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Preferences card "Daily notes (Markdown)" (Oct 2026): the switch, the folder, and writing
 * today's note now. Keeps its own state, so the Preferences dialog and MainScreen need no new
 * parameters.
 */
@Composable
fun DailyNotesSetting() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(GlobalAppSettings.current.dailyNotesEnabled) }
    var folder by remember { mutableStateOf(DailyNoteWriter.folderLabel(context)) }
    var hasChosenFolder by remember { mutableStateOf(DailyNoteWriter.chosenFolder(context) != null) }
    var status by remember { mutableStateOf<String?>(null) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            runCatching { DailyNoteWriter.chooseFolder(context, tree) }
                .onSuccess {
                    folder = DailyNoteWriter.folderLabel(context)
                    hasChosenFolder = true
                    status = null
                }
                .onFailure { status = "That folder can't be used. Please choose another one." }
        }
    }

    SettingCard(
        title = "Daily notes (Markdown)",
        description = "A text file for each day with your events, tasks and memo text, for Obsidian or other " +
            "notes apps. Text you add outside BooxDay's part of the file is kept."
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (enabled) "Written for each day" else "Disabled",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )
                EInkToggle(
                    checked = enabled,
                    onCheckedChange = { on ->
                        enabled = on
                        KvProxy(context).setAppSettings(GlobalAppSettings.current.copy(dailyNotesEnabled = on))
                        DailyNotes.setEnabled(context, on)
                    }
                )
            }
            // Oct 2026 (plan B2): the notes are written unseen, so say when (or what went wrong)
            if (enabled) {
                Text(
                    text = com.boox.day.today.LastWrite.statusText(
                        DailyNotes.lastWrite.read(context), done = "written",
                        use24Hour = GlobalAppSettings.current.timeFormat == com.boox.day.data.datastore.AppSettings.TimeFormat.TwentyFourHour
                    ),
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.primary
                )
            }
            Text(
                text = "Folder: $folder",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
                EinkSecondaryButton(text = "Choose Folder", onClick = { pickFolder.launch(null) }, width = 150.dp)
                if (hasChosenFolder) {
                    EinkSecondaryButton(
                        text = "Use Default",
                        onClick = {
                            DailyNoteWriter.useDefaultFolder(context)
                            folder = DailyNoteWriter.folderLabel(context)
                            hasChosenFolder = false
                        },
                        width = 130.dp
                    )
                }
                EinkSecondaryButton(
                    text = "Write Today's Note",
                    onClick = {
                        status = "Writing…"
                        scope.launch {
                            status = runCatching { DailyNotes.writeNote(context, LocalDate.now()) }
                                .fold({ "Saved $it" }, { "Not saved: ${it.message ?: "the folder can't be written to"}" })
                        }
                    },
                    width = 190.dp
                )
            }
            status?.let {
                Text(text = it, fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.darkGray)
            }
        }
    }
}
