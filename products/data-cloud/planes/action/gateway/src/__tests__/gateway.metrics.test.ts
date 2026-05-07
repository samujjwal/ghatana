/**
 * AEP-P2-002 — Gateway metrics tests.
 *
 * Coverage:
 *  - GatewayMetrics unit tests (counter recording, snapshot, reset, latency histogram).
 *  - Integration tests: /metrics endpoint returns correct counts for
 *    HTTP proxy requests, auth failures, tenant mismatches, SSE, and WS events.
 */

import { describe, it, expect, beforeEach, afterEach, beforeAll, afterAll } from 'vitest';
import { createHmac } from 'node:crypto';
import { createServer, IncomingMessage, ServerResponse } from 'node:http';
import type { FastifyInstance } from 'fastify';
import type { AddressInfo } from 'node:net';
import { GatewayMetrics } from '../metrics.js';
import { buildApp } from '../app.js';

const TEST_SECRET = 'metrics-test-secret';

function makeJwt(payload: Record<string, unknown>, secret = TEST_SECRET): string {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
  const sig = createHmac('sha256', secret).update(`${header}.${body}`).digest('base64url');
  return `${header}.${body}.${sig}`;
}

function validToken(extra: Record<string, unknown> = {}): string {
  return makeJwt({ sub: 'user-1', exp: Math.floor(Date.now() / 1000) + 3600, ...extra });
}

// ── GatewayMetrics unit tests ──────────────────────────────────────────────────
describe('GatewayMetrics — unit', () => {
  let m: GatewayMetrics;

  beforeEach(() => {
    m = new GatewayMetrics();
  });

  it('starts with all counters at zero', () => {
    const snap = m.snapshot();
    expect(snap.tenantMismatchTotal).toBe(0);
    expect(snap.sseAcceptedTotal).toBe(0);
    expect(snap.sseRejectedTotal).toBe(0);
    expect(snap.wsAcceptedTotal).toBe(0);
    expect(snap.wsRejectedTotal).toBe(0);
    expect(snap.wsClosedTotal).toBe(0);
    expect(snap.backendUnreachableTotal).toBe(0);
    expect(snap.httpProxyRequestsByStatus).toEqual({});
    expect(snap.authFailuresByReason).toEqual({});
    expect(snap.backendLatencyMs.count).toBe(0);
    expect(snap.backendLatencyMs.sumMs).toBe(0);
  });

  it('counts HTTP proxy requests by status code', () => {
    m.recordHttpProxyRequest(200);
    m.recordHttpProxyRequest(200);
    m.recordHttpProxyRequest(404);
    const snap = m.snapshot();
    expect(snap.httpProxyRequestsByStatus['200']).toBe(2);
    expect(snap.httpProxyRequestsByStatus['404']).toBe(1);
  });

  it('counts auth failures by reason', () => {
    m.recordAuthFailure('missing_token');
    m.recordAuthFailure('missing_token');
    m.recordAuthFailure('invalid_token');
    const snap = m.snapshot();
    expect(snap.authFailuresByReason['missing_token']).toBe(2);
    expect(snap.authFailuresByReason['invalid_token']).toBe(1);
  });

  it('counts tenant mismatches', () => {
    m.recordTenantMismatch();
    m.recordTenantMismatch();
    expect(m.snapshot().tenantMismatchTotal).toBe(2);
  });

  it('counts SSE accepted and rejected independently', () => {
    m.recordSseAccepted();
    m.recordSseAccepted();
    m.recordSseRejected();
    const snap = m.snapshot();
    expect(snap.sseAcceptedTotal).toBe(2);
    expect(snap.sseRejectedTotal).toBe(1);
  });

  it('counts WS accepted, rejected, and closed independently', () => {
    m.recordWsAccepted();
    m.recordWsRejected();
    m.recordWsRejected();
    m.recordWsClosed();
    const snap = m.snapshot();
    expect(snap.wsAcceptedTotal).toBe(1);
    expect(snap.wsRejectedTotal).toBe(2);
    expect(snap.wsClosedTotal).toBe(1);
  });

  it('counts backend unreachable failures', () => {
    m.recordBackendUnreachable();
    m.recordBackendUnreachable();
    expect(m.snapshot().backendUnreachableTotal).toBe(2);
  });

  it('records backend latency count and sum', () => {
    m.recordBackendLatency(50);
    m.recordBackendLatency(150);
    const snap = m.snapshot();
    expect(snap.backendLatencyMs.count).toBe(2);
    expect(snap.backendLatencyMs.sumMs).toBe(200);
  });

  it('populates histogram buckets correctly', () => {
    m.recordBackendLatency(20); // falls in le_25, le_50, ... le_5000
    const snap = m.snapshot();
    expect(snap.backendLatencyMs.buckets['le_25']).toBe(1);
    expect(snap.backendLatencyMs.buckets['le_50']).toBe(1);
    expect(snap.backendLatencyMs.buckets['le_10']).toBe(0); // 20 > 10
  });

  it('does not double-count buckets across separate recordings', () => {
    m.recordBackendLatency(5);   // le_10 and higher
    m.recordBackendLatency(200); // le_250 and higher only
    const snap = m.snapshot();
    expect(snap.backendLatencyMs.buckets['le_10']).toBe(1); // only 5ms qualifies
    expect(snap.backendLatencyMs.buckets['le_250']).toBe(2); // both qualify
  });

  it('resets all counters to zero', () => {
    m.recordHttpProxyRequest(200);
    m.recordAuthFailure('missing_token');
    m.recordTenantMismatch();
    m.recordSseAccepted();
    m.recordWsAccepted();
    m.recordBackendLatency(100);
    m.reset();
    const snap = m.snapshot();
    expect(snap.httpProxyRequestsByStatus).toEqual({});
    expect(snap.authFailuresByReason).toEqual({});
    expect(snap.tenantMismatchTotal).toBe(0);
    expect(snap.sseAcceptedTotal).toBe(0);
    expect(snap.wsAcceptedTotal).toBe(0);
    expect(snap.backendLatencyMs.count).toBe(0);
    expect(snap.backendLatencyMs.sumMs).toBe(0);
  });
});

