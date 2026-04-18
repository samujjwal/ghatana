#!/usr/bin/env bash
set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-yappc}"
DB_USER="${DB_USER:-ghatana}"
DB_PASSWORD="${DB_PASSWORD:-ghatana123}"
BACKUP_DIR="${BACKUP_DIR:-products/yappc/backups}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_FILE="${BACKUP_DIR}/yappc-${DB_NAME}-${TIMESTAMP}.dump"

mkdir -p "${BACKUP_DIR}"
export PGPASSWORD="${DB_PASSWORD}"

echo "Creating backup ${BACKUP_FILE}"
pg_dump -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -Fc -f "${BACKUP_FILE}"

echo "Backup created: ${BACKUP_FILE}"
