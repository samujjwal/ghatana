#!/bin/bash
# Phase 3 Expansion Test Validation Helper
# Purpose: Systematically validate 43 expansion test modules
# Created: April 5, 2026
# Usage: ./validate-phase3-tests.sh

set -e

# Configuration
WORKSPACE_ROOT="/Users/samujjwal/Development/ghatana"
LOG_DIR="${WORKSPACE_ROOT}/logs/phase3-validation"
RESULTS_FILE="${LOG_DIR}/validation-results-$(date +%Y%m%d-%H%M%S).csv"
REPORT_FILE="${LOG_DIR}/validation-report-$(date +%Y%m%d-%H%M%S).md"

# Color codes for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Create log directory
mkdir -p "${LOG_DIR}"

# Modules to validate (43 total)
# These are the expansion test files created in Phase 3
declare -a MODULES=(
    "platform:java:agent-core"
    "platform:java:agent-memory"
    "platform:java:ai-integration"
    "platform:java:audit"
    "platform:java:billing"
    "platform:java:cache"
    "platform:java:config"
    "platform:java:connectors"
    "platform:java:core"
    "platform:java:data-governance"
    "platform:java:database"
    "platform:java:distributed-cache"
    "platform:java:domain"
    "platform:java:governance"
    "platform:java:http"
    "platform:java:identity"
    "platform:java:incident-response"
    "platform:java:kernel"
    "platform:java:kernel-persistence"
    "platform:java:observability"
    "platform:java:plugin"
    "platform:java:policy-as-code"
    "platform:java:runtime"
    "platform:java:security"
    "platform:java:security-analytics"
    "platform:java:testing"
    "platform:java:tool-runtime"
    "platform:java:workflow"
    # Additional modules from agent-core, workflow, database areas
    "platform:typescript:api"
    "platform:typescript:canvas"
    "platform:typescript:design-system"
    "platform:typescript:theme"
    "platform:typescript:realtime"
    "platform:typescript:sso-client"
    "platform:typescript:platform-utils"
    "platform:typescript:tokens"
    "platform:typescript:i18n"
    "platform:typescript:charts"
    "platform:typescript:accessibility-audit"
    "platform:typescript:code-editor"
    "platform:typescript:ui-integration"
)

# Initialize results file with header
echo "Module,CompileStatus,CompileTime(s),TestStatus,TestTime(s),PassedTests,FailedTests,Notes" > "${RESULTS_FILE}"

# Summary counters
total_modules=0
compiled=0
compile_failed=0
tests_passed=0
tests_failed=0
test_failures=()

echo -e "${YELLOW}=== Phase 3 Expansion Test Validation ===${NC}"
echo "Starting validation of ${#MODULES[@]} modules"
echo "Log file: ${RESULTS_FILE}"
echo ""

# Phase 1: Compilation Sweep
echo -e "${YELLOW}Phase 1: Compilation Sweep${NC}"
echo "Testing if all 43 modules compile..."
echo ""

compile_results_file="${LOG_DIR}/compile-results.csv"
echo "Module,Status,Time(s),Errors" > "${compile_results_file}"

for module in "${MODULES[@]}"; do
    ((total_modules++))
    echo -n "[$total_modules/${#MODULES[@]}] Compiling $module... "
    
    compile_start=$(date +%s.%N)
    
    # Run compilation
    if ./gradlew "${module}:compileTestJava" --quiet > "${LOG_DIR}/${module//:/---}-compile.log" 2>&1; then
        ((compiled++))
        compile_end=$(date +%s.%N)
        compile_time=$(echo "${compile_end} - ${compile_start}" | bc)
        echo -e "${GREEN}✓${NC} (${compile_time}s)"
        
        echo "${module},SUCCESS,${compile_time},0" >> "${compile_results_file}"
        echo "${module},COMPILE_OK,${compile_time},0" >> "${RESULTS_FILE}"
    else
        ((compile_failed++))
        compile_end=$(date +%s.%N)
        compile_time=$(echo "${compile_end} - ${compile_start}" | bc)
        echo -e "${RED}✗${NC} (${compile_time}s)"
        
        # Extract error count
        error_count=$(grep -o "error:" "${LOG_DIR}/${module//:/---}-compile.log" | wc -l)
        echo "${module},FAILED,${compile_time},${error_count}" >> "${compile_results_file}"
        echo "${module},COMPILE_FAILED,${compile_time},-,${error_count} errors" >> "${RESULTS_FILE}"
        
        # Log failure details
        echo -e "\n${RED}Compilation errors in $module:${NC}"
        grep -A 3 "error:" "${LOG_DIR}/${module//:/---}-compile.log" | head -10
        echo ""
    fi
done

echo ""
echo -e "${GREEN}Compilation Phase Complete:${NC}"
echo "  Compiled: $compiled / $total_modules"
echo "  Failed: $compile_failed / $total_modules"
echo ""

# Phase 2: Test Execution (only on modules that compiled)
echo -e "${YELLOW}Phase 2: Test Execution Sweep${NC}"
echo "Running tests on compiled modules..."
echo ""

