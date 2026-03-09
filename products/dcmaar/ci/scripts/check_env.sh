#!/bin/bash

echo "=== Environment Check ==="
echo "Current directory: $(pwd)"
echo "Home directory: $HOME"

echo -e "\n=== Installed Tools ==="
echo "Node.js: $(node --version 2>/dev/null || echo 'Not installed')"
echo "npm: $(npm --version 2>/dev/null || echo 'Not installed')"
echo "npx: $(npx --version 2>/dev/null || echo 'Not installed')"
echo "buf: $(which buf 2>/dev/null || echo 'Not in PATH')"

echo -e "\n=== Directory Contents ==="
ls -la

echo -e "\n=== Proto Directory ==="
ls -la proto/ 2>/dev/null || echo "Proto directory not found"

echo -e "\n=== Checking buf Installation ==="
if command -v buf &> /dev/null; then
    echo "buf found at: $(which buf)"
    echo "buf version: $(buf --version 2>&1 || echo 'Could not get version')"
    
    echo -e "\n=== Running buf lint ==="
    cd proto && buf lint
else
    echo "buf not found in PATH"
    
    echo -e "\n=== Trying with npx ==="
    cd proto && npx @bufbuild/buf --version && npx @bufbuild/buf lint
fi
