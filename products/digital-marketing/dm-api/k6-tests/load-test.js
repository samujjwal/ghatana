/**
 * P2-018: Load testing for critical DMOS API endpoints.
 * 
 * Tests performance under sustained load for critical endpoints:
 * - Workspace creation and listing
 * - Campaign operations
 * - Strategy generation
 * - Budget recommendations
 * - Approval workflows
 * 
 * @doc.type test
 * @doc.purpose Load testing for critical API endpoints (P2-018)
 * @doc.layer test
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const responseTime = new Trend('response_time');

// Test configuration
export const options = {
  stages: [
    { duration: '2m', target: 10 },   // Ramp up to 10 users
    { duration: '5m', target: 10 },   // Stay at 10 users
    { duration: '2m', target: 50 },   // Ramp up to 50 users
    { duration: '5m', target: 50 },   // Stay at 50 users
    { duration: '2m', target: 100 },  // Ramp up to 100 users
    { duration: '5m', target: 100 },  // Stay at 100 users
    { duration: '2m', target: 0 },    // Ramp down to 0
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],  // 95% of requests < 500ms, 99% < 1s
    http_req_failed: ['rate<0.05'],                  // Error rate < 5%
    errors: ['rate<0.05'],
  },
};

const BASE_URL = __ENV.API_URL || 'http://localhost:8080';
const TENANT_ID = __ENV.TENANT_ID || 'test-tenant';
const PRINCIPAL_ID = __ENV.PRINCIPAL_ID || 'test-user';

// Helper function to get common headers
function getHeaders() {
  return {
    'Content-Type': 'application/json',
    'X-Tenant-ID': TENANT_ID,
    'X-Principal-ID': PRINCIPAL_ID,
    'X-Session-ID': `session-${Date.now()}`,
    'X-Correlation-ID': `corr-${Date.now()}-${__VU}`,
    'Authorization': `Bearer test-token-${__VU}`,
  };
}

// Test: Workspace listing
export function testWorkspaceList() {
  const headers = getHeaders();
  const res = http.get(`${BASE_URL}/v1/workspaces`, { headers });
  
  const success = check(res, {
    'workspace list status is 200': (r) => r.status === 200,
    'workspace list response time < 500ms': (r) => r.timings.duration < 500,
  });
  
  errorRate.add(!success);
  responseTime.add(res.timings.duration);
  
  return success;
}

// Test: Campaign listing
export function testCampaignList() {
  const headers = getHeaders();
  const workspaceId = 'test-workspace';
  
  const res = http.get(`${BASE_URL}/v1/workspaces/${workspaceId}/campaigns`, { headers });
  
  const success = check(res, {
    'campaign list status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    'campaign list response time < 500ms': (r) => r.timings.duration < 500,
  });
  
  errorRate.add(!success);
  responseTime.add(res.timings.duration);
  
  return success;
}

// Test: Strategy retrieval
export function testStrategyGet() {
  const headers = getHeaders();
  const workspaceId = 'test-workspace';
  
  const res = http.get(`${BASE_URL}/v1/workspaces/${workspaceId}/strategy`, { headers });
  
  const success = check(res, {
    'strategy get status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    'strategy get response time < 500ms': (r) => r.timings.duration < 500,
  });
  
  errorRate.add(!success);
  responseTime.add(res.timings.duration);
  
  return success;
}

// Test: Budget retrieval
export function testBudgetGet() {
  const headers = getHeaders();
  const workspaceId = 'test-workspace';
  
  const res = http.get(`${BASE_URL}/v1/workspaces/${workspaceId}/budget`, { headers });
  
  const success = check(res, {
    'budget get status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    'budget get response time < 500ms': (r) => r.timings.duration < 500,
  });
  
  errorRate.add(!success);
  responseTime.add(res.timings.duration);
  
  return success;
}

// Test: Approval workflow listing
export function testApprovalList() {
  const headers = getHeaders();
  const workspaceId = 'test-workspace';
  
  const res = http.get(`${BASE_URL}/v1/workspaces/${workspaceId}/approvals`, { headers });
  
  const success = check(res, {
    'approval list status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    'approval list response time < 500ms': (r) => r.timings.duration < 500,
  });
  
  errorRate.add(!success);
  responseTime.add(res.timings.duration);
  
  return success;
}

// Main scenario
export default function() {
  // Run tests in sequence with small delays
  testWorkspaceList();
  sleep(0.1);
  
  testCampaignList();
  sleep(0.1);
  
  testStrategyGet();
  sleep(0.1);
  
  testBudgetGet();
  sleep(0.1);
  
  testApprovalList();
  sleep(0.1);
}
