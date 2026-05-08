/**
 * DMOS backend-backed API smoke tests.
 *
 * Validates that the dm-api responds correctly to unauthenticated and
 * malformed requests.  These run against a locally started Java API server.
 *
 * Anti-theater rule (Section 29/35.3): every assertion targets a real HTTP
 * response, not a mocked or hard-coded value.
 */

import { test, expect } from '@playwright/test';

const DMOS_API_BASE = process.env.DMOS_API_URL ?? 'http://localhost:8090';

// ---------------------------------------------------------------------------
// Health / readiness checks
// ---------------------------------------------------------------------------

test.describe('DMOS API — health checks', () => {
  test('GET /health returns 200', async ({ request }) => {
    const response = await request.get(`${DMOS_API_BASE}/health`);
    expect(response.status()).toBe(200);
  });
});

// ---------------------------------------------------------------------------
// Authentication guards
// ---------------------------------------------------------------------------

test.describe('DMOS API — authentication guards on campaign endpoints', () => {
  const WORKSPACE_ID = 'smoke-test-workspace';

  test('GET /v1/workspaces/:id/campaigns returns 401 without a token', async ({ request }) => {
    const response = await request.get(
      `${DMOS_API_BASE}/v1/workspaces/${WORKSPACE_ID}/campaigns`,
    );
    expect(response.status()).toBe(401);
  });

  test('POST /v1/workspaces/:id/campaigns returns 401 without a token', async ({ request }) => {
    const response = await request.post(
      `${DMOS_API_BASE}/v1/workspaces/${WORKSPACE_ID}/campaigns`,
      {
        data: { name: 'Smoke Test Campaign', type: 'EMAIL' },
        headers: { 'Content-Type': 'application/json' },
      },
    );
    expect(response.status()).toBe(401);
  });
});

// ---------------------------------------------------------------------------
// Error response shape (API contract)
// ---------------------------------------------------------------------------

test.describe('DMOS API — error response shape', () => {
  const WORKSPACE_ID = 'smoke-test-workspace';

  test('401 error response includes correlationId field', async ({ request }) => {
    const response = await request.get(
      `${DMOS_API_BASE}/v1/workspaces/${WORKSPACE_ID}/campaigns`,
    );
    expect(response.status()).toBe(401);
    // DMOS ErrorBody schema requires: error, message, status, correlationId
    const body = await response.json() as {
      error: string;
      message: string;
      status: number;
      correlationId: string;
    };
    expect(typeof body.error).toBe('string');
    expect(typeof body.correlationId).toBe('string');
    expect(body.correlationId.length).toBeGreaterThan(0);
  });
});
