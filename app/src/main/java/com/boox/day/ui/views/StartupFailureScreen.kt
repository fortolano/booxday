package com.boox.day.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boox.day.startup.StartupFailureReport

@Composable
fun StartupFailureScreen(
    report: StartupFailureReport,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val reportData = remember(context) {
        val startupTags = listOf(
            "BooxDayApp",
            "MainActivity",
            "DrawCanvas",
            "OnyxCompat",
            "StartupDiagnostics"
        )
        ReportData(
            context = context,
            selectedTags = startupTags.associateWith { true },
            includeLibrariesLogs = false
        )
    }
    val supportDescription = remember(report) { report.toSupportDescription() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.White)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "BooxDay couldn't finish opening on this device.",
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Stage: ${report.stage.label}")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = report.summary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Exception: ${report.throwableClass}")
        report.throwableMessage?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Message: $it")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "BooxDay is free, open-source software provided as is, with no support service. You can copy this report and post it on the project's GitHub Issues page.")
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    reportData.copyReportToClipboard(context, supportDescription, includeLogs = true)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Copy Report")
            }
            Button(
                onClick = {
                    // The issues page, not a prefilled issue: the report with logs is too long for a URL
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(com.boox.day.ProjectInfo.ISSUES_URL)
                            )
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Open GitHub Issues")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Retry")
        }
    }
}
