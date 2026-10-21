# BooxDay tasks feed (read-only)

Other apps on the reader (launchers, widgets, assistants) can read BooxDay tasks through a
content provider. They can't add, change, complete or delete tasks.

| URI | Returns |
| --- | --- |
| `content://com.boox.day.tasks/due` | Open tasks due today or overdue, oldest due first |
| `content://com.boox.day.tasks/today` | Every task with content on today's page |
| `content://com.boox.day.tasks/open` | All open tasks with content, newest day first |

Optional `?limit=` (1 to 200, default 50).

Columns: `_id`, `task_id`, `title`, `handwritten_only`, `day`, `due_day`, `completed`,
`priority`, `overdue`, `parent_task_id`, `updated_at`. `title` is the converted, typed or
recognised text, or "Handwritten task" when a task has only strokes.

Only task text and dates are shared. Strokes, notes, pages, accounts, API keys and settings are
not. The provider is exported without a permission, so any app on the device can read it.

Readers are notified on `content://com.boox.day.tasks` whenever BooxDay changes a task.

Note: BOOX's app freezing disables idle apps, which also hides this feed until BooxDay is opened
again. Turning off freezing for BooxDay keeps it available.

Source: `app/src/main/java/com/boox/day/share/`.
