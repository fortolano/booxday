package com.boox.day.ui.dialogs

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.R
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * First-run welcome popup shown once after installation.
 * Engaging introduction to Boox Day with feature highlights and
 * direction to badged settings for personalization.
 *
 * Part of Play Store launch onboarding (Jan 2026).
 *
 * E-Ink optimized:
 * - Uses Popup instead of Dialog (no scrim flicker)
 * - No ripple effects or animations
 * - High contrast black/white styling
 * - Clear visual hierarchy
 *
 * @param onDismiss Callback when user dismisses the popup
 */
@Composable
fun WelcomePopup(
    onDismiss: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(380.dp)
                    .background(
                        EInkTheme.Colors.background,
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        2.dp,
                        EInkTheme.Colors.primary,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large app icon (no tint to preserve original colors)
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground_vector),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = androidx.compose.ui.graphics.Color.Unspecified
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Main title
                Text(
                    text = "Welcome to Boox Day",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Tagline
                Text(
                    text = "Your e-ink planner, reimagined",
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    color = EInkTheme.Colors.gray
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Feature highlights
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            EInkTheme.Colors.offWhite,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FeatureRow(
                        icon = { Icon(Icons.Default.Draw, null, Modifier.size(20.dp), tint = EInkTheme.Colors.primary) },
                        text = "Handwrite memos & tasks with your stylus"
                    )
                    FeatureRow(
                        icon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(20.dp), tint = EInkTheme.Colors.primary) },
                        text = "Sync calendars from Google & Outlook"
                    )
                    FeatureRow(
                        icon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp), tint = EInkTheme.Colors.primary) },
                        text = "AI-powered voice input & smart suggestions"
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Setup guidance section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            EInkTheme.Colors.borderStrong,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Badge indicator row
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = EInkTheme.Colors.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Look for ",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.Colors.primary
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(EInkTheme.Colors.primary, CircleShape)
                        )
                        Text(
                            text = " badges in Settings",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.Colors.primary
                        )
                    }

                    // Encouragement text
                    Text(
                        text = "We recommend configuring these marked settings first to unlock Boox Day's full potential. When you have a moment, explore the other settings too!",
                        fontSize = 12.sp,
                        color = EInkTheme.Colors.darkGray,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )

                    // AI preferences tip
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                EInkTheme.Colors.offWhite,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = EInkTheme.Colors.primary
                        )
                        Text(
                            text = "Pro tip: Set up your AI preferences in Settings → AI Settings → AI Preferences for personalized planning and smarter suggestions.",
                            fontSize = 12.sp,
                            color = EInkTheme.Colors.darkGray,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // CTA Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(
                            EInkTheme.Colors.primary,
                            RoundedCornerShape(8.dp)
                        )
                        .noRippleClickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Let's Get Started",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EInkTheme.Colors.white
                    )
                }
            }
        }
    }
}

/**
 * Single feature row with icon and description.
 */
@Composable
private fun FeatureRow(
    icon: @Composable () -> Unit,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        icon()
        Text(
            text = text,
            fontSize = 14.sp,
            color = EInkTheme.Colors.darkGray,
            lineHeight = 18.sp
        )
    }
}
