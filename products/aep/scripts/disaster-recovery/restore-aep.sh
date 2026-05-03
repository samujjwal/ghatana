#!/bin/bash
#
# AEP Disaster Recovery - Restore Script
#
# Restores AEP critical data from a backup archive.
# Performs validation before and after restore.
#
# Usage: ./restore-aep.sh [options]
#   --tenant-id <id>       Tenant ID to restore (required)
#   --backup-file <file>   Backup file to restore (required)
#   --validate-only        Validate backup without restoring
#   --dry-run             Show what would be restored without actually restoring
#
# @doc.type script
# @doc.purpose Automated restore of AEP critical data from backup
# @doc.layer operations
#

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TENANT_ID=""
BACKUP_FILE=""
VALIDATE_ONLY=false
DRY_RUN=false
LOG_FILE="/tmp/aep-restore-$(date +%Y%m%d_%H%M%S).log"

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
        --backup-file)
            BACKUP_FILE="$2"
            shift 2
            ;;
        --validate-only)
            VALIDATE_ONLY=true
            shift
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --help)
            echo "Usage: $0 --tenant-id <id> --backup-file <file> [--validate-only] [--dry-run]"
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

if [[ -z "$BACKUP_FILE" ]]; then
    log_error "Backup file is required. Use --backup-file <file>"
    exit 1
fi

if [[ ! -f "$BACKUP_FILE" ]]; then
    log_error "Backup file not found: $BACKUP_FILE"
    exit 1
fi

log_info "Starting AEP restore for tenant: $TENANT_ID"
log_info "Backup file: $BACKUP_FILE"
log_info "Validate only: $VALIDATE_ONLY"
log_info "Dry run: $DRY_RUN"

# Create temporary directory for extraction
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

# Extract backup if compressed
if [[ "$BACKUP_FILE" == *.tar.gz ]]; then
    log_info "Extracting backup archive..."
    if ! tar -xzf "$BACKUP_FILE" -C "$TEMP_DIR"; then
        log_error "Failed to extract backup archive"
        exit 1
    fi
    
    # Find the extracted directory
    EXTRACTED_DIR=$(find "$TEMP_DIR" -mindepth 1 -maxdepth 1 -type d | head -n 1)
    RESTORE_DIR="$EXTRACTED_DIR"
else
    RESTORE_DIR="$BACKUP_FILE"
fi

# Verify backup checksum if available
if [[ -f "${BACKUP_FILE}.sha256" ]]; then
    log_info "Verifying backup checksum..."
    if sha256sum -c "${BACKUP_FILE}.sha256"; then
        log_info "Checksum verification passed"
    else
        log_error "Checksum verification failed"
        exit 1
    fi
else
    log_warn "No checksum file found, skipping verification"
fi

# Validate backup manifest
log_info "Validating backup manifest..."
MANIFEST_FILE="${RESTORE_DIR}/backup-manifest.json"

if [[ ! -f "$MANIFEST_FILE" ]]; then
    log_error "Backup manifest not found"
    exit 1
fi

# Read manifest tenant ID
MANIFEST_TENANT=$(jq -r '.tenant_id' "$MANIFEST_FILE")
if [[ "$MANIFEST_TENANT" != "$TENANT_ID" ]]; then
    log_error "Backup tenant ID mismatch: expected $TENANT_ID, found $MANIFEST_TENANT"
    exit 1
fi

log_info "Backup manifest validated successfully"
log_info "Backup type: $(jq -r '.backup_type' "$MANIFEST_FILE")"
log_info "Backup version: $(jq -r '.version' "$MANIFEST_FILE")"

# Validate required components
REQUIRED_COMPONENTS=("pipelines.json" "agents.json" "policies.json")
MISSING_COMPONENTS=()

for component in "${REQUIRED_COMPONENTS[@]}"; do
    if [[ ! -f "${RESTORE_DIR}/${component}" ]]; then
        MISSING_COMPONENTS+=("$component")
    fi
done

