import http from 'k6/http';
import { check, sleep } from 'k6';

// Simulated options for continuous performance load simulating real-world AEP workloads
export const options = {
    stages: [
        { duration: '30s', target: 50 }, // Ramp-up to 50 users over 30s
        { duration: '1m', target: 50 },  // Stay at 50 users for 1m
        { duration: '30s', target: 0 },  // Ramp-down to 0 users over 30s
    ],
    thresholds: {
        http_req_duration: ['p(95)<100'], // 95% of requests must complete below 100ms (ActiveJ fast-path bound)
        http_req_failed: ['rate<0.01'],   // Error rate should be less than 1%
    },
};

const BASE_URL = __ENV.AEP_URL || 'http://localhost:8090';

export default function () {
    const payload = JSON.stringify({
        tenantId: 'simulation-tenant-1',
        type: 'event.ingestion.realtime',
        payload: {
            source: 'k6-load-testing',
            timestamp: Date.now(),
            value: Math.random() * 1000
        }
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'X-Tenant-Id': 'simulation-tenant-1'
        },
    };

    const res = http.post(`${BASE_URL}/api/v1/events`, payload, params);

    check(res, {
        'status is 200': (r) => r.status === 200,
        'has event id': (r) => JSON.parse(r.body).hasOwnProperty('eventId'),
    });

    sleep(0.1); // Small think time to mimic high volume streams
}
