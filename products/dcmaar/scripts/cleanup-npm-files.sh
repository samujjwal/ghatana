#!/bin/bash

# Remove all package-lock.json files
echo "Removing package-lock.json files..."
find . -name "package-lock.json" -type f -delete

# Remove node_modules directories
echo "Removing node_modules directories..."
find . -name "node_modules" -type d -exec rm -rf {} +

echo "Cleanup complete. Please run 'pnpm install' to reinstall dependencies."
