#!/bin/bash

###############################################################################
# YAPPC Scaffold Files Migration Script
#
# Migrates scaffold files from the monolithic scaffold/core module to
# focused modules:
# - engine: Core scaffolding orchestration logic
# - generators: Language-specific code generators
# - templates: Template loading, parsing, rendering
#
# Usage: ./scripts/migrate-scaffold-files.sh [--dry-run]
###############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAPPC_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

DRY_RUN=false
if [[ "$1" == "--dry-run" ]]; then
    DRY_RUN=true
    echo "🔍 Running in DRY RUN mode - no files will be moved"
fi

echo "🚀 YAPPC Scaffold Files Migration"
echo "================================="
echo ""

SOURCE_DIR="$YAPPC_ROOT/core/scaffold/core/src/main/java/com/ghatana/yappc/scaffold"
SOURCE_TEST_DIR="$YAPPC_ROOT/core/scaffold/core/src/test/java/com/ghatana/yappc/scaffold"

ENGINE_DIR="$YAPPC_ROOT/core/scaffold/engine/src/main/java/com/ghatana/yappc/scaffold/engine"
GENERATORS_DIR="$YAPPC_ROOT/core/scaffold/generators/src/main/java/com/ghatana/yappc/scaffold/generators"
TEMPLATES_DIR="$YAPPC_ROOT/core/scaffold/templates/src/main/java/com/ghatana/yappc/scaffold/templates"

ENGINE_TEST_DIR="$YAPPC_ROOT/core/scaffold/engine/src/test/java/com/ghatana/yappc/scaffold/engine"
GENERATORS_TEST_DIR="$YAPPC_ROOT/core/scaffold/generators/src/test/java/com/ghatana/yappc/scaffold/generators"
TEMPLATES_TEST_DIR="$YAPPC_ROOT/core/scaffold/templates/src/test/java/com/ghatana/yappc/scaffold/templates"

# Counters
ENGINE_COUNT=0
GENERATORS_COUNT=0
TEMPLATES_COUNT=0

###############################################################################
# Categorization patterns
###############################################################################

# Engine: Core orchestration, coordination, workflow
ENGINE_PATTERNS=(
    "*Engine*" "*Orchestrat*" "*Coordinat*" "*Workflow*" "*Pipeline*"
    "*Manager*" "*Service*" "*Controller*" "*Handler*" "*Processor*"
    "*Executor*" "*Runner*" "*Context*" "*State*" "*Config*"
)

# Generators: Language-specific code generation
GENERATORS_PATTERNS=(
    "*Generator*" "*Builder*" "*Creator*" "*Factory*" "*Emitter*"
    "*Java*" "*TypeScript*" "*Python*" "*React*" "*Spring*"
    "*Code*" "*Source*" "*Class*" "*Method*" "*Function*"
)

# Templates: Template management, loading, parsing, rendering
TEMPLATES_PATTERNS=(
    "*Template*" "*Parser*" "*Render*" "*Loader*" "*Reader*"
    "*Mustache*" "*Handlebars*" "*Velocity*" "*Freemarker*"
    "*Layout*" "*Format*" "*Transform*"
)

###############################################################################
# Function to categorize file
###############################################################################
categorize_file() {
    local filename="$1"
    
    # Check templates patterns first (most specific)
    for pattern in "${TEMPLATES_PATTERNS[@]}"; do
        if [[ "$filename" == $pattern ]]; then
            echo "templates"
            return
        fi
    done
    
    # Check generators patterns
    for pattern in "${GENERATORS_PATTERNS[@]}"; do
        if [[ "$filename" == $pattern ]]; then
            echo "generators"
            return
        fi
    done
    
    # Check engine patterns
    for pattern in "${ENGINE_PATTERNS[@]}"; do
        if [[ "$filename" == $pattern ]]; then
            echo "engine"
            return
        fi
    done
    
    # Default to engine if no match
    echo "engine"
}

