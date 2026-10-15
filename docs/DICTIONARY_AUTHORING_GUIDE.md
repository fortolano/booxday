# Dictionary Authoring Guide

Guide for creating and maintaining SemanticGenius classification dictionaries.

---

## Quick Reference

| File Type | Location | Purpose |
|-----------|----------|---------|
| Domain | `assets/classification/en/domains/*.json` | Classify events by topic area |
| Intent | `assets/classification/en/intents/*.json` | Classify events by purpose/action |

---

## JSON Schema

### Domain Dictionary

```json
{
  "domain": "DOMAIN_NAME",
  "tierA": {
    "weight": 4.0,
    "terms": ["highly_specific_term1", "highly_specific_term2"]
  },
  "tierB": {
    "weight": 2.5,
    "terms": ["moderately_specific_term1", "moderately_specific_term2"]
  },
  "tierC": {
    "weight": 1.0,
    "terms": ["weak_signal_term1", "weak_signal_term2"]
  },
  "phrasePatterns": [
    { "pattern": "regex_pattern", "weight": 3.5 }
  ]
}
```

### Intent Dictionary

```json
{
  "intent": "INTENT_NAME",
  "tierA": { "weight": 4.0, "terms": [...] },
  "tierB": { "weight": 2.5, "terms": [...] },
  "tierC": { "weight": 1.0, "terms": [...] },
  "phrasePatterns": [
    { "pattern": "regex_pattern", "weight": 3.5 }
  ]
}
```

---

## Tier Guidelines

### TierA (weight: 4.0) - High Precision

**Purpose**: Terms that UNIQUELY identify this domain/intent.

**Rules**:
- Term should almost always indicate this domain
- Avoid terms that appear in multiple domains
- Examples: "standup" → WORK, "prescription" → HEALTH

### TierB (weight: 2.5) - Strong Signal

**Purpose**: Terms that strongly suggest this domain but may appear elsewhere.

**Rules**:
- Term is a strong indicator but not exclusive
- May need context from other terms
- Examples: "meeting" → WORK, "appointment" → could be HEALTH or WORK

### TierC (weight: 1.0) - Weak Signal

**Purpose**: Terms that slightly lean toward this domain.

**Rules**:
- Only provides supporting evidence
- Must be 4+ characters (except allowed abbreviations)
- Should NOT include generic terms (see blocklist)
- Examples: "schedule" → slight WORK signal

---

## Critical Rules

### 1. No Intra-File Duplicates

Same term CANNOT appear in multiple tiers within one file.

```json
// WRONG
{
  "tierA": { "terms": ["meeting"] },
  "tierC": { "terms": ["meeting"] }  // ERROR: duplicate
}

// CORRECT
{
  "tierA": { "terms": ["meeting"] },
  "tierC": { "terms": ["schedule"] }
}
```

### 2. Blocklisted Terms (NEVER USE)

These generic terms cause false positives and are automatically removed:

| Term | Why Blocked |
|------|-------------|
| need | Too generic, appears in all contexts |
| want | Too generic, appears in all contexts |
| get | Too generic, appears in all contexts |
| make | Too generic, appears in all contexts |
| do | Too generic, appears in all contexts |
| go | Too generic, appears in all contexts |
| see | Too generic, appears in all contexts |
| think | Too generic, appears in all contexts |
| feel | Too generic, appears in all contexts |
| know | Too generic, appears in all contexts |
| when | Too generic, appears in all contexts |
| what | Too generic, appears in all contexts |
| how | Too generic, appears in all contexts |
| where | Too generic, appears in all contexts |
| why | Too generic, appears in all contexts |
| list | Too generic, appears in all contexts |
| item | Too generic, appears in all contexts |
| note | Too generic, appears in all contexts |
| thing | Too generic, appears in all contexts |
| stuff | Too generic, appears in all contexts |

### 3. TierC Minimum Length

TierC terms must be 4+ characters, except for allowed abbreviations:

**Allowed Short Terms**:
```
hr, ai, ml, it, ux, ui, qa, pm, vp, ceo, cfo, cto,
api, sql, csv, pdf, ocr, lds, pta, k12, rn, md, pa,
dj, mc, tv, pc, ac, dc, hq, gm, am, pm, etc,
app, fix, bug, log, run, git, ssh, tcp, udp, url,
xml, css, dom, ios, aws, gcp, sdk, cta,
vet, pet, car, gas, job, pay, due, map, lab, god,
aim, bad, buy, eat, fun, mix, add, try, say, way
```

### 4. Weight Consistency

Weights must follow: `tierA > tierB > tierC`

Standard weights:
- tierA: 4.0
- tierB: 2.5
- tierC: 1.0

### 5. Phrase Patterns (Required for Intents)

Intent files MUST have at least 2 phrase patterns for accurate matching.

**Pattern Syntax**: Uses Java/Kotlin regex (escaped for JSON).

```json
"phrasePatterns": [
  { "pattern": "meeting\\s+(?:notes|summary)", "weight": 4.0 },
  { "pattern": "action\\s+items?", "weight": 3.5 }
]
```