// ── /metrics endpoint integration ─────────────────────────────────────────────
describe('GET /metrics endpoint', () => {
  let metrics: GatewayMetrics;
  let app: FastifyInstance;

  beforeAll(async () => {
    metrics = new GatewayMetrics();
    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: 'http://127.0.0.1:1',
      allowedOrigins: ['*'],
      metrics,
    });
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(() => {
    metrics.reset();
  });

  it('returns 200 with a JSON snapshot', async () => {
    const res = await app.inject({ method: 'GET', url: '/metrics' });
    expect(res.statusCode).toBe(200);
    const body = res.json() as Record<string, unknown>;
    expect(body).toHaveProperty('httpProxyRequestsByStatus');
    expect(body).toHaveProperty('authFailuresByReason');
    expect(body).toHaveProperty('tenantMismatchTotal');
    expect(body).toHaveProperty('sseAcceptedTotal');
    expect(body).toHaveProperty('sseRejectedTotal');
    expect(body).toHaveProperty('wsAcceptedTotal');
    expect(body).toHaveProperty('wsRejectedTotal');
    expect(body).toHaveProperty('wsClosedTotal');
    expect(body).toHaveProperty('backendUnreachableTotal');
    expect(body).toHaveProperty('backendLatencyMs');
  });

  it('reflects auth failure counter after unauthenticated request', async () => {
    await app.inject({ method: 'GET', url: '/api/v1/any' });
    const snap = metrics.snapshot();
    expect(snap.authFailuresByReason['missing_token']).toBeGreaterThanOrEqual(1);
  });

  it('reflects tenant mismatch counter after mismatched request', async () => {
    const token = validToken({ tenantId: 'tenant-a' });
    await app.inject({
      method: 'GET',
      url: '/api/v1/any',
      headers: { authorization: `Bearer ${token}`, 'x-tenant-id': 'tenant-b' },
    });
    const snap = metrics.snapshot();
    expect(snap.tenantMismatchTotal).toBeGreaterThanOrEqual(1);
  });
});

