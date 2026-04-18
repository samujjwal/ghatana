#!/usr/bin/env bash
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: $0 <backup-file> [target-db-name]" >&2
  exit 1
fi

BACKUP_FILE="$1"
TARGET_DB_NAME="${2:-${TARGET_DB_NAME:-yappc_restore}}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-ghatana}"
DB_PASSWORD="${DB_PASSWORD:-ghatana123}"

if [ ! -f "${BACKUP_FILE}" ]; then
  echo "Backup file not found: ${BACKUP_FILE}" >&2
  exit 1
fi

export PGPASSWORD="${DB_PASSWORD}"

echo "Recreating target database ${TARGET_DB_NAME}"
psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres -c "DROP DATABASE IF EXISTS ${TARGET_DB_NAME};"
psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres -c "CREATE DATABASE ${TARGET_DB_NAME};"

echo "Restoring backup into ${TARGET_DB_NAME}"
pg_restore -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${TARGET_DB_NAME}" "${BACKUP_FILE}"

echo "Restore completed"
