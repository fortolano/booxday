# Dialog Rulebook - Mandatory UI Standards

> **ENFORCEMENT LEVEL: MANDATORY**
> This rulebook defines the ONLY acceptable patterns for dialog buttons in BooxDay.
> AI agents MUST follow these rules exactly. No exceptions. No "improvements."

---

## ⛔ ABSOLUTELY OUT OF SCOPE — DO NOT TOUCH

```
╔══════════════════════════════════════════════════════════════════════════════╗
║  THE FOLLOWING ARE STRICTLY FORBIDDEN. NO EXCEPTIONS. EVER.                  ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

### TOOLBARS — OFF LIMITS

- `editor/ui/toolbar/*.kt` — ALL toolbar files
- `MainToolbar.kt`, `EditorToolbar.kt`, `TaskToolbar.kt`
- Toolbar layouts, toolbar button arrangements, toolbar visibility
- Toolbar button order, toolbar spacing, toolbar dimensions
- Any component that is part of a toolbar (vs. a dialog button)

### ICONS — OFF LIMITS

- Icon selection (which icon is used where)
- Icon sizes (`24.dp`, `20.dp`, etc.)
- Icon colors, icon tints
- Icon assets, icon resources
- `Icons.Default.*`, `painterResource()` for icons
- X/close icons in dialog headers are ONLY in scope for dual-dismiss decisions (keep or remove), NOT for resizing or restyling

### ICON SIZES — OFF LIMITS

- `EInkTheme.Icon.*` size tokens
- Any `Modifier.size()` applied to icons
- IconButton dimensions
- Touch target sizes for icon-only buttons (unless it's a text button)

### WHY THIS MATTERS

This rulebook is about **dialog button standardization** — specifically:
- Button labels (Cancel, Close, Delete, Save, etc.)
- Button order (escape left, primary right)
- Button wording consistency
- Button styling (EinkSecondaryButton vs ActionButton)
- Button sizing and touch targets (42dp minimum)
- Button form factor and layout patterns

It is **NOT** about:
- Toolbars or toolbar buttons
- Icons (selection, sizing, colors, tints)
- Any non-dialog UI components

### ENFORCEMENT TABLE

| If you find yourself... | Action |
|------------------------|--------|
| Editing a toolbar file | ❌ **STOP** — OUT OF SCOPE |
| Changing an icon size | ❌ **STOP** — OUT OF SCOPE |
| Swapping one icon for another | ❌ **STOP** — OUT OF SCOPE |
| Adjusting toolbar button spacing | ❌ **STOP** — OUT OF SCOPE |
| Modifying IconButton dimensions | ❌ **STOP** — OUT OF SCOPE |
| Changing icon colors/tints | ❌ **STOP** — OUT OF SCOPE |

### ONLY EXCEPTION

The **only** icon-related change permitted is removing an X icon from a dialog header when resolving dual-dismiss issues (e.g., Issue 12 in the standardization plan). This is a **button-count decision**, not an icon-styling decision.

---

## Quick Reference - Button Layouts

### 2-Button Layout (Most Common)

```
┌─────────────────────────────────────────────────────────────┐
│  [  Cancel  ]                           [  Primary Action  ]│
│   ↑ LEFT                                   ↑ RIGHT          │
│   ↑ Outlined                               ↑ Filled dark    │
│   ↑ 40%                    20% gap         ↑ 40%            │
└─────────────────────────────────────────────────────────────┘
```

### 3-Button Layout (When Two Actions Needed)

```
┌─────────────────────────────────────────────────────────────┐
│  [Cancel]        [Secondary Action]        [Primary Action] │
│   ↑ LEFT            ↑ MIDDLE                  ↑ RIGHT       │
│   ↑ Outlined        ↑ Outlined                ↑ Filled dark │
│   ↑ Escape          ↑ Destructive/Alt         ↑ Constructive│
└─────────────────────────────────────────────────────────────┘
Example: [Cancel]  [Delete]  [Save]
Example: [Cancel]  [Clear]   [Clear & Sync]
Example: [Cancel]  [Delete]  [Edit]
```

### 4-Button Layout (Use Sparingly)

```
┌─────────────────────────────────────────────────────────────┐
│  [Cancel]   [Action 1]   [Action 2]   [Primary Action]      │
│   ↑ LEFT      ↑ MID-L      ↑ MID-R       ↑ RIGHT            │
└─────────────────────────────────────────────────────────────┘
Example: [Cancel]  [Delete]  [Edit]  [Save]
```

**More than 4 buttons = REDESIGN REQUIRED. Use action menu, tabs, or split into multiple dialogs.**

---

## Rule 1: Button Positioning is FIXED

### THE LAW

- **LEFTMOST button** = ALWAYS the escape/dismiss action (Cancel/Close)
- **RIGHTMOST button** = ALWAYS the most constructive/expected action (Save/Create/Apply)
- **MIDDLE buttons** = Secondary actions, including destructive actions (Delete/Clear)
- **Destructive actions** (Delete, Wipe, Clear) = NEVER on far right, always in middle

### Why Destructive Actions Go in the Middle

Users develop muscle memory: "right button = do it, left button = escape." Placing "Delete" on the far right leads to accidental data loss. By placing destructive actions in the middle:

- Users must consciously move past "Cancel" AND past the destructive action to reach "Save"
- Accidental taps are less likely to cause data loss
- The rightmost position is reserved for the "happy path" action

### Position Priority Table

| Position | Role | Button Types |
|----------|------|--------------|
| **Leftmost** | Escape | "Cancel", "Close", "Dismiss" |
| **Rightmost** | Primary/Constructive | "Save", "Create", "Apply", "Export", "Send", "Move" |
| **Middle** | Secondary/Destructive | "Delete", "Clear", "Wipe", "Remove", "Edit", alternate actions |

### Layout Code Patterns

**2-Button Layout:**

```kotlin
Row(modifier = Modifier.fillMaxWidth()) {
    EinkSecondaryButton(
        text = "Cancel",
        onClick = onDismiss,
        modifier = Modifier.weight(0.4f)
    )
    Spacer(modifier = Modifier.weight(0.2f))
    ActionButton(
        text = "Save Event",
        onClick = onSave,
        modifier = Modifier.weight(0.4f)
    )
}
```

**3-Button Layout:**

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    EinkSecondaryButton(
        text = "Cancel",
        onClick = onDismiss,
        modifier = Modifier.weight(1f)
    )
    EinkSecondaryButton(
        text = "Delete",
        onClick = onDelete,
        modifier = Modifier.weight(1f)
    )
    ActionButton(
        text = "Save",
        onClick = onSave,
        modifier = Modifier.weight(1f)
    )
}
```

**4-Button Layout:**

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(6.dp)
) {
    EinkSecondaryButton(
        text = "Cancel",
        onClick = onDismiss,
        modifier = Modifier.weight(1f)
    )
    EinkSecondaryButton(
        text = "Delete",
        onClick = onDelete,
        modifier = Modifier.weight(1f)
    )
    EinkSecondaryButton(
        text = "Edit",
        onClick = onEdit,
        modifier = Modifier.weight(1f)
    )
    ActionButton(
        text = "Save",
        onClick = onSave,
        modifier = Modifier.weight(1f)
    )
}
```

### Forbidden Patterns

```
❌ WRONG: Destructive action (Delete) on far right
❌ WRONG: Cancel/Close on the right side
❌ WRONG: Primary constructive action in the middle
❌ WRONG: More than 4 buttons without redesigning
❌ WRONG: Using Arrangement.SpaceAround or SpaceEvenly (use spacedBy or weight)
```

---

## Rule 2: Button Text Must Be Specific Action Verbs

### THE LAW

- Button text MUST describe exactly what will happen when clicked
- NEVER use vague words: "OK", "Yes", "No", "Confirm", "Submit"
- Format: `[Verb]` or `[Verb] + [Object]`

### Approved Button Text Patterns

| Action Type | Examples |
|-------------|----------|
| **Dismiss** | "Cancel", "Close" |
| **Create** | "Create Task", "Create Event", "Add" |
| **Save** | "Save", "Save Event", "Save Changes" |
| **Delete** | "Delete", "Delete Page", "Delete Task" |
| **Clear** | "Clear", "Clear All", "Clear & Sync" |
| **Export** | "Export", "Export as PDF" |
| **Move** | "Move", "Move Task" |
| **Edit** | "Edit", "Edit Task" |
| **Send** | "Send", "Send Feedback" |
| **Apply** | "Apply", "Apply Changes" |
| **Retry** | "Retry", "Try Again" |
| **Sign In** | "Sign In", "Connect Account" |

### Specific Examples

| Context | ❌ WRONG | ✅ CORRECT |
|---------|----------|------------|
| Deleting a page | "OK", "Yes" | "Delete Page" |
| Saving an event | "Confirm", "Done" | "Save Event" |
| Confirming export | "OK", "Yes" | "Export" |
| Wiping all data | "OK", "WIPE ALL" | "Wipe All Data" |
| Creating a task | "Submit", "Done" | "Create Task" |
| Closing help screen | "OK" | "Close" |
| After seeing an error | "OK", "Dismiss" | "Close" |
| Retrying sync | "OK" | "Retry" |

### Forbidden Words (NEVER use these alone as button text)

```
❌ "OK"
❌ "Yes"
❌ "No"
❌ "Confirm"
❌ "Submit"
❌ "Done" (except for multi-step wizards at final step)
❌ "Accept"
❌ "Agree"
❌ ALL CAPS like "DELETE", "WIPE ALL", "CLEAR"
```

---

## Rule 3: Button Styling is Standardized

### THE LAW

- **Secondary button** (Cancel/Close/Destructive): Outlined, light background
- **Primary button** (Save/Create/Main action): Filled dark background, white text
- All buttons MUST be same height: `EInkTheme.Button.height`
- All buttons MUST have adequate touch target (minimum 42dp)

### Styling by Position

| Position | Component | Style |
|----------|-----------|-------|
| Leftmost (Cancel) | `EinkSecondaryButton` | Outlined |
| Middle (Secondary/Destructive) | `EinkSecondaryButton` | Outlined |
| Rightmost (Primary) | `ActionButton` | Filled dark |

### Required Components

```kotlin
// For Cancel/Close (always leftmost):
EinkSecondaryButton(text = "Cancel", onClick = onDismiss, ...)

// For destructive or secondary actions (middle):
EinkSecondaryButton(text = "Delete", onClick = onDelete, ...)

// For primary constructive action (always rightmost):
ActionButton(text = "Save Event", onClick = onSave, ...)
```

### Forbidden Styling

```
❌ WRONG: Using raw Box() with custom styling for buttons
❌ WRONG: Using red/colored backgrounds (e-ink unfriendly)
❌ WRONG: ALL CAPS text like "WIPE ALL" or "DELETE"
❌ WRONG: Different button heights in same dialog
❌ WRONG: Buttons smaller than 42dp touch target
❌ WRONG: ActionButton (filled) for destructive actions
```

---

## Rule 4: Dismiss Mechanism is Singular

### THE LAW

- Each dialog has ONE dismiss mechanism, not multiple
- Choose X icon in header OR Cancel button at bottom, NEVER both

### When to Use What

| Dialog Type | Dismiss Mechanism |
|-------------|-------------------|
| **Action dialogs** (forms, confirmations) | "Cancel" button at bottom left |
| **Information dialogs** (About, Help) | X icon in header OR "Close" button |
| **Error dialogs** | "Close" button at bottom |
| **Multi-action dialogs** (3-4 buttons) | "Cancel" button at bottom left |

### Forbidden Patterns

```
❌ WRONG: X icon in header AND "Close" button at bottom
❌ WRONG: X icon in header AND "Cancel" button at bottom
❌ WRONG: No dismiss mechanism at all
❌ WRONG: Only allowing backdrop tap to dismiss (must have explicit button)
```

---

## Rule 5: Destructive Actions Have Clear Warning

### THE LAW

- Destructive actions (delete, wipe, clear, remove permanently) MUST:
  1. Have dialog title that mentions the destruction: "Delete Page?"
  2. Have button text that matches: "Delete Page" (or shortened "Delete" in multi-button)
  3. Have explanatory text mentioning permanence: "This cannot be undone."
  4. Be positioned in the MIDDLE, never far right

### Required Pattern for Destructive Dialogs

```kotlin
// Title must mention the action
Text(text = "Delete Page?", ...)

// Body must mention consequences
Text(text = "This will permanently delete page 3. This cannot be undone.", ...)

// In 2-button layout: destructive action on right is acceptable
// because it's the ONLY action (user is confirming they want to delete)
EinkSecondaryButton(text = "Cancel", ...)      // LEFT
ActionButton(text = "Delete Page", ...)         // RIGHT

// In 3+ button layout: destructive action goes in MIDDLE
EinkSecondaryButton(text = "Cancel", ...)      // LEFT
EinkSecondaryButton(text = "Delete", ...)      // MIDDLE - destructive
ActionButton(text = "Save", ...)               // RIGHT - constructive
```

### Exception: Pure Deletion Confirmation

When a dialog's ONLY purpose is to confirm deletion (no other actions), the destructive button may be on the right:

```
[Cancel]  [Delete Page]
```

This is acceptable because the user explicitly opened this dialog to delete something.

---

## Rule 6: When to Use Multi-Button vs Selection Controls

### Use Multi-Button Layout (3-4 buttons) When

- Actions are fundamentally different operations (Delete vs Edit vs Save)
- User might want to perform ONE of several distinct actions
- Actions cannot be combined or are mutually exclusive paths

**Examples requiring multi-button:**

- Task management: `[Cancel]` `[Delete]` `[Edit]` `[Move]`
- Sync data options: `[Cancel]` `[Clear]` `[Clear & Sync]`
- Unsaved changes: `[Cancel]` `[Discard]` `[Save]`

### Use Selection Controls + Single Action When

- User is choosing between variations of the SAME action
- Options are different formats, destinations, or parameters
- The action itself is the same, just configured differently

**Examples requiring selection control:**

- Export format: Radio buttons for PDF/PNG/XOPP, then `[Cancel]` `[Export]`
- Move destination: Date picker, then `[Cancel]` `[Move]`
- Color choice: Color selector, then `[Cancel]` `[Apply]`

### Decision Flowchart

```
Q: Are the options different ACTIONS or different CONFIGURATIONS of one action?

Different ACTIONS (Delete vs Save) → Multi-button layout
Different CONFIGURATIONS (PDF vs PNG) → Selection control + 2 buttons

Q: Would clicking each button do a fundamentally different thing?

Yes → Multi-button layout
No, same thing with different settings → Selection control + 2 buttons
```

---

## Rule 7: Cancel vs Close Word Choice

### THE LAW

- **"Cancel"** = User started an action and is abandoning it
- **"Close"** = User is dismissing information they've consumed

### Decision Table

| Dialog Context | Leftmost Button Text |
|----------------|---------------------|
| Creating/editing form | "Cancel" |
| Confirmation before action | "Cancel" |
| Viewing information (About, Help) | "Close" |
| Viewing error message | "Close" |
| Viewing results/status | "Close" |
| Settings (with auto-save) | "Close" |
| Any dialog with 3+ action buttons | "Cancel" |

---

## Verification Checklist

Before submitting ANY dialog code, verify ALL items:

```
□ Leftmost button is Cancel/Close (escape action)
□ Rightmost button is primary constructive action (or absent for info-only)
□ Destructive actions (Delete/Clear) are in middle positions, never far right
□ Exception: Pure deletion confirmations may have Delete on right
□ Button text is specific verb, not "OK"/"Yes"/"Confirm"
□ No ALL CAPS button text
□ Using EinkSecondaryButton for left/middle, ActionButton for rightmost
□ All buttons same height with minimum 44dp touch target
□ Only ONE dismiss mechanism (not both X icon and Cancel button)
□ Destructive dialogs have matching title and button text
□ Dialog uses Popup (not Dialog) for e-ink compatibility
□ 5+ buttons? STOP and redesign with selection controls or split dialogs
```

---

## Code Templates

### Template 1: 2-Button Confirmation Dialog

```kotlin
@Composable
fun DeletePageDialog(
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                    .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.ModernDialog.padding),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.ModernDialog.contentSpacing)
            ) {
                Text(
                    text = "Delete Page?",
                    fontWeight = FontWeight.Bold,
                    fontSize = EInkTheme.ModernDialog.titleFontSize,
                    color = EInkTheme.ModernDialog.titleColor
                )
                Text(
                    text = "This will permanently delete the page. This cannot be undone.",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    color = EInkTheme.Colors.primary
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    EinkSecondaryButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.4f)
                    )
                    Spacer(modifier = Modifier.weight(0.2f))
                    ActionButton(
                        text = "Delete Page",
                        onClick = onDelete,
                        modifier = Modifier.weight(0.4f)
                    )
                }
            }
        }
    }
}
```

### Template 2: 3-Button Task Management Dialog

```kotlin
@Composable
fun TaskManagementDialog(
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                    .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.ModernDialog.padding),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.ModernDialog.contentSpacing)
            ) {
                Text(
                    text = "Task Options",
                    fontWeight = FontWeight.Bold,
                    fontSize = EInkTheme.ModernDialog.titleFontSize,
                    color = EInkTheme.ModernDialog.titleColor
                )
                Text(
                    text = "Choose an action for this task.",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    color = EInkTheme.Colors.primary
                )
                // 3-button layout: Cancel | Delete | Edit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EinkSecondaryButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    EinkSecondaryButton(
                        text = "Delete",           // Destructive in MIDDLE
                        onClick = onDelete,
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        text = "Edit",             // Constructive on RIGHT
                        onClick = onEdit,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
```

### Template 3: 4-Button Dialog with Save

```kotlin
@Composable
fun EditTaskWithUnsavedChangesDialog(
    onDiscard: () -> Unit,
    onDelete: () -> Unit,
    onContinueEditing: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                    .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.ModernDialog.padding),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.ModernDialog.contentSpacing)
            ) {
                Text(
                    text = "Unsaved Changes",
                    fontWeight = FontWeight.Bold,
                    fontSize = EInkTheme.ModernDialog.titleFontSize,
                    color = EInkTheme.ModernDialog.titleColor
                )
                Text(
                    text = "You have unsaved changes. What would you like to do?",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    color = EInkTheme.Colors.primary
                )
                // 4-button layout: Cancel | Delete | Discard | Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EinkSecondaryButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    EinkSecondaryButton(
                        text = "Delete",           // Destructive
                        onClick = onDelete,
                        modifier = Modifier.weight(1f)
                    )
                    EinkSecondaryButton(
                        text = "Discard",          // Secondary destructive
                        onClick = onDiscard,
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        text = "Save",             // Primary constructive on RIGHT
                        onClick = onSave,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
```

### Template 4: Information Dialog (Single Button)

```kotlin
@Composable
fun OfflineModeDialog(onDismiss: () -> Unit) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)
                    .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.ModernDialog.padding),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.ModernDialog.contentSpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Offline Mode",
                    fontWeight = FontWeight.Bold,
                    fontSize = EInkTheme.ModernDialog.titleFontSize,
                    color = EInkTheme.ModernDialog.titleColor
                )
                Text(
                    text = "Your changes are saved locally and will sync when you reconnect.",
                    fontSize = EInkTheme.Typography.bodyLarge,
                    color = EInkTheme.Colors.primary
                )
                ActionButton(
                    text = "Close",  // NOT "OK"
                    onClick = onDismiss
                )
            }
        }
    }
}
```

---

## Dialogs Requiring Fixes (From Audit)

| File | Current Issue | Required Fix |
|------|---------------|--------------|
| `editor/ui/MoveTaskDialog.kt` (ChildTaskDialog) | Delete on left, Cancel on right | Swap: `[Cancel]` `[Delete]` |
| `ui/dialogs/DataBackupDialog.kt` | "WIPE ALL" in caps | Change to "Wipe All Data" |
| `ui/dialogs/SyncErrorDialog.kt` (OfflineModeDialog) | Uses "OK" | Change to "Close" |
| `ui/dialogs/AboutDialog.kt` | Has X icon AND "Close" button | Remove "Close" button, keep X icon |
| `ui/dialogs/ConfirmationDialog.kt` (ShowExportDialog) | 3 action buttons for options | Refactor to segmented control + `[Cancel]` `[Export]` |

---

## Summary: The Three Golden Rules

1. **Position is sacred**: Cancel LEFT, Primary constructive action RIGHT, Destructive actions MIDDLE
2. **Words must be specific**: "Delete Page" not "OK", "Save Event" not "Confirm"
3. **Styling is standardized**: `EinkSecondaryButton` for left/middle, `ActionButton` for rightmost only

**This is not a guideline. This is THE LAW for BooxDay dialogs.**
