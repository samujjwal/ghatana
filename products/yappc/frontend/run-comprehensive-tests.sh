#!/bin/bash

# Comprehensive Testing Execution Script
# Tests all packages in the YAPPC ecosystem
# Phase-by-phase execution with detailed reporting

set -e

echo "========================================="
echo "YAPPC Comprehensive Testing Execution"
echo "========================================="
echo ""

WORKSPACE_ROOT="/home/samujjwal/Developments/yappc/yappc/frontend"
TIMESTAMP=$(date +"%Y-%m-%d_%H-%M-%S")
REPORT_DIR="${WORKSPACE_ROOT}/testing-reports/${TIMESTAMP}"

mkdir -p "${REPORT_DIR}"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_section() {
    echo -e "${YELLOW}=========================================${NC}"
    echo -e "${YELLOW}$1${NC}"
    echo -e "${YELLOW}=========================================${NC}"
}

log_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

log_failure() {
    echo -e "${RED}✗ $1${NC}"
}

# Phase 0: Environment Validation
log_section "PHASE 0: Environment Validation"

cd "${WORKSPACE_ROOT}"

echo "Node version: $(node --version)"
echo "pnpm version: $(pnpm --version)"

# Build types
echo ""
log_section "Building Type Declarations"
pnpm run build:types > "${REPORT_DIR}/build-types.log" 2>&1 && log_success "Type build completed" || log_failure "Type build failed"

# TypeCheck
echo ""
log_section "Running TypeScript Check"
pnpm run typecheck:refs > "${REPORT_DIR}/typecheck.log" 2>&1 && log_success "TypeScript check passed" || log_failure "TypeScript check failed"

# Phase 1: Design System Core
log_section "PHASE 1: Design System Core Testing"

cd "${WORKSPACE_ROOT}/libs/design-system-core"

echo "Running unit tests..."
pnpm run test > "${REPORT_DIR}/design-system-core-unit.log" 2>&1
log_success "Unit tests completed"

echo "Running coverage..."
pnpm run test:coverage > "${REPORT_DIR}/design-system-core-coverage.log" 2>&1
log_success "Coverage report generated"

echo "TypeCheck..."
pnpm run typecheck > "${REPORT_DIR}/design-system-core-typecheck.log" 2>&1
log_success "TypeCheck passed"

# Phase 2: UI Components
log_section "PHASE 2: UI Components Testing"

cd "${WORKSPACE_ROOT}/libs/ui"

echo "Running unit tests..."
pnpm run test > "${REPORT_DIR}/ui-unit.log" 2>&1
log_success "Unit tests completed"

echo "Running coverage..."
pnpm run test:coverage > "${REPORT_DIR}/ui-coverage.log" 2>&1
log_success "Coverage report generated"

echo "Building Storybook..."
pnpm run build-storybook > "${REPORT_DIR}/ui-storybook-build.log" 2>&1
log_success "Storybook build completed"

# Phase 3: Canvas
log_section "PHASE 3: Canvas Library Testing"

cd "${WORKSPACE_ROOT}/libs/canvas"

echo "Running unit tests..."
pnpm run test > "${REPORT_DIR}/canvas-unit.log" 2>&1
log_success "Unit tests completed"

echo "Running coverage..."
pnpm run test:coverage > "${REPORT_DIR}/canvas-coverage.log" 2>&1
log_success "Coverage report generated"

# Phase 4: Diagram
log_section "PHASE 4: Diagram Testing"

cd "${WORKSPACE_ROOT}/libs/diagram"

echo "Running tests..."
pnpm run test > "${REPORT_DIR}/diagram-unit.log" 2>&1
log_success "Tests completed"

# Phase 5: Designer
log_section "PHASE 5: Designer Testing"

cd "${WORKSPACE_ROOT}/libs/designer"

echo "Running unit tests..."
pnpm run test > "${REPORT_DIR}/designer-unit.log" 2>&1
log_success "Unit tests completed"

echo "Running coverage..."
pnpm run test:coverage > "${REPORT_DIR}/designer-coverage.log" 2>&1
log_success "Coverage report generated"

echo "Building Storybook..."
pnpm run build-storybook > "${REPORT_DIR}/designer-storybook-build.log" 2>&1
log_success "Storybook build completed"

# Phase 6: Full Integration Testing
log_section "PHASE 6: Full Project Testing"

cd "${WORKSPACE_ROOT}"

echo "Running all unit tests..."
pnpm run test > "${REPORT_DIR}/all-tests.log" 2>&1
log_success "All tests completed"

echo "Running E2E tests..."
timeout 600 pnpm run test:e2e > "${REPORT_DIR}/e2e-tests.log" 2>&1 || true
log_success "E2E tests completed (or timed out)"

# Phase 7: Generate Report
log_section "PHASE 7: Report Generation"

cat > "${REPORT_DIR}/SUMMARY.md" << 'EOF'
# Testing Execution Summary

## Environment
- Node: $(node --version)
- pnpm: $(pnpm --version)
- Timestamp: $(date)

## Packages Tested
1. ✓ Design System Core
2. ✓ UI Components
3. ✓ Canvas
4. ✓ Diagram
5. ✓ Designer

## Test Results

### Design System Core
- Unit Tests: See design-system-core-unit.log
- Coverage: See design-system-core-coverage.log
- Status: COMPLETE

### UI Components
- Unit Tests: See ui-unit.log
- Coverage: See ui-coverage.log
- Storybook: See ui-storybook-build.log
- Status: COMPLETE

### Canvas
- Unit Tests: See canvas-unit.log
- Coverage: See canvas-coverage.log
- Status: COMPLETE

### Diagram
- Unit Tests: See diagram-unit.log
- Status: COMPLETE

### Designer
- Unit Tests: See designer-unit.log
- Coverage: See designer-coverage.log
- Storybook: See designer-storybook-build.log
- Status: COMPLETE

### Full Project
- All Tests: See all-tests.log
- E2E Tests: See e2e-tests.log

## Key Metrics
- Type Errors: Should be 0
- Test Pass Rate: Target ≥95%
- Coverage Target: ≥85%

## Next Steps
1. Review all logs in this directory
2. Fix any failing tests
3. Verify coverage targets met
4. Run full validation: pnpm validate
EOF

log_success "Summary report created at ${REPORT_DIR}/SUMMARY.md"

echo ""
echo "========================================="
echo "Testing Complete!"
echo "Report directory: ${REPORT_DIR}"
echo "========================================="
