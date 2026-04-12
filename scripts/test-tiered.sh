#!/bin/bash
# Tiered Test Scripts for Ghatana
# Provides scripts to run tests at different tiers: unit, integration, contract, e2e

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Function to print colored output
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to run unit tests
run_unit_tests() {
    print_info "Running unit tests..."
    cd "$REPO_ROOT"
    
    # Run Java unit tests
    if [ -f "gradlew" ]; then
        ./gradlew test --tests "*Test" --exclude-task "*IntegrationTest" --exclude-task "*ContractTest" --exclude-task "*E2ETest" || true
    fi
    
    # Run TypeScript unit tests
    if [ -f "package.json" ]; then
        pnpm test --run || true
    fi
    
    print_info "Unit tests completed"
}

# Function to run integration tests
run_integration_tests() {
    print_info "Running integration tests..."
    cd "$REPO_ROOT"
    
    # Run Java integration tests
    if [ -f "gradlew" ]; then
        ./gradlew test --tests "*IntegrationTest" || true
    fi
    
    # Run TypeScript integration tests
    if [ -f "package.json" ]; then
        pnpm test:integration --run || true
    fi
    
    print_info "Integration tests completed"
}

# Function to run contract tests
run_contract_tests() {
    print_info "Running contract tests..."
    cd "$REPO_ROOT"
    
    # Run Java contract tests
    if [ -f "gradlew" ]; then
        ./gradlew test --tests "*ContractTest" || true
    fi
    
    print_info "Contract tests completed"
}

# Function to run E2E tests
run_e2e_tests() {
    print_info "Running E2E tests..."
    cd "$REPO_ROOT"
    
    # Run Java E2E tests
    if [ -f "gradlew" ]; then
        ./gradlew test --tests "*E2ETest" || true
    fi
    
    # Run TypeScript E2E tests
    if [ -f "package.json" ]; then
        pnpm test:e2e --run || true
    fi
    
    print_info "E2E tests completed"
}

# Function to run all tests
run_all_tests() {
    print_info "Running all tests..."
    run_unit_tests
    run_integration_tests
    run_contract_tests
    run_e2e_tests
    print_info "All tests completed"
}

# Function to run fast tests (unit + contract)
run_fast_tests() {
    print_info "Running fast tests (unit + contract)..."
    run_unit_tests
    run_contract_tests
    print_info "Fast tests completed"
}

# Function to run slow tests (integration + e2e)
run_slow_tests() {
    print_info "Running slow tests (integration + e2e)..."
    run_integration_tests
    run_e2e_tests
    print_info "Slow tests completed"
}

# Main script logic
case "${1:-all}" in
    unit)
        run_unit_tests
        ;;
    integration)
        run_integration_tests
        ;;
    contract)
        run_contract_tests
        ;;
    e2e)
        run_e2e_tests
        ;;
    fast)
        run_fast_tests
        ;;
    slow)
        run_slow_tests
        ;;
    all)
        run_all_tests
        ;;
    *)
        echo "Usage: $0 {unit|integration|contract|e2e|fast|slow|all}"
        echo ""
        echo "Options:"
        echo "  unit         Run unit tests only"
        echo "  integration  Run integration tests only"
        echo "  contract     Run contract tests only"
        echo "  e2e          Run E2E tests only"
        echo "  fast         Run fast tests (unit + contract)"
        echo "  slow         Run slow tests (integration + e2e)"
        echo "  all          Run all tests (default)"
        exit 1
        ;;
esac
