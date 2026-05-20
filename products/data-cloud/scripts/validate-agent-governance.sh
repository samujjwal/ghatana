#!/bin/bash
# DC-P2-02: Agent governance validation gate for production readiness
# Validates that agent catalog entries are properly configured and governed

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "[DC-P2-02] Running agent governance validation..."

# Check agent catalog YAML files
AGENT_CATALOG_DIR="$PROJECT_ROOT/planes/action/registry/src/main/resources/catalog"
if [ -d "$AGENT_CATALOG_DIR" ]; then
    echo "[DC-P2-02] Found agent catalog directory"
    
    # Validate each agent catalog entry
    for catalog_file in "$AGENT_CATALOG_DIR"/*.yaml; do
        if [ -f "$catalog_file" ]; then
            echo "[DC-P2-02] Validating agent catalog entry: $(basename "$catalog_file")"
            
            # Check for required fields
            required_fields=("agentType" "autonomyLevel" "determinismGuarantee" "stateMutability")
            for field in "${required_fields[@]}"; do
                if ! grep -q "$field:" "$catalog_file"; then
                    echo "[ERROR] Agent catalog entry missing required field '$field': $catalog_file"
                    exit 1
                fi
            done
            
            # Validate enum values against platform canonical values
            # AgentType must be one of: LLM, TOOL, HYBRID, WORKFLOW
            agent_type=$(grep "agentType:" "$catalog_file" | awk '{print $2}')
            if [[ ! "$agent_type" =~ ^(LLM|TOOL|HYBRID|WORKFLOW)$ ]]; then
                echo "[ERROR] Invalid agentType '$agent_type' in: $catalog_file"
                echo "Must be one of: LLM, TOOL, HYBRID, WORKFLOW"
                exit 1
            fi
            
            # AutonomyLevel must be one of: NONE, LOW, MEDIUM, HIGH, FULL
            autonomy_level=$(grep "autonomyLevel:" "$catalog_file" | awk '{print $2}')
            if [[ ! "$autonomy_level" =~ ^(NONE|LOW|MEDIUM|HIGH|FULL)$ ]]; then
                echo "[ERROR] Invalid autonomyLevel '$autonomy_level' in: $catalog_file"
                echo "Must be one of: NONE, LOW, MEDIUM, HIGH, FULL"
                exit 1
            fi
            
            # Check for governance metadata
            if ! grep -q "governance:" "$catalog_file"; then
                echo "[WARNING] Agent catalog entry missing 'governance' section: $catalog_file"
            fi
            
            # Check for kill-switch configuration
            if ! grep -q "killSwitch:" "$catalog_file"; then
                echo "[WARNING] Agent catalog entry missing 'killSwitch' configuration: $catalog_file"
            fi
        fi
    done
else
    echo "[WARNING] No agent catalog directory found at $AGENT_CATALOG_DIR"
fi

# Check agent governance documentation
GOVERNANCE_DOCS="$PROJECT_ROOT/docs/agent-governance"
if [ -d "$GOVERNANCE_DOCS" ]; then
    echo "[DC-P2-02] Found agent governance documentation"
    
    # Validate governance documentation has required sections
    required_docs=("approval-process.md" "kill-switch-procedure.md" "risk-assessment.md")
    for doc in "${required_docs[@]}"; do
        if [ ! -f "$GOVERNANCE_DOCS/$doc" ]; then
            echo "[WARNING] Agent governance doc missing: $doc"
        fi
    done
else
    echo "[WARNING] No agent governance documentation found at $GOVERNANCE_DOCS"
fi

# Check agent registry tests
REGISTRY_TESTS="$PROJECT_ROOT/planes/action/registry/src/test/java"
if [ -d "$REGISTRY_TESTS" ]; then
    echo "[DC-P2-02] Checking for agent registry tests..."
    
    if find "$REGISTRY_TESTS" -name "*AgentRegistry*Test.java" -o -name "*Catalog*Test.java" | grep -q .; then
        echo "[DC-P2-02] ✓ Agent registry tests found"
    else
        echo "[WARNING] No agent registry tests found"
    fi
fi

# Validate agent runtime safety checks
RUNTIME_SAFETY="$PROJECT_ROOT/planes/action/agent-runtime/src/main/java"
if [ -d "$RUNTIME_SAFETY" ]; then
    echo "[DC-P2-02] Checking agent runtime safety checks..."
    
    # Check for kill-switch implementation
    if find "$RUNTIME_SAFETY" -name "*KillSwitch*.java" | grep -q .; then
        echo "[DC-P2-02] ✓ Kill-switch implementation found"
    else
        echo "[WARNING] No kill-switch implementation found"
    fi
    
    # Check for resource isolation
    if find "$RUNTIME_SAFETY" -name "*Sandbox*.java" -o -name "*Isolation*.java" | grep -q .; then
        echo "[DC-P2-02] ✓ Resource isolation implementation found"
    else
        echo "[WARNING] No resource isolation implementation found"
    fi
fi

echo "[DC-P2-02] ✓ Agent governance validation passed"
