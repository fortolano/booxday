package com.boox.day.tips

/**
 * Repository of all tips in the app.
 *
 * Tips are organized by category:
 * - Contextual tips: Shown when user is in the relevant context (75% weight)
 * - Discovery tips: Promote features in other areas of the app (25% weight); their
 *   contexts say where they are shown (e.g. a Tasks tip shown on Memos)
 *
 * Checked against the app on 29 Oct 2026 (1.0.47): every menu path and label exists, one tip
 * per feature, and the newer features (full screen, two-finger move, Cross out = done,
 * Start-the-day review, Search, sleep screen, daily notes for Obsidian, Connect OpenRouter,
 * AI Models, Check for Updates) have theirs. TipsRepositoryTest checks ids, length and
 * Settings paths. Wording follows Help (ui/dialogs/HelpDialog.kt).
 */
object TipsRepository {

    /** Get all tips from all categories */
    fun getAllTips(): List<Tip> =
        memosTips + tasksTips + calendarTips + eventsTips + booxNotesTips + generalTips + aiTips + discoveryTips

    // ═══════════════════════════════════════════════════════════════
    // MEMOS TAB - Contextual Tips (shown when Memos tab is active)
    // ═══════════════════════════════════════════════════════════════
    private val memosTips = listOf(
        Tip(
            id = "memos_pens",
            message = "Tap the pen again to choose Pen, Fountain Pen, Marker, Pencil or Brush, and its size and color.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_eraser",
            message = "Tap the eraser again for Standard Eraser, Stroke Eraser or Scribble to Erase.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_eraser_end",
            message = "Turn the pen around: its eraser end erases too.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_undo",
            message = "Made a mistake? Tap undo in the toolbar.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_selection",
            message = "Long-press an empty spot to select an area, or use Lasso select. Then move, copy or delete it.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_select_objects",
            message = "Long-press an image or a text box to select it. Tap inside a selected text box to edit it.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_insert",
            message = "Insert adds an image, a background, a table, a text box or a link to a file on the tablet.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_pages",
            message = "Need another page? The forward arrow on the last memo page adds one.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_full_screen",
            message = "Need more room? The full screen switch (by ≡, or in ≡ on small tablets) hides the calendar.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_more_below",
            message = "A small arrow on the full screen switch means there is ink lower on the page.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_two_finger_move",
            message = "Slide two fingers on a memo page to move it and see the rest of your ink.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_paste",
            message = "Two-finger tap pastes ink or an image copied in BooxDay, or text copied in another app.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_export",
            message = "≡ → Export Pages saves this page or the whole day as PDF, PNG or JPG in Documents/BooxDay.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_boox_notes_button",
            message = "The B on the Memos toolbar opens your Boox Notes.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_ocr",
            message = "Recognize Handwriting (the AI button) reads a page's ink: copy the text or put it in place of the ink.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_voice_to_text",
            message = "Voice to Text (the AI button) turns what you say into a typed note, with punctuation.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "memos_summarize_translate",
            message = "Summarize and Translate (the AI button) work on this page or every page of the day.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "ai_memo_to_tasks",
            message = "Convert Memo to Tasks finds tasks in your notes. Tick the ones to add.",
            contexts = setOf(TipContext.MEMOS)
        ),
    )

    // ═══════════════════════════════════════════════════════════════
    // TASKS TAB - Contextual Tips (shown when Tasks tab is active)
    // ═══════════════════════════════════════════════════════════════
    private val tasksTips = listOf(
        Tip(
            id = "tasks_handwriting",
            message = "Write a task by hand on a row, or tap › on an empty row to type it.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_cross_out",
            message = "Draw a line through a task to tick it. Cross out = done in the task actions menu turns this off.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_checkbox",
            message = "Tick a checkbox to mark a task done. Long-press a checkbox to tick the whole page.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_priority",
            message = "Tap a task's priority button to pick A (High), B (Medium), C (Low) or None.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_card",
            message = "Tap › on a task for its card: Move or Copy, Repeat, Reminder and Break Down.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_edit_text",
            message = "Long-press › to edit a task's text straight away.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_clear",
            message = "Long-press a task row to clear it.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_pages",
            message = "A task page has 10 rows. The forward arrow on the last page adds another page.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_full_screen",
            message = "In full screen a task page shows about 17 rows.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_bulk_actions",
            message = "The task actions menu moves, copies or deletes the tasks of this page or the whole day.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_convert_handwriting",
            message = "Convert handwriting… in the task actions menu turns written tasks into typed text.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_start_day",
            message = "Unfinished tasks from earlier days? Start-the-day review in the task actions menu lists them.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_per_day",
            message = "Each day has its own task list. Tap a date in the calendar to see that day's tasks.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_streak",
            message = "The flame counts the days in a row you finished a task. Tap it for your stats.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_handwriting_language",
            message = "Task Handwriting in Settings → Preferences sets the language for reading written tasks.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_smart_suggestions",
            message = "Smart Suggestions offers task ideas from your calendar, memos and recent tasks, not ones already on your list.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_plan_my_day",
            message = "Optimize My Day proposes changes to your day. Nothing changes until you tap a proposal.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_sort_by_priority",
            message = "Sort by Priority orders this page or the whole day. Check the order, then tap Apply Order.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_breakdown",
            message = "Big task? Break Down Task splits it into smaller ones.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_daily_summary",
            message = "End your day with Daily Summary: see what you finished and move the rest to tomorrow.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "tasks_weekly_summary",
            message = "Weekly Summary shows the week's totals, your best day and tasks left undone again and again.",
            contexts = setOf(TipContext.TASKS)
        ),
    )

