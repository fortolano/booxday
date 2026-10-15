package com.boox.day.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Popup limits from the real screen (Oct 2026; were 400 x 520 dp on narrow screens, sized for a Go 6
 * assumed to be 415 x 561 dp, really 572 x 772 dp): a 16 dp margin on each side, and at most 82% of
 * the screen height. Go 6: 540 x 633 dp; Go 10.3 (773 x 1031 dp at 385 dpi): 741 x 845 dp.
 */
private val SCREEN_POPUP_MAX_WIDTH: Dp get() = (LayoutProfiles.screenWidthDp - 32f).dp
private val SCREEN_POPUP_MAX_HEIGHT: Dp get() = (LayoutProfiles.screenHeightDp * 0.82f).dp

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                                                                              ║
 * ║   🚨🚨🚨  MANDATORY SIZING RULES - READ BEFORE WRITING ANY UI  🚨🚨🚨        ║
 * ║                                                                              ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║                                                                              ║
 * ║   THESE RULES ARE NON-NEGOTIABLE. VIOLATION = BROKEN UI ON E-INK DEVICES.   ║
 * ║                                                                              ║
 * ║   ┌─────────────────────────────────────────────────────────────────────┐    ║
 * ║   │  RULE A: MINIMUM 42dp TOUCH TARGETS - NO EXCEPTIONS                 │    ║
 * ║   │                                                                     │    ║
 * ║   │  ALL clickable elements MUST be at least 28dp x 28dp to 42dp × 42dp:│    ║
 * ║   │                                                                     │    ║
 * ║   │  ❌ NEVER use smaller values like paddingSmall to size buttons      │    ║
 * ║   │  ❌ NEVER assume "it looks fine" - MEASURE IT                       │    ║
 * ║   └─────────────────────────────────────────────────────────────────────┘    ║
 * ║                                                                              ║
 * ║   ┌─────────────────────────────────────────────────────────────────────┐    ║
 * ║   │  RULE B: MINIMUM FONT SIZES - TEXT MUST BE READABLE                 │    ║
 * ║   │                                                                     │    ║
 * ║   │  E-Ink has lower contrast than LCD. Use these MINIMUM sizes:        │    ║
 * ║   │  ✓ Primary text (buttons, labels): bodyLarge (16.sp) or larger      │    ║
 * ║   │  ✓ Secondary text (descriptions): bodyMedium (15.sp) minimum        │    ║
 * ║   │  ✓ Hints/captions: bodySmall (13.sp) - ONLY for non-critical text   │    ║
 * ║   │                                                                     │    ║
 * ║   │  ❌ NEVER use labelSmall (12.sp) for important text                 │    ║
 * ║   │  ❌ NEVER use bodySmall (13.sp) for button/chip labels              │    ║
 * ║   │  ❌ NEVER shrink fonts to "fit more content"                        │    ║
 * ║   └─────────────────────────────────────────────────────────────────────┘    ║
 * ║                                                                              ║
 * ║   ┌─────────────────────────────────────────────────────────────────────┐    ║
 * ║   │  RULE C: USE STANDARD COMPONENT HEIGHTS                             │    ║
 * ║   │                                                                     │    ║
 * ║   │  When creating new UI, use these pre-defined heights:               │    ║
 * ║   │  ✓ Buttons: Button.height (44.dp)                                   │    ║
 * ║   │  ✓ Menu items: MenuItem.height (44.dp)                              │    ║
 * ║   │  ✓ Text fields: TextField.height (44.dp)                            │    ║
 * ║   │  ✓ List rows: 56.dp minimum (EventsPanel.rowHeight, TasksTab.rowHeight)│ ║
 * ║   │                                                                     │    ║
 * ║   │  ❌ NEVER invent new smaller heights                                │    ║
 * ║   │  ❌ NEVER use padding alone to determine size - set explicit height │    ║
 * ║   └─────────────────────────────────────────────────────────────────────┘    ║
 * ║                                                                              ║
 * ║   ┌─────────────────────────────────────────────────────────────────────┐    ║
 * ║   │  QUICK REFERENCE - CORRECT PATTERNS                                 │    ║
 * ║   │                                                                     │    ║
 * ║   │  // ✅ CORRECT - Button with proper sizing                          │    ║
 * ║   │  Box(                                                               │    ║
 * ║   │      modifier = Modifier                                            │    ║
 * ║   │          .height(EInkTheme.Global.touchTarget)  // 44dp             │    ║
 * ║   │          .padding(horizontal = EInkTheme.Global.padding)            │    ║
 * ║   │  ) {                                                                │    ║
 * ║   │      Text(text, fontSize = EInkTheme.Typography.bodyLarge)  // 16sp │    ║
 * ║   │  }                                                                  │    ║
 * ║   │                                                                     │    ║
 * ║   │  // ❌ WRONG - Too small, text unreadable                           │    ║
 * ║   │  Box(                                                               │    ║
 * ║   │      modifier = Modifier                                            │    ║
 * ║   │          .padding(EInkTheme.Global.paddingSmall)  // NO explicit ht │    ║
 * ║   │  ) {                                                                │    ║
 * ║   │      Text(text, fontSize = EInkTheme.Typography.bodySmall)  // 13sp │    ║
 * ║   │  }                                                                  │    ║
 * ║   └─────────────────────────────────────────────────────────────────────┘    ║
 * ║                                                                              ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 *
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  ⚠️  E-INK DESIGN SYSTEM - CRITICAL GUIDELINES FOR LLMs & DEVELOPERS  ⚠️     ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║  This app runs on Onyx Boox E-Ink tablets. E-Ink displays have FUNDAMENTALLY ║
 * ║  different rendering characteristics than LCD/OLED. Ignoring these rules     ║
 * ║  will cause visible bugs: flickering, ghosting, slow UI, and poor UX.        ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * RULE 1: NEVER USE ANDROID DIALOG - USE COMPOSE POPUP INSTEAD
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * ❌ WRONG:
 *    Dialog(onDismissRequest = {}) { content }
 *    AlertDialog.Builder(context).setTitle("...").show()
 *
 * ✅ CORRECT:
 *    Popup(alignment = Alignment.Center, onDismissRequest = {}) { content }
 *
 * WHY: Android Dialog creates a separate Window with a SCRIM (semi-transparent
 * dark overlay). On E-Ink displays:
 *   - Scrim causes full-screen GC (Global Clear) refresh (~500ms flash)
 *   - Scrim renders as dithered gray pattern (ugly on E-Ink)
 *   - Window transitions cause additional refresh cycles
 *
 * Compose Popup renders in the same composition tree with NO scrim, NO separate
 * window, and only refreshes the popup region (~50ms, no flash).
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * RULE 2: NEVER USE RIPPLE - USE noRippleClickable INSTEAD
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * ❌ WRONG:
 *    Modifier.clickable { onClick() }
 *
 * ✅ CORRECT:
 *    Modifier.noRippleClickable { onClick() }
 *
 * WHY: Ripple is a circular animation that expands from the tap point. On E-Ink:
 *   - E-Ink refreshes at ~15Hz (vs 60Hz LCD) - can't render smooth animations
 *   - Ripple causes visible ghosting and "smearing" artifacts
 *   - Multiple partial refreshes degrade display quality
 *
 * Import: import com.boox.day.ui.noRippleClickable
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * RULE 3: USE VECTOR DRAWABLE ICONS INSTEAD OF TEXT SYMBOLS
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * ❌ WRONG:
 *    Text("✕", fontSize = 18.sp)  // Close
 *    Text("✓", fontSize = 18.sp)  // Check/Confirm
 *
 * ✅ CORRECT:
 *    Icon(Icons.Default.Close, contentDescription = "Close")
 *    Icon(Icons.Default.Check, contentDescription = "Confirm")
 *
 * WHY:
 *   - Text symbols often have inconsistent line weights and alignment across fonts
 *   - Vector drawables ensure consistent stroke width and visual weight
 *   - Material Icons standardizes the visual language
 *   - Better accessibility support via contentDescription
 *
 * COMMON REPLACEMENTS:
 *   Text("✕")  -> Icon(Icons.Default.Close, ...)
 *   Text("✓")  -> Icon(Icons.Default.Check, ...)
 *   Text("→")  -> Icon(Icons.AutoMirrored.Filled.ArrowForward, ...)
 *   Text("←")  -> Icon(Icons.AutoMirrored.Filled.ArrowBack, ...)
 *   Text("★")  -> Icon(Icons.Default.Star, ...)
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * RULE 4: AVOID TRANSPARENCY AND MID-GRAYS ON LARGE AREAS
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * ❌ WRONG:
 *    Modifier.background(Color.Black.copy(alpha = 0.5f))  // Semi-transparent
 *    Modifier.background(Color(0xFF808080))               // Mid-gray on large area
 *
 * ✅ CORRECT:
 *    Modifier.background(Color.White)                     // Pure white
 *    Modifier.background(Color.Black)                     // Pure black
 *    // Use mid-grays ONLY for small elements (thin dividers, small icons)
 *
 * WHY: E-Ink displays have limited grayscale levels (typically 16). Colors that
 * fall between levels are rendered using DITHERING (checkerboard patterns of
 * black and white pixels). On large areas, this looks noisy and unprofessional.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * RULE 5: NO ANIMATIONS, SHADOWS, OR ELEVATION
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * ❌ WRONG:
 *    Modifier.shadow(elevation = 4.dp)
 *    Modifier.animateContentSize()
 *    AnimatedVisibility(visible) { content }
 *
 * ✅ CORRECT:
 *    Modifier.border(EInkTheme.Global.borderWidth, Color.Black)  // Use solid borders instead of shadows
 *    // Show/hide content immediately without animation
 *
 * WHY:
 *   - Shadows use gradients → dithering on E-Ink
 *   - Animations require rapid screen updates → ghosting, flicker
 *   - E-Ink refresh is inherently slow; animations make it worse
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * RULE 6: RANGE 28dp to 42dp TOUCH TARGETS
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * All interactive elements must be at least 28dp x 28dp to 42dp × 42dp for accessibility and
 * accurate stylus/finger input on E-Ink devices (which may have slight input lag).
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * QUICK REFERENCE - E-INK SAFE PATTERNS
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * // E-Ink optimized popup:
 * Popup(alignment = Alignment.Center) {
 *     Column(
 *         Modifier
 *             .width(EInkTheme.PopupOverlay.width)
 *             .background(EInkTheme.PopupOverlay.background)
 *             .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor)
 *             .padding(EInkTheme.PopupOverlay.padding)
 *     ) {
 *         // Header with vector close button
 *         Row {
 *             Text("Title", fontSize = EInkTheme.PopupOverlay.titleFontSize)
 *             Spacer(Modifier.weight(1f))
 *             Icon(Icons.Default.Close, contentDescription = "Close", Modifier.noRippleClickable { onDismiss() })
 *         }
 *         // Content...
 *     }
 * }
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Centralized E-Ink Design System for Boox Day
 *
 * All UI components should reference these values instead of hardcoding.
 * Change a value here = entire app updates on next build.
 *
 * Structure:
 * - Colors: E-Ink optimized palette (grayscale-friendly)
 * - Typography: Font size scale for readability
 * - Global: App-wide defaults
 * - Main Features: CalendarGrid, EventsPanel, Toolbar, MemosTab, TasksTab, Canvas
 * - Popups: PopupMenu (small), PopupPanel (medium), PopupOverlay (large)
 * - Internals: MenuItem, Button, TextField, Divider, TextBoxOverlay
 * - Specific: DatePicker, TimePicker, ColorPicker, SizePicker
 * - States: Selection, Disabled, Hover
 */
