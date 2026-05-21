#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# smoke-validate-scripts.sh — Smoke validation for critical Data-Cloud scripts
#
# PURPOSE (DC-A12):
#   Ensure that each critical product script is syntactically valid and can
#   run its non-destructive validation path (--warn-only or --dry-run) without
#   returning an unexpected exit code.
#
#   This script is intended to be run in CI as a lightweight gate before
#   heavier integration pipelines. It does NOT start any services.
#
# USAGE:
#   ./products/data-cloud/scripts/smoke-validate-scripts.sh
#
# EXIT CODES:
#   0  All checks passed
#   1  One or more checks failed
#
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── Colour helpers ────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS=0
FAIL=0

pass() { echo -e "${GREEN}✓${NC} $1"; ((PASS += 1)); }
fail() { echo -e "${RED}✗${NC} $1"; ((FAIL += 1)); }
info() { echo -e "${YELLOW}▸${NC} $1"; }

# ── Helper: assert bash syntax is valid ───────────────────────────────────────
assert_syntax() {
  local script="$1"
  local name
  name="$(basename "$script")"

  if [[ ! -f "$script" ]]; then
    fail "${name}: file not found"
    return
  fi

  if bash -n "$script" 2>&1; then
    pass "${name}: bash syntax OK"
  else
    fail "${name}: bash syntax error"
  fi
}

# ── Helper: assert script is executable ───────────────────────────────────────
assert_executable() {
  local script="$1"
  local name
  name="$(basename "$script")"

  if [[ -x "$script" ]]; then
    pass "${name}: executable bit set"
  else
    fail "${name}: not executable (run: chmod +x $script)"
  fi
}

# ── Helper: assert warn-only exits 0 ─────────────────────────────────────────
# Only runs the script if ALL required resource files exist in the workspace.
# Missing files are expected in CI pull-request environments without a full
# product checkout; in that case the check is skipped.
assert_warn_only() {
  local script="$1"
  local name
  name="$(basename "$script")"

  local exit_code=0
  bash "$script" --warn-only > /dev/null 2>&1 || exit_code=$?

  if [[ $exit_code -eq 0 ]]; then
    pass "${name}: --warn-only exits 0"
  elif [[ $exit_code -eq 2 ]]; then
    # Exit 2 = required files not found (expected in sparse CI checkout)
    info "${name}: --warn-only exited 2 (required files absent — skipped)"
  else
    fail "${name}: --warn-only exited ${exit_code} (expected 0 or 2)"
  fi
}

# ═════════════════════════════════════════════════════════════════════════════
# CRITICAL SCRIPTS TO VALIDATE
# ═════════════════════════════════════════════════════════════════════════════

CRITICAL_SCRIPTS=(
  "check-openapi-drift.sh"
  "check-doc-boundaries.sh"
  "check-reuse-scorecard.sh"
  "audit-tenant-isolation.sh"
  "verify-coverage.sh"
  "verify-requirements.sh"
  "verify-flakiness.sh"
)

echo "──────────────────────────────────────────────────────────────────────────"
echo " Data-Cloud Script Smoke Validation (DC-A12)"
echo "──────────────────────────────────────────────────────────────────────────"
echo ""

# ── Pass 1: Syntax check all critical scripts ─────────────────────────────────
info "Pass 1: Syntax validation"
for name in "${CRITICAL_SCRIPTS[@]}"; do
  assert_syntax "${SCRIPT_DIR}/${name}"
done
echo ""

# ── Pass 2: Executable bit ────────────────────────────────────────────────────
info "Pass 2: Executable bit"
for name in "${CRITICAL_SCRIPTS[@]}"; do
  assert_executable "${SCRIPT_DIR}/${name}"
done
echo ""

# ── Pass 3: warn-only / dry-run mode for scripts that support it ──────────────
WARN_ONLY_SCRIPTS=(
  "check-openapi-drift.sh"
  "check-doc-boundaries.sh"
)

info "Pass 3: --warn-only exits 0"
for name in "${WARN_ONLY_SCRIPTS[@]}"; do
  assert_warn_only "${SCRIPT_DIR}/${name}"
done
echo ""

# ─── Summary ─────────────────────────────────────────────────────────────────
echo "──────────────────────────────────────────────────────────────────────────"
echo -e " Results: ${GREEN}${PASS} passed${NC}  ${RED}${FAIL} failed${NC}"
echo "──────────────────────────────────────────────────────────────────────────"

if [[ $FAIL -gt 0 ]]; then
  exit 1
fi
exit 0
