"""
Entity pools for generating realistic names, organizations, and locations.

These pools provide domain-specific entities for test data generation.
"""

import random
from typing import List, Optional, Dict
from ..config import Domain


# Person name pools by context - EXPANDED for diverse user types
PERSON_NAMES: Dict[str, List[str]] = {
    "common": [
        # Diverse common names
        "John Smith", "Sarah Chen", "Michael Roberts", "Emily Johnson",
        "David Kim", "Jessica Williams", "James Anderson", "Amanda Martinez",
        "Robert Taylor", "Jennifer Brown", "Christopher Lee", "Michelle Davis",
        "Matthew Wilson", "Ashley Garcia", "Daniel Thomas", "Stephanie Moore",
        "Andrew Jackson", "Nicole White", "Joshua Harris", "Elizabeth Martin",
        "Ryan Thompson", "Samantha Clark", "Kevin Lewis", "Lauren Robinson",
        "Brian Walker", "Rachel Hall", "Jason Young", "Megan Allen",
        "Justin King", "Heather Wright", "Brandon Scott", "Amber Hill",
        "Carlos Rodriguez", "Maria Santos", "Wei Zhang", "Priya Patel",
        "Ahmed Hassan", "Yuki Tanaka", "Sofia Petrov", "Lars Jensen",
        "Anna Schmidt", "Pedro Gonzalez", "Nina Kowalski", "Omar Farouk",
    ],
    "professional": [
        # PM, Engineering, Design, Marketing, Sales roles
        "Sarah Chen (PM)", "Michael Roberts (Eng)", "Emily Johnson (Design)",
        "David Kim (QA)", "Jessica Williams (DevOps)", "James Anderson (Lead)",
        "Amanda Martinez (Director)", "Robert Taylor (VP)", "Jennifer Brown (CTO)",
        "Chris Lee (Sr. Engineer)", "Matt Wilson (Product)", "Ashley Garcia (UX)",
        "Dan Thomas (Architect)", "Rachel Hall (Manager)", "Kevin Lewis (Analyst)",
        "Maria Santos (Marketing)", "Carlos Rodriguez (Sales)", "Wei Zhang (Data)",
        "Priya Patel (Growth)", "Ahmed Hassan (Operations)", "Sofia Petrov (HR)",
    ],
    "lds": [
        "President Anderson", "Bishop Williams", "Elder Johnson", "Sister Martinez",
        "Brother Taylor", "Sister Chen", "Bishop Roberts", "President Kim",
        "Elder Smith", "Sister Davis", "Brother Wilson", "President Brown",
        "Elder Thompson", "Sister Clark", "Brother Garcia", "Elder Hall",
        "Sister Lewis", "Bishop Moore", "President Harris", "Elder White",
    ],
    "education": [
        # Teachers, professors, administrators
        "Professor Smith", "Dr. Martinez", "Professor Chen", "Dr. Williams",
        "Professor Johnson", "Dr. Taylor", "Professor Kim", "Dr. Anderson",
        "Professor Brown", "Dr. Davis", "Professor Wilson", "Dr. Garcia",
        "Professor Lee", "Dr. Thomas", "Professor Hall", "Dr. Moore",
        "Dean Roberts", "Principal Adams", "Teacher Jones", "Instructor Park",
        "Coach Miller", "Counselor White", "Librarian Green", "TA Rodriguez",
    ],
    "teaching": [
        # K-12 focused teaching names
        "Ms. Johnson", "Mr. Smith", "Mrs. Williams", "Mr. Chen",
        "Ms. Martinez", "Mrs. Taylor", "Mr. Anderson", "Ms. Brown",
        "Mrs. Davis", "Mr. Wilson", "Ms. Garcia", "Mr. Thomas",
        "Principal Adams", "Vice Principal Lee", "Counselor White",
        "Coach Miller", "Librarian Green", "Aide Rodriguez",
    ],
    "medical": [
        "Dr. Smith", "Dr. Chen", "Dr. Williams", "Dr. Martinez",
        "Nurse Johnson", "Dr. Taylor", "Dr. Kim", "Nurse Anderson",
        "Dr. Brown", "Dr. Davis", "Nurse Wilson", "Dr. Garcia",
    ],
    "travel": [
        # Travel/hospitality contacts
        "Travel Agent Kim", "Concierge Martinez", "Guide Smith",
        "Host Johnson", "Driver Chen", "Captain Williams",
        "Tour Leader Davis", "Receptionist Brown", "Manager Taylor",
    ],
    "creative": [
        # Artists, writers, designers
        "Director Chen", "Writer Smith", "Designer Johnson", "Artist Martinez",
        "Editor Williams", "Producer Taylor", "Photographer Kim", "Curator Davis",
        "Composer Brown", "Illustrator Garcia", "Animator Lee", "Sculptor Wilson",
    ],
}

