package com.boox.day.ui.dialogs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.components.ScrollableWithIndicator
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.components.ScrollMoreBar
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ScrollState
import com.boox.day.ui.theme.EInkTheme

/**
 * Help Dialog for BooxDay
 * Split-screen layout: Left navigation (30%) + Right content (70%)
 * Optimized for e-ink display with high contrast and clear typography
 */
@Composable
fun HelpDialog(
    onDismiss: () -> Unit
) {
    var selectedSection by remember { mutableIntStateOf(0) }

    val sections = remember {
        listOf(
            HelpSection("Quick Start", Icons.Default.PlayArrow),
            HelpSection("Touch Gestures", Icons.Default.TouchApp),
            HelpSection("Writing & Memos", Icons.Default.Edit),
            HelpSection("Task Management", Icons.Default.CheckBox),
            HelpSection("Calendar & Sync", Icons.Default.CalendarToday),
            HelpSection("Search", Icons.Default.Search),
            HelpSection("AI Features", Icons.Default.AutoAwesome),
            HelpSection("Smart Link", Icons.Default.Link),
            HelpSection("Voice Input", Icons.Default.Mic),
            HelpSection("E-Ink & Sleep Screen", Icons.Default.PhoneAndroid),
            HelpSection("Data & Troubleshooting", Icons.Default.Warning),
            HelpSection("Key Legends", Icons.Default.VpnKey)
        )
    }

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
                    .fillMaxWidth(EInkTheme.PanelDialog.widthFraction)  // Oct 2026: was 0.85f
                    .fillMaxHeight(EInkTheme.PanelDialog.heightFraction)  // was 0.80f
                    .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            ) {
                // Header
                HelpHeader(onDismiss = onDismiss)

                Divider(color = EInkTheme.Colors.primary, thickness = EInkTheme.Global.borderWidth)

                // Main content: Split screen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Left Navigation (30%)
                    NavigationMenu(
                        sections = sections,
                        selectedIndex = selectedSection,
                        onSectionSelected = { selectedSection = it },
                        modifier = Modifier
                            .weight(0.30f)
                            .fillMaxHeight()
                    )

                    // Vertical Divider
                    Divider(
                        color = EInkTheme.Colors.primary,
                        modifier = Modifier
                            .width(EInkTheme.Global.borderWidth)
                            .fillMaxHeight()
                    )

                    // Right Content (70%)
                    HelpContent(
                        sectionIndex = selectedSection,
                        modifier = Modifier
                            .weight(0.70f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EInkTheme.Global.padding, vertical = EInkTheme.Global.paddingMedium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Help,
                contentDescription = null,
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.size(EInkTheme.Global.iconSize)
            )
            Text(
                text = "Help & User Guide",
                fontSize = EInkTheme.Typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary
            )
        }

        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.height(EInkTheme.Button.height),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = EInkTheme.Colors.primary
            ),
            border = BorderStroke(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary),
            shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
        ) {
            Text(
                text = "Close",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun NavigationMenu(
    sections: List<HelpSection>,
    selectedIndex: Int,
    onSectionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    Box(modifier = modifier.background(EInkTheme.Colors.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(sections) { index, section ->
                val isSelected = index == selectedIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = EInkTheme.Global.touchTarget)
                        .noRippleClickable { onSectionSelected(index) }
                        .background(
                            if (isSelected) EInkTheme.Colors.primary
                            else EInkTheme.Colors.background
                        )
                        .padding(horizontal = EInkTheme.Global.padding, vertical = EInkTheme.Global.paddingMedium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
                ) {
                    Icon(
                        imageVector = section.icon,
                        contentDescription = null,
                        tint = if (isSelected) EInkTheme.Colors.background
                               else EInkTheme.Colors.darkGray,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = section.title,
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) EInkTheme.Colors.background
                                else EInkTheme.Colors.primary
                    )
                }

                if (index < sections.lastIndex) {
                    Divider(
                        color = EInkTheme.Colors.lightGray,
                        thickness = EInkTheme.Global.borderWidth
                    )
                }
            }
        }
        ScrollMoreBar(listState)
    }
}

