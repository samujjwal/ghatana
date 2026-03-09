#!/usr/bin/env bash
# DCMaar End-to-End Test Script
# Validates the entire demo environment

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")/.."
REPORTS_DIR="${REPORTS_DIR:-$ROOT_DIR/reports/tests}"
DOCKER_COMPOSE_FILE="${DOCKER_COMPOSE_FILE:-docker-compose.demo.yml}"
TEST_TIMEOUT=${TEST_TIMEOUT:-300}  # 5 minutes default timeout

# Logging function
log() {
    local level=$1
    local message=$2
    local timestamp
    timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo -e "[${timestamp}] [${level}] ${message}"
    
    # Also log to file
    mkdir -p "$REPORTS_DIR"
    echo "[${timestamp}] [${level}] ${message}" >> "$REPORTS_DIR/e2e-test-$(date +%Y%m%d).log"
}

# Check if a command exists
command_exists() {
    command -v "$1" &> /dev/null
}

# Check if a service is healthy
wait_for_service() {
    local service=$1
    local url=$2
    local timeout=$3
    local start_time
    start_time=$(date +%s)
    
    log "INFO" "⏳ Waiting for ${service} to be ready..."
    
    while true; do
        local current_time
        current_time=$(date +%s)
        local elapsed=$((current_time - start_time))
        
        if [ $elapsed -ge $timeout ]; then
            log "ERROR" "❌ Timeout waiting for ${service} to be ready"
            return 1
        fi
        
        if curl -s --fail "$url" &> /dev/null; then
            log "INFO" "✅ ${service} is ready"
            return 0
        fi
        
        sleep 5
    done
}

# Run a test with timeout
run_test() {
    local name=$1
    local command=$2
    local timeout=${3:-60}  # Default 60s timeout
    
    log "TEST" "🚀 Starting test: ${name}"
    local start_time
    start_time=$(date +%s)
    
    # Run command with timeout
    if timeout $timeout bash -c "$command" &>> "$REPORTS_DIR/e2e-test-$(date +%Y%m%d).log"; then
        local end_time
        end_time=$(date +%s)
        local duration=$((end_time - start_time))
        log "TEST" "✅ PASS: ${name} (${duration}s)"
        return 0
    else
        log "TEST" "❌ FAIL: ${name} (exceeded ${timeout}s timeout)"
        return 1
    fi
}

# Check prerequisites
check_prerequisites() {
    log "INFO" "🔍 Checking prerequisites..."
    
    local missing=0
    local commands=("docker" "docker-compose" "curl" "jq")
    
    for cmd in "${commands[@]}"; do
        if ! command_exists "$cmd"; then
            log "ERROR" "❌ Required command '$cmd' is not installed."
            ((missing++))
        fi
    done
    
    if [ $missing -gt 0 ]; then
        log "ERROR" "❌ $missing required commands are missing. Please install them first."
        return 1
    fi
    
    if ! docker info &> /dev/null; then
        log "ERROR" "❌ Docker daemon is not running."
        return 1
    fi
    
    log "INFO" "✅ All prerequisites are met"
    return 0
}

# Test environment setup
test_environment() {
    log "INFO" "🔧 Testing environment setup..."
    
    # Check if docker-compose file exists
    if [ ! -f "$DOCKER_COMPOSE_FILE" ]; then
        log "ERROR" "❌ Docker Compose file not found: $DOCKER_COMPOSE_FILE"
        return 1
    fi
    
    # Start services if not already running
    if ! docker-compose -f "$DOCKER_COMPOSE_FILE" ps | grep -q "Up"; then
        log "INFO" "🚀 Starting demo environment..."
        if ! docker-compose -f "$DOCKER_COMPOSE_FILE" up -d; then
            log "ERROR" "❌ Failed to start demo environment"
            return 1
        fi
        
        # Wait for services to be ready
        wait_for_service "API" "http://localhost:8080/health" 60 || return 1
        wait_for_service "Database" "http://localhost:5432" 60 || return 1
        wait_for_service "Prometheus" "http://localhost:9090/-/ready" 60 || return 1
        wait_for_service "Grafana" "http://localhost:3000" 60 || return 1
    else
        log "INFO" "✅ Demo environment is already running"
    fi
    
    log "INFO" "✅ Environment setup completed successfully"
    return 0
}

# Test API endpoints
test_api_endpoints() {
    log "INFO" "🔌 Testing API endpoints..."
    local api_url="http://localhost:8080"
    local test_passed=true
    
    # Test health endpoint
    run_test "Health Check" "curl -s -f '${api_url}/health' | jq -e '.status == \"UP\"'" 10 || test_passed=false
    
    # Test metrics endpoint
    run_test "Metrics Endpoint" "curl -s -f '${api_url}/metrics' | grep -q 'http_requests_total'" 10 || test_passed=false
    
    # Test sample data endpoint
    run_test "Data Endpoint" "curl -s -f '${api_url}/api/v1/data' | jq -e '. | length >= 0'" 10 || test_passed=false
    
    if [ "$test_passed" = false ]; then
        log "ERROR" "❌ Some API tests failed"
        return 1
    fi
    
    log "INFO" "✅ All API tests passed"
    return 0
}

