#!/usr/bin/env bash
# =============================================================================
# backup-postgres.sh — Automated PostgreSQL backup for Data-Cloud
#
# Purpose: Produces a compressed pg_dump of the Data-Cloud PostgreSQL database,
#          uploads the archive to an S3-compatible store, and prunes archives
#          older than the configured retention window.
#
# Usage:
#   bash products/data-cloud/scripts/backup-postgres.sh
#   bash products/data-cloud/scripts/backup-postgres.sh --dry-run
#   bash products/data-cloud/scripts/backup-postgres.sh --no-upload
#
# Required environment variables:
#   POSTGRES_HOST          PostgreSQL host
#   POSTGRES_PORT          PostgreSQL port (default: 5432)
#   POSTGRES_DB            Database name
#   POSTGRES_USER          PostgreSQL user
#   PGPASSWORD             Password (never hardcode; read from env / secret mount)
#   S3_BUCKET              Destination bucket (e.g. ghatana-pg-backups)
#
# Optional environment variables:
#   S3_ENDPOINT            Custom S3 endpoint URL (for Ceph / MinIO)
#   S3_PREFIX              Key prefix inside bucket (default: data-cloud/postgres)
#   BACKUP_RETENTION_DAYS  Days to keep backups (default: 14)
#   BACKUP_COMPRESS        Compression: gzip|zstd (default: gzip; zstd if available)
#   BACKUP_FORMAT          pg_dump format: custom|plain (default: custom)
#   BACKUP_PARALLEL_JOBS   Parallel dump jobs (default: 4; only for directory format)
#   BACKUP_DIR             Local staging directory (default: /tmp/dc-pg-backups)
#   NOTIFY_WEBHOOK_URL     Optional HTTP POST webhook on completion (Slack, etc.)
#
# Exit codes:
#   0  Backup completed and uploaded successfully
#   1  Configuration error
#   2  pg_dump failed
#   3  S3 upload failed
#   4  Retention pruning failed (non-fatal: exits 0 after a warning)
#
# Notes:
#   - PGPASSWORD is the only supported auth method here; for certificate auth,
#     set PGSSLCERT / PGSSLKEY / PGSSLROOTCERT.
#   - For WAL archiving (PITR to minute-level), configure wal_archive_command on
#     the PostgreSQL primary; this script covers base backups only.
# =============================================================================
set -uo pipefail

# ── Constants ─────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TIMESTAMP="$(date -u '+%Y%m%dT%H%M%SZ')"
SCRIPT_NAME="$(basename "${BASH_SOURCE[0]}")"

# ── Defaults ──────────────────────────────────────────────────────────────────
PG_HOST="${POSTGRES_HOST:?POSTGRES_HOST is required}"
PG_PORT="${POSTGRES_PORT:-5432}"
PG_DB="${POSTGRES_DB:?POSTGRES_DB is required}"
PG_USER="${POSTGRES_USER:?POSTGRES_USER is required}"
S3_BUCKET="${S3_BUCKET:?S3_BUCKET is required}"
S3_PREFIX="${S3_PREFIX:-data-cloud/postgres}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
BACKUP_FORMAT="${BACKUP_FORMAT:-custom}"
PARALLEL_JOBS="${BACKUP_PARALLEL_JOBS:-4}"
BACKUP_DIR="${BACKUP_DIR:-/tmp/dc-pg-backups}"
NOTIFY_WEBHOOK_URL="${NOTIFY_WEBHOOK_URL:-}"

# Determine compression
if [[ "${BACKUP_COMPRESS:-}" == "zstd" ]] && command -v zstd >/dev/null 2>&1; then
    COMPRESS_CMD="zstd -T0 -q"
    COMPRESS_EXT="zst"
elif [[ "${BACKUP_COMPRESS:-}" == "zstd" ]]; then
    echo "[$SCRIPT_NAME] WARN: zstd requested but not found; falling back to gzip" >&2
    COMPRESS_CMD="gzip -c"
    COMPRESS_EXT="gz"
else
    COMPRESS_CMD="gzip -c"
    COMPRESS_EXT="gz"
