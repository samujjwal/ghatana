# YAPPC TODO Reduction Strategy

**Date:** March 23, 2026  
**Current TODOs:** 3,520  
**Target:** <100  
**Reduction Required:** 97% (3,420 TODOs)

---

## 🎯 Strategy Overview

Systematic approach to reduce TODOs from 3,520 to <100 through categorization, prioritization, and action.

---

## 📊 Categorization Framework

### Category 1: Immediate Action Required (Critical)
**Criteria:**
- Blocks functionality
- Security concerns
- Data integrity issues
- Breaking changes needed

**Action:** Convert to GitHub issues, assign, track

### Category 2: Important Improvements
**Criteria:**
- Performance optimizations
- User experience enhancements
- Code quality improvements
- Technical debt

**Action:** Convert to GitHub issues, prioritize in backlog

### Category 3: Nice-to-Have
**Criteria:**
- Future enhancements
- Experimental features
- Minor optimizations
- Documentation improvements

**Action:** Document in feature backlog, remove from code

### Category 4: Obsolete/Vague
**Criteria:**
- Already completed
- No longer relevant
- Unclear intent
- Duplicate entries

**Action:** Remove immediately

---

## 🔄 Execution Plan

### Phase 1: Automated Scanning (Week 1)
```bash
# Scan all TODO comments
grep -r "TODO" --include="*.java" --include="*.ts" --include="*.tsx" core/ frontend/ > todos-raw.txt

# Categorize by pattern
grep -i "FIXME\|BUG\|CRITICAL" todos-raw.txt > todos-critical.txt
grep -i "PERF\|OPTIMIZE\|SLOW" todos-raw.txt > todos-performance.txt
grep -i "REFACTOR\|CLEANUP\|IMPROVE" todos-raw.txt > todos-quality.txt
```

### Phase 2: Manual Review (Week 1-2)
- Review each TODO in context
- Assign category (1-4)
- Create GitHub issues for categories 1-2
- Document category 3 in feature backlog
- Remove category 4 immediately

### Phase 3: Conversion (Week 2-3)
**For Critical & Important TODOs:**
```markdown
GitHub Issue Template:
Title: [TODO] Brief description
Labels: todo-migration, priority-{high|medium|low}
Body:
- Original TODO location: file:line
- Category: {Critical|Important}
- Description: Full context
- Acceptance Criteria: Clear definition of done
```

### Phase 4: Cleanup (Week 3-4)
- Remove TODOs converted to issues
- Remove obsolete TODOs
- Update code with issue references where needed
- Verify TODO count <100

---

## 📋 Sample Categorization

### Critical Examples
```java
// TODO: Fix SQL injection vulnerability in query builder
// TODO: Handle null pointer exception in payment processing
// TODO: Implement proper authentication before production
```
**Action:** Immediate GitHub issues, P0 priority

### Important Examples
```java
// TODO: Optimize database query - currently O(n²)
// TODO: Add caching layer for frequently accessed data
// TODO: Refactor this 500-line method into smaller functions
```
**Action:** GitHub issues, prioritize in sprint planning

### Nice-to-Have Examples
```java
// TODO: Consider adding dark mode support
// TODO: Explore GraphQL as alternative to REST
// TODO: Add more comprehensive logging
```
**Action:** Document in feature backlog, remove from code

### Obsolete Examples
```java
// TODO: Update to Java 11 (already on Java 21)
// TODO: Migrate to new API (migration complete)
// TODO: Fix this later (no context, unclear)
```
**Action:** Remove immediately

---

## 🛠️ Automation Scripts

### Script 1: TODO Scanner
```bash
#!/bin/bash
# scan-todos.sh

YAPPC_ROOT="/Users/samujjwal/Development/ghatana/products/yappc"

echo "Scanning TODOs in YAPPC..."

# Java files
find "$YAPPC_ROOT/core" -name "*.java" -exec grep -Hn "TODO\|FIXME" {} \; > "$YAPPC_ROOT/docs/todos-java.txt"

# TypeScript files
find "$YAPPC_ROOT/frontend" -name "*.ts" -name "*.tsx" -exec grep -Hn "TODO\|FIXME" {} \; > "$YAPPC_ROOT/docs/todos-typescript.txt"

# Count
JAVA_COUNT=$(wc -l < "$YAPPC_ROOT/docs/todos-java.txt")
TS_COUNT=$(wc -l < "$YAPPC_ROOT/docs/todos-typescript.txt")
TOTAL=$((JAVA_COUNT + TS_COUNT))

echo "Java TODOs: $JAVA_COUNT"
echo "TypeScript TODOs: $TS_COUNT"
echo "Total: $TOTAL"
```