# Test database connectivity
test_database() {
    log "INFO" "💾 Testing database connectivity..."
    
    # Check if PostgreSQL is accessible
    run_test "PostgreSQL Connection" "PGPASSWORD=postgres psql -h localhost -U postgres -d postgres -c 'SELECT 1'" 10 || return 1
    
    # Check if tables exist
    run_test "Database Schema" "PGPASSWORD=postgres psql -h localhost -U postgres -d postgres -c '\\dt' | grep -q 'public'" 10 || return 1
    
    log "INFO" "✅ Database tests passed"
    return 0
}

# Test monitoring stack
test_monitoring() {
    log "INFO" "📊 Testing monitoring stack..."
    
    # Test Prometheus
    run_test "Prometheus" "curl -s -f 'http://localhost:9090/api/v1/query?query=up' | jq -e '.data.result | length > 0'" 10 || return 1
    
    # Test Grafana
    run_test "Grafana" "curl -s -f 'http://localhost:3000/api/health' | jq -e '.database == \"ok\"'" 10 || return 1
    
    # Test Alertmanager
    run_test "Alertmanager" "curl -s -f 'http://localhost:9093/-/ready' | grep -q 'OK'" 10 || return 1
    
    log "INFO" "✅ Monitoring tests passed"
    return 0
}

# Test data flow
test_data_flow() {
    log "INFO" "🔄 Testing data flow..."
    
    # Generate test data
    run_test "Data Generation" "$SCRIPT_DIR/seed-data.sh" 60 || return 1
    
    # Verify data in database
    run_test "Data Verification" "PGPASSWORD=postgres psql -h localhost -U postgres -d postgres -c 'SELECT COUNT(*) FROM events' | grep -q '[0-9]'" 10 || return 1
    
    # Verify metrics in Prometheus
    run_test "Metrics Verification" "curl -s 'http://localhost:9090/api/v1/query?query=events_total' | jq -e '.data.result | length > 0'" 10 || return 1
    
    log "INFO" "✅ Data flow tests passed"
    return 0
}

# Generate test report
generate_report() {
    local passed=$1
    local failed=$2
    local skipped=$3
    local total=$((passed + failed + skipped))
    local timestamp
    timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    
    local report_file="$REPORTS_DIR/e2e-test-report-$(date +%Y%m%d-%H%M%S).md"
    
    cat > "$report_file" << EOF
# DCMaar End-to-End Test Report

## Summary
- **Date**: $timestamp
- **Total Tests**: $total
- **Passed**: $passed ✅
- **Failed**: $failed ❌
- **Skipped**: $skipped ⚠️
- **Success Rate**: $((passed * 100 / total))%

## Test Results
$(grep -E 'TEST.*(PASS|FAIL|SKIP)' "$REPORTS_DIR/e2e-test-$(date +%Y%m%d).log" | sed 's/^/  - /')

## Details
$(cat "$REPORTS_DIR/e2e-test-$(date +%Y%m%d).log")

## Environment
- **OS**: $(uname -a)
- **Docker**: $(docker --version)
- **Docker Compose**: $(docker-compose --version)
- **Script Version**: $(git rev-parse --short HEAD 2>/dev/null || echo "unknown")

## Notes
- Tests were run against the demo environment
- See logs for detailed error information
- Run individual tests for more detailed debugging
EOF
    
    log "INFO" "📊 Test report generated: $report_file"
    
    # Print summary
    echo ""
    echo "📋 Test Summary:"
    echo "  Total:  $total"
    echo "  Passed: $passed ✅"
    echo "  Failed: $failed ❌"
    echo "  Skipped: $skipped ⚠️"
    echo ""
    echo "View full report: $report_file"
    
    if [ $failed -gt 0 ]; then
        return 1
    fi
    return 0
}

# Main function
main() {
    # Initialize counters
    local passed=0
    local failed=0
    local skipped=0
    
    # Create reports directory
    mkdir -p "$REPORTS_DIR"
    
    # Run tests
    if check_prerequisites; then
        ((passed++))
    else
        ((failed++))
        generate_report $passed $failed $skipped
        return 1
    fi
    
    if test_environment; then
        ((passed++))
    else
        ((failed++))
    fi
    
    if test_api_endpoints; then
        ((passed++))
    else
        ((failed++))
    fi
    
    if test_database; then
        ((passed++))
    else
        ((failed++))
    fi
    
    if test_monitoring; then
        ((passed++))
    else
        ((failed++))
    fi
    
    if test_data_flow; then
        ((passed++))
    else
        ((failed++))
    fi
    
    # Generate final report
    if ! generate_report $passed $failed $skipped; then
        return 1
    fi
    
    if [ $failed -gt 0 ]; then
        log "ERROR" "❌ Some tests failed. Check the report for details."
        return 1
    fi
    
    log "INFO" "🎉 All tests passed successfully!"
    return 0
}

# Run the main function
if main; then
    exit 0
else
    exit 1
fi
