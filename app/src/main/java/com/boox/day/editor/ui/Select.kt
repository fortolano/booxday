package com.boox.day.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * A composable function that creates a dropdown-style select menu.
 *
 * It displays the currently selected value and, when clicked, opens a popup
 * with a list of all available options. Selecting an option from the list
 * updates the state and closes the popup.
 *
 * @param T The type of the value for each option.
 * @param options A list of pairs, where each pair contains a value of type [T] and its corresponding display string.
 * @param value The currently selected value of type [T].
 * @param onChange A callback function that is invoked with the new value of type [T] when an option is selected.
 */
@Composable
fun <T> SelectMenu(options: List<Pair<T, String>>, value: T, onChange: (T) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }

    Box {
        Row {
            Text(
                text = options.find { it.first == value }?.second ?: "Undefined",
                fontWeight = FontWeight.Light,
                color = EInkTheme.MenuItem.textColor,
                fontSize = EInkTheme.MenuItem.fontSize,
                modifier = Modifier.noRippleClickable { isExpanded = true })

            Icon(
                Icons.Rounded.ArrowDropDown, contentDescription = "open select",
                tint = EInkTheme.MenuItem.iconColor,
                modifier = Modifier.height(20.dp)
            )
        }
        if (isExpanded) Popup(
            onDismissRequest = { isExpanded = false },
            properties = PopupProperties(focusable = true)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = EInkTheme.PopupMenu.width)
                    .background(EInkTheme.PopupMenu.background)
                    .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.PopupMenu.padding)
            ) {
                options.map {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(EInkTheme.MenuItem.height)
                            .background(if (it.first == value) EInkTheme.MenuItem.selectedBackground else EInkTheme.PopupMenu.background)
                            .noRippleClickable {
                                onChange(it.first)
                                isExpanded = false
                            }
                            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = it.second,
                            fontWeight = FontWeight.Normal,
                            color = if (it.first == value) EInkTheme.MenuItem.selectedTextColor else EInkTheme.MenuItem.textColor,
                            fontSize = EInkTheme.MenuItem.fontSize
                        )
                    }
                }
            }
        }
    }
}