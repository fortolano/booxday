package com.boox.day.ui.dialogs

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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.diagnostics.CrashReport
import com.boox.day.diagnostics.CrashReportGate
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.theme.EInkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MainScreen's host for the crash popup: shows it while [CrashReportGate] holds a report.
 * Its own composable, outside MainScreen's root lambda (at the JVM method-size limit).
 */
@Composable
fun CrashReportHost() {
    val report = CrashReportGate.report
    if (report != null) CrashReportPopup(report)
}

/**
 * "Boox Day closed unexpectedly" (Oct 2026): shown once at the start after a crash. Buttons follow
 * docs/DIALOG_RULEBOOK.md: Close (escape, left), Copy Report, Report on GitHub (main, right).
 */
@Composable
private fun CrashReportPopup(report: CrashReport) {
    val context = LocalContext.current
    val whenText = SimpleDateFormat("MMM d 'at' HH:mm", Locale.getDefault()).format(Date(report.crashedAtMs))
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = { CrashReportGate.close(context) },
        properties = PopupProperties(focusable = true)
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(EInkTheme.PopupOverlay.width)
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
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = EInkTheme.Colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Boox Day closed unexpectedly",
                        fontSize = EInkTheme.PopupOverlay.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.PopupOverlay.titleColor
                    )
                }
                Body("It stopped on $whenText. A report was saved on this tablet.")
                Body(
                    "Sending it helps fix the problem. It has the error details and recent app " +
                        "messages, not your memos or tasks."
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                ) {
                    EinkSecondaryButton(
                        text = "Close",
                        onClick = { CrashReportGate.close(context) },
                        modifier = Modifier.weight(1f)
                    )
                    EinkSecondaryButton(
                        text = "Copy Report",
                        onClick = { CrashReportGate.copy(context) },
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        text = "Report on GitHub",
                        onClick = { CrashReportGate.reportOnGitHub(context) },
                        modifier = Modifier.weight(1.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text = text, fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.primary)
}
