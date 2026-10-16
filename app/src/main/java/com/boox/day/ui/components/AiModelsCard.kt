package com.boox.day.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.boox.day.api.AiJob
import com.boox.day.api.ModelConfigProvider
import com.boox.day.api.OpenRouterAccount
import com.boox.day.ui.theme.EInkTheme

/** The job's name in AI Settings (Oct 2026). */
val AiJob.label: String
    get() = when (this) {
        AiJob.HANDWRITING -> "Reading handwriting"
        AiJob.VOICE -> "Voice"
        AiJob.WRITING -> "Writing and summaries"
        AiJob.PLANNING -> "Planning and suggestions"
        AiJob.SEARCH_INDEXING -> "Search indexing"
    }

/**
 * AI Models (Oct 2026, plan step c): which OpenRouter model each AI job uses right now, the user's
 * pick or Boox Day's choice (ModelConfigProvider). Read-only until the model list exists (step d);
 * then each row opens it. Greyed out while AI Features is off.
 *
 * Reads Compose state (the settings and OpenRouterAccount), so a new pick or the account running
 * out of credit shows at once.
 */
@Composable
fun AiModelsCard(aiEnabled: Boolean, modifier: Modifier = Modifier) {
    val textColor = if (aiEnabled) EInkTheme.Colors.primary else EInkTheme.Colors.gray
    val modelColor = if (aiEnabled) EInkTheme.Colors.darkGray else EInkTheme.Colors.gray

    SettingCard(
        title = "AI Models",
        description = "Choose the model for each job. If it fails, Boox Day uses its own.",
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)) {
            if (OpenRouterAccount.isFreeOnly()) {
                Text(
                    text = "No credit: Boox Day's choice uses free models.",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
            for (job in AiJob.entries) {
                val inUse = ModelConfigProvider.modelInUse(job)
                val source = when {
                    inUse.waitsForCredit -> "waits for credit"
                    inUse.isUserChoice -> "Your choice"
                    else -> "Boox Day's choice"
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = EInkTheme.Global.touchTarget),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = job.label,
                        fontSize = EInkTheme.Typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = textColor
                    )
                    Text(
                        text = "${inUse.name} · $source",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = modelColor
                    )
                }
            }
        }
    }
}
