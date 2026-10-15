#!/usr/bin/env python3
"""
Dictionary Fixer for SemanticGenius Classification System

Automatically fixes safe-to-automate issues:
1. Intra-file duplicates (keeps term in highest tier)
2. Blocklisted generic terms (removes from all tiers)
3. Too-short tierC terms (removes unless in allowed list)

Usage:
  python fix_duplicates.py --dictionaries path/to/classification/en --dry-run
  python fix_duplicates.py --dictionaries path/to/classification/en --apply
"""

import argparse
import json
import os
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

# Short terms allowed in tierC (abbreviations, acronyms)
ALLOWED_SHORT_TERMS = {
    "hr", "ai", "ml", "it", "ux", "ui", "qa", "pm", "vp", "ceo", "cfo", "cto",
    "api", "sql", "csv", "pdf", "ocr", "lds", "pta", "k12", "rn", "md", "pa",
    "dj", "mc", "tv", "pc", "ac", "dc", "hq", "gm", "am", "pm", "etc",
    # Common tech abbreviations
    "app", "fix", "bug", "log", "run", "git", "ssh", "tcp", "udp", "url",
    "xml", "css", "dom", "ios", "aws", "gcp", "sdk",
    # Common domain-specific short terms
    "vet", "pet", "car", "gas", "job", "pay", "due", "map", "lab", "god",
    "aim", "bad", "buy", "eat", "fun", "mix", "add", "try", "say", "way",
    "cta"
}

# Minimum term length for tierC (except allowed abbreviations)
TIER_C_MIN_LENGTH = 4


# ============================================================================
# DATA CLASSES
# ============================================================================

@dataclass
class FixAction:
    """Represents a fix action to be applied."""
    file: str
    action: str  # "remove_duplicate", "remove_blocklist", "remove_short"
    tier: str    # "tierA", "tierB", "tierC"
    term: str
    reason: str


@dataclass
class FixResult:
    """Aggregated fix results."""
    actions: List[FixAction] = field(default_factory=list)
    files_modified: Set[str] = field(default_factory=set)
    duplicates_fixed: int = 0
    blocklist_removed: int = 0
    short_terms_removed: int = 0


# ============================================================================
# FIX LOGIC
# ============================================================================

def analyze_file(file_path: str) -> Tuple[dict, List[FixAction]]:
    """Analyze a file and determine fixes needed."""
    actions = []
    rel_path = os.path.basename(file_path)

    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
    except Exception as e:
        print(f"ERROR: Cannot read {rel_path}: {e}")
        return {}, []

    # Collect terms from each tier (preserving case for removal)
    tier_terms: Dict[str, Dict[str, str]] = {}  # tier -> {lower: original}
    for tier in ["tierA", "tierB", "tierC"]:
        if tier in data and isinstance(data[tier], dict):
            terms = data[tier].get("terms", [])
            if isinstance(terms, list):
                tier_terms[tier] = {t.lower(): t for t in terms if isinstance(t, str)}
            else:
                tier_terms[tier] = {}
        else:
            tier_terms[tier] = {}

    # 1. Find intra-file duplicates (mark lower tiers for removal)
    tiers_priority = ["tierA", "tierB", "tierC"]
    seen_terms: Set[str] = set()

    for tier in tiers_priority:
        for term_lower, term_original in list(tier_terms[tier].items()):
            if term_lower in seen_terms:
                # This term exists in a higher tier - mark for removal
                actions.append(FixAction(
                    file=rel_path,
                    action="remove_duplicate",
                    tier=tier,
                    term=term_original,
                    reason=f"Duplicate of term in higher tier"
                ))
            else:
                seen_terms.add(term_lower)

    # 2. Find blocklisted terms
    for tier in tiers_priority:
        for term_lower, term_original in tier_terms[tier].items():
            if term_lower in BLOCKLIST:
                actions.append(FixAction(
                    file=rel_path,
                    action="remove_blocklist",
                    tier=tier,
                    term=term_original,
                    reason=f"Blocklisted generic term"
                ))

    # 3. Find too-short tierC terms (only for tierC)
    for term_lower, term_original in tier_terms["tierC"].items():
        if term_lower in ALLOWED_SHORT_TERMS:
            continue
        if len(term_original) < TIER_C_MIN_LENGTH:
            # Skip if already marked for removal
            already_marked = any(
                a.tier == "tierC" and a.term.lower() == term_lower
                for a in actions
            )
            if not already_marked:
                actions.append(FixAction(
                    file=rel_path,
                    action="remove_short",
                    tier="tierC",
                    term=term_original,
                    reason=f"Too short ({len(term_original)} chars, min {TIER_C_MIN_LENGTH})"
                ))

    return data, actions


