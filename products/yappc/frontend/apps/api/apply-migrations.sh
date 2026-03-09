#!/bin/bash
set -e

echo "📦 Applying Prisma migrations to database..."
echo ""

# Change to API directory
cd "$(dirname "$0")"

# Load .env if exists
if [ -f .env ]; then
    echo "✅ Loading environment from .env"
    export $(cat .env | grep -v '^#' | xargs)
else
    echo "⚠️  No .env file found, using environment variables"
fi

echo ""
echo "🔍 Checking migration status..."
npx prisma migrate status --schema=prisma/schema.prisma

echo ""
echo "🚀 Applying migrations..."
npx prisma migrate deploy --schema=prisma/schema.prisma

echo ""
echo "✅ Migrations applied successfully!"
echo ""
echo "🌱 Run seed script? (y/n)"
read -r response
if [[ "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
    echo "🌱 Running seed..."
    npx tsx prisma/seed.ts
    echo "✅ Seed completed!"
fi

echo ""
echo "✨ All done! Your database is ready."
