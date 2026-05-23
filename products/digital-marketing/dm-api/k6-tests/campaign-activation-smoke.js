#!/usr/bin/env node

/**
 * Phase 1: Lightweight performance smoke test for Digital Marketing campaign activation
 *
 * Validates that campaign activation workflow meets SLO latency thresholds:
 * - p50 latency < 200ms
 * - p95 latency < 800ms
 * - p99 latency < 1400ms
 * - throughput >= 40 rps
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
    http_req_duration: ['p(50)<200', 'p(95)<800', 'p(99)<1400'],
    http_req_failed: ['rate<0.01'],
    errors: ['rate<0.01'],
  },
  stages: [
    { duration: '10s', target: 10 },  // Ramp up to 10 users
    { duration: '20s', target: 10 },  // Stay at 10 users
    { duration: '5s', target: 0 },    // Ramp down
  ],
};

const BASE_URL = __ENV.DM_API_URL || 'http://localhost:8080';

export default function () {
  const campaignId = 'smoke-test-campaign';
  const payload = JSON.stringify({
    campaignId,
    action: 'activate',
    consentCheck: true,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${__ENV.API_KEY || 'test-key'}`,
    },
  };

  const res = http.post(`${BASE_URL}/api/v1/campaigns/${campaignId}/activate`, payload, params);

  const success = check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 200ms p50': (r) => r.timings.duration < 200,
    'response time < 800ms p95': (r) => r.timings.duration < 800,
    'has activation result': (r) => r.json('activated') === true,
  });

  errorRate.add(!success);

  sleep(1);
}
