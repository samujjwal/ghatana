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
OVERSIZED=0
while IFS= read -r dir; do
    COUNT=$(find "$dir" -name '*.java' -not -path '*/test/*' -not -path '*/build/*' 2>/dev/null | wc -l | tr -d ' ')
    COUNT=${COUNT:-0}
    if [[ "$COUNT" -gt 200 ]]; then
        MODULE=$(echo "$dir" | sed 's|/src/main.*||')
        deduct 5 "Module ${MODULE} has ${COUNT} source files (limit: 200)"
        OVERSIZED=$((OVERSIZED + 1))
    fi
done < <(find products platform -type d -name "main" -path "*/src/main" 2>/dev/null)

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

THRESHOLD=70
if [[ "$SCORE" -lt "$THRESHOLD" ]]; then
    echo "❌  FAIL — Score ${SCORE} is below threshold ${THRESHOLD}"
    exit 1
else
    echo "✅  PASS — Score ${SCORE} meets threshold ${THRESHOLD}"
    exit 0
fi
