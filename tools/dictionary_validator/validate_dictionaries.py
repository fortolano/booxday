#!/usr/bin/env python3
"""
Dictionary Validator for SemanticGenius Classification System

Validates JSON dictionary files for domains and intents to ensure:
1. No intra-file duplicates (same term in multiple tiers)
2. No generic/blocklisted terms
3. Proper schema (required fields)
4. Weight consistency (tierA > tierB > tierC)
5. Phrase patterns present (for intents)
6. Cross-file tierA overlap detection
7. TierC minimum length (4+ chars)

Exit codes:
  0 = All validations pass
  1 = Warnings only (non-blocking)
  2 = Errors found (build should fail)

Usage:
  python validate_dictionaries.py --dictionaries path/to/classification/en
  python validate_dictionaries.py --dictionaries path/to/classification/en --fail-on-error
"""

import argparse
import json
import os
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple

# ============================================================================
# CONFIGURATION
# ============================================================================

# Conservative blocklist - only truly universal terms that cause false positives everywhere
# Domain-specific terms like "shop" in SHOPPING or "page" in READING are OK
# Use phrase patterns to match generic words in specific contexts instead
BLOCKLIST = {
    # Basic verbs/action words (too generic as standalone in ANY domain)
    "need", "want", "get", "make", "do", "go", "see", "think", "feel", "know",
    "have", "take", "give", "use", "find",
    # Question words (too generic)
    "when", "what", "how", "where", "why", "which", "who",
    # Generic structural nouns
    "list", "item", "note", "thing", "stuff",
    # Generic adjectives (appear in any context - use phrase patterns instead)
    "nice", "cool", "awesome", "like", "great"
}

# Short terms allowed in tierC (abbreviations, acronyms, domain-specific terms)
ALLOWED_SHORT_TERMS = {
    # Tech/Business abbreviations
    "hr", "ai", "ml", "it", "ux", "ui", "qa", "pm", "vp", "ceo", "cfo", "cto",
    "api", "sql", "csv", "pdf", "ocr", "lds", "pta", "k12", "rn", "md", "pa",
    "dj", "mc", "tv", "pc", "ac", "dc", "hq", "gm", "am", "pm", "etc",
    "app", "fix", "bug", "log", "run", "git", "ssh", "tcp", "udp", "url",
    "xml", "css", "dom", "ios", "aws", "gcp", "sdk", "cta",
    # Domain-specific short terms (valid in context)
    "vet", "pet", "car", "gas", "job", "pay", "due", "map", "lab", "god",
    "aim", "bad", "buy", "eat", "fun", "mix", "add", "try", "say", "way"
}

# Minimum term length for tierC (except allowed abbreviations)
TIER_C_MIN_LENGTH = 4

# Enterprise-grade minimum coverage thresholds
DOMAIN_MIN_TERMS = 80        # Minimum total terms per domain
INTENT_MIN_TERMS = 40        # Minimum total terms per intent
INTENT_MIN_PATTERNS = 10     # Minimum phrase patterns per intent
DOMAIN_MIN_PATTERNS = 10     # Minimum phrase patterns per domain

# Coverage enforcement mode (set via --enforce-coverage flag)
ENFORCE_COVERAGE = False


# ============================================================================
# DATA CLASSES
# ============================================================================

@dataclass
class ValidationIssue:
    """Represents a validation issue found in a dictionary file."""
    code: str           # E001, W001, etc.
    severity: str       # ERROR or WARNING
    file: str           # Relative file path
    message: str        # Human-readable description
    term: Optional[str] = None  # The problematic term (if applicable)
    fix: Optional[str] = None   # Suggested fix


@dataclass
class ValidationResult:
    """Aggregated validation results."""
    errors: List[ValidationIssue] = field(default_factory=list)
    warnings: List[ValidationIssue] = field(default_factory=list)
    files_checked: int = 0
    domains_checked: int = 0
    intents_checked: int = 0


# ============================================================================
# VALIDATION RULES
# ============================================================================