### Script 2: TODO Categorizer
```python
#!/usr/bin/env python3
# categorize-todos.py

import re
from pathlib import Path

CRITICAL_PATTERNS = [
    r'FIXME', r'BUG', r'CRITICAL', r'SECURITY', r'VULNERABILITY',
    r'BROKEN', r'URGENT', r'BLOCKER'
]

IMPORTANT_PATTERNS = [
    r'PERF', r'OPTIMIZE', r'SLOW', r'REFACTOR', r'CLEANUP',
    r'IMPROVE', r'TECHNICAL DEBT', r'CODE SMELL'
]

NICE_TO_HAVE_PATTERNS = [
    r'CONSIDER', r'EXPLORE', r'MAYBE', r'FUTURE', r'ENHANCEMENT',
    r'NICE TO HAVE', r'OPTIONAL'
]

def categorize_todo(todo_line):
    """Categorize a TODO based on patterns."""
    upper = todo_line.upper()
    
    for pattern in CRITICAL_PATTERNS:
        if re.search(pattern, upper):
            return 'CRITICAL'
    
    for pattern in IMPORTANT_PATTERNS:
        if re.search(pattern, upper):
            return 'IMPORTANT'
    
    for pattern in NICE_TO_HAVE_PATTERNS:
        if re.search(pattern, upper):
            return 'NICE_TO_HAVE'
    
    # Check for vague/obsolete
    if len(todo_line.split()) < 5:
        return 'OBSOLETE'
    
    return 'UNCATEGORIZED'

# Process todos and generate report
```

### Script 3: GitHub Issue Creator
```bash
#!/bin/bash
# create-github-issues.sh

# Read critical TODOs and create issues
while IFS= read -r line; do
    FILE=$(echo "$line" | cut -d: -f1)
    LINE_NUM=$(echo "$line" | cut -d: -f2)
    TODO_TEXT=$(echo "$line" | cut -d: -f3-)
    
    # Create GitHub issue via gh CLI
    gh issue create \
        --title "[TODO] $TODO_TEXT" \
        --body "Location: $FILE:$LINE_NUM\nCategory: Critical\n\n$TODO_TEXT" \
        --label "todo-migration,priority-high"
done < todos-critical.txt
```

---

## 📈 Success Metrics

### Weekly Targets
| Week | Target | Actions |
|------|--------|---------|
| Week 1 | Scan & categorize all TODOs | Automated scanning + manual review |
| Week 2 | Convert 50% critical/important | Create GitHub issues |
| Week 3 | Convert remaining + cleanup | Issue creation + code cleanup |
| Week 4 | Final verification | Verify <100 TODOs remain |

### Quality Gates
- ✅ All critical TODOs converted to P0 issues
- ✅ All important TODOs converted to issues or documented
- ✅ All obsolete TODOs removed
- ✅ Remaining TODOs <100
- ✅ All remaining TODOs have clear context and actionability

---

## 🎯 Immediate Actions (This Week)

1. **Run TODO Scanner**
   ```bash
   cd /Users/samujjwal/Development/ghatana/products/yappc
   ./scripts/scan-todos.sh
   ```

2. **Review Critical TODOs**
   - Open `docs/todos-java.txt` and `docs/todos-typescript.txt`
   - Identify critical TODOs (security, bugs, blockers)
   - Create GitHub issues immediately

3. **Quick Wins**
   - Remove obviously obsolete TODOs
   - Update completed TODOs
   - Fix vague TODOs with proper context

4. **Track Progress**
   - Update TODO count daily
   - Report progress weekly
   - Adjust strategy as needed

---

## 📋 Governance

### TODO Standards Going Forward
```java
// ✅ GOOD: Clear, actionable, with context
// TODO(username): Add input validation for email field (Issue #123)

// ❌ BAD: Vague, no context
// TODO: Fix this later

// ✅ GOOD: Linked to issue
// TODO: Optimize query performance (see Issue #456)

// ❌ BAD: No action plan
// TODO: This could be better
```

### Prevention
- **Pre-commit hook:** Warn on new TODOs without issue reference
- **Code review:** Require justification for new TODOs
- **Sprint planning:** Allocate time for TODO reduction
- **Quality gate:** Fail CI if TODO count exceeds threshold

---

## 🏆 Success Criteria

### Completion Checklist
- [ ] All 3,520 TODOs scanned and categorized
- [ ] Critical TODOs converted to P0 GitHub issues
- [ ] Important TODOs converted to issues or documented
- [ ] Nice-to-have TODOs documented in feature backlog
- [ ] Obsolete TODOs removed from codebase
- [ ] Final TODO count <100
- [ ] All remaining TODOs have clear context
- [ ] TODO standards documented and enforced
- [ ] Prevention mechanisms in place

---

**Status:** Ready to execute  
**Owner:** YAPPC Core Team  
**Timeline:** 4 weeks  
**Next Action:** Run TODO scanner and begin categorization