object EInkTheme {

    // ╔══════════════════════════════════════════════════════════════════════════════╗
    // ║  🚨🚨🚨  CRITICAL: NO ALPHA/TRANSPARENCY - USE SOLID COLORS ONLY  🚨🚨🚨     ║
    // ╠══════════════════════════════════════════════════════════════════════════════╣
    // ║                                                                              ║
    // ║   E-Ink displays render transparency as DITHERED PATTERNS (checkerboard     ║
    // ║   noise). This causes visible grain, smearing, and ghosting artifacts.      ║
    // ║                                                                              ║
    // ║   ┌────────────────────────────────────────────────────────────────────┐     ║
    // ║   │  ALPHA IS FORBIDDEN EVERYWHERE:                                   │     ║
    // ║   │  ✗ Containers (Box, Column, Row, Surface, Card)                   │     ║
    // ║   │  ✗ Backgrounds (.background(), .drawBehind())                     │     ║
    // ║   │  ✗ Overlays and scrims                                            │     ║
    // ║   │  ✗ Dialogs and popups                                             │     ║
    // ║   │  ✗ Text and icons (use solid gray colors instead)                 │     ║
    // ║   │  ✗ Any UI element - PERIOD                                        │     ║
    // ║   └────────────────────────────────────────────────────────────────────┘     ║
    // ║                                                                              ║
    // ║   USE SOLID COLORS FOR ALL STATES:                                          ║
    // ║   - Disabled text/icons: Colors.disabled (gray #6A6A6A)                     ║
    // ║   - Secondary text/icons: Colors.secondary (darkGray #3A3A3A)               ║
    // ║   - Disabled strokes/lines: Colors.dividerHairline (lightGray #B8B8B8)      ║
    // ║   - NEVER use .copy(alpha=...) on ANY color                                 ║
    // ║   - NEVER use Modifier.alpha() on ANY composable                            ║
    // ║                                                                              ║
    // ║   VIOLATION = Dithering, grain, ghosting, and smearing on E-Ink displays    ║
    // ║                                                                              ║
    // ╚══════════════════════════════════════════════════════════════════════════════╝

    // ═══════════════════════════════════════════════════════════════════════════
    // COLORS (E-Ink Optimized Palette)
    //
    // E-Ink displays render colors as grayscale. This palette is optimized for:
    // - High contrast (black on white)
    // - Minimal dithering (avoid mid-grays for large areas)
    // - Clear visual hierarchy
    //
    // ┌─────────────────────────────────────────────────────────────────────────┐
    // │  E-INK GRAYSCALE USAGE RULES (MANDATORY)                                │
    // ├─────────────────────────────────────────────────────────────────────────┤
    // │  LARGE SURFACES (backgrounds, cards, panels):                           │
    // │    ✓ white (#FFFFFF) - primary backgrounds                              │
    // │    ✓ offWhite (#F7F7F7) - alternating rows, subtle distinction          │
    // │    ✗ NEVER use gray, lightGray, or darkGray as background fills         │
    // │                                                                         │
    // │  BORDERS & DIVIDERS:                                                    │
    // │    ✓ black - high visibility dividers                                   │
    // │    ✓ lightGray (#B8B8B8) - subtle 1dp lines/borders ONLY                │
    // │    ✗ NEVER use lightGray as a fill or large area background             │
    // │                                                                         │
    // │  TEXT & ICONS:                                                          │
    // │    ✓ black - primary text, icons                                        │
    // │    ✓ darkGray (#3A3A3A) - secondary/hint text                           │
    // │    ✓ gray (#6A6A6A) - disabled text/icons                               │
    // │    ✗ NEVER use mid-grays for large fills (causes dithering/grain)       │
    // │                                                                         │
    // │  TRANSPARENCY:                                                          │
    // │    ✗ NEVER use .copy(alpha=...) on background colors                    │
    // │    ✗ NEVER use semi-transparent overlays (causes E-Ink dithering)       │
    // └─────────────────────────────────────────────────────────────────────────┘
    // ═══════════════════════════════════════════════════════════════════════════
    object Colors {
        // Primary (use for most UI - safe on all E-Ink displays)
        val black = Color.Black              // #000000 - Text, borders, icons
        val white = Color.White              // #FFFFFF - Backgrounds

        // Grayscale (E-Ink safe values - use ONLY as documented above)
        val darkGray = Color(0xFF3A3A3A)     // #3A3A3A - Secondary text/icons ONLY
        val gray = Color(0xFF6A6A6A)         // #6A6A6A - Disabled text/icons ONLY
        val pastGray = Color(0xFF6A6A6A)     // #6A6A6A - Past days text (Kaleido 3 improved visibility)
        val lightGray = Color(0xFFB8B8B8)    // #B8B8B8 - 1dp borders/dividers ONLY (NOT fills)
        val selectedGray = Color(0xFFEBEBEB) // #EBEBEB - Kaleido 3 safe selected/actioned fill (no rainbow fringing)
        val offWhite = Color(0xFFF7F7F7)     // #F7F7F7 - Safe near-white for large areas

