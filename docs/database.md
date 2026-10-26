# BooxDay Database Reference

> **Last Verified**: March 2026
> **Covers Files**: Db.kt, all entity files, all DAO files
> **Current Version**: 25 (First public production baseline)
> **Database Location**: `context.filesDir/booxdaydb/app_database`

---

## Overview

BooxDay uses Room (SQLite) for local storage. The database is stored in **internal storage** (no external storage permission needed). Reset via: `adb shell pm clear com.boox.day`.

**Key Characteristics**:
- **24 entities** (as of v25)
- **Version 25** is the first public production baseline
- **SB1 binary encoding** for stroke points (efficient storage + compression)
- **Multi-provider sync support** (Google Calendar, Microsoft Outlook)
- **Future schema changes after v25 require**: version bump + migration + schema export + migration test

---

## Database Version History

| Version | Date | Changes |
|---------|------|---------|
| **v8** | Initial | Core entities (Page, Stroke, DayPage, CalendarEvent) |
| **v9** | - | Add `linkedEventId` to Page |
| **v10** | - | TextAnnotation additions |
| **v11** | - | BooxNotes integration (BooxNoteIndex, BooxNoteLink) |
| **v12** | - | Task entity |
| **v13** | - | Attachment entity |
| **v14** | - | LocalEvent, LocalCalendar (sync metadata) |
| **v15** | - | SummaryCache (AI summaries) |
| **v16** | - | Task.priority field |
| **v17** | ~2024 | Task.dueDate field (epoch ms, nullable) |
| **v18** | Dec 2025 | **Multi-provider sync**: ConnectedAccountEntity, CalendarSelectionEntity |
| **v25** | Mar 2026 | **First public production baseline** with OCR, semantic, announcement, and note aggregate tables |

**Migration Policy**:
- historical pre-public migrations exist in `Db.kt` and `Migrations.kt`
- the public support promise starts at **v25**
- every schema change after v25 must include a migration and test

**Location**: `app/src/main/java/com/boox/day/data/db/Db.kt`

---

## Entity Reference

### Core Drawing Entities

#### Page

**Purpose**: Represents a drawing page (canvas background + metadata).

**Schema**:
```kotlin
@Entity
data class Page(
    @PrimaryKey val id: String = Nanoid.generate(),
    val notebookId: String = "",              // Parent notebook (unused in Day mode)
    val title: String = "",                   // Unused in Day mode
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val background: String = "#FFFFFF",       // Hex color
    val backgroundType: BackgroundType = BackgroundType.BLANK,  // BLANK, IMAGE, PDF
    val backgroundImagePath: String? = null,  // Path to background image
    val linkedEventId: String? = null         // v9: Link to CalendarEvent
)
```

**Key Notes**:
- **One page = one drawing surface** (fixed 1404x1872 bitmap)
- `linkedEventId` connects drawing to calendar event (optional)
- Background can be solid color, image, or PDF page

**DAO**: `PageDao` (`getById`, `insert`, `update`, `delete`)

**File**: `app/src/main/java/com/boox/day/data/db/Page.kt`

---

#### Stroke

**Purpose**: Individual ink strokes drawn by user.

**Schema**:
```kotlin
@Entity(
    indices = [Index("pageId")]
)
data class Stroke(
    @PrimaryKey val id: String = Nanoid.generate(),
    val pageId: String,                       // FK → Page
    val pen: Pen,                             // BALLPEN, PENCIL, BRUSH, MARKER, FOUNTAIN
    val color: Int,                           // ARGB int
    val points: List<StrokePoint>,            // Stored as SB1 binary (see below)
    val pressure: Float = 1f,                 // Unused (pressure in points)
    val isEraser: Boolean = false,            // Unused (eraser is separate mode)
    val createdAt: Date = Date()
)

data class StrokePoint(
    val x: Float,                             // Canvas coordinates
    val y: Float,
    val pressure: Float,                      // 0.0-1.0
    val tiltX: Float = 0f,                    // Unused
    val tiltY: Float = 0f                     // Unused
)
```

**SB1 Binary Encoding**:
- **Format**: 9-byte header + polyline-encoded coordinates + optional LZ4 compression
- **Header**: Magic (SB1), version (1), mask (presence bits), count (u16), compression (0/1)
- **Compression**: Applied if body ≥512 bytes
- **Efficiency**: ~70% size reduction for typical strokes

