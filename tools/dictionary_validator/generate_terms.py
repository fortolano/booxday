#!/usr/bin/env python3
"""
AI-Assisted Term Generation System for SemanticGenius Dictionaries

This tool generates domain/intent-specific terminology tailored to:
- Business/Project/Product Managers
- Teachers & College Professors
- Students
- LDS Church Leaders
- Professionals

Usage:
  python generate_terms.py --domain work --output suggestions.json
  python generate_terms.py --intent meeting_notes --output suggestions.json
  python generate_terms.py --list-prompts
  python generate_terms.py --analyze work.json --suggest

The tool generates prompts optimized for LLM term generation and can
analyze existing dictionaries to suggest expansion areas.
"""

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Dict, List, Set, Optional
from collections import defaultdict

# Base path for dictionaries
DICT_BASE = Path("C:/Users/fort/Projects/Boox_Day/app/src/main/assets/classification/en")

# =============================================================================
# DOMAIN-SPECIFIC TERM GENERATION PROMPTS
# =============================================================================
# These prompts are designed to generate comprehensive terminology for each domain

DOMAIN_PROMPTS = {
    "work": """Generate professional workplace terminology for a calendar/note-taking app used by:
- Business managers, project managers, product managers
- Team leads and department heads
- Knowledge workers and professionals

Categories to cover:
1. MEETING TYPES: standup, sync, kickoff, retrospective, all-hands, townhall, offsite, workshop, brainstorm, debrief, check-in, status update, sprint planning, demo, showcase, roadmap review
2. PROJECT TERMS: sprint, milestone, deliverable, blocker, dependency, scope, timeline, deadline, backlog, epic, story, task, subtask, burndown, velocity, capacity
3. REVIEW CYCLES: quarterly review, annual review, mid-year, performance, calibration, promotion, compensation, feedback session
4. COLLABORATION TOOLS: Teams, Zoom, Slack, Jira, Asana, Notion, Confluence, Monday, Trello, Linear, Figma, Miro
5. TIME PATTERNS: EOD, EOW, EOM, EOQ, fiscal year, H1, H2, Q1-Q4, YTD, MTD, WTD
6. WORK ACTIVITIES: handoff, escalation, delegation, ownership, accountability, stakeholder, alignment, sync, async

Output format: JSON with tierA (5-10 unique terms), tierB (15-25 strong terms), tierC (30-50 supporting terms)
Focus on terms that UNIQUELY indicate work context vs personal life.""",

    "management": """Generate management and leadership terminology for:
- People managers conducting 1:1s
- Department heads running teams
- HR professionals
- Executive leadership

Categories:
1. PEOPLE MANAGEMENT: 1:1, one-on-one, skip-level, coaching, mentoring, feedback, development plan, career growth, performance improvement plan, PIP
2. TEAM DYNAMICS: team building, morale, engagement, retention, attrition, turnover, headcount, org chart, reporting structure
3. HIRING: interview, candidate, requisition, job posting, offer letter, onboarding, offboarding, exit interview
4. PERFORMANCE: objectives, KPIs, OKRs, goals, targets, metrics, rating, calibration, promotion, compensation, bonus
5. LEADERSHIP: strategy, vision, mission, values, culture, alignment, communication, transparency, accountability

Output: JSON with tierA/tierB/tierC weighted terms uniquely identifying management vs general work.""",

    "teaching": """Generate education terminology for:
- K-12 teachers
- College professors
- Academic instructors
- Educational administrators

Categories:
1. INSTRUCTION: lecture, seminar, workshop, lab, discussion section, tutorial, office hours, recitation, practicum
2. ASSESSMENT: quiz, test, exam, midterm, final, rubric, grading, feedback, evaluation, peer review, self-assessment
3. CURRICULUM: syllabus, module, unit, chapter, learning objectives, standards, competencies, prerequisites, electives
4. STUDENTS: attendance, participation, behavior, IEP, 504 plan, accommodations, differentiation, remediation, enrichment
5. ACADEMIC CALENDAR: semester, trimester, quarter, credits, GPA, academic year, spring break, finals week
6. TOOLS: Canvas, Blackboard, Moodle, Google Classroom, Turnitin, Gradebook, LMS
7. MEETINGS: faculty meeting, department meeting, parent-teacher conference, IEP meeting, curriculum committee

Output: JSON with terms uniquely identifying teaching/education context.""",

    "student": """Generate student-focused terminology for:
- High school students
- College/university students
- Graduate students
- PhD candidates

Categories:
1. STUDYING: study session, review, flashcards, notes, summary, outline, memorization, practice problems, study group
2. ASSIGNMENTS: homework, essay, paper, project, presentation, lab report, problem set, reading assignment
3. EXAMS: midterm, final, quiz, test, exam prep, study guide, practice exam, cramming
4. ACADEMIC: GPA, credits, major, minor, thesis, dissertation, defense, advisor, committee, research
5. CAMPUS LIFE: office hours, tutoring, library, dorm, dining hall, registration, financial aid, scholarship
6. PLANNING: study plan, schedule, deadline, due date, submission, extension, incomplete

Output: JSON with terms uniquely identifying student academic context.""",

    "lds_leadership": """Generate LDS (Latter-day Saint) church leadership terminology for:
- Bishops and bishopric members
- Stake presidents and stake leadership
- Relief Society, Elders Quorum, Young Men/Women leaders
- Primary and Sunday School leaders

Categories:
1. CALLINGS: bishop, bishopric, stake president, relief society president, elders quorum president, young men president, young women president, primary president, sunday school president, ward clerk, executive secretary
2. COUNCILS: ward council, bishopric meeting, stake council, PEC (priesthood executive committee), welfare committee, youth committee
3. MINISTERING: ministering interview, ministering assignment, ministering companion, home teaching (legacy), visiting teaching (legacy)
4. FINANCIAL: tithing settlement, tithing declaration, fast offering, ward budget, missionary fund, humanitarian fund, perpetual education fund
5. TEMPLE: temple recommend, temple recommend interview, temple prep, endowment, sealing, baptisms for the dead
6. YOUTH: youth conference, trek, mutual, seminary, FSY, EFY, young men/young women activity, duty to god, personal progress
7. PROGRAMS: come follow me, ministering, self-reliance, addiction recovery, pathway, BYU-I online
8. LEADERSHIP: stewardship, keys, priesthood, calling extension, release, sustaining, setting apart, ordination
9. MEETINGS: sacrament meeting, sunday school, relief society meeting, elders quorum meeting, primary, ward conference, stake conference

Output: JSON with terms uniquely identifying LDS church leadership context vs general Christianity.""",

    "strategy": """Generate strategic planning terminology for:
- Executives and senior leaders
- Strategy consultants
- Business planners

Categories:
1. PLANNING: strategic plan, roadmap, vision, mission, goals, objectives, initiatives, OKRs, SWOT analysis
2. ANALYSIS: market analysis, competitive analysis, PESTLE, Porter's five forces, value chain, benchmarking
3. EXECUTION: implementation, rollout, change management, transformation, pivot, iteration
4. METRICS: KPIs, success metrics, leading indicators, lagging indicators, ROI, TCO

Output: JSON with strategy-specific terms.""",

    "business": """Generate business operations terminology for:
- Business owners
- Sales professionals
- Account managers
- Client-facing roles

Categories:
1. CLIENTS: client meeting, account review, business review, QBR, proposal, pitch, presentation, demo
2. SALES: pipeline, forecast, quota, target, deal, opportunity, lead, prospect, conversion
3. CONTRACTS: agreement, SOW, MSA, NDA, terms, negotiation, renewal, expansion
4. REVENUE: revenue, ARR, MRR, churn, retention, upsell, cross-sell

Output: JSON with business/sales specific terms.""",

    "entrepreneurship": """Generate startup and entrepreneurship terminology for:
- Founders and co-founders
- Startup teams
- Venture-backed companies

Categories:
1. STARTUP: MVP, product-market fit, pivot, iteration, launch, beta, alpha, early adopter
2. FUNDING: fundraising, pitch deck, investor meeting, term sheet, valuation, cap table, dilution, runway
3. GROWTH: traction, metrics, growth hacking, viral, network effects, scale, hypergrowth

Output: JSON with startup/entrepreneurship terms.""",

    "finance": """Generate finance and accounting terminology for:
- Financial analysts
- Accountants
- CFOs and finance teams

Categories:
1. ACCOUNTING: P&L, balance sheet, cash flow, EBITDA, gross margin, net income, revenue recognition
2. BUDGETING: budget, forecast, variance, actuals, projections, FP&A
3. INVESTMENTS: portfolio, returns, risk, diversification, allocation

Output: JSON with finance-specific terms.""",

    "health": """Generate health and medical terminology for:
- Patients tracking appointments
- Healthcare workers
- Fitness enthusiasts

Categories:
1. MEDICAL: doctor appointment, checkup, physical, screening, lab work, prescription, medication, therapy
2. FITNESS: workout, exercise, gym, training, run, walk, swim, yoga, stretching
3. WELLNESS: nutrition, diet, sleep, meditation, mental health, self-care

Output: JSON with health/wellness terms.""",

    "personal": """Generate personal life terminology for:
- General personal planning
- Life goals and habits
- Self-improvement

Categories:
1. GOALS: personal goal, habit, resolution, milestone, achievement, progress
2. LIFE EVENTS: birthday, anniversary, celebration, party, gathering
3. SELF-IMPROVEMENT: journaling, reflection, gratitude, mindfulness

Output: JSON with personal/life terms.""",

    "family": """Generate family-related terminology for:
- Parents managing family schedules
- Family activities and events

Categories:
1. PARENTING: kids, children, school, daycare, babysitter, pediatrician, sports practice
2. HOUSEHOLD: chores, errands, grocery, cleaning, maintenance, repairs
3. FAMILY EVENTS: family dinner, family meeting, reunion, vacation, holiday

Output: JSON with family-specific terms.""",

    "spiritual": """Generate general spiritual/religious terminology (non-LDS specific):
- Christians of various denominations
- General religious practice

Categories:
1. WORSHIP: church, service, worship, sermon, prayer, bible study, small group
2. PRACTICE: devotional, quiet time, meditation, fasting, scripture reading
3. COMMUNITY: fellowship, congregation, ministry, volunteer, service project

Output: JSON with general spiritual terms (distinct from LDS_LEADERSHIP)."""
}

