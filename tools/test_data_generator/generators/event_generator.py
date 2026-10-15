"""
Calendar event generator.

Generates CalendarEvent and LocalEvent records for testing semantic matching.
"""

import random
import uuid
import json
from datetime import datetime, timedelta
from typing import List, Optional, Dict

from ..config import Domain, Intent, FOCUS_DOMAINS
from ..models import GeneratedCalendarEvent, GeneratedLocalEvent
from ..dictionaries.entity_pools import EntityPool, get_pool


# Event title templates by domain
TITLE_TEMPLATES: Dict[Domain, List[str]] = {
    Domain.PROFESSIONAL: [
        "1:1 with {person}",
        "Team standup",
        "Sprint planning",
        "Sprint retrospective",
        "Weekly sync",
        "{person} - Performance review",
        "All hands meeting",
        "Project kickoff: {topic}",
        "Design review",
        "Tech sync",
        "Career discussion with {person}",
        "Quarterly planning",
    ],
    Domain.BUSINESS: [
        "Client meeting: {org}",
        "Sales pipeline review",
        "Account planning: {org}",
        "{person} - Deal review",
        "QBR with {org}",
        "Contract negotiation",
        "Partner meeting: {org}",
        "Customer success check-in",
        "Proposal review",
        "Investor update",
        "Board meeting",
    ],
    Domain.LDS_LEADERSHIP: [
        "Ward council",
        "Bishopric meeting",
        "Stake presidency meeting",
        "Temple recommend interview - {person}",
        "Ministering interview",
        "Youth fireside",
        "Relief Society meeting",
        "Elders quorum presidency",
        "Primary meeting",
        "Sunday School council",
        "Welfare meeting",
        "Tithing settlement - {person}",
        "Ward conference prep",
    ],
    Domain.SPIRITUAL: [
        "Scripture study group",
        "Prayer meeting",
        "Bible study",
        "Spiritual retreat",
        "Church service",
        "Meditation session",
        "Faith discussion",
        "Ministry meeting",
    ],
    Domain.EDUCATION: [
        "Lecture: {topic}",
        "Office hours",
        "Faculty meeting",
        "Parent-teacher conference - {person}",
        "Curriculum review",
        "Grading session",
        "Department meeting",
        "Student advising - {person}",
        "Academic council",
        "Research presentation",
        "Thesis committee meeting",
    ],
    Domain.TECH: [
        "Code review session",
        "Architecture review",
        "Sprint demo",
        "Bug bash",
        "Tech debt discussion",
        "Security review",
        "Deployment planning",
        "Infrastructure sync",
        "API design review",
        "Performance review: {topic}",
    ],
    Domain.HEALTH: [
        "Doctor appointment",
        "Physical therapy",
        "Dentist appointment",
        "Annual checkup",
        "Lab work",
        "Specialist consultation",
        "Follow-up appointment",
        "Vaccination appointment",
        "Mental health check-in",
    ],
    Domain.LIFE: [
        "Family dinner",
        "Birthday party",
        "Home maintenance",
        "Car service",
        "Grocery shopping",
        "Date night",
        "Weekend plans",
        "House cleaning",
        "Personal time",
    ],
    Domain.FINANCE: [
        "Financial advisor meeting",
        "Tax prep appointment",
        "Budget review",
        "Investment review",
        "Insurance meeting",
        "Bank appointment",
        "Retirement planning",
        "Estate planning",
    ],
    Domain.CREATIVE: [
        "Writing workshop",
        "Art class",
        "Music lesson",
        "Creative brainstorm",
        "Gallery visit",
        "Photography session",
        "Design review",
        "Portfolio review",
    ],
    Domain.TRAVEL: [
        "Flight to {topic}",
        "Hotel check-in",
        "Car rental pickup",
        "Sightseeing: {topic}",
        "Restaurant reservation",
        "Airport transfer",
        "Coworking session",
        "Local tour",
        "Travel day",
        "Departure: {topic}",
    ],
    Domain.TEACHING: [
        "Class: {topic}",
        "Parent-teacher conference - {person}",
        "Staff meeting",
        "Lesson planning",
        "Grading time",
        "PTA meeting",
        "Field trip",
        "School assembly",
        "Department meeting",
        "Professional development",
        "IEP meeting - {person}",
    ],
}

