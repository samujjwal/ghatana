#!/usr/bin/env bash
# Agent Conformance Audit Script
# Validates that all products follow the canonical agent layout and deprecation rules.
# Part of Phase 7 (P7-1): Legacy cleanup conformance audit
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EXIT_CODE=0

echo "========================================"
echo " Agent Conformance Audit"
echo "========================================"
echo ""

# --- 1. Check canonical agent-catalog.yaml presence ---
echo "--- 1. Canonical agent-catalog.yaml ---"
PRODUCTS=("yappc" "data-cloud" "software-org" "virtual-org" "app-platform" "finance" "tutorputor")
for product in "${PRODUCTS[@]}"; do
    CATALOG_PATH="$REPO_ROOT/products/$product/config/agents/agent-catalog.yaml"
    if [[ -f "$CATALOG_PATH" ]]; then
        echo "  OK: $product has agent-catalog.yaml"
    else
        echo "  WARN: $product missing agent-catalog.yaml"
    fi
done
echo ""

# --- 2. Check AgentLogicProvider SPI registration (uses git ls-files for speed) ---
echo "--- 2. AgentLogicProvider SPI Registration ---"
SPI_FILE="com.ghatana.agent.spi.AgentLogicProvider"
cd "$REPO_ROOT"
for spi in $(git ls-files "*/META-INF/services/$SPI_FILE" 2>/dev/null); do
    echo "  OK: $spi"
done
echo ""

# --- 3. Deprecated classes (uses git grep for speed) ---
echo "--- 3. Deprecated Classes (forRemoval=true) ---"
DEPRECATED_COUNT=0
for file in $(git grep -l 'forRemoval = true' -- '*.java' 2>/dev/null || true); do
    class_name=$(basename "$file" .java)
    echo "  DEPRECATED: $class_name [$file]"
    DEPRECATED_COUNT=$((DEPRECATED_COUNT + 1))
done
echo "  Total: $DEPRECATED_COUNT"
echo ""

# --- 4. Reflective class loading anti-pattern ---
echo "--- 4. Reflective Class Loading ---"
REFLECTIVE_COUNT=0
for match in $(git grep -l 'Class.forName.*aep\|Class.forName.*agent\|Class.forName.*registry' -- '*.java' 2>/dev/null || true); do
    echo "  FAIL: $match"
    REFLECTIVE_COUNT=$((REFLECTIVE_COUNT + 1))
done
if [[ "$REFLECTIVE_COUNT" -eq 0 ]]; then
    echo "  OK: No reflective class loading for agent/AEP classes"
fi
echo ""

# --- 5. Summary ---
echo "========================================"
echo " Summary"
echo "========================================"
echo "Deprecated (forRemoval): $DEPRECATED_COUNT"
echo "Reflective loading:      $REFLECTIVE_COUNT"
if [[ "$REFLECTIVE_COUNT" -gt 0 ]]; then
    echo "RESULT: FAIL"
    EXIT_CODE=1
else
    echo "RESULT: PASS"
fi

exit "$EXIT_CODE"