**Common Pattern Elements**:
- `\\s+` - one or more whitespace
- `(?:a|b)` - match "a" or "b"
- `\\w+` - one or more word characters
- `[\\s-]?` - optional space or hyphen

---

## Validation

### Run Validation

```bash
# From project root
python tools/dictionary_validator/validate_dictionaries.py \
    --dictionaries app/src/main/assets/classification/en \
    --verbose
```

### Exit Codes

| Code | Meaning |
|------|---------|
| 0 | All validations pass |
| 1 | Warnings only (non-blocking) |
| 2 | Errors found (build fails) |

### Auto-Fix Issues

```bash
# Preview changes
python tools/dictionary_validator/fix_duplicates.py \
    --dictionaries app/src/main/assets/classification/en \
    --dry-run

# Apply fixes
python tools/dictionary_validator/fix_duplicates.py \
    --dictionaries app/src/main/assets/classification/en \
    --apply
```

---

## Golden Tests

### Test File Location

`tools/dictionary_validator/golden_tests/golden_test_cases.json`

### Test Case Format

```json
{
  "id": "DOM_WORK_001",
  "category": "domain_positive",
  "input": "Weekly standup with the team",
  "expected_domain": "WORK",
  "expected_intents": ["MEETING_NOTES"],
  "min_confidence": 0.6,
  "notes": "Core work domain test"
}
```

### Run Tests

```bash
# Validate test file format
python tools/dictionary_validator/golden_tests/run_tests.py --validate

# Show statistics
python tools/dictionary_validator/golden_tests/run_tests.py --stats

# Generate Kotlin test code
python tools/dictionary_validator/golden_tests/run_tests.py --generate-kotlin
```

---

## Adding a New Domain

1. Create `domains/new_domain.json`:

```json
{
  "domain": "NEW_DOMAIN",
  "tierA": {
    "weight": 4.0,
    "terms": ["unique_term1", "unique_term2"]
  },
  "tierB": {
    "weight": 2.5,
    "terms": ["strong_term1", "strong_term2"]
  },
  "tierC": {
    "weight": 1.0,
    "terms": ["weak_term1", "weak_term2"]
  },
  "phrasePatterns": [
    { "pattern": "pattern1\\s+pattern2", "weight": 3.5 }
  ]
}
```

2. Add to `Domain.kt` enum
3. Run validation
4. Add golden test cases

---

## Adding a New Intent

1. Create `intents/new_intent.json`:

```json
{
  "intent": "NEW_INTENT",
  "tierA": {
    "weight": 4.0,
    "terms": ["unique_term1", "unique_term2"]
  },
  "tierB": {
    "weight": 2.5,
    "terms": ["strong_term1", "strong_term2"]
  },
  "tierC": {
    "weight": 1.0,
    "terms": ["weak_term1", "weak_term2"]
  },
  "phrasePatterns": [
    { "pattern": "pattern1\\s+(?:notes|summary)", "weight": 4.0 },
    { "pattern": "(?:action|follow[\\s-]?up)\\s+items?", "weight": 3.5 }
  ]
}
```

2. Add to `Intent.kt` enum
3. Run validation
4. Add golden test cases

---

## Gradle Integration

Validation runs automatically before every build:

```bash
# Run validation only
./gradlew validateDictionaries

# Full build (includes validation)
./gradlew assembleDebug
```

If validation fails, the build fails with error details.

---

## Best Practices

1. **Be specific** - Choose terms that uniquely identify the domain/intent
2. **Avoid overlap** - Check if term already exists in another domain's tierA
3. **Test incrementally** - Run validation after each change
4. **Add golden tests** - For each new domain/intent, add test cases
5. **Document edge cases** - Add notes to test cases explaining tricky scenarios
6. **Review cross-file warnings** - Same term in multiple tierA indicates ambiguity

---

## File Structure

```
app/src/main/assets/classification/en/
├── domains/
│   ├── work.json
│   ├── health.json
│   ├── finance.json
│   └── ... (27 total)
├── intents/
│   ├── meeting_notes.json
│   ├── todo_list.json
│   ├── journal.json
│   └── ... (62 total)
├── abbreviations.json      # OCR corrections
├── collocations.json       # Disambiguation rules
└── event_patterns.json     # Event-specific patterns

tools/dictionary_validator/
├── validate_dictionaries.py    # Main validation script
├── fix_duplicates.py           # Auto-fix script
├── add_phrase_patterns.py      # Batch add patterns
└── golden_tests/
    ├── golden_test_cases.json  # 94 test cases
    └── run_tests.py            # Test runner
```

---

## Troubleshooting

### "Intra-file duplicate" error

Same term appears in multiple tiers. Keep only in highest tier.

### "Blocklisted term" error

Remove the generic term. Find a more specific alternative.

### "TierC term too short" error

Either:
- Remove the short term
- Add to ALLOWED_SHORT_TERMS if it's a valid abbreviation
- Move to a higher tier if appropriate

### "No phrase patterns" warning

Add at least 2 regex patterns to the intent file.

### Build fails with validation error

Run `python tools/dictionary_validator/fix_duplicates.py --apply` to auto-fix common issues.