# =============================================================================
# INTENT-SPECIFIC TERM GENERATION PROMPTS
# =============================================================================

INTENT_PROMPTS = {
    "meeting_notes": """Generate meeting notes terminology:
- Attendees, participants, facilitator, note-taker
- Agenda items, discussion points, topics covered
- Action items, follow-ups, next steps, owners, due dates
- Decisions made, agreements, outcomes
- Key takeaways, summary, recap

Phrase patterns to include:
- "meeting with [person/team]"
- "notes from [meeting type]"
- "action items from"
- "follow up on"
- "discussed [topic]"
""",

    "planning": """Generate planning/roadmap terminology:
- Timeline, phases, milestones, dependencies
- Resources, capacity, allocation
- Risks, blockers, contingencies
- Goals, objectives, success criteria

Phrase patterns:
- "plan for [project/quarter]"
- "roadmap for"
- "timeline for"
- "phases of"
""",

    "goal_setting": """Generate goal-setting terminology:
- OKRs, KPIs, SMART goals, targets
- Objectives, key results, metrics
- Quarterly goals, annual goals
- Progress tracking, status updates

Phrase patterns:
- "goal for [period]"
- "objective:"
- "key result:"
- "target for"
""",

    "lesson_plan": """Generate lesson planning terminology for teachers:
- Learning objectives, standards, outcomes
- Activities, exercises, demonstrations
- Materials, resources, handouts
- Assessment, evaluation, rubric
- Differentiation, accommodations

Phrase patterns:
- "lesson on [topic]"
- "learning objective:"
- "students will be able to"
- "assessment:"
""",

    "grading": """Generate grading/assessment terminology:
- Rubric, criteria, standards
- Scores, grades, points, percentage
- Feedback, comments, corrections
- Late work, incomplete, missing

Phrase patterns:
- "grade [assignment]"
- "rubric for"
- "feedback on"
- "scores for"
""",

    "study_plan": """Generate study planning terminology for students:
- Study schedule, review session
- Practice problems, flashcards
- Topics to cover, chapters, units
- Exam prep, test review

Phrase patterns:
- "study for [exam]"
- "review [chapter/topic]"
- "practice [subject]"
- "prepare for"
""",

    "lds_council": """Generate LDS council meeting terminology:
- Ward council, bishopric meeting, PEC
- Agenda items, assignments, reports
- Welfare needs, ministering concerns
- Calendar coordination, upcoming events

Phrase patterns:
- "ward council - [topic]"
- "bishopric meeting agenda"
- "discuss [member/family]"
- "welfare needs:"
""",

    "lds_calling": """Generate LDS calling-related terminology:
- Calling extension, interview, prayer
- Stewardship, responsibilities, training
- Release, sustaining, setting apart
- Keys, priesthood authority

Phrase patterns:
- "extend calling to"
- "interview [name] for"
- "release [name] from"
- "set apart [name]"
""",

    "lds_ministering": """Generate LDS ministering terminology:
- Ministering assignment, companion
- Visit, contact, check-in
- Needs, concerns, support
- Report, interview with bishop

Phrase patterns:
- "ministering to [family]"
- "visit [name/family]"
- "ministering interview"
- "needs of [family]"
""",

    "lds_financial": """Generate LDS financial terminology:
- Tithing settlement, declaration
- Fast offering, donation
- Ward budget, expenses
- Missionary fund, temple fund

Phrase patterns:
- "tithing settlement with"
- "fast offering for"
- "ward budget:"
- "missionary fund donation"
""",

    "lds_lesson_prep": """Generate LDS lesson preparation terminology:
- Come Follow Me, Sunday School
- Relief Society lesson, Elders Quorum
- Primary lesson, Young Men/Women
- Scripture references, discussion questions

Phrase patterns:
- "lesson prep - [topic]"
- "come follow me [chapter]"
- "sunday school lesson"
- "discussion questions for"
""",

    "thesis_work": """Generate thesis/dissertation terminology:
- Research, literature review, methodology
- Data collection, analysis, findings
- Chapters, sections, drafts
- Advisor meeting, committee feedback
- Defense, revisions, submission

Phrase patterns:
- "thesis chapter [number]"
- "advisor meeting"
- "literature review on"
- "defense preparation"
"""
}

