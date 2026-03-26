#!/bin/bash
# TODO Tracker - Scans codebase for TODO/FIXME comments and generates tracking report

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_FILE="${ROOT_DIR}/TODO_TRACKING_REPORT.md"

echo "=== Scanning for TODOs and FIXMEs ==="
echo ""

# Temporary files
JAVA_TODOS=$(mktemp)
KOTLIN_TODOS=$(mktemp)
TS_TODOS=$(mktemp)

# Scan Java files
echo "Scanning Java files..."
grep -rn --include="*.java" -E "(TODO|FIXME):" "$ROOT_DIR" > "$JAVA_TODOS" 2>/dev/null || true

# Scan Kotlin files
echo "Scanning Kotlin files..."
grep -rn --include="*.kt" -E "(TODO|FIXME):" "$ROOT_DIR" > "$KOTLIN_TODOS" 2>/dev/null || true

# Scan TypeScript files
echo "Scanning TypeScript files..."
grep -rn --include="*.ts" --include="*.tsx" -E "(TODO|FIXME):" "$ROOT_DIR" > "$TS_TODOS" 2>/dev/null || true

# Count totals
JAVA_COUNT=$(wc -l < "$JAVA_TODOS" | tr -d ' ')
KOTLIN_COUNT=$(wc -l < "$KOTLIN_TODOS" | tr -d ' ')
TS_COUNT=$(wc -l < "$TS_TODOS" | tr -d ' ')
TOTAL=$((JAVA_COUNT + KOTLIN_COUNT + TS_COUNT))

echo ""
echo "Found $TOTAL TODOs/FIXMEs:"
echo "  Java: $JAVA_COUNT"
echo "  Kotlin: $KOTLIN_COUNT"
echo "  TypeScript: $TS_COUNT"
echo ""

# Generate report
cat > "$OUTPUT_FILE" << EOF
# TODO Tracking Report

**Generated**: $(date)  
**Total TODOs/FIXMEs**: $TOTAL

---

## Summary by Language

| Language | Count | Percentage |
|----------|-------|------------|
| Java | $JAVA_COUNT | $(( JAVA_COUNT * 100 / (TOTAL + 1) ))% |
| Kotlin | $KOTLIN_COUNT | $(( KOTLIN_COUNT * 100 / (TOTAL + 1) ))% |
| TypeScript | $TS_COUNT | $(( TS_COUNT * 100 / (TOTAL + 1) ))% |

---

## Top Files with TODOs

### Java Files
EOF

# Top 10 Java files with most TODOs
if [ -s "$JAVA_TODOS" ]; then
    echo "" >> "$OUTPUT_FILE"
    awk -F: '{print $1}' "$JAVA_TODOS" | sort | uniq -c | sort -rn | head -10 | \
        awk '{printf "- **%s**: %d TODOs\n", $2, $1}' >> "$OUTPUT_FILE"
else
    echo "- No TODOs found" >> "$OUTPUT_FILE"
fi

cat >> "$OUTPUT_FILE" << EOF

### TypeScript Files
EOF

# Top 10 TypeScript files with most TODOs
if [ -s "$TS_TODOS" ]; then
    echo "" >> "$OUTPUT_FILE"
    awk -F: '{print $1}' "$TS_TODOS" | sort | uniq -c | sort -rn | head -10 | \
        awk '{printf "- **%s**: %d TODOs\n", $2, $1}' >> "$OUTPUT_FILE"
else
    echo "- No TODOs found" >> "$OUTPUT_FILE"
fi

cat >> "$OUTPUT_FILE" << EOF

---

## Agent-Core Module Analysis

EOF

# Focus on agent-core module
AGENT_CORE_TODOS=$(grep "platform/java/agent-core" "$JAVA_TODOS" | wc -l | tr -d ' ')

cat >> "$OUTPUT_FILE" << EOF
**Agent-Core TODOs**: $AGENT_CORE_TODOS

