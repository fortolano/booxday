# EInkTheme Cheat Sheet

> **BooxDay** - E-Ink Optimized Design System for Android/Compose
> **Last Updated**: December 2025

---

## Quick Reference

| Category | Value | Purpose |
|----------|-------|---------|
| **Primary** | `Colors.black`, `Colors.white` | Text, backgrounds, borders |
| **Secondary** | `Colors.darkGray`, `Colors.gray` | Disabled, secondary text |
| **Borders** | `1.dp`, `Colors.primary` | High contrast visibility |
| **Touch Target** | `48.dp` | Accessibility minimum |
| **Corner Radius** | `6.dp` | Consistent rounding |
| **Title Font** | `18-20.sp` | Headers, section titles |
| **Body Font** | `14-16.sp` | Primary content |
| **Label Font** | `10-14.sp` | Buttons, captions |
| **Popup Menu** | `230.dp` wide | Small anchored menus |
| **Popup Compact** | `280.dp` wide | Simple confirmations |
| **Popup Panel** | `380.dp` wide | Date/color pickers |
| **Popup Overlay** | `460.dp` wide | Full dialogs/forms |
| **Popup Wide** | `520.dp` wide | Stats, AI results |

---

## File Location

```
app/src/main/java/com/boox/day/ui/theme/EInkTheme.kt
```

---

## Theme Structure

```kotlin
EInkTheme
├── Colors              // Grayscale palette + semantic aliases
├── Typography          // Font sizes
├── Global              // App-wide defaults
├── CalendarGrid        // Calendar cells
├── EventsPanel         // Event list
├── Toolbar             // Drawing toolbar
├── MemosTab            // Memo canvas
├── TasksTab            // Task grid + Due Dates + Subtasks
├── Canvas              // Drawing surface
├── PopupMenu           // Small menus (~230dp)
├── PopupCompact        // Simple dialogs (~280dp)
├── PopupPanel          // Medium pickers (~380dp)
├── PopupOverlay        // Large dialogs (~460dp)
├── ModernDialog        // Soft gray palette dialogs
├── MenuItem            // Menu item cells
├── Button              // Action buttons
├── TextField           // Text inputs + InlineAction
├── Divider             // Separators
├── TextBoxOverlay      // Text selection + mini toolbar
├── DatePicker          // Date grid
├── TaskDatePickerDialog// Task move/copy dialog
├── TimePicker          // Time scrollers
├── ColorPicker         // Color swatches
├── SizePicker          // Size buttons
├── Toggle              // On/Off switches (standard + compact)
├── Checkbox            // Check boxes
├── StatsDialog         // Task statistics
├── BooxNotes           // Notes management dialog
├── Selection           // Inverted state
├── Disabled            // Gray state
└── Pressed             // Hover/press state
```

---

## Colors

### Primary Palette

```kotlin
EInkTheme.Colors.black      // Color.Black (#000000)
EInkTheme.Colors.white      // Color.White (#FFFFFF)
```

### Grayscale (Use Sparingly!)

```kotlin
EInkTheme.Colors.darkGray   // Color(0xFF4A4A4A) - Secondary text
EInkTheme.Colors.gray       // Color(0xFF808080) - Disabled
EInkTheme.Colors.lightGray  // Color(0xFFC0C0C0) - Thin dividers only
EInkTheme.Colors.offWhite   // Color(0xFFF5F5F5) - Alternate backgrounds
```

### Semantic Aliases (Preferred)

```kotlin
EInkTheme.Colors.primary              // = black
EInkTheme.Colors.background           // = white
EInkTheme.Colors.onBackground         // = black
EInkTheme.Colors.secondary            // = darkGray
EInkTheme.Colors.disabled             // = gray
EInkTheme.Colors.divider              // = black
EInkTheme.Colors.subtleDivider        // = lightGray
EInkTheme.Colors.alternateBackground  // = offWhite
```

> **E-Ink Rule:** Avoid mid-grays on large areas (causes dithering). Use black/white for contrast.

---

## Typography

### Headers

```kotlin
EInkTheme.Typography.titleLarge   // 20.sp - Screen titles
EInkTheme.Typography.titleMedium  // 18.sp - Section headers
EInkTheme.Typography.titleSmall   // 16.sp - Card titles
```

### Body

```kotlin
EInkTheme.Typography.bodyLarge    // 16.sp - Primary content
EInkTheme.Typography.bodyMedium   // 14.sp - Secondary content
EInkTheme.Typography.bodySmall    // 12.sp - Timestamps, hints
```

### Labels

```kotlin
EInkTheme.Typography.labelLarge   // 14.sp - Button text
EInkTheme.Typography.labelMedium  // 12.sp - Chip text
EInkTheme.Typography.labelSmall   // 10.sp - Tiny labels
```

### Semantic Aliases

