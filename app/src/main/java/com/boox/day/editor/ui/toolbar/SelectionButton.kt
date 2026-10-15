package com.boox.day.editor.ui.toolbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.boox.day.R

/**
 * Simple selection button (lasso tool).
 * No dropdown - just activates lasso selection mode.
 *
 * Note: Mode.Select remains as-is (object, not data class).
 * No SelectionType enum is needed - only lasso selection is supported.
 */
@Composable
fun SelectionButton(
    isSelectionActive: Boolean,
    onLassoSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    ToolbarButton(
        iconId = R.drawable.lasso_select_40px,
        isSelected = isSelectionActive,
        onSelect = onLassoSelect,
        contentDescription = "Lasso selection",
        modifier = modifier
    )
}