        // Accent colors (Kaleido 3 color e-ink + monochrome compatible)
        val cyan = Color(0xFF00CCCC)         // #00CCCC - Due date indicator (informational)
        val red = Color(0xFFCC0000)          // #CC0000 - Overdue indicator (urgent/alert)

        // Semantic aliases (preferred over direct color references)
        val primary = black
        val background = white
        val onBackground = black
        val secondary = darkGray
        val disabled = gray
        val divider = black                  // Use black for visibility!
        val subtleDivider = lightGray        // Only for very subtle thin lines
        val alternateBackground = offWhite   // For alternating rows

        // Border semantic aliases (Kaleid 3 visibility fix)
        // - dividerHairline: ONLY for thin 1dp row dividers/separators
        // - borderStrong: For card/panel/box borders that need Kaleid 3 visibility
        val dividerHairline = lightGray      // Subtle 1dp lines between rows
        val borderStrong = darkGray          // Visible borders on cards/panels (Kaleid 3 safe)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TYPOGRAPHY (E-Ink Optimized - Larger sizes for readability)
    //
    // E-Ink has lower contrast than LCD. Larger text improves readability.
    // ═══════════════════════════════════════════════════════════════════════════
    object Typography {
        // Headers
        val titleLarge = 20.sp               // Screen titles, popup headers
        val titleMedium = 18.sp              // Section headers
        val titleSmall = 16.sp               // Card titles, list item titles

        // Body text
        val bodyLarge = 16.sp                // Primary content, menu items
        val bodyMedium = 15.sp               // Secondary content, descriptions
        val bodySmall = 13.sp               // Timestamps, hints, captions

        // Labels
        val labelLarge = 15.sp               // Button text
        val labelMedium = 13.5.sp             // Chip text, badges
        val labelSmall = 12.sp               // Tiny labels (use sparingly!)

        // Semantic aliases
        val menuItem = bodyLarge
        val button = labelLarge
        val hint = bodySmall
        val timestamp = bodySmall
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AI PROCESSING FEEDBACK
    //
    // Standard values for RotatingFunMessage, EinkLoadingDots, and companion
    // text shown during AI/LLM wait states across all dialogs.
    // ═══════════════════════════════════════════════════════════════════════════
    object AiProcessingFeedback {
        // Rotating fun message (primary — the "star" of the wait screen)
        val funMessageFontSize = 20.sp
        val funMessageFontWeight = FontWeight.Medium
        val funMessageColor = Colors.primary
        val funMessageIntervalMs = 4000L

        // Loading dots
        val dotsFontSize = 48.sp
        val dotsSpacing = 12.dp
        val dotsActiveColor = Colors.black
        val dotsInactiveColor = Color.LightGray

        // Technical status message (secondary)
        val technicalFontSize = Typography.bodyLarge       // 16sp
        val technicalColor = Colors.gray                   // #6A6A6A

        // Helper/hint text (tertiary)
        val helperFontSize = Typography.bodyLarge           // 16sp
        val helperColor = Color(0xFF888888)                // #888888 — readable on e-ink, clearly tertiary
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GLOBAL DEFAULTS
    //
    // App-wide values. Change here = affects everything that inherits.
    // ═══════════════════════════════════════════════════════════════════════════
    object Global {
        val background = Colors.background
        val foreground = Colors.primary
        val borderWidth = 1.dp
        val borderColor = Colors.primary
        val touchTarget = 44.dp              // Standard touch target (accessibility)
        val touchTargetCompact = 42.dp       // Minimum touch target (time pickers, adjacent buttons)
        val iconSize = 24.dp
        val iconSizeSmall = 20.dp
        val iconSizeLarge = 32.dp
        val padding = 16.dp
        val paddingSmall = 8.dp
        val paddingLarge = 24.dp
        val cornerRadius = 6.dp              // Consistent rounding for all dialogs/popups
        val paddingMedium = 12.dp             // Between paddingSmall (8.dp) and padding (16.dp)
        val spacing = 8.dp                   // Default spacing between elements
        val spacingSmall = 4.dp
        val spacingLarge = 16.dp
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MAIN FEATURES
    //
    // The primary app areas - not popups, but the actual screen content.
    // ═══════════════════════════════════════════════════════════════════════════

    /** Monthly calendar grid in the top-left area */
    object CalendarGrid {
        val cellSize = 48.dp
        val cellPadding = 4.dp
        val dayFontSize = Typography.bodyMedium
        val dayColor = Colors.primary
        val dayColorOutsideMonth = Colors.gray  // #6A6A6A
        val weekdayHeaderHeight = 32.dp
        val weekdayFontSize = 11.5.dp
        val weekdayColor = Colors.gray
        val monthHeaderHeight = 48.dp
        val monthFontSize = Typography.titleMedium
        val monthColor = Colors.primary
        val todayBorderWidth = 1.5.dp  // Today (not selected) is a black ring, no grey fill
        val todayBorderColor = Colors.primary

        // Today (not selected) - light gray to distinguish from other days
        val todayBackground = Color(0xFFE0E0E0)  // Lighter than lightGray (#C0C0C0)
        val todayTextColor = Colors.primary

        // Today + Selected - medium gray (distinguished from regular black selection)
        val todaySelectedBackground = Colors.gray
        val todaySelectedTextColor = Colors.background

        val memoIconSize = 9.dp           // Memo badge icon size (ic_memo_badge)
        val memoIconSizeSelected = 10.5.dp  // Larger when day is selected
        val memoIconColor = Colors.primary

        val taskIconSize = 9.5.dp           // Task badge icon size (ic_check_badge)
        val taskIconSizeSelected = 11.dp  // Larger when day is selected

        val booxNoteContainerSize = 12.dp // Circular background for Boox Notes badge (top-left corner of the day)
        val booxNoteIconSize = 7.dp       // Icon inside (boox_b_letter_tiny)

        // Calendar events: one dot under the day number (Oct 2026: was a 10 x 2 dp bar that crossed the digits)
        val eventIndicatorWidth = 5.dp
        val eventIndicatorHeight = 5.dp
        val eventIndicatorCornerRadius = 2.5.dp
        val eventIndicatorBottomPadding = 3.dp

        val selectedBackground = Colors.primary
        val selectedForeground = Colors.background
    }

    /** Events list panel in the top-right area */
    object EventsPanel {
        // 40% of header area; 44% on narrow screens (Go 6, Oct 2026: the Events/Due tabs and the
        // calendar filter icons need ~250 dp; the calendar keeps ~46 dp per day column)
        val widthFraction: Float get() = if (LayoutProfiles.isNarrowScreen) 0.44f else 0.4f
        val headerHeight = 40.dp
        val headerFontSize = Typography.titleSmall
        val headerColor = Colors.primary
        val rowHeight = 56.dp
        val rowPadding = 8.dp
        val rowBackground = Colors.background
        val rowBackgroundAlt = Colors.offWhite
        val rowDividerColor = Colors.dividerHairline
        val rowDividerThickness = 1.dp
        val timeColumnWidth = 60.dp
        val timeFontSize = Typography.bodySmall       // Footer counts, pagination
        val timeColor = Colors.darkGray
        val titleFontSize = Typography.bodyMedium
        val titleColor = Colors.primary
        val descriptionFontSize = Typography.bodySmall
        val descriptionColor = Colors.darkGray
        val emptyStateIconSize = 48.dp
        val emptyStateTextSize = Typography.bodyMedium
        val emptyStateColor = Colors.gray

        // Event item row specifics (compact sizing for list density)
        val eventItemTimeFontSize = 12.5.sp            // Event time (e.g., "9:00 AM")
        val eventItemTitleFontSize = 14.sp           // Event title
        val eventItemDurationFontSize = 12.sp      // Duration badge (e.g., "1h 30m")
        val eventItemBadgeCountFontSize = 11.sp      // Note count in badge

        // Tab button styling
        val tabLabelFontSizeSelected = 14.sp
        val tabLabelFontSizeUnselected = 13.sp
        val tabCountFontSizeSelected = 13.sp
        val tabCountFontSizeUnselected = Typography.labelSmall  // 12.sp
    }

