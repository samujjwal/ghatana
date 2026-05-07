#!/usr/bin/env bash
set -euo pipefail

# ARCH-P1-003: fail CI when production code includes placeholder/stub markers.
# Scope is intentionally limited to Data Cloud + AEP production paths.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ALLOWLIST_FILE="${ROOT_DIR}/config/ci/production-placeholder-allowlist.txt"
TMP_HITS="$(mktemp)"
TMP_FILTERED="$(mktemp)"
trap 'rm -f "${TMP_HITS}" "${TMP_FILTERED}"' EXIT

TARGET_DIRS=(
  "${ROOT_DIR}/products/data-cloud"
  "${ROOT_DIR}/products/data-cloud/planes/action"
)

find "${TARGET_DIRS[@]}" -type f \
  \( -name "*.java" -o -name "*.kt" -o -name "*.kts" -o -name "*.ts" -o -name "*.tsx" -o -name "*.js" -o -name "*.mjs" -o -name "*.cjs" \) \
  ! -path "*/src/test/*" \
  ! -path "*/src/integrationTest/*" \
  ! -path "*/__tests__/*" \
  ! -path "*/tests/*" \
  ! -path "*/test/*" \
  ! -path "*/build/*" \
  ! -path "*/dist/*" \
  ! -path "*/node_modules/*" \
  ! -path "*/docs/*" \
  ! -path "*/docs-generated/*" \
  ! -name "*.test.*" \
  ! -name "*.spec.*" \
  -print0 | while IFS= read -r -d '' file; do
    grep -nE "^[[:space:]]*(//|/\*|\*)[[:space:]]*(TODO|FIXME|HACK|TEMP|XXX)\b" "$file" >> "${TMP_HITS}" || true
    grep -nEi "^[[:space:]]*(//|/\*|\*).*(\bstub\b|\bplaceholder\b|\bmock\b|\bnot implemented\b|\bcoming soon\b|\bdemo-only\b)" "$file" >> "${TMP_HITS}" || true
  done

sort -u "${TMP_HITS}" > "${TMP_FILTERED}" || true

if [[ -f "${ALLOWLIST_FILE}" ]]; then
  while IFS= read -r allow; do
    [[ -z "${allow}" || "${allow}" =~ ^# ]] && continue
    grep -Fv "${allow}" "${TMP_FILTERED}" > "${TMP_FILTERED}.next" || true
    mv "${TMP_FILTERED}.next" "${TMP_FILTERED}"
  done < "${ALLOWLIST_FILE}"
fi

if [[ -s "${TMP_FILTERED}" ]]; then
  echo "❌ Production placeholder scan failed."
  echo "Found forbidden placeholder/stub markers in production code:"
  cat "${TMP_FILTERED}"
  echo
  echo "If a finding is temporary and explicitly approved, add a narrow substring allowlist entry in ${ALLOWLIST_FILE}."
  exit 1
fi

echo "✅ Production placeholder scan passed (Data Cloud + AEP production paths)."
