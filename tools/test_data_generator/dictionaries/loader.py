"""
Dictionary loader for classification dictionaries.

Loads domain and intent dictionaries from the app's assets directory
for use in generating realistic test data.
"""

import json
import os
from pathlib import Path
from typing import Dict, List, Optional, Set
from dataclasses import dataclass, field

from ..config import Domain, Intent, TIER_WEIGHTS


@dataclass
class DomainDictionary:
    """Loaded domain dictionary with tiered terms."""
    domain: str
    tier_a: List[str] = field(default_factory=list)
    tier_b: List[str] = field(default_factory=list)
    tier_c: List[str] = field(default_factory=list)
    phrase_patterns: List[Dict] = field(default_factory=list)

    def get_all_terms(self) -> List[str]:
        """Get all terms across all tiers."""
        return self.tier_a + self.tier_b + self.tier_c

    def get_weighted_terms(self) -> List[tuple]:
        """Get terms with their weights: [(term, weight), ...]"""
        result = []
        for term in self.tier_a:
            result.append((term, TIER_WEIGHTS["tierA"]))
        for term in self.tier_b:
            result.append((term, TIER_WEIGHTS["tierB"]))
        for term in self.tier_c:
            result.append((term, TIER_WEIGHTS["tierC"]))
        return result


@dataclass
class IntentDictionary:
    """Loaded intent dictionary with phrase patterns."""
    intent: str
    phrase_patterns: List[Dict] = field(default_factory=list)
    triggers: List[str] = field(default_factory=list)


