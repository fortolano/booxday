#!/usr/bin/env python3
"""
Database seeding script for BooxDay testing.

Populates the BooxDay database with sample events, tasks, memos, and AI preferences
for testing purposes. By default, clears existing data before seeding.

Usage:
    python tools/seed_database.py              # Generate and apply (clears first)
    python tools/seed_database.py --dry-run    # Generate SQL only, don't execute
    python tools/seed_database.py --no-clear   # Add data without clearing first
    python tools/seed_database.py --events 100 --tasks 50  # Custom counts
    python tools/seed_database.py --persona 0  # Use specific AI persona
    python tools/seed_database.py --no-ai-prefs  # Skip AI preferences
    python tools/seed_database.py --no-memos   # Skip memo pages
    python tools/seed_database.py --life-persona teacher  # Use Teacher persona

Default date range: 2026-02-10 to 2026-04-20 (70 days)

Life Personas (--life-persona):
    tech (default) - Tech Professional:
        - Software engineer with sprints, code reviews, 1:1s
        - Family: spouse and two kids (Emma, Jake)
        - Side project (task management app), fitness tracking
        - Apartment hunting, relationship milestones

    teacher - High School English Teacher:
        - Teaching: lesson planning, grading, student stories (Marcus, Sarah, Tyler)
        - Family: spouse David, kids Lily (13) and Nathan (9)
        - Church: Deacon, Adult Sunday School (Book of James series)
        - Book writing: "Teaching with Purpose" (methodology book)
        - Speaking: educational conferences, teacher workshops
        - Community: literacy tutoring, HOA secretary

AI Personas (--persona N):
    0 - Tech Professional: Morning person, urban, work-focused, practical suggestions
    1 - Parent & Freelancer: Afternoon peak, suburban, family-first, holistic style
    2 - Graduate Student: Night owl, urban, education-focused, balanced style
    3 - Entrepreneur: Steady energy, urban, finance/work-focused, minimal suggestions
    4 - Creative Professional: Afternoon peak, urban, hobby-focused, holistic style

Generated Data:
    - Events: ~500 local events with titles, times, locations, colors
    - Tasks: ~200 tasks with priorities, due dates, parent-child relationships
    - Memos: 2-4 pages per day with typed text annotations
      - Meeting minutes linked to calendar events
      - Personal notes matching persona interests
      - Journal entries and voice-to-text style notes
    (OCR indexing is handled by the app's background service)

Requirements:
    - Python 3.8+
    - ADB in PATH
    - Device connected with USB debugging enabled
    - BooxDay app installed and database initialized
"""

import argparse
import json
import os
import random
import re
import string
import subprocess
import sys
import uuid
from datetime import datetime, timedelta
from pathlib import Path
from typing import List, Tuple, Optional

# ============================================================================
# Configuration
# ============================================================================

DB_PATH = "/data/data/com.boox.day/files/booxdaydb/app_database"
TEMP_SQL_PATH = "/data/local/tmp/seed.sql"

# Event colors (hex format)
EVENT_COLORS = [
    "#4285F4",  # Blue
    "#EA4335",  # Red
    "#FBBC04",  # Yellow
    "#34A853",  # Green
    "#FF6D01",  # Orange
    "#46BDC6",  # Teal
    "#7986CB",  # Indigo
    "#8E24AA",  # Purple
    "#E67C73",  # Pink
    "#F6BF26",  # Amber
]

# Event title templates with frequency limits (title, max_occurrences in 60 days)
# - Weekly events: ~9 occurrences
# - Biweekly: ~4 occurrences
# - Monthly: ~2 occurrences
# - One-time: 1 occurrence
EVENT_TITLES_WITH_LIMITS = {
    "meeting": [
        ("Team Standup", 9),        # Weekly recurring
        ("Weekly Sync", 9),         # Weekly recurring
        ("Project Review", 4),      # Biweekly
        ("Sprint Planning", 4),     # Biweekly (sprint cadence)
        ("Retrospective", 4),       # Biweekly
        ("All-Hands Meeting", 2),   # Monthly
        ("Budget Review", 2),       # Monthly
        ("Strategy Session", 2),    # Monthly
        ("Quarterly Planning", 1),  # Once per quarter
        ("Department Meeting", 4),  # Biweekly
        ("Town Hall", 2),           # Monthly
        ("OKR Review", 2),          # Monthly
        ("Team Lunch", 4),          # Biweekly
        ("Knowledge Share", 4),     # Biweekly
    ],
    "one_on_one": [
        ("1:1 with Sarah", 4),      # Biweekly
        ("1:1 with Mike", 4),       # Biweekly
        ("1:1 with Alex", 4),       # Biweekly
        ("1:1 with Jordan", 4),     # Biweekly
        ("1:1 with Taylor", 4),     # Biweekly
        ("1:1 with Manager", 4),    # Biweekly
        ("Mentorship Session", 4),  # Biweekly
        ("Career Discussion", 2),   # Monthly
        ("Skip Level", 2),          # Monthly
        ("Peer Feedback Session", 2), # Monthly
    ],
    "call": [
        ("Client Call - Acme Corp", 4),     # Biweekly client
        ("Client Call - TechStart", 3),     # Every 3 weeks
        ("Client Call - DataFlow", 2),      # Monthly
        ("Sales Demo", 4),                   # Multiple prospects
        ("Partner Call", 3),                 # Periodic
        ("Investor Update", 1),              # Once
        ("Vendor Discussion", 2),            # Occasional
        ("Interview - Backend Dev", 2),      # Hiring
        ("Interview - Designer", 2),         # Hiring
        ("Interview - PM", 1),               # Hiring
        ("Customer Success Check-in", 3),    # Monthly-ish
        ("Onboarding Call", 2),              # New customers
    ],
    "review": [
        ("Design Review", 4),        # Biweekly
        ("Code Review Session", 8),  # Weekly (important)
        ("Architecture Review", 2),  # Monthly
        ("Security Audit", 1),       # Once
        ("Performance Review", 2),   # Cycle-based
        ("UX Review", 3),            # Every 3 weeks
        ("Documentation Review", 2), # Monthly
        ("Accessibility Review", 1), # Once
        ("API Review", 2),           # Monthly
    ],
    "personal": [
        ("Dentist Appointment", 1),   # Once in 60 days
        ("Doctor Visit", 1),          # Once
        ("Car Service", 1),           # Once
        ("Gym Session", 12),          # Can be frequent
        ("Lunch with Friends", 4),    # Biweekly social
        ("Coffee Chat", 6),           # Weekly-ish networking
        ("Haircut", 2),               # Every 4-5 weeks
        ("Vet Appointment", 1),       # Once
        ("Dinner with Family", 4),    # Biweekly
        ("Movie Night", 3),           # Occasional
        ("Date Night", 4),            # Biweekly
        ("Physical Therapy", 4),      # If ongoing
        ("Eye Exam", 1),              # Once
        ("Grocery Run", 8),           # Weekly
        # Social activities
        ("Food Bank Volunteering", 2),    # Bimonthly
        ("Visit Dave at Hospital", 1),    # One-time
        ("Host Dinner Party", 2),         # Monthly
        ("Neighborhood BBQ", 1),          # Once
        ("Book Club Meeting", 4),         # Biweekly
        ("Game Night at Marcus's", 3),    # Every 3 weeks
        # Family activities
        ("Emma's Soccer Game", 8),        # Weekly on Saturdays
        ("Jake's Basketball Practice", 8), # Weekly
        ("School Play - West Side Story", 2),  # Multi-day event
        ("Parent-Teacher Conference", 1), # Once
        ("Take Jake to Scout Camp", 1),   # Once
        ("Emma's Piano Recital", 1),      # Once
        ("Family Bike Ride", 4),          # Biweekly
        ("Kids' Doctor Checkup", 1),      # Annual
        ("Help Emma with Science Fair", 1), # Once
        ("Jake's Robotics Competition", 1), # Once
    ],
    "all_day": [
        ("Company Holiday", 2),       # A couple holidays
        ("Conference Day", 2),        # Multi-day conference
        ("Training Workshop", 2),     # Occasional
        ("Team Offsite", 1),          # Once
        ("Work From Home", 8),        # Weekly option
        ("Vacation Day", 5),          # A week of PTO
        ("Birthday - Sarah", 1),      # Annual
        ("Birthday - Mike", 1),       # Annual
        ("Birthday - Mom", 1),        # Annual
        ("Birthday - Emma", 1),       # Kid's birthday
        ("Birthday - Jake", 1),       # Kid's birthday
        ("Anniversary", 1),           # Annual
        ("Product Launch", 1),        # Big event
        ("Demo Day", 2),              # Sprint demos
        ("Hackathon", 1),             # Once
        ("Volunteer Day", 1),         # Once
        ("Mental Health Day", 2),     # Occasional
        # Family all-day events
        ("School Holiday - MLK Day", 1),
        ("School Holiday - Presidents Day", 1),
        ("Spring Break - Kids Off", 1),
        ("Family Trip to Beach", 1),
        ("Camping Weekend", 1),
        ("Visit Grandparents", 2),
        ("Family Reunion", 1),
    ],
    "community": [
        ("Code2Community Mentoring", 8),      # Weekly
        ("Tech Volunteer Orientation", 1),
        ("Mentee Check-in Call", 4),          # Biweekly
        ("Community Coding Workshop", 2),     # Monthly
        ("Career Advice Session", 2),         # Monthly
    ],
    "reflection": [
        ("Morning Journaling", 12),           # Regular practice
        ("Quarterly Goals Review", 1),
        ("Meditation Session", 8),            # Weekly
        ("Life Purpose Reflection", 2),
    ],
}

# Legacy format for backward compatibility (used by memo correlation)
EVENT_TITLES = {
    category: [title for title, _ in items]
    for category, items in EVENT_TITLES_WITH_LIMITS.items()
}

# ============================================================================
# Teacher Persona Event Titles with Frequency Limits
# ============================================================================
TEACHER_EVENT_TITLES_WITH_LIMITS = {
    "teaching": [
        ("Faculty Meeting", 4),              # Biweekly
        ("Department Meeting", 4),           # Biweekly
        ("Parent-Teacher Conference", 2),    # Once per quarter
        ("PLC Meeting", 8),                  # Weekly Professional Learning Community
        ("Curriculum Committee", 2),         # Monthly
        ("IEP Meeting - Marcus", 1),         # Individual student
        ("IEP Meeting - Sarah", 1),          # Individual student
        ("Staff Development Day", 2),        # Monthly
        ("Grade Level Meeting", 4),          # Biweekly
        ("Student Council Advisor", 4),      # Biweekly
        ("Poetry Slam Night", 1),            # Annual event
        ("Senior Project Presentations", 1), # Once
        ("AP Exam Prep Session", 4),         # Weekly leading up to exams
        ("Book Club Discussion", 4),         # Biweekly with students
    ],
    "church": [
        ("Sunday School Teaching", 9),       # Weekly
        ("Deacon Meeting", 2),               # Monthly
        ("Worship Committee", 2),            # Monthly
        ("Church Workday", 1),               # Quarterly
        ("VBS Planning Meeting", 2),         # Seasonal
        ("New Member Class", 2),             # Periodic
        ("Easter Sunrise Service", 1),       # Annual
        ("Deacon Retreat", 1),               # Annual
        ("Visitation Team", 4),              # Biweekly
        ("Prayer Breakfast", 2),             # Monthly
        ("Adult Bible Study", 8),            # Weekly
    ],
    "family": [
        ("Lily's Volleyball Game", 8),       # Weekly during season
        ("Nathan's Soccer Game", 8),         # Weekly during season
        ("Lily's Drama Club Rehearsal", 4),  # Biweekly
        ("Nathan's Cub Scout Meeting", 4),   # Biweekly
        ("Date Night with David", 4),        # Biweekly
        ("Family Movie Night", 4),           # Biweekly
        ("Kids' Dentist Appointment", 1),    # Once
        ("Lily's 8th Grade Dance", 1),       # Once
        ("Nathan's Scout Camping Trip", 1),  # Once
        ("Spring Break - Visit Grandparents", 1),  # Once
        ("Lily's Piano Recital", 1),         # Once
        ("Nathan's Art Show", 1),            # Once
        ("Family Bike Ride", 4),             # Biweekly
        ("Help Nathan with Science Fair", 1), # Once
    ],
    "writing_speaking": [
        ("Writing Time - Book Chapter", 12), # Regular writing sessions
        ("Editor Call - Educator's Press", 2), # Periodic
        ("State English Teachers Conference", 1), # Annual
        ("District Professional Development", 1), # Once
        ("Workshop Preparation", 4),         # Before speaking events
        ("Book Study Discussion", 4),        # Biweekly with colleagues
        ("Podcast Interview", 1),            # One-time opportunity
        ("Submit Conference Proposal", 1),   # Deadline
    ],
    "community": [
        ("Literacy Tutoring", 8),            # Weekly
        ("HOA Meeting", 2),                  # Monthly
        ("Community Cleanup Day", 1),        # Quarterly
        ("Neighborhood Block Party", 1),     # Annual
        ("Mentoring Session - New Teacher", 4), # Biweekly
    ],
    "personal": [
        ("Doctor Appointment", 1),           # Once
        ("Dentist Appointment", 1),          # Once
        ("Gym / Morning Walk", 12),          # Regular
        ("Hair Appointment", 2),             # Monthly
        ("Coffee with Mrs. Williams", 4),    # Biweekly mentor meetup
        ("Teacher Book Club", 4),            # Biweekly
        ("Lunch with Department", 4),        # Biweekly
    ],
    "all_day": [
        ("School Holiday - MLK Day", 1),
        ("School Holiday - Presidents Day", 1),
        ("Spring Break", 5),                 # Week-long
        ("State Testing Week", 5),           # Week-long
        ("Easter Sunday", 1),
        ("Birthday - David", 1),
        ("Birthday - Lily", 1),
        ("Birthday - Nathan", 1),
        ("Wedding Anniversary", 1),
        ("Teacher Appreciation Week", 5),    # Week-long
        ("Grading Day (No Students)", 2),    # End of quarter
        ("Professional Development Day", 2), # District-wide
    ],
}

# Legacy format for teacher events
TEACHER_EVENT_TITLES = {
    category: [title for title, _ in items]
    for category, items in TEACHER_EVENT_TITLES_WITH_LIMITS.items()
}

# Teacher-specific locations
TEACHER_LOCATIONS = [
    "Room 214",              # Her classroom
    "Media Center",
    "Teacher's Lounge",
    "Principal's Office",
    "Guidance Office",
    "Cafeteria",
    "Auditorium",
    "Grace Community Church",
    "Fellowship Hall",
    "Community Center",
    "Local Coffee Shop",
    "David's Office",
    "Soccer Field",
    "Volleyball Court",
    None,  # No location
    None,
    None,  # Weight towards no location
]

# Locations
LOCATIONS = [
    "Conference Room A",
    "Conference Room B",
    "Main Office",
    "Downtown Office",
    "Building 2, Floor 3",
    "Zoom",
    "Google Meet",
    "Microsoft Teams",
    "Phone Call",
    "123 Main Street",
    "Coffee Shop",
    "Client Office",
    None,  # No location
    None,
    None,  # Weight towards no location
]

# Task content templates with frequency limits (title, max_occurrences in 60 days)
# - Recurring work tasks: can repeat
# - One-time errands/personal: max 1-2 times
TASK_TITLES_WITH_LIMITS = {
    "work": [
        ("Review PR #2341", 1),
        ("Review PR #2342", 1),
        ("Review PR #2345", 1),
        ("Review PR #2348", 1),
        ("Review PR #2351", 1),
        ("Review PR #2356", 1),
        ("Fix login redirect bug", 1),
        ("Fix session timeout issue", 1),
        ("Fix mobile responsive layout", 1),
        ("Fix API rate limiting", 1),
        ("Update API documentation", 2),      # Occasionally needs updates
        ("Deploy to staging", 4),              # Sprint-based
        ("Deploy to production", 2),           # Monthly-ish
        ("Write unit tests for auth", 1),
        ("Write integration tests", 1),
        ("Write e2e test suite", 1),
        ("Refactor auth module", 1),
        ("Refactor payment service", 1),
        ("Update npm dependencies", 2),        # Monthly
        ("Update Python dependencies", 1),
        ("Optimize database queries", 1),
        ("Review security patches", 2),        # Monthly
        ("Setup CI/CD pipeline", 1),
        ("Configure staging environment", 1),
        ("Create design mockups", 1),
        ("Write technical spec", 2),
        ("Code review for team", 4),           # Regular activity
        ("Debug performance issue", 2),
        ("Investigate memory leak", 1),
        ("Profile API endpoints", 1),
        ("Setup monitoring alerts", 1),
        ("Document API changes", 2),
        ("Update README", 1),
        ("Archive old branches", 1),
        ("Clean up tech debt", 2),
    ],
    "personal": [
        ("Call insurance company", 1),
        ("Schedule dentist appointment", 1),
        ("Pay utility bills", 2),             # Monthly
        ("Renew car registration", 1),
        ("Book flight tickets", 1),
        ("Return Amazon package", 1),
        ("Cancel streaming subscription", 1),
        ("Update resume", 1),
        ("Research new laptop", 1),
        ("File tax documents", 1),
        ("Renew passport", 1),
        ("Update emergency contacts", 1),
        ("Review health insurance", 1),
        ("Set up 401k contribution", 1),
        ("Schedule annual physical", 1),
        ("Backup phone photos", 1),
        ("Clean up email inbox", 2),
        ("Update LinkedIn profile", 1),
        ("Review credit card statements", 2),
        ("Change HVAC filter", 1),
    ],
    "errands": [
        ("Buy groceries", 8),                 # Weekly
        ("Pick up dry cleaning", 2),
        ("Get car oil changed", 1),
        ("Mail package to mom", 1),
        ("Mail tax documents", 1),
        ("Deposit check", 1),
        ("Buy birthday gift for Sarah", 1),
        ("Buy birthday gift for Mike", 1),
        ("Buy anniversary gift", 1),
        ("Return library books", 2),
        ("Get prescription refilled", 1),
        ("Buy office supplies", 1),
        ("Drop off donations", 1),
        ("Get spare keys made", 1),
        ("Return shoes", 1),
        ("Pick up package from locker", 2),
        ("Drop off recycling", 3),
        ("Buy new running shoes", 1),
        ("Get haircut", 2),
        ("Fill up gas tank", 4),
    ],
    "projects": [
        ("Prepare Q1 presentation", 1),
        ("Prepare sprint demo", 2),
        ("Send invoice to client", 2),
        ("Research competitors", 1),
        ("Draft project proposal", 1),
        ("Create budget spreadsheet", 1),
        ("Write blog post on K8s", 1),
        ("Write blog post on React", 1),
        ("Update portfolio website", 1),
        ("Record tutorial video", 1),
        ("Design logo concepts", 1),
        ("Plan team offsite", 1),
        ("Organize conference talk", 1),
        ("Create expense report", 2),
        ("Review OKR progress", 2),
        ("Plan product roadmap", 1),
        ("Draft RFC document", 1),
        ("Create onboarding guide", 1),
    ],
    "community": [
        ("Prepare mentoring materials", 4),
        ("Review Alex's code submission", 8),
        ("Schedule mentee check-in", 4),
        ("Write recommendation for Alex", 1),
        ("Plan workshop curriculum", 2),
        ("Update volunteer hours log", 2),
    ],
    "reflection": [
        ("Morning journal entry", 12),
        ("Review quarterly goals", 2),
        ("Update life vision board", 1),
        ("Gratitude list", 8),
        ("Values alignment check", 2),
    ],
}

# Legacy format for backward compatibility
TASK_TITLES = {
    category: [title for title, _ in items]
    for category, items in TASK_TITLES_WITH_LIMITS.items()
}

# ============================================================================
# Teacher Persona Task Titles with Frequency Limits
# ============================================================================
TEACHER_TASK_TITLES_WITH_LIMITS = {
    "teaching": [
        ("Grade essay batch - Period 1", 4),
        ("Grade essay batch - Period 3", 4),
        ("Grade essay batch - Period 5", 4),
        ("Prepare lesson plans for week", 9),   # Weekly
        ("Update gradebook", 8),                 # Weekly
        ("Create quiz for Harlem Renaissance", 1),
        ("Create quiz for Poetry unit", 1),
        ("Create rubric for research paper", 1),
        ("Contact parent - Tyler missing work", 1),
        ("Contact parent - Marcus behavior", 1),
        ("Write recommendation letter - Sarah", 1),
        ("Write recommendation letter - Emma", 1),
        ("Order classroom supplies", 1),
        ("Copy handouts for week", 8),          # Weekly
        ("Set up discussion groups", 4),        # Biweekly
        ("Review IEP accommodations", 2),       # Monthly
        ("Enter attendance corrections", 4),    # Biweekly
        ("Submit textbook order", 1),
        ("Prepare poetry slam rubric", 1),
        ("Schedule computer lab", 2),
        ("Update bulletin board", 2),           # Monthly
        ("Plan field trip to theater", 1),
        ("Review AP exam prep materials", 2),
        ("Create extra credit assignment", 2),
        ("Update class website", 4),            # Biweekly
    ],
    "church": [
        ("Prepare Sunday School lesson", 9),    # Weekly
        ("Study James passage", 9),             # Weekly
        ("Print discussion guides", 8),         # Weekly
        ("Visit Mrs. Patterson (homebound)", 2), # Monthly
        ("Follow up on benevolence request", 2),
        ("Recruit communion servers", 1),
        ("Plan Easter breakfast logistics", 1),
        ("Coordinate meals ministry", 2),
        ("Update prayer list", 4),              # Biweekly
        ("Send cards to shut-ins", 2),          # Monthly
        ("Review deacon budget report", 2),     # Monthly
        ("VBS volunteer signup", 1),
    ],
    "book_writing": [
        ("Write Chapter 7 - Differentiation", 1),
        ("Write Chapter 8 - Parent Partnership", 1),
        ("Write Chapter 9 - Self-Care", 1),
        ("Write Chapter 10 - When Nothing Works", 1),
        ("Revise Chapter 3 - Relationships", 1),
        ("Revise Chapter 5 - Hook Strategies", 1),
        ("Review editor feedback", 4),          # Multiple rounds
        ("Research growth mindset studies", 1),
        ("Add classroom vignettes", 2),
        ("Format citations", 1),
        ("Send draft to editor", 2),
        ("Review contract terms", 1),
        ("Write acknowledgments section", 1),
        ("Approve cover design", 1),
    ],
    "speaking": [
        ("Prepare conference slides", 2),
        ("Create workshop handouts", 2),
        ("Practice presentation", 4),
        ("Submit session proposal", 1),
        ("Book hotel for conference", 1),
        ("Send follow-up resources", 2),        # After presentations
        ("Update speaker bio", 1),
        ("Prepare strategy cards", 1),
    ],
    "family": [
        ("Help Lily with algebra", 4),          # Biweekly
        ("Help Nathan with reading", 4),        # Biweekly
        ("Plan date night with David", 4),      # Biweekly
        ("Schedule kids' checkups", 1),
        ("Sign volleyball permission slip", 1),
        ("Buy Nathan's scout uniform", 1),
        ("Plan Lily's birthday party", 1),
        ("Buy school supplies for kids", 1),
        ("Pack Nathan's camping gear", 1),
        ("RSVP to Lily's friend's party", 1),
        ("Schedule family photos", 1),
        ("Plan spring break activities", 1),
        ("Order Nathan's birthday cake", 1),
    ],
    "personal": [
        ("Morning walk / exercise", 12),        # Regular
        ("Call insurance about claim", 1),
        ("Schedule annual physical", 1),
        ("Renew teaching certificate", 1),
        ("Update resume for portfolio", 1),
        ("Complete PD hours report", 2),        # Semester-based
        ("Read 'Educated' chapter", 8),         # Regular reading
        ("Respond to book club questions", 4),  # Biweekly
        ("Order new walking shoes", 1),
        ("Schedule hair appointment", 2),       # Monthly
        ("Pay quarterly taxes", 1),             # David's accountant handles most
        ("Clean out email inbox", 4),           # Biweekly
    ],
    "community": [
        ("Tutoring session - Maria", 8),        # Weekly
        ("Tutoring session - James", 8),        # Weekly
        ("Prepare tutoring materials", 4),      # Biweekly
        ("HOA meeting prep", 2),                # Monthly
        ("Draft HOA newsletter", 2),            # Monthly
        ("Mentor meeting with new teacher", 4), # Biweekly
        ("Review HOA budget", 1),
        ("Coordinate block party food", 1),
    ],
}

