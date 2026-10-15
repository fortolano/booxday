# Boox Day Quick Reference

> **Last Verified**: December 2025
> **For**: Developers and AI agents working on Boox Day
> **See Also**: CLAUDE.md (ultra-quick ref), memory.md (detailed ref)

---

## Project Info

| Property | Value |
|----------|-------|
| **Package** | `com.boox.day` |
| **Min SDK** | 29 (Android 10) |
| **Target SDK** | 35 (Android 14) |
| **Compile SDK** | 36 |
| **Database Version** | 18 (Multi-provider sync) |
| **Platform** | Onyx Boox E-Ink devices |
| **Screen Size** | 1404x1872 (landscape) |

---

## File Locations Guide

### "Where is...?"

| Component | File Path |
|-----------|-----------|
| **Entry point** | `app/src/main/java/com/boox/day/MainActivity.kt` |
| **App init** | `app/src/main/java/com/boox/day/BooxDayApp.kt` |
| **Navigation** | `app/src/main/java/com/boox/day/ui/Router.kt` |
| **Main UI** | `app/src/main/java/com/boox/day/ui/views/MainScreen.kt` |
| **Canvas** | `app/src/main/java/com/boox/day/editor/DrawCanvas.kt` |
| **Page state** | `app/src/main/java/com/boox/day/editor/PageView.kt` |
| **Editor state** | `app/src/main/java/com/boox/day/editor/state/EditorState.kt` |
| **Tab routing** | `app/src/main/java/com/boox/day/editor/EditorTabManager.kt` |
| **Database** | `app/src/main/java/com/boox/day/data/db/Db.kt` |
| **Settings** | `app/src/main/java/com/boox/day/data/datastore/AppSettings.kt` |
| **Theme** | `app/src/main/java/com/boox/day/ui/theme/EInkTheme.kt` |
| **AI client** | `app/src/main/java/com/boox/day/api/ai/AiClient.kt` |
| **Sync manager** | `app/src/main/java/com/boox/day/sync/MultiAccountSyncManager.kt` |
| **Cache manager** | `app/src/main/java/com/boox/day/data/PageDataManager.kt` |

---

## Build & Deploy

### Build APK

```bash
# From project root
powershell -Command "Set-Location 'C:\Users\fort\Projects\Boox_Day'; & .\gradlew.bat assembleDebug"

# Output location
# app/build/outputs/apk/debug/app-debug.apk
```

### Install to Device

```bash
# Install (replace existing)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# If signature conflict:
adb uninstall com.boox.day
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Reset App Data

```bash
# Clear all data (reset database)
adb shell pm clear com.boox.day
```

---

## Common Tasks

### Task: Add a New Dialog

1. **Create dialog file**:
   ```kotlin
   // ui/dialogs/MyDialog.kt
   @Composable
   fun MyDialog(onDismiss: () -> Unit) {
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
               // Content
           }
       }
   }
   ```

2. **Import noRippleClickable**:
   ```kotlin
   import com.boox.day.ui.noRippleClickable
   ```

3. **Use in composable**:
   ```kotlin
   if (showDialog) {
       MyDialog(onDismiss = { showDialog = false })
   }
   ```

**References**:
- `ui/dialogs/AddEditEventDialog.kt` - Full example
- `docs/ui-patterns.md` - Dialog patterns

---

### Task: Add a Database Field

1. **Update entity**:
   ```kotlin
   @Entity
   data class Task(
       val newField: String = "default"  // MUST provide default!
   )
   ```

2. **Create migration**:
   ```kotlin
   // In Db.kt
   private val MIGRATION_18_19 = object : Migration(18, 19) {
       override fun migrate(database: SupportSQLiteDatabase) {
           database.execSQL(
               "ALTER TABLE Task ADD COLUMN newField TEXT NOT NULL DEFAULT 'default'"
           )
       }
   }
   ```

3. **Update database version**:
   ```kotlin
   @Database(..., version = 19)
   ```

4. **Register migration**:
   ```kotlin
   .addMigrations(MIGRATION_18_19)
   ```

5. **Test**:
   ```bash
   adb shell pm clear com.boox.day  # Test migration from fresh install
   ```

**Reference**: `docs/database.md` - Migration strategy

---

### Task: Add a Toolbar Button

1. **Add icon to Toolbar.kt**:
   ```kotlin
   ToolButton(
       icon = Icons.Default.MyIcon,
       isActive = state.someProperty,
       onClick = {
           editorTabManager.getActiveState().someProperty = !someProperty
       }
   )
   ```

2. **Update EditorState.kt** (if needed):
   ```kotlin
   class EditorState {
       var someProperty by mutableStateOf(false)
   }
   ```

**Reference**: `editor/ui/Toolbar.kt`

---

### Task: Add AI Model

1. **Update ModelId enum**:
   ```kotlin
   // api/ai/Models.kt
   enum class ModelId(val value: String) {
       MY_MODEL("provider/model-name")
   }
   ```

2. **Add capabilities**:
   ```kotlin
   fun ModelId.capabilities() = when (this) {
       MY_MODEL -> setOf(Capability.VISION, Capability.JSON)
   }
   ```

3. **Use in service**:
   ```kotlin
   aiRequest {
       model = ModelId.MY_MODEL
       prompt = "..."
   }
   ```

**Reference**: `api/ai/Models.kt`, `docs/ai-integration.md`

---

### Task: Add a New Entity

See "Add a Database Field" above, plus:

1. **Create entity file** (e.g., `MyEntity.kt`)
2. **Create DAO interface** (e.g., `MyEntityDao.kt`)
3. **Add to AppDatabase**:
   ```kotlin
   @Database(
       entities = [..., MyEntity::class],
       version = 19
   )
   abstract class AppDatabase : RoomDatabase() {
       abstract fun myEntityDao(): MyEntityDao
   }
   ```

4. **Create migration** with `CREATE TABLE` SQL
5. **Create repository** in `data/repositories/`

**Reference**: `docs/database.md` - Adding entities

---

## Debugging

### Logcat Filters

```bash
# DrawCanvas debug
adb logcat -s "DrawCanvas"

