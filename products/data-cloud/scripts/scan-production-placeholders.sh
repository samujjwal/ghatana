#!/usr/bin/env bash
# scan-production-placeholders.sh
#
# Scans the Data Cloud codebase for stub/placeholder/demo code that must not
# appear in production builds. Fails with a non-zero exit code and prints
# each violation so CI blocks the merge.
#
# Usage:
#   ./scripts/scan-production-placeholders.sh
#   ALLOWLIST=config/production-stub-allowlist.json ./scripts/scan-production-placeholders.sh
#
# The ALLOWLIST file (JSON array of {"pattern","file","reason","owner","expires"})
# suppresses specific findings. Entries without an "owner" or with an "expires"
# date in the past cause the script itself to fail.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRODUCT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${PRODUCT_ROOT}/../.." && pwd)"
ALLOWLIST="${ALLOWLIST:-${REPO_ROOT}/config/production-stub-allowlist.json}"

VIOLATIONS=0
ALLOWLIST_ERRORS=0

# ─── Colour output ────────────────────────────────────────────────────────────
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

fail() { echo -e "${RED}[FAIL]${NC} $*" >&2; ((VIOLATIONS++)) || true; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }

# ─── Allowlist helpers ────────────────────────────────────────────────────────
is_allowlisted() {
  local pattern="$1"
  local file="$2"
  if [[ -f "$ALLOWLIST" ]] && command -v jq >/dev/null 2>&1; then
    jq -e --arg p "$pattern" --arg f "$file" \
      'any(. ; .pattern == $p and (.file == $f or .file == "*"))' \
      "$ALLOWLIST" >/dev/null 2>&1
    return $?
  fi
  return 1
}

validate_allowlist() {
  if [[ ! -f "$ALLOWLIST" ]]; then
    warn "No allowlist found at ${ALLOWLIST} — all violations are hard failures."
    return
  fi
  if ! command -v jq >/dev/null 2>&1; then
    warn "jq not installed — allowlist entries will be ignored."
    return
  fi

  local today
  today="$(date -u +%Y-%m-%d)"

  # Check for entries missing owner
  local missing_owner
  missing_owner="$(jq -r '.[] | select(.owner == null or .owner == "") | .pattern' "$ALLOWLIST" 2>/dev/null || true)"
  if [[ -n "$missing_owner" ]]; then
    echo -e "${RED}[ALLOWLIST ERROR]${NC} The following allowlist entries are missing an 'owner' field:" >&2
    echo "$missing_owner" >&2
    ((ALLOWLIST_ERRORS++)) || true
  fi

  # Check for expired entries
  local expired
  expired="$(jq -r --arg today "$today" \
    '.[] | select(.expires != null and .expires != "" and .expires <= $today) | "\(.pattern) (expired: \(.expires))"' \
    "$ALLOWLIST" 2>/dev/null || true)"
  if [[ -n "$expired" ]]; then
    echo -e "${RED}[ALLOWLIST ERROR]${NC} The following allowlist entries have expired:" >&2
    echo "$expired" >&2
    ((ALLOWLIST_ERRORS++)) || true
  fi
}

# ─── Scan helpers ─────────────────────────────────────────────────────────────
# scan_pattern <label> <grep-regex> <include-glob> [<exclude-path-patterns...>]
scan_java() {
  local label="$1"
  local pattern="$2"
  shift 2
  local results
  results="$(grep -rn --include="*.java" -E "$pattern" \
    --exclude-dir="src/test" \
    --exclude-dir="build" \
    "$PRODUCT_ROOT" 2>/dev/null || true)"

  if [[ -n "$results" ]]; then
    while IFS= read -r line; do
      local file="${line%%:*}"
      local rel_file="${file#"$REPO_ROOT/"}"
      if ! is_allowlisted "$label" "$rel_file"; then
        fail "[Java/$label] $line"
      fi
    done <<< "$results"
  fi
}

