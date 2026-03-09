#!/bin/bash

set -e

echo "🔄 Force Regenerating Prisma Client..."
echo ""

cd "$(dirname "${BASH_SOURCE[0]}")"

# Clean everything
echo "1. Cleaning old client..."
rm -rf generated/prisma
rm -rf node_modules/.prisma

# Ensure directory exists
mkdir -p generated/prisma

echo "2. Running prisma generate..."
export DATABASE_URL="${DATABASE_URL:-postgresql://ghatana:ghatana123@localhost:5433/flashit_dev}"
export NODE_ENV=development

# Use absolute paths
SCHEMA_PATH="$(pwd)/prisma/schema.prisma"
OUTPUT_PATH="$(pwd)/generated/prisma"

echo "   Schema: $SCHEMA_PATH"
echo "   Output: $OUTPUT_PATH"
echo ""

# Run generate with explicit paths
./node_modules/.bin/prisma generate \
  --schema="$SCHEMA_PATH" || {
  echo "❌ Generation failed"
  exit 1
}

echo ""
echo "3. Verifying generated client..."

if [ ! -f "generated/prisma/schema.prisma" ]; then
  echo "❌ ERROR: schema.prisma not generated!"
  ls -la generated/prisma/
  exit 1
fi

# Check that the generated schema has PostgreSQL provider
if grep -q "provider = \"postgresql\"" generated/prisma/schema.prisma; then
  echo "✅ Generated schema uses PostgreSQL"
else
  echo "❌ ERROR: Generated schema does NOT use PostgreSQL!"
  grep "provider" generated/prisma/schema.prisma
  exit 1
fi

echo ""
echo "✅ Prisma client successfully generated with PostgreSQL provider!"
