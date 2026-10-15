"""
JSON fixture writer.

Writes generated test data to JSON files for use in unit tests.
"""

import json
from pathlib import Path
from typing import List, Optional
from datetime import datetime

from ..models import (
    GeneratedOcrText, GeneratedCalendarEvent,
    GeneratedLocalEvent, SemanticRelationship
)


class JsonWriter:
    """
    Write generated data to JSON fixture files.

    Creates JSON files suitable for loading in unit tests.
    """

    def __init__(self, output_dir: str):
        """
        Initialize JSON writer.

        Args:
            output_dir: Directory for output files
        """
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)

    def write_ocr_fixtures(
        self,
        records: List[GeneratedOcrText],
        filename: Optional[str] = None,
        group_by_source: bool = False
    ) -> str:
        """
        Write OCR text records to JSON.

        Args:
            records: List of GeneratedOcrText records
            filename: Output filename (auto-generated if None)
            group_by_source: If True, create separate files per source type

        Returns:
            Path to output file(s)
        """
        if group_by_source:
            # Group by source type
            by_source = {}
            for record in records:
                source = record.source_type
                if source not in by_source:
                    by_source[source] = []
                by_source[source].append(record.to_dict())

            paths = []
            for source, data in by_source.items():
                path = self.output_dir / f"ocr_{source.lower()}.json"
                self._write_json(path, {"records": data, "count": len(data)})
                paths.append(str(path))

            return ", ".join(paths)

        # Single file
        if filename is None:
            filename = "ocr_texts.json"

        path = self.output_dir / filename
        data = {
            "generated_at": datetime.now().isoformat(),
            "count": len(records),
            "records": [r.to_dict() for r in records]
        }
        self._write_json(path, data)
        return str(path)

    def write_event_fixtures(
        self,
        events: List[GeneratedCalendarEvent],
        filename: Optional[str] = None
    ) -> str:
        """
        Write calendar event records to JSON.

        Args:
            events: List of GeneratedCalendarEvent records
            filename: Output filename

        Returns:
            Path to output file
        """
        if filename is None:
            filename = "calendar_events.json"

        path = self.output_dir / filename
        data = {
            "generated_at": datetime.now().isoformat(),
            "count": len(events),
            "events": [e.to_dict() for e in events]
        }
        self._write_json(path, data)
        return str(path)

    def write_local_event_fixtures(
        self,
        events: List[GeneratedLocalEvent],
        filename: Optional[str] = None
    ) -> str:
        """
        Write local event records to JSON.

        Args:
            events: List of GeneratedLocalEvent records
            filename: Output filename

        Returns:
            Path to output file
        """
        if filename is None:
            filename = "local_events.json"

        path = self.output_dir / filename
        data = {
            "generated_at": datetime.now().isoformat(),
            "count": len(events),
            "events": [e.to_dict() for e in events]
        }
        self._write_json(path, data)
        return str(path)

    def write_relationships(
        self,
        relationships: List[SemanticRelationship],
        filename: Optional[str] = None
    ) -> str:
        """
        Write semantic relationships to JSON.

        Args:
            relationships: List of SemanticRelationship records
            filename: Output filename

        Returns:
            Path to output file
        """
        if filename is None:
            filename = "relationships.json"

        path = self.output_dir / filename

        # Group by relationship type for easier analysis
        by_type = {"HIGH_MATCH": [], "MEDIUM_MATCH": [], "LOW_MATCH": []}
        for rel in relationships:
            by_type[rel.relationship_type].append(rel.to_dict())

        data = {
            "generated_at": datetime.now().isoformat(),
            "total_count": len(relationships),
            "by_type": {
                "high_match": {
                    "count": len(by_type["HIGH_MATCH"]),
                    "expected_affinity_range": [0.60, 0.85],
                    "relationships": by_type["HIGH_MATCH"]
                },
                "medium_match": {
                    "count": len(by_type["MEDIUM_MATCH"]),
                    "expected_affinity_range": [0.25, 0.50],
                    "relationships": by_type["MEDIUM_MATCH"]
                },
                "low_match": {
                    "count": len(by_type["LOW_MATCH"]),
                    "expected_affinity_range": [0.00, 0.20],
                    "relationships": by_type["LOW_MATCH"]
                }
            }
        }

        self._write_json(path, data)
        return str(path)

    def write_all(
        self,
        ocr_records: List[GeneratedOcrText],
        events: List[GeneratedCalendarEvent],
        relationships: List[SemanticRelationship],
        local_events: Optional[List[GeneratedLocalEvent]] = None
    ) -> dict:
        """
        Write all data to JSON files.

        Args:
            ocr_records: OCR text records
            events: Calendar event records
            relationships: Semantic relationships
            local_events: Optional local event records

        Returns:
            Dictionary of output paths
        """
        paths = {
            "ocr_texts": self.write_ocr_fixtures(ocr_records),
            "calendar_events": self.write_event_fixtures(events),
            "relationships": self.write_relationships(relationships),
        }

        if local_events:
            paths["local_events"] = self.write_local_event_fixtures(local_events)

        # Also write a manifest
        manifest_path = self.output_dir / "manifest.json"
        manifest = {
            "generated_at": datetime.now().isoformat(),
            "files": paths,
            "stats": {
                "ocr_count": len(ocr_records),
                "event_count": len(events),
                "relationship_count": len(relationships),
                "local_event_count": len(local_events) if local_events else 0,
            }
        }
        self._write_json(manifest_path, manifest)
        paths["manifest"] = str(manifest_path)

        return paths

    def write_golden_test_format(
        self,
        ocr_records: List[GeneratedOcrText],
        events: List[GeneratedCalendarEvent],
        relationships: List[SemanticRelationship],
        filename: Optional[str] = None
    ) -> str:
        """
        Write data in golden test case format.

        Compatible with existing golden_test_cases.json format.

        Args:
            ocr_records: OCR text records
            events: Calendar event records
            relationships: Semantic relationships
            filename: Output filename

        Returns:
            Path to output file
        """
        if filename is None:
            filename = "generated_test_cases.json"

        # Build lookup maps
        ocr_by_id = {r.id: r for r in ocr_records}
        event_by_id = {e.id: e for e in events}

        # Create test cases
        test_cases = []
        for i, rel in enumerate(relationships):
            ocr = ocr_by_id.get(rel.ocr_id)
            event = event_by_id.get(rel.event_id)

            if not ocr or not event:
                continue

            case_id = f"GEN_{rel.relationship_type}_{i:04d}"

            test_case = {
                "id": case_id,
                "category": f"generated_{rel.relationship_type.lower()}",
                "input": ocr.text[:500],  # Truncate for readability
                "expected_domain": ocr.domain,
                "min_confidence": 0.5,
                "event_context": {
                    "title": event.title,
                    "description": event.description[:200] if event.description else None,
                    "day_id": event.day_id,
                },
                "expected_affinity_range": [
                    rel.expected_affinity_min,
                    rel.expected_affinity_max
                ],
                "match_reasons": rel.match_reasons,
            }
            test_cases.append(test_case)

        path = self.output_dir / filename
        data = {
            "version": "2.0",
            "description": "Generated test cases for SemanticGeniusService affinity testing",
            "generated_at": datetime.now().isoformat(),
            "test_count": len(test_cases),
            "categories": {
                "generated_high_match": "Generated pairs with high expected affinity",
                "generated_medium_match": "Generated pairs with medium expected affinity",
                "generated_low_match": "Generated pairs with low expected affinity",
            },
            "test_cases": test_cases
        }
        self._write_json(path, data)
        return str(path)

    def _write_json(self, path: Path, data: dict) -> None:
        """Write data to JSON file with pretty printing."""
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)


def write_json_fixtures(
    output_dir: str,
    ocr_records: List[GeneratedOcrText],
    events: List[GeneratedCalendarEvent],
    relationships: List[SemanticRelationship]
) -> dict:
    """
    Write all fixtures to JSON files.

    Args:
        output_dir: Output directory
        ocr_records: OCR text records
        events: Calendar event records
        relationships: Semantic relationships

    Returns:
        Dictionary of output paths
    """
    writer = JsonWriter(output_dir)
    return writer.write_all(ocr_records, events, relationships)
