"""
Command-line interface for the test data generator.

Usage:
    python -m test_data_generator generate-ocr --count 100
    python -m test_data_generator generate-events --count 50
    python -m test_data_generator generate-pairs --high 50 --medium 30 --low 50
    python -m test_data_generator generate-database --ocr 200 --events 100 --pairs 100
    python -m test_data_generator quick-test
"""

import argparse
import sys
from pathlib import Path
from typing import List, Optional

from .config import Domain, FOCUS_DOMAINS
from .generators.ocr_generator import OcrGenerator
from .generators.event_generator import EventGenerator
from .generators.relationship_generator import RelationshipGenerator
from .outputs.sqlite_writer import SQLiteWriter, write_to_database
from .outputs.json_writer import JsonWriter, write_json_fixtures


def parse_domains(domain_str: Optional[str]) -> List[Domain]:
    """Parse comma-separated domain string."""
    if not domain_str:
        return FOCUS_DOMAINS

    domain_names = [d.strip().upper() for d in domain_str.split(",")]
    domains = []
    for name in domain_names:
        try:
            domains.append(Domain(name))
        except ValueError:
            print(f"Warning: Unknown domain '{name}', skipping")

    return domains if domains else FOCUS_DOMAINS


def cmd_generate_ocr(args):
    """Generate OCR text records."""
    print(f"Generating {args.count} OCR records...")

    domains = parse_domains(args.domains)
    gen = OcrGenerator(seed=args.seed)

    records = gen.generate_batch(
        count=args.count,
        domains=domains,
        date_range_days=args.date_range
    )

    # Output
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    if args.format == "json" or args.format == "both":
        writer = JsonWriter(str(output_dir))
        path = writer.write_ocr_fixtures(records, group_by_source=args.group_by_source)
        print(f"JSON output: {path}")

    if args.format == "sqlite" or args.format == "both":
        db_path = output_dir / "test_ocr.db"
        with SQLiteWriter(str(db_path)) as writer:
            writer.insert_ocr_texts(records)
            stats = writer.get_stats()
        print(f"SQLite output: {db_path}")
        print(f"  Records: {stats['ocr_text_count']}")
        print(f"  Domains: {', '.join(stats['ocr_domains'])}")

    print(f"Generated {len(records)} OCR records")


def cmd_generate_events(args):
    """Generate calendar event records."""
    print(f"Generating {args.count} calendar events...")

    domains = parse_domains(args.domains)
    gen = EventGenerator(seed=args.seed)

    events = gen.generate_batch(
        count=args.count,
        domains=domains,
        date_range_days=args.date_range
    )

    # Output
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    if args.format == "json" or args.format == "both":
        writer = JsonWriter(str(output_dir))
        path = writer.write_event_fixtures(events)
        print(f"JSON output: {path}")

    if args.format == "sqlite" or args.format == "both":
        db_path = output_dir / "test_events.db"
        with SQLiteWriter(str(db_path)) as writer:
            writer.insert_calendar_events(events)
            stats = writer.get_stats()
        print(f"SQLite output: {db_path}")
        print(f"  Events: {stats['calendar_event_count']}")

    print(f"Generated {len(events)} calendar events")


def cmd_generate_pairs(args):
    """Generate relationship pairs."""
    total = args.high + args.medium + args.low
    print(f"Generating {total} relationship pairs...")
    print(f"  High affinity: {args.high}")
    print(f"  Medium affinity: {args.medium}")
    print(f"  Low affinity: {args.low}")

    domains = parse_domains(args.domains)
    gen = RelationshipGenerator(seed=args.seed)

    ocr_records, events, relationships = gen.generate_batch(
        high_count=args.high,
        medium_count=args.medium,
        low_count=args.low,
        domains=domains,
        date_range_days=args.date_range
    )

    # Output
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    if args.format == "json" or args.format == "both":
        paths = write_json_fixtures(
            str(output_dir),
            ocr_records,
            events,
            relationships
        )
        print(f"JSON output: {output_dir}")
        for name, path in paths.items():
            print(f"  {name}: {path}")

    if args.format == "sqlite" or args.format == "both":
        db_path = output_dir / "test_semantic.db"
        stats = write_to_database(str(db_path), ocr_records, events)
        print(f"SQLite output: {db_path}")
        print(f"  OCR records: {stats['ocr_text_count']}")
        print(f"  Events: {stats['calendar_event_count']}")

    # Also write golden test format
    if args.golden:
        writer = JsonWriter(str(output_dir))
        golden_path = writer.write_golden_test_format(
            ocr_records, events, relationships,
            "generated_golden_tests.json"
        )
        print(f"Golden test format: {golden_path}")

    print(f"Generated {len(relationships)} relationship pairs")


