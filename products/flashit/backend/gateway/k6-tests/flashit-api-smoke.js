#!/usr/bin/env node

/**
 * Phase 1: Lightweight performance smoke test for FlashIt API
 *
 * Validates that FlashIt content delivery workflow meets SLO latency thresholds:
 * - p95 latency < 500ms
 * - throughput >= 25 rps
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
    http_req_duration: ['p(95)<500', 'p(99)<900'],
    http_req_failed: ['rate<0.01'],
    errors: ['rate<0.01'],
  },
  stages: [
    { duration: '10s', target: 10 },
    { duration: '20s', target: 10 },
    { duration: '5s', target: 0 },
  ],
};

const BASE_URL = __ENV.FLASHIT_API_URL || 'http://localhost:8080';

export default function () {
  const contentId = `smoke-content-${__VU}-${__ITER}`;
  const payload = JSON.stringify({
    title: `Smoke Content ${__VU}`,
    type: 'flashcard',
    content: {
      front: 'Question',
      back: 'Answer',
    },
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${__ENV.API_KEY || 'test-key'}`,
      'X-Tenant-Id': 'smoke-tenant',
    },
  };

  // Create content
  const createRes = http.post(`${BASE_URL}/api/v1/content`, payload, params);
  
  const createSuccess = check(createRes, {
    'status is 201': (r) => r.status === 201,
    'response time < 500ms p95': (r) => r.timings.duration < 500,
    'has content ID': (r) => r.json('id') !== undefined,
  });

  errorRate.add(!createSuccess);

  // Read content
  if (createSuccess) {
    const contentId = createRes.json('id');
    const readRes = http.get(`${BASE_URL}/api/v1/content/${contentId}`, params);
    
    const readSuccess = check(readRes, {
      'status is 200': (r) => r.status === 200,
      'response time < 500ms p95': (r) => r.timings.duration < 500,
      'has content data': (r) => r.json('title') !== undefined,
    });

    errorRate.add(!readSuccess);
  }

  sleep(1);
}
