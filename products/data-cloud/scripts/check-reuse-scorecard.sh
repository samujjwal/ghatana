#!/usr/bin/env bash
# =============================================================================
# check-reuse-scorecard.sh — Data-Cloud Shared-Library Reuse Scorecard
#
# Purpose: Enforce the reuse-first principle (Workstream G, E7-S1) by auditing
#          data-cloud product modules for duplication in governed categories.
#          Emits a scorecard table and exits 1 when any threshold is exceeded.
#
# Usage:
#   bash products/data-cloud/scripts/check-reuse-scorecard.sh [--warn-only]
#
# --warn-only   Print findings but exit 0 (useful for canary / gradual rollout)
#
# Governed categories (E7):
#   1. HTTP helpers    — must use platform:java:http-server, not custom
#   2. Validation      — must use ApiInputValidator (product local), not reinvent
#   3. Serialisation   — must use JsonUtils / Jackson from platform:java:core
#   4. Policy          — must use DataCloudSecurityFilter / platform governance
#   5. Observability   — must use MetricsCollector from platform:java:observability
#   6. Rate limiting   — must use platform:java:governance RateLimitFilter
#   7. Audit           — must use AuditService from platform:java:audit
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
LAUNCHER_SRC="$REPO_ROOT/products/data-cloud/delivery/launcher/src/main/java"
WARN_ONLY=false
[[ "${1:-}" == "--warn-only" ]] && WARN_ONLY=true

RED='\033[0;31m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'; NC='\033[0m'
BOLD='\033[1m'

pass=0; fail=0; warn=0

# ── Helpers ──────────────────────────────────────────────────────────────────

count_occurrences() {
    local pattern="$1" dir="$2"
    # Use grep || true so pipefail does not abort the script on zero matches
    { grep -r --include="*.java" -l "$pattern" "$dir" 2>/dev/null || true; } | wc -l | tr -d ' '
}

emit() {
    local status="$1" category="$2" finding="$3"
    case "$status" in
      PASS) echo -e "  ${GREEN}✅ PASS${NC}  [$category] $finding"; pass=$((pass+1)) ;;
      WARN) echo -e "  ${YELLOW}⚠  WARN${NC}  [$category] $finding"; warn=$((warn+1)) ;;
      FAIL) echo -e "  ${RED}✗  FAIL${NC}  [$category] $finding"; fail=$((fail+1)) ;;
    esac
}

# ── 1. HTTP helpers ───────────────────────────────────────────────────────────

echo ""
echo -e "${BOLD}Checking governed category: HTTP helpers${NC}"
# Exclude infrastructure files that legitimately use direct HttpResponse construction:
#   - HttpHandlerSupport.java    — the helper itself
#   - DataCloudMiddleware.java   — CORS/rate-limit/payload-size infrastructure
#   - DataCloudSecurityFilter.java — auth/authz deny responses
#   - DataCloudHttpServer.java   — top-level router orchestration
#   - SseStreamingHandler.java   — SSE framing (infrastructure)
# Only domain handlers (EntityCrudHandler, AiModelHandler, etc.) are governed.
LOCAL_HTTP=$(grep -r --include="*.java" \
    "HttpResponse\.ofCode\|HttpResponse\.ok200()" \
    "$LAUNCHER_SRC" 2>/dev/null \
    | grep -v "HttpHandlerSupport.java\|DataCloudMiddleware.java\|DataCloudSecurityFilter.java\|DataCloudHttpServer.java\|SseStreamingHandler.java" \
    | wc -l | tr -d ' ') || LOCAL_HTTP=0
if [[ "$LOCAL_HTTP" -le 5 ]]; then
    emit PASS "http-helpers" "HttpResponse built directly in domain handlers: $LOCAL_HTTP occurrences (threshold ≤5)"
else
    emit FAIL "http-helpers" "Domain handlers directly build HttpResponse $LOCAL_HTTP times — consolidate to HttpHandlerSupport (infrastructure exclusions already applied)"
fi

# ── 2. Validation ─────────────────────────────────────────────────────────────

