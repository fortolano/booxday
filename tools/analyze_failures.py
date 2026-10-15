"""Analyze test failures to understand root cause."""

import json
import sqlite3
from collections import Counter, defaultdict

# Load relationships
rels = json.load(open('test_data_generator/fixtures/semantic_test/relationships.json'))

# Load OCR data
ocr_data = json.load(open('test_data_generator/fixtures/semantic_test/ocr_texts.json'))
ocr_by_id = {r['id']: r for r in ocr_data['records']}
evt_by_id = {r['sourceId']: r for r in ocr_data['records'] if r['sourceType'] == 'CALENDAR_EVENT'}

# Expected ranges
RANGES = {
    'high_match': (0.60, 0.85),
    'low_match': (0.00, 0.20),
    'medium_match': (0.25, 0.50),
}

# We need to simulate what the algorithm computes
# Since we can't run the actual algorithm, let's analyze the data quality

print("=" * 70)
print("FAILURE ANALYSIS")
print("=" * 70)

# Count pairs by type
for match_type in ['high_match', 'medium_match', 'low_match']:
    type_data = rels['by_type'][match_type]
    pairs = type_data['relationships']
    expected_min, expected_max = RANGES[match_type]

    print(f"\n{match_type.upper()}: {len(pairs)} pairs (expected {expected_min}-{expected_max})")

    # Analyze overlap quality
    domain_matches = 0
    person_overlaps = []
    keyword_overlaps = []
    org_overlaps = []
    weekday_matches = 0

    for pair in pairs:
        ocr = ocr_by_id.get(pair['ocrId'])
        evt = evt_by_id.get(pair['eventId'])

        if not ocr or not evt:
            continue

        # Domain match
        if ocr['domain'] == evt['domain']:
            domain_matches += 1

        # Person overlap
        ocr_entities = json.loads(ocr.get('entitiesJson') or '[]')
        evt_entities = json.loads(evt.get('entitiesJson') or '[]')

        ocr_persons = {e['text'].lower() for e in ocr_entities if e['type'] == 'PERSON'}
        evt_persons = {e['text'].lower() for e in evt_entities if e['type'] == 'PERSON'}
        shared_persons = len(ocr_persons & evt_persons)
        person_overlaps.append(shared_persons)

        # Org overlap
        ocr_orgs = {e['text'].lower() for e in ocr_entities if e['type'] == 'ORG'}
        evt_orgs = {e['text'].lower() for e in evt_entities if e['type'] == 'ORG'}
        shared_orgs = len(ocr_orgs & evt_orgs)
        org_overlaps.append(shared_orgs)

        # Keyword overlap
        ocr_kw = json.loads(ocr.get('keywordsJson') or '[]')
        evt_kw = json.loads(evt.get('keywordsJson') or '[]')
        ocr_terms = {k['term'].lower() for k in ocr_kw}
        evt_terms = {k['term'].lower() for k in evt_kw}
        if ocr_terms and evt_terms:
            jaccard = len(ocr_terms & evt_terms) / len(ocr_terms | evt_terms)
            keyword_overlaps.append(jaccard)

        # Weekday match
        ocr_temporal = json.loads(ocr.get('temporalSignalsJson') or '{}')
        evt_temporal = json.loads(evt.get('temporalSignalsJson') or '{}')
        ocr_weekdays = set(ocr_temporal.get('weekdays', []))
        evt_weekdays = set(evt_temporal.get('weekdays', []))
        if ocr_weekdays & evt_weekdays:
            weekday_matches += 1

    print(f"  Domain matches: {domain_matches}/{len(pairs)} ({100*domain_matches/len(pairs):.1f}%)")
    print(f"  Avg person overlap: {sum(person_overlaps)/len(person_overlaps):.2f}")
    print(f"  Avg org overlap: {sum(org_overlaps)/len(org_overlaps):.2f}")
    if keyword_overlaps:
        print(f"  Avg keyword Jaccard: {sum(keyword_overlaps)/len(keyword_overlaps):.3f}")
    print(f"  Weekday matches: {weekday_matches}/{len(pairs)} ({100*weekday_matches/len(pairs):.1f}%)")

# Now estimate expected scores based on algorithm weights
print("\n" + "=" * 70)
print("ESTIMATED SCORE ANALYSIS FOR HIGH_MATCH PAIRS")
print("=" * 70)

# Algorithm weights
WEIGHTS = {
    'domain': 0.20,
    'intent': 0.12,
    'keyword': 0.18,
    'phrase': 0.08,
    'person': 0.12,  # 0.06 per match, max 0.12
    'org': 0.08,     # 0.04 per match, max 0.08
    'location': 0.07,
    'action': 0.10,
    'temporal': 0.05,
}

high_pairs = rels['by_type']['high_match']['relationships']
estimated_scores = []

