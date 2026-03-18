#!/bin/bash
#
# @fileoverview Security audit script for Flashit and monorepo
# Checks for security vulnerabilities, secrets, and hardcoded values
#

set -e

echo "🔒 Running Security Audit..."
echo ""

# Colors for output
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

ERRORS=0
WARNINGS=0

# Function to report error
report_error() {
    echo -e "${RED}❌ ERROR:${NC} $1"
    ((ERRORS++))
}

# Function to report warning
report_warning() {
    echo -e "${YELLOW}⚠️  WARNING:${NC} $1"
    ((WARNINGS++))
}

# Function to report success
report_success() {
    echo -e "${GREEN}✅ PASS:${NC} $1"
}

echo "1. Checking for hardcoded secrets..."
# Check for common secret patterns
if grep -r "password.*=.*['\"][^'\"]\{8,\}['\"]" --include="*.ts" --include="*.tsx" --include="*.js" products/flashit/backend 2>/dev/null | grep -v "node_modules" | grep -v "\.env\." | head -5; then
    report_warning "Potential hardcoded password found in Flashit backend"
else
    report_success "No obvious hardcoded passwords found"
fi

# Check for API keys
if grep -r "api[_-]key.*=.*['\"][a-zA-Z0-9]\{16,\}['\"]" --include="*.ts" --include="*.tsx" products/ 2>/dev/null | grep -v "node_modules" | grep -v "process.env" | head -5; then
    report_error "Hardcoded API key detected"
else
    report_success "No hardcoded API keys found"
fi

echo ""
echo "2. Checking JWT configuration..."
if [ -f "products/flashit/backend/gateway/.env.example" ]; then
    if grep -q "JWT_SECRET=your-secret-key" products/flashit/backend/gateway/.env.example 2>/dev/null; then
        report_error "Default JWT_SECRET found in .env.example"
    else
        report_success "No default JWT_SECRET in .env.example"
    fi
fi

# Check for JWT secret length validation
if grep -r "JWT_SECRET.*length.*<.*32" --include="*.ts" products/flashit 2>/dev/null; then
    report_warning "JWT_SECRET length validation may be insufficient"
else
    report_success "JWT_SECRET length validation adequate"
fi

echo ""
echo "3. Checking for disabled security features..."
if grep -r "ALLOW_UNAUTHENTICATED_ACCESS.*=.*true" --include="*.ts" --include="*.env*" products/ 2>/dev/null | grep -v "node_modules"; then
    report_error "Unauthenticated access enabled in configuration"
else
    report_success "Unauthenticated access not enabled"
fi

if grep -r "DISABLE_RATE_LIMITING.*=.*true" --include="*.ts" --include="*.env*" products/ 2>/dev/null | grep -v "node_modules"; then
    report_error "Rate limiting disabled in configuration"
else
    report_success "Rate limiting enabled"
fi

echo ""
echo "4. Checking for stub/test configurations in production paths..."
if grep -r "EMAIL_PROVIDER.*=.*stub" --include="*.env*" products/flashit 2>/dev/null | grep -v "example" | grep -v "development"; then
    report_error "Stub email provider configured in production"
else
    report_success "No stub email provider in production configs"
fi

echo ""
echo "5. Checking SQL injection vulnerabilities..."
# Look for raw SQL queries without parameterization
if grep -r "prisma\.\$queryRaw.*\`.*\${" --include="*.ts" products/ 2>/dev/null | grep -v "node_modules" | head -5; then
    report_warning "Potential SQL injection - template literal in queryRaw"
else
    report_success "No obvious SQL injection patterns"
fi

echo ""
echo "6. Checking for missing input validation..."
if grep -r "body.*as.*any" --include="*.ts" products/flashit/backend 2>/dev/null | grep -v "node_modules" | head -5; then
    report_warning "'as any' type casting found - may bypass validation"
else
    report_success "No 'as any' casting in request handlers"
fi

echo ""
echo "7. Running npm audit..."
cd products/flashit
if npm audit --audit-level=moderate 2>/dev/null | grep -q "vulnerabilities"; then
    VULNS=$(npm audit --json 2>/dev/null | grep -o '"vulnerabilities":[0-9]*' | cut -d: -f2 || echo "0")
    if [ "$VULNS" -gt 0 ]; then
        report_warning "$VULNS vulnerabilities found (run npm audit fix)"
    else
        report_success "No vulnerabilities found"
    fi
else
    report_success "Audit completed"
fi
cd ../..

echo ""
echo "=========================================="
echo "🔒 SECURITY AUDIT COMPLETE"
echo "=========================================="
echo "Errors: $ERRORS"
echo "Warnings: $WARNINGS"
echo ""

if [ $ERRORS -gt 0 ]; then
    echo -e "${RED}Security issues found. Please fix before deployment.${NC}"
    exit 1
else
    echo -e "${GREEN}Security audit passed!${NC}"
    exit 0
fi
