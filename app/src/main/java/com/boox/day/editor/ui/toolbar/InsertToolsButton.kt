package com.boox.day.editor.ui.toolbar

import com.boox.day.ui.LocalNestedPopupOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.R
import com.boox.day.data.datastore.BUTTON_SIZE
import com.boox.day.ui.convertDpToPixel
import com.boox.day.ui.theme.EInkTheme

/**
 * Insert tools dropdown button.
 * Single-tap behavior:
 * - Tap: Button becomes dark AND dropdown menu appears
 * - Mutually exclusive with other mode buttons (draw, erase, select, ai, notes, menu)
 *
 * Includes:
 * - Image (working) - opens image picker
 * - Background (working) - opens background selector
 * - Table (working) - opens table creation dialog
 * - Attachment (working) - opens attachment picker for ebooks/PDFs
 * - Text (working) - inserts empty text box for manual text entry
 */
@Composable
fun InsertToolsButton(
    onImageInsert: () -> Unit,
    onBackgroundSelect: () -> Unit,
    onTableInsert: () -> Unit,
    onAttachmentInsert: () -> Unit,
    onTextInsert: () -> Unit,
    onMenuOpenChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    onActivate: () -> Unit = {},
    isOpen: Boolean = false,
    onOpenChange: ((Boolean) -> Unit)? = null,
    // Boox permission state - when false, shows badge on Image and Link File menu items
    hasBooxPermission: Boolean = true
) {
    val context = LocalContext.current

    // Use external state if provided, otherwise internal
    var internalShowMenu by remember { mutableStateOf(false) }
    val showMenu = if (onOpenChange != null) isOpen else internalShowMenu
    val setShowMenu: (Boolean) -> Unit = { value ->
        if (onOpenChange != null) {
            onOpenChange(value)
        } else {
            internalShowMenu = value
        }
    }

    if (onMenuOpenChange != null) {
        LaunchedEffect(showMenu) {
            onMenuOpenChange(showMenu)
        }
    }

    Box(modifier = modifier) {
        ToolbarButton(
            iconId = R.drawable.image_inset_40px,
            isSelected = isActive,  // Dark when this button is the active mode
            onSelect = {
                onActivate()  // Set this as active button
                setShowMenu(true)  // Show dropdown immediately
            },
            hasDropdown = true,
            contentDescription = "Insert tools"
        )

        if (showMenu) {
            Popup(
                offset = IntOffset(0, convertDpToPixel(EInkTheme.PopupMenu.offsetY, context).toInt()) + LocalNestedPopupOffset.current,
                onDismissRequest = { setShowMenu(false) },
                properties = PopupProperties(focusable = false),  // Non-focusable allows taps to reach other buttons
                alignment = Alignment.TopStart
            ) {
                Column(
                    modifier = Modifier
                        .background(EInkTheme.PopupMenu.background)
                        .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor)
                        .width(EInkTheme.PopupMenu.width)
                ) {
                    InsertMenuItems(
                        onImageInsert = onImageInsert,
                        onBackgroundSelect = onBackgroundSelect,
                        onTableInsert = onTableInsert,
                        onAttachmentInsert = onAttachmentInsert,
                        onTextInsert = onTextInsert,
                        hasBooxPermission = hasBooxPermission,
                        onDone = { setShowMenu(false) }
                    )
                }
            }
        }
    }
}

/**
 * The Insert menu's items, shared by the toolbar dropdown and the toolbar menu (≡) when Insert is
 * not on the bar (Oct 2026). [onDone] closes whichever menu shows them.
 */
@Composable
fun InsertMenuItems(
    onImageInsert: () -> Unit,
    onBackgroundSelect: () -> Unit,
    onTableInsert: () -> Unit,
    onAttachmentInsert: () -> Unit,
    onTextInsert: () -> Unit,
    hasBooxPermission: Boolean,
    onDone: () -> Unit
) {
    // Image (working) - requires file permission for Boox Notes images
    InsertMenuItem(
        icon = R.drawable.image_inset_40px,
        label = "Image",
        enabled = true,
        onClick = {
            onImageInsert()
            onDone()
        },
        showBadge = !hasBooxPermission
    )

    // Background (working)
    InsertMenuItem(
        icon = R.drawable.background_grid_small_40px,
        label = "Background",
        enabled = true,
        onClick = {
            onBackgroundSelect()
            onDone()
        }
    )

    Divider(color = EInkTheme.Divider.subtleColor, thickness = EInkTheme.Divider.subtleThickness)

    // Table (working)
    InsertMenuItemVector(
        icon = Icons.Default.TableChart,
        label = "Table",
        enabled = true,
        onClick = {
            onTableInsert()
            onDone()
        }
    )

    // Attachment (working) - requires file permission for linking files
    InsertMenuItem(
        icon = R.drawable.add_link_40px,
        label = "Link File",
        enabled = true,
        onClick = {
            onAttachmentInsert()
            onDone()
        },
        showBadge = !hasBooxPermission
    )

    Divider(color = EInkTheme.Divider.subtleColor, thickness = EInkTheme.Divider.subtleThickness)

    // Text box (working) - insert empty text box for manual entry
    InsertMenuItem(
        icon = R.drawable.text_fields_48px,
        label = "Text",
        enabled = true,
        onClick = {
            onTextInsert()
            onDone()
        }
    )
}

@Composable
private fun InsertMenuItem(
    icon: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    showBadge: Boolean = false  // Red "!" badge for permission-required items
) {
    val contentColor = if (enabled) EInkTheme.MenuItem.textColor else EInkTheme.MenuItem.textColorDisabled

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.MenuItem.iconTextSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = label,
            modifier = Modifier.size(EInkTheme.MenuItem.iconSize),
            tint = contentColor
        )
        Text(
            text = label,
            fontSize = EInkTheme.MenuItem.fontSize,
            color = contentColor
        )
        // Permission badge - red "!" indicator when permission needed
        if (showBadge) {
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(Color(0xFFD32F2F), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "!",
                    color = Color.White,
                    fontSize = 10.sp,
                    lineHeight = 10.sp
                )
            }
        }
    }
}

@Composable
private fun InsertMenuItemVector(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (enabled) EInkTheme.MenuItem.textColor else EInkTheme.MenuItem.textColorDisabled

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.MenuItem.iconTextSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(EInkTheme.MenuItem.iconSize),
            tint = contentColor
        )
        Text(
            text = label,
            fontSize = EInkTheme.MenuItem.fontSize,
            color = contentColor
        )
    }
}

@Composable
private fun InsertMenuItemFeather(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (enabled) EInkTheme.MenuItem.textColor else EInkTheme.MenuItem.textColorDisabled

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.MenuItem.iconTextSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(EInkTheme.MenuItem.iconSize),
            tint = contentColor
        )
        Text(
            text = label,
            fontSize = EInkTheme.MenuItem.fontSize,
            color = contentColor
        )
    }
}