echo ""
echo -e "${BOLD}Checking governed category: Input validation${NC}"
CUSTOM_VALIDATION=$(grep -r --include="*.java" \
    "request\.getQueryParameter\|request\.getHeader\|getPathParameter" \
    "$LAUNCHER_SRC" 2>/dev/null | grep -v "ApiInputValidator\|HttpHandlerSupport\|DataCloudSecurityFilter\|EndpointSensitivity" | wc -l | tr -d ' ') || CUSTOM_VALIDATION=0
USES_VALIDATOR=$(count_occurrences "ApiInputValidator" "$LAUNCHER_SRC")
if [[ "$USES_VALIDATOR" -ge 3 ]]; then
    emit PASS "validation" "ApiInputValidator used in $USES_VALIDATOR files (shared validator adopted)"
else
    emit WARN "validation" "ApiInputValidator used in only $USES_VALIDATOR files — ensure all handlers use it for path/query param validation"
fi

# ── 3. Serialisation ──────────────────────────────────────────────────────────

echo ""
echo -e "${BOLD}Checking governed category: Serialisation${NC}"
# Violation: manual JSON string building instead of ObjectMapper
# Pattern: search for lines containing '"{'  (a quoted open-brace — typical JSON string prefix)
# Uses simple BRE-safe literal pattern; -F flag avoids all regex ambiguity
MANUAL_JSON=$(grep -rn --include="*.java" -F '"{' \
    "$LAUNCHER_SRC" 2>/dev/null | wc -l | tr -d ' ') || MANUAL_JSON=0
if [[ "$MANUAL_JSON" -le 10 ]]; then
    emit PASS "serialisation" "Manual JSON string literals: $MANUAL_JSON (threshold ≤10, used only for simple error shapes)"
else
    emit WARN "serialisation" "Manual JSON literals: $MANUAL_JSON — prefer ObjectMapper.writeValueAsString() for consistency"
fi
USES_OBJECT_MAPPER=$(count_occurrences "objectMapper\|ObjectMapper" "$LAUNCHER_SRC")
if [[ "$USES_OBJECT_MAPPER" -ge 3 ]]; then
    emit PASS "serialisation" "ObjectMapper used in $USES_OBJECT_MAPPER files"
else
    emit WARN "serialisation" "ObjectMapper found in only $USES_OBJECT_MAPPER files — check serialisation consistency"
fi

# ── 4. Policy / security ─────────────────────────────────────────────────────

echo ""
echo -e "${BOLD}Checking governed category: Policy enforcement${NC}"
USES_SECURITY_FILTER=$(count_occurrences "DataCloudSecurityFilter" "$LAUNCHER_SRC")
USES_PLATFORM_GOVERNANCE=$(count_occurrences "ApiKeyAuthFilter\|TenantIsolationHttpFilter\|PolicyEngine" "$LAUNCHER_SRC")
if [[ "$USES_SECURITY_FILTER" -ge 1 ]]; then
    emit PASS "policy" "DataCloudSecurityFilter present ($USES_SECURITY_FILTER files)"
else
    emit FAIL "policy" "DataCloudSecurityFilter not found — security filter must wrap the router"
fi
if [[ "$USES_PLATFORM_GOVERNANCE" -ge 2 ]]; then
    emit PASS "policy" "Platform governance primitives used in $USES_PLATFORM_GOVERNANCE files"
else
    emit WARN "policy" "Platform governance (ApiKeyAuthFilter/PolicyEngine/TenantIsolation) found in only $USES_PLATFORM_GOVERNANCE files"
fi

# ── 5. Observability ─────────────────────────────────────────────────────────

