import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { WebSocketClient } from '../WebSocketClient';
import type { WebSocketEvent } from '../WebSocketClient';

/**
 * WebSocket event handling tests — validates that registered listeners receive
 * events when the underlying socket fires open/message/error/close events.
 *
 * @doc.type module
 * @doc.purpose Tests for WebSocket event listener registration and dispatch
 * @doc.layer platform
 * @doc.pattern Test
 */

// ── Mock WebSocket helpers ───────────────────────────────────────────────────

type MockEventListeners = Record<string, ((e: Event | MessageEvent | CloseEvent) => void)[]>;

function makeMockWebSocketWithDispatch(): {
  ws: WebSocket;
  fire: (type: string, event: Event | MessageEvent | CloseEvent) => void;
} {
  const listeners: MockEventListeners = {};

  const ws = {
    readyState: WebSocket.OPEN,
    addEventListener: vi.fn((type: string, handler: () => void) => {
      if (!listeners[type]) listeners[type] = [];
      listeners[type].push(handler);
    }),
    removeEventListener: vi.fn((type: string, handler: () => void) => {
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

  function fire(type: string, event: Event | MessageEvent | CloseEvent): void {
    (listeners[type] ?? []).forEach((handler) => handler(event));
  }

  return { ws, fire };
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe('WebSocket event handling', () => {
  let originalWebSocket: typeof WebSocket;

  beforeEach(() => {
    originalWebSocket = global.WebSocket;
  });

  afterEach(() => {
    global.WebSocket = originalWebSocket;
    vi.clearAllMocks();
  });

  describe('open event', () => {
    it('notifies registered open listeners when the socket opens', () => {
      const { ws, fire } = makeMockWebSocketWithDispatch();
      global.WebSocket = vi.fn(() => ws) as unknown as typeof WebSocket;

      const client = new WebSocketClient({ url: 'ws://localhost:8080' });
      const onOpen = vi.fn<[WebSocketEvent & { type: 'open' }], void>();
      client.on('open', onOpen);
      client.connect();

      const openEvent = new Event('open');
      fire('open', openEvent);

      expect(onOpen).toHaveBeenCalledTimes(1);
    });
  });

  describe('message event', () => {
    it('notifies registered message listeners with the event payload', () => {
      const { ws, fire } = makeMockWebSocketWithDispatch();
      global.WebSocket = vi.fn(() => ws) as unknown as typeof WebSocket;

      const client = new WebSocketClient({ url: 'ws://localhost:8080' });
      const onMessage = vi.fn<[WebSocketEvent & { type: 'message' }], void>();
      client.on('message', onMessage);
      client.connect();

      const msgEvent = new MessageEvent('message', { data: JSON.stringify({ hello: 'world' }) });
      fire('message', msgEvent);

      expect(onMessage).toHaveBeenCalledTimes(1);
    });
  });

  describe('close event', () => {
    it('notifies registered close listeners when the socket closes', () => {
      const { ws, fire } = makeMockWebSocketWithDispatch();
      global.WebSocket = vi.fn(() => ws) as unknown as typeof WebSocket;

      const client = new WebSocketClient({
        url: 'ws://localhost:8080',
        reconnect: false,
      });
      const onClose = vi.fn<[WebSocketEvent & { type: 'close' }], void>();
      client.on('close', onClose);
      client.connect();

      const closeEvent = new CloseEvent('close', { code: 1000, reason: 'Normal closure', wasClean: true });
      fire('close', closeEvent);

      expect(onClose).toHaveBeenCalledTimes(1);
    });
  });

  describe('multiple listeners', () => {
    it('both registered open listeners are called on open event', () => {
      const { ws, fire } = makeMockWebSocketWithDispatch();
      global.WebSocket = vi.fn(() => ws) as unknown as typeof WebSocket;

      const client = new WebSocketClient({ url: 'ws://localhost:8080' });
      const listener1 = vi.fn();
      const listener2 = vi.fn();
      client.on('open', listener1);
      client.on('open', listener2);
      client.connect();

      fire('open', new Event('open'));

      expect(listener1).toHaveBeenCalledOnce();
      expect(listener2).toHaveBeenCalledOnce();
    });
  });
});