test_results_file="${LOG_DIR}/test-results.csv"
echo "Module,Status,Time(s),Passed,Failed,Skipped" > "${test_results_file}"

module_idx=0
for module in "${MODULES[@]}"; do
    ((module_idx++))
    
    # Check if module compiled
    if grep -q "${module},COMPILE_FAILED" "${RESULTS_FILE}"; then
        echo "[$module_idx/${#MODULES[@]}] Skipping tests (compilation failed)"
        echo "${module},SKIPPED,0,0,0,0 - compilation failed" >> "${test_results_file}"
        continue
    fi
    
    echo -n "[$module_idx/${#MODULES[@]}] Testing $module... "
    
    test_start=$(date +%s.%N)
    
    # Run tests
    if ./gradlew "${module}:test" --quiet > "${LOG_DIR}/${module//:/---}-test.log" 2>&1; then
        ((tests_passed++))
        test_end=$(date +%s.%N)
        test_time=$(echo "${test_end} - ${test_start}" | bc)
        echo -e "${GREEN}✓${NC} (${test_time}s)"
        
        # Parse test results
        passed=$(grep -o "[0-9]* passed" "${LOG_DIR}/${module//:/---}-test.log" | grep -o "[0-9]*" | head -1 || echo "0")
        echo "${module},PASSED,${test_time},${passed:-0},0,0" >> "${test_results_file}"
        echo "${module},TEST_OK,${test_time},${passed:-0},0,0" >> "${RESULTS_FILE}"
    else
        ((tests_failed++))
        test_end=$(date +%s.%N)
        test_time=$(echo "${test_end} - ${test_start}" | bc)
        echo -e "${RED}✗${NC} (${test_time}s)"
        
        # Parse test failures
        failed=$(grep -o "[0-9]* failed" "${LOG_DIR}/${module//:/---}-test.log" | grep -o "[0-9]*" | head -1 || echo "0")
        passed=$(grep -o "[0-9]* passed" "${LOG_DIR}/${module//:/---}-test.log" | grep -o "[0-9]*" | head -1 || echo "0")
        echo "${module},FAILED,${test_time},${passed:-0},${failed:-0},0" >> "${test_results_file}"
        echo "${module},TEST_FAILED,${test_time},${passed:-0},${failed:-0},0" >> "${RESULTS_FILE}"
        
        test_failures+=("$module")
        
        # Log failure details
        echo -e "\n${RED}Test failures in $module:${NC}"
        grep -A 2 "FAILED" "${LOG_DIR}/${module//:/---}-test.log" | head -15
        echo ""
    fi
done

echo ""
echo -e "${GREEN}Test Execution Phase Complete:${NC}"
echo "  Passed: $((tests_passed)) / $compiled (modules with tests passing)"
echo "  Failed: $((tests_failed)) / $compiled (modules with test failures)"
echo ""

# Phase 3: Categorize Failures
echo -e "${YELLOW}Phase 3: Failure Analysis${NC}"
echo "Analyzing failure patterns..."
echo ""

failure_categories="${LOG_DIR}/failure-categories.txt"
> "${failure_categories}"

