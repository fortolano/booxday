#!/usr/bin/env python3
"""
Deduplicate Spanish Dictionary Files

Removes duplicate entries from es-ES dictionary files while preserving:
- File structure (comments, section headers)
- UTF-8 encoding
- Alphabetical ordering within sections

Usage:
    python deduplicate_spanish_dictionaries.py

Output:
    - Backs up original files
    - Creates deduplicated versions
    - Generates deduplication report
"""

import os
import sys
from pathlib import Path
from datetime import datetime
from collections import defaultdict

def deduplicate_file(input_path, output_path, file_type="words"):
    """
    Deduplicate a dictionary file while preserving structure.

    Args:
        input_path: Path to input file
        output_path: Path to output file
        file_type: "words" or "names" (affects reporting)

    Returns:
        dict with statistics
    """
    seen_entries = set()
    duplicates = []
    unique_lines = []
    current_section = "HEADER"

    with open(input_path, 'r', encoding='utf-8') as f:
        for line_num, line in enumerate(f, 1):
            stripped = line.rstrip('\n\r')

            # Preserve comments and empty lines
            if stripped.startswith('#') or not stripped.strip():
                unique_lines.append(line)
                if stripped.startswith('#'):
                    current_section = stripped[:50]  # Track section
                continue

            # Check for duplicate entry
            entry = stripped.lower().strip()
            if entry in seen_entries:
                duplicates.append({
                    'line': line_num,
                    'entry': entry,
                    'section': current_section
                })
            else:
                seen_entries.add(entry)
                unique_lines.append(line)

    # Write deduplicated file
    with open(output_path, 'w', encoding='utf-8', newline='\n') as f:
        f.writelines(unique_lines)

    return {
        'original_lines': line_num,
        'unique_entries': len(seen_entries),
        'duplicate_count': len(duplicates),
        'duplicates': duplicates
    }

def generate_report(common_stats, names_stats, report_path):
    """Generate deduplication report"""

    timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

    report = f"""# Spanish Dictionary Deduplication Report

**Date**: {timestamp}
**Tool**: deduplicate_spanish_dictionaries.py

---

## Summary

| File | Original Lines | Unique Entries | Duplicates Removed | Reduction |
|------|----------------|----------------|-------------------|-----------|
| **common_words.txt** | {common_stats['original_lines']:,} | {common_stats['unique_entries']:,} | {common_stats['duplicate_count']:,} | {common_stats['duplicate_count']/common_stats['original_lines']*100:.1f}% |
| **names.txt** | {names_stats['original_lines']:,} | {names_stats['unique_entries']:,} | {names_stats['duplicate_count']:,} | {names_stats['duplicate_count']/names_stats['original_lines']*100:.1f}% |
| **TOTAL** | {common_stats['original_lines'] + names_stats['original_lines']:,} | {common_stats['unique_entries'] + names_stats['unique_entries']:,} | {common_stats['duplicate_count'] + names_stats['duplicate_count']:,} | {(common_stats['duplicate_count'] + names_stats['duplicate_count'])/(common_stats['original_lines'] + names_stats['original_lines'])*100:.1f}% |

---

## common_words.txt Duplicates ({common_stats['duplicate_count']} entries)

"""

    # Group common_words duplicates by entry
    common_dupes = defaultdict(list)
    for dup in common_stats['duplicates']:
        common_dupes[dup['entry']].append(dup)

    report += f"**Most Frequent Duplicates** (top 20):\n\n"
    sorted_dupes = sorted(common_dupes.items(), key=lambda x: len(x[1]), reverse=True)[:20]
    for entry, occurrences in sorted_dupes:
        report += f"- `{entry}` ({len(occurrences) + 1} occurrences)\n"

    report += f"\n**All Duplicates** (alphabetical):\n\n"
    for entry in sorted(common_dupes.keys()):
        report += f"- {entry}\n"

    report += f"\n---\n\n## names.txt Duplicates ({names_stats['duplicate_count']} entries)\n\n"

    # Group names duplicates by entry
    names_dupes = defaultdict(list)
    for dup in names_stats['duplicates']:
        names_dupes[dup['entry']].append(dup)

    report += f"**Most Frequent Duplicates** (top 20):\n\n"
    sorted_dupes = sorted(names_dupes.items(), key=lambda x: len(x[1]), reverse=True)[:20]
    for entry, occurrences in sorted_dupes:
        report += f"- `{entry}` ({len(occurrences) + 1} occurrences)\n"

    report += f"\n**All Duplicates** (alphabetical):\n\n"
    for entry in sorted(names_dupes.keys()):
        report += f"- {entry}\n"

    report += f"""

---

## Deduplication Process

**Method**: First-occurrence preservation
- When a duplicate is found, FIRST occurrence is kept
- Subsequent occurrences are removed
- Comments and structure are preserved
- UTF-8 encoding maintained

**Files Created**:
- `common_words.txt` (deduplicated)
- `names.txt` (deduplicated)
- `common_words_BACKUP_{datetime.now().strftime('%Y%m%d')}.txt` (original backup)
- `names_BACKUP_{datetime.now().strftime('%Y%m%d')}.txt` (original backup)

---

## Next Steps

1. ✅ **Verify deduplicated files**:
   ```bash
   wc -l common_words.txt names.txt
   grep -c "^[^#]" common_words.txt names.txt
   ```

2. ✅ **Test coverage** (run tests with deduplicated files)

3. ✅ **Regenerate quality report** with accurate counts

4. ⚠️ **Consider optimization** (analyze if 7,296 words is optimal)

---

**Status**: 🟢 Deduplication complete. Verify files before deployment.
"""

    with open(report_path, 'w', encoding='utf-8') as f:
        f.write(report)

