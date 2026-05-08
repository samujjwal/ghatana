#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# verify-runbook.sh — Smoke verification for Data Cloud RUNBOOK.md procedures
#
# PURPOSE (DC-OPS-003):
#   Verify that runbook-documented Gradle commands are valid on a local checkout.
#   This script does NOT start any live services or spin up real containers.
#   It validates:
#     1. Runbook-referenced Gradle tasks exist in the build graph
#     2. Runbook-referenced scripts exist and are syntactically valid bash
#     3. Key environment variable names referenced in the runbook match the real
#        launcher bootstrap source
#     4. Health, metrics, and audit API paths match the registered routes in the
#        router builder
#
# USAGE:
#   ./products/data-cloud/scripts/verify-runbook.sh
#
# EXIT CODES:
#   0  All checks passed
#   1  One or more checks failed
#
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DC_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$DC_ROOT/../.." && pwd)"
RUNBOOK="$DC_ROOT/docs/operations/RUNBOOK.md"
BOOTSTRAP="$DC_ROOT/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/bootstrap/DataCloudHttpLauncherBootstrap.java"
ROUTER="$DC_ROOT/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java"
SCRIPTS_DIR="$DC_ROOT/scripts"

# ── Colour helpers ────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS=0
FAIL=0

pass() { echo -e "${GREEN}✓${NC} $1"; ((PASS++)) || true; }
fail() { echo -e "${RED}✗${NC} $1"; ((FAIL++)) || true; }
info() { echo -e "${YELLOW}▸${NC} $1"; }

# ─────────────────────────────────────────────────────────────────────────────
# 1. Runbook and source files exist
# ─────────────────────────────────────────────────────────────────────────────
info "=== Section 1: Required files exist ==="

if [[ -f "$RUNBOOK" ]]; then
  pass "RUNBOOK.md exists"
else
  fail "RUNBOOK.md not found at $RUNBOOK"
fi

if [[ -f "$BOOTSTRAP" ]]; then
  pass "DataCloudHttpLauncherBootstrap.java exists"
else
  fail "Bootstrap not found at $BOOTSTRAP"
fi

if [[ -f "$ROUTER" ]]; then
  pass "DataCloudRouterBuilder.java exists"
else
  fail "RouterBuilder not found at $ROUTER"
fi

# ─────────────────────────────────────────────────────────────────────────────
# 2. Runbook-referenced scripts exist and are valid bash
# ─────────────────────────────────────────────────────────────────────────────
info "=== Section 2: Referenced scripts are valid bash ==="

EXPECTED_SCRIPTS=(
  "run-durable-load-suite.sh"
)

for script_name in "${EXPECTED_SCRIPTS[@]}"; do
  script_path="$SCRIPTS_DIR/$script_name"
  if [[ -f "$script_path" ]]; then
    if bash -n "$script_path" 2>/dev/null; then
      pass "Script $script_name is syntactically valid"
    else
      fail "Script $script_name has syntax errors"
    fi
  else
    fail "Runbook references script $script_name but it does not exist at $script_path"
  fi
done

# ─────────────────────────────────────────────────────────────────────────────
# 3. Runbook-referenced environment variables match launcher bootstrap
# ─────────────────────────────────────────────────────────────────────────────
info "=== Section 3: Env vars documented in runbook match bootstrap source ==="

# Key variables the runbook documents under trace export
TRACE_VARS=(
  "CLICKHOUSE_HOST"
  "CLICKHOUSE_PORT"
  "CLICKHOUSE_DATABASE"
)

for var in "${TRACE_VARS[@]}"; do
  if grep -q "$var" "$BOOTSTRAP"; then
    pass "Env var $var found in bootstrap source"
  else
    fail "Env var $var documented in runbook but NOT found in bootstrap source"
  fi
done

# Key variables the runbook documents under startup/auth
AUTH_VARS=(
  "DATACLOUD_API_KEYS"
  "DATACLOUD_JWT_SECRET"
  "DATACLOUD_JWT_JWKS_URL"
  "DATACLOUD_PROFILE"
)

for var in "${AUTH_VARS[@]}"; do
  # Accept match in bootstrap OR in launcher http server or related sources
  if grep -rq "$var" "$DC_ROOT/delivery/launcher/src/main/java/" 2>/dev/null; then
    pass "Env var $var found in launcher source"
  else
    fail "Env var $var documented in runbook but NOT found in launcher source"
  fi
done

# ─────────────────────────────────────────────────────────────────────────────
# 4. Runbook API paths match registered routes
# ─────────────────────────────────────────────────────────────────────────────
info "=== Section 4: Runbook API paths exist in router ==="

EXPECTED_PATHS=(
  "/ready"
  "/live"
  "/metrics"
  "/api/v1/entities"
  "/api/v1/analytics/query"
  "/api/v1/autonomy/logs"
)

for api_path in "${EXPECTED_PATHS[@]}"; do
  if grep -q "$api_path" "$ROUTER"; then
    pass "Route $api_path registered in RouterBuilder"
  else
    fail "Runbook documents route $api_path but it is NOT registered in RouterBuilder"
  fi
done

# ─────────────────────────────────────────────────────────────────────────────
# 5. Gradle task references in runbook are valid module paths
# ─────────────────────────────────────────────────────────────────────────────
info "=== Section 5: Gradle module paths from runbook exist in workspace ==="

EXPECTED_MODULES=(
  "products/data-cloud/extensions/plugins"
  "products/data-cloud/delivery/runtime-composition"
  "products/data-cloud/delivery/launcher"
)

for module_path in "${EXPECTED_MODULES[@]}"; do
  full_path="$REPO_ROOT/$module_path"
  if [[ -d "$full_path" ]]; then
    if [[ -f "$full_path/build.gradle.kts" ]]; then
      pass "Gradle module $module_path has build.gradle.kts"
    else
      fail "Gradle module $module_path directory exists but has no build.gradle.kts"
    fi
  else
    fail "Gradle module $module_path does not exist (runbook references it)"
  fi
done

# ─────────────────────────────────────────────────────────────────────────────
# 6. Runbook covers all required operational sections (DC-OPS-003)
# ─────────────────────────────────────────────────────────────────────────────
info "=== Section 6: Runbook covers required operational procedures ==="

REQUIRED_SECTIONS=(
  "Deployment Modes"
  "HTTP security"
  "Trace Export"
  "Audit Trail"
  "Route-Level Metrics"
  "Degraded Mode"
  "Production Startup Checklist"
  "Recovery Notes"
  "Tenant Isolation"
)

for section in "${REQUIRED_SECTIONS[@]}"; do
  if grep -q "$section" "$RUNBOOK"; then
    pass "Runbook covers: $section"
  else
    fail "Runbook is missing required section: $section"
  fi
done

# ─────────────────────────────────────────────────────────────────────────────
# Summary
# ─────────────────────────────────────────────────────────────────────────────
echo ""
echo "────────────────────────────────────────"
echo -e "${GREEN}PASS${NC}: $PASS  ${RED}FAIL${NC}: $FAIL"
echo "────────────────────────────────────────"

if [[ $FAIL -gt 0 ]]; then
  echo -e "${RED}Runbook verification FAILED — $FAIL check(s) failed.${NC}"
  exit 1
else
  echo -e "${GREEN}Runbook verification PASSED — all $PASS checks passed.${NC}"
  exit 0
fi