# Legacy format for teacher tasks
TEACHER_TASK_TITLES = {
    category: [title for title, _ in items]
    for category, items in TEACHER_TASK_TITLES_WITH_LIMITS.items()
}

# Teacher-specific project templates
TEACHER_PROJECT_TEMPLATES = [
    {
        "parent": "Plan Unit: Research Paper Workshop",
        "children": [
            "Choose mentor texts",
            "Create thesis workshop",
            "Design peer review protocol",
            "Build source evaluation activity",
            "Prepare MLA citation guide",
            "Schedule library research day",
        ],
    },
    {
        "parent": "Easter Service Planning",
        "children": [
            "Coordinate sunrise service",
            "Plan Easter breakfast menu",
            "Recruit greeters",
            "Prepare communion elements",
            "Coordinate children's activities",
            "Send invitation postcards",
        ],
    },
    {
        "parent": "Conference Presentation Prep",
        "children": [
            "Finalize session outline",
            "Create slideshow",
            "Print handouts",
            "Prepare strategy cards",
            "Practice delivery",
            "Book travel arrangements",
        ],
    },
    {
        "parent": "Lily's Birthday Party",
        "children": [
            "Send invitations",
            "Order cake",
            "Plan activities",
            "Buy decorations",
            "Prepare party favors",
            "Coordinate food",
        ],
    },
    {
        "parent": "VBS Planning",
        "children": [
            "Choose curriculum",
            "Recruit volunteers",
            "Order supplies",
            "Plan decorations",
            "Coordinate snacks",
            "Schedule setup day",
        ],
    },
    # =========================================================================
    # Additional Teacher Project Templates (10 more to balance with Tech)
    # =========================================================================
    {
        "parent": "Write Book Chapter 7: Differentiation",
        "children": [
            "Research differentiation strategies",
            "Draft main section",
            "Add classroom vignettes",
            "Send to peer reviewer",
            "Revise based on feedback",
            "Submit to editor",
        ],
    },
    {
        "parent": "Publish Teaching Article",
        "children": [
            "Choose article topic",
            "Outline main points",
            "Write first draft",
            "Add classroom examples",
            "Submit to education journal",
            "Respond to editor edits",
        ],
    },
    {
        "parent": "End of Semester Grading",
        "children": [
            "Collect final papers",
            "Grade essay batch",
            "Calculate final averages",
            "Enter grades in system",
            "Write student comments",
            "Submit to administration",
        ],
    },
    {
        "parent": "Classroom Library Refresh",
        "children": [
            "Audit current book collection",
            "Research new diverse titles",
            "Submit purchase request",
            "Process book donations",
            "Organize library shelves",
            "Create display for new books",
        ],
    },
    {
        "parent": "Nathan's Scout Campout",
        "children": [
            "Check camping gear list",
            "Buy camping supplies",
            "Pack tent and sleeping bags",
            "Prep campfire meals",
            "Coordinate parent carpool",
            "Plan campsite activities",
        ],
    },
    {
        "parent": "Family Summer Vacation",
        "children": [
            "Research vacation destinations",
            "Book hotel accommodations",
            "Plan daily activities",
            "Pack family luggage",
            "Arrange pet care",
            "Confirm all reservations",
        ],
    },
    {
        "parent": "Professional Certification Renewal",
        "children": [
            "Review renewal requirements",
            "Complete PD hours",
            "Gather documentation",
            "Submit renewal application",
            "Pay certification fees",
            "Update resume with credentials",
        ],
    },
    {
        "parent": "Teacher Wellness Challenge",
        "children": [
            "Set personal fitness goal",
            "Plan healthy meal prep",
            "Track daily progress",
            "Join accountability group",
            "Celebrate weekly milestones",
            "Reflect on wellness journey",
        ],
    },
    {
        "parent": "Host Book Study Group",
        "children": [
            "Select study book",
            "Invite participants",
            "Prepare discussion guide",
            "Set meeting schedule",
            "Facilitate first session",
            "Gather participant feedback",
        ],
    },
    {
        "parent": "State Conference Workshop",
        "children": [
            "Submit workshop proposal",
            "Design session outline",
            "Create presentation materials",
            "Practice delivery",
            "Gather handout supplies",
            "Follow up with attendees",
        ],
    },
]

PROJECT_TEMPLATES = [
    {
        "parent": "Plan a Trip",
        "children": [
            "Pick destination",
            "Book flights",
            "Reserve lodging",
            "Create itinerary",
            "Arrange transportation",
            "Prepare packing list",
        ],
    },
    {
        "parent": "Launch Marketing Campaign",
        "children": [
            "Define target audience",
            "Draft messaging",
            "Design creatives",
            "Set budget",
            "Schedule channels",
            "Review metrics",
        ],
    },
    {
        "parent": "Organize Office Move",
        "children": [
            "Inventory equipment",
            "Label boxes",
            "Coordinate movers",
            "Update address",
            "Set up network",
            "Unpack essentials",
        ],
    },
    {
        "parent": "Build New Website",
        "children": [
            "Gather requirements",
            "Create sitemap",
            "Design mockups",
            "Implement pages",
            "Set up analytics",
            "QA and launch",
        ],
    },
    {
        "parent": "Prepare Product Launch",
        "children": [
            "Finalize features",
            "Create launch plan",
            "Update documentation",
            "Train support",
            "Press outreach",
            "Go-live checklist",
        ],
    },
    {
        "parent": "Host Team Offsite",
        "children": [
            "Choose dates",
            "Book venue",
            "Plan agenda",
            "Arrange travel",
            "Cater meals",
            "Send invites",
        ],
    },
    {
        "parent": "Quarterly Budget Review",
        "children": [
            "Collect expense reports",
            "Forecast revenue",
            "Identify cuts",
            "Update spreadsheet",
            "Present to leadership",
        ],
    },
    {
        "parent": "Hire New Engineer",
        "children": [
            "Write job description",
            "Post listings",
            "Screen resumes",
            "Schedule interviews",
            "Collect feedback",
            "Make offer",
        ],
    },
    {
        "parent": "Audit Security",
        "children": [
            "Review access logs",
            "Check permissions",
            "Update policies",
            "Run vulnerability scan",
            "Document findings",
        ],
    },
    {
        "parent": "Design Mobile App",
        "children": [
            "Define user flows",
            "Create wireframes",
            "Build prototype",
            "Test with users",
            "Iterate UI",
            "Handoff specs",
        ],
    },
    {
        "parent": "Write Annual Report",
        "children": [
            "Gather data",
            "Outline sections",
            "Draft narrative",
            "Create charts",
            "Review edits",
            "Publish report",
        ],
    },
    {
        "parent": "Improve Onboarding",
        "children": [
            "Map current steps",
            "Identify gaps",
            "Update checklist",
            "Record walkthrough",
            "Collect feedback",
        ],
    },
    {
        "parent": "Customer Survey",
        "children": [
            "Define goals",
            "Draft questions",
            "Set audience",
            "Launch survey",
            "Analyze results",
            "Share insights",
        ],
    },
    {
        "parent": "Migrate Database",
        "children": [
            "Inventory tables",
            "Plan migration",
            "Back up data",
            "Execute migration",
            "Validate integrity",
            "Monitor performance",
        ],
    },
    {
        "parent": "Implement New Feature",
        "children": [
            "Define scope",
            "Break into tasks",
            "Implement core logic",
            "Add tests",
            "QA review",
            "Release",
        ],
    },
    {
        "parent": "Create Training Program",
        "children": [
            "Define objectives",
            "Build curriculum",
            "Create materials",
            "Schedule sessions",
            "Gather feedback",
        ],
    },
    {
        "parent": "Plan Fundraiser",
        "children": [
            "Set fundraising goal",
            "Secure venue",
            "Recruit volunteers",
            "Promote event",
            "Manage registrations",
            "Send thank yous",
        ],
    },
    {
        "parent": "Renovate Kitchen",
        "children": [
            "Set budget",
            "Choose contractor",
            "Select materials",
            "Schedule work",
            "Inspect progress",
            "Final walkthrough",
        ],
    },
    {
        "parent": "Start Personal Budget",
        "children": [
            "Track expenses",
            "Categorize spending",
            "Set monthly limits",
            "Automate savings",
            "Review weekly",
        ],
    },
    {
        "parent": "Write Book Draft",
        "children": [
            "Outline chapters",
            "Set writing schedule",
            "Draft chapters",
            "Revise draft",
            "Get beta readers",
            "Final edits",
        ],
    },
    {
        "parent": "Launch Newsletter",
        "children": [
            "Choose platform",
            "Draft template",
            "Build list",
            "Write first issue",
            "Schedule send",
            "Review analytics",
        ],
    },
    {
        "parent": "Plan Wedding",
        "children": [
            "Set date",
            "Book venue",
            "Choose vendors",
            "Send invites",
            "Plan ceremony",
            "Finalize timeline",
        ],
    },
    {
        "parent": "Prepare Conference Talk",
        "children": [
            "Pick topic",
            "Build slides",
            "Practice talk",
            "Rehearse timing",
            "Gather feedback",
            "Final polish",
        ],
    },
    {
        "parent": "Create Podcast",
        "children": [
            "Choose format",
            "Buy equipment",
            "Record pilot",
            "Edit audio",
            "Publish episode",
            "Promote show",
        ],
    },
    {
        "parent": "Clean and Organize Home",
        "children": [
            "Declutter rooms",
            "Donate items",
            "Organize storage",
            "Deep clean",
            "Set maintenance routine",
        ],
    },
    {
        "parent": "Launch Online Course",
        "children": [
            "Define outcomes",
            "Create lesson plan",
            "Record videos",
            "Build exercises",
            "Upload content",
            "Market course",
        ],
    },
    {
        "parent": "Improve Fitness Routine",
        "children": [
            "Set goals",
            "Choose program",
            "Schedule workouts",
            "Track progress",
            "Adjust plan",
        ],
    },
    {
        "parent": "Plan Garden",
        "children": [
            "Design layout",
            "Pick plants",
            "Buy supplies",
            "Prepare soil",
            "Plant seeds",
            "Set watering schedule",
        ],
    },
    {
        "parent": "Upgrade Home Network",
        "children": [
            "Audit devices",
            "Choose router",
            "Update firmware",
            "Optimize placement",
            "Test coverage",
        ],
    },
    {
        "parent": "Create Investment Plan",
        "children": [
            "Define risk level",
            "Research options",
            "Allocate funds",
            "Set auto-invest",
            "Review quarterly",
        ],
    },
]

# ============================================================================
# Tech Professional Life Simulator - Intelligent Content Generation
# ============================================================================
#
# This simulator creates realistic, temporally-coherent content for a tech
# professional over a 60-day period. Content evolves naturally with:
# - Progressive storylines (fitness journey, side project, career growth)
# - Day-of-week patterns (Monday planning, Friday reviews)
# - Sprint cycles (2-week agile sprints)
# - Interconnected references between days
# ============================================================================


