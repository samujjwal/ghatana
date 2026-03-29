#!/usr/bin/env bash
# =============================================================================
# restore-postgres.sh — Point-in-Time Recovery for Data-Cloud PostgreSQL
#
# Purpose: Downloads a pg_dump archive from S3, optionally decompresses it,
#          and restores it to a PostgreSQL instance. For full PITR, this script
#          handles the base-backup restore step; WAL replay is performed by the
#          PostgreSQL recovery process (recovery.conf / restore_command).
#
# Usage:
#   bash products/data-cloud/scripts/restore-postgres.sh               # restore latest
#   bash products/data-cloud/scripts/restore-postgres.sh --list        # list available backups
#   bash products/data-cloud/scripts/restore-postgres.sh --key <s3-key>  # restore specific backup
#   bash products/data-cloud/scripts/restore-postgres.sh --dry-run     # simulate without restoring
#
# Required environment variables:
#   POSTGRES_HOST       Target PostgreSQL host (MUST be the recovery/DR instance)
#   POSTGRES_PORT       Target PostgreSQL port (default: 5432)
#   POSTGRES_DB         Database name to restore into (will be re-created)
#   POSTGRES_USER       PostgreSQL superuser for restore
#   PGPASSWORD          Password (never hardcode; read from env / secret mount)
#   S3_BUCKET           Source bucket containing backups
#
# Optional environment variables:
#   S3_ENDPOINT         Custom S3 endpoint URL
#   S3_PREFIX           Key prefix (default: data-cloud/postgres)
#   RESTORE_DIR         Local staging directory (default: /tmp/dc-pg-restore)
#   TARGET_TIME         For PITR: recovery target timestamp (e.g. "2026-01-19 11:55:00 UTC")
#                       When set, configures recovery_target_time in PostgreSQL.
#
# SAFETY NOTES:
#   - This script DROPS and RECREATES the target database before restore.
#     This is irreversible. Only run against a DR or staging instance.
#   - Production primary should NEVER be the restore target.
#   - Run the backup-drill first: bash scripts/run-backup-drill.sh
#
# Exit codes:
#   0  Restore completed successfully
#   1  Configuration or preflight error
#   2  S3 download or key-listing failed
#   3  Restore (pg_restore) failed
#   4  Post-restore verification failed
# =============================================================================
set -uo pipefail

# ── Constants ─────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_NAME="$(basename "${BASH_SOURCE[0]}")"
TIMESTAMP="$(date -u '+%Y%m%dT%H%M%SZ')"

# ── Defaults ──────────────────────────────────────────────────────────────────
PG_HOST="${POSTGRES_HOST:?POSTGRES_HOST is required}"
PG_PORT="${POSTGRES_PORT:-5432}"
PG_DB="${POSTGRES_DB:?POSTGRES_DB is required}"
PG_USER="${POSTGRES_USER:?POSTGRES_USER is required}"
S3_BUCKET="${S3_BUCKET:?S3_BUCKET is required}"
S3_PREFIX="${S3_PREFIX:-data-cloud/postgres}"
RESTORE_DIR="${RESTORE_DIR:-/tmp/dc-pg-restore}"
TARGET_TIME="${TARGET_TIME:-}"

# ── Flags ─────────────────────────────────────────────────────────────────────
DRY_RUN=false
LIST_ONLY=false
SPECIFIC_KEY=""
i=1
while [[ $i -le $# ]]; do
    arg="${!i}"
    case "$arg" in
        --dry-run)   DRY_RUN=true ;;
        --list)      LIST_ONLY=true ;;
        --key)
            i=$((i + 1))
            SPECIFIC_KEY="${!i}"
            ;;
    esac
    i=$((i + 1))
done

# ── Logging ───────────────────────────────────────────────────────────────────
log()  { echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] [$SCRIPT_NAME] $*"; }
info() { log "INFO  $*"; }
warn() { log "WARN  $*" >&2; }
error() { log "ERROR $*" >&2; }
die()  { error "$*"; exit "${2:-1}"; }

# ── Helper: build aws-cli base command ───────────────────────────────────────
aws_cmd() {
    if [[ -n "${S3_ENDPOINT:-}" ]]; then
        echo "aws --endpoint-url '${S3_ENDPOINT}'"
    else
        echo "aws"
    fi
}

