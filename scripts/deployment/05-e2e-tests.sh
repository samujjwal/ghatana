#!/bin/bash

# Phase 4 E2E Tests Script
# Comprehensive end-to-end workflow validation

set -e

ENVIRONMENT=${1:-staging}
API_URL="http://localhost:8080"

echo "========================================"
echo "PHASE 4 END-TO-END TESTS"
echo "========================================"
echo "Environment: $ENVIRONMENT"
echo "API URL:     $API_URL"
echo ""

PASSED=0
FAILED=0
TENANT_ID="e2e-test-$(date +%s)"

# Helper function for tests
run_test() {
    local test_name=$1
    local test_func=$2
    
    echo -n "Running: $test_name... "
    if $test_func; then
        echo "✅"
        ((PASSED++))
    else
        echo "❌"
        ((FAILED++))
    fi
}

# Test 1: Create Agent
test_create_agent() {
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST $API_URL/api/agents \
        -H "Content-Type: application/json" \
        -H "X-Tenant-Id: $TENANT_ID" \
        -d '{
            "name": "e2e-agent",
            "type": "COLLECTOR",
            "description": "E2E test agent"
        }')
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    BODY=$(echo "$RESPONSE" | head -n -1)
    
    if [ "$HTTP_CODE" != "201" ]; then
        return 1
    fi
    
    AGENT_ID=$(echo "$BODY" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
    if [ -z "$AGENT_ID" ]; then
        return 1
    fi
    
    echo "$AGENT_ID" > /tmp/e2e_agent_id.txt
    return 0
}

# Test 2: List Agents
test_list_agents() {
    RESPONSE=$(curl -s -w "\n%{http_code}" $API_URL/api/agents \
        -H "X-Tenant-Id: $TENANT_ID")
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    BODY=$(echo "$RESPONSE" | head -n -1)
    
    if [ "$HTTP_CODE" != "200" ]; then
        return 1
    fi
    
    # Verify agent exists in list
    if ! echo "$BODY" | grep -q "e2e-agent"; then
        return 1
    fi
    
    return 0
}

# Test 3: Get Single Agent
test_get_agent() {
    if [ ! -f /tmp/e2e_agent_id.txt ]; then
        return 1
    fi
    
    AGENT_ID=$(cat /tmp/e2e_agent_id.txt)
    RESPONSE=$(curl -s -w "\n%{http_code}" $API_URL/api/agents/$AGENT_ID \
        -H "X-Tenant-Id: $TENANT_ID")
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    BODY=$(echo "$RESPONSE" | head -n -1)
    
    if [ "$HTTP_CODE" != "200" ]; then
        return 1
    fi
    
    if ! echo "$BODY" | grep -q "e2e-agent"; then
        return 1
    fi
    
    return 0
}

# Test 4: Update Agent
test_update_agent() {
    if [ ! -f /tmp/e2e_agent_id.txt ]; then
        return 1
    fi
    
    AGENT_ID=$(cat /tmp/e2e_agent_id.txt)
    RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT $API_URL/api/agents/$AGENT_ID \
        -H "Content-Type: application/json" \
        -H "X-Tenant-Id: $TENANT_ID" \
        -d '{
            "name": "updated-e2e-agent",
            "description": "Updated via E2E test"
        }')
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    
    if [ "$HTTP_CODE" != "200" ]; then
        return 1
    fi
    
    return 0
}

# Test 5: Create Pattern
test_create_pattern() {
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST $API_URL/api/patterns \
        -H "Content-Type: application/json" \
        -H "X-Tenant-Id: $TENANT_ID" \
        -d '{
            "name": "e2e-pattern",
            "description": "E2E test pattern",
            "rule": "(event.type = \"login\") -> (event.type = \"transaction\") WITHIN 5m"
        }')
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    BODY=$(echo "$RESPONSE" | head -n -1)
    
    if [ "$HTTP_CODE" != "201" ]; then
        return 1
    fi
    
    PATTERN_ID=$(echo "$BODY" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
    if [ -z "$PATTERN_ID" ]; then
        return 1
    fi
    
    echo "$PATTERN_ID" > /tmp/e2e_pattern_id.txt
    return 0
}

# Test 6: List Patterns
test_list_patterns() {
    RESPONSE=$(curl -s -w "\n%{http_code}" $API_URL/api/patterns \
        -H "X-Tenant-Id: $TENANT_ID")
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    BODY=$(echo "$RESPONSE" | head -n -1)
    
    if [ "$HTTP_CODE" != "200" ]; then
        return 1
    fi
    
    if ! echo "$BODY" | grep -q "e2e-pattern"; then
        return 1
    fi
    
    return 0
}

# Test 7: Create Event
test_create_event() {
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST $API_URL/api/events \
        -H "Content-Type: application/json" \
        -H "X-Tenant-Id: $TENANT_ID" \
        -d '{
            "type": "login",
            "data": {
                "userId": "user123",
                "timestamp": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"
            }
        }')
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    BODY=$(echo "$RESPONSE" | head -n -1)
    
    if [ "$HTTP_CODE" != "201" ] && [ "$HTTP_CODE" != "202" ]; then
        return 1
    fi
    
    return 0
}

