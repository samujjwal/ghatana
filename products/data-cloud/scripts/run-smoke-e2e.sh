#!/usr/bin/env bash
# =============================================================================
# run-smoke-e2e.sh — Data-Cloud E2E Smoke Validation
#
# Purpose: Executes a lightweight end-to-end smoke matrix against a running
#          Data-Cloud instance.  Validates that the primary data path, voice
#          catalog, analytics, and health endpoints are all operational.
#
# Usage:
#   bash products/data-cloud/scripts/run-smoke-e2e.sh [--warn-only] [--json]
#
# Options:
#   --warn-only  Print findings but exit 0 even on FAIL (for canary gates)
#   --json       Emit structured JSON report to stdout (in addition to text log)
#
# Environment variables:
#   DC_BASE_URL    Data-Cloud HTTP base URL (default: http://localhost:8080)
#   DC_TENANT_ID   Tenant ID for smoke test scoping (default: smoke_tenant)
#   DC_API_TOKEN   Bearer token for authenticated requests (default: smoke-test-token)
#   SMOKE_TIMEOUT  curl timeout in seconds per request (default: 10)
#
# Exit codes:
#   0  All checks PASS (or --warn-only was set)
#   1  One or more checks FAIL
# =============================================================================
set -euo pipefail

# ─── Configuration ────────────────────────────────────────────────────────────
BASE_URL="${DC_BASE_URL:-http://localhost:8080}"
TENANT_ID="${DC_TENANT_ID:-smoke_tenant}"
API_TOKEN="${DC_API_TOKEN:-smoke-test-token}"
TIMEOUT="${SMOKE_TIMEOUT:-10}"
SMOKE_COLLECTION="dc_smoke_$$"                   # unique per run, cleaned up in finally

WARN_ONLY=false
EMIT_JSON=false
PASS=0
FAIL=0
SKIP=0
declare -a RESULTS=()

for arg in "$@"; do
  case "$arg" in
    --warn-only) WARN_ONLY=true ;;
    --json)      EMIT_JSON=true ;;
  esac
done

# ─── Helpers ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'

log()  { echo -e "  $*"; }
info() { echo -e "${BLUE}[DC-SMOKE]${NC} $*"; }

emit() {
  local status="$1"; local name="$2"; local detail="$3"
  case "$status" in
    PASS)
      PASS=$((PASS+1))
      log "${GREEN}✓ PASS${NC}  ${name}: ${detail}"
      RESULTS+=("{\"check\":\"$name\",\"status\":\"PASS\",\"detail\":\"$detail\"}")
      ;;
    FAIL)
      FAIL=$((FAIL+1))
      log "${RED}✗ FAIL${NC}  ${name}: ${detail}"
      RESULTS+=("{\"check\":\"$name\",\"status\":\"FAIL\",\"detail\":\"$detail\"}")
      ;;
    WARN)
      SKIP=$((SKIP+1))
      log "${YELLOW}⚠ WARN${NC}  ${name}: ${detail}"
      RESULTS+=("{\"check\":\"$name\",\"status\":\"WARN\",\"detail\":\"$detail\"}")
      ;;
    SKIP)
      SKIP=$((SKIP+1))
      log "${YELLOW}  SKIP${NC}  ${name}: ${detail}"
      RESULTS+=("{\"check\":\"$name\",\"status\":\"SKIP\",\"detail\":\"$detail\"}")
      ;;
  esac
}

# Performs a curl request and returns the HTTP status code.
# Usage: http_code=$(dc_curl GET /path [body_file])
dc_curl() {
  local method="$1"; local path="$2"; local body_file="${3:-}"
  local extra_args=()
  if [[ -n "$body_file" ]]; then
    extra_args=(-X "$method" -H "Content-Type: application/json" --data "@$body_file")
  else
    extra_args=(-X "$method")
  fi
  curl -s -o /dev/null -w "%{http_code}" \
    --max-time "$TIMEOUT" \
    -H "Authorization: Bearer ${API_TOKEN}" \
    -H "X-Tenant-Id: ${TENANT_ID}" \
    "${extra_args[@]}" \
    "${BASE_URL}${path}" 2>/dev/null || true
}

# Performs a curl request and returns the *body* as a string.
dc_curl_body() {
  local method="$1"; local path="$2"; local body_file="${3:-}"
  local extra_args=()
  if [[ -n "$body_file" ]]; then
    extra_args=(-X "$method" -H "Content-Type: application/json" --data "@$body_file")
  else
    extra_args=(-X "$method")
  fi
  curl -s \
    --max-time "$TIMEOUT" \
    -H "Authorization: Bearer ${API_TOKEN}" \
    -H "X-Tenant-Id: ${TENANT_ID}" \
    "${extra_args[@]}" \
    "${BASE_URL}${path}" 2>/dev/null || true
}

