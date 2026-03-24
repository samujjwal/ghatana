#!/bin/bash

###############################################################################
# Fix Agent Import Statements
#
# Updates import statements in migrated agent files to use the new package
# structure instead of the old monolithic package.
###############################################################################

set -e

YAPPC_ROOT="/Users/samujjwal/Development/ghatana/products/yappc"

echo "🔧 Fixing import statements in migrated agent files..."
echo ""

# Update imports in code-specialists
echo "📝 Updating code-specialists imports..."
find "$YAPPC_ROOT/core/agents/code-specialists" -name "*.java" -type f -exec sed -i '' \
    -e 's/import com\.ghatana\.yappc\.agent\.specialists\./import com.ghatana.yappc.agents.code./g' \
    -e 's/import com\.ghatana\.yappc\.agents\.architecture\./import com.ghatana.yappc.agents.architecture./g' \
    -e 's/import com\.ghatana\.yappc\.agents\.testing\./import com.ghatana.yappc.agents.testing./g' \
    {} \;

# Update imports in architecture-specialists
echo "🏛️  Updating architecture-specialists imports..."
find "$YAPPC_ROOT/core/agents/architecture-specialists" -name "*.java" -type f -exec sed -i '' \
    -e 's/import com\.ghatana\.yappc\.agent\.specialists\./import com.ghatana.yappc.agents.architecture./g' \
    -e 's/import com\.ghatana\.yappc\.agents\.code\./import com.ghatana.yappc.agents.code./g' \
    -e 's/import com\.ghatana\.yappc\.agents\.testing\./import com.ghatana.yappc.agents.testing./g' \
    {} \;

# Update imports in testing-specialists
echo "🧪 Updating testing-specialists imports..."
find "$YAPPC_ROOT/core/agents/testing-specialists" -name "*.java" -type f -exec sed -i '' \
    -e 's/import com\.ghatana\.yappc\.agent\.specialists\./import com.ghatana.yappc.agents.testing./g' \
    -e 's/import com\.ghatana\.yappc\.agents\.code\./import com.ghatana.yappc.agents.code./g' \
    -e 's/import com\.ghatana\.yappc\.agents\.architecture\./import com.ghatana.yappc.agents.architecture./g' \
    {} \;

# Update imports in other modules that reference agents
echo "🔄 Updating imports in other YAPPC modules..."
find "$YAPPC_ROOT/core/agents/runtime" -name "*.java" -type f -exec sed -i '' \
    's/import com\.ghatana\.yappc\.agent\.specialists\./import com.ghatana.yappc.agents.code./g' \
    {} \; 2>/dev/null || true

find "$YAPPC_ROOT/core/agents/workflow" -name "*.java" -type f -exec sed -i '' \
    's/import com\.ghatana\.yappc\.agent\.specialists\./import com.ghatana.yappc.agents.code./g' \
    {} \; 2>/dev/null || true

echo ""
echo "✅ Import statements updated successfully!"
echo ""
echo "📋 Next: Run build verification"
