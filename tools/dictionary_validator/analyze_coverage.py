#!/usr/bin/env python3
"""
Analyze dictionary coverage and compare to industry standards.
"""

import json
import os
import statistics
from pathlib import Path
from collections import defaultdict

def analyze_file(file_path: str) -> dict:
    """Analyze a single dictionary file."""
    with open(file_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    stats = {
        "name": os.path.basename(file_path),
        "tierA_count": 0,
        "tierB_count": 0,
        "tierC_count": 0,
        "total_terms": 0,
        "pattern_count": 0,
        "has_patterns": False
    }

    for tier in ["tierA", "tierB", "tierC"]:
        if tier in data and isinstance(data[tier], dict):
            terms = data[tier].get("terms", [])
            count = len(terms) if isinstance(terms, list) else 0
            stats[f"{tier}_count"] = count
            stats["total_terms"] += count

    patterns = data.get("phrasePatterns", [])
    stats["pattern_count"] = len(patterns) if isinstance(patterns, list) else 0
    stats["has_patterns"] = stats["pattern_count"] > 0

    return stats

def analyze_directory(base_path: str):
    """Analyze all dictionaries."""
    results = {
        "domains": [],
        "intents": []
    }

    # Analyze domains
    domains_path = os.path.join(base_path, "domains")
    if os.path.isdir(domains_path):
        for f in sorted(os.listdir(domains_path)):
            if f.endswith(".json"):
                stats = analyze_file(os.path.join(domains_path, f))
                results["domains"].append(stats)

    # Analyze intents
    intents_path = os.path.join(base_path, "intents")
    if os.path.isdir(intents_path):
        for f in sorted(os.listdir(intents_path)):
            if f.endswith(".json"):
                stats = analyze_file(os.path.join(intents_path, f))
                results["intents"].append(stats)

    return results

def print_report(results: dict):
    """Print coverage analysis report."""
    print("=" * 80)
    print("DICTIONARY COVERAGE ANALYSIS")
    print("=" * 80)

    for category in ["domains", "intents"]:
        files = results[category]
        if not files:
            continue

        print(f"\n### {category.upper()} ({len(files)} files) ###\n")

        # Calculate statistics
        tier_a = [f["tierA_count"] for f in files]
        tier_b = [f["tierB_count"] for f in files]
        tier_c = [f["tierC_count"] for f in files]
        totals = [f["total_terms"] for f in files]
        patterns = [f["pattern_count"] for f in files]

        print("Per-Tier Statistics:")
        print(f"  TierA: min={min(tier_a)}, max={max(tier_a)}, avg={statistics.mean(tier_a):.1f}, median={statistics.median(tier_a):.1f}")
        print(f"  TierB: min={min(tier_b)}, max={max(tier_b)}, avg={statistics.mean(tier_b):.1f}, median={statistics.median(tier_b):.1f}")
        print(f"  TierC: min={min(tier_c)}, max={max(tier_c)}, avg={statistics.mean(tier_c):.1f}, median={statistics.median(tier_c):.1f}")
        print(f"  Total: min={min(totals)}, max={max(totals)}, avg={statistics.mean(totals):.1f}, median={statistics.median(totals):.1f}")
        print(f"  Patterns: min={min(patterns)}, max={max(patterns)}, avg={statistics.mean(patterns):.1f}")

        # Distribution
        print(f"\nTotal Terms Distribution:")
        brackets = [(0, 10), (11, 20), (21, 30), (31, 50), (51, 100), (101, 999)]
        for low, high in brackets:
            count = sum(1 for t in totals if low <= t <= high)
            if count > 0:
                print(f"  {low:3d}-{high:3d} terms: {count} files ({count*100/len(files):.0f}%)")

        # Find underdeveloped files
        print(f"\nUnderdeveloped Files (< 15 total terms):")
        underdeveloped = [f for f in files if f["total_terms"] < 15]
        if underdeveloped:
            for f in sorted(underdeveloped, key=lambda x: x["total_terms"]):
                print(f"  {f['name']}: {f['total_terms']} terms ({f['tierA_count']}/{f['tierB_count']}/{f['tierC_count']})")
        else:
            print("  None")

        # Find files without patterns (for intents)
        if category == "intents":
            no_patterns = [f for f in files if f["pattern_count"] == 0]
            print(f"\nFiles Without Phrase Patterns: {len(no_patterns)}")
            if no_patterns:
                for f in no_patterns[:10]:
                    print(f"  {f['name']}")
                if len(no_patterns) > 10:
                    print(f"  ... and {len(no_patterns) - 10} more")

        # Top files by coverage
        print(f"\nTop 5 Most Comprehensive:")
        for f in sorted(files, key=lambda x: x["total_terms"], reverse=True)[:5]:
            print(f"  {f['name']}: {f['total_terms']} terms, {f['pattern_count']} patterns")

    # Summary
    all_files = results["domains"] + results["intents"]
    total_terms = sum(f["total_terms"] for f in all_files)
    total_patterns = sum(f["pattern_count"] for f in all_files)

    print("\n" + "=" * 80)
    print("SUMMARY")
    print("=" * 80)
    print(f"Total dictionary files: {len(all_files)}")
    print(f"Total unique terms: {total_terms}")
    print(f"Total phrase patterns: {total_patterns}")
    print(f"Average terms per file: {total_terms / len(all_files):.1f}")
    print(f"Average patterns per file: {total_patterns / len(all_files):.1f}")

    # Industry comparison
    print("\n" + "=" * 80)
    print("INDUSTRY COMPARISON")
    print("=" * 80)
    print("""
Industry standards for rule-based text classification vary widely:

| System Type | Terms/Category | Patterns | Notes |
|-------------|----------------|----------|-------|
| Minimal     | 5-15          | 0-2      | Proof of concept |
| Basic       | 15-30         | 2-5      | MVP quality |
| Standard    | 30-50         | 5-10     | Production ready |
| Comprehensive| 50-100+      | 10-20    | Enterprise grade |
| Research    | 100-500+      | 20-50    | Academic NLP |

Commercial systems (Google, Microsoft):
- Often use ML models trained on millions of examples
- Rule-based systems typically have 50-200 terms per category
- Phrase patterns: 10-30 per category for precision

Your current coverage:
""")

    domain_avg = statistics.mean([f["total_terms"] for f in results["domains"]])
    intent_avg = statistics.mean([f["total_terms"] for f in results["intents"]])
    pattern_avg = statistics.mean([f["pattern_count"] for f in all_files])

    if domain_avg < 20:
        domain_rating = "Basic (needs expansion)"
    elif domain_avg < 35:
        domain_rating = "Standard (good)"
    elif domain_avg < 60:
        domain_rating = "Comprehensive (excellent)"
    else:
        domain_rating = "Research-grade"

    if intent_avg < 15:
        intent_rating = "Basic (needs expansion)"
    elif intent_avg < 25:
        intent_rating = "Standard (good)"
    elif intent_avg < 40:
        intent_rating = "Comprehensive (excellent)"
    else:
        intent_rating = "Research-grade"

    if pattern_avg < 2:
        pattern_rating = "Minimal (needs more)"
    elif pattern_avg < 4:
        pattern_rating = "Basic (acceptable)"
    elif pattern_avg < 8:
        pattern_rating = "Standard (good)"
    else:
        pattern_rating = "Comprehensive (excellent)"

    print(f"  Domains avg: {domain_avg:.1f} terms -> {domain_rating}")
    print(f"  Intents avg: {intent_avg:.1f} terms -> {intent_rating}")
    print(f"  Patterns avg: {pattern_avg:.1f} -> {pattern_rating}")

    # Recommendations
    print("\n" + "=" * 80)
    print("RECOMMENDATIONS")
    print("=" * 80)

    # Find files that need work
    needs_terms = [f for f in all_files if f["total_terms"] < 20]
    needs_patterns = [f for f in results["intents"] if f["pattern_count"] < 4]

    if needs_terms:
        print(f"\n1. Expand terms ({len(needs_terms)} files < 20 terms):")
        for f in sorted(needs_terms, key=lambda x: x["total_terms"])[:10]:
            deficit = 20 - f["total_terms"]
            print(f"   {f['name']}: add ~{deficit} more terms")

    if needs_patterns:
        print(f"\n2. Add phrase patterns ({len(needs_patterns)} intents < 4 patterns):")
        for f in sorted(needs_patterns, key=lambda x: x["pattern_count"])[:10]:
            print(f"   {f['name']}: {f['pattern_count']} patterns -> add {4 - f['pattern_count']} more")

    print("\n3. Priority improvements:")
    print("   - TierA should have 5-10 highly specific terms")
    print("   - TierB should have 10-15 strong signal terms")
    print("   - TierC should have 10-20 supporting terms")
    print("   - Each intent needs 4-6 phrase patterns for precision")


if __name__ == "__main__":
    base_path = "C:/Users/fort/Projects/Boox_Day/app/src/main/assets/classification/en"
    results = analyze_directory(base_path)
    print_report(results)
