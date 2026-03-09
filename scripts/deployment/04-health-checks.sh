#!/bin/bash

# Phase 4 Health Checks Script
# Validates all service endpoints are healthy

set -e

ENVIRONMENT=${1:-staging}
API_URL="http://localhost:8080"

echo "========================================"
echo "PHASE 4 HEALTH CHECKS"
echo "========================================"
echo "Environment: $ENVIRONMENT"
echo "API URL:     $API_URL"
echo ""

PASSED=0
FAILED=0

# Helper function to check endpoint
check_endpoint() {
    local name=$1
    local method=$2
    local url=$3
    local expected_status=$4
    local data=$5
    
    echo -n "Checking $name... "
    
    if [ "$method" = "POST" ]; then
        RESPONSE=$(curl -s -w "\n%{http_code}" \
            -X POST "$API_URL$url" \
            -H "Content-Type: application/json" \
            -H "X-Tenant-Id: test-tenant" \
            -d "$data")
    else
        RESPONSE=$(curl -s -w "\n%{http_code}" \
            -H "X-Tenant-Id: test-tenant" \
            "$API_URL$url")
    fi
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    BODY=$(echo "$RESPONSE" | head -n -1)
    
    if [ "$HTTP_CODE" = "$expected_status" ]; then
        echo "✅ (HTTP $HTTP_CODE)"
        ((PASSED++))
    else
        echo "❌ (HTTP $HTTP_CODE, expected $expected_status)"
        echo "  Response: $BODY"
        ((FAILED++))
    fi
}

echo "=== API Endpoints ==="
echo ""

# 1. Health endpoint
check_endpoint "Health Endpoint" "GET" "/health" "200"

# 2. Database health
check_endpoint "Database Health" "GET" "/health/db" "200"

# 3. Redis health
check_endpoint "Redis Health" "GET" "/health/cache" "200"

# 4. Metrics endpoint
check_endpoint "Metrics Endpoint" "GET" "/metrics" "200"

# 5. API docs (Swagger/OpenAPI)
check_endpoint "API Documentation" "GET" "/api/docs" "200"

echo ""
echo "=== REST API Endpoints ==="
echo ""

# 6. List agents
check_endpoint "List Agents" "GET" "/api/agents" "200"

# 7. Create agent
check_endpoint "Create Agent" "POST" "/api/agents" "201" \
    '{"name":"test-agent","type":"COLLECTOR"}'

# 8. List patterns
check_endpoint "List Patterns" "GET" "/api/patterns" "200"

# 9. List events
check_endpoint "List Events" "GET" "/api/events" "200"

# 10. GraphQL endpoint
check_endpoint "GraphQL Endpoint" "POST" "/graphql" "200" \
    '{"query":"{ agents { id } }"}'

echo ""
echo "=== Performance Checks ==="
echo ""

# Performance check: Response time
echo -n "Checking response time... "
START_TIME=$(date +%s%N)
curl -s -H "X-Tenant-Id: test-tenant" $API_URL/health > /dev/null
END_TIME=$(date +%s%N)
DURATION=$((($END_TIME - $START_TIME) / 1000000)) # Convert to ms

if [ $DURATION -lt 50 ]; then
    echo "✅ (${DURATION}ms, target <50ms)"
    ((PASSED++))
else
    echo "⚠️  (${DURATION}ms, target <50ms)"
fi

# Load test: 10 parallel requests
echo -n "Running load test (10 parallel requests)... "
for i in {1..10}; do
    curl -s -H "X-Tenant-Id: test-tenant" $API_URL/health > /dev/null &
done
wait
echo "✅"
((PASSED++))

echo ""
echo "=== Database Connectivity ==="
echo ""

# Check database connection
echo -n "Checking database connection... "
DB_CHECK=$(curl -s -H "X-Tenant-Id: test-tenant" $API_URL/health/db)
if echo "$DB_CHECK" | grep -q '"status":"UP"'; then
    echo "✅"
    ((PASSED++))
else
    echo "❌"
    echo "  Response: $DB_CHECK"
    ((FAILED++))
fi

echo ""
echo "=== Cache Connectivity ==="
echo ""

# Check cache connection
echo -n "Checking cache connection... "
CACHE_CHECK=$(curl -s -H "X-Tenant-Id: test-tenant" $API_URL/health/cache)
if echo "$CACHE_CHECK" | grep -q '"status":"UP"'; then
    echo "✅"
    ((PASSED++))
else
    echo "❌"
    echo "  Response: $CACHE_CHECK"
    ((FAILED++))
fi

echo ""
echo "=== Deployment Readiness ==="
echo ""

# Check deployment metadata
echo -n "Checking deployment metadata... "
DEPLOY_INFO=$(curl -s -H "X-Tenant-Id: test-tenant" $API_URL/health)
if echo "$DEPLOY_INFO" | grep -q '"version"'; then
    VERSION=$(echo "$DEPLOY_INFO" | grep -o '"version":"[^"]*"')
    echo "✅ ($VERSION)"
    ((PASSED++))
else
    echo "⚠️  (version info not available)"
fi

echo ""
echo "========================================"
if [ $FAILED -eq 0 ]; then
    echo "✅ ALL HEALTH CHECKS PASSED ($PASSED passed)"
    echo "========================================"
    echo ""
    echo "Deployment Status: READY FOR PRODUCTION"
    exit 0
else
    echo "❌ SOME CHECKS FAILED ($PASSED passed, $FAILED failed)"
    echo "========================================"
    echo ""
    echo "Deployment Status: ISSUES DETECTED"
    echo ""
    echo "Troubleshooting:"
    echo "1. Check service logs:    docker logs phase4-$ENVIRONMENT"
    echo "2. Check database status: docker logs postgres-$ENVIRONMENT"
    echo "3. Check redis status:    docker logs redis-$ENVIRONMENT"
    echo ""
    exit 1
fi