def check_schema(data: dict, file_path: str, is_domain: bool) -> List[ValidationIssue]:
    """
    Rule: Schema validation
    Severity: ERROR

    Required fields:
    - For domains: domain, tierA/B/C with weight and terms
    - For intents: intent, tierA/B/C with weight and terms
    """
    issues = []
    rel_path = os.path.basename(file_path)

    # Check for domain or intent field
    key_field = "domain" if is_domain else "intent"
    if key_field not in data:
        issues.append(ValidationIssue(
            code="E001",
            severity="ERROR",
            file=rel_path,
            message=f"Missing required field: {key_field}",
            fix=f"Add '{key_field}' field to the JSON"
        ))

    # Check tier structure
    for tier in ["tierA", "tierB", "tierC"]:
        if tier not in data:
            issues.append(ValidationIssue(
                code="E002",
                severity="ERROR",
                file=rel_path,
                message=f"Missing required tier: {tier}",
                fix=f"Add '{tier}' with 'weight' and 'terms' fields"
            ))
            continue

        tier_data = data[tier]
        if not isinstance(tier_data, dict):
            issues.append(ValidationIssue(
                code="E003",
                severity="ERROR",
                file=rel_path,
                message=f"{tier} must be an object with 'weight' and 'terms'",
                fix=f"Change {tier} to: {{ \"weight\": X.X, \"terms\": [...] }}"
            ))
            continue

        if "weight" not in tier_data:
            issues.append(ValidationIssue(
                code="E004",
                severity="ERROR",
                file=rel_path,
                message=f"{tier} missing 'weight' field",
                fix=f"Add 'weight' field to {tier}"
            ))

        if "terms" not in tier_data:
            issues.append(ValidationIssue(
                code="E005",
                severity="ERROR",
                file=rel_path,
                message=f"{tier} missing 'terms' field",
                fix=f"Add 'terms' array to {tier}"
            ))
        elif not isinstance(tier_data.get("terms"), list):
            issues.append(ValidationIssue(
                code="E006",
                severity="ERROR",
                file=rel_path,
                message=f"{tier}.terms must be an array",
                fix=f"Change {tier}.terms to an array of strings"
            ))

    return issues


def check_intra_file_duplicates(data: dict, file_path: str) -> List[ValidationIssue]:
    """
    Rule: No intra-file duplicates
    Severity: ERROR

    Same term cannot appear in multiple tiers within one file.
    """
    issues = []
    rel_path = os.path.basename(file_path)

    # Collect terms from each tier
    tier_terms: Dict[str, Set[str]] = {}
    for tier in ["tierA", "tierB", "tierC"]:
        if tier in data and isinstance(data[tier], dict):
            terms = data[tier].get("terms", [])
            if isinstance(terms, list):
                tier_terms[tier] = {t.lower() for t in terms if isinstance(t, str)}
            else:
                tier_terms[tier] = set()
        else:
            tier_terms[tier] = set()

    # Check for duplicates across tiers
    tiers = ["tierA", "tierB", "tierC"]
    for i, tier1 in enumerate(tiers):
        for tier2 in tiers[i+1:]:
            duplicates = tier_terms[tier1] & tier_terms[tier2]
            for term in duplicates:
                issues.append(ValidationIssue(
                    code="E010",
                    severity="ERROR",
                    file=rel_path,
                    message=f"Term '{term}' appears in both {tier1} AND {tier2}",
                    term=term,
                    fix=f"Remove '{term}' from {tier2} (keep in higher tier)"
                ))

    return issues


def check_blocklisted_terms(data: dict, file_path: str) -> List[ValidationIssue]:
    """
    Rule: No generic/blocklisted terms
    Severity: ERROR

    Generic terms cause false positives across many domains.
    """
    issues = []
    rel_path = os.path.basename(file_path)

    for tier in ["tierA", "tierB", "tierC"]:
        if tier in data and isinstance(data[tier], dict):
            terms = data[tier].get("terms", [])
            if isinstance(terms, list):
                for term in terms:
                    if isinstance(term, str) and term.lower() in BLOCKLIST:
                        issues.append(ValidationIssue(
                            code="E011",
                            severity="ERROR",
                            file=rel_path,
                            message=f"Blocklisted term '{term}' in {tier}",
                            term=term,
                            fix=f"Remove '{term}' from {tier} - too generic, causes false positives"
                        ))

    return issues


