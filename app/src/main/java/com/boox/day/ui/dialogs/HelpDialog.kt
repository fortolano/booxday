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
import com.boox.day.ui.theme.EInkTheme

/**
 * Help Dialog for Boox Day
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
            HelpSection("Smart AI Tools", Icons.Default.AutoAwesome),
            HelpSection("SemanticGenius", Icons.Default.Link),
            HelpSection("Voice Input", Icons.Default.Mic),
            HelpSection("E-Ink Optimization", Icons.Default.PhoneAndroid),
            HelpSection("Troubleshooting", Icons.Default.Warning),
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
    LazyColumn(
        modifier = modifier
            .background(EInkTheme.Colors.background)
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
}

@Composable
private fun HelpContent(
    sectionIndex: Int,
    modifier: Modifier = Modifier
) {
    // E-Ink: use ScrollableWithIndicator for scroll feedback
    ScrollableWithIndicator(
        modifier = modifier
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
                6 -> SmartAIToolsContent()
                7 -> SemanticGeniusContent()
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
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun QuickStartContent() {
    SectionHeader("Quick Start")

    SectionSubtitle("Welcome to BooxDay.")
    BodyText("The calendar your Boox tablet deserves — combining a digital calendar with the analog feel of a paper planner.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("The Dashboard")
    BodyText("Your home base is the monthly calendar. Tap any date to open its Day View.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Two Modes")
    BodyText("Every day has two dedicated tabs:")
    BulletItem("Memos", "A blank canvas for freeform writing and sketching.")
    BulletItem("Tasks", "A structured list for to-dos and priorities.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Navigation")
    BulletPoint("Tap calendar dates to switch between days.")
    BulletPoint("Long-press a date to preview it in a Quick View popup.")
    BulletPoint("Tap the Today icon (calendar with number) to return to the current date.")
    BulletPoint("Use the arrow buttons in the toolbar to navigate between pages.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION 2: TOUCH GESTURES
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TouchGesturesContent() {
    SectionHeader("Touch Gestures")

    SectionSubtitle("Work smarter with long-press shortcuts.")
    BodyText("Long-press gestures give you quick access to powerful features throughout the app. Your stylus is for writing; your finger is for navigation and shortcuts.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // ─────────────────────────────────────────────────────────────────────────
    // CALENDAR GESTURES
    // ─────────────────────────────────────────────────────────────────────────
    SectionSubheader("Calendar Grid")

    GestureItem(
        gesture = "Tap a date",
        action = "Open that day",
        details = "Opens the Day View for the selected date, showing your Memos or Tasks."
    )

    GestureItem(
        gesture = "Long-press a date",
        action = "Quick View popup",
        details = "Shows a preview of the day's events and tasks without leaving the calendar. Great for quickly checking what's on a specific day."
    )

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // ─────────────────────────────────────────────────────────────────────────
    // TASKS TAB GESTURES
    // ─────────────────────────────────────────────────────────────────────────
    SectionSubheader("Tasks Tab")
    BodyText("The Tasks tab has several long-press shortcuts to speed up your workflow:")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

    GestureItem(
        gesture = "Long-press the checkbox",
        action = "Toggle all tasks on page",
        details = "Quickly mark all tasks as complete or incomplete. If any task is unchecked, all become checked. If all are checked, all become unchecked."
    )

    GestureItem(
        gesture = "Long-press the arrow (chevron)",
        action = "Edit task directly",
        details = "Opens the task editor immediately. This skips the move/migrate popup that appears on a normal tap."
    )

    GestureItem(
        gesture = "Long-press the task row",
        action = "Clear the task",
        details = "Removes the task from the slot entirely. Use this to delete a task you no longer need."
    )

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // ─────────────────────────────────────────────────────────────────────────
    // MEMOS TAB GESTURES
    // ─────────────────────────────────────────────────────────────────────────
    SectionSubheader("Memos Tab (Canvas)")
    BodyText("Use long-press on the canvas to select and manipulate content:")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

    GestureItem(
        gesture = "Long-press an image",
        action = "Select the image",
        details = "The image becomes selected with resize handles. Drag to move it, use the handles to resize, or tap elsewhere to deselect."
    )

    GestureItem(
        gesture = "Long-press an attachment",
        action = "Select the attachment",
        details = "Works the same as images. Select PDF or EPUB attachments to reposition them on your page."
    )

    GestureItem(
        gesture = "Long-press a text box",
        action = "Select the text box",
        details = "Select text boxes to move or resize them. Double-tap to edit the text inside."
    )

    GestureItem(
        gesture = "Long-press empty area",
        action = "Lasso selection mode",
        details = "A bullseye target appears. Drag to draw a rectangle around handwriting to select it. Selected content can be moved, copied, or deleted."
    )

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // ─────────────────────────────────────────────────────────────────────────
    // QUICK PASTE
    // ─────────────────────────────────────────────────────────────────────────
    SectionSubheader("Quick Paste")

    GestureItem(
        gesture = "Two-finger tap",
        action = "Paste from clipboard",
        details = "Tap with two fingers close together to paste text or images you've copied from any app. A text box or image appears on the canvas."
    )

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // ─────────────────────────────────────────────────────────────────────────
    // TIPS
    // ─────────────────────────────────────────────────────────────────────────
    SectionSubheader("Tips for Best Results")
    BulletItem("Hold for about 1 second", "Long-press gestures activate after holding your finger still for about a second. You'll feel or see feedback when it activates.")
    BulletItem("Use your fingertip", "Gestures work with finger touch only. Your stylus is reserved for writing and drawing.")
    BulletItem("Tap to cancel", "If you accidentally enter selection mode, just tap anywhere else to exit.")
    BulletItem("Two-finger tap technique", "Place two fingers close together and tap quickly. They don't need to touch at the exact same moment.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // ─────────────────────────────────────────────────────────────────────────
    // PRACTICAL EXAMPLES
    // ─────────────────────────────────────────────────────────────────────────
    SectionSubheader("Practical Examples")

    BodyText("Complete all tasks at once:")
    BulletPoint("1. Go to the Tasks tab")
    BulletPoint("2. Long-press any checkbox")
    BulletPoint("3. All tasks on the page are marked complete")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

    BodyText("Preview a busy day:")
    BulletPoint("1. Look at the calendar grid")
    BulletPoint("2. Long-press a date with events")
    BulletPoint("3. The Quick View popup shows what's scheduled")
    BulletPoint("4. Tap outside to close, or tap \"Open\" to go there")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

    BodyText("Move an image on your memo:")
    BulletPoint("1. Long-press the image until \"Image selected!\" appears")
    BulletPoint("2. Drag it to the new position")
    BulletPoint("3. Tap elsewhere to confirm the move")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

    BodyText("Paste copied text:")
    BulletPoint("1. Copy text from any app (browser, email, etc.)")
    BulletPoint("2. Return to BooxDay and open a Memo")
    BulletPoint("3. Two-finger tap on the canvas")
    BulletPoint("4. A text box appears with your copied content")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // ─────────────────────────────────────────────────────────────────────────
    // TROUBLESHOOTING
    // ─────────────────────────────────────────────────────────────────────────
    SectionSubheader("Troubleshooting")

    TroubleshootingItem(
        problem = "Long-press not working?",
        solution = "Make sure you're holding your finger still for about a second. If you move too much, it won't register as a long-press."
    )

    TroubleshootingItem(
        problem = "Accidentally entering selection mode?",
        solution = "This happens if you hold your finger too long on the canvas. Just tap anywhere else to cancel and exit selection mode."
    )

    TroubleshootingItem(
        problem = "Gestures not responding at all?",
        solution = "Check if a text field is active (tap outside to close it) or if the events panel is expanded (tap the canvas to close it). Also make sure you're using your finger, not the stylus."
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

    SectionSubtitle("Your Digital Canvas.")
    BodyText("The Memos tab supports unlimited pages per day.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Toolbar Tools")

    BulletItem("Pen", "Toggle between Ballpoint, Brush, Marker, and Pencil. Tap again to adjust thickness (1-50px) and color.")
    BulletItem("Eraser", "\"Stroke Eraser\" removes whole lines; \"Area Eraser\" wipes specific pixels.")
    BulletItem("Lasso", "Circle content to Move, Resize, Copy, or Delete it.")
    BulletItem("Insert", "Add Images, Tables, Text Boxes, or file Attachments (PDF/EPUB) directly to the page.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Page Navigation")
    BulletPoint("Use the arrow buttons in the toolbar to navigate between pages.")
    BulletPoint("The page indicator shows which page you're on (e.g., 1/3).")
    BulletPoint("Two-finger tap to paste copied content onto the canvas.")
    BulletPoint("Long-press images, attachments, or text boxes to select them.")
    BodyText("See the Touch Gestures section for all available shortcuts.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Scribble-to-Erase")
    BodyText("Rapidly scribble back and forth over handwriting with your stylus to erase it. This mimics the motion of physically crossing out text.")
    BodyText("Turn it on with \"Scribble to Erase\" in the eraser menu.")
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION 4: TASK MANAGEMENT
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TaskManagementContent() {
    SectionHeader("Task Management")

    SectionSubtitle("Structured Productivity.")
    BodyText("Manage up to 10 tasks per page, with unlimited pages per day.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Creating Tasks")
    BodyText("Tap a row to expand it. You can write the task name with your stylus or type it using the keyboard.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Priorities")
    BodyText("Tap the priority button to cycle through levels:")
    BulletItem("A (Red)", "Critical priority")
    BulletItem("B (Orange)", "High priority")
    BulletItem("C (Yellow)", "Normal priority")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Task Details")
    BodyText("Every task row expands into a mini-canvas. Draw diagrams or write sub-notes directly inside the task.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Task Migration")
    BodyText("Tap the Arrow icon to move an unfinished task to tomorrow or a specific date.")
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION 5: CALENDAR & SYNC
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CalendarSyncContent() {
    SectionHeader("Calendar & Sync")

    SectionSubtitle("Offline First, Cloud Connected.")
    BodyText("BooxDay stores all data locally for privacy and speed.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Cloud Sync")
    BulletItem("Events", "Google and Outlook Calendar events sync 6 months back and 6 months forward.")
    BulletItem("Privacy", "Handwritten notes and tasks stay on your device; they are not sent to Google.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Calendar Modes")
    BulletItem("Private Mode", "All calendars stay on your device. No account needed, full privacy.")
    BulletItem("Cloud Sync", "See Google or Outlook calendars alongside your on-device calendars.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Visual Badges")
    BodyText("Calendar dates show badges indicating content:")
    BulletItem("Sticky Note icon", "Day has Memos.")
    BulletItem("Checkmark icon", "Day has Tasks.")
    BulletItem("Color bar", "Day has synced calendar events.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Sync Status")
    BodyText("The Cloud icon shows your sync status. A spinning icon means sync is active.")
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION 6: SEARCH
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SearchContent() {
    SectionHeader("Search")

    SectionSubtitle("Find anything across your entire planner.")
    BodyText("Search lets you find memos, tasks, events, and Boox Notes — all from one place. Tap the magnifying glass icon in the calendar toolbar to open Search.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("What You Can Search")
    BulletItem("Memos", "Handwritten memo pages processed by OCR.")
    BulletItem("Tasks", "All task text — typed, converted, or recognized.")
    BulletItem("Events", "Google Calendar, Outlook Calendar, and local events.")
    BulletItem("Boox Notes", "Pages from the Boox Notes app processed by OCR.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Filters")
    BodyText("Use the filter chips at the top of the search dialog to narrow results by content type:")
    BulletItem("All", "Show results from every source.")
    BulletItem("Memos", "Only memo pages.")
    BulletItem("Tasks", "Only tasks.")
    BulletItem("Events", "Calendar and local events.")
    BulletItem("Notes", "Only Boox Notes pages.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Smart Matching")
    BodyText("Search uses multi-tier relevance scoring to surface the best results:")
    BulletItem("Exact matches", "Word-boundary matches ranked highest.")
    BulletItem("Full-text matches", "Broader text matches for memos and notes.")
    BulletItem("Similar matches", "Partial substring matches labeled \"Similar\" for broader discovery.")
    BodyText("Results also include synonym expansion — for example, searching \"meeting\" will also find \"mtg\", \"standup\", and \"huddle\".")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Result Details")
    BodyText("Each search result shows helpful context:")
    BulletPoint("Source type icon (memo, task, event, or note).")
    BulletPoint("Date of the content.")
    BulletPoint("A text snippet showing where your query matched.")
    BulletPoint("Domain badge and word count for memos and notes.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Tips")
    BulletPoint("Type at least 2 characters to start searching.")
    BulletPoint("Tap any result to navigate directly to that day.")
    BulletPoint("Tap a Boox Notes result to open it in the Boox Notes app.")
    BulletPoint("Up to 50 results are shown, sorted by relevance then date.")
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION 7: SMART AI TOOLS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SmartAIToolsContent() {
    SectionHeader("Smart AI Tools")

    SectionSubtitle("Your Personal AI Chief of Staff")
    BodyText("Boox Day includes a suite of AI features designed to understand how you work and help you be more productive. The AI learns your patterns, respects your preferences, and gives suggestions that actually fit your life.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // ─────────────────────────────────────────────────────────────────────────
    // AI PREFERENCES - THE KEY TO PERSONALIZATION
    // ─────────────────────────────────────────────────────────────────────────
    SectionSubheader("★ AI Preferences — Start Here!")
    BodyText("The magic of personalization starts with telling the AI who you are. Without preferences, you get generic suggestions. With them, you get suggestions tailored to YOUR life.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

    BodyText("Two ways to set up:")
    BulletItem("Voice Setup (30 seconds)", "Just talk naturally: \"I'm a freelance designer, morning person, I have two kids and love hiking.\" The AI extracts your preferences automatically!")
    BulletItem("Manual Setup (2 minutes)", "Answer questions across 5 tabs — pick your top priorities, describe your daily rhythm, select your interests.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

    BodyText("What you'll configure:")
    BulletItem("Life Priorities", "Pick your top 3: Work, Health, Family, Social, Hobbies, Finance, Education. The AI emphasizes these in suggestions.")
    BulletItem("Daily Rhythm", "When do you wake up? Morning person or night owl? Do you prefer deep focus or task switching? Remote or commuting?")
    BulletItem("Lifestyle & Interests", "Are you a homebody or social butterfly? Have kids or pets? Love fitness, reading, cooking?")
    BulletItem("AI Style", "How many suggestions? Purely practical or include wellness reminders? Turn on Vacation Mode or Crunch Mode temporarily.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

    BodyText("💡 Pro tip: Use Voice Setup! It's faster and feels more natural than filling forms.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // ─────────────────────────────────────────────────────────────────────────
    // FLAGSHIP FEATURES
    // ─────────────────────────────────────────────────────────────────────────
    SectionSubheader("Smart Suggestions")
    BodyText("The flagship AI feature. Tap the Sparkle icon to get personalized task ideas based on:")
    BulletPoint("Your calendar — what's coming up that needs prep?")
    BulletPoint("Your recent memos — handwritten notes the AI can read")
    BulletPoint("Your task history — patterns, completion rates, neglected areas")
    BulletPoint("Your preferences — priorities, interests, daily rhythm")
    BulletPoint("Time of day and day of week")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

    BodyText("Examples of what Smart Suggestions might say:")
    BulletPoint("\"Review slides for tomorrow's presentation\" (calendar prep)")
    BulletPoint("\"Follow up with Sarah about project timeline\" (from your memo)")
    BulletPoint("\"Time for a 15-minute walk\" (health priority + afternoon slump)")
    BulletPoint("\"Start meal planning for the week\" (Sunday evening + foodie)")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Optimize My Day")
    BodyText("Your AI Chief of Staff creates an optimized daily schedule by analyzing:")
    BulletPoint("Calendar events and meeting gaps")
    BulletPoint("Your energy pattern (big tasks when you're sharpest)")
    BulletPoint("Task priorities and deadlines")
    BulletPoint("Your preferred work style and routine")
    BodyText("Great for overwhelming days — let AI figure out the optimal order.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Smart Prioritization")
    BodyText("Have a long task list? Let AI reorder by true importance. It considers deadlines, dependencies, and your priorities. Review the suggested order before applying.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // ─────────────────────────────────────────────────────────────────────────
    // DAILY & WEEKLY INSIGHTS
    // ─────────────────────────────────────────────────────────────────────────
    SectionSubheader("Daily Summary")
    BodyText("End-of-day review and migration tool:")
    BulletPoint("See completion rate and what you accomplished")
    BulletPoint("Handwritten tasks are OCR'd and displayed")
    BulletPoint("AI insights about your productivity that day")
    BulletPoint("One-tap migration of unfinished tasks to tomorrow")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

    BodyText("💡 Best practice: Run Daily Summary before bed. Migrate what didn't get done, start fresh tomorrow.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Weekly Summary")
    BodyText("7-day retrospective for Sunday/Monday planning:")
    BulletPoint("Overall completion rate and trend")
    BulletPoint("Best and toughest days identified")
    BulletPoint("Recurring incomplete tasks flagged")
    BulletPoint("Work-life balance analysis by category")
    BulletPoint("AI insights about patterns and actionable suggestions")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

    BodyText("💡 Best practice: Check Weekly Summary on Sunday evening or Monday morning to plan your week.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // ─────────────────────────────────────────────────────────────────────────
    // INPUT & CAPTURE
    // ─────────────────────────────────────────────────────────────────────────
    SectionSubheader("Voice to Task")
    BodyText("Speak naturally to create tasks:")
    BulletPoint("\"Call the dentist by Friday, it's important\"")
    BulletPoint("\"Buy groceries, get milk and eggs\"")
    BulletPoint("\"Research vacation destinations next week\"")
    BodyText("The AI extracts task text, due dates, and priority automatically.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Automatic Handwriting Recognition")
    BodyText("Your handwritten tasks are automatically converted to searchable text in the background using Google ML Kit Digital Ink. No manual action needed - just write and the app intelligently indexes your content for search and Smart Link features.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Break Down Task")
    BodyText("Select a big task like \"Plan vacation\" and let AI generate subtasks:")
    BulletPoint("Research destinations")
    BulletPoint("Compare flight prices")
    BulletPoint("Book accommodations")
    BulletPoint("Create packing list")
    BodyText("Add subtasks directly to your list with one tap.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // ─────────────────────────────────────────────────────────────────────────
    // PRO TIPS - THE GOOD STUFF
    // ─────────────────────────────────────────────────────────────────────────
    SectionSubheader("Pro Tips for Power Users")
    BulletItem("Set up AI Preferences first", "Generic suggestions are okay. Personalized suggestions are game-changing. Take 30 seconds for Voice Setup.")
    BulletItem("Tell it about your Daily Rhythm", "Morning person? The AI puts challenging tasks early. Night owl? They come later. Task switcher? Get more, smaller tasks.")
    BulletItem("Use Special Modes", "Going on vacation? Enable Vacation Mode for relaxation-focused suggestions. Deadline crunch? Enable Crunch Mode to cut the fluff.")
    BulletItem("End each day with Daily Summary", "Migrating tasks takes 10 seconds and keeps your system clean.")
    BulletItem("Check Weekly Summary every Sunday", "10 minutes of reflection saves hours of wheel-spinning.")
    BulletItem("Write memos freely", "The AI reads your recent handwritten notes and uses them for suggestions. Your scribbles become action items!")
    BulletItem("Use Voice Setup to update preferences", "Life changed? Just talk through your new situation instead of editing forms.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("How the AI Works")
    BodyText("All AI features use free models via OpenRouter. Your data stays on your device — only the specific content needed for each request is sent to the AI, and nothing is stored externally. The AI doesn't \"learn\" from your usage in a cloud sense; personalization comes from your explicit preferences.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION 8: SEMANTICGENIUS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SemanticGeniusContent() {
    SectionHeader("SemanticGenius")

    SectionSubtitle("Your notes, intelligently connected.")
    BodyText("SemanticGenius is the intelligence engine that automatically understands what you write and links your notes to related calendar events and tasks.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // ─────────────────────────────────────────────────────────────────────────
    // WHAT IT DOES
    // ─────────────────────────────────────────────────────────────────────────
    SectionSubheader("What It Does")
    BulletItem("Understands your notes", "Extracts keywords, people, dates, organizations, and topics from your handwriting.")
    BulletItem("Smart Link", "Automatically suggests which Boox Notes relate to each calendar event.")
    BulletItem("Action items", "Finds tasks hidden in your handwriting (\"Call John tomorrow\").")
    BulletItem("Semantic search", "Find notes by meaning, not just exact words.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // ─────────────────────────────────────────────────────────────────────────
    // HOW IT WORKS
    // ─────────────────────────────────────────────────────────────────────────
    SectionSubheader("How It Works")
    BulletItem("1. Just write", "SemanticGenius processes notes automatically in the background.")
    BulletItem("2. Check Smart Link", "Tap any calendar event to see related Boox Notes suggestions.")
    BulletItem("3. Search", "Use the search icon to find content across all indexed notes.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // ─────────────────────────────────────────────────────────────────────────
    // TIPS FOR BEST RESULTS
    // ─────────────────────────────────────────────────────────────────────────
    SectionSubheader("Tips for Best Results")
    BulletItem("Write naturally", "SemanticGenius understands context, not just keywords.")
    BulletItem("Include names, dates, and topics", "Better matches for Smart Link.")
    BulletItem("Enable Boox Notes indexing", "Go to Settings → Boox Notes → Enable Indexing to connect your existing notebooks.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVACY
    // ─────────────────────────────────────────────────────────────────────────
    SectionSubheader("Privacy")
    BodyText("All SemanticGenius processing happens ON YOUR DEVICE. Your handwritten notes never leave your tablet. Only the initial OCR uses Google Cloud Vision; all semantic analysis is performed locally.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION 9: VOICE INPUT
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun VoiceInputContent() {
    SectionHeader("Voice Input")

    SectionSubtitle("Speak instead of type.")
    BodyText("Boox Day lets you speak naturally to create memos, events, and tasks.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // ─────────────────────────────────────────────────────────────────────────
    // THREE VOICE FEATURES
    // ─────────────────────────────────────────────────────────────────────────
    SectionSubheader("Three Voice Features")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

    BulletItem("Voice to Text (Memos)", "Dictate notes naturally. AI adds formatting (headers, lists, emphasis) automatically.")
    BulletPoint("\"Meeting notes. First, we discussed the budget. Second, timeline concerns.\"")
    BulletPoint("\"Shopping list: milk, eggs, bread, and don't forget the coffee.\"")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    BulletItem("Voice to Event (Calendar)", "Create calendar events by describing them naturally.")
    BulletPoint("\"Meeting with Sarah tomorrow at 2pm\"")
    BulletPoint("\"Dentist appointment next Tuesday at 3:30\"")
    BulletPoint("\"Team standup at 9am on Zoom\"")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    BulletItem("Voice to Task", "Add tasks with optional due dates and priorities.")
    BulletPoint("\"Call the dentist by Friday\"")
    BulletPoint("\"Buy groceries\" (no date = today)")
    BulletPoint("\"Finish the report by December 20th, it's important\"")
    BodyText("AI extracts: task text, due date, and priority level.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // ─────────────────────────────────────────────────────────────────────────
    // TWO TRANSCRIPTION PROVIDERS
    // ─────────────────────────────────────────────────────────────────────────
    SectionSubheader("Two Transcription Providers")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingSmall))

    BulletItem("Google Speech Recognition (Recommended)", "Free and unlimited. Works locally on your device. Requires Google Search app installed.")
    BodyText("Best for: Daily use without limits.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    BulletItem("Gemini 2.0 AI", "Enhanced accuracy for accents and complex speech. Context-aware corrections.")
    BodyText("Limited: 3/day (Free tier) or 20/day (Pro tier).")
    BodyText("Best for: When you need highest accuracy.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // ─────────────────────────────────────────────────────────────────────────
    // TIPS FOR BEST RESULTS
    // ─────────────────────────────────────────────────────────────────────────
    SectionSubheader("Tips for Best Results")
    BulletItem("Speak naturally", "Don't over-enunciate. The system understands normal speech.")
    BulletItem("Pause briefly", "System auto-detects when you're done (2-3 seconds of silence).")
    BulletItem("Include context", "\"Meeting with John\" is better than just \"meeting\".")
    BulletItem("Dates work flexibly", "\"tomorrow\", \"next Tuesday\", \"December 20th\" all work.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // ─────────────────────────────────────────────────────────────────────────
    // TROUBLESHOOTING
    // ─────────────────────────────────────────────────────────────────────────
    SectionSubheader("Troubleshooting")

    TroubleshootingItem(
        problem = "Voice not working?",
        solution = "Check microphone permissions in Android Settings > Apps > Boox Day > Permissions. Ensure Google Search app is installed for unlimited free voice."
    )

    TroubleshootingItem(
        problem = "Daily limit reached?",
        solution = "You've used all Gemini AI transcriptions for today. Install Google Search app for unlimited free voice-to-text, or wait until midnight when limits reset."
    )

    TroubleshootingItem(
        problem = "Speech not recognized?",
        solution = "Speak closer to the microphone. Reduce background noise. Try speaking more slowly. The Gemini AI option often has better accuracy."
    )

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingLarge))

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVACY
    // ─────────────────────────────────────────────────────────────────────────
    SectionSubheader("Privacy & Data")
    BodyText("Voice data is processed either:")
    BulletPoint("Locally (Google Speech) — never leaves your device")
    BulletPoint("Via secure API (Gemini) — only the audio clip is sent, deleted after processing")
    BodyText("No voice recordings are stored permanently.")
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION 10: E-INK OPTIMIZATION
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EInkOptimizationContent() {
    SectionHeader("E-Ink Optimization")

    SectionSubtitle("Built for your Boox tablet.")
    BodyText("BooxDay is specifically designed for e-ink displays with zero animations and a stylus-optimized UI.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Screen Ghosting")
    BodyText("If the screen looks \"messy\" with artifacts, tap the Refresh button (circular arrows icon) in the toolbar. This triggers a full screen refresh to clear ghosting.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Animation Mode")
    BodyText("The app automatically switches to \"A2\" (fast refresh) mode during scrolling to prevent lag, then snaps back to \"Normal\" (high quality) mode for writing.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Battery Life")
    BodyText("Dark interface elements and minimal animations are designed to extend battery life on e-ink devices.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Display Tips")
    BulletPoint("High-contrast black/white UI works best on e-ink.")
    BulletPoint("Avoid rapid gestures that cause excessive screen refreshes.")
    BulletPoint("Use the stylus for precise input; finger touch works for navigation.")
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION 11: TROUBLESHOOTING
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TroubleshootingContent() {
    SectionHeader("Troubleshooting")

    SectionSubtitle("Common Issues & Solutions")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    TroubleshootingItem(
        problem = "Stylus not writing?",
        solution = "Check if the \"Hand\" icon is active (Touch Mode). Tap it to switch back to Pen Mode."
    )

    TroubleshootingItem(
        problem = "Sync failed?",
        solution = "Ensure you are online. If issues persist, go to Settings → Calendar Accounts, tap Disconnect, then sign in again."
    )

    TroubleshootingItem(
        problem = "Strokes disappearing?",
        solution = "Ensure you wait a moment before switching days. Rapid switching may interrupt saving."
    )

    TroubleshootingItem(
        problem = "Screen showing artifacts?",
        solution = "Tap the Refresh button in the toolbar to perform a full e-ink screen refresh."
    )

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    SectionSubheader("Data Backup")
    BodyText("BooxDay stores data in external storage for easy backup. Your database is located at:")
    BodyText("/Documents/booxdaydb/", isMono = true)

    Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

    BodyText("Back up regularly in Settings → Data & Backup.")

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

    SectionSubtitle("Visual indicators used throughout the app.")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    // Calendar Grid Legends
    SectionSubheader("Calendar Grid")
    LegendItem("● (black dot)", "Day has events scheduled")
    LegendItem("Underline", "Day has events (alternate indicator)")
    LegendItem("Bold date", "Today's date")
    LegendItem("Circle outline", "Selected date")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    // Events Grid Legends
    SectionSubheader("Events Grid")
    LegendItem("B badge", "Event has linked Boox Note(s)")
    LegendItem("B badge + memo icon", "Boox Note linked AND has local memo")
    LegendItem("Memo icon only", "Event has local memo (no Boox Note)")
    LegendItem("⚠ (attention)", "Linked note may need review")
    LegendItem("🔗 (link)", "Event is synced with Google or Outlook Calendar")
    LegendItem("📍 (pin)", "Event has a location")
    LegendItem("🎥 (video)", "Virtual meeting (Zoom, Meet, Teams)")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    // Task Legends
    SectionSubheader("Tasks Tab")
    LegendItem("○ (empty circle)", "Task not started")
    LegendItem("● (filled circle)", "Task completed")
    LegendItem("🔥 (fire)", "High priority task")
    LegendItem("Gray row", "Completed task (grayed out)")

    Spacer(modifier = Modifier.height(EInkTheme.Global.paddingMedium))

    // Boox Notes Dialog Legends
    SectionSubheader("View Boox Notes Dialog")
    LegendItem("⚠ (yellow)", "Note needs attention or review")
    LegendItem("✓ (checkmark)", "Note successfully linked to event")
    LegendItem("Unlinked filter", "Show only notes not linked to events")
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
