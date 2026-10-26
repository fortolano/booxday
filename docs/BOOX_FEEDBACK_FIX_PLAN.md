# BOOX feedback fix plan (from Reddit + GitHub, 25 Oct 2026)

## Review summary (1.0.37, 25 Oct 2026)

- **Commit reviewed:** `04c9d8c` ("Version 1.0.37"). The tag `v1.0.37` was created during the review and points to this same commit, so this is the released code. `git log v1.0.36..HEAD` lists 17 commits; the three after this plan's base `2f4a731` are `0c17cfc`, `f4ee7ce` and `04c9d8c`. There is nothing on main after `04c9d8c`. All code was read with `git show 04c9d8c:<path>`.
- **Owner's rule for this plan (25 Oct):** the goal is not to make every BOOX model work at the cost of quality, responsiveness or pen latency. Propose only what is confirmed, safe, worth doing, and costs no speed. Anything unproven stays a check or a tester question. Each step now states its runtime cost.
- **How to read this plan:** the original text is kept. Where a "Review (1.0.37)" note differs from the text above it, follow the note.
- **Confirmed in the 1.0.37 code:** 1.1 (both causes, and the midnight end is in both Google paths), 1.2, 1.3, 1.4, 1.6, 1.7, 1.8. 1.5 is wider than listed. None of the Part 1 files, `Router.kt`, `einkHelper.kt` or `OnyxCompat.kt` changed in 1.0.37 (`git diff --stat 2f4a731 04c9d8c`).
- **What 1.0.37 changed for this plan:**
  - `f4ee7ce`: a page image is saved once its edits have paused for 2 s (`PerKeyDebouncer`). Before, the 500 ms throttle could skip the last state. Readers of the saved image save a pending one first (`loadPersistBitmap` → `flushPendingPersist`; all four callers run off the main thread). A page waiting for its save is never evicted. This removes one cause of a stale page image (2.3) and about a quarter of a CPU core of work while writing (2.2).
  - `0c17cfc`: on the SurfaceFlinger pen backend, finished strokes are posted to the surface 250 ms after the pen rests (`schedulePenRestBlit`), not after every stroke. On the Go 10.3 the median from pen-down to ink went from 81.6 to 7.5 ms. This is the measured fix for late ink. It was not measured on any other tablet.
  - Neither commit touched the forceUpdate observer, `cancelObservers`, `updateState`, the startup raw-drawing open, `PageView.changePage` / `persistBitmapDebounced` (comment only), or `getCachedBitmapExact`.
- **Owner's test (26 Oct):** 1.0.37 works well on the owner's Go 6 Gen II (Android 13, active pen). Checked by use, not measured. Still untested: EMR pens, and Android 11, 12 and 16.
- **26 Oct, on the Go 10.3:**
  - 4.1 (a) and (b) are confirmed on the tablet, search included.
  - The check also found search results showing "TITLE: Untitled" (4.10).
  - It found the clear actions saying "Today" for the day on screen (4.10).
  - It found the task page counter not updating after a clear (4.1 b2).
- **Owner's list (26 Oct), added as Part 4:**
  - Cleared memos and tasks come back. Two faults in the code: "Clear All Memos Today" clears other pages only in the database, and "Clear All Tasks Today" doesn't leave full screen first.
  - Texts that wrongly say handwriting never leaves the tablet.
  - The Boox Notes X missing outside the list view.
  - "More below" hints.
  - Start screens on the Go 6.
  - Out-of-date Help, welcome, tips and AI Settings texts.
  - The AI Preferences wizard losing answers on close.
  - None of it touches the pen path.
