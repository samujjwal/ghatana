import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

/**
 * @doc.type load-test
 * @doc.purpose AR-5 load profile for AEP agent-registry read path
 * @doc.layer product
 * @doc.pattern Load Test
 */

const BASE_URL = __ENV.AEP_URL || 'http://localhost:8090';
const TENANT_ID = __ENV.AEP_TENANT_ID || 'load-tenant';

const listAgentsLatency = new Trend('aep_list_agents_latency', { unit: 'ms' });
const listAgentsSuccess = new Rate('aep_list_agents_success');
const listAgentsRequests = new Counter('aep_list_agents_requests');

export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '2m', target: 50 },
    { duration: '2m', target: 50 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    aep_list_agents_latency: ['p(95)<300', 'p(99)<750'],
    aep_list_agents_success: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const url = `${BASE_URL}/api/v1/agents?tenantId=${encodeURIComponent(TENANT_ID)}&limit=100`;
  const params = {
    headers: {
      'X-Tenant-Id': TENANT_ID,
      Accept: 'application/json',
    },
  };

  const startedAt = Date.now();
  const response = http.get(url, params);
  const elapsedMs = Date.now() - startedAt;

  listAgentsLatency.add(elapsedMs);
  listAgentsRequests.add(1);

  const ok = check(response, {
    'GET /api/v1/agents returns 200': (r) => r.status === 200,
    'response includes tenantId': (r) => r.body.includes('tenantId'),
    'response includes agents or configured flag': (r) => r.body.includes('agents') || r.body.includes('configured'),
  });

  listAgentsSuccess.add(ok);
  sleep(0.2);
}

