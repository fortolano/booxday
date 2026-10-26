package com.boox.day.tips

import com.boox.day.BuildConfig

/**
 * Repository of all tips in the app.
 *
 * Tips are organized by category:
 * - Contextual tips: Shown when user is in the relevant context (75% weight)
 * - Discovery tips: Promote features in other areas of the app (25% weight)
 *
 * Total: ~91 tips covering gestures, AI features, task management,
 * Smart Link, Boox Notes, and AI Preferences.
 */
object TipsRepository {

    /** Get all tips from all categories */
    fun getAllTips(): List<Tip> = memosTips + tasksTips + calendarTips + eventsTips + generalTips + semanticTips + gestureTips + discoveryTips

    // ═══════════════════════════════════════════════════════════════
    // MEMOS TAB - Contextual Tips (shown when Memos tab is active)
    // ═══════════════════════════════════════════════════════════════
    private val memosTips = listOf(
        Tip(
            id = "memos_handwriting",
            message = "Write naturally with your stylus. Your handwritten notes are saved automatically.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_eraser",
            message = "Tap the eraser again to choose Standard Eraser or Stroke Eraser.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_undo",
            message = "Made a mistake? Tap the undo button in the toolbar to revert your last action.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_pen_sizes",
            message = "Tap the pen again to choose Pen, Fountain Pen, Marker, Pencil or Brush.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_selection",
            message = "Long-press an empty spot to select an area, or use Lasso select in the toolbar. Then move, copy or delete it.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_text_box",
            message = "Add typed text to your memos with Insert → Text.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_ocr",
            message = "Recognize Handwriting (the AI button in Memos) turns a page's ink into typed text.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_pages",
            message = "Need more space? Use the page arrows to add and navigate memo pages.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "ai_memo_to_tasks",
            message = "Convert Memo to Tasks finds tasks in your notes.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "ai_handwriting_search",
            message = "With an OpenRouter key, Enable Indexing (Settings → Boox Notes) makes your handwritten memos searchable.",
            contexts = setOf(TipContext.MEMOS)
        ),
    )

    // ═══════════════════════════════════════════════════════════════
    // TASKS TAB - Contextual Tips (shown when Tasks tab is active)
    // ═══════════════════════════════════════════════════════════════
    private val tasksTips = listOf(
        Tip(
            id = "tasks_checkbox",
            message = "Tap the checkbox to mark tasks complete. Completed tasks show a strikethrough.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_priority",
            message = "Tap a task's priority button to pick A (High), B (Medium) or C (Low).",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_handwriting",
            message = "Write tasks by hand, or tap › on an empty row to type one.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_pages",
            message = "Need more tasks? Use the page arrows to add more task pages (10 tasks per page).",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_move",
            message = "Tap › on a task, then Move, to move it to another day.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_per_day",
            message = "Each day has its own task list. Navigate dates in the calendar to see other days.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_daily_summary",
            message = "End your day with Daily Summary! Review completed tasks and move unfinished ones to tomorrow.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_weekly_summary",
            message = "Check Weekly Summary on Sunday to see your 7-day trends and plan ahead.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_smart_prioritization",
            message = "Sort by Priority (Task AI) orders your tasks by importance. Check the order before you apply it.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_plan_my_day",
            message = "Try Optimize My Day: it proposes changes to your day, and you choose which to keep.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_breakdown",
            message = "Big task? Use Break Down Task to split it into actionable subtasks.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_ai_preferences",
            message = "Set your Daily Rhythm in AI Preferences, so suggestions fit when you work best.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_special_modes",
            message = "On vacation? Turn on Vacation Mode in AI Preferences for relaxation-focused suggestions.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_crunch_mode",
            message = "Deadline crunch? Turn on Crunch Mode in AI Preferences to cut wellness reminders and focus.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_priority_abc",
            message = "Use A/B/C priorities: High, Medium, Low.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_toggle_all",
            message = "Long-press a checkbox to tick every task on the page.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_details_canvas",
            message = "Tap › on a task for its card: Move, Repeat, Reminder and Break Down.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_clear_erase",
            message = "Long-press a task row to clear it.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "ai_smart_suggestions_context",
            message = "Smart Suggestions reads your calendar and memos for ideas.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "ai_daily_summary_migrate",
            message = "Daily Summary lets you move incomplete tasks to tomorrow.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "ai_weekly_summary_patterns",
            message = "Weekly Summary reveals your most productive day.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "ai_prefs_rhythm",
            message = "Set your Daily Rhythm for time-aware suggestions.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "ai_prefs_lifestyle",
            message = "Tell AI about kids, pets, interests for better ideas.",
            contexts = setOf(TipContext.TASKS)
        ),
    )

