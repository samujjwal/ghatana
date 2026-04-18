import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 100 },  // Quick ramp to 100 users
    { duration: '3m', target: 200 },  // Ramp to 200 users
    { duration: '5m', target: 500 },  // Ramp to 500 users
    { duration: '5m', target: 500 },  // Stay at 500 users (stress)
    { duration: '3m', target: 1000 }, // Spike to 1000 users
    { duration: '2m', target: 1000 }, // Stay at 1000 users
    { duration: '2m', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'], // More lenient for stress test
    http_req_failed: ['rate<0.05'],    // Allow up to 5% errors during stress
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:3000';

export default function () {
  // Focus on critical endpoints for stress testing
  const endpoints = [
    `${BASE_URL}/health`,
    `${BASE_URL}/api/modules`,
    `${BASE_URL}/api/lessons`,
  ];

  endpoints.forEach(endpoint => {
    let res = http.get(endpoint);
    check(res, {
      'status is 200 or 503': (r) => r.status === 200 || r.status === 503, // Allow 503 during stress
    });
  });

  sleep(0.5); // Faster requests for stress test
}
