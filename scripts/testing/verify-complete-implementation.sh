#!/bin/bash

# Complete YAPPC Implementation Verification
# Verifies all phases: Immediate, Short-term, and Medium-term

echo "🔍 YAPPC Complete Implementation Verification"
echo "============================================="
echo ""

PASS=0
FAIL=0

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

check() {
  if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ $1${NC}"
    ((PASS++))
  else
    echo -e "${RED}❌ $1${NC}"
    ((FAIL++))
  fi
}

# ============================================================================
# PHASE 1: IMMEDIATE TASKS
# ============================================================================

echo "📋 Phase 1: Immediate Tasks"
echo ""

# 1. Yappc has MUI theme
grep -q "accessibleMuiTheme" products/yappc/frontend/apps/web/src/root.tsx
check "Yappc uses accessibleMuiTheme"

grep -q "ThemeProvider" products/yappc/frontend/apps/web/src/root.tsx
check "Yappc has ThemeProvider"

# 2. Yappc has safe-area CSS
grep -q "@ghatana/tokens/safe-area.css" products/yappc/frontend/apps/web/src/root.tsx
check "Yappc imports safe-area.css"

# 3. useDialog re-exported
grep -q "@ghatana/ui" products/yappc/frontend/apps/web/src/hooks/useDialog.ts
check "Yappc useDialog re-exported from @ghatana/ui"

echo ""

# ============================================================================
# PHASE 2: SHORT-TERM TASKS
# ============================================================================

echo "📋 Phase 2: Short-term Tasks"
echo ""

# 4. Dialog analysis script exists
[ -f "scripts/analyze-flashit-dialogs.sh" ]
check "Flashit dialog analysis script created"

[ -x "scripts/analyze-flashit-dialogs.sh" ]
check "Flashit dialog analysis script is executable"

# 5. Dialog migration guide exists
[ -f "DIALOG_MIGRATION_GUIDE.md" ]
check "Dialog migration guide exists"

# 6. Accessibility audit script exists
[ -f "scripts/run-accessibility-audit.sh" ]
check "Accessibility audit script created"

[ -x "scripts/run-accessibility-audit.sh" ]
check "Accessibility audit script is executable"

echo ""

# ============================================================================
# PHASE 3: MEDIUM-TERM TASKS
# ============================================================================

echo "📋 Phase 3: Medium-term Tasks"
echo ""

# 7. CommandPalette component
[ -f "libs/typescript/ui/src/components/CommandPalette.tsx" ]
check "CommandPalette component created"

grep -q "export.*CommandPalette" libs/typescript/ui/src/index.ts
check "CommandPalette exported from @ghatana/ui"

grep -q "CommandItem" libs/typescript/ui/src/components/CommandPalette.tsx
check "CommandPalette has CommandItem type"

grep -q "fuzzySearch" libs/typescript/ui/src/components/CommandPalette.tsx
check "CommandPalette has fuzzy search"

# 8. MobileShell component
[ -f "libs/typescript/ui/src/components/MobileShell.tsx" ]
check "MobileShell component created"

grep -q "export.*MobileShell" libs/typescript/ui/src/index.ts
check "MobileShell exported from @ghatana/ui"

grep -q "BottomNavItem" libs/typescript/ui/src/components/MobileShell.tsx
check "MobileShell has BottomNavItem type"

grep -q "FABConfig" libs/typescript/ui/src/components/MobileShell.tsx
check "MobileShell has FAB support"

grep -q "safe-area\|safe-top\|safe-bottom" libs/typescript/ui/src/components/MobileShell.tsx
check "MobileShell uses safe-area CSS"

# 9. Collaboration service
[ -f "libs/typescript/ui/src/collaboration/index.tsx" ]
check "Collaboration service created"

grep -q "export.*collaboration" libs/typescript/ui/src/index.ts
check "Collaboration exported from @ghatana/ui"

grep -q "usePresence" libs/typescript/ui/src/collaboration/index.tsx
check "Collaboration has usePresence hook"

grep -q "useCursor" libs/typescript/ui/src/collaboration/index.tsx
check "Collaboration has useCursor hook"

grep -q "WebSocket" libs/typescript/ui/src/collaboration/index.tsx
check "Collaboration uses WebSocket"

echo ""

# ============================================================================
# SUMMARY
# ============================================================================

echo "============================================="
echo "Summary:"
echo ""
echo -e "✅ Passed: ${GREEN}$PASS${NC}"
echo -e "❌ Failed: ${RED}$FAIL${NC}"
echo ""

if [ $FAIL -eq 0 ]; then
  echo -e "${GREEN}🎉 All implementations verified successfully!${NC}"
  echo ""
  echo "Next steps:"
  echo "  1. Build shared libraries: pnpm --filter '@ghatana/*' build"
  echo "  2. Test CommandPalette: Import and use in any product"
  echo "  3. Test MobileShell: Apply to mobile apps"
  echo "  4. Test Collaboration: Set up WebSocket backend"
  echo "  5. Run accessibility audit: ./scripts/run-accessibility-audit.sh"
  echo ""
  exit 0
else
  echo -e "${RED}⚠️  Some verifications failed. Please review and fix.${NC}"
  echo ""
  exit 1
fi