def apply_fixes(data: dict, actions: List[FixAction]) -> dict:
    """Apply fix actions to the data."""
    # Group actions by tier
    removals_by_tier: Dict[str, Set[str]] = defaultdict(set)
    for action in actions:
        removals_by_tier[action.tier].add(action.term.lower())

    # Apply removals
    for tier in ["tierA", "tierB", "tierC"]:
        if tier in data and isinstance(data[tier], dict):
            terms = data[tier].get("terms", [])
            if isinstance(terms, list):
                # Filter out terms to remove
                new_terms = [
                    t for t in terms
                    if isinstance(t, str) and t.lower() not in removals_by_tier[tier]
                ]
                data[tier]["terms"] = new_terms

    return data


def save_file(file_path: str, data: dict) -> bool:
    """Save data to file with pretty formatting."""
    try:
        with open(file_path, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write('\n')  # Trailing newline
        return True
    except Exception as e:
        print(f"ERROR: Cannot write {file_path}: {e}")
        return False


# ============================================================================
# MAIN LOGIC
# ============================================================================

def process_directory(base_path: str, dry_run: bool = True) -> FixResult:
    """Process all dictionary files."""
    result = FixResult()

    # Process domains
    domains_path = os.path.join(base_path, "domains")
    if os.path.isdir(domains_path):
        for filename in sorted(os.listdir(domains_path)):
            if filename.endswith(".json"):
                file_path = os.path.join(domains_path, filename)
                data, actions = analyze_file(file_path)

                if actions:
                    result.files_modified.add(f"domains/{filename}")
                    result.actions.extend(actions)

                    # Count by type
                    for action in actions:
                        if action.action == "remove_duplicate":
                            result.duplicates_fixed += 1
                        elif action.action == "remove_blocklist":
                            result.blocklist_removed += 1
                        elif action.action == "remove_short":
                            result.short_terms_removed += 1

                    # Apply if not dry run
                    if not dry_run:
                        fixed_data = apply_fixes(data, actions)
                        save_file(file_path, fixed_data)

    # Process intents
    intents_path = os.path.join(base_path, "intents")
    if os.path.isdir(intents_path):
        for filename in sorted(os.listdir(intents_path)):
            if filename.endswith(".json"):
                file_path = os.path.join(intents_path, filename)
                data, actions = analyze_file(file_path)

                if actions:
                    result.files_modified.add(f"intents/{filename}")
                    result.actions.extend(actions)

                    # Count by type
                    for action in actions:
                        if action.action == "remove_duplicate":
                            result.duplicates_fixed += 1
                        elif action.action == "remove_blocklist":
                            result.blocklist_removed += 1
                        elif action.action == "remove_short":
                            result.short_terms_removed += 1

                    # Apply if not dry run
                    if not dry_run:
                        fixed_data = apply_fixes(data, actions)
                        save_file(file_path, fixed_data)

    return result


def print_report(result: FixResult, dry_run: bool) -> None:
    """Print fix report."""
    mode = "DRY RUN" if dry_run else "APPLIED"
    print("=" * 60)
    print(f"DICTIONARY FIX REPORT ({mode})")
    print("=" * 60)
    print(f"Files to modify:       {len(result.files_modified)}")
    print(f"Duplicates to fix:     {result.duplicates_fixed}")
    print(f"Blocklist to remove:   {result.blocklist_removed}")
    print(f"Short terms to remove: {result.short_terms_removed}")
    print(f"Total actions:         {len(result.actions)}")
    print("=" * 60)

    if result.actions:
        print("\n### ACTIONS ###\n")

        # Group by file
        by_file: Dict[str, List[FixAction]] = defaultdict(list)
        for action in result.actions:
            by_file[action.file].append(action)

        for file, actions in sorted(by_file.items()):
            print(f"File: {file}")
            for action in actions:
                action_type = {
                    "remove_duplicate": "DUP",
                    "remove_blocklist": "BLK",
                    "remove_short": "SHT"
                }.get(action.action, "???")
                print(f"  [{action_type}] {action.tier}: '{action.term}' - {action.reason}")
            print()

    print("=" * 60)
    if dry_run:
        print("This was a DRY RUN. Use --apply to make changes.")
    else:
        print(f"DONE: {len(result.actions)} fixes applied to {len(result.files_modified)} files.")
    print("=" * 60)


def main():
    parser = argparse.ArgumentParser(
        description="Fix SemanticGenius classification dictionary issues"
    )
    parser.add_argument(
        "--dictionaries", "-d",
        required=True,
        help="Path to classification directory (e.g., app/src/main/assets/classification/en)"
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Preview changes without applying them (default)"
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Actually apply the fixes"
    )

    args = parser.parse_args()

    if not os.path.isdir(args.dictionaries):
        print(f"ERROR: Directory not found: {args.dictionaries}")
        sys.exit(1)

    # Default to dry-run unless --apply is specified
    dry_run = not args.apply

    result = process_directory(args.dictionaries, dry_run=dry_run)
    print_report(result, dry_run=dry_run)


if __name__ == "__main__":
    main()
