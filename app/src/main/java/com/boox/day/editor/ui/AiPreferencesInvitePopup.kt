package com.boox.day.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.R
import com.boox.day.ui.components.EinkPrimaryButton
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * Proactive popup inviting users to set up AI Preferences.
 *
 * Uses progressive backoff system:
 * - Phase 1: Every 2 days (2 attempts)
 * - Phase 2: Every 4 days (2 attempts)
 * - Phase 3: Weekly (3 attempts)
 * - Deactivates after 7 total closes
 *
 * E-ink optimized: Popup (not Dialog), noRippleClickable, EInkTheme.
 */
@Composable
fun AiPreferencesInvitePopup(
    onSetUpNow: () -> Unit,
    onMaybeLater: () -> Unit,
    onDontAskAgain: () -> Unit,
    showDontAskOption: Boolean = false,
    modifier: Modifier = Modifier
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onMaybeLater,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = modifier
                .width(EInkTheme.PopupOverlay.width)
                .background(
                    EInkTheme.PopupOverlay.background,
                    RoundedCornerShape(EInkTheme.Global.cornerRadius)
                )
                .border(
                    EInkTheme.PopupOverlay.borderWidth,
                    EInkTheme.PopupOverlay.borderColor,
                    RoundedCornerShape(EInkTheme.Global.cornerRadius)
                )
        ) {
            Column(
                modifier = Modifier.padding(EInkTheme.PopupOverlay.padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Personalize Your AI",
                        fontSize = EInkTheme.PopupOverlay.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.PopupOverlay.titleColor
                    )

                    Box(
                        modifier = Modifier
                            .size(EInkTheme.Global.touchTarget)
                            .border(
                                EInkTheme.Global.borderWidth,
                                EInkTheme.Colors.gray,
                                RoundedCornerShape(EInkTheme.Global.cornerRadius)
                            )
                            .noRippleClickable { onMaybeLater() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = EInkTheme.Colors.primary,
                            modifier = Modifier.size(EInkTheme.Global.iconSize)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // AI Icon
                Icon(
                    painter = painterResource(R.drawable.ai_sparkles),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = EInkTheme.Colors.primary
                )

                Spacer(Modifier.height(16.dp))

                // Main message
                Text(
                    text = "Help your AI assistant understand you better",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))

                // Description
                Text(
                    text = "Share your priorities, interests, and lifestyle to get personalized task suggestions that fit your life.",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                // Benefits list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BenefitItem("Suggestions aligned with your priorities")
                    BenefitItem("Context-aware reminders for your lifestyle")
                    BenefitItem("Better understanding of your schedule")
                }

                Spacer(Modifier.height(20.dp))

                // Action buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EinkPrimaryButton(
                        text = "Set Up Now",
                        onClick = onSetUpNow,
                        modifier = Modifier.fillMaxWidth()
                    )

                    EinkSecondaryButton(
                        text = "Later",
                        onClick = onMaybeLater,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Show "Don't ask again" only after several dismissals
                    if (showDontAskOption) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .noRippleClickable { onDontAskAgain() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Don't ask again",
                                fontSize = EInkTheme.Typography.labelMedium,
                                color = EInkTheme.Colors.gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BenefitItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.darkGray,
            modifier = Modifier.padding(end = 8.dp, top = 2.dp)
        )
        Text(
            text = text,
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.darkGray
        )
    }
}
