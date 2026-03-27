#!/bin/bash

###############################################################################
# YAPPC Agent Files Migration Script
#
# Intelligently migrates agent specialist files from the monolithic
# agents/specialists module to focused domain modules:
# - code-specialists: Code analysis, generation, refactoring
# - architecture-specialists: Design, patterns, architecture
# - testing-specialists: Test generation, validation, coverage
#
# Usage: ./scripts/migrate-agent-files.sh [--dry-run]
###############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAPPC_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

DRY_RUN=false
if [[ "$1" == "--dry-run" ]]; then
    DRY_RUN=true
    echo "🔍 Running in DRY RUN mode - no files will be moved"
fi

echo "🚀 YAPPC Agent Files Migration"
echo "=============================="
echo ""

SOURCE_DIR="$YAPPC_ROOT/core/agents/specialists/src/main/java/com/ghatana/yappc/agent/specialists"
SOURCE_TEST_DIR="$YAPPC_ROOT/core/agents/specialists/src/test/java/com/ghatana/yappc/agent/specialists"

CODE_DIR="$YAPPC_ROOT/core/agents/code-specialists/src/main/java/com/ghatana/yappc/agents/code"
ARCH_DIR="$YAPPC_ROOT/core/agents/architecture-specialists/src/main/java/com/ghatana/yappc/agents/architecture"
TEST_DIR="$YAPPC_ROOT/core/agents/testing-specialists/src/main/java/com/ghatana/yappc/agents/testing"

CODE_TEST_DIR="$YAPPC_ROOT/core/agents/code-specialists/src/test/java/com/ghatana/yappc/agents/code"
ARCH_TEST_DIR="$YAPPC_ROOT/core/agents/architecture-specialists/src/test/java/com/ghatana/yappc/agents/architecture"
TEST_TEST_DIR="$YAPPC_ROOT/core/agents/testing-specialists/src/test/java/com/ghatana/yappc/agents/testing"

# Counters
CODE_COUNT=0
ARCH_COUNT=0
TEST_COUNT=0

###############################################################################
# Categorization patterns
###############################################################################

# Code specialists: code analysis, generation, refactoring, implementation
CODE_PATTERNS=(
    "*Code*" "*Implement*" "*Refactor*" "*Generate*" "*Review*"
    "*Debug*" "*Optimize*" "*Format*" "*Lint*" "*Style*"
    "*React*" "*Java*" "*Python*" "*TypeScript*" "*Frontend*" "*Backend*"
    "*Api*" "*Db*" "*Database*" "*Query*" "*Sql*"
)

# Architecture specialists: design, patterns, architecture, documentation
ARCH_PATTERNS=(
    "*Architect*" "*Design*" "*Pattern*" "*Structure*" "*Model*"
    "*Doc*" "*Diagram*" "*Blueprint*" "*Plan*" "*Spec*"
    "*System*" "*Component*" "*Module*" "*Service*" "*Cloud*"
    "*Security*" "*Performance*" "*Scale*"
)

# Testing specialists: test generation, validation, coverage
TEST_PATTERNS=(
    "*Test*" "*Qa*" "*Quality*" "*Validate*" "*Verify*"
    "*Coverage*" "*E2e*" "*Integration*" "*Unit*" "*Smoke*"
    "*Benchmark*" "*Load*" "*Stress*" "*Chaos*"
)

###############################################################################
# Function to categorize file
###############################################################################
categorize_file() {
    local filename="$1"
    
    # Check test patterns first (most specific)
    for pattern in "${TEST_PATTERNS[@]}"; do
        if [[ "$filename" == $pattern ]]; then
            echo "testing"
            return
        fi
    done
    
    # Check architecture patterns
    for pattern in "${ARCH_PATTERNS[@]}"; do
        if [[ "$filename" == $pattern ]]; then
            echo "architecture"
            return
        fi
    done
    
    # Check code patterns
    for pattern in "${CODE_PATTERNS[@]}"; do
        if [[ "$filename" == $pattern ]]; then
            echo "code"
            return
        fi
    done
    
    # Default to code if no match
    echo "code"
}

###############################################################################
# Function to move file
###############################################################################
move_file() {
    local src="$1"
    local dest_dir="$2"
    local category="$3"
    local filename=$(basename "$src")
    
    if [[ "$DRY_RUN" == false ]]; then
        mkdir -p "$dest_dir"
        mv "$src" "$dest_dir/"
    fi
    
    case "$category" in
        code)
            ((CODE_COUNT++))
            ;;
        architecture)
            ((ARCH_COUNT++))
            ;;
        testing)
            ((TEST_COUNT++))
            ;;
    esac
}

###############################################################################
# Migrate main source files
###############################################################################
echo "📦 Analyzing and migrating agent files..."
echo ""

