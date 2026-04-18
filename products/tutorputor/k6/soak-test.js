import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '5m', target: 50 },   // Ramp up
    { duration: '4h', target: 50 },   // Stay at 50 users for 4 hours (soak)
    { duration: '5m', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<800'],
    http_req_failed: ['rate<0.02'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:3000';

export default function () {
  // Test endurance over long period
  let res = http.get(`${BASE_URL}/api/modules`);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 1s': (r) => r.timings.duration < 1000,
  });
  
  sleep(Math.random() * 3); // Random sleep to simulate real user behavior
}
