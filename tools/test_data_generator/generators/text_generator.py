"""
Text generator for creating realistic domain-specific content.

Generates text that will classify to specific domains and intents,
incorporating keywords, entities, and structural patterns.
"""

import random
from typing import List, Optional, Dict, Tuple
from datetime import datetime, timedelta

from ..config import Domain, Intent, DocumentStyle, DOMAIN_INTENTS, INTENT_DOC_STYLES
from ..dictionaries.loader import DictionaryLoader, get_loader
from ..dictionaries.entity_pools import EntityPool, get_pool


# Text templates by intent
MEETING_NOTES_TEMPLATES = [
    "Meeting with {person} - discussed {topic1} and {topic2}. Action items: {action1}, {action2}.",
    "{person} meeting notes:\n- {topic1}\n- {topic2}\n- Follow up: {action1}\n- Next steps: {action2}",
    "Weekly sync with {person}. Topics covered: {topic1}, {topic2}. Decisions: {decision}. Owner: {person2}",
    "1:1 with {person}\n\nDiscussed:\n- {topic1}\n- {topic2}\n\nAction items:\n- [ ] {action1}\n- [ ] {action2}",
    "Team meeting - {date}\nAttendees: {person}, {person2}\n\n{topic1}:\n- {detail1}\n- {detail2}\n\n{topic2}:\n- {detail3}\n\nNext meeting: {next_date}",
]

TODO_LIST_TEMPLATES = [
    "- [ ] {task1}\n- [ ] {task2}\n- [ ] {task3}\n- [ ] {task4}",
    "Tasks for {date}:\n1. {task1}\n2. {task2}\n3. {task3}",
    "TODO:\n- {task1} (priority: high)\n- {task2} (priority: medium)\n- {task3}\n- {task4}",
    "Weekly tasks:\n[ ] {task1}\n[ ] {task2}\n[ ] {task3}\n[ ] Review {topic1}",
]

PLANNING_TEMPLATES = [
    "Goals for {period}:\n1. {goal1}\n2. {goal2}\n3. {goal3}\n\nKey milestones:\n- {milestone1}\n- {milestone2}",
    "Q{quarter} Planning:\n\nObjectives:\n- {goal1}\n- {goal2}\n\nKey Results:\n- {kr1}\n- {kr2}\n\nOwner: {person}",
    "{topic1} Roadmap:\n\nPhase 1: {phase1}\nPhase 2: {phase2}\nTimeline: {timeline}",
]

JOURNAL_TEMPLATES = [
    "Today was {adjective}. Worked on {topic1}. Feeling {mood} about {topic2}.",
    "{date} - {mood} day. Accomplished: {task1}. Still need to work on {task2}. Grateful for {gratitude}.",
    "Reflection:\n\nWhat went well: {positive}\nWhat to improve: {improvement}\nLearnings: {learning}",
]

LDS_COUNCIL_TEMPLATES = [
    "Ward Council - {date}\n\nAttendees: {person}, {person2}, {person3}\n\nAgenda:\n1. {topic1}\n2. {topic2}\n3. Member needs discussion\n\nAction items:\n- {action1}\n- {action2}",
    "Bishopric Meeting\n\nDiscussed: {topic1}, {topic2}\nCallings: {calling}\nYouth concerns: {concern}\n\nPrayer by: {person}",
    "Stake Presidency Meeting\n\n{topic1}:\n- {detail1}\n- {detail2}\n\nTemple preparation:\n- {action1}\n\nMinistering update: {update}",
]

LDS_MINISTERING_TEMPLATES = [
    "Ministering visit with {person}\n\nFamily update: {update}\nNeeds discussed: {need}\nFollow up: {action1}",
    "Ministering report - {date}\n\nFamilies visited: {person}, {person2}\nConcerns: {concern}\nSupport provided: {support}",
]

