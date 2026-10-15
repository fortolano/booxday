"""
Configuration constants, enums, and weights for test data generation.

Mirrors the classification system in SemanticGeniusService.
"""

from enum import Enum
from typing import Dict, List, Set


class OcrSource(str, Enum):
    """Source types for OCR text (matches OcrSource.kt)."""
    BOOX_NOTE = "BOOX_NOTE"
    MEMO = "MEMO"
    TASK = "TASK"
    CALENDAR_EVENT = "CALENDAR_EVENT"
    LOCAL_EVENT = "LOCAL_EVENT"


class Domain(str, Enum):
    """Domain classification (22 domains, matches Domain.kt)."""
    EDUCATION = "EDUCATION"
    PROFESSIONAL = "PROFESSIONAL"
    LIFE = "LIFE"
    TEACHING = "TEACHING"
    TECH = "TECH"
    FINANCE = "FINANCE"
    HEALTH = "HEALTH"
    SPIRITUAL = "SPIRITUAL"
    CREATIVE = "CREATIVE"
    TRAVEL = "TRAVEL"
    LEGAL = "LEGAL"
    HOME = "HOME"
    SHOPPING = "SHOPPING"
    BUSINESS = "BUSINESS"
    STRATEGY = "STRATEGY"
    SOCIAL_MEDIA = "SOCIAL_MEDIA"
    ENTREPRENEURSHIP = "ENTREPRENEURSHIP"
    LDS_LEADERSHIP = "LDS_LEADERSHIP"
    FOOD = "FOOD"
    COMMUNITY = "COMMUNITY"
    PET = "PET"
    MISC = "MISC"


class Intent(str, Enum):
    """Intent classification (65+ intents, matches Intent.kt)."""
    # Planning intents
    TODO_LIST = "TODO_LIST"
    PLANNING = "PLANNING"
    GOAL_SETTING = "GOAL_SETTING"
    DAILY_REVIEW = "DAILY_REVIEW"
    WEEKLY_REVIEW = "WEEKLY_REVIEW"
    ACTION_REVIEW = "ACTION_REVIEW"

    # Recording intents
    MEETING_NOTES = "MEETING_NOTES"
    CALL_LOG = "CALL_LOG"
    BRAINSTORM = "BRAINSTORM"
    JOURNAL = "JOURNAL"
    DECISION_LOG = "DECISION_LOG"
    RESEARCH = "RESEARCH"

    # Teaching intents
    LESSON_PLAN = "LESSON_PLAN"
    GRADING = "GRADING"
    CLASSROOM_MGMT = "CLASSROOM_MGMT"
    CURRICULUM = "CURRICULUM"
    STUDENT_NOTES = "STUDENT_NOTES"

    # Management intents
    PEOPLE_MANAGEMENT = "PEOPLE_MANAGEMENT"
    PROJECT_MGMT = "PROJECT_MGMT"
    DELEGATION = "DELEGATION"
    FEEDBACK = "FEEDBACK"
    HIRING = "HIRING"

    # Financial intents
    EXPENSE_LOG = "EXPENSE_LOG"
    BUDGET = "BUDGET"
    INVOICE = "INVOICE"
    TAX_PREP = "TAX_PREP"

    # Health intents
    SYMPTOM_LOG = "SYMPTOM_LOG"
    MEDICATION = "MEDICATION"
    EXERCISE_LOG = "EXERCISE_LOG"
    MEAL_PLAN = "MEAL_PLAN"
    APPOINTMENT = "APPOINTMENT"

    # Creative intents
    WRITING = "WRITING"
    DESIGN = "DESIGN"
    MUSIC = "MUSIC"
    ART = "ART"

    # Spiritual/LDS intents
    SCRIPTURE_STUDY = "SCRIPTURE_STUDY"
    PRAYER = "PRAYER"
    TALK_PREP = "TALK_PREP"
    LDS_COUNCIL = "LDS_COUNCIL"
    LDS_MINISTERING = "LDS_MINISTERING"

    # Tech intents
    CODE_REVIEW = "CODE_REVIEW"
    BUG_TRACKING = "BUG_TRACKING"
    ARCHITECTURE = "ARCHITECTURE"
    DEPLOYMENT = "DEPLOYMENT"

    # Business intents
    SALES = "SALES"
    MARKETING = "MARKETING"
    CUSTOMER_FEEDBACK = "CUSTOMER_FEEDBACK"
    COMPETITIVE_ANALYSIS = "COMPETITIVE_ANALYSIS"
    PRODUCT_ROADMAP = "PRODUCT_ROADMAP"

    # Other intents
    SHOPPING_LIST = "SHOPPING_LIST"
    RECIPE = "RECIPE"
    TRAVEL_PLAN = "TRAVEL_PLAN"
    EVENT_PLANNING = "EVENT_PLANNING"
    COMPARISON = "COMPARISON"
    PROS_CONS = "PROS_CONS"
    WISHLIST = "WISHLIST"
    REFERENCE = "REFERENCE"
    GENERAL = "GENERAL"


