#!/usr/bin/env bash

###############################################################################
# YAPPC TODO Scanner
#
# Scans Java and TypeScript files for TODO/FIXME/XXX/HACK markers.
# Generates categorized reports and optional CI enforcement.
#
# Usage:
#   ./scripts/scan-todos.sh
#   ./scripts/scan-todos.sh --ci
#   ./scripts/scan-todos.sh --ci --max 100
###############################################################################

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAPPC_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DOCS_DIR="$YAPPC_ROOT/docs"
REPORT_DIR="$DOCS_DIR/todo-reports"
SUMMARY_FILE="$REPORT_DIR/TODO_SCAN_SUMMARY.md"
REDUCTION_REPORT_FILE="$DOCS_DIR/TODO_REDUCTION_REPORT.md"

CI_MODE=false
MAX_TODOS=100

while [[ $# -gt 0 ]]; do
    case "$1" in
        --ci)
            CI_MODE=true
            shift
            ;;
        --max)
            if [[ $# -lt 2 ]]; then
                echo "error: --max requires a numeric value" >&2
                exit 2
            fi
            MAX_TODOS="$2"
            shift 2
            ;;
        *)
            echo "error: unknown argument '$1'" >&2
            exit 2
            ;;
    esac
done

if ! [[ "$MAX_TODOS" =~ ^[0-9]+$ ]]; then
    echo "error: --max must be a non-negative integer" >&2
    exit 2
fi

echo "Scanning TODO markers in YAPPC..."
mkdir -p "$REPORT_DIR"

JAVA_FILE="$REPORT_DIR/todos-java.txt"
TS_FILE="$REPORT_DIR/todos-typescript.txt"
CRITICAL_FILE="$REPORT_DIR/todos-critical.txt"
PERF_FILE="$REPORT_DIR/todos-performance.txt"
QUALITY_FILE="$REPORT_DIR/todos-quality.txt"

TODO_PATTERN='TODO|FIXME|XXX|HACK'

# Java TODO scan
if [[ -d "$YAPPC_ROOT/core" ]]; then
    find "$YAPPC_ROOT/core" -type f -name "*.java" -exec grep -EnH "$TODO_PATTERN" {} + > "$JAVA_FILE" || true
else
    : > "$JAVA_FILE"
fi

# TypeScript TODO scan
if [[ -d "$YAPPC_ROOT/frontend" ]]; then
    find "$YAPPC_ROOT/frontend" -type f \( -name "*.ts" -o -name "*.tsx" \) -exec grep -EnH "$TODO_PATTERN" {} + > "$TS_FILE" || true
else
    : > "$TS_FILE"
fi

JAVA_COUNT=$(wc -l < "$JAVA_FILE" | tr -d ' ')
TS_COUNT=$(wc -l < "$TS_FILE" | tr -d ' ')
TOTAL=$((JAVA_COUNT + TS_COUNT))

grep -Ei "FIXME|BUG|CRITICAL|SECURITY|VULNERABILITY|BROKEN|URGENT" "$JAVA_FILE" "$TS_FILE" > "$CRITICAL_FILE" || true
grep -Ei "PERF|OPTIMIZE|SLOW|PERFORMANCE" "$JAVA_FILE" "$TS_FILE" > "$PERF_FILE" || true
grep -Ei "REFACTOR|CLEANUP|IMPROVE|DEBT|SMELL" "$JAVA_FILE" "$TS_FILE" > "$QUALITY_FILE" || true

CRITICAL_COUNT=$(wc -l < "$CRITICAL_FILE" | tr -d ' ')
PERF_COUNT=$(wc -l < "$PERF_FILE" | tr -d ' ')
QUALITY_COUNT=$(wc -l < "$QUALITY_FILE" | tr -d ' ')
UNCATEGORIZED=$((TOTAL - CRITICAL_COUNT - PERF_COUNT - QUALITY_COUNT))

if [[ "$TOTAL" -gt 0 ]]; then
    REDUCTION_REQUIRED=$((TOTAL - MAX_TODOS))
    if [[ "$REDUCTION_REQUIRED" -lt 0 ]]; then
        REDUCTION_REQUIRED=0
    fi
    REDUCTION_PERCENT=$((REDUCTION_REQUIRED * 100 / TOTAL))
    JAVA_PERCENT=$((JAVA_COUNT * 100 / TOTAL))
    TS_PERCENT=$((TS_COUNT * 100 / TOTAL))
else
    REDUCTION_REQUIRED=0
    REDUCTION_PERCENT=0
    JAVA_PERCENT=0
    TS_PERCENT=0
fi

cat > "$SUMMARY_FILE" << EOF
# TODO Scan Summary

**Date:** $(date +"%Y-%m-%d %H:%M:%S")
**Total TODOs:** $TOTAL
**Target:** <= $MAX_TODOS
**Reduction Required:** $REDUCTION_REQUIRED TODOs ($REDUCTION_PERCENT%)

---

## Breakdown by Language

| Language | Count | Percentage |
|----------|-------|------------|
| Java | $JAVA_COUNT | $JAVA_PERCENT% |
| TypeScript | $TS_COUNT | $TS_PERCENT% |
| **Total** | **$TOTAL** | **100%** |

---

## Breakdown by Category

| Category | Count | Priority |
|----------|-------|----------|
| Critical (FIXME, BUG, SECURITY) | $CRITICAL_COUNT | P0 - Immediate |
| Performance (OPTIMIZE, SLOW) | $PERF_COUNT | P1 - High |
| Quality (REFACTOR, CLEANUP) | $QUALITY_COUNT | P2 - Medium |
| Uncategorized | $UNCATEGORIZED | Review needed |

---

## Files Generated

- todos-java.txt
- todos-typescript.txt
- todos-critical.txt
- todos-performance.txt
- todos-quality.txt
EOF

cat > "$REDUCTION_REPORT_FILE" << EOF
# TODO Reduction Report

**Date:** $(date +"%Y-%m-%d")
**Current Count:** $TOTAL
**Target:** <= $MAX_TODOS
**Reduction Needed:** $REDUCTION_REQUIRED

## TODO Categories

- Critical: $CRITICAL_COUNT
- Performance: $PERF_COUNT
- Quality: $QUALITY_COUNT
- Uncategorized: $UNCATEGORIZED

## Source Reports

- docs/todo-reports/TODO_SCAN_SUMMARY.md
- docs/todo-reports/todos-critical.txt
- docs/todo-reports/todos-performance.txt
- docs/todo-reports/todos-quality.txt

## Action Plan

1. Convert critical TODOs to tracked issues.
2. Remove obsolete and vague TODOs.
3. Keep intentional TODOs with clear owner/context.
4. Re-run scan in CI to prevent count regressions.
EOF

echo "TODO Summary"
echo "  Java TODOs: $JAVA_COUNT"
echo "  TypeScript TODOs: $TS_COUNT"
echo "  Total: $TOTAL"
echo "  Target: <= $MAX_TODOS"
echo "  Summary report: $SUMMARY_FILE"
echo "  Reduction report: $REDUCTION_REPORT_FILE"

if [[ "$CI_MODE" == true && "$TOTAL" -gt "$MAX_TODOS" ]]; then
    echo "CI FAIL: TODO count $TOTAL exceeds max $MAX_TODOS" >&2
    exit 1
fi

echo "TODO scan complete."