class TechProfessionalSimulator:
    """
    Simulates 60 days in the life of a tech professional.

    Tracks state across days to generate coherent, realistic content including:
    - Work sprints with planning, standups, and reviews
    - Fitness journey with progressive improvements
    - Side project development from idea to launch
    - Career growth with learning milestones
    - Personal reflections that reference actual events
    """

    def __init__(self, start_date: datetime):
        self.start_date = start_date
        self.current_day = 0

        # Work state
        self.sprint_number = 1
        self.sprint_day = 1  # 1-10 (2-week sprint, weekdays only)
        self.current_project = "Phoenix API Migration"
        self.pr_number = 2341
        self.build_time_seconds = 272  # Improves over time
        self.test_coverage = 94.2  # Improves over time

        # Team members with consistent roles
        self.team = {
            "pm": "Sarah",
            "tech_lead": "Mike",
            "qa": "Alex",
            "devops": "Jordan",
            "designer": "Taylor",
            "manager": "Chris",
            "mentor": "Jamie"
        }

        # Fitness journey state (8 weeks)
        self.fitness_week = 1
        self.five_k_time_seconds = 1470  # 24:30, improves each week
        self.deadlift_weight = 185  # lbs, improves
        self.gym_streak = 0

        # Side project "TaskFlow" phases
        self.side_project_phase = 0  # 0=idea, 1=design, 2=mvp, 3=beta, 4=launch
        self.side_project_phases = [
            "Brainstorming",
            "Design & Architecture",
            "MVP Development",
            "Beta Testing",
            "Launch Prep",
            "Post-Launch"
        ]
        self.side_project_commits = 0

        # Career development
        self.k8s_chapters_read = 0
        self.leetcode_problems = 0
        self.conference_prep_stage = 0  # 0=not started, 1=proposal, 2=accepted, 3=slides

        # 1:1 meeting topics (cycles through)
        self.one_on_one_count = 0
        self.one_on_one_topics = [
            ("career growth", "promotion timeline"),
            ("project challenges", "team dynamics"),
            ("skill development", "conference attendance"),
            ("work-life balance", "PTO planning"),
            ("feedback session", "performance review prep"),
        ]

        # Books being read
        self.current_book = "Designing Data-Intensive Applications"
        self.book_progress = 0  # chapters

        # Journal themes for variety
        self.journal_themes_used = set()

        # =====================================================================
        # Personal Life Storylines (temporal progression)
        # =====================================================================

        # Apartment hunting → signing → moving storyline
        self.apartment_phase = 0  # 0=searching, 1=found place, 2=signed, 3=moving, 4=settled
        self.apartment_phases = [
            "searching",
            "found a great place",
            "lease signed",
            "moving week",
            "settled in"
        ]
        self.apartments_viewed = 0
        self.move_in_date = start_date + timedelta(days=42)  # ~6 weeks in

        # Relationship/social life milestones
        self.relationship_phase = 0  # 0=single, 1=dating, 2=exclusive, 3=introduced to friends
        self.dates_count = 0
        self.partner_name = "Morgan"

        # Hobby progression: Learning guitar
        self.guitar_week = 0
        self.guitar_songs_learned = []
        self.guitar_practice_days = 0

        # Family events tracking
        self.family_events = [
            (14, "Mom's birthday coming up - need to plan"),
            (21, "Mom's birthday! Called her, she loved the gift"),
            (35, "Sister visiting this weekend"),
            (37, "Had a great time with sister - she met Morgan"),
            (50, "Dad's retirement party planning"),
        ]

        # Friend group events
        self.friend_events = [
            (7, "Game night at Marcus's place"),
            (18, "Old college friend in town - dinner at that new Italian place"),
            (28, "Book club meetup - discussing 'Project Hail Mary'"),
            (42, "Helped Dave move - pizza and beers after"),
            (55, "Planning surprise birthday for Priya"),
        ]

        # Health/wellness beyond fitness
        self.wellness_events = [
            (10, "Annual physical scheduled for next week"),
            (12, "Physical done - everything looks good, cholesterol slightly high"),
            (25, "Started meditation using Headspace - day 1"),
            (32, "Meditation streak: 7 days! Actually feeling calmer"),
            (45, "Dentist checkup - no cavities!"),
        ]

        # =====================================================================
        # Community Service & Mentorship State
        # =====================================================================
        self.volunteer_org = "Code2Community"
        self.mentee_name = "Alex"  # Junior dev or coding student
        self.mentee_week = 1
        self.volunteer_hours = 0
        self.mentorship_phase = 0  # 0=starting, 1=building, 2=milestone, 3=independent

        # Mentorship storyline events
        self.community_events = [
            (8, "First mentoring session with Alex - introduced to Python basics"),
            (15, "Alex completed first coding challenge!"),
            (25, "Pair programming session - built a calculator app together"),
            (35, "Alex struggling with recursion - spent extra time explaining"),
            (45, "Alex's first pull request merged!"),
            (55, "Planning Alex's portfolio project"),
        ]

        # =====================================================================
        # Personal Values & Reflection State
        # =====================================================================
        self.reflection_practice = "morning_journal"
        self.values_themes = ["growth", "balance", "contribution", "connection"]
        self.meaningful_moments = []
        self.life_goals_reviewed = False
        self.journal_streak = 0
        self.quarterly_review_done = False

        # Reflection storyline events
        self.reflection_events = [
            (5, "Started morning journaling practice - 10 mins before work"),
            (20, "Reflected on career purpose - want to give back more"),
            (30, "Quarterly life goals review - feeling on track"),
            (50, "Meditation retreat weekend - powerful reset"),
        ]

        # =====================================================================
        # Calendar Event Correlation State
        # =====================================================================
        self.events_by_day = {}  # Will be set from generate_memos
        self.upcoming_events_cache = {}  # Cache for looking ahead

    def advance_day(self):
        """Move simulation forward one day."""
        self.current_day += 1

        # Advance fitness week every 7 days
        if self.current_day % 7 == 0:
            self.fitness_week = min(9, self.fitness_week + 1)
            # Improve metrics
            self.five_k_time_seconds = max(1320, self.five_k_time_seconds - random.randint(10, 30))
            self.deadlift_weight = min(245, self.deadlift_weight + random.randint(0, 5))

        # Advance sprint (10 working days = 2 weeks)
        if self.current_day % 14 == 0:
            self.sprint_number += 1
            self.sprint_day = 1
            # Improve build metrics
            self.build_time_seconds = max(180, self.build_time_seconds - random.randint(5, 15))
            self.test_coverage = min(99.5, self.test_coverage + random.uniform(0.2, 0.8))

        # Advance side project every ~12 days
        if self.current_day % 12 == 0 and self.side_project_phase < 5:
            self.side_project_phase += 1

        # Increment side project commits on coding days
        if random.random() < 0.4:
            self.side_project_commits += random.randint(1, 5)

        # Book progress
        if random.random() < 0.3:
            self.book_progress += 1

        # K8s learning
        if random.random() < 0.2:
            self.k8s_chapters_read += 1

        # LeetCode on some days
        if random.random() < 0.15:
            self.leetcode_problems += random.randint(1, 3)

        # =====================================================================
        # Personal Life Progression
        # =====================================================================

        # Apartment hunting progression
        if self.current_day == 10:
            self.apartment_phase = 1  # Found a place
        elif self.current_day == 20:
            self.apartment_phase = 2  # Signed lease
        elif self.current_day == 40:
            self.apartment_phase = 3  # Moving week
        elif self.current_day == 45:
            self.apartment_phase = 4  # Settled

        if self.apartment_phase == 0 and random.random() < 0.3:
            self.apartments_viewed += 1

        # Relationship progression
        if self.current_day == 5:
            self.relationship_phase = 1  # Started dating
        elif self.current_day == 25:
            self.relationship_phase = 2  # Exclusive
        elif self.current_day == 45:
            self.relationship_phase = 3  # Met friends/family

        if self.relationship_phase >= 1:
            if random.random() < 0.2:
                self.dates_count += 1

        # Guitar progression (every week)
        if self.current_day % 7 == 0:
            self.guitar_week += 1
            if self.guitar_week == 2:
                self.guitar_songs_learned.append("Wonderwall (intro)")
            elif self.guitar_week == 4:
                self.guitar_songs_learned.append("Horse With No Name")
            elif self.guitar_week == 6:
                self.guitar_songs_learned.append("Wish You Were Here")
            elif self.guitar_week == 8:
                self.guitar_songs_learned.append("Blackbird (working on it)")

        if random.random() < 0.4:
            self.guitar_practice_days += 1

        # =====================================================================
        # Community & Reflection Progression
        # =====================================================================

        # Mentorship progression (every 7 days)
        if self.current_day % 7 == 0:
            self.mentee_week += 1
            self.volunteer_hours += 2  # ~2 hours per session

        # Mentorship phase progression (every ~15 days)
        if self.current_day == 15:
            self.mentorship_phase = 1  # Building skills
        elif self.current_day == 30:
            self.mentorship_phase = 2  # Milestone reached
        elif self.current_day == 45:
            self.mentorship_phase = 3  # Gaining independence

        # Journal streak tracking
        if self.current_day % 7 == 0:
            self.journal_streak += 7  # Weekly increment

        # Quarterly review at day 30 and 60
        if self.current_day == 30:
            self.quarterly_review_done = True
            self.life_goals_reviewed = True

    def set_events_context(self, events_by_day: dict):
        """Set the calendar events context for event-aware content generation."""
        self.events_by_day = events_by_day

    def get_date_for_day(self, day_offset: int = 0) -> datetime:
        """Get the date for a specific day in the simulation."""
        return self.start_date + timedelta(days=self.current_day + day_offset)

    def format_time(self, seconds: int) -> str:
        """Format seconds as MM:SS."""
        return f"{seconds // 60}:{seconds % 60:02d}"

    def get_sprint_phase(self) -> str:
        """Get current sprint phase based on day within sprint."""
        day_in_sprint = (self.current_day % 14) + 1
        if day_in_sprint <= 2:
            return "planning"
        elif day_in_sprint <= 12:
            return "execution"
        else:
            return "review"

    # =========================================================================
    # Calendar Event Correlation Helpers
    # =========================================================================

    def get_events_for_date(self, date: datetime) -> List[str]:
        """Get calendar events for a specific date."""
        day_id = date.strftime("%Y-%m-%d")
        return self.events_by_day.get(day_id, [])

    def get_upcoming_events(self, date: datetime, days_ahead: int = 7) -> List[Tuple[datetime, str]]:
        """Get events for the next N days."""
        upcoming = []
        for i in range(1, days_ahead + 1):
            future_date = date + timedelta(days=i)
            for event in self.get_events_for_date(future_date):
                upcoming.append((future_date, event))
        return upcoming

    def find_events_matching(self, events: List[str], keywords: List[str]) -> List[str]:
        """Find events that match any of the given keywords."""
        matching = []
        for event in events:
            event_lower = event.lower()
            if any(kw.lower() in event_lower for kw in keywords):
                matching.append(event)
        return matching

    def find_one_on_one_events(self, events: List[str]) -> List[str]:
        """Find 1:1 meeting events."""
        return self.find_events_matching(events, ["1:1", "one on one", "mentorship", "career discussion"])

    def find_meeting_events(self, events: List[str]) -> List[str]:
        """Find work meeting events."""
        return self.find_events_matching(events, [
            "standup", "sync", "review", "planning", "retro",
            "all-hands", "sprint", "demo", "architecture"
        ])

    def find_personal_events(self, events: List[str]) -> List[str]:
        """Find personal appointment events."""
        return self.find_events_matching(events, [
            "dentist", "doctor", "gym", "lunch", "coffee", "haircut",
            "vet", "car service", "appointment"
        ])

    def find_client_events(self, events: List[str]) -> List[str]:
        """Find client-related events."""
        return self.find_events_matching(events, [
            "client", "call", "demo", "interview", "vendor", "partner"
        ])

    def get_personal_life_context(self) -> str:
        """Get current personal life storyline snippets for journal entries."""
        context_parts = []

        # Apartment storyline
        if self.apartment_phase == 0:
            context_parts.append(f"Still apartment hunting. Viewed {self.apartments_viewed} places so far.")
        elif self.apartment_phase == 1:
            context_parts.append("Found a great apartment in the Mission! 1BR with good natural light.")
        elif self.apartment_phase == 2:
            context_parts.append(f"Lease signed! Moving in on {self.move_in_date.strftime('%B %d')}.")
        elif self.apartment_phase == 3:
            context_parts.append("Moving week chaos. Boxes everywhere but making progress.")
        elif self.apartment_phase == 4:
            context_parts.append("Finally settled into the new place. Love the neighborhood.")

        # Relationship storyline
        if self.relationship_phase == 1 and self.dates_count > 0:
            context_parts.append(f"Dating {self.partner_name} - things are going well. Date #{self.dates_count} coming up.")
        elif self.relationship_phase == 2:
            context_parts.append(f"Made it official with {self.partner_name}! Feeling happy.")
        elif self.relationship_phase == 3:
            context_parts.append(f"{self.partner_name} met my friends last weekend. Everyone got along great.")

        # Guitar progression
        if self.guitar_week > 0:
            if self.guitar_songs_learned:
                songs = ", ".join(self.guitar_songs_learned[-2:])  # Last 2 songs
                context_parts.append(f"Guitar week {self.guitar_week}: {self.guitar_practice_days} practice sessions. Learning: {songs}")
            else:
                context_parts.append(f"Guitar week {self.guitar_week}: Still working on basic chords. Fingers hurt but improving!")

        # Check for special events on this day
        for day, event_text in self.family_events:
            if day == self.current_day:
                context_parts.append(f"Family: {event_text}")
                break

        for day, event_text in self.friend_events:
            if day == self.current_day:
                context_parts.append(f"Friends: {event_text}")
                break

        for day, event_text in self.wellness_events:
            if day == self.current_day:
                context_parts.append(f"Health: {event_text}")
                break

        return "\n".join(context_parts) if context_parts else ""

    # =========================================================================
    # Work Meeting Content Generators
    # =========================================================================

    def generate_sprint_planning(self, date: datetime) -> str:
        """Generate sprint planning meeting notes."""
        velocity = 21 + self.sprint_number * 2 + random.randint(-2, 3)
        stories = random.randint(8, 12)

        return f"""## Sprint {self.sprint_number} Planning
Date: {date.strftime("%B %d, %Y")}

### Attendees
- {self.team['pm']} (PM), {self.team['tech_lead']} (Tech Lead), {self.team['qa']} (QA), {self.team['devops']} (DevOps)

### Sprint Goals
1. Complete {self.current_project} phase {self.sprint_number}
2. Reduce API latency to <50ms p95
3. Implement user feedback from beta

### Capacity
- Team velocity (last 3 sprints): {velocity-4}, {velocity-2}, {velocity}
- Target this sprint: {velocity + 2} points
- {stories} stories committed

### Key Stories
- [ ] PHOE-{1200 + self.sprint_number * 10}: Auth service migration (5 pts)
- [ ] PHOE-{1201 + self.sprint_number * 10}: API rate limiting (3 pts)
- [ ] PHOE-{1202 + self.sprint_number * 10}: Dashboard redesign (8 pts)
- [ ] PHOE-{1203 + self.sprint_number * 10}: Performance monitoring (3 pts)

### Risks & Dependencies
- Waiting on {self.team['designer']} for new mockups (due Wednesday)
- {self.team['devops']} OOO Thursday-Friday
- External API vendor response pending

### Action Items
- [ ] {self.team['tech_lead']}: Break down PHOE-{1202 + self.sprint_number * 10} into subtasks
- [ ] {self.team['qa']}: Update test plan for auth changes
- [ ] Schedule mid-sprint check-in for Wednesday"""

    def generate_standup_notes(self, date: datetime) -> str:
        """Generate daily standup notes."""
        day_of_week = date.strftime("%A")
        pr_num = self.pr_number + random.randint(0, 20)

        blockers = random.choice([
            f"Waiting on {self.team['designer']} for updated designs",
            "CI pipeline flaky - investigating",
            f"Need {self.team['devops']} to review infra changes",
            "No blockers today",
            "Dependency update broke tests - fixing",
        ])

        return f"""## Daily Standup - {day_of_week}
Date: {date.strftime("%B %d, %Y")}

### My Updates

**Yesterday:**
- Completed PR #{pr_num} for API endpoint refactoring
- Reviewed {self.team['qa']}'s test automation changes
- Pair programmed with {self.team['tech_lead']} on caching strategy

**Today:**
- Continue work on PHOE-{1200 + self.sprint_number * 10}
- Address code review feedback on #{pr_num + 1}
- Sync with {self.team['devops']} on deployment pipeline

**Blockers:**
- {blockers}

### Team Notes
- {self.team['pm']}: Demo prep for stakeholders Friday
- {self.team['qa']}: Found edge case in auth flow - creating ticket
- {self.team['tech_lead']}: Architecture review scheduled for 2pm"""

    def generate_sprint_review(self, date: datetime) -> str:
        """Generate sprint review/retro notes."""
        completed = random.randint(7, 10)
        carried = random.randint(1, 3)
        velocity = 21 + self.sprint_number * 2 + random.randint(-1, 2)

        return f"""## Sprint {self.sprint_number} Review & Retrospective
Date: {date.strftime("%B %d, %Y")}

### Sprint Metrics
- **Velocity**: {velocity} points (target was {velocity + 2})
- **Stories Completed**: {completed}/{completed + carried}
- **Carried Over**: {carried} stories
- **Bug Fixes**: {random.randint(3, 8)}

### Demo Highlights
1. New auth flow - passwordless login working
2. Dashboard performance improved 40%
3. API rate limiting in staging

### Stakeholder Feedback
- Product team loved the new onboarding
- Request: Add export functionality (added to backlog)
- CEO asked about mobile timeline

### Retrospective

**What went well:**
- Great collaboration between dev and QA
- CI/CD improvements saved hours
- {self.team['tech_lead']}'s architecture docs were helpful

**What could improve:**
- Too many meetings mid-sprint
- Need better estimation for UI work
- Flaky tests causing delays

**Action Items:**
- [ ] {self.team['pm']}: Block focus time on calendar
- [ ] {self.team['qa']}: Investigate test reliability
- [ ] Me: Document new API patterns

### Next Sprint Preview
- Focus on mobile responsiveness
- Tech debt cleanup for auth module
- Start payment integration research"""

    def generate_one_on_one(self, date: datetime) -> str:
        """Generate 1:1 meeting notes with manager."""
        topic1, topic2 = self.one_on_one_topics[self.one_on_one_count % len(self.one_on_one_topics)]
        self.one_on_one_count += 1

        return f"""## 1:1 with {self.team['manager']}
Date: {date.strftime("%B %d, %Y")}

### Topics Discussed

**{topic1.title()}**
- Discussed path to senior engineer role
- {self.team['manager']} suggested leading the next project initiative
- Goal: Present architecture proposal by end of Q1

**{topic2.title()}**
- Team morale is good after successful launch
- {self.team['tech_lead']} transitioning some responsibilities
- Opportunity to mentor new hire starting next month

### Feedback Received
- Positive: Code quality has improved significantly
- Growth area: Could be more vocal in design discussions
- {self.team['manager']} noticed my K8s deep-dive - keep it up

### My Questions
- Timeline for performance review cycle?
- Budget for conference attendance (KubeCon)?
- Thoughts on 20% time for side projects?

### Action Items
- [ ] Draft proposal for new monitoring system
- [ ] Schedule skip-level with {self.team['mentor']}
- [ ] Complete self-review by end of month

### Next 1:1
- Two weeks: {(date + timedelta(days=14)).strftime("%B %d")}
- Topic: Review proposal draft"""

    def generate_code_review(self, date: datetime) -> str:
        """Generate code review session notes."""
        pr_num = self.pr_number + self.current_day

        return f"""## Code Review Session
Date: {date.strftime("%B %d, %Y")}

### PRs Reviewed

**PR #{pr_num}: Auth Service Refactor** (by {self.team['tech_lead']})
- Good separation of concerns
- Suggested extracting token validation to util
- Approved with minor changes

**PR #{pr_num + 1}: API Rate Limiting** (by me)
- {self.team['qa']} caught edge case with burst limits
- Added unit tests for Redis failover
- Merged after addressing feedback

**PR #{pr_num + 2}: Dashboard Components** (by {self.team['designer']})
- Accessibility improvements needed
- Performance concern with large data sets
- Requested changes, follow-up Monday

### Code Quality Metrics
- Build time: {self.build_time_seconds // 60}m {self.build_time_seconds % 60}s (down from last week)
- Test coverage: {self.test_coverage:.1f}%
- Lint errors: 0
- Bundle size: {2.1 - self.sprint_number * 0.05:.2f}MB

### Patterns to Adopt
- Error boundary pattern from #{pr_num}
- React Query caching strategy
- Zod schema validation

### Tech Debt Noted
- Legacy auth code needs migration (PHOE-999)
- Duplicate API utilities in 3 places
- Consider monorepo for shared components"""

    def generate_architecture_review(self, date: datetime) -> str:
        """Generate architecture review meeting notes."""
        return f"""## Architecture Review: {self.current_project}
Date: {date.strftime("%B %d, %Y")}

### Attendees
- {self.team['tech_lead']} (Lead), {self.team['devops']}, {self.team['mentor']}, Me

### Current Architecture
```
Client → API Gateway → Auth Service → Core API → PostgreSQL
                    ↓
              Redis Cache
```

### Proposed Changes

**1. Microservices Migration**
- Extract payment processing to separate service
- Event-driven communication via Kafka
- Independent deployment and scaling

**2. Performance Improvements**
- Add CDN for static assets
- Implement GraphQL for mobile clients
- Database read replicas for reporting

### Discussion Points
- {self.team['mentor']}: Concern about operational complexity
- {self.team['devops']}: Need to upgrade K8s cluster first
- Consensus: Start with payment service extraction

### Decisions Made
- Proceed with payment service POC
- Keep monolith for core API (for now)
- Invest in observability before splitting

### Next Steps
- [ ] Me: Draft RFC for payment service
- [ ] {self.team['devops']}: K8s upgrade plan
- [ ] {self.team['tech_lead']}: Define service boundaries

### Follow-up
- Review RFC in 2 weeks
- POC demo end of Sprint {self.sprint_number + 1}"""

    # =========================================================================
    # Personal Content Generators
    # =========================================================================

    def generate_fitness_log(self, date: datetime) -> str:
        """Generate weekly fitness tracking log."""
        five_k = self.format_time(self.five_k_time_seconds)
        prev_five_k = self.format_time(self.five_k_time_seconds + random.randint(15, 30))

        workouts = [
            ("Monday", "5K run", five_k),
            ("Wednesday", "Strength training", f"Deadlift: {self.deadlift_weight}lbs"),
            ("Friday", "HIIT session", "30 min"),
            ("Saturday", "Recovery run", "3K easy pace"),
        ]

        return f"""# Fitness Log - Week {self.fitness_week}
Date: {date.strftime("%B %d, %Y")}

## Weekly Summary
- **Workouts completed**: {random.randint(3, 5)}/5 planned
- **Gym streak**: {self.gym_streak + self.fitness_week * 4} days
- **Sleep avg**: {random.uniform(6.5, 7.5):.1f} hours

## Workouts This Week
{chr(10).join(f"- {day}: {workout} ({detail})" for day, workout, detail in workouts)}

## Progress vs Last Week
- 5K time: {five_k} (was {prev_five_k}) {"🎉 PR!" if self.fitness_week > 4 else ""}
- Deadlift: {self.deadlift_weight}lbs (+{random.randint(0, 5)}lbs)
- Body weight: {175 - self.fitness_week * 0.3:.1f}lbs

## How I'm Feeling
{"Strong and motivated! The consistency is paying off." if self.fitness_week > 3 else "Building the habit. Some days are harder than others."}
{"Hit a new PR on the 5K - sub-23 feels achievable now." if self.fitness_week > 5 else ""}

## Goals for Week {self.fitness_week + 1}
- {"Maintain 4+ workouts" if self.fitness_week < 4 else "Push for 5 workouts"}
- {"Try the new HIIT class" if self.fitness_week % 2 == 0 else "Add 5lbs to deadlift"}
- Get to bed by 11pm consistently

## Nutrition Notes
- Meal prep on Sunday working well
- Need more protein on workout days
- Hydration: {random.randint(6, 10)} glasses/day avg"""

    def generate_side_project_log(self, date: datetime) -> str:
        """Generate side project development log."""
        phase = self.side_project_phases[min(self.side_project_phase, 5)]

        phase_content = {
            "Brainstorming": f"""## Concept Exploration

### The Idea: TaskFlow
A minimalist task manager that learns your patterns and suggests optimal scheduling.

### Why This?
- Frustrated with complex tools (Notion, Asana)
- Want something that "just works"
- Opportunity to learn React Native

### Core Features (MVP)
1. Quick task capture (< 2 seconds)
2. Smart due date suggestions
3. Daily focus mode
4. Simple recurring tasks

### Tech Stack Decisions
- **Frontend**: React Native (cross-platform)
- **Backend**: Supabase (fast to set up)
- **State**: Zustand (simple, performant)

### Competition Research
- Todoist: Too many features
- Things 3: Mac only, expensive
- TickTick: Closest to my vision

### Next Steps
- [ ] Sketch initial wireframes
- [ ] Set up React Native environment
- [ ] Create GitHub repo""",

            "Design & Architecture": f"""## TaskFlow - Design Phase

### Wireframes Complete ✓
- Home screen with today's tasks
- Quick add modal
- Settings page
- Weekly review screen

### Technical Architecture
```
React Native App
    ↓
Zustand Store → AsyncStorage (offline)
    ↓
Supabase (sync when online)
```

### Database Schema
- users (id, email, preferences)
- tasks (id, title, due_date, completed, user_id)
- patterns (id, user_id, time_preferences)

### Design System
- Colors: Neutral grays + accent blue
- Typography: Inter for UI, monospace for times
- Spacing: 8px grid

### Commits This Week: {self.side_project_commits}

### Blockers
- Deciding on navigation library (React Navigation vs Expo Router)
- Need to research offline-first patterns

### This Weekend
- [ ] Finalize component library
- [ ] Set up Supabase project
- [ ] Create initial screens""",

            "MVP Development": f"""## TaskFlow - MVP Sprint

### Progress Update
Building out the core functionality. {self.side_project_commits} commits so far.

### Completed This Week
- [x] Basic task CRUD operations
- [x] Local storage with AsyncStorage
- [x] Home screen UI
- [x] Quick add functionality

### In Progress
- [ ] Due date picker component
- [ ] Supabase auth integration
- [ ] Push notifications setup

### Code Snippet - Quick Add
```typescript
const addTask = async (title: string) => {{
  const task = {{
    id: nanoid(),
    title,
    createdAt: Date.now(),
    completed: false,
  }};
  await store.addTask(task);
}};
```

### Challenges Faced
- React Native debugging is painful
- Expo EAS build queue times slow
- Keyboard handling on Android different from iOS

### Metrics
- Bundle size: 12MB (need to optimize)
- Cold start: 1.2s
- Test coverage: 45%

### Next Week Goals
- Complete Supabase sync
- Add onboarding flow
- Internal alpha release""",

            "Beta Testing": f"""## TaskFlow - Beta Phase

### Beta Launch! 🚀
Released to 15 beta testers (friends and colleagues).

### Feedback Summary (Week 1)
- **Positive**: "Love the quick add", "Finally something simple"
- **Issues**: Sync conflicts, crash on Android 12
- **Requests**: Dark mode, widgets, calendar integration

### Bug Fixes This Week
- Fixed sync conflict resolution
- Resolved Android 12 crash (notification permissions)
- Memory leak in task list

### Metrics
- Daily active users: 12/15
- Tasks created: 340
- Crash-free rate: 98.2%

### Commits: {self.side_project_commits}

### Priority Fixes
1. ~~Sync conflicts~~ ✓
2. ~~Android crash~~ ✓
3. Dark mode (in progress)
4. Performance optimization

### User Quotes
> "I've tried so many task apps. This is the first one I actually use daily." - {self.team['qa']}

> "The quick add is genius. Captures my thoughts before I forget." - {self.team['designer']}

### Launch Prep
- [ ] App Store screenshots
- [ ] Write launch blog post
- [ ] Set up analytics (PostHog)""",

            "Launch Prep": f"""## TaskFlow - Launch Prep 🎯

### Launch Checklist
- [x] App Store listing complete
- [x] Privacy policy published
- [x] Marketing site live (taskflow.app)
- [ ] ProductHunt draft ready
- [ ] Launch day social posts scheduled

### Final Polish
- Dark mode complete
- Onboarding flow refined
- Performance: 60fps scrolling achieved

### Marketing Plan
- Day 1: ProductHunt launch
- Day 2-3: Twitter/X thread
- Week 1: HN "Show HN" post
- Week 2: Reach out to reviewers

### Pricing Decision
- Free tier: 50 tasks, local only
- Pro: $4/mo - unlimited + sync + widgets

### Launch Day Tasks
- [ ] Submit to ProductHunt (midnight)
- [ ] Tweet announcement
- [ ] Monitor crash reports
- [ ] Respond to early feedback

### Feeling
Nervous but excited! Months of work coming together. Whatever happens, I learned a ton about React Native and shipping a product.""",

            "Post-Launch": f"""## TaskFlow - Week 1 Post-Launch

### Launch Results! 🎉
- **ProductHunt**: #4 of the day
- **Downloads**: 2,400
- **Pro conversions**: 89 (3.7%)
- **Revenue**: $356

### User Feedback Themes
1. Love the simplicity
2. Want: Widgets (coming soon)
3. Want: Team features (considering)
4. Bug: Rare sync issues on slow networks

### Press Coverage
- Featured in "Top 10 Productivity Apps This Week"
- Mentioned in iOS dev newsletter

### What I Learned
- Launching is just the beginning
- Support takes more time than expected
- Users find bugs I never imagined

### Next Steps
- Widgets for iOS 17
- Android widget parity
- Explore team/collaboration features

### Commits total: {self.side_project_commits}

### Reflection
Building TaskFlow from idea to launch in ~2 months while working full-time was intense but rewarding. The best part: people actually use something I made!""",
        }

        return f"""# Side Project: TaskFlow
Phase: {phase}
Date: {date.strftime("%B %d, %Y")}

{phase_content.get(phase, phase_content["Brainstorming"])}"""

    def generate_learning_log(self, date: datetime) -> str:
        """Generate career development / learning log."""
        book_chapter = min(self.book_progress, 12)

        return f"""# Learning Log
Date: {date.strftime("%B %d, %Y")}

## Currently Reading
**{self.current_book}**
- Progress: Chapter {book_chapter}/12
- Key insight: The importance of idempotent operations in distributed systems
- Will apply: To our event processing pipeline

## Kubernetes Deep Dive
- Chapters completed: {self.k8s_chapters_read}
- This week: Learned about StatefulSets and persistent volumes
- Practice: Set up local minikube cluster
- Goal: CKA certification by Q2

## LeetCode Progress
- Problems solved: {self.leetcode_problems}
- Focus areas: Dynamic programming, graphs
- Today's problem: "Longest Increasing Subsequence"
- {"Struggled with the optimization step" if random.random() < 0.3 else "Solved in 25 minutes"}

## Conference Prep: KubeCon
{"- Proposal submitted! Fingers crossed." if self.conference_prep_stage >= 1 else "- Working on talk proposal about our migration story"}
{"- ACCEPTED! Now need to create slides." if self.conference_prep_stage >= 2 else ""}
{"- Slides 60% done. Practicing delivery." if self.conference_prep_stage >= 3 else ""}

## Team Knowledge Sharing
- Presented: "Intro to GraphQL" at team lunch
- Feedback: Team wants follow-up on subscriptions
- Next topic: "React Server Components"

## Resources Queue
- [ ] Finish DDIA chapter on replication
- [ ] Watch: "Scaling Instagram" talk
- [ ] Read: Stripe's API design guidelines
- [ ] Course: Complete AWS Solutions Architect module 4

## Reflection
{random.choice([
    "Feeling motivated. The K8s knowledge is already helping at work.",
    "Some days learning feels slow, but looking back I've come far.",
    "The book is dense but worth it. Taking good notes.",
    "Excited about the conference opportunity - great motivation to learn.",
])}"""

    def generate_weekly_review(self, date: datetime) -> str:
        """Generate weekly reflection journal."""
        week_num = self.current_day // 7 + 1

        wins = random.sample([
            "Shipped the auth migration on time",
            "Got positive feedback on code review",
            f"Hit a 5K PR: {self.format_time(self.five_k_time_seconds)}",
            f"Side project reached {self.side_project_phases[min(self.side_project_phase, 5)]} phase",
            "Great 1:1 with manager - clear path to promotion",
            "Helped onboard the new team member",
            "Finally understood distributed consensus",
            "Maintained gym streak all week",
        ], 3)

        challenges = random.sample([
            "Struggled with focus during afternoon meetings",
            "Sleep schedule got off track",
            "Felt imposter syndrome during architecture review",
            "Side project progress slower than hoped",
            "Difficult conversation with stakeholder",
        ], 2)

        return f"""# Weekly Review - Week {week_num}
Date: {date.strftime("%B %d, %Y")}

## This Week's Wins
{chr(10).join(f"- {win}" for win in wins)}

## Challenges Faced
{chr(10).join(f"- {challenge}" for challenge in challenges)}

## Key Learnings
- {random.choice([
    "Saying no to meetings protects deep work time",
    "Writing things down beats trying to remember",
    "Small consistent progress beats sporadic big efforts",
    "Asking for help earlier saves time overall",
])}

## Time Audit
- Deep work: {random.randint(15, 22)} hours
- Meetings: {random.randint(8, 14)} hours
- Admin/email: {random.randint(3, 6)} hours
- Learning: {random.randint(3, 5)} hours

## Energy Levels
- Mon: {"🟢" if random.random() > 0.3 else "🟡"} Started strong
- Tue: {"🟢" if random.random() > 0.4 else "🟡"} Good momentum
- Wed: {"🟡" if random.random() > 0.3 else "🔴"} Mid-week slump
- Thu: {"🟢" if random.random() > 0.4 else "🟡"} Recovered
- Fri: {"🟢" if random.random() > 0.3 else "🟡"} Finished strong

## Next Week's Priorities
1. {random.choice(["Complete sprint stories", "Finish feature branch", "Review team PRs"])}
2. {random.choice(["Continue K8s learning", "Work on side project", "Prepare presentation"])}
3. {random.choice(["Maintain fitness routine", "Catch up on sleep", "Schedule social time"])}

## Gratitude
- Great team that supports each other
- Flexible work arrangement
- Health to pursue my goals

## One Thing to Improve
{random.choice([
    "Batch email checking to 2x/day",
    "Take more breaks between meetings",
    "Prepare tomorrow's priorities the night before",
    "Drink more water during the day",
])}"""

    # =========================================================================
    # Community & Reflection Content Generators
    # =========================================================================

    def generate_mentorship_session(self, date: datetime) -> str:
        """Generate mentorship session notes with Alex."""
        week = self.mentee_week

        # Progressive topics based on mentee's journey
        topics_by_phase = {
            0: [  # Starting out
                ("Python basics", "variables, loops, functions"),
                ("Development environment", "VS Code, terminal basics"),
                ("Git fundamentals", "commit, push, pull"),
            ],
            1: [  # Building skills
                ("Object-oriented programming", "classes, inheritance"),
                ("Debugging techniques", "print statements, breakpoints"),
                ("Code organization", "modules, imports"),
            ],
            2: [  # Reaching milestones
                ("Building a project", "calculator app, to-do list"),
                ("API integration", "requests library, JSON parsing"),
                ("Testing basics", "unittest, pytest intro"),
            ],
            3: [  # Gaining independence
                ("Code review practices", "reading others' code"),
                ("Contributing to open source", "finding good first issues"),
                ("Portfolio building", "GitHub profile, project READMEs"),
            ],
        }

        phase = min(self.mentorship_phase, 3)
        topic, subtopic = random.choice(topics_by_phase[phase])

        challenges = [
            f"Alex got confused about {random.choice(['scope', 'indentation', 'type errors', 'loop logic'])}",
            "Took extra time to explain the concept with real-world analogy",
            "Worked through a debugging session together",
            "Alex asked great questions about best practices",
        ]

        wins = [
            "Alex completed the exercise without hints!",
            "Saw real improvement in problem-solving approach",
            "Alex explained the concept back to me correctly",
            "First time Alex spotted a bug on their own",
        ]

        return f"""# Mentorship Session - Week {week}
Date: {date.strftime("%B %d, %Y")}
Mentee: {self.mentee_name}
Organization: {self.volunteer_org}

## Today's Topic
**{topic}**: {subtopic}

## Session Notes
- Started with review of last week's homework
- {random.choice(challenges)}
- {random.choice(wins)}
- Assigned practice exercises for the week

## {self.mentee_name}'s Progress
- **Confidence**: {"Growing" if week > 2 else "Building foundation"}
- **Problem-solving**: {"Improving each week" if week > 3 else "Learning to break down problems"}
- **Questions**: {"Asking deeper questions now" if week > 4 else "Good curiosity"}

## Homework Assigned
- [ ] Complete 3 practice problems on {topic.lower()}
- [ ] Read one chapter of "Automate the Boring Stuff"
- [ ] Try building a small script using today's concepts

## My Reflections
It's rewarding to see {self.mentee_name}'s growth. Teaching helps me understand concepts more deeply too.
{"This mentoring journey reminds me why I got into tech." if week > 3 else "Learning to explain things simply is a skill."}

## Next Session
- Continue with {random.choice(["next topic", "project work", "debugging practice"])}
- Review homework together
- Hours logged: {2 + random.randint(0, 1)}"""

    def generate_community_reflection(self, date: datetime) -> str:
        """Generate reflection on community service impact."""
        total_hours = self.volunteer_hours + self.mentee_week * 2

        impact_stories = [
            f"Seeing {self.mentee_name} grow from nervous beginner to confident coder",
            "The smile on Alex's face when the code finally worked",
            "Alex mentioned they're considering CS as a career now",
            "Other volunteers commented on Alex's improvement",
            "Alex helped another student debug their code!",
        ]

        personal_growth = [
            "Patience - learning to let Alex figure things out",
            "Communication - explaining complex things simply",
            "Empathy - remembering what it was like to be new",
            "Leadership - taking responsibility for someone's learning",
            "Gratitude - appreciating my own mentors more",
        ]

        return f"""# Community Service Reflection
Date: {date.strftime("%B %d, %Y")}
Organization: {self.volunteer_org}

## Impact Summary
- **Total volunteer hours**: {total_hours}
- **Mentorship weeks**: {self.mentee_week}
- **Mentee**: {self.mentee_name}

## Meaningful Moments
{random.choice(impact_stories)}

## What I'm Learning
Through mentoring, I'm developing:
- {random.choice(personal_growth)}
- {random.choice(personal_growth)}

## Why This Matters
Tech changed my life trajectory. {self.volunteer_org} helps others access the same opportunity.
Giving back feels like paying forward what my mentors gave me.

## Connection to Values
- **Contribution**: Making a real difference in someone's future
- **Growth**: Teaching deepens my own understanding
- **Connection**: Building a relationship across generations

## Gratitude
Thankful for:
- The {self.volunteer_org} program coordinators
- {self.mentee_name}'s dedication and curiosity
- The chance to use my skills for something meaningful

## Looking Forward
{"Excited to help Alex with their portfolio project" if self.mentee_week > 4 else "Can't wait to see what Alex accomplishes next"}"""

    def generate_values_review(self, date: datetime) -> str:
        """Generate quarterly life goals and values review."""
        quarter = (date.month - 1) // 3 + 1

        life_areas = {
            "Career": {
                "goals": ["Get promoted to senior engineer", "Launch side project", "Speak at a conference"],
                "progress": random.choice(["On track", "Making progress", "Need more focus"]),
            },
            "Health": {
                "goals": ["Run sub-23 5K", "Consistent gym habit", "Better sleep schedule"],
                "progress": random.choice(["Strong progress", "Maintaining", "Slight slip - recommitting"]),
            },
            "Relationships": {
                "goals": [f"Deepen relationship with {self.partner_name}", "Stay connected with family", "Nurture friendships"],
                "progress": random.choice(["Feeling connected", "Could be better", "Quality time this quarter"]),
            },
            "Growth": {
                "goals": ["Read 12 books this year", "Learn Kubernetes", "Master public speaking"],
                "progress": random.choice(["Ahead of schedule", "On pace", "Behind - adjusting targets"]),
            },
            "Contribution": {
                "goals": [f"Mentor {self.mentee_name} successfully", "Volunteer 50 hours", "Share knowledge with team"],
                "progress": random.choice(["Most fulfilling area", "Going well", "Making impact"]),
            },
        }

        return f"""# Quarterly Life Review - Q{quarter}
Date: {date.strftime("%B %d, %Y")}

## Core Values Check
Am I living aligned with what matters most?

### My Values
1. **Growth** - Continuously learning and improving
2. **Balance** - Sustainable pace, not burnout
3. **Contribution** - Making a positive difference
4. **Connection** - Meaningful relationships

## Life Areas Review

{chr(10).join(f'''### {area}
**Goals**: {", ".join(goals["goals"][:2])}
**Progress**: {goals["progress"]}
''' for area, goals in life_areas.items())}

## Wins This Quarter
- {random.choice(["Promoted to mid-level engineer", "Side project getting traction", "Hit fitness milestone"])}
- {random.choice(["Strong mentorship relationship", "Better work-life boundaries", "Reconnected with old friends"])}
- {random.choice(["Read 4 books", "Learned new technical skill", "Improved sleep habits"])}

## Areas for Growth
- {random.choice(["More present during family time", "Reduce screen time", "More consistent meditation"])}
- {random.choice(["Say no to more things", "Delegate more at work", "Ask for help sooner"])}

## Intention for Next Quarter
{random.choice([
    "Focus on depth over breadth - fewer goals, more impact",
    "Protect energy by saying no to things that don't align",
    "Invest more in relationships that matter",
    "Create more, consume less",
])}

## Gratitude Reflection
Taking a moment to appreciate:
- Health and energy to pursue my goals
- Supportive relationships ({self.partner_name}, family, friends)
- Meaningful work and growth opportunities
- The ability to give back through mentoring

## One Word for Next Quarter
**{random.choice(["Focus", "Presence", "Courage", "Simplicity", "Intentionality"])}**"""

    def generate_daily_journal(self, date: datetime, day_events: List[str] = None) -> str:
        """Generate daily reflection/journal entry with calendar event correlation."""
        day_of_week = date.strftime("%A")
        day_events = day_events or []

        # Get personal life context
        personal_context = self.get_personal_life_context()

        # Check tomorrow's events
        tomorrow = date + timedelta(days=1)
        tomorrow_events = self.get_events_for_date(tomorrow)

        # Mood based on day and events
        if day_of_week == "Monday":
            mood = random.choice(["Energized for the week", "Bit tired but ready", "Motivated after the weekend"])
        elif day_of_week == "Friday":
            mood = random.choice(["Ready for the weekend", "Accomplished", "Relieved the week is done"])
        else:
            mood = random.choice(["Focused", "Productive", "Steady", "Contemplative"])

        # Build today's highlights from actual events
        highlights = []
        meetings = self.find_meeting_events(day_events)
        one_on_ones = self.find_one_on_one_events(day_events)
        personal = self.find_personal_events(day_events)

        if meetings:
            highlights.append(f"Had {meetings[0]} - good discussion on priorities")
        if one_on_ones:
            highlights.append(f"{one_on_ones[0]} went well - got good career advice")
        if personal:
            highlights.append(f"Took care of {personal[0].lower()} - checked off the list")

        if not highlights:
            highlights = [random.choice([
                f"Good standup - {self.team['tech_lead']} praised my PR",
                "Made progress on the tricky bug",
                "Helpful pair programming session",
                "Knocked out 3 small tasks",
            ])]

        highlights.append(random.choice([
            "Squeezed in a quick workout",
            "Took a proper lunch break",
            "Got outside for a 20 min walk",
            "Cooked a nice dinner",
        ]))

        # Tomorrow's prep based on actual events
        tomorrow_section = ""
        if tomorrow_events:
            tomorrow_prep = []
            for event in tomorrow_events[:3]:
                tomorrow_prep.append(f"- {event}")
            tomorrow_section = f"""## Tomorrow's Calendar
{chr(10).join(tomorrow_prep)}

Need to prepare for these."""

        # Personal life section
        personal_section = ""
        if personal_context:
            personal_section = f"""## Life Update
{personal_context}"""

        return f"""# Daily Journal - {day_of_week}
Date: {date.strftime("%B %d, %Y")}

## Mood
{mood}

## Today's Highlights
{chr(10).join(f"- {h}" for h in highlights)}

## What I Learned
{random.choice([
    "The importance of clear commit messages (got confused by old code)",
    "How to use React DevTools profiler effectively",
    "A new SQL optimization technique - EXISTS vs IN",
    "Sometimes the simplest solution really is best",
    "Taking breaks actually improves productivity",
])}

{tomorrow_section}

{personal_section}

## Gratitude
{random.choice([
    "Grateful for the supportive team at work",
    "Appreciate having time for my hobbies",
    "Lucky to have good health",
    "Thankful for the little things - good coffee, sunny weather",
])}"""

    def generate_daily_planning(self, date: datetime, day_events: List[str] = None) -> str:
        """Generate daily planning notes that reference actual calendar events."""
        day_of_week = date.strftime("%A")
        day_events = day_events or []

        # Get events for today, tomorrow, and this week
        tomorrow = date + timedelta(days=1)
        tomorrow_events = self.get_events_for_date(tomorrow)
        week_events = self.get_upcoming_events(date, days_ahead=5)

        # Build today's schedule from actual events
        today_schedule = []
        for event in day_events[:5]:
            # Add some variety in how we describe events
            if "1:1" in event.lower() or "one on one" in event.lower():
                today_schedule.append(f"- {event} → Prepare talking points")
            elif any(x in event.lower() for x in ["standup", "sync"]):
                today_schedule.append(f"- {event} → Update on PHOE-{1200 + self.sprint_number * 10}")
            elif "review" in event.lower():
                today_schedule.append(f"- {event} → Bring notes")
            else:
                today_schedule.append(f"- {event}")

        if not today_schedule:
            today_schedule = ["- Focus day - no meetings scheduled!", "- Deep work on sprint tasks"]

        # Tomorrow preview
        tomorrow_section = ""
        if tomorrow_events:
            tomorrow_items = [f"- {e}" for e in tomorrow_events[:3]]
            tomorrow_section = f"""## Tomorrow Preview
{chr(10).join(tomorrow_items)}"""

        # Week ahead highlights
        week_section = ""
        important_upcoming = []
        for evt_date, event in week_events:
            if any(kw in event.lower() for kw in ["demo", "review", "planning", "all-hands", "interview", "client"]):
                days_away = (evt_date - date).days
                day_name = evt_date.strftime("%A")
                important_upcoming.append(f"- {day_name}: {event}")
        if important_upcoming:
            week_section = f"""## Key Events This Week
{chr(10).join(important_upcoming[:4])}"""

        # Personal reminders based on storylines
        personal_reminders = []
        if self.apartment_phase == 0:
            personal_reminders.append("- Evening: Check new apartment listings on Zillow")
        elif self.apartment_phase == 2:
            days_to_move = (self.move_in_date - date).days
            if 0 < days_to_move <= 14:
                personal_reminders.append(f"- {days_to_move} days until move - start packing!")
        if self.relationship_phase >= 1 and date.weekday() in [4, 5]:  # Fri/Sat
            personal_reminders.append(f"- Plan something nice with {self.partner_name}")
        if self.guitar_week > 0 and random.random() < 0.5:
            personal_reminders.append("- 30 min guitar practice after work")

        personal_section = ""
        if personal_reminders:
            personal_section = f"""## Personal
{chr(10).join(personal_reminders)}"""

        return f"""# Daily Planning - {day_of_week}
Date: {date.strftime("%B %d, %Y")}

## Today's Calendar
{chr(10).join(today_schedule)}

## Priority Tasks
1. Complete PR review for {self.team['tech_lead']}
2. Continue PHOE-{1200 + self.sprint_number * 10} implementation
3. {random.choice(["Update documentation", "Write unit tests", "Address tech debt item"])}

## Time Blocks
- 9:00-10:00: Deep work on sprint task
- 10:00-12:00: Meetings (see calendar)
- 12:00-13:00: Lunch + walk
- 13:00-16:00: Implementation time
- 16:00-17:00: Code reviews & async

{tomorrow_section}

{week_section}

{personal_section}

## End of Day Check
- [ ] Update JIRA tickets
- [ ] Push code & create PR if ready
- [ ] Respond to Slack messages"""

    def generate_meeting_prep(self, date: datetime, day_events: List[str] = None) -> str:
        """Generate meeting preparation notes for important meetings."""
        day_events = day_events or []

        # Find important meetings to prep for
        important_meetings = []
        for event in day_events:
            if any(kw in event.lower() for kw in ["client", "demo", "interview", "review", "planning", "all-hands"]):
                important_meetings.append(event)

        if not important_meetings:
            # Generate generic prep
            return self.generate_standup_notes(date)

        meeting = important_meetings[0]

        # Generate context-aware prep notes
        if "client" in meeting.lower() or "demo" in meeting.lower():
            return f"""# Meeting Prep: {meeting}
Date: {date.strftime("%B %d, %Y")}

## Agenda
1. Project status update
2. Demo new features
3. Q&A and feedback
4. Next steps

## Key Points to Cover
- Auth migration complete - 40% faster login
- Dashboard redesign in progress (Sprint {self.sprint_number})
- Mobile app timeline question

## Demo Flow
1. Show new login experience
2. Walk through dashboard improvements
3. Preview upcoming features

## Potential Questions
- Timeline for mobile? → Q2 tentative
- API rate limits? → 1000 req/min, can discuss enterprise
- Data export? → Coming in Sprint {self.sprint_number + 1}

## Materials
- [ ] Slides updated
- [ ] Demo environment ready
- [ ] Backup screenshots in case of issues"""

        elif "interview" in meeting.lower():
            return f"""# Interview Prep: {meeting}
Date: {date.strftime("%B %d, %Y")}

## My Questions
1. Technical background and experience
2. System design: "Design a URL shortener"
3. Coding: Implement LRU cache
4. Behavioral: Tell me about a difficult project

## Evaluation Criteria
- Technical depth
- Problem-solving approach
- Communication clarity
- Cultural fit

## Notes Space
[Space for interview notes]

## Follow-up
- [ ] Submit feedback within 24 hours
- [ ] Discuss with {self.team['tech_lead']} if strong candidate"""

        else:
            # Generic meeting prep
            return f"""# Meeting Prep: {meeting}
Date: {date.strftime("%B %d, %Y")}

## My Updates
- Sprint {self.sprint_number} progress: on track
- PR #{self.pr_number + self.current_day} merged
- Blocker: waiting on {self.team['designer']} for mockups

## Discussion Points
1. Timeline for phase 2
2. Resource allocation
3. Tech debt priorities

## Questions to Raise
- Clarity on Q2 roadmap
- Budget for new tooling

## Action Items from Last Time
- [x] Complete API documentation
- [ ] Schedule follow-up with {self.team['devops']}"""

    # =========================================================================
    # Day-of-Week Content Selection
    # =========================================================================

    def get_content_for_day(self, date: datetime, day_events: List[str]) -> List[Tuple[str, str, str]]:
        """
        Get appropriate content for a specific day, correlating with calendar events.

        Returns list of (content, memo_type, domain) tuples.
        """
        day_of_week = date.weekday()  # 0=Monday, 6=Sunday
        content_list = []

        # Check for important events that need prep
        one_on_ones = self.find_one_on_one_events(day_events)
        client_meetings = self.find_client_events(day_events)
        important_meetings = self.find_meeting_events(day_events)

        # MONDAY: Sprint planning (if start of sprint) or daily planning + learning
        if day_of_week == 0:
            if self.current_day % 14 < 2:
                content_list.append((self.generate_sprint_planning(date), "meeting_minutes", "WORK"))
            else:
                # Add daily planning that references today's calendar
                content_list.append((self.generate_daily_planning(date, day_events), "planning", "WORK"))
            content_list.append((self.generate_learning_log(date), "personal_notes", "EDUCATION"))

        # TUESDAY: Code review or meeting prep (if client meeting) + side project
        elif day_of_week == 1:
            if client_meetings:
                content_list.append((self.generate_meeting_prep(date, day_events), "meeting_prep", "WORK"))
            else:
                content_list.append((self.generate_code_review(date), "meeting_minutes", "WORK"))
            if self.current_day % 3 == 0:
                content_list.append((self.generate_side_project_log(date), "personal_notes", "TECHNOLOGY"))

        # WEDNESDAY: 1:1 notes (if 1:1 on calendar) or standup + daily journal + mentorship
        elif day_of_week == 2:
            if one_on_ones:
                # Generate 1:1 notes for actual calendar event
                content_list.append((self.generate_one_on_one(date), "meeting_minutes", "WORK"))
            elif self.current_day % 14 < 7:
                content_list.append((self.generate_one_on_one(date), "meeting_minutes", "WORK"))
            else:
                content_list.append((self.generate_standup_notes(date), "meeting_minutes", "WORK"))
            # Journal with event correlation
            content_list.append((self.generate_daily_journal(date, day_events), "journal", "PERSONAL"))
            # Wednesday evening: Mentorship session (every other week)
            if self.current_day % 14 < 7:
                content_list.append((self.generate_mentorship_session(date), "meeting_minutes", "COMMUNITY"))

        # THURSDAY: Architecture review (monthly) or daily planning + learning
        elif day_of_week == 3:
            if self.current_day % 28 < 7:
                content_list.append((self.generate_architecture_review(date), "meeting_minutes", "WORK"))
            elif important_meetings:
                content_list.append((self.generate_meeting_prep(date, day_events), "meeting_prep", "WORK"))
            else:
                content_list.append((self.generate_daily_planning(date, day_events), "planning", "WORK"))
            content_list.append((self.generate_learning_log(date), "personal_notes", "EDUCATION"))

        # FRIDAY: Sprint review (if end of sprint) or weekly review + fitness
        elif day_of_week == 4:
            if self.current_day % 14 >= 12:
                content_list.append((self.generate_sprint_review(date), "meeting_minutes", "WORK"))
            else:
                content_list.append((self.generate_standup_notes(date), "meeting_minutes", "WORK"))
            content_list.append((self.generate_weekly_review(date), "journal", "PERSONAL"))

        # SATURDAY: Side project + fitness log + values review (alternating weeks)
        elif day_of_week == 5:
            content_list.append((self.generate_side_project_log(date), "personal_notes", "TECHNOLOGY"))
            content_list.append((self.generate_fitness_log(date), "personal_notes", "HEALTH"))
            # Saturday morning: Values review or personal reflection (alternating weeks)
            if self.current_day % 14 >= 7:
                content_list.append((self.generate_values_review(date), "journal", "PERSONAL"))

        # SUNDAY: Weekly review + personal journal + community reflection
        elif day_of_week == 6:
            content_list.append((self.generate_weekly_review(date), "journal", "PERSONAL"))
            content_list.append((self.generate_daily_journal(date, day_events), "journal", "PERSONAL"))
            # Include community reflection on Sundays (monthly)
            if self.current_day % 28 < 7:
                content_list.append((self.generate_community_reflection(date), "journal", "COMMUNITY"))

        return content_list


