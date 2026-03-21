import { describe, it, expect, beforeAll, afterAll, beforeEach, afterEach } from 'vitest';
import { createHmac } from 'node:crypto';
import { createServer, IncomingMessage, ServerResponse } from 'node:http';
import type { FastifyInstance } from 'fastify';
import type { AddressInfo } from 'node:net';
import { buildApp } from '../app.js';

const TEST_SECRET = 'integration-test-secret';

function makeJwt(payload: Record<string, unknown>, secret = TEST_SECRET): string {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
  const sig = createHmac('sha256', secret).update(`${header}.${body}`).digest('base64url');
  return `${header}.${body}.${sig}`;
}

function validToken(): string {
  return makeJwt({ sub: 'user-1', exp: Math.floor(Date.now() / 1000) + 3600 });
}

// ── Health endpoint ────────────────────────────────────────────────────────────
describe('GET /health', () => {
  let app: FastifyInstance;

  beforeAll(async () => {
    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: 'http://localhost:9999',
      allowedOrigins: ['http://localhost:5173'],
    });
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  it('returns 200 with status ok', async () => {
    const res = await app.inject({ method: 'GET', url: '/health' });
    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.status).toBe('ok');
    expect(body.timestamp).toBeDefined();
  });

  it('does not require authentication', async () => {
    const res = await app.inject({ method: 'GET', url: '/health' });
    expect(res.statusCode).toBe(200);
  });
});

// ── Authentication on /api/* ───────────────────────────────────────────────────
describe('Authentication', () => {
  let app: FastifyInstance;

  beforeAll(async () => {
    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: 'http://localhost:9999',
      allowedOrigins: ['http://localhost:5173'],
    });
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  it('rejects requests without Authorization header', async () => {
    const res = await app.inject({ method: 'GET', url: '/api/v1/events' });
    expect(res.statusCode).toBe(401);
    expect(res.json().message).toBe('Missing Bearer token');
  });

  it('rejects requests with invalid token', async () => {
    const token = makeJwt({ sub: 'user-1', exp: Math.floor(Date.now() / 1000) + 3600 }, 'wrong-secret');
    const res = await app.inject({
      method: 'GET',
      url: '/api/v1/events',
      headers: { authorization: `Bearer ${token}` },
    });
    expect(res.statusCode).toBe(401);
  });

  it('rejects expired tokens', async () => {
    const token = makeJwt({ sub: 'user-1', exp: Math.floor(Date.now() / 1000) - 100 });
    const res = await app.inject({
      method: 'GET',
      url: '/api/v1/events',
      headers: { authorization: `Bearer ${token}` },
    });
    expect(res.statusCode).toBe(401);
    expect(res.json().message).toBe('JWT has expired');
  });
});

// ── CORS ───────────────────────────────────────────────────────────────────────
describe('CORS', () => {
  let app: FastifyInstance;

  beforeAll(async () => {
    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: 'http://localhost:9999',
      allowedOrigins: ['http://allowed-origin.example.com'],
    });
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  it('sets CORS headers for allowed origin', async () => {
    const res = await app.inject({
      method: 'OPTIONS',
      url: '/health',
      headers: {
        origin: 'http://allowed-origin.example.com',
        'access-control-request-method': 'GET',
      },
    });
    expect(res.headers['access-control-allow-origin']).toBe('http://allowed-origin.example.com');
    expect(res.headers['access-control-allow-credentials']).toBe('true');
  });

  it('includes X-Tenant-Id in allowed headers', async () => {
    const res = await app.inject({
      method: 'OPTIONS',
      url: '/health',
      headers: {
        origin: 'http://allowed-origin.example.com',
        'access-control-request-method': 'GET',
        'access-control-request-headers': 'X-Tenant-Id',
      },
    });
    const allowedHeaders = res.headers['access-control-allow-headers'];
    expect(allowedHeaders).toBeDefined();
    expect(String(allowedHeaders).toLowerCase()).toContain('x-tenant-id');
  });

  it('rejects disallowed origin', async () => {
    const res = await app.inject({
      method: 'OPTIONS',
      url: '/health',
      headers: {
        origin: 'http://evil.example.com',
        'access-control-request-method': 'GET',
      },
    });
    expect(res.headers['access-control-allow-origin']).not.toBe('http://evil.example.com');
  });
});

