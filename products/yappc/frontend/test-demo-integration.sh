#!/bin/bash

# Canvas Refactoring Demo Test Script
echo "🎯 Testing Canvas Refactoring Demo Integration"
echo "=============================================="

cd /home/samujjwal/Developments/yappc/yappc/frontend

echo "📁 Checking demo files..."

# Check if main demo route exists
if [ -f "apps/web/src/routes/canvas-refactoring-demo.tsx" ]; then
    echo "✅ Demo route file exists"
else
    echo "❌ Demo route file missing"
    exit 1
fi

# Check if home page updated
if grep -q "canvas-refactoring-demo" apps/web/src/routes/home.tsx; then
    echo "✅ Home page updated with demo link"
else
    echo "❌ Home page missing demo link"
    exit 1
fi

# Check if route is registered
if grep -q "canvas-refactoring-demo" apps/web/src/routes.ts; then
    echo "✅ Route registered in routes.ts"
else
    echo "❌ Route not registered in routes.ts"
    exit 1
fi

echo ""
echo "🚀 Build Test..."

# Try to build
if pnpm --filter web run build > /dev/null 2>&1; then
    echo "✅ Build successful - no compilation errors"
else
    echo "❌ Build failed - there are compilation errors"
    echo "Running build with output..."
    pnpm --filter web run build
    exit 1
fi

echo ""
echo "🎉 Canvas Refactoring Demo Integration: COMPLETE!"
echo ""
echo "📖 How to test:"
echo "1. Start dev server: pnpm --filter web run dev"
echo "2. Open browser: http://localhost:5173"
echo "3. Click 'View Interactive Demo' button on home page"
echo "4. Navigate through Phase 1, 2, and 3 tabs to test features"
echo ""
echo "🔗 Direct URL: http://localhost:5173/canvas-refactoring-demo"
echo ""
echo "✨ Features to test:"
echo "   • Phase 1: Generic canvas with view modes and selection"
echo "   • Phase 2: Registry migration with component examples"
echo "   • Phase 3A: Performance optimization with large datasets"
echo "   • Phase 3B: Real-time collaboration simulation"  
echo "   • Phase 3C: Advanced history with branching"