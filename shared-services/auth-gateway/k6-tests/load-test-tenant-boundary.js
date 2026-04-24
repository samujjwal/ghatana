import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

/**
 * @doc.type load-test
 * @doc.purpose Validate tenant boundary enforcement under concurrent auth traffic
 * @doc.layer shared-services
 * @doc.pattern Load Test
 */

const BASE_URL = __ENV.AUTH_GATEWAY_URL || 'http://localhost:8080';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || 'replace-me';

const tenantLeakRate = new Rate('tenant_leak_rate');
const tenantRequestLatency = new Trend('tenant_request_latency', { unit: 'ms' });

export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '2m', target: 40 },
    { duration: '2m', target: 40 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    tenant_leak_rate: ['rate == 0'],
    tenant_request_latency: ['p(95) < 2000'],
  },
};

function requestWithTenant(tenantId) {
  const startedAt = Date.now();
  const res = http.get(`${BASE_URL}/auth/tenant`, {
    headers: {
      Authorization: `Bearer ${AUTH_TOKEN}`,
      'X-Tenant-Id': tenantId,
      'X-Correlation-ID': `k6-${tenantId}-${Date.now()}`,
    },
  });
  tenantRequestLatency.add(Date.now() - startedAt);
  return res;
}

export default function () {
  const tenantA = `tenant-a-${__VU % 10}`;
  const tenantB = `tenant-b-${__VU % 10}`;

  group('tenant header isolation checks', () => {
    const resA = requestWithTenant(tenantA);
    const resB = requestWithTenant(tenantB);

    const okA = check(resA, {
      'tenant A returns success or auth error': (r) => [200, 401, 403].includes(r.status),
      'tenant A body does not leak tenant B': (r) => !String(r.body || '').includes(tenantB),
    });

    const okB = check(resB, {
      'tenant B returns success or auth error': (r) => [200, 401, 403].includes(r.status),
      'tenant B body does not leak tenant A': (r) => !String(r.body || '').includes(tenantA),
    });

    tenantLeakRate.add(okA && okB);
  });

  sleep(0.2);
}
