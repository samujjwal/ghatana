#!/bin/bash

###############################################################################
# Fix Missing Imports in Testing-Specialists Tests
#
# Adds missing agent imports from other modules
###############################################################################

set -e

YAPPC_ROOT="/Users/samujjwal/Development/ghatana/products/yappc"

echo "🔧 Fixing missing imports in testing-specialists tests..."
echo ""

# Function to find and add missing agent imports
fix_agent_imports() {
    local test_file="$1"
    local agent_name="$2"
    local package="$3"
    
    echo "📝 Fixing $agent_name import in $(basename "$test_file")"
    
    # Check if import already exists
    if grep -q "import.*${agent_name}" "$test_file"; then
        echo "  ✅ Import already exists"
        return
    fi
    
    # Find the right place to add the import (after existing yappc imports)
    if grep -q "import com.ghatana.yappc.agent" "$test_file"; then
        # Add after yappc.agent imports
        sed -i.bak "/import com.ghatana.yappc.agent/a\\
import com.ghatana.yappc.${package}.${agent_name};" "$test_file"
    else
        # Add after package declaration
        sed -i.bak "/^package /a\\
\\
import com.ghatana.yappc.${package}.${agent_name};" "$test_file"
    fi
    
    rm -f "${test_file}.bak"
    echo "  ✅ Import added"
}

# Find all test files with missing agent classes
echo "🔍 Finding test files with missing imports..."
find "$YAPPC_ROOT/core/agents/testing-specialists/src/test/java" -name "*Test.java" -type f | while read test_file; do
    # Check for compilation errors in this test file
    error_count=$(./gradlew :products:yappc:core:agents:testing-specialists:compileTestJava 2>&1 | grep "$test_file.*error:" | wc -l | tr -d ' ')
    
    if [ "$error_count" -gt 0 ]; then
        echo "📄 Processing $(basename "$test_file") with $error_count errors..."
        
        # Check for specific missing agents and add imports
        if grep -q "AgentDispatcherAgent" "$test_file" && ! grep -q "import.*AgentDispatcherAgent" "$test_file"; then
            fix_agent_imports "$test_file" "AgentDispatcherAgent" "agents.code"
        fi
        
        if grep -q "ReleaseOrchestratorAgent" "$test_file" && ! grep -q "import.*ReleaseOrchestratorAgent" "$test_file"; then
            fix_agent_imports "$test_file" "ReleaseOrchestratorAgent" "agents.code"
        fi
        
        if grep -q "GovernanceOrchestratorAgent" "$test_file" && ! grep -q "import.*GovernanceOrchestratorAgent" "$test_file"; then
            fix_agent_imports "$test_file" "GovernanceOrchestratorAgent" "agents.code"
        fi
        
        if grep -q "MultiCloudOrchestratorAgent" "$test_file" && ! grep -q "import.*MultiCloudOrchestratorAgent" "$test_file"; then
            fix_agent_imports "$test_file" "MultiCloudOrchestratorAgent" "agents.code"
        fi
        
        if grep -q "OperationsOrchestratorAgent" "$test_file" && ! grep -q "import.*OperationsOrchestratorAgent" "$test_file"; then
            fix_agent_imports "$test_file" "OperationsOrchestratorAgent" "agents.architecture"
        fi
    fi
done

echo ""
echo "✅ Import fixes complete!"
echo ""
echo "🧪 Testing the build..."
cd /Users/samujjwal/Development/ghatana && ./gradlew :products:yappc:core:agents:testing-specialists:build --console=plain 2>&1 | tail -10