// ── HTTP Reverse Proxy ─────────────────────────────────────────────────────────
describe('HTTP Reverse Proxy', () => {
  let app: FastifyInstance;
  let backend: ReturnType<typeof createServer>;
  let backendUrl: string;
  let lastRequest: { method: string; url: string; headers: Record<string, string | string[] | undefined>; body: string };

  beforeAll(async () => {
    // Spin up a fake backend
    backend = createServer((req: IncomingMessage, res: ServerResponse) => {
      let body = '';
      req.on('data', (chunk: Buffer) => { body += chunk.toString(); });
      req.on('end', () => {
        lastRequest = {
          method: req.method ?? 'GET',
          url: req.url ?? '/',
          headers: req.headers as Record<string, string | string[] | undefined>,
          body,
        };
        res.writeHead(200, { 'content-type': 'application/json' });
        res.end(JSON.stringify({ proxied: true, method: req.method, url: req.url }));
      });
    });
    await new Promise<void>((resolve) => { backend.listen(0, '127.0.0.1', resolve); });
    const addr = backend.address() as AddressInfo;
    backendUrl = `http://127.0.0.1:${addr.port}`;

    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl,
      allowedOrigins: ['http://localhost:5173'],
    });
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
    await new Promise<void>((resolve) => { backend.close(() => resolve()); });
  });

  it('proxies GET requests to backend', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/api/v1/events',
      headers: { authorization: `Bearer ${validToken()}` },
    });
    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.proxied).toBe(true);
    expect(body.method).toBe('GET');
    expect(body.url).toBe('/api/v1/events');
  });

  it('forwards Authorization header to backend', async () => {
    const token = validToken();
    await app.inject({
      method: 'GET',
      url: '/api/v1/events',
      headers: { authorization: `Bearer ${token}` },
    });
    expect(lastRequest.headers['authorization']).toBe(`Bearer ${token}`);
  });

  it('forwards X-Tenant-Id header to backend', async () => {
    await app.inject({
      method: 'GET',
      url: '/api/v1/events',
      headers: {
        authorization: `Bearer ${validToken()}`,
        'x-tenant-id': 'tenant-42',
      },
    });
    expect(lastRequest.headers['x-tenant-id']).toBe('tenant-42');
  });

  it('proxies POST with body to backend', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/api/v1/events',
      headers: {
        authorization: `Bearer ${validToken()}`,
        'content-type': 'application/json',
      },
      payload: { event: 'test' },
    });
    expect(res.statusCode).toBe(200);
    expect(lastRequest.method).toBe('POST');
    expect(JSON.parse(lastRequest.body)).toEqual({ event: 'test' });
  });

  it('returns 502 when backend is unreachable', async () => {
    const isolatedApp = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: 'http://127.0.0.1:1', // unreachable port
      allowedOrigins: ['http://localhost:5173'],
    });
    await isolatedApp.ready();

    const res = await isolatedApp.inject({
      method: 'GET',
      url: '/api/v1/events',
      headers: { authorization: `Bearer ${validToken()}` },
    });
    expect(res.statusCode).toBe(502);
    expect(res.json().error).toBe('Bad Gateway');

    await isolatedApp.close();
  });
});

// ── WebSocket authentication ───────────────────────────────────────────────────
describe('WebSocket /tail/events auth', () => {
  let app: FastifyInstance;
  let baseUrl: string;

  beforeAll(async () => {
    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: 'http://127.0.0.1:1', // backend WS won't connect — that's fine for auth tests
      allowedOrigins: ['http://localhost:5173'],
    });
    await app.listen({ port: 0, host: '127.0.0.1' });
    const addr = app.server.address() as AddressInfo;
    baseUrl = `ws://127.0.0.1:${addr.port}`;
  });

  afterAll(async () => {
    await app.close();
  });

  it('closes connection without token (code 4001)', async () => {
    const { default: WebSocket } = await import('ws');
    const ws = new WebSocket(`${baseUrl}/tail/events`);
    const code = await new Promise<number>((resolve) => {
      ws.on('close', (c) => resolve(c));
    });
    expect(code).toBe(4001);
  });

  it('closes connection with invalid token (code 4003)', async () => {
    const { default: WebSocket } = await import('ws');
    const badToken = makeJwt({ sub: 'user-1' }, 'wrong-secret');
    const ws = new WebSocket(`${baseUrl}/tail/events`, {
      headers: { authorization: `Bearer ${badToken}` },
    });
    const code = await new Promise<number>((resolve) => {
      ws.on('close', (c) => resolve(c));
    });
    expect(code).toBe(4003);
  });

  it('accepts token from query parameter', async () => {
    const { default: WebSocket } = await import('ws');
    const token = validToken();
    const ws = new WebSocket(`${baseUrl}/tail/events?token=${token}`);
    // With a valid token the server will try to connect to the backend (which is unreachable).
    // The backend error handler will close with 1011.
    const code = await new Promise<number>((resolve) => {
      ws.on('close', (c) => resolve(c));
    });
    // 1011 = backend connection failed, which means auth succeeded
    expect(code).toBe(1011);
  });
});
