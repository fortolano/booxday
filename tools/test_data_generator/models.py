"""
Data models mirroring Kotlin entities for test data generation.

These dataclasses match the Room database schema in the Android app.
"""

from dataclasses import dataclass, field
from typing import Optional, List
from datetime import datetime
import json


@dataclass
class GeneratedOcrText:
    """
    Mirrors UnifiedOcrText entity from UnifiedOcr.kt.

    This is the main OCR text record with full semantic bundle.
    """
    # Primary key: {sourceType}_{sourceId}_{pageId}
    id: str

    # Source identification
    source_type: str  # BOOX_NOTE, MEMO, TASK, CALENDAR_EVENT, LOCAL_EVENT
    source_id: str
    page_id: str

    # Raw OCR output
    text: str
    text_normalized: str
    clean_text: str
    language: str = "en"
    word_count: int = 0
    confidence: float = 0.95

    # Semantic bundle (JSON serialized)
    doc_style: Optional[str] = None
    keywords_json: Optional[str] = None
    key_phrases_json: Optional[str] = None
    key_sentences_json: Optional[str] = None
    entities_json: Optional[str] = None
    action_items_json: Optional[str] = None
    temporal_signals_json: Optional[str] = None
    topics_json: Optional[str] = None
    synopsis: Optional[str] = None

    # Domain + Intent classification
    domain: Optional[str] = None
    domain_confidence: float = 0.0
    intents_json: Optional[str] = None
    subdomain_hint: Optional[str] = None
    classification_explain_json: Optional[str] = None

    # Source linkage
    source_title: Optional[str] = None
    source_path: Optional[str] = None
    source_date: Optional[str] = None  # yyyy-MM-dd
    source_timestamp: Optional[int] = None  # epoch ms

    # Versioning
    semantic_version: int = 2
    updated_at: int = field(default_factory=lambda: int(datetime.now().timestamp() * 1000))

    def to_dict(self) -> dict:
        """Convert to dictionary for SQLite/JSON serialization."""
        return {
            "id": self.id,
            "sourceType": self.source_type,
            "sourceId": self.source_id,
            "pageId": self.page_id,
            "text": self.text,
            "textNormalized": self.text_normalized,
            "cleanText": self.clean_text,
            "language": self.language,
            "wordCount": self.word_count,
            "confidence": self.confidence,
            "docStyle": self.doc_style,
            "keywordsJson": self.keywords_json,
            "keyPhrasesJson": self.key_phrases_json,
            "keySentencesJson": self.key_sentences_json,
            "entitiesJson": self.entities_json,
            "actionItemsJson": self.action_items_json,
            "temporalSignalsJson": self.temporal_signals_json,
            "topicsJson": self.topics_json,
            "synopsis": self.synopsis,
            "domain": self.domain,
            "domainConfidence": self.domain_confidence,
            "intentsJson": self.intents_json,
            "subdomainHint": self.subdomain_hint,
            "classificationExplainJson": self.classification_explain_json,
            "sourceTitle": self.source_title,
            "sourcePath": self.source_path,
            "sourceDate": self.source_date,
            "sourceTimestamp": self.source_timestamp,
            "semanticVersion": self.semantic_version,
            "updatedAt": self.updated_at,
        }


@dataclass
class GeneratedCalendarEvent:
    """
    Mirrors CalendarEvent entity from CalendarEvent.kt.

    Represents a synced calendar event from Google/Outlook.
    """
    id: str
    provider_id: str = "GOOGLE"
    account_id: str = ""
    account_email: str = "test@example.com"
    calendar_id: str = "primary"
    calendar_name: str = "Primary Calendar"
    title: str = ""
    day_id: str = ""  # yyyy-MM-dd
    start_time: int = 0  # epoch ms
    end_time: Optional[int] = None
    is_all_day: bool = False
    location: Optional[str] = None
    description: Optional[str] = None
    attendees: Optional[str] = None  # JSON array of emails
    is_read_only: bool = False
    color_hex: Optional[str] = None
    synced_at: int = field(default_factory=lambda: int(datetime.now().timestamp() * 1000))
    # Test helper: domain (not persisted to calendar_events table)
    domain: Optional[str] = None

    def to_dict(self) -> dict:
        """Convert to dictionary for SQLite/JSON serialization."""
        return {
            "id": self.id,
            "providerId": self.provider_id,
            "accountId": self.account_id,
            "accountEmail": self.account_email,
            "calendarId": self.calendar_id,
            "calendarName": self.calendar_name,
            "title": self.title,
            "dayId": self.day_id,
            "startTime": self.start_time,
            "endTime": self.end_time,
            "isAllDay": 1 if self.is_all_day else 0,
            "location": self.location,
            "description": self.description,
            "attendees": self.attendees,
            "isReadOnly": 1 if self.is_read_only else 0,
            "colorHex": self.color_hex,
            "syncedAt": self.synced_at,
        }


