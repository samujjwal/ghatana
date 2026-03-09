#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "=========================================="
echo "Flashit Clean Setup & Generation"
echo "=========================================="
echo ""

# Step 1: Clean everything
echo "Step 1: Cleaning old artifacts..."
rm -rf node_modules/.prisma 2>/dev/null || true
rm -rf generated/prisma 2>/dev/null || true
rm -rf dist 2>/dev/null || true
echo "✅ Cleaned"
echo ""

# Step 2: Verify environment
echo "Step 2: Checking .env file..."
if [ ! -f ".env" ]; then
  echo "❌ ERROR: .env file not found in $(pwd)"
  echo ""
  echo "Please create .env with:"
  echo "DATABASE_URL=postgresql://ghatana:ghatana123@localhost:5433/flashit_dev"
  exit 1
fi
echo "✅ .env exists"
echo ""

# Step 3: Verify schema
echo "Step 3: Verifying schema..."
if ! grep -q 'provider = "postgresql"' prisma/schema.prisma; then
  echo "❌ ERROR: schema.prisma does not have provider = \"postgresql\""
  exit 1
fi
echo "✅ Schema has PostgreSQL provider"
echo ""

# Step 4: Install dependencies
echo "Step 4: Installing dependencies..."
npm install 2>&1 | grep -E "added|up to date" || true
echo "✅ Dependencies installed"
echo ""

# Step 5: Generate Prisma client
echo "Step 5: Generating Prisma client..."
npm run db:generate 2>&1

echo ""
echo "Step 6: Verifying generation..."
if [ -f "generated/prisma/index.js" ]; then
  echo "✅ Prisma client generated at: generated/prisma/index.js"
  
  # Check provider
  if grep -q '"activeProvider": "postgresql"' generated/prisma/index.js; then
    echo "✅ Provider is PostgreSQL"
  else
    echo "⚠️  Could not confirm provider in index.js"
    if grep -q 'provider = "postgresql"' generated/prisma/schema.prisma 2>/dev/null; then
      echo "✅ But schema.prisma confirms PostgreSQL"
    fi
  fi
else
  echo "❌ ERROR: Generation failed - index.js not found"
  exit 1
fi

echo ""
echo "=========================================="
echo "✅ Setup Complete!"
echo "=========================================="
echo ""
echo "To start the API server, run:"
echo "  npm run dev"
echo ""