# =============================================================================
# ANALYSIS AND SUGGESTION FUNCTIONS
# =============================================================================

def load_dictionary(file_path: Path) -> dict:
    """Load a dictionary JSON file."""
    with open(file_path, 'r', encoding='utf-8') as f:
        return json.load(f)


def get_all_terms(data: dict) -> Set[str]:
    """Extract all terms from a dictionary."""
    terms = set()
    for tier in ["tierA", "tierB", "tierC"]:
        if tier in data and isinstance(data[tier], dict):
            tier_terms = data[tier].get("terms", [])
            if isinstance(tier_terms, list):
                terms.update(tier_terms)
    return terms


def analyze_dictionary(file_path: Path) -> dict:
    """Analyze a dictionary file and suggest improvements."""
    data = load_dictionary(file_path)
    name = file_path.stem

    analysis = {
        "name": name,
        "type": "domain" if "domain" in data else "intent",
        "current_stats": {
            "tierA": len(data.get("tierA", {}).get("terms", [])),
            "tierB": len(data.get("tierB", {}).get("terms", [])),
            "tierC": len(data.get("tierC", {}).get("terms", [])),
            "patterns": len(data.get("phrasePatterns", []))
        },
        "targets": {},
        "gaps": {},
        "suggestions": []
    }

    # Set targets based on type
    if analysis["type"] == "domain":
        analysis["targets"] = {"tierA": 15, "tierB": 35, "tierC": 50, "patterns": 15}
    else:
        analysis["targets"] = {"tierA": 10, "tierB": 20, "tierC": 30, "patterns": 10}

    # Calculate gaps
    for tier in ["tierA", "tierB", "tierC", "patterns"]:
        current = analysis["current_stats"][tier]
        target = analysis["targets"][tier]
        gap = target - current
        analysis["gaps"][tier] = gap if gap > 0 else 0

    # Generate suggestions
    total_gap = sum(analysis["gaps"].values())
    if total_gap > 0:
        analysis["suggestions"].append(f"Total expansion needed: {total_gap} items")
        for tier, gap in analysis["gaps"].items():
            if gap > 0:
                analysis["suggestions"].append(f"  - Add {gap} more {tier} items")

    # Check for specific issues
    terms = get_all_terms(data)
    if len(terms) < 30:
        analysis["suggestions"].append("CRITICAL: Dictionary is severely underdeveloped")

    return analysis


