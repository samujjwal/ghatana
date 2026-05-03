/**
 * SSE and WebSocket backend contract tests for the AEP gateway.
 *
 * Suites marked with `@local-network` bind real TCP ports.
 *
 * Coverage:
 *  - SSE: Content-Type contract, multi-event streaming, correlation ID forwarding,
 *         backend error response mapping, 502 on unreachable backend.
 *  - WebSocket: backend message forwarding, backend close propagation,
 *               backend error propagation (1011), bidirectional message relay.
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { createHmac } from 'node:crypto';
import { createServer, IncomingMessage, ServerResponse } from 'node:http';
import type { FastifyInstance } from 'fastify';
import type { AddressInfo } from 'node:net';
import { buildApp } from '../app.js';

const TEST_SECRET = 'sse-ws-contract-secret';

function makeJwt(payload: Record<string, unknown>, secret = TEST_SECRET): string {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
  const sig = createHmac('sha256', secret).update(`${header}.${body}`).digest('base64url');
  return `${header}.${body}.${sig}`;
}

function validToken(extra: Record<string, unknown> = {}): string {
  return makeJwt({ sub: 'user-1', exp: Math.floor(Date.now() / 1000) + 3600, ...extra });
}

// ── SSE backend contract ───────────────────────────────────────────────────────
// @local-network — spins up a real HTTP backend to serve event-stream responses

describe('SSE /events/stream — backend contract', () => {
  let app: FastifyInstance;
  let backend: ReturnType<typeof createServer>;
  let backendUrl: string;

  beforeEach(async () => {
    // The backend is created per test so each test can configure its own response
    backend = createServer((_req: IncomingMessage, _res: ServerResponse) => {
      // Default no-op; overridden by individual tests via backendHandler
    });
    await new Promise<void>((resolve) => { backend.listen(0, '127.0.0.1', resolve); });
    const addr = backend.address() as AddressInfo;
    backendUrl = `http://127.0.0.1:${addr.port}`;
  });

  afterEach(async () => {
    if (app) await app.close();
    if (backend.listening) {
      await new Promise<void>((resolve) => { backend.close(() => resolve()); });
    }
  });

  it('sets Content-Type: text/event-stream on a successful SSE response', async () => {
    backend.removeAllListeners('request');
    backend.on('request', (_req: IncomingMessage, res: ServerResponse) => {
      res.writeHead(200, { 'content-type': 'text/event-stream' });
      res.end('data: {"event":"hello"}\n\n');
    });

    app = await buildApp({ jwtSecret: TEST_SECRET, backendUrl, allowedOrigins: ['*'] });
    await app.ready();

    const res = await app.inject({
      method: 'GET',
      url: '/events/stream',
      headers: { authorization: `Bearer ${validToken()}` },
    });

    expect(res.statusCode).toBe(200);
    expect(res.headers['content-type']).toContain('text/event-stream');
  });

  it('streams multiple SSE events to the client verbatim', async () => {
    const events = 'data: {"n":1}\n\ndata: {"n":2}\n\ndata: {"n":3}\n\n';
    backend.removeAllListeners('request');
    backend.on('request', (_req: IncomingMessage, res: ServerResponse) => {
      res.writeHead(200, { 'content-type': 'text/event-stream' });
      res.end(events);
    });

    app = await buildApp({ jwtSecret: TEST_SECRET, backendUrl, allowedOrigins: ['*'] });
    await app.ready();

    const res = await app.inject({
      method: 'GET',
      url: '/events/stream',
      headers: { authorization: `Bearer ${validToken()}` },
    });

    expect(res.statusCode).toBe(200);
    expect(res.body).toBe(events);
  });

  it('forwards the X-Correlation-ID header to the SSE backend', async () => {
    let capturedCorrelationId: string | undefined;
    backend.removeAllListeners('request');
    backend.on('request', (req: IncomingMessage, res: ServerResponse) => {
      capturedCorrelationId = req.headers['x-correlation-id'] as string | undefined;
      res.writeHead(200, { 'content-type': 'text/event-stream' });
      res.end('data: ok\n\n');
    });

    app = await buildApp({ jwtSecret: TEST_SECRET, backendUrl, allowedOrigins: ['*'] });
    await app.ready();

    await app.inject({
      method: 'GET',
      url: '/events/stream',
      headers: {
        authorization: `Bearer ${validToken()}`,
        'x-correlation-id': 'corr-sse-contract-001',
      },
    });

    expect(capturedCorrelationId).toBe('corr-sse-contract-001');
  });

  it('sets the X-Correlation-ID response header on the SSE stream', async () => {
    backend.removeAllListeners('request');
    backend.on('request', (_req: IncomingMessage, res: ServerResponse) => {
      res.writeHead(200, { 'content-type': 'text/event-stream' });
      res.end('data: ping\n\n');
    });

    app = await buildApp({ jwtSecret: TEST_SECRET, backendUrl, allowedOrigins: ['*'] });
    await app.ready();

    const res = await app.inject({
      method: 'GET',
      url: '/events/stream',
      headers: {
        authorization: `Bearer ${validToken()}`,
        'x-correlation-id': 'corr-sse-resp-002',
      },
    });

    expect(res.headers['x-correlation-id']).toBe('corr-sse-resp-002');
  });

  it('propagates tenantId from JWT claim to the SSE backend query string', async () => {
    let capturedUrl: string | undefined;
    backend.removeAllListeners('request');
    backend.on('request', (req: IncomingMessage, res: ServerResponse) => {
      capturedUrl = req.url ?? '';
      res.writeHead(200, { 'content-type': 'text/event-stream' });
      res.end('data: done\n\n');
    });

    app = await buildApp({ jwtSecret: TEST_SECRET, backendUrl, allowedOrigins: ['*'] });
    await app.ready();

    await app.inject({
      method: 'GET',
      url: '/events/stream',
      headers: { authorization: `Bearer ${validToken({ tenantId: 'tenant-contract-x' })}` },
    });

    expect(capturedUrl).toContain('tenantId=tenant-contract-x');
  });

  it('returns 502 and a Bad Gateway payload when the SSE backend returns a non-2xx status', async () => {
    backend.removeAllListeners('request');
    backend.on('request', (_req: IncomingMessage, res: ServerResponse) => {
      res.writeHead(500, { 'content-type': 'application/json' });
      res.end('{"error":"internal"}');
    });

    app = await buildApp({ jwtSecret: TEST_SECRET, backendUrl, allowedOrigins: ['*'] });
    await app.ready();

    const res = await app.inject({
      method: 'GET',
      url: '/events/stream',
      headers: { authorization: `Bearer ${validToken()}` },
    });

    expect(res.statusCode).toBe(502);
    expect(res.json().message).toBe('SSE backend unreachable');
  });
});

// ── WebSocket backend proxy contract ──────────────────────────────────────────
// @local-network — binds real TCP ports for WebSocket connections

import WebSocket, { WebSocketServer } from 'ws';

describe('WebSocket /tail/events — backend proxy contract', () => {
  let app: FastifyInstance;
  let gatewayBaseUrl: string;
  let backendWss: WebSocketServer;
  let backendWsUrl: string;

  beforeEach(async () => {
    backendWss = new WebSocketServer({ host: '127.0.0.1', port: 0 });
    await new Promise<void>((resolve) => backendWss.once('listening', resolve));
    const addr = backendWss.address() as AddressInfo;
    const backendHttpUrl = `http://127.0.0.1:${addr.port}`;
    backendWsUrl = `ws://127.0.0.1:${addr.port}`;

    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: backendHttpUrl,
      allowedOrigins: ['*'],
    });
    await app.listen({ port: 0, host: '127.0.0.1' });
    const gwAddr = app.server.address() as AddressInfo;
    gatewayBaseUrl = `ws://127.0.0.1:${gwAddr.port}`;
  });

  afterEach(async () => {
    await app.close();
    await new Promise<void>((resolve) => backendWss.close(() => resolve()));
  });

  it('forwards a message from backend to connected client', { timeout: 10_000 }, async () => {
    backendWss.once('connection', (serverSide) => {
      serverSide.send('{"event":"forwarded"}');
    });

    const client = new WebSocket(`${gatewayBaseUrl}/tail/events?token=${validToken()}`);
    const message = await new Promise<string>((resolve, reject) => {
      client.once('message', (data) => resolve(data.toString()));
      client.once('error', reject);
      setTimeout(() => reject(new Error('Timeout: no message received')), 8_000);
    });
    client.close();

    expect(JSON.parse(message)).toEqual({ event: 'forwarded' });
  });

  it('propagates backend close to the client with code 1000', { timeout: 10_000 }, async () => {
    backendWss.once('connection', (serverSide) => {
      serverSide.close(1000, 'Backend closed connection');
    });

    const client = new WebSocket(`${gatewayBaseUrl}/tail/events?token=${validToken()}`);
    const code = await new Promise<number>((resolve, reject) => {
      client.once('close', (c) => resolve(c));
      client.once('error', reject);
      setTimeout(() => reject(new Error('Timeout: client close event not received')), 8_000);
    });

    // Gateway maps backend close → client close; 1000 = normal close
    expect(code).toBe(1000);
  });

  it('relays a message from client to the backend', { timeout: 10_000 }, async () => {
    const backendReceived = new Promise<string>((resolve, reject) => {
      backendWss.once('connection', (serverSide) => {
        serverSide.once('message', (data) => resolve(data.toString()));
      });
      setTimeout(() => reject(new Error('Timeout: backend did not receive client message')), 8_000);
    });

    const client = new WebSocket(`${gatewayBaseUrl}/tail/events?token=${validToken()}`);
    await new Promise<void>((resolve, reject) => {
      client.once('open', resolve);
      client.once('error', reject);
      setTimeout(() => reject(new Error('Timeout: client did not connect')), 8_000);
    });
    client.send('{"client":"ping"}');

    const received = await backendReceived;
    client.close();

    expect(JSON.parse(received)).toEqual({ client: 'ping' });
  });

  it('forwards x-tenant-id from JWT claim to backend WS connection headers', { timeout: 10_000 }, async () => {
    const capturedHeaders = await new Promise<Record<string, string | string[] | undefined>>((resolve, reject) => {
      backendWss.once('connection', (_serverSide, req) => {
        resolve(req.headers as Record<string, string | string[] | undefined>);
      });
      setTimeout(() => reject(new Error('Timeout: backend WS connection not received')), 8_000);
    });

    const token = validToken({ tenantId: 'tenant-xyz' });
    const client = new WebSocket(`${gatewayBaseUrl}/tail/events?token=${token}`);
    await new Promise<void>((resolve, reject) => {
      client.once('open', resolve);
      client.once('error', reject);
      setTimeout(() => reject(new Error('Timeout: client did not connect')), 8_000);
    });
    client.close();

    await capturedHeaders;
    expect(capturedHeaders['x-tenant-id']).toBe('tenant-xyz');
    expect(capturedHeaders['x-gateway-trusted']).toBe('true');
  });

  it('forwards x-correlation-id from client request to backend WS connection headers', { timeout: 10_000 }, async () => {
    const capturedHeaders = await new Promise<Record<string, string | string[] | undefined>>((resolve, reject) => {
      backendWss.once('connection', (_serverSide, req) => {
        resolve(req.headers as Record<string, string | string[] | undefined>);
      });
      setTimeout(() => reject(new Error('Timeout: backend WS connection not received')), 8_000);
    });

    const correlationId = 'corr-abc-123';
    const client = new WebSocket(
      `${gatewayBaseUrl}/tail/events?token=${validToken()}&correlationId=${correlationId}`,
    );
    await new Promise<void>((resolve, reject) => {
      client.once('open', resolve);
      client.once('error', reject);
      setTimeout(() => reject(new Error('Timeout: client did not connect')), 8_000);
    });
    client.close();

    await capturedHeaders;
    expect(capturedHeaders['x-correlation-id']).toBe(correlationId);
    expect(capturedHeaders['x-gateway-source']).toBe('aep-gateway');
  });
});
