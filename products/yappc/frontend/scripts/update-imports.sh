#!/bin/bash
# Script to update deep imports to use path aliases
# Usage: ./scripts/update-imports.sh

set -e

echo "🔍 Finding files with deep imports..."

# Count files before update
DEEP_IMPORTS=$(find apps/web/src -type f \( -name "*.ts" -o -name "*.tsx" \) -exec grep -l "\.\./\.\./\.\./\.\." {} \; | wc -l)
echo "Found $DEEP_IMPORTS files with 4+ level deep imports"

echo ""
echo "📝 Updating imports..."
echo ""

# Update shared types imports (../../../../shared/types/*)
find apps/web/src -type f \( -name "*.ts" -o -name "*.tsx" \) -exec sed -i '' \
  -e "s|from ['\"]../../../../shared/types/lifecycle-artifacts['\"]|from '@/shared/types/lifecycle-artifacts'|g" \
  -e "s|from ['\"]../../../../../shared/types/lifecycle-artifacts['\"]|from '@/shared/types/lifecycle-artifacts'|g" \
  -e "s|from ['\"]../../../../shared/types/lifecycle['\"]|from '@/shared/types/lifecycle'|g" \
  -e "s|from ['\"]../../../../../shared/types/lifecycle['\"]|from '@/shared/types/lifecycle'|g" \
  -e "s|from ['\"]../../../../shared/types/|from '@/shared/types/|g" \
  -e "s|from ['\"]../../../../../shared/types/|from '@/shared/types/|g" \
  {} \;

echo "✅ Updated shared/types imports"

# Update state atoms imports (../../../../state/atoms/*)
find apps/web/src -type f \( -name "*.ts" -o -name "*.tsx" \) -exec sed -i '' \
  -e "s|from ['\"]../../../../state/atoms/canvasAtom['\"]|from '@/state/atoms/canvasAtom'|g" \
  -e "s|from ['\"]../../../../../state/atoms/canvasAtom['\"]|from '@/state/atoms/canvasAtom'|g" \
  -e "s|from ['\"]../../../../state/atoms/|from '@/state/atoms/|g" \
  -e "s|from ['\"]../../../../../state/atoms/|from '@/state/atoms/|g" \
  -e "s|from ['\"]../../../../state/|from '@/state/|g" \
  -e "s|from ['\"]../../../../../state/|from '@/state/|g" \
  {} \;

echo "✅ Updated state/atoms imports"

# Update services imports (../../../../services/*)
find apps/web/src -type f \( -name "*.ts" -o -name "*.tsx" \) -exec sed -i '' \
  -e "s|from ['\"]../../../../services/canvas/CanvasPersistence['\"]|from '@/services/canvas/CanvasPersistence'|g" \
  -e "s|from ['\"]../../../../services/canvas/CanvasEditor['\"]|from '@/services/canvas/CanvasEditor'|g" \
  -e "s|from ['\"]../../../../services/canvas/lifecycle/CanvasLifecycle['\"]|from '@/services/canvas/lifecycle/CanvasLifecycle'|g" \
  -e "s|from ['\"]../../../../services/canvas/|from '@/services/canvas/|g" \
  -e "s|from ['\"]../../../../../services/canvas/|from '@/services/canvas/|g" \
  -e "s|from ['\"]../../../../services/|from '@/services/|g" \
  -e "s|from ['\"]../../../../../services/|from '@/services/|g" \
  {} \;

echo "✅ Updated services imports"

