#!/bin/bash

set -e

echo "🔧 Prisma Client Generation Script"
echo "===================================="
echo ""

cd "$(dirname "${BASH_SOURCE[0]}")"

echo "📍 Working directory: $(pwd)"
echo ""

# Step 1: Clean
echo "🗑️  Step 1: Cleaning generated folder..."
rm -rf generated/prisma
echo "✅ Cleaned"
echo ""

# Step 2: Create directory
echo "📁 Step 2: Creating generated directory..."
mkdir -p generated
echo "✅ Created"
echo ""

# Step 3: Check schema
echo "📖 Step 3: Validating schema..."
if [ ! -f "prisma/schema.prisma" ]; then
  echo "❌ ERROR: prisma/schema.prisma not found!"
  exit 1
fi
echo "✅ Schema found at prisma/schema.prisma"
echo ""

# Step 4: Check dependencies
echo "📦 Step 4: Checking Prisma installation..."
if [ ! -d "node_modules/@prisma/client" ]; then
  echo "❌ ERROR: @prisma/client not installed!"
  echo "Run: npm install"
  exit 1
fi
echo "✅ @prisma/client is installed"
echo ""

# Step 5: Generate
echo "⏳ Step 5: Running Prisma generate..."
echo ""

# Run with absolute path
ABSOLUTE_SCHEMA="$(pwd)/prisma/schema.prisma"
ABSOLUTE_OUTPUT="$(pwd)/generated/prisma"

echo "Schema path: $ABSOLUTE_SCHEMA"
echo "Output path: $ABSOLUTE_OUTPUT"
echo ""

./node_modules/.bin/prisma generate \
  --schema="$ABSOLUTE_SCHEMA" || {
  echo "❌ Prisma generation failed"
  exit 1
}

echo ""
echo "===================================="

# Step 6: Verify
echo "✅ Prisma client generated successfully!"
echo ""

if [ -f "generated/prisma/index.js" ]; then
  echo "✅ Verified: generated/prisma/index.js exists"
  SIZE=$(wc -c < "generated/prisma/index.js")
  echo "   Size: $SIZE bytes"
  echo ""
  echo "🎉 Ready to start! Run: npm run dev"
else
  echo "❌ ERROR: generated/prisma/index.js not found!"
  echo ""
  echo "Debug info:"
  echo "Contents of generated/:"
  ls -la generated/ || echo "  (empty or no directory)"
  exit 1
fi