# Description templates
DESCRIPTION_TEMPLATES: Dict[Domain, List[str]] = {
    Domain.PROFESSIONAL: [
        "Discuss {topic1} and {topic2}. Review progress on current initiatives.",
        "Weekly sync to cover:\n- Project updates\n- Blockers\n- Action items from last week",
        "Performance discussion with {person}. Topics: goals progress, development areas, feedback.",
        "Sprint planning session. Review backlog, estimate stories, commit to sprint goals.",
        "Agenda:\n1. {topic1}\n2. {topic2}\n3. Open discussion\n\nAction items from last meeting to review.",
    ],
    Domain.BUSINESS: [
        "Client meeting agenda:\n- Account review\n- Renewal discussion\n- Expansion opportunities",
        "Quarterly business review with {org}. Prepare ROI metrics and success stories.",
        "Sales pipeline review. Focus on deals closing this quarter.",
        "Contract discussion with {person}. Key terms to negotiate: pricing, timeline, SLA.",
        "Partner meeting to discuss joint opportunities and integration roadmap.",
    ],
    Domain.LDS_LEADERSHIP: [
        "Ward council agenda:\n- Member needs\n- Youth activities\n- Upcoming events\n\nPrayer assignments to follow.",
        "Bishopric meeting to discuss callings, interviews, and ward coordination.",
        "Ministering coordination. Review assignments and discuss families needing support.",
        "Temple recommend interview preparation. Review worthiness questions.",
        "Youth activity planning. Budget: $200. Need volunteers for supervision.",
    ],
    Domain.EDUCATION: [
        "Office hours for students. Focus areas: midterm review, project questions.",
        "Faculty meeting agenda:\n- Curriculum updates\n- Assessment methods\n- Student concerns",
        "Parent-teacher conference with {person}. Discuss student progress and areas for improvement.",
        "Research presentation: {topic}. Prepare slides and handouts.",
    ],
    Domain.TECH: [
        "Code review session for {topic}. Focus on architecture decisions and test coverage.",
        "Sprint demo. Teams presenting:\n- Frontend: new features\n- Backend: API improvements\n- DevOps: deployment updates",
        "Architecture review for {topic}. Evaluate scalability and security considerations.",
        "Bug bash session. Priority: P0 bugs before release.",
    ],
    Domain.TRAVEL: [
        "Flight details:\n- Departure: {topic}\n- Check-in: 2 hours before\n- Gate info TBD",
        "Itinerary:\n- Arrive at hotel\n- Check-in\n- Explore local area\n- Dinner reservations",
        "Coworking session at {org}. WiFi password provided on arrival.",
        "Day trip to {topic}. Meeting point: hotel lobby. Bring comfortable shoes.",
    ],
    Domain.TEACHING: [
        "Lesson plan for {topic}:\n- Learning objectives\n- Activities\n- Assessment",
        "Parent conference with {person}:\n- Academic progress\n- Behavior\n- Goals for next term",
        "Staff meeting agenda:\n- Announcements\n- Curriculum updates\n- Student concerns",
        "Grading session. Papers to grade: {topic}. Due back to students by Friday.",
    ],
}

