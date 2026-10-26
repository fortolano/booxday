package com.boox.day.editor.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.boox.day.ui.components.ScrollMoreIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.navigation.NavController
import com.boox.day.R
import com.boox.day.data.AppRepository
import com.boox.day.data.datastore.BUTTON_SIZE
import com.boox.day.editor.DrawCanvas
import com.boox.day.editor.state.EditorState
import com.boox.day.io.ExportEngine
import com.boox.day.io.ExportFormat
import com.boox.day.io.ExportLayout
import com.boox.day.io.ExportOptions
import com.boox.day.io.ExportTarget
import com.boox.day.ui.LocalSnackContext
import com.boox.day.ui.SnackConf
import com.boox.day.ui.convertDpToPixel
import com.boox.day.data.db.TaskPageInfo
import com.boox.day.editor.ui.TaskExportAction
import com.boox.day.editor.ui.TaskExportDialog
import com.boox.day.editor.ui.TaskExportFormat
import com.boox.day.editor.ui.TaskExportScope
import com.boox.day.io.TaskExportService
import com.boox.day.ui.dialogs.ExportDialog
import com.boox.day.ui.dialogs.ShowSimpleConfirmationDialog
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.views.EditorTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Width of the toolbar menu (≡) and of the tool menus shown in it (Oct 2026): wide enough for
 * "Clear All Memos Today" on one line, which wrapped at the 230 dp of EInkTheme.PopupMenu.width.
 */
private val TOOLBAR_MENU_WIDTH = 260.dp

/**
 * A toolbar tool that is not on the bar at this screen width, listed at the top of the toolbar
 * menu (≡) with a label (Oct 2026).
 *
 * @param icon draws the tool's icon in the given tint
 * @param selected shown inverted: the tool's mode is on (lasso)
 * @param badge red "!": the tool needs setup (Boox Notes)
 * @param onClick a plain action; the menu closes first
 * @param mayOpenPage checked before [page] opens; false leaves the menu as it is (Boox Notes setup)
 * @param page the tool's own menu, shown in place of the list with a back row; its argument closes
 *   the whole menu
 * @param pageWrapsContent the page takes its content's width instead of the menu width (pen pickers)
 */
class ToolbarMenuTool(
    val label: String,
    val icon: @Composable (tint: Color) -> Unit,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val badge: Boolean = false,
    val onClick: (() -> Unit)? = null,
    val mayOpenPage: () -> Boolean = { true },
    val page: (@Composable ColumnScope.(closeMenu: () -> Unit) -> Unit)? = null,
    val pageWrapsContent: Boolean = false
)

/**
 * The toolbar menu (≡), tab-aware; opened from the bar's last button on both tabs (Oct 2026).
 *
 * - Top: the tools that are not on the bar at this screen width ([tools]), in bar order. A tool
 *   with its own menu opens it in place of the list (a back row returns), so no popup ever opens
 *   from inside this one.
 * - Then the page items:
 *   - **Memos tab**: Screen Refresh, Export Pages, Clear This Page, Delete Page, Clear All Memos Today
 *   - **Tasks tab**: Screen Refresh, Share/Export Tasks, Delete Page, Clear All Tasks Today
 *
 * Page operations (Clear This Page, Delete Page) moved here from Eraser menu.
 *
 * @param activeTab The currently active tab (Memos or Tasks)
 * @param taskPageInfo Current task page info (for Tasks tab export)
 * @param canvasHeight Canvas height for stroke detection in task export
 * @param maxHeight the space below the bar; a taller menu scrolls with a "Scroll for more" marker
 */
