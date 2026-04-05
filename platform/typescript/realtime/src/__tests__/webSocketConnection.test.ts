import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { WebSocketClient } from '../WebSocketClient';
import type { RealtimeClientOptions } from '../WebSocketClient';

/**
 * WebSocket connection lifecycle tests — validates that the WebSocketClient
 * correctly connects, reflects open state, and exposes URL configuration.
 *
 * @doc.type module
 * @doc.purpose Tests for WebSocket connection lifecycle
 * @doc.layer platform
 * @doc.pattern Test
 */

// ── Mock WebSocket factory ───────────────────────────────────────────────────

function makeMockWebSocket(readyState = WebSocket.OPEN): WebSocket {
  return {
    readyState,
    url: 'ws://localhost:8080/ws',
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    close: vi.fn(),
    send: vi.fn(),
    binaryType: 'blob',
    bufferedAmount: 0,
    extensions: '',
    protocol: '',
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
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe('WebSocket connection', () => {
  let originalWebSocket: typeof WebSocket;

  beforeEach(() => {
    originalWebSocket = global.WebSocket;
  });

  afterEach(() => {
    global.WebSocket = originalWebSocket;
    vi.clearAllMocks();
  });

  describe('constructor validation', () => {
    it('throws when URL is missing', () => {
      expect(() => new WebSocketClient({ url: '' } as RealtimeClientOptions)).toThrow(
        'RealtimeClient requires a URL',
      );
    });

    it('creates a client with a valid URL', () => {
      const client = new WebSocketClient({ url: 'ws://localhost:8080' });
      expect(client).toBeDefined();
    });

    it('accepts a function as the URL', () => {
      const client = new WebSocketClient({ url: () => 'ws://dynamic-host/ws' });
      expect(client).toBeDefined();
    });
  });

  describe('connect()', () => {
    it('instantiates a native WebSocket on connect', () => {
      const mockWS = makeMockWebSocket(WebSocket.CONNECTING);
      const MockWebSocket = vi.fn(() => mockWS);
      global.WebSocket = MockWebSocket as unknown as typeof WebSocket;

      const client = new WebSocketClient({ url: 'ws://localhost:8080' });
      client.connect();

      expect(MockWebSocket).toHaveBeenCalledWith('ws://localhost:8080', undefined);
    });

    it('registers open/message/error/close listeners on connect', () => {
      const mockWS = makeMockWebSocket(WebSocket.OPEN);
      global.WebSocket = vi.fn(() => mockWS) as unknown as typeof WebSocket;

      const client = new WebSocketClient({ url: 'ws://localhost:8080' });
      client.connect();

      const addEventListener = mockWS.addEventListener as ReturnType<typeof vi.fn>;
      const registeredEvents = addEventListener.mock.calls.map((c) => c[0]);
      expect(registeredEvents).toContain('open');
      expect(registeredEvents).toContain('message');
      expect(registeredEvents).toContain('error');
      expect(registeredEvents).toContain('close');
    });

    it('calling connect twice when already OPEN does not create a second socket', () => {
      const mockWS = makeMockWebSocket(WebSocket.OPEN);
      const constructor = vi.fn(() => mockWS);
      global.WebSocket = constructor as unknown as typeof WebSocket;

      const client = new WebSocketClient({ url: 'ws://localhost:8080' });
      client.connect();
      client.connect(); // second call should be no-op

      expect(constructor).toHaveBeenCalledTimes(1);
    });
  });

  describe('disconnect()', () => {
    it('calls close on the underlying socket', () => {
      const mockWS = makeMockWebSocket(WebSocket.OPEN);
      global.WebSocket = vi.fn(() => mockWS) as unknown as typeof WebSocket;

      const client = new WebSocketClient({ url: 'ws://localhost:8080' });
      client.connect();
      client.disconnect();

      expect(mockWS.close).toHaveBeenCalled();
    });
  });
});
