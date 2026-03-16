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

const BASE_URL = __ENV.AEP_URL || 'http://localhost:8080';

export default function () {
    const payload = JSON.stringify({
        type: 'event.ingestion.realtime',
        payload: {
            source: 'k6-load-testing',
            timestamp: Date.now(),
            value: Math.random() * 1000
        },
        headers: {
            tenantId: 'simulation-tenant-1'
        }
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'X-Ghatana-Tenant': 'simulation-tenant-1'
        },
    };

    // Assuming an HTTP entrypoint bound to PipelineExecutionEngine
    const res = http.post(`${BASE_URL}/api/v1/events/ingest`, payload, params);

    check(res, {
        'status is 200 or 202': (r) => r.status === 200 || r.status === 202,
        'has tracking id': (r) => JSON.parse(r.body).hasOwnProperty('executionId'),
    });

    sleep(0.1); // Small think time to mimic high volume streams
}
