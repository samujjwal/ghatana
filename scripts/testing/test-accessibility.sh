#!/bin/bash

# Accessibility Testing Script
# Runs automated accessibility checks using axe-core and generates report
#
# Usage: ./scripts/test-accessibility.sh [url]
# Example: ./scripts/test-accessibility.sh http://localhost:3000

set -e

URL="${1:-http://localhost:3000}"
OUTPUT_DIR="./accessibility-reports"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
REPORT_FILE="$OUTPUT_DIR/accessibility-report-$TIMESTAMP.json"
HTML_REPORT="$OUTPUT_DIR/accessibility-report-$TIMESTAMP.html"

echo "🔍 Running Accessibility Tests"
echo "Target URL: $URL"
echo "Report will be saved to: $OUTPUT_DIR"
echo ""

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Check if axe-cli is installed
if ! command -v axe &> /dev/null; then
    echo "❌ axe-cli not found. Installing..."
    npm install -g @axe-core/cli
fi

# Run axe accessibility scan
echo "Running axe-core scan..."
axe "$URL" \
    --save "$REPORT_FILE" \
    --tags wcag2a,wcag2aa,wcag21a,wcag21aa,best-practice \
    --exit || true

# Check if scan completed
if [ -f "$REPORT_FILE" ]; then
    echo "✅ Scan completed"
    
    # Parse results
    VIOLATIONS=$(cat "$REPORT_FILE" | jq '.violations | length')
    PASSES=$(cat "$REPORT_FILE" | jq '.passes | length')
    INCOMPLETE=$(cat "$REPORT_FILE" | jq '.incomplete | length')
    
    echo ""
    echo "📊 Results:"
    echo "  ✅ Passes: $PASSES"
    echo "  ❌ Violations: $VIOLATIONS"
    echo "  ⚠️  Incomplete: $INCOMPLETE"
    echo ""
    
    if [ "$VIOLATIONS" -gt 0 ]; then
        echo "❌ Accessibility violations found:"
        cat "$REPORT_FILE" | jq -r '.violations[] | "  - [\(.impact | ascii_upcase)] \(.id): \(.description)"'
        echo ""
        echo "See full report: $REPORT_FILE"
        exit 1
    else
        echo "✅ No accessibility violations found!"
    fi
else
    echo "❌ Scan failed"
    exit 1
fi

# Generate HTML report
echo ""
echo "Generating HTML report..."
cat > "$HTML_REPORT" << 'EOF'
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Accessibility Report</title>
    <style>
        body {
            font-family: system-ui, -apple-system, sans-serif;
            max-width: 1200px;
            margin: 0 auto;
            padding: 2rem;
            line-height: 1.6;
        }
        h1 { color: #1a202c; }
        .summary {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 1rem;
            margin: 2rem 0;
        }
        .card {
            padding: 1.5rem;
            border-radius: 0.5rem;
            box-shadow: 0 1px 3px rgba(0,0,0,0.1);
        }
        .passes { background: #f0fdf4; border-left: 4px solid #22c55e; }
        .violations { background: #fef2f2; border-left: 4px solid #ef4444; }
        .incomplete { background: #fffbeb; border-left: 4px solid #f59e0b; }
        .metric-value { font-size: 2.5rem; font-weight: bold; }
        .metric-label { color: #6b7280; text-transform: uppercase; font-size: 0.875rem; }
        .violation {
            background: #fff;
            border: 1px solid #e5e7eb;
            border-radius: 0.5rem;
            padding: 1rem;
            margin: 1rem 0;
        }
        .impact-critical { color: #dc2626; font-weight: bold; }
        .impact-serious { color: #ea580c; font-weight: bold; }
        .impact-moderate { color: #f59e0b; font-weight: bold; }
        .impact-minor { color: #3b82f6; font-weight: bold; }
        .help { color: #4b5563; margin: 0.5rem 0; }
        .nodes { margin-top: 1rem; }
        .node { background: #f9fafb; padding: 0.5rem; margin: 0.5rem 0; border-radius: 0.25rem; }
        code { background: #f3f4f6; padding: 0.25rem 0.5rem; border-radius: 0.25rem; font-size: 0.875rem; }
    </style>
</head>
<body>
    <h1>🔍 Accessibility Report</h1>
    <p><strong>Generated:</strong> <span id="timestamp"></span></p>
    <p><strong>URL:</strong> <span id="url"></span></p>
    
    <div class="summary">
        <div class="card passes">
            <div class="metric-value" id="passes-count">0</div>
            <div class="metric-label">Passes</div>
        </div>
        <div class="card violations">
            <div class="metric-value" id="violations-count">0</div>
            <div class="metric-label">Violations</div>
        </div>
        <div class="card incomplete">
            <div class="metric-value" id="incomplete-count">0</div>
            <div class="metric-label">Incomplete</div>
        </div>
    </div>
    
    <div id="violations-list"></div>
    
    <script>
        // Report data will be injected here
        const reportData = REPORT_DATA;
        
        document.getElementById('timestamp').textContent = reportData.timestamp;
        document.getElementById('url').textContent = reportData.url;
        document.getElementById('passes-count').textContent = reportData.passes.length;
        document.getElementById('violations-count').textContent = reportData.violations.length;
        document.getElementById('incomplete-count').textContent = reportData.incomplete.length;
        
        const violationsList = document.getElementById('violations-list');
        
        if (reportData.violations.length > 0) {
            violationsList.innerHTML = '<h2>❌ Violations</h2>';
            reportData.violations.forEach(violation => {
                const div = document.createElement('div');
                div.className = 'violation';
                div.innerHTML = `
                    <h3>
                        <span class="impact-${violation.impact}">[${violation.impact.toUpperCase()}]</span>
                        ${violation.id}
                    </h3>
                    <p class="help">${violation.help}</p>
                    <p><strong>Description:</strong> ${violation.description}</p>
                    <p><strong>Affected nodes:</strong> ${violation.nodes.length}</p>
                    <div class="nodes">
                        ${violation.nodes.map(node => `
                            <div class="node">
                                <code>${node.html}</code>
                                <p><strong>Fix:</strong> ${node.failureSummary}</p>
                            </div>
                        `).join('')}
                    </div>
                `;
                violationsList.appendChild(div);
            });
        } else {
            violationsList.innerHTML = '<h2>✅ No Violations Found</h2><p>Great job! Your application meets WCAG 2.1 AA standards.</p>';
        }
    </script>
</body>
</html>
EOF

# Inject report data into HTML
REPORT_DATA=$(cat "$REPORT_FILE")
sed -i "s|REPORT_DATA|$REPORT_DATA|g" "$HTML_REPORT"

echo "✅ HTML report generated: $HTML_REPORT"
echo ""
echo "To view the report, open:"
echo "  $HTML_REPORT"
