#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Quarterly Boundary Audit Script
#
# Usage: ./scripts/run-quarterly-audit.sh [--report-only]
#
# Produces a full audit report to stdout and to docs/audits/quarterly-YYYY-QN.md
# Run this once per quarter (January, April, July, October).
#
# Checks:
#   1. Module count trend vs baseline
#   2. Module size distribution (god-module detection)
#   3. Cross-product dependency count
#   4. Platform→product boundary violations
#   5. Deprecated module staleness
#   6. Architecture score
#   7. TypeScript boundary health
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

REPORT_ONLY=false
[[ "${1:-}" == "--report-only" ]] && REPORT_ONLY=true

QUARTER=$(date '+%Y-Q%q' 2>/dev/null || python3 -c "import datetime; d=datetime.date.today(); q=(d.month-1)//3+1; print(f'{d.year}-Q{q}')")
REPORT_FILE="docs/audits/quarterly-${QUARTER}.md"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

declare -a FINDINGS=()
declare -a ACTIONS=()

finding() { FINDINGS+=("- $1"); }
action()  { ACTIONS+=("- [ ] $1"); }

echo ""
echo "══════════════════════════════════════════════════════════════"
echo "  Ghatana Monorepo — Quarterly Boundary Audit"
echo "  Quarter: ${QUARTER}    Generated: ${TIMESTAMP}"
echo "══════════════════════════════════════════════════════════════"
echo ""

# ── 1. Module Count ──────────────────────────────────────────────────────────
BASELINE=142
CEILING=145
MODULE_COUNT=$(grep -c '^include(' settings.gradle.kts 2>/dev/null || echo 0)
PLATFORM_JAVA_COUNT=$(grep -c '^include(":platform:java:' settings.gradle.kts 2>/dev/null || echo 0)
PRODUCT_COUNT=$(grep -c '^include(":products:' settings.gradle.kts 2>/dev/null || echo 0)

echo "── 1. Module Count ─────────────────────────────────────────────"
echo "   Total:              ${MODULE_COUNT} (baseline: ${BASELINE}, ceiling: ${CEILING})"
echo "   platform/java:      ${PLATFORM_JAVA_COUNT}"
echo "   products/:          ${PRODUCT_COUNT}"
echo ""

if [[ "$MODULE_COUNT" -gt "$CEILING" ]]; then
    finding "⚠️  Module count ${MODULE_COUNT} exceeds ceiling ${CEILING}"
    action  "Architecture Board review: which new modules can be consolidated?"
elif [[ "$MODULE_COUNT" -gt "$BASELINE" ]]; then
    DELTA=$((MODULE_COUNT - BASELINE))
    finding "ℹ️  Module count grew by ${DELTA} since baseline (${BASELINE} → ${MODULE_COUNT})"
    action  "Review ${DELTA} new modules: do they meet the MODULE_ADMISSION_CHECKLIST?"
else
    finding "✅ Module count ${MODULE_COUNT} is at or below baseline — healthy"
fi

# ── 2. God-Module Detection ──────────────────────────────────────────────────
echo "── 2. God-Module Detection (>250 source files) ─────────────────"
GOD_COUNT=0
while IFS= read -r dir; do
    COUNT=$(find "$dir" -name '*.java' -not -path '*/test/*' -not -path '*/build/*' 2>/dev/null | wc -l | tr -d ' ')
    COUNT=${COUNT:-0}
    if [[ "$COUNT" -gt 250 ]]; then
        MODULE=$(echo "$dir" | sed 's|/src.*||;s|.*/platform/java/|platform/java/|;s|.*/products/|products/|')
        echo "   [!] ${MODULE}: ${COUNT} files"
        finding "⚠️  God-module detected: ${MODULE} (${COUNT} source files) — split recommended"
        action  "Split ${MODULE} into focused sub-modules per ADR process"
        GOD_COUNT=$((GOD_COUNT + 1))
    fi
done < <(find platform products -type d -name "main" -path "*/src/main" 2>/dev/null)
if [[ "$GOD_COUNT" -eq 0 ]]; then
    echo "   ✅ No god-modules detected"
    finding "✅ No god-modules (all modules < 250 source files)"
fi
echo ""

# ── 3. Cross-Product Dependency Count ───────────────────────────────────────
echo "── 3. Cross-Product Dependency Health ─────────────────────────"
if [[ -x scripts/check-cross-product-deps.sh ]]; then
    CROSS_OUTPUT=$(bash scripts/check-cross-product-deps.sh 2>&1 || true)
    if echo "$CROSS_OUTPUT" | grep -q "FAIL"; then
        UNAPPROVED=$(echo "$CROSS_OUTPUT" | grep -c "UNAPPROVED" || true)
        echo "   [!] ${UNAPPROVED} unapproved cross-product deps found"
        finding "⚠️  ${UNAPPROVED} unapproved cross-product dependencies"
        action  "Review and either approve or remove the unapproved deps"
    else
        echo "   ✅ All cross-product deps are approved"
        finding "✅ Cross-product dependency check passed"
    fi
else
    echo "   ⚠️  scripts/check-cross-product-deps.sh not found — skipping"
fi
echo ""

# ── 4. Platform → Product Boundary (Java imports) ───────────────────────────
echo "── 4. Platform→Product Boundary Violations ─────────────────────"
BOUNDARY_VIOLATIONS=$(grep -r \
    "import com.ghatana.yappc\.\|import com.ghatana.aep\.\|import com.ghatana.datacloud\.\|import com.ghatana.virtualorg\.\|import com.ghatana.finance\." \
    platform/java --include="*.java" 2>/dev/null | grep -v "src/test" | wc -l | tr -d ' ')
