package com.boox.day.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.datastore.SetupChecklistStore
import com.boox.day.ui.components.AiModelsCard
import com.boox.day.ui.components.AiOptionsCard
import com.boox.day.ui.components.ApiKeySettingsCard
import com.boox.day.ui.components.EInkToggle
import com.boox.day.ui.components.ScrollableWithIndicator
import com.boox.day.ui.components.SettingCard
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * AI Settings Dialog - Consolidates all AI-related settings.
 *
 * Contents (Oct 2026):
 * - OpenRouter key: every AI feature runs on the user's own key, so it comes first
 * - AI Features: master toggle
 * - AI Models: the OpenRouter model each AI job uses
 * - AI options: how many suggestions, Vacation, Health and Social suggestions (ui/components/AiOptionsCard)
 * - Privacy: where requests go, BYOK link
 *
 * E-Ink optimized: Uses Popup (not Dialog), noRippleClickable, EInkTheme constants.
 */
@Composable
fun AiSettingsDialog(
    onDismiss: () -> Unit,
    // AI Features master toggle
    aiEnabled: Boolean,
    onAiEnabledChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current

    // Oct 2026: opening AI Settings clears its Settings-menu dot (it only cleared on the AI switch,
    // so most users kept a "1" on the gear); covers the menu, the key popup and the calendar notice
    LaunchedEffect(Unit) {
        SetupChecklistStore.mark(context) { it.copy(aiSettingsReviewed = true) }
    }

    // Oct 2026 (plan B3): opened from the Settings dot for a failing model pick, show AI Models at
    // once (without a key, the key card is already at the top)
    val modelsInView = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        if (com.boox.day.ui.attention.SettingsJump.consume(com.boox.day.ui.attention.SettingsPlace.AI_SETTINGS) &&
            com.boox.day.security.UserOpenRouterKey.hasKey && com.boox.day.api.AiPickStatus.failures.isNotEmpty()
        ) {
            kotlinx.coroutines.delay(150)  // laid out first
            modelsInView.bringIntoView()
        }
    }

    // E-Ink: Use Popup instead of Dialog to avoid system scrim/dimming
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.PanelDialog.widthFraction)  // Oct 2026: was 0.85f
                    .fillMaxHeight(EInkTheme.PanelDialog.heightFraction)  // was 0.75f
                    .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.PopupOverlay.padding)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = EInkTheme.Colors.primary
                        )
                        Text(
                            text = "AI Settings",
                            fontSize = EInkTheme.PopupOverlay.titleFontSize,
                            fontWeight = FontWeight.Bold,
                            color = EInkTheme.PopupOverlay.titleColor
                        )
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(EInkTheme.Button.height),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = EInkTheme.Button.textColor
                        ),
                        border = BorderStroke(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor),
                        shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                    ) {
                        Text(
                            text = "Close",
                            fontSize = EInkTheme.Typography.button,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Content
                ScrollableWithIndicator(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
                    ) {
                        // The user's own OpenRouter key (Oct 2026): AI features need it, so it comes first
                        ApiKeySettingsCard()

                        // AI Features Master Toggle
                        SettingCard(
                            title = "AI Features",
                            description = "Voice input, the AI tools in Memos and Tasks (such as Recognize Handwriting, Summarize, Translate and Smart Suggestions), and reading pages for search."
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (aiEnabled) "On" else "Off",
                                        fontSize = EInkTheme.Typography.bodyMedium,
                                        color = EInkTheme.Colors.darkGray
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (aiEnabled)
                                            "When you use an AI tool, its page images, text or voice clip are sent to OpenRouter, on your own account, to be read or answered."
                                        else
                                            "Voice input, the AI tools and reading pages for search are off. None of your data goes to OpenRouter.",
                                        fontSize = EInkTheme.Preferences.explanationFontSize,
                                        color = EInkTheme.Colors.gray
                                    )
                                }
                                EInkToggle(
                                    checked = aiEnabled,
                                    onCheckedChange = onAiEnabledChanged
                                )
                            }
                        }

                        // Oct 2026: which OpenRouter model each AI job uses
                        AiModelsCard(aiEnabled = aiEnabled, modifier = Modifier.bringIntoViewRequester(modelsInView))

                        // Oct 2026: the three AI options that measurably change answers (replaced AI Preferences)
                        AiOptionsCard(aiEnabled = aiEnabled)

                        // Privacy (Oct 2026): where AI requests go. Getting a key is the key card's job
                        SettingCard(
                            title = "Privacy",
                            description = "BooxDay sends nothing to its developer. AI requests go from this tablet to OpenRouter, on your own account, and to the company that runs the model. Reading pages for search also sends memo and Boox Notes pages, while Settings → Boox Notes → Enable Indexing is on. With AI Features off, none of your data goes to OpenRouter."
                        ) {}
                    }
                }
            }
        }
    }
}