// ── Metrics counters — HTTP proxy status integration ───────────────────────────
describe('HTTP proxy metrics integration', () => {
  let metrics: GatewayMetrics;
  let app: FastifyInstance;
  let backend: ReturnType<typeof createServer>;
  let backendUrl: string;

  beforeEach(async () => {
    metrics = new GatewayMetrics();
    backend = createServer((_req: IncomingMessage, res: ServerResponse) => {
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ ok: true }));
    });
    await new Promise<void>((resolve) => { backend.listen(0, '127.0.0.1', resolve); });
    const addr = backend.address() as AddressInfo;
    backendUrl = `http://127.0.0.1:${addr.port}`;

    app = await buildApp({ jwtSecret: TEST_SECRET, backendUrl, allowedOrigins: ['*'], metrics });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
    await new Promise<void>((resolve) => { backend.close(() => resolve()); });
  });

  it('increments httpProxyRequestsByStatus[200] for successful proxy', async () => {
    const token = validToken();
    await app.inject({
      method: 'GET',
      url: '/api/v1/events',
      headers: { authorization: `Bearer ${token}` },
    });
    expect(metrics.snapshot().httpProxyRequestsByStatus['200']).toBe(1);
  });

  it('records backend latency for successful proxy', async () => {
    const token = validToken();
    await app.inject({
      method: 'GET',
      url: '/api/v1/events',
      headers: { authorization: `Bearer ${token}` },
    });
    expect(metrics.snapshot().backendLatencyMs.count).toBe(1);
    expect(metrics.snapshot().backendLatencyMs.sumMs).toBeGreaterThanOrEqual(0);
  });

  it('increments backendUnreachableTotal and 502 on unreachable backend', async () => {
    const isolatedMetrics = new GatewayMetrics();
    const unreachableApp = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: 'http://127.0.0.1:1',
      allowedOrigins: ['*'],
      metrics: isolatedMetrics,
    });
    await unreachableApp.ready();

    await unreachableApp.inject({
      method: 'GET',
      url: '/api/v1/events',
      headers: { authorization: `Bearer ${validToken()}` },
    });

    const snap = isolatedMetrics.snapshot();
    expect(snap.backendUnreachableTotal).toBe(1);
    expect(snap.httpProxyRequestsByStatus['502']).toBe(1);

    await unreachableApp.close();
  });
});

// ── Metrics counters — SSE integration ────────────────────────────────────────
describe('SSE metrics integration', () => {
  let metrics: GatewayMetrics;
  let app: FastifyInstance;
  let backend: ReturnType<typeof createServer>;

  beforeEach(async () => {
    metrics = new GatewayMetrics();
    backend = createServer((_req: IncomingMessage, res: ServerResponse) => {
      res.writeHead(200, { 'content-type': 'text/event-stream' });
      res.end('data: ok\n\n');
    });
    await new Promise<void>((resolve) => { backend.listen(0, '127.0.0.1', resolve); });
    const addr = backend.address() as AddressInfo;
    const backendUrl = `http://127.0.0.1:${addr.port}`;

    app = await buildApp({ jwtSecret: TEST_SECRET, backendUrl, allowedOrigins: ['*'], metrics });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
    await new Promise<void>((resolve) => { backend.close(() => resolve()); });
  });

  it('increments sseRejectedTotal on missing SSE credentials', async () => {
    await app.inject({ method: 'GET', url: '/events/stream' });
    expect(metrics.snapshot().sseRejectedTotal).toBe(1);
  });

  it('increments sseRejectedTotal on invalid SSE credentials', async () => {
    const badToken = makeJwt({ sub: 'user-1' }, 'wrong-secret');
    await app.inject({ method: 'GET', url: `/events/stream?token=${badToken}` });
    expect(metrics.snapshot().sseRejectedTotal).toBe(1);
  });

  it('increments sseAcceptedTotal on successful SSE stream', async () => {
    const token = validToken();
    await app.inject({
      method: 'GET',
      url: '/events/stream',
      headers: { authorization: `Bearer ${token}` },
    });
    expect(metrics.snapshot().sseAcceptedTotal).toBe(1);
    expect(metrics.snapshot().sseRejectedTotal).toBe(0);
  });

  it('increments sseRejectedTotal and backendUnreachableTotal on SSE backend failure', async () => {
    const isolatedMetrics = new GatewayMetrics();
    const unreachableApp = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: 'http://127.0.0.1:1',
      allowedOrigins: ['*'],
      metrics: isolatedMetrics,
    });
    await unreachableApp.ready();

    await unreachableApp.inject({
      method: 'GET',
      url: '/events/stream',
      headers: { authorization: `Bearer ${validToken()}` },
    });

    const snap = isolatedMetrics.snapshot();
    expect(snap.sseRejectedTotal).toBe(1);
    expect(snap.backendUnreachableTotal).toBe(1);

    await unreachableApp.close();
  });

  it('increments tenantMismatchTotal on SSE tenant mismatch', async () => {
    const token = validToken({ tenantId: 'tenant-a' });
    await app.inject({
      method: 'GET',
      url: '/events/stream?tenantId=tenant-b',
      headers: { authorization: `Bearer ${token}` },
    });
    expect(metrics.snapshot().tenantMismatchTotal).toBe(1);
    expect(metrics.snapshot().sseRejectedTotal).toBe(1);
  });
});