echo ""
echo -e "${BOLD}Checking governed category: Observability${NC}"
USES_METRICS_COLLECTOR=$(count_occurrences "MetricsCollector\|MetricsCollectorFactory" "$LAUNCHER_SRC")
# Violation: direct Micrometer usage instead of MetricsCollector wrapper
# Exclude DataCloudLauncher.java — the bootstrap entry point legitimately
# constructs PrometheusMeterRegistry once as the root MeterRegistry.
DIRECT_MICROMETER=$(grep -r --include="*.java" \
    "MeterRegistry\|Counter\.builder\|Timer\.builder" \
    "$LAUNCHER_SRC" 2>/dev/null \
    | grep -v "MetricsCollectorFactory\|MetricsCollector\|DataCloudLauncher.java" \
    | wc -l | tr -d ' ') || DIRECT_MICROMETER=0
if [[ "$USES_METRICS_COLLECTOR" -ge 2 ]]; then
    emit PASS "observability" "MetricsCollector used in $USES_METRICS_COLLECTOR files (platform wrapper adopted)"
else
    emit WARN "observability" "MetricsCollector found in only $USES_METRICS_COLLECTOR files"
fi
if [[ "$DIRECT_MICROMETER" -le 2 ]]; then
    emit PASS "observability" "Direct Micrometer usage outside wrapper: $DIRECT_MICROMETER (threshold ≤2)"
else
    emit FAIL "observability" "Direct Micrometer usage: $DIRECT_MICROMETER — use MetricsCollector wrapper from platform:java:observability"
fi

# ── 6. Rate limiting ─────────────────────────────────────────────────────────

echo ""
echo -e "${BOLD}Checking governed category: Rate limiting${NC}"
LOCAL_RATE_LIMIT=$(grep -r --include="*.java" \
    "rateLimitState\|ConcurrentHashMap.*rateLimit\|rateLimitFilter" \
    "$LAUNCHER_SRC" 2>/dev/null | wc -l | tr -d ' ') || LOCAL_RATE_LIMIT=0
USES_PLATFORM_RATE_LIMIT=$(count_occurrences "RateLimitFilter" "$LAUNCHER_SRC")
if [[ "$LOCAL_RATE_LIMIT" -gt 0 ]]; then
    emit WARN "rate-limiting" "Product-local rate-limit state detected ($LOCAL_RATE_LIMIT lines) — prefer platform:java:governance RateLimitFilter"
else
    emit PASS "rate-limiting" "No product-local rate-limit state detected"
fi
if [[ "$USES_PLATFORM_RATE_LIMIT" -ge 1 ]]; then
    emit PASS "rate-limiting" "Platform RateLimitFilter imported in $USES_PLATFORM_RATE_LIMIT files"
else
    emit WARN "rate-limiting" "platform:java:governance RateLimitFilter not detected — consider migrating product-local implementation"
fi

# ── 7. Audit ─────────────────────────────────────────────────────────────────

echo ""
echo -e "${BOLD}Checking governed category: Audit emission${NC}"
USES_AUDIT_SERVICE=$(count_occurrences "AuditService\|AuditEvent" "$LAUNCHER_SRC")
if [[ "$USES_AUDIT_SERVICE" -ge 2 ]]; then
    emit PASS "audit" "AuditService used in $USES_AUDIT_SERVICE files (platform:java:audit adopted)"
else
    emit WARN "audit" "AuditService found in only $USES_AUDIT_SERVICE files — ensure all SENSITIVE/CRITICAL routes emit audit events"
fi

# ── Summary ───────────────────────────────────────────────────────────────────

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "${BOLD}Reuse Scorecard Summary${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "  ${GREEN}PASS${NC}: $pass  ${YELLOW}WARN${NC}: $warn  ${RED}FAIL${NC}: $fail"
echo ""

if [[ "$fail" -gt 0 ]]; then
    echo -e "${RED}✗ Reuse scorecard FAILED ($fail violation(s))${NC}"
    if [[ "$WARN_ONLY" == "true" ]]; then
        echo "(--warn-only: exiting 0 despite failures)"
        exit 0
    fi
    exit 1
elif [[ "$warn" -gt 0 ]]; then
    echo -e "${YELLOW}⚠ Reuse scorecard passed with $warn warning(s) — review recommended${NC}"
    exit 0
else
    echo -e "${GREEN}✅ Reuse scorecard PASSED — full shared-library adoption confirmed${NC}"
    exit 0
fi