fi

# ── Flags ─────────────────────────────────────────────────────────────────────
DRY_RUN=false
NO_UPLOAD=false
for arg in "$@"; do
    [[ "$arg" == "--dry-run" ]]  && DRY_RUN=true
    [[ "$arg" == "--no-upload" ]] && NO_UPLOAD=true
done

# ── Logging ───────────────────────────────────────────────────────────────────
log()  { echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] [$SCRIPT_NAME] $*"; }
info() { log "INFO  $*"; }
warn() { log "WARN  $*" >&2; }
error() { log "ERROR $*" >&2; }
die()  { error "$*"; exit "${2:-1}"; }

# ── Helper: notify webhook ────────────────────────────────────────────────────
notify() {
    local status="$1" message="$2"
    if [[ -n "$NOTIFY_WEBHOOK_URL" ]]; then
        local payload
        payload="$(printf '{"text":"[data-cloud backup] %s — %s"}' "$status" "$message")"
        curl -sf -X POST -H "Content-Type: application/json" \
            -d "$payload" "$NOTIFY_WEBHOOK_URL" >/dev/null 2>&1 || true
    fi
}

# ── Helper: build aws-cli base command with optional custom endpoint ──────────
aws_cmd() {
    if [[ -n "${S3_ENDPOINT:-}" ]]; then
        echo "aws --endpoint-url '${S3_ENDPOINT}'"
    else
        echo "aws"
    fi
}

# ── Preflight checks ──────────────────────────────────────────────────────────
info "Starting backup: host=$PG_HOST:$PG_PORT db=$PG_DB user=$PG_USER format=$BACKUP_FORMAT"

if [[ "$DRY_RUN" == "true" ]]; then
    info "DRY RUN mode — no backup will be written or uploaded"
fi

command -v pg_dump >/dev/null 2>&1 || die "pg_dump not found in PATH. Install postgresql-client." 1
if [[ "$NO_UPLOAD" == "false" ]]; then
    command -v aws >/dev/null 2>&1 || die "aws CLI not found in PATH. Install awscli." 1
fi

# Check that PGPASSWORD is set (do NOT print it)
if [[ -z "${PGPASSWORD:-}" ]]; then
    die "PGPASSWORD must be set (never hardcode credentials in scripts)." 1
fi

# ── Prepare staging directory ─────────────────────────────────────────────────
mkdir -p "$BACKUP_DIR"

BACKUP_FILENAME="${PG_DB}_${TIMESTAMP}.dump.${COMPRESS_EXT}"
LOCAL_BACKUP_PATH="${BACKUP_DIR}/${BACKUP_FILENAME}"
S3_KEY="${S3_PREFIX}/${BACKUP_FILENAME}"

info "Backup target: local=${LOCAL_BACKUP_PATH} s3=s3://${S3_BUCKET}/${S3_KEY}"

# ── Perform pg_dump ───────────────────────────────────────────────────────────
if [[ "$DRY_RUN" == "false" ]]; then
    info "Running pg_dump ..."
    DUMP_START="$(date +%s)"

    # pg_dump → compress → local file
    # Using process substitution to avoid intermediate uncompressed file.
    if ! pg_dump \
            --host="$PG_HOST" \
            --port="$PG_PORT" \
            --username="$PG_USER" \
            --dbname="$PG_DB" \
            --format="$BACKUP_FORMAT" \
            --no-password \
            --verbose \
            2>"${BACKUP_DIR}/${PG_DB}_${TIMESTAMP}.dump.log" \
        | $COMPRESS_CMD > "$LOCAL_BACKUP_PATH"; then
        error "pg_dump failed. Check ${BACKUP_DIR}/${PG_DB}_${TIMESTAMP}.dump.log"
        notify "FAILED" "pg_dump failed at ${TIMESTAMP}"
        exit 2
    fi

    DUMP_END="$(date +%s)"
    DUMP_ELAPSED=$((DUMP_END - DUMP_START))
    BACKUP_SIZE="$(du -sh "$LOCAL_BACKUP_PATH" | cut -f1)"
    info "pg_dump completed in ${DUMP_ELAPSED}s. Compressed size: ${BACKUP_SIZE}"