# Event colors by domain
DOMAIN_COLORS: Dict[Domain, List[str]] = {
    Domain.PROFESSIONAL: ["#4285F4", "#039BE5", "#3F51B5"],  # Blue
    Domain.BUSINESS: ["#0B8043", "#33B679", "#7CB342"],  # Green
    Domain.LDS_LEADERSHIP: ["#8E24AA", "#7B1FA2", "#9C27B0"],  # Purple
    Domain.SPIRITUAL: ["#8E24AA", "#9C27B0"],  # Purple
    Domain.EDUCATION: ["#F6BF26", "#F09300", "#E4C441"],  # Yellow
    Domain.TEACHING: ["#F6BF26", "#E4C441", "#FDD835"],  # Yellow (same as education)
    Domain.TECH: ["#039BE5", "#0097A7", "#00ACC1"],  # Cyan
    Domain.HEALTH: ["#D50000", "#F4511E", "#E67C73"],  # Red
    Domain.LIFE: ["#616161", "#757575"],  # Gray
    Domain.FINANCE: ["#0B8043", "#33B679"],  # Green
    Domain.CREATIVE: ["#F4511E", "#FF7043", "#EF6C00"],  # Orange
    Domain.TRAVEL: ["#1E88E5", "#42A5F5", "#64B5F6"],  # Light blue
}


