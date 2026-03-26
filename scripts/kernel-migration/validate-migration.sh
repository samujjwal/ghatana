#!/bin/bash

# Kernel Platform Migration Validation Script
# Validates that products have successfully migrated to kernel platform

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "========================================="
echo "Kernel Platform Migration Validation"
echo "========================================="
echo ""

# Function to print success message
success() {
    echo -e "${GREEN}✓${NC} $1"
}

# Function to print error message
error() {
    echo -e "${RED}✗${NC} $1"
}

# Function to print warning message
warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# Validation counters
PASSED=0
FAILED=0
WARNINGS=0

# Check if kernel dependency is added
check_kernel_dependency() {
    local product=$1
    local build_file="$PROJECT_ROOT/products/$product/build.gradle"
    
    if [ ! -f "$build_file" ]; then
        error "$product: build.gradle not found"
        ((FAILED++))
        return 1
    fi
    
    if grep -q "implementation project(':platform:java:kernel')" "$build_file"; then
        success "$product: Kernel dependency added"
        ((PASSED++))
        return 0
    else
        error "$product: Kernel dependency missing"
        ((FAILED++))
        return 1
    fi
}

# Check for legacy capability adapter usage
check_legacy_adapter() {
    local product=$1
    local src_dir="$PROJECT_ROOT/products/$product/src"
    
    if [ ! -d "$src_dir" ]; then
        warning "$product: Source directory not found"
        ((WARNINGS++))
        return 1
    fi
    
    # Check for LegacyCapabilityAdapter imports
    if grep -r "import.*LegacyCapabilityAdapter" "$src_dir" > /dev/null 2>&1; then
        warning "$product: Still using LegacyCapabilityAdapter (migration in progress)"
        ((WARNINGS++))
        return 1
    else
        success "$product: Not using legacy adapters"
        ((PASSED++))
        return 0
    fi
}

# Check for canonical type usage
check_canonical_types() {
    local product=$1
    local src_dir="$PROJECT_ROOT/products/$product/src"
    
    if [ ! -d "$src_dir" ]; then
        return 1
    fi
    
    # Check for canonical KernelCapability usage
    if grep -r "import com.ghatana.kernel.descriptor.KernelCapability" "$src_dir" > /dev/null 2>&1; then
        success "$product: Using canonical KernelCapability"
        ((PASSED++))
    else
        warning "$product: Not using canonical KernelCapability"
        ((WARNINGS++))
    fi
    
    # Check for canonical KernelExtension usage
    if grep -r "import com.ghatana.kernel.extension.KernelExtension" "$src_dir" > /dev/null 2>&1; then
        success "$product: Using canonical KernelExtension"
        ((PASSED++))
    else
        warning "$product: Not using canonical KernelExtension"
        ((WARNINGS++))
    fi
}

# Check for security framework integration
check_security_framework() {
    local product=$1
    local src_dir="$PROJECT_ROOT/products/$product/src"
    
    if [ ! -d "$src_dir" ]; then
        return 1
    fi
    
    if grep -r "import com.ghatana.kernel.security" "$src_dir" > /dev/null 2>&1; then
        success "$product: Security framework integrated"
        ((PASSED++))
        return 0
    else
        warning "$product: Security framework not integrated"
        ((WARNINGS++))
        return 1
    fi
}

# Check for observability framework integration
check_observability_framework() {
    local product=$1
    local src_dir="$PROJECT_ROOT/products/$product/src"
    
    if [ ! -d "$src_dir" ]; then
        return 1
    fi
    
    if grep -r "import com.ghatana.kernel.observability" "$src_dir" > /dev/null 2>&1; then
        success "$product: Observability framework integrated"
        ((PASSED++))
        return 0
    else
        warning "$product: Observability framework not integrated"
        ((WARNINGS++))
        return 1
    fi
}

# Check for AI framework integration
check_ai_framework() {
    local product=$1
    local src_dir="$PROJECT_ROOT/products/$product/src"
    
    if [ ! -d "$src_dir" ]; then
        return 1
    fi
    
    if grep -r "import com.ghatana.kernel.ai" "$src_dir" > /dev/null 2>&1; then
        success "$product: AI framework integrated"
        ((PASSED++))
        return 0
    else
        warning "$product: AI framework not integrated"
        ((WARNINGS++))
        return 1
    fi
}

# Check for contract validation
check_contract_validation() {
    local product=$1
    local contracts_dir="$PROJECT_ROOT/products/$product/src/main/java/com/ghatana/$product/contracts"
    
    if [ -d "$contracts_dir" ]; then
        success "$product: Contract definitions found"
        ((PASSED++))
        
        # Check for contract validation in CI/CD
        local ci_file="$PROJECT_ROOT/.github/workflows/${product}-contract-validation.yml"
        if [ -f "$ci_file" ]; then
            success "$product: Contract validation in CI/CD"
            ((PASSED++))
        else
            warning "$product: Contract validation not in CI/CD"
            ((WARNINGS++))
        fi
    else
        warning "$product: No contract definitions found"
        ((WARNINGS++))
    fi
}

# Check for integration tests
check_integration_tests() {
    local product=$1
    local test_dir="$PROJECT_ROOT/products/$product/src/test"
    
    if [ ! -d "$test_dir" ]; then
        warning "$product: Test directory not found"
        ((WARNINGS++))
        return 1
    fi
    
    # Check for kernel integration tests
    if grep -r "KernelSecurityManager\|KernelTelemetryManager\|AgentOrchestrator" "$test_dir" > /dev/null 2>&1; then
        success "$product: Kernel integration tests found"
        ((PASSED++))
        return 0
    else
        warning "$product: No kernel integration tests found"
        ((WARNINGS++))
        return 1
    fi
}

echo "Validating PHR Migration..."
echo "----------------------------"
check_kernel_dependency "phr"
check_legacy_adapter "phr"
check_canonical_types "phr"
check_security_framework "phr"
check_observability_framework "phr"
check_integration_tests "phr"
echo ""

echo "Validating Finance Migration..."
echo "--------------------------------"
check_kernel_dependency "finance"
check_legacy_adapter "finance"
check_canonical_types "finance"
check_ai_framework "finance"
check_contract_validation "finance"
check_integration_tests "finance"
echo ""

echo "Validating Aura Migration..."
echo "-----------------------------"
check_kernel_dependency "aura"
check_legacy_adapter "aura"
check_canonical_types "aura"
check_ai_framework "aura"
check_observability_framework "aura"
check_integration_tests "aura"
echo ""

# Summary
echo "========================================="
echo "Migration Validation Summary"
echo "========================================="
echo -e "${GREEN}Passed:${NC} $PASSED"
echo -e "${YELLOW}Warnings:${NC} $WARNINGS"
echo -e "${RED}Failed:${NC} $FAILED"
echo ""

if [ $FAILED -gt 0 ]; then
    echo -e "${RED}Migration validation FAILED${NC}"
    echo "Please address the failed checks before proceeding."
    exit 1
elif [ $WARNINGS -gt 0 ]; then
    echo -e "${YELLOW}Migration validation PASSED with warnings${NC}"
    echo "Consider addressing the warnings for complete migration."
    exit 0
else
    echo -e "${GREEN}Migration validation PASSED${NC}"
    echo "All products successfully migrated to kernel platform!"
    exit 0
fi
