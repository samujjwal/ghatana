#!/usr/bin/env bash
set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-yappc}"
DB_USER="${DB_USER:-ghatana}"
DB_PASSWORD="${DB_PASSWORD:-ghatana123}"
MIGRATIONS_DIR="${MIGRATIONS_DIR:-products/yappc/platform/src/main/resources/db/migration}"

if [ ! -d "${MIGRATIONS_DIR}" ]; then
  echo "Migration directory not found: ${MIGRATIONS_DIR}" >&2
  exit 1
fi

echo "Running Flyway migrations against ${DB_HOST}:${DB_PORT}/${DB_NAME}"
docker run --rm \
  --network host \
  -v "${PWD}/${MIGRATIONS_DIR}:/flyway/sql:ro" \
  flyway/flyway:10 \
  -url="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}" \
  -user="${DB_USER}" \
  -password="${DB_PASSWORD}" \
  -locations=filesystem:/flyway/sql \
  migrate

echo "Migration completed successfully"