def suggest_terms_for_category(category_name: str, category_type: str) -> str:
    """Get the generation prompt for a category."""
    if category_type == "domain":
        return DOMAIN_PROMPTS.get(category_name.lower(), f"Generate comprehensive terms for the {category_name} domain.")
    else:
        return INTENT_PROMPTS.get(category_name.lower(), f"Generate comprehensive terms for the {category_name} intent.")


def generate_expansion_report(base_path: Path) -> dict:
    """Generate a comprehensive expansion report for all dictionaries."""
    report = {
        "domains": [],
        "intents": [],
        "priority_expansion": [],
        "summary": {}
    }

    # Analyze domains
    domains_path = base_path / "domains"
    if domains_path.is_dir():
        for f in sorted(domains_path.glob("*.json")):
            analysis = analyze_dictionary(f)
            report["domains"].append(analysis)

    # Analyze intents
    intents_path = base_path / "intents"
    if intents_path.is_dir():
        for f in sorted(intents_path.glob("*.json")):
            analysis = analyze_dictionary(f)
            report["intents"].append(analysis)

    # Identify priority expansions (largest gaps)
    all_items = report["domains"] + report["intents"]
    sorted_by_gap = sorted(all_items, key=lambda x: sum(x["gaps"].values()), reverse=True)
    report["priority_expansion"] = sorted_by_gap[:10]

    # Summary
    total_current = sum(
        sum(item["current_stats"].values()) for item in all_items
    )
    total_target = sum(
        sum(item["targets"].values()) for item in all_items
    )

    report["summary"] = {
        "total_files": len(all_items),
        "total_current_items": total_current,
        "total_target_items": total_target,
        "total_gap": total_target - total_current,
        "completion_percentage": round((total_current / total_target) * 100, 1) if total_target > 0 else 0
    }

    return report


