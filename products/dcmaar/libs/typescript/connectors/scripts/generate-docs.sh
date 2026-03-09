#!/bin/bash

# Documentation Generation Script for DCMAAR Connectors
# This script generates API documentation using TypeDoc

set -e

echo "🚀 Generating DCMAAR Connectors Documentation..."

# Check if TypeDoc is installed
if ! command -v typedoc &> /dev/null; then
    echo "📦 Installing TypeDoc..."
    npm install --save-dev typedoc
fi

# Generate documentation
echo "📝 Running TypeDoc..."
npx typedoc \
    --out docs/api \
    --entryPoints src/index.ts \
    --exclude "**/*.test.ts" \
    --exclude "**/*.spec.ts" \
    --readme README.md \
    --plugin typedoc-plugin-markdown \
    --theme default \
    --name "DCMAAR Connectors API" \
    --includeVersion \
    --excludePrivate false \
    --excludeProtected false \
    --excludeInternal false

echo "✅ Documentation generated successfully!"
echo "📂 Output: docs/api/"
