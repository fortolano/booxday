# Context-Aware OCR Semantic Interpretation Engine

> **Version**: 3.0 | **Last Updated**: January 2026 | **Package**: `com.boox.day.ocr`

## Executive Summary

The Context-Aware OCR Semantic Interpretation Engine is a sophisticated on-device text understanding system that processes handwritten content from multiple sources (Boox Notes, Memos, Tasks, Calendar Events) and extracts deep semantic meaning through a multi-stage pipeline:

1. **Google Cloud Vision API** - High-accuracy OCR with multi-language support
2. **12-Step Semantic Extraction** - Keywords, entities, action items, temporal signals
3. **13-Step Domain+Intent Classification** - Two-tier classification with context-aware disambiguation
4. **Dictionary System** - 22 domains, 65 intents (English; architecture supports multi-language)
5. **SemanticGeniusService** - READ-ONLY intelligence layer for profile retrieval, affinity scoring, search, and personalization

**Key Design Principles:**
- **Cloud Vision API** - NOT ML Kit, provides superior accuracy for handwritten text
- **Context-aware disambiguation** - Uses ±5 token window + collocations to resolve ambiguous terms
- **Full explainability** - Every classification decision is traceable to weighted signals
- **Extensible language support** - Dictionary system designed for multi-language (English implemented)
- **E-ink optimized** - Runs only during idle periods, aborts instantly on user interaction

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [OCR Engine: Google Cloud Vision](#ocr-engine-google-cloud-vision)
3. [Processing Pipeline](#processing-pipeline)
4. [Semantic Extraction (12 Steps)](#semantic-extraction-12-steps)
5. [Domain & Intent Classification (13 Steps)](#domain--intent-classification-13-steps)
6. [SemanticGeniusService](#semanticgeniusservice)
7. [Event Semantic Indexing](#event-semantic-indexing)
8. [Multi-Language Dictionary System](#multi-language-dictionary-system)
9. [Context-Aware Disambiguation](#context-aware-disambiguation)
10. [Dominant Evidence Rules](#dominant-evidence-rules)
11. [Database Schema](#database-schema)
12. [Scheduling & Idle Processing](#scheduling--idle-processing)
13. [Integration Points](#integration-points)
14. [Usage Opportunities](#usage-opportunities)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              CONTENT SOURCES                                     │
├──────────────────┬──────────────────┬───────────────────────────────────────────┤
│   Boox Notes     │     Memos        │        Tasks                              │
│   (JNI render)   │  (Bitmap load)   │     (Bitmap load)                         │
└────────┬─────────┴────────┬─────────┴────────┬──────────────────────────────────┘
         └──────────────────┴──────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         UnifiedOcrPipeline                                       │
│   Step 1: Quota check (backfill: 100, monthly: 150)                             │
│   Step 2: Mark in-progress (crash recovery)                                     │
│   Step 3: Render bitmap from source                                             │
│   Step 4: Preprocess (downscale to 1400px, grayscale)                          │
│   Step 5: Google Cloud Vision OCR                                               │
│   Step 6: 12-step Semantic Extraction                                           │
│   Step 7: Store in UnifiedOcrRepository                                         │
└───────────────────────────────┬─────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                      CloudVisionService                                          │
│   - Google Cloud Vision API (NOT ML Kit!)                                       │
│   - TEXT_DETECTION + DOCUMENT_TEXT_DETECTION                                    │
│   - API key from EntitlementManager.getCloudVisionKey()                         │
│   - Retry with exponential backoff                                              │
└───────────────────────────────┬─────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                      SemanticExtractor (12 Steps)                                │
│   1. TextNormalizer       → NFKC, whitespace, OCR fixes (0→O, 1→l)             │
│   2. DocumentClassifier   → MEETING_NOTES, CHECKLIST, JOURNAL, etc.            │
│   3-4. KeywordExtractor   → TF-IDF with position boosts                        │
│   5. PhraseExtractor      → Bigrams/trigrams                                   │
│   6. SentenceExtractor    → High-signal sentences                              │
│   7. EntityExtractor      → DATE, TIME, EMAIL, PHONE, MONEY, PERSON, ORG...    │
│   8. ActionItemExtractor  → Verb+object patterns                               │
│   9. TemporalExtractor    → Dates, times, relative ("tomorrow", "next week")   │
│   10. TopicClassifier     → Multi-label topics                                 │
│   11. SynopsisGenerator   → Structured summary (max 900 chars)                 │
│   12. DomainIntentClassifier → 13-step classification (see below)              │
└───────────────────────────────┬─────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                  DomainIntentClassifier (13 Steps)                               │
│   1. LanguageDetector      → Detect language, select mode (A/B/C)              │
│   2. OcrCorrector          → Fix common OCR errors                              │
│   3. StructureSignals      → Checkboxes, bullets, code blocks                  │
│   4. DominantEvidence      → Strong single-domain patterns                     │
│   5. TokenScoring          → Dictionary-based with negative signals            │
│   6. ContextDisambiguator  → ±5 token window + collocations                    │
│   7. ScoreCombination      → Merge all signal sources                          │
│   8. UserPriors            → Personalization based on history                  │
│   9. DomainSelection       → Pick winner (min 25% confidence)                  │
│   10. IntentClassification → Multi-label (max 4 intents)                       │
│   11. SubdomainHint        → Optional refinement (e.g., LDS subdomain)         │
│   12. BuildExplanation     → Full traceable output                             │
│   13. RecordPriors         → Update user history                               │
└───────────────────────────────┬─────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        DATABASE (Room)                                           │
├──────────────────────────────────────────────────────────────────────────────────┤
│  unified_ocr_page      │ Status tracking, crash recovery                        │
│  unified_ocr_text      │ OCR text + semantic data (JSON columns)                │
│  unified_ocr_text_fts  │ FTS4 full-text search index                            │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## OCR Engine: Google Cloud Vision

**File:** `ocr/CloudVisionService.kt`

The engine uses **Google Cloud Vision API exclusively** - NOT ML Kit or any LLM fallback.

### Key Characteristics

| Property | Value |
|----------|-------|
| API Endpoint | `https://vision.googleapis.com/v1/images:annotate` |
| Features | `TEXT_DETECTION` + `DOCUMENT_TEXT_DETECTION` |
| Language Hints | `en`, `pt` (expandable) |
| Timeout | 30 seconds |
| JPEG Quality | 85% |
| Max Retries | 3 (exponential backoff) |

### API Key Delivery

```kotlin
// Key comes from server via EntitlementManager
val apiKey = EntitlementManager.getInstance().getCloudVisionKey()
```

**Important:** The API key is static (unlike OpenRouter which rotates). Always check `FeatureGate.canAccess(Feature.OCR_INDEXING)` first.

### Error Handling

| HTTP Code | Exception | Action |
|-----------|-----------|--------|
| 403 | `InvalidKey` | Don't retry, key is invalid |
| 429 | `RateLimited` | Respect `Retry-After` header |
| Network | `NetworkError` | Retry with exponential backoff |

### Response Parsing

```kotlin
data class CloudVisionResult(
    val text: String,        // Full extracted text
    val language: String?,   // Detected language code
    val confidence: Float,   // Average block confidence
    val blocks: Int          // Number of text blocks
)
```

---

## Processing Pipeline

**File:** `ocr/UnifiedOcrPipeline.kt`

### 7-Step Pipeline

```kotlin
suspend fun processPage(
    source: OcrBitmapSource,
    sourceId: String,
    pageId: String,
    sourceModified: Long,
    sourceDate: String? = null,
    sourceTitle: String? = null
): Boolean
```

| Step | Operation | Details |
|------|-----------|---------|
| 1 | Quota Check | `OcrQuotaManager.canIndexNewPage()` - backfill/monthly budget |
| 2 | Mark In-Progress | Crash recovery marker (UPSERT) |
| 3 | Render Bitmap | From Boox Notes (JNI) or Memo/Task (bitmap load) |
| 4 | Preprocess | Downscale to 1400px width, convert to grayscale |
| 5 | Cloud Vision OCR | `cloudVisionService.recognizeText(bitmap)` |
| 6 | Semantic Extraction | `SemanticExtractor.extract(text, title, timestamp)` |
| 7 | Store Results | `ocrRepository.storeOcrResult(...)` |

### Bitmap Preprocessing

```kotlin
private fun preprocessBitmap(bitmap: Bitmap): Bitmap {
    // Step 1: Downscale if > 1400px width
    if (bitmap.width > TARGET_WIDTH) {
        val scale = TARGET_WIDTH.toFloat() / bitmap.width
        result = Bitmap.createScaledBitmap(...)
    }

    // Step 2: Convert to grayscale for better OCR
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }
    canvas.drawBitmap(result, 0f, 0f, paint)

    return grayscale
}
```

---

## Semantic Extraction (12 Steps)

**File:** `ocr/semantic/SemanticExtractor.kt`

### Pipeline Overview

```kotlin
fun extract(
    text: String,
    sourceTitle: String? = null,
    sourceCreatedAt: Long? = null
): SemanticBundle
```

| Step | Component | Output |
|------|-----------|--------|
| 1 | `TextNormalizer` | Cleaned text, paragraphs, sentences |
| 2 | `DocumentClassifier` | `DocumentStyle` (MEETING_NOTES, CHECKLIST, etc.) |
| 3-4 | `KeywordExtractor` | 12-25 TF-IDF keywords with position boosts |
| 5 | `PhraseExtractor` | 5-10 meaningful bigrams/trigrams |
| 6 | `SentenceExtractor` | Max 3 high-signal sentences |
| 7 | `EntityExtractor` | Named entities (DATE, TIME, EMAIL, etc.) |
| 8 | `ActionItemExtractor` | Verb+object patterns with due dates |
| 9 | `TemporalExtractor` | Dates, times, relative dates, weekdays |
| 10 | `TopicClassifier` | Multi-label topic classification |
| 11 | `SynopsisGenerator` | Structured summary (max 900 chars) |
| 12 | `DomainIntentClassifier` | Domain + Intent classification (v2) |

### Step 1: TextNormalizer

**File:** `ocr/semantic/TextNormalizer.kt`

```kotlin
data class NormalizationResult(
    val cleanText: String,
    val normalizedText: String,
    val paragraphs: List<String>,
    val sentences: List<String>,
    val originalLength: Int,
    val wasTruncated: Boolean
)
```

Operations:
1. **NFKC normalization** - Unicode compatibility
2. **Whitespace collapse** - Multiple spaces → single space
3. **OCR garbage removal** - Repeated dashes, underscores, pipes
4. **OCR confusion fixes** - `0→O` in words, `O→0` in numbers, `1→l` in words
5. **Line deduplication** - Remove lines appearing 3+ times
6. **Truncation** - Max 25,000 chars (keep first 15K + last 5K)

### Step 2: DocumentClassifier

**File:** `ocr/semantic/DocumentClassifier.kt`

Detects document style using structural heuristics:

| Style | Detection Criteria |
|-------|-------------------|
| `CHECKLIST` | Checkbox count ≥3, or bullet density >0.5 with checkbox |
| `MEETING_NOTES` | Meeting keywords ≥3 AND timestamps ≥2 |
| `PROJECT_PLAN` | Project keywords ≥3 AND bullet density >0.3 |
| `FORM_LIKE` | Form fields (label: value) ≥5 |
| `FREEFORM_JOURNAL` | Low bullets, low structure, low timestamps |
| `MIXED` | Default fallback |

### Step 7: EntityExtractor

**File:** `ocr/semantic/EntityExtractor.kt`

Regex-based Named Entity Recognition:

| Entity Type | Example Patterns |
|-------------|-----------------|
| `DATE` | `2024-01-15`, `Jan 15`, `tomorrow`, `next week` |
| `TIME` | `14:30`, `10am`, `noon` |
| `EMAIL` | Standard email pattern |
| `PHONE` | US, international, Brazilian formats |
| `URL` | `https://...` |
| `MONEY` | `$100`, `€50`, `R$100`, `USD 50` |
| `PERCENT` | `50%`, `25.5 percent` |
| `PERSON` | Capitalized multi-word names |
| `ORG` | Names with Inc, LLC, Corp, etc. |
| `LOCATION` | After prepositions (in, at, from, to) |

---

## Domain & Intent Classification (13 Steps)

**File:** `ocr/semantic/classification/DomainIntentClassifier.kt`

### Two-Tier Classification System

**Primary (Domain):** What area of life is this note about? → Exactly ONE
**Secondary (Intent):** What is the PURPOSE of this note? → 0-4 intents

### 13-Step Pipeline

```kotlin
fun classify(text: String): ClassificationExplanation {
    // Step 1: Language Detection → Mode Selection
    val langResult = LanguageDetector.detect(text)
    val mode = ModeSelector.selectMode(langResult, hasUserDictionaries)

    // Step 2: OCR Correction
    val correctedText = OcrCorrector.applyDirectSubstitutions(text)
    val tokens = tokenize(correctedText)

    // Step 3: Structure Signal Detection
    val structureSignals = detectStructureSignals(correctedText)

    // Step 4: Dominant Evidence Check
    val dominantRules = DominantEvidenceRules.detect(correctedText)
    val dominantDomain = DominantEvidenceRules.getDominantDomain(dominantRules)

    // Step 5: Token Scoring with Negative Signals
    val tokenScoringResult = scoreTokens(tokens, dominantDomain, mode)

    // Step 6: Ambiguous Term Resolution
    val disambiguationResult = resolveAmbiguousTerms(tokens, tokenScoringResult.domainScores)

    // Step 7: Combine All Scores
    val combinedScores = combineScores(...)

    // Step 8: Apply Per-User Priors
    val finalScores = userPriors.applyPriors(combinedScores, priorResult)

    // Step 9: Select Final Domain
    val (winningDomain, domainConfidence) = selectDomain(finalScores, dominantRules)

    // Step 10: Classify Intents
    val intents = classifyIntents(tokens, structureSignals, winningDomain)

    // Step 11: Detect Subdomain Hint
    val subdomainHint = detectSubdomainHint(tokens, winningDomain)

    // Step 12: Build Explanation
    val explanation = ClassificationExplanation(...)

    // Step 13: Record for Priors
    userPriors.recordClassification(winningDomain, intents)

    return explanation
}
```

### Classification Modes

| Mode | Trigger | Pipeline |
|------|---------|----------|
| **MODE_A_FULL** | Supported language (en) | Structure + patterns + dictionaries + disambiguation |
| **MODE_B_STRUCTURE** | Unknown/unsupported language | Structure + digits + punctuation only |
| **MODE_C_EXTENDED** | User has custom dictionaries | Mode A/B + user dictionaries |

### Domain Taxonomy (22 Domains)

| Category | Domains |
|----------|---------|
| **Professional** | `WORK`, `MANAGEMENT`, `LEADERSHIP`, `BUSINESS`, `STRATEGY` |
| **Education** | `SCHOOL`, `TEACHING`, `STUDENT` |
| **Technical** | `TECH` |
| **Financial** | `FINANCE` |
| **Personal Life** | `HEALTH`, `FAMILY`, `SOCIAL`, `PERSONAL`, `HOME`, `SHOPPING` |
| **Spiritual** | `SPIRITUAL`, `LDS_LEADERSHIP` |
| **Creative** | `CREATIVE`, `SOCIAL_MEDIA` |
| **Other** | `TRAVEL`, `LEGAL`, `ENTREPRENEURSHIP`, `MISC` |

### Intent Taxonomy (65 Intents)

Organized by category:

| Category | Intents |
|----------|---------|
| **Planning** | `TODO_LIST`, `PLANNING`, `GOAL_SETTING` |
| **Recording** | `MEETING_NOTES`, `CALL_LOG`, `BRAINSTORM`, `JOURNAL`, `LECTURE_NOTES` |
| **Teaching** | `LESSON_PLAN`, `GRADING`, `CLASSROOM_MGMT`, `CURRICULUM_DEV`, `PARENT_COMM`, `IEP_504`, `FACULTY_MEETING`, `STUDENT_RECORD` |
| **Student** | `STUDY_PLAN`, `ASSIGNMENT_WORK`, `READING_NOTES`, `LAB_NOTES`, `THESIS_WORK`, `COURSE_PLANNING` |
| **Reference** | `REFERENCE`, `INSTRUCTIONS`, `CONTACT_INFO` |
| **Financial** | `EXPENSE_LOG`, `BUDGET` |
| **Leadership** | `LEADERSHIP_NOTES`, `PEOPLE_MANAGEMENT`, `STRATEGY_PLANNING`, `METRICS_TRACKING` |
| **Entrepreneurship** | `STARTUP_IDEAS`, `FUNDRAISING`, `PRODUCT_DEV` |
| **Social Media** | `CONTENT_PLAN`, `SOCIAL_ANALYTICS`, `CAMPAIGN_NOTES` |
| **LDS Church** | `LDS_INTERVIEW`, `LDS_COUNCIL`, `LDS_MINISTERING`, `LDS_CALLING`, `LDS_MEMBER_CARE`, `LDS_YOUTH_ACTIVITY`, `LDS_LESSON_PREP`, `LDS_FINANCIAL` |
| **Productivity** | `DAILY_REVIEW`, `FOCUS_SESSION` |
| **Academic** | `ADVISING` |

---

## SemanticGeniusService

**File:** `ocr/semantic/SemanticGeniusService.kt` (~2,000 lines)

### Purpose

SemanticGeniusService is a **READ-ONLY semantic intelligence provider** serving multiple UI features:

| Consumer | Use Case |
|----------|----------|
| **BooxNoteIndexer** | Smart Link - match notes to calendar events |
| **SuggestionContextBuilder** | AI context loading for suggestions |
| **SmartSuggestionsDialog** | Task recommendations |
| **OcrSearchComponents** | Enhanced semantic search |

**Core capabilities:**
- **Profile retrieval** - Get semantic profiles for any content (Boox Notes, Memos, Tasks, Events)
- **Semantic affinity** - Calculate 9-component similarity scores between any two content items
- **Context aggregation** - Aggregate semantic data by date or date-range
- **Semantic search** - Full-text search with domain/source filters
- **Domain analytics** - Distribution of domains across content
- **Action items** - Extract pending action items with sources
- **Event indexing** - Index calendar/local events for semantic matching
- **Co-occurrence tracking** - Learn entity relationships (e.g., "John" appears with "Project X")
- **User pattern learning** - Personalization based on linking behavior

**IMPORTANT:** Does NOT re-run classification. All data comes from `unified_ocr_text`.

### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         SemanticGeniusService                                    │
│                         (READ-ONLY Provider)                                     │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                 │
│  │  profileCache   │  │ dateContextCache│  │eventProfileCache│                 │
│  │   (32 entries)  │  │  (14 entries)   │  │  (200 entries)  │                 │
│  │   UNUSED *      │  │   UNUSED *      │  │  ACTIVELY USED  │                 │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘                 │
│           │                    │                    │                           │
│           ▼                    ▼                    ▼                           │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                      UnifiedOcrRepository                                │   │
│  │                      (Database Access)                                   │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│  * profileCache/dateContextCache have no consumers yet - prepared for future    │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Cache Architecture

| Cache | Size | Status | Used By | Notes |
|-------|------|--------|---------|-------|
| `profileCache` | 32 | **UNUSED** | `getProfile()` | No callers - prepared for future |
| `dateContextCache` | 14 | **UNUSED** | `getDateContext()` | No callers - prepared for future |
| `eventProfileCache` | 200 | **ACTIVE** | `getEventProfile()`, `getOrCreateEventProfile()` | Used by BooxNoteIndexer |

**Important:** BooxNoteIndexer uses `getProfilesForSource()` which **bypasses the cache** and queries the database directly. This is intentional - batch loading is more efficient.

### Key Methods

#### Profile Retrieval

```kotlin
// Single-page profile (UNUSED - no callers)
suspend fun getProfile(sourceType, sourceId, pageId): SemanticProfile?

// Multi-page profiles (USED by BooxNoteIndexer)
suspend fun getProfilesForSource(sourceType, sourceId): List<SemanticProfile>

// Batch loading (USED by BooxNoteIndexer for Smart Link)
suspend fun getAllProfilesForSourceType(sourceType): Map<String, List<SemanticProfile>>
```

#### Semantic Affinity (9-Component Scoring)

```kotlin
fun calculateSemanticAffinity(profileA: SemanticProfile, profileB: SemanticProfile): Float
```

Returns 0.0-1.0 score combining:

| Component | Weight | Description |
|-----------|--------|-------------|
| Domain Match | 0.20 | Exact domain or sibling domains |
| Intent Compatibility | 0.12 | Shared intents with confidence |
| Keyword Overlap | 0.18 | Jaccard similarity of keywords |
| Key Phrases | 0.08 | Phrase presence in event |
| Person Entities | 0.12 | Person name matching |
| Org Entities | 0.08 | Organization matching |
| Location Entities | 0.07 | Location matching |
| Action Items | 0.10 | Verb/object matching |
| Temporal Signals | 0.05 | Weekday/date matching |

#### Event Indexing

```kotlin
// Index a calendar event (creates semantic profile)
suspend fun indexEvent(event: CalendarEvent): SemanticProfile
suspend fun indexEvent(event: LocalEvent): SemanticProfile

// Get or create event profile (cache-aware)
suspend fun getOrCreateEventProfile(event: CalendarEvent): SemanticProfile
suspend fun getOrCreateEventProfile(event: LocalEvent): SemanticProfile

// Event profile cache management
fun invalidateEventProfile(eventId: String)
fun clearEventProfileCache()
```

#### Additional Features (Prepared for Future)

```kotlin
// Date context aggregation (NO CALLERS YET)
suspend fun getDateContext(date: String): DateSemanticContext

// Semantic search with filters (NO CALLERS YET)
suspend fun searchWithFilters(query, domains?, sourceTypes?): List<SemanticSearchResult>

// Domain distribution analytics (NO CALLERS YET)
suspend fun getDomainDistribution(startDate?, endDate?): DomainDistribution

// Action item extraction (NO CALLERS YET)
suspend fun getPendingActionItems(limit?): List<ActionItemWithSource>
```

### Integration Points

| Consumer | Method Used | Purpose |
|----------|-------------|---------|
| **BooxNoteIndexer** | `getProfilesForSource()`, `getOrCreateEventProfile()`, `calculateSemanticAffinity()` | Smart Link matching |
| **EventChangeObserver** | `indexEvent()`, `invalidateEventProfile()`, `deleteEventProfile()` | Calendar sync |
| **LocalEventRepository** | `indexEvent()`, `invalidateEventProfile()`, `deleteEventProfile()` | Local event CRUD |
| **EventSemanticIndexingWorker** | `getOrCreateEventProfile()` | Background indexing |

---

## Event Semantic Indexing

### Overview

Calendar events (Google, Outlook) and local events are indexed semantically to enable intelligent matching with handwritten notes.

### Event Sources

| Source Type | Origin | Indexed By |
|-------------|--------|------------|
| `CALENDAR_EVENT` | Google/Outlook sync | `EventChangeObserver` |
| `LOCAL_EVENT` | In-app creation | `LocalEventRepository` |

### Indexing Flow

```
Event Created/Updated
        │
        ▼
┌───────────────────────────────────────────────────────────────┐
│  EventChangeObserver.processChanges()                         │
│  (After calendar sync completes)                              │
└───────────────────────┬───────────────────────────────────────┘
                        │
                        ▼
┌───────────────────────────────────────────────────────────────┐
│  EventSemanticIndexingWorker.enqueue()                        │
│  (Background WorkManager job)                                 │
└───────────────────────┬───────────────────────────────────────┘
                        │
                        ▼
┌───────────────────────────────────────────────────────────────┐
│  SemanticGeniusService.indexEvent()                           │
│  1. Check for duplicates (same title, ±5min window)           │
│  2. Normalize text (EventTextNormalizer)                      │
│  3. Classify (DomainIntentClassifier, recordPriors=false)     │
│  4. Persist to unified_ocr_text                               │
│  5. Record entity co-occurrences                              │
│  6. Cache in eventProfileCache                                │
└───────────────────────────────────────────────────────────────┘
```

### Event Deduplication

Events with the same title within a 5-minute window are considered duplicates:

```kotlin
suspend fun findDuplicateEventProfile(event: IndexableEvent): SemanticProfile? {
    // Search for existing profile with same title ±5 minutes
    val windowMs = 5 * 60 * 1000L
    // Returns existing profile if found, null otherwise
}
```

### Event Profile Schema

Events are stored in `unified_ocr_text` with special handling:

| Field | Value |
|-------|-------|
| `sourceType` | `CALENDAR_EVENT` or `LOCAL_EVENT` |
| `sourceId` | Event ID |
| `pageId` | `"__EVENT_PROFILE__"` (sentinel value) |
| `text` | `title + "\n" + description` |
| `domain` | Classified domain |
| `intentsJson` | Classified intents |

### Dictionary Version Tracking

When dictionaries are updated, events are automatically reindexed:

```kotlin
// Called on app startup
semanticGeniusService.checkAndReindexIfVersionChanged()
```

---

## Dictionary System

**File:** `ocr/semantic/classification/DictionaryLoader.kt`

### Directory Structure

```
assets/classification/
├── en/                          # English (currently implemented)
│   ├── config.json              # Language metadata
│   ├── domains/                 # 22 domain dictionaries
│   │   ├── work.json
│   │   ├── tech.json
│   │   ├── teaching.json
│   │   └── ...
│   └── intents/                 # 56 intent dictionaries
│       ├── todo_list.json
│       ├── meeting_notes.json
│       └── ...
└── shared/                      # Language-independent
    └── structure_patterns.json
```

**Note:** The architecture supports multiple languages. Additional languages can be added by creating new directories following the same structure.

### Domain Dictionary Structure

```json
{
  "domain": "WORK",
  "displayName": "Work",
  "description": "Professional workplace, office, career-related",
  "tierA": {
    "weight": 4.0,
    "terms": ["meeting", "weekly report", "performance review", "schedule", "colleague"]
  },
  "tierB": {
    "weight": 2.5,
    "terms": ["department", "company", "task", "goal", "training", "salary"]
  },
  "tierC": {
    "weight": 1.0,
    "terms": ["work", "office", "email", "deadline"]
  },
  "phrasePatterns": [
    {"pattern": "work.*plan", "weight": 4.0},
    {"pattern": "meeting.*notes", "weight": 4.0}
  ]
}
```

### Three-Tier Token Weighting

| Tier | Weight | Description |
|------|--------|-------------|
| **A** | 4.0 | High precision - very domain-specific terms |
| **B** | 2.5 | Strong signals - commonly associated terms |
| **C** | 1.0 | Weak signals - general terms |

---

## Context-Aware Disambiguation

**File:** `ocr/semantic/classification/ContextDisambiguator.kt`

### Algorithm

```kotlin
fun resolve(
    term: String,
    tokens: List<String>,
    termIndex: Int,
    priorScores: Map<Domain, Float>
): DisambiguationResult? {
    // 1. Get local window (±5 tokens)
    val localTokens = tokens.subList(termIndex - 5, termIndex + 6)

    // 2. Score each possible domain
    for ((domain, collocations) in termCollocations) {
        val collocationHits = collocations.count { it in localTokens }
        val priorScore = priorScores[domain] ?: 0f

        // Combined: 60% collocation weight, 40% prior weight
        combinedScore = (collocationHits * 0.6f) + (priorScore * 0.4f)
    }

    // 3. Return winner with confidence
    return DisambiguationResult(term, winningDomain, confidence, reason)
}
```

### Disambiguation Collocations

```kotlin
val AMBIGUOUS_TERMS = mapOf(
    "service" to mapOf(
        Domain.TECH to listOf("api", "endpoint", "deploy", "server", "microservice"),
        Domain.WORK to listOf("customer", "support", "ticket", "client", "desk"),
        Domain.SPIRITUAL to listOf("worship", "church", "prayer", "congregation")
    ),
    "class" to mapOf(
        Domain.TECH to listOf("object", "method", "public", "extends", "interface"),
        Domain.TEACHING to listOf("lesson", "rubric", "grading", "attendance"),
        Domain.STUDENT to listOf("homework", "study", "exam", "professor"),
        Domain.HEALTH to listOf("yoga", "fitness", "gym", "workout")
    ),
    "project" to mapOf(
        Domain.TECH to listOf("repo", "git", "branch", "deploy", "sprint", "code"),
        Domain.WORK to listOf("deadline", "budget", "stakeholder", "milestone"),
        Domain.SCHOOL to listOf("assignment", "group", "presentation", "professor")
    ),
    // ... 40+ ambiguous terms covering TECH, WORK, SPIRITUAL,
    //     TEACHING, STUDENT, LDS_LEADERSHIP, and more
)
```

---

## Dominant Evidence Rules

**File:** `ocr/semantic/classification/DominantEvidenceRules.kt`

### Purpose

Certain patterns are so strong they should nearly guarantee a domain. When fired, dominant evidence:
1. **Boosts** the target domain (+10 points)
2. **Sets a confidence floor** (e.g., 85% minimum)
3. **Suppresses** competing domains' signals

### Example Rules

| Rule ID | Domain | Confidence Floor | Pattern |
|---------|--------|------------------|---------|
| `TECH_CODEBLOCK` | TECH | 85% | Triple backticks or 4-space indent with code |
| `TECH_STACKTRACE` | TECH | 90% | Exception/Error patterns |
| `SPIRITUAL_SCRIPTURE` | SPIRITUAL | 85% | Bible/Quran/scripture references |
| `TEACHING_LESSON_PLAN` | TEACHING | 90% | "lesson plan", "SWBAT", "exit ticket" |
| `TEACHING_IEP_504` | TEACHING | 92% | IEP, 504 plan, accommodations |
| `LDS_LEADERSHIP_CALLING` | LDS_LEADERSHIP | 90% | Bishop, stake president, elders quorum |
| `STUDENT_THESIS` | STUDENT | 88% | Thesis chapter, dissertation, literature review |
| `ENTREPRENEURSHIP_STARTUP` | ENTREPRENEURSHIP | 80% | Seed round, VC, pitch deck, term sheet |

### Detection Patterns

```kotlin
// Code blocks
val CODE_BLOCK_PATTERN = Regex("""```[\s\S]*?```|^( {4,}|\t+)[a-zA-Z_][a-zA-Z0-9_]*\s*[=(\[{]""")

// LDS Leadership positions
val LDS_CALLING_PATTERN = Regex(
    """(?:bishop(?:ric)?|stake\s+president|elders?\s+quorum(?:\s+president)?|relief\s+society(?:\s+president)?|young\s+(?:men|women)(?:\s+president)?|primary\s+president)""",
    RegexOption.IGNORE_CASE
)

// Teacher-specific patterns
val LESSON_PLAN_PATTERN = Regex(
    """(?:lesson\s+plan|learning\s+objective|swbat|students\s+will\s+be\s+able|bell\s+ringer|exit\s+ticket|anticipatory\s+set|guided\s+practice)""",
    RegexOption.IGNORE_CASE
)
```

---

## Database Schema

**File:** `data/db/UnifiedOcr.kt`

### UnifiedOcrPage (Status Tracking)

```kotlin
@Entity(tableName = "unified_ocr_page")
data class UnifiedOcrPage(
    @PrimaryKey val id: String,           // "{sourceType}:{sourceId}:{pageId}"
    val sourceType: String,               // BOOX_NOTE, MEMO, TASK
    val sourceId: String,
    val pageId: String,
    val status: String,                   // PENDING, IN_PROGRESS, SUCCESS, FAILED, EMPTY, LOW_SIGNAL
    val sourceCreatedAt: Long,
    val sourceModifiedAt: Long,
    val ocrAt: Long,                      // Last OCR timestamp
    val textHash: String?,                // Change detection
    val semanticHash: String?,
    val failCount: Int,
    val lastError: String?
)
```

### UnifiedOcrText (Content + Semantics)

```kotlin
@Entity(tableName = "unified_ocr_text")
data class UnifiedOcrText(
    @PrimaryKey val id: String,

    // Core OCR text
    val text: String,
    val textNormalized: String,           // For FTS search
    val cleanText: String?,

    // Metadata
    val language: String?,
    val wordCount: Int,
    val confidence: Float,

    // Semantic data (JSON columns)
    val docStyle: String?,                // DocumentStyle enum
    val keywordsJson: String?,            // List<KeywordEntry>
    val keyPhrasesJson: String?,          // List<String>
    val keySentencesJson: String?,        // List<String>
    val entitiesJson: String?,            // List<ExtractedEntity>
    val actionItemsJson: String?,         // List<ActionItem>
    val temporalSignalsJson: String?,     // TemporalSignals
    val topicsJson: String?,              // List<TopicEntry>
    val synopsis: String?,

    // v2 Classification
    val domain: String?,                  // Domain enum name
    val domainConfidence: Float?,
    val intentsJson: String?,             // List<IntentEntry>
    val subdomainHint: String?,
    val classificationExplainJson: String?, // Full explanation

    // Source metadata
    val sourceTitle: String?,
    val sourcePath: String?,
    val sourceDate: String?,

    // Version
    val semanticVersion: Int,
    val updatedAt: Long
)
```

### FTS4 Search Index

```sql
CREATE VIRTUAL TABLE unified_ocr_text_fts USING fts4(
    content="unified_ocr_text",
    text,
    textNormalized,
    synopsis,
    sourceTitle
);
```

---

## Scheduling & Idle Processing

### OcrQuotaManager

**File:** `ocr/OcrQuotaManager.kt`

Quota Model:
- **Backfill Phase**: 100 pages for historical Boox Notes (one-time)
- **Monthly Budget**: 150 pages/month for ongoing content (memos, tasks, new notes)
- **Re-indexing**: FREE - modified existing pages don't consume budget

```kotlin
class OcrQuotaManager(context: Context) {
    companion object {
        const val BACKFILL_LIMIT = 100     // One-time historical pages
        const val MONTHLY_BUDGET = 150     // Pages per month
    }

    fun isBackfillPhase(): Boolean
    fun canIndexNewPage(sourceType: OcrSource): Boolean
    fun recordNewPageIndexed(sourceType: OcrSource)
    fun recordReIndexed()  // Free - no budget consumed
    fun getRemainingBudget(): Int
    fun getRemainingBackfill(): Int
    fun getRemainingMonthlyBudget(): Int
}
```

**Budget Enforcement**:
- Memos and Tasks always use monthly budget (even during backfill)
- Boox Notes use backfill budget during backfill phase, monthly budget after
- Re-indexed pages (modified content) are always FREE

### UnifiedOcrIdleProcessor

**File:** `ocr/UnifiedOcrIdleProcessor.kt`

Runs OCR only during idle periods with these safety guards:

| Guard | Value | Purpose |
|-------|-------|---------|
| Idle threshold | 5 seconds | Wait for user inactivity |
| Max pages/session | 2 | Prevent long blocking |
| Cooldown | 7 seconds | Between pages |
| Min free memory | 100MB | Prevent OOM |
| Min battery | 15% | Unless charging |
| `@Volatile shouldAbort` | Cross-thread | Instant abort capability |

---

## Integration Points

### 1. Full-Text Search

```kotlin
val repository = UnifiedOcrRepository(context)
val results = repository.searchText("meeting budget Q4", limit = 50)
```

### 2. Calendar Event Matching (Opportunity!)

Current matching only uses `event.title`. The engine provides:

```kotlin
// Get semantic data for a memo page
val ocrText = repository.getOcrText(OcrSource.MEMO, date, pageId)

// Rich matching opportunities:
val keywords = ocrText?.keywords?.map { it.term }    // Match keywords
val entities = ocrText?.entities                      // Match dates, people, orgs
val domain = ocrText?.domain                          // Relevance scoring
val synopsis = ocrText?.synopsis                      // Context summary
```

**Enhancement opportunity:** Match keywords and entities against `event.description` (currently unused in BooxNoteIndexer).

### 3. Classification Debugging

```kotlin
val classifier = DomainIntentClassifier(context)
val explanation = classifier.classify(text)

// Human-readable summary
println(explanation.toDebugSummary())
// === Classification: WORK (85%) ===
// Language: en | Mode: A
// DOMINANT: TECH_CODEBLOCK
// Intents: TODO_LIST(72%), PLANNING(45%)
// Structure: CHECKBOX_UNCHECKED(3), BULLET_LIST(5)
// Tokens: A=3 B=8 C=12
// Disambiguated: service->TECH(collocation:api,endpoint)
// Time: 45ms
```

---

## Usage Opportunities

### Current Applications

1. **OCR Search Dialog** - Full-text search across all handwritten content
2. **Boox Notes Smart Link** - Match notes to calendar events

### Future Applications

1. **Enhanced Event Matching**
   - Match keywords against `event.description` (currently unused!)
   - Use domain classification for relevance scoring
   - Extract attendee names via PERSON entities

2. **Smart Note Organization**
   - Auto-categorize by domain
   - Surface action items with due dates
   - Group notes by topic/intent

3. **Intelligent Reminders**
   - Extract temporal signals: "Call John tomorrow"
   - Auto-create reminders from action items

4. **Cross-Note Intelligence**
   - "Show me all meeting notes about Project X"
   - "Find notes with expenses over $1000"
   - "What action items do I have for this week?"

5. **Voice/AI Integration**
   - Use synopsis for voice readback
   - Use classification for context-aware AI responses

---

## Key Files Reference

| Component | File |
|-----------|------|
| **OCR Service** | `ocr/CloudVisionService.kt` |
| **Pipeline** | `ocr/UnifiedOcrPipeline.kt` |
| **Quota Manager** | `ocr/OcrQuotaManager.kt` |
| **Idle Processor** | `ocr/UnifiedOcrIdleProcessor.kt` |
| **Scheduler** | `ocr/UnifiedOcrScheduler.kt` |
| **Semantic Extractor** | `ocr/semantic/SemanticExtractor.kt` |
| **SemanticGeniusService** | `ocr/semantic/SemanticGeniusService.kt` |
| **Text Normalizer** | `ocr/semantic/TextNormalizer.kt` |
| **Document Classifier** | `ocr/semantic/DocumentClassifier.kt` |
| **Keyword Extractor** | `ocr/semantic/KeywordExtractor.kt` |
| **Entity Extractor** | `ocr/semantic/EntityExtractor.kt` |
| **Domain Classifier** | `ocr/semantic/classification/DomainIntentClassifier.kt` |
| **Language Detector** | `ocr/semantic/classification/LanguageDetector.kt` |
| **Dictionary Loader** | `ocr/semantic/classification/DictionaryLoader.kt` |
| **Context Disambiguator** | `ocr/semantic/classification/ContextDisambiguator.kt` |
| **Dominant Evidence** | `ocr/semantic/classification/DominantEvidenceRules.kt` |
| **User Priors** | `ocr/semantic/classification/UserPriors.kt` |
| **Semantic Bundle** | `ocr/model/SemanticBundle.kt` |
| **Domain/Intent Enums** | `ocr/model/DomainIntent.kt` |
| **Classification Explanation** | `ocr/model/ClassificationExplanation.kt` |
| **Database Entities** | `data/db/UnifiedOcr.kt` |
| **DAO** | `data/db/UnifiedOcrDao.kt` |
| **Repository** | `data/db/UnifiedOcrRepository.kt` |

---

## NO-TOUCH ZONES

| File | Lines | What |
|------|-------|------|
| `UnifiedOcrIdleProcessor.kt` | Safety constants | Idle threshold, memory limits |
| `UnifiedOcrIdleProcessor.kt` | Processing loop | Abort checks, yield(), recycle() |
| `CloudVisionService.kt` | Error handling | Retry logic, rate limiting |

---

## Adding a New Language

1. Create `assets/classification/{lang}/config.json`:
```json
{
  "languageCode": "es",
  "languageName": "Español",
  "version": 1,
  "completeness": { "domains": 22, "intents": 56, "collocations": 0 }
}
```

2. Create domain dictionaries in `domains/` (22 files):
```json
{
  "domain": "WORK",
  "tierA": { "weight": 4.0, "terms": ["reunión", "proyecto", "equipo"] },
  "tierB": { "weight": 2.5, "terms": ["oficina", "colega"] },
  "tierC": { "weight": 1.0, "terms": ["trabajo"] }
}
```

3. Create intent dictionaries in `intents/` (56 files)

4. Optionally add `collocations.json` for disambiguation

5. Update `LanguageDetector.SUPPORTED_LANGUAGES`:
```kotlin
val SUPPORTED_LANGUAGES = setOf("en", "es")  // Add new language code
```

6. Add language markers to `LanguageDetector.LANGUAGE_MARKERS`
