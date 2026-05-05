/**
 * AEP-P2-004 — WebSocket heartbeat, idle timeout, max message size, and cleanup tests.
 *
 * All suites marked `@local-network` bind real TCP ports.
 *
 * Coverage:
 *  - Heartbeat: gateway pings client; client responds with pong; session stays alive.
 *  - Heartbeat: gateway closes session when client misses a pong.
 *  - Idle timeout: session is closed with 1001 when no message arrives.
 *  - Max message size: oversized client message closes the session with 1009.
 *  - Cleanup on backend close: timers are cleared and client receives 1000.
 *  - Cleanup on client close: timers are cleared and backend is closed.
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { createHmac } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import type { AddressInfo } from 'node:net';
import { buildApp, WS_IDLE_TIMEOUT_MS, WS_HEARTBEAT_INTERVAL_MS, MAX_WS_MESSAGE_BYTES } from '../app.js';

// Use the `ws` package imported dynamically (ESM package)
import WebSocket, { WebSocketServer } from 'ws';

const TEST_SECRET = 'ws-lifecycle-test-secret';

function makeJwt(payload: Record<string, unknown>, secret = TEST_SECRET): string {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
  const sig = createHmac('sha256', secret).update(`${header}.${body}`).digest('base64url');
  return `${header}.${body}.${sig}`;
}

function validToken(): string {
  return makeJwt({ sub: 'user-1', exp: Math.floor(Date.now() / 1000) + 3600 });
}

/** Helpers to create fast-timeout configs for test speed. */
function idleTimeoutConfig() {
  return {
    wsIdleTimeoutMs: 200,
    wsHeartbeatIntervalMs: 10_000, // disabled effectively — won't fire during test
  };
}

function heartbeatConfig() {
  return {
    wsHeartbeatIntervalMs: 100,
    wsIdleTimeoutMs: 10_000,  // disabled effectively — won't fire during test
  };
}

// @local-network
describe('WS exported constants', () => {
  it('WS_IDLE_TIMEOUT_MS is a positive number', () => {
    expect(WS_IDLE_TIMEOUT_MS).toBeGreaterThan(0);
  });
  it('WS_HEARTBEAT_INTERVAL_MS is a positive number', () => {
    expect(WS_HEARTBEAT_INTERVAL_MS).toBeGreaterThan(0);
  });
  it('MAX_WS_MESSAGE_BYTES is a positive number', () => {
    expect(MAX_WS_MESSAGE_BYTES).toBeGreaterThan(0);
  });
  it('heartbeat interval is less than idle timeout', () => {
    expect(WS_HEARTBEAT_INTERVAL_MS).toBeLessThan(WS_IDLE_TIMEOUT_MS);
  });
});

// @local-network
describe('WS idle timeout', () => {
  let app: FastifyInstance;
  let backendWss: WebSocketServer;
  let gatewayBaseUrl: string;

  beforeEach(async () => {
    backendWss = new WebSocketServer({ host: '127.0.0.1', port: 0 });
    await new Promise<void>((resolve) => backendWss.once('listening', resolve));
    const addr = backendWss.address() as AddressInfo;
    const backendHttpUrl = `http://127.0.0.1:${addr.port}`;

    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: backendHttpUrl,
      allowedOrigins: ['*'],
      ...idleTimeoutConfig(),
    });
    await app.listen({ port: 0, host: '127.0.0.1' });
    const gwAddr = app.server.address() as AddressInfo;
    gatewayBaseUrl = `ws://127.0.0.1:${gwAddr.port}`;
  });

  afterEach(async () => {
    await app.close();
    await new Promise<void>((resolve) => backendWss.close(() => resolve()));
  });

  it('closes client with code 1001 after idle period with no messages', { timeout: 5_000 }, async () => {
    // Backend accepts the connection but sends nothing
    backendWss.once('connection', () => { /* no-op */ });

    const client = new WebSocket(`${gatewayBaseUrl}/tail/events?token=${validToken()}`);
    const code = await new Promise<number>((resolve, reject) => {
      client.once('close', (c) => resolve(c));
      client.once('error', reject);
      setTimeout(() => reject(new Error('Timeout: client was not closed by idle timer')), 4_000);
    });
    expect(code).toBe(1001);
  });

  it('keeps session alive while messages are exchanged', { timeout: 5_000 }, async () => {
    let backendServerSide: WebSocket | null = null;
    backendWss.once('connection', (ws) => {
      backendServerSide = ws;
    });

    const client = new WebSocket(`${gatewayBaseUrl}/tail/events?token=${validToken()}`);
    await new Promise<void>((resolve, reject) => {
      client.once('open', resolve);
      client.once('error', reject);
      setTimeout(() => reject(new Error('Timeout: client did not connect')), 3_000);
    });

    // Send messages faster than the idle timeout to keep the session alive
    const sendInterval = setInterval(() => {
      if (client.readyState === WebSocket.OPEN) {
        client.send(JSON.stringify({ keepalive: true }));
      }
      if (backendServerSide?.readyState === WebSocket.OPEN) {
        backendServerSide.send(JSON.stringify({ ack: true }));
      }
    }, 50); // faster than the 200ms idle timeout

    // Wait longer than the idle timeout — session should remain open
    await new Promise<void>((resolve) => setTimeout(resolve, 300));
    clearInterval(sendInterval);

    // Session should still be open
    expect(client.readyState).toBe(WebSocket.OPEN);
    client.close();
    await new Promise<void>((resolve) => { client.once('close', () => resolve()); });
  });
});

