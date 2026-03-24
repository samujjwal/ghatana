#!/bin/bash

###############################################################################
# YAPPC TODO Scanner
#
# Scans all Java and TypeScript files for TODO/FIXME comments
# Generates categorized reports for reduction campaign
###############################################################################

set -e

YAPPC_ROOT="/Users/samujjwal/Development/ghatana/products/yappc"
DOCS_DIR="$YAPPC_ROOT/docs"

echo "🔍 Scanning TODOs in YAPPC..."
echo ""

# Create output directory
mkdir -p "$DOCS_DIR/todo-reports"

# Scan Java files
echo "📝 Scanning Java files..."
find "$YAPPC_ROOT/core" -name "*.java" -type f -exec grep -Hn "TODO\|FIXME" {} \; > "$DOCS_DIR/todo-reports/todos-java.txt" 2>/dev/null || true

# Scan TypeScript files
echo "📝 Scanning TypeScript files..."
find "$YAPPC_ROOT/frontend" -name "*.ts" -o -name "*.tsx" -type f -exec grep -Hn "TODO\|FIXME" {} \; > "$DOCS_DIR/todo-reports/todos-typescript.txt" 2>/dev/null || true

# Count TODOs
JAVA_COUNT=$(wc -l < "$DOCS_DIR/todo-reports/todos-java.txt" | tr -d ' ')
TS_COUNT=$(wc -l < "$DOCS_DIR/todo-reports/todos-typescript.txt" | tr -d ' ')
TOTAL=$((JAVA_COUNT + TS_COUNT))

echo ""
echo "📊 TODO Summary:"
echo "  Java TODOs: $JAVA_COUNT"
echo "  TypeScript TODOs: $TS_COUNT"
echo "  Total: $TOTAL"
echo ""

# Categorize by pattern
echo "🏷️  Categorizing TODOs..."

# Critical TODOs
grep -i "FIXME\|BUG\|CRITICAL\|SECURITY\|VULNERABILITY\|BROKEN\|URGENT" \
    "$DOCS_DIR/todo-reports/todos-java.txt" \
    "$DOCS_DIR/todo-reports/todos-typescript.txt" \
    > "$DOCS_DIR/todo-reports/todos-critical.txt" 2>/dev/null || true

# Performance TODOs
grep -i "PERF\|OPTIMIZE\|SLOW\|PERFORMANCE" \
    "$DOCS_DIR/todo-reports/todos-java.txt" \
    "$DOCS_DIR/todo-reports/todos-typescript.txt" \
    > "$DOCS_DIR/todo-reports/todos-performance.txt" 2>/dev/null || true

# Quality TODOs
grep -i "REFACTOR\|CLEANUP\|IMPROVE\|DEBT\|SMELL" \
    "$DOCS_DIR/todo-reports/todos-java.txt" \
    "$DOCS_DIR/todo-reports/todos-typescript.txt" \
    > "$DOCS_DIR/todo-reports/todos-quality.txt" 2>/dev/null || true

# Count categories
CRITICAL_COUNT=$(wc -l < "$DOCS_DIR/todo-reports/todos-critical.txt" | tr -d ' ')
PERF_COUNT=$(wc -l < "$DOCS_DIR/todo-reports/todos-performance.txt" | tr -d ' ')
QUALITY_COUNT=$(wc -l < "$DOCS_DIR/todo-reports/todos-quality.txt" | tr -d ' ')

echo "  Critical: $CRITICAL_COUNT"
echo "  Performance: $PERF_COUNT"
echo "  Quality: $QUALITY_COUNT"
echo ""

# Generate summary report
cat > "$DOCS_DIR/todo-reports/TODO_SCAN_SUMMARY.md" << EOF
# TODO Scan Summary

**Date:** $(date +"%Y-%m-%d %H:%M:%S")  
**Total TODOs:** $TOTAL  
**Target:** <100  
**Reduction Required:** $((TOTAL - 100)) TODOs ($(((TOTAL - 100) * 100 / TOTAL))%)

---

## Breakdown by Language

| Language | Count | Percentage |
|----------|-------|------------|
| Java | $JAVA_COUNT | $((JAVA_COUNT * 100 / TOTAL))% |
| TypeScript | $TS_COUNT | $((TS_COUNT * 100 / TOTAL))% |
| **Total** | **$TOTAL** | **100%** |

---

## Breakdown by Category

| Category | Count | Priority |
|----------|-------|----------|
| Critical (FIXME, BUG, SECURITY) | $CRITICAL_COUNT | P0 - Immediate |
| Performance (OPTIMIZE, SLOW) | $PERF_COUNT | P1 - High |
| Quality (REFACTOR, CLEANUP) | $QUALITY_COUNT | P2 - Medium |
| Uncategorized | $((TOTAL - CRITICAL_COUNT - PERF_COUNT - QUALITY_COUNT)) | Review needed |

---

## Files Generated

- \`todos-java.txt\` - All Java TODOs with file locations
- \`todos-typescript.txt\` - All TypeScript TODOs with file locations
- \`todos-critical.txt\` - Critical TODOs requiring immediate attention
- \`todos-performance.txt\` - Performance-related TODOs
- \`todos-quality.txt\` - Code quality TODOs

---

## Next Steps

1. **Review Critical TODOs** (\`todos-critical.txt\`)
   - Create GitHub issues for each
   - Assign P0 priority
   - Track in sprint

2. **Review Performance TODOs** (\`todos-performance.txt\`)
   - Evaluate impact
   - Create issues for high-impact items
   - Schedule in backlog

3. **Review Quality TODOs** (\`todos-quality.txt\`)
   - Identify quick wins
   - Create issues for major refactorings
   - Remove obsolete items

4. **Manual Review**
   - Review uncategorized TODOs
   - Remove vague/obsolete TODOs
   - Update completed TODOs

---

**Target Completion:** 4 weeks  
**Weekly Goal:** Reduce by ~850 TODOs per week
EOF

echo "✅ TODO scan complete!"
echo ""
echo "📄 Reports generated in: $DOCS_DIR/todo-reports/"
echo "📋 Summary: $DOCS_DIR/todo-reports/TODO_SCAN_SUMMARY.md"
echo ""
echo "🎯 Next: Review critical TODOs and create GitHub issues"
