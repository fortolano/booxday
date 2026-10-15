"""
Semantic bundle computation for generated text.

Extracts keywords, entities, action items, and other semantic data
from generated text to create complete UnifiedOcrText records.
"""

import re
import unicodedata
from typing import List, Dict, Optional, Tuple, Set
from collections import Counter

from ..config import Domain, Intent, EntityType, DocumentStyle, TIER_WEIGHTS
from ..models import (
    KeywordEntry, ExtractedEntity, ActionItem, TemporalSignals, IntentEntry,
    serialize_keywords, serialize_entities, serialize_action_items,
    serialize_temporal_signals, serialize_intents, serialize_string_list
)
from ..dictionaries.loader import DictionaryLoader, get_loader
from ..dictionaries.entity_pools import PERSON_NAMES, ORGANIZATIONS, LOCATIONS


# Common action verbs for action item extraction
ACTION_VERBS = [
    "review", "complete", "finish", "update", "create", "prepare",
    "schedule", "send", "call", "email", "follow up", "discuss",
    "check", "confirm", "submit", "approve", "assign", "delegate",
    "implement", "fix", "resolve", "investigate", "analyze", "research",
    "write", "draft", "edit", "plan", "organize", "meet with",
]

# Temporal patterns
DATE_PATTERNS = [
    r"\b(\d{4}-\d{2}-\d{2})\b",  # 2024-01-15
    r"\b(\d{1,2}/\d{1,2}(?:/\d{2,4})?)\b",  # 1/15 or 1/15/2024
    r"\b(January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{1,2}(?:st|nd|rd|th)?\b",
]

WEEKDAY_PATTERN = r"\b(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\b"

RELATIVE_DATE_PATTERNS = [
    r"\b(today|tomorrow|yesterday)\b",
    r"\b(next|this)\s+(week|month|quarter|year)\b",
    r"\b(in\s+\d+\s+(?:day|week|month)s?)\b",
]

TIME_PATTERNS = [
    r"\b(\d{1,2}:\d{2}(?:\s*(?:am|pm|AM|PM))?)\b",
    r"\b(\d{1,2}\s*(?:am|pm|AM|PM))\b",
]

# Entity patterns
EMAIL_PATTERN = r"\b[\w.+-]+@[\w.-]+\.\w+\b"
PHONE_PATTERN = r"\b(?:\+?1[-.]?)?\s*\(?[2-9]\d{2}\)?[-.\s]?\d{3}[-.\s]?\d{4}\b"
URL_PATTERN = r"\bhttps?://[^\s<>\"{}|\\^`\[\]]+"
MONEY_PATTERN = r"\$[\d,]+(?:\.\d{2})?"
PERCENT_PATTERN = r"\b\d+(?:\.\d+)?%"


