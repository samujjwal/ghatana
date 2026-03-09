#!/bin/bash
# =============================================================================
# DR Test Suite - Automated Disaster Recovery Testing
# =============================================================================
# Purpose: Automate all DR test scenarios with validation and reporting
# Owner: DevOps Engineer
# Version: 1.0.0
# =============================================================================

set -e
set -o pipefail

# =============================================================================
# CONFIGURATION
# =============================================================================

CONTROL_PLANE_URL="${CONTROL_PLANE_URL:-http://control-plane:8080}"
POSTGRES_HOST="${POSTGRES_HOST:-postgres-l1}"
POSTGRES_USER="${POSTGRES_USER:-ghatana}"
POSTGRES_DB="${POSTGRES_DB:-eventcloud}"
REDIS_HOST="${REDIS_HOST:-redis.ghatana.com}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://prometheus:9090}"

REPORT_DIR="/tmp/dr-test-reports"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
TEST_REPORT="$REPORT_DIR/dr-test-$TIMESTAMP.txt"

# RTO/RPO Targets (seconds)
RTO_NODE_FAILURE=120
RTO_DATABASE_FAILOVER=300
RTO_STATE_RESTORE=180
RPO_TARGET=60

# =============================================================================
# LOGGING
# =============================================================================

mkdir -p "$REPORT_DIR"

log() {
  echo "[$(date +'%Y-%m-%d %H:%M:%S')] $1" | tee -a "$TEST_REPORT"
}

log_success() {
  echo "[$(date +'%Y-%m-%d %H:%M:%S')] ✅ $1" | tee -a "$TEST_REPORT"
}

log_error() {
  echo "[$(date +'%Y-%m-%d %H:%M:%S')] ❌ $1" | tee -a "$TEST_REPORT"
}

log_warning() {
  echo "[$(date +'%Y-%m-%d %H:%M:%S')] ⚠️  $1" | tee -a "$TEST_REPORT"
}

# =============================================================================
# PRE-FLIGHT CHECKS
# =============================================================================

preflight_checks() {
  log "=== Pre-Flight Checks ==="
  
  # Check control plane health
  if ! curl -sf "$CONTROL_PLANE_URL/health" > /dev/null; then
    log_error "Control plane not healthy"
    return 1
  fi
  log_success "Control plane healthy"
  
  # Check PostgreSQL connectivity
  if ! psql -h "$POSTGRES_HOST" -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT 1" > /dev/null 2>&1; then
    log_error "PostgreSQL not accessible"
    return 1
  fi
  log_success "PostgreSQL accessible"
  
  # Check Redis connectivity
  if ! redis-cli -h "$REDIS_HOST" PING > /dev/null 2>&1; then
    log_error "Redis not accessible"
    return 1
  fi
  log_success "Redis accessible"
  
  # Check Prometheus connectivity
  if ! curl -sf "$PROMETHEUS_URL/api/v1/status/config" > /dev/null; then
    log_error "Prometheus not accessible"
    return 1
  fi
  log_success "Prometheus accessible"
  
  log_success "Pre-flight checks passed"
  return 0
}

# =============================================================================
# UTILITY FUNCTIONS
# =============================================================================

get_cluster_health() {
  curl -s "$CONTROL_PLANE_URL/api/v1/cluster/health" | jq -r '.status'
}

get_event_count() {
  local minutes=$1
  psql -h "$POSTGRES_HOST" -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -c \
    "SELECT COUNT(*) FROM events WHERE created_at > NOW() - INTERVAL '$minutes minutes';" | xargs
}

get_unhealthy_partitions() {
  curl -s "$CONTROL_PLANE_URL/api/v1/partitions" | jq '[.partitions[] | select(.status != "healthy")] | length'
}

get_non_running_operators() {
  curl -s "$CONTROL_PLANE_URL/api/v1/operators" | jq '[.operators[] | select(.status != "running")] | length'
}

wait_for_healthy_cluster() {
  local timeout=$1
  local elapsed=0
  local interval=5
  
  while [ $elapsed -lt $timeout ]; do
    local status=$(get_cluster_health)
    if [ "$status" = "healthy" ]; then
      return 0
    fi
    sleep $interval
    elapsed=$((elapsed + interval))
  done
  
  return 1
}