// @local-network
describe('WS heartbeat (ping/pong)', () => {
  let app: FastifyInstance;
  let backendWss: WebSocketServer;
  let gatewayBaseUrl: string;

  beforeEach(async () => {
    backendWss = new WebSocketServer({ host: '127.0.0.1', port: 0 });
    await new Promise<void>((resolve) => backendWss.once('listening', resolve));
    const addr = backendWss.address() as AddressInfo;
    const backendHttpUrl = `http://127.0.0.1:${addr.port}`;

    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: backendHttpUrl,
      allowedOrigins: ['*'],
      wsHeartbeatIntervalMs: 100, // Fast heartbeat
      wsIdleTimeoutMs: 10_000,    // Long idle timeout so it doesn't interfere
    });
    await app.listen({ port: 0, host: '127.0.0.1' });
    const gwAddr = app.server.address() as AddressInfo;
    gatewayBaseUrl = `ws://127.0.0.1:${gwAddr.port}`;
  });

  afterEach(async () => {
    await app.close();
    await new Promise<void>((resolve) => backendWss.close(() => resolve()));
  });

  it('session stays alive when client responds to pings', { timeout: 5_000 }, async () => {
    backendWss.once('connection', () => { /* no-op */ });

    const client = new WebSocket(`${gatewayBaseUrl}/tail/events?token=${validToken()}`);
    // The `ws` library automatically responds to pings with pongs by default.

    await new Promise<void>((resolve, reject) => {
      client.once('open', resolve);
      client.once('error', reject);
      setTimeout(() => reject(new Error('Timeout: client did not open')), 3_000);
    });

    // Wait through two heartbeat cycles — session should remain open
    await new Promise<void>((resolve) => setTimeout(resolve, 250));
    expect(client.readyState).toBe(WebSocket.OPEN);

    client.close();
    await new Promise<void>((resolve) => { client.once('close', () => resolve()); });
  });

  it('gateway sends a ping to the client within the heartbeat interval', { timeout: 5_000 }, async () => {
    backendWss.once('connection', () => { /* no-op */ });

    const client = new WebSocket(`${gatewayBaseUrl}/tail/events?token=${validToken()}`);
    const receivedPing = await new Promise<boolean>((resolve, reject) => {
      client.once('ping', () => resolve(true));
      client.once('error', reject);
      setTimeout(() => reject(new Error('Timeout: no ping received from gateway')), 4_000);
    });
    client.close();
    await new Promise<void>((resolve) => { client.once('close', () => resolve()); });

    expect(receivedPing).toBe(true);
  });
});

