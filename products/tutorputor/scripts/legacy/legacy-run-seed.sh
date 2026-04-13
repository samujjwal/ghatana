#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log() { echo -e "${BLUE}→${NC} $1"; }
success() { echo -e "${GREEN}✅${NC} $1"; }
error() { echo -e "${RED}✗${NC} $1"; }
warn() { echo -e "${YELLOW}⚠️${NC} $1"; }

cd "$(dirname "$0")"

log "Running TutorPutor database seed..."
echo ""

# Navigate to db service
cd services/tutorputor-db

# Set environment
export COREPACK_ENABLE_AUTO_PIN=0
export NODE_ENV=development
export TUTORPUTOR_SEED_PROFILE=demo

log "Step 1: Regenerating Prisma client..."
if pnpm exec prisma generate; then
  success "Prisma client generated"
else
  error "Failed to generate Prisma client"
  exit 1
fi

echo ""
log "Step 2: Creating/syncing database schema..."
if pnpm exec prisma db push --skip-generate --force-reset 2>&1 | tee /tmp/prisma-push.log; then
  success "Database schema synced"
else
  warn "Schema push had issues (this might be okay for first run)"
  # Don't exit - let seeding continue
fi

echo ""
log "Step 3: Running seed (profile=demo)..."
# Run with timeout and capture output
if timeout 120 pnpm prisma db seed 2>&1 | tee /tmp/seed.log; then
  success "Database seeded successfully"
  success "Seed profile: demo"
  echo ""
  echo "Database is ready for development!"
else
  EXIT_CODE=$?
  if [ $EXIT_CODE -eq 124 ]; then
    error "Seed timed out after 120 seconds"
  else
    error "Seed failed with exit code $EXIT_CODE"
  fi
  echo ""
  warn "Check /tmp/seed.log for details:"
  tail -50 /tmp/seed.log
  exit 1
fi

echo ""
success "TutorPutor database ready!"
