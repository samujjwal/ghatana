#!/bin/bash

# Test script for Observability API endpoints
TENANT_ID="7146cb95-180c-485f-9ca4-5adf5dcaf7c4"
BASE_URL="http://localhost:3101/api/v1/observe"

echo "🧪 Testing Observability REST API"
echo "=================================="
echo ""

# Test 1: Get alerts with pagination
echo "📋 Test 1: GET /alerts (pagination)"
curl -s "${BASE_URL}/alerts?tenantId=${TENANT_ID}&page=1&pageSize=3" | jq '{
  total: .total,
  page: .page,
  pageSize: .pageSize,
  alertCount: (.data | length),
  alerts: .data | map({severity, status, title})
}'
echo ""

# Test 2: Filter alerts by severity
echo "🔴 Test 2: GET /alerts (filter by severity=critical)"
curl -s "${BASE_URL}/alerts?tenantId=${TENANT_ID}&severity=critical" | jq '{
  total: .total,
  criticalAlerts: .data | map({title, source})
}'
echo ""

# Test 3: Filter alerts by status
echo "✅ Test 3: GET /alerts (filter by status=resolved)"
curl -s "${BASE_URL}/alerts?tenantId=${TENANT_ID}&status=resolved" | jq '{
  total: .total,
  resolvedAlerts: .data | map({title, resolvedBy, resolvedAt})
}'
echo ""

# Test 4: Get logs with cursor pagination
echo "📝 Test 4: GET /logs (cursor pagination)"
curl -s "${BASE_URL}/logs?tenantId=${TENANT_ID}&limit=5" | jq '{
  logCount: (.data | length),
  hasMore: .hasMore,
  logs: .data | map({level, source, message: (.message | .[0:50])})
}'
echo ""

# Test 5: Search logs
echo "🔍 Test 5: GET /logs (search for 'error')"
curl -s "${BASE_URL}/logs?tenantId=${TENANT_ID}&search=error&limit=3" | jq '{
  logCount: (.data | length),
  errorLogs: .data | map({level, message})
}'
echo ""

# Test 6: Get log sources
echo "📊 Test 6: GET /logs/sources"
curl -s "${BASE_URL}/logs/sources?tenantId=${TENANT_ID}" | jq '.'
echo ""

# Test 7: Get first alert ID for acknowledge/resolve tests
ALERT_ID=$(curl -s "${BASE_URL}/alerts?tenantId=${TENANT_ID}&status=active&page=1&pageSize=1" | jq -r '.data[0].id')

if [ "$ALERT_ID" != "null" ] && [ -n "$ALERT_ID" ]; then
    echo "✍️  Test 7: POST /alerts/${ALERT_ID}/acknowledge"
    curl -s -X POST "${BASE_URL}/alerts/${ALERT_ID}/acknowledge?tenantId=${TENANT_ID}" | jq '{
      id: .id,
      status: .status,
      acknowledgedAt: .acknowledgedAt,
      acknowledgedBy: .acknowledgedBy
    }'
    echo ""
    
    echo "✅ Test 8: POST /alerts/${ALERT_ID}/resolve"
    curl -s -X POST "${BASE_URL}/alerts/${ALERT_ID}/resolve?tenantId=${TENANT_ID}" | jq '{
      id: .id,
      status: .status,
      resolvedAt: .resolvedAt,
      resolvedBy: .resolvedBy
    }'
    echo ""
else
    echo "⚠️  No active alerts found for acknowledge/resolve tests"
fi

echo "=================================="
echo "✨ API Testing Complete!"
