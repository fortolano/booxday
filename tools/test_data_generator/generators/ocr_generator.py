"""
OCR text record generator.

Generates complete UnifiedOcrText records for testing SemanticGeniusService.
"""

import random
import uuid
from datetime import datetime, timedelta
from typing import List, Optional, Dict

from ..config import Domain, Intent, OcrSource, FOCUS_DOMAINS, DOMAIN_INTENTS
from ..models import GeneratedOcrText
from .text_generator import TextGenerator
from .semantic_bundle import SemanticBundleComputer


class OcrGenerator:
    """
    Generate UnifiedOcrText records for testing.

    Creates Boox Notes, Memos, and Task page OCR records
    with full semantic bundles.
    """

    def __init__(self, seed: Optional[int] = None):
        """
        Initialize OCR generator.

        Args:
            seed: Optional random seed for reproducibility
        """
        self.text_gen = TextGenerator(seed=seed)
        self.semantic_computer = SemanticBundleComputer()

        if seed is not None:
            random.seed(seed)

    def generate_boox_note(
        self,
        domain: Domain,
        intents: Optional[List[Intent]] = None,
        note_id: Optional[str] = None,
        title: Optional[str] = None,
        date: Optional[str] = None,
        page_count: int = 1
    ) -> List[GeneratedOcrText]:
        """
        Generate Boox Note OCR records.

        Args:
            domain: Target domain classification
            intents: Target intents (auto-selected if None)
            note_id: Custom note ID (auto-generated if None)
            title: Note title (auto-generated if None)
            date: Note date (today if None)
            page_count: Number of pages to generate

        Returns:
            List of GeneratedOcrText records
        """
        if intents is None:
            intents = self._get_intents_for_domain(domain)
        if note_id is None:
            note_id = f"note_{uuid.uuid4().hex[:8]}"
        if title is None:
            title = self._generate_title(domain, intents[0] if intents else None)
        if date is None:
            date = datetime.now().strftime("%Y-%m-%d")

        records = []
        for page_idx in range(page_count):
            page_id = f"page{page_idx + 1}"

            # Generate text for this page
            text = self.text_gen.generate(domain, intents[0] if intents else Intent.MEETING_NOTES)

            # Add variation for multi-page notes
            if page_count > 1 and page_idx > 0:
                text = f"(continued)\n\n{text}"

            # Compute semantic bundle
            bundle = self.semantic_computer.compute(text, domain, intents, title)

            # Create record
            record_id = f"{OcrSource.BOOX_NOTE.value}_{note_id}_{page_id}"
            record = GeneratedOcrText(
                id=record_id,
                source_type=OcrSource.BOOX_NOTE.value,
                source_id=note_id,
                page_id=page_id,
                text=text,
                text_normalized=bundle["text_normalized"],
                clean_text=bundle["clean_text"],
                word_count=bundle["word_count"],
                confidence=0.92 + random.random() * 0.06,
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
                source_title=title,
                source_path=f"/storage/Note/{note_id}",
                source_date=date,
            )
            records.append(record)

        return records

    def generate_memo(
        self,
        domain: Domain,
        intents: Optional[List[Intent]] = None,
        date: Optional[str] = None,
        page_id: str = "page1"
    ) -> GeneratedOcrText:
        """
        Generate memo OCR record.

        Args:
            domain: Target domain classification
            intents: Target intents (auto-selected if None)
            date: Memo date (today if None)
            page_id: Page identifier

        Returns:
            GeneratedOcrText record
        """
        if intents is None:
            intents = self._get_intents_for_domain(domain)
        if date is None:
            date = datetime.now().strftime("%Y-%m-%d")

        # Generate text based on intent
        primary_intent = intents[0] if intents else Intent.MEETING_NOTES
        if primary_intent == Intent.MEETING_NOTES:
            text = self.text_gen.generate_meeting_notes(domain)
        elif primary_intent == Intent.TODO_LIST:
            text = self.text_gen.generate_todo_list(domain)
        elif primary_intent in [Intent.PLANNING, Intent.GOAL_SETTING]:
            text = self.text_gen.generate_planning(domain)
        elif primary_intent == Intent.JOURNAL:
            text = self.text_gen.generate_journal(domain)
        elif primary_intent == Intent.LDS_COUNCIL:
            text = self.text_gen.generate_lds_council()
        elif primary_intent == Intent.LESSON_PLAN:
            text = self.text_gen.generate_lesson_plan()
        else:
            text = self.text_gen.generate(domain, primary_intent)

        title = f"Memo - {date}"

        # Compute semantic bundle
        bundle = self.semantic_computer.compute(text, domain, intents, title)

        # Create record
        record_id = f"{OcrSource.MEMO.value}_{date}_{page_id}"
        return GeneratedOcrText(
            id=record_id,
            source_type=OcrSource.MEMO.value,
            source_id=date,
            page_id=page_id,
            text=text,
            text_normalized=bundle["text_normalized"],
            clean_text=bundle["clean_text"],
            word_count=bundle["word_count"],
            confidence=0.90 + random.random() * 0.08,
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
            source_title=title,
            source_date=date,
        )

    def generate_task_page(
        self,
        domain: Domain,
        date: Optional[str] = None,
        task_count: int = 5
    ) -> GeneratedOcrText:
        """
        Generate task page OCR record.

        Args:
            domain: Target domain classification
            date: Task date (today if None)
            task_count: Number of tasks to generate

        Returns:
            GeneratedOcrText record
        """
        if date is None:
            date = datetime.now().strftime("%Y-%m-%d")

        # Generate task list text
        text = self.text_gen.generate_todo_list(domain, task_count)

        intents = [Intent.TODO_LIST]
        title = f"Tasks - {date}"

        # Compute semantic bundle
        bundle = self.semantic_computer.compute(text, domain, intents, title)

        # Create record
        record_id = f"{OcrSource.TASK.value}_{date}_page1"
        return GeneratedOcrText(
            id=record_id,
            source_type=OcrSource.TASK.value,
            source_id=date,
            page_id="page1",
            text=text,
            text_normalized=bundle["text_normalized"],
            clean_text=bundle["clean_text"],
            word_count=bundle["word_count"],
            confidence=0.88 + random.random() * 0.10,
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
            source_title=title,
            source_date=date,
        )

    def generate_batch(
        self,
        count: int,
        domains: Optional[List[Domain]] = None,
        source_types: Optional[List[OcrSource]] = None,
        date_range_days: int = 30
    ) -> List[GeneratedOcrText]:
        """
        Generate batch of OCR records.

        Args:
            count: Number of records to generate
            domains: Domains to use (FOCUS_DOMAINS if None)
            source_types: Source types (all if None)
            date_range_days: Days to spread records across

        Returns:
            List of GeneratedOcrText records
        """
        if domains is None:
            domains = FOCUS_DOMAINS
        if source_types is None:
            source_types = [OcrSource.BOOX_NOTE, OcrSource.MEMO, OcrSource.TASK]

        records = []
        base_date = datetime.now()

        for i in range(count):
            # Select random domain and source type
            domain = random.choice(domains)
            source_type = random.choice(source_types)

            # Generate random date within range
            days_offset = random.randint(-date_range_days, 0)
            date = (base_date + timedelta(days=days_offset)).strftime("%Y-%m-%d")

            # Generate record based on source type
            if source_type == OcrSource.BOOX_NOTE:
                note_records = self.generate_boox_note(
                    domain=domain,
                    date=date,
                    page_count=random.randint(1, 3)
                )
                records.extend(note_records)
            elif source_type == OcrSource.MEMO:
                records.append(self.generate_memo(domain=domain, date=date))
            elif source_type == OcrSource.TASK:
                records.append(self.generate_task_page(domain=domain, date=date))

        return records[:count]  # Trim to exact count

    def _get_intents_for_domain(self, domain: Domain) -> List[Intent]:
        """Get appropriate intents for a domain."""
        domain_intents = DOMAIN_INTENTS.get(domain, [Intent.MEETING_NOTES, Intent.TODO_LIST])
        # Select 1-3 intents
        count = random.randint(1, min(3, len(domain_intents)))
        return random.sample(domain_intents, count)

    def _generate_title(self, domain: Domain, intent: Optional[Intent]) -> str:
        """Generate appropriate title for domain/intent."""
        titles = {
            Domain.PROFESSIONAL: [
                "Team Meeting Notes", "Project Update", "Weekly Sync",
                "1:1 Discussion", "Sprint Planning", "Retrospective",
            ],
            Domain.BUSINESS: [
                "Client Meeting", "Sales Review", "Pipeline Discussion",
                "Account Planning", "Deal Review", "Customer Success",
            ],
            Domain.LDS_LEADERSHIP: [
                "Ward Council", "Bishopric Meeting", "Ministering Notes",
                "Temple Planning", "Youth Activity", "Presidency Meeting",
            ],
            Domain.SPIRITUAL: [
                "Scripture Study", "Personal Reflection", "Prayer Journal",
                "Talk Preparation", "Gospel Notes",
            ],
            Domain.EDUCATION: [
                "Lesson Plan", "Student Notes", "Curriculum Planning",
                "Class Observations", "Grading Notes", "Course Outline",
            ],
            Domain.TECH: [
                "Code Review", "Architecture Discussion", "Bug Investigation",
                "Sprint Notes", "Tech Debt Review", "Deployment Plan",
            ],
            Domain.HEALTH: [
                "Health Log", "Appointment Notes", "Symptom Tracker",
                "Exercise Plan", "Wellness Notes", "Medical Visit",
            ],
            Domain.LIFE: [
                "Daily Journal", "Goals & Plans", "Personal Notes",
                "Life Review", "Weekly Reflection",
            ],
            Domain.FINANCE: [
                "Budget Review", "Expense Log", "Financial Planning",
                "Investment Notes", "Tax Prep",
            ],
            Domain.CREATIVE: [
                "Writing Notes", "Design Ideas", "Brainstorm Session",
                "Creative Journal", "Project Concept",
            ],
        }

        domain_titles = titles.get(domain, ["Notes"])
        return random.choice(domain_titles)


# Convenience function
def generate_ocr_batch(
    count: int,
    domains: Optional[List[Domain]] = None,
    seed: Optional[int] = None
) -> List[GeneratedOcrText]:
    """
    Generate batch of OCR records.

    Args:
        count: Number of records to generate
        domains: Domains to use (FOCUS_DOMAINS if None)
        seed: Optional random seed

    Returns:
        List of GeneratedOcrText records
    """
    gen = OcrGenerator(seed=seed)
    return gen.generate_batch(count, domains)