# ============================================================================
# Teacher Life Simulator - Intelligent Content Generation
# ============================================================================
#
# This simulator creates realistic, temporally-coherent content for a high school
# teacher who is married with children, serves in church leadership, writes books,
# and speaks at educational conferences.
# ============================================================================


class TeacherSimulator:
    """
    Simulates 60 days in the life of a high school English teacher.

    Tracks state across days to generate coherent, realistic content including:
    - Teaching: semester progress, lesson planning, grading, student stories
    - Family: spouse David, daughter Lily (13), son Nathan (9)
    - Church: Deacon, leads adult Sunday school, worship committee
    - Book writing: Working on "Teaching with Purpose" - education methodology
    - Speaking: Educational conferences, teacher workshops
    - Community: Literacy tutoring, neighborhood association
    """

    def __init__(self, start_date: datetime):
        self.start_date = start_date
        self.current_day = 0

        # =====================================================================
        # Teaching State (Spring Semester - weeks 1-16)
        # =====================================================================
        self.semester_week = 3  # Start in week 3 of spring semester
        self.current_unit = "American Literature: The Harlem Renaissance"
        self.units = [
            "American Literature: The Harlem Renaissance",
            "Poetry Analysis & Writing",
            "Research Paper Workshop",
            "Modern Drama: Death of a Salesman",
            "Contemporary Voices",
        ]
        self.unit_index = 0
        self.papers_to_grade = 0
        self.essays_graded = 0
        self.total_students = 127  # 5 classes of ~25

        # Student stories (memorable moments that evolve)
        self.student_highlights = [
            (5, "Marcus finally participated in discussion today - breakthrough!"),
            (12, "Sarah's essay on Langston Hughes was exceptional - recommending for contest"),
            (20, "Had to call home about Tyler's missing assignments - tough conversation"),
            (28, "Marcus submitted his first on-time assignment! Growth!"),
            (35, "Poetry slam was a hit - even the quiet kids performed"),
            (45, "Sarah won the district writing contest! So proud"),
            (52, "End of year reflections - Tyler turned it around, B+ final grade"),
        ]

        # Colleagues
        self.colleagues = {
            "dept_chair": "Dr. Patterson",
            "mentor": "Mrs. Williams",
            "team_teacher": "Mr. Rodriguez",
            "principal": "Dr. Chen",
            "counselor": "Ms. Thompson",
        }

        # =====================================================================
        # Family State
        # =====================================================================
        self.spouse = "David"  # Works as an accountant
        self.kids = [
            {"name": "Lily", "age": 13, "grade": "8th", "activities": ["volleyball", "drama club"]},
            {"name": "Nathan", "age": 9, "grade": "4th", "activities": ["soccer", "cub scouts"]},
        ]

        # Family events (day, event)
        self.family_events = [
            (3, "Lily's volleyball game - they won! She had 5 assists"),
            (8, "Nathan's Cub Scout meeting - working on citizenship badge"),
            (15, "Date night with David - finally tried that new Thai place"),
            (22, "Lily's drama club rehearsal for spring play"),
            (28, "Nathan's soccer tournament - made it to semifinals!"),
            (35, "Family movie night - kids picked a classic"),
            (42, "Lily's 8th grade dance - she looked so grown up"),
            (48, "Nathan's scout camping trip this weekend"),
            (55, "Spring break planning - visiting David's parents"),
        ]

        # =====================================================================
        # Church Leadership State
        # =====================================================================
        self.church_role = "Deacon"
        self.ministry = "Adult Sunday School"
        self.church_name = "Grace Community Church"

        # Lesson series progress
        self.sunday_school_series = "Book of James"
        self.sunday_school_week = 1
        self.sunday_school_topics = [
            "James 1: Trials and Temptations",
            "James 1: Hearing and Doing",
            "James 2: Faith and Works",
            "James 3: Taming the Tongue",
            "James 4: Humility Before God",
            "James 5: Prayer and Patience",
        ]

        # Church events
        self.church_events = [
            (7, "Deacon meeting - discussed benevolence fund requests"),
            (14, "Worship committee - planning Easter services"),
            (21, "Sunday school attendance up 20% - encouraging!"),
            (30, "Easter Sunday - beautiful service, full house"),
            (38, "New member class - welcomed 6 new families"),
            (45, "Deacon retreat - great fellowship and planning"),
            (53, "VBS planning kickoff meeting"),
        ]

        # =====================================================================
        # Book Writing State - "Teaching with Purpose"
        # =====================================================================
        self.book_title = "Teaching with Purpose: Engaging the Reluctant Learner"
        self.book_phase = 0  # 0=drafting, 1=revising, 2=editor review, 3=final edits
        self.book_phases = ["First Draft", "Revisions", "Editor Review", "Final Edits", "With Publisher"]
        self.chapters_drafted = 6
        self.total_chapters = 12
        self.word_count = 42000
        self.publisher = "Educator's Press"

        # Book milestones
        self.book_events = [
            (10, "Finished Chapter 7 draft - differentiated instruction strategies"),
            (20, "Editor call - positive feedback, minor structural suggestions"),
            (32, "Completed first draft! 52,000 words. Time for revisions"),
            (45, "Submitted revisions to editor - nerve-wracking wait"),
            (55, "Editor approved! Moving to final copyedits"),
        ]

        # =====================================================================
        # Speaking & Conference State
        # =====================================================================
        self.speaking_events = [
            {
                "name": "State English Teachers Conference",
                "date_offset": 25,
                "topic": "Reaching Reluctant Readers",
                "phase": 0,  # 0=preparing, 1=delivered, 2=follow-up
            },
            {
                "name": "District Professional Development",
                "date_offset": 40,
                "topic": "Writing Across the Curriculum",
                "phase": 0,
            },
        ]
        self.workshop_attendees = 0
        self.speaking_invitations = 0

        # =====================================================================
        # Community Service State
        # =====================================================================
        self.tutoring_students = 3  # Literacy tutoring at community center
        self.tutoring_hours = 0
        self.neighborhood_role = "HOA Secretary"

        self.community_events = [
            (6, "Tutoring session - Maria is making great progress with reading"),
            (18, "HOA meeting - discussed spring landscaping"),
            (30, "Community cleanup day - great turnout"),
            (42, "Tutoring milestone - James passed his GED reading section!"),
            (50, "Neighborhood block party planning"),
        ]

        # =====================================================================
        # Personal Wellness
        # =====================================================================
        self.exercise_type = "morning walks"
        self.exercise_streak = 0
        self.books_reading = ["Educated by Tara Westover", "Teaching to Transgress by bell hooks"]
        self.book_club_meeting_day = 15  # Which day of month

        # =====================================================================
        # Calendar Event Correlation
        # =====================================================================
        self.events_by_day = {}

    def advance_day(self):
        """Move simulation forward one day."""
        self.current_day += 1

        # Advance semester week every 7 days
        if self.current_day % 7 == 0:
            self.semester_week += 1
            self.sunday_school_week = min(6, self.sunday_school_week + 1)

        # Advance teaching unit every ~12 days
        if self.current_day % 12 == 0 and self.unit_index < len(self.units) - 1:
            self.unit_index += 1
            self.current_unit = self.units[self.unit_index]

        # Accumulate papers to grade (simulates assignment collection)
        if self.current_day % 5 == 0:
            self.papers_to_grade += random.randint(25, 40)

        # Grade some papers
        if self.papers_to_grade > 0 and random.random() < 0.6:
            graded = min(self.papers_to_grade, random.randint(10, 25))
            self.papers_to_grade -= graded
            self.essays_graded += graded

        # Book progress
        if random.random() < 0.3:
            self.word_count += random.randint(200, 800)
        if self.current_day == 32:
            self.book_phase = 1  # Start revisions
            self.chapters_drafted = 12
        elif self.current_day == 45:
            self.book_phase = 2  # Editor review
        elif self.current_day == 55:
            self.book_phase = 3  # Final edits

        # Tutoring hours
        if self.current_day % 7 in [2, 4]:  # Tuesday, Thursday tutoring
            self.tutoring_hours += 2

        # Exercise streak
        if random.random() < 0.7:
            self.exercise_streak += 1
        else:
            self.exercise_streak = 0

        # Speaking event phases
        for event in self.speaking_events:
            if self.current_day == event["date_offset"]:
                event["phase"] = 1  # Delivered
            elif self.current_day == event["date_offset"] + 3:
                event["phase"] = 2  # Follow-up

    def set_events_context(self, events_by_day: dict):
        """Set the calendar events context for event-aware content generation."""
        self.events_by_day = events_by_day

    def get_events_for_date(self, date: datetime) -> List[str]:
        """Get calendar events for a specific date."""
        day_id = date.strftime("%Y-%m-%d")
        return self.events_by_day.get(day_id, [])

    def get_upcoming_events(self, date: datetime, days_ahead: int = 7) -> List[Tuple[datetime, str]]:
        """Get events for the next N days."""
        upcoming = []
        for i in range(1, days_ahead + 1):
            future_date = date + timedelta(days=i)
            for event in self.get_events_for_date(future_date):
                upcoming.append((future_date, event))
        return upcoming

    def get_life_context(self) -> str:
        """Get current life storyline snippets for journal entries."""
        context_parts = []

        # Teaching context
        context_parts.append(f"Week {self.semester_week} of spring semester. Currently teaching: {self.current_unit}")
        if self.papers_to_grade > 20:
            context_parts.append(f"Grading pile: {self.papers_to_grade} papers waiting. Need to catch up this weekend.")

        # Student highlight if applicable
        for day, highlight in self.student_highlights:
            if day == self.current_day:
                context_parts.append(f"Student moment: {highlight}")
                break

        # Family context
        for day, event in self.family_events:
            if abs(day - self.current_day) <= 2:
                context_parts.append(f"Family: {event}")
                break

        # Church context
        for day, event in self.church_events:
            if abs(day - self.current_day) <= 2:
                context_parts.append(f"Church: {event}")
                break

        # Book progress
        phase = self.book_phases[self.book_phase]
        context_parts.append(f"Book status: {phase} - {self.word_count:,} words")

        for day, event in self.book_events:
            if day == self.current_day:
                context_parts.append(f"Writing: {event}")
                break

        # Community
        for day, event in self.community_events:
            if abs(day - self.current_day) <= 1:
                context_parts.append(f"Community: {event}")
                break

        return "\n".join(context_parts)

    # =========================================================================
    # Teaching Content Generators
    # =========================================================================

    def generate_lesson_planning(self, date: datetime) -> str:
        """Generate lesson planning notes."""
        day_of_week = date.strftime("%A")

        # Upcoming focus based on unit
        if "Harlem Renaissance" in self.current_unit:
            texts = ["'The Negro Speaks of Rivers' - Hughes", "'If We Must Die' - McKay", "Their Eyes Were Watching God excerpts"]
            activities = ["Close reading stations", "Historical context jigsaw", "Poetry analysis workshop"]
        elif "Poetry" in self.current_unit:
            texts = ["Student-selected poems", "Mentor texts for writing", "Slam poetry examples"]
            activities = ["Poetry workshop", "Peer revision circles", "Open mic preparation"]
        elif "Research" in self.current_unit:
            texts = ["Sample research papers", "Citation guides", "Source evaluation examples"]
            activities = ["Library research day", "Thesis workshop", "Peer review protocol"]
        else:
            texts = ["Unit anchor text", "Supplementary readings", "Student choice texts"]
            activities = ["Socratic seminar", "Writing workshop", "Literature circles"]

        return f"""# Lesson Planning - {day_of_week}
Date: {date.strftime("%B %d, %Y")}

## Week {self.semester_week} | Unit: {self.current_unit}

### This Week's Objectives
- Students will analyze thematic elements in historical context
- Students will develop close reading skills with poetry
- Students will connect literature to contemporary issues

### Daily Plans

**Monday - Period 1, 3, 5**
- Bell ringer: Quote analysis (10 min)
- Mini-lesson: Historical context (15 min)
- Small group work: {activities[0]} (25 min)
- Exit ticket (5 min)

**Tuesday - Period 2, 4, 6**
- Continue {activities[0]}
- Whole class discussion

**Wednesday - All Classes**
- {activities[1]}
- Check-in: essay progress

**Thursday**
- {activities[2]}
- Formative assessment

**Friday**
- Seminar discussion: {texts[0]}
- Week reflection

### Materials Needed
- [ ] Copies of {texts[0]}
- [ ] Graphic organizers for {activities[0]}
- [ ] Rubric for upcoming essay

### Differentiation Notes
- Marcus: Extended time, check for understanding
- Sarah: Challenge extension - analyze secondary sources
- IEP students: Modified graphic organizer, audio support

### Assessment
- Formative: Exit tickets daily
- Summative: Literary analysis essay due {(date + timedelta(days=10)).strftime("%B %d")}

### Reflection Space
[To be completed after lessons]"""

    def generate_grading_session(self, date: datetime) -> str:
        """Generate grading session notes."""
        graded_count = min(self.papers_to_grade, random.randint(15, 30))

        return f"""# Grading Session
Date: {date.strftime("%B %d, %Y")}

## Today's Progress
- **Papers graded**: {graded_count}
- **Remaining**: {max(0, self.papers_to_grade - graded_count)}
- **Assignment**: Literary Analysis Essay - {self.current_unit}

## Grade Distribution (This Batch)
- A (90-100): {random.randint(2, 5)} papers
- B (80-89): {random.randint(5, 10)} papers
- C (70-79): {random.randint(4, 8)} papers
- D/F (Below 70): {random.randint(1, 4)} papers

## Common Strengths
- Strong thesis statements
- Good use of textual evidence
- Improved paragraph organization

## Areas for Whole-Class Review
- Citation formatting - many still struggling with MLA
- Integrating quotes smoothly into sentences
- Moving beyond summary to analysis

## Individual Notes
- **Sarah M.**: Exceptional analysis of symbolism. Contest-worthy.
- **Marcus J.**: Submitted on time! Thesis needs work but effort is there.
- **Tyler R.**: Missing again. Need to contact home.
- **Emma L.**: Beautiful writing but didn't address the prompt fully.

## Next Steps
- [ ] Return papers by {(date + timedelta(days=2)).strftime("%A")}
- [ ] Plan mini-lesson on quote integration
- [ ] Schedule conference with Tyler
- [ ] Recommend Sarah for writing center tutor position

## Self-Care Note
{random.choice([
    "Taking a break after this batch. Cup of tea and 10 minutes outside.",
    "Eyes tired. Remember to look away from papers every 20 minutes.",
    "This is why I teach. Sarah's essay made me tear up.",
    "Grading is the hardest part, but feedback matters.",
])}"""

    def generate_faculty_meeting_notes(self, date: datetime) -> str:
        """Generate faculty/department meeting notes."""
        return f"""# Faculty Meeting Notes
Date: {date.strftime("%B %d, %Y")}

## Attendees
- {self.colleagues['principal']} (Principal)
- {self.colleagues['dept_chair']} (Dept. Chair)
- English Department faculty

## Agenda

### 1. State Assessment Update
- Testing window: {(date + timedelta(days=30)).strftime("%B %d")} - {(date + timedelta(days=35)).strftime("%B %d")}
- Focus areas: Reading comprehension, writing conventions
- Need to integrate more practice questions into instruction

### 2. Curriculum Alignment
- New standards implementation going well
- {self.colleagues['dept_chair']}: Share successful strategies at next meeting
- Common assessments showing improvement in analysis skills

### 3. Student Support Discussion
- Intervention program showing results
- 15 students moved out of reading support this semester
- Challenge: reaching chronically absent students

### 4. Professional Development
- State conference attendees share out next month
- Book study starting: "Culturally Responsive Teaching"
- District PD day: {(date + timedelta(days=20)).strftime("%B %d")}

### 5. Spring Events
- Poetry slam: {(date + timedelta(days=14)).strftime("%B %d")}
- AP exam prep sessions starting
- Senior project presentations

## My Action Items
- [ ] Submit conference session proposal
- [ ] Share Harlem Renaissance unit resources with team
- [ ] Coordinate with {self.colleagues['counselor']} about Tyler

## Notes to Self
- Ask {self.colleagues['mentor']} about differentiation strategies
- Offer to lead next book study discussion
- Remember to submit textbook order"""

    # =========================================================================
    # Church Leadership Content Generators
    # =========================================================================

    def generate_sunday_school_prep(self, date: datetime) -> str:
        """Generate Sunday School lesson preparation notes."""
        topic_idx = min(self.sunday_school_week - 1, len(self.sunday_school_topics) - 1)
        topic = self.sunday_school_topics[topic_idx]

        return f"""# Sunday School Preparation
Date: {date.strftime("%B %d, %Y")}
Series: {self.sunday_school_series} | Week {self.sunday_school_week}

## This Week's Lesson: {topic}

### Key Scripture
{self._get_james_passage(topic_idx)}

### Main Points
1. {self._get_james_point(topic_idx, 0)}
2. {self._get_james_point(topic_idx, 1)}
3. {self._get_james_point(topic_idx, 2)}

### Discussion Questions
- How does this passage challenge our daily choices?
- What practical steps can we take this week?
- How have you seen this truth in your own life?

### Application
- Personal reflection prompt for the week
- Accountability partner discussion
- Service opportunity connected to lesson

### Teaching Notes
- Open with real-world scenario
- Use small group discussion (8-10 min)
- Close with prayer requests and commitment

### Materials
- [ ] Print discussion guides (25 copies)
- [ ] Prepare slideshow
- [ ] Coordinate with worship team on closing song

### Prayer
Lord, help me teach with wisdom and humility. May Your word transform hearts, starting with my own. Help me be a vessel for Your truth."""

    def _get_james_passage(self, idx: int) -> str:
        passages = [
            "James 1:2-4 - Consider it pure joy, my brothers and sisters, whenever you face trials...",
            "James 1:22-25 - Do not merely listen to the word, and so deceive yourselves. Do what it says.",
            "James 2:14-17 - What good is it if someone claims to have faith but has no deeds?",
            "James 3:5-6 - The tongue is a small part of the body, but it makes great boasts.",
            "James 4:6-7 - God opposes the proud but shows favor to the humble.",
            "James 5:13-16 - Is anyone among you in trouble? Let them pray.",
        ]
        return passages[min(idx, len(passages) - 1)]

    def _get_james_point(self, topic_idx: int, point_idx: int) -> str:
        points = [
            ["Trials produce perseverance", "Perseverance leads to maturity", "God gives wisdom generously"],
            ["Hearing without doing is self-deception", "The word is a mirror for our souls", "Blessing comes through obedience"],
            ["Faith without works is dead", "Works demonstrate genuine faith", "Mercy triumphs over judgment"],
            ["Words have immense power", "The tongue can build up or destroy", "Consistency reveals character"],
            ["Pride leads to conflict", "Humility invites God's grace", "Submission brings freedom"],
            ["Prayer is for all circumstances", "Community prayer brings healing", "Confession leads to restoration"],
        ]
        return points[min(topic_idx, len(points) - 1)][min(point_idx, 2)]

    def generate_deacon_meeting_notes(self, date: datetime) -> str:
        """Generate deacon meeting notes."""
        return f"""# Deacon Meeting Notes - {self.church_name}
Date: {date.strftime("%B %d, %Y")}

## Opening Prayer
- Led by Deacon Thompson

## Benevolence Requests
1. **Johnson Family**: Job loss, requesting help with utilities ($350)
   - Decision: Approved, will also connect with job ministry
2. **Mrs. Williams**: Medical expenses, fixed income
   - Decision: Approved $200, coordinate meals ministry
3. **Anonymous request**: Rent assistance
   - Decision: Need more information, assigned to follow up

## Budget Review
- Benevolence fund balance: $4,200
- Monthly giving: on track
- Building fund: discussing roof repairs

## Ministry Updates
- **Visitation**: 12 visits this month, 3 homebound members
- **New Members**: 6 families completed new member class
- **Communion**: Need additional servers for Easter

## Upcoming
- Easter services: {(date + timedelta(days=21)).strftime("%B %d")} (sunrise, 9am, 11am)
- Church workday: {(date + timedelta(days=14)).strftime("%B %d")}
- VBS planning begins next month

## My Action Items
- [ ] Visit Mrs. Patterson this week
- [ ] Coordinate with hospitality for Easter breakfast
- [ ] Follow up on anonymous benevolence request
- [ ] Recruit 2 more communion servers

## Prayer Requests
- Pastor's family - traveling mercies
- Johnson family - job search
- Building committee decisions
- Easter outreach effectiveness

## Personal Reflection
Grateful for the opportunity to serve. Heavy responsibility but such a privilege to walk alongside people in their needs. Lord, give me wisdom and compassion."""

    # =========================================================================
    # Book Writing Content Generators
    # =========================================================================

    def generate_writing_session(self, date: datetime) -> str:
        """Generate book writing progress notes."""
        phase = self.book_phases[self.book_phase]

        if self.book_phase == 0:  # Drafting
            chapter = min(self.chapters_drafted + 1, 12)
            return f"""# Writing Session - {self.book_title}
Date: {date.strftime("%B %d, %Y")}

## Current Status
- **Phase**: {phase}
- **Chapter**: {chapter} of {self.total_chapters}
- **Word Count**: {self.word_count:,}
- **Today's Goal**: 1,000 words

## Chapter {chapter}: {self._get_chapter_title(chapter)}

### Today's Progress
- Drafted section on {random.choice(["classroom management strategies", "building student relationships", "differentiated instruction", "assessment techniques"])}
- Word count added: {random.randint(500, 1200)}

### Key Ideas Captured
- The importance of knowing each student's story
- Practical strategies for reluctant learners
- Real classroom examples (anonymized)

### Research Notes
- Need to cite study on growth mindset
- Add reference to Dweck's work
- Include my own classroom data

### Tomorrow's Plan
- Continue Chapter {chapter}
- Revise opening hook
- Add student vignette about Marcus (name changed)

### Writing Reflection
{random.choice([
    "Words flowed today. This chapter feels authentic.",
    "Struggled to find the right voice. Morning coffee helped.",
    "Teaching all day then writing is exhausting but fulfilling.",
    "David took the kids so I could write. Grateful for his support.",
])}"""

        else:  # Revising/Editing
            return f"""# Revision Session - {self.book_title}
Date: {date.strftime("%B %d, %Y")}

## Current Status
- **Phase**: {phase}
- **Total Word Count**: {self.word_count:,}
- **Editor**: Working with {self.publisher}

## Today's Revisions
- Chapters reviewed: {random.randint(1, 3)}
- Focus: {random.choice(["Tightening prose", "Adding concrete examples", "Clarifying methodology", "Strengthening transitions"])}

## Editor Feedback Addressed
- [ ] Add more diverse student examples
- [ ] Clarify assessment section
- [x] Expand chapter on building relationships
- [ ] Include discussion questions per chapter

## Revision Notes
{random.choice([
    "Cut 500 words from Chapter 3 - much tighter now",
    "Added the story about the reluctant reader who became a writer",
    "Restructured Chapter 7 based on editor's suggestion",
    "The book is really coming together",
])}

## Next Steps
- Complete Chapter {random.randint(5, 10)} revisions
- Send updated draft to editor by {(date + timedelta(days=7)).strftime("%B %d")}
- Schedule phone call with publisher

## Motivation
*Why I'm writing this book: Every teacher deserves practical strategies that work. Every student deserves a teacher who won't give up.*"""

    def _get_chapter_title(self, chapter: int) -> str:
        titles = [
            "Introduction: The Reluctant Learner",
            "Understanding the Why Behind Resistance",
            "Building Genuine Relationships",
            "Creating a Safe Learning Environment",
            "Differentiation That Works",
            "Engaging Hook Strategies",
            "Assessment as Learning Tool",
            "Parent Partnership",
            "Self-Care for Educators",
            "When Nothing Seems to Work",
            "Celebrating Growth",
            "Your Teaching Legacy",
        ]
        return titles[min(chapter - 1, len(titles) - 1)]

    # =========================================================================
    # Conference/Speaking Content Generators
    # =========================================================================

    def generate_conference_prep(self, date: datetime, event: dict) -> str:
        """Generate conference presentation preparation notes."""
        return f"""# Conference Preparation
Date: {date.strftime("%B %d, %Y")}

## Event: {event['name']}
**Presentation Date**: {(self.start_date + timedelta(days=event['date_offset'])).strftime("%B %d, %Y")}
**Topic**: {event['topic']}
**Time Slot**: 90-minute workshop

## Session Outline

### Opening (10 min)
- Hook: "Think of your most reluctant learner..."
- My story with Marcus
- Session objectives

### Core Content (60 min)
1. **Understanding Reluctance** (15 min)
   - Research overview
   - Common misconceptions
   - Activity: Reluctance inventory

2. **Practical Strategies** (25 min)
   - Relationship-first approach
   - Choice and autonomy
   - Small wins strategy
   - Hands-on: Design a lesson hook

3. **Sustaining Engagement** (20 min)
   - Long-term thinking
   - Parent communication
   - Self-care for teachers

### Q&A and Sharing (15 min)
- Table discussions
- Whole group share
- Resource distribution

### Closing (5 min)
- One thing to try Monday
- Contact information
- Book preview (if appropriate)

## Materials Checklist
- [ ] Slideshow (50 slides max)
- [ ] Handouts (100 copies)
- [ ] Strategy cards
- [ ] Sign-up sheet for resources
- [ ] Business cards

## Tech Needs
- Projector
- Microphone (room capacity 80)
- Timer

## Nervous? Remember:
*You have 15 years of experience. You've reached hundreds of reluctant learners. Share what you know. They need to hear it.*"""

    # =========================================================================
    # Family & Personal Content Generators
    # =========================================================================

    def generate_family_journal(self, date: datetime, day_events: List[str] = None) -> str:
        """Generate family-focused journal entry."""
        day_events = day_events or []
        day_of_week = date.strftime("%A")

        # Get family context
        family_note = ""
        for day, event in self.family_events:
            if abs(day - self.current_day) <= 1:
                family_note = event
                break

        # Tomorrow's events
        tomorrow = date + timedelta(days=1)
        tomorrow_events = self.get_events_for_date(tomorrow)

        return f"""# Family Journal - {day_of_week}
Date: {date.strftime("%B %d, %Y")}

## Today's Moments

**{self.spouse}**: {random.choice([
    "Working late on quarter-end reports. Brought him coffee.",
    "Made dinner tonight - his famous pasta. Kids loved it.",
    "We talked after the kids went to bed. Grateful for him.",
    "He handled homework duty so I could grade. Teamwork.",
])}

**{self.kids[0]['name']} ({self.kids[0]['age']})**: {random.choice([
    f"Volleyball practice. She's really improving her serve.",
    f"Drama club meeting - so excited about the spring play.",
    f"Teenage attitude today, but we talked it through.",
    f"Helped her with algebra. She actually thanked me!",
])}

**{self.kids[1]['name']} ({self.kids[1]['age']})**: {random.choice([
    f"Soccer game this week - he's been practicing in the backyard.",
    f"Cub Scout badge work - citizenship in the community.",
    f"Read together before bed. He chose Harry Potter again.",
    f"Lost a tooth! Tooth fairy duty tonight.",
])}

{f"## Family Note{chr(10)}{family_note}" if family_note else ""}

## Gratitude
{random.choice([
    "Grateful for a home filled with noise and love.",
    f"Thankful that {self.spouse} is such a present father.",
    "These kids are growing so fast. Cherishing the moments.",
    "Family dinner together - no phones. Small victory.",
])}

## This Week's Family Goals
- [ ] {self.kids[0]['name']}'s {random.choice(self.kids[0]['activities'])} support
- [ ] {self.kids[1]['name']}'s {random.choice(self.kids[1]['activities'])} commitment
- [ ] Date night with {self.spouse}
- [ ] Family devotion time

{f"## Tomorrow{chr(10)}" + chr(10).join(f"- {e}" for e in tomorrow_events[:3]) if tomorrow_events else ""}

## Prayer
Lord, thank You for my family. Help me be present, patient, and loving. Give {self.spouse} strength at work. Watch over {self.kids[0]['name']} and {self.kids[1]['name']}. May our home reflect Your love."""

    def generate_weekly_reflection(self, date: datetime) -> str:
        """Generate weekly reflection journal."""
        return f"""# Weekly Reflection - Week {self.semester_week}
Date: {date.strftime("%B %d, %Y")}

## Teaching Wins
- {random.choice([
    "Poetry slam went better than expected",
    "Reached a struggling student this week",
    "New lesson strategy worked beautifully",
    "Department meeting - felt heard and valued",
])}
- Essays showed real growth in analysis skills
- {self.essays_graded} papers graded this week

## Teaching Challenges
- {self.papers_to_grade} papers still in the grading pile
- {random.choice([
    "One student's chronic absences - need intervention",
    "Balancing test prep with real learning",
    "Finding time for meaningful feedback",
])}

## Family Highlights
{self._get_family_highlight()}

## Church Service
- Sunday School: {self.sunday_school_topics[min(self.sunday_school_week - 1, len(self.sunday_school_topics) - 1)]}
- Attendance: {random.randint(18, 28)} adults
- {random.choice([
    "Great discussion on practical application",
    "Powerful prayer time together",
    "New couple joined the class",
])}

## Writing Progress
- Book: {self.book_phases[self.book_phase]}
- Words this week: {random.randint(1500, 4000)}

## Community Service
- Tutoring hours: {self.tutoring_hours}
- {random.choice([
    "Maria is reading at grade level now!",
    "James passed his GED reading section",
    "New tutoring student starting next week",
])}

## Physical/Mental Health
- Morning walks: {random.randint(3, 6)}/7 days
- Sleep: {random.choice(["Adequate", "Need more", "Pretty good"])}
- Energy: {random.choice(["Good", "Tired but okay", "Running on coffee"])}

## Next Week's Priorities
1. Teaching: {random.choice(["Finish unit assessments", "Essay feedback", "Parent conferences"])}
2. Family: {random.choice([f"{self.kids[0]['name']}'s event", f"{self.kids[1]['name']}'s activity", "Date night"])}
3. Church: {random.choice(["Prepare Sunday lesson", "Deacon visit", "Easter planning"])}
4. Writing: {random.choice(["Complete chapter draft", "Editor revisions", "Research section"])}

## Sabbath Rest
*"Come to me, all you who are weary and burdened, and I will give you rest." - Matthew 11:28*

Taking Sunday afternoon to truly rest. No grading. No writing. Just presence."""

    def _get_family_highlight(self) -> str:
        highlights = [
            f"{self.kids[0]['name']}'s volleyball game - so proud of her effort",
            f"{self.kids[1]['name']}'s Cub Scout meeting - working on badges",
            f"Family movie night - {self.spouse} made popcorn",
            f"Date night with {self.spouse} - needed that reconnection",
            f"Kids helped with dinner - chaos but wonderful",
            f"{self.kids[0]['name']} opened up about school stress - good talk",
        ]
        return random.choice(highlights)

    # =========================================================================
    # Day-of-Week Content Selection
    # =========================================================================

    def get_content_for_day(self, date: datetime, day_events: List[str]) -> List[Tuple[str, str, str]]:
        """
        Get appropriate content for a specific day, correlating with calendar events.

        Returns list of (content, memo_type, domain) tuples.
        """
        day_of_week = date.weekday()  # 0=Monday, 6=Sunday
        content_list = []

        # Check for special events
        has_conference = any("conference" in e.lower() for e in day_events)
        has_faculty_meeting = any("faculty" in e.lower() or "department" in e.lower() for e in day_events)
        has_church_meeting = any("deacon" in e.lower() or "church" in e.lower() for e in day_events)

        # MONDAY: Lesson planning + faculty meeting if scheduled
        if day_of_week == 0:
            content_list.append((self.generate_lesson_planning(date), "planning", "EDUCATION"))
            if has_faculty_meeting:
                content_list.append((self.generate_faculty_meeting_notes(date), "meeting_minutes", "WORK"))

        # TUESDAY: Grading session + tutoring reflection
        elif day_of_week == 1:
            content_list.append((self.generate_grading_session(date), "work_notes", "EDUCATION"))

        # WEDNESDAY: Church prep (Sunday School) or writing session
        elif day_of_week == 2:
            if has_church_meeting:
                content_list.append((self.generate_deacon_meeting_notes(date), "meeting_minutes", "FAITH"))
            else:
                content_list.append((self.generate_writing_session(date), "personal_notes", "CREATIVE"))
            content_list.append((self.generate_family_journal(date, day_events), "journal", "PERSONAL"))

        # THURSDAY: Conference prep or grading + tutoring
        elif day_of_week == 3:
            # Check for upcoming conference
            for event in self.speaking_events:
                days_until = event["date_offset"] - self.current_day
                if 0 < days_until <= 10 and event["phase"] == 0:
                    content_list.append((self.generate_conference_prep(date, event), "planning", "CAREER"))
                    break
            else:
                content_list.append((self.generate_grading_session(date), "work_notes", "EDUCATION"))

        # FRIDAY: Weekly teaching reflection + family plans
        elif day_of_week == 4:
            content_list.append((self.generate_lesson_planning(date), "planning", "EDUCATION"))
            content_list.append((self.generate_family_journal(date, day_events), "journal", "PERSONAL"))

        # SATURDAY: Family time + church prep
        elif day_of_week == 5:
            content_list.append((self.generate_sunday_school_prep(date), "planning", "FAITH"))
            content_list.append((self.generate_writing_session(date), "personal_notes", "CREATIVE"))

        # SUNDAY: Weekly reflection + family sabbath
        elif day_of_week == 6:
            content_list.append((self.generate_weekly_reflection(date), "journal", "PERSONAL"))
            content_list.append((self.generate_family_journal(date, day_events), "journal", "PERSONAL"))

        return content_list


