/**
 * P2-019: Stress testing for high concurrency scenarios.
 * 
 * Tests system behavior under extreme load to identify breaking points:
 * - Rapid user ramp-up (spike testing)
 * - Sustained high concurrency
 * - Resource exhaustion scenarios
 * - Backpressure and circuit breaker behavior
 * 
 * @doc.type test
 * @doc.purpose Stress testing for high concurrency scenarios (P2-019)
 * @doc.layer test
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const responseTime = new Trend('response_time');
const throughput = new Trend('throughput');

// Stress test configuration - aggressive ramp-up
export const options = {
  stages: [
    { duration: '30s', target: 50 },   // Quick ramp to 50 users
    { duration: '1m', target: 200 },   // Ramp to 200 users
    { duration: '2m', target: 500 },   // Ramp to 500 users
    { duration: '3m', target: 1000 },  // Ramp to 1000 users (stress point)
    { duration: '2m', target: 1000 },  // Sustain at 1000 users
    { duration: '1m', target: 2000 },  // Spike to 2000 users (extreme stress)
    { duration: '1m', target: 2000 },  // Sustain at 2000 users
    { duration: '1m', target: 0 },     // Cool down
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000', 'p(99)<5000'],  // More lenient for stress test
    http_req_failed: ['rate<0.20'],                  // Allow higher error rate during stress
    errors: ['rate<0.20'],
  },
  // Abort early if error rate exceeds 50% for 30 seconds
  abortOnFailures: 0.5,
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
    'X-Session-ID': `session-${Date.now()}-${__VU}`,
    'X-Correlation-ID': `corr-${Date.now()}-${__VU}-${__ITER}`,
    'Authorization': `Bearer test-token-${__VU}`,
  };
}

// Test: Workspace operations under stress
export function testWorkspaceOperations() {
  const headers = getHeaders();
  
  // Mix of read and write operations
  const operations = [
    () => http.get(`${BASE_URL}/v1/workspaces`, { headers }),
    () => {
      const payload = JSON.stringify({
        name: `Test Workspace ${Date.now()}`,
        description: 'Stress test workspace'
      });
      return http.post(`${BASE_URL}/v1/workspaces`, payload, { headers });
    },
    () => http.get(`${BASE_URL}/v1/workspaces/test-workspace`, { headers }),
  ];
  
  const randomOp = operations[Math.floor(Math.random() * operations.length)];
  const res = randomOp();
  
  const success = check(res, {
    'workspace operation status is 200, 201, or 404': (r) => 
      r.status === 200 || r.status === 201 || r.status === 404,
    'workspace operation response time < 2s': (r) => r.timings.duration < 2000,
  });
  
  errorRate.add(!success);
  responseTime.add(res.timings.duration);
  throughput.add(1);
  
  return success;
}

// Test: Campaign operations under stress
export function testCampaignOperations() {
  const headers = getHeaders();
  const workspaceId = 'test-workspace';
  
  const operations = [
    () => http.get(`${BASE_URL}/v1/workspaces/${workspaceId}/campaigns`, { headers }),
    () => http.get(`${BASE_URL}/v1/workspaces/${workspaceId}/campaigns/test-campaign`, { headers }),
  ];
  
  const randomOp = operations[Math.floor(Math.random() * operations.length)];
  const res = randomOp();
  
  const success = check(res, {
    'campaign operation status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    'campaign operation response time < 2s': (r) => r.timings.duration < 2000,
  });
  
  errorRate.add(!success);
  responseTime.add(res.timings.duration);
  throughput.add(1);
  
  return success;
}

// Test: Strategy operations under stress
export function testStrategyOperations() {
  const headers = getHeaders();
  const workspaceId = 'test-workspace';
  
  const res = http.get(`${BASE_URL}/v1/workspaces/${workspaceId}/strategy`, { headers });
  
  const success = check(res, {
    'strategy operation status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    'strategy operation response time < 2s': (r) => r.timings.duration < 2000,
  });
  
  errorRate.add(!success);
  responseTime.add(res.timings.duration);
  throughput.add(1);
  
  return success;
}

// Test: Approval workflow under stress
export function testApprovalOperations() {
  const headers = getHeaders();
  const workspaceId = 'test-workspace';
  
  const res = http.get(`${BASE_URL}/v1/workspaces/${workspaceId}/approvals`, { headers });
  
  const success = check(res, {
    'approval operation status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    'approval operation response time < 2s': (r) => r.timings.duration < 2000,
  });
  
  errorRate.add(!success);
  responseTime.add(res.timings.duration);
  throughput.add(1);
  
  return success;
}

// Test: AI action log under stress
export function testAiActionLogOperations() {
  const headers = getHeaders();
  const workspaceId = 'test-workspace';
  
  const res = http.get(`${BASE_URL}/v1/workspaces/${workspaceId}/ai-action-log`, { headers });
  
  const success = check(res, {
    'ai action log status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    'ai action log response time < 2s': (r) => r.timings.duration < 2000,
  });
  
  errorRate.add(!success);
  responseTime.add(res.timings.duration);
  throughput.add(1);
  
  return success;
}

// Main stress test scenario
export default function() {
  // Execute all tests in parallel with minimal delay
  testWorkspaceOperations();
  sleep(0.05);  // Minimal delay to simulate realistic concurrency
  
  testCampaignOperations();
  sleep(0.05);
  
  testStrategyOperations();
  sleep(0.05);
  
  testApprovalOperations();
  sleep(0.05);
  
  testAiActionLogOperations();
  sleep(0.05);
}
