#!/bin/bash
# update-routes-config.sh - Updates routes.ts to remove deleted routes
# Run AFTER master-cleanup.sh and fix-imports-after-cleanup.sh

set -e
cd "$(dirname "$0")/.."

ROUTES_FILE="apps/web/src/routes.ts"

echo "🔧 Updating routes.ts configuration..."
echo ""

if [ ! -f "$ROUTES_FILE" ]; then
  echo "❌ Error: $ROUTES_FILE not found!"
  exit 1
fi

# Create backup
cp "$ROUTES_FILE" "$ROUTES_FILE.backup"
echo "📦 Created backup: $ROUTES_FILE.backup"

# Remove specific deprecated route entries
echo "🔄 Removing deprecated route entries..."

# Remove canvas redirect route
sed -i '' '/route("canvas", "routes\/canvas-redirect.tsx")/d' "$ROUTES_FILE"

# Remove page-designer route
sed -i '' '/route("page-designer", "routes\/page-designer.tsx")/d' "$ROUTES_FILE"

# Remove workflows routes (multi-line, so more complex)
sed -i '' '/route("workflows"/{
N
N
N
N
d
}' "$ROUTES_FILE" 2>/dev/null || true

# Remove any commented journey/lifecycle routes (already commented but clean up)
sed -i '' '/\/\* *route("journey"/,/\*\//d' "$ROUTES_FILE" 2>/dev/null || true

echo "    ✅ Deprecated routes removed from config"

# Verify syntax
echo ""
echo "🔍 Verifying routes.ts syntax..."
if pnpm tsc --noEmit "$ROUTES_FILE" 2>/dev/null; then
  echo "    ✅ routes.ts syntax is valid"
else
  echo "    ⚠️  Syntax errors detected, check $ROUTES_FILE manually"
  echo "    Backup available at: $ROUTES_FILE.backup"
fi

echo ""
echo "✅ Routes configuration updated!"
echo ""
echo "📋 Remaining routes:"
grep -E 'route\(' "$ROUTES_FILE" | grep -v '//' | head -20
echo ""
echo "⚠️  Next steps:"
echo "   1. Review changes: git diff $ROUTES_FILE"
echo "   2. Build: pnpm build"
echo "   3. Test routes manually"
echo ""
