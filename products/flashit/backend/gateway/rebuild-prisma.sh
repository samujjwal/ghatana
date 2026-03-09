#!/bin/bash

cd /home/samujjwal/Developments/ghatana/products/flashit/apps/web-api

echo "Step 1: Removing old Prisma client..."
rm -rf node_modules/.prisma 2>/dev/null || true
rm -rf node_modules/.bin/prisma 2>/dev/null || true

echo "Step 2: Setting DATABASE_URL..."
export DATABASE_URL="postgresql://ghatana:ghatana123@localhost:5433/flashit_dev"

echo "Step 3: Running prisma generate..."
npx prisma generate --schema=prisma/schema.prisma

echo "Step 4: Verifying schema in generated client..."
if grep -q 'provider = "postgresql"' node_modules/.prisma/client/schema.prisma; then
  echo "✅ SUCCESS: Generated client has PostgreSQL provider"
else
  echo "❌ FAILED: Generated client does not have PostgreSQL provider"
  exit 1
fi

echo "Done!"
