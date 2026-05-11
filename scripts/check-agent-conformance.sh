#!/usr/bin/env bash
# Agent Conformance Audit Script
# Validates that all products follow the canonical GAA substrate.
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

# --- 3. No compatibility/deprecation markers in agent contracts ---
echo "--- 3. No compatibility/deprecation markers in agent contracts ---"
DEPRECATED_COUNT=0
for file in $(git grep -l -E '@Deprecated|forRemoval = true|DETERMINISTIC_LEGACY|PROBABILISTIC_LEGACY|deprecated legacy|compatibility adapter|backward-compat aliases|Backward-compat aliases' -- \
    'platform/java/agent-core/src/main/java/**/*.java' \
    'products/*/config/agents/**/*.yaml' \
    'products/*/config/agents/*.yaml' \
    'docs/agent-system/**/*.md' \
    'platform/agent-catalog/**/*.ts' 2>/dev/null || true); do
    echo "  FAIL: $file"
    DEPRECATED_COUNT=$((DEPRECATED_COUNT + 1))
done
if [[ "$DEPRECATED_COUNT" -eq 0 ]]; then
    echo "  OK: No agent compatibility/deprecation markers"
fi
echo ""

# --- 4. Canonical agent type usage ---
echo "--- 4. Canonical agent type usage ---"
TYPE_ALIAS_COUNT=0
for file in $(git grep -l -E 'AgentType\.LLM|(^|[[:space:]])agentType:[[:space:]]*(llm|LLM|llm-based|rule-based|RULE_BASED|policy|POLICY|pattern|PATTERN|ml-based)|(^|[[:space:]])type:[[:space:]]*(llm|LLM|llm-based|rule-based|RULE_BASED|policy|POLICY|pattern|PATTERN|ml-based)' -- \
    'products/*/config/agents/**/*.yaml' \
    'products/*/config/agents/*.yaml' \
    'platform/agent-catalog/**/*.yaml' \
    'platform/java/**/*.java' 2>/dev/null || true); do
    echo "  FAIL: $file"
    TYPE_ALIAS_COUNT=$((TYPE_ALIAS_COUNT + 1))
done
if [[ "$TYPE_ALIAS_COUNT" -eq 0 ]]; then
    echo "  OK: No noncanonical agent type aliases"
fi
echo ""

# --- 5. No product-local frontend taxonomies or ts-nocheck on agent contracts ---
echo "--- 5. Frontend agent contract canonicality ---"
FRONTEND_COUNT=0
for file in $(git grep -l -E '@ts-nocheck|export type AgentType[[:space:]]*=' -- \
    'products/*/frontend/**/*.ts' \
    'products/*/frontend/**/*.tsx' 2>/dev/null || true); do
    if [[ "$file" == *"/agents/"* || "$file" == *"AgentContract"* ]]; then
        echo "  FAIL: $file"
        FRONTEND_COUNT=$((FRONTEND_COUNT + 1))
    fi
done
if [[ "$FRONTEND_COUNT" -eq 0 ]]; then
    echo "  OK: Frontend agent contracts use canonical platform type + product role"
fi
echo ""

# --- 6. Reflective class loading anti-pattern ---
echo "--- 6. Reflective Class Loading ---"
REFLECTIVE_COUNT=0
for match in $(git grep -l 'Class.forName.*aep\|Class.forName.*agent\|Class.forName.*registry' -- '*.java' 2>/dev/null || true); do
    echo "  FAIL: $match"
    REFLECTIVE_COUNT=$((REFLECTIVE_COUNT + 1))
done
if [[ "$REFLECTIVE_COUNT" -eq 0 ]]; then
    echo "  OK: No reflective class loading for agent/AEP classes"
fi
echo ""

# --- 7. Summary ---
echo "========================================"
echo " Summary"
echo "========================================"
echo "Compatibility markers:   $DEPRECATED_COUNT"
echo "Agent type aliases:      $TYPE_ALIAS_COUNT"
echo "Frontend contract drift: $FRONTEND_COUNT"
echo "Reflective loading:      $REFLECTIVE_COUNT"
if [[ "$DEPRECATED_COUNT" -gt 0 || "$TYPE_ALIAS_COUNT" -gt 0 || "$FRONTEND_COUNT" -gt 0 || "$REFLECTIVE_COUNT" -gt 0 ]]; then
    echo "RESULT: FAIL"
    EXIT_CODE=1
else
    echo "RESULT: PASS"
fi

exit "$EXIT_CODE"
