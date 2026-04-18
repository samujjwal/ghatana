#!/bin/bash
#
# TutorPutor Security Audit Script
#
# Performs comprehensive security checks on the TutorPutor codebase.
# Includes dependency vulnerability scanning, secret detection, and code analysis.
#
# Usage:
#   ./security-audit.sh
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
REPORT_DIR="${PROJECT_ROOT}/security-reports"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT_FILE="${REPORT_DIR}/security-audit_${TIMESTAMP}.txt"

# Create report directory
mkdir -p "${REPORT_DIR}"

# Logging function
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "${REPORT_FILE}"
}

log_section() {
    echo -e "\n${GREEN}=== $1 ===${NC}" | tee -a "${REPORT_FILE}"
}

log_error() {
    echo -e "${RED}[ERROR] $1${NC}" | tee -a "${REPORT_FILE}"
}

log_warning() {
    echo -e "${YELLOW}[WARNING] $1${NC}" | tee -a "${REPORT_FILE}"
}

log "Starting TutorPutor Security Audit"
log_section "1. Dependency Vulnerability Scan"

# Check for npm audit
cd "${PROJECT_ROOT}"
if [ -f "package.json" ]; then
    log "Running npm audit..."
    npm audit --json > "${REPORT_DIR}/npm-audit.json" 2>&1 || true
    VULNERABILITIES=$(cat "${REPORT_DIR}/npm-audit.json" | jq -r '.metadata.vulnerabilities.total // 0' 2>/dev/null || echo "0")
    
    if [ "$VULNERABILITIES" -gt 0 ]; then
        log_error "Found $VULNERABILITIES vulnerabilities in npm dependencies"
        log "Run 'npm audit fix' to address automatically fixable issues"
    else
        log "✓ No npm vulnerabilities found"
    fi
fi

# Check for pnpm audit (monorepo)
if [ -f "pnpm-lock.yaml" ]; then
    log "Running pnpm audit..."
    pnpm audit --json > "${REPORT_DIR}/pnpm-audit.json" 2>&1 || true
    VULNERABILITIES=$(cat "${REPORT_DIR}/pnpm-audit.json" | jq -r '.metadata.vulnerabilities.total // 0' 2>/dev/null || echo "0")
    
    if [ "$VULNERABILITIES" -gt 0 ]; then
        log_error "Found $VULNERABILITIES vulnerabilities in pnpm dependencies"
        log "Run 'pnpm audit fix' to address automatically fixable issues"
    else
        log "✓ No pnpm vulnerabilities found"
    fi
fi

log_section "2. Secret Detection Scan"

# Scan for potential secrets in code
log "Scanning for hardcoded secrets..."
SECRET_PATTERNS=(
    "password\s*=\s*['\"]"
    "api[_-]?key\s*=\s*['\"]"
    "secret\s*=\s*['\"]"
    "token\s*=\s*['\"]"
    "aws[_-]?access[_-]?key[_-]?id"
    "private[_-]?key"
)

FOUND_SECRETS=0
for pattern in "${SECRET_PATTERNS[@]}"; do
    MATCHES=$(rg -i "$pattern" --type-not yarn --type-not lock --type-not json 2>/dev/null || true)
    if [ -n "$MATCHES" ]; then
        log_error "Potential secrets found matching pattern: $pattern"
        echo "$MATCHES" >> "${REPORT_FILE}"
        FOUND_SECRETS=$((FOUND_SECRETS + 1))
    fi
done

if [ "$FOUND_SECRETS" -eq 0 ]; then
    log "✓ No hardcoded secrets detected"
else
    log_warning "Found $FOUND_SECRETS potential secret patterns (review required)"
fi

log_section "3. Code Security Analysis"

# Check for common security issues
log "Checking for common security anti-patterns..."

# Check for eval() usage
EVAL_USAGE=$(rg "eval\(" --type js --type ts 2>/dev/null || true)
if [ -n "$EVAL_USAGE" ]; then
    log_warning "Found eval() usage (security risk):"
    echo "$EVAL_USAGE" >> "${REPORT_FILE}"
fi

# Check for innerHTML usage
INNERHTML_USAGE=$(rg "innerHTML\s*=" --type js --type ts 2>/dev/null || true)
if [ -n "$INNERHTML_USAGE" ]; then
    log_warning "Found innerHTML usage (XSS risk):"
    echo "$INNERHTML_USAGE" >> "${REPORT_FILE}"
fi

# Check for dangerous regex
DANGEROUS_REGEX=$(rg "new RegExp\(\s*\*" --type js --type ts 2>/dev/null || true)
if [ -n "$DANGEROUS_REGEX" ]; then
    log_warning "Found potentially dangerous regex (ReDoS risk):"
    echo "$DANGEROUS_REGEX" >> "${REPORT_FILE}"
fi

log_section "4. Configuration Security Check"

# Check for insecure configurations
log "Checking configuration files..."

# Check for debug mode enabled
if rg -i "debug.*true" --type json 2>/dev/null; then
    log_warning "Debug mode may be enabled in production configs"
fi

# Check for exposed credentials in config
if rg -i "password|secret|key" config/ 2>/dev/null; then
    log_error "Potential credentials found in config files"
fi

log_section "5. File Permissions Check"

# Check for overly permissive file permissions
log "Checking file permissions..."
INSECURE_FILES=$(find . -type f -perm /o+rw 2>/dev/null | head -20 || true)
if [ -n "$INSECURE_FILES" ]; then
    log_warning "Found files with world-writable permissions:"
    echo "$INSECURE_FILES" >> "${REPORT_FILE}"
fi

log_section "6. Summary"

# Generate summary
log "Security Audit Summary:"
log "======================"
log "Report saved to: ${REPORT_FILE}"
log "NPM vulnerabilities: $(cat "${REPORT_DIR}/npm-audit.json" | jq -r '.metadata.vulnerabilities.total // 0' 2>/dev/null || echo "N/A")"
log "PNPM vulnerabilities: $(cat "${REPORT_DIR}/pnpm-audit.json" | jq -r '.metadata.vulnerabilities.total // 0' 2>/dev/null || echo "N/A")"
log "Potential secrets: $FOUND_SECRETS"

log ""
log "Recommendations:"
log "1. Review and fix all dependency vulnerabilities"
log "2. Remove or secure any hardcoded secrets"
log "3. Address code security anti-patterns"
log "4. Review configuration files for exposed credentials"
log "5. Fix insecure file permissions"

log "Security audit completed"

exit 0