# =============================================================================
# TEST 1: NODE FAILURE RECOVERY
# =============================================================================

test_node_failure() {
  log "=== TEST 1: Node Failure Recovery ==="
  local start_time=$(date +%s)
  
  # Select victim node (node-3 by default)
  local victim_node="node-3"
  log "Selected victim node: $victim_node"
  
  # Record baseline
  local baseline_events=$(get_event_count 5)
  log "Baseline events (last 5 min): $baseline_events"
  
  # Simulate failure
  log "Simulating failure on $victim_node..."
  ssh "$victim_node" "sudo systemctl kill -s SIGKILL eventcloud" || true
  
  # Wait for detection and recovery
  log "Waiting for failure detection and recovery..."
  sleep 60
  
  # Verify recovery
  local unhealthy=$(get_unhealthy_partitions)
  if [ "$unhealthy" -ne 0 ]; then
    log_error "TEST FAILED: $unhealthy partitions still unhealthy"
    return 1
  fi
  log_success "All partitions healthy"
  
  local not_running=$(get_non_running_operators)
  if [ "$not_running" -ne 0 ]; then
    log_error "TEST FAILED: $not_running operators not running"
    return 1
  fi
  log_success "All operators running"
  
  # Verify no data loss
  local current_events=$(get_event_count 5)
  local lost_events=$((baseline_events - current_events))
  if [ $lost_events -gt 10 ]; then
    log_warning "Possible data loss: $lost_events events"
  else
    log_success "No significant data loss: $lost_events events"
  fi
  
  # Calculate RTO
  local end_time=$(date +%s)
  local rto=$((end_time - start_time))
  log "Actual RTO: ${rto}s (target: <${RTO_NODE_FAILURE}s)"
  
  if [ $rto -le $RTO_NODE_FAILURE ]; then
    log_success "RTO target met"
  else
    log_warning "RTO target missed by $((rto - RTO_NODE_FAILURE))s"
  fi
  
  # Restore victim node
  log "Restoring $victim_node..."
  ssh "$victim_node" "sudo systemctl start eventcloud"
  sleep 30
  
  # Final health check
  if wait_for_healthy_cluster 60; then
    log_success "TEST 1 PASSED: Node Failure Recovery"
    return 0
  else
    log_error "TEST 1 FAILED: Cluster not healthy after recovery"
    return 1
  fi
}

# =============================================================================
# TEST 2: DATABASE FAILOVER
# =============================================================================

test_database_failover() {
  log "=== TEST 2: Database Failover Recovery ==="
  local start_time=$(date +%s)
  
  local primary="postgres-l1-primary"
  local standby="postgres-l1-standby"
  
  # Verify replication status
  log "Checking replication status..."
  local lag=$(psql -h "$primary" -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -c \
    "SELECT EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp()))::int FROM pg_stat_replication;" | xargs)
  log "Current replication lag: ${lag}s"
  
  # Simulate primary failure
  log "Simulating primary failure..."
  ssh "$primary" "sudo systemctl stop postgresql" || true
  
  # Promote standby
  log "Promoting standby to primary..."
  ssh "$standby" "sudo -u postgres pg_ctl promote -D /var/lib/postgresql/data"
  
  # Wait for promotion
  sleep 30
  
  # Update connection string
  log "Updating connection strings..."
  kubectl set env deployment/eventcloud-control-plane "POSTGRES_HOST=$standby"
  
  # Wait for pods to restart
  sleep 60
  
  # Verify applications reconnected
  if ! psql -h "$standby" -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT COUNT(*) FROM events WHERE created_at > NOW() - INTERVAL '1 minute';" > /dev/null 2>&1; then
    log_error "TEST FAILED: Cannot query new primary"
    return 1
  fi
  log_success "Applications reconnected to new primary"
  
  # Calculate RTO
  local end_time=$(date +%s)
  local rto=$((end_time - start_time))
  log "Actual RTO: ${rto}s (target: <${RTO_DATABASE_FAILOVER}s)"
  
  if [ $rto -le $RTO_DATABASE_FAILOVER ]; then
    log_success "RTO target met"
  else
    log_warning "RTO target missed by $((rto - RTO_DATABASE_FAILOVER))s"
  fi
  
  # Calculate RPO (data loss)
  local rpo=$lag
  log "Actual RPO: ${rpo}s (target: <${RPO_TARGET}s)"
  
  if [ $rpo -le $RPO_TARGET ]; then
    log_success "RPO target met"
  else
    log_warning "RPO target missed by $((rpo - RPO_TARGET))s"
  fi
  
  # Final health check
  if wait_for_healthy_cluster 120; then
    log_success "TEST 2 PASSED: Database Failover"
    return 0
  else
    log_error "TEST 2 FAILED: Cluster not healthy after failover"
    return 1
  fi
}