**Type Converter**:
```kotlin
@TypeConverter
fun fromStrokePoints(points: List<StrokePoint>?): ByteArray?
    // Encodes to SB1 binary

@TypeConverter
fun toStrokePoints(bytes: ByteArray?): List<StrokePoint>?
    // Decodes from SB1 binary
```

**DAO**: `StrokeDao` (`getByPageId`, `insert`, `delete`, `deleteByIds`)

**Files**:
- `app/src/main/java/com/boox/day/data/db/Stroke.kt`
- `app/src/main/java/com/boox/day/data/db/StrokeEncoding.kt` (SB1 implementation)

**See Also**: Legacy docs at `docs/database-structure.md` (NOTE: May be outdated)

---

#### Image

**Purpose**: Images placed on canvas (not backgrounds).

**Schema**:
```kotlin
@Entity(
    indices = [Index("pageId")]
)
data class Image(
    @PrimaryKey val id: String = Nanoid.generate(),
    val pageId: String,                       // FK → Page
    val path: String,                         // File path or URI
    val x: Float,                             // Top-left position
    val y: Float,
    val width: Float,                         // Rendered dimensions
    val height: Float,
    val createdAt: Date = Date()
)
```

**DAO**: `ImageDao` (`getByPageId`, `insert`, `delete`)

**File**: `app/src/main/java/com/boox/day/data/db/Image.kt`

---

### Calendar Bridge Entities

#### DayPage

**Purpose**: Maps calendar dates to drawing pages (Memos + Tasks).

**Schema**:
```kotlin
@Entity
data class DayPage(
    @PrimaryKey val date: String,             // "yyyy-MM-dd"
    val pageIds: List<String>                 // [memosPageId, tasksPageId]
)
```

**Key Notes**:
- **One date = up to 2 pages** (Memos tab, Tasks tab)
- `pageIds[0]` = Memos page
- `pageIds[1]` = Tasks page (optional)
- Created on-demand via `getOrCreatePageForDate()`

**DAO Methods**:
```kotlin
suspend fun getByDate(date: String): DayPage?
suspend fun getDatesWithNotesForMonth(yearMonth: String): List<String>
suspend fun getOrCreatePageForDate(date: String, createIfMissing: Boolean = true): String
```

**File**: `app/src/main/java/com/boox/day/data/db/DayPage.kt`

---

#### CalendarEvent

**Purpose**: Cached calendar events from device Calendar Provider (legacy, v14-).

**Schema**:
```kotlin
@Entity(
    indices = [Index("dayId"), Index("calendarId")]
)
data class CalendarEvent(
    @PrimaryKey val id: String = Nanoid.generate(),
    val dayId: String,                        // "yyyy-MM-dd"
    val title: String,
    val description: String?,
    val startTime: Long,                      // Epoch ms
    val endTime: Long,
    val location: String?,
    val calendarId: Long,                     // Device calendar ID
    val eventId: Long,                        // Device event ID
    val isAllDay: Boolean = false,
    val color: Int? = null
)
```

**Status**: Legacy. Superseded by `LocalEvent` + multi-provider sync (v18+).

**DAO**: `CalendarEventDao` (basic CRUD)

**File**: `app/src/main/java/com/boox/day/data/db/CalendarEvent.kt`

---

#### LocalEvent (v14+)

**Purpose**: Sync-aware calendar events with multi-provider support.

**Schema**:
```kotlin
@Entity(
    indices = [
        Index("dayId"),
        Index("syncStatus"),
        Index("googleEventId"),              // v14-v17
        Index("providerEventId"),            // v18+
        Index("connectedAccountId")          // v18+
    ]
)
data class LocalEvent(
    @PrimaryKey val id: String = Nanoid.generate(),
    val dayId: String,                        // "yyyy-MM-dd"
    val title: String,
    val description: String?,
    val startTime: Long,                      // Epoch ms
    val endTime: Long,
    val location: String?,
    val isAllDay: Boolean = false,
    val color: Int? = null,

    // Sync metadata
    val syncStatus: SyncStatus,               // LOCAL_ONLY, PENDING_CREATE, SYNCED, etc.
    val googleEventId: String? = null,        // DEPRECATED v18+ (use providerEventId)
    val providerEventId: String? = null,      // v18+: Provider's event ID
    val connectedAccountId: String? = null,   // v18+: FK → ConnectedAccountEntity
    val calendarId: String? = null,           // Provider's calendar ID
    val lastSyncAt: Long? = null,
    val localModifiedAt: Long = System.currentTimeMillis()
)

enum class SyncStatus {
    LOCAL_ONLY,          // Created locally, never synced
    PENDING_CREATE,      // Queue for upload
    PENDING_UPDATE,      // Local changes pending
    PENDING_DELETE,      // Marked for deletion
    SYNCED,              // In sync with provider
    CONFLICT             // Conflicting changes
}
```

