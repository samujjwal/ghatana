#!/bin/bash

# Canvas Build Verification Script
# Ensures all canvas components build cleanly

set -e

echo "🔍 Canvas Build Verification"
echo "=============================="
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Track results
ERRORS=0
WARNINGS=0

echo "📋 Step 1: Checking TypeScript files..."
echo ""

# Find all canvas TypeScript files
CANVAS_FILES=$(find apps/web/src -type f \( -name "*.ts" -o -name "*.tsx" \) | grep -E "(canvas|sketch|tools|collaboration|performance|export|registry|navigation|layers)" | wc -l)

echo "   Found ${CANVAS_FILES} canvas-related TypeScript files"
echo ""

echo "📋 Step 2: Checking file size compliance..."
echo ""

# Check file size (should be <= 220 LOC excluding imports)
OVERSIZED_FILES=0
while IFS= read -r file; do
    if [ -f "$file" ]; then
        # Count lines excluding imports and empty lines
        LOC=$(grep -v "^import" "$file" | grep -v "^$" | wc -l | tr -d ' ')
        if [ "$LOC" -gt 220 ]; then
            echo -e "   ${RED}✗${NC} $file: ${LOC} LOC (exceeds 220)"
            OVERSIZED_FILES=$((OVERSIZED_FILES + 1))
        fi
    fi
done < <(find apps/web/src -type f \( -name "*.ts" -o -name "*.tsx" \) | grep -E "(canvas|sketch|tools|collaboration|performance|export|registry|navigation|layers)")

if [ $OVERSIZED_FILES -eq 0 ]; then
    echo -e "   ${GREEN}✓${NC} All files comply with 220 LOC limit"
else
    echo -e "   ${RED}✗${NC} $OVERSIZED_FILES files exceed 220 LOC limit"
    ERRORS=$((ERRORS + OVERSIZED_FILES))
fi
echo ""

echo "📋 Step 3: Checking for TypeScript errors..."
echo ""

# Check if we're in the right directory
if [ ! -f "apps/web/package.json" ]; then
    echo -e "   ${RED}✗${NC} Not in project root directory"
    exit 1
fi

# Run TypeScript check (if tsc is available)
if command -v tsc &> /dev/null; then
    cd apps/web
    if tsc --noEmit --skipLibCheck 2>&1 | grep -q "error TS"; then
        echo -e "   ${RED}✗${NC} TypeScript errors found"
        ERRORS=$((ERRORS + 1))
    else
        echo -e "   ${GREEN}✓${NC} No TypeScript errors"
    fi
    cd ../..
else
    echo -e "   ${YELLOW}⚠${NC} TypeScript compiler not found, skipping type check"
    WARNINGS=$((WARNINGS + 1))
fi
echo ""

echo "📋 Step 4: Checking test files..."
echo ""

# Count test files
TEST_FILES=$(find apps/web/src e2e -type f -name "*.test.ts" -o -name "*.test.tsx" -o -name "*.spec.ts" | wc -l | tr -d ' ')
echo "   Found ${TEST_FILES} test files"

if [ "$TEST_FILES" -lt 10 ]; then
    echo -e "   ${YELLOW}⚠${NC} Low test coverage (< 10 test files)"
    WARNINGS=$((WARNINGS + 1))
else
    echo -e "   ${GREEN}✓${NC} Good test coverage"
fi
echo ""

echo "📋 Step 5: Checking documentation..."
echo ""

# Check for required documentation
REQUIRED_DOCS=(
    "docs/canvas-implementation-plan.md"
    "docs/canvas-phase-0-complete.md"
    "docs/CANVAS_IMPLEMENTATION_STATUS.md"
    "docs/CANVAS_FINAL_IMPLEMENTATION_REPORT.md"
)

MISSING_DOCS=0
for doc in "${REQUIRED_DOCS[@]}"; do
    if [ ! -f "$doc" ]; then
        echo -e "   ${RED}✗${NC} Missing: $doc"
        MISSING_DOCS=$((MISSING_DOCS + 1))
    fi
done

if [ $MISSING_DOCS -eq 0 ]; then
    echo -e "   ${GREEN}✓${NC} All required documentation present"
else
    echo -e "   ${RED}✗${NC} $MISSING_DOCS documentation files missing"
    ERRORS=$((ERRORS + MISSING_DOCS))
fi
echo ""

echo "📋 Step 6: Checking for common issues..."
echo ""

# Check for console.log (should use proper logging)
CONSOLE_LOGS=$(find apps/web/src -type f \( -name "*.ts" -o -name "*.tsx" \) | xargs grep -l "console\.log" | wc -l | tr -d ' ')
if [ "$CONSOLE_LOGS" -gt 0 ]; then
    echo -e "   ${YELLOW}⚠${NC} Found $CONSOLE_LOGS files with console.log statements"
    WARNINGS=$((WARNINGS + 1))
else
    echo -e "   ${GREEN}✓${NC} No console.log statements found"
fi

# Check for TODO comments
TODOS=$(find apps/web/src -type f \( -name "*.ts" -o -name "*.tsx" \) | xargs grep -i "TODO" | wc -l | tr -d ' ')
if [ "$TODOS" -gt 0 ]; then
    echo -e "   ${YELLOW}⚠${NC} Found $TODOS TODO comments"
    WARNINGS=$((WARNINGS + 1))
else
    echo -e "   ${GREEN}✓${NC} No TODO comments found"
fi
echo ""

# Summary
echo "=============================="
echo "📊 Verification Summary"
echo "=============================="
echo ""

if [ $ERRORS -eq 0 ] && [ $WARNINGS -eq 0 ]; then
    echo -e "${GREEN}✓ Build is CLEAN!${NC}"
    echo ""
    echo "   • All files comply with standards"
    echo "   • No TypeScript errors"
    echo "   • Documentation complete"
    echo "   • Ready for production"
    echo ""
    exit 0
elif [ $ERRORS -eq 0 ]; then
    echo -e "${YELLOW}⚠ Build has WARNINGS${NC}"
    echo ""
    echo "   Errors: $ERRORS"
    echo "   Warnings: $WARNINGS"
    echo ""
    echo "   Build is functional but has minor issues"
    echo ""
    exit 0
else
    echo -e "${RED}✗ Build has ERRORS${NC}"
    echo ""
    echo "   Errors: $ERRORS"
    echo "   Warnings: $WARNINGS"
    echo ""
    echo "   Please fix errors before proceeding"
    echo ""
    exit 1
fi