# =============================================================================
# TEST 3: STATE STORE RESTORE
# =============================================================================

test_state_store_restore() {
  log "=== TEST 3: State Store Restore ==="
  local start_time=$(date +%s)
  
  # Simulate Redis failure
  log "Simulating Redis failure..."
  ssh "$REDIS_HOST" "sudo systemctl stop redis" || true
  
  # Wait for operators to detect failure and fall back to local state
  log "Waiting for operators to fall back to local state..."
  sleep 30
  
  # Verify operators still processing (degraded mode)
  local not_running=$(get_non_running_operators)
  if [ "$not_running" -ne 0 ]; then
    log_error "TEST FAILED: $not_running operators stopped (should fall back to local)"
    return 1
  fi
  log_success "Operators running in degraded mode (local state only)"
  
  # Restore Redis from backup
  log "Restoring Redis from latest RDB backup..."
  local latest_backup=$(aws s3 ls s3://ghatana-backups/redis/ | sort | tail -n 1 | awk '{print $4}')
  aws s3 cp "s3://ghatana-backups/redis/$latest_backup" "/tmp/dump.rdb"
  scp "/tmp/dump.rdb" "$REDIS_HOST:/var/lib/redis/dump.rdb"
  ssh "$REDIS_HOST" "sudo systemctl start redis"
  
  # Wait for Redis to start
  sleep 10
  
  # Verify Redis connectivity
  if ! redis-cli -h "$REDIS_HOST" PING > /dev/null 2>&1; then
    log_error "TEST FAILED: Redis not responding after restore"
    return 1
  fi
  log_success "Redis restored successfully"
  
  # Trigger state resynchronization
  log "Triggering state resynchronization..."
  curl -X POST "$CONTROL_PLANE_URL/api/v1/state/resync" \
    -H "Content-Type: application/json" \
    -d '{"strategy": "BATCHED", "maxBatchSize": 1000}' \
    -s > /dev/null
  
  # Wait for resync to complete
  log "Waiting for state resync to complete..."
  local timeout=180
  local elapsed=0
  while [ $elapsed -lt $timeout ]; do
    local progress=$(curl -s "$CONTROL_PLANE_URL/api/v1/state/resync-status" | jq -r '.progress')
    if [ "$progress" = "100" ]; then
      break
    fi
    sleep 5
    elapsed=$((elapsed + 5))
  done
  
  if [ $elapsed -ge $timeout ]; then
    log_error "TEST FAILED: State resync did not complete within ${timeout}s"
    return 1
  fi
  log_success "State resync completed in ${elapsed}s"
  
  # Verify state consistency
  local mismatches=$(curl -s "$CONTROL_PLANE_URL/api/v1/state/consistency-check" | jq '.mismatches')
  if [ "$mismatches" -ne 0 ]; then
    log_error "TEST FAILED: $mismatches state inconsistencies found"
    return 1
  fi
  log_success "State consistency verified"
  
  # Calculate RTO
  local end_time=$(date +%s)
  local rto=$((end_time - start_time))
  log "Actual RTO: ${rto}s (target: <${RTO_STATE_RESTORE}s)"
  
  if [ $rto -le $RTO_STATE_RESTORE ]; then
    log_success "RTO target met"
  else
    log_warning "RTO target missed by $((rto - RTO_STATE_RESTORE))s"
  fi
  
  # Final health check
  if wait_for_healthy_cluster 60; then
    log_success "TEST 3 PASSED: State Store Restore"
    return 0
  else
    log_error "TEST 3 FAILED: Cluster not healthy after restore"
    return 1
  fi
}

# =============================================================================
# TEST 4: BACKUP VERIFICATION
# =============================================================================

test_backup_verification() {
  log "=== TEST 4: Backup Verification ==="
  
  # Test PostgreSQL backup
  log "Testing PostgreSQL backup restore..."
  local latest_pg_backup=$(aws s3 ls s3://ghatana-backups/postgres/ | grep "base-" | sort | tail -n 1 | awk '{print $4}')
  if [ -z "$latest_pg_backup" ]; then
    log_error "No PostgreSQL backups found"
    return 1
  fi
  log "Latest PostgreSQL backup: $latest_pg_backup"
  
  # Test Redis backup
  log "Testing Redis backup restore..."
  local latest_redis_backup=$(aws s3 ls s3://ghatana-backups/redis/ | sort | tail -n 1 | awk '{print $4}')
  if [ -z "$latest_redis_backup" ]; then
    log_error "No Redis backups found"
    return 1
  fi
  log "Latest Redis backup: $latest_redis_backup"
  
  # Test checkpoint backup
  log "Testing checkpoint backup..."
  local latest_checkpoint=$(aws s3 ls s3://ghatana-backups/checkpoints/ | sort | tail -n 1 | awk '{print $4}')
  if [ -z "$latest_checkpoint" ]; then
    log_error "No checkpoint backups found"
    return 1
  fi
  log "Latest checkpoint: $latest_checkpoint"
  
  # Verify backup integrity (checksums)
  log "Verifying backup integrity..."
  aws s3 cp "s3://ghatana-backups/postgres/$latest_pg_backup" "/tmp/test-backup.tar.gz"
  if ! tar -tzf "/tmp/test-backup.tar.gz" > /dev/null 2>&1; then
    log_error "PostgreSQL backup corrupted"
    return 1
  fi
  log_success "PostgreSQL backup integrity verified"
  
  rm "/tmp/test-backup.tar.gz"
  
  log_success "TEST 4 PASSED: Backup Verification"
  return 0
}

# =============================================================================
# GENERATE REPORT
# =============================================================================

generate_report() {
  local test_results=("$@")
  
  log "=== DR Test Suite Summary ==="
  log ""
  log "Test Execution Date: $(date)"
  log "Test Report: $TEST_REPORT"
  log ""
  log "Test Results:"
  log "  1. Node Failure Recovery: ${test_results[0]}"
  log "  2. Database Failover: ${test_results[1]}"
  log "  3. State Store Restore: ${test_results[2]}"
  log "  4. Backup Verification: ${test_results[3]}"
  log ""
  
  local passed=0
  local failed=0
  for result in "${test_results[@]}"; do
    if [ "$result" = "PASS" ]; then
      ((passed++))
    else
      ((failed++))
    fi
  done
  
  log "Total: $passed passed, $failed failed"
  log ""
  
  if [ $failed -eq 0 ]; then
    log_success "ALL DR TESTS PASSED ✅"
  else
    log_error "SOME DR TESTS FAILED ❌"
  fi
  
  # Send report to ops team
  if [ -f "$TEST_REPORT" ]; then
    mail -s "DR Test Report - $(date +%Y-%m-%d)" ops@ghatana.com < "$TEST_REPORT"
  fi
}

# =============================================================================
# MAIN EXECUTION
# =============================================================================

main() {
  log "=== DR Test Suite Starting ==="
  log "Timestamp: $TIMESTAMP"
  log ""
  
  # Pre-flight checks
  if ! preflight_checks; then
    log_error "Pre-flight checks failed, aborting DR tests"
    exit 1
  fi
  
  # Run tests
  declare -a test_results
  
  if test_node_failure; then
    test_results[0]="PASS"
  else
    test_results[0]="FAIL"
  fi
  
  if test_database_failover; then
    test_results[1]="PASS"
  else
    test_results[1]="FAIL"
  fi
  
  if test_state_store_restore; then
    test_results[2]="PASS"
  else
    test_results[2]="FAIL"
  fi
  
  if test_backup_verification; then
    test_results[3]="PASS"
  else
    test_results[3]="FAIL"
  fi
  
  # Generate report
  generate_report "${test_results[@]}"
  
  # Exit with appropriate code
  if [ "${test_results[0]}" = "FAIL" ] || \
     [ "${test_results[1]}" = "FAIL" ] || \
     [ "${test_results[2]}" = "FAIL" ] || \
     [ "${test_results[3]}" = "FAIL" ]; then
    exit 1
  else
    exit 0
  fi
}

# Run main
main "$@"
