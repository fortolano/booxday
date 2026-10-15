# Boox Day UI Patterns & E-Ink Development Guide

> **Last Verified**: December 2025
> **Covers Files**: EInkTheme.kt, all dialog files, Toolbar.kt, MainScreen.kt
> **Theme File**: `app/src/main/java/com/boox/day/ui/theme/EInkTheme.kt`

---

## Overview

Boox Day is optimized for E-Ink displays, which have unique constraints compared to LCD/OLED screens. This guide covers:
- E-Ink display characteristics and constraints
- Design patterns specific to E-Ink
- EInkTheme usage
- Common UI components
- Dialog patterns (Popup vs Dialog)

**Key Principle**: E-Ink requires **high contrast, minimal motion, and deliberate refresh management**.

---

## E-Ink Display Characteristics

### Physical Properties

| Property | E-Ink | LCD/OLED | Impact |
|----------|-------|----------|--------|
| **Refresh Rate** | ~100-250ms | 16ms (60Hz) | Animations feel sluggish |
| **Ghosting** | High (partial refresh) | None | Previous content visible |
| **Contrast** | Lower (gray-on-white) | High | Need bold colors |
| **Response Time** | 100-250ms | <5ms | Interactions feel delayed |
| **Power** | Only on change | Constant | Good battery life |

### Refresh Modes (Onyx SDK)

| Mode | Speed | Quality | Use Case |
|------|-------|---------|----------|
| **DU** (Direct Update) | Fastest (~85ms) | Low (1-bit) | Scrolling, typing |
| **GC16** (Grayscale) | Medium (~250ms) | High (16 levels) | Images, detailed content |
| **A2** (Animation) | Fast (~120ms) | Medium (4 levels) | UI transitions |
| **FULL** (Complete) | Slow (~500ms) | Perfect | Eliminate ghosting |

**Boox Day Strategy**:
- Use **FULL refresh** on Dialog show/hide (avoids scrim ghosting)
- Use **DU/A2** for drawing (fast stylus response)
- Minimize animations (triggers GC16 refresh cascades)

---

## E-Ink UI Rules (MANDATORY)

### 1. Popup vs Dialog - CRITICAL

| Type | Use Case | Refresh Mode | Ghosting |
|------|----------|--------------|----------|
| **Popup** (Compose) | ✅ All UI overlays | None (draw over existing) | Minimal |
| **Dialog** (Android) | ❌ NEVER USE | FULL (500ms scrim) | Severe |

**Why Popup**:
- No scrim (black overlay) → no ghosting
- Faster appearance (no refresh delay)
- Full control over positioning and styling

**Example**:
```kotlin
// ❌ WRONG - Causes 500ms refresh + ghosting
Dialog(onDismissRequest = {}) {
    Box { /* content */ }
}

// ✅ CORRECT - Instant, no ghosting
Popup(
    alignment = Alignment.Center,
    onDismissRequest = {}
) {
    Box { /* content */ }
}
```

**Reference Files**:
- `ui/dialogs/AddEditEventDialog.kt` - Full popup example
- `ui/dialogs/ExportDialog.kt` - ModernDialog pattern

---

### 2. No Ripple Effects

**Rule**: Never use `.clickable {}` - always use `.noRippleClickable {}`.

**Why**: Ripple animations cause:
- Multiple partial refreshes (ghosting)
- 300-500ms visual artifacts
- Poor user experience on E-Ink

**Implementation**:
```kotlin
// ❌ WRONG - Ripple causes ghosting
Box(modifier = Modifier.clickable { onClick() })

// ✅ CORRECT - No ripple
Box(modifier = Modifier.noRippleClickable { onClick() })
```

**Location**: `app/src/main/java/com/boox/day/ui/Clickable.kt`

**Import**:
```kotlin
import com.boox.day.ui.noRippleClickable
```

---

### 3. Use Vector Icons, Not Text Symbols

**Rule**: Use Material Icons (`Icons.Default.*`), never text symbols.

**Why**:
- Text symbols have inconsistent weights (font-dependent)
- Vector icons render crisp at any size
- Better accessibility

