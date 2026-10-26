# E-Ink UI/UX Guide for BooxDay

Purpose
This guide turns the E-Ink constraints and `EInkTheme.kt` tokens into practical rules
for new dialogs, screens, menus, and layouts. It is focused on Boox E-Ink devices.

Use this before building any UI:
- Check sizes and typography against `app/src/main/java/com/boox/day/ui/theme/EInkTheme.kt`.
- Favor clarity and stability over density and motion.

Non-Negotiable Rules
1. Use `Popup(...)`, never `Dialog(...)`.
2. Use `noRippleClickable`, never `clickable`.
3. Use vector icons, never text symbols (x, check, arrows).
4. Avoid transparency and large mid-gray fills.
5. Avoid animations, shadows, and elevation.
6. Touch targets must be at least 42dp (prefer 44dp).

Design Principles
- Legibility over density: E-Ink contrast is lower than LCD.
- Stability over motion: E-Ink refresh is slow and ghosting is visible.
- Precision over compactness: stylus and finger input need generous targets.
- Consistency over novelty: reuse `EInkTheme` tokens instead of inventing sizes.

Layout and Sizing
- Use `EInkTheme.Global.*` for padding, spacing, borders, corner radius.
- Use `EInkTheme.MenuItem.height` for menu rows.
- Use `EInkTheme.Button.height` for dialog actions.
- Use `EInkTheme.TextField.height` for input fields.
- Use row heights from `EInkTheme.EventsPanel.rowHeight` and `EInkTheme.TasksTab.rowHeight`.
- Use toolbar sizes from `EInkTheme.Toolbar.*` for button sizing and spacing.

Typography
- Primary labels: `EInkTheme.Typography.bodyLarge` (16sp) or larger.
- Secondary text: `EInkTheme.Typography.bodyMedium` (15sp).
- Hints/captions: `EInkTheme.Typography.bodySmall` (13sp) only for non-critical text.
- Avoid `labelSmall` for anything interactive or important.

Color and Contrast
- Use `EInkTheme.Colors.primary` on `EInkTheme.Colors.background`.
- Use `darkGray/gray` only for secondary text or small icons.
- Avoid large mid-gray backgrounds that dither on E-Ink.
- Prefer solid borders (`EInkTheme.Global.borderWidth`) over shadows.

Popups and Dialogs
- Always use `Popup` with the correct size token:
  - `PopupMenu` for anchored menus.
  - `PopupCompact` for small confirmations.
  - `PopupPanel` for pickers or small forms.
  - `PopupOverlay` for larger forms or AI results.
- Use `EInkTheme.Popup*.width`, `padding`, and `borderWidth`.
- Keep menu item spacing at 0dp and use full-height rows.
- Provide a visible close control using a vector icon.

Interaction Feedback
- No ripple, no hover animations, no micro-motion.
- Use clear state changes: invert colors or add a border for selection.
- Keep state transitions instantaneous.

Performance and Refresh
- Avoid heavy recomposition in hot paths.
- Avoid per-frame logging or animation tickers.
- Prefer localized updates to avoid full-screen refreshes.

Dialog/Menu Checklist (use before shipping)
- Popup instead of Dialog.
- Width/height from `EInkTheme.Popup*`.
- Menu items use `EInkTheme.MenuItem.height`.
- Touch targets at least 42dp; 44dp preferred.
- Typography meets minimum sizes.
- No ripple, no animations, no shadows.
- Vector icons with `contentDescription`.
- High contrast: black on white, minimal mid-grays.

