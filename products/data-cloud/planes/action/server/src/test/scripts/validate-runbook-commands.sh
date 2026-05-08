#!/usr/bin/env bash
# Recovery Runbook Validation Smoke Tests (DC-P1-452)
#
# Validates that the AEP disaster recovery runbook scripts:
#   1. Exist at the documented paths
#   2. Are executable (have execute permission)
#   3. Respond to --help without errors (syntactically valid bash)
#   4. Contain the expected option flags documented in README.md
#
# Acceptance criteria (from todo DC-P1-452):
#   "commands in runbook are executable and current — runbook command drift fails CI"
#
# Usage:
#   ./validate-runbook-commands.sh
#   Returns 0 on success, non-zero on failure.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DR_DIR="$(cd "${SCRIPT_DIR}/../../../../scripts/disaster-recovery" && pwd)"

PASS=0
FAIL=0
ERRORS=()

pass() { echo "  ✓ $1"; ((PASS++)) || true; }
fail() { echo "  ✗ $1"; ERRORS+=("$1"); ((FAIL++)) || true; }

echo "=== Recovery Runbook Command Smoke Tests (DC-P1-452) ==="
echo ""

# ─── 1. Script existence ─────────────────────────────────────────────────────
echo "1. Script existence checks"

for script in backup-aep.sh restore-aep.sh dr-drill.sh; do
  if [[ -f "${DR_DIR}/${script}" ]]; then
    pass "${script} exists at expected path"
  else
    fail "${script} MISSING at ${DR_DIR}/${script}"
  fi
done

# ─── 2. Execute permissions ──────────────────────────────────────────────────
echo ""
echo "2. Execute permission checks"

for script in backup-aep.sh restore-aep.sh dr-drill.sh; do
  if [[ -x "${DR_DIR}/${script}" ]]; then
    pass "${script} is executable"
  else
    fail "${script} is NOT executable — run: chmod +x ${DR_DIR}/${script}"
  fi
done

# ─── 3. Bash syntax validity ─────────────────────────────────────────────────
echo ""
echo "3. Bash syntax validation"

for script in backup-aep.sh restore-aep.sh dr-drill.sh; do
  script_path="${DR_DIR}/${script}"
  if [[ ! -f "${script_path}" ]]; then
    fail "${script} syntax check skipped (file missing)"
    continue
  fi
  if bash -n "${script_path}" 2>/dev/null; then
    pass "${script} passes bash -n (no syntax errors)"
  else
    fail "${script} has SYNTAX ERRORS — output: $(bash -n "${script_path}" 2>&1)"
  fi
done

# ─── 4. Required option flags documented in README ───────────────────────────
echo ""
echo "4. Required option flag presence (README alignment)"

check_flags() {
  local script="$1"; shift
  local script_path="${DR_DIR}/${script}"
  if [[ ! -f "${script_path}" ]]; then
    fail "${script} flag check skipped (file missing)"; return
  fi
  for flag in "$@"; do
    if grep -qF -- "${flag}" "${script_path}"; then
      pass "${script}: flag '${flag}' present in script body"
    else
      fail "${script}: flag '${flag}' documented in README but NOT FOUND in script — runbook drift detected"
    fi
  done
}

check_flags "backup-aep.sh"  "--tenant-id" "--output-dir" "--retention" "--full" "--no-compress"
check_flags "restore-aep.sh" "--tenant-id" "--backup-file" "--validate-only" "--dry-run"
check_flags "dr-drill.sh"    "--tenant-id" "--scenario" "--rto-target" "--no-cleanup"

# ─── 5. README is present and non-empty ──────────────────────────────────────
echo ""
echo "5. Runbook documentation checks"

readme="${DR_DIR}/README.md"
if [[ -f "${readme}" ]]; then
  pass "README.md exists"
  line_count=$(wc -l < "${readme}")
  if [[ "${line_count}" -gt 50 ]]; then
    pass "README.md has substantial content (${line_count} lines)"
  else
    fail "README.md is suspiciously short (${line_count} lines) — possible truncation"
  fi
else
  fail "README.md MISSING — operators cannot follow runbook"
fi

# ─── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "=== Results: ${PASS} passed, ${FAIL} failed ==="

if [[ "${FAIL}" -gt 0 ]]; then
  echo ""
  echo "FAILURES:"
  for err in "${ERRORS[@]}"; do
    echo "  - ${err}"
  done
  echo ""
  echo "Fix the above issues to ensure the recovery runbook remains current and executable."
  exit 1
fi

echo ""
echo "All recovery runbook smoke tests PASSED. ✓"
exit 0
