#!/usr/bin/env python3
"""
Golden Test Runner for SemanticGenius Classification

This script validates the golden test file format and can generate
Kotlin test code for running against the actual classifier.

Usage:
  python run_tests.py --validate              # Validate test file format
  python run_tests.py --generate-kotlin       # Generate Kotlin test code
  python run_tests.py --stats                 # Show test statistics
"""

import argparse
import json
import os
import sys
from collections import Counter
from pathlib import Path

GOLDEN_TEST_FILE = Path(__file__).parent / "golden_test_cases.json"


def load_tests():
    """Load golden test cases."""
    with open(GOLDEN_TEST_FILE, 'r', encoding='utf-8') as f:
        return json.load(f)


def validate_tests(data: dict) -> bool:
    """Validate test file format and content."""
    print("=" * 60)
    print("GOLDEN TEST VALIDATION")
    print("=" * 60)

    errors = []
    warnings = []

    # Check required fields
    if "test_cases" not in data:
        errors.append("Missing 'test_cases' array")
        return False

    test_cases = data["test_cases"]
    ids_seen = set()

    for i, tc in enumerate(test_cases):
        tc_id = tc.get("id", f"test_{i}")

        # Required fields
        if "id" not in tc:
            errors.append(f"Test {i}: missing 'id' field")
        elif tc["id"] in ids_seen:
            errors.append(f"Test {i}: duplicate id '{tc['id']}'")
        else:
            ids_seen.add(tc["id"])

        if "input" not in tc:
            errors.append(f"Test {tc_id}: missing 'input' field")

        if "expected_domain" not in tc:
            errors.append(f"Test {tc_id}: missing 'expected_domain' field")

        if "category" not in tc:
            warnings.append(f"Test {tc_id}: missing 'category' field")

        # Validate expected_intents is a list
        if "expected_intents" in tc and not isinstance(tc["expected_intents"], list):
            errors.append(f"Test {tc_id}: 'expected_intents' must be an array")

        # Validate min_confidence is a number
        if "min_confidence" in tc:
            conf = tc["min_confidence"]
            if not isinstance(conf, (int, float)) or conf < 0 or conf > 1:
                errors.append(f"Test {tc_id}: 'min_confidence' must be 0.0-1.0")

    # Print results
    print(f"\nTests validated: {len(test_cases)}")
    print(f"Errors: {len(errors)}")
    print(f"Warnings: {len(warnings)}")

    if errors:
        print("\n### ERRORS ###")
        for err in errors:
            print(f"  - {err}")

    if warnings:
        print("\n### WARNINGS ###")
        for warn in warnings:
            print(f"  - {warn}")

    print("=" * 60)
    if errors:
        print("RESULT: FAILED")
        return False
    else:
        print("RESULT: PASSED")
        return True


def show_stats(data: dict):
    """Show test statistics."""
    print("=" * 60)
    print("GOLDEN TEST STATISTICS")
    print("=" * 60)

    test_cases = data["test_cases"]

    # Count by category
    categories = Counter(tc.get("category", "unknown") for tc in test_cases)
    print("\nBy Category:")
    for cat, count in sorted(categories.items()):
        print(f"  {cat}: {count}")

    # Count by expected domain
    domains = Counter(tc.get("expected_domain", "unknown") for tc in test_cases)
    print(f"\nBy Expected Domain ({len(domains)} unique):")
    for dom, count in sorted(domains.items(), key=lambda x: -x[1])[:15]:
        print(f"  {dom}: {count}")
    if len(domains) > 15:
        print(f"  ... and {len(domains) - 15} more")

    # Count by expected intent (flattened)
    intents = Counter()
    for tc in test_cases:
        for intent in tc.get("expected_intents", []):
            intents[intent] += 1
    print(f"\nBy Expected Intent ({len(intents)} unique):")
    for intent, count in sorted(intents.items(), key=lambda x: -x[1])[:15]:
        print(f"  {intent}: {count}")
    if len(intents) > 15:
        print(f"  ... and {len(intents) - 15} more")

    print(f"\nTotal test cases: {len(test_cases)}")
    print("=" * 60)


def generate_kotlin(data: dict):
    """Generate Kotlin test code."""
    print("""
// Generated Kotlin test code for SemanticGenius golden tests
// Place in: app/src/androidTest/java/com/boox/day/ocr/semantic/GoldenClassificationTest.kt

package com.boox.day.ocr.semantic

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.boox.day.ocr.model.Domain
import com.boox.day.ocr.model.Intent
import com.boox.day.ocr.semantic.classification.DomainIntentClassifier
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoldenClassificationTest {

    private lateinit var classifier: DomainIntentClassifier

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        classifier = DomainIntentClassifier(context)
    }
""")

    for tc in data["test_cases"]:
        test_name = tc["id"].replace("-", "_").lower()
        input_text = tc["input"].replace('"', '\\"').replace("\n", "\\n")
        expected_domain = tc["expected_domain"]
        expected_intents = tc.get("expected_intents", [])
        min_confidence = tc.get("min_confidence", 0.5)
        notes = tc.get("notes", "")
        not_expected = tc.get("not_expected_domain", "")

        print(f"""
    /**
     * {notes}
     */
    @Test
    fun test_{test_name}() {{
        TestUtils.assertEventClassification(
            classifier = classifier,
            eventTitle = "{input_text}",
            eventDescription = null,
            expectedDomain = Domain.{expected_domain},
            expectedIntents = listOf({', '.join(f'Intent.{i}' for i in expected_intents)})
        )
    }}
""")

    print("}")


def main():
    parser = argparse.ArgumentParser(description="Golden test runner")
    parser.add_argument("--validate", action="store_true", help="Validate test file format")
    parser.add_argument("--stats", action="store_true", help="Show test statistics")
    parser.add_argument("--generate-kotlin", action="store_true", help="Generate Kotlin test code")

    args = parser.parse_args()

    if not GOLDEN_TEST_FILE.exists():
        print(f"ERROR: Golden test file not found: {GOLDEN_TEST_FILE}")
        sys.exit(1)

    data = load_tests()

    if args.validate:
        success = validate_tests(data)
        sys.exit(0 if success else 1)
    elif args.stats:
        show_stats(data)
    elif args.generate_kotlin:
        generate_kotlin(data)
    else:
        # Default: validate and show stats
        validate_tests(data)
        print()
        show_stats(data)


if __name__ == "__main__":
    main()