def check_tier_c_length(data: dict, file_path: str) -> List[ValidationIssue]:
    """
    Rule: TierC minimum length
    Severity: ERROR

    TierC terms should be 4+ characters (except allowed abbreviations).
    """
    issues = []
    rel_path = os.path.basename(file_path)

    if "tierC" in data and isinstance(data["tierC"], dict):
        terms = data["tierC"].get("terms", [])
        if isinstance(terms, list):
            for term in terms:
                if isinstance(term, str):
                    # Skip allowed short terms
                    if term.lower() in ALLOWED_SHORT_TERMS:
                        continue
                    if len(term) < TIER_C_MIN_LENGTH:
                        issues.append(ValidationIssue(
                            code="E012",
                            severity="ERROR",
                            file=rel_path,
                            message=f"TierC term '{term}' is too short ({len(term)} chars, min {TIER_C_MIN_LENGTH})",
                            term=term,
                            fix=f"Remove '{term}' from tierC or move to appropriate higher tier if specific enough"
                        ))

    return issues


def check_weight_consistency(data: dict, file_path: str) -> List[ValidationIssue]:
    """
    Rule: Weight consistency
    Severity: WARNING

    tierA weight > tierB weight > tierC weight
    """
    issues = []
    rel_path = os.path.basename(file_path)

    weights = {}
    for tier in ["tierA", "tierB", "tierC"]:
        if tier in data and isinstance(data[tier], dict):
            weight = data[tier].get("weight")
            if isinstance(weight, (int, float)):
                weights[tier] = weight

    if len(weights) == 3:
        if weights["tierA"] <= weights["tierB"]:
            issues.append(ValidationIssue(
                code="W001",
                severity="WARNING",
                file=rel_path,
                message=f"tierA weight ({weights['tierA']}) should be > tierB weight ({weights['tierB']})",
                fix="Adjust weights so tierA > tierB > tierC"
            ))
        if weights["tierB"] <= weights["tierC"]:
            issues.append(ValidationIssue(
                code="W002",
                severity="WARNING",
                file=rel_path,
                message=f"tierB weight ({weights['tierB']}) should be > tierC weight ({weights['tierC']})",
                fix="Adjust weights so tierA > tierB > tierC"
            ))

    return issues


def check_phrase_patterns(data: dict, file_path: str, is_intent: bool) -> List[ValidationIssue]:
    """
    Rule: Phrase patterns present (for intents)
    Severity: WARNING

    Intent files should have 2+ phrase patterns for better matching.
    """
    issues = []
    if not is_intent:
        return issues

    rel_path = os.path.basename(file_path)

    patterns = data.get("phrasePatterns", [])
    if not isinstance(patterns, list):
        patterns = []

    if len(patterns) < 2:
        issues.append(ValidationIssue(
            code="W003",
            severity="WARNING",
            file=rel_path,
            message=f"Intent has only {len(patterns)} phrase patterns (recommend 2+)",
            fix="Add more phrasePatterns for better matching accuracy"
        ))

    return issues


def check_coverage_thresholds(data: dict, file_path: str, is_domain: bool) -> List[ValidationIssue]:
    """
    Rule: Enterprise-grade coverage thresholds
    Severity: WARNING (or ERROR if ENFORCE_COVERAGE is True)

    Minimum requirements for enterprise-grade classification:
    - Domains: 80+ total terms, 10+ patterns
    - Intents: 40+ total terms, 10+ patterns
    """
    issues = []
    rel_path = os.path.basename(file_path)

    # Count total terms
    total_terms = 0
    for tier in ["tierA", "tierB", "tierC"]:
        if tier in data and isinstance(data[tier], dict):
            terms = data[tier].get("terms", [])
            if isinstance(terms, list):
                total_terms += len(terms)

    # Count patterns
    patterns = data.get("phrasePatterns", [])
    pattern_count = len(patterns) if isinstance(patterns, list) else 0

    # Set thresholds based on file type
    min_terms = DOMAIN_MIN_TERMS if is_domain else INTENT_MIN_TERMS
    min_patterns = DOMAIN_MIN_PATTERNS if is_domain else INTENT_MIN_PATTERNS
    file_type = "Domain" if is_domain else "Intent"

    severity = "ERROR" if ENFORCE_COVERAGE else "WARNING"
    code_prefix = "E" if ENFORCE_COVERAGE else "W"

    # Check term count
    if total_terms < min_terms:
        gap = min_terms - total_terms
        issues.append(ValidationIssue(
            code=f"{code_prefix}020",
            severity=severity,
            file=rel_path,
            message=f"{file_type} has only {total_terms} terms (minimum: {min_terms}, gap: {gap})",
            fix=f"Add {gap} more terms using: python generate_terms.py --{'domain' if is_domain else 'intent'} {rel_path.replace('.json', '')}"
        ))

    # Check pattern count
    if pattern_count < min_patterns:
        gap = min_patterns - pattern_count
        issues.append(ValidationIssue(
            code=f"{code_prefix}021",
            severity=severity,
            file=rel_path,
            message=f"{file_type} has only {pattern_count} phrase patterns (minimum: {min_patterns}, gap: {gap})",
            fix=f"Add {gap} more phrase patterns for better matching precision"
        ))

    return issues


