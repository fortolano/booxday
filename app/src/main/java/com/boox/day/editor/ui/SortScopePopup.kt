package com.boox.day.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.components.EinkPrimaryButton
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.noRippleClickable

enum class SortScope {
    PAGE, // Sort only the current page (10 rows)
    DAY   // Sort all tasks for the selected date
}

/**
 * Popup to select the scope of the "Sort by Priority" action.
 * Forces the user to choose between sorting the current page or the entire day.
 */
@Composable
fun SortScopePopup(
    onScopeSelected: (SortScope) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedScope by remember { mutableStateOf(SortScope.PAGE) }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .width(EInkTheme.PopupOverlay.width)
                .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
        ) {
            Column(
                modifier = Modifier.padding(EInkTheme.PopupOverlay.padding)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sort by Priority",
                        fontSize = EInkTheme.PopupOverlay.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.PopupOverlay.titleColor
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = EInkTheme.Colors.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacingLarge))

                Text(
                    text = "What would you like to sort?",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary
                )

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacing))

                // Options
                Column(
                    verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                ) {
                    ScopeOption(
                        text = "This page only (10 rows)",
                        selected = selectedScope == SortScope.PAGE,
                        onClick = { selectedScope = SortScope.PAGE }
                    )
                    ScopeOption(
                        text = "All pages for this day",
                        selected = selectedScope == SortScope.DAY,
                        onClick = { selectedScope = SortScope.DAY }
                    )
                }

                Spacer(modifier = Modifier.height(EInkTheme.Global.spacingLarge))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    EinkSecondaryButton(
                        text = "Cancel",
                        onClick = onDismiss
                    )
                    Spacer(modifier = Modifier.width(EInkTheme.Button.spacing))
                    EinkPrimaryButton(
                        text = "Continue",
                        onClick = { onScopeSelected(selectedScope) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScopeOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onClick)
            .background(
                if (selected) EInkTheme.Colors.offWhite else Color.Transparent,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .padding(EInkTheme.Global.paddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null, // Handled by Row click
            colors = RadioButtonDefaults.colors(
                selectedColor = EInkTheme.Colors.primary,
                unselectedColor = EInkTheme.Colors.gray
            )
        )
        Spacer(modifier = Modifier.width(EInkTheme.Global.spacingSmall))
        Text(
            text = text,
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary
        )
    }
}
