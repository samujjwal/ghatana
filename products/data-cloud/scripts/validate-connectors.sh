#!/bin/bash
# DC-P2-02: Connector validation gate for production readiness
# Validates that all connectors are properly configured and documented

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "[DC-P2-02] Running connector validation..."

# Check connector configuration files
CONNECTOR_CONFIG_DIR="$PROJECT_ROOT/planes/data/entity/src/main/resources/connectors"
if [ -d "$CONNECTOR_CONFIG_DIR" ]; then
    echo "[DC-P2-02] Found connector configuration directory"
    
    # Validate each connector config
    for config_file in "$CONNECTOR_CONFIG_DIR"/*.yaml; do
        if [ -f "$config_file" ]; then
            echo "[DC-P2-02] Validating connector config: $(basename "$config_file")"
            
            # Check for required fields
            if ! grep -q "type:" "$config_file"; then
                echo "[ERROR] Connector config missing 'type' field: $config_file"
                exit 1
            fi
            
            if ! grep -q "name:" "$config_file"; then
                echo "[ERROR] Connector config missing 'name' field: $config_file"
                exit 1
            fi
            
            # Check for hardcoded credentials
            if grep -qi "password\|secret\|api[_-]?key" "$config_file" | grep -v "placeholder\|example\|template" | grep -q .; then
                echo "[ERROR] Connector config may contain hardcoded credentials: $config_file"
                exit 1
            fi
        fi
    done
else
    echo "[WARNING] No connector configuration directory found at $CONNECTOR_CONFIG_DIR"
fi

# Check connector documentation
CONNECTOR_DOCS="$PROJECT_ROOT/docs/connectors"
if [ -d "$CONNECTOR_DOCS" ]; then
    echo "[DC-P2-02] Found connector documentation directory"
    
    # Validate each connector doc
    for doc_file in "$CONNECTOR_DOCS"/*.md; do
        if [ -f "$doc_file" ]; then
            echo "[DC-P2-02] Validating connector doc: $(basename "$doc_file")"
            
            # Check for required sections
            required_sections=("Configuration" "Authentication" "Supported Operations")
            for section in "${required_sections[@]}"; do
                if ! grep -qi "$section" "$doc_file"; then
                    echo "[WARNING] Connector doc missing section '$section': $doc_file"
                fi
            done
        fi
    done
else
    echo "[WARNING] No connector documentation found at $CONNECTOR_DOCS"
fi

# Check connector tests
CONNECTOR_TESTS="$PROJECT_ROOT/planes/data/entity/src/test/java"
if [ -d "$CONNECTOR_TESTS" ]; then
    echo "[DC-P2-02] Checking for connector tests..."
    
    if find "$CONNECTOR_TESTS" -name "*Connector*Test.java" | grep -q .; then
        echo "[DC-P2-02] ✓ Connector tests found"
    else
        echo "[WARNING] No connector tests found"
    fi
fi

echo "[DC-P2-02] ✓ Connector validation passed"