# Organization pools by context - EXPANDED
ORGANIZATIONS: Dict[str, List[str]] = {
    "tech": [
        "Acme Corp", "TechStart Inc", "CloudScale", "DataFlow Systems",
        "InnovateTech", "CodeCraft", "DevOps Pro", "AgileWorks",
        "SoftwarePlus", "ByteLogic", "QuantumCode", "NexGen Tech",
        "CyberSolutions", "AppDynamics", "SystemsCore", "TechFusion",
        "AI Dynamics", "CloudNative Labs", "DataPipeline Inc", "SecureStack",
    ],
    "business": [
        "Client XYZ", "Partner ABC", "Vendor Solutions", "GlobalTrade Co",
        "Enterprise Partners", "Strategic Alliance", "Market Leaders",
        "Growth Ventures", "Capital Partners", "Commerce Group",
        "Industry Leaders", "Business First", "Corporate Solutions",
        "Global Industries", "Premier Partners", "Apex Consulting",
        "Sterling Group", "Summit Enterprises", "Horizon Holdings",
    ],
    "lds": [
        "First Ward", "Second Ward", "Third Ward", "Fourth Ward",
        "Stake Presidency", "Relief Society", "Elders Quorum",
        "Young Men", "Young Women", "Primary", "Sunday School",
        "Bishopric", "High Council", "Temple Committee",
    ],
    "education": [
        "Computer Science Dept", "Student Affairs", "Academic Council",
        "Faculty Senate", "Research Office", "Graduate Studies",
        "Undergraduate Programs", "Career Services", "Admissions Office",
        "Financial Aid", "Student Government", "Athletics Department",
        "Math Department", "English Department", "Science Division",
        "Arts & Humanities", "Engineering School", "Business School",
    ],
    "teaching": [
        # K-12 schools and departments
        "Lincoln Elementary", "Washington High School", "Jefferson Middle",
        "Oak Park Academy", "Riverside School District", "Central High",
        "PTA", "School Board", "Athletics Department", "Music Department",
        "Drama Club", "Science Club", "Math Team", "Debate Team",
    ],
    "medical": [
        "Memorial Hospital", "City Medical Center", "Regional Health",
        "Family Practice", "Urgent Care Clinic", "Specialty Associates",
        "Health Partners", "Medical Group", "Wellness Center",
    ],
    "travel": [
        # Airlines, hotels, travel companies
        "United Airlines", "Delta", "American Airlines", "Southwest",
        "Marriott", "Hilton", "Airbnb", "Booking.com", "Expedia",
        "Hertz", "Enterprise", "Uber", "Lyft", "TripAdvisor",
        "Lonely Planet", "World Nomads", "Travel Guard",
    ],
    "creative": [
        # Studios, agencies, publications
        "Creative Studio", "Design Agency", "Art Gallery", "Publishing House",
        "Music Studio", "Film Production", "Photography Studio", "Ad Agency",
        "Media Group", "Content Studio", "Digital Arts", "Creative Lab",
    ],
    "sales": [
        # Sales-specific clients and prospects
        "Prospect Corp", "Lead Company", "Demo Client", "Pipeline Inc",
        "Deal Partners", "Account First", "Revenue Group", "Sales Target",
        "Customer Success", "Enterprise Client", "SMB Account", "Key Account",
    ],
    "marketing": [
        # Marketing platforms and agencies
        "Brand Agency", "Digital Marketing Co", "Content Partners",
        "Social Media Agency", "SEO Experts", "Campaign Studio",
        "Analytics Group", "AdTech Inc", "Influencer Network",
    ],
}

