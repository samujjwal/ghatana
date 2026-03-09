#!/bin/bash

set -e

cd "$(dirname "${BASH_SOURCE[0]}")"

echo "🔄 Simple Prisma Generation"
echo ""

# Ensure .env is loaded
export $(cat .env | xargs)

# Create generated folder
mkdir -p generated/prisma

# Copy schema
cp prisma/schema.prisma generated/prisma/schema.prisma

echo "Running: ./node_modules/.bin/prisma generate --schema prisma/schema.prisma"
echo ""

./node_modules/.bin/prisma generate --schema prisma/schema.prisma

echo ""
echo "✅ Generation complete"
echo ""
echo "Verifying..."
if grep -q 'provider = "postgresql"' generated/prisma/schema.prisma; then
  echo "✅ Schema has PostgreSQL provider"
else
  echo "❌ Schema does NOT have PostgreSQL provider"
  exit 1
fi

echo ""
echo "Ready to start: npm run dev"