# Topics for memo content generation
PERSONA_TOPICS = {
    "tech_professional": ["sprint", "code", "api", "deployment", "testing", "kubernetes", "react", "performance", "architecture", "PR", "review"],
    "teacher": ["lesson", "grading", "essay", "student", "curriculum", "assessment", "IEP", "poetry", "literature", "discussion", "rubric", "chapter", "writing", "sermon", "deacon", "Sunday School", "tutoring"],
}

# Person names
PERSONA_PEOPLE = {
    "tech_professional": ["Sarah", "Mike", "Alex", "Jordan", "Taylor", "Chris", "Jamie"],
    "teacher": ["David", "Lily", "Nathan", "Marcus", "Sarah M.", "Tyler", "Dr. Patterson", "Mrs. Williams", "Mr. Rodriguez", "Dr. Chen", "Ms. Thompson"],
}

# ============================================================================
# AI Preferences Configuration (matches AiPreferences.kt enums)
# ============================================================================

# Key names for kv table
AI_PREFERENCES_KEY = "AI_PREFERENCES"
AI_PREFERENCES_PROMPT_STATE_KEY = "AI_PREFERENCES_PROMPT_STATE"

# Enum values (must match Kotlin enum names exactly)
LIFE_SITUATIONS = [
    "STUDENT",
    "WORKING_PROFESSIONAL",
    "FREELANCER",
    "STAY_AT_HOME_PARENT",
    "RETIRED",
    "BETWEEN_JOBS",
    "PART_TIME",
    "ENTREPRENEUR",
]

