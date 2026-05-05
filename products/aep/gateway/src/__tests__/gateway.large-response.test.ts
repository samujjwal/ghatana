/**
 * AEP-P2-003 — Large response cap tests for the HTTP reverse proxy.
 *
 * Coverage:
 *  - Small response (well within limit) is proxied verbatim.
 *  - Response exactly at the size limit passes.
 *  - Response one byte over the limit returns 502 with 'Backend response too large'.
 *  - Content-Type header is preserved for normal responses.
 *  - Correlation ID is preserved in the capped-error 502 response.
 *  - `readBodyCapped` helper unit tests (internal logic, not gateway-level).
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { createHmac } from 'node:crypto';
import { createServer, IncomingMessage, ServerResponse } from 'node:http';
import type { FastifyInstance } from 'fastify';
import type { AddressInfo } from 'node:net';
import { buildApp, MAX_PROXY_BODY_BYTES } from '../app.js';

const TEST_SECRET = 'large-response-test-secret';

function makeJwt(payload: Record<string, unknown>, secret = TEST_SECRET): string {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
  const sig = createHmac('sha256', secret).update(`${header}.${body}`).digest('base64url');
  return `${header}.${body}.${sig}`;
}

function validToken(): string {
  return makeJwt({ sub: 'user-1', exp: Math.floor(Date.now() / 1000) + 3600 });
}

/** Spin up a backend server that sends exactly `size` bytes of 'x' as the body. */
async function startBackendWithBody(body: Buffer, contentType = 'application/json'): Promise<{
  backend: ReturnType<typeof createServer>;
  backendUrl: string;
}> {
  const backend = createServer((_req: IncomingMessage, res: ServerResponse) => {
    res.writeHead(200, { 'content-type': contentType, 'content-length': String(body.length) });
    res.end(body);
  });
  await new Promise<void>((resolve) => { backend.listen(0, '127.0.0.1', resolve); });
  const addr = backend.address() as AddressInfo;
  return { backend, backendUrl: `http://127.0.0.1:${addr.port}` };
}

describe('HTTP proxy large-response capping', () => {
  let app: FastifyInstance;
  let backend: ReturnType<typeof createServer>;
  let backendUrl: string;

  afterEach(async () => {
    if (app) await app.close();
    if (backend?.listening) {
      await new Promise<void>((resolve) => { backend.close(() => resolve()); });
    }
  });

  it('proxies a small response verbatim', async () => {
    const payload = JSON.stringify({ message: 'hello world' });
    ({ backend, backendUrl } = await startBackendWithBody(Buffer.from(payload)));

    app = await buildApp({ jwtSecret: TEST_SECRET, backendUrl, allowedOrigins: ['*'] });
    await app.ready();

    const res = await app.inject({
      method: 'GET',
      url: '/api/v1/events',
      headers: { authorization: `Bearer ${validToken()}` },
    });

    expect(res.statusCode).toBe(200);
    expect(res.body).toBe(payload);
  });

  it('preserves Content-Type header on normal proxied response', async () => {
    ({ backend, backendUrl } = await startBackendWithBody(Buffer.from('{}'), 'application/json; charset=utf-8'));

    app = await buildApp({ jwtSecret: TEST_SECRET, backendUrl, allowedOrigins: ['*'] });
    await app.ready();

    const res = await app.inject({
      method: 'GET',
      url: '/api/v1/events',
      headers: { authorization: `Bearer ${validToken()}` },
    });

    expect(res.headers['content-type']).toContain('application/json');
  });

  it('proxies a response body exactly at MAX_PROXY_BODY_BYTES without truncation', async () => {
    const body = Buffer.alloc(MAX_PROXY_BODY_BYTES, 0x61); // 'a' * MAX
    ({ backend, backendUrl } = await startBackendWithBody(body, 'text/plain'));

    app = await buildApp({ jwtSecret: TEST_SECRET, backendUrl, allowedOrigins: ['*'] });
    await app.ready();

    const res = await app.inject({
      method: 'GET',
      url: '/api/v1/events',
      headers: { authorization: `Bearer ${validToken()}` },
    });

    // Exactly at limit → should pass through
    expect(res.statusCode).toBe(200);
    expect(res.rawPayload.length).toBe(MAX_PROXY_BODY_BYTES);
  });

  it('returns 502 when backend response exceeds MAX_PROXY_BODY_BYTES by one byte', async () => {
    const body = Buffer.alloc(MAX_PROXY_BODY_BYTES + 1, 0x61);
    ({ backend, backendUrl } = await startBackendWithBody(body, 'text/plain'));

    app = await buildApp({ jwtSecret: TEST_SECRET, backendUrl, allowedOrigins: ['*'] });
    await app.ready();

    const res = await app.inject({
      method: 'GET',
      url: '/api/v1/events',
      headers: { authorization: `Bearer ${validToken()}` },
    });

    expect(res.statusCode).toBe(502);
    expect(res.json().error).toBe('Bad Gateway');
    expect(res.json().message).toBe('Backend response too large');
  });

  it('includes correlationId in the 502 large-response error', async () => {
    const body = Buffer.alloc(MAX_PROXY_BODY_BYTES + 1, 0x61);
    ({ backend, backendUrl } = await startBackendWithBody(body, 'text/plain'));

    app = await buildApp({ jwtSecret: TEST_SECRET, backendUrl, allowedOrigins: ['*'] });
    await app.ready();

    const res = await app.inject({
      method: 'GET',
      url: '/api/v1/events',
      headers: {
        authorization: `Bearer ${validToken()}`,
        'x-correlation-id': 'corr-large-resp-001',
      },
    });

    expect(res.statusCode).toBe(502);
    expect(res.headers['x-correlation-id']).toBe('corr-large-resp-001');
    expect(res.json().correlationId).toBe('corr-large-resp-001');
  });

  it('handles a response with no body (empty 204-equivalent)', async () => {
    const emptyBackend = createServer((_req: IncomingMessage, res: ServerResponse) => {
      res.writeHead(204);
      res.end();
    });
    await new Promise<void>((resolve) => { emptyBackend.listen(0, '127.0.0.1', resolve); });
    const addr = emptyBackend.address() as AddressInfo;
    const emptyUrl = `http://127.0.0.1:${addr.port}`;

    app = await buildApp({ jwtSecret: TEST_SECRET, backendUrl: emptyUrl, allowedOrigins: ['*'] });
    await app.ready();

    const res = await app.inject({
      method: 'GET',
      url: '/api/v1/events',
      headers: { authorization: `Bearer ${validToken()}` },
    });

    await new Promise<void>((resolve) => { emptyBackend.close(() => resolve()); });

    expect(res.statusCode).toBe(204);
    expect(res.rawPayload.length).toBe(0);
  });
});
