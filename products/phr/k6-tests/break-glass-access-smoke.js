#!/usr/bin/env node

/**
 * Phase 1: Lightweight performance smoke test for PHR break-glass emergency access
 *
 * Validates that break-glass emergency access workflow meets SLO latency thresholds:
 * - p95 latency < 700ms
 * - audit log success rate > 99%
 * - error rate < 1%
 *
 * This is a smoke test for CI, not a full load test.
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

export const options = {
  thresholds: {
    http_req_duration: ['p(95)<700', 'p(99)<1100'],
    http_req_failed: ['rate<0.01'],
    errors: ['rate<0.01'],
  },
  stages: [
    { duration: '10s', target: 5 },  // Lower target for emergency access
    { duration: '20s', target: 5 },
    { duration: '5s', target: 0 },
  ],
};

const BASE_URL = __ENV.PHR_API_URL || 'http://localhost:8080';

export default function () {
  const patientId = `smoke-patient-${__VU}`;
  const payload = JSON.stringify({
    patientId,
    reason: 'emergency_smoke_test',
    requestingUser: `smoke-user-${__VU}`,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${__ENV.API_KEY || 'test-key'}`,
      'X-Tenant-Id': 'smoke-tenant',
    },
  };

  const res = http.post(`${BASE_URL}/api/v1/patients/${patientId}/break-glass`, payload, params);

  const success = check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 700ms p95': (r) => r.timings.duration < 700,
    'has access granted': (r) => r.json('accessGranted') === true,
    'has audit log ID': (r) => r.json('auditLogId') !== undefined,
  });

  errorRate.add(!success);

  sleep(1);
}
