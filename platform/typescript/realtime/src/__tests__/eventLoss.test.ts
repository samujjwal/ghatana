import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { WebSocketClient } from '../WebSocketClient';

/**
 * Event loss tests — validates that the WebSocketClient does not silently drop
 * messages and that disconnection during send is handled safely.
 *
 * @doc.type module
 * @doc.purpose Tests for message delivery reliability and event loss prevention
 * @doc.layer platform
 * @doc.pattern Test
 */

type EventHandler = (e: Event | MessageEvent | CloseEvent) => void;

function makeMockWebSocket(): {
  ws: WebSocket;
  fire: (type: string, e: Event | MessageEvent | CloseEvent) => void;
} {
  const listeners: Record<string, EventHandler[]> = {};

  const ws = {
    readyState: WebSocket.OPEN,
    addEventListener: vi.fn((type: string, handler: EventHandler) => {
      if (!listeners[type]) listeners[type] = [];
      listeners[type].push(handler);
    }),
    removeEventListener: vi.fn(),
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

  function fire(type: string, e: Event | MessageEvent | CloseEvent): void {
    (listeners[type] ?? []).forEach((h) => h(e));
  }

  return { ws, fire };
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe('WebSocket event loss prevention', () => {
  let originalWebSocket: typeof WebSocket;

  beforeEach(() => {
    originalWebSocket = global.WebSocket;
  });

  afterEach(() => {
    global.WebSocket = originalWebSocket;
    vi.clearAllMocks();
  });

  describe('message delivery', () => {
    it('registered listener receives every message event fired in sequence', () => {
      const { ws, fire } = makeMockWebSocket();
      global.WebSocket = vi.fn(() => ws) as unknown as typeof WebSocket;

      const client = new WebSocketClient({ url: 'ws://localhost:8080' });
      const received: unknown[] = [];
      client.on('message', (e) => received.push((e.event as MessageEvent).data));
      client.connect();

      fire('message', new MessageEvent('message', { data: 'msg-1' }));
      fire('message', new MessageEvent('message', { data: 'msg-2' }));
      fire('message', new MessageEvent('message', { data: 'msg-3' }));

      expect(received).toEqual(['msg-1', 'msg-2', 'msg-3']);
    });

    it('no messages are lost when two listeners are registered', () => {
      const { ws, fire } = makeMockWebSocket();
      global.WebSocket = vi.fn(() => ws) as unknown as typeof WebSocket;

      const client = new WebSocketClient({ url: 'ws://localhost:8080' });
      const received1: unknown[] = [];
      const received2: unknown[] = [];
      client.on('message', (e) => received1.push((e.event as MessageEvent).data));
      client.on('message', (e) => received2.push((e.event as MessageEvent).data));
      client.connect();

      fire('message', new MessageEvent('message', { data: 'hello' }));

      expect(received1).toEqual(['hello']);
      expect(received2).toEqual(['hello']);
    });
  });

  describe('send on closed socket', () => {
    it('calling send before connect throws or is handled safely without losing other state', () => {
      global.WebSocket = vi.fn() as unknown as typeof WebSocket;

      const client = new WebSocketClient({ url: 'ws://localhost:8080' });

      // The client has no socket yet — send should throw a meaningful error
      expect(() => client.send('payload')).toThrow();
    });
  });

  describe('listener removal', () => {
    it('removed listener does not receive subsequent messages', () => {
      const { ws, fire } = makeMockWebSocket();
      global.WebSocket = vi.fn(() => ws) as unknown as typeof WebSocket;

      const client = new WebSocketClient({ url: 'ws://localhost:8080' });
      const received: unknown[] = [];
      const removeListener = client.on('message', (e) =>
        received.push((e.event as MessageEvent).data),
      );
      client.connect();

      fire('message', new MessageEvent('message', { data: 'before' }));
      removeListener(); // deregister
      fire('message', new MessageEvent('message', { data: 'after' }));

      expect(received).toEqual(['before']); // 'after' should not appear
    });
  });
});