```kotlin
EInkTheme.Typography.menuItem     // = bodyLarge (16.sp)
EInkTheme.Typography.button       // = labelLarge (14.sp)
EInkTheme.Typography.hint         // = bodySmall (12.sp)
EInkTheme.Typography.timestamp    // = bodySmall (12.sp)
```

---

## Global Defaults

```kotlin
EInkTheme.Global.borderWidth      // 1.dp
EInkTheme.Global.borderColor      // Colors.primary
EInkTheme.Global.touchTarget      // 48.dp (accessibility minimum)
EInkTheme.Global.iconSize         // 24.dp
EInkTheme.Global.iconSizeSmall    // 20.dp
EInkTheme.Global.iconSizeLarge    // 32.dp
EInkTheme.Global.padding          // 16.dp
EInkTheme.Global.paddingSmall     // 8.dp
EInkTheme.Global.paddingMedium    // 12.dp
EInkTheme.Global.paddingLarge     // 24.dp
EInkTheme.Global.cornerRadius     // 6.dp
EInkTheme.Global.spacing          // 8.dp
EInkTheme.Global.spacingSmall     // 4.dp
EInkTheme.Global.spacingLarge     // 16.dp
EInkTheme.Global.background       // Colors.background
EInkTheme.Global.foreground       // Colors.primary
```

---

## Popup Components

### PopupMenu (Small - 230dp)

```kotlin
EInkTheme.PopupMenu.width         // 230.dp
EInkTheme.PopupMenu.maxHeight     // 400.dp
EInkTheme.PopupMenu.background    // Colors.background
EInkTheme.PopupMenu.borderColor   // Colors.primary
EInkTheme.PopupMenu.borderWidth   // 1.dp (Global.borderWidth)
EInkTheme.PopupMenu.padding       // 8.dp
EInkTheme.PopupMenu.itemSpacing   // 0.dp
EInkTheme.PopupMenu.offsetY       // 46.dp
```

**Use for:** Settings menu, pen picker, AI menu

### PopupCompact (Small Centered - 280dp)

```kotlin
EInkTheme.PopupCompact.width         // 280.dp
EInkTheme.PopupCompact.maxHeight     // 300.dp
EInkTheme.PopupCompact.background    // Colors.background
EInkTheme.PopupCompact.borderColor   // Colors.primary
EInkTheme.PopupCompact.borderWidth   // 1.dp
EInkTheme.PopupCompact.padding       // 12.dp
EInkTheme.PopupCompact.titleFontSize // 16.sp
EInkTheme.PopupCompact.titleColor    // Colors.primary
```

**Use for:** Move task, delete confirmation, simple dialogs

### PopupPanel (Medium - 380dp)

```kotlin
EInkTheme.PopupPanel.width              // 380.dp
EInkTheme.PopupPanel.maxHeight          // 500.dp
EInkTheme.PopupPanel.background         // Colors.background
EInkTheme.PopupPanel.borderColor        // Colors.primary
EInkTheme.PopupPanel.borderWidth        // 1.dp
EInkTheme.PopupPanel.padding            // 12.dp
EInkTheme.PopupPanel.titleFontSize      // 16.sp
EInkTheme.PopupPanel.titleColor         // Colors.primary
EInkTheme.PopupPanel.titlePaddingBottom // 8.dp
```

**Use for:** Date picker, color picker

### PopupOverlay (Large - 460dp)

```kotlin
EInkTheme.PopupOverlay.width                // 460.dp
EInkTheme.PopupOverlay.maxWidth             // 480.dp
EInkTheme.PopupOverlay.wideWidth            // 520.dp (stats, AI results)
EInkTheme.PopupOverlay.maxHeight            // 580.dp
EInkTheme.PopupOverlay.background           // Colors.background
EInkTheme.PopupOverlay.borderColor          // Colors.primary
EInkTheme.PopupOverlay.borderWidth          // 1.dp
EInkTheme.PopupOverlay.padding              // 16.dp
EInkTheme.PopupOverlay.titleFontSize        // 18.sp
EInkTheme.PopupOverlay.titleColor           // Colors.primary
EInkTheme.PopupOverlay.titlePaddingBottom   // 12.dp
EInkTheme.PopupOverlay.sectionSpacing       // 16.dp
EInkTheme.PopupOverlay.buttonRowPaddingTop  // 16.dp
```

**Use for:** Event editor, settings dialog, voice input

> **Critical:** All popups use Compose `Popup` - NO Android Dialog (avoids scrim/dithering).

---

## ModernDialog (Soft Gray Palette)

Modern dialog styling with gray palette instead of harsh black/white.

### Container

```kotlin
EInkTheme.ModernDialog.background       // Colors.background
EInkTheme.ModernDialog.borderWidth      // 0.5.dp
EInkTheme.ModernDialog.borderColor      // Colors.lightGray
EInkTheme.ModernDialog.cornerRadius     // 0.dp (or 6.dp for rounded)
EInkTheme.ModernDialog.widthFraction    // 0.82f
EInkTheme.ModernDialog.padding          // 20.dp
EInkTheme.ModernDialog.contentSpacing   // 16.dp
EInkTheme.ModernDialog.titleFontSize    // 20.sp
EInkTheme.ModernDialog.titleColor       // Colors.darkGray
```

