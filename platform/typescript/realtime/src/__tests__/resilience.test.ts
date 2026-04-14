/**
 * @group unit
 * @tier U, I
 *
 * Resilience and recovery tests for @ghatana/realtime — WebSocketClient.
 *
 * Covers:
 *   1. Reconnect after a forced abnormal disconnect.
 *   2. Event loss detection (message sequence gap awareness).
 *   3. Auth session expiry recovery — 4401 close code triggers re-auth.
 *   4. Provider timeout fallback behavior (maxReconnectAttempts guard).
 *
 * All tests use fake timers to keep the suite fast and deterministic.
 *
 * @doc.type test-suite
 * @doc.purpose Resilience coverage for WebSocketClient reconnect and recovery
 * @doc.layer platform
 * @doc.pattern Test
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { WebSocketClient } from '../WebSocketClient';
import type { WebSocketEvent } from '../WebSocketClient';

// ─── Mock WebSocket factory ───────────────────────────────────────────────────

type AnyHandler = (e: Event | CloseEvent | MessageEvent) => void;

interface MockWSControl {
  ws: WebSocket;
  fireOpen: () => void;
  fireMessage: (data: unknown) => void;
  fireClose: (code?: number, reason?: string) => void;
  fireError: () => void;
}

function makeMockWS(readyState: number = WebSocket.OPEN): MockWSControl {
  const listeners: Record<string, AnyHandler[]> = {};

  const ws = {
    readyState,
    addEventListener: vi.fn((type: string, handler: AnyHandler) => {
      listeners[type] = listeners[type] ?? [];
      listeners[type].push(handler);
    }),
    removeEventListener: vi.fn((type: string, handler: AnyHandler) => {
      if (listeners[type]) {
        listeners[type] = listeners[type].filter((h) => h !== handler);
      }
    }),
    close: vi.fn(),
    send: vi.fn(),
    binaryType: 'blob' as BinaryType,
    bufferedAmount: 0,
    extensions: '',
    protocol: '',
    url: 'ws://localhost:9999',
    onopen: null,
    onclose: null,
    onmessage: null,
    onerror: null,
    CONNECTING: 0,
    OPEN: 1,
    CLOSING: 2,
    CLOSED: 3,
    dispatchEvent: vi.fn(),
  };

  const fireOpen = () => {
    ws.readyState = WebSocket.OPEN;
    (listeners.open ?? []).forEach((h) => h(new Event('open')));
  };

  const fireMessage = (data: unknown) => {
    const msg = new MessageEvent('message', { data: JSON.stringify(data) });
    (listeners.message ?? []).forEach((h) => h(msg));
  };

  const fireClose = (code = 1006, reason = '') => {
    ws.readyState = WebSocket.CLOSED;
    const ev = new CloseEvent('close', { code, reason, wasClean: code === 1000 });
    (listeners.close ?? []).forEach((h) => h(ev));
  };

  const fireError = () => {
    (listeners.error ?? []).forEach((h) => h(new Event('error')));
  };

  return { ws: ws as unknown as WebSocket, fireOpen, fireMessage, fireClose, fireError };
}

// ─── Test Setup ───────────────────────────────────────────────────────────────

let wsFactory: ReturnType<typeof makeMockWS>;
const WS_URL = 'ws://test.local/realtime';

beforeEach(() => {
  vi.useFakeTimers();
  wsFactory = makeMockWS(WebSocket.CONNECTING);
  vi.stubGlobal('WebSocket', vi.fn(() => wsFactory.ws));
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

// ─── 1. Reconnect after forced disconnect ─────────────────────────────────────

describe('Reconnect after forced abnormal disconnect', () => {
  it('emits a reconnect event after an abnormal close (code 1006)', () => {
    const client = new WebSocketClient({
      url: WS_URL,
      reconnect: true,
      maxReconnectAttempts: 3,
      backoffMs: { initial: 100, max: 1000, multiplier: 1.5 },
      heartbeatIntervalMs: 0,
    });

    const reconnectEvents: WebSocketEvent[] = [];
    client.on('reconnect', (e) => reconnectEvents.push(e));

    client.connect();
    wsFactory.fireOpen();
    wsFactory.fireClose(1006, 'abnormal');

    // Fast-forward past the initial backoff delay
    vi.advanceTimersByTime(200);

    expect(reconnectEvents.length).toBeGreaterThanOrEqual(1);
    expect(
      (reconnectEvents[0] as Extract<WebSocketEvent, { type: 'reconnect' }>).attempt,
    ).toBe(1);
  });

  it('reconnnects by calling WebSocket constructor a second time', () => {
    const client = new WebSocketClient({
      url: WS_URL,
      reconnect: true,
      maxReconnectAttempts: 5,
      backoffMs: { initial: 50, max: 500, multiplier: 1 },
      heartbeatIntervalMs: 0,
    });

    client.connect();
    wsFactory.fireOpen();
    wsFactory.fireClose(1006);
    vi.advanceTimersByTime(100);

    // WebSocket should have been instantiated at least twice (initial + reconnect)
    expect(vi.mocked(WebSocket)).toHaveBeenCalledTimes(2);
  });

  it('does NOT send reconnect on a normal close (code 1000)', () => {
    const client = new WebSocketClient({
      url: WS_URL,
      reconnect: true,
      heartbeatIntervalMs: 0,
    });

    const reconnectEvents: WebSocketEvent[] = [];
    client.on('reconnect', (e) => reconnectEvents.push(e));

    client.connect();
    wsFactory.fireOpen();
    wsFactory.fireClose(1000);
    vi.advanceTimersByTime(500);

    expect(reconnectEvents).toHaveLength(0);
  });

  it('multiple reconnects accumulate the attempt counter', () => {
    const attempts: number[] = [];
    const client = new WebSocketClient({
      url: WS_URL,
      reconnect: true,
      maxReconnectAttempts: 5,
      backoffMs: { initial: 10, max: 50, multiplier: 1 },
      heartbeatIntervalMs: 0,
    });

    client.on('reconnect', (e) => {
      attempts.push(
        (e as Extract<WebSocketEvent, { type: 'reconnect' }>).attempt,
      );
    });

    client.connect();
    wsFactory.fireOpen();

    // Simulate two consecutive closes
    wsFactory.fireClose(1006);
    vi.advanceTimersByTime(50);
    wsFactory.fireClose(1006);
    vi.advanceTimersByTime(50);

    expect(attempts.length).toBeGreaterThanOrEqual(2);
    expect(attempts[0]).toBe(1);
    expect(attempts[1]).toBe(2);
  });
});

// ─── 2. Event loss detection ──────────────────────────────────────────────────

describe('Event loss detection via sequence gaps', () => {
  it('detects a gap when received sequence numbers are non-consecutive', () => {
    const received: number[] = [];
    const gaps: Array<{ expected: number; received: number }> = [];

    let expectedSeq = 1;

    const client = new WebSocketClient({
      url: WS_URL,
      reconnect: false,
      heartbeatIntervalMs: 0,
    });

    client.on('message', (e) => {
      const data = JSON.parse(
        (e as Extract<WebSocketEvent, { type: 'message' }>).event.data as string,
      ) as { seq: number };
      if (data.seq !== expectedSeq) {
        gaps.push({ expected: expectedSeq, received: data.seq });
      }
      expectedSeq = data.seq + 1;
      received.push(data.seq);
    });

    client.connect();
    wsFactory.fireOpen();

    wsFactory.fireMessage({ seq: 1, payload: 'a' });
    wsFactory.fireMessage({ seq: 2, payload: 'b' });
    // Simulate message loss: seq 3 missing
    wsFactory.fireMessage({ seq: 4, payload: 'd' });

    expect(received).toEqual([1, 2, 4]);
    expect(gaps).toHaveLength(1);
    expect(gaps[0]).toEqual({ expected: 3, received: 4 });
  });

  it('reports no gaps when all sequence numbers are consecutive', () => {
    const gaps: unknown[] = [];
    let expectedSeq = 1;

    const client = new WebSocketClient({
      url: WS_URL,
      reconnect: false,
      heartbeatIntervalMs: 0,
    });

    client.on('message', (e) => {
      const data = JSON.parse(
        (e as Extract<WebSocketEvent, { type: 'message' }>).event.data as string,
      ) as { seq: number };
      if (data.seq !== expectedSeq) {
        gaps.push({ expected: expectedSeq, received: data.seq });
      }
      expectedSeq = data.seq + 1;
    });

    client.connect();
    wsFactory.fireOpen();

    wsFactory.fireMessage({ seq: 1 });
    wsFactory.fireMessage({ seq: 2 });
    wsFactory.fireMessage({ seq: 3 });

    expect(gaps).toHaveLength(0);
  });
});

// ─── 3. Auth session expiry recovery ─────────────────────────────────────────

describe('Auth session expiry recovery', () => {
  it('triggers a reconnect after close code 4401 (Unauthorized)', () => {
    // 4401 is a custom application-level close code meaning "auth token expired"
    const reconnectAttempts: number[] = [];

    const client = new WebSocketClient({
      url: WS_URL,
      reconnect: true,
      maxReconnectAttempts: 3,
      backoffMs: { initial: 50, max: 200, multiplier: 1 },
      heartbeatIntervalMs: 0,
    });

    client.on('reconnect', (e) => {
      reconnectAttempts.push(
        (e as Extract<WebSocketEvent, { type: 'reconnect' }>).attempt,
      );
    });

    client.connect();
    wsFactory.fireOpen();
    // Simulate token expiry — server closes with 4401
    wsFactory.fireClose(4401, 'Token expired');
    vi.advanceTimersByTime(100);

    // The client should attempt a reconnect (application-level re-auth
    // would happen via the URL factory providing a fresh token on reconnect)
    expect(reconnectAttempts.length).toBeGreaterThanOrEqual(1);
  });

  it('allows refreshed token URL on reconnect via URL factory function', () => {
    const urlCalls: string[] = [];
    let tokenVersion = 1;

    const client = new WebSocketClient({
      url: () => {
        const url = `${WS_URL}?token=v${tokenVersion}`;
        urlCalls.push(url);
        return url;
      },
      reconnect: true,
      maxReconnectAttempts: 2,
      backoffMs: { initial: 10, max: 50, multiplier: 1 },
      heartbeatIntervalMs: 0,
    });

    client.connect();
    wsFactory.fireOpen();
    expect(urlCalls[0]).toContain('token=v1');

    // Simulate auth expiry and token refresh
    tokenVersion = 2;
    wsFactory.fireClose(4401, 'Token expired');
    vi.advanceTimersByTime(50);

    // Reconnect should use the new token via the URL factory
    expect(urlCalls.length).toBeGreaterThanOrEqual(2);
    expect(urlCalls[urlCalls.length - 1]).toContain('token=v2');
  });
});

// ─── 4. Provider timeout — maxReconnectAttempts guard ────────────────────────

describe('Provider timeout fallback — maxReconnectAttempts', () => {
  it('stops attempting reconnects after maxReconnectAttempts is reached', () => {
    const reconnectAttempts: number[] = [];

    const client = new WebSocketClient({
      url: WS_URL,
      reconnect: true,
      maxReconnectAttempts: 2,
      backoffMs: { initial: 10, max: 50, multiplier: 1 },
      heartbeatIntervalMs: 0,
    });

    client.on('reconnect', (e) => {
      reconnectAttempts.push(
        (e as Extract<WebSocketEvent, { type: 'reconnect' }>).attempt,
      );
    });

    client.connect();
    wsFactory.fireOpen();

    // Force three consecutive failures
    for (let i = 0; i < 3; i++) {
      wsFactory.fireClose(1006);
      vi.advanceTimersByTime(100);
    }

    // Should not exceed maxReconnectAttempts
    expect(reconnectAttempts.length).toBeLessThanOrEqual(2);
    expect(Math.max(...reconnectAttempts)).toBeLessThanOrEqual(2);
  });

  it('emits a close event when maxReconnectAttempts is 0 (no reconnect)', () => {
    const closeEvents: WebSocketEvent[] = [];

    const client = new WebSocketClient({
      url: WS_URL,
      reconnect: true,
      maxReconnectAttempts: 0,
      heartbeatIntervalMs: 0,
    });

    client.on('close', (e) => closeEvents.push(e));
    const reconnects: WebSocketEvent[] = [];
    client.on('reconnect', (e) => reconnects.push(e));

    client.connect();
    wsFactory.fireOpen();
    wsFactory.fireClose(1006);
    vi.advanceTimersByTime(500);

    expect(closeEvents.length).toBeGreaterThanOrEqual(1);
    expect(reconnects).toHaveLength(0);
  });

  it('disconnects cleanly — no reconnect after explicit disconnect()', () => {
    const reconnects: WebSocketEvent[] = [];

    const client = new WebSocketClient({
      url: WS_URL,
      reconnect: true,
      maxReconnectAttempts: 10,
      backoffMs: { initial: 10, max: 50, multiplier: 1 },
      heartbeatIntervalMs: 0,
    });

    client.on('reconnect', (e) => reconnects.push(e));

    client.connect();
    wsFactory.fireOpen();

    // Caller explicitly disconnects
    client.disconnect();
    vi.advanceTimersByTime(500);

    expect(reconnects).toHaveLength(0);
  });
});
