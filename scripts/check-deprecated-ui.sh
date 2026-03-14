#!/bin/bash
#
# Check for deprecated @ghatana/ui usage in the repository
# This script is used by CI to prevent new deprecated usage from landing
#

set -e

DEPRECATED_PACKAGE="@ghatana/ui"
EXIT_CODE=0

echo "🔍 Checking for deprecated ${DEPRECATED_PACKAGE} usage..."

# Check 1: TypeScript/TSX imports
echo ""
echo "Checking TypeScript/TSX imports..."
if rg "${DEPRECATED_PACKAGE}" --type ts --type tsx --no-heading --line-number -n; then
    echo ""
    echo "❌ Found ${DEPRECATED_PACKAGE} imports in TypeScript/TSX files"
    EXIT_CODE=1
else
    echo "✅ No ${DEPRECATED_PACKAGE} imports in TypeScript/TSX files"
fi

# Check 2: Package.json dependencies
echo ""
echo "Checking package.json dependencies..."
if rg "\"${DEPRECATED_PACKAGE}\":" --type json -g 'package.json' --no-heading --line-number -n; then
    echo ""
    echo "❌ Found ${DEPRECATED_PACKAGE} in package.json dependencies"
    EXIT_CODE=1
else
    echo "✅ No ${DEPRECATED_PACKAGE} in package.json dependencies"
fi

# Check 3: Tailwind config references
echo ""
echo "Checking Tailwind config references..."
if rg "${DEPRECATED_PACKAGE}" -g 'tailwind.config.*' --no-heading --line-number -n; then
    echo ""
    echo "❌ Found ${DEPRECATED_PACKAGE} in Tailwind configs"
    EXIT_CODE=1
else
    echo "✅ No ${DEPRECATED_PACKAGE} in Tailwind configs"
fi

# Check 4: Check pnpm lockfile for workspace dependencies
echo ""
echo "Checking pnpm-lock.yaml for workspace references..."
if rg "${DEPRECATED_PACKAGE}@" pnpm-lock.yaml --no-heading --line-number -n | head -20; then
    echo ""
    echo "⚠️  Found ${DEPRECATED_PACKAGE} references in pnpm-lock.yaml"
    echo "   (This may be acceptable if consumers still exist during migration)"
fi

if [ $EXIT_CODE -eq 0 ]; then
    echo ""
    echo "✅ All checks passed - no new deprecated usage detected"
else
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "  ❌ DEPRECATED PACKAGE USAGE DETECTED"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""
    echo "  Found usage of ${DEPRECATED_PACKAGE}"
    echo ""
    echo "  Migration path:"
    echo "    - UI components → @ghatana/design-system"
    echo "    - Product-specific UI → product-local packages"
    echo ""
    echo "  See: docs/PLATFORM_SHARED_LIBRARIES_REMAINING_WORK_PLAN_2026-03-14.md"
    echo "═══════════════════════════════════════════════════════════════"
fi

exit $EXIT_CODE