# Performance traces
adb logcat -s "DrawCanvas" | grep "PERF_DEBUG"

# Touch events
adb logcat -s "DrawCanvas" | grep "TOUCH_TRACE"

# AI requests
adb logcat -s "AiClient"

# Sync operations
adb logcat -s "MultiAccountSyncManager"
```

### Take Screenshot

```bash
# Method 1: ADB pull (recommended for Windows)
adb shell "screencap /sdcard/screenshot.png" && powershell -Command "adb pull /sdcard/screenshot.png 'C:\Users\fort\Projects\screenshot.png'" && adb shell "rm /sdcard/screenshot.png"

# Method 2: Direct (works on Mac/Linux)
adb exec-out screencap -p > screenshot.png
```

**Note**: On Windows, use Method 1 to avoid binary corruption.

---

## Database Operations

### View Database

```bash
# Pull database
adb pull /data/data/com.boox.day/files/booxdaydb/app_database ~/boox_day.db

# Open with sqlite3
sqlite3 ~/boox_day.db
.tables
SELECT * FROM Task WHERE dayDate = '2025-01-15';
```

### Export Database (in code)

```kotlin
val dbFile = context.getDatabasePath("app_database")
val exportFile = File(context.getExternalFilesDir(null), "backup.db")
dbFile.copyTo(exportFile, overwrite = true)
```

---

## NO-TOUCH Zones

**CRITICAL**: Do not modify these sections without understanding them fully.

| File | Lines | What | Why |
|------|-------|------|-----|
| **DrawCanvas.kt** | ~1585-1660 | `reactivateRawDrawing()` cycle | Close→open required to apply limit rect |
| **DrawCanvas.kt** | ~1150-1200 | `memoModeChanged` observer | Tab change detection |
| **DrawCanvas.kt** | ~194-253 | `cancelObservers()` + `updateState()` | Observer lifecycle |
| **DrawCanvas.kt** | ~826-895 | `withRawDrawingOwner()` mutex | Prevents concurrent raw drawing |
| **DrawCanvas.kt** | ~341-345 | `lastStylusActivityMs` tracking | Palm rejection |
| **DrawCanvas.kt** | ~418-435 | `flushUIUpdates()` guard | Prevents fast draw loss |
| **DrawCanvas.kt** | ~605 | `drawCanvasToView(null)` after `handleDraw()` | **FLICK BUG FIX** |
| **AppSettings.kt** | ~79-92 | Gesture defaults (all None) | Palm rejection |
| **PageDataManager.kt** | ~150-160, ~180-220 | `activePages` + deduplication | Atomic persistence |
| **PageView.kt** | ~286-310 | `disposeOldPage()` flow | Bitmap lifecycle |
| **History.kt** | ~166-174 | Max-5 operation limit | Memory management |

**See**: CLAUDE.md, memory.md Section 12, `.claude/FLICK_BUG_FIX.md`

---

## Common Patterns

### Pattern: Database Query

```kotlin
// ❌ WRONG - Direct DAO call
val tasks = appDatabase.taskDao().getByDayDate(date)

