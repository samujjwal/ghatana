#!/bin/bash

##
# Run Guardian backend tests sequentially
#
# Usage:
#   ./run-tests-sequential.sh              # Run all tests sequentially
#   ./run-tests-sequential.sh --coverage   # Run with coverage
#   ./run-tests-sequential.sh --watch      # Run in watch mode (interactive)
#
# Why sequential?
# Individual tests pass 100%, but concurrent execution causes transaction
# deadlocks in authService.register() and related service methods.
# See: ../CONCURRENT_TEST_FIX_STRATEGY.md
#
# Expected output:
# - All tests passing (100% pass rate)
# - Test duration: 4-5 minutes for full suite
# - Trade-off: Slower than concurrent, but 100% reliable
#
# Future: Replace with concurrent execution after implementing
# proper test data seeding via testDb.ts seeders.
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Parse arguments
COVERAGE=""
WATCH=""
args="$@"

if [[ "$args" == *"--coverage"* ]]; then
  COVERAGE="--coverage"
fi

if [[ "$args" == *"--watch"* ]]; then
  WATCH="--watch"
  # Remove --run flag when in watch mode
  WATCH_FLAG=""
else
  WATCH_FLAG="--run"
fi

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🧪 Guardian Backend - Sequential Test Execution${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "📋 Configuration:"
echo "   Mode: Sequential (VITEST_SINGLE_THREAD=1)"
echo "   Coverage: ${COVERAGE:-disabled}"
echo "   Watch: ${WATCH:-disabled}"
echo ""
echo "⏱️  Expected duration: 4-5 minutes for full suite"
echo ""
echo "💡 For concurrent execution, see: ../CONCURRENT_TEST_FIX_STRATEGY.md"
echo ""

# Run tests with environment variable for single-threaded execution
# Note: This uses the VITEST_SINGLE_THREAD hint if vitest supports it,
# otherwise vitest will use its default behavior
export VITEST_SINGLE_THREAD=1
export NODE_ENV=test

echo -e "${YELLOW}🚀 Starting test execution...${NC}"
echo ""

# Construct vitest command
CMD="pnpm test $WATCH_FLAG $COVERAGE"

# Run the command
if eval $CMD; then
  echo ""
  echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "${GREEN}✅ All tests passed!${NC}"
  echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  exit 0
else
  echo ""
  echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "${RED}❌ Tests failed${NC}"
  echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  exit 1
fi