**Key Notes**:
- **v18 migration**: `googleEventId` → `providerEventId` (multi-provider support)
- **Conflict resolution**: Manual (user chooses local vs remote)
- **Soft delete**: `PENDING_DELETE` allows undo before actual deletion

**DAO Methods**:
```kotlin
suspend fun getByDayId(dayId: String): List<LocalEvent>
suspend fun getByStatus(status: SyncStatus): List<LocalEvent>
suspend fun getByAccountId(accountId: String): List<LocalEvent>
suspend fun getPendingSync(): List<LocalEvent>
```

**File**: `app/src/main/java/com/boox/day/data/db/LocalEvent.kt`

---

#### LocalCalendar (v14+)

**Purpose**: Sync metadata for device calendars.

**Schema**:
```kotlin
@Entity
data class LocalCalendar(
    @PrimaryKey val id: String,               // Device calendar ID (string)
    val accountName: String,
    val displayName: String,
    val color: Int,
    val isEnabled: Boolean = true,            // Sync this calendar?
    val lastSyncAt: Long? = null
)
```

**Status**: Partially superseded by `CalendarSelectionEntity` (v18+). Still used for device calendar provider integration.

**File**: `app/src/main/java/com/boox/day/data/db/LocalCalendar.kt`

---

### Multi-Provider Sync Entities (v18+)

#### ConnectedAccountEntity

**Purpose**: Stores connected calendar accounts (Google, Outlook).

**Schema**:
```kotlin
@Entity(
    tableName = "connected_accounts",
    indices = [Index("providerId"), Index("email")]
)
data class ConnectedAccountEntity(
    @PrimaryKey val id: String = Nanoid.generate(),
    val providerId: String,                   // "GOOGLE" | "OUTLOOK"
    val accountType: String,                  // Personal, Workspace, Work/School
    val email: String,
    val displayName: String? = null,
    val refreshToken: String? = null,         // Encrypted (Outlook only)
    val accessToken: String? = null,          // Short-lived token
    val accessTokenExpiresAt: Long? = null,   // Epoch ms
    val isActive: Boolean = true,             // Soft delete
    val lastSyncAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
```

**Key Notes**:
- **Google**: Uses Intent-based auth, no refresh token stored
- **Outlook**: Uses MSAL, refresh token stored encrypted
- **Soft delete**: `isActive = false` preserves events

**DAO Methods**:
```kotlin
suspend fun getActiveAccounts(): List<ConnectedAccountEntity>
suspend fun getByProviderId(providerId: String): List<ConnectedAccountEntity>
suspend fun getById(id: String): ConnectedAccountEntity?
suspend fun markInactive(id: String)
```

**File**: `app/src/main/java/com/boox/day/data/db/ConnectedAccountEntity.kt`

---

#### CalendarSelectionEntity

**Purpose**: Per-calendar sync settings within an account.

**Schema**:
```kotlin
@Entity(
    tableName = "calendar_selections",
    foreignKeys = [
        ForeignKey(
            entity = ConnectedAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE        // Delete when account deleted
        )
    ],
    indices = [Index("accountId"), Index("calendarId")]
)
data class CalendarSelectionEntity(
    @PrimaryKey val id: String = Nanoid.generate(),
    val accountId: String,                    // FK → ConnectedAccountEntity
    val calendarId: String,                   // Provider's calendar ID
    val calendarName: String,
    val calendarColor: Int? = null,
    val isFetchEnabled: Boolean = true,       // Sync this calendar?
    val isDefaultForAccount: Boolean = false  // Create new events here?
)
```

**Key Notes**:
- **Cascade delete**: Removing account removes all calendar selections
- **One default per account**: Only one calendar marked as default
- **Granular control**: Enable/disable sync per calendar

**DAO Methods**:
```kotlin
suspend fun getByAccountId(accountId: String): List<CalendarSelectionEntity>
suspend fun getEnabledByAccountId(accountId: String): List<CalendarSelectionEntity>
suspend fun getDefaultForAccount(accountId: String): CalendarSelectionEntity?
suspend fun setDefault(id: String, accountId: String)
```

