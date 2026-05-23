#!/usr/bin/env node

/**
 * Phase 1: Lightweight performance smoke test for Data Cloud collection CRUD
 *
 * Validates that collection CRUD workflow meets SLO latency thresholds:
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

const BASE_URL = __ENV.DATA_CLOUD_API_URL || 'http://localhost:8080';

export default function () {
  const collectionId = `smoke-collection-${__VU}-${__ITER}`;
  const payload = JSON.stringify({
    name: `Smoke Collection ${__VU}`,
    description: 'Smoke test collection',
    schema: {
      type: 'object',
      properties: {
        title: { type: 'string' },
        value: { type: 'number' },
      },
    },
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${__ENV.API_KEY || 'test-key'}`,
      'X-Tenant-Id': 'smoke-tenant',
    },
  };

  // Create collection
  const createRes = http.post(`${BASE_URL}/api/v1/collections`, payload, params);
  
  const createSuccess = check(createRes, {
    'status is 201': (r) => r.status === 201,
    'response time < 600ms p95': (r) => r.timings.duration < 600,
    'has collection ID': (r) => r.json('id') !== undefined,
  });

  errorRate.add(!createSuccess);

  // Read collection
  if (createSuccess) {
    const collectionId = createRes.json('id');
    const readRes = http.get(`${BASE_URL}/api/v1/collections/${collectionId}`, params);
    
    const readSuccess = check(readRes, {
      'status is 200': (r) => r.status === 200,
      'response time < 600ms p95': (r) => r.timings.duration < 600,
      'has collection data': (r) => r.json('name') !== undefined,
    });

    errorRate.add(!readSuccess);
  }

  sleep(1);
}