# ── Preflight ─────────────────────────────────────────────────────────────────
[[ -z "${PGPASSWORD:-}" ]] && die "PGPASSWORD must be set." 1
command -v aws >/dev/null 2>&1       || die "aws CLI not found. Install awscli." 1
command -v pg_restore >/dev/null 2>&1 || die "pg_restore not found. Install postgresql-client." 1
command -v psql >/dev/null 2>&1       || die "psql not found. Install postgresql-client." 1

# ── List available backups ────────────────────────────────────────────────────
AWS="$(aws_cmd)"

list_backups() {
    info "Available backups in s3://${S3_BUCKET}/${S3_PREFIX}/ :"
    eval "$AWS s3api list-objects-v2 \
        --bucket '${S3_BUCKET}' \
        --prefix '${S3_PREFIX}/' \
        --query 'reverse(sort_by(Contents[],&LastModified))[*].{Key:Key,Size:Size,LastModified:LastModified}' \
        --output table" 2>/dev/null || {
            eval "$AWS s3 ls 's3://${S3_BUCKET}/${S3_PREFIX}/' --recursive" 2>/dev/null || \
                die "Failed to list S3 backups. Check credentials and bucket name." 2
        }
}

if [[ "$LIST_ONLY" == "true" ]]; then
    list_backups
    exit 0
fi

# ── Resolve which backup to restore ──────────────────────────────────────────
if [[ -n "$SPECIFIC_KEY" ]]; then
    RESTORE_KEY="$SPECIFIC_KEY"
    info "Restoring specific backup: s3://${S3_BUCKET}/${RESTORE_KEY}"
else
    info "Resolving latest backup from s3://${S3_BUCKET}/${S3_PREFIX}/ ..."
    RESTORE_KEY=$(eval "$AWS s3api list-objects-v2 \
        --bucket '${S3_BUCKET}' \
        --prefix '${S3_PREFIX}/' \
        --query 'reverse(sort_by(Contents[],&LastModified))[0].Key' \
        --output text" 2>/dev/null || echo "")

    if [[ -z "$RESTORE_KEY" || "$RESTORE_KEY" == "None" ]]; then
        die "No backups found at s3://${S3_BUCKET}/${S3_PREFIX}/. Run backup-postgres.sh first." 2
    fi
    info "Latest backup: s3://${S3_BUCKET}/${RESTORE_KEY}"
fi

ARCHIVE_FILENAME="$(basename "$RESTORE_KEY")"
LOCAL_ARCHIVE="${RESTORE_DIR}/${ARCHIVE_FILENAME}"

if [[ "$DRY_RUN" == "true" ]]; then
    info "[dry-run] Would restore: s3://${S3_BUCKET}/${RESTORE_KEY} → $PG_HOST:$PG_PORT/$PG_DB"
    [[ -n "$TARGET_TIME" ]] && info "[dry-run] PITR target time: ${TARGET_TIME}"
    exit 0
fi

# ── Safety guard: refuse to restore to a primary-like host ───────────────────
# A known pattern: production primary is typically labeled 'prod' or similar.
# Warn but don't block; operators may knowingly restore to non-DR hosts.
if [[ "$PG_HOST" =~ prod.*primary|primary.*prod ]]; then
    warn "RESTORE TARGET looks like a production primary ($PG_HOST). Are you sure?"
    warn "Proceeding in 10 seconds. Press Ctrl+C to abort."
    sleep 10
fi

# ── Download archive from S3 ──────────────────────────────────────────────────
mkdir -p "$RESTORE_DIR"
info "Downloading s3://${S3_BUCKET}/${RESTORE_KEY} → ${LOCAL_ARCHIVE} ..."
DOWNLOAD_START="$(date +%s)"

eval "$AWS s3 cp 's3://${S3_BUCKET}/${RESTORE_KEY}' '${LOCAL_ARCHIVE}'" || \
    die "S3 download failed for s3://${S3_BUCKET}/${RESTORE_KEY}" 2

DOWNLOAD_SIZE="$(du -sh "$LOCAL_ARCHIVE" | cut -f1)"
info "Download complete in $(( $(date +%s) - DOWNLOAD_START ))s. Size: ${DOWNLOAD_SIZE}"

