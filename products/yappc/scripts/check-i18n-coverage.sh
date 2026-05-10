#!/bin/bash
# i18n Coverage Check Script
#
# Validates that production UI strings use i18n keys instead of hardcoded strings.
# This script checks for:
# 1. Hardcoded strings in JSX (excluding test files)
# 2. Strings that should use i18n keys but don't
# 3. Locale-unsafe formatting patterns
#
# Usage: ./scripts/check-i18n-coverage.sh [--ci]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$(dirname "$SCRIPT_DIR")/frontend/web"
I18N_FILE="$FRONTEND_DIR/src/i18n/messages.ts"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

CI_MODE=false
if [[ "$1" == "--ci" ]]; then
  CI_MODE=true
fi

log_error() {
  echo -e "${RED}ERROR: $1${NC}"
  if [ "$CI_MODE" = true ]; then
    exit 1
  fi
}

log_warn() {
  echo -e "${YELLOW}WARNING: $1${NC}"
}

log_success() {
  echo -e "${GREEN}✓ $1${NC}"
}

# Check if i18n messages file exists
if [ ! -f "$I18N_FILE" ]; then
  log_error "i18n messages file not found at $I18N_FILE"
fi

# Patterns that indicate hardcoded strings in JSX
# These are patterns that should use i18n keys instead
HARDCODED_PATTERNS=(
  # Text content in JSX that's longer than 3 characters and not a variable
  ">[^<]\{4,\}<"
  # Button labels
  "Button>[^<]\{4,\}<"
  # Typography content
  "Typography[^>]*>[^<]\{4,\}<"
  # Labels in form fields
  "label=\"[^\"]\{4,\}\""
  # Placeholder text
  "placeholder=\"[^\"]\{4,\}\""
  # Error messages
  "error=\"[^\"]\{4,\}\""
  # Helper text
  "helperText=\"[^\"]\{4,\}\""
)

# Locale-unsafe formatting patterns
LOCALE_UNSAFE_PATTERNS=(
  # Hardcoded date formatting
  "toLocaleString\("
  # Hardcoded currency
  "\$[0-9]"
  # Hardcoded number formatting
  "toFixed\("
)

# Files to exclude (test files, stories, examples)
EXCLUDE_PATTERNS=(
  "__tests__"
  ".test."
  ".spec."
  ".stories."
  "stories/"
  "__examples__"
  ".examples."
  "node_modules"
)

# Function to check if a file should be excluded
should_exclude() {
  local file="$1"
  for pattern in "${EXCLUDE_PATTERNS[@]}"; do
    if [[ "$file" == *"$pattern"* ]]; then
      return 0
    fi
  done
  return 1
}

# Check for hardcoded strings in TSX files
check_hardcoded_strings() {
  local file="$1"
  local violations=0
  
  for pattern in "${HARDCODED_PATTERNS[@]}"; do
    # Use grep to find the pattern
    matches=$(grep -n "$pattern" "$file" 2>/dev/null || true)
    if [ -n "$matches" ]; then
      while IFS= read -r line; do
        violations=$((violations + 1))
        echo "  Line ${line%%:*}: ${line#*:}"
      done <<< "$matches"
    fi
  done
  
  echo "$violations"
}

# Check for locale-unsafe patterns
check_locale_unsafe() {
  local file="$1"
  local violations=0
  
  for pattern in "${LOCALE_UNSAFE_PATTERNS[@]}"; do
    matches=$(grep -n "$pattern" "$file" 2>/dev/null || true)
    if [ -n "$matches" ]; then
      while IFS= read -r line; do
        violations=$((violations + 1))
        echo "  Line ${line%%:*}: ${line#*:}"
      done <<< "$matches"
    fi
  done
  
  echo "$violations"
}

# Main validation
echo "Checking i18n coverage in production UI..."
echo ""

total_hardcoded_violations=0
total_locale_unsafe_violations=0
files_with_violations=0

# Find all TSX files in the web src directory
while IFS= read -r -d '' file; do
  if should_exclude "$file"; then
    continue
  fi
  
  hardcoded_violations=$(check_hardcoded_strings "$file")
  locale_unsafe_violations=$(check_locale_unsafe "$file")
  
  if [ "$hardcoded_violations" -gt 0 ] || [ "$locale_unsafe_violations" -gt 0 ]; then
    files_with_violations=$((files_with_violations + 1))
    echo "${file#$FRONTEND_DIR/src/}:"
    
    if [ "$hardcoded_violations" -gt 0 ]; then
      echo "  Hardcoded string violations:"
      check_hardcoded_strings "$file"
      total_hardcoded_violations=$((total_hardcoded_violations + hardcoded_violations))
    fi
    
    if [ "$locale_unsafe_violations" -gt 0 ]; then
      echo "  Locale-unsafe formatting violations:"
      check_locale_unsafe "$file"
      total_locale_unsafe_violations=$((total_locale_unsafe_violations + locale_unsafe_violations))
    fi
    
    echo ""
  fi
done < <(find "$FRONTEND_DIR/src" -name "*.tsx" -print0 2>/dev/null)

# Find all TS files in the web src directory
while IFS= read -r -d '' file; do
  if should_exclude "$file"; then
    continue
  fi
  
  hardcoded_violations=$(check_hardcoded_strings "$file")
  locale_unsafe_violations=$(check_locale_unsafe "$file")
  
  if [ "$hardcoded_violations" -gt 0 ] || [ "$locale_unsafe_violations" -gt 0 ]; then
    files_with_violations=$((files_with_violations + 1))
    echo "${file#$FRONTEND_DIR/src/}:"
    
    if [ "$hardcoded_violations" -gt 0 ]; then
      echo "  Hardcoded string violations:"
      check_hardcoded_strings "$file"
      total_hardcoded_violations=$((total_hardcoded_violations + hardcoded_violations))
    fi
    
    if [ "$locale_unsafe_violations" -gt 0 ]; then
      echo "  Locale-unsafe formatting violations:"
      check_locale_unsafe "$file"
      total_locale_unsafe_violations=$((total_locale_unsafe_violations + locale_unsafe_violations))
    fi
    
    echo ""
  fi
done < <(find "$FRONTEND_DIR/src" -name "*.ts" -print0 2>/dev/null)

echo ""
echo "Summary:"
echo "  Files with violations: $files_with_violations"
echo "  Total hardcoded string violations: $total_hardcoded_violations"
echo "  Total locale-unsafe formatting violations: $total_locale_unsafe_violations"
echo ""

if [ "$total_hardcoded_violations" -gt 0 ] || [ "$total_locale_unsafe_violations" -gt 0 ]; then
  log_error "i18n coverage check failed. Please use i18n keys for all production UI strings."
  echo ""
  echo "To fix these violations:"
  echo "1. Extract hardcoded strings to i18n keys in $I18N_FILE"
  echo "2. Use useI18n() hook and t() function in components"
  echo "3. Use locale-aware formatting functions (Intl.DateTimeFormat, Intl.NumberFormat, etc.)"
  exit 1
fi

log_success "i18n coverage check passed. All production UI strings use i18n keys."
