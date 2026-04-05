#!/bin/bash

# Coverage Report Script
# Generates comprehensive coverage reports for all products

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Generating Coverage Reports${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Function to run coverage for a module
run_coverage() {
    local module=$1
    local name=$2
    
    echo -e "${BLUE}Generating coverage for ${name}...${NC}"
    
    if ./gradlew "$module:jacocoTestReport" --quiet; then
        echo -e "${GREEN}✅ ${name} coverage report generated${NC}"
        
        # Extract coverage percentage if available
        local report_file="$PROJECT_ROOT/${module//://}/build/reports/jacoco/test/html/index.html"
        if [ -f "$report_file" ]; then
            echo -e "  Report: $report_file"
        fi
    else
        echo -e "${RED}❌ ${name} coverage report failed${NC}"
    fi
    echo ""
}

# Generate coverage reports
run_coverage ":platform:java:kernel" "Kernel"
run_coverage ":products:finance" "Finance"
run_coverage ":products:phr" "PHR"

# Generate aggregate report
echo -e "${BLUE}Generating aggregate coverage report...${NC}"
if ./gradlew jacocoTestReport --quiet; then
    echo -e "${GREEN}✅ Aggregate coverage report generated${NC}"
else
    echo -e "${YELLOW}⚠️  Aggregate coverage report skipped${NC}"
fi
echo ""

# Summary
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Coverage Reports Generated${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "View reports:"
echo -e "  Kernel:  open platform/java/kernel/build/reports/jacoco/test/html/index.html"
echo -e "  Finance: open products/finance/build/reports/jacoco/test/html/index.html"
echo -e "  PHR:     open products/phr/build/reports/jacoco/test/html/index.html"
echo ""