    /** Main toolbar between header and canvas */
    object Toolbar {
        val height = 44.dp
        val background = Colors.background
        val buttonSize = 40.dp
        val buttonSizeMedium = 44.dp     // For tool buttons (Eraser, Selection, AI, Notes)
        val buttonSizeLarge = 48.dp      // For high-frequency buttons (Drawing, Undo, Redo)
        val tabWidth = 75.dp             // Enlarged tab width (was 70dp implicit)
        val tabTotalWidth = 152.dp       // Total tab toggle width (75dp × 2 + 2dp gap)
        val navChevronWidth = 30.dp      // Compact chevron width (reduced from 36dp)
        val iconSize = Global.iconSize
        val iconColor = Colors.primary
        val iconColorSelected = Colors.background
        val iconColorDisabled = Colors.gray
        val buttonBackground = Colors.background
        val buttonBackgroundSelected = Colors.primary
        val spacing = 4.dp
        val groupSpacing = 8.dp              // Space between button groups
        val tabIndicatorHeight = 3.dp
        val tabIndicatorColor = Colors.primary
        val dividerWidth = 0.5.dp
        val dividerHeight = 32.dp
        val dividerColor = Colors.dividerHairline  // Subtle toolbar dividers
    }

    /** Calendar header toolbar (month navigation + action icons) */
    object CalendarToolbar {
        // Layout
        val elevation = 2.dp
        val background = Colors.background
        val paddingHorizontal = 8.dp
        val paddingVertical = 4.dp
        val monthLabelWidth = 105.dp         // Fixed width for "September" (longest)
        val statusTextWidth = 45.dp          // Fixed width for sync status text

        // Month/Year navigation
        val monthFontSize = 20.sp   
        val yearFontSize = 20.sp    // 18.sp
        val chevronToMonthSpacing = 4.dp             // Left chevron to month label
        val monthToYearSpacing = 0.dp                // Month label to year label
        val yearToChevronSpacing = 4.dp              // Year label to right chevron

        // Icon sizes
        val iconSizeStandard = 28.dp         // Most toolbar icons
        val iconSizeSmall = 22.dp            // Fire/streak icon
        val iconSizeLarge = 36.dp            // Megaphone/campaign icon

        // Icon colors
        val iconColor = Colors.primary
        val iconColorDisabled = Colors.gray
        val iconColorError = Color(0xFFB71C1C)

        // Badge styling (sync count, unread count)
        val badgeSize = 16.dp                // Standard badge (sync pending)
        val badgeSizeSmall = 14.dp           // Smaller badge (unread count)
        val badgeFontSize = 9.sp             // Badge count text
        val badgeBackground = Colors.primary
        val badgeBackgroundError = Colors.red
        val badgeTextColor = Colors.background
    }

    /** Memos tab canvas area */
    object MemosTab {
        val background = Colors.background
        val pageIndicatorHeight = 32.dp
        val pageIndicatorFontSize = Typography.bodyMedium
        val pageIndicatorColor = Colors.darkGray
        val pageNavButtonSize = 32.dp
        val pageNavIconSize = 20.dp
    }

    /** Tasks tab canvas area with control column */
    object TasksTab {
        val background = Colors.background
        val controlColumnWidth = 152.dp  // Actual compact layout width
        val controlColumnBackground = Colors.background
        val controlColumnBorderWidth = 0.5.dp
        val controlColumnBorderColor = Colors.borderStrong  // Kaleid 3 visible
        val rowHeight = 56.dp
        val rowCount = 10
        val rowDividerColor = Colors.borderStrong
        val rowDividerThickness = 0.5.dp
        val checkboxSize = 24.dp
        val checkboxBorderWidth = 1.dp
        val checkboxColor = Colors.primary
        val checkboxCheckedBackground = Colors.primary
        val checkboxCheckedForeground = Colors.background
        val priorityButtonSize = 32.dp
        val priorityIconSize = 20.dp
        val expandButtonSize = 24.dp

        // Due dates feature - compact layout
        // Layout: 4dp + 39dp + 3dp + 28dp + 5dp + 32dp + 3dp + 39dp = 152dp
        val controlColumnWidthWithDueDates = 152.dp

        // Compact screen profile (plan Phase 1.5, Go 6): checkbox + priority + chevron, due date via
        // the priority menu. Layout: 4dp + 39dp + 3dp + 28dp + 3dp + 39dp = 116dp. Fixed per device.
        val controlColumnWidthCompactProfile = 116.dp

        // Touch targets (tap area)
        val checkboxTouchCompact = 39.dp                  // Checkbox tap area
        val priorityTouchCompact = 28.dp                  // Priority tap area
        val calendarTouchCompact = 32.dp                  // Calendar tap area
        val repeatTouchCompact = 30.dp                    // Repeat series tap area
        val chevronTouchCompact = 39.dp                   // Chevron tap area

        // Icon sizes (visual)
        val checkboxIconCompact = 22.dp                   // Checkbox visual
        val priorityIconCompact = 23.dp                   // Priority visual
        val calendarIconCompact = 29.dp                   // Calendar visual
        val repeatIconCompact = 22.dp                     // Repeat badge visual
        val chevronIconCompact = 30.dp                    // Chevron visual (same as touch)

        // Gaps and padding
        val compactGap = 3.dp                             // Tight gap between elements
        val compactGapAfterCheckbox = 3.dp                // Gap after checkbox
        val compactGapAfterPriority = 5.dp                // Gap between priority and due date
        val compactGapAfterCalendar = 2.dp                // Gap after calendar
        val compactGapAfterRepeat = 4.dp                  // Gap after repeat badge
        val compactPaddingStart = 4.dp                    // Left padding
        val compactPaddingEnd = 0.dp                      // Right padding

        // Due date visual states
        val dueDateIconColorEmpty = Colors.gray           // No due date set
        val dueDateIconColorSet = Colors.primary          // Due date set (future)
        val dueDateIconColorOverdue = Colors.primary      // Overdue (same color, different icon)
        val repeatIconColorActive = Colors.primary
        val repeatIconColorPaused = Colors.gray

        // Completed task styling - NO ALPHA on containers (E-Ink rule)
        // Use solid colors for text/strokes, NOT alpha overlays on rows
        val completedTextColor = Colors.pastGray                    // Solid gray for completed text
        val completedTypedTextColor =  Colors.lightGray       // Light gray for typed text when completed
        val completedStrikethroughColor = Colors.lightGray      // Solid gray for strikethrough line
        val completedStrikethroughThickness = 0.3.dp            // Strikethrough line thickness
        val completedStrokeColor = Colors.lightGray             // Light gray for handwritten strokes when completed
        val completedRowBackground = Colors.lightGray          // Keep row background solid white (NO alpha overlay!)

        // Typed text styling
        val typedTextFontSize = 18.sp                        // Larger font to match handwritten size
        val typedTextColor = Colors.primary                  // Normal text color
        val typedTextPaddingStart = 20.dp                    // Left padding for visual spacing
        val typedTextPaddingEnd = 8.dp                       // Right padding
        val typedTextMaxLength = 60                          // Max characters for typed task title

        // Subtask styling (for Break Down Task feature)
        // Child tasks are identified by parentTaskId in database, not by text prefix
        // Visually distinguished with smaller font and extra indentation (no dash)
        object Subtask {
            val fontSize = 16.sp                             // Smaller than parent (20sp)
            val textColor = Colors.darkGray                  // Slightly muted compared to parent
            val paddingStart = 50.dp                         // Extra left indent (30dp base + 18dp indent)
        }
    }

    /** Drawing canvas (shared by Memos and Tasks) */
    object Canvas {
        val background = Colors.background
        val defaultStrokeColor = Colors.primary
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POPUPS (All overlays - NO Android Dialog!)
    //
    // We use Compose Popup instead of Dialog to avoid:
    // - Scrim (semi-transparent overlay that causes E-Ink dithering)
    // - WindowManager overhead (slow on E-Ink CPUs)
    // - Full screen refresh (GC update on window change)
    //
    // Four sizes: Menu (320dp), Compact (280dp), Panel (380dp), Overlay (460dp/520dp)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Small popups anchored to buttons (settings menu, pen picker, AI menu) */
    object PopupMenu {
        val background = Colors.background
        val borderColor = Colors.primary
        val borderWidth = Global.borderWidth
        val width = 230.dp                   // Settings menu width
        val maxHeight = 400.dp
        val padding = Global.paddingSmall
        val itemSpacing = 0.dp               // Menu items are usually contiguous
        val offsetY = 46.dp                  // Standard offset below toolbar (46dp height)
    }

    /** Compact centered popups for simple confirmations (move task, delete confirmation) */
    object PopupCompact {
        val background = Colors.background
        val borderColor = Colors.primary
        val borderWidth = Global.borderWidth
        val width = 280.dp                   // Smaller than PopupPanel for simple dialogs
        val maxHeight = 300.dp
        val padding = Global.paddingMedium
        val titleFontSize = Typography.titleSmall
        val titleColor = Colors.primary
    }

