#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Architecture Score Gate
#
# Runs lightweight static checks on the monorepo and produces a pass/fail
# score.  Designed for CI — exits 0 on pass, 1 on fail.
#
# Checks:
#   1. No deprecated shared:* module references in build files
#   2. No reflective (non-SPI) agent instantiation
#   3. Dependency flow: products → libs → contracts (no upward deps)
#   4. Module size limits (class count per module)
#   5. TypeScript dist artifacts not committed
#   6. New unapproved cross-product Java imports
#   7. Module count ceiling (prevent sprawl)
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SCORE=100
ISSUES=()

deduct() {
    local points=$1 msg=$2
    SCORE=$((SCORE - points))
    ISSUES+=("[-${points}] ${msg}")
}

echo "🏗  Architecture Score Gate"
echo "══════════════════════════"

# ── 1. Deprecated shared:* references ────────────────────────────────────────
SHARED_COUNT=$(git grep -cl ':shared:' -- '*.gradle.kts' 2>/dev/null | wc -l | tr -d ' ' || true)
SHARED_COUNT=${SHARED_COUNT:-0}
if [[ "$SHARED_COUNT" -gt 0 ]]; then
    deduct 20 "Found ${SHARED_COUNT} build file(s) referencing deprecated shared:* modules"
fi

# ── 2. Reflective agent instantiation ────────────────────────────────────────
REFLECT_COUNT=$(git grep -cP 'Class\.forName.*[Aa]gent|\.newInstance\(\).*[Aa]gent' -- '*.java' 2>/dev/null | awk -F: '{s+=$2}END{print s+0}' || true)
REFLECT_COUNT=${REFLECT_COUNT:-0}
if [[ "$REFLECT_COUNT" -gt 0 ]]; then
    deduct 15 "Found ${REFLECT_COUNT} reflective agent instantiation(s) — use SPI providers"
fi

# ── 3. Upward dependency violations ──────────────────────────────────────────
# products should not depend on other products' internal packages
CROSS_TOP=$(git grep -l 'project(":products:' -- 'products/*/build.gradle.kts' 2>/dev/null | wc -l | tr -d ' ' || true)
CROSS_TOP=${CROSS_TOP:-0}
if [[ "$CROSS_TOP" -gt 0 ]]; then
    deduct 0 "(info) ${CROSS_TOP} product build files reference sibling product modules"
fi

# ── 4. Module size check ─────────────────────────────────────────────────────
# Alert threshold: 400 files (chosen to warn above current max of 324 in scaffold/core)
# Fail threshold: 600 files (hard block for truly out-of-control god modules)
OVERSIZED=0
ALERT_THRESHOLD=400
FAIL_THRESHOLD=600
while IFS= read -r dir; do
    COUNT=$(find "$dir" -name '*.java' -not -path '*/test/*' -not -path '*/build/*' 2>/dev/null | wc -l | tr -d ' ')
    COUNT=${COUNT:-0}
    if [[ "$COUNT" -gt "$FAIL_THRESHOLD" ]]; then
        MODULE=$(echo "$dir" | sed 's|/src/main.*||')
        deduct 10 "Module ${MODULE} has ${COUNT} source files (HARD LIMIT: ${FAIL_THRESHOLD}) — mandatory split required"
        OVERSIZED=$((OVERSIZED + 1))
    elif [[ "$COUNT" -gt "$ALERT_THRESHOLD" ]]; then
        MODULE=$(echo "$dir" | sed 's|/src/main.*||')
        deduct 5 "Module ${MODULE} has ${COUNT} source files (alert threshold: ${ALERT_THRESHOLD}) — consider splitting"
        OVERSIZED=$((OVERSIZED + 1))
    fi
done < <(find products platform -type d -name "main" -path "*/src/main" 2>/dev/null)

# ── 5. Committed dist artifacts in TypeScript packages ───────────────────────
# Platform TypeScript packages must not commit built dist/ files.
COMMITTED_DIST=$(git ls-files 'platform/typescript/*/dist' 'platform/typescript/capabilities/*/dist' 2>/dev/null | wc -l | tr -d ' ' || true)
COMMITTED_DIST=${COMMITTED_DIST:-0}
if [[ "$COMMITTED_DIST" -gt 0 ]]; then
    deduct 10 "Found ${COMMITTED_DIST} committed dist/ artifact(s) in platform/typescript — add to .gitignore and un-track. See docs/platform-libraries/LIBRARY_canvas.md"
fi

# ── 6. New unapproved cross-product Java imports ──────────────────────────────
# The cross-product deps script documents approved deps; any new ones need approval.
if bash scripts/check-cross-product-deps.sh 2>/dev/null | grep -q "FAIL"; then
    UNAPPROVED_COUNT=$(bash scripts/check-cross-product-deps.sh 2>/dev/null | grep -c "product '.*' depends on" || true)
    UNAPPROVED_COUNT=${UNAPPROVED_COUNT:-1}
    deduct 15 "Found ${UNAPPROVED_COUNT} NEW unapproved cross-product dependency/ies — get Architecture Board approval first"
fi

# ── 7. Module count ceiling ─────────────────────────────────────────────────
# Current baseline: 142 modules (after Phase 1 consolidation 2026-01).
# Ceiling: 145 — any new platform modules require Arch Board approval (GOV-2).
MODULE_COUNT=$(grep -c '^include(' settings.gradle.kts 2>/dev/null || echo 0)
MODULE_CEILING=145
if [[ "$MODULE_COUNT" -gt "$MODULE_CEILING" ]]; then
    deduct 15 "Module count ${MODULE_COUNT} exceeds ceiling ${MODULE_CEILING} — new modules need Architecture Board approval (see docs/MODULE_ADMISSION_CHECKLIST.md)"
fi

# ── Report ───────────────────────────────────────────────────────────────────
echo ""
if [[ ${#ISSUES[@]} -eq 0 ]]; then
    echo "✅  All checks passed"
else
    for issue in "${ISSUES[@]}"; do
        echo "  $issue"
    done
fi

echo ""
echo "Score: ${SCORE}/100"
echo ""

THRESHOLD=80
if [[ "$SCORE" -lt "$THRESHOLD" ]]; then
    echo "❌  FAIL — Score ${SCORE} is below threshold ${THRESHOLD}"
    exit 1
else
    echo "✅  PASS — Score ${SCORE} meets threshold ${THRESHOLD}"
    exit 0
fi