LESSON_PLAN_TEMPLATES = [
    "Lesson: {topic1}\nDate: {date}\nClass: {class_name}\n\nObjectives:\n- Students will {objective1}\n- Students will {objective2}\n\nMaterials: {materials}\n\nActivities:\n1. {activity1}\n2. {activity2}\n\nAssessment: {assessment}",
    "{topic1} Curriculum\n\nWeek {week}: {subtopic1}\nWeek {week2}: {subtopic2}\n\nHomework: {homework}\nProject: {project}",
]

CODE_REVIEW_TEMPLATES = [
    "PR Review: {topic1}\n\nChanges:\n- {change1}\n- {change2}\n\nComments:\n- Consider refactoring {detail1}\n- Add tests for {detail2}\n\nApprover: {person}",
    "Code review notes for {topic1}:\n\n+ Good: {positive}\n- Needs work: {improvement}\n\nAction: {action1}",
]

EXPENSE_TEMPLATES = [
    "Expenses - {date}:\n\n{item1}: ${amount1}\n{item2}: ${amount2}\n{item3}: ${amount3}\n\nTotal: ${total}",
    "Budget tracking:\n\nCategory: {category}\nSpent: ${amount1}\nRemaining: ${amount2}\n\nNotes: {note}",
]

HEALTH_TEMPLATES = [
    "Symptoms today: {symptom1}, {symptom2}\nMedication: {medication}\nFeeling: {mood}\n\nDoctor appointment: {date}",
    "Exercise log - {date}:\n\nWorkout: {workout}\nDuration: {duration}\nCalories: {calories}\n\nNotes: {note}",
]