**File**: `app/src/main/java/com/boox/day/data/db/CalendarSelectionEntity.kt`

---

### Task Management Entities

#### Task

**Purpose**: Task item with priority, due date, and completion tracking.

**Schema**:
```kotlin
@Entity(
    indices = [
        Index("dayDate"),
        Index("dueDate"),                     // v17+
        Index("rowIndex")
    ]
)
data class Task(
    @PrimaryKey val id: String = Nanoid.generate(),
    val dayDate: String,                      // "yyyy-MM-dd" (parent day)
    val rowIndex: Int,                        // 0-9 (10 tasks per day)
    val priority: Int = 0,                    // 0-3 (v16+)
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val dueDate: Long? = null,                // v17+: Epoch ms (nullable)
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)
```

**Key Notes**:
- **10 tasks per day**: `rowIndex` 0-9
- **Priority levels**: 0 (none), 1 (low), 2 (medium), 3 (high)
- **Due dates** (v17+): Optional deadline tracking
- **Completion**: `completed` flag + `completedAt` timestamp

**DAO Methods**:
```kotlin
suspend fun getByDayDate(dayDate: String): List<Task>
suspend fun getTasksDueOn(dueDate: Long): List<Task>
suspend fun getOverdueTasks(now: Long): List<Task>
suspend fun setCompleted(id: String, completed: Boolean, completedAt: Long?)
suspend fun setPriority(id: String, priority: Int)
suspend fun setDueDate(id: String, dueDate: Long?)
```

**File**: `app/src/main/java/com/boox/day/data/db/Task.kt`

---

#### Attachment

**Purpose**: File attachments linked to tasks or pages.

**Schema**:
```kotlin
@Entity(
    indices = [Index("taskId"), Index("pageId")]
)
data class Attachment(
    @PrimaryKey val id: String = Nanoid.generate(),
    val taskId: String? = null,               // FK → Task (optional)
    val pageId: String? = null,               // FK → Page (optional)
    val filePath: String,                     // Local file path
    val fileName: String,
    val mimeType: String,
    val size: Long,                           // Bytes
    val createdAt: Date = Date()
)
```

**File**: `app/src/main/java/com/boox/day/data/db/Attachment.kt`

---

### Text Recognition Entities

#### TextAnnotation

**Purpose**: OCR/voice-transcribed text with rich formatting spans.

**Schema**:
```kotlin
@Entity(
    indices = [Index("pageId"), Index("sourceType")]
)
data class TextAnnotation(
    @PrimaryKey val id: String = Nanoid.generate(),
    val pageId: String,                       // FK → Page
    val content: String,                      // Plain text
    val spans: String? = null,                // JSON: List<RichTextSpan>
    val sourceType: SourceType,               // OCR, VOICE, MANUAL
    val x: Float,                             // Position on canvas
    val y: Float,
    val width: Float? = null,
    val height: Float? = null,
    val createdAt: Date = Date()
)

enum class SourceType {
    OCR,                 // Handwriting recognition
    VOICE,               // Voice-to-text
    MANUAL               // Typed text
}

// Stored as JSON in spans field:
sealed class RichTextSpan {
    data class BoldSpan(val start: Int, val end: Int)
    data class ItalicSpan(val start: Int, val end: Int)
    data class UnderlineSpan(val start: Int, val end: Int)
    data class ColorSpan(val start: Int, val end: Int, val color: Int)
    data class SizeSpan(val start: Int, val end: Int, val size: Float)
    data class HeaderSpan(val start: Int, val end: Int, val level: Int)
}
```

**Key Notes**:
- **Rich text**: Spans stored as JSON, applied during rendering
- **Positioning**: `(x, y)` on canvas, size optional
- **Source tracking**: Distinguish OCR vs Voice vs Manual input

**DAO**: `TextAnnotationDao` (`getByPageId`, `insert`, `update`, `delete`)

**File**: `app/src/main/java/com/boox/day/data/db/TextAnnotation.kt`

---

### Boox Notes Integration Entities

#### BooxNoteIndex

**Purpose**: Index of Boox Notes app notes (external app integration).

