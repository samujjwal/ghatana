#!/bin/bash
# Consolidation Verification Script
# Verifies all Phase 3 requirements are met

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Counters
PASSED=0
FAILED=0
WARNINGS=0

# Helper functions
print_header() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}\n"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
    ((PASSED++))
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
    ((FAILED++))
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
    ((WARNINGS++))
}

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

# Start verification
print_header "Starting Consolidation Verification"
echo "Date: $(date)"
echo "Directory: $(pwd)"

# Check we're in the right directory
if [ ! -f "Cargo.toml" ]; then
    print_error "Not in workspace root directory"
    exit 1
fi

# Phase 1: Clean Build
print_header "Phase 1: Clean Build Verification"

print_info "Cleaning workspace..."
cargo clean > /dev/null 2>&1

print_info "Building workspace..."
if cargo build --workspace 2>&1 | tee build.log; then
    print_success "Workspace builds successfully"
else
    print_error "Workspace build failed"
    echo "Check build.log for details"
fi

# Phase 2: Build with All Features
print_header "Phase 2: Feature Build Verification"

print_info "Building with all features..."
if cargo build --workspace --all-features 2>&1 | tee build-features.log; then
    print_success "All features build successfully"
else
    print_error "Feature build failed"
fi

# Phase 3: Clippy
print_header "Phase 3: Clippy Verification"

print_info "Running clippy..."
if cargo clippy --workspace --all-features -- -D warnings 2>&1 | tee clippy.log; then
    print_success "Clippy passed with no warnings"
else
    print_error "Clippy found issues"
    echo "Check clippy.log for details"
fi

# Phase 4: Formatting
print_header "Phase 4: Format Verification"

print_info "Checking code formatting..."
if cargo fmt --all -- --check 2>&1 | tee format.log; then
    print_success "Code is properly formatted"
else
    print_warning "Code needs formatting"
    echo "Run: cargo fmt --all"
fi

# Phase 5: Tests
print_header "Phase 5: Test Verification"

print_info "Running all tests..."
if cargo test --workspace --all-features 2>&1 | tee test.log; then
    print_success "All tests passed"
else
    print_error "Some tests failed"
    echo "Check test.log for details"
fi

# Phase 6: Integration Tests
print_header "Phase 6: Integration Test Verification"

print_info "Running integration tests..."
if cargo test -p agent-common --test integration_storage 2>&1 | tee integration.log; then
    print_success "Integration tests passed"
else
    print_error "Integration tests failed"
fi

# Phase 7: Documentation
print_header "Phase 7: Documentation Verification"

print_info "Building documentation..."
if cargo doc --workspace --all-features --no-deps 2>&1 | tee doc.log; then
    print_success "Documentation builds successfully"
else
    print_error "Documentation build failed"
fi

# Phase 8: Security Audit
print_header "Phase 8: Security Audit"

print_info "Running security audit..."
if command -v cargo-audit &> /dev/null; then
    if cargo audit 2>&1 | tee audit.log; then
        print_success "Security audit passed"
    else
        print_warning "Security audit found issues"
        echo "Check audit.log for details"
    fi
else
    print_warning "cargo-audit not installed"
    echo "Install with: cargo install cargo-audit"
fi

# Phase 9: Dependency Check
print_header "Phase 9: Dependency Verification"

print_info "Checking for duplicate dependencies..."
DUPLICATES=$(cargo tree --workspace --duplicates 2>&1 | grep -v "^$" | wc -l)
if [ "$DUPLICATES" -eq 0 ]; then
    print_success "No duplicate dependencies"
else
    print_warning "Found $DUPLICATES duplicate dependencies"
    echo "Run: cargo tree --workspace --duplicates"
fi

# Phase 10: Build Times
print_header "Phase 10: Performance Verification"

print_info "Measuring incremental build time..."
touch libs/agent-common/src/lib.rs
START_TIME=$(date +%s)
cargo build --workspace > /dev/null 2>&1
END_TIME=$(date +%s)
BUILD_TIME=$((END_TIME - START_TIME))

if [ "$BUILD_TIME" -lt 30 ]; then
    print_success "Incremental build time: ${BUILD_TIME}s (target: <30s)"
else
    print_warning "Incremental build time: ${BUILD_TIME}s (target: <30s)"
fi

# Phase 11: Code Statistics
print_header "Phase 11: Code Statistics"

print_info "Gathering statistics..."

# Count lines in agent-common
if [ -d "libs/agent-common/src" ]; then
    COMMON_LINES=$(find libs/agent-common/src -name "*.rs" -exec wc -l {} + | tail -1 | awk '{print $1}')
    print_info "agent-common: $COMMON_LINES lines of code"
fi

# Count lines in adapter
if [ -f "services/desktop/src-tauri/src/db/adapters.rs" ]; then
    ADAPTER_LINES=$(wc -l < services/desktop/src-tauri/src/db/adapters.rs)
    print_info "Desktop adapter: $ADAPTER_LINES lines of code"
fi

# Count documentation
DOC_SIZE=$(du -sh docs/migrations 2>/dev/null | awk '{print $1}')
if [ -n "$DOC_SIZE" ]; then
    print_info "Documentation size: $DOC_SIZE"
fi

# Phase 12: File Verification
print_header "Phase 12: File Structure Verification"

# Check key files exist
FILES=(
    "libs/agent-common/Cargo.toml"
    "libs/agent-common/src/lib.rs"
    "libs/agent-common/src/storage/mod.rs"
    "libs/agent-common/src/storage/sqlite.rs"
    "services/desktop/src-tauri/src/db/adapters.rs"
    "docs/migrations/PHASE_3_PLAN.md"
)

for file in "${FILES[@]}"; do
    if [ -f "$file" ]; then
        print_success "Found: $file"
    else
        print_error "Missing: $file"
    fi
done

# Phase 13: Search for Duplicates
print_header "Phase 13: Duplicate Code Check"

print_info "Searching for duplicate error types..."
DUPLICATE_ERRORS=$(rg "enum SdkError" --type rust 2>/dev/null | wc -l)
if [ "$DUPLICATE_ERRORS" -eq 0 ]; then
    print_success "No duplicate SdkError enums found"
else
    print_warning "Found $DUPLICATE_ERRORS duplicate SdkError definitions"
fi

print_info "Searching for old repository usage..."
OLD_REPOS=$(rg "MetricRepository|EventRepository|ActionRepository" --type rust 2>/dev/null | grep -v "//\|#" | wc -l)
if [ "$OLD_REPOS" -eq 0 ]; then
    print_success "No old repository usage found"
else
    print_warning "Found $OLD_REPOS old repository references"
fi

# Summary
print_header "Verification Summary"

echo -e "${GREEN}Passed:   $PASSED${NC}"
echo -e "${YELLOW}Warnings: $WARNINGS${NC}"
echo -e "${RED}Failed:   $FAILED${NC}"
echo ""

# Overall result
if [ "$FAILED" -eq 0 ]; then
    if [ "$WARNINGS" -eq 0 ]; then
        echo -e "${GREEN}🎉 All verifications passed! Consolidation is complete.${NC}"
        exit 0
    else
        echo -e "${YELLOW}⚠️  Verification passed with warnings. Review warnings above.${NC}"
        exit 0
    fi
else
    echo -e "${RED}❌ Verification failed. Please fix errors above.${NC}"
    exit 1
fi
