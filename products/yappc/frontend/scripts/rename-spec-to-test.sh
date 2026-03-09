#!/bin/bash
#
# Rename Unit Test .spec Files to .test Files
#
# This script renames .spec.ts/.spec.tsx files to .test.ts/.test.tsx
# for unit tests (excluding E2E tests which should remain as .spec)
#
# Usage: ./scripts/rename-spec-to-test.sh

set -e

FRONTEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$FRONTEND_DIR"

echo "======================================"
echo "Rename .spec to .test Script"
echo "======================================"
echo ""

# Counter for renamed files
RENAMED_COUNT=0

# Find all .spec.ts files (excluding e2e directory)
echo "Finding .spec files to rename..."
echo ""

# Process .spec.ts files
while IFS= read -r spec_file; do
  if [ -f "$spec_file" ]; then
    # Generate new name
    test_file="${spec_file%.spec.ts}.test.ts"
    
    echo "Renaming: $spec_file"
    echo "      To: $test_file"
    mv "$spec_file" "$test_file"
    ((RENAMED_COUNT++))
    echo ""
  fi
done < <(find libs apps/web/src -name "*.spec.ts" -not -path "*/e2e/*" 2>/dev/null || true)

# Process .spec.tsx files
while IFS= read -r spec_file; do
  if [ -f "$spec_file" ]; then
    # Generate new name
    test_file="${spec_file%.spec.tsx}.test.tsx"
    
    echo "Renaming: $spec_file"
    echo "      To: $test_file"
    mv "$spec_file" "$test_file"
    ((RENAMED_COUNT++))
    echo ""
  fi
done < <(find libs apps/web/src -name "*.spec.tsx" -not -path "*/e2e/*" 2>/dev/null || true)

echo "======================================"
echo "✅ .spec to .test Rename Complete"
echo "======================================"
echo ""
echo "Renamed $RENAMED_COUNT files from .spec to .test"
echo ""
echo "E2E tests in e2e/ directory remain as .spec files (correct)"
echo ""
echo "Next steps:"
echo "1. Run 'pnpm test' to verify all tests still pass"
echo "2. Run 'pnpm typecheck' to verify everything compiles"
echo ""