// ── Metrics counters — WS integration ─────────────────────────────────────────
// @local-network — binds real TCP port for WebSocket connections
describe('WS metrics integration', () => {
  let metrics: GatewayMetrics;
  let app: FastifyInstance;
  let baseUrl: string;

  beforeAll(async () => {
    metrics = new GatewayMetrics();
    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: 'http://127.0.0.1:1', // unreachable — auth tests only
      allowedOrigins: ['*'],
      metrics,
    });
    await app.listen({ port: 0, host: '127.0.0.1' });
    const addr = app.server.address() as AddressInfo;
    baseUrl = `ws://127.0.0.1:${addr.port}`;
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(() => {
    metrics.reset();
  });

  it('increments wsRejectedTotal on missing WS token (4001)', async () => {
    const { default: WebSocket } = await import('ws');
    const ws = new WebSocket(`${baseUrl}/tail/events`);
    await new Promise<void>((resolve) => { ws.on('close', () => resolve()); });
    expect(metrics.snapshot().wsRejectedTotal).toBe(1);
    expect(metrics.snapshot().wsAcceptedTotal).toBe(0);
    expect(metrics.snapshot().authFailuresByReason['missing_token']).toBe(1);
  });

  it('increments wsRejectedTotal on invalid WS token (4003)', async () => {
    const { default: WebSocket } = await import('ws');
    const badToken = makeJwt({ sub: 'user-1' }, 'wrong-secret');
    const ws = new WebSocket(`${baseUrl}/tail/events`, {
      headers: { authorization: `Bearer ${badToken}` },
    });
    await new Promise<void>((resolve) => { ws.on('close', () => resolve()); });
    expect(metrics.snapshot().wsRejectedTotal).toBe(1);
    expect(metrics.snapshot().wsAcceptedTotal).toBe(0);
    expect(metrics.snapshot().authFailuresByReason['invalid_token']).toBe(1);
  });

  it('increments wsAcceptedTotal when auth succeeds (backend unreachable → 1011)', async () => {
    const { default: WebSocket } = await import('ws');
    const ws = new WebSocket(`${baseUrl}/tail/events?token=${validToken()}`);
    await new Promise<void>((resolve) => { ws.on('close', () => resolve()); });
    expect(metrics.snapshot().wsAcceptedTotal).toBe(1);
    expect(metrics.snapshot().wsRejectedTotal).toBe(0);
  });

  it('increments wsClosedTotal when the client closes the connection', async () => {
    const { default: WebSocket } = await import('ws');
    const ws = new WebSocket(`${baseUrl}/tail/events?token=${validToken()}`);
    // Wait for close from backend-unreachable error path (1011)
    await new Promise<void>((resolve) => { ws.on('close', () => resolve()); });
    // wsClosedTotal is recorded when the clientSocket fires 'close'
    expect(metrics.snapshot().wsClosedTotal).toBeGreaterThanOrEqual(1);
  });
});