**Example**:
```kotlin
// ❌ WRONG - Font weight issues
Text("✕", fontSize = 24.sp)

// ✅ CORRECT - Clean vector rendering
Icon(
    Icons.Default.Close,
    contentDescription = "Close",
    modifier = Modifier.size(EInkTheme.Global.iconSize)
)
```

---

### 4. EInkTheme for All Values

**Rule**: Never hardcode sizes/colors - use EInkTheme constants.

**Why**:
- Consistent design system
- Easy global adjustments
- E-Ink optimized values (tested touch targets, contrast ratios)

**Example**:
```kotlin
// ❌ WRONG - Hardcoded
Box(
    modifier = Modifier
        .width(360.dp)
        .border(1.dp, Color.Black)
)

// ✅ CORRECT - EInkTheme
Box(
    modifier = Modifier
        .width(EInkTheme.PopupOverlay.width)
        .border(
            width = EInkTheme.PopupOverlay.borderWidth,
            color = EInkTheme.PopupOverlay.borderColor
        )
)
```

**See**: `docs/EINKTHEME_CHEATSHEET.md` for complete reference.

---

### 5. High Contrast Colors

**Rule**: Use pure black/white, avoid mid-grays on large areas.

**Why**:
- E-Ink has lower contrast than LCD
- Mid-grays require dithering (looks grainy)
- Black/white provides maximum readability

**Color Palette**:
```kotlin
EInkTheme.Colors.black      // Primary text, borders
EInkTheme.Colors.white      // Backgrounds
EInkTheme.Colors.darkGray   // Secondary text (sparingly)
EInkTheme.Colors.gray       // Disabled state
```

**Example**:
```kotlin
// ❌ WRONG - Mid-gray background (dithers)
Box(modifier = Modifier.background(Color(0xFF808080)))

// ✅ CORRECT - Pure black/white
Box(modifier = Modifier.background(EInkTheme.Colors.white))
Text("Title", color = EInkTheme.Colors.black)
```

---

### 6. Large Touch Targets

**Rule**: Minimum 48dp touch targets (WCAG AA accessibility).

**Why**:
- Stylus precision varies
- Finger touch on E-Ink requires larger targets
- Accidental mis-taps frustrating

**Example**:
```kotlin
// ❌ WRONG - 24dp too small for touch
Icon(
    Icons.Default.Delete,
    modifier = Modifier.size(24.dp).clickable { }
)

// ✅ CORRECT - 48dp touch target
Box(
    modifier = Modifier
        .size(EInkTheme.Global.touchTarget)  // 48.dp
        .noRippleClickable { onClick() },
    contentAlignment = Alignment.Center
) {
    Icon(
        Icons.Default.Delete,
        modifier = Modifier.size(EInkTheme.Global.iconSize)  // 24.dp visual
    )
}
```

---

### 7. No Shadows or Elevation

**Rule**: Use borders, not Material elevation/shadows.

**Why**:
- Shadows require grayscale gradients (dithers on E-Ink)
- Elevation causes z-order refresh issues
- Borders are crisp and clear

**Example**:
```kotlin
// ❌ WRONG - Shadow/elevation
Card(elevation = 4.dp) { }

// ✅ CORRECT - Border
Box(
    modifier = Modifier.border(
        width = EInkTheme.Global.borderWidth,
        color = EInkTheme.Global.borderColor
    )
) { }
```

---

### 8. Minimal Animations

**Rule**: Avoid animations - use instant state changes.

**Why**:
- Animations trigger multiple refreshes
- Each refresh = 100-250ms (visible lag)
- Static transitions feel faster on E-Ink

**Example**:
```kotlin
// ❌ WRONG - Animated fade
AnimatedVisibility(visible = isVisible) { }

// ✅ CORRECT - Instant show/hide
if (isVisible) {
    Popup { /* content */ }
}
```

---

### 9. Inverted Selection State

**Rule**: Selected items use inverted colors (black bg, white text).

**Why**:
- Maximum contrast indicates selection
- Consistent with E-Ink conventions
- Clear visual feedback

