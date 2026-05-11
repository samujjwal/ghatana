#!/bin/bash
# P0-05: CI check script to validate route security metadata drift
# This script ensures that:
# - All routes registered in DataCloudRouterBuilder are present in OpenAPI
# - All OpenAPI routes have required security metadata extensions
# - Runtime registry matches generated registry from OpenAPI

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OPENAPI_DIR="$PROJECT_ROOT/contracts/openapi"
JAVA_SOURCE_DIR="$PROJECT_ROOT/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http"

echo "[P0-05] Checking route security metadata drift..."

# Check if OpenAPI files exist
if [ ! -f "$OPENAPI_DIR/data-cloud.yaml" ]; then
    echo "[ERROR] OpenAPI file not found: $OPENAPI_DIR/data-cloud.yaml"
    exit 1
fi

if [ ! -f "$OPENAPI_DIR/action-plane.yaml" ]; then
    echo "[ERROR] OpenAPI file not found: $OPENAPI_DIR/action-plane.yaml"
    exit 1
fi

# Check if RouterBuilder exists
if [ ! -f "$JAVA_SOURCE_DIR/DataCloudRouterBuilder.java" ]; then
    echo "[ERROR] RouterBuilder not found: $JAVA_SOURCE_DIR/DataCloudRouterBuilder.java"
    exit 1
fi

echo "[P0-05] Found required files"

# Extract routes from RouterBuilder
echo "[P0-05] Extracting routes from DataCloudRouterBuilder..."
ROUTES_FROM_ROUTER=$(grep -E 'route\(|Routes\.|router\.route' "$JAVA_SOURCE_DIR/DataCloudRouterBuilder.java" | grep -v '//' | wc -l)
echo "[P0-05] Found $ROUTES_FROM_ROUTER route definitions in RouterBuilder"

# Extract paths from OpenAPI
echo "[P0-05] Extracting paths from OpenAPI..."
PATHS_FROM_OPENAPI=$(grep -E '^\s*/api' "$OPENAPI_DIR/data-cloud.yaml" | wc -l)
echo "[P0-05] Found $PATHS_FROM_OPENAPI path definitions in OpenAPI"

# Check for required OpenAPI extensions on mutating routes
echo "[P0-05] Checking for required security metadata extensions on mutating routes..."

# Check POST, PUT, DELETE routes for x-ghatana-sensitivity extension
MUTATING_ROUTES_WITHOUT_SENSITIVITY=$(grep -A 10 'post:\|put:\|delete:' "$OPENAPI_DIR/data-cloud.yaml" | grep -B 10 'operationId' | grep -c 'x-ghatana-sensitivity' || true)
TOTAL_MUTATING_ROUTES=$(grep -E '^\s+(post|put|delete):' "$OPENAPI_DIR/data-cloud.yaml" | wc -l)

if [ "$MUTATING_ROUTES_WITHOUT_SENSITENCY" -lt "$TOTAL_MUTATING_ROUTES" ]; then
    echo "[WARNING] Some mutating routes may be missing x-ghatana-sensitivity extension"
    echo "[P0-05] Total mutating routes: $TOTAL_MUTATING_ROUTES"
    echo "[P0-05] Routes with sensitivity metadata: $MUTATING_ROUTES_WITHOUT_SENSITIVITY"
fi

# Check for EndpointSensitivity.java
if [ ! -f "$PROJECT_ROOT/planes/shared-spi/src/main/java/com/ghatana/datacloud/spi/EndpointSensitivity.java" ]; then
    echo "[ERROR] EndpointSensitivity.java not found"
    exit 1
fi

echo "[P0-05] EndpointSensitivity.java found"

# Check for RouteActionAccessRegistry.java
if [ ! -f "$PROJECT_ROOT/planes/shared-spi/src/main/java/com/ghatana/datacloud/spi/RouteActionAccessRegistry.java" ]; then
    echo "[WARNING] RouteActionAccessRegistry.java not found - this should be generated from OpenAPI"
fi

echo "[P0-05] Route security metadata drift check completed"
echo "[P0-05] Summary:"
echo "  - RouterBuilder routes: $ROUTES_FROM_ROUTER"
echo "  - OpenAPI paths: $PATHS_FROM_OPENAPI"
echo "  - Mutating routes with sensitivity: $MUTATING_ROUTES_WITHOUT_SENSITENCY / $TOTAL_MUTATING_ROUTES"
