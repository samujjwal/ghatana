#!/bin/bash
# YAPPC Frontend Library Consolidation Migration Script
# This script migrates source code from old library structure to new consolidated libraries

set -e  # Exit on error

YAPPC_ROOT="/Users/samujjwal/Development/ghatana/products/yappc"
cd "$YAPPC_ROOT"

echo "=========================================="
echo "YAPPC Frontend Library Migration"
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
# 1. YAPPC-CORE (core + types + utils)
# ==========================================
echo ""
echo "1. Migrating yappc-core..."
mkdir -p frontend/libs/yappc-core/src/{utils,types,constants}

# Copy core library
if [ -d "frontend/libs/core/src" ]; then
    safe_copy "frontend/libs/core/src" "frontend/libs/yappc-core/src"
fi

# Copy types library
if [ -d "frontend/libs/types/src" ]; then
    safe_copy "frontend/libs/types/src" "frontend/libs/yappc-core/src/types"
fi

# Copy utils library
if [ -d "frontend/libs/utils/src" ]; then
    safe_copy "frontend/libs/utils/src" "frontend/libs/yappc-core/src/utils"
fi

# Create index files
cat > frontend/libs/yappc-core/src/index.ts << 'EOF'
// Core utilities and types
export * from './utils';
export * from './types';
export * from './constants';
EOF

cat > frontend/libs/yappc-core/src/utils/index.ts << 'EOF'
// Re-export all utilities
export * from './common';
EOF

cat > frontend/libs/yappc-core/src/types/index.ts << 'EOF'
// Re-export all types
export * from './common';
EOF

cat > frontend/libs/yappc-core/src/constants/index.ts << 'EOF'
// Re-export all constants
export const DEFAULT_TIMEOUT = 30000;
export const DEFAULT_PAGE_SIZE = 20;
EOF

echo "  ✓ yappc-core migration complete"

# ==========================================
# 2. YAPPC-UI (ui + base-ui)
# ==========================================
echo ""
echo "2. Migrating yappc-ui..."
mkdir -p frontend/libs/yappc-ui/src/{components,base,hooks,styles}

# Copy ui library
if [ -d "frontend/libs/ui/src" ]; then
    safe_copy "frontend/libs/ui/src" "frontend/libs/yappc-ui/src/components"
fi

# Copy base-ui library
if [ -d "frontend/libs/base-ui/src" ]; then
    safe_copy "frontend/libs/base-ui/src" "frontend/libs/yappc-ui/src/base"
fi

# Create index file
cat > frontend/libs/yappc-ui/src/index.ts << 'EOF'
// UI Components
export * from './components';
export * from './base';
export * from './hooks';
EOF

echo "  ✓ yappc-ui migration complete"

# ==========================================
# 3. YAPPC-AI (ai + chat)
# ==========================================
echo ""
echo "3. Migrating yappc-ai..."
mkdir -p frontend/libs/yappc-ai/src/{components,hooks,utils,chat}

# Copy ai library
if [ -d "frontend/libs/ai/src" ]; then
    safe_copy "frontend/libs/ai/src" "frontend/libs/yappc-ai/src"
fi

# Copy chat library
if [ -d "frontend/libs/chat/src" ]; then
    safe_copy "frontend/libs/chat/src" "frontend/libs/yappc-ai/src/chat"
fi

# Create index file
cat > frontend/libs/yappc-ai/src/index.ts << 'EOF'
// AI Components and utilities
export * from './components';
export * from './hooks';
export * from './utils';
export * from './chat';
EOF

echo "  ✓ yappc-ai migration complete"

# ==========================================
# 4. YAPPC-CANVAS (standalone - largest)
# ==========================================
echo ""
echo "4. Migrating yappc-canvas..."
mkdir -p frontend/libs/yappc-canvas/src

# Copy canvas library
if [ -d "frontend/libs/canvas/src" ]; then
    safe_copy "frontend/libs/canvas/src" "frontend/libs/yappc-canvas/src"
fi

# Create index file
cat > frontend/libs/yappc-canvas/src/index.ts << 'EOF'
// Canvas components
export * from './components';
EOF

echo "  ✓ yappc-canvas migration complete"

# ==========================================
# 5. YAPPC-STATE (state + config + config-hooks)
# ==========================================
echo ""
echo "5. Migrating yappc-state..."
mkdir -p frontend/libs/yappc-state/src/{store,config,hooks}

# Copy state library
if [ -d "frontend/libs/state/src" ]; then
    safe_copy "frontend/libs/state/src" "frontend/libs/yappc-state/src/store"
fi

# Copy config library
if [ -d "frontend/libs/config/src" ]; then
    safe_copy "frontend/libs/config/src" "frontend/libs/yappc-state/src/config"
fi

# Copy config-hooks library
if [ -d "frontend/libs/config-hooks/src" ]; then
    safe_copy "frontend/libs/config-hooks/src" "frontend/libs/yappc-state/src/hooks"
fi

# Create package.json
cat > frontend/libs/yappc-state/package.json << 'EOF'
{
  "name": "@yappc/state",
  "version": "1.0.0",
  "main": "./dist/index.js",
  "module": "./dist/index.mjs",
  "types": "./dist/index.d.ts"
}
EOF

echo "  ✓ yappc-state migration complete"

# ==========================================
# Summary
# ==========================================
echo ""
echo "=========================================="
echo "Frontend Migration Summary"
echo "=========================================="
echo "✓ yappc-core: $(find frontend/libs/yappc-core/src -name "*.ts" -o -name "*.tsx" 2>/dev/null | wc -l | tr -d ' ') TypeScript files"
echo "✓ yappc-ui: $(find frontend/libs/yappc-ui/src -name "*.ts" -o -name "*.tsx" 2>/dev/null | wc -l | tr -d ' ') TypeScript files"
echo "✓ yappc-ai: $(find frontend/libs/yappc-ai/src -name "*.ts" -o -name "*.tsx" 2>/dev/null | wc -l | tr -d ' ') TypeScript files"
echo "✓ yappc-canvas: $(find frontend/libs/yappc-canvas/src -name "*.ts" -o -name "*.tsx" 2>/dev/null | wc -l | tr -d ' ') TypeScript files"
echo "✓ yappc-state: $(find frontend/libs/yappc-state/src -name "*.ts" -o -name "*.tsx" 2>/dev/null | wc -l | tr -d ' ') TypeScript files"
echo ""
echo "Migration complete! Next steps:"
echo "1. Update pnpm-workspace.yaml"
echo "2. Update import statements"
echo "3. Test builds"
echo "4. Delete old libraries"
echo "=========================================="
