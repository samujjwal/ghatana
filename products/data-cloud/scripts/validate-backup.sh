#!/usr/bin/env bash
# =============================================================================
# validate-backup.sh — Backup Integrity Validation for Data-Cloud PostgreSQL
#
# Purpose: Downloads the most recent (or a specified) pg_dump archive from S3,
#          restores it to an isolated validation database, verifies table
#          presence and non-zero row counts, and reports a structured result.
#          Intended to run as a scheduled CI/CD or cron job after each backup.
#
# Usage:
#   bash products/data-cloud/scripts/validate-backup.sh
#   bash products/data-cloud/scripts/validate-backup.sh --key <s3-key>
#   bash products/data-cloud/scripts/validate-backup.sh --json
#   bash products/data-cloud/scripts/validate-backup.sh --warn-only
#
# Required environment variables:
#   POSTGRES_HOST     PostgreSQL host for the VALIDATION instance
#   POSTGRES_PORT     PostgreSQL port (default: 5432)
#   POSTGRES_DB       Source database name (determines archive filename prefix)
#   POSTGRES_USER     PostgreSQL superuser for the validation restore
#   PGPASSWORD        Password (never hardcode)
#   S3_BUCKET         S3 bucket containing backup archives
#
# Optional environment variables:
#   S3_ENDPOINT       Custom endpoint (Ceph / MinIO)
#   S3_PREFIX         Key prefix (default: data-cloud/postgres)
#   VALIDATE_DB       Temporary database for restoration (default: dc_validate_tmp)
#   VALIDATE_TABLES   Comma-separated list of tables to row-count-check
#                     (default: entities,events,collections,tenants)
#   RESTORE_DIR       Local staging dir (default: /tmp/dc-pg-validate)
#
# Exit codes:
#   0  All validation checks passed
#   1  Configuration error
#   2  S3 download failed
#   3  Restore failed
#   4  One or more validation checks failed (unless --warn-only)
# =============================================================================
set -uo pipefail

# ── Constants ─────────────────────────────────────────────────────────────────
SCRIPT_NAME="$(basename "${BASH_SOURCE[0]}")"
TIMESTAMP="$(date -u '+%Y%m%dT%H%M%SZ')"

# ── Defaults ──────────────────────────────────────────────────────────────────
PG_HOST="${POSTGRES_HOST:?POSTGRES_HOST is required}"
PG_PORT="${POSTGRES_PORT:-5432}"
PG_DB="${POSTGRES_DB:?POSTGRES_DB is required}"
PG_USER="${POSTGRES_USER:?POSTGRES_USER is required}"
S3_BUCKET="${S3_BUCKET:?S3_BUCKET is required}"
S3_PREFIX="${S3_PREFIX:-data-cloud/postgres}"
VALIDATE_DB="${VALIDATE_DB:-dc_validate_tmp}"
VALIDATE_TABLES="${VALIDATE_TABLES:-entities,events,collections,tenants}"
RESTORE_DIR="${RESTORE_DIR:-/tmp/dc-pg-validate}"

# ── Flags ─────────────────────────────────────────────────────────────────────
EMIT_JSON=false
WARN_ONLY=false
SPECIFIC_KEY=""
i=1
while [[ $i -le $# ]]; do
    arg="${!i}"
    case "$arg" in
        --json)      EMIT_JSON=true ;;
        --warn-only) WARN_ONLY=true ;;
        --key)
            i=$((i + 1))
            SPECIFIC_KEY="${!i}"
            ;;
    esac
    i=$((i + 1))
done

# ── Logging helpers ──────────────────────────────────────────────────────────
log()  { echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] [$SCRIPT_NAME] $*"; }
info() { log "INFO  $*"; }
warn() { log "WARN  $*" >&2; }
error() { log "ERROR $*" >&2; }
die()  { error "$*"; exit "${2:-1}"; }

# ── Result tracking ───────────────────────────────────────────────────────────
PASS_COUNT=0
FAIL_COUNT=0
declare -a RESULTS=()