class EntityType(str, Enum):
    """Entity types (matches EntityType.kt)."""
    PERSON = "PERSON"
    ORG = "ORG"
    LOCATION = "LOCATION"
    DATE = "DATE"
    TIME = "TIME"
    EMAIL = "EMAIL"
    PHONE = "PHONE"
    URL = "URL"
    MONEY = "MONEY"
    PERCENT = "PERCENT"


class DocumentStyle(str, Enum):
    """Document style classification."""
    MEETING_NOTES = "MEETING_NOTES"
    CHECKLIST = "CHECKLIST"
    JOURNAL = "JOURNAL"
    NOTES = "NOTES"
    OUTLINE = "OUTLINE"
    LETTER = "LETTER"
    MIXED = "MIXED"


class RelationshipType(str, Enum):
    """Semantic relationship strength."""
    HIGH_MATCH = "HIGH_MATCH"      # 0.60-0.85 affinity
    MEDIUM_MATCH = "MEDIUM_MATCH"  # 0.25-0.50 affinity
    LOW_MATCH = "LOW_MATCH"        # 0.00-0.20 affinity


# Affinity score weights (from SemanticGeniusService.kt lines 803-964)
AFFINITY_WEIGHTS = {
    "DOMAIN_EXACT": 0.20,
    "DOMAIN_SIBLING": 0.10,
    "INTENT": 0.12,
    "KEYWORD": 0.18,
    "KEY_PHRASE": 0.08,
    "PERSON": 0.12,  # 0.06 per match, max 0.12
    "ORG": 0.08,     # 0.04 per match, max 0.08
    "LOCATION": 0.07,  # 0.035 per match, max 0.07
    "ACTION_ITEM": 0.10,
    "TEMPORAL": 0.05,
}

# Domain sibling relationships (domains that get partial match credit)
DOMAIN_SIBLINGS: Dict[Domain, Set[Domain]] = {
    Domain.PROFESSIONAL: {Domain.BUSINESS, Domain.STRATEGY},
    Domain.BUSINESS: {Domain.PROFESSIONAL, Domain.ENTREPRENEURSHIP, Domain.STRATEGY},
    Domain.EDUCATION: {Domain.TEACHING},
    Domain.TEACHING: {Domain.EDUCATION},
    Domain.SPIRITUAL: {Domain.LDS_LEADERSHIP},
    Domain.LDS_LEADERSHIP: {Domain.SPIRITUAL},
    Domain.TECH: {Domain.PROFESSIONAL},
    Domain.FINANCE: {Domain.BUSINESS},
    Domain.HEALTH: {Domain.LIFE},
    Domain.LIFE: {Domain.HEALTH, Domain.HOME},
    Domain.HOME: {Domain.LIFE},
    Domain.CREATIVE: {Domain.LIFE},
    Domain.STRATEGY: {Domain.BUSINESS, Domain.PROFESSIONAL},
    Domain.ENTREPRENEURSHIP: {Domain.BUSINESS},
}

# Focus domains for test data generation - BALANCED for diverse user types
# Weighted list: domains appear multiple times based on target user population
FOCUS_DOMAINS: List[Domain] = [
    # Professional/Business (30% - largest user segment)
    Domain.PROFESSIONAL, Domain.PROFESSIONAL, Domain.PROFESSIONAL,
    Domain.BUSINESS, Domain.BUSINESS, Domain.BUSINESS,

    # Education (20% - teachers, students, academics)
    Domain.EDUCATION, Domain.EDUCATION, Domain.EDUCATION, Domain.EDUCATION,
    Domain.TEACHING, Domain.TEACHING,

    # Tech (15% - developers, PMs, digital workers)
    Domain.TECH, Domain.TECH, Domain.TECH,

    # Travel/Digital Nomads (10%)
    Domain.TRAVEL, Domain.TRAVEL,

    # Creative (10% - writers, designers, artists)
    Domain.CREATIVE, Domain.CREATIVE,

    # Life/Personal (10%)
    Domain.LIFE, Domain.LIFE,
    Domain.HEALTH,
    Domain.FINANCE,

    # Spiritual/LDS (5% - niche but supported)
    Domain.SPIRITUAL,
    Domain.LDS_LEADERSHIP,
]