for pair in high_pairs[:20]:  # Sample first 20
    ocr = ocr_by_id.get(pair['ocrId'])
    evt = evt_by_id.get(pair['eventId'])

    if not ocr or not evt:
        continue

    score = 0
    breakdown = {}

    # Domain
    if ocr['domain'] == evt['domain']:
        score += 0.20
        breakdown['domain'] = 0.20

    # Intent - check overlap
    ocr_intents = json.loads(ocr.get('intentsJson') or '[]')
    evt_intents = json.loads(evt.get('intentsJson') or '[]')
    ocr_intent_set = {i['intent'] for i in ocr_intents}
    evt_intent_set = {i['intent'] for i in evt_intents}
    if ocr_intent_set & evt_intent_set:
        # Avg confidence ~ 0.75
        score += 0.12 * 0.75
        breakdown['intent'] = 0.09

    # Person
    ocr_entities = json.loads(ocr.get('entitiesJson') or '[]')
    evt_entities = json.loads(evt.get('entitiesJson') or '[]')
    ocr_persons = {e['text'].lower() for e in ocr_entities if e['type'] == 'PERSON'}
    evt_persons = {e['text'].lower() for e in evt_entities if e['type'] == 'PERSON'}
    person_matches = len(ocr_persons & evt_persons)
    person_score = min(person_matches * 0.06, 0.12)
    score += person_score
    breakdown['person'] = person_score

    # Org
    ocr_orgs = {e['text'].lower() for e in ocr_entities if e['type'] == 'ORG'}
    evt_orgs = {e['text'].lower() for e in evt_entities if e['type'] == 'ORG'}
    org_matches = len(ocr_orgs & evt_orgs)
    org_score = min(org_matches * 0.04, 0.08)
    score += org_score
    breakdown['org'] = org_score

    # Keyword Jaccard
    ocr_kw = json.loads(ocr.get('keywordsJson') or '[]')
    evt_kw = json.loads(evt.get('keywordsJson') or '[]')
    ocr_terms = {k['term'].lower() for k in ocr_kw}
    evt_terms = {k['term'].lower() for k in evt_kw}
    if ocr_terms and evt_terms:
        jaccard = len(ocr_terms & evt_terms) / len(ocr_terms | evt_terms)
        kw_score = jaccard * 0.18
        score += kw_score
        breakdown['keyword'] = round(kw_score, 3)

    # Temporal (weekday match)
    ocr_temporal = json.loads(ocr.get('temporalSignalsJson') or '{}')
    evt_temporal = json.loads(evt.get('temporalSignalsJson') or '{}')
    ocr_weekdays = set(ocr_temporal.get('weekdays', []))
    evt_weekdays = set(evt_temporal.get('weekdays', []))
    if ocr_weekdays & evt_weekdays:
        score += 0.05
        breakdown['temporal'] = 0.05

    estimated_scores.append((score, breakdown, ocr['domain']))

# Show distribution
print("\nEstimated scores for HIGH_MATCH pairs:")
for score, breakdown, domain in sorted(estimated_scores, key=lambda x: x[0])[:10]:
    print(f"  {score:.3f} ({domain}): {breakdown}")

print("\nScore distribution:")
under_60 = sum(1 for s, _, _ in estimated_scores if s < 0.60)
in_range = sum(1 for s, _, _ in estimated_scores if 0.60 <= s <= 0.85)
over_85 = sum(1 for s, _, _ in estimated_scores if s > 0.85)
print(f"  Under 0.60: {under_60}/{len(estimated_scores)}")
print(f"  In range (0.60-0.85): {in_range}/{len(estimated_scores)}")
print(f"  Over 0.85: {over_85}/{len(estimated_scores)}")

# What's missing to get to 0.60?
print("\n" + "=" * 70)
print("WHAT'S MISSING TO REACH 0.60?")
print("=" * 70)
print("""
Max possible score: 1.00
- Domain match: 0.20
- Intent match: 0.12 (with confidence)
- Keyword Jaccard: 0.18 (at 100% overlap)
- Key phrases: 0.08
- Person matches: 0.12 (2+ matches)
- Org matches: 0.08 (2+ matches)
- Location matches: 0.07
- Action items: 0.10
- Temporal: 0.05

To hit 0.60, a HIGH_MATCH pair typically needs:
- Domain match: 0.20 ✓ (always for high match)
- Intent match: ~0.09 ✓ (usually)
- Person matches (2): 0.12 ✓ (usually)
- Org match (1): 0.04 ✓ (usually)
- Temporal: 0.05 ✓ (with weekday fix)
Subtotal: 0.50

Still need 0.10 from:
- Keywords: need ~55% Jaccard for 0.10
- Phrases: need 2-3 matches for 0.08
- Actions: need verb+object matches for 0.05-0.10
""")
