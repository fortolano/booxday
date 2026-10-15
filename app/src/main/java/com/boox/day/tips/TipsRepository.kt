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
 * SemanticGenius, Boox Notes, and AI Preferences.
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
            message = "Use the eraser tool to remove strokes. Tap it again to switch between stroke and area eraser.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_undo",
            message = "Made a mistake? Tap the undo button in the toolbar to revert your last action.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_pen_sizes",
            message = "Tap any pen tool to access different sizes and colors.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_selection",
            message = "Long press on the canvas to select strokes. Move, resize, or copy your handwriting.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_text_box",
            message = "Add typed text to your memos using the text tool in the toolbar.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_ocr",
            message = "Convert handwriting to typed text using the AI recognition feature.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_pages",
            message = "Need more space? Use the page arrows to add and navigate memo pages.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "ai_memo_to_tasks",
            message = "Memo to Tasks extracts action items from your notes.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "ai_handwriting_search",
            message = "Handwriting Recognition makes your notes searchable.",
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
            message = "Tap the star icon to mark important tasks as high priority.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_handwriting",
            message = "Write tasks by hand or tap the row to type. Both methods work great!",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_voice",
            message = "Use Voice to Task to add multiple tasks by speaking naturally.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_pages",
            message = "Need more tasks? Use the page arrows to add more task pages (10 tasks per page).",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_move",
            message = "Long press a task row to move it to a different position or day.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_per_day",
            message = "Each day has its own task list. Navigate dates in the calendar to see other days.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_daily_summary",
            message = "End your day with Daily Summary! Review completed tasks and migrate unfinished ones.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_weekly_summary",
            message = "Check Weekly Summary on Sunday to see your 7-day trends and plan ahead.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_smart_prioritization",
            message = "Let AI sort your tasks! Smart Prioritization reorders by deadlines and importance.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_plan_my_day",
            message = "Try Optimize My Day - your AI Chief of Staff creates an optimized daily schedule.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_breakdown",
            message = "Big task? Use Break Down Task to split it into actionable subtasks.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_ai_preferences",
            message = "Set up your Daily Rhythm in AI Preferences - the AI learns how you work best!",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_voice_setup",
            message = "AI Preferences has Voice Setup! Just describe yourself in 30 seconds - no forms needed.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_special_modes",
            message = "On vacation? Enable Vacation Mode in AI Preferences for relaxation-focused suggestions.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_crunch_mode",
            message = "Deadline crunch? Enable Crunch Mode in AI Preferences to cut wellness reminders and focus.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_priority_abc",
            message = "Use A/B/C priorities: urgent, important, nice-to-have.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_toggle_all",
            message = "Tap the column header to mark all tasks complete.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_details_canvas",
            message = "Task Details has a mini-canvas for extra notes.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_clear_erase",
            message = "Erase a task's content to clear the row.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "ai_smart_suggestions_context",
            message = "Smart Suggestions reads your calendar and memos for ideas.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "ai_daily_summary_migrate",
            message = "Daily Summary lets you migrate incomplete tasks to tomorrow.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "ai_weekly_summary_patterns",
            message = "Weekly Summary reveals your most productive day.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "ai_voice_multiple",
            message = "Voice to Task handles multiple tasks in one sentence.",
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
            message = "The current date is highlighted with a border. Tap it to jump back to today.",
            contexts = setOf(TipContext.CALENDAR)
        ),
        Tip(
            id = "calendar_dots",
            message = "Dots under dates indicate you have notes, tasks, or events on that day.",
            contexts = setOf(TipContext.CALENDAR)
        ),
        Tip(
            id = "calendar_week_numbers",
            message = "Enable week numbers in Settings > Basic for easier planning.",
            contexts = setOf(TipContext.CALENDAR)
        ),
        Tip(
            id = "calendar_first_day",
            message = "Prefer Monday starts? Change the first day of week in Settings > Basic.",
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
            message = "Tap an event to see details, edit, or attach handwritten notes.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "events_sync",
            message = "Connect Google Calendar in Settings to sync events across devices.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "events_local",
            message = "Events are local by default. Toggle sync per-event if you use Google Calendar.",
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
            message = "Check 'Unlinked' in View Boox Notes to find notes not linked to any event.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "events_smart_link",
            message = "Use Smart Link to auto-match Boox Notes with nearby events.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "boox_notes_import",
            message = "View your Boox Notes in the events panel.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "boox_notes_date_title",
            message = "Name notes with dates for automatic day linking.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "boox_notes_bulk",
            message = "Smart Link can auto-match dozens of notes at once.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "boox_notes_b_badge",
            message = "B badge means linked Boox Notes. Tap to open.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "boox_notes_unlinked",
            message = "Use 'Unlinked' filter to find orphaned notes.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "ai_voice_event",
            message = "Voice to Event: 'Lunch with Sarah Friday at noon'.",
            contexts = setOf(TipContext.EVENTS)
        ),
    )

    // ═══════════════════════════════════════════════════════════════
    // GENERAL - Tips applicable anywhere
    // ═══════════════════════════════════════════════════════════════
    private val generalTips = listOf(
        Tip(
            id = "general_settings",
            message = "Find more options in General Settings (gear icon in the top toolbar).",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_ai_preferences",
            message = "Set up AI Preferences in Settings to get personalized task suggestions that fit YOUR life.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_voice_preferences",
            message = "Don't like filling forms? AI Preferences has Voice Setup - just talk for 30 seconds!",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_daily_rhythm",
            message = "Tell the AI your Daily Rhythm - morning person, night owl, deep focus or task switcher?",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_backup",
            message = "Enable Google Drive backup in Settings > Advanced to protect your data.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_date_format",
            message = "Change date and time format in Settings > Basic to match your preference.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_tips_toggle",
            message = "You can disable these tips in Settings > Basic if you prefer.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_key_legends",
            message = "See Help > Key Legends for icon and badge explanations.",
            contexts = setOf(TipContext.GENERAL)
        ),
    )

    // ═══════════════════════════════════════════════════════════════
    // SEMANTICGENIUS - Tips about note understanding and Smart Link
    // ═══════════════════════════════════════════════════════════════
    private val semanticTips = listOf(
        Tip(
            id = "semantic_just_write",
            message = "Just write naturally. SemanticGenius understands context, not just keywords.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "semantic_privacy",
            message = "All note analysis happens on your device. Your handwriting never leaves your tablet.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "semantic_boox_notes",
            message = "Enable Boox Notes indexing in Settings to make your notebooks searchable.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "semantic_names_dates",
            message = "Include names, dates, and topics in notes for better Smart Link matches.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "semantic_search",
            message = "Your handwritten notes are searchable! Use Search to find content by meaning.",
            contexts = setOf(TipContext.MEMOS, TipContext.TASKS)
        ),
        Tip(
            id = "semantic_action_items",
            message = "SemanticGenius detects action items in your notes.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "semantic_cross_day",
            message = "Smart Link matches notes to events across days.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "semantic_domains",
            message = "SemanticGenius classifies notes into 22 domains.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "semantic_learns",
            message = "SemanticGenius learns your patterns over time.",
            contexts = setOf(TipContext.GENERAL)
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
            id = "gesture_customize",
            message = "Customize gestures in Settings > Gestures.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "gesture_calendar_longpress",
            message = "Long press a date for Quick View.",
            contexts = setOf(TipContext.CALENDAR)
        ),
    )

    // ═══════════════════════════════════════════════════════════════
    // DISCOVERY - Cross-feature tips (shown when NOT in that context)
    // ═══════════════════════════════════════════════════════════════
    private val discoveryTips = listOf(
        Tip(
            id = "discover_tasks_from_memos",
            message = "Try the Tasks tab! Track your to-dos with checkboxes and priority stars.",
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
            id = "discover_voice_tasks",
            message = "Say multiple tasks at once with Voice to Task. It's faster than typing!",
            contexts = setOf(TipContext.MEMOS, TipContext.EVENTS),
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
            message = "AI-powered handwriting recognition can convert your notes to typed text.",
            contexts = setOf(TipContext.TASKS, TipContext.EVENTS),
            isDiscovery = true
        ),
        Tip(
            id = "discover_export",
            message = "Export your memos as PDF from the toolbar menu for sharing or printing.",
            contexts = setOf(TipContext.TASKS, TipContext.EVENTS),
            isDiscovery = true
        ),
        Tip(
            id = "discover_ai_preferences_tasks",
            message = "Smart Suggestions work better when you tell the AI about yourself. Try Settings > AI Preferences!",
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
            message = "Overwhelmed? Optimize My Day analyzes your calendar and creates an optimized task schedule.",
            contexts = setOf(TipContext.MEMOS, TipContext.EVENTS),
            isDiscovery = true
        ),
        Tip(
            id = "discover_daily_rhythm",
            message = "Tell the AI if you're a morning person or night owl - it personalizes all suggestions!",
            contexts = setOf(TipContext.MEMOS, TipContext.EVENTS, TipContext.CALENDAR),
            isDiscovery = true
        ),
        Tip(
            id = "discover_voice_setup",
            message = "AI Preferences Voice Setup is magic! Just describe yourself naturally - the AI figures out the rest.",
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
            message = "Enable Vacation Mode, Crunch Mode, or Holiday Mode in AI Preferences when your routine changes.",
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
            message = "Smart Suggestions can read your recent handwritten memos! Your notes become AI-powered task ideas.",
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
