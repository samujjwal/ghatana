#!/bin/bash

###############################################################################
# Fix Package Declarations in Migrated Agent Files
#
# Updates package declarations from old monolithic structure to new modules
###############################################################################

set -e

YAPPC_ROOT="/Users/samujjwal/Development/ghatana/products/yappc"

echo "🔧 Fixing package declarations in migrated agent files..."
echo ""

# Fix code-specialists
echo "📝 Fixing code-specialists package declarations..."
find "$YAPPC_ROOT/core/agents/code-specialists/src/main/java/com/ghatana/yappc/agents/code" -name "*.java" -type f | while read file; do
    sed -i.bak 's/^package com\.ghatana\.yappc\.agent\.specialists;$/package com.ghatana.yappc.agents.code;/' "$file"
    rm -f "${file}.bak"
done

# Fix architecture-specialists
echo "📝 Fixing architecture-specialists package declarations..."
find "$YAPPC_ROOT/core/agents/architecture-specialists/src/main/java/com/ghatana/yappc/agents/architecture" -name "*.java" -type f | while read file; do
    sed -i.bak 's/^package com\.ghatana\.yappc\.agent\.specialists;$/package com.ghatana.yappc.agents.architecture;/' "$file"
    rm -f "${file}.bak"
done

# Fix testing-specialists
echo "📝 Fixing testing-specialists package declarations..."
find "$YAPPC_ROOT/core/agents/testing-specialists/src/main/java/com/ghatana/yappc/agents/testing" -name "*.java" -type f | while read file; do
    sed -i.bak 's/^package com\.ghatana\.yappc\.agent\.specialists;$/package com.ghatana.yappc.agents.testing;/' "$file"
    rm -f "${file}.bak"
done

echo ""
echo "✅ Package declarations fixed!"
echo ""
echo "📊 Summary:"
echo "  Code specialists: $(find "$YAPPC_ROOT/core/agents/code-specialists" -name "*.java" | wc -l | tr -d ' ') files"
echo "  Architecture specialists: $(find "$YAPPC_ROOT/core/agents/architecture-specialists" -name "*.java" | wc -l | tr -d ' ') files"
echo "  Testing specialists: $(find "$YAPPC_ROOT/core/agents/testing-specialists" -name "*.java" | wc -l | tr -d ' ') files"
