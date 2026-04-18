#!/usr/bin/env bash
set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-yappc}"
DB_USER="${DB_USER:-ghatana}"
DB_PASSWORD="${DB_PASSWORD:-ghatana123}"
VERIFY_DB_NAME="${VERIFY_DB_NAME:-yappc_restore_verify}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
BACKUP_TMP_DIR="${PROJECT_ROOT}/products/yappc/backups"

mkdir -p "${BACKUP_TMP_DIR}"

export DB_HOST DB_PORT DB_NAME DB_USER DB_PASSWORD BACKUP_DIR="${BACKUP_TMP_DIR}"
"${SCRIPT_DIR}/backup.sh"

LATEST_BACKUP="$(ls -t "${BACKUP_TMP_DIR}"/*.dump | head -1)"
if [ -z "${LATEST_BACKUP}" ]; then
  echo "No backup file generated" >&2
  exit 1
fi

export TARGET_DB_NAME="${VERIFY_DB_NAME}"
"${SCRIPT_DIR}/restore.sh" "${LATEST_BACKUP}" "${VERIFY_DB_NAME}"

export PGPASSWORD="${DB_PASSWORD}"
TENANT_COUNT="$(psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${VERIFY_DB_NAME}" -tAc 'SELECT COUNT(*) FROM tenants;')"

if [ "${TENANT_COUNT}" -lt 1 ]; then
  echo "Backup/restore verification failed: tenants table is empty" >&2
  exit 1
fi

echo "Backup/restore verification passed. tenants=${TENANT_COUNT}"
