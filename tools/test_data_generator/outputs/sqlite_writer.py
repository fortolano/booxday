"""
SQLite database writer.

Writes generated test data to SQLite databases compatible
with the Room database schema used in the Android app.
"""

import sqlite3
from pathlib import Path
from typing import List, Optional

from ..models import GeneratedOcrText, GeneratedCalendarEvent, GeneratedLocalEvent


class SQLiteWriter:
    """
    Write generated data to SQLite database.

    Creates Room-compatible database schema for testing
    with SemanticGeniusService.
    """

    def __init__(self, db_path: str):
        """
        Initialize SQLite writer.

        Args:
            db_path: Path to output database file
        """
        self.db_path = Path(db_path)
        self.conn: Optional[sqlite3.Connection] = None

    def __enter__(self):
        """Context manager entry."""
        self.open()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        """Context manager exit."""
        self.close()

    def open(self) -> None:
        """Open database connection and create schema."""
        # Ensure parent directory exists
        self.db_path.parent.mkdir(parents=True, exist_ok=True)

        self.conn = sqlite3.connect(str(self.db_path))
        self.create_schema()

    def close(self) -> None:
        """Close database connection."""
        if self.conn:
            self.conn.close()
            self.conn = None

    def create_schema(self) -> None:
        """Create Room-compatible table schema."""
        cursor = self.conn.cursor()

        # unified_ocr_text table (matches UnifiedOcrText entity)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS unified_ocr_text (
                id TEXT PRIMARY KEY NOT NULL,
                sourceType TEXT NOT NULL,
                sourceId TEXT NOT NULL,
                pageId TEXT NOT NULL,
                text TEXT NOT NULL,
                textNormalized TEXT NOT NULL,
                cleanText TEXT NOT NULL,
                language TEXT,
                wordCount INTEGER NOT NULL DEFAULT 0,
                confidence REAL NOT NULL DEFAULT 0.0,
                docStyle TEXT,
                keywordsJson TEXT,
                keyPhrasesJson TEXT,
                keySentencesJson TEXT,
                entitiesJson TEXT,
                actionItemsJson TEXT,
                temporalSignalsJson TEXT,
                topicsJson TEXT,
                synopsis TEXT,
                domain TEXT,
                domainConfidence REAL NOT NULL DEFAULT 0.0,
                intentsJson TEXT,
                subdomainHint TEXT,
                classificationExplainJson TEXT,
                sourceTitle TEXT,
                sourcePath TEXT,
                sourceDate TEXT,
                sourceTimestamp INTEGER,
                semanticVersion INTEGER NOT NULL DEFAULT 2,
                updatedAt INTEGER NOT NULL
            )
        """)

        # Create indices for common queries
        cursor.execute("""
            CREATE INDEX IF NOT EXISTS idx_ocr_source
            ON unified_ocr_text(sourceType, sourceId)
        """)
        cursor.execute("""
            CREATE INDEX IF NOT EXISTS idx_ocr_date
            ON unified_ocr_text(sourceDate)
        """)
        cursor.execute("""
            CREATE INDEX IF NOT EXISTS idx_ocr_domain
            ON unified_ocr_text(domain)
        """)

        # calendar_events table (matches CalendarEvent entity)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS calendar_events (
                id TEXT PRIMARY KEY NOT NULL,
                providerId TEXT NOT NULL DEFAULT 'GOOGLE',
                accountId TEXT NOT NULL DEFAULT '',
                accountEmail TEXT NOT NULL DEFAULT '',
                calendarId TEXT NOT NULL DEFAULT '',
                calendarName TEXT NOT NULL DEFAULT '',
                title TEXT NOT NULL,
                dayId TEXT NOT NULL,
                startTime INTEGER NOT NULL,
                endTime INTEGER,
                isAllDay INTEGER NOT NULL DEFAULT 0,
                location TEXT,
                description TEXT,
                attendees TEXT,
                isReadOnly INTEGER NOT NULL DEFAULT 0,
                colorHex TEXT,
                syncedAt INTEGER NOT NULL
            )
        """)

        cursor.execute("""
            CREATE INDEX IF NOT EXISTS idx_event_day
            ON calendar_events(dayId)
        """)

        # local_events table (matches LocalEvent entity)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS local_events (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                dayId TEXT NOT NULL,
                startTime INTEGER NOT NULL,
                endTime INTEGER,
                isAllDay INTEGER NOT NULL DEFAULT 0,
                location TEXT,
                description TEXT,
                colorHex TEXT,
                attendees TEXT,
                syncEnabled INTEGER NOT NULL DEFAULT 0,
                syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY',
                googleEventId TEXT,
                googleCalendarId TEXT,
                googleCalendarName TEXT,
                lastSyncedAt INTEGER,
                serverEtag TEXT,
                isReadOnly INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                linkedMemoPageId TEXT,
                localCalendarId TEXT
            )
        """)

        cursor.execute("""
            CREATE INDEX IF NOT EXISTS idx_local_event_day
            ON local_events(dayId)
        """)

        self.conn.commit()

    def insert_ocr_texts(self, records: List[GeneratedOcrText]) -> int:
        """
        Insert OCR text records.

        Args:
            records: List of GeneratedOcrText records

        Returns:
            Number of records inserted
        """
        if not self.conn:
            raise RuntimeError("Database not open")

        cursor = self.conn.cursor()

        insert_sql = """
            INSERT OR REPLACE INTO unified_ocr_text (
                id, sourceType, sourceId, pageId, text, textNormalized,
                cleanText, language, wordCount, confidence, docStyle,
                keywordsJson, keyPhrasesJson, keySentencesJson, entitiesJson,
                actionItemsJson, temporalSignalsJson, topicsJson, synopsis,
                domain, domainConfidence, intentsJson, subdomainHint,
                classificationExplainJson, sourceTitle, sourcePath,
                sourceDate, sourceTimestamp, semanticVersion, updatedAt
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
        """

        for record in records:
            data = record.to_dict()
            cursor.execute(insert_sql, (
                data["id"],
                data["sourceType"],
                data["sourceId"],
                data["pageId"],
                data["text"],
                data["textNormalized"],
                data["cleanText"],
                data["language"],
                data["wordCount"],
                data["confidence"],
                data["docStyle"],
                data["keywordsJson"],
                data["keyPhrasesJson"],
                data["keySentencesJson"],
                data["entitiesJson"],
                data["actionItemsJson"],
                data["temporalSignalsJson"],
                data["topicsJson"],
                data["synopsis"],
                data["domain"],
                data["domainConfidence"],
                data["intentsJson"],
                data["subdomainHint"],
                data["classificationExplainJson"],
                data["sourceTitle"],
                data["sourcePath"],
                data["sourceDate"],
                data["sourceTimestamp"],
                data["semanticVersion"],
                data["updatedAt"],
            ))

        self.conn.commit()
        return len(records)

    def insert_calendar_events(self, events: List[GeneratedCalendarEvent]) -> int:
        """
        Insert calendar event records.

        Args:
            events: List of GeneratedCalendarEvent records

        Returns:
            Number of records inserted
        """
        if not self.conn:
            raise RuntimeError("Database not open")

        cursor = self.conn.cursor()

        insert_sql = """
            INSERT OR REPLACE INTO calendar_events (
                id, providerId, accountId, accountEmail, calendarId,
                calendarName, title, dayId, startTime, endTime, isAllDay,
                location, description, attendees, isReadOnly, colorHex, syncedAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """

        for event in events:
            data = event.to_dict()
            cursor.execute(insert_sql, (
                data["id"],
                data["providerId"],
                data["accountId"],
                data["accountEmail"],
                data["calendarId"],
                data["calendarName"],
                data["title"],
                data["dayId"],
                data["startTime"],
                data["endTime"],
                data["isAllDay"],
                data["location"],
                data["description"],
                data["attendees"],
                data["isReadOnly"],
                data["colorHex"],
                data["syncedAt"],
            ))

        self.conn.commit()
        return len(events)

    def insert_local_events(self, events: List[GeneratedLocalEvent]) -> int:
        """
        Insert local event records.

        Args:
            events: List of GeneratedLocalEvent records

        Returns:
            Number of records inserted
        """
        if not self.conn:
            raise RuntimeError("Database not open")

        cursor = self.conn.cursor()

        insert_sql = """
            INSERT OR REPLACE INTO local_events (
                id, title, dayId, startTime, endTime, isAllDay, location,
                description, colorHex, attendees, syncEnabled, syncStatus,
                googleEventId, googleCalendarId, googleCalendarName,
                lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
                linkedMemoPageId, localCalendarId
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """

        for event in events:
            data = event.to_dict()
            cursor.execute(insert_sql, (
                data["id"],
                data["title"],
                data["dayId"],
                data["startTime"],
                data["endTime"],
                data["isAllDay"],
                data["location"],
                data["description"],
                data["colorHex"],
                data["attendees"],
                data["syncEnabled"],
                data["syncStatus"],
                data["googleEventId"],
                data["googleCalendarId"],
                data["googleCalendarName"],
                data["lastSyncedAt"],
                data["serverEtag"],
                data["isReadOnly"],
                data["createdAt"],
                data["updatedAt"],
                data["linkedMemoPageId"],
                data["localCalendarId"],
            ))

        self.conn.commit()
        return len(events)

    def get_stats(self) -> dict:
        """Get statistics about database contents."""
        if not self.conn:
            raise RuntimeError("Database not open")

        cursor = self.conn.cursor()

        stats = {}

        # OCR text stats
        cursor.execute("SELECT COUNT(*) FROM unified_ocr_text")
        stats["ocr_text_count"] = cursor.fetchone()[0]

        cursor.execute("SELECT DISTINCT domain FROM unified_ocr_text WHERE domain IS NOT NULL")
        stats["ocr_domains"] = [row[0] for row in cursor.fetchall()]

        cursor.execute("SELECT DISTINCT sourceType FROM unified_ocr_text")
        stats["ocr_source_types"] = [row[0] for row in cursor.fetchall()]

        # Calendar event stats
        cursor.execute("SELECT COUNT(*) FROM calendar_events")
        stats["calendar_event_count"] = cursor.fetchone()[0]

        # Local event stats
        cursor.execute("SELECT COUNT(*) FROM local_events")
        stats["local_event_count"] = cursor.fetchone()[0]

        return stats


def write_to_database(
    db_path: str,
    ocr_records: List[GeneratedOcrText],
    events: List[GeneratedCalendarEvent],
    local_events: Optional[List[GeneratedLocalEvent]] = None
) -> dict:
    """
    Write all data to a database file.

    Args:
        db_path: Path to output database
        ocr_records: OCR text records
        events: Calendar event records
        local_events: Optional local event records

    Returns:
        Statistics about written data
    """
    with SQLiteWriter(db_path) as writer:
        writer.insert_ocr_texts(ocr_records)
        writer.insert_calendar_events(events)
        if local_events:
            writer.insert_local_events(local_events)
        return writer.get_stats()