### SegmentedControl (Format Selector)

```kotlin
EInkTheme.ModernDialog.SegmentedControl.height              // 48.dp
EInkTheme.ModernDialog.SegmentedControl.borderWidth         // 0.5.dp
EInkTheme.ModernDialog.SegmentedControl.borderColor         // Colors.lightGray
EInkTheme.ModernDialog.SegmentedControl.fontSize            // 16.sp
EInkTheme.ModernDialog.SegmentedControl.textColor           // Colors.darkGray
EInkTheme.ModernDialog.SegmentedControl.textColorSelected   // Colors.white
EInkTheme.ModernDialog.SegmentedControl.background          // Colors.background
EInkTheme.ModernDialog.SegmentedControl.backgroundSelected  // Colors.darkGray
```

### OptionCard & OptionRow

```kotlin
EInkTheme.ModernDialog.OptionCard.borderWidth    // 0.5.dp
EInkTheme.ModernDialog.OptionCard.borderColor    // Colors.lightGray
EInkTheme.ModernDialog.OptionCard.background     // Colors.background

EInkTheme.ModernDialog.OptionRow.height            // 52.dp
EInkTheme.ModernDialog.OptionRow.paddingHorizontal // 16.dp
EInkTheme.ModernDialog.OptionRow.paddingVertical   // 14.dp
EInkTheme.ModernDialog.OptionRow.fontSize          // 16.sp
EInkTheme.ModernDialog.OptionRow.textColor         // Colors.darkGray
EInkTheme.ModernDialog.OptionRow.textColorSelected // Colors.primary
```

### RadioIndicator

```kotlin
EInkTheme.ModernDialog.RadioIndicator.size         // 18.dp
EInkTheme.ModernDialog.RadioIndicator.borderWidth  // 1.dp
EInkTheme.ModernDialog.RadioIndicator.borderColor  // Colors.darkGray
EInkTheme.ModernDialog.RadioIndicator.cornerRadius // 0.dp (square) or 9.dp (circle)
EInkTheme.ModernDialog.RadioIndicator.fillColor    // Colors.darkGray
EInkTheme.ModernDialog.RadioIndicator.innerPadding // 3.dp
```

### PrimaryButton & CancelLink

```kotlin
EInkTheme.ModernDialog.PrimaryButton.height       // 48.dp
EInkTheme.ModernDialog.PrimaryButton.cornerRadius // 0.dp
EInkTheme.ModernDialog.PrimaryButton.fontSize     // 16.sp
EInkTheme.ModernDialog.PrimaryButton.background   // Colors.darkGray
EInkTheme.ModernDialog.PrimaryButton.textColor    // Colors.white

EInkTheme.ModernDialog.CancelLink.fontSize        // 14.sp
EInkTheme.ModernDialog.CancelLink.textColor       // Colors.gray
EInkTheme.ModernDialog.CancelLink.paddingVertical // 8.dp
```

**Use for:** Export dialog, settings dialogs where a softer feel is desired

---

## Toolbar

```kotlin
EInkTheme.Toolbar.height                   // 48.dp
EInkTheme.Toolbar.background               // Colors.background
EInkTheme.Toolbar.buttonSize               // 40.dp
EInkTheme.Toolbar.iconSize                 // 24.dp
EInkTheme.Toolbar.iconColor                // Colors.primary
EInkTheme.Toolbar.iconColorSelected        // Colors.background
EInkTheme.Toolbar.iconColorDisabled        // Colors.gray
EInkTheme.Toolbar.buttonBackground         // Colors.background
EInkTheme.Toolbar.buttonBackgroundSelected // Colors.primary
EInkTheme.Toolbar.spacing                  // 4.dp
EInkTheme.Toolbar.groupSpacing             // 8.dp
EInkTheme.Toolbar.tabIndicatorHeight       // 3.dp
EInkTheme.Toolbar.tabIndicatorColor        // Colors.primary
EInkTheme.Toolbar.dividerWidth             // 0.5.dp
EInkTheme.Toolbar.dividerHeight            // 32.dp
EInkTheme.Toolbar.dividerColor             // Colors.white (currently hidden)
```

---

## MenuItem

```kotlin
EInkTheme.MenuItem.height              // 48.dp
EInkTheme.MenuItem.fontSize            // 16.sp
EInkTheme.MenuItem.textColor           // Colors.primary
EInkTheme.MenuItem.textColorDisabled   // Colors.disabled
EInkTheme.MenuItem.iconSize            // 24.dp
EInkTheme.MenuItem.iconColor           // Colors.primary
EInkTheme.MenuItem.iconColorDisabled   // Colors.disabled
EInkTheme.MenuItem.horizontalPadding   // 16.dp
EInkTheme.MenuItem.iconTextSpacing     // 12.dp
EInkTheme.MenuItem.selectedBackground  // Colors.primary
EInkTheme.MenuItem.selectedTextColor   // Colors.background
EInkTheme.MenuItem.selectedIconColor   // Colors.background
```

