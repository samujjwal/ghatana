#!/bin/bash

# Comprehensive Accessibility Audit Script
# Uses @yappc/accessibility-audit to test all products

set -e

echo "♿ YAPPC Comprehensive Accessibility Audit"
echo "=========================================="
echo ""

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to run audit for a product
run_audit() {
  local product=$1
  local url=$2
  
  echo -e "${YELLOW}Testing: $product${NC}"
  echo "URL: $url"
  echo ""
  
  # Check if product has test script
  if [ -f "products/$product/package.json" ]; then
    if grep -q "test:a11y" "products/$product/package.json"; then
      echo "✅ Found test:a11y script"
      cd "products/$product"
      pnpm test:a11y || echo -e "${RED}❌ Audit failed${NC}"
      cd ../..
    else
      echo -e "${YELLOW}⚠️  No test:a11y script found${NC}"
    fi
  else
    echo -e "${RED}❌ Product package.json not found${NC}"
  fi
  
  echo ""
  echo "---"
  echo ""
}

# Main audit execution
echo "Starting audits for all products..."
echo ""

# Yappc App Creator
run_audit "yappc/frontend" "http://localhost:5173"

# Flashit
run_audit "flashit" "http://localhost:3000"

# Software-org
run_audit "software-org" "http://localhost:3001"

echo ""
echo -e "${GREEN}✅ Audit complete!${NC}"
echo ""
echo "📊 Summary:"
echo "  - Yappc: Check WCAG 2.1 AA compliance"
echo "  - Flashit: Review touch targets and mobile UX"
echo "  - Software-org: Verify keyboard navigation"
echo ""
echo "📝 Next steps:"
echo "  1. Review audit reports in each product's test output"
echo "  2. Address any violations found"
echo "  3. Re-run audits to verify fixes"
echo ""
echo "🔧 Manual testing checklist:"
echo "  □ Keyboard navigation (Tab, Enter, Escape)"
echo "  □ Screen reader compatibility (NVDA, JAWS, VoiceOver)"
echo "  □ Color contrast ratios (4.5:1 for text, 3:1 for UI)"
echo "  □ Touch target sizes (44x44px minimum)"
echo "  □ Focus indicators (visible and clear)"
echo "  □ Semantic HTML (headings, landmarks, labels)"
echo "  □ Alternative text for images"
echo "  □ Form labels and error messages"
echo "  □ Skip links and landmarks"
echo "  □ Responsive design (mobile, tablet, desktop)"
echo ""
