#!/usr/bin/env python3
"""
TODO Reduction Script

Identifies and categorizes TODOs for removal:
1. Vague TODOs (< 5 words, no context)
2. Obsolete TODOs (already done, outdated)
3. Duplicate TODOs
4. Template/Example TODOs
"""

import re
from pathlib import Path
from typing import List, Dict, Set

YAPPC_ROOT = Path("/Users/samujjwal/Development/ghatana/products/yappc")

# Patterns for obsolete TODOs
OBSOLETE_PATTERNS = [
    r'update.*java\s*\d+',  # "update to Java X" when already updated
    r'migrate.*new.*api',   # "migrate to new API" when already migrated
    r'fix.*later',          # "fix this later" with no context
    r'cleanup',             # vague "cleanup" without specifics
    r'improve',             # vague "improve" without specifics
    r'consider',            # "consider doing X" without decision
    r'maybe',               # "maybe do X" without commitment
    r'placeholder',         # placeholder comments
]

# Patterns for vague TODOs
VAGUE_PATTERNS = [
    r'^TODO:?\s*$',                    # Empty TODO
    r'^TODO:?\s*\w{1,3}\s*$',          # 1-3 word TODO
    r'^TODO:?\s*fix\s*$',              # Just "fix"
    r'^TODO:?\s*update\s*$',           # Just "update"
    r'^TODO:?\s*check\s*$',            # Just "check"
]

def analyze_todo_file(file_path: Path) -> Dict[str, List[str]]:
    """Analyze a file for TODOs and categorize them."""
    if not file_path.exists():
        return {}
    
    content = file_path.read_text()
    lines = content.split('\n')
    
    results = {
        'vague': [],
        'obsolete': [],
        'template': [],
        'keep': []
    }
    
    for i, line in enumerate(lines, 1):
        if 'TODO' not in line and 'FIXME' not in line:
            continue
        
        # Extract TODO text
        todo_match = re.search(r'(TODO|FIXME):?\s*(.+)', line, re.IGNORECASE)
        if not todo_match:
            continue
        
        todo_text = todo_match.group(2).strip()
        location = f"{file_path}:{i}"
        
        # Check if vague
        is_vague = False
        for pattern in VAGUE_PATTERNS:
            if re.search(pattern, todo_text, re.IGNORECASE):
                results['vague'].append(location)
                is_vague = True
                break
        
        if is_vague:
            continue
        
        # Check if obsolete
        is_obsolete = False
        for pattern in OBSOLETE_PATTERNS:
            if re.search(pattern, todo_text, re.IGNORECASE):
                results['obsolete'].append(location)
                is_obsolete = True
                break
        
        if is_obsolete:
            continue
        
        # Check if template/example (in generator files)
        if 'Generator.java' in str(file_path) or 'Template' in str(file_path):
            if 'grep' in line or 'echo' in line or '#' in line:
                results['template'].append(location)
                continue
        
        # Keep this TODO
        results['keep'].append(location)
    
    return results

def scan_all_files() -> Dict[str, Dict[str, List[str]]]:
    """Scan all Java and TypeScript files."""
    all_results = {
        'vague': [],
        'obsolete': [],
        'template': [],
        'keep': []
    }
    
    # Scan Java files
    java_files = list((YAPPC_ROOT / 'core').rglob('*.java'))
    for file_path in java_files:
        results = analyze_todo_file(file_path)
        for category in all_results:
            all_results[category].extend(results.get(category, []))
    
    # Scan TypeScript files
    ts_files = list((YAPPC_ROOT / 'frontend').rglob('*.ts')) + \
               list((YAPPC_ROOT / 'frontend').rglob('*.tsx'))
    for file_path in ts_files:
        results = analyze_todo_file(file_path)
        for category in all_results:
            all_results[category].extend(results.get(category, []))
    
    return all_results

def generate_report(results: Dict[str, List[str]]) -> str:
    """Generate reduction report."""
    total = sum(len(v) for v in results.values())
    removable = len(results['vague']) + len(results['obsolete'])
    
    report = f"""# TODO Reduction Analysis

**Date:** 2026-03-23
**Total TODOs Analyzed:** {total}
**Removable TODOs:** {removable} ({removable * 100 // total if total > 0 else 0}%)
**TODOs to Keep:** {len(results['keep'])}
**Template TODOs:** {len(results['template'])}

---

## Breakdown

### Vague TODOs (Recommend Remove): {len(results['vague'])}
TODOs with insufficient context or < 5 words

### Obsolete TODOs (Recommend Remove): {len(results['obsolete'])}
TODOs that are already done or outdated

### Template TODOs (Keep): {len(results['template'])}
TODOs in generator/template code (intentional examples)

### Actionable TODOs (Keep): {len(results['keep'])}
TODOs with clear context and actionable items

---

## Recommendations

1. **Remove {removable} vague/obsolete TODOs**
   - Run cleanup script to remove these automatically
   - Or manually review and remove

2. **Convert {min(20, len(results['keep']))} actionable TODOs to GitHub issues**
   - Create issues for high-priority items
   - Link TODO comments to issue numbers

3. **Document {len(results['template'])} template TODOs**
   - Add comments clarifying these are examples
   - Exclude from TODO counts

---

## Next Steps

1. Review vague TODOs list and confirm removal
2. Review obsolete TODOs list and confirm removal
3. Execute cleanup (removes {removable} TODOs)
4. Create GitHub issues for top actionable TODOs
5. Re-scan to verify target <100 TODOs achieved

**Expected Final Count:** {len(results['keep']) + len(results['template'])} TODOs
"""
    
    return report

def main():
    """Main execution."""
    print("🔍 Analyzing TODOs for reduction...")
    print("")
    
    results = scan_all_files()
    
    print(f"📊 Analysis Complete:")
    print(f"  Vague: {len(results['vague'])}")
    print(f"  Obsolete: {len(results['obsolete'])}")
    print(f"  Template: {len(results['template'])}")
    print(f"  Keep: {len(results['keep'])}")
    print("")
    
    # Generate report
    report = generate_report(results)
    report_path = YAPPC_ROOT / 'docs' / 'todo-reports' / 'TODO_REDUCTION_ANALYSIS.md'
    report_path.write_text(report)
    
    print(f"✅ Report generated: {report_path}")
    print("")
    print(f"🎯 Reduction potential: {len(results['vague']) + len(results['obsolete'])} TODOs")

if __name__ == '__main__':
    main()