class SemanticBundleComputer:
    """
    Compute semantic bundle data from text.

    Creates all the JSON fields needed for UnifiedOcrText records.
    """

    def __init__(self, dictionary_loader: Optional[DictionaryLoader] = None):
        """
        Initialize semantic bundle computer.

        Args:
            dictionary_loader: Optional custom dictionary loader
        """
        self.dict_loader = dictionary_loader or get_loader()

        # Build entity lookup sets from pools
        self._person_names = self._build_entity_set(PERSON_NAMES)
        self._org_names = self._build_entity_set(ORGANIZATIONS)
        self._location_names = self._build_entity_set(LOCATIONS)

    def _build_entity_set(self, pool_dict: Dict[str, List[str]]) -> Set[str]:
        """Build lowercase set of all entities in pool."""
        entities = set()
        for names in pool_dict.values():
            for name in names:
                # Add full name and parts
                entities.add(name.lower())
                # Also add parts for partial matching
                parts = name.split()
                for part in parts:
                    if len(part) > 2 and part[0].isupper():
                        entities.add(part.lower())
        return entities

    def compute(
        self,
        text: str,
        domain: Domain,
        intents: List[Intent],
        source_title: Optional[str] = None
    ) -> Dict:
        """
        Compute full semantic bundle for text.

        Args:
            text: Input text
            domain: Target domain
            intents: Target intents
            source_title: Optional title for keyword boosting

        Returns:
            Dictionary with all semantic fields ready for JSON serialization
        """
        # Normalize text
        text_normalized = self.normalize_text(text)
        clean_text = self.clean_text(text)

        # Extract components
        keywords = self.extract_keywords(text, domain, source_title)
        entities = self.extract_entities(text)
        action_items = self.extract_action_items(text)
        temporal = self.extract_temporal_signals(text)
        key_phrases = self.extract_key_phrases(text, domain)
        key_sentences = self.extract_key_sentences(text)
        synopsis = self.generate_synopsis(text, domain, intents)
        doc_style = self.detect_doc_style(text, intents)

        # Create intent entries
        intent_entries = [
            IntentEntry(intent=i.value, confidence=0.8 - (idx * 0.1))
            for idx, i in enumerate(intents[:4])
        ]

        return {
            "text_normalized": text_normalized,
            "clean_text": clean_text,
            "word_count": len(text.split()),
            "doc_style": doc_style.value if doc_style else None,
            "keywords_json": serialize_keywords(keywords),
            "key_phrases_json": serialize_string_list(key_phrases),
            "key_sentences_json": serialize_string_list(key_sentences),
            "entities_json": serialize_entities(entities),
            "action_items_json": serialize_action_items(action_items),
            "temporal_signals_json": serialize_temporal_signals(temporal),
            "topics_json": serialize_string_list(key_phrases[:3]),
            "synopsis": synopsis,
            "domain": domain.value,
            "domain_confidence": 0.85,
            "intents_json": serialize_intents(intent_entries),
        }

    def normalize_text(self, text: str) -> str:
        """
        Normalize text (lowercase, NFKC, collapse whitespace).

        Matches the normalization in UnifiedOcrPipeline.
        """
        # NFKC normalization
        text = unicodedata.normalize("NFKC", text)
        # Lowercase
        text = text.lower()
        # Collapse whitespace
        text = re.sub(r"\s+", " ", text)
        return text.strip()

    def clean_text(self, text: str) -> str:
        """
        Clean text (remove OCR artifacts, normalize lines).

        Matches the cleaning in UnifiedOcrPipeline.
        """
        # Remove excessive blank lines
        text = re.sub(r"\n{3,}", "\n\n", text)
        # Normalize whitespace within lines
        lines = text.split("\n")
        lines = [re.sub(r"\s+", " ", line).strip() for line in lines]
        return "\n".join(lines)

    def extract_keywords(
        self,
        text: str,
        domain: Domain,
        source_title: Optional[str] = None
    ) -> List[KeywordEntry]:
        """
        Extract keywords from text with scoring.

        Uses domain dictionary terms for boosting.
        """
        # Get domain dictionary
        domain_dict = self.dict_loader.get_domain(domain.value)

        # Build term weight map
        term_weights = {}
        if domain_dict:
            for term in domain_dict.tier_a:
                term_weights[term.lower()] = TIER_WEIGHTS["tierA"]
            for term in domain_dict.tier_b:
                term_weights[term.lower()] = TIER_WEIGHTS["tierB"]
            for term in domain_dict.tier_c:
                term_weights[term.lower()] = TIER_WEIGHTS["tierC"]

        # Tokenize and count
        words = re.findall(r"\b\w+\b", text.lower())
        word_counts = Counter(words)

        # Score keywords
        keywords = []
        seen = set()

        for word, count in word_counts.most_common(50):
            if len(word) < 3 or word in seen:
                continue

            # Check if it's a domain term
            weight = term_weights.get(word, 0.5)
            tier = None
            if word in [t.lower() for t in (domain_dict.tier_a if domain_dict else [])]:
                tier = "tierA"
            elif word in [t.lower() for t in (domain_dict.tier_b if domain_dict else [])]:
                tier = "tierB"
            elif word in [t.lower() for t in (domain_dict.tier_c if domain_dict else [])]:
                tier = "tierC"

            # Calculate score
            score = min(1.0, (count * weight) / 10.0)

            # Boost if in title
            if source_title and word in source_title.lower():
                score = min(1.0, score + 0.2)
                tier = tier or "title_boost"

            if score >= 0.3:
                keywords.append(KeywordEntry(
                    term=word,
                    score=round(score, 2),
                    frequency=count,
                    boosted_by=tier
                ))
                seen.add(word)

        # Sort by score
        keywords.sort(key=lambda k: k.score, reverse=True)
        return keywords[:20]

    def extract_entities(self, text: str) -> List[ExtractedEntity]:
        """Extract named entities from text."""
        entities = []

        # Extract persons (match against pool)
        for name in self._find_entities_in_text(text, self._person_names):
            entities.append(ExtractedEntity(
                text=name,
                entity_type=EntityType.PERSON.value,
                confidence=0.9
            ))

        # Extract organizations
        for name in self._find_entities_in_text(text, self._org_names):
            entities.append(ExtractedEntity(
                text=name,
                entity_type=EntityType.ORG.value,
                confidence=0.85
            ))

        # Extract locations
        for name in self._find_entities_in_text(text, self._location_names):
            entities.append(ExtractedEntity(
                text=name,
                entity_type=EntityType.LOCATION.value,
                confidence=0.85
            ))

        # Extract dates
        for pattern in DATE_PATTERNS:
            for match in re.finditer(pattern, text, re.IGNORECASE):
                entities.append(ExtractedEntity(
                    text=match.group(0),
                    entity_type=EntityType.DATE.value,
                    confidence=0.95
                ))

        # Extract times
        for pattern in TIME_PATTERNS:
            for match in re.finditer(pattern, text):
                entities.append(ExtractedEntity(
                    text=match.group(0),
                    entity_type=EntityType.TIME.value,
                    confidence=0.95
                ))

        # Extract emails
        for match in re.finditer(EMAIL_PATTERN, text):
            entities.append(ExtractedEntity(
                text=match.group(0),
                entity_type=EntityType.EMAIL.value,
                confidence=0.98
            ))

        # Extract phones
        for match in re.finditer(PHONE_PATTERN, text):
            entities.append(ExtractedEntity(
                text=match.group(0),
                entity_type=EntityType.PHONE.value,
                confidence=0.9
            ))

        # Extract URLs
        for match in re.finditer(URL_PATTERN, text):
            entities.append(ExtractedEntity(
                text=match.group(0),
                entity_type=EntityType.URL.value,
                confidence=0.98
            ))

        # Extract money
        for match in re.finditer(MONEY_PATTERN, text):
            entities.append(ExtractedEntity(
                text=match.group(0),
                entity_type=EntityType.MONEY.value,
                confidence=0.95
            ))

        # Extract percentages
        for match in re.finditer(PERCENT_PATTERN, text):
            entities.append(ExtractedEntity(
                text=match.group(0),
                entity_type=EntityType.PERCENT.value,
                confidence=0.95
            ))

        # Deduplicate
        seen = set()
        unique = []
        for e in entities:
            key = (e.text.lower(), e.entity_type)
            if key not in seen:
                seen.add(key)
                unique.append(e)

        return unique[:30]

    def _find_entities_in_text(
        self,
        text: str,
        entity_set: Set[str]
    ) -> List[str]:
        """Find entity pool matches in text."""
        found = []
        text_lower = text.lower()

        # Look for capitalized words that might be names
        # Use [ ]+ instead of \s+ to avoid matching newlines
        potential = re.findall(r"\b([A-Z][a-z]+(?:[ ]+[A-Z][a-z]+)*)\b", text)

        for name in potential:
            name_lower = name.lower()
            # Check if any part matches entity pool
            parts = name_lower.split()
            for part in parts:
                if part in entity_set:
                    found.append(name)
                    break

        return list(set(found))

    def extract_action_items(self, text: str) -> List[ActionItem]:
        """Extract action items (verb + object) from text."""
        action_items = []

        # Split into sentences/lines
        lines = re.split(r"[.\n]", text)

        for line in lines:
            line = line.strip()
            if not line:
                continue

            # Check for checkbox patterns
            checkbox_match = re.match(r"[\-\*\[\]x]\s*\[?\s*[xX]?\s*\]?\s*(.+)", line)
            if checkbox_match:
                task_text = checkbox_match.group(1)
                verb, obj = self._extract_verb_object(task_text)
                if verb:
                    action_items.append(ActionItem(
                        verb=verb,
                        object_text=obj,
                        confidence=0.85
                    ))
                continue

            # Check for action verb at start
            for verb in ACTION_VERBS:
                if line.lower().startswith(verb):
                    # Extract object (rest of line)
                    obj = line[len(verb):].strip()
                    if obj:
                        # Remove leading punctuation
                        obj = re.sub(r"^[\s:,\-]+", "", obj)
                        action_items.append(ActionItem(
                            verb=verb,
                            object_text=obj[:100],
                            confidence=0.8
                        ))
                    break

        return action_items[:10]

    def _extract_verb_object(self, text: str) -> Tuple[Optional[str], str]:
        """Extract verb and object from task text."""
        text_lower = text.lower().strip()

        for verb in ACTION_VERBS:
            if text_lower.startswith(verb):
                obj = text[len(verb):].strip()
                obj = re.sub(r"^[\s:,\-]+", "", obj)
                return verb, obj

        # If no verb found, use "complete" as default
        return "complete", text

    def extract_temporal_signals(self, text: str) -> TemporalSignals:
        """Extract temporal references from text."""
        signals = TemporalSignals()

        # Explicit dates
        for pattern in DATE_PATTERNS:
            for match in re.finditer(pattern, text, re.IGNORECASE):
                signals.explicit_dates.append(match.group(0))

        # Weekdays
        for match in re.finditer(WEEKDAY_PATTERN, text, re.IGNORECASE):
            signals.weekdays.append(match.group(1))

        # Relative dates
        for pattern in RELATIVE_DATE_PATTERNS:
            for match in re.finditer(pattern, text, re.IGNORECASE):
                signals.relative_dates.append(match.group(0))

        # Times
        for pattern in TIME_PATTERNS:
            for match in re.finditer(pattern, text):
                signals.times.append(match.group(0))

        # Deduplicate
        signals.explicit_dates = list(set(signals.explicit_dates))
        signals.weekdays = list(set(signals.weekdays))
        signals.relative_dates = list(set(signals.relative_dates))
        signals.times = list(set(signals.times))

        return signals

    def extract_key_phrases(
        self,
        text: str,
        domain: Domain
    ) -> List[str]:
        """Extract key phrases from text."""
        phrases = []

        # Get domain dictionary phrase patterns
        domain_dict = self.dict_loader.get_domain(domain.value)
        if domain_dict and domain_dict.phrase_patterns:
            for pattern_info in domain_dict.phrase_patterns:
                pattern = pattern_info.get("pattern", "")
                try:
                    for match in re.finditer(pattern, text, re.IGNORECASE):
                        phrases.append(match.group(0))
                except re.error:
                    continue

        # Extract noun phrases (simple heuristic)
        # Look for sequences of capitalized words
        # Use [ ]+ instead of \s+ to avoid matching newlines
        np_matches = re.findall(
            r"\b([A-Z][a-z]+(?:[ ]+[A-Z][a-z]+)+)\b",
            text
        )
        phrases.extend(np_matches[:5])

        # Deduplicate and limit
        return list(set(phrases))[:10]

    def extract_key_sentences(self, text: str) -> List[str]:
        """Extract important sentences from text."""
        sentences = re.split(r"[.!?]\s+", text)
        sentences = [s.strip() for s in sentences if len(s.strip()) > 20]

        # Score sentences by signal words
        signal_words = [
            "important", "key", "critical", "must", "should", "need",
            "decision", "action", "follow up", "next steps", "agreed",
        ]

        scored = []
        for sent in sentences:
            score = sum(1 for w in signal_words if w in sent.lower())
            scored.append((score, sent))

        # Sort by score and take top
        scored.sort(key=lambda x: x[0], reverse=True)
        return [s[1][:200] for s in scored[:5]]

    def generate_synopsis(
        self,
        text: str,
        domain: Domain,
        intents: List[Intent]
    ) -> str:
        """Generate a brief synopsis of the text."""
        # Take first sentence or ~100 chars
        sentences = re.split(r"[.!?]\s+", text)
        first_sent = sentences[0] if sentences else text[:100]

        # Add domain/intent context
        domain_label = domain.value.replace("_", " ").title()
        intent_labels = ", ".join(i.value.replace("_", " ").title() for i in intents[:2])

        synopsis = f"{domain_label} content: {first_sent[:150]}"
        if intent_labels:
            synopsis += f" (Intent: {intent_labels})"

        return synopsis[:900]

    def detect_doc_style(
        self,
        text: str,
        intents: List[Intent]
    ) -> Optional[DocumentStyle]:
        """Detect document style from text patterns."""
        # Check for checklist indicators
        if re.search(r"[\-\*]\s*\[\s*[xX]?\s*\]", text):
            return DocumentStyle.CHECKLIST
        if text.count("- [ ]") >= 2 or text.count("[ ]") >= 2:
            return DocumentStyle.CHECKLIST

        # Check for meeting notes indicators
        if any(w in text.lower() for w in ["attendees:", "agenda:", "action items:"]):
            return DocumentStyle.MEETING_NOTES
        if re.search(r"meeting\s+(notes|minutes)", text, re.IGNORECASE):
            return DocumentStyle.MEETING_NOTES

        # Check for outline (numbered lists)
        if re.search(r"^\d+\.\s+", text, re.MULTILINE):
            return DocumentStyle.OUTLINE

        # Use intent as fallback
        if intents:
            from ..config import INTENT_DOC_STYLES
            return INTENT_DOC_STYLES.get(intents[0], DocumentStyle.NOTES)

        return DocumentStyle.NOTES


# Convenience function
def compute_semantic_bundle(
    text: str,
    domain: Domain,
    intents: List[Intent],
    source_title: Optional[str] = None
) -> Dict:
    """
    Compute semantic bundle for text.

    Args:
        text: Input text
        domain: Target domain
        intents: Target intents
        source_title: Optional title for keyword boosting

    Returns:
        Dictionary with all semantic fields
    """
    computer = SemanticBundleComputer()
    return computer.compute(text, domain, intents, source_title)