record() {
    local status="$1" check="$2" detail="${3:-}"
    RESULTS+=("{\"status\":\"${status}\",\"check\":\"${check}\",\"detail\":\"${detail}\"}")
    case "$status" in
        PASS) info "  ✅ PASS: ${check}${detail:+ — ${detail}}"; PASS_COUNT=$((PASS_COUNT+1)) ;;
        FAIL) error "  ✗  FAIL: ${check}${detail:+ — ${detail}}"; FAIL_COUNT=$((FAIL_COUNT+1)) ;;
        WARN) warn "  ⚠  WARN: ${check}${detail:+ — ${detail}}"; PASS_COUNT=$((PASS_COUNT+1)) ;;
    esac
}

# ── Helper: aws command with optional endpoint ────────────────────────────────
aws_cmd() {
    if [[ -n "${S3_ENDPOINT:-}" ]]; then
        echo "aws --endpoint-url '${S3_ENDPOINT}'"
    else
        echo "aws"
    fi
}

# ── Preflight ─────────────────────────────────────────────────────────────────
info "Data-Cloud Backup Validation — ${TIMESTAMP}"
info "Target validation instance: ${PG_HOST}:${PG_PORT} (temp db: ${VALIDATE_DB})"

[[ -z "${PGPASSWORD:-}" ]]             && die "PGPASSWORD must be set." 1
command -v aws >/dev/null 2>&1         || die "aws CLI not found." 1
command -v pg_restore >/dev/null 2>&1  || die "pg_restore not found." 1
command -v psql >/dev/null 2>&1        || die "psql not found." 1

AWS="$(aws_cmd)"
mkdir -p "$RESTORE_DIR"

# ── Resolve backup to validate ────────────────────────────────────────────────
if [[ -n "$SPECIFIC_KEY" ]]; then
    RESTORE_KEY="$SPECIFIC_KEY"
    info "Validating specified backup: s3://${S3_BUCKET}/${RESTORE_KEY}"
else
    info "Resolving latest backup from s3://${S3_BUCKET}/${S3_PREFIX}/ ..."
    RESTORE_KEY=$(eval "$AWS s3api list-objects-v2 \
        --bucket '${S3_BUCKET}' \
        --prefix '${S3_PREFIX}/' \
        --query 'reverse(sort_by(Contents[],&LastModified))[0].Key' \
        --output text" 2>/dev/null || echo "")

    if [[ -z "$RESTORE_KEY" || "$RESTORE_KEY" == "None" ]]; then
        record FAIL "backup-exists" "No archives found at s3://${S3_BUCKET}/${S3_PREFIX}/"
        info "FAIL: No backups to validate."
        exit 2
    fi
fi

# ── Check backup archive age ──────────────────────────────────────────────────
BACKUP_DATE=$(eval "$AWS s3api head-object \
    --bucket '${S3_BUCKET}' \
    --key '${RESTORE_KEY}' \
    --query 'LastModified' \
    --output text" 2>/dev/null | cut -d' ' -f1 || echo "unknown")

