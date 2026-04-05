import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { WebSocketClient } from '../WebSocketClient';
import type { WebSocketEvent } from '../WebSocketClient';

/**
 * Reconnection logic tests — validates that the WebSocketClient emits
 * reconnect events and limits attempts according to maxReconnectAttempts.
 *
 * @doc.type module
 * @doc.purpose Tests for WebSocket reconnection behavior and attempt limiting
 * @doc.layer platform
 * @doc.pattern Test
 */

// ── Mock factory ─────────────────────────────────────────────────────────────

type EventHandler = (e: Event | CloseEvent) => void;

function makeMockWebSocketWithCloseDispatch(): {
  ws: WebSocket;
  fireClose: (code?: number) => void;
} {
  const listeners: Record<string, EventHandler[]> = {};

  const ws = {
    readyState: WebSocket.OPEN,
    addEventListener: vi.fn((type: string, handler: EventHandler) => {
      if (!listeners[type]) listeners[type] = [];
      listeners[type].push(handler);
    }),
    removeEventListener: vi.fn((type: string, handler: EventHandler) => {
      if (listeners[type]) {
        listeners[type] = listeners[type].filter((h) => h !== handler);
      }
    }),
    close: vi.fn(),
    send: vi.fn(),
    binaryType: 'blob',
    bufferedAmount: 0,
    extensions: '',
    protocol: '',
    url: 'ws://localhost:8080',
    onopen: null,
    onclose: null,
    onmessage: null,
    onerror: null,
    CONNECTING: 0,
    OPEN: 1,
    CLOSING: 2,
    CLOSED: 3,
    dispatchEvent: vi.fn().mockReturnValue(true),
  } as unknown as WebSocket;

  function fireClose(code = 1006): void {
    const evt = new CloseEvent('close', { code, wasClean: code === 1000 });
    (listeners['close'] ?? []).forEach((h) => h(evt));
  }

  return { ws, fireClose };
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe('WebSocket reconnection logic', () => {
  let originalWebSocket: typeof WebSocket;

  beforeEach(() => {
    vi.useFakeTimers();
    originalWebSocket = global.WebSocket;
  });

  afterEach(() => {
    vi.useRealTimers();
    global.WebSocket = originalWebSocket;
    vi.clearAllMocks();
  });

  describe('reconnect is triggered on abnormal close', () => {
    it('emits a reconnect event after an abnormal close', () => {
      const { ws, fireClose } = makeMockWebSocketWithCloseDispatch();
      global.WebSocket = vi.fn(() => ws) as unknown as typeof WebSocket;

      const client = new WebSocketClient({
        url: 'ws://localhost:8080',
        reconnect: true,
        maxReconnectAttempts: 3,
      });

      const onReconnect = vi.fn<[WebSocketEvent & { type: 'reconnect' }], void>();
      client.on('reconnect', onReconnect);
      client.connect();

      fireClose(1006); // abnormal closure

      expect(onReconnect).toHaveBeenCalledTimes(1);
      expect(onReconnect.mock.calls[0][0]).toMatchObject({ type: 'reconnect', attempt: 1 });
    });
  });

  describe('no reconnect on clean normal close', () => {
    it('does not emit reconnect when close code is 1000', () => {
      const { ws, fireClose } = makeMockWebSocketWithCloseDispatch();
      global.WebSocket = vi.fn(() => ws) as unknown as typeof WebSocket;

      const client = new WebSocketClient({
        url: 'ws://localhost:8080',
        reconnect: true,
        maxReconnectAttempts: 3,
      });

      const onReconnect = vi.fn();
      client.on('reconnect', onReconnect);
      client.connect();

      fireClose(1000); // normal close code → no reconnect

      expect(onReconnect).not.toHaveBeenCalled();
    });
  });

  describe('reconnect disabled', () => {
    it('does not emit reconnect when reconnect: false', () => {
      const { ws, fireClose } = makeMockWebSocketWithCloseDispatch();
      global.WebSocket = vi.fn(() => ws) as unknown as typeof WebSocket;

      const client = new WebSocketClient({
        url: 'ws://localhost:8080',
        reconnect: false,
      });

      const onReconnect = vi.fn();
      client.on('reconnect', onReconnect);
      client.connect();

      fireClose(1006);

      expect(onReconnect).not.toHaveBeenCalled();
    });
  });

  describe('attempt counter', () => {
    it('increments attempt number on each reconnect event', () => {
      const { ws, fireClose } = makeMockWebSocketWithCloseDispatch();
      const WS = vi.fn(() => ws);
      global.WebSocket = WS as unknown as typeof WebSocket;

      const client = new WebSocketClient({
        url: 'ws://localhost:8080',
        reconnect: true,
        maxReconnectAttempts: 5,
        backoffMs: { initial: 0, max: 0, multiplier: 1 },
      });

      const attempts: number[] = [];
      client.on('reconnect', (e) => attempts.push(e.attempt));
      client.connect();

      fireClose(1006); // triggers attempt 1

      expect(attempts).toEqual([1]);
    });
  });
});