**Example**:
```kotlin
val isSelected = item == selectedItem
Box(
    modifier = Modifier
        .background(
            if (isSelected) EInkTheme.Selection.background  // Black
            else EInkTheme.Colors.background  // White
        )
) {
    Text(
        "Item",
        color = if (isSelected) EInkTheme.Selection.foreground  // White
                else EInkTheme.Colors.primary  // Black
    )
}
```

---

### 10. Thick Borders for Visibility

**Rule**: Use 1-2dp borders (not 0.5dp on large elements).

**Why**:
- E-Ink resolution requires visible borders
- Thin borders disappear on low-contrast displays
- 1dp is the standard, 0.5dp only for modern dialogs

**Example**:
```kotlin
// Standard popups
borderWidth = EInkTheme.PopupMenu.borderWidth  // 1.dp

// Modern dialogs only
borderWidth = EInkTheme.ModernDialog.borderWidth  // 0.5.dp
```

---

## EInkTheme Usage

### Structure Overview

```kotlin
EInkTheme
├── Colors              // Grayscale palette
├── Typography          // Font sizes
├── Global              // App-wide defaults
├── CalendarGrid
├── EventsPanel
├── Toolbar
├── TasksTab
├── PopupMenu           // 230.dp wide (small anchored menus)
├── PopupCompact        // 280.dp wide (simple confirmations)
├── PopupPanel          // 380.dp wide (date/color pickers)
├── PopupOverlay        // 460.dp wide (full dialogs/forms)
├── ModernDialog        // Soft gray palette dialogs
├── MenuItem
├── Button
├── TextField
└── ... (30+ components)
```

**Complete Reference**: `docs/EINKTHEME_CHEATSHEET.md`

---

### Popup Size Guidelines

| Size | Width | Use Case | Example |
|------|-------|----------|---------|
| **PopupMenu** | 230.dp | Small anchored menus | Settings, pen picker |
| **PopupCompact** | 280.dp | Simple confirmations | Delete task, move day |
| **PopupPanel** | 380.dp | Pickers | Date picker, color picker |
| **PopupOverlay** | 460.dp | Full dialogs | Event editor, voice input |
| **PopupOverlay (wide)** | 520.dp | Data-heavy | Stats dialog, AI results |

**Example**:
```kotlin
Popup(alignment = Alignment.Center) {
    Column(
        modifier = Modifier
            .width(EInkTheme.PopupOverlay.width)  // 460.dp
            .background(EInkTheme.PopupOverlay.background)
            .border(
                width = EInkTheme.PopupOverlay.borderWidth,
                color = EInkTheme.PopupOverlay.borderColor
            )
            .padding(EInkTheme.PopupOverlay.padding)
    ) {
        // Dialog content
    }
}
```

---

### Typography Guidelines

| Size | Use Case | Theme Constant |
|------|----------|----------------|
| 20sp | Screen titles | `Typography.titleLarge` |
| 18sp | Section headers | `Typography.titleMedium` |
| 16sp | Body text, menu items | `Typography.bodyLarge` |
| 14sp | Secondary text, buttons | `Typography.bodyMedium` |
| 12sp | Timestamps, hints | `Typography.bodySmall` |

**Note**: E-Ink requires slightly larger text than LCD for readability.

---

### Color Usage

**Primary**:
```kotlin
EInkTheme.Colors.primary       // Black - primary text, borders
EInkTheme.Colors.background    // White - backgrounds
```

**Secondary**:
```kotlin
EInkTheme.Colors.secondary     // Dark gray - secondary text
EInkTheme.Colors.disabled      // Gray - disabled state
```

**Dividers**:
```kotlin
EInkTheme.Colors.divider       // Black - standard dividers
EInkTheme.Colors.subtleDivider // Light gray - thin dividers only
```

**Avoid**:
- Mid-grays on large backgrounds (use sparingly)
- Color gradients (dithers poorly)
- Transparency on E-Ink (causes artifacts)

---

## Common UI Patterns

### Pattern: Centered Dialog

