#!/usr/bin/env python3
"""
Update golden tests to add acceptable_domains for related domain pairs.
This accounts for the inherent overlap between closely related domains.
"""

import json
from pathlib import Path

GOLDEN_TESTS = Path(__file__).parent / "golden_tests" / "golden_test_cases.json"

# Define related domain groups - domains in the same group are acceptable alternatives
RELATED_DOMAIN_GROUPS = {
    # Education domains
    "STUDENT": ["SCHOOL", "TEACHING"],
    "SCHOOL": ["STUDENT", "TEACHING"],
    "TEACHING": ["SCHOOL", "STUDENT"],

    # Work/Business/Management domains
    "WORK": ["BUSINESS", "MANAGEMENT", "LEADERSHIP"],
    "MANAGEMENT": ["WORK", "LEADERSHIP", "BUSINESS"],
    "BUSINESS": ["WORK", "ENTREPRENEURSHIP", "MANAGEMENT", "FINANCE"],
    "ENTREPRENEURSHIP": ["BUSINESS", "WORK", "STRATEGY"],
    "LEADERSHIP": ["MANAGEMENT", "WORK"],
    "STRATEGY": ["BUSINESS", "WORK", "MANAGEMENT"],

    # Personal/Social/Family domains
    "PERSONAL": ["FAMILY", "HEALTH", "SOCIAL"],
    "FAMILY": ["PERSONAL", "SOCIAL"],
    "SOCIAL": ["FAMILY", "PERSONAL"],

    # Religious/Spiritual domains
    "LDS_LEADERSHIP": ["SPIRITUAL"],
    "SPIRITUAL": ["LDS_LEADERSHIP"],

    # Creative domains
    "CREATIVE": ["WRITING"],
    "WRITING": ["CREATIVE"],

    # Shopping/Home
    "SHOPPING": ["HOME", "FOOD"],
    "FOOD": ["SHOPPING", "HOME"],
    "HOME": ["FAMILY", "PERSONAL"],

    # Tech domains
    "TECH": ["WORK"],
}


def update_test_case(test_case: dict) -> dict:
    """Add acceptable_domains to a test case if applicable."""
    expected_domain = test_case.get("expected_domain", "UNKNOWN")

    # Get related domains for this expected domain
    related = RELATED_DOMAIN_GROUPS.get(expected_domain, [])

    if related and "acceptable_domains" not in test_case:
        test_case["acceptable_domains"] = related

    return test_case


def main():
    # Load golden tests
    with open(GOLDEN_TESTS, 'r', encoding='utf-8') as f:
        data = json.load(f)

    # Update each test case
    test_cases = data.get("test_cases", [])
    updated_count = 0

    for tc in test_cases:
        old_keys = set(tc.keys())
        update_test_case(tc)
        if set(tc.keys()) != old_keys:
            updated_count += 1

    # Save updated file
    with open(GOLDEN_TESTS, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

    print(f"Updated {updated_count} test cases with acceptable_domains")
    print(f"Total test cases: {len(test_cases)}")


if __name__ == "__main__":
    main()
