#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Platform Module Admission Gate
#
# Usage: ./scripts/check-new-platform-module.sh <module-gradle-path>
#   e.g. ./scripts/check-new-platform-module.sh platform/java/my-new-module
#
# Checks a candidate new platform/java module against the admission criteria
# defined in docs/MODULE_ADMISSION_CHECKLIST.md.
#
# Exits 0 if all checks pass; 1 if any check fails.
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ $# -lt 1 ]]; then
    echo "Usage: $0 <module-path>"
    echo "  e.g. $0 platform/java/my-new-module"
    exit 1
fi

MODULE_PATH="${1%/}"   # strip trailing slash
PASS=true

fail() { echo "  ❌ FAIL: $1"; PASS=false; }
warn() { echo "  ⚠️  WARN: $1"; }
ok()   { echo "  ✅ OK:   $1"; }

echo ""
echo "🔎  Module Admission Gate"
echo "═══════════════════════════════════════════"
echo "Module: ${MODULE_PATH}"
echo ""

# ── 1. Directory must exist ───────────────────────────────────────────────────
if [[ ! -d "${MODULE_PATH}" ]]; then
    fail "Directory '${MODULE_PATH}' does not exist"
    echo ""
    echo "Result: ❌ FAIL (module directory missing)"
    exit 1
fi
ok "Module directory exists"

# ── 2. Must have build.gradle.kts ─────────────────────────────────────────────
if [[ ! -f "${MODULE_PATH}/build.gradle.kts" ]]; then
    fail "Missing build.gradle.kts"
else
    ok "build.gradle.kts present"
fi

# ── 3. Must be registered in settings.gradle.kts ─────────────────────────────
GRADLE_PATH=":$(echo "$MODULE_PATH" | tr '/' ':')"
if ! grep -q "include(\"${GRADLE_PATH}\")" settings.gradle.kts 2>/dev/null; then
    fail "Not registered in settings.gradle.kts — add include(\"${GRADLE_PATH}\")"
else
    ok "Registered in settings.gradle.kts"
fi

# ── 4. Must be under platform/java (or otherwise justified) ──────────────────
if [[ "$MODULE_PATH" == platform/java/* ]]; then
    ok "Located under platform/java/"
else
    warn "Not under platform/java/ — ensure placement is intentional (products/, libs/, etc.)"
fi

# ── 5. Source must be present ─────────────────────────────────────────────────
JAVA_COUNT=$(find "${MODULE_PATH}/src/main" -name "*.java" 2>/dev/null | wc -l | tr -d ' ')
JAVA_COUNT=${JAVA_COUNT:-0}
if [[ "$JAVA_COUNT" -eq 0 ]]; then
    fail "No Java source files found in ${MODULE_PATH}/src/main"
else
    ok "${JAVA_COUNT} Java source file(s) found"
fi

# ── 6. Size check: platform modules must stay lean ────────────────────────────
SIZE_CEILING=150
if [[ "$JAVA_COUNT" -gt "$SIZE_CEILING" ]]; then
    fail "Module has ${JAVA_COUNT} source files — exceeds platform module ceiling of ${SIZE_CEILING}. Consider splitting."
else
    ok "Module size (${JAVA_COUNT} files) within ceiling (${SIZE_CEILING})"
fi

# ── 7. Must not create circular dependencies ─────────────────────────────────
BUILD_FILE="${MODULE_PATH}/build.gradle.kts"
if [[ -f "$BUILD_FILE" ]]; then
    # Warn if it depends on a sibling platform module that depends back (simple heuristic)
    MODULE_NAME=$(basename "$MODULE_PATH")
    REVERSE_DEPS=$(grep -r "\"${GRADLE_PATH}\"" platform --include="*.gradle.kts" 2>/dev/null | wc -l | tr -d ' ')
    REVERSE_DEPS=${REVERSE_DEPS:-0}
    if [[ "$REVERSE_DEPS" -gt 0 ]]; then
        warn "Detected ${REVERSE_DEPS} existing module(s) already depend on ${GRADLE_PATH} — verify no circular deps after adding yours"
    else
        ok "No existing modules depend on this path (potential circular deps: none)"
    fi
fi

# ── 8. Must have at least one external consumer (not orphan) ──────────────────
CONSUMER_COUNT=$(grep -r "\"${GRADLE_PATH}\"" --include="*.gradle.kts" . 2>/dev/null | grep -v "settings.gradle.kts" | grep -v "${MODULE_PATH}/build.gradle.kts" | wc -l | tr -d ' ')
CONSUMER_COUNT=${CONSUMER_COUNT:-0}
if [[ "$CONSUMER_COUNT" -eq 0 ]]; then
    warn "No consumers found yet — ensure this module will have at least one consumer before merging"
else
    ok "${CONSUMER_COUNT} consumer(s) found"
fi

# ── 9. Module count ceiling check ─────────────────────────────────────────────
CURRENT_COUNT=$(grep -c '^include(' settings.gradle.kts 2>/dev/null || echo 0)
MODULE_CEILING=145
echo ""
echo "Module count: ${CURRENT_COUNT}/${MODULE_CEILING}"
if [[ "$CURRENT_COUNT" -gt "$MODULE_CEILING" ]]; then
    fail "Total module count (${CURRENT_COUNT}) exceeds ceiling (${MODULE_CEILING}) — Architecture Board approval required, and an existing module must be retired first"
else
    ok "Module count (${CURRENT_COUNT}) is within ceiling (${MODULE_CEILING})"
fi

# ── 10. JavaDoc @doc tags present ─────────────────────────────────────────────
DOC_TAG_COUNT=$(find "${MODULE_PATH}/src/main" -name "*.java" 2>/dev/null | xargs grep -l "@doc.type" 2>/dev/null | wc -l | tr -d ' ')
DOC_TAG_COUNT=${DOC_TAG_COUNT:-0}
if [[ "$JAVA_COUNT" -gt 0 && "$DOC_TAG_COUNT" -eq 0 ]]; then
    fail "No @doc.type tags found in source files — all public classes require @doc.* tags per copilot-instructions.md"
else
    ok "${DOC_TAG_COUNT} source file(s) with @doc tags"
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════"
if [[ "$PASS" == "true" ]]; then
    echo "✅  PASS — Module '${MODULE_PATH}' meets admission criteria"
    exit 0
else
    echo "❌  FAIL — Fix issues above before merging"
    echo "     See docs/MODULE_ADMISSION_CHECKLIST.md for full process"
    exit 1
fi
