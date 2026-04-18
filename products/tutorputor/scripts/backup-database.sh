#!/bin/bash
#
# TutorPutor Database Backup Script
#
# Performs automated backups of TutorPutor databases with retention policy.
# Supports PostgreSQL, Redis, and file system backups.
#
# Usage:
#   ./backup-database.sh [environment]
#
# Environment: development | staging | production (default: development)
#

set -e

# Configuration
ENVIRONMENT="${1:-development}"
BACKUP_ROOT="/var/backups/tutorputor"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RETENTION_DAYS=30
LOG_FILE="/var/log/tutorputor/backup_${TIMESTAMP}.log"

# Database configuration
POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_USER="${POSTGRES_USER:-tutorputor}"
POSTGRES_DB="${POSTGRES_DB:-tutorputor_${ENVIRONMENT}}"
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"

# Backup directories
mkdir -p "${BACKUP_ROOT}/postgres"
mkdir -p "${BACKUP_ROOT}/redis"
mkdir -p "${BACKUP_ROOT}/files"
mkdir -p "$(dirname "${LOG_FILE}")"

# Logging function
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "${LOG_FILE}"
}

log "Starting TutorPutor database backup for ${ENVIRONMENT}"

# PostgreSQL backup
log "Backing up PostgreSQL database..."
PGPASSWORD="${POSTGRES_PASSWORD}" pg_dump \
    -h "${POSTGRES_HOST}" \
    -p "${POSTGRES_PORT}" \
    -U "${POSTGRES_USER}" \
    -d "${POSTGRES_DB}" \
    --format=custom \
    --compress=9 \
    --file="${BACKUP_ROOT}/postgres/${POSTGRES_DB}_${TIMESTAMP}.dump" \
    2>&1 | tee -a "${LOG_FILE}"

if [ $? -eq 0 ]; then
    log "PostgreSQL backup completed successfully"
else
    log "ERROR: PostgreSQL backup failed"
    exit 1
fi

# Redis backup
log "Backing up Redis data..."
redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" --rdb "${BACKUP_ROOT}/redis/redis_${TIMESTAMP}.rdb" BGSAVE
redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" LASTSAVE > /dev/null

if [ $? -eq 0 ]; then
    log "Redis backup completed successfully"
else
    log "WARNING: Redis backup failed (non-critical)"
fi

# File system backup (uploads, exports, etc.)
log "Backing up file system data..."
if [ -d "/var/lib/tutorputor/uploads" ]; then
    tar -czf "${BACKUP_ROOT}/files/uploads_${TIMESTAMP}.tar.gz" -C /var/lib/tutorputor uploads
    log "Uploads backup completed"
fi

if [ -d "/var/lib/tutorputor/exports" ]; then
    tar -czf "${BACKUP_ROOT}/files/exports_${TIMESTAMP}.tar.gz" -C /var/lib/tutorputor exports
    log "Exports backup completed"
fi

# Cleanup old backups
log "Cleaning up backups older than ${RETENTION_DAYS} days..."
find "${BACKUP_ROOT}/postgres" -name "*.dump" -mtime +${RETENTION_DAYS} -delete
find "${BACKUP_ROOT}/redis" -name "*.rdb" -mtime +${RETENTION_DAYS} -delete
find "${BACKUP_ROOT}/files" -name "*.tar.gz" -mtime +${RETENTION_DAYS} -delete
find "$(dirname "${LOG_FILE}")" -name "backup_*.log" -mtime +${RETENTION_DAYS} -delete

log "Cleanup completed"

# Backup summary
TOTAL_SIZE=$(du -sh "${BACKUP_ROOT}" | cut -f1)
log "Backup completed successfully. Total size: ${TOTAL_SIZE}"

# Send notification (implement based on your notification system)
# curl -X POST "${WEBHOOK_URL}" -d "Backup completed for ${ENVIRONMENT}"

exit 0
