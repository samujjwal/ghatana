#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# audit-tenant-isolation.sh - Data-Cloud tenant scope static audit
#
# Detects Java repository/handler classes that appear to access persistence
# without an explicit tenant scope signal.
#
# Usage:
#   ./products/data-cloud/scripts/audit-tenant-isolation.sh [--warn-only]
#
# Exit codes:
#   0 - no findings (or warn-only mode)
#   1 - findings detected
#   2 - invalid invocation or missing required folders
# -----------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRODUCT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
WARN_ONLY=false

for arg in "$@"; do
  case "$arg" in
    --warn-only) WARN_ONLY=true ;;
    *)
      echo "Unknown argument: $arg" >&2
      exit 2
      ;;
  esac
done

SCAN_ROOTS=(
  "${PRODUCT_DIR}/platform"
  "${PRODUCT_DIR}/launcher"
)

for root in "${SCAN_ROOTS[@]}"; do
  if [[ ! -d "$root" ]]; then
    echo "ERROR: required scan root missing: $root" >&2
    exit 2
  fi
done

TMP_CANDIDATES="$(mktemp)"
TMP_FINDINGS="$(mktemp)"
trap 'rm -f "$TMP_CANDIDATES" "$TMP_FINDINGS"' EXIT

find "${SCAN_ROOTS[@]}" -type f -name "*.java" \
  | grep -E "(Repository|Store|Handler|Dao|Service)\.java$" \
  | sort -u > "$TMP_CANDIDATES"

CANDIDATE_COUNT="$(wc -l < "$TMP_CANDIDATES" | tr -d ' ')"

if [[ "$CANDIDATE_COUNT" == "0" ]]; then
  echo "No Java candidate files found for tenant audit."
  exit 0
fi

while IFS= read -r file; do
  case "$file" in
    */HealthHandler.java)
      continue
      ;;
  esac

  if ! grep -Eiq "tenantId|TenantContext|X-Tenant-Id|setCurrentTenantId|getCurrentTenantId" "$file"; then
    echo "$file" >> "$TMP_FINDINGS"
  fi
done < "$TMP_CANDIDATES"

FINDING_COUNT="$(wc -l < "$TMP_FINDINGS" | tr -d ' ')"

if [[ "$FINDING_COUNT" == "0" ]]; then
  echo "✅ Tenant isolation static audit passed."
  echo "   Candidate files checked: $CANDIDATE_COUNT"
  exit 0
fi

echo ""
echo "⚠️  Files missing obvious tenant scope signal:"
while IFS= read -r file; do
  echo "   - ${file#${PRODUCT_DIR}/}"
done < "$TMP_FINDINGS"

echo ""
if [[ "$WARN_ONLY" == "true" ]]; then
  echo "⚠️  Tenant audit produced warnings (--warn-only enabled)."
  exit 0
fi

echo "❌ Tenant isolation audit failed. Add tenant scope in the files above."
exit 1
