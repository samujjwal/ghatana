#!/bin/bash
#
# Move Co-Located Test Files to __tests__/ Directories
#
# This script moves all test files that are co-located with source files
# into proper __tests__/ directories following the standardized structure.
#
# Usage: ./scripts/organize-tests.sh

set -e

FRONTEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$FRONTEND_DIR"

echo "======================================"
echo "Test File Organization Script"
echo "======================================"
echo ""

# Counter for moved files
MOVED_COUNT=0

# Find all co-located test files (not already in __tests__/)
echo "Finding co-located test files..."
echo ""

# Process .test.ts files
while IFS= read -r test_file; do
  if [ -f "$test_file" ]; then
    # Get directory and filename
    dir=$(dirname "$test_file")
    filename=$(basename "$test_file")
    
    # Create __tests__ directory if it doesn't exist
    tests_dir="$dir/__tests__"
    mkdir -p "$tests_dir"
    
    # Move the file
    target="$tests_dir/$filename"
    echo "Moving: $test_file"
    echo "    To: $target"
    mv "$test_file" "$target"
    ((MOVED_COUNT++))
    echo ""
  fi
done < <(find libs apps/web/src -name "*.test.ts" -not -path "*/__tests__/*" -not -path "*/e2e/*" 2>/dev/null || true)

# Process .test.tsx files
while IFS= read -r test_file; do
  if [ -f "$test_file" ]; then
    # Get directory and filename
    dir=$(dirname "$test_file")
    filename=$(basename "$test_file")
    
    # Create __tests__ directory if it doesn't exist
    tests_dir="$dir/__tests__"
    mkdir -p "$tests_dir"
    
    # Move the file
    target="$tests_dir/$filename"
    echo "Moving: $test_file"
    echo "    To: $target"
    mv "$test_file" "$target"
    ((MOVED_COUNT++))
    echo ""
  fi
done < <(find libs apps/web/src -name "*.test.tsx" -not -path "*/__tests__/*" -not -path "*/e2e/*" 2>/dev/null || true)

echo "======================================"
echo "✅ Test Organization Complete"
echo "======================================"
echo ""
echo "Moved $MOVED_COUNT test files to __tests__/ directories"
echo ""
echo "Next steps:"
echo "1. Run 'pnpm test' to verify all tests still pass"
echo "2. Run 'pnpm typecheck' to verify imports are still correct"
echo "3. Update any test import paths if needed"
echo ""
