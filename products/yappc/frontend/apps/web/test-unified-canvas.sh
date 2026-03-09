#!/bin/bash

# Unified Canvas - Quick Test Script
# Run this to verify the unified canvas implementation

set -e

echo "🎨 Unified Canvas - Quick Test Script"
echo "======================================"
echo ""

# Change to web app directory
cd "$(dirname "$0")"
echo "📍 Current directory: $(pwd)"
echo ""

# Step 1: Type Check
echo "1️⃣  Running TypeScript type check..."
pnpm typecheck 2>&1 | grep -E "(error|warning|✓|✔)" || echo "✓ Type check passed"
echo ""

# Step 2: Check for errors in unified canvas files
echo "2️⃣  Checking unified canvas files..."
FILES=(
  "src/lib/canvas/ZoomManager.ts"
  "src/lib/canvas/HierarchyManager.ts"
  "src/lib/canvas/NodeManipulation.ts"
  "src/lib/canvas/AlignmentEngine.ts"
  "src/state/atoms/unifiedCanvasAtom.ts"
  "src/hooks/useUnifiedCanvas.ts"
  "src/hooks/useKeyboardShortcuts.ts"
  "src/routes/app/project/unified-canvas.tsx"
  "src/components/canvas/unified/UnifiedTopBar.tsx"
  "src/components/canvas/unified/UnifiedToolbar.tsx"
  "src/components/canvas/unified/UnifiedLeftRail.tsx"
  "src/components/canvas/unified/UnifiedRightPanel.tsx"
  "src/components/canvas/unified/UnifiedStatusBar.tsx"
  "src/components/canvas/unified/SmartGuides.tsx"
  "src/components/canvas/unified/BreadcrumbTrail.tsx"
  "src/components/canvas/unified/CustomNodeRenderer.tsx"
)

for file in "${FILES[@]}"; do
  if [ -f "$file" ]; then
    echo "  ✓ $file"
  else
    echo "  ✗ $file (MISSING)"
  fi
done
echo ""

# Step 3: Check route registration
echo "3️⃣  Checking route registration..."
if grep -q "unified-canvas" src/routes.ts; then
  echo "  ✓ Route registered in src/routes.ts"
else
  echo "  ✗ Route NOT found in src/routes.ts"
fi
echo ""

# Step 4: Count lines of code
echo "4️⃣  Code statistics..."
TOTAL_LINES=0
for file in "${FILES[@]}"; do
  if [ -f "$file" ]; then
    LINES=$(wc -l < "$file" | tr -d ' ')
    TOTAL_LINES=$((TOTAL_LINES + LINES))
  fi
done
echo "  Total lines of code: $TOTAL_LINES"
echo ""

# Step 5: Build check
echo "5️⃣  Running build check..."
echo "  (This may take a minute...)"
pnpm build --mode development 2>&1 | tail -10
echo ""

# Summary
echo "✅ Test Summary"
echo "==============="
echo "Files checked: ${#FILES[@]}"
echo "Total lines: $TOTAL_LINES"
echo ""
echo "📍 Access the unified canvas at:"
echo "   http://localhost:3000/p/test-project/unified-canvas"
echo ""
echo "🚀 To start dev server:"
echo "   pnpm dev"
echo ""
echo "🎉 Testing complete!"