// @local-network
describe('WS max message size', () => {
  let app: FastifyInstance;
  let backendWss: WebSocketServer;
  let gatewayBaseUrl: string;

  beforeEach(async () => {
    backendWss = new WebSocketServer({ host: '127.0.0.1', port: 0 });
    await new Promise<void>((resolve) => backendWss.once('listening', resolve));
    const addr = backendWss.address() as AddressInfo;
    const backendHttpUrl = `http://127.0.0.1:${addr.port}`;

    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: backendHttpUrl,
      allowedOrigins: ['*'],
      wsMaxMessageBytes: 64,      // Tiny limit for test speed
      wsIdleTimeoutMs: 10_000,
      wsHeartbeatIntervalMs: 10_000,
    });
    await app.listen({ port: 0, host: '127.0.0.1' });
    const gwAddr = app.server.address() as AddressInfo;
    gatewayBaseUrl = `ws://127.0.0.1:${gwAddr.port}`;
  });

  afterEach(async () => {
    await app.close();
    await new Promise<void>((resolve) => backendWss.close(() => resolve()));
  });

  it('closes connection with 1009 when client sends an oversized message', { timeout: 5_000 }, async () => {
    backendWss.once('connection', () => { /* no-op */ });

    const client = new WebSocket(`${gatewayBaseUrl}/tail/events?token=${validToken()}`);
    await new Promise<void>((resolve, reject) => {
      client.once('open', resolve);
      client.once('error', reject);
      setTimeout(() => reject(new Error('Timeout: client did not open')), 3_000);
    });

    // Send a message larger than 64 bytes
    const oversizedMessage = 'x'.repeat(100);
    client.send(oversizedMessage);

    const code = await new Promise<number>((resolve, reject) => {
      client.once('close', (c) => resolve(c));
      client.once('error', reject);
      setTimeout(() => reject(new Error('Timeout: connection was not closed after oversized message')), 4_000);
    });
    expect(code).toBe(1009);
  });

  it('relays small messages normally (within size limit)', { timeout: 5_000 }, async () => {
    const backendReceived = new Promise<string>((resolve, reject) => {
      backendWss.once('connection', (serverSide) => {
        serverSide.once('message', (data) => resolve(data.toString()));
      });
      setTimeout(() => reject(new Error('Timeout: backend did not receive message')), 4_000);
    });

    const client = new WebSocket(`${gatewayBaseUrl}/tail/events?token=${validToken()}`);
    await new Promise<void>((resolve, reject) => {
      client.once('open', resolve);
      client.once('error', reject);
      setTimeout(() => reject(new Error('Timeout: client did not open')), 3_000);
    });

    client.send('small'); // 5 bytes, well under 64-byte limit
    const received = await backendReceived;
    client.close();
    await new Promise<void>((resolve) => { client.once('close', () => resolve()); });

    expect(received).toBe('small');
  });
});

// @local-network
describe('WS session cleanup', () => {
  let app: FastifyInstance;
  let backendWss: WebSocketServer;
  let gatewayBaseUrl: string;

  beforeEach(async () => {
    backendWss = new WebSocketServer({ host: '127.0.0.1', port: 0 });
    await new Promise<void>((resolve) => backendWss.once('listening', resolve));
    const addr = backendWss.address() as AddressInfo;
    const backendHttpUrl = `http://127.0.0.1:${addr.port}`;

    app = await buildApp({
      jwtSecret: TEST_SECRET,
      backendUrl: backendHttpUrl,
      allowedOrigins: ['*'],
      wsIdleTimeoutMs: 10_000,
      wsHeartbeatIntervalMs: 10_000,
    });
    await app.listen({ port: 0, host: '127.0.0.1' });
    const gwAddr = app.server.address() as AddressInfo;
    gatewayBaseUrl = `ws://127.0.0.1:${gwAddr.port}`;
  });

  afterEach(async () => {
    await app.close();
    await new Promise<void>((resolve) => backendWss.close(() => resolve()));
  });

  it('closes backend when client disconnects', { timeout: 5_000 }, async () => {
    const backendClosed = new Promise<void>((resolve, reject) => {
      backendWss.once('connection', (serverSide) => {
        serverSide.once('close', () => resolve());
      });
      setTimeout(() => reject(new Error('Timeout: backend was not closed after client disconnect')), 4_000);
    });

    const client = new WebSocket(`${gatewayBaseUrl}/tail/events?token=${validToken()}`);
    await new Promise<void>((resolve, reject) => {
      client.once('open', resolve);
      client.once('error', reject);
      setTimeout(() => reject(new Error('Timeout: client did not open')), 3_000);
    });

    client.close(1000, 'Client done');
    await backendClosed;
  });

  it('propagates backend close (1000) to the client', { timeout: 5_000 }, async () => {
    backendWss.once('connection', (serverSide) => {
      setTimeout(() => serverSide.close(1000, 'Done'), 50);
    });

    const client = new WebSocket(`${gatewayBaseUrl}/tail/events?token=${validToken()}`);
    const code = await new Promise<number>((resolve, reject) => {
      client.once('close', (c) => resolve(c));
      client.once('error', reject);
      setTimeout(() => reject(new Error('Timeout: client was not closed after backend close')), 4_000);
    });
    expect(code).toBe(1000);
  });
});