```kotlin
@Composable
fun MyCenteredDialog(
    onDismiss: () -> Unit
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .width(EInkTheme.PopupOverlay.width)
                .background(EInkTheme.PopupOverlay.background)
                .border(
                    width = EInkTheme.PopupOverlay.borderWidth,
                    color = EInkTheme.PopupOverlay.borderColor
                )
                .padding(EInkTheme.PopupOverlay.padding)
        ) {
            // Title
            Text(
                "Dialog Title",
                fontSize = EInkTheme.PopupOverlay.titleFontSize,
                color = EInkTheme.PopupOverlay.titleColor,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(EInkTheme.PopupOverlay.titlePaddingBottom))

            // Content
            Text("Dialog content here")

            Spacer(Modifier.height(EInkTheme.PopupOverlay.buttonRowPaddingTop))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                EInkSecondaryButton(
                    text = "Cancel",
                    onClick = onDismiss
                )
                Spacer(Modifier.width(8.dp))
                EInkPrimaryButton(
                    text = "Confirm",
                    onClick = { /* action */ }
                )
            }
        }
    }
}
```

**Reference**: `ui/dialogs/AddEditEventDialog.kt`

---

### Pattern: Anchored Popup Menu

```kotlin
@Composable
fun MyPopupMenu(
    anchorBounds: IntRect,
    onDismiss: () -> Unit
) {
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(
            anchorBounds.left,
            anchorBounds.bottom + 4  // 4dp gap below anchor
        ),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .width(EInkTheme.PopupMenu.width)
                .background(EInkTheme.PopupMenu.background)
                .border(
                    width = EInkTheme.PopupMenu.borderWidth,
                    color = EInkTheme.PopupMenu.borderColor
                )
                .padding(EInkTheme.PopupMenu.padding)
        ) {
            MenuItem("Option 1", onClick = { })
            MenuItem("Option 2", onClick = { })
            MenuItem("Option 3", onClick = { })
        }
    }
}

@Composable
fun MenuItem(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            fontSize = EInkTheme.MenuItem.fontSize,
            color = EInkTheme.MenuItem.textColor
        )
    }
}
```

**Reference**: `ui/SettingsMenu.kt`

---

### Pattern: Modern Dialog (Soft Gray Palette)

```kotlin
@Composable
fun MyModernDialog(onDismiss: () -> Unit) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(EInkTheme.ModernDialog.widthFraction)  // 0.82f
                .background(EInkTheme.ModernDialog.background)
                .border(
                    width = EInkTheme.ModernDialog.borderWidth,      // 0.5.dp
                    color = EInkTheme.ModernDialog.borderColor       // Light gray
                )
                .padding(EInkTheme.ModernDialog.padding)
        ) {
            // Title
            Text(
                "Modern Dialog",
                fontSize = EInkTheme.ModernDialog.titleFontSize,
                color = EInkTheme.ModernDialog.titleColor,  // Dark gray
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(EInkTheme.ModernDialog.contentSpacing))

            // Content with OptionCards
            OptionCard(
                title = "Option 1",
                isSelected = selectedOption == 1,
                onClick = { selectedOption = 1 }
            )

            Spacer(Modifier.height(EInkTheme.ModernDialog.contentSpacing))

            // Primary button
            EInkModernPrimaryButton(
                text = "Confirm",
                onClick = { /* action */ }
            )

            // Cancel link
            EInkCancelLink(
                text = "Cancel",
                onClick = onDismiss
            )
        }
    }
}
```

**Reference**: `ui/dialogs/ExportDialog.kt`

---

### Pattern: Button Row

```kotlin
@Composable
fun ButtonRow(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "Confirm",
    cancelText: String = "Cancel"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = EInkTheme.PopupOverlay.buttonRowPaddingTop),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EInkSecondaryButton(
            text = cancelText,
            onClick = onCancel
        )
        Spacer(Modifier.width(8.dp))
        EInkPrimaryButton(
            text = confirmText,
            onClick = onConfirm
        )
    }
}
```

---

### Pattern: Toggle

```kotlin
@Composable
fun MyToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = EInkTheme.Typography.bodyLarge,
            color = EInkTheme.Colors.primary
        )

        EInkToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            compact = false  // false = with ON/OFF labels
        )
    }
}
```

**Reference**: `ui/components/EInkToggle.kt`