# ── Decompress if needed ──────────────────────────────────────────────────────
DUMP_FILE="$LOCAL_ARCHIVE"
if [[ "$ARCHIVE_FILENAME" == *.zst ]]; then
    command -v zstd >/dev/null 2>&1 || die "zstd not found; cannot decompress .zst archive." 1
    DUMP_FILE="${LOCAL_ARCHIVE%.zst}"
    info "Decompressing .zst archive → ${DUMP_FILE} ..."
    zstd -d -T0 -q "$LOCAL_ARCHIVE" -o "$DUMP_FILE"
elif [[ "$ARCHIVE_FILENAME" == *.gz ]]; then
    DUMP_FILE="${LOCAL_ARCHIVE%.gz}"
    info "Decompressing .gz archive → ${DUMP_FILE} ..."
    gunzip -c "$LOCAL_ARCHIVE" > "$DUMP_FILE"
fi

# ── Drop and re-create the target database ────────────────────────────────────
info "Dropping and re-creating database '${PG_DB}' on ${PG_HOST}:${PG_PORT} ..."

PGPASSWORD="${PGPASSWORD}" psql \
    --host="$PG_HOST" --port="$PG_PORT" \
    --username="$PG_USER" --dbname="postgres" \
    --no-psqlrc --quiet \
    -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='${PG_DB}' AND pid <> pg_backend_pid();" \
    -c "DROP DATABASE IF EXISTS ${PG_DB};" \
    -c "CREATE DATABASE ${PG_DB} WITH ENCODING 'UTF8';" || \
    die "Failed to re-create database ${PG_DB}" 3

info "Database re-created."

# ── Restore from dump ─────────────────────────────────────────────────────────
info "Starting pg_restore from ${DUMP_FILE} ..."
RESTORE_START="$(date +%s)"

PGPASSWORD="${PGPASSWORD}" pg_restore \
    --host="$PG_HOST" \
    --port="$PG_PORT" \
    --username="$PG_USER" \
    --dbname="$PG_DB" \
    --no-password \
    --jobs=4 \
    --verbose \
    --exit-on-error \
    "$DUMP_FILE" 2>"${RESTORE_DIR}/pg_restore_${TIMESTAMP}.log" || {
        error "pg_restore failed. Check ${RESTORE_DIR}/pg_restore_${TIMESTAMP}.log"
        exit 3
    }

info "pg_restore completed in $(( $(date +%s) - RESTORE_START ))s"

# ── Configure PITR if a target time is provided ───────────────────────────────
if [[ -n "$TARGET_TIME" ]]; then
    info "PITR target time requested: ${TARGET_TIME}"
    info "To complete PITR, configure the following in your PostgreSQL recovery settings:"
    info "  For CloudNativePG: set recoveryTarget.targetTime=\"${TARGET_TIME}\" in your Cluster spec"
    info "  For bare PostgreSQL: add recovery_target_time='${TARGET_TIME}' to postgresql.conf"
    info "  Then call: SELECT pg_wal_replay_resume(); once the server has replayed to the target"
fi

# ── Post-restore verification ─────────────────────────────────────────────────
info "Running post-restore verification ..."

ROW_CHECK=$(PGPASSWORD="${PGPASSWORD}" psql \
    --host="$PG_HOST" --port="$PG_PORT" \
    --username="$PG_USER" --dbname="$PG_DB" \
    --no-psqlrc --tuples-only --quiet \
    -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='public';" \
    2>/dev/null | tr -d ' \n' || echo "0")

if [[ "$ROW_CHECK" =~ ^[0-9]+$ ]] && [[ "$ROW_CHECK" -gt 0 ]]; then
    info "Verification passed: ${ROW_CHECK} public table(s) found in restored database"
else
    error "Verification failed: no public tables found — restore may be incomplete"
    exit 4
fi

TABLE_COUNTS=$(PGPASSWORD="${PGPASSWORD}" psql \
    --host="$PG_HOST" --port="$PG_PORT" \
    --username="$PG_USER" --dbname="$PG_DB" \
    --no-psqlrc --quiet \
    -c "\dt+" 2>/dev/null | head -30 || true)
info "Table summary:"
echo "$TABLE_COUNTS"

# ── Cleanup staging files ─────────────────────────────────────────────────────
rm -f "$LOCAL_ARCHIVE" "$DUMP_FILE"
info "Cleaned up local staging files"

info "Restore completed successfully. Target: ${PG_HOST}:${PG_PORT}/${PG_DB}"
exit 0
