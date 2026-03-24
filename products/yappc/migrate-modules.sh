#!/bin/bash
# YAPPC Module Consolidation Migration Script
# This script migrates source code from old module structure to new consolidated modules

set -e  # Exit on error

YAPPC_ROOT="/Users/samujjwal/Development/ghatana/products/yappc"
cd "$YAPPC_ROOT"

echo "=========================================="
echo "YAPPC Module Consolidation Migration"
echo "=========================================="

# Function to safely copy directory contents
safe_copy() {
    local src=$1
    local dest=$2
    if [ -d "$src" ]; then
        echo "  Copying: $src -> $dest"
        mkdir -p "$dest"
        cp -r "$src"/* "$dest/" 2>/dev/null || true
    else
        echo "  Skipping (not found): $src"
    fi
}

# ==========================================
# 1. YAPPC-SHARED (SPI + utilities)
# ==========================================
echo ""
echo "1. Migrating yappc-shared..."
mkdir -p core/yappc-shared/src/main/java
mkdir -p core/yappc-shared/src/test/java

# Copy SPI code
if [ -d "core/spi/src/main/java" ]; then
    safe_copy "core/spi/src/main/java" "core/yappc-shared/src/main/java"
fi
if [ -d "core/spi/src/test/java" ]; then
    safe_copy "core/spi/src/test/java" "core/yappc-shared/src/test/java"
fi

echo "  ✓ yappc-shared migration complete"

# ==========================================
# 2. YAPPC-INFRASTRUCTURE (framework)
# ==========================================
echo ""
echo "2. Migrating yappc-infrastructure..."
mkdir -p core/yappc-infrastructure/src/main/java
mkdir -p core/yappc-infrastructure/src/test/java
mkdir -p core/yappc-infrastructure/src/main/resources

# Copy framework code
if [ -d "core/framework/src/main/java" ]; then
    safe_copy "core/framework/src/main/java" "core/yappc-infrastructure/src/main/java"
fi
if [ -d "core/framework/src/test/java" ]; then
    safe_copy "core/framework/src/test/java" "core/yappc-infrastructure/src/test/java"
fi
if [ -d "core/framework/src/main/resources" ]; then
    safe_copy "core/framework/src/main/resources" "core/yappc-infrastructure/src/main/resources"
fi

echo "  ✓ yappc-infrastructure migration complete"

# ==========================================
# 3. YAPPC-SERVICES (lifecycle + orchestration)
# ==========================================
echo ""
echo "3. Migrating yappc-services..."
mkdir -p core/yappc-services/src/main/java
mkdir -p core/yappc-services/src/test/java

# Copy lifecycle code
if [ -d "core/lifecycle/src/main/java" ]; then
    safe_copy "core/lifecycle/src/main/java" "core/yappc-services/src/main/java"
fi
if [ -d "core/lifecycle/src/test/java" ]; then
    safe_copy "core/lifecycle/src/test/java" "core/yappc-services/src/test/java"
fi

echo "  ✓ yappc-services migration complete"

# ==========================================
# 4. YAPPC-API (if exists)
# ==========================================
echo ""
echo "4. Migrating yappc-api..."
mkdir -p core/yappc-api/src/main/java
mkdir -p core/yappc-api/src/test/java

# Check for API-related code in domain or other modules
if [ -d "core/domain/src/main/java" ]; then
    # Copy HTTP/API related files
    find core/domain/src/main/java -type d -name "http" -o -name "api" -o -name "rest" 2>/dev/null | while read dir; do
        if [ -d "$dir" ]; then
            safe_copy "$dir" "core/yappc-api/src/main/java/$(basename $dir)"
        fi
    done
fi

echo "  ✓ yappc-api migration complete"

# ==========================================
# 5. YAPPC-AGENTS (additional files)
# ==========================================
echo ""
echo "5. Completing yappc-agents migration..."

# Copy remaining agent-related code
if [ -d "core/agents/src/main/java/com/ghatana/yappc/agent" ]; then
    safe_copy "core/agents/src/main/java/com/ghatana/yappc/agent" "core/yappc-agents/src/main/java/com/ghatana/yappc/agent"
fi

# Copy agent generators
if [ -d "core/agents/src/main/java/com/ghatana/yappc/generators" ]; then
    safe_copy "core/agents/src/main/java/com/ghatana/yappc/generators" "core/yappc-agents/src/main/java/com/ghatana/yappc/generators"
fi

# Copy prompts and templates
if [ -d "core/agents/src/main/resources/prompts" ]; then
    safe_copy "core/agents/src/main/resources/prompts" "core/yappc-agents/src/main/resources/prompts"
fi

echo "  ✓ yappc-agents migration complete"

# ==========================================
# Summary
# ==========================================
echo ""
echo "=========================================="
echo "Migration Summary"
echo "=========================================="
echo "✓ yappc-shared: $(find core/yappc-shared/src -name "*.java" 2>/dev/null | wc -l | tr -d ' ') Java files"
echo "✓ yappc-infrastructure: $(find core/yappc-infrastructure/src -name "*.java" 2>/dev/null | wc -l | tr -d ' ') Java files"
echo "✓ yappc-services: $(find core/yappc-services/src -name "*.java" 2>/dev/null | wc -l | tr -d ' ') Java files"
echo "✓ yappc-api: $(find core/yappc-api/src -name "*.java" 2>/dev/null | wc -l | tr -d ' ') Java files"
echo "✓ yappc-agents: $(find core/yappc-agents/src -name "*.java" 2>/dev/null | wc -l | tr -d ' ') Java files"
echo "✓ yappc-domain: $(find core/yappc-domain/src -name "*.java" 2>/dev/null | wc -l | tr -d ' ') Java files"
echo ""
echo "Migration complete! Next steps:"
echo "1. Update settings.gradle.kts"
echo "2. Test builds"
echo "3. Delete old modules"
echo "=========================================="
