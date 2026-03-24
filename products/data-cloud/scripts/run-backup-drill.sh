#!/usr/bin/env bash
# =============================================================================
# run-backup-drill.sh — Data-Cloud Backup/Restore Drill Automation
#
# Purpose: Automates the pre-flight connectivity and configuration validation
#          checks for each storage tier defined in DR_RUNBOOK.md (Workstream F).
#          Does NOT perform actual backup/restore — validates that the backup
#          toolchain is configured, reachable, and reporting healthy status.
#
# Usage:
#   bash products/data-cloud/scripts/run-backup-drill.sh [--warn-only] [--json]
#
# Options:
#   --warn-only  Print findings but exit 0 even on FAIL (gradual rollout / CI)
#   --json       Emit structured JSON report to stdout (in addition to text log)
#
# Environment variables (each tier is SKIPPED if not set):
#   POSTGRES_HOST         PostgreSQL host (default: localhost)
#   POSTGRES_PORT         PostgreSQL port (default: 5432)
#   POSTGRES_DB           PostgreSQL database name
#   POSTGRES_USER         PostgreSQL user
#   PGPASSWORD            PostgreSQL password
#   CLICKHOUSE_HOST       ClickHouse host (default: localhost)
#   CLICKHOUSE_PORT       ClickHouse HTTP port (default: 8123)
#   CLICKHOUSE_USER       ClickHouse user (default: default)
#   CLICKHOUSE_PASSWORD   ClickHouse password
#   OPENSEARCH_HOST       OpenSearch host (default: localhost)
#   OPENSEARCH_PORT       OpenSearch REST port (default: 9200)
#   OPENSEARCH_USER       OpenSearch user (optional, for auth)
#   OPENSEARCH_PASSWORD   OpenSearch password (optional)
#   S3_ENDPOINT           S3 / Ceph endpoint URL
#   S3_BUCKET             Backup destination bucket name
#   AWS_ACCESS_KEY_ID     S3 access key
#   AWS_SECRET_ACCESS_KEY S3 secret key
#
# Exit codes:
#   0  All configured tiers passed (or --warn-only set)
#   1  One or more configured tiers FAILED
#
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

WARN_ONLY=false
EMIT_JSON=false
for arg in "$@"; do
  [[ "$arg" == "--warn-only" ]] && WARN_ONLY=true
  [[ "$arg" == "--json" ]]      && EMIT_JSON=true
done

RED='\033[0;31m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'; CYAN='\033[0;36m'
NC='\033[0m'; BOLD='\033[1m'

PASS=0; FAIL=0; SKIP=0
declare -a RESULTS=()

# ── Helpers ──────────────────────────────────────────────────────────────────

is_set() { [[ -n "${!1:-}" ]]; }

cmd_available() { command -v "$1" >/dev/null 2>&1; }

emit() {
  local status="$1" tier="$2" check="$3" detail="${4:-}"
  RESULTS+=("{\"status\":\"$status\",\"tier\":\"$tier\",\"check\":\"$check\",\"detail\":\"$detail\"}")
  case "$status" in
    PASS) echo -e "  ${GREEN}✅ PASS${NC}  [$tier] $check${detail:+ — $detail}"; PASS=$((PASS+1)) ;;
    WARN) echo -e "  ${YELLOW}⚠  WARN${NC}  [$tier] $check${detail:+ — $detail}"; PASS=$((PASS+1)) ;;
    SKIP) echo -e "  ${CYAN}⬜ SKIP${NC}  [$tier] $check — not configured"; SKIP=$((SKIP+1)) ;;
    FAIL) echo -e "  ${RED}✗  FAIL${NC}  [$tier] $check${detail:+ — $detail}"; FAIL=$((FAIL+1)) ;;
  esac
}

require_tool() {
  local tool="$1" install_hint="$2"
  if ! cmd_available "$tool"; then
    emit FAIL "toolchain" "$tool not found in PATH" "install: $install_hint"
    return 1
  fi
  return 0
}

