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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
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
 * Decision options for switching from Cloud Sync to Private Mode.
 */
enum class SwitchToStandaloneChoice {
    /** Disconnect accounts, keep synced events (read-only), switch to Private Mode */
    KEEP_DATA,
    /** Disconnect accounts, clear synced events, switch to Private Mode */
    CLEAR_DATA,
    /** Cancel - stay in Cloud Sync mode */
    CANCEL
}

/**
 * Warning popup shown when user tries to switch from Cloud Sync to Private Mode
 * while having connected cloud calendar accounts.
 *
 * Explains that accounts will be disconnected and offers choice about synced events.
 *
 * E-Ink optimized:
 * - Uses Popup instead of Dialog (no scrim flicker)
 * - No ripple effects
 * - High contrast black/white styling
 * - Descriptive options with icons
 *
 * @param connectedEmails List of emails for connected accounts that will be disconnected
 * @param syncedEventCount Number of synced events that would be affected
 * @param onChoice Callback with user's decision
 */
@Composable
fun SwitchToStandalonePopup(
    connectedEmails: List<String>,
    syncedEventCount: Int = 0,
    onChoice: (SwitchToStandaloneChoice) -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = { onChoice(SwitchToStandaloneChoice.CANCEL) },
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(360.dp)
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
                // Warning header
                Row(
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = EInkTheme.Colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Switch to Private Mode?",
                        fontSize = EInkTheme.Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.primary
                    )
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

                // Explanation
                Text(
                    text = "Switching to Private Mode will disconnect these cloud accounts:" +
                        // Task sync is Cloud Sync only (TaskSyncRun)
                        if (com.boox.day.tasksync.TaskSync.isOn(androidx.compose.ui.platform.LocalContext.current)) " Task Sync stops too." else "",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

                // List of accounts to be disconnected
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            EInkTheme.Colors.offWhite,
                            RoundedCornerShape(EInkTheme.Global.cornerRadius)
                        )
                        .padding(EInkTheme.Global.paddingSmall),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    connectedEmails.forEach { email ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "•",
                                fontSize = EInkTheme.Typography.bodyMedium,
                                color = EInkTheme.Colors.primary
                            )
                            Text(
                                text = email,
                                fontSize = EInkTheme.Typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = EInkTheme.Colors.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

                // Event count info
                if (syncedEventCount > 0) {
                    Text(
                        text = "You have $syncedEventCount synced event${if (syncedEventCount > 1) "s" else ""} from these accounts.",
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.darkGray
                    )
                    Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
                }

                Text(
                    text = "What would you like to do with synced events?",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Option 1: Keep data
                StandaloneOptionCard(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.calendar_today_40px),
                            contentDescription = null,
                            tint = EInkTheme.Colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    title = "Keep events (read-only)",
                    description = "Events remain visible but cannot be edited or synced.",
                    onClick = { onChoice(SwitchToStandaloneChoice.KEEP_DATA) }
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

                // Option 2: Clear data
                StandaloneOptionCard(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = EInkTheme.Colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    title = "Clear all synced events",
                    description = "Permanently removes all events from cloud calendars.",
                    onClick = { onChoice(SwitchToStandaloneChoice.CLEAR_DATA) }
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Cancel button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EInkTheme.Global.touchTarget)
                        .border(
                            EInkTheme.Global.borderWidth,
                            EInkTheme.Colors.darkGray,
                            RoundedCornerShape(EInkTheme.Global.cornerRadius)
                        )
                        .noRippleClickable(onClick = { onChoice(SwitchToStandaloneChoice.CANCEL) }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.primary
                    )
                }
            }
        }
    }
}

/**
 * Option card for the standalone switch popup.
 */
@Composable
private fun StandaloneOptionCard(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                EInkTheme.Global.borderWidth,
                EInkTheme.Colors.lightGray,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .noRippleClickable(onClick = onClick)
            .padding(EInkTheme.Global.paddingMedium)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        EInkTheme.Colors.offWhite,
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            // Text content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )
                Text(
                    text = description,
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.darkGray,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