    /** Medium popups for pickers and small forms (date picker, color picker) */
    object PopupPanel {
        val background = Colors.background
        val borderColor = Colors.primary
        val borderWidth = Global.borderWidth
        val width = 380.dp
        val maxHeight = 500.dp
        val padding = Global.paddingMedium  // 12.dp - consistent with PopupCompact
        val titleFontSize = Typography.titleSmall
        val titleColor = Colors.primary
        val titlePaddingBottom = 8.dp
    }

    /** Large centered popups for full forms (event editor, settings, voice input) */
    object PopupOverlay {
        val background = Colors.background
        val borderColor = Colors.primary
        val borderWidth = 1.dp               // Thicker for Kaleid 3 visibility
        // Never wider/taller than the screen allows (SCREEN_POPUP_MAX_*, Oct 2026). Go 6: forms 460,
        // content-rich 540, height up to 633; Go 10.3: 460 / 570, height up to 845 (was 640)
        val width: Dp get() = minOf(460.dp, SCREEN_POPUP_MAX_WIDTH)
        val maxWidth: Dp get() = minOf(480.dp, SCREEN_POPUP_MAX_WIDTH)  // For wider screens
        val wideWidth: Dp get() = minOf(570.dp, SCREEN_POPUP_MAX_WIDTH)  // For content-rich dialogs (stats, summaries, AI results)
        val maxHeight: Dp get() = SCREEN_POPUP_MAX_HEIGHT
        val padding = Global.padding
        val titleFontSize = Typography.titleMedium
        val titleColor = Colors.primary
        val titlePaddingBottom = 12.dp
        val sectionSpacing = 16.dp           // Space between form sections
        val buttonRowPaddingTop = 16.dp
    }

    /** Large report-style dialogs for summaries, stats, and analytics (Weekly Summary, Daily Summary) */
    object ReportDialog {
        val width: Dp get() = minOf(600.dp, SCREEN_POPUP_MAX_WIDTH)  // Wider than wideWidth for data-rich reports
        // Taller to show more content without scrolling: 86% of the screen (Go 6 664, Go 10.3 887; was 780)
        val maxHeight: Dp get() = (LayoutProfiles.screenHeightDp * 0.86f).dp
        val background = Colors.background
        val borderColor = Colors.primary
        val borderWidth = 1.dp
        val padding = Global.padding
    }

    /**
     * Full-height dialogs with scrolling content: Preferences, AI Settings, Data & Backup, Calendar
     * settings, Calendar sync, Help, What's New, Handwriting languages (Oct 2026: one size for all,
     * they were 75-87% wide and 70-80% tall for no reason). Fractions of the screen: Go 6 (narrow)
     * 94% x 86% = 538 x 664 dp; Go 10.3 85% x 84% = 657 x 866 dp.
     */
    object PanelDialog {
        val widthFraction: Float get() = if (LayoutProfiles.isNarrowScreen) 0.94f else 0.85f
        val heightFraction: Float get() = if (LayoutProfiles.isNarrowScreen) 0.86f else 0.84f
        /** Height cap for panel dialogs that size to their content (and scroll beyond it) */
        val maxHeight: Dp get() = (LayoutProfiles.screenHeightDp * heightFraction).dp
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MODERN DIALOG STYLE
    //
    // Softer, more refined dialog style with gray palette instead of harsh black/white.
    // Uses subtle borders, floating chips, and generous whitespace for a modern feel.
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Modern dialog styling with gray palette - soft, refined, professional.
     * Use for dialogs like Export, Settings, etc. where a modern feel is desired.
     */
    object ModernDialog {
        // Container
        val background = Colors.background
        val borderWidth = 1.dp               // Thicker for Kaleid 3 visibility
        val borderColor = Colors.primary     // Black border for E-Ink visibility
        val cornerRadius = 4.dp              // Subtle rounded corners
        // Percentage of screen width; 90% on narrow screens (Go 6, Oct 2026: 515 instead of 469 dp)
        val widthFraction: Float get() = if (LayoutProfiles.isNarrowScreen) 0.9f else 0.82f
        val padding = 20.dp
        val contentSpacing = 16.dp           // Space between sections

        // Title
        val titleFontSize = Typography.titleLarge
        val titleColor = Colors.darkGray     // Softer than black

        // Segmented control (format selector) - floating chips style
        object SegmentedControl {
            val height = Global.touchTarget  // Touch-friendly
            val borderWidth = 1.dp           // Thicker for Kaleid 3 visibility
            val borderColor = Colors.borderStrong  // Kaleid 3 visible
            val cornerRadius = 0.dp          // Inherits from parent or set independently
            val fontSize = Typography.bodyLarge
            val textColor = Colors.darkGray
            val textColorSelected = Colors.white
            val background = Colors.background
            val backgroundSelected = Colors.darkGray  // Dark gray instead of black
            val dividerColor = Colors.dividerHairline
            val dividerWidth = 0.5.dp
        }

        // Option cards/groups
        object OptionCard {
            val borderWidth = 1.dp           // Thicker for Kaleid 3 visibility
            val borderColor = Colors.borderStrong  // Kaleid 3 visible
            val cornerRadius = 0.dp          // Inherits from parent or set independently
            val background = Colors.background
            val dividerColor = Colors.dividerHairline
            val dividerHeight = 1.dp
        }

        // Option rows (radio-style selection)
        object OptionRow {
            val height = 52.dp               // Comfortable touch target
            val paddingHorizontal = 16.dp
            val paddingVertical = 14.dp
            val fontSize = Typography.bodyLarge
            val textColor = Colors.darkGray
            val textColorSelected = Colors.primary  // Slightly bolder when selected
        }

        // Radio indicator (small filled square/circle)
        object RadioIndicator {
            val size = 18.dp
            val borderWidth = 1.dp           // Thinner border
            val borderColor = Colors.darkGray  // Softer than black
            val cornerRadius = 0.dp          // 0 = square, 9.dp = circle
            val fillColor = Colors.darkGray  // Fill when selected
            val innerPadding = 3.dp          // Gap between border and fill
        }

        // Primary action button (filled)
        object PrimaryButton {
            val height = Global.touchTarget
            val heightLarge = 58.dp           // Prominent CTA buttons (subscribe, purchase)
            val cornerRadius = 6.dp           // Rounded corners for modern look
            val fontSize = Typography.bodyLarge
            val background = Colors.darkGray   // Dark gray instead of black
            val textColor = Colors.white
        }

        // Cancel/dismiss text link
        object CancelLink {
            val fontSize = Typography.bodyMedium
            val textColor = Colors.gray        // Subtle gray
            val paddingVertical = 8.dp
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POPUP INTERNALS
    //
    // Shared components used inside popups (menus, panels, overlays).
    // ═══════════════════════════════════════════════════════════════════════════

    /** Menu items inside popup menus */
    object MenuItem {
        val height = Global.touchTarget
        val fontSize = Typography.menuItem
        val textColor = Colors.primary
        val textColorDisabled = Colors.disabled
        val iconSize = Global.iconSize
        val iconColor = Colors.primary
        val iconColorDisabled = Colors.disabled
        val horizontalPadding = 16.dp
        val iconTextSpacing = 12.dp
        val selectedBackground = Colors.primary
        val selectedTextColor = Colors.background
        val selectedIconColor = Colors.background
    }

    /** Action buttons in popups */
    object Button {
        val height = Global.touchTarget
        val minWidth = 80.dp
        val fontSize = Typography.button
        val horizontalPadding = 16.dp
        val spacing = 8.dp                   // Space between buttons

        // Secondary button (outlined)
        val background = Colors.background
        val textColor = Colors.primary
        val borderWidth = Global.borderWidth
        val borderColor = Colors.primary

        // Primary button (filled)
        val primaryBackground = Colors.primary
        val primaryTextColor = Colors.background
        val primaryBorderWidth = Global.borderWidth
        val primaryBorderColor = Colors.primary

        // Disabled state
        val disabledBackground = Colors.background
        val disabledTextColor = Colors.disabled
        val disabledBorderColor = Colors.disabled
    }