---

## Button

### Secondary (Outlined)

```kotlin
EInkTheme.Button.height             // 48.dp
EInkTheme.Button.minWidth           // 80.dp
EInkTheme.Button.fontSize           // 14.sp
EInkTheme.Button.horizontalPadding  // 16.dp
EInkTheme.Button.spacing            // 8.dp
EInkTheme.Button.background         // Colors.background
EInkTheme.Button.textColor          // Colors.primary
EInkTheme.Button.borderWidth        // 1.dp
EInkTheme.Button.borderColor        // Colors.primary
```

### Primary (Filled)

```kotlin
EInkTheme.Button.primaryBackground  // Colors.primary
EInkTheme.Button.primaryTextColor   // Colors.background
EInkTheme.Button.primaryBorderWidth // 1.dp
EInkTheme.Button.primaryBorderColor // Colors.primary
```

### Disabled

```kotlin
EInkTheme.Button.disabledBackground  // Colors.background
EInkTheme.Button.disabledTextColor   // Colors.disabled
EInkTheme.Button.disabledBorderColor // Colors.disabled
```

---

## TextField

```kotlin
EInkTheme.TextField.height              // 48.dp
EInkTheme.TextField.fontSize            // 16.sp
EInkTheme.TextField.textColor           // Colors.primary
EInkTheme.TextField.hintColor           // Colors.gray
EInkTheme.TextField.background          // Colors.background
EInkTheme.TextField.borderWidth         // 1.dp
EInkTheme.TextField.borderColor         // Colors.darkGray
EInkTheme.TextField.focusedBorderWidth  // 2.dp
EInkTheme.TextField.focusedBorderColor  // Colors.primary
EInkTheme.TextField.padding             // 12.dp
EInkTheme.TextField.labelFontSize       // 12.sp
EInkTheme.TextField.labelColor          // Colors.darkGray
```

### InlineAction (Buttons next to text fields)

```kotlin
EInkTheme.TextField.InlineAction.buttonSize  // 44.dp
EInkTheme.TextField.InlineAction.iconSize    // 22.dp
EInkTheme.TextField.InlineAction.iconColor   // Colors.secondary
EInkTheme.TextField.InlineAction.spacing     // 4.dp
```

---

## States

### Selection (Inverted)

```kotlin
EInkTheme.Selection.background  // Colors.primary (black)
EInkTheme.Selection.foreground  // Colors.background (white)
```

### Disabled

```kotlin
EInkTheme.Disabled.foreground   // Colors.disabled (gray)
EInkTheme.Disabled.background   // Colors.background (white)
EInkTheme.Disabled.alpha        // 0.5f
```

### Pressed

```kotlin
EInkTheme.Pressed.background    // Colors.offWhite
EInkTheme.Pressed.foreground    // Colors.primary
```

---

## CalendarGrid

```kotlin
EInkTheme.CalendarGrid.cellSize               // 48.dp
EInkTheme.CalendarGrid.cellPadding            // 4.dp
EInkTheme.CalendarGrid.dayFontSize            // 14.sp
EInkTheme.CalendarGrid.dayColor               // Colors.primary
EInkTheme.CalendarGrid.dayColorOutsideMonth   // Colors.gray
EInkTheme.CalendarGrid.weekdayHeaderHeight    // 32.dp
EInkTheme.CalendarGrid.weekdayFontSize        // 12.sp
EInkTheme.CalendarGrid.weekdayColor           // Colors.gray
EInkTheme.CalendarGrid.monthHeaderHeight      // 48.dp
EInkTheme.CalendarGrid.monthFontSize          // 18.sp
EInkTheme.CalendarGrid.monthColor             // Colors.primary
EInkTheme.CalendarGrid.todayBorderWidth       // 1.dp
EInkTheme.CalendarGrid.todayBorderColor       // Colors.primary
EInkTheme.CalendarGrid.todayBackground        // Color(0xFFE0E0E0)
EInkTheme.CalendarGrid.todayTextColor         // Colors.primary
EInkTheme.CalendarGrid.todaySelectedBackground // Colors.gray
EInkTheme.CalendarGrid.todaySelectedTextColor  // Colors.background
EInkTheme.CalendarGrid.memoIconSize           // 8.dp
EInkTheme.CalendarGrid.memoIconSizeSelected   // 10.dp
EInkTheme.CalendarGrid.memoIconColor          // Colors.primary
EInkTheme.CalendarGrid.taskIconSize           // 8.dp
EInkTheme.CalendarGrid.taskIconSizeSelected   // 10.dp
EInkTheme.CalendarGrid.eventIndicatorWidth    // 10.dp
EInkTheme.CalendarGrid.eventIndicatorHeight   // 2.dp
EInkTheme.CalendarGrid.selectedBackground     // Colors.primary
EInkTheme.CalendarGrid.selectedForeground     // Colors.background
```

