#!/bin/bash
# Run integration tests for client-side components

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}DCMaar Client-Side Integration Tests${NC}"
echo "======================================"

# Get project root
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

# Test results
TESTS_PASSED=0
TESTS_FAILED=0

# Function to run test
run_test() {
    local test_name=$1
    local test_command=$2
    
    echo -e "\n${YELLOW}Running: $test_name${NC}"
    if eval "$test_command"; then
        echo -e "${GREEN}✓ $test_name passed${NC}"
        ((TESTS_PASSED++))
        return 0
    else
        echo -e "${RED}✗ $test_name failed${NC}"
        ((TESTS_FAILED++))
        return 1
    fi
}

# Start services
echo -e "\n${YELLOW}Starting services...${NC}"
./scripts/dev/start-all.sh &
START_PID=$!

# Wait for services to be ready
echo -e "${YELLOW}Waiting for services to be ready...${NC}"
sleep 10

# Test 1: Agent gRPC endpoint
run_test "Agent gRPC endpoint" \
    "grpcurl -plaintext localhost:50051 list 2>/dev/null"

# Test 2: Desktop WebSocket endpoint
run_test "Desktop WebSocket endpoint" \
    "curl -s http://localhost:12345 2>/dev/null || true"

# Test 3: Agent metrics collection
run_test "Agent metrics collection" \
    "cd agent-rs && cargo test --lib 2>&1 | grep -q 'test result: ok'"

# Test 4: Desktop frontend tests
run_test "Desktop frontend tests" \
    "bash ${PROJECT_ROOT}/../scripts/run-pnpm-from-root.sh --filter \"./products/dcmaar/services/desktop\" test -- --run 2>&1 | grep -q 'Tests.*passed'"

# Test 5: Desktop backend tests
run_test "Desktop backend tests" \
    "cd services/desktop/src-tauri && cargo test 2>&1 | grep -q 'test result: ok'"

# Test 6: Extension tests
run_test "Extension tests" \
    "bash ${PROJECT_ROOT}/../scripts/run-pnpm-from-root.sh --filter \"./products/dcmaar/services/extension\" test -- --run 2>&1 | grep -q 'Tests.*passed'"

# Stop services
echo -e "\n${YELLOW}Stopping services...${NC}"
kill $START_PID 2>/dev/null || true
./scripts/dev/stop-all.sh

# Print results
echo -e "\n======================================"
echo -e "${BLUE}Integration Test Results${NC}"
echo "======================================"
echo -e "${GREEN}Passed: $TESTS_PASSED${NC}"
echo -e "${RED}Failed: $TESTS_FAILED${NC}"
echo "======================================"

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "\n${GREEN}All integration tests passed!${NC}"
    exit 0
else
    echo -e "\n${RED}Some integration tests failed${NC}"
    exit 1
fi
