package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * Dialog shown when an AI feature has no free task rows to fill: every row of the day's task pages
 * is taken and the day already has the most pages AI adds (TaskSlotCalculator).
 *
 * @param onDismiss Called when user dismisses the dialog
 */
@Composable
fun NoTaskSlotsDialog(
    onDismiss: () -> Unit
) {
    val message = "All task slots for today are full.\n\nComplete or delete some tasks to make room."

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
                    .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                    .background(
                        EInkTheme.ModernDialog.background,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .border(
                        EInkTheme.ModernDialog.borderWidth,
                        EInkTheme.ModernDialog.borderColor,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .padding(EInkTheme.ModernDialog.padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(EInkTheme.ModernDialog.contentSpacing)
            ) {
                // Icon
                Icon(
                    imageVector = Icons.Default.EventBusy,
                    contentDescription = "No slots available",
                    modifier = Modifier.size(40.dp),
                    tint = EInkTheme.Colors.primary
                )

                // Title
                Text(
                    text = "No Task Slots Available",
                    fontWeight = FontWeight.Bold,
                    fontSize = EInkTheme.ModernDialog.titleFontSize,
                    color = EInkTheme.ModernDialog.titleColor,
                    textAlign = TextAlign.Center
                )

                // Message
                Text(
                    text = message,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = EInkTheme.Global.paddingSmall)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Close button (the only action)
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(EInkTheme.ModernDialog.PrimaryButton.height)
                            .border(
                                EInkTheme.ModernDialog.OptionCard.borderWidth,
                                EInkTheme.ModernDialog.CancelLink.textColor,
                                RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                            )
                            .noRippleClickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Close",
                            color = EInkTheme.ModernDialog.CancelLink.textColor,
                            fontWeight = FontWeight.Normal,
                            fontSize = EInkTheme.ModernDialog.PrimaryButton.fontSize
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog shown when only partial insertion is possible.
 *
 * Displayed when user selected more items than available slots.
 * Offers options to insert partial or go back.
 *
 * @param selectedCount Number of items the user selected
 * @param availableSlots Number of slots available for insertion
 * @param itemName Name of items being inserted (e.g., "tasks", "subtasks", "suggestions")
 * @param onInsertPartial Called when user chooses to insert available slots
 * @param onCancel Called when user wants to go back and modify selection
 */
@Composable
fun PartialInsertDialog(
    selectedCount: Int,
    availableSlots: Int,
    itemName: String = "tasks",
    onInsertPartial: () -> Unit,
    onCancel: () -> Unit
) {
    val slotWord = if (availableSlots == 1) "slot is" else "slots are"

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onCancel,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                    .background(
                        EInkTheme.ModernDialog.background,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .border(
                        EInkTheme.ModernDialog.borderWidth,
                        EInkTheme.ModernDialog.borderColor,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .padding(EInkTheme.ModernDialog.padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(EInkTheme.ModernDialog.contentSpacing)
            ) {
                // Warning icon
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Partial insert",
                    modifier = Modifier.size(40.dp),
                    tint = EInkTheme.Colors.primary
                )

                // Title
                Text(
                    text = "Not Enough Slots",
                    fontWeight = FontWeight.Bold,
                    fontSize = EInkTheme.ModernDialog.titleFontSize,
                    color = EInkTheme.ModernDialog.titleColor,
                    textAlign = TextAlign.Center
                )

                // Message
                Text(
                    text = "You selected $selectedCount $itemName but only $availableSlots $slotWord available.\n\nInsert the first $availableSlots?",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = EInkTheme.Global.paddingSmall)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Button row - Go Back (40%) | Space (20%) | Insert N (40%)
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Go Back button (outlined)
                    Box(
                        modifier = Modifier
                            .weight(0.4f)
                            .height(EInkTheme.ModernDialog.PrimaryButton.height)
                            .border(
                                EInkTheme.ModernDialog.OptionCard.borderWidth,
                                EInkTheme.ModernDialog.CancelLink.textColor,
                                RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                            )
                            .noRippleClickable { onCancel() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Go Back",
                            color = EInkTheme.ModernDialog.CancelLink.textColor,
                            fontWeight = FontWeight.Normal,
                            fontSize = EInkTheme.ModernDialog.PrimaryButton.fontSize
                        )
                    }

                    Spacer(modifier = Modifier.weight(0.2f))

                    // Insert button (filled)
                    Box(
                        modifier = Modifier
                            .weight(0.4f)
                            .height(EInkTheme.ModernDialog.PrimaryButton.height)
                            .background(
                                EInkTheme.ModernDialog.PrimaryButton.background,
                                RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
                            )
                            .noRippleClickable { onInsertPartial() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Insert $availableSlots",
                            color = EInkTheme.ModernDialog.PrimaryButton.textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = EInkTheme.ModernDialog.PrimaryButton.fontSize
                        )
                    }
                }
            }
        }
    }
}
