#!/bin/bash
# validate-v3-workflows.sh
# Validates v3 workflow definitions against operator index
#
# Usage: ./validate-v3-workflows.sh [--verbose]

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
WORKFLOWS_DIR="${PROJECT_ROOT}/libs/java/software-org/src/main/resources/workflows/v3"
OPERATIONS_DIR="${PROJECT_ROOT}/libs/java/software-org/src/main/resources/operations"
VERBOSE=false

# Counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
WARNINGS=0

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
    PASSED_TESTS=$((PASSED_TESTS + 1))
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
}

log_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    FAILED_TESTS=$((FAILED_TESTS + 1))
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
    WARNINGS=$((WARNINGS + 1))
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --verbose|-v)
            VERBOSE=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Workflow v3 Validation${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Test 1: Check stage files exist
log_info "Test 1: Checking stage files exist..."
EXPECTED_STAGES=(
    "0_audit_trail.yaml"
    "1_plan.yaml"
    "2_solution.yaml"
    "3_design.yaml"
    "4_develop.yaml"
    "5_build.yaml"
    "6_test.yaml"
    "7_secure.yaml"
    "8_compliance_validation.yaml"
    "9_release_validation.yaml"
    "10_uat.yaml"
    "11_package_release.yaml"
    "12_deploy.yaml"
    "13_operate.yaml"
    "14_monitor.yaml"
    "15_backup.yaml"
    "16_retrospective.yaml"
    "17_dashboard.yaml"
)

for stage in "${EXPECTED_STAGES[@]}"; do
    if [[ -f "${WORKFLOWS_DIR}/stages/${stage}" ]]; then
        [[ "$VERBOSE" == "true" ]] && log_pass "Stage exists: $stage"
    else
        log_fail "Missing stage: $stage"
    fi
done
log_pass "Stage files validation complete (${#EXPECTED_STAGES[@]} stages)"

# Test 2: Check pipeline exists
log_info "Test 2: Checking pipeline definition..."
if [[ -f "${WORKFLOWS_DIR}/pipeline.yaml" ]]; then
    log_pass "Pipeline definition exists"
else
    log_fail "Pipeline definition missing"
fi

# Test 3: Check operator index exists
log_info "Test 3: Checking operator index..."
if [[ -f "${OPERATIONS_DIR}/OPERATOR_INDEX.yaml" ]]; then
    log_pass "Operator index exists"
else
    log_fail "Operator index missing"
fi

# Test 4: Check operator directories exist
log_info "Test 4: Checking operator directories..."
EXPECTED_DIRS=(
    "domain/planning"
    "domain/design"
    "domain/build"
    "domain/release"
    "domain/operate"
    "cross-cutting"
)

for dir in "${EXPECTED_DIRS[@]}"; do
    if [[ -d "${OPERATIONS_DIR}/${dir}" ]]; then
        [[ "$VERBOSE" == "true" ]] && log_pass "Directory exists: $dir"
    else
        log_fail "Missing directory: $dir"
    fi
done
log_pass "Operator directories validation complete"

# Test 5: Validate YAML syntax
log_info "Test 5: Validating YAML syntax..."
YAML_ERRORS=0
for file in "${WORKFLOWS_DIR}"/stages/*.yaml "${WORKFLOWS_DIR}"/pipeline.yaml; do
    if [[ -f "$file" ]]; then
        # Basic YAML syntax check - look for common issues
        # Check for tabs (YAML doesn't allow tabs for indentation)
        if grep -qP '\t' "$file" 2>/dev/null; then
            log_fail "Tab characters found: $(basename "$file")"
            ((YAML_ERRORS++))
        elif head -1 "$file" | grep -q "^#\|^name:\|^---" 2>/dev/null; then
            [[ "$VERBOSE" == "true" ]] && echo "  ✓ $(basename "$file")"
        else
            log_warn "Possible invalid YAML start: $(basename "$file")"
        fi
    fi
done
if [[ $YAML_ERRORS -eq 0 ]]; then
    log_pass "All YAML files pass basic validation"
fi

# Test 6: Check operator references in stage files
log_info "Test 6: Checking operator references..."
OPERATOR_ERRORS=0

# Define valid operators
VALID_OPERATORS=(
    "planning/product_discovery_operator"
    "planning/business_goals_operator"
    "planning/stakeholder_operator"
    "planning/requirements_operator"
    "planning/plan_synthesis_operator"
    "design/architecture_operator"
    "design/component_operator"
    "design/data_model_operator"
    "design/api_operator"
    "design/security_design_operator"
    "design/design_synthesis_operator"
    "build/build_pipeline_operator"
    "build/code_quality_operator"
    "build/test_execution_operator"
    "build/security_scan_operator"
    "release/deploy_operator"
    "release/release_management_operator"
    "operate/monitor_operator"
    "operate/incident_operator"
    "cross-cutting/audit_log"
    "cross-cutting/progress_dashboard"
    "cross-cutting/observability_export"
    "cross-cutting/feedback_loop"
    "cross-cutting/evidence_collector"
    "cross-cutting/notification"
    "cross-cutting/kpi"
    "cross-cutting/success_criteria"
    "cross-cutting/milestone_tracker"
    "cross-cutting/risk_trend"
    "cross-cutting/best_practice"
    "cross-cutting/readiness"
    "cross-cutting/go_nogo"
    "cross-cutting/escalation"
)

for file in "${WORKFLOWS_DIR}"/stages/*.yaml; do
    if [[ -f "$file" ]]; then
        # Extract operator references from the file
        operators=$(grep -E "operator:" "$file" 2>/dev/null | sed 's/.*operator: *//' | sort -u || true)
        
        for op in $operators; do
            # Check if operator is in valid list
            found=false
            for valid in "${VALID_OPERATORS[@]}"; do
                if [[ "$op" == "$valid" ]]; then
                    found=true
                    break
                fi
            done
            
            if [[ "$found" == "false" ]]; then
                log_warn "Unknown operator in $(basename "$file"): $op"
            fi
        done
    fi
done

log_pass "Operator references validation complete"

# Test 7: Count workflow steps
log_info "Test 7: Counting workflow steps..."
TOTAL_STEPS=0
for file in "${WORKFLOWS_DIR}"/stages/*.yaml; do
    if [[ -f "$file" ]]; then
        steps=$(grep -c "- step:" "$file" 2>/dev/null || echo "0")
        TOTAL_STEPS=$((TOTAL_STEPS + steps))
        [[ "$VERBOSE" == "true" ]] && echo "  $(basename "$file"): $steps steps"
    fi
done
log_pass "Total workflow steps: $TOTAL_STEPS"

# Test 8: Check cross-cutting operator usage
log_info "Test 8: Checking cross-cutting operator coverage..."
STAGES_WITH_MILESTONE=$(grep -rl "milestone_tracker" "${WORKFLOWS_DIR}/stages" 2>/dev/null | wc -l | tr -d ' ')
STAGES_WITH_BEST_PRACTICE=$(grep -rl "best_practice" "${WORKFLOWS_DIR}/stages" 2>/dev/null | wc -l | tr -d ' ')

log_pass "Stages with milestone_tracker: $STAGES_WITH_MILESTONE"
log_pass "Stages with best_practice: $STAGES_WITH_BEST_PRACTICE"

# Summary
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Validation Summary${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo "Total Tests:    $TOTAL_TESTS"
echo -e "Passed:         ${GREEN}$PASSED_TESTS${NC}"
echo -e "Failed:         ${RED}$FAILED_TESTS${NC}"
echo -e "Warnings:       ${YELLOW}$WARNINGS${NC}"
echo ""
echo "Stages:         ${#EXPECTED_STAGES[@]}"
echo "Workflow Steps: $TOTAL_STEPS"
echo ""

if [[ $FAILED_TESTS -eq 0 ]]; then
    echo -e "${GREEN}✓ All validation tests passed!${NC}"
    exit 0
else
    echo -e "${RED}✗ Some validation tests failed${NC}"
    exit 1
fi
