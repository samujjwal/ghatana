#!/bin/bash

echo "Testing environment..."
echo "Current directory: $(pwd)"
echo "Node version: $(node --version 2>&1 || echo 'Node not found')"
echo "npm version: $(npm --version 2>&1 || echo 'npm not found')"
echo "npx version: $(npx --version 2>&1 || echo 'npx not found')"

echo "Checking for buf..."
if command -v buf &> /dev/null; then
    echo "buf found at: $(which buf)"
    echo "buf version: $(buf --version 2>&1 || echo 'Could not get version')
else
    echo "buf not found in PATH"
fi

echo "Checking for npx buf..."
npx --yes @bufbuild/buf --version || echo "npx @bufbuild/buf not available"

echo "Environment test complete."
