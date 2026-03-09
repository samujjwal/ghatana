#!/bin/bash

set -e

cd "$(dirname "${BASH_SOURCE[0]}")"

echo "🔧 Comprehensive Prisma Fix"
echo "=============================="
echo ""

# Step 1: Clean all caches
echo "1. Cleaning Prisma caches..."
rm -rf generated
rm -rf node_modules/.prisma
rm -rf ~/.cache/prisma

echo "   ✅ Caches cleaned"
echo ""

# Step 2: Verify schema
echo "2. Verifying schema..."
if ! grep -q 'provider = "postgresql"' prisma/schema.prisma; then
  echo "   ❌ Schema doesn't have PostgreSQL provider!"
  exit 1
fi
echo "   ✅ Schema uses PostgreSQL"
echo ""

# Step 3: Set environment
echo "3. Setting environment..."
export DATABASE_URL="${DATABASE_URL:-postgresql://ghatana:ghatana123@localhost:5433/flashit_dev}"
export NODE_ENV=development
echo "   ✅ DATABASE_URL set"
echo ""

# Step 4: Clear npm cache
echo "4. Clearing npm/pnpm cache..."
npm cache clean --force 2>/dev/null || true
pnpm store prune 2>/dev/null || true
echo "   ✅ Cache cleared"
echo ""

# Step 5: Reinstall node_modules
echo "5. Reinstalling dependencies..."
rm -rf node_modules
npm install
echo "   ✅ Dependencies installed"
echo ""

# Step 6: Generate Prisma client
echo "6. Generating Prisma client..."
npx prisma generate --schema prisma/schema.prisma

echo ""
echo "=============================="

# Step 7: Verify
echo "7. Verifying generation..."
if [ -f "generated/schema.prisma" ]; then
  echo "   ✅ Schema generated"
  if grep -q 'provider = "postgresql"' generated/schema.prisma; then
    echo "   ✅ Generated schema uses PostgreSQL"
  else
    echo "   ❌ Generated schema doesn't use PostgreSQL!"
    exit 1
  fi
else
  echo "   ❌ Schema not generated!"
  exit 1
fi

echo ""
echo "✅ Prisma client successfully generated!"
echo ""
echo "Ready to start: npm run dev"