    /** Text input fields */
    object TextField {
        val height = Global.touchTarget
        val fontSize = Typography.bodyLarge
        val textColor = Colors.primary
        val hintColor = Colors.gray
        val background = Colors.background
        val borderWidth = 1.dp
        val borderColor = Colors.darkGray
        val focusedBorderWidth = 2.dp
        val focusedBorderColor = Colors.primary
        val padding = 12.dp
        val labelFontSize = Typography.bodySmall
        val labelColor = Colors.darkGray

        /** Inline action buttons next to text fields (paste, meet, etc.) */
        object InlineAction {
            val buttonSize = 42.dp            // 95% of field height (44dp * 0.95 ≈ 42dp)
            val iconSize = 22.dp              // 90% of standard icon (24dp * 0.9 ≈ 22dp, rounded up)
            val iconColor = Colors.secondary
            val spacing = 4.dp                // Gap between field and buttons
        }
    }

    /** Dividers/separators */
    object Divider {
        val color = Colors.divider
        val thickness = 1.dp
        val subtleColor = Colors.subtleDivider
        val subtleThickness = 1.dp
    }

    /** Text box selection overlay */
    object TextBoxOverlay {
        val handleSize = 10.dp
        val handleColor = Colors.primary
        val borderWidth = 0.5.dp
        val borderColor = Colors.primary
        // Mini toolbar heights for offset calculation (places toolbar above/below selection)
        val miniToolbarHeightAdvanced = 76.dp  // 2-row edit mode (2×35dp buttons + 6dp padding)
        val miniToolbarHeightBasic = 41.dp     // 1-row select mode (35dp buttons + 6dp padding)
        val miniToolbarBackground = Colors.background
        val miniToolbarBorderWidth = 0.5.dp
        val miniToolbarBorderColor = Colors.primary
        val miniToolbarButtonSize = 40.dp
        val miniToolbarButtonSpacing = 4.dp
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SPECIFIC COMPONENTS
    //
    // Components with unique settings that don't fit the general patterns.
    // ═══════════════════════════════════════════════════════════════════════════

    /** Date picker grid */
    object DatePicker {
        val width = PopupPanel.width
        val cellSize = 36.dp
        val cellFontSize = Typography.bodyMedium
        val cellColor = Colors.primary
        val cellColorOutsideMonth = Colors.gray
        val headerHeight = 40.dp
        val headerFontSize = Typography.titleSmall
        val headerColor = Colors.primary
        val weekdayFontSize = Typography.bodySmall
        val weekdayColor = Colors.gray
        val selectedBackground = Colors.primary
        val selectedForeground = Colors.background
        val todayBorderWidth = 2.dp
        val todayBorderColor = Colors.primary
    }

    /**
     * Task Date Picker Dialog (Move/Copy Tasks)
     *
     * Enlarged date picker dialog for task management operations.
     * Touch-optimized with larger cells and navigation buttons.
     */
    object TaskDatePickerDialog {
        // Dialog container (widened from 360 to 395 to fit Copy button alongside Move)
        val dialogWidth = 395.dp
        val dialogCornerRadius = Global.cornerRadius  // Consistent with other dialogs
        val dialogPadding = Global.paddingMedium      // 12.dp - consistent spacing
        val dialogBackground = Colors.background
        val dialogBorderWidth = Global.borderWidth
        val dialogBorderColor = Colors.primary

        // Title
        val titleFontSize = Typography.titleMedium
        val titleColor = Colors.primary

        // Month/Year navigation (10% smaller)
        val navButtonSize = 40.dp
        val navIconSize = 25.dp
        val navIconColor = Colors.primary
        val monthYearFontSize = Typography.bodyLarge
        val monthYearColor = Colors.primary

        // Weekday headers
        val weekdayFontSize = Typography.bodySmall
        val weekdayColor = Colors.darkGray

        // Calendar grid (10% smaller)
        val cellSize = 36.dp                      // Reduced from 40dp for compact layout
        val cellPadding = 2.dp
        val cellCornerRadius = Global.cornerRadius  // Consistent rounding
        val cellFontSize = Typography.bodySmall
        val gridHeight = 221.dp                   // 36dp cells * 6 rows + padding

        // Cell states
        val cellColor = Colors.primary
        val cellColorOutsideMonth = Colors.gray
        val cellSelectedBackground = Selection.background
        val cellSelectedForeground = Selection.foreground
        val cellCurrentDateBackground = Colors.lightGray
        val cellCurrentDateTextColor = Colors.darkGray
        val cellCurrentDateBorderWidth = Divider.thickness
        val cellCurrentDateBorderColor = Colors.gray
        val cellTodayTextColor = Colors.primary

        // Action buttons (10% smaller)
        val buttonRowSpacing = 9.dp
        val buttonCornerRadius = Global.cornerRadius  // Consistent with dialog
        val buttonHorizontalPadding = 18.dp

        // Delete button (destructive)
        val deleteButtonBackground = Color(0xFF8B0000)  // Dark red
        val deleteButtonTextColor = Colors.white

        // Section spacing (10% smaller)
        val sectionSpacing = 14.dp
        val headerToNavSpacing = 14.dp
        val navToWeekdaySpacing = 10.dp
        val weekdayToGridSpacing = 7.dp
        val gridToButtonsSpacing = 14.dp
    }

    /** Time picker columns */
    object TimePicker {
        val columnWidth = 60.dp
        val rowHeight = 40.dp
        val fontSize = Typography.bodyLarge
        val textColor = Colors.primary
        val selectedBackground = Colors.primary
        val selectedTextColor = Colors.background
        val separatorFontSize = Typography.titleMedium
    }

    /** Color selection swatches */
    object ColorPicker {
        val swatchSize = 40.dp
        val swatchSizeCompact = 22.dp            // Compact swatch in StrokeMenu
        val swatchSpacing = 6.dp
        val swatchBorderWidth = 2.dp
        val swatchBorderColor = Colors.primary
        val selectedBorderWidth = 3.dp
        val columnWidth = 44.dp                  // Width of each color column in StrokeMenu
    }

    /** Tool settings indicator in toolbar (shows pen name, color dot, size) */
    object ToolSettingsIndicator {
        // Color dot
        val colorDotSize = 16.dp
        val colorDotBorderWidth = Global.borderWidth
        val colorDotBorderColor = Colors.primary

        // Pen name label
        val penNameFontSize = 11.sp
        val penNameColor = Colors.primary
        val penNameLineHeight = 13.sp

        // Size label
        val sizeFontSize = 10.sp
        val sizeColor = Colors.secondary
        val sizeLineHeight = 11.sp
    }

    /** Size/stroke picker buttons */
    object SizePicker {
        val buttonSize = 40.dp
        val buttonSpacing = 6.dp
        val buttonBorderWidth = 2.dp
        val buttonBorderColor = Colors.primary
        val labelFontSize = Typography.labelLarge
        val labelColor = Colors.primary
        val selectedBackground = Colors.primary
        val selectedLabelColor = Colors.background
    }

    /**
     * Toggle switches - E-Ink optimized with high contrast black/white states.
     * Used by EInkToggle (with labels) and EInkToggleCompact (no labels).
     *
     * Design: Pure black/white for maximum E-Ink visibility.
     * OFF: White track, black border, black thumb on left
     * ON: Black track, white thumb on right
     */
    object Toggle {
        // Standard toggle (with ON/OFF labels)
        val width = 56.dp
        val height = 28.dp
        val thumbSize = 22.dp
        val cornerRadius = 14.dp  // Half of height for pill shape
        val borderWidth = 2.dp
        val thumbBorderWidth = 1.dp
        val labelPadding = 6.dp
        val thumbPadding = 3.dp

        // Compact toggle (no labels, for tight spaces)
        val compactWidth = 44.dp
        val compactHeight = 24.dp
        val compactThumbSize = 18.dp
        val compactCornerRadius = 12.dp

        // Colors - pure black/white for E-Ink clarity
        val trackColorOff = Colors.white
        val trackColorOn = Colors.black
        val thumbColorOff = Colors.black
        val thumbColorOn = Colors.white
        val borderColor = Colors.black
        val labelColor = Colors.black        // OFF state text
        val labelColorOn = Colors.white      // ON state text

        // Disabled state
        val disabledTrackColor = Colors.lightGray
        val disabledThumbColor = Colors.gray
        val disabledBorderColor = Colors.gray

        // Label typography
        val labelFontSize = Typography.labelSmall
    }

