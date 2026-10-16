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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.components.AiModelsCard
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
 * - AI Personalization: button to open AiPreferencesDialog wizard
 * - Privacy: where requests go, BYOK link
 *
 * E-Ink optimized: Uses Popup (not Dialog), noRippleClickable, EInkTheme constants.
 */
@Composable
fun AiSettingsDialog(
    onDismiss: () -> Unit,
    // AI Features master toggle
    aiEnabled: Boolean,
    onAiEnabledChanged: (Boolean) -> Unit,
    // Open AI Preferences wizard
    onOpenAiPreferences: () -> Unit
) {
    val context = LocalContext.current

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
                            contentDescription = "AI Assistant",
                            modifier = Modifier.size(24.dp),
                            tint = EInkTheme.Colors.primary
                        )
                        Text(
                            text = "AI Assistant",
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
                            description = "Enable AI-powered tools like voice input, handwriting recognition, summaries, and smart suggestions"
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (aiEnabled) "Enabled" else "Disabled",
                                        fontSize = EInkTheme.Typography.bodyMedium,
                                        color = EInkTheme.Colors.darkGray
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (aiEnabled)
                                            "Semantic insights (keywords, patterns) are sent to AI providers. Your full notes stay on your device."
                                        else
                                            "Voice input and AI tools are turned off",
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
                        AiModelsCard(aiEnabled = aiEnabled)

                        // Personalization Section
                        SettingCard(
                            title = "AI Personalization",
                            description = "Configure your preferences to get smarter, more relevant suggestions tailored to your lifestyle"
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                            ) {
                                Text(
                                    text = "Tell us about your priorities, schedule, and interests so AI features can provide personalized assistance.",
                                    fontSize = EInkTheme.Preferences.explanationFontSize,
                                    color = EInkTheme.Colors.gray
                                )

                                Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

                                // Configure Preferences Button
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(EInkTheme.Button.height)
                                        .background(
                                            if (aiEnabled) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                                            RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                                        )
                                        .noRippleClickable(enabled = aiEnabled) {
                                            onOpenAiPreferences()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Configure AI Preferences",
                                            fontSize = EInkTheme.Typography.button,
                                            fontWeight = FontWeight.Medium,
                                            color = if (aiEnabled) EInkTheme.Colors.white else EInkTheme.Colors.gray
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = if (aiEnabled) EInkTheme.Colors.white else EInkTheme.Colors.gray
                                        )
                                    }
                                }

                                if (!aiEnabled) {
                                    Text(
                                        text = "Enable AI Features above to configure preferences",
                                        fontSize = EInkTheme.Preferences.explanationFontSize,
                                        color = EInkTheme.Colors.gray
                                    )
                                }
                            }
                        }

                        // Privacy (Oct 2026): where AI requests go
                        SettingCard(
                            title = "Privacy",
                            description = "Requests go from this tablet to OpenRouter and the provider that runs the model."
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = EInkTheme.Global.touchTarget)
                                    .noRippleClickable { OpenRouterLinks.open(context, OpenRouterLinks.BYOK) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = "Open in browser",
                                    tint = EInkTheme.Colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Use your own Google, OpenAI or Anthropic keys (BYOK)",
                                    fontSize = EInkTheme.Typography.bodyMedium,
                                    color = EInkTheme.Colors.primary,
                                    textDecoration = TextDecoration.Underline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
