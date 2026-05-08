/**
 * FlashIt backend-backed API smoke tests.
 *
 * These tests verify that the gateway API is reachable and returns correct
 * shapes for unauthenticated endpoints (/health, /metrics) and validates
 * auth guards on protected endpoints.
 *
 * They are designed to run against a locally started gateway server
 * (see playwright.config.ts webServer configuration).
 *
 * Anti-theater rule (Section 29/35.3): every assertion is against a real
 * HTTP response, never a hard-coded object literal.
 */

import { test, expect } from '@playwright/test';

const API_BASE = process.env.FLASHIT_API_URL ?? 'http://localhost:3000';

// ---------------------------------------------------------------------------
// Health check
// ---------------------------------------------------------------------------

test.describe('FlashIt gateway — health and observability endpoints', () => {
  test('GET /health returns 200 with status field', async ({ request }) => {
    const response = await request.get(`${API_BASE}/health`);
    expect(response.status()).toBe(200);
    const body = await response.json() as { status: string };
    expect(typeof body.status).toBe('string');
    expect(['ok', 'degraded', 'down']).toContain(body.status);
  });

  test('GET /metrics returns 200 with Prometheus text format', async ({ request }) => {
    const response = await request.get(`${API_BASE}/metrics`);
    expect(response.status()).toBe(200);
    const contentType = response.headers()['content-type'] ?? '';
    expect(contentType).toMatch(/text\/plain/);
    const text = await response.text();
    // Prometheus text format always contains at least one HELP comment
    expect(text).toMatch(/^# (HELP|TYPE) /m);
  });
});

// ---------------------------------------------------------------------------
// Authentication guards — protected routes must reject unauthenticated callers
// ---------------------------------------------------------------------------

test.describe('FlashIt gateway — authentication guards', () => {
  test('GET /moments returns 401 without a token', async ({ request }) => {
    const response = await request.get(`${API_BASE}/moments`);
    expect(response.status()).toBe(401);
  });

  test('GET /route-entitlements returns 401 without a token', async ({ request }) => {
    const response = await request.get(`${API_BASE}/route-entitlements`);
    expect(response.status()).toBe(401);
  });

  test('GET /spheres returns 401 without a token', async ({ request }) => {
    const response = await request.get(`${API_BASE}/spheres`);
    expect(response.status()).toBe(401);
  });
});

// ---------------------------------------------------------------------------
// Registration flow (API contract)
// ---------------------------------------------------------------------------

test.describe('FlashIt gateway — auth registration API contract', () => {
  test('POST /auth/register with invalid body returns 400', async ({ request }) => {
    const response = await request.post(`${API_BASE}/auth/register`, {
      data: { email: 'not-an-email', password: 'short' },
      headers: { 'Content-Type': 'application/json' },
    });
    expect(response.status()).toBe(400);
    const body = await response.json() as { error: string };
    // Error response must have the `error` field defined in the OpenAPI spec
    expect(typeof body.error).toBe('string');
    expect(body.error.length).toBeGreaterThan(0);
  });

  test('POST /auth/login with wrong credentials returns 401', async ({ request }) => {
    const response = await request.post(`${API_BASE}/auth/login`, {
      data: {
        email: 'nobody@flashit.invalid',
        password: 'wrong-password-99',
      },
      headers: { 'Content-Type': 'application/json' },
    });
    expect(response.status()).toBe(401);
  });
});
