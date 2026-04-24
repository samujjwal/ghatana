import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

/**
 * k6 Load Tests for Auth Gateway core endpoints.
 *
 * Covers:
 *   1. Token issuance   (POST /auth/login)
 *   2. Token validation (GET  /auth/validate)
 *   3. Token refresh    (POST /auth/refresh)
 *   4. Blocklist lookup via logout + validate blocked token
 *   5. Audit write throughput (implicit via login/logout sequences)
 *   6. Login throttling / rate-limit enforcement
 *
 * @doc.type   load-test
 * @doc.purpose Auth-gateway endpoint performance and correctness under load
 * @doc.layer  shared-services
 * @doc.pattern Load Test
 */

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

const BASE_URL = __ENV.AUTH_GATEWAY_URL || 'http://localhost:8080';

// Credentials seeded in the test credential store (InMemoryCredentialStore
// or a test JDBC store); override via env vars for non-local runs.
const TEST_USERNAME = __ENV.TEST_USERNAME || 'loadtest-user@ghatana.io';
const TEST_PASSWORD = __ENV.TEST_PASSWORD || 'loadtest-pass-123';

// SLA thresholds (milliseconds)
const LOGIN_P95_MS     = 500;
const VALIDATE_P95_MS  = 200;
const REFRESH_P95_MS   = 300;
const BLOCKLIST_P95_MS = 400;

const ERROR_RATE_MAX = 0.01; // 1 % max error rate

// ---------------------------------------------------------------------------
// Custom metrics
// ---------------------------------------------------------------------------

const loginLatency    = new Trend('auth_login_latency',    true);
const validateLatency = new Trend('auth_validate_latency', true);
const refreshLatency  = new Trend('auth_refresh_latency',  true);
const blocklistLatency = new Trend('auth_blocklist_latency', true);

const loginSuccessRate    = new Rate('auth_login_success');
const validateSuccessRate = new Rate('auth_validate_success');
const refreshSuccessRate  = new Rate('auth_refresh_success');
const blocklistSuccessRate = new Rate('auth_blocklist_success');
const throttleHitRate     = new Rate('auth_throttle_hit');

const totalLogins     = new Counter('auth_total_logins');
const totalValidates  = new Counter('auth_total_validates');

// ---------------------------------------------------------------------------
// Test stages
// ---------------------------------------------------------------------------

export const options = {
  scenarios: {
    normal_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '2m',  target: 30 },
        { duration: '3m',  target: 30 },
        { duration: '30s', target: 0  },
      ],
      gracefulRampDown: '10s',
    },
    spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '0s',  target: 0  },
        { duration: '10s', target: 50 },
        { duration: '1m',  target: 50 },
        { duration: '10s', target: 0  },
      ],
      startTime: '4m',
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    auth_login_latency:    [`p(95)<${LOGIN_P95_MS}`,    `p(99)<1000`],
    auth_validate_latency: [`p(95)<${VALIDATE_P95_MS}`, `p(99)<500`],
    auth_refresh_latency:  [`p(95)<${REFRESH_P95_MS}`,  `p(99)<600`],
    auth_blocklist_latency: [`p(95)<${BLOCKLIST_P95_MS}`],
    auth_login_success:    [`rate>${1 - ERROR_RATE_MAX}`],
    auth_validate_success: [`rate>${1 - ERROR_RATE_MAX}`],
    http_req_failed:       [`rate<${ERROR_RATE_MAX}`],
  },
};

// ---------------------------------------------------------------------------
// Helper: POST JSON body
// ---------------------------------------------------------------------------

function postJson(url, body, headers) {
  return http.post(url, JSON.stringify(body), {
    headers: Object.assign({ 'Content-Type': 'application/json' }, headers || {}),
  });
}

// ---------------------------------------------------------------------------
// 1. Token issuance (POST /auth/login)
// ---------------------------------------------------------------------------