    // ═══════════════════════════════════════════════════════════════
    // CALENDAR - Contextual Tips (always visible in top section)
    // ═══════════════════════════════════════════════════════════════
    private val calendarTips = listOf(
        Tip(
            id = "calendar_select_date",
            message = "Tap a date to open that day's memos, tasks and events.",
            contexts = setOf(TipContext.CALENDAR)
        ),
        Tip(
            id = "calendar_preview",
            message = "Long-press a date for a short preview of that day.",
            contexts = setOf(TipContext.CALENDAR)
        ),
        Tip(
            id = "calendar_nav",
            message = "The arrows next to the month name go to the previous or next month.",
            contexts = setOf(TipContext.CALENDAR)
        ),
        Tip(
            id = "calendar_today",
            message = "Today has a black ring. The today button in the calendar header jumps back to it.",
            contexts = setOf(TipContext.CALENDAR)
        ),
        Tip(
            id = "calendar_marks",
            message = "A small mark under a date means events; a B means Boox Notes. The day header shows memos and tasks.",
            contexts = setOf(TipContext.CALENDAR)
        ),
        Tip(
            id = "calendar_search",
            message = "Tap the magnifier in the calendar header to search your memos, tasks, events and Boox Notes.",
            contexts = setOf(TipContext.CALENDAR)
        ),
        Tip(
            id = "calendar_first_day",
            message = "Start the week on Sunday, Monday or Saturday: Settings → Calendar & Sync → First Day of Week.",
            contexts = setOf(TipContext.CALENDAR)
        ),
        Tip(
            id = "calendar_week_numbers",
            message = "On larger tablets, Show Week Numbers is in Settings → Calendar & Sync.",
            contexts = setOf(TipContext.CALENDAR)
        ),
    )

    // ═══════════════════════════════════════════════════════════════
    // EVENTS - Contextual Tips (always visible in top section)
    // ═══════════════════════════════════════════════════════════════
    private val eventsTips = listOf(
        Tip(
            id = "events_add",
            message = "Tap + to add an event.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "events_voice",
            message = "Tap the mic in the calendar header and say an event, like 'Lunch with Sarah Friday at noon'.",
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
            id = "events_sync_now",
            message = "Tap the cloud icon in the calendar header to sync now. A number means changes are waiting to be sent.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "events_local",
            message = "Want an event only on this tablet? Under Save to, pick This device only: it never syncs.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "events_reminders",
            message = "Set how early event reminders come in Settings → Preferences → Event Reminders.",
            contexts = setOf(TipContext.EVENTS)
        ),
    )

    // ═══════════════════════════════════════════════════════════════
    // BOOX NOTES AND SMART LINK
    // ═══════════════════════════════════════════════════════════════
    private val booxNotesTips = listOf(
        Tip(
            id = "boox_notes_day_header",
            message = "Tap the B in the day header to see that day's Boox Notes.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "boox_notes_badge",
            message = "A B on an event means linked Boox Notes; a number shows how many.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "boox_notes_suggestions",
            message = "Review suggestions in Boox Notes lists the events your notes likely belong to. Link strong… links them all.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "boox_notes_unlinked",
            message = "Turn on Unlinked only in Boox Notes → All notes to find notes without an event.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "boox_notes_date_title",
            message = "Put a date in a Boox note's title to set its day.",
            contexts = setOf(TipContext.EVENTS)
        ),
        Tip(
            id = "boox_notes_titles",
            message = "Names and topics in a note's title help Smart Link match it to the right event.",
            contexts = setOf(TipContext.MEMOS)
        ),
        Tip(
            id = "boox_notes_indexing",
            message = "Enable Indexing in Settings → Boox Notes makes your handwritten memos and notebooks searchable.",
            contexts = setOf(TipContext.MEMOS, TipContext.EVENTS)
        ),
        Tip(
            id = "boox_notes_action_items",
            message = "In an event's Meeting notes, Action items lists the to-dos from that day's memos.",
            contexts = setOf(TipContext.MEMOS)
        ),
    )

