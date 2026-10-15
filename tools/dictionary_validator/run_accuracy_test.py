#!/usr/bin/env python3
"""
Accuracy Benchmark System for SemanticGenius Classification

This tool tests classification accuracy against golden test cases by simulating
the DomainIntentClassifier behavior using the dictionary files.

Target Metrics:
- 95%+ domain accuracy
- 90%+ intent accuracy
- 99%+ on high-confidence results

Usage:
  python run_accuracy_test.py                    # Run full benchmark
  python run_accuracy_test.py --verbose          # Show all failures
  python run_accuracy_test.py --category domain  # Test only domains
  python run_accuracy_test.py --threshold 0.8    # Set confidence threshold
"""

import argparse
import json
import os
import re
import sys
from pathlib import Path
from typing import Dict, List, Set, Tuple, Optional
from collections import defaultdict
from dataclasses import dataclass

# Paths
DICT_BASE = Path("C:/Users/fort/Projects/Boox_Day/app/src/main/assets/classification/en")
GOLDEN_TESTS = Path(__file__).parent / "golden_tests" / "golden_test_cases.json"

# Tier weights (matching Kotlin DomainIntentClassifier)
TIER_WEIGHTS = {
    "tierA": 4.0,
    "tierB": 2.5,
    "tierC": 1.0
}

# Minimum score threshold for classification
# Lowered from 3.0 to 2.0 to allow TierB (2.5) and TierC×2 (2.0) matches
DEFAULT_MIN_SCORE = 2.0


@dataclass
class ClassificationResult:
    """Result of classifying a text."""
    domain: str
    domain_score: float
    domain_confidence: float
    intents: List[Tuple[str, float]]  # (intent_name, score)
    all_domain_scores: Dict[str, float]
    all_intent_scores: Dict[str, float]


@dataclass
class TestResult:
    """Result of a single test case."""
    test_id: str
    passed: bool
    expected_domain: str
    actual_domain: str
    domain_correct: bool
    expected_intents: List[str]
    actual_intents: List[str]
    intents_correct: bool
    domain_score: float
    domain_confidence: float
    notes: str


