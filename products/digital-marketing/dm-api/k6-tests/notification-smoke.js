#!/usr/bin/env node

/**
 * Phase 1: Lightweight performance smoke test for Digital Marketing notification delivery
 *
 * Validates that notification delivery workflow meets SLO latency thresholds:
 * - p95 latency < 800ms
 * - delivery success rate > 99%
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
    http_req_duration: ['p(95)<800', 'p(99)<1200'],
    http_req_failed: ['rate<0.01'],
    errors: ['rate<0.01'],
  },
  stages: [
    { duration: '10s', target: 10 },
    { duration: '20s', target: 10 },
    { duration: '5s', target: 0 },
  ],
};

const BASE_URL = __ENV.DM_API_URL || 'http://localhost:8080';

export default function () {
  const notificationId = `smoke-notification-${__VU}-${__ITER}`;
  const payload = JSON.stringify({
    notificationId,
    recipient: `test-${__VU}@example.com`,
    type: 'campaign_update',
    content: 'Smoke test notification',
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${__ENV.API_KEY || 'test-key'}`,
    },
  };

  const res = http.post(`${BASE_URL}/api/v1/notifications/send`, payload, params);

  const success = check(res, {
    'status is 202': (r) => r.status === 202,
    'response time < 800ms p95': (r) => r.timings.duration < 800,
    'has notification ID': (r) => r.json('notificationId') !== undefined,
  });

  errorRate.add(!success);

  sleep(1);
}