    // ═══════════════════════════════════════════════════════════════
    // CALENDAR - Contextual Tips (always visible in top section)
    // ═══════════════════════════════════════════════════════════════
    private val calendarTips = listOf(
        Tip(
            id = "calendar_nav",
            message = "Tap the arrows next to the month name to navigate between months.",
            contexts = setOf(TipContext.CALENDAR)
        ),
        Tip(
            id = "calendar_today",
            message = "Today has a ring around its date. The today button in the calendar header jumps back to it.",
            contexts = setOf(TipContext.CALENDAR)
        ),
        Tip(
            id = "calendar_dots",
            message = "A small mark under a date means events; a B means Boox Notes. The day header shows memos and tasks.",
            contexts = setOf(TipContext.CALENDAR)
        ),
        Tip(
            id = "calendar_week_numbers",
            message = "Turn on week numbers in Settings → Calendar & Sync for easier planning.",
            contexts = setOf(TipContext.CALENDAR)
        ),
        Tip(
            id = "calendar_first_day",
            message = "Prefer Monday starts? Change the first day of the week in Settings → Calendar & Sync.",
            contexts = setOf(TipContext.CALENDAR)
        ),
        Tip(
            id = "calendar_select_date",
            message = "Tap any date to view that day's memos, tasks, and events.",
            contexts = setOf(TipContext.CALENDAR)
        ),
    )

    // ═══════════════════════════════════════════════════════════════
    // EVENTS - Contextual Tips (always visible in top section)
    // ═══════════════════════════════════════════════════════════════
    private val eventsTips = listOf(
        Tip(
            id = "events_add",
            message = "Tap the + button to quickly add a new event to your calendar.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "events_voice",
            message = "Add events by voice! Say something like 'Meeting with John tomorrow at 3pm'.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "events_details",
            message = "Tap an event to see its details, edit it, or open its linked Boox Notes.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "events_sync",
            message = "Add your Google or Outlook account in Settings → Calendar Accounts to see its events here.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "events_local",
            message = "Want an event only on this tablet? Under Save to, pick This device only: it never syncs.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "events_boox_notes",
            message = "Link your Boox Notes to calendar events for quick access to meeting notes.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "events_multi_notes",
            message = "Tap the B badge on events with multiple linked notes to choose which one to open.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "events_unlinked_filter",
            message = "Turn on 'Unlinked only' in Boox Notes → All notes to find notes not linked to any event.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "events_smart_link",
            message = "Use Smart Link to auto-match Boox Notes with nearby events.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "boox_notes_import",
            message = "Tap the B in the day header to see that day's Boox Notes.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "boox_notes_date_title",
            message = "Put a date in a note's title to set its day.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "boox_notes_bulk",
            message = "Link strong… in Smart Link suggestions links all strong matches at once.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "boox_notes_b_badge",
            message = "B badge means linked Boox Notes. Tap to open.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "boox_notes_unlinked",
            message = "Use 'Unlinked only' in All notes to find notes without a link.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "ai_voice_event",
            message = "Tap the mic in the calendar header and say: 'Lunch with Sarah Friday at noon'.",
            contexts = setOf(TipContext.EVENTS)
        ),
    )