// ✅ CORRECT - Repository pattern
val tasks = appRepository.taskRepository.getByDayDate(date)

// With coroutine
suspend fun loadTasks(date: String) = withContext(Dispatchers.IO) {
    appRepository.taskRepository.getByDayDate(date)
}
```

### Pattern: Observer Lifecycle

```kotlin
// ❌ WRONG - Zombie observers accumulate
coroutineScope.launch {
    DrawCanvas.forceUpdate.collect { rect ->
        // This will NEVER be cancelled
    }
}

// ✅ CORRECT - Cancelled on updateState()
observerScope!!.launch {
    DrawCanvas.forceUpdate.collect { rect ->
        // Cancelled when page changes
    }
}
```

### Pattern: Bitmap Persistence

```kotlin
// On disposal (must save now)
forcePersistBitmap()

// During editing (debounced)
persistBitmapDebounced()
```

### Pattern: Cross-Component Communication

```kotlin
// Trigger canvas redraw
DrawCanvas.forceUpdate.emit(dirtyRect)

// Update toolbar state
DrawCanvas.refreshUi.emit(Unit)

// Update calendar badge
DrawCanvas.contentChanged.emit(pageId)

// Signal drawing state
DrawCanvas.isDrawing.value = true
```

---

## Code Snippets

### Get Current Page

```kotlin
val pageId = withContext(Dispatchers.IO) {
    dayPageRepository.getOrCreatePageForDate(selectedDate)
}
```

### Load Strokes for Page

```kotlin
val strokes = withContext(Dispatchers.IO) {
    strokeRepository.getByPageId(pageId)
}
```

### Save Stroke

```kotlin
suspend fun saveStroke(stroke: Stroke) = withContext(Dispatchers.IO) {
    strokeRepository.insert(stroke)
}
```

### Update GlobalAppSettings

```kotlin
val newSettings = GlobalAppSettings.current.copy(
    taskDueDatesEnabled = true
)
appRepository.kvProxy.setAppSettings(newSettings)
```

### Check if Day Has Notes

```kotlin
val datesWithNotes = withContext(Dispatchers.IO) {
    dayPageRepository.getDatesWithNotesForMonth("2025-01")
}
val hasNotes = "2025-01-15" in datesWithNotes
```

---

## Keyboard Shortcuts (When Implemented)

| Key | Action |
|-----|--------|
| Ctrl+Z | Undo |
| Ctrl+Y | Redo |
| Ctrl+A | Select All |
| Delete | Delete selected |
| Escape | Deselect / Close dialog |

**Note**: Physical keyboard support is minimal on E-Ink devices.

---

## Environment Variables

None required. All configuration in `AppSettings.kt`.

---

## Dependencies

### Key Libraries

| Library | Version | Purpose |
|---------|---------|---------|
| **Jetpack Compose** | 1.7+ | UI framework |
| **Room** | 2.6+ | Database |
| **Koin** | 3.5+ | Dependency injection |
| **Retrofit** | 2.9+ | Networking (AI API) |
| **OkHttp** | 4.12+ | HTTP client |
| **Kotlin Coroutines** | 1.8+ | Async/concurrency |
| **Onyx SDK** | 1.1.0+ | Stylus/E-Ink support |
| **MSAL** | 5.0+ | Microsoft auth |
| **Google Sign-In** | 21.0+ | Google auth |

### Gradle Commands

```bash
# List dependencies
.\gradlew.bat app:dependencies

# Update dependencies (check for updates)
.\gradlew.bat dependencyUpdates

