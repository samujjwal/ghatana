#!/usr/bin/env bash
#
# OpenAPI Contract Testing Script for YAPPC API
#
# Usage:
#   ./test-contracts.sh                    # Run against local server (assumes running)
#   ./test-contracts.sh --start-server     # Start server, run tests, stop server
#   ./test-contracts.sh --help             # Show help
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
OPENAPI_SPEC="$SCRIPT_DIR/openapi.yaml"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_dependencies() {
    if ! command -v schemathesis &> /dev/null; then
        log_error "schemathesis not found. Install with: pip install schemathesis"
        exit 1
    fi
    
    if ! command -v curl &> /dev/null; then
        log_error "curl not found. Please install curl."
        exit 1
    fi
}

wait_for_api() {
    local max_attempts=60
    local attempt=1
    
    log_info "Waiting for API at $API_BASE_URL..."
    
    while [ $attempt -le $max_attempts ]; do
        if curl -f -s "$API_BASE_URL/health" > /dev/null 2>&1; then
            log_info "API is ready!"
            return 0
        fi
        
        echo -n "."
        sleep 1
        attempt=$((attempt + 1))
    done
    
    log_error "API did not become ready after $max_attempts seconds"
    return 1
}

run_contract_tests() {
    log_info "Running Schemathesis contract tests..."
    log_info "OpenAPI spec: $OPENAPI_SPEC"
    log_info "Base URL: $API_BASE_URL"
    
    schemathesis run "$OPENAPI_SPEC" \
        --base-url "$API_BASE_URL" \
        --checks all \
        --hypothesis-max-examples 50 \
        --hypothesis-deadline 5000 \
        --workers 4 \
        --show-errors-tracebacks \
        --report \
        --junit-xml contract-test-results.xml
    
    local exit_code=$?
    
    if [ $exit_code -eq 0 ]; then
        log_info "✅ All contract tests passed!"
    else
        log_error "❌ Contract tests failed with exit code $exit_code"
    fi
    
    return $exit_code
}

start_server() {
    log_info "Starting YAPPC API server..."
    
    cd "$SCRIPT_DIR/../../.."
    ./gradlew :backend:api:bootRun --no-daemon > /tmp/yappc-api.log 2>&1 &
    echo $! > /tmp/yappc-api.pid
    
    log_info "Server PID: $(cat /tmp/yappc-api.pid)"
}

stop_server() {
    if [ -f /tmp/yappc-api.pid ]; then
        local pid=$(cat /tmp/yappc-api.pid)
        log_info "Stopping API server (PID: $pid)..."
        kill "$pid" 2>/dev/null || true
        rm /tmp/yappc-api.pid
        log_info "Server stopped"
    fi
}

show_help() {
    cat << EOF
OpenAPI Contract Testing Script for YAPPC API

Usage:
  $0 [OPTIONS]

Options:
  --start-server    Start the API server before running tests (and stop after)
  --base-url URL    Override API base URL (default: http://localhost:8080)
  --help            Show this help message

Examples:
  # Run tests against already-running server
  $0

  # Start server, run tests, stop server
  $0 --start-server

  # Test against different URL
  $0 --base-url http://localhost:9090

Environment Variables:
  API_BASE_URL      Base URL for the API (default: http://localhost:8080)

Requirements:
  - schemathesis (pip install schemathesis)
  - curl
  - Running PostgreSQL database (if starting server)

EOF
}

main() {
    local start_server_flag=false
    
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --start-server)
                start_server_flag=true
                shift
                ;;
            --base-url)
                API_BASE_URL="$2"
                shift 2
                ;;
            --help)
                show_help
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    check_dependencies
    
    if [ "$start_server_flag" = true ]; then
        trap stop_server EXIT
        start_server
        wait_for_api || exit 1
    fi
    
    run_contract_tests
}

main "$@"