@dataclass
class GeneratedLocalEvent:
    """
    Mirrors LocalEvent entity from LocalEvent.kt.

    Represents a locally-created event (source of truth).
    """
    id: str
    title: str
    day_id: str  # yyyy-MM-dd
    start_time: int  # epoch ms
    end_time: Optional[int] = None
    is_all_day: bool = False
    location: Optional[str] = None
    description: Optional[str] = None
    color_hex: Optional[str] = None
    attendees: Optional[str] = None  # comma-separated
    sync_enabled: bool = False
    sync_status: str = "LOCAL_ONLY"
    google_event_id: Optional[str] = None
    google_calendar_id: Optional[str] = None
    google_calendar_name: Optional[str] = None
    last_synced_at: Optional[int] = None
    server_etag: Optional[str] = None
    is_read_only: bool = False
    created_at: int = field(default_factory=lambda: int(datetime.now().timestamp() * 1000))
    updated_at: int = field(default_factory=lambda: int(datetime.now().timestamp() * 1000))
    linked_memo_page_id: Optional[str] = None
    local_calendar_id: Optional[str] = None

    def to_dict(self) -> dict:
        """Convert to dictionary for SQLite/JSON serialization."""
        return {
            "id": self.id,
            "title": self.title,
            "dayId": self.day_id,
            "startTime": self.start_time,
            "endTime": self.end_time,
            "isAllDay": 1 if self.is_all_day else 0,
            "location": self.location,
            "description": self.description,
            "colorHex": self.color_hex,
            "attendees": self.attendees,
            "syncEnabled": 1 if self.sync_enabled else 0,
            "syncStatus": self.sync_status,
            "googleEventId": self.google_event_id,
            "googleCalendarId": self.google_calendar_id,
            "googleCalendarName": self.google_calendar_name,
            "lastSyncedAt": self.last_synced_at,
            "serverEtag": self.server_etag,
            "isReadOnly": 1 if self.is_read_only else 0,
            "createdAt": self.created_at,
            "updatedAt": self.updated_at,
            "linkedMemoPageId": self.linked_memo_page_id,
            "localCalendarId": self.local_calendar_id,
        }


@dataclass
class SemanticRelationship:
    """
    Defines expected semantic affinity between an OCR text and an event.

    Used for validation in tests - verify that SemanticGeniusService
    calculates affinities within expected ranges.
    """
    ocr_id: str
    event_id: str
    expected_affinity_min: float  # 0.0-1.0
    expected_affinity_max: float  # 0.0-1.0
    relationship_type: str  # HIGH_MATCH, MEDIUM_MATCH, LOW_MATCH
    match_reasons: List[str] = field(default_factory=list)
    # Match reasons: DOMAIN, KEYWORD, PERSON, ORG, LOCATION, ACTION_ITEM, TEMPORAL, INTENT

    def to_dict(self) -> dict:
        """Convert to dictionary for JSON serialization."""
        return {
            "ocrId": self.ocr_id,
            "eventId": self.event_id,
            "expectedAffinityMin": self.expected_affinity_min,
            "expectedAffinityMax": self.expected_affinity_max,
            "relationshipType": self.relationship_type,
            "matchReasons": self.match_reasons,
        }


# Semantic bundle components (for building JSON fields)

@dataclass
class KeywordEntry:
    """Extracted keyword with scoring."""
    term: str
    score: float
    frequency: int = 1
    boosted_by: Optional[str] = None  # Source of boost (e.g., "tierA")

    def to_dict(self) -> dict:
        result = {"term": self.term, "score": self.score, "frequency": self.frequency}
        if self.boosted_by:
            result["boostedBy"] = self.boosted_by
        return result


@dataclass
class ExtractedEntity:
    """Named entity extracted from text."""
    text: str
    entity_type: str  # PERSON, ORG, LOCATION, DATE, TIME, EMAIL, PHONE, URL, MONEY, PERCENT
    confidence: float = 0.9

    def to_dict(self) -> dict:
        return {"text": self.text, "type": self.entity_type, "confidence": self.confidence}


@dataclass
class ActionItem:
    """Extracted action item (verb + object)."""
    verb: str
    object_text: str
    due_date: Optional[str] = None
    confidence: float = 0.8

    def to_dict(self) -> dict:
        result = {"verb": self.verb, "objectText": self.object_text, "confidence": self.confidence}
        if self.due_date:
            result["dueDate"] = self.due_date
        return result


@dataclass
class TemporalSignals:
    """Temporal references extracted from text."""
    explicit_dates: List[str] = field(default_factory=list)  # yyyy-MM-dd format
    weekdays: List[str] = field(default_factory=list)  # Monday, Tuesday, etc.
    relative_dates: List[str] = field(default_factory=list)  # tomorrow, next week, etc.
    times: List[str] = field(default_factory=list)  # 3pm, 14:00, etc.

    def to_dict(self) -> dict:
        return {
            "explicitDates": self.explicit_dates,
            "weekdays": self.weekdays,
            "relativeDates": self.relative_dates,
            "times": self.times,
        }


@dataclass
class IntentEntry:
    """Intent classification result."""
    intent: str
    confidence: float

    def to_dict(self) -> dict:
        return {"intent": self.intent, "confidence": self.confidence}


# Helper functions for JSON serialization

def serialize_keywords(keywords: List[KeywordEntry]) -> str:
    """Serialize keyword list to JSON string."""
    return json.dumps([k.to_dict() for k in keywords])


def serialize_entities(entities: List[ExtractedEntity]) -> str:
    """Serialize entity list to JSON string."""
    return json.dumps([e.to_dict() for e in entities])


def serialize_action_items(items: List[ActionItem]) -> str:
    """Serialize action items to JSON string."""
    return json.dumps([a.to_dict() for a in items])


def serialize_temporal_signals(signals: TemporalSignals) -> str:
    """Serialize temporal signals to JSON string."""
    return json.dumps(signals.to_dict())


def serialize_intents(intents: List[IntentEntry]) -> str:
    """Serialize intent list to JSON string."""
    return json.dumps([i.to_dict() for i in intents])


def serialize_string_list(items: List[str]) -> str:
    """Serialize string list to JSON string."""
    return json.dumps(items)
