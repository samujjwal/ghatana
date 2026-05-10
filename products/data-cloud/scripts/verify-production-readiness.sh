#!/bin/bash
#
# verify-production-readiness.sh
#
# Consolidated production-readiness gate for Data Cloud (P1-9).
# Runs all critical checks in one shot and exits non-zero if any fail.
#
# Usage:
#   ./scripts/verify-production-readiness.sh [--profile <local|staging|production>]
#
# CI Usage (from repo root):
#   ./products/data-cloud/scripts/verify-production-readiness.sh --profile production
#
# @doc.type script
# @doc.purpose Consolidated production-readiness gate (P1-9)
# @doc.layer infrastructure
# @doc.pattern CI/CD

set -euo pipefail

# ---------------------------------------------------------------------------
# Colour helpers
# ---------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

pass()  { echo -e "  ${GREEN}[PASS]${NC} $*"; }
fail()  { echo -e "  ${RED}[FAIL]${NC} $*"; FAILURES=$((FAILURES + 1)); }
warn()  { echo -e "  ${YELLOW}[WARN]${NC} $*"; }
info()  { echo -e "  ${BLUE}[INFO]${NC} $*"; }
section() { echo -e "\n${BLUE}=== $* ===${NC}"; }

FAILURES=0
PROFILE="local"

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --profile)
      PROFILE="$2"
      shift 2
      ;;
    --profile=*)
      PROFILE="${1#*=}"
      shift
      ;;
    *)
      shift
      ;;
  esac
done

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || echo ".")"
DC_ROOT="${REPO_ROOT}/products/data-cloud"

echo -e "${BLUE}"
echo "╔══════════════════════════════════════════════════════════╗"
echo "║     Data Cloud — Production Readiness Gate (P1-9)       ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo -e "${NC}"
info "Profile: ${PROFILE}"
info "Repo root: ${REPO_ROOT}"

# ---------------------------------------------------------------------------
# 1. Java build health
# ---------------------------------------------------------------------------
section "Java build health"

if command -v ./gradlew &>/dev/null; then
  if ./gradlew products:data-cloud:delivery:launcher:compileJava --quiet 2>/dev/null; then
    pass "Launcher compiles without errors"
  else
    fail "Launcher compile failed — check Java sources"
  fi
else
  warn "gradlew not found — skipping Java compile check"
fi

# ---------------------------------------------------------------------------
# 2. Unit test gate
# ---------------------------------------------------------------------------
section "Unit tests (launcher)"

if command -v ./gradlew &>/dev/null; then
  if ./gradlew products:data-cloud:delivery:launcher:test --quiet 2>/dev/null; then
    pass "All launcher unit tests pass"
  else
    fail "Launcher unit tests FAILED"
  fi
else
  warn "gradlew not found — skipping unit test check"
fi

# ---------------------------------------------------------------------------
# 3. RuntimeProfileValidator existence
# ---------------------------------------------------------------------------
section "Fail-closed gate class (P0-1)"

VALIDATOR_SRC="${DC_ROOT}/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/RuntimeProfileValidator.java"
VALIDATOR_TEST="${DC_ROOT}/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/RuntimeProfileValidatorTest.java"

if [[ -f "${VALIDATOR_SRC}" ]]; then
  pass "RuntimeProfileValidator.java exists"
else
  fail "RuntimeProfileValidator.java is missing — P0-1 fail-closed gate not implemented"
fi

if [[ -f "${VALIDATOR_TEST}" ]]; then
  pass "RuntimeProfileValidatorTest.java exists"
else
  fail "RuntimeProfileValidatorTest.java is missing — P0-1 tests not implemented"
fi

# ---------------------------------------------------------------------------
# 4. SurfaceRecord / DependencyProbeResult typed runtime truth (P1-5)
# ---------------------------------------------------------------------------
section "Typed Runtime Truth (P1-5)"

SURFACE_RECORD="${DC_ROOT}/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/SurfaceRecord.java"
DEP_PROBE="${DC_ROOT}/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DependencyProbeResult.java"
SURFACE_TEST="${DC_ROOT}/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/SurfaceRecordTest.java"

if [[ -f "${SURFACE_RECORD}" ]]; then
  pass "SurfaceRecord.java exists"
else
  fail "SurfaceRecord.java is missing — typed Runtime Truth not implemented"
fi

if [[ -f "${DEP_PROBE}" ]]; then
  pass "DependencyProbeResult.java exists"
else
  fail "DependencyProbeResult.java is missing — typed Runtime Truth not implemented"
fi

if [[ -f "${SURFACE_TEST}" ]]; then
  pass "SurfaceRecordTest.java exists"
else
  fail "SurfaceRecordTest.java is missing"
fi

# ---------------------------------------------------------------------------
# 5. No production stubs / placeholders in critical paths (P0)
# ---------------------------------------------------------------------------
section "Production placeholder check"

PLACEHOLDER_SCAN="${DC_ROOT}/scripts/scan-production-placeholders.sh"
if [[ -x "${PLACEHOLDER_SCAN}" ]]; then
  if "${PLACEHOLDER_SCAN}" 2>/dev/null | grep -q "PLACEHOLDER.*FAIL\|TODO.*production\|FIXME.*production" 2>/dev/null; then
    fail "Production placeholders detected — see scan-production-placeholders.sh output"
  else
    pass "No production placeholders detected"
  fi
