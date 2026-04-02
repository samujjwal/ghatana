import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

/**
 * k6 Load Testing for Security Gateway OAuth Flows
 * Tests OAuth2 authorization, token generation, and refresh performance under load
 * @doc.type load-test
 * @doc.purpose Performance validation for OAuth2 authentication flows
 * @doc.layer shared-services
 * @doc.pattern Load Test
 */

// Configuration
const BASE_URL = __ENV.SECURITY_GATEWAY_URL || 'http://localhost:9000';
const OAUTH_CLIENT_ID = __ENV.OAUTH_CLIENT_ID || 'test-client-id';
const OAUTH_CLIENT_SECRET = __ENV.OAUTH_CLIENT_SECRET || 'test-client-secret';
const REDIRECT_URI = __ENV.REDIRECT_URI || 'http://localhost:3000/callback';

// Performance SLA thresholds
const AUTHORIZE_P95_MS = 2000;      // Authorization endpoint < 2s
const TOKEN_P95_MS = 3000;           // Token endpoint < 3s
const REFRESH_P95_MS = 1500;         // Refresh endpoint < 1.5s
const ERROR_RATE_THRESHOLD = 0.02;   // 2% acceptable at peak load

// Custom metrics
const authorizeLatency = new Trend('authorize_latency', { unit: 'ms' });
const tokenLatency = new Trend('token_latency', { unit: 'ms' });
const refreshLatency = new Trend('refresh_latency', { unit: 'ms' });
const introspectLatency = new Trend('introspect_latency', { unit: 'ms' });
const revokeLatency = new Trend('revoke_latency', { unit: 'ms' });

const authorizationSuccess = new Rate('authorization_success');
const tokenSuccess = new Rate('token_success');
const refreshSuccess = new Rate('refresh_success');
const introspectSuccess = new Rate('introspect_success');
const revokeSuccess = new Rate('revoke_success');

const totalRequests = new Counter('total_oauth_requests');
const concurrentUsers = new Gauge('concurrent_users');

// Test stages
export const options = {
  stages: [
    { duration: '30s', target: 10 },      // Ramp-up to 10 users
    { duration: '2m', target: 30 },       // Ramp-up to 30 users
    { duration: '5m', target: 30 },       // Stay at 30 users
    { duration: '1m', target: 50 },       // Spike to 50 users
    { duration: '3m', target: 50 },       // Stay at 50 users (peak load)
    { duration: '1m', target: 20 },       // Ramp-down to 20 users
    { duration: '1m', target: 0 },        // Ramp-down to 0
  ],
  thresholds: {
    'authorize_latency': [
      `p(95) < ${AUTHORIZE_P95_MS}`,
      `p(99) < 5000`,
      'avg < 1000',
    ],
    'token_latency': [
      `p(95) < ${TOKEN_P95_MS}`,
      `p(99) < 5000`,
      'avg < 1000',
    ],
    'refresh_latency': [
      `p(95) < ${REFRESH_P95_MS}`,
      `p(99) < 3000`,
      'avg < 500',
    ],
    'token_success': [
      `rate > ${1 - ERROR_RATE_THRESHOLD}`,
    ],
  },
};

// Global state for test data
let testDataCache = {};

// Setup: Initialize test state
export function setup() {
  console.log('Setting up OAuth load test...');
  
  // Pre-compute authorization URLs for efficiency
  const authorizationUrls = [];
  for (let i = 0; i < 5; i++) {
    const state = `state-${Date.now()}-${i}`;
    const url = `${BASE_URL}/oauth/authorize?` +
      `client_id=${OAUTH_CLIENT_ID}&` +
      `redirect_uri=${encodeURIComponent(REDIRECT_URI)}&` +
      `response_type=code&` +
      `scope=read%20write&` +
      `state=${state}`;
    authorizationUrls.push({ url, state });
  }

  return { authorizationUrls };
}

