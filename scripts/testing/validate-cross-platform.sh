#!/usr/bin/env bash

##############################################################################
# Cross-Platform Compatibility Validator for TutorPutor Startup Script
# 
# This script verifies that tutorputor-startup.sh will work on both
# macOS and Linux without platform-specific issues.
#
# Usage: ./scripts/validate-cross-platform.sh
##############################################################################

set +e  # Don't exit on individual command failures

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Counters
CHECKS_PASSED=0
CHECKS_FAILED=0
CHECKS_WARNED=0

print_header() {
    echo -e "\n${BLUE}=== $1 ===${NC}\n"
}

check_pass() {
    echo -e "${GREEN}✓${NC} $1"
    ((CHECKS_PASSED++))
}

check_fail() {
    echo -e "${RED}✗${NC} $1"
    ((CHECKS_FAILED++))
}

check_warn() {
    echo -e "${YELLOW}⚠${NC} $1"
    ((CHECKS_WARNED++))
}

print_header "Platform Detection"

# Detect OS
if [[ "$OSTYPE" == "darwin"* ]]; then
    DETECTED_OS="macOS"
    check_pass "Running on macOS ($(sw_vers -productVersion))"
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    DETECTED_OS="Linux"
    check_pass "Running on Linux ($(uname -r))"
else
    check_fail "Unknown OS: $OSTYPE"
    DETECTED_OS="Unknown"
fi

print_header "Required Commands"

# Check for required commands
REQUIRED_CMDS=("docker" "docker compose" "nc" "pgrep" "xargs" "grep" "sed" "awk" "sort" "uniq")

for cmd in "${REQUIRED_CMDS[@]}"; do
    if command -v $cmd &> /dev/null 2>&1; then
        check_pass "Command available: $cmd"
    else
        check_fail "Command NOT available: $cmd (required for cross-platform compatibility)"
    fi
done

print_header "Docker Configuration"

# Check Docker
if ! command -v docker &> /dev/null; then
    check_fail "Docker not installed"
else
    check_pass "Docker is installed"
    docker_version=$(docker --version)
    echo "  Version: $docker_version"
fi

# Check Docker Compose
if docker compose version &> /dev/null; then
    check_pass "Docker Compose v2 detected (docker compose)"
    compose_version=$(docker compose version 2>/dev/null | head -1)
    echo "  Version: $compose_version"
elif command -v docker-compose &> /dev/null; then
    check_warn "Using Docker Compose v1 (docker-compose) - v2 is preferred"
    compose_version=$(docker-compose --version 2>/dev/null || echo "unknown")
    echo "  Version: $compose_version"
else
    check_fail "Docker Compose not found (neither v1 nor v2)"
fi

# Check Docker daemon
if docker ps &>/dev/null; then
    check_pass "Docker daemon is responsive"
else
    check_warn "Docker daemon not responding (may need to start Docker Desktop)"
fi

# Check Docker socket paths (platform-specific)
print_header "Platform-Specific Paths"

if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS socket paths
    SOCKET1="$HOME/.docker/desktop/docker.sock"
    SOCKET2="/var/run/docker.sock"
    
    if [ -e "$SOCKET1" ]; then
        check_pass "Found Docker Desktop socket (macOS): $SOCKET1"
    elif [ -e "$SOCKET2" ]; then
        check_warn "Using standard Docker socket (macOS): $SOCKET2 (Docker Desktop socket not found)"
    else
        check_fail "Neither Docker Desktop socket nor standard socket found on macOS"
    fi
else
    # Linux socket paths
    SOCKET="/var/run/docker.sock"
    if [ -e "$SOCKET" ]; then
        check_pass "Found Docker socket (Linux): $SOCKET"
    else
        check_warn "Docker socket not found (Docker may not be running)"
    fi
fi

print_header "Script Syntax Analysis"

# Check for Linux-only commands in the startup script
SCRIPT_FILE="tutorputor-startup.sh"

if [ ! -f "$SCRIPT_FILE" ]; then
    check_fail "Startup script not found: $SCRIPT_FILE"