# Location pools by context - EXPANDED
LOCATIONS: Dict[str, List[str]] = {
    "office": [
        "Conference Room A", "Conference Room B", "Meeting Room 101",
        "Building 3, Floor 2", "Main Office", "Executive Suite",
        "Training Room", "Boardroom", "Breakout Room 1", "Huddle Space",
        "Innovation Lab", "War Room", "All Hands Room",
    ],
    "virtual": [
        "Zoom", "Google Meet", "Microsoft Teams", "Webex",
        "Slack Huddle", "Discord", "Video Call", "Online",
    ],
    "lds": [
        "Chapel", "Bishop's Office", "Cultural Hall", "Relief Society Room",
        "Primary Room", "Youth Room", "Temple", "Stake Center",
        "Visitor Center", "Family History Center", "Baptistry",
    ],
    "education": [
        "Room 204", "Library", "Student Center", "Lecture Hall A",
        "Science Building", "Computer Lab", "Study Room 3",
        "Auditorium", "Campus Center", "Classroom 101",
    ],
    "teaching": [
        # K-12 school locations
        "Classroom 12", "Gymnasium", "Cafeteria", "Library",
        "Computer Lab", "Science Lab", "Art Room", "Music Room",
        "Principal's Office", "Counselor's Office", "Teacher's Lounge",
        "Playground", "Athletic Field", "Auditorium", "Media Center",
    ],
    "medical": [
        "Exam Room 1", "Waiting Room", "Consultation Room",
        "Operating Room", "Recovery Room", "Lab", "Imaging Center",
        "Physical Therapy", "Emergency Room",
    ],
    "travel": [
        # Airports, hotels, destinations
        "SFO Terminal 3", "JFK Terminal 4", "LAX International",
        "Hotel Lobby", "Gate B12", "Rental Car Center", "Train Station",
        "Airport Lounge", "Coworking Space", "Cafe in Tokyo",
        "Beach Resort", "Mountain Lodge", "City Center Hotel",
        "Hostel Common Area", "Airbnb Downtown", "Conference Center",
    ],
    "creative": [
        # Studios, galleries, creative spaces
        "Studio A", "Recording Booth", "Gallery Space", "Design Lab",
        "Photo Studio", "Editing Suite", "Green Screen Room",
        "Art Workshop", "Music Practice Room", "Writers Room",
    ],
    "general": [
        "Home", "Office", "Downtown", "City Center",
        "Coffee Shop", "Restaurant", "Park", "Community Center",
        "Coworking Space", "Home Office", "Library", "Gym",
    ],
}

# Domain to context mapping - EXPANDED for all user types
DOMAIN_CONTEXTS: Dict[Domain, List[str]] = {
    Domain.PROFESSIONAL: ["common", "professional"],
    Domain.BUSINESS: ["common", "professional"],
    Domain.LDS_LEADERSHIP: ["lds"],
    Domain.SPIRITUAL: ["common", "lds"],
    Domain.EDUCATION: ["common", "education"],
    Domain.TEACHING: ["common", "teaching", "education"],
    Domain.TECH: ["common", "professional"],
    Domain.HEALTH: ["common", "medical"],
    Domain.LIFE: ["common"],
    Domain.FINANCE: ["common", "professional"],
    Domain.CREATIVE: ["common", "creative"],
    Domain.TRAVEL: ["common", "travel"],
}

