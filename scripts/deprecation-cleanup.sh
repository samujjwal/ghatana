#!/bin/bash
# Deprecation Cleanup Script
# Identifies and reports deprecated API usage across the codebase

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=== Ghatana Deprecation Cleanup Report ==="
echo "Date: $(date)"
echo ""

# Colors
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

# Counters
TOTAL_DEPRECATED=0
CRITICAL_COUNT=0
HIGH_COUNT=0

# Function to search for deprecated usage
search_deprecated() {
    local pattern=$1
    local description=$2
    local severity=$3
    
    echo -e "${YELLOW}Searching: ${description}${NC}"
    
    # Search in Java files
    java_count=$(grep -r --include="*.java" "$pattern" "$ROOT_DIR" 2>/dev/null | wc -l || echo 0)
    
    # Search in Kotlin files
    kotlin_count=$(grep -r --include="*.kt" "$pattern" "$ROOT_DIR" 2>/dev/null | wc -l || echo 0)
    
    # Search in TypeScript files
    ts_count=$(grep -r --include="*.ts" --include="*.tsx" "$pattern" "$ROOT_DIR" 2>/dev/null | wc -l || echo 0)
    
    total=$((java_count + kotlin_count + ts_count))
    
    if [ $total -gt 0 ]; then
        echo -e "  ${RED}Found $total occurrences${NC}"
        echo "    Java: $java_count"
        echo "    Kotlin: $kotlin_count"
        echo "    TypeScript: $ts_count"
        
        TOTAL_DEPRECATED=$((TOTAL_DEPRECATED + total))
        
        if [ "$severity" = "CRITICAL" ]; then
            CRITICAL_COUNT=$((CRITICAL_COUNT + total))
        elif [ "$severity" = "HIGH" ]; then
            HIGH_COUNT=$((HIGH_COUNT + total))
        fi
    else
        echo -e "  ${GREEN}✓ Clean${NC}"
    fi
    echo ""
}

echo "## Java Deprecated APIs"
echo ""

# Agent-Core deprecated types
search_deprecated "DETERMINISTIC_LEGACY" "AgentType.DETERMINISTIC_LEGACY" "HIGH"
search_deprecated "PROBABILISTIC_LEGACY" "AgentType.PROBABILISTIC_LEGACY" "HIGH"

# Kernel deprecated utilities
search_deprecated "JsonUtils\.toJson" "JsonUtils.toJson (use Jackson directly)" "MEDIUM"
search_deprecated "JsonUtils\.fromJson" "JsonUtils.fromJson (use Jackson directly)" "MEDIUM"

# Security deprecated fields
search_deprecated "User\.legacyRole" "User.legacyRole field" "HIGH"

echo "## TypeScript Deprecated Packages"
echo ""

# Deprecated package imports
search_deprecated "@ghatana/ui" "@ghatana/ui (use @ghatana/design-system)" "HIGH"
search_deprecated "@ghatana/utils" "@ghatana/utils (use @ghatana/foundation)" "HIGH"
search_deprecated "@ghatana/dcmaar" "@ghatana/dcmaar-* packages" "CRITICAL"
search_deprecated "@ghatana/yappc" "@ghatana/yappc-* packages" "CRITICAL"

echo "## Promise Pattern Issues"
echo ""

# Promise.ofBlocking without eventloop context
search_deprecated "Promise\.ofBlocking\(" "Promise.ofBlocking (check eventloop context)" "HIGH"

echo "## Summary"
echo ""
echo "Total deprecated references: $TOTAL_DEPRECATED"
echo "  Critical: $CRITICAL_COUNT"
echo "  High: $HIGH_COUNT"
echo ""

if [ $TOTAL_DEPRECATED -gt 0 ]; then
    echo -e "${RED}⚠ Deprecation cleanup required${NC}"
    echo ""
    echo "Next steps:"
    echo "1. Review deprecation policy: docs/DEPRECATION_POLICY.md"
    echo "2. Create migration issues for each deprecated API"
    echo "3. Schedule cleanup sprint"
    echo ""
    exit 1
else
    echo -e "${GREEN}✓ No deprecated API usage found${NC}"
    exit 0
fi
