package com.boox.day.ui.dialogs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.R
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

/**
 * Dialog for managing memo pages - reorder and delete.
 * Triggered by tapping the page counter "1/3" in the toolbar.
 *
 * E-Ink optimized: Uses Popup (no scrim), noRippleClickable, text symbols.
 *
 * @param dayDate The current date (yyyy-MM-dd format)
 * @param pageIds List of page IDs in current order
 * @param currentPageId The currently displayed page ID
 * @param onReorderPages Callback when pages are reordered (passes new order)
 * @param onDeletePage Callback to delete a page (returns new page to navigate to)
 * @param onNavigateToPage Callback to navigate to a specific page
 * @param onDismiss Callback to close the dialog
 */
@Composable
fun PageManagementDialog(
    dayDate: String,
    pageIds: List<String>,
    currentPageId: String,
    onReorderPages: (List<String>) -> Unit,
    onDeletePage: (pageId: String) -> String?,
    onNavigateToPage: (pageId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Local copy of page order for immediate UI updates
    var localPageIds by remember(pageIds) { mutableStateOf(pageIds) }

    // Track which page is pending delete confirmation
    var pendingDeletePageId by remember { mutableStateOf<String?>(null) }

    // Thumbnails cache - Map<pageId, Bitmap?>
    var thumbnails by remember { mutableStateOf<Map<String, Bitmap?>>(emptyMap()) }

    // Load thumbnails asynchronously
    LaunchedEffect(localPageIds) {
        val loadedThumbnails = withContext(Dispatchers.IO) {
            localPageIds.associateWith { pageId ->
                val file = File(context.filesDir, "pages/previews/thumbs/$pageId")
                if (file.exists()) {
                    try {
                        BitmapFactory.decodeFile(file.absolutePath)
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
            }
        }
        thumbnails = loadedThumbnails
    }

    // Move page up
    fun moveUp(index: Int) {
        if (index <= 0) return
        val newList = localPageIds.toMutableList()
        Collections.swap(newList, index, index - 1)
        localPageIds = newList
        onReorderPages(newList)
    }

    // Move page down
    fun moveDown(index: Int) {
        if (index >= localPageIds.size - 1) return
        val newList = localPageIds.toMutableList()
        Collections.swap(newList, index, index + 1)
        localPageIds = newList
        onReorderPages(newList)
    }

    // Delete page
    fun confirmDelete(pageId: String) {
        val newPageId = onDeletePage(pageId)
        localPageIds = localPageIds.filter { it != pageId }
        pendingDeletePageId = null

        // If we deleted the current page, navigate to new page
        if (pageId == currentPageId && newPageId != null) {
            onNavigateToPage(newPageId)
        }

        // If only one page left, close dialog
        if (localPageIds.size <= 1) {
            onDismiss()
        }
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = { onDismiss() },
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(EInkTheme.PopupOverlay.width)  // Standard EInkTheme size (460.dp)
                    .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.PopupOverlay.padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with title and Close button (replaces X, saves bottom space)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Manage Pages",
                        fontWeight = FontWeight.Bold,
                        fontSize = EInkTheme.PopupOverlay.titleFontSize,
                        color = EInkTheme.PopupOverlay.titleColor
                    )
                    EinkSecondaryButton(
                        text = "Close",
                        onClick = onDismiss,
                        width = 80.dp,
                        height = 36.dp
                    )
                }

                // Fixed height list area with scroll indicators
                val listState = rememberLazyListState()
                val listHeight = 300.dp  // Reduced height (was 380.dp)

                // Derive scroll state
                val canScrollUp by remember {
                    derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
                }
                val canScrollDown by remember {
                    derivedStateOf {
                        val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                        lastVisibleItem != null && lastVisibleItem.index < localPageIds.size - 1
                    }
                }

                // Scroll indicator - top (Fixed height container to prevent shifting)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(if (canScrollUp) EInkTheme.Colors.offWhite else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    if (canScrollUp) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = EInkTheme.Colors.darkGray,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "scroll up",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.darkGray
                            )
                        }
                    }
                }

                // Page list with fixed height
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(listHeight),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(localPageIds) { index, pageId ->
                        val isCurrent = pageId == currentPageId
                        val isPendingDelete = pendingDeletePageId == pageId
                        val thumbnail = thumbnails[pageId]

                        PageManagementItem(
                            pageId = pageId,
                            pageIndex = index,
                            totalPages = localPageIds.size,
                            thumbnail = thumbnail,
                            isCurrent = isCurrent,
                            isPendingDelete = isPendingDelete,
                            onMoveUp = { moveUp(index) },
                            onMoveDown = { moveDown(index) },
                            onDeleteRequest = { pendingDeletePageId = pageId },
                            onDeleteConfirm = { confirmDelete(pageId) },
                            onDeleteCancel = { pendingDeletePageId = null },
                            onTap = { onNavigateToPage(pageId) }
                        )
                    }
                }

                // Scroll indicator - bottom (Fixed height container to prevent shifting)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(if (canScrollDown) EInkTheme.Colors.offWhite else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    if (canScrollDown) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = EInkTheme.Colors.darkGray,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "scroll down",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.darkGray
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual page item in the management dialog.
 *
 * UI Design (E-Ink optimized - follows app patterns):
 * - Current page: offWhite background + bold text (NO heavy borders)
 * - Action buttons: Plain text symbols with padding (NO boxes around icons)
 * - Delete confirmation: Standard EInkTheme button styling (1dp borders)
 */
