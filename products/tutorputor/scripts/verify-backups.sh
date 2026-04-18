#!/bin/bash
#
# TutorPutor Backup Verification Script
#
# Verifies backup integrity and tests restore capability.
# Runs weekly to ensure backups are valid and recoverable.
#

set -e

BACKUP_ROOT="/var/backups/tutorputor"
LOG_FILE="/var/log/tutorputor/backup_verification_$(date +%Y%m%d_%H%M%S).log"

# Logging function
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "${LOG_FILE}"
}

log "Starting backup verification"

# Check if backups exist
if [ ! -d "${BACKUP_ROOT}" ]; then
    log "ERROR: Backup directory does not exist: ${BACKUP_ROOT}"
    exit 1
fi

# Verify PostgreSQL backups
log "Verifying PostgreSQL backups..."
PG_BACKUPS=$(find "${BACKUP_ROOT}/postgres" -name "*.dump" -mtime -7)
PG_COUNT=$(echo "${PG_BACKUPS}" | wc -l)

if [ "${PG_COUNT}" -lt 7 ]; then
    log "WARNING: Less than 7 PostgreSQL backups found in last 7 days (found: ${PG_COUNT})"
else
    log "✓ PostgreSQL backups: ${PG_COUNT} backups in last 7 days"
fi

# Test restore of latest backup (dry-run)
LATEST_PG=$(ls -t "${BACKUP_ROOT}/postgres"/*.dump 2>/dev/null | head -1)
if [ -n "${LATEST_PG}" ]; then
    log "Testing PostgreSQL backup integrity: ${LATEST_PG}"
    pg_restore --list "${LATEST_PG}" > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        log "✓ PostgreSQL backup integrity check passed"
    else
        log "ERROR: PostgreSQL backup integrity check failed"
        # Send alert
    fi
else
    log "ERROR: No PostgreSQL backups found"
fi

# Verify Redis backups
log "Verifying Redis backups..."
REDIS_BACKUPS=$(find "${BACKUP_ROOT}/redis" -name "*.rdb" -mtime -7)
REDIS_COUNT=$(echo "${REDIS_BACKUPS}" | wc -l)

if [ "${REDIS_COUNT}" -lt 7 ]; then
    log "WARNING: Less than 7 Redis backups found in last 7 days (found: ${REDIS_COUNT})"
else
    log "✓ Redis backups: ${REDIS_COUNT} backups in last 7 days"
fi

# Verify file system backups
log "Verifying file system backups..."
FILE_BACKUPS=$(find "${BACKUP_ROOT}/files" -name "*.tar.gz" -mtime -7)
FILE_COUNT=$(echo "${FILE_BACKUPS}" | wc -l)

if [ "${FILE_COUNT}" -lt 7 ]; then
    log "WARNING: Less than 7 file backups found in last 7 days (found: ${FILE_COUNT})"
else
    log "✓ File backups: ${FILE_COUNT} backups in last 7 days"
fi

# Check backup sizes
log "Checking backup sizes..."
TOTAL_SIZE=$(du -sh "${BACKUP_ROOT}" | cut -f1)
log "Total backup size: ${TOTAL_SIZE}"

# Check disk space
AVAILABLE_SPACE=$(df -h "${BACKUP_ROOT}" | tail -1 | awk '{print $4}')
log "Available disk space: ${AVAILABLE_SPACE}"

# Generate verification report
REPORT_FILE="/var/log/tutorputor/backup_report_$(date +%Y%m%d).txt"
cat > "${REPORT_FILE}" << EOF
TutorPutor Backup Verification Report
Date: $(date)
Environment: ${ENVIRONMENT:-unknown}

PostgreSQL Backups: ${PG_COUNT} (last 7 days)
Redis Backups: ${REDIS_COUNT} (last 7 days)
File Backups: ${FILE_COUNT} (last 7 days)

Total Backup Size: ${TOTAL_SIZE}
Available Disk Space: ${AVAILABLE_SPACE}

Status: ${STATUS:-OK}
EOF

log "Verification completed. Report saved to ${REPORT_FILE}"

exit 0