def cmd_generate_database(args):
    """Generate complete test database."""
    print(f"Generating complete test database...")
    print(f"  OCR records: {args.ocr}")
    print(f"  Events: {args.events}")
    print(f"  Relationship pairs: {args.pairs}")

    domains = parse_domains(args.domains)

    # Generate OCR records
    ocr_gen = OcrGenerator(seed=args.seed)
    ocr_records = ocr_gen.generate_batch(args.ocr, domains)
    print(f"  Generated {len(ocr_records)} OCR records")

    # Generate events
    event_gen = EventGenerator(seed=args.seed)
    events = event_gen.generate_batch(args.events, domains)
    print(f"  Generated {len(events)} events")

    # Generate relationship pairs
    rel_gen = RelationshipGenerator(seed=args.seed)
    pair_ocr, pair_events, relationships = rel_gen.generate_batch(
        high_count=args.pairs // 3,
        medium_count=args.pairs // 3,
        low_count=args.pairs - 2 * (args.pairs // 3),
        domains=domains
    )

    # Combine
    all_ocr = ocr_records + pair_ocr
    all_events = events + pair_events

    # Output
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    # Write database
    db_path = output_dir / "test_semantic.db"
    stats = write_to_database(str(db_path), all_ocr, all_events)

    print(f"\nDatabase written: {db_path}")
    print(f"  Total OCR records: {stats['ocr_text_count']}")
    print(f"  Total events: {stats['calendar_event_count']}")
    print(f"  Domains covered: {', '.join(stats['ocr_domains'])}")

    # Also write JSON fixtures
    paths = write_json_fixtures(
        str(output_dir / "json"),
        all_ocr,
        all_events,
        relationships
    )
    print(f"\nJSON fixtures: {output_dir / 'json'}")

    # Write golden test format
    writer = JsonWriter(str(output_dir))
    golden_path = writer.write_golden_test_format(
        pair_ocr, pair_events, relationships
    )
    print(f"Golden tests: {golden_path}")


def cmd_quick_test(args):
    """Quick sanity test with small dataset."""
    print("Running quick test (10 of each)...")

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    # Generate small batch
    rel_gen = RelationshipGenerator(seed=42)
    ocr_records, events, relationships = rel_gen.generate_batch(
        high_count=4,
        medium_count=3,
        low_count=3,
        domains=[Domain.PROFESSIONAL, Domain.LDS_LEADERSHIP, Domain.EDUCATION]
    )

    print(f"\nGenerated:")
    print(f"  OCR records: {len(ocr_records)}")
    print(f"  Events: {len(events)}")
    print(f"  Relationships: {len(relationships)}")

    # Show samples
    print("\n--- Sample OCR Record ---")
    sample_ocr = ocr_records[0]
    print(f"ID: {sample_ocr.id}")
    print(f"Domain: {sample_ocr.domain}")
    print(f"Text preview: {sample_ocr.text[:200]}...")

    print("\n--- Sample Event ---")
    sample_event = events[0]
    print(f"ID: {sample_event.id}")
    print(f"Title: {sample_event.title}")
    print(f"Description: {sample_event.description[:100] if sample_event.description else 'N/A'}...")

    print("\n--- Sample Relationship ---")
    sample_rel = relationships[0]
    print(f"Type: {sample_rel.relationship_type}")
    print(f"Expected affinity: {sample_rel.expected_affinity_min:.2f} - {sample_rel.expected_affinity_max:.2f}")
    print(f"Match reasons: {', '.join(sample_rel.match_reasons)}")

    # Write to files
    db_path = output_dir / "quick_test.db"
    write_to_database(str(db_path), ocr_records, events)
    print(f"\nDatabase: {db_path}")

    json_paths = write_json_fixtures(
        str(output_dir / "quick_test_json"),
        ocr_records,
        events,
        relationships
    )
    print(f"JSON: {output_dir / 'quick_test_json'}")

    print("\nQuick test complete!")


def main():
    """Main CLI entry point."""
    parser = argparse.ArgumentParser(
        description="Semantic Test Data Generator for SemanticGeniusService testing",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python -m test_data_generator quick-test
  python -m test_data_generator generate-ocr --count 100 --output fixtures/
  python -m test_data_generator generate-events --count 50 --domains PROFESSIONAL,LDS_LEADERSHIP
  python -m test_data_generator generate-pairs --high 50 --medium 30 --low 50
  python -m test_data_generator generate-database --ocr 200 --events 100 --pairs 100
        """
    )

    subparsers = parser.add_subparsers(dest="command", help="Command to run")

    # generate-ocr command
    ocr_parser = subparsers.add_parser("generate-ocr", help="Generate OCR text records")
    ocr_parser.add_argument("--count", type=int, default=100, help="Number of records")
    ocr_parser.add_argument("--domains", type=str, help="Comma-separated domains")
    ocr_parser.add_argument("--date-range", type=int, default=30, help="Days to spread across")
    ocr_parser.add_argument("--output", type=str, default="fixtures/", help="Output directory")
    ocr_parser.add_argument("--format", choices=["json", "sqlite", "both"], default="both")
    ocr_parser.add_argument("--group-by-source", action="store_true", help="Group JSON by source type")
    ocr_parser.add_argument("--seed", type=int, help="Random seed")
    ocr_parser.set_defaults(func=cmd_generate_ocr)

    # generate-events command
    events_parser = subparsers.add_parser("generate-events", help="Generate calendar events")
    events_parser.add_argument("--count", type=int, default=50, help="Number of events")
    events_parser.add_argument("--domains", type=str, help="Comma-separated domains")
    events_parser.add_argument("--date-range", type=int, default=30, help="Days to spread across")
    events_parser.add_argument("--output", type=str, default="fixtures/", help="Output directory")
    events_parser.add_argument("--format", choices=["json", "sqlite", "both"], default="both")
    events_parser.add_argument("--seed", type=int, help="Random seed")
    events_parser.set_defaults(func=cmd_generate_events)

    # generate-pairs command
    pairs_parser = subparsers.add_parser("generate-pairs", help="Generate relationship pairs")
    pairs_parser.add_argument("--high", type=int, default=50, help="High affinity pairs")
    pairs_parser.add_argument("--medium", type=int, default=30, help="Medium affinity pairs")
    pairs_parser.add_argument("--low", type=int, default=50, help="Low affinity pairs")
    pairs_parser.add_argument("--domains", type=str, help="Comma-separated domains")
    pairs_parser.add_argument("--date-range", type=int, default=30, help="Days to spread across")
    pairs_parser.add_argument("--output", type=str, default="fixtures/", help="Output directory")
    pairs_parser.add_argument("--format", choices=["json", "sqlite", "both"], default="both")
    pairs_parser.add_argument("--golden", action="store_true", help="Also output golden test format")
    pairs_parser.add_argument("--seed", type=int, help="Random seed")
    pairs_parser.set_defaults(func=cmd_generate_pairs)

    # generate-database command
    db_parser = subparsers.add_parser("generate-database", help="Generate complete test database")
    db_parser.add_argument("--ocr", type=int, default=200, help="Number of OCR records")
    db_parser.add_argument("--events", type=int, default=100, help="Number of events")
    db_parser.add_argument("--pairs", type=int, default=100, help="Number of relationship pairs")
    db_parser.add_argument("--domains", type=str, help="Comma-separated domains")
    db_parser.add_argument("--output", type=str, default="fixtures/", help="Output directory")
    db_parser.add_argument("--seed", type=int, help="Random seed")
    db_parser.set_defaults(func=cmd_generate_database)

    # quick-test command
    quick_parser = subparsers.add_parser("quick-test", help="Quick sanity test")
    quick_parser.add_argument("--output", type=str, default="fixtures/quick_test/", help="Output directory")
    quick_parser.set_defaults(func=cmd_quick_test)

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        sys.exit(1)

    args.func(args)


if __name__ == "__main__":
    main()
