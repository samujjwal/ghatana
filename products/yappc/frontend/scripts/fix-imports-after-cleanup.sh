#!/bin/bash
# fix-imports-after-cleanup.sh - Run AFTER master-cleanup.sh
# Automatically fixes imports for deleted files

set -e
cd "$(dirname "$0")/.."

echo "🔧 Fixing imports after aggressive cleanup..."
echo ""

# ============================================================================
# SECTION 1: Fix canvas-atoms imports
# ============================================================================
echo "🔄 [1/4] Fixing canvas-atoms imports..."

find apps/web/src -type f \( -name "*.ts" -o -name "*.tsx" \) ! -path "*/node_modules/*" -exec sed -i '' \
  -e "s|from ['\"]\.\.*/canvas-atoms['\"]|from '@yappc/canvas'|g" \
  -e "s|from ['\"]@yappc/shared-ui-core/canvas-atoms['\"]|from '@yappc/canvas'|g" \
  -e "s|from ['\"]@yappc/ui/canvas-atoms['\"]|from '@yappc/canvas'|g" \
  {} \; 2>/dev/null || true

echo "    ✅ Canvas atoms imports fixed"

# ============================================================================
# SECTION 2: Fix auth atom imports
# ============================================================================
echo "🔄 [2/4] Fixing auth atom imports..."

find apps/web/src -type f \( -name "*.ts" -o -name "*.tsx" \) ! -path "*/node_modules/*" -exec sed -i '' \
  -e "s|from ['\"]@yappc/store/atoms/auth['\"]|from '@yappc/ui/state'|g" \
  -e "s|from ['\"].*store.*atoms/auth['\"]|from '@yappc/ui/state'|g" \
  {} \; 2>/dev/null || true

echo "    ✅ Auth atom imports fixed"

# ============================================================================
# SECTION 3: Remove deleted route imports
# ============================================================================
echo "🔄 [3/4] Removing deleted route imports..."

find apps/web/src -type f \( -name "*.ts" -o -name "*.tsx" \) ! -path "*/node_modules/*" -exec sed -i '' \
  -e "/from.*routes\/lifecycle/d" \
  -e "/from.*routes\/journey['\"];/d" \
  -e "/from.*page-designer/d" \
  -e "/from.*workflows\//d" \
  {} \; 2>/dev/null || true

echo "    ✅ Deleted route imports removed"

# ============================================================================
# SECTION 4: Remove deleted component imports
# ============================================================================
echo "🔄 [4/4] Removing deleted component imports..."

find apps/web/src -type f \( -name "*.ts" -o -name "*.tsx" \) ! -path "*/node_modules/*" -exec sed -i '' \
  -e "/from.*WorkflowShell/d" \
  -e "/from.*StepRail/d" \
  -e "/from.*TaskExecutionGrid/d" \
  -e "/from.*BreadcrumbBar/d" \
  -e "/from.*CanvasToolbar/d" \
  -e "/from.*NodeTypePicker/d" \
  {} \; 2>/dev/null || true

echo "    ✅ Deleted component imports removed"

# ============================================================================
# SUMMARY
# ============================================================================
echo ""
echo "✅ Import fixes complete!"
echo ""
echo "🔍 Verify no legacy imports remain:"
echo "   grep -r 'canvas-atoms' apps/web/src"
echo "   grep -r 'page-designer' apps/web/src"
echo "   grep -r 'WorkflowShell' apps/web/src"
echo ""
echo "⚠️  Next steps:"
echo "   1. Check for remaining errors: pnpm build 2>&1 | grep 'Cannot find module'"
echo "   2. Manually fix any remaining import errors"
echo "   3. Update apps/web/src/routes.ts to remove deleted routes"
echo ""