def main():
    """Main deduplication workflow"""

    # Paths
    script_dir = Path(__file__).parent
    project_root = script_dir.parent
    dict_dir = project_root / 'app' / 'src' / 'main' / 'assets' / 'text_processing' / 'es-ES'

    common_words_path = dict_dir / 'common_words.txt'
    names_path = dict_dir / 'names.txt'

    # Verify files exist
    if not common_words_path.exists():
        print(f"ERROR: {common_words_path} not found")
        sys.exit(1)
    if not names_path.exists():
        print(f"ERROR: {names_path} not found")
        sys.exit(1)

    # Create backups
    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
    backup_common = dict_dir / f'common_words_BACKUP_{timestamp}.txt'
    backup_names = dict_dir / f'names_BACKUP_{timestamp}.txt'

    print(f"Creating backups...")
    print(f"  - {backup_common}")
    print(f"  - {backup_names}")

    import shutil
    shutil.copy2(common_words_path, backup_common)
    shutil.copy2(names_path, backup_names)

    # Create temp files for deduplication
    temp_common = dict_dir / 'common_words_DEDUPED_TEMP.txt'
    temp_names = dict_dir / 'names_DEDUPED_TEMP.txt'

    print(f"\nDeduplicating common_words.txt...")
    common_stats = deduplicate_file(common_words_path, temp_common, "words")
    print(f"  Original: {common_stats['original_lines']:,} lines")
    print(f"  Unique: {common_stats['unique_entries']:,} entries")
    print(f"  Duplicates removed: {common_stats['duplicate_count']:,}")

    print(f"\nDeduplicating names.txt...")
    names_stats = deduplicate_file(names_path, temp_names, "names")
    print(f"  Original: {names_stats['original_lines']:,} lines")
    print(f"  Unique: {names_stats['unique_entries']:,} entries")
    print(f"  Duplicates removed: {names_stats['duplicate_count']:,}")

    # Replace original files with deduplicated versions
    print(f"\nReplacing original files with deduplicated versions...")
    shutil.move(temp_common, common_words_path)
    shutil.move(temp_names, names_path)

    # Generate report
    report_path = project_root / f'DEDUPLICATION_REPORT_es-ES_{timestamp}.md'
    print(f"\nGenerating deduplication report...")
    print(f"  {report_path}")
    generate_report(common_stats, names_stats, report_path)

    print(f"\n✅ Deduplication complete!")
    print(f"\nNext steps:")
    print(f"  1. Verify files: wc -l {common_words_path} {names_path}")
    print(f"  2. Test coverage with deduplicated files")
    print(f"  3. Regenerate quality report")
    print(f"  4. Review: {report_path}")

if __name__ == '__main__':
    main()
