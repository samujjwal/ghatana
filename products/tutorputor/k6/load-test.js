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
    http_req_duration: ['p(95)<500'], // 95% of requests must complete below 500ms
    http_req_failed: ['rate<0.01'],    // Error rate must be less than 1%
    errors: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:3000';
const API_BASE = `${BASE_URL}/api`;

export default function () {
  // Test 1: Health check
  let healthRes = http.get(`${BASE_URL}/health`);
  check(healthRes, {
    'health status is 200': (r) => r.status === 200,
  }) || errorRate.add(1);
  responseTime.add(healthRes.timings.duration);
  sleep(1);

  // Test 2: Get modules
  let modulesRes = http.get(`${API_BASE}/modules`);
  check(modulesRes, {
    'modules status is 200': (r) => r.status === 200,
    'modules has data': (r) => JSON.parse(r.body).length > 0,
  }) || errorRate.add(1);
  responseTime.add(modulesRes.timings.duration);
  sleep(2);

  // Test 3: Get module detail
  if (JSON.parse(modulesRes.body).length > 0) {
    const firstModuleId = JSON.parse(modulesRes.body)[0].id;
    let moduleDetailRes = http.get(`${API_BASE}/modules/${firstModuleId}`);
    check(moduleDetailRes, {
      'module detail status is 200': (r) => r.status === 200,
    }) || errorRate.add(1);
    responseTime.add(moduleDetailRes.timings.duration);
    sleep(1);
  }

  // Test 4: Get lessons
  let lessonsRes = http.get(`${API_BASE}/lessons`);
  check(lessonsRes, {
    'lessons status is 200': (r) => r.status === 200,
  }) || errorRate.add(1);
  responseTime.add(lessonsRes.timings.duration);
  sleep(2);

  // Test 5: Analytics (if authenticated)
  const token = __ENV.AUTH_TOKEN;
  if (token) {
    let analyticsRes = http.get(`${API_BASE}/analytics/summary`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    check(analyticsRes, {
      'analytics status is 200': (r) => r.status === 200,
    }) || errorRate.add(1);
    responseTime.add(analyticsRes.timings.duration);
  }

  sleep(1);
}
