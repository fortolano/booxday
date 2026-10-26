package com.boox.day.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boox.day.api.AiJob
import com.boox.day.api.AiPickStatus
import com.boox.day.api.ModelConfigProvider
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.api.OpenRouterAccount
import com.boox.day.ui.dialogs.ModelPickerPopup
import com.boox.day.ui.noRippleClickable
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

/** What the job covers, at the top of its model list. */
val AiJob.description: String
    get() = when (this) {
        AiJob.HANDWRITING -> "Reads your ink for Recognize Handwriting, Summarize, Translate and Convert Memo to Tasks."
        AiJob.VOICE -> "Turns your speech into text for voice notes, events, tasks and preferences."
        AiJob.WRITING -> "Formats, summarizes and translates text, and turns memos into tasks."
        AiJob.PLANNING -> "Task suggestions and breakdowns, planning, voice events, and linking notes to events."
        AiJob.SEARCH_INDEXING -> "Reads pages in the background so search can find them. Only providers that keep no copy of your pages."
    }

/**
 * AI Models (Oct 2026, plan steps c and d): which OpenRouter model each AI job uses right now, the
 * user's pick or Boox Day's choice (ModelConfigProvider). Each row opens the job's model list
 * ([ModelPickerPopup]). Greyed out, and not tappable, while AI Features is off.
 *
 * Reads Compose state (the settings, OpenRouterAccount and the model list), so a new pick, the
 * account running out of credit or a pick OpenRouter dropped shows at once.
 */
@Composable
fun AiModelsCard(aiEnabled: Boolean, modifier: Modifier = Modifier) {
    val textColor = if (aiEnabled) EInkTheme.Colors.primary else EInkTheme.Colors.gray
    val modelColor = if (aiEnabled) EInkTheme.Colors.darkGray else EInkTheme.Colors.gray
    var openJob by remember { mutableStateOf<AiJob?>(null) }

    openJob?.let { job -> ModelPickerPopup(job = job, onDismiss = { openJob = null }) }

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = EInkTheme.Global.touchTarget)
                        .noRippleClickable(enabled = aiEnabled) { openJob = job },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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
                        inUse.unavailablePick?.let { pick ->
                            Text(
                                text = "$pick is no longer available, so Boox Day's choice is used.",
                                fontSize = EInkTheme.Typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }
                        // Plan step f: the pick failed last time and Boox Day's models answered
                        val failure = if (inUse.isUserChoice) {
                            AiPickStatus.failureFor(job, GlobalAppSettings.current.aiModels.forJob(job)?.id)
                        } else null
                        failure?.let {
                            Text(
                                text = "Didn't answer last time (${it.reason}), so Boox Day's choice was used.",
                                fontSize = EInkTheme.Typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Choose the model for ${job.label}",
                        tint = modelColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