class TextGenerator:
    """
    Generate realistic text content for specific domains and intents.

    Usage:
        gen = TextGenerator()
        text = gen.generate(Domain.PROFESSIONAL, Intent.MEETING_NOTES)
    """

    def __init__(
        self,
        dictionary_loader: Optional[DictionaryLoader] = None,
        entity_pool: Optional[EntityPool] = None,
        seed: Optional[int] = None
    ):
        """
        Initialize text generator.

        Args:
            dictionary_loader: Optional custom dictionary loader
            entity_pool: Optional custom entity pool
            seed: Optional random seed for reproducibility
        """
        self.dict_loader = dictionary_loader or get_loader()
        self.entity_pool = entity_pool or get_pool(seed)

        if seed is not None:
            random.seed(seed)

    def generate(
        self,
        domain: Domain,
        intent: Intent,
        entities: Optional[Dict] = None,
        length: str = "medium",
        include_keywords: bool = True
    ) -> str:
        """
        Generate text for a specific domain and intent.

        Args:
            domain: Target domain classification
            intent: Target intent classification
            entities: Optional pre-defined entities to include
            length: "short", "medium", or "long"
            include_keywords: Whether to include domain keywords

        Returns:
            Generated text string
        """
        # Get template based on intent
        template = self._get_template(intent)

        # Generate entities if not provided
        if entities is None:
            entities = self._generate_entities(domain)

        # Get domain keywords
        keywords = []
        if include_keywords:
            keywords = self._get_domain_keywords(domain, count=5)

        # Fill template with entities and keywords
        text = self._fill_template(template, entities, keywords, domain)

        # Adjust length
        text = self._adjust_length(text, length)

        return text

    def generate_meeting_notes(
        self,
        domain: Domain,
        attendees: Optional[List[str]] = None,
        topics: Optional[List[str]] = None,
        action_items: int = 2
    ) -> str:
        """Generate realistic meeting notes text."""
        if attendees is None:
            attendees = self.entity_pool.get_persons(3, domain)
        if topics is None:
            topics = self._get_domain_keywords(domain, count=3)

        template = random.choice(MEETING_NOTES_TEMPLATES)
        entities = {
            "person": attendees[0] if attendees else "Team",
            "person2": attendees[1] if len(attendees) > 1 else "Group",
            "person3": attendees[2] if len(attendees) > 2 else "Others",
            "topic1": topics[0] if topics else "project updates",
            "topic2": topics[1] if len(topics) > 1 else "next steps",
            "action1": f"Review {topics[0]}" if topics else "Follow up",
            "action2": f"Update {topics[1]}" if len(topics) > 1 else "Schedule next meeting",
            "decision": f"Proceed with {topics[0]}" if topics else "Continue as planned",
            "date": self._get_date_str(),
            "next_date": self._get_date_str(days_ahead=7),
            "detail1": f"Discussed {topics[0]} progress" if topics else "Progress update",
            "detail2": f"Blockers for {topics[0]}" if topics else "Blockers identified",
            "detail3": f"Timeline for {topics[1]}" if len(topics) > 1 else "Timeline discussed",
        }

        return self._fill_template(template, entities, topics, domain)

    def generate_todo_list(
        self,
        domain: Domain,
        task_count: int = 5
    ) -> str:
        """Generate task list text."""
        keywords = self._get_domain_keywords(domain, count=task_count)
        tasks = [f"Complete {kw}" for kw in keywords[:task_count]]

        template = random.choice(TODO_LIST_TEMPLATES)
        entities = {
            "task1": tasks[0] if tasks else "Task 1",
            "task2": tasks[1] if len(tasks) > 1 else "Task 2",
            "task3": tasks[2] if len(tasks) > 2 else "Task 3",
            "task4": tasks[3] if len(tasks) > 3 else "Task 4",
            "topic1": keywords[0] if keywords else "project",
            "date": self._get_date_str(),
        }

        return self._fill_template(template, entities, keywords, domain)

    def generate_planning(
        self,
        domain: Domain,
        period: str = "Q1"
    ) -> str:
        """Generate planning/goals text."""
        keywords = self._get_domain_keywords(domain, count=5)
        person = self.entity_pool.get_person(domain)

        template = random.choice(PLANNING_TEMPLATES)
        entities = {
            "period": period,
            "quarter": period.replace("Q", "") if "Q" in period else "1",
            "goal1": f"Improve {keywords[0]}" if keywords else "Increase productivity",
            "goal2": f"Launch {keywords[1]}" if len(keywords) > 1 else "Complete project",
            "goal3": f"Optimize {keywords[2]}" if len(keywords) > 2 else "Streamline processes",
            "kr1": f"{keywords[0]} metric +20%" if keywords else "Key result 1",
            "kr2": f"Complete {keywords[1]} by end of quarter" if len(keywords) > 1 else "Key result 2",
            "milestone1": f"{keywords[0]} phase 1 complete" if keywords else "Milestone 1",
            "milestone2": f"{keywords[1]} beta launch" if len(keywords) > 1 else "Milestone 2",
            "topic1": keywords[0] if keywords else "Project",
            "phase1": "Requirements and design",
            "phase2": "Implementation and testing",
            "timeline": "8 weeks",
            "person": person,
        }

        return self._fill_template(template, entities, keywords, domain)

    def generate_journal(
        self,
        domain: Domain
    ) -> str:
        """Generate journal entry text."""
        keywords = self._get_domain_keywords(domain, count=3)

        adjectives = ["productive", "challenging", "interesting", "busy", "thoughtful"]
        moods = ["optimistic", "focused", "determined", "grateful", "reflective"]

        template = random.choice(JOURNAL_TEMPLATES)
        entities = {
            "adjective": random.choice(adjectives),
            "mood": random.choice(moods),
            "topic1": keywords[0] if keywords else "work",
            "topic2": keywords[1] if len(keywords) > 1 else "projects",
            "task1": f"Finished {keywords[0]}" if keywords else "Completed tasks",
            "task2": f"{keywords[1]} improvements" if len(keywords) > 1 else "Next steps",
            "gratitude": "supportive team",
            "positive": f"Made progress on {keywords[0]}" if keywords else "Good progress",
            "improvement": f"Better time management for {keywords[1]}" if len(keywords) > 1 else "Time management",
            "learning": f"New approach to {keywords[2]}" if len(keywords) > 2 else "New techniques",
            "date": self._get_date_str(),
        }

        return self._fill_template(template, entities, keywords, domain)

    def generate_lds_council(
        self,
        attendees: Optional[List[str]] = None
    ) -> str:
        """Generate LDS ward/stake council meeting notes."""
        if attendees is None:
            attendees = self.entity_pool.get_persons(4, Domain.LDS_LEADERSHIP)

        lds_topics = [
            "youth activities", "ministering", "temple preparation",
            "missionary work", "welfare needs", "callings",
            "sacrament meeting", "Sunday School", "ward coordination"
        ]
        topics = random.sample(lds_topics, 3)

        template = random.choice(LDS_COUNCIL_TEMPLATES)
        entities = {
            "person": attendees[0],
            "person2": attendees[1] if len(attendees) > 1 else "Bishop",
            "person3": attendees[2] if len(attendees) > 2 else "Counselor",
            "topic1": topics[0],
            "topic2": topics[1],
            "action1": f"Follow up on {topics[0]}",
            "action2": f"Prepare {topics[1]} report",
            "calling": "Primary teacher",
            "concern": "Youth participation",
            "detail1": f"Progress on {topics[0]}",
            "detail2": f"Plans for {topics[1]}",
            "update": "85% contacted this month",
            "date": self._get_date_str(),
        }

        return self._fill_template(template, entities, topics, Domain.LDS_LEADERSHIP)

    def generate_lesson_plan(
        self,
        subject: Optional[str] = None
    ) -> str:
        """Generate education lesson plan text."""
        if subject is None:
            subjects = ["Math", "Science", "History", "English", "Programming"]
            subject = random.choice(subjects)

        template = random.choice(LESSON_PLAN_TEMPLATES)
        entities = {
            "topic1": subject,
            "subtopic1": f"Introduction to {subject}",
            "subtopic2": f"Advanced {subject}",
            "class_name": "Period 3",
            "objective1": f"understand key {subject} concepts",
            "objective2": "apply knowledge to problems",
            "materials": "Textbook, whiteboard, worksheets",
            "activity1": "Group discussion",
            "activity2": "Practice problems",
            "assessment": "Quiz at end of unit",
            "homework": f"{subject} worksheet pages 45-48",
            "project": f"Research project on {subject}",
            "week": "1",
            "week2": "2",
            "date": self._get_date_str(),
        }

        return self._fill_template(template, entities, [subject], Domain.EDUCATION)

    def _get_template(self, intent: Intent) -> str:
        """Get appropriate template for intent."""
        templates = {
            Intent.MEETING_NOTES: MEETING_NOTES_TEMPLATES,
            Intent.TODO_LIST: TODO_LIST_TEMPLATES,
            Intent.PLANNING: PLANNING_TEMPLATES,
            Intent.GOAL_SETTING: PLANNING_TEMPLATES,
            Intent.JOURNAL: JOURNAL_TEMPLATES,
            Intent.DAILY_REVIEW: JOURNAL_TEMPLATES,
            Intent.LDS_COUNCIL: LDS_COUNCIL_TEMPLATES,
            Intent.LDS_MINISTERING: LDS_MINISTERING_TEMPLATES,
            Intent.LESSON_PLAN: LESSON_PLAN_TEMPLATES,
            Intent.CODE_REVIEW: CODE_REVIEW_TEMPLATES,
            Intent.EXPENSE_LOG: EXPENSE_TEMPLATES,
            Intent.BUDGET: EXPENSE_TEMPLATES,
            Intent.SYMPTOM_LOG: HEALTH_TEMPLATES,
            Intent.EXERCISE_LOG: HEALTH_TEMPLATES,
        }

        template_list = templates.get(intent, MEETING_NOTES_TEMPLATES)
        return random.choice(template_list)

    def _generate_entities(self, domain: Domain) -> Dict:
        """Generate default entities for a domain."""
        return {
            "person": self.entity_pool.get_person(domain),
            "person2": self.entity_pool.get_person(domain),
            "person3": self.entity_pool.get_person(domain),
            "org": self.entity_pool.get_organization(domain),
            "location": self.entity_pool.get_location(domain),
            "date": self._get_date_str(),
            "next_date": self._get_date_str(days_ahead=7),
        }

    def _get_domain_keywords(self, domain: Domain, count: int = 5) -> List[str]:
        """Get keywords for a domain from dictionaries."""
        terms = self.dict_loader.get_high_weight_terms(domain.value, count)
        if not terms:
            # Fallback generic terms
            fallback = {
                Domain.PROFESSIONAL: ["project", "meeting", "team", "deadline", "review"],
                Domain.BUSINESS: ["client", "sales", "revenue", "pipeline", "deal"],
                Domain.LDS_LEADERSHIP: ["ward", "calling", "ministering", "temple", "youth"],
                Domain.EDUCATION: ["lesson", "student", "curriculum", "assessment", "homework"],
                Domain.TECH: ["code", "deploy", "bug", "feature", "sprint"],
                Domain.HEALTH: ["symptoms", "medication", "exercise", "appointment", "wellness"],
                Domain.LIFE: ["family", "goals", "plans", "balance", "habits"],
                Domain.FINANCE: ["budget", "expenses", "savings", "investment", "taxes"],
            }
            terms = fallback.get(domain, ["task", "update", "review", "plan", "follow-up"])
        return terms[:count]

    def _fill_template(
        self,
        template: str,
        entities: Dict,
        keywords: List[str],
        domain: Domain
    ) -> str:
        """Fill template with entities and add keywords."""
        # Add keywords to entities for template filling
        for i, kw in enumerate(keywords):
            entities[f"keyword{i+1}"] = kw

        # Fill template
        try:
            text = template.format(**entities)
        except KeyError:
            # Handle missing keys gracefully
            text = template
            for key, value in entities.items():
                text = text.replace("{" + key + "}", str(value))

        # Ensure some domain keywords appear in text
        if keywords and not any(kw.lower() in text.lower() for kw in keywords[:2]):
            text += f"\n\nTopics: {', '.join(keywords[:3])}"

        return text

    def _adjust_length(self, text: str, length: str) -> str:
        """Adjust text length."""
        if length == "short":
            # Take first paragraph or ~100 chars
            lines = text.split("\n")
            return "\n".join(lines[:3])
        elif length == "long":
            # Add more content
            return text + "\n\nAdditional notes:\n- Follow up required\n- Schedule review meeting"
        return text

    def _get_date_str(self, days_ahead: int = 0) -> str:
        """Get date string in common format."""
        date = datetime.now() + timedelta(days=days_ahead)
        formats = [
            date.strftime("%B %d"),
            date.strftime("%m/%d"),
            date.strftime("%Y-%m-%d"),
        ]
        return random.choice(formats)

    def add_ocr_artifacts(self, text: str, level: str = "light") -> str:
        """
        Add realistic OCR-like artifacts to text.

        Args:
            text: Input text
            level: "light", "medium", or "heavy"

        Returns:
            Text with OCR artifacts
        """
        if level == "light":
            # Occasional substitutions
            substitutions = [
                ("rn", "m"),
                ("l", "1"),
                ("O", "0"),
            ]
            for old, new in substitutions:
                if random.random() < 0.1:
                    text = text.replace(old, new, 1)
        elif level == "medium":
            # More substitutions + whitespace issues
            text = self.add_ocr_artifacts(text, "light")
            if random.random() < 0.2:
                text = text.replace("  ", " ")
            if random.random() < 0.2:
                text = text.replace("\n", "\n\n")
        elif level == "heavy":
            # All of the above + case issues
            text = self.add_ocr_artifacts(text, "medium")
            if random.random() < 0.1:
                words = text.split()
                if words:
                    idx = random.randint(0, len(words) - 1)
                    words[idx] = words[idx].upper()
                    text = " ".join(words)

        return text


# Convenience function
def generate_text(
    domain: Domain,
    intent: Intent,
    seed: Optional[int] = None
) -> str:
    """
    Generate text for a specific domain and intent.

    Args:
        domain: Target domain classification
        intent: Target intent classification
        seed: Optional random seed

    Returns:
        Generated text string
    """
    gen = TextGenerator(seed=seed)
    return gen.generate(domain, intent)