# Location context by domain - EXPANDED
DOMAIN_LOCATION_CONTEXTS: Dict[Domain, List[str]] = {
    Domain.PROFESSIONAL: ["office", "virtual"],
    Domain.BUSINESS: ["office", "virtual"],
    Domain.LDS_LEADERSHIP: ["lds"],
    Domain.SPIRITUAL: ["lds", "general"],
    Domain.EDUCATION: ["education"],
    Domain.TEACHING: ["teaching"],
    Domain.TECH: ["office", "virtual"],
    Domain.HEALTH: ["medical"],
    Domain.LIFE: ["general"],
    Domain.FINANCE: ["office", "virtual"],
    Domain.CREATIVE: ["creative", "general"],
    Domain.TRAVEL: ["travel", "general"],
}

# Org context by domain - EXPANDED
DOMAIN_ORG_CONTEXTS: Dict[Domain, List[str]] = {
    Domain.PROFESSIONAL: ["tech", "business"],
    Domain.BUSINESS: ["business", "sales", "marketing"],
    Domain.LDS_LEADERSHIP: ["lds"],
    Domain.SPIRITUAL: ["lds"],
    Domain.EDUCATION: ["education"],
    Domain.TEACHING: ["teaching", "education"],
    Domain.TECH: ["tech"],
    Domain.HEALTH: ["medical"],
    Domain.LIFE: ["general"],
    Domain.FINANCE: ["business"],
    Domain.CREATIVE: ["creative"],
    Domain.TRAVEL: ["travel"],
}