scan_ts() {
  local label="$1"
  local pattern="$2"
  shift 2
  local results
  results="$(grep -rn --include="*.ts" --include="*.tsx" -E "$pattern" \
    --exclude-dir="__tests__" \
    --exclude-dir="node_modules" \
    --exclude-dir="dist" \
    --exclude-dir="build" \
    --exclude="*.test.ts" \
    --exclude="*.test.tsx" \
    --exclude="*.spec.ts" \
    --exclude="*.spec.tsx" \
    --exclude="setupTests.ts" \
    "$PRODUCT_ROOT/delivery/ui/src" 2>/dev/null || true)"

  if [[ -n "$results" ]]; then
    while IFS= read -r line; do
      local file="${line%%:*}"
      local rel_file="${file#"$REPO_ROOT/"}"
      if ! is_allowlisted "$label" "$rel_file"; then
        fail "[TS/$label] $line"
      fi
    done <<< "$results"
  fi
}

# ─── Validate allowlist before scanning ───────────────────────────────────────
validate_allowlist

# ─── Java production code checks ──────────────────────────────────────────────

# 1. TODO/FIXME in production Java source
scan_java "TODO_FIXME" '(//\s*(TODO|FIXME)|/\*\*?\s*(TODO|FIXME))'

# 2. Stub/placeholder/demo patterns in production Java source
scan_java "STUB_METHOD" '(throw new UnsupportedOperationException\(".*stub\|.*TODO\|.*not implemented")'
scan_java "STUB_RETURN"  '\breturn\s+(null|Collections\.emptyList\(\)|List\.of\(\)|Map\.of\(\));\s*//\s*(stub|placeholder|todo|fixme)'
scan_java "DEMO_DATA"    '(DEMO_|MOCK_|FAKE_|PLACEHOLDER_|HARDCODED_)[A-Z_]+\s*='
scan_java "UNSAFE_HACK"  '\b(Unsafe|Hack|Temp|Demo|Placeholder)(Service|Adapter|Client|Handler)\b'

# 3. println in production Java (use structured logging)
scan_java "SYSTEM_OUT_PRINTLN" 'System\.out\.println'

# ─── TypeScript/React production code checks ──────────────────────────────────

# 4. TODO/FIXME in production TS source
scan_ts "TODO_FIXME" '(//\s*(TODO|FIXME)|/\*[\s\S]*?(TODO|FIXME))'

# 5. console.log left in production TS source (console.error/warn are acceptable)
scan_ts "CONSOLE_LOG" '\bconsole\.log\('

# 6. Hardcoded localhost/127.0.0.1 URLs (should come from env/config)
scan_ts "HARDCODED_LOCALHOST" '"(http|ws)://(localhost|127\.0\.0\.1):[0-9]+'

# 7. MSW mock flag enabled at build time in non-test source
scan_ts "MSW_MOCK_FLAG" 'VITE_USE_MSW\s*=\s*["\x27]true["\x27]'

# 8. Imports from __mocks__ in production source
scan_ts "MOCK_IMPORT" "from\s+['\"].*/__mocks__/"

# 9. Static/demo/fixture data exported as real API responses
scan_ts "STATIC_FIXTURE_EXPORT" 'export\s+const\s+(MOCK_|FAKE_|DEMO_|STUB_|FIXTURE_)[A-Z_]+'

# 10. return [] / return null patterns in production API handlers
scan_java "EMPTY_RETURN_STUB" '^\s*return\s+(null|Promise\.of\(null\)|Promise\.of\(List\.of\(\)\));\s*$'

# ─── Summary ──────────────────────────────────────────────────────────────────
echo ""
if [[ $ALLOWLIST_ERRORS -gt 0 ]]; then
  echo -e "${RED}✗ Allowlist has ${ALLOWLIST_ERRORS} governance error(s). Fix the allowlist before proceeding.${NC}"
  exit 1
fi

if [[ $VIOLATIONS -gt 0 ]]; then
  echo -e "${RED}✗ ${VIOLATIONS} production placeholder violation(s) found.${NC}"
  echo "  Add an allowlist entry to config/production-stub-allowlist.json with:"
  echo "  { \"pattern\": \"<LABEL>\", \"file\": \"<relative/path>\", \"reason\": \"<why>\", \"owner\": \"@handle\", \"expires\": \"YYYY-MM-DD\" }"
  exit 1
fi

echo -e "✓ No production placeholder violations found."
