#!/bin/bash

# Script to migrate Jest tests to Vitest
# Runs on all .test.ts files in src/__tests__

echo "🔄 Migrating Jest to Vitest..."
echo ""

# Find all test files
test_files=$(find src/__tests__ -name "*.test.ts")
file_count=$(echo "$test_files" | wc -l | tr -d ' ')

echo "Found $file_count test files to migrate"
echo ""

# Counter for changes
total_changes=0

for file in $test_files; do
  echo "Processing: $file"
  changes=0

  # Create backup
  cp "$file" "$file.bak"

  # Replace jest global with vi
  if grep -q "jest\." "$file"; then
    sed -i '' 's/jest\./vi./g' "$file"
    ((changes++))
  fi

  # Replace jest imports
  if grep -q "from '@jest/globals'" "$file" || grep -q "from 'jest'" "$file"; then
    sed -i '' "s/from '@jest\/globals'/from 'vitest'/g" "$file"
    sed -i '' "s/from 'jest'/from 'vitest'/g" "$file"
    ((changes++))
  fi

  # Add vitest imports if needed (for vi, describe, it, expect, etc.)
  if ! grep -q "import.*from 'vitest'" "$file"; then
    # Check if file uses describe, it, expect, beforeEach, afterEach, etc.
    if grep -E -q "(describe|it|expect|beforeEach|afterEach|beforeAll|afterAll|vi)" "$file"; then
      # Add import at the top after other imports
      sed -i '' "1a\\
import { describe, it, expect, beforeEach, afterEach, beforeAll, afterAll, vi } from 'vitest';\\
" "$file"
      ((changes++))
    fi
  fi

  # Replace jest.requireActual with vi.importActual (async)
  if grep -q "jest\.requireActual" "$file"; then
    sed -i '' 's/jest\.requireActual/await vi.importActual/g' "$file"
    ((changes++))
  fi

  # Replace jest.resetModules with vi.resetModules
  if grep -q "jest\.resetModules" "$file"; then
    sed -i '' 's/jest\.resetModules/vi.resetModules/g' "$file"
    ((changes++))
  fi

  # Done with this file
  if [ $changes -gt 0 ]; then
    echo "  ✅ Made $changes type(s) of changes"
    total_changes=$((total_changes + changes))
  else
    echo "  ⏭️  No changes needed"
    rm "$file.bak"  # Remove backup if no changes
  fi
  echo ""
done

echo "✨ Migration complete!"
echo "📊 Total changes: $total_changes"
echo ""
echo "⚠️  Backup files created with .bak extension"
echo "Run 'find src/__tests__ -name \"*.bak\" -delete' to remove backups after verifying"
echo ""
echo "🧪 Run 'pnpm test' to verify all tests pass!"
