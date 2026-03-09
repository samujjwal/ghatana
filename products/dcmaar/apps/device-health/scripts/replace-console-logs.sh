#!/bin/bash

# Find all TypeScript files in src directory
echo "🔍 Finding TypeScript files..."
FILES=$(find src -type f \( -name "*.ts" -o -name "*.tsx" \) ! -path "*/node_modules/*" ! -path "*/dist/*" ! -path "*/.git/*")

# Counter for modified files
MODIFIED=0

for file in $FILES; do
  # Skip files that already import devLog
  if grep -q "from.*dev-logger" "$file"; then
    continue
  fi

  # Check if file contains console.*
  if grep -q "console\." "$file"; then
    echo "🔄 Processing: $file"
    
    # Add import if not present
    if ! grep -q "import.*dev-logger" "$file"; then
      sed -i '' '1i\
import { devLog } from "../utils/dev-logger";\
' "$file"
    fi

    # Replace console.* with devLog.*
    sed -i '' 's/console\.log(/devLog.log(/g' "$file"
    sed -i '' 's/console\.info(/devLog.info(/g' "$file"
    sed -i '' 's/console\.warn(/devLog.warn(/g' "$file"
    sed -i '' 's/console\.error(/devLog.error(/g' "$file"
    sed -i '' 's/console\.debug(/devLog.debug(/g' "$file"
    
    ((MODIFIED++))
  fi
done

echo "✅ Done! Modified $MODIFIED files."
echo "✨ Running linter to fix any formatting issues..."
pnpm run lint:fix

echo "🎉 All done!"
