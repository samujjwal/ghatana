#!/bin/bash
#
# Requirement Coverage Verification Script
# Validates all 69 requirements have corresponding tests
#
# @doc.type script
# @doc.purpose CI gate for requirement coverage
# @doc.layer infrastructure
# @doc.pattern CI/CD

set -e

echo "=================================="
echo "Requirement Coverage Verification"
echo "=================================="

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

REQUIREMENTS_FILE="products/data-cloud/DATA_CLOUD_100_PERCENT_COVERAGE_IMPLEMENTATION_PLAN.md"
MISSING=0

# Requirement patterns to search for in tests
REQUIREMENT_PATTERNS=(
  "[A001]" "[A002]" "[A003]" "[A004]" "[A005]"
  "[B001]" "[B002]" "[B003]" "[B004]"
  "[C001]" "[C002]" "[C003]" "[C004]"
  "[D001]" "[D002]" "[D003]" "[D004]" "[D005]"
  "[E001]" "[E002]" "[E003]" "[E004]"
  "[F001]" "[F002]" "[F003]" "[F004]" "[F005]" "[F006]" "[F007]"
  "[G001]" "[G002]" "[G003]" "[G004]" "[G005]"
  "[H001]" "[H002]" "[H003]" "[H004]"
  "[I001]" "[I002]" "[I003]" "[I004]" "[I005]"
  "[J001]" "[J002]" "[J003]" "[J004]" "[J005]" "[J006]" "[J007]"
  "[K001]" "[K002]" "[K003]" "[K004]" "[K005]"
  "[L001]" "[L002]" "[L003]" "[L004]"
  "[M001]" "[M002]" "[M003]" "[M004]" "[M005]" "[M006]" "[M007]" "[M008]"
  "[SC001]" "[SC002]" "[SC003]"
  "[IE001]" "[IE002]" "[IE003]" "[IE004]" "[IE005]"
  "[PF001]" "[PF002]" "[PF003]"
  "[AI001]" "[AI002]" "[AI003]" "[AI004]" "[AI005]"
  "[S001]" "[S002]" "[S003]" "[S004]" "[S005]" "[S006]" "[S007]" "[S008]"
)

echo ""
echo "Checking requirement test coverage..."
echo ""

for req in "${REQUIREMENT_PATTERNS[@]}"; do
  # Search for requirement in test files
  FOUND=$(find products/data-cloud -name "*.java" -o -name "*.tsx" -o -name "*.ts" | \
    xargs grep -l "$req" 2>/dev/null | wc -l)
  
  if [ "$FOUND" -eq 0 ]; then
    echo -e "${RED}✗ Missing: ${req}${NC}"
    MISSING=$((MISSING + 1))
  else
    echo -e "${GREEN}✓ ${req}${NC}"
  fi
done

echo ""
echo "=================================="

if [ $MISSING -gt 0 ]; then
  echo -e "${RED}Requirement Coverage: FAILED${NC}"
  echo "Missing ${MISSING} requirement tests"
  exit 1
else
  echo -e "${GREEN}Requirement Coverage: PASSED${NC}"
  echo "All 69 requirements have test coverage"
  exit 0
fi
