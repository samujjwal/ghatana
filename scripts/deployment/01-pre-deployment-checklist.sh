# Phase 4 Staging Deployment Guide
## November 18-19, 2025 | Production-Ready Deployment

---

# DEPLOYMENT STRATEGY

## Overview
- **Environment**: Staging (pre-production testing)
- **Deployment Type**: Blue-Green (zero downtime)
- **Rollback**: Automatic if error rate >5%
- **Duration**: 15-20 minutes
- **Team**: 2 engineers minimum (1 deployment, 1 monitoring)

## Pre-Deployment Checklist (30 min)

```bash
#!/bin/bash
set -e

echo "========================================"
echo "PRE-DEPLOYMENT CHECKLIST"
echo "========================================"

# 1. Run all unit tests
echo "1. Running unit tests..."
./gradlew test --tests "Webhook*" --tests "Agent*"
if [ $? -ne 0 ]; then
    echo "❌ Unit tests failed"
    exit 1
fi
echo "✅ Unit tests passed"

# 2. Run integration tests
echo "2. Running integration tests..."
./gradlew integrationTest
if [ $? -ne 0 ]; then
    echo "❌ Integration tests failed"
    exit 1
fi
echo "✅ Integration tests passed"

# 3. Check code coverage
echo "3. Checking code coverage..."
./gradlew jacocoTestReport
COVERAGE=$(grep -oP 'LINE\">(\d+)%' build/reports/jacoco/test/html/index.html | head -1 | grep -oP '\d+')
if [ $COVERAGE -lt 50 ]; then
    echo "❌ Code coverage too low: ${COVERAGE}%"
    exit 1
fi
echo "✅ Code coverage OK: ${COVERAGE}%"

# 4. Security scan
echo "4. Running security scan..."
./gradlew dependencyCheckAnalyze
if grep -q "CRITICAL\|HIGH" build/reports/dependency-check-report.json; then
    echo "❌ Security vulnerabilities found"
    exit 1
fi
echo "✅ Security scan passed"

# 5. Check database migrations
echo "5. Verifying database migrations..."
./gradlew flywayInfo
if grep -q "PENDING" flyway_info.txt; then
    echo "❌ Pending migrations found"
    exit 1
fi
echo "✅ All migrations applied"

# 6. Code style check
echo "6. Checking code style..."
./gradlew spotlessCheck
if [ $? -ne 0 ]; then
    echo "⚠️  Code style issues found"
    ./gradlew spotlessApply
    echo "✅ Code style fixed"
fi
echo "✅ Code style OK"

# 7. Architecture validation
echo "7. Validating architecture..."
./gradlew :testing:architecture-tests:test
if [ $? -ne 0 ]; then
    echo "❌ Architecture violations found"
    exit 1
fi
echo "✅ Architecture valid"

# 8. Build distribution
echo "8. Building distribution..."
./gradlew clean build -x test
if [ $? -ne 0 ]; then
    echo "❌ Build failed"
    exit 1
fi
echo "✅ Build successful"

echo ""
echo "========================================"
echo "✅ ALL PRE-DEPLOYMENT CHECKS PASSED"
echo "========================================"
echo ""
echo "Ready for deployment!"