if [[ -d "$SOURCE_DIR" ]]; then
    for file in "$SOURCE_DIR"/*.java; do
        if [[ -f "$file" ]]; then
            filename=$(basename "$file")
            category=$(categorize_file "$filename")
            
            case "$category" in
                code)
                    echo "  📝 $filename → code-specialists"
                    move_file "$file" "$CODE_DIR" "code"
                    ;;
                architecture)
                    echo "  🏛️  $filename → architecture-specialists"
                    move_file "$file" "$ARCH_DIR" "architecture"
                    ;;
                testing)
                    echo "  🧪 $filename → testing-specialists"
                    move_file "$file" "$TEST_DIR" "testing"
                    ;;
            esac
        fi
    done
fi

###############################################################################
# Migrate test files
###############################################################################
echo ""
echo "📦 Migrating test files..."
echo ""

if [[ -d "$SOURCE_TEST_DIR" ]]; then
    for file in "$SOURCE_TEST_DIR"/*.java; do
        if [[ -f "$file" ]]; then
            filename=$(basename "$file")
            category=$(categorize_file "$filename")
            
            case "$category" in
                code)
                    echo "  📝 $filename → code-specialists/test"
                    move_file "$file" "$CODE_TEST_DIR" "code"
                    ;;
                architecture)
                    echo "  🏛️  $filename → architecture-specialists/test"
                    move_file "$file" "$ARCH_TEST_DIR" "architecture"
                    ;;
                testing)
                    echo "  🧪 $filename → testing-specialists/test"
                    move_file "$file" "$TEST_TEST_DIR" "testing"
                    ;;
            esac
        fi
    done
fi

###############################################################################
# Update package declarations
###############################################################################
echo ""
echo "📝 Updating package declarations..."
echo ""

if [[ "$DRY_RUN" == false ]]; then
    # Update code specialists
    find "$CODE_DIR" -name "*.java" -type f -exec sed -i '' 's/package com\.ghatana\.yappc\.agent\.specialists/package com.ghatana.yappc.agents.code/g' {} \;
    find "$CODE_TEST_DIR" -name "*.java" -type f -exec sed -i '' 's/package com\.ghatana\.yappc\.agent\.specialists/package com.ghatana.yappc.agents.code/g' {} \; 2>/dev/null || true
    
    # Update architecture specialists
    find "$ARCH_DIR" -name "*.java" -type f -exec sed -i '' 's/package com\.ghatana\.yappc\.agent\.specialists/package com.ghatana.yappc.agents.architecture/g' {} \;
    find "$ARCH_TEST_DIR" -name "*.java" -type f -exec sed -i '' 's/package com\.ghatana\.yappc\.agent\.specialists/package com.ghatana.yappc.agents.architecture/g' {} \; 2>/dev/null || true
    
    # Update testing specialists
    find "$TEST_DIR" -name "*.java" -type f -exec sed -i '' 's/package com\.ghatana\.yappc\.agent\.specialists/package com.ghatana.yappc.agents.testing/g' {} \;
    find "$TEST_TEST_DIR" -name "*.java" -type f -exec sed -i '' 's/package com\.ghatana\.yappc\.agent\.specialists/package com.ghatana.yappc.agents.testing/g' {} \; 2>/dev/null || true
    
    echo "  ✅ Updated package declarations"
fi

###############################################################################
# Update imports across codebase
###############################################################################
echo ""
echo "📝 Updating import statements across codebase..."
echo ""

if [[ "$DRY_RUN" == false ]]; then
    # Update imports in all Java files
    find "$YAPPC_ROOT/core" -name "*.java" -type f -exec sed -i '' 's/import com\.ghatana\.yappc\.agent\.specialists\./import com.ghatana.yappc.agents.code./g' {} \; 2>/dev/null || true
    
    echo "  ✅ Updated import statements"
fi

###############################################################################
# Summary
###############################################################################
echo ""
echo "✅ Agent Files Migration Complete!"
echo ""
echo "📊 Summary:"
echo "  Code Specialists: $CODE_COUNT files"
echo "  Architecture Specialists: $ARCH_COUNT files"
echo "  Testing Specialists: $TEST_COUNT files"
echo "  Total: $((CODE_COUNT + ARCH_COUNT + TEST_COUNT)) files migrated"
echo ""
echo "📋 Next Steps:"
echo "  1. Review categorization (some files may need manual adjustment)"
echo "  2. Run: ./gradlew clean build"
echo "  3. Run: ./gradlew test"
echo "  4. Update CORE_ARCHITECTURE.md"
echo "  5. Remove old agents/specialists directory"
echo ""

if [[ "$DRY_RUN" == true ]]; then
    echo "⚠️  This was a DRY RUN - no files were moved"
fi
