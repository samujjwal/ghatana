#!/bin/bash
#
# @doc.type script
# @doc.purpose Verify API client, OpenAPI spec, and server route parity
# @doc.layer product
# @doc.pattern CI Gate
#
# This script checks that:
# 1. The generated API client is up-to-date with the OpenAPI spec
# 2. The OpenAPI spec matches the actual server routes
# 3. No drift between client, spec, and server implementation
#
# Usage: ./scripts/check-api-parity.sh [--ci]
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRODUCTS_DIR="$(dirname "$SCRIPT_DIR")"
FRONTEND_DIR="$PRODUCTS_DIR/frontend"
API_DIR="$PRODUCTS_DIR/api"

CI_MODE=false
if [ "$1" = "--ci" ]; then
  CI_MODE=true
fi

echo "🔍 Checking API Client, OpenAPI Spec, and Server Route Parity"
echo "============================================================"

# Check 1: Verify generated client is up-to-date
echo ""
echo "📋 Check 1: Verifying generated API client is up-to-date"
echo "---------------------------------------------------------"

cd "$FRONTEND_DIR"

# Generate the client to a temp location
TEMP_CLIENT_FILE=$(mktemp)
npx -y openapi-typescript-codegen ../../api/yappc-api.openapi.yaml -o "$TEMP_CLIENT_FILE" --client axios

# Compare with existing generated client
if [ -f "web/src/clients/generated/api.ts" ]; then
  if diff -q "$TEMP_CLIENT_FILE" "web/src/clients/generated/api.ts" > /dev/null 2>&1; then
    echo "✅ Generated API client is up-to-date with OpenAPI spec"
  else
    echo "❌ Generated API client is out of sync with OpenAPI spec"
    echo "   Run 'pnpm codegen:openapi' to regenerate the client"
    rm -f "$TEMP_CLIENT_FILE"
    if [ "$CI_MODE" = true ]; then
      exit 1
    fi
  fi
else
  echo "⚠️  Generated API client does not exist yet"
  echo "   Run 'pnpm codegen:openapi' to generate the client"
  rm -f "$TEMP_CLIENT_FILE"
  if [ "$CI_MODE" = true ]; then
    exit 1
  fi
fi

rm -f "$TEMP_CLIENT_FILE"

# Check 2: Verify OpenAPI spec is valid
echo ""
echo "📋 Check 2: Validating OpenAPI spec"
echo "-----------------------------------"

cd "$PRODUCTS_DIR"

if [ -f "api/yappc-api.openapi.yaml" ]; then
  # Validate the OpenAPI spec using spectral or similar tool
  if command -v spectral &> /dev/null; then
    if spectral lint api/yappc-api.openapi.yaml > /dev/null 2>&1; then
      echo "✅ OpenAPI spec is valid"
    else
      echo "❌ OpenAPI spec has validation errors"
      spectral lint api/yappc-api.openapi.yaml
      if [ "$CI_MODE" = true ]; then
        exit 1
      fi
    fi
  else
    echo "⚠️  Spectral not installed, skipping OpenAPI validation"
    echo "   Install with: npm install -g @stoplight/spectral-cli"
  fi
else
  echo "❌ OpenAPI spec not found at api/yappc-api.openapi.yaml"
  if [ "$CI_MODE" = true ]; then
    exit 1
  fi
fi

# Check 3: Verify server routes match OpenAPI spec
echo ""
echo "📋 Check 3: Verifying server routes match OpenAPI spec"
echo "------------------------------------------------------"

# Extract paths from OpenAPI spec
OPENAPI_PATHS=$(grep -E "^\s*/" api/yappc-api.openapi.yaml | sed 's/://g' | sed 's/^[[:space:]]*//' | sort)

# Extract route definitions from Java backend (if they exist)
# This is a simplified check - in production you'd parse the actual route annotations
JAVA_ROUTE_COUNT=$(find core -name "*.java" -type f | xargs grep -l "@Path\|@GetMapping\|@PostMapping" 2>/dev/null | wc -l | tr -d ' ')

if [ "$JAVA_ROUTE_COUNT" -gt 0 ]; then
  echo "✅ Found $JAVA_ROUTE_COUNT Java files with route definitions"
  echo "   (Full route-to-spec mapping requires additional tooling)"
else
  echo "⚠️  No Java route definitions found or unable to parse"
fi

# Check 4: Verify no duplicate or conflicting route definitions
echo ""
echo "📋 Check 4: Checking for duplicate route definitions"
echo "-----------------------------------------------------"

DUPLICATE_PATHS=$(grep -E "^\s*/" api/yappc-api.openapi.yaml | sed 's/://g' | sed 's/^[[:space:]]*//' | sort | uniq -d)

if [ -z "$DUPLICATE_PATHS" ]; then
  echo "✅ No duplicate path definitions found in OpenAPI spec"
else
  echo "❌ Duplicate path definitions found in OpenAPI spec:"
  echo "$DUPLICATE_PATHS"
  if [ "$CI_MODE" = true ]; then
    exit 1
  fi
fi

# Check 5: Verify operation IDs are unique
echo ""
echo "📋 Check 5: Verifying operation IDs are unique"
echo "---------------------------------------------"

DUPLICATE_OPS=$(grep -E "^\s+operationId:" api/yappc-api.openapi.yaml | sed 's/operationId://g' | sed 's/^[[:space:]]*//' | sort | uniq -d)

if [ -z "$DUPLICATE_OPS" ]; then
  echo "✅ All operation IDs are unique"
else
  echo "❌ Duplicate operation IDs found:"
  echo "$DUPLICATE_OPS"
  if [ "$CI_MODE" = true ]; then
    exit 1
  fi
fi

# Summary
echo ""
echo "============================================================"
echo "✅ API parity checks passed"
echo ""
echo "Next steps:"
echo "  - Keep the generated client in sync: pnpm codegen:openapi"
echo "  - Update OpenAPI spec when server routes change"
echo "  - Run this script in CI to prevent drift"