class EntityPool:
    """
    Pool of entities for generating realistic names, organizations, and locations.

    Usage:
        pool = EntityPool()
        person = pool.get_person(Domain.PROFESSIONAL)
        org = pool.get_organization(Domain.BUSINESS)
        location = pool.get_location(Domain.LDS_LEADERSHIP)
    """

    def __init__(self, seed: Optional[int] = None):
        """
        Initialize entity pool.

        Args:
            seed: Optional random seed for reproducibility
        """
        if seed is not None:
            random.seed(seed)

        # Track used entities to avoid duplicates in same generation run
        self._used_persons: set = set()
        self._used_orgs: set = set()
        self._used_locations: set = set()

    def reset_used(self) -> None:
        """Reset used entity tracking."""
        self._used_persons.clear()
        self._used_orgs.clear()
        self._used_locations.clear()

    def get_person(
        self,
        domain: Optional[Domain] = None,
        avoid_duplicates: bool = True
    ) -> str:
        """
        Get a random person name.

        Args:
            domain: Optional domain for context-appropriate names
            avoid_duplicates: If True, avoid returning same name twice

        Returns:
            Person name string
        """
        contexts = ["common"]
        if domain and domain in DOMAIN_CONTEXTS:
            contexts = DOMAIN_CONTEXTS[domain]

        # Collect all candidates from relevant contexts
        candidates = []
        for ctx in contexts:
            if ctx in PERSON_NAMES:
                candidates.extend(PERSON_NAMES[ctx])

        if not candidates:
            candidates = PERSON_NAMES["common"]

        # Filter out used if requested
        if avoid_duplicates:
            available = [p for p in candidates if p not in self._used_persons]
            if available:
                candidates = available
            else:
                # Reset if all used
                self._used_persons.clear()

        selected = random.choice(candidates)
        if avoid_duplicates:
            self._used_persons.add(selected)
        return selected

    def get_persons(
        self,
        count: int,
        domain: Optional[Domain] = None
    ) -> List[str]:
        """Get multiple unique person names."""
        persons = []
        for _ in range(count):
            person = self.get_person(domain, avoid_duplicates=True)
            persons.append(person)
        return persons

    def get_organization(
        self,
        domain: Optional[Domain] = None,
        avoid_duplicates: bool = True
    ) -> str:
        """
        Get a random organization name.

        Args:
            domain: Optional domain for context-appropriate orgs
            avoid_duplicates: If True, avoid returning same org twice

        Returns:
            Organization name string
        """
        contexts = ["business"]
        if domain and domain in DOMAIN_ORG_CONTEXTS:
            contexts = DOMAIN_ORG_CONTEXTS[domain]
            if not contexts:
                contexts = ["business"]

        candidates = []
        for ctx in contexts:
            if ctx in ORGANIZATIONS:
                candidates.extend(ORGANIZATIONS[ctx])

        if not candidates:
            candidates = ORGANIZATIONS["business"]

        if avoid_duplicates:
            available = [o for o in candidates if o not in self._used_orgs]
            if available:
                candidates = available
            else:
                self._used_orgs.clear()

        selected = random.choice(candidates)
        if avoid_duplicates:
            self._used_orgs.add(selected)
        return selected

    def get_organizations(
        self,
        count: int,
        domain: Optional[Domain] = None
    ) -> List[str]:
        """Get multiple unique organization names."""
        orgs = []
        for _ in range(count):
            org = self.get_organization(domain, avoid_duplicates=True)
            orgs.append(org)
        return orgs

    def get_location(
        self,
        domain: Optional[Domain] = None,
        virtual: bool = False,
        avoid_duplicates: bool = True
    ) -> str:
        """
        Get a random location.

        Args:
            domain: Optional domain for context-appropriate locations
            virtual: If True, prefer virtual meeting locations
            avoid_duplicates: If True, avoid returning same location twice

        Returns:
            Location string
        """
        if virtual:
            contexts = ["virtual"]
        else:
            contexts = ["office"]
            if domain and domain in DOMAIN_LOCATION_CONTEXTS:
                contexts = DOMAIN_LOCATION_CONTEXTS[domain]

        candidates = []
        for ctx in contexts:
            if ctx in LOCATIONS:
                candidates.extend(LOCATIONS[ctx])

        if not candidates:
            candidates = LOCATIONS["general"]

        if avoid_duplicates:
            available = [l for l in candidates if l not in self._used_locations]
            if available:
                candidates = available
            else:
                self._used_locations.clear()

        selected = random.choice(candidates)
        if avoid_duplicates:
            self._used_locations.add(selected)
        return selected

    def get_meeting_location(self, domain: Optional[Domain] = None) -> str:
        """Get a location suitable for meetings (office or virtual)."""
        # 60% chance of virtual for modern meetings
        virtual = random.random() < 0.6
        return self.get_location(domain, virtual=virtual)

    def get_email(self, person_name: str) -> str:
        """Generate email address from person name."""
        # Extract first and last name
        parts = person_name.split()
        if len(parts) >= 2:
            first = parts[0].lower()
            last = parts[-1].lower()
            # Remove any parenthetical roles like "(PM)"
            last = last.replace("(", "").replace(")", "")
        else:
            first = parts[0].lower()
            last = "user"

        domains = ["example.com", "company.com", "org.test", "test.local"]
        domain = random.choice(domains)
        return f"{first}.{last}@{domain}"

    def get_attendee_list(
        self,
        count: int,
        domain: Optional[Domain] = None
    ) -> List[Dict[str, str]]:
        """
        Get a list of attendees with names and emails.

        Returns list of dicts: [{"name": "...", "email": "..."}]
        """
        persons = self.get_persons(count, domain)
        return [
            {"name": p, "email": self.get_email(p)}
            for p in persons
        ]


# Singleton pool instance
_default_pool: Optional[EntityPool] = None


def get_pool(seed: Optional[int] = None) -> EntityPool:
    """
    Get the default entity pool instance.

    Args:
        seed: Optional seed for new pool (returns new instance if provided)

    Returns:
        EntityPool instance
    """
    global _default_pool

    if seed is not None:
        return EntityPool(seed=seed)

    if _default_pool is None:
        _default_pool = EntityPool()

    return _default_pool