###############################################################################
# Function to move file
###############################################################################
move_file() {
    local src="$1"
    local dest_dir="$2"
    local category="$3"
    
    if [[ "$DRY_RUN" == false ]]; then
        mkdir -p "$dest_dir"
        mv "$src" "$dest_dir/"
    fi
    
    case "$category" in
        engine)
            ((ENGINE_COUNT++))
            ;;
        generators)
            ((GENERATORS_COUNT++))
            ;;
        templates)
            ((TEMPLATES_COUNT++))
            ;;
    esac
}

###############################################################################
# Migrate main source files
###############################################################################
echo "📦 Analyzing and migrating scaffold files..."
echo ""

if [[ -d "$SOURCE_DIR" ]]; then
    for file in "$SOURCE_DIR"/*.java; do
        if [[ -f "$file" ]]; then
            filename=$(basename "$file")
            category=$(categorize_file "$filename")
            
            case "$category" in
                engine)
                    echo "  ⚙️  $filename → engine"
                    move_file "$file" "$ENGINE_DIR" "engine"
                    ;;
                generators)
                    echo "  🏭 $filename → generators"
                    move_file "$file" "$GENERATORS_DIR" "generators"
                    ;;
                templates)
                    echo "  📄 $filename → templates"
                    move_file "$file" "$TEMPLATES_DIR" "templates"
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
                engine)
                    echo "  ⚙️  $filename → engine/test"
                    move_file "$file" "$ENGINE_TEST_DIR" "engine"
                    ;;
                generators)
                    echo "  🏭 $filename → generators/test"
                    move_file "$file" "$GENERATORS_TEST_DIR" "generators"
                    ;;
                templates)
                    echo "  📄 $filename → templates/test"
                    move_file "$file" "$TEMPLATES_TEST_DIR" "templates"
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
    # Update engine
    find "$ENGINE_DIR" -name "*.java" -type f -exec sed -i '' 's/package com\.ghatana\.yappc\.scaffold\([^.]\)/package com.ghatana.yappc.scaffold.engine\1/g' {} \; 2>/dev/null || true
    find "$ENGINE_TEST_DIR" -name "*.java" -type f -exec sed -i '' 's/package com\.ghatana\.yappc\.scaffold\([^.]\)/package com.ghatana.yappc.scaffold.engine\1/g' {} \; 2>/dev/null || true
    
    # Update generators
    find "$GENERATORS_DIR" -name "*.java" -type f -exec sed -i '' 's/package com\.ghatana\.yappc\.scaffold\([^.]\)/package com.ghatana.yappc.scaffold.generators\1/g' {} \; 2>/dev/null || true
    find "$GENERATORS_TEST_DIR" -name "*.java" -type f -exec sed -i '' 's/package com\.ghatana\.yappc\.scaffold\([^.]\)/package com.ghatana.yappc.scaffold.generators\1/g' {} \; 2>/dev/null || true
    
    # Update templates
    find "$TEMPLATES_DIR" -name "*.java" -type f -exec sed -i '' 's/package com\.ghatana\.yappc\.scaffold\([^.]\)/package com.ghatana.yappc.scaffold.templates\1/g' {} \; 2>/dev/null || true
    find "$TEMPLATES_TEST_DIR" -name "*.java" -type f -exec sed -i '' 's/package com\.ghatana\.yappc\.scaffold\([^.]\)/package com.ghatana.yappc.scaffold.templates\1/g' {} \; 2>/dev/null || true
    
    echo "  ✅ Updated package declarations"
fi

###############################################################################
# Summary
###############################################################################
echo ""
echo "✅ Scaffold Files Migration Complete!"
echo ""
echo "📊 Summary:"
echo "  Engine: $ENGINE_COUNT files"
echo "  Generators: $GENERATORS_COUNT files"
echo "  Templates: $TEMPLATES_COUNT files"
echo "  Total: $((ENGINE_COUNT + GENERATORS_COUNT + TEMPLATES_COUNT)) files migrated"
echo ""
echo "📋 Next Steps:"
echo "  1. Review categorization (some files may need manual adjustment)"
echo "  2. Run: ./gradlew clean build"
echo "  3. Run: ./gradlew test"
echo "  4. Update CORE_ARCHITECTURE.md"
echo "  5. Remove old scaffold/core directory"
echo ""

if [[ "$DRY_RUN" == true ]]; then
    echo "⚠️  This was a DRY RUN - no files were moved"
fi