else
    info "[dry-run] Would run: pg_dump --host=$PG_HOST --port=$PG_PORT --username=$PG_USER --dbname=$PG_DB"
    exit 0
fi

# ── Upload to S3 ──────────────────────────────────────────────────────────────
if [[ "$NO_UPLOAD" == "false" ]]; then
    info "Uploading to s3://${S3_BUCKET}/${S3_KEY} ..."
    UPLOAD_START="$(date +%s)"

    AWS="$(aws_cmd)"
    # Metadata tags for retention auditing
    TAGS="Key=product,Value=data-cloud&Key=backup-type,Value=pg_dump&Key=created-at,Value=${TIMESTAMP}&Key=db,Value=${PG_DB}"

    if ! eval "$AWS s3 cp '${LOCAL_BACKUP_PATH}' 's3://${S3_BUCKET}/${S3_KEY}' \
        --storage-class STANDARD_IA \
        --metadata 'backup-db=${PG_DB},backup-ts=${TIMESTAMP}'" >/dev/null; then
        error "S3 upload failed for s3://${S3_BUCKET}/${S3_KEY}"
        notify "FAILED" "S3 upload failed for ${BACKUP_FILENAME}"
        exit 3
    fi

    UPLOAD_END="$(date +%s)"
    info "Upload completed in $((UPLOAD_END - UPLOAD_START))s"

    # ── S3 tag for lifecycle management ──────────────────────────────────────
    eval "$AWS s3api put-object-tagging \
        --bucket '${S3_BUCKET}' \
        --key '${S3_KEY}' \
        --tagging 'TagSet=[{Key=retention-days,Value=${RETENTION_DAYS}},{Key=product,Value=data-cloud}]'" \
        >/dev/null 2>&1 || warn "Could not tag S3 object — lifecycle tagging skipped"

    # ── Remove local staging file ─────────────────────────────────────────────
    rm -f "$LOCAL_BACKUP_PATH" && info "Removed local staging file"
fi

# ── Prune old S3 objects beyond retention window ──────────────────────────────
if [[ "$NO_UPLOAD" == "false" ]]; then
    info "Pruning backups older than ${RETENTION_DAYS} days from s3://${S3_BUCKET}/${S3_PREFIX}/ ..."
    CUTOFF_EPOCH=$(( $(date +%s) - RETENTION_DAYS * 86400 ))

    AWS="$(aws_cmd)"
    OLD_KEYS=$(eval "$AWS s3api list-objects-v2 \
        --bucket '${S3_BUCKET}' \
        --prefix '${S3_PREFIX}/' \
        --query \"Contents[?LastModified<='$(date -u -d @${CUTOFF_EPOCH} '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || date -u -r "${CUTOFF_EPOCH}" '+%Y-%m-%dT%H:%M:%SZ')'].Key\" \
        --output text" 2>/dev/null || echo "")

    if [[ -n "$OLD_KEYS" ]]; then
        PRUNED_COUNT=0
        while IFS= read -r key; do
            [[ -z "$key" ]] && continue
            if eval "$AWS s3 rm 's3://${S3_BUCKET}/${key}'" >/dev/null 2>&1; then
                info "Pruned: s3://${S3_BUCKET}/${key}"
                PRUNED_COUNT=$((PRUNED_COUNT + 1))
            else
                warn "Failed to prune s3://${S3_BUCKET}/${key}"
            fi
        done <<< "$OLD_KEYS"
        info "Pruning complete: removed ${PRUNED_COUNT} old backup(s)"
    else
        info "No backups older than ${RETENTION_DAYS} days found"
    fi
fi

# ── Final status ──────────────────────────────────────────────────────────────
TOTAL_END="$(date +%s)"
info "Backup completed successfully in $((TOTAL_END - DUMP_START))s total"
notify "SUCCESS" "Backup ${BACKUP_FILENAME} completed (${BACKUP_SIZE:-unknown})"
exit 0
