# Permission reasons (for the verification form)

Google asks for a reason for each **sensitive** permission, including why a narrower one won't do, and requires apps to ask for the least access they need. Basic sign-in (`openid`, email, profile) and `drive.file` are not sensitive, so the form doesn't ask about them; short texts are below in case a reviewer does.

## The calendar permission (narrowed on branch `google-verification`)

Released versions up to 1.0.37 ask for `https://www.googleapis.com/auth/calendar`. Google describes it to users as "See, edit, share, and permanently delete all the calendars you can access using Google Calendar."

What BooxDay actually calls (all in `sync/CalendarSyncManager.kt`):

| Calendar API call | Where | Used for |
|---|---|---|
| `calendarList.list` | lines 212, 418 | List the user's calendars so they can pick which to show |
| `calendarList.get` | lines 224, 501, 592 | Name and colour of a calendar |
| `events.list` | line 231 | Read events for the tablet |
| `events.get` | line 541 | Read an event before saving the user's change |
| `events.insert` | line 498 | User adds an event in BooxDay |
| `events.update` | line 589 | User edits an event in BooxDay |
| `events.delete` | line 637 | User deletes an event in BooxDay |

Nothing shares calendars, changes sharing settings, or creates or deletes whole calendars. So full `calendar` access can't be honestly justified, and a reviewer is likely to say so. Google's own API reference shows two narrower permissions cover every call above:

- `https://www.googleapis.com/auth/calendar.events`: "View and edit events on all your calendars." Covers `events.list/get/insert/update/delete`.
- `https://www.googleapis.com/auth/calendar.calendarlist.readonly`: "See the list of Google calendars you're subscribed to." Covers `calendarList.list/get`.

**Done** (`GoogleAuthManager.CALENDAR_SCOPES`, pinned by `GoogleCalendarScopesTest`), in `main`, not released yet. Release it before recording the video: the video must show the same permissions as the form. People already signed in keep syncing without being asked again: Google gives the narrower access to accounts that granted the full scope (checked on the Go 10.3, 26 Oct 2026). If Google ever refuses a token (access withdrawn), Calendar Accounts shows Reconnect.

`calendar.events.owned` ("calendars you own") would be narrower still, but it can't read calendars shared with the user (family, work, holidays), which BooxDay shows. So `calendar.events` is the narrowest that keeps the app working.

## Text to paste

### `https://www.googleapis.com/auth/calendar.events`

> BooxDay is a daily planner for Onyx BOOX e-ink tablets. Its main screen is a month calendar with the selected day's events next to the user's handwritten notes and tasks. With this permission BooxDay reads the events of the calendars the user turns on, so they appear on the tablet, and saves the changes the user makes on the tablet: adding an event, editing its time, title, location or description, and deleting it. Writes happen only when the user saves or deletes an event in BooxDay. A read-only permission (calendar.events.readonly) isn't enough because users create, edit and delete events from the tablet, which is the main reason to use a planner on it. calendar.events.owned isn't enough because users also need to see and edit events on calendars shared with them, such as a family or work calendar. Event data is stored only on the user's tablet; BooxDay has no server, and the developer never receives it. The demo video shows reading, creating, editing and deleting events.

### `https://www.googleapis.com/auth/calendar.calendarlist.readonly`

> BooxDay lists the user's Google calendars (names and colours) so the user can choose which calendars appear on the tablet, and which calendar a new event is saved to. Read-only access to the calendar list is the narrowest permission that provides this. BooxDay never adds, removes or changes calendars. The demo video shows the calendar list after sign-in.

### `https://www.googleapis.com/auth/tasks` (Task Sync, Oct 2026)

What BooxDay calls (all in `tasksync/google/GoogleTasksProvider.kt`): `tasklists.list` and `tasklists.get("@default")` (the lists to pick from, and the main list), `tasks.list` (changes in the ticked lists, and a daily check of which tasks still exist), `tasks.get` (before a change, to see whether the phone changed it first), `tasks.insert`, `tasks.patch` (only the fields that changed), `tasks.move` (subtasks), `tasks.delete`. `tasks.readonly` can't do the writes. There is no narrower Tasks permission.

> BooxDay is a daily planner for Onyx BOOX e-ink tablets, where users write their tasks by hand or type them. Task Sync, which the user turns on in BooxDay's Preferences, keeps those tasks in step with the user's Google Tasks, so the same list is on their phone. With this permission BooxDay lists the user's task lists so they can choose which to sync, reads the tasks in the chosen lists to show them on the tablet, and saves the user's changes made on the tablet: a new task, its words, its done mark, its due date, subtasks, and deleting it. A read-only permission (tasks.readonly) isn't enough, because adding, ticking and deleting tasks on the tablet is the point of the feature, and the Tasks API has no narrower permission. BooxDay never uses or changes task notes. BooxDay keeps task data only on the user's tablet; BooxDay has no server, and the developer never receives it. The permission is asked for only when the user turns Task Sync on, separately from the calendar sign-in. The demo video shows the lists, a task written on the tablet appearing in Google Tasks, a task ticked in Google Tasks appearing ticked on the tablet, and a delete.

## Non-sensitive permissions (only if asked)

### `https://www.googleapis.com/auth/drive.file`

> Optional Cloud Backup: when the user turns it on or taps Backup Now, BooxDay saves a backup of the planner in a BooxDay folder in the user's own Google Drive, lists those backups so the user can restore one, and deletes the old ones when the user chooses to replace them. drive.file only gives access to files BooxDay created, not the user's other files.

### `openid`, `userinfo.email`, `userinfo.profile`

> Used to identify which Google account is connected and to show its email address in BooxDay's account list.