TMPDIR_LOCAL="$(mktemp -d)"
cleanup() { rm -rf "$TMPDIR_LOCAL"; }
trap cleanup EXIT

# ─── Test matrix ──────────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║          Data-Cloud E2E Smoke Validation                            ║"
echo "╚══════════════════════════════════════════════════════════════════════╝"
echo ""
info "Base URL  : ${BASE_URL}"
info "Tenant    : ${TENANT_ID}"
info "Timeout   : ${TIMEOUT}s"
echo ""

# ─── 1. Health endpoints ──────────────────────────────────────────────────────
echo "── [1] Health endpoints ──────────────────────────────────────────────"

code=$(dc_curl GET /health)
if [[ "$code" == "200" ]]; then
  emit PASS "health:liveness" "GET /health → HTTP 200"
else
  emit FAIL "health:liveness" "GET /health returned HTTP $code (expected 200)"
fi

code=$(dc_curl GET /ready)
if [[ "$code" == "200" ]]; then
  emit PASS "health:readiness" "GET /ready → HTTP 200"
elif [[ "$code" == "503" ]]; then
  emit WARN "health:readiness" "GET /ready → HTTP 503 (service not ready; downstream may be degraded)"
else
  emit FAIL "health:readiness" "GET /ready returned HTTP $code"
fi

# ─── 2. Entity CRUD smoke ─────────────────────────────────────────────────────
echo ""
echo "── [2] Entity CRUD ───────────────────────────────────────────────────"

# Create entity
ENTITY_BODY="$TMPDIR_LOCAL/entity.json"
cat > "$ENTITY_BODY" <<EOF
{"name":"smoke-entity-$$","source":"smoke-test","status":"active"}
EOF

code=$(dc_curl POST "/api/v1/entities/${SMOKE_COLLECTION}" "$ENTITY_BODY")
if [[ "$code" == "201" || "$code" == "200" ]]; then
  emit PASS "entity:create" "POST /entities/${SMOKE_COLLECTION} → HTTP $code"
else
  emit FAIL "entity:create" "POST /entities/${SMOKE_COLLECTION} → HTTP $code (expected 201)"
fi

# Query entities (list)
code=$(dc_curl GET "/api/v1/entities/${SMOKE_COLLECTION}?limit=1")
if [[ "$code" == "200" ]]; then
  emit PASS "entity:list" "GET /entities/${SMOKE_COLLECTION} → HTTP 200"
else
  emit FAIL "entity:list" "GET /entities/${SMOKE_COLLECTION} → HTTP $code (expected 200)"
fi

# Delete the smoke collection's entities (cleanup is best-effort, not a test failure)
code=$(dc_curl DELETE "/api/v1/entities/${SMOKE_COLLECTION}") || true
if [[ "$code" == "200" || "$code" == "204" || "$code" == "404" ]]; then
  emit PASS "entity:cleanup" "DELETE /entities/${SMOKE_COLLECTION} → HTTP $code"
else
  emit WARN "entity:cleanup" "DELETE /entities/${SMOKE_COLLECTION} → HTTP $code (non-blocking)"
fi

# ─── 3. Event append + query ──────────────────────────────────────────────────
echo ""
echo "── [3] Event operations ──────────────────────────────────────────────"

EVENT_BODY="$TMPDIR_LOCAL/event.json"
cat > "$EVENT_BODY" <<EOF
{"type":"smoke.test.event","payload":{"run":"$$"},"tenantId":"${TENANT_ID}"}
EOF

code=$(dc_curl POST "/api/v1/events" "$EVENT_BODY")
if [[ "$code" == "201" || "$code" == "200" ]]; then
  emit PASS "event:append" "POST /events → HTTP $code"
else
  emit FAIL "event:append" "POST /events → HTTP $code (expected 201)"
fi

code=$(dc_curl GET "/api/v1/events?type=smoke.test.event&limit=1")
if [[ "$code" == "200" ]]; then
  emit PASS "event:query" "GET /events?type=smoke.test.event → HTTP 200"
else
  emit FAIL "event:query" "GET /events → HTTP $code (expected 200)"
fi

# ─── 4. Analytics query smoke ─────────────────────────────────────────────────
echo ""
echo "── [4] Analytics ─────────────────────────────────────────────────────"

ANALYTICS_BODY="$TMPDIR_LOCAL/analytics.json"
cat > "$ANALYTICS_BODY" <<EOF
{"query":"SELECT 1 AS ok","parameters":{},"timeout":5000}
EOF

code=$(dc_curl POST "/api/v1/analytics/query" "$ANALYTICS_BODY")
if [[ "$code" == "200" || "$code" == "202" ]]; then
  emit PASS "analytics:query" "POST /analytics/query → HTTP $code"