- **What users actually reported** (re-read 25 Oct: the four threads hold 94 comments, plus the 16 Oct open-source thread and GitHub issue #1, which has no comments):
  - Backed by a report: 1.1 (a full-week Google event shown only on Monday), 1.2 (the exact "Sign-in failed: Sign-in failed: 10:" text, and a sign-in loop with no message), 1.4, 1.7, 3.1, 3.7.
  - Found in the code, not reported: the midnight end in 1.1, 1.3, 1.5, 1.6. They are kept because they are text-only or cost nothing.
  - Part 2 rests on few, old or off-target reports. White screen: 4 users, all on Tab models (Tab Ultra, Tab Mini C, Tab Ultra C), none of them a target tablet. Lasting lag: 1 user, Dec 2025 beta. Tasks page not drawn: 1 user, Mar 2026, Tab Ultra C Pro.
  - **Missing from the plan:** the newest report on a target tablet. On a Note Air 6C (Android 16, EMR pen) the pen didn't write on 1.0.31/1.0.32 (16 Oct), and 1.0.33 crashed at start (20 Oct). Added as 2.4.
  - The 6C's "View" and "Open" buttons doing nothing: the developer's 19 Oct reply says 1.0.33 fixed them; the reporter hasn't confirmed.
- **What this review changed in the plan:**
  - 2.1: issue #1's symptom (a white cover over a pen that still works) was already fixed in 1.0.33 (`a624882`, Router.kt), and the issue was filed before that release. The first step is now asking the reporter to try 1.0.37. No code unless a target tablet shows it.
  - 2.2: the claimed cause is most likely wrong. The reopen it says is missing happens through the memoModeChanged observer, and a closed pen session gives no ink, not slow ink. The NonCancellable fix is dropped and the check now measures latency first.
  - 2.3: causes (1) and (2) corrected. Fix (2) is dropped (`updateActiveSurface` already does it). Cause (1) can only give a short blank flash, because every page switch ends with a full redraw. The lasting wrong page in the report has no cause in the 1.0.37 code. So fix (1) (by reference only, never a bitmap copy) waits for the check; if the check shows nothing, no code.
  - 2.4 (new): Note Air 6C, check only: get the 1.0.37 crash report from the reporter.
  - 1.1: use the day list's own condition (no COALESCE, can use the index), skip the unused all-dates queries, fix the after-sync refresh that drops adjacent-month dots, and use one shared end-day function in all three writers.
  - 1.5: more wrong menu paths (Help, Welcome); the gestures tip points to a setting that doesn't exist.
  - 1.7: open the list already scrolled, with no scroll animation.
  - 1.9: dropped. Every target tablet, and both tablets reported as "not compatible" on Play, have a microphone, and the line changes nothing for GitHub installs.
  - 3.3: the January crashes were not EMR-only: the Go 7 and Tab X C have active pens.
  - Device facts in the Context list and in 3.6; a privacy note for logs (1.8, 3.5).
- **Not verified:** no tablet was connected, so no getprop or logcat. No build or tests, because the release build was running and Gradle was off limits. The Reddit archives reach about 24 Oct, and the comments on the 24 Oct post weren't archived yet. Device specs come from public pages; shop.boox.com refused repeated requests, so the Go 10.3 Gen I and Gen II specs rest on the research pass. Every Part 2 cause still needs logs from a tablet.

## Context

On 25 Oct 2026 we read BooxDay feedback from r/Onyx_Boox: the beta thread (Dec 2025), the closed test thread (Jan–Jun 2026) and a "vapourware" thread. Most of it is about the old closed builds (1.0.6–1.0.29). We also read GitHub issue #1, which is about 1.0.31 and later: on a Tab Ultra C (Android 11, firmware 4.2) the screen goes white after the start screens. We traced every report to today's code, and the line references were spot-checked.

Review (1.0.37): re-traced against `04c9d8c` on 25 Oct; see the summary above.

Goal: fix what is clear now, prove the cause of the canvas problems before fixing them, and leave the owner's decisions to the owner. Everything must work on the target tablets:
- Go 6 Gen II, Go 7 / Go Color 7 Gen II
- Go 10.3 Gen I (EMR pen) and Gen II (USI pen)
- Note Air 4C, 5C and 6C
- Note Max (nice to have)

Review (1.0.37): pens and Android versions, from BOOX's spec pages and reviews (25 Oct). BOOX says "active pen" and never names a standard. Reviewers call the Go 7 series pens USI 2.0.
- **Go 6 Gen II:** optional InkSense Plus pen, which is active (has a battery). It shipped with Android 11, and an Android 13 update has been rolling out in batches since 24 Sep 2026. The owner's unit runs 13, so it has the update.
- **Go 7 (B&W, 2025) and Go Color 7 Gen II:** active InkSense pens, Android 13.
- **Go 10.3 Gen I:** passive EMR pen (Pen Plus), Android 12.
- **Go 10.3 Gen II and Lumi:** active InkSense Plus pen, in the box, Android 15.
- **Note Air 4C / 5C / 6C:** EMR pens, Android 13 / 15 / 16.
- **Note Max:** EMR pen, Android 13.
- **What this means for testing:** the owner's two tablets are both active-pen tablets, on Android 13 and 15. Neither covers EMR, or Android 11, 12 or 16. The owner's 19 Oct Reddit reply says 1.0.33 writing was also tried on a "5C"; if that Note Air 5C is at hand, it is the one EMR tablet to check on.

**Rules for the session that carries this out** (from CLAUDE.md):
- Re-read each file right before editing it. Line numbers below are from 25 Oct and will drift, so find code by name.
- Run focused tests only (`--tests`). Build with `.\gradlew.bat assembleDebug`.
- Make one local commit per step, and never push.
- Never install on a tablet without asking the owner first.
- Before touching a **protected fix** (CLAUDE.md table), get the owner's OK.
- **Owner's rule (25 Oct):** no step may add work to the pen path (per stroke or per point), slow startup or day switching, or lower drawing quality. If a step can't meet that, stop and report.
- Review (1.0.37): the note about another session's uncommitted `editor/DrawCanvas.kt` changes is settled: the pen-rest blit is committed (`0c17cfc`), and so is the page-image save change (`f4ee7ce`). Part 2 below was re-checked against both. Still run `git status` first.
- Step 0: copy this plan to `docs/BOOX_FEEDBACK_FIX_PLAN.md`, commit it ("Plan: fixes from BOOX user feedback"), and tick steps off there as they land.

Paths are under `app/src/main/java/com/boox/day/`, and tests under `app/src/test/java/com/boox/day/`.

---

## Part 1: fixes ready now (cause known, unit-testable)

The steps don't depend on each other. Do them in this order (most user-visible first).

### 1.1 Event dots on every day of a multi-day event
- **Cause:** the badge queries take only the start day: `SELECT DISTINCT dayId`, in `data/db/CalendarEvent.kt` (`getDatesWithEvents`, `getDatesWithEventsForMonth`, `…Filtered`) and in `data/db/LocalEvent.kt`. The month versions use `dayId LIKE 'yyyy-MM%'`, so an event that starts last month and runs into this one gets no dot at all.
- **Change:**
  - Make the month queries return `(dayId, endDayId)` rows that overlap the month: `WHERE dayId <= :lastDay AND COALESCE(NULLIF(endDayId,''), dayId) >= :firstDay`, keeping the `calendar_selections` join in the filtered versions.
  - In the repository, expand each row with the existing `expandCoveredDayIds()` (`data/db/SmartLinkDateRanges.kt`), clipped to the month, so one long event adds at most 31 days.
  - Apply the same to the all-dates queries; give them a date window, or clip to the months asked for.
  - Callers that consume the `Set<String>`:
    - `CalendarEventRepository.preWarmCacheForMonth`
    - `data/CacheWarmupCoordinator.kt`
    - `LocalEventRepository`
    - `viewmodel/EventsViewModel.getDatesWithEvents*`

    Their result type stays the same. `preWarmCacheForMonth` should warm the covered days.
- **Same step, needed for the dots to be right: events that end exactly at midnight.**
  - A 22:00–00:00 event gets `endDayId` = the next day, in three places: `sync/EventConverters.kt` (timed branch), `data/model/ProviderEvent.kt` and `LocalEventService`.
  - Fix: a timed event whose end is exactly local midnight, and after its start, ends on the previous day.
  - Check that existing rows get the new `endDayId` on the next sync (`MultiAccountSyncManager` upsert, `smartLinkFieldsChanged`). If they don't, compute it from `endTime` in the badge expansion.
- **Tests:**
  - Extend `data/db/CalendarEventLinkSurvivalTest.kt`, or add `CalendarEventBadgeDaysTest` (Robolectric, in-memory Room). Cover: a 5-day event gives 5 dots, an event from last month gives dots this month, a blank `endDayId` gives its start day, the filtered query skips disabled calendars, and a local event.
  - Extend `sync/EventConvertersTest.kt` for the midnight end.
- **Review (1.0.37):** both causes confirmed, unchanged in 1.0.37. Reported once (22 Jan, Note Air 3C, Google): a full-week appointment shows only on Monday. Nobody reported the midnight end; it was found in the code. Changes to the approach:
  - **Query:** use the condition the day's event list already uses (`getByDayIdFiltered`: `dayId <= :day AND endDayId >= :day`), so: `WHERE dayId <= :lastDay AND endDayId >= :firstDay`. A dot then appears exactly on the days where the list shows the event. Drop the COALESCE: every writer fills `endDayId` (`toLocalEvent`/`toCalendarEvent` use `ifEmpty { dayId }`, `ImportService` too), the day list already skips blank rows, and the expression would stop SQLite using the `endDayId` index (both tables index `dayId` and `endDayId`). Keep `syncStatus != 'PENDING_DELETE'` for local events. In the test, replace "a blank `endDayId` gives its start day" with "a blank `endDayId` behaves like the day list".
  - **Clip** each row's start and end to the month before calling `expandCoveredDayIds()`: the helper has no limit of its own.
  - **Leave the all-dates queries alone:** `EventsViewModel.getDatesWithEvents()`, `getDatesWithEventsFiltered()` and `LocalEventDao.getDaysWithEvents()` have no callers.
  - **Real callers:** `CacheWarmupCoordinator.warmupForMonth` (the current and both adjacent months, for the grid's overflow days) and `warmupForMonthExclusive`, through both `preWarmCacheForMonth`; `EventsViewModel.getDatesWithEventsForMonth` (MainScreen's after-sync refresh); `LocalEventService.getDatesWithEventsForMonth`.
  - **Also in this step:** MainScreen's "Refresh events dates after sync completes" effect replaces `datesWithEvents` with the current month only, so after every sync the adjacent-month days in the grid lose their dots (the warmup had loaded three months). Load the same three months there. Keep that edit to one call: add a small `EventsViewModel` function that returns the three months, and call it in place of `getDatesWithEventsForMonth(currentYearMonth)`. The call sits inside the `LaunchedEffect` block, which compiles to its own class, so MainScreen's root lambda (at its method-size limit) doesn't grow.
  - **Midnight end:** the three writers must agree. `EventConverters.extractGoogleEventTiming` is used by `CalendarSyncManager` (Google). `ProviderEvent.endDayId` is used by `MultiAccountSyncManager` for every provider, and for Google its write comes second, so it is the one that stays. `LocalEventService.createEvent` is the third. Put the rule in one small pure function and call it from all three. `EventsViewModel`'s in-app create paths set `endDayId = date` directly; they are single-day by construction, so leave them.
  - **Existing rows:** every sync writes all fetched events (`eventRepository.cacheEvents`), so rows inside the sync window get the new `endDayId` at the next sync, and no migration is needed. `smartLinkFieldsChanged` compares `endDayId`, so those days are re-linked once (in the background).
  - **Runtime cost:** nothing on the pen path. One range query per month on IO (at month change and after sync), at most 31 days per event.
  - **Tests:** `SmartLinkDateRangesTest` and `EventConvertersTest` exist; add the DAO test (Robolectric) as written, plus a test that both Google paths give the same `endDayId`.
  - **Done (26 Oct):**
    - The month queries are now `CalendarEventDao.getDaySpansFiltered` and `LocalEventDao.getDaySpans` (`dayId <= :lastDay AND endDayId >= :firstDay`). `data/db/EventBadgeDays.kt` expands them, cut to the month. Both repositories' `getDatesWithEventsForMonth` and `preWarmCacheForMonth` use them, so the warmup also caches the covered days.
    - MainScreen's after-sync refresh calls the new `EventsViewModel.getDatesWithEventsAroundMonth` (three months), inside its `LaunchedEffect`.
    - Midnight end: `data/model/EventDays.kt` `timedEventLastDay`, called by `EventConverters`, `ProviderEvent.endDayId` and `LocalEventService.createEvent`.
    - The unused all-dates queries are left alone.
    - Tests: `EventDaysTest` (4), `EventBadgeDaysTest` (3: week, from last month, into next month, hidden calendar, blank end day, long event, local and deleted local events; dots compared with the day list for all 30 days), and a new `EventConvertersTest` case (both sync paths agree on the midnight end). All pass, with `SmartLinkDateRangesTest`.
    - Not checked on a tablet.

### 1.2 Google sign-in errors in plain words
- **Cause:**
  - `sync/provider/GoogleCalendarProvider.kt` (`catch ApiException`) wraps the error as `"Sign-in failed: ${e.message}"` (message `"10: "`).
  - `sync/MultiAccountSyncViewModel.kt` (Google around line 348, Outlook around 411) adds `"Sign-in failed: "` again.
  - Code 12501 shows nothing at all.
  - `ui/dialogs/DataBackupDialog.kt` (Drive sign-in catch) shows nothing.
- **Change:** add `sync/SignInErrorText.kt`, a pure function `(statusCode: Int?, network: NetworkState) -> String`. Reuse `api/NetworkStatus.current()` and follow the `api/ai/AiProblem` wording style. Mapping, starting from the old `viewmodel/CalendarSyncViewModel.kt` text:

  | Situation | Message |
  |---|---|
  | No Wi-Fi / no internet | Same words as AiProblem |
  | Code 7 | "Couldn't reach Google. Check the internet connection." |
  | Code 10 | "Google didn't accept Boox Day's sign-in (code 10). This is a setup problem in Boox Day, not your account. Please report it on GitHub." |
  | Code 12500 | "Google sign-in failed. Make sure Google Play services is up to date, then try again." |
  | Code 12501 | "Sign-in was cancelled." (shown, so something visible happens) |
  | Code 12502 | "Sign-in is already in progress." |
  | Anything else | "Google sign-in failed (code N)." |

  Then:
  - The provider sends the status code in the exception, and the ViewModel shows its message without adding a prefix.
  - Outlook drops the double prefix.
  - `DataBackupDialog` shows the same text.
  - The `multiProviderSignInLauncher` in `ui/views/MainScreen.kt` passes `resultCode` through so RESULT_CANCELED maps to 12501. Keep this edit small: MainScreen is at its method-size limit.
- **Tests:** add `sync/SignInErrorTextTest.kt` (plain JUnit, like `api/ai/AiProblemTest.kt`).
- **Owner, outside code:** see 3.2 (Google console check).
- **Review (1.0.37):** confirmed. `GoogleCalendarProvider.handleSignInResult` wraps `"Sign-in failed: ${e.message}"`, and `MultiAccountSyncViewModel.handleGoogleSignInResult` and `addOutlookAccount` add `"Sign-in failed: "` again. 12501 becomes `CancelledException`, which shows nothing. The Drive launcher in `DataBackupDialog` catches silently. `multiProviderSignInLauncher` passes only `result.data`; with RESULT_CANCELED and no data, GoogleSignIn reports code 8, not 12501, so passing `resultCode` is right. That edit is inside the launcher's own lambda, not MainScreen's root lambda. For 12500, also say to turn on Google Play in the BOOX settings (it is off by default on many BOOX tablets); owner to confirm the words. Runtime cost: none.
  - Reports: 21 Jan, exactly "Sign-in failed: Sign-in failed: 10:", still failing after the next update on a Play install. 14 Dec: a sign-in loop with no message, which fits the silent 12501. Code 10 on the Play builds points to the signing certificate of the Play build. GitHub builds use the owner's release key, and sign-in works on the owner's tablets, so that key's SHA-1 is very likely registered already. The consent-screen check in 3.2 is what still matters for other users.
- **Done (26 Oct, texts approved by the owner):**
  - `sync/SignInErrorText.google(statusCode, network)` gives the table's texts.
    - 12500 also says to turn on Google Play in the BOOX settings.
    - With no code (an unexpected error) it says "Google sign-in failed."
    - Offline, the AI wording wins for every case except cancel and "already in progress".
  - `GoogleCalendarProvider` uses it for its failures.
  - `MultiAccountSyncViewModel`:
    - Shows the provider's message with no prefix, for Outlook too.
    - Says "Sign-in was cancelled." instead of nothing.
    - Takes `resultCode`, so RESULT_CANCELED with no data counts as a cancel, not code 8.
  - `DataBackupDialog` shows the text in the Google Drive card in place of "Not connected" (backup and restore screens).
  - MainScreen's launcher passes `result.resultCode` (inside the launcher lambda).
  - MainScreen's old `signInLauncher` (legacy `CalendarSyncViewModel` messages) is never launched; left alone.
  - Test: `SignInErrorTextTest` (3). Not checked on a tablet.

### 1.3 Boox Notes indexing shows "waiting" when the monthly limit is reached
- **Cause:** `ocr/UnifiedOcrPipeline.kt` returns `BUDGET_EXHAUSTED` on `!quotaManager.canReadWithAi()` (1000 reads a month) without calling `IndexingStatus.onWaiting`. The settings line then says "N items pending • Processing during idle time" forever (`ui/dialogs/BooxNotesSettingsDialog.kt`).
  - The other budget check, `canIndexNewPage`, always returns true, so leave it alone.
- **Change:**
  - Add `AiProblem.MONTHLY_LIMIT`. Message: "Boox Day reads up to 1000 pages a month with AI. The rest will be read next month." Short text: "monthly limit of 1000 AI page reads reached". Update every exhaustive `when` on `AiProblem`.
  - Call `IndexingStatus.onWaiting(MONTHLY_LIMIT)` on that path.
- **Tests:** extend `api/ai/AiProblemTest.kt`. If `processPageWithResult` can be reached without Android, add a pipeline test for the limit path. Otherwise say it wasn't covered.
- **Review (1.0.37):** confirmed. The card above the status line already shows "AI page reads this month: N of 1000", but the status line still says "N items pending • Processing during idle time". There is no exhaustive `when` on `AiProblem` in the app. `AiProblemTest` requires `indexingWaits = true` for every problem except `MODEL_FAILED`, so `MONTHLY_LIMIT` needs `indexingWaits = true`. The short text also appears in the daily notes (`export/DailyNotes.kt`). Runtime cost: none.
  - Not reported in these words. The closest reports (Mar and Jun 2026, Tab Ultra C Pro, old builds) are a library of 500+ notes whose indexing "never completes". At 1000 reads a month, a library that size takes months even after this fix. Whether heavy users may raise the limit is the owner's call; this step only makes the wait visible.
- **Done (26 Oct, texts approved by the owner):**
  - `AiProblem.MONTHLY_LIMIT` has the message "Boox Day reads up to 1000 pages a month with AI. The rest are read next month." and the short text "monthly limit of 1000 AI page reads reached". The number comes from `OcrQuotaManager.MONTHLY_AI_READ_LIMIT`; `indexingWaits = true`.
  - `UnifiedOcrPipeline` calls `IndexingStatus.onWaiting(MONTHLY_LIMIT)` on that path.
  - The Boox Notes status line becomes "Waiting: monthly limit of 1000 AI page reads reached • N items will be read later", and the daily note says the same.
  - Test: `AiProblemTest` (new case). The pipeline path itself isn't unit-tested: `UnifiedOcrPipeline` needs Android repositories and the quota manager.

### 1.4 Say where an exported PDF was saved
- **Cause:** `io/ExportEngine.kt` (`saveInternal`) and `io/CompactExportEngine.kt` return only `"Saved $name"`.
- **Change:** return `"Saved $name to <folder>"`, using the existing `export/DailyNoteWriter.folderLabel` style (for example "Documents/BooxDay/…"). Check that the snackbar (`ui/SnackBar.kt`) fits two lines on the Go 6.
- **Tests:** a small JUnit test for the label function, if it's pure. Otherwise check by hand on a tablet.
- **Review (1.0.37):** confirmed (`saveInternal`, and `savePdfDocument` in two places). `DailyNoteWriter.folderLabel` reads the daily-notes folder setting, so it can't be reused as is. Add a small label function for the export folder: a `file://` path under Documents becomes "Documents/BooxDay/…", and a picked folder shows its document id after ':'. Written to take a path string, it is unit-testable. Runtime cost: none. Reported 13 Dec: an exported memo, and no idea where the PDF went.

### 1.5 Tips that point to the wrong menu
- `tips/TipsRepository.kt`: the backup tip says "Settings > Advanced", but the menu item is **Data & Backup** (`ui/calendar/SettingsMenu.kt`), card "Google Drive".
- Also check the tips that say "Settings > Basic". There is no such tab; the menu item is "Preferences". For each tip, find where the setting really is and correct the path.
- **Tests:** none exist. Show the changed strings in the commit.
- **Review (1.0.37):** confirmed, and wider than listed. Nobody reported it; found in the code.
  - Where things really are: week numbers and first day of week → **Calendar & Sync** (`CalendarSettingsDialog`); date format, time format and Show Tips → **Preferences**; backup → **Data & Backup**; AI preferences → **AI Settings** → "Configure AI Preferences".
  - "Customize gestures in Settings > Gestures": no dialog has a gesture setting, so remove this tip.
  - The same kind of wrong path outside the tips: `ui/dialogs/HelpDialog.kt` ("Settings > Canvas Settings", "Settings > OCR & Search", "Settings > Sync > Unlink Account", "Settings > Backup") and `ui/dialogs/WelcomePopup.kt` ("Settings → AI Assistant"). Fix them in the same step; text only.
  - Runtime cost: none.

### 1.6 The task date picker follows the week start setting
- **Cause:** `editor/ui/TaskDatePickerContent.kt` hard-codes the Sunday header, and `generateCalendarDays` uses `DAY_OF_WEEK` raw.
- **Change:** read `GlobalAppSettings.current.firstDayOfWeek`. Rotate the header and offset the first day as `ui/calendar/CalendarView.kt` does (`(raw + 6) % 7` for Monday).
- **Tests:** add `editor/ui/TaskDatePickerDaysTest.kt` for `generateCalendarDays`. Use a known month with a Sunday start and a Monday start, including a month that starts on a Sunday.
- **Review (1.0.37):** confirmed. The one change covers all four places that use the picker: `TaskDueDateDialog`, `TaskOperationDialog`, `BooxNoteEventPicker` and the popup in the same file. 6 leading days plus 31 still fit in the 42 cells. Runtime cost: none. Found in the code; the one week-start request on Reddit was for Saturday (3.7).
- **Done (26 Oct):**
  - `generateCalendarDays` takes the `FirstDayOfWeek` setting (leading days = `(dayOfWeek − start + 7) % 7`). The header comes from `weekdayInitials(start)`. The picker reads `GlobalAppSettings.current.firstDayOfWeek`.
  - Taking the enum means a Saturday value (3.7) needs only a new `when` branch.
  - Test: `TaskDatePickerDaysTest` (4: Sunday-start and Monday-start months, all 12 months × both settings fill 42 consecutive days from the right weekday, header).
  - **Not in this step:** the event date picker in `AddEditEventDialog.kt` (~1617–1633) is Sunday-first too (its own header and `dayOfWeek.value % 7`). Reported to the owner.

### 1.7 The end-time list opens at the chosen hour
- **Cause:** `ui/dialogs/AddEditEventDialog.kt` `TimeSelector` (hour `DropdownMenu`) always lists from 00 at the top.
- **Change:** pass `scrollState = rememberScrollState()` to the Material 1 `DropdownMenu` (Compose 1.9.5 has the parameter). When it opens, scroll to the selected hour: the index is `hour` in 24-hour mode, otherwise display hour − 1, at about 48 dp per item. The end value already follows the start, so the end list opens just after the start.
- **Tests:** UI only. Check by hand.
- **Review (1.0.37):** confirmed. The Material 1.9.5 `DropdownMenu` does take `scrollState` (checked in the cached library). Create the state for each opening with its first value already at the selected hour (`remember(showHourDropdown) { ScrollState(initialPx) }`), so the list opens there with no scroll animation and one e-ink refresh. Offset: `DropdownMenuItem` is at least 48 dp tall, plus the menu's 8 dp top padding; convert with `LocalDensity` (the app sets its own density). In 12-hour mode, 12 is the last item. The end follows the start: confirmed in the start-time handlers. Runtime cost: none. Reported 22 Jan (Tab X C): after an 11:00 start, the end list starts at 00 again.
- **Done (26 Oct):**
  - `TimeSelector`'s hour `DropdownMenu` gets `scrollState = remember(showHourDropdown) { ScrollState(index × 48 dp) }`. The index is the hour in 24-hour mode, or the display hour − 1.
  - In Material 1 the menu's 8 dp vertical padding is outside its scrolling column, so the offset needs no padding term.
  - Compiles. UI only: not checked on a tablet yet. The minute list is unchanged.

### 1.8 Crash report says the firmware and the pen mode
- **Why:** EMR tablets are the untested group, so reports should show the pen mode and firmware without needing adb.
- **Change:**
  - In `diagnostics/CrashReporter.kt` (`buildReport`, near the `Device:` line), add `Firmware: ${Build.DISPLAY}` and `Pen: <backend>`.
  - `compat/OnyxCompat.createTouchHelper` stores the backend it chose ("SurfaceFlinger" or "Android pen / EPD", "not started" by default) in a field.
  - Keep these lines near the top so the shortened GitHub issue body keeps them.
- **Tests:** extend `diagnostics/CrashReporterTest.kt`.
- **Review (1.0.37):** confirmed. `createTouchHelper` only logs the backend once at start, so the line is usually gone from the report's last 150 log lines. The backend is "Android pen / EPD" whenever `DeviceFeatureUtil.hasStylus()` is false, which is exactly the case to spot on unknown pens. Runtime cost: none (one field set when the TouchHelper is created).
  - **Privacy (report only, no change here):** the report's log lines can contain the account email (18 log calls print one, for example "Google sign-in successful: <email>"). "Report on GitHub" puts up to 4,000 characters of the report into a public issue form. Before asking testers for reports, decide whether to mask emails in the report or warn in the popup.

### 1.9 Manifest: microphone not required
- Add `<uses-feature android:name="android.hardware.microphone" android:required="false"/>` to `AndroidManifest.xml`. `RECORD_AUDIO` otherwise implies a microphone, which is one reason Play once marked BOOX tablets "not compatible".
- Build only; there is no test.
- **Review (1.0.37): dropped.** It changes nothing today: Android doesn't check `uses-feature` when installing, only app stores filter on it, and BooxDay is installed from GitHub. The stated reason doesn't hold either. Every target tablet has a microphone, and so do both tablets reported as "not compatible" on Play (Tab Ultra C Pro, Note Air 5C), so their filtering had another cause. `RECORD_AUDIO` is the only permission in the manifest that implies hardware. Revisit only if a store listing is planned.

**Part 1 is done when:** every step is committed, its focused tests pass, and `assembleDebug` builds. For the manual checks (1.4, 1.5, 1.7), list them for the owner and, with the owner's OK, do them on the Go 10.3 and the Go 6.

---

## Part 2: confirm the cause on a tablet, then fix

**Rule for every step in Part 2:**
1. Do the check.
2. Report what logcat showed.
3. Stop for the owner's OK before the fix. These steps touch protected fixes or their neighbours.

Log tags: `DrawCanvas`, `einkHelper`, `Router`, `PageView`, `PageDataManager`, `OnyxCompat`, `DrawCanvas.drawingLock`.

Review (1.0.37): `PERF_LOGS` is `false` in every build (`Logging.kt`), so lines behind it never print (`CACHE_STORE`, `TOUCH_TRACE`, `PERSIST: Persisting SNAPSHOT`, `PERF: setupSurface took`). Where a check needs them, add a temporary log line in a local debug build and don't commit it. Per the owner's rule, a Part 2 fix is proposed only after its check shows the cause, and it must add no work per stroke.

### 2.1 White screen after the start screens (GitHub issue #1)
- **Likely cause:** opening the pen mode (raw drawing) stops the rest of the screen from refreshing (`editor/DrawCanvas.kt`, the comment on `flushUIUpdates`).
  - It opens at the end of startup (`tryActivateDeferredStartupRawDrawing` / `updateActiveSurface` → `einkHelper.setupSurface`). That waits for the loading flags, but not for the frame that draws the loaded data.
  - Only a tap (`forceUnfreeze` / `flushUIUpdates`) lets the screen refresh again.
  - On a slower or older tablet, the panel keeps the white frame.
  - The DPI clue fits: `configChanges` lacks density, so a DPI change rebuilds the screen and closes the pen mode, and the start screen shows again.
- **Review (1.0.37):** 1.0.37 changed none of this: the startup open, `forceUnfreeze` / `flushUIUpdates`, `notifyStartupCanvasReady`, `einkHelper.setupSurface` and the pen backend choice are as before.
  - **But 1.0.33 already fixed issue #1's symptom** (`a624882`, 19 Oct; not in 1.0.31 or 1.0.32). Before it, the Router showed a plain white hand-off cover while raw drawing opened. Now it removes the splash, waits two frames, logs `STARTUP_FRAME: splash removed before native drawing activation`, and only then lets raw drawing open (`notifySplashDismissed()`, then the 500 ms settle). Its comment says a white cover "can remain visible even while the pen works (#1)". A frozen white cover over a canvas that still takes ink fits "white screen, the pen draws only in the lower part": the canvas is the lower part of the screen.
  - The plan's remaining theory (data drawn after raw drawing opens never reaches the panel) predicts a different symptom: the main screen with missing or stale data, not a white screen. The month badges (`LaunchedEffect(currentYearMonth)`) arrive without a flush; only content-change badge updates call `flushUIUpdates()`.
  - The DPI clue still holds: MainActivity has `configChanges="orientation|screenSize"`, and a recreated activity starts the Router again with `showSplash = true`, which is why the start screens come back.
  - **Who reported it:** 4 users, Dec 2025 – 16 Oct 2026, all on Tab models (Tab Ultra, Tab Mini C, Tab Ultra C), which shipped with Android 11; none of them is a target tablet. Issue #1 was filed on 16 Oct, before 1.0.33 came out on 19 Oct, so on 1.0.31 or 1.0.32, and has no comments. Its own words fit the white cover: two info screens, then all white; changing the DPI brings the start screens back; and the pen draws in the lower-left area, which the reporter takes for the writing area. The Dec 2025 Tab Ultra report shows the same picture: upper half white, lower half grey, and tapping made event fields appear. Among target tablets, only Go 6 Gen II units not yet updated still run Android 11.
  - **So do Check B first:** ask the reporter to update to 1.0.37 and say whether it still happens (3.4). It costs nothing and decides the rest. Do Check A only if the white screen remains on 1.0.37, or the owner sees stale data after start. Per the owner's rule, write no startup code for this unless a target tablet shows it.
- **Check A (owner's tablets, debug build, not committed):**
  1. In a local debug build, delay the first data draw of MainScreen by about 1.5 s, so the pen mode opens before the data is on screen.
  2. Install with the owner's OK. Capture logcat from start-up.
  3. If the Go 10.3 or Go 6 then shows a stale or white screen until a tap, the mechanism is confirmed.
  4. Expected log order: `PERF: Splash pre-loading COMPLETE` → `STARTUP_FRAME: splash removed…` → `SPLASH_FIX[Tasks]: activating deferred startup raw drawing` → `Setup editable surface completed - raw drawing ENABLED`, and the white screen starts there.
  5. Revert the delay.
  - Review (1.0.37): the log lines exist as written. The canvas name is `[Memos]` or `[Tasks]`, whichever tab the app starts on. Also watch for `PERF: Startup canvas handoff timed out after …ms` (Router).
- **Check B (the reporter's tablet):** see 3.4. Ask for the crash/log text, or for logcat if they have adb.
- **Fix, once confirmed:** a one-time screen refresh after the first open.
  - In the companion `notifyStartupCanvasReady()` (both first-open paths end there), act only on the first call.
  - About 300 ms later, unless `isStylusRecentlyActive()`, do the same raw-drawing off/on toggle as `forceUnfreeze()`.
  - It doesn't close or reopen the session, so it takes no new framebuffer capture and the sparkle fix is untouched.
  - Log `UNFREEZE: startup one-time refresh`.
  - Review (1.0.37): runtime cost is one 50 ms toggle per app start, skipped while the pen is active. Only with evidence from Check A or B.
- **Not doing:** adding `density` to `configChanges`. CLAUDE.md says density handling in MainActivity must stay minimal (re-applying density broke Memos day switching).
- **If Check B points to the pen backend instead** (`Creating BOOX pen backend: SurfaceFlinger` on firmware 4.2): build a test APK that uses the SDK default `TouchHelper.create(view, callback)` (the behaviour before `a624882`) for the reporter to try. Don't ship it until it's proven.
  - Review (1.0.37): the pen SDK upgrade (onyxsdk-pen 1.4.12.1 → 1.5.8) and the forced backend arrived in 1.0.33 too, so a report from 1.0.31 or 1.0.32 can't be caused by them. Ask which version the reporter runs. Where `DeviceFeatureUtil.hasStylus()` is false the app uses the Android pen backend, and `0c17cfc` keeps the old immediate post there.

### 2.2 Writing stays slow until a restart
- **Likely cause:** the `forceUpdate.collect` observer in DrawCanvas (protected "sparkle" fix) does close → draw → `delay(100)` → open, with no `finally`. If `cancelObservers()` runs during the 100 ms (day or page switch via `updateState`), the pen session stays closed.
- **Review (1.0.37): this cause is most likely wrong.**
  - True: the cycle has no `finally`, and on a day switch `EditorSurface` calls `updateState()` (when `lastUpdatedPageId != page.currentPageId`, a frame or so after `changePage`), which cancels it. That can land in the 100 ms.
  - But `cancelObservers()` resets `lastKnownMemoMode`, so the new memoModeChanged collector's first value takes the "observer re-registration" branch. When `rawDrawingOpened` is false it calls `reactivateRawDrawing(clearGhosts = true)` (log `FLICK_FIX: Raw drawing not opened yet - opening without close cycle`). That waits for the raw-drawing lock, which the cancelled cycle releases, and reopens with the right limit rect.
  - A surface that isn't ready yet, or an open overlay, delays the reopen but doesn't stop it: `updateActiveSurface` reopens once the surface is ready or the overlay closes. It stays closed only if `state.isDrawing` is false at that moment, because `updateIsDrawing` only re-enables a closed session.
  - And a closed session gives **no ink at all**, which doesn't fit "writing stays slow".
  - **Who reported it:** 2 users, both on the Dec 2025 beta. One (Note Air 3C and a 10.3-inch BOOX) found writing memos and ticking tasks laggy. One (Note Air 5C or Palma 2 Pro, not said which) found that writing "at random" became unusable until the app restarted, with no trigger given. Nobody has reported it since. A Feb 2026 tester called writing practically lag-free.
  - So the NonCancellable fix is dropped: it would change a protected fix without proof.
  - **What 1.0.37 changed:** `0c17cfc` removed the e-ink commit after every stroke that held back the next stroke's ink by 100–300 ms (worst 557 ms). That is the measured cause of late ink. `f4ee7ce` removed the page-image save twice a second while writing (about 200 ms of CPU each). Both were measured on the Go 10.3; slower tablets paid more.
- **Check (Go 10.3, the owner's OK to install):**
  1. Tick a task, or move one, which does a full redraw.
  2. Within 100 ms, switch the day or the Tasks page. Repeat about 20 times.
  3. In logcat, look for `SPARKLE_FIX: forceUpdate full page - close/open cycle…` followed by `RAW_OWNER: … released after forceUpdate-full`, with **no** `LIMIT_RECT_FIX[Tasks]: forceUpdate setting limitRect=` or `PAGE_RESET: reopened page=` between them.
  4. Then write and see whether ink is late.
- **Review (1.0.37): new check, symptom first** (1.0.37 debug build, owner's OK):
  1. Measure pen-down to ink the way `0c17cfc` did (memory "measuring-pen-latency-on-boox"), right after start.
  2. Do about 50 day switches and 30 task ticks or moves, some within 100 ms of each other.
  3. Measure again. If nothing changed, lasting lag doesn't reproduce on 1.0.37: ask testers (checklist items 2 and 4) instead of changing code.
  4. Only if latency grew, read the log: the sequence in step 3 above, **and no** `FLICK_FIX: Raw drawing not opened yet` or `RAW_OWNER: … acquired for reactivate-…` after it, plus any `TAB_DEBUG: SKIPPED reactivateRawDrawing!`.
- **Fix, once confirmed and with the owner's OK (protected fix):**
  - Run delay → limit rect → open inside `withContext(NonCancellable)`, keeping the 100 ms order exactly.
  - Before opening, check `surfaceReady && memoModeChanged.value == isMemosCanvas`.
  - Leave everything else in the observer as it is.
  - Review (1.0.37): dropped, see above. If step 4 shows a stuck session, the smaller fix is to reopen in the `isDrawing` case (an `updateIsDrawing` that finds the session closed calls `reactivateRawDrawing`), not to change the protected cycle.
- **Measure before changing anything else:**
  - On a Tasks page and a Memos page with a lot of ink, read the `DrawCanvas.drawingLock` lines (`Waited Nms…`, `Held drawing lock for Nms…`) and `DigitalInkService` `Extraction complete … (Nms)`.
  - Only if a number is clearly high, propose a separate step. Examples: update `PageDataManager.indexStrokes` per stroke instead of rebuilding the whole map, or drop the double copy in MainScreen's `contentChanged` handler.
  - Review (1.0.37): these log lines exist as written. Work done per stroke today, all growing with the page's stroke count: `indexStrokes` rebuilds the page's whole id map on the main thread (it runs on PageView's scope); the Tasks cross-out collector copies the page's strokes and builds an id set on every `contentChanged` (Default dispatcher); the Memos "more below" check (`pageContentBottom`) runs on every `contentChanged`. Change them only with numbers.

### 2.3 A Tasks page sometimes isn't drawn after switching pages
- **Likely causes:**
  1. `editor/PageView.persistBitmapDebounced(oldId)` caches the bitmap under `currentPageId`, which is already the new page, not the `pageId` argument.
  2. The single `forceUpdate(null)` from `loadPage` is dropped if it arrives while `!surfaceReady`. The early return in the observer just renders and doesn't blit.
  3. `PageDataManager.getCachedBitmapExact` ignores `generation` (`pageGenerations`, which is barely used).
- **Review (1.0.37): causes corrected.** `f4ee7ce` changed the saving side only.
  1. **Corrected.** `changePage` switches `currentPageId` and `windowedBitmap` first, then the launched `persistBitmapDebounced(oldId)` caches `windowedBitmap` under `currentPageId`. That is the **new** page's bitmap under the new id, before its strokes are drawn: only the background or the saved preview. The old page's bitmap is not cached by this call. If the user leaves the new page before its load redraws it, the cache keeps that undrawn image (`loadInitialBitmap` also drops the disk preview when the page has changed: `currentPageId != pageIdSnapshot`). The next visit shows it until the load's redraw is posted.
  2. **Wrong.** A forceUpdate while `!surfaceReady` isn't dropped. The observer draws the strokes into the bitmap first (`drawAreaScreenCoordinates`, before any check); only the post waits. Both places that set `surfaceReady = true` (in `updateActiveSurface`) post the bitmap right after. All 40-odd emitters send `null` (a full redraw), so a buffered full redraw is never replaced by a partial one either.
  3. **Unchanged:** leave it. Bitmaps are cached by reference, so the generation isn't needed.
  - From `f4ee7ce`: a page's saved image is never behind the page any more (readers save a pending image first), and a page waiting for its save stays cached. A page loaded from disk shows its latest strokes.
  - **Who reported it:** 1 user, 17 Mar 2026, on a Tab Ultra C Pro with a closed-test build: after filling a few task pages, switching between them sometimes didn't paint the page properly, and going to another day and back fixed it.
  - **What cause (1) can explain:** every page switch already ends with one full redraw (`loadPage` → `forceUpdate.emit(null)`; a switch doesn't cancel the load, only `cleanJob` on disposal does). So cause (1) gives at most a short blank flash when going back to a page whose load hadn't finished, not a page that stays wrong. Nothing in the 1.0.37 code explains a page that stays wrong. The report is from a March closed-test build on a non-target tablet, and the page-image code has changed since (`f4ee7ce`, and the Oct 2026 "PREVIEW OVERWRITE FIX" in `changePage`).
- **Check (Go 10.3):**
  1. Fill 4 or more task pages and switch pages quickly 30 times.
  2. Log `PageView` / `PageDataManager` (`CACHE_EVICT`) and `refreshUiSuspend: surface not ready`.
  3. Note the steps that leave the page wrong.
  - Review (1.0.37): `CACHE_EVICT` and `refreshUiSuspend: surface not ready` print (`log.d`). `CACHE_STORE` doesn't (behind `PERF_LOGS`), so add a temporary local log in `changePage` (old id, new id, cache hit or not) and in `persistBitmapDebounced` (the id it caches under, and whether the page's load had finished). Also log in the forceUpdate observer (page id, whether it drew and posted), to see whether a switch's full redraw is ever missed.
- **Fix, once confirmed:**
  - (1) Snapshot the old page's bitmap before `changePage` switches `currentPageId`, and cache it under the old id.
  - (2) When the observer returns early because the surface isn't ready, set a "redraw pending" flag, and blit in `updateActiveSurface` once the surface is ready.
  - Leave (3) unless the check shows it.
  - These are next to protected fixes (PageDataManager, DrawCanvas observers): the owner's OK first.
  - Review (1.0.37): only if the check shows a wrong page or the blank flash; otherwise no code. Then (1), **by reference, never a copy**: before switching, `cacheBitmapIfSynced(oldId)` with the old bitmap, only if that page's load finished. A bitmap copy would be about 10 MB on the main thread at every page switch. In the launched coroutine, only send the save for `oldId`; don't cache under `currentPageId`. The old bitmap object is never drawn into again (the new page gets its own bitmap), so keeping it is safe. Runtime cost: one map entry per switch. (2) is dropped: `updateActiveSurface` already posts.

### 2.4 Note Air 6C: the pen doesn't write, then a crash at start (Review (1.0.37): new)
- **Report** (one user, r/Onyx_Boox thread of 16 Oct). On 1.0.31/1.0.32 the pen didn't write in BooxDay but worked in BOOX's own apps (16 Oct). The developer pointed them to 1.0.33's new pen SDK on 19 Oct. On 20 Oct they reported that 1.0.33 crashes at start, and said they would try a Note Air 5C next; no reply was archived after that. This is the only report on a target tablet since the open-source release: Android 16, EMR pen, neither of which the owner's tablets cover.
- **Ruled out from here:** native libraries on Android's 16 KB memory pages. In the Gradle cache, every arm64 library that 1.0.37 packages has 16 KB-aligned segments: onyxsdk-pen 1.5.8, onyxsdk-pennative 1.0.5 (`libneopen_jni.so`) and ML Kit digital ink 19.0.0. The one 4 KB library, `libneo_pen.so`, has been left out of the APK since 1.0.33. This was checked on the cached libraries, not on the APK itself.
- **Check (no code):** ask the reporter to install 1.0.37 (3.4).
  - If it still crashes, reopen the app → "Copy Report" and send the text. 1.0.37 is the first release that keeps a crash report and offers it at the next start (`9e11e2d`); 1.0.33 had none.
  - If no report appears, the crash is in native code, which the Java crash handler can't catch: ask for `adb logcat -b crash -d` if they have adb.
  - Also ask whether writing works on their Note Air 5C.
- **Fix:** none until the report shows the cause. A fix must not change the pen path on the tablets that work.

---

## Part 3: owner's decisions and tasks (no code until decided)

1. **Keyboard covers text fields.** Edge-to-edge is on (`setDecorFitsSystemWindows(false)`), and no code moves content up for the keyboard. Proposal: add `imePadding()` one dialog at a time. Start with the task text editor, which is the one the report was about, and show the owner before the next one (CLAUDE.md: UI changes one at a time).
   - Review (1.0.37): confirmed (no `imePadding` or `windowSoftInputMode` anywhere). The report (14 Dec) was about typing in the task box: the text popup sits under the keyboard. The task text editor is `editor/ui/TypeTaskDialog.kt`, a focusable Compose `Popup`, which is a window of its own. Compose UI 1.9.5's `PopupLayout` sets no keyboard mode and handles no insets, so `imePadding()` inside it may do nothing. Try it on that one dialog on a tablet first; if it has no effect, show the dialog in the upper half of the screen while typing.
   - Recommendation (26 Oct): owner tries it on the Go 6 first, which takes 30 seconds: open a task, type with the on-screen keyboard. Fix only that one dialog, and only if the keyboard covers the box. The report is from the Dec 2025 beta. Runtime cost: none (only while typing).
2. **Google Cloud console.**
   - Check that the Android OAuth client for `com.boox.day` has the **SHA-1 of the release key**. The executor can print it with `build-tools\36.0.0\apksigner.bat verify --print-certs booxday-release\booxday-1.0.36.apk`.
   - Check the consent screen status. Calendar is a sensitive scope: an unverified app shows a warning, and in testing mode only listed testers can sign in.
   - 26 Oct: the release key's SHA-1, printed from `booxday-1.0.37.apk`, is `9D:FA:F2:D0:9F:63:C7:D3:09:A5:90:6C:A0:28:55:90:E7:DE:BC:2F` (SHA-256 `b037422f…bc0e`, as in CLAUDE.md). Debug builds use the same key. If Google sign-in works on the owner's tablet, this SHA-1 is already registered, and only the consent screen is left to check. The code 10 report (21 Jan) was a Play install, which Play signs with its own key.
   - Consent screen, what to look at (Google Cloud console → Google Auth Platform / OAuth consent screen):
     - **Publishing status:** "Testing" means only the listed test users can sign in. It needs to be "In production".
     - **Verification status:** if unverified, users see a "Google hasn't verified this app" warning, and Google limits an unverified app to about 100 users.
3. **EMR testers.** Every crash report from January was on an EMR tablet: Note Air 3C/4C, Tab X C, Go 10.3 Gen I, Note Max. Both test tablets use USI pens.
   - Owner to post on r/Onyx_Boox asking for testers with a Note Air 4C/5C/6C, Go 10.3 Gen I, Go 7 Gen II or Note Max.
   - Hand them the checklist below.
   - Review (1.0.37): **corrected.** The crashes on writing (22 Jan – 5 Feb, 6 users, closed test) were on EMR tablets (Note Air 3C and 4C, Go 10.3 Gen I, Note Max) **and** on active-pen tablets: the Go 7 (InkSense) and the Tab X C (InkSpire, an active pen with wireless charging). So they weren't EMR-specific. All were on the old pen SDK (onyxsdk-pen 1.4.12.1); 1.0.33 moved to 1.5.8 (`a624882`), so their causes may be gone.
   - What the new SDK hasn't been tried on: EMR pens (the owner's two tablets have active pens) and Android 16 (2.4). The Go 7 has an active pen like the owner's tablets: still useful for the 7-inch layout, but not for EMR. The README says BooxDay was tested on the Note Air 4C, Note Air 5C, Go 7 and Go 6 Gen II; ask the owner which builds that was.
4. **Reply to GitHub issue #1** (owner's OK before posting). Draft: thanks; ask for the BOOX model and firmware; ask whether a tap on the calendar makes the screen appear; if they have adb, ask for `adb logcat -d > log.txt` right after the white screen.
   - Review (1.0.37): put first: "1.0.33 changed the start-up hand-off for exactly this; please update to 1.0.37 (the app offers it, or download it from Releases) and tell us whether it still happens, and which version you had." Ask the rest only if it does. The issue was filed on 16 Oct, before 1.0.33, and has no comments yet.
   - Same kind of short reply in the 16 Oct Reddit thread (owner posts it): to the Note Air 6C user (2.4), asking for 1.0.37 and the crash report; and to the Tab X user ("not functioning", 17 Oct, no details), asking for 1.0.37 and what they see.
   - Recommendation (26 Oct): post these now. 1.0.37 is out, and the answers decide whether 2.1 and 2.4 need any code. Claude drafts the final texts; the owner posts them.
   - 26 Oct: drafts written in the session; the owner posts them. Re-read before writing them:
     - GitHub #1 (Svartvatten, open, no comments). The reporter changed the DPI in the E-Ink Center while testing.
     - Reddit `ddavtian`: 6C, 1.0.33 crashed at start, 20 Oct.
     - Reddit `2Wheeler_Workshop`: Tab X. The owner's 19 Oct reply already asked about 1.0.33, with no answer since.
   - 1.0.37 doesn't mask emails in the crash report (`CrashReportGate.reportOnGitHub` copies it as is), so the drafts tell users to check for their address before posting.
5. **Optional small feature: About → "Copy recent log"** in release builds. It reuses CrashReporter's own-log capture, so testers without adb can send logs. Recommended before the tester call.
   - Review (1.0.37): runtime cost is none until it is tapped (it runs `logcat -d --pid` once, like `CrashReporter.recentLogLines`). Same privacy note as 1.8: the log can contain the account email and calendar names, so say so next to the button, or mask emails.
   - Recommendation (26 Oct): yes, with email addresses masked (`f…@gmail.com`). Use the same masking for the crash report in 1.8, which answers that open privacy question too. The masking runs only when a report is made, so it costs nothing at runtime.
6. **Go 6 Android version.** CLAUDE.md says Android 13, but reviews say the Go 6 Gen II ships Android 11. Next time it's connected, run `adb -s 6d029feb shell getprop ro.build.version.release`. If it's 11 and has no white screen, the white screen is about timing or firmware, not Android 11.
   - Review (1.0.37): **answered without the tablet** (it wasn't connected). Both statements are true. The Go 6 Gen II shipped with Android 11. Onyx has been rolling out an Android 13 update in batches since 24 Sep 2026 (The eBook Reader, 24 Sep; Notebookcheck). The owner's unit runs 13 (CLAUDE.md), so it has the update. Units not yet updated are the only target tablets on Android 11. The getprop check is still worth one line next time it is connected. The white-screen reports came from Tab models, not from a Go 6 (2.1).
7. **Saturday week start** was asked for on Reddit; today only Sunday and Monday exist. Add it or not.
   - Review (1.0.37): confirmed: `AppSettings.FirstDayOfWeek` has only Sunday and Monday. One user asked (13 Dec) for a choice of Saturday, Sunday or Monday.
   - 26 Oct, size: about 28 uses in 5 files.
     - The enum.
     - The two day-shift formulas in `CalendarView.kt` (:1038–1040, :1065–1067, :1170–1172).
     - `CompactCalendar.kt:77–79`.
     - The radio buttons in `CalendarSettingsDialog.kt:355–361`.
     - The task date picker from 1.6.
   - Safe for saved settings and backups: settings are read with `coerceInputValues = true` (`Kv.kt:146`), so an older version reads "Saturday" as Sunday instead of failing.
   - The executor also checks every other week calculation (grep `DayOfWeek`, for example the weekly summary), so all of them follow the setting.
   - Recommendation: yes, together with 1.6 (same code, same tests). It's cheap, but only one user asked, so skipping it is also reasonable.
8. **Before `targetSdk` 36:** Android 16 (Note Air 6C) ignores `screenOrientation="portrait"` on large screens at targetSdk 36. Plan for it before bumping.
   - Review (1.0.37): correct; the app targets 35 today (`app/build.gradle`).
9. **CalDAV calendars: parked (owner, 26 Oct: "too much work for little benefit").** Nothing to do now. Kept here so the findings aren't lost:
   - **Asked by:** 4 Reddit users since Dec 2025 (CalDAV twice, Nextcloud, WebDAV).
   - **BOOX's own calendar can't be shared.** Checked on the Go 10.3, firmware 4.2 of 21 Apr 2026, read-only. The calendar is part of BOOX Mail (`com.onyx.mail`). It has its own CalDAV client (dav4jvm + ical4j) but keeps the calendars in its own private database (`CalendarModel`, `CalendarEventModel`). It doesn't use Android's shared calendar store and offers no way for other apps to read it. So Boox Day can't read CalDAV accounts set up there.
   - **Route 2 (small):** read Android's shared calendar store. CalDAV users would add their account in DAVx⁵ (free on F-Droid, about $5–6.50 on Google Play). Android expands repeating events itself.
   - **Route 1 (large):** a CalDAV client inside Boox Day, like BOOX's calendar.
   - **If it is picked up:** `ProviderId.fromKey` reads an unknown provider as Google, so an older version restoring a newer backup would mistake the new account for a Google one. Give it a safe fallback first.

---

## Part 4: owner's list (26 Oct): cleared content, dialogs, texts

The owner asked for these on 26 Oct. All were checked read-only against `04c9d8c`; line numbers are from that commit. Each item is marked **Ready** (cause or fault shown in the code), **Check first** (needs the tablet) or **Owner decides**.

Rules for this part:
- Every item here is in a dialog or a menu action. None touches the pen path, the protected raw-drawing fixes, startup or day switching. Runtime cost: none while writing.
- UI changes go one at a time, shown to the owner first (CLAUDE.md). For each dialog: one Go 6 screenshot before and after.
- Texts the user reads (Help, welcome, tips, AI Settings) are drafted first and approved by the owner before they go into code.
- No new dialog framework and no rewrite. Reuse what exists (4.4).

### 4.1 Cleared memos and tasks come back (Ready: (a) and (b) confirmed on the Go 10.3, 26 Oct)

The owner saw deleted content come back after going between the normal view and full screen, or between Memos and Tasks. Two faults in the code explain content or pages coming back; a third is a rare timing gap.

- **(a) "Clear All Memos Today" clears the other pages only in the database.** `EditorControlTower.clearMemoPages` clears every memo page of the day in the database (`DayPageRepository.clearPageContent`), but clears memory only for the page on screen: `if (state.currentPageId in memoPageIds) page.clearAllContent()`.
  - Pages opened earlier in the session stay loaded in `PageDataManager` (`loadPage` keeps up to 20: `reduceCache(20)`). A loaded page is shown from memory and never read again (`getOrStartLoadingJob`: a finished load job is reused). So going to page 2 shows its cleared ink again, until the app restarts.
  - Their saved page images (`previews/full/<pageId>…png`) aren't deleted either. Those images are what search reads for Memos (`ocr/source/MemoBitmapSource`) and what the sleep screen uses (`SleepScreenPublisher:148`).
  - Search: no clear updates `Page.updatedAt`, the field the indexer uses to find changed pages (`UnifiedOcrScheduler.buildMemoQueue`), and no clear deletes search entries. So search likely keeps finding text from cleared pages, including the one on screen (`clearCurrentPage`). This is read from the code; confirm with a search on the tablet.
- **(b) "Clear All Tasks Today" doesn't leave full screen first.** Every other Tasks tool that changes pages is wrapped in `FullScreenState.exitThen` (MainScreen.kt 2597–2976, including Delete Task Page and "Delete tasks…"). This one is not (`ToolbarMenu.kt` ~323 → `controlTower.clearTaskPages()`). In full screen it runs on the full screen pages: `deleteAllTasksForDate` keeps page 1 and deletes the rest. Full screen pages hold about 16 rows, so there are fewer of them, and fewer get deleted. When full screen ends, `TaskPagination.collapse` rebuilds as many pages as the day had before (`minPages = record.pageIds.size`; missing pages are created anew). So the pages the clear should have removed come back, empty.
- **(c) Timing gap (rare).** The clears write to the database directly, not through `PagePersistenceWriter`, the ordered queue that every pen write uses. A pen write still in the queue would land after the clear and put that ink back; it shows at the next load. The queue usually empties within milliseconds, so this needs a busy queue. The writer's own note (A1b) describes the same kind of race for lasso moves.
- **Not found:** full screen itself doesn't bring content back. The Tasks reflow reads the database inside the write queue (`TaskPagination.onWriteQueue`), and the bitmap cache keeps one size per page (`cacheBitmapValidated` drops other sizes), so a resize can't show an old image. `clearAllDayPages` has no caller.
- **Check (Go 10.3), before the fix:**
  1. Memos: write on pages 1, 2 and 3; go back to page 1; ≡ → "Clear All Memos Today"; go to page 2. Expect the old ink (fault (a)). Restart: it's gone. Search for a word that was on page 2.
  2. Tasks: fill 2 task pages; turn on full screen; ≡ → "Clear All Tasks Today"; leave full screen. Expect 2 pages again, one of them empty (fault (b)).
  3. Ask the owner which steps they used, and whether it was Memos or Tasks, in case it was something else.
- **Checked on the Go 10.3 (26 Oct)**
  - **Setup:** 1.0.36 debug build from 25 Oct. Its clear and full-screen code is the same as 1.0.37. Test day 8 Nov 2026. Screenshots in the session's scratchpad (`shots/`).
  - **(1) Memos: confirmed, and search too.**
    - 8 Nov had 2 memo pages with ink (99 and 119 strokes), both indexed for search. The day opened on page 2, so page 2 was loaded. I went to page 1, then ≡ → "Clear All Memos Today" → "Clear All": page 1 went blank.
    - Going to page 2 showed **all its old ink**.
    - Search for "attempt" (a page 2 word) and "latency" (a page 1 word) still found the 8 Nov memo.
    - Database after the clear (copied, integrity ok): both pages have 0 strokes, so the clear was saved. Each page still has its search row (`unified_ocr_text`), and `Page.updatedAt` hasn't changed, so the indexer will never read them again: search keeps finding cleared memos for good.
    - After a restart, page 2 is empty. So the ink came back from memory only, as the code says.
  - **(2) Tasks: confirmed.**
    - Control in the normal view: ">" created task page 2, then ≡ → "Clear All Tasks Today". Log: `Clear task pages: pageCount=2`, `DELETE_ALL: Deleted 1 extra pages`. After a reload: 1/1.
    - Same in full screen: ">" made page 2 again, then full screen showed 1/1 (log: `on pages of 16 rows`). The clear logged `pageCount=1` and deleted no page. Leaving full screen logged `back on pages of 10 rows`, and the day shows **1/2**, still after a reload.
  - **(c)** wasn't tested; it needs a busy write queue. It stays "only if seen".
  - The owner's own steps (item 3) are still worth asking, in case there is a third way.
- **Fix, once (1) and (2) show it:**
  - (a) In `clearMemoPages`, for every cleared page, not only the one on screen: `PageDataManager.clearPageData(pageId)` (strokes, images, text boxes and bitmaps in memory), `invalidatePreviewCache(pageId)`, and delete its saved preview files (as `TaskPagination.afterReflow` does). Mark each cleared page changed (`updatedAt`), or delete its search entries, so search forgets the text. Do the same search step in `clearCurrentPage`. Best done in one small shared function (for example `forgetPageContent(pageIds)`) with a Robolectric test: after a clear, nothing of those pages is left in memory, in previews, or in search.
    - **Done (26 Oct):** `data/ClearedPages.kt`.
      - `forgetLoadedContent` (pages not on screen) cancels a pending image save, then calls `clearPageData` and `invalidatePreviewCache`, and deletes the full image and thumbnail.
      - `forgetSearchText` deletes the pages' MEMO search rows. The indexer then marks the empty page EMPTY with no AI read.
      - It is called from "Clear All Memos Today", "Clear This Page" and the one-page "Delete Page" (which clears).
      - Test: `ClearedPagesTest`.
      - **Checked on the Go 10.3 (26 Oct, debug build of `fe526c8`):**
        - 28 Nov had 2 memo pages with ink. It opened on page 2, then I went to page 1 and cleared all: page 2 is **blank**.
        - Search "hmmm" (a page 2 word) found the memo before the clear and gives "No results" after.
        - Log: both MEMO search rows deleted. 30 s later the indexer logged "Skipping empty memo page" for both, with no AI read.
  - (b) Wrap the "Clear All Tasks Today" confirm in `FullScreenState.exitThen { … }`, like the other Tasks tools. One line in `ToolbarMenu.kt`; `SharedToolbar` is untouched.
    - **Done (26 Oct):** wrapped in `ToolbarMenu.kt`. The menu's scope (`remember { CoroutineScope(Dispatchers.IO) }`) isn't tied to the menu's lifetime, so the clear still runs after full screen closes. Compiles. No JVM test (UI wrapper).
      - **Checked on the Go 10.3 (26 Oct):** 8 Nov had 2 task pages and full screen showed 1/1. "Clear All Tasks Today" there logged, in this order: `back on pages of 10 rows`, `Clear task pages: pageCount=2`, `Deleted 1 extra pages`. The day then shows the normal view at 1/1, still after a day switch.
  - (b2, seen in the check) After "Clear All Tasks Today", even in the normal view, the page counter kept showing "2/2" until a day switch. Nothing reloads MainScreen's task page info after this clear. The "Delete tasks… → all pages" handler does reload it (`getOrCreateTasksForPage` → `currentTaskPageInfo`, MainScreen.kt ~4166–4180). Do the same after this clear.
    - Don't hook it to `TaskRepository.taskChanged` (MainScreen.kt:1686). That flow also fires for ink on task pages, so a reload there would add a database read while writing.
    - Use a one-time signal instead, sent only when a day's task pages are added or removed. MainScreen collects it in a `LaunchedEffect`, which compiles to its own class, so the root lambda doesn't grow.
    - Don't add a new parameter through `SharedToolbar` (close to its compile limit).
    - **Done (26 Oct):**
      - `TaskRepository.dayCleared` is sent once by `EditorControlTower.clearTaskPages`.
      - MainScreen collects it in a child coroutine of the existing `taskChanged` `LaunchedEffect`, so the root lambda doesn't grow. For the shown day it calls `loadTasksPage(date, 0)` plus `TasksCanvasRefresh.request()`, like the "Delete tasks… → all pages" handler.
      - It loads page 1, not the old index, because `getOrCreateTasksForPage` creates a page past the end.
      - Compiles.
      - **Checked on the Go 10.3 (26 Oct):** right after that clear the counter shows 1/1, and the tab no longer says "Tasks · 2". No day switch needed.
  - (c) Only if the check shows it: send the database part of the clears through `PagePersistenceWriter`.
  - `PageDataManager` is in the protected list (`protectedPages`). `clearPageData` empties a page's lists in place and doesn't evict it, so page protection is unchanged. Say so to the owner before the change.
  - Runtime cost: none. It runs only when the user clears.

### 4.2 Texts that say handwriting never leaves the tablet (Ready, owner approves the wording)

These are wrong when AI is on. Memo pages are read by AI on the user's own OpenRouter key: the page image is sent (`ocr/OpenRouterOcrService.kt`, `UnifiedOcrPipeline`), and so are Boox Notes pages. Most important text fix in this part, because it is about privacy.
- `tips/TipsRepository.kt:386`: "All note analysis happens on your device. Your handwriting never leaves your tablet."
- `ui/dialogs/HelpDialog.kt:884`: "…Your handwritten notes never leave your tablet. Only the initial OCR uses Google Cloud Vision…" (Cloud Vision is gone).
- `ui/dialogs/HelpDialog.kt:830`: "All AI features use free models via OpenRouter. Your data stays on your device…" (a key is needed; free models only while the account has no credit).
- `ui/dialogs/AiSettingsDialog.kt:157`: "Semantic insights (keywords, patterns) are sent to AI providers. Your full notes stay on your device."
- Proposed meaning, one wording everywhere: "With AI on, page images and text you choose are sent to OpenRouter, on your own account, to be read or answered. Nothing is sent with AI off." The About privacy text (`strings.xml:39`) already says this correctly; check it against this wording.

### 4.3 Boox Notes: a close X in every view (Ready)

- **Confirmed:** the X is only in the list view. `BooxNotesDialog.kt:555`: the header with the X (`BooxNotesHeader`, X at 761–773, 44 dp) is drawn only in the `else` branch. The other views have no X:
  - Note reader (`BooxNoteDetail.kt:420`): back arrow only, so closing takes two taps.
  - Link suggestions (`BooxNotesSuggestions.kt:97`): back arrow only. In its "Blocked" state the bottom button says "Close" but goes back (`onBack`, `BooxNotesSuggestions.kt:445`).
- The X never scrolls away: in every view the header sits outside the scrolling list.
- **Fix:** add the same X (44 dp, `Icons.Default.Close`, calls `onDismiss`) at the right end of the reader and suggestions headers. Keep the back arrows: back goes one step, X closes. Rename the "Blocked" button to "Back" (it goes back), or make it close; owner picks.
  - **Decided (owner, 26 Oct):** rename it to **"Back"** and keep what it does. Once the header has an X, the X closes and the bottom button goes back one step, as everywhere else in the dialog. "Close" that only goes back is the one mismatch.
- Leave the event picker and the bulk-link confirm as they are. They are small confirm popups with a "Cancel" button, which is the rulebook's escape (`docs/DIALOG_RULEBOOK.md`); adding an X there would make two exits.
- **Go 6 check:** the list header holds the title, the Day/All switch, "Checking for new notes…", the date and the X, and the title has no weight. Check that the X isn't squeezed at 572 dp. If it is, let the status text shrink, not the X.
- Frame size is fine: it fills the screen minus fixed insets (`EInkTheme.BooxNotes`, width 0.97). Only the event picker has a fixed cap (560 dp, `pickerMaxHeight`); it fits the Go 6 (772 dp).

### 4.4 Showing that more is below (Ready; shared look decided 26 Oct)

- **What exists:** `ui/components/ScrollableWithIndicator.kt`: a "Scroll for more" bar (`ScrollMoreIndicator`) that shows only while `canScrollForward`, so it disappears at the end. No animation, no gradient. Already used by AI Settings, AI Preferences, Help (content side), Preferences, About, Data & Backup, Calendar settings, the sync error dialog and the ≡ menu. Lists (`LazyColumn`) use the same bar by hand in `SearchDialog.kt:314–364` and `ModelPickerPopup.kt:267–305`.
- **Where it's missing:** Boox Notes day list and all-notes list (`BooxNotesLists.kt:185`, `:326`), link suggestions (`BooxNotesSuggestions.kt:222`), the reader's text page (`BooxNoteDetail.kt:329`), the event picker (`BooxNoteEventPicker.kt:118`), the date menu (`BooxNotesDialog.kt:839`), Boox Notes settings (`BooxNotesSettingsDialog.kt:178`), the Help topic list, the full-screen welcome (`WelcomeView`), and the update popup (`AppUpdatePopup.kt:98`).
- **Fix:** add the same bar to each, using the existing pieces. Add one small overload for lists (`LazyListState.canScrollForward`), so the two hand-made copies aren't repeated a third time. No new kinds of hint: no scrollbars, no chevrons, no half-visible rows added on purpose.
- **Why AI Settings users still miss it:** the bar exists there, but it is small and grey: `offWhite` behind `darkGray` text with a 16 dp arrow. The e-ink guide keeps meaning in black and white because grey dithers. **Owner decides:** make the shared bar black text on white with a thin black line above it. That one change reaches every dialog listed above. Show it on the Go 6 first.
  - **Decided (owner, 26 Oct):** yes. A white bar with a 1 dp black line on top, a black down arrow (20 dp, up from 16) and the black text "Scroll for more". Keep its height (about 32 dp) so it covers no more of the content than today. It still disappears at the end of the list.
- Runtime cost: none. `derivedStateOf` recomposes only the bar, and only when "more below" changes; the dialogs are open while it runs.
- Help: when the topic changes, the content may keep the old scroll position (one `ScrollState` for all topics). Reset it per topic, so each topic opens at its top. Inferred from the code; check on the tablet.

### 4.5 First-start and update screens on small tablets (Check first on the Go 6, then Ready)

Heights below are estimates from the code, not measured. Take a Go 6 screenshot of each first.
- **WelcomeView** (full screen, first start): one scrolling column with "Get Started" at the very end (`WelcomeView.kt:88–92`). Estimated about 1,000 dp, so on the Go 6 (772 dp) the button is below the edge, with no hint. **Fix:** keep "Get Started" fixed below the scrolling part (scroll area `weight(1f)`, button under it), and add the 4.4 bar. Shorter text (4.6) helps too.
- **WelcomePopup** (shown once, after WelcomeView): fixed `width(380.dp)` with no height cap and no scroll (`WelcomePopup.kt:69`). Estimated about 600 dp, so it likely fits the Go 6. **Fix:** use the token width (`EInkTheme.PopupOverlay.width`), and add a height cap with scrolling as a safety net, keeping the button fixed.
- **Update popup:** fits. Capped at 0.82 of the screen height, with the title and buttons fixed (`AppUpdatePopup.kt`, `EInkTheme.kt:717–720`). Only the 4.4 bar is missing.
- **Crash report popup:** three buttons in one row on a 460 dp popup ("Close", "Copy Report", "Report on GitHub"; `CrashReportPopup.kt:88–107`). Check on the Go 6 that no label wraps.
- **Tips:** they appear as a snackbar, not a dialog. Tips are on by default, every hour (`AppSettings.kt:148`), and don't check whether the welcome popup is still open (`TipsService.kt:137–153`). Small fix: no tip while the welcome popup is up.

### 4.6 What the start screens and the update popup say (Ready to draft; owner approves)

- **WelcomeView:**
  - Says "you'll be asked to grant a few permissions" (`WelcomeView.kt:479–504`), but nothing asks: `requestPermissions` has no caller.
  - Says "Semantic search finds notes by meaning" (`:410`). Search matches words and word starts, with synonyms (`search/SearchText.ftsPrefixQuery`, `SearchDialog.kt:76`). The same claim is in `TipsRepository.kt:401` and `HelpDialog.kt:856`.
- **WelcomePopup:** the "Settings → AI Assistant" path is already in 1.5. Two smaller things:
  - It writes "Boox Day", where the welcome title and the start screen write "BooxDay". **Owner decides** one spelling.
  - **Decided (owner, 26 Oct): "Boox Day"** in every text the user sees. Today both spellings are used: "Boox Day" in about 44 code strings, and "BooxDay" in about 28 code strings and in `strings.xml`:
    - the home-screen name, `app_name`;
    - the welcome title, `welcome_view_title`;
    - the About texts.
  - Change all of the visible ones, the home-screen name included.
  - Don't change the names users never see, or that other things depend on:
    - the package `com.boox.day`;
    - the repo name `booxday`;
    - the APK file name `booxday-1.0.N.apk` (the in-app updater looks for exactly this name);
    - tag names `v1.0.N`;
    - class names and log tags.
  - The GitHub release title (CLAUDE.md: "BooxDay 1.0.N") can become "Boox Day 1.0.N" from the next release on; the updater doesn't read it. Update the release format in CLAUDE.md to match.
  - It doesn't mention Help.
- **Update popup:** every update says "Boox Day X is ready, with performance improvements and bug fixes." (hard-coded, `AppUpdatePopup.kt:104`), even for a feature release. **Fix:** "Boox Day X is ready." and let the release notes below it speak.
- **What's New:** the app has none. The old What's New center was removed in DB v29 (`Migrations.kt:733–736`); comments in `CalendarView.kt` 592, 670 and 864 still point to it. Release notes reach users only in the update popup, before they update (at most 6 bullets). **Owner decides:** keep it that way (recommended: no new screen), or after an update show the notes once (a new flag like `onboardingDismissedAt`; that is a new feature). The stale comments can go with any edit to those lines.
  - **Decided (owner, 26 Oct): show the release notes once after an update.** Design:
    - **When:** the app already knows the first start after an update (`AppUpdateGate.announceIfUpdated`, which today shows the snackbar "Boox Day was updated to X."). That start shows a popup instead of the snackbar. It is shown once per version: the stored version code is already updated there. A fresh install shows nothing; the welcome screens cover it.
    - **Where the notes come from:**
      1. In-app update: the updater already has the notes (`AppUpdateChecker.UpdateInfo.notes`, the release's `- ` bullets, at most 6). Save them in the update prefs, with the version, right before installing.
      2. Installed by hand (APK from GitHub): nothing was saved. Then fetch that version's release once (`releases/tags/v<versionName>`, the same parsing as the update check), only if online. If that fails, show the old snackbar and nothing more. Never retry.
    - **Popup:** the update popup's frame and size (fits the Go 6, buttons fixed, 4.4 bar). Title "Boox Day 1.0.N", heading "What's new", the bullets, and one button, "Close".
    - **Timing:** wait like the update popup does: not while the pen is active or the crash popup is open, and after the welcome screens.
    - **Release notes rule:** the bullets are now read right after the update too, so keep them short and plain (the CLAUDE.md release notes format already says so).
    - **Runtime cost:** one prefs read at start (already done there). The network call happens only on the first start after an update installed by hand.
    - **Tests:** a JVM test for the choice: saved notes, then fetch, then snackbar only, and shown once.
- Rewrite the welcome texts from what the app does today, using CLAUDE.md's feature list (calendar and accounts, Memos and Tasks, full screen, search, Boox Notes and Smart Link, AI on your own OpenRouter account, sleep screen, daily notes, updates). Short, no marketing words, nothing presented as new that isn't. The executor drafts and the owner approves before code.

### 4.7 Help is out of date (Ready to draft; owner approves)

Help is a 12-topic split popup of about 4,500–5,000 words (`HelpDialog.kt`). It scrolls with the bar on the content side. Wrong statements found (Help line → what the app does):
- **Calendar marks (Legend):**
  - :1211 "Underline = events": there is one dot under the number.
  - :1213 "Circle outline = Selected date": the selected day is a filled square; today has a ring (`CalendarView.kt:1325–1351`).
  - :615–619: memo, task and colour-bar marks on cells. Those marks are in the day header only (CLAUDE.md "Calendar card").
  - :1220–1224: event icons that don't exist (memo, ⚠, 🔗, 📍).
- **Tasks:**
  - :575–577 "A (Red) Critical, B (Orange), C (Yellow)", "tap to cycle": priorities are black and white, A/B/C = High/Medium/Low, picked in a popup (`TasksOverlay.kt:408–492`).
  - :1233 "🔥 High priority": the flame is the task streak.
- **Toolbar:**
  - :534 "Ballpoint, Brush, Marker, Pencil", "1–50px": the pens are Pen, Fountain Pen, Marker, Pencil and Brush, in five sizes.
  - :535 "Area Eraser": the erasers are "Standard Eraser" and "Stroke Eraser" (`EraserToolsButton.kt:107, 118`).
  - :537: the Insert items are wrong (Image, Background, Table, Link File, Text).
  - :552 "Settings > Canvas Settings": it is the "Scribble to Erase" checkbox in the eraser menu.
  - :1006, :1055 "Refresh button in the toolbar": it is "Screen Refresh" in ≡.
  - :1040 "Hand icon (Touch Mode)": doesn't exist.
  - :1011 "A2 mode during scrolling": scrolling is off.
- **Sync and data:**
  - :604 "6 months back and 6 months forward": it is 2 back and 3 forward (`SyncScheduler.kt:34–35`).
  - :1045 "Settings > Sync > Unlink Account": it is Calendar Accounts → Disconnect.
  - :1061–1062 "external storage /Documents/booxdaydb/": the database is app-private (`dbUtils.kt:22–25`).
  - :1066 "Settings > Backup": the menu says "Data & Backup".
- **AI, voice, Boox Notes:**
  - :830, :884: see 4.2.
  - :876 "Settings > OCR & Search": it is Settings → Boox Notes.
  - :865 "tap any calendar event to see suggestions": suggestions are in the Boox Notes menu.
  - :943–944, :938, :988: voice says "Gemini 2.0" and gives daily limits that the code doesn't have. The Voice settings dialog says something else (`VoiceSettingsDialog.kt:123–170`).
  - :754 "Smart Prioritization": the menu item is "Sort by Priority".
- **Missing from Help:** full screen, sleep screen, daily notes, crash report, in-app updates, Connect OpenRouter and the AI Models card, Summarize/Translate, recurring tasks, Export Pages, calendar accounts, Clear/Delete Page, task streak and stats, reminders, Google Drive backup.
- **Plan:**
  - Fix every wrong line above, together with 1.5's menu paths, in one pass.
  - Add the missing features as one short paragraph each, in the topic where a user would look. Don't add topics unless one is clearly needed (full screen, sleep screen and daily notes fit an existing one).
  - Cut what no longer applies, so Help gets shorter or stays the same length, not longer.
  - Tips (`TipsRepository.kt`) get the same pass: :167, :107, :87, :228 and :494 describe gestures and icons that don't match (the Help audit lists them). Lines 233, 238, 355, 360, 365, 442 and 500 are already in 1.5.
  - Draft to the owner first. Some Help lines weren't matched to code (lasso "bullseye", double-tap on a text box, "mini-canvas"): check them on the tablet, not by guessing.

### 4.8 AI Settings and AI Preferences (Ready; names and saving decided 26 Oct)

- **Size:** both fit. AI Settings is 0.94 × 0.86 of the screen on the Go 6 (`EInkTheme.kt:747–748`), and its Close button is fixed in the header. The AI Preferences wizard is 0.85 high, with Back/Next/Save fixed at the bottom and an X in the header. Both already have the "Scroll for more" bar. On the Go 6, the "Configure AI Preferences" button is likely below the first screen (estimate). The stronger bar in 4.4 is the fix for "users don't notice".
- **Texts:**
  - :157: see 4.2.
  - :141 lists "handwriting recognition" as an AI tool. Tasks are read on the tablet (ML Kit); memo pages are read by AI. Say "reading handwritten memo pages".
  - :253 "Use your own Google, OpenAI or Anthropic keys (BYOK)" links to OpenRouter, the only key the app takes. Say "Get an OpenRouter key".
  - One name for the area: the menu says "AI Settings", the title "AI Assistant", the card "AI Personalization", the wizard "AI Preferences". **Owner picks.** Proposal: "AI Settings" for the dialog and "AI Preferences" for the wizard.
    - **Decided (owner, 26 Oct):**
      - Menu item and dialog title: "AI Settings" (today the title is "AI Assistant", `AiSettingsDialog.kt:102`).
      - Card: "AI Preferences" (today "AI Personalization", `:176`).
      - Its button: "Set Up AI Preferences" (today "Configure AI Preferences", `:209`).
      - Wizard title: stays "AI Preferences" (`AiPreferencesDialog.kt:274`).
      - Help, the tips and the welcome popup then all say "Settings → AI Settings → AI Preferences".
- **Answers can be lost:**
  - The wizard keeps answers in memory across its 5 tabs, but writes them only when Save is tapped on the last tab (`AiPreferencesDialog.kt:133, 240`). The X, the back key or a tap outside throws away every answer without asking (`:151, :320`).
  - **Owner decides:** save on close (recommended: no question, no lost answers), or a Save button on every tab.
  - **Decided (owner, 26 Oct): save on close.** Every way out saves the answers: the X, the back key, a tap outside and Save. They go through the same save call that Save uses today (`MainScreen.kt:3989–3991`). Save on the last tab stays. Closing without changing anything writes nothing. Test: closing with the X keeps a changed answer.
  - The pasted-key field (`ApiKeySettingsCard.kt:83`, `remember`) is lost when AI Settings closes. That's fine: a key should not linger.
- **Keyboard:** the only text field (the key) is the first card, near the top, so the keyboard is unlikely to cover it. Covered by Part 3.1 if it does.
- **Not changed:** every stored answer is read by an AI prompt (`TaskAiContextBuilderV1`, `TaskAiService.buildV3SystemPrompt`), so no question is dead. The hidden "Voice Setup" button (`if (false)`, `AiPreferencesDialog.kt:287`) stays as it is (rule 2: no unrelated cleanups); report it only.

### 4.9 One look for dialogs, without a framework (Ready, small)

- There is no shared dialog header today. About 30 dialogs build their own title and X (private `DialogHeader`s in AgenticPlanDialog, CalendarSyncSettingsDialog and AiPreferencesDialog; `HelpHeader`; `AboutHeader`; `BooxNotesHeader`).
- Don't unify them in this plan. Do only what 4.3–4.5 need, with the same pieces: the X from `BooxNotesHeader` (44 dp, `Icons.Default.Close`), the 4.4 bar, sizes from `EInkTheme` tokens, and buttons fixed below the scrolling part.
- If the X is needed in 3 or more places, move it into one small `DialogCloseButton(onClose)` in `ui/components/`. Otherwise copy the 10 lines.

### 4.10 Seen during the 4.1 check (26 Oct, Go 10.3)

- **Search shows "TITLE: Untitled" instead of the memo's words (Ready).**
  - **Seen:** the memo results for "attempt" and "latency" were titled "TITLE: Untitled", with "TITLE: Untitled / TOPICS: MISC…" (or "PROJECTS, WORK…") underneath, not the handwriting.
  - **Cause:** the search query takes its snippet from the synopsis column first and the text second (`UnifiedOcrDao.kt` ~322–327, both search queries: `COALESCE(snippet(…, 2, …), snippet(…, 0, …))`). The synopsis is the fixed block that Smart Link uses (`ocr/semantic/SynopsisGenerator.kt`: `TITLE:`, `TOPICS:`, `KEYWORDS:`…). The result card then uses the snippet's first line as the title (`SearchDialog.kt:598–606`).
  - **Fix:** take the snippet from the text (column 0) first, and the synopsis only when there is no text. Check `SearchDialog`'s title rule still reads well, using the Go 10.3 results above.
  - **Cost:** none (only while searching).
  - **Test:** a Robolectric case in the FTS tests: a page with text and a synopsis gives a snippet from the text.
  - CLAUDE.md's search note ("COALESCE it (synopsis, else text)") changes with it.
  - **Done (26 Oct):** both queries now use `COALESCE(NULLIF(snippet(…0…), ''), snippet(…2…), '')`, so the text comes first, then the synopsis, never NULL. Tests in `UnifiedOcrFtsConsistencyTest`: the Go 10.3 case, plus a page without text. The existing BOOX_NOTE expectation now comes from the text. CLAUDE.md note updated.
    - **Checked on the Go 10.3 (26 Oct):** memo results are now titled with the handwriting:
      - "privacy" → "Privacy and security monthly meeting", with "2:00 PM - 3:00 PM…" below (15 Oct memo);
      - "hmmm" → "hmmm" (28 Nov memo, before the clear).
      - Before the fix, these results read "TITLE: Untitled".
- **"Today" in the clear actions means the day on screen (Ready, owner approves the words).**
  - The ≡ items "Clear All Memos Today" / "Clear All Tasks Today" and their confirm text ("This will clear all memo pages for today") clear the day being shown. In the check that was 8 Nov, not today.
  - Proposal: "Clear All Memos on This Day" / "Clear All Tasks on This Day", and in the confirm: "This clears every memo page of Sun 8 Nov. It can't be undone."
  - Check the ≡ menu width on the Go 6: `ToolbarMenu.kt:82` notes the old label already wrapped at 230 dp.
- **Opening Boox Day by `am start` fails while BOOX has it frozen.** After a force-stop, BOOX's app freeze disabled it (`enabled=3`), and `am start` said "No activity found". Opening it from the BOOX launcher icon unfreezes it. This is a note for testing, not an app fault.

### Part 4 order and tests
1. 4.1 (checked 26 Oct; fix (a), (b), (b2)), the 4.10 search snippet, and 4.2: data, search and privacy first.
2. 4.3, then 4.4: Boox Notes first, then the shared bar, which needs the owner's OK on its look.
3. 4.5 and 4.6.
4. 4.7 and 1.5 together.
5. 4.8.
- **Tests:** a Robolectric test for 4.1 (a): after a clear, nothing of the cleared pages is left in memory, previews or search. The rest are UI and texts, checked on the Go 6 and Go 10.3 by screenshot, one dialog at a time. Build `assembleDebug` once per batch shown to the owner, not per dialog.

---

## Tester checklist (every target tablet)

Note the model, firmware (Settings → About), pen type and the BooxDay version.

Review (1.0.37): testers should be on 1.0.37 or later; items 2 and 4 now ask about late ink directly, and item 10 checks the pen-rest post from `0c17cfc`.

1. **First start:** do the start screens end in the calendar, or a white screen?
2. **Write in Memos** for 2 minutes. Any crash, or lag building up? Does ink appear the moment the pen touches, also for quick short strokes?
3. **Write 10 tasks** across 2 pages, tick 3, switch pages back and forth. Is everything drawn?
4. **Switch days quickly** 10 times, then write. Is ink on time? Is it still on time after an hour of normal use?
5. **Connect Google:** any error, and what does it say?
6. **Multi-day event:** does it have a dot on every day?
7. **Toolbar and ≡ menu:** does anything get cut off? (Go 6, Go 7)
8. **Kaleido tablets:** are the colour pens readable? Is the black-and-white UI clean, with no grey mud?
9. **If it crashes:** reopen the app → "Copy Report", and send it.
10. **Single strokes on an empty Memos page:** does a stroke ever flash white or vanish for a moment after the pen lifts? (1.0.37 posts strokes 250 ms after the pen rests; on the Go 10.3 the system's own repaint comes at about 570 ms, other tablets may differ.)

11. **Clear All Memos Today** with 3 memo pages, then open page 2: is it empty? **Clear All Tasks Today** in full screen, then leave full screen: is the page count right? (Part 4.1)
12. **Dialogs on a small screen:** can every dialog be closed, and is it clear when more is below? (Part 4.3–4.5)

## Verification summary
- Per step: run the focused tests named in the step, and build `assembleDebug`.
- Part 4: the 4.1 checks on the Go 10.3 before and after the fix; for each dialog, a Go 6 screenshot before and after, shown to the owner.
- Part 2: logcat evidence before and after on the Go 10.3, and the Go 6 where it applies (installs with the owner's OK).
- Final report to the owner: what was fixed, what was checked on which tablet, and what is still unverified, especially EMR tablets and Android 11.
- Review (1.0.37): for 2.2, the before/after evidence is the measured pen-down to ink time, not only log lines.