check_http_endpoint() {
  local tier="$1" url="$2" expected_http_code="${3:-200}"
  local http_code
  http_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$url" 2>/dev/null || echo "000")
  if [[ "$http_code" == "$expected_http_code" ]] || [[ "$http_code" =~ ^2 ]]; then
    emit PASS "$tier" "HTTP endpoint reachable" "GET $url → $http_code"
  else
    emit FAIL "$tier" "HTTP endpoint unreachable or error" "GET $url → $http_code (expected ~$expected_http_code)"
  fi
}

# ── Header ───────────────────────────────────────────────────────────────────

echo ""
echo -e "${BOLD}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}║       Data-Cloud Backup/Restore Drill — $(date -u '+%Y-%m-%d %H:%M:%S UTC')     ║${NC}"
echo -e "${BOLD}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# ── 1. PostgreSQL ─────────────────────────────────────────────────────────────

echo -e "${BOLD}── PostgreSQL (entity store, point-in-time recovery) ──────────${NC}"
if ! is_set POSTGRES_DB; then
  emit SKIP "postgresql" "connectivity check" "POSTGRES_DB not set"
  emit SKIP "postgresql" "WAL archiving status"
  emit SKIP "postgresql" "base backup listing"
else
  PG_HOST="${POSTGRES_HOST:-localhost}"
  PG_PORT="${POSTGRES_PORT:-5432}"
  PG_USER="${POSTGRES_USER:-postgres}"
  PG_DB="$POSTGRES_DB"

  if require_tool psql "apt install postgresql-client / brew install postgresql"; then
    # Connectivity
    if PGPASSWORD="${PGPASSWORD:-}" psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" \
        -c "SELECT 1;" -q --no-psqlrc >/dev/null 2>&1; then
      emit PASS "postgresql" "connectivity: $PG_USER@$PG_HOST:$PG_PORT/$PG_DB"
    else
      emit FAIL "postgresql" "connectivity failed" "$PG_USER@$PG_HOST:$PG_PORT/$PG_DB"
    fi

    # WAL archiving status
    WAL_STATUS=$(PGPASSWORD="${PGPASSWORD:-}" psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" \
      -t -c "SELECT CASE WHEN current_setting('archive_mode')='on' THEN 'enabled' ELSE 'disabled' END;" \
      --no-psqlrc 2>/dev/null | tr -d ' \n' || echo "unknown")
    if [[ "$WAL_STATUS" == "enabled" ]]; then
      emit PASS "postgresql" "WAL archive_mode=on"
    else
      emit WARN "postgresql" "WAL archive_mode=$WAL_STATUS" "enable for RPO ≤5min"
    fi

    # pg_basebackup dry-run list
    if cmd_available pg_basebackup; then
      emit PASS "postgresql" "pg_basebackup available for scheduled base backups"
    else
      emit WARN "postgresql" "pg_basebackup not in PATH" "needed for base backups in cron/scripts"
    fi
  fi
fi

# ── 2. ClickHouse ────────────────────────────────────────────────────────────

echo ""
echo -e "${BOLD}── ClickHouse (time-series analytics) ─────────────────────────${NC}"
if ! is_set CLICKHOUSE_HOST; then
  emit SKIP "clickhouse" "connectivity check"
  emit SKIP "clickhouse" "backup status query"
else
  CH_HOST="${CLICKHOUSE_HOST:-localhost}"
  CH_PORT="${CLICKHOUSE_PORT:-8123}"
  CH_USER="${CLICKHOUSE_USER:-default}"
  CH_PASS="${CLICKHOUSE_PASSWORD:-}"
  CH_URL="http://$CH_HOST:$CH_PORT"

  # Connectivity via HTTP ping
  PING_RESPONSE=$(curl -s --max-time 5 \
    -u "${CH_USER}:${CH_PASS}" \
    "${CH_URL}/?query=SELECT%201" 2>/dev/null || echo "")
  if [[ "$PING_RESPONSE" == "1" ]]; then
    emit PASS "clickhouse" "connectivity: $CH_URL (user=$CH_USER)"
  else
    emit FAIL "clickhouse" "connectivity failed" "URL=$CH_URL response='${PING_RESPONSE:0:100}'"
  fi

  # List most recent backup via system.backups (ClickHouse 22.7+)
  BACKUP_COUNT=$(curl -s --max-time 10 \
    -u "${CH_USER}:${CH_PASS}" \
    "${CH_URL}/?query=SELECT+count()+FROM+system.backups+WHERE+status%3D'BACKUP_COMPLETE'" \
    2>/dev/null | tr -d ' \n' || echo "0")
  if [[ "$BACKUP_COUNT" =~ ^[0-9]+$ ]] && [[ "$BACKUP_COUNT" -gt 0 ]]; then
    emit PASS "clickhouse" "backup history: $BACKUP_COUNT completed backup(s) in system.backups"
  else
    emit WARN "clickhouse" "no completed backups found in system.backups" \
      "schedule BACKUP TABLE ... TO Disk('backups', ...) via cron"
  fi

  # Latest backup age check
  LATEST_BACKUP_HOURS=$(curl -s --max-time 10 \
    -u "${CH_USER}:${CH_PASS}" \
    "${CH_URL}/?query=SELECT+dateDiff('hour',+max(start_time),+now())+FROM+system.backups+WHERE+status%3D'BACKUP_COMPLETE'" \
    2>/dev/null | tr -d ' \n' || echo "9999")
  if [[ "$LATEST_BACKUP_HOURS" =~ ^[0-9]+$ ]] && [[ "$LATEST_BACKUP_HOURS" -le 6 ]]; then
    emit PASS "clickhouse" "latest backup age: ${LATEST_BACKUP_HOURS}h (RPO target ≤6h)"
  elif [[ "$LATEST_BACKUP_HOURS" =~ ^[0-9]+$ ]]; then
    emit WARN "clickhouse" "latest backup age: ${LATEST_BACKUP_HOURS}h" "RPO target is ≤6h — check backup schedule"
  fi
fi

# ── 3. OpenSearch ────────────────────────────────────────────────────────────

echo ""
echo -e "${BOLD}── OpenSearch (full-text search indexes) ──────────────────────${NC}"
if ! is_set OPENSEARCH_HOST; then
  emit SKIP "opensearch" "connectivity check"
  emit SKIP "opensearch" "snapshot repository check"
else
  OS_HOST="${OPENSEARCH_HOST:-localhost}"
  OS_PORT="${OPENSEARCH_PORT:-9200}"
  OS_URL="http://$OS_HOST:$OS_PORT"
  OS_AUTH=""
  is_set OPENSEARCH_USER && OS_AUTH="-u ${OPENSEARCH_USER}:${OPENSEARCH_PASSWORD:-}"

  # Cluster health
  HEALTH=$(curl -s --max-time 5 $OS_AUTH "${OS_URL}/_cluster/health" 2>/dev/null | \
    python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('status','unknown'))" 2>/dev/null || echo "unknown")
  if [[ "$HEALTH" == "green" || "$HEALTH" == "yellow" ]]; then
    emit PASS "opensearch" "cluster health: $HEALTH"
  else
    emit FAIL "opensearch" "cluster health: $HEALTH" "expected green or yellow"
  fi

  # Snapshot repository registration
  REPO_COUNT=$(curl -s --max-time 5 $OS_AUTH "${OS_URL}/_snapshot" 2>/dev/null | \
    python3 -c "import json,sys; d=json.load(sys.stdin); print(len(d))" 2>/dev/null || echo "0")
  if [[ "$REPO_COUNT" =~ ^[0-9]+$ ]] && [[ "$REPO_COUNT" -gt 0 ]]; then
    emit PASS "opensearch" "snapshot repositories: $REPO_COUNT registered"
  else
    emit WARN "opensearch" "no snapshot repositories configured" \
      "register via PUT /_snapshot/<repo> before scheduling daily snapshots"
  fi
fi

# ── 4. S3 / Ceph Blob Storage ────────────────────────────────────────────────

echo ""
echo -e "${BOLD}── S3 / Ceph Blob Storage ─────────────────────────────────────${NC}"
if ! is_set S3_ENDPOINT; then
  emit SKIP "s3" "endpoint connectivity"
  emit SKIP "s3" "backup bucket exists"
else
  if require_tool aws "pip install awscli / brew install awscli"; then
    # Endpoint connectivity via aws s3 ls with custom endpoint
    AWS_CMD="aws s3 ls"
    [[ -n "${S3_ENDPOINT:-}" ]] && AWS_CMD="aws --endpoint-url '${S3_ENDPOINT}' s3 ls"

    if eval "$AWS_CMD" >/dev/null 2>&1; then
      emit PASS "s3" "S3 endpoint reachable: ${S3_ENDPOINT}"
    else
      emit FAIL "s3" "S3 endpoint unreachable or auth failed" "endpoint=${S3_ENDPOINT}"
    fi

    # Backup bucket exists
    if is_set S3_BUCKET; then
      if eval "aws ${S3_ENDPOINT:+--endpoint-url '${S3_ENDPOINT}'} s3 ls 's3://${S3_BUCKET}'" >/dev/null 2>&1; then
        emit PASS "s3" "backup bucket exists: s3://${S3_BUCKET}"
      else
        emit FAIL "s3" "backup bucket not found or inaccessible" "s3://${S3_BUCKET}"
      fi
    else
      emit SKIP "s3" "backup bucket check" "S3_BUCKET not set"
    fi
  fi
fi

# ── 5. Recovery Runbook Currency ─────────────────────────────────────────────

echo ""
echo -e "${BOLD}── DR Runbook Currency ────────────────────────────────────────${NC}"
RUNBOOK="$REPO_ROOT/products/data-cloud/docs/DR_RUNBOOK.md"
if [[ -f "$RUNBOOK" ]]; then
  # Check runbook is not older than 90 days
  if [[ "$(uname)" == "Darwin" ]]; then
    MTIME=$(stat -f "%m" "$RUNBOOK" 2>/dev/null || echo "0")
  else
    MTIME=$(stat -c "%Y" "$RUNBOOK" 2>/dev/null || echo "0")
  fi
  NOW=$(date +%s)
  AGE_DAYS=$(( (NOW - MTIME) / 86400 ))
  if [[ "$AGE_DAYS" -le 90 ]]; then
    emit PASS "runbook" "DR_RUNBOOK.md last modified ${AGE_DAYS} days ago (threshold ≤90 days)"
  else
    emit WARN "runbook" "DR_RUNBOOK.md last modified ${AGE_DAYS} days ago" \
      "review and update at least quarterly"
  fi

  # Version line present
  if grep -q "^> \*\*Version\*\*:" "$RUNBOOK"; then
    RUNBOOK_VERSION=$(grep "^\> \*\*Version\*\*:" "$RUNBOOK" | head -1 | sed 's/.*: //')
    emit PASS "runbook" "version tag present: $RUNBOOK_VERSION"
  else
    emit WARN "runbook" "no version tag found in DR_RUNBOOK.md"
  fi
else
  emit FAIL "runbook" "DR_RUNBOOK.md not found" "expected at products/data-cloud/docs/DR_RUNBOOK.md"
fi

# ── Summary ───────────────────────────────────────────────────────────────────

echo ""
echo -e "${BOLD}══════════════════════════════════════════════════════════════${NC}"
echo -e "  ${GREEN}PASS: $PASS${NC}  ${RED}FAIL: $FAIL${NC}  ${CYAN}SKIP: $SKIP${NC}"
echo -e "${BOLD}══════════════════════════════════════════════════════════════${NC}"

if "$EMIT_JSON"; then
  echo ""
  echo -e "${BOLD}JSON Report:${NC}"
  printf '{"drill_timestamp":"%s","pass":%d,"fail":%d,"skip":%d,"results":[' \
    "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$PASS" "$FAIL" "$SKIP"
  IFS=','; echo "${RESULTS[*]}"
  printf ']}\n'
fi

if [[ "$FAIL" -gt 0 ]]; then
  if "$WARN_ONLY"; then
    echo -e "${YELLOW}⚠  Drill completed with $FAIL failure(s) — --warn-only mode, exit 0${NC}"
    exit 0
  else
    echo -e "${RED}✗  Drill FAILED: $FAIL storage tier(s) did not pass pre-flight checks.${NC}"
    echo    "   Review the FAIL entries above and consult DR_RUNBOOK.md for remediation."
    exit 1
  fi
else
  echo -e "${GREEN}✅ Drill PASSED: all configured storage tiers are healthy (SKIP'd tiers not configured in this environment).${NC}"
  exit 0
fi