@Composable
private fun HelpContent(
    sectionIndex: Int,
    modifier: Modifier = Modifier
) {
    // E-Ink: use ScrollableWithIndicator for scroll feedback. Each topic opens at its top (Oct 2026:
    // one scroll state for all topics kept the last topic's position)
    ScrollableWithIndicator(
        modifier = modifier,
        scrollState = remember(sectionIndex) { ScrollState(0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(EInkTheme.Global.padding),
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
        ) {
            when (sectionIndex) {
                0 -> QuickStartContent()
                1 -> TouchGesturesContent()
                2 -> WritingMemosContent()
                3 -> TaskManagementContent()
                4 -> CalendarSyncContent()
                5 -> SearchContent()
                6 -> AiFeaturesContent()
                7 -> SmartLinkContent()
                8 -> VoiceInputContent()
                9 -> EInkOptimizationContent()
                10 -> TroubleshootingContent()
                11 -> KeyLegendsContent()
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION 1: QUICK START
// Oct 2026: every Help topic rewritten from the code (plan 4.7); labels are the app's own
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun QuickStartContent() {
    SectionHeader("Quick Start")

    SectionSubtitle("Welcome to BooxDay.")
    BodyText("A daily planner for your Boox tablet: the calendar and the chosen day's events on top, and below them two tabs for that day.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("The Screen")
    BulletItem("Calendar", "Tap a date to open that day. Long-press a date for a short preview.")
    BulletItem("Events", "The chosen day's events.")
    BulletItem("Memos", "Pages for handwriting and drawing.")
    BulletItem("Tasks", "Rows for your tasks, 10 to a page.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Getting Around")
    BulletPoint("The today button (a calendar with today's number, in the calendar header) goes back to today.")
    BulletPoint("The arrows in the toolbar turn the day's pages; the counter shows where you are, for example 1/3.")
    BulletPoint("The full screen switch, just before ≡ in the toolbar, hides the calendar to give the page more room.")
    BulletPoint("≡ at the end of the toolbar holds Screen Refresh, Export Pages, and clearing or deleting pages.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Settings")
    BodyText("The Settings menu has Preferences, Calendar & Sync, Calendar Accounts, AI Settings, Voice-to-Text, Data & Backup, Boox Notes, Help and About. A dot next to an item marks a setting worth a look first.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION 2: TOUCH GESTURES
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TouchGesturesContent() {
    SectionHeader("Touch Gestures")

    SectionSubtitle("Your pen writes; your finger opens and selects.")
    BodyText("For a long-press, hold your finger still for a moment.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    SectionSubheader("Calendar")

    GestureItem(
        gesture = "Tap a date",
        action = "Open that day"
    )

    GestureItem(
        gesture = "Long-press a date",
        action = "Preview the day",
        details = "A popup shows how many events and tasks the day has, each with a View button, and the day's Boox Notes with an Open button."
    )

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    SectionSubheader("Tasks Tab")

    GestureItem(
        gesture = "Tap › on a row",
        action = "Open the task",
        details = "On an empty row it opens Type task. On a task it opens the task card: move, repeat, reminder and more."
    )

    GestureItem(
        gesture = "Long-press ›",
        action = "Edit the text",
        details = "Opens the text editor straight away."
    )

    GestureItem(
        gesture = "Long-press a checkbox",
        action = "Tick the whole page",
        details = "Ticks every task on the page. If they are all ticked, it unticks them."
    )

    GestureItem(
        gesture = "Long-press a task row",
        action = "Clear the task",
        details = "Removes the task from its row."
    )

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    SectionSubheader("Memos Tab")

    GestureItem(
        gesture = "Long-press an image",
        action = "Select it",
        details = "\"Image selected!\" appears with corner handles to resize it. Drag to move it; tap elsewhere when done."
    )

    GestureItem(
        gesture = "Long-press an attachment",
        action = "Select it",
        details = "\"Attachment selected!\" Move it like an image."
    )

    GestureItem(
        gesture = "Long-press a text box",
        action = "Select it",
        details = "Tap inside a selected text box to edit its text."
    )

    GestureItem(
        gesture = "Long-press an empty spot",
        action = "Select an area",
        details = "\"Selection mode!\" and a target appear. Drag a rectangle around the ink; the selection bar then offers Delete, Cut, Copy, Duplicate, AI Recognize and Convert to Tasks."
    )

    GestureItem(
        gesture = "Two-finger tap",
        action = "Paste",
        details = "Pastes ink or an image you copied in BooxDay, or text copied in another app as a text box."
    )

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    SectionSubheader("Troubleshooting")

    TroubleshootingItem(
        problem = "Long-press not working?",
        solution = "Hold your finger still; a long-press that moves counts as a drag."
    )

    TroubleshootingItem(
        problem = "In selection mode by accident?",
        solution = "Tap anywhere else to leave it."
    )
}

/**
 * A styled gesture item showing the gesture name, action, and optional details.
 */
@Composable
private fun GestureItem(gesture: String, action: String, details: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = EInkTheme.Global.spacingSmall)
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.borderStrong, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
            .padding(EInkTheme.Global.paddingMedium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = gesture,
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary
            )
            Text(
                text = action,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.darkGray
            )
        }
        if (details != null) {
            Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
            Text(
                text = details,
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.gray
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION 3: WRITING & MEMOS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun WritingMemosContent() {
    SectionHeader("Writing & Memos")

    SectionSubtitle("Your pages for the day.")
    BodyText("A day has as many memo pages as you need: the forward arrow on the last page adds a new one.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Toolbar")
    BulletItem("Pens", "Pen, Fountain Pen, Marker, Pencil and Brush. Tap the pen again to switch; each has five sizes and a choice of colors.")
    BulletItem("Erasers", "Standard Eraser and Stroke Eraser. With \"Scribble to Erase\" (in the eraser menu), scribbling over ink erases it.")
    BulletItem("Lasso select", "Draw around ink to select it, then move, copy or delete it.")
    BulletItem("Insert", "Image, Background, Table, Link File (a PDF, EPUB or other file on the tablet) and Text.")
    BulletItem("AI tools", "Voice to Text, Recognize Handwriting, Summarize, Translate and Convert Memo to Tasks. See AI Features.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Page Actions (≡)")
    BulletItem("Export Pages", "This page or all pages of the day, as PDF, PNG or JPG, saved in Documents/BooxDay.")
    BulletItem("Clear This Page", "Empties the page and keeps it.")
    BulletItem("Delete Page", "Removes the page.")
    BulletItem("Clear This Day's Memos", "Empties every memo page of the day on screen. It can't be undone.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Full Screen")
    BodyText("The switch just before ≡ hides the calendar, so the page gets the whole screen. Tap it again for the normal view. When the switch shows a small arrow, there is ink lower on the page than the normal view shows.")
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION 4: TASK MANAGEMENT
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TaskManagementContent() {
    SectionHeader("Task Management")

    SectionSubtitle("One row per task.")
    BodyText("A task page has 10 rows, and a day can have more pages. In full screen a page shows about 17 rows.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Writing a Task")
    BodyText("Write on a row with the pen. BooxDay reads handwritten tasks on the tablet, so search finds them. To type instead, tap › on an empty row.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("The Task Card (tap › on a task)")
    BulletItem("Edit text / Convert to text", "Type the text, or turn the handwriting into text.")
    BulletItem("Move", "To another date; Copy keeps it here too.")
    BulletItem("Repeat", "Makes it a recurring task.")
    BulletItem("Reminder", "A time to be reminded.")
    BulletItem("Break Down", "Splits it into smaller tasks (AI).")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Priorities")
    BodyText("Tap a row's priority button (\"-\" when none) and pick None, A (High), B (Medium) or C (Low). A shows white on black, B in bold, C with a thin border.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Done and Streak")
    BulletPoint("Tick the checkbox: the row gets a line through it.")
    BulletPoint("The flame in the calendar header counts the days in a row on which you finished a task. Tap it for your stats. On small screens it is in the Settings menu (Task streak).")
    BulletPoint("≡ → Clear This Day's Tasks empties the task pages of the day on screen.")
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION 5: CALENDAR & SYNC
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CalendarSyncContent() {
    SectionHeader("Calendar & Sync")

    SectionSubtitle("Your calendars, on the tablet.")
    BodyText("Your memos, tasks and calendar are stored on this tablet.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Calendar Mode (Settings → Calendar & Sync)")
    BulletItem("Private Mode", "Everything stays on the tablet. No account needed.")
    BulletItem("Cloud Sync", "Also shows your Google and Outlook calendars.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Accounts (Settings → Calendar Accounts)")
    BulletPoint("Add Gmail Account, Add Workspace Account, Add Outlook.com Account or Add Work/School Account.")
    BulletPoint("Disconnect removes an account from BooxDay.")
    BulletPoint("A new event goes to a Cloud Calendar when an account is connected. Under Save to, pick This device only to keep it on the tablet: it never syncs.")
    BulletPoint("Events sync from two months back to three months ahead.")
    BulletPoint("BooxDay asks Google for little access to your calendar: only your events and your list of calendars. If you signed in with an older version, BooxDay asks you once to reconnect; until you do, your Google events don't update. You can also reconnect in Settings → Calendar Accounts.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Sync Status")
    BodyText("The cloud icon in the calendar header turns while syncing and shows a number when changes wait to be sent. Tap it to sync now. Next to it: Syncing..., Sync failed, or the time of the last sync.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Privacy")
    BodyText("Calendar sync sends and receives events only. Memos and tasks are never synced; Drive backup, if you turn it on, copies them to your own Google Drive.")
    BodyText("The marks on the calendar are in Key Legends.")
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION 6: SEARCH
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SearchContent() {
    SectionHeader("Search")

    SectionSubtitle("Find anything in your planner.")
    BodyText("Tap the magnifier in the calendar header and type at least 2 characters.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("What It Finds")
    BulletItem("Memos and Boox Notes", "The words read from your handwriting, once the pages are indexed (see Smart Link).")
    BulletItem("Tasks", "Typed text, and handwriting read on the tablet.")
    BulletItem("Events", "Google, Outlook and the tablet's own events.")
    BodyText("The filters All, Memos, Tasks, Events and Notes narrow the results.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("How It Matches")
    BodyText("Each word you type matches the start of words, whatever the accents or capitals. Events and tasks that only contain your text inside a word are marked SIMILAR.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Results")
    BulletPoint("Up to 50, best matches first, then the newest.")
    BulletPoint("Memo and Boox Notes results show their topic and word count.")
    BulletPoint("Tap a result to go to its day. A task opens; a Boox Notes result opens in the Boox Notes app.")
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION 7: AI FEATURES
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AiFeaturesContent() {
    SectionHeader("AI Features")

    SectionSubtitle("Optional, on your own OpenRouter account.")
    BodyText("AI features run on your own OpenRouter account, so OpenRouter bills you, not BooxDay. Without credit on the account, BooxDay uses free models, which OpenRouter limits to a number of requests a day.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Setting Up (Settings → AI Settings)")
    BulletItem("Connect OpenRouter", "Sign in to OpenRouter in the browser; BooxDay saves the key. You can also paste a key.")
    BulletItem("AI Features", "The switch for voice input, the AI tools and reading pages for search. With it off, none of your data goes to OpenRouter.")
    BulletItem("AI Models", "The model for each job: Reading handwriting, Voice, Writing and summaries, Planning and suggestions, Search indexing. If a model fails, BooxDay's choice answers.")
    BulletItem("AI Preferences", "Five short tabs: Priorities, About Me, Daily Rhythm, Lifestyle and AI Style (with Vacation, Crunch and Holiday modes). Smart Suggestions and Optimize My Day use your answers. Closing keeps what you picked.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("AI Tools in Memos")
    BulletItem("Voice to Text", "Dictate; BooxDay adds punctuation and capitals, and lists where they are clear.")
    BulletItem("Recognize Handwriting", "Reads the page's ink: copy the text, replace the handwriting with it, or place it below.")
    BulletItem("Summarize and Translate", "The current page or all pages of the day.")
    BulletItem("Convert Memo to Tasks", "Finds tasks in your notes; tick the ones to add.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Task AI")
    BulletItem("Optimize My Day", "Proposes changes to your day, such as moving a task or blocking time. Nothing changes until you tap a proposal's button; Skip leaves it.")
    BulletItem("Smart Suggestions", "Task ideas from your calendar, recent tasks, memos and AI Preferences. Add the ones you want, or all with Add All.")
    BulletItem("Sort by Priority", "Orders this page or the whole day by importance, on the tablet. Check the order, then tap Apply Order.")
    BulletItem("Break Down Task", "Splits a big task into smaller ones.")
    BulletItem("Daily Summary", "What you finished today; move unfinished tasks to tomorrow one by one.")
    BulletItem("Weekly Summary", "The week's totals, your best and toughest days, tasks left undone several times, and insights.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("How the AI Works")
    BodyText("When you use an AI feature, only what it needs (page images, memo text, your tasks and events, or a voice clip) is sent to OpenRouter and the company that runs the model. BooxDay has no server of its own. Some free models may keep what they are sent. The AI doesn't learn from your use: each request carries what it needs, plus your AI Preferences.")
    BodyText("The first time you use Optimize My Day, Daily Summary, Weekly Summary or Smart Suggestions, BooxDay tells you which calendar details it sends to OpenRouter, and how to keep them private: paid models (AI Settings → AI Models) and Zero Data Retention in your OpenRouter account's privacy settings. Free models keep working.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION 8: SMART LINK
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SmartLinkContent() {
    SectionHeader("Smart Link")

    SectionSubtitle("Your Boox notes, linked to your events.")
    BodyText("BooxDay reads the notes you write in the Boox Notes app and suggests which calendar event each one belongs to.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Setting Up (Settings → Boox Notes)")
    BulletPoint("Allow file access once (Open Permission Settings), then turn on Enable Indexing.")
    BulletPoint("Reading pages needs an OpenRouter key and AI Features on; up to 1,000 pages a month are read. Linking notes by hand works without them.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Using It")
    BulletPoint("Open Boox Notes with the B in the day header, or from the Notes button in the Memos toolbar.")
    BulletPoint("Pick a note to see its pages, link it to an event, or open it in Boox Notes.")
    BulletPoint("Review suggestions lists the likely matches: link them one by one, or all strong ones with Link strong….")
    BulletPoint("An event's details show its linked notes.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Tips")
    BulletPoint("Names and topics in a note's title help Smart Link.")
    BulletPoint("A date in the title sets the note's day.")
    BulletPoint("Action items: in a calendar event's Meeting notes, it lists the to-do lines in that day's memos, read on the tablet (English).")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Privacy")
    BodyText("Finding topics and linking notes to events run on the tablet. Only reading the pages uses AI: while AI Features and Settings → Boox Notes → Enable Indexing are on, memo and Boox Notes pages are sent to OpenRouter, on your own account, and only to providers that keep no copy. With either off, no pages are sent.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION 9: VOICE INPUT
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun VoiceInputContent() {
    SectionHeader("Voice Input")

    SectionSubtitle("Speak instead of writing.")
    BodyText("Voice needs AI Features turned on (Settings → AI Settings).")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Where")
    BulletItem("Voice to Text", "The AI button in Memos: dictate a note.")
    BulletItem("Voice events", "The microphone in the calendar header, or in a new event: describe it, for example \"Meeting with Sarah tomorrow at 2pm\".")
    BodyText("Recording stops by itself a few seconds after you stop speaking.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Two Ways to Turn Voice into Text")
    BulletItem("Google Speech Recognition", "Free, with no key or credit. Used whenever the tablet has Google's speech recognition: from the Google Search app, or Google's speech services that some BOOX tablets come with.")
    BulletItem("AI on OpenRouter", "Only for tablets without Google's speech recognition. Runs on your own OpenRouter account, with the Voice model from AI Settings → AI Models; Settings → Voice-to-Text shows which one.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Troubleshooting")

    TroubleshootingItem(
        problem = "Voice not working?",
        solution = "Check the microphone permission in Android Settings → Apps → BooxDay → Permissions."
    )

    TroubleshootingItem(
        problem = "AI voice not working?",
        solution = "AI on OpenRouter needs an OpenRouter key and AI Features turned on (Settings → AI Settings). Or install the Google Search app for free speech to text."
    )

    TroubleshootingItem(
        problem = "Speech not recognized?",
        solution = "Speak closer to the microphone. Reduce background noise. Try speaking more slowly."
    )

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Privacy & Data")
    BulletPoint("Google Speech: Google's speech service on the tablet. Google may process the audio on its servers.")
    BulletPoint("AI on OpenRouter: the audio clip goes to OpenRouter, on your own account, and to the company that runs the model.")
    BodyText("BooxDay keeps no recordings: the audio is held in memory only until it is turned into text.")
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION 10: E-INK & SLEEP SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EInkOptimizationContent() {
    SectionHeader("E-Ink & Sleep Screen")

    SectionSubtitle("Built for your Boox tablet.")
    BodyText("BooxDay uses no animations and shows meaning in black and white only, which stays sharp in every refresh mode.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Screen Refresh")
    BodyText("If traces of earlier screens remain (ghosting), tap ≡ → Screen Refresh for a full refresh.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Today on Sleep Screen")
    BodyText("Shows today's events and tasks as the tablet's sleep screen. Turn it on in Preferences (Boox tablets only; off at first). Turning it off brings back Boox's own sleep image.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Display Tips")
    BulletPoint("Use the pen to write and your finger to tap and select.")
    BulletPoint("Fewer quick gestures mean fewer screen refreshes.")
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION 11: DATA & TROUBLESHOOTING
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TroubleshootingContent() {
    SectionHeader("Data & Troubleshooting")

    SectionSubtitle("Your data, updates and fixes.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Backup (Settings → Data & Backup)")
    BodyText("Your data is kept in BooxDay's private storage on the tablet. Create Backup saves a copy to Google Drive or Device Storage (Downloads or an SD card); Restore Backup brings one back. Google Drive Backup can also run by itself (Auto Backup).")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Daily Notes (Markdown)")
    BodyText("Turn it on in Preferences: each day's events, tasks and memo text go into a Markdown file in Documents/BooxDay/Daily Notes, or a folder you choose, for other notes apps. Text you add outside BooxDay's part of the file is kept.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Updates")
    BodyText("A while after it starts, BooxDay checks for a new version: Update Now installs it, Not Now asks again the next day. You can also check in Settings → About → Check for Updates. After an update, What's new shows once.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("If BooxDay Closes Unexpectedly")
    BodyText("At the next start, \"BooxDay closed unexpectedly\" offers Copy Report and Report on GitHub. Email addresses in the report are shortened.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    TroubleshootingItem(
        problem = "Pen not writing?",
        solution = "Close any open popup or menu: the pen doesn't write while one is open. If it still doesn't write, close BooxDay and open it again."
    )

    TroubleshootingItem(
        problem = "Sync failed?",
        solution = "Check the Wi-Fi. If it keeps failing, go to Settings → Calendar Accounts, tap Disconnect, then add the account again."
    )

    TroubleshootingItem(
        problem = "Screen showing artifacts?",
        solution = "Tap ≡ → Screen Refresh."
    )

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))
}

// ═══════════════════════════════════════════════════════════════════════════════
// REUSABLE COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

private data class HelpSection(
    val title: String,
    val icon: ImageVector
)

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = EInkTheme.Colors.primary
    )
    Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
}

@Composable
private fun SectionSubtitle(text: String) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = EInkTheme.Colors.darkGray
    )
}

@Composable
private fun SectionSubheader(text: String) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        color = EInkTheme.Colors.primary
    )
    Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
}

@Composable
private fun BodyText(text: String, isMono: Boolean = false) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyLarge,
        fontWeight = FontWeight.Normal,
        color = EInkTheme.Colors.darkGray,
        fontFamily = if (isMono) androidx.compose.ui.text.font.FontFamily.Monospace else null
    )
}

@Composable
private fun BulletPoint(text: String) {
    Row(
        modifier = Modifier.padding(start = EInkTheme.Global.spacingSmall, top = EInkTheme.Global.spacingSmall),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
    ) {
        Text(
            text = "•",
            fontSize = EInkTheme.Typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary
        )
        Text(
            text = text,
            fontSize = EInkTheme.Typography.bodyLarge,
            color = EInkTheme.Colors.darkGray
        )
    }
}

@Composable
private fun BulletItem(label: String, description: String) {
    Row(
        modifier = Modifier.padding(start = EInkTheme.Global.spacingSmall, top = EInkTheme.Global.spacingSmall),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
    ) {
        Text(
            text = "•",
            fontSize = EInkTheme.Typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary
        )
        Column {
            Text(
                text = label,
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = EInkTheme.Colors.primary
            )
            Text(
                text = description,
                fontSize = EInkTheme.Typography.bodyLarge,
                color = EInkTheme.Colors.darkGray
            )
        }
    }
}

@Composable
private fun TroubleshootingItem(problem: String, solution: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = EInkTheme.Global.spacingSmall)
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.borderStrong, RoundedCornerShape(EInkTheme.ModernDialog.OptionCard.cornerRadius))
            .padding(EInkTheme.Global.paddingMedium)
    ) {
        Text(
            text = problem,
            fontSize = EInkTheme.Typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary
        )
        Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))
        Text(
            text = solution,
            fontSize = EInkTheme.Typography.bodyLarge,
            color = EInkTheme.Colors.darkGray
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION 12: KEY LEGENDS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun KeyLegendsContent() {
    SectionHeader("Key Legends")

    SectionSubtitle("What the marks mean.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Calendar")
    LegendItem("Small black mark", "Under the number: the day has events")
    LegendItem("B in a circle", "Top corner: the day has Boox Notes")
    LegendItem("Black ring", "Today")
    LegendItem("White on black", "The day you picked")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Day Header (above the tabs)")
    LegendItem("Note icon", "The day has memos")
    LegendItem("Check icon", "The day has tasks")
    LegendItem("B", "The day has Boox Notes; tap it to open them")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Events")
    LegendItem("B badge", "Linked Boox Notes; a number when there are 2 or more")
    LegendItem("Dot on the B", "A linked note may need a look")
    LegendItem("Camera icon", "Online meeting (Zoom, Teams, Meet and others)")
    LegendItem("Lock", "Read-only event")
    LegendItem("Day 2/3", "One day of an event that spans several days")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Tasks")
    LegendItem("Filled checkbox", "Task done; its row gets a line through it")
    LegendItem("A, B, C or -", "Priority: High, Medium, Low or none")
    LegendItem("Circular arrows", "A recurring task")
    LegendItem("Flame", "In the calendar header: your task streak")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Boox Notes")
    LegendItem("Link icon", "Linked; the event's title follows")
    LegendItem("Star", "A suggested link")
    LegendItem("Unlinked only", "In All notes: shows notes without a link")
}

@Composable
private fun LegendItem(symbol: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = EInkTheme.Global.spacingSmall),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = symbol,
            fontSize = EInkTheme.Typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary,
            modifier = Modifier.width(EInkTheme.Global.padding * 8)
        )
        Text(
            text = description,
            fontSize = EInkTheme.Typography.bodyLarge,
            color = EInkTheme.Colors.darkGray,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun WebsiteLink(label: String, url: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .noRippleClickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
            .padding(vertical = EInkTheme.Global.spacingSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.Colors.primary,
            textDecoration = TextDecoration.Underline
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Open link",
            tint = EInkTheme.Colors.gray,
            modifier = Modifier.size(14.dp)
        )
    }
}
