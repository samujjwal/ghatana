#!/bin/bash
# Test Classification Scanner
# Scans the codebase for potentially mislabeled integration tests

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

print_header() {
    echo -e "${CYAN}=== $1 ===${NC}"
}

print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Counters
total_files=0
potential_misclassifications=0
unit_tests=0
integration_tests=0
contract_tests=0
e2e_tests=0

# Arrays to store findings
declare -a unit_tests_as_integration
declare -a integration_tests_as_unit
declare -a e2e_tests_as_integration
declare -a tests_without_tags

print_header "Test Classification Scanner"
print_info "Scanning repository: $REPO_ROOT"
echo ""

# Find all Java test files
print_info "Scanning Java test files..."
java_test_files=$(find "$REPO_ROOT" -type f -name "*Test.java" -o -name "*IT.java" 2>/dev/null || true)

for file in $java_test_files; do
    if [[ ! -f "$file" ]]; then
        continue
    fi

    total_files=$((total_files + 1))
    
    # Skip if file is in build directory
    if [[ "$file" =~ /build/ ]]; then
        continue
    fi

    filename=$(basename "$file")
    content=$(cat "$file")
    filepath=$(realpath --relative-to="$REPO_ROOT" "$file")

    # Check for tags
    has_unit_tag=$(echo "$content" | grep -c '@Tag("unit")' || true)
    has_integration_tag=$(echo "$content" | grep -c '@Tag("integration")' || true)
    has_contract_tag=$(echo "$content" | grep -c '@Tag("contract")' || true)
    has_e2e_tag=$(echo "$content" | grep -c '@Tag("e2e")' || true)

    # Check for Testcontainers
    has_testcontainers=$(echo "$content" | grep -c 'Testcontainers\|PostgreSQLContainer\|GenericContainer' || true)

    # Check for folder location
    is_in_integration_folder=$(echo "$filepath" | grep -c '/integration/' || true)
    is_in_e2e_folder=$(echo "$filepath" | grep -c '/e2e/' || true)

    # Count by tag
    if [[ $has_unit_tag -gt 0 ]]; then
        unit_tests=$((unit_tests + 1))
    fi
    if [[ $has_integration_tag -gt 0 ]]; then
        integration_tests=$((integration_tests + 1))
    fi
    if [[ $has_contract_tag -gt 0 ]]; then
        contract_tests=$((contract_tests + 1))
    fi
    if [[ $has_e2e_tag -gt 0 ]]; then
        e2e_tests=$((e2e_tests + 1))
    fi

    # Detect misclassifications
    # Case 1: Tagged as integration but uses no Testcontainers and no folder indication
    if [[ $has_integration_tag -gt 0 ]] && [[ $has_testcontainers -eq 0 ]] && [[ $is_in_integration_folder -eq 0 ]]; then
        unit_tests_as_integration+=("$filepath")
        potential_misclassifications=$((potential_misclassifications + 1))
    fi

    # Case 2: Tagged as unit but uses Testcontainers
    if [[ $has_unit_tag -gt 0 ]] && [[ $has_testcontainers -gt 0 ]]; then
        integration_tests_as_unit+=("$filepath")
        potential_misclassifications=$((potential_misclassifications + 1))
    fi

    # Case 3: Tagged as integration but in e2e folder
    if [[ $has_integration_tag -gt 0 ]] && [[ $is_in_e2e_folder -gt 0 ]]; then
        e2e_tests_as_integration+=("$filepath")
        potential_misclassifications=$((potential_misclassifications + 1))
    fi

    # Case 4: No tags at all
    if [[ $has_unit_tag -eq 0 ]] && [[ $has_integration_tag -eq 0 ]] && [[ $has_contract_tag -eq 0 ]] && [[ $has_e2e_tag -eq 0 ]]; then
        tests_without_tags+=("$filepath")
    fi
done

# Find all TypeScript test files
print_info "Scanning TypeScript test files..."
ts_test_files=$(find "$REPO_ROOT" -type f -name "*.test.ts" -o -name "*.test.tsx" -o -name "*.spec.ts" 2>/dev/null || true)

for file in $ts_test_files; do
    if [[ ! -f "$file" ]]; then
        continue
    fi

    # Skip if file is in node_modules
    if [[ "$file" =~ /node_modules/ ]]; then
        continue
    fi

    total_files=$((total_files + 1))
    filepath=$(realpath --relative-to="$REPO_ROOT" "$file")

    # TypeScript tests typically don't use JUnit tags
    # Check folder structure instead
    is_in_integration_folder=$(echo "$filepath" | grep -c '/integration/' || true)
    is_in_e2e_folder=$(echo "$filepath" | grep -c '/e2e/' || true)

    if [[ $is_in_integration_folder -gt 0 ]]; then
        integration_tests=$((integration_tests + 1))
    fi
    if [[ $is_in_e2e_folder -gt 0 ]]; then
        e2e_tests=$((e2e_tests + 1))
    fi
    if [[ $is_in_integration_folder -eq 0 ]] && [[ $is_in_e2e_folder -eq 0 ]]; then
        unit_tests=$((unit_tests + 1))
    fi
done

# Print summary
print_header "Scan Summary"
echo ""
echo "Total test files scanned: $total_files"
echo ""
echo "Tests by type (based on tags):"
echo "  Unit tests: $unit_tests"
echo "  Integration tests: $integration_tests"
echo "  Contract tests: $contract_tests"
echo "  E2E tests: $e2e_tests"
echo ""
echo "Potential misclassifications: $potential_misclassifications"
echo ""

# Print findings
if [[ ${#unit_tests_as_integration[@]} -gt 0 ]]; then
    print_warn "Tests tagged as 'integration' but may be unit tests:"
    for file in "${unit_tests_as_integration[@]}"; do
        echo "  - $file"
    done
    echo ""
fi

if [[ ${#integration_tests_as_unit[@]} -gt 0 ]]; then
    print_warn "Tests tagged as 'unit' but use Testcontainers (likely integration tests):"
    for file in "${integration_tests_as_unit[@]}"; do
        echo "  - $file"
    done
    echo ""
fi

if [[ ${#e2e_tests_as_integration[@]} -gt 0 ]]; then
    print_warn "Tests in e2e folder but tagged as 'integration' (should be e2e):"
    for file in "${e2e_tests_as_integration[@]}"; do
        echo "  - $file"
    done
    echo ""
fi

if [[ ${#tests_without_tags[@]} -gt 0 ]]; then
    print_warn "Tests without any tags:"
    for file in "${tests_without_tags[@]}"; do
        echo "  - $file"
    done
    echo ""
fi

if [[ $potential_misclassifications -eq 0 ]]; then
    print_info "No potential misclassifications found!"
else
    print_warn "Found $potential_misclassifications potential misclassification(s)"
    print_info "Review these files and apply fixes according to TEST_RECLASSIFICATION_GUIDE.md"
fi

print_header "Scan Complete"
