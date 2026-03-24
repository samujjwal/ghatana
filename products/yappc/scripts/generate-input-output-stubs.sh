#!/bin/bash

###############################################################################
# Generate Input/Output Stub Classes
#
# Creates stub Input/Output record classes for agents that reference them
###############################################################################

set -e

YAPPC_ROOT="/Users/samujjwal/Development/ghatana/products/yappc"

echo "🔧 Generating Input/Output stub classes..."
echo ""

# Function to create Input/Output stubs for an agent
create_stubs() {
    local agent_file="$1"
    local package_name="$2"
    local dir=$(dirname "$agent_file")
    
    # Extract agent name from file
    local agent_name=$(basename "$agent_file" .java)
    
    # Check if agent references Input/Output classes
    if grep -q "${agent_name}Input\|${agent_name}Output" "$agent_file"; then
        echo "📝 Creating stubs for $agent_name..."
        
        # Create Input stub if referenced
        if grep -q "${agent_name}Input" "$agent_file"; then
            cat > "$dir/${agent_name}Input.java" << EOF
package $package_name;

/**
 * Input for ${agent_name}.
 * Auto-generated stub - replace with actual implementation.
 */
public record ${agent_name}Input(
    String codeContext,
    String requestId
) {}
EOF
        fi
        
        # Create Output stub if referenced
        if grep -q "${agent_name}Output" "$agent_file"; then
            cat > "$dir/${agent_name}Output.java" << EOF
package $package_name;

import java.util.List;
import java.util.Map;

/**
 * Output for ${agent_name}.
 * Auto-generated stub - replace with actual implementation.
 */
public record ${agent_name}Output(
    String responseId,
    String result,
    List<String> recommendations,
    Map<String, Object> metadata
) {}
EOF
        fi
    fi
}

# Process code-specialists
echo "Processing code-specialists..."
find "$YAPPC_ROOT/core/agents/code-specialists/src/main/java/com/ghatana/yappc/agents/code" -name "*Agent.java" -type f | while read file; do
    create_stubs "$file" "com.ghatana.yappc.agents.code"
done

# Process architecture-specialists
echo "Processing architecture-specialists..."
find "$YAPPC_ROOT/core/agents/architecture-specialists/src/main/java/com/ghatana/yappc/agents/architecture" -name "*Agent.java" -type f | while read file; do
    create_stubs "$file" "com.ghatana.yappc.agents.architecture"
done

# Process testing-specialists
echo "Processing testing-specialists..."
find "$YAPPC_ROOT/core/agents/testing-specialists/src/main/java/com/ghatana/yappc/agents/testing" -name "*Agent.java" -type f | while read file; do
    create_stubs "$file" "com.ghatana.yappc.agents.testing"
done

echo ""
echo "✅ Input/Output stubs generated!"
echo ""
echo "📊 Summary:"
echo "  Code specialists: $(find "$YAPPC_ROOT/core/agents/code-specialists" -name "*Input.java" -o -name "*Output.java" | wc -l | tr -d ' ') stubs"
echo "  Architecture specialists: $(find "$YAPPC_ROOT/core/agents/architecture-specialists" -name "*Input.java" -o -name "*Output.java" | wc -l | tr -d ' ') stubs"
echo "  Testing specialists: $(find "$YAPPC_ROOT/core/agents/testing-specialists" -name "*Input.java" -o -name "*Output.java" | wc -l | tr -d ' ') stubs"