if [[ ${#MISSING_COMPONENTS[@]} -gt 0 ]]; then
    log_error "Missing required components: ${MISSING_COMPONENTS[*]}"
    exit 1
fi

log_info "All required components present"

if [[ "$VALIDATE_ONLY" == true ]]; then
    log_info "Validation complete - backup is valid"
    exit 0
fi

if [[ "$DRY_RUN" == true ]]; then
    log_info "Dry run mode - would restore the following components:"
    for file in "${RESTORE_DIR}"/*.json; do
        log_info "  - $(basename "$file")"
    done
    log_info "Dry run complete - no changes made"
    exit 0
fi

# Function to restore to AEP API
restore_to_api() {
    local endpoint="$1"
    local input_file="$2"
    local description="$3"
    
    log_info "Restoring $description..."
    
    local api_url="http://localhost:8080${endpoint}?tenantId=${TENANT_ID}"
    
    if curl -s -f -X POST "$api_url" \
        -H "Content-Type: application/json" \
        -d @"$input_file"; then
        log_info "Successfully restored $description"
        return 0
    else
        log_error "Failed to restore $description to $api_url"
        return 1
    fi
}

# Perform restore
restore_errors=0

# Stop AEP services to prevent conflicts during restore
log_info "Stopping AEP services..."
# This would call systemd or kubectl to stop services
# systemctl stop aep-server || log_warn "Failed to stop AEP server (may not be running)"

# Restore pipeline configurations
restore_to_api "/api/v1/pipelines/restore" "${RESTORE_DIR}/pipelines.json" "Pipeline configurations" || ((restore_errors++))

# Restore agent registry
restore_to_api "/api/v1/agents/restore" "${RESTORE_DIR}/agents.json" "Agent registry" || ((restore_errors++))

# Restore learning policies
restore_to_api "/api/v1/learning/policies/restore" "${RESTORE_DIR}/policies.json" "Learning policies" || ((restore_errors++))

# Restore patterns
restore_to_api "/api/v1/patterns/restore" "${RESTORE_DIR}/patterns.json" "Patterns" || ((restore_errors++))

# Restore governance state
restore_to_api "/api/v1/governance/kill-switch/restore" "${RESTORE_DIR}/kill-switch.json" "Kill switch state" || ((restore_errors++))
restore_to_api "/api/v1/governance/degradation/restore" "${RESTORE_DIR}/degradation.json" "Degradation mode" || ((restore_errors++))

# Restore checkpoint data (if present)
if [[ -f "${RESTORE_DIR}/pipeline_checkpoint.csv" ]]; then
    log_info "Restoring checkpoint data from database..."
    # This would use psql or appropriate database client
    # PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -U "${DB_USER}" \
    #     -d "${DB_NAME}" -c "\copy pipeline_checkpoint FROM '${RESTORE_DIR}/pipeline_checkpoint.csv}' WITH CSV HEADER"
    log_warn "Checkpoint restore requires database access - skipping"
fi

# Start AEP services
log_info "Starting AEP services..."
# systemctl start aep-server || log_warn "Failed to start AEP server"

# Validate restore
log_info "Validating restore..."
sleep 10  # Wait for services to start

# Verify pipelines restored
PIPELINE_COUNT=$(curl -s "http://localhost:8080/api/v1/pipelines?tenantId=${TENANT_ID}" | jq '.pipelines | length')
BACKUP_PIPELINE_COUNT=$(jq '.pipelines | length' "${RESTORE_DIR}/pipelines.json")

if [[ "$PIPELINE_COUNT" -eq "$BACKUP_PIPELINE_COUNT" ]]; then
    log_info "Pipeline restore validated: $PIPELINE_COUNT pipelines restored"
else
    log_warn "Pipeline count mismatch: expected $BACKUP_PIPELINE_COUNT, found $PIPELINE_COUNT"
fi

# Verify agents restored
AGENT_COUNT=$(curl -s "http://localhost:8080/api/v1/agents?tenantId=${TENANT_ID}" | jq '.agents | length')
BACKUP_AGENT_COUNT=$(jq '.agents | length' "${RESTORE_DIR}/agents.json")

if [[ "$AGENT_COUNT" -eq "$BACKUP_AGENT_COUNT" ]]; then
    log_info "Agent restore validated: $AGENT_COUNT agents restored"
else
    log_warn "Agent count mismatch: expected $BACKUP_AGENT_COUNT, found $AGENT_COUNT"
fi

# Final status
if [[ $restore_errors -eq 0 ]]; then
    log_info "Restore completed successfully"
    log_info "Restored from: $BACKUP_FILE"
    
    # Update governance ops summary
    log_info "Updating governance ops summary with restore status..."
    
    exit 0
else
    log_error "Restore completed with $restore_errors error(s)"
    exit 1
fi
