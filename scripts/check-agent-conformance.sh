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
while IFS= read -r file; do
    echo "  FAIL: $file"
    TYPE_ALIAS_COUNT=$((TYPE_ALIAS_COUNT + 1))
done < <(git grep -l -E 'AgentType\.LLM|(^|[[:space:]])agentType:[[:space:]]*(llm|LLM|llm-based|rule-based|RULE_BASED|policy|POLICY|pattern|PATTERN|ml-based)|(^|[[:space:]])type:[[:space:]]*(llm|LLM|llm-based|rule-based|RULE_BASED|policy|POLICY|pattern|PATTERN|ml-based)' -- \
    'products/*/config/agents/definitions/**/*.yaml' \
    'products/*/config/agents/definitions/*.yaml' \
    'platform/agent-catalog/**/*.yaml' \
    'platform/java/**/*.java' 2>/dev/null | grep -v '/evaluation-packs/')
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

# --- 7. Governed dispatch trace sequence conformance ---
echo "--- 7. Governed Dispatch Trace Sequence ---"
TRACE_SEQ_COUNT=0
MASTERY_CONFORMANCE_COUNT=0

TRACE_ENUM_FILE="products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/audit/TraceEventType.java"
DISPATCHER_FILE="products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java"
DISPATCHER_TEST_FILE="products/data-cloud/planes/action/agent-runtime/src/test/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcherTest.java"

for required in DISPATCH_REQUESTED RELEASE_CHECKED VERSION_CONTEXT_RESOLVED MEMORY_RETRIEVAL_STARTED MEMORY_RETRIEVAL_COMPLETED POLICY_EVALUATED DISPATCH_ALLOWED TURN_STARTED TURN_COMPLETED; do
    if ! grep -q "$required" "$TRACE_ENUM_FILE"; then
        echo "  FAIL: Missing trace enum $required in $TRACE_ENUM_FILE"
        TRACE_SEQ_COUNT=$((TRACE_SEQ_COUNT + 1))
    fi
done

for required in DISPATCH_REQUESTED RELEASE_CHECKED MEMORY_RETRIEVAL_STARTED MEMORY_RETRIEVAL_COMPLETED DISPATCH_ALLOWED; do
    if ! grep -q "$required" "$DISPATCHER_FILE"; then
        echo "  FAIL: Governed dispatcher does not emit $required"
        TRACE_SEQ_COUNT=$((TRACE_SEQ_COUNT + 1))
    fi
done

if ! grep -q "dispatch sequence includes DISPATCH_REQUESTED and RELEASE_CHECKED" "$DISPATCHER_TEST_FILE"; then
    echo "  FAIL: Missing governed dispatch trace sequence test"
    TRACE_SEQ_COUNT=$((TRACE_SEQ_COUNT + 1))
fi

if [[ "$TRACE_SEQ_COUNT" -eq 0 ]]; then
    echo "  OK: Governed dispatch trace sequence gates are present"
fi
echo ""

# --- 8. Mastery API/runtime conformance gates ---
echo "--- 8. Mastery API/Runtime Conformance ---"

# 8a. Direct mastery mutation routes must not be publicly registered.
MUTATION_ROUTE_MATCHES="$(git grep -n -E '/api/v1/mastery/(save|transition)($|[[:space:]"])' -- \
    'products/data-cloud/delivery/launcher/src/main/java/**/*.java' 2>/dev/null || true)"
if [[ -n "$MUTATION_ROUTE_MATCHES" ]]; then
    echo "$MUTATION_ROUTE_MATCHES" | while IFS= read -r line; do
        echo "  FAIL: Deprecated direct mutation route registered: $line"
    done
    MASTERY_CONFORMANCE_COUNT=$((MASTERY_CONFORMANCE_COUNT + 1))
else
    echo "  OK: No deprecated direct mastery mutation routes are registered"
fi

# 8b. Deprecated mastery registry APIs are forbidden outside tests.
DEPRECATED_REGISTRY_USAGE="$(git grep -n -E '\.findBySkill\([^,]+,[^\)]*\)|\.findStale\([^,\)]*\)' -- \
    'platform/java/agent-core/src/main/java/**/*.java' \
    'products/data-cloud/**/src/main/java/**/*.java' \
    'products/aep/**/src/main/java/**/*.java' 2>/dev/null || true)"
if [[ -n "$DEPRECATED_REGISTRY_USAGE" ]]; then
    echo "$DEPRECATED_REGISTRY_USAGE" | while IFS= read -r line; do
        echo "  FAIL: Deprecated mastery registry API usage: $line"
    done
    MASTERY_CONFORMANCE_COUNT=$((MASTERY_CONFORMANCE_COUNT + 1))
else
    echo "  OK: No deprecated mastery registry API usage in production code"
fi

# 8c. Legacy projection bridge usage is compatibility-only and must not leak into product runtime code.
LEGACY_BRIDGE_USAGE="$(git grep -n 'MemoryProjectionBridge' -- \
    'products/**/src/main/java/**/*.java' \
    'shared-services/**/src/main/java/**/*.java' \
    'platform-kernel/**/src/main/java/**/*.java' 2>/dev/null || true)"
if [[ -n "$LEGACY_BRIDGE_USAGE" ]]; then
    echo "$LEGACY_BRIDGE_USAGE" | while IFS= read -r line; do
        echo "  FAIL: Legacy MemoryProjectionBridge usage in runtime/product code: $line"
    done
    MASTERY_CONFORMANCE_COUNT=$((MASTERY_CONFORMANCE_COUNT + 1))
else
    echo "  OK: No MemoryProjectionBridge usage in runtime/product code"
fi

# 8d. L5 definitions must be explicit governance workflows, never normal response-serving agents.
L5_FILES=( $(git grep -l -E 'learningLevel:[[:space:]]*L5([[:space:]]|$)' -- \
    'products/*/config/agents/**/*.yaml' 'products/*/config/agents/*.yaml' 2>/dev/null || true) )
for file in "${L5_FILES[@]}"; do
    if ! grep -Eq 'governanceWorkflow:[[:space:]]*true|supervision:[[:space:]]*HUMAN_GATED' "$REPO_ROOT/$file"; then
        echo "  FAIL: L5 agent definition is not marked as a governed workflow: $file"
        MASTERY_CONFORMANCE_COUNT=$((MASTERY_CONFORMANCE_COUNT + 1))
    else
        echo "  OK: L5 governed workflow verified: $file"
    fi
done

echo ""

# --- 9. Summary ---
echo "========================================"
echo " Summary"
echo "========================================"
echo "Compatibility markers:   $DEPRECATED_COUNT"
echo "Agent type aliases:      $TYPE_ALIAS_COUNT"
echo "Frontend contract drift: $FRONTEND_COUNT"
echo "Reflective loading:      $REFLECTIVE_COUNT"
echo "Trace sequence gates:    $TRACE_SEQ_COUNT"
echo "Mastery conformance:     $MASTERY_CONFORMANCE_COUNT"
if [[ "$DEPRECATED_COUNT" -gt 0 || "$TYPE_ALIAS_COUNT" -gt 0 || "$FRONTEND_COUNT" -gt 0 || "$REFLECTIVE_COUNT" -gt 0 || "$TRACE_SEQ_COUNT" -gt 0 || "$MASTERY_CONFORMANCE_COUNT" -gt 0 ]]; then
    echo "RESULT: FAIL"
    EXIT_CODE=1
else
    echo "RESULT: PASS"
fi

exit "$EXIT_CODE"
