#!/bin/bash
#
# Documentation Tag Validation Script
# Identifies public classes missing @doc.* tags per the audit requirements
#
# Usage: ./scripts/validate-doc-tags.sh [path]

SEARCH_PATH="${1:-products/yappc}"

echo "=========================================="
echo "Documentation Tag Validation"
echo "Searching: ${SEARCH_PATH}"
echo "=========================================="

# Count of missing @doc tags
MISSING_COUNT=0
MISSING_FILES=()

# Find all Java files with public classes/interfaces/enums without @doc tags
while IFS= read -r -d '' file; do
    # Skip if file contains @doc. annotation
    if grep -q "@doc\." "$file" 2>/dev/null; then
        continue
    fi
    
    # Check if file has public class/interface/enum/record
    if grep -qE "^public (class|interface|enum|record)" "$file" 2>/dev/null; then
        MISSING_COUNT=$((MISSING_COUNT + 1))
        MISSING_FILES+=("$file")
        
        # Extract class name
        CLASS_NAME=$(grep -oE "^public (class|interface|enum|record) [A-Za-z0-9_]+" "$file" | head -1)
        
        if [ $MISSING_COUNT -le 25 ]; then
            echo "MISSING: $file"
            echo "  $CLASS_NAME"
        fi
    fi
done < <(find "$SEARCH_PATH" -name "*.java" -type f -print0 2>/dev/null)

echo ""
echo "=========================================="
echo "SUMMARY"
echo "=========================================="
echo "Public classes without @doc tags: $MISSING_COUNT"
echo "Target from audit: 22"
echo ""

if [ $MISSING_COUNT -eq 0 ]; then
    echo "✓ All public classes have @doc tags"
    exit 0
elif [ $MISSING_COUNT -le 25 ]; then
    echo "⚠ $MISSING_COUNT classes need @doc tags (within acceptable range)"
    exit 0
else
    echo "✗ Too many undocumented classes (>25)"
    exit 1
fi