ENERGY_PATTERNS = [
    "MORNING_PERSON",
    "NIGHT_OWL",
    "STEADY",
    "AFTERNOON_PEAK",
]

LIVING_ENVIRONMENTS = [
    "URBAN",
    "SUBURBAN",
    "RURAL",
    "REMOTE",
]

INTERESTS = [
    # Creative
    "READING", "WRITING", "ART", "MUSIC", "PHOTOGRAPHY", "CRAFTS",
    # Physical
    "FITNESS", "YOGA", "SPORTS", "HIKING", "CYCLING",
    # Intellectual
    "TECHNOLOGY", "SCIENCE", "LANGUAGES", "HISTORY", "PODCASTS",
    # Social
    "COOKING", "GARDENING", "TRAVEL", "VOLUNTEERING", "GAMING",
    # Professional
    "INVESTING", "CAREER_GROWTH", "NETWORKING", "SIDE_PROJECTS",
]

SUGGESTION_COUNTS = ["MINIMAL", "MODERATE", "COMPREHENSIVE"]
SUGGESTION_STYLES = ["PRACTICAL", "BALANCED", "HOLISTIC"]
PLANNING_HORIZONS = ["DAY_BY_DAY", "WEEKLY", "MONTHLY"]
FOCUS_STYLES = ["DEEP_FOCUS", "TASK_SWITCHING", "MIXED"]
SLEEP_SCHEDULES = ["EARLY_BIRD", "NIGHT_OWL", "FLEXIBLE"]
COMMUTE_STYLES = ["REMOTE", "COMMUTE", "HYBRID", "VARIES"]
ROUTINE_PREFERENCES = ["STRUCTURED", "SPONTANEOUS", "BALANCED"]
STRESS_RESPONSES = ["ORGANIZE", "DECOMPRESS", "PUSH_THROUGH"]
PROMPT_PHASES = ["INITIAL", "REMINDED", "PERSISTENT", "DEACTIVATED"]

# Pre-defined user personas for realistic test data
USER_PERSONAS = [
    {
        "name": "Tech Professional",
        "lifeSituation": "WORKING_PROFESSIONAL",
        "energyPattern": "MORNING_PERSON",
        "livingEnvironment": "URBAN",
        "priorities": {"work": 5, "health": 4, "social": 3, "hobby": 2, "family": 3, "education": 3, "finance": 4},
        "lifestyle": ["isDigitalNative", "isActiveLifestyle"],
        "situations": [],
        "interests": ["TECHNOLOGY", "FITNESS", "PODCASTS", "CAREER_GROWTH", "SIDE_PROJECTS"],
        "suggestionCount": "MODERATE",
        "suggestionStyle": "PRACTICAL",
        "dailyRhythm": {
            "planningHorizon": "WEEKLY",
            "focusStyle": "DEEP_FOCUS",
            "sleepSchedule": "EARLY_BIRD",
            "commuteStyle": "HYBRID",
            "routinePreference": "STRUCTURED",
            "stressResponse": "ORGANIZE",
        },
    },
    {
        "name": "Parent & Freelancer",
        "lifeSituation": "FREELANCER",
        "energyPattern": "AFTERNOON_PEAK",
        "livingEnvironment": "SUBURBAN",
        "priorities": {"work": 4, "health": 3, "social": 2, "hobby": 2, "family": 5, "education": 2, "finance": 4},
        "lifestyle": ["isHomebody"],
        "situations": ["hasKids", "hasPets"],
        "interests": ["COOKING", "READING", "GARDENING", "PHOTOGRAPHY"],
        "suggestionCount": "COMPREHENSIVE",
        "suggestionStyle": "HOLISTIC",
        "dailyRhythm": {
            "planningHorizon": "DAY_BY_DAY",
            "focusStyle": "TASK_SWITCHING",
            "sleepSchedule": "FLEXIBLE",
            "commuteStyle": "REMOTE",
            "routinePreference": "BALANCED",
            "stressResponse": "DECOMPRESS",
        },
    },
    {
        "name": "Graduate Student",
        "lifeSituation": "STUDENT",
        "energyPattern": "NIGHT_OWL",
        "livingEnvironment": "URBAN",
        "priorities": {"work": 2, "health": 3, "social": 4, "hobby": 3, "family": 2, "education": 5, "finance": 2},
        "lifestyle": ["isSocialButterfly", "isDigitalNative"],
        "situations": ["livesAlone"],
        "interests": ["READING", "WRITING", "SCIENCE", "LANGUAGES", "GAMING", "TRAVEL"],
        "suggestionCount": "MODERATE",
        "suggestionStyle": "BALANCED",
        "dailyRhythm": {
            "planningHorizon": "WEEKLY",
            "focusStyle": "DEEP_FOCUS",
            "sleepSchedule": "NIGHT_OWL",
            "commuteStyle": "COMMUTE",
            "routinePreference": "SPONTANEOUS",
            "stressResponse": "PUSH_THROUGH",
        },
    },
    {
        "name": "Entrepreneur",
        "lifeSituation": "ENTREPRENEUR",
        "energyPattern": "STEADY",
        "livingEnvironment": "URBAN",
        "priorities": {"work": 5, "health": 4, "social": 3, "hobby": 1, "family": 3, "education": 3, "finance": 5},
        "lifestyle": ["isActiveLifestyle", "isDigitalNative"],
        "situations": [],
        "interests": ["TECHNOLOGY", "INVESTING", "NETWORKING", "CAREER_GROWTH", "FITNESS", "PODCASTS"],
        "suggestionCount": "MINIMAL",
        "suggestionStyle": "PRACTICAL",
        "dailyRhythm": {
            "planningHorizon": "MONTHLY",
            "focusStyle": "MIXED",
            "sleepSchedule": "FLEXIBLE",
            "commuteStyle": "VARIES",
            "routinePreference": "STRUCTURED",
            "stressResponse": "ORGANIZE",
        },
    },
    {
        "name": "Creative Professional",
        "lifeSituation": "FREELANCER",
        "energyPattern": "AFTERNOON_PEAK",
        "livingEnvironment": "URBAN",
        "priorities": {"work": 4, "health": 3, "social": 4, "hobby": 5, "family": 2, "education": 3, "finance": 3},
        "lifestyle": ["isFoodie", "isSocialButterfly", "isOutdoorsLover"],
        "situations": ["hasPets"],
        "interests": ["ART", "MUSIC", "PHOTOGRAPHY", "WRITING", "TRAVEL", "COOKING"],
        "suggestionCount": "COMPREHENSIVE",
        "suggestionStyle": "HOLISTIC",
        "dailyRhythm": {
            "planningHorizon": "DAY_BY_DAY",
            "focusStyle": "TASK_SWITCHING",
            "sleepSchedule": "NIGHT_OWL",
            "commuteStyle": "REMOTE",
            "routinePreference": "SPONTANEOUS",
            "stressResponse": "DECOMPRESS",
        },
    },
]


# ============================================================================
# NanoId Generator (matches NanoIdUtils in Kotlin)
# ============================================================================

NANOID_ALPHABET = "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
NANOID_SIZE = 21


def generate_nanoid() -> str:
    """Generate a NanoId compatible with JNanoId."""
    return "".join(random.choice(NANOID_ALPHABET) for _ in range(NANOID_SIZE))


# ============================================================================
# SQL Helpers
# ============================================================================


def sql_string(value: Optional[str]) -> str:
    """Escape string for SQL, handling None."""
    if value is None:
        return "NULL"
    escaped = value.replace("'", "''")
    return f"'{escaped}'"


def sql_int(value: Optional[int]) -> str:
    """Format integer for SQL, handling None."""
    return str(value) if value is not None else "NULL"


def sql_bool(value: bool) -> str:
    """Format boolean for SQL (Room uses 0/1)."""
    return "1" if value else "0"


# ============================================================================
# Data Generators
# ============================================================================


