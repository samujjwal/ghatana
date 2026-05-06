#!/bin/bash
set -e

cd "$(dirname "${BASH_SOURCE[0]}")"

echo "Step 1: Removing old Prisma client..."
rm -rf node_modules/.prisma 2>/dev/null || true
rm -rf node_modules/.bin/prisma 2>/dev/null || true

echo "Step 2: Setting DATABASE_URL..."
if [ -z "${DATABASE_URL:-}" ]; then
  echo "❌ DATABASE_URL must be set before running this script"
  exit 1
fi

echo "Step 3: Running prisma generate..."
pnpm exec prisma generate --schema=prisma/schema.prisma

echo "Step 4: Verifying schema in generated client..."
if grep -q 'provider = "postgresql"' node_modules/.prisma/client/schema.prisma; then
  echo "✅ SUCCESS: Generated client has PostgreSQL provider"
else
  echo "❌ FAILED: Generated client does not have PostgreSQL provider"
  exit 1
fi

echo "Done!"