---

## EventsPanel

```kotlin
EInkTheme.EventsPanel.widthFraction       // 0.4f (40% of header)
EInkTheme.EventsPanel.headerHeight        // 40.dp
EInkTheme.EventsPanel.headerFontSize      // 16.sp
EInkTheme.EventsPanel.headerColor         // Colors.primary
EInkTheme.EventsPanel.rowHeight           // 56.dp
EInkTheme.EventsPanel.rowPadding          // 8.dp
EInkTheme.EventsPanel.rowBackground       // Colors.background
EInkTheme.EventsPanel.rowBackgroundAlt    // Colors.offWhite
EInkTheme.EventsPanel.rowDividerColor     // Colors.lightGray
EInkTheme.EventsPanel.rowDividerThickness // 1.dp
EInkTheme.EventsPanel.timeColumnWidth     // 60.dp
EInkTheme.EventsPanel.timeFontSize        // 12.sp
EInkTheme.EventsPanel.timeColor           // Colors.darkGray
EInkTheme.EventsPanel.titleFontSize       // 14.sp
EInkTheme.EventsPanel.titleColor          // Colors.primary
EInkTheme.EventsPanel.descriptionFontSize // 12.sp
EInkTheme.EventsPanel.descriptionColor    // Colors.darkGray
EInkTheme.EventsPanel.emptyStateIconSize  // 48.dp
EInkTheme.EventsPanel.emptyStateTextSize  // 14.sp
EInkTheme.EventsPanel.emptyStateColor     // Colors.gray
```

---

## TasksTab

### Basic Layout

```kotlin
EInkTheme.TasksTab.background                // Colors.background
EInkTheme.TasksTab.controlColumnWidth        // 146.dp
EInkTheme.TasksTab.controlColumnBackground   // Colors.background
EInkTheme.TasksTab.controlColumnBorderWidth  // 1.dp
EInkTheme.TasksTab.controlColumnBorderColor  // Colors.lightGray
EInkTheme.TasksTab.rowHeight                 // 56.dp
EInkTheme.TasksTab.rowCount                  // 10
EInkTheme.TasksTab.rowDividerColor           // Colors.lightGray
EInkTheme.TasksTab.rowDividerThickness       // 1.dp
EInkTheme.TasksTab.checkboxSize              // 24.dp
EInkTheme.TasksTab.checkboxBorderWidth       // 1.dp
EInkTheme.TasksTab.checkboxColor             // Colors.primary
EInkTheme.TasksTab.checkboxCheckedBackground // Colors.primary
EInkTheme.TasksTab.checkboxCheckedForeground // Colors.background
EInkTheme.TasksTab.priorityButtonSize        // 32.dp
EInkTheme.TasksTab.priorityIconSize          // 20.dp
EInkTheme.TasksTab.expandButtonSize          // 24.dp
```

### Compact Layout (Due Dates Feature)

```kotlin
// Layout: 2dp + 40dp + 2dp + 26dp + 3dp + 30dp + 2dp + 40dp + 1dp = 146dp
EInkTheme.TasksTab.controlColumnWidthWithDueDates // 146.dp

// Touch targets
EInkTheme.TasksTab.checkboxTouchCompact   // 40.dp
EInkTheme.TasksTab.priorityTouchCompact   // 26.dp
EInkTheme.TasksTab.calendarTouchCompact   // 30.dp
EInkTheme.TasksTab.chevronTouchCompact    // 40.dp

// Icon sizes (visual)
EInkTheme.TasksTab.checkboxIconCompact    // 22.dp
EInkTheme.TasksTab.priorityIconCompact    // 23.dp
EInkTheme.TasksTab.calendarIconCompact    // 29.dp
EInkTheme.TasksTab.chevronIconCompact     // 30.dp

// Gaps
EInkTheme.TasksTab.compactGap                // 2.dp
EInkTheme.TasksTab.compactGapAfterCheckbox   // 2.dp
EInkTheme.TasksTab.compactGapAfterPriority   // 3.dp
EInkTheme.TasksTab.compactPaddingStart       // 2.dp
EInkTheme.TasksTab.compactPaddingEnd         // 1.dp

// Due date icons
EInkTheme.TasksTab.dueDateIconColorEmpty     // Colors.gray
EInkTheme.TasksTab.dueDateIconColorSet       // Colors.primary
EInkTheme.TasksTab.dueDateIconColorOverdue   // Colors.primary
```

### Completed Task Styling

```kotlin
EInkTheme.TasksTab.completedRowAlpha              // 0.5f
EInkTheme.TasksTab.completedTextColor             // Colors.gray
EInkTheme.TasksTab.completedTypedTextColor        // Colors.lightGray
EInkTheme.TasksTab.completedStrikethroughColor    // Colors.gray
EInkTheme.TasksTab.completedStrikethroughThickness // 1.5.dp
EInkTheme.TasksTab.completedRowOverlayAlpha       // 0.06f
EInkTheme.TasksTab.completedStrokeColor           // Colors.lightGray
```