else
  warn "scan-production-placeholders.sh not executable — skipping"
fi

# ---------------------------------------------------------------------------
# 6. Stale coverage-gates module references
# ---------------------------------------------------------------------------
section "Coverage gates hygiene"

COVERAGE_GATES="${DC_ROOT}/gradle/coverage-gates.gradle"
if [[ -f "${COVERAGE_GATES}" ]]; then
  # Extract module names from the coverageTargets map
  STALE_MODULES=()
  while IFS= read -r line; do
    if [[ "$line" =~ \'([a-zA-Z0-9_-]+)\':[[:space:]]*\[ ]]; then
      MODULE="${BASH_REMATCH[1]}"
      MODULE_DIR="${DC_ROOT}/${MODULE}"
      if [[ ! -d "${MODULE_DIR}" ]]; then
        STALE_MODULES+=("${MODULE}")
      fi
    fi
  done < "${COVERAGE_GATES}"

  if [[ ${#STALE_MODULES[@]} -eq 0 ]]; then
    pass "All coverage-gates modules exist on disk"
  else
    for m in "${STALE_MODULES[@]}"; do
      fail "Stale coverage-gates module: '${m}' (directory not found)"
    done
  fi
else
  warn "coverage-gates.gradle not found — skipping"
fi

# ---------------------------------------------------------------------------
# 7. OpenAPI contract drift check
# ---------------------------------------------------------------------------
section "OpenAPI contract drift"

OPENAPI_CHECK="${DC_ROOT}/scripts/check-openapi-drift.sh"
if [[ -x "${OPENAPI_CHECK}" ]]; then
  if "${OPENAPI_CHECK}" 2>/dev/null; then
    pass "OpenAPI contract is in sync"
  else
    fail "OpenAPI contract drift detected — run check-openapi-drift.sh for details"
  fi
else
  warn "check-openapi-drift.sh not executable — skipping"
fi

# ---------------------------------------------------------------------------
# 8. Profile-specific environment variable checks (production only)
# ---------------------------------------------------------------------------
if [[ "${PROFILE}" == "production" || "${PROFILE}" == "staging" ]]; then
  section "Environment variables (${PROFILE} profile)"

  REQUIRED_VARS=(
    "DATACLOUD_PROFILE"
    "DATACLOUD_DB_ENABLED"
    "DATACLOUD_DB_URL"
    "DATACLOUD_DB_USER"
    "DATACLOUD_DB_PASSWORD"
    "DATACLOUD_KAFKA_ENABLED"
    "DATACLOUD_KAFKA_BOOTSTRAP"
    "DATACLOUD_AUDIT_ENABLED"
    "DATACLOUD_AUTH_ENABLED"
    "DATACLOUD_POLICY_ENGINE_URL"
    "DATACLOUD_METRICS_ENABLED"
    "DATACLOUD_TRACING_ENABLED"
    "CLICKHOUSE_HOST"
  )

  for VAR in "${REQUIRED_VARS[@]}"; do
    if [[ -n "${!VAR:-}" ]]; then
      pass "${VAR} is set"
    else
      fail "${VAR} is NOT set — required for ${PROFILE} profile"
    fi
  done
fi

# ---------------------------------------------------------------------------
# 9. Runbook existence
# ---------------------------------------------------------------------------
section "Runbook and operations docs"

RUNBOOK="${DC_ROOT}/docs/operations/RUNBOOK.md"
CHECKLIST="${DC_ROOT}/docs/operations/PRODUCTION_PROFILE_CHECKLIST.md"

if [[ -f "${RUNBOOK}" ]]; then
  pass "RUNBOOK.md exists"
else
  fail "RUNBOOK.md is missing"
fi

if [[ -f "${CHECKLIST}" ]]; then
  pass "PRODUCTION_PROFILE_CHECKLIST.md exists"
else
  fail "PRODUCTION_PROFILE_CHECKLIST.md is missing"
fi

# ---------------------------------------------------------------------------
# 10. TypeScript build
# ---------------------------------------------------------------------------
section "TypeScript build (UI)"

UI_DIR="${DC_ROOT}/delivery/ui"
if [[ -d "${UI_DIR}" ]] && command -v pnpm &>/dev/null; then
  if pnpm --prefix "${UI_DIR}" exec tsc --noEmit 2>/dev/null; then
    pass "TypeScript compilation succeeds with zero errors"
  else
    fail "TypeScript compilation failed — fix type errors before deploying"
  fi
else
  warn "pnpm not found or UI directory missing — skipping TypeScript check"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo -e "${BLUE}════════════════════════════════════════${NC}"
if [[ ${FAILURES} -eq 0 ]]; then
  echo -e "  ${GREEN}ALL CHECKS PASSED — Profile: ${PROFILE}${NC}"
  echo -e "${BLUE}════════════════════════════════════════${NC}"
  exit 0
else
  echo -e "  ${RED}${FAILURES} CHECK(S) FAILED — Profile: ${PROFILE}${NC}"
  echo -e "  Fix all failures before promoting to ${PROFILE}."
  echo -e "${BLUE}════════════════════════════════════════${NC}"
  exit 1
fi
