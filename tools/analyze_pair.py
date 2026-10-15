"""Analyze a HIGH_MATCH pair to understand affinity scoring."""

import json

# Load relationships
rels = json.load(open('fixtures/semantic_test/relationships.json'))
high_rels = rels['by_type']['high_match']['relationships'][:3]

# Load OCR records
ocr_data = json.load(open('fixtures/semantic_test/ocr_texts.json'))
ocr_by_id = {r['id']: r for r in ocr_data['records']}

# Load events - need to find them in unified_ocr_text
# Events are stored as CALENDAR_EVENT source type
evt_ocr_by_id = {r['sourceId']: r for r in ocr_data['records'] if r['sourceType'] == 'CALENDAR_EVENT'}

for rel in high_rels:
    ocr = ocr_by_id.get(rel['ocrId'])
    evt = evt_ocr_by_id.get(rel['eventId'])

    if ocr and evt:
        print('=' * 60)
        print('HIGH MATCH PAIR')
        print('=' * 60)
        print(f"OCR ID: {rel['ocrId']}")
        print(f"Event ID: {rel['eventId']}")
        print()

        print('--- OCR Record ---')
        print(f"Domain: {ocr.get('domain')}")
        ocr_entities = json.loads(ocr.get('entitiesJson') or '[]')
        print(f"Entities ({len(ocr_entities)}):")
        for e in ocr_entities[:5]:
            print(f"  - {e['type']}: {e['text']}")
        ocr_keywords = json.loads(ocr.get('keywordsJson') or '[]')
        print(f"Keywords ({len(ocr_keywords)}): {[k['term'] for k in ocr_keywords[:5]]}")

        print()
        print('--- Event Profile ---')
        print(f"Domain: {evt.get('domain')}")
        evt_entities = json.loads(evt.get('entitiesJson') or '[]')
        print(f"Entities ({len(evt_entities)}):")
        for e in evt_entities[:5]:
            print(f"  - {e['type']}: {e['text']}")
        evt_keywords = json.loads(evt.get('keywordsJson') or '[]')
        print(f"Keywords ({len(evt_keywords)}): {[k['term'] for k in evt_keywords[:5]]}")

        # Calculate expected overlap
        ocr_persons = {e['text'] for e in ocr_entities if e['type'] == 'PERSON'}
        evt_persons = {e['text'] for e in evt_entities if e['type'] == 'PERSON'}
        shared_persons = ocr_persons & evt_persons
        print()
        print(f"Shared PERSON entities: {shared_persons}")

        ocr_orgs = {e['text'] for e in ocr_entities if e['type'] == 'ORG'}
        evt_orgs = {e['text'] for e in evt_entities if e['type'] == 'ORG'}
        shared_orgs = ocr_orgs & evt_orgs
        print(f"Shared ORG entities: {shared_orgs}")

        ocr_kw = {k['term'].lower() for k in ocr_keywords}
        evt_kw = {k['term'].lower() for k in evt_keywords}
        shared_kw = ocr_kw & evt_kw
        print(f"Shared keywords ({len(shared_kw)}): {list(shared_kw)[:10]}")

        print()
    else:
        print(f"Missing: OCR={rel['ocrId']} exists={ocr is not None}, Event={rel['eventId']} exists={evt is not None}")
