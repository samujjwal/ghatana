#!/bin/bash

# Performance Benchmarking Script
# Measures Core Web Vitals and performance metrics for production readiness

set -e

echo "🚀 Starting Performance Benchmarking..."
echo ""

# Configuration
REPORT_DIR="performance/reports"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT_FILE="$REPORT_DIR/lighthouse_report_${TIMESTAMP}.json"
HTML_REPORT="$REPORT_DIR/lighthouse_report_${TIMESTAMP}.html"

mkdir -p "$REPORT_DIR"

# Lighthouse URLs to test
declare -a COMPONENTS=(
  "canvas--basic:Canvas%20Component"
  "designer--empty:Designer%20Component"
)

echo "📊 Running Lighthouse Audits..."
echo ""

# Run Lighthouse audits
pnpm exec lighthouse \
  "http://localhost:6006/iframe.html?id=canvas--basic" \
  --chrome-flags="--headless=new --no-sandbox" \
  --output=json \
  --output-path="$REPORT_FILE" \
  --quiet

# Also generate HTML report
pnpm exec lighthouse \
  "http://localhost:6006/iframe.html?id=canvas--basic" \
  --chrome-flags="--headless=new --no-sandbox" \
  --output=html \
  --output-path="$HTML_REPORT" \
  --quiet

echo "✅ Lighthouse audit complete!"
echo ""
echo "Reports generated:"
echo "  JSON: $REPORT_FILE"
echo "  HTML: $HTML_REPORT"

# Extract key metrics from JSON report
if [ -f "$REPORT_FILE" ]; then
  echo ""
  echo "📈 Key Metrics:"
  node -e "
    const data = require('./$REPORT_FILE');
    const { audits, categories } = data;
    
    // Extract LCP, FID, CLS
    const metrics = {
      'Performance Score': Math.round(categories.performance.score * 100),
      'Accessibility Score': Math.round(categories.accessibility.score * 100),
      'Best Practices Score': Math.round(categories['best-practices'].score * 100),
      'SEO Score': Math.round(categories.seo.score * 100),
    };
    
    Object.entries(metrics).forEach(([key, value]) => {
      console.log(\`  \${key}: \${value}%\`);
    });
  "
fi

echo ""
echo "✨ Performance benchmarking complete!"