@Composable
fun ToolbarMenu(
    navController: NavController,
    state: EditorState,
    controlTower: com.boox.day.editor.EditorControlTower,
    activeTab: EditorTab,
    taskPageInfo: TaskPageInfo? = null,
    canvasHeight: Float = 0f,
    tools: List<ToolbarMenuTool> = emptyList(),
    maxHeight: Dp = Dp.Unspecified,
    onClearPage: () -> Unit,
    onDeletePage: () -> Unit,
    onScreenRefresh: () -> Unit = {},
    onClose: () -> Unit
) {
    val context = LocalContext.current
    // Phase 3 (Oct 2026): kept across recompositions; the unused page/book lookups (blocking
    // database reads on the main thread on every recomposition, and a crash if the page was gone)
    // are removed
    val scope = remember { CoroutineScope(Dispatchers.IO) }
    val snackManager = LocalSnackContext.current
    val appRepository = remember { AppRepository(context) }

    var showExportDialog by remember { mutableStateOf(false) }

    // Confirmation dialog states
    var showClearPageConfirmation by remember { mutableStateOf(false) }
    var showDeletePageConfirmation by remember { mutableStateOf(false) }
    var showClearMemosConfirmation by remember { mutableStateOf(false) }
    var showClearTasksConfirmation by remember { mutableStateOf(false) }

    // Task Export Dialog state
    var showTaskExportDialog by remember { mutableStateOf(false) }

    // Task Export Dialog
    if (showTaskExportDialog && state.dayDate != null) {
        TaskExportDialog(
            dayDate = state.dayDate!!,
            currentTaskPageIndex = taskPageInfo?.currentIndex ?: 0,
            canvasHeight = canvasHeight,
            onExport = { exportScope, format, action ->
                showTaskExportDialog = false
                onClose()

                // Perform export using TaskExportService (with stroke detection)
                val exportService = TaskExportService(context)
                val result = exportService.export(
                    scope = exportScope,
                    format = format,
                    action = action,
                    dayDate = state.dayDate!!,
                    currentTaskPageIndex = taskPageInfo?.currentIndex ?: 0,
                    canvasHeight = canvasHeight
                )

                // Show result snackbar for both clipboard and share
                scope.launch {
                    snackManager.displaySnack(
                        SnackConf(
                            text = result.message,
                            duration = SnackConf.DURATION_QUICK
                        )
                    )
                }
            },
            onDismiss = { showTaskExportDialog = false }
        )
    }

    // Export dialog (for Memos)
    if (showExportDialog) {
        val dayPageInfo = state.dayPageInfo
        val dayDate = state.dayDate
        val totalPages = dayPageInfo?.totalPages ?: 1
        ExportDialog(
            totalPages = totalPages,
            onExport = { format, exportAll, layout ->
                showExportDialog = false
                onClose()
                scope.launch {
                    val target = if (exportAll && dayDate != null && dayPageInfo != null) {
                        // Export all pages for this day
                        ExportTarget.DayPages(
                            date = dayDate,
                            pageIds = dayPageInfo.pageIds
                        )
                    } else {
                        // Export current page only
                        ExportTarget.Page(pageId = state.currentPageId)
                    }

                    val formatName = when (format) {
                        ExportFormat.PDF -> "PDF"
                        ExportFormat.PNG -> "PNG"
                        ExportFormat.JPEG -> "JPG"
                        else -> format.name
                    }

                    // Create export options with layout preference
                    val options = ExportOptions(layout = layout)

                    snackManager.runWithSnack(
                        context.getString(R.string.exporting_the_page_to, formatName)
                    ) {
                        ExportEngine(context).export(target = target, format = format, options = options)
                    }
                }
            },
            onDismiss = { showExportDialog = false }
        )
    }

    // Confirmation: Clear This Page (current page only)
    if (showClearPageConfirmation) {
        ShowSimpleConfirmationDialog(
            title = "Clear This Page",
            message = "This will remove all content from the current page. This action cannot be undone.",
            confirmButtonText = "Clear Page",
            cancelButtonText = "Cancel",
            onConfirm = {
                showClearPageConfirmation = false
                onClose()
                onClearPage()
                scope.launch {
                    snackManager.displaySnack(
                        SnackConf(
                            text = "Page cleared",
                            duration = SnackConf.DURATION_QUICK
                        )
                    )
                }
            },
            onCancel = { showClearPageConfirmation = false }
        )
    }

    // Confirmation: Delete Page (removes entire page)
    if (showDeletePageConfirmation) {
        ShowSimpleConfirmationDialog(
            title = "Delete Page",
            message = "This will delete the entire page. This action cannot be undone.",
            confirmButtonText = "Delete Page",
            cancelButtonText = "Cancel",
            onConfirm = {
                showDeletePageConfirmation = false
                onClose()
                onDeletePage()
                scope.launch {
                    snackManager.displaySnack(
                        SnackConf(
                            text = "Page deleted",
                            duration = SnackConf.DURATION_QUICK
                        )
                    )
                }
            },
            onCancel = { showDeletePageConfirmation = false }
        )
    }

    // Confirmation: Clear All Memos Today
    if (showClearMemosConfirmation) {
        ShowSimpleConfirmationDialog(
            title = "Clear All Memos Today",
            message = "This will clear all memo pages for today. This action cannot be undone.",
            confirmButtonText = "Clear All",
            cancelButtonText = "Cancel",
            onConfirm = {
                showClearMemosConfirmation = false
                onClose()
                scope.launch {
                    val pageCount = controlTower.clearMemoPages()
                    snackManager.displaySnack(
                        SnackConf(
                            text = "Cleared $pageCount memo page(s)",
                            duration = SnackConf.DURATION_QUICK
                        )
                    )
                }
            },
            onCancel = { showClearMemosConfirmation = false }
        )
    }

    // Confirmation: Clear All Tasks Today
    if (showClearTasksConfirmation) {
        ShowSimpleConfirmationDialog(
            title = "Clear All Tasks Today",
            message = "This will clear all task pages for today. This action cannot be undone.",
            confirmButtonText = "Clear All",
            cancelButtonText = "Cancel",
            onConfirm = {
                showClearTasksConfirmation = false
                onClose()
                // Full screen (Oct 2026): runs in the normal view, like the other Tasks page tools. On the
                // full screen pages it deleted fewer pages, and leaving full screen put the others back.
                com.boox.day.ui.FullScreenState.exitThen {
                    scope.launch {
                        val pageCount = controlTower.clearTaskPages()
                        snackManager.displaySnack(
                            SnackConf(
                                text = "Cleared $pageCount task page(s)",
                                duration = SnackConf.DURATION_QUICK
                            )
                        )
                    }
                }
            },
            onCancel = { showClearTasksConfirmation = false }
        )
    }

    // The tool whose own menu is shown in place of the list (null: the list)
    var openToolLabel by remember { mutableStateOf<String?>(null) }
    val openTool = tools.firstOrNull { it.label == openToolLabel }

    Popup(
        alignment = Alignment.TopEnd,
        onDismissRequest = { onClose() },
        offset = IntOffset(
            0,
            convertDpToPixel(EInkTheme.PopupMenu.offsetY, context).toInt()
        ),
        properties = PopupProperties(focusable = true),
    ) {
        // Each page of the menu starts at its top
        key(openToolLabel) {
            val scrollState = rememberScrollState()
            Box(
                Modifier
                    .background(EInkTheme.PopupMenu.background)
                    .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .then(
                        if (openTool?.pageWrapsContent == true) Modifier.width(IntrinsicSize.Max)
                        else Modifier.width(TOOLBAR_MENU_WIDTH)
                    )
            ) {
                Column(
                    Modifier
                        .heightIn(max = maxHeight)
                        .verticalScroll(scrollState)
                ) {
                    val page = openTool?.page
                    if (openTool != null && page != null) {
                        // A tool's own menu, with a row back to the list
                        MenuPageHeader(label = openTool.label, onBack = { openToolLabel = null })
                        Divider(color = EInkTheme.Divider.color, thickness = EInkTheme.Divider.thickness)
                        page(this, onClose)
                    } else {
                        // The tools that are not on the bar, then the page items
                        tools.forEach { tool ->
                            ToolMenuRow(
                                tool = tool,
                                onOpenPage = { openToolLabel = tool.label },
                                onClose = onClose
                            )
                        }
                        if (tools.isNotEmpty()) {
                            Divider(color = EInkTheme.Divider.color, thickness = EInkTheme.Divider.thickness)
                        }

                        // Screen Refresh (full e-ink GC refresh - moved from calendar toolbar)
                        MenuItemWithDrawableIcon(
                            label = "Screen Refresh",
                            iconResId = R.drawable.refresh_40px
                        ) {
                            onScreenRefresh()
                            onClose()
                        }

                        DividerCentered()

                        // Tab-specific export options
                        when (activeTab) {
                            EditorTab.Memos -> {
                                MenuItem(
                                    label = stringResource(R.string.export_pages),
                                    icon = Icons.Default.Share
                                ) {
                                    showExportDialog = true
                                }
                            }
                            EditorTab.Tasks -> {
                                MenuItem(
                                    label = "Share/Export Tasks",
                                    icon = Icons.Default.Share
                                ) {
                                    showTaskExportDialog = true
                                }
                            }
                        }

                        DividerCentered()

                        // Page operations section
                        if (activeTab == EditorTab.Memos) {
                            // Memos: Clear This Page (removes content, keeps page)
                            MenuItemWithDrawableIcon(
                                label = "Clear This Page",
                                iconResId = R.drawable.clear_all_40px
                            ) {
                                showClearPageConfirmation = true
                            }

                            DividerCentered()
                        }

                        // Both tabs: Delete Page
                        MenuItemWithDrawableIcon(
                            label = "Delete Page",
                            iconResId = R.drawable.delete
                        ) {
                            showDeletePageConfirmation = true
                        }

                        DividerCentered()

                        // Tab-specific clear all options (with clearer scope labels)
                        when (activeTab) {
                            EditorTab.Memos -> {
                                MenuItem(
                                    label = "Clear All Memos Today",
                                    icon = Icons.Default.DeleteSweep
                                ) {
                                    showClearMemosConfirmation = true
                                }
                            }
                            EditorTab.Tasks -> {
                                MenuItem(
                                    label = "Clear All Tasks Today",
                                    icon = Icons.Default.DeleteSweep
                                ) {
                                    showClearTasksConfirmation = true
                                }
                            }
                        }
                    }
                }
                if (scrollState.canScrollForward) {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        ScrollMoreIndicator()
                    }
                }
            }
        }
    }
}

