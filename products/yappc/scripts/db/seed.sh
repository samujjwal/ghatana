#!/usr/bin/env bash
set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-yappc}"
DB_USER="${DB_USER:-ghatana}"
DB_PASSWORD="${DB_PASSWORD:-ghatana123}"
SEED_FILE="${SEED_FILE:-products/yappc/platform/src/main/resources/db/seed/V1_0_0__YAPPC_DEV_SEED.sql}"

if [ ! -f "${SEED_FILE}" ]; then
  echo "Seed file not found: ${SEED_FILE}" >&2
  exit 1
fi

export PGPASSWORD="${DB_PASSWORD}"

echo "Applying seed file ${SEED_FILE} to ${DB_HOST}:${DB_PORT}/${DB_NAME}"
psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -f "${SEED_FILE}"

echo "Seed completed successfully"
