#!/bin/bash

# Install dependencies with a reasonable timeout and better error handling

set -e

echo "Installing dependencies for FlashIt gateway..."
echo "This may take a few minutes on first run..."

# Install through the product-local pnpm workspace so gateway dependencies,
# workspace links, and the canonical lockfile stay in sync.
echo "Using pnpm workspace install..."
timeout 300 pnpm --dir .. install --frozen-lockfile || exit 1

# Verify critical packages
if [ ! -d "node_modules/@fastify/multipart" ]; then
    echo "ERROR: @fastify/multipart not installed"
    exit 1
fi

echo "✅ Dependencies installed successfully"