elif [[ "$code" == "503" ]]; then
  emit WARN "analytics:query" "POST /analytics/query → HTTP 503 (analytics engine may be warming up)"
else
  emit FAIL "analytics:query" "POST /analytics/query → HTTP $code (expected 200 or 202)"
fi

# ─── 5. Voice intent catalog ──────────────────────────────────────────────────
echo ""
echo "── [5] Voice intent catalog ──────────────────────────────────────────"

body=$(dc_curl_body GET "/api/v1/voice/intents")
catalog_count=$(echo "$body" | grep -o '"name"' | wc -l | tr -d ' ') || catalog_count=0

if [[ "$catalog_count" -ge 20 ]]; then
  emit PASS "voice:catalog-count" "GET /voice/intents → $catalog_count intents (≥ 20 required)"
elif [[ "$catalog_count" -ge 10 ]]; then
  emit WARN "voice:catalog-count" "GET /voice/intents → $catalog_count intents (expected ≥ 20)"
else
  emit FAIL "voice:catalog-count" "GET /voice/intents → $catalog_count intents (expected ≥ 20)"
fi

# Test a known intent classification via text path
INTENT_BODY="$TMPDIR_LOCAL/intent.json"
cat > "$INTENT_BODY" <<EOF
{"utterance":"list pipelines","confirm":false}
EOF

code=$(dc_curl POST "/api/v1/voice/intent" "$INTENT_BODY")
if [[ "$code" == "200" ]]; then
  emit PASS "voice:classify" "POST /voice/intent('list pipelines') → HTTP 200"
elif [[ "$code" == "404" ]]; then
  emit WARN "voice:classify" "POST /voice/intent → HTTP 404 (voice endpoint may not be deployed in this env)"
else
  emit FAIL "voice:classify" "POST /voice/intent → HTTP $code (expected 200)"
fi

# ─── 6. Pipeline list ─────────────────────────────────────────────────────────
echo ""
echo "── [6] Pipeline operations ───────────────────────────────────────────"

code=$(dc_curl GET "/api/v1/pipelines")
if [[ "$code" == "200" ]]; then
  emit PASS "pipeline:list" "GET /pipelines → HTTP 200"
else
  emit FAIL "pipeline:list" "GET /pipelines → HTTP $code (expected 200)"
fi

# ─── 7. Checkpoint list ───────────────────────────────────────────────────────
echo ""
echo "── [7] Checkpoint operations ─────────────────────────────────────────"

code=$(dc_curl GET "/api/v1/checkpoints")
if [[ "$code" == "200" ]]; then
  emit PASS "checkpoint:list" "GET /checkpoints → HTTP 200"
else
  emit FAIL "checkpoint:list" "GET /checkpoints → HTTP $code (expected 200)"
fi

# ─── 8. Governance retention policy ──────────────────────────────────────────
echo ""
echo "── [8] Governance ────────────────────────────────────────────────────"

code=$(dc_curl GET "/api/v1/governance/retention/policy?collection=entities")
if [[ "$code" == "200" ]]; then
  emit PASS "governance:retention-policy" "GET /governance/retention/policy → HTTP 200"
elif [[ "$code" == "404" ]]; then
  emit WARN "governance:retention-policy" "GET /governance/retention/policy → 404 (no policy classified yet)"
else
  emit FAIL "governance:retention-policy" "GET /governance/retention/policy → HTTP $code"
fi

# ─────────────────────────────────────────────────────────────────────────────
# Summary
# ─────────────────────────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║  Smoke Results                                                      ║"
echo "╚══════════════════════════════════════════════════════════════════════╝"
printf "  PASS: %d   FAIL: %d   WARN/SKIP: %d\n" "$PASS" "$FAIL" "$SKIP"
echo ""

if [[ "$EMIT_JSON" == "true" ]]; then
  echo "{"
  echo "  \"summary\": {\"pass\": $PASS, \"fail\": $FAIL, \"warn\": $SKIP},"
  echo "  \"results\": ["
  for i in "${!RESULTS[@]}"; do
    if [[ $i -lt $(( ${#RESULTS[@]} - 1 )) ]]; then
      echo "    ${RESULTS[$i]},"
    else
      echo "    ${RESULTS[$i]}"
    fi
  done
  echo "  ]"
  echo "}"
fi

if [[ "$FAIL" -gt 0 ]]; then
  if [[ "$WARN_ONLY" == "true" ]]; then
    info "WARN_ONLY mode: $FAIL check(s) failed — exiting 0 for gradual rollout"
    exit 0
  else
    echo -e "${RED}ERROR: $FAIL smoke check(s) failed — deployment blocked.${NC}"
    exit 1
  fi
fi

info "All smoke checks passed. ✓"
exit 0
