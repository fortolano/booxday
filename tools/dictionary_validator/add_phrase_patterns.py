#!/usr/bin/env python3
"""
Add phrase patterns to intents that are missing them.

This script adds meaningful phrase patterns to improve intent classification accuracy.
"""

import json
import os
from pathlib import Path

# ============================================================================
# PHRASE PATTERNS FOR EACH INTENT
# ============================================================================

INTENT_PATTERNS = {
    "action_review.json": [
        {"pattern": "action\\s+(?:item|review)", "weight": 3.5},
        {"pattern": "follow[\\s-]?up\\s+(?:on|item|task)", "weight": 3.0},
        {"pattern": "(?:pending|outstanding)\\s+(?:action|task)", "weight": 3.0},
        {"pattern": "what\\s+(?:was|were)\\s+(?:decided|agreed)", "weight": 2.5}
    ],
    "brainstorm.json": [
        {"pattern": "brainstorm(?:ing)?\\s+(?:session|ideas|notes)", "weight": 4.0},
        {"pattern": "idea\\s+(?:dump|storm|list)", "weight": 3.5},
        {"pattern": "(?:possible|potential)\\s+(?:ideas|options)", "weight": 3.0},
        {"pattern": "(?:creative|free)\\s+thinking", "weight": 2.5}
    ],
    "budget.json": [
        {"pattern": "budget\\s+(?:for|plan|breakdown)", "weight": 4.0},
        {"pattern": "(?:monthly|annual|quarterly)\\s+budget", "weight": 3.5},
        {"pattern": "(?:income|expense)\\s+(?:plan|forecast)", "weight": 3.0},
        {"pattern": "(?:allocate|allocation)\\s+(?:for|to)", "weight": 2.5}
    ],
    "call_log.json": [
        {"pattern": "(?:call|phone)\\s+(?:log|notes|summary)", "weight": 4.0},
        {"pattern": "(?:spoke|talked|called)\\s+with", "weight": 3.5},
        {"pattern": "(?:discussed|conversation)\\s+(?:about|with)", "weight": 3.0},
        {"pattern": "follow[\\s-]?up\\s+call", "weight": 3.0}
    ],
    "comparison.json": [
        {"pattern": "(?:compare|comparison)\\s+(?:of|between)", "weight": 4.0},
        {"pattern": "(?:vs|versus|\\/|\\|)\\s+", "weight": 3.0},
        {"pattern": "(?:pros|cons)\\s+(?:and|of)", "weight": 3.5},
        {"pattern": "(?:which|what)\\s+(?:is|would be)\\s+better", "weight": 3.0}
    ],
    "contact_info.json": [
        {"pattern": "contact\\s+(?:info|information|details)", "weight": 4.0},
        {"pattern": "(?:email|phone|address)\\s*:", "weight": 3.5},
        {"pattern": "@[a-z]+\\.[a-z]+", "weight": 3.0},
        {"pattern": "\\d{3}[\\s.-]\\d{3}[\\s.-]\\d{4}", "weight": 3.5}
    ],
    "draft_message.json": [
        {"pattern": "(?:draft|compose)\\s+(?:email|message|reply)", "weight": 4.0},
        {"pattern": "(?:dear|hi|hello)\\s+[A-Z][a-z]+", "weight": 3.0},
        {"pattern": "(?:subject|re|fwd)\\s*:", "weight": 3.5},
        {"pattern": "(?:send|reply)\\s+(?:to|about)", "weight": 2.5}
    ],
    "expense_log.json": [
        {"pattern": "(?:expense|spending)\\s+(?:log|tracker|report)", "weight": 4.0},
        {"pattern": "\\$\\s*\\d+(?:\\.\\d{2})?", "weight": 3.5},
        {"pattern": "(?:paid|spent|cost)\\s+\\$?\\d+", "weight": 3.0},
        {"pattern": "(?:receipt|reimburs)", "weight": 3.0}
    ],
    "goal_setting.json": [
        {"pattern": "(?:set|setting)\\s+(?:goal|objective)", "weight": 4.0},
        {"pattern": "(?:smart|okr|kpi)\\s+goal", "weight": 4.0},
        {"pattern": "(?:by|before|within)\\s+(?:end\\s+of|next)", "weight": 2.5},
        {"pattern": "(?:achieve|accomplish|complete)\\s+(?:by|within)", "weight": 3.0}
    ],
    "instructions.json": [
        {"pattern": "(?:step|how)\\s+(?:\\d+|to)", "weight": 3.5},
        {"pattern": "(?:first|then|next|finally)", "weight": 2.5},
        {"pattern": "(?:instructions?|guide|tutorial)\\s+(?:for|on)", "weight": 4.0},
        {"pattern": "\\d+\\.\\s+[A-Z]", "weight": 3.0}
    ],
    "journal.json": [
        {"pattern": "(?:dear|today)\\s+(?:diary|journal)?", "weight": 4.0},
        {"pattern": "(?:i\\s+feel|feeling|felt)\\s+(?:like|so|very)?", "weight": 3.5},
        {"pattern": "(?:today|yesterday|this\\s+(?:morning|evening))", "weight": 2.5},
        {"pattern": "(?:grateful|thankful)\\s+(?:for|that)", "weight": 3.0}
    ],
    "leadership_notes.json": [
        {"pattern": "(?:leadership|leader)\\s+(?:notes|meeting|discussion)", "weight": 4.0},
        {"pattern": "(?:team|org)\\s+(?:structure|dynamics|culture)", "weight": 3.0},
        {"pattern": "(?:mentor|coach)(?:ing)?\\s+(?:session|notes)", "weight": 3.5},
        {"pattern": "(?:development|growth)\\s+(?:plan|opportunity)", "weight": 3.0}
    ],
    "lecture_notes.json": [
        {"pattern": "(?:lecture|class)\\s+(?:notes|summary)", "weight": 4.0},
        {"pattern": "(?:professor|instructor|teacher)\\s+(?:said|mentioned)", "weight": 3.5},
        {"pattern": "(?:key|main)\\s+(?:point|concept|takeaway)", "weight": 3.0},
        {"pattern": "(?:chapter|unit|module)\\s+\\d+", "weight": 3.0}
    ],
    "meeting_notes.json": [
        {"pattern": "(?:meeting|call)\\s+(?:notes|summary|minutes)", "weight": 4.0},
        {"pattern": "(?:attendee|participant)s?\\s*:", "weight": 3.5},
        {"pattern": "action\\s+items?\\s*:", "weight": 4.0},
        {"pattern": "(?:discussed|reviewed|decided)", "weight": 2.5}
    ],
    "people_management.json": [
        {"pattern": "(?:1[:\\-x]1|one[\\s-]on[\\s-]one)\\s+(?:with|notes)", "weight": 4.0},
        {"pattern": "(?:performance|feedback)\\s+(?:review|discussion)", "weight": 3.5},
        {"pattern": "(?:career|development)\\s+(?:plan|goal|conversation)", "weight": 3.5},
        {"pattern": "(?:team|direct\\s+report)\\s+(?:update|check[\\s-]?in)", "weight": 3.0}
    ],
    "planning.json": [
        {"pattern": "(?:plan|planning)\\s+(?:for|to|session)", "weight": 4.0},
        {"pattern": "(?:timeline|schedule|roadmap)\\s+(?:for|of)", "weight": 3.5},
        {"pattern": "(?:phase|milestone|deliverable)\\s+\\d+", "weight": 3.0},
        {"pattern": "(?:by|before|deadline)\\s+(?:end\\s+of)?", "weight": 2.5}
    ],
    "pros_cons.json": [
        {"pattern": "(?:pros?|cons?)\\s+(?:and|&|/)", "weight": 4.0},
        {"pattern": "(?:advantage|disadvantage|benefit|drawback)", "weight": 3.5},
        {"pattern": "(?:positive|negative)\\s+(?:aspect|side|point)", "weight": 3.0},
        {"pattern": "(?:for|against)\\s+(?:this|the)", "weight": 2.5}
    ],
    "quote_snippet.json": [
        {"pattern": "[\"""].*[\"""]\\s*[-–—]\\s*[A-Z]", "weight": 4.0},
        {"pattern": "(?:quote|saying|proverb)\\s*:", "weight": 3.5},
        {"pattern": "(?:as|according\\s+to)\\s+[A-Z][a-z]+\\s+(?:said|wrote)", "weight": 3.0},
        {"pattern": "[-–—]\\s*[A-Z][a-z]+\\s+[A-Z][a-z]+", "weight": 2.5}
    ],
    "reference.json": [
        {"pattern": "(?:reference|citation|source)\\s*:", "weight": 4.0},
        {"pattern": "(?:isbn|doi|url)\\s*:", "weight": 4.0},
        {"pattern": "\\(\\d{4}\\)", "weight": 3.0},
        {"pattern": "(?:et\\s+al|pp?\\.\\s*\\d+)", "weight": 3.5}
    ],
    "research.json": [
        {"pattern": "(?:research|study)\\s+(?:on|about|notes)", "weight": 4.0},
        {"pattern": "(?:finding|result|conclusion)s?\\s*:", "weight": 3.5},
        {"pattern": "(?:hypothesis|theory|evidence)\\s+(?:that|for)", "weight": 3.0},
        {"pattern": "(?:source|data)\\s+(?:from|collection)", "weight": 2.5}
    ],
    "talking_points.json": [
        {"pattern": "(?:talking|key)\\s+(?:point|topic)s?", "weight": 4.0},
        {"pattern": "(?:mention|discuss|bring\\s+up)\\s+(?:that|the)", "weight": 3.0},
        {"pattern": "(?:important|key)\\s+(?:to\\s+)?(?:highlight|mention)", "weight": 3.0},
        {"pattern": "[-•]\\s+(?:mention|discuss|point)", "weight": 2.5}
    ],
    "todo_list.json": [
        {"pattern": "to[\\s-]?do\\s*(?:list|items?)?", "weight": 4.0},
        {"pattern": "(?:task|item)s?\\s+(?:to|for)\\s+(?:complete|do)", "weight": 3.5},
        {"pattern": "\\[\\s*\\]", "weight": 4.0},
        {"pattern": "(?:need|must|should)\\s+to\\s+(?:do|complete|finish)", "weight": 3.0}
    ],
    "troubleshooting.json": [
        {"pattern": "(?:troubleshoot|debug)(?:ing)?\\s+(?:steps?|notes)", "weight": 4.0},
        {"pattern": "(?:issue|problem|error)\\s*:", "weight": 3.5},
        {"pattern": "(?:tried|attempt|solution)\\s*:", "weight": 3.0},
        {"pattern": "(?:fix|resolve|workaround)\\s+(?:for|by)", "weight": 3.0}
    ],
    "wishlist.json": [
        {"pattern": "(?:wish|want)\\s*list", "weight": 4.0},
        {"pattern": "(?:would\\s+like|hoping\\s+(?:for|to))", "weight": 3.0},
        {"pattern": "(?:birthday|holiday|christmas)\\s+(?:list|ideas)", "weight": 3.5},
        {"pattern": "(?:gift|present)\\s+(?:idea|suggestion)", "weight": 3.0}
    ]
}


def add_patterns_to_file(file_path: str, patterns: list) -> bool:
    """Add phrase patterns to a file."""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)

        # Add or update phrasePatterns
        data['phrasePatterns'] = patterns

        with open(file_path, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write('\n')

        return True
    except Exception as e:
        print(f"ERROR: {file_path}: {e}")
        return False


def main():
    intents_dir = Path("C:/Users/fort/Projects/Boox_Day/app/src/main/assets/classification/en/intents")

    if not intents_dir.exists():
        print(f"ERROR: Directory not found: {intents_dir}")
        return

    success = 0
    failed = 0

    for filename, patterns in INTENT_PATTERNS.items():
        file_path = intents_dir / filename
        if not file_path.exists():
            print(f"WARNING: File not found: {filename}")
            failed += 1
            continue

        if add_patterns_to_file(str(file_path), patterns):
            print(f"OK: {filename} - added {len(patterns)} patterns")
            success += 1
        else:
            failed += 1

    print(f"\n{'='*60}")
    print(f"DONE: {success} files updated, {failed} failures")
    print(f"{'='*60}")


if __name__ == "__main__":
    main()
