#!/bin/bash
# validate-cleanup.sh - Validates that cleanup was successful
# Run AFTER all cleanup scripts

set -e
cd "$(dirname "$0")/.."

echo "🔍 VALIDATING AGGRESSIVE CLEANUP..."
echo ""

ERRORS=0

# ============================================================================
# CHECK 1: Verify deleted files are gone
# ============================================================================
echo "📋 [1/6] Checking deleted files..."

SHOULD_NOT_EXIST=(
  "apps/web/src/routes/lifecycle/"
  "apps/web/src/routes/journey.tsx"
  "apps/web/src/routes/page-designer.tsx"
  "apps/web/src/routes/page-designer/"
  "apps/web/src/routes/workflows/"
  "apps/web/src/routes/canvas-redirect.tsx"
  "apps/web/src/components/canvas/devsecops/"
  "apps/web/src/components/workflow/WorkflowShell.tsx"
  "apps/web/src/grapes-origin/"
  "apps/web/src/components/canvas/canvas-atoms.ts"
  "libs/ui/src/canvas-atoms.ts"
  "libs/page-builder-ui/"
  "libs/page-builder/"
)

for file in "${SHOULD_NOT_EXIST[@]}"; do
  if [ -e "$file" ]; then
    echo "    ❌ STILL EXISTS: $file"
    ERRORS=$((ERRORS + 1))
  fi
done

if [ $ERRORS -eq 0 ]; then
  echo "    ✅ All deprecated files deleted"
else
  echo "    ❌ $ERRORS files still exist!"
fi

# ============================================================================
# CHECK 2: Verify no legacy imports
# ============================================================================
echo ""
echo "📋 [2/6] Checking for legacy imports..."

LEGACY_IMPORTS=$(grep -r "canvas-atoms" apps/web/src 2>/dev/null | wc -l || echo "0")
if [ "$LEGACY_IMPORTS" -gt 0 ]; then
  echo "    ❌ Found $LEGACY_IMPORTS legacy canvas-atoms imports"
  grep -r "canvas-atoms" apps/web/src | head -5
  ERRORS=$((ERRORS + 1))
else
  echo "    ✅ No legacy canvas-atoms imports"
fi

# ============================================================================
# CHECK 3: Verify no deprecated @deprecated tags
# ============================================================================
echo ""
echo "📋 [3/6] Checking for @deprecated tags..."

DEPRECATED_COUNT=$(grep -r "@deprecated" apps/web/src 2>/dev/null | wc -l || echo "0")
if [ "$DEPRECATED_COUNT" -gt 0 ]; then
  echo "    ⚠️  Found $DEPRECATED_COUNT @deprecated tags (review manually)"
  grep -r "@deprecated" apps/web/src | head -5
else
  echo "    ✅ No @deprecated tags found"
fi

# ============================================================================
# CHECK 4: Count remaining files and LOC
# ============================================================================
echo ""
echo "📋 [4/6] Counting lines of code..."

TOTAL_LOC=$(find apps/web/src -name "*.ts" -o -name "*.tsx" 2>/dev/null | xargs wc -l 2>/dev/null | tail -1 | awk '{print $1}' || echo "0")
echo "    📊 Total LOC: $TOTAL_LOC"

if [ "$TOTAL_LOC" -lt 120000 ]; then
  echo "    ✅ LOC under target (<120K)"
else
  echo "    ⚠️  LOC above target (>120K)"
fi

# ============================================================================
# CHECK 5: Route count
# ============================================================================
echo ""
echo "📋 [5/6] Counting routes..."

ROUTE_COUNT=$(grep -E 'route\(' apps/web/src/routes.ts 2>/dev/null | grep -v '//' | wc -l || echo "0")
echo "    📊 Active routes: $ROUTE_COUNT"

if [ "$ROUTE_COUNT" -lt 25 ]; then
  echo "    ✅ Route count under target (<25)"
else
  echo "    ⚠️  Route count above target (>25)"
fi

# ============================================================================
# CHECK 6: Build test
# ============================================================================
echo ""
echo "📋 [6/6] Testing build..."

if pnpm build > /dev/null 2>&1; then
  echo "    ✅ Build successful"
else
  echo "    ❌ Build failed - check errors with: pnpm build"
  ERRORS=$((ERRORS + 1))
fi

# ============================================================================
# SUMMARY
# ============================================================================
echo ""
echo "═══════════════════════════════════════"
if [ $ERRORS -eq 0 ]; then
  echo "✅ VALIDATION PASSED!"
  echo ""
  echo "📊 Cleanup Statistics:"
  echo "   • Lines of code: $TOTAL_LOC"
  echo "   • Active routes: $ROUTE_COUNT"
  echo "   • Deprecated files: 0"
  echo "   • Legacy imports: 0"
  echo ""
  echo "🎉 Codebase is clean and ready!"
else
  echo "❌ VALIDATION FAILED ($ERRORS errors)"
  echo ""
  echo "⚠️  Fix the errors above and run validation again"
  exit 1
fi
echo "═══════════════════════════════════════"
echo ""