BOUNDARY_VIOLATIONS=${BOUNDARY_VIOLATIONS:-0}
if [[ "$BOUNDARY_VIOLATIONS" -gt 0 ]]; then
    echo "   [!] ${BOUNDARY_VIOLATIONS} platform→product import(s) detected"
    finding "🚨 CRITICAL: ${BOUNDARY_VIOLATIONS} platform→product boundary violations (platform code importing product code)"
    action  "Fix immediately: platform must never import from products. See ArchUnit PlatformBoundaryRulesTest."
else
    echo "   ✅ No platform→product boundary violations"
    finding "✅ Platform→product boundary is clean"
fi
echo ""

# ── 5. Stale Deprecation Warnings ──────────────────────────────────────────
echo "── 5. Deprecated Module Staleness ─────────────────────────────"
STALE_COUNT=0
while IFS= read -r line; do
    # Extracts "DEPRECATED(YYYY-MM)" pattern
    DEPRECATED_MONTH=$(echo "$line" | grep -oP 'DEPRECATED\(\K[0-9]{4}-[0-9]{2}')
    if [[ -n "$DEPRECATED_MONTH" ]]; then
        DEPRECATED_EPOCH=$(date -d "${DEPRECATED_MONTH}-01" '+%s' 2>/dev/null || \
                            python3 -c "import datetime; print(int(datetime.datetime(${DEPRECATED_MONTH%-*}, int('${DEPRECATED_MONTH#*-}'), 1).timestamp()))" 2>/dev/null || echo 0)
        NOW_EPOCH=$(date '+%s')
        AGE_DAYS=$(( (NOW_EPOCH - DEPRECATED_EPOCH) / 86400 ))
        if [[ "$AGE_DAYS" -gt 180 ]]; then
            MODULE=$(echo "$line" | grep -oP '":platform:[^"]+"|":products:[^"]+"')
            echo "   [!] ${MODULE} deprecated ${DEPRECATED_MONTH} (${AGE_DAYS} days ago)"
            finding "⚠️  Stale deprecation: ${MODULE} has been DEPRECATED for ${AGE_DAYS} days without moving to SUNSET"
            action  "Move ${MODULE} to SUNSET stage or extend the deprecation window (see DEPRECATION_POLICY.md)"
            STALE_COUNT=$((STALE_COUNT + 1))
        fi
    fi
done < <(grep 'DEPRECATED(' settings.gradle.kts 2>/dev/null || true)
if [[ "$STALE_COUNT" -eq 0 ]]; then
    echo "   ✅ No stale deprecations"
    finding "✅ No modules in stale deprecation state"
fi
echo ""

# ── 6. Architecture Score ────────────────────────────────────────────────────
echo "── 6. Architecture Score Gate ──────────────────────────────────"
if [[ -x scripts/architecture-score-gate.sh ]]; then
    SCORE_OUTPUT=$(bash scripts/architecture-score-gate.sh 2>&1 || true)
    SCORE=$(echo "$SCORE_OUTPUT" | grep -oP 'Score: \K[0-9]+' | head -1 || echo "?")
    echo "   Score: ${SCORE}/100"
    finding "Architecture score this quarter: ${SCORE}/100"
    if [[ "$SCORE" != "?" && "$SCORE" -lt 80 ]]; then
        action "Architecture score ${SCORE} is below 80 — investigate and fix failing checks"
    fi
else
    echo "   ⚠️  scripts/architecture-score-gate.sh not found — skipping"
fi
echo ""

# ── 7. TypeScript Boundary Health ───────────────────────────────────────────
echo "── 7. TypeScript Boundary Health ───────────────────────────────"
TS_COMMITTED_DIST=$(git ls-files 'platform/typescript/*/dist' 'platform/typescript/capabilities/*/dist' 2>/dev/null | wc -l | tr -d ' ' || echo 0)
if [[ "$TS_COMMITTED_DIST" -gt 0 ]]; then
    echo "   [!] ${TS_COMMITTED_DIST} committed dist/ artifacts"
    finding "⚠️  ${TS_COMMITTED_DIST} TypeScript dist/ artifacts committed to repo"
    action  "Run 'git rm -r --cached platform/typescript/*/dist' and add to .gitignore"
else
    echo "   ✅ No committed dist/ TypeScript artifacts"
    finding "✅ TypeScript dist artifacts are not committed"
fi
echo ""

# ── Write Report ─────────────────────────────────────────────────────────────
REPORT_CONTENT="# Quarterly Boundary Audit — ${QUARTER}

**Generated**: ${TIMESTAMP}  
**Run by**: \`scripts/run-quarterly-audit.sh\`

---

## Findings

$(printf '%s\n' "${FINDINGS[@]}")

## Action Items

$(printf '%s\n' "${ACTIONS[@]}")

---

## Raw Metrics

| Metric | Value |
|--------|-------|
| Total modules | ${MODULE_COUNT} (ceiling: ${CEILING}) |
| Platform Java modules | ${PLATFORM_JAVA_COUNT} |
| Product modules | ${PRODUCT_COUNT} |
| Platform→product violations | ${BOUNDARY_VIOLATIONS} |
| Architecture score | ${SCORE:-?}/100 |
| TS dist artifacts committed | ${TS_COMMITTED_DIST} |

---

*Next audit due: next quarter*
"

if [[ "$REPORT_ONLY" == "false" ]]; then
    mkdir -p docs/audits
    printf '%s\n' "$REPORT_CONTENT" > "$REPORT_FILE"
    echo "══════════════════════════════════════════════════════════════"
    echo "  Report written to: ${REPORT_FILE}"
fi

echo "══════════════════════════════════════════════════════════════"
echo ""
echo "Findings:"
printf '%s\n' "${FINDINGS[@]}"
echo ""
echo "Actions:"
printf '%s\n' "${ACTIONS[@]}"
echo ""