# Update hooks imports (../../../../hooks/*)
find apps/web/src -type f \( -name "*.ts" -o -name "*.tsx" \) -exec sed -i '' \
  -e "s|from ['\"]../../../../hooks/useCanvasMode['\"]|from '@/hooks/useCanvasMode'|g" \
  -e "s|from ['\"]../../../../hooks/useCanvasPanels['\"]|from '@/hooks/useCanvasPanels'|g" \
  -e "s|from ['\"]../../../../hooks/useAbstractionLevel['\"]|from '@/hooks/useAbstractionLevel'|g" \
  -e "s|from ['\"]../../../../hooks/useTechStack['\"]|from '@/hooks/useTechStack'|g" \
  -e "s|from ['\"]../../../../hooks/useAIAssistant['\"]|from '@/hooks/useAIAssistant'|g" \
  -e "s|from ['\"]../../../../hooks/useCanvasValidation['\"]|from '@/hooks/useCanvasValidation'|g" \
  -e "s|from ['\"]../../../../hooks/useLifecyclePhaseTransition['\"]|from '@/hooks/useLifecyclePhaseTransition'|g" \
  -e "s|from ['\"]../../../../hooks/|from '@/hooks/|g" \
  -e "s|from ['\"]../../../../../hooks/|from '@/hooks/|g" \
  {} \;

echo "✅ Updated hooks imports"

# Update components imports (../../../../components/*)
find apps/web/src -type f \( -name "*.ts" -o -name "*.tsx" \) -exec sed -i '' \
  -e "s|from ['\"]../../../../components/canvas/UnifiedLeftPanel['\"]|from '@/components/canvas/UnifiedLeftPanel'|g" \
  -e "s|from ['\"]../../../../components/canvas/CanvasModeSelector['\"]|from '@/components/canvas/CanvasModeSelector'|g" \
  -e "s|from ['\"]../../../../components/canvas/CanvasPhaseNavigator['\"]|from '@/components/canvas/CanvasPhaseNavigator'|g" \
  -e "s|from ['\"]../../../../components/canvas/CanvasStatusBar['\"]|from '@/components/canvas/CanvasStatusBar'|g" \
  -e "s|from ['\"]../../../../components/canvas/CanvasProgressWidget['\"]|from '@/components/canvas/CanvasProgressWidget'|g" \
  -e "s|from ['\"]../../../../components/canvas/ComponentPalette['\"]|from '@/components/canvas/ComponentPalette'|g" \
  -e "s|from ['\"]../../../../components/canvas/HistoryToolbar['\"]|from '@/components/canvas/HistoryToolbar'|g" \
  -e "s|from ['\"]../../../../components/canvas/|from '@/components/canvas/|g" \
  -e "s|from ['\"]../../../../../components/canvas/|from '@/components/canvas/|g" \
  -e "s|from ['\"]../../../../components/|from '@/components/|g" \
  -e "s|from ['\"]../../../../../components/|from '@/components/|g" \
  {} \;

echo "✅ Updated components imports"

# Update type imports to use 'import type' where appropriate
find apps/web/src -type f \( -name "*.ts" -o -name "*.tsx" \) -exec sed -i '' \
  -e "s|import { CanvasState, CanvasElement } from '@/state/atoms/canvasAtom'|import type { CanvasState, CanvasElement } from '@/state/atoms/canvasAtom'|g" \
  -e "s|import { CanvasState } from '@/state/atoms/canvasAtom'|import type { CanvasState } from '@/state/atoms/canvasAtom'|g" \
  -e "s|import { CanvasElement } from '@/state/atoms/canvasAtom'|import type { CanvasElement } from '@/state/atoms/canvasAtom'|g" \
  -e "s|import { LifecyclePhase } from '@/shared/types/lifecycle'|import type { LifecyclePhase } from '@/shared/types/lifecycle'|g" \
  {} \;

echo "✅ Updated type imports to use 'import type'"

echo ""
echo "🔍 Checking remaining deep imports..."
REMAINING=$(find apps/web/src -type f \( -name "*.ts" -o -name "*.tsx" \) -exec grep -l "\.\./\.\./\.\./\.\." {} \; | wc -l)
echo "Remaining files with 4+ level deep imports: $REMAINING"

if [ "$REMAINING" -gt 0 ]; then
  echo ""
  echo "📋 Files still needing manual review:"
  find apps/web/src -type f \( -name "*.ts" -o -name "*.tsx" \) -exec grep -l "\.\./\.\./\.\./\.\." {} \;
fi

echo ""
echo "✅ Import update complete!"
echo ""
echo "Next steps:"
echo "1. Run: pnpm typecheck"
echo "2. Fix any remaining type errors"
echo "3. Run: pnpm test"
echo "4. Commit changes"