def check_cross_file_tier_a_overlap(
    all_data: Dict[str, Tuple[dict, str]],  # {file_path: (data, type)}
    domain_only: bool = True
) -> List[ValidationIssue]:
    """
    Rule: Cross-file tierA overlap
    Severity: WARNING

    Same term in tierA of multiple domains indicates ambiguity.
    """
    issues = []

    # Collect tierA terms per file
    term_to_files: Dict[str, List[str]] = defaultdict(list)

    for file_path, (data, file_type) in all_data.items():
        if domain_only and file_type != "domain":
            continue

        if "tierA" in data and isinstance(data["tierA"], dict):
            terms = data["tierA"].get("terms", [])
            if isinstance(terms, list):
                for term in terms:
                    if isinstance(term, str):
                        term_to_files[term.lower()].append(os.path.basename(file_path))

    # Find terms in multiple files
    for term, files in term_to_files.items():
        if len(files) > 1:
            issues.append(ValidationIssue(
                code="W004",
                severity="WARNING",
                file=", ".join(files),
                message=f"TierA term '{term}' appears in multiple domains: {', '.join(files)}",
                term=term,
                fix="Consider moving to collocations.json for context-aware disambiguation"
            ))

    return issues


# ============================================================================
# MAIN VALIDATION LOGIC
# ============================================================================

def validate_file(file_path: str, is_domain: bool) -> Tuple[dict, List[ValidationIssue]]:
    """Validate a single dictionary file."""
    issues = []

    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
    except json.JSONDecodeError as e:
        return {}, [ValidationIssue(
            code="E000",
            severity="ERROR",
            file=os.path.basename(file_path),
            message=f"Invalid JSON: {e}",
            fix="Fix JSON syntax errors"
        )]
    except Exception as e:
        return {}, [ValidationIssue(
            code="E000",
            severity="ERROR",
            file=os.path.basename(file_path),
            message=f"Cannot read file: {e}",
            fix="Check file permissions and encoding"
        )]

    # Run all validation rules
    issues.extend(check_schema(data, file_path, is_domain))
    issues.extend(check_intra_file_duplicates(data, file_path))
    issues.extend(check_blocklisted_terms(data, file_path))
    issues.extend(check_tier_c_length(data, file_path))
    issues.extend(check_weight_consistency(data, file_path))
    issues.extend(check_phrase_patterns(data, file_path, is_intent=not is_domain))
    issues.extend(check_coverage_thresholds(data, file_path, is_domain))

    return data, issues


def validate_directory(base_path: str) -> ValidationResult:
    """Validate all dictionary files in the classification directory."""
    result = ValidationResult()
    all_data: Dict[str, Tuple[dict, str]] = {}

    # Validate domains
    domains_path = os.path.join(base_path, "domains")
    if os.path.isdir(domains_path):
        for filename in os.listdir(domains_path):
            if filename.endswith(".json"):
                file_path = os.path.join(domains_path, filename)
                data, issues = validate_file(file_path, is_domain=True)

                result.files_checked += 1
                result.domains_checked += 1
                all_data[file_path] = (data, "domain")

                for issue in issues:
                    issue.file = f"domains/{filename}"
                    if issue.severity == "ERROR":
                        result.errors.append(issue)
                    else:
                        result.warnings.append(issue)

    # Validate intents
    intents_path = os.path.join(base_path, "intents")
    if os.path.isdir(intents_path):
        for filename in os.listdir(intents_path):
            if filename.endswith(".json"):
                file_path = os.path.join(intents_path, filename)
                data, issues = validate_file(file_path, is_domain=False)

                result.files_checked += 1
                result.intents_checked += 1
                all_data[file_path] = (data, "intent")

                for issue in issues:
                    issue.file = f"intents/{filename}"
                    if issue.severity == "ERROR":
                        result.errors.append(issue)
                    else:
                        result.warnings.append(issue)

    # Cross-file validation
    cross_file_issues = check_cross_file_tier_a_overlap(all_data)
    for issue in cross_file_issues:
        result.warnings.append(issue)

    return result