---

### Pattern: List with Selection

```kotlin
@Composable
fun SelectableList(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column {
        items.forEachIndexed { index, item ->
            val isSelected = index == selectedIndex

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EInkTheme.MenuItem.height)
                    .background(
                        if (isSelected) EInkTheme.Selection.background
                        else EInkTheme.Colors.background
                    )
                    .noRippleClickable { onSelect(index) }
                    .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item,
                    fontSize = EInkTheme.MenuItem.fontSize,
                    color = if (isSelected) EInkTheme.Selection.foreground
                            else EInkTheme.MenuItem.textColor
                )
            }

            // Divider (skip last)
            if (index < items.lastIndex) {
                Divider(
                    color = EInkTheme.Divider.subtleColor,
                    thickness = EInkTheme.Divider.subtleThickness
                )
            }
        }
    }
}
```

---

## Component Library

### Buttons

**EInkPrimaryButton** (filled):
```kotlin
EInkPrimaryButton(
    text = "Save",
    onClick = { },
    enabled = true
)
```

**EInkSecondaryButton** (outlined):
```kotlin
EInkSecondaryButton(
    text = "Cancel",
    onClick = { }
)
```

**Location**: `ui/components/EinkButtons.kt`

---

### Toggles

**Standard** (with ON/OFF labels):
```kotlin
EInkToggle(
    checked = isEnabled,
    onCheckedChange = { isEnabled = it },
    compact = false
)
```

**Compact** (no labels, smaller):
```kotlin
EInkToggle(
    checked = isEnabled,
    onCheckedChange = { isEnabled = it },
    compact = true
)
```

**Location**: `ui/components/EInkToggle.kt`

---

### Loading Indicator

```kotlin
EInkLoadingDots(
    modifier = Modifier.size(48.dp)
)
```

**Location**: `ui/components/EinkLoadingDots.kt`

---

### Text Fields

```kotlin
EInkTextField(
    value = text,
    onValueChange = { text = it },
    label = "Title",
    placeholder = "Enter title...",
    modifier = Modifier.fillMaxWidth()
)
```

**Location**: `ui/components/EInkTextField.kt` (if exists, otherwise use custom implementation)

---

## Dialog Examples by Type

### Confirmation Dialog

**Use**: Simple yes/no questions.

**Width**: `PopupCompact` (280.dp)

**Example**: `ui/dialogs/DeleteTaskDialog.kt`

---

### Form Dialog

**Use**: User input with multiple fields.

**Width**: `PopupOverlay` (460.dp)

**Example**: `ui/dialogs/AddEditEventDialog.kt`

---

### Picker Dialog

**Use**: Date, color, or option selection.

**Width**: `PopupPanel` (380.dp)

**Example**: `ui/dialogs/DatePickerDialog.kt`

---

### Data Display Dialog

**Use**: Stats, summaries, read-only data.

**Width**: `PopupOverlay.wideWidth` (520.dp)

**Example**: `ui/dialogs/TaskStatsDialog.kt`

---

## Toolbar Patterns

### Toolbar Structure

```kotlin
Row(
    modifier = Modifier
        .height(EInkTheme.Toolbar.height)
        .background(EInkTheme.Toolbar.background)
        .padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    // Tab indicators
    TabIndicator(label = "Memos", isActive = tab == EditorTab.Memos)
    TabIndicator(label = "Tasks", isActive = tab == EditorTab.Tasks)

    Spacer(Modifier.width(EInkTheme.Toolbar.groupSpacing))

    // Tool buttons
    ToolButton(icon = Icons.Default.Edit, isActive = mode == Mode.Draw)
    ToolButton(icon = Icons.Default.ContentCut, isActive = mode == Mode.Select)

    Spacer(Modifier.weight(1f))

    // Right-aligned actions
    ToolButton(icon = Icons.Default.Undo, enabled = canUndo)
    ToolButton(icon = Icons.Default.Redo, enabled = canRedo)
}
```

**Reference**: `editor/ui/Toolbar.kt`

---

### Tool Button Pattern