class EventGenerator:
    """
    Generate calendar event records for testing.

    Creates CalendarEvent and LocalEvent records with
    realistic titles, descriptions, and attendees.
    """

    def __init__(self, seed: Optional[int] = None):
        """
        Initialize event generator.

        Args:
            seed: Optional random seed for reproducibility
        """
        self.entity_pool = get_pool(seed)

        if seed is not None:
            random.seed(seed)

    def generate_event(
        self,
        domain: Domain,
        date: Optional[str] = None,
        with_description: bool = True,
        with_attendees: bool = True,
        with_location: bool = True,
        is_all_day: bool = False
    ) -> GeneratedCalendarEvent:
        """
        Generate a calendar event.

        Args:
            domain: Event domain
            date: Event date (today if None)
            with_description: Include description
            with_attendees: Include attendees
            with_location: Include location
            is_all_day: Create all-day event

        Returns:
            GeneratedCalendarEvent record
        """
        if date is None:
            date = datetime.now().strftime("%Y-%m-%d")

        # Generate title
        title = self._generate_title(domain)

        # Generate times
        start_time, end_time = self._generate_times(date, is_all_day)

        # Generate optional fields
        description = None
        if with_description:
            description = self._generate_description(domain, title)

        location = None
        if with_location:
            location = self.entity_pool.get_meeting_location(domain)

        attendees = None
        if with_attendees:
            attendee_list = self.entity_pool.get_attendee_list(
                random.randint(2, 5), domain
            )
            attendees = json.dumps([a["email"] for a in attendee_list])

        color = random.choice(DOMAIN_COLORS.get(domain, ["#4285F4"]))

        event_id = f"evt_{uuid.uuid4().hex[:12]}"

        return GeneratedCalendarEvent(
            id=event_id,
            provider_id="GOOGLE",
            account_id="test_account_1",
            account_email="test@example.com",
            calendar_id="primary",
            calendar_name="Primary Calendar",
            title=title,
            day_id=date,
            start_time=start_time,
            end_time=end_time,
            is_all_day=is_all_day,
            location=location,
            description=description,
            attendees=attendees,
            is_read_only=False,
            color_hex=color,
        )

    def generate_local_event(
        self,
        domain: Domain,
        date: Optional[str] = None,
        with_description: bool = True,
        sync_enabled: bool = False
    ) -> GeneratedLocalEvent:
        """
        Generate a local event.

        Args:
            domain: Event domain
            date: Event date (today if None)
            with_description: Include description
            sync_enabled: Enable sync

        Returns:
            GeneratedLocalEvent record
        """
        if date is None:
            date = datetime.now().strftime("%Y-%m-%d")

        title = self._generate_title(domain)
        start_time, end_time = self._generate_times(date, False)

        description = None
        if with_description:
            description = self._generate_description(domain, title)

        location = self.entity_pool.get_meeting_location(domain)
        color = random.choice(DOMAIN_COLORS.get(domain, ["#4285F4"]))

        event_id = f"local_{uuid.uuid4().hex[:12]}"

        return GeneratedLocalEvent(
            id=event_id,
            title=title,
            day_id=date,
            start_time=start_time,
            end_time=end_time,
            is_all_day=False,
            location=location,
            description=description,
            color_hex=color,
            attendees=None,
            sync_enabled=sync_enabled,
            sync_status="LOCAL_ONLY",
        )

    def generate_event_with_entities(
        self,
        domain: Domain,
        persons: List[str],
        org: Optional[str] = None,
        topics: Optional[List[str]] = None,
        date: Optional[str] = None
    ) -> GeneratedCalendarEvent:
        """
        Generate event with specific entities.

        Used for creating events that should match specific OCR content.

        Args:
            domain: Event domain
            persons: Person names to include
            org: Organization name to include
            topics: Topics to include in description
            date: Event date

        Returns:
            GeneratedCalendarEvent with specified entities
        """
        if date is None:
            date = datetime.now().strftime("%Y-%m-%d")

        # Build title with entities
        templates = TITLE_TEMPLATES.get(domain, ["Meeting"])
        template = random.choice(templates)

        title = template.format(
            person=persons[0] if persons else "Team",
            org=org or "Partner",
            topic=topics[0] if topics else "Project"
        )

        start_time, end_time = self._generate_times(date, False)

        # Build description with entities - include keywords multiple times
        # to improve keyword extraction overlap with OCR text
        description_parts = []
        if persons:
            description_parts.append(f"Attendees: {', '.join(persons)}")
        if org:
            description_parts.append(f"Organization: {org}")
        if topics:
            # Include topics in multiple formats for better keyword extraction
            description_parts.append(f"Topics: {', '.join(topics)}")

        description_parts.append("\nAgenda:")
        if topics:
            for i, topic in enumerate(topics[:3], 1):
                # Use topic in natural sentence form
                description_parts.append(f"{i}. Discuss {topic}")
            # Add action items that reference topics (improves keyword overlap)
            description_parts.append("\nAction items:")
            for i, topic in enumerate(topics[:2], 1):
                if persons and len(persons) > i - 1:
                    description_parts.append(f"- {persons[i-1]}: Follow up on {topic}")
                else:
                    description_parts.append(f"- Review {topic} progress")
        else:
            description_parts.append("1. Project updates")
            description_parts.append("2. Action items")

        description = "\n".join(description_parts)

        # Attendees
        attendees = json.dumps([
            f"{p.split()[0].lower()}.{p.split()[-1].lower()}@example.com"
            for p in persons
        ]) if persons else None

        location = self.entity_pool.get_meeting_location(domain)
        color = random.choice(DOMAIN_COLORS.get(domain, ["#4285F4"]))

        event_id = f"evt_{uuid.uuid4().hex[:12]}"

        return GeneratedCalendarEvent(
            id=event_id,
            provider_id="GOOGLE",
            account_id="test_account_1",
            account_email="test@example.com",
            calendar_id="primary",
            calendar_name="Primary Calendar",
            title=title,
            day_id=date,
            start_time=start_time,
            end_time=end_time,
            is_all_day=False,
            location=location,
            description=description,
            attendees=attendees,
            is_read_only=False,
            color_hex=color,
            domain=domain.value,
        )

    def generate_batch(
        self,
        count: int,
        domains: Optional[List[Domain]] = None,
        date_range_days: int = 30,
        include_local: bool = False
    ) -> List[GeneratedCalendarEvent]:
        """
        Generate batch of calendar events.

        Args:
            count: Number of events to generate
            domains: Domains to use (FOCUS_DOMAINS if None)
            date_range_days: Days to spread events across
            include_local: Include local events (mixed types)

        Returns:
            List of GeneratedCalendarEvent records
        """
        if domains is None:
            domains = FOCUS_DOMAINS

        events = []
        base_date = datetime.now()

        for i in range(count):
            domain = random.choice(domains)

            # Generate date within range
            days_offset = random.randint(-date_range_days, date_range_days)
            date = (base_date + timedelta(days=days_offset)).strftime("%Y-%m-%d")

            # Random event type
            is_all_day = random.random() < 0.1  # 10% all-day events

            event = self.generate_event(
                domain=domain,
                date=date,
                with_description=random.random() < 0.8,  # 80% have descriptions
                with_attendees=random.random() < 0.7,  # 70% have attendees
                with_location=random.random() < 0.6,  # 60% have locations
                is_all_day=is_all_day,
            )
            events.append(event)

        return events

    def _generate_title(self, domain: Domain) -> str:
        """Generate event title for domain."""
        templates = TITLE_TEMPLATES.get(domain, ["Meeting"])
        template = random.choice(templates)

        # Fill placeholders
        return template.format(
            person=self.entity_pool.get_person(domain),
            org=self.entity_pool.get_organization(domain),
            topic=self._get_domain_topic(domain),
        )

    def _generate_description(self, domain: Domain, title: str) -> str:
        """Generate event description."""
        templates = DESCRIPTION_TEMPLATES.get(domain, [])
        if not templates:
            return f"Meeting: {title}\n\nPlease come prepared."

        template = random.choice(templates)

        person = self.entity_pool.get_person(domain)
        org = self.entity_pool.get_organization(domain)
        topic1 = self._get_domain_topic(domain)
        topic2 = self._get_domain_topic(domain)

        return template.format(
            person=person,
            org=org,
            topic=topic1,
            topic1=topic1,
            topic2=topic2,
        )

    def _generate_times(
        self,
        date: str,
        is_all_day: bool
    ) -> tuple:
        """Generate start and end times."""
        # Parse date
        date_obj = datetime.strptime(date, "%Y-%m-%d")

        if is_all_day:
            # All-day event: midnight to midnight
            start = int(date_obj.timestamp() * 1000)
            return start, None

        # Regular event: random time between 8am and 5pm
        hour = random.randint(8, 16)
        minute = random.choice([0, 15, 30, 45])
        duration_minutes = random.choice([30, 45, 60, 90, 120])

        start_dt = date_obj.replace(hour=hour, minute=minute)
        end_dt = start_dt + timedelta(minutes=duration_minutes)

        start = int(start_dt.timestamp() * 1000)
        end = int(end_dt.timestamp() * 1000)

        return start, end

    def _get_domain_topic(self, domain: Domain) -> str:
        """Get a topic appropriate for domain."""
        topics = {
            Domain.PROFESSIONAL: [
                "project updates", "roadmap", "sprint goals", "team velocity",
                "hiring plans", "process improvements", "OKRs",
            ],
            Domain.BUSINESS: [
                "account strategy", "revenue targets", "market expansion",
                "competitive landscape", "customer retention", "pricing",
            ],
            Domain.LDS_LEADERSHIP: [
                "member needs", "youth activities", "temple preparation",
                "missionary work", "ministering assignments", "callings",
            ],
            Domain.EDUCATION: [
                "curriculum development", "student assessment", "course materials",
                "research progress", "academic standards", "pedagogy",
            ],
            Domain.TEACHING: [
                "math", "reading", "science", "social studies", "art",
                "PE", "music", "homework review", "parent communication",
                "behavior management", "assessment", "differentiation",
            ],
            Domain.TECH: [
                "system architecture", "code quality", "technical debt",
                "security review", "performance optimization", "API design",
            ],
            Domain.TRAVEL: [
                "Tokyo", "Paris", "London", "New York", "Barcelona",
                "Bali", "Berlin", "Amsterdam", "Sydney", "Singapore",
                "remote work", "coworking", "flight", "hotel",
            ],
            Domain.CREATIVE: [
                "novel draft", "painting series", "music composition",
                "portfolio", "client project", "exhibition", "photoshoot",
            ],
        }
        domain_topics = topics.get(domain, ["project", "updates", "planning"])
        return random.choice(domain_topics)


# Convenience function
def generate_event_batch(
    count: int,
    domains: Optional[List[Domain]] = None,
    seed: Optional[int] = None
) -> List[GeneratedCalendarEvent]:
    """
    Generate batch of calendar events.

    Args:
        count: Number of events
        domains: Domains to use
        seed: Random seed

    Returns:
        List of events
    """
    gen = EventGenerator(seed=seed)
    return gen.generate_batch(count, domains)