class DictionaryLoader:
    """
    Load classification dictionaries from the app's assets.

    Usage:
        loader = DictionaryLoader("app/src/main/assets/classification/en")
        loader.load_all()
        professional_dict = loader.get_domain("PROFESSIONAL")
        terms = professional_dict.tier_a[:5]
    """

    def __init__(self, assets_path: str):
        """
        Initialize with path to classification assets.

        Args:
            assets_path: Path like "app/src/main/assets/classification/en"
        """
        self.assets_path = Path(assets_path)
        self.domains: Dict[str, DomainDictionary] = {}
        self.intents: Dict[str, IntentDictionary] = {}
        self.event_patterns: Dict = {}
        self._loaded = False

    def load_all(self) -> None:
        """Load all domain and intent dictionaries."""
        self._load_domains()
        self._load_intents()
        self._load_event_patterns()
        self._loaded = True

    def _load_domains(self) -> None:
        """Load all domain dictionaries from domains/*.json."""
        domains_dir = self.assets_path / "domains"
        if not domains_dir.exists():
            print(f"Warning: Domains directory not found: {domains_dir}")
            return

        for json_file in domains_dir.glob("*.json"):
            try:
                with open(json_file, "r", encoding="utf-8") as f:
                    data = json.load(f)

                domain_name = data.get("domain", json_file.stem.upper())
                dictionary = DomainDictionary(
                    domain=domain_name,
                    tier_a=data.get("tierA", {}).get("terms", []),
                    tier_b=data.get("tierB", {}).get("terms", []),
                    tier_c=data.get("tierC", {}).get("terms", []),
                    phrase_patterns=data.get("phrasePatterns", []),
                )
                self.domains[domain_name] = dictionary
            except Exception as e:
                print(f"Warning: Failed to load {json_file}: {e}")

    def _load_intents(self) -> None:
        """Load all intent dictionaries from intents/*.json."""
        intents_dir = self.assets_path / "intents"
        if not intents_dir.exists():
            print(f"Warning: Intents directory not found: {intents_dir}")
            return

        for json_file in intents_dir.glob("*.json"):
            try:
                with open(json_file, "r", encoding="utf-8") as f:
                    data = json.load(f)

                intent_name = data.get("intent", json_file.stem.upper())
                dictionary = IntentDictionary(
                    intent=intent_name,
                    phrase_patterns=data.get("phrasePatterns", []),
                    triggers=data.get("triggers", []),
                )
                self.intents[intent_name] = dictionary
            except Exception as e:
                print(f"Warning: Failed to load {json_file}: {e}")

    def _load_event_patterns(self) -> None:
        """Load event_patterns.json."""
        patterns_file = self.assets_path / "event_patterns.json"
        if not patterns_file.exists():
            return

        try:
            with open(patterns_file, "r", encoding="utf-8") as f:
                self.event_patterns = json.load(f)
        except Exception as e:
            print(f"Warning: Failed to load event_patterns.json: {e}")

    def get_domain(self, domain: str) -> Optional[DomainDictionary]:
        """Get dictionary for a specific domain."""
        if not self._loaded:
            self.load_all()
        return self.domains.get(domain.upper())

    def get_intent(self, intent: str) -> Optional[IntentDictionary]:
        """Get dictionary for a specific intent."""
        if not self._loaded:
            self.load_all()
        return self.intents.get(intent.upper())

    def get_domain_terms(
        self,
        domain: str,
        tier: Optional[str] = None,
        count: Optional[int] = None
    ) -> List[str]:
        """
        Get terms for a domain, optionally filtered by tier.

        Args:
            domain: Domain name (e.g., "PROFESSIONAL")
            tier: Optional tier filter ("tierA", "tierB", "tierC")
            count: Optional max number of terms to return

        Returns:
            List of terms
        """
        dictionary = self.get_domain(domain)
        if not dictionary:
            return []

        if tier == "tierA":
            terms = dictionary.tier_a
        elif tier == "tierB":
            terms = dictionary.tier_b
        elif tier == "tierC":
            terms = dictionary.tier_c
        else:
            terms = dictionary.get_all_terms()

        if count:
            return terms[:count]
        return terms

    def get_intent_patterns(self, intent: str) -> List[Dict]:
        """Get phrase patterns for an intent."""
        dictionary = self.get_intent(intent)
        if not dictionary:
            return []
        return dictionary.phrase_patterns

    def get_event_keywords(self, domain: str) -> List[str]:
        """Get event-specific keywords for a domain from event_patterns.json."""
        if not self._loaded:
            self.load_all()

        domain_patterns = self.event_patterns.get(domain.lower(), {})
        return domain_patterns.get("keywords", [])

    def get_available_domains(self) -> List[str]:
        """Get list of loaded domain names."""
        if not self._loaded:
            self.load_all()
        return list(self.domains.keys())

    def get_available_intents(self) -> List[str]:
        """Get list of loaded intent names."""
        if not self._loaded:
            self.load_all()
        return list(self.intents.keys())

    def get_high_weight_terms(self, domain: str, count: int = 5) -> List[str]:
        """Get the highest-weight terms for a domain (tierA first)."""
        dictionary = self.get_domain(domain)
        if not dictionary:
            return []

        terms = []
        # Prioritize tierA
        terms.extend(dictionary.tier_a[:count])
        remaining = count - len(terms)

        # Add tierB if needed
        if remaining > 0:
            terms.extend(dictionary.tier_b[:remaining])
            remaining = count - len(terms)

        # Add tierC if still needed
        if remaining > 0:
            terms.extend(dictionary.tier_c[:remaining])

        return terms[:count]

    def get_phrase_pattern_examples(self, domain: str, count: int = 3) -> List[str]:
        """
        Get example text matching phrase patterns for a domain.

        Returns human-readable examples, not regex patterns.
        """
        dictionary = self.get_domain(domain)
        if not dictionary or not dictionary.phrase_patterns:
            return []

        examples = []
        for pattern in dictionary.phrase_patterns[:count]:
            # Convert regex-style pattern to example text
            pattern_str = pattern.get("pattern", "")
            # Simple conversion: remove regex syntax
            example = pattern_str.replace("\\s+", " ")
            example = example.replace("(?:", "")
            example = example.replace(")", "")
            example = example.replace("|", " or ")
            examples.append(example)

        return examples


# Singleton loader instance
_default_loader: Optional[DictionaryLoader] = None


def get_loader(assets_path: Optional[str] = None) -> DictionaryLoader:
    """
    Get the default dictionary loader instance.

    Args:
        assets_path: Optional custom path to classification assets

    Returns:
        DictionaryLoader instance (singleton unless custom path provided)
    """
    global _default_loader

    if assets_path:
        return DictionaryLoader(assets_path)

    if _default_loader is None:
        # Try to find assets relative to project root
        possible_paths = [
            "app/src/main/assets/classification/en",
            "../../../app/src/main/assets/classification/en",
            "C:/Users/fort/Projects/Boox_Day/app/src/main/assets/classification/en",
        ]
        for path in possible_paths:
            if Path(path).exists():
                _default_loader = DictionaryLoader(path)
                break
        else:
            _default_loader = DictionaryLoader(possible_paths[0])

    return _default_loader
