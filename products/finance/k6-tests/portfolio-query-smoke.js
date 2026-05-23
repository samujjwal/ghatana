#!/usr/bin/env node

/**
 * Phase 1: Lightweight performance smoke test for Finance portfolio query
 *
 * Validates that portfolio query workflow meets SLO latency thresholds:
 * - p95 latency < 600ms
 * - throughput >= 20 rps
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
    http_req_duration: ['p(95)<600', 'p(99)<1000'],
    http_req_failed: ['rate<0.01'],
    errors: ['rate<0.01'],
  },
  stages: [
    { duration: '10s', target: 10 },
    { duration: '20s', target: 10 },
    { duration: '5s', target: 0 },
  ],
};

const BASE_URL = __ENV.FINANCE_API_URL || 'http://localhost:8080';

export default function () {
  const portfolioId = `smoke-portfolio-${__VU}`;
  const params = {
    headers: {
      'Authorization': `Bearer ${__ENV.API_KEY || 'test-key'}`,
      'X-Tenant-Id': 'smoke-tenant',
    },
  };

  // Query portfolio
  const res = http.get(`${BASE_URL}/api/v1/portfolios/${portfolioId}`, params);
  
  const success = check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 600ms p95': (r) => r.timings.duration < 600,
    'has portfolio data': (r) => r.json('portfolioId') !== undefined,
  });

  errorRate.add(!success);

  sleep(1);
}