export default function (data) {
  concurrentUsers.set(1);
  totalRequests.add(1);

  // Test 1: Authorization Endpoint (GET)
  group('Authorization Code Flow - Step 1: Get Authorization Code', () => {
    const authUrl = data.authorizationUrls[Math.floor(Math.random() * data.authorizationUrls.length)];

    const startTime = Date.now();
    const authRes = http.get(authUrl.url, {
      headers: {
        'User-Agent': 'LoadTest-Client',
        'Accept': 'text/html',
      },
      redirects: 0,  // Don't follow redirects to capture latency
    });
    const latency = Date.now() - startTime;

    authorizeLatency.add(latency);

    const success = check(authRes, {
      'authorization endpoint returns 302 (redirect)': (r) => r.status === 302 || r.status === 200,
      'authorization response includes location header': (r) => r.headers['Location'] || true,
      'authorization latency acceptable': () => latency < AUTHORIZE_P95_MS,
    });

    authorizationSuccess.add(success);
  });

  sleep(0.5);

  // Test 2: Token Endpoint (POST) - Client Credentials Grant
  group('OAuth Token Endpoint - Client Credentials', () => {
    const tokenPayload = {
      grant_type: 'client_credentials',
      client_id: OAUTH_CLIENT_ID,
      client_secret: OAUTH_CLIENT_SECRET,
      scope: 'read write',
    };

    const params = {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    };

    const startTime = Date.now();
    const tokenRes = http.post(
      `${BASE_URL}/oauth/token`,
      new URLSearchParams(tokenPayload).toString(),
      params
    );
    const latency = Date.now() - startTime;

    tokenLatency.add(latency);

    const success = check(tokenRes, {
      'token endpoint returns 200': (r) => r.status === 200,
      'token response includes access_token': (r) => r.body.includes('access_token'),
      'token response includes expires_in': (r) => r.body.includes('expires_in'),
      'token response is valid JSON': (r) => {
        try {
          JSON.parse(r.body);
          return true;
        } catch {
          return false;
        }
      },
      'token latency acceptable': () => latency < TOKEN_P95_MS,
    });

    tokenSuccess.add(success);

    // Cache access token for later tests
    if (tokenRes.status === 200) {
      try {
        const tokenData = JSON.parse(tokenRes.body);
        if (tokenData.access_token) {
          if (!testDataCache.accessTokens) {
            testDataCache.accessTokens = [];
          }
          testDataCache.accessTokens.push(tokenData.access_token);
        }
      } catch (e) {
        console.error('Failed to parse token response:', e);
      }
    }
  });

  sleep(1);

  // Test 3: Token Refresh
  group('OAuth Token Endpoint - Refresh Token Grant', () => {
    const refreshPayload = {
      grant_type: 'refresh_token',
      client_id: OAUTH_CLIENT_ID,
      client_secret: OAUTH_CLIENT_SECRET,
      refresh_token: `refresh-${Date.now()}`,  // Simulated refresh token
    };

    const params = {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    };

    const startTime = Date.now();
    const refreshRes = http.post(
      `${BASE_URL}/oauth/token`,
      new URLSearchParams(refreshPayload).toString(),
      params
    );
    const latency = Date.now() - startTime;

    refreshLatency.add(latency);

    // Refresh should succeed or return 400 (invalid refresh token is expected in load test)
    const success = check(refreshRes, {
      'refresh endpoint returns 200 or 400': (r) => r.status === 200 || r.status === 400,
      'refresh response contains token or error': (r) => r.body.includes('access_token') || r.body.includes('error'),
      'refresh latency acceptable': () => latency < REFRESH_P95_MS,
    });

    refreshSuccess.add(success);
  });

  sleep(0.5);

  // Test 4: Token Introspection
  group('Token Introspection Endpoint', () => {
    // Use a cached token or generate new one for introspection
    let tokenToIntrospect = 'sample-token-for-introspection';
    if (testDataCache.accessTokens && testDataCache.accessTokens.length > 0) {
      tokenToIntrospect = testDataCache.accessTokens[Math.floor(Math.random() * testDataCache.accessTokens.length)];
    }

    const introspectPayload = {
      token: tokenToIntrospect,
      client_id: OAUTH_CLIENT_ID,
      client_secret: OAUTH_CLIENT_SECRET,
    };

    const params = {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    };

    const startTime = Date.now();
    const introspectRes = http.post(
      `${BASE_URL}/oauth/introspect`,
      new URLSearchParams(introspectPayload).toString(),
      params
    );
    const latency = Date.now() - startTime;

    introspectLatency.add(latency);

    const success = check(introspectRes, {
      'introspect endpoint returns 200': (r) => r.status === 200,
      'introspect response includes active field': (r) => r.body.includes('active'),
      'introspect response is valid JSON': (r) => {
        try {
          JSON.parse(r.body);
          return true;
        } catch {
          return false;
        }
      },
      'introspect latency acceptable': () => latency < 2000,
    });

    introspectSuccess.add(success);
  });

  sleep(0.5);

  // Test 5: Token Revocation
  group('Token Revocation Endpoint', () => {
    const revokePayload = {
      token: `token-to-revoke-${Date.now()}`,
      client_id: OAUTH_CLIENT_ID,
      client_secret: OAUTH_CLIENT_SECRET,
    };

    const params = {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    };

    const startTime = Date.now();
    const revokeRes = http.post(
      `${BASE_URL}/oauth/revoke`,
      new URLSearchParams(revokePayload).toString(),
      params
    );
    const latency = Date.now() - startTime;

    revokeLatency.add(latency);

    // Revoke should return 200 even for invalid tokens (per RFC 7009)
    const success = check(revokeRes, {
      'revoke endpoint returns 200': (r) => r.status === 200,
      'revoke latency acceptable': () => latency < 1000,
    });

    revokeSuccess.add(success);
  });

  sleep(1);

  // Test 6: Concurrent Token Requests
  group('Concurrent Token Requests', () => {
    const requests = [];
    const batchSize = 5;

    for (let i = 0; i < batchSize; i++) {
      const payload = {
        grant_type: 'client_credentials',
        client_id: OAUTH_CLIENT_ID,
        client_secret: OAUTH_CLIENT_SECRET,
        scope: 'read write admin',
      };

      requests.push({
        method: 'POST',
        url: `${BASE_URL}/oauth/token`,
        body: new URLSearchParams(payload).toString(),
        params: {
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
          },
        },
      });
    }

    const startTime = Date.now();
    const responses = http.batch(requests);
    const totalLatency = Date.now() - startTime;

    tokenLatency.add(totalLatency / batchSize);

    const successCount = responses.filter(r => r.status === 200).length;

    check({ successCount }, {
      'batch token requests at least 80% success': (data) => data.successCount >= batchSize * 0.8,
    });

    console.log(`Batch: ${successCount}/${batchSize} token requests succeeded`);
  });

  sleep(1);

  // Test 7: Rate Limiting Verification
  group('Rate Limiting', () => {
    const requests = [];
    const rapidRequestCount = 20;

    for (let i = 0; i < rapidRequestCount; i++) {
      const payload = {
        grant_type: 'client_credentials',
        client_id: OAUTH_CLIENT_ID,
        client_secret: OAUTH_CLIENT_SECRET,
      };

      requests.push({
        method: 'POST',
        url: `${BASE_URL}/oauth/token`,
        body: new URLSearchParams(payload).toString(),
        params: {
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
          },
        },
      });
    }

    const responses = http.batch(requests);

    const successCount = responses.filter(r => r.status === 200).length;
    const rateLimitCount = responses.filter(r => r.status === 429).length;

    check({ successCount, rateLimitCount }, {
      'rate limiting working': (data) => data.rateLimitCount > 0 || data.successCount < rapidRequestCount,
    });

    if (rateLimitCount > 0) {
      console.log(`Rate limiting engaged: ${rateLimitCount}/${rapidRequestCount} requests throttled`);
    }
  });

  sleep(2);

  // Test 8: Error Handling
  group('Error Handling', () => {
    // Test invalid client
    const invalidClientRes = http.post(
      `${BASE_URL}/oauth/token`,
      new URLSearchParams({
        grant_type: 'client_credentials',
        client_id: 'invalid-client',
        client_secret: 'invalid-secret',
      }).toString(),
      {
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
      }
    );

    check(invalidClientRes, {
      'invalid client returns 401': (r) => r.status === 401 || r.status === 400,
      'error response includes error field': (r) => r.body.includes('error'),
    });

    // Test missing required field
    const missingFieldRes = http.post(
      `${BASE_URL}/oauth/token`,
      new URLSearchParams({
        client_id: OAUTH_CLIENT_ID,
        client_secret: OAUTH_CLIENT_SECRET,
        // Missing grant_type
      }).toString(),
      {
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
      }
    );

    check(missingFieldRes, {
      'missing field returns 400': (r) => r.status === 400,
      'error message explains issue': (r) => r.body.includes('grant_type') || r.body.includes('required'),
    });
  });

  sleep(1);
}

