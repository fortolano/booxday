package com.boox.day.ui.dialogs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.permissions.FileAccess
import com.boox.day.ui.theme.EInkTheme

/** What the files are for: the one sentence of why. */
enum class FileAccessPurpose(val why: String) {
    INSERT_IMAGE("To insert pictures, BooxDay needs to see the tablet's files (Pictures, Downloads, Documents, Camera)."),
    LINK_FILE("To link files to your notes, BooxDay needs to see the tablet's files (Library, Books, Documents, Downloads).")
}

/**
 * "Allow file access" (Oct 2026, plan B2): one step for Insert Image and Link File, which had two
 * near-identical popups that each checked every 3 seconds. Android 11 and later: Open Android
 * Settings, turn on All files access, come back; the access is read again then, and [onGranted]
 * opens the picker. Already allowed: [onGranted] at once.
 */
@Composable
fun FileAccessPopup(purpose: FileAccessPurpose, onDismiss: () -> Unit, onGranted: () -> Unit) {
    val context = LocalContext.current
    val granted by rememberUpdatedState(onGranted)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (FileAccess.canBrowse(context)) granted()
    }
    LaunchedEffect(Unit) { if (FileAccess.canBrowse(context)) granted() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && FileAccess.canBrowse(context)) granted()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Popup(alignment = Alignment.Center, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FileAccessCard(purpose, onNotNow = onDismiss, onOpenSettings = {
                FileAccess.openAllFilesSettings(context) { launcher.launch(it) }
            })
        }
    }
}

/** The popup's content; its own composable for the render test. */
@Composable
internal fun FileAccessCard(purpose: FileAccessPurpose, onNotNow: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .width(EInkTheme.PopupOverlay.width)
            .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .padding(EInkTheme.PopupOverlay.padding),
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = EInkTheme.Colors.primary, modifier = Modifier.size(24.dp))
            Text(
                text = "Allow file access",
                fontSize = EInkTheme.PopupOverlay.titleFontSize,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.PopupOverlay.titleColor
            )
        }
        Text(text = purpose.why, fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.primary)
        Text(
            text = "On the next screen, turn on Allow access to manage all files, then come back. You only do this once.",
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
            EinkSecondaryButton(text = "Not Now", onClick = onNotNow, modifier = Modifier.weight(0.35f))
            ActionButton(text = "Open Android Settings", onClick = onOpenSettings, modifier = Modifier.weight(0.65f))
        }
    }
}