def print_report(result: ValidationResult, verbose: bool = False) -> None:
    """Print validation report to stdout."""
    print("=" * 60)
    print("DICTIONARY VALIDATION REPORT")
    print("=" * 60)
    print(f"Files checked:   {result.files_checked}")
    print(f"  Domains:       {result.domains_checked}")
    print(f"  Intents:       {result.intents_checked}")
    print()
    print(f"Errors:          {len(result.errors)} (MUST fix)")
    print(f"Warnings:        {len(result.warnings)} (SHOULD fix)")
    print("=" * 60)

    if result.errors:
        print("\n### ERRORS ###\n")
        for issue in result.errors:
            print(f"[{issue.code}] {issue.severity}")
            print(f"  File: {issue.file}")
            print(f"  {issue.message}")
            if issue.fix:
                print(f"  Fix: {issue.fix}")
            print()

    if result.warnings and verbose:
        print("\n### WARNINGS ###\n")
        for issue in result.warnings:
            print(f"[{issue.code}] {issue.severity}")
            print(f"  File: {issue.file}")
            print(f"  {issue.message}")
            if issue.fix:
                print(f"  Fix: {issue.fix}")
            print()
    elif result.warnings:
        print(f"\n(Use --verbose to see {len(result.warnings)} warnings)")

    print("\n" + "=" * 60)
    if result.errors:
        print("RESULT: FAILED - Dictionary validation errors found")
    elif result.warnings:
        print("RESULT: PASSED WITH WARNINGS")
    else:
        print("RESULT: PASSED - All validations successful")
    print("=" * 60)


def main():
    parser = argparse.ArgumentParser(
        description="Validate SemanticGenius classification dictionaries"
    )
    parser.add_argument(
        "--dictionaries", "-d",
        required=True,
        help="Path to classification directory (e.g., app/src/main/assets/classification/en)"
    )
    parser.add_argument(
        "--fail-on-error",
        action="store_true",
        help="Exit with code 2 if errors found (for CI)"
    )
    parser.add_argument(
        "--fail-on-warning",
        action="store_true",
        help="Exit with code 1 if warnings found (strict mode)"
    )
    parser.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Show all warnings in output"
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Output results as JSON"
    )
    parser.add_argument(
        "--enforce-coverage",
        action="store_true",
        help="Treat coverage threshold violations as errors (for enterprise-grade enforcement)"
    )

    args = parser.parse_args()

    # Set global coverage enforcement mode
    global ENFORCE_COVERAGE
    if args.enforce_coverage:
        ENFORCE_COVERAGE = True

    if not os.path.isdir(args.dictionaries):
        print(f"ERROR: Directory not found: {args.dictionaries}")
        sys.exit(2)

    result = validate_directory(args.dictionaries)

    if args.json:
        output = {
            "files_checked": result.files_checked,
            "domains_checked": result.domains_checked,
            "intents_checked": result.intents_checked,
            "error_count": len(result.errors),
            "warning_count": len(result.warnings),
            "errors": [
                {"code": e.code, "file": e.file, "message": e.message, "term": e.term, "fix": e.fix}
                for e in result.errors
            ],
            "warnings": [
                {"code": w.code, "file": w.file, "message": w.message, "term": w.term, "fix": w.fix}
                for w in result.warnings
            ]
        }
        print(json.dumps(output, indent=2))
    else:
        print_report(result, verbose=args.verbose)

    # Determine exit code
    if result.errors and args.fail_on_error:
        sys.exit(2)
    elif result.warnings and args.fail_on_warning:
        sys.exit(1)
    else:
        sys.exit(0)


if __name__ == "__main__":
    main()
