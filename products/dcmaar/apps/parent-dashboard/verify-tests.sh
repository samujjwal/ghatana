#!/bin/bash
# Quick verification script for parent-dashboard tests

echo "=== Parent Dashboard Test Verification ==="
echo ""
echo "Date: $(date)"
echo "Directory: $(pwd)"
echo ""

# Check if @tanstack/react-query is installed
echo "1. Checking @tanstack/react-query installation..."
if [ -L "node_modules/@tanstack/react-query" ]; then
    echo "   ✅ @tanstack/react-query is properly linked"
    ls -l node_modules/@tanstack/react-query | cut -d'>' -f2
else
    echo "   ❌ @tanstack/react-query not found"
fi
echo ""

# Check test configuration
echo "2. Checking test configuration..."
if grep -q "@tanstack/react-query" package.json; then
    echo "   ✅ @tanstack/react-query in package.json dependencies"
else
    echo "   ❌ @tanstack/react-query missing from package.json"
fi

if grep -q "test:unit" package.json; then
    echo "   ✅ Separate test:unit script exists"
else
    echo "   ⚠️  No test:unit script"
fi

if grep -q "test:e2e" package.json; then
    echo "   ✅ Separate test:e2e script exists"
else
    echo "   ⚠️  No test:e2e script"
fi
echo ""

# Check vitest config
echo "3. Checking vitest configuration..."
if grep -q "exclude.*e2e" vitest.config.ts; then
    echo "   ✅ e2e folder excluded from vitest"
else
    echo "   ⚠️  e2e folder not excluded from vitest"
fi

if grep -q "@tanstack/react-query" vitest.config.ts; then
    echo "   ✅ @tanstack/react-query in vitest inline deps"
else
    echo "   ⚠️  @tanstack/react-query not in vitest config"
fi
echo ""

# Check playwright config
echo "4. Checking playwright configuration..."
if grep -q "testIgnore.*src/test" playwright.config.ts; then
    echo "   ✅ src/test folder excluded from playwright"
else
    echo "   ⚠️  src/test folder not excluded from playwright"
fi
echo ""

echo "=== Verification Complete ==="
echo ""
echo "To run tests:"
echo "  pnpm test:unit    # Run unit tests"
echo "  pnpm test:e2e     # Run e2e tests"

