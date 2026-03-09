#!/bin/bash

# YAPPC Refactorer Module Migration Script
# Migrates code from 19 legacy modules to 6 consolidated modules
#
# Usage: ./scripts/migrate-refactorer.sh [--dry-run]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAPPC_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

DRY_RUN=false
if [[ "$1" == "--dry-run" ]]; then
    DRY_RUN=true
    echo "🔍 DRY RUN MODE - No files will be modified"
fi

echo "=================================================="
echo "YAPPC Refactorer Module Migration"
echo "=================================================="
echo "Root: $YAPPC_ROOT"
echo ""

# Define source and target mappings
MODULE_MAPPINGS=(
    # refactorer-core (Core Infrastructure)
    "core/refactorer/modules/shared:core/refactorer-consolidated/refactorer-core/src/main/java"
    "core/refactorer/modules/diagnostics:core/refactorer-consolidated/refactorer-core/src/main/java"
    "core/refactorer/modules/indexer:core/refactorer-consolidated/refactorer-core/src/main/java"
    "core/refactorer/modules/consistency:core/refactorer-consolidated/refactorer-core/src/main/java"
    
    # refactorer-engine (Transformation Engine)
    "core/refactorer/modules/refactoring:core/refactorer-consolidated/refactorer-engine/src/main/java"
    "core/refactorer/modules/rewriters:core/refactorer-consolidated/refactorer-engine/src/main/java"
    "core/refactorer/modules/codemods:core/refactorer-consolidated/refactorer-engine/src/main/java"
    
    # refactorer-languages (Language Support)
    "core/refactorer/modules/languages:core/refactorer-consolidated/refactorer-languages/src/main/java"
    "core/refactorer/modules/languages-tsjs:core/refactorer-consolidated/refactorer-languages/src/main/java"
    
    # refactorer-api (Service Interfaces)
    "core/refactorer/modules/cli:core/refactorer-consolidated/refactorer-api/src/main/java"
    "core/refactorer/modules/service-grpc-api:core/refactorer-consolidated/refactorer-api/src/main/java"
    "core/refactorer/modules/service-server:core/refactorer-consolidated/refactorer-api/src/main/java"
    
    # refactorer-adapters (External Integrations)
    "core/refactorer/modules/adapters/a2a:core/refactorer-consolidated/refactorer-adapters/src/main/java"
    "core/refactorer/modules/adapters/mcp:core/refactorer-consolidated/refactorer-adapters/src/main/java"
    
    # refactorer-infra (Infrastructure)
    "core/refactorer/modules/storage-engine:core/refactorer-consolidated/refactorer-infra/src/main/java"
    "core/refactorer/modules/eventhistory:core/refactorer-consolidated/refactorer-infra/src/main/java"
    "core/refactorer/modules/debug:core/refactorer-consolidated/refactorer-infra/src/main/java"
    "core/refactorer/modules/performance-tests:core/refactorer-consolidated/refactorer-infra/src/main/java"
    "core/refactorer/modules/orchestrator:core/refactorer-consolidated/refactorer-infra/src/main/java"
)

# Function to migrate a module
migrate_module() {
    local source_dir="$1"
    local target_dir="$2"
    
    if [[ ! -d "$YAPPC_ROOT/$source_dir" ]]; then
        echo "⚠️  Source directory not found: $source_dir"
        return
    fi
    
    echo "📦 Migrating: $source_dir"
    echo "   → Target: $target_dir"
    
    if [[ "$DRY_RUN" == true ]]; then
        echo "   [DRY RUN] Would copy files"
        return
    fi
    
    # Create target directory
    mkdir -p "$YAPPC_ROOT/$target_dir"
    
    # Copy Java source files
    if [[ -d "$YAPPC_ROOT/$source_dir/src/main/java" ]]; then
        echo "   Copying Java sources..."
        cp -r "$YAPPC_ROOT/$source_dir/src/main/java/"* "$YAPPC_ROOT/$target_dir/" 2>/dev/null || true
    fi
    
    # Copy test files
    local test_target="${target_dir/src\/main\/java/src/test/java}"
    mkdir -p "$YAPPC_ROOT/$test_target"
    if [[ -d "$YAPPC_ROOT/$source_dir/src/test/java" ]]; then
        echo "   Copying test sources..."
        cp -r "$YAPPC_ROOT/$source_dir/src/test/java/"* "$YAPPC_ROOT/$test_target/" 2>/dev/null || true
    fi
    
    # Copy resources
    local resource_target="${target_dir/src\/main\/java/src/main/resources}"
    mkdir -p "$YAPPC_ROOT/$resource_target"
    if [[ -d "$YAPPC_ROOT/$source_dir/src/main/resources" ]]; then
        echo "   Copying resources..."
        cp -r "$YAPPC_ROOT/$source_dir/src/main/resources/"* "$YAPPC_ROOT/$resource_target/" 2>/dev/null || true
    fi
    
    echo "   ✅ Migration complete"
}

# Perform migration
echo ""
echo "Starting migration..."
echo ""

for mapping in "${MODULE_MAPPINGS[@]}"; do
    IFS=':' read -r source_dir target_dir <<< "$mapping"
    migrate_module "$source_dir" "$target_dir"
    echo ""
done

if [[ "$DRY_RUN" == false ]]; then
    echo "=================================================="
    echo "✅ Migration Complete!"
    echo "=================================================="
    echo ""
    echo "Next steps:"
    echo "1. Update package declarations in migrated files"
    echo "2. Fix import statements"
    echo "3. Run: ./gradlew :core:refactorer-consolidated:refactorer-core:build"
    echo "4. Run: ./gradlew :core:refactorer-consolidated:refactorer-engine:build"
    echo "5. Run: ./gradlew :core:refactorer-consolidated:refactorer-languages:build"
    echo "6. Run: ./gradlew :core:refactorer-consolidated:refactorer-api:build"
    echo "7. Run: ./gradlew :core:refactorer-consolidated:refactorer-adapters:build"
    echo "8. Run: ./gradlew :core:refactorer-consolidated:refactorer-infra:build"
    echo ""
    echo "After successful build:"
    echo "1. Update dependent modules to use new consolidated modules"
    echo "2. Remove legacy modules from settings.gradle"
    echo "3. Archive legacy modules: mv core/refactorer core/refactorer-legacy"
else
    echo "=================================================="
    echo "🔍 Dry Run Complete - No Changes Made"
    echo "=================================================="
    echo ""
    echo "Run without --dry-run to perform actual migration"
fi