class DictionaryClassifier:
    """
    Python implementation of DomainIntentClassifier for testing.
    Mirrors the Kotlin implementation logic.
    """

    def __init__(self, dict_base: Path):
        self.domains: Dict[str, dict] = {}
        self.intents: Dict[str, dict] = {}
        self.abbreviations: Dict[str, str] = {}
        self.collocations: Dict[str, dict] = {}

        self._load_dictionaries(dict_base)

    def _load_dictionaries(self, base_path: Path):
        """Load all dictionary files."""
        # Load domains
        domains_path = base_path / "domains"
        if domains_path.is_dir():
            for f in domains_path.glob("*.json"):
                with open(f, 'r', encoding='utf-8') as file:
                    data = json.load(file)
                    name = data.get("domain", f.stem.upper())
                    self.domains[name] = data

        # Load intents
        intents_path = base_path / "intents"
        if intents_path.is_dir():
            for f in intents_path.glob("*.json"):
                with open(f, 'r', encoding='utf-8') as file:
                    data = json.load(file)
                    name = data.get("intent", f.stem.upper())
                    self.intents[name] = data

        # Load abbreviations (nested structure)
        abbrev_path = base_path / "abbreviations.json"
        if abbrev_path.exists():
            with open(abbrev_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
                # Handle nested structure: {"abbreviations": {...}}
                if "abbreviations" in data:
                    self.abbreviations = data["abbreviations"]
                else:
                    self.abbreviations = data

        # Load collocations (nested structure)
        colloc_path = base_path / "collocations.json"
        if colloc_path.exists():
            with open(colloc_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
                # Handle nested structure: {"collocations": {...}}
                if "collocations" in data:
                    self.collocations = data["collocations"]
                else:
                    self.collocations = data

    def _tokenize(self, text: str) -> List[str]:
        """Tokenize text into lowercase terms."""
        # Simple tokenization: split on non-word characters
        tokens = re.findall(r'\b\w+\b', text.lower())
        return tokens

    def _generate_ngram_tokens(self, tokens: List[str], max_n: int = 4) -> Set[str]:
        """
        Generate hyphenated n-gram tokens from word list.
        This mirrors the Kotlin normalizeToHyphenated() behavior.
        """
        ngram_set = set(tokens)  # Include original single tokens

        for n in range(2, max_n + 1):
            for i in range(len(tokens) - n + 1):
                ngram = "-".join(tokens[i:i + n])
                ngram_set.add(ngram)

        return ngram_set

    def _expand_abbreviations(self, tokens: List[str]) -> List[str]:
        """Expand known abbreviations."""
        expanded = []
        for token in tokens:
            if token in self.abbreviations:
                # Add both the abbreviation and its expansion
                expanded.append(token)
                expansion = self.abbreviations[token]
                expanded.extend(self._tokenize(expansion))
            else:
                expanded.append(token)
        return expanded

    def _score_dictionary(self, ngram_tokens: Set[str], original_text: str, data: dict) -> float:
        """Score tokens against a dictionary."""
        score = 0.0
        matched_tokens = set()

        # Score tier terms against n-gram set
        for tier, weight in TIER_WEIGHTS.items():
            if tier in data and isinstance(data[tier], dict):
                terms = data[tier].get("terms", [])
                if isinstance(terms, list):
                    for term in terms:
                        term_lower = term.lower()
                        if term_lower in ngram_tokens:
                            score += weight
                            matched_tokens.add(term_lower)

        # Score phrase patterns against original text
        patterns = data.get("phrasePatterns", [])
        if isinstance(patterns, list):
            for pattern_obj in patterns:
                if isinstance(pattern_obj, dict):
                    pattern = pattern_obj.get("pattern", "")
                    weight = pattern_obj.get("weight", 3.0)
                    try:
                        if re.search(pattern, original_text, re.IGNORECASE):
                            score += weight
                    except re.error:
                        pass  # Invalid regex, skip

        return score

    def classify(self, text: str, title: str = "") -> ClassificationResult:
        """Classify text and return results."""
        # Combine title and text
        full_text = f"{title} {text}".strip()

        # Tokenize and expand
        tokens = self._tokenize(full_text)
        tokens = self._expand_abbreviations(tokens)

        # Generate n-gram tokens for term matching
        ngram_tokens = self._generate_ngram_tokens(tokens)

        # Score all domains
        domain_scores: Dict[str, float] = {}
        for domain_name, domain_data in self.domains.items():
            score = self._score_dictionary(ngram_tokens, full_text, domain_data)
            domain_scores[domain_name] = score

        # Score all intents
        intent_scores: Dict[str, float] = {}
        for intent_name, intent_data in self.intents.items():
            score = self._score_dictionary(ngram_tokens, full_text, intent_data)
            intent_scores[intent_name] = score

        # Find best domain
        best_domain = "UNKNOWN"
        best_domain_score = 0.0
        for domain, score in domain_scores.items():
            if score > best_domain_score:
                best_domain = domain
                best_domain_score = score

        # Calculate domain confidence
        total_domain_score = sum(domain_scores.values())
        domain_confidence = (best_domain_score / total_domain_score) if total_domain_score > 0 else 0.0

        # Find top intents (above threshold)
        sorted_intents = sorted(intent_scores.items(), key=lambda x: -x[1])
        top_intents = [(name, score) for name, score in sorted_intents if score >= DEFAULT_MIN_SCORE][:3]

        return ClassificationResult(
            domain=best_domain if best_domain_score >= DEFAULT_MIN_SCORE else "UNKNOWN",
            domain_score=best_domain_score,
            domain_confidence=domain_confidence,
            intents=top_intents,
            all_domain_scores=domain_scores,
            all_intent_scores=intent_scores
        )


def load_golden_tests() -> dict:
    """Load golden test cases."""
    with open(GOLDEN_TESTS, 'r', encoding='utf-8') as f:
        return json.load(f)


def run_test_case(classifier: DictionaryClassifier, test_case: dict) -> TestResult:
    """Run a single test case and return result."""
    test_id = test_case.get("id", "unknown")
    input_text = test_case.get("input", "")
    expected_domain = test_case.get("expected_domain", "UNKNOWN")
    acceptable_domains = test_case.get("acceptable_domains", [])  # Alternative valid domains
    expected_intents = test_case.get("expected_intents", [])
    min_confidence = test_case.get("min_confidence", 0.5)
    notes = test_case.get("notes", "")

    # Classify
    result = classifier.classify(input_text)

    # Check domain - accept primary or any acceptable alternatives
    all_valid_domains = [expected_domain] + acceptable_domains

    # For edge cases with min_confidence <= 0.1, accept UNKNOWN as valid
    if min_confidence <= 0.1:
        all_valid_domains.append("UNKNOWN")

    domain_correct = result.domain in all_valid_domains

    # Check intents (at least one expected intent should be in top results)
    actual_intent_names = [name for name, _ in result.intents]
    intents_correct = True
    if expected_intents:
        intents_correct = any(ei in actual_intent_names for ei in expected_intents)

    # Overall pass
    passed = domain_correct and (intents_correct or not expected_intents)

    return TestResult(
        test_id=test_id,
        passed=passed,
        expected_domain=expected_domain,
        actual_domain=result.domain,
        domain_correct=domain_correct,
        expected_intents=expected_intents,
        actual_intents=actual_intent_names,
        intents_correct=intents_correct,
        domain_score=result.domain_score,
        domain_confidence=result.domain_confidence,
        notes=notes
    )


def run_benchmark(verbose: bool = False, category_filter: str = None) -> dict:
    """Run full benchmark and return results."""
    classifier = DictionaryClassifier(DICT_BASE)
    test_data = load_golden_tests()
    test_cases = test_data.get("test_cases", [])

    results = {
        "total": 0,
        "passed": 0,
        "failed": 0,
        "domain_correct": 0,
        "intent_correct": 0,
        "by_category": defaultdict(lambda: {"total": 0, "passed": 0}),
        "by_domain": defaultdict(lambda: {"total": 0, "correct": 0}),
        "failures": []
    }

    for tc in test_cases:
        # Filter by category if specified
        category = tc.get("category", "unknown")
        if category_filter and category_filter not in category:
            continue

        result = run_test_case(classifier, tc)
        results["total"] += 1
        results["by_category"][category]["total"] += 1
        results["by_domain"][result.expected_domain]["total"] += 1

        if result.domain_correct:
            results["domain_correct"] += 1
            results["by_domain"][result.expected_domain]["correct"] += 1

        if result.intents_correct:
            results["intent_correct"] += 1

        if result.passed:
            results["passed"] += 1
            results["by_category"][category]["passed"] += 1
        else:
            results["failed"] += 1
            results["failures"].append(result)

    return results


def print_benchmark_report(results: dict, verbose: bool = False):
    """Print benchmark results."""
    print("=" * 80)
    print("SEMANTICGENIUS ACCURACY BENCHMARK")
    print("=" * 80)

    total = results["total"]
    if total == 0:
        print("\nNo test cases found!")
        return

    # Overall metrics
    domain_accuracy = (results["domain_correct"] / total) * 100
    intent_accuracy = (results["intent_correct"] / total) * 100
    overall_accuracy = (results["passed"] / total) * 100

    print(f"\n### OVERALL RESULTS ###")
    print(f"  Total tests: {total}")
    print(f"  Passed: {results['passed']} ({overall_accuracy:.1f}%)")
    print(f"  Failed: {results['failed']}")
    print(f"\n### COMPONENT ACCURACY ###")
    print(f"  Domain accuracy: {results['domain_correct']}/{total} ({domain_accuracy:.1f}%)")
    print(f"  Intent accuracy: {results['intent_correct']}/{total} ({intent_accuracy:.1f}%)")

    # Target comparison
    print(f"\n### TARGET COMPARISON ###")
    targets = {"Domain": 95.0, "Intent": 90.0, "Overall": 95.0}

    print(f"  {'Metric':<15} {'Current':>10} {'Target':>10} {'Status':>10}")
    print(f"  {'-'*45}")

    for metric, target in targets.items():
        if metric == "Domain":
            current = domain_accuracy
        elif metric == "Intent":
            current = intent_accuracy
        else:
            current = overall_accuracy

        status = "PASS" if current >= target else "FAIL"
        gap = current - target
        gap_str = f"+{gap:.1f}" if gap >= 0 else f"{gap:.1f}"
        print(f"  {metric:<15} {current:>9.1f}% {target:>9.1f}% {status:>6} ({gap_str})")

    # By category
    print(f"\n### BY CATEGORY ###")
    for category, stats in sorted(results["by_category"].items()):
        cat_total = stats["total"]
        cat_passed = stats["passed"]
        cat_pct = (cat_passed / cat_total * 100) if cat_total > 0 else 0
        status = "OK" if cat_pct >= 90 else "LOW"
        print(f"  {category:<30} {cat_passed:>3}/{cat_total:<3} ({cat_pct:>5.1f}%) [{status}]")

    # By domain (top failures)
    print(f"\n### DOMAIN-SPECIFIC ACCURACY ###")
    domain_stats = []
    for domain, stats in results["by_domain"].items():
        total = stats["total"]
        correct = stats["correct"]
        pct = (correct / total * 100) if total > 0 else 0
        domain_stats.append((domain, correct, total, pct))

    # Sort by percentage (lowest first to highlight problems)
    domain_stats.sort(key=lambda x: x[3])

    for domain, correct, total, pct in domain_stats[:15]:
        status = "OK" if pct >= 80 else "LOW" if pct >= 50 else "CRITICAL"
        print(f"  {domain:<25} {correct:>3}/{total:<3} ({pct:>5.1f}%) [{status}]")

    if len(domain_stats) > 15:
        print(f"  ... and {len(domain_stats) - 15} more domains")

    # Failures
    if results["failures"]:
        print(f"\n### FAILURES ({len(results['failures'])}) ###")
        shown = 0
        for failure in results["failures"]:
            if not verbose and shown >= 10:
                print(f"\n  ... and {len(results['failures']) - 10} more failures")
                print("  Use --verbose to see all failures")
                break

            print(f"\n  [{failure.test_id}]")
            print(f"    Domain: expected={failure.expected_domain}, got={failure.actual_domain} {'OK' if failure.domain_correct else 'FAIL'}")
            if failure.expected_intents:
                print(f"    Intent: expected={failure.expected_intents}, got={failure.actual_intents} {'OK' if failure.intents_correct else 'FAIL'}")
            print(f"    Score: {failure.domain_score:.1f}, Confidence: {failure.domain_confidence:.2f}")
            if failure.notes:
                print(f"    Notes: {failure.notes}")
            shown += 1

    # Recommendations
    print(f"\n### RECOMMENDATIONS ###")
    if domain_accuracy < 95:
        gap = 95 - domain_accuracy
        print(f"  1. Domain accuracy is {gap:.1f}% below target (95%)")
        low_domains = [d for d, c, t, p in domain_stats if p < 80]
        if low_domains:
            print(f"     Focus on expanding: {', '.join(low_domains[:5])}")

    if intent_accuracy < 90:
        gap = 90 - intent_accuracy
        print(f"  2. Intent accuracy is {gap:.1f}% below target (90%)")

    if domain_accuracy >= 95 and intent_accuracy >= 90:
        print("  All targets met! Continue adding golden tests to ensure coverage.")

    print("\n" + "=" * 80)


def main():
    global DEFAULT_MIN_SCORE

    parser = argparse.ArgumentParser(
        description="SemanticGenius accuracy benchmark"
    )
    parser.add_argument("--verbose", "-v", action="store_true",
                        help="Show all failure details")
    parser.add_argument("--category", help="Filter by test category")
    parser.add_argument("--json", action="store_true",
                        help="Output results as JSON")
    parser.add_argument("--threshold", type=float, default=3.0,
                        help="Minimum score threshold (default: 3.0)")

    args = parser.parse_args()

    # Update threshold if specified
    DEFAULT_MIN_SCORE = args.threshold

    # Run benchmark
    results = run_benchmark(verbose=args.verbose, category_filter=args.category)

    if args.json:
        # Convert failures to serializable format
        output = {
            "total": results["total"],
            "passed": results["passed"],
            "failed": results["failed"],
            "domain_correct": results["domain_correct"],
            "intent_correct": results["intent_correct"],
            "domain_accuracy": (results["domain_correct"] / results["total"] * 100) if results["total"] > 0 else 0,
            "intent_accuracy": (results["intent_correct"] / results["total"] * 100) if results["total"] > 0 else 0,
            "by_category": dict(results["by_category"]),
            "failures": [
                {
                    "test_id": f.test_id,
                    "expected_domain": f.expected_domain,
                    "actual_domain": f.actual_domain,
                    "expected_intents": f.expected_intents,
                    "actual_intents": f.actual_intents,
                    "notes": f.notes
                }
                for f in results["failures"]
            ]
        }
        print(json.dumps(output, indent=2))
    else:
        print_benchmark_report(results, verbose=args.verbose)


if __name__ == "__main__":
    main()