export function testTokenIssuance() {
  const start = Date.now();
  const res = postJson(`${BASE_URL}/auth/login`, {
    username: TEST_USERNAME,
    password: TEST_PASSWORD,
  });
  const latency = Date.now() - start;
  loginLatency.add(latency);
  totalLogins.add(1);

  const ok = check(res, {
    'login: status 200':            (r) => r.status === 200,
    'login: has accessToken':       (r) => {
      try { return !!JSON.parse(r.body).accessToken; } catch { return false; }
    },
    'login: has refreshToken':      (r) => {
      try { return !!JSON.parse(r.body).refreshToken; } catch { return false; }
    },
    'login: has expiresIn > 0':     (r) => {
      try { return JSON.parse(r.body).expiresIn > 0; } catch { return false; }
    },
    'login: latency within SLA':    () => latency < LOGIN_P95_MS * 2,
  });
  loginSuccessRate.add(ok);

  if (res.status === 200) {
    try { return JSON.parse(res.body); } catch { return null; }
  }
  return null;
}

// ---------------------------------------------------------------------------
// 2. Token validation (GET /auth/validate)
// ---------------------------------------------------------------------------

export function testTokenValidation(accessToken) {
  if (!accessToken) return false;

  const start = Date.now();
  const res = http.get(`${BASE_URL}/auth/validate`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  const latency = Date.now() - start;
  validateLatency.add(latency);
  totalValidates.add(1);

  const ok = check(res, {
    'validate: status 200':           (r) => r.status === 200,
    'validate: valid=true':           (r) => {
      try { return JSON.parse(r.body).valid === true; } catch { return false; }
    },
    'validate: has userId':           (r) => {
      try { return !!JSON.parse(r.body).userId; } catch { return false; }
    },
    'validate: latency within SLA':   () => latency < VALIDATE_P95_MS * 2,
  });
  validateSuccessRate.add(ok);
  return ok;
}

// ---------------------------------------------------------------------------
// 3. Token refresh (POST /auth/refresh)
// ---------------------------------------------------------------------------

export function testTokenRefresh(refreshToken) {
  if (!refreshToken) return null;

  const start = Date.now();
  const res = postJson(`${BASE_URL}/auth/refresh`, {}, {
    Authorization: `Bearer ${refreshToken}`,
  });
  const latency = Date.now() - start;
  refreshLatency.add(latency);

  const ok = check(res, {
    'refresh: status 200':          (r) => r.status === 200,
    'refresh: has accessToken':     (r) => {
      try { return !!JSON.parse(r.body).accessToken; } catch { return false; }
    },
    'refresh: latency within SLA':  () => latency < REFRESH_P95_MS * 2,
  });
  refreshSuccessRate.add(ok);

  if (res.status === 200) {
    try { return JSON.parse(res.body).accessToken; } catch { return null; }
  }
  return null;
}

// ---------------------------------------------------------------------------
// 4. Blocklist: logout then confirm token is rejected on validate
// ---------------------------------------------------------------------------

export function testBlocklistEnforcement(tokens) {
  if (!tokens || !tokens.refreshToken) return;

  // Logout revokes the refresh token (adds jti to blocklist)
  const logoutStart = Date.now();
  const logoutRes = postJson(`${BASE_URL}/auth/logout`, {}, {
    Authorization: `Bearer ${tokens.refreshToken}`,
  });
  blocklistLatency.add(Date.now() - logoutStart);

  const logoutOk = check(logoutRes, {
    'blocklist/logout: status 200':     (r) => r.status === 200,
    'blocklist/logout: revoked message': (r) => {
      try { return String(r.body).includes('revoked'); } catch { return false; }
    },
  });

  // A revoked refresh token must be rejected on a subsequent refresh attempt
  if (logoutOk && tokens.refreshToken) {
    const rejectStart = Date.now();
    const rejectRes = postJson(`${BASE_URL}/auth/refresh`, {}, {
      Authorization: `Bearer ${tokens.refreshToken}`,
    });
    blocklistLatency.add(Date.now() - rejectStart);

    const blockOk = check(rejectRes, {
      'blocklist/refresh-rejected: status 401': (r) => r.status === 401,
    });
    blocklistSuccessRate.add(blockOk);
  }
}

// ---------------------------------------------------------------------------
// 5. Login throttling / rate-limit enforcement
// ---------------------------------------------------------------------------

export function testLoginThrottling() {
  const burst = 25; // exceed typical rate-limit window
  const requests = [];

  for (let i = 0; i < burst; i++) {
    requests.push({
      method: 'POST',
      url:    `${BASE_URL}/auth/login`,
      body:   JSON.stringify({ username: 'nonexistent@example.com', password: 'wrong' }),
      params: { headers: { 'Content-Type': 'application/json' } },
    });
  }

  const responses = http.batch(requests);
  const rateLimited = responses.filter((r) => r.status === 429).length;

  const throttleEngaged = check({ rateLimited }, {
    'throttle: at least one 429 in burst of 25': (d) => d.rateLimited > 0,
  });

  throttleHitRate.add(throttleEngaged ? 1 : 0);
}

// ---------------------------------------------------------------------------
// 6. Invalid token rejected on validate
// ---------------------------------------------------------------------------

export function testInvalidTokenRejected() {
  const res = http.get(`${BASE_URL}/auth/validate`, {
    headers: { Authorization: 'Bearer not.a.valid.token' },
  });
  check(res, {
    'invalid token: status 401': (r) => r.status === 401,
  });
}

// ---------------------------------------------------------------------------
// 7. Missing Authorization header rejected
// ---------------------------------------------------------------------------

export function testMissingAuthHeaderRejected() {
  const res = http.get(`${BASE_URL}/auth/validate`);
  check(res, {
    'no auth header: status 401': (r) => r.status === 401,
  });
}

// ---------------------------------------------------------------------------
// Default scenario — combines all flows
// ---------------------------------------------------------------------------

export default function () {
  group('1. Token Issuance', () => {
    const tokens = testTokenIssuance();
    sleep(0.2);

    if (tokens) {
      group('2. Token Validation', () => {
        testTokenValidation(tokens.accessToken);
      });
      sleep(0.1);

      group('3. Token Refresh', () => {
        testTokenRefresh(tokens.refreshToken);
      });
      sleep(0.1);

      // Blocklist runs less frequently to avoid excessive logout churn
      if (Math.random() < 0.2) {
        group('4. Blocklist Enforcement', () => {
          testBlocklistEnforcement(tokens);
        });
        sleep(0.3);
      }
    }
  });

  group('5. Invalid Token Handling', () => {
    testInvalidTokenRejected();
    testMissingAuthHeaderRejected();
  });
  sleep(0.1);

  // Throttle test runs rarely — just enough to confirm the gate is live
  if (Math.random() < 0.05) {
    group('6. Login Throttling', () => {
      testLoginThrottling();
    });
    sleep(1);
  }

  sleep(0.5);
}

// ---------------------------------------------------------------------------
// Summary
// ---------------------------------------------------------------------------

export function handleSummary(data) {
  const m = data.metrics;
  const fmt = (v) => (v != null ? v.toFixed(0) : 'N/A');

  let out = '\n=== Auth Gateway Load Test Summary ===\n\n';
  out += `Login     P95=${fmt(m.auth_login_latency?.['p(95)'])}ms  success=${pct(m.auth_login_success)}\n`;
  out += `Validate  P95=${fmt(m.auth_validate_latency?.['p(95)'])}ms  success=${pct(m.auth_validate_success)}\n`;
  out += `Refresh   P95=${fmt(m.auth_refresh_latency?.['p(95)'])}ms  success=${pct(m.auth_refresh_success)}\n`;
  out += `Blocklist P95=${fmt(m.auth_blocklist_latency?.['p(95)'])}ms  success=${pct(m.auth_blocklist_success)}\n`;
  out += `Throttle  hits=${pct(m.auth_throttle_hit)}\n`;
  out += `\nTotal logins=${m.auth_total_logins?.value ?? 0}  validates=${m.auth_total_validates?.value ?? 0}\n`;
  out += '\n=== End ===\n';

  console.log(out);
  return {
    stdout: out,
    '/tmp/auth-gateway-load-test-summary.json': JSON.stringify(data, null, 2),
  };
}

function pct(metric) {
  if (!metric) return 'N/A';
  const v = metric.value ?? metric.rate;
  return v != null ? `${(v * 100).toFixed(1)}%` : 'N/A';
}