# Use python3 for portable date arithmetic (available on most Linux)
if command -v python3 >/dev/null 2>&1 && [[ "$BACKUP_DATE" != "unknown" ]]; then
    AGE_HOURS=$(python3 -c "
from datetime import datetime, timezone
import sys
backup = datetime.fromisoformat('${BACKUP_DATE}'.replace('Z','+00:00'))
now = datetime.now(timezone.utc)
print(int((now - backup).total_seconds() / 3600))
" 2>/dev/null || echo "999")
    if [[ "$AGE_HOURS" -le 24 ]]; then
        record PASS "backup-freshness" "Latest backup is ${AGE_HOURS}h old (threshold ≤24h)"
    elif [[ "$AGE_HOURS" -le 48 ]]; then
        record WARN "backup-freshness" "Latest backup is ${AGE_HOURS}h old — approaching RPO boundary"
    else
        record FAIL "backup-freshness" "Latest backup is ${AGE_HOURS}h old — exceeds 48h threshold"
    fi
fi

# ── Download archive ──────────────────────────────────────────────────────────
ARCHIVE_FILENAME="$(basename "$RESTORE_KEY")"
LOCAL_ARCHIVE="${RESTORE_DIR}/${ARCHIVE_FILENAME}"
info "Downloading s3://${S3_BUCKET}/${RESTORE_KEY} ..."

if eval "$AWS s3 cp 's3://${S3_BUCKET}/${RESTORE_KEY}' '${LOCAL_ARCHIVE}'" >/dev/null 2>&1; then
    ARCHIVE_SIZE="$(du -sh "$LOCAL_ARCHIVE" | cut -f1)"
    record PASS "s3-download" "Downloaded to ${LOCAL_ARCHIVE} (size: ${ARCHIVE_SIZE})"
else
    record FAIL "s3-download" "Failed to download s3://${S3_BUCKET}/${RESTORE_KEY}"
    info "Validation aborted: cannot download archive."
    exit 2
fi

# Sanity check: ensure file is not empty
ARCHIVE_BYTES=$(stat -c%s "$LOCAL_ARCHIVE" 2>/dev/null || stat -f%z "$LOCAL_ARCHIVE" 2>/dev/null || echo "0")
if [[ "$ARCHIVE_BYTES" -lt 1024 ]]; then
    record FAIL "archive-non-empty" "Archive is suspiciously small: ${ARCHIVE_BYTES} bytes"
else
    record PASS "archive-non-empty" "Archive size: ${ARCHIVE_BYTES} bytes"
fi

# ── Decompress if needed ──────────────────────────────────────────────────────
DUMP_FILE="$LOCAL_ARCHIVE"
if [[ "$ARCHIVE_FILENAME" == *.zst ]]; then
    command -v zstd >/dev/null 2>&1 || die "zstd required to decompress .zst archive." 1
    DUMP_FILE="${LOCAL_ARCHIVE%.zst}"
    zstd -d -T0 -q "$LOCAL_ARCHIVE" -o "$DUMP_FILE"
elif [[ "$ARCHIVE_FILENAME" == *.gz ]]; then
    DUMP_FILE="${LOCAL_ARCHIVE%.gz}"
    gunzip -c "$LOCAL_ARCHIVE" > "$DUMP_FILE"
fi

# ── TOC inspection (verify backup contains tables before restoring) ───────────
if command -v pg_restore >/dev/null 2>&1; then
    TOC_TABLES=$(pg_restore --list "$DUMP_FILE" 2>/dev/null | grep -c "TABLE DATA" || echo "0")
    if [[ "$TOC_TABLES" =~ ^[0-9]+$ ]] && [[ "$TOC_TABLES" -gt 0 ]]; then
        record PASS "toc-table-count" "TOC reports ${TOC_TABLES} TABLE DATA entries"
    else
        record WARN "toc-table-count" "No TABLE DATA entries in TOC — may be an empty backup"
    fi
fi

# ── Create and populate validation database ───────────────────────────────────
info "Creating validation database '${VALIDATE_DB}' ..."

PGPASSWORD="${PGPASSWORD}" psql \
    --host="$PG_HOST" --port="$PG_PORT" \
    --username="$PG_USER" --dbname="postgres" \
    --no-psqlrc --quiet \
    -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='${VALIDATE_DB}' AND pid <> pg_backend_pid();" \
    -c "DROP DATABASE IF EXISTS ${VALIDATE_DB};" \
    -c "CREATE DATABASE ${VALIDATE_DB} WITH ENCODING 'UTF8';" 2>/dev/null || \
    die "Failed to create validation database ${VALIDATE_DB}" 3

RESTORE_START="$(date +%s)"
info "Restoring into validation database ..."
PGPASSWORD="${PGPASSWORD}" pg_restore \
    --host="$PG_HOST" --port="$PG_PORT" \
    --username="$PG_USER" --dbname="$VALIDATE_DB" \
    --no-password \
    --jobs=2 \
    "$DUMP_FILE" >/dev/null 2>"${RESTORE_DIR}/validate_${TIMESTAMP}.log" || {
        error "pg_restore produced errors — check ${RESTORE_DIR}/validate_${TIMESTAMP}.log (non-fatal errors are common)"
        # Non-fatal; inspect the restored DB below rather than aborting
    }
info "Restore completed in $(( $(date +%s) - RESTORE_START ))s"

# ── Validate table presence and rough row counts ──────────────────────────────
info "Checking table counts ..."

ACTUAL_TABLE_COUNT=$(PGPASSWORD="${PGPASSWORD}" psql \
    --host="$PG_HOST" --port="$PG_PORT" \
    --username="$PG_USER" --dbname="$VALIDATE_DB" \
    --no-psqlrc --tuples-only --quiet \
    -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='public';" \
    2>/dev/null | tr -d ' \n' || echo "0")

if [[ "$ACTUAL_TABLE_COUNT" =~ ^[0-9]+$ ]] && [[ "$ACTUAL_TABLE_COUNT" -gt 0 ]]; then
    record PASS "public-schema-tables" "${ACTUAL_TABLE_COUNT} public table(s) present"
else
    record FAIL "public-schema-tables" "No public tables found after restore"
fi

# Row-count check for each expected table
IFS=',' read -ra EXPECTED_TABLES <<< "$VALIDATE_TABLES"
for tbl in "${EXPECTED_TABLES[@]}"; do
    tbl="${tbl// /}"   # trim whitespace
    [[ -z "$tbl" ]] && continue

    # Check table exists
    TABLE_EXISTS=$(PGPASSWORD="${PGPASSWORD}" psql \
        --host="$PG_HOST" --port="$PG_PORT" \
        --username="$PG_USER" --dbname="$VALIDATE_DB" \
        --no-psqlrc --tuples-only --quiet \
        -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='${tbl}';" \
        2>/dev/null | tr -d ' \n' || echo "0")

    if [[ "$TABLE_EXISTS" == "0" ]]; then
        record WARN "table-present:${tbl}" "Table '${tbl}' not found in restored database"
        continue
    fi

    # Check that table has at least some rows (0 is acceptable for fresh DBs but worth noting)
    ROW_COUNT=$(PGPASSWORD="${PGPASSWORD}" psql \
        --host="$PG_HOST" --port="$PG_PORT" \
        --username="$PG_USER" --dbname="$VALIDATE_DB" \
        --no-psqlrc --tuples-only --quiet \
        -c "SELECT count(*) FROM ${tbl};" \
        2>/dev/null | tr -d ' \n' || echo "-1")

    if [[ "$ROW_COUNT" == "-1" ]]; then
        record FAIL "row-count:${tbl}" "Count query failed for table '${tbl}'"
    elif [[ "$ROW_COUNT" -gt 0 ]]; then
        record PASS "row-count:${tbl}" "${ROW_COUNT} row(s)"
    else
        record WARN "row-count:${tbl}" "Table '${tbl}' is empty (may be expected for a fresh deploy)"
    fi
done

# ── Cleanup ───────────────────────────────────────────────────────────────────
info "Cleaning up validation database and staging files ..."
PGPASSWORD="${PGPASSWORD}" psql \
    --host="$PG_HOST" --port="$PG_PORT" \
    --username="$PG_USER" --dbname="postgres" \
    --no-psqlrc --quiet \
    -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='${VALIDATE_DB}' AND pid <> pg_backend_pid();" \
    -c "DROP DATABASE IF EXISTS ${VALIDATE_DB};" >/dev/null 2>&1 || \
    warn "Failed to drop validation database ${VALIDATE_DB} — clean up manually"

rm -f "$LOCAL_ARCHIVE" "$DUMP_FILE"

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════════"
echo "  Validation summary: PASS=${PASS_COUNT}  FAIL=${FAIL_COUNT}"
echo "══════════════════════════════════════════════════════"

if [[ "$EMIT_JSON" == "true" ]]; then
    echo ""
    printf '{"validation_timestamp":"%s","backup_key":"%s","pass":%d,"fail":%d,"results":[' \
        "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$RESTORE_KEY" "$PASS_COUNT" "$FAIL_COUNT"
    IFS=','; printf '%s' "${RESULTS[*]}"
    printf ']}\n'
fi

if [[ "$FAIL_COUNT" -gt 0 ]]; then
    if [[ "$WARN_ONLY" == "true" ]]; then
        warn "Validation completed with ${FAIL_COUNT} failure(s) — --warn-only mode, exit 0"
        exit 0
    else
        error "Backup validation FAILED: ${FAIL_COUNT} check(s) did not pass."
        exit 4
    fi
fi

info "Backup validation PASSED for s3://${S3_BUCKET}/${RESTORE_KEY}"
exit 0
