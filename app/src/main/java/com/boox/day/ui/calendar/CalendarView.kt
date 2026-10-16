package com.boox.day.ui.calendar

import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.boox.day.ui.NestedPopupHost
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.boox.day.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.datastore.SetupChecklist
import com.boox.day.data.db.TaskRepository
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.theme.LayoutProfiles
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import com.boox.day.ui.calendar.CalendarBadges
import com.boox.day.editor.ui.toolbar.TaskStatsPopup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * CalendarView - Monthly calendar with day selection and note indicators
 * Includes integrated toolbar icons in the header row
 */
@Composable
fun CalendarView(
    selectedDate: String,
    datesWithNotes: Set<String>,
    datesWithTasks: Set<String> = emptySet(),
    onDateSelected: (String) -> Unit,
    onMonthChanged: (year: Int, month: Int) -> Unit,
    onTodayClick: () -> Unit,
    onAddEventClick: () -> Unit,
    onSyncClick: () -> Unit,
    onScreenRefresh: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onPreferences: () -> Unit = {},
    onCalendarSettings: () -> Unit = {},
    onCalendarAccounts: () -> Unit = {},
    onAiSettings: () -> Unit = {},
    onVoiceSettings: () -> Unit = {},
    onDataBackup: () -> Unit = {},
    onBooxNotesSettings: () -> Unit = {},
    onHelp: () -> Unit = {},
    onAbout: () -> Unit = {},
    onDayLongPress: ((String) -> Unit)? = null,
    firstDayOfWeekSetting: AppSettings.FirstDayOfWeek = AppSettings.FirstDayOfWeek.Sunday,
    showWeekNumbers: Boolean = false,
    isSyncing: Boolean = false,
    lastSyncTime: Long? = null,
    setupChecklist: SetupChecklist = SetupChecklist(),
    isStandaloneMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val today = dateFormat.format(Calendar.getInstance().time)

    var viewingCalendar by remember {
        mutableStateOf(Calendar.getInstance().apply {
            try {
                time = dateFormat.parse(selectedDate) ?: time
            } catch (e: Exception) { }
        })
    }

    val currentYear = viewingCalendar.get(Calendar.YEAR)
    val currentMonth = viewingCalendar.get(Calendar.MONTH)

    Column(modifier = modifier.fillMaxWidth()) {
        // Combined header: Month navigation + Toolbar icons
        MonthHeaderWithToolbar(
            year = currentYear,
            month = currentMonth,
            onPreviousMonth = {
                viewingCalendar = (viewingCalendar.clone() as Calendar).apply {
                    add(Calendar.MONTH, -1)
                }
                onMonthChanged(viewingCalendar.get(Calendar.YEAR), viewingCalendar.get(Calendar.MONTH))
            },
            onNextMonth = {
                viewingCalendar = (viewingCalendar.clone() as Calendar).apply {
                    add(Calendar.MONTH, 1)
                }
                onMonthChanged(viewingCalendar.get(Calendar.YEAR), viewingCalendar.get(Calendar.MONTH))
            },
            onTodayClick = onTodayClick,
            onAddEventClick = onAddEventClick,
            onSyncClick = onSyncClick,
            onScreenRefresh = onScreenRefresh,
            onSearchClick = onSearchClick,
            onPreferences = onPreferences,
            onCalendarSettings = onCalendarSettings,
            onCalendarAccounts = onCalendarAccounts,
            onAiSettings = onAiSettings,
            onVoiceSettings = onVoiceSettings,
            onDataBackup = onDataBackup,
            onBooxNotesSettings = onBooxNotesSettings,
            onHelp = onHelp,
            onAbout = onAbout,
            isSyncing = isSyncing,
            lastSyncTime = lastSyncTime,
            setupChecklist = setupChecklist,
            isStandaloneMode = isStandaloneMode
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Day of week headers with current day indicator
        DayOfWeekHeader(
            selectedDate = selectedDate,
            firstDayOfWeekSetting = firstDayOfWeekSetting,
            showWeekNumbers = showWeekNumbers
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Calendar grid (compact)
        CalendarGrid(
            year = currentYear,
            month = currentMonth,
            selectedDate = selectedDate,
            today = today,
            badges = CalendarBadges(
                datesWithNotes = datesWithNotes,
                datesWithTasks = datesWithTasks
            ),
            onDateSelected = onDateSelected,
            onDayLongPress = onDayLongPress,
            firstDayOfWeekSetting = firstDayOfWeekSetting,
            showWeekNumbers = showWeekNumbers
        )
    }
}

@Composable
fun MonthHeaderWithToolbar(
    year: Int,
    month: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTodayClick: () -> Unit,
    onVoiceInput: () -> Unit = {},
    onAddEventClick: () -> Unit,
    onSyncClick: () -> Unit,
    onScreenRefresh: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onPreferences: () -> Unit = {},
    onCalendarSettings: () -> Unit = {},
    onCalendarAccounts: () -> Unit = {},
    onAiSettings: () -> Unit = {},
    onVoiceSettings: () -> Unit = {},
    onDataBackup: () -> Unit = {},
    onBooxNotesSettings: () -> Unit = {},
    onHelp: () -> Unit = {},
    onAbout: () -> Unit = {},
    onMonthYearSelected: (newYear: Int, newMonth: Int) -> Unit = { _, _ -> },
    isSyncing: Boolean = false,
    lastSyncTime: Long? = null,
    // New sync status parameters
    isSignedIn: Boolean = true,
    pendingSyncCount: Int = 0,
    hasSyncError: Boolean = false,
    // First-run onboarding - badges on settings that need attention
    setupChecklist: SetupChecklist = SetupChecklist(),
    // Standalone mode - disables Calendar Accounts menu item
    isStandaloneMode: Boolean = false,
    // Compact screens (plan Phase 1.5): ‹ Month Year › title opens the month popup; the other
    // header buttons move into a ⋮ popup. Regular layout is unchanged when false.
    compact: Boolean = false,
    onMonthTitleClick: () -> Unit = {},
    onCompactPopupVisibleChange: (Boolean) -> Unit = {}
) {
    var isSettingsMenuOpen by remember { mutableStateOf(false) }
    var isMonthDropdownOpen by remember { mutableStateOf(false) }
    var isYearDropdownOpen by remember { mutableStateOf(false) }

    // Task streak badge state
    var showStatsPopup by remember { mutableStateOf(false) }
    var currentStreak by remember { mutableStateOf(0) }
    val context = LocalContext.current

    // Load streak count from database, and again whenever tasks change
    // (T18, Oct 2026: it was loaded once and never refreshed)
    LaunchedEffect(Unit) {
        val taskRepo = TaskRepository(context)
        currentStreak = withContext(Dispatchers.IO) { taskRepo.getCurrentStreak() }
        TaskRepository.taskChanged.collect {
            currentStreak = withContext(Dispatchers.IO) { taskRepo.getCurrentStreak() }
        }
    }

    val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    val years = (2023..2030).toList()

    // Header button groups (plan Phase 1.5): shared by the Regular row (same order as before)
    // and the compact ⋮ popup. In Regular, isOverflowOpen stays false.
    var isOverflowOpen by remember { mutableStateOf(false) }

    // Today button
    val todayButton: @Composable () -> Unit = {
        // Right side: Toolbar icons with spacing
        // Today button with day number inside calendar icon (FIRST)
        val todayDayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        IconButton(onClick = onTodayClick, modifier = Modifier.size(EInkTheme.Global.touchTarget * LocalHeaderIconScale.current)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.calendar_today_thin),  // Custom thin lines
                    contentDescription = "Go to today",
                    modifier = Modifier.size(EInkTheme.CalendarToolbar.iconSizeStandard * LocalHeaderIconScale.current),
                    tint = EInkTheme.Colors.primary
                )
                // Day number positioned inside the calendar icon (centered with slight offset for visual balance)
                Text(
                    text = todayDayOfMonth.toString(),
                    fontSize = EInkTheme.Typography.labelSmall * LocalHeaderIconScale.current,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary,
                    modifier = Modifier.padding(top = 7.dp * LocalHeaderIconScale.current)  // Adjusted for better centering
                )
            }
        }
    }

    // Voice input
    val voiceButton: @Composable () -> Unit = {
        // Voice input button (respects AI enabled setting)
        val aiEnabled = GlobalAppSettings.current.aiEnabled
        IconButton(
            onClick = { if (aiEnabled) { isOverflowOpen = false; onVoiceInput() } },
            enabled = aiEnabled,
            modifier = Modifier
                .size(EInkTheme.Global.touchTarget * LocalHeaderIconScale.current)
                .alpha(if (aiEnabled) 1f else 0.35f)  // More obvious disabled state
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_voice_input),
                contentDescription = "Voice input",
                modifier = Modifier.size(EInkTheme.CalendarToolbar.iconSizeStandard * LocalHeaderIconScale.current),
                tint = if (aiEnabled) EInkTheme.Colors.primary else EInkTheme.Toolbar.iconColorDisabled
            )
        }
    }

    // Add event
    val addEventButton: @Composable () -> Unit = {
        IconButton(onClick = onAddEventClick, modifier = Modifier.size(EInkTheme.Global.touchTarget * LocalHeaderIconScale.current)) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add event",
                modifier = Modifier.size(EInkTheme.CalendarToolbar.iconSizeStandard * LocalHeaderIconScale.current),
                tint = EInkTheme.Colors.primary
            )
        }
    }

    // Sync icon with spinning animation and pending badge (compact: on the header bar, layout B)
    val syncIcon: @Composable () -> Unit = {
            // PERFORMANCE FIX: Only create animation when actively syncing
            // Previously the infiniteTransition ran constantly even when not syncing
            val rotation = if (isSyncing) {
                val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
                infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "sync_icon_rotation"
                ).value
            } else {
                0f
            }

            Box {
                IconButton(onClick = { isOverflowOpen = false; onSyncClick() }, modifier = Modifier.size(EInkTheme.Global.touchTarget * LocalHeaderIconScale.current)) {
                    Icon(
                        painter = painterResource(R.drawable.cloud_sync),
                        contentDescription = when {
                            !isSignedIn -> "Not signed in"
                            isSyncing -> "Syncing..."
                            hasSyncError -> "Sync error"
                            pendingSyncCount > 0 -> "$pendingSyncCount pending"
                            else -> "Synced"
                        },
                        modifier = Modifier
                            .size(EInkTheme.CalendarToolbar.iconSizeStandard * LocalHeaderIconScale.current)
                            .then(
                                if (isSyncing) Modifier.graphicsLayer { rotationZ = rotation }
                                else Modifier
                            ),
                        tint = when {
                            !isSignedIn -> EInkTheme.Colors.lightGray
                            hasSyncError -> EInkTheme.CalendarToolbar.iconColorError
                            else -> EInkTheme.Colors.primary
                        }
                    )
                }

                // Pending count badge
                if (isSignedIn && pendingSyncCount > 0 && !isSyncing) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-2).dp, y = 4.dp)
                            .size(EInkTheme.CalendarToolbar.badgeSize)
                            .clip(CircleShape)
                            .background(EInkTheme.CalendarToolbar.badgeBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (pendingSyncCount > 9) "9+" else pendingSyncCount.toString(),
                            color = EInkTheme.CalendarToolbar.badgeTextColor,
                            fontSize = EInkTheme.CalendarToolbar.badgeFontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
    }

    // Sync status text
    val syncStatusText: @Composable () -> Unit = {
            // Status text - fixed width to prevent icon shifting
            Box(modifier = Modifier.width(EInkTheme.CalendarToolbar.statusTextWidth)) {
                when {
                    !isSignedIn -> {
                        Text(
                            text = "Sign in",
                            fontSize = EInkTheme.Typography.labelSmall,
                            color = EInkTheme.Colors.gray,
                            maxLines = 1
                        )
                    }
                    isSyncing -> {
                        Text(
                            text = "Syncing...",
                            fontSize = EInkTheme.Typography.labelSmall,
                            color = EInkTheme.Colors.gray,
                            maxLines = 1
                        )
                    }
                    hasSyncError -> {
                        Text(
                            text = "Sync failed",
                            fontSize = EInkTheme.Typography.labelSmall,
                            color = Color(0xFFB71C1C),
                            maxLines = 1
                        )
                    }
                    pendingSyncCount > 0 -> {
                        Text(
                            text = "$pendingSyncCount pending",
                            fontSize = EInkTheme.Typography.labelSmall,
                            color = EInkTheme.Colors.gray,
                            maxLines = 1
                        )
                    }
                    lastSyncTime != null -> {
                        Text(
                            text = formatLastSyncTime(lastSyncTime),
                            fontSize = EInkTheme.Typography.labelSmall,
                            color = EInkTheme.Colors.gray,
                            maxLines = 1
                        )
                    }
                }
            }
    }

    // Sync button with spinning animation, pending badge, and status text
    val syncButton: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            syncIcon()
            syncStatusText()
        }
    }

    // Task streak
    val streakButton: @Composable () -> Unit = {
        // Task streak badge (🔥 icon + count) - opens statistics popup
        Box(
            modifier = Modifier
                .size(EInkTheme.Global.touchTarget * LocalHeaderIconScale.current)
                .noRippleClickable {
                    isOverflowOpen = false
                    showStatsPopup = true
                },
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.local_fire_department_40px),
                    contentDescription = "Task streak",
                    modifier = Modifier.size(EInkTheme.CalendarToolbar.iconSizeSmall * LocalHeaderIconScale.current),
                    tint = EInkTheme.Colors.primary
                )
                if (currentStreak > 0) {
                    Text(
                        text = currentStreak.toString(),
                        fontSize = EInkTheme.Typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.primary,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
        }
    }

    // Search
    val searchButton: @Composable () -> Unit = {
        // Search button (opens search dialog)
        IconButton(onClick = { isOverflowOpen = false; onSearchClick() }, modifier = Modifier.size(EInkTheme.Global.touchTarget * LocalHeaderIconScale.current)) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                modifier = Modifier.size(EInkTheme.CalendarToolbar.iconSizeStandard * LocalHeaderIconScale.current),
                tint = EInkTheme.Colors.primary
            )
        }
    }

    // Settings
    val settingsButton: @Composable () -> Unit = {
        // Settings button with dropdown menu and badge count
        Box {
            Box {
                IconButton(
                    onClick = { isSettingsMenuOpen = !isSettingsMenuOpen },
                    modifier = Modifier.size(EInkTheme.Global.touchTarget * LocalHeaderIconScale.current)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(EInkTheme.CalendarToolbar.iconSizeStandard * LocalHeaderIconScale.current),
                        tint = EInkTheme.Colors.primary
                    )
                }

                // Badge count overlay (only show if pending > 0)
                val pendingCount = setupChecklist.pendingCount
                if (pendingCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp)
                            .size(16.dp)
                            .background(EInkTheme.Colors.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pendingCount.toString(),
                            color = EInkTheme.Colors.white,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (isSettingsMenuOpen) {
                SettingsMenu(
                    onPreferences = {
                        onPreferences()
                        isSettingsMenuOpen = false
                    },
                    onCalendarSettings = {
                        onCalendarSettings()
                        isSettingsMenuOpen = false
                    },
                    onCalendarAccounts = {
                        onCalendarAccounts()
                        isSettingsMenuOpen = false
                    },
                    onAiSettings = {
                        onAiSettings()
                        isSettingsMenuOpen = false
                    },
                    onVoiceSettings = {
                        onVoiceSettings()
                        isSettingsMenuOpen = false
                    },
                    onDataBackup = {
                        onDataBackup()
                        isSettingsMenuOpen = false
                    },
                    onBooxNotesSettings = {
                        onBooxNotesSettings()
                        isSettingsMenuOpen = false
                    },
                    onHelp = {
                        onHelp()
                        isSettingsMenuOpen = false
                    },
                    onAbout = {
                        onAbout()
                        isSettingsMenuOpen = false
                    },
                    onClose = { isSettingsMenuOpen = false },
                    setupChecklist = setupChecklist,
                    isStandaloneMode = isStandaloneMode,
                    // Narrow screens (Go 6, Oct 2026): the header has no room for the streak button,
                    // so the menu lists it
                    taskStreak = if (LayoutProfiles.isNarrowScreen) currentStreak else null,
                    onTaskStreak = {
                        isSettingsMenuOpen = false
                        showStatsPopup = true
                    }
                )
            }
        }
    }

    // Sync, streak, What's New, search, settings
    val trailingButtons: @Composable () -> Unit = {
        syncButton()
        Spacer(modifier = Modifier.width(7.dp))
        streakButton()
        Spacer(modifier = Modifier.width(10.dp))
        searchButton()
        Spacer(modifier = Modifier.width(8.dp))
        settingsButton()
        Spacer(modifier = Modifier.width(2.dp))
    }

    if (compact) {
        // Report popups so MainScreen can pause raw drawing under them
        val compactPopupVisible = isOverflowOpen || isSettingsMenuOpen || showStatsPopup
        LaunchedEffect(compactPopupVisible) { onCompactPopupVisibleChange(compactPopupVisible) }
        // Closing the settings menu (item chosen or dismissed) also closes the ⋮ popup
        LaunchedEffect(isSettingsMenuOpen) { if (!isSettingsMenuOpen) isOverflowOpen = false }

        // Layout B (Oct 2026): search and sync sit on the bar when the screen is wide enough (e.g.
        // ~570-590dp at a 290-300 dpi setting); otherwise they stay in ⋮. ‹ "September 2026" ›,
        // today, add and ⋮ (53dp buttons) with their gaps take ~438dp; search and sync add 63dp each.
        val headerWidth = with(LocalDensity.current) { context.resources.displayMetrics.widthPixels.toDp() }
        val searchOnBar = headerWidth >= 502.dp
        val syncOnBar = headerWidth >= 565.dp
        val headerHostView = LocalView.current  // for the settings menu opened inside the ⋮ popup

        // Bigger header buttons on compact screens, easier to hit with a finger (Oct 2026)
        CompositionLocalProvider(LocalHeaderIconScale provides HEADER_BAR_ICON_SCALE) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousMonth, modifier = Modifier.size(EInkTheme.Global.touchTarget)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous two weeks",
                    modifier = Modifier.size(EInkTheme.Global.iconSizeLarge),
                    tint = EInkTheme.Colors.primary
                )
            }
            // Month title opens the month grid popup
            Text(
                text = "${monthNames[month]} $year",
                fontSize = EInkTheme.CalendarToolbar.monthFontSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .noRippleClickable { onMonthTitleClick() }
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            )
            IconButton(onClick = onNextMonth, modifier = Modifier.size(EInkTheme.Global.touchTarget)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next two weeks",
                    modifier = Modifier.size(EInkTheme.Global.iconSizeLarge),
                    tint = EInkTheme.Colors.primary
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // A finger-width gap between the icons so one tap never hits two
            if (searchOnBar) {
                searchButton()
                Spacer(modifier = Modifier.width(HEADER_ICON_GAP))
            }
            if (syncOnBar) {
                syncIcon()
                Spacer(modifier = Modifier.width(HEADER_ICON_GAP))
            }
            todayButton()
            Spacer(modifier = Modifier.width(HEADER_ICON_GAP))
            addEventButton()
            Spacer(modifier = Modifier.width(HEADER_ICON_GAP))

            // ⋮ — voice, streak, What's New, settings (+ sync and search when they're not on the bar)
            Box {
                IconButton(
                    onClick = { isOverflowOpen = !isOverflowOpen },
                    modifier = Modifier.size(EInkTheme.Global.touchTarget * LocalHeaderIconScale.current)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More",
                        modifier = Modifier.size(EInkTheme.CalendarToolbar.iconSizeStandard * LocalHeaderIconScale.current),
                        tint = EInkTheme.Colors.primary
                    )
                }
                // Badge carries the counts of the buttons inside
                val overflowBadgeCount = setupChecklist.pendingCount
                val overflowNeedsAttention = overflowBadgeCount > 0 ||
                    (!syncOnBar && isSignedIn && (hasSyncError || pendingSyncCount > 0))
                if (overflowNeedsAttention) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp)
                            .size(16.dp)
                            .background(EInkTheme.Colors.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                overflowBadgeCount > 9 -> "9+"
                                overflowBadgeCount > 0 -> overflowBadgeCount.toString()
                                else -> "!"
                            },
                            color = EInkTheme.Colors.white,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isOverflowOpen) {
                    val popupOffsetY = with(LocalDensity.current) { (EInkTheme.Global.touchTarget * HEADER_BAR_ICON_SCALE).roundToPx() }
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset = IntOffset(0, popupOffsetY),
                        onDismissRequest = { if (!isSettingsMenuOpen) isOverflowOpen = false },
                        properties = PopupProperties(focusable = true)
                    ) {
                        // Icons in the ⋮ popup are shown a bit larger than on the bar (Oct 2026)
                        CompositionLocalProvider(LocalHeaderIconScale provides HEADER_POPUP_ICON_SCALE) {
                        // The settings menu opens right under its button
                        NestedPopupHost(hostView = headerHostView) {
                        Row(
                            modifier = Modifier
                                .background(EInkTheme.PopupMenu.background)
                                .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor)
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            voiceButton()
                            Spacer(modifier = Modifier.width(7.dp))
                            if (!syncOnBar) {
                                syncButton()
                                Spacer(modifier = Modifier.width(7.dp))
                            }
                            streakButton()
                            Spacer(modifier = Modifier.width(10.dp))
                            if (!searchOnBar) {
                                searchButton()
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            settingsButton()
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                        }
                        }
                    }
                }
            }
        }
        }
    } else Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: Month navigation
        IconButton(onClick = onPreviousMonth, modifier = Modifier.size(EInkTheme.Global.touchTarget)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous month",
                modifier = Modifier.size(EInkTheme.Global.iconSizeLarge),
                tint = EInkTheme.Colors.primary
            )
        }

        Spacer(modifier = Modifier.width(EInkTheme.CalendarToolbar.chevronToMonthSpacing))

        // Month label with dropdown - fixed width to prevent layout shift. The width grows with the
        // system font scale (Go 6: 1.15), else "September" ran into the year (Oct 2026). Narrow screens
        // (Go 6): the label takes the month's own width, so the six header buttons fit at touch size
        Box(
            modifier = if (LayoutProfiles.isNarrowScreen) Modifier.padding(end = 8.dp)
            else Modifier.width(EInkTheme.CalendarToolbar.monthLabelWidth * LocalDensity.current.fontScale),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = monthNames[month],
                fontSize = EInkTheme.CalendarToolbar.monthFontSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .noRippleClickable { isMonthDropdownOpen = true }
                    .padding(vertical = 4.dp)
            )

            androidx.compose.material.DropdownMenu(
                expanded = isMonthDropdownOpen,
                onDismissRequest = { isMonthDropdownOpen = false },
                modifier = Modifier.background(EInkTheme.Colors.background)
            ) {
                monthNames.forEachIndexed { index, monthName ->
                    androidx.compose.material.DropdownMenuItem(
                        onClick = {
                            isMonthDropdownOpen = false
                            if (index != month) {
                                onMonthYearSelected(year, index)
                            }
                        }
                    ) {
                        Text(
                            text = monthName,
                            fontWeight = if (index == month) FontWeight.Bold else FontWeight.Normal,
                            color = if (index == month) EInkTheme.Colors.primary else EInkTheme.Colors.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(EInkTheme.CalendarToolbar.monthToYearSpacing))

        // Year label with dropdown
        Box {
            Text(
                text = year.toString(),
                fontSize = EInkTheme.CalendarToolbar.yearFontSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .noRippleClickable { isYearDropdownOpen = true }
                    .padding(vertical = 4.dp)
            )

            androidx.compose.material.DropdownMenu(
                expanded = isYearDropdownOpen,
                onDismissRequest = { isYearDropdownOpen = false },
                modifier = Modifier.background(EInkTheme.Colors.background)
            ) {
                years.forEach { yearOption ->
                    androidx.compose.material.DropdownMenuItem(
                        onClick = {
                            isYearDropdownOpen = false
                            if (yearOption != year) {
                                onMonthYearSelected(yearOption, month)
                            }
                        }
                    ) {
                        Text(
                            text = yearOption.toString(),
                            fontWeight = if (yearOption == year) FontWeight.Bold else FontWeight.Normal,
                            color = if (yearOption == year) EInkTheme.Colors.primary else EInkTheme.Colors.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(EInkTheme.CalendarToolbar.yearToChevronSpacing))

        IconButton(onClick = onNextMonth, modifier = Modifier.size(EInkTheme.Global.touchTarget)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next month",
                modifier = Modifier.size(EInkTheme.Global.iconSizeLarge),
                tint = EInkTheme.Colors.primary
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Right side: Toolbar icons with spacing. Today comes first (back on 14 Oct 2026 at the owner's
        // request; it is the only Today button since DayHeader's shortcut was removed on 15 Oct 2026).
        if (LayoutProfiles.isNarrowScreen) {
            // Narrow screens (Go 6, Oct 2026): six buttons at full touch size, spread evenly like the
            // toolbar. All eight did not fit (search and settings went off the screen): the streak and
            // What's New are in the settings menu, and sync shows its state on the icon only.
            todayButton()
            HeaderGap()
            voiceButton()
            HeaderGap()
            addEventButton()
            HeaderGap()
            syncIcon()
            HeaderGap()
            searchButton()
            HeaderGap()
            settingsButton()
        } else {
            todayButton()

            Spacer(modifier = Modifier.width(7.dp))

            voiceButton()

            Spacer(modifier = Modifier.width(7.dp))

            addEventButton()

            Spacer(modifier = Modifier.width(8.dp))

            trailingButtons()
        }
    }

    // Task Statistics Popup
    if (showStatsPopup) {
        TaskStatsPopup(
            selectedDate = LocalDate.now(),
            onDismiss = { showStatsPopup = false }
        )
    }
}

/** Height of [DayHeader]. */
private val DAY_HEADER_HEIGHT = 50.dp

/**
 * Header of the calendar area (Oct 2026, like the BOOX Calendar): the selected date in big type and the
 * marks of that day (Boox Notes, memo, tasks). The marks used to sit on every day cell; a mark is shown
 * only when the day has it. No Today shortcut here (15 Oct 2026): the Today button is in the top bar.
 */
@Composable
fun DayHeader(
    selectedDate: String,
    hasBooxNotes: Boolean,
    hasMemo: Boolean,
    hasTasks: Boolean,
    onBooxNotesClick: () -> Unit,
    onMemoClick: () -> Unit,
    onTasksClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val date = remember(selectedDate) {
        try { LocalDate.parse(selectedDate) } catch (e: Exception) { null }
    }
    val weekdayName = remember(date) {
        date?.dayOfWeek?.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()).orEmpty()
    }
    val dateText = remember(date) {
        date?.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())).orEmpty()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DAY_HEADER_HEIGHT)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = (date?.dayOfMonth ?: 0).toString(),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = weekdayName,
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = EInkTheme.Colors.primary,
                maxLines = 1
            )
            Text(
                text = dateText,
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.darkGray,
                maxLines = 1
            )
        }

        // Marks of the selected day (each only when the day has it)
        if (hasBooxNotes) {
            DayMark(contentDescription = "Boox Notes of this day", onClick = onBooxNotesClick) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(EInkTheme.Colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.boox_b_letter_tiny),
                        contentDescription = null,
                        tint = EInkTheme.Colors.background,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        if (hasMemo) {
            DayMark(contentDescription = "Memo of this day", onClick = onMemoClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_memo_badge),
                    contentDescription = null,
                    tint = EInkTheme.Colors.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        if (hasTasks) {
            DayMark(contentDescription = "Tasks of this day", onClick = onTasksClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check_badge),
                    contentDescription = null,
                    tint = EInkTheme.Colors.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/** One tappable mark in [DayHeader]: a 40 x 42 dp touch box around an icon. */
@Composable
private fun DayMark(
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 42.dp)
            .noRippleClickable { onClick() }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun DayOfWeekHeader(
    selectedDate: String? = null,
    firstDayOfWeekSetting: AppSettings.FirstDayOfWeek = AppSettings.FirstDayOfWeek.Sunday,
    showWeekNumbers: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Week numbers are off on narrow screens (the Go 6, Oct 2026); the setting still applies on bigger ones
    val weekNumbersShown = showWeekNumbers && !LayoutProfiles.isNarrowScreen

    // Base days list (Sunday = index 0). Three letters on one line: the second line with the full
    // name broke mid-word in the narrow columns of the Go 6 (e.g. "Wednes / day") and made this row 77 dp tall.
    val baseDaysOfWeek = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    // Reorder days based on first day of week setting
    val daysOfWeek = remember(firstDayOfWeekSetting) {
        when (firstDayOfWeekSetting) {
            AppSettings.FirstDayOfWeek.Sunday -> baseDaysOfWeek
            AppSettings.FirstDayOfWeek.Monday -> baseDaysOfWeek.drop(1) + baseDaysOfWeek.take(1)
        }
    }

    // Determine current day of week, adjusted for the firstDayOfWeek setting
    val currentDayOfWeek = remember(selectedDate, firstDayOfWeekSetting) {
        val rawDayOfWeek = if (selectedDate != null) {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = dateFormat.parse(selectedDate)
                val cal = Calendar.getInstance()
                if (date != null) {
                    cal.time = date
                    cal.get(Calendar.DAY_OF_WEEK) - 1 // Convert to 0-based index (Sunday = 0)
                } else {
                    Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
                }
            } catch (e: Exception) {
                Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
            }
        } else {
            Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
        }

        // Adjust index based on first day of week setting
        when (firstDayOfWeekSetting) {
            AppSettings.FirstDayOfWeek.Sunday -> rawDayOfWeek
            AppSettings.FirstDayOfWeek.Monday -> (rawDayOfWeek + 6) % 7  // Shift: Mon=0, Tue=1, ..., Sun=6
        }
    }

    // Wrap in Column to keep Row and bottom border together
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
                .background(EInkTheme.Colors.background)
        ) {
            // Week number column: only present when week numbers are shown (it used to be reserved
            // always; the Go 6 hides week numbers now, so the days get the 18 dp)
            if (weekNumbersShown) {
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Wk",
                        fontSize = EInkTheme.Typography.labelSmall,
                        fontWeight = FontWeight.Normal,
                        color = EInkTheme.Colors.gray,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.wrapContentWidth(unbounded = true)
                    )
                }
            }

            daysOfWeek.forEachIndexed { index, name ->
                val isSelected = index == currentDayOfWeek

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 2.dp, bottom = 1.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Three-letter day name on one line - larger when selected
                    Text(
                        text = name,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        fontSize = if (isSelected) EInkTheme.Typography.titleSmall else EInkTheme.CalendarGrid.dayFontSize,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) EInkTheme.Colors.primary else EInkTheme.CalendarGrid.weekdayColor
                    )

                    // Tab indicator for selected day
                    if (isSelected) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(EInkTheme.Toolbar.tabIndicatorHeight)
                                .background(EInkTheme.Colors.primary, RoundedCornerShape(1.5.dp))
                        )
                    }
                }
            }
        }

        // Bottom border for the entire header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(EInkTheme.Colors.borderStrong)
        )
    }
}