**Schema**:
```kotlin
@Entity(
    indices = [Index("uniqueId"), Index("matchedDate")]
)
data class BooxNoteIndex(
    @PrimaryKey val id: String = Nanoid.generate(),
    val uniqueId: String,                     // Boox Notes unique ID
    val title: String,
    val matchedDate: String? = null,          // "yyyy-MM-dd" (best-effort)
    val dateSource: String,                   // "title", "shape_file", "note_tree"
    val confidence: Float,                    // 0.0-1.0
    val lastIndexed: Long = System.currentTimeMillis()
)
```

**File**: `app/src/main/java/com/boox/day/data/db/BooxNoteIndex.kt`

---

#### BooxNoteLink

**Purpose**: Links between BooxDay pages and Boox Notes pages.

**Schema**:
```kotlin
@Entity(
    indices = [Index("dayPageId"), Index("booxNoteUniqueId")]
)
data class BooxNoteLink(
    @PrimaryKey val id: String = Nanoid.generate(),
    val dayPageId: String,                    // FK → DayPage
    val booxNoteUniqueId: String,             // FK → BooxNoteIndex
    val booxNotePageId: String,               // Page within Boox note
    val linkedAt: Long = System.currentTimeMillis()
)
```

**File**: `app/src/main/java/com/boox/day/data/db/BooxNoteLink.kt`

---

### AI/Cache Entities

#### SummaryCache

**Purpose**: Cached AI-generated summaries (daily, weekly, monthly).

**Schema**:
```kotlin
@Entity(
    indices = [Index(value = ["summaryType", "periodKey"], unique = true)]
)
data class SummaryCache(
    @PrimaryKey val id: String = Nanoid.generate(),
    val summaryType: String,                  // "daily", "weekly", "monthly"
    val periodKey: String,                    // "2025-01-15", "2025-W03", "2025-01"
    val content: String,                      // Markdown summary
    val generatedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null               // Invalidation timestamp
)
```

**File**: `app/src/main/java/com/boox/day/data/db/SummaryCache.kt`

---

### Utility Entities

#### Kv

**Purpose**: Key-value store for app settings and state.

**Schema**:
```kotlin
@Entity
data class Kv(
    @PrimaryKey val key: String,
    val value: String                         // JSON-serialized
)
```

**Usage**:
```kotlin
val kvProxy = KvProxy(kvDao)
kvProxy.set("APP_SETTINGS", GlobalAppSettings.current)
val settings = kvProxy.get<AppSettings>("APP_SETTINGS")
```

**File**: `app/src/main/java/com/boox/day/data/db/Kv.kt`

---

#### Folder, Notebook

**Purpose**: Legacy entities from Notable app (not used in Day mode).

**Status**: Dead code - entities exist but never queried in Day mode.

**Files**:
- `app/src/main/java/com/boox/day/data/db/Folder.kt`
- `app/src/main/java/com/boox/day/data/db/Notebook.kt`

---

## Key Relationships

```
DayPage (date)
    ├─→ Page (Memos)
    │   ├─→ Stroke[]
    │   ├─→ Image[]
    │   ├─→ TextAnnotation[]
    │   └─→ CalendarEvent (via linkedEventId)
    │
    ├─→ Page (Tasks)
    │   ├─→ Stroke[]
    │   └─→ Task[]
    │       └─→ Attachment[]
    │
    ├─→ LocalEvent[] (via dayId)
    │   └─→ ConnectedAccountEntity (via connectedAccountId)
    │       └─→ CalendarSelectionEntity[]
    │
    └─→ BooxNoteLink[]
        └─→ BooxNoteIndex

ConnectedAccountEntity
    ├─→ CalendarSelectionEntity[] (CASCADE delete)
    └─→ LocalEvent[]
```

---

## DAO Patterns

### Repository Pattern

**Do NOT use DAOs directly**. Always use repositories:

```kotlin
// ❌ WRONG
val page = appDatabase.pageDao().getById(pageId)

// ✅ CORRECT
val page = appRepository.pageRepository.getById(pageId)
```

**Why**: Repositories handle threading, caching, and business logic.

**Location**: `app/src/main/java/com/boox/day/data/repositories/`

---

### Common DAO Methods

| Pattern | Example | Purpose |
|---------|---------|---------|
| `getById(id)` | `pageDao.getById(pageId)` | Fetch single entity |
| `getAll()` | `taskDao.getAll()` | Fetch all (rarely used) |
| `getBy*` | `strokeDao.getByPageId(pageId)` | Fetch by foreign key |
| `insert(entity)` | `pageDao.insert(page)` | Create |
| `update(entity)` | `pageDao.update(page)` | Update |
| `delete(entity)` | `pageDao.delete(page)` | Delete |
| `deleteById(id)` | `pageDao.deleteById(id)` | Delete by ID |

