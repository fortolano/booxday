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
 * Decision options for when all cloud accounts are removed.
 */
enum class ClearSyncedDataChoice {
    /** Clear all synced events and return to Standalone mode */
    CLEAR_AND_STANDALONE,
    /** Keep synced events (read-only) and stay in Hybrid mode */
    KEEP_AND_HYBRID,
    /** Cancel the operation */
    CANCEL
}

/**
 * Popup shown when the last cloud calendar account is disconnected.
 * Asks user whether to clear synced data and return to Standalone mode,
 * or keep data (read-only) and stay in Hybrid mode.
 *
 * E-Ink optimized:
 * - Uses Popup instead of Dialog (no scrim flicker)
 * - No ripple effects
 * - High contrast black/white styling
 * - Descriptive options with icons
 *
 * @param syncedEventCount Number of synced events that would be affected
 * @param onChoice Callback with user's decision
 */
@Composable
fun ClearSyncedDataPopup(
    syncedEventCount: Int = 0,
    onChoice: (ClearSyncedDataChoice) -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = { onChoice(ClearSyncedDataChoice.CANCEL) },
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(340.dp)
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
                // Title
                Text(
                    text = "All Accounts Disconnected",
                    fontSize = EInkTheme.Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

                // Subtitle with event count
                Text(
                    text = if (syncedEventCount > 0) {
                        "You have $syncedEventCount synced event${if (syncedEventCount > 1) "s" else ""} from cloud calendars."
                    } else {
                        "You have synced events from cloud calendars."
                    },
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

                Text(
                    text = "What would you like to do?",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

                // Option 1: Clear events & return to Standalone
                OptionCard(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = EInkTheme.Colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    title = "Clear events & go Standalone",
                    description = "Removes all synced events and returns to Standalone mode. Cloud sync features will be disabled.",
                    onClick = { onChoice(ClearSyncedDataChoice.CLEAR_AND_STANDALONE) }
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

                // Option 2: Keep events & stay in Hybrid
                OptionCard(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.calendar_today_40px),
                            contentDescription = null,
                            tint = EInkTheme.Colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    title = "Keep events & stay in Hybrid",
                    description = "Synced events remain visible (read-only). You can add cloud accounts later to sync again.",
                    onClick = { onChoice(ClearSyncedDataChoice.KEEP_AND_HYBRID) }
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
                        .noRippleClickable(onClick = { onChoice(ClearSyncedDataChoice.CANCEL) }),
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
 * Individual option card with icon, title, and description.
 * E-Ink optimized with high contrast and clear tap targets.
 */
@Composable
private fun OptionCard(
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
            verticalAlignment = Alignment.Top
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                    lineHeight = 16.sp
                )
            }
        }
    }
}