    /** Checkbox */
    object Checkbox {
        val size = 24.dp
        val borderWidth = 1.dp
        val borderColor = Colors.primary
        val checkedBackground = Colors.primary
        val checkmarkColor = Colors.background
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PREFERENCES DIALOG (E-Ink Optimized - Centralized sizing)
    //
    // All font sizes and icon sizes for the Preferences/Settings dialog.
    // Tuned for stylus interaction and e-ink readability.
    // ═══════════════════════════════════════════════════════════════════════════

    /** Preferences dialog - centralized sizing for e-ink readability */
    object Preferences {
        // Section headers ("Notifications", "Handwriting Recognition")
        val sectionHeaderFontSize = 17.sp
        val sectionHeaderColor = Colors.primary

        // SettingCard title ("Date Format", "Time Format", etc.)
        val cardTitleFontSize = 17.sp
        // SettingCard description ("Choose how dates are displayed")
        val cardDescriptionFontSize = 14.sp

        // RadioOption label text ("MM/DD/YYYY", "12-hour", "Memos")
        val radioLabelFontSize = 16.sp

        // InfoCell in License Card (label + value pairs)
        val infoLabelFontSize = 13.sp               // "Device", "Version", "Install ID"
        val infoValueFontSize = 14.sp                // The actual values

        // Chip text (time pickers, interval chips)
        val chipFontSize = 14.sp

        // Sub-labels inside cards ("Reminder Time", "Remind Before", "Active Language")
        val subLabelFontSize = 14.sp

        // Warning/permission card body text
        val explanationFontSize = 14.sp

        // Action icon buttons (eye, copy) - must be stylus-friendly
        val actionIconBoxSize = 28.dp                // Touch target for eye/copy buttons
        val actionIconSize = 18.dp                   // Icon inside the box
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATS DIALOG (Task Statistics - E-Ink Optimized)
    //
    // Enlarged dialog for task statistics display. E-Ink devices need:
    // - Larger fonts for readability on lower-contrast displays
    // - Bigger touch targets for stylus/finger input
    // - More generous spacing for visual clarity
    // - Thicker progress bars for visibility
    // ═══════════════════════════════════════════════════════════════════════════

    /** Task Stats Dialog - enlarged E-Ink optimized design */
    object StatsDialog {
        // Dialog container - LARGE for E-Ink readability; never wider than the screen allows (Go 6: 540)
        val width: Dp get() = minOf(580.dp, SCREEN_POPUP_MAX_WIDTH)  // Wider than standard overlay
        val padding = 20.dp                           // More generous padding
        val cornerRadius = Global.cornerRadius        // 6.dp consistent

        // Typography - ENLARGED for E-Ink
        val titleFontSize = 22.sp                     // Large header
        val sectionTitleFontSize = 18.sp              // Section headers
        val valueFontSize = 20.sp                     // Big numbers for stats
        val labelFontSize = 16.sp                     // Readable labels
        val smallLabelFontSize = 14.sp                // Secondary labels
        val hintFontSize = 14.sp                      // Contextual hints

        // Streak section
        val streakBadgeSize = 56.dp                   // Large badge for streak icon
        val streakBadgeIconSize = 28.sp               // Icon inside badge
        val streakTitleFontSize = 20.sp               // "X Day Streak"
        val streakMessageFontSize = 14.sp             // Motivational message

        // Mini stat boxes (Best Streak, Total Done, Days Active)
        val miniStatHeight = 44.dp                    // Taller rows
        val miniStatIconSize = 18.sp                  // Emoji/symbol size
        val miniStatLabelFontSize = 14.sp             // Label text
        val miniStatValueFontSize = 18.sp             // Value text
        val miniStatColumnWidth = 160.dp              // Wider column
        val miniStatRowSpacing = 8.dp                 // Space between rows
        val miniStatPaddingH = 12.dp                  // Horizontal padding
        val miniStatPaddingV = 10.dp                  // Vertical padding
        val miniStatCornerRadius = Global.cornerRadius

        // Progress bars (period stats)
        val progressBarHeight = 24.dp                 // Much thicker for E-Ink visibility
        val progressBarCornerRadius = 4.dp
        val progressBarFillCornerRadius = 3.dp
        val periodLabelWidth = 85.dp                  // Wider for "This Month"
        val periodCountWidth = 50.dp                  // Space for "XX/XX"
        val periodRowSpacing = 10.dp                  // Between period rows

        // Personal bests section
        val bestBadgeSize = 36.dp                     // Badge with symbol
        val bestBadgeIconSize = 16.sp                 // Symbol inside badge
        val bestLabelFontSize = 13.sp                 // "Best Day/Week/Month"
        val bestDateFontSize = 15.sp                  // Date label
        val bestCountFontSize = 22.sp                 // Big count number
        val bestRowHeight = 52.dp                     // Row height
        val bestRowSpacing = 10.dp                    // Between rows
        val bestRowPaddingH = 12.dp
        val bestRowPaddingV = 10.dp

        // Two-column layout
        val columnSpacing = 16.dp                     // Between columns
        val verticalDividerWidth = 1.dp
        val verticalDividerColor = Colors.dividerHairline  // Subtle column separator

        // Section spacing
        val headerSpacing = 8.dp                      // After header
        val sectionSpacing = 16.dp                    // Between major sections
        val dividerSpacing = 16.dp                    // Around horizontal divider
        val buttonTopSpacing = 20.dp                  // Before close button

        // Close button
        val buttonHeight = 52.dp                      // Larger touch target
        val buttonFontSize = 16.sp
        val buttonCornerRadius = Global.cornerRadius
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATES
    //
    // Reusable state definitions for selection, disabled, etc.
    // ═══════════════════════════════════════════════════════════════════════════

    /** Selected/active state (inverted colors) */
    object Selection {
        val background = Colors.primary      // Black
        val foreground = Colors.background   // White
    }

    /** Disabled/inactive state */
    object Disabled {
    val foreground = Colors.disabled
    val background = Colors.background
    }


    /** Press/hover state (subtle feedback) */
    object Pressed {
        val background = Colors.offWhite
        val foreground = Colors.primary
    }

    /**
     * Warning state - for non-critical alerts requiring user attention.
     * E-Ink optimized: Uses light gray background with dark border for visibility
     * without relying on yellow/orange colors that dither poorly on grayscale.
     */
    object Warning {
        val background = Colors.offWhite           // Light gray instead of yellow
        val borderColor = Colors.darkGray          // Dark gray border for visibility
        val borderWidth = 1.dp
        val iconColor = Colors.darkGray            // Consistent with border
        val textColor = Colors.darkGray            // Readable on light background
    }

    /**
     * Error state - for validation errors and critical issues.
     * E-Ink optimized: Uses high-contrast black for maximum visibility
     * instead of red which renders as mid-gray on monochrome displays.
     */
    object Error {
        val background = Colors.background         // White background
        val borderColor = Colors.primary           // Black border for emphasis
        val borderWidth = 1.dp
        val iconColor = Colors.primary             // Black icon
        val textColor = Colors.primary             // Black text for maximum contrast
    }

    /**
     * Alert badge - small red circular indicator with "!" text.
     * Used on toolbar buttons to indicate attention needed (permission, quota limit).
     * Kaleido 3 compatible: uses saturated red (#CC0000) for color e-ink visibility.
     */
    object AlertBadge {
        val size = 12.dp                           // Badge diameter
        val fontSize = 9.sp                        // "!" text size
        val fontWeight = FontWeight.Bold           // Bold for visibility at small size
        val lineHeight = 9.sp                      // Match fontSize for vertical centering
        val background = Colors.red                // #CC0000 - Alert/warning indicator
        val textColor = Colors.background          // White text on red background
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DIALOG SCROLLABLE CONTENT
    //
    // Settings for scrollable dialogs with content indicators.
    // ═══════════════════════════════════════════════════════════════════════════

    /** Dialog scrollable content settings */
    object Dialog {
        val maxContentHeight = 400.dp        // Maximum height before scrolling
        val scrollIndicatorWidth = 4.dp      // Width of scroll indicator bar
        val scrollIndicatorColor = Colors.dividerHairline  // Subtle scroll track
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BOOX NOTES DIALOG (Dec 2025)
    //
    // Enhanced notes dialog with smart grouping, suggestion chips, and search.
    // ═══════════════════════════════════════════════════════════════════════════