def generate_events(
    count: int,
    start_date: datetime,
    end_date: datetime,
    life_persona: str = "tech",
) -> List[str]:
    """
    Generate LocalEvent INSERT statements.

    Distributes events across the date range with the following distribution:
    - At least 50% of days have 7-12 events (busy days)
    - Remaining days have 3-6 events (lighter days)
    - No day has fewer than 3 events

    Respects frequency limits for each event type to prevent unrealistic repetition.

    Args:
        life_persona: "tech" for tech professional or "teacher" for teacher persona
    """
    statements = []

    # Select appropriate title set and locations based on persona
    if life_persona == "teacher":
        event_titles_dict = TEACHER_EVENT_TITLES_WITH_LIMITS
        locations_list = TEACHER_LOCATIONS
        event_categories = ["teaching", "church", "family", "writing_speaking", "community", "personal"]
    else:
        event_titles_dict = EVENT_TITLES_WITH_LIMITS
        locations_list = LOCATIONS
        event_categories = ["meeting", "one_on_one", "call", "review", "personal", "community", "reflection"]

    # Initialize frequency tracking for all event titles
    title_usage = {}
    for category, items in event_titles_dict.items():
        for title, max_count in items:
            title_usage[title] = {"used": 0, "max": max_count}

    def pick_title_with_limit(category: str) -> str:
        """Pick a title from category respecting frequency limits."""
        items = event_titles_dict[category]
        # Filter to titles that haven't exceeded their limit
        available = [(title, max_count) for title, max_count in items
                     if title_usage[title]["used"] < title_usage[title]["max"]]

        if not available:
            # This category exhausted - try to find any available title across all categories
            all_available = []
            for cat, cat_items in event_titles_dict.items():
                for title, max_count in cat_items:
                    if title_usage[title]["used"] < title_usage[title]["max"]:
                        remaining = title_usage[title]["max"] - title_usage[title]["used"]
                        all_available.append((title, remaining))

            if all_available:
                # Pick from any available title, weighted by remaining capacity
                weights = [r for _, r in all_available]
                title = random.choices([t for t, _ in all_available], weights=weights, k=1)[0]
            else:
                # Truly exhausted - create a generic title
                title = f"Meeting #{random.randint(1000, 9999)}"
                title_usage[title] = {"used": 0, "max": 1}
        else:
            # Weight selection toward titles with more remaining capacity
            weights = [title_usage[t]["max"] - title_usage[t]["used"] for t, _ in available]
            title = random.choices([t for t, _ in available], weights=weights, k=1)[0]

        title_usage[title]["used"] += 1
        return title

    # Calculate days in range
    days = (end_date - start_date).days + 1
    dates = [start_date + timedelta(days=i) for i in range(days)]

    # Determine day categories: 50%+ busy (7-12), rest lighter (3-6)
    num_busy_days = int(len(dates) * 0.55)  # 55% busy days to ensure >50%
    num_light_days = len(dates) - num_busy_days

    # Shuffle dates and assign categories
    shuffled_dates = dates.copy()
    random.shuffle(shuffled_dates)
    busy_dates = set(shuffled_dates[:num_busy_days])

    # Distribute events across days based on category
    events_per_day = {}

    for date in dates:
        if date in busy_dates:
            # Busy day: 7-12 events
            day_count = random.randint(7, 12)
        else:
            # Lighter day: 3-6 events (weighted toward 3-5)
            # 40% chance of 3, 30% chance of 4, 20% chance of 5, 10% chance of 6
            roll = random.random()
            if roll < 0.40:
                day_count = 3
            elif roll < 0.70:
                day_count = 4
            elif roll < 0.90:
                day_count = 5
            else:
                day_count = 6
        events_per_day[date] = day_count

    # Calculate total from distribution
    total_from_distribution = sum(events_per_day.values())

    # If requested count is higher, add more events to busy days
    if count > total_from_distribution:
        extra = count - total_from_distribution
        busy_date_list = [d for d in dates if d in busy_dates]
        while extra > 0 and busy_date_list:
            date = random.choice(busy_date_list)
            # Don't exceed 15 events per day
            if events_per_day[date] < 15:
                add = min(random.randint(1, 2), extra, 15 - events_per_day[date])
                events_per_day[date] += add
                extra -= add
            else:
                busy_date_list.remove(date)

    # If requested count is lower, reduce events from busy days (but keep minimum 7)
    elif count < total_from_distribution:
        excess = total_from_distribution - count
        busy_date_list = [d for d in dates if d in busy_dates]
        while excess > 0 and busy_date_list:
            date = random.choice(busy_date_list)
            if events_per_day[date] > 7:
                reduce = min(random.randint(1, 2), excess, events_per_day[date] - 7)
                events_per_day[date] -= reduce
                excess -= reduce
            else:
                busy_date_list.remove(date)

    # Generate events for each day
    for date, day_count in events_per_day.items():
        day_id = date.strftime("%Y-%m-%d")

        for i in range(day_count):
            event_id = str(uuid.uuid4())

            # 30% all-day events
            is_all_day = random.random() < 0.30

            if is_all_day:
                title = pick_title_with_limit("all_day")
                # All-day: startTime = midnight UTC, endTime = NULL
                start_time = int(datetime.combine(date, datetime.min.time()).timestamp() * 1000)
                end_time = None
                location = None
            else:
                # Pick event type based on persona categories
                event_type = random.choice(event_categories)
                title = pick_title_with_limit(event_type)

                # Random start time (8am - 6pm)
                hour = random.randint(8, 17)
                minute = random.choice([0, 15, 30, 45])
                start_dt = datetime.combine(date, datetime.min.time().replace(hour=hour, minute=minute))

                # Duration: 30min, 1hr, 2hr, or 4hr
                duration_minutes = random.choice([30, 60, 120, 240])
                end_dt = start_dt + timedelta(minutes=duration_minutes)

                start_time = int(start_dt.timestamp() * 1000)
                end_time = int(end_dt.timestamp() * 1000)

                location = random.choice(locations_list)

            # Random color
            color_hex = random.choice(EVENT_COLORS)

            # Description (50% chance)
            description = f"Notes for {title}" if random.random() < 0.5 else None

            # Timestamps
            now = int(datetime.now().timestamp() * 1000)

            sql = f"""INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    {sql_string(event_id)}, {sql_string(title)}, {sql_string(day_id)}, {sql_string(day_id)},
    {start_time}, {sql_int(end_time)}, {sql_bool(is_all_day)},
    {sql_string(location)}, {sql_string(description)}, {sql_string(color_hex)}, NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, {now}, {now},
    NULL, NULL, 'LOCAL'
);"""
            statements.append(sql)

    return statements


def generate_tasks_and_pages(
    count: int,
    start_date: datetime,
    end_date: datetime,
    life_persona: str = "tech",
) -> Tuple[List[str], int, int]:
    """
    Generate Task and Page INSERT statements with contiguous parent-child groups.

    Template groups (parent + children) are generated as contiguous blocks first,
    then random tasks fill remaining slots. This ensures children are always
    immediately after their parent.

    Respects frequency limits for each task type to prevent unrealistic repetition.

    Args:
        life_persona: "tech" for tech professional or "teacher" for teacher persona

    Returns:
        - List of SQL statements
        - Count of standalone tasks generated
        - Count of subtasks generated
    """
    statements = []

    # Select appropriate task titles and project templates based on persona
    if life_persona == "teacher":
        task_titles_dict = TEACHER_TASK_TITLES_WITH_LIMITS
        project_templates = TEACHER_PROJECT_TEMPLATES
        task_categories = ["teaching", "church", "book_writing", "speaking", "family", "personal", "community"]
    else:
        task_titles_dict = TASK_TITLES_WITH_LIMITS
        project_templates = PROJECT_TEMPLATES
        task_categories = ["work", "personal", "errands", "projects", "community", "reflection"]

    # Initialize frequency tracking for all task titles
    task_title_usage = {}
    for category, items in task_titles_dict.items():
        for title, max_count in items:
            task_title_usage[title] = {"used": 0, "max": max_count}

    def pick_task_title_with_limit(category: str) -> Optional[str]:
        """Pick a task title from category respecting frequency limits."""
        items = task_titles_dict[category]
        # Filter to titles that haven't exceeded their limit
        available = [(title, max_count) for title, max_count in items
                     if task_title_usage[title]["used"] < task_title_usage[title]["max"]]

        if not available:
            # All titles in this category exhausted - return None (will become handwritten)
            return None

        # Weight selection toward titles with more remaining capacity
        weights = [task_title_usage[t]["max"] - task_title_usage[t]["used"] for t, _ in available]
        title = random.choices([t for t, _ in available], weights=weights, k=1)[0]

        task_title_usage[title]["used"] += 1
        return title

    if count < len(project_templates):
        raise ValueError(
            f"Task count {count} is less than required templates ({len(project_templates)})."
        )

    # Calculate days in range
    days = (end_date - start_date).days + 1
    dates = [start_date + timedelta(days=i) for i in range(days)]

    # Select ~60-70% of days to have tasks
    task_days_count = min(len(dates), max(30, int(len(dates) * 0.65)))
    task_days = sorted(random.sample(dates, task_days_count))

    # Categorize days: Heavy 20%, Medium 40%, Light 40%
    num_heavy = int(task_days_count * 0.20)
    num_medium = int(task_days_count * 0.40)

    shuffled_task_days = task_days.copy()
    random.shuffle(shuffled_task_days)
    heavy_days = set(shuffled_task_days[:num_heavy])
    medium_days = set(shuffled_task_days[num_heavy:num_heavy + num_medium])

    # Distribute tasks across days based on category
    tasks_per_day = {}
    for date in task_days:
        if date in heavy_days:
            day_count = random.randint(15, 25)
        elif date in medium_days:
            day_count = random.randint(8, 14)
        else:
            day_count = random.randint(3, 7)
        tasks_per_day[date] = day_count

    # Adjust to match requested count
    total_from_distribution = sum(tasks_per_day.values())
    if count > total_from_distribution:
        extra = count - total_from_distribution
        expandable_days = [d for d in task_days if d in heavy_days or d in medium_days]
        while extra > 0 and expandable_days:
            date = random.choice(expandable_days)
            max_for_day = 30 if date in heavy_days else 20
            if tasks_per_day[date] < max_for_day:
                add = min(random.randint(1, 3), extra, max_for_day - tasks_per_day[date])
                tasks_per_day[date] += add
                extra -= add
            else:
                expandable_days.remove(date)
    elif count < total_from_distribution:
        excess = total_from_distribution - count
        reducible_days = [d for d in task_days if d in heavy_days]
        while excess > 0 and reducible_days:
            date = random.choice(reducible_days)
            min_for_day = 15
            if tasks_per_day[date] > min_for_day:
                reduce = min(random.randint(1, 3), excess, tasks_per_day[date] - min_for_day)
                tasks_per_day[date] -= reduce
                excess -= reduce
            else:
                reducible_days.remove(date)

    # ========================================================================
    # PHASE 1: Assign templates to days and generate contiguous parent+children
    # ========================================================================

    # Prefer heavy/medium days for templates (more room for children)
    template_eligible_days = [d for d in task_days if d in heavy_days or d in medium_days]
    if len(template_eligible_days) < len(project_templates):
        # Add light days if needed
        remaining = [d for d in task_days if d not in template_eligible_days]
        template_eligible_days.extend(remaining[:len(project_templates) - len(template_eligible_days)])

    random.shuffle(template_eligible_days)

    # Assign one template per day (spread them out)
    template_indices = list(range(len(project_templates)))
    random.shuffle(template_indices)

    template_assignments = {}  # day_date -> template_index
    for i, template_idx in enumerate(template_indices):
        if i < len(template_eligible_days):
            day = template_eligible_days[i]
            day_date = day.strftime("%Y-%m-%d")
            template_assignments[day_date] = template_idx

    # Track used slots per day: day_date -> {(page_idx, row_idx)}
    used_slots_by_day = {date.strftime("%Y-%m-%d"): set() for date in task_days}

    # Helper to find next available contiguous block
    def find_contiguous_slots(day_date: str, needed: int, used_slots: set) -> List[Tuple[int, int]]:
        """Find `needed` contiguous slots on a day, returns list of (page_idx, row_idx)."""
        slots = []
        page_idx = 0

        while len(slots) < needed:
            for row in range(10):
                if (page_idx, row) not in used_slots:
                    slots.append((page_idx, row))
                    if len(slots) == needed:
                        return slots
                else:
                    # Non-contiguous, reset and try from next available
                    slots = []
            page_idx += 1
            if page_idx > 10:  # Safety limit
                break

        # Fallback: just find any available slots (may not be fully contiguous)
        if len(slots) < needed:
            slots = []
            page_idx = 0
            while len(slots) < needed:
                for row in range(10):
                    if (page_idx, row) not in used_slots:
                        slots.append((page_idx, row))
                        if len(slots) == needed:
                            return slots
                page_idx += 1
                if page_idx > 10:
                    break

        return slots

    # Helper to create a task
    def create_task_sql(
        task_id: str,
        day_date: str,
        page_idx: int,
        row_idx: int,
        typed_text: Optional[str],
        is_completed: bool,
        priority: str,
        due_date: Optional[int],
        parent_task_id: Optional[str] = None
    ) -> List[str]:
        page_id = str(uuid.uuid4())
        now_ms = int(datetime.now().timestamp() * 1000)

        page_sql = f"""INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    {sql_string(page_id)}, 0, NULL, 'blank', 'native',
    NULL, NULL, {now_ms}, {now_ms}
);"""

        task_sql = f"""INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    {sql_string(task_id)}, {sql_string(day_date)}, {page_idx}, {row_idx}, {sql_string(page_id)},
    {sql_bool(is_completed)}, {sql_string(priority)}, {sql_string(typed_text)}, NULL,
    {sql_string(parent_task_id) if parent_task_id else 'NULL'}, 0, NULL, NULL, NULL,
    {sql_int(due_date)}, {now_ms}, {now_ms}
);"""
        return [page_sql, task_sql]

    reference_date = start_date.replace(hour=0, minute=0, second=0, microsecond=0)

    def generate_due_date() -> Optional[int]:
        """Generate a random due date (30% none, 35% overdue, 35% future)."""
        roll = random.random()
        if roll < 0.30:
            return None
        elif roll < 0.65:
            days_ago = random.randint(1, 60)
            due_date_dt = reference_date - timedelta(days=days_ago)
            return int(due_date_dt.timestamp() * 1000)
        else:
            days_ahead = random.randint(1, 90)
            due_date_dt = reference_date + timedelta(days=days_ahead)
            if due_date_dt > end_date:
                due_date_dt = end_date
            return int(due_date_dt.timestamp() * 1000)

    def generate_priority() -> str:
        """Generate random priority (50% none, 20% A, 20% B, 10% C)."""
        roll = random.random()
        if roll < 0.50:
            return ""
        elif roll < 0.70:
            return "A"
        elif roll < 0.90:
            return "B"
        else:
            return "C"

    subtask_count = 0
    standalone_count = 0

    # Generate template groups (parent + children) first
    for day_date, template_idx in template_assignments.items():
        template = project_templates[template_idx]
        parent_text = template["parent"]
        children = template["children"]

        # Select 4-5 children
        num_children = random.randint(4, min(5, len(children)))
        selected_children = random.sample(children, k=num_children)

        # Total slots needed: 1 parent + num_children
        needed_slots = 1 + num_children

        used_slots = used_slots_by_day[day_date]
        slots = find_contiguous_slots(day_date, needed_slots, used_slots)

        if len(slots) < needed_slots:
            # Not enough room, skip this template
            continue

        # First slot is parent
        parent_slot = slots[0]
        parent_id = generate_nanoid()
        parent_priority = generate_priority()
        parent_due = generate_due_date()

        sql = create_task_sql(
            task_id=parent_id,
            day_date=day_date,
            page_idx=parent_slot[0],
            row_idx=parent_slot[1],
            typed_text=parent_text,
            is_completed=False,
            priority=parent_priority,
            due_date=parent_due,
            parent_task_id=None
        )
        statements.extend(sql)
        used_slots.add(parent_slot)
        standalone_count += 1

        # Remaining slots are children
        for i, child_text in enumerate(selected_children):
            child_slot = slots[1 + i]
            child_id = generate_nanoid()

            sql = create_task_sql(
                task_id=child_id,
                day_date=day_date,
                page_idx=child_slot[0],
                row_idx=child_slot[1],
                typed_text=child_text,
                is_completed=False,
                priority="",  # Children inherit from parent in UI
                due_date=None,  # Children typically don't have separate due dates
                parent_task_id=parent_id
            )
            statements.extend(sql)
            used_slots.add(child_slot)
            subtask_count += 1

    # ========================================================================
    # PHASE 2: Fill remaining slots with random tasks
    # ========================================================================

    for date in task_days:
        day_date = date.strftime("%Y-%m-%d")
        target_count = tasks_per_day[date]
        used_slots = used_slots_by_day[day_date]
        current_count = len(used_slots)

        # Fill up to target count
        page_idx = 0
        row_idx = 0

        while current_count < target_count:
            # Find next available slot (rows 0-9 per page)
            found = False
            while not found:
                # Normalize row_idx first (must be 0-9)
                if row_idx >= 10:
                    row_idx = 0
                    page_idx += 1
                if page_idx > 10:
                    break
                # Check if slot is available
                if (page_idx, row_idx) not in used_slots:
                    found = True
                else:
                    row_idx += 1

            if not found:
                break

            # Generate random task
            task_id = generate_nanoid()

            # 60% typed, 40% handwritten (empty)
            is_typed = random.random() < 0.60
            if is_typed:
                task_type = random.choice(task_categories)
                typed_text = pick_task_title_with_limit(task_type)
                # If all titles exhausted, becomes handwritten
                if typed_text is None:
                    is_typed = False
            else:
                typed_text = None

            # 15% of typed tasks are completed
            is_completed = is_typed and random.random() < 0.15

            priority = generate_priority()
            due_date = generate_due_date()

            sql = create_task_sql(
                task_id=task_id,
                day_date=day_date,
                page_idx=page_idx,
                row_idx=row_idx,
                typed_text=typed_text,
                is_completed=is_completed,
                priority=priority,
                due_date=due_date,
                parent_task_id=None
            )
            statements.extend(sql)
            used_slots.add((page_idx, row_idx))
            current_count += 1
            standalone_count += 1

            row_idx += 1

    return statements, standalone_count, subtask_count


def generate_clear_statements(start_date: str, end_date: str) -> List[str]:
    """Generate DELETE statements to clear existing data in date range."""
    # Note: Comments must be on separate lines to avoid being concatenated
    # with the following statement (which would cause skipping)
    return [
        f"DELETE FROM local_events WHERE dayId BETWEEN '{start_date}' AND '{end_date}';",
        f"DELETE FROM task WHERE dayDate BETWEEN '{start_date}' AND '{end_date}';",
        # Clear memo-related tables
        f"DELETE FROM text_annotations WHERE pageId IN (SELECT id FROM page WHERE id IN (SELECT json_each.value FROM daypage, json_each(pageIds) WHERE date BETWEEN '{start_date}' AND '{end_date}'));",
        f"DELETE FROM unified_ocr_text WHERE sourceType = 'MEMO' AND sourceId BETWEEN '{start_date}' AND '{end_date}';",
        f"DELETE FROM unified_ocr_page WHERE sourceType = 'MEMO' AND sourceId BETWEEN '{start_date}' AND '{end_date}';",
        f"DELETE FROM page WHERE id IN (SELECT json_each.value FROM daypage, json_each(pageIds) WHERE date BETWEEN '{start_date}' AND '{end_date}');",
        f"DELETE FROM daypage WHERE date BETWEEN '{start_date}' AND '{end_date}';",
        "",
    ]


# ============================================================================
# Memo Generation Functions
# ============================================================================


def get_persona_key(persona_index: Optional[int]) -> str:
    """Convert persona index to template key."""
    persona_keys = [
        "tech_professional",
        "parent_freelancer",
        "graduate_student",
        "entrepreneur",
        "creative_professional",
    ]
    if persona_index is not None and 0 <= persona_index < len(persona_keys):
        return persona_keys[persona_index]
    return random.choice(persona_keys)


def generate_memo_content(
    persona_key: str,
    memo_type: str,
    date: datetime,
    event_title: Optional[str] = None,
) -> str:
    """
    Generate memo content based on persona and type.

    Args:
        persona_key: Persona identifier (e.g., 'tech_professional')
        memo_type: 'meeting_minutes', 'personal_notes', 'journal', or 'voice'
        date: Date for the memo
        event_title: Event title for meeting minutes

    Returns:
        Generated memo content string
    """
    date_str = date.strftime("%B %d, %Y")
    week_num = date.isocalendar()[1]

    # Pick random topic and person for placeholder replacement
    topics = PERSONA_TOPICS.get(persona_key, ["productivity", "planning"])
    people = PERSONA_PEOPLE.get(persona_key, ["colleague", "friend"])
    topic = random.choice(topics)
    person = random.choice(people)
    idea = random.choice(["workflow optimization", "new approach", "process improvement"])

    if memo_type == "meeting_minutes":
        templates = MEETING_MINUTES_TEMPLATES.get(persona_key, MEETING_MINUTES_TEMPLATES["tech_professional"])
        template = random.choice(templates)
        content = template.format(
            event_title=event_title or "Team Meeting",
            date=date_str,
            week_num=week_num,
        )
    elif memo_type == "personal_notes":
        templates = PERSONAL_NOTES_TEMPLATES.get(persona_key, PERSONAL_NOTES_TEMPLATES["tech_professional"])
        template = random.choice(templates)
        content = template.format(
            date=date_str,
            week_num=week_num,
            topic=topic,
            person=person,
            idea=idea,
        )
    elif memo_type == "journal":
        template = random.choice(JOURNAL_ENTRIES)
        content = template.format(
            date=date_str,
            week_num=week_num,
            topic=topic,
            person=person,
            idea=idea,
        )
    else:  # voice
        template = random.choice(VOICE_NOTES)
        content = template.format(
            topic=topic,
            person=person,
        )

    return content


