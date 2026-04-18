#!/bin/bash
#
# TutorPutor Database Restore Script
#
# Restores databases from backup files.
# WARNING: This will overwrite existing data. Use with caution.
#
# Usage:
#   ./restore-database.sh [environment] [backup_timestamp]
#
# Environment: development | staging | production (default: development)
# Backup timestamp: Format YYYYMMDD_HHMMSS (default: latest)
#

set -e

# Configuration
ENVIRONMENT="${1:-development}"
BACKUP_TIMESTAMP="${2:-latest}"
BACKUP_ROOT="/var/backups/tutorputor"
LOG_FILE="/var/log/tutorputor/restore_$(date +%Y%m%d_%H%M%S).log"

# Database configuration
POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_USER="${POSTGRES_USER:-tutorputor}"
POSTGRES_DB="${POSTGRES_DB:-tutorputor_${ENVIRONMENT}}"
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"

# Logging function
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "${LOG_FILE}"
}

log "Starting TutorPutor database restore for ${ENVIRONMENT}"

# Confirmation prompt
if [ "${ENVIRONMENT}" == "production" ]; then
    echo "WARNING: You are about to restore PRODUCTION database."
    echo "This will overwrite all existing data."
    read -p "Type 'PRODUCTION' to confirm: " confirmation
    if [ "${confirmation}" != "PRODUCTION" ]; then
        log "Restore cancelled by user"
        exit 1
    fi
fi

# Find backup file
if [ "${BACKUP_TIMESTAMP}" == "latest" ]; then
    POSTGRES_BACKUP=$(ls -t "${BACKUP_ROOT}/postgres/${POSTGRES_DB}_*.dump" | head -1)
    REDIS_BACKUP=$(ls -t "${BACKUP_ROOT}/redis/redis_*.rdb" | head -1)
else
    POSTGRES_BACKUP="${BACKUP_ROOT}/postgres/${POSTGRES_DB}_${BACKUP_TIMESTAMP}.dump"
    REDIS_BACKUP="${BACKUP_ROOT}/redis/redis_${BACKUP_TIMESTAMP}.rdb"
fi

if [ ! -f "${POSTGRES_BACKUP}" ]; then
    log "ERROR: PostgreSQL backup file not found: ${POSTGRES_BACKUP}"
    exit 1
fi

log "Using PostgreSQL backup: ${POSTGRES_BACKUP}"

# PostgreSQL restore
log "Restoring PostgreSQL database..."
log "Dropping existing database..."
PGPASSWORD="${POSTGRES_PASSWORD}" dropdb -h "${POSTGRES_HOST}" -p "${POSTGRES_PORT}" -U "${POSTGRES_USER}" "${POSTGRES_DB}" || true

log "Creating new database..."
PGPASSWORD="${POSTGRES_PASSWORD}" createdb -h "${POSTGRES_HOST}" -p "${POSTGRES_PORT}" -U "${POSTGRES_USER}" "${POSTGRES_DB}"

log "Restoring from backup..."
PGPASSWORD="${POSTGRES_PASSWORD}" pg_restore \
    -h "${POSTGRES_HOST}" \
    -p "${POSTGRES_PORT}" \
    -U "${POSTGRES_USER}" \
    -d "${POSTGRES_DB}" \
    --format=custom \
    "${POSTGRES_BACKUP}" \
    2>&1 | tee -a "${LOG_FILE}"

if [ $? -eq 0 ]; then
    log "PostgreSQL restore completed successfully"
else
    log "ERROR: PostgreSQL restore failed"
    exit 1
fi

# Redis restore (if backup exists)
if [ -f "${REDIS_BACKUP}" ]; then
    log "Restoring Redis data from: ${REDIS_BACKUP}"
    log "WARNING: This will flush all existing Redis data"
    read -p "Continue with Redis restore? (y/N): " redis_confirm
    if [ "${redis_confirm}" == "y" ]; then
        redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" FLUSHALL
        redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" --rdb "${REDIS_BACKUP}"
        log "Redis restore completed"
    else
        log "Redis restore skipped by user"
    fi
else
    log "WARNING: Redis backup not found, skipping Redis restore"
fi

# File system restore
FILE_BACKUP="${BACKUP_ROOT}/files/uploads_${BACKUP_TIMESTAMP}.tar.gz"
if [ -f "${FILE_BACKUP}" ]; then
    log "Restoring file system data..."
    read -p "Continue with file system restore? (y/N): " files_confirm
    if [ "${files_confirm}" == "y" ]; then
        rm -rf /var/lib/tutorputor/uploads
        tar -xzf "${FILE_BACKUP}" -C /var/lib/tutorputor
        log "File system restore completed"
    else
        log "File system restore skipped by user"
    fi
fi

log "Restore completed successfully"

# Run post-restore migrations
log "Running post-restore database migrations..."
cd /Users/samujjwal/Development/ghatana/products/tutorputor
pnpm --filter @tutorputor/db run migrate

log "All restore operations completed successfully"

exit 0