# Common intents by domain
DOMAIN_INTENTS: Dict[Domain, List[Intent]] = {
    Domain.PROFESSIONAL: [
        Intent.MEETING_NOTES, Intent.TODO_LIST, Intent.PLANNING,
        Intent.PEOPLE_MANAGEMENT, Intent.PROJECT_MGMT, Intent.DAILY_REVIEW,
    ],
    Domain.BUSINESS: [
        Intent.MEETING_NOTES, Intent.SALES, Intent.CUSTOMER_FEEDBACK,
        Intent.COMPETITIVE_ANALYSIS, Intent.PRODUCT_ROADMAP, Intent.PLANNING,
    ],
    Domain.LDS_LEADERSHIP: [
        Intent.LDS_COUNCIL, Intent.LDS_MINISTERING, Intent.MEETING_NOTES,
        Intent.PLANNING, Intent.PEOPLE_MANAGEMENT, Intent.TALK_PREP,
    ],
    Domain.SPIRITUAL: [
        Intent.SCRIPTURE_STUDY, Intent.PRAYER, Intent.TALK_PREP,
        Intent.JOURNAL, Intent.GOAL_SETTING,
    ],
    Domain.EDUCATION: [
        Intent.LESSON_PLAN, Intent.GRADING, Intent.CURRICULUM,
        Intent.STUDENT_NOTES, Intent.PLANNING, Intent.RESEARCH,
    ],
    Domain.TECH: [
        Intent.CODE_REVIEW, Intent.BUG_TRACKING, Intent.ARCHITECTURE,
        Intent.DEPLOYMENT, Intent.MEETING_NOTES, Intent.PROJECT_MGMT,
    ],
    Domain.HEALTH: [
        Intent.SYMPTOM_LOG, Intent.MEDICATION, Intent.EXERCISE_LOG,
        Intent.MEAL_PLAN, Intent.APPOINTMENT, Intent.JOURNAL,
    ],
    Domain.LIFE: [
        Intent.JOURNAL, Intent.TODO_LIST, Intent.GOAL_SETTING,
        Intent.DAILY_REVIEW, Intent.PLANNING, Intent.WISHLIST,
    ],
    Domain.FINANCE: [
        Intent.EXPENSE_LOG, Intent.BUDGET, Intent.INVOICE,
        Intent.TAX_PREP, Intent.PLANNING,
    ],
    Domain.CREATIVE: [
        Intent.WRITING, Intent.DESIGN, Intent.BRAINSTORM,
        Intent.JOURNAL, Intent.PLANNING,
    ],
    Domain.TRAVEL: [
        Intent.TRAVEL_PLAN, Intent.TODO_LIST, Intent.PLANNING,
        Intent.JOURNAL, Intent.EXPENSE_LOG, Intent.RESEARCH,
    ],
    Domain.TEACHING: [
        Intent.LESSON_PLAN, Intent.GRADING, Intent.CURRICULUM,
        Intent.CLASSROOM_MGMT, Intent.STUDENT_NOTES, Intent.PLANNING,
    ],
}

# Document styles by intent
INTENT_DOC_STYLES: Dict[Intent, DocumentStyle] = {
    Intent.MEETING_NOTES: DocumentStyle.MEETING_NOTES,
    Intent.TODO_LIST: DocumentStyle.CHECKLIST,
    Intent.JOURNAL: DocumentStyle.JOURNAL,
    Intent.PLANNING: DocumentStyle.NOTES,
    Intent.GOAL_SETTING: DocumentStyle.OUTLINE,
    Intent.LESSON_PLAN: DocumentStyle.OUTLINE,
    Intent.LDS_COUNCIL: DocumentStyle.MEETING_NOTES,
    Intent.CODE_REVIEW: DocumentStyle.NOTES,
}

# Affinity score ranges by relationship type
# Calibrated based on actual SemanticGeniusService output (Jan 2026)
# Note: Keyword Jaccard ~35% in practice limits HIGH_MATCH to ~0.50-0.55
AFFINITY_RANGES = {
    RelationshipType.HIGH_MATCH: (0.45, 0.85),    # Same domain + shared entities
    RelationshipType.MEDIUM_MATCH: (0.18, 0.50),  # Same/sibling domain, partial overlap
    RelationshipType.LOW_MATCH: (0.00, 0.22),     # Different domains, no overlap
}

# Match reason labels
class MatchReason(str, Enum):
    """Reasons why two items match semantically."""
    DOMAIN = "DOMAIN"
    DOMAIN_SIBLING = "DOMAIN_SIBLING"
    INTENT = "INTENT"
    KEYWORD = "KEYWORD"
    PERSON = "PERSON"
    ORG = "ORG"
    LOCATION = "LOCATION"
    ACTION_ITEM = "ACTION_ITEM"
    TEMPORAL = "TEMPORAL"
    KEY_PHRASE = "KEY_PHRASE"


# Dictionary tier weights (from domain dictionaries)
TIER_WEIGHTS = {
    "tierA": 4.0,
    "tierB": 2.5,
    "tierC": 1.0,
}

# Path to classification dictionaries
DICTIONARIES_PATH = "app/src/main/assets/classification/en"
