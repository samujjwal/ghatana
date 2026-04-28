#!/bin/bash
# CI gate script to validate OpenAPI ↔ route table parity
# This script ensures all Java HTTP routes are documented in OpenAPI spec

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAPPC_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OPENAPI_FILE="$YAPPC_DIR/api/yappc-api.openapi.yaml"
ROUTE_FILES=(
  "$YAPPC_DIR/core/yappc-api/src/main/java/com/ghatana/yappc/api/http/WorkflowRoutes.java"
  "$YAPPC_DIR/core/yappc-api/src/main/java/com/ghatana/yappc/api/http/AgentRoutes.java"
  "$YAPPC_DIR/core/yappc-api/src/main/java/com/ghatana/yappc/api/http/VectorRoutes.java"
  "$YAPPC_DIR/core/yappc-domain-impl/src/main/java/com/ghatana/yappc/domain/agent/http/AgentRoutes.java"
  "$YAPPC_DIR/core/yappc-domain-impl/src/main/java/com/ghatana/yappc/domain/vector/http/VectorRoutes.java"
  "$YAPPC_DIR/core/yappc-domain-impl/src/main/java/com/ghatana/yappc/domain/workflow/http/WorkflowRoutes.java"
)

echo "=== OpenAPI ↔ Route Table Parity Validation ==="

# Extract all routes from OpenAPI spec
extract_openapi_routes() {
  if [ ! -f "$OPENAPI_FILE" ]; then
    echo "ERROR: OpenAPI file not found: $OPENAPI_FILE"
    exit 1
  fi

  # Extract all path + method combinations from OpenAPI
  grep -E "^\s+/(get|post|put|delete|patch):" "$OPENAPI_FILE" | \
    sed 's/^\s\+//' | \
    sed 's/:$//' | \
    while read -r method; do
      # Get the parent path
      path=$(grep -B10 "$method:" "$OPENAPI_FILE" | grep -E "^\s+/" | tail -1 | sed 's/^\s\+//' | sed 's/:$//')
      if [ -n "$path" ]; then
        echo "${method^^}:${path}"
      fi
    done
}

# Extract all routes from Java Route files
extract_java_routes() {
  local routes=()
  
  for route_file in "${ROUTE_FILES[@]}"; do
    if [ -f "$route_file" ]; then
      # Extract servlet.addAsyncRoute calls
      grep -E "addAsyncRoute\(HttpMethod\." "$route_file" | \
      sed -E 's/.*addAsyncRoute\(HttpMethod\.([A-Z]+),\s*"([^"]+)".*/\1:\2/' | \
      while read -r route; do
        # Replace :id, :stepId, etc. with {id}, {stepId} for OpenAPI format
        route=$(echo "$route" | sed 's/:\([a-zA-Z_][a-zA-Z0-9_]*\)/{\1}/g')
        echo "$route"
      done
    fi
  done
}

# Compare routes
validate_parity() {
  echo "Extracting OpenAPI routes..."
  openapi_routes=$(extract_openapi_routes | sort -u)
  openapi_count=$(echo "$openapi_routes" | wc -l)
  echo "Found $openapi_count routes in OpenAPI spec"

  echo "Extracting Java route table routes..."
  java_routes=$(extract_java_routes | sort -u)
  java_count=$(echo "$java_routes" | wc -l)
  echo "Found $java_count routes in Java files"

  # Find routes in Java but not in OpenAPI
  missing_in_openapi=$(comm -13 <(echo "$openapi_routes") <(echo "$java_routes"))
  if [ -n "$missing_in_openapi" ]; then
    echo "ERROR: Routes in Java but missing from OpenAPI:"
    echo "$missing_in_openapi"
    return 1
  fi

  # Find routes in OpenAPI but not in Java
  extra_in_openapi=$(comm -23 <(echo "$openapi_routes") <(echo "$java_routes"))
  if [ -n "$extra_in_openapi" ]; then
    echo "WARNING: Routes in OpenAPI but not found in Java (may be in other controllers):"
    echo "$extra_in_openapi"
  fi

  echo "✓ OpenAPI ↔ route table parity validated successfully"
  return 0
}

# Main execution
if validate_parity; then
  echo "=== Validation Passed ==="
  exit 0
else
  echo "=== Validation Failed ==="
  exit 1
fi