### Typed Text Styling

```kotlin
EInkTheme.TasksTab.typedTextFontSize      // 20.sp
EInkTheme.TasksTab.typedTextColor         // Colors.primary
EInkTheme.TasksTab.typedTextPaddingStart  // 30.dp
EInkTheme.TasksTab.typedTextPaddingEnd    // 8.dp
EInkTheme.TasksTab.typedTextMaxLength     // 60
```

### Subtask Styling

```kotlin
EInkTheme.TasksTab.Subtask.fontSize       // 16.sp
EInkTheme.TasksTab.Subtask.textColor      // Colors.darkGray
EInkTheme.TasksTab.Subtask.paddingStart   // 48.dp
```

---

## Pickers

### DatePicker

```kotlin
EInkTheme.DatePicker.width                  // 380.dp (PopupPanel.width)
EInkTheme.DatePicker.cellSize               // 36.dp
EInkTheme.DatePicker.cellFontSize           // 14.sp
EInkTheme.DatePicker.cellColor              // Colors.primary
EInkTheme.DatePicker.cellColorOutsideMonth  // Colors.gray
EInkTheme.DatePicker.headerHeight           // 40.dp
EInkTheme.DatePicker.headerFontSize         // 16.sp
EInkTheme.DatePicker.headerColor            // Colors.primary
EInkTheme.DatePicker.weekdayFontSize        // 12.sp
EInkTheme.DatePicker.weekdayColor           // Colors.gray
EInkTheme.DatePicker.selectedBackground     // Colors.primary
EInkTheme.DatePicker.selectedForeground     // Colors.background
EInkTheme.DatePicker.todayBorderWidth       // 2.dp
EInkTheme.DatePicker.todayBorderColor       // Colors.primary
```

### TaskDatePickerDialog (Move/Copy Tasks)

```kotlin
// Dialog container
EInkTheme.TaskDatePickerDialog.dialogWidth        // 360.dp
EInkTheme.TaskDatePickerDialog.dialogCornerRadius // 6.dp
EInkTheme.TaskDatePickerDialog.dialogPadding      // 12.dp
EInkTheme.TaskDatePickerDialog.dialogBorderWidth  // 1.dp
EInkTheme.TaskDatePickerDialog.dialogBorderColor  // Colors.primary

// Navigation
EInkTheme.TaskDatePickerDialog.navButtonSize      // 40.dp
EInkTheme.TaskDatePickerDialog.navIconSize        // 25.dp
EInkTheme.TaskDatePickerDialog.monthYearFontSize  // 16.sp

// Calendar grid
EInkTheme.TaskDatePickerDialog.cellSize           // 36.dp
EInkTheme.TaskDatePickerDialog.cellFontSize       // 12.sp
EInkTheme.TaskDatePickerDialog.gridHeight         // 221.dp

// Cell states
EInkTheme.TaskDatePickerDialog.cellSelectedBackground    // Selection.background
EInkTheme.TaskDatePickerDialog.cellSelectedForeground    // Selection.foreground
EInkTheme.TaskDatePickerDialog.cellCurrentDateBackground // Colors.lightGray

// Delete button
EInkTheme.TaskDatePickerDialog.deleteButtonBackground // Color(0xFF8B0000) (dark red)
EInkTheme.TaskDatePickerDialog.deleteButtonTextColor  // Colors.white
```

### TimePicker

```kotlin
EInkTheme.TimePicker.columnWidth         // 60.dp
EInkTheme.TimePicker.rowHeight           // 40.dp
EInkTheme.TimePicker.fontSize            // 16.sp
EInkTheme.TimePicker.textColor           // Colors.primary
EInkTheme.TimePicker.selectedBackground  // Colors.primary
EInkTheme.TimePicker.selectedTextColor   // Colors.background
EInkTheme.TimePicker.separatorFontSize   // 18.sp
```

### ColorPicker

```kotlin
EInkTheme.ColorPicker.swatchSize          // 40.dp
EInkTheme.ColorPicker.swatchSpacing       // 6.dp
EInkTheme.ColorPicker.swatchBorderWidth   // 2.dp
EInkTheme.ColorPicker.swatchBorderColor   // Colors.primary
EInkTheme.ColorPicker.selectedBorderWidth // 3.dp
```

### SizePicker

```kotlin
EInkTheme.SizePicker.buttonSize           // 40.dp
EInkTheme.SizePicker.buttonSpacing        // 6.dp
EInkTheme.SizePicker.buttonBorderWidth    // 2.dp
EInkTheme.SizePicker.buttonBorderColor    // Colors.primary
EInkTheme.SizePicker.labelFontSize        // 14.sp
EInkTheme.SizePicker.labelColor           // Colors.primary
EInkTheme.SizePicker.selectedBackground   // Colors.primary
EInkTheme.SizePicker.selectedLabelColor   // Colors.background
```

