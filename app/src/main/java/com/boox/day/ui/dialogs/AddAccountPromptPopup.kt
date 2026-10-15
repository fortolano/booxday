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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.R
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * Popup shown after user enables Cloud Sync mode.
 * Prompts them to add a cloud calendar account now or later.
 *
 * Enhancement #1: Auto-prompt after toggling to Cloud Sync.
 *
 * E-Ink optimized:
 * - Uses Popup instead of Dialog (no scrim flicker)
 * - No ripple effects
 * - High contrast black/white styling
 * - Clear call-to-action
 *
 * @param onAddAccount Callback when user chooses to add an account now
 * @param onLater Callback when user chooses to add an account later
 */
@Composable
fun AddAccountPromptPopup(
    onAddAccount: () -> Unit,
    onLater: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onLater,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .background(
                        EInkTheme.Colors.background,
                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                    .border(
                        EInkTheme.Global.borderWidth,
                        EInkTheme.Colors.primary,
                        RoundedCornerShape(EInkTheme.Global.cornerRadius)
                    )
                    .padding(EInkTheme.Global.padding)
            ) {
                // Success header
                Row(
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                EInkTheme.Colors.primary,
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = EInkTheme.Colors.white,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Calendar Sync Enabled!",
                        fontSize = EInkTheme.Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.primary
                    )
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Explanation
                Text(
                    text = "Cloud Sync is now enabled. Connect Google or Outlook to see your cloud calendars alongside your on-device calendars.",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

                Text(
                    text = "Would you like to add a calendar account now?",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Provider icons hint
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_google_calendar),
                        contentDescription = "Google Calendar",
                        modifier = Modifier.size(32.dp),
                        tint = androidx.compose.ui.graphics.Color.Unspecified
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_outlook),
                        contentDescription = "Outlook Calendar",
                        modifier = Modifier.size(32.dp),
                        tint = androidx.compose.ui.graphics.Color.Unspecified
                    )
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                ) {
                    // Later button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Global.touchTarget)
                            .border(
                                EInkTheme.Global.borderWidth,
                                EInkTheme.Colors.darkGray,
                                RoundedCornerShape(EInkTheme.Global.cornerRadius)
                            )
                            .noRippleClickable(onClick = onLater),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Later",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.Colors.primary
                        )
                    }

                    // Add Account button (primary)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.Global.touchTarget)
                            .background(
                                EInkTheme.Colors.primary,
                                RoundedCornerShape(EInkTheme.Global.cornerRadius)
                            )
                            .noRippleClickable(onClick = onAddAccount),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Add Account",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = EInkTheme.Colors.white
                        )
                    }
                }
            }
        }
    }
}