    // ═══════════════════════════════════════════════════════════════
    // GENERAL - Tips applicable anywhere
    // ═══════════════════════════════════════════════════════════════
    private val generalTips = listOf(
        Tip(
            id = "general_settings",
            message = "Find more options in the Settings menu (the gear in the calendar header).",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_help",
            message = "Settings → Help explains every feature. Key Legends shows what the marks mean.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_screen_refresh",
            message = "Traces of earlier screens? ≡ → Screen Refresh cleans up the display.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_sleep_screen",
            message = "Turn on Today on sleep screen in Settings → Preferences to see today's plan while the tablet sleeps.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_daily_notes",
            message = "Use Obsidian? Daily notes (Markdown) in Settings → Preferences writes each day to a Markdown file.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_daily_notes_folder",
            message = "Daily notes → Choose Folder can point at your Obsidian vault. Write Today's Note saves it now.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_daily_notes_kept",
            message = "Daily notes keep what you write around BooxDay's part of the file, so you can add to them in Obsidian.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_backup",
            message = "Settings → Data & Backup saves a backup to Google Drive or the tablet. Auto Backup does it for you.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_updates",
            message = "BooxDay checks for new versions itself. Settings → About → Check for Updates looks now.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_date_format",
            message = "Change the date and time format in Settings → Preferences.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_tips_toggle",
            message = "Show Tips in Settings → Preferences turns these tips off or shows them less often.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "general_voice",
            message = "Voice uses Google's free speech recognition when the tablet has it. See Settings → Voice-to-Text.",
            contexts = setOf(TipContext.GENERAL)
        ),
    )

    // ═══════════════════════════════════════════════════════════════
    // AI - Setup, models and privacy
    // ═══════════════════════════════════════════════════════════════
    private val aiTips = listOf(
        Tip(
            id = "ai_connect_openrouter",
            message = "AI runs on your own OpenRouter account. Settings → AI Settings → Connect OpenRouter signs you in.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "ai_models",
            message = "Pick the model for each AI job in Settings → AI Settings → AI Models, or keep BooxDay's choice.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "ai_off",
            message = "Turn AI Features off in Settings → AI Settings and none of your data goes to OpenRouter.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "ai_privacy",
            message = "More privacy with AI: use paid models and turn on Zero Data Retention in your OpenRouter account.",
            contexts = setOf(TipContext.GENERAL)
        ),
        Tip(
            id = "ai_prefs_rhythm",
            message = "Morning person or night owl? Tell AI Preferences when you're most productive (About Me).",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "ai_prefs_modes",
            message = "Routine changing? Turn on Vacation, Crunch or Holiday mode in AI Preferences → AI Style.",
            contexts = setOf(TipContext.TASKS)
        ),
        Tip(
            id = "ai_prefs_priorities",
            message = "Pick your top priorities in AI Preferences so suggestions focus on them.",
            contexts = setOf(TipContext.TASKS)
        ),
    )

    // ═══════════════════════════════════════════════════════════════
    // DISCOVERY - Cross-feature tips, shown in the contexts listed
    // ═══════════════════════════════════════════════════════════════
    private val discoveryTips = listOf(
        Tip(
            id = "discover_tasks_from_memos",
            message = "Try the Tasks tab: rows for your to-dos, with checkboxes and A/B/C priorities.",
            contexts = setOf(TipContext.MEMOS),  // Show when on Memos to promote Tasks
            isDiscovery = true
        ),
        Tip(
            id = "discover_memos_from_tasks",
            message = "Need free-form notes? The Memos tab gives you pages to write and draw on.",
            contexts = setOf(TipContext.TASKS),  // Show when on Tasks to promote Memos
            isDiscovery = true
        ),
        Tip(
            id = "discover_smart_suggestions",
            message = "Need ideas? Smart Suggestions in the Tasks tab reads your calendar and memos.",
            contexts = setOf(TipContext.MEMOS),
            isDiscovery = true
        ),
        Tip(
            id = "discover_plan_my_day",
            message = "Busy day? Optimize My Day in the Tasks tab looks at your events and tasks and proposes a plan.",
            contexts = setOf(TipContext.MEMOS),
            isDiscovery = true
        ),
        Tip(
            id = "discover_daily_summary",
            message = "Daily Summary in the Tasks tab shows what you finished today.",
            contexts = setOf(TipContext.MEMOS),
            isDiscovery = true
        ),
        Tip(
            id = "discover_ai_ocr",
            message = "Recognize Handwriting in the Memos tab turns your notes into typed text.",
            contexts = setOf(TipContext.TASKS),
            isDiscovery = true
        ),
        Tip(
            id = "discover_memo_to_tasks",
            message = "Wrote a to-do in your memos? Convert Memo to Tasks in the Memos tab adds it to your list.",
            contexts = setOf(TipContext.TASKS),
            isDiscovery = true
        ),
        Tip(
            id = "discover_ai_preferences",
            message = "AI suggestions too generic? Tell AI Preferences (Settings → AI Settings) a little about you.",
            contexts = setOf(TipContext.MEMOS, TipContext.TASKS),
            isDiscovery = true
        ),
        Tip(
            id = "discover_boox_notes",
            message = "Your Boox Notes can be linked to calendar events, so meeting notes are one tap away.",
            contexts = setOf(TipContext.TASKS),
            isDiscovery = true
        ),
    )
}
