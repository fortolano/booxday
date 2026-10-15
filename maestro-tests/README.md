# Maestro E2E Tests for Boox Day

Automated UI testing using [Maestro](https://maestro.mobile.dev/) on Boox e-ink devices.

## Quick Start

```bash
# Run a single test
maestro test tests/test_tab_switching.yaml

# Run all tests
maestro test tests/

# Run calibration (after UI changes)
maestro test calibration_flow.yaml
```

## Structure

```
maestro-tests/
├── ui_map.yaml           # UI coordinate mapping (THE SOURCE OF TRUTH)
├── delay.js              # E-ink delay helper script
├── calibration_flow.yaml # Re-capture all UI states
├── flows/                # Reusable flow fragments
│   ├── switch_to_tasks.yaml
│   ├── switch_to_memos.yaml
│   ├── open_settings.yaml
│   ├── open_boox_notes.yaml
│   └── close_dialog.yaml
└── tests/                # Actual test scenarios
    ├── test_tab_switching.yaml
    ├── test_calendar_navigation.yaml
    └── test_boox_notes_dialog.yaml
```

## Key Concepts

### UI Coordinate Map (`ui_map.yaml`)

All tap coordinates are stored in `ui_map.yaml`. When the UI changes:

1. Run `maestro test calibration_flow.yaml`
2. Review screenshots in `calibration/` folder
3. Update coordinates in `ui_map.yaml`
4. Tests automatically use correct positions

### E-Ink Delays

E-ink displays need time to refresh. Use the `delay.js` script:

```yaml
- runScript:
    file: delay.js
    env:
      DELAY_MS: "1500"  # Adjust as needed
```

Recommended delays:
- After launch: 4000ms
- After tap: 1500ms
- After dialog open: 2000ms

### Reusable Flows

Common actions are in `flows/`. Use them with `runFlow`:

```yaml
- runFlow: flows/open_settings.yaml
- takeScreenshot: my_test/settings_open
- runFlow: flows/close_dialog.yaml
```

## Writing New Tests

1. Check `ui_map.yaml` for coordinates
2. Create test in `tests/` folder
3. Use `runFlow` for common actions
4. Add screenshots at key points
5. Run and verify

Example:

```yaml
appId: com.boox.day
---
- launchApp
- runScript:
    file: ../delay.js
    env:
      DELAY_MS: "4000"

- runFlow: ../flows/open_boox_notes.yaml
- takeScreenshot: my_test/dialog_open

# Your test actions here...
```

## For Claude Code (and other agents)

When asked to "run a maestro test for X":

1. Read `ui_map.yaml` for coordinates
2. Check if test exists in `tests/`
3. If not, create new test using coordinates from map
4. Run with `maestro test <file>.yaml`
5. Report results and attach screenshots if needed

### Toolbar semantics for coordinate naming

Use consistent semantic names and the canonical left-to-right ordering when adding or updating entries in `ui_map.yaml`:

- **Top toolbar (global)** – back, month label (e.g., "December 2025"), forward, microphone, today button, add calendar event, sync / "Sign in" (Google/Outlook), screen refresh, task stats (flame icon), settings gear.
- **Memos toolbar** – memos tab, tasks tab, drawing tools, drawing settings (dropdown), eraser (dropdown), lasso, image & inserts (dropdown), AI features (dropdown), Boox Notes (dropdown), undo, redo, page chevron left, memos pages label (e.g., "1/2"), page chevron right, hamburger menu (dropdown).
- **Tasks toolbar** – memos tab, tasks tab, drawing tools (dropdown), drawing settings (dropdown), eraser (dropdown), tasks actions (dropdown: move/copy/delete tasks), add task, AI features (dropdown), Boox Notes (dropdown), undo, redo, page chevron left, tasks pages label (e.g., "1/2"), page chevron right, hamburger menu (dropdown).

When writing Maestro flows, refer to coordinates using these semantic labels (e.g., `top_toolbar.task_stats_flame`, `memos_toolbar.undo`, `tasks_toolbar.add_task`) so tests remain readable and aligned with the actual UI.

## Troubleshooting

- **Blank screenshots**: Increase delay before screenshot
- **Wrong element tapped**: Re-run calibration, update `ui_map.yaml`
- **App won't launch**: Run `adb shell pm enable com.boox.day` first
