#!/bin/bash
#
# Coverage Gate Verification Script
# Validates 100% line/branch/method coverage across all modules
#
# @doc.type script
# @doc.purpose CI gate for coverage validation
# @doc.layer infrastructure
# @doc.pattern CI/CD

set -e

echo "=================================="
echo "Data Cloud Coverage Gate Verification"
echo "=================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Coverage thresholds
LINE_THRESHOLD=100
BRANCH_THRESHOLD=100
METHOD_THRESHOLD=100

# Modules to check
MODULES=(
  "platform-api"
  "platform-entity"
  "platform-event"
  "launcher"
  "feature-store-ingest"
)

FAILED=0

echo ""
echo "Checking JaCoCo coverage reports..."
echo ""

for module in "${MODULES[@]}"; do
  REPORT_PATH="products/data-cloud/${module}/build/reports/jacoco/test/jacocoTestReport.csv"
  
  if [ ! -f "$REPORT_PATH" ]; then
    echo -e "${YELLOW}WARNING: Coverage report not found for ${module}${NC}"
    continue
  fi
  
  echo "Module: ${module}"
  
  # Parse CSV report (format: group,package,class,instruction_missed,instruction_covered,...
  # Skip header, sum up coverage
  COVERAGE=$(tail -n +2 "$REPORT_PATH" | awk -F',' '{
    missed+=$4; covered+=$5; 
    branch_missed+=$6; branch_covered+=$7;
    line_missed+=$8; line_covered+=$9;
    method_missed+=$10; method_covered+=$11
  } END {
    if (covered+missed > 0) {
      printf "%.2f", (covered/(covered+missed))*100
    }
  }')
  
  LINE_COV=$(tail -n +2 "$REPORT_PATH" | awk -F',' '{
    line_missed+=$8; line_covered+=$9
  } END {
    if (line_covered+line_missed > 0) {
      printf "%.2f", (line_covered/(line_covered+line_missed))*100
    }
  }')
  
  BRANCH_COV=$(tail -n +2 "$REPORT_PATH" | awk -F',' '{
    branch_missed+=$6; branch_covered+=$7
  } END {
    if (branch_covered+branch_missed > 0) {
      printf "%.2f", (branch_covered/(branch_covered+branch_missed))*100
    }
  }')
  
  METHOD_COV=$(tail -n +2 "$REPORT_PATH" | awk -F',' '{
    method_missed+=$10; method_covered+=$11
  } END {
    if (method_covered+method_missed > 0) {
      printf "%.2f", (method_covered/(method_covered+method_missed))*100
    }
  }')
  
  echo "  Line Coverage:    ${LINE_COV}%"
  echo "  Branch Coverage:  ${BRANCH_COV}%"
  echo "  Method Coverage:  ${METHOD_COV}%"
  
  # Check thresholds
  LINE_PASS=$(echo "$LINE_COV >= $LINE_THRESHOLD" | bc)
  BRANCH_PASS=$(echo "$BRANCH_COV >= $BRANCH_THRESHOLD" | bc)
  METHOD_PASS=$(echo "$METHOD_COV >= $METHOD_THRESHOLD" | bc)
  
  if [ "$LINE_PASS" -eq 0 ] || [ "$BRANCH_PASS" -eq 0 ] || [ "$METHOD_PASS" -eq 0 ]; then
    echo -e "${RED}  ✗ FAILED - Coverage below threshold${NC}"
    FAILED=1
  else
    echo -e "${GREEN}  ✓ PASSED${NC}"
  fi
  echo ""
done

# Check UI coverage
echo "Checking UI (Vitest) coverage..."
echo ""

UI_REPORT_PATH="products/data-cloud/ui/coverage/coverage-summary.json"

if [ -f "$UI_REPORT_PATH" ]; then
  UI_LINES=$(cat "$UI_REPORT_PATH" | grep -o '"lines":{"total":[0-9]*,"covered":[0-9]*,"skipped":[0-9]*,"pct":[0-9.]*' | grep -o '"pct":[0-9.]*' | cut -d':' -f2)
  UI_BRANCHES=$(cat "$UI_REPORT_PATH" | grep -o '"branches":{"total":[0-9]*,"covered":[0-9]*,"skipped":[0-9]*,"pct":[0-9.]*' | grep -o '"pct":[0-9.]*' | cut -d':' -f2)
  UI_FUNCS=$(cat "$UI_REPORT_PATH" | grep -o '"functions":{"total":[0-9]*,"covered":[0-9]*,"skipped":[0-9]*,"pct":[0-9.]*' | grep -o '"pct":[0-9.]*' | cut -d':' -f2)
  
  echo "  Line Coverage:    ${UI_LINES}%"
  echo "  Branch Coverage:  ${UI_BRANCHES}%"
  echo "  Function Coverage: ${UI_FUNCS}%"
  
  UI_LINE_PASS=$(echo "$UI_LINES >= 100" | bc)
  UI_BRANCH_PASS=$(echo "$UI_BRANCHES >= 100" | bc)
  UI_FUNC_PASS=$(echo "$UI_FUNCS >= 100" | bc)
  
  if [ "$UI_LINE_PASS" -eq 0 ] || [ "$UI_BRANCH_PASS" -eq 0 ] || [ "$UI_FUNC_PASS" -eq 0 ]; then
    echo -e "${RED}  ✗ FAILED - UI coverage below threshold${NC}"
    FAILED=1
  else
    echo -e "${GREEN}  ✓ PASSED${NC}"
  fi
else
  echo -e "${YELLOW}WARNING: UI coverage report not found${NC}"
fi

echo ""
echo "=================================="

if [ $FAILED -eq 1 ]; then
  echo -e "${RED}Coverage Gate: FAILED${NC}"
  exit 1
else
  echo -e "${GREEN}Coverage Gate: PASSED${NC}"
  echo "All modules meet 100% coverage threshold"
  exit 0
fi