def print_generation_prompt(category: str, category_type: str):
    """Print the generation prompt for a category."""
    prompt = suggest_terms_for_category(category, category_type)

    print("=" * 80)
    print(f"TERM GENERATION PROMPT: {category.upper()} ({category_type})")
    print("=" * 80)
    print()
    print("Copy this prompt to an LLM (Claude, GPT, Gemini) to generate terms:")
    print()
    print("-" * 80)
    print(prompt)
    print("-" * 80)
    print()
    print("After generation, validate terms with:")
    print(f"  python validate_dictionaries.py --dictionaries {DICT_BASE}")


def print_expansion_report(report: dict):
    """Print the expansion report."""
    print("=" * 80)
    print("DICTIONARY EXPANSION REPORT")
    print("=" * 80)

    # Summary
    s = report["summary"]
    print(f"\nOverall Status: {s['completion_percentage']}% complete")
    print(f"Total files: {s['total_files']}")
    print(f"Current items: {s['total_current_items']}")
    print(f"Target items: {s['total_target_items']}")
    print(f"Gap to fill: {s['total_gap']}")

    # Priority expansions
    print("\n" + "=" * 80)
    print("TOP 10 PRIORITY EXPANSIONS")
    print("=" * 80)

    for i, item in enumerate(report["priority_expansion"], 1):
        total_gap = sum(item["gaps"].values())
        print(f"\n{i}. {item['name']} ({item['type']})")
        print(f"   Current: tierA={item['current_stats']['tierA']}, tierB={item['current_stats']['tierB']}, tierC={item['current_stats']['tierC']}, patterns={item['current_stats']['patterns']}")
        print(f"   Gaps: tierA={item['gaps']['tierA']}, tierB={item['gaps']['tierB']}, tierC={item['gaps']['tierC']}, patterns={item['gaps']['patterns']}")
        print(f"   Total gap: {total_gap}")

    # All domains
    print("\n" + "=" * 80)
    print("ALL DOMAINS")
    print("=" * 80)

    for item in sorted(report["domains"], key=lambda x: x["name"]):
        gap = sum(item["gaps"].values())
        status = "OK" if gap == 0 else f"GAP: {gap}"
        total = sum(item["current_stats"].values())
        print(f"  {item['name']}: {total} items [{status}]")

    # All intents
    print("\n" + "=" * 80)
    print("ALL INTENTS")
    print("=" * 80)

    for item in sorted(report["intents"], key=lambda x: x["name"]):
        gap = sum(item["gaps"].values())
        status = "OK" if gap == 0 else f"GAP: {gap}"
        total = sum(item["current_stats"].values())
        print(f"  {item['name']}: {total} items [{status}]")