**All DAOs are `suspend` functions** - use within coroutines:

```kotlin
suspend fun savePage(page: Page) = withContext(Dispatchers.IO) {
    pageDao.insert(page)
}
```

---

## Migration Strategy

### Adding a New Field

```kotlin
// 1. Update entity
@Entity
data class Task(
    val newField: String = "default"  // Provide default!
)

// 2. Create migration in Db.kt
private val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE Task ADD COLUMN newField TEXT NOT NULL DEFAULT 'default'")
    }
}

// 3. Update database version
@Database(..., version = 19)

// 4. Register migration
.addMigrations(MIGRATION_18_19)
```

### Adding a New Entity

```kotlin
// 1. Create entity file
@Entity
data class NewEntity(...)

// 2. Create DAO
@Dao
interface NewEntityDao { ... }

// 3. Add to AppDatabase
@Database(
    entities = [..., NewEntity::class],
    version = 19
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun newEntityDao(): NewEntityDao
}

// 4. Create migration
private val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE NewEntity (
                id TEXT PRIMARY KEY NOT NULL,
                field1 TEXT NOT NULL
            )
        """)
    }
}
```

**Testing Migrations**:
```bash
adb shell pm clear com.boox.day  # Reset to test migration from scratch
```

---

## Performance Notes

### Indexing

**All foreign keys should have indexes**:
```kotlin
@Entity(
    indices = [
        Index("pageId"),      // FK index
        Index("dayDate")      // Query optimization
    ]
)
```

**Composite indexes** for common queries:
```kotlin
Index(value = ["summaryType", "periodKey"], unique = true)
```

### Batch Operations

For bulk inserts, use `@Transaction`:

```kotlin
@Transaction
suspend fun insertAll(strokes: List<Stroke>) {
    strokes.forEach { insert(it) }
}
```

### Query Optimization

Use `Flow` for reactive queries (auto-updates):

```kotlin
@Query("SELECT * FROM Task WHERE dayDate = :date")
fun observeTasksForDay(date: String): Flow<List<Task>>
```

Use `suspend` for one-time queries:

```kotlin
@Query("SELECT * FROM Task WHERE dayDate = :date")
suspend fun getTasksForDay(date: String): List<Task>
```

---

## Storage Locations

| Data Type | Location | Notes |
|-----------|----------|-------|
| **Database** | `context.filesDir/booxdaydb/` | Internal storage |
| **Bitmaps** | `context.filesDir/pagedata/` | Cached page bitmaps |
| **Attachments** | `context.filesDir/attachments/` | Task attachments |
| **Boox Notes** | `/storage/emulated/0/.ksync/` | External (requires permission) |

---

## Debugging

### View Database

```bash
# Pull database to computer
adb pull /data/data/com.boox.day/files/booxdaydb/app_database ~/boox_day.db

# Open with SQLite browser
sqlite3 ~/boox_day.db
.tables
SELECT * FROM Task;
```

### Reset Database

```bash
adb shell pm clear com.boox.day
```

### Export Database

```kotlin
// In code (dev builds only)
val dbFile = context.getDatabasePath("app_database")
dbFile.copyTo(File(context.getExternalFilesDir(null), "backup.db"))
```

---

## Related Documentation

- **CLAUDE.md** - Quick entity reference
- **memory.md** - Section 2 (Data Layer)
- **docs/architecture.md** - How entities connect to UI
- **docs/EINKTHEME_CHEATSHEET.md** - UI patterns
- **.claude/docs/data-context.md** - Detailed data layer context

---

## Key Takeaways

1. **Current version: v25** - First public production baseline
2. **24 entities** - Core drawing + Calendar + Tasks + Sync + OCR + semantic + announcements
3. **SB1 encoding** - Efficient stroke storage (~70% smaller)
4. **Multi-provider** - ConnectedAccountEntity + CalendarSelectionEntity
5. **Soft deletes** - `isActive` flag for accounts, `PENDING_DELETE` for events
6. **Always use repositories** - Never call DAOs directly
7. **Future migrations required** - Every schema change after v25 needs migration code and tests
8. **Internal storage** - No external storage permission needed for core app

---

**Last Updated**: December 2025
**Maintainer**: Claude Code
**Questions**: Reference memory.md Section 2 or .claude/docs/data-context.md
