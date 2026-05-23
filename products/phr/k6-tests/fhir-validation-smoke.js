#!/usr/bin/env node

/**
 * Phase 1: Lightweight performance smoke test for PHR FHIR validation
 *
 * Validates that FHIR R4 resource validation workflow meets SLO latency thresholds:
 * - p95 latency < 700ms
 * - validation success rate > 99%
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
    { duration: '10s', target: 10 },
    { duration: '20s', target: 10 },
    { duration: '5s', target: 0 },
  ],
};

const BASE_URL = __ENV.PHR_API_URL || 'http://localhost:8080';

export default function () {
  const fhirResource = {
    resourceType: 'Patient',
    id: `smoke-patient-${__VU}`,
    name: [{ family: 'Test', given: ['Smoke'] }],
    birthDate: '1990-01-01',
  };

  const payload = JSON.stringify(fhirResource);
  const params = {
    headers: {
      'Content-Type': 'application/fhir+json',
      'Authorization': `Bearer ${__ENV.API_KEY || 'test-key'}`,
      'X-Tenant-Id': 'smoke-tenant',
    },
  };

  const res = http.post(`${BASE_URL}/api/v1/fhir/validate`, payload, params);

  const success = check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 700ms p95': (r) => r.timings.duration < 700,
    'validation result valid': (r) => r.json('valid') === true,
  });

  errorRate.add(!success);

  sleep(1);
}