    /** Boox Notes dialog - full-featured note management */
    object BooxNotes {
        // Dialog sizing (large overlay) - increased by ~45px total for quick link space
        val dialogWidthFraction = 0.97f
        val dialogHeightFraction = 0.80f
        val dialogVerticalOffset = (-10).dp   // Move dialog 20px higher on screen

        // Event dropdown positioning
        val eventDropdownOffset = 78.dp       // Push dropdown below its header
        val eventDropdownMaxHeight = 230.dp   // Scrollable dropdown max height
        val eventDropdownBorderColor = Color.Black

        // Date selector dropdown (header) positioning
        val dateSelectorDropdownOffsetY = 310  // IntOffset Y pixels - push below header
        val dateSelectorDropdownWidth = 260.dp // Width to fit "Select date with Notes" on one line

        // Master-detail layout (list pane + detail pane)
        val listPaneWeight = 0.40f           // Left: notes list (40%)
        val detailPaneWeight = 0.60f         // Right: note preview (60%)
        val detailPaneWeightWider = 0.62f    // Right: +20px wider for note render area

        // Filter button ("View Unlinked")
        val filterButtonFontSize = 13.5.sp   // 0.5sp smaller than bodyMedium (14sp)

        // Chevron navigation (prev/next note)
        val chevronSize = 36.dp              // Touch-friendly chevron button
        val chevronIconSize = 28.dp          // Icon inside chevron
        val chevronIconColor = Colors.primary
        val chevronIconColorDisabled = Colors.lightGray

        // Header
        val headerHeight = 48.dp
        val headerFontSize = Typography.titleMedium
        val headerColor = Colors.primary

        // Search bar
        val searchBarHeight = 40.dp
        val searchBarBackground = Colors.offWhite
        val searchBarBorderWidth = 1.dp
        val searchBarBorderColor = Colors.darkGray
        val searchBarFontSize = Typography.bodyMedium
        val searchBarHintColor = Colors.gray
        val searchIconSize = 20.dp

        // Filter chips
        val filterChipHeight = 28.dp
        val filterChipPaddingH = 10.dp
        val filterChipFontSize = Typography.labelMedium
        val filterChipBackground = Colors.background
        val filterChipSelectedBackground = Colors.primary
        val filterChipBorderWidth = 1.dp
        val filterChipBorderColor = Colors.darkGray
        val filterChipTextColor = Colors.primary
        val filterChipSelectedTextColor = Colors.background
        val filterChipSpacing = 6.dp

        // Section headers (Needs Attention, Linked Notes, General Notes)
        val sectionHeaderHeight = 36.dp
        val sectionHeaderFontSize = Typography.titleSmall
        val sectionHeaderColor = Colors.darkGray
        val sectionHeaderBackground = Colors.offWhite
        val sectionHeaderPadding = 12.dp

        // Note cards
        val noteCardHeight = 72.dp
        val noteCardPadding = 10.dp
        val noteCardSpacing = 6.dp
        val noteCardBorderWidth = 1.dp
        val noteCardBorderColor = Colors.borderStrong  // Kaleid 3 visible
        val noteCardSelectedBorderColor = Colors.primary
        val noteCardSelectedBorderWidth = 2.dp
        val noteCardBackground = Colors.background
        val noteCardSelectedBackground = Colors.offWhite

        // Note card content
        val noteTitleFontSize = Typography.bodyMedium
        val noteTitleColor = Colors.primary
        val noteMetaFontSize = Typography.bodySmall
        val noteMetaColor = Colors.darkGray
        val noteTimestampFontSize = Typography.labelSmall
        val noteTimestampColor = Colors.gray

        // Thumbnail in note card
        val thumbnailSize = 52.dp
        val thumbnailBorderWidth = 1.dp
        val thumbnailBorderColor = Colors.dividerHairline  // Small element, subtle OK

        // Status indicators
        val statusDotSize = 8.dp
        val statusAttentionColor = Color(0xFFE57373)  // Soft red - needs attention
        val statusLinkedColor = Color(0xFF81C784)     // Soft green - linked
        val statusGeneralColor = Colors.gray          // Gray - general note

        // Suggestion chips (inside note cards)
        val suggestionChipHeight = 28.dp
        val suggestionChipPaddingH = 10.dp
        val suggestionChipFontSize = Typography.labelSmall
        val suggestionChipBackground = Colors.offWhite
        val suggestionChipBorderWidth = 1.dp
        val suggestionChipBorderColor = Colors.darkGray
        val suggestionChipTextColor = Colors.darkGray
        val suggestionChipSpacing = 6.dp

        // Accept/Dismiss buttons on suggestions
        val suggestionButtonSize = 24.dp
        val suggestionAcceptColor = Color(0xFF4CAF50)  // Green check
        val suggestionDismissColor = Color(0xFFE57373) // Red X

        // Confidence badge
        val confidenceBadgeHeight = 20.dp
        val confidenceBadgePaddingH = 6.dp
        val confidenceBadgeFontSize = Typography.labelSmall
        val confidenceHighBackground = Color(0xFF81C784)
        val confidenceHighTextColor = Colors.background
        val confidenceMediumBackground = Color(0xFFFFD54F)
        val confidenceMediumTextColor = Colors.primary
        val confidenceLowBackground = Color(0xFFE57373)
        val confidenceLowTextColor = Colors.background

        // Preview pane (right side)
        val previewPaneWidthFraction = 0.55f
        val previewTitleFontSize = Typography.titleSmall
        val previewTitleColor = Colors.primary
        val previewMetaFontSize = Typography.bodySmall
        val previewMetaColor = Colors.darkGray

        // Match reasons display
        val matchReasonChipHeight = 24.dp
        val matchReasonChipPaddingH = 8.dp
        val matchReasonChipFontSize = Typography.labelSmall
        val matchReasonChipBackground = Colors.offWhite
        val matchReasonChipTextColor = Colors.darkGray

        // Empty states
        val emptyStateIconSize = 48.dp
        val emptyStateFontSize = Typography.bodyMedium
        val emptyStateColor = Colors.gray

        // Attention count badge (in header)
        val attentionBadgeSize = 22.dp
        val attentionBadgeFontSize = Typography.labelSmall
        val attentionBadgeBackground = Color(0xFFE57373)
        val attentionBadgeTextColor = Colors.background

        // Attention indicator (shown next to notes needing user review)
        val attentionIconSize = 16.dp
        val attentionIconColor = Colors.darkGray  // Grayscale-friendly
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TASK EXPORT DIALOG (Jan 2026)
    //
    // Redesigned single-step export dialog with visual hierarchy.
    // Features: 2x2 scope grid, large option cards with icons, live preview.
    // ═══════════════════════════════════════════════════════════════════════════

    /** Task Export Dialog - redesigned single-step layout */
    object TaskExport {
        // Dialog size - consistent with ModernDialog
        val dialogWidthFraction = ModernDialog.widthFraction  // 0.82f

        // Section labels
        val sectionLabelFontSize = Typography.bodySmall
        val sectionLabelColor = Colors.gray
        val sectionSpacing = 12.dp

        // Scope grid (2x2)
        val scopeGridGap = 8.dp
        val scopeCardHeight = 52.dp              // Touch-friendly (exceeds 44dp minimum)
        val scopeCardPadding = 10.dp
        val scopeCardBorderWidth = 1.dp
        val scopeCardBorderColor = Colors.borderStrong
        val scopeCardSelectedBorderWidth = 2.dp
        val scopeCardSelectedBorderColor = Colors.primary
        val scopeCardSelectedBackground = Colors.offWhite
        val scopeLabelFontSize = Typography.bodyMedium
        val scopeCountFontSize = Typography.bodySmall
        val scopeCountColor = Colors.gray

        // Format/Action option cards (side-by-side)
        val optionCardHeight = 64.dp             // Room for icon + label + subtitle
        val optionCardGap = 12.dp
        val optionCardPadding = 8.dp
        val optionIconSize = 18.dp
        val optionLabelFontSize = Typography.bodyLarge
        val optionSubtitleFontSize = Typography.labelSmall
        val optionSubtitleColor = Colors.gray

        // Preview line
        val previewFontSize = Typography.bodySmall
        val previewColor = Colors.gray
        val previewPaddingVertical = 8.dp
        val previewPaddingHorizontal = 12.dp
        val previewBackground = Colors.offWhite
        val previewCornerRadius = 4.dp

        // Radio indicator
        val radioSize = 16.dp
        val radioBorderWidth = 1.dp
        val radioInnerPadding = 3.dp
    }
}