@Composable
fun CalendarGrid(
    year: Int,
    month: Int,
    selectedDate: String,
    today: String,
    badges: CalendarBadges,
    onDateSelected: (String) -> Unit,
    onDayLongPress: ((String) -> Unit)? = null,
    firstDayOfWeekSetting: AppSettings.FirstDayOfWeek = AppSettings.FirstDayOfWeek.Sunday,
    showWeekNumbers: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Current month calendar
    val calendar = remember(year, month) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }

    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    // Raw first day of week (0 = Sunday)
    val rawFirstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
    // Adjust for first day of week setting
    val firstDayOfWeek = when (firstDayOfWeekSetting) {
        AppSettings.FirstDayOfWeek.Sunday -> rawFirstDayOfWeek
        AppSettings.FirstDayOfWeek.Monday -> (rawFirstDayOfWeek + 6) % 7  // Shift: Mon=0, Sun=6
    }

    // Previous month info
    val prevMonthCalendar = remember(year, month) {
        (calendar.clone() as Calendar).apply {
            add(Calendar.MONTH, -1)
        }
    }
    val daysInPrevMonth = prevMonthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val prevYear = prevMonthCalendar.get(Calendar.YEAR)
    val prevMonth = prevMonthCalendar.get(Calendar.MONTH)

    // Next month info
    val nextMonthCalendar = remember(year, month) {
        (calendar.clone() as Calendar).apply {
            add(Calendar.MONTH, 1)
        }
    }
    val nextYear = nextMonthCalendar.get(Calendar.YEAR)
    val nextMonth = nextMonthCalendar.get(Calendar.MONTH)

    // Always show 6 rows for consistent height
    val rows = 6

    // Week numbers are off on narrow screens (the Go 6, Oct 2026); the setting still applies on bigger ones
    val weekNumbersShown = showWeekNumbers && !LayoutProfiles.isNarrowScreen

    Column(modifier = modifier) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Week number column (only when week numbers are shown, aligned with the header)
                val weekNumber = if (weekNumbersShown) {
                    // Calculate week number for first day of this row
                    val firstCellIndex = row * 7
                    val firstDayOffset = firstCellIndex - firstDayOfWeek + 1
                    val weekCalendar = (calendar.clone() as Calendar).apply {
                        when {
                            firstDayOffset < 1 -> {
                                set(Calendar.YEAR, prevYear)
                                set(Calendar.MONTH, prevMonth)
                                set(Calendar.DAY_OF_MONTH, daysInPrevMonth + firstDayOffset)
                            }
                            firstDayOffset <= daysInMonth -> {
                                set(Calendar.DAY_OF_MONTH, firstDayOffset)
                            }
                            else -> {
                                set(Calendar.YEAR, nextYear)
                                set(Calendar.MONTH, nextMonth)
                                set(Calendar.DAY_OF_MONTH, firstDayOffset - daysInMonth)
                            }
                        }
                    }
                    weekCalendar.get(Calendar.WEEK_OF_YEAR)
                } else null

                if (weekNumber != null) {
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = weekNumber.toString(),
                            fontSize = EInkTheme.Typography.labelSmall,
                            fontWeight = FontWeight.Normal,
                            color = EInkTheme.Colors.gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    val dayOffset = cellIndex - firstDayOfWeek + 1

                    val (displayDay, displayYear, displayMonth, isOtherMonth) = when {
                        // Previous month days
                        dayOffset < 1 -> {
                            val day = daysInPrevMonth + dayOffset
                            arrayOf(day, prevYear, prevMonth, true)
                        }
                        // Current month days
                        dayOffset <= daysInMonth -> {
                            arrayOf(dayOffset, year, month, false)
                        }
                        // Next month days
                        else -> {
                            val day = dayOffset - daysInMonth
                            arrayOf(day, nextYear, nextMonth, true)
                        }
                    }

                    val dateString = String.format(
                        Locale.getDefault(),
                        "%04d-%02d-%02d",
                        displayYear as Int,
                        (displayMonth as Int) + 1,
                        displayDay as Int
                    )
                    val isSelected = dateString == selectedDate
                    val isToday = dateString == today
                    val isPast = dateString < today && !(isOtherMonth as Boolean)
                    val hasEvents = badges.datesWithEvents.contains(dateString)
                    val hasBooxNotes = badges.datesWithBooxNotes.contains(dateString)

                    DayCell(
                        day = displayDay,
                        dateString = dateString,
                        isSelected = isSelected,
                        isToday = isToday,
                        isPast = isPast,
                        hasEvents = hasEvents,
                        hasBooxNotes = hasBooxNotes,
                        isOtherMonth = isOtherMonth as Boolean,
                        onClick = { onDateSelected(dateString) },
                        onLongPress = onDayLongPress,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    dateString: String,
    isSelected: Boolean,
    isToday: Boolean,
    isPast: Boolean = false,
    hasEvents: Boolean = false,
    hasBooxNotes: Boolean = false,
    isOtherMonth: Boolean = false,
    onClick: () -> Unit,
    onLongPress: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // E-ink friendly colors from theme
    val pastDayGray = EInkTheme.Colors.pastGray  // #AAAAAA - Kaleido 3 visible
    val pastBadgeGray = EInkTheme.Colors.pastGray  // #AAAAAA - Kaleido 3 visible
    val otherMonthGray = EInkTheme.CalendarGrid.dayColorOutsideMonth  // Light gray for other month days

    // Oct 2026 (like the BOOX Calendar): a day is its number plus one dot for calendar events. The
    // Boox Notes B stays in the corner. Memo and task marks moved into the selected-day header.
    // The selected day is a black square (also when it is today). Today, when not selected, is a ring.
    val backgroundColor = if (isSelected) EInkTheme.CalendarGrid.selectedBackground else Color.Transparent

    val textColor = when {
        isSelected -> EInkTheme.CalendarGrid.selectedForeground
        isOtherMonth -> otherMonthGray
        isPast -> pastDayGray
        isToday -> EInkTheme.CalendarGrid.todayTextColor
        else -> EInkTheme.CalendarGrid.dayColor
    }

    val indicatorColor = when {
        isSelected -> EInkTheme.CalendarGrid.selectedForeground
        isOtherMonth -> otherMonthGray
        else -> EInkTheme.Colors.primary  // Boox Notes stays black even for past days
    }

    // Event dot color (grayed for past days)
    val dotColor = when {
        isSelected -> EInkTheme.CalendarGrid.selectedForeground
        isOtherMonth -> otherMonthGray
        isPast -> pastBadgeGray
        else -> EInkTheme.Colors.primary
    }

    val borderModifier = if (isToday && !isOtherMonth && !isSelected) {
        Modifier.border(EInkTheme.CalendarGrid.todayBorderWidth, EInkTheme.CalendarGrid.todayBorderColor, RoundedCornerShape(6.dp))
    } else {
        Modifier
    }

    // Cell layout: [B] top-left corner, number top-centre (nudged right when there is a B), event dot bottom-centre.

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(1.dp)
            .clip(RoundedCornerShape(6.dp))
            .then(borderModifier)
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .pointerInput(dateString) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress?.invoke(dateString) }
                )
            }
    ) {
        // Boox Notes badge, top-left corner
        if (hasBooxNotes) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 2.dp, top = 2.dp)
                    .size(EInkTheme.CalendarGrid.booxNoteContainerSize)
                    .clip(CircleShape)
                    .background(indicatorColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.boox_b_letter_tiny),
                    contentDescription = "Has Boox Notes",
                    tint = if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.background,
                    modifier = Modifier.size(EInkTheme.CalendarGrid.booxNoteIconSize)
                )
            }
        }

        // Day number, top-centre. With a B in the corner it moves 2.5 dp to the right so they never touch.
        Text(
            text = day.toString(),
            color = textColor,
            fontSize = EInkTheme.Typography.bodyLarge,
            fontWeight = if ((isToday || isSelected) && !isOtherMonth) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(start = if (hasBooxNotes) 5.dp else 0.dp, top = 1.dp)
        )

        // Event indicator - one dot below the day number (shows calendar events)
        if (hasEvents) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = EInkTheme.CalendarGrid.eventIndicatorBottomPadding)
                    .width(EInkTheme.CalendarGrid.eventIndicatorWidth)
                    .height(EInkTheme.CalendarGrid.eventIndicatorHeight)
                    .background(color = dotColor, shape = RoundedCornerShape(EInkTheme.CalendarGrid.eventIndicatorCornerRadius))
            )
        }
    }
}

/**
 * Format the last sync time as a human-readable "X ago" string.
 */
private fun formatLastSyncTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestamp

    val seconds = diffMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> {
            val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}

/** Gap between the compact header's icons (layout B), so a finger tap never lands on two. */
private val HEADER_ICON_GAP = 10.dp

/**
 * Gap between the header's buttons on narrow screens (Go 6, Oct 2026): at least 8 dp plus an equal
 * share of the header's free width, so the buttons spread evenly, as on the toolbar.
 */
@Composable
private fun RowScope.HeaderGap() {
    Spacer(modifier = Modifier.width(8.dp))
    Spacer(modifier = Modifier.weight(1f))
}

/** Compact header button size relative to the 44dp touch target: on the bar and in the ⋮ popup. */
private const val HEADER_BAR_ICON_SCALE = 1.2f
private const val HEADER_POPUP_ICON_SCALE = 1.3f

/** Size scale for the header's icon buttons; the compact ⋮ popup raises it (Oct 2026), elsewhere 1. */
private val LocalHeaderIconScale = androidx.compose.runtime.staticCompositionLocalOf { 1f }