def list_available_prompts():
    """List all available generation prompts."""
    print("=" * 80)
    print("AVAILABLE TERM GENERATION PROMPTS")
    print("=" * 80)

    print("\nDOMAIN PROMPTS:")
    for domain in sorted(DOMAIN_PROMPTS.keys()):
        print(f"  --domain {domain}")

    print("\nINTENT PROMPTS:")
    for intent in sorted(INTENT_PROMPTS.keys()):
        print(f"  --intent {intent}")

    print("\nUsage:")
    print("  python generate_terms.py --domain work")
    print("  python generate_terms.py --intent meeting_notes")
    print("  python generate_terms.py --report")


def main():
    parser = argparse.ArgumentParser(
        description="AI-assisted term generation for SemanticGenius dictionaries"
    )
    parser.add_argument("--domain", help="Generate prompt for a domain")
    parser.add_argument("--intent", help="Generate prompt for an intent")
    parser.add_argument("--analyze", help="Analyze a specific dictionary file")
    parser.add_argument("--report", action="store_true", help="Generate full expansion report")
    parser.add_argument("--list-prompts", action="store_true", help="List available prompts")
    parser.add_argument("--output", help="Output file for results")

    args = parser.parse_args()

    if args.list_prompts:
        list_available_prompts()
    elif args.domain:
        print_generation_prompt(args.domain, "domain")
    elif args.intent:
        print_generation_prompt(args.intent, "intent")
    elif args.analyze:
        analysis = analyze_dictionary(Path(args.analyze))
        if args.output:
            with open(args.output, 'w') as f:
                json.dump(analysis, f, indent=2)
            print(f"Analysis written to {args.output}")
        else:
            print(json.dumps(analysis, indent=2))
    elif args.report:
        report = generate_expansion_report(DICT_BASE)
        if args.output:
            with open(args.output, 'w') as f:
                json.dump(report, f, indent=2)
            print(f"Report written to {args.output}")
        else:
            print_expansion_report(report)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
