#!/bin/bash
#
# Flakiness Check Script
# Runs test suite multiple times to detect flaky tests
#
# @doc.type script
# @doc.purpose CI gate for test stability
# @doc.layer infrastructure
# @doc.pattern CI/CD

set -e

echo "=================================="
echo "Test Flakiness Check"
echo "=================================="

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

RUNS=10
FAILED_RUNS=0

echo ""
echo "Running test suite ${RUNS} times..."
echo ""

for i in $(seq 1 $RUNS); do
  echo "Run ${i}/${RUNS}..."
  
  # Run Java tests
  if ! ./gradlew :products:data-cloud:test --rerun-tasks -q > /tmp/test-run-${i}.log 2>&1; then
    echo -e "${RED}✗ Run ${i} FAILED${NC}"
    FAILED_RUNS=$((FAILED_RUNS + 1))
  else
    echo -e "${GREEN}✓ Run ${i} PASSED${NC}"
  fi
  
  # Run UI tests
  if ! (cd products/data-cloud/delivery/ui && npm test -- --run -q) > /tmp/ui-test-run-${i}.log 2>&1; then
    echo -e "${RED}✗ UI Run ${i} FAILED${NC}"
    FAILED_RUNS=$((FAILED_RUNS + 1))
  fi
done

echo ""
echo "=================================="
echo "Results:"
echo "  Total runs: ${RUNS}"
echo "  Failed runs: ${FAILED_RUNS}"
echo ""

if [ $FAILED_RUNS -gt 0 ]; then
  echo -e "${RED}Flakiness Check: FAILED${NC}"
  echo "Found ${FAILED_RUNS} flaky test failures"
  
  # Show which tests failed
  echo ""
  echo "Analyzing failures..."
  for i in $(seq 1 $RUNS); do
    if [ -f "/tmp/test-run-${i}.log" ]; then
      grep "FAILED" /tmp/test-run-${i}.log || true
    fi
  done
  
  exit 1
else
  echo -e "${GREEN}Flakiness Check: PASSED${NC}"
  echo "No flaky tests detected"
  exit 0
fi