```kotlin
@Composable
fun ToolButton(
    icon: ImageVector,
    isActive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(EInkTheme.Toolbar.buttonSize)
            .background(
                if (isActive) EInkTheme.Toolbar.buttonBackgroundSelected
                else EInkTheme.Toolbar.buttonBackground
            )
            .noRippleClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(EInkTheme.Toolbar.iconSize),
            tint = when {
                !enabled -> EInkTheme.Toolbar.iconColorDisabled
                isActive -> EInkTheme.Toolbar.iconColorSelected
                else -> EInkTheme.Toolbar.iconColor
            }
        )
    }
}
```

---

## Performance Tips

### 1. Minimize Recomposition

**Use `remember` and `derivedStateOf`**:
```kotlin
val sortedItems = remember(items, sortOrder) {
    items.sortedBy { it.priority }
}
```

### 2. Avoid Nested Scrolling

E-Ink scroll performance degrades with nesting. Use flat lists.

### 3. Use `key()` in Lists

```kotlin
LazyColumn {
    items(items, key = { it.id }) { item ->
        ItemRow(item)
    }
}
```

### 4. Debounce User Input

```kotlin
var searchQuery by remember { mutableStateOf("") }
val debouncedQuery by produceState(searchQuery, searchQuery) {
    delay(300)
    value = searchQuery
}
```

---

## Accessibility

### 1. Content Descriptions

Always provide for icons:
```kotlin
Icon(
    Icons.Default.Delete,
    contentDescription = "Delete task"
)
```

### 2. Touch Targets

Minimum 48dp (enforced by EInkTheme):
```kotlin
EInkTheme.Global.touchTarget  // 48.dp
```

### 3. Text Contrast

Use EInkTheme colors (pre-tested for E-Ink contrast):
```kotlin
EInkTheme.Colors.primary on EInkTheme.Colors.background  // Max contrast
```

---

## Testing on E-Ink

### Visual Testing Checklist

- [ ] Text readable at arm's length
- [ ] Borders visible (not washed out)
- [ ] Touch targets easy to hit with stylus
- [ ] No ghosting on popup show/hide
- [ ] Selected state clearly visible (inverted colors)
- [ ] Disabled state distinguishable (grayed out)

### Performance Testing

- [ ] Popup appears instantly (<100ms perceived)
- [ ] Tab switch <200ms
- [ ] Scroll smooth (no jank)
- [ ] Drawing responsive (<50ms stylus lag)

---

## Common Mistakes

| Mistake | Symptom | Fix |
|---------|---------|-----|
| Using `Dialog` | 500ms scrim refresh | Use `Popup` |
| Using `.clickable` | Ripple ghosting | Use `.noRippleClickable` |
| Hardcoding 360.dp | Inconsistent sizes | Use `EInkTheme.PopupOverlay.width` |
| Mid-gray backgrounds | Grainy dithering | Use black/white |
| 24dp touch targets | Hard to tap | Use 48dp |
| Text symbols | Inconsistent weights | Use vector icons |

---

## Related Documentation

- **docs/EINKTHEME_CHEATSHEET.md** - Complete theme reference
- **CLAUDE.md** - Quick E-Ink rules summary
- **memory.md** - Section 11 (E-Ink UI Styling)
- **.claude/commands/eink-theme.md** - Theme context loader
- **ui/dialogs/** - All dialog examples

---

## Key Takeaways

1. **Always Popup, never Dialog** - 500ms scrim is unacceptable
2. **Always noRippleClickable** - Ripple causes ghosting
3. **Always EInkTheme** - Hardcoded values break consistency
4. **Black/white primary** - Mid-grays only for secondary text
5. **48dp touch targets** - Accessibility and E-Ink precision
6. **Vector icons** - Text symbols have font weight issues
7. **No animations** - Static state changes feel faster on E-Ink
8. **Inverted selection** - Maximum contrast for selected state
9. **Reference ExportDialog** - Best example of modern E-Ink dialog
10. **Test on device** - Emulator doesn't show E-Ink issues

---

**Last Updated**: December 2025
**Maintainer**: Claude Code
**Questions**: Reference docs/EINKTHEME_CHEATSHEET.md or .claude/commands/eink-theme.md