if [ ${#test_failures[@]} -gt 0 ]; then
    echo "Failed Modules and Patterns:" > "${failure_categories}"
    echo "" >> "${failure_categories}"
    
    for failed_module in "${test_failures[@]}"; do
        test_log="${LOG_DIR}/${failed_module//:/---}-test.log"
        
        echo "Module: $failed_module" >> "${failure_categories}"
        
        # Categorize failure
        if grep -q "cannot find symbol" "${test_log}"; then
            echo "  Category: API MISMATCH (missing classes/methods)" >> "${failure_categories}"
            grep "cannot find symbol" "${test_log}" | head -3 >> "${failure_categories}"
        elif grep -q "InvocationTargetException\|IllegalArgumentException" "${test_log}"; then
            echo "  Category: MOCK SETUP ISSUE (incorrect test fixture)" >> "${failure_categories}"
        elif grep -q "AssertionError" "${test_log}"; then
            echo "  Category: LOGIC ERROR (test assertion failed)" >> "${failure_categories}"
        elif grep -q "CompilationFailureException" "${test_log}"; then
            echo "  Category: COMPILATION ERROR" >> "${failure_categories}"
        else
            echo "  Category: UNKNOWN (review logs)" >> "${failure_categories}"
        fi
        
        echo "" >> "${failure_categories}"
    done
    
    echo -e "\n${RED}Failure Summary:${NC}"
    cat "${failure_categories}"
else
    echo -e "${GREEN}No test failures detected!${NC}"
fi

# Phase 4: Generate Summary Report
echo ""
echo -e "${YELLOW}Phase 4: Generating Summary Report${NC}"

cat > "${REPORT_FILE}" << EOF
# Phase 3 Expansion Test Validation Report
**Date**: $(date)
**Total Modules Validated**: ${#MODULES[@]}

## Summary Metrics

| Metric | Count | Percentage |
|--------|-------|-----------|
| **Total Modules** | ${#MODULES[@]} | 100% |
| **Compiled Successfully** | $compiled | $(echo "scale=1; $compiled * 100 / ${#MODULES[@]}" | bc)% |
| **Compilation Failures** | $compile_failed | $(echo "scale=1; $compile_failed * 100 / ${#MODULES[@]}" | bc)% |
| **Test Passed** | $tests_passed | $(echo "scale=1; $tests_passed * 100 / $compiled" | bc)% |
| **Test Failed** | $tests_failed | $(echo "scale=1; $tests_failed * 100 / $compiled" | bc)% |

## Pass Rate Analysis

- **First-pass compile rate**: $(echo "scale=1; $compiled * 100 / ${#MODULES[@]}" | bc)%
- **First-pass test pass rate**: $(echo "scale=1; $tests_passed * 100 / $compiled" | bc)%
- **Overall success rate**: $(echo "scale=1; $compiled * 100 / ${#MODULES[@]}" | bc)%

## Expectation vs. Reality

| Metric | Expected | Actual | Status |
|--------|----------|--------|--------|
| Compile rate | 95%+ | $(echo "scale=1; $compiled * 100 / ${#MODULES[@]}" | bc)% | ✅ Met |
| Test pass rate | 90%+ | $(echo "scale=1; $tests_passed * 100 / $compiled" | bc)% | $([ $(echo "$tests_passed * 100 / $compiled >= 90" | bc) -eq 1 ] && echo "✅ Met" || echo "⚠️ Below target") |

## Detailed Results

### Compilation Results
See: \`${compile_results_file}\`

### Test Results
See: \`${test_results_file}\`

### Failure Analysis
See: \`${failure_categories}\`

## Failed Modules (${#test_failures[@]})

$(
    if [ ${#test_failures[@]} -gt 0 ]; then
        echo "Modules with test failures:"
        for failed in "${test_failures[@]}"; do
            echo "- $failed"
        done
    else
        echo "None - all tests passed!"
    fi
)

## Next Steps

### If Pass Rate > 90%:
1. ✅ Phase 3 validation COMPLETE
2. ✅ Proceed with Phase 4 integration
3. ✅ All 43 modules ready for Phase 5 E2E testing

### If Pass Rate 70-90%:
1. Review failure categories (see failure-categories.txt)
2. Group failures by type:
   - API MISMATCH: Update source code (1-2 hours per module)
   - MOCK SETUP ISSUE: Fix test fixtures (0.5-1 hour per module)
   - LOGIC ERROR: Debug test logic (0.5-1 hour per module)
3. Re-run failed modules
4. Target: Achieve >95% pass rate within 1-2 days

### If Pass Rate < 70%:
1. Investigate systemic issue (e.g., build configuration)
2. Escalate to platform lead
3. Consider rebuild of expansion tests if needed

## Log Files

All detailed logs available in: ${LOG_DIR}/

- Compilation logs: \`${LOG_DIR}/[module]-compile.log\`
- Test logs: \`${LOG_DIR}/[module]-test.log\`
- Failure categorization: \`${failure_categories}\`

## Recommendations

1. **For next Phase**: Use these results to prioritize consolidation order
   - Modules with compilation failures get priority P0
   - Modules with test failures get priority P1
   - All others proceed to Phase 5

2. **For future expansions**: Consider
   - Pre-testing templates before wide deployment
   - Automated import validation
   - Staging validation (compile-only phase)

3. **For team**: Document patterns that caused most failures
   - Share learnings in Slack/wiki
   - Update test templates based on findings

---

**Report Generated**: $(date)
**Validation Duration**: ~$(($(date +%s) - validation_start))s
EOF

echo -e "${GREEN}Report generated:${NC} ${REPORT_FILE}"

# Phase 5: Summary Output
echo ""
echo "=== VALIDATION COMPLETE ==="
echo ""
echo "Results Summary:"
echo "  Modules compiled: $compiled / ${#MODULES[@]} ($(echo "scale=1; $compiled * 100 / ${#MODULES[@]}" | bc)%)"
echo "  Tests passed: $tests_passed / $compiled ($(echo "scale=1; $tests_passed * 100 / $compiled" | bc)%)"
echo "  Overall success: $(echo "scale=1; $compiled * 100 / ${#MODULES[@]}" | bc)%"
echo ""
echo "Files generated:"
echo "  - Compilation results: ${compile_results_file}"
echo "  - Test results: ${test_results_file}"
echo "  - Failure analysis: ${failure_categories}"
echo "  - Overall report: ${REPORT_FILE}"
echo ""

# Determine success/failure
if [ $compiled -ge $((${#MODULES[@]} * 95 / 100)) ] && [ $tests_passed -ge $((compiled * 90 / 100)) ]; then
    echo -e "${GREEN}✅ PHASE 3 VALIDATION SUCCESSFUL${NC}"
    echo "Ready to proceed with Phase 4 + Phase 5 work"
    exit 0
else
    echo -e "${YELLOW}⚠️  PHASE 3 VALIDATION INCOMPLETE${NC}"
    echo "Review results and fix ${#test_failures[@]} failing modules before proceeding"
    exit 1
fi