def generate_memos(
    start_date: datetime,
    end_date: datetime,
    persona_index: Optional[int] = None,
    events_by_day: Optional[dict] = None,
    life_persona: str = "tech",
) -> Tuple[List[str], int, int]:
    """
    Generate memo pages with text annotations using the TechProfessionalSimulator
    or TeacherSimulator for intelligent, temporally-coherent content.

    Creates for each day:
    - DayPage entry mapping date to page IDs
    - Page entries (2 per day, matching simulator output)
    - TextAnnotation entries with content

    Note: OCR indexing (UnifiedOcrPage/UnifiedOcrText) is NOT seeded here.
    The app's background indexing service will process these pages naturally,
    which provides better test coverage of the actual indexing pipeline.

    Args:
        start_date: Start of date range
        end_date: End of date range
        persona_index: AI persona index (only 0=tech_professional supported)
        events_by_day: Dict of day_id -> list of event titles (for meeting minutes)
        life_persona: "tech" for tech professional or "teacher" for teacher persona

    Returns:
        Tuple of (SQL statements, page count, annotation count)
    """
    statements = []

    # Initialize the appropriate life simulator based on persona
    if life_persona == "teacher":
        simulator = TeacherSimulator(start_date)
        persona_key = "teacher"
    else:
        simulator = TechProfessionalSimulator(start_date)
        persona_key = "tech_professional"

    # Set events context for calendar correlation
    if events_by_day:
        simulator.set_events_context(events_by_day)

    # Calculate days in range
    days = (end_date - start_date).days + 1
    dates = [start_date + timedelta(days=i) for i in range(days)]

    page_count = 0
    annotation_count = 0
    now_ms = int(datetime.now().timestamp() * 1000)

    for date in dates:
        day_id = date.strftime("%Y-%m-%d")
        day_events = events_by_day.get(day_id, []) if events_by_day else []

        # Get content from the simulator (returns list of (content, memo_type, domain) tuples)
        day_content = simulator.get_content_for_day(date, day_events)

        # Advance simulation state for next day
        simulator.advance_day()

        page_ids = []

        for idx, (content, memo_type, domain) in enumerate(day_content):
            page_id = str(uuid.uuid4())
            page_ids.append(page_id)
            page_count += 1

            # Create Page entry
            page_sql = f"""INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    {sql_string(page_id)}, 0, NULL, 'blank', 'native',
    NULL, NULL, {now_ms}, {now_ms}
);"""
            statements.append(page_sql)

            # Create TextAnnotation entry
            annotation_id = str(uuid.uuid4())
            annotation_count += 1

            # Position text in the upper area of the canvas
            x_pos = 50.0 + random.uniform(0, 20)
            y_pos = 80.0 + random.uniform(0, 30)
            width = 1300.0
            height = max(200.0, len(content) / 3)

            source_type = "typed"

            annotation_sql = f"""INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    {sql_string(annotation_id)}, {sql_string(content)}, {x_pos}, {y_pos}, {width}, {height},
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', {sql_string(page_id)}, {sql_string(source_type)}, NULL, 'en', 0.95,
    {now_ms}, {now_ms}
);"""
            statements.append(annotation_sql)

            # Note: UnifiedOcrPage/UnifiedOcrText are NOT seeded here.
            # The app's background indexing service will process these pages,
            # providing better test coverage of the actual indexing pipeline.

        # Create DayPage entry mapping date to all memo pages
        page_ids_json = json.dumps(page_ids)
        open_page_id = page_ids[0] if page_ids else None

        daypage_sql = f"""INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    {sql_string(day_id)}, {sql_string(page_ids_json)}, {sql_string(open_page_id)}, {now_ms}, {now_ms}
);"""
        statements.append(daypage_sql)

    return statements, page_count, annotation_count


# ============================================================================
# AI Preferences Generators
# ============================================================================


def generate_ai_preferences(persona_index: Optional[int] = None) -> dict:
    """
    Generate an AiPreferences object matching the Kotlin data class.

    Args:
        persona_index: Index into USER_PERSONAS list. If None, picks randomly.

    Returns:
        Dictionary representing AiPreferences that can be JSON-serialized.
    """
    # Select persona
    if persona_index is not None and 0 <= persona_index < len(USER_PERSONAS):
        persona = USER_PERSONAS[persona_index]
    else:
        persona = random.choice(USER_PERSONAS)

    # Build preferences from persona
    priorities = persona["priorities"]
    rhythm = persona["dailyRhythm"]

    # All lifestyle flags default to False
    lifestyle_flags = {
        "isActiveLifestyle": False,
        "isWeekendWarrior": False,
        "isHomebody": False,
        "isSocialButterfly": False,
        "isQuietIntrovert": False,
        "isOutdoorsLover": False,
        "isFoodie": False,
        "isDigitalNative": False,
    }
    # Set flags from persona
    for flag in persona.get("lifestyle", []):
        if flag in lifestyle_flags:
            lifestyle_flags[flag] = True

    # Situation flags
    situation_flags = {
        "hasKids": False,
        "hasPets": False,
        "isCareGiver": False,
        "livesAlone": False,
    }
    for flag in persona.get("situations", []):
        if flag in situation_flags:
            situation_flags[flag] = True

    # Current timestamp
    now_ms = int(datetime.now().timestamp() * 1000)

    preferences = {
        # Life Priorities (1-5 scale)
        "workPriority": priorities.get("work", 3),
        "educationPriority": priorities.get("education", 3),
        "familyPriority": priorities.get("family", 3),
        "healthPriority": priorities.get("health", 3),
        "socialPriority": priorities.get("social", 3),
        "hobbyPriority": priorities.get("hobby", 3),
        "financePriority": priorities.get("finance", 3),
        "homePriority": priorities.get("home", 3),
        "growthPriority": priorities.get("growth", 3),
        # About Me
        "lifeSituations": [persona["lifeSituation"]],  # Set serialized as array
        "energyPattern": persona["energyPattern"],
        "livingEnvironment": persona["livingEnvironment"],
        "industry": "NOT_SET",
        "profession": "NOT_SET",
        # Lifestyle Vibes
        **lifestyle_flags,
        # Situation Flags
        **situation_flags,
        # Interests (serialized as array of enum names)
        "interests": persona.get("interests", []),
        # AI Behavior Settings
        "suggestionCount": persona.get("suggestionCount", "MODERATE"),
        "suggestionStyle": persona.get("suggestionStyle", "BALANCED"),
        "includeHealthReminders": True,
        "includeSocialReminders": True,
        "includeDeadlineWarnings": True,
        # Special Modes
        "vacationMode": False,
        "crunchMode": False,
        "holidayMode": False,
        # Daily Rhythm
        "planningHorizon": rhythm.get("planningHorizon", "DAY_BY_DAY"),
        "focusStyle": rhythm.get("focusStyle", "MIXED"),
        "sleepSchedule": rhythm.get("sleepSchedule", "FLEXIBLE"),
        "commuteStyle": rhythm.get("commuteStyle", "HYBRID"),
        "routinePreference": rhythm.get("routinePreference", "BALANCED"),
        "stressResponse": rhythm.get("stressResponse", "ORGANIZE"),
        # Meta
        "setupComplete": True,
        "lastUpdated": now_ms,
    }

    return preferences


def generate_ai_preferences_prompt_state() -> dict:
    """
    Generate an AiPreferencesPromptState that marks setup as completed.

    This deactivates the proactive prompt system since preferences are seeded.

    Returns:
        Dictionary representing AiPreferencesPromptState.
    """
    now_ms = int(datetime.now().timestamp() * 1000)

    return {
        "phase": "DEACTIVATED",
        "totalCloses": 0,
        "closesInCurrentPhase": 0,
        "lastPromptTimestamp": now_ms,
        "isDeactivated": True,
    }


def generate_ai_preferences_sql(persona_index: Optional[int] = None) -> List[str]:
    """
    Generate SQL INSERT statements for AI preferences in kv table.

    Args:
        persona_index: Index into USER_PERSONAS list. If None, picks randomly.

    Returns:
        List of SQL INSERT statements.
    """
    statements = []

    # Generate preferences
    preferences = generate_ai_preferences(persona_index)
    preferences_json = json.dumps(preferences, separators=(",", ":"))

    # Generate prompt state (deactivated since preferences are set)
    prompt_state = generate_ai_preferences_prompt_state()
    prompt_state_json = json.dumps(prompt_state, separators=(",", ":"))

    # Use INSERT OR REPLACE to handle existing entries
    statements.append(
        f"INSERT OR REPLACE INTO kv (`key`, value) VALUES "
        f"({sql_string(AI_PREFERENCES_KEY)}, {sql_string(preferences_json)});"
    )
    statements.append(
        f"INSERT OR REPLACE INTO kv (`key`, value) VALUES "
        f"({sql_string(AI_PREFERENCES_PROMPT_STATE_KEY)}, {sql_string(prompt_state_json)});"
    )

    return statements


def generate_clear_ai_preferences_sql() -> List[str]:
    """Generate DELETE statements to clear existing AI preferences."""
    return [
        f"DELETE FROM kv WHERE `key` = {sql_string(AI_PREFERENCES_KEY)};",
        f"DELETE FROM kv WHERE `key` = {sql_string(AI_PREFERENCES_PROMPT_STATE_KEY)};",
    ]


# ============================================================================
# ADB Execution
# ============================================================================


def check_adb_connection() -> bool:
    """Check if ADB is available and device is connected."""
    try:
        result = subprocess.run(
            ["adb", "devices"],
            capture_output=True,
            text=True,
            timeout=10,
        )
        lines = result.stdout.strip().split("\n")
        # First line is header, check if any device is listed
        return len(lines) > 1 and any("device" in line for line in lines[1:])
    except (subprocess.TimeoutExpired, FileNotFoundError):
        return False


def execute_via_adb(sql_content: str) -> bool:
    """Push SQL file and execute via ADB."""
    # Write to temp file
    temp_file = Path(__file__).parent / "seed_data" / "seed.sql"
    temp_file.parent.mkdir(exist_ok=True)
    temp_file.write_text(sql_content, encoding="utf-8")

    print(f"SQL file written to: {temp_file}")
    print(f"Pushing to device...")

    try:
        # Push file to device
        result = subprocess.run(
            ["adb", "push", str(temp_file), TEMP_SQL_PATH],
            capture_output=True,
            text=True,
            timeout=30,
        )
        if result.returncode != 0:
            print(f"Error pushing file: {result.stderr}")
            return False

        print(f"Executing SQL on device...")

        # Execute SQL using sqlite3
        # Note: Need root or run-as for app's private storage
        cmd = f'run-as com.boox.day sqlite3 {DB_PATH} < {TEMP_SQL_PATH}'
        result = subprocess.run(
            ["adb", "shell", cmd],
            capture_output=True,
            text=True,
            timeout=60,
        )

        if result.returncode != 0:
            print(f"Error executing SQL: {result.stderr}")
            # Try alternative: copy DB, modify, push back
            print("Trying alternative method...")
            return execute_via_adb_alternative(sql_content)

        print("SQL executed successfully!")

        # Clean up temp file on device
        subprocess.run(["adb", "shell", f"rm {TEMP_SQL_PATH}"], timeout=10)

        return True

    except subprocess.TimeoutExpired:
        print("Error: ADB command timed out")
        return False


def execute_via_adb_alternative(sql_content: str) -> bool:
    """Alternative method: pull DB using base64, modify locally, push back."""
    import sqlite3 as sqlite
    import base64

    # Use a persistent local directory to avoid temp cleanup issues
    local_dir = Path(__file__).parent / "seed_data"
    local_dir.mkdir(exist_ok=True)
    local_db = local_dir / "app_database_local.db"

    app_db_path = "/data/data/com.boox.day/files/booxdaydb/app_database"
    app_db_dir = "/data/data/com.boox.day/files/booxdaydb"

    # Step 0: Force stop the app to prevent DB write conflicts
    print("Stopping app to prevent DB conflicts...")
    subprocess.run(
        ["adb", "shell", "am", "force-stop", "com.boox.day"],
        capture_output=True,
        timeout=10,
    )

    # Step 1: Checkpoint WAL to consolidate all changes into main DB file
    print("Checkpointing WAL mode...")
    subprocess.run(
        ["adb", "shell", f"run-as com.boox.day sqlite3 {app_db_path} 'PRAGMA wal_checkpoint(TRUNCATE)'"],
        capture_output=True,
        timeout=30,
    )
    # Note: This may fail if sqlite3 isn't available, but that's OK - we'll try anyway

    print("Pulling database from device (using base64)...")

    # Step 1: Use base64 to safely transfer binary data
    # This avoids binary corruption issues on Windows
    result = subprocess.run(
        ["adb", "shell", f"run-as com.boox.day base64 {app_db_path}"],
        capture_output=True,
        text=True,
        timeout=120,
    )
    if result.returncode != 0:
        print(f"Error reading database: {result.stderr}")
        return False

    # Decode base64 and save locally
    try:
        db_bytes = base64.b64decode(result.stdout.strip())
        local_db.write_bytes(db_bytes)
    except Exception as e:
        print(f"Error decoding database: {e}")
        return False

    print(f"Database size: {len(db_bytes)} bytes")
    print("Executing SQL locally...")

    # Execute SQL
    conn = None
    try:
        conn = sqlite.connect(str(local_db))
        cursor = conn.cursor()

        # Checkpoint any existing WAL content first
        cursor.execute("PRAGMA wal_checkpoint(TRUNCATE)")

        # Use executescript which properly handles SQL parsing
        # (naive split by semicolon breaks on semicolons inside string values)
        cursor.executescript(sql_content)
        conn.commit()

        # Switch to DELETE journal mode and vacuum to create a clean single-file DB
        # This ensures no WAL/SHM files are needed
        cursor.execute("PRAGMA journal_mode=DELETE")
        cursor.execute("VACUUM")
        conn.commit()

        print("SQL executed successfully")
    except sqlite.Error as e:
        print(f"SQLite error: {e}")
        if conn:
            conn.close()
        return False
    finally:
        if conn:
            conn.close()

    print("Pushing database back to device...")

    # Step 2: Push modified DB to /data/local/tmp
    tmp_device = "/data/local/tmp/booxday_seed_db"

    result = subprocess.run(
        ["adb", "push", str(local_db), tmp_device],
        capture_output=True,
        text=True,
        timeout=60,
    )
    if result.returncode != 0:
        print(f"Error pushing database: {result.stderr}")
        return False

    # Step 3: Make it world-readable so run-as can access it
    subprocess.run(["adb", "shell", f"chmod 644 {tmp_device}"], timeout=10)

    # Step 4: Copy into app sandbox using run-as (reading from world-readable tmp)
    result = subprocess.run(
        ["adb", "shell", f"run-as com.boox.day sh -c 'cat {tmp_device} > {app_db_path}'"],
        capture_output=True,
        text=True,
        timeout=60,
    )

    # Clean up temp file
    subprocess.run(["adb", "shell", f"rm -f {tmp_device}"], timeout=10)

    if result.returncode != 0:
        print(f"Error copying database to app: {result.stderr}")
        return False

    # Step 5: Delete WAL and SHM files to prevent corruption
    # These files can cause issues if they're out of sync with the main DB
    print("Cleaning up WAL files...")
    subprocess.run(
        ["adb", "shell", f"run-as com.boox.day sh -c 'rm -f {app_db_path}-wal {app_db_path}-shm'"],
        capture_output=True,
        timeout=10,
    )

    # Step 6: Force stop again to ensure app reloads fresh data on next launch
    print("Force stopping app to ensure fresh reload...")
    subprocess.run(
        ["adb", "shell", "am", "force-stop", "com.boox.day"],
        capture_output=True,
        timeout=10,
    )

    # Step 7: Launch the app so user sees the data immediately
    print("Launching app...")
    subprocess.run(
        ["adb", "shell", "am", "start", "-n", "com.boox.day/.MainActivity"],
        capture_output=True,
        timeout=10,
    )

    # Clean up local copy
    try:
        local_db.unlink()
    except:
        pass  # Ignore cleanup errors

    print("Database updated successfully!")
    return True


# ============================================================================
# Main
# ============================================================================


def main():
    parser = argparse.ArgumentParser(
        description="Seed BooxDay database with test data",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--events",
        type=int,
        default=500,
        help="Number of events to generate (default: 500)",
    )
    parser.add_argument(
        "--tasks",
        type=int,
        default=200,
        help="Number of tasks to generate (default: 200)",
    )
    parser.add_argument(
        "--start-date",
        type=str,
        default="2026-02-10",
        help="Start date (YYYY-MM-DD, default: 2026-02-10)",
    )
    parser.add_argument(
        "--end-date",
        type=str,
        default="2026-04-20",
        help="End date (YYYY-MM-DD, default: 2026-04-20)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Generate SQL without executing",
    )
    parser.add_argument(
        "--no-clear",
        action="store_true",
        help="Don't clear existing data before seeding",
    )
    parser.add_argument(
        "--output",
        type=str,
        help="Custom output path for SQL file",
    )
    parser.add_argument(
        "--persona",
        type=int,
        choices=range(len(USER_PERSONAS)),
        default=None,
        metavar="N",
        help=f"AI persona index (0-{len(USER_PERSONAS)-1}). Random if not specified.",
    )
    parser.add_argument(
        "--no-ai-prefs",
        action="store_true",
        help="Skip AI preferences seeding",
    )
    parser.add_argument(
        "--no-memos",
        action="store_true",
        help="Skip memo pages seeding",
    )
    parser.add_argument(
        "--life-persona",
        type=str,
        choices=["tech", "teacher"],
        default="tech",
        help="Life persona for content generation: 'tech' (Tech Professional) or 'teacher' (High School Teacher). Default: tech",
    )

    args = parser.parse_args()

    # Parse dates
    try:
        start_date = datetime.strptime(args.start_date, "%Y-%m-%d")
        end_date = datetime.strptime(args.end_date, "%Y-%m-%d")
    except ValueError as e:
        print(f"Error parsing date: {e}")
        sys.exit(1)

    if start_date > end_date:
        print("Error: start-date must be before end-date")
        sys.exit(1)

    # Select project templates based on life persona
    life_persona = args.life_persona
    if life_persona == "teacher":
        selected_templates = TEACHER_PROJECT_TEMPLATES
        life_persona_desc = "Teacher (High School English Teacher)"
    else:
        selected_templates = PROJECT_TEMPLATES
        life_persona_desc = "Tech Professional"

    if args.tasks < len(selected_templates):
        print(
            f"Error: --tasks must be at least {len(selected_templates)} "
            "to include all project templates."
        )
        sys.exit(1)

    print(f"Generating test data for {args.start_date} to {args.end_date}")
    print(f"  Life Persona: {life_persona_desc}")
    print(f"  Events: {args.events}")
    print(f"  Tasks: {args.tasks}")
    if not args.no_memos:
        days_count = (end_date - start_date).days + 1
        print(f"  Memos: ~{days_count * 2}-{days_count * 4} pages ({days_count} days)")
    if not args.no_ai_prefs:
        persona_desc = f"persona {args.persona}" if args.persona is not None else "random persona"
        print(f"  AI Preferences: {persona_desc}")
    print()

    # Generate SQL
    sql_statements = []

    # Clear existing data (unless --no-clear)
    if not args.no_clear:
        sql_statements.extend(generate_clear_statements(args.start_date, args.end_date))

    # Generate events and collect titles by day for memo generation
    print("Generating events...")
    event_sql = generate_events(args.events, start_date, end_date, life_persona)
    sql_statements.extend(event_sql)
    sql_statements.append("")  # Blank line

    # Extract event titles by day from generated SQL for memo linking
    events_by_day = {}
    for sql in event_sql:
        if sql.startswith("INSERT INTO local_events"):
            # Extract dayId and title from the SQL
            title_match = re.search(r"VALUES \(\s*'[^']+',\s*'([^']+)',\s*'(\d{4}-\d{2}-\d{2})'", sql)
            if title_match:
                title = title_match.group(1)
                day_id = title_match.group(2)
                if day_id not in events_by_day:
                    events_by_day[day_id] = []
                events_by_day[day_id].append(title)

    # Generate tasks (with pages) - includes parent+children in contiguous blocks
    print("Generating tasks and subtasks...")
    task_sql, standalone_count, subtask_count = generate_tasks_and_pages(args.tasks, start_date, end_date, life_persona)
    sql_statements.extend(task_sql)
    sql_statements.append("")  # Blank line

    # Generate memos (unless --no-memos)
    memo_page_count = 0
    memo_annotation_count = 0
    if not args.no_memos:
        print("Generating memo pages with text annotations...")
        memo_sql, memo_page_count, memo_annotation_count = generate_memos(
            start_date, end_date, args.persona, events_by_day, life_persona
        )
        sql_statements.extend(memo_sql)
        sql_statements.append("")  # Blank line

    # Generate AI preferences (unless --no-ai-prefs)
    ai_prefs_sql = []
    selected_persona = None
    if not args.no_ai_prefs:
        print("Generating AI preferences...")
        # Clear existing AI preferences if clearing other data
        if not args.no_clear:
            sql_statements.extend(generate_clear_ai_preferences_sql())
        ai_prefs_sql = generate_ai_preferences_sql(args.persona)
        sql_statements.extend(ai_prefs_sql)

        # Determine which persona was selected for display
        if args.persona is not None:
            selected_persona = USER_PERSONAS[args.persona]["name"]
        else:
            # When random, we can't know which was picked without re-calling
            # Just indicate it was random
            selected_persona = "Random"

    # Combine SQL
    sql_content = "\n".join(sql_statements)

    # Count actual records
    event_count = len([s for s in event_sql if s.startswith("INSERT INTO local_events")])
    task_count = standalone_count
    task_page_count = len([s for s in task_sql if s.startswith("INSERT INTO page")])

    print()
    print(f"Generated:")
    print(f"  {event_count} events")
    print(f"  {task_count} tasks (+ {subtask_count} subtasks = {task_count + subtask_count} total)")
    print(f"  {task_page_count} task pages")
    if not args.no_memos:
        print(f"  {memo_page_count} memo pages")
        print(f"  {memo_annotation_count} text annotations")
        print(f"  (OCR indexing will be handled by app's background service)")
    if not args.no_ai_prefs:
        print(f"  AI preferences (persona: {selected_persona})")
    print()

    # Save SQL file
    if args.output:
        output_path = Path(args.output)
    else:
        output_path = Path(__file__).parent / "seed_data" / "seed.sql"

    output_path.parent.mkdir(exist_ok=True)
    output_path.write_text(sql_content, encoding="utf-8")
    print(f"SQL saved to: {output_path}")

    # Execute if not dry-run
    if args.dry_run:
        print()
        print("Dry run complete. SQL file generated but not executed.")
        print(f"To execute manually: adb shell 'sqlite3 {DB_PATH}' < {output_path}")
    else:
        print()
        print("Checking ADB connection...")

        if not check_adb_connection():
            print("Error: No device connected. Connect a device or use --dry-run.")
            sys.exit(1)

        print("Executing SQL on device...")
        if execute_via_adb(sql_content):
            print()
            print("Done! Open BooxDay and navigate to January 2026 to see the test data.")
        else:
            print()
            print("Failed to execute SQL on device.")
            print(f"You can try manually: adb shell 'run-as com.boox.day sqlite3 {DB_PATH}' < {output_path}")
            sys.exit(1)


if __name__ == "__main__":
    main()
