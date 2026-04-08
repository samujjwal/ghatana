#!/usr/bin/env bash
#
# Gradle Build Validation Script
#
# This script validates that the Gradle build system follows best practices:
# - No hardcoded dependency versions in build.gradle.kts files
# - All modules use version catalog references (libs.*)
# - Platform BOM validation passes
#
# Usage: ./scripts/validate-gradle-build.sh
#
# Exit codes:
# 0 - Validation passed
# 1 - Validation failed

set -e

echo "==============================================================================="
echo "Gradle Build Validation"
echo "==============================================================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

VALIDATION_FAILED=0

# Function to print colored output
print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Check for hardcoded dependency versions
echo "1. Checking for hardcoded dependency versions..."
HARDCODED_DEPS=$(find . -name "build.gradle.kts" -type f -exec grep -H '"[0-9]\+\.[0-9]\+\.[0-9]\+"' {} \; | grep -v libs.versions || true)

if [ -n "$HARDCODED_DEPS" ]; then
    print_error "Found hardcoded dependency versions:"
    echo "$HARDCODED_DEPS"
    VALIDATION_FAILED=1
else
    print_success "No hardcoded dependency versions found"
fi
echo ""

# Check for inline versions in dependencies
echo "2. Checking for inline version declarations..."
INLINE_VERSIONS=$(find . -name "build.gradle.kts" -type f -exec grep -H 'version.*=' {} \; | grep -v libs.versions | grep -v 'project.version' | grep -v 'rootProject.version' || true)

if [ -n "$INLINE_VERSIONS" ]; then
    print_warning "Found inline version declarations (review needed):"
    echo "$INLINE_VERSIONS"
else
    print_success "No problematic inline version declarations found"
fi
echo ""

# Validate Platform BOM
echo "3. Validating Platform BOM..."
if ./gradlew validatePlatformBom > /dev/null 2>&1; then
    print_success "Platform BOM validation passed"
else
    print_error "Platform BOM validation failed"
    ./gradlew validatePlatformBom
    VALIDATION_FAILED=1
fi
echo ""

# Check for composite build module dependency issues
echo "4. Checking composite build configuration..."
if [ -f "platform-kernel/settings.gradle.kts" ]; then
    if grep -q "versionCatalogs" platform-kernel/settings.gradle.kts; then
        print_success "platform-kernel has version catalog bridge"
    else
        print_error "platform-kernel missing version catalog bridge"
        VALIDATION_FAILED=1
    fi
else
    print_warning "platform-kernel/settings.gradle.kts not found"
fi

if [ -f "platform-plugins/settings.gradle.kts" ]; then
    if grep -q "versionCatalogs" platform-plugins/settings.gradle.kts; then
        print_success "platform-plugins has version catalog bridge"
    else
        print_error "platform-plugins missing version catalog bridge"
        VALIDATION_FAILED=1
    fi
else
    print_warning "platform-plugins/settings.gradle.kts not found"
fi
echo ""

# Check for repository blocks in composite build modules (should inherit from settings)
echo "5. Checking for redundant repository declarations in composite builds..."
KERNEL_REPOS=$(find platform-kernel -name "build.gradle.kts" -type f -exec grep -l "repositories" {} \; || true)
PLUGIN_REPOS=$(find platform-plugins -name "build.gradle.kts" -type f -exec grep -l "repositories" {} \; || true)

if [ -n "$KERNEL_REPOS" ]; then
    print_warning "Found repository declarations in kernel modules (may be redundant):"
    echo "$KERNEL_REPOS"
fi

if [ -n "$PLUGIN_REPOS" ]; then
    print_warning "Found repository declarations in plugin modules (may be redundant):"
    echo "$PLUGIN_REPOS"
fi

if [ -z "$KERNEL_REPOS" ] && [ -z "$PLUGIN_REPOS" ]; then
    print_success "No redundant repository declarations in composite builds"
fi
echo ""

# Final summary
echo "==============================================================================="
if [ $VALIDATION_FAILED -eq 0 ]; then
    print_success "All validations passed!"
    echo "==============================================================================="
    exit 0
else
    print_error "Validation failed with errors above"
    echo "==============================================================================="
    exit 1
fi
