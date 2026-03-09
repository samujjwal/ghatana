#!/bin/bash
# Week 1 Priority 1: Fix Hardcoded Colors - Automated Script
# This script replaces hardcoded hex colors with design system tokens

set -e

echo "🎨 Week 1: Fixing Hardcoded Colors..."
echo "========================================="

# Function to fix hardcoded colors in a file
fix_colors() {
  local file=$1
  echo "Processing: $file"
  
  # Common color replacements (Note: palette.neutral is used instead of palette.grey)
  sed -i.bak \
    -e "s/#ffffff/palette.neutral[50]/g" \
    -e "s/#cccccc/palette.neutral[300]/g" \
    -e "s/#333333/palette.neutral[900]/g" \
    -e "s/#000000/palette.neutral[900]/g" \
    -e "s/#999999/palette.neutral[500]/g" \
    -e "s/#f5f5f5/palette.neutral[100]/g" \
    -e "s/#1976d2/palette.primary[500]/g" \
    "$file"
  
  # Check if file needs palette import
  if grep -q "palette\." "$file" && ! grep -q "import.*palette.*from.*@yappc/ui/tokens" "$file"; then
    # Add import at the beginning of imports section
    sed -i.bak "1s/^/import { palette } from '@yappc\/ui\/tokens';\n/" "$file"
  fi
  
  # Remove backup file
  rm -f "${file}.bak"
}

# Priority files (50 violations total)

# 1. Export Services (10 violations) - Partially done, skip for now
# apps/web/src/services/export/PNGExportService.ts - Already fixed
# apps/web/src/services/export/ExportService.ts - Need to check

if [ -f "apps/web/src/services/export/ExportService.ts" ]; then
  fix_colors "apps/web/src/services/export/ExportService.ts"
fi

# 2. Mobile App (2 violations)
if [ -f "apps/mobile-cap/src/mobile-entry.tsx" ]; then
  echo "Fixing mobile app colors..."
  # Special case for StatusBar.setBackgroundColor
  sed -i.bak "s/StatusBar\.setBackgroundColor({ color: '#1976d2' })/StatusBar.setBackgroundColor({ color: palette.primary[500] })/" \
    "apps/mobile-cap/src/mobile-entry.tsx"
  rm -f "apps/mobile-cap/src/mobile-entry.tsx.bak"
fi

# 3. Mobile Layout (1 violation)
if [ -f "apps/mobile-cap/src/components/MobileLayout.tsx" ]; then
  echo "Note: MobileLayout.tsx has inline styles that need Tailwind migration (Week 1 Priority 2)"
fi

# 4. Routes (14 violations) - These need Tailwind migration more than token replacement
# Will be handled in Priority 2: Inline Style Migration

# 5. Storybook Stories (24 violations)
echo "Fixing Storybook story wrappers..."
if [ -f "apps/web/src/routes/devsecops/devsecops.stories.tsx" ]; then
  fix_colors "apps/web/src/routes/devsecops/devsecops.stories.tsx"
fi

echo ""
echo "✅ Hardcoded color fixes completed!"
echo "Next: Run 'pnpm lint' to verify remaining violations"
echo ""
echo "Note: Canvas rendering colors (#ffffff, etc.) in PNGExportService.ts"
echo "      may need eslint-disable comments with justification if they're"
echo "      required for canvas API compatibility."
