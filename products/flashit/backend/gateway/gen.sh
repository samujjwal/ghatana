#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "=========================================="
echo "Generating Prisma Client"
echo "=========================================="
echo ""

# Load environment
if [ -f ".env" ]; then
  export $(cat .env | grep -v '^#' | xargs)
  echo "✅ Loaded .env file"
else
  echo "⚠️  No .env file found, using environment variables"
fi

echo "DATABASE_URL: ${DATABASE_URL:0:50}..."
echo ""

# Ensure node_modules exists
if [ ! -d "node_modules" ]; then
  echo "Installing dependencies..."
  npm install
fi

# Delete old generated client
echo "Cleaning old generated client..."
rm -rf generated/prisma 2>/dev/null || true

echo ""
echo "Generating Prisma client from prisma/schema.prisma..."
npx prisma generate --schema prisma/schema.prisma

echo ""
echo "Verifying generation..."
if [ -f "generated/prisma/index.js" ]; then
  echo "✅ SUCCESS: Prisma client generated"
  ls -lh generated/prisma/
else
  echo "❌ FAILED: generated/prisma/index.js not found"
  exit 1
fi

echo ""
echo "Ready to start with: npm run dev"
