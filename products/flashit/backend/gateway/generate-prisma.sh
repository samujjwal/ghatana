#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "=========================================="
echo "Flashit Prisma Client Generation"
echo "=========================================="
echo ""

# Step 1: Verify environment
echo "Step 1: Checking environment..."
if [ ! -f ".env" ]; then
  echo "❌ ERROR: .env file not found"
  echo "Creating .env from .env.example..."
  cp .env.example .env
  echo "⚠️  WARNING: Please configure .env with actual values"
fi

# Source .env for inspection
export $(cat .env | grep -v '^#' | xargs)

if [ -z "$DATABASE_URL" ]; then
  echo "❌ ERROR: DATABASE_URL not set in .env"
  exit 1
fi

echo "✅ DATABASE_URL is set: ${DATABASE_URL:0:40}..."
echo ""

# Step 2: Delete old Prisma client
echo "Step 2: Cleaning old Prisma client..."
rm -rf node_modules/.prisma 2>/dev/null || true
rm -rf node_modules/.next 2>/dev/null || true
rm -rf dist 2>/dev/null || true
echo "✅ Old files deleted"
echo ""

# Step 3: Verify schema
echo "Step 3: Verifying Prisma schema..."
if ! grep -q 'provider = "postgresql"' prisma/schema.prisma; then
  echo "❌ ERROR: schema.prisma does not have provider = \"postgresql\""
  echo "Current provider:"
  grep "provider =" prisma/schema.prisma | head -1
  exit 1
fi
echo "✅ Schema provider is correct: postgresql"
echo ""

# Step 4: Run Prisma generate
echo "Step 4: Generating Prisma client..."
echo "Command: npx prisma generate --schema prisma/schema.prisma"
npx prisma generate --schema prisma/schema.prisma 2>&1

echo ""
echo "Step 5: Verifying generated client..."
if [ ! -f "node_modules/.prisma/client/index.js" ]; then
  echo "❌ ERROR: Generation failed - index.js not created"
  exit 1
fi

# Verify the generated schema has postgresql
if grep -q '"activeProvider": "postgresql"' node_modules/.prisma/client/index.js; then
  echo "✅ Generated client has correct provider: postgresql"
else
  echo "⚠️  WARNING: Could not confirm provider in generated client"
  echo "Checking schema in generated files..."
  if grep -q 'provider = "postgresql"' node_modules/.prisma/client/schema.prisma 2>/dev/null; then
    echo "✅ Schema file has correct provider"
  fi
fi

echo ""
echo "=========================================="
echo "✅ SUCCESS: Prisma client generated!"
echo "=========================================="
echo ""
echo "You can now start the API server:"
echo "  npm run dev"
echo ""