    // ═══════════════════════════════════════════════════════════════
    // GENERAL - Tips applicable anywhere
    // ═══════════════════════════════════════════════════════════════
    private val generalTips = listOf(
        Tip(
            id = "general_settings",
            message = "Find more options in the Settings menu (gear icon in the calendar header).",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_ai_preferences",
            message = "Set up AI Preferences (Settings → AI Settings) so suggestions fit your life.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_daily_rhythm",
            message = "Tell the AI your Daily Rhythm - morning person, night owl, deep focus or task switcher?",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_backup",
            message = "Turn on Google Drive backup in Settings → Data & Backup to protect your data.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_date_format",
            message = "Change the date and time format in Settings → Preferences.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_tips_toggle",
            message = "You can turn these tips off in Settings → Preferences.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_key_legends",
            message = "See Help → Key Legends for what the marks and badges mean.",
            contexts = setOf(TipContext.GENERAL)
        ),
    )

    // ═══════════════════════════════════════════════════════════════
    // SMART LINK - Tips about note understanding and Smart Link
    // ═══════════════════════════════════════════════════════════════
    private val semanticTips = listOf(
        Tip(
            id = "semantic_just_write",
            message = "Just write naturally: Smart Link matches notes by the words in their titles and their topics.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "semantic_privacy",
            message = "Pages are read for search only while Settings → Boox Notes → Enable Indexing is on. They go to OpenRouter on your own account, to providers that keep no copy.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "semantic_boox_notes",
            message = "Turn on Enable Indexing in Settings → Boox Notes to make your notebooks searchable.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "semantic_names_dates",
            message = "Include names, dates, and topics in notes for better Smart Link matches.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "semantic_search",
            message = "Indexed handwritten notes are searchable: Search finds the words you wrote.",
            contexts = setOf(TipContext.MEMOS, TipContext.TASKS)
        ),
        Tip(
            id = "semantic_action_items",
            message = "In a calendar event's Meeting notes, Action items lists the to-dos from that day's memos.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "semantic_cross_day",
            message = "Smart Link matches notes to events across days.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "semantic_domains",
            message = "Search results show each note's topic, such as work or health.",
            contexts = setOf(TipContext.MEMOS)
        ),
    )

    // ═══════════════════════════════════════════════════════════════
    // GESTURES - Tips about touch gestures and shortcuts
    // ═══════════════════════════════════════════════════════════════
    private val gestureTips = listOf(
        Tip(
            id = "gesture_hold_select",
            message = "Long press to select strokes, images, or text boxes.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "gesture_two_finger_paste",
            message = "Two-finger tap to paste from clipboard.",
            contexts = setOf(TipContext.MEMOS, TipContext.TASKS)
        ),
        Tip(
            id = "gesture_calendar_longpress",
            message = "Long-press a date for a preview of that day.",
            contexts = setOf(TipContext.CALENDAR)
        ),
    )

