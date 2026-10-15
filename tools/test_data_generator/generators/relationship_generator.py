"""
Semantic relationship generator.

Creates OCR + Event pairs with controlled affinity levels
for testing SemanticGeniusService matching algorithms.
"""

import random
import json
from datetime import datetime, timedelta
from typing import List, Optional, Tuple

from ..config import (
    Domain, Intent, RelationshipType, MatchReason,
    AFFINITY_RANGES, DOMAIN_SIBLINGS, FOCUS_DOMAINS, DOMAIN_INTENTS
)
from ..models import GeneratedOcrText, GeneratedCalendarEvent, SemanticRelationship
from ..dictionaries.entity_pools import EntityPool, get_pool
from ..dictionaries.loader import get_loader
from .ocr_generator import OcrGenerator
from .event_generator import EventGenerator
from .text_generator import TextGenerator
from .semantic_bundle import SemanticBundleComputer


def get_weekday_name(date_str: str) -> str:
    """Get weekday name from date string (YYYY-MM-DD)."""
    try:
        dt = datetime.strptime(date_str, "%Y-%m-%d")
        return dt.strftime("%A")  # e.g., "Monday", "Tuesday"
    except ValueError:
        return ""


class RelationshipGenerator:
    """
    Generate OCR + Event pairs with controlled semantic affinity.

    Creates test data for validating SemanticGeniusService's
    9-component affinity scoring algorithm.
    """

    def __init__(self, seed: Optional[int] = None):
        """
        Initialize relationship generator.

        Args:
            seed: Optional random seed for reproducibility
        """
        self.ocr_gen = OcrGenerator(seed=seed)
        self.event_gen = EventGenerator(seed=seed)
        self.text_gen = TextGenerator(seed=seed)
        self.semantic_computer = SemanticBundleComputer()
        self.entity_pool = get_pool(seed)
        self.dict_loader = get_loader()

        if seed is not None:
            random.seed(seed)

    def generate_high_affinity_pair(
        self,
        domain: Domain,
        date: Optional[str] = None
    ) -> Tuple[GeneratedOcrText, GeneratedCalendarEvent, SemanticRelationship]:
        """
        Generate OCR + Event pair with HIGH expected affinity (0.45-0.85).

        High affinity pairs share:
        - Same domain (+0.20)
        - 2+ person entities (+0.12)
        - 1 org entity (+0.04)
        - 35%+ keyword overlap (~0.06-0.08)
        - Matching temporal signals (same date) (+0.05)
        - Intent overlap (~0.09)
        Total expected: ~0.50-0.58

        Args:
            domain: Domain for both records
            date: Shared date (today if None)

        Returns:
            Tuple of (OCR record, Event record, Relationship definition)
        """
        if date is None:
            date = datetime.now().strftime("%Y-%m-%d")

        # Reset entity pool for fresh entities
        self.entity_pool.reset_used()

        # Get shared entities
        shared_persons = self.entity_pool.get_persons(3, domain)
        shared_org = self.entity_pool.get_organization(domain)

        # Get shared keywords from domain dictionary
        # FORCE exact same 5 keywords for both to maximize Jaccard
        shared_keywords = self.dict_loader.get_high_weight_terms(domain.value, 5)

        # Generate intents
        intents = self._get_intents_for_domain(domain)
        # FORCE exact intent match for primary intent
        primary_intent = intents[0] if intents else Intent.MEETING_NOTES
        
        # Shared Key Phrases (inject explicitly)
        shared_phrases = ["Critical Milestone", "Final Review"]
        
        # Shared Action Item components
        action_verb = "complete"
        action_object = "quarterly report"

        # Generate OCR text with shared entities and keywords
        ocr_text = self._generate_ocr_with_entities(
            domain=domain,
            intents=intents,
            persons=shared_persons,
            org=shared_org,
            keywords=shared_keywords,
            date=date,
            extra_phrases=shared_phrases,
            action_item=(action_verb, action_object)
        )

        # Generate event with same entities
        # Pass ALL keywords to event for max overlap
        event = self.event_gen.generate_event_with_entities(
            domain=domain,
            persons=shared_persons[:2],  # 2 shared persons
            org=shared_org,
            topics=shared_keywords,  # Use ALL shared keywords
            date=date
        )
        
        # Inject key phrases and action into event description to ensure match
        if event.description:
            event.description += f"\n\nContext: {', '.join(shared_phrases)}."
            event.description += f"\nGoal: {action_verb} {action_object}."
        else:
            event.description = f"Context: {', '.join(shared_phrases)}.\nGoal: {action_verb} {action_object}."

        # Calculate expected affinity
        match_reasons = [
            MatchReason.DOMAIN.value,
            MatchReason.PERSON.value,
            MatchReason.KEYWORD.value,
            MatchReason.TEMPORAL.value,
            MatchReason.INTENT.value,
            MatchReason.KEY_PHRASE.value,
            MatchReason.ACTION_ITEM.value
        ]
        if shared_org:
            match_reasons.append(MatchReason.ORG.value)

        min_affinity, max_affinity = AFFINITY_RANGES[RelationshipType.HIGH_MATCH]

        relationship = SemanticRelationship(
            ocr_id=ocr_text.id,
            event_id=event.id,
            expected_affinity_min=min_affinity,
            expected_affinity_max=max_affinity,
            relationship_type=RelationshipType.HIGH_MATCH.value,
            match_reasons=match_reasons
        )

        return ocr_text, event, relationship

    def generate_medium_affinity_pair(
        self,
        domain: Domain,
        date: Optional[str] = None
    ) -> Tuple[GeneratedOcrText, GeneratedCalendarEvent, SemanticRelationship]:
        """
        Generate OCR + Event pair with MEDIUM expected affinity (0.25-0.50).

        Medium affinity pairs share:
        - Same or sibling domain
        - 1 shared entity OR 15%+ keyword overlap
        - Maybe partial temporal match

        Args:
            domain: Domain for OCR record
            date: Date (today if None)

        Returns:
            Tuple of (OCR record, Event record, Relationship definition)
        """
        if date is None:
            date = datetime.now().strftime("%Y-%m-%d")

        self.entity_pool.reset_used()

        # Get some shared entities (fewer than high affinity)
        shared_person = self.entity_pool.get_person(domain)

        # Get shared keywords (fewer)
        shared_keywords = self.dict_loader.get_high_weight_terms(domain.value, 3)

        intents = self._get_intents_for_domain(domain)

        # Generate OCR with one shared person and some keywords
        ocr_text = self._generate_ocr_with_entities(
            domain=domain,
            intents=intents,
            persons=[shared_person],
            org=None,  # No shared org
            keywords=shared_keywords,
            date=date
        )

        # Use same or sibling domain for event
        siblings = DOMAIN_SIBLINGS.get(domain, set())
        event_domain = random.choice(list(siblings)) if siblings and random.random() < 0.3 else domain

        # Generate event with partial overlap
        other_person = self.entity_pool.get_person(event_domain)
        event = self.event_gen.generate_event_with_entities(
            domain=event_domain,
            persons=[shared_person, other_person],  # 1 shared
            org=None,
            topics=shared_keywords[:1] if shared_keywords else None,
            date=date
        )

        # Calculate match reasons
        match_reasons = []
        if event_domain == domain:
            match_reasons.append(MatchReason.DOMAIN.value)
        else:
            match_reasons.append(MatchReason.DOMAIN_SIBLING.value)
        match_reasons.append(MatchReason.PERSON.value)
        if shared_keywords:
            match_reasons.append(MatchReason.KEYWORD.value)

        min_affinity, max_affinity = AFFINITY_RANGES[RelationshipType.MEDIUM_MATCH]

        relationship = SemanticRelationship(
            ocr_id=ocr_text.id,
            event_id=event.id,
            expected_affinity_min=min_affinity,
            expected_affinity_max=max_affinity,
            relationship_type=RelationshipType.MEDIUM_MATCH.value,
            match_reasons=match_reasons
        )

        return ocr_text, event, relationship

    def generate_low_affinity_pair(
        self,
        ocr_domain: Domain,
        event_domain: Optional[Domain] = None,
        date: Optional[str] = None
    ) -> Tuple[GeneratedOcrText, GeneratedCalendarEvent, SemanticRelationship]:
        """
        Generate OCR + Event pair with LOW expected affinity (0.00-0.20).

        Low affinity pairs have:
        - Different non-sibling domains
        - No shared entities
        - Minimal keyword overlap

        Args:
            ocr_domain: Domain for OCR record
            event_domain: Domain for event (auto-selected if None)
            date: Date (today if None)

        Returns:
            Tuple of (OCR record, Event record, Relationship definition)
        """
        if date is None:
            date = datetime.now().strftime("%Y-%m-%d")

        # Select event domain that is NOT a sibling
        if event_domain is None:
            siblings = DOMAIN_SIBLINGS.get(ocr_domain, set())
            forbidden = {ocr_domain} | siblings
            available = [d for d in FOCUS_DOMAINS if d not in forbidden]
            event_domain = random.choice(available) if available else Domain.MISC

        self.entity_pool.reset_used()

        # Generate OCR with domain-specific content
        ocr_intents = self._get_intents_for_domain(ocr_domain)
        ocr_persons = self.entity_pool.get_persons(2, ocr_domain)
        ocr_keywords = self.dict_loader.get_high_weight_terms(ocr_domain.value, 4)

        ocr_text = self._generate_ocr_with_entities(
            domain=ocr_domain,
            intents=ocr_intents,
            persons=ocr_persons,
            org=self.entity_pool.get_organization(ocr_domain),
            keywords=ocr_keywords,
            date=date
        )

        # Generate event with completely different content
        self.entity_pool.reset_used()  # Reset to get different entities
        event_persons = self.entity_pool.get_persons(2, event_domain)
        event_keywords = self.dict_loader.get_high_weight_terms(event_domain.value, 3)

        # Use DIFFERENT date for LOW_MATCH to avoid temporal matching
        # Offset by 3 days to ensure different weekday
        ocr_date_obj = datetime.strptime(date, "%Y-%m-%d")
        event_date = (ocr_date_obj + timedelta(days=3)).strftime("%Y-%m-%d")

        event = self.event_gen.generate_event_with_entities(
            domain=event_domain,
            persons=event_persons,
            org=self.entity_pool.get_organization(event_domain),
            topics=event_keywords,
            date=event_date  # Different date to avoid temporal matching
        )

        # Minimal/no match reasons
        match_reasons = []  # No significant matches expected

        min_affinity, max_affinity = AFFINITY_RANGES[RelationshipType.LOW_MATCH]

        relationship = SemanticRelationship(
            ocr_id=ocr_text.id,
            event_id=event.id,
            expected_affinity_min=min_affinity,
            expected_affinity_max=max_affinity,
            relationship_type=RelationshipType.LOW_MATCH.value,
            match_reasons=match_reasons
        )

        return ocr_text, event, relationship

    def generate_batch(
        self,
        high_count: int = 50,
        medium_count: int = 30,
        low_count: int = 50,
        domains: Optional[List[Domain]] = None,
        date_range_days: int = 30
    ) -> Tuple[List[GeneratedOcrText], List[GeneratedCalendarEvent], List[SemanticRelationship]]:
        """
        Generate batch of relationship pairs.

        Args:
            high_count: Number of high affinity pairs
            medium_count: Number of medium affinity pairs
            low_count: Number of low affinity pairs
            domains: Domains to use (FOCUS_DOMAINS if None)
            date_range_days: Days to spread across

        Returns:
            Tuple of (OCR records including event profiles, Event records, Relationships)
        """
        if domains is None:
            domains = FOCUS_DOMAINS

        ocr_records = []
        events = []
        relationships = []
        event_profiles = []  # Semantic profiles for events

        base_date = datetime.now()

        # Generate high affinity pairs
        for i in range(high_count):
            domain = random.choice(domains)
            days_offset = random.randint(-date_range_days, date_range_days)
            date = (base_date + timedelta(days=days_offset)).strftime("%Y-%m-%d")

            ocr, event, rel = self.generate_high_affinity_pair(domain, date)
            ocr_records.append(ocr)
            events.append(event)
            relationships.append(rel)
            # Create semantic profile for event
            event_profiles.append(self._create_event_profile(event))

        # Generate medium affinity pairs
        for i in range(medium_count):
            domain = random.choice(domains)
            days_offset = random.randint(-date_range_days, date_range_days)
            date = (base_date + timedelta(days=days_offset)).strftime("%Y-%m-%d")

            ocr, event, rel = self.generate_medium_affinity_pair(domain, date)
            ocr_records.append(ocr)
            events.append(event)
            relationships.append(rel)
            event_profiles.append(self._create_event_profile(event))

        # Generate low affinity pairs
        for i in range(low_count):
            domain = random.choice(domains)
            days_offset = random.randint(-date_range_days, date_range_days)
            date = (base_date + timedelta(days=days_offset)).strftime("%Y-%m-%d")

            ocr, event, rel = self.generate_low_affinity_pair(domain, date=date)
            ocr_records.append(ocr)
            events.append(event)
            relationships.append(rel)
            event_profiles.append(self._create_event_profile(event))

        # Combine OCR records with event profiles
        all_ocr_records = ocr_records + event_profiles

        return all_ocr_records, events, relationships

    def _generate_ocr_with_entities(
        self,
        domain: Domain,
        intents: List[Intent],
        persons: List[str],
        org: Optional[str],
        keywords: List[str],
        date: str,
        extra_phrases: List[str] = [],
        action_item: Optional[Tuple[str, str]] = None
    ) -> GeneratedOcrText:
        """Generate OCR record with specific entities embedded."""
        # Build text with entities
        text_parts = []

        # Add meeting header with persons
        primary_intent = intents[0] if intents else Intent.MEETING_NOTES

        if primary_intent == Intent.MEETING_NOTES:
            text_parts.append(f"Meeting with {', '.join(persons)}")
        elif primary_intent == Intent.LDS_COUNCIL:
            text_parts.append(f"Ward Council Meeting")
            text_parts.append(f"Attendees: {', '.join(persons)}")
        else:
            text_parts.append(f"Notes - {date}")
            text_parts.append(f"Participants: {', '.join(persons)}")

        # Always include organization if provided (for ORG entity matching)
        if org:
            text_parts.append(f"Organization: {org}")
            
        # Add Key Phrases
        if extra_phrases:
            text_parts.append(f"Key Points: {', '.join(extra_phrases)}")

        # Add topics/keywords
        if keywords:
            text_parts.append("\nTopics discussed:")
            for kw in keywords[:3]:
                text_parts.append(f"- {kw.replace('-', ' ').title()}")

        # Add action items
        text_parts.append("\nAction items:")
        
        # Specific injected action item
        if action_item:
            verb, obj = action_item
            text_parts.append(f"- [ ] {verb} {obj}")
            
        for person in persons[:2]:
            text_parts.append(f"- [ ] {person}: Follow up on {keywords[0] if keywords else 'project'}")

        # Add temporal signal with weekday name for matching
        weekday = get_weekday_name(date)
        text_parts.append(f"\nDate: {weekday}, {date}")
        text_parts.append(f"Next meeting: next week")

        text = "\n".join(text_parts)

        # Compute semantic bundle
        bundle = self.semantic_computer.compute(text, domain, intents, f"Notes - {date}")

        # Create unique record ID using hash to avoid collisions
        unique_hash = abs(hash(text)) % 1000000
        source_id = f"{date}_{unique_hash:06d}"
        record_id = f"MEMO_{source_id}_page1"

        return GeneratedOcrText(
            id=record_id,
            source_type="MEMO",
            source_id=source_id,
            page_id="page1",
            text=text,
            text_normalized=bundle["text_normalized"],
            clean_text=bundle["clean_text"],
            word_count=bundle["word_count"],
            confidence=0.92,
            doc_style=bundle["doc_style"],
            keywords_json=bundle["keywords_json"],
            key_phrases_json=bundle["key_phrases_json"],
            key_sentences_json=bundle["key_sentences_json"],
            entities_json=bundle["entities_json"],
            action_items_json=bundle["action_items_json"],
            temporal_signals_json=bundle["temporal_signals_json"],
            topics_json=bundle["topics_json"],
            synopsis=bundle["synopsis"],
            domain=bundle["domain"],
            domain_confidence=bundle["domain_confidence"],
            intents_json=bundle["intents_json"],
            source_title=f"Notes - {date}",
            source_date=date,
        )

    def _get_intents_for_domain(self, domain: Domain) -> List[Intent]:
        """Get appropriate intents for domain."""
        domain_intents = DOMAIN_INTENTS.get(domain, [Intent.MEETING_NOTES])
        count = random.randint(1, min(3, len(domain_intents)))
        return random.sample(domain_intents, count)

    def _create_event_profile(
        self,
        event: GeneratedCalendarEvent
    ) -> GeneratedOcrText:
        """
        Create a semantic profile for an event.

        This creates a unified_ocr_text record with sourceType=CALENDAR_EVENT
        so the test can load event profiles from the same table as OCR profiles.
        """
        # Build text from event - include date with weekday for temporal signal extraction
        text_parts = [event.title]
        if event.day_id:
            weekday = get_weekday_name(event.day_id)
            text_parts.append(f"Date: {weekday}, {event.day_id}")
        if event.description:
            text_parts.append(event.description)
        if event.location:
            text_parts.append(f"Location: {event.location}")
        text = "\n".join(text_parts)

        # Get domain from event (we added this field earlier)
        domain = Domain(event.domain) if event.domain else Domain.MISC
        intents = self._get_intents_for_domain(domain)

        # Compute semantic bundle
        bundle = self.semantic_computer.compute(text, domain, intents, event.title)

        # Create profile ID matching event ID
        profile_id = f"CALENDAR_EVENT_{event.id}_event"

        return GeneratedOcrText(
            id=profile_id,
            source_type="CALENDAR_EVENT",
            source_id=event.id,
            page_id="event",
            text=text,
            text_normalized=bundle["text_normalized"],
            clean_text=bundle["clean_text"],
            word_count=bundle["word_count"],
            confidence=0.95,
            doc_style=bundle["doc_style"],
            keywords_json=bundle["keywords_json"],
            key_phrases_json=bundle["key_phrases_json"],
            key_sentences_json=bundle["key_sentences_json"],
            entities_json=bundle["entities_json"],
            action_items_json=bundle["action_items_json"],
            temporal_signals_json=bundle["temporal_signals_json"],
            topics_json=bundle["topics_json"],
            synopsis=event.title,
            domain=domain.value,
            domain_confidence=0.85,
            intents_json=bundle["intents_json"],
            source_title=event.title,
            source_date=event.day_id,
        )


# Convenience function
def generate_relationship_batch(
    high: int = 50,
    medium: int = 30,
    low: int = 50,
    seed: Optional[int] = None
) -> Tuple[List[GeneratedOcrText], List[GeneratedCalendarEvent], List[SemanticRelationship]]:
    """
    Generate batch of relationship pairs.

    Args:
        high: Number of high affinity pairs
        medium: Number of medium affinity pairs
        low: Number of low affinity pairs
        seed: Random seed

    Returns:
        Tuple of (OCR records, Events, Relationships)
    """
    gen = RelationshipGenerator(seed=seed)
    return gen.generate_batch(high, medium, low)