# Clean build
.\gradlew.bat clean assembleDebug
```

---

## Play Store Notes

### HiddenApiBypass

**Required**: App crashes without it (Onyx SDK needs hidden APIs).

```gradle
// build.gradle.kts
dependenciesInfo {
    includeInApk = false  // Hides from Play Store scanner
}
```

**Do NOT remove** `HiddenApiBypass.addHiddenApiExemptions("")` from `BooxDayApp.kt`.

### Required Permissions

```xml
<!-- Required -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Calendar sync -->
<uses-permission android:name="android.permission.READ_CALENDAR" />
<uses-permission android:name="android.permission.WRITE_CALENDAR" />

<!-- Optional (Boox Notes integration only) -->
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

---

## Testing

### Unit Tests

```bash
.\gradlew.bat test
```

### Instrumented Tests

```bash
.\gradlew.bat connectedAndroidTest
```

**Note**: Most testing is manual due to E-Ink device-specific behavior.

---

## Performance Profiling

### Canvas Performance

Check `DrawCanvas.kt` logs:
```
PERF_DEBUG: handleDraw took 12ms
PERF_DEBUG: reactivateRawDrawing took 8ms
```

### Database Performance

Enable Room logging:
```kotlin
Room.databaseBuilder(...)
    .setQueryCallback(QueryCallback { sqlQuery, bindArgs ->
        Log.d("RoomQuery", "Query: $sqlQuery")
    }, Dispatchers.IO.asExecutor())
```

---

## Useful Links

### Internal Docs

- **CLAUDE.md** - Ultra-quick reference
- **memory.md** - Detailed implementation notes
- **docs/architecture.md** - System architecture
- **docs/database.md** - Database reference
- **docs/ui-patterns.md** - E-Ink UI guide
- **docs/EINKTHEME_CHEATSHEET.md** - Design system
- **docs/ai-integration.md** - AI/OpenRouter guide
- **.claude/docs/** - Domain context files (drawing, data, sync, AI)

### Context Commands

Use these to load domain-specific context in Claude Code:

```
@memory.md              # Full implementation reference
/ai-context            # AI/voice features
/drawing-context       # Canvas/stylus details
/data-context          # Database/caching
/ui-context            # Calendar/toolbar
/eink-theme            # Theme system
/sync-context          # Multi-provider sync
/perf-context          # Performance notes
```

---

## Emergency Recovery

### App Won't Start

```bash
# Clear app data
adb shell pm clear com.boox.day

# Uninstall + reinstall
adb uninstall com.boox.day
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Database Corrupted

```bash
# Reset database
adb shell pm clear com.boox.day
```

### Canvas Not Responding

Check logs for:
- `TouchHelper initialization failed`
- `Raw drawing callback null`
- `OpenGL context lost`

**Fix**: Usually requires app restart.

---

## Common Errors

| Error | Cause | Fix |
|-------|-------|-----|
| `HiddenApiBypass not found` | Missing dependency | Check `build.gradle.kts` |
| `TouchHelper null` | Onyx SDK init failed | Check `BooxDayApp.onCreate()` |
| `SurfaceView not ready` | Canvas lifecycle issue | Wait for `surfaceCreated` callback |
| `Room migration failed` | Missing migration | Add migration v17→v18 |
| `Bitmap too large` | Memory issue | Reduce bitmap dimensions or quality |

---

## Tips & Tricks

### Faster Builds

```bash
# Skip tests
.\gradlew.bat assembleDebug -x test

# Parallel execution
.\gradlew.bat assembleDebug --parallel
```

### Quick Install

```bash
# Build + install in one command
.\gradlew.bat installDebug
```

### View Crash Logs

```bash
adb logcat -s "AndroidRuntime"
```

---

## Contact & Support

- **Primary Docs**: CLAUDE.md, memory.md
- **Context Files**: `.claude/docs/` directory
- **Issue Tracker**: `.github/ISSUE_TEMPLATE/bug_report.md`

---

## Key Takeaways

1. **Always use repositories**, not DAOs directly
2. **Always use EInkTheme**, not hardcoded values
3. **Always use Popup**, not Dialog
4. **Always use noRippleClickable**, not .clickable
5. **Never modify NO-TOUCH zones** without understanding
6. **Migrations required** for all database changes
7. **Reset database** via `adb shell pm clear com.boox.day`
8. **Build on Windows** via PowerShell wrapper (gradlew.bat)
9. **HiddenApiBypass required** for Onyx SDK
10. **Reference CLAUDE.md** for quickest lookup

---

**Last Updated**: December 2025
**Maintainer**: Claude Code
**Questions**: Start with CLAUDE.md → memory.md → specific docs as needed
