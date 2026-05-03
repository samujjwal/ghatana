#!/bin/bash
#
# AEP Disaster Recovery - Backup Script
#
# Performs automated backup of AEP critical data including:
# - Pipeline configurations
# - Agent registry
# - Learning policies
# - Governance state
# - Checkpoint data
#
# Usage: ./backup-aep.sh [options]
#   --tenant-id <id>    Tenant ID to backup (required)
#   --output-dir <dir>  Output directory for backups (default: /backups/aep)
#   --retention <days>  Retention period in days (default: 30)
#   --full              Perform full backup (default: incremental)
#   --compress          Compress backup files (default: true)
#
# @doc.type script
# @doc.purpose Automated backup of AEP critical data
# @doc.layer operations
#

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="${BACKUP_DIR:-/backups/aep}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
TENANT_ID=""
FULL_BACKUP=false
COMPRESS=true
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG_FILE="${BACKUP_DIR}/backup_${TIMESTAMP}.log"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $*" | tee -a "$LOG_FILE"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $*" | tee -a "$LOG_FILE"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $*" | tee -a "$LOG_FILE"
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --tenant-id)
            TENANT_ID="$2"
            shift 2
            ;;
        --output-dir)
            BACKUP_DIR="$2"
            shift 2
            ;;
        --retention)
            RETENTION_DAYS="$2"
            shift 2
            ;;
        --full)
            FULL_BACKUP=true
            shift
            ;;
        --no-compress)
            COMPRESS=false
            shift
            ;;
        --help)
            echo "Usage: $0 --tenant-id <id> [--output-dir <dir>] [--retention <days>] [--full] [--no-compress]"
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Validate required arguments
if [[ -z "$TENANT_ID" ]]; then
    log_error "Tenant ID is required. Use --tenant-id <id>"
    exit 1
fi

# Create backup directory
BACKUP_PATH="${BACKUP_DIR}/${TENANT_ID}/${TIMESTAMP}"
mkdir -p "$BACKUP_PATH"

log_info "Starting AEP backup for tenant: $TENANT_ID"
log_info "Backup path: $BACKUP_PATH"
log_info "Full backup: $FULL_BACKUP"
log_info "Compress: $COMPRESS"

# Function to backup from AEP API
backup_from_api() {
    local endpoint="$1"
    local output_file="$2"
    local description="$3"
    
    log_info "Backing up $description..."
    
    local api_url="http://localhost:8080${endpoint}?tenantId=${TENANT_ID}"
    
    if curl -s -f "$api_url" -o "$output_file"; then
        log_info "Successfully backed up $description"
        return 0
    else
        log_error "Failed to backup $description from $api_url"
        return 1
    fi
}

# Function to backup from database
backup_from_db() {
    local query="$1"
    local output_file="$2"
    local description="$3"
    
    log_info "Backing up $description from database..."
    
    # Use psql or appropriate database client
    # This is a placeholder - adjust based on actual database setup
    if PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST:-localhost}" -U "${DB_USER:-aep}" \
        -d "${DB_NAME:-aep}" -c "\copy (${query}) TO '${output_file}' WITH CSV HEADER"; then
        log_info "Successfully backed up $description"
        return 0
    else
        log_error "Failed to backup $description from database"
        return 1
    fi
}

# Perform backups
backup_errors=0

# Backup pipeline configurations
backup_from_api "/api/v1/pipelines" "${BACKUP_PATH}/pipelines.json" "Pipeline configurations" || ((backup_errors++))

# Backup agent registry
backup_from_api "/api/v1/agents" "${BACKUP_PATH}/agents.json" "Agent registry" || ((backup_errors++))

# Backup learning policies
backup_from_api "/api/v1/learning/policies" "${BACKUP_PATH}/policies.json" "Learning policies" || ((backup_errors++))

# Backup patterns
backup_from_api "/api/v1/patterns" "${BACKUP_PATH}/patterns.json" "Patterns" || ((backup_errors++))

# Backup governance state
backup_from_api "/api/v1/governance/kill-switch" "${BACKUP_PATH}/kill-switch.json" "Kill switch state" || ((backup_errors++))
backup_from_api "/api/v1/governance/degradation" "${BACKUP_PATH}/degradation.json" "Degradation mode" || ((backup_errors++))

# Backup audit log (last 1000 entries)
backup_from_api "/api/v1/governance/audit/summary?limit=1000" "${BACKUP_PATH}/audit-summary.json" "Audit summary" || ((backup_errors++))

# Backup compliance state
backup_from_api "/api/v1/governance/compliance/summary" "${BACKUP_PATH}/compliance-summary.json" "Compliance summary" || ((backup_errors++))

# Backup checkpoint data (if using PostgreSQL)
if [[ "$FULL_BACKUP" == true ]]; then
    backup_from_db "SELECT * FROM pipeline_checkpoint" "${BACKUP_PATH}/pipeline_checkpoint.csv" "Pipeline checkpoints" || ((backup_errors++))
    backup_from_db "SELECT * FROM step_checkpoint" "${BACKUP_PATH}/step_checkpoint.csv" "Step checkpoints" || ((backup_errors++))
fi

# Create backup manifest
cat > "${BACKUP_PATH}/backup-manifest.json" <<EOF
{
  "tenant_id": "${TENANT_ID}",
  "backup_timestamp": "${TIMESTAMP}",
  "backup_type": "$([ "$FULL_BACKUP" = true ] && echo "full" || echo "incremental")",
  "compressed": $COMPRESS,
  "components": [
    "pipelines",
    "agents",
    "policies",
    "patterns",
    "governance",
    "audit",
    "compliance",
    "checkpoints"
  ],
  "version": "1.0.0"
}
EOF

log_info "Backup manifest created"

# Compress backup if enabled
if [[ "$COMPRESS" == true ]]; then
    log_info "Compressing backup..."
    if tar -czf "${BACKUP_PATH}.tar.gz" -C "${BACKUP_DIR}/${TENANT_ID}" "${TIMESTAMP}"; then
        log_info "Backup compressed successfully: ${BACKUP_PATH}.tar.gz"
        rm -rf "${BACKUP_PATH}"
        BACKUP_PATH="${BACKUP_PATH}.tar.gz"
    else
        log_error "Failed to compress backup"
        ((backup_errors++))
    fi
fi

# Generate backup checksum
if command -v sha256sum &> /dev/null; then
    sha256sum "$BACKUP_PATH" > "${BACKUP_PATH}.sha256"
    log_info "Backup checksum created"
fi

# Clean up old backups
log_info "Cleaning up backups older than ${RETENTION_DAYS} days..."
find "${BACKUP_DIR}/${TENANT_ID}" -name "*.tar.gz" -mtime +${RETENTION_DAYS} -delete
find "${BACKUP_DIR}/${TENANT_ID}" -name "*.sha256" -mtime +${RETENTION_DAYS} -delete

# Final status
if [[ $backup_errors -eq 0 ]]; then
    log_info "Backup completed successfully"
    log_info "Backup location: $BACKUP_PATH"
    
    # Update governance ops summary
    log_info "Updating governance ops summary with backup status..."
    # This would call an API to update the backup status
    
    exit 0
else
    log_error "Backup completed with $backup_errors error(s)"
    exit 1
fi
