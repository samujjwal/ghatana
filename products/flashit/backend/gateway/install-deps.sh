#!/bin/bash

# Install dependencies with a reasonable timeout and better error handling

set -e

echo "Installing dependencies for web-api..."
echo "This may take a few minutes on first run..."

# Use npm ci if lock file exists, otherwise use npm install
if [ -f "package-lock.json" ]; then
    echo "Using npm ci (from package-lock.json)..."
    timeout 300 npm ci --prefer-offline || exit 1
else
    echo "Using npm install..."
    timeout 300 npm install --prefer-offline --no-audit || exit 1
fi

# Verify critical packages
if [ ! -d "node_modules/@fastify/multipart" ]; then
    echo "ERROR: @fastify/multipart not installed"
    exit 1
fi

echo "✅ Dependencies installed successfully"