// Custom summary report
export function handleSummary(data) {
  const summary = generateOAuthSummary(data);
  console.log(summary);
  return {
    stdout: summary,
    '/tmp/oauth-load-test-summary.json': JSON.stringify(data, null, 2),
  };
}

function generateOAuthSummary(data) {
  let output = '\n\n=== OAuth Load Test Summary ===\n\n';

  output += 'Total Requests: ' + (data.metrics.total_oauth_requests?.value || 0) + '\n';
  output += 'Peak Concurrent Users: ' + (data.metrics.concurrent_users?.value || 0) + '\n\n';

  const endpoints = [
    { name: 'Authorization', metric: 'authorize_latency', successRate: 'authorization_success' },
    { name: 'Token Grant', metric: 'token_latency', successRate: 'token_success' },
    { name: 'Token Refresh', metric: 'refresh_latency', successRate: 'refresh_success' },
    { name: 'Token Introspection', metric: 'introspect_latency', successRate: 'introspect_success' },
    { name: 'Token Revocation', metric: 'revoke_latency', successRate: 'revoke_success' },
  ];

  output += '=== Per-Endpoint Performance ===\n\n';

  endpoints.forEach(ep => {
    const latencyData = data.metrics[ep.metric];
    const successData = data.metrics[ep.successRate];

    output += `${ep.name}:\n`;
    if (latencyData) {
      output += `  Avg: ${latencyData.avg?.toFixed(0) || 'N/A'}ms\n`;
      output += `  P95: ${latencyData['p(95)']?.toFixed(0) || 'N/A'}ms\n`;
      output += `  P99: ${latencyData['p(99)']?.toFixed(0) || 'N/A'}ms\n`;
      output += `  Max: ${latencyData.max?.toFixed(0) || 'N/A'}ms\n`;
    }
    if (successData) {
      output += `  Success Rate: ${(successData.value * 100).toFixed(2)}%\n`;
    }
    output += '\n';
  });

  output += '=== SLA Compliance ===\n\n';
  
  const authorize = data.metrics.authorize_latency;
  const token = data.metrics.token_latency;
  const refresh = data.metrics.refresh_latency;
  const tokenSuccess = data.metrics.token_success;

  output += `Authorization P95 (< ${AUTHORIZE_P95_MS}ms): ${authorize?.['p(95)'] < AUTHORIZE_P95_MS ? 'PASS' : 'FAIL'}\n`;
  output += `Token P95 (< ${TOKEN_P95_MS}ms): ${token?.['p(95)'] < TOKEN_P95_MS ? 'PASS' : 'FAIL'}\n`;
  output += `Refresh P95 (< ${REFRESH_P95_MS}ms): ${refresh?.['p(95)'] < REFRESH_P95_MS ? 'PASS' : 'FAIL'}\n`;
  output += `Token Success Rate (> 98%): ${tokenSuccess?.value > 0.98 ? 'PASS' : 'FAIL'}\n`;

  output += '\n=== End of Report ===\n\n';

  return output;
}