---

## Controls

### Toggle (Standard with ON/OFF labels)

```kotlin
EInkTheme.Toggle.width              // 56.dp
EInkTheme.Toggle.height             // 28.dp
EInkTheme.Toggle.thumbSize          // 22.dp
EInkTheme.Toggle.cornerRadius       // 14.dp (pill shape)
EInkTheme.Toggle.borderWidth        // 2.dp
EInkTheme.Toggle.thumbBorderWidth   // 1.dp
EInkTheme.Toggle.labelPadding       // 6.dp
EInkTheme.Toggle.thumbPadding       // 3.dp

// Colors (pure black/white for E-Ink)
EInkTheme.Toggle.trackColorOff      // Colors.white
EInkTheme.Toggle.trackColorOn       // Colors.black
EInkTheme.Toggle.thumbColorOff      // Colors.black
EInkTheme.Toggle.thumbColorOn       // Colors.white
EInkTheme.Toggle.borderColor        // Colors.black
EInkTheme.Toggle.labelColor         // Colors.black (OFF state)
EInkTheme.Toggle.labelColorOn       // Colors.white (ON state)
EInkTheme.Toggle.labelFontSize      // 10.sp
```

### Toggle Compact (No labels)

```kotlin
EInkTheme.Toggle.compactWidth       // 44.dp
EInkTheme.Toggle.compactHeight      // 24.dp
EInkTheme.Toggle.compactThumbSize   // 18.dp
EInkTheme.Toggle.compactCornerRadius // 12.dp
```

### Toggle Disabled State

```kotlin
EInkTheme.Toggle.disabledTrackColor  // Colors.lightGray
EInkTheme.Toggle.disabledThumbColor  // Colors.gray
EInkTheme.Toggle.disabledBorderColor // Colors.gray
```

### Checkbox

```kotlin
EInkTheme.Checkbox.size              // 24.dp
EInkTheme.Checkbox.borderWidth       // 2.dp
EInkTheme.Checkbox.borderColor       // Colors.primary
EInkTheme.Checkbox.checkedBackground // Colors.primary
EInkTheme.Checkbox.checkmarkColor    // Colors.background
```

### Divider

```kotlin
EInkTheme.Divider.color           // Colors.divider (black)
EInkTheme.Divider.thickness       // 1.dp
EInkTheme.Divider.subtleColor     // Colors.subtleDivider (lightGray)
EInkTheme.Divider.subtleThickness // 1.dp
```

---

## TextBoxOverlay

```kotlin
EInkTheme.TextBoxOverlay.handleSize               // 10.dp
EInkTheme.TextBoxOverlay.handleColor              // Colors.primary
EInkTheme.TextBoxOverlay.borderWidth              // 0.5.dp
EInkTheme.TextBoxOverlay.borderColor              // Colors.primary
EInkTheme.TextBoxOverlay.miniToolbarHeightAdvanced // 62.dp (2-row edit mode)
EInkTheme.TextBoxOverlay.miniToolbarHeightBasic   // 32.dp (1-row select mode)
EInkTheme.TextBoxOverlay.miniToolbarBackground    // Colors.background
EInkTheme.TextBoxOverlay.miniToolbarBorderWidth   // 0.5.dp
EInkTheme.TextBoxOverlay.miniToolbarBorderColor   // Colors.primary
EInkTheme.TextBoxOverlay.miniToolbarButtonSize    // 40.dp
EInkTheme.TextBoxOverlay.miniToolbarButtonSpacing // 4.dp
```

---

## StatsDialog (Task Statistics)

```kotlin
// Dialog container
EInkTheme.StatsDialog.width           // 580.dp
EInkTheme.StatsDialog.padding         // 20.dp
EInkTheme.StatsDialog.cornerRadius    // 6.dp

// Typography (enlarged for E-Ink)
EInkTheme.StatsDialog.titleFontSize        // 22.sp
EInkTheme.StatsDialog.sectionTitleFontSize // 18.sp
EInkTheme.StatsDialog.valueFontSize        // 20.sp
EInkTheme.StatsDialog.labelFontSize        // 16.sp
EInkTheme.StatsDialog.smallLabelFontSize   // 14.sp

// Streak section
EInkTheme.StatsDialog.streakBadgeSize      // 56.dp
EInkTheme.StatsDialog.streakBadgeIconSize  // 28.sp
EInkTheme.StatsDialog.streakTitleFontSize  // 20.sp
EInkTheme.StatsDialog.streakMessageFontSize // 14.sp

// Mini stat boxes
EInkTheme.StatsDialog.miniStatHeight       // 44.dp
EInkTheme.StatsDialog.miniStatColumnWidth  // 160.dp
EInkTheme.StatsDialog.miniStatRowSpacing   // 8.dp

// Progress bars
EInkTheme.StatsDialog.progressBarHeight    // 24.dp
EInkTheme.StatsDialog.periodLabelWidth     // 85.dp
EInkTheme.StatsDialog.periodRowSpacing     // 10.dp

// Personal bests
EInkTheme.StatsDialog.bestBadgeSize        // 36.dp
EInkTheme.StatsDialog.bestCountFontSize    // 22.sp
EInkTheme.StatsDialog.bestRowHeight        // 52.dp

// Layout
EInkTheme.StatsDialog.columnSpacing        // 16.dp
EInkTheme.StatsDialog.sectionSpacing       // 16.dp
EInkTheme.StatsDialog.buttonHeight         // 52.dp
```