@Composable
private fun PageManagementItem(
    pageId: String,
    pageIndex: Int,
    totalPages: Int,
    thumbnail: Bitmap?,
    isCurrent: Boolean,
    isPendingDelete: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteCancel: () -> Unit,
    onTap: () -> Unit
) {
    // Current page: subtle gray background (matches app's selection pattern)
    val backgroundColor = if (isCurrent) EInkTheme.Colors.offWhite else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .background(backgroundColor, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
            .noRippleClickable { onTap() }
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left accent bar for current page (subtle, not full border)
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Thumbnail (60x80dp)
        Box(
            modifier = Modifier
                .size(60.dp, 80.dp)
                .background(EInkTheme.Colors.white, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.darkGray, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius)),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1} thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            // Page number badge (bottom-right corner)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(
                        if (thumbnail != null) Color.Black.copy(alpha = 0.75f) else EInkTheme.Colors.darkGray,
                    RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${pageIndex + 1}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Page info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Page ${pageIndex + 1}",
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = EInkTheme.Colors.primary
            )
            if (isCurrent) {
                Text(
                    text = "current",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.darkGray
                )
            }
        }

        // Action buttons or delete confirmation
        if (!isPendingDelete) {
            // Clean icon buttons using vector drawables
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Move Up
                IconActionButton(
                    iconRes = R.drawable.expand_circle_up_40px,
                    enabled = pageIndex > 0,
                    onClick = onMoveUp,
                    contentDescription = "Move up"
                )

                // Move Down
                IconActionButton(
                    iconRes = R.drawable.expand_circle_down_40px,
                    enabled = pageIndex < totalPages - 1,
                    onClick = onMoveDown,
                    contentDescription = "Move down"
                )

                // Delete
                IconActionButton(
                    iconRes = R.drawable.delete,
                    enabled = true,
                    onClick = onDeleteRequest,
                    contentDescription = "Delete"
                )
            }
        } else {
            // Inline delete confirmation with proper button styling
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Delete?",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.primary
                )

                // Yes button - primary (filled)
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .widthIn(min = 56.dp)
                        .background(EInkTheme.Button.primaryBackground, RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius))
                        .border(EInkTheme.Button.primaryBorderWidth, EInkTheme.Button.primaryBorderColor, RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius))
                        .noRippleClickable { onDeleteConfirm() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Delete Page",
                        fontSize = EInkTheme.Button.fontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Button.primaryTextColor
                    )
                }

                // No button - secondary (outlined)
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .widthIn(min = 56.dp)
                        .background(EInkTheme.Button.background, RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius))
                        .border(EInkTheme.Button.borderWidth, EInkTheme.Button.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius))
                        .noRippleClickable { onDeleteCancel() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = EInkTheme.Button.fontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Button.textColor
                    )
                }
            }
        }
    }
}

/**
 * Icon action button using vector drawables.
 * Touch target achieved through size, not visible border.
 */
@Composable
private fun IconActionButton(
    iconRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String
) {
    Box(
        modifier = Modifier
            .size(48.dp)  // 48dp touch target
            .noRippleClickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(28.dp),
            tint = if (enabled) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray
        )
    }
}