else
    check_pass "Startup script found: $SCRIPT_FILE"
    
    # Check for problematic patterns
    declare -A patterns=(
        ["systemctl execute without condition"]="^\s*systemctl"
        ["timeout command direct"]="^\s*timeout [0-9]"
        ["sudo systemctl execute"]="^\s*sudo systemctl"
        ["apt-get/yum direct execute"]="^\s*(?:apt-get|yum)\s+(?:install|remove)"
    )
    
    echo ""
    for pattern_name in "${!patterns[@]}"; do
        pattern="${patterns[$pattern_name]}"
        if grep -E "$pattern" "$SCRIPT_FILE" > /dev/null 2>&1; then
            check_warn "Found pattern '$pattern_name' in script (verify it's in conditional block)"
            grep -n "$pattern" "$SCRIPT_FILE" | head -3
        else
            check_pass "No direct execution of platform-specific command: $pattern_name"
        fi
    done
fi

print_header "NetCat Compatibility"

# Test netcat port checking (used in wait_for_port function)
if command -v nc &> /dev/null; then
    nc_version=$(nc -h 2>&1 | head -1 || echo "unknown")
    check_pass "netcat available: $nc_version"
    
    # Test on localhost (should fail quickly if nothing is listening)
    if nc -z localhost 65432 &>/dev/null; then
        check_warn "Port 65432 appears to be in use (unexpected)"
    else
        check_pass "Port checking works (tested port 65432)"
    fi
else
    check_fail "netcat (nc) not available - needed for port checking"
fi

print_header "Node.js & Package Manager"

if command -v node &> /dev/null; then
    node_version=$(node --version)
    check_pass "Node.js available: $node_version"
else
    check_fail "Node.js not installed"
fi

if command -v pnpm &> /dev/null; then
    pnpm_version=$(pnpm --version)
    check_pass "pnpm available: v$pnpm_version"
else
    check_warn "pnpm not installed (may need to install: npm install -g pnpm)"
fi

print_header "Database Tools"

if command -v psql &> /dev/null; then
    psql_version=$(psql --version)
    check_pass "PostgreSQL client available: $psql_version"
else
    check_warn "PostgreSQL client (psql) not available (optional, used for direct DB access)"
fi

print_header "Environment Configuration"

# Check for .env file
if [ -f ".env" ]; then
    check_pass ".env file exists"
    # Don't show contents for security
    line_count=$(wc -l < .env)
    echo "  Lines: $line_count"
else
    check_warn ".env file not found (will be created during startup)"
fi

# Check for docker-compose.tutorputor.yml
if [ -f "docker-compose.tutorputor.yml" ]; then
    check_pass "Docker Compose config found: docker-compose.tutorputor.yml"
else
    check_fail "Docker Compose config not found: docker-compose.tutorputor.yml"
fi

print_header "Script Permissions"

if [ -x "$SCRIPT_FILE" ]; then
    check_pass "Startup script is executable"
else
    check_fail "Startup script is NOT executable (run: chmod +x $SCRIPT_FILE)"
fi

print_header "Summary Report"

TOTAL_CHECKS=$((CHECKS_PASSED + CHECKS_FAILED + CHECKS_WARNED))
echo "Detected OS: ${BLUE}${DETECTED_OS}${NC}"
echo "Total Checks: ${TOTAL_CHECKS}"
echo -e "  ${GREEN}Passed: ${CHECKS_PASSED}${NC}"
echo -e "  ${YELLOW}Warnings: ${CHECKS_WARNED}${NC}"
echo -e "  ${RED}Failed: ${CHECKS_FAILED}${NC}"

echo ""
if [ "$CHECKS_FAILED" -eq 0 ]; then
    echo -e "${GREEN}✓ All critical checks passed!${NC}"
    echo -e "The startup script should work on both macOS and Linux."
    exit 0
else
    echo -e "${RED}✗ Some checks failed - fix issues before running startup script${NC}"
    exit 1
fi