/** A tool in the toolbar menu: its icon and label, "!" when it needs setup, "›" when it has its own menu. */
@Composable
private fun ToolMenuRow(
    tool: ToolbarMenuTool,
    onOpenPage: () -> Unit,
    onClose: () -> Unit
) {
    val isSelected = tool.selected && tool.enabled
    val contentColor = when {
        !tool.enabled -> EInkTheme.MenuItem.textColorDisabled
        isSelected -> EInkTheme.MenuItem.selectedTextColor
        else -> EInkTheme.MenuItem.textColor
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .then(if (isSelected) Modifier.background(EInkTheme.MenuItem.selectedBackground) else Modifier)
            .then(
                if (tool.enabled) {
                    Modifier.noRippleClickable {
                        if (tool.page != null) {
                            if (tool.mayOpenPage()) onOpenPage()
                        } else {
                            onClose()
                            tool.onClick?.invoke()
                        }
                    }
                } else Modifier
            )
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.MenuItem.iconTextSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(EInkTheme.MenuItem.iconSize), contentAlignment = Alignment.Center) {
            tool.icon(contentColor)
        }
        Text(
            text = tool.label,
            color = contentColor,
            fontSize = EInkTheme.MenuItem.fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (tool.badge) {
            Box(
                modifier = Modifier
                    .size(EInkTheme.AlertBadge.size)
                    .background(EInkTheme.AlertBadge.background, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "!",
                    color = EInkTheme.AlertBadge.textColor,
                    fontSize = EInkTheme.AlertBadge.fontSize,
                    fontWeight = EInkTheme.AlertBadge.fontWeight,
                    lineHeight = EInkTheme.AlertBadge.lineHeight
                )
            }
        }
        if (tool.page != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(EInkTheme.MenuItem.iconSize),
                tint = contentColor
            )
        }
    }
}