### Critical Files

EOF

# Top agent-core files
grep "platform/java/agent-core" "$JAVA_TODOS" | awk -F: '{print $1}' | sort | uniq -c | sort -rn | head -10 | \
    awk '{printf "- **%s**: %d TODOs\n", $2, $1}' >> "$OUTPUT_FILE"

cat >> "$OUTPUT_FILE" << EOF

---

## TODO Categories

### Performance Optimization
EOF

grep -i "performance\|optimize\|slow" "$JAVA_TODOS" "$KOTLIN_TODOS" "$TS_TODOS" 2>/dev/null | \
    awk -F: '{printf "- `%s:%s` - %s\n", $1, $2, substr($0, index($0,$3))}' | head -20 >> "$OUTPUT_FILE" || echo "- None found" >> "$OUTPUT_FILE"

cat >> "$OUTPUT_FILE" << EOF

### Error Handling
EOF

grep -i "error\|exception\|handle" "$JAVA_TODOS" "$KOTLIN_TODOS" "$TS_TODOS" 2>/dev/null | \
    awk -F: '{printf "- `%s:%s` - %s\n", $1, $2, substr($0, index($0,$3))}' | head -20 >> "$OUTPUT_FILE" || echo "- None found" >> "$OUTPUT_FILE"

cat >> "$OUTPUT_FILE" << EOF

### Testing
EOF

grep -i "test\|coverage" "$JAVA_TODOS" "$KOTLIN_TODOS" "$TS_TODOS" 2>/dev/null | \
    awk -F: '{printf "- `%s:%s` - %s\n", $1, $2, substr($0, index($0,$3))}' | head -20 >> "$OUTPUT_FILE" || echo "- None found" >> "$OUTPUT_FILE"

cat >> "$OUTPUT_FILE" << EOF

### Documentation
EOF

grep -i "document\|javadoc\|comment" "$JAVA_TODOS" "$KOTLIN_TODOS" "$TS_TODOS" 2>/dev/null | \
    awk -F: '{printf "- `%s:%s` - %s\n", $1, $2, substr($0, index($0,$3))}' | head -20 >> "$OUTPUT_FILE" || echo "- None found" >> "$OUTPUT_FILE"

cat >> "$OUTPUT_FILE" << EOF

---

## Recommended Actions

### High Priority (Address in next sprint)
1. Review EventLogMemoryStore.java TODOs (snapshot mechanism, retention policy)
2. Complete DefaultAgentContext.java missing implementations
3. Address TemplateContextBuilder.java configuration TODOs

### Medium Priority (Address in next month)
1. Optimize query performance with secondary indexes
2. Implement configurable retention policies
3. Add comprehensive error handling

### Low Priority (Backlog)
1. Documentation improvements
2. Code style refinements
3. Nice-to-have features

---

## GitHub Issue Creation

To create GitHub issues for these TODOs:

\`\`\`bash
# Create issues for agent-core TODOs
./scripts/create-todo-issues.sh platform/java/agent-core
\`\`\`

---

## Cleanup Progress

Track TODO cleanup progress:
- **Baseline**: $TOTAL TODOs ($(date +%Y-%m-%d))
- **Target**: Reduce by 50% in 3 months
- **Next Review**: $(date -d "+1 month" +%Y-%m-%d)

---

**Report Location**: \`TODO_TRACKING_REPORT.md\`  
**Update Frequency**: Weekly (automated via CI)
EOF

echo "Report generated: $OUTPUT_FILE"

# Cleanup
rm -f "$JAVA_TODOS" "$KOTLIN_TODOS" "$TS_TODOS"

# Exit with error if too many TODOs
if [ $TOTAL -gt 500 ]; then
    echo ""
    echo "⚠️  WARNING: $TOTAL TODOs found (threshold: 500)"
    echo "Consider scheduling a TODO cleanup sprint"
fi
