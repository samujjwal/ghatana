#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$API_DIR/../../../../.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/products/yappc/deployment/docker/docker-compose.db.yml"
TEST_DATABASE_URL="postgresql://ghatana:ghatana123@localhost:5434/yappc?schema=public"

cleanup() {
  docker compose -f "$COMPOSE_FILE" down -v >/dev/null 2>&1 || true
}

trap cleanup EXIT

cd "$API_DIR"

echo "[yappc-api] Starting disposable Postgres for lifecycle integration tests"
docker compose -f "$COMPOSE_FILE" up -d postgres

echo "[yappc-api] Waiting for Postgres readiness"
for _ in $(seq 1 30); do
  if docker compose -f "$COMPOSE_FILE" exec -T postgres pg_isready -U ghatana -d yappc >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! docker compose -f "$COMPOSE_FILE" exec -T postgres pg_isready -U ghatana -d yappc >/dev/null 2>&1; then
  echo "[yappc-api] Postgres did not become ready"
  exit 1
fi

echo "[yappc-api] Generating Prisma client"
npx prisma generate --schema=prisma/schema.prisma

echo "[yappc-api] Applying schema to disposable database"
DATABASE_URL="$TEST_DATABASE_URL" npx prisma db push --schema=prisma/schema.prisma

echo "[yappc-api] Running lifecycle gate integration suite"
TEST_DATABASE_URL="$TEST_DATABASE_URL" \
JWT_ACCESS_SECRET="test-secret-for-gate-tests" \
pnpm exec vitest run src/__tests__/lifecycle-gates.integration.test.ts