/** The first row of a tool's own menu: back to the list. */
@Composable
private fun MenuPageHeader(label: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .noRippleClickable { onBack() }
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.MenuItem.iconTextSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Back to the menu",
            modifier = Modifier.size(EInkTheme.MenuItem.iconSize),
            tint = EInkTheme.MenuItem.iconColor
        )
        Text(
            text = label,
            color = EInkTheme.MenuItem.textColor,
            fontSize = EInkTheme.MenuItem.fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MenuItem(
    label: String,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .noRippleClickable { onClick() }
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.MenuItem.iconTextSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(EInkTheme.MenuItem.iconSize),
                tint = EInkTheme.MenuItem.iconColor
            )
        }
        Text(
            text = label,
            color = EInkTheme.MenuItem.textColor,
            fontSize = EInkTheme.MenuItem.fontSize
        )
    }
}

@Composable
private fun MenuItemWithDrawableIcon(
    label: String,
    iconResId: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .noRippleClickable { onClick() }
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.MenuItem.iconTextSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = label,
            modifier = Modifier.size(EInkTheme.MenuItem.iconSize),
            tint = EInkTheme.MenuItem.iconColor
        )
        Text(
            text = label,
            color = EInkTheme.MenuItem.textColor,
            fontSize = EInkTheme.MenuItem.fontSize
        )
    }
}

@Composable
private fun ColumnScope.DividerCentered() {
    Box(
        Modifier
            .fillMaxWidth(1f / 2f)
            .align(Alignment.CenterHorizontally)
            .height(EInkTheme.Divider.subtleThickness)
            .background(EInkTheme.Divider.subtleColor)
    )
}