    // ═══════════════════════════════════════════════════════════════
    // DISCOVERY - Cross-feature tips (shown when NOT in that context)
    // ═══════════════════════════════════════════════════════════════
    private val discoveryTips = listOf(
        Tip(
            id = "discover_tasks_from_memos",
            message = "Try the Tasks tab! Track your to-dos with checkboxes and A/B/C priorities.",
            contexts = setOf(TipContext.MEMOS),  // Show when on Memos to promote Tasks
            isDiscovery = true
        ),
        Tip(
            id = "discover_memos_from_tasks",
            message = "Need free-form notes? The Memos tab gives you a blank canvas to write on.",
            contexts = setOf(TipContext.TASKS),  // Show when on Tasks to promote Memos
            isDiscovery = true
        ),
        Tip(
            id = "discover_voice_events",
            message = "Voice input works for calendar events! Just tap the mic and speak naturally.",
            contexts = setOf(TipContext.MEMOS, TipContext.TASKS),
            isDiscovery = true
        ),
        Tip(
            id = "discover_boox_notes",
            message = "You can link your existing Boox Notes to calendar events for quick access.",
            contexts = setOf(TipContext.MEMOS, TipContext.TASKS),
            isDiscovery = true
        ),
        Tip(
            id = "discover_ai_ocr",
            message = "Recognize Handwriting (the AI button in Memos) can turn your notes into typed text.",
            contexts = setOf(TipContext.TASKS, TipContext.EVENTS),
            isDiscovery = true
        ),
        Tip(
            id = "discover_export",
            message = "Export Pages in ≡ saves your memos as PDF, PNG or JPG for sharing or printing.",
            contexts = setOf(TipContext.TASKS, TipContext.EVENTS),
            isDiscovery = true
        ),
        Tip(
            id = "discover_ai_preferences_tasks",
            message = "Smart Suggestions work better when the AI knows a little about you. Try Settings → AI Settings → AI Preferences.",
            contexts = setOf(TipContext.TASKS),
            isDiscovery = true
        ),
        Tip(
            id = "discover_smart_suggestions",
            message = "Need ideas? Try Smart Suggestions in the Tasks tab - it reads your memos and calendar!",
            contexts = setOf(TipContext.MEMOS, TipContext.EVENTS),
            isDiscovery = true
        ),
        Tip(
            id = "discover_daily_summary",
            message = "Review your day! Daily Summary in Tasks shows what you accomplished and what needs attention.",
            contexts = setOf(TipContext.MEMOS, TipContext.EVENTS),
            isDiscovery = true
        ),
        Tip(
            id = "discover_weekly_summary",
            message = "Weekly Summary reveals your productivity patterns - find your best days and recurring blockers.",
            contexts = setOf(TipContext.MEMOS, TipContext.EVENTS),
            isDiscovery = true
        ),
        Tip(
            id = "discover_plan_my_day",
            message = "Overwhelmed? Optimize My Day looks at your calendar and tasks and proposes a plan for the day.",
            contexts = setOf(TipContext.MEMOS, TipContext.EVENTS),
            isDiscovery = true
        ),
        Tip(
            id = "discover_daily_rhythm",
            message = "Tell AI Preferences if you're a morning person or night owl, for better-timed suggestions.",
            contexts = setOf(TipContext.MEMOS, TipContext.EVENTS, TipContext.CALENDAR),
            isDiscovery = true
        ),
        Tip(
            id = "discover_priorities",
            message = "Pick your top 3 life priorities in AI Preferences - the AI emphasizes suggestions in those areas.",
            contexts = setOf(TipContext.MEMOS, TipContext.EVENTS, TipContext.CALENDAR),
            isDiscovery = true
        ),
        Tip(
            id = "discover_focus_style",
            message = "Are you a deep focus worker or task switcher? Tell AI Preferences for better task sizing.",
            contexts = setOf(TipContext.MEMOS, TipContext.TASKS),
            isDiscovery = true
        ),
        Tip(
            id = "discover_special_modes",
            message = "Turn on Vacation Mode, Crunch Mode or Holiday Mode in AI Preferences when your routine changes.",
            contexts = setOf(TipContext.TASKS, TipContext.EVENTS),
            isDiscovery = true
        ),
        Tip(
            id = "discover_lifestyle_traits",
            message = "Homebody? Foodie? Social butterfly? AI Preferences uses your lifestyle for relevant suggestions.",
            contexts = setOf(TipContext.MEMOS, TipContext.CALENDAR),
            isDiscovery = true
        ),
        Tip(
            id = "discover_ai_reads_memos",
            message = "Smart Suggestions can use your recent handwritten memos for task ideas.",
            contexts = setOf(TipContext.TASKS, TipContext.EVENTS),
            isDiscovery = true
        ),
        Tip(
            id = "discover_personalization",
            message = "Generic AI suggestions? Set up AI Preferences for tasks that actually fit your life and schedule.",
            contexts = setOf(TipContext.MEMOS, TipContext.EVENTS),
            isDiscovery = true
        ),
        Tip(
            id = "ai_prefs_generic",
            message = "Generic suggestions? Set up AI Preferences.",
            contexts = setOf(TipContext.TASKS),
            isDiscovery = true
        ),
    )
}
