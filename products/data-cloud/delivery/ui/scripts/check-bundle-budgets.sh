#!/usr/bin/env bash
# check-bundle-budgets.sh — CI bundle-budget gate for Data Cloud UI (DC-P1-477)
#
# Builds the Vite project and validates that no JS chunk exceeds the defined
# budget.  Exits non-zero if any chunk is over budget, so CI fails fast.
#
# Usage:
#   bash scripts/check-bundle-budgets.sh [--skip-build]
#
# Environment overrides:
#   CHUNK_BUDGET_KB   — max size per individual chunk in kB (default: 600)
#   VENDOR_BUDGET_KB  — max size for the vendor chunk in kB (default: 1000)
#   DIST_DIR          — output directory to inspect (default: dist/assets)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

CHUNK_BUDGET_KB="${CHUNK_BUDGET_KB:-600}"
VENDOR_BUDGET_KB="${VENDOR_BUDGET_KB:-1000}"
DIST_DIR="${DIST_DIR:-${ROOT_DIR}/dist/assets}"

SKIP_BUILD=false
for arg in "$@"; do
  [[ "$arg" == "--skip-build" ]] && SKIP_BUILD=true
done

# ── Build ──────────────────────────────────────────────────────────────────────
if [[ "$SKIP_BUILD" == "false" ]]; then
  echo "==> Building Data Cloud UI…"
  (cd "${ROOT_DIR}" && pnpm build)
fi

# ── Validate ───────────────────────────────────────────────────────────────────
if [[ ! -d "${DIST_DIR}" ]]; then
  echo "ERROR: dist/assets directory not found at ${DIST_DIR}" >&2
  echo "       Run without --skip-build or run 'pnpm build' first." >&2
  exit 1
fi

PASS=0
FAIL=0
WARNINGS=""

while IFS= read -r -d '' chunk; do
  filename="$(basename "${chunk}")"
  size_bytes="$(wc -c < "${chunk}")"
  size_kb=$(( size_bytes / 1024 ))

  # vendor chunk gets its own (larger) budget
  if [[ "${filename}" == vendor* ]]; then
    budget_kb="${VENDOR_BUDGET_KB}"
  else
    budget_kb="${CHUNK_BUDGET_KB}"
  fi

  if (( size_kb > budget_kb )); then
    WARNINGS="${WARNINGS}\n  OVER BUDGET  ${filename}  ${size_kb} kB  (limit: ${budget_kb} kB)"
    FAIL=$(( FAIL + 1 ))
  else
    echo "  OK           ${filename}  ${size_kb} kB  (limit: ${budget_kb} kB)"
    PASS=$(( PASS + 1 ))
  fi
done < <(find "${DIST_DIR}" -name "*.js" -print0)

echo ""
echo "Bundle budget check: ${PASS} OK, ${FAIL} OVER BUDGET"

if (( FAIL > 0 )); then
  echo ""
  echo "FAILED — the following chunks exceed their budget:"
  echo -e "${WARNINGS}"
  echo ""
  echo "To fix: split the chunk further in vite.config.ts (manualChunks)"
  echo "        or reduce imported dependencies."
  exit 1
fi

echo "All chunks within budget. ✓"
