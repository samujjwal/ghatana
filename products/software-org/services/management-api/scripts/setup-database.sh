#!/usr/bin/env bash
#
# Database Setup Script for Software-Org Backend
#
# Purpose:
# Automates PostgreSQL setup and Prisma migrations for development and production.
#
# Features:
# - Docker-based PostgreSQL setup
# - Automatic migration execution
# - Seed data generation
# - Connection verification
#
# Usage:
#   ./scripts/setup-database.sh [dev|prod|test]
#
# Environment Variables:
#   DATABASE_URL - PostgreSQL connection string
#   SKIP_DOCKER - Set to "true" to skip Docker setup (use existing DB)

set -e  # Exit on error

ENV="${1:-dev}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "🚀 Setting up database for environment: $ENV"

# Load environment variables
if [ -f "$PROJECT_ROOT/.env" ]; then
    echo "📋 Loading environment from .env"
    export $(cat "$PROJECT_ROOT/.env" | grep -v '^#' | xargs)
else
    echo "⚠️  No .env file found, using .env.example"
    export $(cat "$PROJECT_ROOT/.env.example" | grep -v '^#' | xargs)
fi

DB_HOST_PORT="${DB_HOST_PORT:-5432}"

# Docker setup (unless SKIP_DOCKER=true)
if [ "$SKIP_DOCKER" != "true" ]; then
    echo "🐳 Starting PostgreSQL with Docker on host port ${DB_HOST_PORT}..."
    
    # Check if Docker is running
    if ! docker info > /dev/null 2>&1; then
        echo "❌ Docker is not running. Please start Docker Desktop."
        exit 1
    fi
    
    # Stop existing container (if any)
    docker stop software-org-db 2>/dev/null || true
    docker rm software-org-db 2>/dev/null || true
    
    # Start PostgreSQL container
    docker run -d \
        --name software-org-db \
        -e POSTGRES_USER=software_org \
        -e POSTGRES_PASSWORD=software_org_dev \
        -e POSTGRES_DB=software_org_${ENV} \
        -p "${DB_HOST_PORT}:5432" \
        -v software_org_data:/var/lib/postgresql/data \
        postgres:15-alpine
    
    echo "⏳ Waiting for PostgreSQL to be ready..."
    sleep 5
    
    # Wait for PostgreSQL to accept connections
    for i in {1..30}; do
        if docker exec software-org-db pg_isready -U software_org > /dev/null 2>&1; then
            echo "✅ PostgreSQL is ready!"
            break
        fi
        if [ $i -eq 30 ]; then
            echo "❌ PostgreSQL failed to start"
            exit 1
        fi
        sleep 1
    done
    
    # Update DATABASE_URL for Docker container
    export DATABASE_URL="postgresql://software_org:software_org_dev@localhost:${DB_HOST_PORT}/software_org_${ENV}"
    echo "DATABASE_URL=${DATABASE_URL}" > "$PROJECT_ROOT/.env"
    cat "$PROJECT_ROOT/.env.example" | grep -v '^DATABASE_URL' >> "$PROJECT_ROOT/.env"
fi

# Verify connection
echo "🔍 Verifying database connection..."
cd "$PROJECT_ROOT"

if ! pnpm exec prisma db execute --stdin <<< "SELECT 1"; then
    echo "❌ Failed to connect to database"
    echo "   DATABASE_URL: $DATABASE_URL"
    exit 1
fi

echo "✅ Database connection verified"

# Generate Prisma client
echo "🔧 Generating Prisma client..."
pnpm exec prisma generate

# Run migrations
echo "📦 Running Prisma migrations..."
if [ "$ENV" = "prod" ]; then
    # Production: deploy migrations (no prompts)
    pnpm exec prisma migrate deploy
else
    # Development/Test: deploy existing migrations
    pnpm exec prisma migrate deploy
fi

# Seed database (development only, and only if empty)
if [ "$ENV" = "dev" ]; then
	echo "🌱 Checking if database seed is needed..."

	# Use Prisma to check if the User table already has data.
	# If it does, we skip seeding to avoid re-running on every dev start.
	USER_ROW_COUNT=$(pnpm exec prisma db execute --stdin <<< 'SELECT COUNT(*) FROM "User";' | tail -n 2 | head -n 1 | tr -dc '0-9') || USER_ROW_COUNT="0"

	if [ -n "$USER_ROW_COUNT" ] && [ "$USER_ROW_COUNT" -gt 0 ]; then
		echo "✅ Database already contains data (User count=$USER_ROW_COUNT). Skipping seed."
	else
		echo "🌱 Seeding database (no existing users detected)..."
		# Check if seed script exists
		if [ -f "$PROJECT_ROOT/prisma/seed.ts" ]; then
			pnpm exec tsx "$PROJECT_ROOT/prisma/seed.ts"
		else
			echo "⚠️  No seed script found (prisma/seed.ts)"
		fi
	fi
fi

echo ""
echo "✨ Database setup complete!"
echo ""
echo "📊 Database Info:"
echo "   Environment: $ENV"
echo "   URL: $DATABASE_URL"
echo ""
echo "🔗 Quick Commands:"
echo "   pnpm db:studio    - Open Prisma Studio (GUI)"
echo "   pnpm db:migrate   - Run migrations"
echo "   pnpm db:push      - Push schema changes (dev only)"
echo ""
echo "🧪 Test connection:"
echo "   docker exec software-org-db psql -U software_org -d software_org_${ENV} -c 'SELECT NOW();'"
echo ""