---

## BooxNotes (Notes Management Dialog)

```kotlin
// Dialog sizing
EInkTheme.BooxNotes.dialogWidthFraction   // 0.88f
EInkTheme.BooxNotes.dialogHeightFraction  // 0.72f

// Header
EInkTheme.BooxNotes.headerHeight          // 48.dp
EInkTheme.BooxNotes.headerFontSize        // 18.sp

// Search bar
EInkTheme.BooxNotes.searchBarHeight       // 40.dp
EInkTheme.BooxNotes.searchBarBackground   // Colors.offWhite
EInkTheme.BooxNotes.searchIconSize        // 20.dp

// Filter chips
EInkTheme.BooxNotes.filterChipHeight               // 32.dp
EInkTheme.BooxNotes.filterChipSelectedBackground   // Colors.primary
EInkTheme.BooxNotes.filterChipSelectedTextColor    // Colors.background

// Section headers
EInkTheme.BooxNotes.sectionHeaderHeight     // 36.dp
EInkTheme.BooxNotes.sectionHeaderFontSize   // 16.sp
EInkTheme.BooxNotes.sectionHeaderBackground // Colors.offWhite

// Note cards
EInkTheme.BooxNotes.noteCardHeight           // 72.dp
EInkTheme.BooxNotes.noteCardSelectedBorderWidth // 2.dp
EInkTheme.BooxNotes.thumbnailSize            // 52.dp

// Status colors
EInkTheme.BooxNotes.statusAttentionColor     // Color(0xFFE57373) (soft red)
EInkTheme.BooxNotes.statusLinkedColor        // Color(0xFF81C784) (soft green)

// Confidence badge
EInkTheme.BooxNotes.confidenceHighBackground   // Color(0xFF81C784)
EInkTheme.BooxNotes.confidenceMediumBackground // Color(0xFFFFD54F)
EInkTheme.BooxNotes.confidenceLowBackground    // Color(0xFFE57373)
```

---

## Usage Example

```kotlin
import com.boox.day.ui.theme.EInkTheme

@Composable
fun MyPopupMenu() {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(EInkTheme.MenuItem.height)
                .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(EInkTheme.MenuItem.iconSize),
                tint = EInkTheme.MenuItem.iconColor
            )
            Spacer(Modifier.width(EInkTheme.MenuItem.iconTextSpacing))
            Text(
                "Settings",
                fontSize = EInkTheme.MenuItem.fontSize,
                color = EInkTheme.MenuItem.textColor
            )
        }
    }
}
```

---

## E-Ink Design Principles

1. **High Contrast** - Use black/white, avoid mid-grays on large areas
2. **No Shadows** - Elevation causes ghosting; use borders instead
3. **No Scrim** - Use Compose `Popup`, not Android Dialog
4. **Large Touch Targets** - Minimum 48dp for stylus/finger input
5. **Thick Borders** - 1-2dp for visibility on low-contrast displays
6. **Larger Text** - E-Ink has lower contrast than LCD
7. **Static UI** - Avoid animations (cause full refreshes)
8. **Inverted Selection** - Black background + white text for feedback
9. **No Ripple** - Use `noRippleClickable` instead of `clickable`
10. **Vector Icons** - Use Material Icons, not text symbols

---

## Files Using EInkTheme

### Dialogs
- `AddEditEventDialog.kt`, `EventDetailsDialog.kt`
- `TextStyleDialog.kt`, `GeneralSettingsDialog.kt`
- `VoiceToTaskDialog.kt`, `TypeTaskDialog.kt`
- `ExportDialog.kt`, `TaskStatsDialog.kt`

### Menus & Popups
- `SettingsMenu.kt`, `PageMenu.kt`, `StrokeMenu.kt`
- `TaskDatePickerDialog.kt`, `BooxNotesDialog.kt`

### Toolbar
- `Toolbar.kt`, `ToolbarButton.kt`, `ToolbarMenu.kt`

### Overlays
- `TextBoxOverlay.kt`, `TasksOverlay.kt`

### Main UI
- `CalendarView.kt`, `Select.kt`, `MainScreen.kt`

### Components
- `EinkButtons.kt`, `EinkLoadingDots.kt`, `EInkToggle.kt`

... and 50+ more components