# Test 8: Query Events
test_query_events() {
    RESPONSE=$(curl -s -w "\n%{http_code}" $API_URL/api/events \
        -H "X-Tenant-Id: $TENANT_ID")
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    
    if [ "$HTTP_CODE" != "200" ]; then
        return 1
    fi
    
    return 0
}

# Test 9: GraphQL Query
test_graphql_query() {
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST $API_URL/graphql \
        -H "Content-Type: application/json" \
        -H "X-Tenant-Id: $TENANT_ID" \
        -d '{
            "query": "{ agents { id name } patterns { id name } }"
        }')
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    BODY=$(echo "$RESPONSE" | head -n -1)
    
    if [ "$HTTP_CODE" != "200" ]; then
        return 1
    fi
    
    if echo "$BODY" | grep -q '"errors"'; then
        return 1
    fi
    
    return 0
}

# Test 10: Delete Agent
test_delete_agent() {
    if [ ! -f /tmp/e2e_agent_id.txt ]; then
        return 1
    fi
    
    AGENT_ID=$(cat /tmp/e2e_agent_id.txt)
    RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE $API_URL/api/agents/$AGENT_ID \
        -H "X-Tenant-Id: $TENANT_ID")
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    
    if [ "$HTTP_CODE" != "204" ] && [ "$HTTP_CODE" != "200" ]; then
        return 1
    fi
    
    return 0
}

# Test 11: Verify Agent Deleted
test_verify_agent_deleted() {
    if [ ! -f /tmp/e2e_agent_id.txt ]; then
        return 1
    fi
    
    AGENT_ID=$(cat /tmp/e2e_agent_id.txt)
    RESPONSE=$(curl -s -w "\n%{http_code}" $API_URL/api/agents/$AGENT_ID \
        -H "X-Tenant-Id: $TENANT_ID")
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    
    # Should return 404 after deletion
    if [ "$HTTP_CODE" != "404" ]; then
        return 1
    fi
    
    return 0
}

# Test 12: Multi-tenant Isolation
test_multi_tenant_isolation() {
    OTHER_TENANT="other-tenant-$(date +%s)"
    
    # Create agent in first tenant
    RESPONSE1=$(curl -s -X POST $API_URL/api/agents \
        -H "Content-Type: application/json" \
        -H "X-Tenant-Id: $TENANT_ID" \
        -d '{"name":"tenant1-agent","type":"COLLECTOR"}')
    
    # Try to access with different tenant
    RESPONSE2=$(curl -s -w "\n%{http_code}" $API_URL/api/agents \
        -H "X-Tenant-Id: $OTHER_TENANT")
    
    HTTP_CODE=$(echo "$RESPONSE2" | tail -n 1)
    BODY=$(echo "$RESPONSE2" | head -n -1)
    
    # Should NOT see first tenant's agent
    if echo "$BODY" | grep -q "tenant1-agent"; then
        return 1
    fi
    
    return 0
}

# Test 13: Error Handling
test_error_handling() {
    # Try to get non-existent agent
    RESPONSE=$(curl -s -w "\n%{http_code}" $API_URL/api/agents/invalid-id \
        -H "X-Tenant-Id: $TENANT_ID")
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    
    if [ "$HTTP_CODE" != "404" ]; then
        return 1
    fi
    
    return 0
}

# Run all tests
echo "=== Agent Operations ==="
run_test "Create Agent" test_create_agent
run_test "List Agents" test_list_agents
run_test "Get Single Agent" test_get_agent
run_test "Update Agent" test_update_agent

echo ""
echo "=== Pattern Operations ==="
run_test "Create Pattern" test_create_pattern
run_test "List Patterns" test_list_patterns

echo ""
echo "=== Event Operations ==="
run_test "Create Event" test_create_event
run_test "Query Events" test_query_events

echo ""
echo "=== GraphQL ==="
run_test "GraphQL Query" test_graphql_query

echo ""
echo "=== Cleanup Operations ==="
run_test "Delete Agent" test_delete_agent
run_test "Verify Agent Deleted" test_verify_agent_deleted

echo ""
echo "=== Advanced Tests ==="
run_test "Multi-tenant Isolation" test_multi_tenant_isolation
run_test "Error Handling" test_error_handling

# Cleanup
rm -f /tmp/e2e_agent_id.txt /tmp/e2e_pattern_id.txt

echo ""
echo "========================================"
if [ $FAILED -eq 0 ]; then
    echo "✅ ALL E2E TESTS PASSED ($PASSED passed)"
    echo "========================================"
    echo ""
    echo "Deployment Status: PRODUCTION READY"
    exit 0
else
    echo "❌ SOME E2E TESTS FAILED ($PASSED passed, $FAILED failed)"
    echo "========================================"
    echo ""
    echo "Troubleshooting:"
    echo "1. Check service logs:    docker logs phase4-$ENVIRONMENT"
    echo "2. Verify deployment:     ./scripts/04-health-checks.sh"
    echo "3. Check API response:    curl -v http://localhost:8080/health"
    echo ""
    exit 1
fi
